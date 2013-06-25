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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;



import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;



/**
 * Class that define messages sent by a replication domain (DS) to the
 * replication server to let the RS know the DS current change time.
 */
public class ChangeTimeHeartbeatMsg extends ReplicationMsg
{
  /**
   * The ChangeNumber containing the change time.
   */
  private final ChangeNumber changeNumber;



  /**
   * Constructor of a Change Time Heartbeat message providing the change time
   * value in a change number.
   *
   * @param cn
   *          The provided change number.
   */
  public ChangeTimeHeartbeatMsg(ChangeNumber cn)
  {
    this.changeNumber = cn;
  }



  /**
   * Get a change number with the transmitted change time.
   *
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Creates a message from a provided byte array.
   *
   * @param in
   *          The provided byte array.
   * @param version
   *          The version of the protocol to use to decode the msg.
   * @throws DataFormatException
   *           When an error occurs.
   */
  public ChangeTimeHeartbeatMsg(byte[] in, short version)
      throws DataFormatException
  {
    final ByteSequenceReader reader = ByteString.wrap(in).asReader();
    try
    {
      if (reader.get() != MSG_TYPE_CT_HEARTBEAT)
      {
        // Throw better exception below.
        throw new IllegalArgumentException();
      }

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V7)
      {
        changeNumber = ChangeNumber.valueOf(reader
            .getByteSequence(ChangeNumber.BYTE_ENCODING_LENGTH));
      }
      else
      {
        changeNumber = ChangeNumber.valueOf(reader
            .getString(ChangeNumber.STRING_ENCODING_LENGTH));
        reader.get(); // Read trailing 0 byte.
      }

      if (reader.remaining() > 0)
      {
        // Throw better exception below.
        throw new IllegalArgumentException();
      }
    }
    catch (Exception e)
    {
      // Index out of bounds, bad format, etc.
      throw new DataFormatException("byte[] is not a valid CT_HEARTBEAT msg");
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V7)
    {
      final ByteStringBuilder builder = new ByteStringBuilder(
          ChangeNumber.BYTE_ENCODING_LENGTH + 1 /* type + csn */);
      builder.append(MSG_TYPE_CT_HEARTBEAT);
      changeNumber.toByteString(builder);
      return builder.toByteArray();
    }
    else
    {
      final ByteStringBuilder builder = new ByteStringBuilder(
          ChangeNumber.STRING_ENCODING_LENGTH + 2 /* type + csn str + nul */);
      builder.append(MSG_TYPE_CT_HEARTBEAT);
      builder.append(changeNumber.toString());
      builder.append((byte) 0); // For compatibility with earlier protocol
                                // versions.
      return builder.toByteArray();
    }
  }

}
