/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import static org.opends.server.util.StaticUtils.*;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;

/**
 * Record for import composed of a byte sequence key and an indexID.
 */
final class ImportRecord implements Comparable<ImportRecord>
{

  /**
   * The record overhead. In addition to entryID, key length and key bytes, the record overhead
   * includes the INS/DEL bit + indexID
   */
  static final int REC_OVERHEAD = 1 + INT_SIZE;

  static ImportRecord fromBufferAndPosition(byte[] buffer, int position)
  {
    return fromBufferAndOffset(buffer, readOffset(buffer, position));
  }

  static ImportRecord fromBufferAndOffset(byte[] buffer, int offSet)
  {
    int indexID = readIndexIDFromOffset(buffer, offSet);
    offSet += REC_OVERHEAD + LONG_SIZE;
    int keyLength = readInt(buffer, offSet);
    ByteString key = ByteString.wrap(buffer, INT_SIZE + offSet, keyLength);
    return new ImportRecord(key, indexID);
  }

  static ImportRecord from(ByteSequence key, int indexID)
  {
    return new ImportRecord(key, indexID);
  }

  private static int readOffset(byte[] buffer, int position)
  {
    return readInt(buffer, position * INT_SIZE);
  }

  private static int readIndexIDFromOffset(byte[] buffer, int offset)
  {
    return readInt(buffer, offset + 1);
  }

  private static int readInt(byte[] buffer, int index)
  {
    int answer = 0;
    for (int i = 0; i < INT_SIZE; i++)
    {
      byte b = buffer[index + i];
      answer <<= 8;
      answer |= b & 0xff;
    }
    return answer;
  }

  private final ByteSequence key;
  /**
   * The indexID, computed by calling {@link System#identityHashCode(Object)}
   * on the in-memory {@link Index} object.
   */
  private final int indexID;

  private ImportRecord(ByteSequence key, int indexID)
  {
    this.key = key;
    this.indexID = indexID;
  }

  public int getIndexID()
  {
    return indexID;
  }

  public ByteSequence getKey()
  {
    return key;
  }

  @Override
  public int compareTo(ImportRecord o)
  {
    if (o == null)
    {
      return -1;
    }
    int cmp = key.compareTo(o.getKey());
    if (cmp == 0)
    {
      return indexID - o.getIndexID();
    }
    return cmp;
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (o instanceof ImportRecord)
    {
      ImportRecord other = (ImportRecord) o;
      return indexID == other.getIndexID() && key.equals(other.getKey());
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + indexID;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ImportRecord(key=" + key + ", indexID=" + indexID + ")";
  }
}
