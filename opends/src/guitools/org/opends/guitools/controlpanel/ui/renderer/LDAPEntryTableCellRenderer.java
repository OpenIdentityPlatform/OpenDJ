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
import org.opends.server.types.Schema;

/**
 * The renderer used by the table in the 'Attribute View' of the LDAP entry
 * browser.
 *
 */
public class LDAPEntryTableCellRenderer extends SelectableTableCellRenderer
{
  private static final long serialVersionUID = 3590456676685339618L;
  private BinaryCellPanel binaryPanel;
  private ObjectClassCellPanel ocPanel;
  private JLabel lockLabel = new JLabel();
  private ImageIcon lockIcon =
    Utilities.createImageIcon(IconPool.IMAGE_PATH+"/field-locked.png");
  private Schema schema;
  private Collection<String> requiredAttrs = new ArrayList<String>();

  /**
   * Constructor of the cell renderer.
   *
   */
  public LDAPEntryTableCellRenderer()
  {
    binaryPanel = new BinaryCellPanel();
    binaryPanel.setOpaque(true);
    ocPanel = new ObjectClassCellPanel();
    ocPanel.setOpaque(true);
    GridBagConstraints gbc = new GridBagConstraints();
    add(lockLabel, gbc);

  }

  /**
   * {@inheritDoc}
   */
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
      return getStringValue(table, Utilities.OBFUSCATED_VALUE, isSelected,
          hasFocus, row, column);
    }
    else if (value instanceof ObjectClassValue)
    {
      if (!table.isCellEditable(row, column))
      {
        ocPanel.setLockIconVisible(true);
        ocPanel.setEditButtonVisible(false);
      }
      else
      {
        ocPanel.setLockIconVisible(false);
        ocPanel.setEditButtonVisible(true);
      }
      ocPanel.setValue((ObjectClassValue)value);
      if (hasFocus)
      {
        ocPanel.setBorder(getDefaultFocusBorder(table, value, isSelected,
            row, column));
      }
      else
      {
        ocPanel.setBorder(defaultBorder);
      }
      updateComponent(ocPanel, table, row, column, isSelected);
      return ocPanel;
    }
    else if ((value instanceof byte[]) || (value instanceof BinaryValue))
    {
      if (value instanceof byte[])
      {
        if (((byte[])value).length > 0)
        {
          binaryPanel.setValue((byte[])value, isImage(table, row, column));
        }
        else
        {
          binaryPanel.setValue((byte[])null, isImage(table, row, column));
        }
      }
      else
      {
        binaryPanel.setValue((BinaryValue)value, isImage(table, row, column));
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
      if (hasFocus)
      {
        binaryPanel.setBorder(getDefaultFocusBorder(table, value, isSelected,
            row, column));
      }
      else
      {
        binaryPanel.setBorder(defaultBorder);
      }
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
    if (table.isCellEditable(row, column) && !isSelected)
    {
      lockLabel.setIcon(null);
    }
    else
    {
      if ((column == 1) && !table.isCellEditable(row, column))
      {
        lockLabel.setIcon(lockIcon);
      }
      else
      {
        lockLabel.setIcon(null);
      }
    }
    return this;
  }

  private boolean isPassword(JTable table, int row, int col)
  {
    boolean isPassword = false;
    if (col == 1)
    {
      Object o = table.getValueAt(row, 0);
      if (Utilities.hasPasswordSyntax((String)o, getSchema()))
      {
        isPassword = true;
      }
    }
    return isPassword;
  }

  private boolean isImage(JTable table, int row, int col)
  {
    boolean isImage = false;
    if (col == 1)
    {
      Object o = table.getValueAt(row, 0);
      isImage = Utilities.hasImageSyntax((String)o, schema);
    }
    return isImage;
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
    boolean isRequired = false;
    if (col == 0)
    {
      Object o = table.getValueAt(row, 0);
      isRequired = requiredAttrs.contains(
          Utilities.getAttributeNameWithoutOptions((String)o).toLowerCase());
    }
    return isRequired;
  }
}
