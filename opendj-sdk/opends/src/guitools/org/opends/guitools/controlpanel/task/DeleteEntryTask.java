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
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.BasicControl;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The task that is launched when an entry must be deleted.
 */
public class DeleteEntryTask extends Task
{
  private Set<String> backendSet;
  private DN lastDn;
  private int nDeleted = 0;
  private int nToDelete = -1;
  private BrowserController controller;
  private TreePath[] paths;
  private long lastProgressTime;
  private boolean equivalentCommandWithControlPrinted = false;
  private boolean equivalentCommandWithoutControlPrinted = false;

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
    backendSet = new HashSet<String>();
    this.controller = controller;
    this.paths = paths;
    SortedSet<DN> entries = new TreeSet<DN>();
    boolean canPrecalculateNumberOfEntries = true;
    nToDelete = paths.length;
    for (TreePath path : paths)
    {
      BasicNode node = (BasicNode)path.getLastPathComponent();
      try
      {
        DN dn = DN.decode(node.getDN());
        entries.add(dn);
      }
      catch (DirectoryException de)
      {
        throw new IllegalStateException("Unexpected error parsing dn: "+
            node.getDN(), de);
      }
    }
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        for (DN dn : entries)
        {
          if (dn.isDescendantOf(baseDN.getDn()))
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

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    return Type.DELETE_ENTRY;
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
    return INFO_CTRL_PANEL_DELETE_ENTRY_TASK_DESCRIPTION.get();
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
        // backend for safety.
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
  public boolean regenerateDescriptor()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    ArrayList<DN> alreadyDeleted = new ArrayList<DN>();
    ArrayList<BrowserNodeInfo> toNotify = new ArrayList<BrowserNodeInfo>();
    try
    {
      for (TreePath path : paths)
      {
        BasicNode node = (BasicNode)path.getLastPathComponent();
        try
        {
          DN dn = DN.decode(node.getDN());
          boolean isDnDeleted = false;
          for (DN deletedDn : alreadyDeleted)
          {
            if (dn.isDescendantOf(deletedDn))
            {
              isDnDeleted = true;
              break;
            }
          }
          if (!isDnDeleted)
          {
            InitialLdapContext ctx =
              controller.findConnectionForDisplayedEntry(node);
            if (node.getNumSubOrdinates() > 0)
            {
              deleteSubtreeWithControl(ctx, dn, path, toNotify);
            }
            else
            {
              deleteSubtreeRecursively(ctx, dn, path, toNotify);
            }
            alreadyDeleted.add(dn);
          }
        }
        catch (DirectoryException de)
        {
          throw new IllegalStateException("Unexpected error parsing dn: "+
              node.getDN(), de);
        }
      }
      if (toNotify.size() > 0)
      {
        final List<BrowserNodeInfo> fToNotify =
          new ArrayList<BrowserNodeInfo>(toNotify);
        toNotify.clear();
        SwingUtilities.invokeLater(new Runnable()
        {
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
          (pathToSelect.getPathCount() < selectedPath.getPathCount()))
      {
        controller.getTree().setSelectionPath(pathToSelect);
      }
    }
  }

  private void deleteSubtreeRecursively(InitialLdapContext ctx, DN dnToRemove,
      TreePath path, ArrayList<BrowserNodeInfo> toNotify)
  throws NamingException, DirectoryException
  {
    lastDn = dnToRemove;

    long t = System.currentTimeMillis();
    boolean displayProgress =
      (((nDeleted % 20) == 0) || ((t - lastProgressTime) > 5000))  &&
      (nToDelete > 0) && (nToDelete > nDeleted);

    if (displayProgress)
    {
      // Only display the first entry equivalent command-line.
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          if (!equivalentCommandWithoutControlPrinted)
          {
            printEquivalentCommandToDelete(lastDn, false);
            equivalentCommandWithoutControlPrinted = true;
          }
          getProgressDialog().setSummary(
              Message.raw(
                  Utilities.applyFont(
                      INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(
                          lastDn.toString()).toString(),
                          ColorAndFontConstants.defaultFont)));
        }
      });
    }

