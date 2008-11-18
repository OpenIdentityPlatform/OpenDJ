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
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ToolMessages.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.opends.guitools.controlpanel.datamodel.BackupDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackupTableModel;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.renderer.BackupTableCellRenderer;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.StaticUtils;

/**
 * Abstract class used to refactor code in panels that contain a backup list on
 * it.
 *
 */
public abstract class BackupListPanel extends StatusGenericPanel
{
  /**
   * The text field containing the parent directory.
   */
  protected JTextField parentDirectory;

  /**
   * The refreshing list message, displayed when the list of backups is
   * refreshed.
   */
  protected final Message REFRESHING_LIST =
    INFO_CTRL_PANEL_REFRESHING_LIST_SUMMARY.get();

  /**
   * The message informing that no backups where found.
   */
  protected final Message NO_BACKUPS_FOUND =
    INFO_CTRL_PANEL_NO_BACKUPS_FOUND.get();

  /**
   * Label for the path field.
   */
  protected JLabel lPath;
  /**
   * Label for the list.
   */
  protected JLabel lAvailableBackups;
  /**
   * Refreshing list label (displayed instead of the list when this one is
   * being refreshed).
   */
  protected JLabel lRefreshingList;
  /**
   * Refresh list button.
   */
  protected JButton refreshList;
  /**
   * Verify backup button.
   */
  protected JButton verifyBackup;
  /**
   * Browse button.
   */
  protected JButton browse;
  /**
   * The scroll that contains the list of backups (actually is a table).
   */
  protected JScrollPane tableScroll;
  /**
   * The list of backups.
   */
  protected JTable backupList;

  /**
   * Whether the backup parent directory has been initialized with a value or
   * not.
   */
  private boolean backupDirectoryInitialized;

  private static final Logger LOG =
    Logger.getLogger(RestorePanel.class.getName());

