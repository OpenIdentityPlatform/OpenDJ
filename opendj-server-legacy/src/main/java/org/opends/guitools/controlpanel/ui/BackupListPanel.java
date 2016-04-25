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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;

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
import java.util.List;
import java.util.Set;

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
import javax.swing.table.TableColumn;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.guitools.controlpanel.datamodel.BackupDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackupTableModel;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowseActionListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.renderer.BackupTableCellRenderer;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.Installation;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.util.StaticUtils;

/** Abstract class used to refactor code in panels that contain a backup list on it. */
public abstract class BackupListPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4804555239922795163L;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The refreshing list message, displayed when the list of backups is refreshed. */
  private static final LocalizableMessage REFRESHING_LIST = INFO_CTRL_PANEL_REFRESHING_LIST_SUMMARY.get();

  /** The message informing that no backups where found. */
  private static final LocalizableMessage NO_BACKUPS_FOUND = INFO_CTRL_PANEL_NO_BACKUPS_FOUND.get();

  private static final String DUMMY_PARENT_PATH = "/local/OpenDJ-X.X.X/bak";

  /** The text field containing the parent directory. */
  protected JTextField parentDirectory;

  /** Label for the path field. */
  protected JLabel lPath;

  /** Label for the list. */
  protected JLabel lAvailableBackups;

  /** Refreshing list label (displayed instead of the list when this one is being refreshed). */
  protected JLabel lRefreshingList;

  /** Refresh list button. */
  protected JButton refreshList;

  /** Verify backup button. */
  protected JButton verifyBackup;

  /** Browse button. */
  protected JButton browse;

  /** The scroll that contains the list of backups (actually is a table). */
  protected JScrollPane tableScroll;

  /** The list of backups. */
  protected JTable backupList;

  private JLabel lRemoteFileHelp;

  /** Whether the backup parent directory has been initialized with a value. */
  private boolean backupDirectoryInitialized;

  private BackupTableCellRenderer renderer;

  /** Default constructor. */
  protected BackupListPanel()
  {
    super();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return parentDirectory;
  }

  /**
   * Returns the selected backup in the list.
   *
   * @return the selected backup in the list.
   */
  protected BackupDescriptor getSelectedBackup()
  {
    BackupDescriptor backup = null;
    int row = backupList.getSelectedRow();
    if (row != -1)
    {
      BackupTableModel model = (BackupTableModel) backupList.getModel();
      backup = model.get(row);
    }
    return backup;
  }

  /**
   * Notification that the verify button was clicked. Whatever is required to be
   * done must be done in this method.
   */
  protected abstract void verifyBackupClicked();

  /**
   * Creates the components and lays them in the panel.
   *
   * @param gbc
   *          the grid bag constraints to be used.
   */
  protected void createLayout(GridBagConstraints gbc)
  {
    gbc.gridy++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 1;
    gbc.insets.left = 0;
    lPath = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BACKUP_PATH_LABEL.get());
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
        new BrowseActionListener(parentDirectory, BrowseActionListener.BrowseType.LOCATION_DIRECTORY, this));
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    add(browse, gbc);

    lRemoteFileHelp = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_REMOTE_SERVER_PATH.get());
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.insets.top = 3;
    gbc.insets.left = 10;
    gbc.gridy++;
    add(lRemoteFileHelp, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    lAvailableBackups = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_AVAILABLE_BACKUPS_LABEL.get());
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 1;
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
    backupList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    backupList.setShowGrid(false);
    backupList.setIntercellSpacing(new Dimension(0, 0));
    renderer = new BackupTableCellRenderer();
    renderer.setParentPath(new File(DUMMY_PARENT_PATH));
    for (int i = 0; i < model.getColumnCount(); i++)
    {
      TableColumn col = backupList.getColumn(model.getColumnName(i));
      col.setCellRenderer(renderer);
    }
    backupList.setTableHeader(null);
    Utilities.updateTableSizes(backupList);
    tableScroll = Utilities.createScrollPane(backupList);
    tableScroll.setColumnHeaderView(null);
    tableScroll.setPreferredSize(backupList.getPreferredSize());
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(tableScroll, gbc);
    lRefreshingList.setPreferredSize(tableScroll.getPreferredSize());

    gbc.gridy++;
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
    refreshList = Utilities.createButton(INFO_CTRL_PANEL_REFRESH_LIST_BUTTON_LABEL.get());
    refreshList.setOpaque(false);
    refreshList.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        refreshList();
      }
    });
    gbc2.weightx = 0.0;
    gbc2.gridx++;
    buttonPanel.add(refreshList, gbc2);
    gbc2.gridx++;
    gbc2.insets.left = 5;
    verifyBackup = Utilities.createButton(INFO_CTRL_PANEL_VERIFY_BACKUP_BUTTON_LABEL.get());
    verifyBackup.setOpaque(false);
    verifyBackup.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        verifyBackupClicked();
      }
    });
    ListSelectionListener listener = new ListSelectionListener()
    {
      @Override
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
   */
  protected void refreshList()
  {
    final boolean refreshEnabled = refreshList.isEnabled();
    refreshList.setEnabled(false);
    verifyBackup.setEnabled(false);
    tableScroll.setVisible(false);
    lRefreshingList.setText(REFRESHING_LIST.toString());
    lRefreshingList.setVisible(isLocal());

    final int lastSelectedRow = backupList.getSelectedRow();
    final String parentPath = parentDirectory.getText();

    BackgroundTask<Set<BackupInfo>> worker = new BackgroundTask<Set<BackupInfo>>()
    {
      @Override
      public Set<BackupInfo> processBackgroundTask() throws Throwable
      {
        // Open the backup directory and make sure it is valid.
        Set<BackupInfo> backups = new LinkedHashSet<>();
        Throwable firstThrowable = null;

        if (new File(parentPath, BACKUP_DIRECTORY_DESCRIPTOR_FILE).exists())
        {
          try
          {
            BackupDirectory backupDir = BackupDirectory.readBackupDirectoryDescriptor(parentPath);
            backups.addAll(backupDir.getBackups().values());
          }
          catch (Throwable t)
          {
            firstThrowable = t;
          }
        }

        // Check the subdirectories
        File f = new File(parentPath);

        // Check the first level of directories (we might have done
        // a backup of one backend and then a backup of several backends under the same directory).
        if (f.isDirectory())
        {
          File[] children = f.listFiles();
          for (int i = 0; i < children.length; i++)
          {
            if (children[i].isDirectory())
            {
              try
              {
                BackupDirectory backupDir =
                    BackupDirectory.readBackupDirectoryDescriptor(children[i].getAbsolutePath());

                backups.addAll(backupDir.getBackups().values());
              }
              catch (Throwable t2)
              {
                if (!children[i].getName().equals("tasks") && firstThrowable != null)
                {
                  logger.warn(LocalizableMessage.raw("Error searching backup: " + t2, t2));
                }
              }
            }
          }
        }
        if (backups.isEmpty() && firstThrowable != null)
        {
          throw firstThrowable;
        }
        return backups;
      }

      @Override
      public void backgroundTaskCompleted(Set<BackupInfo> returnValue, Throwable t)
      {
        BackupTableModel model = (BackupTableModel) backupList.getModel();
        model.clear();
        renderer.setParentPath(new File(parentPath));
        if (t == null)
        {
          performSuccessActions(returnValue, model);
        }
        else
        {
          performErrorActions(t, model);
        }

        refreshList.setEnabled(refreshEnabled);
        verifyBackup.setEnabled(getSelectedBackup() != null);
        if (lastSelectedRow != -1 && lastSelectedRow < backupList.getRowCount())
        {
          backupList.setRowSelectionInterval(lastSelectedRow, lastSelectedRow);
        }
        else if (backupList.getRowCount() > 0)
        {
          backupList.setRowSelectionInterval(0, 0);
        }
      }

      private void performSuccessActions(Set<BackupInfo> returnValue, BackupTableModel model)
      {
        if (!returnValue.isEmpty())
        {
          for (BackupInfo backup : returnValue)
          {
            model.add(new BackupDescriptor(backup));
          }
          Utilities.updateTableSizes(backupList);
          tableScroll.setVisible(true);
          lRefreshingList.setVisible(false);
        }
        else
        {
          lRefreshingList.setText(NO_BACKUPS_FOUND.toString());
          lRefreshingList.setVisible(isLocal());
        }
        updateUI(true, model);
      }

      private void performErrorActions(Throwable t, BackupTableModel model)
      {
        LocalizableMessage details = ERR_RESTOREDB_CANNOT_READ_BACKUP_DIRECTORY.get(
            parentDirectory.getText(), StaticUtils.getExceptionMessage(t));
        updateErrorPane(errorPane,
                        ERR_ERROR_SEARCHING_BACKUPS_SUMMARY.get(),
                        ColorAndFontConstants.errorTitleFont,
                        details,
                        errorPane.getFont());
        packParentDialog();
        updateUI(false, model);
     }

      private void updateUI(boolean isSuccess, BackupTableModel model)
      {
        model.fireTableDataChanged();
        errorPane.setVisible(!isSuccess);
        if (isSuccess)
        {
          // This is done to perform checks against whether we require to display an error message.
          configurationChanged(new ConfigurationChangeEvent(null, getInfo().getServerDescriptor()));
        }
        else
        {
          lRefreshingList.setText(NO_BACKUPS_FOUND.toString());
        }
      }
    };
    worker.startBackgroundTask();
  }


  /**
   * Creates a list with backup descriptor.
   * This is done simply to have a good initial size for the table.
   *
   * @return a list with bogus backup descriptors.
   */
  private List<BackupDescriptor> createDummyBackupList()
  {
    List<BackupDescriptor> list = new ArrayList<>();
    list.add(new BackupDescriptor(new File(DUMMY_PARENT_PATH + "/200704201567Z"),
             new GregorianCalendar(2007, 5, 20, 8, 10).getTime(), BackupDescriptor.Type.FULL, "id"));
    list.add(new BackupDescriptor(new File(DUMMY_PARENT_PATH + "/200704201567Z"),
             new GregorianCalendar(2007, 5, 22, 8, 10).getTime(), BackupDescriptor.Type.INCREMENTAL, "id"));
    list.add(new BackupDescriptor(new File(DUMMY_PARENT_PATH + "/200704221567Z"),
             new GregorianCalendar(2007, 5, 25, 8, 10).getTime(), BackupDescriptor.Type.INCREMENTAL, "id"));
    return list;
  }

  @Override
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    if (!backupDirectoryInitialized && parentDirectory.getText().length() == 0)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          parentDirectory.setText(getBackupPath(ev.getNewDescriptor()));
          refreshList();
          backupDirectoryInitialized = true;
        }
      });
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        lRemoteFileHelp.setVisible(!isLocal());
        browse.setVisible(isLocal());
        lAvailableBackups.setVisible(isLocal());
        tableScroll.setVisible(isLocal());
        refreshList.setVisible(isLocal());
        verifyBackup.setVisible(isLocal());
      }
    });
  }

  private String getBackupPath(ServerDescriptor desc)
  {
    if (desc.isLocal() || desc.isWindows() == isWindows())
    {
      File f = new File(desc.getInstancePath(), Installation.BACKUPS_PATH_RELATIVE);
      try
      {
        return f.getCanonicalPath();
      }
      catch (Throwable t)
      {
        return f.getAbsolutePath();
      }
    }
    else
    {
      String separator = desc.isWindows() ? "\\" : "/";
      return desc.getInstancePath() + separator + Installation.BACKUPS_PATH_RELATIVE;
    }
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    if (visible && backupDirectoryInitialized)
    {
      refreshList();
    }
  }
}
