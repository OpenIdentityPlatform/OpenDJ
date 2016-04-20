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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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

  @Override
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

  @Override
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

    if (totalLength >= maxSize &&
        caretPosition == fb.getDocument().getLength())
    {
      tf.setCaretPosition(0);
    }
  }
}
