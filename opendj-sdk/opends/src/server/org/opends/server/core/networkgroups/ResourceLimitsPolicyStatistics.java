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



/**
 * This class implements the statistics associated to a network group
 * resource limit.
 */
final class ResourceLimitsPolicyStatistics
{
  // Updates to these are protected by a mutex in the resource limits
  // policy.
  private long clientConnections = 0;
  private long maxClientConnections = 0;
  private long totalClientConnections = 0;



  /**
   * Creates a new resource limits statistics.
   */
  ResourceLimitsPolicyStatistics()
  {
    // Do nothing.
  }



  /**
   * Updates these statistics to reflect a new client connection being
   * added.
   */
  void addClientConnection()
  {
    clientConnections++;
    totalClientConnections++;
    if (clientConnections > maxClientConnections)
    {
      maxClientConnections = clientConnections;
    }
  }



  /**
   * Returns the number of client connections currently in the network
   * group.
   *
   * @return The number of client connections currently in the network
   *         group.
   */
  long getClientConnections()
  {
    return clientConnections;
  }



  /**
   * Returns the maximum number of simultaneous client connections in
   * the network group.
   *
   * @return The maximum number of simultaneous client connections in
   *         the network group.
   */
  long getMaxClientConnections()
  {
    return maxClientConnections;
  }



  /**
   * Returns the total number of client connections managed by the
   * network group since its creation.
   *
   * @return The total number of client connections managed by the
   *         network group since its creation.
   */
  long getTotalClientConnections()
  {
    return totalClientConnections;
  }



  /**
   * Updates these statistics to reflect an existing client connection
   * being closed.
   */
  void removeClientConnection()
  {
    clientConnections--;
  }
}
