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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationWrapper;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to modify an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendModifyOperation
       extends ModifyOperationWrapper
       implements PreOperationModifyOperation, PostOperationModifyOperation,
                  PostResponseModifyOperation,
                  PostSynchronizationModifyOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The backend in which the target entry exists.
  private Backend backend;

  // Indicates whether the request included the user's current password.
  private boolean currentPasswordProvided;

  // Indicates whether the user's account has been enabled or disabled by this
  // modify operation.
  private boolean enabledStateChanged;

  // Indicates whether the user's account is currently enabled.
  private boolean isEnabled;

  // Indicates whether the request included the LDAP no-op control.
  private boolean noOp;

  // Indicates whether this modify operation includees a password change.
  private boolean passwordChanged;

  // Indicates whether the request included the password policy request control.
  private boolean pwPolicyControlRequested;

  // Indicates whether the password change is a self-change.
  private boolean selfChange;

  // Indicates whether to skip post-operation plugin processing.
  private boolean skipPostOperation;

  // Indicates whether the user's account was locked before this change.
  private boolean wasLocked;

  // The client connection associated with this operation.
  private ClientConnection clientConnection;

  // The DN of the entry to modify.
  private DN entryDN;

  // The current entry, before any changes are applied.
  private Entry currentEntry = null;

  // The modified entry that will be stored in the backend.
  private Entry modifiedEntry = null;

  // The number of passwords contained in the modify operation.
  private int numPasswords;

  // The post-read request control, if present.
  private LDAPPostReadRequestControl postReadRequest;

  // The pre-read request control, if present.
  private LDAPPreReadRequestControl preReadRequest;

  // The set of clear-text current passwords (if any were provided).
  private List<AttributeValue> currentPasswords = null;

  // The set of clear-text new passwords (if any were provided).
  private List<AttributeValue> newPasswords = null;

  // The set of modifications contained in this request.
  private List<Modification> modifications;

  // The password policy error type for this operation.
  private PasswordPolicyErrorType pwpErrorType;

  // The password policy state for this modify operation.
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
   * Retrieves the current entry before any modifications are applied.  This
   * will not be available to pre-parse plugins.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *          available.
   */
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
   *          modify request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  public final List<AttributeValue> getCurrentPasswords()
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
   *          <CODE>null</CODE> if it is not yet available.
   */
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
   *          request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  public final List<AttributeValue> getNewPasswords()
  {
    return newPasswords;
  }



  /**
   * Adds the provided modification to the set of modifications to this modify
   * operation.
   * In addition, the modification is applied to the modified entry.
   *
   * This may only be called by pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of changes for
   *                       this modify operation.
   *
   * @throws  DirectoryException  If an unexpected problem occurs while applying
   *                              the modification to the entry.
   */
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modifiedEntry.applyModification(modification);
    super.addModification(modification);
  }



  /**
   * Process this modify operation against a local backend.
   *
   * @param  backend  The backend in which the modify operation should be
   *                  performed.
   */
  void processLocalModify(Backend backend)
  {
    this.backend = backend;

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    skipPostOperation = false;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyProcessing:
    {
      entryDN = getEntryDN();
      if (entryDN == null){
        break modifyProcessing;
      }

      // Process the modifications to convert them from their raw form to the
      // form required for the rest of the modify processing.
      modifications = getModifications();
      if (modifications == null)
      {
        break modifyProcessing;
      }

      if (modifications.isEmpty())
      {
        setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        appendErrorMessage(ERR_MODIFY_NO_MODIFICATIONS.get(
                                String.valueOf(entryDN)));
        break modifyProcessing;
      }


      // If the user must change their password before doing anything else, and
      // if the target of the modify operation isn't the user's own entry, then
      // reject the request.
      if ((! isInternalOperation()) && clientConnection.mustChangePassword())
      {
        DN authzDN = getAuthorizationDN();
        if ((authzDN != null) && (! authzDN.equals(entryDN)))
        {
          // The user will not be allowed to do anything else before the
          // password gets changed.  Also note that we haven't yet checked the
          // request controls so we need to do that now to see if the password
          // policy request control was provided.
          for (Control c : getRequestControls())
          {
            if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
            {
              pwPolicyControlRequested = true;
              pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
              break;
            }
          }

          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
          break modifyProcessing;
        }
      }


      // Check for a request to cancel this operation.
      if (cancelIfRequested())
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
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_MODIFY_CANNOT_LOCK_ENTRY.get(
                                String.valueOf(entryDN)));
        skipPostOperation = true;
        break modifyProcessing;
      }


      try
      {
        // Check for a request to cancel this operation.
        if (cancelIfRequested())
        {
          return;
        }


        try
        {
          // Get the entry to modify.  If it does not exist, then fail.
          getEntryToModify();

          // Check to see if there are any controls in the request.  If so, then
          // see if there is any special processing required.
          processRequestControls();

          // Get the password policy state object for the entry that can be used
          // to perform any appropriate password policy processing.  Also, see
          // if the entry is being updated by the end user or an administrator.
          selfChange = entryDN.equals(getAuthorizationDN());

          // FIXME -- Need a way to enable debug mode.
          pwPolicyState = new PasswordPolicyState(currentEntry, false,
                                                  TimeThread.getTime(), true,
                                                  false);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        // Create a duplicate of the entry and apply the changes to it.
        modifiedEntry = currentEntry.duplicate(false);

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
                provider.handleConflictResolution(this);
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

              logError(ERR_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED.get(
                            getConnectionID(), getOperationID(),
                            getExceptionMessage(de)));
              setResponseData(de);
              break modifyProcessing;
            }
          }
        }


        try
        {
          handleSchemaProcessing();
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        // Check to see if the client has permission to perform the modify.
        // The access control check is not made any earlier because the handler
        // needs access to the modified entry.

        // FIXME: for now assume that this will check all permissions
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may have
        // already exposed sensitive information to the client.
        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(this))
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                                  String.valueOf(entryDN)));
          skipPostOperation = true;
          break modifyProcessing;
        }


        try
        {
          handleInitialPasswordPolicyProcessing();

          wasLocked = false;
          if (passwordChanged)
          {
            performAdditionalPasswordChangedProcessing();
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }


        if ((! passwordChanged) && (! isInternalOperation()) &&
            pwPolicyState.mustChangePassword())
        {
          // The user will not be allowed to do anything else before the
          // password gets changed.
          pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD.get());
          break modifyProcessing;
        }


        // If the server is configured to check the schema and the
        // operation is not a sycnhronization operation,
        // make sure that the new entry is valid per the server schema.
        if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
        {
          MessageBuilder invalidReason = new MessageBuilder();
          if (! modifiedEntry.conformsToSchema(null, false, false, false,
              invalidReason))
          {
            setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            appendErrorMessage(ERR_MODIFY_VIOLATES_SCHEMA.get(
                                    String.valueOf(entryDN), invalidReason));
            break modifyProcessing;
          }
        }


        // Check for a request to cancel this operation.
        if (cancelIfRequested())
        {
          return;
        }

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify plugins.
        if (! isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationModifyPlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            setResultCode(ResultCode.CANCELED);
            appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
            setProcessingStopTime();
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
        if (cancelIfRequested())
        {
          return;
        }


        // Actually perform the modify operation.  This should also include
        // taking care of any synchronization that might be needed.
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(ERR_MODIFY_NO_BACKEND_FOR_ENTRY.get(
                                  String.valueOf(entryDN)));
          break modifyProcessing;
        }

        try
        {
          try
          {
            checkWritability();
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break modifyProcessing;
          }


          if (noOp)
          {
            appendErrorMessage(INFO_MODIFY_NOOP.get());
            setResultCode(ResultCode.NO_OPERATION);
          }
          else
          {
            for (SynchronizationProvider provider :
              DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(this);
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

                logError(ERR_MODIFY_SYNCH_PREOP_FAILED.get(getConnectionID(),
                              getOperationID(), getExceptionMessage(de)));
                setResponseData(de);
                break modifyProcessing;
              }
            }

            backend.replaceEntry(modifiedEntry, this);



            // See if we need to generate any account status notifications as a
            // result of the changes.
            if (passwordChanged || enabledStateChanged || wasLocked)
            {
              handleAccountStatusNotifications();
            }
          }


          // Handle any processing that may be needed for the pre-read and/or
          // post-read controls.
          handleReadEntryProcessing();


          if (! noOp)
          {
            setResultCode(ResultCode.SUCCESS);
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResponseData(de);
          break modifyProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          setCancelResult(cancelResult);
          setResultCode(cancelResult.getResultCode());

          Message message = coe.getMessageObject();
          if (message != null)
          {
            appendErrorMessage(message);
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
            provider.doPostOperation(this);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_MODIFY_SYNCH_POSTOP_FAILED.get(getConnectionID(),
                          getOperationID(), getExceptionMessage(de)));
            setResponseData(de);
            break;
          }
        }
      }
    }


    // If the password policy request control was included, then make sure we
    // send the corresponding response control.
    if (pwPolicyControlRequested)
    {
      addResponseControl(new PasswordPolicyResponseControl(null, 0,
                                                           pwpErrorType));
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // Invoke the post-operation or post-synchronization modify plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationModifyPlugins(this);
      }
    }
    else if (! skipPostOperation)
    {
      // FIXME -- Should this also be done while holding the locks?
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationModifyPlugins(this);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        setResultCode(ResultCode.CANCELED);
        appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
        setProcessingStopTime();
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      notifyChangeListeners();
    }


    // Stop the processing timer.
    setProcessingStopTime();
  }



  /**
   * Checks to determine whether there has been a request to cancel this
   * operation.  If so, then set the cancel result and processing stop time.
   *
   * @return  {@code true} if there was a cancel request, or {@code false} if
   *          not.
   */
  private boolean cancelIfRequested()
  {
    if (getCancelRequest() == null)
    {
      return false;
    }

    indicateCancelled(getCancelRequest());
    setProcessingStopTime();
    return true;
  }



  /**
   * Gets the entry to modify.
   *
   *
   * @throws  DirectoryException  If a problem occurs while trying to get the
   *                              entry, or if the entry doesn't exist.
   */
  private void getEntryToModify()
          throws DirectoryException
  {
    currentEntry = backend.getEntry(entryDN);
    if (currentEntry == null)
    {
      // See if one of the entry's ancestors exists.
      DN matchedDN = null;
      DN parentDN = entryDN.getParentDNInSuffix();
      while (parentDN != null)
      {
        try
        {
          if (DirectoryServer.entryExists(parentDN))
          {
            matchedDN = parentDN;
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

      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                     ERR_MODIFY_NO_SUCH_ENTRY.get(String.valueOf(entryDN)),
                     matchedDN, null);
    }
  }



  /**
   * Processes any controls contained in the modify request.
   *
   * @throws  DirectoryException  If a problem is encountered with any of the
   *                              controls.
   */
  private void processRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(entryDN, this, c))
        {
          skipPostOperation = true;
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }

          try
          {
            // FIXME -- We need to determine whether the current user has
            //          permission to make this determination.
            SearchFilter filter = assertControl.getSearchFilter();
            if (! filter.matchesEntry(currentEntry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_MODIFY_ASSERTION_FAILED.get(
                                                String.valueOf(entryDN)));
            }
          }
          catch (DirectoryException de)
          {
            if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
            {
              throw de;
            }

            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                           ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V2))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
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
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_MODIFY_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN), oid));
          }
        }
      }
    }
  }

   /**
   * Handles schema processing for non-password modifications.
   *
   * @throws  DirectoryException  If a problem is encountered that should cause
   *                              the modify operation to fail.
   */
  private void handleSchemaProcessing() throws DirectoryException
  {

    for (Modification m : modifications)
    {
      Attribute     a = m.getAttribute();
      AttributeType t = a.getAttributeType();


      // If the attribute type is marked "NO-USER-MODIFICATION" then fail unless
      // this is an internal operation or is related to synchronization in some
      // way.
      if (t.isNoUserModification())
      {
        if (! (isInternalOperation() || isSynchronizationOperation() ||
                m.isInternal()))
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  ERR_MODIFY_ATTR_IS_NO_USER_MOD.get(
                          String.valueOf(entryDN), a.getName()));
        }
      }

      // If the attribute type is marked "OBSOLETE" and the modification is
      // setting new values, then fail unless this is an internal operation or
      // is related to synchronization in some way.
      if (t.isObsolete())
      {
        if (a.hasValue() &&
                (m.getModificationType() != ModificationType.DELETE))
        {
          if (! (isInternalOperation() || isSynchronizationOperation() ||
                  m.isInternal()))
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_MODIFY_ATTR_IS_OBSOLETE.get(
                            String.valueOf(entryDN), a.getName()));
          }
        }
      }


      // See if the attribute is one which controls the privileges available for
      // a user.  If it is, then the client must have the PRIVILEGE_CHANGE
      // privilege.
      if (t.hasName(OP_ATTR_PRIVILEGE_NAME))
      {
        if (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                  ERR_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
        }
      }

      // If the modification is not updating the password attribute,
      // then check if the isEnabled flag should be set and then perform any
      // schema processing.
      boolean isPassword =
              t.equals(pwPolicyState.getPolicy().getPasswordAttribute());
      if (!isPassword )
      {
        // See if it's an attribute used to maintain the account
        // enabled/disabled state.
        AttributeType disabledAttr =
               DirectoryServer.getAttributeType(OP_ATTR_ACCOUNT_DISABLED, true);
        if (t.equals(disabledAttr))
        {
          enabledStateChanged = true;
          for (AttributeValue v : a.getValues())
          {
            try
            {
              isEnabled =
                  (! BooleanSyntax.decodeBooleanValue(v.getNormalizedValue()));
            }
            catch (DirectoryException de)
            {
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                      ERR_MODIFY_INVALID_DISABLED_VALUE.get(
                              OP_ATTR_ACCOUNT_DISABLED,
                              String.valueOf(de.getMessageObject())), de);
            }
          }
        }

        switch (m.getModificationType())
        {
          case ADD:
            processInitialAddSchema(a);
            break;

          case DELETE:
            processInitialDeleteSchema(a);
            break;

          case REPLACE:
            processInitialReplaceSchema(a);
            break;

          case INCREMENT:
            processInitialIncrementSchema(a);
            break;
        }
      }
    }
  }

  /**
   * Handles the initial set of password policy  for this modify operation.
   *
   * @throws  DirectoryException  If a problem is encountered that should cause
   *                              the modify operation to fail.
   */
  private void handleInitialPasswordPolicyProcessing()
          throws DirectoryException
  {
    // Declare variables used for password policy state processing.
    currentPasswordProvided = false;
    isEnabled = true;
    enabledStateChanged = false;
    if (currentEntry.hasAttribute(
            pwPolicyState.getPolicy().getPasswordAttribute()))
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


    // If it's not an internal or synchronization operation, then iterate
    // through the set of modifications to see if a password is included in the
    // changes.  If so, then add the appropriate state changes to the set of
    // modifications.
    if (! (isInternalOperation() || isSynchronizationOperation()))
    {
      for (Modification m : modifications)
      {
        AttributeType t = m.getAttribute().getAttributeType();
        boolean isPassword =
                t.equals(pwPolicyState.getPolicy().getPasswordAttribute());
        if (isPassword)
        {
          passwordChanged = true;
          if (! selfChange)
          {
            if (! clientConnection.hasPrivilege(Privilege.PASSWORD_RESET, this))
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
              throw new DirectoryException(
                      ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                      ERR_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES.get());
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


      // If the modification is updating the password attribute, then perform
      // any necessary password policy processing.  This processing should be
      // skipped for synchronization operations.
      boolean isPassword =
              t.equals(pwPolicyState.getPolicy().getPasswordAttribute());
      if (isPassword && (!(isSynchronizationOperation())))
      {
        // If the attribute contains any options, then reject it.  Passwords
        // will not be allowed to have options. Skipped for internal operations.
        if(! isInternalOperation())
        {
          if (a.hasOptions())
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
          }


          // If it's a self change, then see if that's allowed.
          if (selfChange &&
                  (! pwPolicyState.getPolicy().allowUserPasswordChanges()))
          {
            pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_MODIFY_NO_USER_PW_CHANGES.get());
          }


          // If we require secure password changes, then makes sure it's a
          // secure communication channel.
          if (pwPolicyState.getPolicy().requireSecurePasswordChanges() &&
                  (! clientConnection.isSecure()))
          {
            pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
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

        // Check to see whether this will adding, deleting, or replacing
        // password values (increment doesn't make any sense for passwords).
        // Then perform the appropriate type of processing for that kind of
        // modification.
        boolean isAdd = (m.getModificationType() == ModificationType.ADD);
        LinkedHashSet<AttributeValue> pwValues = a.getValues();
        LinkedHashSet<AttributeValue> encodedValues =
                new LinkedHashSet<AttributeValue>();
        switch (m.getModificationType())
        {
          case ADD:
          case REPLACE:
            processInitialAddOrReplacePW(isAdd, pwValues, encodedValues, a);
            break;

          case DELETE:
            processInitialDeletePW(pwValues, encodedValues, a);
            break;

          default:
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD.get(
                            String.valueOf(m.getModificationType()),
                            a.getName()));
        }

      switch (m.getModificationType())
      {
        case ADD:
          processInitialAddSchema(a);
          break;

        case DELETE:
          processInitialDeleteSchema(a);
          break;

        case REPLACE:
          processInitialReplaceSchema(a);
          break;

        case INCREMENT:
          processInitialIncrementSchema(a);
          break;
      }
     }
    }
  }



  /**
   * Performs the initial password policy add or replace processing.
   *
   * @param  isAdd          Indicates whether it is an add or replace update.
   * @param  pwValues       The set of password values as included in the
   *                        request.
   * @param  encodedValues  The set of encoded password values.
   * @param  pwAttr         The attribute involved in the password change.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialAddOrReplacePW(boolean isAdd,
                    LinkedHashSet<AttributeValue> pwValues,
                    LinkedHashSet<AttributeValue> encodedValues,
                    Attribute pwAttr)
          throws DirectoryException
  {
    int passwordsToAdd = pwValues.size();

    if (isAdd)
    {
      numPasswords += passwordsToAdd;
    }
    else
    {
      numPasswords = passwordsToAdd;
    }


    // If there were multiple password values, then make sure that's OK.
    if ((! isInternalOperation()) &&
        (! pwPolicyState.getPolicy().allowMultiplePasswordValues()) &&
        (passwordsToAdd > 1))
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                     ERR_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED.get());
    }


    // Iterate through the password values and see if any of them are
    // pre-encoded.  If so, then check to see if we'll allow it.  Otherwise,
    // store the clear-text values for later validation and update the attribute
    // with the encoded values.
    for (AttributeValue v : pwValues)
    {
      if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
      {
        if ((! isInternalOperation()) &&
            ! pwPolicyState.getPolicy().allowPreEncodedPasswords())
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                         ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
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
          // Make sure that the password value doesn't already exist.
          if (pwPolicyState.passwordMatches(v.getValue()))
          {
            pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
            throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                                         ERR_MODIFY_PASSWORD_EXISTS.get());
          }
        }

        if (newPasswords == null)
        {
          newPasswords = new LinkedList<AttributeValue>();
        }

        newPasswords.add(v);

        for (ByteString s : pwPolicyState.encodePassword(v.getValue()))
        {
          encodedValues.add(new AttributeValue(pwAttr.getAttributeType(), s));
        }
      }
    }

    pwAttr.setValues(encodedValues);
  }



  /**
   * Performs the initial password policy delete processing.
   *
   * @param  pwValues       The set of password values as included in the
   *                        request.
   * @param  encodedValues  The set of encoded password values.
   * @param  pwAttr         The attribute involved in the password change.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialDeletePW(LinkedHashSet<AttributeValue> pwValues,
                    LinkedHashSet<AttributeValue> encodedValues,
                    Attribute pwAttr)
          throws DirectoryException
  {
    // Iterate through the password values and see if any of them are
    // pre-encoded.  We will never allow pre-encoded passwords for user password
    // changes, but we will allow them for administrators.  For each clear-text
    // value, verify that at least one value in the entry matches and replace
    // the clear-text value with the appropriate encoded forms.
    for (AttributeValue v : pwValues)
    {
      if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
      {
        if ((! isInternalOperation()) && selfChange)
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                         ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
        }
        else
        {
          encodedValues.add(v);
        }
      }
      else
      {
        List<Attribute> attrList =
             currentEntry.getAttribute(pwAttr.getAttributeType());
        if ((attrList == null) || (attrList.isEmpty()))
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       ERR_MODIFY_NO_EXISTING_VALUES.get());
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
                StringBuilder[] compoenents =
                     AuthPasswordSyntax.decodeAuthPassword(av.getStringValue());
                PasswordStorageScheme scheme =
                     DirectoryServer.getAuthPasswordStorageScheme(
                          compoenents[0].toString());
                if (scheme != null)
                {
                  if (scheme.authPasswordMatches(v.getValue(),
                                                 compoenents[1].toString(),
                                                 compoenents[2].toString()))
                  {
                    encodedValues.add(av);
                    found = true;
                  }
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
                String[] compoenents = UserPasswordSyntax.decodeUserPassword(
                                            av.getStringValue());
                PasswordStorageScheme scheme =
                     DirectoryServer.getPasswordStorageScheme(
                          toLowerCase(compoenents[0]));
                if (scheme != null)
                {
                  if (scheme.passwordMatches(v.getValue(),
                                  new ASN1OctetString(compoenents[1])))
                  {
                    encodedValues.add(av);
                    found = true;
                  }
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
          if (currentPasswords == null)
          {
            currentPasswords = new LinkedList<AttributeValue>();
          }
          currentPasswords.add(v);
          numPasswords--;
        }
        else
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       ERR_MODIFY_INVALID_PASSWORD.get());
        }

        currentPasswordProvided = true;
      }
    }

    pwAttr.setValues(encodedValues);
  }



  /**
   * Performs the initial schema processing for an add modification and updates
   * the entry appropriately.
   *
   * @param  attr  The attribute being added.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialAddSchema(Attribute attr)
          throws DirectoryException
  {
    // Make sure that one or more values have been provided for the attribute.
    LinkedHashSet<AttributeValue> newValues = attr.getValues();
    if ((newValues == null) || newValues.isEmpty())
    {
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                     ERR_MODIFY_ADD_NO_VALUES.get(String.valueOf(entryDN),
                                                 attr.getName()));
    }

    // If the server is configured to check schema and the operation is not a
    // synchronization operation, make sure that all the new values are valid
    // according to the associated syntax.
    if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
    {
      AcceptRejectWarn syntaxPolicy =
           DirectoryServer.getSyntaxEnforcementPolicy();
      AttributeSyntax syntax = attr.getAttributeType().getSyntax();

      if (syntaxPolicy == AcceptRejectWarn.REJECT)
      {
        MessageBuilder invalidReason = new MessageBuilder();
        for (AttributeValue v : newValues)
        {
          if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
          {
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                           ERR_MODIFY_ADD_INVALID_SYNTAX.get(
                                String.valueOf(entryDN), attr.getName(),
                                v.getStringValue(), invalidReason));
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
            setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
            logError(ERR_MODIFY_ADD_INVALID_SYNTAX.get(String.valueOf(entryDN),
                          attr.getName(), v.getStringValue(), invalidReason));
            invalidReason = new MessageBuilder();
          }
        }
      }
    }


    // Add the provided attribute or merge an existing attribute with
    // the values of the new attribute.  If there are any duplicates,
    // then fail.
    if (attr.getAttributeType().isObjectClassType())
    {
      modifiedEntry.addObjectClasses(newValues);
    }
    else
    {
      LinkedList<AttributeValue> duplicateValues =
           new LinkedList<AttributeValue>();
      modifiedEntry.addAttribute(attr, duplicateValues);
      if (! duplicateValues.isEmpty())
      {
        StringBuilder buffer = new StringBuilder();
        Iterator<AttributeValue> iterator = duplicateValues.iterator();
        buffer.append(iterator.next().getStringValue());
        while (iterator.hasNext())
        {
          buffer.append(", ");
          buffer.append(iterator.next().getStringValue());
        }

        throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                       ERR_MODIFY_ADD_DUPLICATE_VALUE.get(
                            String.valueOf(entryDN), attr.getName(), buffer));
      }
    }
  }



  /**
   * Performs the initial schema processing for a delete modification and
   * updates the entry appropriately.
   *
   * @param  attr  The attribute being deleted.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialDeleteSchema(Attribute attr)
          throws DirectoryException
  {
    // Remove the specified attribute values or the entire attribute from the
    // value.  If there are any specified values that were not present, then
    // fail.  If the RDN attribute value would be removed, then fail.
    LinkedList<AttributeValue> missingValues = new LinkedList<AttributeValue>();
    boolean attrExists = modifiedEntry.removeAttribute(attr, missingValues);

    if (attrExists)
    {
      if (missingValues.isEmpty())
      {
        AttributeType t = attr.getAttributeType();

        RDN rdn = modifiedEntry.getDN().getRDN();
        if ((rdn !=  null) && rdn.hasAttributeType(t) &&
            (! modifiedEntry.hasValue(t, attr.getOptions(),
                                      rdn.getAttributeValue(t))))
        {
          throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                                       ERR_MODIFY_DELETE_RDN_ATTR.get(
                                            String.valueOf(entryDN),
                                            attr.getName()));
        }
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

        throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
                       ERR_MODIFY_DELETE_MISSING_VALUES.get(
                            String.valueOf(entryDN), attr.getName(), buffer));
      }
    }
    else
    {
      throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
                     ERR_MODIFY_DELETE_NO_SUCH_ATTR.get(
                          String.valueOf(entryDN), attr.getName()));
    }
  }



  /**
   * Performs the initial schema processing for a replace modification and
   * updates the entry appropriately.
   *
   * @param  attr  The attribute being replaced.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialReplaceSchema(Attribute attr)
          throws DirectoryException
  {
    // If it is the objectclass attribute, then treat that separately.
    if (attr.getAttributeType().isObjectClassType())
    {
      modifiedEntry.setObjectClasses(attr.getValues());
      return;
    }


    // If the provided attribute does not have any values, then we will simply
    // remove the attribute from the entry (if it exists).
    AttributeType t = attr.getAttributeType();
    if (! attr.hasValue())
    {
      modifiedEntry.removeAttribute(t, attr.getOptions());
      RDN rdn = modifiedEntry.getDN().getRDN();
      if ((rdn !=  null) && rdn.hasAttributeType(t) &&
          (! modifiedEntry.hasValue(t, attr.getOptions(),
                                    rdn.getAttributeValue(t))))
      {
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                       ERR_MODIFY_DELETE_RDN_ATTR.get(String.valueOf(entryDN),
                                                      attr.getName()));
      }

      return;
    }

    // If the server is configured to check schema and the operation is not a
    // synchronization operation, make sure that all the new values are valid
    // according to the associated syntax.
    LinkedHashSet<AttributeValue> newValues = attr.getValues();
    if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
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
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                           ERR_MODIFY_REPLACE_INVALID_SYNTAX.get(
                                String.valueOf(entryDN), attr.getName(),
                                v.getStringValue(), invalidReason));
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
            setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
            logError(ERR_MODIFY_REPLACE_INVALID_SYNTAX.get(
                          String.valueOf(entryDN), attr.getName(),
                          v.getStringValue(), invalidReason));
            invalidReason = new MessageBuilder();
          }
        }
      }
    }


    // If the provided attribute does not have any options, then we will simply
    // use it in place of any existing attribute of the provided type (or add it
    // if it doesn't exist).
    if (! attr.hasOptions())
    {
      List<Attribute> attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      modifiedEntry.putAttribute(t, attrList);

      RDN rdn = modifiedEntry.getDN().getRDN();
      if ((rdn !=  null) && rdn.hasAttributeType(t) &&
          (! modifiedEntry.hasValue(t, attr.getOptions(),
                                    rdn.getAttributeValue(t))))
      {
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                                     ERR_MODIFY_DELETE_RDN_ATTR.get(
                                          String.valueOf(entryDN),
                                          attr.getName()));
      }

      return;
    }


    // See if there is an existing attribute of the provided type.  If not, then
    // we'll use the new one.
    List<Attribute> attrList = modifiedEntry.getAttribute(t);
    if ((attrList == null) || attrList.isEmpty())
    {
      attrList = new ArrayList<Attribute>(1);
      attrList.add(attr);
      modifiedEntry.putAttribute(t, attrList);

      RDN rdn = modifiedEntry.getDN().getRDN();
      if ((rdn !=  null) && rdn.hasAttributeType(t) &&
          (! modifiedEntry.hasValue(t, attr.getOptions(),
                                    rdn.getAttributeValue(t))))
      {
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                                     ERR_MODIFY_DELETE_RDN_ATTR.get(
                                          String.valueOf(entryDN),
                                          attr.getName()));
      }

      return;
    }


    // There must be an existing occurrence of the provided attribute in the
    // entry.  If there is a version with exactly the set of options provided,
    // then replace it.  Otherwise, add a new one.
    boolean found = false;
    for (int i=0; i < attrList.size(); i++)
    {
      if (attrList.get(i).optionsEqual(attr.getOptions()))
      {
        attrList.set(i, attr);
        found = true;
        break;
      }
    }

    if (! found)
    {
      attrList.add(attr);
    }

    RDN rdn = modifiedEntry.getDN().getRDN();
    if ((rdn !=  null) && rdn.hasAttributeType(t) &&
        (! modifiedEntry.hasValue(t, attr.getOptions(),
                                  rdn.getAttributeValue(t))))
    {
      throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                                   ERR_MODIFY_DELETE_RDN_ATTR.get(
                                        String.valueOf(entryDN),
                                        attr.getName()));
    }
  }



  /**
   * Performs the initial schema processing for an increment modification and
   * updates the entry appropriately.
   *
   * @param  attr  The attribute being incremented.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  private void processInitialIncrementSchema(Attribute attr)
          throws DirectoryException
  {
    // The specified attribute type must not be an RDN attribute.
    AttributeType t = attr.getAttributeType();
    RDN rdn = modifiedEntry.getDN().getRDN();
    if ((rdn !=  null) && rdn.hasAttributeType(t))
    {
      throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_RDN,
                                   ERR_MODIFY_INCREMENT_RDN.get(
                                        String.valueOf(entryDN),
                                        attr.getName()));
    }


    // The provided attribute must have a single value, and it must be an
    // integer.
    LinkedHashSet<AttributeValue> values = attr.getValues();
    if ((values == null) || values.isEmpty())
    {
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                   ERR_MODIFY_INCREMENT_REQUIRES_VALUE.get(
                                        String.valueOf(entryDN),
                                        attr.getName()));
    }
    else if (values.size() > 1)
    {
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                     ERR_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE.get(
                          String.valueOf(entryDN), attr.getName()));
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

      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                     ERR_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER.get(
                          String.valueOf(entryDN), attr.getName(),
                          v.getStringValue()), e);
    }


    // Get the corresponding attribute from the entry and make sure that it has
    // a single integer value.
    List<Attribute> attrList = modifiedEntry.getAttribute(t, attr.getOptions());
    if ((attrList == null) || attrList.isEmpty())
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                     ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(
                          String.valueOf(entryDN), attr.getName()));
    }

    boolean updated = false;
    for (Attribute a : attrList)
    {
      LinkedHashSet<AttributeValue> valueList = a.getValues();
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

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                         ERR_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE.get(
                              String.valueOf(entryDN), a.getName(),
                              existingValue.getStringValue()), e);
        }

        ByteString newValue = new ASN1OctetString(String.valueOf(newIntValue));
        newValueList.add(new AttributeValue(t, newValue));
      }

      a.setValues(newValueList);
      updated = true;
    }

    if (! updated)
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                     ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(
                          String.valueOf(entryDN), attr.getName()));
    }
  }



  /**
   * Performs additional preliminary processing that is required for a password
   * change.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the
   *                              modify operation to fail.
   */
  public void performAdditionalPasswordChangedProcessing()
         throws DirectoryException
  {
    // If it was a self change, then see if the current password was provided
    // and handle accordingly.
    if (selfChange &&
        pwPolicyState.getPolicy().requireCurrentPassword() &&
        (! currentPasswordProvided))
    {
      pwpErrorType = PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;

      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                     ERR_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW.get());
    }


    // If this change would result in multiple password values, then see if
    // that's OK.
    if ((numPasswords > 1) &&
        (! pwPolicyState.getPolicy().allowMultiplePasswordValues()))
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                     ERR_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED.get());
    }


    // If any of the password values should be validated, then do so now.
    if (selfChange ||
        (! pwPolicyState.getPolicy().skipValidationForAdministrators()))
    {
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
            // duplicates because technically it's possible that the values
            // aren't duplicates if they are ASN.1 elements with different types
            // (like 0x04 for a standard universal octet string type versus 0x80
            // for a simple password in a bind operation).  So we have to
            // manually check for duplicates.
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
          if (! pwPolicyState.passwordIsAcceptable(this, modifiedEntry,
                                   v.getValue(), clearPasswords, invalidReason))
          {
            pwpErrorType =
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         ERR_MODIFY_PW_VALIDATION_FAILED.get(
                                              invalidReason));
          }
        }
      }
    }


    // If we should check the password history, then do so now.
    if (pwPolicyState.maintainHistory())
    {
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
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                           ERR_MODIFY_PW_IN_HISTORY.get());
            }
          }
        }

        pwPolicyState.updatePasswordHistory();
      }
    }


    // See if the account was locked for any reason.
    wasLocked = pwPolicyState.lockedDueToIdleInterval() ||
                pwPolicyState.lockedDueToMaximumResetAge() ||
                pwPolicyState.lockedDueToFailures();

    // Update the password policy state attributes in the user's entry.  If the
    // modification fails, then these changes won't be applied.
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
    modifiedEntry.applyModifications(pwPolicyState.getModifications());
  }



  /**
   * Checks to ensure that both the Directory Server and the backend are
   * writable.
   *
   * @throws  DirectoryException  If the modify operation should not be allowed
   *                              as a result of the writability check.
   */
  private void checkWritability()
          throws DirectoryException
  {
    // If it is not a private backend, then check to see if the server or
    // backend is operating in read-only mode.
    if (! backend.isPrivateBackend())
    {
      switch (DirectoryServer.getWritabilityMode())
      {
        case DISABLED:
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       ERR_MODIFY_SERVER_READONLY.get(
                                            String.valueOf(entryDN)));

        case INTERNAL_ONLY:
          if (! (isInternalOperation() || isSynchronizationOperation()))
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         ERR_MODIFY_SERVER_READONLY.get(
                                              String.valueOf(entryDN)));
          }
      }

      switch (backend.getWritabilityMode())
      {
        case DISABLED:
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       ERR_MODIFY_BACKEND_READONLY.get(
                                            String.valueOf(entryDN)));

        case INTERNAL_ONLY:
          if (! isInternalOperation() || isSynchronizationOperation())
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         ERR_MODIFY_BACKEND_READONLY.get(
                                              String.valueOf(entryDN)));
          }
      }
    }
  }



  /**
   * Handles any account status notifications that may be needed as a result of
   * modify processing.
   */
  private void handleAccountStatusNotifications()
  {
    if (passwordChanged)
    {
      if (selfChange)
      {
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if (authInfo.getAuthenticationDN().equals(modifiedEntry.getDN()))
        {
          clientConnection.setMustChangePassword(false);
        }

        Message message = INFO_MODIFY_PASSWORD_CHANGED.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_CHANGED,
            modifiedEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 currentPasswords, newPasswords));
      }
      else
      {
        Message message = INFO_MODIFY_PASSWORD_RESET.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_RESET, modifiedEntry,
            message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 currentPasswords, newPasswords));
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
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 null, null));
      }
      else
      {
        Message message = INFO_MODIFY_ACCOUNT_DISABLED.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.ACCOUNT_DISABLED,
            modifiedEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 null, null));
      }
    }

    if (wasLocked)
    {
      Message message = INFO_MODIFY_ACCOUNT_UNLOCKED.get();
      pwPolicyState.generateAccountStatusNotification(
          AccountStatusNotificationType.ACCOUNT_UNLOCKED, modifiedEntry,
          message,
          AccountStatusNotification.createProperties(pwPolicyState, false, -1,
               null, null));
    }
  }



  /**
   * Handles any processing that is required for the LDAP pre-read and/or
   * post-read controls.
   */
  private void handleReadEntryProcessing()
  {
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

      // FIXME -- Check access controls on the entry to see if it should be
      //          returned or if any attributes need to be stripped out..
      SearchResultEntry searchEntry = new SearchResultEntry(entry);
      LDAPPreReadResponseControl responseControl =
           new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                          preReadRequest.isCritical(),
                                          searchEntry);
      getResponseControls().add(responseControl);
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

      // FIXME -- Check access controls on the entry to see if it should be
      //          returned or if any attributes need to be stripped out..
      SearchResultEntry searchEntry = new SearchResultEntry(entry);
      LDAPPostReadResponseControl responseControl =
           new LDAPPostReadResponseControl(postReadRequest.getOID(),
                                           postReadRequest.isCritical(),
                                           searchEntry);

      getResponseControls().add(responseControl);
    }
  }



  /**
   * Notify any registered change listeners about this update.
   */
  private void notifyChangeListeners()
  {
    for (ChangeNotificationListener changeListener :
         DirectoryServer.getChangeNotificationListeners())
    {
      try
      {
        changeListener.handleModifyOperation(this, currentEntry, modifiedEntry);
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
}

