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



import java.util.ArrayList;
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

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.TrustManagerCfg;
import org.opends.server.admin.std.server.FileBasedTrustManagerCfg;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.ExpirationCheckTrustManager;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class FileBasedTrustManagerProvider
       extends TrustManagerProvider<FileBasedTrustManagerCfg>
       implements ConfigurationChangeListener<FileBasedTrustManagerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The DN of the configuration entry for this trust manager provider.
  private DN configEntryDN;

  // The PIN needed to access the trust store.
  private char[] trustStorePIN;

  // The handle to the configuration for this trust manager.
  private FileBasedTrustManagerCfg currentConfig;

  // The path to the trust store backing file.
  private String trustStoreFile;

  // The trust store type to use.
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeTrustManagerProvider(
                   FileBasedTrustManagerCfg configuration)
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
    if (! (f.exists() && f.isFile()))
    {
      int    msgID   = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
      String message = getMessage(msgID, String.valueOf(trustStoreFile),
                                  String.valueOf(configEntryDN));
      throw new InitializationException(msgID, message);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, kse);
      }

      int    msgID   = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
      String message = getMessage(msgID, String.valueOf(trustStoreType),
                                  String.valueOf(configEntryDN),
                                  getExceptionMessage(kse));
      throw new InitializationException(msgID, message);
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
            int    msgID    = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
            String message = getMessage(msgID,
                                        String.valueOf(pinFilePath),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
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
              int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
              String message = getMessage(msgID,
                                          String.valueOf(pinFilePath),
                                          String.valueOf(configEntryDN),
                                          getExceptionMessage(ioe));
              throw new InitializationException(msgID, message, ioe);
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
              int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
              String message = getMessage(msgID,
                                          String.valueOf(pinFilePath),
                                          String.valueOf(configEntryDN));
              throw new InitializationException(msgID, message);
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
          int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
          String message = getMessage(msgID,
                                      String.valueOf(pinProperty),
                                      String.valueOf(configEntryDN));
          throw new InitializationException(msgID, message);
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
        int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
        String message = getMessage(msgID,
                                    String.valueOf(pinProperty),
                                    String.valueOf(configEntryDN));
        throw new InitializationException(msgID, message);
      }
      else
      {
        trustStorePIN = pinStr.toCharArray();
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeTrustManagerProvider()
  {
    currentConfig.removeFileBasedChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_LOAD;
      String message = getMessage(msgID, trustStoreFile,
                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY;
      String message = getMessage(msgID, trustStoreFile,
                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(TrustManagerCfg configuration,
                                           List<String> unacceptableReasons)
  {
    FileBasedTrustManagerCfg config = (FileBasedTrustManagerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      FileBasedTrustManagerCfg configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN configEntryDN = configuration.dn();


    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    try
    {
      File f = getFileForPath(newTrustStoreFile);
      if (!(f.exists() && f.isFile()))
      {
        int msgID = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(newTrustStoreFile),
                                           String.valueOf(configEntryDN)));
        configAcceptable = false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         getExceptionMessage(e)));
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, kse);
        }

        int    msgID   = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
        String message = getMessage(msgID, String.valueOf(storeType),
                                    String.valueOf(configEntryDN),
                                    getExceptionMessage(kse));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN property, then make sure the corresponding
    // property is set.
    String pinProp = configuration.getTrustStorePinProperty();
    if (pinProp != null)
    {
      if (System.getProperty(pinProp) == null)
      {
        int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
        String message = getMessage(msgID, String.valueOf(pinProp),
                                    String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN environment variable, then make sure the corresponding
    // environment variable is set.
    String pinEnVar = configuration.getTrustStorePinEnvironmentVariable();
    if (pinEnVar != null)
    {
      if (System.getenv(pinEnVar) == null)
      {
        int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
        String message = getMessage(msgID, String.valueOf(pinEnVar),
                                    String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    // If there is a PIN file, then make sure the file exists and is readable.
    String pinFile = configuration.getTrustStorePinFile();
    if (pinFile != null)
    {
      File f = new File(pinFile);
      if (f.exists())
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
          int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
          String message = getMessage(msgID, String.valueOf(pinFile),
                                      String.valueOf(configEntryDN),
                                      getExceptionMessage(ioe));
          unacceptableReasons.add(message);
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
          int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
          String message =  getMessage(msgID, String.valueOf(pinFile),
                                       String.valueOf(configEntryDN));
          unacceptableReasons.add(message);
          configAcceptable = false;
        }
      }
      else
      {
        int    msgID   = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
        String message = getMessage(msgID, String.valueOf(pinFile),
                                    String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        configAcceptable = false;
      }
    }


    return configAcceptable;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 FileBasedTrustManagerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the path to the trust store file.
    String newTrustStoreFile = configuration.getTrustStoreFile();
    File f = getFileForPath(newTrustStoreFile);
    if (! (f.exists() && f.isFile()))
    {
      resultCode = DirectoryServer.getServerErrorResultCode();

      int msgID = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
      messages.add(getMessage(msgID, String.valueOf(newTrustStoreFile),
                              String.valueOf(configEntryDN)));
    }


    // Get the trust store type.  If none is specified, then use the default
    // type.
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, kse);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
      messages.add(getMessage(msgID, String.valueOf(newTrustStoreType),
                              String.valueOf(configEntryDN),
                              getExceptionMessage(kse)));

      resultCode = DirectoryServer.getServerErrorResultCode();
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
            resultCode = DirectoryServer.getServerErrorResultCode();

            int msgID = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
            messages.add(getMessage(msgID, String.valueOf(newPINFile),
                                    String.valueOf(configEntryDN)));
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
              resultCode = DirectoryServer.getServerErrorResultCode();

              int msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
              messages.add(getMessage(msgID, String.valueOf(newPINFile),
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

              int msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
              messages.add(getMessage(msgID, String.valueOf(newPINFile),
                                      String.valueOf(configEntryDN)));
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
          resultCode = DirectoryServer.getServerErrorResultCode();

          int msgID = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
          messages.add(getMessage(msgID, String.valueOf(newPINEnVar),
                                  String.valueOf(configEntryDN)));
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
        resultCode = DirectoryServer.getServerErrorResultCode();

        int msgID = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
        messages.add(getMessage(msgID, String.valueOf(newPINProperty),
                                String.valueOf(configEntryDN)));
      }
      else
      {
        newPIN = pinStr.toCharArray();
      }
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      trustStoreFile = newTrustStoreFile;
      trustStoreType = newTrustStoreType;
      trustStorePIN  = newPIN;
      currentConfig  = configuration;
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

