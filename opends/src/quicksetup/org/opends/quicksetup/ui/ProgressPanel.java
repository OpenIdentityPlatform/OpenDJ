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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.ProgressDescriptor;

/**
 * This panel is used to show the progress of the install or the uninstall.
 *
 */
public class ProgressPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 8129425068163357170L;

  private JEditorPane progressBarLabel;

  private JProgressBar progressBar;

  private JButton btnCancel;

  private JEditorPane detailsTextArea;

  private String lastText;

  private Component lastFocusComponent;

  /**
   * ProgressPanel constructor.
   * @param application Application this panel represents
   */
  public ProgressPanel(GuiApplication application)
  {
    super(application);
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

    progressBarLabel = UIFactory.makeHtmlPane(
        getMsg("progressbar-initial-label"), UIFactory.PROGRESS_FONT);
    progressBarLabel.setOpaque(false);
    progressBarLabel.setEditable(false);
    progressBarLabel.setFocusable(false);
    progressBarLabel.setFocusCycleRoot(false);
    CustomHTMLEditorKit htmlEditor = new CustomHTMLEditorKit();
    htmlEditor.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        // Assume is the authentication button.
        ButtonEvent be = new ButtonEvent(ev.getSource(),
            ButtonName.LAUNCH_STATUS_PANEL);
        notifyButtonListeners(be);
      }
    });
    progressBarLabel.setEditorKit(htmlEditor);
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

    JScrollPane scroll = new JScrollPane();
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
          lastText = getFormatter().getFormattedAfterUrlClick(url,
              lastText);
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

    addFocusListeners();

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
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void displayProgress(ProgressDescriptor descriptor)
  {
    ProgressStep status = descriptor.getProgressStep();
    String summaryText = UIFactory.applyFontToHtml(descriptor
        .getProgressBarMsg(), UIFactory.PROGRESS_FONT);

    if (status.isLast()) {
      progressBar.setVisible(false);
      progressBarLabel.setFocusable(true);
      btnCancel.setVisible(false);
      if (!status.isError()) {
        summaryText = "<form>"+summaryText+"</form>";
      }
    }
    progressBarLabel.setText(summaryText);

    int v = descriptor.getProgressBarRatio();
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

    btnCancel = UIFactory.makeJButton(
                    getMsg("cancel-button-label"),
                    getMsg("cancel-button-tooltip"));
    btnCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        GuiApplication app = getApplication();
        QuickSetup qs = getQuickSetup();
        if (app.confirmCancel(qs)) {
          app.cancel();
          btnCancel.setEnabled(false);
        }
      }
    });

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

    if (getApplication().isCancellable()) {
      gbc.insets.left = 15;
      gbc.fill = GridBagConstraints.NONE;
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.gridwidth = 1;
      panel.add(btnCancel, gbc);
    }

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    panel.add(Box.createHorizontalGlue(), gbc);



    return panel;
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addFocusListeners()
  {
    final FocusListener l = new FocusListener()
    {
      public void focusGained(FocusEvent e)
      {
        lastFocusComponent = e.getComponent();
      }

      public void focusLost(FocusEvent e)
      {
      }
    };

    JComponent[] comps =
    {
        progressBarLabel,
        progressBar,
        btnCancel,
        detailsTextArea
    };
    for (int i = 0; i < comps.length; i++)
    {
      comps[i].addFocusListener(l);
    }

    lastFocusComponent = detailsTextArea;
  }

//  public static void main(String[] args) {
//    final UserData ud = new UpgradeUserData();
//    ud.setServerLocation("XXX/XXXXX/XX/XXXXXXXXXXXX/XXXX");
//    Upgrader app = new Upgrader();
//    app.setUserData(ud);
//    final ProgressPanel p = new ProgressPanel(app);
//    p.initialize();
//    JFrame frame = new JFrame();
//    frame.getContentPane().add(p);
//    frame.addComponentListener(new ComponentAdapter() {
//      public void componentHidden(ComponentEvent componentEvent) {
//        System.exit(0);
//      }
//    });
//    frame.pack();
//    frame.setVisible(true);
//    new Thread(new Runnable() {
//      public void run() {
//        p.beginDisplay(ud);
//      }
//    }).start();
//
//  }


}
