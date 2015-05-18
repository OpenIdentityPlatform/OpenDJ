/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.task;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.server.types.DN;

/**
 * Abstract task used to factorize some code shared by different tasks involving
 * indexes.
 *
 */
public abstract class IndexTask extends Task
{
  /**
   * The set of backends that are affected by this task.
   */
  protected Set<String> backendSet;
  /**
   * The set of base DNs that are affected by this task.
   */
  protected Set<String> baseDNs;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param baseDN the base DN where the indexes are defined.
   */
  protected IndexTask(ControlPanelInfo info, ProgressDialog dlg,
      String baseDN)
  {
    super(info, dlg);
    baseDNs = new HashSet<>();
    baseDNs.add(baseDN);
    initializeBackendSet();
  }

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param baseDNs the list of base DNs where the indexes are defined.
   */
  protected IndexTask(ControlPanelInfo info, ProgressDialog dlg,
      Collection<String> baseDNs)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    this.baseDNs = new TreeSet<>();
    this.baseDNs.addAll(baseDNs);
    initializeBackendSet();
  }

  /** Initialize the list of backends that are affected by this task. */
  private void initializeBackendSet()
  {
    backendSet = new TreeSet<>();
    DN theDN = null;
    for (String baseDN : baseDNs)
    {
      try
      {
        theDN = DN.valueOf(baseDN);
      }
      catch (Throwable t)
      {
        throw new IllegalArgumentException("Could not decode dn " + baseDN, t);
      }
      BackendDescriptor backend = findBackendByID(theDN);
      if (backend != null) {
        backendSet.add(backend.getBackendID());
      }
    }
  }

  private BackendDescriptor findBackendByID(DN dn)
  {
    for (BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor b : backend.getBaseDns())
      {
        if (b.getDn().equals(dn))
        {
          return backend;
        }
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  public Set<String> getBackends()
  {
    return backendSet;
  }
}
