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
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.common;

import java.io.Serializable;
import java.util.Date;

import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;

/**
 * Class used to represent Change Sequence Numbers.
 */
public class CSN implements Serializable, Comparable<CSN>
{
  /**
   * The number of bytes used by the byte string representation of a change
   * number.
   *
   * @see #valueOf(ByteSequence)
   * @see #toByteString()
   * @see #toByteString(ByteStringBuilder)
   */
  public static final int BYTE_ENCODING_LENGTH = 14;

  /**
   * The number of characters used by the string representation of a change
   * number.
   *
   * @see #valueOf(String)
   * @see #toString()
   */
  public static final int STRING_ENCODING_LENGTH = 28;

  private static final long serialVersionUID = -8802722277749190740L;
  private final long timeStamp;
  private final int seqnum;
  private final int serverId;

  /**
   * Parses the provided {@link #toString()} representation of a CSN.
   *
   * @param s
   *          The string to be parsed.
   * @return The parsed CSN.
   * @see #toString()
   */
  public static CSN valueOf(String s)
  {
    return new CSN(s);
  }

  /**
   * Decodes the provided {@link #toByteString()} representation of a change
   * number.
   *
   * @param bs
   *          The byte sequence to be parsed.
   * @return The decoded CSN.
   * @see #toByteString()
   */
  public static CSN valueOf(ByteSequence bs)
  {
    ByteSequenceReader reader = bs.asReader();
    long timeStamp = reader.getLong();
    int serverId = reader.getShort() & 0xffff;
    int seqnum = reader.getInt();
    return new CSN(timeStamp, seqnum, serverId);
  }

  /**
   * Create a new {@link CSN} from a String.
   *
   * @param str the string from which to create a {@link CSN}
   */
  public CSN(String str)
  {
    String temp = str.substring(0, 16);
    timeStamp = Long.parseLong(temp, 16);

    temp = str.substring(16, 20);
    serverId = Integer.parseInt(temp, 16);

    temp = str.substring(20, 28);
    seqnum = Integer.parseInt(temp, 16);
  }

  /**
   * Create a new {@link CSN}.
   *
   * @param timeStamp timeStamp for the {@link CSN}
   * @param seqNum sequence number
   * @param serverId identity of server
   */
  public CSN(long timeStamp, int seqNum, int serverId)
  {
    this.serverId = serverId;
    this.timeStamp = timeStamp;
    this.seqnum = seqNum;
  }

  /**
   * Getter for the time.
   * @return the time
   */
  public long getTime()
  {
    return timeStamp;
  }

  /**
   * Get the timestamp associated to this {@link CSN} in seconds.
   * @return timestamp associated to this {@link CSN} in seconds
   */
  public long getTimeSec()
  {
    return timeStamp/1000;
  }

  /**
   * Getter for the sequence number.
   * @return the sequence number
   */
  public int getSeqnum()
  {
    return seqnum;
  }

  /**
   * Getter for the server ID.
   * @return the server ID
   */
  public int getServerId()
  {
    return serverId;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof CSN)
    {
      CSN csn = (CSN) obj;
      return this.seqnum == csn.seqnum &&
          this.serverId == csn.serverId &&
          this.timeStamp == csn.timeStamp;
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return this.seqnum + this.serverId + Long.valueOf(timeStamp).hashCode();
  }

  /**
   * Encodes this CSN as a byte string.
   * <p>
   * NOTE: this representation must not be modified otherwise interop with
   * earlier protocol versions will be broken.
   *
   * @return The encoded representation of this CSN.
   * @see #valueOf(ByteSequence)
   */
  public ByteString toByteString()
  {
    return toByteString(new ByteStringBuilder(BYTE_ENCODING_LENGTH))
        .toByteString();
  }

  /**
   * Encodes this CSN into the provided byte string builder.
   * <p>
   * NOTE: this representation must not be modified otherwise interop with
   * earlier protocol versions will be broken.
   *
   * @param builder
   *          The byte string builder.
   * @return The byte string builder containing the encoded CSN.
   * @see #valueOf(ByteSequence)
   */
  public ByteStringBuilder toByteString(ByteStringBuilder builder)
  {
    return builder.append(timeStamp).append((short) (serverId & 0xffff))
        .append(seqnum);
  }

