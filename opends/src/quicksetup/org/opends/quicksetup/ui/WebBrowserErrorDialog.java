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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.WebBrowserException;

/**
 * This class is a dialog that appears when we could not launch the user web
 * browser after the user clicked on a link.
 *
 * The dialog displays the URL to be displayed and provides a 'Copy URL' button
 * to copy it to the system clipboard.  This way (even if not ideal) the user
 * can view the contents of the URL we display by pasting manually the URL
 * in his/her browser.
 *
 */
public class WebBrowserErrorDialog extends JDialog
{
  private static final long serialVersionUID = 1063837373763193941L;

  private JFrame parent;

  private String url;

  /**
   * Constructor of the WebBrowserErrorDialog.
   * @param parent the parent frame for this dialog.
   * @param ex the WebBrowserException.
   */
  public WebBrowserErrorDialog(JFrame parent, WebBrowserException ex)
  {
    super(parent);
    setTitle(getMsg("error-browser-display-title"));
    this.parent = parent;
    this.url = ex.getUrl();
    getContentPane().add(createPanel());
  }

  /**
   * Packs and displays this dialog.
   *
   */
  public void packAndShow()
  {
    pack();
    int minWidth = (int) getPreferredSize().getWidth();
    int minHeight = (int) getPreferredSize().getHeight();
    addComponentListener(new MinimumSizeComponentListener(this,
        minWidth, minHeight));
    Utils.centerOnComponent(this, parent);
    setVisible(true);
  }

  /* The following three methods are just commodity methods to retrieve
   * localized messages */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
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
    gbc.gridwidth = 3;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    p1.add(UIFactory.makeJLabel(UIFactory.IconType.WARNING_LARGE, null,
        UIFactory.TextStyle.NO_STYLE), gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    Insets pInsets = UIFactory.getCurrentStepPanelInsets();
    gbc.insets.left = 0;
    gbc.fill = GridBagConstraints.BOTH;
    String msg = getMsg("error-browser-display-msg", new String[]
      { url });
    JTextComponent tf =
        UIFactory.makeHtmlPane(msg,
            UIFactory.ERROR_DIALOG_FONT);
    tf.setOpaque(false);
    tf.setEditable(false);
    p1.add(tf, gbc);

    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    JButton copyButton =
        UIFactory.makeJButton(getMsg("error-browser-copy-button-label"),
            getMsg("error-browser-copy-button-tooltip"));
    copyButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        StringSelection s = new StringSelection(url);
        getToolkit().getSystemClipboard().setContents(s, s);
      }
    });
    gbc.insets.left = UIFactory.LEFT_INSET_COPY_BUTTON;
    gbc.insets.right = pInsets.right;
    gbc.fill = GridBagConstraints.NONE;
    p1.add(copyButton, gbc);
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    p1.add(Box.createVerticalGlue(), gbc);

    JPanel p2 = new JPanel(new GridBagLayout());
    p2.setOpaque(false);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    p2.add(Box.createHorizontalGlue(), gbc);
    JButton closeButton =
        UIFactory.makeJButton(getMsg("close-button-label"),
            getMsg("error-browser-close-button-tooltip"));
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    p2.add(closeButton, gbc);
    closeButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        dispose();
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
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      // UIFactory.initialize();
      WebBrowserErrorDialog dlg =
          new WebBrowserErrorDialog(new JFrame(),
              new WebBrowserException("http://www.yahoo.com", "toto", null));
      dlg.packAndShow();
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
