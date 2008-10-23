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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.NewEntryTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;

/**
 * Abstract class used to refactor some code among the different panels that are
 * used to create a new entry.
 *
 */
public abstract class AbstractNewEntryPanel extends StatusGenericPanel
{
  /**
   * The parent node that was selected when the user clicked on the new entry
   * action.
   */
  protected BasicNode parentNode;

  /**
   * The browser controller.
   */
  protected BrowserController controller;

  /**
   * Sets the parent and the browser controller for this panel.
   * @param parentNode the selected parent node (or <CODE>null</CODE> if no
   * parent node was selected).
   * @param controller the browser controller.
   */
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    this.parentNode = parentNode;
    this.controller = controller;
  }

  /**
   * Returns the title for the progress dialog.
   * @return the title for the progress dialog.
   */
  protected abstract Message getProgressDialogTitle();
  /**
   * Returns the LDIF representation of the new entry.
   * @return the LDIF representation of the new entry.
   */
  protected abstract String getLDIF();

  /**
   * Updates the list of errors by checking the syntax of the entry.
   * @param errors the list of errors that must be updated.
   */
  protected abstract void checkSyntax(ArrayList<Message> errors);

  /**
   * Returns <CODE>true</CODE> if the syntax of the entry must be checked in
   * the background and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the syntax of the entry must be checked in
   * the background and <CODE>false</CODE> otherwise.
   */
  protected boolean checkSyntaxBackground()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    final ArrayList<Message> errors = new ArrayList<Message>();

    if (checkSyntaxBackground())
    {
      BackgroundTask<Void> worker = new BackgroundTask<Void>()
      {
        public Void processBackgroundTask()
        {
          try
          {
            Thread.sleep(2000);
          }
          catch (Throwable t)
          {
          }
          checkSyntax(errors);
          return null;
        }
        public void backgroundTaskCompleted(Void returnValue, Throwable t)
        {
          if (t != null)
          {
            errors.add(ERR_CTRL_PANEL_UNEXPECTED_DETAILS.get(t.toString()));
          }
          displayMainPanel();
          setEnabledCancel(true);
          setEnabledOK(true);
          handleErrorsAndLaunchTask(errors);
        }
      };
      displayMessage(INFO_CTRL_PANEL_CHECKING_SUMMARY.get());
      setEnabledCancel(false);
      setEnabledOK(false);
      worker.startBackgroundTask();
    }
    else
    {
      checkSyntax(errors);
      handleErrorsAndLaunchTask(errors);
    }
  }

  /**
   * Checks that there are not errors in the list and launches a new entry
   * task.
   * @param errors the list of errors.
   */
  private void handleErrorsAndLaunchTask(ArrayList<Message> errors)
  {
    Entry entry = null;
    if (errors.size() == 0)
    {
      try
      {
        entry = getEntry();
      }
      catch (Throwable t)
      {
        // Unexpected error: getEntry() should work after calling checkSyntax
        throw new IllegalStateException("Unexpected error: "+t, t);
      }
      String dn = entry.getDN().toString();
      // Checking for the existence of an entry is fast enough so we can do
      // it on the event thread.
      if (entryExists(dn))
      {
        errors.add(ERR_CTRL_PANEL_ENTRY_ALREADY_EXISTS.get(dn));
      }
    }
    if (errors.size() == 0)
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this), getProgressDialogTitle(), getInfo());
      try
      {
        NewEntryTask newTask =
          new NewEntryTask(getInfo(), dlg, entry, getLDIF(),
              parentNode, controller);
        for (Task task : getInfo().getTasks())
        {
          task.canLaunch(newTask, errors);
        }
        if (errors.size() == 0)
        {
          launchOperation(newTask,
              INFO_CTRL_PANEL_CREATING_NEW_ENTRY_SUMMARY.get(),
              INFO_CTRL_PANEL_CREATING_NEW_ENTRY_SUCCESSFUL_SUMMARY.get(),
              INFO_CTRL_PANEL_CREATING_NEW_ENTRY_SUCCESSFUL_DETAILS.get(),
              ERR_CTRL_PANEL_CREATING_NEW_ENTRY_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_CREATING_NEW_ENTRY_ERROR_DETAILS.get(),
              null,
              dlg);
          dlg.setVisible(true);
          Utilities.getParentDialog(this).setVisible(false);
        }
      }
      catch (Throwable t)
      {
        // Unexpected error: getEntry() should work after calling checkSyntax
        throw new IllegalStateException("Unexpected error: "+t, t);
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
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    updateErrorPaneIfServerRunningAndAuthRequired(ev.getNewDescriptor(),
        INFO_CTRL_PANEL_NEW_ENTRY_REQUIRES_SERVER_RUNNING.get(),
        INFO_CTRL_PANEL_NEW_ENTRY_REQUIRES_AUTHENTICATION.get());
  }

  /**
   * Returns the entry object representing what the user provided as data.
   * @return the entry object representing what the user provided as data.
   * @throws LDIFException if there is an error with the LDIF syntax.
   * @throws IOException if there is an error creating the internal stream.
   */
  protected Entry getEntry() throws LDIFException, IOException
  {
    Entry entry;
    LDIFImportConfig ldifImportConfig = null;
    try
    {
      String ldif = getLDIF();

      if (ldif.trim().length() == 0)
      {
        throw new LDIFException(ERR_LDIF_REPRESENTATION_REQUIRED.get());
      }

      ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
      LDIFReader reader = new LDIFReader(ldifImportConfig);
      entry = reader.readEntry(checkSchema());
      if (entry == null)
      {
        throw new LDIFException(ERR_LDIF_REPRESENTATION_REQUIRED.get());
      }
      else
      {
        if (entry.getObjectClasses().size() == 0)
        {
          throw new LDIFException(ERR_OBJECTCLASS_FOR_ENTRY_REQUIRED.get());
        }
      }
    }
    finally
    {
      if (ldifImportConfig != null)
      {
        ldifImportConfig.close();
      }
    }
    return entry;
  }

  /**
   * Returns <CODE>true</CODE> if the schema must be checked and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the schema must be checked and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean checkSchema()
  {
    return getInfo().getServerDescriptor().isSchemaEnabled();
  }
}
