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

package org.opends.guitools.controlpanel.ui.renderer;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;

import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.forgerock.i18n.LocalizableMessageDescriptor.Arg1;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;

/** Class used to render elements of the class VLVSortOrder. */
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

  @Override
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
