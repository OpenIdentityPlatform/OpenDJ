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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.LinkedHashSet;

import org.opends.server.types.*;


/**
 * This class defines a set of methods that are available for use by
 * search result reference plugins.  Note that this interface is
 * intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface SearchReferenceSearchOperation
       extends InProgressOperation
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
   * Retrieves the base DN for this search operation.
   *
   * @return  The base DN for this search operation.
   */
  public DN getBaseDN();



  /**
   * Retrieves the scope for this search operation.
   *
   * @return  The scope for this search operation.
   */
  public SearchScope getScope();



  /**
   * Retrieves the alias dereferencing policy for this search
   * operation.
   *
   * @return  The alias dereferencing policy for this search
   *          operation.
   */
  public DereferencePolicy getDerefPolicy();



  /**
   * Retrieves the size limit for this search operation.
   *
   * @return  The size limit for this search operation.
   */
  public int getSizeLimit();



  /**
   * Retrieves the time limit for this search operation.
   *
   * @return  The time limit for this search operation.
   */
  public int getTimeLimit();



  /**
   * Retrieves the typesOnly flag for this search operation.
   *
   * @return  The typesOnly flag for this search operation.
   */
  public boolean getTypesOnly();



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
  public RawFilter getRawFilter();



  /**
   * Retrieves the filter for this search operation.
   *
   * @return  The filter for this search operation.
   */
  public SearchFilter getFilter();



  /**
   * Retrieves the set of requested attributes for this search
   * operation.  Its contents should not be altered.
   *
   * @return  The set of requested attributes for this search
   *          operation.
   */
  public LinkedHashSet<String> getAttributes();
}

