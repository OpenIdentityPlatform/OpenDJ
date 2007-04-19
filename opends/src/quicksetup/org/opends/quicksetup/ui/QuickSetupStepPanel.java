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

import java.util.HashMap;
import java.util.HashSet;

import javax.swing.Box;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.ProgressDescriptor;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.URLWorker;
import org.opends.quicksetup.util.Utils;

/**
 * This is an abstract class that is extended by all the classes that are in
 * the CardLayout of CurrentStepPanel.  All the panels that appear on the
 * top-right side of the dialog extend this class: WelcomePane, ReviewPanel,
 * etc.
 *
 */
public abstract class QuickSetupStepPanel extends QuickSetupPanel
implements HyperlinkListener
{
  private Component inputPanel;

  private HashSet<ButtonActionListener> buttonListeners =
    new HashSet<ButtonActionListener>();

  private ProgressMessageFormatter formatter;

  /* We can use a HashMap (not multi-thread safe) because all
  the calls to this object are done in the event-thread.
  */
  private HashMap<String, URLWorker> hmURLWorkers =
      new HashMap<String, URLWorker>();

  /**
   * Creates a default instance.
   * @param application Application this panel represents
   */
  public QuickSetupStepPanel(GuiApplication application) {
    super(application);
  }

  /**
   * Initializes this panel.  Called soon after creation.  In general this
   * is where maps should be populated etc.
   */
  public void initialize() {
    createLayout();
  }

  /**
   * Called just before the panel is shown: used to update the contents of the
   * panel with new UserData (used in particular in the review panel).
   *
   * @param data the new user data.
   */
  public void beginDisplay(UserData data)
  {
  }

  /**
   * Called just after the panel is shown: used to set focus properly.
   */
  public void endDisplay()
  {
  }

  /**
   * Called when a progress change must be reflected in the panels.  Only
   * ProgressPanel overwrites this method and for all the others it stays empty.
   * @param descriptor the descriptor of the Installation progress.
   */
  public void displayProgress(ProgressDescriptor descriptor)
  {
  }

  /**
   * Implements HyperlinkListener.  When the user clicks on a link we will
   * try to display the associated URL in the browser of the user.
   *
   * @param e the HyperlinkEvent.
   */
  public void hyperlinkUpdate(HyperlinkEvent e)
  {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
    {
      String url = e.getURL().toString();
      if (!isURLWorkerRunning(url))
      {
        /*
         * Only launch the worker if there is not already a worker trying to
         * display this URL.
         */
        URLWorker worker = new URLWorker(this, url);
        startWorker(worker);
      }
    }
  }

  /**
   * Returns the value corresponding to the provided FieldName.
   * @param fieldName the FieldName for which we want to obtain the value.
   * @return the value corresponding to the provided FieldName.
   */
  public Object getFieldValue(FieldName fieldName)
  {
    return null;
  }

  /**
   * Sets the specified value in the field associated with the field name.
   * @param fieldName the field name associated with the field.
   * @param value the value that must be set.
   */
  public void setFieldValue(FieldName fieldName, Object value)
  {
  }

  /**
   * Marks as invalid (or valid depending on the value of the invalid parameter)
   * a field corresponding to FieldName.  This basically implies udpating the
   * style of the JLabel associated with fieldName (the association is done
   * using the LabelFieldDescriptor class).
   * @param fieldName the FieldName to be marked as valid or invalid.
   * @param invalid whether to mark the field as valid or invalid.
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
  }

  /**
   * Returns the minimum width of the panel.  This is used to calculate the
   * minimum widht of the dialog.
   * @return the minimum width of the panel.
   */
  public int getMinimumWidth()
  {
    // Just take the preferred width of the inputPanel because the
    // instructionsPanel
    // are too wide.
    int width = 0;
    if (inputPanel != null)
    {
      width = (int) inputPanel.getPreferredSize().getWidth();
    }
    return width;
  }

  /**
   * Returns the minimum height of the panel.  This is used to calculate the
   * minimum height of the dialog.
   * @return the minimum height of the panel.
   */
  public int getMinimumHeight()
  {

    return (int) getPreferredSize().getHeight();
  }


  /**
   * Adds a button listener.  All the button listeners will be notified when
   * the buttons are clicked (by the user or programatically).
   * @param l the ButtonActionListener to be added.
   */
  public void addButtonActionListener(ButtonActionListener l)
  {
    buttonListeners.add(l);
  }

  /**
   * Removes a button listener.
   * @param l the ButtonActionListener to be removed.
   */
  public void removeButtonActionListener(ButtonActionListener l)
  {
    buttonListeners.remove(l);
  }

  /**
   * Notifies the button action listeners that an event occurred.
   * @param ev the button event to be notified.
   */
  protected void notifyButtonListeners(ButtonEvent ev)
  {
    for (ButtonActionListener l : buttonListeners)
    {
      l.buttonActionPerformed(ev);
    }
  }
  /**
   * Creates the layout of the panel.
   *
   */
  private void createLayout()
  {
    setLayout(new GridBagLayout());

    setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();

    Component titlePanel = createTitlePanel();
    Component instructionsPanel = createInstructionsPanel();
    inputPanel = createInputPanel();

    boolean somethingAdded = false;

    if (titlePanel != null)
    {
      gbc.weightx = 1.0;
      gbc.weighty = 0.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      gbc.insets.left = 0;
      add(titlePanel, gbc);
      somethingAdded = true;
    }

    if (instructionsPanel != null)
    {
      if (somethingAdded)
      {
        gbc.insets.top = UIFactory.TOP_INSET_INSTRUCTIONS_SUBPANEL;
      } else
      {
        gbc.insets.top = 0;
      }
      gbc.insets.left = 0;
      gbc.weightx = 1.0;
      gbc.weighty = 0.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      add(instructionsPanel, gbc);
      somethingAdded = true;
    }

    if (inputPanel != null)
    {
      if (somethingAdded)
      {
        gbc.insets.top = UIFactory.TOP_INSET_INPUT_SUBPANEL;
      } else
      {
        gbc.insets.top = 0;
      }
      gbc.weightx = 1.0;
      gbc.weighty = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      gbc.insets.left = 0;
      add(inputPanel, gbc);
      somethingAdded = true;

    } else
    {
      addVerticalGlue(this);
    }
  }

  /**
   * Commodity method that returns a JLabel based on a LabelFieldDescriptor.
   * @param desc the LabelFieldDescriptor describing the JLabel.
   * @return a JLabel based on a LabelFieldDescriptor.
   */
  protected JLabel makeJLabel(LabelFieldDescriptor desc)
  {
    UIFactory.TextStyle style;
    if (desc.getLabelType() == LabelFieldDescriptor.LabelType.PRIMARY)
    {
      style = UIFactory.TextStyle.PRIMARY_FIELD_VALID;
    } else
    {
      style = UIFactory.TextStyle.SECONDARY_FIELD_VALID;
    }
    return UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, desc.getLabel(),
        style);
  }

  /**
   * Creates and returns the panel that contains the layout specific to the
   * panel.
   * @return the panel that contains the layout specific to the
   * panel.
   */
  protected abstract Component createInputPanel();

  /**
   * Returns the title of this panel.
   * @return the title of this panel.
   */
  protected abstract String getTitle();

  /**
   * Returns the instruction of this panel.
   * @return the instruction of this panel.
   */
  protected abstract String getInstructions();

  /**
   * Commodity method that adds a vertical glue at the bottom of a given panel.
   * @param panel the panel to which we want to add a vertical glue.
   */
  protected void addVerticalGlue(JPanel panel)
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);
  }

  /**
   * This method is called by the URLWorker when it has finished its task.
   * @param worker the URLWorker that finished its task.
   */
  public void urlWorkerFinished(URLWorker worker)
  {
    hmURLWorkers.remove(worker.getURL());
  }

  /**
   * Returns <CODE>true</CODE> if this is a WebStart based installer and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this is a WebStart based installer and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isWebStart()
  {
    return Utils.isWebStart();
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

  /**
   * Returns a localized String representation of the provided SecurityOptions
   * object.
   * @param ops the SecurityOptions object from which we want to obtain the
   * String representation.
   * @param html whether the resulting String must be in HTML or not.
   * @return a localized String representation of the provided SecurityOptions
   * object.
   */
  protected String getSecurityOptionsString(SecurityOptions ops, boolean html)
  {
    StringBuilder buf = new StringBuilder();

    if (ops.getCertificateType() ==
      SecurityOptions.CertificateType.NO_CERTIFICATE)
    {
      buf.append(getMsg("no-security"));
    }
    else
    {
      if (ops.getEnableStartTLS())
      {
        buf.append(getMsg("enable-starttls"));
      }
      if (ops.getEnableSSL())
      {
        if (buf.length() > 0)
        {
          if (html)
          {
            buf.append("<br>");
          }
          else
          {
            buf.append("\n");
          }
        }
        String[] arg = new String[] {String.valueOf(ops.getSslPort())};
        buf.append(getMsg("enable-ssl", arg));
      }
      if (html)
      {
        buf.append("<br>");
      }
      else
      {
        buf.append("\n");
      }
      String certMsg;
      switch (ops.getCertificateType())
      {
      case SELF_SIGNED_CERTIFICATE:
        certMsg = getMsg("self-signed-certificate");
        break;

      case JKS:
        certMsg = getMsg("jks-certificate");
        break;

      case PKCS11:
        certMsg = getMsg("pkcs11-certificate");
        break;

      case PKCS12:
        certMsg = getMsg("pkcs12-certificate");
        break;

      default:
        throw new IllegalStateException("Unknown certificate options type: "+
            ops.getCertificateType());
      }
      buf.append(certMsg);
    }

    if (html)
    {
      return "<html>"+UIFactory.applyFontToHtml(buf.toString(),
          UIFactory.SECONDARY_FIELD_VALID_FONT);
    }
    else
    {
      return buf.toString();
    }
  }

  /**
   * Creates and returns the title panel.
   * @return the title panel.
   */
  private Component createTitlePanel()
  {
    Component titlePanel = null;
    String title = getTitle();
    if (title != null)
    {
      JPanel p = new JPanel(new GridBagLayout());
      p.setOpaque(false);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.NORTHWEST;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 0.0;
      gbc.gridwidth = GridBagConstraints.RELATIVE;

      JLabel l =
          UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, title,
              UIFactory.TextStyle.TITLE);
      p.add(l, gbc);

      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      p.add(Box.createHorizontalGlue(), gbc);

      titlePanel = p;
    }
    return titlePanel;
  }

  /**
   * Creates and returns the instructions panel.
   * @return the instructions panel.
   */
  private Component createInstructionsPanel()
  {
    Component instructionsPanel = null;
    String instructions = getInstructions();
    if (instructions != null)
    {
      JEditorPane p =
          UIFactory.makeHtmlPane(instructions, UIFactory.INSTRUCTIONS_FONT);
      p.setOpaque(false);
      p.setEditable(false);
      p.addHyperlinkListener(this);
      instructionsPanel = p;
    }
    return instructionsPanel;
  }

  /**
   * Returns <CODE>true</CODE> if there is URLWorker running for the given url
   * and <CODE>false</CODE> otherwise.
   * @param url the url.
   * @return <CODE>true</CODE> if there is URLWorker running for the given url
   * and <CODE>false</CODE> otherwise.
   */
  private boolean isURLWorkerRunning(String url)
  {
    return hmURLWorkers.get(url) != null;
  }

  /**
   * Starts a worker.
   * @param worker the URLWorker to be started.
   */
  private void startWorker(URLWorker worker)
  {
    hmURLWorkers.put(worker.getURL(), worker);
    worker.startBackgroundTask();
  }
}
