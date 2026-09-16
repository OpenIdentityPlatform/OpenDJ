/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.quicksetup.installer;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.util.Utils.createProtectedFile;
import static org.opends.server.config.ConfigConstants.ADS_CERTIFICATE_ALIAS;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.ReturnCode;
import org.opends.server.util.CertificateManager;
import org.opends.server.util.SetupUtils;

/**
 * Provisions the trust store used for server to server communication, {@code
 * ads-truststore}, from a key store the operator already holds.
 * <p>
 * Replication reads both the key pair it presents on the replication port and the
 * certificates it trusts there from that file, and from nowhere else: neither the key
 * store configured for LDAPS nor the one of the administration connector is consulted.
 * Without this, a server installs with the self-signed {@code ads-certificate} the trust
 * store backend generates on the first start, and securing replication with an
 * organisation's own certificates means stopping every server afterwards and repeating a
 * {@code keytool} procedure by hand on each of them.
 * <p>
 * Two properties of the trust store shape what this class does:
 * <ul>
 * <li>Only a trusted certificate entry is a trust anchor. The certificate chain of a key
 * entry is not: the trust managers take the certificate the key belongs to and none of
 * its issuers. The issuing certificates are therefore imported as trusted certificate
 * entries of their own, and a key pair which comes without any, and without certificates
 * named separately, is reported rather than left to fail as a handshake later on.</li>
 * <li>The {@code ads-certificate} key pair is not provisioned. It is the crypto manager
 * instance key, published to the topology under {@code cn=instance keys,cn=admin data}
 * and read by its alias, and the trust store backend generates it on the first start when
 * the alias is free.</li>
 * </ul>
 */
final class AdsTrustStoreProvisioner
{
  /** The prefix of the aliases the trusted certificates are imported under. */
  private static final String CA_ALIAS_PREFIX = "ads-ca-";

  private final String trustStorePath;
  private final String pinFilePath;

  /**
   * Creates a provisioner for the provided trust store.
   *
   * @param trustStorePath
   *          The path of the trust store file to create.
   * @param pinFilePath
   *          The path of the file to write the generated PIN of the trust store to.
   */
  AdsTrustStoreProvisioner(String trustStorePath, String pinFilePath)
  {
    this.trustStorePath = trustStorePath;
    this.pinFilePath = pinFilePath;
  }

