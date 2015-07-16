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
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;

import com.sleepycat.je.DatabaseComparator;
import com.sleepycat.je.DatabaseException;

/**
 * This class is used to compare the keys used in a VLV index. Each key is
 * made up the sort values and the entry ID of the largest entry in the sorted
 * set stored in the data for the key.
 */
public class VLVKeyComparator implements DatabaseComparator
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  static final long serialVersionUID = 1585167927344130604L;

  /** Matching rules are not serializable. */
  private transient MatchingRule[] orderingRules;

  /**
   * Only oids of matching rules are recorded for serialization. Oids allow to
   * retrieve matching rules after deserialization, through
   * {@code initialize(ClassLoader)} method.
   */
  private String[] orderingRuleOids;

  private boolean[] ascending;

  /**
   * Construct a new VLV Key Comparator object.
   *
   * @param orderingRules The array of ordering rules to use when comparing
   *                      the decoded values in the key.
   * @param ascending     The array of booleans indicating the ordering for
   *                      each value.
   */
  public VLVKeyComparator(MatchingRule[] orderingRules, boolean[] ascending)
  {
    this.orderingRules = orderingRules;
    this.orderingRuleOids = new String[orderingRules.length];
    for (int i = 0; i < orderingRules.length; i++)
    {
      orderingRuleOids[i] = orderingRules[i].getOID();
    }
    this.ascending = ascending;
  }

  /**
   * Compares the contents of the provided byte arrays to determine their
   * relative order. A key in the VLV index contains the sorted attribute values
   * in order followed by the 8 byte entry ID. A attribute value of length 0
   * means that value is null and the attribute type was not part of the entry.
   * A null value is always considered greater then a non null value. If all
   * attribute values are the same, the entry ID will be used to determine the
   * ordering.
   *
   * When comparing partial keys (ie. keys with only the first attribute value
   * encoded for evaluating VLV assertion value offsets or keys with no entry
   * IDs), only information available in both byte keys will be used to
   * determine the ordering. If all available information is the same, 0 will
   * be returned.
   *
   * @param  b1  The first byte array to use in the comparison.
   * @param  b2  The second byte array to use in the comparison.
   *
   * @return  A negative integer if <CODE>b1</CODE> should come before
   *          <CODE>b2</CODE> in ascending order, a positive integer if
   *          <CODE>b1</CODE> should come after <CODE>b2</CODE> in ascending
   *          order, or zero if there is no difference between the values with
   *          regard to ordering.
   */
  @Override
  public int compare(byte[] b1, byte[] b2)
  {
    // A 0 length byte array is a special key used for the unbound max
    // sort values set. It always comes after a non length byte array.
    if(b1.length == 0)
    {
      if(b2.length == 0)
      {
        return 0;
      }
      else
      {
        return 1;
      }
    }
    else if(b2.length == 0)
    {
      return -1;
    }

    int b1Pos = 0;
    int b2Pos = 0;
    for (int j=0;
         j < orderingRules.length && b1Pos < b1.length && b2Pos < b2.length;
         j++)
    {
      int b1Length = b1[b1Pos] & 0x7F;
      if (b1[b1Pos++] != b1Length)
      {
        int b1NumLengthBytes = b1Length;
        b1Length = 0;
        for (int k=0; k < b1NumLengthBytes; k++, b1Pos++)
        {
          b1Length = (b1Length << 8) |
              (b1[b1Pos] & 0xFF);
        }
      }

      int b2Length = b2[b2Pos] & 0x7F;
      if (b2[b2Pos++] != b2Length)
      {
        int b2NumLengthBytes = b2Length;
        b2Length = 0;
        for (int k=0; k < b2NumLengthBytes; k++, b2Pos++)
        {
          b2Length = (b2Length << 8) |
              (b2[b2Pos] & 0xFF);
        }
      }

      byte[] b1Bytes;
      byte[] b2Bytes;
      if(b1Length > 0)
      {
        b1Bytes = new byte[b1Length];
        System.arraycopy(b1, b1Pos, b1Bytes, 0, b1Length);
        b1Pos += b1Length;
      }
      else
      {
        b1Bytes = null;
      }

      if(b2Length > 0)
      {
        b2Bytes = new byte[b2Length];
        System.arraycopy(b2, b2Pos, b2Bytes, 0, b2Length);
        b2Pos += b2Length;
      }
      else
      {
        b2Bytes = null;
      }

      // A null value will always come after a non-null value.
      if (b1Bytes == null)
      {
        if (b2Bytes == null)
        {
          continue;
        }
        else
        {
          return 1;
        }
      }
      else if (b2Bytes == null)
      {
        return -1;
      }

      final ByteString val1 = ByteString.valueOf(b1Bytes);
      final ByteString val2 = ByteString.valueOf(b2Bytes);
      final int result = ascending[j] ? val1.compareTo(val2) : val2.compareTo(val1);
      if(result != 0)
      {
        return result;
      }
    }

    // If we've gotten here, then we can't tell a difference between the sets
    // of available values, so sort based on entry ID if its in the key.

    if(b1Pos + 8 <= b1.length && b2Pos + 8 <= b2.length)
    {
      long b1ID = JebFormat.toLong(b1, b1Pos, b1Pos + 8);
      long b2ID = JebFormat.toLong(b2, b2Pos, b2Pos + 8);
      return compare(b1ID, b2ID);
    }

    // If we've gotten here, then we can't tell the difference between the sets
    // of available values and entry IDs are not all available, so just return 0
    return 0;
  }

  /**
   * Compares the contents in the provided values set with the given values to
   * determine their relative order. A null value is always considered greater
   * then a non null value. If all attribute values are the same, the entry ID
   * will be used to determine the ordering.
   *
   * If the given attribute values array does not contain all the values in the
   * sort order, any missing values will be considered as a unknown or
   * wildcard value instead of a non existent value. When comparing partial
   * information, only values available in both the values set and the
   * given values will be used to determine the ordering. If all available
   * information is the same, 0 will be returned.
   *
   * @param  set  The sort values set to containing the values.
   * @param  index The index of the values in the set.
   * @param  entryID The entry ID to use in the comparison.
   * @param  values The values to use in the comparison.
   * @return  A negative integer if the values in the set should come before
   *          the given values in ascending order, a positive integer if
   *          the values in the set should come after the given values in
   *          ascending order, or zero if there is no difference between the
   *          values with regard to ordering.
   * @throws DatabaseException If an error occurs during an operation on a
   * JE database.
   * @throws DirectoryException  If an error occurs while trying to
   *                              normalize the value (e.g., if it is
   *                              not acceptable for use with the
   *                              associated equality matching rule).
   */
  public int compare(SortValuesSet set, int index, long entryID,
      ByteString[] values) throws DatabaseException, DirectoryException
  {
    for (int j=0; j < orderingRules.length; j++)
    {
      if(j >= values.length)
      {
        break;
      }

      ByteString b1Bytes = set.getValue(index * orderingRules.length + j);
      ByteString b2Bytes = null;

      if(values[j] != null)
      {
        try
        {
          b2Bytes = orderingRules[j].normalizeAttributeValue(values[j]);
        }
        catch (DecodeException e)
        {
          throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, e.getMessageObject(), e);
        }
      }

      // A null value will always come after a non-null value.
      if (b1Bytes == null)
      {
        if (b2Bytes == null)
        {
          continue;
        }
        else
        {
          return 1;
        }
      }
      else if (b2Bytes == null)
      {
        return -1;
      }

      final int result = ascending[j] ? b1Bytes.compareTo(b2Bytes) : b2Bytes.compareTo(b1Bytes);
      if(result != 0)
      {
        return result;
      }
    }

    if(entryID != -1)
    {
      // If we've gotten here, then we can't tell a difference between the sets
      // of values, so sort based on entry ID.
      return compare(set.getEntryIDs()[index], entryID);
    }

    // If we've gotten here, then we can't tell the difference between the sets
    // of available values and the entry ID is not available. Just return 0.
    return 0;
  }

  private int compare(long l1, long l2)
  {
    final long difference = l1 - l2;
    if (difference < 0)
    {
      return -1;
    }
    else if (difference > 0)
    {
      return 1;
    }
    else
    {
      return 0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initialize(ClassLoader loader)
  {
    if (orderingRules == null)
    {
      orderingRules = new MatchingRule[orderingRuleOids.length];
      for (int i = 0; i < orderingRuleOids.length; i++)
      {
        orderingRules[i] = DirectoryServer.getSchema().getMatchingRule(orderingRuleOids[i]);
      }
    }
  }
}
