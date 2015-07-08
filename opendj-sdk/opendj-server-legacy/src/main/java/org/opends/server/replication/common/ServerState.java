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
package org.opends.server.replication.common;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.replication.protocol.ProtocolVersion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.Utils;

/**
 * This class is used to associate serverIds with {@link CSN}s.
 * <p>
 * For example, it is exchanged with the replication servers at connection
 * establishment time to communicate "which CSNs was last seen by a serverId".
 */
public class ServerState implements Iterable<CSN>
{

  /** Associates a serverId with a CSN. */
  private final ConcurrentMap<Integer, CSN> serverIdToCSN = new ConcurrentSkipListMap<>();
  /**
   * Whether the state has been saved to persistent storage. It starts at true,
   * and moves to false when an update is made to the current object.
   */
  private volatile boolean saved = true;

  /**
   * Creates a new empty ServerState.
   */
  public ServerState()
  {
    super();
  }

  /**
   * Empty the ServerState.
   * After this call the Server State will be in the same state
   * as if it was just created.
   */
  public void clear()
  {
    serverIdToCSN.clear();
  }

  /**
   * Forward update the Server State with a CSN. The provided CSN will be put on
   * the current object only if it is newer than the existing CSN for the same
   * serverId or if there is no existing CSN.
   *
   * @param csn
   *          The committed CSN.
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean update(CSN csn)
  {
    if (csn == null)
    {
      return false;
    }

    saved = false;

    final int serverId = csn.getServerId();
    while (true)
    {
      final CSN existingCSN = serverIdToCSN.get(serverId);
      if (existingCSN == null)
      {
        if (serverIdToCSN.putIfAbsent(serverId, csn) == null)
        {
          return true;
        }
        // oops, a concurrent modification happened, run the same process again
        continue;
      }
      else if (csn.isNewerThan(existingCSN))
      {
        if (serverIdToCSN.replace(serverId, existingCSN, csn))
        {
          return true;
        }
        // oops, a concurrent modification happened, run the same process again
        continue;
      }
      return false;
    }
  }

  /**
   * Update the Server State with a Server State. Every CSN of this object is
   * updated with the CSN of the passed server state if it is newer.
   *
   * @param serverState the server state to use for the update.
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean update(ServerState serverState)
  {
    if (serverState == null)
    {
      return false;
    }

    boolean updated = false;
    for (CSN csn : serverState.serverIdToCSN.values())
    {
      if (update(csn))
      {
        updated = true;
      }
    }
    return updated;
  }

  /**
   * Removes the mapping to the provided CSN if it is present in this
   * ServerState.
   *
   * @param expectedCSN
   *          the CSN to be removed
   * @return true if the CSN could be removed, false otherwise.
   */
  public boolean removeCSN(CSN expectedCSN)
  {
    if (expectedCSN == null)
    {
      return false;
    }

    if (serverIdToCSN.remove(expectedCSN.getServerId(), expectedCSN))
    {
      saved = false;
      return true;
    }
    return false;
  }

  /**
   * Replace the Server State with another ServerState.
   *
   * @param serverState The ServerState.
   *
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean reload(ServerState serverState) {
    if (serverState == null) {
      return false;
    }

    clear();
    return update(serverState);
  }

  /**
   * Return a Set of String usable as a textual representation of
   * a Server state.
   * format : time seqnum id
   *
   * example :
   *  1 00000109e4666da600220001
   *  2 00000109e44567a600220002
   *
   * @return the representation of the Server state
   */
  public Set<String> toStringSet()
  {
    final Set<String> result = new HashSet<>();
    for (CSN change : serverIdToCSN.values())
    {
      Date date = new Date(change.getTime());
      result.add(change + " " + date + " " + change.getTime());
    }
    return result;
  }

  /**
   * Return an ArrayList of ANS1OctetString encoding the CSNs
   * contained in the ServerState.
   * @return an ArrayList of ANS1OctetString encoding the CSNs
   * contained in the ServerState.
   */
  public ArrayList<ByteString> toASN1ArrayList()
  {
    final ArrayList<ByteString> values = new ArrayList<>(0);
    for (CSN csn : serverIdToCSN.values())
    {
      values.add(ByteString.valueOf(csn.toString()));
    }
    return values;
  }



