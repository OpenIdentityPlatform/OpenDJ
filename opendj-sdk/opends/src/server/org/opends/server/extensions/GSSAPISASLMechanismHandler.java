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



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.GSSAPISASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
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
       extends SASLMechanismHandler<GSSAPISASLMechanismHandlerCfg>
       implements ConfigurationChangeListener<
                       GSSAPISASLMechanismHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The current configuration for this SASL mechanism handler.
  private GSSAPISASLMechanismHandlerCfg currentConfig;

  // The identity mapper that will be used to map the Kerberos principal to a
  // directory user.
  private IdentityMapper identityMapper;

  // The fully-qualified domain name for the server system.
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
  public void initializeSASLMechanismHandler(
                   GSSAPISASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addGSSAPIChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
    if (identityMapper == null)
    {
      int    msgID   = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(identityMapperDN),
                                  String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    // Determine the fully-qualified hostname for this system.  It may be
    // provided, but if not, then try to determine it programmatically.
    serverFQDN = configuration.getServerFqdn();
    if (serverFQDN == null)
    {
      try
      {
        serverFQDN = InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN;
        String message = getMessage(msgID, String.valueOf(configEntryDN),
                                    getExceptionMessage(e));
        throw new InitializationException(msgID, message, e);
      }
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

      String keyTabFile = configuration.getKeytab();
      if (keyTabFile != null)
      {
        w.write("keyTab=\"" + keyTabFile + "\" ");
      }

      // FIXME -- Should we add the ability to include "debug=true"?

      // FIXME -- Can we get away from hard-coding a protocol here?
      w.write("principal=\"ldap/" + serverFQDN);

      String realm = configuration.getRealm();
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }

    System.setProperty(JAAS_PROPERTY_CONFIG_FILE, configFileName);
    System.setProperty(JAAS_PROPERTY_SUBJECT_CREDS_ONLY, "false");


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_GSSAPI, this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removeGSSAPIChangeListener(this);
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
          TRACER.debugCaught(DebugLogLevel.ERROR, ie);
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
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



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    GSSAPISASLMechanismHandlerCfg config =
         (GSSAPISASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      GSSAPISASLMechanismHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    IdentityMapper newIdentityMapper =
         DirectoryServer.getIdentityMapper(identityMapperDN);
    if (newIdentityMapper == null)
    {
      int msgID = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
      unacceptableReasons.add(getMessage(msgID,
                                         String.valueOf(identityMapperDN),
                                         String.valueOf(cfgEntryDN)));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              GSSAPISASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    IdentityMapper newIdentityMapper =
         DirectoryServer.getIdentityMapper(identityMapperDN);
    if (newIdentityMapper == null)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      int msgID = MSGID_SASLGSSAPI_NO_SUCH_IDENTITY_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(identityMapperDN),
                              String.valueOf(configEntryDN)));
    }


    // Determine the fully-qualified hostname for this system.  It may be
    // provided, but if not, then try to determine it programmatically.
    String newFQDN = configuration.getServerFqdn();
    if (newFQDN == null)
    {
      try
      {
        newFQDN = InetAddress.getLocalHost().getCanonicalHostName();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (resultCode == ResultCode.SUCCESS)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
        }

        int msgID = MSGID_SASLGSSAPI_CANNOT_GET_SERVER_FQDN;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                getExceptionMessage(e)));
      }
    }


    if (resultCode == ResultCode.SUCCESS)
    {
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

        String keyTabFile = configuration.getKeytab();
        if (keyTabFile != null)
        {
          w.write("keyTab=\"" + keyTabFile + "\" ");
        }

        // FIXME -- Should we add the ability to include "debug=true"?

        // FIXME -- Can we get away from hard-coding a protocol here?
        w.write("principal=\"ldap/" + serverFQDN);

        String realm = configuration.getRealm();
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        resultCode = DirectoryServer.getServerErrorResultCode();

        int msgID = MSGID_SASLGSSAPI_CANNOT_CREATE_JAAS_CONFIG;
        messages.add(getMessage(msgID, getExceptionMessage(e)));

       return new ConfigChangeResult(resultCode, adminActionRequired, messages);
      }

      System.setProperty(JAAS_PROPERTY_CONFIG_FILE, configFileName);

      identityMapper = newIdentityMapper;
      serverFQDN     = newFQDN;
      currentConfig  = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

