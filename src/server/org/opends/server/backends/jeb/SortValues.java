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
package org.opends.server.backends.jeb;



import java.util.List;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
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
 */
public class SortValues
       implements Comparable<SortValues>
{
  // The set of sort keys in this sort order.
  private AttributeValue[] values;

  // The entry ID for the entry associated with this sort values.
  private EntryID entryID;

  // The sort order for this set of sort values.
  private SortOrder sortOrder;



  /**
   * Creates a new sort values object with the provided information.
   *
   * @param entryID    The entry ID for the entry associated with this set of
   *                   values.
   * @param values     The attribute values for this sort values.
   * @param sortOrder  The sort order to use to obtain the necessary values.
   */
  public SortValues(EntryID entryID, AttributeValue[] values,
                    SortOrder sortOrder)
  {
    this.entryID = entryID;
    this.sortOrder = sortOrder;
    this.values = values;
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
    values = new AttributeValue[sortKeys.length];
    for (int i=0; i < sortKeys.length; i++)
    {
      SortKey sortKey = sortKeys[i];
      AttributeType attrType = sortKey.getAttributeType();
      List<Attribute> attrList = entry.getAttribute(attrType);
      if (attrList != null)
      {
        AttributeValue sortValue = null;

        // There may be multiple versions of this attribute in the target entry
        // (e.g., with different sets of options), and it may also be a
        // multivalued attribute.  In that case, we need to find the value that
        // is the best match for the corresponding sort key (i.e., for sorting
        // in ascending order, we want to find the lowest value; for sorting in
        // descending order, we want to find the highest value).  This is
        // handled by the SortKey.compareValues method.
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a)
          {
            if (sortValue == null)
            {
              sortValue = v;
            }
            else if (sortKey.compareValues(v, sortValue) < 0)
            {
              sortValue = v;
            }
          }
        }

        values[i] = sortValue;
      }
    }
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
  public int compareTo(SortValues sortValues)
  {
    SortKey[] sortKeys = sortOrder.getSortKeys();

    for (int i=0; i < values.length; i++)
    {
      int compareValue = sortKeys[i].compareValues(values[i],
                                          sortValues.values[i]);
      if (compareValue != 0)
      {
        return compareValue;
      }
    }

    // If we've gotten here, then we can't tell a difference between the sets of
    // sort values, so sort based on entry ID.
    long idDifference = (entryID.longValue() - sortValues.entryID.longValue());
    if (idDifference < 0)
    {
      return -1;
    }
    else if (idDifference > 0)
    {
      return 1;
    }
    else
    {
      return 0;
    }
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
  public int compareTo(AttributeValue assertionValue)
  {
    SortKey sortKey = sortOrder.getSortKeys()[0];
    return sortKey.compareValues(values[0], assertionValue);
  }



  /**
   * Retrieves a string representation of this sort values object.
   *
   * @return  A string representation of this sort values object.
   */
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

      if (sortKeys[i].ascending())
      {
        buffer.append("+");
      }
      else
      {
        buffer.append("-");
      }

      buffer.append(sortKeys[i].getAttributeType().getNameOrOID());
      buffer.append("=");
      if (values[i] == null)
      {
        buffer.append("null");
      }
      else
      {
        buffer.append(values[i].getValue().toString());
      }
    }

    buffer.append(", id=");
    buffer.append(entryID.toString());
    buffer.append(")");
  }

  /**
   * Retrieve the attribute values in this sort values.
   *
   * @return The array of attribute values for this sort values.
   */
  public AttributeValue[] getValues()
  {
    return values;
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


