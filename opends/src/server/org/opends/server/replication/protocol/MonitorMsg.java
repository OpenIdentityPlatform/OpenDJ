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
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.opends.server.replication.common.ServerState;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteSequenceReader;

/**
 * This message is part of the replication protocol.
 * RS1 sends a MonitorRequestMessage to RS2 to requests its monitoring
 * informations.
 * When RS2 receives a MonitorRequestMessage from RS1, RS2 responds with a
 * MonitorMsg.
 */
public class MonitorMsg extends RoutableMsg
{
  /**
   * Data structure to manage the state and the approximation
   * of the data of the first missing change for each LDAP server
   * connected to a Replication Server.
   */
  class ServerData
  {
    ServerState state;
    Long approxFirstMissingDate;
  }

  /**
   * Data structure to manage the state of this replication server
   * and the state informations for the servers connected to it.
   *
   */
  class SubTopoMonitorData
  {
    // This replication server DbState
    ServerState replServerDbState;
    // The data related to the LDAP servers connected to this RS
    HashMap<Integer, ServerData> ldapStates =
      new HashMap<Integer, ServerData>();
    // The data related to the RS servers connected to this RS
    HashMap<Integer, ServerData> rsStates =
      new HashMap<Integer, ServerData>();
  }

  SubTopoMonitorData data = new SubTopoMonitorData();

  /**
   * Creates a new MonitorMsg.
   *
   * @param sender The sender of this message.
   * @param destination The destination of this message.
   */
  public MonitorMsg(int sender, int destination)
  {
    super(sender, destination);
  }

  /**
   * Sets the sender ID.
   * @param senderID The sender ID.
   */
  public void setSenderID(int senderID)
  {
    this.senderID = senderID;
  }

  /**
   * Sets the destination.
   * @param destination The destination.
   */
  public void setDestination(int destination)
  {
    this.destination = destination;
  }

  /**
   * Sets the state of the replication server.
   * @param state The state.
   */
  public void setReplServerDbState(ServerState state)
  {
    data.replServerDbState = state;
  }

  /**
   * Sets the informations of an LDAP server.
   * @param serverId The serverID.
   * @param state The server state.
   * @param approxFirstMissingDate  The approximation of the date
   * of the older missing change. null when none.
   * @param isLDAP Specifies whether the server is a LS or a RS
   */
  public void setServerState(int serverId, ServerState state,
      Long approxFirstMissingDate, boolean isLDAP)
  {
    if (data.ldapStates == null)
    {
      data.ldapStates = new HashMap<Integer, ServerData>();
    }
    if (data.rsStates == null)
    {
      data.rsStates = new HashMap<Integer, ServerData>();
    }
    ServerData sd = new ServerData();
    sd.state = state;
    sd.approxFirstMissingDate = approxFirstMissingDate;
    if (isLDAP)
      data.ldapStates.put(serverId, sd);
    else
      data.rsStates.put(serverId, sd);
  }

  /**
   * Get the server state for the LDAP server with the provided serverId.
   * @param serverId The provided serverId.
   * @return The state.
   */
  public ServerState getLDAPServerState(int serverId)
  {
    return data.ldapStates.get(serverId).state;
  }

  /**
   * Get the server state for the RS server with the provided serverId.
   * @param serverId The provided serverId.
   * @return The state.
   */
  public ServerState getRSServerState(int serverId)
  {
    return data.rsStates.get(serverId).state;
  }


  /**
   * Get the approximation of the date of the older missing change for the
   * LDAP Server with the provided server Id.
   * @param serverId The provided serverId.
   * @return The approximated state.
   */
  public Long getLDAPApproxFirstMissingDate(int serverId)
  {
    return data.ldapStates.get(serverId).approxFirstMissingDate;
  }

  /**
   * Get the approximation of the date of the older missing change for the
   * RS Server with the provided server Id.
   * @param serverId The provided serverId.
   * @return The approximated state.
   */
  public Long getRSApproxFirstMissingDate(int serverId)
  {
    return data.rsStates.get(serverId).approxFirstMissingDate;
  }

