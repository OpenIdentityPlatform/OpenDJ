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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.DeleteBaseDNAndBackendTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * The panel displayed when the user clicks on 'Delete Backend...' in the
 * browse entries dialog.
 *
 */
public class DeleteBackendPanel extends DeleteBaseDNPanel
{
  private static final long serialVersionUID = 8744925738292396658L;

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_DELETE_BACKEND_TITLE.get();
  }

  /**
   * Returns the no backend found label.
   * @return the no backend found label.
   */
  protected Message getNoElementsFoundLabel()
  {
    return INFO_CTRL_PANEL_NO_BACKENDS_FOUND_LABEL.get();
  }

  /**
   * Returns the list label.
   * @return the list label.
   */
  protected Message getListLabel()
  {
    return INFO_CTRL_PANEL_SELECT_BACKENDS_TO_DELETE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ServerDescriptor desc = ev.getNewDescriptor();
    final SortedSet<String> newElements = new TreeSet<String>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (!backend.isConfigBackend())
      {
        newElements.add(backend.getBackendID());
      }
    }
    updateList(newElements);
    updateErrorPaneAndOKButtonIfAuthRequired(getInfo().getServerDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_BACKEND_DELETE.get());
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this), getTitle(), getInfo());
    Object[] backends = list.getSelectedValues();
    ArrayList<BackendDescriptor> backendsToDelete =
      new ArrayList<BackendDescriptor>();
    for (Object o : backends)
    {
      String id = (String)o;
      for (BackendDescriptor backend :
        getInfo().getServerDescriptor().getBackends())
      {
        if (backend.getBackendID().equalsIgnoreCase(id))
        {
          backendsToDelete.add(backend);
          break;
        }
      }
    }
    DeleteBaseDNAndBackendTask newTask = new DeleteBaseDNAndBackendTask(
        getInfo(), progressDialog, backendsToDelete,
        new HashSet<BaseDNDescriptor>());
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      Message confirmationMessage = getConfirmationMessage(backendsToDelete);
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          confirmationMessage))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_BACKENDS_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETING_BACKENDS_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_BACKENDS_SUCCESSFUL.get(),
            ERR_CTRL_PANEL_DELETING_BACKENDS_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_BACKENDS_ERROR_DETAILS.get(),
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
      Collection<BackendDescriptor> backendsToDelete)
  {
    MessageBuilder mb = new MessageBuilder();
    mb.append(INFO_CTRL_PANEL_CONFIRMATION_DELETE_BACKENDS_DETAILS.get());
    for (BackendDescriptor backend : backendsToDelete)
    {
      mb.append("<br> - "+backend.getBackendID());
    }
    mb.append("<br><br>");
    mb.append(INFO_CTRL_PANEL_DO_YOU_WANT_TO_CONTINUE.get());
    return mb.toMessage();
  }
}

