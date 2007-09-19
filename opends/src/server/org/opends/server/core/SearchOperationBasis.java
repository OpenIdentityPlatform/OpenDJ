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
package org.opends.server.core;

import org.opends.messages.MessageBuilder;
import org.opends.messages.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.api.plugin.SearchEntryPluginResult;
import org.opends.server.api.plugin.SearchReferencePluginResult;
import org.opends.server.controls.AccountUsableResponseControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;
import org.opends.server.types.operation.PostResponseSearchOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.TimeThread;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

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

  // Indicates whether a search result done response has been sent to the
  // client.
  private AtomicBoolean responseSent;

  // Indicates whether the client is able to handle referrals.
  private boolean clientAcceptsReferrals;

  // Indicates whether to include the account usable control with search result
  // entries.
  private boolean includeUsableControl;

  // Indicates whether to only real attributes should be returned.
  private boolean realAttributesOnly;

  // Indicates whether LDAP subentries should be returned.
  private boolean returnLDAPSubentries;

  // Indicates whether to include attribute types only or both types and values.
  private boolean typesOnly;

  // Indicates whether to only virtual attributes should be returned.
  private boolean virtualAttributesOnly;

  // The raw, unprocessed base DN as included in the request from the client.
  private ByteString rawBaseDN;

  // The cancel request that has been issued for this search operation.
  private CancelRequest cancelRequest;

  // The dereferencing policy for the search operation.
  private DereferencePolicy derefPolicy;

  // The base DN for the search operation.
  private DN baseDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The number of entries that have been sent to the client.
  private int entriesSent;

  // The number of search result references that have been sent to the client.
  private int referencesSent;

  // The size limit for the search operation.
  private int sizeLimit;

  // The time limit for the search operation.
  private int timeLimit;

  // The raw, unprocessed filter as included in the request from the client.
  private RawFilter rawFilter;

  // The set of attributes that should be returned in matching entries.
  private LinkedHashSet<String> attributes;

  // The set of response controls for this search operation.
  private List<Control> responseControls;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

  // The time that the search time limit has expired.
  private long timeLimitExpiration;

  // The matched values control associated with this search operation.
  private MatchedValuesControl matchedValuesControl;

  // The persistent search associated with this search operation.
  private PersistentSearch persistentSearch;

  // The search filter for the search operation.
  private SearchFilter filter;

  // The search scope for the search operation.
  private SearchScope scope;

  // Indicates wether to send the search result done to the client or not
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
                         LinkedHashSet<String> attributes)
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
    persistentSearch       = null;
    returnLDAPSubentries   = false;
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
                         LinkedHashSet<String> attributes)
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

    rawBaseDN = new ASN1OctetString(baseDN.toString());
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
    persistentSearch       = null;
    returnLDAPSubentries   = false;
    matchedValuesControl   = null;
  }



  /**
   * {@inheritDoc}
   */
  public final ByteString getRawBaseDN()
  {
    return rawBaseDN;
  }



  /**
   * {@inheritDoc}
   */
  public final void setRawBaseDN(ByteString rawBaseDN)
  {
    this.rawBaseDN = rawBaseDN;

    baseDN = null;
  }


  /**
   * {@inheritDoc}
   */
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
  public final void setBaseDN(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * {@inheritDoc}
   */
  public final SearchScope getScope()
  {
    return scope;
  }

  /**
   * {@inheritDoc}
   */
  public final void setScope(SearchScope scope)
  {
    this.scope = scope;
  }

  /**
   * {@inheritDoc}
   */
  public final DereferencePolicy getDerefPolicy()
  {
    return derefPolicy;
  }

  /**
   * {@inheritDoc}
   */
  public final void setDerefPolicy(DereferencePolicy derefPolicy)
  {
    this.derefPolicy = derefPolicy;
  }

  /**
   * {@inheritDoc}
   */
  public final int getSizeLimit()
  {
    return sizeLimit;
  }

  /**
   * {@inheritDoc}
   */
  public final void setSizeLimit(int sizeLimit)
  {
    this.sizeLimit = sizeLimit;
  }

  /**
   * {@inheritDoc}
   */
  public final int getTimeLimit()
  {
    return timeLimit;
  }

  /**
   * {@inheritDoc}
   */
  public final void setTimeLimit(int timeLimit)
  {
    this.timeLimit = timeLimit;
  }

  /**
   * {@inheritDoc}
   */
  public final boolean getTypesOnly()
  {
    return typesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public final void setTypesOnly(boolean typesOnly)
  {
    this.typesOnly = typesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public final RawFilter getRawFilter()
  {
    return rawFilter;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawFilter(RawFilter rawFilter)
  {
    this.rawFilter = rawFilter;

    filter = null;
  }

  /**
   * {@inheritDoc}
   */
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
  public final LinkedHashSet<String> getAttributes()
  {
    return attributes;
  }

  /**
   * {@inheritDoc}
   */
  public final void setAttributes(LinkedHashSet<String> attributes)
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
  public final int getEntriesSent()
  {
    return entriesSent;
  }

  /**
   * {@inheritDoc}
   */
  public final int getReferencesSent()
  {
    return referencesSent;
  }

  /**
   * {@inheritDoc}
   */
  public final boolean returnEntry(Entry entry, List<Control> controls)
  {
    boolean typesOnly = getTypesOnly();
    // See if the operation has been abandoned.  If so, then don't send the
    // entry and indicate that the search should end.
    if (getCancelRequest() != null)
    {
      setResultCode(ResultCode.CANCELED);
      return false;
    }

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
    if ((getScope() != SearchScope.BASE_OBJECT) &&
        (! isReturnLDAPSubentries()) &&
        entry.isLDAPSubentry())
    {
      // Check to see if the filter contains an equality element with the
      // objectclass attribute type and a value of "ldapSubentry".  If so, then
      // we'll return it anyway.  Technically, this isn't part of the
      // specification so we don't need to get carried away with really in-depth
      // checks.
      SearchFilter filter = getFilter();
      switch (filter.getFilterType())
      {
        case AND:
        case OR:
          for (SearchFilter f : filter.getFilterComponents())
          {
            if ((f.getFilterType() == FilterType.EQUALITY) &&
                (f.getAttributeType().isObjectClassType()))
            {
              AttributeValue v = f.getAssertionValue();
              if (toLowerCase(v.getStringValue()).equals("ldapsubentry"))
              {
                setReturnLDAPSubentries(true);
              }
              break;
            }
          }
          break;
        case EQUALITY:
          AttributeType t = filter.getAttributeType();
          if (t.isObjectClassType())
          {
            AttributeValue v = filter.getAssertionValue();
            if (toLowerCase(v.getStringValue()).equals("ldapsubentry"))
            {
              setReturnLDAPSubentries(true);
            }
          }
          break;
      }

      if (! isReturnLDAPSubentries())
      {
        // We still shouldn't return it even based on the filter.  Just throw it
        // away without doing anything.
        return true;
      }
    }


    // Determine whether to include the account usable control.  If so, then
    // create it now.
    if (isIncludeUsableControl())
    {
      try
      {
        // FIXME -- Need a way to enable PWP debugging.
        PasswordPolicyState pwpState = new PasswordPolicyState(entry, false,
                                                               false);

        boolean isInactive           = pwpState.isDisabled() ||
                                       pwpState.isAccountExpired();
        boolean isLocked             = pwpState.lockedDueToFailures() ||
                                       pwpState.lockedDueToMaximumResetAge() ||
                                       pwpState.lockedDueToIdleInterval();
        boolean isReset              = pwpState.mustChangePassword();
        boolean isExpired            = pwpState.isPasswordExpired();

        if (isInactive || isLocked || isReset || isExpired)
        {
          int secondsBeforeUnlock  = pwpState.getSecondsUntilUnlock();
          int remainingGraceLogins = pwpState.getGraceLoginsRemaining();

          if (controls == null)
          {
            controls = new ArrayList<Control>(1);
          }

          controls.add(new AccountUsableResponseControl(isInactive, isReset,
                                isExpired, remainingGraceLogins, isLocked,
                                secondsBeforeUnlock));
        }
        else
        {
          if (controls == null)
          {
            controls = new ArrayList<Control>(1);
          }

          int secondsBeforeExpiration = pwpState.getSecondsUntilExpiration();
          controls.add(new AccountUsableResponseControl(
                                secondsBeforeExpiration));
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
    SearchResultEntry tmpSearchEntry = new SearchResultEntry(entry,
        controls);
    if (AccessControlConfigManager.getInstance()
        .getAccessControlHandler().maySend(this, tmpSearchEntry) == false) {
      return true;
    }

    // Make a copy of the entry and pare it down to only include the set
    // of
    // requested attributes.
    Entry entryToReturn;
    if ((getAttributes() == null) || getAttributes().isEmpty())
    {
      entryToReturn = entry.duplicateWithoutOperationalAttributes(typesOnly,
                                                                  true);
    }
    else
    {
      entryToReturn = entry.duplicateWithoutAttributes();

      for (String attrName : getAttributes())
      {
        if (attrName.equals("*"))
        {
          // This is a special placeholder indicating that all user attributes
          // should be returned.
          if (typesOnly)
          {
            // First, add the placeholder for the objectclass attribute.
            AttributeType ocType =
                 DirectoryServer.getObjectClassAttributeType();
            List<Attribute> ocList = new ArrayList<Attribute>(1);
            ocList.add(new Attribute(ocType));
            entryToReturn.putAttribute(ocType, ocList);
          }
          else
          {
            // First, add the objectclass attribute.
            Attribute ocAttr = entry.getObjectClassAttribute();
            try
            {
              if (ocAttr != null)
                entryToReturn.setObjectClasses(ocAttr.getValues());
            }
            catch (DirectoryException e)
            {
              // We cannot get this exception because the object classes have
              // already been validated in the entry they came from.
            }
          }

          // Next iterate through all the user attributes and include them.
          for (AttributeType t : entry.getUserAttributes().keySet())
          {
            List<Attribute> attrList =
                 entry.duplicateUserAttribute(t, null, typesOnly);
            entryToReturn.putAttribute(t, attrList);
          }

          continue;
        }
        else if (attrName.equals("+"))
        {
          // This is a special placeholder indicating that all operational
          // attributes should be returned.
          for (AttributeType t : entry.getOperationalAttributes().keySet())
          {
            List<Attribute> attrList =
                 entry.duplicateOperationalAttribute(t, null, typesOnly);
            entryToReturn.putAttribute(t, attrList);
          }

          continue;
        }

        String lowerName;
        HashSet<String> options;
        int semicolonPos = attrName.indexOf(';');
        if (semicolonPos > 0)
        {
          lowerName = toLowerCase(attrName.substring(0, semicolonPos));
          int nextPos = attrName.indexOf(';', semicolonPos+1);
          options = new HashSet<String>();
          while (nextPos > 0)
          {
            options.add(attrName.substring(semicolonPos+1, nextPos));

            semicolonPos = nextPos;
            nextPos = attrName.indexOf(';', semicolonPos+1);
          }

          options.add(attrName.substring(semicolonPos+1));
        }
        else
        {
          lowerName = toLowerCase(attrName);
          options = null;
        }


        AttributeType attrType = DirectoryServer.getAttributeType(lowerName);
        if (attrType == null)
        {
          boolean added = false;
          for (AttributeType t : entry.getUserAttributes().keySet())
          {
            if (t.hasNameOrOID(lowerName))
            {
              List<Attribute> attrList =
                   entry.duplicateUserAttribute(t, options, typesOnly);
              if (attrList != null)
              {
                entryToReturn.putAttribute(t, attrList);

                added = true;
                break;
              }
            }
          }

          if (added)
          {
            continue;
          }

          for (AttributeType t : entry.getOperationalAttributes().keySet())
          {
            if (t.hasNameOrOID(lowerName))
            {
              List<Attribute> attrList =
                   entry.duplicateOperationalAttribute(t, options, typesOnly);
              if (attrList != null)
              {
                entryToReturn.putAttribute(t, attrList);

                break;
              }
            }
          }
        }
        else
        {
          if (attrType.isObjectClassType()) {
            if (typesOnly)
            {
              AttributeType ocType =
                   DirectoryServer.getObjectClassAttributeType();
              List<Attribute> ocList = new ArrayList<Attribute>(1);
              ocList.add(new Attribute(ocType));
              entryToReturn.putAttribute(ocType, ocList);
            }
            else
            {
              List<Attribute> attrList = new ArrayList<Attribute>(1);
              attrList.add(entry.getObjectClassAttribute());
              entryToReturn.putAttribute(attrType, attrList);
            }
          }
          else
          {
            List<Attribute> attrList =
                 entry.duplicateOperationalAttribute(attrType, options,
                                                     typesOnly);
            if (attrList == null)
            {
              attrList = entry.duplicateUserAttribute(attrType, options,
                                                      typesOnly);
            }
            if (attrList != null)
            {
              entryToReturn.putAttribute(attrType, attrList);
            }
          }
        }
      }
    }

    if (isRealAttributesOnly())
    {
      entryToReturn.stripVirtualAttributes();
    }
    else if (isVirtualAttributesOnly())
    {
      entryToReturn.stripRealAttributes();
    }

    // If there is a matched values control, then further pare down the entry
    // based on the filters that it contains.
    MatchedValuesControl matchedValuesControl = getMatchedValuesControl();
    if ((matchedValuesControl != null) && (! typesOnly))
    {
      // First, look at the set of objectclasses.
      AttributeType attrType = DirectoryServer.getObjectClassAttributeType();
      Iterator<String> ocIterator =
           entryToReturn.getObjectClasses().values().iterator();
      while (ocIterator.hasNext())
      {
        String ocName = ocIterator.next();
        AttributeValue v = new AttributeValue(attrType,
                                              new ASN1OctetString(ocName));
        if (! matchedValuesControl.valueMatches(attrType, v))
        {
          ocIterator.remove();
        }
      }


      // Next, the set of user attributes.
      for (AttributeType t : entryToReturn.getUserAttributes().keySet())
      {
        for (Attribute a : entryToReturn.getUserAttribute(t))
        {
          Iterator<AttributeValue> valueIterator = a.getValues().iterator();
          while (valueIterator.hasNext())
          {
            AttributeValue v = valueIterator.next();
            if (! matchedValuesControl.valueMatches(t, v))
            {
              valueIterator.remove();
            }
          }
        }
      }


      // Then the set of operational attributes.
      for (AttributeType t : entryToReturn.getOperationalAttributes().keySet())
      {
        for (Attribute a : entryToReturn.getOperationalAttribute(t))
        {
          Iterator<AttributeValue> valueIterator = a.getValues().iterator();
          while (valueIterator.hasNext())
          {
            AttributeValue v = valueIterator.next();
            if (! matchedValuesControl.valueMatches(t, v))
            {
              valueIterator.remove();
            }
          }
        }
      }
    }


    // Convert the provided entry to a search result entry.
    SearchResultEntry searchEntry = new SearchResultEntry(entryToReturn,
                                                          controls);

    // Strip out any attributes that the client does not have access to.

    // FIXME: need some way to prevent plugins from adding attributes or
    // values that the client is not permitted to see.
    searchEntry = AccessControlConfigManager.getInstance()
        .getAccessControlHandler().filterEntry(this, searchEntry);

    // Invoke any search entry plugins that may be registered with the server.
    SearchEntryPluginResult pluginResult =
         DirectoryServer.getPluginConfigManager().
              invokeSearchResultEntryPlugins(this, searchEntry);
    if (pluginResult.connectionTerminated())
    {
      // We won't attempt to send this entry, and we won't continue with
      // any processing.  Just update the operation to indicate that it was
      // cancelled and return false.
      setResultCode(ResultCode.CANCELED);
      appendErrorMessage(ERR_CANCELED_BY_SEARCH_ENTRY_DISCONNECT.get(
              String.valueOf(entry.getDN())));
      return false;
    }


    // Send the entry to the client.
    if (pluginResult.sendEntry())
    {
      try
      {
        sendSearchEntry(searchEntry);
        // Log the entry sent to the client.
        logSearchResultEntry(this, searchEntry);

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

    return pluginResult.continueSearch();
  }

  /**
   * {@inheritDoc}
   */
  public final boolean returnReference(DN dn, SearchResultReference reference)
  {
    // See if the operation has been abandoned.  If so, then don't send the
    // reference and indicate that the search should end.
    if (getCancelRequest() != null)
    {
      setResultCode(ResultCode.CANCELED);
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


    // See if we know that this client can't handle referrals.  If so, then
    // don't even try to send it.
    if (! isClientAcceptsReferrals())
    {
      return true;
    }


    // See if the client has permission to read this reference.
    if (AccessControlConfigManager.getInstance()
        .getAccessControlHandler().maySend(dn, this, reference) == false) {
      return true;
    }


    // Invoke any search reference plugins that may be registered with the
    // server.
    SearchReferencePluginResult pluginResult =
         DirectoryServer.getPluginConfigManager().
              invokeSearchResultReferencePlugins(this, reference);
    if (pluginResult.connectionTerminated())
    {
      // We won't attempt to send this entry, and we won't continue with
      // any processing.  Just update the operation to indicate that it was
      // cancelled and return false.
      setResultCode(ResultCode.CANCELED);
      appendErrorMessage(ERR_CANCELED_BY_SEARCH_REF_DISCONNECT.get(
              String.valueOf(reference.getReferralURLString())));
      return false;
    }


    // Send the reference to the client.  Note that this could throw an
    // exception, which would indicate that the associated client can't handle
    // referrals.  If that't the case, then set a flag so we'll know not to try
    // to send any more.
    if (pluginResult.sendReference())
    {
      try
      {
        if (sendSearchReference(reference))
        {
          // Log the entry sent to the client.
          logSearchResultReference(this, reference);
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

    return pluginResult.continueSearch();
  }

  /**
   * {@inheritDoc}
   */
  public final void sendSearchResultDone()
  {
    // Send the search result done message to the client.  We want to make sure
    // that this only gets sent once, and it's possible that this could be
    // multithreaded in the event of a persistent search, so do it safely.
    if (responseSent.compareAndSet(false, true))
    {
      // Send the response to the client.
      clientConnection.sendResponse(this);

      // Log the search result.
      logSearchResultDone(this);


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
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, Message message
  )
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

    clientConnection.disconnect(disconnectReason, sendNotification, message);
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
  public final CancelResult cancel(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;

    if (persistentSearch != null)
    {
      DirectoryServer.deregisterPersistentSearch(persistentSearch);
      persistentSearch = null;
    }

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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
  public
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
  public void setTimeLimitExpiration(Long timeLimitExpiration){
    this.timeLimitExpiration = timeLimitExpiration;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isReturnLDAPSubentries()
  {
    return returnLDAPSubentries;
  }

  /**
   * {@inheritDoc}
   */
  public void setReturnLDAPSubentries(boolean returnLDAPSubentries)
  {
    this.returnLDAPSubentries = returnLDAPSubentries;
  }

  /**
   * {@inheritDoc}
   */
  public MatchedValuesControl getMatchedValuesControl()
  {
    return matchedValuesControl;
  }

  /**
   * {@inheritDoc}
   */
  public void setMatchedValuesControl(MatchedValuesControl controls)
  {
    this.matchedValuesControl = controls;
  }

  /**
   * {@inheritDoc}
   */
  public PersistentSearch getPersistentSearch()
  {
    return persistentSearch;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isIncludeUsableControl()
  {
    return includeUsableControl;
  }

  /**
   * {@inheritDoc}
   */
  public void setIncludeUsableControl(boolean includeUsableControl)
  {
    this.includeUsableControl = includeUsableControl;
  }

  /**
   * {@inheritDoc}
   */
  public void setPersistentSearch(PersistentSearch psearch)
  {
    this.persistentSearch = psearch;
  }

  /**
   * {@inheritDoc}
   */
  public Long getTimeLimitExpiration()
  {
    return timeLimitExpiration;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isClientAcceptsReferrals()
  {
    return clientAcceptsReferrals;
  }

  /**
   * {@inheritDoc}
   */
  public void setClientAcceptsReferrals(boolean clientAcceptReferrals)
  {
    this.clientAcceptsReferrals = clientAcceptReferrals;
  }

  /**
   * {@inheritDoc}
   */
  public void incrementEntriesSent()
  {
    entriesSent++;
  }

  /**
   * {@inheritDoc}
   */
  public void incrementReferencesSent()
  {
    referencesSent++;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSendResponse()
  {
    return sendResponse;
  }

  /**
   * {@inheritDoc}
   */
  public void setSendResponse(boolean sendResponse)
  {
    this.sendResponse = sendResponse;

  }

  /**
   * {@inheritDoc}
   */
  public boolean isRealAttributesOnly()
  {
    return this.realAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isVirtualAttributesOnly()
  {
    return this.virtualAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public void setRealAttributesOnly(boolean realAttributesOnly)
  {
    this.realAttributesOnly = realAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public void setVirtualAttributesOnly(boolean virtualAttributesOnly)
  {
    this.virtualAttributesOnly = virtualAttributesOnly;
  }

  /**
   * {@inheritDoc}
   */
  public void sendSearchEntry(SearchResultEntry searchEntry)
    throws DirectoryException
    {
    getClientConnection().sendSearchEntry(this, searchEntry);
  }

  /**
   * {@inheritDoc}
   */
  public boolean sendSearchReference(SearchResultReference searchReference)
  throws DirectoryException
  {
    return getClientConnection().sendSearchReference(this, searchReference);
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);
    setSendResponse(true);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();


    // Start the processing timer.
    setProcessingStartTime();
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

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      setProcessingStopTime();
      logSearchResultDone(this);
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
searchProcessing:
    {
      PreParsePluginResult preParseResult =
        pluginConfigManager.invokePreParseSearchPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        appendErrorMessage(ERR_CANCELED_BY_PREPARSE_DISCONNECT.get());

        setProcessingStopTime();

        logSearchRequest(this);
        logSearchResultDone(this);
        pluginConfigManager.invokePostResponseSearchPlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        logSearchRequest(this);
        break searchProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        break searchProcessing;
      }


      // Log the search request message.
      logSearchRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        break searchProcessing;
      }


      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
      DN baseDN = getBaseDN();
      if (baseDN == null){
        break searchProcessing;
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
        break searchProcessing;
      }
      workflow.execute(this);
    }


    // Check for a terminated connection.
    if (getCancelResult() == CancelResult.CANCELED)
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the add response message.
      logSearchResultDone(this);

      return;
    }

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);

      // Stop the processing timer.
      setProcessingStopTime();

      // Log the search response message.
      logSearchResultDone(this);

      // Invoke the post-response search plugins.
      invokePostResponsePlugins();

      return;
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // Stop the processing timer.
    setProcessingStopTime();

    // If everything is successful to this point and it is not a persistent
    // search, then send the search result done message to the client.
    // Otherwise, we'll want to make the size and time limit values unlimited
    // to ensure that the remainder of the persistent search isn't subject to
    // those restrictions.
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

}