    try
    {
      SearchControls ctls = new SearchControls();
      ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      String filter =
        "(|(objectClass=*)(objectclass=ldapsubentry))";
      ctls.setReturningAttributes(new String[] {"dn"});
      NamingEnumeration<SearchResult> entryDNs =
        ctx.search(Utilities.getJNDIName(dnToRemove.toString()), filter, ctls);

      DN entryDNFound = dnToRemove;
      while (entryDNs.hasMore())
      {
        SearchResult sr = entryDNs.next();
        if (!sr.getName().equals(""))
        {
          CustomSearchResult res =
            new CustomSearchResult(sr, dnToRemove.toString());
          entryDNFound = DN.decode(res.getDN());
          deleteSubtreeRecursively(ctx, entryDNFound, null, toNotify);
        }
      }

    } catch (NameNotFoundException nnfe) {
      // The entry is not there: it has been removed
    }

    try
    {
      ctx.destroySubcontext(Utilities.getJNDIName(dnToRemove.toString()));
      if (path != null)
      {
        toNotify.add(controller.getNodeInfoFromPath(path));
      }
      nDeleted ++;
      if (displayProgress)
      {
        lastProgressTime = t;
        final Collection<BrowserNodeInfo> fToNotify;
        if (toNotify.size() > 0)
        {
          fToNotify = new ArrayList<BrowserNodeInfo>(toNotify);
          toNotify.clear();
        }
        else
        {
          fToNotify = null;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
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
    } catch (NameNotFoundException nnfe)
    {
      // The entry is not there: it has been removed
    }
  }

  private void deleteSubtreeWithControl(InitialLdapContext ctx, DN dn,
      TreePath path, ArrayList<BrowserNodeInfo> toNotify)
  throws NamingException
  {
    lastDn = dn;
    long t = System.currentTimeMillis();
    //  Only display the first entry equivalent command-line.
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        if (!equivalentCommandWithControlPrinted)
        {
          printEquivalentCommandToDelete(lastDn, true);
          equivalentCommandWithControlPrinted = true;
        }
        getProgressDialog().setSummary(
            Message.raw(
                Utilities.applyFont(
                    INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(
                        lastDn.toString()).toString(),
                        ColorAndFontConstants.defaultFont)));
      }
    });
    //  Use a copy of the dir context since we are using an specific
    // control to delete the subtree and this can cause
    // synchronization problems when the tree is refreshed.
    InitialLdapContext ctx1 = null;
    try
    {
      ctx1 = ConnectionUtils.cloneInitialLdapContext(ctx,
          ConnectionUtils.getDefaultLDAPTimeout(),
          getInfo().getTrustManager(), null);
      Control[] ctls = {new BasicControl(Utilities.SUBTREE_CTRL_OID)};
      ctx1.setRequestControls(ctls);
      ctx1.destroySubcontext(Utilities.getJNDIName(dn.toString()));
    }
    finally
    {
      try
      {
        ctx1.close();
      }
      catch (Throwable th)
      {
      }
    }
    nDeleted ++;
    lastProgressTime = t;
    if (path != null)
    {
      toNotify.add(controller.getNodeInfoFromPath(path));
    }
    final Collection<BrowserNodeInfo> fToNotify;
    if (toNotify.size() > 0)
    {
      fToNotify = new ArrayList<BrowserNodeInfo>(toNotify);
      toNotify.clear();
    }
    else
    {
      fToNotify = null;
    }
    SwingUtilities.invokeLater(new Runnable()
    {
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
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLinePath("ldapdelete"));
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments()));
    if (usingControl)
    {
      args.add("-J");
      args.add(Utilities.SUBTREE_CTRL_OID);
    }
    args.add(dn.toString());
    StringBuilder sb = new StringBuilder();
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }

    getProgressDialog().appendProgressHtml(Utilities.applyFont(
        INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_ENTRY.get(dn.toString())+
        "<br><b>"+
        sb.toString()+"</b><br><br>",
        ColorAndFontConstants.progressFont));
  }
}
