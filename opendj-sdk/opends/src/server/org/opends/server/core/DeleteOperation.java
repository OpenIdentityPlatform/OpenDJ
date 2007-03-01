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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LockManager;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to remove an entry from the
 * Directory Server.
 */
public class DeleteOperation
       extends Operation
       implements PreParseDeleteOperation, PreOperationDeleteOperation,
                  PostOperationDeleteOperation, PostResponseDeleteOperation
{



  // The raw, unprocessed entry DN as included in the client request.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this delete operation.
  private CancelRequest cancelRequest;

  // The DN of the entry for the delete operation.
  private DN entryDN;

  // The entry to be deleted.
  private Entry entry;

  // The set of response controls for this delete operation.
  private List<Control> responseControls;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;



  /**
   * Creates a new delete operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to delete,
   *                           as included in the client request.
   */
  public DeleteOperation(ClientConnection clientConnection, long operationID,
                         int messageID, List<Control> requestControls,
                         ByteString rawEntryDN)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN = rawEntryDN;

    entry            = null;
    entryDN          = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
  }



  /**
   * Creates a new delete operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for this delete operation.
   */
  public DeleteOperation(ClientConnection clientConnection, long operationID,
                         int messageID, List<Control> requestControls,
                         DN entryDN)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN = entryDN;

    rawEntryDN       = new ASN1OctetString(entryDN.toString());
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
    entry            = null;
  }



  /**
   * Retrieves the raw, unprocessed entry DN as included in the client request.
   * The DN that is returned may or may not be a valid DN, since no validation
   * will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client request.
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }



  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.  All other code that needs
   * to set the entry DN should use the <CODE>setEntryDN</CODE> method.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * Retrieves the DN of the entry to delete.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to delete, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  public final DN getEntryDN()
  {
    return entryDN;
  }



  /**
   * Retrieves the entry to be deleted.  This will not be available to pre-parse
   * plugins.
   *
   * @return  The entry to be deleted, or <CODE>null</CODE> if the entry is not
   *          yet available.
   */
  public final Entry getEntryToDelete()
  {
    return entry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStartTime()
  {
    return processingStartTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStopTime()
  {
    return processingStopTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingTime()
  {
    return (processingStopTime - processingStartTime);
  }



  /**
   * Retrieves the change number that has been assigned to this operation.
   *
   * @return  The change number that has been assigned to this operation, or -1
   *          if none has been assigned yet or if there is no applicable
   *          synchronization mechanism in place that uses change numbers.
   */
  public final long getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Specifies the change number that has been assigned to this operation by the
   * synchronization mechanism.
   *
   * @param  changeNumber  The change number that has been assigned to this
   *                       operation by the synchronization mechanism.
   */
  public final void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.DELETE;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, String message,
                                     int messageID)
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

    clientConnection.disconnect(disconnectReason, sendNotification, message,
                                messageID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    StringBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String matchedDNStr;
    DN matchedDN = getMatchedDN();
    if (matchedDN == null)
    {
      matchedDNStr = null;
    }
    else
    {
      matchedDNStr = matchedDN.toString();
    }

    String referrals;
    List<String> referralURLs = getReferralURLs();
    if ((referralURLs == null) || referralURLs.isEmpty())
    {
      referrals = null;
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      referrals = buffer.toString();
    }

    String processingTime =
         String.valueOf(processingStopTime - processingStartTime);

    return new String[][]
    {
      new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
      new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
      new String[] { LOG_ELEMENT_MATCHED_DN, matchedDNStr },
      new String[] { LOG_ELEMENT_REFERRAL_URLS, referrals },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
deleteProcessing:
    {
      // Invoke the pre-parse delete plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseDeletePlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logDeleteRequest(this);
        logDeleteResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logDeleteRequest(this);
        break deleteProcessing;
      }


      // Log the delete request message.
      logDeleteRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logDeleteResponse(this);
        return;
      }


      // Process the entry DN to convert it from its raw form as provided by the
      // client to the form required for the rest of the delete processing.
      try
      {
        if (entryDN == null)
        {
          entryDN = DN.decode(rawEntryDN);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        setMatchedDN(de.getMatchedDN());
        setReferralURLs(de.getReferralURLs());

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
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(getMessage(MSGID_DELETE_CANNOT_LOCK_ENTRY,
                                      String.valueOf(entryDN)));
        break deleteProcessing;
      }


      try
      {
        // Get the entry to delete.  If it doesn't exist, then fail.
        try
        {
          entry = DirectoryServer.getEntry(entryDN);
          if (entry == null)
          {
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(getMessage(MSGID_DELETE_NO_SUCH_ENTRY,
                                          String.valueOf(entryDN)));

            try
            {
              DN parentDN = entryDN.getParentDNInSuffix();
              while (parentDN != null)
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  setMatchedDN(parentDN);
                  break;
                }

                parentDN = parentDN.getParentDNInSuffix();
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
            }

            break deleteProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());
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
                 provider.handleConflictResolution(this);
            if (! result.continueOperationProcessing())
            {
              break deleteProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_DELETE_SYNCH_CONFLICT_RESOLUTION_FAILED,
                     getConnectionID(), getOperationID(),
                     stackTraceToSingleLineString(de));

            setResponseData(de);
            break deleteProcessing;
          }
        }

        // Check to see if the client has permission to perform the
        // delete.

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                   noOp           = false;
        LDAPPreReadRequestControl preReadRequest = null;
        List<Control> requestControls = getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

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
                    debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

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
                  setResultCode(ResultCode.ASSERTION_FAILED);

                  appendErrorMessage(getMessage(MSGID_DELETE_ASSERTION_FAILED,
                                                String.valueOf(entryDN)));

                  break deleteProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_DELETE_CANNOT_PROCESS_ASSERTION_FILTER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                break deleteProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPAssertionRequestControl)
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
                    debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break deleteProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
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
                    debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

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
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break deleteProcessing;
              }


              setAuthorizationEntry(authorizationEntry);
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
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
                    debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

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
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break deleteProcessing;
              }


              setAuthorizationEntry(authorizationEntry);
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                int msgID = MSGID_DELETE_UNSUPPORTED_CRITICAL_CONTROL;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
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
            .getAccessControlHandler().isAllowed(this) == false) {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          int msgID = MSGID_DELETE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
          appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

          skipPostOperation = true;
          break deleteProcessing;
        }

        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logDeleteResponse(this);
          return;
        }


        // If the operation is not a synchronization operation,
        // invoke the pre-delete plugins.
        if (!isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationDeletePlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the request
            // and result and return.
            setResultCode(ResultCode.CANCELED);

            int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
            appendErrorMessage(getMessage(msgID));

            processingStopTime = System.currentTimeMillis();
            logDeleteResponse(this);
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break deleteProcessing;
          }
        }


        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logDeleteResponse(this);
          return;
        }


        // Get the backend to use for the delete.  If there is none, then fail.
        Backend backend = DirectoryServer.getBackend(entryDN);
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(getMessage(MSGID_DELETE_NO_SUCH_ENTRY,
                                        String.valueOf(entryDN)));
          break deleteProcessing;
        }


        // If it is not a private backend, then check to see if the server or
        // backend is operating in read-only mode.
        if (! backend.isPrivateBackend())
        {
          switch (DirectoryServer.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(getMessage(MSGID_DELETE_SERVER_READONLY,
                                            String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_DELETE_SERVER_READONLY,
                                              String.valueOf(entryDN)));
                break deleteProcessing;
              }
          }

          switch (backend.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(getMessage(MSGID_DELETE_BACKEND_READONLY,
                                            String.valueOf(entryDN)));
              break deleteProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_DELETE_BACKEND_READONLY,
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
              setResultCode(ResultCode.NOT_ALLOWED_ON_NONLEAF);
              appendErrorMessage(getMessage(MSGID_DELETE_HAS_SUB_BACKEND,
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
            appendErrorMessage(getMessage(MSGID_DELETE_NOOP));

            // FIXME -- We must set a result code other than SUCCESS.
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
                  break deleteProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ErrorLogCategory.SYNCHRONIZATION,
                         ErrorLogSeverity.SEVERE_ERROR,
                         MSGID_DELETE_SYNCH_PREOP_FAILED, getConnectionID(),
                         getOperationID(), stackTraceToSingleLineString(de));

                setResponseData(de);
                break deleteProcessing;
              }
            }

            backend.deleteEntry(entryDN, this);
          }

          if (preReadRequest != null)
          {
            Entry entryCopy = entry.duplicate();

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

            responseControls.add(responseControl);
          }

          setResultCode(ResultCode.SUCCESS);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());

          break deleteProcessing;
        }
        catch (CancelledOperationException coe)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, coe);
          }

          CancelResult cancelResult = coe.getCancelResult();

          setCancelResult(cancelResult);
          setResultCode(cancelResult.getResultCode());

          String message = coe.getMessage();
          if ((message != null) && (message.length() > 0))
          {
            appendErrorMessage(message);
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
            provider.doPostOperation(this);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_DELETE_SYNCH_POSTOP_FAILED, getConnectionID(),
                     getOperationID(), stackTraceToSingleLineString(de));

            setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation delete plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationDeletePlugins(this);
      if (postOperationResult.connectionTerminated())
      {
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_POSTOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();
        logDeleteResponse(this);
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleDeleteOperation(this, entry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_DELETE_ERROR_NOTIFYING_CHANGE_LISTENER;
          String message = getMessage(msgID, stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the delete response to the client.
    getClientConnection().sendResponse(this);


    // Log the delete response.
    logDeleteResponse(this);


    // Notify any persistent searches that might be registered with the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (PersistentSearch persistentSearch :
           DirectoryServer.getPersistentSearches())
      {
        try
        {
          persistentSearch.processDelete(this, entry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_DELETE_ERROR_NOTIFYING_PERSISTENT_SEARCH;
          String message = getMessage(msgID, String.valueOf(persistentSearch),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);

          DirectoryServer.deregisterPersistentSearch(persistentSearch);
        }
      }
    }


    // Invoke the post-response delete plugins.
    pluginConfigManager.invokePostResponseDeletePlugins(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelResult cancel(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;

    CancelResult cancelResult = getCancelResult();
    long stopWaitingTime = System.currentTimeMillis() + 5000;
    while ((cancelResult == null) &&
           (System.currentTimeMillis() < stopWaitingTime))
    {
      try
      {
        Thread.sleep(50);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      cancelResult = getCancelResult();
    }

    if (cancelResult == null)
    {
      // This can happen in some rare cases (e.g., if a client disconnects and
      // there is still a lot of data to send to that client), and in this case
      // we'll prevent the cancel thread from blocking for a long period of
      // time.
      cancelResult = CancelResult.CANNOT_CANCEL;
    }

    return cancelResult;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  boolean setCancelRequest(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {
    buffer.append("DeleteOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }
}

