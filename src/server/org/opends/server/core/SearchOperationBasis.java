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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.AccountUsableResponseControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostResponseSearchOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  /**
   * Indicates whether a search result done response has been sent to the
   * client.
   */
  private AtomicBoolean responseSent;

  /** Indicates whether the client is able to handle referrals. */
  private boolean clientAcceptsReferrals;

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
  private DereferencePolicy derefPolicy;

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
  private List<Control> responseControls;

  /** The time that processing started on this operation. */
  private long processingStartTime;

  /** The time that processing ended on this operation. */
  private long processingStopTime;

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
                         DereferencePolicy derefPolicy, int sizeLimit,
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

    if (attributes == null)
    {
      this.attributes  = new LinkedHashSet<String>(0);
    }
    else
    {
      this.attributes  = attributes;
    }


    if (clientConnection.getSizeLimit() <= 0)
    {
      this.sizeLimit = sizeLimit;
    }
    else
    {
      if (sizeLimit <= 0)
      {
        this.sizeLimit = clientConnection.getSizeLimit();
      }
      else
      {
        this.sizeLimit = Math.min(sizeLimit, clientConnection.getSizeLimit());
      }
    }


    if (clientConnection.getTimeLimit() <= 0)
    {
      this.timeLimit = timeLimit;
    }
    else
    {
      if (timeLimit <= 0)
      {
        this.timeLimit = clientConnection.getTimeLimit();
      }
      else
      {
        this.timeLimit = Math.min(timeLimit, clientConnection.getTimeLimit());
      }
    }


    baseDN                 = null;
    filter                 = null;
    entriesSent            = 0;
    referencesSent         = 0;
    responseControls       = new ArrayList<Control>();
    cancelRequest          = null;
    clientAcceptsReferrals = true;
    includeUsableControl   = false;
    responseSent           = new AtomicBoolean(false);
    returnSubentriesOnly   = false;
    matchedValuesControl   = null;
    realAttributesOnly     = false;
    virtualAttributesOnly  = false;
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
                         DereferencePolicy derefPolicy, int sizeLimit,
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

    if (attributes == null)
    {
      this.attributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.attributes  = attributes;
    }

    rawBaseDN = ByteString.valueOf(baseDN.toString());
    rawFilter = new LDAPFilter(filter);


    if (clientConnection.getSizeLimit() <= 0)
    {
      this.sizeLimit = sizeLimit;
    }
    else
    {
      if (sizeLimit <= 0)
      {
        this.sizeLimit = clientConnection.getSizeLimit();
      }
      else
      {
        this.sizeLimit = Math.min(sizeLimit, clientConnection.getSizeLimit());
      }
    }


    if (clientConnection.getTimeLimit() <= 0)
    {
      this.timeLimit = timeLimit;
    }
    else
    {
      if (timeLimit <= 0)
      {
        this.timeLimit = clientConnection.getTimeLimit();
      }
      else
      {
        this.timeLimit = Math.min(timeLimit, clientConnection.getTimeLimit());
      }
    }


    entriesSent            = 0;
    referencesSent         = 0;
    responseControls       = new ArrayList<Control>();
    cancelRequest          = null;
    clientAcceptsReferrals = true;
    includeUsableControl   = false;
    responseSent           = new AtomicBoolean(false);
    returnSubentriesOnly   = false;
    matchedValuesControl   = null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final ByteString getRawBaseDN()
  {
    return rawBaseDN;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final void setRawBaseDN(ByteString rawBaseDN)
  {
    this.rawBaseDN = rawBaseDN;

    baseDN = null;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public final DN getBaseDN()
  {
    try
    {
      if (baseDN == null)
      {
        baseDN = DN.decode(rawBaseDN);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResultCode(de.getResultCode());
      appendErrorMessage(de.getMessageObject());
      setMatchedDN(de.getMatchedDN());
      setReferralURLs(de.getReferralURLs());
    }
    return baseDN;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public final void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final SearchScope getScope()
  {
    return scope;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setScope(SearchScope scope)
  {
    this.scope = scope;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final DereferencePolicy getDerefPolicy()
  {
    return derefPolicy;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setDerefPolicy(DereferencePolicy derefPolicy)
  {
    this.derefPolicy = derefPolicy;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final int getSizeLimit()
  {
    return sizeLimit;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setSizeLimit(int sizeLimit)
  {
    this.sizeLimit = sizeLimit;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final int getTimeLimit()
  {
    return timeLimit;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setTimeLimit(int timeLimit)
  {
    this.timeLimit = timeLimit;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean getTypesOnly()
  {
    return typesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setTypesOnly(boolean typesOnly)
  {
    this.typesOnly = typesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final RawFilter getRawFilter()
  {
    return rawFilter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void setRawFilter(RawFilter rawFilter)
  {
    this.rawFilter = rawFilter;

    filter = null;
  }

  /**
   * {@inheritDoc}
   */
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResultCode(de.getResultCode());
      appendErrorMessage(de.getMessageObject());
      setMatchedDN(de.getMatchedDN());
      setReferralURLs(de.getReferralURLs());
    }
    return filter;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final Set<String> getAttributes()
  {
    return attributes;
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public final int getEntriesSent()
  {
    return entriesSent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final int getReferencesSent()
  {
    return referencesSent;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean returnEntry(Entry entry, List<Control> controls)
  {
    return returnEntry(entry, controls, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean returnEntry(Entry entry, List<Control> controls,
                                   boolean evaluateAci)
  {
    boolean typesOnly = getTypesOnly();

    // See if the size limit has been exceeded.  If so, then don't send the
    // entry and indicate that the search should end.
    if ((getSizeLimit() > 0) && (getEntriesSent() >= getSizeLimit()))
    {
      setResultCode(ResultCode.SIZE_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_SIZE_LIMIT_EXCEEDED.get(getSizeLimit()));
      return false;
    }

    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if ((getTimeLimit() > 0) && (TimeThread.getTime() >=
                                                getTimeLimitExpiration()))
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_TIME_LIMIT_EXCEEDED.get(getTimeLimit()));
      return false;
    }

    // Determine whether the provided entry is a subentry and if so whether it
    // should be returned.
    if (entry.isSubentry() || entry.isLDAPSubentry())
    {
      if (filterNeedsCheckingForSubentries == true)
      {
        filterIncludesSubentries = checkFilterForLDAPSubEntry(filter, 0);
        filterNeedsCheckingForSubentries = false;
      }

      if ((getScope() != SearchScope.BASE_OBJECT)
          && !filterIncludesSubentries
          && !isReturnSubentriesOnly())
      {
        return true;
      }
    }
    else
    {
      if (isReturnSubentriesOnly())
      {
        // Subentries are visible and normal entries are not.
        return true;
      }
    }

    // Determine whether to include the account usable control. If so, then
    // create it now.
    if (isIncludeUsableControl())
    {
      if (controls == null)
      {
        controls = new ArrayList<Control>(1);
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
          boolean isLocked = pwpState.lockedDueToFailures()
              || pwpState.lockedDueToMaximumResetAge()
              || pwpState.lockedDueToIdleInterval();
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
        else
        {
          // Another type of authentication policy (e.g. PTA).
          if (state.isDisabled())
          {
            controls.add(new AccountUsableResponseControl(false, false, false,
                -1, true, -1));
          }
          else
          {
            controls.add(new AccountUsableResponseControl(-1));
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Check to see if the entry can be read by the client.
    SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(entry,
        controls);
    if (evaluateAci)
    {
      if (AccessControlConfigManager.getInstance().getAccessControlHandler()
          .maySend(this, unfilteredSearchEntry) == false)
      {
        return true;
      }
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
    if ((matchedValuesControl != null) && (! typesOnly))
    {
      // First, look at the set of objectclasses.

      // NOTE: the objectClass attribute is also present and must be
      // dealt with later.
      AttributeType attrType = DirectoryServer.getObjectClassAttributeType();
      Iterator<String> ocIterator =
           filteredEntry.getObjectClasses().values().iterator();
      while (ocIterator.hasNext())
      {
        String ocName = ocIterator.next();
        AttributeValue v =
            AttributeValues.create(attrType,ocName);
        if (! matchedValuesControl.valueMatches(attrType, v))
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
        List<Attribute> newAttributes =
            new ArrayList<Attribute>(oldAttributes.size());

        for (Attribute a : oldAttributes)
        {
          // Assume that the attribute will be either empty or contain
          // very few values.
          AttributeBuilder builder = new AttributeBuilder(a, true);
          for (AttributeValue v : a)
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
        List<Attribute> newAttributes =
            new ArrayList<Attribute>(oldAttributes.size());

        for (Attribute a : oldAttributes)
        {
          // Assume that the attribute will be either empty or contain
          // very few values.
          AttributeBuilder builder = new AttributeBuilder(a, true);
          for (AttributeValue v : a)
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
      AccessControlConfigManager.getInstance().getAccessControlHandler()
          .filterEntry(this, unfilteredSearchEntry, filteredSearchEntry);
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

        incrementEntriesSent();
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);
        return false;
      }
    }

    return pluginResult.continueProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean returnReference(DN dn, SearchResultReference reference)
  {
    return returnReference(dn, reference, true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean returnReference(DN dn, SearchResultReference reference,
                                       boolean evaluateAci)
  {
    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if ((getTimeLimit() > 0) && (TimeThread.getTime() >=
                                                getTimeLimitExpiration()))
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(ERR_SEARCH_TIME_LIMIT_EXCEEDED.get(getTimeLimit()));
      return false;
    }


    // See if we know that this client can't handle referrals.  If so, then
    // don't even try to send it.
    if (! isClientAcceptsReferrals())
    {
      return true;
    }


    // See if the client has permission to read this reference.
    if (evaluateAci)
    {
      if (AccessControlConfigManager.getInstance()
        .getAccessControlHandler().maySend(dn, this, reference) == false) {
        return true;
      }
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
          incrementReferencesSent();

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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);
        return false;
      }
    }

    return pluginResult.continueProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void sendSearchResultDone()
  {
    // Send the search result done message to the client.  We want to make sure
    // that this only gets sent once, and it's possible that this could be
    // multithreaded in the event of a persistent search, so do it safely.
    if (responseSent.compareAndSet(false, true))
    {
      // Log the search result.
      logSearchResultDone(this);

      // Send the response to the client.
      clientConnection.sendResponse(this);

      // Invoke the post-response search plugins.
      invokePostResponsePlugins();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.SEARCH;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String attrs;
    if ((attributes == null) || attributes.isEmpty())
    {
      attrs = null;
    }
    else
    {
      StringBuilder attrBuffer = new StringBuilder();
      Iterator<String> iterator = attributes.iterator();
      attrBuffer.append(iterator.next());

      while (iterator.hasNext())
      {
        attrBuffer.append(", ");
        attrBuffer.append(iterator.next());
      }

      attrs = attrBuffer.toString();
    }

    return new String[][]
    {
      new String[] { LOG_ELEMENT_BASE_DN, String.valueOf(rawBaseDN) },
      new String[] { LOG_ELEMENT_SCOPE, String.valueOf(scope) },
      new String[] { LOG_ELEMENT_SIZE_LIMIT, String.valueOf(sizeLimit) },
      new String[] { LOG_ELEMENT_TIME_LIMIT, String.valueOf(timeLimit) },
      new String[] { LOG_ELEMENT_FILTER, String.valueOf(rawFilter) },
      new String[] { LOG_ELEMENT_REQUESTED_ATTRIBUTES, attrs }
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
    MessageBuilder errorMessageBuffer = getErrorMessage();
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
      new String[] { LOG_ELEMENT_ENTRIES_SENT, String.valueOf(entriesSent) },
      new String[] { LOG_ELEMENT_REFERENCES_SENT,
                     String.valueOf(referencesSent ) },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
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
  public void abort(CancelRequest cancelRequest)
  {
    if(cancelResult == null && this.cancelRequest == null)
    {
      this.cancelRequest = cancelRequest;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {
    buffer.append("SearchOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", baseDN=");
    buffer.append(rawBaseDN);
    buffer.append(", scope=");
    buffer.append(scope.toString());
    buffer.append(", filter=");
    buffer.append(rawFilter.toString());
    buffer.append(")");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTimeLimitExpiration(Long timeLimitExpiration){
    this.timeLimitExpiration = timeLimitExpiration;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isReturnSubentriesOnly()
  {
    return returnSubentriesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setReturnSubentriesOnly(boolean returnLDAPSubentries)
  {
    this.returnSubentriesOnly = returnLDAPSubentries;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MatchedValuesControl getMatchedValuesControl()
  {
    return matchedValuesControl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    this.matchedValuesControl = controls;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isIncludeUsableControl()
  {
    return includeUsableControl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    this.includeUsableControl = includeUsableControl;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Long getTimeLimitExpiration()
  {
    return timeLimitExpiration;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClientAcceptsReferrals()
  {
    return clientAcceptsReferrals;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    this.clientAcceptsReferrals = clientAcceptReferrals;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void incrementEntriesSent()
  {
    entriesSent++;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void incrementReferencesSent()
  {
    referencesSent++;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSendResponse()
  {
    return sendResponse;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSendResponse(boolean sendResponse)
  {
    this.sendResponse = sendResponse;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isRealAttributesOnly()
  {
    return this.realAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVirtualAttributesOnly()
  {
    return this.virtualAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setRealAttributesOnly(boolean realAttributesOnly)
  {
    this.realAttributesOnly = realAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly)
  {
    this.virtualAttributesOnly = virtualAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sendSearchEntry(SearchResultEntry searchEntry)
      throws DirectoryException
  {
    getClientConnection().sendSearchEntry(this, searchEntry);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean sendSearchReference(SearchResultReference searchReference)
      throws DirectoryException
  {
    return getClientConnection().sendSearchReference(this, searchReference);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    // Log the search request message.
    logSearchRequest(this);

    setSendResponse(true);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();

    int timeLimit = getTimeLimit();
    Long timeLimitExpiration;
    if (timeLimit <= 0)
    {
      timeLimitExpiration = Long.MAX_VALUE;
    }
    else
    {
      // FIXME -- Factor in the user's effective time limit.
      timeLimitExpiration =
          getProcessingStartTime() + (1000L * timeLimit);
    }
    setTimeLimitExpiration(timeLimitExpiration);

    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseSearchPlugins(this);

      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
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


      // Retrieve the network group attached to the client connection
      // and get a workflow to process the operation.
      NetworkGroup ng = getClientConnection().getNetworkGroup();
      Workflow workflow = ng.getWorkflowCandidate(baseDN);
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        return;
      }
      workflow.execute(this);
    }
    catch(CanceledOperationException coe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, coe);
      }

      setResultCode(ResultCode.CANCELED);
      cancelResult = new CancelResult(ResultCode.CANCELED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELED)
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


  /**
   * Invokes the post response plugins.
   */
  private void invokePostResponsePlugins()
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Invoke the post response plugins that have been registered with
    // the current operation
    pluginConfigManager.invokePostResponseSearchPlugins(this);
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflows were found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    Message message =
            ERR_SEARCH_BASE_DOESNT_EXIST.get(String.valueOf(getBaseDN()));
    appendErrorMessage(message);
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
      if (debugEnabled())
      {
        TRACER.debugError("Exceeded maximum filter depth");
      }
      return false;
    }

    switch (filter.getFilterType())
    {
    case EQUALITY:
      if (filter.getAttributeType().isObjectClassType())
      {
        AttributeValue v = filter.getAssertionValue();
        // FIXME : technically this is not correct since the presense
        // of draft oc would trigger rfc oc visibility and visa versa.
        String stringValueLC = toLowerCase(v.getValue().toString());
        if (stringValueLC.equals(OC_LDAP_SUBENTRY_LC) ||
            stringValueLC.equals(OC_SUBENTRY))
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
