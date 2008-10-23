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

import javax.swing.JComboBox;

import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The renderer to be used to render CategorizedComboBoxElement objects whose
 * values are AbstraceIndexDescriptor objects.  It can be used with combo
 * boxes.
 *
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
