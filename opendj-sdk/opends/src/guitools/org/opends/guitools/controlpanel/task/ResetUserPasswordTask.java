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
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The task called when we want to reset the password of the user.
 *
 */
public class ResetUserPasswordTask extends Task
{
  private Set<String> backendSet;
  private BasicNode node;
  private char[] newPassword;
  private BrowserController controller;
  private DN dn;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param node the node corresponding to the entry whose password is going
   * to be reset.
   * @param controller the BrowserController.
   * @param pwd the new password.
   */
  public ResetUserPasswordTask(ControlPanelInfo info, ProgressDialog dlg,
      BasicNode node, BrowserController controller, char[] pwd)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    this.node = node;
    this.newPassword = pwd;
    this.controller = controller;
    try
    {
      dn = DN.decode(node.getDN());
      for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (dn.isDescendantOf(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
    }
    catch (OpenDsException ode)
    {
      throw new IllegalStateException("Could not parse DN: "+node.getDN(), ode);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.MODIFY_ENTRY;
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
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_RESET_USER_PASSWORD_TASK_DESCRIPTION.get(
        node.getDN());
  }

  /**
   * {@inheritDoc}
   */
  public boolean regenerateDescriptor()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (!isServerRunning())
    {
      if (state == State.RUNNING)
      {
        // All the operations are incompatible if they apply to this
        // backend for safety.  This is a short operation so the limitation
        // has not a lot of impact.
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
      useAdminCtx = controller.isConfigurationNode(node);
      InitialLdapContext ctx =
        controller.findConnectionForDisplayedEntry(node);
      BasicAttribute attr =
        new BasicAttribute(ServerConstants.ATTR_USER_PASSWORD);
      attr.add(new String(newPassword));
      ModificationItem mod =
        new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attr);
      ModificationItem[] mods = {mod};
      final ArrayList<ModificationItem> modifications =
        new ArrayList<ModificationItem>();
      modifications.add(mod);

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          printEquivalentCommand(dn, newPassword, useAdminCtx);
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_RESETTING_USER_PASSWORD.get(node.getDN()),
                  ColorAndFontConstants.progressFont));
        }
      });

      ctx.modifyAttributes(Utilities.getJNDIName(node.getDN()), mods);

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          TreePath treePath =
            new TreePath(controller.getTreeModel().getPathToRoot(node));
          if (treePath != null)
          {
            BrowserNodeInfo nodeInfo = controller.getNodeInfoFromPath(treePath);
            if (nodeInfo != null)
            {
              controller.notifyEntryChanged(nodeInfo);
            }
            controller.getTree().removeSelectionPath(treePath);
            controller.getTree().setSelectionPath(treePath);
          }
        }
      });
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /**
   * Prints the equivalent modify command line in the progress dialog.
   * @param dn the dn of the modified entry.
   * @param newPassword the new password.
   * @param useAdminCtx use the administration connector.
   */
  private void printEquivalentCommand(DN dn, char[] newPassword,
      boolean useAdminCtx)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLinePath("ldappasswordmodify"));
    args.add("--authzID");
    args.add("dn:"+dn);
    args.add("--newPassword");
    args.add(Utilities.OBFUSCATED_VALUE);
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    StringBuilder sb = new StringBuilder();
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }
    getProgressDialog().appendProgressHtml(Utilities.applyFont(
        INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_RESET_PASSWORD.get().toString()+
        "<br><b>"+sb.toString()+"</b><br><br>",
        ColorAndFontConstants.progressFont));
  }
}
