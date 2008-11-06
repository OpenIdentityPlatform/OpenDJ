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
 * This class implements the statistics associated to a network group
 * resource limit.
 */
public class ResourceLimitsStat {
    private long clientConnections;
    private long maxClientConnections;
    private long totalClientConnections;

    /**
     * Constructor.
     * @param clientConnections number of client connections currently
     *        in the network group
     * @param maxClientConnections maximum number of simultaneous
     *        connections in the network group
     * @param totalClientConnections total number of client connections
     *        managed by the network group since its creation
     */
    public ResourceLimitsStat(
        long clientConnections,
        long maxClientConnections,
        long totalClientConnections) {
      this.clientConnections = clientConnections;
      this.maxClientConnections = maxClientConnections;
      this.totalClientConnections = totalClientConnections;
    }

    /**
     * Returns the number of client connections currently in the network
     * group.
     * @return number of client connections currently in the network
     * group
     */
    public long getClientConnections() {
      return clientConnections;
    }

    /**
     * Returns the maximum number of simultaneous client connections in
     * the network group.
     * @return the maximum number of simultaneous client connections in
     * the network group
     */
    public long getMaxClientConnections() {
      return maxClientConnections;
    }

    /**
     * Returns the total number of client connections managed by the
     * network group since its creation.
     * @return the cumulated number of client connections managed by
     * the network group since its creation
     */
    public long getTotalClientConnections() {
      return totalClientConnections;
    }
}
