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
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * This component displays three list (one available list and two selected
 * lists) with some buttons to move the components of one list to the other.
 *
 * @param <T> the type of the objects in the list.
 */
public class DoubleAddRemovePanel<T> extends JPanel
{
  private static final long serialVersionUID = 6881453848780359594L;
  private final SortableListModel<T> availableListModel;
  private final SortableListModel<T> selectedListModel1;
  private final SortableListModel<T> selectedListModel2;
  private final JLabel selectedLabel1;
  private final JLabel selectedLabel2;
  private final JLabel availableLabel;
  private final JButton add1;
  private final JButton remove1;
  private final JButton add2;
  private final JButton remove2;
  private final JButton addAll1;
  private final JButton removeAll1;
  private final JButton addAll2;
  private final JButton removeAll2;
  private final JScrollPane availableScroll;
  private final JScrollPane selectedScroll1;
  private final JScrollPane selectedScroll2;
  private final JList availableList;
  private final JList<T> selectedList1;
  private final JList<T> selectedList2;
  private final Class<T> theClass;
  private final Collection<T> unmovableItems = new ArrayList<>();
  private boolean ignoreListEvents;

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
   * Constructor of the double add remove panel allowing the user to provide
   * some display options.
   * The class is required to avoid warnings in compilation.
   * @param displayOptions the display options.
   * @param theClass the class of the objects in the panel.
   */
  public DoubleAddRemovePanel(int displayOptions, Class<T> theClass)
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
    selectedLabel1 = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_SELECTED_LABEL.get());
    add(selectedLabel1, gbc);
    gbc.gridy ++;

    ListDataListener listDataListener = new ListDataListener()
    {
      @Override
      public void intervalRemoved(ListDataEvent ev)
      {
        listSelectionChanged();
      }

      @Override
      public void intervalAdded(ListDataEvent ev)
      {
        listSelectionChanged();
      }

      @Override
      public void contentsChanged(ListDataEvent ev)
      {
        listSelectionChanged();
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
              addClicked(selectedListModel1);
            }
          }
          else if (e.getSource() == selectedList1)
          {
            if (selectedList1.getSelectedValue() != null)
            {
              remove1Clicked();
            }
          }
          else if (e.getSource() == selectedList2
              && selectedList2.getSelectedValue() != null)
          {
            remove2Clicked();
          }
        }
      }
    };


    availableListModel = new SortableListModel<>();
    availableListModel.addListDataListener(listDataListener);
    availableList = new JList<>();
    availableList.setModel(availableListModel);
    availableList.setVisibleRowCount(15);
    availableList.addMouseListener(doubleClickListener);

    selectedListModel1 = new SortableListModel<>();
    selectedListModel1.addListDataListener(listDataListener);
    selectedList1 = new JList<>();
    selectedList1.setModel(selectedListModel1);
    selectedList1.setVisibleRowCount(7);
    selectedList1.addMouseListener(doubleClickListener);

    selectedListModel2 = new SortableListModel<>();
    selectedListModel2.addListDataListener(listDataListener);
    selectedList2 = new JList<>();
    selectedList2.setModel(selectedListModel2);
    selectedList2.setVisibleRowCount(7);
    selectedList2.addMouseListener(doubleClickListener);

    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.gridheight = 7;
    displayOptions &= DISPLAY_ADD_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight += 2;
    }
    // FIXME how can this be any different than 0? Ditto everywhere else down below
    displayOptions &= DISPLAY_REMOVE_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight += 2;
    }
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
    add1 = Utilities.createButton(INFO_CTRL_PANEL_ADDREMOVE_ADD_BUTTON.get());
    add1.setOpaque(false);
    add1.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        addClicked(selectedListModel1);
      }
    });
    gbc.insets = new Insets(5, 5, 0, 5);
    add(add1, gbc);

    displayOptions &= DISPLAY_ADD_ALL;
    if (displayOptions != 0)
    {
      addAll1 = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_ADD_ALL_BUTTON.get());
      addAll1.setOpaque(false);
      addAll1.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          moveAll(availableListModel, selectedListModel1);
        }
      });
      gbc.gridy ++;
      add(addAll1, gbc);
    }
    else
    {
      addAll1 = null;
    }

    remove1 = Utilities.createButton(
        INFO_CTRL_PANEL_ADDREMOVE_REMOVE_BUTTON.get());
    remove1.setOpaque(false);
    remove1.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        remove1Clicked();
      }
    });
    gbc.gridy ++;
    gbc.insets.top = 10;
    add(remove1, gbc);

    displayOptions &= DISPLAY_REMOVE_ALL;
    if (displayOptions != 0)
    {
      removeAll1 = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_REMOVE_ALL_BUTTON.get());
      removeAll1.setOpaque(false);
      removeAll1.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          moveAll(selectedListModel1, availableListModel);
        }
      });
      gbc.gridy ++;
      gbc.insets.top = 5;
      add(removeAll1, gbc);
    }
    else
    {
      removeAll1 = null;
    }


    gbc.weighty = 1.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridy ++;
    gbc.gridheight = 1;
    gbc.fill = GridBagConstraints.VERTICAL;
    add(Box.createVerticalGlue(), gbc);

    gbc.gridy += 2;
    gbc.gridx = 1;
    gbc.gridheight = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add2 = Utilities.createButton(INFO_CTRL_PANEL_ADDREMOVE_ADD_BUTTON.get());
    add2.setOpaque(false);
    add2.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        addClicked(selectedListModel2);
      }
    });
    gbc.insets = new Insets(5, 5, 0, 5);
    add(add2, gbc);

    displayOptions &= DISPLAY_ADD_ALL;
    if (displayOptions != 0)
    {
      addAll2 = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_ADD_ALL_BUTTON.get());
      addAll2.setOpaque(false);
      addAll2.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          moveAll(availableListModel, selectedListModel2);
        }
      });
      gbc.gridy ++;
      add(addAll2, gbc);
    }
    else
    {
      addAll2 = null;
    }

    remove2 = Utilities.createButton(
        INFO_CTRL_PANEL_ADDREMOVE_REMOVE_BUTTON.get());
    remove2.setOpaque(false);
    remove2.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        remove2Clicked();
      }
    });
    gbc.gridy ++;
    gbc.insets.top = 10;
    add(remove2, gbc);

    displayOptions &= DISPLAY_REMOVE_ALL;
    if (displayOptions != 0)
    {
      removeAll2 = Utilities.createButton(
          INFO_CTRL_PANEL_ADDREMOVE_REMOVE_ALL_BUTTON.get());
      removeAll2.setOpaque(false);
      removeAll2.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          moveAll(selectedListModel2, availableListModel);
        }
      });
      gbc.gridy ++;
      gbc.insets.top = 5;
      add(removeAll2, gbc);
    }
    else
    {
      removeAll2 = null;
    }


    gbc.weighty = 1.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridy ++;
    gbc.gridheight = 1;
    gbc.fill = GridBagConstraints.VERTICAL;
    add(Box.createVerticalGlue(), gbc);

    gbc.weightx = 1.0;
    gbc.insets = new Insets(5, 0, 0, 0);
    gbc.gridheight = 3;
    displayOptions &= DISPLAY_ADD_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight ++;
    }
    displayOptions &= DISPLAY_REMOVE_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight ++;
    }
    gbc.gridy = listGridY;
    gbc.gridx = 2;
    gbc.fill = GridBagConstraints.BOTH;
    selectedScroll1 = Utilities.createScrollPane(selectedList1);
    gbc.weighty = 1.0;
    add(selectedScroll1, gbc);

    gbc.gridy += gbc.gridheight;
    gbc.gridheight = 1;
    gbc.weighty = 0.0;
    gbc.insets.top = 10;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    selectedLabel2 = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_SELECTED_LABEL.get());
    add(selectedLabel2, gbc);

    gbc.weightx = 1.0;
    gbc.insets = new Insets(5, 0, 0, 0);
    gbc.gridheight = 3;
    displayOptions &= DISPLAY_ADD_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight ++;
    }
    displayOptions &= DISPLAY_REMOVE_ALL;
    if (displayOptions != 0)
    {
      gbc.gridheight ++;
    }
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    selectedScroll2 = Utilities.createScrollPane(selectedList2);
    gbc.weighty = 1.0;
    add(selectedScroll2, gbc);


    selectedList1.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    ListSelectionListener listener = new ListSelectionListener()
    {
      @Override
      public void valueChanged(ListSelectionEvent ev)
      {
        listSelectionChanged();
      }
    };
    selectedList1.getSelectionModel().addListSelectionListener(listener);
    selectedList2.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    selectedList2.getSelectionModel().addListSelectionListener(listener);
    availableList.getSelectionModel().setSelectionMode(
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    availableList.getSelectionModel().addListSelectionListener(listener);

    add1.setEnabled(false);
    remove1.setEnabled(false);

    add2.setEnabled(false);
    remove2.setEnabled(false);

    // Set preferred size for the scroll panes.
    Component comp =
      availableList.getCellRenderer().getListCellRendererComponent(
          availableList,
        "The cell that we want to display", 0, true, true);
    Dimension d = new Dimension(comp.getPreferredSize().width,
        availableScroll.getPreferredSize().height);
    availableScroll.setPreferredSize(d);
    d = new Dimension(comp.getPreferredSize().width,
        selectedScroll1.getPreferredSize().height);
    selectedScroll1.setPreferredSize(d);
    selectedScroll2.setPreferredSize(d);
  }

  @Override
  public void setEnabled(boolean enable)
  {
    super.setEnabled(enable);

    selectedLabel1.setEnabled(enable);
    selectedLabel2.setEnabled(enable);
    availableLabel.setEnabled(enable);
    availableList.setEnabled(enable);
    selectedList1.setEnabled(enable);
    selectedList2.setEnabled(enable);
    availableScroll.setEnabled(enable);
    selectedScroll2.setEnabled(enable);
    selectedScroll2.setEnabled(enable);

    listSelectionChanged();
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
   * Returns the first selected label contained in the panel.
   * @return the first selected label contained in the panel.
   */
  public JLabel getSelectedLabel1()
  {
    return selectedLabel1;
  }

  /**
   * Returns the list of elements in the first selected list.
   * @return the list of elements in the first selected list.
   */
  public SortableListModel<T> getSelectedListModel1()
  {
    return selectedListModel1;
  }

  /**
   * Returns the second selected label contained in the panel.
   * @return the second selected label contained in the panel.
   */
  public JLabel getSelectedLabel2()
  {
    return selectedLabel2;
  }

  /**
   * Returns the list of elements in the second selected list.
   * @return the list of elements in the second selected list.
   */
  public SortableListModel<T> getSelectedListModel2()
  {
    return selectedListModel2;
  }

  private void listSelectionChanged()
  {
    if (ignoreListEvents)
    {
      return;
    }
    ignoreListEvents = true;

    JList[] lists = {availableList, selectedList1, selectedList2};
    for (JList<T> list : lists)
    {
      for (T element : unmovableItems)
      {
        int[] indexes = list.getSelectedIndices();
        if (indexes != null)
        {
          for (int index : indexes)
          {
            // This check is necessary since the selection model might not
            // be in sync with the list model.
            if (selectionAndListModelAreInSync(list, element, index))
            {
              list.getSelectionModel().removeIndexInterval(index, index);
            }
          }
        }
      }
    }

    ignoreListEvents = false;
    add1.setEnabled(isEnabled(availableList, availableListModel));
    add2.setEnabled(add1.isEnabled());
    remove1.setEnabled(isEnabled(selectedList1, selectedListModel1));
    remove2.setEnabled(isEnabled(selectedList2, selectedListModel2));

    if (addAll1 != null)
    {
      addAll1.setEnabled(isEnabled(availableListModel));
      addAll2.setEnabled(addAll1.isEnabled());
    }
    if (removeAll1 != null)
    {
      removeAll1.setEnabled(isEnabled(selectedListModel1));
    }
    if (removeAll2 != null)
    {
      removeAll2.setEnabled(isEnabled(selectedListModel2));
    }
  }

  private boolean selectionAndListModelAreInSync(JList<T> list, T element, int index)
  {
    final ListModel<T> listModel = list.getModel();
    return index < listModel.getSize()
        && listModel.getElementAt(index).equals(element);
  }

  private boolean isEnabled(JList<T> list, SortableListModel<T> model)
  {
    int index = list.getSelectedIndex();
    return index != -1 && index < model.getSize() && isEnabled();
  }

  private boolean isEnabled(SortableListModel<T> model)
  {
    boolean onlyUnmovable = unmovableItems.containsAll(model.getData());
    return model.getSize() > 0 && isEnabled() && !onlyUnmovable;
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
   * Returns the first selected list.
   * @return the first selected list.
   */
  public JList<T> getSelectedList1()
  {
    return selectedList1;
  }

  /**
   * Returns the second selected list.
   * @return the second selected list.
   */
  public JList<T> getSelectedList2()
  {
    return selectedList2;
  }

  private void addClicked(SortableListModel<T> selectedListModel)
  {
    for (Object selectedObject : availableList.getSelectedValuesList())
    {
      T value = DoubleAddRemovePanel.this.theClass.cast(selectedObject);
      selectedListModel.add(value);
      availableListModel.remove(value);
    }
    selectedListModel.fireContentsChanged(selectedListModel, 0, selectedListModel.getSize());
    availableListModel.fireContentsChanged(availableListModel, 0, availableListModel.getSize());
  }

  private void remove1Clicked()
  {
    removeClicked(selectedListModel1, selectedList1);
  }

  private void remove2Clicked()
  {
    removeClicked(selectedListModel2, selectedList2);
  }

  private void removeClicked(SortableListModel<T> selectedListModel, JList<T> selectedList)
  {
    for (T value : selectedList.getSelectedValuesList())
    {
      availableListModel.add(value);
      selectedListModel.remove(value);
    }
    selectedListModel.fireContentsChanged(selectedListModel, 0, selectedListModel.getSize());
    availableListModel.fireContentsChanged(availableListModel, 0, availableListModel.getSize());
  }

  /**
   * Sets the list of items that cannot be moved from one list to the others.
   * @param unmovableItems the list of items that cannot be moved from one
   * list to the others.
   */
  public void setUnmovableItems(Collection<T> unmovableItems)
  {
    this.unmovableItems.clear();
    this.unmovableItems.addAll(unmovableItems);
  }

  private void moveAll(SortableListModel<T> fromModel,
      SortableListModel<T> toModel)
  {
    Collection<T> toKeep = fromModel.getData();
    toKeep.retainAll(unmovableItems);
    Collection<T> toMove = fromModel.getData();
    toMove.removeAll(unmovableItems);
    toModel.addAll(toMove);
    fromModel.clear();
    fromModel.addAll(toKeep);
    fromModel.fireContentsChanged(selectedListModel1, 0,
        selectedListModel1.getSize());
    toModel.fireContentsChanged(availableListModel, 0,
        availableListModel.getSize());
  }
}
