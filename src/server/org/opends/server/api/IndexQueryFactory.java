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
package org.opends.server.api;



import java.util.Collection;

import org.opends.server.types.ByteSequence;



/**
 * A factory for creating arbitrarily complex index queries. This
 * interface is implemented by the underlying backend implementation
 * and passed to extensible matching rules so that they can construct
 * arbitrarily complex index queries.
 *
 * @param <T>
 *          The type of query created by this factory.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate = false,
    mayExtend = true,
    mayInvoke = false)
public interface IndexQueryFactory<T>
{

  /**
   * Returns a query requesting an index record matching the provided
   * key.
   *
   * @param indexID
   *          An identifier of the index type.
   * @param key
   *          A byte sequence containing the key.
   * @return A query requesting the index record matching the key.
   */
  T createExactMatchQuery(String indexID, ByteSequence key);



  /**
   * Returns a query requesting all index records. A backend
   * implementation may choose to return all or no records as part of
   * the optimization.
   *
   * @return A query requesting all index records.
   */
  T createMatchAllQuery();



  /**
   * Returns a query requesting all index records in the specified
   * range.
   *
   * @param indexID
   *          An identifier of the index type.
   * @param lower
   *          The lower bound of the range. A 0 length byte array
   *          indicates no lower bound and the range will start from
   *          the smallest key.
   * @param upper
   *          The upper bound of the range. A 0 length byte array
   *          indicates no upper bound and the range will end at the
   *          largest key.
   * @param lowerIncluded
   *          true if a key exactly matching the lower bound is
   *          included in the range, false if only keys strictly
   *          greater than the lower bound are included.This value
   *          is ignored if the lower bound is not specified.
   * @param upperIncluded
   *          true if a key exactly matching the upper bound is
   *          included in the range, false if only keys strictly
   *          less than the upper bound are included. This value is
   *          ignored if the upper bound is not specified.
   * @return A query requesting all index records in the specified
   *         range.
   */
  T createRangeMatchQuery(String indexID, ByteSequence lower,
      ByteSequence upper, boolean lowerIncluded,
      boolean upperIncluded);



  /**
   * Returns a query which returns the intersection of a collection of
   * sub-queries.
   *
   * @param subquery
   *          A collection of sub-queries.
   * @return A query which returns the intersection of a collection of
   *         sub-queries.
   */
  T createIntersectionQuery(Collection<T> subquery);



  /**
   * Returns a query which combines the results of a collection of
   * sub-queries.
   *
   * @param subquery
   *          A collection of sub-queries.
   * @return A query which combines the results of a collection of
   *         sub-queries.
   */
  T createUnionQuery(Collection<T> subquery);
}