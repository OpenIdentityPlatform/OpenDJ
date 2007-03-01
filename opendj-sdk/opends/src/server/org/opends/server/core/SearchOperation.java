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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.api.plugin.SearchEntryPluginResult;
import org.opends.server.api.plugin.SearchReferencePluginResult;
import org.opends.server.controls.AccountUsableResponseControl;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.controls.PersistentSearchControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;
import org.opends.server.types.operation.PostOperationSearchOperation;
import org.opends.server.types.operation.PostResponseSearchOperation;
import org.opends.server.types.operation.PreOperationSearchOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import org.opends.server.types.operation.SearchEntrySearchOperation;
import org.opends.server.types.operation.SearchReferenceSearchOperation;
import org.opends.server.util.TimeThread;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to locate entries in the
 * Directory Server based on a given set of criteria.
 */
public class SearchOperation
       extends Operation
       implements PreParseSearchOperation, PreOperationSearchOperation,
                  PostOperationSearchOperation, PostResponseSearchOperation,
                  SearchEntrySearchOperation, SearchReferenceSearchOperation
{



  // Indicates whether a search result done response has been sent to the
  // client.
  private AtomicBoolean responseSent;

  // Indicates whether the client is able to handle referrals.
  private boolean clientAcceptsReferrals;

  // Indicates whether to include the account usable control with search result
  // entries.
  private boolean includeUsableControl;

  // Indicates whether LDAP subentries should be returned.
  private boolean returnLDAPSubentries;

  // Indicates whether to include attribute types only or both types and values.
  private boolean typesOnly;

  // The raw, unprocessed base DN as included in the request from the client.
  private ByteString rawBaseDN;

  // The cancel request that has been issued for this search operation.
  private CancelRequest cancelRequest;

  // The dereferencing policy for the search operation.
  private DereferencePolicy derefPolicy;

  // The base DN for the search operation.
  private DN baseDN;

  // The number of entries that have been sent to the client.
  private int entriesSent;

  // The number of search result references that have been sent to the client.
  private int referencesSent;

  // The size limit for the search operation.
  private int sizeLimit;

  // The time limit for the search operation.
  private int timeLimit;

  // The raw, unprocessed filter as included in the request from the client.
  private LDAPFilter rawFilter;

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
  public SearchOperation(ClientConnection clientConnection, long operationID,
                         int messageID, List<Control> requestControls,
                         ByteString rawBaseDN, SearchScope scope,
                         DereferencePolicy derefPolicy, int sizeLimit,
                         int timeLimit, boolean typesOnly, LDAPFilter rawFilter,
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
  public SearchOperation(ClientConnection clientConnection, long operationID,
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
   * Retrieves the raw, unprocessed base DN as included in the request from the
   * client.  This may or may not contain a valid DN, as no validation will have
   * been performed.
   *
   * @return  The raw, unprocessed base DN as included in the request from the
   *          client.
   */
  public final ByteString getRawBaseDN()
  {

    return rawBaseDN;
  }



  /**
   * Specifies the raw, unprocessed base DN as included in the request from the
   * client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawBaseDN  The raw, unprocessed base DN as included in the request
   *                    from the client.
   */
  public final void setRawBaseDN(ByteString rawBaseDN)
  {

    this.rawBaseDN = rawBaseDN;

    baseDN = null;
  }



  /**
   * Retrieves the base DN for this search operation.  This should not be called
   * by pre-parse plugins, as the raw base DN will not yet have been processed.
   * Instead, they should use the <CODE>getRawBaseDN</CODE> method.
   *
   * @return  The base DN for this search operation, or <CODE>null</CODE> if the
   *          raw base DN has not yet been processed.
   */
  public final DN getBaseDN()
  {

    return baseDN;
  }



  /**
   * Specifies the base DN for this search operation.  This method is only
   * intended for internal use.
   *
   * @param  baseDN  The base DN for this search operation.
   */
  public final void setBaseDN(DN baseDN)
  {

    this.baseDN = baseDN;
  }



  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  public final SearchScope getScope()
  {

    return scope;
  }



  /**
   * Specifies the scope for this search operation.  This should only be called
   * by pre-parse plugins.
   *
   * @param  scope  The scope for this search operation.
   */
  public final void setScope(SearchScope scope)
  {

    this.scope = scope;
  }



  /**
   * Retrieves the alias dereferencing policy for this search operation.
   *
   * @return  The alias dereferencing policy for this search operation.
   */
  public final DereferencePolicy getDerefPolicy()
  {

    return derefPolicy;
  }



  /**
   * Specifies the alias dereferencing policy for this search operation.  This
   * should only be called by pre-parse plugins.
   *
   * @param  derefPolicy  The alias dereferencing policy for this search
   *                      operation.
   */
  public final void setDerefPolicy(DereferencePolicy derefPolicy)
  {

    this.derefPolicy = derefPolicy;
  }



  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  public final int getSizeLimit()
  {

    return sizeLimit;
  }



  /**
   * Specifies the size limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  sizeLimit  The size limit for this search operation.
   */
  public final void setSizeLimit(int sizeLimit)
  {

    this.sizeLimit = sizeLimit;
  }



  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  public final int getTimeLimit()
  {

    return timeLimit;
  }



  /**
   * Specifies the time limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  timeLimit  The time limit for this search operation.
   */
  public final void setTimeLimit(int timeLimit)
  {

    this.timeLimit = timeLimit;
  }



  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  public final boolean getTypesOnly()
  {

    return typesOnly;
  }



  /**
   * Specifies the typesOnly flag for this search operation.  This should only
   * be called by pre-parse plugins.
   *
   * @param  typesOnly  The typesOnly flag for this search operation.
   */
  public final void setTypesOnly(boolean typesOnly)
  {

    this.typesOnly = typesOnly;
  }



  /**
   * Retrieves the raw, unprocessed search filter as included in the request
   * from the client.  It may or may not contain a valid filter (e.g.,
   * unsupported attribute types or values with an invalid syntax) because no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed search filter as included in the request from
   *          the client.
   */
  public final LDAPFilter getRawFilter()
  {

    return rawFilter;
  }



  /**
   * Specifies the raw, unprocessed search filter as included in the request
   * from the client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawFilter  The raw, unprocessed search filter as included in the
   *                    request from the client.
   */
  public final void setRawFilter(LDAPFilter rawFilter)
  {

    this.rawFilter = rawFilter;

    filter = null;
  }



  /**
   * Retrieves the filter for this search operation.  This should not be called
   * by pre-parse plugins, because the raw filter will not yet have been
   * processed.
   *
   * @return  The filter for this search operation, or <CODE>null</CODE> if the
   *          raw filter has not yet been processed.
   */
  public final SearchFilter getFilter()
  {

    return filter;
  }



  /**
   * Retrieves the set of requested attributes for this search operation.  Its
   * contents should not be be altered.
   *
   * @return  The set of requested attributes for this search operation.
   */
  public final LinkedHashSet<String> getAttributes()
  {

    return attributes;
  }



  /**
   * Specifies the set of requested attributes for this search operation.  It
   * should only be called by pre-parse plugins.
   *
   * @param  attributes  The set of requested attributes for this search
   *                     operation.
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
   * Retrieves the number of entries sent to the client for this search
   * operation.
   *
   * @return  The number of entries sent to the client for this search
   *          operation.
   */
  public final int getEntriesSent()
  {

    return entriesSent;
  }



  /**
   * Retrieves the number of search references sent to the client for this
   * search operation.
   *
   * @return  The number of search references sent to the client for this search
   *          operation.
   */
  public final int getReferencesSent()
  {

    return referencesSent;
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
   * Used as a callback for backends to indicate that the provided entry matches
   * the search criteria and that additional processing should be performed to
   * potentially send it back to the client.
   *
   * @param  entry     The entry that matches the search criteria and should be
   *                   sent to the client.
   * @param  controls  The set of controls to include with the entry (may be
   *                   <CODE>null</CODE> if none are needed).
   *
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   */
  public final boolean returnEntry(Entry entry, List<Control> controls)
  {


    // See if the operation has been abandoned.  If so, then don't send the
    // entry and indicate that the search should end.
    if (cancelRequest != null)
    {
      setResultCode(ResultCode.CANCELED);
      return false;
    }

    // See if the size limit has been exceeded.  If so, then don't send the
    // entry and indicate that the search should end.
    if ((sizeLimit > 0) && (entriesSent >= sizeLimit))
    {
      setResultCode(ResultCode.SIZE_LIMIT_EXCEEDED);
      appendErrorMessage(getMessage(MSGID_SEARCH_SIZE_LIMIT_EXCEEDED,
                                    sizeLimit));
      return false;
    }

    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if ((timeLimit > 0) && (TimeThread.getTime() >= timeLimitExpiration))
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(getMessage(MSGID_SEARCH_TIME_LIMIT_EXCEEDED,
                                    timeLimit));
      return false;
    }

    // Determine whether the provided entry is a subentry and if so whether it
    // should be returned.
    if ((scope != SearchScope.BASE_OBJECT) && (! returnLDAPSubentries) &&
        entry.isLDAPSubentry())
    {
      // Check to see if the filter contains an equality element with the
      // objectclass attribute type and a value of "ldapSubentry".  If so, then
      // we'll return it anyway.  Technically, this isn't part of the
      // specification so we don't need to get carried away with really in-depth
      // checks.
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
                returnLDAPSubentries = true;
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
              returnLDAPSubentries = true;
            }
          }
          break;
      }

      if (! returnLDAPSubentries)
      {
        // We still shouldn't return it even based on the filter.  Just throw it
        // away without doing anything.
        return true;
      }
    }


    // Determine whether to include the account usable control.  If so, then
    // create it now.
    if (includeUsableControl)
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
          debugCought(DebugLogLevel.ERROR, e);
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
    if ((attributes == null) || attributes.isEmpty())
    {
      entryToReturn = entry.duplicateWithoutOperationalAttributes(typesOnly);
    }
    else
    {
      entryToReturn = entry.duplicateWithoutAttributes();

      for (String attrName : attributes)
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


    // If there is a matched values control, then further pare down the entry
    // based on the filters that it contains.
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
      appendErrorMessage(getMessage(MSGID_CANCELED_BY_SEARCH_ENTRY_DISCONNECT,
                                    String.valueOf(entry.getDN())));
      return false;
    }


    // Send the entry to the client.
    if (pluginResult.sendEntry())
    {
      clientConnection.sendSearchEntry(this, searchEntry);

      // Log the entry sent to the client.
      logSearchResultEntry(this, searchEntry);

      entriesSent++;
    }


    return pluginResult.continueSearch();
  }



  /**
   * Used as a callback for backends to indicate that the provided search
   * reference was encountered during processing and that additional processing
   * should be performed to potentially send it back to the client.
   *
   * @param  reference  The search reference to send to the client.
   *
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references , or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   */
  public final boolean returnReference(SearchResultReference reference)
  {

    // See if the operation has been abandoned.  If so, then don't send the
    // reference and indicate that the search should end.
    if (cancelRequest != null)
    {
      setResultCode(ResultCode.CANCELED);
      return false;
    }


    // See if the time limit has expired.  If so, then don't send the entry and
    // indicate that the search should end.
    if ((timeLimit > 0) && (TimeThread.getTime() >= timeLimitExpiration))
    {
      setResultCode(ResultCode.TIME_LIMIT_EXCEEDED);
      appendErrorMessage(getMessage(MSGID_SEARCH_TIME_LIMIT_EXCEEDED,
                                    timeLimit));
      return false;
    }


    // See if we know that this client can't handle referrals.  If so, then
    // don't even try to send it.
    if (! clientAcceptsReferrals)
    {
      return true;
    }


    // See if the client has permission to read this reference.
    if (AccessControlConfigManager.getInstance()
        .getAccessControlHandler().maySend(this, reference) == false) {
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
      appendErrorMessage(getMessage(MSGID_CANCELED_BY_SEARCH_REF_DISCONNECT,
           String.valueOf(reference.getReferralURLString())));
      return false;
    }


    // Send the reference to the client.  Note that this could throw an
    // exception, which would indicate that the associated client can't handle
    // referrals.  If that't the case, then set a flag so we'll know not to try
    // to send any more.
    if (pluginResult.sendReference())
    {
      if (clientConnection.sendSearchReference(this, reference))
      {
        // Log the entry sent to the client.
        logSearchResultReference(this, reference);
        referencesSent++;

        // FIXME -- Should the size limit apply here?
      }
      else
      {
        // We know that the client can't handle referrals, so we won't try to
        // send it any more.
        clientAcceptsReferrals = false;
      }
    }


    return pluginResult.continueSearch();
  }



  /**
   * Sends the search result done message to the client.  Note that this method
   * should only be called from external classes in special cases (e.g.,
   * persistent search) where they are sure that the result won't be sent by the
   * core server.  Also note that the result code and optionally the error
   * message should have been set for this operation before this method is
   * called.
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
      DirectoryServer.getPluginConfigManager().
           invokePostResponseSearchPlugins(this);
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
      new String[] { LOG_ELEMENT_ENTRIES_SENT, String.valueOf(entriesSent) },
      new String[] { LOG_ELEMENT_REFERENCES_SENT,
                     String.valueOf(referencesSent ) },
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
    boolean sendResponse = true;


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();
    if (timeLimit <= 0)
    {
      timeLimitExpiration = Long.MAX_VALUE;
    }
    else
    {
      // FIXME -- Factor in the user's effective time limit.
      timeLimitExpiration = processingStartTime + (1000L * timeLimit);
    }


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
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

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logSearchRequest(this);
        logSearchResultDone(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logSearchRequest(this);
        break searchProcessing;
      }


      // Log the search request message.
      logSearchRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logSearchResultDone(this);
        return;
      }


      // Process the search base and filter to convert them from their raw forms
      // as provided by the client to the forms required for the rest of the
      // search processing.
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
          debugCought(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        setMatchedDN(de.getMatchedDN());
        setReferralURLs(de.getReferralURLs());

        break searchProcessing;
      }

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
          debugCought(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        setMatchedDN(de.getMatchedDN());
        setReferralURLs(de.getReferralURLs());

        break searchProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then
      // see if there is any special processing required.
      boolean       processSearch    = true;
      List<Control> requestControls  = getRequestControls();
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
                  debugCought(DebugLogLevel.ERROR, le);
                }

                setResultCode(ResultCode.valueOf(le.getResultCode()));
                appendErrorMessage(le.getMessage());

                break searchProcessing;
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
                  debugCought(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());

                int msgID = MSGID_SEARCH_CANNOT_GET_ENTRY_FOR_ASSERTION;
                appendErrorMessage(getMessage(msgID, de.getErrorMessage()));

                break searchProcessing;
              }

              if (entry == null)
              {
                setResultCode(ResultCode.NO_SUCH_OBJECT);

                int msgID = MSGID_SEARCH_NO_SUCH_ENTRY_FOR_ASSERTION;
                appendErrorMessage(getMessage(msgID));

                break searchProcessing;
              }


              if (! assertionFilter.matchesEntry(entry))
              {
                setResultCode(ResultCode.ASSERTION_FAILED);

                appendErrorMessage(getMessage(MSGID_SEARCH_ASSERTION_FAILED));

                break searchProcessing;
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, de);
              }

              setResultCode(ResultCode.PROTOCOL_ERROR);

              int msgID = MSGID_SEARCH_CANNOT_PROCESS_ASSERTION_FILTER;
              appendErrorMessage(getMessage(msgID, de.getErrorMessage()));

              break searchProcessing;
            }
          }
          else if (oid.equals(OID_PROXIED_AUTH_V1))
          {
            // The requester must have the PROXIED_AUTH privilige in order to be
            // able to use this control.
            if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
            {
              int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
              appendErrorMessage(getMessage(msgID));
              setResultCode(ResultCode.AUTHORIZATION_DENIED);
              break searchProcessing;
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
                  debugCought(DebugLogLevel.ERROR, le);
                }

                setResultCode(ResultCode.valueOf(le.getResultCode()));
                appendErrorMessage(le.getMessage());

                break searchProcessing;
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
                debugCought(DebugLogLevel.ERROR, de);
              }

              setResultCode(de.getResultCode());
              appendErrorMessage(de.getErrorMessage());

              break searchProcessing;
            }


            setAuthorizationEntry(authorizationEntry);
          }
          else if (oid.equals(OID_PROXIED_AUTH_V2))
          {
            // The requester must have the PROXIED_AUTH privilige in order to be
            // able to use this control.
            if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
            {
              int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
              appendErrorMessage(getMessage(msgID));
              setResultCode(ResultCode.AUTHORIZATION_DENIED);
              break searchProcessing;
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
                  debugCought(DebugLogLevel.ERROR, le);
                }

                setResultCode(ResultCode.valueOf(le.getResultCode()));
                appendErrorMessage(le.getMessage());

                break searchProcessing;
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
                debugCought(DebugLogLevel.ERROR, de);
              }

              setResultCode(de.getResultCode());
              appendErrorMessage(de.getErrorMessage());

              break searchProcessing;
            }


            setAuthorizationEntry(authorizationEntry);
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
                  debugCought(DebugLogLevel.ERROR, le);
                }

                setResultCode(ResultCode.valueOf(le.getResultCode()));
                appendErrorMessage(le.getMessage());

                break searchProcessing;
              }
            }

            persistentSearch =
                 new PersistentSearch(this, psearchControl.getChangeTypes(),
                                      psearchControl.getReturnECs());

            // If we're only interested in changes, then we don't actually want
            // to process the search now.
            if (psearchControl.getChangesOnly())
            {
              processSearch = false;
            }
          }
          else if (oid.equals(OID_LDAP_SUBENTRIES))
          {
            returnLDAPSubentries = true;
          }
          else if (oid.equals(OID_MATCHED_VALUES))
          {
            if (c instanceof MatchedValuesControl)
            {
              matchedValuesControl = (MatchedValuesControl) c;
            }
            else
            {
              try
              {
                matchedValuesControl = MatchedValuesControl.decodeControl(c);
              }
              catch (LDAPException le)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, le);
                }

                setResultCode(ResultCode.valueOf(le.getResultCode()));
                appendErrorMessage(le.getMessage());

                break searchProcessing;
              }
            }
          }
          else if (oid.equals(OID_ACCOUNT_USABLE_CONTROL))
          {
            includeUsableControl = true;
          }

          // NYI -- Add support for additional controls.
          else if (c.isCritical())
          {
            Backend backend = DirectoryServer.getBackend(baseDN);
            if ((backend == null) || (! backend.supportsControl(oid)))
            {
              setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

              int msgID = MSGID_SEARCH_UNSUPPORTED_CRITICAL_CONTROL;
              appendErrorMessage(getMessage(msgID, oid));

              break searchProcessing;
            }
          }
        }
      }


      // Check to see if the client has permission to perform the
      // search.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.
      if (AccessControlConfigManager.getInstance()
          .getAccessControlHandler().isAllowed(this) == false) {
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

        int msgID = MSGID_SEARCH_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
        appendErrorMessage(getMessage(msgID, String.valueOf(baseDN)));

        skipPostOperation = true;
        break searchProcessing;
      }

      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logSearchResultDone(this);
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

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();
        logSearchResultDone(this);
        return;
      }
      else if (preOpResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        break searchProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logSearchResultDone(this);
        return;
      }


      // Get the backend that should hold the search base.  If there is none,
      // then fail.
      Backend backend = DirectoryServer.getBackend(baseDN);
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(getMessage(MSGID_SEARCH_BASE_DOESNT_EXIST,
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
        sendResponse = false;
      }


      // Process the search in the backend and all its subordinates.
      try
      {
        if (processSearch)
        {
          searchBackend(backend);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        setMatchedDN(de.getMatchedDN());
        setReferralURLs(de.getReferralURLs());

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          sendResponse = true;
        }

        break searchProcessing;
      }
      catch (CancelledOperationException coe)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, coe);
        }

        CancelResult cancelResult = coe.getCancelResult();

        setCancelResult(cancelResult);
        setResultCode(cancelResult.getResultCode());

        String message = coe.getMessage();
        if ((message != null) && (message.length() > 0))
        {
          appendErrorMessage(message);
        }

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          sendResponse = true;
        }

        skipPostOperation = true;
        break searchProcessing;
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        setResultCode(DirectoryServer.getServerErrorResultCode());

        int msgID = MSGID_SEARCH_BACKEND_EXCEPTION;
        appendErrorMessage(getMessage(msgID, stackTraceToSingleLineString(e)));

        if (persistentSearch != null)
        {
          DirectoryServer.deregisterPersistentSearch(persistentSearch);
          sendResponse = true;
        }

        skipPostOperation = true;
        break searchProcessing;
      }
    }


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      logSearchResultDone(this);
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

        int msgID = MSGID_CANCELED_BY_POSTOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();
        logSearchResultDone(this);
        return;
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the search result done message to the client.
    if (sendResponse)
    {
      sendSearchResultDone();
    }
  }



  /**
   * Processes the search in the provided backend and recursively through its
   * subordinate backends.
   *
   * @param  backend  The backend in which to process the search.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   *
   * @throws  CancelledOperationException  If the backend noticed and reacted
   *                                       to a request to cancel or abandon the
   *                                       search operation.
   */
  private final void searchBackend(Backend backend)
          throws DirectoryException, CancelledOperationException
  {


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      setCancelResult(CancelResult.CANCELED);
      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Perform the search in the provided backend.
    backend.search(this);


    // If there are any subordinate backends, then process the search there as
    // well.
    Backend[] subBackends = backend.getSubordinateBackends();
    for (Backend b : subBackends)
    {
      DN[] baseDNs = b.getBaseDNs();
      for (DN dn : baseDNs)
      {
        if (dn.isDescendantOf(baseDN))
        {
          searchBackend(b);
          break;
        }
      }
    }
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
          debugCought(DebugLogLevel.ERROR, e);
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
}

