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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.components;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * This component displays two list (available list and selected list) with
 * some buttons to move the components of one list to the other.
 *
 * @param <T> the type of the objects in the list.
 */
public class AddRemovePanel<T> extends JPanel
{
  private static final long serialVersionUID = 461800576153651284L;
  private SortableListModel<T> availableListModel;
  private SortableListModel<T> selectedListModel;
  private JLabel selectedLabel;
  private JLabel availableLabel;
  private JButton add;
  private JButton remove;
  private JButton addAll;
  private JButton removeAll;
  private JScrollPane availableScroll;
  private JScrollPane selectedScroll;
  private JList availableList;
  private JList selectedList;
  private Class<T> theClass;

  /**
   * Mask used as display option.  If the provided display options contain
   * this mask, the panel will display the remove all button.
   */
  private static final int DISPLAY_REMOVE_ALL = 0x001;

  /**
   * Mask used as display option.  If the provided display options contain
   * this mask, the panel will display the add all button.
   */
  private static final int DISPLAY_ADD_ALL = 0x010;


  /**
   * Constructor of the default add remove panel (including 'Add All' and
   * 'Remove All' buttons).
   * The class is required to avoid warnings in compilation.
   * @param theClass the class of the objects in the panel.
   */
  public AddRemovePanel(Class<T> theClass)
  {
    this(DISPLAY_REMOVE_ALL | DISPLAY_ADD_ALL, theClass);
  }

