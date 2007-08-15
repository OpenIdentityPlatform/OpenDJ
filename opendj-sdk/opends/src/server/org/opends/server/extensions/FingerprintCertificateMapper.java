/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;
import org.opends.messages.Message;



import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.admin.std.server.FingerprintCertificateMapperCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.ExtensionMessages.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user only if that user's entry contains an
 * attribute with the fingerprint of the client certificate.  There must be
 * exactly one matching user entry for the mapping to be successful.
 */
public class FingerprintCertificateMapper
       extends CertificateMapper<FingerprintCertificateMapperCfg>
       implements ConfigurationChangeListener<
                       FingerprintCertificateMapperCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type that will be used to map the certificate's fingerprint.
  private AttributeType fingerprintAttributeType;

  // The DN of the configuration entry for this certificate mapper.
  private DN configEntryDN;

  // The current configuration for this certificate mapper.
  private FingerprintCertificateMapperCfg currentConfig;

  // The algorithm that will be used to generate the fingerprint.
  private String fingerprintAlgorithm;



  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public FingerprintCertificateMapper()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeCertificateMapper(
                   FingerprintCertificateMapperCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addFingerprintChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Get the attribute type that will be used to hold the fingerprint.
    String attrName = configuration.getFingerprintAttribute();
    fingerprintAttributeType =
         DirectoryServer.getAttributeType(toLowerCase(attrName), false);
    if (fingerprintAttributeType == null)
    {
      Message message =
          ERR_FCM_NO_SUCH_ATTR.get(String.valueOf(configEntryDN), attrName);
      throw new ConfigException(message);
    }


    // Get the algorithm that will be used to generate the fingerprint.
    switch (configuration.getFingerprintAlgorithm())
    {
      case MD5:
        fingerprintAlgorithm = "MD5";
        break;
      case SHA1:
        fingerprintAlgorithm = "SHA1";
        break;
    }
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeCertificateMapper()
  {
    currentConfig.removeFingerprintChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    FingerprintCertificateMapperCfg config = currentConfig;
    AttributeType fingerprintAttributeType = this.fingerprintAttributeType;
    String fingerprintAlgorithm = this.fingerprintAlgorithm;

    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      Message message = ERR_FCM_NO_PEER_CERTIFICATE.get();
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_FCM_PEER_CERT_NOT_X509.get(
          String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the signature from the peer certificate and create a digest of it
    // using the configured algorithm.
    String fingerprintString;
    try
    {
      MessageDigest digest = MessageDigest.getInstance(fingerprintAlgorithm);
      byte[] fingerprintBytes = digest.digest(peerCertificate.getEncoded());
      fingerprintString = bytesToColonDelimitedHex(fingerprintBytes);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      String peerSubject = peerCertificate.getSubjectX500Principal().getName(
                                X500Principal.RFC2253);

      Message message = ERR_FCM_CANNOT_CALCULATE_FINGERPRINT.get(
          peerSubject, getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Create the search filter from the fingerprint.
    AttributeValue value =
         new AttributeValue(fingerprintAttributeType, fingerprintString);
    SearchFilter filter =
         SearchFilter.createEqualityFilter(fingerprintAttributeType, value);


    // If we have an explicit set of base DNs, then use it.  Otherwise, use the
    // set of public naming contexts in the server.
    Collection<DN> baseDNs = config.getUserBaseDN();
    if ((baseDNs == null) || baseDNs.isEmpty())
    {
      baseDNs = DirectoryServer.getPublicNamingContexts().keySet();
    }


    // For each base DN, issue an internal search in an attempt to map the
    // certificate.
    Entry userEntry = null;
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    for (DN baseDN : baseDNs)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDN, SearchScope.WHOLE_SUBTREE, filter);
      for (SearchResultEntry entry : searchOperation.getSearchEntries())
      {
        if (userEntry == null)
        {
          userEntry = entry;
        }
        else
        {
          Message message = ERR_FCM_MULTIPLE_MATCHING_ENTRIES.
              get(fingerprintString, String.valueOf(userEntry.getDN()),
                  String.valueOf(entry.getDN()));
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
        }
      }
    }


    // If we've gotten here, then we either found exactly one user entry or we
    // didn't find any.  Either way, return the entry or null to the caller.
    return userEntry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(CertificateMapperCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    FingerprintCertificateMapperCfg config =
         (FingerprintCertificateMapperCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      FingerprintCertificateMapperCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Make sure that the fingerprint attribute is defined in the server schema.
    String attrName = configuration.getFingerprintAttribute();
    AttributeType newFingerprintType =
                       DirectoryServer.getAttributeType(toLowerCase(attrName),
                                       false);
    if (newFingerprintType == null)
    {
      unacceptableReasons.add(ERR_FCM_NO_SUCH_ATTR.get(
              String.valueOf(cfgEntryDN),
              attrName));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              FingerprintCertificateMapperCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Make sure that the fingerprint attribute is defined in the server schema.
    String attrName = configuration.getFingerprintAttribute();
    AttributeType newFingerprintType =
                       DirectoryServer.getAttributeType(toLowerCase(attrName),
                                       false);
    if (newFingerprintType == null)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.NO_SUCH_ATTRIBUTE;
      }

      messages.add(ERR_FCM_NO_SUCH_ATTR.get(
              String.valueOf(configEntryDN), attrName));
    }


    // Get the algorithm that will be used to generate the fingerprint.
    String newFingerprintAlgorithm = null;
    switch (configuration.getFingerprintAlgorithm())
    {
      case MD5:
        newFingerprintAlgorithm = "MD5";
        break;
      case SHA1:
        newFingerprintAlgorithm = "SHA1";
        break;
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      fingerprintAttributeType = newFingerprintType;
      fingerprintAlgorithm     = newFingerprintAlgorithm;
      currentConfig            = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

