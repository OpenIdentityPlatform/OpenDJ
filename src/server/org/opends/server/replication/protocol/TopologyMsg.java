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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
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
 *
 * This class defines a message that is sent:
 * - By a RS to the other RSs in the topology, containing:
 *   - the list of DSs directly connected to the RS in the DS list
 *   - only this RS in the RS list
 * - By a RS to his connected DSs, containing every DSs and RSs he knows.
 * In that case the message contains:
 *   - the list of every DS the RS knows except the destinator DS in the DS list
 *   - the list of every connected RSs (including the sending RS) in the RS list
 *
 * Exchanging these messages allows to have each RS or DS take
 * appropriate decisions according to the current topology:
 * - a RS can route a message to a DS
 * - a DS can decide towards which peer DS send referrals
 * ...
 */
public class TopologyMsg extends ReplicationMsg
{
  // Information for the DS known in the topology
  private final List<DSInfo> dsList;
  // Information for the RS known in the topology
  private final List<RSInfo> rsList;

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
          "Input is not a valid " + this.getClass().getCanonicalName());
      }

      int pos = 1;

      /* Read number of following DS info entries */

      byte nDsInfo = in[pos++];

      /* Read the DS info entries */
      List<DSInfo> dsList = new ArrayList<DSInfo>(Math.max(0, nDsInfo));
      while ( (nDsInfo > 0) && (pos < in.length) )
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
        length =
          getNextLength(in, pos);
        serverIdString =
          new String(in, pos, length, "UTF-8");
        int rsId = Integer.valueOf(serverIdString);
        pos += length + 1;

        /* Read the generation id */
        length = getNextLength(in, pos);
        long generationId =
          Long.valueOf(new String(in, pos, length,
          "UTF-8"));
        pos += length + 1;

        /* Read DS status */
        ServerStatus status = ServerStatus.valueOf(in[pos++]);

        /* Read DS assured flag */
        boolean assuredFlag;
        assuredFlag = in[pos++] == 1;

        /* Read DS assured mode */
        AssuredMode assuredMode = AssuredMode.valueOf(in[pos++]);

        /* Read DS safe data level */
        byte safeDataLevel = in[pos++];

        /* Read DS group id */
        byte groupId = in[pos++];

        /* Read number of referrals URLs */
        List<String> refUrls = new ArrayList<String>();
        byte nUrls = in[pos++];
        byte nRead = 0;
        /* Read urls until expected number read */
        while ((nRead != nUrls) &&
          (pos < in.length) //security
          )
        {
          length = getNextLength(in, pos);
          String url = new String(in, pos, length, "UTF-8");
          refUrls.add(url);
          pos += length + 1;
          nRead++;
        }

        Set<String> attrs = new HashSet<String>();
        Set<String> delattrs = new HashSet<String>();
        short protocolVersion = -1;
        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          byte nAttrs = in[pos++];
          nRead = 0;
          /* Read attrs until expected number read */
          while ((nRead != nAttrs) && (pos < in.length))
          {
            length = getNextLength(in, pos);
            String attr = new String(in, pos, length, "UTF-8");
            attrs.add(attr);
            pos += length + 1;
            nRead++;
          }

          if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
          {
            nAttrs = in[pos++];
            nRead = 0;
            /* Read attrs until expected number read */
            while ((nRead != nAttrs) && (pos < in.length))
            {
              length = getNextLength(in, pos);
              String attr = new String(in, pos, length, "UTF-8");
              delattrs.add(attr);
              pos += length + 1;
              nRead++;
            }
          }
          else
          {
            // Default to using the same set of attributes for deletes.
            delattrs.addAll(attrs);
          }

          /* Read Protocol version */
          protocolVersion = (short)in[pos++];
        }

        /* Now create DSInfo and store it in list */

        DSInfo dsInfo = new DSInfo(dsId, dsUrl, rsId, generationId, status,
          assuredFlag, assuredMode, safeDataLevel, groupId, refUrls, attrs,
          delattrs, protocolVersion);
        dsList.add(dsInfo);

        nDsInfo--;
      }

      /* Read number of following RS info entries */

      byte nRsInfo = in[pos++];

      /* Read the RS info entries */
      List<RSInfo> rsList = new ArrayList<RSInfo>(Math.max(0, nRsInfo));
      while ( (nRsInfo > 0) && (pos < in.length) )
      {
        /* Read RS id */
        int length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        int id = Integer.valueOf(serverIdString);
        pos += length + 1;

        /* Read the generation id */
        length = getNextLength(in, pos);
        long generationId =
          Long.valueOf(new String(in, pos, length,
          "UTF-8"));
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

        /* Now create RSInfo and store it in list */

        RSInfo rsInfo = new RSInfo(id, serverUrl, generationId, groupId,
          weight);
        rsList.add(rsInfo);

        nRsInfo--;
      }

      this.dsList = Collections.unmodifiableList(dsList);
      this.rsList = Collections.unmodifiableList(rsList);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Creates a new  message from a list of the currently connected servers.
   *
   * @param dsList The list of currently connected DS servers ID.
   * @param rsList The list of currently connected RS servers ID.
   */
  public TopologyMsg(List<DSInfo> dsList, List<RSInfo> rsList)
  {
    if (dsList == null || dsList.isEmpty())
    {
      this.dsList = Collections.emptyList();
    }
    else
    {
      this.dsList = Collections.unmodifiableList(new ArrayList<DSInfo>(dsList));
    }

    if (rsList == null || rsList.isEmpty())
    {
      this.rsList = Collections.emptyList();
    }
    else
    {
      this.rsList = Collections.unmodifiableList(new ArrayList<RSInfo>(rsList));
    }
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short version)
  throws UnsupportedEncodingException
  {
    try
    {
      /**
       * Message has the following form:
       * <pdu type><number of following DSInfo entries>[<DSInfo>]*
       * <number of following RSInfo entries>[<RSInfo>]*
       */
      ByteArrayOutputStream oStream = new ByteArrayOutputStream();

      /* Put the message type */
      oStream.write(MSG_TYPE_TOPOLOGY);

      // Put number of following DS info entries
      oStream.write((byte)dsList.size());

      // Put DS info
      for (DSInfo dsInfo : dsList)
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
        byteServerId =
          String.valueOf(dsInfo.getRsId()).getBytes("UTF-8");
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

        List<String> refUrls = dsInfo.getRefUrls();
        // Put number of following URLs as a byte
        oStream.write(refUrls.size());
        for (String url : refUrls)
        {
          // Write the url and a 0 terminating byte
          oStream.write(url.getBytes("UTF-8"));
          oStream.write(0);
        }

        if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          // Put ECL includes
          Set<String> attrs = dsInfo.getEclIncludes();
          oStream.write(attrs.size());
          for (String attr : attrs)
          {
            oStream.write(attr.getBytes("UTF-8"));
            oStream.write(0);
          }

          if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
          {
            Set<String> delattrs = dsInfo.getEclIncludesForDeletes();
            oStream.write(delattrs.size());
            for (String attr : delattrs)
            {
              oStream.write(attr.getBytes("UTF-8"));
              oStream.write(0);
            }
          }

          oStream.write(dsInfo.getProtocolVersion());
        }
      }

      // Put number of following RS info entries
      oStream.write((byte)rsList.size());

      // Put RS info
      for (RSInfo rsInfo : rsList)
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



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String dsStr = "";
    for (DSInfo dsInfo : dsList)
    {
      dsStr += dsInfo.toString() + "\n----------------------------\n";
    }

    String rsStr = "";
    for (RSInfo rsInfo : rsList)
    {
      rsStr += rsInfo.toString() + "\n----------------------------\n";
    }

    return ("TopologyMsg content: "
      + "\n----------------------------"
      + "\nCONNECTED DS SERVERS:"
      + "\n--------------------\n"
      + dsStr
      + "CONNECTED RS SERVERS:"
      + "\n--------------------\n"
      + rsStr + (rsStr.equals("") ? "----------------------------\n" : ""));
  }

  /**
   * Get the list of DS info.
   * @return The list of DS info
   */
  public List<DSInfo> getDsList()
  {
    return dsList;
  }

  /**
   * Get the list of RS info.
   * @return The list of RS info
   */
  public List<RSInfo> getRsList()
  {
    return rsList;
  }
}