  /**
   * Convert the {@link CSN} to a printable String.
   * <p>
   * NOTE: this representation must not be modified otherwise interop with
   * earlier protocol versions will be broken.
   *
   * @return the string
   */
  @Override
  public String toString()
  {
    return String.format("%016x%04x%08x", timeStamp, serverId, seqnum);
  }

  /**
   * Convert the {@link CSN} to a printable String with a user friendly
   * format.
   *
   * @return the string
   */
  public String toStringUI()
  {
    Date date = new Date(timeStamp);
    return String.format(
        "%016x%04x%08x (sid=%d,tsd=%s,ts=%d,seqnum=%d)",
        timeStamp, serverId, seqnum,
        serverId, date.toString(), timeStamp, seqnum);
  }

  /**
   * Compares 2 {@link CSN}.
   * @param csn1 the first {@link CSN} to compare
   * @param csn2 the second {@link CSN} to compare
   * @return value 0 if CSN matches, negative if first
   * CSN is smaller, positive otherwise
   */
  public static int compare(CSN csn1, CSN csn2)
  {
    if (csn1 == null)
    {
      if (csn2 == null)
        return 0;
      return -1;
    }
    else if (csn2 == null)
      return 1;
    else if (csn1.timeStamp < csn2.timeStamp)
      return -1;
    else if (csn2.timeStamp < csn1.timeStamp)
      return 1;
    else
    {
      // timestamps are equals compare seqnums
      if (csn1.seqnum < csn2.seqnum)
        return -1;
      else if (csn2.seqnum < csn1.seqnum)
        return 1;
      else
      {
        // timestamp and seqnum are equals compare serverIds
        if (csn1.serverId < csn2.serverId)
          return -1;
        else if (csn2.serverId < csn1.serverId)
          return 1;

        // if we get here {@link CSN} are equals
        return 0;
      }

    }
  }

  /**
   * Computes the difference in number of changes between 2 CSNs. First one is
   * expected to be newer than second one. If this is not the case, 0 will be
   * returned.
   *
   * @param csn1
   *          the first {@link CSN}
   * @param csn2
   *          the second {@link CSN}
   * @return the difference
   */
  public static int diffSeqNum(CSN csn1, CSN csn2)
  {
    if (csn1 == null)
    {
      return 0;
    }
    if (csn2 == null)
    {
      return csn1.getSeqnum();
    }
    if (csn2.newerOrEquals(csn1))
    {
      return 0;
    }

    int seqnum1 = csn1.getSeqnum();
    long time1 = csn1.getTime();
    int seqnum2 = csn2.getSeqnum();
    long time2 = csn2.getTime();

    if (time2 <= time1)
    {
      if (seqnum2 <= seqnum1)
      {
        return seqnum1 - seqnum2;
      }
      return Integer.MAX_VALUE - (seqnum2 - seqnum1) + 1;
    }
    return 0;
  }

  /**
   * check if the current Object is strictly older than {@link CSN}
   * given in parameter.
   * @param csn the {@link CSN} to compare with
   * @return true if strictly older, false if younger or same
   */
  public boolean older(CSN csn)
  {
    return compare(this, csn) < 0;
  }

  /**
   * check if the current Object is older than {@link CSN}
   * given in parameter.
   * @param csn the {@link CSN} to compare with
   * @return true if older or equal, false if younger
   */
  public boolean olderOrEqual(CSN csn)
  {
    return compare(this, csn) <= 0;
  }

  /**
   * Check if the current Object is newer than {@link CSN}.
   * @param csn the {@link CSN} to compare with
   * @return true if newer
   */
  public boolean newerOrEquals(CSN csn)
  {
    return compare(this, csn) >= 0;
  }

  /**
   * Check if the current Object is strictly newer than {@link CSN}.
   * @param csn the {@link CSN} to compare with
   * @return true if strictly newer
   */
  public boolean newer(CSN csn)
  {
    return compare(this, csn) > 0;
  }

  /**
   * Compares this object with the specified object for order.
   * @param csn the {@link CSN} to compare with.
   * @return a negative integer, zero, or a positive integer as this object
   *         is less than, equal to, or greater than the specified object.
   */
  @Override
  public int compareTo(CSN csn)
  {
    return compare(this, csn);
  }
}
