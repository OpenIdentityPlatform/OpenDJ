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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.secondsToTimeString;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.controls.AuthorizationIdentityResponseControl;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.PasswordExpiredControl;
import org.opends.server.controls.PasswordExpiringControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.LDAPException;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;


import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.WritabilityMode;
import org.opends.server.util.Validator;
import org.opends.server.workflowelement.LeafWorkflowElement;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.AccessLogger.*;

/**
 * This class defines a local backend workflow element; e-g an entity that
 * handle the processing of an operation aginst a local backend.
 */
public class LocalBackendWorkflowElement extends LeafWorkflowElement
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // the backend associated to that workflow element
  Backend backend;


  /**
   * Creates a new instance of the local backend workflow element.
   *
   * @param workflowElementID  the workflow element identifier
   * @param backend  the backend associated to that workflow element
   */
  public LocalBackendWorkflowElement(
      String  workflowElementID,
      Backend backend
      )
  {
    super(workflowElementID);

    this.backend  = backend;

    if (this.backend != null)
    {
      isPrivate = this.backend.isPrivateBackend();
    }
  }


  /**
   * {@inheritDoc}
   */
  public void execute(Operation operation)
  {
    switch (operation.getOperationType())
    {
    case BIND:
      processBind((BindOperation) operation);
      break;
    case SEARCH:
      processSearch((SearchOperation) operation);
      break;
    case ADD:
      processAdd((AddOperation) operation);
      break;
    case DELETE:
      processDelete((DeleteOperation) operation);
      break;
    case MODIFY:
      processModify((ModifyOperation) operation);
      break;
    case MODIFY_DN:
      processModifyDN((ModifyDNOperation) operation);
      break;
    case COMPARE:
      processCompare((CompareOperation) operation);
      break;
    case ABANDON:
      // There is no processing for an abandon operation.
      break;
    default:
      // jdemendi - temporary code, just make sure that we are not falling
      // into that incomplete code...
      Validator.ensureTrue(false);
      break;
    }
  }


  /**
   * Perform a modify operation against a local backend.
   *
   * @param operation - The operation to perform
   */
  public void processModify(ModifyOperation operation)
  {
    LocalBackendModifyOperation localOperation =
      new LocalBackendModifyOperation(operation);

    processLocalModify(localOperation);
  }

  /**
   * Perform a local modify operation against the local backend.
   *
   * @param localOp - The operation to perform
   */
  private void processLocalModify(LocalBackendModifyOperation localOp)
  {
    ClientConnection clientConnection = localOp.getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
    boolean                 pwPolicyControlRequested = false;
    PasswordPolicyErrorType pwpErrorType             = null;
    modifyProcessing:
    {
      DN entryDN = localOp.getEntryDN();
      if (entryDN == null){
        break modifyProcessing;
      }

      // Process the modifications to convert them from their raw form to the
      // form required for the rest of the modify processing.
      List<Modification> modifications = localOp.getModifications();
      if (modifications == null)
      {
        break modifyProcessing;
      }

      if (modifications.isEmpty())
      {
        localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        localOp.appendErrorMessage(
            ERR_MODIFY_NO_MODIFICATIONS.get(String.valueOf(entryDN)));
        break modifyProcessing;
      }


      // If the user must change their password before doing anything else, and
      // if the target of the modify operation isn't the user's own entry, then
      // reject the request.
      if ((! localOp.isInternalOperation()) &&
          clientConnection.mustChangePassword())
      {
        DN authzDN = localOp.getAuthorizationDN();
        if ((authzDN != null) && (! authzDN.equals(entryDN)))
        {
          // The user will not be allowed to do anything else before the
          // password gets changed.  Also note that we haven't yet checked the
          // request controls so we need to do that now to see if the password
          // policy request control was provided.
          for (Control c : localOp.getRequestControls())
          {
            if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
            {
              pwPolicyControlRequested = true;
              pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
              break;
            }
          }

          localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);


          localOp.appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
          break modifyProcessing;
        }
      }


      // Check for a request to cancel this operation.
      if (localOp.getCancelRequest() != null)
      {
        return;
      }

      // Acquire a write lock on the target entry.
      Lock entryLock = null;
      for (int i=0; i < 3; i++)
      {
        entryLock = LockManager.lockWrite(entryDN);
        if (entryLock != null)
        {
          break;
        }
      }

      if (entryLock == null)
      {
        localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
        localOp.appendErrorMessage(
            ERR_MODIFY_CANNOT_LOCK_ENTRY.get(String.valueOf(entryDN)));

        skipPostOperation = true;
        break modifyProcessing;
      }


      try
      {
        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Get the entry to modify.  If it does not exist, then fail.
        Entry currentEntry = null;
        try
        {
          currentEntry = backend.getEntry(entryDN);
          localOp.setCurrentEntry(currentEntry);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          localOp.setMatchedDN(de.getMatchedDN());
          localOp.setReferralURLs(de.getReferralURLs());

          break modifyProcessing;
        }

        if (currentEntry == null)
        {
          localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
          localOp.appendErrorMessage(ERR_MODIFY_NO_SUCH_ENTRY.get(
                  String.valueOf(entryDN)));

          // See if one of the entry's ancestors exists.
          DN parentDN = entryDN.getParentDNInSuffix();
          while (parentDN != null)
          {
            try
            {
              if (DirectoryServer.entryExists(parentDN))
              {
                localOp.setMatchedDN(parentDN);
                break;
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              break;
            }

            parentDN = parentDN.getParentDNInSuffix();
          }

          break modifyProcessing;
        }

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                    noOp            = false;
        LDAPPreReadRequestControl  preReadRequest  = null;
        LDAPPostReadRequestControl postReadRequest = null;
        List<Control> requestControls = localOp.getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (!AccessControlConfigManager.getInstance().
                     getAccessControlHandler().
                     isAllowed(entryDN, localOp, c))
            {
              localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

              localOp.appendErrorMessage(
                      ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
              skipPostOperation = true;
              break modifyProcessing;
            }

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break modifyProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(currentEntry))
                {
                  localOp.setResultCode(ResultCode.ASSERTION_FAILED);

                  localOp.appendErrorMessage(
                      ERR_MODIFY_ASSERTION_FAILED.get(String.valueOf(entryDN)));

                  break modifyProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

                localOp.appendErrorMessage(
                        ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));

                break modifyProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPPreReadRequestControl)
              {
                preReadRequest = (LDAPPreReadRequestControl) c;
              }
              else
              {
                try
                {
                  preReadRequest = LDAPPreReadRequestControl.decodeControl(c);
                  requestControls.set(i, preReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break modifyProcessing;
                }
              }
            }
            else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
            {
              if (c instanceof LDAPPostReadRequestControl)
              {
                postReadRequest = (LDAPPostReadRequestControl) c;
              }
              else
              {
                try
                {
                  postReadRequest = LDAPPostReadRequestControl.decodeControl(c);
                  requestControls.set(i, postReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break modifyProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break modifyProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break modifyProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break modifyProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break modifyProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
            {
              pwPolicyControlRequested = true;
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                localOp.setResultCode(
                    ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                localOp.appendErrorMessage(
                    ERR_MODIFY_UNSUPPORTED_CRITICAL_CONTROL.get(
                            String.valueOf(entryDN),
                            oid));

                break modifyProcessing;
              }
            }
          }
        }


        // Get the password policy state object for the entry that can be used
        // to perform any appropriate password policy processing.  Also, see if
        // the entry is being updated by the end user or an administrator.
        PasswordPolicyState pwPolicyState;
        boolean selfChange = entryDN.equals(localOp.getAuthorizationDN());
        try
        {
          // FIXME -- Need a way to enable debug mode.
          pwPolicyState = new PasswordPolicyState(currentEntry, false, false);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());

          break modifyProcessing;
        }


        // Create a duplicate of the entry and apply the changes to it.
        Entry modifiedEntry = currentEntry.duplicate(false);
        localOp.setModifiedEntry(modifiedEntry);

        if (! noOp)
        {
          // Invoke any conflict resolution processing that might be needed by
          // the synchronization provider.
          for (SynchronizationProvider provider :
            DirectoryServer.getSynchronizationProviders())
          {
            try
            {
              SynchronizationProviderResult result =
                provider.handleConflictResolution(localOp);
              if (! result.continueOperationProcessing())
              {
                break modifyProcessing;
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              logError(ERR_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED.
                  get(localOp.getConnectionID(), localOp.getOperationID(),
                      getExceptionMessage(de)));

              localOp.setResponseData(de);
              break modifyProcessing;
            }
          }
        }


        // Declare variables used for password policy state processing.
        boolean passwordChanged = false;
        boolean currentPasswordProvided = false;
        boolean isEnabled = true;
        boolean enabledStateChanged = false;
        int numPasswords;
        if (currentEntry.hasAttribute(
            pwPolicyState.getPolicy().getPasswordAttribute()))
        {
          // It may actually have more than one, but we can't tell the
          // difference if the values are encoded, and its enough for our
          // purposes just to know that there is at least one.
          numPasswords = 1;
        }
        else
        {
          numPasswords = 0;
        }


        // If it's not an internal or synchronization operation, then iterate
        // through the set of modifications to see if a password is included in
        // the changes.  If so, then add the appropriate state changes to the
        // set of modifications.
        if (! (localOp.isInternalOperation()
            || localOp.isSynchronizationOperation()))
        {
          for (Modification m : modifications)
          {
            if (m.getAttribute().getAttributeType().equals(
                pwPolicyState.getPolicy().getPasswordAttribute()))
            {
              passwordChanged = true;
              if (! selfChange)
              {
                if (! clientConnection.hasPrivilege(
                    Privilege.PASSWORD_RESET,
                    localOp))
                {
                  pwpErrorType =
                       PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;

                  localOp.appendErrorMessage(
                          ERR_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES.get());
                  localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
                  break modifyProcessing;
                }
              }

              break;
            }
          }
        }


        for (Modification m : modifications)
        {
          Attribute     a = m.getAttribute();
          AttributeType t = a.getAttributeType();


          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          if (t.isNoUserModification())
          {
            if (! (localOp.isInternalOperation() ||
                localOp.isSynchronizationOperation() ||
                m.isInternal()))
            {
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(
                  ERR_MODIFY_ATTR_IS_NO_USER_MOD.get(String.valueOf(entryDN),
                  a.getName()));
              break modifyProcessing;
            }
          }

          // If the attribute type is marked "OBSOLETE" and the modification
          // is setting new values, then fail unless this is an internal
          // operation or is related to synchronization in some way.
          if (t.isObsolete())
          {
            if (a.hasValue() &&
                (m.getModificationType() != ModificationType.DELETE))
            {
              if (! (localOp.isInternalOperation() ||
                  localOp.isSynchronizationOperation() ||
                  m.isInternal()))
              {
                localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
                localOp.appendErrorMessage(
                    ERR_MODIFY_ATTR_IS_OBSOLETE.get(String.valueOf(entryDN),
                    a.getName()));
                break modifyProcessing;
              }
            }
          }


          // See if the attribute is one which controls the privileges available
          // for a user.  If it is, then the client must have the
          // PRIVILEGE_CHANGE privilege.
          if (t.hasName(OP_ATTR_PRIVILEGE_NAME))
          {
            if (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE,
                localOp))
            {
              localOp.appendErrorMessage(
                      ERR_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES
                              .get());
              localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
              break modifyProcessing;
            }
          }


          // If the modification is updating the password attribute, then
          // perform any necessary password policy processing.  This processing
          // should be skipped for synchronization operations.
          boolean isPassword
          = t.equals(pwPolicyState.getPolicy().getPasswordAttribute());
          if (isPassword && (!(localOp.isSynchronizationOperation())))
          {
            // If the attribute contains any options, then reject it.  Passwords
            // will not be allowed to have options. Skipped for internal
            // operations.
            if(!localOp.isInternalOperation())
            {
              if (a.hasOptions())
              {
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                localOp.appendErrorMessage(
                        ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
                break modifyProcessing;
              }


              // If it's a self change, then see if that's allowed.
              if (selfChange &&
                  (! pwPolicyState.getPolicy().allowUserPasswordChanges()))
              {
                pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;

                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                localOp.appendErrorMessage(
                        ERR_MODIFY_NO_USER_PW_CHANGES.get());
                break modifyProcessing;
              }


              // If we require secure password changes, then makes sure it's a
              // secure communication channel.
              if (pwPolicyState.getPolicy().requireSecurePasswordChanges() &&
                  (! clientConnection.isSecure()))
              {
                pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;

                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                localOp.appendErrorMessage(
                        ERR_MODIFY_REQUIRE_SECURE_CHANGES.get());
                break modifyProcessing;
              }


              // If it's a self change and it's not been long enough since the
              // previous change, then reject it.
              if (selfChange && pwPolicyState.isWithinMinimumAge())
              {
                pwpErrorType = PasswordPolicyErrorType.PASSWORD_TOO_YOUNG;

                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                localOp.appendErrorMessage(ERR_MODIFY_WITHIN_MINIMUM_AGE.get());
                break modifyProcessing;
              }
            }

            // Check to see whether this will adding, deleting, or replacing
            // password values (increment doesn't make any sense for passwords).
            // Then perform the appropriate type of processing for that kind of
            // modification.
            boolean isAdd = false;
            LinkedHashSet<AttributeValue> pwValues = a.getValues();
            LinkedHashSet<AttributeValue> encodedValues =
              new LinkedHashSet<AttributeValue>();
            switch (m.getModificationType())
            {
            case ADD:
            case REPLACE:
              int passwordsToAdd = pwValues.size();

              if (m.getModificationType() == ModificationType.ADD)
              {
                numPasswords += passwordsToAdd;
                isAdd = true;
              }
              else
              {
                numPasswords = passwordsToAdd;
              }
              // If there were multiple password values provided, then make
              // sure that's OK.

              if ((! localOp.isInternalOperation()) &&
                  (! pwPolicyState.getPolicy().allowMultiplePasswordValues()) &&
                  (passwordsToAdd > 1))
              {
                pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;

                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                localOp.appendErrorMessage(
                        ERR_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED.get());
                break modifyProcessing;
              }

              // Iterate through the password values and see if any of them
              // are pre-encoded.  If so, then check to see if we'll allow it.
              // Otherwise, store the clear-text values for later validation
              // and update the attribute with the encoded values.
              for (AttributeValue v : pwValues)
              {
                if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
                {
                  if ((!localOp.isInternalOperation()) &&
                      ! pwPolicyState.getPolicy().allowPreEncodedPasswords())
                  {
                    pwpErrorType =
                         PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;

                    localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                    localOp.appendErrorMessage(
                            ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
                    break modifyProcessing;
                  }
                  else
                  {
                    encodedValues.add(v);
                  }
                }
                else
                {
                  if (isAdd)
                  {
                    // Make sure that the password value doesn't already
                    // exist.
                    if (pwPolicyState.passwordMatches(v.getValue()))
                    {
                      pwpErrorType =
                           PasswordPolicyErrorType.PASSWORD_IN_HISTORY;

                      localOp.setResultCode(
                          ResultCode.ATTRIBUTE_OR_VALUE_EXISTS);

                      localOp.appendErrorMessage(
                              ERR_MODIFY_PASSWORD_EXISTS.get());
                      break modifyProcessing;
                    }
                  }

                  List<AttributeValue> newPasswords =
                    localOp.getNewPasswords() ;
                  if (newPasswords == null)
                  {
                    newPasswords = new LinkedList<AttributeValue>();
                    localOp.setNewPasswords(newPasswords);
                  }

                  newPasswords.add(v);

                  try
                  {
                    for (ByteString s :
                      pwPolicyState.encodePassword(v.getValue()))
                    {
                      encodedValues.add(new AttributeValue(
                          a.getAttributeType(), s));
                    }
                  }
                  catch (DirectoryException de)
                  {
                    if (debugEnabled())
                    {
                      TRACER.debugCaught(DebugLogLevel.ERROR, de);
                    }

                    localOp.setResultCode(de.getResultCode());
                    localOp.appendErrorMessage(de.getMessageObject());
                    break modifyProcessing;
                  }
                }
              }

              a.setValues(encodedValues);

              break;

            case DELETE:
              // Iterate through the password values and see if any of them
              // are pre-encoded.  We will never allow pre-encoded passwords
              // for user password changes, but we will allow them for
              // administrators.  For each clear-text value, verify that at
              // least one value in the entry matches and replace the
              // clear-text value with the appropriate encoded forms.
              for (AttributeValue v : pwValues)
              {
                if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
                {
                  if ((!localOp.isInternalOperation()) && selfChange)
                  {
                    pwpErrorType =
                         PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;

                    localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                    localOp.appendErrorMessage(
                            ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
                    break modifyProcessing;
                  }
                  else
                  {
                    encodedValues.add(v);
                  }
                }
                else
                {
                  List<Attribute> attrList = currentEntry.getAttribute(t);
                  if ((attrList == null) || (attrList.isEmpty()))
                  {
                    localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                    localOp.appendErrorMessage(
                            ERR_MODIFY_NO_EXISTING_VALUES.get());
                    break modifyProcessing;
                  }
                  boolean found = false;
                  for (Attribute attr : attrList)
                  {
                    for (AttributeValue av : attr.getValues())
                    {
                      if (pwPolicyState.getPolicy().usesAuthPasswordSyntax())
                      {
                        if (AuthPasswordSyntax.isEncoded(av.getValue()))
                        {
                          try
                          {
                            StringBuilder[] compoenents =
                              AuthPasswordSyntax.decodeAuthPassword(
                                  av.getStringValue());
                            PasswordStorageScheme scheme =
                              DirectoryServer.
                              getAuthPasswordStorageScheme(
                                  compoenents[0].toString());
                            if (scheme != null)
                            {
                              if (scheme.authPasswordMatches(
                                  v.getValue(),
                                  compoenents[1].toString(),
                                  compoenents[2].toString()))
                              {
                                encodedValues.add(av);
                                found = true;
                              }
                            }
                          }
                          catch (DirectoryException de)
                          {
                            if (debugEnabled())
                            {
                              TRACER.debugCaught(
                                  DebugLogLevel.ERROR, de);
                            }

                            localOp.setResultCode(de.getResultCode());

                            localOp.appendErrorMessage(
                                ERR_MODIFY_CANNOT_DECODE_PW.get(
                                        de.getMessageObject()));
                            break modifyProcessing;
                          }
                        }
                        else
                        {
                          if (av.equals(v))
                          {
                            encodedValues.add(v);
                            found = true;
                          }
                        }
                      }
                      else
                      {
                        if (UserPasswordSyntax.isEncoded(av.getValue()))
                        {
                          try
                          {
                            String[] compoenents =
                              UserPasswordSyntax.decodeUserPassword(
                                  av.getStringValue());
                            PasswordStorageScheme scheme =
                              DirectoryServer.getPasswordStorageScheme(
                                  toLowerCase(compoenents[0]));
                            if (scheme != null)
                            {
                              if (scheme.passwordMatches(
                                  v.getValue(),
                                  new ASN1OctetString(compoenents[1])))
                              {
                                encodedValues.add(av);
                                found = true;
                              }
                            }
                          }
                          catch (DirectoryException de)
                          {
                            if (debugEnabled())
                            {
                              TRACER.debugCaught(
                                  DebugLogLevel.ERROR, de);
                            }

                            localOp.setResultCode(de.getResultCode());

                            localOp.appendErrorMessage(
                                    ERR_MODIFY_CANNOT_DECODE_PW.get(
                                            de.getMessageObject()));
                            break modifyProcessing;
                          }
                        }
                        else
                        {
                          if (av.equals(v))
                          {
                            encodedValues.add(v);
                            found = true;
                          }
                        }
                      }
                    }
                  }

                  if (found)
                  {
                    List<AttributeValue> currentPasswords =
                      localOp.getCurrentPasswords();
                    if (currentPasswords == null)
                    {
                      currentPasswords = new LinkedList<AttributeValue>();
                      localOp.setCurrentPasswords(currentPasswords);
                    }
                    currentPasswords.add(v);

                    numPasswords--;
                  }
                  else
                  {
                    localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                    localOp.appendErrorMessage(
                            ERR_MODIFY_INVALID_PASSWORD.get());
                    break modifyProcessing;
                  }

                  currentPasswordProvided = true;
                }
              }

              a.setValues(encodedValues);

              break;

            default:
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

            localOp.appendErrorMessage(
                    ERR_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD.get(
                            String.valueOf(m.getModificationType()),
                            a.getName()));

            break modifyProcessing;
            }
          }
          else
          {
            // See if it's an attribute used to maintain the account
            // enabled/disabled state.
            AttributeType disabledAttr =
              DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_DISABLED,
                  true);
            if (t.equals(disabledAttr))
            {
              enabledStateChanged = true;
              for (AttributeValue v : a.getValues())
              {
                try
                {
                  isEnabled = (! BooleanSyntax.decodeBooleanValue(
                      v.getNormalizedValue()));
                }
                catch (DirectoryException de)
                {
                  localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                  Message message =
                    ERR_MODIFY_INVALID_DISABLED_VALUE.get(
                            OP_ATTR_ACCOUNT_DISABLED,
                            String.valueOf(de.getMessageObject()));
                  localOp.appendErrorMessage(message);
                  break modifyProcessing;
                }
              }
            }
          }


          switch (m.getModificationType())
          {
          case ADD:
            // Make sure that one or more values have been provided for the
            // attribute.
            LinkedHashSet<AttributeValue> newValues = a.getValues();
            if ((newValues == null) || newValues.isEmpty())
            {
              localOp.setResultCode(ResultCode.PROTOCOL_ERROR);
              localOp.appendErrorMessage(ERR_MODIFY_ADD_NO_VALUES.get(
                      String.valueOf(entryDN),
                      a.getName()));
              break modifyProcessing;
            }

            // If the server is configured to check schema and the
            // operation is not a synchronization operation,
            // make sure that all the new values are valid according to the
            // associated syntax.
            if ((DirectoryServer.checkSchema()) &&
                (!localOp.isSynchronizationOperation()) )
            {
              AcceptRejectWarn syntaxPolicy =
                DirectoryServer.getSyntaxEnforcementPolicy();
              AttributeSyntax syntax = t.getSyntax();

              if (syntaxPolicy == AcceptRejectWarn.REJECT)
              {
                MessageBuilder invalidReason =
                  new MessageBuilder();

                for (AttributeValue v : newValues)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                  {
                    localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);


                    localOp.appendErrorMessage(ERR_MODIFY_ADD_INVALID_SYNTAX
                            .get(String.valueOf(entryDN),
                                 a.getName(),
                                 v.getStringValue(),
                                 invalidReason.toString()));

                    break modifyProcessing;
                  }
                }
              }
              else if (syntaxPolicy == AcceptRejectWarn.WARN)
              {
                MessageBuilder invalidReason = new MessageBuilder();

                for (AttributeValue v : newValues)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                  {
                    localOp.setResultCode(
                        ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                    logError(
                          ERR_MODIFY_ADD_INVALID_SYNTAX.
                            get(String.valueOf(entryDN), a.getName(),
                                v.getStringValue(), invalidReason.toString()));

                    invalidReason = new MessageBuilder();
                  }
                }
              }
            }


            // Add the provided attribute or merge an existing attribute with
            // the values of the new attribute.  If there are any duplicates,
            // then fail.
            LinkedList<AttributeValue> duplicateValues =
              new LinkedList<AttributeValue>();
            if (a.getAttributeType().isObjectClassType())
            {
              try
              {
                modifiedEntry.addObjectClasses(newValues);
                break;
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResponseData(de);
                break modifyProcessing;
              }
            }
            else
            {
              modifiedEntry.addAttribute(a, duplicateValues);
              if (duplicateValues.isEmpty())
              {
                break;
              }
              else
              {
                StringBuilder buffer = new StringBuilder();
                Iterator<AttributeValue> iterator =
                  duplicateValues.iterator();
                buffer.append(iterator.next().getStringValue());
                while (iterator.hasNext())
                {
                  buffer.append(", ");
                  buffer.append(iterator.next().getStringValue());
                }

                localOp.setResultCode(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS);

                localOp.appendErrorMessage(
                    ERR_MODIFY_ADD_DUPLICATE_VALUE.get(String.valueOf(entryDN),
                    a.getName(),
                    buffer.toString()));

                break modifyProcessing;
              }
            }


          case DELETE:
            // Remove the specified attribute values or the entire attribute
            // from the value.  If there are any specified values that were
            // not present, then fail.  If the RDN attribute value would be
            // removed, then fail.
            LinkedList<AttributeValue> missingValues =
              new LinkedList<AttributeValue>();
            boolean attrExists =
              modifiedEntry.removeAttribute(a, missingValues);

            if (attrExists)
            {
              if (missingValues.isEmpty())
              {
                RDN rdn = modifiedEntry.getDN().getRDN();
                if ((rdn !=  null) && rdn.hasAttributeType(t) &&
                    (! modifiedEntry.hasValue(t, a.getOptions(),
                        rdn.getAttributeValue(t))))
                {
                  localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                  localOp.appendErrorMessage(ERR_MODIFY_DELETE_RDN_ATTR.get(
                          String.valueOf(entryDN),
                      a.getName()));
                  break modifyProcessing;
                }

                break;
              }
              else
              {
                StringBuilder buffer = new StringBuilder();
                Iterator<AttributeValue> iterator = missingValues.iterator();
                buffer.append(iterator.next().getStringValue());
                while (iterator.hasNext())
                {
                  buffer.append(", ");
                  buffer.append(iterator.next().getStringValue());
                }

                localOp.setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                localOp.appendErrorMessage(
                    ERR_MODIFY_DELETE_MISSING_VALUES.get(
                            String.valueOf(entryDN),
                    a.getName(),
                    buffer.toString()));

                break modifyProcessing;
              }
            }
            else
            {
              localOp.setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

              localOp.appendErrorMessage(
                  ERR_MODIFY_DELETE_NO_SUCH_ATTR.get(String.valueOf(entryDN),
                  a.getName()));
              break modifyProcessing;
            }


          case REPLACE:
            // If it is the objectclass attribute, then treat that separately.
            if (a.getAttributeType().isObjectClassType())
            {
              try
              {
                modifiedEntry.setObjectClasses(a.getValues());
                break;
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResponseData(de);
                break modifyProcessing;
              }
            }


            // If the provided attribute does not have any values, then we
            // will simply remove the attribute from the entry (if it exists).
            if (! a.hasValue())
            {
              modifiedEntry.removeAttribute(t, a.getOptions());
              RDN rdn = modifiedEntry.getDN().getRDN();
              if ((rdn !=  null) && rdn.hasAttributeType(t) &&
                  (! modifiedEntry.hasValue(t, a.getOptions(),
                      rdn.getAttributeValue(t))))
              {
                localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                localOp.appendErrorMessage(
                    ERR_MODIFY_DELETE_RDN_ATTR.get(String.valueOf(entryDN),
                    a.getName()));
                break modifyProcessing;
              }
              break;
            }

            // If the server is configured to check schema and the
            // operation is not a synchronization operation,
            // make sure that all the new values are valid according to the
            // associated syntax.
            newValues = a.getValues();
            if ((DirectoryServer.checkSchema()) &&
                (!localOp.isSynchronizationOperation()) )
            {
              AcceptRejectWarn syntaxPolicy =
                DirectoryServer.getSyntaxEnforcementPolicy();
              AttributeSyntax syntax = t.getSyntax();

              if (syntaxPolicy == AcceptRejectWarn.REJECT)
              {
                MessageBuilder invalidReason = new MessageBuilder();

                for (AttributeValue v : newValues)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                  {
                    localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                    localOp.appendErrorMessage(
                            ERR_MODIFY_REPLACE_INVALID_SYNTAX.get(
                                    String.valueOf(entryDN),
                        a.getName(),
                        v.getStringValue(),
                        invalidReason.toString()));

                    break modifyProcessing;
                  }
                }
              }
              else if (syntaxPolicy == AcceptRejectWarn.WARN)
              {
                MessageBuilder invalidReason = new MessageBuilder();
                for (AttributeValue v : newValues)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                  {
                    localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                    logError(
                          ERR_MODIFY_REPLACE_INVALID_SYNTAX.
                            get(String.valueOf(entryDN), a.getName(),
                                v.getStringValue(), invalidReason.toString()));

                    invalidReason = new MessageBuilder();
                  }
                }
              }
            }


            // If the provided attribute does not have any options, then we
            // will simply use it in place of any existing attribute of the
            // provided type (or add it if it doesn't exist).
            if (! a.hasOptions())
            {
              List<Attribute> attrList = new ArrayList<Attribute>(1);
              attrList.add(a);
              modifiedEntry.putAttribute(t, attrList);

              RDN rdn = modifiedEntry.getDN().getRDN();
              if ((rdn !=  null) && rdn.hasAttributeType(t) &&
                  (! modifiedEntry.hasValue(t, a.getOptions(),
                      rdn.getAttributeValue(t))))
              {
                localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                localOp.appendErrorMessage(
                    ERR_MODIFY_DELETE_RDN_ATTR.get(String.valueOf(entryDN),
                    a.getName()));
                break modifyProcessing;
              }
              break;
            }


            // See if there is an existing attribute of the provided type.  If
            // not, then we'll use the new one.
            List<Attribute> attrList = modifiedEntry.getAttribute(t);
            if ((attrList == null) || attrList.isEmpty())
            {
              attrList = new ArrayList<Attribute>(1);
              attrList.add(a);
              modifiedEntry.putAttribute(t, attrList);

              RDN rdn = modifiedEntry.getDN().getRDN();
              if ((rdn !=  null) && rdn.hasAttributeType(t) &&
                  (! modifiedEntry.hasValue(t, a.getOptions(),
                      rdn.getAttributeValue(t))))
              {
                localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                localOp.appendErrorMessage(
                    ERR_MODIFY_DELETE_RDN_ATTR.get(String.valueOf(entryDN),
                    a.getName()));
                break modifyProcessing;
              }
              break;
            }


            // There must be an existing occurrence of the provided attribute
            // in the entry.  If there is a version with exactly the set of
            // options provided, then replace it.  Otherwise, add a new one.
            boolean found = false;
            for (int i=0; i < attrList.size(); i++)
            {
              if (attrList.get(i).optionsEqual(a.getOptions()))
              {
                attrList.set(i, a);
                found = true;
                break;
              }
            }

            if (! found)
            {
              attrList.add(a);
            }

            RDN rdn = modifiedEntry.getDN().getRDN();
            if ((rdn !=  null) && rdn.hasAttributeType(t) &&
                (! modifiedEntry.hasValue(t, a.getOptions(),
                    rdn.getAttributeValue(t))))
            {
              localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

              localOp.appendErrorMessage(
                  ERR_MODIFY_DELETE_RDN_ATTR.get(String.valueOf(entryDN),
                  a.getName()));
              break modifyProcessing;
            }
            break;


          case INCREMENT:
            // The specified attribute type must not be an RDN attribute.
            rdn = modifiedEntry.getDN().getRDN();
            if ((rdn !=  null) && rdn.hasAttributeType(t))
            {
              localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);
              localOp.appendErrorMessage(ERR_MODIFY_INCREMENT_RDN.get(
                      String.valueOf(entryDN),
                  a.getName()));
            }


            // The provided attribute must have a single value, and it must be
            // an integer.
            LinkedHashSet<AttributeValue> values = a.getValues();
            if ((values == null) || values.isEmpty())
            {
              localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

              localOp.appendErrorMessage(
                  ERR_MODIFY_INCREMENT_REQUIRES_VALUE.get(
                          String.valueOf(entryDN),
                  a.getName()));

              break modifyProcessing;
            }
            else if (values.size() > 1)
            {
              localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

              localOp.appendErrorMessage(
                  ERR_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE.get(
                          String.valueOf(entryDN),
                  a.getName()));
              break modifyProcessing;
            }

            AttributeValue v = values.iterator().next();

            long incrementValue;
            try
            {
              incrementValue = Long.parseLong(v.getNormalizedStringValue());
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

              localOp.appendErrorMessage(
                  ERR_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER.get(
                          String.valueOf(entryDN),
                  a.getName(), v.getStringValue()));

              break modifyProcessing;
            }


            // Get the corresponding attribute from the entry and make sure
            // that it has a single integer value.
            attrList = modifiedEntry.getAttribute(t, a.getOptions());
            if ((attrList == null) || attrList.isEmpty())
            {
              localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

              localOp.appendErrorMessage(
                  ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(
                          String.valueOf(entryDN),
                  a.getName()));

              break modifyProcessing;
            }

            boolean updated = false;
            for (Attribute attr : attrList)
            {
              LinkedHashSet<AttributeValue> valueList = attr.getValues();
              if ((valueList == null) || valueList.isEmpty())
              {
                continue;
              }

              LinkedHashSet<AttributeValue> newValueList =
                new LinkedHashSet<AttributeValue>(valueList.size());
              for (AttributeValue existingValue : valueList)
              {
                long newIntValue;
                try
                {
                  long existingIntValue =
                    Long.parseLong(existingValue.getStringValue());
                  newIntValue = existingIntValue + incrementValue;
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                  localOp.appendErrorMessage(
                          ERR_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE.get(
                                  String.valueOf(entryDN),
                      a.getName(),
                      existingValue.getStringValue()));
                  break modifyProcessing;
                }

                ByteString newValue =
                  new ASN1OctetString(String.valueOf(newIntValue));
                newValueList.add(new AttributeValue(t, newValue));
              }

              attr.setValues(newValueList);
              updated = true;
            }

            if (! updated)
            {
              localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

              localOp.appendErrorMessage(
                  ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(
                          String.valueOf(entryDN),
                          a.getName()));

              break modifyProcessing;
            }

            break;

          default:
          }
        }


        // If there was a password change, then perform any additional checks
        // that may be necessary.
        if (passwordChanged)
        {
          // If it was a self change, then see if the current password was
          // provided and handle accordingly.
          if (selfChange &&
              pwPolicyState.getPolicy().requireCurrentPassword() &&
              (! currentPasswordProvided))
          {
            pwpErrorType = PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;

            localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

            localOp.appendErrorMessage(
                    ERR_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW.get());
            break modifyProcessing;
          }


          // If this change would result in multiple password values, then see
          // if that's OK.
          if ((numPasswords > 1) &&
              (! pwPolicyState.getPolicy().allowMultiplePasswordValues()))
          {
            pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;

            localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

            localOp.appendErrorMessage(
                    ERR_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED.get());
            break modifyProcessing;
          }


          // If any of the password values should be validated, then do so now.
          if (selfChange ||
              (! pwPolicyState.getPolicy().skipValidationForAdministrators()))
          {
            List<AttributeValue> newPasswords =
              localOp.getNewPasswords();
            List<AttributeValue> currentPasswords =
              localOp.getCurrentPasswords();

            if (newPasswords != null)
            {
              HashSet<ByteString> clearPasswords = new HashSet<ByteString>();
              clearPasswords.addAll(pwPolicyState.getClearPasswords());

              if (currentPasswords != null)
              {
                if (clearPasswords.isEmpty())
                {
                  for (AttributeValue v : currentPasswords)
                  {
                    clearPasswords.add(v.getValue());
                  }
                }
                else
                {
                  // NOTE:  We can't rely on the fact that Set doesn't allow
                  // duplicates because technically it's possible that the
                  // values aren't duplicates if they are ASN.1 elements with
                  // different types (like 0x04 for a standard universal octet
                  // string type versus 0x80 for a simple password in a bind
                  // operation).  So we have to manually check for duplicates.
                  for (AttributeValue v : currentPasswords)
                  {
                    ByteString pw = v.getValue();

                    boolean found = false;
                    for (ByteString s : clearPasswords)
                    {
                      if (Arrays.equals(s.value(), pw.value()))
                      {
                        found = true;
                        break;
                      }
                    }

                    if (! found)
                    {
                      clearPasswords.add(pw);
                    }
                  }
                }
              }

              for (AttributeValue v : newPasswords)
              {
                MessageBuilder invalidReason = new MessageBuilder();
                if (! pwPolicyState.passwordIsAcceptable(localOp, modifiedEntry,
                    v.getValue(),
                    clearPasswords,
                    invalidReason))
                {
                  pwpErrorType =
                       PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;

                  localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                  localOp.appendErrorMessage(ERR_MODIFY_PW_VALIDATION_FAILED
                          .get(invalidReason.toString()));
                  break modifyProcessing;
                }
              }
            }
          }


          // If we should check the password history, then do so now.
          if (pwPolicyState.maintainHistory())
          {
            List<AttributeValue> newPasswords = localOp.getNewPasswords();
            if (newPasswords != null)
            {
              for (AttributeValue v : newPasswords)
              {
                if (pwPolicyState.isPasswordInHistory(v.getValue()))
                {
                  if (selfChange || (! pwPolicyState.getPolicy().
                                            skipValidationForAdministrators()))
                  {
                    pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;

                    localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                    localOp.appendErrorMessage(ERR_MODIFY_PW_IN_HISTORY.get());
                    break modifyProcessing;
                  }
                }
              }

              pwPolicyState.updatePasswordHistory();
            }
          }
        }


        // Check to see if the client has permission to perform the
        // modify.
        // The access control check is not made any earlier because the
        // handler needs access to the modified entry.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        if (!AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(localOp)) {
          localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          localOp.appendErrorMessage(
              ERR_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                      String.valueOf(entryDN)));

          skipPostOperation = true;
          break modifyProcessing;
        }

        boolean wasLocked = false;
        if (passwordChanged)
        {
          // See if the account was locked for any reason.
          wasLocked = pwPolicyState.lockedDueToIdleInterval() ||
                      pwPolicyState.lockedDueToMaximumResetAge() ||
                      pwPolicyState.lockedDueToFailures();

          // Update the password policy state attributes in the user's entry.
          // If the modification fails, then these changes won't be applied.
          pwPolicyState.setPasswordChangedTime();
          pwPolicyState.clearFailureLockout();
          pwPolicyState.clearGraceLoginTimes();
          pwPolicyState.clearWarnedTime();

          if (pwPolicyState.getPolicy().forceChangeOnAdd() ||
              pwPolicyState.getPolicy().forceChangeOnReset())
          {
            if (selfChange)
            {
              pwPolicyState.setMustChangePassword(false);
            }
            else
            {
              if ((pwpErrorType == null) &&
                  pwPolicyState.getPolicy().forceChangeOnReset())
              {
                pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
              }

              pwPolicyState.setMustChangePassword(
                   pwPolicyState.getPolicy().forceChangeOnReset());
            }
          }

          if (pwPolicyState.getPolicy().getRequireChangeByTime() > 0)
          {
            pwPolicyState.setRequiredChangeTime();
          }
          modifications.addAll(pwPolicyState.getModifications());
          //Apply pwd Policy modifications to modified entry.
          try {
            modifiedEntry.applyModifications(pwPolicyState.getModifications());
          } catch (DirectoryException e) {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            localOp.setResponseData(e);
            break modifyProcessing;
          }
        }
        else if((! localOp.isInternalOperation()) &&
            pwPolicyState.mustChangePassword())
        {
            // The user will not be allowed to do anything else before
            // the password gets changed.
            pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;

            localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

            localOp.appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
            break modifyProcessing;
        }

        // If the server is configured to check the schema and the
        // operation is not a sycnhronization operation,
        // make sure that the new entry is valid per the server schema.
        if ((DirectoryServer.checkSchema()) &&
            (!localOp.isSynchronizationOperation()) )
        {
          MessageBuilder invalidReason = new MessageBuilder();
          if (! modifiedEntry.conformsToSchema(null, false, false, false,
              invalidReason))
          {
            localOp.setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            localOp.appendErrorMessage(ERR_MODIFY_VIOLATES_SCHEMA.get(
                    String.valueOf(entryDN),
                    invalidReason.toString()));
            break modifyProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify plugins.
        if (!localOp.isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationModifyPlugins(localOp);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            localOp.setResultCode(ResultCode.CANCELED);

            localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

            localOp.setProcessingStopTime();

            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break modifyProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break modifyProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Actually perform the modify operation.  This should also include
        // taking care of any synchronization that might be needed.
        if (backend == null)
        {
          localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
          localOp.appendErrorMessage(
              ERR_MODIFY_NO_BACKEND_FOR_ENTRY.get(String.valueOf(entryDN)));
          break modifyProcessing;
        }

        try
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! backend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
            case DISABLED:
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(
                  ERR_MODIFY_SERVER_READONLY.get(String.valueOf(entryDN)));
              break modifyProcessing;

            case INTERNAL_ONLY:
              if (! (localOp.isInternalOperation() ||
                  localOp.isSynchronizationOperation()))
              {
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(
                    ERR_MODIFY_SERVER_READONLY.get(String.valueOf(entryDN)));
                break modifyProcessing;
              }
            }

            switch (backend.getWritabilityMode())
            {
            case DISABLED:
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(
                  ERR_MODIFY_BACKEND_READONLY.get(String.valueOf(entryDN)));
              break modifyProcessing;

            case INTERNAL_ONLY:
              if (! localOp.isInternalOperation() ||
                  localOp.isSynchronizationOperation())
              {
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(
                    ERR_MODIFY_BACKEND_READONLY.get(String.valueOf(entryDN)));
                break modifyProcessing;
              }
            }
          }


          if (noOp)
          {
            localOp.appendErrorMessage(INFO_MODIFY_NOOP.get());

            localOp.setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
            for (SynchronizationProvider provider :
              DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                  provider.doPreOperation(localOp);
                if (! result.continueOperationProcessing())
                {
                  break modifyProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_MODIFY_SYNCH_PREOP_FAILED.
                    get(localOp.getConnectionID(), localOp.getOperationID(),
                        getExceptionMessage(de)));

                localOp.setResponseData(de);
                break modifyProcessing;
              }
            }

            backend.replaceEntry(modifiedEntry, localOp);


            // If the modification was successful, then see if there's any other
            // work that we need to do here before handing off to postop
            // plugins.
            if (passwordChanged)
            {
              if (selfChange)
              {
                AuthenticationInfo authInfo =
                  clientConnection.getAuthenticationInfo();
                if (authInfo.getAuthenticationDN().equals(entryDN))
                {
                  clientConnection.setMustChangePassword(false);
                }

                Message message = INFO_MODIFY_PASSWORD_CHANGED.get();
                pwPolicyState.generateAccountStatusNotification(
                    AccountStatusNotificationType.PASSWORD_CHANGED,
                    modifiedEntry, message,
                    AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, localOp.getCurrentPasswords(),
                         localOp.getNewPasswords()));
              }
              else
              {
                Message message = INFO_MODIFY_PASSWORD_RESET.get();
                pwPolicyState.generateAccountStatusNotification(
                    AccountStatusNotificationType.PASSWORD_RESET, modifiedEntry,
                    message,
                    AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, localOp.getCurrentPasswords(),
                         localOp.getNewPasswords()));
              }
            }

            if (enabledStateChanged)
            {
              if (isEnabled)
              {
                Message message = INFO_MODIFY_ACCOUNT_ENABLED.get();
                pwPolicyState.generateAccountStatusNotification(
                    AccountStatusNotificationType.ACCOUNT_ENABLED,
                    modifiedEntry, message,
                    AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));
              }
              else
              {
                Message message = INFO_MODIFY_ACCOUNT_DISABLED.get();
                pwPolicyState.generateAccountStatusNotification(
                    AccountStatusNotificationType.ACCOUNT_DISABLED,
                    modifiedEntry, message,
                    AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));
              }
            }

            if (wasLocked)
            {
              Message message = INFO_MODIFY_ACCOUNT_UNLOCKED.get();
              pwPolicyState.generateAccountStatusNotification(
                  AccountStatusNotificationType.ACCOUNT_UNLOCKED, modifiedEntry,
                  message,
                  AccountStatusNotification.createProperties(pwPolicyState,
                       false, -1, null, null));
            }
          }

          if (preReadRequest != null)
          {
            Entry entry = currentEntry.duplicate(true);

            if (! preReadRequest.allowsAttribute(
                DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                  DirectoryServer.getObjectClassAttributeType());
            }

            if (! preReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! preReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPreReadResponseControl responseControl =
              new LDAPPreReadResponseControl(preReadRequest.getOID(),
                  preReadRequest.isCritical(),
                  searchEntry);

            localOp.getResponseControls().add(responseControl);
          }

          if (postReadRequest != null)
          {
            Entry entry = modifiedEntry.duplicate(true);

            if (! postReadRequest.allowsAttribute(
                DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                  DirectoryServer.getObjectClassAttributeType());
            }

            if (! postReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! postReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPostReadResponseControl responseControl =
              new LDAPPostReadResponseControl(postReadRequest.getOID(),
                  postReadRequest.isCritical(),
                  searchEntry);

            localOp.getResponseControls().add(responseControl);
          }

          if (! noOp)
          {
            localOp.setResultCode(ResultCode.SUCCESS);
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          localOp.setMatchedDN(de.getMatchedDN());
          localOp.setReferralURLs(de.getReferralURLs());

          break modifyProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          localOp.setCancelResult(cancelResult);
          localOp.setResultCode(cancelResult.getResultCode());

          Message message = coe.getMessageObject();
          if ((message != null) && (message.length() > 0))
          {
            localOp.appendErrorMessage(message);
          }

          break modifyProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, entryLock);

        for (SynchronizationProvider provider :
          DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(localOp);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_MODIFY_SYNCH_POSTOP_FAILED.
                get(localOp.getConnectionID(), localOp.getOperationID(),
                    getExceptionMessage(de)));

            localOp.setResponseData(de);
            break;
          }
        }
      }
    }


    // If the password policy request control was included, then make sure we
    // send the corresponding response control.
    if (pwPolicyControlRequested)
    {
      localOp.addResponseControl(
           new PasswordPolicyResponseControl(null, 0, pwpErrorType));
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    localOp.setCancelResult(CancelResult.TOO_LATE);

    // Invoke the post-operation modify plugins.
    if (! skipPostOperation)
    {
      // FIXME -- Should this also be done while holding the locks?
      PostOperationPluginResult postOpResult =
        pluginConfigManager.invokePostOperationModifyPlugins(localOp);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

        localOp.setProcessingStopTime();

        return;
      }
    }

    // Notify any change notification listeners that might be registered with
    // the server.
    if (localOp.getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleModifyOperation(localOp,
              localOp.getCurrentEntry(),
              localOp.getModifiedEntry());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER.get(
              getExceptionMessage(e));
          logError(message);
        }
      }
    }



    // Stop the processing timer.
    localOp.setProcessingStopTime();
  }

  /**
   * Perform a search operation against a local backend.
   *
   * @param operation - The operation to perform
   */
  public void processSearch(SearchOperation operation)
  {

    LocalBackendSearchOperation localOp =
      new LocalBackendSearchOperation(operation);

    PersistentSearch persistentSearch = null;

    ClientConnection clientConnection = localOp.getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
    searchProcessing:
    {
      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      DN baseDN = localOp.getBaseDN();
      SearchFilter filter = localOp.getFilter();

      if ((baseDN == null) || (filter == null)){
        break searchProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then
      // see if there is any special processing required.
      boolean       processSearch    = true;
      List<Control> requestControls  = localOp.getRequestControls();
      if ((requestControls != null) && (! requestControls.isEmpty()))
      {
        for (int i=0; i < requestControls.size(); i++)
        {
          Control c   = requestControls.get(i);
          String  oid = c.getOID();
          if (!AccessControlConfigManager.getInstance().
                  getAccessControlHandler().
                  isAllowed(baseDN, localOp, c))
          {
            localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

            localOp.appendErrorMessage(
                    ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
            skipPostOperation = true;
            break searchProcessing;
          }

          if (oid.equals(OID_LDAP_ASSERTION))
          {
            LDAPAssertionRequestControl assertControl;
            if (c instanceof LDAPAssertionRequestControl)
            {
              assertControl = (LDAPAssertionRequestControl) c;
            }
            else
            {
              try
              {
                assertControl = LDAPAssertionRequestControl.decodeControl(c);
                requestControls.set(i, assertControl);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, le);
                }

                localOp.setResultCode(
                    ResultCode.valueOf(le.getResultCode()));
                localOp.appendErrorMessage(le.getMessageObject());

                break searchProcessing;
              }
            }

            try
            {
              // FIXME -- We need to determine whether the current user has
              //          permission to make this determination.
              SearchFilter assertionFilter = assertControl.getSearchFilter();
              Entry entry;
              try
              {
                entry = DirectoryServer.getEntry(baseDN);
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());

                localOp.appendErrorMessage(
                    ERR_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION.get(
                            de.getMessageObject()));

                break searchProcessing;
              }

              if (entry == null)
              {
                localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);

                localOp.appendErrorMessage(
                    ERR_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION.get());

                break searchProcessing;
              }


              if (! assertionFilter.matchesEntry(entry))
              {
                localOp.setResultCode(ResultCode.ASSERTION_FAILED);

                localOp.appendErrorMessage(
                    ERR_SEARCH_ASSERTION_FAILED.get());

                break searchProcessing;
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

              localOp.appendErrorMessage(
                  ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                          de.getMessageObject()));

              break searchProcessing;
            }
          }
          else if (oid.equals(OID_PROXIED_AUTH_V1))
          {
            // The requester must have the PROXIED_AUTH privilige in order to be
            // able to use this control.
            if (! clientConnection.hasPrivilege(
                Privilege.PROXIED_AUTH, localOp))
            {
              localOp.appendErrorMessage(
                      ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
              localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
              break searchProcessing;
            }


            ProxiedAuthV1Control proxyControl;
            if (c instanceof ProxiedAuthV1Control)
            {
              proxyControl = (ProxiedAuthV1Control) c;
            }
            else
            {
              try
              {
                proxyControl = ProxiedAuthV1Control.decodeControl(c);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, le);
                }

                localOp.setResultCode(
                    ResultCode.valueOf(le.getResultCode()));
                localOp.appendErrorMessage(le.getMessageObject());

                break searchProcessing;
              }
            }


            Entry authorizationEntry;
            try
            {
              authorizationEntry = proxyControl.getAuthorizationEntry();
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              localOp.setResultCode(de.getResultCode());
              localOp.appendErrorMessage(de.getMessageObject());

              break searchProcessing;
            }

            localOp.setAuthorizationEntry(authorizationEntry);
            if (authorizationEntry == null)
            {
              localOp.setProxiedAuthorizationDN(DN.nullDN());
            }
            else
            {
              localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
            }
          }
          else if (oid.equals(OID_PROXIED_AUTH_V2))
          {
            // The requester must have the PROXIED_AUTH privilige in order to be
            // able to use this control.
            if (! clientConnection.hasPrivilege(
                Privilege.PROXIED_AUTH, localOp))
            {
              localOp.appendErrorMessage(
                      ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
              localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
              break searchProcessing;
            }


            ProxiedAuthV2Control proxyControl;
            if (c instanceof ProxiedAuthV2Control)
            {
              proxyControl = (ProxiedAuthV2Control) c;
            }
            else
            {
              try
              {
                proxyControl = ProxiedAuthV2Control.decodeControl(c);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, le);
                }

                localOp.setResultCode(
                    ResultCode.valueOf(le.getResultCode()));
                localOp.appendErrorMessage(le.getMessageObject());

                break searchProcessing;
              }
            }


            Entry authorizationEntry;
            try
            {
              authorizationEntry = proxyControl.getAuthorizationEntry();
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              localOp.setResultCode(de.getResultCode());
              localOp.appendErrorMessage(de.getMessageObject());

              break searchProcessing;
            }

            localOp.setAuthorizationEntry(authorizationEntry);
            if (authorizationEntry == null)
            {
              localOp.setProxiedAuthorizationDN(DN.nullDN());
            }
            else
            {
              localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
            }
          }
          else if (oid.equals(OID_PERSISTENT_SEARCH))
          {
            PersistentSearchControl psearchControl;
            if (c instanceof PersistentSearchControl)
            {
              psearchControl = (PersistentSearchControl) c;
            }
            else
            {
              try
              {
                psearchControl = PersistentSearchControl.decodeControl(c);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, le);
                }

                localOp.setResultCode(
                    ResultCode.valueOf(le.getResultCode()));
                localOp.appendErrorMessage(le.getMessageObject());

                break searchProcessing;
              }
            }

            persistentSearch =
              new PersistentSearch(operation, psearchControl.getChangeTypes(),
                  psearchControl.getReturnECs());
            localOp.setPersistentSearch(persistentSearch);

            // If we're only interested in changes, then we don't actually want
            // to process the search now.
            if (psearchControl.getChangesOnly())
            {
              processSearch = false;
            }
          }
          else if (oid.equals(OID_LDAP_SUBENTRIES))
          {
            localOp.setReturnLDAPSubentries(true);
          }
          else if (oid.equals(OID_MATCHED_VALUES))
          {
            if (c instanceof MatchedValuesControl)
            {
              localOp.setMatchedValuesControl((MatchedValuesControl) c);
            }
            else
            {
              try
              {
                MatchedValuesControl matchedValuesControl =
                  MatchedValuesControl.decodeControl(c);
                localOp.setMatchedValuesControl(matchedValuesControl);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, le);
                }

                localOp.setResultCode(
                    ResultCode.valueOf(le.getResultCode()));
                localOp.appendErrorMessage(le.getMessageObject());

                break searchProcessing;
              }
            }
          }
          else if (oid.equals(OID_ACCOUNT_USABLE_CONTROL))
          {
            localOp.setIncludeUsableControl(true);
          }
          else if (oid.equals(OID_REAL_ATTRS_ONLY))
          {
            localOp.setRealAttributesOnly(true);
          }
          else if (oid.equals(OID_VIRTUAL_ATTRS_ONLY))
          {
            localOp.setVirtualAttributesOnly(true);
          }
          // NYI -- Add support for additional controls.
          else if (c.isCritical())
          {
            if ((backend == null) || (! backend.supportsControl(oid)))
            {
              localOp.setResultCode(
                  ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

              localOp.appendErrorMessage(
                      ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));

              break searchProcessing;
            }
          }
        }
      }


      // Check to see if the client has permission to perform the
      // search.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      if (AccessControlConfigManager.getInstance()
          .getAccessControlHandler().isAllowed(localOp) == false) {
        localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

        localOp.appendErrorMessage(
            ERR_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                    String.valueOf(baseDN)));

        skipPostOperation = true;
        break searchProcessing;
      }

      // Check for a request to cancel this operation.
      if (localOp.getCancelRequest() != null)
      {
        return;
      }


      // Invoke the pre-operation search plugins.
      PreOperationPluginResult preOpResult =
        pluginConfigManager.invokePreOperationSearchPlugins(localOp);
      if (preOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

        localOp.setProcessingStopTime();
        return;
      }
      else if (preOpResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        break searchProcessing;
      }
      else if (preOpResult.skipCoreProcessing())
      {
        skipPostOperation = false;
        break searchProcessing;
      }


      // Check for a request to cancel this operation.
      if (localOp.getCancelRequest() != null)
      {
        return;
      }


      // Get the backend that should hold the search base.  If there is none,
      // then fail.
      if (backend == null)
      {
        localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
        localOp.appendErrorMessage(
            ERR_SEARCH_BASE_DOESNT_EXIST.get(String.valueOf(baseDN)));
        break searchProcessing;
      }


      // We'll set the result code to "success".  If a problem occurs, then it
      // will be overwritten.
      localOp.setResultCode(ResultCode.SUCCESS);


      // If there's a persistent search, then register it with the server.
      if (persistentSearch != null)
      {
        DirectoryServer.registerPersistentSearch(persistentSearch);
        localOp.setSendResponse(false);
      }


      // Process the search in the backend and all its subordinates.
      try
      {
        if (processSearch)
        {
          localOp.searchBackend(backend);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        localOp.setResultCode(de.getResultCode());
        localOp.appendErrorMessage(de.getMessageObject());
        localOp.setMatchedDN(de.getMatchedDN());
        localOp.setReferralURLs(de.getReferralURLs());

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          localOp.setSendResponse(true);
        }

        break searchProcessing;
      }
      catch (CancelledOperationException coe)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, coe);
        }

        CancelResult cancelResult = coe.getCancelResult();

        localOp.setCancelResult(cancelResult);
        localOp.setResultCode(cancelResult.getResultCode());

        Message message = coe.getMessageObject();
        if ((message != null) && (message.length() > 0))
        {
          localOp.appendErrorMessage(message);
        }

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          localOp.setSendResponse(true);
        }

        skipPostOperation = true;
        break searchProcessing;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        localOp.setResultCode(
            DirectoryServer.getServerErrorResultCode());

        localOp.appendErrorMessage(
            ERR_SEARCH_BACKEND_EXCEPTION.get(getExceptionMessage(e)));

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          localOp.setSendResponse(true);
        }

        skipPostOperation = true;
        break searchProcessing;
      }
    }


    // Check for a request to cancel this operation.
    if (localOp.getCancelRequest() != null)
    {
      return;
    }


    // Invoke the post-operation search plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
        pluginConfigManager.invokePostOperationSearchPlugins(localOp);
      if (postOperationResult.connectionTerminated())
      {
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());

        localOp.setProcessingStopTime();
        return;
      }
    }

  }

  /**
   * Perform a bind operation against a local backend.
   *
   * @param operation - The operation to perform
   */
  public void processBind(BindOperation operation)
  {
    LocalBackendBindOperation localOperation =
        new LocalBackendBindOperation(operation);

    processLocalBind(localOperation);
  }

  /**
   * Perform a local bind operation against a local backend.
   *
   * @param localOp - The operation to perform
   */
  private void processLocalBind(LocalBackendBindOperation localOp)
  {
    ClientConnection clientConnection = localOp.getClientConnection();

    boolean returnAuthzID    = false;
    int     sizeLimit        = DirectoryServer.getSizeLimit();
    int     timeLimit        = DirectoryServer.getTimeLimit();
    int     lookthroughLimit = DirectoryServer.getLookthroughLimit();
    long    idleTimeLimit    = DirectoryServer.getIdleTimeLimit();
    boolean skipPostOperation = false;

    // The password policy state information for this bind operation.
    PasswordPolicyState pwPolicyState = null;

    // The password policy error type that should be included in the response
    // control
    PasswordPolicyErrorType pwPolicyErrorType = null;

    // Indicates whether the client included the password policy control in the
    // bind request.
    boolean pwPolicyControlRequested = false;

    // Indicates whether the authentication should use a grace login if it is
    // successful.
    boolean isGraceLogin = false;

    // Indicates whether the user's password must be changed before any other
    // operations will be allowed.
    boolean mustChangePassword = false;

    // The password policy warning type that should be included in the response
    // control
    PasswordPolicyWarningType pwPolicyWarningType = null;

    // The password policy warning value that should be included in the response
    // control.
    int pwPolicyWarningValue = -1 ;

    String saslMechanism = localOp.getSASLMechanism();

    // Indicates whether the warning notification that should be sent to
    // the user would be the first warning.
    boolean isFirstWarning = false;

    // The entry of the user that successfully authenticated during processing
    // of this bind operation.
    Entry authenticatedUserEntry = null;

    // The password policy state information for this bind operation.
    pwPolicyState = null;

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
bindProcessing:
    {
      DN bindDN = localOp.getBindDN();
      // Check to see if the client has permission to perform the
      // bind.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes any controls
      // specified.
      if (AccessControlConfigManager.getInstance()
          .getAccessControlHandler().isAllowed(localOp) == false) {
        localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

        Message message = ERR_BIND_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                String.valueOf(bindDN));
        localOp.setAuthFailureReason(message);

        skipPostOperation = true;
        break bindProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then see
      // if there is any special processing required.
      List<Control> requestControls = localOp.getRequestControls();
      if ((requestControls != null) && (! requestControls.isEmpty()))
      {
        for (int i=0; i < requestControls.size(); i++)
        {
          Control c   = requestControls.get(i);
          String  oid = c.getOID();

          if (!AccessControlConfigManager.getInstance().
                  getAccessControlHandler(). isAllowed(bindDN, localOp, c))
          {
            localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            localOp.appendErrorMessage(ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS
                    .get(oid));
            skipPostOperation = true;
            break bindProcessing;
          }

          if (oid.equals(OID_AUTHZID_REQUEST))
          {
            returnAuthzID = true;
          }
          else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
          {
            pwPolicyControlRequested = true;
          }

          // NYI -- Add support for additional controls.
          else if (c.isCritical())
          {
            localOp.setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

            localOp.appendErrorMessage(ERR_BIND_UNSUPPORTED_CRITICAL_CONTROL
                    .get(String.valueOf(oid)));

            break bindProcessing;
          }
        }
      }


      // Check to see if this is a simple bind or a SASL bind and process
      // accordingly.
      switch (localOp.getAuthenticationType())
      {
        case SIMPLE:
          // See if this is an anonymous bind.  If so, then determine whether
          // to allow it.

          ByteString simplePassword = localOp.getSimplePassword();
          if ((simplePassword == null) || (simplePassword.value().length == 0))
          {
            // If the server is in lockdown mode, then fail.
            if (DirectoryServer.lockdownMode())
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              Message message = ERR_BIND_REJECTED_LOCKDOWN_MODE.get();
              localOp.setAuthFailureReason(message);

              localOp.setProcessingStopTime();
              logBindResponse(localOp);
              break bindProcessing;
            }

            // If there is a bind DN, then see whether that is acceptable.
            if (DirectoryServer.bindWithDNRequiresPassword() &&
                ((bindDN != null) && (! bindDN.isNullDN())))
            {
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

              Message message = ERR_BIND_DN_BUT_NO_PASSWORD.get();
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }


            // Invoke the pre-operation bind plugins.
            PreOperationPluginResult preOpResult =
                 pluginConfigManager.invokePreOperationBindPlugins(localOp);
            if (preOpResult.connectionTerminated())
            {
              // There's no point in continuing with anything.  Log the result
              // and return.
              localOp.setResultCode(ResultCode.CANCELED);

              localOp.appendErrorMessage(
                      ERR_CANCELED_BY_PREOP_DISCONNECT.get());

              return;
            }
            else if (preOpResult.sendResponseImmediately())
            {
              skipPostOperation = true;
              break bindProcessing;
            }
            else if (preOpResult.skipCoreProcessing())
            {
              skipPostOperation = false;
              break bindProcessing;
            }

            localOp.setResultCode(ResultCode.SUCCESS);
            localOp.setAuthenticationInfo(new AuthenticationInfo());
            break bindProcessing;
          }

          // See if the bind DN is actually one of the alternate root DNs
          // defined in the server.  If so, then replace it with the actual DN
          // for that user.
          DN actualRootDN = DirectoryServer.getActualRootBindDN(bindDN);
          if (actualRootDN != null)
          {
            bindDN = actualRootDN;
          }

          // Get the user entry based on the bind DN.  If it does not exist,
          // then fail.
          Lock userLock = null;
          for (int i=0; i < 3; i++)
          {
            userLock = LockManager.lockRead(bindDN);
            if (userLock != null)
            {
              break;
            }
          }

          if (userLock == null)
          {
            Message message = ERR_BIND_OPERATION_CANNOT_LOCK_USER.get(
                    String.valueOf(bindDN));

            localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
            localOp.setAuthFailureReason(message);
            break bindProcessing;
          }

          try
          {
            Entry userEntry;
            try
            {
              userEntry = backend.getEntry(bindDN);
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(de.getMessageObject());

              userEntry = null;
              break bindProcessing;
            }

            if (userEntry == null)
            {

              Message message = ERR_BIND_OPERATION_UNKNOWN_USER.get(
                      String.valueOf(bindDN));

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }
            else
            {
              localOp.setUserEntryDN(userEntry.getDN());
            }


            // Check to see if the user has a password.  If not, then fail.
            // FIXME -- We need to have a way to enable/disable debugging.
            pwPolicyState = new PasswordPolicyState(userEntry, false, false);
            PasswordPolicy policy = pwPolicyState.getPolicy();
            AttributeType  pwType = policy.getPasswordAttribute();

            List<Attribute> pwAttr = userEntry.getAttribute(pwType);
            if ((pwAttr == null) || (pwAttr.isEmpty()))
            {
              Message message = ERR_BIND_OPERATION_NO_PASSWORD.get(
                      String.valueOf(bindDN));

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }


            // If the password policy is configured to track authentication
            // failures or keep the last login time and the associated backend
            // is disabled, then we may need to reject the bind immediately.
            if ((policy.getStateUpdateFailurePolicy() ==
                 PasswordPolicyCfgDefn.StateUpdateFailurePolicy.PROACTIVE) &&
                ((policy.getLockoutFailureCount() > 0) ||
                 ((policy.getLastLoginTimeAttribute() != null) &&
                  (policy.getLastLoginTimeFormat() != null))) &&
                ((DirectoryServer.getWritabilityMode() ==
                  WritabilityMode.DISABLED) ||
                 (backend.getWritabilityMode() == WritabilityMode.DISABLED)))
            {
              // This policy isn't applicable to root users, so if it's a root
              // user then ignore it.
              if (! DirectoryServer.isRootDN(bindDN))
              {
                Message message = ERR_BIND_OPERATION_WRITABILITY_DISABLED.get(
                        String.valueOf(bindDN));

                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                localOp.setAuthFailureReason(message);
                break bindProcessing;
              }
            }


            // Check to see if the authentication must be done in a secure
            // manner.  If so, then the client connection must be secure.
            if (policy.requireSecureAuthentication() &&
                (! clientConnection.isSecure()))
            {
              Message message = ERR_BIND_OPERATION_INSECURE_SIMPLE_BIND.get(
                      String.valueOf(bindDN));

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }


            // Check to see if the user is administratively disabled or locked.
            if (pwPolicyState.isDisabled())
            {
              Message message = ERR_BIND_OPERATION_ACCOUNT_DISABLED.get(
                      String.valueOf(bindDN));

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }
            else if (pwPolicyState.isAccountExpired())
            {
              Message message = ERR_BIND_OPERATION_ACCOUNT_EXPIRED.get(
                      String.valueOf(bindDN));

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);

              pwPolicyState.generateAccountStatusNotification(
                   AccountStatusNotificationType.ACCOUNT_EXPIRED, userEntry,
                   message,
                   AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));

              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToFailures())
            {
              Message message = ERR_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED.get(
                      String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToMaximumResetAge())
            {
              Message message = ERR_BIND_OPERATION_ACCOUNT_RESET_LOCKED.get(
                      String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);

              pwPolicyState.generateAccountStatusNotification(
                   AccountStatusNotificationType.ACCOUNT_RESET_LOCKED,
                   userEntry, message,
                   AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));

              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToIdleInterval())
            {
              Message message = ERR_BIND_OPERATION_ACCOUNT_IDLE_LOCKED.get(
                      String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);

              pwPolicyState.generateAccountStatusNotification(
                   AccountStatusNotificationType.ACCOUNT_IDLE_LOCKED, userEntry,
                   message,
                   AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));

              break bindProcessing;
            }


            // Determine whether the password is expired, or whether the user
            // should be warned about an upcoming expiration.
            if (pwPolicyState.isPasswordExpired())
            {
              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.PASSWORD_EXPIRED;
              }

              int maxGraceLogins = policy.getGraceLoginCount();
              if ((maxGraceLogins > 0) && pwPolicyState.mayUseGraceLogin())
              {
                List<Long> graceLoginTimes = pwPolicyState.getGraceLoginTimes();
                if ((graceLoginTimes == null) ||
                    (graceLoginTimes.size() < maxGraceLogins))
                {
                  isGraceLogin       = true;
                  mustChangePassword = true;

                  if (pwPolicyWarningType == null)
                  {
                    pwPolicyWarningType =
                         PasswordPolicyWarningType.GRACE_LOGINS_REMAINING;
                    pwPolicyWarningValue = maxGraceLogins -
                                           (graceLoginTimes.size() + 1);
                  }
                }
                else
                {
                  Message message = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get(
                          String.valueOf(bindDN));

                  localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                  localOp.setAuthFailureReason(message);

                  pwPolicyState.generateAccountStatusNotification(
                       AccountStatusNotificationType.PASSWORD_EXPIRED,
                       userEntry, message,
                       AccountStatusNotification.createProperties(pwPolicyState,
                             false, -1, null, null));

                  break bindProcessing;
                }
              }
              else
              {
                Message message = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get(
                        String.valueOf(bindDN));

                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                localOp.setAuthFailureReason(message);

                pwPolicyState.generateAccountStatusNotification(
                     AccountStatusNotificationType.PASSWORD_EXPIRED, userEntry,
                     message,
                     AccountStatusNotification.createProperties(pwPolicyState,
                           false, -1, null, null));

                break bindProcessing;
              }
            }
            else if (pwPolicyState.shouldWarn())
            {
              int numSeconds = pwPolicyState.getSecondsUntilExpiration();

              if (pwPolicyWarningType == null)
              {
                pwPolicyWarningType =
                     PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
                pwPolicyWarningValue = numSeconds;
              }

              isFirstWarning = pwPolicyState.isFirstWarning();
            }


            // Check to see if the user's password has been reset.
            if (pwPolicyState.mustChangePassword())
            {
              mustChangePassword = true;

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
              }
            }


            // Invoke the pre-operation bind plugins.
            PreOperationPluginResult preOpResult =
                 pluginConfigManager.invokePreOperationBindPlugins(localOp);
            if (preOpResult.connectionTerminated())
            {
              // There's no point in continuing with anything.  Log the result
              // and return.
              localOp.setResultCode(ResultCode.CANCELED);

              localOp.appendErrorMessage(
                      ERR_CANCELED_BY_PREOP_DISCONNECT.get());

              return;
            }
            else if (preOpResult.sendResponseImmediately())
            {
              skipPostOperation = true;
              break bindProcessing;
            }
            else if (preOpResult.skipCoreProcessing())
            {
              skipPostOperation = false;
              break bindProcessing;
            }


            // Determine whether the provided password matches any of the stored
            // passwords for the user.
            if (pwPolicyState.passwordMatches(simplePassword))
            {
              localOp.setResultCode(ResultCode.SUCCESS);

              boolean isRoot = DirectoryServer.isRootDN(userEntry.getDN());
              if (DirectoryServer.lockdownMode() && (! isRoot))
              {
                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

                Message message = ERR_BIND_REJECTED_LOCKDOWN_MODE.get();
                localOp.setAuthFailureReason(message);

                break bindProcessing;
              }
              localOp.setAuthenticationInfo(new AuthenticationInfo(
                                                userEntry,
                                                simplePassword,
                                                isRoot));


              // See if the user's entry contains a custom size limit.
              AttributeType attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_SIZE_LIMIT,
                                                 true);
              List<Attribute> attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message = WARN_BIND_MULTIPLE_USER_SIZE_LIMITS.get(
                        String.valueOf(userEntry.getDN()));
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      sizeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message = WARN_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT
                              .get(v.getStringValue(),
                                String.valueOf(userEntry.getDN()));
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom time limit.
              attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_TIME_LIMIT,
                                                 true);
              attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message = WARN_BIND_MULTIPLE_USER_TIME_LIMITS.get(
                        String.valueOf(userEntry.getDN()));
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      timeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_TIME_LIMIT.
                            get(v.getStringValue(),
                                String.valueOf(userEntry.getDN()));
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom idle time limit.
              attrType = DirectoryServer.getAttributeType(
                              OP_ATTR_USER_IDLE_TIME_LIMIT, true);
              attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message = WARN_BIND_MULTIPLE_USER_IDLE_TIME_LIMITS.
                        get(String.valueOf(userEntry.getDN()));
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      idleTimeLimit =
                           1000L * Long.parseLong(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_IDLE_TIME_LIMIT.
                            get(v.getStringValue(),
                                String.valueOf(userEntry.getDN()));
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom lookthrough limit.
              attrType =
                   DirectoryServer.getAttributeType(
                       OP_ATTR_USER_LOOKTHROUGH_LIMIT, true);
              attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message =
                        WARN_BIND_MULTIPLE_USER_LOOKTHROUGH_LIMITS.
                          get(String.valueOf(userEntry.getDN()));
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      lookthroughLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_LOOKTHROUGH_LIMIT.
                            get(v.getStringValue(),
                                String.valueOf(userEntry.getDN()));
                      logError(message);
                    }
                  }
                }
              }


              pwPolicyState.handleDeprecatedStorageSchemes(simplePassword);
              pwPolicyState.clearFailureLockout();

              if (isFirstWarning)
              {
                pwPolicyState.setWarnedTime();

                int numSeconds = pwPolicyState.getSecondsUntilExpiration();
                Message timeToExpiration = secondsToTimeString(numSeconds);

                Message message = WARN_BIND_PASSWORD_EXPIRING.get(
                        timeToExpiration);

                pwPolicyState.generateAccountStatusNotification(
                     AccountStatusNotificationType.PASSWORD_EXPIRING, userEntry,
                     message,
                     AccountStatusNotification.createProperties(pwPolicyState,
                           false, numSeconds, null, null));
              }

              if (isGraceLogin)
              {
                pwPolicyState.updateGraceLoginTimes();
              }

              pwPolicyState.setLastLoginTime();
            }
            else
            {
              Message message = ERR_BIND_OPERATION_WRONG_PASSWORD.get();

              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
              localOp.setAuthFailureReason(message);

              if (policy.getLockoutFailureCount() > 0)
              {
                pwPolicyState.updateAuthFailureTimes();
                if (pwPolicyState.lockedDueToFailures())
                {
                  AccountStatusNotificationType notificationType;

                  boolean tempLocked;
                  int lockoutDuration = pwPolicyState.getSecondsUntilUnlock();
                  if (lockoutDuration > -1)
                  {
                    notificationType = AccountStatusNotificationType.
                                            ACCOUNT_TEMPORARILY_LOCKED;
                    tempLocked = true;

                    message = ERR_BIND_ACCOUNT_TEMPORARILY_LOCKED.get(
                            secondsToTimeString(lockoutDuration));
                  }
                  else
                  {
                    notificationType = AccountStatusNotificationType.
                                            ACCOUNT_PERMANENTLY_LOCKED;
                    tempLocked = false;

                    message = ERR_BIND_ACCOUNT_PERMANENTLY_LOCKED.get();
                  }

                  pwPolicyState.generateAccountStatusNotification(
                       notificationType, userEntry, message,
                       AccountStatusNotification.createProperties(pwPolicyState,
                             tempLocked, -1, null, null));
                }
              }
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            Message message = ERR_BIND_OPERATION_PASSWORD_VALIDATION_EXCEPTION
                    .get(getExceptionMessage(e));

            localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
            localOp.setAuthFailureReason(message);
            break bindProcessing;
          }
          finally
          {
            // No matter what, make sure to unlock the user's entry.
            LockManager.unlock(bindDN, userLock);
          }

          break;


        case SASL:
          // Get the appropriate authentication handler for this request based
          // on the SASL mechanism.  If there is none, then fail.
          SASLMechanismHandler saslHandler =
               DirectoryServer.getSASLMechanismHandler(saslMechanism);
          if (saslHandler == null)
          {
            localOp.setResultCode(ResultCode.AUTH_METHOD_NOT_SUPPORTED);

            Message message = ERR_BIND_OPERATION_UNKNOWN_SASL_MECHANISM.get(
                    saslMechanism);

            localOp.appendErrorMessage(message);
            localOp.setAuthFailureReason(message);
            break bindProcessing;
          }


          // Check to see if the client has sufficient permission to perform the
          // bind.
          // NYI


          // Invoke the pre-operation bind plugins.
          PreOperationPluginResult preOpResult =
               pluginConfigManager.invokePreOperationBindPlugins(localOp);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            localOp.setResultCode(ResultCode.CANCELED);

            localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break bindProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break bindProcessing;
          }

          // Actually process the SASL bind.
          saslHandler.processSASLBind(localOp);

          // If the server is operating in lockdown mode, then we will need to
          // ensure that the authentication was successful and performed as a
          // root user to continue.
          if (DirectoryServer.lockdownMode())
          {
            ResultCode resultCode = localOp.getResultCode();
            if (resultCode != ResultCode.SASL_BIND_IN_PROGRESS)
            {
              if ((resultCode != ResultCode.SUCCESS) ||
                  (localOp.getSASLAuthUserEntry() == null) ||
                  (! DirectoryServer.isRootDN(
                      localOp.getSASLAuthUserEntry().getDN())))
              {
                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

                Message message = ERR_BIND_REJECTED_LOCKDOWN_MODE.get();
                localOp.setAuthFailureReason(message);

                break bindProcessing;
              }
            }
          }

          // Create the password policy state object.
          String userDNString;
          Entry saslAuthUserEntry = localOp.getSASLAuthUserEntry();
          if (saslAuthUserEntry == null)
          {
            pwPolicyState = null;
            userDNString  = null;
          }
          else
          {
            try
            {
              // FIXME -- Need to have a way to enable debugging.
              pwPolicyState = new PasswordPolicyState(saslAuthUserEntry, false,
                                                      false);
              localOp.setUserEntryDN(saslAuthUserEntry.getDN());
              userDNString = String.valueOf(localOp.getUserEntryDN());
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              localOp.setResponseData(de);
              break bindProcessing;
            }
          }


          // Perform password policy checks that will need to be completed
          // regardless of whether the authentication was successful.
          if (pwPolicyState != null)
          {
            PasswordPolicy policy = pwPolicyState.getPolicy();

            // If the password policy is configured to track authentication
            // failures or keep the last login time and the associated backend
            // is disabled, then we may need to reject the bind immediately.
            if ((policy.getStateUpdateFailurePolicy() ==
                 PasswordPolicyCfgDefn.StateUpdateFailurePolicy.PROACTIVE) &&
                ((policy.getLockoutFailureCount() > 0) ||
                 ((policy.getLastLoginTimeAttribute() != null) &&
                  (policy.getLastLoginTimeFormat() != null))) &&
                ((DirectoryServer.getWritabilityMode() ==
                  WritabilityMode.DISABLED) ||
                 (backend.getWritabilityMode() == WritabilityMode.DISABLED)))
            {
              // This policy isn't applicable to root users, so if it's a root
              // user then ignore it.
              if (! DirectoryServer.isRootDN(bindDN))
              {
                Message message = ERR_BIND_OPERATION_WRITABILITY_DISABLED.get(
                        userDNString);

                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                localOp.setAuthFailureReason(message);
                break bindProcessing;
              }
            }
            else if (pwPolicyState.isDisabled())
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              Message message = ERR_BIND_OPERATION_ACCOUNT_DISABLED.get(
                      userDNString);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }
            else if (pwPolicyState.isAccountExpired())
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              Message message = ERR_BIND_OPERATION_ACCOUNT_EXPIRED.get(
                      userDNString);
              localOp.setAuthFailureReason(message);

              pwPolicyState.generateAccountStatusNotification(
                   AccountStatusNotificationType.ACCOUNT_EXPIRED,
                   saslAuthUserEntry, message,
                   AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));

              break bindProcessing;
            }

            if (policy.requireSecureAuthentication() &&
                (! clientConnection.isSecure()) &&
                (! saslHandler.isSecure(saslMechanism)))
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              Message message = ERR_BIND_OPERATION_INSECURE_SASL_BIND.get(
                      saslMechanism, userDNString);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }

            if (pwPolicyState.lockedDueToFailures())
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              Message message = ERR_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED.get(
                      userDNString);
              localOp.setAuthFailureReason(message);
              break bindProcessing;
            }

            if (pwPolicyState.lockedDueToIdleInterval())
            {
              localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              Message message = ERR_BIND_OPERATION_ACCOUNT_IDLE_LOCKED.get(
                      userDNString);
              localOp.setAuthFailureReason(message);

              pwPolicyState.generateAccountStatusNotification(
                   AccountStatusNotificationType.ACCOUNT_IDLE_LOCKED,
                   saslAuthUserEntry, message,
                   AccountStatusNotification.createProperties(pwPolicyState,
                         false, -1, null, null));

              break bindProcessing;
            }


            if (saslHandler.isPasswordBased(saslMechanism))
            {
              if (pwPolicyState.lockedDueToMaximumResetAge())
              {
                localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);

                if (pwPolicyErrorType == null)
                {
                  pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
                }

                Message message = ERR_BIND_OPERATION_ACCOUNT_RESET_LOCKED.get(
                        userDNString);
                localOp.setAuthFailureReason(message);

                pwPolicyState.generateAccountStatusNotification(
                     AccountStatusNotificationType.ACCOUNT_RESET_LOCKED,
                     saslAuthUserEntry, message,
                     AccountStatusNotification.createProperties(pwPolicyState,
                           false, -1, null, null));

                break bindProcessing;
              }

              if (pwPolicyState.isPasswordExpired())
              {
                if (pwPolicyErrorType == null)
                {
                  pwPolicyErrorType = PasswordPolicyErrorType.PASSWORD_EXPIRED;
                }

                int maxGraceLogins = policy.getGraceLoginCount();
                if ((maxGraceLogins > 0) && pwPolicyState.mayUseGraceLogin())
                {
                  List<Long> graceLoginTimes =
                       pwPolicyState.getGraceLoginTimes();
                  if ((graceLoginTimes == null) ||
                      (graceLoginTimes.size() < maxGraceLogins))
                  {
                    isGraceLogin       = true;
                    mustChangePassword = true;

                    if (pwPolicyWarningType == null)
                    {
                      pwPolicyWarningType =
                           PasswordPolicyWarningType.GRACE_LOGINS_REMAINING;
                      pwPolicyWarningValue =
                           maxGraceLogins - (graceLoginTimes.size() + 1);
                    }
                  }
                  else
                  {
                    Message message = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get(
                            String.valueOf(bindDN));

                    localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                    localOp.setAuthFailureReason(message);

                    pwPolicyState.generateAccountStatusNotification(
                         AccountStatusNotificationType.PASSWORD_EXPIRED,
                         saslAuthUserEntry, message,
                         AccountStatusNotification.createProperties(
                              pwPolicyState, false, -1, null, null));

                    break bindProcessing;
                  }
                }
                else
                {
                  Message message = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get(
                          String.valueOf(bindDN));

                  localOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
                  localOp.setAuthFailureReason(message);

                  pwPolicyState.generateAccountStatusNotification(
                       AccountStatusNotificationType.PASSWORD_EXPIRED,
                       saslAuthUserEntry, message,
                       AccountStatusNotification.createProperties(pwPolicyState,
                             false, -1, null, null));

                  break bindProcessing;
                }
              }
              else if (pwPolicyState.shouldWarn())
              {
                int numSeconds = pwPolicyState.getSecondsUntilExpiration();

                if (pwPolicyWarningType == null)
                {
                  pwPolicyWarningType =
                       PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
                  pwPolicyWarningValue = numSeconds;
                }

                isFirstWarning = pwPolicyState.isFirstWarning();
              }
            }
          }


          // Determine whether the authentication was successful and perform
          // any remaining password policy processing accordingly.  Also check
          // for a custom size/time limit.
          ResultCode resultCode = localOp.getResultCode();
          if (resultCode == ResultCode.SUCCESS)
          {
            if (pwPolicyState != null)
            {
              if (saslHandler.isPasswordBased(saslMechanism) &&
                  pwPolicyState.mustChangePassword())
              {
                mustChangePassword = true;
              }

              if (isFirstWarning)
              {
                pwPolicyState.setWarnedTime();

                int numSeconds = pwPolicyState.getSecondsUntilExpiration();
                Message timeToExpiration = secondsToTimeString(numSeconds);

                Message message = WARN_BIND_PASSWORD_EXPIRING.get(
                        timeToExpiration);

                pwPolicyState.generateAccountStatusNotification(
                     AccountStatusNotificationType.PASSWORD_EXPIRING,
                     saslAuthUserEntry, message,
                     AccountStatusNotification.createProperties(pwPolicyState,
                           false, numSeconds, null, null));
              }

              if (isGraceLogin)
              {
                pwPolicyState.updateGraceLoginTimes();
              }

              pwPolicyState.setLastLoginTime();


              // See if the user's entry contains a custom size limit.
              AttributeType attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_SIZE_LIMIT,
                                                 true);
              List<Attribute> attrList =
                   saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message =
                        WARN_BIND_MULTIPLE_USER_SIZE_LIMITS.get(userDNString);
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      sizeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT.
                            get(v.getStringValue(), userDNString);
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom time limit.
              attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_TIME_LIMIT,
                                                    true);
              attrList = saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message =
                        WARN_BIND_MULTIPLE_USER_TIME_LIMITS.get(userDNString);
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      timeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_TIME_LIMIT.
                            get(v.getStringValue(), userDNString);
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom idle time limit.
              attrType = DirectoryServer.getAttributeType(
                              OP_ATTR_USER_IDLE_TIME_LIMIT, true);
              attrList = saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message = WARN_BIND_MULTIPLE_USER_IDLE_TIME_LIMITS.
                        get(String.valueOf(userDNString));
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      idleTimeLimit =
                           1000L * Long.parseLong(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_IDLE_TIME_LIMIT.
                            get(v.getStringValue(),
                                String.valueOf(userDNString));
                      logError(message);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom lookthrough limit.
              attrType =
                   DirectoryServer.getAttributeType(
                       OP_ATTR_USER_LOOKTHROUGH_LIMIT, true);
              attrList = saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    Message message =
                        WARN_BIND_MULTIPLE_USER_LOOKTHROUGH_LIMITS.
                          get(userDNString);
                    logError(message);
                  }
                  else
                  {
                    try
                    {
                      lookthroughLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      if (debugEnabled())
                      {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                      }

                      Message message =
                          WARN_BIND_CANNOT_PROCESS_USER_LOOKTHROUGH_LIMIT.
                            get(v.getStringValue(), userDNString);
                      logError(message);
                    }
                  }
                }
              }
            }
          }
          else if (resultCode == ResultCode.SASL_BIND_IN_PROGRESS)
          {
            // FIXME -- Is any special processing needed here?
          }
          else
          {
            if (pwPolicyState != null)
            {
              if (saslHandler.isPasswordBased(saslMechanism))
              {

                if (pwPolicyState.getPolicy().getLockoutFailureCount() > 0)
                {
                  pwPolicyState.updateAuthFailureTimes();
                  if (pwPolicyState.lockedDueToFailures())
                  {
                    AccountStatusNotificationType notificationType;
                    boolean tempLocked;
                    Message message;

                    int lockoutDuration = pwPolicyState.getSecondsUntilUnlock();
                    if (lockoutDuration > -1)
                    {
                      notificationType = AccountStatusNotificationType.
                                              ACCOUNT_TEMPORARILY_LOCKED;
                      tempLocked = true;
                      message = ERR_BIND_ACCOUNT_TEMPORARILY_LOCKED.get(
                              secondsToTimeString(lockoutDuration));
                    }
                    else
                    {
                      notificationType = AccountStatusNotificationType.
                                              ACCOUNT_PERMANENTLY_LOCKED;
                      tempLocked = false;
                      message = ERR_BIND_ACCOUNT_PERMANENTLY_LOCKED.get();
                    }

                    pwPolicyState.generateAccountStatusNotification(
                         notificationType, saslAuthUserEntry, message,
                         AccountStatusNotification.createProperties(
                              pwPolicyState, tempLocked, -1, null, null));
                  }
                }
              }
            }
          }

          break;


        default:
          // Send a protocol error response to the client and disconnect.
          // NYI
          return;
      }
    }


    // Update the user's account with any password policy changes that may be
    // required.
    try
    {
      if (pwPolicyState != null)
      {
        pwPolicyState.updateUserEntry();
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      localOp.setResponseData(de);
    }


    // Invoke the post-operation bind plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationBindPlugins(localOp);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result
        // and return.
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

        return;
      }
    }


    // Update the authentication information for the user.
    AuthenticationInfo authInfo = localOp.getAuthenticationInfo();
    if ((localOp.getResultCode() == ResultCode.SUCCESS) &&
        (authInfo != null))
    {
      authenticatedUserEntry = authInfo.getAuthenticationEntry();
      clientConnection.setAuthenticationInfo(authInfo);
      clientConnection.setSizeLimit(sizeLimit);
      clientConnection.setTimeLimit(timeLimit);
      clientConnection.setIdleTimeLimit(idleTimeLimit);
      clientConnection.setLookthroughLimit(lookthroughLimit);
      clientConnection.setMustChangePassword(mustChangePassword);

      if (returnAuthzID)
      {
        localOp.addResponseControl(
            new AuthorizationIdentityResponseControl(
                                      authInfo.getAuthorizationDN()));
      }
    }


    // See if we need to send a password policy control to the client.  If so,
    // then add it to the response.
    if (localOp.getResultCode() == ResultCode.SUCCESS)
    {
      if (pwPolicyControlRequested)
      {
        PasswordPolicyResponseControl pwpControl =
             new PasswordPolicyResponseControl(pwPolicyWarningType,
                                               pwPolicyWarningValue,
                                               pwPolicyErrorType);
        localOp.addResponseControl(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          localOp.addResponseControl(new PasswordExpiredControl());
        }
        else if (pwPolicyWarningType ==
                 PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION)
        {
          localOp.addResponseControl(new PasswordExpiringControl(
                                        pwPolicyWarningValue));
        }
      }
    }
    else
    {
      if (pwPolicyControlRequested)
      {
        PasswordPolicyResponseControl pwpControl =
             new PasswordPolicyResponseControl(pwPolicyWarningType,
                                               pwPolicyWarningValue,
                                               pwPolicyErrorType);
        localOp.addResponseControl(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          localOp.addResponseControl(new PasswordExpiredControl());
        }
      }
    }

    // Stop the processing timer.
    localOp.setProcessingStopTime();

  }

  /**
   * Perform an add operation against a local backend.
   *
   * @param operation - The operation to perform
   */
  public void processAdd(AddOperation operation)
  {
    LocalBackendAddOperation localOperation =
      new LocalBackendAddOperation(operation);

    processLocalAdd(localOperation);
  }

  /**
   * Perform a local add operation against a local backend.
   *
   * @param localOp - The operation to perform
   */
  private void processLocalAdd(LocalBackendAddOperation localOp)
  {
    ClientConnection clientConnection = localOp.getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    // Check for a request to cancel this operation.
    if (localOp.getCancelRequest() != null)
    {
      return;
    }

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
addProcessing:
    {
      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
      DN entryDN = localOp.getEntryDN();
      if (entryDN == null){
        break addProcessing;
      }

      Map<ObjectClass, String> objectClasses =
        localOp.getObjectClasses();
      Map<AttributeType, List<Attribute>> userAttributes =
        localOp.getUserAttributes();
      Map<AttributeType, List<Attribute>> operationalAttributes =
        localOp.getOperationalAttributes();

      if ((objectClasses == null ) ||
          (userAttributes == null) ||
          (operationalAttributes == null)){
        break addProcessing;
      }

      // Check for a request to cancel this operation.
      if (localOp.getCancelRequest() != null)
      {
        return;
      }


      // Grab a read lock on the parent entry, if there is one.  We need to do
      // this to ensure that the parent is not deleted or renamed while this add
      // is in progress, and we could also need it to check the entry against
      // a DIT structure rule.
      Lock parentLock = null;
      Lock entryLock  = null;

      DN parentDN = entryDN.getParentDNInSuffix();
      if (parentDN == null)
      {
        // Either this entry is a suffix or doesn't belong in the directory.
        if (DirectoryServer.isNamingContext(entryDN))
        {
          // This is fine.  This entry is one of the configured suffixes.
          parentLock = null;
        }
        else if (entryDN.isNullDN())
        {
          // This is not fine.  The root DSE cannot be added.
          localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          localOp.appendErrorMessage(ERR_ADD_CANNOT_ADD_ROOT_DSE.get());
          break addProcessing;
        }
        else
        {
          // The entry doesn't have a parent but isn't a suffix.  This is not
          // allowed.
          localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
          localOp.appendErrorMessage(ERR_ADD_ENTRY_NOT_SUFFIX.get(
                  String.valueOf(entryDN)));
          break addProcessing;
        }
      }
      else
      {
        for (int i=0; i < 3; i++)
        {
          parentLock = LockManager.lockRead(parentDN);
          if (parentLock != null)
          {
            break;
          }
        }

        if (parentLock == null)
        {
          localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
          localOp.appendErrorMessage(ERR_ADD_CANNOT_LOCK_PARENT.get(
                  String.valueOf(entryDN),
                  String.valueOf(parentDN)));

          skipPostOperation = true;
          break addProcessing;
        }
      }


      try
      {
        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Grab a write lock on the target entry.  We'll need to do this
        // eventually anyway, and we want to make sure that the two locks are
        // always released when exiting this method, no matter what.  Since
        // the entry shouldn't exist yet, locking earlier than necessary
        // shouldn't cause a problem.
        for (int i=0; i < 3; i++)
        {
          entryLock = LockManager.lockWrite(entryDN);
          if (entryLock != null)
          {
            break;
          }
        }

        if (entryLock == null)
        {
          localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
          localOp.appendErrorMessage(ERR_ADD_CANNOT_LOCK_ENTRY.get(
                  String.valueOf(entryDN)));

          skipPostOperation = true;
          break addProcessing;
        }


        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                 provider.handleConflictResolution(localOp);
            if (! result.continueOperationProcessing())
            {
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED.
                get(localOp.getConnectionID(), localOp.getOperationID(),
                    getExceptionMessage(de)));

            localOp.setResponseData(de);
            break addProcessing;
          }
        }

        // If the attribute type is marked "NO-USER-MODIFICATION" then fail
        // unless this is an internal operation or is related to
        // synchronization in some way.
        // This must be done before running the password policy code
        // and any other code that may add attributes marked as
        // "NO-USER-MODIFICATION"
        //
        // Note that doing this checks at this time
        // of the processing does not make it possible for pre-parse plugins
        // to add NO-USER-MODIFICATION attributes to the entry.
        for (AttributeType at : userAttributes.keySet())
        {
          if (at.isNoUserModification())
          {
            if (! (localOp.isInternalOperation() ||
              localOp.isSynchronizationOperation()))
            {
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                  String.valueOf(entryDN), at.getNameOrOID()));

              break addProcessing;
            }
          }
        }

        for (AttributeType at : operationalAttributes.keySet())
        {
          if (at.isNoUserModification())
          {
            if (! (localOp.isInternalOperation() ||
              localOp.isSynchronizationOperation()))
            {
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                  String.valueOf(entryDN), at.getNameOrOID()));

              break addProcessing;
            }
          }
        }

        // Check to see if the entry already exists.  We do this before
        // checking whether the parent exists to ensure a referral entry
        // above the parent results in a correct referral.
        try
        {
          if (DirectoryServer.entryExists(entryDN))
          {
            localOp.setResultCode(ResultCode.ENTRY_ALREADY_EXISTS);
            localOp.appendErrorMessage(ERR_ADD_ENTRY_ALREADY_EXISTS.get(
                    String.valueOf(entryDN)));
            break addProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          localOp.setMatchedDN(de.getMatchedDN());
          localOp.setReferralURLs(de.getReferralURLs());
          break addProcessing;
        }


        // Get the parent entry, if it exists.
        Entry parentEntry = null;
        if (parentDN != null)
        {
          try
          {
            parentEntry = DirectoryServer.getEntry(parentDN);

            if (parentEntry == null)
            {
              DN matchedDN = parentDN.getParentDNInSuffix();
              while (matchedDN != null)
              {
                try
                {
                  if (DirectoryServer.entryExists(matchedDN))
                  {
                    localOp.setMatchedDN(matchedDN);
                    break;
                  }
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }
                  break;
                }

                matchedDN = matchedDN.getParentDNInSuffix();
              }


              // The parent doesn't exist, so this add can't be successful.
              localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
              localOp.appendErrorMessage(ERR_ADD_NO_PARENT.get(
                      String.valueOf(entryDN),
                      String.valueOf(parentDN)));
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            localOp.setResultCode(de.getResultCode());
            localOp.appendErrorMessage(de.getMessageObject());
            localOp.setMatchedDN(de.getMatchedDN());
            localOp.setReferralURLs(de.getReferralURLs());
            break addProcessing;
          }
        }


        // Check to make sure that all of the RDN attributes are included as
        // attribute values.  If not, then either add them or report an error.
        RDN rdn = entryDN.getRDN();
        int numAVAs = rdn.getNumValues();
        for (int i=0; i < numAVAs; i++)
        {
          AttributeType  t = rdn.getAttributeType(i);
          AttributeValue v = rdn.getAttributeValue(i);
          String         n = rdn.getAttributeName(i);
          if (t.isOperational())
          {
            List<Attribute> attrList = operationalAttributes.get(t);
            if (attrList == null)
            {
              if (localOp.isSynchronizationOperation() ||
                  DirectoryServer.addMissingRDNAttributes())
              {
                LinkedHashSet<AttributeValue> valueList =
                     new LinkedHashSet<AttributeValue>(1);
                valueList.add(v);

                attrList = new ArrayList<Attribute>();
                attrList.add(new Attribute(t, n, valueList));

                operationalAttributes.put(t, attrList);
              }
              else
              {
                localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                localOp.appendErrorMessage(ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                        String.valueOf(entryDN),
                        n));

                break addProcessing;
              }
            }
            else
            {
              boolean found = false;
              for (Attribute a : attrList)
              {
                if (a.hasOptions())
                {
                  continue;
                }
                else
                {
                  if (! a.hasValue(v))
                  {
                    a.getValues().add(v);
                  }

                  found = true;
                  break;
                }
              }

              if (! found)
              {
                if (localOp.isSynchronizationOperation() ||
                    DirectoryServer.addMissingRDNAttributes())
                {
                  LinkedHashSet<AttributeValue> valueList =
                       new LinkedHashSet<AttributeValue>(1);
                  valueList.add(v);
                  attrList.add(new Attribute(t, n, valueList));
                }
                else
                {
                  localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  localOp.appendErrorMessage(
                      ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                              String.valueOf(entryDN), n));

                  break addProcessing;
                }
              }
            }
          }
          else
          {
            List<Attribute> attrList = userAttributes.get(t);
            if (attrList == null)
            {
              if (localOp.isSynchronizationOperation() ||
                  DirectoryServer.addMissingRDNAttributes())
              {
                LinkedHashSet<AttributeValue> valueList =
                     new LinkedHashSet<AttributeValue>(1);
                valueList.add(v);

                attrList = new ArrayList<Attribute>();
                attrList.add(new Attribute(t, n, valueList));

                userAttributes.put(t, attrList);
              }
              else
              {
                localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                localOp.appendErrorMessage(
                    ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                            String.valueOf(entryDN),n));

                break addProcessing;
              }
            }
            else
            {
              boolean found = false;
              for (Attribute a : attrList)
              {
                if (a.hasOptions())
                {
                  continue;
                }
                else
                {
                  if (! a.hasValue(v))
                  {
                    a.getValues().add(v);
                  }

                  found = true;
                  break;
                }
              }

              if (! found)
              {
                if (localOp.isSynchronizationOperation() ||
                    DirectoryServer.addMissingRDNAttributes())
                {
                  LinkedHashSet<AttributeValue> valueList =
                       new LinkedHashSet<AttributeValue>(1);
                  valueList.add(v);
                  attrList.add(new Attribute(t, n, valueList));
                }
                else
                {
                  localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  localOp.appendErrorMessage(
                      ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                              String.valueOf(entryDN),n));

                  break addProcessing;
                }
              }
            }
          }
        }


        // Check to make sure that all objectclasses have their superior classes
        // listed in the entry.  If not, then add them.
        HashSet<ObjectClass> additionalClasses = null;
        for (ObjectClass oc : objectClasses.keySet())
        {
          ObjectClass superiorClass = oc.getSuperiorClass();
          if ((superiorClass != null) &&
              (! objectClasses.containsKey(superiorClass)))
          {
            if (additionalClasses == null)
            {
              additionalClasses = new HashSet<ObjectClass>();
            }

            additionalClasses.add(superiorClass);
          }
        }

        if (additionalClasses != null)
        {
          for (ObjectClass oc : additionalClasses)
          {
            localOp.addObjectClassChain(oc);
          }
        }


        // Create an entry object to encapsulate the set of attributes and
        // objectclasses.
        Entry entry = new Entry(entryDN, objectClasses, userAttributes,
                          operationalAttributes);
        localOp.setEntryToAdd(entry);

        // Check to see if the entry includes a privilege specification.  If so,
        // then the requester must have the PRIVILEGE_CHANGE privilege.
        AttributeType privType =
             DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME, true);
        if (entry.hasAttribute(privType) &&
            (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE,
                localOp)))
        {

          localOp.appendErrorMessage(
                  ERR_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
          localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          break addProcessing;
        }


        // If it's not a synchronization operation, then check
        // to see if the entry contains one or more passwords and if they
        // are valid in accordance with the password policies associated with
        // the user.  Also perform any encoding that might be required by
        // password storage schemes.
        if (! localOp.isSynchronizationOperation())
        {
          // FIXME -- We need to check to see if the password policy subentry
          //          might be specified virtually rather than as a real
          //          attribute.
          PasswordPolicy pwPolicy = null;
          List<Attribute> pwAttrList =
               entry.getAttribute(OP_ATTR_PWPOLICY_POLICY_DN);
          if ((pwAttrList != null) && (! pwAttrList.isEmpty()))
          {
            Attribute a = pwAttrList.get(0);
            LinkedHashSet<AttributeValue> valueSet = a.getValues();
            Iterator<AttributeValue> iterator = valueSet.iterator();
            if (iterator.hasNext())
            {
              DN policyDN;
              try
              {
                policyDN = DN.decode(iterator.next().getValue());
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.appendErrorMessage(
                    ERR_ADD_INVALID_PWPOLICY_DN_SYNTAX.get(
                            String.valueOf(entryDN),
                            de.getMessageObject()));

                localOp.setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                break addProcessing;
              }

              pwPolicy = DirectoryServer.getPasswordPolicy(policyDN);
              if (pwPolicy == null)
              {
                localOp.appendErrorMessage(
                    ERR_ADD_NO_SUCH_PWPOLICY.get(String.valueOf(entryDN),
                        String.valueOf(policyDN)));

                localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
                break addProcessing;
              }
            }
          }

          if (pwPolicy == null)
          {
            pwPolicy = DirectoryServer.getDefaultPasswordPolicy();
          }

          try
          {
            localOp.handlePasswordPolicy(pwPolicy, entry);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            localOp.setResponseData(de);
            break addProcessing;
          }
        }

        // If the server is configured to check schema and the
        // operation is not a synchronization operation,
        // check to see if the entry is valid according to the server schema,
        // and also whether its attributes are valid according to their syntax.
        if ((DirectoryServer.checkSchema()) &&
            (!localOp.isSynchronizationOperation()) )
        {
          MessageBuilder invalidReason = new MessageBuilder();
          if (! entry.conformsToSchema(parentEntry, true, true, true,
                                       invalidReason))
          {
            localOp.setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            localOp.setErrorMessage(invalidReason);
            break addProcessing;
          }
          else
          {
            switch (DirectoryServer.getSyntaxEnforcementPolicy())
            {
              case REJECT:
                invalidReason = new MessageBuilder();
                for (List<Attribute> attrList : userAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          Message message = WARN_ADD_OP_INVALID_SYNTAX.
                              get(String.valueOf(entryDN),
                                  String.valueOf(v.getStringValue()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason));
                          invalidReason = new MessageBuilder(message);

                          localOp.setResultCode(
                              ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                          localOp.setErrorMessage(invalidReason);
                          break addProcessing;
                        }
                      }
                    }
                  }
                }

                for (List<Attribute> attrList :
                     operationalAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          Message message = WARN_ADD_OP_INVALID_SYNTAX.
                              get(String.valueOf(entryDN),
                                  String.valueOf(v.getStringValue()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason));
                          invalidReason = new MessageBuilder(message);

                          localOp.setResultCode(
                              ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                          localOp.setErrorMessage(invalidReason);
                          break addProcessing;
                        }
                      }
                    }
                  }
                }

                break;


              case WARN:
                invalidReason = new MessageBuilder();
                for (List<Attribute> attrList : userAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          logError(WARN_ADD_OP_INVALID_SYNTAX.
                              get(String.valueOf(entryDN),
                                  String.valueOf(v.getStringValue()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason)));
                        }
                      }
                    }
                  }
                }

                for (List<Attribute> attrList : operationalAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          logError(WARN_ADD_OP_INVALID_SYNTAX.
                              get(String.valueOf(entryDN),
                                  String.valueOf(v.getStringValue()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason)));
                        }
                      }
                    }
                  }
                }

                break;
            }
          }


          // See if the entry contains any attributes or object classes marked
          // OBSOLETE.  If so, then reject the entry.
          for (AttributeType at : userAttributes.keySet())
          {
            if (at.isObsolete())
            {
              Message message = WARN_ADD_ATTR_IS_OBSOLETE.get(
                      String.valueOf(entryDN),
                      at.getNameOrOID());
              localOp.appendErrorMessage(message);
              localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }

          for (AttributeType at : operationalAttributes.keySet())
          {
            if (at.isObsolete())
            {
              Message message = WARN_ADD_ATTR_IS_OBSOLETE.get(
                      String.valueOf(entryDN),
                      at.getNameOrOID());
              localOp.appendErrorMessage(message);
              localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }

          for (ObjectClass oc : objectClasses.keySet())
          {
            if (oc.isObsolete())
            {
              Message message = WARN_ADD_OC_IS_OBSOLETE.get(
                      String.valueOf(entryDN),
                      oc.getNameOrOID());
              localOp.appendErrorMessage(message);
              localOp.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }
        }

        // Check to see if there are any controls in the request. If so, then
        // see if there is any special processing required.
        boolean                    noOp            = false;
        LDAPPostReadRequestControl postReadRequest = null;
        List<Control> requestControls = localOp.getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (!AccessControlConfigManager.getInstance().
                    getAccessControlHandler().isAllowed(parentDN, localOp, c))
            {
              localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
              localOp.appendErrorMessage(
                      ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
              skipPostOperation = true;
              break addProcessing;
            }

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break addProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(entry))
                {
                  localOp.setResultCode(ResultCode.ASSERTION_FAILED);

                  localOp.appendErrorMessage(
                          ERR_ADD_ASSERTION_FAILED.get(
                                  String.valueOf(entryDN)));

                  break addProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

                localOp.appendErrorMessage(
                        ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));

                break addProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
            {
              if (c instanceof LDAPPostReadRequestControl)
              {
                postReadRequest = (LDAPPostReadRequestControl) c;
              }
              else
              {
                try
                {
                  postReadRequest = LDAPPostReadRequestControl.decodeControl(c);
                  requestControls.set(i, postReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break addProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break addProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break addProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break addProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break addProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break addProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break addProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
            {
              // We don't need to do anything here because it's already handled
              // in LocalBackendAddOperation.handlePasswordPolicy().
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                localOp.setResultCode(
                    ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                localOp.appendErrorMessage(
                        ERR_ADD_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN),
                                oid));

                break addProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the add.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists or
        // if the parent entry does not exist may have already exposed
        // sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(localOp) == false) {
          localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          localOp.appendErrorMessage(ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
                  .get(String.valueOf(entryDN)));

          skipPostOperation = true;
          break addProcessing;
        }

        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // If the operation is not a synchronization operation,
        // Invoke the pre-operation add plugins.
        if (!localOp.isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationAddPlugins(localOp);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            localOp.setResultCode(ResultCode.CANCELED);

            localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break addProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break addProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Actually perform the add operation.  This should also include taking
        // care of any synchronization that might be needed.
        Backend backend = DirectoryServer.getBackend(entryDN);
        if (backend == null)
        {
          localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
          localOp.appendErrorMessage(Message.raw("No backend for entry " +
              entryDN.toString())); // TODO: i18n
        }
        else
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! backend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
              case DISABLED:
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                        String.valueOf(entryDN)));
                break addProcessing;

              case INTERNAL_ONLY:
                if (! (localOp.isInternalOperation() ||
                    localOp.isSynchronizationOperation()))
                {
                  localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  localOp.appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                          String.valueOf(entryDN)));
                  break addProcessing;
                }
            }

            switch (backend.getWritabilityMode())
            {
              case DISABLED:
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                        String.valueOf(entryDN)));
                break addProcessing;

              case INTERNAL_ONLY:
                if (! (localOp.isInternalOperation() ||
                    localOp.isSynchronizationOperation()))
                {
                  localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  localOp.appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                          String.valueOf(entryDN)));
                  break addProcessing;
                }
            }
          }


          try
          {
            if (noOp)
            {
              localOp.appendErrorMessage(INFO_ADD_NOOP.get());

              localOp.setResultCode(ResultCode.NO_OPERATION);
            }
            else
            {
              for (SynchronizationProvider provider :
                   DirectoryServer.getSynchronizationProviders())
              {
                try
                {
                  SynchronizationProviderResult result =
                       provider.doPreOperation(localOp);
                  if (! result.continueOperationProcessing())
                  {
                    break addProcessing;
                  }
                }
                catch (DirectoryException de)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                  }

                  logError(ERR_ADD_SYNCH_PREOP_FAILED.
                      get(localOp.getConnectionID(), localOp.getOperationID(),
                          getExceptionMessage(de)));

                  localOp.setResponseData(de);
                  break addProcessing;
                }
              }

              backend.addEntry(entry, localOp);
            }

            if (postReadRequest != null)
            {
              Entry addedEntry = entry.duplicate(true);

              if (! postReadRequest.allowsAttribute(
                         DirectoryServer.getObjectClassAttributeType()))
              {
                addedEntry.removeAttribute(
                     DirectoryServer.getObjectClassAttributeType());
              }

              if (! postReadRequest.returnAllUserAttributes())
              {
                Iterator<AttributeType> iterator =
                     addedEntry.getUserAttributes().keySet().iterator();
                while (iterator.hasNext())
                {
                  AttributeType attrType = iterator.next();
                  if (! postReadRequest.allowsAttribute(attrType))
                  {
                    iterator.remove();
                  }
                }
              }

              if (! postReadRequest.returnAllOperationalAttributes())
              {
                Iterator<AttributeType> iterator =
                     addedEntry.getOperationalAttributes().keySet().iterator();
                while (iterator.hasNext())
                {
                  AttributeType attrType = iterator.next();
                  if (! postReadRequest.allowsAttribute(attrType))
                  {
                    iterator.remove();
                  }
                }
              }

              // FIXME -- Check access controls on the entry to see if it should
              //          be returned or if any attributes need to be stripped
              //          out..
              SearchResultEntry searchEntry = new SearchResultEntry(addedEntry);
              LDAPPostReadResponseControl responseControl =
                   new LDAPPostReadResponseControl(postReadRequest.getOID(),
                                                   postReadRequest.isCritical(),
                                                   searchEntry);

              localOp.addResponseControl(responseControl);
            }


            if (! noOp)
            {
              localOp.setResultCode(ResultCode.SUCCESS);
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            localOp.setResultCode(de.getResultCode());
            localOp.appendErrorMessage(de.getMessageObject());
            localOp.setMatchedDN(de.getMatchedDN());
            localOp.setReferralURLs(de.getReferralURLs());

            break addProcessing;
          }
          catch (CancelledOperationException coe)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, coe);
            }

            CancelResult cancelResult = coe.getCancelResult();

            localOp.setCancelResult(cancelResult);
            localOp.setResultCode(cancelResult.getResultCode());

            Message message = coe.getMessageObject();
            if ((message != null) && (message.length() > 0))
            {
              localOp.appendErrorMessage(message);
            }

            break addProcessing;
          }
        }
      }
      finally
      {
        if (entryLock != null)
        {
          LockManager.unlock(entryDN, entryLock);
        }

        if (parentLock != null)
        {
          LockManager.unlock(parentDN, parentLock);
        }


        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(localOp);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_ADD_SYNCH_POSTOP_FAILED.
                get(localOp.getConnectionID(), localOp.getOperationID(),
                    getExceptionMessage(de)));

            localOp.setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    localOp.setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation add plugins.
    if (! skipPostOperation)
    {
      // FIXME -- Should this also be done while holding the locks?
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationAddPlugins(localOp);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if ((localOp.getResultCode() == ResultCode.SUCCESS) &&
        (localOp.getEntryToAdd() != null))
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleAddOperation(localOp, localOp.getEntryToAdd());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_ADD_ERROR_NOTIFYING_CHANGE_LISTENER.get(
              getExceptionMessage(e));
          logError(message);
        }
      }
    }
  }


  /**
   * Performs a delete operation against a local backend.
   *
   * @param operation the operation to perform
   */
  public void processDelete(DeleteOperation operation){
    LocalBackendDeleteOperation localOperation =
      new LocalBackendDeleteOperation(operation);
    processLocalDelete(localOperation);
  }

  /**
   * Performs a local delete operation against a local backend.
   *
   * @param localOp the operation to perform
   */
  private void processLocalDelete(LocalBackendDeleteOperation localOp)
  {
    ClientConnection clientConnection = localOp.getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    // Check for a request to cancel this operation.
    if (localOp.getCancelRequest() != null)
    {
      return;
    }

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
deleteProcessing:
    {
      // Process the entry DN to convert it from its raw form as provided by the
      // client to the form required for the rest of the delete processing.
      DN entryDN = localOp.getEntryDN();
      if (entryDN == null){
        break deleteProcessing;
      }

      // Grab a write lock on the entry.
      Lock entryLock = null;
      for (int i=0; i < 3; i++)
      {
        entryLock = LockManager.lockWrite(entryDN);
        if (entryLock != null)
        {
          break;
        }
      }

      if (entryLock == null)
      {
        localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
        localOp.appendErrorMessage(ERR_DELETE_CANNOT_LOCK_ENTRY.get(
                String.valueOf(entryDN)));
        break deleteProcessing;
      }

      Entry entry = null;
      try
      {
        // Get the entry to delete.  If it doesn't exist, then fail.
        try
        {
          entry = backend.getEntry(entryDN);
          localOp.setEntryToDelete(entry);
          if (entry == null)
          {
            localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
            localOp.appendErrorMessage(ERR_DELETE_NO_SUCH_ENTRY.get(
                    String.valueOf(entryDN)));

            try
            {
              DN parentDN = entryDN.getParentDNInSuffix();
              while (parentDN != null)
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  localOp.setMatchedDN(parentDN);
                  break;
                }

                parentDN = parentDN.getParentDNInSuffix();
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
            }

            break deleteProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          localOp.setMatchedDN(de.getMatchedDN());
          localOp.setReferralURLs(de.getReferralURLs());
          break deleteProcessing;
        }


        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                 provider.handleConflictResolution(localOp);
            if (! result.continueOperationProcessing())
            {
              break deleteProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED.
                get(localOp.getConnectionID(), localOp.getOperationID(),
                    getExceptionMessage(de)));

            localOp.setResponseData(de);
            break deleteProcessing;
          }
        }

        // Check to see if the client has permission to perform the
        // delete.

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                   noOp           = false;
        LDAPPreReadRequestControl preReadRequest = null;
        List<Control> requestControls =
          localOp.getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (!AccessControlConfigManager.getInstance().
                     getAccessControlHandler().isAllowed(entryDN, localOp, c))
            {
              localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

              localOp.appendErrorMessage(
                      ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
              skipPostOperation = true;
              break deleteProcessing;
            }

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break deleteProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(entry))
                {
                  localOp.setResultCode(ResultCode.ASSERTION_FAILED);

                  localOp.appendErrorMessage(
                          ERR_DELETE_ASSERTION_FAILED.get(
                                  String.valueOf(entryDN)));

                  break deleteProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

                localOp.appendErrorMessage(
                        ERR_DELETE_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));

                break deleteProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPPreReadRequestControl)
              {
                preReadRequest = (LDAPPreReadRequestControl) c;
              }
              else
              {
                try
                {
                  preReadRequest = LDAPPreReadRequestControl.decodeControl(c);
                  requestControls.set(i, preReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break deleteProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {

                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break deleteProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break deleteProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break deleteProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
                  localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break deleteProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break deleteProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break deleteProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                localOp.setResultCode(
                    ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                localOp.appendErrorMessage(
                        ERR_DELETE_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN),
                                oid));

                break deleteProcessing;
              }
            }
          }
        }


        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(localOp) == false) {
          localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          localOp.appendErrorMessage(
                  ERR_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                          String.valueOf(entryDN)));

          skipPostOperation = true;
          break deleteProcessing;
        }

        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // If the operation is not a synchronization operation,
        // invoke the pre-delete plugins.
        if (!localOp.isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationDeletePlugins(localOp);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the request
            // and result and return.
            localOp.setResultCode(ResultCode.CANCELED);

            localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

            localOp.setProcessingStopTime();
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break deleteProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break deleteProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Get the backend to use for the delete.  If there is none, then fail.
        if (backend == null)
        {
          localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
          localOp.appendErrorMessage(
                  ERR_DELETE_NO_SUCH_ENTRY.get(String.valueOf(entryDN)));
          break deleteProcessing;
        }


        // If it is not a private backend, then check to see if the server or
        // backend is operating in read-only mode.
        if (! backend.isPrivateBackend())
        {
          switch (DirectoryServer.getWritabilityMode())
          {
            case DISABLED:
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(
                      ERR_DELETE_SERVER_READONLY.get(String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (localOp.isInternalOperation() ||
                  localOp.isSynchronizationOperation()))
              {
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(
                        ERR_DELETE_SERVER_READONLY.get(
                                String.valueOf(entryDN)));
                break deleteProcessing;
              }
          }

          switch (backend.getWritabilityMode())
          {
            case DISABLED:
              localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              localOp.appendErrorMessage(
                      ERR_DELETE_BACKEND_READONLY.get(String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (localOp.isInternalOperation() ||
                  localOp.isSynchronizationOperation()))
              {
                localOp.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                localOp.appendErrorMessage(
                        ERR_DELETE_BACKEND_READONLY.get(
                                String.valueOf(entryDN)));
                break deleteProcessing;
              }
          }
        }


        // The selected backend will have the responsibility of making sure that
        // the entry actually exists and does not have any children (or possibly
        // handling a subtree delete).  But we will need to check if there are
        // any subordinate backends that should stop us from attempting the
        // delete.
        Backend[] subBackends = backend.getSubordinateBackends();
        for (Backend b : subBackends)
        {
          DN[] baseDNs = b.getBaseDNs();
          for (DN dn : baseDNs)
          {
            if (dn.isDescendantOf(entryDN))
            {
              localOp.setResultCode(ResultCode.NOT_ALLOWED_ON_NONLEAF);
              localOp.appendErrorMessage(ERR_DELETE_HAS_SUB_BACKEND.get(
                      String.valueOf(entryDN),
                      String.valueOf(dn)));
              break deleteProcessing;
            }
          }
        }


        // Actually perform the delete.
        try
        {
          if (noOp)
          {
            localOp.appendErrorMessage(INFO_DELETE_NOOP.get());

            localOp.setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
            for (SynchronizationProvider provider :
                 DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(localOp);
                if (! result.continueOperationProcessing())
                {
                  break deleteProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_DELETE_SYNCH_PREOP_FAILED.
                    get(localOp.getConnectionID(), localOp.getOperationID(),
                        getExceptionMessage(de)));

                localOp.setResponseData(de);
                break deleteProcessing;
              }
            }

            backend.deleteEntry(entryDN, localOp);
          }

          if (preReadRequest != null)
          {
            Entry entryCopy = entry.duplicate(true);

            if (! preReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entryCopy.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! preReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entryCopy.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! preReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entryCopy.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entryCopy);
            LDAPPreReadResponseControl responseControl =
                 new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                                preReadRequest.isCritical(),
                                                searchEntry);

            localOp.addResponseControl(responseControl);
          }


          if (! noOp)
          {
            localOp.setResultCode(ResultCode.SUCCESS);
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          localOp.setMatchedDN(de.getMatchedDN());
          localOp.setReferralURLs(de.getReferralURLs());

          break deleteProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          localOp.setCancelResult(cancelResult);
          localOp.setResultCode(cancelResult.getResultCode());

          Message message = coe.getMessageObject();
          if ((message != null) && (message.length() > 0))
          {
            localOp.appendErrorMessage(message);
          }

          break deleteProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, entryLock);

        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(localOp);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_DELETE_SYNCH_POSTOP_FAILED.
                get(localOp.getConnectionID(), localOp.getOperationID(),
                    getExceptionMessage(de)));

            localOp.setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    localOp.setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation delete plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationDeletePlugins(localOp);
      if (postOperationResult.connectionTerminated())
      {
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());

        localOp.setProcessingStopTime();
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (localOp.getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleDeleteOperation(localOp,
              localOp.getEntryToDelete());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER.get(
              getExceptionMessage(e));
          logError(message);
        }
      }
    }

    // Stop the processing timer.
    localOp.setProcessingStopTime();
  }



  /**
   * Perform a compare operation against a local backend.
   *
   * @param operation - The operation to perform
   */
  public void processCompare(CompareOperation operation)
  {
    LocalBackendCompareOperation localOperation =
      new LocalBackendCompareOperation(operation);
    processLocalCompare(localOperation);
  }


  /**
   * Perform a local compare operation against a local backend.
   *
   * @param localOp - The operation to perform
   */
  private void processLocalCompare(LocalBackendCompareOperation localOp)
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Get a reference to the client connection
    ClientConnection clientConnection = localOp.getClientConnection();


    // Check for a request to cancel this operation.
    if (localOp.getCancelRequest() != null)
    {
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
compareProcessing:
    {
      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the compare processing.
      DN entryDN = localOp.getEntryDN();
      if (entryDN == null)
      {
        skipPostOperation = true;
        break compareProcessing;
      }


      // If the target entry is in the server configuration, then make sure the
      // requester has the CONFIG_READ privilege.
      if (DirectoryServer.getConfigHandler().handlesEntry(entryDN) &&
          (! clientConnection.hasPrivilege(Privilege.CONFIG_READ, localOp)))
      {
        localOp.appendErrorMessage(
                ERR_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES.get());
        localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        skipPostOperation = true;
        break compareProcessing;
      }


      // Check for a request to cancel this operation.
      if (localOp.getCancelRequest() != null)
      {
        return;
      }


      // Grab a read lock on the entry.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(entryDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        Message message = ERR_COMPARE_CANNOT_LOCK_ENTRY.get(
                String.valueOf(entryDN));

        localOp.setResultCode(DirectoryServer.getServerErrorResultCode());
        localOp.appendErrorMessage(message);
        skipPostOperation = true;
        break compareProcessing;
      }

      Entry entry = null;
      try
      {
        // Get the entry.  If it does not exist, then fail.
        try
        {
          entry = DirectoryServer.getEntry(entryDN);
          localOp.setEntryToCompare(entry);

          if (entry == null)
          {
            localOp.setResultCode(ResultCode.NO_SUCH_OBJECT);
            localOp.appendErrorMessage(
                    ERR_COMPARE_NO_SUCH_ENTRY.get(String.valueOf(entryDN)));

            // See if one of the entry's ancestors exists.
            DN parentDN = entryDN.getParentDNInSuffix();
            while (parentDN != null)
            {
              try
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  localOp.setMatchedDN(parentDN);
                  break;
                }
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }
                break;
              }

              parentDN = parentDN.getParentDNInSuffix();
            }

            break compareProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          localOp.setResultCode(de.getResultCode());
          localOp.appendErrorMessage(de.getMessageObject());
          break compareProcessing;
        }

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        List<Control> requestControls = localOp.getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (!AccessControlConfigManager.getInstance().
                    getAccessControlHandler().
                    isAllowed(entryDN, localOp, c))
            {
              localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

              localOp.appendErrorMessage(
                      ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
              skipPostOperation = true;
              break compareProcessing;
            }

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break compareProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(entry))
                {
                  localOp.setResultCode(ResultCode.ASSERTION_FAILED);

                  localOp.appendErrorMessage(
                      ERR_COMPARE_ASSERTION_FAILED.get(
                              String.valueOf(entryDN)));

                  break compareProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(ResultCode.PROTOCOL_ERROR);

                localOp.appendErrorMessage(
                    ERR_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER.get(
                            String.valueOf(entryDN),
                            de.getMessageObject()));

                break compareProcessing;
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(
                       Privilege.PROXIED_AUTH, localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break compareProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break compareProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break compareProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(
                       Privilege.PROXIED_AUTH, localOp))
              {
                localOp.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                localOp.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break compareProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  localOp.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  localOp.appendErrorMessage(le.getMessageObject());

                  break compareProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                localOp.setResultCode(de.getResultCode());
                localOp.appendErrorMessage(de.getMessageObject());

                break compareProcessing;
              }

              localOp.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                localOp.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                localOp.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                localOp.setResultCode(
                    ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                localOp.appendErrorMessage(
                    ERR_COMPARE_UNSUPPORTED_CRITICAL_CONTROL.get(
                            String.valueOf(entryDN), oid));

                break compareProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the
        // compare.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(localOp) == false) {
          localOp.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          localOp.appendErrorMessage(
              ERR_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                      String.valueOf(entryDN)));

          skipPostOperation = true;
          break compareProcessing;
        }

        // Check for a request to cancel this operation.
        if (localOp.getCancelRequest() != null)
        {
          return;
        }


        // Invoke the pre-operation compare plugins.
        PreOperationPluginResult preOpResult =
             pluginConfigManager.invokePreOperationComparePlugins(localOp);
        if (preOpResult.connectionTerminated())
        {
          // There's no point in continuing with anything.  Log the request and
          // result and return.
          localOp.setResultCode(ResultCode.CANCELED);

          localOp.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());

          return;
        }
        else if (preOpResult.sendResponseImmediately())
        {
          skipPostOperation = true;
          break compareProcessing;
        }
        else if (preOpResult.skipCoreProcessing())
        {
          skipPostOperation = false;
          break compareProcessing;
        }


        // Get the base attribute type and set of options.
        String          baseName;
        HashSet<String> options;
        String rawAttributeType = localOp.getRawAttributeType();
        int             semicolonPos = rawAttributeType.indexOf(';');
        if (semicolonPos > 0)
        {
          baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

          options = new HashSet<String>();
          int nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          while (nextPos > 0)
          {
            options.add(rawAttributeType.substring(semicolonPos+1, nextPos));
            semicolonPos = nextPos;
            nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          }

          options.add(rawAttributeType.substring(semicolonPos+1));
        }
        else
        {
          baseName = toLowerCase(rawAttributeType);
          options  = null;
        }


        // Actually perform the compare operation.
        List<Attribute> attrList = null;
        if (localOp.getAttributeType() == null)
        {
          localOp.setAttributeType(DirectoryServer.getAttributeType(baseName));
        }
        if (localOp.getAttributeType() == null)
        {
          attrList = entry.getAttribute(baseName, options);
          localOp.setAttributeType(
              DirectoryServer.getDefaultAttributeType(baseName));
        }
        else
        {
          attrList = entry.getAttribute(localOp.getAttributeType(), options);
        }

        if ((attrList == null) || attrList.isEmpty())
        {
          localOp.setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
          if (options == null)
          {
            localOp.appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR.get(
                    String.valueOf(entryDN), baseName));
          }
          else
          {
            localOp.appendErrorMessage(
                    WARN_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS.get(
                            String.valueOf(entryDN), baseName));
          }
        }
        else
        {
          AttributeValue value = new AttributeValue(
              localOp.getAttributeType(),
              localOp.getAssertionValue());

          boolean matchFound = false;
          for (Attribute a : attrList)
          {
            if (a.hasValue(value))
            {
              matchFound = true;
              break;
            }
          }

          if (matchFound)
          {
            localOp.setResultCode(ResultCode.COMPARE_TRUE);
          }
          else
          {
            localOp.setResultCode(ResultCode.COMPARE_FALSE);
          }
        }
      }
      finally
      {
        LockManager.unlock(entryDN, readLock);
      }
    }


    // Check for a request to cancel this operation.
    if (localOp.getCancelRequest() != null)
    {
      return;
    }


    // Invoke the post-operation compare plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationComparePlugins(localOp);
      if (postOperationResult.connectionTerminated())
      {
        localOp.setResultCode(ResultCode.CANCELED);

        localOp.appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());

        return;
      }
    }
  }

  /**
   * Perform a moddn operation against a local backend.
   *
   * @param op The operation to perform
   */
  public void processModifyDN(ModifyDNOperation op)
  {
    LocalBackendModifyDNOperation localOp =
      new LocalBackendModifyDNOperation(op);
    processLocalModifyDN(localOp);
  }

  /**
   * Perform a local moddn operation against the local backend.
   *
   * @param op - The operation to perform
   */
  private void processLocalModifyDN(LocalBackendModifyDNOperation op)
  {

    ClientConnection clientConnection = op.getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;

    // Check for a request to cancel this operation.
    if (op.getCancelRequest() != null)
    {
      return;
    }

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyDNProcessing:
    {
      // Process the entry DN, newRDN, and newSuperior elements from their raw
      // forms as provided by the client to the forms required for the rest of
      // the modify DN processing.
      DN entryDN = op.getEntryDN();

      RDN newRDN = op.getNewRDN();
      if (newRDN == null)
      {
        skipPostOperation = true;
        break modifyDNProcessing;
      }

      DN newSuperior = op.getNewSuperior();
      if ((newSuperior == null) &&
          (op.getRawNewSuperior() != null))
      {
        skipPostOperation = true;
        break modifyDNProcessing;
      }

      // Construct the new DN to use for the entry.
      DN parentDN;
      if (newSuperior == null)
      {
        parentDN = entryDN.getParentDNInSuffix();
      }
      else
      {
        parentDN = newSuperior;
      }

      if ((parentDN == null) || parentDN.isNullDN())
      {
        op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        op.appendErrorMessage(ERR_MODDN_NO_PARENT.get(String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      DN newDN = parentDN.concat(newRDN);

      // Get the backend for the current entry, and the backend for the new
      // entry.  If either is null, or if they are different, then fail.
      Backend currentBackend = backend;
      if (currentBackend == null)
      {
        op.setResultCode(ResultCode.NO_SUCH_OBJECT);
        op.appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_CURRENT_ENTRY.get(
                String.valueOf(entryDN)));
        break modifyDNProcessing;
      }

      Backend newBackend = DirectoryServer.getBackend(newDN);
      if (newBackend == null)
      {
        op.setResultCode(ResultCode.NO_SUCH_OBJECT);
        op.appendErrorMessage(ERR_MODDN_NO_BACKEND_FOR_NEW_ENTRY.get(
                String.valueOf(entryDN),
                                      String.valueOf(newDN)));
        break modifyDNProcessing;
      }
      else if (! currentBackend.equals(newBackend))
      {
        op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        op.appendErrorMessage(ERR_MODDN_DIFFERENT_BACKENDS.get(
                String.valueOf(entryDN),
                String.valueOf(newDN)));
        break modifyDNProcessing;
      }


      // Check for a request to cancel this operation.
      if (op.getCancelRequest() != null)
      {
        return;
      }


      // Acquire write locks for the current and new DN.
      Lock currentLock = null;
      for (int i=0; i < 3; i++)
      {
        currentLock = LockManager.lockWrite(entryDN);
        if (currentLock != null)
        {
          break;
        }
      }

      if (currentLock == null)
      {
        op.setResultCode(DirectoryServer.getServerErrorResultCode());
        op.appendErrorMessage(ERR_MODDN_CANNOT_LOCK_CURRENT_DN.get(
                String.valueOf(entryDN)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }

      Lock newLock = null;
      try
      {
        for (int i=0; i < 3; i++)
        {
          newLock = LockManager.lockWrite(newDN);
          if (newLock != null)
          {
            break;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        LockManager.unlock(entryDN, currentLock);

        if (newLock != null)
        {
          LockManager.unlock(newDN, newLock);
        }

        op.setResultCode(DirectoryServer.getServerErrorResultCode());
        op.appendErrorMessage(ERR_MODDN_EXCEPTION_LOCKING_NEW_DN.get(
                String.valueOf(entryDN),
                String.valueOf(newDN),
                getExceptionMessage(e)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }

      if (newLock == null)
      {
        LockManager.unlock(entryDN, currentLock);

        op.setResultCode(DirectoryServer.getServerErrorResultCode());
        op.appendErrorMessage(ERR_MODDN_CANNOT_LOCK_NEW_DN.get(
                String.valueOf(entryDN),
                String.valueOf(newDN)));

        skipPostOperation = true;
        break modifyDNProcessing;
      }

      Entry currentEntry = null;
      try
      {
        // Check for a request to cancel this operation.
        if (op.getCancelRequest() != null)
        {
          return;
        }


        // Get the current entry from the appropriate backend.  If it doesn't
        // exist, then fail.
        try
        {
          currentEntry = currentBackend.getEntry(entryDN);
          op.setOriginalEntry(currentEntry);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          op.setResultCode(de.getResultCode());
          op.appendErrorMessage(de.getMessageObject());
          op.setMatchedDN(de.getMatchedDN());
          op.setReferralURLs(de.getReferralURLs());

          break modifyDNProcessing;
        }

        if (op.getOriginalEntry() == null)
        {
          // See if one of the entry's ancestors exists.
          parentDN = entryDN.getParentDNInSuffix();
          while (parentDN != null)
          {
            try
            {
              if (DirectoryServer.entryExists(parentDN))
              {
                op.setMatchedDN(parentDN);
                break;
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              break;
            }

            parentDN = parentDN.getParentDNInSuffix();
          }

          op.setResultCode(ResultCode.NO_SUCH_OBJECT);
          op.appendErrorMessage(ERR_MODDN_NO_CURRENT_ENTRY.get(
                  String.valueOf(entryDN)));

          break modifyDNProcessing;
        }


        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                 provider.handleConflictResolution(op);
            if (! result.continueOperationProcessing())
            {
              break modifyDNProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_MODDN_SYNCH_CONFLICT_RESOLUTION_FAILED.
                get(op.getConnectionID(), op.getOperationID(),
                    getExceptionMessage(de)));

            op.setResponseData(de);
            break modifyDNProcessing;
          }
        }


        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                    noOp            = false;
        LDAPPreReadRequestControl  preReadRequest  = null;
        LDAPPostReadRequestControl postReadRequest = null;
        List<Control> requestControls = op.getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (!AccessControlConfigManager.getInstance().
                     getAccessControlHandler().isAllowed(entryDN,  op, c))
            {
              op.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

              op.appendErrorMessage(
                      ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
              skipPostOperation = true;
              break modifyDNProcessing;
            }

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  op.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  op.appendErrorMessage(le.getMessageObject());

                  break modifyDNProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(currentEntry))
                {
                  op.setResultCode(ResultCode.ASSERTION_FAILED);

                  op.appendErrorMessage(ERR_MODDN_ASSERTION_FAILED.get(
                          String.valueOf(entryDN)));

                  break modifyDNProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                op.setResultCode(ResultCode.PROTOCOL_ERROR);

                op.appendErrorMessage(
                        ERR_MODDN_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));

                break modifyDNProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPPreReadRequestControl)
              {
                preReadRequest = (LDAPPreReadRequestControl) c;
              }
              else
              {
                try
                {
                  preReadRequest = LDAPPreReadRequestControl.decodeControl(c);
                  requestControls.set(i, preReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  op.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  op.appendErrorMessage(le.getMessageObject());

                  break modifyDNProcessing;
                }
              }
            }
            else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
            {
              if (c instanceof LDAPPostReadRequestControl)
              {
                postReadRequest = (LDAPPostReadRequestControl) c;
              }
              else
              {
                try
                {
                  postReadRequest = LDAPPostReadRequestControl.decodeControl(c);
                  requestControls.set(i, postReadRequest);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  op.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  op.appendErrorMessage(le.getMessageObject());

                  break modifyDNProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, op))
              {

                op.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                op.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyDNProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  op.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  op.appendErrorMessage(le.getMessageObject());

                  break modifyDNProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                op.setResultCode(de.getResultCode());
                op.appendErrorMessage(de.getMessageObject());

                break modifyDNProcessing;
              }

              op.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                op.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                op.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, op))
              {

                op.appendErrorMessage(
                        ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
                op.setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break modifyDNProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  op.setResultCode(ResultCode.valueOf(le.getResultCode()));
                  op.appendErrorMessage(le.getMessageObject());

                  break modifyDNProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                op.setResultCode(de.getResultCode());
                op.appendErrorMessage(de.getMessageObject());

                break modifyDNProcessing;
              }

              op.setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                op.setProxiedAuthorizationDN(DN.nullDN());
              }
              else
              {
                op.setProxiedAuthorizationDN(authorizationEntry.getDN());
              }
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                op.setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);


                op.appendErrorMessage(
                        ERR_MODDN_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN),
                                oid));

                break modifyDNProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the
        // modify DN.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry or new superior
        // already exists may have already exposed sensitive information
        // to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(op) == false) {
          op.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          op.appendErrorMessage(ERR_MODDN_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                  String.valueOf(entryDN)));

          skipPostOperation = true;
          break modifyDNProcessing;
        }

        // Duplicate the entry and set its new DN.  Also, create an empty list
        // to hold the attribute-level modifications.
        Entry newEntry = currentEntry.duplicate(false);
        newEntry.setDN(newDN);
        op.setUpdatedEntry(newEntry);

        // init the modifications
        op.addModification(null);
        List<Modification> modifications = op.getModifications();


        // If we should delete the old RDN values from the entry, then do so.
        if (op.deleteOldRDN())
        {
          RDN currentRDN = entryDN.getRDN();
          int numValues  = currentRDN.getNumValues();
          for (int i=0; i < numValues; i++)
          {
            LinkedHashSet<AttributeValue> valueSet =
                 new LinkedHashSet<AttributeValue>(1);
            valueSet.add(currentRDN.getAttributeValue(i));

            Attribute a = new Attribute(currentRDN.getAttributeType(i),
                                        currentRDN.getAttributeName(i),
                                        valueSet);

            // If the associated attribute type is marked NO-USER-MODIFICATION,
            // then refuse the update.
            if (a.getAttributeType().isNoUserModification())
            {
              if (! (op.isInternalOperation() ||
                  op.isSynchronizationOperation()))
              {
                op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                op.appendErrorMessage(ERR_MODDN_OLD_RDN_ATTR_IS_NO_USER_MOD.get(
                        String.valueOf(entryDN), a.getName()));
                break modifyDNProcessing;
              }
            }

            LinkedList<AttributeValue> missingValues =
                 new LinkedList<AttributeValue>();
            newEntry.removeAttribute(a, missingValues);

            if (missingValues.isEmpty())
            {
              modifications.add(new Modification(ModificationType.DELETE, a));
            }
          }
        }


        // Add the new RDN values to the entry.
        int newRDNValues = newRDN.getNumValues();
        for (int i=0; i < newRDNValues; i++)
        {
          LinkedHashSet<AttributeValue> valueSet =
               new LinkedHashSet<AttributeValue>(1);
          valueSet.add(newRDN.getAttributeValue(i));

          Attribute a = new Attribute(newRDN.getAttributeType(i),
                                      newRDN.getAttributeName(i),
                                      valueSet);

          LinkedList<AttributeValue> duplicateValues =
               new LinkedList<AttributeValue>();
          newEntry.addAttribute(a, duplicateValues);

          if (duplicateValues.isEmpty())
          {
            // If the associated attribute type is marked NO-USER-MODIFICATION,
            // then refuse the update.
            if (a.getAttributeType().isNoUserModification())
            {
              if (! (op.isInternalOperation() ||
                  op.isSynchronizationOperation()))
              {
                op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

                op.appendErrorMessage(ERR_MODDN_NEW_RDN_ATTR_IS_NO_USER_MOD.get(
                        String.valueOf(entryDN), a.getName()));
                break modifyDNProcessing;
              }
            }
            else
            {
              modifications.add(new Modification(ModificationType.ADD, a));
            }
          }
        }

        // If the server is configured to check the schema and the
        // operation is not a synchronization operation,
        // make sure that the resulting entry is valid as per the server schema.
        if ((DirectoryServer.checkSchema()) &&
            (!op.isSynchronizationOperation()) )
        {
          MessageBuilder invalidReason = new MessageBuilder();
          if (! newEntry.conformsToSchema(null, false, true, true,
                                          invalidReason))
          {
            op.setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            op.appendErrorMessage(ERR_MODDN_VIOLATES_SCHEMA.get(
                    String.valueOf(entryDN),
                    String.valueOf(invalidReason)));
            break modifyDNProcessing;
          }

          for (int i=0; i < newRDNValues; i++)
          {
            AttributeType at = newRDN.getAttributeType(i);
            if (at.isObsolete())
            {
              op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              op.appendErrorMessage(ERR_MODDN_NEWRDN_ATTR_IS_OBSOLETE.get(
                      String.valueOf(entryDN),
                      at.getNameOrOID()));
              break modifyDNProcessing;
            }
          }
        }


        // Check for a request to cancel this operation.
        if (op.getCancelRequest() != null)
        {
          return;
        }


        // Get a count of the current number of modifications.  The
        // pre-operation plugins may alter this list, and we need to be able to
        // identify which changes were made after they're done.
        int modCount = op.getModifications().size();


        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify DN plugins.
        if (!op.isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationModifyDNPlugins(op);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the request
            // and result and return.
            op.setResultCode(ResultCode.CANCELED);


            op.appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break modifyDNProcessing;
          }
          else if (preOpResult.skipCoreProcessing())
          {
            skipPostOperation = false;
            break modifyDNProcessing;
          }
        }

        // Check to see if any of the pre-operation plugins made any changes to
        // the entry.  If so, then apply them.
        if (modifications.size() > modCount)
        {
          for (int i=modCount; i < modifications.size(); i++)
          {
            Modification m = modifications.get(i);
            Attribute    a = m.getAttribute();

            switch (m.getModificationType())
            {
              case ADD:
                LinkedList<AttributeValue> duplicateValues =
                     new LinkedList<AttributeValue>();
                newEntry.addAttribute(a, duplicateValues);
                break;
              case DELETE:
                LinkedList<AttributeValue> missingValues =
                     new LinkedList<AttributeValue>();
                newEntry.removeAttribute(a, missingValues);
                break;
              case REPLACE:
                duplicateValues = new LinkedList<AttributeValue>();
                newEntry.removeAttribute(a.getAttributeType(), a.getOptions());
                newEntry.addAttribute(a, duplicateValues);
                break;
              case INCREMENT:
                List<Attribute> attrList =
                     newEntry.getAttribute(a.getAttributeType(),
                                           a.getOptions());
                if ((attrList == null) || attrList.isEmpty())
                {
                  op.setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                  op.appendErrorMessage(ERR_MODDN_PREOP_INCREMENT_NO_ATTR.get(
                          String.valueOf(entryDN), a.getName()));

                  break modifyDNProcessing;
                }
                else if (attrList.size() > 1)
                {
                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }

                LinkedHashSet<AttributeValue> values =
                     attrList.get(0).getValues();
                if ((values == null) || values.isEmpty())
                {
                  op.setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                  op.appendErrorMessage(ERR_MODDN_PREOP_INCREMENT_NO_ATTR.get(
                          String.valueOf(entryDN),
                          a.getName()));

                  break modifyDNProcessing;
                }
                else if (values.size() > 1)
                {
                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_MULTIPLE_VALUES.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }

                long currentLongValue;
                try
                {
                  AttributeValue v = values.iterator().next();
                  currentLongValue = Long.parseLong(v.getStringValue());
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_VALUE_NOT_INTEGER.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }

                LinkedHashSet<AttributeValue> newValues = a.getValues();
                if ((newValues == null) || newValues.isEmpty())
                {
                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_NO_AMOUNT.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }
                else if (newValues.size() > 1)
                {
                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_MULTIPLE_AMOUNTS.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }

                long incrementAmount;
                try
                {
                  AttributeValue v = values.iterator().next();
                  incrementAmount = Long.parseLong(v.getStringValue());
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }

                  op.setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  op.appendErrorMessage(
                          ERR_MODDN_PREOP_INCREMENT_AMOUNT_NOT_INTEGER.get(
                                  String.valueOf(entryDN),
                                  a.getName()));

                  break modifyDNProcessing;
                }

                long newLongValue = currentLongValue + incrementAmount;
                ByteString newValueOS =
                     new ASN1OctetString(String.valueOf(newLongValue));

                newValues = new LinkedHashSet<AttributeValue>(1);
                newValues.add(new AttributeValue(a.getAttributeType(),
                                                 newValueOS));

                List<Attribute> newAttrList = new ArrayList<Attribute>(1);
                newAttrList.add(new Attribute(a.getAttributeType(),
                                              a.getName(), newValues));
                newEntry.putAttribute(a.getAttributeType(), newAttrList);

                break;
            }
          }


          // Make sure that the updated entry still conforms to the server
          // schema.
          if (DirectoryServer.checkSchema())
          {
            MessageBuilder invalidReason = new MessageBuilder();
            if (! newEntry.conformsToSchema(null, false, true, true,
                                            invalidReason))
            {
              op.setResultCode(ResultCode.OBJECTCLASS_VIOLATION);

              op.appendErrorMessage(ERR_MODDN_PREOP_VIOLATES_SCHEMA.get(
                      String.valueOf(entryDN),
                      String.valueOf(invalidReason)));
              break modifyDNProcessing;
            }
          }
        }


        // Check for a request to cancel this operation.
        if (op.getCancelRequest() != null)
        {
          return;
        }


        // Actually perform the modify DN operation.
        // This should include taking
        // care of any synchronization that might be needed.
        try
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! currentBackend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
              case DISABLED:
                op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                op.appendErrorMessage(ERR_MODDN_SERVER_READONLY.get(
                        String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (op.isInternalOperation() ||
                    op.isSynchronizationOperation()))
                {
                  op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  op.appendErrorMessage(ERR_MODDN_SERVER_READONLY.get(
                          String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }

            switch (currentBackend.getWritabilityMode())
            {
              case DISABLED:
                op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                op.appendErrorMessage(ERR_MODDN_BACKEND_READONLY.get(
                        String.valueOf(entryDN)));
                break modifyDNProcessing;

              case INTERNAL_ONLY:
                if (! (op.isInternalOperation() ||
                    op.isSynchronizationOperation()))
                {
                  op.setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  op.appendErrorMessage(ERR_MODDN_BACKEND_READONLY.get(
                          String.valueOf(entryDN)));
                  break modifyDNProcessing;
                }
            }
          }


          if (noOp)
          {
            op.appendErrorMessage(INFO_MODDN_NOOP.get());

            op.setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
            for (SynchronizationProvider provider :
                 DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(op);
                if (! result.continueOperationProcessing())
                {
                  break modifyDNProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_MODDN_SYNCH_PREOP_FAILED.
                    get(op.getConnectionID(), op.getOperationID(),
                        getExceptionMessage(de)));

                op.setResponseData(de);
                break modifyDNProcessing;
              }
            }

            currentBackend.renameEntry(entryDN, newEntry, op);
          }

          if (preReadRequest != null)
          {
            Entry entry = currentEntry.duplicate(true);

            if (! preReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! preReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! preReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPreReadResponseControl responseControl =
                 new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                                preReadRequest.isCritical(),
                                                searchEntry);

            op.addResponseControl(responseControl);
          }

          if (postReadRequest != null)
          {
            Entry entry = newEntry.duplicate(true);

            if (! postReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! postReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! postReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPostReadResponseControl responseControl =
                 new LDAPPostReadResponseControl(postReadRequest.getOID(),
                                                 postReadRequest.isCritical(),
                                                 searchEntry);

            op.addResponseControl(responseControl);
          }


          if (! noOp)
          {
            op.setResultCode(ResultCode.SUCCESS);
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          op.setResultCode(de.getResultCode());
          op.appendErrorMessage(de.getMessageObject());
          op.setMatchedDN(de.getMatchedDN());
          op.setReferralURLs(de.getReferralURLs());

          break modifyDNProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          op.setCancelResult(cancelResult);
          op.setResultCode(cancelResult.getResultCode());

          Message message = coe.getMessageObject();
          if ((message != null) && (message.length() > 0))
          {
            op.appendErrorMessage(message);
          }

          break modifyDNProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, currentLock);
        LockManager.unlock(newDN, newLock);

        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(op);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_MODDN_SYNCH_POSTOP_FAILED.
                get(op.getConnectionID(), op.getOperationID(),
                    getExceptionMessage(de)));

            op.setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    op.setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation modify DN plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationModifyDNPlugins(op);
      if (postOperationResult.connectionTerminated())
      {
        op.setResultCode(ResultCode.CANCELED);


        op.appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (op.getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleModifyDNOperation(op,
              op.getOriginalEntry(),
              op.getUpdatedEntry());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_MODDN_ERROR_NOTIFYING_CHANGE_LISTENER.get(
              getExceptionMessage(e));
          logError(message);
        }
      }
    }

  }


  /**
   * Attaches the current local operation to the global operation so that
   * operation runner can execute local operation post response later on.
   *
   * @param <O>              subtype of Operation
   * @param <L>              subtype of LocalBackendOperation
   * @param globalOperation  the global operation to which local operation
   *                         should be attached to
   * @param currentLocalOperation  the local operation to attach to the global
   *                               operation
   */
  @SuppressWarnings("unchecked")
  public static final <O extends Operation, L>
  void attachLocalOperation (
      O globalOperation,
      L currentLocalOperation
      )
  {
    List<?> existingAttachment =
      (List<?>) globalOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);

    List<L> newAttachment = new ArrayList<L>();

    if (existingAttachment != null)
    {
      // This line raises an unchecked conversion warning.
      // There is nothing we can do to prevent this warning
      // so let's get rid of it since we know the cast is safe.
      newAttachment.addAll ((List<L>) existingAttachment);
    }
    newAttachment.add (currentLocalOperation);
    globalOperation.setAttachment(
        Operation.LOCALBACKENDOPERATIONS, newAttachment);
  }

}
