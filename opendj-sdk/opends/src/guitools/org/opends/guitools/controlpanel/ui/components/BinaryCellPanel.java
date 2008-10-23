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
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.BinaryValue;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * A simple panel used in the LDAP entry viewers to display a binary value.
 * It does not allow to edit the binary value.  It is used for instance in the
 * tables.
 *
 */
public class BinaryCellPanel extends JPanel
{
  private static final long serialVersionUID = 6607973945986559802L;
  private JButton iconButton;
  private JLabel label;
  private CellEditorButton editButton;
  private CellEditorButton deleteButton;
  private boolean displayDelete;
  private JLabel lockLabel = Utilities.createDefaultLabel();

  private ImageIcon lockIcon =
    Utilities.createImageIcon(IconPool.IMAGE_PATH+"/field-locked.png");

  private Object value;

  private final static int THUMBNAIL_HEIGHT = 50;

  private static final Logger LOG =
    Logger.getLogger(BinaryCellPanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public BinaryCellPanel()
  {
    super(new GridBagLayout());
    setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx = 0;
    gbc.gridy = 0;
    iconButton = Utilities.createButton(Message.EMPTY);
    label = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get());
    add(iconButton);
    iconButton.setVisible(false);
    gbc.weightx = 1.0;
    gbc.gridx ++;
    add(label, gbc);
    add(Box.createHorizontalGlue(), gbc);
    gbc.gridx ++;
    editButton = new CellEditorButton(INFO_CTRL_PANEL_EDIT_BUTTON_LABEL.get());
    editButton.setForeground(ColorAndFontConstants.buttonForeground);
    editButton.setOpaque(false);
    gbc.insets.left = 5;
    gbc.weightx = 0.0;
    add(editButton, gbc);

    gbc.gridx ++;
    deleteButton =
      new CellEditorButton(INFO_CTRL_PANEL_DELETE_BUTTON_LABEL.get());
    deleteButton.setForeground(ColorAndFontConstants.buttonForeground);
    deleteButton.setOpaque(false);
    deleteButton.setVisible(isDisplayDelete());
    add(deleteButton, gbc);

    gbc.insets.left = 5;
    gbc.gridx ++;
    add(lockLabel, gbc);
    lockLabel.setVisible(false);
  }

