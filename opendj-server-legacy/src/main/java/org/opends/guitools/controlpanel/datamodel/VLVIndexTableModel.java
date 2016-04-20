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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.datamodel;

import static org.opends.messages.AdminToolMessages.*;

import org.forgerock.opendj.ldap.SearchScope;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The table model for the VLV indexes.  This is the table model used by the
 * table that appears on the right side of the Manage Index dialog when the user
 * clicks on the node "VLV Indexes" and it gives a global view of the VLV
 * indexes defined on a given backend.
 */
public class VLVIndexTableModel extends AbstractIndexTableModel
{
  private static final long serialVersionUID = 897379916278218775L;

  @Override
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
  @Override
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

  @Override
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
   * Returns the VLV index scope value in String format. This is the value used
   * to make String comparisons.
   *
   * @param scope
   *          the VLV index.
   * @return the VLV index scope value in String format.
   */
  private String toUIString(final SearchScope scope)
  {
    switch (scope.asEnum())
    {
    case BASE_OBJECT:
      return INFO_CTRL_PANEL_VLV_INDEX_BASE_OBJECT_LABEL.get().toString();
    case SINGLE_LEVEL:
      return INFO_CTRL_PANEL_VLV_INDEX_SINGLE_LEVEL_LABEL.get().toString();
    case SUBORDINATES:
      return INFO_CTRL_PANEL_VLV_INDEX_SUBORDINATE_SUBTREE_LABEL.get().toString();
    case WHOLE_SUBTREE:
      return INFO_CTRL_PANEL_VLV_INDEX_WHOLE_SUBTREE_LABEL.get().toString();
    default:
      throw new IllegalArgumentException("Unknown scope: " + scope);
    }
  }

  /**
   * Returns the VLV index scope display value in String format. This is the
   * value to be stored in the table model.
   *
   * @param i
   *          the VLV index.
   * @return the VLV index DN value in String format.
   */
  private String getScopeDisplayValue(final VLVIndexDescriptor i)
  {
    return "<html>" + toUIString(i.getScope());
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
      sb.append(INFO_NOT_APPLICABLE_LABEL.get());
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
    return toUIString(i1.getScope()).compareTo(toUIString(i2.getScope()));
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
