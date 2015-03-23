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

package org.opends.guitools.controlpanel.ui.renderer;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
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

  /** {@inheritDoc} */
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    if (value instanceof VLVSortOrder)
    {
      VLVSortOrder v = (VLVSortOrder)value;
      Arg1<Object> arg = v.isAscending()
          ? INFO_CTRL_PANEL_VLV_ASCENDING_VLV_INDEX
          : INFO_CTRL_PANEL_VLV_DESCENDING_VLV_INDEX;
      value = arg.get(v.getAttributeName()).toString();
    }
    return defaultRenderer.getListCellRendererComponent(
        list, value, index, isSelected, cellHasFocus);
  }
}
