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

import org.forgerock.i18n.LocalizableMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.*;
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
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.util.ExpirationCheckTrustManager;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class FileBasedTrustManagerProvider
       extends TrustManagerProvider<FileBasedTrustManagerProviderCfg>
       implements ConfigurationChangeListener<FileBasedTrustManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the configuration entry for this trust manager provider. */
  private DN configEntryDN;

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
  public void initializeTrustManagerProvider(
                   FileBasedTrustManagerProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // Store the DN of the configuration entry and register to listen for any
    // changes to the configuration entry.
    currentConfig = configuration;
    configEntryDN = configuration.dn();
    configuration.addFileBasedChangeListener(this);

    // Get the path to the trust store file.
    trustStoreFile = configuration.getTrustStoreFile();
    File f = getFileForPath(trustStoreFile);
    if (!f.exists() || !f.isFile())
    {
      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_NO_SUCH_FILE.get(trustStoreFile, configEntryDN);
      throw new InitializationException(message);
    }

    // Get the trust store type.  If none is specified, then use the default
    // type.
    trustStoreType = configuration.getTrustStoreType();
    if (trustStoreType == null)
    {
      trustStoreType = KeyStore.getDefaultType();
    }

    try
    {
      KeyStore.getInstance(trustStoreType);
    }
    catch (KeyStoreException kse)
    {
      logger.traceException(kse);

      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_INVALID_TYPE.
          get(trustStoreType, configEntryDN, getExceptionMessage(kse));
      throw new InitializationException(message);
    }

    // Get the PIN needed to access the contents of the trust store file.  We
    // will offer several places to look for the PIN, and we will do so in the
    // following order:
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    // In any case, the PIN must be in the clear.  If no PIN is provided, then
    // it will be assumed that none is required to access the information in the
    // trust store.
    String pinProperty = configuration.getTrustStorePinProperty();
    if (pinProperty == null)
    {
      String pinEnVar = configuration.getTrustStorePinEnvironmentVariable();
      if (pinEnVar == null)
      {
        String pinFilePath = configuration.getTrustStorePinFile();
        if (pinFilePath == null)
        {
          String pinStr = configuration.getTrustStorePin();
          if (pinStr == null)
          {
            trustStorePIN = null;
          }
          else
          {
            trustStorePIN = pinStr.toCharArray();
          }
        }
        else
        {
          File pinFile = getFileForPath(pinFilePath);
          if (! pinFile.exists())
          {
            LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE.get(pinFilePath, configEntryDN);
            throw new InitializationException(message);
          }
          else
          {
            String pinStr;

            BufferedReader br = null;
            try
            {
              br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
            }
            catch (IOException ioe)
            {
              LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ.
                  get(pinFilePath, configEntryDN, getExceptionMessage(ioe));
              throw new InitializationException(message, ioe);
            }
            finally
            {
              close(br);
            }

            if (pinStr == null)
            {
              LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_FILE_EMPTY.get(pinFilePath, configEntryDN);
              throw new InitializationException(message);
            }
            else
            {
              trustStorePIN     = pinStr.toCharArray();
            }
          }
        }
      }
      else
      {
        String pinStr = System.getenv(pinEnVar);
        if (pinStr == null)
        {
          LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET.get(pinProperty, configEntryDN);
          throw new InitializationException(message);
        }
        else
        {
          trustStorePIN = pinStr.toCharArray();
        }
      }
    }
    else
    {
      String pinStr = System.getProperty(pinProperty);
      if (pinStr == null)
      {
        LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET.get(pinProperty, configEntryDN);
        throw new InitializationException(message);
      }
      else
      {
        trustStorePIN = pinStr.toCharArray();
      }
    }
  }

  @Override
  public void finalizeTrustManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }

  @Override
  public TrustManager[] getTrustManagers()
         throws DirectoryException
  {
    KeyStore trustStore;
    try
    {
      trustStore = KeyStore.getInstance(trustStoreType);

      FileInputStream inputStream =
           new FileInputStream(getFileForPath(trustStoreFile));
      trustStore.load(inputStream, trustStorePIN);
      inputStream.close();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_CANNOT_LOAD.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory =
           TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      TrustManager[] newTrustManagers = new TrustManager[trustManagers.length];
      for (int i=0; i < trustManagers.length; i++)
      {
        newTrustManagers[i] = new ExpirationCheckTrustManager(
                                       (X509TrustManager) trustManagers[i]);
      }
      return newTrustManagers;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY.get(
          trustStoreFile, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(
                         TrustManagerProviderCfg configuration,
                         List<LocalizableMessage> unacceptableReasons)
  {
    FileBasedTrustManagerProviderCfg config =
            (FileBasedTrustManagerProviderCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      FileBasedTrustManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    try
    {
      File f = getFileForPath(newTrustStoreFile);
      if (!f.exists() || !f.isFile())
      {
        unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_NO_SUCH_FILE.get(newTrustStoreFile, cfgEntryDN));
        configAcceptable = false;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE.get(cfgEntryDN, getExceptionMessage(e)));
      configAcceptable = false;
    }

    // Check to see if the trust store type is acceptable.
    String storeType = configuration.getTrustStoreType();
    if (storeType != null)
    {
      try
      {
        KeyStore.getInstance(storeType);
      }
      catch (KeyStoreException kse)
      {
        logger.traceException(kse);

        unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_INVALID_TYPE.get(
            storeType, cfgEntryDN, getExceptionMessage(kse)));
        configAcceptable = false;
      }
    }

    // If there is a PIN property, then make sure the corresponding
    // property is set.
    String pinProp = configuration.getTrustStorePinProperty();
    if (pinProp != null && System.getProperty(pinProp) == null)
    {
      unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET.get(pinProp, cfgEntryDN));
      configAcceptable = false;
    }

    // If there is a PIN environment variable, then make sure the corresponding
    // environment variable is set.
    String pinEnVar = configuration.getTrustStorePinEnvironmentVariable();
    if (pinEnVar != null && System.getenv(pinEnVar) == null)
    {
      unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET.get(pinEnVar, cfgEntryDN));
      configAcceptable = false;
    }

    // If there is a PIN file, then make sure the file exists and is readable.
    String pinFile = configuration.getTrustStorePinFile();
    if (pinFile != null)
    {
      File f = getFileForPath(pinFile);
      if (f.exists())
      {
        String pinStr = null;

        BufferedReader br = null;
        try
        {
          br = new BufferedReader(new FileReader(f));
          pinStr = br.readLine();
        }
        catch (IOException ioe)
        {
          unacceptableReasons.add(ERR_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ.get(
              pinFile, cfgEntryDN, getExceptionMessage(ioe)));
          configAcceptable = false;
        }
        finally
        {
          close(br);
        }

        if (pinStr == null)
        {
          LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_FILE_EMPTY.get(pinFile, cfgEntryDN);
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
      }
      else
      {
        LocalizableMessage message = ERR_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE.get(pinFile, cfgEntryDN);
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 FileBasedTrustManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    File f = getFileForPath(newTrustStoreFile);
    if (!f.exists() || !f.isFile())
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_FILE_TRUSTMANAGER_NO_SUCH_FILE.get(newTrustStoreFile, configEntryDN));
    }

    // Get the trust store type.  If none is specified, then use the default type.
    String newTrustStoreType = configuration.getTrustStoreType();
    if (newTrustStoreType == null)
    {
      newTrustStoreType = KeyStore.getDefaultType();
    }

    try
    {
      KeyStore.getInstance(newTrustStoreType);
    }
    catch (KeyStoreException kse)
    {
      logger.traceException(kse);

      ccr.addMessage(ERR_FILE_TRUSTMANAGER_INVALID_TYPE.get(
          newTrustStoreType, configEntryDN, getExceptionMessage(kse)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }

    // Get the PIN needed to access the contents of the trust store file.  We
    // will offer several places to look for the PIN, and we will do so in the
    // following order:
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    // In any case, the PIN must be in the clear.  If no PIN is provided, then
    // it will be assumed that none is required to access the information in the
    // trust store.
    char[] newPIN = null;
    String newPINProperty = configuration.getTrustStorePinProperty();
    if (newPINProperty == null)
    {
      String newPINEnVar = configuration.getTrustStorePinEnvironmentVariable();
      if (newPINEnVar == null)
      {
        String newPINFile = configuration.getTrustStorePinFile();
        if (newPINFile == null)
        {
          String pinStr = configuration.getTrustStorePin();
          if (pinStr == null)
          {
            newPIN = null;
          }
          else
          {
            newPIN = pinStr.toCharArray();
          }
        }
        else
        {
          File pinFile = getFileForPath(newPINFile);
          if (! pinFile.exists())
          {
            ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
            ccr.addMessage(ERR_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE.get(newPINFile, configEntryDN));
          }
          else
          {
            String pinStr = null;

            BufferedReader br = null;
            try
            {
              br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
            }
            catch (IOException ioe)
            {
              ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
              ccr.addMessage(ERR_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ.get(
                  newPINFile, configEntryDN, getExceptionMessage(ioe)));
            }
            finally
            {
              close(br);
            }

            if (pinStr == null)
            {
              ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
              ccr.addMessage(ERR_FILE_TRUSTMANAGER_PIN_FILE_EMPTY.get(newPINFile, configEntryDN));
            }
            else
            {
              newPIN = pinStr.toCharArray();
            }
          }
        }
      }
      else
      {
        String pinStr = System.getenv(newPINEnVar);
        if (pinStr == null)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET.get(newPINEnVar, configEntryDN));
        }
        else
        {
          newPIN = pinStr.toCharArray();
        }
      }
    }
    else
    {
      String pinStr = System.getProperty(newPINProperty);
      if (pinStr == null)
      {
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET.get(newPINProperty, configEntryDN));
      }
      else
      {
        newPIN = pinStr.toCharArray();
      }
    }

    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      trustStoreFile = newTrustStoreFile;
      trustStoreType = newTrustStoreType;
      trustStorePIN  = newPIN;
      currentConfig  = configuration;
    }

    return ccr;
  }
}
