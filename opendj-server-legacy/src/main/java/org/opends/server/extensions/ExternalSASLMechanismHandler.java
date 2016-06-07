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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.security.cert.Certificate;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.ExternalSASLMechanismHandlerCfg;
import org.forgerock.opendj.server.config.server.SASLMechanismHandlerCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.AuthenticationInfo;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.config.ConfigConstants.*;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The attribute type that should hold the certificates to use for the
   * validation.
   */
  private AttributeType certificateAttributeType;

  /**
   * Indicates whether to attempt to validate the certificate presented by the
   * client with a certificate in the user's entry.
   */
  private CertificateValidationPolicy validationPolicy;

  /** The current configuration for this SASL mechanism handler. */
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

  @Override
  public void initializeSASLMechanismHandler(
                   ExternalSASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addExternalChangeListener(this);
    currentConfig = configuration;

    // See if we should attempt to validate client certificates against those in
    // the corresponding user's entry.
    validationPolicy = toCertificateValidationPolicy(configuration);


    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    certificateAttributeType = configuration.getCertificateAttribute();
    if (certificateAttributeType == null)
    {
      certificateAttributeType = DirectoryServer.getSchema().getAttributeType(DEFAULT_VALIDATION_CERT_ATTRIBUTE);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_EXTERNAL, this);
  }

  private CertificateValidationPolicy toCertificateValidationPolicy(ExternalSASLMechanismHandlerCfg cfg)
  {
    switch (cfg.getCertificateValidationPolicy())
    {
    case NEVER:
      return CertificateValidationPolicy.NEVER;
    case IFPRESENT:
      return CertificateValidationPolicy.IFPRESENT;
    default:
      return CertificateValidationPolicy.ALWAYS;
    }
  }

  @Override
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removeExternalChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_EXTERNAL);
  }

  @Override
  public void processSASLBind(BindOperation bindOperation)
  {
    ExternalSASLMechanismHandlerCfg config = currentConfig;
    AttributeType certificateAttributeType = this.certificateAttributeType;
    CertificateValidationPolicy validationPolicy = this.validationPolicy;


    // Get the client connection used for the bind request, and get the
    // security manager for that connection.  If either are null, then fail.
    ClientConnection clientConnection = bindOperation.getClientConnection();
    if (clientConnection == null) {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      LocalizableMessage message = ERR_SASLEXTERNAL_NO_CLIENT_CONNECTION.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    if(!(clientConnection instanceof LDAPClientConnection)) {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        LocalizableMessage message = ERR_SASLEXTERNAL_NOT_LDAP_CLIENT_INSTANCE.get();
        bindOperation.setAuthFailureReason(message);
        return;
    }
    LDAPClientConnection lc = (LDAPClientConnection) clientConnection;
    Certificate[] clientCertChain = lc.getClientCertificateChain();
    if (clientCertChain == null || clientCertChain.length == 0) {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      LocalizableMessage message = ERR_SASLEXTERNAL_NO_CLIENT_CERT.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }


    // Get the certificate mapper to use to map the certificate to a user entry.
    DN certificateMapperDN = config.getCertificateMapperDN();
    CertificateMapper<?> certificateMapper =
         DirectoryServer.getCertificateMapper(certificateMapperDN);


    // Use the Directory Server certificate mapper to map the client certificate
    // chain to a single user DN.
    Entry userEntry;
    try
    {
      userEntry = certificateMapper.mapCertificateToUser(clientCertChain);
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      bindOperation.setResponseData(de);
      return;
    }


    // If the user DN is null, then we couldn't establish a mapping and
    // therefore the authentication failed.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      LocalizableMessage message = ERR_SASLEXTERNAL_NO_MAPPING.get();
      bindOperation.setAuthFailureReason(message);
      return;
    }

    bindOperation.setSASLAuthUserEntry(userEntry);


    // Get the userCertificate attribute from the user's entry for use in the
    // validation process.
    List<Attribute> certAttrList = userEntry.getAttribute(certificateAttributeType);
    switch (validationPolicy)
    {
      case ALWAYS:
        if (certAttrList.isEmpty())
        {
          if (validationPolicy == CertificateValidationPolicy.ALWAYS)
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLEXTERNAL_NO_CERT_IN_ENTRY.get(userEntry.getName());
            bindOperation.setAuthFailureReason(message);
            return;
          }
        }
        else
        {
          try
          {
            ByteString certBytes = ByteString.wrap(clientCertChain[0].getEncoded());
            if (!findAttributeValue(certAttrList, certBytes))
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              LocalizableMessage message = ERR_SASLEXTERNAL_PEER_CERT_NOT_FOUND.get(userEntry.getName());
              bindOperation.setAuthFailureReason(message);
              return;
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLEXTERNAL_CANNOT_VALIDATE_CERT.get(
                userEntry.getName(), getExceptionMessage(e));
            bindOperation.setAuthFailureReason(message);
            return;
          }
        }
        break;

      case IFPRESENT:
        if (!certAttrList.isEmpty())
        {
          try
          {
            ByteString certBytes = ByteString.wrap(clientCertChain[0].getEncoded());
            if (!findAttributeValue(certAttrList, certBytes))
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              LocalizableMessage message = ERR_SASLEXTERNAL_PEER_CERT_NOT_FOUND.get(userEntry.getName());
              bindOperation.setAuthFailureReason(message);
              return;
            }
          }
          catch (Exception e)
          {
            logger.traceException(e);

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            LocalizableMessage message = ERR_SASLEXTERNAL_CANNOT_VALIDATE_CERT.get(
                    userEntry.getName(), getExceptionMessage(e));
            bindOperation.setAuthFailureReason(message);
            return;
          }
        }
    }


    AuthenticationInfo authInfo = new AuthenticationInfo(userEntry,
        SASL_MECHANISM_EXTERNAL, DirectoryServer.isRootDN(userEntry.getName()));
    bindOperation.setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);
  }

  private boolean findAttributeValue(List<Attribute> certAttrList, ByteString certBytes)
  {
    for (Attribute a : certAttrList)
    {
      if (a.contains(certBytes))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPasswordBased(String mechanism)
  {
    // This is not a password-based mechanism.
    return false;
  }

  @Override
  public boolean isSecure(String mechanism)
  {
    // This may be considered a secure mechanism.
    return true;
  }

  @Override
  public boolean isConfigurationAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    ExternalSASLMechanismHandlerCfg config =
         (ExternalSASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      ExternalSASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
              ExternalSASLMechanismHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // See if we should attempt to validate client certificates against those in
    // the corresponding user's entry.
    CertificateValidationPolicy newValidationPolicy = toCertificateValidationPolicy(configuration);


    // Get the attribute type to use for validating the certificates.  If none
    // is provided, then default to the userCertificate type.
    AttributeType newCertificateType = configuration.getCertificateAttribute();
    if (newCertificateType == null)
    {
      newCertificateType = DirectoryServer.getSchema().getAttributeType(DEFAULT_VALIDATION_CERT_ATTRIBUTE);
    }


    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      validationPolicy         = newValidationPolicy;
      certificateAttributeType = newCertificateType;
      currentConfig            = configuration;
    }

    return ccr;
  }
}
