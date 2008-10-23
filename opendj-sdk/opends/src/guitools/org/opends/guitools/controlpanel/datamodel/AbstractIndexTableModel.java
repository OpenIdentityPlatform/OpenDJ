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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.opends.messages.AdminToolMessages;
import org.opends.messages.Message;

/**
 * Table Model used to store information about indexes.  It is used basically
 * by the tables that appear on the right side of the 'Manage Indexes...'
 * dialog when the user clicks on 'Indexes' or 'VLV Indexes'.
 *
 */
public abstract class AbstractIndexTableModel extends SortableTableModel
implements Comparator<AbstractIndexDescriptor>
{
  private Set<AbstractIndexDescriptor> data =
    new HashSet<AbstractIndexDescriptor>();
  private ArrayList<String[]> dataArray =
    new ArrayList<String[]>();
  private ArrayList<AbstractIndexDescriptor> indexArray =
      new ArrayList<AbstractIndexDescriptor>();
  private final String[] COLUMN_NAMES = getColumnNames();
  /**
   * The sort column of the table.
   */
  protected int sortColumn = 0;
  /**
   * Whether the sorting is ascending or descending.
   */
  protected boolean sortAscending = true;
  private ControlPanelInfo info;


  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   * @param info the control panel info.
   */
  public void setData(Set<AbstractIndexDescriptor> newData,
      ControlPanelInfo info)
  {
    this.info = info;
    if (!newData.equals(data))
    {
      data.clear();
      data.addAll(newData);
      updateDataArray();
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  public void forceResort()
  {
    updateDataArray();
    fireTableDataChanged();
  }



  /**
   * {@inheritDoc}
   */
  public int getColumnCount()
  {
    return COLUMN_NAMES.length;
  }

  /**
   * {@inheritDoc}
   */
  public int getRowCount()
  {
    return dataArray.size();
  }

  /**
   * {@inheritDoc}
   */
  public Object getValueAt(int row, int col)
  {
    return dataArray.get(row)[col];
  }

  /**
   * {@inheritDoc}
   */
  public String getColumnName(int col) {
    return COLUMN_NAMES[col];
  }


  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  public int getSortColumn()
  {
    return sortColumn;
  }

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  public void setSortColumn(int sortColumn)
  {
    this.sortColumn = sortColumn;
  }

  /**
   * Returns the index in the specified row.
   * @param row the row.
   * @return the index in the specified row.
   */
  public AbstractIndexDescriptor getIndexAt(int row)
  {
    return indexArray.get(row);
  }

  /**
   * Returns the message to be displayed in the cell if an index must be
   * rebuilt.
   * @param index the index to be analyzed.
   * @return the message to be displayed in the cell if an index must be
   * rebuilt.
   */
  protected Message getRebuildRequiredString(AbstractIndexDescriptor index)
  {
    if (info.mustReindex(index))
    {
      return AdminToolMessages.INFO_INDEX_MUST_BE_REBUILT_CELL_VALUE.get();
    }
    else
    {
      return AdminToolMessages.INFO_INDEX_MUST_NOT_BE_REBUILT_CELL_VALUE.get();
    }
  }

  /**
   * Compares the names of the two indexes.
   * @param i1 the first index.
   * @param i2 the second index.
   * @return the alphabetical comparison of the names of both indexes.
   */
  protected int compareNames(AbstractIndexDescriptor i1,
      AbstractIndexDescriptor i2)
  {
    return i1.getName().compareTo(i2.getName());
  }

  /**
   * Compares the rebuild messages for the two indexes.
   * @param i1 the first index.
   * @param i2 the second index.
   * @return the alphabetical comparison of the rebuild required message of both
   * indexes.
   */
  protected int compareRebuildRequired(AbstractIndexDescriptor i1,
      AbstractIndexDescriptor i2)
  {
    return getRebuildRequiredString(i1).compareTo(getRebuildRequiredString(i2));
  }

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<AbstractIndexDescriptor> sortedSet =
      new TreeSet<AbstractIndexDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    indexArray.clear();
    for (AbstractIndexDescriptor index : sortedSet)
    {
      String[] s = getLine(index);
      dataArray.add(s);
      indexArray.add(index);
    }
  }


  /**
   * Returns the column names of the table.
   * @return the column names of the table.
   */
  protected abstract String[] getColumnNames();
  /**
   * Returns the different cell values for a given index in a String array.
   * @param index the index.
   * @return the different cell values for a given index in a String array.
   */
  protected abstract String[] getLine(AbstractIndexDescriptor index);
}

