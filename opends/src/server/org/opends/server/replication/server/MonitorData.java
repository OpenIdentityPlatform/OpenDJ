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
package org.opends.server.replication.server;

import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ServerState;
import org.opends.server.util.TimeThread;

/**
 * This class defines the Monitor Data that are consolidated across the
 * whole replication topology.
 */
public class MonitorData
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   *
   * - For each server, the max (most recent) CN produced
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

  /* The date of the last time they have been elaborated */
  private long buildDate = 0;

  // For each LDAP server, its server state
  private ConcurrentHashMap<Short, ServerState> LDAPStates =
    new ConcurrentHashMap<Short, ServerState>();

  // A Map containing the ServerStates of each RS.
  private ConcurrentHashMap<Short, ServerState> RSStates =
    new ConcurrentHashMap<Short, ServerState>();

  // For each LDAP server, the last(max) CN it published
  private ConcurrentHashMap<Short, ChangeNumber> maxCNs =
    new ConcurrentHashMap<Short, ChangeNumber>();

  // For each LDAP server, an approximation of the date of the first missing
  // change
  private ConcurrentHashMap<Short, Long> fmd =
    new ConcurrentHashMap<Short, Long>();

  private ConcurrentHashMap<Short, Long> missingChanges =
    new ConcurrentHashMap<Short, Long>();

  // For each RS server, an approximation of the date of the first missing
  // change
  private ConcurrentHashMap<Short, Long> fmRSDate =
    new ConcurrentHashMap<Short, Long>();

  private ConcurrentHashMap<Short, Long> missingChangesRS =
    new ConcurrentHashMap<Short, Long>();


  /**
   * Get an approximation of the latency delay of the replication.
   * @param serverId The server ID.
   * @return The delay
   */
  public long getApproxDelay(short serverId)
  {
    Long afmd = fmd.get(serverId);
    if ((afmd != null) && (afmd>0))
      return ((this.getBuildDate() - afmd)/1000);
    else
      return 0;
  }

  /**
   * Get an approximation of the date of the first missing update.
   * @param serverId The server ID.
   * @return The date.
   */
  public long getApproxFirstMissingDate(short serverId)
  {
    Long res;
    if ((res = fmd.get(serverId)) != null)
      return res;
    return 0;
  }

  /**
   * Get the number of missing changes.
   * @param serverId The server ID.
   * @return The number of missing changes.
   */
  public long getMissingChanges(short serverId)
  {
    Long res = missingChanges.get(serverId);
    if (res==null)
      return 0;
    else
      return res;
  }

  /**
   * Get the number of missing changes for a Replication Server.
   *
   * @param serverId   The server ID.
   *
   * @return           The number of missing changes.
   */
  public long getMissingChangesRS(short serverId)
  {
    Long res = missingChangesRS.get(serverId);
    if (res==null)
      return 0;
    else
      return res;
  }

  /**
   * Build the monitor data that are computed from the collected ones.
   */
  public void completeComputing()
  {
    String mds = "";

    // Computes the missing changes counters for LDAP servers
    // For each LSi ,
    //   Regarding each other LSj
    //    Sum the difference : max(LSj) - state(LSi)

    Iterator<Short> lsiStateItr = this.LDAPStates.keySet().iterator();
    while (lsiStateItr.hasNext())
    {
      Short lsiSid = lsiStateItr.next();
      ServerState lsiState = this.LDAPStates.get(lsiSid);
      Long lsiMissingChanges = (long)0;
      if (lsiState != null)
      {
        Iterator<Short> lsjMaxItr = this.maxCNs.keySet().iterator();
        while (lsjMaxItr.hasNext())
        {
          Short lsjSid = lsjMaxItr.next();
          ChangeNumber lsjMaxCN = this.maxCNs.get(lsjSid);
          ChangeNumber lsiLastCN = lsiState.getMaxChangeNumber(lsjSid);

          int missingChangesLsiLsj =
            ChangeNumber.diffSeqNum(lsjMaxCN, lsiLastCN);

          mds +=
            "+ diff("+lsjMaxCN+"-"
                     +lsiLastCN+")="+missingChangesLsiLsj;

          // Regarding a DS that is generating changes. If it is a local DS1,
          // we get its server state, store it, then retrieve server states of
          // remote DSs. When a remote server state is coming, it may contain
          // a change number for DS1 which is newer than the one we locally
          // stored in the server state of DS1. To prevent seeing DS1 has
          // missing changes whereas it is wrong, we replace the value with 0
          // if it is a low value. We cannot overwrite big values as they may be
          // useful for a local server retrieving changes it generated earlier,
          // when it is recovering from an old snapshot and the local RS is
          // sending him the changes it is missing.
          if (lsjSid.equals(lsiSid)) {
            if (missingChangesLsiLsj <= 50)
            {
              missingChangesLsiLsj = 0;
              mds += " (diff replaced by 0 as for server id " + lsiSid + ")";
            }
          }

          lsiMissingChanges += missingChangesLsiLsj;
        }
      }
      mds += "=" + lsiMissingChanges;
      this.missingChanges.put(lsiSid,lsiMissingChanges);
    }

    // Computes the missing changes counters for RS :
    // Sum the difference of sequence numbers for each element in the States.

    for (short lsiSid : RSStates.keySet())
    {
      ServerState lsiState = this.RSStates.get(lsiSid);
      Long lsiMissingChanges = (long)0;
      if (lsiState != null)
      {
        Iterator<Short> lsjMaxItr = this.maxCNs.keySet().iterator();
        while (lsjMaxItr.hasNext())
        {
          Short lsjSid = lsjMaxItr.next();
          ChangeNumber lsjMaxCN = this.maxCNs.get(lsjSid);
          ChangeNumber lsiLastCN = lsiState.getMaxChangeNumber(lsjSid);

          int missingChangesLsiLsj =
            ChangeNumber.diffSeqNum(lsjMaxCN, lsiLastCN);

          mds +=
            "+ diff("+lsjMaxCN+"-"
                     +lsiLastCN+")="+missingChangesLsiLsj;

          lsiMissingChanges += missingChangesLsiLsj;
        }
      }
      mds += "=" + lsiMissingChanges;
      this.missingChangesRS.put(lsiSid,lsiMissingChanges);

      if (debugEnabled())
        TRACER.debugInfo(
          "Complete monitor data : Missing changes ("+ lsiSid +")=" + mds);
    }
    this.setBuildDate(TimeThread.getTime());
    }

  /**
   * Returns a <code>String</code> object representing this
   * object's value.
   * @return  a string representation of the value of this object in
   */
  public String toString()
  {
    String mds = "Monitor data=\n";

    mds+= "Build date=" + this.getBuildDate();
    // RS data
    Iterator<Short> rsite = fmRSDate.keySet().iterator();
    while (rsite.hasNext())
    {
      Short sid = rsite.next();
      mds += "\nfmRSDate(" + sid + ")=\t "+ "afmd=" + fmRSDate.get(sid);
    }

    // maxCNs
    Iterator<Short> itc = maxCNs.keySet().iterator();
    while (itc.hasNext())
    {
      Short sid = itc.next();
      ChangeNumber cn = maxCNs.get(sid);
      mds += "\nmaxCNs(" + sid + ")= " + cn.toStringUI();
    }

    // LDAP data
    Iterator<Short> lsite = LDAPStates.keySet().iterator();
    while (lsite.hasNext())
    {
      Short sid = lsite.next();
      ServerState ss = LDAPStates.get(sid);
      mds += "\nLSData(" + sid + ")=\t" + "state=[" + ss.toString()
      + "] afmd=" + this.getApproxFirstMissingDate(sid);
      if (getBuildDate()>0)
      {
        mds += " missingDelay=" + this.getApproxDelay(sid);
      }
      mds +=" missingCount=" + missingChanges.get(sid);
    }

    // RS data
    rsite = RSStates.keySet().iterator();
    while (rsite.hasNext())
    {
      Short sid = rsite.next();
      ServerState ss = RSStates.get(sid);
      mds += "\nRSData(" + sid + ")=\t" + "state=[" + ss.toString()
      + "] missingCount=" + missingChangesRS.get(sid);
    }

    //
    mds += "\n--";
    return mds;
  }

  /**
   * Sets the build date of the data.
   * @param buildDate The date.
   */
  public void setBuildDate(long buildDate)
  {
    this.buildDate = buildDate;
  }

  /**
   * Returns the build date of the data.
   * @return The date.
   */
  public long getBuildDate()
  {
    return buildDate;
  }

  /**
   * From a provided state, sets the max CN of the monitor data.
   * @param state the provided state.
   */
  public void setMaxCNs(ServerState state)
  {
    Iterator<Short> it = state.iterator();
    while (it.hasNext())
    {
      short sid = it.next();
      ChangeNumber newCN = state.getMaxChangeNumber(sid);
      setMaxCN(sid, newCN);
    }
  }

  /**
   * For the provided serverId, sets the provided CN as the max if
   * it is newer than the current max.
   * @param serverId the provided serverId
   * @param newCN the provided new CN
   */
  public void setMaxCN(short serverId, ChangeNumber newCN)
  {
    if (newCN==null) return;
    ChangeNumber currentMaxCN = maxCNs.get(serverId);
    if (currentMaxCN == null)
    {
      maxCNs.put(serverId, newCN);
    }
    else
    {
      if (newCN.newer(currentMaxCN))
        maxCNs.replace(serverId, newCN);
    }
  }

  /**
   * Get the highest know change number of the LDAP server with the provided
   * serverId.
   * @param serverId The server ID.
   * @return The highest change number.
   */
  public ChangeNumber getMaxCN(short serverId)
  {
    return maxCNs.get(serverId);
  }

  /**
   * Get the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @return The server state.
   */
  public ServerState getLDAPServerState(short serverId)
  {
    return LDAPStates.get(serverId);
  }

  /**
   * Set the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @param state The server state.
   */
  public void setLDAPServerState(short serverId, ServerState state)
  {
    LDAPStates.put(serverId, state);
  }

  /**
   * Set the state of the RS with the provided serverId.
   *
   * @param serverId   The server ID.
   * @param state      The server state.
   */
  public void setRSState(short serverId, ServerState state)
  {
    RSStates.put(serverId, state);
  }

  /**
   * Set the state of the LDAP server with the provided serverId.
   * @param serverId The server ID.
   * @param newFmd The first missing date.
   */
  public void setFirstMissingDate(short serverId, Long newFmd)
  {
    if (newFmd==null) return;
    Long currentfmd = fmd.get(serverId);
    if (currentfmd==null)
    {
      fmd.put(serverId, newFmd);
    }
    else
    {
      if ((newFmd!=0) && (newFmd<currentfmd))
        fmd.replace(serverId, newFmd);
    }
  }

  /**
   * Returns an iterator on the serverId of the Replicas for which
   * we have monitoring data.
   *
   * @return The iterator.
   */
  public Iterator<Short> ldapIterator()
  {
    return LDAPStates.keySet().iterator();
  }

  /**
   * Returns an iterator on the serverId of the Replication Servers for which
   * we have monitoring data.
   *
   * @return The iterator.
   */
  public Iterator<Short> rsIterator()
  {
    return RSStates.keySet().iterator();
  }

  /**
   * Get the state of the RS server with the provided serverId.
   *
   * @param serverId The server ID.
   * @return The server state.
   */
  public ServerState getRSStates(short serverId)
  {
    return RSStates.get(serverId);
  }

  /**
   * Get an approximation of the date of the first missing update.
   *
   * @param serverId The server ID.
   * @return The date.
   */
  public long getRSApproxFirstMissingDate(short serverId)
  {
    Long res;
    if ((res = fmRSDate.get(serverId)) != null)
      return res;
    return 0;
  }
}
