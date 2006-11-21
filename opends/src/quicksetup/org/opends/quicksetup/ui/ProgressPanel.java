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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.opends.quicksetup.installer.InstallProgressDescriptor;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;

/**
 * This panel is used to show the progress of the install or the uninstall.
 *
 */
public class ProgressPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 8129425068163357170L;

  private JEditorPane progressBarLabel;

  private JProgressBar progressBar;

  private JEditorPane detailsTextArea;

  private JScrollPane scroll;

  private String lastText;

  private ProgressMessageFormatter formatter;

  /**
   * ProgressPanel constructor.
   */
  public ProgressPanel()
  {
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    progressBarLabel =
        UIFactory.makeHtmlPane(getMsg("progressbar-initial-label"),
            UIFactory.PROGRESS_FONT);
    progressBarLabel.addHyperlinkListener(this);
    panel.add(progressBarLabel, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_PROGRESS_BAR;
    gbc.insets.bottom = UIFactory.BOTTOM_INSET_PROGRESS_BAR;
    panel.add(createProgressBarPanel(), gbc);
    progressBar.setToolTipText(getMsg("progressbar-tooltip"));

    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            getMsg("progress-details-label"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    gbc.insets = UIFactory.getEmptyInsets();
    panel.add(l, gbc);

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
    panel.add(scroll, gbc);

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle()
  {
    return getMsg("progress-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  public void displayProgress(InstallProgressDescriptor descriptor)
  {
    progressBarLabel.setText(UIFactory.applyFontToHtml(descriptor
        .getProgressBarMsg(), UIFactory.PROGRESS_FONT));
    InstallProgressStep status = descriptor.getProgressStep();
    if ((status == InstallProgressStep.FINISHED_WITH_ERROR)
        || (status == InstallProgressStep.FINISHED_SUCCESSFULLY))
    {
      progressBar.setVisible(false);
    }
    int v = descriptor.getProgressBarRatio().intValue();
    if (v > 0)
    {
      progressBar.setIndeterminate(false);
      progressBar.setValue(v);
    }
    lastText = descriptor.getDetailsMsg();
    detailsTextArea.setText(lastText);
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
    panel.add(progressBar, gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * Returns the formatter that will be used to display the messages in this
   * panel.
   * @return the formatter that will be used to display the messages in this
   * panel.
   */
  ProgressMessageFormatter getFormatter()
  {
    if (formatter == null)
    {
      formatter = new HtmlProgressMessageFormatter();
    }
    return formatter;
  }
}
