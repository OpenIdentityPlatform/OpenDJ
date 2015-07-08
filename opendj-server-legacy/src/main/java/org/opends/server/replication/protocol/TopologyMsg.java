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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.*;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;

import static org.opends.server.replication.protocol.ProtocolVersion.*;

/**
 * This class defines a message that is sent:
 * - By a RS to the other RSs in the topology, containing:
 *   - the DSs directly connected to the RS in the DS infos
 *   - only this RS in the RS infos
 * - By a RS to his connected DSs, containing every DSs and RSs he knows.
 * In that case the message contains:
 *   - every DSs the RS knows except the destinator DS in the DS infos
 *   - every connected RSs (including the sending RS) in the RS infos
 *
 * Exchanging these messages allows to have each RS or DS take
 * appropriate decisions according to the current topology:
 * - a RS can route a message to a DS
 * - a DS can decide towards which peer DS send referrals
 * ...
 */
public class TopologyMsg extends ReplicationMsg
{
  /** Information for the DSs (aka replicas) known in the topology. */
  private final Map<Integer, DSInfo> replicaInfos;
  /** Information for the RSs known in the topology. */
  private final List<RSInfo> rsInfos;

  /**
   * Creates a new changelogInfo message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @param version The protocol version to use to decode the msg.
   * @throws java.util.zip.DataFormatException If the byte array does not
   * contain a valid encoded form of the message.
   */
  TopologyMsg(byte[] in, short version) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_TOPOLOGY)
    {
      throw new DataFormatException("Input is not a valid "
          + getClass().getCanonicalName());
    }

    // Read the DS info entries, first read number of them
    int nDsInfo = scanner.nextByte();
    final Map<Integer, DSInfo> replicaInfos = new HashMap<>(Math.max(0, nDsInfo));
    while (nDsInfo > 0 && !scanner.isEmpty())
    {
      final DSInfo dsInfo = nextDSInfo(scanner, version);
      replicaInfos.put(dsInfo.getDsId(), dsInfo);
      nDsInfo--;
    }

    // Read the RS info entries
    int nRsInfo = scanner.nextByte();
    final List<RSInfo> rsInfos = new ArrayList<>(Math.max(0, nRsInfo));
    while (nRsInfo > 0 && !scanner.isEmpty())
    {
      rsInfos.add(nextRSInfo(scanner, version));
      nRsInfo--;
    }

    this.replicaInfos = Collections.unmodifiableMap(replicaInfos);
    this.rsInfos = Collections.unmodifiableList(rsInfos);
  }

  private DSInfo nextDSInfo(ByteArrayScanner scanner, short version)
      throws DataFormatException
  {
    final int dsId = scanner.nextIntUTF8();
    final String dsUrl =
        version < REPLICATION_PROTOCOL_V6 ? "" : scanner.nextString();
    final int rsId = scanner.nextIntUTF8();
    final long generationId = scanner.nextLongUTF8();
    final ServerStatus status = ServerStatus.valueOf(scanner.nextByte());
    final boolean assuredFlag = scanner.nextBoolean();
    final AssuredMode assuredMode = AssuredMode.valueOf(scanner.nextByte());
    final byte safeDataLevel = scanner.nextByte();
    final byte groupId = scanner.nextByte();

    final List<String> refUrls = new ArrayList<>();
    scanner.nextStrings(refUrls);

    final Set<String> attrs = new HashSet<>();
    final Set<String> delattrs = new HashSet<>();
    short protocolVersion = -1;
    if (version >= REPLICATION_PROTOCOL_V4)
    {
      scanner.nextStrings(attrs);

      if (version >= REPLICATION_PROTOCOL_V5)
      {
        scanner.nextStrings(delattrs);
      }
      else
      {
        // Default to using the same set of attributes for deletes.
        delattrs.addAll(attrs);
      }

      protocolVersion = scanner.nextByte();
    }

    return new DSInfo(dsId, dsUrl, rsId, generationId, status, assuredFlag,
        assuredMode, safeDataLevel, groupId, refUrls, attrs, delattrs,
        protocolVersion);
  }

  private RSInfo nextRSInfo(ByteArrayScanner scanner, short version)
      throws DataFormatException
  {
    final int rsId = scanner.nextIntUTF8();
    final long generationId = scanner.nextLongUTF8();
    final byte groupId = scanner.nextByte();

    int weight = 1;
    String serverUrl = null;
    if (version >= REPLICATION_PROTOCOL_V4)
    {
      serverUrl = scanner.nextString();
      weight = scanner.nextIntUTF8();
    }

    return new RSInfo(rsId, serverUrl, generationId, groupId, weight);
  }

  /**
   * Creates a new message of the currently connected servers.
   *
   * @param dsInfos The collection of currently connected DS servers ID.
   * @param rsInfos The list of currently connected RS servers ID.
   */
  public TopologyMsg(Collection<DSInfo> dsInfos, List<RSInfo> rsInfos)
  {
    if (dsInfos == null || dsInfos.isEmpty())
    {
      this.replicaInfos = Collections.emptyMap();
    }
    else
    {
      Map<Integer, DSInfo> replicas = new HashMap<>();
      for (DSInfo dsInfo : dsInfos)
      {
        replicas.put(dsInfo.getDsId(), dsInfo);
      }
      this.replicaInfos = Collections.unmodifiableMap(replicas);
    }

    if (rsInfos == null || rsInfos.isEmpty())
    {
      this.rsInfos = Collections.emptyList();
    }
    else
    {
      this.rsInfos =
          Collections.unmodifiableList(new ArrayList<RSInfo>(rsInfos));
    }
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short version)
  {
    /**
     * Message has the following form:
     * <pdu type><number of following DSInfo entries>[<DSInfo>]*
     * <number of following RSInfo entries>[<RSInfo>]*
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_TOPOLOGY);

    // Put DS infos
    builder.appendByte((byte) replicaInfos.size());
    for (DSInfo dsInfo : replicaInfos.values())
    {
      builder.appendIntUTF8(dsInfo.getDsId());
      if (version >= REPLICATION_PROTOCOL_V6)
      {
        builder.appendString(dsInfo.getDsUrl());
      }
      builder.appendIntUTF8(dsInfo.getRsId());
      builder.appendLongUTF8(dsInfo.getGenerationId());
      builder.appendByte(dsInfo.getStatus().getValue());
      builder.appendBoolean(dsInfo.isAssured());
      builder.appendByte(dsInfo.getAssuredMode().getValue());
      builder.appendByte(dsInfo.getSafeDataLevel());
      builder.appendByte(dsInfo.getGroupId());

      builder.appendStrings(dsInfo.getRefUrls());

      if (version >= REPLICATION_PROTOCOL_V4)
      {
        builder.appendStrings(dsInfo.getEclIncludes());
        if (version >= REPLICATION_PROTOCOL_V5)
        {
          builder.appendStrings(dsInfo.getEclIncludesForDeletes());
        }
        builder.appendByte((byte) dsInfo.getProtocolVersion());
      }
    }

    // Put RS infos
    builder.appendByte((byte) rsInfos.size());
    for (RSInfo rsInfo : rsInfos)
    {
      builder.appendIntUTF8(rsInfo.getId());
      builder.appendLongUTF8(rsInfo.getGenerationId());
      builder.appendByte(rsInfo.getGroupId());

      if (version >= REPLICATION_PROTOCOL_V4)
      {
        builder.appendString(rsInfo.getServerUrl());
        builder.appendIntUTF8(rsInfo.getWeight());
      }
    }

    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    String dsStr = "";
    for (DSInfo dsInfo : replicaInfos.values())
    {
      dsStr += dsInfo + "\n----------------------------\n";
    }

    String rsStr = "";
    for (RSInfo rsInfo : rsInfos)
    {
      rsStr += rsInfo + "\n----------------------------\n";
    }

    return "TopologyMsg content:"
      + "\n----------------------------"
      + "\nCONNECTED DS SERVERS:"
      + "\n--------------------\n"
      + dsStr
      + "CONNECTED RS SERVERS:"
      + "\n--------------------\n"
      + rsStr
      + ("".equals(rsStr) ? "----------------------------\n" : "");
  }

  /**
   * Get the DS infos.
   *
   * @return The DS infos
   */
  public Map<Integer, DSInfo> getReplicaInfos()
  {
    return replicaInfos;
  }

  /**
   * Get the RS infos.
   *
   * @return The RS infos
   */
  public List<RSInfo> getRsInfos()
  {
    return rsInfos;
  }
}
