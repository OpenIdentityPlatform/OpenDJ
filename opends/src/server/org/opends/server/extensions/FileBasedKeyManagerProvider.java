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
import org.opends.messages.Message;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FileBasedKeyManagerCfg;
import org.opends.server.admin.std.server.KeyManagerCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.messages.ExtensionMessages.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a key manager provider that will access keys stored in a
 * file located on the Directory Server filesystem.
 */
public class FileBasedKeyManagerProvider
       extends KeyManagerProvider<FileBasedKeyManagerCfg>
       implements ConfigurationChangeListener<FileBasedKeyManagerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The DN of the configuration entry for this key manager provider.
  private DN configEntryDN;

  // The PIN needed to access the keystore.
  private char[] keyStorePIN;

  // The configuration for this key manager provider.
  private FileBasedKeyManagerCfg currentConfig;

  // The path to the key store backing file.
  private String keyStoreFile;

  // The key store type to use.
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



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeKeyManagerProvider(
      FileBasedKeyManagerCfg configuration)
      throws ConfigException, InitializationException {
    // Store the DN of the configuration entry and register as a change
    // listener.
    currentConfig = configuration;
    configEntryDN = configuration.dn();
    configuration.addFileBasedChangeListener(this);


    // Get the path to the key store file.
    keyStoreFile = configuration.getKeyStoreFile();
    try {
      File f = getFileForPath(keyStoreFile);
      if (!(f.exists() && f.isFile())) {
        Message message = ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(
            String.valueOf(keyStoreFile), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }
    } catch (SecurityException e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(
          String.valueOf(configEntryDN), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    // Get the keystore type. If none is specified, then use the
    // default type.
    if (configuration.getKeyStoreType() != null) {
      try {
        KeyStore.getInstance(configuration.getKeyStoreType());
        keyStoreType = configuration.getKeyStoreType();
      } catch (KeyStoreException kse) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, kse);
        }

        Message message = ERR_FILE_KEYMANAGER_INVALID_TYPE.
            get(String.valueOf(configuration.getKeyStoreType()),
                String.valueOf(configEntryDN), getExceptionMessage(kse));
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
        Message message = ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
            String.valueOf(propertyName), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinEnvironmentVariable() != null) {
      String enVarName = configuration
          .getKeyStorePinEnvironmentVariable();
      String pinStr = System.getenv(enVarName);

      if (pinStr == null) {
        Message message = ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(
            String.valueOf(enVarName), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinFile() != null) {
      String fileName = configuration.getKeyStorePinFile();
      File pinFile = getFileForPath(fileName);

      if (!pinFile.exists()) {
        Message message = ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(
            String.valueOf(fileName), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }

      String pinStr;
      try {
        BufferedReader br = new BufferedReader(
            new FileReader(pinFile));
        pinStr = br.readLine();
        br.close();
      } catch (IOException ioe) {
        Message message = ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.
            get(String.valueOf(fileName), String.valueOf(configEntryDN),
                getExceptionMessage(ioe));
        throw new InitializationException(message, ioe);
      }

      if (pinStr == null) {
        Message message = ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(
            String.valueOf(fileName), String.valueOf(configEntryDN));
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePin() != null) {
      keyStorePIN = configuration.getKeyStorePin().toCharArray();
    } else {
      // Pin wasn't defined anywhere.
      Message message =
          ERR_FILE_KEYMANAGER_NO_PIN.get(String.valueOf(configEntryDN));
      throw new ConfigException(message);
    }
  }



  /**
   * Performs any finalization that may be necessary for this key
   * manager provider.
   */
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
  public KeyManager[] getKeyManagers()
         throws DirectoryException
  {
    KeyStore keyStore;
    try
    {
      keyStore = KeyStore.getInstance(keyStoreType);

      FileInputStream inputStream =
           new FileInputStream(getFileForPath(keyStoreFile));
      keyStore.load(inputStream, keyStorePIN);
      inputStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_FILE_KEYMANAGER_CANNOT_LOAD.get(
          keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_FILE_KEYMANAGER_CANNOT_CREATE_FACTORY.get(
          keyStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(KeyManagerCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    FileBasedKeyManagerCfg config = (FileBasedKeyManagerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      FileBasedKeyManagerCfg configuration,
                      List<Message> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();


    // Get the path to the key store file.
    String newKeyStoreFile = configuration.getKeyStoreFile();
    try
    {
      File f = getFileForPath(newKeyStoreFile);
      if (!(f.exists() && f.isFile()))
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(
                String.valueOf(newKeyStoreFile),
                String.valueOf(cfgEntryDN)));
        configAcceptable = false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      unacceptableReasons.add(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(
              String.valueOf(cfgEntryDN),
              getExceptionMessage(e)));
      configAcceptable = false;
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, kse);
        }

        unacceptableReasons.add(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(
                String.valueOf(configuration.getKeyStoreType()),
               String.valueOf(cfgEntryDN), getExceptionMessage(kse)));
        configAcceptable = false;
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
    if (configuration.getKeyStorePinProperty() != null)
    {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
                String.valueOf(propertyName),
                String.valueOf(cfgEntryDN)));
        configAcceptable = false;
      }
    }
    else if (configuration.getKeyStorePinEnvironmentVariable() != null)
    {
      String enVarName = configuration.getKeyStorePinEnvironmentVariable();
      String pinStr    = System.getenv(enVarName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(
                String.valueOf(enVarName),
                String.valueOf(cfgEntryDN)));
        configAcceptable = false;
      }
    }
    else if (configuration.getKeyStorePinFile() != null)
    {
      String fileName = configuration.getKeyStorePinFile();
      File   pinFile  = getFileForPath(fileName);

      if (!pinFile.exists())
      {
        unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(
                String.valueOf(fileName),
                String.valueOf(cfgEntryDN)));
        configAcceptable = false;
      }
      else
      {
        String pinStr = null;
        BufferedReader br = null;
        try {
          br = new BufferedReader(new FileReader(pinFile));
          pinStr = br.readLine();
        }
        catch (IOException ioe)
        {
          unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(
                  String.valueOf(fileName),
                  String.valueOf(cfgEntryDN),
                  getExceptionMessage(ioe)));
          configAcceptable = false;
        }
        finally
        {
          try
          {
            br.close();
          } catch (Exception e) {}
        }

        if (pinStr == null)
        {
          unacceptableReasons.add(ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(
                  String.valueOf(fileName),
                  String.valueOf(cfgEntryDN)));
          configAcceptable = false;
        }
      }
    }
    else if (configuration.getKeyStorePin() != null)
    {
      configuration.getKeyStorePin().toCharArray();
    }
    else
    {
      // Pin wasn't defined anywhere.
      unacceptableReasons.add(ERR_FILE_KEYMANAGER_NO_PIN.get(
              String.valueOf(cfgEntryDN)));
      configAcceptable = false;
    }

    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 FileBasedKeyManagerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Get the path to the key store file.
    String newKeyStoreFile = configuration.getKeyStoreFile();
    try
    {
      File f = getFileForPath(newKeyStoreFile);
      if (!(f.exists() && f.isFile()))
      {
        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_FILE_KEYMANAGER_NO_SUCH_FILE.get(
                String.valueOf(newKeyStoreFile),
                String.valueOf(configEntryDN)));
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      resultCode = DirectoryServer.getServerErrorResultCode();

      messages.add(ERR_FILE_KEYMANAGER_CANNOT_DETERMINE_FILE.get(
              String.valueOf(configEntryDN),
              getExceptionMessage(e)));
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, kse);
        }

        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_FILE_KEYMANAGER_INVALID_TYPE.get(
                String.valueOf(configuration.getKeyStoreType()),
                String.valueOf(configEntryDN),
                getExceptionMessage(kse)));
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
        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_FILE_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
                String.valueOf(propertyName),
                String.valueOf(configEntryDN)));
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
        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_FILE_KEYMANAGER_PIN_ENVAR_NOT_SET.get(
                String.valueOf(enVarName),
                String.valueOf(configEntryDN)));
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
        resultCode = DirectoryServer.getServerErrorResultCode();

        messages.add(ERR_FILE_KEYMANAGER_PIN_NO_SUCH_FILE.get(
                String.valueOf(fileName),
                String.valueOf(configEntryDN)));
      }
      else
      {
        String pinStr = null;
        BufferedReader br = null;
        try {
          br = new BufferedReader(new FileReader(pinFile));
          pinStr = br.readLine();
        }
        catch (IOException ioe)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_FILE_KEYMANAGER_PIN_FILE_CANNOT_READ.get(
                  String.valueOf(fileName),
                  String.valueOf(configEntryDN),
                  getExceptionMessage(ioe)));
        }
        finally
        {
          try
          {
            br.close();
          } catch (Exception e) {}
        }

        if (pinStr == null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();

          messages.add(ERR_FILE_KEYMANAGER_PIN_FILE_EMPTY.get(
                  String.valueOf(fileName),
                  String.valueOf(configEntryDN)));
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
    else
    {
      // Pin wasn't defined anywhere.
      resultCode = DirectoryServer.getServerErrorResultCode();

      messages.add(ERR_FILE_KEYMANAGER_NO_PIN.get(
              String.valueOf(configEntryDN)));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      currentConfig = configuration;
      keyStorePIN   = newPIN;
      keyStoreFile  = newKeyStoreFile;
      keyStoreType  = newKeyStoreType;
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

