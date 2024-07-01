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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.messages.AdminToolMessages;

/**
 * Table Model used to store information about indexes.  It is used basically
 * by the tables that appear on the right side of the 'Manage Indexes...'
 * dialog when the user clicks on 'Indexes' or 'VLV Indexes'.
 */
public abstract class AbstractIndexTableModel extends SortableTableModel
implements Comparator<AbstractIndexDescriptor>
{
  private static final long serialVersionUID = -5131878622200568636L;
  private final Set<AbstractIndexDescriptor> data = new HashSet<>();
  private final List<String[]> dataArray = new ArrayList<>();
  private final List<AbstractIndexDescriptor> indexArray = new ArrayList<>();
  private final String[] COLUMN_NAMES = getColumnNames();
  /** The sort column of the table. */
  protected int sortColumn;
  /** Whether the sorting is ascending or descending. */
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
    data.clear();
    data.addAll(newData);
    updateDataArray();
    fireTableDataChanged();
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  @Override
  public void forceResort()
  {
    updateDataArray();
    fireTableDataChanged();
  }



  @Override
  public int getColumnCount()
  {
    return COLUMN_NAMES.length;
  }

  @Override
  public int getRowCount()
  {
    return dataArray.size();
  }

  @Override
  public Object getValueAt(int row, int col)
  {
    return dataArray.get(row)[col];
  }

  @Override
  public String getColumnName(int col) {
    return COLUMN_NAMES[col];
  }


  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  @Override
  public boolean isSortAscending()
  {
    return sortAscending;
  }

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  @Override
  public void setSortAscending(boolean sortAscending)
  {
    this.sortAscending = sortAscending;
  }

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  @Override
  public int getSortColumn()
  {
    return sortColumn;
  }

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  @Override
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
  protected LocalizableMessage getRebuildRequiredString(AbstractIndexDescriptor index)
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

  /** Updates the array data. This includes resorting it. */
  private void updateDataArray()
  {
    TreeSet<AbstractIndexDescriptor> sortedSet = new TreeSet<>(this);
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

