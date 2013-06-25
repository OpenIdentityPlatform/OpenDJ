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
    baseDNs = new HashSet<String>();
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
    backendSet = new HashSet<String>();
    this.baseDNs = new TreeSet<String>();
    this.baseDNs.addAll(baseDNs);
    initializeBackendSet();
  }

  /**
   * Initialize the list of backends that are affected by this task.
   *
   */
  private void initializeBackendSet()
  {
    backendSet = new TreeSet<String>();
    DN theDN = null;
    for (String baseDN : baseDNs)
    {
      try
      {
        theDN = DN.decode(baseDN);
      }
      catch (Throwable t)
      {
        throw new IllegalArgumentException("Could not decode dn "+
            baseDN, t);
      }
      for (BackendDescriptor backend :
        getInfo().getServerDescriptor().getBackends())
      {
        for (BaseDNDescriptor b : backend.getBaseDns())
        {
          if (b.getDn().equals(theDN))
          {
            backendSet.add(backend.getBackendID());
            break;
          }
        }
        if (backendSet.size() > 0)
        {
          break;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public Set<String> getBackends()
  {
    return backendSet;
  }
}
