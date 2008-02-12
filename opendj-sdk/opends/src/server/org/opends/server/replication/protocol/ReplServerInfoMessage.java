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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.replication.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

/**
 *
 * This class defines a message that is sent by a replication server
 * to the other replication servers in the topology containing the list
 * of LDAP servers directly connected to it.
 * A replication server sends a ReplServerInfoMessage when an LDAP
 * server connects or disconnects.
 *
 * Exchanging these messages allows to have each replication server
 * knowing the complete list of LDAP servers in the topology and
 * their associated replication server and thus take the appropriate
 * decision to route a message to an LDAP server.
 *
 */
public class ReplServerInfoMessage extends ReplicationMessage
{
  private List<String> connectedServers = null;
  private long generationId;

  /**
   * Creates a new changelogInfo message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws java.util.zip.DataFormatException If the byte array does not
   * contain a valid encoded form of the message.
   */
  public ReplServerInfoMessage(byte[] in) throws DataFormatException
  {
    try
    {
      /* first byte is the type */
      if (in.length < 1 || in[0] != MSG_TYPE_REPL_SERVER_INFO)
        throw new DataFormatException(
        "Input is not a valid " + this.getClass().getCanonicalName());

      int pos = 1;

      /* read the generationId */
      int length = getNextLength(in, pos);
      generationId = Long.valueOf(new String(in, pos, length,
          "UTF-8"));
      pos += length +1;

      /* read the connected servers */
      connectedServers = new ArrayList<String>();
      while (pos < in.length)
      {
        /*
         * Read the next server ID
         * first calculate the length then construct the string
         */
        length = getNextLength(in, pos);
        connectedServers.add(new String(in, pos, length, "UTF-8"));
        pos += length +1;
      }
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }


  /**
   * Creates a new ReplServerInfo message from a list of the currently
   * connected servers.
   *
   * @param connectedServers The list of currently connected servers ID.
   * @param generationId     The generationId currently associated with this
   *                         domain.
   */
  public ReplServerInfoMessage(List<String> connectedServers,
      long generationId)
  {
    this.connectedServers = connectedServers;
    this.generationId = generationId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    try
    {
      ByteArrayOutputStream oStream = new ByteArrayOutputStream();

      /* Put the message type */
      oStream.write(MSG_TYPE_REPL_SERVER_INFO);

      // Put the generationId
      oStream.write(String.valueOf(generationId).getBytes("UTF-8"));
      oStream.write(0);

      // Put the servers
      if (connectedServers.size() >= 1)
      {
        for (String server : connectedServers)
        {
          byte[] byteServerURL = server.getBytes("UTF-8");
          oStream.write(byteServerURL);
          oStream.write(0);
        }
      }

      return oStream.toByteArray();
    }
    catch (IOException e)
    {
      // never happens
      return null;
    }
  }

  /**
   * Get the list of servers currently connected to the Changelog server
   * that generated this message.
   *
   * @return A collection of the servers currently connected to the Changelog
   *         server that generated this message.
   */
  public List<String> getConnectedServers()
  {
    return connectedServers;
  }

  /**
   * Get the generationId from this message.
   * @return The generationId.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    String csrvs = "";
    for (String s : connectedServers)
    {
      csrvs += s + "/";
    }
    return ("ReplServerInfoMessage: genId=" + getGenerationId() +
            " Connected peers:" + csrvs);
  }

}
