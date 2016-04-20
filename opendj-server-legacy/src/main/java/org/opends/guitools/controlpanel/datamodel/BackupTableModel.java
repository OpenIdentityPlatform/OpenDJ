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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.datamodel;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

/** The table used to display the backups. */
public class BackupTableModel extends AbstractTableModel
{
  private static final long serialVersionUID = -3511425157550147124L;
  private ArrayList<BackupDescriptor> backups = new ArrayList<>();

  /** Clears the contents of the table model. */
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

  @Override
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

  @Override
  public int getRowCount()
  {
    return backups.size();
  }

  @Override
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
