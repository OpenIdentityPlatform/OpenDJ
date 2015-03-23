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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.KeyStroke;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This is a simple extension of the JButton class used to be able to invoke
 * the method processKeyBinding.
 */
public class CellEditorButton extends JButton
{
  private static final long serialVersionUID = -1491628553090264453L;

  /**
   * The constructor of the cell editor button.
   * @param label the label of the button.
   */
  public CellEditorButton(LocalizableMessage label)
  {
    super(label.toString());
  }

  /** {@inheritDoc} */
  public boolean processKeyBinding(KeyStroke ks, KeyEvent e,
      int condition, boolean pressed)
  {
    return super.processKeyBinding(ks, e, condition, pressed);
  }
}
