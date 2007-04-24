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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ExternalSASLMechanismHandlerCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConnectionSecurityProvider;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a SASL mechanism that relies on some
 * form of authentication that has already been done outside the LDAP layer.  At
 * the present time, this implementation only provides support for SSL-based
 * clients that presented their own certificate to the Directory Server during
 * the negotiation process.  Future implementations may be updated to look in
 * other places to find and evaluate this external authentication information.
 */
public class ExternalSASLMechanismHandler
       extends SASLMechanismHandler<ExternalSASLMechanismHandlerCfg>
       implements ConfigurationChangeListener<
                       ExternalSASLMechanismHandlerCfg>
{
  // The attribute type that should hold the certificates to use for the
  // validation.
  private AttributeType certificateAttributeType;

  // Indicates whether to attempt to validate the certificate presented by the
  // client with a certificate in the user's entry.
  private CertificateValidationPolicy validationPolicy;

  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The current configuration for this SASL mechanism handler.
  private ExternalSASLMechanismHandlerCfg currentConfig;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public ExternalSASLMechanismHandler()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(
                   ExternalSASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addExternalChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // See if we should attempt to validate client certificates against those in
    // the corresponding user's entry.
    switch (configuration.getCertificateValidationPolicy())
    {
      case NEVER:
        validationPolicy = CertificateValidationPolicy.NEVER;
        break;
      case IFPRESENT:
        validationPolicy = CertificateValidationPolicy.IFPRESENT;
        break;
      case ALWAYS:
        validationPolicy = CertificateValidationPolicy.ALWAYS;
        break;
    }


    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    String attrTypeName = configuration.getCertificateAttribute();
    if (attrTypeName == null)
    {
      attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    }
    certificateAttributeType =
         DirectoryServer.getAttributeType(toLowerCase(attrTypeName), false);
    if (certificateAttributeType == null)
    {
      int    msgID   = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      String message = getMessage(msgID, String.valueOf(attrTypeName),
                                  String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    // Make sure that the configured certificate mapper is valid.
    CertificateMapper certificateMapper =
         DirectoryServer.getCertificateMapper(
              configuration.getCertificateMapperDN());
    if (certificateMapper == null)
    {
      int    msgID   = MSGID_SASLEXTERNAL_INVALID_CERTIFICATE_MAPPER_DN;
      String message =
           getMessage(msgID, String.valueOf(configEntryDN),
                      String.valueOf(configuration.getCertificateMapperDN()));
      throw new ConfigException(msgID, message);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_EXTERNAL, this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removeExternalChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_EXTERNAL);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {
    ExternalSASLMechanismHandlerCfg config = currentConfig;
    AttributeType certificateAttributeType = this.certificateAttributeType;
    CertificateValidationPolicy validationPolicy = this.validationPolicy;


    // Get the client connection used for the bind request, and get the
    // security manager for that connection.  If either are null, then fail.
    ClientConnection clientConnection = bindOperation.getClientConnection();
    if (clientConnection == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_CLIENT_CONNECTION;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    ConnectionSecurityProvider securityProvider =
         clientConnection.getConnectionSecurityProvider();
    if (securityProvider == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_SECURITY_PROVIDER;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Make sure that the client connection is using the TLS security provider.
    // If not, then fail.
    if (! (securityProvider instanceof TLSConnectionSecurityProvider))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_CLIENT_NOT_USING_TLS_PROVIDER;
      String message = getMessage(msgID,
                                  securityProvider.getSecurityMechanismName());
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    TLSConnectionSecurityProvider tlsSecurityProvider =
         (TLSConnectionSecurityProvider) securityProvider;


    // Get the certificate chain that the client presented to the server, if
    // possible.  If there isn't one, then fail.
    java.security.cert.Certificate[] clientCertChain =
         tlsSecurityProvider.getClientCertificateChain();
    if ((clientCertChain == null) || (clientCertChain.length == 0))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_CLIENT_CERT;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Get the certificate mapper to use to map the certificate to a user entry.
    DN certificateMapperDN = config.getCertificateMapperDN();
    CertificateMapper certificateMapper =
         DirectoryServer.getCertificateMapper(certificateMapperDN);
    if (certificateMapper == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_INVALID_CERTIFICATE_MAPPER_DN;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(certificateMapperDN));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Use the Directory Server certificate mapper to map the client certificate
    // chain to a single user DN.
    Entry userEntry;
    try
    {
      userEntry = certificateMapper.mapCertificateToUser(clientCertChain);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, de);
      }

      bindOperation.setResponseData(de);
      return;
    }


    // If the user DN is null, then we couldn't establish a mapping and
    // therefore the authentication failed.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLEXTERNAL_NO_MAPPING;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // Get the userCertificate attribute from the user's entry for use in the
    // validation process.
    List<Attribute> certAttrList =
         userEntry.getAttribute(certificateAttributeType);
    switch (validationPolicy)
    {
      case ALWAYS:
        if (certAttrList == null)
        {
          if (validationPolicy == CertificateValidationPolicy.ALWAYS)
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_NO_CERT_IN_ENTRY;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
        else
        {
          try
          {
            byte[] certBytes = clientCertChain[0].getEncoded();
            AttributeValue v =
                 new AttributeValue(certificateAttributeType,
                                    new ASN1OctetString(certBytes));

            boolean found = false;
            for (Attribute a : certAttrList)
            {
              if (a.hasValue(v))
              {
                found = true;
                break;
              }
            }

            if (! found)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()),
                                        getExceptionMessage(e));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
        break;

      case IFPRESENT:
        if (certAttrList != null)
        {
          try
          {
            byte[] certBytes = clientCertChain[0].getEncoded();
            AttributeValue v =
                 new AttributeValue(certificateAttributeType,
                                    new ASN1OctetString(certBytes));

            boolean found = false;
            for (Attribute a : certAttrList)
            {
              if (a.hasValue(v))
              {
                found = true;
                break;
              }
            }

            if (! found)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLEXTERNAL_PEER_CERT_NOT_FOUND;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLEXTERNAL_CANNOT_VALIDATE_CERT;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()),
                                        getExceptionMessage(e));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
    }


    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, SASL_MECHANISM_EXTERNAL,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    // This is not a password-based mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    // This may be considered a secure mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      ExternalSASLMechanismHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;

    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    String attrTypeName = configuration.getCertificateAttribute();
    if (attrTypeName != null)
    {
      attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    }
    AttributeType newCertificateType =
         DirectoryServer.getAttributeType(toLowerCase(attrTypeName), false);
    if (newCertificateType == null)
    {
      int msgID = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(attrTypeName),
                                         String.valueOf(configEntryDN)));
      configAcceptable = false;
    }


    // Make sure that the configured certificate mapper is valid.
    CertificateMapper certificateMapper =
         DirectoryServer.getCertificateMapper(
              configuration.getCertificateMapperDN());
    if (certificateMapper == null)
    {
      int msgID = MSGID_SASLEXTERNAL_INVALID_CERTIFICATE_MAPPER_DN;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
           String.valueOf(configuration.getCertificateMapperDN())));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              ExternalSASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // See if we should attempt to validate client certificates against those in
    // the corresponding user's entry.
    CertificateValidationPolicy newValidationPolicy =
         CertificateValidationPolicy.ALWAYS;
    switch (configuration.getCertificateValidationPolicy())
    {
      case NEVER:
        newValidationPolicy = CertificateValidationPolicy.NEVER;
        break;
      case IFPRESENT:
        newValidationPolicy = CertificateValidationPolicy.IFPRESENT;
        break;
      case ALWAYS:
        newValidationPolicy = CertificateValidationPolicy.ALWAYS;
        break;
    }


    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    String attrTypeName = configuration.getCertificateAttribute();
    if (attrTypeName == null)
    {
      attrTypeName = DEFAULT_VALIDATION_CERT_ATTRIBUTE;
    }
    AttributeType newCertificateType =
         DirectoryServer.getAttributeType(toLowerCase(attrTypeName), false);
    if (newCertificateType == null)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.NO_SUCH_ATTRIBUTE;
      }

      int msgID = MSGID_SASLEXTERNAL_UNKNOWN_CERT_ATTR;
      messages.add(getMessage(msgID, String.valueOf(attrTypeName),
                              String.valueOf(configEntryDN)));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      validationPolicy         = newValidationPolicy;
      certificateAttributeType = newCertificateType;
      currentConfig            = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

