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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2012 ForgeRock AS
 */
package org.opends.server.workflowelement.externalchangelog;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.LDIFWriter.appendLDIFSeparatorAndValue;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.text.SimpleDateFormat;
import java.util.*;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigConstants;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ExternalChangeLogSession;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.ServerConstants;



/**
 * This class defines an operation used to search for entries in a local backend
 * of the Directory Server.
 */
public class ECLSearchOperation
       extends SearchOperationWrapper
       implements PreOperationSearchOperation, PostOperationSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The ECL Start Session we'll send to the RS.
   */
  private StartECLSessionMsg startECLSessionMsg;

  //The set of supported controls for this WE
  private static final HashSet<String> CHANGELOG_SUPPORTED_CONTROLS;
  static
  {
    CHANGELOG_SUPPORTED_CONTROLS = new HashSet<String>(0);
    CHANGELOG_SUPPORTED_CONTROLS
        .add(ServerConstants.OID_SERVER_SIDE_SORT_REQUEST_CONTROL);
    CHANGELOG_SUPPORTED_CONTROLS.add(ServerConstants.OID_VLV_REQUEST_CONTROL);
  }


  // The set of objectclasses that will be used in ECL root entry.
  private static final HashMap<ObjectClass, String>
    CHANGELOG_ROOT_OBJECT_CLASSES;
  static
  {
    CHANGELOG_ROOT_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(topOC, OC_TOP);

    ObjectClass containerOC = DirectoryServer.getObjectClass("container", true);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(containerOC, "container");
  }

  // The set of objectclasses that will be used in ECL entries.
  private static final HashMap<ObjectClass, String>
    CHANGELOG_ENTRY_OBJECT_CLASSES;
  static
  {
    CHANGELOG_ENTRY_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(topOC, OC_TOP);

    ObjectClass eclEntryOC = DirectoryServer.getObjectClass(OC_CHANGELOG_ENTRY,
        true);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(eclEntryOC, OC_CHANGELOG_ENTRY);
  }


  // The attribute type for the "creatorsName" attribute.
  private static final AttributeType CREATORS_NAME_TYPE;

  // The attribute type for the "modifiersName" attribute.
  private static final AttributeType MODIFIERS_NAME_TYPE;

  static
  {
    CREATORS_NAME_TYPE = DirectoryConfig.getAttributeType(
        OP_ATTR_CREATORS_NAME_LC, true);
    MODIFIERS_NAME_TYPE = DirectoryConfig.getAttributeType(
        OP_ATTR_MODIFIERS_NAME_LC, true);
  }


  // The associated DN.
  private static final DN CHANGELOG_ROOT_DN;
  static
  {
    try
    {
      CHANGELOG_ROOT_DN = DN
          .decode(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * The replication server in which the search on ECL is to be performed.
   */
  private ReplicationServer replicationServer;

  /**
   * The client connection for the search operation.
   */
  private ClientConnection clientConnection;

  /**
   * The base DN for the search.
   */
  private DN baseDN;

  /**
   * The persistent search request, if applicable.
   */
  private PersistentSearch persistentSearch;

  /**
   * The filter for the search.
   */
  private SearchFilter filter;

  private ExternalChangeLogSession eclSession;

  /**
   * A flag to know if the ECLControl has been requested.
   */
  private Boolean returnECLControl = false;

  /**
   * Creates a new operation that may be used to search for entries in a local
   * backend of the Directory Server.
   *
   * @param  search  The operation to process.
   */
  ECLSearchOperation(SearchOperation search)
  {
    super(search);

    ECLWorkflowElement.attachLocalOperation(search, this);
  }



  /**
   * Process this search operation against a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be canceled
   */
  void processECLSearch(ECLWorkflowElement wfe)
  throws CanceledOperationException
  {
    boolean executePostOpPlugins = false;

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    searchProcessing:
    {
      replicationServer  = wfe.getReplicationServer();
      clientConnection   = getClientConnection();
      startECLSessionMsg = new StartECLSessionMsg();

      // Set default behavior as "from draft change number".
      // "from cookie" is set only when cookie is provided.
      startECLSessionMsg.setECLRequestType(
          StartECLSessionMsg.REQUEST_TYPE_FROM_DRAFT_CHANGE_NUMBER);

      // Set a string operationid that will help correlate any error message
      // logged for this operation with the 'real' client operation.
      startECLSessionMsg.setOperationId(this.toString());

      // Set a list of excluded domains (also exclude 'cn=changelog' itself)
      ArrayList<String> excludedDomains =
        MultimasterReplication.getECLDisabledDomains();
      if (!excludedDomains.contains(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT))
        excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
      startECLSessionMsg.setExcludedDNs(excludedDomains);

      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      baseDN = getBaseDN();
      filter = getFilter();
      if ((baseDN == null) || (filter == null)){
        break searchProcessing;
      }

      // Test existence of the RS - normally should always be here
      if (replicationServer == null)
      {
        setResultCode(ResultCode.OPERATIONS_ERROR);
        appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(
            String.valueOf(baseDN)));
        break searchProcessing;
      }

      // Analyse controls - including the cookie control
      try
      {
        handleRequestControls();
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        setResponseData(de);
        break searchProcessing;
      }

      // Process search parameters to optimize session query.
      try
      {
        evaluateSearchParameters(startECLSessionMsg, baseDN, filter);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        setResponseData(de);
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

      // Be optimistic by default.
      setResultCode(ResultCode.SUCCESS);

      // If there's a persistent search, then register it with the server.
      if (persistentSearch != null)
      {
        wfe.registerPersistentSearch(persistentSearch);
        persistentSearch.enable();
      }

      // Process the search.
      try
      {
        processSearch();
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, de);

        setResponseData(de);

        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }
        break searchProcessing;
      }
      catch (CanceledOperationException coe)
      {
        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }
        if (eclSession != null)
        {
          try
          {
            eclSession.close();
          }
          catch(Exception ignored){}
        }
          throw coe;
      }
      catch (Exception e)
      {
        if (debugEnabled())
          TRACER.debugCaught(DebugLogLevel.ERROR, e);

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_SEARCH_BACKEND_EXCEPTION.get(
            getExceptionMessage(e)));
        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }
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
   * Handles any controls contained in the request - including the cookie ctrl.
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

        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(baseDN, this, c))
        {
          // As per RFC 4511 4.1.11.
          if (c.isCritical())
          {
            throw new DirectoryException(
                ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
          }
          else
          {
            // We don't want to process this non-critical control, so remove it.
            removeRequestControl(c);
            continue;
          }
        }

        if (oid.equals(OID_ECL_COOKIE_EXCHANGE_CONTROL))
        {
          ExternalChangelogRequestControl eclControl =
            getRequestControl(ExternalChangelogRequestControl.DECODER);
          MultiDomainServerState cookie = eclControl.getCookie();
          returnECLControl = true;
          if (cookie!=null)
          {
            startECLSessionMsg.setECLRequestType(
                StartECLSessionMsg.REQUEST_TYPE_FROM_COOKIE);
            startECLSessionMsg.setCrossDomainServerState(cookie.toString());
          }
        }
        else if (oid.equals(OID_LDAP_ASSERTION))
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
              // FIXME: this is broken (recursive)?
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
          // Log usage of legacy proxy authz V1 control.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteProxiedAuthzV1Control"));

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
        else if (oid.equals(OID_PERSISTENT_SEARCH))
        {
          PersistentSearchControl psearchControl =
            getRequestControl(PersistentSearchControl.DECODER);

          persistentSearch = new PersistentSearch(this,
              psearchControl.getChangeTypes(),
              psearchControl.getReturnECs());

          // If we're only interested in changes, then we don't actually want
          // to process the search now.
          if (psearchControl.getChangesOnly())
            startECLSessionMsg.setPersistent(
                StartECLSessionMsg.PERSISTENT_CHANGES_ONLY);
          else
            startECLSessionMsg.setPersistent(
                StartECLSessionMsg.PERSISTENT);
        }
        else if (oid.equals(OID_LDAP_SUBENTRIES))
        {
          SubentriesControl subentriesControl =
                  getRequestControl(SubentriesControl.DECODER);
          setReturnSubentriesOnly(subentriesControl.getVisibility());
        }
        else if (oid.equals(OID_LDUP_SUBENTRIES))
        {
          // Support for legacy draft-ietf-ldup-subentry.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteSubentryControl"));

          setReturnSubentriesOnly(true);
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

        // TODO: Add support for additional controls, including VLV
        else if (c.isCritical())
        {
          if ((replicationServer == null) || (! supportsControl(oid)))
          {
            throw new DirectoryException(
                ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
          }
        }
      }
    }
  }

  private void processSearch() throws DirectoryException,
      CanceledOperationException
  {
    if (debugEnabled())
    {
      TRACER.debugInfo(" processSearch toString=[" + toString() + "] opid=["
          + startECLSessionMsg.getOperationId() + "]");
    }

    // Start a specific ECL session
    eclSession = replicationServer.createECLSession(startECLSessionMsg);
    boolean abortECLSession = false;
    try
    {
      // Get first update (this is needed to determine hasSubordinates.
      ECLUpdateMsg update = eclSession.getNextUpdate();

      // Return root entry if requested.
      if (CHANGELOG_ROOT_DN.matchesBaseAndScope(baseDN, getScope()))
      {
        final Entry entry = createRootEntry(update != null);
        if (filter.matchesEntry(entry))
        {
          if (!returnEntry(entry, null))
          {
            // Abandon, Size limit reached.
            abortECLSession = true;
            return;
          }
        }
      }

      if (baseDN.equals(CHANGELOG_ROOT_DN) && getScope().equals(
          SearchScope.BASE_OBJECT))
      {
        // Only the change log root entry was requested. There is no need to
        // process other entries.
        return;
      }

      // Process change log entries.
      while (update != null)
      {
        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        if (!buildAndReturnEntry(update))
        {
          // Abandon, Size limit reached.
          abortECLSession = true;
          return;
        }

        update = eclSession.getNextUpdate();
      }
    }
    catch (CanceledOperationException e)
    {
      abortECLSession = true;
      throw e;
    }
    catch (DirectoryException e)
    {
      abortECLSession = true;
      throw e;
    }
    finally
    {
      if (persistentSearch == null || abortECLSession)
      {
        eclSession.close();
      }
    }
  }

  private boolean supportsControl(String oid)
  {
    return CHANGELOG_SUPPORTED_CONTROLS.contains(oid);
  }

  /**
   * Build an ECL entry from a provided ECL msg and return it.
   * @param eclmsg The provided ECL msg.
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   * @throws DirectoryException When an errors occurs.
   */
  private boolean buildAndReturnEntry(ECLUpdateMsg eclmsg)
      throws DirectoryException
  {
    final Entry entry = createEntryFromMsg(eclmsg);
    if (matchScopeAndFilter(entry))
    {
      List<Control> controls = null;
      if (returnECLControl)
      {
        controls = new ArrayList<Control>(1);

        EntryChangelogNotificationControl clrc =
            new EntryChangelogNotificationControl(
                true, eclmsg.getCookie().toString());
        controls.add(clrc);
      }
      return returnEntry(entry, controls);
    }
    return true;
  }



  /**
   * Test if the provided entry matches the filter, base and scope.
   *
   * @param entry
   *          The provided entry
   * @return whether the entry matches.
   * @throws DirectoryException
   *           When a problem occurs.
   */
  private boolean matchScopeAndFilter(Entry entry) throws DirectoryException
  {
    if (entry.matchesBaseAndScope(getBaseDN(), getScope()))
    {
      return getFilter().matchesEntry(entry);
    }
    else
    {
      return false;
    }
  }



  /**
   * Create an ECL entry from a provided ECL msg.
   *
   * @param eclmsg
   *          the provided ECL msg.
   * @return the created ECL entry.
   * @throws DirectoryException
   *           When an error occurs.
   */
  public static Entry createEntryFromMsg(ECLUpdateMsg eclmsg)
      throws DirectoryException
  {
    Entry clEntry = null;

    // Get the meat from the ecl msg
    UpdateMsg msg = eclmsg.getUpdateMsg();

    if (msg instanceof AddMsg)
    {
      AddMsg addMsg = (AddMsg) msg;

      // Map addMsg to an LDIF string for the 'changes' attribute, and pull
      // out change initiators name if available which is contained in the
      // creatorsName attribute.
      String changeInitiatorsName = null;
      String ldifChanges = null;

      try
      {
        StringBuilder builder = new StringBuilder(256);
        for (Attribute a : addMsg.getAttributes())
        {
          if (a.getAttributeType().equals(CREATORS_NAME_TYPE)
              && !a.isEmpty())
          {
            // This attribute is not multi-valued.
            changeInitiatorsName = a.iterator().next().toString();
          }

          String attrName = a.getNameWithOptions();
          for (AttributeValue v : a)
          {
            builder.append(attrName);
            appendLDIFSeparatorAndValue(builder, v.getValue());
            builder.append('\n');
          }
        }
        ldifChanges = builder.toString();
      }
      catch (Exception e)
      {
        // Unable to decode the message - log an error.
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        logError(Message.raw(
            Category.SYNC,
            Severity.MILD_ERROR,
            "An exception was encountered while try to encode a "
                + "replication add message for entry \""
                + addMsg.getDn()
                + "\" into an External Change Log entry: "
                + e.getMessage()));
      }

      ArrayList<RawAttribute> eclAttributes = addMsg.getEclIncludes();

      clEntry = createChangelogEntry(eclmsg.getServiceId(), eclmsg
          .getCookie().toString(), DN.decode(addMsg.getDn()),
          addMsg.getChangeNumber(), ldifChanges, // entry as created (in LDIF
                                                 // format)
          addMsg.getEntryUUID(),
          eclAttributes, // entry attributes
          eclmsg.getDraftChangeNumber(), "add", changeInitiatorsName);

    }
    else if (msg instanceof ModifyCommonMsg)
    {
      ModifyCommonMsg modifyMsg = (ModifyCommonMsg) msg;

      // Map the modifyMsg to an LDIF string for the 'changes' attribute, and
      // pull out change initiators name if available which is contained in the
      // modifiersName attribute.
      String changeInitiatorsName = null;
      String ldifChanges = null;

      try
      {
        StringBuilder builder = new StringBuilder(128);
        for (Modification m : modifyMsg.getMods())
        {
          Attribute a = m.getAttribute();

          if (m.getModificationType() == ModificationType.REPLACE
              && a.getAttributeType().equals(MODIFIERS_NAME_TYPE)
              && !a.isEmpty())
          {
            // This attribute is not multi-valued.
            changeInitiatorsName = a.iterator().next().toString();
          }

          String attrName = a.getNameWithOptions();
          builder.append(m.getModificationType().getLDIFName());
          builder.append(": ");
          builder.append(attrName);
          builder.append('\n');

          for (AttributeValue v : a)
          {
            builder.append(attrName);
            appendLDIFSeparatorAndValue(builder, v.getValue());
            builder.append('\n');
          }
          builder.append("-\n");
        }
        ldifChanges = builder.toString();
      }
      catch (Exception e)
      {
        // Unable to decode the message - log an error.
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

        logError(Message.raw(
            Category.SYNC,
            Severity.MILD_ERROR,
            "An exception was encountered while try to encode a "
                + "replication modify message for entry \""
                + modifyMsg.getDn()
                + "\" into an External Change Log entry: "
                + e.getMessage()));
      }

      String changeType = (modifyMsg instanceof ModifyDNMsg) ? "modrdn"
          : "modify";

      clEntry = createChangelogEntry(eclmsg.getServiceId(), eclmsg
          .getCookie().toString(), DN.decode(modifyMsg.getDn()),
          modifyMsg.getChangeNumber(), ldifChanges,
          modifyMsg.getEntryUUID(),
          modifyMsg.getEclIncludes(), // entry attributes
          eclmsg.getDraftChangeNumber(), changeType,
          changeInitiatorsName);

      if (modifyMsg instanceof ModifyDNMsg)
      {
        ModifyDNMsg modDNMsg = (ModifyDNMsg) modifyMsg;

        Attribute a = Attributes.create("newrdn",
            modDNMsg.getNewRDN());
        clEntry.addAttribute(a, null);

        if (modDNMsg.getNewSuperior() != null)
        {
          Attribute b = Attributes.create("newsuperior",
              modDNMsg.getNewSuperior());
          clEntry.addAttribute(b, null);
        }

        Attribute c = Attributes.create("deleteoldrdn",
            String.valueOf(modDNMsg.deleteOldRdn()));
        clEntry.addAttribute(c, null);
      }
    }
    else if (msg instanceof DeleteMsg)
    {
      DeleteMsg delMsg = (DeleteMsg) msg;

      clEntry = createChangelogEntry(eclmsg.getServiceId(), eclmsg
          .getCookie().toString(), DN.decode(delMsg.getDn()),
          delMsg.getChangeNumber(),
          null, // no changes
          delMsg.getEntryUUID(),
          delMsg.getEclIncludes(), // entry attributes
          eclmsg.getDraftChangeNumber(), "delete",
          delMsg.getInitiatorsName());
    }

    return clEntry;
  }

  /**
   * Creates the root entry of the external changelog.
   * @param hasSubordinates whether the root entry has subordinates or not.
   * @return The root entry created.
   */
  private Entry createRootEntry(boolean hasSubordinates)
  {
    // Attributes.
    HashMap<AttributeType,List<Attribute>> userAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();
    HashMap<AttributeType,List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    // CN.
    AttributeType aType = DirectoryServer.getAttributeType(ATTR_COMMON_NAME);
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType(ATTR_COMMON_NAME);
    Attribute a = Attributes.create(ATTR_COMMON_NAME, "changelog");
    List<Attribute> attrList = Collections.singletonList(a);
    userAttrs.put(aType, attrList);

    // subSchemaSubentry
    aType = DirectoryServer.getAttributeType(ATTR_SUBSCHEMA_SUBENTRY_LC);
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType(ATTR_SUBSCHEMA_SUBENTRY);
    a = Attributes.create(ATTR_SUBSCHEMA_SUBENTRY,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT);
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else userAttrs.put(aType, attrList);

    // TODO:numSubordinates

    // hasSubordinates
    aType = DirectoryServer.getAttributeType("hassubordinates");
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType("hasSubordinates");
    a = Attributes.create("hasSubordinates", Boolean.toString(hasSubordinates));
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else userAttrs.put(aType, attrList);

    // entryDN
    aType = DirectoryServer.getAttributeType("entrydn");
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType("entryDN");
    a = Attributes.create("entryDN", CHANGELOG_ROOT_DN.toNormalizedString());
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      userAttrs.put(aType, attrList);

    Entry e = new Entry(CHANGELOG_ROOT_DN, CHANGELOG_ROOT_OBJECT_CLASSES,
        userAttrs, operationalAttrs);
    return e;
  }

  /**
   * Create an ECL entry from a set of provided information. This is the part
   * of entry creation common to all types of msgs (ADD, DEL, MOD, MODDN).
   *
   * @param serviceID       The provided cookie value.
   * @param cookie          The provided cookie value.
   * @param targetDN        The provided targetDN.
   * @param changeNumber    The provided replication changeNumber.
   * @param clearLDIFchanges     The provided LDIF changes for ADD and MODIFY
   * @param targetUUID      The provided targetUUID.
   * @param includedAttributes The provided attributes to include
   * @param draftChangenumber The provided draft change number (integer)
   * @param changetype      The provided change type (add, ...)
   * @param changeInitiatorsName The provided initiators name
   * @return                The created ECL entry.
   * @throws DirectoryException
   *         When any error occurs.
   */
  private static Entry createChangelogEntry(
      String serviceID,
      String cookie,
      DN targetDN,
      ChangeNumber changeNumber,
      String clearLDIFchanges,
      String targetUUID,
      List<RawAttribute> includedAttributes,
      int draftChangenumber,
      String changetype,
      String changeInitiatorsName)
  throws DirectoryException
  {
    AttributeType aType;

    String dnString;
    if (draftChangenumber == 0)
    {
      // Draft uncompat mode
      dnString = "replicationCSN=" + changeNumber + "," + serviceID + ","
          + ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
    }
    else
    {
      // Draft compat mode
      dnString = "changeNumber=" + draftChangenumber + ","
          + ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
    }

    // Objectclass
    HashMap<AttributeType,List<Attribute>> uAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    HashMap<AttributeType,List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    // Operational standard attributes

    // subSchemaSubentry
    aType = DirectoryServer.getAttributeType(ATTR_SUBSCHEMA_SUBENTRY_LC);
    if (aType == null)
      aType = DirectoryServer
          .getDefaultAttributeType(ATTR_SUBSCHEMA_SUBENTRY_LC);
    Attribute a = Attributes.create(aType,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT);
    List<Attribute> attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    // numSubordinates
    aType = DirectoryServer.getAttributeType("numsubordinates");
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType("numSubordinates");
    a = Attributes.create(aType, "0");
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    // hasSubordinates
    aType = DirectoryServer.getAttributeType("hassubordinates");
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType("hasSubordinates");
    a = Attributes.create(aType, "false");
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    // entryDN
    aType = DirectoryServer.getAttributeType("entrydn");
    if (aType == null)
      aType = DirectoryServer.getDefaultAttributeType("entryDN");
    a = Attributes.create(aType, dnString);
    attrList = Collections.singletonList(a);
    if (aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    // REQUIRED attributes

    // ECL Changelog draft change number
    if((aType = DirectoryServer.getAttributeType("changenumber")) == null)
      aType = DirectoryServer.getDefaultAttributeType("changeNumber");
    a = Attributes.create(aType, String.valueOf(draftChangenumber));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    //
    if((aType = DirectoryServer.getAttributeType("changetime")) == null)
      aType = DirectoryServer.getDefaultAttributeType("changeTime");
    SimpleDateFormat dateFormat;
    dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ??
    a = Attributes.create(aType,
        dateFormat.format(new Date(changeNumber.getTime())));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    //
    if((aType = DirectoryServer.getAttributeType("changetype")) == null)
      aType = DirectoryServer.getDefaultAttributeType("changeType");
    a = Attributes.create(aType, changetype);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    //
    if((aType = DirectoryServer.getAttributeType("targetdn")) == null)
      aType = DirectoryServer.getDefaultAttributeType("targetDN");
    a = Attributes.create(aType, targetDN.toNormalizedString());
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    // NON REQUESTED attributes

    if((aType = DirectoryServer.getAttributeType("replicationcsn")) == null)
      aType = DirectoryServer.getDefaultAttributeType("replicationCSN");
    a = Attributes.create(aType, changeNumber.toString());
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    //
    if((aType = DirectoryServer.getAttributeType("replicaidentifier")) == null)
      aType = DirectoryServer.getDefaultAttributeType("replicaIdentifier");
    a = Attributes.create(aType, Integer.toString(changeNumber.getServerId()));
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    if (clearLDIFchanges != null)
    {
      if ((aType = DirectoryServer.getAttributeType("changes")) == null)
      {
        aType = DirectoryServer.getDefaultAttributeType("changes");
      }

      a = Attributes.create(aType, clearLDIFchanges); // force base64
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (aType.isOperational())
      {
        operationalAttrs.put(aType, attrList);
      }
      else
      {
        uAttrs.put(aType, attrList);
      }
    }

    if (changeInitiatorsName != null)
    {
      if ((aType = DirectoryServer
          .getAttributeType("changeinitiatorsname")) == null)
      {
        aType = DirectoryServer
            .getDefaultAttributeType("changeInitiatorsName");
      }
      a = Attributes.create(aType, changeInitiatorsName);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (aType.isOperational())
      {
        operationalAttrs.put(aType, attrList);
      }
      else
      {
        uAttrs.put(aType, attrList);
      }
    }

    if (targetUUID != null)
    {
      if((aType = DirectoryServer.getAttributeType("targetentryuuid")) == null)
        aType = DirectoryServer.getDefaultAttributeType("targetEntryUUID");
      a = Attributes.create(aType, targetUUID);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);
      if(aType.isOperational())
        operationalAttrs.put(aType, attrList);
      else
        uAttrs.put(aType, attrList);
    }

    if((aType = DirectoryServer.getAttributeType("changelogcookie")) == null)
      aType = DirectoryServer.getDefaultAttributeType("changeLogCookie");
    a = Attributes.create(aType, cookie);
    attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    if(aType.isOperational())
      operationalAttrs.put(aType, attrList);
    else
      uAttrs.put(aType, attrList);

    if (includedAttributes != null && !includedAttributes.isEmpty())
    {
      StringBuilder builder = new StringBuilder(256);
      for (RawAttribute includedAttribute : includedAttributes)
      {
        String name = includedAttribute.getAttributeType();
        for (ByteString value : includedAttribute.getValues())
        {
          builder.append(name);
          appendLDIFSeparatorAndValue(builder, value);
          builder.append('\n');
        }
      }
      String includedAttributesLDIF = builder.toString();

      if ((aType = DirectoryServer
          .getAttributeType("includedattributes")) == null)
      {
        aType = DirectoryServer
            .getDefaultAttributeType("includedAttributes");
      }
      a = Attributes.create(aType, includedAttributesLDIF);
      attrList = new ArrayList<Attribute>(1);
      attrList.add(a);

      if (aType.isOperational())
      {
        operationalAttrs.put(aType, attrList);
      }
      else
      {
        uAttrs.put(aType, attrList);
      }
    }

    // at the end build the CL entry to be returned
    Entry cle = new Entry(
        DN.decode(dnString),
        CHANGELOG_ENTRY_OBJECT_CLASSES,
        uAttrs,
        operationalAttrs);

    return cle;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " cancel() " + eclSession);
    if (eclSession != null)
    {
      try
      {
        eclSession.close();
      }
      catch(Exception e){}
    }
    return super.cancel(cancelRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void abort(CancelRequest cancelRequest)
  {
    if (debugEnabled())
      TRACER.debugInfo(this + " abort() " + eclSession);
    if (eclSession != null)
    {
      try
      {
        eclSession.close();
      }
      catch(Exception e){}
    }
  }

  /**
   * Traverse the provided search filter, looking for some conditions
   * on attributes that can be optimized in the ECL.
   * When found, populate the provided StartECLSessionMsg.
   * @param startCLmsg the startCLMsg to be populated.
   * @param baseDN the provided search baseDN.
   * @param sf the provided search filter.
   * @throws DirectoryException when an exception occurs.
   */
  public static void evaluateSearchParameters(StartECLSessionMsg startCLmsg,
      DN baseDN, SearchFilter sf) throws DirectoryException
  {
    // Select whether to use the DN or the filter.
    switch (baseDN.getNumComponents())
    {
    case 1:
      // cn=changelog - use user provided search filter.
      break;
    case 2:
      // changeNumber=xxx,cn=changelog - draft ECL - use faked up equality
      // filter.

      // The DN could also be a new ECL <service-id>,cn=changelog so be sure it
      // is draft ECL.
      RDN rdn = baseDN.getRDN();

      AttributeType at = DirectoryServer.getAttributeType("changenumber");
      if (at == null)
      {
        at = DirectoryServer.getDefaultAttributeType("changeNumber");
      }

      AttributeValue av = rdn.getAttributeValue(at);
      if (av != null)
      {
        sf = SearchFilter.createEqualityFilter(at, av);
      }
      break;
    default:
      // replicationCSN=xxx,<service-id>,cn=changelog - new ECL - use faked up
      // equality filter.
      rdn = baseDN.getRDN();

      at = DirectoryServer.getAttributeType("replicationcsn");
      if (at == null)
      {
        at = DirectoryServer.getDefaultAttributeType("replicationCSN");
      }

      av = rdn.getAttributeValue(at);
      if (av != null)
      {
        sf = SearchFilter.createEqualityFilter(at, av);
      }
      break;
    }

    StartECLSessionMsg msg = evaluateSearchParameters2(sf);
    startCLmsg.setFirstDraftChangeNumber(msg.getFirstDraftChangeNumber());
    startCLmsg.setLastDraftChangeNumber(msg.getLastDraftChangeNumber());
    startCLmsg.setChangeNumber(msg.getChangeNumber());
  }

  private static StartECLSessionMsg evaluateSearchParameters2(SearchFilter sf)
  throws DirectoryException
  {
    StartECLSessionMsg startCLmsg = new StartECLSessionMsg();
    startCLmsg.setFirstDraftChangeNumber(-1);
    startCLmsg.setLastDraftChangeNumber(-1);
    startCLmsg.setChangeNumber(new ChangeNumber(0,0,(short)0));

    // If there's no filter, just return
    if (sf == null)
    {
      return startCLmsg;
    }

    // Here are the 3 elementary cases we know how to optimize
    if ((sf.getFilterType() == FilterType.GREATER_OR_EQUAL)
        && (sf.getAttributeType() != null)
        && (sf.getAttributeType().getPrimaryName().
            equalsIgnoreCase("changeNumber")))
    {
      int sn = Integer.decode(
          sf.getAssertionValue().getNormalizedValue().toString());
      startCLmsg.setFirstDraftChangeNumber(sn);
      return startCLmsg;
    }
    else if ((sf.getFilterType() == FilterType.LESS_OR_EQUAL)
        && (sf.getAttributeType() != null)
        && (sf.getAttributeType().getPrimaryName().
            equalsIgnoreCase("changeNumber")))
    {
      int sn = Integer.decode(
          sf.getAssertionValue().getNormalizedValue().toString());
      startCLmsg.setLastDraftChangeNumber(sn);
      return startCLmsg;
    }
    else if ((sf.getFilterType() == FilterType.EQUALITY)
        && (sf.getAttributeType() != null)
        && (sf.getAttributeType().getPrimaryName().
            equalsIgnoreCase("replicationcsn")))
    {
      // == exact changenumber
      ChangeNumber cn = new ChangeNumber(sf.getAssertionValue().toString());
      startCLmsg.setChangeNumber(cn);
      return startCLmsg;
    }
    else if ((sf.getFilterType() == FilterType.EQUALITY)
        && (sf.getAttributeType() != null)
        && (sf.getAttributeType().getPrimaryName().
            equalsIgnoreCase("changenumber")))
    {
      int sn = Integer.decode(
          sf.getAssertionValue().getNormalizedValue().toString());
      startCLmsg.setFirstDraftChangeNumber(sn);
      startCLmsg.setLastDraftChangeNumber(sn);
      return startCLmsg;
    }
    else if (sf.getFilterType() == FilterType.AND)
    {
      // Here is the only binary operation we know how to optimize
      Collection<SearchFilter> comps = sf.getFilterComponents();
      SearchFilter sfs[] = comps.toArray(new SearchFilter[0]);
      int l1 = -1;
      int f1 = -1;
      int l2 = -1;
      int f2 = -1;
      StartECLSessionMsg m1;
      StartECLSessionMsg m2;
      if (sfs.length > 0)
      {
        m1 = evaluateSearchParameters2(sfs[0]);
        l1 = m1.getLastDraftChangeNumber();
        f1 = m1.getFirstDraftChangeNumber();
      }
      if (sfs.length > 1)
      {
        m2 = evaluateSearchParameters2(sfs[1]);
        l2 = m2.getLastDraftChangeNumber();
        f2 = m2.getFirstDraftChangeNumber();
      }
      if (l1 == -1)
        startCLmsg.setLastDraftChangeNumber(l2);
      else
        if (l2 == -1)
          startCLmsg.setLastDraftChangeNumber(l1);
        else
          startCLmsg.setLastDraftChangeNumber(Math.min(l1,l2));

      startCLmsg.setFirstDraftChangeNumber(Math.max(f1,f2));
      return startCLmsg;
    }
    else
    {
      return startCLmsg;
    }
  }
}
