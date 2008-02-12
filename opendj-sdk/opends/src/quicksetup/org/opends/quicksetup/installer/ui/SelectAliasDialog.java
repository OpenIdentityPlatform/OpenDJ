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

package org.opends.quicksetup.installer.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is a dialog that appears when the user must choose the alias to
 * be used from a certificate keystore.
 */
public class SelectAliasDialog extends JDialog
{
  private JButton okButton;
  private JComboBox comboAliases;
  private boolean isCancelled;

  private static final long serialVersionUID = -8140704273612764046L;

  /**
   * Constructor of the SelectAliasDialog.
   * @param parent the parent frame for this dialog.
   */
  public SelectAliasDialog(JDialog parent)
  {
    super(parent);
    setTitle(INFO_SELECT_ALIAS_TITLE.get().toString());
    getContentPane().add(createPanel());
    pack();
    int minWidth = (int) getPreferredSize().getWidth();
    int minHeight = (int) getPreferredSize().getHeight();
    addComponentListener(new MinimumSizeComponentListener(this, minWidth,
        minHeight));
    getRootPane().setDefaultButton(okButton);

    addWindowListener(new WindowAdapter()
    {
      public void windowClosing(WindowEvent e)
      {
        cancelClicked();
      }
    });
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

    Utilities.centerOnComponent(this, parent);
    setModal(true);
  }

  /**
   * Displays this dialog with the provided aliases.
   *
   * @param aliases the aliases to display.
   */
  public void display(String[] aliases)
  {
    if ((aliases == null) || (aliases.length ==0))
    {
      throw new IllegalArgumentException(
          "The provided aliases are null or empty.");
    }
    isCancelled = true;
    TreeSet<String> s = new TreeSet<String>();
    for (int i=0; i<aliases.length; i++)
    {
      s.add(aliases[i]);
    }
    String[] orderedAliases = new String[s.size()];
    s.toArray(orderedAliases);
    comboAliases.setModel(new DefaultComboBoxModel(orderedAliases));
    comboAliases.setSelectedIndex(0);
    setVisible(true);
  }

  /**
   * Returns <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the user clicked on cancel and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCancelled()
  {
    return isCancelled;
  }

  /**
   * Returns the selected certificate alias.
   * @return the selected certificate alias.
   */
  public String getSelectedAlias()
  {
    return (String) comboAliases.getSelectedItem();
  }

  /**
   * Creates and returns the panel of the dialog.
   * @return the panel of the dialog.
   */
  private JPanel createPanel()
  {
    JPanel p1 = new JPanel(new GridBagLayout());
    p1.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    p1.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.NORTHWEST;

    Insets currentStepInsets = UIFactory.getCurrentStepPanelInsets();
    gbc.insets.top = currentStepInsets.top;
    gbc.insets.left = currentStepInsets.left;

    p1.add(UIFactory.makeJLabel(UIFactory.IconType.INFORMATION_LARGE, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.fill = GridBagConstraints.BOTH;
    Message msg = INFO_SELECT_ALIAS_MSG.get();
    JTextComponent tf = UIFactory.makeHtmlPane(msg,
            UIFactory.INSTRUCTIONS_FONT);
    tf.setOpaque(false);
    tf.setEditable(false);
    p1.add(tf, gbc);
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = currentStepInsets.left;
    gbc.insets.right = currentStepInsets.right;
    gbc.insets.bottom = currentStepInsets.bottom;
    comboAliases = new JComboBox();
    comboAliases.setPrototypeDisplayValue("The prototype alias name");
    gbc.fill = GridBagConstraints.NONE;
    p1.add(comboAliases, gbc);

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    p1.add(Box.createVerticalGlue(), gbc);

    JPanel p2 = new JPanel(new GridBagLayout());
    p2.setOpaque(false);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.gridwidth = 3;
    p2.add(Box.createHorizontalGlue(), gbc);
    okButton = UIFactory.makeJButton(INFO_OK_BUTTON_LABEL.get(),
          INFO_SELECT_ALIAS_OK_BUTTON_TOOLTIP.get());
    okButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        okClicked();
      }
    });
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    p2.add(okButton, gbc);
    JButton cancelButton = UIFactory.makeJButton(INFO_CANCEL_BUTTON_LABEL.get(),
            INFO_SELECT_ALIAS_CANCEL_BUTTON_TOOLTIP.get());
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.HORIZONTAL_INSET_BETWEEN_BUTTONS;
    p2.add(cancelButton, gbc);
    cancelButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        cancelClicked();
      }
    });

    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    p.add(p1, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    p.add(p2, gbc);
    return p;
  }

  /**
   * Method called when user clicks on cancel.
   *
   */
  private void cancelClicked()
  {
    isCancelled = true;
    dispose();
  }

  /**
   * Method called when user clicks on OK.
   *
   */
  private void okClicked()
  {
    isCancelled = false;
    dispose();
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      // UIFactory.initialize();
      SelectAliasDialog dlg =
          new SelectAliasDialog(new JDialog());
      dlg.display(new String[] {"test1", "test2"});
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
