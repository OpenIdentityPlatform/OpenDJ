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
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.PKCS11KeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a key manager provider that will access keys stored on a
 * PKCS#11 device.  It will use the Java PKCS#11 interface, which may need to be
 * configured on the underlying system.
 */
public class PKCS11KeyManagerProvider
    extends KeyManagerProvider<PKCS11KeyManagerProviderCfg>
    implements ConfigurationChangeListener<PKCS11KeyManagerProviderCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The keystore type to use when accessing the PKCS#11 keystore. */
  public static final String PKCS11_KEYSTORE_TYPE = "PKCS11";

  /** The DN of the configuration entry for this key manager provider. */
  private DN configEntryDN;

  /** The PIN needed to access the keystore. */
  private char[] keyStorePIN;

  /** The current configuration for this key manager provider. */
  private PKCS11KeyManagerProviderCfg currentConfig;

  /**
   * Creates a new instance of this PKCS#11 key manager provider.  The
   * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public PKCS11KeyManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeKeyManagerProvider(
                    PKCS11KeyManagerProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // Store the DN of the configuration entry and register to be notified of
    // configuration changes.
    currentConfig = configuration;
    configEntryDN = configuration.dn();
    configuration.addPKCS11ChangeListener(this);

    // Get the PIN needed to access the contents of the PKCS#11
    // keystore. We will offer several places to look for the PIN, and
    // we will do so in the following order:
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
        LocalizableMessage message = ERR_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(
            propertyName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinEnvironmentVariable() != null) {
      String enVarName = configuration
          .getKeyStorePinEnvironmentVariable();
      String pinStr = System.getenv(enVarName);

      if (pinStr == null) {
        LocalizableMessage message = ERR_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET.get(
            enVarName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePinFile() != null) {
      String fileName = configuration.getKeyStorePinFile();
      File pinFile = getFileForPath(fileName);

      if (!pinFile.exists()) {
        LocalizableMessage message = ERR_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, configEntryDN);
        throw new InitializationException(message);
      }

      String pinStr;
      try {
        BufferedReader br = new BufferedReader(
            new FileReader(pinFile));
        pinStr = br.readLine();
        br.close();
      } catch (IOException ioe) {
        logger.traceException(ioe);

        LocalizableMessage message = ERR_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ.
            get(fileName, configEntryDN, getExceptionMessage(ioe));
        throw new InitializationException(message, ioe);
      }

      if (pinStr == null) {
        LocalizableMessage message = ERR_PKCS11_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, configEntryDN);
        throw new InitializationException(message);
      }

      keyStorePIN = pinStr.toCharArray();
    } else if (configuration.getKeyStorePin() != null) {
      keyStorePIN = configuration.getKeyStorePin().toCharArray();
    }
  }

  @Override
  public void finalizeKeyManagerProvider()
  {
    currentConfig.removePKCS11ChangeListener(this);
  }

  @Override
  public KeyManager[] getKeyManagers()
         throws DirectoryException
  {
    KeyStore keyStore;
    try
    {
      keyStore = KeyStore.getInstance(PKCS11_KEYSTORE_TYPE);
      keyStore.load(null, keyStorePIN);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_PKCS11_KEYMANAGER_CANNOT_LOAD.get(getExceptionMessage(e));
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
      logger.traceException(e);

      LocalizableMessage message = ERR_PKCS11_KEYMANAGER_CANNOT_CREATE_FACTORY.get(
          getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }
  }

  @Override
  public boolean isConfigurationAcceptable(
                        PKCS11KeyManagerProviderCfg configuration,
                          List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      PKCS11KeyManagerProviderCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();


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
    //
    // It is acceptable to have no PIN (OPENDJ-18)
    if (configuration.getKeyStorePinProperty() != null)
    {
      String propertyName = configuration.getKeyStorePinProperty();
      String pinStr = System.getProperty(propertyName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(propertyName, cfgEntryDN));
        configAcceptable = false;
      }
    }
    else if (configuration.getKeyStorePinEnvironmentVariable() != null)
    {
      String enVarName = configuration.getKeyStorePinEnvironmentVariable();
      String pinStr    = System.getenv(enVarName);

      if (pinStr == null)
      {
        unacceptableReasons.add(ERR_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET.get(enVarName, configEntryDN));
        configAcceptable = false;
      }
    }
    else if (configuration.getKeyStorePinFile() != null)
    {
      String fileName = configuration.getKeyStorePinFile();
      File   pinFile  = getFileForPath(fileName);

      if (!pinFile.exists())
      {
        unacceptableReasons.add(ERR_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, configEntryDN));
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
          unacceptableReasons.add(
                  ERR_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ.get(
                      fileName, cfgEntryDN, getExceptionMessage(ioe)));
          configAcceptable = false;
        }
        finally
        {
          StaticUtils.close(br);
        }

        if (pinStr == null)
        {
          unacceptableReasons.add(ERR_PKCS11_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, configEntryDN));
          configAcceptable = false;
        }
      }
    }
    else if (configuration.getKeyStorePin() != null)
    {
      String pinStr = configuration.getKeyStorePin();
      if (pinStr == null)
      {
        // We should have a pin from the configuration, but no.
        unacceptableReasons.add(
            ERR_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR.get(cfgEntryDN, null));
        configAcceptable = false;
      }
    }

    return configAcceptable;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                 PKCS11KeyManagerProviderCfg configuration)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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
        ccr.addMessage(ERR_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET.get(propertyName, configEntryDN));
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
        ccr.addMessage(ERR_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET.get(enVarName, configEntryDN));
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
        ccr.addMessage(ERR_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE.get(fileName, configEntryDN));
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
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ.get(
              fileName, configEntryDN, getExceptionMessage(ioe)));
        }
        finally
        {
          StaticUtils.close(br);
        }

        if (pinStr == null)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(ERR_PKCS11_KEYMANAGER_PIN_FILE_EMPTY.get(fileName, configEntryDN));
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
    }

    return ccr;
  }
}
