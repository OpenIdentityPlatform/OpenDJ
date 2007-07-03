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

import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

/**
 * This interface defines an operation used to search for entries
 * in the Directory Server.
 */
public interface SearchOperation extends Operation
{

  /**
   * Retrieves the raw, unprocessed base DN as included in the request from the
   * client.  This may or may not contain a valid DN, as no validation will have
   * been performed.
   *
   * @return  The raw, unprocessed base DN as included in the request from the
   *          client.
   */
  public abstract ByteString getRawBaseDN();

  /**
   * Specifies the raw, unprocessed base DN as included in the request from the
   * client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawBaseDN  The raw, unprocessed base DN as included in the request
   *                    from the client.
   */
  public abstract void setRawBaseDN(ByteString rawBaseDN);

  /**
   * Retrieves the base DN for this search operation.  This should not be called
   * by pre-parse plugins, as the raw base DN will not yet have been processed.
   * Instead, they should use the <CODE>getRawBaseDN</CODE> method.
   *
   * @return  The base DN for this search operation, or <CODE>null</CODE> if the
   *          raw base DN has not yet been processed.
   */
  public abstract DN getBaseDN();

  /**
   * Specifies the base DN for this search operation.  This method is only
   * intended for internal use.
   *
   * @param  baseDN  The base DN for this search operation.
   */
  public abstract void setBaseDN(DN baseDN);

  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  public abstract SearchScope getScope();

  /**
   * Specifies the scope for this search operation.  This should only be called
   * by pre-parse plugins.
   *
   * @param  scope  The scope for this search operation.
   */
  public abstract void setScope(SearchScope scope);

  /**
   * Retrieves the alias dereferencing policy for this search operation.
   *
   * @return  The alias dereferencing policy for this search operation.
   */
  public abstract DereferencePolicy getDerefPolicy();

  /**
   * Specifies the alias dereferencing policy for this search operation.  This
   * should only be called by pre-parse plugins.
   *
   * @param  derefPolicy  The alias dereferencing policy for this search
   *                      operation.
   */
  public abstract void setDerefPolicy(DereferencePolicy derefPolicy);

  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  public abstract int getSizeLimit();

  /**
   * Specifies the size limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  sizeLimit  The size limit for this search operation.
   */
  public abstract void setSizeLimit(int sizeLimit);

  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  public abstract int getTimeLimit();

  /**
   * Get the time after which the search time limit has expired.
   *
   * @return the timeLimitExpiration
   */
  public abstract Long getTimeLimitExpiration();

  /**
   * Specifies the time limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  timeLimit  The time limit for this search operation.
   */
  public abstract void setTimeLimit(int timeLimit);

  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  public abstract boolean getTypesOnly();

  /**
   * Specifies the typesOnly flag for this search operation.  This should only
   * be called by pre-parse plugins.
   *
   * @param  typesOnly  The typesOnly flag for this search operation.
   */
  public abstract void setTypesOnly(boolean typesOnly);

  /**
   * Retrieves the raw, unprocessed search filter as included in the request
   * from the client.  It may or may not contain a valid filter (e.g.,
   * unsupported attribute types or values with an invalid syntax) because no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed search filter as included in the request from
   *          the client.
   */
  public abstract RawFilter getRawFilter();

  /**
   * Specifies the raw, unprocessed search filter as included in the request
   * from the client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawFilter  The raw, unprocessed search filter as included in the
   *                    request from the client.
   */
  public abstract void setRawFilter(RawFilter rawFilter);

  /**
   * Retrieves the filter for this search operation.  This should not be called
   * by pre-parse plugins, because the raw filter will not yet have been
   * processed.
   *
   * @return  The filter for this search operation, or <CODE>null</CODE> if the
   *          raw filter has not yet been processed.
   */
  public abstract SearchFilter getFilter();

  /**
   * Retrieves the set of requested attributes for this search operation.  Its
   * contents should not be be altered.
   *
   * @return  The set of requested attributes for this search operation.
   */
  public abstract LinkedHashSet<String> getAttributes();

  /**
   * Specifies the set of requested attributes for this search operation.  It
   * should only be called by pre-parse plugins.
   *
   * @param  attributes  The set of requested attributes for this search
   *                     operation.
   */
  public abstract void setAttributes(LinkedHashSet<String> attributes);

  /**
   * Retrieves the number of entries sent to the client for this search
   * operation.
   *
   * @return  The number of entries sent to the client for this search
   *          operation.
   */
  public abstract int getEntriesSent();

  /**
   * Retrieves the number of search references sent to the client for this
   * search operation.
   *
   * @return  The number of search references sent to the client for this search
   *          operation.
   */
  public abstract int getReferencesSent();

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
  public abstract boolean returnEntry(Entry entry, List<Control> controls);

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
  public abstract boolean returnReference(SearchResultReference reference);

