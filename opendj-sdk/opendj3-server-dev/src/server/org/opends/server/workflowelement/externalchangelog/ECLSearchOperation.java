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
 *      Portions Copyright 2010-2014 ForgeRock AS
 */
package org.opends.server.workflowelement.externalchangelog;

import java.text.SimpleDateFormat;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigConstants;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.ECLServerHandler;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.types.*;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.replication.protocol.StartECLSessionMsg.ECLRequestType.*;
import static org.opends.server.replication.protocol.StartECLSessionMsg.Persistent.*;
import static org.opends.server.util.LDIFWriter.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines an operation used to search for entries in a local backend
 * of the Directory Server.
 */
public class ECLSearchOperation
       extends SearchOperationWrapper
       implements PreOperationSearchOperation, PostOperationSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of supported controls for this WE. */
  private static final Set<String> CHANGELOG_SUPPORTED_CONTROLS =
      new HashSet<String>(Arrays.asList(
          ServerConstants.OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
          ServerConstants.OID_VLV_REQUEST_CONTROL));

  /** The set of objectclasses that will be used in ECL root entry. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ROOT_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);
  static
  {
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(topOC, OC_TOP);

    ObjectClass containerOC = DirectoryServer.getObjectClass("container", true);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(containerOC, "container");
  }

  /** The set of objectclasses that will be used in ECL entries. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ENTRY_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);
  static
  {
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP, true);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(topOC, OC_TOP);

    ObjectClass eclEntryOC = DirectoryServer.getObjectClass(OC_CHANGELOG_ENTRY,
        true);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(eclEntryOC, OC_CHANGELOG_ENTRY);
  }


  /** The attribute type for the "creatorsName" attribute. */
  private static final AttributeType CREATORS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_CREATORS_NAME_LC, true);

  /** The attribute type for the "modifiersName" attribute. */
  private static final AttributeType MODIFIERS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC, true);


  /** The associated DN. */
  private static final DN CHANGELOG_ROOT_DN;
  static
  {
    try
    {
      CHANGELOG_ROOT_DN = DN
          .valueOf(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
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

  private ECLServerHandler eclServerHandler;

  /**
   * A flag to know if the ECLControl has been requested.
   */
  private boolean returnECLControl = false;

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

      final StartECLSessionMsg startECLSessionMsg = new StartECLSessionMsg();

      // Set default behavior as "from change number".
      // "from cookie" is set only when cookie is provided.
      startECLSessionMsg.setECLRequestType(REQUEST_TYPE_FROM_CHANGE_NUMBER);

      // Set a string operationId that will help correlate any error message
      // logged for this operation with the 'real' client operation.
      startECLSessionMsg.setOperationId(toString());

      // Set a list of excluded domains (also exclude 'cn=changelog' itself)
      Set<String> excludedDomains =
        MultimasterReplication.getECLDisabledDomains();
      excludedDomains.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
      startECLSessionMsg.setExcludedDNs(excludedDomains);

      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      baseDN = getBaseDN();
      filter = getFilter();
      if (baseDN == null || filter == null)
      {
        break searchProcessing;
      }

      // Test existence of the RS - normally should always be here
      if (replicationServer == null)
      {
        setResultCode(ResultCode.OPERATIONS_ERROR);
        appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(baseDN));
        break searchProcessing;
      }

      // Analyse controls - including the cookie control
      try
      {
        handleRequestControls(startECLSessionMsg);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);
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
        logger.traceException(de);
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
        processSearch(startECLSessionMsg);
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
        break searchProcessing;
      }
      catch (CanceledOperationException coe)
      {
        if (persistentSearch != null)
        {
          persistentSearch.cancel();
          setSendResponse(true);
        }
        shutdownECLServerHandler();
        throw coe;
      }
      catch (Exception e)
      {
        logger.traceException(e);

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
  private void handleRequestControls(StartECLSessionMsg startECLSessionMsg)
      throws DirectoryException
  {
    List<Control> requestControls  = getRequestControls();
    if (requestControls != null && !requestControls.isEmpty())
    {
      for (Iterator<Control> iter = requestControls.iterator(); iter.hasNext();)
      {
        final Control c = iter.next();
        final String oid = c.getOID();

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
          // We don't want to process this non-critical control, so remove it.
          iter.remove();
          continue;
        }

        if (OID_ECL_COOKIE_EXCHANGE_CONTROL.equals(oid))
        {
          ExternalChangelogRequestControl eclControl =
            getRequestControl(ExternalChangelogRequestControl.DECODER);
          MultiDomainServerState cookie = eclControl.getCookie();
          returnECLControl = true;
          if (cookie != null)
          {
            startECLSessionMsg.setECLRequestType(REQUEST_TYPE_FROM_COOKIE);
            startECLSessionMsg.setCrossDomainServerState(cookie.toString());
          }
        }
        else if (OID_LDAP_ASSERTION.equals(oid))
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

            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                ERR_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER.get(
                    de.getMessageObject()), de);
          }
        }
        else if (OID_PROXIED_AUTH_V1.equals(oid))
        {
          // Log usage of legacy proxy authz V1 control.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteProxiedAuthzV1Control"));

          // The requester must have the PROXIED_AUTH privilege in order to be
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
            setProxiedAuthorizationDN(DN.rootDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getName());
          }
        }
        else if (OID_PROXIED_AUTH_V2.equals(oid))
        {
          // The requester must have the PROXIED_AUTH privilege in order to be
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
            setProxiedAuthorizationDN(DN.rootDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getName());
          }
        }
        else if (OID_PERSISTENT_SEARCH.equals(oid))
        {
          PersistentSearchControl psearchControl =
            getRequestControl(PersistentSearchControl.DECODER);

          persistentSearch = new PersistentSearch(this,
              psearchControl.getChangeTypes(),
              psearchControl.getReturnECs());

          // If we're only interested in changes, then we don't actually want
          // to process the search now.
          if (psearchControl.getChangesOnly())
            startECLSessionMsg.setPersistent(PERSISTENT_CHANGES_ONLY);
          else
            startECLSessionMsg.setPersistent(PERSISTENT);
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

        // TODO: Add support for additional controls, including VLV
        else if (c.isCritical()
            && (replicationServer == null || !supportsControl(oid)))
        {
          throw new DirectoryException(
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_SEARCH_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
        }
      }
    }
  }

  private void processSearch(StartECLSessionMsg startECLSessionMsg)
      throws DirectoryException, CanceledOperationException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace(" processSearch toString=[" + toString() + "] opid=["
          + startECLSessionMsg.getOperationId() + "]");
    }

    // Start a specific ECL server handler
    eclServerHandler =
        new ECLServerHandler(replicationServer, startECLSessionMsg);
    boolean abortECLSession = false;
    try
    {
      // Get first update (this is needed to determine hasSubordinates.
      ECLUpdateMsg update = eclServerHandler.getNextECLUpdate();

      // Return root entry if requested.
      if (CHANGELOG_ROOT_DN.matchesBaseAndScope(baseDN, getScope()))
      {
        final Entry entry = createRootEntry(update != null);
        if (filter.matchesEntry(entry) && !returnEntry(entry, null))
        {
          // Abandon, Size limit reached.
          abortECLSession = true;
          return;
        }
      }

      if (baseDN.equals(CHANGELOG_ROOT_DN)
          && getScope().equals(SearchScope.BASE_OBJECT))
      {
        // Only the change log root entry was requested. There is no need to
        // process other entries.
        return;
      }

      int lookthroughCount = 0;
      int lookthroughLimit = getClientConnection().getLookthroughLimit();

      // Process change log entries.
      while (update != null)
      {
        if(lookthroughLimit > 0 && lookthroughCount > lookthroughLimit)
        {
          //Lookthrough limit exceeded
          setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
          appendErrorMessage(
                  NOTE_ECL_LOOKTHROUGH_LIMIT_EXCEEDED.get(lookthroughLimit));
          return;
        }
        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        if (!buildAndReturnEntry(update))
        {
          // Abandon, Size limit reached.
          abortECLSession = true;
          return;
        }

        lookthroughCount++;

        update = eclServerHandler.getNextECLUpdate();
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
        shutdownECLServerHandler();
      }
    }
  }

  private boolean supportsControl(String oid)
  {
    return CHANGELOG_SUPPORTED_CONTROLS.contains(oid);
  }

  /**
   * Build an ECL entry from a provided ECL msg and return it.
   * @param eclMsg The provided ECL msg.
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   * @throws DirectoryException When an errors occurs.
   */
  private boolean buildAndReturnEntry(ECLUpdateMsg eclMsg)
      throws DirectoryException
  {
    final Entry entry = createEntryFromMsg(eclMsg);
    if (matchScopeAndFilter(entry))
    {
      List<Control> controls = null;
      if (returnECLControl)
      {
        controls = new ArrayList<Control>(1);

        EntryChangelogNotificationControl clrc =
            new EntryChangelogNotificationControl(
                true, eclMsg.getCookie().toString());
        controls.add(clrc);
      }
      return returnEntry(entry, controls);
    }

    // Check the timelimit here as well, in case there are no matches
    if ((getTimeLimit() > 0) && (TimeThread.getTime() >=
      getTimeLimitExpiration()))
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_TIME_LIMIT_EXCEEDED.get(getTimeLimit()));
      return false;
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
    return entry.matchesBaseAndScope(getBaseDN(), getScope())
        && getFilter().matchesEntry(entry);
  }

  /**
   * Create an ECL entry from a provided ECL msg.
   *
   * @param eclMsg
   *          the provided ECL msg.
   * @return the created ECL entry.
   * @throws DirectoryException
   *           When an error occurs.
   */
  public static Entry createEntryFromMsg(ECLUpdateMsg eclMsg)
      throws DirectoryException
  {
    Entry entry = null;

    // Get the meat from the ecl msg
    UpdateMsg msg = eclMsg.getUpdateMsg();

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
          for (ByteString v : a)
          {
            builder.append(attrName);
            appendLDIFSeparatorAndValue(builder, v);
            builder.append('\n');
          }
        }
        ldifChanges = builder.toString();
      }
      catch (Exception e)
      {
        // Unable to decode the message - log an error.
        logger.traceException(e);

        logger.error(LocalizableMessage.raw("An exception was encountered while try to encode a "
                + "replication add message for entry \""
                + addMsg.getDN()
                + "\" into an External Change Log entry: "
                + e.getMessage()));
      }

      entry = createChangelogEntry(eclMsg,
          addMsg,
          ldifChanges, // entry as created (in LDIF format)
          "add", changeInitiatorsName);
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
          builder.append(m.getModificationType());
          builder.append(": ");
          builder.append(attrName);
          builder.append('\n');

          for (ByteString v : a)
          {
            builder.append(attrName);
            appendLDIFSeparatorAndValue(builder, v);
            builder.append('\n');
          }
          builder.append("-\n");
        }
        ldifChanges = builder.toString();
      }
      catch (Exception e)
      {
        // Unable to decode the message - log an error.
        logger.traceException(e);

        logger.error(LocalizableMessage.raw("An exception was encountered while try to encode a "
                + "replication modify message for entry \""
                + modifyMsg.getDN()
                + "\" into an External Change Log entry: "
                + e.getMessage()));
      }

      final boolean isModifyDNMsg = modifyMsg instanceof ModifyDNMsg;
      entry = createChangelogEntry(eclMsg,
          modifyMsg,
          ldifChanges,
          (isModifyDNMsg ? "modrdn" : "modify"),
          changeInitiatorsName);

      if (isModifyDNMsg)
      {
        ModifyDNMsg modDNMsg = (ModifyDNMsg) modifyMsg;

        addAttribute(entry, "newrdn", modDNMsg.getNewRDN());
        if (modDNMsg.getNewSuperior() != null)
        {
          addAttribute(entry, "newsuperior", modDNMsg.getNewSuperior());
        }
        addAttribute(entry, "deleteoldrdn",
            String.valueOf(modDNMsg.deleteOldRdn()));
      }
    }
    else if (msg instanceof DeleteMsg)
    {
      DeleteMsg delMsg = (DeleteMsg) msg;

      entry = createChangelogEntry(eclMsg,
          delMsg,
          null, // no changes
          "delete",
          delMsg.getInitiatorsName());
    }

    return entry;
  }

  private static void addAttribute(Entry e, String attrType, String attrValue)
  {
    e.addAttribute(Attributes.create(attrType, attrValue), null);
  }

  /**
   * Creates the root entry of the external changelog.
   * @param hasSubordinates whether the root entry has subordinates or not.
   * @return The root entry created.
   */
  private Entry createRootEntry(boolean hasSubordinates)
  {
    // Attributes
    Map<AttributeType, List<Attribute>> userAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();
    Map<AttributeType, List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    addAttributeByUppercaseName(ATTR_COMMON_NAME, ATTR_COMMON_NAME,
        "changelog", userAttrs, operationalAttrs);
    addAttributeByUppercaseName(ATTR_SUBSCHEMA_SUBENTRY_LC,
        ATTR_SUBSCHEMA_SUBENTRY, ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
        userAttrs, operationalAttrs);

    // TODO:numSubordinates

    addAttributeByUppercaseName("hassubordinates", "hasSubordinates",
        Boolean.toString(hasSubordinates), userAttrs, operationalAttrs);
    addAttributeByUppercaseName("entrydn", "entryDN",
        CHANGELOG_ROOT_DN.toNormalizedString(), userAttrs, operationalAttrs);

    return new Entry(CHANGELOG_ROOT_DN, CHANGELOG_ROOT_OBJECT_CLASSES,
        userAttrs, operationalAttrs);
  }

  private void addAttributeByUppercaseName(String attrNameLowercase,
      String attrNameUppercase, String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    AttributeType aType = DirectoryServer.getAttributeType(attrNameLowercase);
    if (aType == null)
    {
      aType = DirectoryServer.getDefaultAttributeType(attrNameUppercase);
    }
    final Attribute a = Attributes.create(attrNameUppercase, attrValue);
    final List<Attribute> attrList = Collections.singletonList(a);
    if (aType.isOperational())
    {
      operationalAttrs.put(aType, attrList);
    }
    else
    {
      userAttrs.put(aType, attrList);
    }
  }

  private static void addAttributeByType(String attrNameLowercase,
      String attrNameUppercase, String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    AttributeType aType = DirectoryServer.getAttributeType(attrNameLowercase);
    if (aType == null)
    {
      aType = DirectoryServer.getDefaultAttributeType(attrNameUppercase);
    }
    Attribute a = Attributes.create(aType, attrValue);
    List<Attribute> attrList = Collections.singletonList(a);
    if (aType.isOperational())
    {
      operationalAttrs.put(aType, attrList);
    }
    else
    {
      userAttrs.put(aType, attrList);
    }
  }

  /**
   * Create an ECL entry from a set of provided information. This is the part of
   * entry creation common to all types of msgs (ADD, DEL, MOD, MODDN).
   *
   * @param eclMsg
   *          The provided ECLUpdateMsg for which to build the changelog entry.
   * @param msg
   *          The provided LDAPUpdateMsg for which to build the changelog entry.
   * @param ldifChanges
   *          The provided LDIF changes for ADD and MODIFY
   * @param changeType
   *          The provided change type (add, ...)
   * @param changeInitiatorsName
   *          The provided initiators name
   * @return The created ECL entry.
   * @throws DirectoryException
   *           When any error occurs.
   */
  private static Entry createChangelogEntry(
      ECLUpdateMsg eclMsg,
      LDAPUpdateMsg msg,
      String ldifChanges,
      String changeType,
      String changeInitiatorsName)
  throws DirectoryException
  {
    final DN baseDN = eclMsg.getBaseDN();
    final long changeNumber = eclMsg.getChangeNumber();
    final CSN csn = msg.getCSN();

    String dnString;
    if (changeNumber == 0)
    {
      // cookie mode
      dnString = "replicationCSN=" + csn + "," + baseDN.toNormalizedString()
          + "," + ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
    }
    else
    {
      // Draft compat mode
      dnString = "changeNumber=" + changeNumber + ","
          + ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT;
    }

    // Attributes
    Map<AttributeType, List<Attribute>> uAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();
    Map<AttributeType, List<Attribute>> operationalAttrs =
      new LinkedHashMap<AttributeType,List<Attribute>>();

    // Operational standard attributes
    addAttributeByType(ATTR_SUBSCHEMA_SUBENTRY_LC, ATTR_SUBSCHEMA_SUBENTRY_LC,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, uAttrs, operationalAttrs);
    addAttributeByType("numsubordinates", "numSubordinates", "0", uAttrs,
        operationalAttrs);
    addAttributeByType("hassubordinates", "hasSubordinates", "false", uAttrs,
        operationalAttrs);
    addAttributeByType("entrydn", "entryDN", dnString, uAttrs,
        operationalAttrs);

    // REQUIRED attributes

    // ECL Changelog change number
    addAttributeByType("changenumber", "changeNumber",
        String.valueOf(changeNumber), uAttrs, operationalAttrs);

    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ??
    final String format = dateFormat.format(new Date(csn.getTime()));
    addAttributeByType("changetime", "changeTime", format, uAttrs,
        operationalAttrs);
    addAttributeByType("changetype", "changeType", changeType, uAttrs,
        operationalAttrs);
    addAttributeByType("targetdn", "targetDN", msg.getDN().toNormalizedString(),
        uAttrs, operationalAttrs);

    // NON REQUESTED attributes

    addAttributeByType("replicationcsn", "replicationCSN",
        csn.toString(), uAttrs, operationalAttrs);
    addAttributeByType("replicaidentifier", "replicaIdentifier",
        Integer.toString(csn.getServerId()), uAttrs, operationalAttrs);

    if (ldifChanges != null)
    {
      addAttributeByType("changes", "changes", ldifChanges, uAttrs,
          operationalAttrs);
    }

    if (changeInitiatorsName != null)
    {
      addAttributeByType("changeinitiatorsname", "changeInitiatorsName",
          changeInitiatorsName, uAttrs, operationalAttrs);
    }

    final String targetUUID = msg.getEntryUUID();
    if (targetUUID != null)
    {
      addAttributeByType("targetentryuuid", "targetEntryUUID", targetUUID,
          uAttrs, operationalAttrs);
    }

    final String cookie = eclMsg.getCookie().toString();
    addAttributeByType("changelogcookie", "changeLogCookie", cookie, uAttrs,
        operationalAttrs);

    final List<RawAttribute> includedAttributes = msg.getEclIncludes();
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

      addAttributeByType("includedattributes", "includedAttributes",
          includedAttributesLDIF, uAttrs, operationalAttrs);
    }

    // at the end build the CL entry to be returned
    return new Entry(DN.valueOf(dnString), CHANGELOG_ENTRY_OBJECT_CLASSES,
        uAttrs, operationalAttrs);
  }

  /** {@inheritDoc} */
  @Override
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    if (logger.isTraceEnabled())
      logger.trace(this + " cancel() " + eclServerHandler);
    shutdownECLServerHandler();
    return super.cancel(cancelRequest);
  }

  /** {@inheritDoc} */
  @Override
  public void abort(CancelRequest cancelRequest)
  {
    if (logger.isTraceEnabled())
      logger.trace(this + " abort() " + eclServerHandler);
    shutdownECLServerHandler();
  }

  private void shutdownECLServerHandler()
  {
    if (eclServerHandler != null)
    {
      eclServerHandler.shutdown();
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
    switch (baseDN.size())
    {
    case 1:
      // cn=changelog - use user provided search filter.
      break;
    case 2:
      // changeNumber=xxx,cn=changelog - draft ECL - use faked up equality
      // filter.

      // The DN could also be a new ECL <service-id>,cn=changelog so be sure it
      // is draft ECL.
      RDN rdn = baseDN.rdn();

      AttributeType at = DirectoryServer.getAttributeType("changenumber");
      if (at == null)
      {
        at = DirectoryServer.getDefaultAttributeType("changeNumber");
      }

      ByteString av = rdn.getAttributeValue(at);
      if (av != null)
      {
        sf = SearchFilter.createEqualityFilter(at, av);
      }
      break;
    default:
      // replicationCSN=xxx,<service-id>,cn=changelog - new ECL - use faked up
      // equality filter.
      rdn = baseDN.rdn();

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
    startCLmsg.setFirstChangeNumber(msg.getFirstChangeNumber());
    startCLmsg.setLastChangeNumber(msg.getLastChangeNumber());
    startCLmsg.setCSN(msg.getCSN());
  }

  private static StartECLSessionMsg evaluateSearchParameters2(SearchFilter sf)
  throws DirectoryException
  {
    StartECLSessionMsg startCLmsg = new StartECLSessionMsg();
    startCLmsg.setFirstChangeNumber(-1);
    startCLmsg.setLastChangeNumber(-1);
    startCLmsg.setCSN(new CSN(0, 0, 0));

    // If there's no filter, just return
    if (sf == null)
    {
      return startCLmsg;
    }

    // Here are the 3 elementary cases we know how to optimize
    if (matches(sf, FilterType.GREATER_OR_EQUAL, "changeNumber"))
    {
      int sn = extractChangeNumber(sf);
      startCLmsg.setFirstChangeNumber(sn);
    }
    else if (matches(sf, FilterType.LESS_OR_EQUAL, "changeNumber"))
    {
      int sn = extractChangeNumber(sf);
      startCLmsg.setLastChangeNumber(sn);
    }
    else if (matches(sf, FilterType.EQUALITY, "replicationcsn"))
    {
      // == exact CSN
      startCLmsg.setCSN(new CSN(sf.getAssertionValue().toString()));
    }
    else if (matches(sf, FilterType.EQUALITY, "changenumber"))
    {
      int sn = extractChangeNumber(sf);
      startCLmsg.setFirstChangeNumber(sn);
      startCLmsg.setLastChangeNumber(sn);
    }
    else if (sf.getFilterType() == FilterType.AND)
    {
      // Here is the only binary operation we know how to optimize
      Collection<SearchFilter> comps = sf.getFilterComponents();
      SearchFilter sfs[] = comps.toArray(new SearchFilter[0]);
      long l1 = -1;
      long f1 = -1;
      long l2 = -1;
      long f2 = -1;
      StartECLSessionMsg m1;
      StartECLSessionMsg m2;
      if (sfs.length > 0)
      {
        m1 = evaluateSearchParameters2(sfs[0]);
        l1 = m1.getLastChangeNumber();
        f1 = m1.getFirstChangeNumber();
      }
      if (sfs.length > 1)
      {
        m2 = evaluateSearchParameters2(sfs[1]);
        l2 = m2.getLastChangeNumber();
        f2 = m2.getFirstChangeNumber();
      }
      if (l1 == -1)
        startCLmsg.setLastChangeNumber(l2);
      else if (l2 == -1)
        startCLmsg.setLastChangeNumber(l1);
      else
        startCLmsg.setLastChangeNumber(Math.min(l1, l2));

      startCLmsg.setFirstChangeNumber(Math.max(f1,f2));
    }
    return startCLmsg;
  }

  private static int extractChangeNumber(SearchFilter sf)
      throws DirectoryException
  {
    return Integer.decode(sf.getAssertionValue().toString());
  }

  private static boolean matches(SearchFilter sf, FilterType filterType,
      String primaryName)
  {
    return sf.getFilterType() == filterType
        && sf.getAttributeType() != null
        && sf.getAttributeType().getPrimaryName().equalsIgnoreCase(primaryName);
  }
}
