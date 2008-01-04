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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.opends.server.replication.common.ServerState;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.replication.common.ChangeNumber;

/**
 * This message is part of the replication protocol.
 * RS1 sends a MonitorRequestMessage to RS2 to requests its monitoring
 * informations.
 * When RS2 receives a MonitorRequestMessage from RS1, RS2 responds with a
 * MonitorMessage.
 */
public class MonitorMessage extends RoutableMessage implements
    Serializable
{

  private static final long serialVersionUID = -1900670921496804942L;

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
   * Data structure to manage the state of the replication server
   * and the state informations for the LDAP servers connected.
   *
   */
  class SubTopoMonitorData
  {
    ServerState replServerState;
    HashMap<Short, ServerData> ldapStates =
      new HashMap<Short, ServerData>();
  }

  SubTopoMonitorData data = new SubTopoMonitorData();;

  /**
   * Creates a new EntryMessage.
   *
   * @param sender The sender of this message.
   * @param destination The destination of this message.
   */
  public MonitorMessage(short sender, short destination)
  {
    super(sender, destination);
  }

  /**
   * Sets the state of the replication server.
   * @param state The state.
   */
  public void setReplServerState(ServerState state)
  {
    data.replServerState = state;
  }

  /**
   * Sets the informations of an LDAP server.
   * @param serverId The serverID.
   * @param state The server state.
   * @param approxFirstMissingDate  The approximation of the date
   * of the older missing change.
   *
   */
  public void setLDAPServerState(short serverId, ServerState state,
      Long approxFirstMissingDate)
  {
    if (data.ldapStates == null)
    {
      data.ldapStates = new HashMap<Short, ServerData>();
    }
    ServerData sd = new ServerData();
    sd.state = state;
    sd.approxFirstMissingDate = approxFirstMissingDate;
    data.ldapStates.put(serverId, sd);
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
   * Get the approximation of the date of the older missing change for the
   * LDAP Server with the provided server Id.
   * @param serverId The provided serverId.
   * @return The approximated state.
   */
  public Long getApproxFirstMissingDate(short serverId)
  {
    return data.ldapStates.get(serverId).approxFirstMissingDate;
  }


  /**
   * Creates a new EntryMessage from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public MonitorMessage(byte[] in) throws DataFormatException
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
      this.senderID = Short.valueOf(senderIDString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String destinationString = new String(in, pos, length, "UTF-8");
      this.destination = Short.valueOf(destinationString);
      pos += length +1;

       /* Read the states : all the remaining bytes but the terminating 0 */
      byte[] encodedS = new byte[in.length-pos-1];
      int i =0;
      while (pos<in.length-1)
      {
        encodedS[i++] = in[pos++];
      }


      try
      {
        ASN1Sequence s0 = ASN1Sequence.decodeAsSequence(encodedS);
        for (ASN1Element el0 : s0.elements())
        {
          ServerState newState = new ServerState();
          short serverId = 0;
          Long outime = (long)0;
          ASN1Sequence s1 = el0.decodeAsSequence();
          for (ASN1Element el1 : s1.elements())
          {
            ASN1OctetString o = el1.decodeAsOctetString();
            String s = o.stringValue();
            ChangeNumber cn = new ChangeNumber(s);
            if ((data.replServerState != null) && (serverId == 0))
            {
              serverId = cn.getServerId();
              outime = cn.getTime();
            }
            else
            {
              newState.update(cn);
            }
          }

          // the first state is the replication state
          if (data.replServerState == null)
          {
            data.replServerState = newState;
          }
          else
          {
            ServerData sd = new ServerData();
            sd.state = newState;
            sd.approxFirstMissingDate = outime;
            data.ldapStates.put(serverId, sd);
          }
        }
      } catch(Exception e)
      {

      }
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
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
      byte[] senderBytes = String.valueOf(senderID).getBytes("UTF-8");
      byte[] destinationBytes = String.valueOf(destination).getBytes("UTF-8");

      int length = 1 + senderBytes.length +
                   1 + destinationBytes.length;

      ASN1Sequence stateElementSequence = new ASN1Sequence();
      ArrayList<ASN1Element> stateElementList = new ArrayList<ASN1Element>();

      // First loop computes the length
      /* Put the serverStates ... */
      stateElementSequence = new ASN1Sequence();
      stateElementList = new ArrayList<ASN1Element>();

      /* first put the Replication Server state */
      ArrayList<ASN1OctetString> cnOctetList =
        data.replServerState.toASN1ArrayList();
      ArrayList<ASN1Element> cnElementList = new ArrayList<ASN1Element>();
      for (ASN1OctetString soci : cnOctetList)
      {
        cnElementList.add(soci);
      }
      ASN1Sequence cnSequence = new ASN1Sequence(cnElementList);
      stateElementList.add(cnSequence);

      // then the LDAP server data
      Set<Short> servers = data.ldapStates.keySet();
      for (Short sid : servers)
      {
        // State
        ServerState statei = data.ldapStates.get(sid).state;
        // First missing date
        Long outime =  data.ldapStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();
        cnElementList = new ArrayList<ASN1Element>();

        // a fake changenumber helps storing the LDAP server ID
        // and the olderupdatetime
        ChangeNumber cn = new ChangeNumber(outime,0,sid);
        cnElementList.add(new ASN1OctetString(cn.toString()));

        // the changenumbers
        for (ASN1OctetString soci : cnOctetList)
        {
          cnElementList.add(soci);
        }

        cnSequence = new ASN1Sequence(cnElementList);
        stateElementList.add(cnSequence);
      }
      stateElementSequence.setElements(stateElementList);
      int seqLen = stateElementSequence.encode().length;

      //
      length += seqLen;
      length += 2;

      // Allocate the array sized from the computed length
      byte[] resultByteArray = new byte[length];

      // Second loop build the array

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_REPL_SERVER_MONITOR;
      int pos = 1;

      pos = addByteArray(senderBytes, resultByteArray, pos);
      pos = addByteArray(destinationBytes, resultByteArray, pos);

      /* Put the serverStates ... */
      stateElementSequence = new ASN1Sequence();
      stateElementList = new ArrayList<ASN1Element>();

      /* first put the Replication Server state */
      cnOctetList =
        data.replServerState.toASN1ArrayList();
      cnElementList = new ArrayList<ASN1Element>();
      for (ASN1OctetString soci : cnOctetList)
      {
        cnElementList.add(soci);
      }
      cnSequence = new ASN1Sequence(cnElementList);
      stateElementList.add(cnSequence);

      // then the LDAP server state
      servers = data.ldapStates.keySet();
      for (Short sid : servers)
      {
        ServerState statei = data.ldapStates.get(sid).state;
        Long outime = data.ldapStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();
        cnElementList = new ArrayList<ASN1Element>();

        // a fake changenumber helps storing the LDAP server ID
        ChangeNumber cn = new ChangeNumber(outime,0,sid);
        cnElementList.add(new ASN1OctetString(cn.toString()));

        // the changenumbers
        for (ASN1OctetString soci : cnOctetList)
        {
          cnElementList.add(soci);
        }

        cnSequence = new ASN1Sequence(cnElementList);
        stateElementList.add(cnSequence);
      }
      stateElementSequence.setElements(stateElementList);
      pos = addByteArray(stateElementSequence.encode(), resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * Get the state of the replication server that sent this message.
   * @return The state.
   */
  public ServerState getReplServerState()
  {
    return data.replServerState;
  }

  /**
   * Returns an iterator on the serverId of the connected LDAP servers.
   * @return The iterator.
   */
  public Iterator<Short> iterator()
  {
    return data.ldapStates.keySet().iterator();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String stateS = " RState:";
    stateS += "/" + data.replServerState.toString();
    stateS += " LDAPStates:";
    Iterator<ServerData> it = data.ldapStates.values().iterator();
    while (it.hasNext())
    {
      ServerData sd = it.next();
      stateS += "/ state=" + sd.state.toString()
      + " afmd=" + sd.approxFirstMissingDate + "] ";
    }

    String me = this.getClass().getCanonicalName() +
    " sender=" + this.senderID +
    " destination=" + this.destination +
    " states=" + stateS +
    "]";
    return me;
  }
}
