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
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * Dialog used to inform the user that there are unsaved changes in a panel.
 * It proposes the user to save the changes, do not save them or cancel the
 * action that make the dialog appear (for instance when the user is editing
 * an entry and clicks on another node, this dialog appears).
 *
 */
public class UnsavedChangesDialog extends GenericDialog
{
  /**
   * The different input that the user can provide.
   *
   */
  public enum Result
  {
    /**
     * The user asks to save the changes.
     */
    SAVE,
    /**
     * The user asks to not to save the changes.
     */
    DO_NOT_SAVE,
    /**
     * The user asks to cancel the operation that made this dialog to appear.
     */
    CANCEL
  }
  private static final long serialVersionUID = -4436794801035162388L;

  /**
   * Constructor of the dialog.
   * @param parentDialog the parent dialog.
   * @param info the control panel info.
   */
  public UnsavedChangesDialog(Component parentDialog,
      ControlPanelInfo info)
  {
    super(Utilities.getFrame(parentDialog), getPanel(info));
    Utilities.centerGoldenMean(this, parentDialog);
    getRootPane().setDefaultButton(
        ((UnsavedChangesPanel)panel).saveButton);
    setModal(true);
  }

  /**
   * Sets the message to be displayed in this dialog.
   * @param title the title of the message.
   * @param details the details of the message.
   */
  public void setMessage(Message title, Message details)
  {
    panel.updateConfirmationPane(panel.errorPane, title,
        ColorAndFontConstants.errorTitleFont, details,
        ColorAndFontConstants.defaultFont);
    invalidate();
    pack();
  }

  /**
   * {@inheritDoc}
   */
  public void setVisible(boolean visible)
  {
    if (visible)
    {
      ((UnsavedChangesPanel)panel).result = Result.CANCEL;
    }
    super.setVisible(visible);
  }

  /**
   * Returns the option the user gave when closing this dialog.
   * @return the option the user gave when closing this dialog.
   */
  public Result getResult()
  {
    return ((UnsavedChangesPanel)panel).result;
  }

  /**
   * Creates the panel to be displayed inside the dialog.
   * @param info the control panel info.
   * @return the panel to be displayed inside the dialog.
   */
  private static StatusGenericPanel getPanel(ControlPanelInfo info)
  {
    UnsavedChangesPanel panel = new UnsavedChangesPanel();
    panel.setInfo(info);
    return panel;
  }

  /**
   * The panel to be displayed inside the dialog.
   *
   */
  private static class UnsavedChangesPanel extends StatusGenericPanel
  {
    private static final long serialVersionUID = -1528939816762604059L;

    private JButton saveButton;
    private JButton doNotSaveButton;
    private JButton cancelButton;

    private Result result;

    /**
     * Default constructor.
     *
     */
    public UnsavedChangesPanel()
    {
      super();
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.gridwidth = 1;
      addErrorPane(gbc);
      errorPane.setVisible(true);
      gbc.gridy ++;
      gbc.fill = GridBagConstraints.VERTICAL;
      gbc.weighty = 1.0;
      add(Box.createVerticalGlue(), gbc);
      gbc.fill = GridBagConstraints.HORIZONTAL;
//    The button panel
      gbc.gridy ++;
      gbc.weighty = 0.0;
      gbc.insets = new Insets(0, 0, 0, 0);
      add(createButtonsPanel(), gbc);
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresBorder()
    {
      return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresScroll()
    {
      return false;
    }

    private JPanel createButtonsPanel()
    {
      JPanel buttonsPanel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.gridwidth = 1;
      gbc.gridy = 0;
      doNotSaveButton =
        Utilities.createButton(INFO_CTRL_PANEL_DO_NOT_SAVE_BUTTON_LABEL.get());
      doNotSaveButton.setOpaque(false);
      gbc.insets = new Insets(10, 10, 10, 10);
      buttonsPanel.add(doNotSaveButton, gbc);
      doNotSaveButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          result = Result.DO_NOT_SAVE;
          cancelClicked();
        }
      });
      gbc.weightx = 1.0;
      gbc.gridx ++;
      buttonsPanel.add(Box.createHorizontalStrut(150));
      buttonsPanel.add(Box.createHorizontalGlue(), gbc);
      buttonsPanel.setOpaque(true);
      buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
      gbc.gridx ++;
      gbc.weightx = 0.0;
      buttonsPanel.add(Box.createHorizontalStrut(100));
      gbc.gridx ++;
      cancelButton = Utilities.createButton(
          INFO_CTRL_PANEL_CANCEL_BUTTON_LABEL.get());
      cancelButton.setOpaque(false);
      gbc.insets.right = 0;
      buttonsPanel.add(cancelButton, gbc);
      cancelButton.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          result = Result.CANCEL;
          cancelClicked();
        }
      });
      saveButton = Utilities.createButton(
          INFO_CTRL_PANEL_SAVE_BUTTON_LABEL.get());
      saveButton.setOpaque(false);
      gbc.gridx ++;
      gbc.insets.left = 5;
      gbc.insets.right = 10;
      buttonsPanel.add(saveButton, gbc);
      saveButton.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          result = Result.SAVE;
          cancelClicked();
        }
      });

      buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
          ColorAndFontConstants.defaultBorderColor));

      return buttonsPanel;
    }

    /**
     * {@inheritDoc}
     */
    public Component getPreferredFocusComponent()
    {
      return doNotSaveButton;
    }

    /**
     * {@inheritDoc}
     */
    public void okClicked()
    {
    }

    /**
     * {@inheritDoc}
     */
    public Message getTitle()
    {
      return INFO_CTRL_PANEL_UNSAVED_CHANGES_DIALOG_TITLE.get();
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
    public GenericDialog.ButtonType getButtonType()
    {
      return GenericDialog.ButtonType.NO_BUTTON;
    }
  }
}

