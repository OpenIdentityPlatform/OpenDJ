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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui.components;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

/**
 * Document filter used to update properly a text component displaying a
 * numeric field with a limited size.
 */
public class NumericLimitedSizeDocumentFilter extends DocumentFilter
{
  private JTextComponent tf;
  private int maxSize;

  /**
   * Constructor.
   * @param tf the text component associated with the document.
   * @param maxSize the maximum size.
   */
  public NumericLimitedSizeDocumentFilter(JTextComponent tf, int maxSize)
  {
    this.tf = tf;
    this.maxSize = maxSize;
  }

  /**
   * {@inheritDoc}
   */
  public void insertString(DocumentFilter.FilterBypass fb, int offset,
      String text, AttributeSet attr)
  throws BadLocationException
  {
    int previousLength = fb.getDocument().getLength();
    String newText = text.replaceAll("[^0-9]", "");
    if (newText.length() > maxSize)
    {
      newText = newText.substring(0, maxSize);
    }
    if (newText.length() + previousLength > maxSize)
    {
      if (offset + newText.length() > maxSize)
      {
        int newOffset = offset + newText.length() - maxSize;
        fb.remove(0, newOffset);
        fb.insertString(newOffset, newText, attr);
      }
      else
      {
        fb.insertString(offset, newText, attr);
        fb.remove(maxSize, newText.length() + previousLength - maxSize);
      }
    }
    else
    {
      fb.insertString(offset, newText, attr);
    }
    updateCaretPosition(fb);
  }

  /**
   * {@inheritDoc}
   */
  public void replace(DocumentFilter.FilterBypass fb, int offset,
      int length, String text, AttributeSet attr)
  throws BadLocationException
  {
    if (length > 0)
    {
      fb.remove(offset, length);
    }

    insertString(fb, offset, text, attr);
  }

  private void updateCaretPosition(DocumentFilter.FilterBypass fb)
  throws BadLocationException
  {
    int totalLength = fb.getDocument().getLength();

    int caretPosition = tf.getCaretPosition();

    if ((totalLength >= maxSize) &&
        (caretPosition == fb.getDocument().getLength()))
    {
      tf.setCaretPosition(0);
    }
  }
}
