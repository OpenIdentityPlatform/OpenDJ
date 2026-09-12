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
package org.opends.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A miniature Certificate Authority for tests which need a CA-signed key pair rather
 * than a self-signed one: the key stores {@code setup} is given by an operator who runs
 * their own PKI.
 * <p>
 * The class name deliberately avoids the {@code Test} prefix and the {@code Test},
 * {@code TestCase} suffixes: those are the patterns the failsafe configuration picks up
 * as test classes, and every test class is required to extend {@code DirectoryServerTestCase}.
 */
public final class CertificateFixture
{
  private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
  private static final int VALIDITY_DAYS = 365;

  private final KeyPair caKeyPair;
  private final X509Certificate caCertificate;
  private final String caSubject;

  /**
   * Creates a certificate authority whose certificate is self-signed, as a root CA is.
   *
   * @param caSubject
   *          The subject DN of the CA certificate, for instance {@code "CN=Example CA"}.
   * @throws Exception
   *           If the CA key pair or certificate cannot be generated.
   */
  public CertificateFixture(String caSubject) throws Exception
  {
    this.caSubject = caSubject;
    this.caKeyPair = newKeyPair();
    this.caCertificate = sign(caSubject, caKeyPair.getPublic(), caSubject, caKeyPair, true);
  }

  /**
   * Returns the certificate of this authority, the one which has to be trusted for the
   * certificates it issues to be accepted.
   *
   * @return The CA certificate.
   */
  public X509Certificate getCaCertificate()
  {
    return caCertificate;
  }

  /**
   * Writes the CA certificate to the provided file, DER encoded, as {@code keytool
   * -exportcert} does.
   *
   * @param file
   *          The file to write the certificate to.
   * @throws Exception
   *           If the file cannot be written.
   */
  public void writeCaCertificate(File file) throws Exception
  {
    try (final OutputStream out = new FileOutputStream(file))
    {
      out.write(caCertificate.getEncoded());
    }
  }

  /**
   * Adds one key pair signed by this authority to a key store, creating the key store if
   * it does not exist yet.
   *
   * @param file
   *          The key store file to create or extend.
   * @param storeType
   *          The key store type, for instance {@code "PKCS12"} or {@code "JKS"}.
   * @param password
   *          The password protecting both the store and the private key, as the key
   *          managers of the server require them to be identical.
   * @param alias
   *          The alias to store the key pair under.
   * @param subject
   *          The subject DN of the issued certificate.
   * @param withChain
   *          {@code true} to store the CA certificate along with the issued certificate,
   *          as a properly built key store does, {@code false} to store the issued
   *          certificate on its own.
   * @throws Exception
   *           If the key store cannot be written.
   */
  public void addKeyEntry(File file, String storeType, String password, String alias, String subject,
      boolean withChain) throws Exception
  {
    final KeyPair keyPair = newKeyPair();
    final X509Certificate certificate = sign(subject, keyPair.getPublic(), caSubject, caKeyPair, false);
    final Certificate[] chain = withChain
        ? new Certificate[] { certificate, caCertificate }
        : new Certificate[] { certificate };

    final KeyStore keyStore = KeyStore.getInstance(storeType);
    if (file.exists())
    {
      try (final InputStream in = new FileInputStream(file))
      {
        keyStore.load(in, password.toCharArray());
      }
    }
    else
    {
      keyStore.load(null, password.toCharArray());
    }
    keyStore.setKeyEntry(alias, keyPair.getPrivate(), password.toCharArray(), chain);
    try (final OutputStream out = new FileOutputStream(file))
    {
      keyStore.store(out, password.toCharArray());
    }
  }

  private static KeyPair newKeyPair() throws Exception
  {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static X509Certificate sign(String subject, java.security.PublicKey subjectKey, String issuer,
      KeyPair issuerKeyPair, boolean isCa) throws Exception
  {
    final Instant now = Instant.now();
    final JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
        new X500Name(issuer),
        new BigInteger(64, new SecureRandom()),
        Date.from(now.minus(1, ChronoUnit.DAYS)),
        Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS)),
        new X500Name(subject),
        subjectKey);
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));

    final ContentSigner signer =
        new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerKeyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
  }
}