  /**
   * Default constructor.
   *
   */
  protected BackupListPanel()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return parentDirectory;
  }

  /**
   * Returns the selected backup in the list.
   * @return the selected backup in the list.
   */
  protected BackupDescriptor getSelectedBackup()
  {
    BackupDescriptor backup = null;
    int row = backupList.getSelectedRow();
    if (row != -1)
    {
      BackupTableModel model = (BackupTableModel)backupList.getModel();
      backup = model.get(row);
    }
    return backup;
  }

  /**
   * Notification that the verify button was clicked.  Whatever is required
   * to be done must be done in this method.
   *
   */
  protected abstract void verifyBackupClicked();

  /**
   * Creates the components and lays them in the panel.
   * @param gbc the grid bag constraints to be used.
   */
  protected void createLayout(GridBagConstraints gbc)
  {
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 1;
    gbc.insets.left = 0;
    lPath = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKUP_PATH_LABEL.get());
    add(lPath, gbc);

    gbc.gridx = 1;
    gbc.insets.left = 10;
    parentDirectory = Utilities.createLongTextField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(parentDirectory, gbc);
    browse = Utilities.createButton(INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    browse.setOpaque(false);
    browse.addActionListener(
        new BrowseActionListener(parentDirectory,
            BrowseActionListener.BrowseType.LOCATION_DIRECTORY,  this));
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    add(browse, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    lAvailableBackups = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_AVAILABLE_BACKUPS_LABEL.get());
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.NONE;
    add(lAvailableBackups, gbc);

    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets.left = 10;
    lRefreshingList = Utilities.createDefaultLabel(REFRESHING_LIST);
    lRefreshingList.setHorizontalAlignment(SwingConstants.CENTER);
    gbc.anchor = GridBagConstraints.CENTER;
    add(lRefreshingList, gbc);

    backupList = new JTable();
    // Done to provide a good size to the table.
    BackupTableModel model = new BackupTableModel();
    for (BackupDescriptor backup : createDummyBackupList())
    {
      model.add(backup);
    }
    backupList.setModel(model);
    backupList.getSelectionModel().setSelectionMode(
        ListSelectionModel.SINGLE_SELECTION);
    backupList.setShowGrid(false);
    backupList.setIntercellSpacing(new Dimension(0, 0));
    TableCellRenderer renderer = new BackupTableCellRenderer();
    for (int i=0; i<model.getColumnCount(); i++)
    {
      TableColumn col = backupList.getColumn(model.getColumnName(i));
      col.setCellRenderer(renderer);
    }
    backupList.getTableHeader().setVisible(false);
    backupList.getTableHeader().setPreferredSize(new Dimension(0, 0));
    backupList.getTableHeader().setMinimumSize(new Dimension(0, 0));
    Utilities.updateTableSizes(backupList);
    tableScroll = Utilities.createScrollPane(backupList);
    tableScroll.setPreferredSize(backupList.getPreferredSize());
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(tableScroll, gbc);
    lRefreshingList.setPreferredSize(tableScroll.getPreferredSize());

    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets.top = 5;
    JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);
    add(buttonPanel, gbc);
    GridBagConstraints gbc2 = new GridBagConstraints();
    gbc2.gridx = 0;
    gbc2.gridy = 0;
    gbc2.gridwidth = 1;
    gbc2.anchor = GridBagConstraints.EAST;
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    gbc2.weightx = 1.0;
    buttonPanel.add(Box.createHorizontalGlue(), gbc2);
    refreshList = Utilities.createButton(
        INFO_CTRL_PANEL_REFRESH_LIST_BUTTON_LABEL.get());
    refreshList.setOpaque(false);
    refreshList.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        refreshList();
      }
    });
    gbc2.weightx = 0.0;
    gbc2.gridx ++;
    buttonPanel.add(refreshList, gbc2);
    gbc2.gridx ++;
    gbc2.insets.left = 5;
    verifyBackup = Utilities.createButton(
        INFO_CTRL_PANEL_VERIFY_BACKUP_BUTTON_LABEL.get());
    verifyBackup.setOpaque(false);
    verifyBackup.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        verifyBackupClicked();
      }
    });
    ListSelectionListener listener = new ListSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(ListSelectionEvent ev)
      {
        BackupDescriptor backup = getSelectedBackup();
        verifyBackup.setEnabled(backup != null);
      }
    };
    backupList.getSelectionModel().addListSelectionListener(listener);
    listener.valueChanged(null);
    buttonPanel.add(verifyBackup, gbc2);
  }

  /**
   * Refresh the list of backups by looking in the backups defined under the
   * provided parent backup directory.
   *
   */
  protected void refreshList()
  {
    final boolean refreshEnabled = refreshList.isEnabled();

    refreshList.setEnabled(false);
    verifyBackup.setEnabled(false);
    tableScroll.setVisible(false);
    lRefreshingList.setText(REFRESHING_LIST.toString());
    lRefreshingList.setVisible(true);
    final int lastSelectedRow = backupList.getSelectedRow();

    BackgroundTask<Set<BackupInfo>> worker =
      new BackgroundTask<Set<BackupInfo>>()
    {
      /**
       * {@inheritDoc}
       */
      public Set<BackupInfo> processBackgroundTask() throws Throwable
      {
        // Open the backup directory and make sure it is valid.
        LinkedHashSet<BackupInfo> backups = new LinkedHashSet<BackupInfo>();
        Throwable firstThrowable = null;
        try
        {
          BackupDirectory backupDir =
            BackupDirectory.readBackupDirectoryDescriptor(
                parentDirectory.getText());
          backups.addAll(backupDir.getBackups().values());
        }
        catch (Throwable t)
        {
          firstThrowable = t;
          // Check the subdirectories
          File f = new File(parentDirectory.getText());

          if (f.isDirectory())
          {
            File[] children = f.listFiles();
            for (int i=0; i<children.length; i++)
            {
              if (children[i].isDirectory())
              {
                try
                {
                  BackupDirectory backupDir =
                    BackupDirectory.readBackupDirectoryDescriptor(
                        children[i].getAbsolutePath());

                  backups.addAll(backupDir.getBackups().values());
                }
                catch (Throwable t2)
                {
                  if (!children[i].getName().equals("tasks"))
                  {
                    LOG.log(Level.WARNING, "Error searching backup: "+t2, t2);
                  }
                }
              }
            }
          }
          if (backups.size() == 0)
          {
            throw firstThrowable;
          }
        }
        return backups;
      }
      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Set<BackupInfo> returnValue,
          Throwable t)
      {
        BackupTableModel model = (BackupTableModel)backupList.getModel();
        model.clear();
        if (t == null)
        {
          if (returnValue.size() > 0)
          {
            for (BackupInfo backup : returnValue)
            {
              model.add(new BackupDescriptor(backup));
            }
            model.fireTableDataChanged();
            Utilities.updateTableSizes(backupList);
            tableScroll.setVisible(true);
            lRefreshingList.setVisible(false);
          }
          else
          {
            model.fireTableDataChanged();
            lRefreshingList.setText(NO_BACKUPS_FOUND.toString());
          }
          errorPane.setVisible(false);
          // This is done to perform checks against whether we require to
          // display an error message or not.
          configurationChanged(new ConfigurationChangeEvent(null,
              getInfo().getServerDescriptor()));
        }
        else
        {
          model.fireTableDataChanged();
          boolean displayError = true;
          if (t instanceof OpenDsException)
          {
            OpenDsException e = (OpenDsException)t;
            if (e.getMessageObject().getDescriptor().equals(
                ERR_BACKUPDIRECTORY_NO_DESCRIPTOR_FILE))
            {
              displayError = false;
            }
          }
          if (displayError)
          {
            Message details = ERR_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY.get(
              parentDirectory.getText(), StaticUtils.getExceptionMessage(t));

            updateErrorPane(errorPane,
                ERR_ERROR_SEARCHING_BACKUPS_SUMMARY.get(),
                ColorAndFontConstants.errorTitleFont,
                details,
                errorPane.getFont());
            packParentDialog();
          }
          errorPane.setVisible(displayError);

          if (!displayError)
          {
            // This is done to perform checks against whether we require to
            // display an error message or not.
            configurationChanged(new ConfigurationChangeEvent(null,
                getInfo().getServerDescriptor()));
          }

          lRefreshingList.setText(NO_BACKUPS_FOUND.toString());
        }
        refreshList.setEnabled(refreshEnabled);
        verifyBackup.setEnabled(getSelectedBackup() != null);
        if ((lastSelectedRow != -1) &&
            (lastSelectedRow < backupList.getRowCount()))
        {
          backupList.setRowSelectionInterval(lastSelectedRow, lastSelectedRow);
        }
        else if (backupList.getRowCount() > 0)
        {
          backupList.setRowSelectionInterval(0, 0);
        }
      }
    };
    worker.startBackgroundTask();
  }

  /**
   * Creates a list with backup descriptor.  This is done simply to have a good
   * initial size for the table.
   * @return a list with bogus backup descriptors.
   */
  private ArrayList<BackupDescriptor> createDummyBackupList()
  {
    ArrayList<BackupDescriptor> list = new ArrayList<BackupDescriptor>();
    list.add(new BackupDescriptor(
        new File("/local/OpenDS-0.9.0/bak/200704201567Z"),
        new GregorianCalendar(2007, 5, 20, 8, 10).getTime(),
        BackupDescriptor.Type.FULL, "id"));
    list.add(new BackupDescriptor(
        new File("/local/OpenDS-0.9.0/bak/200704201567Z"),
        new GregorianCalendar(2007, 5, 22, 8, 10).getTime(),
        BackupDescriptor.Type.INCREMENTAL, "id"));
    list.add(new BackupDescriptor(
        new File("/local/OpenDS-0.9.0/bak/200704221567Z"),
        new GregorianCalendar(2007, 5, 25, 8, 10).getTime(),
        BackupDescriptor.Type.INCREMENTAL, "id"));
    return list;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();

    if (!backupDirectoryInitialized &&
        (parentDirectory.getText().length() == 0))
    {
      final String path = new File(desc.getInstallPath(),
          org.opends.quicksetup.Installation.BACKUPS_PATH_RELATIVE).
          getAbsolutePath();
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          parentDirectory.setText(path);
          refreshList();
          backupDirectoryInitialized = true;
        }
      });
    }
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible && backupDirectoryInitialized)
    {
      refreshList();
    }
  }
}
