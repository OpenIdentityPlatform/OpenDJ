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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.event;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.text.JTextComponent;

/** A class used to be able to select the contents of the text field when it gets the focus. */
public class TextComponentFocusListener implements FocusListener
{
  private JTextComponent tf;

  /**
   * The constructor for this listener.
   * @param tf the text field associated with this listener.
   */
  public TextComponentFocusListener(JTextComponent tf)
  {
    this.tf = tf;
  }

  @Override
  public void focusGained(FocusEvent e)
  {
    if (tf.getText() == null || "".equals(tf.getText()))
    {
      tf.setText(" ");
      tf.selectAll();
      tf.setText("");
    }
    else
    {
      tf.selectAll();
    }
  }

  @Override
  public void focusLost(FocusEvent e)
  {
  }
}
