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



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperationWrapper;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to search for entries in a local backend
 * of the Directory Server.
 */
public class LocalBackendSearchOperation
       extends SearchOperationWrapper
       implements PreOperationSearchOperation, PostOperationSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The backend in which the search is to be performed.
  private Backend backend;

  // Indicates whether we should actually process the search.  This should
  // only be false if it's a persistent search with changesOnly=true.
  private boolean processSearch;

  // Indicates whether to skip post-operation plugin processing.
  private boolean skipPostOperation;

  // The client connection for the search operation.
  private ClientConnection clientConnection;

  // The base DN for the search.
  private DN baseDN;

  // The persistent search request, if applicable.
  private PersistentSearch persistentSearch;

  // The filter for the search.
  private SearchFilter filter;



  /**
   * Creates a new operation that may be used to search for entries in a local
   * backend of the Directory Server.
   *
   * @param  search  The operation to process.
   */
  public LocalBackendSearchOperation(SearchOperation search)
  {
    super(search);
    LocalBackendWorkflowElement.attachLocalOperation(search, this);
  }



  /**
   * Process this search operation against a local backend.
   *
   * @param  backend  The backend in which the search operation should be
   *                  performed.
   */
  void processLocalSearch(Backend backend)
  {
    this.backend = backend;

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    skipPostOperation = false;
    processSearch = true;

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
searchProcessing:
    {
      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      baseDN = getBaseDN();
      filter = getFilter();

      if ((baseDN == null) || (filter == null)){
        break searchProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then
      // see if there is any special processing required.
      try
      {
        handleRequestControls();
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);
        break searchProcessing;
      }


      // Check to see if the client has permission to perform the
      // search.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      if (! AccessControlConfigManager.getInstance().getAccessControlHandler().
                 isAllowed(this))
      {
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        appendErrorMessage(ERR_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                                String.valueOf(baseDN)));
        skipPostOperation = true;
        break searchProcessing;
      }

      // Check for a request to cancel this operation.
      if (getCancelRequest() != null)
      {
        return;
      }


      // Invoke the pre-operation search plugins.
      PreOperationPluginResult preOpResult =
           pluginConfigManager.invokePreOperationSearchPlugins(this);
      if (preOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);
        appendErrorMessage(ERR_CANCELED_BY_PREOP_DISCONNECT.get());
        setProcessingStopTime();
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
      if (getCancelRequest() != null)
      {
        return;
      }


      // Get the backend that should hold the search base.  If there is none,
      // then fail.
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(
                                String.valueOf(baseDN)));
        break searchProcessing;
      }


      // We'll set the result code to "success".  If a problem occurs, then it
      // will be overwritten.
      setResultCode(ResultCode.SUCCESS);


      // If there's a persistent search, then register it with the server.
      if (persistentSearch != null)
      {
        DirectoryServer.registerPersistentSearch(persistentSearch);
        setSendResponse(false);
      }


      // Process the search in the backend and all its subordinates.
      try
      {
        if (processSearch)
        {
          backend.search(this);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          setSendResponse(true);
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

        setCancelResult(cancelResult);
        setResultCode(cancelResult.getResultCode());

        Message message = coe.getMessageObject();
        if ((message != null) && (message.length() > 0))
        {
          appendErrorMessage(message);
        }

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          setSendResponse(true);
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

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_SEARCH_BACKEND_EXCEPTION.get(
                                getExceptionMessage(e)));

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          setSendResponse(true);
        }

        skipPostOperation = true;
        break searchProcessing;
      }
    }


    // Check for a request to cancel this operation.
    if (getCancelRequest() != null)
    {
      return;
    }


    // Invoke the post-operation search plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationSearchPlugins(this);
      if (postOperationResult.connectionTerminated())
      {
        setResultCode(ResultCode.CANCELED);
        appendErrorMessage(ERR_CANCELED_BY_POSTOP_DISCONNECT.get());
        setProcessingStopTime();
        return;
      }
    }
  }



  /**
   * Handles any controls contained in the request.
   *
   * @throws  DirectoryException  If there is a problem with any of the request
   *                              controls.
   */
  private void handleRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls  = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();
        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(baseDN, this, c))
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
                             le.getMessageObject(), le);
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

              throw new DirectoryException(de.getResultCode(),
                             ERR_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION.get(
                                  de.getMessageObject()));
            }

            if (entry == null)
            {
              throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                             ERR_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION.get());
            }

            if (! assertionFilter.matchesEntry(entry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_SEARCH_ASSERTION_FAILED.get());
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
                           ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                de.getMessageObject()), de);
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to be
          // able to use this control.
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
                             le.getMessageObject(), le);
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
          // The requester must have the PROXIED_AUTH privilige in order to be
          // able to use this control.
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
                             le.getMessageObject(), le);
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject(), le);
            }
          }

          persistentSearch = new PersistentSearch(this,
                                      psearchControl.getChangeTypes(),
                                      psearchControl.getReturnECs());
          setPersistentSearch(persistentSearch);

          // If we're only interested in changes, then we don't actually want
          // to process the search now.
          if (psearchControl.getChangesOnly())
          {
            processSearch = false;
          }
        }
        else if (oid.equals(OID_LDAP_SUBENTRIES))
        {
          setReturnLDAPSubentries(true);
        }
        else if (oid.equals(OID_MATCHED_VALUES))
        {
          if (c instanceof MatchedValuesControl)
          {
            setMatchedValuesControl((MatchedValuesControl) c);
          }
          else
          {
            try
            {
              MatchedValuesControl matchedValuesControl =
                MatchedValuesControl.decodeControl(c);
              setMatchedValuesControl(matchedValuesControl);
            }
            catch (LDAPException le)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, le);
              }

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject(), le);
            }
          }
        }
        else if (oid.equals(OID_ACCOUNT_USABLE_CONTROL))
        {
          setIncludeUsableControl(true);
        }
        else if (oid.equals(OID_REAL_ATTRS_ONLY))
        {
          setRealAttributesOnly(true);
        }
        else if (oid.equals(OID_VIRTUAL_ATTRS_ONLY))
        {
          setVirtualAttributesOnly(true);
        }

        // NYI -- Add support for additional controls.

        else if (c.isCritical())
        {
          if ((backend == null) || (! backend.supportsControl(oid)))
          {
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
          }
        }
      }
    }
  }
}

