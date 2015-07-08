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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.common;

import static org.opends.messages.ReplicationMessages.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Pair;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

/**
 * This object is used to store a list of ServerState object, one by replication
 * domain. Globally, it is the generalization of ServerState (that applies to
 * one domain) to a list of domains.
 * <p>
 * MultiDomainServerState is also known as "cookie" and is used with the
 * cookie-based changelog.
 */
public class MultiDomainServerState implements Iterable<DN>
{
  /**
   * The list of (domain service id, ServerState).
   */
  private final ConcurrentMap<DN, ServerState> list;

  /**
   * Creates a new empty object.
   */
  public MultiDomainServerState()
  {
    list = new ConcurrentSkipListMap<>();
  }

  /**
   * Create an object from a string representation.
   * @param mdss The provided string representation of the state.
   * @throws DirectoryException when the string has an invalid format
   */
  public MultiDomainServerState(String mdss) throws DirectoryException
  {
    list = new ConcurrentSkipListMap<>(splitGenStateToServerStates(mdss));
  }

  /**
   * Empty the object..
   * After this call the object will be in the same state as if it
   * was just created.
   */
  public void clear()
  {
    list.clear();
  }

  /**
   * Update the ServerState of the provided baseDN with the replication
   * {@link CSN} provided.
   *
   * @param baseDN       The provided baseDN.
   * @param csn          The provided CSN.
   *
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean update(DN baseDN, CSN csn)
  {
    if (csn == null)
    {
      return false;
    }

    ServerState serverState = list.get(baseDN);
    if (serverState == null)
    {
      serverState = new ServerState();
      final ServerState existingSS = list.putIfAbsent(baseDN, serverState);
      if (existingSS != null)
      {
        serverState = existingSS;
      }
    }
    return serverState.update(csn);
  }

  /**
   * Update the ServerState of the provided baseDN with the provided server
   * state.
   *
   * @param baseDN
   *          The provided baseDN.
   * @param serverState
   *          The provided serverState.
   */
  public void update(DN baseDN, ServerState serverState)
  {
    for (CSN csn : serverState)
    {
      update(baseDN, csn);
    }
  }

  /**
   * Replace the ServerState of the provided baseDN with the provided server
   * state. The provided server state will be owned by this instance, so care
   * must be taken by calling code to duplicate it if needed.
   *
   * @param baseDN
   *          The provided baseDN.
   * @param serverState
   *          The provided serverState.
   */
  public void replace(DN baseDN, ServerState serverState)
  {
    if (serverState == null)
    {
      throw new IllegalArgumentException("ServerState must not be null");
    }
    list.put(baseDN, serverState);
  }

