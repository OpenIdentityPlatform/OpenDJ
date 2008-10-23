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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The generic dialog of the Control Panel.  It contains a StatusGenericPanel.
 *
 */
public class GenericDialog extends JDialog
{
  private static final long serialVersionUID = -2643144936460484112L;
  private final static Color buttonPanelBackground =
    ColorAndFontConstants.greyBackground;
  private JButton okButton;
  /**
   * The close button.
   */
  protected JButton closeButton;
  private JButton cancelButton;
  //private JPanel contentPanel;
  /**
   * The panel contained in the dialog.
   */
  protected StatusGenericPanel panel;
  //private ProgressPanel progressPanel;
  //private boolean displayInputInNextVisible;
  private Component lastComponentWithFocus;

  /**
   * The different combinations of buttons that the dialog can have.
   *
   */
  public enum ButtonType
  {
    /**
     * The dialog contains OK and CANCEL buttons.
     */
    OK_CANCEL,
    /**
     * The dialog contains a OK button.
     */
    OK,
    /**
     * The dialog contains a CLOSE button.
     */
    CLOSE,
    /**
     * The dialog has no buttons.
     */
    NO_BUTTON
  };

  /**
   * Constructor of the dialog.
   * @param parentFrame the parent frame of the dialog.
   * @param panel the panel contained in this dialog.
   */
  public GenericDialog(JFrame parentFrame, StatusGenericPanel panel)
  {
    super();
    this.panel = panel;
    if (panel.requiresBorder())
    {
      setDefaultBorder(panel);
    }
    JMenuBar menu = panel.getMenuBar();
    if (menu != null)
    {
      setJMenuBar(menu);
    }
    JScrollPane scroll = Utilities.createScrollPane(panel);
    /*
    CardLayout cardLayout = new CardLayout();
    contentPanel = new JPanel(cardLayout);
    contentPanel.setOpaque(false);
    setContentPane(contentPanel);
  */
    JPanel inputPanel = new JPanel(new GridBagLayout());
    setContentPane(inputPanel);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    if (panel.requiresScroll())
    {
      inputPanel.add(scroll, gbc);
    }
    else
    {
      inputPanel.add(panel, gbc);
    }
    if (panel.getButtonType() != ButtonType.NO_BUTTON)
    {
      gbc.gridy ++;
      gbc.weighty = 0.0;
      inputPanel.add(createButtonsPanel(panel), gbc);
    }

    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    ActionListener actionListener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        setVisible(false);
      }
    };
    getRootPane().registerKeyboardAction(actionListener, stroke,
        JComponent.WHEN_IN_FOCUSED_WINDOW);

    FocusListener focusListener = new FocusAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void focusGained(FocusEvent ev)
      {
        lastComponentWithFocus = ev.getComponent();
      }
    };
    addFocusListener(focusListener, panel);

    addWindowListener(new WindowAdapter() {
      /**
       * {@inheritDoc}
       */
      public void windowClosing(WindowEvent e) {
        GenericDialog.this.panel.closeClicked();
      }
    });

    pack();
    if (!SwingUtilities.isEventDispatchThread())
    {
      Thread.dumpStack();
    }
  }

  /**
   * Method used to add a focus listeners to all the components in the panel.
   * This is done to recover the focus on an item when the dialog is closed
   * and then opened again.
   * @param focusListener the focus listener.
   * @param container the container where the components are layed out.
   */
  private void addFocusListener(FocusListener focusListener,
      Container container)
  {
    for (int i=0; i < container.getComponentCount(); i++)
    {
      Component comp = container.getComponent(i);
      if ((comp instanceof AbstractButton) ||
          (comp instanceof JTextComponent) ||
          (comp instanceof JList) ||
          (comp instanceof JComboBox) ||
          (comp instanceof JTable))
      {
        comp.addFocusListener(focusListener);
      }
      else if ((comp instanceof JPanel) || (comp instanceof JScrollPane)
          || (comp instanceof JViewport))
      {
        addFocusListener(focusListener, (Container)comp);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setVisible(boolean visible)
  {
    if (lastComponentWithFocus == null)
    {
      lastComponentWithFocus = panel.getPreferredFocusComponent();
    }
    if (visible && (lastComponentWithFocus != null))
    {
      lastComponentWithFocus.requestFocusInWindow();
    }
    updateDefaultButton(panel);
    panel.toBeDisplayed(visible);
    updateTitle();
    super.setVisible(visible);
  }

  /**
   * Sets the enable state of the OK button.
   * @param enable whether the OK button must be enabled or not.
   */
  public void setEnabledOK(boolean enable)
  {
    okButton.setEnabled(enable);
  }

  /**
   * Sets the enable state of the Cancel button.
   * @param enable whether the Cancel button must be enabled or not.
   */
  public void setEnabledCancel(boolean enable)
  {
    cancelButton.setEnabled(enable);
  }

  /**
   * Sets the enable state of the Close button.
   * @param enable whether the Close button must be enabled or not.
   */
  public void setEnabledClose(boolean enable)
  {
    closeButton.setEnabled(enable);
  }

  /**
   * Updates the title of the dialog using the title of the panel.
   *
   */
  void updateTitle()
  {
    if (panel.getTitle() != null)
    {
      setTitle(INFO_CTRL_PANEL_GENERIC_TITLE.get(
              panel.getTitle().toString()).toString());
    }
  }

  private void setDefaultBorder(JComponent comp)
  {
    Utilities.setBorder(comp, new EmptyBorder(20, 20, 20, 20));
  }


  private JPanel createButtonsPanel(final StatusGenericPanel panel)
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    ButtonType buttonType = panel.getButtonType();
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    buttonsPanel.setOpaque(true);
    buttonsPanel.setBackground(buttonPanelBackground);
    gbc.insets = new Insets(10, 0, 10, 0);
    gbc.insets.left = 5;

    if (buttonType == ButtonType.OK_CANCEL)
    {
      gbc.gridx ++;
      gbc.weightx = 0.0;
      okButton = Utilities.createButton(
          INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          panel.okClicked();
        }
      });
      okButton.setEnabled(panel.isEnableOK());

      gbc.gridx ++;
      cancelButton = Utilities.createButton(
          INFO_CTRL_PANEL_CANCEL_BUTTON_LABEL.get());
      cancelButton.setOpaque(false);
      cancelButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          panel.cancelClicked();
        }
      });
      cancelButton.setEnabled(panel.isEnableCancel());
      gbc.insets.right = 10;
      buttonsPanel.add(cancelButton, gbc);
    }

    if (buttonType == ButtonType.OK)
    {
      gbc.gridx ++;
      gbc.weightx = 0.0;
      okButton = Utilities.createButton(
          INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      gbc.insets.right = 10;
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          panel.okClicked();
        }
      });
      okButton.setEnabled(panel.isEnableOK());
    }

    if (buttonType == ButtonType.CLOSE)
    {
      gbc.gridx ++;
      gbc.weightx = 0.0;
      closeButton = Utilities.createButton(
          INFO_CTRL_PANEL_CLOSE_BUTTON_LABEL.get());
      closeButton.setOpaque(false);
      gbc.insets.right = 10;
      buttonsPanel.add(closeButton, gbc);
      closeButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          panel.closeClicked();
        }
      });
      closeButton.setEnabled(panel.isEnableClose());
    }



    buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
        ColorAndFontConstants.defaultBorderColor));
    return buttonsPanel;
  }

  /**
   * Updates the default button of the dialog, depending on the type of
   * generic panel that it contains.
   * @param panel the generic panel contained in this dialog.
   */
  private void updateDefaultButton(StatusGenericPanel panel)
  {
    ButtonType buttonType = panel.getButtonType();

    if (buttonType == ButtonType.OK_CANCEL)
    {
      getRootPane().setDefaultButton(okButton);
    }
    else if (buttonType == ButtonType.OK)
    {
      getRootPane().setDefaultButton(okButton);
    }
    else if (buttonType == ButtonType.CLOSE)
    {
      getRootPane().setDefaultButton(closeButton);
    }
  }
}

