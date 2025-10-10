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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import com.forgerock.opendj.util.FipsStaticUtils;
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
import org.forgerock.opendj.server.config.server.FileBasedTrustManagerProviderCfg;
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

import static com.forgerock.opendj.util.StaticUtils.isFips;

/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class FileBasedTrustManagerProvider
       extends TrustManagerProvider<FileBasedTrustManagerProviderCfg>
       implements ConfigurationChangeListener<FileBasedTrustManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The PIN needed to access the trust store. */
  private char[] trustStorePIN;

  /** The handle to the configuration for this trust manager. */
  private FileBasedTrustManagerProviderCfg currentConfig;

  /** The path to the trust store backing file. */
  private String trustStoreFile;

  /** The trust store type to use. */
  private String trustStoreType;

  /**
   * Creates a new instance of this file-based trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public FileBasedTrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeTrustManagerProvider(FileBasedTrustManagerProviderCfg cfg)
          throws ConfigException, InitializationException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = cfg;
    trustStoreFile = getTrustStoreFile(cfg, ccr);
    trustStoreType = getTrustStoreType(cfg, ccr);
    trustStorePIN = getTrustStorePIN(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new InitializationException(ccr.getMessages().get(0));
    }

    cfg.addFileBasedChangeListener(this);
  }

  @Override
  public void finalizeTrustManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  @Override
  public TrustManager[] getTrustManagers() throws DirectoryException
  {
    KeyStore trustStore;
    try (FileInputStream inputStream = new FileInputStream(getFileForPath(trustStoreFile)))
    {
      trustStore = KeyStore.getInstance(trustStoreType);
      trustStore.load(inputStream, trustStorePIN);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_CANNOT_LOAD.get(trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      TrustManager[] newTrustManagers = new TrustManager[trustManagers.length];
      if (isFips()) {
    	  newTrustManagers = trustManagers;
      } else {
	      for (int i=0; i < trustManagers.length; i++)
	      {
	        newTrustManagers[i] = new ExpirationCheckTrustManager((X509TrustManager) trustManagers[i]);
	      }
      }
      return newTrustManagers;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
              ERR_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(TrustManagerProviderCfg cfg, List<LocalizableMessage> unacceptableReasons)
  {
    FileBasedTrustManagerProviderCfg config = (FileBasedTrustManagerProviderCfg) cfg;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(FileBasedTrustManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getTrustStoreFile(cfg, ccr);
    getTrustStoreType(cfg, ccr);
    getTrustStorePIN(cfg, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedTrustManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    String newTrustStoreFile = getTrustStoreFile(cfg, ccr);
    String newTrustStoreType = getTrustStoreType(cfg, ccr);
    char[] newPIN = getTrustStorePIN(cfg, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      currentConfig = cfg;
      trustStorePIN   = newPIN;
      trustStoreFile  = newTrustStoreFile;
      trustStoreType  = newTrustStoreType;
    }

    return ccr;
  }

  /** Get the path to the key store file. */
  private String getTrustStoreFile(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    final String keyStoreFile = cfg.getTrustStoreFile();
    final File f = getFileForPath(keyStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_TRUSTMANAGER_NO_SUCH_FILE.get(keyStoreFile, cfg.dn()));
    }
    return keyStoreFile;
  }

  /** Get the keystore type. If none is specified, then use the default type. */
  private String getTrustStoreType(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    final String trustStoreType = cfg.getTrustStoreType();
    if (trustStoreType != null)
    {
      if(trustStoreType.equals("BCFKS")) {
        FipsStaticUtils.registerBcProvider(true);
      }
      try
      {
        KeyStore.getInstance(trustStoreType);
        return trustStoreType;
      }
      catch (KeyStoreException e)
      {
        logger.traceException(e);
        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_TRUSTMANAGER_INVALID_TYPE.get(trustStoreType, cfg.dn(), getExceptionMessage(e)));
      }
    }
    return KeyStore.getDefaultType();
  }

  private char[] getTrustStorePIN(FileBasedTrustManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      return getKeyStorePIN(cfg.getTrustStorePinProperty(),
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