  /**
   * Encodes this server state to the provided ASN1 writer.
   *
   * @param writer
   *          The ASN1 writer.
   * @param protocolVersion
   *          The replication protocol version.
   * @throws IOException
   *           If an error occurred during encoding.
   */
  public void writeTo(ASN1Writer writer, short protocolVersion)
      throws IOException
  {
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V7)
    {
      for (CSN csn : serverIdToCSN.values())
      {
        writer.writeOctetString(csn.toByteString());
      }
    }
    else
    {
      for (CSN csn : serverIdToCSN.values())
      {
        writer.writeOctetString(csn.toString());
      }
    }
  }

  /**
   * Returns a snapshot of this object.
   *
   * @return an unmodifiable List representing a snapshot of this object.
   */
  public List<CSN> getSnapshot()
  {
    if (serverIdToCSN.isEmpty())
    {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<CSN>(serverIdToCSN.values()));
  }

  /**
   * Return the text representation of ServerState.
   * @return the text representation of ServerState
   */
  @Override
  public String toString()
  {
    return Utils.joinAsString(" ", serverIdToCSN.values());
  }

  /**
   * Returns the {@code CSN} contained in this server state which corresponds to
   * the provided server ID.
   *
   * @param serverId
   *          The server ID.
   * @return The {@code CSN} contained in this server state which
   *         corresponds to the provided server ID.
   */
  public CSN getCSN(int serverId)
  {
    return serverIdToCSN.get(serverId);
  }

  /**
   * Returns a copy of this ServerState's content as a Map of serverId => CSN.
   *
   * @return a copy of this ServerState's content as a Map of serverId => CSN.
   */
  public Map<Integer, CSN> getServerIdToCSNMap()
  {
    // copy to protect from concurrent updates
    // that could change the number of elements in the Map
    return new HashMap<>(serverIdToCSN);
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<CSN> iterator()
  {
    return serverIdToCSN.values().iterator();
  }

  /**
   * Check that all the CSNs in the covered serverState are also in this
   * serverState.
   *
   * @param covered The ServerState that needs to be checked.
   * @return A boolean indicating if this ServerState covers the ServerState
   *         given in parameter.
   */
  public boolean cover(ServerState covered)
  {
    for (CSN coveredChange : covered.serverIdToCSN.values())
    {
      if (!cover(coveredChange))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks that the CSN given as a parameter is in this ServerState.
   *
   * @param   covered The CSN that should be checked.
   * @return  A boolean indicating if this ServerState contains the CSN given in
   *          parameter.
   */
  public boolean cover(CSN covered)
  {
    final CSN csn = this.serverIdToCSN.get(covered.getServerId());
    return csn != null && !csn.isOlderThan(covered);
  }

  /**
   * Tests if the state is empty.
   *
   * @return True if the state is empty.
   */
  public boolean isEmpty()
  {
    return serverIdToCSN.isEmpty();
  }

  /**
   * Make a duplicate of this state.
   * @return The duplicate of this state.
   */
  public ServerState duplicate()
  {
    final ServerState newState = new ServerState();
    newState.serverIdToCSN.putAll(serverIdToCSN);
    return newState;
  }

  /**
   * Computes the number of changes a first server state has in advance
   * compared to a second server state.
   * @param ss1 The server state supposed to be newer than the second one
   * @param ss2 The server state supposed to be older than the first one
   * @return The difference of changes (sum of the differences for each server
   * id changes). 0 If no gap between 2 states.
   * @throws IllegalArgumentException If one of the passed state is null
   */
  public static int diffChanges(ServerState ss1, ServerState ss2)
      throws IllegalArgumentException
  {
    if (ss1 == null || ss2 == null)
    {
      throw new IllegalArgumentException("Null server state(s)");
    }

    int diff = 0;
    for (Integer serverId : ss1.serverIdToCSN.keySet())
    {
      CSN csn1 = ss1.serverIdToCSN.get(serverId);
      if (csn1 != null)
      {
        CSN csn2 = ss2.serverIdToCSN.get(serverId);
        if (csn2 != null)
        {
          diff += CSN.diffSeqNum(csn1, csn2);
        }
        else
        {
          // ss2 does not have a change for this server id but ss1, so the
          // server holding ss1 has every changes represented in csn1 in advance
          // compared to server holding ss2, add this amount
          diff += csn1.getSeqnum();
        }
      }
    }

    return diff;
  }

  /**
   * Set the saved status of this ServerState.
   *
   * @param b A boolean indicating if the State has been safely stored.
   */
  public void setSaved(boolean b)
  {
    saved = b;
  }

  /**
   * Get the saved status of this ServerState.
   *
   * @return The saved status of this ServerState.
   */
  public boolean isSaved()
  {
    return saved;
  }

}
