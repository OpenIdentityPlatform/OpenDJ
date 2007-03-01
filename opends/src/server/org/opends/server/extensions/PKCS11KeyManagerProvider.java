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
 * by brackets "[]" replaced with your own identifying * information:
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
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.KeyManagerProvider;
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
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a key manager provider that will access keys stored on a
 * PKCS#11 device.  It will use the Java PKCS#11 interface, which may need to be
 * configured on the underlying system.
 */
public class PKCS11KeyManagerProvider
       extends KeyManagerProvider
       implements ConfigurableComponent
{



  /**
   * The keystore type to use when accessing the PKCS#11 keystore.
   */
  public static final String PKCS11_KEYSTORE_TYPE = "PKCS11";



  // The DN of the configuration entry for this key manager provider.
  private DN configEntryDN;

  // The PIN needed to access the keystore.
  private char[] keyStorePIN;

  // The name of the environment variable containing the keystore PIN.
  private String keyStorePINEnVar;

  // The path to the file containing the keystore PIN.
  private String keyStorePINFile;

  // The name of the Java property containing the keystore PIN.
  private String keyStorePINProperty;



  /**
   * Creates a new instance of this PKCS#11 key manager provider.  The
   * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public PKCS11KeyManagerProvider()
  {

    // No implementation is required.
  }



  /**
   * Initializes this key manager provider based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this key manager provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization as a
   *                           result of the server configuration.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeKeyManagerProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {


    // Store the DN of the configuration entry.
    configEntryDN = configEntry.getDN();


    // Get the PIN needed to access the contents of the PKCS#11 keystore.  We
    // will offer several places to look for the PIN, and we will do so in the
    // following order:
    // - In a specified Java property
    // - In a specified environment variable
    // - In a specified file on the server filesystem.
    // - As the value of a configuration attribute.
    // In any case, the PIN must be in the clear.
    keyStorePIN         = null;
    keyStorePINEnVar    = null;
    keyStorePINFile     = null;
    keyStorePINProperty = null;
pinSelection:
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_PROPERTY,
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET;
            String message = getMessage(msgID, String.valueOf(propertyName),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
          }
          else
          {
            keyStorePIN         = pinStr.toCharArray();
            keyStorePINProperty = propertyName;
            break pinSelection;
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_ENVAR, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET;
            String message = getMessage(msgID, String.valueOf(enVarName),
                                        String.valueOf(configEntryDN));
            throw new InitializationException(msgID, message);
          }
          else
          {
            keyStorePIN      = pinStr.toCharArray();
            keyStorePINEnVar = enVarName;
            break pinSelection;
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_FILE, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE;
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
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN),
                                          stackTraceToSingleLineString(ioe));
              throw new InitializationException(msgID, message, ioe);
            }

            if (pinStr == null)
            {
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_EMPTY;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN));
              throw new InitializationException(msgID, message);
            }
            else
            {
              keyStorePIN     = pinStr.toCharArray();
              keyStorePINFile = fileName;
              break pinSelection;
            }
          }
        }
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ie);
        }

        throw ie;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          keyStorePIN = pinAttr.activeValue().toCharArray();
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }
    }

    if (keyStorePIN == null)
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_NO_PIN;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * Performs any finalization that may be necessary for this key manager
   * provider.
   */
  public void finalizeKeyManagerProvider()
  {

    DirectoryServer.deregisterConfigurableComponent(this);
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
      keyStore = KeyStore.getInstance(PKCS11_KEYSTORE_TYPE);
      keyStore.load(null, keyStorePIN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_LOAD;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID, e);
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
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_CREATE_FACTORY;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
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


    int msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY;
    StringConfigAttribute pinPropertyAttr =
         new StringConfigAttribute(ATTR_KEYSTORE_PIN_PROPERTY,
                                   getMessage(msgID), false, false, false,
                                   keyStorePINProperty);
    attrList.add(pinPropertyAttr);


    msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR;
    StringConfigAttribute pinEnvVarAttr =
         new StringConfigAttribute(ATTR_KEYSTORE_PIN_ENVAR,
                                   getMessage(msgID), false, false, false,
                                   keyStorePINEnVar);
    attrList.add(pinEnvVarAttr);


    msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE;
    StringConfigAttribute pinFileAttr =
         new StringConfigAttribute(ATTR_KEYSTORE_PIN_FILE,
                                   getMessage(msgID), false, false, false,
                                   keyStorePINFile);
    attrList.add(pinFileAttr);


    String pinString;
    if ((keyStorePINProperty == null) && (keyStorePINEnVar == null) &&
        (keyStorePINFile == null))
    {
      pinString = new String(keyStorePIN);
    }
    else
    {
      pinString = null;
    }
    msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR;
    StringConfigAttribute pinAttr =
         new StringConfigAttribute(ATTR_KEYSTORE_PIN, getMessage(msgID), false,
                                   false, false, pinString);
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

    DN configEntryDN = configEntry.getDN();


    // Make sure that there is some way to determine the PIN.  Look for the PIN
    // in a property, environment variable, file, or configuration attribute, in
    // that order.
    char[] keyStorePIN = null;
pinSelection:
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_PROPERTY,
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET;
            String message = getMessage(msgID, String.valueOf(propertyName),
                                        String.valueOf(configEntryDN));
            unacceptableReasons.add(message);
            return false;
          }
          else
          {
            keyStorePIN = pinStr.toCharArray();
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_ENVAR, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET;
            String message = getMessage(msgID, String.valueOf(enVarName),
                                        String.valueOf(configEntryDN));
            unacceptableReasons.add(message);
            return false;
          }
          else
          {
            keyStorePIN = pinStr.toCharArray();
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_FILE, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE;
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
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN),
                                          stackTraceToSingleLineString(ioe));
              unacceptableReasons.add(message);
              return false;
            }

            if (pinStr == null)
            {
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_EMPTY;
              String message = getMessage(msgID, String.valueOf(fileName),
                                          String.valueOf(configEntryDN));
              unacceptableReasons.add(message);
              return false;
            }
            else
            {
              keyStorePIN = pinStr.toCharArray();
              break pinSelection;
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          keyStorePIN = pinAttr.pendingValue().toCharArray();
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    stackTraceToSingleLineString(e));
        unacceptableReasons.add(message);
        return false;
      }
    }

    if (keyStorePIN == null)
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_NO_PIN;
      String message = getMessage(msgID, String.valueOf(configEntryDN));
      unacceptableReasons.add(message);
      return false;
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


    // Make sure that there is some way to determine the PIN.  Look for the PIN
    // in a property, environment variable, file, or configuration attribute, in
    // that order.
    char[] newKeyStorePIN         = null;
    String newKeyStorePINEnVar    = null;
    String newKeyStorePINFile     = null;
    String newKeyStorePINProperty = null;