  /**
   * Update the current object with the provided multi domain server state.
   *
   * @param state
   *          The provided multi domain server state.
   */
  public void update(MultiDomainServerState state)
  {
    for (Entry<DN, ServerState> entry : state.list.entrySet())
    {
      update(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Returns a snapshot of this object.
   *
   * @return an unmodifiable Map representing a snapshot of this object.
   */
  public Map<DN, List<CSN>> getSnapshot()
  {
    if (list.isEmpty())
    {
      return Collections.emptyMap();
    }
    final Map<DN, List<CSN>> map = new HashMap<>();
    for (Entry<DN, ServerState> entry : list.entrySet())
    {
      final List<CSN> l = entry.getValue().getSnapshot();
      if (!l.isEmpty())
      {
        map.put(entry.getKey(), l);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Returns a string representation of this object.
   *
   * @return The string representation.
   */
  @Override
  public String toString()
  {
    final StringBuilder res = new StringBuilder();
    if (list != null && !list.isEmpty())
    {
      for (Entry<DN, ServerState> entry : list.entrySet())
      {
        res.append(entry.getKey()).append(":")
           .append(entry.getValue()).append(";");
      }
    }
    return res.toString();
  }

  /**
   * Dump a string representation in the provided buffer.
   * @param buffer The provided buffer.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(this);
  }

  /**
   * Tests if the state is empty.
   *
   * @return True if the state is empty.
   */
  public boolean isEmpty()
  {
    return list.isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<DN> iterator()
  {
    return list.keySet().iterator();
  }

  /**
   * Returns the ServerState associated to the provided replication domain's
   * baseDN.
   *
   * @param baseDN
   *          the replication domain's baseDN
   * @return the associated ServerState
   */
  public ServerState getServerState(DN baseDN)
  {
    return list.get(baseDN);
  }

  /**
   * Returns the CSN associated to the provided replication domain's baseDN and
   * serverId.
   *
   * @param baseDN
   *          the replication domain's baseDN
   * @param serverId
   *          the serverId
   * @return the associated CSN
   */
  public CSN getCSN(DN baseDN, int serverId)
  {
    final ServerState ss = list.get(baseDN);
    if (ss != null)
    {
      return ss.getCSN(serverId);
    }
    return null;
  }

  /**
   * Returns the oldest Pair&lt;DN, CSN&gt; held in current object, excluding
   * the provided CSNs. Said otherwise, the value returned is the oldest
   * Pair&lt;DN, CSN&gt; included in the current object, that is not part of the
   * excludedCSNs.
   *
   * @param excludedCSNs
   *          the CSNs that cannot be returned
   * @return the oldest Pair&lt;DN, CSN&gt; included in the current object that
   *         is not part of the excludedCSNs, or {@link Pair#EMPTY} if no such
   *         older CSN exists.
   */
  public Pair<DN, CSN> getOldestCSNExcluding(MultiDomainServerState excludedCSNs)
  {
    Pair<DN, CSN> oldest = Pair.empty();
    for (Entry<DN, ServerState> entry : list.entrySet())
    {
      final DN baseDN = entry.getKey();
      final ServerState value = entry.getValue();
      for (Entry<Integer, CSN> entry2 : value.getServerIdToCSNMap().entrySet())
      {
        final CSN csn = entry2.getValue();
        if (!isReplicaExcluded(excludedCSNs, baseDN, csn)
            && (oldest == Pair.EMPTY || csn.isOlderThan(oldest.getSecond())))
        {
          oldest = Pair.of(baseDN, csn);
        }
      }
    }
    return oldest;
  }

  private boolean isReplicaExcluded(MultiDomainServerState excluded, DN baseDN,
      CSN csn)
  {
    return excluded != null
        && csn.equals(excluded.getCSN(baseDN, csn.getServerId()));
  }

  /**
   * Removes the mapping to the provided CSN if it is present in this
   * MultiDomainServerState.
   *
   * @param baseDN
   *          the replication domain's baseDN
   * @param expectedCSN
   *          the CSN to be removed
   * @return true if the CSN could be removed, false otherwise.
   */
  public boolean removeCSN(DN baseDN, CSN expectedCSN)
  {
    final ServerState ss = list.get(baseDN);
    return ss != null && ss.removeCSN(expectedCSN);
  }

  /**
   * Test if this object equals the provided other object.
   * @param other The other object with which we want to test equality.
   * @return      Returns True if this equals other, else return false.
   */
  public boolean equalsTo(MultiDomainServerState other)
  {
    return cover(other) && other.cover(this);
  }

  /**
   * Test if this object covers the provided covered object.
   * @param  covered The provided object.
   * @return true when this covers the provided object.
   */
  public boolean cover(MultiDomainServerState covered)
  {
    for (DN baseDN : covered.list.keySet())
    {
      ServerState state = list.get(baseDN);
      ServerState coveredState = covered.list.get(baseDN);
      if (state == null || coveredState == null || !state.cover(coveredState))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Test if this object covers the provided CSN for the provided baseDN.
   *
   * @param baseDN
   *          The provided baseDN.
   * @param csn
   *          The provided CSN.
   * @return true when this object covers the provided CSN for the provided
   *         baseDN.
   */
  public boolean cover(DN baseDN, CSN csn)
  {
    final ServerState state = list.get(baseDN);
    return state != null && state.cover(csn);
  }

  /**
   * Splits the provided generalizedServerState being a String with the
   * following syntax: "domain1:state1;domain2:state2;..." to a Map of (domain
   * DN, domain ServerState).
   *
   * @param multiDomainServerState
   *          the provided multi domain server state also known as cookie
   * @exception DirectoryException
   *              when an error occurs
   * @return the split state.
   */
  private static Map<DN, ServerState> splitGenStateToServerStates(
      String multiDomainServerState) throws DirectoryException
  {
    Map<DN, ServerState> startStates = new TreeMap<>();
    if (multiDomainServerState != null && multiDomainServerState.length() > 0)
    {
      try
      {
        // Split the provided multiDomainServerState into domains
        String[] domains = multiDomainServerState.split(";");
        for (String domain : domains)
        {
          // For each domain, split the CSNs by server
          // and build a server state (SHOULD BE OPTIMIZED)
          final ServerState serverStateByDomain = new ServerState();

          final String[] fields = domain.split(":");
          if (fields.length == 0)
          {
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                ERR_INVALID_COOKIE_SYNTAX.get(multiDomainServerState));
          }
          final String domainBaseDN = fields[0];
          if (fields.length > 1)
          {
            final String serverStateStr = fields[1];
            for (String csnStr : serverStateStr.split(" "))
            {
              final CSN csn = new CSN(csnStr);
              serverStateByDomain.update(csn);
            }
          }
          startStates.put(DN.valueOf(domainBaseDN), serverStateByDomain);
        }
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        throw new DirectoryException(
            ResultCode.PROTOCOL_ERROR,
            LocalizableMessage.raw("Exception raised: " + e),
            e);
      }
    }
    return startStates;
  }
}
