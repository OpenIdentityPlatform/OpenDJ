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



import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Privilege;
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
 * This class provides an implementation of a SASL mechanism that uses
 * plain-text authentication.  It is based on the proposal defined in
 * draft-ietf-sasl-plain-08 in which the SASL credentials are in the form:
 * <BR>
 * <BLOCKQUOTE>[authzid] UTF8NULL authcid UTF8NULL passwd</BLOCKQUOTE>
 * <BR>
 * Note that this is a weak mechanism by itself and does not offer any
 * protection for the password, so it may need to be used in conjunction with a
 * connection security provider to prevent exposing the password.
 */
public class PlainSASLMechanismHandler
       extends SASLMechanismHandler
       implements ConfigurableComponent
{



  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The DN of the identity mapper configuration entry.
  private DN identityMapperDN;

  // The identity mapper that will be used to map ID strings to user entries.
  private IdentityMapper identityMapper;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public PlainSASLMechanismHandler()
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
    int msgID = MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLPLAIN_NO_IDENTITY_MAPPER_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        identityMapperDN = mapperAttr.activeValue();
        identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
        if (identityMapper == null)
        {
          msgID = MSGID_SASLPLAIN_NO_SUCH_IDENTITY_MAPPER;
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

      msgID = MSGID_SASLPLAIN_CANNOT_GET_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_PLAIN, this);
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {

    DirectoryServer.deregisterConfigurableComponent(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_PLAIN);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {


    // Get the SASL credentials provided by the user and decode them.
    String authzID  = null;
    String authcID  = null;
    String password = null;

    ByteString saslCredentials = bindOperation.getSASLCredentials();
    if (saslCredentials == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_NO_SASL_CREDENTIALS;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    String credString = saslCredentials.stringValue();
    int    length     = credString.length();
    int    nullPos1   = credString.indexOf('\u0000');
    if (nullPos1 < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_NO_NULLS_IN_CREDENTIALS;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    if (nullPos1 > 0)
    {
      authzID = credString.substring(0, nullPos1);
    }


    int nullPos2 = credString.indexOf('\u0000', nullPos1+1);
    if (nullPos2 < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_NO_SECOND_NULL;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    if (nullPos2 == (nullPos1+1))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_ZERO_LENGTH_AUTHCID;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    if (nullPos2 == (length-1))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_ZERO_LENGTH_PASSWORD;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    authcID  = credString.substring(nullPos1+1, nullPos2);
    password = credString.substring(nullPos2+1);


    // Get the user entry for the authentication ID.  Allow for an
    // authentication ID that is just a username (as per the SASL PLAIN spec),
    // but also allow a value in the authzid form specified in RFC 2829.
    Entry  userEntry    = null;
    String lowerAuthcID = toLowerCase(authcID);
    if (lowerAuthcID.startsWith("dn:"))
    {
      // Try to decode the user DN and retrieve the corresponding entry.
      DN userDN;
      try
      {
        userDN = DN.decode(authcID.substring(3));
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLPLAIN_CANNOT_DECODE_AUTHCID_AS_DN;
        String message = getMessage(msgID, authcID, de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      if (userDN.isNullDN())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLPLAIN_AUTHCID_IS_NULL_DN;
        String message = getMessage(msgID);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
      if (rootDN != null)
      {
        userDN = rootDN;
      }

      // Acquire a read lock on the user entry.  If this fails, then so will the
      // authentication.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(userDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());

        int    msgID   = MSGID_SASLPLAIN_CANNOT_LOCK_ENTRY;
        String message = getMessage(msgID, String.valueOf(userDN));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      try
      {
        userEntry = DirectoryServer.getEntry(userDN);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLPLAIN_CANNOT_GET_ENTRY_BY_DN;
        String message = getMessage(msgID, String.valueOf(userDN),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
      finally
      {
        LockManager.unlock(userDN, readLock);
      }
    }
    else
    {
      // Use the identity mapper to resolve the username to an entry.
      if (lowerAuthcID.startsWith("u:"))
      {
        authcID = authcID.substring(2);
      }

      try
      {
        userEntry = identityMapper.getEntryForID(authcID);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLPLAIN_CANNOT_MAP_USERNAME;
        String message = getMessage(msgID, String.valueOf(authcID),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }


    // At this point, we should have a user entry.  If we don't then fail.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_NO_MATCHING_ENTRIES;
      String message = getMessage(msgID, authcID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // If an authorization ID was provided, then make sure that it is
    // acceptable.
    Entry authZEntry = userEntry;
    if (authzID != null)
    {
      String lowerAuthzID = toLowerCase(authzID);
      if (lowerAuthzID.startsWith("dn:"))
      {
        DN authzDN;
        try
        {
          authzDN = DN.decode(authzID.substring(3));
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, de);
          }

          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLPLAIN_AUTHZID_INVALID_DN;
          String message = getMessage(msgID, authzID, de.getErrorMessage());
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }

        DN actualAuthzDN = DirectoryServer.getActualRootBindDN(authzDN);
        if (actualAuthzDN != null)
        {
          authzDN = actualAuthzDN;
        }

        if (! authzDN.equals(userEntry.getDN()))
        {
          AuthenticationInfo tempAuthInfo =
            new AuthenticationInfo(userEntry,
                     DirectoryServer.isRootDN(userEntry.getDN()));
          InternalClientConnection tempConn =
               new InternalClientConnection(tempAuthInfo);
          if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int msgID = MSGID_SASLPLAIN_AUTHZID_INSUFFICIENT_PRIVILEGES;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }

          if (authzDN.isNullDN())
          {
            authZEntry = null;
          }
          else
          {
            try
            {
              authZEntry = DirectoryServer.getEntry(authzDN);
              if (authZEntry == null)
              {
                bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                int msgID = MSGID_SASLPLAIN_AUTHZID_NO_SUCH_ENTRY;
                String message = getMessage(msgID, String.valueOf(authzDN));
                bindOperation.setAuthFailureReason(msgID, message);
                return;
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, de);
              }

              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_SASLPLAIN_AUTHZID_CANNOT_GET_ENTRY;
              String message = getMessage(msgID, String.valueOf(authzDN),
                                          de.getErrorMessage());
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
        }
      }
      else
      {
        String idStr;
        if (lowerAuthzID.startsWith("u:"))
        {
          idStr = authzID.substring(2);
        }
        else
        {
          idStr = authzID;
        }

        if (idStr.length() == 0)
        {
          authZEntry = null;
        }
        else
        {
          try
          {
            authZEntry = identityMapper.getEntryForID(idStr);
            if (authZEntry == null)
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLPLAIN_AUTHZID_NO_MAPPED_ENTRY;
              String message = getMessage(msgID, authzID);
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCought(DebugLogLevel.ERROR, de);
            }

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLPLAIN_AUTHZID_CANNOT_MAP_AUTHZID;
            String message = getMessage(msgID, authzID, de.getErrorMessage());
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }

        if ((authZEntry == null) ||
            (! authZEntry.getDN().equals(userEntry.getDN())))
        {
          AuthenticationInfo tempAuthInfo =
            new AuthenticationInfo(userEntry,
                     DirectoryServer.isRootDN(userEntry.getDN()));
          InternalClientConnection tempConn =
               new InternalClientConnection(tempAuthInfo);
          if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int msgID = MSGID_SASLPLAIN_AUTHZID_INSUFFICIENT_PRIVILEGES;
            String message = getMessage(msgID,
                                        String.valueOf(userEntry.getDN()));
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
      }
    }


    // Get the password policy for the user and use it to determine if the
    // provided password was correct.
    try
    {
      PasswordPolicyState pwPolicyState =
           new PasswordPolicyState(userEntry, false, false);
      if (! pwPolicyState.passwordMatches(new ASN1OctetString(password)))
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLPLAIN_INVALID_PASSWORD;
        String message = getMessage(msgID);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLPLAIN_CANNOT_CHECK_PASSWORD_VALIDITY;
      String message = getMessage(msgID, String.valueOf(userEntry.getDN()),
                                  String.valueOf(e));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // If we've gotten here, then the authentication was successful.
    bindOperation.setResultCode(ResultCode.SUCCESS);

    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, authZEntry, SASL_MECHANISM_PLAIN,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    return;
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

    int msgID = MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN;
    attrList.add(new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID),
                                       true, false, false, identityMapperDN));

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


    // Look at the identity mapper configuration.
    int msgID = MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLPLAIN_NO_IDENTITY_MAPPER_ATTR;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN)));
        return false;
      }

      DN mapperDN = mapperAttr.pendingValue();
      if (! mapperDN.equals(identityMapperDN))
      {
        IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          msgID = MSGID_SASLPLAIN_NO_SUCH_IDENTITY_MAPPER;
          unacceptableReasons.add(getMessage(msgID, String.valueOf(mapperDN),
                                             String.valueOf(configEntryDN)));
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

      msgID = MSGID_SASLPLAIN_CANNOT_GET_IDENTITY_MAPPER;
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


    // Look at the identity mapper configuration.
    DN newIdentityMapperDN = null;
    IdentityMapper newIdentityMapper = null;
    int msgID = MSGID_SASLPLAIN_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLPLAIN_NO_IDENTITY_MAPPER_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
      else
      {
        newIdentityMapperDN = mapperAttr.pendingValue();
        if (! newIdentityMapperDN.equals(identityMapperDN))
        {
          newIdentityMapper =
               DirectoryServer.getIdentityMapper(newIdentityMapperDN);
          if (newIdentityMapper == null)
          {
            msgID = MSGID_SASLPLAIN_NO_SUCH_IDENTITY_MAPPER;
            messages.add(getMessage(msgID, String.valueOf(newIdentityMapperDN),
                                    String.valueOf(configEntryDN)));

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

      msgID = MSGID_SASLPLAIN_CANNOT_GET_IDENTITY_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    // If everything has been successful, then apply any changes that were made.
    if (resultCode == ResultCode.SUCCESS)
    {
      if ((newIdentityMapperDN != null) && (identityMapper != null))
      {
        identityMapperDN = newIdentityMapperDN;
        identityMapper   = newIdentityMapper;

        if (detailedResults)
        {
          msgID = MSGID_SASLPLAIN_UPDATED_IDENTITY_MAPPER;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(identityMapperDN)));
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

    // This is a password-based mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {

    // This is not a secure mechanism.
    return false;
  }
}

