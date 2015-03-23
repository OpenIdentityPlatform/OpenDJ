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
 *
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

  /** {@inheritDoc} */
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
