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

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.Schema;

/**
 * This is the class used to edit the object class of a given entry, it displays
 * the structural objectclass of the entry and its auxiliary objectclasses.
 *
 */
public class ObjectClassEditorPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 6632731109835897496L;
  private JComboBox structural;
  private AddRemovePanel<String> auxiliary;

  private ObjectClassValue value;

  private boolean valueChanged;

  /**
   * Default constructor.
   *
   */
  public ObjectClassEditorPanel()
  {
    super();
    createLayout();
  }

  /**
   * Sets the object class to be displayed in the panel.
   * @param value the object class to be displayed in the panel.
   */
  public void setValue(ObjectClassValue value)
  {
    this.value = value;
    String struct = value.getStructural();
    if (struct != null)
    {
      DefaultComboBoxModel structuralModel =
        (DefaultComboBoxModel)structural.getModel();
      for (int i=0; i<structuralModel.getSize(); i++)
      {
        if (struct.equalsIgnoreCase((String)structuralModel.getElementAt(i)))
        {
          structural.setSelectedIndex(i);
          break;
        }
      }
    }
    SortableListModel<String> availableListModel =
      auxiliary.getAvailableListModel();
    SortableListModel<String> selectedListModel =
      auxiliary.getSelectedListModel();
    availableListModel.addAll(selectedListModel.getData());
    selectedListModel.clear();

    for (String oc : value.getAuxiliary())
    {
      int index = -1;
      for (int i=0; i<availableListModel.getSize(); i++)
      {
        if (availableListModel.getElementAt(i).equalsIgnoreCase(oc))
        {
          index = i;
          break;
        }
      }
      if (index != -1)
      {
        oc = availableListModel.getElementAt(index);
        selectedListModel.add(oc);
        availableListModel.remove(oc);
      }
    }
    selectedListModel.fireContentsChanged(
        selectedListModel, 0, selectedListModel.getSize());
    availableListModel.fireContentsChanged(
        availableListModel, 0, availableListModel.getSize());
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return structural;
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    valueChanged = false;
    super.cancelClicked();
  }

  /**
   * Returns the object class value displayed by the panel.
   * @return the object class value displayed by the panel.
   */
  public ObjectClassValue getObjectClassValue()
  {
    return value;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    String struct = (String)  structural.getSelectedItem();
    TreeSet<String> aux = new TreeSet<String>();
    aux.addAll(auxiliary.getSelectedListModel().getData());
    aux.add("top");
    ObjectClassValue newValue = new ObjectClassValue(struct, aux);
    valueChanged = !newValue.equals(value);
    value = newValue;
    Utilities.getParentDialog(this).setVisible(false);
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_EDIT_OBJECTCLASS_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final Schema schema = ev.getNewDescriptor().getSchema();
    if (schema != null)
    {
      final SortedSet<String> auxiliaryOcs = new TreeSet<String>();
      final SortedSet<String> structuralOcs = new TreeSet<String>();
      for (ObjectClass oc : schema.getObjectClasses().values())
      {
        if (oc.getObjectClassType() == ObjectClassType.AUXILIARY)
        {
          if (!oc.getNameOrOID().equals("top"))
          {
            auxiliaryOcs.add(oc.getNameOrOID());
          }
        }
        else if (oc.getObjectClassType() == ObjectClassType.STRUCTURAL)
        {
          structuralOcs.add(oc.getNameOrOID());
        }
      }

      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          String currentStruct = (String)structural.getSelectedItem();

          SortedSet<String> currentAux;
          if (currentStruct != null)
          {
            currentAux = auxiliary.getSelectedListModel().getData();
          }
          else if (value != null)
          {
            // This is to handle the case where the schema is updated after
            // a value was set.
            currentStruct = value.getStructural();
            currentAux = value.getAuxiliary();
          }
          else
          {
            currentAux = new TreeSet<String>();
          }
          SortableListModel<String> availableListModel =
            auxiliary.getAvailableListModel();
          SortableListModel<String> selectedListModel =
            auxiliary.getSelectedListModel();
          DefaultComboBoxModel structuralModel =
            (DefaultComboBoxModel)structural.getModel();
          structuralModel.removeAllElements();
          availableListModel.clear();
          selectedListModel.clear();
          for (String oc : structuralOcs)
          {
            structuralModel.addElement(oc);
          }
          for (String oc : auxiliaryOcs)
          {
            availableListModel.add(oc);
          }
          if (currentStruct != null)
          {
            structural.setSelectedItem(currentStruct);
          }
          for (String oc : currentAux)
          {
            availableListModel.remove(oc);
            selectedListModel.add(oc);
          }
          selectedListModel.fireContentsChanged(
              selectedListModel, 0, selectedListModel.getSize());
          availableListModel.fireContentsChanged(
              availableListModel, 0, availableListModel.getSize());
          setEnabledOK(true);
        }
      });
    }
    else
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          setEnabledOK(false);
        }
      });
    }
  }

  /**
   * Returns <CODE>true</CODE> if the value changed and <CODE>false</CODE>
   * otherwise.
   * @return <CODE>true</CODE> if the value changed and <CODE>false</CODE>
   * otherwise.
   */
  public boolean valueChanged()
  {
    return valueChanged;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weighty = 0.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 2;
    addErrorPane(gbc);

    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    JLabel l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_STRUCTURAL_OBJECTCLASS_LABEL.get());
    add(l, gbc);
    gbc.gridx ++;
    gbc.insets.left = 10;
    gbc.anchor = GridBagConstraints.WEST;
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    structural = Utilities.createComboBox();
    structural.setModel(model);
    gbc.weightx = 1.0;
    add(structural, gbc);

    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_AUXILIARY_OBJECTCLASS_LABEL.get());
    add(l, gbc);
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    auxiliary = new AddRemovePanel<String>(String.class);
    gbc.insets.left = 30;
    add(auxiliary, gbc);
  }
}
