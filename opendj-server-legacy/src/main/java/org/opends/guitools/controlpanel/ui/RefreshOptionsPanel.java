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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.LinkedHashSet;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;

/**
 * The panel that displays the refresh options of the control panel.  Basically
 * it allows to set the refreshing period used by the control panel.
 */
public class RefreshOptionsPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 641533296295459469L;
  private JTextField period;
  private JLabel lPeriod;

  private boolean isCanceled = true;

  private int MAX_VALUE = 5000;

  /** Default constructor. */
  public RefreshOptionsPanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_REFRESH_PANEL_TITLE.get();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;

    String text = INFO_CTRL_PANEL_REFRESH_OPTIONS_PANEL_TEXT.get().toString();

    JEditorPane pane = Utilities.makeHtmlPane(text,
        ColorAndFontConstants.defaultFont);

    Utilities.updatePreferredSize(pane, 60, text,
        ColorAndFontConstants.defaultFont, false);
    gbc.weighty = 0.0;
    add(pane, gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    lPeriod =Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_REFRESH_OPTIONS_LABEL.get());
    gbc.insets.top = 10;
    add(lPeriod, gbc);
    period = Utilities.createShortTextField();
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    add(period, gbc);

    gbc.gridwidth = 2;
    addBottomGlue(gbc);
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK_CANCEL;
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return period;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  @Override
  public void okClicked()
  {
    isCanceled = true;

    setPrimaryValid(lPeriod);
    LinkedHashSet<LocalizableMessage> errors = new LinkedHashSet<>();
    long t = -1;
    try
    {
      t = Long.parseLong(period.getText());
    }
    catch (Throwable th)
    {
    }
    if (t <= 0 || t > MAX_VALUE)
    {
      errors.add(INFO_CTRL_PANEL_INVALID_PERIOD_VALUE.get(MAX_VALUE));
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
    else
    {
      isCanceled = false;
      Utilities.getParentDialog(this).setVisible(false);
    }
  }

  /**
   * Returns whether this dialog has been cancelled or not.
   * @return whether this dialog has been cancelled or not.
   */
  public boolean isCanceled()
  {
    return isCanceled;
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      isCanceled = true;
      long timeInSeconds = getInfo().getPoolingPeriod() / 1000;
      period.setText(String.valueOf(timeInSeconds));
    }
  }

  /**
   * Returns the time specified by the user in milliseconds.
   * @return the time specified by the user in milliseconds.
   */
  public long getPoolingPeriod()
  {
    long t = -1;
    try
    {
      t = 1000 * Long.parseLong(period.getText());
    }
    catch (Throwable th)
    {
    }
    return t;
  }
}
