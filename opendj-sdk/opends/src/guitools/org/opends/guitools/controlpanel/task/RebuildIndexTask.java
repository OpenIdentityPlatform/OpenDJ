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

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The class that is used when a set of indexes must be rebuilt.
 *
 */
public class RebuildIndexTask extends IndexTask
{
  private SortedSet<AbstractIndexDescriptor> indexes =
    new TreeSet<AbstractIndexDescriptor>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param baseDNs the baseDNs corresponding to the indexes.
   * @param indexes the indexes.
   */
  public RebuildIndexTask(ControlPanelInfo info, ProgressDialog dlg,
      Collection<String> baseDNs, SortedSet<AbstractIndexDescriptor> indexes)
  {
    super(info, dlg, baseDNs);
    this.indexes.addAll(indexes);
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.REBUILD_INDEXES;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    if (baseDNs.size() == 1)
    {
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(
          baseDNs.iterator().next());
    }
    else
    {
      // Assume is in a backend
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(
          backendSet.iterator().next());
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
      boolean mustDisable = false;
      boolean mustEnable = false;
      String backendName = backendSet.iterator().next();
      if (isServerRunning())
      {
        for (BackendDescriptor backend :
          getInfo().getServerDescriptor().getBackends())
        {
          if (backendName.equals(backend.getBackendID()))
          {
            mustDisable = backend.isEnabled();
            break;
          }
        }
      }

      if (mustDisable)
      {
        setBackendEnable(backendName, false);
        mustEnable = true;
      }

      for (final String baseDN : baseDNs)
      {
        ArrayList<String> arguments = getCommandLineArguments(baseDN);

        String[] args = new String[arguments.size()];

        arguments.toArray(args);

        final StringBuilder sb = new StringBuilder();
        sb.append(getCommandLinePath("rebuild-index"));
        Collection<String> displayArgs = getObfuscatedCommandLineArguments(
            getCommandLineArguments(baseDN));
        displayArgs.removeAll(getConfigCommandLineArguments());
        for (String arg : displayArgs)
        {
          sb.append(" "+CommandBuilder.escapeValue(arg));
        }
        sb.toString();
        final ProgressDialog progressDialog = getProgressDialog();

        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            progressDialog.appendProgressHtml(Utilities.applyFont(
                INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_REBUILD_INDEX.get(baseDN)+
                "<br><b>"+sb.toString()+"</b><br><br>",
                ColorAndFontConstants.progressFont));
          }
        });

        returnCode = executeCommandLine(getCommandLinePath("rebuild-index"),
            args);

        if (returnCode != 0)
        {
          break;
        }
      }
      if (mustEnable)
      {
        setBackendEnable(backendName, true);
      }

      if (returnCode != 0)
      {
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        for (AbstractIndexDescriptor index : indexes)
        {
          getInfo().unregisterModifiedIndex(index);
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
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /**
   * Returns the command line arguments required to rebuild the indexes
   * in the specified base DN.
   * @param baseDN the base DN.
   * @return the command line arguments required to rebuild the indexes
   * in the specified base DN.
   */
  protected ArrayList<String> getCommandLineArguments(String baseDN)
  {
    ArrayList<String> args = new ArrayList<String>();

    args.add("--baseDN");
    args.add(baseDN);

    for (AbstractIndexDescriptor index : indexes)
    {
      args.add("--index");
      if (index instanceof VLVIndexDescriptor)
      {
        args.add(
            Utilities.getVLVNameInCommandLine((VLVIndexDescriptor)index));
      }
      else
      {
        args.add(index.getName());
      }
    }

    return args;
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * Enables a backend.
   * @param backendName the backend name.
   * @param enable whether to enable or disable the backend.
   * @throws OpenDsException if an error occurs.
   */
  private void setBackendEnable(final String backendName,
      final boolean enable) throws OpenDsException
      {
    ArrayList<String> args = new ArrayList<String>();
    args.add("set-backend-prop");
    args.add("--backend-name");
    args.add(backendName);
    args.add("--set");
    args.add("enabled:"+enable);

    args.addAll(getConnectionCommandLineArguments());
    args.add("--no-prompt");

    final StringBuilder sb = new StringBuilder();
    sb.append(getCommandLinePath("dsconfig"));
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }

    final ProgressDialog progressDialog = getProgressDialog();

    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        if (enable)
        {
          progressDialog.appendProgressHtml("<br><br>"+Utilities.applyFont(
              INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_ENABLE_BACKEND.get(
                  backendName)+"<br><b>"+sb.toString()+"</b><br><br>",
              ColorAndFontConstants.progressFont));
          progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_ENABLING_BACKEND.get(backendName),
              ColorAndFontConstants.progressFont));
        }
        else
        {
          progressDialog.appendProgressHtml(Utilities.applyFont(
              INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DISABLE_BACKEND.get(
                  backendName)+"<br><b>"+sb.toString()+"</b><br><br>",
              ColorAndFontConstants.progressFont));
          progressDialog.appendProgressHtml(Utilities.getProgressWithPoints(
              INFO_CTRL_PANEL_DISABLING_BACKEND.get(backendName),
              ColorAndFontConstants.progressFont));
        }
      }
    });

    ManagementContext mCtx = LDAPManagementContext.createFromContext(
        JNDIDirContextAdaptor.adapt(getInfo().getDirContext()));
    RootCfgClient root = mCtx.getRootConfiguration();
    LocalDBBackendCfgClient backend =
      (LocalDBBackendCfgClient)root.getBackend(backendName);

    if (backend.isEnabled() != enable)
    {
      backend.setEnabled(enable);
      backend.commit();
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        progressDialog.appendProgressHtml(Utilities.getProgressDone(
            ColorAndFontConstants.progressFont)+
        "<br><br>");
      }
    });
  }
}
