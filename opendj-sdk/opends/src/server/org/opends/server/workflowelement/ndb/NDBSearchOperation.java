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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.ndb;




import java.util.List;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;

import
  org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to search for entries in a NDB backend
 * of the Directory Server.
 */
public class NDBSearchOperation
       extends LocalBackendSearchOperation
       implements PreOperationSearchOperation, PostOperationSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to search for entries in a NDB
   * backend of the Directory Server.
   *
   * @param  search  The operation to process.
   */
  public NDBSearchOperation(SearchOperation search)
  {
    super(search);
    NDBWorkflowElement.attachLocalOperation(search, this);
  }



  /**
   * Process this search operation against a NDB backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalSearch(LocalBackendWorkflowElement wfe)
    throws CanceledOperationException {
    boolean executePostOpPlugins = false;

    this.backend = wfe.getBackend();

    clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();
    processSearch = true;

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

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
        break searchProcessing;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Invoke the pre-operation search plugins.
      executePostOpPlugins = true;
      PluginResult.PreOperation preOpResult =
          pluginConfigManager.invokePreOperationSearchPlugins(this);
      if (!preOpResult.continueProcessing())
      {
        setResultCode(preOpResult.getResultCode());
        appendErrorMessage(preOpResult.getErrorMessage());
        setMatchedDN(preOpResult.getMatchedDN());
        setReferralURLs(preOpResult.getReferralURLs());
        break searchProcessing;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);


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
          TRACER.debugCaught(DebugLogLevel.VERBOSE, de);
        }

        setResponseData(de);

        break searchProcessing;
      }
      catch (CanceledOperationException coe)
      {
        throw coe;
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

        break searchProcessing;
      }
    }


    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Invoke the post-operation search plugins.
    if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationSearchPlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
      }
    }
  }



  /**
   * Handles any controls contained in the request.
   *
   * @throws  DirectoryException  If there is a problem with any of the request
   *                              controls.
   */
  @Override
  protected void handleRequestControls()
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
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        if (oid.equals(OID_LDAP_ASSERTION))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

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

          ProxiedAuthV1Control proxyControl =
              getRequestControl(ProxiedAuthV1Control.DECODER);

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

          ProxiedAuthV2Control proxyControl =
              getRequestControl(ProxiedAuthV2Control.DECODER);

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
        else if (oid.equals(OID_LDAP_SUBENTRIES))
        {
          setReturnLDAPSubentries(true);
        }
        else if (oid.equals(OID_MATCHED_VALUES))
        {
          MatchedValuesControl matchedValuesControl =
                getRequestControl(MatchedValuesControl.DECODER);
          setMatchedValuesControl(matchedValuesControl);
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
        else if (oid.equals(OID_GET_EFFECTIVE_RIGHTS) &&
          DirectoryServer.isSupportedControl(OID_GET_EFFECTIVE_RIGHTS))
        {
          // Do nothing here and let AciHandler deal with it.
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
