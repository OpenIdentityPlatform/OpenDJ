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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The table model used to display all the base DNs.
 *
 */
public class BaseDNTableModel extends SortableTableModel
implements Comparator<BaseDNDescriptor>
{
  private static final long serialVersionUID = -5650762484071136983L;
  private HashSet<BaseDNDescriptor> data = new HashSet<BaseDNDescriptor>();
  private ServerDescriptor.ServerStatus serverStatus;
  private boolean isAuthenticated;

  private ArrayList<String[]> dataArray =
    new ArrayList<String[]>();
  private String[] COLUMN_NAMES;
  private int sortColumn = 0;
  private boolean sortAscending = true;
  private boolean displayReplicationInformation;

  /**
   * Key value to identify the case of a value not available because the server
   * is down.
   */
  public static String NOT_AVAILABLE_SERVER_DOWN = "NOT_AVAILABLE_SERVER_DOWN";

  /**
   * Key value to identify the case of a value not available because
   * authentication is required.
   */
  public static String NOT_AVAILABLE_AUTHENTICATION_REQUIRED =
    "NOT_AVAILABLE_AUTHENTICATION_REQUIRED";

  /**
   * Key value to identify the case of a value not available.
   */
  public static String NOT_AVAILABLE = "NOT_AVAILABLE";

  /**
   * Constructor for this table model.
   * @param displayReplicationInformation whether to display replication.
   * monitoring information or not.
   */
  public BaseDNTableModel(boolean displayReplicationInformation)
  {
    this(displayReplicationInformation, true);
  }

  /**
   * Constructor for this table model.
   * @param displayReplicationInformation whether to display replication.
   * @param wrapHeader whether to wrap the headers or not.
   * monitoring information or not.
   */
  public BaseDNTableModel(boolean displayReplicationInformation,
      boolean wrapHeader)
  {
    this.displayReplicationInformation = displayReplicationInformation;
    if (wrapHeader)
    {
     COLUMN_NAMES = new String[] {
          getHeader(INFO_BASEDN_COLUMN.get()),
          getHeader(INFO_BACKENDID_COLUMN.get()),
          getHeader(INFO_NUMBER_ENTRIES_COLUMN.get()),
          getHeader(INFO_REPLICATED_COLUMN.get()),
          getHeader(INFO_MISSING_CHANGES_COLUMN.get()),
          getHeader(INFO_AGE_OF_OLDEST_MISSING_CHANGE_COLUMN.get())
      };
    }
    else
    {
      COLUMN_NAMES = new String[] {
          INFO_BASEDN_COLUMN.get().toString(),
          INFO_BACKENDID_COLUMN.get().toString(),
          INFO_NUMBER_ENTRIES_COLUMN.get().toString(),
          INFO_REPLICATED_COLUMN.get().toString(),
          INFO_MISSING_CHANGES_COLUMN.get().toString(),
          INFO_AGE_OF_OLDEST_MISSING_CHANGE_COLUMN.get().toString()
      };
    }
  }

  /**
   * Sets the data for this table model.
   * @param newData the data for this table model.
   * @param status the server status.
   * @param isAuthenticated whether the user provided authentication or not.
   */
  public void setData(Set<BaseDNDescriptor> newData,
      ServerDescriptor.ServerStatus status, boolean isAuthenticated)
  {
    if (!newData.equals(data) || (serverStatus != status) ||
        (this.isAuthenticated != isAuthenticated))
    {
      serverStatus = status;
      this.isAuthenticated = isAuthenticated;
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
   * Comparable implementation.
   * @param desc1 the first replica descriptor to compare.
   * @param desc2 the second replica descriptor to compare.
   * @return 1 if according to the sorting options set by the user the first
   * base DN descriptor must be put before the second descriptor, 0 if they
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
    return dataArray.get(row)[col];
  }

  /**
   * Updates the array data.  This includes resorting it.
   */
  private void updateDataArray()
  {
    TreeSet<BaseDNDescriptor> sortedSet = new TreeSet<BaseDNDescriptor>(this);
    sortedSet.addAll(data);
    dataArray.clear();
    for (BaseDNDescriptor desc : sortedSet)
    {
      String[] s = new String[6];

      s[0] = Utilities.unescapeUtf8(desc.getDn().toString());

      s[1] = desc.getBackend().getBackendID();

      s[2] = getValueForEntries(desc);

      s[3] = getStringForReplState(desc);

      s[4] = getValueForMissingChanges(desc);

      s[5] = getValueForOldestMissingChange(desc);

      dataArray.add(s);
    }
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
    return desc1.getBackend().getBackendID().compareTo(
      desc2.getBackend().getBackendID());
  }

  private int compareEntries(BaseDNDescriptor desc1, BaseDNDescriptor desc2)
  {
    int n1 = desc1.getEntries();
    int n2 = desc2.getEntries();
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

  private int compareLongs(long n1, long n2)
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
    return Utilities.unescapeUtf8(desc1.getDn().toString()).compareTo(
        Utilities.unescapeUtf8(desc2.getDn().toString()));
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
    return compareLongs(desc1.getAgeOfOldestMissingChange(),
        desc2.getAgeOfOldestMissingChange());
  }

  /**
   * Returns the Object describing the number of entries of a given Base DN.
   * The Object will be an Integer.
   * @param rep the Base DN object to handle.
   * @return the Object describing the number of entries of a given Base DN.
   */
  private String getValueForEntries(BaseDNDescriptor rep)
  {
    String returnValue;
    if (serverStatus != ServerDescriptor.ServerStatus.STARTED)
    {
      returnValue = NOT_AVAILABLE_SERVER_DOWN;
    }
    else if (!isAuthenticated)
    {
      returnValue = NOT_AVAILABLE_AUTHENTICATION_REQUIRED;
    }
    else
    {
      if (rep.getEntries() < 0)
      {
        returnValue = NOT_AVAILABLE;
      }
      else
      {
        returnValue = String.valueOf(rep.getEntries());
      }
    }
    return returnValue;
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
  private String getValueForMissingChanges(BaseDNDescriptor rep)
  {
    String returnValue;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      if (serverStatus != ServerDescriptor.ServerStatus.STARTED)
      {
        returnValue = NOT_AVAILABLE_SERVER_DOWN;
      }
      else if (!isAuthenticated)
      {
        returnValue = NOT_AVAILABLE_AUTHENTICATION_REQUIRED;
      }
      else
      {
        if (rep.getMissingChanges() < 0)
        {
          returnValue = NOT_AVAILABLE;
        }
        else
        {
          returnValue = String.valueOf(rep.getMissingChanges());
        }
      }
    }
    else
    {
      returnValue = INFO_NOT_APPLICABLE_LABEL.get().toString();
    }
    return returnValue;
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
  private String getValueForOldestMissingChange(BaseDNDescriptor rep)
  {
    String returnValue;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      if (serverStatus != ServerDescriptor.ServerStatus.STARTED)
      {
        returnValue = NOT_AVAILABLE_SERVER_DOWN;
      }
      else if (!isAuthenticated)
      {
        returnValue = NOT_AVAILABLE_AUTHENTICATION_REQUIRED;
      }
      else
      {
        long age = rep.getAgeOfOldestMissingChange();
        if (age > 0)
        {
          Date date = new Date(age);
          returnValue = date.toString();
        }
        else
        {
          // Not available
          returnValue = NOT_AVAILABLE;
        }
      }
    }
    else
    {
      returnValue = INFO_NOT_APPLICABLE_LABEL.get().toString();
    }
    return returnValue;
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
    Message s;
    if (rep.getType() == BaseDNDescriptor.Type.REPLICATED)
    {
      s = INFO_BASEDN_REPLICATED_LABEL.get();
    }
    else
    {
      s = INFO_BASEDN_NOT_REPLICATED_LABEL.get();
    }
    return s.toString();
  }
}
