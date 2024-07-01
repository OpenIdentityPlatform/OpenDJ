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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
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
import org.opends.server.types.DirectoryException;
import org.opends.server.util.ServerConstants;

/** The task that is launched when an entry must be deleted. */
public class DeleteEntryTask extends Task
{
  private Set<String> backendSet;
  private DN lastDn;
  private int nDeleted;
  private int nToDelete = -1;
  private BrowserController controller;
  private TreePath[] paths;
  private long lastProgressTime;
  private boolean equivalentCommandWithControlPrinted;
  private boolean equivalentCommandWithoutControlPrinted;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param paths the tree paths of the entries that must be deleted.
   * @param controller the Browser Controller.
   */
  public DeleteEntryTask(ControlPanelInfo info, ProgressDialog dlg,
      TreePath[] paths, BrowserController controller)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.controller = controller;
    this.paths = paths;
    SortedSet<DN> entries = new TreeSet<>();
    boolean canPrecalculateNumberOfEntries = true;
    nToDelete = paths.length;
    for (TreePath path : paths)
    {
      BasicNode node = (BasicNode)path.getLastPathComponent();
      entries.add(node.getDN());
    }
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        for (DN dn : entries)
        {
          if (dn.isSubordinateOrEqualTo(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
            break;
          }
        }
      }
    }
    if (!canPrecalculateNumberOfEntries)
    {
      nToDelete = -1;
    }
  }

  @Override
  public Type getType()
  {
    return Type.DELETE_ENTRY;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_DELETE_ENTRY_TASK_DESCRIPTION.get();
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
      // backend for safety.
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

    ArrayList<DN> alreadyDeleted = new ArrayList<>();
    ArrayList<BrowserNodeInfo> toNotify = new ArrayList<>();
    try
    {
      for (TreePath path : paths)
      {
        BasicNode node = (BasicNode)path.getLastPathComponent();
        DN dn = node.getDN();
        if (!isAlreadyDeleted(alreadyDeleted, dn))
        {
          ConnectionWithControls conn = controller.findConnectionForDisplayedEntry(node);
          useAdminCtx = controller.isConfigurationNode(node);
          if (node.hasSubOrdinates())
          {
            deleteSubtreeWithControl(conn, dn, path, toNotify);
          }
          else
          {
            deleteSubtreeRecursively(conn, dn, path, toNotify);
          }
          alreadyDeleted.add(dn);
        }
      }
      if (!toNotify.isEmpty())
      {
        final List<BrowserNodeInfo> fToNotify = new ArrayList<>(toNotify);
        toNotify.clear();
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            notifyEntriesDeleted(fToNotify);
          }
        });
      }
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
    if (nDeleted > 1)
    {
      getProgressDialog().appendProgressHtml(Utilities.applyFont(
          "<br>"+INFO_CTRL_PANEL_ENTRIES_DELETED.get(nDeleted),
          ColorAndFontConstants.progressFont));
    }
  }

  private boolean isAlreadyDeleted(ArrayList<DN> dns, DN dnToFind)
  {
    for (DN dn : dns)
    {
      if (dnToFind.isSubordinateOrEqualTo(dn))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Notifies that some entries have been deleted.  This will basically update
   * the browser controller so that the tree reflects the changes that have
   * been made.
   * @param deletedNodes the nodes that have been deleted.
   */
  private void notifyEntriesDeleted(Collection<BrowserNodeInfo> deletedNodes)
  {
    TreePath pathToSelect = null;
    for (BrowserNodeInfo nodeInfo : deletedNodes)
    {
      TreePath parentPath = controller.notifyEntryDeleted(nodeInfo);
      if (pathToSelect != null)
      {
        if (parentPath.getPathCount() < pathToSelect.getPathCount())
        {
          pathToSelect = parentPath;
        }
      }
      else
      {
        pathToSelect = parentPath;
      }
    }
    if (pathToSelect != null)
    {
      TreePath selectedPath = controller.getTree().getSelectionPath();
      if (selectedPath == null)
      {
        controller.getTree().setSelectionPath(pathToSelect);
      }
      else if (!selectedPath.equals(pathToSelect) &&
          pathToSelect.getPathCount() < selectedPath.getPathCount())
      {
        controller.getTree().setSelectionPath(pathToSelect);
      }
    }
  }

  private void deleteSubtreeRecursively(ConnectionWithControls conn, DN dnToRemove, TreePath path,
      List<BrowserNodeInfo> toNotify) throws IOException, DirectoryException
  {
    lastDn = dnToRemove;

    long t = System.currentTimeMillis();
    boolean canDelete = nToDelete > 0 && nToDelete > nDeleted;
    boolean displayProgress =
      canDelete && ((nDeleted % 20) == 0 || t - lastProgressTime > 5000);

    if (displayProgress)
    {
      // Only display the first entry equivalent command-line.
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          if (!equivalentCommandWithoutControlPrinted)
          {
            printEquivalentCommandToDelete(lastDn, false);
            equivalentCommandWithoutControlPrinted = true;
          }
          getProgressDialog().setSummary(
              LocalizableMessage.raw(
                  Utilities.applyFont(
                      INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(lastDn),
                      ColorAndFontConstants.defaultFont)));
        }
      });
    }

    String filter = "(|(objectClass=*)(objectclass=ldapsubentry))";
    SearchRequest request = newSearchRequest(dnToRemove, SINGLE_LEVEL, Filter.valueOf(filter), NO_ATTRIBUTES);
    try (ConnectionEntryReader entryDNs = conn.search(request))
    {
      while (entryDNs.hasNext())
      {
        SearchResultEntry sr = entryDNs.readEntry();
        if (!sr.getName().equals(""))
        {
          deleteSubtreeRecursively(conn, sr.getName(), null, toNotify);
        }
      }
    }
    catch (EntryNotFoundException e)
    {
      // The entry is not there: it has been removed
    }

    try
    {
      conn.delete(newDeleteRequest(dnToRemove));
      if (path != null)
      {
        toNotify.add(controller.getNodeInfoFromPath(path));
      }
      nDeleted ++;
      if (displayProgress)
      {
        lastProgressTime = t;
        final Collection<BrowserNodeInfo> fToNotify;
        if (!toNotify.isEmpty())
        {
          fToNotify = new ArrayList<>(toNotify);
          toNotify.clear();
        }
        else
        {
          fToNotify = null;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(
                (100 * nDeleted) / nToDelete);
            if (fToNotify != null)
            {
              notifyEntriesDeleted(fToNotify);
            }
          }
        });
      }
    }
    catch (EntryNotFoundException ignored)
    {
      // The entry is not there: it has been removed
    }
  }

  private void deleteSubtreeWithControl(ConnectionWithControls conn, DN dn, TreePath path,
      List<BrowserNodeInfo> toNotify) throws LdapException
  {
    lastDn = dn;
    long t = System.currentTimeMillis();
    //  Only display the first entry equivalent command-line.
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        if (!equivalentCommandWithControlPrinted)
        {
          printEquivalentCommandToDelete(lastDn, true);
          equivalentCommandWithControlPrinted = true;
        }
        getProgressDialog().setSummary(
            LocalizableMessage.raw(
                Utilities.applyFont(
                    INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(lastDn),
                    ColorAndFontConstants.defaultFont)));
      }
    });

    conn.delete(newDeleteRequest(dn).addControl(SubtreeDeleteRequestControl.newControl(true)));

    nDeleted ++;
    lastProgressTime = t;
    if (path != null)
    {
      toNotify.add(controller.getNodeInfoFromPath(path));
    }
    final Collection<BrowserNodeInfo> fToNotify;
    if (!toNotify.isEmpty())
    {
      fToNotify = new ArrayList<>(toNotify);
      toNotify.clear();
    }
    else
    {
      fToNotify = null;
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        getProgressDialog().getProgressBar().setIndeterminate(false);
        getProgressDialog().getProgressBar().setValue(
            (100 * nDeleted) / nToDelete);
        if (fToNotify != null)
        {
          notifyEntriesDeleted(fToNotify);
        }
      }
    });
  }

  /**
   * Prints in the progress dialog the equivalent command-line to delete a
   * subtree.
   * @param dn the DN of the subtree to be deleted.
   * @param usingControl whether we must include the control or not.
   */
  private void printEquivalentCommandToDelete(DN dn, boolean usingControl)
  {
    ArrayList<String> args = new ArrayList<>(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    if (usingControl)
    {
      args.add("-J");
      args.add(ServerConstants.OID_SUBTREE_DELETE_CONTROL);
    }
    args.add(dn.toString());
    printEquivalentCommandLine(getCommandLinePath("ldapdelete"),
        args,
        INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ENTRY.get(dn));
  }
}
