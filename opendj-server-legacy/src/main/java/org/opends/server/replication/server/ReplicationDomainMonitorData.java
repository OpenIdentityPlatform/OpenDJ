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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.util.TimeThread;

/** This class defines the Monitor Data that are consolidated across a replication domain. */
class ReplicationDomainMonitorData
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * - For each server, the max (most recent) CSN produced
   *
   * - For each server, its state i.e. the last processed from of each
   *   other LDAP server.
   *   The change latency (missing changes) will be
   *   the difference between the max above and the state here
   *
   * - For each server, the date of the first missing change.
   *   The time latency (delay) will be the difference between now and the
   *   date of the first missing change.
   */

  /** BaseDN being monitored. This field is only used for debugging purposes. */
  private final DN baseDN;

  /** For each LDAP server, its server state. This is the point-of-view of the DSs. */
  private final ConcurrentMap<Integer, ServerState> ldapStates = new ConcurrentHashMap<>();
  /** A Map containing the ServerStates of each RS. This is the point-of-view of the RSs. */
  private final ConcurrentMap<Integer, ServerState> rsStates = new ConcurrentHashMap<>();
  /**
   * For each LDAP server, the last(max) CSN it published.
   * <p>
   * Union of the view from all the {@code ldapStates} and {@code rsStates}.
   */
  private final ConcurrentMap<Integer, CSN> maxCSNs = new ConcurrentHashMap<>();

  /** For each LDAP server, an approximation of the date of the first missing change. */
  private final ConcurrentMap<Integer, Long> firstMissingDates = new ConcurrentHashMap<>();
  private final ConcurrentMap<Integer, Long> missingChanges = new ConcurrentHashMap<>();
  private final ConcurrentMap<Integer, Long> missingChangesRS = new ConcurrentHashMap<>();

  public ReplicationDomainMonitorData(DN baseDN)
  {
    this.baseDN = baseDN;
  }

  /**
   * Get an approximation of the latency delay of the replication.
   * @param serverId The server ID.
   * @return The delay
   */
  public long getApproxDelay(int serverId)
  {
    Long afmd = firstMissingDates.get(serverId);
    if (afmd != null && afmd > 0) {
      return (TimeThread.getTime() - afmd) / 1000;
    }
    return 0;
  }

  /**
   * Get an approximation of the date of the first missing update.
   * @param serverId The server ID.
   * @return The date.
   */
  public long getApproxFirstMissingDate(int serverId)
  {
    return getValueOrZero(firstMissingDates.get(serverId));
  }

  /**
   * Get the number of missing changes.
   * @param serverId The server ID.
   * @return The number of missing changes.
   */
  public long getMissingChanges(int serverId)
  {
    return getValueOrZero(missingChanges.get(serverId));
  }

  /**
   * Get the number of missing changes for a Replication Server.
   *
   * @param serverId   The server ID.
   *
   * @return           The number of missing changes.
   */
  public long getMissingChangesRS(int serverId)
  {
    return getValueOrZero(missingChangesRS.get(serverId));
  }

  private long getValueOrZero(Long res)
  {
    return res != null ? res : 0;
  }

  /** Build the monitor data that are computed from the collected ones. */
  public void completeComputing()
  {
    StringBuilder mds = new StringBuilder();

    // Computes the missing changes counters for LDAP servers
    // For each LSi ,
    //   Regarding each other LSj
    //    Sum the difference : max(LSj) - state(LSi)

    for (Entry<Integer, ServerState> entry : ldapStates.entrySet())
    {
      clear(mds);
      final Integer lsiServerId = entry.getKey();
      final ServerState lsiState = entry.getValue();
      long lsiMissingChanges = computeMissingChanges(mds, lsiServerId, lsiState);
      if (logger.isTraceEnabled()) {
        mds.append("=").append(lsiMissingChanges);
      }

      this.missingChanges.put(lsiServerId, lsiMissingChanges);
      logger.trace("Complete monitor data : Missing changes    (%5d)=%s", lsiServerId, mds);
    }

    // Computes the missing changes counters for RS :
    // Sum the difference of sequence numbers for each element in the States.

    for (Entry<Integer, ServerState> entry : rsStates.entrySet())
    {
      clear(mds);
      final Integer lsiServerId = entry.getKey();
      final ServerState lsiState = entry.getValue();
      long lsiMissingChanges = computeMissingChanges(mds, null, lsiState);
      if (logger.isTraceEnabled()) {
        mds.append("=").append(lsiMissingChanges);
      }

      this.missingChangesRS.put(lsiServerId, lsiMissingChanges);
      logger.trace("Complete monitor data : Missing changes RS (%5d)=%s", lsiServerId, mds);
    }
  }

  private void clear(StringBuilder mds)
  {
    mds.setLength(0);
  }

  private long computeMissingChanges(StringBuilder mds, final Integer lsiServerId, final ServerState lsiState)
  {
    long lsiMissingChanges = 0;
    if (lsiState != null) {
      for (Entry<Integer, CSN> entry2 : maxCSNs.entrySet())
      {
        final Integer lsjServerId = entry2.getKey();
        final CSN lsjMaxCSN = entry2.getValue();
        CSN lsiLastCSN = lsiState.getCSN(lsjServerId);

        int missingChangesLsiLsj = CSN.diffSeqNum(lsjMaxCSN, lsiLastCSN);

        if (logger.isTraceEnabled()) {
          if (mds.length() > 0)
          {
            mds.append(" + ");
          }
          mds.append("diff(")
             .append(lsjMaxCSN).append("-").append(lsiLastCSN).append(")=").append(missingChangesLsiLsj);
        }
        /*
        THIS BIT OF CODE IS IRRELEVANT TO RSs.
        Regarding a DS that is generating changes. If it is a local DS1,
        we get its server state, store it, then retrieve server states of
        remote DSs. When a remote server state is coming, it may contain
        a CSN for DS1 which is newer than the one we locally
        stored in the server state of DS1. To prevent seeing DS1 has
        missing changes whereas it is wrong, we replace the value with 0
        if it is a low value. We cannot overwrite big values as they may be
        useful for a local server retrieving changes it generated earlier,
        when it is recovering from an old snapshot and the local RS is
        sending him the changes it is missing.
        */
        if (lsjServerId.equals(lsiServerId) && missingChangesLsiLsj <= 50)
        {
          missingChangesLsiLsj = 0;
          if (logger.isTraceEnabled()) {
            mds.append(" (diff replaced by 0 as for server id ").append(lsiServerId).append(")");
          }
        }

        lsiMissingChanges += missingChangesLsiLsj;
      }
    }
    return lsiMissingChanges;
  }

  @Override
  public String toString()
  {
    StringBuilder mds = new StringBuilder("Monitor data='").append(baseDN).append("'\n");

    // maxCSNs
    for (Entry<Integer, CSN> entry : maxCSNs.entrySet())
    {
      final Integer serverId = entry.getKey();
      final CSN csn = entry.getValue();
      mds.append("\nmaxCSNs(").append(serverId).append(")= ").append(csn.toStringUI());
    }

    // LDAP data
    for (Entry<Integer, ServerState> entry : ldapStates.entrySet())
    {
      final Integer serverId = entry.getKey();
      final ServerState ss = entry.getValue();
      mds.append("\nLSData(").append(serverId).append(")=\t")
         .append("state=[").append(ss)
         .append("] afmd=").append(getApproxFirstMissingDate(serverId))
         .append(" missingDelay=").append(getApproxDelay(serverId))
         .append(" missingCount=").append(missingChanges.get(serverId));
    }

    // RS data
    for (Entry<Integer, ServerState> entry : rsStates.entrySet())
    {
      final Integer serverId = entry.getKey();
      final ServerState ss = entry.getValue();
      mds.append("\nRSData(").append(serverId).append(")=\t")
         .append("state=[").append(ss)
         .append("] missingCount=").append(missingChangesRS.get(serverId));
    }

    mds.append("\n--");
    return mds.toString();
  }

  /**
   * From a provided state, sets the max CSN of the monitor data.
   * @param state the provided state.
   */
  public void setMaxCSNs(ServerState state)
  {
    for (CSN newCSN : state)
    {
      setMaxCSN(newCSN);
    }
  }

  /**
   * For the provided serverId, sets the provided CSN as the max if
   * it is newer than the current max.
   * @param newCSN the provided new CSN
   */
  public void setMaxCSN(CSN newCSN)
  {
    if (newCSN != null)
    {
      while (!setMaxCsn0(newCSN))
      {
        // try setting up the max CSN until the CSN is no longer the max one, or until it succeeds
      }
    }
  }

  private boolean setMaxCsn0(CSN newCSN)
  {
    int serverId = newCSN.getServerId();
    CSN currentMaxCSN = maxCSNs.get(serverId);
    if (currentMaxCSN == null)
    {
      return maxCSNs.putIfAbsent(serverId, newCSN) == null;
    }
    else if (newCSN.isNewerThan(currentMaxCSN))
    {
      return maxCSNs.replace(serverId, currentMaxCSN, newCSN);
    }
    return true;
  }

  /**
   * Get the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @return The server state.
   */
  public ServerState getLDAPServerState(int serverId)
  {
    return ldapStates.get(serverId);
  }

  /**
   * Set the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @param state The server state.
   */
  public void setLDAPServerState(int serverId, ServerState state)
  {
    ldapStates.put(serverId, state);
  }

  /**
   * Set the state of the RS with the provided serverId.
   *
   * @param serverId   The server ID.
   * @param state      The server state.
   */
  public void setRSState(int serverId, ServerState state)
  {
    rsStates.put(serverId, state);
    setMaxCSNs(state);
  }

  /**
   * Set the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @param newFmd The new first missing date.
   */
  public void setFirstMissingDate(int serverId, long newFmd)
  {
    while (!setFirstMissingDate0(serverId, newFmd))
    {
      // try setting up the first missing date
      // until the first missing date is no longer the min one, or until it succeeds
    }
  }

  public boolean setFirstMissingDate0(int serverId, long newFmd)
  {
    Long currentFmd = firstMissingDates.get(serverId);
    if (currentFmd == null)
    {
      return firstMissingDates.putIfAbsent(serverId, newFmd) == null;
    }
    else if (newFmd != 0 && (newFmd < currentFmd || currentFmd == 0))
    {
      return firstMissingDates.replace(serverId, currentFmd, newFmd);
    }
    return true;
  }

  /**
   * Returns an iterator on the serverId of the Replicas for which
   * we have monitoring data.
   *
   * @return The iterator.
   */
  public Iterator<Integer> ldapIterator()
  {
    return ldapStates.keySet().iterator();
  }

  /**
   * Returns an iterator on the serverId of the Replication Servers for which
   * we have monitoring data.
   *
   * @return The iterator.
   */
  public Iterator<Integer> rsIterator()
  {
    return rsStates.keySet().iterator();
  }

  /**
   * Get the state of the RS server with the provided serverId.
   *
   * @param serverId The server ID.
   * @return The server state.
   */
  public ServerState getRSStates(int serverId)
  {
    return rsStates.get(serverId);
  }

  /**
   * Get an approximation of the date of the first missing update.
   *
   * @param serverId The server ID.
   * @return The date.
   */
  public long getRSApproxFirstMissingDate(int serverId)
  {
    // For now, we do store RS first missing change date
    return 0;
  }
}
