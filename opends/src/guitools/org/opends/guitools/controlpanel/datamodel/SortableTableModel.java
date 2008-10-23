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

import javax.swing.table.AbstractTableModel;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * A generic interface that must implement table models that are sortable.
 */
public abstract class SortableTableModel extends AbstractTableModel
{
  /**
   * Returns whether the sort is ascending or descending.
   * @return <CODE>true</CODE> if the sort is ascending and <CODE>false</CODE>
   * otherwise.
   */
  public abstract boolean isSortAscending();

  /**
   * Sets whether to sort ascending of descending.
   * @param sortAscending whether to sort ascending or descending.
   */
  public abstract void setSortAscending(boolean sortAscending);

  /**
   * Returns the column index used to sort.
   * @return the column index used to sort.
   */
  public abstract int getSortColumn();

  /**
   * Sets the column index used to sort.
   * @param sortColumn column index used to sort..
   */
  public abstract void setSortColumn(int sortColumn);

  /**
   * Updates the table model contents and sorts its contents depending on the
   * sort options set by the user.
   */
  public abstract void forceResort();


  /**
   * Returns the header wrapped with the default line width.
   * @param msg the header message value (with no HTML formatting).
   * @return the header wrapped with the default line width.
   */
  protected String getHeader(Message msg)
  {
    return getHeader(msg, 15);
  }

  /**
   * Returns the header wrapped with a certain line width.
   * @param msg the header message value (with no HTML formatting).
   * @param wrap the maximum line width before wrapping.
   * @return the header wrapped with the specified line width.
   */
  protected String getHeader(Message msg, int wrap)
  {
    String text = msg.toString();
    String wrappedText = StaticUtils.wrapText(text, wrap);
    wrappedText = wrappedText.replaceAll(ServerConstants.EOL, "<br>");
    return "<html>"+Utilities.applyFont(wrappedText,
        ColorAndFontConstants.headerFont);
  }
}
