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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.swing.SwingUtilities;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.util.ServerConstants;

/**
 * The class that is in charge of adding a set of entries to a set of static
 * groups.
 */
public class AddToGroupTask extends Task
{
  private Set<String> backendSet;
  private LinkedHashSet<DN> dns = new LinkedHashSet<DN>();
  private LinkedHashSet<DN> groupDns = new LinkedHashSet<DN>();

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param dns the DNs of the entries we want to add to the groups.
   * @param groupDns the groups that we want to modify.
   */
  public AddToGroupTask(ControlPanelInfo info, ProgressDialog dlg,
      Set<DN> dns, Set<DN> groupDns)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    this.dns.addAll(dns);
    this.groupDns.addAll(groupDns);
    for (DN groupDn : groupDns)
    {
      for (BackendDescriptor backend :
        info.getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (groupDn.isDescendantOf(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
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
    return
    AdminToolMessages.INFO_CTRL_PANEL_ADD_TO_GROUP_TASK_DESCRIPTION.get();
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

    try
    {
      for (final DN groupDn : groupDns)
      {
        final Collection<ModificationItem> modifications =
          getModifications(groupDn, dns);
        if (modifications.size() > 0)
        {
          ModificationItem[] mods =
          new ModificationItem[modifications.size()];
          modifications.toArray(mods);

          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              printEquivalentCommandToModify(groupDn, modifications);
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_ADDING_TO_GROUP.get(groupDn.toString()),
                      ColorAndFontConstants.progressFont));
            }
          });

          getInfo().getDirContext().modifyAttributes(
              Utilities.getJNDIName(groupDn.toString()), mods);

          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressDone(
                      ColorAndFontConstants.progressFont));
            }
          });
        }
      }
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /**
   * Returns the modifications that must be made to the provided group.
   * @param groupDn the DN of the static group that must be updated.
   * @param dns the list of entry DNs that must be added to the group.
   * @return the list of modifications (in form of ModificationItem) that
   *  must be made to the provided group.
   * @throws NamingException if an error occurs.
   */
  private Collection<ModificationItem> getModifications(DN groupDn,
  Set<DN> dns) throws NamingException
  {
    ArrayList<ModificationItem> modifications =
      new ArrayList<ModificationItem>();
    // Search for the group entry

    SearchControls ctls = new SearchControls();
    ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
    ctls.setReturningAttributes(
        new String[] {
            ServerConstants.ATTR_UNIQUE_MEMBER
        });
    String filter = BrowserController.ALL_OBJECTS_FILTER;
    NamingEnumeration<SearchResult> result =
      getInfo().getDirContext().search(
          Utilities.getJNDIName(groupDn.toString()),
          filter, ctls);

    while (result.hasMore())
    {
      SearchResult sr = result.next();
      Set<String> values =
        ConnectionUtils.getValues(sr, ServerConstants.ATTR_UNIQUE_MEMBER);
      Set<String> dnsToAdd = new LinkedHashSet<String>();
      if (values != null)
      {
        for (DN newDn : dns)
        {
          boolean found = false;
          for (String dn : values)
          {
            if (Utilities.areDnsEqual(dn, newDn.toString()))
            {
              found = true;
              break;
            }
          }
          if (!found)
          {
            dnsToAdd.add(newDn.toString());
          }
        }
      }
      else
      {
        for (DN newDn : dns)
        {
          dnsToAdd.add(newDn.toString());
        }
      }
      if (dnsToAdd.size() > 0)
      {
        Attribute attribute =
          new BasicAttribute(ServerConstants.ATTR_UNIQUE_MEMBER);
        for (String dn : dnsToAdd)
        {
          attribute.add(dn);
        }
        modifications.add(new ModificationItem(
            DirContext.ADD_ATTRIBUTE,
            attribute));
      }
    }
    return modifications;
  }
}

