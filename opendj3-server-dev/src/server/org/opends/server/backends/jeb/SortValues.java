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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Entry;
import org.opends.server.types.SortKey;
import org.opends.server.types.SortOrder;

/**
 * This class defines a data structure that holds a set of attribute values that
 * are associated with a sort order for a given entry.  Any or all of the
 * attribute values may be {@code null} if the entry does not include any values
 * for the attribute type targeted by the corresponding sort key.
 * <BR><BR>
 * This class implements the {@code Comparable} interface and may therefore be
 * used to order the elements in components like {@code TreeMap} and
 * {@code TreeSet}.
 * <p>
 * FIXME: replace with the SDK's SortKey?
 */
public class SortValues
       implements Comparable<SortValues>
{
  /** The set of sort keys (attribute values) in this sort order. */
  private ByteString[] values;
  /**
   * The types of sort keys.
   *
   * @see #values
   */
  private AttributeType[] types;

  /** The entry ID for the entry associated with this sort values. */
  private EntryID entryID;

  /** The sort order for this set of sort values. */
  private SortOrder sortOrder;



  /**
   * Creates a new sort values object with the provided information.
   *
   * @param entryID    The entry ID for the entry associated with this set of
   *                   values.
   * @param values     The attribute values for this sort values.
   * @param sortOrder  The sort order to use to obtain the necessary values.
   */
  public SortValues(EntryID entryID, ByteString[] values,
                    SortOrder sortOrder)
  {
    this.entryID = entryID;
    this.sortOrder = sortOrder;
    this.values = values;

    final SortKey[] sortKeys = sortOrder.getSortKeys();
    this.types = new AttributeType[sortKeys.length];
    for (int i = 0; i < sortKeys.length; i++)
    {
      types[i] = sortKeys[i].getAttributeType();
    }
  }

  /**
   * Creates a new sort values object with the provided information.
   *
   * @param  entryID    The entry ID for the entry associated with this set of
   *                    values.
   * @param  entry      The entry containing the values to extract and use when
   *                    sorting.
   * @param  sortOrder  The sort order to use to obtain the necessary values.
   */
  public SortValues(EntryID entryID, Entry entry, SortOrder sortOrder)
  {
    this.entryID   = entryID;
    this.sortOrder = sortOrder;

    SortKey[] sortKeys = sortOrder.getSortKeys();
    this.values = new ByteString[sortKeys.length];
    this.types = new AttributeType[sortKeys.length];
    for (int i=0; i < sortKeys.length; i++)
    {
      SortKey sortKey = sortKeys[i];
      types[i] = sortKey.getAttributeType();
      List<Attribute> attrList = entry.getAttribute(types[i]);
      if (attrList != null)
      {
        values[i] = findBestMatchingValue(sortKey, attrList);
      }
    }
  }

  /**
   * Finds the best matching attribute value for the provided sort key in the
   * provided attribute list.
   * <p>
   * There may be multiple versions of this attribute in the target entry (e.g.,
   * with different sets of options), and it may also be a multivalued
   * attribute. In that case, we need to find the value that is the best match
   * for the corresponding sort key (i.e., for sorting in ascending order, we
   * want to find the lowest value; for sorting in descending order, we want to
   * find the highest value). This is handled by the SortKey.compareValues
   * method.
   */
  private ByteString findBestMatchingValue(SortKey sortKey, List<Attribute> attrList)
  {
    ByteString sortValue = null;
    for (Attribute a : attrList)
    {
      for (ByteString v : a)
      {
        if (sortValue == null || sortKey.compareValues(v, sortValue) < 0)
        {
          sortValue = v;
        }
      }
    }
    return sortValue;
  }

  /**
   * Compares this set of sort values with the provided set of values to
   * determine their relative order in a sorted list.
   *
   * @param  sortValues  The set of values to compare against this sort values.
   *                     It must also have the same sort order as this set of
   *                     values.
   *
   * @return  A negative value if this sort values object should come before the
   *          provided values in a sorted list, a positive value if this sort
   *          values object should come after the provided values in a sorted
   *          list, or zero if there is no significant difference in their
   *          relative order.
   */
  @Override
  public int compareTo(SortValues sortValues)
  {
    SortKey[] sortKeys = sortOrder.getSortKeys();

    for (int i=0; i < values.length; i++)
    {
      int compareValue = sortKeys[i].compareValues(values[i], sortValues.values[i]);
      if (compareValue != 0)
      {
        return compareValue;
      }
    }

    // If we've gotten here, then we can't tell a difference between the sets of
    // sort values, so sort based on entry ID.
    return entryID.compareTo(sortValues.entryID);
  }

  /**
   * Compares the first element in this set of sort values with the provided
   * assertion value to determine whether the assertion value is greater than or
   * equal to the initial sort value.  This is used during VLV processing to
   * find the offset by assertion value.
   *
   * @param  assertionValue  The assertion value to compare against the first
   *                         sort value.
   *
   * @return  A negative value if the provided assertion value should come
   *          before the first sort value, zero if the provided assertion value
   *          is equal to the first sort value, or a positive value if the
   *          provided assertion value should come after the first sort value.
   */
  public int compareTo(ByteString assertionValue)
  {
    SortKey sortKey = sortOrder.getSortKeys()[0];
    return sortKey.compareValues(values[0], assertionValue);
  }

  /**
   * Retrieves a string representation of this sort values object.
   *
   * @return  A string representation of this sort values object.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Appends a string representation of this sort values object to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SortValues(");

    SortKey[] sortKeys = sortOrder.getSortKeys();
    for (int i=0; i < sortKeys.length; i++)
    {
      if (i > 0)
      {
        buffer.append(",");
      }

      buffer.append(sortKeys[i].ascending() ? "+" : "-");

      buffer.append(sortKeys[i].getAttributeType().getNameOrOID());
      buffer.append("=");
      buffer.append(values[i]);
    }

    buffer.append(", id=");
    buffer.append(entryID);
    buffer.append(")");
  }

  /**
   * Retrieve the attribute values in this sort values.
   *
   * @return The array of attribute values for this sort values.
   */
  public ByteString[] getValues()
  {
    return values;
  }

  /**
   * Retrieve the type of the attribute values in this sort values.
   *
   * @return The array of type of the attribute values for this sort values.
   */
  public AttributeType[] getTypes()
  {
    return types;
  }

  /**
   * Retrieve the entry ID in this sort values.
   *
   * @return The entry ID for this sort values.
   */
  public long getEntryID()
  {
    return entryID.longValue();
  }
}
