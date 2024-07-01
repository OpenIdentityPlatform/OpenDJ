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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.opends.server.types.HostPort;

/**
 * Named set of servers defining a distributed service. A distribution load balancer expects data to
 * be split up into shards referred to as "partitions", each partition exposing the same set of
 * naming contexts, but only a sub-set of the data. For example, a distribution might have two
 * partitions, the first containing all users whose name begins with A-M, and the second containing
 * all users whose name begins with N-Z. Both partitions have the same naming contexts, e.g:
 * <dl>
 * <dt>dc=example,dc=com
 * <dd>unsharded parent naming context replicated across all servers.<br>
 * Contains data common to all partitions, such as ACIs, groups, etc.
 * <dt>ou=people,dc=example,dc=com<
 * <dd>sharded naming context whose content (the users) is split up according to some function, e.g.
 * consistent hashing.
 * </dl>
 *
 * @see ServiceDiscoveryMechanism#getPartitions(Collection)
 */
public final class Partition
{
  /** A server from a partition. */
  public static class Server
  {
    private final HostPort hostPort;
    /** Connection options for ConnectionFactory. */
    private final Options options;

    /**
     * Builds a server for a partition.
     * @param hostPort
     *          the host and port to use when contacting the server
     * @param options
     *          the connection options to use for connecting to the server
     */
    Server(HostPort hostPort, Options options)
    {
      Reject.ifNull(hostPort);
      this.hostPort = hostPort;
      this.options = Options.unmodifiableCopyOf(options);
    }

    @Override
    public final int hashCode()
    {
      return hostPort.hashCode();
    }

    @Override
    public final boolean equals(Object obj)
    {
      if (this == obj)
      {
        return true;
      }
      if (!(obj instanceof Server))
      {
        return false;
      }
      return hostPort.equals(((Server) obj).getHostPort());
    }

    /**
     * Returns the host port for this server.
     *
     * @return the host port for this server
     */
    public final HostPort getHostPort()
    {
      return hostPort;
    }

    /**
     * Return the connections options for this server.
     *
     * @return the connections options for this server.
     */
    public final Options getOptions()
    {
      return options;
    }
  }

  private final String partitionId;
  private final Set<Server> primaryServers;
  private final Set<Server> secondaryServers;

  Partition(String partitionId, Collection<Server> primaryServers, Collection<Server> secondaryServers)
  {
    this.partitionId = partitionId;
    this.primaryServers = Collections.unmodifiableSet(new LinkedHashSet<>(primaryServers));
    this.secondaryServers = Collections.unmodifiableSet(new LinkedHashSet<>(secondaryServers));
  }

  /**
   * Returns the set of primary server to use.
   *
   * @return the set of primary server to use.
   */
  public Set<Server> getPrimaryServers()
  {
    return primaryServers;
  }

  /**
   * Returns the set of fallback servers to use.
   *
   * @return the set of fallback servers to use
   */
  public Set<Server> getSecondaryServers()
  {
    return secondaryServers;
  }

  /**
   * Returns the ID for the partition.
   *
   * @return the ID for the partition
   */
  public String getPartitionId()
  {
    return partitionId;
  }
}
