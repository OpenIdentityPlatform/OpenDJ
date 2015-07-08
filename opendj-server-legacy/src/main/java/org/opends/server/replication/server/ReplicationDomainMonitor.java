/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.MonitorMsg;
import org.opends.server.replication.protocol.MonitorRequestMsg;
import org.opends.server.types.DN;
import org.opends.server.util.TimeThread;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class maintains monitor data for a replication domain.
 */
class ReplicationDomainMonitor
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * The monitor data consolidated over the topology.
   */
  private volatile ReplicationDomainMonitorData monitorData =
      new ReplicationDomainMonitorData();

  /**
   * This lock guards against multiple concurrent monitor data recalculation.
   */
  private final Object pendingMonitorLock = new Object();

  /** Guarded by pendingMonitorLock. */
  private long monitorDataLastBuildDate;

  /**
   * The set of replication servers which are already known to be slow to send
   * monitor data.
   * <p>
   * Guarded by pendingMonitorLock.
   */
  private final Set<Integer> monitorDataLateServers = new HashSet<>();

  /** This lock serializes updates to the pending monitor data. */
  private final Object pendingMonitorDataLock = new Object();

  /**
   * Monitor data which is currently being calculated.
   * <p>
   * Guarded by pendingMonitorDataLock.
   */
  private ReplicationDomainMonitorData pendingMonitorData;

  /**
   * A set containing the IDs of servers from which we are currently expecting
   * monitor responses. When a response is received from a server we remove the
   * ID from this table, and count down the latch if the ID was in the table.
   * <p>
   * Guarded by pendingMonitorDataLock.
   */
  private final Set<Integer> pendingMonitorDataServerIDs = new HashSet<>();

  /**
   * This latch is non-null and is used in order to count incoming responses as
   * they arrive. Since incoming response may arrive at any time, even when
   * there is no pending monitor request, access to the latch must be guarded.
   * <p>
   * Guarded by pendingMonitorDataLock.
   */
  private CountDownLatch pendingMonitorDataLatch;

  /**
   * TODO: Remote monitor data cache lifetime is 500ms/should be configurable.
   */
  private final long monitorDataLifeTime = 500;

  /** The replication domain monitored by this class. */
  private final ReplicationServerDomain domain;


  /**
   * Builds an object of this class.
   *
   * @param replicationDomain
   *          The replication domain that will be monitored by this class
   */
  public ReplicationDomainMonitor(ReplicationServerDomain replicationDomain)
  {
    this.domain = replicationDomain;
  }

  /**
   * Returns the latest monitor data available for this replication server
   * domain.
   *
   * @return The latest monitor data available for this replication server
   *         domain, which is never {@code null}.
   */
  public ReplicationDomainMonitorData getMonitorData()
  {
    return monitorData;
  }

  /**
   * Recomputes the monitor data for this replication server domain.
   *
   * @return The recomputed monitor data for this replication server domain.
   * @throws InterruptedException
   *           If this thread is interrupted while waiting for a response.
   */
  public ReplicationDomainMonitorData recomputeMonitorData()
      throws InterruptedException
  {
    // Only allow monitor recalculation at a time.
    synchronized (pendingMonitorLock)
    {
      if ((monitorDataLastBuildDate + monitorDataLifeTime) < TimeThread
          .getTime())
      {
        try
        {
          DN baseDN = domain.getBaseDN();

          // Prevent out of band monitor responses from updating our pending
          // table until we are ready.
          synchronized (pendingMonitorDataLock)
          {
            // Clear the pending monitor data.
            pendingMonitorDataServerIDs.clear();
            pendingMonitorData = new ReplicationDomainMonitorData();

            initializePendingMonitorData();

            // Send the monitor requests to the connected replication servers.
            for (ServerHandler rs : domain.getConnectedRSs().values())
            {
              final int serverId = rs.getServerId();

              MonitorRequestMsg msg =
                  new MonitorRequestMsg(domain.getLocalRSServerId(), serverId);
              try
              {
                rs.send(msg);

                // Only register this server ID to pending table if we were able
                // to send the message.
                pendingMonitorDataServerIDs.add(serverId);
              }
              catch (IOException e)
              {
                // Log a message and do a best effort from here.
                logger.error(ERR_SENDING_REMOTE_MONITOR_DATA_REQUEST, baseDN, serverId, e.getMessage());
              }
            }

            // Create the pending response latch based on the number of expected
            // monitor responses.
            pendingMonitorDataLatch =
                new CountDownLatch(pendingMonitorDataServerIDs.size());
          }

          // Wait for the responses to come back.
          pendingMonitorDataLatch.await(5, TimeUnit.SECONDS);

          // Log messages for replication servers that have gone or come back.
          synchronized (pendingMonitorDataLock)
          {
            // Log servers that have come back.
            for (int serverId : monitorDataLateServers)
            {
              // Ensure that we only log once per server: don't fill the
              // error log with repeated messages.
              if (!pendingMonitorDataServerIDs.contains(serverId))
              {
                logger.info(NOTE_MONITOR_DATA_RECEIVED, baseDN, serverId);
              }
            }

            // Log servers that have gone away.
            for (int serverId : pendingMonitorDataServerIDs)
            {
              // Ensure that we only log once per server: don't fill the
              // error log with repeated messages.
              if (!monitorDataLateServers.contains(serverId))
              {
                logger.warn(WARN_MISSING_REMOTE_MONITOR_DATA, baseDN, serverId);
              }
            }

            // Remember which servers were late this time.
            monitorDataLateServers.clear();
            monitorDataLateServers.addAll(pendingMonitorDataServerIDs);
          }

          // Store the new computed data as the reference
          synchronized (pendingMonitorDataLock)
          {
            // Now we have the expected answers or an error occurred
            pendingMonitorData.completeComputing();
            monitorData = pendingMonitorData;
            monitorDataLastBuildDate = TimeThread.getTime();
          }
        }
        finally
        {
          synchronized (pendingMonitorDataLock)
          {
            // Clear pending state.
            pendingMonitorData = null;
            pendingMonitorDataLatch = null;
            pendingMonitorDataServerIDs.clear();
          }
        }
      }
    }

    return monitorData;
  }

  /**
   * Start collecting global monitoring information for the replication domain.
   */
  private void initializePendingMonitorData()
  {
    // Let's process our directly connected DS
    // - in the ServerHandler for a given DS1, the stored state contains :
    // -- the max CSN produced by DS1
    // -- the last CSN consumed by DS1 from DS2..n
    // - in the ReplicationDomainDB/ReplicaDB, the built-in state contains:
    // -- the max CSN produced by each server
    // So for a given DS connected we can take the state and the max from
    // the DS/state.

    for (ServerHandler ds : domain.getConnectedDSs().values())
    {
      final int serverId = ds.getServerId();
      final ServerState dsState = ds.getServerState().duplicate();

      CSN maxCSN = dsState.getCSN(serverId);
      if (maxCSN == null)
      {
        // This directly connected LS has never produced any change
        maxCSN = new CSN(0, 0, serverId);
      }
      pendingMonitorData.setMaxCSN(maxCSN);
      pendingMonitorData.setLDAPServerState(serverId, dsState);
      pendingMonitorData.setFirstMissingDate(serverId,
          ds.getApproxFirstMissingDate());
    }

    // Then initialize the max CSN for the LS that produced something
    // - from our own local db state
    // - whatever they are directly or indirectly connected
    final ServerState dbServerState = domain.getLatestServerState();
    pendingMonitorData.setRSState(domain.getLocalRSServerId(), dbServerState);
    for (CSN storedCSN : dbServerState)
    {
      pendingMonitorData.setMaxCSN(storedCSN);
    }
  }

  /**
   * Processes a Monitor message receives from a remote Replication Server and
   * stores the data received.
   *
   * @param msg
   *          The message to be processed.
   * @param serverId
   *          server handler that is receiving the message.
   */
  public void receiveMonitorDataResponse(MonitorMsg msg, int serverId)
  {
    synchronized (pendingMonitorDataLock)
    {
      if (pendingMonitorData == null)
      {
        // This is a response for an earlier request whose computing is
        // already complete.
        logger.debug(INFO_IGNORING_REMOTE_MONITOR_DATA, domain.getBaseDN(), msg.getSenderID());
        return;
      }

      try
      {
        // Here is the RS state : list <serverID, lastCSN>
        // For each LDAP Server, we keep the max CSN across the RSes
        ServerState replServerState = msg.getReplServerDbState();
        pendingMonitorData.setMaxCSNs(replServerState);

        // store the remote RS states.
        pendingMonitorData.setRSState(msg.getSenderID(), replServerState);

        // Store the remote LDAP servers states
        for (int dsServerId : toIterable(msg.ldapIterator()))
        {
          ServerState dsServerState = msg.getLDAPServerState(dsServerId);
          pendingMonitorData.setMaxCSNs(dsServerState);
          pendingMonitorData.setLDAPServerState(dsServerId, dsServerState);
          pendingMonitorData.setFirstMissingDate(dsServerId,
              msg.getLDAPApproxFirstMissingDate(dsServerId));
        }

        // Process the latency reported by the remote RSi on its connections
        // to the other RSes
        for (int rsServerId : toIterable(msg.rsIterator()))
        {
          long newFmd = msg.getRSApproxFirstMissingDate(rsServerId);
          if (rsServerId == domain.getLocalRSServerId())
          {
            // this is the latency of the remote RSi regarding the current RS
            // let's update the first missing date of my connected LS
            for (DataServerHandler ds : domain.getConnectedDSs().values())
            {
              int connectedServerId = ds.getServerId();
              pendingMonitorData.setFirstMissingDate(connectedServerId, newFmd);
            }
          }
          else
          {
            // this is the latency of the remote RSi regarding another RSj
            // let's update the latency of the LSes connected to RSj
            ReplicationServerHandler rsjHdr =
                domain.getConnectedRSs().get(rsServerId);
            if (rsjHdr != null)
            {
              for (int remoteServerId : rsjHdr.getConnectedDirectoryServerIds())
              {
                pendingMonitorData.setFirstMissingDate(remoteServerId, newFmd);
              }
            }
          }
        }
      }
      catch (RuntimeException e)
      {
        // FIXME: do we really expect these???
        logger.error(ERR_PROCESSING_REMOTE_MONITOR_DATA, e.getMessage() + " " + stackTraceToSingleLineString(e));
      }
      finally
      {
        // Decreases the number of expected responses and potentially
        // wakes up the waiting requester thread.
        if (pendingMonitorDataServerIDs.remove(serverId))
        {
          pendingMonitorDataLatch.countDown();
        }
      }
    }
  }

}
