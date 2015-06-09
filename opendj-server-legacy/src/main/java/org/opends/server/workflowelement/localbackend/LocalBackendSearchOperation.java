/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;

import static org.opends.messages.CoreMessages.*;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the search is to be performed. */
  private Backend<?> backend;

  /** The client connection for the search operation. */
  private ClientConnection clientConnection;

  /** The base DN for the search. */
  private DN baseDN;

  /** The persistent search request, if applicable. */
  private PersistentSearch persistentSearch;

  /** The filter for the search. */
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
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalSearch(LocalBackendWorkflowElement wfe)
      throws CanceledOperationException
  {
    this.backend = wfe.getBackend();
    this.clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processSearch(executePostOpPlugins);

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the post-operation search plugins.
      if (executePostOpPlugins.get())
      {
        PluginResult.PostOperation postOpResult =
            DirectoryServer.getPluginConfigManager()
                .invokePostOperationSearchPlugins(this);
        if (!postOpResult.continueProcessing())
        {
          setResultCode(postOpResult.getResultCode());
          appendErrorMessage(postOpResult.getErrorMessage());
          setMatchedDN(postOpResult.getMatchedDN());
          setReferralURLs(postOpResult.getReferralURLs());
        }
      }
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }
  }

  private void processSearch(AtomicBoolean executePostOpPlugins) throws CanceledOperationException
  {
    // Process the search base and filter to convert them from their raw forms
    // as provided by the client to the forms required for the rest of the
    // search processing.
    baseDN = getBaseDN();
    filter = getFilter();

    if (baseDN == null || filter == null)
    {
      return;
    }

    // Check to see if there are any controls in the request. If so, then
    // see if there is any special processing required.
    try
    {
      handleRequestControls();
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      setResponseData(de);
      return;
    }


    // Check to see if the client has permission to perform the
    // search.

    // FIXME: for now assume that this will check all permission
    // pertinent to the operation. This includes proxy authorization
    // and any other controls specified.
    try
    {
      if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
          .isAllowed(this))
      {
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        appendErrorMessage(ERR_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(baseDN));
        return;
      }
    }
    catch (DirectoryException e)
    {
      setResultCode(e.getResultCode());
      appendErrorMessage(e.getMessageObject());
      return;
    }

    // Check for a request to cancel this operation.
    checkIfCanceled(false);


    // Invoke the pre-operation search plugins.
    executePostOpPlugins.set(true);
    PluginResult.PreOperation preOpResult =
        DirectoryServer.getPluginConfigManager()
            .invokePreOperationSearchPlugins(this);
    if (!preOpResult.continueProcessing())
    {
      setResultCode(preOpResult.getResultCode());
      appendErrorMessage(preOpResult.getErrorMessage());
      setMatchedDN(preOpResult.getMatchedDN());
      setReferralURLs(preOpResult.getReferralURLs());
      return;
    }


    // Check for a request to cancel this operation.
    checkIfCanceled(false);


    // Get the backend that should hold the search base. If there is none,
    // then fail.
    if (backend == null)
    {
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(baseDN));
      return;
    }


    // We'll set the result code to "success". If a problem occurs, then it
    // will be overwritten.
    setResultCode(ResultCode.SUCCESS);

    try
    {
      // If there's a persistent search, then register it with the server.
      boolean processSearchNow = true;
      if (persistentSearch != null)
      {
        // If we're only interested in changes, then we do not actually want
        // to process the search now.
        processSearchNow = !persistentSearch.isChangesOnly();

        // The Core server maintains the count of concurrent persistent searches
        // so that all the backends (Remote and Local) are aware of it. Verify
        // with the core if we have already reached the threshold.
        if (!DirectoryServer.allowNewPersistentSearch())
        {
          setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
          appendErrorMessage(ERR_MAX_PSEARCH_LIMIT_EXCEEDED.get());
          return;
        }
        backend.registerPersistentSearch(persistentSearch);
        persistentSearch.enable();
      }


      if (processSearchNow)
      {
        // Process the search in the backend and all its subordinates.
        backend.search(this);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);
      setResponseData(de);

      if (persistentSearch != null)
      {
        persistentSearch.cancel();
        setSendResponse(true);
      }

      return;
    }
    catch (CanceledOperationException coe)
    {
      if (persistentSearch != null)
      {
        persistentSearch.cancel();
        setSendResponse(true);
      }

      throw coe;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      setResultCode(DirectoryServer.getServerErrorResultCode());
      appendErrorMessage(ERR_SEARCH_BACKEND_EXCEPTION
          .get(getExceptionMessage(e)));

      if (persistentSearch != null)
      {
        persistentSearch.cancel();
        setSendResponse(true);
      }
    }
  }


  /**
   * Handles any controls contained in the request.
   *
   * @throws DirectoryException
   *           If there is a problem with any of the request controls.
   */
  private void handleRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(baseDN, this);

    List<Control> requestControls  = getRequestControls();
    if (requestControls != null && ! requestControls.isEmpty())
    {
      for (Control c : requestControls)
      {
        final String  oid = c.getOID();

        if (OID_LDAP_ASSERTION.equals(oid))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

          SearchFilter assertionFilter;
          try
          {
            assertionFilter = assertControl.getSearchFilter();
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            throw new DirectoryException(de.getResultCode(),
                           ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                de.getMessageObject()), de);
          }

          Entry entry;
          try
          {
            entry = DirectoryServer.getEntry(baseDN);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            throw new DirectoryException(de.getResultCode(),
                           ERR_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION.get(
                                de.getMessageObject()));
          }

          if (entry == null)
          {
            throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                           ERR_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION.get());
          }

          // Check if the current user has permission to make
          // this determination.
          if (!AccessControlConfigManager.getInstance().
            getAccessControlHandler().isAllowed(this, entry, assertionFilter))
          {
            throw new DirectoryException(
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
          }

          try {
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

            logger.traceException(de);

            throw new DirectoryException(de.getResultCode(),
                           ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                de.getMessageObject()), de);
          }
        }
        else if (LocalBackendWorkflowElement.isProxyAuthzControl(oid))
        {
          continue;
        }
        else if (OID_PERSISTENT_SEARCH.equals(oid))
        {
          final PersistentSearchControl ctrl =
              getRequestControl(PersistentSearchControl.DECODER);

          persistentSearch = new PersistentSearch(this,
              ctrl.getChangeTypes(), ctrl.getChangesOnly(), ctrl.getReturnECs());
        }
        else if (OID_LDAP_SUBENTRIES.equals(oid))
        {
          SubentriesControl subentriesControl =
                  getRequestControl(SubentriesControl.DECODER);
          setReturnSubentriesOnly(subentriesControl.getVisibility());
        }
        else if (OID_LDUP_SUBENTRIES.equals(oid))
        {
          // Support for legacy draft-ietf-ldup-subentry.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteSubentryControl"));

          setReturnSubentriesOnly(true);
        }
        else if (OID_MATCHED_VALUES.equals(oid))
        {
          MatchedValuesControl matchedValuesControl =
                getRequestControl(MatchedValuesControl.DECODER);
          setMatchedValuesControl(matchedValuesControl);
        }
        else if (OID_ACCOUNT_USABLE_CONTROL.equals(oid))
        {
          setIncludeUsableControl(true);
        }
        else if (OID_REAL_ATTRS_ONLY.equals(oid))
        {
          setRealAttributesOnly(true);
        }
        else if (OID_VIRTUAL_ATTRS_ONLY.equals(oid))
        {
          setVirtualAttributesOnly(true);
        }
        else if (OID_GET_EFFECTIVE_RIGHTS.equals(oid) &&
          DirectoryServer.isSupportedControl(OID_GET_EFFECTIVE_RIGHTS))
        {
          // Do nothing here and let AciHandler deal with it.
        }

        // NYI -- Add support for additional controls.
        else if (c.isCritical() && !backendSupportsControl(oid))
        {
          throw new DirectoryException(
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
        }
      }
    }
  }

  private DN getName(Entry e)
  {
    return e != null ? e.getName() : DN.rootDN();
  }

  /** Indicates if the backend supports the control corresponding to provided oid. */
  private boolean backendSupportsControl(final String oid)
  {
    return backend != null && backend.supportsControl(oid);
  }
}