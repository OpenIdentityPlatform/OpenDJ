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
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui.components;

import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;

/**
 * A component that acts as a checkbox but uses some customized buttons to
 * indicate the selected and unselected states.  This component is used in the
 * 'Import LDIF' and 'Export LDIF' panels to hide parts of the dialog.
 *
 */
public class BasicExpander extends JCheckBox
{
  private static final long serialVersionUID = 2997408236059683190L;

  /**
   * Constructor of the BasicExpander.
   * @param text the text to be displayed in the label of the BasicExpander.
   */
  public BasicExpander(LocalizableMessage text)
  {
    super(text.toString());
    setHorizontalTextPosition(SwingConstants.TRAILING);
    setHorizontalAlignment(SwingConstants.LEADING);
    setFocusPainted(true);
    setOpaque(false);
    setSelectedIcon(
        Utilities.createImageIcon(IconPool.IMAGE_PATH+"/downarrow.png"));
    setIcon(Utilities.createImageIcon(IconPool.IMAGE_PATH+"/rightarrow.png"));
    setFont(ColorAndFontConstants.expanderFont);
    setForeground(ColorAndFontConstants.expanderForeground);
  }
}
