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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2021 Gluu, Inc.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.List;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.TrustManagerProviderCfg;
import org.forgerock.opendj.server.config.server.PKCS11TrustManagerProviderCfg;
import org.opends.server.api.TrustManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.util.ExpirationCheckTrustManager;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.FileBasedKeyManagerProvider.getKeyStorePIN;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class PKCS11TrustManagerProvider
       extends TrustManagerProvider<PKCS11TrustManagerProviderCfg>
       implements ConfigurationChangeListener<PKCS11TrustManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The truststore type to use when accessing the PKCS#11 keystore. */
  private static final String PKCS11_TRUSTSTORE_TYPE = "PKCS11";

  /** The PIN needed to access the trust store. */
  private char[] trustStorePIN;

  /** The handle to the configuration for this trust manager. */
  private PKCS11TrustManagerProviderCfg currentConfig;

  /**
   * Creates a new instance of this file-based trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public PKCS11TrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeTrustManagerProvider(PKCS11TrustManagerProviderCfg cfg)
          throws ConfigException, InitializationException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = cfg;
    trustStorePIN = getTrustStorePIN(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new InitializationException(ccr.getMessages().get(0));
    }

    cfg.addPKCS11ChangeListener(this);
  }

  @Override
  public void finalizeTrustManagerProvider()
  {
    currentConfig.removePKCS11ChangeListener(this);
  }

  @Override
  public TrustManager[] getTrustManagers() throws DirectoryException
  {
    KeyStore trustStore;
    try {
      trustStore = KeyStore.getInstance(PKCS11_TRUSTSTORE_TYPE);
      trustStore.load(null, trustStorePIN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_PKCS11_KEYMANAGER_CANNOT_LOAD.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

      return trustManagers;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
    		  ERR_PKCS11_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(TrustManagerProviderCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    PKCS11TrustManagerProviderCfg config = (PKCS11TrustManagerProviderCfg) cfg;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(PKCS11TrustManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getTrustStorePIN(cfg, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(PKCS11TrustManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    char[] newPIN = getTrustStorePIN(cfg, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      currentConfig = cfg;
      trustStorePIN   = newPIN;
    }

    return ccr;
  }

  private char[] getTrustStorePIN(PKCS11TrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      return FileBasedKeyManagerProvider.getKeyStorePIN(cfg.getTrustStorePinProperty(),
                            cfg.getTrustStorePinEnvironmentVariable(),
                            cfg.getTrustStorePinFile(),
                            cfg.getTrustStorePin(),
                            cfg.dn(),
                            ERR_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET,
                            ERR_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET,
                            ERR_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE,
                            ERR_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ,
                            ERR_FILE_TRUSTMANAGER_PIN_FILE_EMPTY);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
      return null;
    }
  }
}
