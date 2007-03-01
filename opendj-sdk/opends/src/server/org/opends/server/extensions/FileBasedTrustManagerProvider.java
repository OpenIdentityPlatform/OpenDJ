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



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a trust manager provider that will reference certificates
 * stored in a file located on the Directory Server filesystem.
 */
public class FileBasedTrustManagerProvider
       extends TrustManagerProvider
       implements ConfigurableComponent
{



  // The DN of the configuration entry for this trust manager provider.
  private DN configEntryDN;

  // The PIN needed to access the trust store.
  private char[] trustStorePIN;

  // The path to the trust store backing file.
  private String trustStoreFile;

  // The name of the environment variable containing the trust store PIN.
  private String trustStorePINEnVar;

  // The path to the file containing the trust store PIN.
  private String trustStorePINFile;

  // The name of the Java property containing the trust store PIN.
  private String trustStorePINProperty;

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
   * Initializes this trust manager provider based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this trust manager provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization as a
   *                           result of the server configuration.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeTrustManagerProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    // Store the DN of the configuration entry.
    configEntryDN = configEntry.getDN();


    // Get the path to the trust store file.
    int msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE;
    StringConfigAttribute fileStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_FILE, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute fileAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(fileStub);
      if ((fileAttr == null) ||
          ((trustStoreFile = fileAttr.activeValue()) == null))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_FILE_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

      File f = getFileForPath(trustStoreFile);
      if (! (f.exists() && f.isFile()))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
        String message = getMessage(msgID, String.valueOf(trustStoreFile),
                                    String.valueOf(configEntryDN));
        throw new InitializationException(msgID, message);
      }
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }

      throw ce;
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the trust store type.  If none is specified, then use the default
    // type.
    trustStoreType = KeyStore.getDefaultType();
    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE;
    StringConfigAttribute typeStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_TYPE, getMessage(msgID),
                                   false, false, false);
    try
    {
      StringConfigAttribute typeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(typeStub);
      if (typeAttr != null)
      {
        // A trust store type was specified, so make sure it is valid.
        String typeStr = typeAttr.activeValue();

        try
        {
          KeyStore.getInstance(typeStr);
          trustStoreType = typeStr;
        }
        catch (KeyStoreException kse)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, kse);
          }

          msgID = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
          String message = getMessage(msgID, String.valueOf(typeStr),
                                      String.valueOf(configEntryDN),
                                      stackTraceToSingleLineString(kse));
          throw new InitializationException(msgID, message);
        }
      }
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_TYPE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
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
    trustStorePIN         = null;
    trustStorePINEnVar    = null;
    trustStorePINFile     = null;
    trustStorePINProperty = null;
