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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
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
import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

/** This panel is used to show the progress of the application. */
public class ProgressPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 8129425068163357170L;

  private JEditorPane progressBarLabel;

  private JProgressBar progressBar;

  private JButton btnCancel;

  private JEditorPane detailsTextArea;

  private LocalizableMessage lastText;

  private Component lastFocusComponent;

  /**
   * ProgressPanel constructor.
   * @param application Application this panel represents
   */
  public ProgressPanel(GuiApplication application)
  {
    super(application);
  }

  @Override
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
            null,
            UIFactory.PROGRESS_FONT);
    progressBarLabel.setOpaque(false);
    progressBarLabel.setEditable(false);
    progressBarLabel.setFocusable(false);
    progressBarLabel.setFocusCycleRoot(false);
    CustomHTMLEditorKit htmlEditor = new CustomHTMLEditorKit();
    htmlEditor.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        // Assume is the authentication button.
        ButtonEvent be = new ButtonEvent(ev.getSource(),
            ButtonName.LAUNCH_STATUS_PANEL);
        notifyButtonListeners(be);
      }
    });
    progressBarLabel.setEditorKit(htmlEditor);
    String summaryText = UIFactory.applyFontToHtml(
        String.valueOf(INFO_PROGRESSBAR_INITIAL_LABEL.get()),
        UIFactory.PROGRESS_FONT);
    progressBarLabel.setText(summaryText);
    progressBarLabel.addHyperlinkListener(this);
    panel.add(progressBarLabel, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_PROGRESS_BAR;
    gbc.insets.bottom = UIFactory.BOTTOM_INSET_PROGRESS_BAR;
    panel.add(createProgressBarPanel(), gbc);
    progressBar.setToolTipText(INFO_PROGRESSBAR_TOOLTIP.get().toString());

    JLabel l =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            INFO_PROGRESS_DETAILS_LABEL.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    gbc.insets = UIFactory.getEmptyInsets();
    panel.add(l, gbc);

    JScrollPane scroll = new JScrollPane();
    detailsTextArea = UIFactory.makeProgressPane(scroll);
    detailsTextArea.setBackground(
        UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    detailsTextArea.addHyperlinkListener(new HyperlinkListener()
    {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e)
      {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        {
          String url = e.getURL().toString();
          lastText = getFormatter().getFormattedAfterUrlClick(url,
              lastText);
          detailsTextArea.setText(lastText.toString());
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

  @Override
  protected LocalizableMessage getInstructions()
  {
    return null;
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_PROGRESS_PANEL_TITLE.get();
  }

  @Override
  protected boolean requiresScroll()
  {
    return false;
  }

  @Override
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  @Override
  public void displayProgress(ProgressDescriptor descriptor)
  {
    ProgressStep status = descriptor.getProgressStep();
    String summaryText = UIFactory.applyFontToHtml(
            String.valueOf(descriptor.getProgressBarMsg()),
            UIFactory.PROGRESS_FONT);

    if (status.isLast()) {
      progressBar.setVisible(false);
      progressBarLabel.setFocusable(true);
      btnCancel.setVisible(false);
      if (!status.isError()) {
        summaryText = "<form>"+summaryText+"</form>";
      }
    }

    progressBarLabel.setText(summaryText);

    Integer v = descriptor.getProgressBarRatio();
    if (v != null && v > 0)
    {
      progressBar.setIndeterminate(false);
      progressBar.setValue(v);
    }
    lastText = descriptor.getDetailsMsg();
    detailsTextArea.setText(lastText.toString());
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
                    INFO_CANCEL_BUTTON_LABEL.get(),
                    INFO_CANCEL_BUTTON_TOOLTIP.get());
    btnCancel.addActionListener(new ActionListener() {
      @Override
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

  /** Adds the required focus listeners to the fields. */
  private void addFocusListeners()
  {
    final FocusListener l = new FocusListener()
    {
      @Override
      public void focusGained(FocusEvent e)
      {
        lastFocusComponent = e.getComponent();
      }

      @Override
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
    for (JComponent comp : comps) {
      comp.addFocusListener(l);
    }

    lastFocusComponent = detailsTextArea;
  }
}
