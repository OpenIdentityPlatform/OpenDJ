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

import java.awt.GridBagConstraints;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackupDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.BackupCreatedEvent;
import org.opends.guitools.controlpanel.event.BackupCreatedListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.tools.RestoreDB;

/**
 * The panel that appears when the user wants to restore from a backup.
 *
 */
public class RestorePanel extends BackupListPanel
implements BackupCreatedListener
{
  private static final long serialVersionUID = -205585323128518051L;
  private ListSelectionListener listener;

  /**
   * Constructor of the panel.
   *
   */
  public RestorePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_RESTORE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void backupCreated(BackupCreatedEvent ev)
  {
    boolean refreshList = false;
    File f = new File(parentDirectory.getText());
    File fBackup = ev.getBackupDescriptor().getPath();
    if (fBackup.equals(f))
    {
      refreshList = true;
    }
    else
    {
      f = f.getParentFile();
      if (f != null)
      {
        refreshList = fBackup.equals(f);
      }
    }
    if (refreshList && isVisible())
    {
      // If not visible the list will be refreshed next time the dialog is
      // opened.
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          refreshList();
        }
      });
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    info.addBackupCreatedListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      listener.valueChanged(null);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    super.configurationChanged(ev);
    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_RESTORE.get());
  }

  /**
   * {@inheritDoc}
   */
  protected void verifyBackupClicked()
  {
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
//  Launch the task in another progress dialog.
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_VERIFY_BACKUP_TITLE.get(),
        getInfo());
    RestoreTask newTask = new RestoreTask(getInfo(), dlg, true);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      BackupDescriptor backup = getSelectedBackup();
      launchOperation(newTask,
          INFO_CTRL_PANEL_VERIFYING_BACKUP_SUMMARY.get(backup.getID()),
          INFO_CTRL_PANEL_VERIFYING_BACKUP_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_VERIFYING_BACKUP_SUCCESSFUL_DETAILS.get(),
          ERR_CTRL_PANEL_VERIFYING_BACKUP_ERROR_SUMMARY.get(),
          null,
          ERR_CTRL_PANEL_VERIFYING_BACKUP_ERROR_DETAILS,
          dlg);
      dlg.setVisible(true);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;

    gbc.gridwidth = 3;
    addErrorPane(gbc);

    super.createLayout(gbc);

    listener = new ListSelectionListener()
    {
      public void valueChanged(ListSelectionEvent ev)
      {
        BackupDescriptor backup = getSelectedBackup();
        setEnabledOK((backup != null) && !errorPane.isVisible());
      }
    };
    backupList.getSelectionModel().addListSelectionListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  protected void checkOKButtonEnable()
  {
    listener.valueChanged(null);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lPath);
    setPrimaryValid(lAvailableBackups);

    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    BackupDescriptor backup = getSelectedBackup();

    boolean selected = backupList.isVisible() && (backup != null);
    if (!selected)
    {
      if (backupList.getRowCount() == 0)
      {
        setPrimaryInvalid(lPath);
        errors.add(ERR_CTRL_PANEL_NO_PARENT_BACKUP_TO_VERIFY.get());
      }
      else
      {
        errors.add(ERR_CTRL_PANEL_REQUIRED_BACKUP_TO_VERIFY.get());
      }
      setPrimaryInvalid(lAvailableBackups);
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog = new ProgressDialog(
          Utilities.getParentDialog(this), getTitle(), getInfo());
      RestoreTask newTask = new RestoreTask(getInfo(), progressDialog, false);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
//    Ask for confirmation
      boolean confirmed = true;
      if (errors.isEmpty())
      {
        confirmed = displayConfirmationDialog(
            INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
            INFO_CTRL_PANEL_CONFIRM_RESTORE_DETAILS.get());
      }

      if ((errors.isEmpty()) && confirmed)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_RESTORING_SUMMARY.get(backup.getID()),
            INFO_CTRL_PANEL_RESTORING_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_RESTORING_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_RESTORING_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_RESTORING_ERROR_DETAILS,
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

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lPath);
    setPrimaryValid(lAvailableBackups);

    super.cancelClicked();
  }

  /**
   * The task in charge of restoring or verifying the backup.
   *
   */
  protected class RestoreTask extends Task
  {
    private Set<String> backendSet;
    private String dir;
    private String backupID;
    private boolean verify;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     * @param verify whether this is an actual restore or a verify of the
     * backup.
     */
    public RestoreTask(ControlPanelInfo info, ProgressDialog dlg,
        boolean verify)
    {
      super(info, dlg);
      this.verify = verify;
      dir = parentDirectory.getText();
      BackupDescriptor backup = getSelectedBackup();
      backupID = backup.getID();
      backendSet = new HashSet<String>();
      for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
      {
        if (!backend.isConfigBackend())
        {
          backendSet.add(backend.getBackendID());
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.RESTORE;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      if (verify)
      {
        return INFO_CTRL_PANEL_VERIFY_TASK_DESCRIPTION.get(backupID, dir);
      }
      else
      {
        return INFO_CTRL_PANEL_RESTORE_TASK_DESCRIPTION.get(backupID, dir);
      }
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
          returnCode = RestoreDB.mainRestoreDB(args, false, outPrintStream,
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
          if (!verify)
          {
            for (String backend : getBackends())
            {
              getInfo().unregisterModifiedIndexesInBackend(backend);
            }
          }
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
      args.add(backupID);

      if (verify)
      {
        args.add("--dry-run");
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
      return getCommandLinePath("restore");
    }
  };
}
