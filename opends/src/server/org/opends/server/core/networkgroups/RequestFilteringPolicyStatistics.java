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

package org.opends.server.core.networkgroups;



import java.util.concurrent.atomic.AtomicLong;



/**
 * This class implements the statistics associated with a network group
 * request filtering policy.
 */
final class RequestFilteringPolicyStatistics
{
  private final AtomicLong rejectedAttributes = new AtomicLong();
  private final AtomicLong rejectedOperations = new AtomicLong();
  private final AtomicLong rejectedScopes = new AtomicLong();
  private final AtomicLong rejectedSubtrees = new AtomicLong();



  /**
   * Creates a new request filtering policy statistics.
   */
  RequestFilteringPolicyStatistics()
  {
    // Do nothing.
  }



  /**
   * Returns the number of rejected operations due to an attribute not
   * allowed by the request filtering policy.
   *
   * @return The number of rejected operations due to an invalid
   *         attribute.
   */
  long getRejectedAttributes()
  {
    return rejectedAttributes.get();
  }



  /**
   * Returns the number of rejected operations due to an operation type
   * not allowed by the request filtering policy.
   *
   * @return The number of rejected operations due to an invalid
   *         operation type.
   */
  long getRejectedOperations()
  {
    return rejectedOperations.get();
  }



  /**
   * Returns the number of rejected operations due to a scope not
   * allowed by the request filtering policy.
   *
   * @return The number of rejected operations due to an invalid scope.
   */
  long getRejectedScopes()
  {
    return rejectedScopes.get();
  }



  /**
   * Returns the number of rejected operations due to a subtree not
   * allowed by the request filtering policy.
   *
   * @return The number of rejected operations due to an invalid
   *         subtree.
   */
  long getRejectedSubtrees()
  {
    return rejectedSubtrees.get();
  }



  /**
   * Increments the number of rejected operations due to an attribute
   * not allowed by the request filtering policy.
   */
  void updateRejectedAttributes()
  {
    rejectedAttributes.incrementAndGet();
  }



  /**
   * Increments the number of rejected operations due to an operation
   * type not allowed by the request filtering policy.
   */
  void updateRejectedOperations()
  {
    rejectedOperations.incrementAndGet();
  }



  /**
   * Increments the number of rejected operations due to a search scope
   * not allowed by the request filtering policy.
   */
  void updateRejectedScopes()
  {
    rejectedScopes.incrementAndGet();
  }



  /**
   * Increments the number of rejected operations due to a subtree not
   * allowed by the request filtering policy.
   */
  void updateRejectedSubtrees()
  {
    rejectedSubtrees.incrementAndGet();
  }
}
