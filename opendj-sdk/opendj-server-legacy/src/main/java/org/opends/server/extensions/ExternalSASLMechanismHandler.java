/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.security.cert.Certificate;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ExternalSASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SASLMechanismHandler;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
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



  /** {@inheritDoc} */
  @Override
  public void initializeSASLMechanismHandler(
                   ExternalSASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addExternalChangeListener(this);
    currentConfig = configuration;

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
    certificateAttributeType = configuration.getCertificateAttribute();
    if (certificateAttributeType == null)
    {
      certificateAttributeType =
           DirectoryServer.getAttributeTypeOrDefault(DEFAULT_VALIDATION_CERT_ATTRIBUTE);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_EXTERNAL, this);
  }



  /** {@inheritDoc} */
  @Override
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removeExternalChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_EXTERNAL);
  }




  /** {@inheritDoc} */
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
            if (!find(certAttrList, certBytes))
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
        if (certAttrList != null)
        {
          try
          {
            ByteString certBytes = ByteString.wrap(clientCertChain[0].getEncoded());
            if (!find(certAttrList, certBytes))
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



  private boolean find(List<Attribute> certAttrList, ByteString certBytes)
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



  /** {@inheritDoc} */
  @Override
  public boolean isPasswordBased(String mechanism)
  {
    // This is not a password-based mechanism.
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isSecure(String mechanism)
  {
    // This may be considered a secure mechanism.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    ExternalSASLMechanismHandlerCfg config =
         (ExternalSASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
                      ExternalSASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }



  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
              ExternalSASLMechanismHandlerCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


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
    AttributeType newCertificateType = configuration.getCertificateAttribute();
    if (newCertificateType == null)
    {
      newCertificateType =
           DirectoryServer.getAttributeTypeOrDefault(DEFAULT_VALIDATION_CERT_ATTRIBUTE);
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