pinSelection:
    {
      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_PROPERTY,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinPropertyAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinPropertyStub);
        if (pinPropertyAttr != null)
        {
          String propertyName = pinPropertyAttr.activeValue();
          String pinStr       = System.getProperty(propertyName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
            String message = getMessage(msgID, String.valueOf(propertyName),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
          }
          else
          {
            trustStorePIN         = pinStr.toCharArray();
            trustStorePINProperty = propertyName;
            break pinSelection;
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_ENVAR,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinEnVarAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinEnVarStub);
        if (pinEnVarAttr != null)
        {
          String enVarName = pinEnVarAttr.activeValue();
          String pinStr    = System.getenv(enVarName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
            String message = getMessage(msgID, String.valueOf(enVarName),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
          }
          else
          {
            trustStorePIN      = pinStr.toCharArray();
            trustStorePINEnVar = enVarName;
            break pinSelection;
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_FILE,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinFileAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinFileStub);
        if (pinFileAttr != null)
        {
          String fileName = pinFileAttr.activeValue();

          File pinFile = getFileForPath(fileName);
          if (! pinFile.exists())
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
            String message = getMessage(msgID, String.valueOf(fileName),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
          }
          else
          {
            String pinStr;

            try
            {
              BufferedReader br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
              br.close();
            }
            catch (IOException ioe)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN),
                                          stackTraceToSingleLineString(ioe));
              throw new InitializationException(msgID, message, ioe);
            }

            if (pinStr == null)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN));
              throw new InitializationException(msgID, message);
            }
            else
            {
              trustStorePIN     = pinStr.toCharArray();
              trustStorePINFile = fileName;
              break pinSelection;
            }
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FILE;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          trustStorePIN = pinAttr.activeValue().toCharArray();
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }
    }


    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any finalization that may be necessary for this trust manager
   * provider.
   */
  public void finalizeTrustManagerProvider()
  {
    DirectoryServer.deregisterConfigurableComponent(this);
  }



  /**
   * Retrieves a set of <CODE>TrustManager</CODE> objects that may be used for
   * interactions requiring access to a trust manager.
   *
   * @return  A set of <CODE>TrustManager</CODE> objects that may be used for
   *          interactions requiring access to a trust manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to obtain
   *                              the set of trust managers.
   */
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_LOAD;
      String message = getMessage(msgID, trustStoreFile,
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }


    try
    {
      String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory trustManagerFactory =
           TrustManagerFactory.getInstance(trustManagerAlgorithm);
      trustManagerFactory.init(trustStore);
      return trustManagerFactory.getTrustManagers();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_CREATE_FACTORY;
      String message = getMessage(msgID, trustStoreFile,
                                  stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();


    int msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE;
    StringConfigAttribute fileAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_FILE, getMessage(msgID),
                                   true, false, false, trustStoreFile);
    attrList.add(fileAttr);


    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE;
    StringConfigAttribute typeAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_TYPE, getMessage(msgID),
                                   true, false, false, trustStoreType);
    attrList.add(typeAttr);


    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY;
    StringConfigAttribute pinPropertyAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_PROPERTY,
                                   getMessage(msgID), false, false, false,
                                   trustStorePINProperty);
    attrList.add(pinPropertyAttr);


    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR;
    StringConfigAttribute pinEnvVarAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_ENVAR,
                                   getMessage(msgID), false, false, false,
                                   trustStorePINEnVar);
    attrList.add(pinEnvVarAttr);


    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE;
    StringConfigAttribute pinFileAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_FILE,
                                   getMessage(msgID), false, false, false,
                                   trustStorePINFile);
    attrList.add(pinFileAttr);


    String pinString;
    if ((trustStorePINProperty == null) && (trustStorePINEnVar == null) &&
        (trustStorePINFile == null))
    {
      pinString = new String(trustStorePIN);
    }
    else
    {
      pinString = null;
    }
    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR;
    StringConfigAttribute pinAttr =
         new StringConfigAttribute(ATTR_TRUSTSTORE_PIN, getMessage(msgID),
                                   false, false, false, pinString);
    attrList.add(pinAttr);


    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    // Make sure that a trust store file was provided.
    int msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE;
    StringConfigAttribute fileStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_FILE, getMessage(msgID),
                                   true, false, false);
    try
    {
      String newTrustStoreFile = null;

      StringConfigAttribute fileAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(fileStub);
      if ((fileAttr == null) ||
          ((newTrustStoreFile = fileAttr.activeValue()) == null))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_FILE_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

      File f = getFileForPath(newTrustStoreFile);
      if (! (f.exists() && f.isFile()))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
        String message = getMessage(msgID, String.valueOf(newTrustStoreFile),
                                    String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }

      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // See if a trust store type was provided.  It is optional, but if one was
    // provided, then it must be a valid type.
    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE;
    StringConfigAttribute typeStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_TYPE, getMessage(msgID),
                                   false, false, false);
    try
    {
      StringConfigAttribute typeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(typeStub);
      if (typeAttr != null)
      {
        // A trust store type was specified, so make sure it is valid.
        String typeStr = typeAttr.activeValue();

        try
        {
          KeyStore.getInstance(typeStr);
        }
        catch (KeyStoreException kse)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, kse);
          }

          msgID = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
          String message = getMessage(msgID, String.valueOf(typeStr),
                                      String.valueOf(configEntryDN),
                                      stackTraceToSingleLineString(kse));
          unacceptableReasons.add(message);
          return false;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_TYPE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // Make sure that there is some way to determine the PIN.  Look for the PIN
    // in a property, environment variable, file, or configuration attribute, in
    // that order.