  /**
   * Creates the trust store, holding the provided key pairs and the certificates to trust
   * on the replication port, and writes its PIN file.
   * <p>
   * Everything is read and checked before anything is written: a refusal leaves no file
   * behind, and a failure while writing removes the partial trust store.
   *
   * @param source
   *          The key store holding the key pairs to import.
   * @param aliases
   *          The aliases of the key pairs to import, as the key store spells them, which
   *          become the certificate nicknames the crypto manager presents.
   * @param caCertificateFiles
   *          The files holding certificates to trust, on top of the issuers found in the
   *          certificate chains of the imported key pairs. Every certificate of a file is
   *          trusted. May be empty.
   * @throws ApplicationException
   *           If the key pairs cannot be read, if an alias is one the trust store reserves,
   *           if the trust store would end up trusting no certificate at all, if it
   *           already exists, or if it cannot be written.
   */
  void provision(CertificateManager source, Collection<String> aliases, Collection<File> caCertificateFiles)
      throws ApplicationException
  {
    final List<Certificate> trusted;
    try
    {
      checkReservedAliases(aliases);
      trusted = issuersOf(source, aliases);
      for (File caCertificateFile : caCertificateFiles)
      {
        for (Certificate certificate : certificatesOf(caCertificateFile))
        {
          if (!trusted.contains(certificate))
          {
            trusted.add(certificate);
          }
        }
      }
    }
    catch (ApplicationException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE.get(trustStorePath, String.valueOf(e)), e);
    }
    if (trusted.isEmpty())
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE_NO_TRUST_ANCHOR.get(
              source.getKeyStorePath(), joinAliases(aliases)), null);
    }
    if (new File(trustStorePath).exists())
    {
      // Only what this run writes is removed on failure, so a store which is already
      // there is left alone rather than overwritten or deleted.
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE_EXISTS.get(trustStorePath), null);
    }

    try
    {
      // The trust store is a JKS whatever the type of the key store the key pairs come
      // from: ds-cfg-trust-store-type of the ads-truststore backend says JKS.
      final String pin = new String(SetupUtils.createSelfSignedCertificatePwd());
      final CertificateManager trustStore =
          new CertificateManager(trustStorePath, CertificateManager.KEY_STORE_TYPE_JKS, pin);
      for (String alias : aliases)
      {
        trustStore.importKeyEntry(alias, source, alias);
      }
      int trustedCertificates = 0;
      for (Certificate certificate : trusted)
      {
        trustStore.addTrustedCertificate(CA_ALIAS_PREFIX + ++trustedCertificates, certificate);
      }
      createProtectedFile(pinFilePath, pin);
    }
    catch (Throwable t)
    {
      deletePartialTrustStore();
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE.get(trustStorePath, String.valueOf(t)), t);
    }
  }

  /**
   * Refuses the aliases the trust store keeps for itself: {@code ads-certificate} is the
   * instance key the server generates on its first start, which it would skip if the alias
   * were taken, and {@code ads-ca-N} are the certificates trusted here.
   */
  private void checkReservedAliases(Collection<String> aliases) throws ApplicationException
  {
    for (String alias : aliases)
    {
      if (alias.equalsIgnoreCase(ADS_CERTIFICATE_ALIAS)
          || alias.toLowerCase(Locale.ENGLISH).startsWith(CA_ALIAS_PREFIX))
      {
        throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
            ERR_INSTALL_ADS_TRUSTSTORE_RESERVED_ALIAS.get(alias, ADS_CERTIFICATE_ALIAS, CA_ALIAS_PREFIX), null);
      }
    }
  }

  /**
   * Returns the issuers of the certificate chains of the provided key pairs, one entry
   * per distinct certificate: the key pairs of one server are usually issued by the same
   * authority, which is then to be trusted once rather than under one alias each.
   */
  private List<Certificate> issuersOf(CertificateManager source, Collection<String> aliases) throws Exception
  {
    final List<Certificate> issuers = new ArrayList<>();
    for (String alias : aliases)
    {
      final Certificate[] chain = source.getCertificateChain(alias);
      if (chain == null || chain.length == 0)
      {
        throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
            ERR_INSTALL_ADS_TRUSTSTORE_NO_KEY_PAIR.get(source.getKeyStorePath(), alias), null);
      }
      for (int i = 1; i < chain.length; i++)
      {
        if (!issuers.contains(chain[i]))
        {
          issuers.add(chain[i]);
        }
      }
    }
    return issuers;
  }

  /**
   * Returns every certificate held in the provided file, which may be a single DER or PEM
   * certificate, a PEM bundle or a PKCS#7 chain: a file which holds a chain of authorities
   * is trusted whole, rather than up to its first certificate only.
   */
  private Collection<? extends Certificate> certificatesOf(File caCertificateFile) throws Exception
  {
    final Collection<? extends Certificate> certificates;
    try (InputStream in = new FileInputStream(caCertificateFile))
    {
      certificates = CertificateFactory.getInstance("X.509").generateCertificates(in);
    }
    catch (CertificateException e)
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE_CA_CERT_FILE_UNREADABLE.get(caCertificateFile.getPath(), e.getMessage()), e);
    }
    if (certificates.isEmpty())
    {
      throw new ApplicationException(ReturnCode.CONFIGURATION_ERROR,
          ERR_INSTALL_ADS_TRUSTSTORE_CA_CERT_FILE_EMPTY.get(caCertificateFile.getPath()), null);
    }
    return certificates;
  }

  private void deletePartialTrustStore()
  {
    new File(trustStorePath).delete();
    new File(pinFilePath).delete();
  }

  private String joinAliases(Collection<String> aliases)
  {
    return String.join(", ", aliases);
  }
}
