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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.types.operation;

import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.opends.server.types.RawFilter;
import org.opends.server.types.SearchFilter;

/**
 * This class defines a set of methods that are available for use by
 * post-response plugins for search operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PostResponseSearchOperation
       extends PostResponseOperation
{
  /**
   * Retrieves the raw, unprocessed base DN as included in the request
   * from the client.  This may or may not contain a valid DN, as no
   * validation will have been performed.
   *
   * @return  The raw, unprocessed base DN as included in the request
   *          from the client.
   */
  ByteString getRawBaseDN();



  /**
   * Retrieves the base DN for this search operation.
   *
   * @return  The base DN for this search operation.
   */
  DN getBaseDN();



  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  SearchScope getScope();



  /**
   * Retrieves the alias dereferencing policy for this search
   * operation.
   *
   * @return  The alias dereferencing policy for this search
   *          operation.
   */
  DereferenceAliasesPolicy getDerefPolicy();



  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  int getSizeLimit();



  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  int getTimeLimit();



  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  boolean getTypesOnly();



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
  RawFilter getRawFilter();



  /**
   * Retrieves the filter for this search operation.
   *
   * @return  The filter for this search operation.
   */
  SearchFilter getFilter();



  /**
   * Retrieves the set of requested attributes for this search
   * operation.  Its contents should not be altered.
   *
   * @return  The set of requested attributes for this search
   *          operation.
   */
  Set<String> getAttributes();



  /**
   * Retrieves the number of entries sent to the client for this
   * search operation.
   *
   * @return  The number of entries sent to the client for this search
   *          operation.
   */
  int getEntriesSent();



  /**
   * Retrieves the number of search references sent to the client for
   * this search operation.
   *
   * @return  The number of search references sent to the client for
   *          this search operation.
   */
  int getReferencesSent();
}

