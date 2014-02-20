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
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerStatus;

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
  public TopologyMsg(byte[] in, short version) throws DataFormatException
  {
    try
    {
      /* First byte is the type */
      if (in.length < 1 || in[0] != MSG_TYPE_TOPOLOGY)
      {
        throw new DataFormatException(
          "Input is not a valid " + getClass().getCanonicalName());
      }

      int pos = 1;

      /* Read number of following DS info entries */
      byte nDsInfo = in[pos++];

      /* Read the DS info entries */
      Map<Integer, DSInfo> replicaInfos =
          new HashMap<Integer, DSInfo>(Math.max(0, nDsInfo));
      while (nDsInfo > 0 && pos < in.length)
      {
        /* Read DS id */
        int length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        int dsId = Integer.valueOf(serverIdString);
        pos += length + 1;

        /* Read DS URL */
        String dsUrl;
        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V6)
        {
          length = getNextLength(in, pos);
          dsUrl = new String(in, pos, length, "UTF-8");
          pos += length + 1;
        }
        else
        {
          dsUrl = "";
        }

        /* Read RS id */
        length = getNextLength(in, pos);
        serverIdString = new String(in, pos, length, "UTF-8");
        int rsId = Integer.valueOf(serverIdString);
        pos += length + 1;

        /* Read the generation id */
        length = getNextLength(in, pos);
        long generationId = Long.valueOf(new String(in, pos, length, "UTF-8"));
        pos += length + 1;

        /* Read DS status */
        ServerStatus status = ServerStatus.valueOf(in[pos++]);

        /* Read DS assured flag */
        boolean assuredFlag = in[pos++] == 1;

        /* Read DS assured mode */
        AssuredMode assuredMode = AssuredMode.valueOf(in[pos++]);

        /* Read DS safe data level */
        byte safeDataLevel = in[pos++];

        /* Read DS group id */
        byte groupId = in[pos++];

        /* Read number of referrals URLs */
        List<String> refUrls = new ArrayList<String>();
        pos = readStrings(in, pos, refUrls);

        Set<String> attrs = new HashSet<String>();
        Set<String> delattrs = new HashSet<String>();
        short protocolVersion = -1;
        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          pos = readStrings(in, pos, attrs);

          if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
          {
            pos = readStrings(in, pos, delattrs);
          }
          else
          {
            // Default to using the same set of attributes for deletes.
            delattrs.addAll(attrs);
          }

          /* Read Protocol version */
          protocolVersion = in[pos++];
        }

        /* Now create DSInfo and store it */
        replicaInfos.put(dsId, new DSInfo(dsId, dsUrl, rsId, generationId,
            status, assuredFlag, assuredMode, safeDataLevel, groupId, refUrls,
            attrs, delattrs, protocolVersion));

        nDsInfo--;
      }

      /* Read number of following RS info entries */
      byte nRsInfo = in[pos++];

      /* Read the RS info entries */
      List<RSInfo> rsInfos = new ArrayList<RSInfo>(Math.max(0, nRsInfo));
      while (nRsInfo > 0 && pos < in.length)
      {
        /* Read RS id */
        int length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        int id = Integer.valueOf(serverIdString);
        pos += length + 1;

        /* Read the generation id */
        length = getNextLength(in, pos);
        long generationId = Long.valueOf(new String(in, pos, length, "UTF-8"));
        pos += length + 1;

        /* Read RS group id */
        byte groupId = in[pos++];

        int weight = 1;
        String serverUrl = null;
        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          length = getNextLength(in, pos);
          serverUrl = new String(in, pos, length, "UTF-8");
          pos += length + 1;

          /* Read RS weight */
          length = getNextLength(in, pos);
          weight = Integer.valueOf(new String(in, pos, length, "UTF-8"));
          pos += length + 1;
        }

        /* Now create RSInfo and store it */
        rsInfos.add(new RSInfo(id, serverUrl, generationId, groupId, weight));

        nRsInfo--;
      }

      this.replicaInfos = Collections.unmodifiableMap(replicaInfos);
      this.rsInfos = Collections.unmodifiableList(rsInfos);
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  private int readStrings(byte[] in, int pos, Collection<String> outputCol)
      throws DataFormatException, UnsupportedEncodingException
  {
    byte nAttrs = in[pos++];
    byte nRead = 0;
    // Read all elements until expected number read
    while (nRead != nAttrs && pos < in.length)
    {
      int length = getNextLength(in, pos);
      outputCol.add(new String(in, pos, length, "UTF-8"));
      pos += length + 1;
      nRead++;
    }
    return pos;
  }

  /**
   * Creates a new  message of the currently connected servers.
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
      Map<Integer, DSInfo> replicas = new HashMap<Integer, DSInfo>();
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
  public byte[] getBytes(short version) throws UnsupportedEncodingException
  {
    try
    {
      /**
       * LocalizableMessage has the following form:
       * <pdu type><number of following DSInfo entries>[<DSInfo>]*
       * <number of following RSInfo entries>[<RSInfo>]*
       */
      ByteArrayOutputStream oStream = new ByteArrayOutputStream();

      /* Put the message type */
      oStream.write(MSG_TYPE_TOPOLOGY);

      // Put number of following DS info entries
      oStream.write((byte) replicaInfos.size());

      // Put DS info
      for (DSInfo dsInfo : replicaInfos.values())
      {
        // Put DS id
        byte[] byteServerId =
          String.valueOf(dsInfo.getDsId()).getBytes("UTF-8");
        oStream.write(byteServerId);
        oStream.write(0);
        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V6)
        {
          // Put DS URL
          oStream.write(dsInfo.getDsUrl().getBytes("UTF-8"));
          oStream.write(0);
        }
        // Put RS id
        byteServerId = String.valueOf(dsInfo.getRsId()).getBytes("UTF-8");
        oStream.write(byteServerId);
        oStream.write(0);
        // Put the generation id
        oStream.write(String.valueOf(dsInfo.getGenerationId()).
            getBytes("UTF-8"));
        oStream.write(0);
        // Put DS status
        oStream.write(dsInfo.getStatus().getValue());
        // Put DS assured flag
        oStream.write(dsInfo.isAssured() ? (byte) 1 : (byte) 0);
        // Put DS assured mode
        oStream.write(dsInfo.getAssuredMode().getValue());
        // Put DS safe data level
        oStream.write(dsInfo.getSafeDataLevel());
        // Put DS group id
        oStream.write(dsInfo.getGroupId());

        writeStrings(oStream, dsInfo.getRefUrls());

        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          // Put ECL includes
          writeStrings(oStream, dsInfo.getEclIncludes());

          if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
          {
            writeStrings(oStream, dsInfo.getEclIncludesForDeletes());
          }

          oStream.write(dsInfo.getProtocolVersion());
        }
      }

      // Put number of following RS info entries
      oStream.write((byte) rsInfos.size());

      // Put RS info
      for (RSInfo rsInfo : rsInfos)
      {
        // Put RS id
        byte[] byteServerId =
          String.valueOf(rsInfo.getId()).getBytes("UTF-8");
        oStream.write(byteServerId);
        oStream.write(0);
        // Put the generation id
        oStream.write(String.valueOf(rsInfo.getGenerationId()).
          getBytes("UTF-8"));
        oStream.write(0);
        // Put RS group id
        oStream.write(rsInfo.getGroupId());

        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          // Put server URL
          oStream.write(rsInfo.getServerUrl().getBytes("UTF-8"));
          oStream.write(0);

          // Put RS weight
          oStream.write(String.valueOf(rsInfo.getWeight()).getBytes("UTF-8"));
          oStream.write(0);
        }
      }

      return oStream.toByteArray();
    }
    catch (IOException e)
    {
      // never happens
      throw new RuntimeException(e);
    }
  }

  private void writeStrings(ByteArrayOutputStream oStream,
      Collection<String> col) throws IOException, UnsupportedEncodingException
  {
    // Put collection length as a byte
    oStream.write(col.size());
    for (String elem : col)
    {
      // Write the element and a 0 terminating byte
      oStream.write(elem.getBytes("UTF-8"));
      oStream.write(0);
    }
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
      + (rsStr.equals("") ? "----------------------------\n" : "");
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
