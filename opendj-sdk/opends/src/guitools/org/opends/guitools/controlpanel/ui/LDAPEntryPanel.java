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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.EntryReadErrorEvent;
import org.opends.guitools.controlpanel.event.EntryReadEvent;
import org.opends.guitools.controlpanel.event.EntryReadListener;
import org.opends.guitools.controlpanel.event.LDAPEntryChangedEvent;
import org.opends.guitools.controlpanel.event.LDAPEntryChangedListener;
import org.opends.guitools.controlpanel.task.DeleteEntryTask;
import org.opends.guitools.controlpanel.task.ModifyEntryTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.Constants;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.OpenDsException;

/**
 * This is the panel that contains all the different views to display an entry.
 *
 */
public class LDAPEntryPanel extends StatusGenericPanel
implements EntryReadListener
{
  private static final long serialVersionUID = -6608246173472437830L;
  private JButton saveChanges;
  private JButton delete;
  private JPanel mainPanel;
  private CardLayout cardLayout;

  private ErrorSearchingEntryPanel errorSearchingPanel;
  private LDIFViewEntryPanel ldifEntryPanel;
  private TableViewEntryPanel tableEntryPanel;
  private SimplifiedViewEntryPanel simplifiedEntryPanel;

  private ViewEntryPanel displayedEntryPanel;

  private CustomSearchResult searchResult;
  private BrowserController controller;
  private TreePath treePath;

  private ModifyEntryTask newTask;

  private final String NOTHING_SELECTED = "Nothing Selected";
  private final String MULTIPLE_SELECTED = "Multiple Selected";
  private final String LDIF_VIEW = "LDIF View";
  private final String ATTRIBUTE_VIEW = "Attribute View";
  private final String SIMPLIFIED_VIEW = "Simplified View";
  private final String ERROR_SEARCHING = "Error Searching";

  private View view = View.SIMPLIFIED_VIEW;

  /**
   * The different views that we have to display an LDAP entry.
   *
   */
  public enum View
  {
    /**
     * Simplified view.
     */
    SIMPLIFIED_VIEW,
    /**
     * Attribute view (contained in a table).
     */
    ATTRIBUTE_VIEW,
    /**
     * LDIF view (text based).
     */
    LDIF_VIEW
  };

  /**
   * Default constructor.
   *
   */
  public LDAPEntryPanel()
  {
    super();
    createLayout();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    cardLayout = new CardLayout();
    mainPanel = new JPanel(cardLayout);
    mainPanel.setOpaque(false);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mainPanel, gbc);
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.weighty = 0.0;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.NONE;
    delete = Utilities.createButton(INFO_CTRL_PANEL_DELETE_ENTRY_BUTTON.get());
    delete.setOpaque(false);
    add(delete, gbc);
    delete.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        deleteEntry();
      }
    });

    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridx ++;
    saveChanges =
      Utilities.createButton(INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
    saveChanges.setOpaque(false);
    add(saveChanges, gbc);
    saveChanges.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        saveChanges(true);
      }
    });

    Border border = new EmptyBorder(10, 10, 10, 10);

    NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();
    noEntryPanel.setMessage(INFO_CTRL_PANEL_NO_ENTRY_SELECTED_LABEL.get());
    Utilities.setBorder(noEntryPanel, border);
    mainPanel.add(noEntryPanel, NOTHING_SELECTED);

    NoItemSelectedPanel multipleEntryPanel = new NoItemSelectedPanel();
    multipleEntryPanel.setMessage(
        INFO_CTRL_PANEL_MULTIPLE_ENTRIES_SELECTED_LABEL.get());
    Utilities.setBorder(multipleEntryPanel, border);
    mainPanel.add(multipleEntryPanel, MULTIPLE_SELECTED);

    errorSearchingPanel = new ErrorSearchingEntryPanel();
    if (errorSearchingPanel.requiresBorder())
    {
      Utilities.setBorder(multipleEntryPanel, border);
    }
    mainPanel.add(errorSearchingPanel, ERROR_SEARCHING);

    LDAPEntryChangedListener listener = new LDAPEntryChangedListener()
    {
      /**
       * {@inheritDoc}
       */
      public void entryChanged(LDAPEntryChangedEvent ev)
      {
        boolean enable = saveChanges.isVisible() &&
            !authenticationRequired(getInfo().getServerDescriptor());
        if (enable)
        {
          if (ev.getEntry() == null)
          {
            // Something changed that is wrong: assume the entry has been
            // modified, when the user tries to save we will inform of the
            // problem
            enable = true;
          }
          else
          {
            boolean modified =
              !Utilities.areDnsEqual(ev.getEntry().getDN().toString(),
                  searchResult.getDN()) ||
                  !ModifyEntryTask.getModifications(ev.getEntry(), searchResult,
                      getInfo()).isEmpty();
            enable = modified;
          }
        }
        saveChanges.setEnabled(enable);
      }
    };

    ldifEntryPanel = new LDIFViewEntryPanel();
    ldifEntryPanel.addLDAPEntryChangedListener(listener);
    if (ldifEntryPanel.requiresBorder())
    {
      Utilities.setBorder(ldifEntryPanel, border);
    }
    mainPanel.add(ldifEntryPanel, LDIF_VIEW);

    tableEntryPanel = new TableViewEntryPanel();
    tableEntryPanel.addLDAPEntryChangedListener(listener);
    if (tableEntryPanel.requiresBorder())
    {
      Utilities.setBorder(tableEntryPanel, border);
    }
    mainPanel.add(tableEntryPanel, ATTRIBUTE_VIEW);

    simplifiedEntryPanel = new SimplifiedViewEntryPanel();
    simplifiedEntryPanel.addLDAPEntryChangedListener(listener);
    if (simplifiedEntryPanel.requiresBorder())
    {
      Utilities.setBorder(simplifiedEntryPanel, border);
    }
    mainPanel.add(simplifiedEntryPanel, SIMPLIFIED_VIEW);

    cardLayout.show(mainPanel, NOTHING_SELECTED);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No ok button
  }

  /**
   * {@inheritDoc}
   */
  public void entryRead(EntryReadEvent ev)
  {
    searchResult = ev.getSearchResult();

    updateEntryView(searchResult, treePath);
  }

  /**
   * Updates the panel with the provided search result.
   * @param searchResult the search result corresponding to the selected node.
   * @param treePath the tree path of the selected node.
   */
  private void updateEntryView(CustomSearchResult searchResult,
      TreePath treePath)
  {
    boolean isReadOnly = isReadOnly(searchResult.getDN());
    boolean canDelete = canDelete(searchResult.getDN());

    delete.setVisible(canDelete);
    saveChanges.setVisible(!isReadOnly);
    String cardKey;
    switch (view)
    {
    case LDIF_VIEW:
      displayedEntryPanel = ldifEntryPanel;
      cardKey = LDIF_VIEW;
      break;
    case ATTRIBUTE_VIEW:
      displayedEntryPanel = tableEntryPanel;
      cardKey = ATTRIBUTE_VIEW;
      break;
    default:
      displayedEntryPanel = simplifiedEntryPanel;
      cardKey = SIMPLIFIED_VIEW;
    }
    displayedEntryPanel.update(searchResult, isReadOnly, treePath);
    saveChanges.setEnabled(false);
    cardLayout.show(mainPanel, cardKey);
  }

  /**
   * Sets the view to be displayed by this panel.
   * @param view the view.
   */
  public void setView(View view)
  {
    if (view != this.view)
    {
      this.view = view;
      if (searchResult != null)
      {
        updateEntryView(searchResult, treePath);
      }
    }
  }

  /**
   * Displays a message informing that an error occurred reading the entry.
   * @param ev the entry read error event.
   */
  public void entryReadError(EntryReadErrorEvent ev)
  {
    searchResult = null;

    errorSearchingPanel.setError(ev.getDN(), ev.getError());

    delete.setVisible(false);
    saveChanges.setVisible(false);

    cardLayout.show(mainPanel, ERROR_SEARCHING);

    displayedEntryPanel = null;
  }

  /**
   * Displays a panel informing that nothing is selected.
   *
   */
  public void noEntrySelected()
  {
    searchResult = null;

    delete.setVisible(false);
    saveChanges.setVisible(false);

    cardLayout.show(mainPanel, NOTHING_SELECTED);

    displayedEntryPanel = null;
  }

  /**
   * Displays a panel informing that multiple entries are selected.
   *
   */
  public void multipleEntriesSelected()
  {
    searchResult = null;

    delete.setVisible(false);
    saveChanges.setVisible(false);

    cardLayout.show(mainPanel, MULTIPLE_SELECTED);

    displayedEntryPanel = null;
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_EDIT_LDAP_ENTRY_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return saveChanges;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        boolean isReadOnly = true;
        boolean canDelete = false;
        if ((searchResult != null) && desc.isAuthenticated())
        {
          isReadOnly = isReadOnly(searchResult.getDN());
          canDelete = canDelete(searchResult.getDN());
        }

        delete.setVisible(canDelete);
        saveChanges.setVisible(!isReadOnly);
      }
    });
  }


  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    simplifiedEntryPanel.setInfo(info);
    ldifEntryPanel.setInfo(info);
    tableEntryPanel.setInfo(info);
    errorSearchingPanel.setInfo(info);
  }

  private DN[] parentReadOnly;
  private DN[] nonDeletable;
  {
    try
    {
      parentReadOnly = new DN[] {
        DN.decode(ConfigConstants.DN_TASK_ROOT),
        DN.decode(ConfigConstants.DN_MONITOR_ROOT),
        DN.decode(ConfigConstants.DN_BACKUP_ROOT),
        DN.decode(Constants.REPLICATION_CHANGES_DN)
      };
      nonDeletable = new DN[] {
          DN.decode(ConfigConstants.DN_CONFIG_ROOT),
          DN.decode(ConfigConstants.DN_DEFAULT_SCHEMA_ROOT),
          DN.decode(ConfigConstants.DN_TRUST_STORE_ROOT)
      };
    }
    catch (Throwable t)
    {
      throw new IllegalStateException("Error decoding DNs: "+t, t);
    }
  }

  /**
   * Returns <CODE>true</CODE> if the provided DN corresponds to a read-only
   * entry and <CODE>false</CODE> otherwise.
   * @param sDn the DN of the entry.
   * @return <CODE>true</CODE> if the provided DN corresponds to a read-only
   * entry and <CODE>false</CODE> otherwise.
   */
  public boolean isReadOnly(String sDn)
  {
    boolean isReadOnly = false;
    try
    {
      DN dn = DN.decode(sDn);
      for (DN parentDN : parentReadOnly)
      {
        if (dn.isDescendantOf(parentDN))
        {
          isReadOnly = true;
          break;
        }
      }
      if (!isReadOnly)
      {
        isReadOnly = dn.equals(DN.NULL_DN);
      }
    }
    catch (Throwable t)
    {
      throw new IllegalStateException("Error decoding DNs: "+t, t);
    }
    return isReadOnly;
  }

  /**
   * Returns <CODE>true</CODE> if the provided DN corresponds to an entry that
   * can be deleted and <CODE>false</CODE> otherwise.
   * @param sDn the DN of the entry.
   * @return <CODE>true</CODE> if the provided DN corresponds to an entry that
   * can be deleted and <CODE>false</CODE> otherwise.
   */
  public boolean canDelete(String sDn)
  {
    boolean canDelete = true;
    try
    {
      DN dn = DN.decode(sDn);
      for (DN parentDN : parentReadOnly)
      {
        if (dn.isDescendantOf(parentDN))
        {
          canDelete = false;
          break;
        }
      }
      if (canDelete)
      {
        for (DN cannotDelete : nonDeletable)
        {
          if (cannotDelete.equals(dn))
          {
            canDelete = false;
            break;
          }
        }
      }
      if (canDelete)
      {
        canDelete = !dn.equals(DN.NULL_DN);
      }
    }
    catch (Throwable t)
    {
      throw new IllegalStateException("Error decoding DNs: "+t, t);
    }
    return canDelete;
  }

  /**
   * Saves the changes done to the entry.
   * @param modal whether the progress dialog for the task must be modal or
   * not.
   */
  private void saveChanges(boolean modal)
  {
    newTask = null;
    final ArrayList<Message> errors = new ArrayList<Message>();
    // Check that the entry is correct.
    try
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_MODIFYING_ENTRY_CHANGES_TITLE.get(), getInfo());
      dlg.setModal(modal);
      Entry entry = displayedEntryPanel.getEntry();
      newTask = new ModifyEntryTask(getInfo(), dlg, entry,
            searchResult, controller, treePath);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if ((errors.size() == 0) && newTask.hasModifications())
      {
        String dn = entry.getDN().toString();
        launchOperation(newTask,
            INFO_CTRL_PANEL_MODIFYING_ENTRY_SUMMARY.get(dn),
            INFO_CTRL_PANEL_MODIFYING_ENTRY_COMPLETE.get(),
            INFO_CTRL_PANEL_MODIFYING_ENTRY_SUCCESSFUL.get(dn),
            ERR_CTRL_PANEL_MODIFYING_ENTRY_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_MODIFYING_ENTRY_ERROR_DETAILS.get(dn),
            null,
            dlg);
        saveChanges.setEnabled(false);
        dlg.setVisible(true);
      }
    }
    catch (OpenDsException ode)
    {
      errors.add(ERR_CTRL_PANEL_INVALID_ENTRY.get(
          ode.getMessageObject().toString()));
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  private void deleteEntry()
  {
    final ArrayList<Message> errors = new ArrayList<Message>();
    // Check that the entry is correct.
    // Rely in numsubordinates...
    boolean isLeaf = true;

    Set<Object> o = searchResult.getAttributeValues("numsubordinates");
    if (!o.isEmpty())
    {
      int numsubordinates = Integer.parseInt((String)o.iterator().next());
      isLeaf = numsubordinates <= 0;
    }

    if (treePath != null)
    {
      Message title = isLeaf ? INFO_CTRL_PANEL_DELETING_ENTRY_TITLE.get() :
        INFO_CTRL_PANEL_DELETING_SUBTREE_TITLE.get();
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          title, getInfo());
      DeleteEntryTask newTask = new DeleteEntryTask(getInfo(), dlg,
          new TreePath[]{treePath}, controller);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.size() == 0)
      {
        Message confirmationMessage =
          isLeaf ? INFO_CTRL_PANEL_DELETE_ENTRY_CONFIRMATION_DETAILS.get(
              searchResult.getDN()) :
                INFO_CTRL_PANEL_DELETE_SUBTREE_CONFIRMATION_DETAILS.get(
                    searchResult.getDN());
          if (displayConfirmationDialog(
              INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
              confirmationMessage))
          {
            String dn = searchResult.getDN();
            if (isLeaf)
            {
              launchOperation(newTask,
                  INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(dn),
                  INFO_CTRL_PANEL_DELETING_ENTRY_COMPLETE.get(),
                  INFO_CTRL_PANEL_DELETING_ENTRY_SUCCESSFUL.get(dn),
                  ERR_CTRL_PANEL_DELETING_ENTRY_ERROR_SUMMARY.get(),
                  ERR_CTRL_PANEL_DELETING_ENTRY_ERROR_DETAILS.get(dn),
                  null,
                  dlg);
            }
            else
            {
              launchOperation(newTask,
                  INFO_CTRL_PANEL_DELETING_SUBTREE_SUMMARY.get(dn),
                  INFO_CTRL_PANEL_DELETING_SUBTREE_COMPLETE.get(),
                  INFO_CTRL_PANEL_DELETING_SUBTREE_SUCCESSFUL.get(dn),
                  ERR_CTRL_PANEL_DELETING_SUBTREE_ERROR_SUMMARY.get(),
                  ERR_CTRL_PANEL_DELETING_SUBTREE_ERROR_DETAILS.get(dn),
                  null,
                  dlg);
            }
            dlg.setVisible(true);
          }
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the browser controller in charge of the tree.
   * @return the browser controller in charge of the tree.
   */
  public BrowserController getController()
  {
    return controller;
  }

  /**
   * Sets the browser controller in charge of the tree.
   * @param controller the browser controller in charge of the tree.
   */
  public void setController(BrowserController controller)
  {
    this.controller = controller;
  }

  /**
   * Returns the tree path associated with the node that is being displayed.
   * @return the tree path associated with the node that is being displayed.
   */
  public TreePath getTreePath()
  {
    return treePath;
  }

  /**
   * Sets the tree path associated with the node that is being displayed.
   * @param treePath the tree path associated with the node that is being
   * displayed.
   */
  public void setTreePath(TreePath treePath)
  {
    this.treePath = treePath;
  }

  /**
   * Method used to know if there are unsaved changes or not.  It is used by
   * the entry selection listener when the user changes the selection.
   * @return <CODE>true</CODE> if there are unsaved changes (and so the
   * selection of the entry should be cancelled) and <CODE>false</CODE>
   * otherwise.
   */
  public boolean mustCheckUnsavedChanges()
  {
    return (displayedEntryPanel != null) &&
        saveChanges.isVisible() && saveChanges.isEnabled();
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection change in the tree.
   * @return the value telling whether the user chose to save the changes in the
   * panel, to not save them or simply cancelled the selection in the tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(
          Utilities.getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
       INFO_CTRL_PANEL_UNSAVED_ENTRY_CHANGES_DETAILS.get(searchResult.getDN()));
    Utilities.centerGoldenMean(unsavedChangesDlg,
          Utilities.getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      saveChanges(false);
      if ((newTask == null) || // The user data is not valid
          newTask.getState() != Task.State.FINISHED_SUCCESSFULLY)
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }
}
