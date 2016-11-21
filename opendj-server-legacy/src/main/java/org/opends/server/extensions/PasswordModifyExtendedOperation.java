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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.controls.PasswordPolicyErrorType.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.types.AccountStatusNotificationType.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.server.ExtendedOperationHandlerCfg;
import org.forgerock.opendj.server.config.server.PasswordModifyExtendedOperationHandlerCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationProperty;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;

/**
 * This class implements the password modify extended operation defined in RFC
 * 3062.  It includes support for requiring the user's current password as well
 * as for generating a new password if none was provided.
 */
public class PasswordModifyExtendedOperation
       extends ExtendedOperationHandler<PasswordModifyExtendedOperationHandlerCfg>
       implements ConfigurationChangeListener<PasswordModifyExtendedOperationHandlerCfg>
{
  // The following attachments may be used by post-op plugins (e.g. Samba) in
  // order to avoid re-decoding the request parameters and also to enforce
  // atomicity.

  /** The name of the attachment which will be used to store the fully resolved target entry. */
  public static final String AUTHZ_DN_ATTACHMENT;
  /** The name of the attachment which will be used to store the password attribute. */
  public static final String PWD_ATTRIBUTE_ATTACHMENT;
  /** The clear text password, which may not be present if the provided password was pre-encoded. */
  public static final String CLEAR_PWD_ATTACHMENT;
  /** A list containing the encoded passwords: plugins can perform changes atomically via CAS. */
  public static final String ENCODED_PWD_ATTACHMENT;

  static
  {
    final String PREFIX = PasswordModifyExtendedOperation.class.getName();
    AUTHZ_DN_ATTACHMENT = PREFIX + ".AUTHZ_DN";
    PWD_ATTRIBUTE_ATTACHMENT = PREFIX + ".PWD_ATTRIBUTE";
    CLEAR_PWD_ATTACHMENT = PREFIX + ".CLEAR_PWD";
    ENCODED_PWD_ATTACHMENT = PREFIX + ".ENCODED_PWD";
  }
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current configuration state. */
  private PasswordModifyExtendedOperationHandlerCfg currentConfig;

  /** The DN of the identity mapper. */
  private DN identityMapperDN;

  /** The reference to the identity mapper. */
  private IdentityMapper<?> identityMapper;


  /**
   * Create an instance of this password modify extended operation.  All initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public PasswordModifyExtendedOperation()
  {
    super(newHashSet(OID_LDAP_NOOP_OPENLDAP_ASSIGNED, OID_PASSWORD_POLICY_CONTROL));
  }

  @Override
  public void initializeExtendedOperationHandler(PasswordModifyExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    try
    {
      identityMapperDN = config.getIdentityMapperDN();
      identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
      if (identityMapper == null)
      {
        throw new ConfigException(ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(identityMapperDN, config.dn()));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER
          .get(config.dn(), getExceptionMessage(e));
      throw new InitializationException(message, e);
    }

    // Save this configuration for future reference.
    currentConfig = config;

    // Register this as a change listener.
    config.addPasswordModifyChangeListener(this);

    super.initializeExtendedOperationHandler(config);
  }

  @Override
  public void finalizeExtendedOperationHandler()
  {
    currentConfig.removePasswordModifyChangeListener(this);

    super.finalizeExtendedOperationHandler();
  }

  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // Initialize the variables associated with components that may be included in the request.
    ByteString userIdentity = null;
    ByteString oldPassword  = null;
    ByteString newPassword  = null;

    // Look at the set of controls included in the request, if there are any.
    boolean                   noOpRequested        = false;
    boolean                   pwPolicyRequested    = false;
    for (Control c : operation.getRequestControls())
    {
      String oid = c.getOID();
      if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
      {
        noOpRequested = true;
      }
      else if (OID_PASSWORD_POLICY_CONTROL.equals(oid))
      {
        pwPolicyRequested = true;
      }
    }

    // Parse the encoded request, if there is one.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue != null)
    {
      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();
        if(reader.hasNextElement() && reader.peekType() == TYPE_PASSWORD_MODIFY_USER_ID)
        {
          userIdentity = reader.readOctetString();
        }
        if(reader.hasNextElement() && reader.peekType() == TYPE_PASSWORD_MODIFY_OLD_PASSWORD)
        {
          oldPassword = reader.readOctetString();
        }
        if(reader.hasNextElement() && reader.peekType() == TYPE_PASSWORD_MODIFY_NEW_PASSWORD)
        {
          newPassword = reader.readOctetString();
        }
        reader.readEndSequence();
      }
      catch (Exception ae)
      {
        logger.traceException(ae);

        operation.setResultCode(ResultCode.PROTOCOL_ERROR);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_DECODE_REQUEST.get(getExceptionMessage(ae)));
        return;
      }
    }

    // Get the entry for the user that issued the request.
    Entry requestorEntry = operation.getAuthorizationEntry();

    // See if a user identity was provided.  If so, then try to resolve it to an actual user.
    DN userDN = null;
    Entry userEntry = null;
    DNLock userLock = null;
    try
    {
      if (userIdentity == null)
      {
        // This request must be targeted at changing the password for the currently-authenticated user.
        // Make sure that the user actually is authenticated.
        ClientConnection   clientConnection = operation.getClientConnection();
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if (!authInfo.isAuthenticated() || requestorEntry == null)
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_NO_AUTH_OR_USERID.get());
          return;
        }

        userDN = requestorEntry.getName();
        userEntry = requestorEntry;
      }
      else
      {
        // There was a userIdentity field in the request.
        String authzIDStr      = userIdentity.toString();
        String lowerAuthzIDStr = toLowerCase(authzIDStr);
        if (lowerAuthzIDStr.startsWith("dn:"))
        {
          try
          {
            userDN = DN.valueOf(authzIDStr.substring(3));
          }
          catch (LocalizedIllegalArgumentException de)
          {
            logger.traceException(de);

            operation.setResultCode(ResultCode.INVALID_DN_SYNTAX);
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_DECODE_AUTHZ_DN.get(authzIDStr));
            return;
          }

          // If the provided DN is an alternate DN for a root user, then replace it with the actual root DN.
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
              operation.setResultCode(ResultCode.NO_SUCH_OBJECT);
              operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_MAP_USER.get(authzIDStr));
              return;
            }

            userDN = userEntry.getName();
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            //Encountered an exception while resolving identity.
            operation.setResultCode(de.getResultCode());
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ERROR_MAPPING_USER.get(authzIDStr, de.getMessageObject()));
            return;
          }
        }
        else
        {
          /*
           * the userIdentity provided does not follow Authorization Identity form. RFC3062
           * declaration "may or may not be an LDAPDN" allows for pretty much anything in that
           * field. we gonna try to parse it as DN first then if that fails as user ID.
           */
          try
          {
            userDN = DN.valueOf(authzIDStr);
          }
          catch (LocalizedIllegalArgumentException ignored)
          {
            logger.traceException(ignored);
          }

          if (userDN != null && !userDN.isRootDN()) {
            // If the provided DN is an alternate DN for a root user, then replace it with the actual root DN.
            DN actualRootDN = DirectoryServer.getActualRootBindDN(userDN);
            if (actualRootDN != null) {
              userDN = actualRootDN;
            }
            userEntry = getEntryByDN(operation, userDN);
          } else {
            try
            {
              userEntry = identityMapper.getEntryForID(authzIDStr);
            }
            catch (DirectoryException ignored)
            {
              logger.traceException(ignored);
            }
          }

          if (userEntry == null) {
            // The userIdentity was invalid.
            operation.setResultCode(ResultCode.PROTOCOL_ERROR);
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_INVALID_AUTHZID_STRING.get(authzIDStr));
            return;
          }

          userDN = userEntry.getName();
        }
      }

      userLock = DirectoryServer.getLockManager().tryWriteLockEntry(userDN);
      if (userLock == null)
      {
        operation.setResultCode(ResultCode.BUSY);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_LOCK_USER_ENTRY.get(userDN));
        return;
      }

      // At this point, we should have the user entry.  Get the associated password policy.
      PasswordPolicyState pwPolicyState;
      try
      {
        AuthenticationPolicy policy = AuthenticationPolicy.forUser(userEntry, false);
        if (!policy.isPasswordPolicy())
        {
          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ACCOUNT_NOT_LOCAL.get(userDN));
          return;
        }
        pwPolicyState = (PasswordPolicyState) policy.createAuthenticationPolicyState(userEntry);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        operation.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_GET_PW_POLICY.get(userDN, de.getMessageObject()));
        return;
      }

      // Determine whether the user is changing his own password or if it's an administrative reset.
      // If it's an administrative reset, then the requester must have the PASSWORD_RESET privilege.
      boolean selfChange = isSelfChange(userIdentity, requestorEntry, userDN, oldPassword);

      if (! selfChange)
      {
        ClientConnection clientConnection = operation.getClientConnection();
        if (! clientConnection.hasPrivilege(Privilege.PASSWORD_RESET, operation))
        {
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_INSUFFICIENT_PRIVILEGES.get());
          operation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          return;
        }
      }

      // See if the account is locked.  If so, then reject the request.
      if (pwPolicyState.isDisabled())
      {
        addPwPolicyErrorResponseControl(operation, pwPolicyRequested, ACCOUNT_LOCKED);

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ACCOUNT_DISABLED.get());
        return;
      }
      else if (selfChange && pwPolicyState.isLocked())
      {
        addPwPolicyErrorResponseControl(operation, pwPolicyRequested, ACCOUNT_LOCKED);

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_ACCOUNT_LOCKED.get());
        return;
      }

      // If the current password was provided, then we'll need to verify whether it was correct.
      // If it wasn't provided but this is a self change, then make sure that's OK.
      if (oldPassword == null)
      {
        if (selfChange
            && pwPolicyState.getAuthenticationPolicy().isPasswordChangeRequiresCurrentPassword())
        {
          addPwPolicyErrorResponseControl(operation, pwPolicyRequested, MUST_SUPPLY_OLD_PASSWORD);

          operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_REQUIRE_CURRENT_PW.get());
          return;
        }
      }
      else
      {
        if (pwPolicyState.getAuthenticationPolicy().isRequireSecureAuthentication()
            && !operation.getClientConnection().isSecure())
        {
          operation.setResultCode(ResultCode.CONFIDENTIALITY_REQUIRED);
          operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(getClass(), "additionalInfo",
              ERR_EXTOP_PASSMOD_SECURE_AUTH_REQUIRED.get()));
          return;
        }

        if (pwPolicyState.passwordMatches(oldPassword))
        {
          pwPolicyState.setLastLoginTime();
        }
        else
        {
          operation.setResultCode(ResultCode.INVALID_CREDENTIALS);
          operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(getClass(), "additionalInfo",
              ERR_EXTOP_PASSMOD_INVALID_OLD_PASSWORD.get()));

          pwPolicyState.updateAuthFailureTimes();
          List<Modification> mods = pwPolicyState.getModifications();
          if (! mods.isEmpty())
          {
            getRootConnection().processModify(userDN, mods);
          }

          return;
        }
      }

      // If it is a self password change and we don't allow that, then reject the request.
      if (selfChange
          && !pwPolicyState.getAuthenticationPolicy().isAllowUserPasswordChanges())
      {
        addPwPolicyErrorResponseControl(operation, pwPolicyRequested, PASSWORD_MOD_NOT_ALLOWED);

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_USER_PW_CHANGES_NOT_ALLOWED.get());
        return;
      }

      // If we require secure password changes and the connection isn't secure, then reject the request.
      if (pwPolicyState.getAuthenticationPolicy().isRequireSecurePasswordChanges()
          && !operation.getClientConnection().isSecure())
      {
        operation.setResultCode(ResultCode.CONFIDENTIALITY_REQUIRED);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_SECURE_CHANGES_REQUIRED.get());
        return;
      }

      // If it's a self-change request and the user is within the minimum age, then reject it.
      if (selfChange && pwPolicyState.isWithinMinimumAge())
      {
        addPwPolicyErrorResponseControl(operation, pwPolicyRequested, PASSWORD_TOO_YOUNG);

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_IN_MIN_AGE.get());
        return;
      }

      // If the user's password is expired and it's a self-change request, then see if that's OK.
      if (selfChange
          && pwPolicyState.isPasswordExpired()
          && !pwPolicyState.getAuthenticationPolicy().isAllowExpiredPasswordChanges())
      {
        addPwPolicyErrorResponseControl(operation, pwPolicyRequested, PasswordPolicyErrorType.PASSWORD_EXPIRED);

        operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_PASSWORD_IS_EXPIRED.get());
        return;
      }

      // If the a new password was provided, then perform any appropriate validation on it.
      // If not, then see if we can generate one.
      boolean generatedPassword = false;
      boolean isPreEncoded      = false;
      if (newPassword == null)
      {
        try
        {
          newPassword = pwPolicyState.generatePassword();
          if (newPassword == null)
          {
            operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_NO_PW_GENERATOR.get());
            return;
          }

          generatedPassword = true;
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          operation.setResultCode(de.getResultCode());
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_GENERATE_PW.get(de.getMessageObject()));
          return;
        }
        // Prepare to update the password history, if necessary.
        if (pwPolicyState.maintainHistory())
        {
          if (pwPolicyState.isPasswordInHistory(newPassword))
          {
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_PW_IN_HISTORY.get());
            return;
          }
          else
          {
            pwPolicyState.updatePasswordHistory();
          }
        }
      }
      else if (pwPolicyState.passwordIsPreEncoded(newPassword))
      {
        // The password modify extended operation isn't intended to be invoked
        // by an internal operation or during synchronization, so we don't
        // need to check for those cases.
        isPreEncoded = true;
        if (!pwPolicyState.getAuthenticationPolicy().isAllowPreEncodedPasswords())
        {
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_PRE_ENCODED_NOT_ALLOWED.get());
          return;
        }
      }
      else
      {
        // Run the new password through the set of password validators.
        if (selfChange || !pwPolicyState.getAuthenticationPolicy().isSkipValidationForAdministrators())
        {
          Set<ByteString> clearPasswords = new HashSet<>(pwPolicyState.getClearPasswords());
          if (oldPassword != null)
          {
            clearPasswords.add(oldPassword);
          }

          LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
          if (!pwPolicyState.passwordIsAcceptable(operation, userEntry, newPassword, clearPasswords, invalidReason))
          {
            addPwPolicyErrorResponseControl(operation, pwPolicyRequested, INSUFFICIENT_PASSWORD_QUALITY);

            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            operation.appendErrorMessage(ERR_EXTOP_PASSMOD_UNACCEPTABLE_PW.get(invalidReason));
            return;
          }
        }

        // Prepare to update the password history, if necessary.
        if (pwPolicyState.maintainHistory())
        {
          if (pwPolicyState.isPasswordInHistory(newPassword))
          {
            if (selfChange || !pwPolicyState.getAuthenticationPolicy().isSkipValidationForAdministrators())
            {
              operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              operation.appendErrorMessage(ERR_EXTOP_PASSMOD_PW_IN_HISTORY.get());
              return;
            }
          }
          else
          {
            pwPolicyState.updatePasswordHistory();
          }
        }
      }

      // Get the encoded forms of the new password.
      List<ByteString> encodedPasswords;
      if (isPreEncoded)
      {
        encodedPasswords = newArrayList(newPassword);
      }
      else
      {
        try
        {
          encodedPasswords = pwPolicyState.encodePassword(newPassword);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          operation.setResultCode(de.getResultCode());
          operation.appendErrorMessage(ERR_EXTOP_PASSMOD_CANNOT_ENCODE_PASSWORD.get(de.getMessageObject()));
          return;
        }
      }

      // If the current password was provided, then remove all matching values from the user's entry
      // and replace them with the new password.  Otherwise replace all password values.
      AttributeType attrType = pwPolicyState.getAuthenticationPolicy().getPasswordAttribute();
      List<Modification> modList = new ArrayList<>();
      if (oldPassword != null)
      {
        // Remove all existing encoded values that match the old password.
        Set<ByteString> existingValues = pwPolicyState.getPasswordValues();
        Set<ByteString> deleteValues = new LinkedHashSet<>(existingValues.size());

        for (ByteString v : existingValues)
        {
          try
          {
            String[] components = decodePassword(pwPolicyState, v.toString());
            PasswordStorageScheme<?> scheme = getPasswordStorageScheme(pwPolicyState, components[0]);
            if (// The password is encoded using an unknown scheme.  Remove it from the user's entry.
                scheme == null
                || passwordMatches(pwPolicyState, scheme, oldPassword, components))
            {
              deleteValues.add(v);
            }
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            // We couldn't decode the provided password value, so remove it from the user's entry.
            deleteValues.add(v);
          }
        }

        modList.add(newModification(ModificationType.DELETE, attrType, deleteValues));
        modList.add(newModification(ModificationType.ADD, attrType, encodedPasswords));
      }
      else
      {
        modList.add(newModification(ModificationType.REPLACE, attrType, encodedPasswords));
      }

      // Update the password changed time for the user entry.
      pwPolicyState.setPasswordChangedTime();

      // If the password was changed by an end user, then clear any reset flag that might exist.
      // If the password was changed by an administrator, then see if we need to set the reset flag.
      pwPolicyState.setMustChangePassword(
          !selfChange && pwPolicyState.getAuthenticationPolicy().isForceChangeOnReset());

      // Clear any record of grace logins, auth failures, and expiration warnings.
      pwPolicyState.clearFailureLockout();
      pwPolicyState.clearGraceLoginTimes();
      pwPolicyState.clearWarnedTime();

      // If the LDAP no-op control was included in the request, then set the
      // appropriate response.  Otherwise, process the operation.
      if (noOpRequested)
      {
        operation.appendErrorMessage(WARN_EXTOP_PASSMOD_NOOP.get());
        operation.setResultCode(ResultCode.NO_OPERATION);
        return;
      }

      if (selfChange && requestorEntry == null)
      {
        requestorEntry = userEntry;
      }

      // Get an internal connection and use it to perform the modification.
      boolean isRoot = DirectoryServer.isRootDN(requestorEntry.getName());
      AuthenticationInfo authInfo = new AuthenticationInfo(requestorEntry, isRoot);
      InternalClientConnection internalConnection = new InternalClientConnection(authInfo);

      ModifyOperation modifyOperation = internalConnection.processModify(userDN, modList);
      ResultCode resultCode = modifyOperation.getResultCode();
      if (resultCode != ResultCode.SUCCESS)
      {
        operation.setResultCode(resultCode);
        operation.setErrorMessage(modifyOperation.getErrorMessage());
        // FIXME should it also call setMatchedDN()
        operation.setReferralURLs(modifyOperation.getReferralURLs());
        return;
      }

      // If there were any password policy state changes, we need to apply
      // them using a root connection because the end user may not have
      // sufficient access to apply them.  This is less efficient than
      // doing them all in the same modification, but it's safer.
      List<Modification> pwPolicyMods = pwPolicyState.getModifications();
      if (! pwPolicyMods.isEmpty())
      {
        ModifyOperation modOp = getRootConnection().processModify(userDN, pwPolicyMods);
        if (modOp.getResultCode() != ResultCode.SUCCESS)
        {
          // At this point, the user's password is already changed so there's
          // not much point in returning a non-success result.  However, we
          // should at least log that something went wrong.
          logger.warn(WARN_EXTOP_PASSMOD_CANNOT_UPDATE_PWP_STATE, userDN, modOp.getResultCode(),
              modOp.getErrorMessage());
        }
      }

      // If we've gotten here, then everything is OK, so indicate that the operation was successful.
      operation.setResultCode(ResultCode.SUCCESS);

      // Save attachments for post-op plugins (e.g. Samba password plugin).
      operation.setAttachment(AUTHZ_DN_ATTACHMENT, userDN);
      operation.setAttachment(PWD_ATTRIBUTE_ATTACHMENT, pwPolicyState.getAuthenticationPolicy().getPasswordAttribute());
      if (!isPreEncoded)
      {
        operation.setAttachment(CLEAR_PWD_ATTACHMENT, newPassword);
      }
      operation.setAttachment(ENCODED_PWD_ATTACHMENT, encodedPasswords);

      // If a password was generated, then include it in the response.
      if (generatedPassword)
      {
        ByteStringBuilder builder = new ByteStringBuilder();
        ASN1Writer writer = ASN1.getWriter(builder);

        try
        {
          writer.writeStartSequence();
          writer.writeOctetString(TYPE_PASSWORD_MODIFY_GENERATED_PASSWORD, newPassword);
          writer.writeEndSequence();
        }
        catch (IOException e)
        {
          logger.traceException(e);
        }

        operation.setResponseValue(builder.toByteString());
      }


      // If this was a self password change, and the client is authenticated as the user whose password was changed,
      // then clear the "must change password" flag in the client connection.  Note that we're using the
      // authentication DN rather than the authorization DN in this case to avoid mistakenly clearing the flag
      // for the wrong user.
      if (selfChange
          && authInfo.getAuthenticationDN() != null
          && authInfo.getAuthenticationDN().equals(userDN))
      {
        operation.getClientConnection().setMustChangePassword(false);
      }

      addPwPolicyErrorResponseControl(operation, pwPolicyRequested, null);

      generateAccountStatusNotification(oldPassword, newPassword, userEntry, pwPolicyState, selfChange);
    }
    finally
    {
      if (userLock != null)
      {
        userLock.unlock();
      }
    }
  }

  private void addPwPolicyErrorResponseControl(ExtendedOperation operation, boolean pwPolicyRequested,
      PasswordPolicyErrorType pwPolicyErrorType)
  {
    if (pwPolicyRequested)
    {
      operation.addResponseControl(new PasswordPolicyResponseControl(null, 0, pwPolicyErrorType));
    }
  }

  private void generateAccountStatusNotification(ByteString oldPassword, ByteString newPassword, Entry userEntry,
      PasswordPolicyState pwPolicyState, boolean selfChange)
  {
    List<ByteString> currentPasswords = null;
    if (oldPassword != null)
    {
      currentPasswords = newArrayList(oldPassword);
    }
    List<ByteString> newPasswords = newArrayList(newPassword);

    Map<AccountStatusNotificationProperty, List<String>> notifProperties =
        AccountStatusNotification.createProperties(pwPolicyState, false, -1, currentPasswords, newPasswords);
    if (selfChange)
    {
      pwPolicyState.generateAccountStatusNotification(
          PASSWORD_CHANGED, userEntry, INFO_MODIFY_PASSWORD_CHANGED.get(), notifProperties);
    }
    else
    {
      pwPolicyState.generateAccountStatusNotification(
          PASSWORD_RESET, userEntry, INFO_MODIFY_PASSWORD_RESET.get(), notifProperties);
    }
  }

  private String[] decodePassword(PasswordPolicyState pwPolicyState, String encodedPassword) throws DirectoryException
  {
    return pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax()
        ? AuthPasswordSyntax.decodeAuthPassword(encodedPassword)
        : UserPasswordSyntax.decodeUserPassword(encodedPassword);
  }

  private PasswordStorageScheme<?> getPasswordStorageScheme(PasswordPolicyState pwPolicyState, String scheme)
  {
    return pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax()
        ? DirectoryServer.getAuthPasswordStorageScheme(scheme)
        : DirectoryServer.getPasswordStorageScheme(toLowerCase(scheme));
  }

  private boolean passwordMatches(
      PasswordPolicyState pwPolicyState, PasswordStorageScheme<?> scheme, ByteString oldPassword, String[] components)
  {
    return pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax()
        ? scheme.authPasswordMatches(oldPassword, components[1], components[2])
        : scheme.passwordMatches(oldPassword, ByteString.valueOfUtf8(components[1]));
  }

  private boolean isSelfChange(ByteString userIdentity, Entry requestorEntry, DN userDN, ByteString oldPassword)
  {
    if (userIdentity == null)
    {
      return true;
    }
    else if (requestorEntry != null)
    {
      return userDN.equals(requestorEntry.getName());
    }
    else
    {
      return oldPassword != null;
    }
  }

  private Modification newModification(ModificationType modType, AttributeType attrType, Collection<ByteString> value)
  {
    AttributeBuilder builder = new AttributeBuilder(attrType);
    builder.addAll(value);
    return new Modification(modType, builder.toAttribute());
  }


  /**
   * Retrieves the entry for the specified user based on the provided DN.  If any problem is encountered or
   * the requested entry does not exist, then the provided operation will be updated with appropriate result
   * information and this method will return <CODE>null</CODE>.
   * The caller must hold a write lock on the specified entry.
   *
   * @param  operation  The extended operation being processed.
   * @param  entryDN    The DN of the user entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if there was no such entry or it could not be retrieved.
   */
  private Entry getEntryByDN(ExtendedOperation operation, DN entryDN)
  {
    // Retrieve the user's entry from the directory.  If it does not exist, then fail.
    try
    {
      Entry userEntry = DirectoryServer.getEntry(entryDN);

      if (userEntry == null)
      {
        operation.setResultCode(ResultCode.NO_SUCH_OBJECT);
        operation.appendErrorMessage(ERR_EXTOP_PASSMOD_NO_USER_ENTRY_BY_AUTHZID.get(entryDN));

        // See if one of the entry's ancestors exists.
        operation.setMatchedDN(findMatchedDN(entryDN));
        return null;
      }

      return userEntry;
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      operation.setResultCode(de.getResultCode());
      operation.appendErrorMessage(de.getMessageObject());
      operation.setMatchedDN(de.getMatchedDN());
      operation.setReferralURLs(de.getReferralURLs());
      return null;
    }
  }

  private DN findMatchedDN(DN entryDN)
  {
    try
    {
      BackendConfigManager backendConfigManager =
          DirectoryServer.getInstance().getServerContext().getBackendConfigManager();
      DN matchedDN = backendConfigManager.getParentDNInSuffix(entryDN);
      while (matchedDN != null)
      {
        if (DirectoryServer.entryExists(matchedDN))
        {
          return matchedDN;
        }

        matchedDN = backendConfigManager.getParentDNInSuffix(matchedDN);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return null;
  }

  @Override
  public boolean isConfigurationAcceptable(ExtendedOperationHandlerCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    PasswordModifyExtendedOperationHandlerCfg config = (PasswordModifyExtendedOperationHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(PasswordModifyExtendedOperationHandlerCfg config,
                                                 List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      // Make sure that the specified identity mapper is OK.
      DN mapperDN = config.getIdentityMapperDN();
      IdentityMapper<?> mapper = DirectoryServer.getIdentityMapper(mapperDN);
      if (mapper == null)
      {
        unacceptableReasons.add(ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(mapperDN, config.dn()));
        return false;
      }
      return true;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER.get(config.dn(), getExceptionMessage(e)));
      return false;
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(PasswordModifyExtendedOperationHandlerCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // Make sure that the specified identity mapper is OK.
    DN             mapperDN = null;
    IdentityMapper<?> mapper   = null;
    try
    {
      mapperDN = config.getIdentityMapperDN();
      mapper   = DirectoryServer.getIdentityMapper(mapperDN);
      if (mapper == null)
      {
        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_EXTOP_PASSMOD_NO_SUCH_ID_MAPPER.get(mapperDN, config.dn()));
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_EXTOP_PASSMOD_CANNOT_DETERMINE_ID_MAPPER.get(config.dn(), getExceptionMessage(e)));
    }

    // If all of the changes were acceptable, then apply them.
    if (ccr.getResultCode() == ResultCode.SUCCESS
        && ! identityMapperDN.equals(mapperDN))
    {
      identityMapper   = mapper;
      identityMapperDN = mapperDN;
    }

    // Save this configuration for future reference.
    currentConfig = config;

    return ccr;
  }

  @Override
  public String getExtendedOperationOID()
  {
    return OID_PASSWORD_MODIFY_REQUEST;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "Password Modify";
  }
}
