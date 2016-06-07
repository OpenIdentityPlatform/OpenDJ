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
package org.opends.server.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.AccountUsableResponseControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.operation.PostResponseSearchOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation that may be used to locate entries in the
 * Directory Server based on a given set of criteria.
 */
public class SearchOperationBasis
       extends AbstractOperation
       implements PreParseSearchOperation,
                  PostResponseSearchOperation,
                  SearchEntrySearchOperation,
                  SearchReferenceSearchOperation,
                  SearchOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Indicates whether a search result done response has been sent to the
   * client.
   */
  private final AtomicBoolean responseSent = new AtomicBoolean(false);

  /** Indicates whether the client is able to handle referrals. */
  private boolean clientAcceptsReferrals = true;

  /**
   * Indicates whether to include the account usable control with search result
   * entries.
   */
  private boolean includeUsableControl;

  /** Indicates whether to only real attributes should be returned. */
  private boolean realAttributesOnly;

  /** Indicates whether only LDAP subentries should be returned. */
  private boolean returnSubentriesOnly;

  /**
   * Indicates whether the filter references subentry or ldapSubentry object
   * class.
   */
  private boolean filterIncludesSubentries;
  private boolean filterNeedsCheckingForSubentries = true;

  /**
   * Indicates whether to include attribute types only or both types and values.
   */
  private boolean typesOnly;

  /** Indicates whether to only virtual attributes should be returned. */
  private boolean virtualAttributesOnly;

  /**
   * The raw, unprocessed base DN as included in the request from the client.
   */
  private ByteString rawBaseDN;

  /** The dereferencing policy for the search operation. */
  private DereferenceAliasesPolicy derefPolicy;

  /** The base DN for the search operation. */
  private DN baseDN;

  /** The proxied authorization target DN for this operation. */
  private DN proxiedAuthorizationDN;

  /** The number of entries that have been sent to the client. */
  private int entriesSent;

  /**
   * The number of search result references that have been sent to the client.
   */
  private int referencesSent;

  /** The size limit for the search operation. */
  private int sizeLimit;

  /** The time limit for the search operation. */
  private int timeLimit;

  /** The raw, unprocessed filter as included in the request from the client. */
  private RawFilter rawFilter;

  /** The set of attributes that should be returned in matching entries. */
  private Set<String> attributes;

  /** The set of response controls for this search operation. */
  private final List<Control> responseControls = new ArrayList<>();

  /** The time that the search time limit has expired. */
  private long timeLimitExpiration;

  /** The matched values control associated with this search operation. */
  private MatchedValuesControl matchedValuesControl;

  /** The search filter for the search operation. */
  private SearchFilter filter;

  /** The search scope for the search operation. */
  private SearchScope scope;

  /** Indicates whether to send the search result done to the client or not. */
  private boolean sendResponse = true;

  /**
   * Creates a new search operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawBaseDN         The raw, unprocessed base DN as included in the
   *                           request from the client.
   * @param  scope             The scope for this search operation.
   * @param  derefPolicy       The alias dereferencing policy for this search
   *                           operation.
   * @param  sizeLimit         The size limit for this search operation.
   * @param  timeLimit         The time limit for this search operation.
   * @param  typesOnly         The typesOnly flag for this search operation.
   * @param  rawFilter         the raw, unprocessed filter as included in the
   *                           request from the client.
   * @param  attributes        The requested attributes for this search
   *                           operation.
   */
  public SearchOperationBasis(ClientConnection clientConnection,
                         long operationID,
                         int messageID, List<Control> requestControls,
                         ByteString rawBaseDN, SearchScope scope,
                         DereferenceAliasesPolicy derefPolicy, int sizeLimit,
                         int timeLimit, boolean typesOnly, RawFilter rawFilter,
                         Set<String> attributes)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.rawBaseDN   = rawBaseDN;
    this.scope       = scope;
    this.derefPolicy = derefPolicy;
    this.sizeLimit   = sizeLimit;
    this.timeLimit   = timeLimit;
    this.typesOnly   = typesOnly;
    this.rawFilter   = rawFilter;
    this.attributes  = attributes != null ? attributes : new LinkedHashSet<String>(0);

    this.sizeLimit = getSizeLimit(sizeLimit, clientConnection);
    this.timeLimit = getTimeLimit(timeLimit, clientConnection);
  }

  /**
   * Creates a new search operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  baseDN            The base DN for this search operation.
   * @param  scope             The scope for this search operation.
   * @param  derefPolicy       The alias dereferencing policy for this search
   *                           operation.
   * @param  sizeLimit         The size limit for this search operation.
   * @param  timeLimit         The time limit for this search operation.
   * @param  typesOnly         The typesOnly flag for this search operation.
   * @param  filter            The filter for this search operation.
   * @param  attributes        The attributes for this search operation.
   */
  public SearchOperationBasis(ClientConnection clientConnection,
                         long operationID,
                         int messageID, List<Control> requestControls,
                         DN baseDN, SearchScope scope,
                         DereferenceAliasesPolicy derefPolicy, int sizeLimit,
                         int timeLimit, boolean typesOnly, SearchFilter filter,
                         Set<String> attributes)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.baseDN      = baseDN;
    this.scope       = scope;
    this.derefPolicy = derefPolicy;
    this.sizeLimit   = sizeLimit;
    this.timeLimit   = timeLimit;
    this.typesOnly   = typesOnly;
    this.filter      = filter;
    this.attributes  = attributes != null ? attributes : new LinkedHashSet<String>(0);

    rawBaseDN = ByteString.valueOfUtf8(baseDN.toString());
    rawFilter = new LDAPFilter(filter);

    this.sizeLimit = getSizeLimit(sizeLimit, clientConnection);
    this.timeLimit = getTimeLimit(timeLimit, clientConnection);
  }


  private int getSizeLimit(int sizeLimit, ClientConnection clientConnection)
  {
    if (clientConnection.getSizeLimit() <= 0)
    {
      return sizeLimit;
    }
    else if (sizeLimit <= 0)
    {
      return clientConnection.getSizeLimit();
    }
    return Math.min(sizeLimit, clientConnection.getSizeLimit());
  }

  private int getTimeLimit(int timeLimit, ClientConnection clientConnection)
  {
    if (clientConnection.getTimeLimit() <= 0)
    {
      return timeLimit;
    }
    else if (timeLimit <= 0)
    {
      return clientConnection.getTimeLimit();
    }
    return Math.min(timeLimit, clientConnection.getTimeLimit());
  }

  @Override
  public final ByteString getRawBaseDN()
  {
    return rawBaseDN;
  }

  @Override
  public final void setRawBaseDN(ByteString rawBaseDN)
  {
    this.rawBaseDN = rawBaseDN;

    baseDN = null;
  }

  @Override
  public final DN getBaseDN()
  {
    try
    {
      if (baseDN == null)
      {
        baseDN = DN.valueOf(rawBaseDN);
      }
    }
    catch (LocalizedIllegalArgumentException e)
    {
      logger.traceException(e);
      setResultCode(ResultCode.INVALID_DN_SYNTAX);
      appendErrorMessage(e.getMessageObject());
    }
    return baseDN;
  }

  @Override
  public final void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  @Override
  public final SearchScope getScope()
  {
    return scope;
  }

  @Override
  public final void setScope(SearchScope scope)
  {
    this.scope = scope;
  }

  @Override
  public final DereferenceAliasesPolicy getDerefPolicy()
  {
    return derefPolicy;
  }

  @Override
  public final void setDerefPolicy(DereferenceAliasesPolicy derefPolicy)
  {
    this.derefPolicy = derefPolicy;
  }

  @Override
  public final int getSizeLimit()
  {
    return sizeLimit;
  }

  @Override
  public final void setSizeLimit(int sizeLimit)
  {
    this.sizeLimit = sizeLimit;
  }

  @Override
  public final int getTimeLimit()
  {
    return timeLimit;
  }

  @Override
  public final void setTimeLimit(int timeLimit)
  {
    this.timeLimit = timeLimit;
  }

  @Override
  public final boolean getTypesOnly()
  {
    return typesOnly;
  }

  @Override
  public final void setTypesOnly(boolean typesOnly)
  {
    this.typesOnly = typesOnly;
  }

  @Override
  public final RawFilter getRawFilter()
  {
    return rawFilter;
  }

  @Override
  public final void setRawFilter(RawFilter rawFilter)
  {
    this.rawFilter = rawFilter;

    filter = null;
  }

  @Override
  public final SearchFilter getFilter()
  {
    try
    {
      if (filter == null)
      {
        filter = rawFilter.toSearchFilter();
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);
      setResponseData(de);
    }
    return filter;
  }

  @Override
  public final Set<String> getAttributes()
  {
    return attributes;
  }

  @Override
  public final void setAttributes(Set<String> attributes)
  {
    if (attributes == null)
    {
      this.attributes.clear();
    }
    else
    {
      this.attributes = attributes;
    }
  }

  @Override
  public final int getEntriesSent()
  {
    return entriesSent;
  }

  @Override
  public final int getReferencesSent()
  {
    return referencesSent;
  }

  @Override
  public final boolean returnEntry(Entry entry, List<Control> controls)
  {
    return returnEntry(entry, controls, true);
  }

  @Override
  public final boolean returnEntry(Entry entry, List<Control> controls,
                                   boolean evaluateAci)
  {
    boolean typesOnly = getTypesOnly();

    // See if the size limit has been exceeded.  If so, then don't send the
    // entry and indicate that the search should end.
    if (getSizeLimit() > 0 && getEntriesSent() >= getSizeLimit())
    {
      setResultCode(ResultCode.SIZE_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_SIZE_LIMIT_EXCEEDED.get(getSizeLimit()));
      return false;
    }

    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if (getTimeLimit() > 0
        && TimeThread.getTime() >= getTimeLimitExpiration())
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_TIME_LIMIT_EXCEEDED.get(getTimeLimit()));
      return false;
    }

    // Determine whether the provided entry is a subentry and if so whether it
    // should be returned.
    if (entry.isSubentry() || entry.isLDAPSubentry())
    {
      if (filterNeedsCheckingForSubentries)
      {
        filterIncludesSubentries = checkFilterForLDAPSubEntry(filter, 0);
        filterNeedsCheckingForSubentries = false;
      }

      if (getScope() != SearchScope.BASE_OBJECT
          && !filterIncludesSubentries
          && !isReturnSubentriesOnly())
      {
        return true;
      }
    }
    else if (isReturnSubentriesOnly())
    {
      // Subentries are visible and normal entries are not.
      return true;
    }

    // Determine whether to include the account usable control. If so, then
    // create it now.
    if (isIncludeUsableControl())
    {
      if (controls == null)
      {
        controls = new ArrayList<>(1);
      }

      try
      {
        // FIXME -- Need a way to enable PWP debugging.
        AuthenticationPolicyState state = AuthenticationPolicyState.forUser(
            entry, false);
        if (state.isPasswordPolicy())
        {
          PasswordPolicyState pwpState = (PasswordPolicyState) state;

          boolean isInactive = pwpState.isDisabled()
              || pwpState.isAccountExpired();
          boolean isLocked = pwpState.isLocked();
          boolean isReset = pwpState.mustChangePassword();
          boolean isExpired = pwpState.isPasswordExpired();

          if (isInactive || isLocked || isReset || isExpired)
          {
            int secondsBeforeUnlock = pwpState.getSecondsUntilUnlock();
            int remainingGraceLogins = pwpState.getGraceLoginsRemaining();
            controls
                .add(new AccountUsableResponseControl(isInactive, isReset,
                    isExpired, remainingGraceLogins, isLocked,
                    secondsBeforeUnlock));
          }
          else
          {
            int secondsBeforeExpiration = pwpState.getSecondsUntilExpiration();
            controls.add(new AccountUsableResponseControl(
                secondsBeforeExpiration));
          }
        }
        // Another type of authentication policy (e.g. PTA).
        else if (state.isDisabled())
        {
          controls.add(new AccountUsableResponseControl(false, false, false,
              -1, true, -1));
        }
        else
        {
          controls.add(new AccountUsableResponseControl(-1));
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    // Check to see if the entry can be read by the client.
    SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(entry, controls);
    if (evaluateAci && !getACIHandler().maySend(this, unfilteredSearchEntry))
    {
      return true;
    }

    // Make a copy of the entry and pare it down to only include the set
    // of requested attributes.

    // NOTE: that this copy will include the objectClass attribute.
    Entry filteredEntry =
        entry.filterEntry(getAttributes(), typesOnly,
            isVirtualAttributesOnly(), isRealAttributesOnly());


    // If there is a matched values control, then further pare down the entry
    // based on the filters that it contains.
    MatchedValuesControl matchedValuesControl = getMatchedValuesControl();
    if (matchedValuesControl != null && !typesOnly)
    {
      // First, look at the set of objectclasses.

      // NOTE: the objectClass attribute is also present and must be
      // dealt with later.
      AttributeType attrType = CoreSchema.getObjectClassAttributeType();
      Iterator<String> ocIterator = filteredEntry.getObjectClasses().values().iterator();
      while (ocIterator.hasNext())
      {
        ByteString ocName = ByteString.valueOfUtf8(ocIterator.next());
        if (! matchedValuesControl.valueMatches(attrType, ocName))
        {
          ocIterator.remove();
        }
      }


      // Next, the set of user attributes (incl. objectClass attribute).
      for (Map.Entry<AttributeType, List<Attribute>> e : filteredEntry
          .getUserAttributes().entrySet())
      {
        AttributeType t = e.getKey();
        List<Attribute> oldAttributes = e.getValue();
        List<Attribute> newAttributes = new ArrayList<>(oldAttributes.size());

        for (Attribute a : oldAttributes)
        {
          // Assume that the attribute will be either empty or contain
          // very few values.
          AttributeBuilder builder = new AttributeBuilder(a.getAttributeDescription());
          for (ByteString v : a)
          {
            if (matchedValuesControl.valueMatches(t, v))
            {
              builder.add(v);
            }
          }
          newAttributes.add(builder.toAttribute());
        }
        e.setValue(newAttributes);
      }


      // Then the set of operational attributes.
      for (Map.Entry<AttributeType, List<Attribute>> e : filteredEntry
          .getOperationalAttributes().entrySet())
      {
        AttributeType t = e.getKey();
        List<Attribute> oldAttributes = e.getValue();
        List<Attribute> newAttributes = new ArrayList<>(oldAttributes.size());

        for (Attribute a : oldAttributes)
        {
          // Assume that the attribute will be either empty or contain
          // very few values.
          AttributeBuilder builder = new AttributeBuilder(a.getAttributeDescription());
          for (ByteString v : a)
          {
            if (matchedValuesControl.valueMatches(t, v))
            {
              builder.add(v);
            }
          }
          newAttributes.add(builder.toAttribute());
        }
        e.setValue(newAttributes);
      }
    }


    // Convert the provided entry to a search result entry.
    SearchResultEntry filteredSearchEntry = new SearchResultEntry(
        filteredEntry, controls);

    // Strip out any attributes that the client does not have access to.

    // FIXME: need some way to prevent plugins from adding attributes or
    // values that the client is not permitted to see.
    if (evaluateAci)
    {
      getACIHandler().filterEntry(this, unfilteredSearchEntry, filteredSearchEntry);
    }

    // Invoke any search entry plugins that may be registered with the server.
    PluginResult.IntermediateResponse pluginResult =
         DirectoryServer.getPluginConfigManager().
              invokeSearchResultEntryPlugins(this, filteredSearchEntry);

    // Send the entry to the client.
    if (pluginResult.sendResponse())
    {
      // Log the entry sent to the client.
      logSearchResultEntry(this, filteredSearchEntry);

      try
      {
        sendSearchEntry(filteredSearchEntry);

        entriesSent++;
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        setResponseData(de);
        return false;
      }
    }

    return pluginResult.continueProcessing();
  }

  private AccessControlHandler<?> getACIHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  @Override
  public final boolean returnReference(DN dn, SearchResultReference reference)
  {
    return returnReference(dn, reference, true);
  }

  @Override
  public final boolean returnReference(DN dn, SearchResultReference reference,
                                       boolean evaluateAci)
  {
    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if (getTimeLimit() > 0
        && TimeThread.getTime() >= getTimeLimitExpiration())
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_TIME_LIMIT_EXCEEDED.get(getTimeLimit()));
      return false;
    }


    // See if we know that this client can't handle referrals.  If so, then
    // don't even try to send it.
    if (!isClientAcceptsReferrals()
        // See if the client has permission to read this reference.
        || (evaluateAci && !getACIHandler().maySend(dn, this, reference)))
    {
      return true;
    }


    // Invoke any search reference plugins that may be registered with the
    // server.
    PluginResult.IntermediateResponse pluginResult =
         DirectoryServer.getPluginConfigManager().
              invokeSearchResultReferencePlugins(this, reference);

    // Send the reference to the client.  Note that this could throw an
    // exception, which would indicate that the associated client can't handle
    // referrals.  If that't the case, then set a flag so we'll know not to try
    // to send any more.
    if (pluginResult.sendResponse())
    {
      // Log the entry sent to the client.
      logSearchResultReference(this, reference);

      try
      {
        if (sendSearchReference(reference))
        {
          referencesSent++;

          // FIXME -- Should the size limit apply here?
        }
        else
        {
          // We know that the client can't handle referrals, so we won't try to
          // send it any more.
          setClientAcceptsReferrals(false);
        }
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        setResponseData(de);
        return false;
      }
    }

    return pluginResult.continueProcessing();
  }

  @Override
  public final void sendSearchResultDone()
  {
    // Send the search result done message to the client.  We want to make sure
    // that this only gets sent once, and it's possible that this could be
    // multithreaded in the event of a persistent search, so do it safely.
    if (responseSent.compareAndSet(false, true))
    {
      logSearchResultDone(this);

      clientConnection.sendResponse(this);

      invokePostResponsePlugins();
    }
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.SEARCH;
  }

  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  @Override
  public void abort(CancelRequest cancelRequest)
  {
    if(cancelResult == null && this.cancelRequest == null)
    {
      this.cancelRequest = cancelRequest;
    }
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("SearchOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", baseDN=");
    buffer.append(rawBaseDN);
    buffer.append(", scope=");
    buffer.append(scope);
    buffer.append(", filter=");
    buffer.append(rawFilter);
    buffer.append(")");
  }

  @Override
  public void setTimeLimitExpiration(long timeLimitExpiration)
  {
    this.timeLimitExpiration = timeLimitExpiration;
  }

  @Override
  public boolean isReturnSubentriesOnly()
  {
    return returnSubentriesOnly;
  }

  @Override
  public void setReturnSubentriesOnly(boolean returnLDAPSubentries)
  {
    this.returnSubentriesOnly = returnLDAPSubentries;
  }

  @Override
  public MatchedValuesControl getMatchedValuesControl()
  {
    return matchedValuesControl;
  }

  @Override
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    this.matchedValuesControl = controls;
  }

  @Override
  public boolean isIncludeUsableControl()
  {
    return includeUsableControl;
  }

  @Override
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    this.includeUsableControl = includeUsableControl;
  }

  @Override
  public long getTimeLimitExpiration()
  {
    return timeLimitExpiration;
  }

  @Override
  public boolean isClientAcceptsReferrals()
  {
    return clientAcceptsReferrals;
  }

  @Override
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    this.clientAcceptsReferrals = clientAcceptReferrals;
  }

  @Override
  public boolean isSendResponse()
  {
    return sendResponse;
  }

  @Override
  public void setSendResponse(boolean sendResponse)
  {
    this.sendResponse = sendResponse;
  }

  @Override
  public boolean isRealAttributesOnly()
  {
    return this.realAttributesOnly;
  }

  @Override
  public boolean isVirtualAttributesOnly()
  {
    return this.virtualAttributesOnly;
  }

  @Override
  public void setRealAttributesOnly(boolean realAttributesOnly)
  {
    this.realAttributesOnly = realAttributesOnly;
  }

  @Override
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly)
  {
    this.virtualAttributesOnly = virtualAttributesOnly;
  }

  @Override
  public void sendSearchEntry(SearchResultEntry searchEntry)
      throws DirectoryException
  {
    getClientConnection().sendSearchEntry(this, searchEntry);
  }

  @Override
  public boolean sendSearchReference(SearchResultReference searchReference)
      throws DirectoryException
  {
    return getClientConnection().sendSearchReference(this, searchReference);
  }

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    logSearchRequest(this);

    setSendResponse(true);

    int timeLimit = getTimeLimit();
    long timeLimitExpiration;
    if (timeLimit <= 0)
    {
      timeLimitExpiration = Long.MAX_VALUE;
    }
    else
    {
      // FIXME -- Factor in the user's effective time limit.
      timeLimitExpiration = getProcessingStartTime() + (1000L * timeLimit);
    }
    setTimeLimitExpiration(timeLimitExpiration);

    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      if (!processOperationResult(getPluginConfigManager().invokePreParseSearchPlugins(this)))
      {
        return;
      }

      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      DN baseDN = getBaseDN();
      if (baseDN == null){
        return;
      }

      execute(this, baseDN);
    }
    catch(CanceledOperationException coe)
    {
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);
      cancelResult = new CancelResult(ResultCode.CANCELLED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED)
      {
        // If everything is successful to this point and it is not a persistent
        // search, then send the search result done message to the client.
        // Otherwise, we'll want to make the size and time limit values
        // unlimited to ensure that the remainder of the persistent search
        // isn't subject to those restrictions.
        if (isSendResponse())
        {
          sendSearchResultDone();
        }
        else
        {
          setSizeLimit(0);
          setTimeLimit(0);
        }
      }
      else if(cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        sendSearchResultDone();
      }

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }


  /** Invokes the post response plugins. */
  private void invokePostResponsePlugins()
  {
    // Invoke the post response plugins that have been registered with
    // the current operation
    getPluginConfigManager().invokePostResponseSearchPlugins(this);
  }

  @Override
  public void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_SEARCH_BASE_DOESNT_EXIST.get(getBaseDN()));
  }

  /**
   * Checks if the filter contains an equality element with the objectclass
   * attribute type and a value of "ldapSubentry" and if so sets
   * returnSubentriesOnly to <code>true</code>.
   *
   * @param filter
   *          The complete filter being checked, of which this filter may be a
   *          subset.
   * @param depth
   *          The current depth of the evaluation, which is used to prevent
   *          infinite recursion due to highly nested filters and eventually
   *          running out of stack space.
   * @return {@code true} if the filter references the sub-entry object class.
   */
  private boolean checkFilterForLDAPSubEntry(SearchFilter filter, int depth)
  {
    // Paranoid check to avoid recursion deep enough to provoke
    // the stack overflow. This should never happen because if
    // a given filter is too nested SearchFilter exception gets
    // raised long before this method is invoked.
    if (depth >= MAX_NESTED_FILTER_DEPTH)
    {
      if (logger.isTraceEnabled())
      {
        logger.trace("Exceeded maximum filter depth");
      }
      return false;
    }

    switch (filter.getFilterType())
    {
    case EQUALITY:
      if (filter.getAttributeType().isObjectClass())
      {
        ByteString v = filter.getAssertionValue();
        // FIXME : technically this is not correct since the presence
        // of draft oc would trigger rfc oc visibility and visa versa.
        String stringValueLC = toLowerCase(v.toString());
        if (OC_LDAP_SUBENTRY_LC.equals(stringValueLC) ||
            OC_SUBENTRY.equals(stringValueLC))
        {
          return true;
        }
      }
      break;
    case AND:
    case OR:
      for (SearchFilter f : filter.getFilterComponents())
      {
        if (checkFilterForLDAPSubEntry(f, depth + 1))
        {
          return true;
        }
      }
      break;
    }

    return false;
  }
}
