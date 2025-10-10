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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import com.forgerock.opendj.util.FipsStaticUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg3;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class defines a key manager provider that will access keys stored in a
 * file located on the Directory Server filesystem.
 */
public class FileBasedKeyManagerProvider
       extends KeyManagerProvider<FileBasedKeyManagerProviderCfg>
       implements ConfigurationChangeListener<FileBasedKeyManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The configuration for this key manager provider. */
  private FileBasedKeyManagerProviderCfg currentConfig;

  /** The PIN needed to access the keystore. */
  private char[] keyStorePIN;
  /** The path to the key store backing file. */
  private String keyStoreFile;
  /** The key store type to use. */
  private String keyStoreType;

  /**
   * Creates a new instance of this file-based key manager provider.  The
   * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public FileBasedKeyManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeKeyManagerProvider(FileBasedKeyManagerProviderCfg cfg)
      throws ConfigException, InitializationException
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    currentConfig = cfg;
    keyStoreFile = getKeyStoreFile(cfg, ccr);
    keyStoreType = getKeyStoreType(cfg, ccr);
    keyStorePIN = getKeyStorePIN(cfg, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new InitializationException(ccr.getMessages().get(0));
    }

    cfg.addFileBasedChangeListener(this);
  }

  @Override
  public void finalizeKeyManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  @Override
  public boolean containsKeyWithAlias(String alias)
  {
    try
    {
      KeyStore keyStore = getKeystore();
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements())
      {
        String theAlias = aliases.nextElement();
        if (alias.equals(theAlias) && keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class))
        {
          return true;
        }
      }
    }
    catch (DirectoryException | KeyStoreException e)
    {
      // Ignore.
      logger.traceException(e);
    }
    return false;
  }

  private KeyStore getKeystore() throws DirectoryException
  {
    try
    {
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);

      try (FileInputStream inputStream = new FileInputStream(getFileForPath(keyStoreFile)))
      {
        keyStore.load(inputStream, keyStorePIN);
      }
      return keyStore;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public KeyManager[] getKeyManagers() throws DirectoryException
  {
    KeyStore keyStore = getKeystore();

    try
    {
      if (! findOneKeyEntry(keyStore))
      {
        // Troubleshooting message to let now of possible config error
        logger.error(ERR_NO_KEY_ENTRY_IN_KEYSTORE, keyStoreFile);
      }

      String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
      keyManagerFactory.init(keyStore, keyStorePIN);
      return keyManagerFactory.getKeyManagers();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY.get(keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getCoreConfigManager().getServerErrorResultCode(), message, e);
    }
  }

  @Override
  public boolean containsAtLeastOneKey()
  {
    try
    {
      return findOneKeyEntry(getKeystore());
    }
    catch (Exception e) {
      logger.traceException(e);
      return false;
    }
  }

  private boolean findOneKeyEntry(KeyStore keyStore) throws KeyStoreException
  {
    Enumeration<String> aliases = keyStore.aliases();
    while (aliases.hasMoreElements())
    {
      String alias = aliases.nextElement();
      if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isConfigurationAcceptable(FileBasedKeyManagerProviderCfg cfg,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(FileBasedKeyManagerProviderCfg cfg,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getKeyStoreFile(cfg, ccr);
    getKeyStoreType(cfg, ccr);
    getKeyStorePIN(cfg, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedKeyManagerProviderCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    String newKeyStoreFile = getKeyStoreFile(cfg, ccr);
    String newKeyStoreType = getKeyStoreType(cfg, ccr);
    char[] newPIN = getKeyStorePIN(cfg, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      currentConfig = cfg;
      keyStorePIN   = newPIN;
      keyStoreFile  = newKeyStoreFile;
      keyStoreType  = newKeyStoreType;
    }

    return ccr;
  }

  /** Get the path to the key store file. */
  private String getKeyStoreFile(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    String keyStoreFile = cfg.getKeyStoreFile();
    File f = getFileForPath(keyStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(keyStoreFile, cfg.dn()));
    }
    return keyStoreFile;
  }

  /** Get the keystore type. If none is specified, then use the default type. */
  private String getKeyStoreType(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    if (cfg.getKeyStoreType() != null)
    {
      try
      {
        if(cfg.getKeyStoreType().equals("BCFKS")) {
          FipsStaticUtils.registerBcProvider(true);
        }
        KeyStore.getInstance(cfg.getKeyStoreType());
        return cfg.getKeyStoreType();
      }
      catch (KeyStoreException e)
      {
        logger.traceException(e);
        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(cfg.getKeyStoreType(), cfg.dn(), getExceptionMessage(e)));
      }
    }
    return KeyStore.getDefaultType();
  }

  private char[] getKeyStorePIN(FileBasedKeyManagerProviderCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      return getKeyStorePIN(cfg.getKeyStorePinProperty(),
                            cfg.getKeyStorePinEnvironmentVariable(),
                            cfg.getKeyStorePinFile(),
                            cfg.getKeyStorePin(),
                            cfg.dn(),
                            ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET,
                            ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET,
                            ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE,
                            ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ,
                            ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY);
    }
    catch (InitializationException e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(e.getMessageObject());
      return null;
    }
  }

  /**
   * Returns the PIN needed to access the contents of a key store. We will offer several places to look for the PIN,
   * and we will do so in the following order:
   * <ol>
   *     <li>In a specified Java property</li>
   *     <li>In a specified environment variable</li>
   *     <li>In a specified file on the server filesystem</li>
   *     <li>As the value of a configuration attribute.</li>
   * </ol>
   * In any case, the PIN must be in the clear.
   * <p>
   * It is acceptable to have no PIN (OPENDJ-18).
   */
  static char[] getKeyStorePIN(final String pinProperty, final String pinEnvVar, final String pinFileName,
                               final String pinString, final DN cfgDN, final Arg2<Object, Object> propertyNotSetMsg,
                               final Arg2<Object, Object> envVarNotSetMsg, final Arg2<Object, Object> noSuchFileMsg,
                               final Arg3<Object, Object, Object> fileCannotReadMsg,
                               final Arg2<Object, Object> fileEmptyMsg) throws InitializationException
  {
    if (pinProperty != null)
    {
      final String pin = System.getProperty(pinProperty);
      if (pin == null)
      {
        throw new InitializationException(propertyNotSetMsg.get(pinProperty, cfgDN));
      }
      return pin.toCharArray();
    }

    if (pinEnvVar != null)
    {
      final String pin = System.getenv(pinEnvVar);
      if (pin == null)
      {
        throw new InitializationException(envVarNotSetMsg.get(pinEnvVar, cfgDN));
      }
      return pin.toCharArray();
    }

    if (pinFileName != null)
    {
      final File pinFile = getFileForPath(pinFileName);
      if (pinFile.exists())
      {
        String pin;
        try (BufferedReader br = new BufferedReader(new FileReader(pinFile)))
        {
          pin = br.readLine();
        }
        catch (IOException e)
        {
          final LocalizableMessage msg = fileCannotReadMsg.get(pinFileName, cfgDN, getExceptionMessage(e));
          throw new InitializationException(msg, e);
        }
        if (pin == null)
        {
          throw new InitializationException(fileEmptyMsg.get(pinFileName, cfgDN));
        }
        return pin.toCharArray();
      }
      else
      {
        throw new InitializationException(noSuchFileMsg.get(pinFileName, cfgDN));
      }
    }

    return pinString != null ? pinString.toCharArray() : null;
  }
}
