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

package org.opends.guitools.controlpanel.event;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.text.JTextComponent;

/**
 * A class used to be able to select the contents of the text field when
 * it gets the focus.
 *
 */
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

  /**
   * {@inheritDoc}
   */
  public void focusGained(FocusEvent e)
  {
    if ((tf.getText() == null) || "".equals(tf.getText()))
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

  /**
   * {@inheritDoc}
   */
  public void focusLost(FocusEvent e)
  {
  }
}