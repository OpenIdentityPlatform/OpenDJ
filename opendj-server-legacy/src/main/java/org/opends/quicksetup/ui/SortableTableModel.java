/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
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
