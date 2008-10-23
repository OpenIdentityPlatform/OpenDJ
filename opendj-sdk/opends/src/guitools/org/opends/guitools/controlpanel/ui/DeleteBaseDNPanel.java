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
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.DeleteBaseDNAndBackendTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.types.DN;

/**
 * The panel displayed when the user clicks on 'Delete Base DN...' in the
 * browse entries dialog.
 *
 */
public class DeleteBaseDNPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2182662824496761087L;

  /**
   * The list containing the base DNs.
   */
  protected JList list;

  /**
   * Label indicating that no element was found.
   */
  protected JLabel lNoElementsFound;

  /**
   * The main panel.
   */
  protected JPanel mainPanel;

  /**
   * Default constructor.
   *
   */
  public DeleteBaseDNPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_DELETE_BASE_DN_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return list;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }


  /**
   * Returns the no backend found label.
   * @return the no backend found label.
   */
  protected Message getNoElementsFoundLabel()
  {
    return INFO_CTRL_PANEL_NO_BASE_DNS_FOUND_LABEL.get();
  }

  /**
   * Returns the list label.
   * @return the list label.
   */
  protected Message getListLabel()
  {
    return INFO_CTRL_PANEL_SELECT_BASE_DNS_TO_DELETE.get();
  }

  /**
   * Updates the list of base DNs.
   * @param newElements the base DNs to be used to update the list.
   */
  protected void updateList(final Collection<?> newElements)
  {
    final DefaultListModel model = (DefaultListModel)list.getModel();
    boolean changed = newElements.size() != model.getSize();
    if (!changed)
    {
      int i = 0;
      for (Object newElement : newElements)
      {
        changed = !newElement.equals(model.getElementAt(i));
        if (changed)
        {
          break;
        }
        i++;
      }
    }
    if (changed)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          Object[] s = list.getSelectedValues();
          Set<Object> selected = new HashSet<Object>();
          if (s != null)
          {
            for (Object o : s)
            {
              selected.add(o);
            }
          }
          final DefaultListModel model = (DefaultListModel)list.getModel();
          model.clear();
          SortedSet<Integer> indices = new TreeSet<Integer>();
          int i = 0;
          for (Object newElement : newElements)
          {
            model.addElement(newElement);
            if (selected.contains(newElement))
            {
              indices.add(i);
            }
            i ++;
          }
          if (selected.size() > 0)
          {
            int[] indArray = new int[indices.size()];
            i = 0;
            for (Integer index : indices)
            {
              indArray[i] = index;
              i++;
            }
            list.setSelectedIndices(indArray);
          }
          checkVisibility();
        }
      });
    }
  }

  private void checkVisibility()
  {
    mainPanel.setVisible(list.getModel().getSize() > 0);
    lNoElementsFound.setVisible(list.getModel().getSize() == 0);
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    addErrorPane(gbc);

    mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setOpaque(false);
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mainPanel, gbc);

    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.NONE;
    lNoElementsFound = Utilities.createPrimaryLabel(getNoElementsFoundLabel());
    add(lNoElementsFound, gbc);
    lNoElementsFound.setVisible(false);

    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridwidth = 2;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    JLabel lBaseDNs =
      Utilities.createPrimaryLabel(getListLabel());
    mainPanel.add(lBaseDNs, gbc);
    gbc.insets.top = 5;
    list = new JList(new DefaultListModel());
    list.setCellRenderer(new CustomListCellRenderer(list));
    list.setVisibleRowCount(15);
    gbc.gridy ++;
    gbc.gridheight = 3;
    gbc.gridwidth = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    mainPanel.add(Utilities.createScrollPane(list), gbc);

    JButton selectAllButton = Utilities.createButton(
        INFO_CTRL_PANEL_SELECT_ALL_BUTTON.get());
    selectAllButton.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        int[] indices = new int[list.getModel().getSize()];
        for (int i=0 ; i<indices.length; i++)
        {
          indices[i] = i;
        }
        list.setSelectedIndices(indices);
      }
    });
    gbc.gridx ++;
    gbc.gridheight = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 5;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    mainPanel.add(selectAllButton, gbc);

    gbc.gridy ++;
    JButton unselectAllButton = Utilities.createButton(
        INFO_CTRL_PANEL_CLEAR_SELECTION_BUTTON.get());
    unselectAllButton.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        list.clearSelection();
      }
    });
    mainPanel.add(unselectAllButton, gbc);

    list.addListSelectionListener(new ListSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(ListSelectionEvent ev)
      {
        checkOKButtonEnable();
      }
    });

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets.top = 0;
    gbc.weighty = 1.0;
    mainPanel.add(Box.createVerticalGlue(), gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      list.clearSelection();
      checkVisibility();
    }
  }

  /**
   * {@inheritDoc}
   */
  protected void checkOKButtonEnable()
  {
    setEnabledOK(!list.isSelectionEmpty() && mainPanel.isVisible());
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();
    final SortedSet<DN> newElements = new TreeSet<DN>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (!backend.isConfigBackend())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          newElements.add(baseDN.getDn());
        }
      }
    }
    updateList(newElements);
    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_BASE_DN_DELETE.get());
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this), getTitle(), getInfo());
    Object[] dns = list.getSelectedValues();
    ArrayList<BaseDNDescriptor> baseDNsToDelete =
      new ArrayList<BaseDNDescriptor>();
    for (Object o : dns)
    {
      DN dn = (DN)o;
      boolean found = false;
      for (BackendDescriptor backend :
        getInfo().getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (baseDN.getDn().equals(dn))
          {
            baseDNsToDelete.add(baseDN);
            found = true;
            break;
          }
        }
        if (found)
        {
          break;
        }
      }
    }
    DeleteBaseDNAndBackendTask newTask = new DeleteBaseDNAndBackendTask(
        getInfo(), progressDialog, new HashSet<BackendDescriptor>(),
        baseDNsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      Message confirmationMessage = getConfirmationMessage(baseDNsToDelete);
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          confirmationMessage))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_BASE_DNS_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETING_BASE_DNS_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_BASE_DNS_SUCCESSFUL.get(),
            ERR_CTRL_PANEL_DELETING_BASE_DNS_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_BASE_DNS_ERROR_DETAILS.get(),
            null,
            progressDialog);
        progressDialog.setVisible(true);
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  private Message getConfirmationMessage(
      Collection<BaseDNDescriptor> baseDNsToDelete)
  {
    MessageBuilder mb = new MessageBuilder();
    Map<String, Set<BaseDNDescriptor>> hmBackends =
      new HashMap<String, Set<BaseDNDescriptor>>();
    for (BaseDNDescriptor baseDN : baseDNsToDelete)
    {
      String backendID = baseDN.getBackend().getBackendID();
      Set<BaseDNDescriptor> set = hmBackends.get(backendID);
      if (set == null)
      {
        set = new HashSet<BaseDNDescriptor>();
        hmBackends.put(backendID, set);
      }
      set.add(baseDN);
    }
    ArrayList<String> indirectBackendsToDelete = new ArrayList<String>();
    for (Set<BaseDNDescriptor> set : hmBackends.values())
    {
      BackendDescriptor backend = set.iterator().next().getBackend();
      if (set.size() == backend.getBaseDns().size())
      {
        // All of the suffixes must be deleted.
        indirectBackendsToDelete.add(backend.getBackendID());
      }
    }
    mb.append(INFO_CTRL_PANEL_CONFIRMATION_DELETE_BASE_DNS_DETAILS.get());
    for (BaseDNDescriptor baseDN : baseDNsToDelete)
    {
      mb.append("<br> - "+baseDN.getDn());
    }
    if (indirectBackendsToDelete.size() > 0)
    {
      mb.append("<br><br>");
      mb.append(
          INFO_CTRL_PANEL_CONFIRMATION_DELETE_BASE_DNS_INDIRECT_DETAILS.get());
      for (String backendID : indirectBackendsToDelete)
      {
        mb.append("<br> - "+backendID);
      }
    }
    mb.append("<br><br>");
    mb.append(INFO_CTRL_PANEL_DO_YOU_WANT_TO_CONTINUE.get());
    return mb.toMessage();
  }
}
