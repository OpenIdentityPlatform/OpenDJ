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

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;

import javax.swing.JTable;

import org.opends.guitools.controlpanel.datamodel.BaseDNTableModel;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * Class used to render the base DN table cells.
 */
public class BaseDNCellRenderer extends CustomCellRenderer
{
  private static final long serialVersionUID = -256719167426289735L;

  /**
   * Default constructor.
   */
  public BaseDNCellRenderer()
  {
    super();
  }

  /**
   * {@inheritDoc}
   */
  public Component getTableCellRendererComponent(JTable table, Object value,
      boolean isSelected, boolean hasFocus, int row, int column) {
    String text = (String)value;
    if (text == BaseDNTableModel.NOT_AVAILABLE)
    {
      Utilities.setNotAvailable(this);
    }
    else if (text == BaseDNTableModel.NOT_AVAILABLE_AUTHENTICATION_REQUIRED)
    {
      Utilities.setNotAvailableBecauseAuthenticationIsRequired(this);
    }
    else if (text == BaseDNTableModel.NOT_AVAILABLE_SERVER_DOWN)
    {
      Utilities.setNotAvailableBecauseServerIsDown(this);
    }
    else
    {
      Utilities.setTextValue(this, text);
    }
    return super.getTableCellRendererComponent(table, getText(),
        isSelected, hasFocus, row, column);
  }
}
