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
 *      Portions Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.DigestMD5SASLMechanismHandlerCfgDefn.QualityOfProtection;
import org.opends.server.admin.std.server.DigestMD5SASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

/**
 * This class provides an implementation of a SASL mechanism that authenticates
 * clients through DIGEST-MD5.
 */
public class DigestMD5SASLMechanismHandler
      extends SASLMechanismHandler<DigestMD5SASLMechanismHandlerCfg>
      implements ConfigurationChangeListener<DigestMD5SASLMechanismHandlerCfg> {

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration for this SASL mechanism handler. */
  private DigestMD5SASLMechanismHandlerCfg configuration;

  /** The identity mapper that will be used to map ID strings to user entries. */
  private IdentityMapper<?> identityMapper;

  /** Properties to use when creating a SASL server to process the authentication. */
  private HashMap<String,String> saslProps;

  /** The fully qualified domain name used when creating the SASL server. */
  private String serverFQDN;

  /** The DN of the configuration entry for this SASL mechanism handler. */
  private DN configEntryDN;

  /** Property used to set the realm in the environment. */
  private static final String REALM_PROPERTY = "com.sun.security.sasl.digest.realm";


  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public DigestMD5SASLMechanismHandler()
  {
    super();
  }


  /** {@inheritDoc} */
  @Override
  public void initializeSASLMechanismHandler(
          DigestMD5SASLMechanismHandlerCfg configuration)
  throws ConfigException, InitializationException {
      configuration.addDigestMD5ChangeListener(this);
      configEntryDN = configuration.dn();
      try {
         DN identityMapperDN = configuration.getIdentityMapperDN();
         identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
         serverFQDN = getFQDN(configuration);
         LocalizableMessage msg= NOTE_DIGEST_MD5_SERVER_FQDN.get(serverFQDN);
         logger.info(msg);
         String QOP = getQOP(configuration);
         saslProps = new HashMap<>();
         saslProps.put(Sasl.QOP, QOP);
         String realm=getRealm(configuration);
         if(realm != null) {
           msg = INFO_DIGEST_MD5_REALM.get(realm);
           logger.error(msg);
           saslProps.put(REALM_PROPERTY, getRealm(configuration));
         }
         this.configuration = configuration;
         DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5,
                  this);
      } catch (UnknownHostException unhe) {
          logger.traceException(unhe);
          LocalizableMessage message = ERR_SASL_CANNOT_GET_SERVER_FQDN.get(configEntryDN, getExceptionMessage(unhe));
          throw new InitializationException(message, unhe);
      }
  }


  /** {@inheritDoc} */
  @Override
  public void finalizeSASLMechanismHandler() {
    configuration.removeDigestMD5ChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);
  }


  /** {@inheritDoc} */
  @Override
  public void processSASLBind(BindOperation bindOp) {
      ClientConnection clientConnection = bindOp.getClientConnection();
      if (clientConnection == null) {
          LocalizableMessage message = ERR_SASLGSSAPI_NO_CLIENT_CONNECTION.get();
          bindOp.setAuthFailureReason(message);
          bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
          return;
      }
      ClientConnection clientConn  = bindOp.getClientConnection();
      SASLContext saslContext =
         (SASLContext) clientConn.getSASLAuthStateInfo();
      if(saslContext == null) {
          try {
            saslContext = SASLContext.createSASLContext(saslProps, serverFQDN,
                            SASL_MECHANISM_DIGEST_MD5, identityMapper);
          } catch (SaslException ex) {
              logger.traceException(ex);
              LocalizableMessage msg =
                  ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_DIGEST_MD5,
                                                    getExceptionMessage(ex));
              clientConn.setSASLAuthStateInfo(null);
              bindOp.setAuthFailureReason(msg);
              bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              return;
          }
          saslContext.evaluateInitialStage(bindOp);
      } else {
          saslContext.evaluateFinalStage(bindOp);
      }
  }


  /** {@inheritDoc} */
  @Override
  public boolean isPasswordBased(String mechanism)
  {
    // This is a password-based mechanism.
    return true;
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
    DigestMD5SASLMechanismHandlerCfg config =
         (DigestMD5SASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      DigestMD5SASLMechanismHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
          DigestMD5SASLMechanismHandlerCfg configuration)
  {
      final ConfigChangeResult ccr = new ConfigChangeResult();
      try {
          DN identityMapperDN = configuration.getIdentityMapperDN();
          identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
          serverFQDN = getFQDN(configuration);
          LocalizableMessage msg = NOTE_DIGEST_MD5_SERVER_FQDN.get(serverFQDN);
          logger.info(msg);
          String QOP = getQOP(configuration);
          saslProps = new HashMap<>();
          saslProps.put(Sasl.QOP, QOP);
          String realm=getRealm(configuration);
          if(realm != null) {
               msg = INFO_DIGEST_MD5_REALM.get(realm);
              logger.error(msg);
             saslProps.put(REALM_PROPERTY, getRealm(configuration));
          }
          this.configuration  = configuration;
      } catch (UnknownHostException unhe) {
          logger.traceException(unhe);
          ccr.setResultCode(ResultCode.OPERATIONS_ERROR);
          ccr.addMessage(ERR_SASL_CANNOT_GET_SERVER_FQDN.get(configEntryDN, getExceptionMessage(unhe)));
      }
      return ccr;
  }


  /**
   * Retrieves the QOP (quality-of-protection) from the specified
   * configuration.
   *
   * @param configuration The new configuration to use.
   * @return A string representing the quality-of-protection.
   */
  private String
  getQOP(DigestMD5SASLMechanismHandlerCfg configuration) {
      QualityOfProtection QOP = configuration.getQualityOfProtection();
      if(QOP.equals(QualityOfProtection.CONFIDENTIALITY)) {
        return "auth-conf";
      } else if(QOP.equals(QualityOfProtection.INTEGRITY)) {
        return "auth-int";
      } else {
        return "auth";
      }
  }


  /**
   * Returns the fully qualified name either defined in the configuration, or,
   * determined by examining the system configuration.
   *
   * @param configuration The configuration to check.
   * @return The fully qualified hostname of the server.
   *
   * @throws UnknownHostException If the name cannot be determined from the
   *                              system configuration.
   */
  private String getFQDN(DigestMD5SASLMechanismHandlerCfg configuration)
  throws UnknownHostException {
      String serverName = configuration.getServerFqdn();
      if (serverName == null) {
              serverName = InetAddress.getLocalHost().getCanonicalHostName();
      }
      return serverName;
  }


  /**
   * Retrieve the realm either defined in the specified configuration. If this
   * isn't defined, the SaslServer internal code uses the server name.
   *
   * @param configuration The configuration to check.
   * @return A string representing the realm.
   */
  private String getRealm(DigestMD5SASLMechanismHandlerCfg configuration) {
    return configuration.getRealm();
  }
}