pinSelection:
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_PROPERTY;
      StringConfigAttribute pinPropertyStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_PROPERTY,
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_PROPERTY_NOT_SET;
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
            newKeyStorePIN         = pinStr.toCharArray();
            newKeyStorePINProperty = propertyName;
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_PROPERTY;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ENVAR;
      StringConfigAttribute pinEnVarStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_ENVAR, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_ENVAR_NOT_SET;
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
            newKeyStorePIN      = pinStr.toCharArray();
            newKeyStorePINEnVar = enVarName;
            break pinSelection;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_ENVAR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_FILE;
      StringConfigAttribute pinFileStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN_FILE, getMessage(msgID),
                                     false, false, false);
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
            msgID = MSGID_PKCS11_KEYMANAGER_PIN_NO_SUCH_FILE;
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
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_CANNOT_READ;
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
              msgID = MSGID_PKCS11_KEYMANAGER_PIN_FILE_EMPTY;
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
              newKeyStorePIN     = pinStr.toCharArray();
              newKeyStorePINFile = fileName;
              break pinSelection;
            }
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FILE;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }

      msgID = MSGID_PKCS11_KEYMANAGER_DESCRIPTION_PIN_ATTR;
      StringConfigAttribute pinStub =
           new StringConfigAttribute(ATTR_KEYSTORE_PIN, getMessage(msgID),
                                     false, false, false);
      try
      {
        StringConfigAttribute pinAttr =
             (StringConfigAttribute)
             configEntry.getConfigAttribute(pinStub);
        if (pinAttr != null)
        {
          newKeyStorePIN = pinAttr.activeValue().toCharArray();
          break pinSelection;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        msgID = MSGID_PKCS11_KEYMANAGER_CANNOT_DETERMINE_PIN_FROM_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                stackTraceToSingleLineString(e)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        break pinSelection;
      }
    }

    if (newKeyStorePIN == null)
    {
      int msgID = MSGID_PKCS11_KEYMANAGER_NO_PIN;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
    }


    // If everything looks successful, then apply the changes.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! Arrays.equals(keyStorePIN, newKeyStorePIN))
      {
        keyStorePIN = newKeyStorePIN;

        keyStorePINProperty = newKeyStorePINProperty;
        keyStorePINEnVar    = newKeyStorePINEnVar;
        keyStorePINFile     = newKeyStorePINFile;

        if (detailedResults)
        {
          int msgID = MSGID_PKCS11_KEYMANAGER_UPDATED_PIN;
          messages.add(getMessage(msgID));
        }
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

