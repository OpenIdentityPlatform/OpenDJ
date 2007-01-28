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
package org.opends.server.types.operation;



import java.util.LinkedHashSet;
import java.util.List;

import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;



/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for search operations.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
public interface PreParseSearchOperation
       extends PreParseOperation
{
  /**
   * Retrieves the raw, unprocessed base DN as included in the request
   * from the client.  This may or may not contain a valid DN, as no
   * validation will have been performed.
   *
   * @return  The raw, unprocessed base DN as included in the request
   *          from the client.
   */
  public ByteString getRawBaseDN();



  /**
   * Specifies the raw, unprocessed base DN for this search operation.
   *
   * @param  rawBaseDN  The raw, unprocessed base DN for this search
                        operation.
   */
  public void setRawBaseDN(ByteString rawBaseDN);



  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  public SearchScope getScope();



  /**
   * Specifies the scope for this search operation.
   *
   * @param  scope  The scope for this search operation.
   */
  public void setScope(SearchScope scope);



  /**
   * Retrieves the alias dereferencing policy for this search
   * operation.
   *
   * @return  The alias dereferencing policy for this search
   *          operation.
   */
  public DereferencePolicy getDerefPolicy();



  /**
   * Specifies the alias dereferencing policy for this search
   * operation.
   *
   * @param  derefPolicy  The alias dereferencing policy for this
   *                      search operation.
   */
  public void setDerefPolicy(DereferencePolicy derefPolicy);



  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  public int getSizeLimit();



  /**
   * Specifies the size limit for this search operation.
   *
   * @param  sizeLimit  The size limit for this search operation.
   */
  public void setSizeLimit(int sizeLimit);



  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  public int getTimeLimit();



  /**
   * Specifies the time limit for this search operation.
   *
   * @param  timeLimit  The time limit for this search operation.
   */
  public void setTimeLimit(int timeLimit);



  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  public boolean getTypesOnly();



  /**
   * Specifies the typesOnly flag for this search operation.
   *
   * @param  typesOnly  The typesOnly flag for this search operation.
   */
  public void setTypesOnly(boolean typesOnly);



  /**
   * Retrieves the raw, unprocessed search filter as included in the
   * request from the client.  It may or may not contain a valid
   * filter (e.g., unsupported attribute types or values with an
   * invalid syntax) because no validation will have been performed on
   * it.
   *
   * @return  The raw, unprocessed search filter as included in the
   *          request from the client.
   */
  public LDAPFilter getRawFilter();



  /**
   * Specifies the raw, unprocessed search filter as included in the
   * request from the client.
   *
   * @param  rawFilter  The raw, unprocessed search filter.
   */
  public void setRawFilter(LDAPFilter rawFilter);



  /**
   * Retrieves the set of requested attributes for this search
   * operation.  Its contents should not be be altered.
   *
   * @return  The set of requested attributes for this search
   *          operation.
   */
  public LinkedHashSet<String> getAttributes();



  /**
   * Specifies the set of requested attributes for this search
   * operation.
   *
   * @param  attributes  The set of requested attributes for this
   *                     search operation.
   */
  public void setAttributes(LinkedHashSet<String> attributes);



  /**
   * Returns the provided entry to the client.
   *
   * @param  entry     The entry that should be returned.
   * @param  controls  The set of controls to include with the entry
   *                   (may be {@code null} if no controls should be
   *                   included with the entry).
   *
   * @return  {@code true} if the caller should continue processing
   *          the search request and sending additional entries and
   *          references, or {@code false} if not for some reason
   *          (e.g., the size limit has been reached or the search has
   *          been abandoned).
   */
  public boolean returnEntry(Entry entry, List<Control> controls);



  /**
   * Returns the provided search result reference to the client.
   *
   * @param  reference  The search reference that should be returned.
   *
   * @return  {@code true} if the caller should continue processing
   *          the search request and sending additional entries and
   *          references, or {@code false} if not for some reason
   *          (e.g., the size limit has been reached or the search has
   *          been abandoned).
   */
  public boolean returnReference(SearchResultReference reference);
}

