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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;

/** The panel used to display a binary value. */
public class BinaryValuePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2536360199438858665L;
  private JLabel lBase64;
  private JTextField base64;
  private JLabel attrName;
  private JLabel imagePreview;
  private JLabel lImage = Utilities.createDefaultLabel();
  private byte[] lastBytes;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Default constructor. */
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
      @Override
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

      @Override
      public void backgroundTaskCompleted(Void returnValue, Throwable t)
      {
        displayMainPanel();
        packParentDialog();
        if (t != null)
        {
          logger.warn(LocalizableMessage.raw("Error reading binary contents: "+t, t));
        }
      }
    };
    if (launchBackground)
    {
      displayMessage(INFO_CTRL_PANEL_READING_SUMMARY.get());
      worker.startBackgroundTask();
    }
    else
    {
      attrName.setText(attr);
    }
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return base64;
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  @Override
  public void okClicked()
  {
    // No OK Button
  }

  @Override
  public boolean requiresScroll()
  {
    return true;
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_VIEW_BINARY_ATTRIBUTE_TITLE.get();
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
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
