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

package org.opends.guitools.controlpanel.ui.components;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.ObjectClassValue;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * A simple panel used in the LDAP entry viewers to display the object class
 * values of an entry.  It displays the structural and auxiliary object classes.
 * It does not allow to edit directly the object class value.  It is used for
 * instance in the entry editors (simplified and table views).
 *
 */
public class ObjectClassCellPanel extends JPanel
{
  private static final long serialVersionUID = -2362754512894888888L;
  private JLabel label;
  private CellEditorButton editButton;
  private ObjectClassValue value;
  private JLabel lockLabel = Utilities.createDefaultLabel();

  private ImageIcon lockIcon =
    Utilities.createImageIcon(IconPool.IMAGE_PATH+"/field-locked.png");

  /**
   * Default constructor.
   *
   */
  public ObjectClassCellPanel()
  {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx = 0;
    gbc.gridy = 0;
    label = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get());
    gbc.weightx = 1.0;
    add(label, gbc);
    gbc.gridx ++;
    editButton = new CellEditorButton(INFO_CTRL_PANEL_EDIT_BUTTON_LABEL.get());
    editButton.setForeground(ColorAndFontConstants.buttonForeground);
    editButton.setOpaque(false);
    gbc.insets.left = 5;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weightx = 0.0;
    add(editButton, gbc);
    gbc.weightx = 0.0;
    gbc.insets.left = 5;
    gbc.gridx ++;
    add(lockLabel, gbc);
    lockLabel.setVisible(false);
  }

  /**
   * Sets the object class value to be displayed in this panel.
   * @param value the object class value to be displayed in this panel.
   */
  public void setValue(ObjectClassValue value)
  {
    label.setText(getMessage(value).toString());
    this.value = value;
  }

  /**
   * Returns the object class value displayed in this panel.
   * @return the object class value displayed in this panel.
   */
  public ObjectClassValue getValue()
  {
    return value;
  }

  /**
   * Updates the visibility of the lock icon.
   * @param visible whether the lock icon is visible or not.
   */
  public void setLockIconVisible(boolean visible)
  {
    if (visible)
    {
      lockLabel.setIcon(lockIcon);
      lockLabel.setVisible(true);
    }
    else
    {
      lockLabel.setIcon(null);
      lockLabel.setVisible(false);
    }
  }

  /**
   * Explicitly request the focus for the edit button of this panel.
   *
   */
  public void requestFocusForButton()
  {
    editButton.requestFocusInWindow();
  }


  /**
   * Adds an action listener to this panel.  The action listener will be
   * invoked when the user clicks on the 'Edit' button.
   * @param listener the action listener.
   */
  public void addEditActionListener(ActionListener listener)
  {
    editButton.addActionListener(listener);
  }

  /**
   * Removes an action listener previously added with the method
   * addEditActionListener.
   * @param listener the action listener.
   */
  public void removeEditActionListener(ActionListener listener)
  {
    editButton.removeActionListener(listener);
  }

  /**
   * Updates the visibility of the edit button.
   * @param visible whether the edit button must be visible or not.
   */
  public void setEditButtonVisible(boolean visible)
  {
    editButton.setVisible(visible);
  }

  /**
   * {@inheritDoc}
   */
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
      int condition, boolean pressed)
  {
    return editButton.processKeyBinding(ks, e, condition, pressed);
  }

  /**
   * Returns the message describing the provided object class value.
   * @param value the object class value.
   * @return the message describing the provided object class value.
   */
  public Message getMessage(ObjectClassValue value)
  {
    MessageBuilder sb = new MessageBuilder();
    if (value != null)
    {
      Set<String> aux = new TreeSet<String>();
      aux.addAll(value.getAuxiliary());
      aux.remove("top");
      if (value.getStructural() != null)
      {
        sb.append(value.getStructural());
      }
      if (aux.size() > 0)
      {
        if (sb.length() > 0)
        {
          sb.append("<br>");
        }
        sb.append(INFO_CTRL_PANEL_OBJECTCLASS_CELL_PANEL_AUXILIARY.get(
            Utilities.getStringFromCollection(aux, ", ")));
      }
    }
    if (sb.length() > 0)
    {
      return Message.raw("<html>"+Utilities.applyFont(sb.toString(),
          ColorAndFontConstants.defaultFont));
    }
    else
    {
      return INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get();
    }
  }
}