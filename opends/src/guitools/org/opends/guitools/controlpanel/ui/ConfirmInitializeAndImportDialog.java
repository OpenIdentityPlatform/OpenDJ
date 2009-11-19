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
public class ConfirmInitializeAndImportDialog extends GenericDialog
{
  /**
   * The different input that the user can provide.
   *
   */
  public enum Result
  {
    /**
     * The user asks to do the import and then the initialization.
     */
    INITIALIZE_ALL,
    /**
     * The user asks to only do the import locally.
     */
    IMPORT_ONLY,
    /**
     * The user asks to cancel the operation that made this dialog to appear.
     */
    CANCEL
  }
  private static final long serialVersionUID = -442311801035162311L;

  /**
   * Constructor of the dialog.
   * @param parentDialog the parent dialog.
   * @param info the control panel info.
   */
  public ConfirmInitializeAndImportDialog(Component parentDialog,
      ControlPanelInfo info)
  {
    super(Utilities.getFrame(parentDialog), getPanel(info));
    Utilities.centerGoldenMean(this, parentDialog);
    getRootPane().setDefaultButton(
        ((ConfirmInitializeAndImportPanel)panel).initializeAllButton);
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
      ((ConfirmInitializeAndImportPanel)panel).result = Result.CANCEL;
    }
    super.setVisible(visible);
  }

  /**
   * Returns the option the user gave when closing this dialog.
   * @return the option the user gave when closing this dialog.
   */
  public Result getResult()
  {
    return ((ConfirmInitializeAndImportPanel)panel).result;
  }

  /**
   * Creates the panel to be displayed inside the dialog.
   * @param info the control panel info.
   * @return the panel to be displayed inside the dialog.
   */
  private static StatusGenericPanel getPanel(ControlPanelInfo info)
  {
    ConfirmInitializeAndImportPanel panel =
      new ConfirmInitializeAndImportPanel();
    panel.setInfo(info);
    return panel;
  }

  /**
   * The panel to be displayed inside the dialog.
   *
   */
  private static class ConfirmInitializeAndImportPanel
  extends StatusGenericPanel
  {
    private static final long serialVersionUID = -9890116762604059L;

    private JButton initializeAllButton;
    private JButton importOnlyButton;
    private JButton cancelButton;

    private Result result;

    /**
     * Default constructor.
     *
     */
    public ConfirmInitializeAndImportPanel()
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
      buttonsPanel.setOpaque(true);
      buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.gridwidth = 1;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.gridx ++;
      buttonsPanel.add(Box.createHorizontalStrut(150));
      buttonsPanel.add(Box.createHorizontalGlue(), gbc);

      initializeAllButton = Utilities.createButton(
          INFO_CTRL_PANEL_INITIALIZE_ALL_BUTTON_LABEL.get());
      initializeAllButton.setOpaque(false);
      gbc.insets = new Insets(10, 10, 10, 10);
      gbc.weightx = 0.0;
      gbc.gridx ++;
      buttonsPanel.add(initializeAllButton, gbc);
      initializeAllButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          result = Result.INITIALIZE_ALL;
          cancelClicked();
        }
      });

      gbc.gridx ++;
      importOnlyButton = Utilities.createButton(
          INFO_CTRL_PANEL_IMPORT_ONLY_BUTTON_LABEL.get());
      importOnlyButton.setOpaque(false);
      gbc.gridx ++;
      gbc.insets.left = 0;
      gbc.insets.right = 10;
      buttonsPanel.add(importOnlyButton, gbc);
      importOnlyButton.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          result = Result.IMPORT_ONLY;
          cancelClicked();
        }
      });

      cancelButton = Utilities.createButton(
          INFO_CTRL_PANEL_CANCEL_BUTTON_LABEL.get());
      cancelButton.setOpaque(false);
      gbc.insets.right = 10;
      gbc.gridx ++;
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

      buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
          ColorAndFontConstants.defaultBorderColor));

      return buttonsPanel;
    }

    /**
     * {@inheritDoc}
     */
    public Component getPreferredFocusComponent()
    {
      return initializeAllButton;
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
      return INFO_CTRL_PANEL_CONFIRM_INITIALIZE_TITLE.get();
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

