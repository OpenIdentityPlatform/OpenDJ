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
package org.opends.server.replication.server;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ProtocolVersion;

/**
 * This is a facility class that is in fact an hack to optimize replication
 * server performances in case of assured replication usage:
 * When received from a server by a server reader, an update message is to be
 * posted in the queues of the server writers. Thus, they receive the same
 * reference of update message. As we want to transform an assured update
 * message to an equivalent not assured one for some servers but not for all,
 * instead of performing a painful clone of the message, we use this special
 * class to keep a reference to the real object, but that will overwrite the
 * assured flag value to false when serializing the message, and return false
 * when calling the isAssured() method.
 *
 */
public class NotAssuredUpdateMsg extends UpdateMsg
{
  // The real update message this message represents
  private UpdateMsg realUpdateMsg = null;

  /**
   * Creates a new empty UpdateMsg.
   * This class is only used by replication server code so constructor is not
   * public by security.
   * @param updateMsg The real underlying update message this object represents.
   */
  NotAssuredUpdateMsg(UpdateMsg updateMsg)
  {
    realUpdateMsg = updateMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ChangeNumber getChangeNumber()
  {
    return realUpdateMsg.getChangeNumber();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isAssured()
  {
    // Always return false as we represent a not assured message
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAssured(boolean assured)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    // Compare with the underlying real update message
    if (obj != null)
    {
      if (obj.getClass() != realUpdateMsg.getClass())
        return false;
      return realUpdateMsg.getChangeNumber().
        equals(((UpdateMsg)obj).getChangeNumber());
    }
    else
    {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return realUpdateMsg.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int compareTo(UpdateMsg msg)
  {
    return realUpdateMsg.compareTo(msg);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short reqProtocolVersion)
    throws UnsupportedEncodingException
  {
    // Get the encoding of the real message then overwrite the assured flag byte
    // to always be false
    byte[] bytes = realUpdateMsg.getBytes(reqProtocolVersion);
    int maxLen = bytes.length;
    int pos = -1;
    int nZeroFound = 0; // Number of 0 value found
    boolean found = false;

    /**
     * Overwrite the assured flag at the right position according to
     * message type.
     */
    // Look for assured flag position
    if (realUpdateMsg instanceof LDAPUpdateMsg)
    {
      // LDAP update message
      switch (reqProtocolVersion)
      {
        case ProtocolVersion.REPLICATION_PROTOCOL_V1:
          /* The message header is stored in the form :
           * <operation type><changenumber><dn><assured><entryuuid><change>
           * the length of result byte array is therefore :
           *   1 + change number length + 1 + dn length + 1  + 1 +
           *   uuid length + 1 + additional_length
           * See LDAPUpdateMsg.encodeHeader_V1() for more information
           */
          // Find end of change number then end of dn
          for (pos = 1 ; pos < maxLen ; pos++ ) {
            if (bytes[pos] == (byte)0)
            {
              nZeroFound++;
              if (nZeroFound == 2) // 2 end of string to find
              {
                found = true;
                break;
              }
            }
          }
          if (!found)
            throw new UnsupportedEncodingException("Could not find end of " +
              "change number.");
          pos++;
          if (pos >= maxLen)
            throw new UnsupportedEncodingException("Reached end of packet.");
          // Force assured flag to false
          bytes[pos] = (byte)0;
          break;
        case ProtocolVersion.REPLICATION_PROTOCOL_V2:
          /* The message header is stored in the form :
           * <operation type><protocol version><changenumber><dn><entryuuid>
           * <assured> <assured mode> <safe data level>
           * the length of result byte array is therefore :
           *   1 + 1 + change number length + 1 + dn length + 1 + uuid length +
           *   1 + 1 + 1 + 1 + additional_length
           * See LDAPUpdateMsg.encodeHeader() for more information
           */
          // Find end of change number then end of dn then end of uuid
          for (pos = 2 ; pos < maxLen ; pos++ ) {
            if (bytes[pos] == (byte)0)
            {
              nZeroFound++;
              if (nZeroFound == 3) // 3 end of string to find
              {
                found = true;
                break;
              }
            }
          }
          if (!found)
            throw new UnsupportedEncodingException("Could not find end of " +
              "change number.");
          pos++;
          if (pos >= maxLen)
            throw new UnsupportedEncodingException("Reached end of packet.");
          // Force assured flag to false
          bytes[pos] = (byte)0;
          break;
        default:
          throw new UnsupportedEncodingException("Unsupported requested " +
            " protocol version: " + reqProtocolVersion);
      }
    } else
    {
      if (!(realUpdateMsg instanceof UpdateMsg))
      {
        // Should never happen
        throw new UnsupportedEncodingException(
          "Unknown underlying real message type.");
      }
      // This is a generic update message
      /* The message header is stored in the form :
       * <operation type><protocol version><changenumber><assured>
       * <assured mode> <safe data level>
       * the length of result byte array is therefore :
       *   1 + 1 + change number length + 1 + 1
       *   + 1 + 1 + additional_length
       * See UpdateMsg.encodeHeader() for more  information
       */
      // Find end of change number
      for (pos = 2 ; pos < maxLen ; pos++ )
      {
        if (bytes[pos] == (byte)0)
        {
          nZeroFound++;
          if (nZeroFound == 1) // 1 end of string to find
          {
            found = true;
            break;
          }
        }
      }
      if (!found)
        throw new UnsupportedEncodingException("Could not find end of " +
          "change number.");
      pos++;
      if (pos >= maxLen)
        throw new UnsupportedEncodingException("Reached end of packet.");
      // Force assured flag to false
      bytes[pos] = (byte) 0;
    }
    return bytes;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    return getBytes(ProtocolVersion.getCurrentVersion());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AssuredMode getAssuredMode()
  {
    return realUpdateMsg.getAssuredMode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte getSafeDataLevel()
  {
    return realUpdateMsg.getSafeDataLevel();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setAssuredMode(AssuredMode assuredMode)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSafeDataLevel(byte safeDataLevel)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public short getVersion()
  {
    return realUpdateMsg.getVersion();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return realUpdateMsg.size();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected byte[] encodeHeader(byte type, int additionalLength)
    throws UnsupportedEncodingException
  {
    // No called as only used by constructors using bytes
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int decodeHeader(byte type, byte[] encodedMsg)
                          throws DataFormatException
  {
    // No called as only used by getBytes methods
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getPayload()
  {
    return realUpdateMsg.getPayload();
  }
}
