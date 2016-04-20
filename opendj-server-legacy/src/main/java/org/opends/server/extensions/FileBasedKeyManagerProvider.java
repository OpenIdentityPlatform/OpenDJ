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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.DN;
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

  /** The DN of the configuration entry for this key manager provider. */
  private DN configEntryDN;
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
  public void initializeKeyManagerProvider(
      FileBasedKeyManagerProviderCfg configuration)
      throws ConfigException, InitializationException {
    // Store the DN of the configuration entry and register as a change listener
    currentConfig = configuration;
    configEntryDN = configuration.dn();
    configuration.addFileBasedChangeListener(this);

    final ConfigChangeResult ccr = new ConfigChangeResult();
    keyStoreFile = getKeyStoreFile(configuration, configEntryDN, ccr);
    keyStoreType = getKeyStoreType(configuration, configEntryDN, ccr);
    keyStorePIN = getKeyStorePIN(configuration, configEntryDN, ccr);
    if (!ccr.getMessages().isEmpty()) {
      throw new InitializationException(ccr.getMessages().get(0));
    }
  }

  @Override
  public void finalizeKeyManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  @Override
  public boolean containsKeyWithAlias(String alias) {
    try {
      KeyStore keyStore = getKeystore();
      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String theAlias = aliases.nextElement();
        if (alias.equals(theAlias) && keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
          return true;
        }
      }
    }
    catch (DirectoryException | KeyStoreException e) {
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
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(
              keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
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
      KeyManagerFactory keyManagerFactory =
           KeyManagerFactory.getInstance(keyManagerAlgorithm);
      keyManagerFactory.init(keyStore, keyStorePIN);
      return keyManagerFactory.getKeyManagers();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY.get(
          keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
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
  public boolean isConfigurationAcceptable(
                        FileBasedKeyManagerProviderCfg configuration,
                        List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      FileBasedKeyManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();
    DN cfgEntryDN = configuration.dn();

    final ConfigChangeResult ccr = new ConfigChangeResult();
    getKeyStoreFile(configuration, cfgEntryDN, ccr);
    getKeyStoreType(configuration, cfgEntryDN, ccr);
    getKeyStorePIN(configuration, cfgEntryDN, ccr);
    unacceptableReasons.addAll(ccr.getMessages());

    return startSize == unacceptableReasons.size();
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 FileBasedKeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();
    String newKeyStoreFile = getKeyStoreFile(configuration, configEntryDN, ccr);
    String newKeyStoreType = getKeyStoreType(configuration, configEntryDN, ccr);
    char[] newPIN = getKeyStorePIN(configuration, configEntryDN, ccr);

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      currentConfig = configuration;
      keyStorePIN   = newPIN;
      keyStoreFile  = newKeyStoreFile;
      keyStoreType  = newKeyStoreType;
    }

    return ccr;
  }

  /** Get the path to the key store file. */
  private String getKeyStoreFile(FileBasedKeyManagerProviderCfg configuration, DN cfgEntryDN,
      final ConfigChangeResult ccr)
  {
    String keyStoreFile = configuration.getKeyStoreFile();
    try
    {
      File f = getFileForPath(keyStoreFile);
      if (!f.exists() || !f.isFile())
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(keyStoreFile, cfgEntryDN));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(cfgEntryDN, getExceptionMessage(e)));
    }
    return keyStoreFile;
  }

  /** Get the keystore type. If none is specified, then use the default type. */
  private String getKeyStoreType(FileBasedKeyManagerProviderCfg configuration, DN cfgEntryDN,
      final ConfigChangeResult ccr)
  {
    if (configuration.getKeyStoreType() != null)
    {
      try
      {
        KeyStore.getInstance(configuration.getKeyStoreType());
        return configuration.getKeyStoreType();
      }
      catch (KeyStoreException kse)
      {
        logger.traceException(kse);

        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(
            configuration.getKeyStoreType(), cfgEntryDN, getExceptionMessage(kse)));
      }
    }
    return KeyStore.getDefaultType();
  }

  /**
   * Get the PIN needed to access the contents of the keystore file.
   * <p>
   * We will offer several places to look for the PIN, and we will do so in the following order:
   * <ol>
   * <li>In a specified Java property</li>
   * <li>In a specified environment variable</li>
   * <li>In a specified file on the server filesystem</li>
   * <li>As the value of a configuration attribute.</li>
   * <ol>
   * In any case, the PIN must be in the clear.
   * <p>
   * It is acceptable to have no PIN (OPENDJ-18)
   */
  private char[] getKeyStorePIN(FileBasedKeyManagerProviderCfg configuration, DN cfgEntryDN,
      final ConfigChangeResult ccr)
  {
    if (configuration.getKeyStorePinProperty() != null)
    {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(propertyName, cfgEntryDN));
      }
      else
      {
        return pinStr.toCharArray();
      }
    }
    else if (configuration.getKeyStorePinEnvironmentVariable() != null)
    {
      String enVarName = configuration.getKeyStorePinEnvironmentVariable();
      String pinStr    = System.getenv(enVarName);

      if (pinStr == null)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(enVarName, cfgEntryDN));
      }
      else
      {
        return pinStr.toCharArray();
      }
    }
    else if (configuration.getKeyStorePinFile() != null)
    {
      String fileName = configuration.getKeyStorePinFile();
      File   pinFile  = getFileForPath(fileName);

      if (!pinFile.exists())
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, cfgEntryDN));
      }
      else
      {
        String pinStr = readPinFromFile(pinFile, fileName, ccr);
        if (pinStr == null)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, cfgEntryDN));
        }
        else
        {
          return pinStr.toCharArray();
        }
      }
    }
    else if (configuration.getKeyStorePin() != null)
    {
      return configuration.getKeyStorePin().toCharArray();
    }
    return null;
  }

  private String readPinFromFile(File pinFile, String fileName, ConfigChangeResult ccr)
  {
    try (BufferedReader br = new BufferedReader(new FileReader(pinFile)))
    {
      return br.readLine();
    }
    catch (IOException ioe)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(fileName, configEntryDN, getExceptionMessage(ioe)));
      return null;
    }
  }
}