  /**
   * Returns the message describing the provided array of bytes.
   * @param value the array of bytes.
   * @param isImage whether the array of bytes represents an image or not.
   * @return the message describing the provided array of bytes.
   */
  public Message getString(byte[] value, boolean isImage)
  {
    if (value == null)
    {
      return INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get();
    }
    else if (isImage)
    {
      return Message.EMPTY;
    }
    else
    {
      return INFO_CTRL_PANEL_BINARY_VALUE.get();
    }
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
   * Sets the text of the edit button (for instance if this panel is displaying
   * a read-only value, the user might set a value of 'View...' that launches
   * a viewer).
   * @param text the text of the button.
   */
  public void setEditButtonText(Message text)
  {
    editButton.setText(text.toString());
  }

  /**
   * Returns the message describing the provided binary value.
   * @param value the binary value.
   * @param isImage whether the binary value represents an image or not.
   * @return the message describing the provided binary value.
   */
  public Message getMessage(BinaryValue value, boolean isImage)
  {
    Message returnValue;
    if (value == null)
    {
      returnValue = INFO_CTRL_PANEL_NO_VALUE_SPECIFIED.get();
    }
    else if (isImage)
    {
      returnValue = Message.EMPTY;
    }
    else if (value.getType() == BinaryValue.Type.BASE64_STRING)
    {
      returnValue = INFO_CTRL_PANEL_BINARY_VALUE.get();
    }
    else
    {
      returnValue = INFO_CTRL_PANEL_CONTENTS_OF_FILE.get(
          value.getFile().toString());
    }
    return returnValue;
  }

  /**
   * Sets the value to be displayed by this panel.
   * @param value the binary value as an array of bytes.
   * @param isImage whether the binary value represents an image or not.
   */
  public void setValue(byte[] value, boolean isImage)
  {
    label.setText(getString(value, isImage).toString());
    deleteButton.setVisible((value != null) && isDisplayDelete());
    this.value = value;
    if (!isImage)
    {
      label.setIcon(null);
      label.setVisible(true);
      iconButton.setVisible(false);
    }
    else
    {
      updateIcon(value);
    }
  }

  /**
   * Sets the value to be displayed by this panel.
   * @param value the binary value as a BinaryValue object.
   * @param isImage whether the binary value represents an image or not.
   */
  public void setValue(BinaryValue value, boolean isImage)
  {
    label.setText(getMessage(value, isImage).toString());
    deleteButton.setVisible((value != null) && isDisplayDelete());
    this.value = value;
    if (!isImage)
    {
      label.setIcon(null);
      label.setVisible(true);
      iconButton.setVisible(false);
    }
    else
    {
      try
      {
        updateIcon(value.getBytes());
      }
      catch (ParseException pe)
      {
        LOG.log(Level.WARNING, "Error decoding base 64 value: "+pe, pe);
        Utilities.setWarningLabel(label, ERR_LOADING_IMAGE.get());
      }
    }
  }

  private void updateIcon(byte[] value)
  {
    if (value == null)
    {
      label.setVisible(true);
      iconButton.setVisible(false);
    }
    else
    {
      Icon icon = getIcon(value);
      if ((icon == null) || (icon.getIconHeight() <= 0))
      {
        Utilities.setWarningLabel(label, ERR_LOADING_IMAGE.get());
        label.setVisible(true);
        iconButton.setVisible(false);
      }
      else
      {
        iconButton.setVisible(true);
        iconButton.setIcon(icon);
        label.setVisible(false);
      }
    }
  }

  /**
   * Returns the object represented by this panel.
   * @return the object represented by this panel.
   */
  public Object getValue()
  {
    return value;
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
   * invoked when the user clicks on the 'Edit' button or the icon.
   * @param listener the action listener.
   */
  public void addEditActionListener(ActionListener listener)
  {
    editButton.addActionListener(listener);
    iconButton.addActionListener(listener);
  }

  /**
   * Removes an action listener previously added with the method
   * addEditActionListener.
   * @param listener the action listener.
   */
  public void removeEditActionListener(ActionListener listener)
  {
    editButton.removeActionListener(listener);
    iconButton.removeActionListener(listener);
  }

  /**
   * Adds an action listener to this panel.  The action listener will be
   * invoked when the user clicks on the 'Delete'.
   * @param listener the action listener.
   */
  public void addDeleteActionListener(ActionListener listener)
  {
    deleteButton.addActionListener(listener);
  }

  /**
   * Removes an action listener previously added with the method
   * addDeleteActionListener.
   * @param listener the action listener.
   */
  public void removeDeleteActionListener(ActionListener listener)
  {
    deleteButton.removeActionListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  protected boolean processKeyBinding(KeyStroke ks, KeyEvent e,
      int condition, boolean pressed)
  {
    // This method is used to transfer the key events to the button.
    return editButton.processKeyBinding(ks, e, condition, pressed);
  }

  /**
   * Tells whether the 'Delete' button is displayed or not.
   * @return <CODE>true</CODE> if the 'Delete' button is visible and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isDisplayDelete()
  {
    return displayDelete;
  }

  /**
   * Sets whether the 'Delete' button must be displayed or not.
   * @param displayDelete whether the 'Delete' button must be displayed or not.
   */
  public void setDisplayDelete(boolean displayDelete)
  {
    this.displayDelete = displayDelete;
  }

  private Icon getIcon(byte[] bytes)
  {
    return Utilities.createImageIcon(bytes, THUMBNAIL_HEIGHT,
        INFO_CTRL_PANEL_THUMBNAIL_DESCRIPTION.get(),
        true);
  }
}