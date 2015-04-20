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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
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
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FileBasedKeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
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

  /** The PIN needed to access the keystore. */
  private char[] keyStorePIN;

  /** The configuration for this key manager provider. */
  private FileBasedKeyManagerProviderCfg currentConfig;

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



  /** {@inheritDoc} */
  @Override
  public void initializeKeyManagerProvider(
      FileBasedKeyManagerProviderCfg configuration)
      throws ConfigException, InitializationException {
    // Store the DN of the configuration entry and register as a change listener
    currentConfig = configuration;
    configEntryDN = configuration.dn();
    configuration.addFileBasedChangeListener(this);


    // Get the path to the key store file.
    keyStoreFile = configuration.getKeyStoreFile();
    try {
      File f = getFileForPath(keyStoreFile);
      if (!f.exists() || !f.isFile()) {
        throw new InitializationException(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(keyStoreFile, configEntryDN));
      }
    } catch (SecurityException e) {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(
          configEntryDN, getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    // Get the keystore type. If none is specified, then use the
    // default type.
    if (configuration.getKeyStoreType() != null) {
      try {
        KeyStore.getInstance(configuration.getKeyStoreType());
        keyStoreType = configuration.getKeyStoreType();
      } catch (KeyStoreException kse) {
        logger.traceException(kse);

        LocalizableMessage message = ERR_FILE_KEYMANAGER_INVALID_TYPE.
            get(configuration.getKeyStoreType(), configEntryDN, getExceptionMessage(kse));
        throw new InitializationException(message);
      }
    } else {
      keyStoreType = KeyStore.getDefaultType();
    }

    // Get the PIN needed to access the contents of the keystore file.
    //
    // We will offer several places to look for the PIN, and we will
    // do so in the following order:
    //
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    //
    // In any case, the PIN must be in the clear.
    keyStorePIN = null;

    if (configuration.getKeyStorePinProperty() != null) {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null) {
        LocalizableMessage message = ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
            propertyName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinEnvironmentVariable() != null) {
      String enVarName = configuration
          .getKeyStorePinEnvironmentVariable();
      String pinStr = System.getenv(enVarName);

      if (pinStr == null) {
        LocalizableMessage message = ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(
            enVarName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinFile() != null) {
      String fileName = configuration.getKeyStorePinFile();
      File pinFile = getFileForPath(fileName);

      if (!pinFile.exists()) {
        LocalizableMessage message = ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(
            fileName, configEntryDN);
        throw new InitializationException(message);
      }

      String pinStr = readPinFromFile(fileName, pinFile);
      if (pinStr == null) {
        LocalizableMessage message = ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePin() != null) {
      keyStorePIN = configuration.getKeyStorePin().toCharArray();
    }
  }

  private String readPinFromFile(String fileName, File pinFile) throws InitializationException
  {
    BufferedReader br = null;
    try
    {
      br = new BufferedReader(new FileReader(pinFile));
      return br.readLine();
    }
    catch (IOException ioe)
    {
      LocalizableMessage message =
          ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(fileName, configEntryDN, getExceptionMessage(ioe));
      throw new InitializationException(message, ioe);
    }
    finally
    {
      close(br);
    }
  }

  /** Performs any finalization that may be necessary for this key manager provider. */
  @Override
  public void finalizeKeyManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }



  /**
   * Retrieves a set of <CODE>KeyManager</CODE> objects that may be used for
   * interactions requiring access to a key manager.
   *
   * @return  A set of <CODE>KeyManager</CODE> objects that may be used for
   *          interactions requiring access to a key manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to obtain
   *                              the set of key managers.
   */
  @Override
  public KeyManager[] getKeyManagers() throws DirectoryException
  {
    KeyStore keyStore;
    try
    {
      keyStore = KeyStore.getInstance(keyStoreType);

      FileInputStream inputStream =
           new FileInputStream(getFileForPath(keyStoreFile));
      try
      {
        keyStore.load(inputStream, keyStorePIN);
      }
      finally
      {
        close(inputStream);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(
          keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    try {
      // Troubleshooting aid; Analyse the keystore for the presence of at least one private entry.
      if (!findOneKeyEntry(keyStore))
      {
        logger.warn(INFO_NO_KEY_ENTRY_IN_KEYSTORE, keyStoreFile);
      }
    }
    catch (Exception e) {
      logger.traceException(e);
    }

    try
    {
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
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
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

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
                        FileBasedKeyManagerProviderCfg configuration,
                        List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
                      FileBasedKeyManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    int startSize = unacceptableReasons.size();
    DN cfgEntryDN = configuration.dn();


    // Get the path to the key store file.
    String newKeyStoreFile = configuration.getKeyStoreFile();
    try
    {
      File f = getFileForPath(newKeyStoreFile);
      if (!f.exists() || !f.isFile())
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(newKeyStoreFile, cfgEntryDN));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(cfgEntryDN, getExceptionMessage(e)));
    }

    // Get the keystore type. If none is specified, then use the default type.
    if (configuration.getKeyStoreType() != null)
    {
      try
      {
        KeyStore.getInstance(configuration.getKeyStoreType());
      }
      catch (KeyStoreException kse)
      {
        logger.traceException(kse);

        unacceptableReasons.add(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(
            configuration.getKeyStoreType(), cfgEntryDN, getExceptionMessage(kse)));
      }
    }

    // Get the PIN needed to access the contents of the keystore file.
    //
    // We will offer several places to look for the PIN, and we will
    // do so in the following order:
    //
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    //
    // In any case, the PIN must be in the clear.
    // It is acceptable to have no PIN (OPENDJ-18)
    if (configuration.getKeyStorePinProperty() != null)
    {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(propertyName, cfgEntryDN));
      }
    }
    else if (configuration.getKeyStorePinEnvironmentVariable() != null)
    {
      String enVarName = configuration.getKeyStorePinEnvironmentVariable();
      String pinStr    = System.getenv(enVarName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(enVarName, cfgEntryDN));
      }
    }
    else if (configuration.getKeyStorePinFile() != null)
    {
      String fileName = configuration.getKeyStorePinFile();
      File   pinFile  = getFileForPath(fileName);

      if (!pinFile.exists())
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, cfgEntryDN));
      }
      else
      {
        String pinStr = readPinFromFile(pinFile, fileName, cfgEntryDN, unacceptableReasons);
        if (pinStr == null)
        {
          unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, cfgEntryDN));
        }
      }
    }
    else if (configuration.getKeyStorePin() != null)
    {
      String pinStr = configuration.getKeyStorePin();
      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR.get(cfgEntryDN, null));
      }
    }

    return startSize != unacceptableReasons.size();
  }

  private String readPinFromFile(File pinFile, String fileName, DN cfgEntryDN,
      List<LocalizableMessage> unacceptableReasons)
  {
    BufferedReader br = null;
    try
    {
      br = new BufferedReader(new FileReader(pinFile));
      return br.readLine();
    }
    catch (IOException ioe)
    {
      unacceptableReasons.add(
          ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(fileName, cfgEntryDN, getExceptionMessage(ioe)));
      return null;
    }
    finally
    {
      close(br);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 FileBasedKeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Get the path to the key store file.
    String newKeyStoreFile = configuration.getKeyStoreFile();
    try
    {
      File f = getFileForPath(newKeyStoreFile);
      if (!f.exists() || !f.isFile())
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(newKeyStoreFile, configEntryDN));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(
          configEntryDN, getExceptionMessage(e)));
    }

    // Get the keystore type. If none is specified, then use the default type.
    String newKeyStoreType = KeyStore.getDefaultType();
    if (configuration.getKeyStoreType() != null)
    {
      try
      {
        KeyStore.getInstance(configuration.getKeyStoreType());
        newKeyStoreType = configuration.getKeyStoreType();
      }
      catch (KeyStoreException kse)
      {
        logger.traceException(kse);

        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(
            configuration.getKeyStoreType(), configEntryDN, getExceptionMessage(kse)));
      }
    }

    // Get the PIN needed to access the contents of the keystore file.
    //
    // We will offer several places to look for the PIN, and we will
    // do so in the following order:
    //
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    //
    // In any case, the PIN must be in the clear.
    char[] newPIN = null;

    if (configuration.getKeyStorePinProperty() != null)
    {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
            propertyName, configEntryDN));
      }
      else
      {
        newPIN = pinStr.toCharArray();
      }
    }
    else if (configuration.getKeyStorePinEnvironmentVariable() != null)
    {
      String enVarName = configuration.getKeyStorePinEnvironmentVariable();
      String pinStr    = System.getenv(enVarName);

      if (pinStr == null)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(enVarName, configEntryDN));
      }
      else
      {
        newPIN = pinStr.toCharArray();
      }
    }
    else if (configuration.getKeyStorePinFile() != null)
    {
      String fileName = configuration.getKeyStorePinFile();
      File   pinFile  = getFileForPath(fileName);

      if (!pinFile.exists())
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, configEntryDN));
      }
      else
      {
        String pinStr = readPinFromFile(pinFile, fileName, ccr);
        if (pinStr == null)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, configEntryDN));
        }
        else
        {
          newPIN = pinStr.toCharArray();
        }
      }
    }
    else if (configuration.getKeyStorePin() != null)
    {
      newPIN = configuration.getKeyStorePin().toCharArray();
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      currentConfig = configuration;
      keyStorePIN   = newPIN;
      keyStoreFile  = newKeyStoreFile;
      keyStoreType  = newKeyStoreType;
    }

    return ccr;
  }

  private String readPinFromFile(File pinFile, String fileName, ConfigChangeResult ccr)
  {
    BufferedReader br = null;
    try
    {
      br = new BufferedReader(new FileReader(pinFile));
      return br.readLine();
    }
    catch (IOException ioe)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(fileName, configEntryDN, getExceptionMessage(ioe)));
      return null;
    }
    finally
    {
      close(br);
    }
  }
}
