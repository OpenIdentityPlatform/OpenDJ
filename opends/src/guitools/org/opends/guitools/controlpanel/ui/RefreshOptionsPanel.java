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
import org.opends.messages.Message;

/**
 * The panel that displays the refresh options of the control panel.  Basically
 * it allows to set the refreshing period used by the control panel.
 *
 */
public class RefreshOptionsPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 641533296295459469L;
  private JTextField period;
  private JLabel lPeriod;

  private boolean isCancelled = true;

  private int MAX_VALUE = 5000;

  /**
   * Default constructor.
   *
   */
  public RefreshOptionsPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_REFRESH_PANEL_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK_CANCEL;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return period;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    isCancelled = true;

    setPrimaryValid(lPeriod);
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    long t = -1;
    try
    {
      t = Long.parseLong(period.getText());
    }
    catch (Throwable th)
    {
    }
    if ((t <= 0) || (t > MAX_VALUE))
    {
      errors.add(INFO_CTRL_PANEL_INVALID_PERIOD_VALUE.get(MAX_VALUE));
    }

    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
    else
    {
      isCancelled = false;
      Utilities.getParentDialog(this).setVisible(false);
    }
  }

  /**
   * Returns whether this dialog has been cancelled or not.
   * @return whether this dialog has been cancelled or not.
   */
  public boolean isCancelled()
  {
    return isCancelled;
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    if (visible)
    {
      isCancelled = true;
      long timeInSeconds = getInfo().getPoolingPeriod() / 1000;
      period.setText(String.valueOf(timeInSeconds));
    }
  }

  /**
   * Returns the time specified by the user in miliseconds.
   * @return the time specified by the user in miliseconds.
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
