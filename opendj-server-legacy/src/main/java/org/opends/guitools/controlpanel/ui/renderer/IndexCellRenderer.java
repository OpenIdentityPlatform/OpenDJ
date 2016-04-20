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

import java.awt.Component;

import javax.swing.JList;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The renderer to be used to render AbstractIndexDescriptor objects in a list.
 * It marks the indexes that require to be rebuilt with a '*' character.
 */
public class IndexCellRenderer extends CustomListCellRenderer
{
  private ControlPanelInfo info;

  /**
   * Constructor of the index cell renderer.
   * @param list the list whose contents will be rendered.
   * @param info the control panel information.
   */
  public IndexCellRenderer(JList list, ControlPanelInfo info)
  {
    super(list);
    this.info = info;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    boolean mustReindex = false;
    if (value instanceof AbstractIndexDescriptor)
    {
      mustReindex = info.mustReindex((AbstractIndexDescriptor)value);
    }
    if (value instanceof IndexDescriptor)
    {
      String name = ((IndexDescriptor)value).getName();
      value = mustReindex ? name + " (*)" : name;

    } else if (value instanceof VLVIndexDescriptor)
    {
      String name =
        Utilities.getVLVNameInCellRenderer((VLVIndexDescriptor)value);
      value = mustReindex ? name + " (*)" : name;
    }
    return super.getListCellRendererComponent(
        list, value, index, isSelected, cellHasFocus);
  }
}
