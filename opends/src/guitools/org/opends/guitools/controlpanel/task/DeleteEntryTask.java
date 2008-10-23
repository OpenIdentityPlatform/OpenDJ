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
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
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
      /*
      if (node.getNumSubOrdinates() != -1)
      {
        nToDelete += node.getNumSubOrdinates();
      }
      else if (node.isLeaf())
      {
        canPrecalculateNumberOfEntries = false;
      }
      */
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
    final ArrayList<BrowserNodeInfo> toNotify =
      new ArrayList<BrowserNodeInfo>();
    int deletedSinceLastNotify = 0;
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
            deleteSubtree(ctx, dn);
            alreadyDeleted.add(dn);
            toNotify.add(controller.getNodeInfoFromPath(path));
            deletedSinceLastNotify = nDeleted - deletedSinceLastNotify;
            if (deletedSinceLastNotify >= 10)
            {
              SwingUtilities.invokeAndWait(new Runnable()
              {
                public void run()
                {
                  notifyEntriesDeleted(toNotify);
                  toNotify.clear();
                }
              });
            }
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
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            notifyEntriesDeleted(toNotify);
            toNotify.clear();
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
   * @param deleteNodes the nodes that have been deleted.
   */
  private void notifyEntriesDeleted(Collection<BrowserNodeInfo> deleteNodes)
  {
    TreePath pathToSelect = null;
    for (BrowserNodeInfo nodeInfo : deleteNodes)
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

  /**
   * Deletes a subtree.
   * @param ctx the connection to the server.
   * @param dnToRemove the DN of the subtree to delete.
   * @throws NamingException if an error occurs deleting the subtree.
   */
  private void deleteSubtree(InitialLdapContext ctx, DN dnToRemove)
  throws NamingException
  {
    lastDn = dnToRemove;
    try
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          printEquivalentCommandToDelete(lastDn);
          getProgressDialog().setSummary(
              Message.raw(
              Utilities.applyFont(
                  INFO_CTRL_PANEL_DELETING_ENTRY_SUMMARY.get(
                      lastDn.toString()).toString(),
                  ColorAndFontConstants.defaultFont)));
        }
      });
      Utilities.deleteSubtree(ctx, dnToRemove);
      nDeleted ++;
      if ((nToDelete > 0) && (nToDelete > nDeleted))
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(
                (100 * nDeleted) / nToDelete);
          }
        });
      }
    } catch (NameNotFoundException nnfe) {
      // The entry is not there: it has been removed
    }
  }

/*
  private void deleteSubtree(DirContext ctx, DN dnToRemove)
  throws NamingException, DirectoryException
  {
    lastDn = dnToRemove;

    try {
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
          deleteSubtree(ctx,entryDNFound);
        }
      }

    } catch (NameNotFoundException nnfe) {
      // The entry is not there: it has been removed
    }

    try
    {
      if (((nDeleted % 10) == 0) || (nDeleted == 0))
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().setSummary(
                Utilities.applyFont("Deleting entry '"+lastDn+"'...",
                    ColorAndFontConstants.defaultFont));
            if (nDeleted == 0)
            {
              // Just give an example
              printEquivalentCommandToDelete(lastDn);
            }
          }
        });
      }
      ctx.destroySubcontext(Utilities.getJNDIName(dnToRemove.toString()));
      nDeleted ++;
      if (((nDeleted % 10) == 0) && (nToDelete > 0) && (nToDelete > nDeleted))
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue(
                (100 * nDeleted) / nToDelete);
          }
        });
      }
    } catch (NameNotFoundException nnfe) {
      // The entry is not there: it has been removed
    }
  }

  private void printEquivalentCommandToDelete(DN dn)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLineName("ldapdelete"));
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments()));
    args.add(dn.toString());
    StringBuilder sb = new StringBuilder();
    for (String arg : args)
    {
      sb.append(" "+CommandBuilder.escapeValue(arg));
    }

    getProgressDialog().appendProgressHtml(Utilities.applyFont(
        "Equivalent command line to delete entry '"+dn+"':<br><b>"+
        sb.toString()+"</b><br><br>",
        ColorAndFontConstants.progressFont));
  }
*/
  /**
   * Prints in the progress dialog the equivalent command-line to delete a
   * subtree.
   * @param dn the DN of the subtree to be deleted.
   */
  private void printEquivalentCommandToDelete(DN dn)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add(getCommandLinePath("ldapdelete"));
    args.addAll(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments()));
    args.add("-J");
    args.add(Utilities.SUBTREE_CTRL_OID);
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
