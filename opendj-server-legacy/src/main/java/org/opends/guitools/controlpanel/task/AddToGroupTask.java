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

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.guitools.controlpanel.browser.BrowserController.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;

/** The class that is in charge of adding a set of entries to a set of static groups. */
public class AddToGroupTask extends Task
{
  private Set<String> backendSet;
  private LinkedHashSet<DN> dns = new LinkedHashSet<>();
  private LinkedHashSet<DN> groupDns = new LinkedHashSet<>();

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
    backendSet = new HashSet<>();
    this.dns.addAll(dns);
    this.groupDns.addAll(groupDns);
    for (DN groupDn : groupDns)
    {
      for (BackendDescriptor backend :
        info.getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          if (groupDn.isSubordinateOrEqualTo(baseDN.getDn()))
          {
            backendSet.add(backend.getBackendID());
          }
        }
      }
    }
  }

  @Override
  public Type getType()
  {
    return Type.MODIFY_ENTRY;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_ADD_TO_GROUP_TASK_DESCRIPTION.get();
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
      for (final DN groupDn : groupDns)
      {
        ModifyRequest request = newModifyRequest(groupDn);
        addModifications(groupDn, dns, request);
        final List<Modification> modifications = request.getModifications();
        if (!modifications.isEmpty())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              printEquivalentCommandToModify(groupDn, modifications, false);
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(
                      INFO_CTRL_PANEL_ADDING_TO_GROUP.get(groupDn),
                      ColorAndFontConstants.progressFont));
            }
          });

          getInfo().getConnection().getConnection().modify(request);

          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
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
   * @param modRequest
   * @throws IOException if an error occurs.
   */
  private void addModifications(DN groupDn, Set<DN> dns, ModifyRequest modRequest) throws IOException
  {
    // Search for the group entry
    SearchRequest searchRequest = newSearchRequest(groupDn, BASE_OBJECT, ALL_OBJECTS_FILTER,
        OBJECTCLASS_ATTRIBUTE_TYPE_NAME,
        ATTR_MEMBER,
        ATTR_UNIQUE_MEMBER);

    try (ConnectionEntryReader reader = getInfo().getConnection().getConnection().search(searchRequest))
    {
      String memberAttr = ATTR_MEMBER;
      while (reader.hasNext())
      {
        SearchResultEntry sr = reader.readEntry();
        Set<String> objectClasses = sr.parseAttribute(OBJECTCLASS_ATTRIBUTE_TYPE_NAME).asSetOfString();
        if (objectClasses.contains(OC_GROUP_OF_UNIQUE_NAMES))
        {
          memberAttr = ATTR_UNIQUE_MEMBER;
        }
        Set<DN> dnsToAdd = new LinkedHashSet<>(dns);
        // remove all existing members
        dnsToAdd.removeAll(sr.parseAttribute(memberAttr).asSetOfDN());
        if (!dnsToAdd.isEmpty())
        {
          modRequest.addModification(ADD, memberAttr, dnsToAdd.toArray());
        }
      }
    }
  }
}
