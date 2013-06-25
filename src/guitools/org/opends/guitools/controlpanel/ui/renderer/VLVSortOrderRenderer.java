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

package org.opends.guitools.controlpanel.ui.renderer;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;

/**
 * Class used to render elements of the class VLVSortOrder.
 *
 */
public class VLVSortOrderRenderer implements ListCellRenderer
{
  private ListCellRenderer defaultRenderer;

  /**
   * Constructor of the renderer.
   * @param list the list whose elements must be rendered.
   */
  public VLVSortOrderRenderer(JList list)
  {
    this.defaultRenderer = list.getCellRenderer();
  }

  /**
   * {@inheritDoc}
   */
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    if (value instanceof VLVSortOrder)
    {
      VLVSortOrder v = (VLVSortOrder)value;
      if (v.isAscending())
      {
        value = INFO_CTRL_PANEL_VLV_ASCENDING_VLV_INDEX.get(
            v.getAttributeName()).toString();
      }
      else
      {
        value = INFO_CTRL_PANEL_VLV_DESCENDING_VLV_INDEX.get(
            v.getAttributeName()).toString();
      }
    }
    Component comp = defaultRenderer.getListCellRendererComponent(
        list, value, index, isSelected, cellHasFocus);

    return comp;
  }
}
