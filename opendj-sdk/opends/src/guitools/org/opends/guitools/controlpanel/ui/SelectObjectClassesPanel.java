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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JEditorPane;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.ui.renderer.
 SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * This is a class where the user can choose from a list of available object
 * classes one or more object classes.
 *
 */
public class SelectObjectClassesPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 1230982500028334L;
  private AddRemovePanel<ObjectClass> addRemove =
    new AddRemovePanel<ObjectClass>(ObjectClass.class);
  private Set<ObjectClass> toExclude = new HashSet<ObjectClass>();
  private Schema schema;
  private boolean isCanceled = true;

  /**
   * Default constructor of this panel.
   */
  public SelectObjectClassesPanel()
  {
    createLayout();
  }

  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    JEditorPane instructions = Utilities.makePlainTextPane(
        INFO_CTRL_PANEL_SUPERIOR_OBJECTCLASSES_INSTRUCTIONS.get().toString(),
        ColorAndFontConstants.defaultFont);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(instructions, gbc);
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    addRemove.getAvailableLabel().setText(
        INFO_CTRL_PANEL_ADDREMOVE_AVAILABLE_OBJECTCLASSES.get().toString());
    addRemove.getSelectedLabel().setText(
        INFO_CTRL_PANEL_ADDREMOVE_SELECTED_OBJECTCLASSES.get().toString());

    Comparator<ObjectClass> comparator = new Comparator<ObjectClass>()
    {
      /**
       * {@inheritDoc}
       */
      public int compare(ObjectClass oc1, ObjectClass oc2)
      {
        return oc1.getNameOrOID().toLowerCase().compareTo(
            oc2.getNameOrOID().toLowerCase());
      }
    };
    addRemove.getAvailableListModel().setComparator(comparator);
    addRemove.getSelectedListModel().setComparator(comparator);
    SchemaElementComboBoxCellRenderer renderer =
      new SchemaElementComboBoxCellRenderer(addRemove.getAvailableList());
    addRemove.getAvailableList().setCellRenderer(renderer);
    renderer =
      new SchemaElementComboBoxCellRenderer(addRemove.getSelectedList());
    addRemove.getSelectedList().setCellRenderer(renderer);
    gbc.insets.top = 10;
    add(addRemove, gbc);
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return addRemove;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_SUPERIOR_OBJECTCLASSES_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    isCanceled = true;
    Set<ObjectClass> selectedObjectClasses =
      addRemove.getSelectedListModel().getData();
    if (selectedObjectClasses.isEmpty())
    {
      displayErrorMessage(INFO_CTRL_PANEL_ERROR_DIALOG_TITLE.get(),
          INFO_CTRL_PANEL_ERROR_NO_SUPERIOR_SELECTED.get());
    }
    else
    {
      isCanceled = false;
      closeClicked();
    }
  }

  /**
   * Returns whether this dialog has been canceled or not.
   * @return whether this dialog has been canceled or not.
   */
  public boolean isCanceled()
  {
    return isCanceled;
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      isCanceled = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Returns the selected object classes.
   * @return the selected object classes.
   */
  public Set<ObjectClass> getSelectedObjectClasses()
  {
    return addRemove.getSelectedListModel().getData();
  }

  /**
   * Sets the selected object classes.
   * @param selectedObjectClasses the selected object classes.
   */
  public void setSelectedObjectClasses(Set<ObjectClass> selectedObjectClasses)
  {
    Set<ObjectClass> toAdd = new HashSet<ObjectClass>();
    Set<ObjectClass> previouslySelected =
      addRemove.getSelectedListModel().getData();
    for (ObjectClass oc : previouslySelected)
    {
      if (!selectedObjectClasses.contains(oc))
      {
        addRemove.getSelectedListModel().remove(oc);
        toAdd.add(oc);
      }
    }

    addRemove.getAvailableListModel().addAll(toAdd);

    for (ObjectClass oc : selectedObjectClasses)
    {
      if (!previouslySelected.contains(oc))
      {
        addRemove.getSelectedListModel().add(oc);
      }
      addRemove.getAvailableListModel().remove(oc);
    }
    fireAddRemoveNotifications();
  }

  /**
   * Sets the list of object classes that this panel should not display
   * (mainly used to not display the object class for which we are editing
   * the superior object classes).
   * @param toExclude the list of object classes to exclude.
   */
  public void setObjectClassesToExclude(Set<ObjectClass> toExclude)
  {
    this.toExclude.clear();
    this.toExclude.addAll(toExclude);

    updateWithSchema(schema);
    fireAddRemoveNotifications();
  }

  /**
   * Sets the schema to be used by this panel.
   * @param schema the schema to be used by this panel.
   */
  public void setSchema(Schema schema)
  {
    updateWithSchema(schema);
    fireAddRemoveNotifications();
  }

  private void updateWithSchema(Schema schema)
  {
    ArrayList<ObjectClass> allOcs = new ArrayList<ObjectClass>();
    for (String key : schema.getObjectClasses().keySet())
    {
      ObjectClass oc = schema.getObjectClass(key);
      if (!toExclude.contains(oc))
      {
        allOcs.add(oc);
      }
    }

    for (ObjectClass oc : addRemove.getSelectedListModel().getData())
    {
      if (!allOcs.contains(oc))
      {
        addRemove.getSelectedListModel().remove(oc);
      }
      else
      {
        allOcs.remove(oc);
      }
    }

    addRemove.getAvailableListModel().clear();
    addRemove.getAvailableListModel().addAll(allOcs);

    this.schema = schema;
  }

  /**
   * Returns the list of object classes that this panel will not display.
   * @return the list of object classes that this panel will not display.
   */
  public Set<ObjectClass> getObjectClassToExclude()
  {
    return Collections.unmodifiableSet(toExclude);
  }

  private void fireAddRemoveNotifications()
  {
    addRemove.getSelectedListModel().fireContentsChanged(this, 0,
        addRemove.getSelectedListModel().getSize() - 1);
    addRemove.getAvailableListModel().fireContentsChanged(this, 0,
        addRemove.getAvailableListModel().getSize() - 1);
  }
}
