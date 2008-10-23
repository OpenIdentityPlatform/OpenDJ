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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellEditor;

import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.ui.BinaryAttributeEditorPanel;
import org.opends.guitools.controlpanel.ui.ObjectClassEditorPanel;
import org.opends.guitools.controlpanel.ui.GenericDialog;
import org.opends.guitools.controlpanel.ui.components.BinaryCellPanel;
import org.opends.guitools.controlpanel.ui.components.ObjectClassCellPanel;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The cell editor used in the 'Attribute' View of the entries in the LDAP
 * entry browser.
 *
 */
public class AttributeCellEditor extends AbstractCellEditor
implements TableCellEditor
{
  private static final long serialVersionUID = 1979354208925355746L;

  private BinaryCellPanel binaryPanel;

  private ObjectClassCellPanel ocPanel;

  private ObjectClassValue ocValue;
  private byte[] value;
  private BinaryValue binaryValue;

  private TableCellEditor defaultEditor;
  private TableCellEditor passwordEditor;

  private GenericDialog editBinaryDlg;
  private BinaryAttributeEditorPanel editBinaryPanel;

  private GenericDialog editOcDlg;
  private ObjectClassEditorPanel editOcPanel;

  private JTable table;

  private JTextField textField;

  private JPasswordField passwordField;

  private ControlPanelInfo info;

  private String attrName;


  /**
   * Default constructor.
   *
   */
  public AttributeCellEditor()
  {
    textField = Utilities.createTextField();
    textField.getDocument().addDocumentListener(new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        if (!textField.hasFocus())
        {
          textField.requestFocusInWindow();
        }
      }

      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    });
    passwordField = Utilities.createPasswordField();
    passwordField.getDocument().addDocumentListener(new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        if (!passwordField.hasFocus())
        {
          passwordField.requestFocusInWindow();
        }
      }

      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    });
    this.defaultEditor = new DefaultCellEditor(textField);
    this.passwordEditor = new DefaultCellEditor(passwordField);
    binaryPanel = new BinaryCellPanel();
    binaryPanel.addEditActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent e)
      {
        if (editBinaryDlg == null)
        {
          editBinaryPanel = new BinaryAttributeEditorPanel();
          editBinaryPanel.setInfo(getInfo());
          editBinaryDlg = new GenericDialog(Utilities.getFrame(table),
              editBinaryPanel);
          editBinaryDlg.setModal(true);
          Utilities.centerGoldenMean(editBinaryDlg,
              Utilities.getParentDialog(table));
        }
        if (binaryValue != null)
        {
          editBinaryPanel.setValue(attrName, binaryValue);
        }
        else if (value != null)
        {
          if (value.length > 0)
          {
            editBinaryPanel.setValue(attrName,
                BinaryValue.createBase64(value));
          }
          else
          {
            editBinaryPanel.setValue(attrName, null);
          }
        }
        else
        {
          editBinaryPanel.setValue(attrName, null);
        }
        editBinaryDlg.setVisible(true);
        if (editBinaryPanel.valueChanged())
        {
          BinaryValue changedValue = editBinaryPanel.getBinaryValue();
          binaryValue = changedValue;
          value = null;
          ocValue = null;
        }
        fireEditingStopped();
      }
    });
    ocPanel = new ObjectClassCellPanel();
    ocPanel.addEditActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {

        if (editOcDlg == null)
        {
          editOcPanel = new ObjectClassEditorPanel();
          editOcPanel.setInfo(getInfo());
          editOcDlg = new GenericDialog(
              null,
              editOcPanel);
          editOcDlg.setModal(true);
          Utilities.centerGoldenMean(editOcDlg,
              Utilities.getParentDialog(table));
        }
        if (ocValue != null)
        {
          editOcPanel.setValue(ocValue);
        }
        editOcDlg.setVisible(true);
        if (editOcPanel.valueChanged())
        {
          binaryValue = null;
          value = null;
          ocValue = editOcPanel.getObjectClassValue();
          fireEditingStopped();
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public Component getTableCellEditorComponent(JTable table, Object value,
                   boolean isSelected, int row, int column)
  {
    this.table = table;
    if (isPassword(table, row))
    {
      this.value = null;
      this.binaryValue = null;
      this.ocValue = null;
      return passwordEditor.getTableCellEditorComponent(table, value,
          isSelected, row, column);
    }
    else if (value instanceof ObjectClassValue)
    {
      this.value = null;
      this.binaryValue = null;
      this.ocValue = (ObjectClassValue)value;
      ocPanel.setValue(ocValue);
      ocPanel.setBorder(CustomCellRenderer.getDefaultFocusBorder(table,
          value, isSelected, row, column));
      return ocPanel;
    }
    else if ((value instanceof byte[]) || (value instanceof BinaryValue))
    {
      attrName = getAttributeName(table, row);
      boolean isImage = Utilities.hasImageSyntax(attrName,
          getInfo().getServerDescriptor().getSchema());
      if (value instanceof byte[])
      {
        this.value = (byte[])value;
        this.binaryValue = null;
        this.ocValue = null;
        if (this.value.length > 0)
        {
          binaryPanel.setValue(BinaryValue.createBase64(this.value), isImage);
        }
        else
        {
          binaryPanel.setValue((byte[])null, isImage);
        }
      }
      else
      {
        this.value = null;
        this.ocValue = null;
        binaryValue = (BinaryValue)value;
        binaryPanel.setValue(binaryValue, isImage);
      }
      binaryPanel.setBorder(CustomCellRenderer.getDefaultFocusBorder(table,
          value, isSelected, row, column));
      return binaryPanel;
    }
    else
    {
      this.value = null;
      this.binaryValue = null;
      this.ocValue = null;
      return defaultEditor.getTableCellEditorComponent(table, value, isSelected,
          row, column);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Object getCellEditorValue()
  {
    if (binaryValue != null)
    {
      return binaryValue;
    }
    else if (value != null)
    {
      return value;
    }
    else if (ocValue != null)
    {
      return ocValue;
    }
    else
    {
      return defaultEditor.getCellEditorValue();
    }
  }

  private boolean isPassword(JTable table, int row)
  {
    boolean isPassword = false;
    Object o = table.getValueAt(row, 0);
    if (Utilities.hasPasswordSyntax(String.valueOf(o),
        getInfo().getServerDescriptor().getSchema()))
    {
      isPassword = true;
    }
    return isPassword;
  }

  private String getAttributeName(JTable table, int row)
  {
    Object o = table.getValueAt(row, 0);
    String attrName = String.valueOf(o);
    return attrName;
  }

  /**
   * Returns the control panel information.
   * @return the control panel information.
   */
  public ControlPanelInfo getInfo()
  {
    return info;
  }

  /**
   * Sets the control panel information.
   * @param info the control panel information.
   */
  public void setInfo(ControlPanelInfo info)
  {
    this.info = info;
  }
}
