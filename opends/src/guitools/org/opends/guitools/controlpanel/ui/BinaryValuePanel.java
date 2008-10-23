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

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.Schema;

/**
 * The panel used to display a binary value.
 *
 */
public class BinaryValuePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2536360199438858665L;
  private JLabel lBase64;
  private JTextField base64;
  private JLabel attrName;
  private JLabel imagePreview;
  private JLabel lImage = Utilities.createDefaultLabel();
  private byte[] lastBytes;

  private static final Logger LOG =
    Logger.getLogger(BinaryValuePanel.class.getName());

  /**
   * Default constructor.
   *
   */
  public BinaryValuePanel()
  {
    super();
    createLayout();
  }

  /**
   * Sets the value to be displayed in the panel.
   * @param attr the attribute name.
   * @param bytes the binary value.
   */
  public void setValue(final String attr, final byte[] bytes)
  {
    final boolean launchBackground = lastBytes != bytes;
    lastBytes = bytes;
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
      public Void processBackgroundTask() throws Throwable
      {
        try
        {
          Thread.sleep(1000);
        }
        catch (Throwable t)
        {
        }
        attrName.setText(attr);
        Schema schema = getInfo().getServerDescriptor().getSchema();
        if (Utilities.hasImageSyntax(attr, schema))
        {
          BinaryAttributeEditorPanel.updateImage(lImage, bytes);
          lBase64.setVisible(false);
          base64.setVisible(false);
          imagePreview.setVisible(true);
        }
        else
        {
          lImage.setIcon(null);
          lImage.setText("");
          imagePreview.setVisible(false);
          lBase64.setVisible(true);
          base64.setVisible(true);
          BinaryAttributeEditorPanel.updateBase64(base64, bytes);
        }
        return null;
      }
      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Void returnValue,
          Throwable t)
      {
        displayMainPanel();
        packParentDialog();
        if (t != null)
        {
          LOG.log(Level.WARNING, "Error reading binary contents: "+t, t);
        }
      }
    };
    if (launchBackground)
    {
      /**
       * {@inheritDoc}
       */
      displayMessage(INFO_CTRL_PANEL_READING_SUMMARY.get());
      worker.startBackgroundTask();
    }
    else
    {
      attrName.setText(attr);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return base64;
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No OK Button
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_VIEW_BINARY_ATTRIBUTE_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.gridx = 0;

    JLabel l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get());
    add(l, gbc);
    gbc.gridx ++;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.NONE;
    attrName = Utilities.createDefaultLabel();
    add(attrName, gbc);
    gbc.gridx ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    add(Box.createHorizontalGlue(), gbc);

    gbc.gridwidth = 3;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.gridx = 0;
    lBase64 = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_VALUE_IN_BASE_64_LABEL.get());
    add(lBase64, gbc);

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    base64 = Utilities.createLongTextField();
    add(base64, gbc);

    imagePreview = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_IMAGE_PREVIEW_LABEL.get());
    gbc.gridy ++;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    add(imagePreview, gbc);
    gbc.gridy ++;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets.top = 5;
    add(lImage, gbc);

    addBottomGlue(gbc);
  }
}
