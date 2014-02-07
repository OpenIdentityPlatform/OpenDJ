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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.zip.DataFormatException;

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
  private final ConcurrentMap<Integer, CSN> serverIdToCSN =
      new ConcurrentSkipListMap<Integer, CSN>();
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
   * Creates a new ServerState object from its encoded form.
   *
   * @param in The byte array containing the encoded ServerState form.
   * @param pos The position in the byte array where the encoded ServerState
   *            starts.
   * @param endpos The position in the byte array where the encoded ServerState
   *               ends.
   * @throws DataFormatException If the encoded form was not correct.
   */
  public ServerState(byte[] in, int pos, int endpos) throws DataFormatException
  {
    try
    {
      while (endpos > pos)
      {
        // FIXME JNR: why store the serverId separately from the CSN since the
        // CSN already contains the serverId?

        // read the ServerId
        int length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        int serverId = Integer.valueOf(serverIdString);
        pos += length +1;

        // read the CSN
        length = getNextLength(in, pos);
        String csnString = new String(in, pos, length, "UTF-8");
        CSN csn = new CSN(csnString);
        pos += length +1;

        // Add the serverId
        serverIdToCSN.put(serverId, csn);
      }
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the length of the next String encoded in the in byte array.
   * This method is used to cut the different parts (serverIds, CSN)
   * of a server state.
   *
   * @param in the byte array where to calculate the string.
   * @param pos the position where to start from in the byte array.
   * @return the length of the next string.
   * @throws DataFormatException If the byte array does not end with null.
   */
  private int getNextLength(byte[] in, int pos) throws DataFormatException
  {
    int offset = pos;
    int length = 0;
    while (in[offset++] != 0)
    {
      if (offset >= in.length)
        throw new DataFormatException("byte[] is not a valid server state");
      length++;
    }
    return length;
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
      return false;

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
      return false;

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
      return false;

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
   * return a Set of String usable as a textual representation of
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
    final Set<String> result = new HashSet<String>();
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
    final ArrayList<ByteString> values = new ArrayList<ByteString>(0);
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
   * Add the tail into resultByteArray at position pos.
   */
  private int addByteArray(byte[] tail, byte[] resultByteArray, int pos)
  {
    for (int i=0; i<tail.length; i++,pos++)
    {
      resultByteArray[pos] = tail[i];
    }
    resultByteArray[pos++] = 0;
    return pos;
  }

  /**
   * Encode this ServerState object and return its byte array representation.
   *
   * @return a byte array with an encoded representation of this object.
   * @throws UnsupportedEncodingException if UTF8 is not supported by the JVM.
   */
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    // copy to protect from concurrent updates
    // that could change the number of elements in the Map
    final Map<Integer, CSN> copy = new HashMap<Integer, CSN>(serverIdToCSN);

    final int size = copy.size();
    List<String> idList = new ArrayList<String>(size);
    List<String> csnList = new ArrayList<String>(size);
    // calculate the total length needed to allocate byte array
    int length = 0;
    for (Entry<Integer, CSN> entry : copy.entrySet())
    {
      // serverId is useless, see comment in ServerState ctor
      final String serverIdStr = String.valueOf(entry.getKey());
      idList.add(serverIdStr);
      length += serverIdStr.length() + 1;

      final String csnStr = entry.getValue().toString();
      csnList.add(csnStr);
      length += csnStr.length() + 1;
    }
    byte[] result = new byte[length];

    // write the server state into the byte array
    int pos = 0;
    for (int i = 0; i < size; i++)
    {
      String str = idList.get(i);
      pos = addByteArray(str.getBytes("UTF-8"), result, pos);
      str = csnList.get(i);
      pos = addByteArray(str.getBytes("UTF-8"), result, pos);
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * Build a copy of the ServerState with only CSNs older than a provided
   * timestamp. This is used when building the initial Cookie in the External
   * Changelog, to cope with purged changes.
   *
   * @param timestamp
   *          The timestamp to compare the ServerState against
   * @return a copy of the ServerState which only contains the CSNs older than
   *         csn.
   */
  public ServerState duplicateOnlyOlderThan(long timestamp)
  {
    final CSN csn = new CSN(timestamp, 0, 0);
    final ServerState newState = new ServerState();
    for (CSN change : serverIdToCSN.values())
    {
      if (change.isOlderThan(csn))
      {
        newState.serverIdToCSN.put(change.getServerId(), change);
      }
    }
    return newState;
  }

}
