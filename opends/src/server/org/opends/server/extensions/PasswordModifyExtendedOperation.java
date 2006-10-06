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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the password modify extended operation defined in RFC
 * 3062.  It includes support for requiring the user's current password as well
 * as for generating a new password if none was provided.
 */
public class PasswordModifyExtendedOperation
       extends ExtendedOperationHandler
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.PasswordModifyExtendedOperation";



  // The DN of the configuration entry.
  private DN configEntryDN;

  // The DN of the identity mapper.
  private DN identityMapperDN;

  // The reference to the identity mapper.
  private IdentityMapper identityMapper;



  /**
   * Create an instance of this password modify extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public PasswordModifyExtendedOperation()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }




  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeExtendedOperationHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeExtendedOperationHandler",
                      String.valueOf(configEntry));

    configEntryDN = configEntry.getDN();

    int msgID = MSGID_EXTOP_PASSMOD_DESCRIPTION_ID_MAPPER;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_EXTOP_PASSMOD_NO_ID_MAPPER;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        identityMapperDN = mapperAttr.activeValue();
        identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
        if (identityMapper == null)
        {
          msgID = MSGID_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER;
          String message = getMessage(msgID, String.valueOf(identityMapperDN),
                                      String.valueOf(configEntryDN));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeExtendedOperationHandler",
                            e);
      msgID = MSGID_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    DirectoryServer.registerConfigurableComponent(this);

    DirectoryServer.registerSupportedExtension(OID_PASSWORD_MODIFY_REQUEST,
                                               this);
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeExtendedOperationHandler");

    DirectoryServer.deregisterConfigurableComponent(this);

    DirectoryServer.deregisterSupportedExtension(OID_PASSWORD_MODIFY_REQUEST);
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {
    assert debugEnter(CLASS_NAME, "processExtendedOperation",
                      String.valueOf(operation));

    // Initialize the variables associated with components that may be included
    // in the request.
    ByteString userIdentity = null;
    ByteString oldPassword  = null;
    ByteString newPassword  = null;


    // Parse the encoded request, if there is one.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue != null)
    {
      try
      {
        ASN1Sequence requestSequence =
             ASN1Sequence.decodeAsSequence(requestValue.value());

        for (ASN1Element e : requestSequence.elements())
        {
          switch (e.getType())
          {
            case TYPE_PASSWORD_MODIFY_USER_ID:
              userIdentity = e.decodeAsOctetString();
              break;
            case TYPE_PASSWORD_MODIFY_OLD_PASSWORD:
              oldPassword = e.decodeAsOctetString();
              break;
            case TYPE_PASSWORD_MODIFY_NEW_PASSWORD:
              newPassword = e.decodeAsOctetString();
              break;
            default:
              operation.setResultCode(ResultCode.PROTOCOL_ERROR);

              int msgID = MSGID_EXTOP_PASSMOD_ILLEGAL_REQUEST_ELEMENT_TYPE;
              operation.appendErrorMessage(getMessage(msgID,
                                                      byteToHex(e.getType())));
              return;
          }
        }
      }
      catch (ASN1Exception ae)
      {
        assert debugException(CLASS_NAME, "processExtendedOperation", ae);

        operation.setResultCode(ResultCode.PROTOCOL_ERROR);

        int    msgID   = MSGID_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST;
        String message = getMessage(msgID, stackTraceToSingleLineString(ae));
        operation.appendErrorMessage(message);

        return;
      }
    }


    // Get the DN of the user that issued the request.
    DN requestorDN = operation.getAuthorizationDN();


    // See if a user identity was provided.  If so, then try to resolve it to
    // an actual user.
    DN    userDN    = null;
    Entry userEntry = null;
    Lock  userLock  = null;

    try
    {
      if (userIdentity == null)
      {
        // This request must be targeted at changing the password for the
        // currently-authenticated user.  Make sure that the user actually is
        // authenticated.
        ClientConnection   clientConnection = operation.getClientConnection();
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if ((! authInfo.isAuthenticated()) || (requestorDN == null) ||
            (requestorDN.isNullDN()))
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_NO_AUTH_OR_USERID;
          operation.appendErrorMessage(getMessage(msgID));

          return;
        }


        // Retrieve a write lock on that user's entry.
        userDN = requestorDN;

        for (int i=0; i < 3; i++)
        {
          userLock = LockManager.lockWrite(userDN);

          if (userLock != null)
          {
            break;
          }
        }

        if (userLock == null)
        {
          operation.setResultCode(DirectoryServer.getServerErrorResultCode());

          int    msgID   = MSGID_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY;
          String message = getMessage(msgID, String.valueOf(userDN));
          operation.appendErrorMessage(message);

          return;
        }


        userEntry = getEntryByDN(operation, userDN);
        if (userEntry == null)
        {
          return;
        }
      }
      else
      {
        // There was a userIdentity section in the request.  It should have
        // started with either "dn:" to indicate that it contained a DN, or
        // "u:" to indicate that it contained a user ID.
        String authzIDStr      = userIdentity.stringValue();
        String lowerAuthzIDStr = toLowerCase(authzIDStr);
        if (lowerAuthzIDStr.startsWith("dn:"))
        {
          try
          {
            userDN = DN.decode(authzIDStr.substring(3));
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "processExtendedOperation", de);

            operation.setResultCode(ResultCode.INVALID_DN_SYNTAX);

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN;
            operation.appendErrorMessage(getMessage(msgID, authzIDStr));

            return;
          }

          // If the provided DN is an alternate DN for a root user, then replace
          // it with the actual root DN.
          DN actualRootDN = DirectoryServer.getActualRootBindDN(userDN);
          if (actualRootDN != null)
          {
            userDN = actualRootDN;
          }

          userEntry = getEntryByDN(operation, userDN);
          if (userEntry == null)
          {
            return;
          }
        }
        else if (lowerAuthzIDStr.startsWith("u:"))
        {
          try
          {
            userEntry = identityMapper.getEntryForID(authzIDStr.substring(2));
            if (userEntry == null)
            {
              if (oldPassword == null)
              {
                operation.setResultCode(ResultCode.NO_SUCH_OBJECT);

                int msgID = MSGID_EXTOP_PASSMOD_CANNOT_MAP_USER;
                operation.appendErrorMessage(getMessage(msgID, authzIDStr));
              }
              else
              {
                operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                int msgID = MSGID_EXTOP_PASSMOD_CANNOT_MAP_USER;
                operation.appendAdditionalLogMessage(getMessage(msgID,
                                                                authzIDStr));
              }

              return;
            }
            else
            {
              userDN = userEntry.getDN();
            }
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "processExtendedOperation", de);

            if (oldPassword == null)
            {
              operation.setResultCode(de.getResultCode());

              int msgID = MSGID_EXTOP_PASSMOD_ERROR_MAPPING_USER;
              operation.appendErrorMessage(getMessage(msgID, authzIDStr,
                                                      de.getErrorMessage()));
            }
            else
            {
              operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_EXTOP_PASSMOD_ERROR_MAPPING_USER;
              operation.appendAdditionalLogMessage(getMessage(msgID, authzIDStr,
                                                        de.getErrorMessage()));
            }

            return;
          }
        }
        else
        {
          // The authorization ID was in an illegal format.
          operation.setResultCode(ResultCode.PROTOCOL_ERROR);

          int msgID = MSGID_EXTOP_PASSMOD_INVALID_AUTHZID_STRING;
          operation.appendErrorMessage(getMessage(msgID, authzIDStr));

          return;
        }
      }


      // At this point, we should have the user entry.  Get the associated
      // password policy.
      PasswordPolicyState pwPolicyState;
      try
      {
        pwPolicyState = new PasswordPolicyState(userEntry, false, false);
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "processExtendedOperation", de);

        operation.setResultCode(DirectoryServer.getServerErrorResultCode());

        int msgID = MSGID_EXTOP_PASSMOD_CANNOT_GET_PW_POLICY;
        operation.appendErrorMessage(getMessage(msgID, String.valueOf(userDN),
                                                de.getErrorMessage()));
        return;
      }


      // Determine whether the user is changing his own password or if it's an
      // administrative reset.
      boolean selfChange = ((userIdentity == null) || (requestorDN == null) ||
                            userDN.equals(requestorDN));


      // If the current password was provided, then we'll need to verify whether
      // it was correct.  If it wasn't provided but this is a self change, then
      // make sure that's OK.
      if (oldPassword == null)
      {
        if (selfChange && pwPolicyState.requireCurrentPassword())
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_REQUIRE_CURRENT_PW;
          operation.appendErrorMessage(getMessage(msgID));
          return;
        }
      }
      else
      {
        if (pwPolicyState.requireSecureAuthentication() &&
            (! operation.getClientConnection().isSecure()))
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_SECURE_AUTH_REQUIRED;
          operation.appendAdditionalLogMessage(getMessage(msgID));
          return;
        }

        if (! pwPolicyState.passwordMatches(oldPassword))
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_INVALID_OLD_PASSWORD;
          operation.appendAdditionalLogMessage(getMessage(msgID));
          return;
        }
      }


      // If it is a self password change and we don't allow that, then reject
      // the request.
      if (selfChange && (! pwPolicyState.allowUserPasswordChanges()))
      {
        if (oldPassword == null)
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED;
          operation.appendErrorMessage(getMessage(msgID));
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED;
          operation.appendAdditionalLogMessage(getMessage(msgID));
        }

        return;
      }


      // If we require secure password changes and the connection isn't secure,
      // then reject the request.
      if (pwPolicyState.requireSecurePasswordChanges() &&
          (! operation.getClientConnection().isSecure()))
      {
        if (oldPassword == null)
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED;
          operation.appendErrorMessage(getMessage(msgID));
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED;
          operation.appendAdditionalLogMessage(getMessage(msgID));
        }

        return;
      }


      // If it's a self-change request and the user is within the minimum age,
      // then reject it.
      if (selfChange && pwPolicyState.isWithinMinimumAge())
      {
        if (oldPassword == null)
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_IN_MIN_AGE;
          operation.appendErrorMessage(getMessage(msgID));
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_IN_MIN_AGE;
          operation.appendAdditionalLogMessage(getMessage(msgID));
        }

        return;
      }


      // If the user's password is expired and it's a self-change request, then
      // see if that's OK.
      if ((selfChange && pwPolicyState.isPasswordExpired() &&
          (! pwPolicyState.allowExpiredPasswordChanges())))
      {
        if (oldPassword == null)
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

          int msgID = MSGID_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED;
          operation.appendErrorMessage(getMessage(msgID));
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED;
          operation.appendAdditionalLogMessage(getMessage(msgID));
        }

        return;
      }



      // If the a new password was provided, then peform any appropriate
      // validation on it.  If not, then see if we can generate one.
      boolean generatedPassword = false;
      boolean isPreEncoded      = false;
      if (newPassword == null)
      {
        try
        {
          newPassword = pwPolicyState.generatePassword();
          if (newPassword == null)
          {
            if (oldPassword == null)
            {
              operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

              int msgID = MSGID_EXTOP_PASSMOD_NO_PW_GENERATOR;
              operation.appendErrorMessage(getMessage(msgID));
            }
            else
            {
              operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_EXTOP_PASSMOD_NO_PW_GENERATOR;
              operation.appendAdditionalLogMessage(getMessage(msgID));
            }

            return;
          }
          else
          {
            generatedPassword = true;
          }
        }
        catch (DirectoryException de)
        {
          assert debugException(CLASS_NAME, "processExtendedOperation", de);

          if (oldPassword == null)
          {
            operation.setResultCode(de.getResultCode());

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_GENERATE_PW;
            operation.appendErrorMessage(getMessage(msgID,
                                                    de.getErrorMessage()));
          }
          else
          {
            operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_GENERATE_PW;
            operation.appendAdditionalLogMessage(getMessage(msgID,
                                                      de.getErrorMessage()));
          }

          return;
        }
      }
      else
      {
        if (pwPolicyState.passwordIsPreEncoded(newPassword))
        {
          // The password modify extended operation isn't intended to be invoked
          // by an internal operation or during synchronization, so we don't
          // need to check for those cases.
          isPreEncoded = true;
          if (! pwPolicyState.allowPreEncodedPasswords())
          {
            if (oldPassword == null)
            {
              operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

              int msgID = MSGID_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED;
              operation.appendErrorMessage(getMessage(msgID));
            }
            else
            {
              operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED;
              operation.appendAdditionalLogMessage(getMessage(msgID));
            }

            return;
          }
        }
        else
        {
          if (selfChange || (! pwPolicyState.skipValidationForAdministrators()))
          {
            HashSet<ByteString> clearPasswords;
            if (oldPassword == null)
            {
              clearPasswords =
                   new HashSet<ByteString>(pwPolicyState.getClearPasswords());
            }
            else
            {
              clearPasswords = new HashSet<ByteString>();
              clearPasswords.add(oldPassword);
              for (ByteString pw : pwPolicyState.getClearPasswords())
              {
                if (! Arrays.equals(pw.value(), oldPassword.value()))
                {
                  clearPasswords.add(pw);
                }
              }
            }

            StringBuilder invalidReason = new StringBuilder();
            if (! pwPolicyState.passwordIsAcceptable(operation, userEntry,
                                                     newPassword,
                                                     clearPasswords,
                                                     invalidReason))
            {
              if (oldPassword == null)
              {
                operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                int msgID = MSGID_EXTOP_PASSMOD_UNACCEPTABLE_PW;
                operation.appendErrorMessage(getMessage(msgID,
                               String.valueOf(invalidReason)));
              }
              else
              {
                operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                int msgID = MSGID_EXTOP_PASSMOD_UNACCEPTABLE_PW;
                operation.appendAdditionalLogMessage(getMessage(msgID,
                               String.valueOf(invalidReason)));
              }

              return;
            }
          }
        }
      }


      // Get the encoded forms of the new password.
      List<ByteString> encodedPasswords;
      if (isPreEncoded)
      {
        encodedPasswords = new ArrayList<ByteString>(1);
        encodedPasswords.add(newPassword);
      }
      else
      {
        try
        {
          encodedPasswords = pwPolicyState.encodePassword(newPassword);
        }
        catch (DirectoryException de)
        {
          assert debugException(CLASS_NAME, "processExtendedOperation", de);

          if (oldPassword == null)
          {
            operation.setResultCode(de.getResultCode());

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD;
            operation.appendErrorMessage(getMessage(msgID,
                                                    de.getErrorMessage()));
          }
          else
          {
            operation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int msgID = MSGID_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD;
            operation.appendAdditionalLogMessage(getMessage(msgID,
                                                      de.getErrorMessage()));
          }

          return;
        }
      }


      // If the current password was provided, then remove all matching values
      // from the user's entry and replace them with the new password.
      // Otherwise replace all password values.
      AttributeType attrType = pwPolicyState.getPasswordAttribute();
      List<Modification> modList = new ArrayList<Modification>();
      if (oldPassword != null)
      {
        // Remove all existing encoded values that match the old password.
        LinkedHashSet<AttributeValue> existingValues =
             pwPolicyState.getPasswordValues();
        LinkedHashSet<AttributeValue> deleteValues =
             new LinkedHashSet<AttributeValue>(existingValues.size());
        if (pwPolicyState.usesAuthPasswordSyntax())
        {
          for (AttributeValue v : existingValues)
          {
            try
            {
              StringBuilder[] components =
                   AuthPasswordSyntax.decodeAuthPassword(v.getStringValue());
              PasswordStorageScheme scheme =
                   DirectoryServer.getAuthPasswordStorageScheme(
                        components[0].toString());
              if (scheme == null)
              {
                // The password is encoded using an unknown scheme.  Remove it
                // from the user's entry.
                deleteValues.add(v);
              }
              else
              {
                if (scheme.authPasswordMatches(oldPassword,
                                               components[1].toString(),
                                               components[2].toString()))
                {
                  deleteValues.add(v);
                }
              }
            }
            catch (DirectoryException de)
            {
              assert debugException(CLASS_NAME, "processExtendedOperation", de);

              // We couldn't decode the provided password value, so remove it
              // from the user's entry.
              deleteValues.add(v);
            }
          }
        }
        else
        {
          for (AttributeValue v : existingValues)
          {
            try
            {
              String[] components =
                   UserPasswordSyntax.decodeUserPassword(v.getStringValue());
              PasswordStorageScheme scheme =
                   DirectoryServer.getPasswordStorageScheme(
                        toLowerCase(components[0].toString()));
              if (scheme == null)
              {
                // The password is encoded using an unknown scheme.  Remove it
                // from the user's entry.
                deleteValues.add(v);
              }
              else
              {
                if (scheme.passwordMatches(oldPassword,
                                           new ASN1OctetString(components[1])))
                {
                  deleteValues.add(v);
                }
              }
            }
            catch (DirectoryException de)
            {
              assert debugException(CLASS_NAME, "processExtendedOperation", de);

              // We couldn't decode the provided password value, so remove it
              // from the user's entry.
              deleteValues.add(v);
            }
          }
        }

        Attribute deleteAttr = new Attribute(attrType, attrType.getNameOrOID(),
                                             deleteValues);
        modList.add(new Modification(ModificationType.DELETE, deleteAttr));


        // Add the new encoded values.
        LinkedHashSet<AttributeValue> addValues =
             new LinkedHashSet<AttributeValue>(encodedPasswords.size());
        for (ByteString s : encodedPasswords)
        {
          addValues.add(new AttributeValue(attrType, s));
        }

        Attribute addAttr = new Attribute(attrType, attrType.getNameOrOID(),
                                          addValues);
        modList.add(new Modification(ModificationType.ADD, addAttr));
      }
      else
      {
        LinkedHashSet<AttributeValue> replaceValues =
             new LinkedHashSet<AttributeValue>(encodedPasswords.size());
        for (ByteString s : encodedPasswords)
        {
          replaceValues.add(new AttributeValue(attrType, s));
        }

        Attribute addAttr = new Attribute(attrType, attrType.getNameOrOID(),
                                          replaceValues);
        modList.add(new Modification(ModificationType.REPLACE, addAttr));
      }


      // Update the password changed time for the user entry.
      pwPolicyState.setPasswordChangedTime();


      // If the password was changed by an end user, then clear any reset flag
      // that might exist.  If the password was changed by an administrator,
      // then see if we need to set the reset flag.
      if (selfChange)
      {
        pwPolicyState.setMustChangePassword(false);
      }
      else
      {
        pwPolicyState.setMustChangePassword(pwPolicyState.forceChangeOnReset());
      }


      // Clear any record of grace logins, auth failures, and expiration
      // warnings.
      pwPolicyState.clearAuthFailureTimes();
      pwPolicyState.clearFailureLockout();
      pwPolicyState.clearGraceLoginTimes();
      pwPolicyState.clearWarnedTime();


      // Get the list of modifications from the password policy state and add
      // them to the existing password modifications.
      modList.addAll(pwPolicyState.getModifications());


      // Get an internal connection and use it to perform the modification.
      boolean isRoot = DirectoryServer.isRootDN(requestorDN);
      AuthenticationInfo authInfo = new AuthenticationInfo(requestorDN, isRoot);
      InternalClientConnection internalConnection = new
           InternalClientConnection(authInfo);

      ModifyOperation modifyOperation =
           internalConnection.processModify(userDN, modList);
      ResultCode resultCode = modifyOperation.getResultCode();
      if (resultCode != resultCode.SUCCESS)
      {
        operation.setResultCode(resultCode);
        operation.setErrorMessage(modifyOperation.getErrorMessage());
        operation.setReferralURLs(modifyOperation.getReferralURLs());
        return;
      }


      // If we've gotten here, then everything is OK, so indicate that the
      // operation was successful.  If a password was generated, then include
      // it in the response.
      operation.setResultCode(ResultCode.SUCCESS);

      if (generatedPassword)
      {
        ArrayList<ASN1Element> valueElements = new ArrayList<ASN1Element>(1);

        ASN1OctetString newPWString =
             new ASN1OctetString(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD,
                                 newPassword.value());
        valueElements.add(newPWString);

        ASN1Sequence valueSequence = new ASN1Sequence(valueElements);
        operation.setResponseValue(new ASN1OctetString(valueSequence.encode()));
      }


      // If this was a self password change, and the client is authenticated as
      // the user whose password was changed, then clear the "must change
      // password" flag in the client connection.  Note that we're using the
      // authentication DN rather than the authorization DN in this case to
      // avoid mistakenly clearing the flag for the wrong user.
      if (selfChange && (authInfo.getAuthenticationDN() != null) &&
          (authInfo.getAuthenticationDN().equals(userDN)))
      {
        operation.getClientConnection().setMustChangePassword(false);
      }
    }
    finally
    {
      if (userLock != null)
      {
        LockManager.unlock(userDN, userLock);
      }
    }
  }



  /**
   * Retrieves the entry for the specified user based on the provided DN.  If
   * any problem is encountered or the requested entry does not exist, then the
   * provided operation will be updated with appropriate result information and
   * this method will return <CODE>null</CODE>.  The caller must hold a write
   * lock on the specified entry.
   *
   * @param  operation  The extended operation being processed.
   * @param  entryDN    The DN of the user entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if there was no such
   *          entry or it could not be retrieved.
   */
  private Entry getEntryByDN(ExtendedOperation operation, DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "getEntryByDN", String.valueOf(operation),
                      String.valueOf(entryDN));

    // Retrieve the user's entry from the directory.  If it does not exist, then
    // fail.
    try
    {
      Entry userEntry = DirectoryServer.getEntry(entryDN);

      if (userEntry == null)
      {
        operation.setResultCode(ResultCode.NO_SUCH_OBJECT);

        int msgID = MSGID_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID;
        operation.appendErrorMessage(getMessage(msgID,
                                                String.valueOf(entryDN)));

        // See if one of the entry's ancestors exists.
        DN parentDN = entryDN.getParent();
        while (parentDN != null)
        {
          try
          {
            if (DirectoryServer.entryExists(parentDN))
            {
              operation.setMatchedDN(parentDN);
              break;
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "getEntryByDN", e);
            break;
          }

          parentDN = parentDN.getParent();
        }

        return null;
      }

      return userEntry;
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "getEntryByDN", de);

      operation.setResultCode(de.getResultCode());
      operation.appendErrorMessage(de.getErrorMessage());
      operation.setMatchedDN(de.getMatchedDN());
      operation.setReferralURLs(de.getReferralURLs());

      return null;
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
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

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
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    List<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_EXTOP_PASSMOD_DESCRIPTION_ID_MAPPER;
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
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "List<String>");


    // Make sure that the specified identity mapper is OK.
    int msgID = MSGID_EXTOP_PASSMOD_DESCRIPTION_ID_MAPPER;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_EXTOP_PASSMOD_NO_ID_MAPPER;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()));
        unacceptableReasons.add(message);
        return false;
      }
      else
      {
        DN mapperDN = mapperAttr.pendingValue();
        IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          msgID = MSGID_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER;
          String message = getMessage(msgID, String.valueOf(mapperDN),
                                      String.valueOf(configEntry.getDN()));
          unacceptableReasons.add(message);
          return false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }


    // If we've gotten here, then everything is OK.
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


    // Make sure that the specified identity mapper is OK.
    DN             mapperDN = null;
    IdentityMapper mapper   = null;
    int msgID = MSGID_EXTOP_PASSMOD_DESCRIPTION_ID_MAPPER;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        resultCode = ResultCode.OBJECTCLASS_VIOLATION;

        msgID = MSGID_EXTOP_PASSMOD_NO_ID_MAPPER;
        messages.add(getMessage(msgID, String.valueOf(configEntry.getDN())));
      }
      else
      {
        mapperDN = mapperAttr.pendingValue();
        mapper   = DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          resultCode = ResultCode.CONSTRAINT_VIOLATION;

          msgID = MSGID_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER;
          messages.add(getMessage(msgID, String.valueOf(mapperDN),
                                  String.valueOf(configEntry.getDN())));
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      resultCode = DirectoryServer.getServerErrorResultCode();

      msgID = MSGID_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(configEntry.getDN()),
                              stackTraceToSingleLineString(e)));
    }


    // If all of the changes were acceptable, then apply them.
    if (resultCode == ResultCode.SUCCESS)
    {
      if (! identityMapperDN.equals(mapperDN))
      {
        identityMapper   = mapper;
        identityMapperDN = mapperDN;
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

