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
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.quicksetup.ui;

import javax.swing.table.TableModel;

/**
 * A generic interface that must implement table models that are sortable.
 */
public interface SortableTableModel extends TableModel
{
  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  boolean isSortAscending();

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  void setSortAscending(boolean sortAscending);

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  int getSortColumn();

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  void setSortColumn(int sortColumn);

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  void forceResort();
}
