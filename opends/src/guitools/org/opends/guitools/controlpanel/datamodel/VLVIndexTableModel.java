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

package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;

import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The table model for the VLV indexes.  This is the table model used by the
 * table that appears on the right side of the Manage Index dialog when the user
 * clicks on the node "VLV Indexes" and it gives a global view of the VLV
 * indexes defined on a given backend.
 *
 */
public class VLVIndexTableModel extends AbstractIndexTableModel
{
  private static final long serialVersionUID = 897379916278218775L;

  /**
   * {@inheritDoc}
   */
  protected String[] getColumnNames()
  {
    return new String[] {
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_NAME.get()),
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_BASE_DN.get(), 30),
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_SCOPE.get()),
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_FILTER.get()),
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_SORT_ORDER.get(), 30),
        getHeader(INFO_CTRL_PANEL_VLV_INDEXES_HEADER_REQUIRES_REBUILD.get(), 30)
    };
  }

  /**
   * Comparable implementation.
   * @param index1 the first VLV index descriptor to compare.
   * @param index2 the second VLV index descriptor to compare.
   * @return 1 if according to the sorting options set by the user the first
   * index descriptor must be put before the second descriptor, 0 if they
   * are equivalent in terms of sorting and -1 if the second descriptor must
   * be put before the first descriptor.
   */
  public int compare(AbstractIndexDescriptor index1,
      AbstractIndexDescriptor index2)
  {
    int result;
    VLVIndexDescriptor i1 = (VLVIndexDescriptor)index1;
    VLVIndexDescriptor i2 = (VLVIndexDescriptor)index2;

    int[] possibleResults = {compareNames(i1, i2), compareBaseDNs(i1, i2),
        compareScopes(i1, i2), compareFilters(i1, i2),
        compareSortOrders(i1, i2), compareRebuildRequired(i1, i2)};
    result = possibleResults[sortColumn];
    if (result == 0)
    {
      for (int i : possibleResults)
      {
        if (i != 0)
        {
          result = i;
          break;
        }
      }
    }
    if (!sortAscending)
    {
      result = -result;
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  protected String[] getLine(AbstractIndexDescriptor index)
  {
    VLVIndexDescriptor i = (VLVIndexDescriptor)index;
    return new String[] {
        i.getName(), getDNValue(i), getScopeDisplayValue(i), i.getFilter(),
        getSortOrderDisplayValue(i), getRebuildRequiredString(i).toString()
    };
  }

  /**
   * Returns the VLV index DN value in String format.
   * @param i the VLV index.
   * @return the VLV index DN value in String format.
   */
  private String getDNValue(VLVIndexDescriptor i)
  {
    return Utilities.unescapeUtf8(i.getBaseDN().toString());
  }

  /**
   * Returns the VLV index scope value in String format.  This is the value used
   * to make String comparisons.
   * @param i the VLV index.
   * @return the VLV index scope value in String format.
   */
  private String getScopeStringValue(VLVIndexDescriptor i)
  {
    String s;
    switch (i.getScope())
    {
    case BASE_OBJECT:
      s = "Base Object";
      break;
    case SINGLE_LEVEL:
      s = "Single Level";
      break;
    case WHOLE_SUBTREE:
      s = "Whole Subtree";
      break;
    case SUBORDINATE_SUBTREE:
      s = "Subordinate Subtree";
      break;
    default:
      throw new IllegalStateException("Unknow scope: "+i.getScope());
    }
    return s;
  }

  /**
   * Returns the VLV index scope display value in String format.  This is the
   * value to be stored in the table model.
   * @param i the VLV index.
   * @return the VLV index DN value in String format.
   */
  private String getScopeDisplayValue(VLVIndexDescriptor i)
  {
    return "<html>"+getScopeStringValue(i);
  }

  /**
   * Returns the VLV index sort order value in String format.  This is the value
   * used to make String comparisons.
   * @param i the VLV index.
   * @return the VLV index DN value in String format.
   */
  private String getSortOrderStringValue(VLVIndexDescriptor i)
  {
    StringBuilder sb = new StringBuilder();
    for (VLVSortOrder sortOrder : i.getSortOrder())
    {
      if (sb.length() > 0)
      {
        sb.append(", ");
      }
      sb.append(sortOrder.getAttributeName());
      if (sortOrder.isAscending())
      {
        sb.append(" (ascending)");
      }
      else
      {
        sb.append(" (descending)");
      }
    }
    if (sb.length() == 0)
    {
      sb.append(INFO_NOT_APPLICABLE_LABEL.get().toString());
    }
    return sb.toString();
  }

  /**
   * Returns the VLV index sort order value in String format.  This is the value
   * stored in the table model.
   * @param i the VLV index.
   * @return the VLV index sort order value in String format.
   */
  private String getSortOrderDisplayValue(VLVIndexDescriptor i)
  {
    return "<html>"+getSortOrderStringValue(i).replaceAll(", ",",<br>");
  }

  //Comparison methods.

  private int compareBaseDNs(VLVIndexDescriptor i1, VLVIndexDescriptor i2)
  {
    return getDNValue(i1).compareTo(getDNValue(i2));
  }

  private int compareScopes(VLVIndexDescriptor i1, VLVIndexDescriptor i2)
  {
    return getScopeStringValue(i1).compareTo(getScopeStringValue(i2));
  }

  private int compareFilters(VLVIndexDescriptor i1, VLVIndexDescriptor i2)
  {
    return i1.getFilter().compareTo(i2.getFilter());
  }

  private int compareSortOrders(VLVIndexDescriptor i1, VLVIndexDescriptor i2)
  {
    return getSortOrderStringValue(i1).compareTo(getSortOrderStringValue(i2));
  }
}
