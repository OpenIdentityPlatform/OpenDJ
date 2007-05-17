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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.asn1.ASN1OctetString;


/**
 * ServerState class.
 * This object is used to store the last update seem on this server
 * from each server.
 * It is exchanged with the replication servers at connection establishment
 * time.
 */
public class ServerState implements Iterable<Short>
{
  private HashMap<Short, ChangeNumber> list;

  /**
   * Creates a new empty ServerState.
   */
  public ServerState()
  {
    list = new HashMap<Short, ChangeNumber>();
  }

  /**
   * Empty the ServerState.
   * After this call the Server State will be in the same state
   * as if it was just created.
   */
  public void clear()
  {
    synchronized (this)
    {
      list.clear();
    }
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
  public ServerState(byte[] in, int pos, int endpos)
         throws DataFormatException
  {
    try
    {
      list = new HashMap<Short, ChangeNumber>();

      while (endpos > pos)
      {
        /*
         * read the ServerId
         */
        int length = getNextLength(in, pos);
        String serverIdString = new String(in, pos, length, "UTF-8");
        short serverId = Short.valueOf(serverIdString);
        pos += length +1;

        /*
         * read the ChangeNumber
         */
        length = getNextLength(in, pos);
        String cnString = new String(in, pos, length, "UTF-8");
        ChangeNumber cn = new ChangeNumber(cnString);
        pos += length +1;

        /*
         * Add the serverid
         */
        list.put(serverId, cn);
      }

    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the length of the next String encoded in the in byte array.
   *
   * @param in the byte array where to calculate the string.
   * @param pos the position whre to start from in the byte array.
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
        throw new DataFormatException("byte[] is not a valid modify msg");
      length++;
    }
    return length;
  }

  /**
   * Update the Server State with a ChangeNumber.
   * All operations with smaller CSN and the same serverID must be committed
   * before calling this method.
   * @param changeNumber the committed ChangeNumber.
   * @return a boolean indicating if the update was meaningfull.
   */
  public boolean update(ChangeNumber changeNumber)
  {
    if (changeNumber == null)
      return false;
    synchronized(this)
    {
      Short id =  changeNumber.getServerId();
      ChangeNumber oldCN = list.get(id);
      if (oldCN == null || changeNumber.newer(oldCN))
      {
        list.put(id,changeNumber);
        return true;
      }
      else
      {
        return false;
      }
    }
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
    HashSet<String> set = new HashSet<String>();
    synchronized (this)
    {

      for (Short key  : list.keySet())
      {
        ChangeNumber change = list.get(key);
        Date date = new Date(change.getTime());
        set.add(change.toString() + " " + date.toString());
      }
    }

    return set;
  }

  /**
   * Return an ArrayList of ANS1OctetString encoding the ChangeNumbers
   * contained in the ServerState.
   * @return an ArrayList of ANS1OctetString encoding the ChangeNumbers
   * contained in the ServerState.
   */
  public ArrayList<ASN1OctetString> toASN1ArrayList()
  {
    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    synchronized (this)
    {
      for (Short id : list.keySet())
      {
        ASN1OctetString value = new ASN1OctetString(list.get(id).toString());
        values.add(value);
      }
    }
    return values;
  }
  /**
   * return the text representation of ServerState.
   * @return the text representation of ServerState
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();

    synchronized (this)
    {
      for (Short key  : list.keySet())
      {
        ChangeNumber change = list.get(key);
        buffer.append(" ");
        buffer.append(change);
      }
    }

    return buffer.toString();
  }

  /**
   * Get the largest ChangeNumber seen for a given LDAP server ID.
   *
   * @param serverId : the server ID
   * @return the largest ChangeNumber seen
   */
  public ChangeNumber getMaxChangeNumber(short serverId)
  {
    return list.get(serverId);
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
    synchronized (this)
    {
      int length = 0;
      List<String> idList = new ArrayList<String>(list.size());
      for (short id : list.keySet())
      {
        String temp = String.valueOf(id);
        idList.add(temp);
        length += temp.length() + 1;
      }
      List<String> cnList = new ArrayList<String>(list.size());
      for (ChangeNumber cn : list.values())
      {
        String temp = cn.toString();
        cnList.add(temp);
        length += temp.length() + 1;
      }
      byte[] result = new byte[length];

      int pos = 0;
      for (int i=0; i< list.size(); i++)
      {
        String str = idList.get(i);
        pos = addByteArray(str.getBytes("UTF-8"), result, pos);
        str = cnList.get(i);
        pos = addByteArray(str.getBytes("UTF-8"), result, pos);
      }
      return result;
    }
  }

  /**
   * {@inheritDoc}
   */
  public Iterator<Short> iterator()
  {
    return list.keySet().iterator();
  }

  /**
   * Check that all the ChangeNumbers in the covered serverState are also in
   * this serverState.
   *
   * @param covered The ServerState that needs to be checked.
   * @return A boolean indicating if this ServerState covers the ServerState
   *         given in parameter.
   */
  public boolean cover(ServerState covered)
  {
    for (ChangeNumber coveredChange : covered.list.values())
    {
      ChangeNumber change = this.list.get(coveredChange.getServerId());
      if ((change == null) || (change.older(coveredChange)))
      {
        return false;
      }
    }
    return true;
  }
}
