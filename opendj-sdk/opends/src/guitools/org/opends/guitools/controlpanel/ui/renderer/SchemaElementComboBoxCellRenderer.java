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

import javax.swing.JComboBox;
import javax.swing.JList;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.ObjectClassType;

/**
 * The cell renderer to be used to render schema elements in a combo box.
 *
 */
public class SchemaElementComboBoxCellRenderer extends CustomListCellRenderer
{
  /**
   * Constructor of the cell renderer.
   * @param combo the combo box containing the elements to be rendered.
   */
  public SchemaElementComboBoxCellRenderer(JComboBox combo)
  {
    super(combo);
  }

  /**
   * {@inheritDoc}
   */
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    if (value instanceof AttributeSyntax)
    {
      value = ((AttributeSyntax)value).getSyntaxName();
      if (value == null)
      {
        value = ((AttributeSyntax)value).getOID();
      }
    }
    else if (value instanceof CommonSchemaElements)
    {
      value = ((CommonSchemaElements)value).getNameOrOID();
    }
    else if (value instanceof MatchingRule)
    {
      value = ((MatchingRule)value).getNameOrOID();
    }
    else if (value instanceof AttributeUsage)
    {
      boolean isOperational = ((AttributeUsage)value).isOperational();
      if (isOperational)
      {
        value = INFO_CTRL_PANEL_ATTRIBUTE_USAGE_OPERATIONAL.get(
            value.toString());
      }
    }
    else if (value instanceof ObjectClassType)
    {
      switch ((ObjectClassType)value)
      {
      case AUXILIARY:
        value = INFO_CTRL_PANEL_OBJECTCLASS_AUXILIARY_LABEL.get().toString();
        break;
      case STRUCTURAL:
        value = INFO_CTRL_PANEL_OBJECTCLASS_STRUCTURAL_LABEL.get().toString();
        break;
      case ABSTRACT:
        value = INFO_CTRL_PANEL_OBJECTCLASS_ABSTRACT_LABEL.get().toString();
        break;
      }
    }
    Component comp = super.getListCellRendererComponent(list, value, index,
        isSelected, cellHasFocus);

    return comp;
  }
}
