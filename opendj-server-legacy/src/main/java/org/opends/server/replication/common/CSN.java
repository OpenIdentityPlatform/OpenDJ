/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.common;

import java.io.Serializable;
import java.util.Date;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;

/**
 * Class used to represent Change Sequence Numbers.
 *
 * @see <a href="http://tools.ietf.org/html/draft-ietf-ldup-infomod-08"
 * >Inspiration for this class comes from LDAPChangeSequenceNumber</a>
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

  /** The maximum possible value for a CSN. */
  public static final CSN MAX_CSN_VALUE = new CSN(Long.MAX_VALUE, Integer.MAX_VALUE, Short.MAX_VALUE);

  private static final long serialVersionUID = -8802722277749190740L;
  private final long timeStamp;
  /**
   * The sequence number is set to zero at the start of each millisecond, and
   * incremented by one for each update operation that occurs within that
   * millisecond. It allows to distinguish changes that have been done in the
   * same millisecond.
   */
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
    long timeStamp = reader.readLong();
    int serverId = reader.readShort() & 0xffff;
    int seqnum = reader.readInt();
    return new CSN(timeStamp, seqnum, serverId);
  }

  /**
   * Create a new {@link CSN} from a String.
   *
   * @param str
   *          the string from which to create a {@link CSN}
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
   * @param timeStamp
   *          timeStamp for the {@link CSN}
   * @param seqNum
   *          sequence number
   * @param serverId
   *          identity of server
   */
  public CSN(long timeStamp, int seqNum, int serverId)
  {
    this.serverId = serverId;
    this.timeStamp = timeStamp;
    this.seqnum = seqNum;
  }

  /**
   * Getter for the time.
   *
   * @return the time
   */
  public long getTime()
  {
    return timeStamp;
  }

  /**
   * Get the timestamp associated to this {@link CSN} in seconds.
   *
   * @return timestamp associated to this {@link CSN} in seconds
   */
  public long getTimeSec()
  {
    return timeStamp / 1000;
  }

  /**
   * Getter for the sequence number.
   *
   * @return the sequence number
   */
  public int getSeqnum()
  {
    return seqnum;
  }

  /**
   * Getter for the server ID.
   *
   * @return the server ID
   */
  public int getServerId()
  {
    return serverId;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof CSN)
    {
      final CSN csn = (CSN) obj;
      return this.seqnum == csn.seqnum && this.serverId == csn.serverId
          && this.timeStamp == csn.timeStamp;
    }
    else
    {
      return false;
    }
  }

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
    final ByteStringBuilder builder = new ByteStringBuilder(BYTE_ENCODING_LENGTH);
    toByteString(builder);
    return builder.toByteString();
  }

  /**
   * Encodes this CSN into the provided byte string builder.
   * <p>
   * NOTE: this representation must not be modified otherwise interop with
   * earlier protocol versions will be broken.
   *
   * @param builder
   *          The byte string builder.
   * @see #valueOf(ByteSequence)
   */
  public void toByteString(ByteStringBuilder builder)
  {
    builder.appendLong(timeStamp).appendShort(serverId & 0xffff).appendInt(seqnum);
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
    final StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Appends the text representation of this {@link CSN} into the provided StringBuilder.
   * <p>
   * NOTE: this representation must not be modified otherwise interop with
   * earlier protocol versions will be broken.
   *
   * @param buffer the StringBuilder where to output the CSN text representation
   */
  void toString(final StringBuilder buffer)
  {
    leftPadWithZeros(buffer, 16, Long.toHexString(timeStamp));
    leftPadWithZeros(buffer, 4, Integer.toHexString(serverId));
    leftPadWithZeros(buffer, 8, Integer.toHexString(seqnum));
  }

  private void leftPadWithZeros(StringBuilder buffer, int nbChars, String toAppend)
  {
    final int padding = nbChars - toAppend.length();
    for (int i = 0; i < padding; i++)
    {
      buffer.append('0');
    }
    buffer.append(toAppend);
  }

  /**
   * Convert the {@link CSN} to a printable String with a user friendly format.
   *
   * @return the string
   */
  public String toStringUI()
  {
    final StringBuilder buffer = new StringBuilder();
    toStringUI(buffer);
    return buffer.toString();
  }

  private void toStringUI(final StringBuilder buffer)
  {
    toString(buffer);
    buffer.append(" (sid=").append(serverId)
          .append(",tsd=").append(new Date(timeStamp))
          .append(",ts=").append(timeStamp)
          .append(",seqnum=").append(seqnum)
          .append(")");
  }

  /**
   * Compares this CSN with the provided CSN for order and returns a negative
   * number if {@code csn1} is older than {@code csn2}, zero if they have the
   * same age, or a positive number if {@code csn1} is newer than {@code csn2}.
   *
   * @param csn1
   *          The first CSN to be compared, which may be {@code null}.
   * @param csn2
   *          The second CSN to be compared, which may be {@code null}.
   * @return A negative number if {@code csn1} is older than {@code csn2}, zero
   *         if they have the same age, or a positive number if {@code csn1} is
   *         newer than {@code csn2}.
   */
  public static int compare(CSN csn1, CSN csn2)
  {
    if (csn1 == null)
    {
      return csn2 == null ? 0 : -1;
    }
    else if (csn2 == null)
    {
      return 1;
    }
    else if (csn1.timeStamp != csn2.timeStamp)
    {
      return csn1.timeStamp < csn2.timeStamp ? -1 : 1;
    }
    else if (csn1.seqnum != csn2.seqnum)
    {
      return csn1.seqnum - csn2.seqnum;
    }
    else
    {
      return csn1.serverId - csn2.serverId;
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
    if (csn2.isNewerThanOrEqualTo(csn1))
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
   * Returns {@code true} if this CSN is older than the provided CSN.
   *
   * @param csn
   *          The CSN to be compared.
   * @return {@code true} if this CSN is older than the provided CSN.
   */
  public boolean isOlderThan(CSN csn)
  {
    return compare(this, csn) < 0;
  }

  /**
   * Returns {@code true} if this CSN is older than or equal to the provided
   * CSN.
   *
   * @param csn
   *          The CSN to be compared.
   * @return {@code true} if this CSN is older than or equal to the provided
   *         CSN.
   */
  public boolean isOlderThanOrEqualTo(CSN csn)
  {
    return compare(this, csn) <= 0;
  }

  /**
   * Returns {@code true} if this CSN is newer than or equal to the provided
   * CSN.
   *
   * @param csn
   *          The CSN to be compared.
   * @return {@code true} if this CSN is newer than or equal to the provided
   *         CSN.
   */
  public boolean isNewerThanOrEqualTo(CSN csn)
  {
    return compare(this, csn) >= 0;
  }

  /**
   * Returns {@code true} if this CSN is newer than the provided CSN.
   *
   * @param csn
   *          The CSN to be compared.
   * @return {@code true} if this CSN is newer than the provided CSN.
   */
  public boolean isNewerThan(CSN csn)
  {
    return compare(this, csn) > 0;
  }

  /**
   * Compares this CSN with the provided CSN for order and returns a negative
   * number if this CSN is older than {@code csn}, zero if they have the same
   * age, or a positive number if this CSN is newer than {@code csn}.
   *
   * @param csn
   *          The CSN to be compared.
   * @return A negative number if this CSN is older than {@code csn}, zero if
   *         they have the same age, or a positive number if this CSN is newer
   *         than {@code csn}.
   */
  @Override
  public int compareTo(CSN csn)
  {
    return compare(this, csn);
  }
}
