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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import java.util.HashSet;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexTableModel;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.IndexTableModel;
import org.opends.messages.AdminToolMessages;
import org.forgerock.i18n.LocalizableMessage;

/** Panel displaying a table containing the indexes of a backend. */
public class BackendIndexesPanel extends AbstractBackendIndexesPanel
{
  private static final long serialVersionUID = 7214847636854721907L;

  /** Default constructor. */
  public BackendIndexesPanel()
  {
    super();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return AdminToolMessages.INFO_CTRL_PANEL_BACKEND_INDEXES_TITLE.get();
  }

  @Override
  protected AbstractIndexTableModel getIndexTableModel()
  {
    return new IndexTableModel();
  }

  @Override
  protected void updateTableModel(BackendDescriptor backend)
  {
    tableModel.setData(
        new HashSet<AbstractIndexDescriptor>(backend.getIndexes()),
        getInfo());
  }
}
