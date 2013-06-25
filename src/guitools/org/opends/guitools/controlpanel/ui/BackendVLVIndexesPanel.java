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

package org.opends.guitools.controlpanel.ui;

import java.util.HashSet;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexTableModel;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexTableModel;
import org.opends.messages.AdminToolMessages;
import org.opends.messages.Message;

/**
 * Panel displaying a table containing the VLV indexes of a backend.
 *
 */
public class BackendVLVIndexesPanel extends AbstractBackendIndexesPanel
{
  private static final long serialVersionUID = -5864660402543106492L;

  /**
   * Default constructor.
   *
   */
  public BackendVLVIndexesPanel()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return AdminToolMessages.INFO_CTRL_PANEL_BACKEND_VLV_INDEXES_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected AbstractIndexTableModel getIndexTableModel()
  {
    return new VLVIndexTableModel();
  }

  /**
   * {@inheritDoc}
   */
  protected void updateTableModel(BackendDescriptor backend)
  {
    tableModel.setData(
        new HashSet<AbstractIndexDescriptor>(backend.getVLVIndexes()),
        getInfo());
  }
}
