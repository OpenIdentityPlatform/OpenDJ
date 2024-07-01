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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.renderer;

import javax.swing.JComboBox;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The renderer to be used to render CategorizedComboBoxElement objects whose
 * values are AbstraceIndexDescriptor objects.  It can be used with combo
 * boxes.
 */
public class IndexComboBoxCellRenderer extends CustomListCellRenderer
{
  /**
   * Constructor of the index renderer.
   * @param combo the combo whose contents will be rendered.
   */
  public IndexComboBoxCellRenderer(JComboBox combo)
  {
    super(combo);
  }

  /**
   * Returns the String value for a given CategorizedComboBoxElement.
   * @param desc the combo box element.
   * @return the String value for a given CategorizedComboBoxElement.
   */
  @Override
  protected String getStringValue(CategorizedComboBoxElement desc)
  {
    String v;
    Object value = desc.getValue();
    if (value instanceof IndexDescriptor)
    {
      v = ((IndexDescriptor)value).getName();
    }
    else if (value instanceof VLVIndexDescriptor)
    {
      v = Utilities.getVLVNameInCellRenderer((VLVIndexDescriptor)value);
    }
    else
    {
      v = super.getStringValue(desc);
    }
    return v;
  }
}