  /**
   * Constructor of the add remove panel allowing the user to provide some
   * display options.
   * The class is required to avoid warnings in compilation.
   * @param displayOptions the display options.
   * @param theClass the class of the objects in the panel.
   */
  private AddRemovePanel(int displayOptions, Class<T> theClass)
  {
    super(new GridBagLayout());
    setOpaque(false);
    this.theClass = theClass;
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 1;
    gbc.gridheight = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;

    availableLabel = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_AVAILABLE_LABEL.get());
    add(availableLabel, gbc);
    gbc.gridx = 2;
    selectedLabel = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_SELECTED_LABEL.get());
    add(selectedLabel, gbc);
    gbc.gridy ++;

    ListDataListener listDataListener = new ListDataListener()
    {
      @Override
      public void intervalRemoved(ListDataEvent ev)
      {
        updateButtonEnabling();
      }

      @Override
      public void intervalAdded(ListDataEvent ev)
      {
        updateButtonEnabling();
      }

      @Override
      public void contentsChanged(ListDataEvent ev)
      {
        updateButtonEnabling();
      }
    };
    MouseAdapter doubleClickListener = new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (isEnabled() && e.getClickCount() == 2)
        {
          if (e.getSource() == availableList)
          {
            if (availableList.getSelectedValue() != null)
            {
              addClicked();
            }
          }
          else if (e.getSource() == selectedList)
          {
            if (selectedList.getSelectedValue() != null)
            {
              removeClicked();
            }
          }
        }
      }
    };


    availableListModel = new SortableListModel<>();
    availableListModel.addListDataListener(listDataListener);
    availableList = new JList();
    availableList.setModel(availableListModel);
    availableList.setVisibleRowCount(15);
    availableList.addMouseListener(doubleClickListener);

    selectedListModel = new SortableListModel<>();
    selectedListModel.addListDataListener(listDataListener);
    selectedList = new JList();
    selectedList.setModel(selectedListModel);
    selectedList.setVisibleRowCount(15);
    selectedList.addMouseListener(doubleClickListener);

    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.gridheight = 3;
    if ((displayOptions & DISPLAY_ADD_ALL) != 0)
    {
      gbc.gridheight ++;
    }
    if ((displayOptions & DISPLAY_REMOVE_ALL) != 0)
    {
      gbc.gridheight ++;
    }
    int listGridHeight = gbc.gridheight;
    int listGridY = gbc.gridy;
    gbc.gridx = 0;
    gbc.insets.top = 5;
    availableScroll = Utilities.createScrollPane(availableList);
    gbc.fill = GridBagConstraints.BOTH;
    add(availableScroll, gbc);

    gbc.gridx = 1;
    gbc.gridheight = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add = Utilities.createButton(INFO_CTRL_PANEL_ADDREMOVE_ADD_BUTTON.get());
    add.setOpaque(false);
    add.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        addClicked();
      }
    });
    gbc.insets = new Insets(5, 5, 0, 5);
    add(add, gbc);

    if ((displayOptions & DISPLAY_ADD_ALL) != 0)
    {
      addAll = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_ADD_ALL_BUTTON.get());
      addAll.setOpaque(false);
      addAll.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          selectedListModel.addAll(availableListModel.getData());
          availableListModel.clear();
          fireContentsChanged(selectedListModel);
          fireContentsChanged(availableListModel);
        }
      });
      gbc.gridy ++;
      add(addAll, gbc);
    }

    remove = Utilities.createButton(
        INFO_CTRL_PANEL_ADDREMOVE_REMOVE_BUTTON.get());
    remove.setOpaque(false);
    remove.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        removeClicked();
      }
    });
    gbc.gridy ++;
    gbc.insets.top = 10;
    add(remove, gbc);

    if ((displayOptions & DISPLAY_REMOVE_ALL) != 0)
    {
      removeAll = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_REMOVE_ALL_BUTTON.get());
      removeAll.setOpaque(false);
      removeAll.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          availableListModel.addAll(selectedListModel.getData());
          selectedListModel.clear();
          fireContentsChanged(selectedListModel);
          fireContentsChanged(availableListModel);
        }
      });
      gbc.gridy ++;
      gbc.insets.top = 5;
      add(removeAll, gbc);
    }


    gbc.weighty = 1.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.VERTICAL;
    add(Box.createVerticalGlue(), gbc);

    gbc.weightx = 1.0;
    gbc.insets = new Insets(5, 0, 0, 0);
    gbc.gridheight = listGridHeight;
    gbc.gridy = listGridY;
    gbc.gridx = 2;
    gbc.fill = GridBagConstraints.BOTH;
    selectedScroll = Utilities.createScrollPane(selectedList);
    add(selectedScroll, gbc);

    selectedList.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    ListSelectionListener listener = new ListSelectionListener()
    {
      @Override
      public void valueChanged(ListSelectionEvent ev)
      {
        updateButtonEnabling();
      }
    };
    selectedList.getSelectionModel().addListSelectionListener(listener);
    availableList.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    availableList.getSelectionModel().addListSelectionListener(listener);

    add.setEnabled(false);
    remove.setEnabled(false);

    // Set preferred size for the scroll panes.
    Component comp =
      availableList.getCellRenderer().getListCellRendererComponent(
          availableList,
        "The cell that we want to display", 0, true, true);
    Dimension d = new Dimension(comp.getPreferredSize().width,
        availableScroll.getPreferredSize().height);
    availableScroll.setPreferredSize(d);
    selectedScroll.setPreferredSize(d);
  }

  /**
   * Enables the state of the components in the panel.
   * @param enable whether to enable the components in the panel or not.
   */
  @Override
  public void setEnabled(boolean enable)
  {
    super.setEnabled(enable);

    selectedLabel.setEnabled(enable);
    availableLabel.setEnabled(enable);
    availableList.setEnabled(enable);
    selectedList.setEnabled(enable);
    availableScroll.setEnabled(enable);
    selectedScroll.setEnabled(enable);

    updateButtonEnabling();
  }

  /**
   * Returns the available label contained in the panel.
   * @return the available label contained in the panel.
   */
  public JLabel getAvailableLabel()
  {
    return availableLabel;
  }

  /**
   * Returns the list of elements in the available list.
   * @return the list of elements in the available list.
   */
  public SortableListModel<T> getAvailableListModel()
  {
    return availableListModel;
  }

  /**
   * Returns the selected label contained in the panel.
   * @return the selected label contained in the panel.
   */
  public JLabel getSelectedLabel()
  {
    return selectedLabel;
  }

  /**
   * Returns the list of elements in the selected list.
   * @return the list of elements in the selected list.
   */
  public SortableListModel<T> getSelectedListModel()
  {
    return selectedListModel;
  }

  private void updateButtonEnabling()
  {
    int index = availableList.getSelectedIndex();
    add.setEnabled(index != -1 &&
        index <availableListModel.getSize() && isEnabled());
    index = selectedList.getSelectedIndex();
    remove.setEnabled(index != -1 &&
        index <selectedListModel.getSize() && isEnabled());

    if (addAll != null)
    {
      addAll.setEnabled(availableListModel.getSize() > 0 && isEnabled());
    }
    if (removeAll != null)
    {
      removeAll.setEnabled(selectedListModel.getSize() > 0 && isEnabled());
    }
  }

  /**
   * Returns the available list.
   * @return the available list.
   */
  public JList getAvailableList()
  {
    return availableList;
  }

  /**
   * Returns the selected list.
   * @return the selected list.
   */
  public JList getSelectedList()
  {
    return selectedList;
  }

  private void addClicked()
  {
    List<?> selectedObjects = availableList.getSelectedValuesList();
    for (Object selectedObject : selectedObjects)
    {
      T value = AddRemovePanel.this.theClass.cast(selectedObject);
      selectedListModel.add(value);
      availableListModel.remove(value);
    }
    fireContentsChanged(selectedListModel);
    fireContentsChanged(availableListModel);
  }

  private void removeClicked()
  {
    List<?> selectedObjects = selectedList.getSelectedValuesList();
    for (Object selectedObject : selectedObjects)
    {
      T value = AddRemovePanel.this.theClass.cast(selectedObject);
      availableListModel.add(value);
      selectedListModel.remove(value);
    }
    fireContentsChanged(selectedListModel);
    fireContentsChanged(availableListModel);
  }

  private void fireContentsChanged(SortableListModel<T> listModel)
  {
    listModel.fireContentsChanged(listModel, 0, listModel.getSize());
  }
}
