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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import java.util.Collection;

import org.opends.sdk.*;
import org.opends.sdk.controls.Control;




/**
 * The Search operation is used to request a server to return, subject
 * to access controls and other restrictions, a set of entries matching
 * a complex search criterion. This can be used to read attributes from
 * a single entry, from entries immediately subordinate to a particular
 * entry, or from a whole subtree of entries.
 */
public interface SearchRequest extends Request
{
  /**
   * Adds the provided attribute names to the list of attributes to be
   * included with each entry that matches the search criteria.
   * Attributes that are sub-types of listed attributes are implicitly
   * included.
   * 
   * @param attributeDescriptions
   *          The names of the attributes to be included with each
   *          entry.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit attribute names to
   *           be added.
   * @throws NullPointerException
   *           If {@code attributeDescriptions} was {@code null}, or if
   *           it contained a {@code null} element.
   */
  SearchRequest addAttribute(Collection<String> attributeDescriptions)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds the provided attribute name to the list of attributes to be
   * included with each entry that matches the search criteria.
   * Attributes that are sub-types of listed attributes are implicitly
   * included.
   * 
   * @param attributeDescription
   *          The name of the attribute to be included with each entry.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit attribute names to
   *           be added.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  SearchRequest addAttribute(String attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds the provided attribute names to the list of attributes to be
   * included with each entry that matches the search criteria.
   * Attributes that are sub-types of listed attributes are implicitly
   * included.
   * 
   * @param attributeDescriptions
   *          The names of the attributes to be included with each
   *          entry.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit attribute names to
   *           be added.
   * @throws NullPointerException
   *           If {@code attributeDescriptions} was {@code null}, or if
   *           it contained a {@code null} element.
   */
  SearchRequest addAttribute(String... attributeDescriptions)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds the provided control to this request.
   * 
   * @param control
   *          The control to be added to this request.
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be added.
   * @throws NullPointerException
   *           If {@code control} was {@code null}.
   */
  SearchRequest addControl(Control control)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Clears the list of attributes to be included with each entry that
   * matches the search criteria. Attributes that are sub-types of
   * listed attributes are implicitly included.
   * 
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit attributes to be
   *           removed.
   */
  SearchRequest clearAttributes() throws UnsupportedOperationException;



  /**
   * Removes all the controls included with this request.
   * 
   * @return This request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   */
  SearchRequest clearControls() throws UnsupportedOperationException;



  /**
   * Returns an {@code Iterable} containing the list of attributes to be
   * included with each entry that matches the search criteria.
   * Attributes that are sub-types of listed attributes are implicitly
   * included. The returned {@code Iterable} may be used to remove
   * attribute names if permitted by this search request.
   * 
   * @return An {@code Iterable} containing the list of attributes.
   */
  Iterable<String> getAttributes();



  /**
   * Returns the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be returned.
   * @return The control, or {@code null} if the control is not included
   *         with this request.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control getControl(String oid) throws NullPointerException;



  /**
   * Returns an {@code Iterable} containing the controls included with
   * this request. The returned {@code Iterable} may be used to remove
   * controls if permitted by this request.
   * 
   * @return An {@code Iterable} containing the controls.
   */
  Iterable<Control> getControls();



  /**
   * Returns an indication as to whether or not alias entries are to be
   * dereferenced during the search.
   * 
   * @return The alias dereferencing policy.
   */
  DereferenceAliasesPolicy getDereferenceAliasesPolicy();



  /**
   * Returns the filter that defines the conditions that must be
   * fulfilled in order for an entry to be returned.
   * 
   * @return The search filter.
   */
  Filter getFilter();



  /**
   * Returns the distinguished name of the base entry relative to which
   * the search is to be performed.
   * 
   * @return The distinguished name of the base entry.
   */
  DN getName();



  /**
   * Returns the scope of the search.
   * 
   * @return The search scope.
   */
  SearchScope getScope();



  /**
   * Returns the size limit that should be used in order to restrict the
   * maximum number of entries returned by the search.
   * <p>
   * A value of zero (the default) in this field indicates that no
   * client-requested size limit restrictions are in effect. Servers may
   * also enforce a maximum number of entries to return.
   * 
   * @return The size limit that should be used in order to restrict the
   *         maximum number of entries returned by the search.
   */
  int getSizeLimit();



  /**
   * Returns the time limit that should be used in order to restrict the
   * maximum time (in seconds) allowed for the search.
   * <p>
   * A value of zero (the default) in this field indicates that no
   * client-requested time limit restrictions are in effect for the
   * search. Servers may also enforce a maximum time limit for the
   * search.
   * 
   * @return The time limit that should be used in order to restrict the
   *         maximum time (in seconds) allowed for the search.
   */
  int getTimeLimit();



  /**
   * Indicates whether or not this search request has a list of
   * attributes to be included with each entry that matches the search
   * criteria.
   * 
   * @return {@code true} if this search request has a list of
   *         attributes to be included with each entry, otherwise
   *         {@code false}.
   */
  boolean hasAttributes();



  /**
   * Indicates whether or not this request has any controls.
   * 
   * @return {@code true} if this request has any controls, otherwise
   *         {@code false}.
   */
  boolean hasControls();



  /**
   * Indicates whether search results are to contain both attribute
   * descriptions and values, or just attribute descriptions.
   * 
   * @return {@code true} if only attribute descriptions (and not
   *         values) are to be returned, or {@code false} (the default)
   *         if both attribute descriptions and values are to be
   *         returned.
   */
  boolean isTypesOnly();



  /**
   * Removes the provided attribute name from the list of attributes to
   * be included with each entry that matches the search criteria.
   * Attributes that are sub-types of listed attributes are implicitly
   * included.
   * 
   * @param attributeDescription
   *          The name of the attribute to be removed.
   * @return {@code true} if the attribute name was found in the list of
   *         attributes.
   * @throws UnsupportedOperationException
   *           If this search request does not permit attribute names to
   *           be removed.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  boolean removeAttribute(String attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes the first control contained in this request having the
   * specified OID.
   * 
   * @param oid
   *          The OID of the control to be removed.
   * @return The removed control, or {@code null} if the control is not
   *         included with this request.
   * @throws UnsupportedOperationException
   *           If this request does not permit controls to be removed.
   * @throws NullPointerException
   *           If {@code oid} was {@code null}.
   */
  Control removeControl(String oid)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the alias dereferencing policy to be used during the search.
   * 
   * @param policy
   *          The alias dereferencing policy to be used during the
   *          search.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the alias
   *           dereferencing policy to be set.
   * @throws NullPointerException
   *           If {@code policy} was {@code null}.
   */
  SearchRequest setDereferenceAliasesPolicy(
      DereferenceAliasesPolicy policy)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the filter that defines the conditions that must be fulfilled
   * in order for an entry to be returned.
   * 
   * @param filter
   *          The filter that defines the conditions that must be
   *          fulfilled in order for an entry to be returned.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the filter to be
   *           set.
   * @throws NullPointerException
   *           If {@code filter} was {@code null}.
   */
  SearchRequest setFilter(Filter filter)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the filter that defines the conditions that must be fulfilled
   * in order for an entry to be returned.
   * 
   * @param filter
   *          The filter that defines the conditions that must be
   *          fulfilled in order for an entry to be returned.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the filter to be
   *           set.
   * @throws LocalizedIllegalArgumentException
   *           If {@code filter} is not a valid LDAP string
   *           representation of a filter.
   * @throws NullPointerException
   *           If {@code filter} was {@code null}.
   */
  SearchRequest setFilter(String filter)
      throws UnsupportedOperationException,
      LocalizedIllegalArgumentException, NullPointerException;



  /**
   * Sets the distinguished name of the base entry relative to which the
   * search is to be performed.
   * 
   * @param dn
   *          The distinguished name of the base entry relative to which
   *          the search is to be performed.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  SearchRequest setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Sets the distinguished name of the base entry relative to which the
   * search is to be performed.
   * 
   * @param dn
   *          The distinguished name of the base entry relative to which
   *          the search is to be performed.
   * @return This search request.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the default
   *           schema.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the distinguished
   *           name to be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  SearchRequest setName(String dn)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Sets the scope of the search.
   * 
   * @param scope
   *          The scope of the search.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the scope to be
   *           set.
   * @throws NullPointerException
   *           If {@code scope} was {@code null}.
   */
  SearchRequest setScope(SearchScope scope)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Sets the size limit that should be used in order to restrict the
   * maximum number of entries returned by the search.
   * <p>
   * A value of zero (the default) in this field indicates that no
   * client-requested size limit restrictions are in effect. Servers may
   * also enforce a maximum number of entries to return.
   * 
   * @param limit
   *          The size limit that should be used in order to restrict
   *          the maximum number of entries returned by the search.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the size limit to
   *           be set.
   * @throws LocalizedIllegalArgumentException
   *           If {@code limit} was negative.
   */
  SearchRequest setSizeLimit(int limit)
      throws UnsupportedOperationException,
      LocalizedIllegalArgumentException;



  /**
   * Sets the time limit that should be used in order to restrict the
   * maximum time (in seconds) allowed for the search.
   * <p>
   * A value of zero (the default) in this field indicates that no
   * client-requested time limit restrictions are in effect for the
   * search. Servers may also enforce a maximum time limit for the
   * search.
   * 
   * @param limit
   *          The time limit that should be used in order to restrict
   *          the maximum time (in seconds) allowed for the search.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the time limit to
   *           be set.
   * @throws LocalizedIllegalArgumentException
   *           If {@code limit} was negative.
   */
  SearchRequest setTimeLimit(int limit)
      throws UnsupportedOperationException,
      LocalizedIllegalArgumentException;



  /**
   * Specifies whether search results are to contain both attribute
   * descriptions and values, or just attribute descriptions.
   * 
   * @param typesOnly
   *          {@code true} if only attribute descriptions (and not
   *          values) are to be returned, or {@code false} (the default)
   *          if both attribute descriptions and values are to be
   *          returned.
   * @return This search request.
   * @throws UnsupportedOperationException
   *           If this search request does not permit the types-only
   *           parameter to be set.
   */
  SearchRequest setTypesOnly(boolean typesOnly)
      throws UnsupportedOperationException;

}
