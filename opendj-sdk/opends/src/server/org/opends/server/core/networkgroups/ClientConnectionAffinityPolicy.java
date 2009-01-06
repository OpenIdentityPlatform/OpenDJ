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

import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AffinityPolicy;


/**
 * This enumerate defines the client connection affinity policy.
 * An affinity is the ability for the server to bypass some
 * route algorithm so that requests are always sent to a specific
 * data source.
 */
public enum ClientConnectionAffinityPolicy
{
  /**
   * Disables the client connection affinity.
   */
  NONE,

  /**
   * Routes the first read request to the data source to which
   * a previous write request has been routed to. This affinity
   * is useful when a client application performs a read request
   * after a write request and the read request should return
   * consistent data.
   */
  FIRST_READ_REQUEST_AFTER_WRITE_REQUEST,

  /**
   * Routes all the write requests to the data source to which
   * a previous write request has been routed to. This affinity
   * policy is useful for batch update where a parent entry and
   * its subordinates must be sent to the same data source.
   */
  ALL_WRITE_REQUESTS_AFTER_FIRST_WRITE_REQUEST,

  /**
   * Routes all the requests to the data source to which a
   * previous write request has been routed to.
   */
  ALL_REQUESTS_AFTER_FIRST_WRITE_REQUEST,

  /**
   * Routes all the requests to the data source to which a
   * previous request has been routed to. This affinity policy
   * allows to create a kind of tunnel between a client application
   * and a data source.
   */
  ALL_REQUESTS_AFTER_FIRST_REQUEST;


  /**
   * Indicates whether the current policy defines an active affinity
   * policy or not.
   *
   * @return <code>true</code> if the current value of the policy indicates
   *         an active affinity.
   */
  public boolean affinityIsActive()
  {
    return (this != NONE);
  }


  /**
   * Returns the client connection affinity policy that matches the
   * affinity policy as defined by the administration framework.
   *
   * @param  affinityPolicy
   *         The administration framework affinity policy for which we
   *         want to make a determination.
   *
   * @return the client connection affinity policy that matches an affinity
   *         policy as defined by the administration framework.
   */
  public static ClientConnectionAffinityPolicy
      toClientConnectionAffinityPolicy(AffinityPolicy affinityPolicy)
  {
    switch (affinityPolicy)
    {
      case NONE:
        return NONE;

      case ALL_REQUESTS_AFTER_FIRST_WRITE_REQUEST:
        return ALL_REQUESTS_AFTER_FIRST_WRITE_REQUEST;

      case ALL_REQUESTS_AFTER_FIRST_REQUEST:
        return ALL_REQUESTS_AFTER_FIRST_REQUEST;

      case ALL_WRITE_REQUESTS_AFTER_FIRST_WRITE_REQUEST:
        return ALL_WRITE_REQUESTS_AFTER_FIRST_WRITE_REQUEST;

      case FIRST_READ_REQUEST_AFTER_WRITE_REQUEST:
        return FIRST_READ_REQUEST_AFTER_WRITE_REQUEST;

      default:
        throw new AssertionError(
          "Unexpected afinity policy value " + affinityPolicy);
    }
  }

}
