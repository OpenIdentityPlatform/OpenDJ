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



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a SASL mechanism that authenticates
 * clients through Kerberos over GSSAPI.
 */
public class GSSAPISASLMechanismHandler
       extends SASLMechanismHandler
       implements ConfigurableComponent
{



  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The DN of the identity mapper configuration entry.
  private DN identityMapperDN;

  // The identity mapper that will be used to map the Kerberos principal to a
  // directory user.
  private IdentityMapper identityMapper;

  // The address of the KDC to use for Kerberos authentication.
  private String kdcAddress;

  // The path to the keytab file to use to obtain the server key.
  private String keyTabFile;

  // The default realm to use for Kerberos authentication.
  private String realm;

  // The fully-qualified DNS name to use for the Directory Server system.  This
  // is factored into the authentication process.
  private String serverFQDN;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public GSSAPISASLMechanismHandler()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {


    this.configEntryDN = configEntry.getDN();


    // Get the identity mapper that should be used to find users.
    int msgID = MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLGSSAPI_NO_IDENTITY_MAPPER_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        identityMapperDN = mapperAttr.activeValue();
        identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
        if (identityMapper == null)
        {
          msgID = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
          String message = getMessage(msgID, String.valueOf(identityMapperDN),
                                      String.valueOf(configEntryDN));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the fully-qualified hostname for this system.  It may be
    // provided, but if not, then try to determine it programmatically.
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN;
    StringConfigAttribute serverFQDNStub =
         new StringConfigAttribute(ATTR_SERVER_FQDN, getMessage(msgID), false,
                                false, false);
    try
    {
      StringConfigAttribute serverFQDNAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(serverFQDNStub);
      if (serverFQDNAttr == null)
      {
        // No value was provided, so try to figure it out for ourselves.
        serverFQDN = InetAddress.getLocalHost().getCanonicalHostName();
      }
      else
      {
        serverFQDN = serverFQDNAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the address of the KDC to use.  If it is not provided, then
    // we'll assume that the underlying OS has a valid config file.
    kdcAddress = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS;
    StringConfigAttribute kdcStub =
         new StringConfigAttribute(ATTR_GSSAPI_KDC, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute kdcAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(kdcStub);
      if (kdcAttr != null)
      {
        kdcAddress = kdcAttr.activeValue();
        System.setProperty(KRBV_PROPERTY_KDC, kdcAddress);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_KDC_ADDRESS;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the default realm to use.  If it is not provided, then we'll
    // assume that the underlying OS has a valid config file.
    realm = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_REALM;
    StringConfigAttribute realmStub =
         new StringConfigAttribute(ATTR_GSSAPI_REALM, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute realmAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(realmStub);
      if (realmAttr != null)
      {
        realm = realmAttr.activeValue();
        System.setProperty(KRBV_PROPERTY_REALM, realm);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_REALM;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Determine the path of the keytab file to use.  If it is not provided,
    // then we'll let Java use the system default keytab.
    keyTabFile = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_KEYTAB_FILE;
    StringConfigAttribute keyTabStub =
         new StringConfigAttribute(ATTR_GSSAPI_KEYTAB_FILE, getMessage(msgID),
                                   false, false, false);
    try
    {
      StringConfigAttribute keyTabAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(keyTabStub);
      if (keyTabAttr != null)
      {
        keyTabFile = keyTabAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_KEYTAB_FILE;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Since we're going to be using JAAS behind the scenes, we need to have a
    // JAAS configuration.  Rather than always requiring the user to provide it,
    // we'll write one to a temporary file that will be deleted when the JVM
    // exits.
    String configFileName;
    try
    {
      File tempFile = File.createTempFile("login", "conf");
      configFileName = tempFile.getAbsolutePath();
      tempFile.deleteOnExit();
      BufferedWriter w = new BufferedWriter(new FileWriter(tempFile, false));

      w.write(getClass().getName() + " {");
      w.newLine();

      w.write("  com.sun.security.auth.module.Krb5LoginModule required " +
              "storeKey=true useKeyTab=true ");

      if (keyTabFile != null)
      {
        w.write("keyTab=\"" + keyTabFile + "\" ");
      }

      // FIXME -- Should we add the ability to include "debug=true"?

      // FIXME -- Can we get away from hard-coding a protocol here?
      w.write("principal=\"ldap/" + serverFQDN);
      if (realm != null)
      {
        w.write("@" + realm);
      }
      w.write("\";");

      w.newLine();

      w.write("};");
      w.newLine();

      w.flush();
      w.close();
    }
    catch (Exception e)
    {
      msgID = MSGID_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    System.setProperty(JAAS_PROPERTY_CONFIG_FILE, configFileName);
    System.setProperty(JAAS_PROPERTY_SUBJECT_CREDS_ONLY, "false");


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_GSSAPI, this);
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {

    DirectoryServer.deregisterConfigurableComponent(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_GSSAPI);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {


    // GSSAPI binds use multiple stages, so we need to determine whether this is
    // the first stage or a subsequent one.  To do that, see if we have SASL
    // state information in the client connection.
    ClientConnection clientConnection = bindOperation.getClientConnection();
    if (clientConnection == null)
    {
      int    msgID   = MSGID_SASLGSSAPI_NO_CLIENT_CONNECTION;
      String message = getMessage(msgID);

      bindOperation.setAuthFailureReason(msgID, message);
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return;
    }

    GSSAPIStateInfo stateInfo = null;
    Object saslBindState = clientConnection.getSASLAuthStateInfo();
    if ((saslBindState != null) && (saslBindState instanceof GSSAPIStateInfo))
    {
      stateInfo = (GSSAPIStateInfo) saslBindState;
    }
    else
    {
      try
      {
        stateInfo = new GSSAPIStateInfo(this, bindOperation, serverFQDN);
      }
      catch (InitializationException ie)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, ie);
        }

        bindOperation.setAuthFailureReason(ie.getMessageID(), ie.getMessage());
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        clientConnection.setSASLAuthStateInfo(null);
        return;
      }
    }

    stateInfo.setBindOperation(bindOperation);
    stateInfo.processAuthenticationStage();


    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      // The authentication was successful, so set the proper state information
      // in the client connection and return success.
      Entry userEntry = stateInfo.getUserEntry();
      AuthenticationInfo authInfo =
           new AuthenticationInfo(userEntry, SASL_MECHANISM_GSSAPI,
                                  DirectoryServer.isRootDN(userEntry.getDN()));
      bindOperation.setAuthenticationInfo(authInfo);
      bindOperation.setResultCode(ResultCode.SUCCESS);

      // FIXME -- If we're using integrity or confidentiality, then we can't do
      // this.
      clientConnection.setSASLAuthStateInfo(null);

      try
      {
        stateInfo.dispose();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
    else if (bindOperation.getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS)
    {
      // We need to store the SASL auth state with the client connection so we
      // can resume authentication the next time around.
      clientConnection.setSASLAuthStateInfo(stateInfo);
    }
    else
    {
      // The authentication failed.  We don't want to keep the SASL state
      // around.
      // FIXME -- Are there other result codes that we need to check for and
      //          preserve the auth state?
      clientConnection.setSASLAuthStateInfo(null);
    }
  }



  /**
   * Retrieves the user account for the user associated with the provided
   * authorization ID.
   *
   * @param  bindOperation  The bind operation from which the provided
   *                        authorization ID was derived.
   * @param  authzID        The authorization ID for which to retrieve the
   *                        associated user.
   *
   * @return  The user entry for the user with the specified authorization ID,
   *          or <CODE>null</CODE> if none is identified.
   *
   * @throws  DirectoryException  If a problem occurs while searching the
   *                              directory for the associated user, or if
   *                              multiple matching entries are found.
   */
  public Entry getUserForAuthzID(BindOperation bindOperation, String authzID)
         throws DirectoryException
  {

    return identityMapper.getEntryForID(authzID);
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

    int msgID = MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN;
    attrList.add(new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID),
                                       true, false, false, identityMapperDN));

    msgID = MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN;
    attrList.add(new StringConfigAttribute(ATTR_SERVER_FQDN, getMessage(msgID),
                                           false, false, false, serverFQDN));

    msgID = MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS;
    attrList.add(new StringConfigAttribute(ATTR_GSSAPI_KDC, getMessage(msgID),
                                           false, false, false, kdcAddress));

    msgID = MSGID_SASLGSSAPI_DESCRIPTION_REALM;
    attrList.add(new StringConfigAttribute(ATTR_GSSAPI_REALM, getMessage(msgID),
                                           false, false, false, realm));

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


    // Look at the identity mapper configuration
    int msgID = MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLGSSAPI_NO_IDENTITY_MAPPER_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        unacceptableReasons.add(message);
        return false;
      }
      else
      {
        DN mapperDN = mapperAttr.activeValue();
        IdentityMapper mapper =
             DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          msgID = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
          String message = getMessage(msgID, String.valueOf(mapperDN),
                                      String.valueOf(configEntryDN));
          unacceptableReasons.add(message);
          return false;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // Look a the server FQDN configuration.
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN;
    StringConfigAttribute serverFQDNStub  =
         new StringConfigAttribute(ATTR_SERVER_FQDN, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute serverFQDNAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(serverFQDNStub);

      // FIXME -- Should we try to resolve the value if one is provided?
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // Look at the KDC configuration.
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS;
    StringConfigAttribute kdcStub =
         new StringConfigAttribute(ATTR_GSSAPI_KDC, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute kdcAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(kdcStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_KDC_ADDRESS;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // Look at the realm configuration.
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_REALM;
    StringConfigAttribute realmStub =
         new StringConfigAttribute(ATTR_GSSAPI_REALM, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute realmAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(realmStub);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_REALM;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten to this point, then everything must be OK.
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


    // Look at the identity mapper configuration
    DN             newIdentityMapperDN = null;
    IdentityMapper newIdentityMapper   = null;
    int msgID = MSGID_SASLGSSAPI_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLGSSAPI_NO_IDENTITY_MAPPER_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = ResultCode.OBJECTCLASS_VIOLATION;
        }
      }
      else
      {
        newIdentityMapperDN = mapperAttr.activeValue();
        newIdentityMapper =
             DirectoryServer.getIdentityMapper(newIdentityMapperDN);
        if (newIdentityMapper == null)
        {
          msgID = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
          messages.add(getMessage(msgID, String.valueOf(newIdentityMapperDN),
                                  String.valueOf(configEntryDN)));

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
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_IDENTITY_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Look at the server FQDN configuration.
    String newServerFQDN = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_SERVER_FQDN;
    StringConfigAttribute serverFQDNStub  =
         new StringConfigAttribute(ATTR_SERVER_FQDN, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute serverFQDNAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(serverFQDNStub);
      if (serverFQDNAttr == null)
      {
        newServerFQDN = InetAddress.getLocalHost().getCanonicalHostName();
      }
      else
      {
        newServerFQDN = serverFQDNAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Look at the KDC configuration.
    String newKDC = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_KDC_ADDRESS;
    StringConfigAttribute kdcStub =
         new StringConfigAttribute(ATTR_GSSAPI_KDC, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute kdcAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(kdcStub);
      if (kdcAttr != null)
      {
        newKDC = kdcAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_KDC_ADDRESS;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // Look at the realm configuration.
    String newRealm = null;
    msgID = MSGID_SASLGSSAPI_DESCRIPTION_REALM;
    StringConfigAttribute realmStub =
         new StringConfigAttribute(ATTR_GSSAPI_REALM, getMessage(msgID), false,
                                   false, false);
    try
    {
      StringConfigAttribute realmAttr =
           (StringConfigAttribute) configEntry.getConfigAttribute(realmStub);
      if (realmAttr != null)
      {
        newRealm = realmAttr.pendingValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      msgID = MSGID_SASLGSSAPI_CANNOT_GET_REALM;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }


    // If everything has been successful, then apply any changes that were made.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! identityMapperDN.equals(newIdentityMapperDN))
      {
        identityMapperDN = newIdentityMapperDN;
        identityMapper   = newIdentityMapper;

        if (detailedResults)
        {
          msgID = MSGID_SASLGSSAPI_UPDATED_IDENTITY_MAPPER;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(identityMapperDN)));
        }
      }

      if (serverFQDN == null)
      {
        if (newServerFQDN != null)
        {
          serverFQDN = newServerFQDN;

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_NEW_SERVER_FQDN;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    String.valueOf(serverFQDN)));
          }
        }
      }
      else if (newServerFQDN == null)
      {
        serverFQDN = null;

        if (detailedResults)
        {
          msgID = MSGID_SASLGSSAPI_UPDATED_NO_SERVER_FQDN;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
        }
      }
      else
      {
        if (! serverFQDN.equals(newServerFQDN))
        {
          serverFQDN = newServerFQDN;

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_NEW_SERVER_FQDN;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    String.valueOf(serverFQDN)));
          }
        }
      }

      if (kdcAddress == null)
      {
        if (newKDC != null)
        {
          kdcAddress = newKDC;
          System.setProperty(KRBV_PROPERTY_KDC, kdcAddress);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_KDC;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                    String.valueOf(kdcAddress)));
          }
        }
      }
      else
      {
        if (newKDC == null)
        {
          kdcAddress = null;
          System.clearProperty(KRBV_PROPERTY_KDC);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UNSET_KDC;
            messages.add(getMessage(msgID, String.valueOf(configEntryDN)));
          }
        }
        else if (! kdcAddress.equals(newKDC))
        {
          kdcAddress = newKDC;
          System.setProperty(KRBV_PROPERTY_KDC, kdcAddress);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_KDC;
            messages.add(getMessage(msgID, String.valueOf(kdcAddress)));
          }
        }
      }

      if (realm == null)
      {
        if (newRealm != null)
        {
          realm = newRealm;
          System.setProperty(KRBV_PROPERTY_REALM, realm);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_REALM;
            messages.add(getMessage(msgID, String.valueOf(realm)));
          }
        }
      }
      else
      {
        if (newRealm == null)
        {
          realm = null;
          System.clearProperty(KRBV_PROPERTY_REALM);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UNSET_REALM;
            messages.add(getMessage(msgID));
          }
        }
        else if (! realm.equals(newRealm))
        {
          realm = newRealm;
          System.setProperty(KRBV_PROPERTY_REALM, realm);

          if (detailedResults)
          {
            msgID = MSGID_SASLGSSAPI_UPDATED_REALM;
            messages.add(getMessage(msgID, String.valueOf(realm)));
          }
        }
      }
    }


    // Return the result to the caller.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {

    // This is not a password-based mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {

    // This may be considered a secure mechanism.
    return true;
  }
}

