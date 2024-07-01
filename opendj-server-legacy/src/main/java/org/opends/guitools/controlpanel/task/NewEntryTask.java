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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.DN;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.browser.ConnectionWithControls;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Entry;

/** The task launched when we must create an entry. */
public class NewEntryTask extends Task
{
  private Entry newEntry;
  private String ldif;
  private Set<String> backendSet;
  private BasicNode parentNode;
  private BrowserController controller;
  private DN dn;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param newEntry the entry containing the new values.
   * @param ldif the LDIF representation of the new entry.
   * @param controller the BrowserController.
   * @param parentNode the parent node in the tree of the entry that we want
   * to create.
   */
  public NewEntryTask(ControlPanelInfo info, ProgressDialog dlg,
      Entry newEntry, String ldif,
      BasicNode parentNode, BrowserController controller)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.newEntry = newEntry;
    this.ldif = ldif;
    this.parentNode = parentNode;
    this.controller = controller;
    dn = newEntry.getName();
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (dn.isSubordinateOrEqualTo(baseDN.getDn()))
        {
          backendSet.add(backend.getBackendID());
        }
      }
    }
  }

  @Override
  public Type getType()
  {
    return Type.NEW_ENTRY;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_TASK_DESCRIPTION.get(dn);
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    if (!isServerRunning()
        && state == State.RUNNING
        && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean regenerateDescriptor()
  {
    return false;
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      if (parentNode != null)
      {
        ConnectionWithControls conn = controller.findConnectionForDisplayedEntry(parentNode);
        useAdminCtx = controller.isConfigurationNode(parentNode);
        printProgressCreatingEntry();
        conn.add(newAddRequest(Converters.from(newEntry)));
      }
      else
      {
        ConnectionWrapper conn = getInfo().getConnection();
        useAdminCtx = true;
        printProgressCreatingEntry();
        conn.getConnection().add(Converters.from(newEntry));
      }



      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          final DN newEntryName = newEntry.getName();
          boolean entryInserted = false;
          if (parentNode != null)
          {
            boolean isReallyParentNode = parentNode.getDN().equals(newEntryName.parent());
            if (isReallyParentNode)
            {
              insertNode(parentNode, newEntryName, isBaseDN(newEntryName));
              entryInserted = true;
            }
          }
          if (!entryInserted)
          {
            BasicNode root = (BasicNode)controller.getTreeModel().getRoot();
            BasicNode realParentNode = findParentNode(newEntryName, root);
            if (realParentNode != null)
            {
              insertNode(realParentNode, newEntryName, false);
            }
            else if (isBaseDN(newEntryName))
            {
              int nRootChildren = controller.getTreeModel().getChildCount(controller.getTreeModel().getRoot());
              if (nRootChildren > 1)
              {
                // Insert in the root.
                insertNode(root, newEntryName, true);
              }
            }
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

  private void printProgressCreatingEntry()
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        printEquivalentCommand();
        getProgressDialog().appendProgressHtml(
            Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_CREATING_ENTRY.get(dn),
                ColorAndFontConstants.progressFont));
      }
    });
  }

  /** Prints the equivalent command-line in the progress dialog. */
  private void printEquivalentCommand()
  {
    ArrayList<String> args = new ArrayList<>(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    args.add("--defaultAdd");
    String equiv = getEquivalentCommandLine(getCommandLinePath("ldapmodify"),
        args);
    StringBuilder sb = new StringBuilder();
    sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_ENTRY.get()).append("<br><b>");
    sb.append(equiv);
    sb.append("<br>");
    String[] lines = ldif.split("\n");
    for (String line : lines)
    {
      sb.append(obfuscateLDIFLine(line));
      sb.append("<br>");
    }
    sb.append("</b><br>");
    getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(),
        ColorAndFontConstants.progressFont));
  }

  private BasicNode findParentNode(DN dn, BasicNode root)
  {
    BasicNode parentNode = null;
    int nRootChildren = controller.getTreeModel().getChildCount(root);
    for (int i=0; i<nRootChildren; i++)
    {
      BasicNode node = (BasicNode) controller.getTreeModel().getChild(root, i);
      DN nodeDN = node.getDN();
      if (dn.isSubordinateOrEqualTo(nodeDN))
      {
        if (dn.size() == nodeDN.size() + 1)
        {
          parentNode = node;
          break;
        }
        else
        {
          parentNode = findParentNode(dn, node);
          break;
        }
      }
    }
    return parentNode;
  }

  private void insertNode(BasicNode parentNode, DN dn, boolean isSuffix)
  {
    TreePath parentPath = new TreePath(controller.getTreeModel().getPathToRoot(parentNode));
    BrowserNodeInfo nodeInfo = controller.getNodeInfoFromPath(parentPath);
    if (nodeInfo != null)
    {
      TreePath newPath;
      if (isSuffix)
      {
        newPath = controller.addSuffix(dn, parentNode.getDN());
      }
      else
      {
        newPath = controller.notifyEntryAdded(controller.getNodeInfoFromPath(parentPath), dn);
      }
      if (newPath != null)
      {
        controller.getTree().setSelectionPath(newPath);
        controller.getTree().scrollPathToVisible(newPath);
      }
    }
  }

  private boolean isBaseDN(DN dn)
  {
    for (BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (baseDN.getDn().equals(dn))
        {
          return true;
        }
      }
    }
    return false;
  }
}
