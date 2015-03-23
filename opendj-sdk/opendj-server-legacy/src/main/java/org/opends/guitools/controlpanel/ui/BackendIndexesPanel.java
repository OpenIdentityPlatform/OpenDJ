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

package org.opends.guitools.controlpanel.ui;

import java.util.HashSet;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexTableModel;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexTableModel;
import org.opends.messages.AdminToolMessages;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Panel displaying a table containing the indexes of a backend.
 *
 */
public class BackendIndexesPanel extends AbstractBackendIndexesPanel
{
  private static final long serialVersionUID = 7214847636854721907L;

  /** Default constructor. */
  public BackendIndexesPanel()
  {
    super();
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTitle()
  {
    return AdminToolMessages.INFO_CTRL_PANEL_BACKEND_INDEXES_TITLE.get();
  }

  /** {@inheritDoc} */
  protected AbstractIndexTableModel getIndexTableModel()
  {
    return new IndexTableModel();
  }

  /** {@inheritDoc} */
  protected void updateTableModel(BackendDescriptor backend)
  {
    tableModel.setData(
        new HashSet<AbstractIndexDescriptor>(backend.getIndexes()),
        getInfo());
  }
}
