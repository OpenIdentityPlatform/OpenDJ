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
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackupDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.tools.BackUpDB;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.BackupInfo;
import org.opends.server.util.ServerConstants;

/**
 * The panel that appears when the user clicks on 'Backup...'.
 *
 */
public class BackupPanel extends BackupListPanel
{
  private static final long serialVersionUID = -1626301350756394814L;
  private JComboBox backends;
  private JCheckBox allBackends;
  private JTextField backupID;
  private JRadioButton fullBackup;
  private JRadioButton incrementalBackup;
  private JCheckBox compressData;
  private JCheckBox encryptData;
  private JCheckBox generateMessageDigest;
  private JCheckBox signMessageDigest;

  private JLabel lBackend;
  private JLabel lNoBackendsFound;
  private JLabel lBackupID;
  private JLabel lBackupType;
  private JLabel lBackupOptions;

  private ChangeListener changeListener;

  private boolean backupIDInitialized = false;

  private static final Logger LOG =
    Logger.getLogger(BackupPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public BackupPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_BACKUP_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return backupID;
  }

  /**
   * {@inheritDoc}
   */
  protected void verifyBackupClicked()
  {
    // Nothing to do: the button is not visible.
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    addErrorPane(gbc);

    gbc.weightx = 0.0;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    lBackend = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKEND_LABEL.get());
    add(lBackend, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    JPanel auxPanel = new JPanel(new GridBagLayout());
    add(auxPanel, gbc);
    auxPanel.setOpaque(false);
    GridBagConstraints gbc2 = new GridBagConstraints();
    backends = Utilities.createComboBox();
    backends.setModel(new DefaultComboBoxModel(new String[]{}));
    auxPanel.add(backends, gbc2);
    lNoBackendsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_BACKENDS_FOUND_LABEL.get());
    add(lNoBackendsFound, gbc);
    lNoBackendsFound.setVisible(false);
    gbc2.insets.left = 10;
    allBackends = Utilities.createCheckBox(
        INFO_CTRL_PANEL_BACKUP_ALL_BACKENDS_LABEL.get());
    auxPanel.add(allBackends, gbc2);

    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lBackupType = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKUP_TYPE_LABEL.get());
    add(lBackupType, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    fullBackup = Utilities.createRadioButton(
        INFO_CTRL_PANEL_FULL_BACKUP_LABEL.get());
    add(fullBackup, gbc);

    gbc.gridy ++;
    gbc.insets.top = 5;
    incrementalBackup = Utilities.createRadioButton(
        INFO_CTRL_PANEL_INCREMENTAL_BACKUP_LABEL.get());
    add(incrementalBackup, gbc);

    ButtonGroup group = new ButtonGroup();
    group.add(fullBackup);
    group.add(incrementalBackup);
    fullBackup.setSelected(true);

    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    lBackupID = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKUP_ID_LABEL.get());
    add(lBackupID, gbc);
    backupID = Utilities.createMediumTextField();
    gbc.weightx = 0.0;
    gbc.gridx ++;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = 2;
    add(backupID, gbc);

    gbc.insets.top = 10;
    gbc.gridx = 0;
    gbc.gridy ++;
    super.createLayout(gbc);
    verifyBackup.setVisible(false);
    lAvailableBackups.setText(
        INFO_CTRL_PANEL_AVAILABLE_PARENT_BACKUPS_LABEL.get().toString());
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.insets.top = 10;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    lBackupOptions = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_BACKUP_OPTIONS_LABEL.get());
    add(lBackupOptions, gbc);

    compressData = Utilities.createCheckBox(
        INFO_CTRL_PANEL_COMPRESS_DATA_LABEL.get());
    compressData.setSelected(false);

    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    add(compressData, gbc);

    encryptData = Utilities.createCheckBox(
        INFO_CTRL_PANEL_ENCRYPT_DATA_LABEL.get());

    gbc.gridy ++;
    gbc.insets.top = 5;
    add(encryptData, gbc);
    encryptData.setSelected(false);
    generateMessageDigest = Utilities.createCheckBox(
        INFO_CTRL_PANEL_GENERATE_MESSAGE_DIGEST_LABEL.get());

    gbc.gridy ++;
    add(generateMessageDigest, gbc);

    signMessageDigest = Utilities.createCheckBox(
        INFO_CTRL_PANEL_SIGN_MESSAGE_DIGEST_HASH_LABEL.get());
    gbc.insets.left = 30;
    gbc.gridy ++;
    add(signMessageDigest, gbc);
    generateMessageDigest.setSelected(false);

    changeListener = new ChangeListener()
    {
      /**
       * {@inheritDoc}
       */
      public void stateChanged(ChangeEvent ev)
      {
        backends.setEnabled(!allBackends.isSelected());
        signMessageDigest.setEnabled(generateMessageDigest.isSelected());
        boolean enable = incrementalBackup.isSelected();
        refreshList.setEnabled(enable);
        tableScroll.setEnabled(enable);
        backupList.setEnabled(enable);
      }
    };
    incrementalBackup.addChangeListener(changeListener);
    generateMessageDigest.addChangeListener(changeListener);
    allBackends.addChangeListener(changeListener);
    changeListener.stateChanged(null);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();
    updateSimpleBackendComboBoxModel(backends, lNoBackendsFound, desc);
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        allBackends.setVisible(backends.getModel().getSize() > 0);
      }
    });
    super.configurationChanged(ev);
    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_BACKUP.get());
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lPath);
    setPrimaryValid(lAvailableBackups);
    backupIDInitialized = false;

    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    if (!allBackends.isSelected())
    {
      String backendName = (String)backends.getSelectedItem();
      if (backendName == null)
      {
        errors.add(ERR_CTRL_PANEL_NO_BACKENDS_SELECTED.get());
        setPrimaryInvalid(lBackend);
      }
    }
    else
    {
      if (backends.getModel().getSize() == 0)
      {
        errors.add(ERR_CTRL_PANEL_NO_BACKENDS_AVAILABLE.get());
        setPrimaryInvalid(lBackend);
      }
    }

    String parentPath = parentDirectory.getText();
    if ((parentPath == null) || (parentPath.trim().equals("")))
    {
      errors.add(ERR_CTRL_PANEL_NO_BACKUP_PATH_PROVIDED.get());
      setPrimaryInvalid(lPath);
    }
    else
    {
      File f = new File(parentPath);
      if (f.isFile())
      {
        errors.add(ERR_CTRL_PANEL_BACKUP_PATH_IS_A_FILE.get(parentPath));
        setPrimaryInvalid(lPath);
      }
      else if (!f.exists())
      {
        errors.add(ERR_CTRL_PANEL_BACKUP_PATH_DOES_NOT_EXIST.get(parentPath));
        setPrimaryInvalid(lPath);
      }
    }
    String dir = backupID.getText();
    if ((dir == null) || (dir.trim().equals("")))
    {
      errors.add(ERR_CTRL_PANEL_NO_BACKUP_ID_PROVIDED.get());
      setPrimaryInvalid(lBackupID);
    }

    if (errors.isEmpty())
    {
      File f = new File(parentPath, dir);
      if (f.isFile())
      {
        errors.add(ERR_CTRL_PANEL_BACKUP_PATH_EXISTS.get(
            f.getAbsolutePath()));
        setPrimaryInvalid(lPath);
      }
    }

    if (incrementalBackup.isSelected())
    {
      boolean selected = backupList.isVisible() &&
      (getSelectedBackup() != null);
      if (!selected)
      {
        errors.add(ERR_CTRL_PANEL_NO_PARENT_BACKUP_SELECTED.get());
        setPrimaryInvalid(lAvailableBackups);
      }
    }

    // Check that there is not a backup with the provided ID
    final JComponent[] components =
    {
        backends, allBackends, fullBackup, incrementalBackup, parentDirectory,
        browse, backupList, refreshList, compressData, encryptData,
        generateMessageDigest, signMessageDigest
    };
    setEnabledOK(false);
    setEnabledCancel(false);
    for (int i=0; i<components.length; i++)
    {
      components[i].setEnabled(false);
    }
    final String id = backupID.getText();
    final String path = parentDirectory.getText();
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
      public Void processBackgroundTask() throws Throwable
      {
        // Open the backup directory and make sure it is valid.
        LinkedHashSet<BackupInfo> backups = new LinkedHashSet<BackupInfo>();
        try
        {
          BackupDirectory backupDir =
            BackupDirectory.readBackupDirectoryDescriptor(path);
          backups.addAll(backupDir.getBackups().values());
        }
        catch (Throwable t)
        {
          // Check the subdirectories
          File f = new File(path);

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
        }
        for (BackupInfo backup : backups)
        {
          if (backup.getBackupID().equalsIgnoreCase(id))
          {
            errors.add(ERR_CTRL_PANEL_BACKUP_ID_ALREADY_EXIST.get(id, path));
            SwingUtilities.invokeLater(new Runnable()
            {
              /**
               * {@inheritDoc}
               */
              public void run()
              {
                setPrimaryInvalid(lBackupID);
              }
            });
            break;
          }
        }
        return null;
      }
      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Void returnValue,
          Throwable t)
      {
        for (int i=0; i<components.length; i++)
        {
          components[i].setEnabled(true);
        }
        setEnabledOK(true);
        setEnabledCancel(true);
        changeListener.stateChanged(null);
        if (errors.isEmpty())
        {
          ProgressDialog progressDialog = new ProgressDialog(
              Utilities.getParentDialog(BackupPanel.this), getTitle(),
              getInfo());
          BackupTask newTask = new BackupTask(getInfo(), progressDialog);
          for (Task task : getInfo().getTasks())
          {
            task.canLaunch(newTask, errors);
          }
          if (errors.isEmpty())
          {
            Message initMsg;
            if (allBackends.isSelected())
            {
              initMsg = INFO_CTRL_PANEL_RUN_BACKUP_ALL_BACKENDS.get();
            }
            else
            {
              initMsg = INFO_CTRL_PANEL_RUN_BACKUP_SUMMARY.get(
                  backends.getSelectedItem().toString());
            }
            launchOperation(newTask,
                initMsg,
                INFO_CTRL_PANEL_RUN_BACKUP_SUCCESSFUL_SUMMARY.get(),
                INFO_CTRL_PANEL_RUN_BACKUP_SUCCESSFUL_DETAILS.get(),
                ERR_CTRL_PANEL_RUN_BACKUP_ERROR_SUMMARY.get(),
                null,
                ERR_CTRL_PANEL_RUN_BACKUP_ERROR_DETAILS,
                progressDialog);
            progressDialog.setVisible(true);
            Utilities.getParentDialog(BackupPanel.this).setVisible(false);
          }
        }
        if (errors.size() > 0)
        {
          displayErrorDialog(errors);
        }
      }
    };
    if (errors.isEmpty())
    {
      worker.startBackgroundTask();
    }
    else
    {
      worker.backgroundTaskCompleted(null, null);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lBackend);
    setPrimaryValid(lPath);
    setPrimaryValid(lAvailableBackups);

    super.cancelClicked();
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible && !backupIDInitialized)
    {
      initializeBackupID();
    }
    if (!visible)
    {
      backupIDInitialized = false;
    }
  }

  /**
   * Initialize the backup ID field with a value.
   *
   */
  private void initializeBackupID()
  {
    SimpleDateFormat dateFormat = new SimpleDateFormat(
        ServerConstants.DATE_FORMAT_COMPACT_LOCAL_TIME);
    final String id = dateFormat.format(new Date());
    backupID.setText(id);
  }

  /**
   * Class that launches the backup.
   *
   */
  protected class BackupTask extends Task
  {
    private Set<String> backendSet;
    private String dir;
    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public BackupTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      if (!allBackends.isSelected())
      {
        backendSet.add((String)backends.getSelectedItem());
      }
      else
      {
        for (BackendDescriptor backend :
          info.getServerDescriptor().getBackends())
        {
          if (!backend.isConfigBackend())
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
      dir = parentDirectory.getText();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.BACKUP;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_BACKUP_TASK_DESCRIPTION.get(
      Utilities.getStringFromCollection(backendSet, ", "), dir);
    }

    /**
     * {@inheritDoc}
     */
    public boolean canLaunch(Task taskToBeLaunched,
        Collection<Message> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (state == State.RUNNING)
      {
        // All the operations are incompatible if they apply to this
        // backend.
        Set<String> backends =
          new TreeSet<String>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        if (backends.size() > 0)
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this,
              taskToBeLaunched));
          canLaunch = false;
        }
      }
      return canLaunch;
    }

    /**
     * {@inheritDoc}
     */
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;
      try
      {
        ArrayList<String> arguments = getCommandLineArguments();

        String[] args = new String[arguments.size()];

        arguments.toArray(args);
        if (isServerRunning())
        {
          returnCode = BackUpDB.mainBackUpDB(args, false, outPrintStream,
              errorPrintStream);
        }
        else
        {
          returnCode = executeCommandLine(getCommandLinePath(), args);
        }
        if (returnCode != 0)
        {
          state = State.FINISHED_WITH_ERROR;
        }
        else
        {
          getInfo().backupCreated(
              new BackupDescriptor(
                  new File(parentDirectory.getText()),
                  new Date(),
                  fullBackup.isSelected() ? BackupDescriptor.Type.FULL :
                    BackupDescriptor.Type.INCREMENTAL,
                  backupID.getText()));
          state = State.FINISHED_SUCCESSFULLY;
        }
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getBackends()
    {
      return backendSet;
    }

    /**
     * {@inheritDoc}
     */
    protected ArrayList<String> getCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();

      args.add("--backupDirectory");
      args.add(dir);

      args.add("--backupID");
      args.add(backupID.getText());

      if (allBackends.isSelected())
      {
        args.add("--backUpAll");
      }
      else
      {
        args.add("--backendID");
        args.add((String)backends.getSelectedItem());
      }

      if (incrementalBackup.isSelected())
      {
        args.add("--incremental");
        BackupDescriptor backup = getSelectedBackup();
        args.add("--incrementalBaseID");
        args.add(backup.getID());
      }


      if (compressData.isSelected())
      {
        args.add("--compress");
      }

      if (encryptData.isSelected())
      {
        args.add("--encrypt");
      }

      if (generateMessageDigest.isSelected())
      {
        args.add("--hash");
        if (signMessageDigest.isSelected())
        {
          args.add("--signHash");
        }
      }

      args.addAll(getConnectionCommandLineArguments());

      if (isServerRunning())
      {
        args.addAll(getConfigCommandLineArguments());
      }

      return args;
    }

    /**
     * {@inheritDoc}
     */
    protected String getCommandLinePath()
    {
      return getCommandLinePath("backup");
    }
  };
}
