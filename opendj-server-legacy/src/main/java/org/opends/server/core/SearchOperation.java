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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.controls.MatchedValuesControl;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

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
  ByteString getRawBaseDN();

  /**
   * Specifies the raw, unprocessed base DN as included in the request from the
   * client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawBaseDN  The raw, unprocessed base DN as included in the request
   *                    from the client.
   */
  void setRawBaseDN(ByteString rawBaseDN);

  /**
   * Retrieves the base DN for this search operation.  This should not be called
   * by pre-parse plugins, as the raw base DN will not yet have been processed.
   * Instead, they should use the <CODE>getRawBaseDN</CODE> method.
   *
   * @return  The base DN for this search operation, or <CODE>null</CODE> if the
   *          raw base DN has not yet been processed.
   */
  DN getBaseDN();

  /**
   * Specifies the base DN for this search operation.  This method is only
   * intended for internal use.
   *
   * @param  baseDN  The base DN for this search operation.
   */
  void setBaseDN(DN baseDN);

  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  SearchScope getScope();

  /**
   * Specifies the scope for this search operation.  This should only be called
   * by pre-parse plugins.
   *
   * @param  scope  The scope for this search operation.
   */
  void setScope(SearchScope scope);

  /**
   * Retrieves the alias dereferencing policy for this search operation.
   *
   * @return  The alias dereferencing policy for this search operation.
   */
  DereferenceAliasesPolicy getDerefPolicy();

  /**
   * Specifies the alias dereferencing policy for this search operation.  This
   * should only be called by pre-parse plugins.
   *
   * @param  derefPolicy  The alias dereferencing policy for this search
   *                      operation.
   */
  void setDerefPolicy(DereferenceAliasesPolicy derefPolicy);

  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  int getSizeLimit();

  /**
   * Specifies the size limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  sizeLimit  The size limit for this search operation.
   */
  void setSizeLimit(int sizeLimit);

  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  int getTimeLimit();

  /**
   * Get the time after which the search time limit has expired.
   *
   * @return the timeLimitExpiration
   */
  long getTimeLimitExpiration();

  /**
   * Specifies the time limit for this search operation.  This should only be
   * called by pre-parse plugins.
   *
   * @param  timeLimit  The time limit for this search operation.
   */
  void setTimeLimit(int timeLimit);

  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  boolean getTypesOnly();

  /**
   * Specifies the typesOnly flag for this search operation.  This should only
   * be called by pre-parse plugins.
   *
   * @param  typesOnly  The typesOnly flag for this search operation.
   */
  void setTypesOnly(boolean typesOnly);

  /**
   * Retrieves the raw, unprocessed search filter as included in the request
   * from the client.  It may or may not contain a valid filter (e.g.,
   * unsupported attribute types or values with an invalid syntax) because no
   * validation will have been performed on it.
   *
   * @return  The raw, unprocessed search filter as included in the request from
   *          the client.
   */
  RawFilter getRawFilter();

  /**
   * Specifies the raw, unprocessed search filter as included in the request
   * from the client.  This method should only be called by pre-parse plugins.
   *
   * @param  rawFilter  The raw, unprocessed search filter as included in the
   *                    request from the client.
   */
  void setRawFilter(RawFilter rawFilter);

  /**
   * Retrieves the filter for this search operation.  This should not be called
   * by pre-parse plugins, because the raw filter will not yet have been
   * processed.
   *
   * @return  The filter for this search operation, or <CODE>null</CODE> if the
   *          raw filter has not yet been processed.
   */
  SearchFilter getFilter();

  /**
   * Retrieves the set of requested attributes for this search operation.  Its
   * contents should not be altered.
   *
   * @return  The set of requested attributes for this search operation.
   */
  Set<String> getAttributes();

  /**
   * Specifies the set of requested attributes for this search operation.  It
   * should only be called by pre-parse plugins.
   *
   * @param  attributes  The set of requested attributes for this search
   *                     operation.
   */
  void setAttributes(Set<String> attributes);

  /**
   * Retrieves the number of entries sent to the client for this search
   * operation.
   *
   * @return  The number of entries sent to the client for this search
   *          operation.
   */
  int getEntriesSent();

  /**
   * Retrieves the number of search references sent to the client for this
   * search operation.
   *
   * @return  The number of search references sent to the client for this search
   *          operation.
   */
  int getReferencesSent();

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
  boolean returnEntry(Entry entry, List<Control> controls);

  /**
   * Used as a callback for backends to indicate that the provided entry matches
   * the search criteria and that additional processing should be performed to
   * potentially send it back to the client.
   *
   * @param  entry        The entry that matches the search criteria and should
   *                      be sent to the client.
   * @param  controls     The set of controls to include with the entry (may be
   *                      <CODE>null</CODE> if none are needed).
   * @param  evaluateAci  Indicates whether the access rights to the entry
   *                      should be evaluated.
   *
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   */
  boolean returnEntry(Entry entry, List<Control> controls,
                                      boolean evaluateAci);

  /**
   * Used as a callback for backends to indicate that the provided search
   * reference was encountered during processing and that additional processing
   * should be performed to potentially send it back to the client.
   *
   * @param  reference  The search reference to send to the client.
   * @param  dn         The DN related to the specified search reference.
   *
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references , or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   */
  boolean returnReference(DN dn,
                                          SearchResultReference reference);

  /**
   * Used as a callback for backends to indicate that the provided search
   * reference was encountered during processing and that additional processing
   * should be performed to potentially send it back to the client.
   *
   * @param  reference    The search reference to send to the client.
   * @param  dn           The DN related to the specified search reference.
   * @param  evaluateAci  Indicates whether the access rights to the entry
   *                      should be evaluated.
   *
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references , or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   */
  boolean returnReference(DN dn,
                                          SearchResultReference reference,
                                          boolean evaluateAci);

  /**
   * Sends the search result done message to the client.  Note that this method
   * should only be called from external classes in special cases (e.g.,
   * persistent search) where they are sure that the result won't be sent by the
   * core server.  Also note that the result code and optionally the error
   * message should have been set for this operation before this method is
   * called.
   */
  void sendSearchResultDone();

  /**
   * Set the time after which the search time limit has expired.
   *
   * @param timeLimitExpiration - Time after which the search has expired
   */
  void setTimeLimitExpiration(long timeLimitExpiration);

  /**
   * Indicates whether LDAP subentries should be returned or not.
   *
   * @return true if the LDAP subentries should be returned, false otherwise
   */
  boolean isReturnSubentriesOnly();

  /**
   * Set the flag indicating whether the LDAP subentries should be returned.
   *
   * @param returnLDAPSubentries - Boolean indicating whether the LDAP
   *                               subentries should be returned or not
   */
  void setReturnSubentriesOnly(boolean returnLDAPSubentries);

  /**
   * The matched values control associated with this search operation.
   *
   * @return the match values control
   */
  MatchedValuesControl getMatchedValuesControl();

  /**
   * Set the match values control.
   *
   * @param controls - The matched values control
   */
  void setMatchedValuesControl(MatchedValuesControl controls);

  /**
   * Indicates whether to include the account usable response control with
   * search result entries or not.
   *
   * @return true if the usable control has to be part of the search result
   *         entry
   */
  boolean isIncludeUsableControl();

  /**
   * Specify whether to include the account usable response control within the
   * search result entries.
   *
   * @param includeUsableControl - True if the account usable response control
   *                               has to be included within the search result
   *                               entries, false otherwise
   */
  void setIncludeUsableControl(boolean includeUsableControl);

  /**
   * Indicates whether the client is able to handle referrals.
   *
   * @return true, if the client is able to handle referrals
   */
  boolean isClientAcceptsReferrals();

  /**
   * Specify whether the client is able to handle referrals.
   *
   * @param clientAcceptReferrals - Boolean set to true if the client
   *                                can handle referrals
   */
  void setClientAcceptsReferrals(boolean clientAcceptReferrals);

  /**
   * Indicates whether the search result done message has to be sent
   * to the client, or not.
   *
   * @return true if the search result done message is to be sent to the client
   */
  boolean isSendResponse();

  /**
   * Specify whether the search result done message has to be sent
   * to the client, or not.
   *
   * @param sendResponse - boolean indicating whether the search result done
   *                       message is to send to the client
   */
  void setSendResponse(boolean sendResponse);

  /**
   * Returns true if only real attributes should be returned.
   *
   * @return true if only real attributes should be returned, false otherwise
   */
  boolean isRealAttributesOnly();

  /**
   * Specify whether to only return real attributes.
   *
   * @param realAttributesOnly - boolean setup to true, if only the real
   *                             attributes should be returned
   */
  void setRealAttributesOnly(boolean realAttributesOnly);

  /**
   * Returns true if only virtual attributes should be returned.
   *
   * @return true if only virtual attributes should be returned, false
   *         otherwise
   */
  boolean isVirtualAttributesOnly();

  /**
   * Specify whether to only return virtual attributes.
   *
   * @param virtualAttributesOnly - boolean setup to true, if only the virtual
   *                                attributes should be returned
   */
  void setVirtualAttributesOnly(boolean virtualAttributesOnly);

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
  void sendSearchEntry(SearchResultEntry entry)
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
  boolean sendSearchReference(SearchResultReference reference)
    throws DirectoryException;

  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  @Override
  DN getProxiedAuthorizationDN();

  /**
   * Set the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @param proxiedAuthorizationDN
   *          The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  @Override
  void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}