  /**
   * Sends the search result done message to the client.  Note that this method
   * should only be called from external classes in special cases (e.g.,
   * persistent search) where they are sure that the result won't be sent by the
   * core server.  Also note that the result code and optionally the error
   * message should have been set for this operation before this method is
   * called.
   */
  public abstract void sendSearchResultDone();

  /**
   * Set the time after which the search time limit has expired.
   *
   * @param timeLimitExpiration - Time after which the search has expired
   */
  public abstract void setTimeLimitExpiration(Long timeLimitExpiration);

  /**
   * Indicates whether LDAP subentries should be returned or not.
   *
   * @return true if the LDAP subentries should be returned, false otherwise
   */
  public abstract boolean isReturnLDAPSubentries();

  /**
   * Set the flag indicating wether the LDAP subentries should be returned.
   *
   * @param returnLDAPSubentries - Boolean indicating wether the LDAP
   *                               subentries should be returned or not
   */
  public abstract void setReturnLDAPSubentries(boolean returnLDAPSubentries);

  /**
   * The matched values control associated with this search operation.
   *
   * @return the match values control
   */
  public abstract MatchedValuesControl getMatchedValuesControl();

  /**
   * Set the match values control.
   *
   * @param controls - The matched values control
   */
  public abstract void setMatchedValuesControl(MatchedValuesControl controls);

  /**
   * Indicates whether to include the account usable response control with
   * search result entries or not.
   *
   * @return true if the usable control has to be part of the search result
   *         entry
   */
  public abstract boolean isIncludeUsableControl();

  /**
   * Specify whether to include the account usable response control within the
   * search result entries.
   *
   * @param includeUsableControl - True if the account usable response control
   *                               has to be included within the search result
   *                               entries, false otherwise
   */
  public abstract void setIncludeUsableControl(boolean includeUsableControl);

  /**
   * Register the psearch in the search operation.
   *
   * @param psearch - Persistent search associated to that operation
   */
  public abstract void setPersistentSearch(PersistentSearch psearch);

  /**
   * Get the psearch from the search operation.
   *
   * @return the psearch, or null if no psearch was registered
   */
  public abstract PersistentSearch getPersistentSearch();

  /**
   * Indicates whether the client is able to handle referrals.
   *
   * @return true, if the client is able to handle referrals
   */
  public abstract boolean isClientAcceptsReferrals();

  /**
   * Specify whether the client is able to handle referrals.
   *
   * @param clientAcceptReferrals - Boolean set to true if the client
   *                                can handle referrals
   */
  public abstract void setClientAcceptsReferrals(boolean clientAcceptReferrals);

  /**
   * Increments by 1 the number of entries sent to the client for this search
   * operation.
   */
  public abstract void incrementEntriesSent();

  /**
   * Increments by 1 the number of search references sent to the client for this
   * search operation.
   */
  public abstract void incrementReferencesSent();

  /**
   * Indicates wether the search result done message has to be sent
   * to the client, or not.
   *
   * @return true if the search result done message is to be sent to the client
   */
  public abstract boolean isSendResponse();

  /**
   * Specify wether the search result done message has to be sent
   * to the client, or not.
   *
   * @param sendResponse - boolean indicating wether the search result done
   *                       message is to send to the client
   */
  public abstract void setSendResponse(boolean sendResponse);

  /**
   * Returns true if only real attributes should be returned.
   *
   * @return true if only real attributes should be returned, false otherwise
   */
  public abstract boolean isRealAttributesOnly();

  /**
   * Specify wether to only return real attributes.
   *
   * @param realAttributesOnly - boolean setup to true, if only the real
   *                             attributes should be returned
   */
  public abstract void setRealAttributesOnly(boolean realAttributesOnly);

  /**
   * Returns true if only virtual attributes should be returned.
   *
   * @return true if only virtual attributes should be returned, false
   *         otherwise
   */
  public abstract boolean isVirtualAttributesOnly();

  /**
   * Specify wether to only return virtual attributes.
   *
   * @param virtualAttributesOnly - boolean setup to true, if only the virtual
   *                                attributes should be returned
   */
  public abstract void setVirtualAttributesOnly(boolean virtualAttributesOnly);

  /**
   * Sends the provided search result entry to the client.
   *
   * @param  entry      The search result entry to be sent to
   *                          the client.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the entry to the client and
   *                              the search should be terminated.
   */
  public abstract void sendSearchEntry(SearchResultEntry entry)
    throws DirectoryException;

  /**
   * Sends the provided search result reference to the client.
   *
   * @param  reference  The search result reference to be sent
   *                          to the client.
   *
   * @return  <CODE>true</CODE> if the client is able to accept
   *          referrals, or <CODE>false</CODE> if the client cannot
   *          handle referrals and no more attempts should be made to
   *          send them for the associated search operation.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to send the reference to the client
   *                              and the search should be terminated.
   */
  public abstract boolean sendSearchReference(SearchResultReference reference)
    throws DirectoryException;

  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public abstract DN getProxiedAuthorizationDN();

  /**
   * Set the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @param proxiedAuthorizationDN
   *          The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public abstract void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}