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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;

/**
 * This panel is used to show the progress of the start/stop/restart operations.
 *
 */
public class ProgressDialog extends JDialog
{
  private static final long serialVersionUID = 8635080171100378470L;

  private JEditorPane progressBarLabel;

  private JProgressBar progressBar;

  private JEditorPane detailsTextArea;

  private JScrollPane scroll;

  private String lastText;

  private JFrame parent;

  private JButton closeButton;

  private String panelTitle = getMsg("progress-title");

  private ProgressMessageFormatter formatter =
    new HtmlProgressMessageFormatter();

  /**
   * ProgressDialog constructor.
   * @param frame the parent frame for this dialog.
   */
  public ProgressDialog(JFrame frame)
  {
    super(frame);
    this.parent = frame;
    setTitle(getMsg("progress-dialog-title"));
    createLayout();
  }

  /**
   * Prepares size for this dialog.
   *
   */
  public void pack()
  {
    /*
     * TODO: find a way to calculate this dynamically.
     */
    setPreferredSize(new Dimension(500, 300));
    addComponentListener(new MinimumSizeComponentListener(this, 500, 300));
    super.pack();
    closeButton.requestFocusInWindow();
    getRootPane().setDefaultButton(closeButton);
  }

  /**
   * Returns the parent for this dialog.
   * @return the parent for this dialog.
   */
  public JFrame getFrame()
  {
    return parent;
  }

  /**
   * Sets the title of the panel.
   * @param title the title of the panel.
   */
  public void setPanelTitle(String title)
  {
    this.panelTitle = title;
  }

  /**
   * Returns the title of the panel.
   * @return the title of the panel
   */
  public String getPanelTitle()
  {
    return panelTitle;
  }

  /**
   * Sets the enable state of the close button.
   * @param enable whether to enable or disable the button.
   */
  public void setCloseButtonEnabled(boolean enable)
  {
    closeButton.setEnabled(enable);
  }

  /**
   * Sets the text in the summary label.  The text can be in HTML format.
   * @param text the text to be set.
   */
  public void setSummary(String text)
  {
    progressBarLabel.setText(text);
  }

  /**
   * Sets the text in the details text pane.  The text can be in HTML format.
   * @param text the text to be set.
   */
  public void setDetails(String text)
  {
    detailsTextArea.setText(text);
  }

  /**
   * Returns the formatter that will be used to display the messages in this
   * panel.
   * @return the formatter that will be used to display the messages in this
   * panel.
   */
  private ProgressMessageFormatter getFormatter()
  {
    if (formatter == null)
    {
      formatter = new HtmlProgressMessageFormatter();
    }
    return formatter;
  }

  /**
   * Creates the layout of the dialog panel.
   *
   */
  private void createLayout()
  {
    /* Create title panel */
    JPanel titlePanel = new JPanel(new GridBagLayout());
    titlePanel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;

    String title = getPanelTitle();

    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, title,
            UIFactory.TextStyle.TITLE);
    l.setOpaque(false);
    titlePanel.add(l, gbc);

    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    titlePanel.add(Box.createHorizontalGlue(), gbc);

    /* Create input panel. */
    JPanel mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setOpaque(false);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    mainPanel.add(titlePanel, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_INSTRUCTIONS_SUBPANEL;
    progressBarLabel =
        UIFactory.makeHtmlPane(getMsg("progressbar-initial-label"),
            UIFactory.PROGRESS_FONT);
    progressBarLabel.setOpaque(false);
    progressBarLabel.setEditable(false);
    mainPanel.add(progressBarLabel, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_PROGRESS_BAR;
    gbc.insets.bottom = UIFactory.BOTTOM_INSET_PROGRESS_BAR;
    mainPanel.add(createProgressBarPanel(), gbc);
    progressBar.setToolTipText(getMsg("progressbar-tooltip"));

    l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("progress-details-label"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    gbc.insets = UIFactory.getEmptyInsets();
    mainPanel.add(l, gbc);

    scroll = new JScrollPane();
    detailsTextArea = UIFactory.makeProgressPane(scroll);
    detailsTextArea.setBackground(
        UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    detailsTextArea.addHyperlinkListener(new HyperlinkListener()
    {
      public void hyperlinkUpdate(HyperlinkEvent e)
      {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        {
          String url = e.getURL().toString();
          String newText = getFormatter().getFormattedAfterUrlClick(url,
              lastText);
          lastText = newText;
          detailsTextArea.setText(lastText);
        }
      }
    });
    detailsTextArea.setAutoscrolls(true);
    scroll.setViewportView(detailsTextArea);

    scroll.setBorder(UIFactory.TEXT_AREA_BORDER);
    scroll.setWheelScrollingEnabled(true);
    l.setLabelFor(detailsTextArea);
    gbc.insets.top = UIFactory.TOP_INSET_PROGRESS_TEXTAREA;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    mainPanel.add(scroll, gbc);

    /* Create buttons panel */
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    buttonsPanel.setBackground(UIFactory.DEFAULT_BACKGROUND);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    closeButton =
        UIFactory.makeJButton(getMsg("close-button-label"),
            getMsg("close-progress-button-tooltip"));
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    buttonsPanel.add(closeButton, gbc);
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
    gbc.insets = UIFactory.getEmptyInsets();
    JPanel p1 = new JPanel(new GridBagLayout());
    p1.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    p1.setBorder(UIFactory.DIALOG_PANEL_BORDER);
    gbc.insets = UIFactory.getCurrentStepPanelInsets();
    p1.add(mainPanel, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    p.add(p1, gbc);
    gbc.weighty = 0.0;
    gbc.insets = UIFactory.getButtonsPanelInsets();
    p.add(buttonsPanel, gbc);

    getContentPane().add(p);
  }

  /**
   * Creates the progress bar panel.
   * @return the created panel.
   */
  private JPanel createProgressBarPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.HORIZONTAL;

    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    // The ProgressDescriptor provides the ratio in %
    progressBar.setMaximum(100);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    panel.add(Box.createHorizontalStrut(UIFactory.PROGRESS_BAR_SIZE), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    panel.add(Box.createHorizontalGlue(), gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    //panel.add(progressBar, gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * The following three methods are just commodity methods to get localized
   * messages.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Method written for testing purposes.
   * @param args the arguments to be passed to the test program.
   */
  public static void main(String[] args)
  {
    try
    {
      ProgressDialog dlg = new ProgressDialog(new JFrame());
      dlg.pack();
      dlg.setVisible(true);
    } catch (Exception ex)
    {
      ex.printStackTrace();
    }
  }
}
