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
import static org.opends.messages.QuickSetupMessages.INFO_CLOSE_BUTTON_LABEL;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLDocument;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.PrintStreamListener;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.util.ApplicationPrintStream;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The dialog that is used to display progress in a task.
 *
 */
public class ProgressDialog extends GenericDialog
{
  private static final long serialVersionUID = -6462866257463062629L;
  private ProgressPanel progressPanel;

  /**
   * Constructor of the dialog.
   * @param parentDialog the parent dialog.
   * @param title the title of the dialog.
   * @param info the control panel information.
   */
  public ProgressDialog(Component parentDialog, Message title,
      ControlPanelInfo info)
  {
    super(Utilities.getFrame(parentDialog), getPanel(info));
    Utilities.centerGoldenMean(this, parentDialog);
    setTitle(title.toString());
    progressPanel = (ProgressPanel)panel;
    getRootPane().setDefaultButton(progressPanel.closeButton);
  }

  /**
   * Creates the panel that will be contained in the dialog.
   * @param info the control panel information.
   * @return the panel that will be contained in the dialog.
   */
  private static StatusGenericPanel getPanel(ControlPanelInfo info)
  {
    ProgressPanel panel = new ProgressPanel();
    panel.setInfo(info);
    return panel;
  }

  /**
   * Adds two print stream listeners.
   * @param outPrintStream the output stream listener.
   * @param errorPrintStream the error stream listener.
   */
  public void addPrintStreamListeners(ApplicationPrintStream outPrintStream,
      ApplicationPrintStream errorPrintStream)
  {
    errorPrintStream.addListener(new PrintStreamListener()
    {
      public void newLine(final String msg)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            progressPanel.appendErrorLine(msg);
          }
        });
      }
    });
    outPrintStream.addListener(new PrintStreamListener()
    {
      public void newLine(final String msg)
      {
        /**
         * {@inheritDoc}
         */
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            progressPanel.appendOutputLine(msg);
          }
        });
      }
    });
  }

  /**
   * Returns the progress bar of the dialog.
   * @return the progress bar of the dialog.
   */
  public JProgressBar getProgressBar()
  {
    return progressPanel.getProgressBar();
  }

  /**
   * Appends some text in HTML format to the 'Details' section of the dialog.
   * @param text the text in HTML format to be appended.
   */
  public void appendProgressHtml(String text)
  {
    progressPanel.appendHtml(text);
  }

  /**
   * Resets the contents of the 'Details' section of the dialog.
   *
   */
  public void resetProgressLogs()
  {
    progressPanel.resetLogs();
  }

  /**
   * Sets the text to be displayed in the summary area of the progress
   * dialog.
   * @param text the text to be displayed.
   */
  public void setSummary(Message text)
  {
    progressPanel.setSummary(text);
  }

  /**
   * {@inheritDoc}
   */
  public void setEnabledClose(boolean enable)
  {
    progressPanel.closeButton.setEnabled(enable);
  }

  /**
   * Note: this will make the dialog to be closed asynchronously.  So that
   * sequential calls to setTaskIsOver(true) and setTaskIsOver(false) on the
   * event thread are guaranteed not to close the dialog.
   * @param taskIsOver whether the task is finished or not.
   */
  public void setTaskIsOver(boolean taskIsOver)
  {
    progressPanel.taskIsOver = taskIsOver;
    progressPanel.closeWhenOverClicked();
  }

  /**
   * The panel contained in the progress dialog.
   *
   */
  static class ProgressPanel extends StatusGenericPanel
  {
    private static final long serialVersionUID = -364496083928260306L;
    private BasicExpander details;
    private JEditorPane logs;
    private JScrollPane scroll;
    private JCheckBox closeWhenOver;
    private final String LASTID = "lastid";
    private final String INIT_TEXT = "<span id=\""+LASTID+
    "\" style=\"bold\">&nbsp;</span>";
    private JProgressBar progressBar;
    private Component extraStrut;
    private JButton closeButton;
    private static final String FAKE_PROGRESS_TEXT =
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"+
      "<br><br><br><br><br><br><br><br><br><br><br><br><br><br><br>"+
      "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private int heightDiff;
    private int lastCollapsedHeight = -1;
    private int lastExpandedHeight = -1;

    private static boolean lastShowDetails = false;
    private static boolean lastCloseWhenOver = false;

    private boolean taskIsOver;

    /**
     * Default constructor.
     *
     */
    public ProgressPanel()
    {
      super();
      createLayout();
    }

    /**
     * {@inheritDoc}
     */
    public Message getTitle()
    {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresScroll()
    {
      return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean requiresBorder()
    {
      return false;
    }

    /**
     * Appends a line to the logs (Details are) section of the panel.  The text
     * will be preceded by a new line (is similar to println()).
     * @param msg the HTML formatted text to be appended.
     */
    public void appendErrorLine(String msg)
    {
      HTMLDocument doc = (HTMLDocument)logs.getDocument();
      msg = Utilities.applyFont(msg+"<br>", ColorAndFontConstants.progressFont);
      try
      {
        doc.insertBeforeStart(doc.getElement(LASTID), msg);
      }
      catch (Throwable t)
      {
        // Bug
        t.printStackTrace();
      }
    }

    /**
     * Sets the text to be displayed in the summary area of the progress
     * dialog.
     * @param msg the text to be displayed.
     */
    public void setSummary(Message msg)
    {
      errorPane.setText(msg.toString());
    }

    /**
     * Appends a line to the logs (Details are) section of the panel.  The text
     * will be preceded by a new line (is similar to println()).
     * @param msg the HTML formatted text to be appended.
     */
    public void appendOutputLine(String msg)
    {
      appendErrorLine(msg);
    }

    /**
     * Appends text to the logs (Details are) section of the panel.  The text
     * will be appended as it is (is similar to print()).
     * @param msg the HTML formatted text to be appended.
     */
    public void appendHtml(String msg)
    {
      HTMLDocument doc = (HTMLDocument)logs.getDocument();

      try
      {
        doc.insertBeforeStart(doc.getElement(LASTID), msg);
      }
      catch (Throwable t)
      {
        // Bug
        t.printStackTrace();
      }
    }

    /**
     * Resets the contents of the logs (Details) section.
     *
     */
    public void resetLogs()
    {
      logs.setText(INIT_TEXT);
    }

    /**
     * Creates the layout of the panel (but the contents are not populated
     * here).
     *
     */
    private void createLayout()
    {
      GridBagConstraints gbc = new GridBagConstraints();
      addErrorPane(gbc);

      errorPane.setVisible(true);
      errorPane.setText(Utilities.applyFont(
              INFO_CTRL_PANEL_PLEASE_WAIT_SUMMARY.get().toString(),
              ColorAndFontConstants.defaultFont));

      gbc.anchor = GridBagConstraints.WEST;
      gbc.gridwidth = 1;
      gbc.gridx = 0;
      gbc.gridy = 1;

      progressBar = new JProgressBar();
      progressBar.setMaximum(100);
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = new Insets(10, 20, 0, 30);
      add(progressBar, gbc);

      gbc.insets.top = 10;
      gbc.insets.bottom = 5;
      details =
        new BasicExpander(INFO_CTRL_PANEL_PROGRESS_DIALOG_DETAILS_LABEL.get());
      gbc.gridy ++;
      add(details, gbc);

      logs = Utilities.makeHtmlPane(FAKE_PROGRESS_TEXT,
          ColorAndFontConstants.progressFont);
      gbc.gridy ++;
      gbc.weighty = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets.top = 5;
      gbc.insets.right = 20;
      gbc.insets.bottom = 5;
      scroll = Utilities.createScrollPane(logs);
      scroll.setOpaque(false);
      scroll.getViewport().setOpaque(false);
      add(scroll, gbc);
      Dimension scrollDim = scroll.getPreferredSize();

      gbc.weighty = 1.0;
      extraStrut = Box.createRigidArea(new Dimension(scrollDim.width, 50));
      add(extraStrut, gbc);
      gbc.gridy ++;
      gbc.weighty = 0.0;
      add(Box.createHorizontalStrut(scrollDim.width), gbc);

      heightDiff = scrollDim.height - extraStrut.getHeight();

      logs.setText(INIT_TEXT);

      scroll.setPreferredSize(scrollDim);

      updateVisibility(lastShowDetails);
      details.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          lastShowDetails = details.isSelected();
          updateVisibility(lastShowDetails);
        }
      });

//    The button panel
      gbc.gridy ++;
      gbc.weighty = 0.0;
      gbc.insets = new Insets(0, 0, 0, 0);
      add(createButtonsPanel(), gbc);
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
      closeWhenOver = Utilities.createCheckBox(
          INFO_CTRL_PANEL_CLOSE_WINDOW_WHEN_OPERATION_COMPLETES_LABEL.get());
      closeWhenOver.setOpaque(false);
      closeWhenOver.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          closeWhenOverClicked();
        }
      });
      closeWhenOver.setSelected(lastCloseWhenOver);
      gbc.insets = new Insets(10, 10, 10, 10);
      buttonsPanel.add(closeWhenOver, gbc);
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
      closeButton = Utilities.createButton(INFO_CLOSE_BUTTON_LABEL.get());
      closeButton.setOpaque(false);
      gbc.gridx ++;
      gbc.insets.left = 5;
      gbc.insets.right = 10;
      buttonsPanel.add(closeButton, gbc);
      closeButton.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          closeClicked();
        }
      });

      buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
          ColorAndFontConstants.defaultBorderColor));

      return buttonsPanel;
    }

    private void updateVisibility(boolean showDetails)
    {
      scroll.setVisible(showDetails);
      extraStrut.setVisible(!showDetails);
      details.setSelected(showDetails);
      if (showDetails)
      {
        Window dialog = Utilities.getParentDialog(this);
        if (dialog != null)
        {
          lastCollapsedHeight = dialog.getSize().height;
          if (lastExpandedHeight == -1)
          {
            dialog.setSize(new Dimension(dialog.getSize().width,
                dialog.getSize().height + heightDiff));
          }
          else
          {
            dialog.setSize(new Dimension(dialog.getSize().width,
                lastExpandedHeight));
          }
        }
      }
      else
      {
        Window dialog = Utilities.getParentDialog(this);
        if (dialog != null)
        {
          lastExpandedHeight = dialog.getSize().height;
          if (lastCollapsedHeight == -1)
          {
            packParentDialog();
          }
          else
          {
            dialog.setSize(new Dimension(dialog.getSize().width,
                lastCollapsedHeight));
          }
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    public GenericDialog.ButtonType getButtonType()
    {
      return GenericDialog.ButtonType.NO_BUTTON;
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
    public Component getPreferredFocusComponent()
    {
      return details;
    }

    /**
     * {@inheritDoc}
     */
    public void okClicked()
    {
      Utilities.getParentDialog(this).setVisible(false);
    }

    /**
     * Returns the progress bar of the dialog.
     * @return the progress bar of the dialog.
     */
    public JProgressBar getProgressBar()
    {
      return progressBar;
    }

    /**
     * Checks if the 'Close when over' checkbox is selected and if it is the
     * case, closes the dialog after waiting for 2 seconds (so that the user
     * can see the result, or cancel the automatic closing of the dialog).
     *
     */
    private void closeWhenOverClicked()
    {
      lastCloseWhenOver = closeWhenOver.isSelected();
      if (lastCloseWhenOver && taskIsOver)
      {
        Thread t = new Thread(new Runnable()
        {
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            try
            {
              Thread.sleep(2000);
              SwingUtilities.invokeLater(new Runnable()
              {
                public void run()
                {
                  if (closeWhenOver.isSelected() && taskIsOver)
                  {
                    closeClicked();
                  }
                }
              });
            }
            catch (Throwable t)
            {
            }
          }
        });
        t.start();
      }
    }
  }
}
