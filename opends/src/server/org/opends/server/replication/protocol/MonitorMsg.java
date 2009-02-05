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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

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
    HashMap<Short, ServerData> ldapStates =
      new HashMap<Short, ServerData>();
    // The data related to the RS servers connected to this RS
    HashMap<Short, ServerData> rsStates =
      new HashMap<Short, ServerData>();
  }

  SubTopoMonitorData data = new SubTopoMonitorData();;

  /**
   * Creates a new EntryMessage.
   *
   * @param sender The sender of this message.
   * @param destination The destination of this message.
   */
  public MonitorMsg(short sender, short destination)
  {
    super(sender, destination);
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
  public void setServerState(short serverId, ServerState state,
      Long approxFirstMissingDate, boolean isLDAP)
  {
    if (data.ldapStates == null)
    {
      data.ldapStates = new HashMap<Short, ServerData>();
    }
    if (data.rsStates == null)
    {
      data.rsStates = new HashMap<Short, ServerData>();
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
  public ServerState getLDAPServerState(short serverId)
  {
    return data.ldapStates.get(serverId).state;
  }

  /**
   * Get the server state for the RS server with the provided serverId.
   * @param serverId The provided serverId.
   * @return The state.
   */
  public ServerState getRSServerState(short serverId)
  {
    return data.rsStates.get(serverId).state;
  }


  /**
   * Get the approximation of the date of the older missing change for the
   * LDAP Server with the provided server Id.
   * @param serverId The provided serverId.
   * @return The approximated state.
   */
  public Long getLDAPApproxFirstMissingDate(short serverId)
  {
    return data.ldapStates.get(serverId).approxFirstMissingDate;
  }

  /**
   * Get the approximation of the date of the older missing change for the
   * RS Server with the provided server Id.
   * @param serverId The provided serverId.
   * @return The approximated state.
   */
  public Long getRSApproxFirstMissingDate(short serverId)
  {
    return data.rsStates.get(serverId).approxFirstMissingDate;
  }

  /**
   * Creates a new EntryMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public MonitorMsg(byte[] in) throws DataFormatException
  {
    ByteSequenceReader reader = ByteString.wrap(in).asReader();

    /* first byte is the type */
    if (reader.get() != MSG_TYPE_REPL_SERVER_MONITOR)
      throw new DataFormatException("input is not a valid " +
          this.getClass().getCanonicalName());
    int pos = 1;

    // sender
    this.senderID = reader.getShort();

    // destination
    this.destination = reader.getShort();

    ASN1Reader asn1Reader = ASN1.getReader(reader);
    try
    {
      asn1Reader.readStartSequence();
      // loop on the servers
      while(asn1Reader.hasNextElement())
      {
        ServerState newState = new ServerState();
        short serverId = 0;
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
  {
    try
    {
      ByteStringBuilder byteBuilder = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(byteBuilder);

      /* put the type of the operation */
      byteBuilder.append(MSG_TYPE_REPL_SERVER_MONITOR);

      byteBuilder.append(senderID);
      byteBuilder.append(destination);

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
      Set<Short> servers = data.ldapStates.keySet();
      for (Short sid : servers)
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
      for (Short sid : servers)
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

      return byteBuilder.toByteArray();
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
  public Iterator<Short> ldapIterator()
  {
    return data.ldapStates.keySet().iterator();
  }

  /**
   * Returns an iterator on the serverId of the connected RS servers.
   * @return The iterator.
   */
  public Iterator<Short> rsIterator()
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
    for (Short sid : data.ldapStates.keySet())
    {
      ServerData sd = data.ldapStates.get(sid);
      stateS +=
               "\n[LSstate("+ sid + ")=" +
                sd.state.toString() + "]" +
                " afmd=" + sd.approxFirstMissingDate + "]";
    }

    stateS += "\nRSStates:[";
    for (Short sid : data.rsStates.keySet())
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
