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
package org.opends.server.synchronization.protocol;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.synchronization.common.ServerState;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;

/**
 * Message sent by a changelog server to another changelog server at Startup.
 */
public class ChangelogStartMessage extends SynchronizationMessage implements
    Serializable
{
  private static final long serialVersionUID = -5871385537169856856L;

  private String baseDn = null;
  private short serverId;
  private String serverURL;
  private ServerState serverState;

  private int windowSize;

  /**
   * Create a ChangelogStartMessage.
   *
   * @param serverId changelog server id
   * @param serverURL changelog server URL
   * @param baseDn base DN for which the ChangelogStartMessage is created.
   * @param windowSize The window size.
   * @param serverState our ServerState for this baseDn.
   */
  public ChangelogStartMessage(short serverId, String serverURL, DN baseDn,
                               int windowSize,
                               ServerState serverState)
  {
    this.serverId = serverId;
    this.serverURL = serverURL;
    if (baseDn != null)
      this.baseDn = baseDn.toNormalizedString();
    else
      this.baseDn = null;
    this.windowSize = windowSize;
    this.serverState = serverState;
  }

  /**
   * Creates a new ChangelogStartMessage by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the
   *             ChangelogStartMessage
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded ChangelogStartMessage.
   */
  public ChangelogStartMessage(byte[] in) throws DataFormatException
  {
    /* The ChangelogStartMessage is encoded in the form :
     * <baseDn><ServerId><ServerUrl><windowsize><ServerState>
     */
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_CHANGELOG_START)
        throw new DataFormatException("input is not a valid ChangelogStartMsg");
      int pos = 1;

      /* read the dn
       * first calculate the length then construct the string
       */
      int length = getNextLength(in, pos);
      baseDn = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the ServerId
       */
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      serverId = Short.valueOf(serverIdString);
      pos += length +1;

      /*
       * read the ServerURL
       */
      length = getNextLength(in, pos);
      serverURL = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the window size
       */
      length = getNextLength(in, pos);
      windowSize = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
      * read the ServerState
      */
      serverState = new ServerState(in, pos, in.length-1);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the Server Id.
   * @return the server id
   */
  public short getServerId()
  {
    return this.serverId;
  }

  /**
   * Set the server URL.
   * @return the server URL
   */
  public String getServerURL()
  {
    return this.serverURL;
  }

  /**
   * Get the base DN from this ChangelogStartMessage.
   *
   * @return the base DN from this ChangelogStartMessage.
   */
  public DN getBaseDn()
  {
    if (baseDn == null)
      return null;
    try
    {
      return DN.decode(baseDn);
    } catch (DirectoryException e)
    {
      return null;
    }
  }

  /**
   * Get the serverState.
   * @return Returns the serverState.
   */
  public ServerState getServerState()
  {
    return this.serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    /* The ChangelogStartMessage is stored in the form :
     * <operation type><basedn><serverid><serverURL><windowsize><serverState>
     */
    try {
      byte[] byteDn = baseDn.getBytes("UTF-8");
      byte[] byteServerId = String.valueOf(serverId).getBytes("UTF-8");
      byte[] byteServerUrl = serverURL.getBytes("UTF-8");
      byte[] byteServerState = serverState.getBytes();
      byte[] byteWindowSize = String.valueOf(windowSize).getBytes("UTF-8");

      int length = 1 + byteDn.length + 1 + byteServerId.length + 1 +
          byteServerUrl.length + 1 + byteWindowSize.length + 1 +
          byteServerState.length + 1;

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_CHANGELOG_START;
      int pos = 1;

      /* put the baseDN and a terminating 0 */
      pos = addByteArray(byteDn, resultByteArray, pos);

      /* put the ServerId */
      pos = addByteArray(byteServerId, resultByteArray, pos);

      /* put the ServerURL */
      pos = addByteArray(byteServerUrl, resultByteArray, pos);

      /* put the window size */
      pos = addByteArray(byteWindowSize, resultByteArray, pos);

      /* put the ServerState */
      pos = addByteArray(byteServerState, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * get the window size for the server that created this message.
   *
   * @return The window size for the server that created this message.
   */
  public int getWindowSize()
  {
    return windowSize;
  }
}
