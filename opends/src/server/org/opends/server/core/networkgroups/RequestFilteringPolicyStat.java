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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.core.networkgroups;

/**
 * This class implements the statistics associated to a
 * network group request filtering policy.
 */
public class RequestFilteringPolicyStat {
  private long rejectedAttributes;
  private long rejectedOperations;
  private long rejectedSubtrees;
  private long rejectedScopes;
  private Object statLock;

  /**
   * Constructor.
   */
  public RequestFilteringPolicyStat() {
    rejectedAttributes = 0;
    rejectedOperations = 0;
    rejectedSubtrees = 0;
    rejectedScopes = 0;
    statLock = new Object();
  }

  /**
   * Increments the number of rejected operations due to an
   * attribute not allowed by the request filtering policy.
   */
  public void updateRejectedAttributes() {
    synchronized(statLock) {
      rejectedAttributes++;
    }
  }

  /**
   * Increments the number of rejected operations due to an
   * operation type not allowed by the request filtering policy.
   */
  public void updateRejectedOperations() {
    synchronized(statLock) {
      rejectedOperations++;
    }
  }

  /**
   * Increments the number of rejected operations due to a subtree
   * not allowed by the request filtering policy.
   */
  public void updateRejectedSubtrees() {
    synchronized(statLock) {
      rejectedSubtrees++;
    }
  }

  /**
   * Increments the number of rejected operations due to a search scope
   * not allowed by the request filtering policy.
   */
  public void updateRejectedScopes() {
    synchronized(statLock) {
      rejectedScopes++;
    }
  }

  /**
   * Retrieves the number of rejected operations due to an
   * attribute not allowed by the request filtering policy.
   * @return number of rejected operations due to an invalid attribute
   */
  public long getRejectedAttributes() {
    return rejectedAttributes;
  }

  /**
   * Retrieves the number of rejected operations due to an
   * operation type not allowed by the request filtering policy.
   * @return number of rejected operations due to an invalid op type
   */
  public long getRejectedOperations() {
    return rejectedOperations;
  }

  /**
   * Retrieves the number of rejected operations due to a
   * subtree not allowed by the request filtering policy.
   * @return number of rejected operations due to an invalid subtree
   */
  public long getRejectedSubtrees() {
    return rejectedSubtrees;
  }

  /**
   * Retrieves the number of rejected operations due to a
   * scope not allowed by the request filtering policy.
   * @return number of rejected operations due to an invalid scope
   */
  public long getRejectedScopes() {
    return rejectedScopes;
  }
}
