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
 * Copyright 2008-2011 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg3;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg4;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationWrapper;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.core.PersistentSearch;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.types.AbstractOperation.*;
import static org.opends.server.types.AccountStatusNotificationType.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/** This class defines an operation used to modify an entry in a local backend of the Directory Server. */
public class LocalBackendModifyOperation
       extends ModifyOperationWrapper
       implements PreOperationModifyOperation, PostOperationModifyOperation,
                  PostResponseModifyOperation,
                  PostSynchronizationModifyOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the target entry exists. */
  private Backend<?> backend;
  /** The client connection associated with this operation. */
  private ClientConnection clientConnection;
  private boolean preOperationPluginsExecuted;

  /** Indicates whether this modify operation includes a password change. */
  private boolean passwordChanged;
  /** Indicates whether the password change is a self-change. */
  private boolean selfChange;
  /** Indicates whether the request included the user's current password. */
  private boolean currentPasswordProvided;
  /** Indicates whether the user's account has been enabled or disabled by this modify operation. */
  private boolean enabledStateChanged;
  /** Indicates whether the user's account is currently enabled. */
  private boolean isEnabled;
  /** Indicates whether the user's account was locked before this change. */
  private boolean wasLocked;

  /** Indicates whether the request included the LDAP no-op control. */
  private boolean noOp;
  /** Indicates whether the request included the Permissive Modify control. */
  private boolean permissiveModify;
  /** Indicates whether the request included the password policy request control. */
  private boolean pwPolicyControlRequested;
  /** The post-read request control, if present. */
  private LDAPPostReadRequestControl postReadRequest;
  /** The pre-read request control, if present. */
  private LDAPPreReadRequestControl preReadRequest;

  /** The DN of the entry to modify. */
  private DN entryDN;
  /** The current entry, before any changes are applied. */
  private Entry currentEntry;
  /** The modified entry that will be stored in the backend. */
  private Entry modifiedEntry;
  /** The set of modifications contained in this request. */
  private List<Modification> modifications;

  /** The number of passwords contained in the modify operation. */
  private int numPasswords;

  /** The set of clear-text current passwords (if any were provided). */
  private List<ByteString> currentPasswords;
  /** The set of clear-text new passwords (if any were provided). */
  private List<ByteString> newPasswords;

  /** The password policy error type for this operation. */
  private PasswordPolicyErrorType pwpErrorType;
  /** The password policy state for this modify operation. */
  private PasswordPolicyState pwPolicyState;


  /**
   * Creates a new operation that may be used to modify an entry in a
   * local backend of the Directory Server.
   *
   * @param modify The operation to enhance.
   */
  public LocalBackendModifyOperation(ModifyOperation modify)
  {
    super(modify);
    LocalBackendWorkflowElement.attachLocalOperation (modify, this);
  }

  /**
   * Returns whether authentication for this user is managed locally
   * or via Pass-Through Authentication.
   */
  private boolean isAuthnManagedLocally()
  {
    return pwPolicyState != null;
  }

  /**
   * Retrieves the current entry before any modifications are applied.  This
   * will not be available to pre-parse plugins.
   *
   * @return  The current entry, or {@code null} if it is not yet available.
   */
  @Override
  public final Entry getCurrentEntry()
  {
    return currentEntry;
  }



  /**
   * Retrieves the set of clear-text current passwords for the user, if
   * available.  This will only be available if the modify operation contains
   * one or more delete elements that target the password attribute and provide
   * the values to delete in the clear.  It will not be available to pre-parse
   * plugins.
   *
   * @return  The set of clear-text current password values as provided in the
   *          modify request, or {@code null} if there were none or this
   *          information is not yet available.
   */
  @Override
  public final List<ByteString> getCurrentPasswords()
  {
    return currentPasswords;
  }



  /**
   * Retrieves the modified entry that is to be written to the backend.  This
   * will be available to pre-operation plugins, and if such a plugin does make
   * a change to this entry, then it is also necessary to add that change to
   * the set of modifications to ensure that the update will be consistent.
   *
   * @return  The modified entry that is to be written to the backend, or
   *          {@code null} if it is not yet available.
   */
  @Override
  public final Entry getModifiedEntry()
  {
    return modifiedEntry;
  }



  /**
   * Retrieves the set of clear-text new passwords for the user, if available.
   * This will only be available if the modify operation contains one or more
   * add or replace elements that target the password attribute and provide the
   * values in the clear.  It will not be available to pre-parse plugins.
   *
   * @return  The set of clear-text new passwords as provided in the modify
   *          request, or {@code null} if there were none or this
   *          information is not yet available.
   */
  @Override
  public final List<ByteString> getNewPasswords()
  {
    return newPasswords;
  }



  /**
   * Adds the provided modification to the set of modifications to this modify operation.
   * In addition, the modification is applied to the modified entry.
   * <p>
   * This may only be called by pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of changes for
   *                       this modify operation.
   * @throws  DirectoryException  If an unexpected problem occurs while applying
   *                              the modification to the entry.
   */
  @Override
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modifiedEntry.applyModification(modification, permissiveModify);
    super.addModification(modification);
  }



  /**
   * Process this modify operation against a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  void processLocalModify(final LocalBackendWorkflowElement wfe) throws CanceledOperationException
  {
    this.backend = wfe.getBackend();
    this.clientConnection = getClientConnection();

    checkIfCanceled(false);
    try
    {
      processModify();

      if (pwPolicyControlRequested)
      {
        addResponseControl(new PasswordPolicyResponseControl(null, 0, pwpErrorType));
      }

      invokePostModifyPlugins();
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }


    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {
        @Override
        public void run()
        {
          for (PersistentSearch psearch : backend.getPersistentSearches())
          {
            psearch.processModify(modifiedEntry, currentEntry);
          }
        }
      });
    }
  }

  private boolean invokePreModifyPlugins() throws CanceledOperationException
  {
    if (!isSynchronizationOperation())
    {
      preOperationPluginsExecuted = true;
      if (!processOperationResult(this, getPluginConfigManager().invokePreOperationModifyPlugins(this)))
      {
        return false;
      }
    }
    return true;
  }

  private void invokePostModifyPlugins()
  {
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        getPluginConfigManager().invokePostSynchronizationModifyPlugins(this);
      }
    }
    else if (preOperationPluginsExecuted)
    {
      PostOperation result = getPluginConfigManager().invokePostOperationModifyPlugins(this);
      if (!processOperationResult(this, result))
      {
        return;
      }
    }
  }

  private void processModify() throws CanceledOperationException
  {
    entryDN = getEntryDN();
    if (entryDN == null)
    {
      return;
    }
    if (backend == null)
    {
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_MODIFY_NO_BACKEND_FOR_ENTRY.get(entryDN));
      return;
    }

    // Process the modifications to convert them from their raw form to the
    // form required for the rest of the modify processing.
    modifications = getModifications();
    if (modifications == null)
    {
      return;
    }

    if (modifications.isEmpty())
    {
      setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      appendErrorMessage(ERR_MODIFY_NO_MODIFICATIONS.get(entryDN));
      return;
    }

    checkIfCanceled(false);

    // Acquire a write lock on the target entry.
    final DNLock entryLock = DirectoryServer.getLockManager().tryWriteLockEntry(entryDN);
    try
    {
      if (entryLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_MODIFY_CANNOT_LOCK_ENTRY.get(entryDN));
        return;
      }

      checkIfCanceled(false);

      currentEntry = backend.getEntry(entryDN);
      if (currentEntry == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));
        setMatchedDN(findMatchedDN(entryDN));
        return;
      }

      processRequestControls();

      // Get the password policy state object for the entry that can be used
      // to perform any appropriate password policy processing. Also, see
      // if the entry is being updated by the end user or an administrator.
      final DN authzDN = getAuthorizationDN();
      selfChange = entryDN.equals(authzDN);

      // Should the authorizing account change its password?
      if (mustChangePassword(selfChange, getAuthorizationEntry()))
      {
        pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
        setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get(authzDN != null ? authzDN : "anonymous"));
        return;
      }

      // FIXME -- Need a way to enable debug mode.
      pwPolicyState = createPasswordPolicyState(currentEntry);

      // Create a duplicate of the entry and apply the changes to it.
      modifiedEntry = currentEntry.duplicate(false);

      if (!noOp && !handleConflictResolution())
      {
        return;
      }

      processNonPasswordModifications();

      // Check to see if the client has permission to perform the modify.
      // The access control check is not made any earlier because the handler
      // needs access to the modified entry.

      // FIXME: for now assume that this will check all permissions pertinent to the operation.
      // This includes proxy authorization and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists may have
      // already exposed sensitive information to the client.
      if (!operationIsAllowed())
      {
        return;
      }

      if (isAuthnManagedLocally())
      {
        processPasswordPolicyModifications();
        performAdditionalPasswordChangedProcessing();

        if (currentUserMustChangePassword())
        {
          // The user did not attempt to change their password.
          pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
          setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get(authzDN != null ? authzDN : "anonymous"));
          return;
        }
      }

      if (mustCheckSchema())
      {
        // make sure that the new entry is valid per the server schema.
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (!modifiedEntry.conformsToSchema(null, false, false, false, invalidReason))
        {
          setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
          appendErrorMessage(ERR_MODIFY_VIOLATES_SCHEMA.get(entryDN, invalidReason));
          return;
        }
      }

      checkIfCanceled(false);

      if (!invokePreModifyPlugins())
      {
        return;
      }

      // Actually perform the modify operation. This should also include
      // taking care of any synchronization that might be needed.
      LocalBackendWorkflowElement.checkIfBackendIsWritable(backend, this,
          entryDN, ERR_MODIFY_SERVER_READONLY, ERR_MODIFY_BACKEND_READONLY);

      if (noOp)
      {
        appendErrorMessage(INFO_MODIFY_NOOP.get());
        setResultCode(ResultCode.NO_OPERATION);
      }
      else
      {
        if (!processPreOperation())
        {
          return;
        }

        backend.replaceEntry(currentEntry, modifiedEntry, this);

        if (isAuthnManagedLocally())
        {
          generatePwpAccountStatusNotifications();
        }
      }

      // Handle any processing that may be needed for the pre-read and/or post-read controls.
      LocalBackendWorkflowElement.addPreReadResponse(this, preReadRequest, currentEntry);
      LocalBackendWorkflowElement.addPostReadResponse(this, postReadRequest, modifiedEntry);

      if (!noOp)
      {
        setResultCode(ResultCode.SUCCESS);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      setResponseData(de);
    }
    finally
    {
      if (entryLock != null)
      {
        entryLock.unlock();
      }
      processSynchPostOperationPlugins();
    }
  }

  private boolean operationIsAllowed()
  {
    try
    {
      if (!getAccessControlHandler().isAllowed(this))
      {
        setResultCodeAndMessageNoInfoDisclosure(modifiedEntry,
            ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            ERR_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
        return false;
      }
      return true;
    }
    catch (DirectoryException e)
    {
      setResultCode(e.getResultCode());
      appendErrorMessage(e.getMessageObject());
      return false;
    }
  }

  private boolean currentUserMustChangePassword()
  {
    return !isInternalOperation() && selfChange && !passwordChanged && pwPolicyState.mustChangePassword();
  }

  private boolean mustChangePassword(boolean selfChange, Entry authzEntry) throws DirectoryException
  {
    return !isInternalOperation() && !selfChange && authzEntry != null && mustChangePassword(authzEntry);
  }

  private boolean mustChangePassword(Entry authzEntry) throws DirectoryException
  {
    PasswordPolicyState authzState = createPasswordPolicyState(authzEntry);
    return authzState != null && authzState.mustChangePassword();
  }

  private PasswordPolicyState createPasswordPolicyState(Entry entry) throws DirectoryException
  {
    AuthenticationPolicy policy = AuthenticationPolicy.forUser(entry, true);
    if (policy.isPasswordPolicy())
    {
      return (PasswordPolicyState) policy.createAuthenticationPolicyState(entry);
    }
    return null;
  }

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  private DirectoryException newDirectoryException(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, entry,
        entryDN, resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(Entry entry,
      ResultCode realResultCode, LocalizableMessage realMessage) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        entry, entryDN, realResultCode, realMessage, ResultCode.NO_SUCH_OBJECT,
        ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));
  }

  /**
   * Processes any controls contained in the modify request.
   *
   * @throws  DirectoryException  If a problem is encountered with any of the
   *                              controls.
   */
  private void processRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    for (ListIterator<Control> iter = getRequestControls().listIterator(); iter.hasNext();)
    {
      final Control c = iter.next();
      final String oid = c.getOID();

      if (OID_LDAP_ASSERTION.equals(oid))
      {
        LDAPAssertionRequestControl assertControl = getRequestControl(LDAPAssertionRequestControl.DECODER);

        SearchFilter filter;
        try
        {
          filter = assertControl.getSearchFilter();
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          throw newDirectoryException(currentEntry, de.getResultCode(),
              ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }

        // Check if the current user has permission to make this determination.
        if (!getAccessControlHandler().isAllowed(this, currentEntry, filter))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        try
        {
          if (!filter.matchesEntry(currentEntry))
          {
            throw newDirectoryException(currentEntry, ResultCode.ASSERTION_FAILED,
                ERR_MODIFY_ASSERTION_FAILED.get(entryDN));
          }
        }
        catch (DirectoryException de)
        {
          if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
          {
            throw de;
          }

          logger.traceException(de);

          throw newDirectoryException(currentEntry, de.getResultCode(),
              ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }
      }
      else if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
      {
        noOp = true;
      }
      else if (OID_PERMISSIVE_MODIFY_CONTROL.equals(oid))
      {
        permissiveModify = true;
      }
      else if (OID_LDAP_READENTRY_PREREAD.equals(oid))
      {
        preReadRequest = getRequestControl(LDAPPreReadRequestControl.DECODER);
      }
      else if (OID_LDAP_READENTRY_POSTREAD.equals(oid))
      {
        if (c instanceof LDAPPostReadRequestControl)
        {
          postReadRequest = (LDAPPostReadRequestControl) c;
        }
        else
        {
          postReadRequest = getRequestControl(LDAPPostReadRequestControl.DECODER);
          iter.set(postReadRequest);
        }
      }
      else if (LocalBackendWorkflowElement.isProxyAuthzControl(oid))
      {
        continue;
      }
      else if (OID_PASSWORD_POLICY_CONTROL.equals(oid))
      {
        pwPolicyControlRequested = true;
      }
      else if (c.isCritical() && !backend.supportsControl(oid))
      {
        throw newDirectoryException(currentEntry, ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            ERR_MODIFY_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
      }
    }
  }

  private void processNonPasswordModifications() throws DirectoryException
  {
    for (Modification m : modifications)
    {
      Attribute     a = m.getAttribute();
      AttributeDescription attrDesc = a.getAttributeDescription();
      AttributeType t = attrDesc.getAttributeType();


      // If the attribute type is marked "NO-USER-MODIFICATION" then fail unless
      // this is an internal operation or is related to synchronization in some way.
      final boolean isInternalOrSynchro = isInternalOrSynchro(m);
      if (t.isNoUserModification() && !isInternalOrSynchro)
      {
        throw newDirectoryException(currentEntry,
            ResultCode.CONSTRAINT_VIOLATION,
            ERR_MODIFY_ATTR_IS_NO_USER_MOD.get(entryDN, attrDesc));
      }

      // If the attribute type is marked "OBSOLETE" and the modification is
      // setting new values, then fail unless this is an internal operation or
      // is related to synchronization in some way.
      if (t.isObsolete()
          && !a.isEmpty()
          && m.getModificationType() != ModificationType.DELETE
          && !isInternalOrSynchro)
      {
        throw newDirectoryException(currentEntry,
            ResultCode.CONSTRAINT_VIOLATION,
            ERR_MODIFY_ATTR_IS_OBSOLETE.get(entryDN, attrDesc));
      }


      // See if the attribute is one which controls the privileges available for a user.
      // If it is, then the client must have the PRIVILEGE_CHANGE privilege.
      if (t.hasName(OP_ATTR_PRIVILEGE_NAME)
          && !clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this))
      {
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
      }

      // If the modification is not updating the password attribute,
      // then perform any schema processing.
      if (!isPassword(t))
      {
        processModification(m);
      }
    }
  }

  private boolean isInternalOrSynchro(Modification m)
  {
    return isInternalOperation() || m.isInternal() || isSynchronizationOperation();
  }

  private boolean isPassword(AttributeType t)
  {
    return pwPolicyState != null
        && t.equals(pwPolicyState.getAuthenticationPolicy().getPasswordAttribute());
  }

  /** Processes the modifications related to password policy for this modify operation. */
  private void processPasswordPolicyModifications() throws DirectoryException
  {
    // Declare variables used for password policy state processing.
    currentPasswordProvided = false;
    isEnabled = true;
    enabledStateChanged = false;

    final PasswordPolicy authPolicy = pwPolicyState.getAuthenticationPolicy();
    if (currentEntry.hasAttribute(authPolicy.getPasswordAttribute()))
    {
      // It may actually have more than one, but we can't tell the difference if
      // the values are encoded, and its enough for our purposes just to know
      // that there is at least one.
      numPasswords = 1;
    }
    else
    {
      numPasswords = 0;
    }

    passwordChanged = !isInternalOperation() && !isSynchronizationOperation() && isModifyingPassword();


    for (Modification m : modifications)
    {
      AttributeType t = m.getAttribute().getAttributeDescription().getAttributeType();

      // If the modification is updating the password attribute, then perform
      // any necessary password policy processing.  This processing should be
      // skipped for synchronization operations.
      if (isPassword(t))
      {
        if (!isSynchronizationOperation())
        {
          // If the attribute contains any options and new values are going to
          // be added, then reject it. Passwords will not be allowed to have options.
          if (!isInternalOperation())
          {
            validatePasswordModification(m, authPolicy);
          }
          preProcessPasswordModification(m);
        }

        processModification(m);
      }
      else if (!isInternalOrSynchro(m)
          && t.equals(getSchema().getAttributeType(OP_ATTR_ACCOUNT_DISABLED)))
      {
        enabledStateChanged = true;
        isEnabled = !pwPolicyState.isDisabled();
      }
    }
  }

  /** Adds the appropriate state changes for the provided modification. */
  private void preProcessPasswordModification(Modification m) throws DirectoryException
  {
    switch (m.getModificationType().asEnum())
    {
    case ADD:
    case REPLACE:
      preProcessPasswordAddOrReplace(m);
      break;

    case DELETE:
      preProcessPasswordDelete(m);
      break;

    // case INCREMENT does not make any sense for passwords
    default:
      AttributeDescription attrDesc = m.getAttribute().getAttributeDescription();
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD.get(m.getModificationType(), attrDesc));
    }
  }

  private boolean isModifyingPassword() throws DirectoryException
  {
    for (Modification m : modifications)
    {
      if (isPassword(m.getAttribute().getAttributeDescription().getAttributeType()))
      {
        if (!selfChange && !clientConnection.hasPrivilege(Privilege.PASSWORD_RESET, this))
        {
          pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES.get());
        }
        return true;
      }
    }
    return false;
  }

  private void validatePasswordModification(Modification m, PasswordPolicy authPolicy) throws DirectoryException
  {
    Attribute a = m.getAttribute();
    if (a.getAttributeDescription().hasOptions())
    {
      switch (m.getModificationType().asEnum())
      {
      case REPLACE:
        if (!a.isEmpty())
        {
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
        }
        // Allow delete operations to clean up after import.
        break;
      case ADD:
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
      default:
        // Allow delete operations to clean up after import.
        break;
      }
    }

    // If it's a self change, then see if that's allowed.
    if (selfChange && !authPolicy.isAllowUserPasswordChanges())
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_MODIFY_NO_USER_PW_CHANGES.get());
    }


    // If we require secure password changes, then makes sure it's a
    // secure communication channel.
    if (authPolicy.isRequireSecurePasswordChanges()
        && !clientConnection.isSecure())
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.CONFIDENTIALITY_REQUIRED,
          ERR_MODIFY_REQUIRE_SECURE_CHANGES.get());
    }


    // If it's a self change and it's not been long enough since the
    // previous change, then reject it.
    if (selfChange && pwPolicyState.isWithinMinimumAge())
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_TOO_YOUNG;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_MODIFY_WITHIN_MINIMUM_AGE.get());
    }
  }

  /**
   * Process the provided modification and updates the entry appropriately.
   *
   * @param m
   *          The modification to perform
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void processModification(Modification m) throws DirectoryException
  {
    Attribute attr = m.getAttribute();
    switch (m.getModificationType().asEnum())
    {
    case ADD:
      processAddModification(attr);
      break;

    case DELETE:
      processDeleteModification(attr);
      break;

    case REPLACE:
      processReplaceModification(attr);
      break;

    case INCREMENT:
      processIncrementModification(attr);
      break;
    }
  }

  private void preProcessPasswordAddOrReplace(Modification m) throws DirectoryException
  {
    Attribute pwAttr = m.getAttribute();
    int passwordsToAdd = pwAttr.size();

    if (m.getModificationType() == ModificationType.ADD)
    {
      numPasswords += passwordsToAdd;
    }
    else
    {
      numPasswords = passwordsToAdd;
    }

    // If there were multiple password values, then make sure that's OK.
    final PasswordPolicy authPolicy = pwPolicyState.getAuthenticationPolicy();
    if (!isInternalOperation()
        && !authPolicy.isAllowMultiplePasswordValues()
        && passwordsToAdd > 1)
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED.get());
    }

    // Iterate through the password values and see if any of them are
    // pre-encoded. If so, then check to see if we'll allow it.
    // Otherwise, store the clear-text values for later validation
    // and update the attribute with the encoded values.
    AttributeBuilder builder = new AttributeBuilder(pwAttr.getAttributeDescription());
    for (ByteString v : pwAttr)
    {
      if (pwPolicyState.passwordIsPreEncoded(v))
      {
        if (!isInternalOperation()
            && !authPolicy.isAllowPreEncodedPasswords())
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
        }

        builder.add(v);
      }
      else
      {
        if (m.getModificationType() == ModificationType.ADD
            // Make sure that the password value does not already exist.
            && pwPolicyState.passwordMatches(v))
        {
          pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
          throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
              ERR_MODIFY_PASSWORD_EXISTS.get());
        }

        if (newPasswords == null)
        {
          newPasswords = new LinkedList<>();
        }
        newPasswords.add(v);

        builder.addAll(pwPolicyState.encodePassword(v));
      }
    }

    m.setAttribute(builder.toAttribute());
  }

  private void preProcessPasswordDelete(Modification m) throws DirectoryException
  {
    // Iterate through the password values and see if any of them are pre-encoded.
    // We will never allow pre-encoded passwords for user password changes,
    // but we will allow them for administrators.
    // For each clear-text value, verify that at least one value in the entry matches
    // and replace the clear-text value with the appropriate encoded forms.
    Attribute pwAttr = m.getAttribute();
    if (pwAttr.isEmpty())
    {
      // Removing all current password values.
      numPasswords = 0;
    }

    AttributeDescription pwdAttrDesc = pwAttr.getAttributeDescription();
    AttributeBuilder builder = new AttributeBuilder(pwdAttrDesc);
    for (ByteString v : pwAttr)
    {
      if (pwPolicyState.passwordIsPreEncoded(v))
      {
        if (!isInternalOperation() && selfChange)
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
        }

        // We still need to check if the pre-encoded password matches
        // an existing value, to decrease the number of passwords.
        List<Attribute> attrList = currentEntry.getAttribute(pwdAttrDesc.getAttributeType());
        if (attrList.isEmpty())
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, ERR_MODIFY_NO_EXISTING_VALUES.get());
        }

        if (addIfAttributeValueExistsPreEncodedPassword(builder, attrList, v))
        {
          numPasswords--;
        }
      }
      else
      {
        List<Attribute> attrList = currentEntry.getAttribute(pwdAttrDesc.getAttributeType());
        if (attrList.isEmpty())
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE, ERR_MODIFY_NO_EXISTING_VALUES.get());
        }

        if (addIfAttributeValueExistsNoPreEncodedPassword(builder, attrList, v))
        {
          if (currentPasswords == null)
          {
            currentPasswords = new LinkedList<>();
          }
          currentPasswords.add(v);
          numPasswords--;
        }
        else
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
              ERR_MODIFY_INVALID_PASSWORD.get());
        }

        currentPasswordProvided = true;
      }
    }

    m.setAttribute(builder.toAttribute());
  }

  private boolean addIfAttributeValueExistsPreEncodedPassword(AttributeBuilder builder, List<Attribute> attrList,
      ByteString val)
  {
    for (Attribute attr : attrList)
    {
      for (ByteString av : attr)
      {
        if (av.equals(val))
        {
          builder.add(val);
          return true;
        }
      }
    }
    return false;
  }

  private boolean addIfAttributeValueExistsNoPreEncodedPassword(AttributeBuilder builder, List<Attribute> attrList,
      ByteString val) throws DirectoryException
  {
    boolean found = false;
    for (Attribute attr : attrList)
    {
      for (ByteString av : attr)
      {
        if (pwPolicyState.passwordIsPreEncoded(av))
        {
          if (passwordMatches(val, av))
          {
            builder.add(av);
            found = true;
          }
        }
        else if (av.equals(val))
        {
          builder.add(val);
          found = true;
        }
      }
    }
    return found;
  }

  private boolean passwordMatches(ByteString val, ByteString av) throws DirectoryException
  {
    if (pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax())
    {
      String[] components = AuthPasswordSyntax.decodeAuthPassword(av.toString());
      PasswordStorageScheme<?> scheme = DirectoryServer.getAuthPasswordStorageScheme(components[0]);
      return scheme != null && scheme.authPasswordMatches(val, components[1], components[2]);
    } else {
      String[] components = UserPasswordSyntax.decodeUserPassword(av.toString());
      PasswordStorageScheme<?> scheme = DirectoryServer.getPasswordStorageScheme(toLowerCase(components[0]));
      return scheme != null && scheme.passwordMatches(val, ByteString.valueOfUtf8(components[1]));
    }
  }

  /**
   * Process an add modification and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being added.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void processAddModification(Attribute attr) throws DirectoryException
  {
    // Make sure that one or more values have been provided for the attribute.
    AttributeDescription attrDesc = attr.getAttributeDescription();
    if (attr.isEmpty())
    {
      throw newDirectoryException(currentEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_ADD_NO_VALUES.get(entryDN, attrDesc));
    }

    if (mustCheckSchema())
    {
      // make sure that all the new values are valid according to the associated syntax.
      checkSchema(attr, ERR_MODIFY_ADD_INVALID_SYNTAX, ERR_MODIFY_ADD_INVALID_SYNTAX_NO_VALUE);
    }

    // If the attribute to be added is the object class attribute
    // then make sure that all the object classes are known and not obsoleted.
    if (attrDesc.getAttributeType().isObjectClass())
    {
      validateObjectClasses(attr);
    }

    // Add the provided attribute or merge an existing attribute with
    // the values of the new attribute. If there are any duplicates, then fail.
    List<ByteString> duplicateValues = new LinkedList<>();
    modifiedEntry.addAttribute(attr, duplicateValues);
    if (!duplicateValues.isEmpty() && !permissiveModify)
    {
      String duplicateValuesStr = Utils.joinAsString(", ", duplicateValues);

      throw newDirectoryException(currentEntry,
          ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
          ERR_MODIFY_ADD_DUPLICATE_VALUE.get(entryDN, attrDesc, duplicateValuesStr));
    }
  }

  private boolean mustCheckSchema()
  {
    return !isSynchronizationOperation() && DirectoryServer.checkSchema();
  }

  /**
   * Verifies that all the new values are valid according to the associated syntax.
   *
   * @throws DirectoryException
   *           If any of the new values violate the server schema configuration and server is
   *           configured to reject violations.
   */
  private void checkSchema(Attribute attr,
      Arg4<Object, Object, Object, Object> invalidSyntaxErrorMsg,
      Arg3<Object, Object, Object> invalidSyntaxNoValueErrorMsg) throws DirectoryException
  {
    AcceptRejectWarn syntaxPolicy = DirectoryServer.getSyntaxEnforcementPolicy();
    AttributeDescription attrDesc = attr.getAttributeDescription();
    Syntax syntax = attrDesc.getAttributeType().getSyntax();

    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    for (ByteString v : attr)
    {
      if (!syntax.valueIsAcceptable(v, invalidReason))
      {
        LocalizableMessage msg = isHumanReadable(syntax)
            ? invalidSyntaxErrorMsg.get(entryDN, attrDesc, v, invalidReason)
            : invalidSyntaxNoValueErrorMsg.get(entryDN, attrDesc, invalidReason);

        switch (syntaxPolicy)
        {
        case REJECT:
          throw newDirectoryException(currentEntry, ResultCode.INVALID_ATTRIBUTE_SYNTAX, msg);

        case WARN:
          // FIXME remove next line of code. According to Matt, since this is
          // just a warning, the code should not set the resultCode
          setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
          logger.error(msg);
          invalidReason = new LocalizableMessageBuilder();
          break;
        }
      }
    }
  }

  private boolean isHumanReadable(Syntax syntax)
  {
    return syntax.isHumanReadable() && !syntax.isBEREncodingRequired();
  }

  /**
   * Ensures that the provided object class attribute contains known
   * non-obsolete object classes.
   *
   * @param attr
   *          The object class attribute to validate.
   * @throws DirectoryException
   *           If the attribute contained unknown or obsolete object
   *           classes.
   */
  private void validateObjectClasses(Attribute attr) throws DirectoryException
  {
    final AttributeType attrType = attr.getAttributeDescription().getAttributeType();
    Reject.ifFalse(attrType.isObjectClass());

    for (ByteString v : attr)
    {
      String name = v.toString();
      ObjectClass oc = DirectoryServer.getSchema().getObjectClass(name);
      if (oc.isPlaceHolder())
      {
        throw newDirectoryException(currentEntry,
            ResultCode.OBJECTCLASS_VIOLATION,
            ERR_ENTRY_ADD_UNKNOWN_OC.get(name, entryDN));
      }
      else if (oc.isObsolete())
      {
        throw newDirectoryException(currentEntry,
            ResultCode.CONSTRAINT_VIOLATION,
            ERR_ENTRY_ADD_OBSOLETE_OC.get(name, entryDN));
      }
    }
  }



  /**
   * Process a delete modification and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being deleted.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void processDeleteModification(Attribute attr) throws DirectoryException
  {
    // Remove the specified attribute values or the entire attribute from the value.
    // If there are any specified values that were not present, then fail.
    // If the RDN attribute value would be removed, then fail.
    List<ByteString> missingValues = new LinkedList<>();
    boolean attrExists = modifiedEntry.removeAttribute(attr, missingValues);

    AttributeDescription attrDesc = attr.getAttributeDescription();
    if (attrExists)
    {
      if (missingValues.isEmpty())
      {
        AttributeType t = attrDesc.getAttributeType();

        RDN rdn = modifiedEntry.getName().rdn();
        if (rdn != null
            && rdn.hasAttributeType(t)
            && !modifiedEntry.hasValue(attrDesc, rdn.getAttributeValue(t)))
        {
          throw newDirectoryException(currentEntry,
              ResultCode.NOT_ALLOWED_ON_RDN,
              ERR_MODIFY_DELETE_RDN_ATTR.get(entryDN, attrDesc));
        }
      }
      else if (!permissiveModify)
      {
        String missingValuesStr = Utils.joinAsString(", ", missingValues);

        throw newDirectoryException(currentEntry, ResultCode.NO_SUCH_ATTRIBUTE,
            ERR_MODIFY_DELETE_MISSING_VALUES.get(entryDN, attrDesc, missingValuesStr));
      }
    }
    else if (!permissiveModify)
    {
      throw newDirectoryException(currentEntry, ResultCode.NO_SUCH_ATTRIBUTE,
          ERR_MODIFY_DELETE_NO_SUCH_ATTR.get(entryDN, attrDesc));
    }
  }



  /**
   * Process a replace modification and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being replaced.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void processReplaceModification(Attribute attr) throws DirectoryException
  {
    if (mustCheckSchema())
    {
      // make sure that all the new values are valid according to the associated syntax.
      checkSchema(attr, ERR_MODIFY_REPLACE_INVALID_SYNTAX, ERR_MODIFY_REPLACE_INVALID_SYNTAX_NO_VALUE);
    }

    // If the attribute to be replaced is the object class attribute
    // then make sure that all the object classes are known and not obsoleted.
    AttributeDescription attrDesc = attr.getAttributeDescription();
    AttributeType t = attrDesc.getAttributeType();
    if (t.isObjectClass())
    {
      validateObjectClasses(attr);
    }

    // Replace the provided attribute.
    modifiedEntry.replaceAttribute(attr);

    // Make sure that the RDN attribute value(s) has not been removed.
    RDN rdn = modifiedEntry.getName().rdn();
    if (rdn != null
        && rdn.hasAttributeType(t)
        && !modifiedEntry.hasValue(attrDesc, rdn.getAttributeValue(t)))
    {
      throw newDirectoryException(modifiedEntry, ResultCode.NOT_ALLOWED_ON_RDN,
          ERR_MODIFY_DELETE_RDN_ATTR.get(entryDN, attrDesc));
    }
  }

  /**
   * Process an increment modification and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being incremented.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void processIncrementModification(Attribute attr) throws DirectoryException
  {
    // The specified attribute type must not be an RDN attribute.
    AttributeDescription attrDesc = attr.getAttributeDescription();
    AttributeType t = attrDesc.getAttributeType();
    RDN rdn = modifiedEntry.getName().rdn();
    if (rdn != null && rdn.hasAttributeType(t))
    {
      throw newDirectoryException(modifiedEntry, ResultCode.NOT_ALLOWED_ON_RDN,
          ERR_MODIFY_INCREMENT_RDN.get(entryDN, attrDesc));
    }

    // The provided attribute must have a single value, and it must be an integer
    if (attr.isEmpty())
    {
      throw newDirectoryException(modifiedEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_INCREMENT_REQUIRES_VALUE.get(entryDN, attrDesc));
    }
    else if (attr.size() > 1)
    {
      throw newDirectoryException(modifiedEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE.get(entryDN, attrDesc));
    }

    MatchingRule eqRule = t.getEqualityMatchingRule();
    ByteString v = attr.iterator().next();

    long incrementValue;
    try
    {
      String nv = eqRule.normalizeAttributeValue(v).toString();
      incrementValue = Long.parseLong(nv);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          ERR_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER.get(entryDN, attrDesc, v), e);
    }

    // Get the attribute that is to be incremented.
    Attribute modifiedAttr = modifiedEntry.getExactAttribute(attrDesc);
    if (modifiedAttr == null)
    {
      throw newDirectoryException(modifiedEntry,
          ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(entryDN, attrDesc));
    }

    // Increment each attribute value by the specified amount.
    AttributeDescription modifiedAttrDesc = modifiedAttr.getAttributeDescription();
    AttributeBuilder builder = new AttributeBuilder(modifiedAttrDesc);
    for (ByteString existingValue : modifiedAttr)
    {
      long currentValue;
      try
      {
        currentValue = Long.parseLong(existingValue.toString());
      }
      catch (Exception e)
      {
        logger.traceException(e);

        throw new DirectoryException(
            ResultCode.INVALID_ATTRIBUTE_SYNTAX,
            ERR_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE.get(entryDN, modifiedAttrDesc, existingValue),
            e);
      }

      long newValue = currentValue + incrementValue;
      builder.add(String.valueOf(newValue));
    }

    // Replace the existing attribute with the incremented version.
    modifiedEntry.replaceAttribute(builder.toAttribute());
  }

  /**
   * Performs additional preliminary processing that is required for a password change.
   *
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify operation to fail.
   */
  private void performAdditionalPasswordChangedProcessing() throws DirectoryException
  {
    if (!passwordChanged)
    {
      // Nothing to do.
      return;
    }

    // If it was a self change, then see if the current password was provided
    // and handle accordingly.
    final PasswordPolicy authPolicy = pwPolicyState.getAuthenticationPolicy();
    if (selfChange
        && authPolicy.isPasswordChangeRequiresCurrentPassword()
        && !currentPasswordProvided)
    {
      pwpErrorType = PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW.get());
    }


    // If this change would result in multiple password values, then see if that's OK.
    if (numPasswords > 1 && !authPolicy.isAllowMultiplePasswordValues())
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED.get());
    }


    // If any of the password values should be validated, then do so now.
    if (newPasswords != null
        && (selfChange || !authPolicy.isSkipValidationForAdministrators()))
    {
      HashSet<ByteString> clearPasswords = new HashSet<>(pwPolicyState.getClearPasswords());
      if (currentPasswords != null)
      {
        clearPasswords.addAll(currentPasswords);
      }

      for (ByteString v : newPasswords)
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (! pwPolicyState.passwordIsAcceptable(this, modifiedEntry,
                                 v, clearPasswords, invalidReason))
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_PW_VALIDATION_FAILED.get(invalidReason));
        }
      }
    }

    // If we should check the password history, then do so now.
    if (newPasswords != null && pwPolicyState.maintainHistory())
    {
      for (ByteString v : newPasswords)
      {
        if (pwPolicyState.isPasswordInHistory(v)
            && (selfChange || !authPolicy.isSkipValidationForAdministrators()))
        {
          pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_PW_IN_HISTORY.get());
        }
      }

      pwPolicyState.updatePasswordHistory();
    }


    wasLocked = pwPolicyState.isLocked();

    // Update the password policy state attributes in the user's entry.  If the
    // modification fails, then these changes won't be applied.
    pwPolicyState.setPasswordChangedTime();
    pwPolicyState.clearFailureLockout();
    pwPolicyState.clearGraceLoginTimes();
    pwPolicyState.clearWarnedTime();

    if (authPolicy.isForceChangeOnAdd() || authPolicy.isForceChangeOnReset())
    {
      if (selfChange)
      {
        pwPolicyState.setMustChangePassword(false);
      }
      else
      {
        if (pwpErrorType == null && authPolicy.isForceChangeOnReset())
        {
          pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
        }

        pwPolicyState.setMustChangePassword(authPolicy.isForceChangeOnReset());
      }
    }

    if (authPolicy.getRequireChangeByTime() > 0)
    {
      pwPolicyState.setRequiredChangeTime();
    }

    modifications.addAll(pwPolicyState.getModifications());
    modifiedEntry.applyModifications(pwPolicyState.getModifications());
  }

  /** Generate any password policy account status notifications as a result of modify processing. */
  private void generatePwpAccountStatusNotifications()
  {
    if (passwordChanged)
    {
      if (selfChange)
      {
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if (authInfo.getAuthenticationDN().equals(modifiedEntry.getName()))
        {
          clientConnection.setMustChangePassword(false);
        }

        generateAccountStatusNotificationForPwds(PASSWORD_CHANGED, INFO_MODIFY_PASSWORD_CHANGED.get());
      }
      else
      {
        generateAccountStatusNotificationForPwds(PASSWORD_RESET, INFO_MODIFY_PASSWORD_RESET.get());
      }
    }

    if (enabledStateChanged)
    {
      if (isEnabled)
      {
        generateAccountStatusNotificationNoPwds(ACCOUNT_ENABLED, INFO_MODIFY_ACCOUNT_ENABLED.get());
      }
      else
      {
        generateAccountStatusNotificationNoPwds(ACCOUNT_DISABLED, INFO_MODIFY_ACCOUNT_DISABLED.get());
      }
    }

    if (wasLocked)
    {
      generateAccountStatusNotificationNoPwds(ACCOUNT_UNLOCKED, INFO_MODIFY_ACCOUNT_UNLOCKED.get());
    }
  }

  private void generateAccountStatusNotificationNoPwds(
      AccountStatusNotificationType notificationType, LocalizableMessage message)
  {
    pwPolicyState.generateAccountStatusNotification(notificationType, modifiedEntry, message,
        AccountStatusNotification.createProperties(pwPolicyState, false, -1, null, null));
  }

  private void generateAccountStatusNotificationForPwds(
      AccountStatusNotificationType notificationType, LocalizableMessage message)
  {
    pwPolicyState.generateAccountStatusNotification(notificationType, modifiedEntry, message,
        AccountStatusNotification.createProperties(pwPolicyState, false, -1, currentPasswords, newPasswords));
  }

  /**
   * Handle conflict resolution.
   *
   * @return {@code true} if processing should continue for the operation, or {@code false} if not.
   */
  private boolean handleConflictResolution() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              SynchronizationProviderResult result =
                  provider.handleConflictResolution(this);
              if (! result.continueProcessing()) {
                  setResultCodeAndMessageNoInfoDisclosure(modifiedEntry,
                      result.getResultCode(), result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED,
                  getConnectionID(), getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /**
   * Process pre operation.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  private boolean processPreOperation() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              if (!processOperationResult(this, provider.doPreOperation(this))) {
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_PREOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /** Invoke post operation synchronization providers. */
  private void processSynchPostOperationPlugins() {
      for (SynchronizationProvider<?> provider : getSynchronizationProviders()) {
          try {
              provider.doPostOperation(this);
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_POSTOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return;
          }
      }
  }
}
