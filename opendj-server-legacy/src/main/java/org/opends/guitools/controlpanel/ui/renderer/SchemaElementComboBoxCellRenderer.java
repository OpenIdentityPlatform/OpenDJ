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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;

import javax.swing.JComboBox;
import javax.swing.JList;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.AttributeUsage;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.forgerock.opendj.ldap.schema.Syntax;

/** The cell renderer to be used to render schema elements in a combo box. */
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
   * Constructor of the cell renderer.
   * @param list the list containing the elements to be rendered.
   */
  public SchemaElementComboBoxCellRenderer(JList list)
  {
    super(list);
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value,
      int index, boolean isSelected, boolean cellHasFocus)
  {
    return super.getListCellRendererComponent(
        list, getLabel(value), index, isSelected, cellHasFocus);
  }

  private Object getLabel(Object value)
  {
    if (value instanceof Syntax)
    {
      Syntax syntax = (Syntax) value;
      return syntax.getName() != null ? syntax.getName() : syntax.getOID();
    }
    else if (value instanceof AttributeType)
    {
      return ((AttributeType) value).getNameOrOID();
    }
    else if (value instanceof ObjectClass)
    {
      return ((ObjectClass) value).getNameOrOID();
    }
    else if (value instanceof MatchingRule)
    {
      return ((MatchingRule) value).getNameOrOID();
    }
    else if (value instanceof AttributeUsage)
    {
      boolean isOperational = ((AttributeUsage)value).isOperational();
      if (isOperational)
      {
        return INFO_CTRL_PANEL_ATTRIBUTE_USAGE_OPERATIONAL.get(value.toString());
      }
    }
    else if (value instanceof ObjectClassType)
    {
      switch ((ObjectClassType)value)
      {
      case AUXILIARY:
        return INFO_CTRL_PANEL_OBJECTCLASS_AUXILIARY_LABEL.get().toString();
      case STRUCTURAL:
        return INFO_CTRL_PANEL_OBJECTCLASS_STRUCTURAL_LABEL.get().toString();
      case ABSTRACT:
        return INFO_CTRL_PANEL_OBJECTCLASS_ABSTRACT_LABEL.get().toString();
      }
    }
    return value;
  }
}
