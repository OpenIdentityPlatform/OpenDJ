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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.MonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.*;
import org.opends.guitools.controlpanel.task.RebuildIndexTask;
import org.opends.guitools.controlpanel.task.RestartServerTask;
import org.opends.guitools.controlpanel.task.StartServerTask;
import org.opends.guitools.controlpanel.task.StopServerTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.MessageDescriptor;
import org.opends.quicksetup.ui.CustomHTMLEditorKit;
import org.opends.server.util.ServerConstants;

/**
 * An abstract class that contains a number of methods that are shared by all
 * the inheriting classes.  In general a StatusGenericPanel is contained in a
 * GenericDialog and specifies the kind of buttons that this dialog has.  The
 * StatusGenericPanel is also notified when the dialog is displayed (through
 * the toBeDisplayed method)
 *
 */

public abstract class StatusGenericPanel extends JPanel
implements ConfigChangeListener
{
  /**
   * The string to be used as combo separator.
   */
  public static final String COMBO_SEPARATOR = "----------";

  /**
   * The not applicable message.
   */
  protected final static Message NOT_APPLICABLE =
    INFO_NOT_APPLICABLE_LABEL.get();

  private Message AUTHENTICATE = INFO_AUTHENTICATE_BUTTON_LABEL.get();
  private Message START = INFO_START_BUTTON_LABEL.get();

  private ControlPanelInfo info;

  private boolean enableClose = true;
  private boolean enableCancel = true;
  private boolean enableOK = true;

  private boolean disposeOnClose = false;

  private JPanel mainPanel;
  private JLabel message;

  private GenericDialog loginDialog;

  /**
   * The error pane.
   */
  protected JEditorPane errorPane;

  /**
   * The last displayed message in the error pane.
   */
  protected String lastDisplayedError = null;

  private ArrayList<ConfigurationElementCreatedListener> confListeners =
    new ArrayList<ConfigurationElementCreatedListener>();

  private boolean sizeSet = false;
  private boolean focusSet = false;

  /**
   * Returns the title that will be used as title of the dialog.
   * @return the title that will be used as title of the dialog.
   */
  public abstract Message getTitle();

  /**
   * Returns the buttons that the dialog where this panel is contained should
   * display.
   * @return the buttons that the dialog where this panel is contained should
   * display.
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK_CANCEL;
  }

  /**
   * Returns the component that should get the focus when the dialog that
   * contains this panel is displayed.
   * @return the component that should get the focus.
   */
  public abstract Component getPreferredFocusComponent();

  /**
   * Returns <CODE>true</CODE> if this panel requires some bordering (in general
   * an EmptyBorder with some insets) and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this panel requires some bordering (in general
   * an EmptyBorder with some insets) and <CODE>false</CODE> otherwise.
   */
  public boolean requiresBorder()
  {
    return true;
  }

  /**
   * Returns the menu bar that the panel might have.  Returns
   * <CODE>null</CODE> if the panel has no menu bar associated.
   * @return the menu bar that the panel might have.
   */
  public JMenuBar getMenuBar()
  {
    return null;
  }


  /**
   * This method is called to indicate that the configuration changes should
   * be called in the background.  In the case of panels which require some
   * time to be updated with the new configuration this method returns
   * <CODE>true</CODE> and the operation will be performed in the background
   * while a message of type 'Loading...' is displayed on the panel.
   * @return <CODE>true</CODE> if changes should be loaded in the background and
   * <CODE>false</CODE> otherwise.
   */
  public boolean callConfigurationChangedInBackground()
  {
    return false;
  }

  /**
   * The panel is notified that the dialog is going to be visible or invisible.
   * @param visible whether is going to be visible or not.
   */
  public void toBeDisplayed(boolean visible)
  {
  }

  /**
   * Tells whether this panel should be contained in a scroll pane or not.
   * @return <CODE>true</CODE> if this panel should be contained in a scroll
   * pane and <CODE>false</CODE> otherwise.
   */
  public boolean requiresScroll()
  {
    return true;
  }

  /**
   * Constructor.
   *
   */
  protected StatusGenericPanel()
  {
    super(new GridBagLayout());
    setBackground(ColorAndFontConstants.background);

    mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setOpaque(false);

    message = Utilities.createDefaultLabel();

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    super.add(mainPanel, gbc);
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    super.add(message, gbc);
    message.setVisible(false);
  }

  /**
   * The components are not added directly to the panel but to the main panel.
   * This is done to be able to display a message that takes the whole panel
   * (of type 'Loading...') when we are doing long operations.
   * @param comp the Component to be added.
   * @param constraints the constraints.
   */
  public void add(Component comp, Object constraints)
  {
    mainPanel.add(comp, constraints);
  }

  /**
   * Adds a bottom glue to the main panel with the provided constraints.
   * @param gbc the constraints.
   */
  protected void addBottomGlue(GridBagConstraints gbc)
  {
    GridBagConstraints gbc2 = (GridBagConstraints)gbc.clone();
    gbc2.insets = new Insets(0, 0, 0, 0);
    gbc2.gridy ++;
    gbc2.gridwidth = GridBagConstraints.REMAINDER;
    gbc2.weighty = 1.0;
    gbc2.fill = GridBagConstraints.VERTICAL;
    add(Box.createVerticalGlue(), gbc2);
    gbc.gridy ++;
  }

  /**
   * Returns a label with text 'Required Field' and an icon (used as legend in
   * some panels).
   * @return a label with text 'Required Field' and an icon (used as legend in
   * some panels).
   */
  protected JLabel createRequiredLabel()
  {
    JLabel requiredLabel = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_INDICATES_REQUIRED_FIELD_LABEL.get());
    requiredLabel.setIcon(
        Utilities.createImageIcon(IconPool.IMAGE_PATH+"/required.gif"));

    return requiredLabel;
  }

  /**
   * Creates and adds an error pane.  Is up to the caller to set the proper
   * gridheight, gridwidth, gridx and gridy on the provided GridBagConstraints.
   * @param baseGbc the GridBagConstraints to be used.
   */
  protected void addErrorPane(GridBagConstraints baseGbc)
  {
    addErrorPane(this, baseGbc);
  }

  /**
   * Adds an error pane to the provided container.
   * Is up to the caller to set the proper gridheight, gridwidth, gridx and
   * gridy on the provided GridBagConstraints.
   * @param baseGbc the GridBagConstraints to be used.
   * @param p the container.
   */
  protected void addErrorPane(Container p, GridBagConstraints baseGbc)
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = baseGbc.gridx;
    gbc.gridy = baseGbc.gridy;
    gbc.gridwidth = baseGbc.gridwidth;
    gbc.gridheight = baseGbc.gridheight;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    if (requiresBorder())
    {
      gbc.insets = new Insets(0, 0, 10, 0);
    }
    else
    {
      gbc.insets = new Insets(20, 20, 0, 20);
    }
    errorPane = Utilities.makeHtmlPane("", ColorAndFontConstants.progressFont);
    errorPane.setOpaque(false);
    errorPane.setEditable(false);
    errorPane.setVisible(false);
    CustomHTMLEditorKit htmlEditor = new CustomHTMLEditorKit();
    htmlEditor.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        if (AUTHENTICATE.toString().equals(ev.getActionCommand()))
        {
          authenticate();
        }
        else if (START.toString().equals(ev.getActionCommand()))
        {
          startServer();
        }
      }
    });
    errorPane.setEditorKit(htmlEditor);
    p.add(errorPane, gbc);
  }

  /**
   * Commodity method used to add lines, where each line contains a label, a
   * component and an inline help label.
   * @param labels the labels.
   * @param comps the components.
   * @param inlineHelp the inline help labels.
   * @param panel the panel where we will add the lines.
   * @param gbc the grid bag constraints.
   */
  protected void add(JLabel[] labels, Component[] comps, JLabel[] inlineHelp,
      Container panel, GridBagConstraints gbc)
  {
    int i = 0;
    for (Component comp : comps)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      if (labels[i] != null)
      {
        panel.add(labels[i], gbc);
      }
      gbc.insets.left = 10;
      gbc.weightx = 1.0;
      gbc.gridx = 1;
      panel.add(comp, gbc);
      if (inlineHelp[i] != null)
      {
        gbc.insets.top = 3;
        gbc.gridy ++;
        panel.add(inlineHelp[i], gbc);
      }
      gbc.insets.top = 10;
      gbc.gridy ++;
      i++;
    }
  }

  /**
   * Enables the OK button in the parent dialog.
   * @param enable whether to enable or disable the button.
   */
  protected void setEnabledOK(boolean enable)
  {
    Window parent = Utilities.getParentDialog(this);
    if ((parent != null) && (parent instanceof GenericDialog))
    {
      ((GenericDialog)parent).setEnabledOK(enable);
    }
    enableOK = enable;
  }

  /**
   * Enables the Cancel button in the parent dialog.
   * @param enable whether to enable or disable the button.
   */
  protected void setEnabledCancel(boolean enable)
  {
    JDialog parent = (JDialog)Utilities.getParentDialog(this);
    if ((parent != null) && (parent instanceof GenericDialog))
    {
      ((GenericDialog)parent).setEnabledCancel(enable);
    }
    enableCancel = enable;
  }

  /**
   * Updates the font type and color of the component to be invalid and
   * primary.
   * @param comp the component to update.
   */
  protected void setPrimaryInvalid(JComponent comp)
  {
    comp.setFont(ColorAndFontConstants.primaryInvalidFont);
    comp.setForeground(ColorAndFontConstants.invalidFontColor);
  }

  /**
   * Updates the font type and color of the component to be valid and
   * primary.
   * @param comp the component to update.
   */
  protected void setPrimaryValid(JComponent comp)
  {
    comp.setForeground(ColorAndFontConstants.validFontColor);
    comp.setFont(ColorAndFontConstants.primaryFont);
  }

  /**
   * Updates the font type and color of the component to be invalid and
   * secondary.
   * @param comp the component to update.
   */
  protected void setSecondaryInvalid(JComponent comp)
  {
    comp.setForeground(ColorAndFontConstants.invalidFontColor);
    comp.setFont(ColorAndFontConstants.invalidFont);
  }

  /**
   * Updates the font type and color of the component to be valid and
   * secondary.
   * @param comp the component to update.
   */
  protected void setSecondaryValid(JComponent comp)
  {
    comp.setForeground(ColorAndFontConstants.validFontColor);
    comp.setFont(ColorAndFontConstants.defaultFont);
  }

  /**
   * Packs the parent dialog.
   *
   */
  protected void packParentDialog()
  {
    Window dlg = Utilities.getParentDialog(this);
    if (dlg != null)
    {
      invalidate();
      dlg.invalidate();
      dlg.pack();
      if (!SwingUtilities.isEventDispatchThread())
      {
        Thread.dumpStack();
      }
    }
  }

  /**
   * Notification that the ok button has been clicked, the panel is in charge
   * of doing whatever is required (close the dialog, launch a task, etc.).
   *
   */
  abstract public void okClicked();

  /**
   * Adds a configuration element created listener.
   * @param listener the listener.
   */
  public void addConfigurationElementCreatedListener(
      ConfigurationElementCreatedListener listener)
  {
    confListeners.add(listener);
  }

  /**
   * Removes a configuration element created listener.
   * @param listener the listener.
   */
  public void removeConfigurationElementCreatedListener(
      ConfigurationElementCreatedListener listener)
  {
    confListeners.remove(listener);
  }

  /**
   * Notifies the configuraton element created listener that a new object has
   * been created.
   * @param configObject the created object.
   */
  protected void notifyConfigurationElementCreated(Object configObject)
  {
    for (ConfigurationElementCreatedListener listener : confListeners)
    {
      listener.elementCreated(
          new ConfigurationElementCreatedEvent(this, configObject));
    }
  }

  /**
   * Notification that cancel was clicked, the panel is in charge
   * of doing whatever is required (close the dialog, etc.).
   *
   */
  public void cancelClicked()
  {
    // Default implementation
    Utilities.getParentDialog(this).setVisible(false);
    if (isDisposeOnClose())
    {
      Utilities.getParentDialog(this).dispose();
    }
  }

  /**
   * Whether the dialog should be disposed when the user closes it.
   * @return <CODE>true</CODE> if the dialog should be disposed when the user
   * closes it or <CODE>true</CODE> otherwise.
   */
  public boolean isDisposeOnClose()
  {
    return disposeOnClose;
  }

  /**
   * Sets whether the dialog should be disposed when the user closes it or not.
   * @param disposeOnClose <CODE>true</CODE> if the dialog should be disposed
   * when the user closes it or <CODE>true</CODE> otherwise.
   */
  public void setDisposeOnClose(boolean disposeOnClose)
  {
    this.disposeOnClose = disposeOnClose;
  }

  /**
   * Notification that close was clicked, the panel is in charge
   * of doing whatever is required (close the dialog, etc.).
   *
   */
  public void closeClicked()
  {
    // Default implementation
    Utilities.getParentDialog(this).setVisible(false);
    if (isDisposeOnClose())
    {
      Utilities.getParentDialog(this).dispose();
    }
  }

  /**
   * Displays a dialog with the provided list of error messages.
   * @param errors the error messages.
   */
  protected void displayErrorDialog(Collection<Message> errors)
  {
    Utilities.displayErrorDialog(Utilities.getParentDialog(this), errors);
  }

  /**
   * Displays a confirmation message.
   * @param title the title/summary of the message.
   * @param msg the description of the confirmation.
   * @return <CODE>true</CODE> if the user confirms and <CODE>false</CODE>
   * otherwise.
   */
  protected boolean displayConfirmationDialog(Message title, Message msg)
  {
    return Utilities.displayConfirmationDialog(Utilities.getParentDialog(this),
        title, msg);
  }


  /**
   * If the index must be rebuilt, asks the user for confirmation.  If the user
   * confirms launches a task that will rebuild the indexes.  The progress will
   * be displayed in the provided progress dialog.
   * @param index the index.
   * @param progressDialog the progress dialog.
   */
  protected void rebuildIndexIfNecessary(AbstractIndexDescriptor index,
      ProgressDialog progressDialog)
  {
    progressDialog.setTaskIsOver(false);
    boolean rebuildIndexes;
    String backendName = index.getBackend().getBackendID();
    if (!isServerRunning())
    {
      rebuildIndexes = Utilities.displayConfirmationDialog(progressDialog,
          INFO_CTRL_PANEL_INDEX_REBUILD_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_INDEX_REBUILD_REQUIRED_OFFLINE_DETAILS.get(
              index.getName(), backendName));
    }
    else
    {
      rebuildIndexes = Utilities.displayConfirmationDialog(progressDialog,
          INFO_CTRL_PANEL_INDEX_REBUILD_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_INDEX_REBUILD_REQUIRED_ONLINE_DETAILS.get(
              index.getName(), backendName, backendName));
    }
    if (rebuildIndexes)
    {
      SortedSet<AbstractIndexDescriptor> indexes =
        new TreeSet<AbstractIndexDescriptor>();
      indexes.add(index);
      SortedSet<String> baseDNs = new TreeSet<String>();
      for (BaseDNDescriptor b : index.getBackend().getBaseDns())
      {
        String baseDN = Utilities.unescapeUtf8(b.getDn().toString());
        baseDNs.add(baseDN);
      }

      RebuildIndexTask newTask = new RebuildIndexTask(getInfo(),
          progressDialog, baseDNs, indexes);
      ArrayList<Message> errors = new ArrayList<Message>();
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.size() == 0)
      {
        progressDialog.appendProgressHtml("<br><br>");
        launchOperation(newTask,
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUMMARY.get(backendName),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_DETAILS,
            progressDialog, false);
        progressDialog.toFront();
        progressDialog.setVisible(true);
      }
      if (errors.size() > 0)
      {
        displayErrorDialog(errors);
      }
    }
    else
    {
      progressDialog.setTaskIsOver(true);
      if (progressDialog.isVisible())
      {
        progressDialog.toFront();
      }
    }
  }


  /**
   * A class used to avoid the possibility a certain type of objects in a combo
   * box.  This is used for instance in the combo box that contains base DNs
   * where the base DNs are separated in backends, so the combo box displays
   * both the backends (~ categories) and base DNs (~ values) and we do not
   * allow to select the backends (~ categories).
   *
   */
  protected class IgnoreItemListener implements ItemListener
  {
    private Object selectedItem;
    private JComboBox combo;

    /**
     * Constructor.
     * @param combo the combo box.
     */
    public IgnoreItemListener(JComboBox combo)
    {
      this.combo = combo;
      selectedItem = combo.getSelectedItem();
      if (isCategory(selectedItem))
      {
        selectedItem = null;
      }
    }

    /**
     * {@inheritDoc}
     */
    public void itemStateChanged(ItemEvent ev)
    {
      Object o = combo.getSelectedItem();
      if (isCategory(o))
      {
        if (selectedItem == null)
        {
          // Look for the first element that is not a category
          for (int i=0; i<combo.getModel().getSize(); i++)
          {
            Object item = combo.getModel().getElementAt(i);
            if (item instanceof CategorizedComboBoxElement)
            {
              if (!isCategory(item))
              {
                selectedItem = item;
                break;
              }
            }
          }
        }
        if (selectedItem != null)
        {
          combo.setSelectedItem(selectedItem);
        }
      }
      else if (COMBO_SEPARATOR.equals(o))
      {
        combo.setSelectedItem(selectedItem);
      }
      else
      {
        selectedItem = o;
      }
    }
  }

  /**
   * Returns the HTML required to render an Authenticate button in HTML.
   * @return the HTML required to render an Authenticate button in HTML.
   */
  protected String getAuthenticateHTML()
  {
    return "<INPUT type=\"submit\" value=\""+AUTHENTICATE+"\"></INPUT>";
  }

  /**
   * Returns the HTML required to render an Start button in HTML.
   * @return the HTML required to render an Start button in HTML.
   */
  protected String getStartServerHTML()
  {
    return "<INPUT type=\"submit\" value=\""+START+"\"></INPUT>";
  }

  /**
   * Updates the error panel and enables/disables the OK button depending on
   * the status of the server.
   * @param desc the Server Descriptor.
   * @param details the message to be displayed if authentication has not been
   * provided and the server is running.
   */
  protected void updateErrorPaneAndOKButtonIfAuthRequired(ServerDescriptor desc,
      Message details)
  {
    if (authenticationRequired(desc))
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(details);
      mb.append("<br><br>"+getAuthenticateHTML());
      Message title = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();
      updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
          mb.toMessage(), ColorAndFontConstants.defaultFont);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(true);
          packParentDialog();
          setEnabledOK(false);
        }
      });
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(false);
          checkOKButtonEnable();
        }
      });
    }
  }

  /**
   * Returns <CODE>true</CODE> if the server is running and the user did not
   * provide authentication and <CODE>false</CODE> otherwise.
   * @param desc the server descriptor.
   * @return <CODE>true</CODE> if the server is running and the user did not
   * provide authentication and <CODE>false</CODE> otherwise.
   */
  protected boolean authenticationRequired(ServerDescriptor desc)
  {
    boolean returnValue;
    ServerDescriptor.ServerStatus status = desc.getStatus();
    if ((status == ServerDescriptor.ServerStatus.STARTED) &&
        !desc.isAuthenticated())
    {
      returnValue = true;
    }
    else
    {
      returnValue = false;
    }
    return returnValue;
  }

  /**
   * Updates the error panel depending on the status of the server.
   * @param desc the Server Descriptor.
   * @param details the message to be displayed if authentication has not been
   * provided and the server is running.
   */
  protected void updateErrorPaneIfAuthRequired(ServerDescriptor desc,
      Message details)
  {
    if (authenticationRequired(desc))
    {
      Message title = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(details);
      mb.append("<br><br>"+getAuthenticateHTML());
      updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
          mb.toMessage(), ColorAndFontConstants.defaultFont);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(true);
          packParentDialog();
        }
      });
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(false);
        }
      });
    }
  }

  /**
   * Updates the error panel depending on the status of the server.  This
   * method will display an error message in the error pane if the server is not
   * running and another message if the server is running but authentication
   * has not been provided.
   * @param desc the Server Descriptor.
   * @param detailsServerNotRunning the message to be displayed if the server is
   * not running.
   * @param authRequired the message to be displayed if authentication has not
   * been provided and the server is running.
   */
  protected void updateErrorPaneIfServerRunningAndAuthRequired(
      ServerDescriptor desc, Message detailsServerNotRunning,
      Message authRequired)
  {
    ServerDescriptor.ServerStatus status = desc.getStatus();
    if (status != ServerDescriptor.ServerStatus.STARTED)
    {
      Message title = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(detailsServerNotRunning);
      mb.append("<br><br>"+getStartServerHTML());
      updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
          mb.toMessage(),
          ColorAndFontConstants.defaultFont);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(true);
          packParentDialog();
        }
      });
    }
    else if (authenticationRequired(desc))
    {
      Message title = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(authRequired);
      mb.append("<br><br>"+getAuthenticateHTML());
      updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
          mb.toMessage(), ColorAndFontConstants.defaultFont);
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(true);
          packParentDialog();
        }
      });
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          errorPane.setVisible(false);
        }
      });
    }
  }

  /**
   * Updates the enabling/disabling of the OK button.  The code assumes that
   * the error pane has already been updated.
   *
   */
  protected void checkOKButtonEnable()
  {
    setEnabledOK(!errorPane.isVisible());
  }

  /**
   * Returns <CODE>true</CODE> if the provided object is a category object in
   * a combo box.
   * @param o the item in the combo box.
   * @return <CODE>true</CODE> if the provided object is a category object in
   * a combo box.
   */
  protected boolean isCategory(Object o)
  {
    boolean isCategory = false;
    if (o instanceof CategorizedComboBoxElement)
    {
      CategorizedComboBoxElement desc = (CategorizedComboBoxElement)o;
      isCategory = desc.getType() == CategorizedComboBoxElement.Type.CATEGORY;
    }
    return isCategory;
  }

  /**
   * Returns the control panel info object.
   * @return the control panel info object.
   */
  public ControlPanelInfo getInfo()
  {
    return info;
  }

  /**
   * Sets the control panel info object.
   * @param info the control panel info object.
   */
  public void setInfo(ControlPanelInfo info)
  {
    if (!info.equals(this.info))
    {
      if (this.info != null)
      {
        this.info.removeConfigChangeListener(this);
      }
      this.info = info;
      this.info.addConfigChangeListener(this);
      if (SwingUtilities.isEventDispatchThread() &&
          callConfigurationChangedInBackground())
      {
        final Color savedBackground = getBackground();
        setBackground(ColorAndFontConstants.background);
        if (!sizeSet)
        {
          setPreferredSize(mainPanel.getPreferredSize());
          sizeSet = true;
        }
        // Do it outside the event thread if the panel requires it.
        BackgroundTask<Void> worker = new BackgroundTask<Void>()
        {
          public Void processBackgroundTask() throws Throwable
          {
            try
            {
              Thread.sleep(1000);
            }
            catch (Throwable t)
            {
            }
            configurationChanged(new ConfigurationChangeEvent(
                StatusGenericPanel.this.info,
                StatusGenericPanel.this.info.getServerDescriptor()));
            return null;
          }


          public void backgroundTaskCompleted(Void returnValue,
              Throwable t)
          {
            setBackground(savedBackground);
            displayMainPanel();
            if (!focusSet)
            {
              focusSet = true;
              Component comp = getPreferredFocusComponent();
              if (comp != null)
              {
                comp.requestFocusInWindow();
              }
            }
          }
        };
        displayMessage(INFO_CTRL_PANEL_LOADING_PANEL_SUMMARY.get());
        worker.startBackgroundTask();
      }
      else
      {
        configurationChanged(new ConfigurationChangeEvent(
          this.info, this.info.getServerDescriptor()));
      }
    }
  }

  /**
   * Displays the main panel.
   *
   */
  protected void displayMainPanel()
  {
    mainPanel.setVisible(true);
    message.setVisible(false);
  }

  /**
   * Displays a message and hides the main panel.
   * @param msg the message to be displayed.
   */
  protected void displayMessage(Message msg)
  {
    message.setText(msg.toString());
    mainPanel.setVisible(false);
    message.setVisible(true);
  }

  /**
   * Updates the contents of an editor pane using the error format.
   * @param pane the editor pane to be updated.
   * @param title the title.
   * @param titleFont the font to be used for the title.
   * @param details the details message.
   * @param detailsFont the font to be used for the details.
   */
  protected void updateErrorPane(JEditorPane pane, Message title,
      Font titleFont, Message details, Font detailsFont)
  {
    updatePane(pane, title, titleFont, details, detailsFont, PanelType.ERROR);
  }

  /**
   * Updates the contents of an editor pane using the warning format.
   * @param pane the editor pane to be updated.
   * @param title the title.
   * @param titleFont the font to be used for the title.
   * @param details the details message.
   * @param detailsFont the font to be used for the details.
   */
  protected void updateWarningPane(JEditorPane pane, Message title,
      Font titleFont, Message details, Font detailsFont)
  {
    updatePane(pane, title, titleFont, details, detailsFont, PanelType.WARNING);
  }

  /**
   * Updates the contents of an editor pane using the confirmation format.
   * @param pane the editor pane to be updated.
   * @param title the title.
   * @param titleFont the font to be used for the title.
   * @param details the details message.
   * @param detailsFont the font to be used for the details.
   */
  protected void updateConfirmationPane(JEditorPane pane, Message title,
      Font titleFont, Message details, Font detailsFont)
  {
    updatePane(pane, title, titleFont, details, detailsFont,
        PanelType.CONFIRMATION);
  }

  /**
   * The different types of error panels that are handled.
   *
   */
  protected enum PanelType
  {
    /**
     * The message in the panel is an error.
     */
    ERROR,
    /**
     * The message in the panel is a confirmation.
     */
    CONFIRMATION,
    /**
     * The message in the panel is an information message.
     */
    INFORMATION,
    /**
     * The message in the panel is a warning message.
     */
    WARNING
  };

  /**
   * Updates the contents of an editor pane using the provided format.
   * @param pane the editor pane to be updated.
   * @param title the title.
   * @param titleFont the font to be used for the title.
   * @param details the details message.
   * @param detailsFont the font to be used for the details.
   * @param type the type of panel.
   */
  private void updatePane(JEditorPane pane, Message title,
      Font titleFont, Message details, Font detailsFont, PanelType type)
  {
    String text;
    switch (type)
    {
    case ERROR:
      text = Utilities.getFormattedError(title, titleFont, details,
          detailsFont);
      break;
    case CONFIRMATION:
      text = Utilities.getFormattedConfirmation(title, titleFont, details,
          detailsFont);
      break;
    case WARNING:
      text = Utilities.getFormattedWarning(title, titleFont, details,
          detailsFont);
      break;
    default:
      text = Utilities.getFormattedSuccess(title, titleFont, details,
          detailsFont);
      break;
    }
    if (!text.equals(lastDisplayedError))
    {
      JEditorPane pane1 = Utilities.makeHtmlPane(null, pane.getFont());
      String text1;
      switch (type)
      {
      case ERROR:
        text1 = Utilities.getFormattedError(title, titleFont,
            null, detailsFont);
        break;
      default:
        text1 = Utilities.getFormattedSuccess(title, titleFont,
            null, detailsFont);
        break;
      }
      pane1.setText(text1);
      Dimension d1 = pane1.getPreferredSize();
      JEditorPane pane2 = Utilities.makeHtmlPane(null, pane.getFont());
      pane2.setText(details.toString());
      String plainText = details.toString().replaceAll("<br>",
          ServerConstants.EOL);
      Utilities.updatePreferredSize(pane2, 100, plainText, detailsFont, true);
      Dimension d2 = pane2.getPreferredSize();
      pane.setPreferredSize(new Dimension(Math.max(d1.width, d2.width),
          d1.height + d2.height));

      lastDisplayedError = text;
      pane.setText(text);
    }
    final Window window =
      Utilities.getParentDialog(StatusGenericPanel.this);
    if (window != null)
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          window.validate();
        }
      });
    }
  }

  /**
   * Commodity method used to update the elements of a combo box that contains
   * the different user backends.  If no backends are found the combo box will
   * be made invisible and a label will be made visible.  This method does not
   * update the label's text nor creates any layout.
   * @param combo the combo to be updated.
   * @param lNoBackendsFound the label that must be shown if no user backends
   * are found.
   * @param desc the server descriptor that contains the configuration.
   */
  protected void updateSimpleBackendComboBoxModel(final JComboBox combo,
      final JLabel lNoBackendsFound, ServerDescriptor desc)
  {
    final SortedSet<String> newElements = new TreeSet<String>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (!backend.isConfigBackend())
      {
        newElements.add(backend.getBackendID());
      }
    }
    DefaultComboBoxModel model = (DefaultComboBoxModel)combo.getModel();
    updateComboBoxModel(newElements, model);
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        combo.setVisible(newElements.size() > 0);
        lNoBackendsFound.setVisible(newElements.size() == 0);
      }
    });
  }

  /**
   * Method that says if a backend must be displayed.  Only non-config backends
   * are displayed.
   * @param backend the backend.
   * @return <CODE>true</CODE> if the backend must be displayed and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean displayBackend(BackendDescriptor backend)
  {
    return !backend.isConfigBackend();
  }

  /**
   * Commodity method to update a combo box model with the backends of a server.
   * @param model the combo box model to be updated.
   * @param desc the server descriptor containing the configuration.
   */
  protected void updateBaseDNComboBoxModel(DefaultComboBoxModel model,
      ServerDescriptor desc)
  {
    LinkedHashSet<CategorizedComboBoxElement> newElements =
      new LinkedHashSet<CategorizedComboBoxElement>();
    SortedSet<String> backendIDs = new TreeSet<String>();
    HashMap<String, SortedSet<String>> hmBaseDNs =
      new HashMap<String, SortedSet<String>>();

    for (BackendDescriptor backend : desc.getBackends())
    {
      if (displayBackend(backend))
      {
        String backendID = backend.getBackendID();
        backendIDs.add(backendID);
        SortedSet<String> baseDNs = new TreeSet<String>();
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            baseDNs.add(Utilities.unescapeUtf8(baseDN.getDn().toString()));
          }
          catch (Throwable t)
          {
            throw new IllegalStateException("Unexpected error: "+t, t);
          }
        }
        hmBaseDNs.put(backendID, baseDNs);
      }
    }

    for (String backendID : backendIDs)
    {
      newElements.add(new CategorizedComboBoxElement(backendID,
          CategorizedComboBoxElement.Type.CATEGORY));
      SortedSet<String> baseDNs = hmBaseDNs.get(backendID);
      for (String baseDN : baseDNs)
      {
        newElements.add(new CategorizedComboBoxElement(baseDN,
            CategorizedComboBoxElement.Type.REGULAR));
      }
    }
    updateComboBoxModel(newElements, model);
  }

  /**
   * Updates a combo box model with a number of items.
   * @param newElements the new items for the combo box model.
   * @param model the combo box model to be updated.
   */
  protected void updateComboBoxModel(final Collection<?> newElements,
      final DefaultComboBoxModel model)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        boolean changed = newElements.size() != model.getSize();
        if (!changed)
        {
          int i = 0;
          for (Object newElement : newElements)
          {
            changed = !newElement.equals(model.getElementAt(i));
            if (changed)
            {
              break;
            }
            i++;
          }
        }
        if (changed)
        {
          Object selected = model.getSelectedItem();
          model.removeAllElements();
          boolean selectDefault = false;
          for (Object newElement : newElements)
          {
            model.addElement(newElement);
          }
          if (selected != null)
          {
            if (model.getIndexOf(selected) != -1)
            {
              model.setSelectedItem(selected);
            }
            else
            {
              selectDefault = true;
            }
          }
          else
          {
            selectDefault = true;
          }
          if (selectDefault)
          {
            for (int i=0; i<model.getSize(); i++)
            {
              Object o = model.getElementAt(i);
              if (o instanceof CategorizedComboBoxElement)
              {
                if (((CategorizedComboBoxElement)o).getType() ==
                  CategorizedComboBoxElement.Type.CATEGORY)
                {
                  continue;
                }
              }
              model.setSelectedItem(o);
              break;
            }
          }
        }
      }
    });
  }

  /**
   * Updates a map, so that the keys are the base DN where the indexes are
   * defined and the values are a sorted set of indexes.
   * @param desc the server descriptor containing the index configuration.
   * @param hmIndexes the map to be updated.
   */
  protected void updateIndexMap(ServerDescriptor desc,
      HashMap<String, SortedSet<AbstractIndexDescriptor>> hmIndexes)
  {
    synchronized (hmIndexes)
    {
      HashSet<String> dns = new HashSet<String>();
      for (BackendDescriptor backend : desc.getBackends())
      {
        if (backend.getType() == BackendDescriptor.Type.LOCAL_DB)
        {
          for (BaseDNDescriptor baseDN : backend.getBaseDns())
          {
            String dn;
            try
            {
              dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
            }
            catch (Throwable t)
            {
              throw new IllegalStateException("Unexpected error: "+t, t);
            }
            dns.add(dn);
            SortedSet<AbstractIndexDescriptor> indexes =
              new TreeSet<AbstractIndexDescriptor>();
            indexes.addAll(backend.getIndexes());
            indexes.addAll(backend.getVLVIndexes());
            SortedSet<AbstractIndexDescriptor> currentIndexes =
              hmIndexes.get(dn);
            if (currentIndexes != null)
            {
              if (!currentIndexes.equals(indexes))
              {
                hmIndexes.put(dn, indexes);
              }
            }
            else
            {
              hmIndexes.put(dn, indexes);
            }
          }
        }
      }
      for (String dn : new HashSet<String>(hmIndexes.keySet()))
      {
        if (!dns.contains(dn))
        {
          hmIndexes.remove(dn);
        }
      }
    }
  }



  /**
   * Updates and addremove panel with the contents of the provided item.  The
   * selected item represents a base DN.
   * @param hmIndexes the map that contains the indexes definitions as values
   * and the base DNs as keys.
   * @param selectedItem the selected item.
   * @param addRemove the add remove panel to be updated.
   */
  protected void comboBoxSelected(
      HashMap<String, SortedSet<AbstractIndexDescriptor>> hmIndexes,
      CategorizedComboBoxElement selectedItem,
      AddRemovePanel<AbstractIndexDescriptor> addRemove)
  {
    synchronized (hmIndexes)
    {
      String selectedDn = null;
      if (selectedItem != null)
      {
        selectedDn = (String)selectedItem.getValue();
      }
      if (selectedDn != null)
      {
        SortedSet<AbstractIndexDescriptor> indexes =
          hmIndexes.get(selectedDn);
        if (indexes != null)
        {
          boolean availableChanged = false;
          boolean selectedChanged = false;
          SortedSet<AbstractIndexDescriptor> availableIndexes =
            addRemove.getAvailableListModel().getData();
          SortedSet<AbstractIndexDescriptor> selectedIndexes =
            addRemove.getSelectedListModel().getData();
          availableChanged = availableIndexes.retainAll(indexes);
          selectedChanged = selectedIndexes.retainAll(indexes);

          for (AbstractIndexDescriptor index : indexes)
          {
            if (!availableIndexes.contains(index) &&
                !selectedIndexes.contains(index))
            {
              availableIndexes.add(index);
              availableChanged = true;
            }
          }
          if (availableChanged)
          {
            addRemove.getAvailableListModel().clear();
            addRemove.getAvailableListModel().addAll(availableIndexes);
            addRemove.getAvailableListModel().fireContentsChanged(
                addRemove.getAvailableListModel(), 0,
                addRemove.getAvailableListModel().getSize());
          }
          if (selectedChanged)
          {
            addRemove.getSelectedListModel().clear();
            addRemove.getSelectedListModel().addAll(selectedIndexes);
            addRemove.getSelectedListModel().fireContentsChanged(
                addRemove.getSelectedListModel(), 0,
                addRemove.getSelectedListModel().getSize());
          }
        }
      }
    }
  }

  /**
   * Returns <CODE>true</CODE> if the cancel button is enabled and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the cancel button is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isEnableCancel()
  {
    return enableCancel;
  }

  /**
   * Returns <CODE>true</CODE> if the close button is enabled and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the close button is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isEnableClose()
  {
    return enableClose;
  }

  /**
   * Returns <CODE>true</CODE> if the ok button is enabled and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the ok button is enabled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isEnableOK()
  {
    return enableOK;
  }

  /**
   * Returns <CODE>true</CODE> if the server is running  and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the server is running  and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean isServerRunning()
  {
    return getInfo().getServerDescriptor().getStatus() ==
      ServerDescriptor.ServerStatus.STARTED;
  }

  /**
   * Launch an task.
   * @param task the task to be launched.
   * @param initialSummary the initial summary to be displayed in the progress
   * dialog.
   * @param successSummary the success summary to be displayed in the progress
   * dialog if the task is successful.
   * @param successDetail the success details to be displayed in the progress
   * dialog if the task is successful.
   * @param errorSummary the error summary to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetail error details to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetailCode error detail message to be displayed in the progress
   * dialog if the task ended with error and we have an exit error code (for
   * instance if the error occurred when launching a script we will have an
   * error code).
   * @param dialog the progress dialog.
   */
  protected void launchOperation(final Task task, Message initialSummary,
      final Message successSummary, final Message successDetail,
      final Message errorSummary,
      final Message errorDetail,
      final MessageDescriptor.Arg1<Number> errorDetailCode,
      final ProgressDialog dialog)
  {
    launchOperation(task, initialSummary, successSummary, successDetail,
        errorSummary, errorDetail, errorDetailCode, dialog, true);
  }

  /**
   * Launch an task.
   * @param task the task to be launched.
   * @param initialSummary the initial summary to be displayed in the progress
   * dialog.
   * @param successSummary the success summary to be displayed in the progress
   * dialog if the task is successful.
   * @param successDetail the success details to be displayed in the progress
   * dialog if the task is successful.
   * @param errorSummary the error summary to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetail error details to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetailCode error detail message to be displayed in the progress
   * dialog if the task ended with error and we have an exit error code (for
   * instance if the error occurred when launching a script we will have an
   * error code).
   * @param dialog the progress dialog.
   * @param resetLogs whether the contents of the progress dialog should be
   * reset or not.
   */
  protected void launchOperation(final Task task, Message initialSummary,
      final Message successSummary, final Message successDetail,
      final Message errorSummary,
      final Message errorDetail,
      final MessageDescriptor.Arg1<Number> errorDetailCode,
      final ProgressDialog dialog, boolean resetLogs)
  {
    launchOperation(task, initialSummary, successSummary, successDetail,
        errorSummary, errorDetail, errorDetailCode, dialog, resetLogs,
        getInfo());
  }

  /**
   * Launch an task.
   * @param task the task to be launched.
   * @param initialSummary the initial summary to be displayed in the progress
   * dialog.
   * @param successSummary the success summary to be displayed in the progress
   * dialog if the task is successful.
   * @param successDetail the success details to be displayed in the progress
   * dialog if the task is successful.
   * @param errorSummary the error summary to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetail error details to be displayed in the progress
   * dialog if the task ended with error.
   * @param errorDetailCode error detail message to be displayed in the progress
   * dialog if the task ended with error and we have an exit error code (for
   * instance if the error occurred when launching a script we will have an
   * error code).
   * @param dialog the progress dialog.
   * @param resetLogs whether the contents of the progress dialog should be
   * reset or not.
   * @param info the ControlPanelInfo.
   */
  public static void launchOperation(final Task task, Message initialSummary,
      final Message successSummary, final Message successDetail,
      final Message errorSummary,
      final Message errorDetail,
      final MessageDescriptor.Arg1<Number> errorDetailCode,
      final ProgressDialog dialog, boolean resetLogs,
      final ControlPanelInfo info)
  {
    dialog.setTaskIsOver(false);
    dialog.getProgressBar().setIndeterminate(true);
    dialog.addPrintStreamListeners(task.getOutPrintStream(),
        task.getErrorPrintStream());
    if (resetLogs)
    {
      dialog.resetProgressLogs();
    }
    String cmdLine = task.getCommandLineToDisplay();
    if (cmdLine != null)
    {
      dialog.appendProgressHtml(Utilities.applyFont(
          INFO_CTRL_PANEL_EQUIVALENT_COMMAND_LINE.get()+"<br><b>"+cmdLine+
          "</b><br><br>",
          ColorAndFontConstants.progressFont));
    }
    dialog.setEnabledClose(false);
    dialog.setSummary(Message.raw(
        Utilities.applyFont(initialSummary.toString(),
            ColorAndFontConstants.defaultFont)));
    dialog.getProgressBar().setVisible(true);
    BackgroundTask<Task> worker = new BackgroundTask<Task>()
    {
      /**
       * {@inheritDoc}
       */
      public Task processBackgroundTask() throws Throwable
      {
        task.runTask();
        if (task.regenerateDescriptor())
        {
          info.regenerateDescriptor();
        }
        return task;
      }

      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Task returnValue, Throwable t)
      {
        String summaryMsg;
        if (task.getState() == Task.State.FINISHED_SUCCESSFULLY)
        {
          summaryMsg = Utilities.getFormattedSuccess(successSummary,
              ColorAndFontConstants.errorTitleFont,
              successDetail, ColorAndFontConstants.defaultFont);
        }
        else
        {
          if (t == null)
          {
            t = task.getLastException();
          }

          if (t != null)
          {
            if ((task.getReturnCode() != null) &&
                (errorDetailCode != null))
            {
              MessageBuilder mb = new MessageBuilder();
              mb.append(errorDetailCode.get(task.getReturnCode()));
              mb.append(
                  ".  "+INFO_CTRL_PANEL_DETAILS_THROWABLE.get(t.toString()));
              summaryMsg = Utilities.getFormattedError(errorSummary,
                  ColorAndFontConstants.errorTitleFont,
                  mb.toMessage(), ColorAndFontConstants.defaultFont);
            }
            else if (errorDetail != null)
            {
              MessageBuilder mb = new MessageBuilder();
              mb.append(errorDetail);
              mb.append(
                  ".  "+INFO_CTRL_PANEL_DETAILS_THROWABLE.get(t.toString()));
              summaryMsg = Utilities.getFormattedError(errorSummary,
                  ColorAndFontConstants.errorTitleFont,
                  mb.toMessage(), ColorAndFontConstants.defaultFont);
            }
            else
            {
              summaryMsg = null;
            }
          }
          else if ((task.getReturnCode() != null) &&
              (errorDetailCode != null))
          {
            summaryMsg = Utilities.getFormattedError(errorSummary,
                ColorAndFontConstants.errorTitleFont,
                errorDetailCode.get(task.getReturnCode()),
                ColorAndFontConstants.defaultFont);
          }
          else if (errorDetail != null)
          {
            summaryMsg = Utilities.getFormattedError(errorSummary,
                ColorAndFontConstants.errorTitleFont,
                errorDetail, ColorAndFontConstants.defaultFont);
          }
          else
          {
            summaryMsg = null;
          }
        }
        if (summaryMsg != null)
        {
          dialog.setSummary(Message.raw(summaryMsg));
        }
        dialog.setEnabledClose(true);
        dialog.getProgressBar().setVisible(false);
        if (task.getState() == Task.State.FINISHED_SUCCESSFULLY)
        {
          dialog.setTaskIsOver(true);
        }
        task.postOperation();
      }
    };
    info.registerTask(task);
    worker.startBackgroundTask();
  }

  /**
   * Checks that the provided string value is a valid integer and if it is not
   * updates a list of error messages with an error.
   * @param errors the list of error messages to be updated.
   * @param stringValue the string value to analyze.
   * @param minValue the minimum integer value accepted.
   * @param maxValue the maximum integer value accepted.
   * @param errMsg the error message to use to update the error list if the
   * provided value is not valid.
   */
  protected void checkIntValue(Collection<Message> errors, String stringValue,
      int minValue, int maxValue, Message errMsg)
  {
    try
    {
      int n = Integer.parseInt(stringValue);
      if ((n > maxValue) || (n < minValue))
      {
        throw new IllegalStateException("Invalid value");
      }
    }
    catch (Throwable t)
    {
      errors.add(errMsg);
    }
  }

  /**
   * Starts the server.  This method will launch a task and open a progress
   * dialog that will start the server.  This method must be called from the
   * event thread.
   *
   */
  protected void startServer()
  {
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_START_SERVER_PROGRESS_DLG_TITLE.get(), getInfo());
    StartServerTask newTask = new StartServerTask(getInfo(), progressDialog);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.size() == 0)
    {
      launchOperation(newTask,
          INFO_CTRL_PANEL_STARTING_SERVER_SUMMARY.get(),
          INFO_CTRL_PANEL_STARTING_SERVER_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_STARTING_SERVER_SUCCESSFUL_DETAILS.get(),
          ERR_CTRL_PANEL_STARTING_SERVER_ERROR_SUMMARY.get(),
          null,
          ERR_CTRL_PANEL_STARTING_SERVER_ERROR_DETAILS,
          progressDialog);
      progressDialog.setVisible(true);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Stops the server.  This method will launch a task and open a progress
   * dialog that will stop the server.  This method must be called from the
   * event thread.
   *
   */
  protected void stopServer()
  {
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_STOP_SERVER_PROGRESS_DLG_TITLE.get(), getInfo());
    StopServerTask newTask = new StopServerTask(getInfo(), progressDialog);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    boolean confirmed = true;
    if (errors.size() == 0)
    {
      confirmed = displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_CONFIRM_STOP_SERVER_DETAILS.get());
    }
    if ((errors.size() == 0) && confirmed)
    {
      launchOperation(newTask,
          INFO_CTRL_PANEL_STOPPING_SERVER_SUMMARY.get(),
          INFO_CTRL_PANEL_STOPPING_SERVER_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_STOPPING_SERVER_SUCCESSFUL_DETAILS.get(),
          ERR_CTRL_PANEL_STOPPING_SERVER_ERROR_SUMMARY.get(),
          null,
          ERR_CTRL_PANEL_STOPPING_SERVER_ERROR_DETAILS,
          progressDialog);
      progressDialog.setVisible(true);
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Restarts the server.  This method will launch a task and open a progress
   * dialog that will restart the server.  This method must be called from the
   * event thread.
   *
   */
  protected void restartServer()
  {
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_RESTART_SERVER_PROGRESS_DLG_TITLE.get(), getInfo());
    RestartServerTask newTask = new RestartServerTask(getInfo(),
        progressDialog);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    boolean confirmed = true;
    if (errors.size() == 0)
    {
      confirmed = displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_CONFIRM_RESTART_SERVER_DETAILS.get());
    }
    if ((errors.size() == 0) && confirmed)
    {
      launchOperation(newTask,
          INFO_CTRL_PANEL_STOPPING_SERVER_SUMMARY.get(),
          INFO_CTRL_PANEL_RESTARTING_SERVER_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_RESTARTING_SERVER_SUCCESSFUL_DETAILS.get(),
          ERR_CTRL_PANEL_RESTARTING_SERVER_ERROR_SUMMARY.get(),
          null,
          ERR_CTRL_PANEL_RESTARTING_SERVER_ERROR_DETAILS,
          progressDialog);
      progressDialog.setVisible(true);
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Displays a dialog asking for authentication. This method must be called
   * from the event thread.
   *
   */
  protected void authenticate()
  {
    if (!getLoginDialog().isVisible())
    {
      getLoginDialog().setVisible(true);
    }
    getLoginDialog().toFront();
  }

  /**
   * Returns the login dialog that is displayed when the method authenticate
   * is called.
   * @return the login dialog that is displayed when the method authenticate
   * is called.
   */
  protected GenericDialog getLoginDialog()
  {
    if (loginDialog == null)
    {
      LoginPanel loginPanel = new LoginPanel();
      loginDialog = new GenericDialog(Utilities.getFrame(this), loginPanel);
      loginPanel.setInfo(getInfo());
      Utilities.centerGoldenMean(loginDialog, Utilities.getFrame(this));
      loginDialog.setModal(true);
    }
    return loginDialog;
  }

  /**
   * Tells whether an entry exists or not.  Actually it tells if we could find
   * a given entry or not.
   * @param dn the DN of the entry to look for.
   * @return <CODE>true</CODE> if the entry with the provided DN could be found
   * and <CODE>false</CODE> otherwise.
   */
  protected boolean entryExists(String dn)
  {
    boolean entryExists = false;
    try
    {
      SearchControls ctls = new SearchControls();
      ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
      ctls.setReturningAttributes(
          new String[] {
              "dn"
          });
      String filter = BrowserController.ALL_OBJECTS_FILTER;
      NamingEnumeration<SearchResult> result =
        getInfo().getDirContext().search(Utilities.getJNDIName(dn),
            filter, ctls);

      while (result.hasMore())
      {
        SearchResult sr = result.next();
        entryExists = sr != null;
      }
    }
    catch (Throwable t)
    {
    }
    return entryExists;
  }

  /**
   * Tells whether a given entry exists and contains the specified object class.
   * @param dn the DN of the entry.
   * @param objectClass the object class.
   * @return <CODE>true</CODE> if the entry exists and contains the specified
   * object class and <CODE>false</CODE> otherwise.
   */
  protected boolean hasObjectClass(String dn, String objectClass)
  {
    boolean hasObjectClass = false;
    try
    {
      SearchControls ctls = new SearchControls();
      ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
      ctls.setReturningAttributes(
          new String[] {
              ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME
          });
      String filter = BrowserController.ALL_OBJECTS_FILTER;
      NamingEnumeration<SearchResult> result =
        getInfo().getDirContext().search(Utilities.getJNDIName(dn),
            filter, ctls);

      while (result.hasMore())
      {
        SearchResult sr = result.next();
        Set<String> values = ConnectionUtils.getValues(sr,
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
        if (values != null)
        {
          for (String s : values)
          {
            if (s.equalsIgnoreCase(objectClass))
            {
              hasObjectClass = true;
              break;
            }
          }
        }
      }
    }
    catch (Throwable t)
    {
    }
    return hasObjectClass;
  }

  /**
   * Returns the border to be used in the right panel of the dialog with a tree
   * on the left (for instance the schema browser, entry browser and index
   * browser).
   * @return the border to be used in the right panel.
   */
  protected Border getRightPanelBorder()
  {
    return ColorAndFontConstants.textAreaBorder;
  }

  /**
   * Returns the monitoring value in a String form to be displayed to the user.
   * @param attr the attribute to analyze.
   * @param monitoringEntry the monitoring entry.
   * @return the monitoring value in a String form to be displayed to the user.
   */
  public static String getMonitoringValue(MonitoringAttributes attr,
      CustomSearchResult monitoringEntry)
  {
    return Utilities.getMonitoringValue(attr, monitoringEntry);
  }

  /**
   * Updates the monitoring information writing it to a list of labels.
   * @param monitoringAttrs the monitoring operations whose information we want
   * to update.
   * @param monitoringLabels the monitoring labels to be updated.
   * @param monitoringEntry the monitoring entry containing the information to
   * be displayed.
   */
  protected void updateMonitoringInfo(
      List<MonitoringAttributes> monitoringAttrs,
      List<JLabel> monitoringLabels, CustomSearchResult monitoringEntry)
  {
    for (int i=0 ; i<monitoringAttrs.size(); i++)
    {
      String value =
        getMonitoringValue(monitoringAttrs.get(i), monitoringEntry);
      JLabel l = monitoringLabels.get(i);
      l.setText(value);
    }
  }

  /**
   * Returns the first value for a given attribute in the provided entry.
   * @param sr the entry.  It may be <CODE>null</CODE>.
   * @param attrName the attribute name.
   * @return the first value for a given attribute in the provided entry.
   */
  protected Object getFirstMonitoringValue(CustomSearchResult sr,
      String attrName)
  {
    return Utilities.getFirstMonitoringValue(sr, attrName);
  }

  /**
   * Returns the label to be used in panels (with ':') based on the definition
   * of the monitoring attribute.
   * @param attr the monitoring attribute.
   * @return the label to be used in panels (with ':') based on the definition
   * of the monitoring attribute.
   */
  protected static Message getLabel(MonitoringAttributes attr)
  {
    return INFO_CTRL_PANEL_OPERATION_NAME_AS_LABEL.get(
        attr.getMessage().toString());
  }
}