  /**
   * Creates a new EntryMessage from its encoded form.
   *
   * @param in       The byte array containing the encoded form of the message.
   * @param version  The version of the protocol to use to decode the msg.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public MonitorMsg(byte[] in, short version) throws DataFormatException
  {
    ByteSequenceReader reader = ByteString.wrap(in).asReader();

    if (version == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      try
      {
        /* first byte is the type */
        if (in[0] != MSG_TYPE_REPL_SERVER_MONITOR)
          throw new DataFormatException("input is not a valid " +
              this.getClass().getCanonicalName());
        int pos = 1;

        // sender
        int length = getNextLength(in, pos);
        String senderIDString = new String(in, pos, length, "UTF-8");
        this.senderID = Integer.valueOf(senderIDString);
        pos += length +1;

        // destination
        length = getNextLength(in, pos);
        String destinationString = new String(in, pos, length, "UTF-8");
        this.destination = Integer.valueOf(destinationString);
        pos += length +1;

        reader.position(pos);
      }
      catch (UnsupportedEncodingException e)
      {
        throw new DataFormatException("UTF-8 is not supported by this jvm.");
      }
    }
    else
    {
      if (reader.get() != MSG_TYPE_REPL_SERVER_MONITOR)
        throw new DataFormatException("input is not a valid " +
            this.getClass().getCanonicalName());

      /*
       * V4 and above uses integers for its serverIds while V2 and V3
       * use shorts.
       */
      if (version <= ProtocolVersion.REPLICATION_PROTOCOL_V3)
      {
        // sender
        this.senderID = reader.getShort();

        // destination
        this.destination = reader.getShort();
      }
      else
      {
        // sender
        this.senderID = reader.getInt();

        // destination
        this.destination = reader.getInt();
      }
    }


    ASN1Reader asn1Reader = ASN1.getReader(reader);
    try
    {
      asn1Reader.readStartSequence();
      // loop on the servers
      while(asn1Reader.hasNextElement())
      {
        ServerState newState = new ServerState();
        int serverId = 0;
        Long outime = (long)0;
        boolean isLDAPServer = false;

        asn1Reader.readStartSequence();
        // loop on the list of CN of the state
        while(asn1Reader.hasNextElement())
        {
          String s = asn1Reader.readOctetStringAsString();
          ChangeNumber cn = new ChangeNumber(s);
          if ((data.replServerDbState != null) && (serverId == 0))
          {
            // we are on the first CN that is a fake CN to store the serverId
            // and the older update time
            serverId = cn.getServerId();
            outime = cn.getTime();
            isLDAPServer = (cn.getSeqnum()>0);
          }
          else
          {
            // we are on a normal CN
            newState.update(cn);
          }
        }
        asn1Reader.readEndSequence();

        if (data.replServerDbState == null)
        {
          // the first state is the replication state
          data.replServerDbState = newState;
        }
        else
        {
          // the next states are the server states
          ServerData sd = new ServerData();
          sd.state = newState;
          sd.approxFirstMissingDate = outime;
          if (isLDAPServer)
            data.ldapStates.put(serverId, sd);
          else
            data.rsStates.put(serverId, sd);
        }
      }
      asn1Reader.readEndSequence();
    } catch(Exception e)
    {

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  throws UnsupportedEncodingException
  {
    return getBytes(ProtocolVersion.getCurrentVersion());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
     throws UnsupportedEncodingException
  {
    try
    {
      ByteStringBuilder byteBuilder = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(byteBuilder);

      if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
      {
        /* put the type of the operation */
        byteBuilder.append(MSG_TYPE_REPL_SERVER_MONITOR);

        /*
         * V4 and above uses integers for its serverIds while V2 and V3
         * use shorts.
         */
        if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
        {
          byteBuilder.append(senderID);
          byteBuilder.append(destination);
        }
        else
        {
          byteBuilder.append((short)senderID);
          byteBuilder.append((short)destination);
        }
      }

      /* Put the serverStates ... */
      writer.writeStartSequence();

      /* first put the Replication Server state */
      writer.writeStartSequence();
      ArrayList<ByteString> cnOctetList =
        data.replServerDbState.toASN1ArrayList();
      for (ByteString soci : cnOctetList)
      {
        writer.writeOctetString(soci);
      }
      writer.writeEndSequence();

      // then the LDAP server datas
      Set<Integer> servers = data.ldapStates.keySet();
      for (Integer sid : servers)
      {
        ServerState statei = data.ldapStates.get(sid).state;
        Long outime = data.ldapStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();

        writer.writeStartSequence();
        // a fake changenumber helps storing the LDAP server ID
        ChangeNumber cn = new ChangeNumber(outime,1,sid);
        writer.writeOctetString(cn.toString());

        // the changenumbers that make the state
        for (ByteString soci : cnOctetList)
        {
          writer.writeOctetString(soci);
        }

        writer.writeEndSequence();
      }

      // then the RS server datas
      servers = data.rsStates.keySet();
      for (Integer sid : servers)
      {
        ServerState statei = data.rsStates.get(sid).state;
        Long outime = data.rsStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();

        writer.writeStartSequence();
        // a fake changenumber helps storing the LDAP server ID
        ChangeNumber cn = new ChangeNumber(outime,0,sid);
        writer.writeOctetString(cn.toString());

        // the changenumbers that make the state
        for (ByteString soci : cnOctetList)
        {
          writer.writeOctetString(soci);
        }

        writer.writeEndSequence();
      }

      writer.writeEndSequence();

      if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
      {
        return byteBuilder.toByteArray();
      }
      else
      {
        byte[] temp = byteBuilder.toByteArray();

        byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
        byte[] destinationBytes = String.valueOf(destination).getBytes("UTF-8");

        int length = 1 +  1 + senderBytes.length +
        1 + destinationBytes.length + temp.length +1;
        byte[] resultByteArray = new byte[length];

        /* put the type of the operation */
        resultByteArray[0] = MSG_TYPE_REPL_SERVER_MONITOR;
        int pos = 1;

        pos = addByteArray(senderBytes, resultByteArray, pos);
        pos = addByteArray(destinationBytes, resultByteArray, pos);
        pos = addByteArray(temp, resultByteArray, pos);

        return resultByteArray;
      }
    }
    catch (Exception e)
    {
      return null;
    }
  }

  /**
   * Get the state of the replication server that sent this message.
   * @return The state.
   */
  public ServerState getReplServerDbState()
  {
    return data.replServerDbState;
  }

  /**
   * Returns an iterator on the serverId of the connected LDAP servers.
   * @return The iterator.
   */
  public Iterator<Integer> ldapIterator()
  {
    return data.ldapStates.keySet().iterator();
  }

  /**
   * Returns an iterator on the serverId of the connected RS servers.
   * @return The iterator.
   */
  public Iterator<Integer> rsIterator()
  {
    return data.rsStates.keySet().iterator();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String stateS = "\nRState:[";
    stateS += data.replServerDbState.toString();
    stateS += "]";

    stateS += "\nLDAPStates:[";
    for (Integer sid : data.ldapStates.keySet())
    {
      ServerData sd = data.ldapStates.get(sid);
      stateS +=
               "\n[LSstate("+ sid + ")=" +
                sd.state.toString() + "]" +
                " afmd=" + sd.approxFirstMissingDate + "]";
    }

    stateS += "\nRSStates:[";
    for (Integer sid : data.rsStates.keySet())
    {
      ServerData sd = data.rsStates.get(sid);
      stateS +=
               "\n[RSState("+ sid + ")=" +
               sd.state.toString() + "]" +
               " afmd=" + sd.approxFirstMissingDate + "]";
    }
    String me = this.getClass().getCanonicalName() +
    "[ sender=" + this.senderID +
    " destination=" + this.destination +
    " data=[" + stateS + "]" +
    "]";
    return me;
  }
}
