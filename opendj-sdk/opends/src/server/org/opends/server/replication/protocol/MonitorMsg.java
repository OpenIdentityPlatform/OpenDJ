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
        // loop on the servers
        for (ASN1Element el0 : s0.elements())
        {
          ServerState newState = new ServerState();
          short serverId = 0;
          Long outime = (long)0;
          boolean isLDAPServer = false;
          ASN1Sequence s1 = el0.decodeAsSequence();

          // loop on the list of CN of the state
          for (ASN1Element el1 : s1.elements())
          {
            ASN1OctetString o = el1.decodeAsOctetString();
            String s = o.stringValue();
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

      /**
       * First loop computes the length
       */

      /* Put the serverStates ... */
      stateElementSequence = new ASN1Sequence();
      stateElementList = new ArrayList<ASN1Element>();

      /* first put the Replication Server state */
      ArrayList<ASN1OctetString> cnOctetList =
        data.replServerDbState.toASN1ArrayList();
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

      // then the rs server data
      servers = data.rsStates.keySet();
      for (Short sid : servers)
      {
        // State
        ServerState statei = data.rsStates.get(sid).state;
        // First missing date
        Long outime =  data.rsStates.get(sid).approxFirstMissingDate;

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

      /**
       * Second loop really builds the array
       */

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
        data.replServerDbState.toASN1ArrayList();
      cnElementList = new ArrayList<ASN1Element>();
      for (ASN1OctetString soci : cnOctetList)
      {
        cnElementList.add(soci);
      }
      cnSequence = new ASN1Sequence(cnElementList);
      stateElementList.add(cnSequence);

      // then the LDAP server datas
      servers = data.ldapStates.keySet();
      for (Short sid : servers)
      {
        ServerState statei = data.ldapStates.get(sid).state;
        Long outime = data.ldapStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();
        cnElementList = new ArrayList<ASN1Element>();

        // a fake changenumber helps storing the LDAP server ID
        ChangeNumber cn = new ChangeNumber(outime,1,sid);
        cnElementList.add(new ASN1OctetString(cn.toString()));

        // the changenumbers that make the state
        for (ASN1OctetString soci : cnOctetList)
        {
          cnElementList.add(soci);
        }

        cnSequence = new ASN1Sequence(cnElementList);
        stateElementList.add(cnSequence);
      }

      // then the RS server datas
      servers = data.rsStates.keySet();
      for (Short sid : servers)
      {
        ServerState statei = data.rsStates.get(sid).state;
        Long outime = data.rsStates.get(sid).approxFirstMissingDate;

        // retrieves the change numbers as an arrayList of ANSN1OctetString
        cnOctetList = statei.toASN1ArrayList();
        cnElementList = new ArrayList<ASN1Element>();

        // a fake changenumber helps storing the LDAP server ID
        ChangeNumber cn = new ChangeNumber(outime,0,sid);
        cnElementList.add(new ASN1OctetString(cn.toString()));

        // the changenumbers that make the state
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
