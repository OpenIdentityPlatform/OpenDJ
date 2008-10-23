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

import javax.swing.table.AbstractTableModel;

/**
 * The table used to display the backups.
 *
 */
public class BackupTableModel extends AbstractTableModel
{
  private static final long serialVersionUID = -3511425157550147124L;
  private ArrayList<BackupDescriptor> backups =
    new ArrayList<BackupDescriptor>();
  /**
   * Clears the contents ot the table model.
   *
   */
  public void clear()
  {
    backups.clear();
  }

  /**
   * Adds a backup to the model.
   * @param backup the backup to be added.
   */
  public void add(BackupDescriptor backup)
  {
    backups.add(backup);
  }

  /**
   * {@inheritDoc}
   */
  public Object getValueAt(int row, int column)
  {
    switch (column)
    {
    case 0:
      return get(row).getID();
    case 1:
      return get(row).getPath();
    case 2:
      return get(row).getCreationDate();
    case 3:
      return get(row).getType();
      default:
        throw new IllegalArgumentException("Invalid column: "+column);
    }
  }

  /**
   * Returns the row count.
   * @return the row count.
   */
  public int getRowCount()
  {
    return backups.size();
  }

  /**
   * Returns the column count.
   * @return the column count.
   */
  public int getColumnCount()
  {
    return 4;
  }

  /**
   * Gets the BackupDescriptor in a given row.
   * @param row the row.
   * @return the BackupDescriptor in a given row.
   */
  public BackupDescriptor get(int row)
  {
    return backups.get(row);
  }
}
