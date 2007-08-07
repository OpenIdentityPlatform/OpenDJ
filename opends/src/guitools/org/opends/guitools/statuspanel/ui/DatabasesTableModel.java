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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.guitools.statuspanel.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

import org.opends.guitools.i18n.ResourceProvider;
import org.opends.guitools.statuspanel.BaseDNDescriptor;
import org.opends.quicksetup.ui.SortableTableModel;

/**
 * This class is just a table model used to display the information about
 * databases in a table.
 *
 */
public class DatabasesTableModel extends AbstractTableModel
implements SortableTableModel, Comparator<BaseDNDescriptor>
{
  private static final long serialVersionUID = -5650762484071136983L;
  private HashSet<BaseDNDescriptor> data = new HashSet<BaseDNDescriptor>();
  private ArrayList<BaseDNDescriptor> dataArray =
    new ArrayList<BaseDNDescriptor>();
  private final String[] COLUMN_NAMES = {
    getMsg("basedn-column"),
    getMsg("backendid-column"),
    getMsg("number-entries-column"),
    getMsg("replicated-column"),
    getMsg("missing-changes-column"),
    getMsg("age-of-oldest-missing-change-column")
  };
  private int sortColumn = 0;
  private boolean sortAscending = true;
  private boolean displayReplicationInformation;

  /**
   * Constructor for this table model.
   * @param displayReplicationInformation whether to display replication
   * monitoring information or not.
   */
  public DatabasesTableModel(boolean displayReplicationInformation)
  {
    this.displayReplicationInformation = displayReplicationInformation;
  }

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   */
  public void setData(Set<BaseDNDescriptor> newData)
  {
    if (!newData.equals(data))
    {
      data.clear();
      data.addAll(newData);
      dataArray.clear();
      TreeSet<BaseDNDescriptor> sortedSet =
        new TreeSet<BaseDNDescriptor>(this);
      sortedSet.addAll(data);
      dataArray.addAll(sortedSet);
      fireTableDataChanged();
    }
  }

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  public void forceResort()
  {
    dataArray.clear();
    TreeSet<BaseDNDescriptor> sortedSet =
      new TreeSet<BaseDNDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.addAll(sortedSet);
    fireTableDataChanged();
  }

  /**
   * Comparable implementation.
   * @param desc1 the first replica descriptor to compare.
   * @param desc2 the second replica descriptor to compare.
   * @return 1 if according to the sorting options set by the user the first
   * database descriptor must be put before the second descriptor, 0 if they
   * are equivalent in terms of sorting and -1 if the second descriptor must
   * be put before the first descriptor.
   */
  public int compare(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    int result = 0;
    if (sortColumn == 0)
    {
      result = compareDns(desc1, desc2);

      if (result == 0)
      {
        result = compareBackendIDs(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareEntries(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareRepl(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareMissingChanges(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareAgeOfOldestMissingChange(desc1, desc2);
      }
    }

    if (sortColumn == 1)
    {
      result = compareBackendIDs(desc1, desc2);

      if (result == 0)
      {
        result = compareDns(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareEntries(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareRepl(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareMissingChanges(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareAgeOfOldestMissingChange(desc1, desc2);
      }
    }
    else if (sortColumn == 2)
    {
      result = compareEntries(desc1, desc2);

      if (result == 0)
      {
        result = compareBackendIDs(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareDns(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareRepl(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareMissingChanges(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareAgeOfOldestMissingChange(desc1, desc2);
      }
    }
    else if (sortColumn == 3)
    {
      result = compareRepl(desc1, desc2);

      if (result == 0)
      {
        result = compareBackendIDs(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareDns(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareEntries(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareMissingChanges(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareAgeOfOldestMissingChange(desc1, desc2);
      }
    }
    else if (sortColumn == 4)
    {
      result = compareMissingChanges(desc1, desc2);

      if (result == 0)
      {
        result = compareBackendIDs(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareDns(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareEntries(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareRepl(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareAgeOfOldestMissingChange(desc1, desc2);
      }
    }
    else if (sortColumn == 5)
    {
      result = compareAgeOfOldestMissingChange(desc1, desc2);

      if (result == 0)
      {
        result = compareBackendIDs(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareDns(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareEntries(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareRepl(desc1, desc2);
      }

      if (result == 0)
      {
        result = compareMissingChanges(desc1, desc2);
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
  public int getColumnCount()
  {
    return displayReplicationInformation ? 6 : 4;
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
    Object v;
    BaseDNDescriptor desc = dataArray.get(row);
    if (col == 0)
    {
      v = desc.getDn();
    }
    else if (col == 1)
    {
      v = desc.getDatabase().getBackendID();
    }
    else if (col == 2)
    {
      v = getValueForEntries(desc);
    }
    else if (col == 3)
    {
      v = getStringForReplState(desc);
    }
    else if (col == 4)
    {
      v = getValueForMissingChanges(desc);
    }
    else if (col == 5)
    {
      v = getValueForOldestMissingChange(desc);
    }
    else
    {
      throw new IllegalArgumentException("Invalid col number: "+col);
    }
    return v;
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

  /*
   * Several comparison methods to be able to sort the table model.
   */
  private int compareBackendIDs(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    return desc1.getDatabase().getBackendID().compareTo(
      desc2.getDatabase().getBackendID());
  }

  private int compareEntries(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    int n1 = desc1.getDatabase().getEntries();
    int n2 = desc2.getDatabase().getEntries();
    return compareIntegers(n1, n2);
  }

  private int compareIntegers(int n1, int n2)
  {
    if (n1 == n2)
    {
      return 0;
    }
    if (n1 > n2)
    {
      return 1;
    }
    return -1;
  }

  private int compareDns(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    return desc1.getDn().compareTo(desc2.getDn());
  }

  private int compareRepl(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    return (String.valueOf(desc1.getType()).compareTo(
        String.valueOf(desc2.getType())));
  }

  private int compareMissingChanges(BaseDNDescriptor desc1,
      BaseDNDescriptor desc2)
  {
    return compareIntegers(desc1.getMissingChanges(),
        desc2.getMissingChanges());
  }

  private int compareAgeOfOldestMissingChange(BaseDNDescriptor desc1,
      BaseDNDescriptor desc2)
  {
    return compareIntegers(desc1.getAgeOfOldestMissingChange(),
        desc2.getAgeOfOldestMissingChange());
  }

  /**
   * Returns the Object describing the number of entries of a given Base DN.
   * The Object will be an Integer unless the database of the Base DN contains
   * several Base DNs.  In this case we return a String.
   * @param rep the Base DN object to handle.
   * @return the Object describing the number of entries of a given Base DN.
   */
  private Object getValueForEntries(BaseDNDescriptor rep)
  {
    Object v;
    int nEntries = rep.getDatabase().getEntries();
    if ((rep.getDatabase().getBaseDns().size() > 1) &&
      (nEntries >= 0))
    {
      String[] args = {
        String.valueOf(nEntries),
        rep.getDatabase().getBackendID()
      };
      v = getMsg("number-entries-multiple-suffixes-in-db", args);
    }
    else
    {
      v = new Integer(nEntries);
    }
    return v;
  }

  /**
   * Returns the Object describing the number of missing changes of a given Base
   * DN.  The Object will be a String unless the base DN is
   * replicated and we could not find a valid value (in this case we return
   * an Integer with the invalid value).
   * @param rep the Base DN object to handle.
   * @return the Object describing the number of missing changes of
   * a given Base DN.
   */
  private Object getValueForMissingChanges(BaseDNDescriptor rep)
  {
    Object v;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      v = new Integer(rep.getMissingChanges());
    }
    else
    {
      v = getMsg("not-applicable-label");
    }
    return v;
  }

  /**
   * Returns the Object describing the age of oldest missing change of
   * a given Base DN.  The Object will be a String unless the base DN is
   * replicated and we could not find a valid value (in this case we return
   * an Integer with the invalid value).
   * @param rep the Base DN object to handle.
   * @return the Object describing the age of oldest missing change of
   * a given Base DN.
   */
  private Object getValueForOldestMissingChange(BaseDNDescriptor rep)
  {
    Object v;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      int age = rep.getAgeOfOldestMissingChange();
      if (age >= 0)
      {
        int remainingSeconds = age % 60;
        int minutes = age / 60;
        int remainingMinutes = minutes % 60;
        int hours = minutes / 60;

        String sMinutes = (remainingMinutes>=10)?
        String.valueOf(remainingMinutes) : "0"+remainingMinutes;

        String sSeconds = (remainingSeconds>=10)?
        String.valueOf(remainingSeconds) : "0"+remainingSeconds;

        String sHours = (hours>=10)?String.valueOf(hours):"0"+hours;

        v = sHours+":"+sMinutes+":"+sSeconds;
      }
      else
      {
        v = new Integer(age);
      }
    }
    else
    {
      v = getMsg("not-applicable-label");
    }
    return v;
  }

  /**
   * Returns the localized String describing the replication state of
   * a given Base DN.
   * @param rep the Base DN object to handle.
   * @return the localized String describing the replication state of
   * a given Base DN.
   */
  private String getStringForReplState(BaseDNDescriptor rep)
  {
    String s;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      s = getMsg("suffix-replicated-label");
    }
    else
    {
      s = getMsg("suffix-not-replicated-label");
    }
    return s;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
