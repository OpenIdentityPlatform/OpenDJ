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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.event;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JComboBox.KeySelectionManager;
import javax.swing.text.JTextComponent;

/**
 * A class used to allow the user to type a sequence of characters that will
 * select automatically an item in the combo box.
 * Note that there must be one instance of this class per combo box.  Two
 * combo boxes must not share the same ComboKeySelectionManager object and
 * two ComboKeySelectionManager must not be used by the same combo box.
 */
public class ComboKeySelectionManager implements KeySelectionManager
{
  private JComboBox combo;
  private JList list;

  // The String that we are searching.
  private String lastSearchedString;

  private long lastSearchedTime;

  // The number of milliseconds we wait between types before considering that
  // the search starts again.
  private long RESET_BETWEEN_TYPES = 700;

  /**
   * Default constructor.
   * @param combo the combo box that is attached to this selection key manager.
   */
  public ComboKeySelectionManager(JComboBox combo)
  {
    this.combo = combo;
    list = new JList();
  }

  /**
   * {@inheritDoc}
   */
  public int selectionForKey(char key, ComboBoxModel model)
  {
    int selectedIndex = -1;
    long currentTime = System.currentTimeMillis();
    if (key == KeyEvent.VK_BACK_SPACE)
    {
      if (lastSearchedString == null)
      {
        lastSearchedString = "";
      }
      else if (lastSearchedString.length() > 0)
      {
        lastSearchedString = lastSearchedString.substring(0,
            lastSearchedString.length() -1);
      }
      else
      {
        // Nothing to do.
      }
    }
    else
    {
      if (lastSearchedTime + RESET_BETWEEN_TYPES < currentTime)
      {
        // Reset the search.
        lastSearchedString = String.valueOf(key);
      }
      else
      {
        if (lastSearchedString == null)
        {
          lastSearchedString = String.valueOf(key);
        }
        else
        {
          lastSearchedString += key;
        }
      }
    }
    lastSearchedTime = currentTime;
    if (lastSearchedString.length() > 0)
    {
      for (int i = 0; i < model.getSize() && selectedIndex == -1; i++)
      {
        Object value = model.getElementAt(i);
        Component comp = combo.getRenderer().getListCellRendererComponent(list,
            value, i, true, true);
        String sValue;
        if (comp instanceof Container)
        {
          sValue = getDisplayedStringValue((Container)comp);
          if (sValue == null)
          {
            sValue = "";
          }
          else
          {
            sValue = sValue.trim();
          }
        }
        else
        {
          sValue = String.valueOf(value);
        }
        if (sValue.toLowerCase().startsWith(lastSearchedString.toLowerCase()))
        {
          selectedIndex = i;
        }
      }
    }
    return selectedIndex;
  }

  private String getDisplayedStringValue(Container c)
  {
    String sValue = null;
    if (c instanceof JLabel)
    {
      sValue = ((JLabel)c).getText();
    }
    else if (c instanceof JTextComponent)
    {
      sValue = ((JTextComponent)c).getText();
    }
    else
    {
      int nCount = c.getComponentCount();
      for (int i=0 ; i<nCount && sValue == null; i++)
      {
        Component child = c.getComponent(i);
        if (child instanceof Container)
        {
          sValue = getDisplayedStringValue((Container)child);
        }
      }
    }
    return sValue;
  }
}
