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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.renderer;

import static org.opends.messages.AdminToolMessages.*;
import static com.forgerock.opendj.cli.Utils.OBFUSCATED_VALUE;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.ui.components.BinaryCellPanel;
import org.opends.guitools.controlpanel.ui.components.ObjectClassCellPanel;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.opendj.ldap.schema.Schema;

/** The renderer used by the table in the 'Attribute View' of the LDAP entry browser. */
public class LDAPEntryTableCellRenderer extends SelectableTableCellRenderer
{
  private static final long serialVersionUID = 3590456676685339618L;
  private BinaryCellPanel binaryPanel;
  private ObjectClassCellPanel ocPanel;
  private JLabel lockLabel = new JLabel();
  private ImageIcon lockIcon =
    Utilities.createImageIcon(IconPool.IMAGE_PATH+"/field-locked.png");
  private Schema schema;
  private Collection<String> requiredAttrs = new ArrayList<>();

  /** Constructor of the cell renderer. */
  public LDAPEntryTableCellRenderer()
  {
    binaryPanel = new BinaryCellPanel();
    binaryPanel.setOpaque(true);
    ocPanel = new ObjectClassCellPanel();
    ocPanel.setOpaque(true);
    GridBagConstraints gbc = new GridBagConstraints();
    add(lockLabel, gbc);
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
    if (isRequired(table, row, column))
    {
      Utilities.setRequiredIcon(label);
    }
    else
    {
      label.setIcon(null);
    }
    if (isPassword(table, row, column))
    {
      return getStringValue(table, OBFUSCATED_VALUE, isSelected, hasFocus, row, column);
    }
    else if (value instanceof ObjectClassValue)
    {
      final boolean cellEditable = table.isCellEditable(row, column);
      ocPanel.setLockIconVisible(!cellEditable);
      ocPanel.setEditButtonVisible(cellEditable);
      ocPanel.setValue((ObjectClassValue)value);
      ocPanel.setBorder(hasFocus
          ? getDefaultFocusBorder(table, value, isSelected, row, column)
          : defaultBorder);
      updateComponent(ocPanel, table, row, column, isSelected);
      return ocPanel;
    }
    else if (value instanceof byte[] || value instanceof BinaryValue)
    {
      boolean isImage = isImage(table, row, column);
      if (value instanceof byte[])
      {
        byte[] bytes = (byte[]) value;
        binaryPanel.setValue(bytes.length > 0 ? bytes : null, isImage);
      }
      else
      {
        binaryPanel.setValue((BinaryValue) value, isImage);
      }

      if (!table.isCellEditable(row, column))
      {
        binaryPanel.setLockIconVisible(true);
        binaryPanel.setEditButtonText(INFO_CTRL_PANEL_VIEW_BUTTON_LABEL.get());
      }
      else
      {
        binaryPanel.setLockIconVisible(false);
        binaryPanel.setEditButtonText(INFO_CTRL_PANEL_EDIT_BUTTON_LABEL.get());
      }

      binaryPanel.setBorder(hasFocus
          ? getDefaultFocusBorder(table, value, isSelected, row, column)
          : defaultBorder);
      updateComponent(binaryPanel, table, row, column, isSelected);
      return binaryPanel;
    }
    else
    {
      return getStringValue(table, value, isSelected, hasFocus, row, column);
    }
  }

  /**
   * Returns the String representation for a given byte array.
   * @param value the byte array.
   * @return the String representation for a given byte array.
   */
  public String getString(byte[] value)
  {
    return binaryPanel.getString(value, false).toString();
  }

  /**
   * Returns the String representation for a given BinaryValue object.
   * @param value the BinaryValue object.
   * @return the String representation for the provided BinaryValue object.
   */
  public String getString(BinaryValue value)
  {
    return binaryPanel.getMessage(value, false).toString();
  }

  /**
   * Returns the String representation for a given ObjectClassValue object.
   * @param value the ObjectClassValue object.
   * @return the String representation for the provided ObjectClassValue object.
   */
  public String getString(ObjectClassValue value)
  {
    return ocPanel.getMessage(value).toString();
  }

  private Component getStringValue(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column)
  {
    super.getTableCellRendererComponent(table, value, isSelected,
        hasFocus, row, column);
    lockLabel.setIcon(getIcon(table, row, column, isSelected));
    return this;
  }

  private ImageIcon getIcon(JTable table, int row, int column, boolean isSelected)
  {
    if (table.isCellEditable(row, column) && !isSelected)
    {
      return null;
    }
    else if (column == 1 && !table.isCellEditable(row, column))
    {
      return lockIcon;
    }
    else
    {
      return null;
    }
  }

  private boolean isPassword(JTable table, int row, int col)
  {
    if (col == 1)
    {
      Object o = table.getValueAt(row, 0);
      if (Utilities.hasPasswordSyntax((String)o, getSchema()))
      {
        return true;
      }
    }
    return false;
  }

  private boolean isImage(JTable table, int row, int col)
  {
    if (col == 1)
    {
      Object o = table.getValueAt(row, 0);
      return Utilities.hasImageSyntax((String)o, schema);
    }
    return false;
  }

  /**
   * Returns the schema.
   * @return the schema.
   */
  public Schema getSchema()
  {
    return schema;
  }

  /**
   * Sets the schema.
   * @param schema the schema.
   */
  public void setSchema(Schema schema)
  {
    this.schema = schema;
  }

  /**
   * Sets the list of required attributes for the entry that is being rendered
   * using this renderer.
   * @param requiredAttrs the required attribute names.
   */
  public void setRequiredAttrs(Collection<String> requiredAttrs)
  {
    this.requiredAttrs.clear();
    this.requiredAttrs.addAll(requiredAttrs);
  }

  private boolean isRequired(JTable table, int row, int col)
  {
    if (col == 0)
    {
      Object o = table.getValueAt(row, 0);
      return requiredAttrs.contains(Utilities.getAttributeNameWithoutOptions((String) o).toLowerCase());
    }
    return false;
  }
}