pinSelection:
    {
      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_PROPERTY,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinPropertyAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinPropertyStub);
        if (pinPropertyAttr != null)
        {
          String propertyName = pinPropertyAttr.activeValue();
          String pinStr       = System.getProperty(propertyName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
            String message = getMessage(msgID, String.valueOf(propertyName),
                                        String.valueOf(configEntryDN));
            unacceptableReasons.add(message);
            return false;
          }
          else
          {
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_ENVAR,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinEnVarAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinEnVarStub);
        if (pinEnVarAttr != null)
        {
          String enVarName = pinEnVarAttr.activeValue();
          String pinStr    = System.getenv(enVarName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
            String message = getMessage(msgID, String.valueOf(enVarName),
                                        String.valueOf(configEntryDN));
            unacceptableReasons.add(message);
            return false;
          }
          else
          {
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_FILE,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinFileAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinFileStub);
        if (pinFileAttr != null)
        {
          String fileName = pinFileAttr.activeValue();

          File pinFile = getFileForPath(fileName);
          if (! pinFile.exists())
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
            String message = getMessage(msgID, String.valueOf(fileName),
                                        String.valueOf(configEntryDN));
            unacceptableReasons.add(message);
            return false;
          }
          else
          {
            String pinStr;

            try
            {
              BufferedReader br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
              br.close();
            }
            catch (IOException ioe)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN),
                                          stackTraceToSingleLineString(ioe));
              unacceptableReasons.add(message);
              return false;
            }

            if (pinStr == null)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN));
              unacceptableReasons.add(message);
              return false;
            }
            else
            {
              break pinSelection;
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FILE;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }
    }


    // If we've gotten here, then everything looks OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure that a trust store file was provided.
    String newTrustStoreFile = null;
    int msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_FILE;
    StringConfigAttribute fileStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_FILE, getMessage(msgID),
                                   true, false, false);
    try
    {
      StringConfigAttribute fileAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(fileStub);
      if ((fileAttr == null) ||
          ((newTrustStoreFile = fileAttr.activeValue()) == null))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_FILE_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }

      File f = getFileForPath(newTrustStoreFile);
      if (! (f.exists() && f.isFile()))
      {
        msgID = MSGID_FILE_TRUSTMANAGER_NO_SUCH_FILE;
        messages.add(getMessage(msgID, String.valueOf(newTrustStoreFile),
                                String.valueOf(configEntryDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;
        }
      }
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_FILE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // See if a trust store type was provided.  It is optional, but if one was
    // provided, then it must be a valid type.
    String newTrustStoreType = KeyStore.getDefaultType();
    msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_TYPE;
    StringConfigAttribute typeStub =
         new StringConfigAttribute(ATTR_TRUSTSTORE_TYPE, getMessage(msgID),
                                   false, false, false);
    try
    {
      StringConfigAttribute typeAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(typeStub);
      if (typeAttr != null)
      {
        // A trust store type was specified, so make sure it is valid.
        newTrustStoreType = typeAttr.activeValue();

        try
        {
          KeyStore.getInstance(newTrustStoreType);
        }
        catch (KeyStoreException kse)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, kse);
          }

          msgID = MSGID_FILE_TRUSTMANAGER_INVALID_TYPE;
          messages.add(getMessage(msgID, String.valueOf(newTrustStoreType),
                                  String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(kse)));

          if (resultCode == ResultCode.SUCCESS)
          {
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_TYPE;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Make sure that there is some way to determine the PIN.  Look for the PIN
    // in a property, environment variable, file, or configuration attribute, in
    // that order.
    char[] newTrustStorePIN         = null;
    String newTrustStorePINEnVar    = null;
    String newTrustStorePINFile     = null;
    String newTrustStorePINProperty = null;
pinSelection:
    {
      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_PROPERTY,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinPropertyAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinPropertyStub);
        if (pinPropertyAttr != null)
        {
          String propertyName = pinPropertyAttr.activeValue();
          String pinStr       = System.getProperty(propertyName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_PROPERTY_NOT_SET;
            messages.add(getMessage(msgID, String.valueOf(propertyName),
                                    String.valueOf(configEntryDN)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }

            break pinSelection;
          }
          else
          {
            newTrustStorePIN         = pinStr.toCharArray();
            newTrustStorePINProperty = propertyName;
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_ENVAR,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinEnVarAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinEnVarStub);
        if (pinEnVarAttr != null)
        {
          String enVarName = pinEnVarAttr.activeValue();
          String pinStr    = System.getenv(enVarName);
          if (pinStr == null)
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_ENVAR_NOT_SET;
            messages.add(getMessage(msgID, String.valueOf(enVarName),
                                    String.valueOf(configEntryDN)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }

            break pinSelection;
          }
          else
          {
            newTrustStorePIN      = pinStr.toCharArray();
            newTrustStorePINEnVar = enVarName;
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN_FILE,
                                     getMessage(msgID), false, false, false);
      try
      {
        StringConfigAttribute pinFileAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinFileStub);
        if (pinFileAttr != null)
        {
          String fileName = pinFileAttr.activeValue();

          File pinFile = getFileForPath(fileName);
          if (! pinFile.exists())
          {
            msgID = MSGID_FILE_TRUSTMANAGER_PIN_NO_SUCH_FILE;
            messages.add(getMessage(msgID, String.valueOf(fileName),
                                    String.valueOf(configEntryDN)));

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = ResultCode.CONSTRAINT_VIOLATION;
            }

            break pinSelection;
          }
          else
          {
            String pinStr;

            try
            {
              BufferedReader br = new BufferedReader(new FileReader(pinFile));
              pinStr = br.readLine();
              br.close();
            }
            catch (IOException ioe)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_CANNOT_READ;
              messages.add(getMessage(msgID, String.valueOf(fileName),
                                      String.valueOf(configEntryDN),
                                      stackTraceToSingleLineString(ioe)));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = DirectoryServer.getServerErrorResultCode();
              }

              break pinSelection;
            }

            if (pinStr == null)
            {
              msgID = MSGID_FILE_TRUSTMANAGER_PIN_FILE_EMPTY;
              messages.add(getMessage(msgID, String.valueOf(fileName),
                                      String.valueOf(configEntryDN)));

              if (resultCode == ResultCode.SUCCESS)
              {
                resultCode = ResultCode.CONSTRAINT_VIOLATION;
              }

              break pinSelection;
            }
            else
            {
              newTrustStorePIN     = pinStr.toCharArray();
              newTrustStorePINFile = fileName;
              break pinSelection;
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FILE;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_FILE_TRUSTMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_TRUSTSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          newTrustStorePIN = pinAttr.activeValue().toCharArray();
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_FILE_TRUSTMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }
    }


    // If everything looks successful, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! trustStoreFile.equals(newTrustStoreFile))
      {
        trustStoreFile = newTrustStoreFile;

        if (detailedResults)
        {
          msgID = MSGID_FILE_TRUSTMANAGER_UPDATED_FILE;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(newTrustStoreFile)));
        }
      }

      if (! trustStoreType.equals(newTrustStoreType))
      {
        trustStoreType = newTrustStoreType;

        if (detailedResults)
        {
          msgID = MSGID_FILE_TRUSTMANAGER_UPDATED_TYPE;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(newTrustStoreType)));
        }
      }

      if (! (((trustStorePIN == null) && (newTrustStorePIN == null)) ||
             Arrays.equals(trustStorePIN, newTrustStorePIN)))
      {
        trustStorePIN = newTrustStorePIN;

        trustStorePINProperty = newTrustStorePINProperty;
        trustStorePINEnVar    = newTrustStorePINEnVar;
        trustStorePINFile     = newTrustStorePINFile;

        if (detailedResults)
        {
          msgID = MSGID_FILE_TRUSTMANAGER_UPDATED_PIN;
          messages.add(getMessage(msgID));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

