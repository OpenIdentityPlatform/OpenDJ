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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.admin.ads.ServerDescriptor;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.JavaArguments;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.*;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * This is the panel that contains the Review Panel.
 *
 */
public class InstallReviewPanel extends ReviewPanel {

  private static final long serialVersionUID = -7356174829193265699L;

  private boolean displayServerLocation;

  private HashMap<FieldName, JLabel> hmLabels =
      new HashMap<FieldName, JLabel>();

  private HashMap<FieldName, JTextComponent> hmFields =
      new HashMap<FieldName, JTextComponent>();
  private JPanel bottomComponent;
  private JCheckBox startCheckBox;
  private JCheckBox enableWindowsServiceCheckBox;
  private JLabel warningLabel;

  private JComboBox viewCombo;
  private final Message DISPLAY_TEXT = INFO_REVIEW_DISPLAY_TEXT.get();
  private final Message DISPLAY_EQUIVALENT_COMMAND =
    INFO_REVIEW_DISPLAY_EQUIVALENT_COMMAND.get();

  private JComponent cardLayoutPanel;

  private JEditorPane equivalentCommandPane;

  private UserData lastUserData;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel
   * the fields of the panel.
   */
  public InstallReviewPanel(GuiApplication application)
  {
    super(application);
    this.displayServerLocation = isWebStart();
    populateLabelAndFieldsMap();
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData userData)
  {
    if (displayServerLocation)
    {
      setFieldValue(FieldName.SERVER_LOCATION, userData.getServerLocation());
    }
    setFieldValue(FieldName.HOST_NAME,
        String.valueOf(userData.getHostName()));
    setFieldValue(FieldName.SERVER_PORT,
        String.valueOf(userData.getServerPort()));
    setFieldValue(FieldName.ADMIN_CONNECTOR_PORT,
        String.valueOf(userData.getAdminConnectorPort()));
    setFieldValue(FieldName.SECURITY_OPTIONS,
        Utils.getSecurityOptionsString(userData.getSecurityOptions(), false));
    setFieldValue(FieldName.DIRECTORY_MANAGER_DN, userData
        .getDirectoryManagerDn());
    setFieldValue(FieldName.DATA_OPTIONS, Utils.getDataDisplayString(userData));
    if (userData.mustCreateAdministrator())
    {
      setFieldValue(FieldName.GLOBAL_ADMINISTRATOR_UID,
          String.valueOf(userData.getGlobalAdministratorUID()));
      getField(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(true);
      getLabel(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(true);
    }
    else
    {
      getField(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(false);
      getLabel(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(false);
    }

    if (userData.getReplicationOptions().getType() ==
      DataReplicationOptions.Type.STANDALONE)
    {
      getField(FieldName.REPLICATION_PORT).setVisible(false);
      getLabel(FieldName.REPLICATION_PORT).setVisible(false);
    }
    else
    {
      setFieldValue(FieldName.REPLICATION_PORT,
          getReplicationPortString(userData));
      getField(FieldName.REPLICATION_PORT).setVisible(true);
      getLabel(FieldName.REPLICATION_PORT).setVisible(true);
    }

    setFieldValue(FieldName.SERVER_JAVA_ARGUMENTS, getRuntimeString(userData));

    checkStartWarningLabel();
    updateEquivalentCommand(userData);

    lastUserData = userData;
  }

  /**
   * Creates and returns the instructions panel.
   * @return the instructions panel.
   */
  protected Component createInstructionsPanel()
  {
    JPanel instructionsPanel = new JPanel(new GridBagLayout());
    instructionsPanel.setOpaque(false);
    Message instructions = getInstructions();
    JLabel l = new JLabel(instructions.toString());
    l.setFont(UIFactory.INSTRUCTIONS_FONT);

    Message[] values = {
      DISPLAY_TEXT,
      DISPLAY_EQUIVALENT_COMMAND
    };
    DefaultComboBoxModel model = new DefaultComboBoxModel(values);
    viewCombo = new JComboBox();
    viewCombo.setModel(model);
    viewCombo.setSelectedIndex(0);

    viewCombo.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        updateInputPanel();
      }
    });

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    instructionsPanel.add(l, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    instructionsPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridx = 2;
    gbc.weightx = 0.0;
    gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
    instructionsPanel.add(viewCombo);

    return instructionsPanel;
  }

  /**
   * {@inheritDoc}
   */
  protected boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(createFieldsPanel(), gbc);

    JComponent chk = getBottomComponent();
    if (chk != null) {
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.weighty = 0.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(chk, gbc);
    }

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    if (fieldName == FieldName.SERVER_START_INSTALLER)
    {
      value = getStartCheckBox().isSelected();
    }
    else if (fieldName == FieldName.ENABLE_WINDOWS_SERVICE)
    {
      value = getEnableWindowsServiceCheckBox().isSelected();
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return INFO_REVIEW_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_REVIEW_PANEL_TITLE.get();
  }

  private void updateInputPanel()
  {
    CardLayout cl = (CardLayout)cardLayoutPanel.getLayout();
    cl.show(cardLayoutPanel, viewCombo.getSelectedItem().toString());
  }

  /**
   * Create the components and populate the Maps.
   */
  private void populateLabelAndFieldsMap()
  {
    HashMap<FieldName, LabelFieldDescriptor> hm =
        new HashMap<FieldName, LabelFieldDescriptor>();

    if (displayServerLocation)
    {
      hm.put(FieldName.SERVER_LOCATION, new LabelFieldDescriptor(
              INFO_SERVER_LOCATION_LABEL.get(),
              INFO_SERVER_LOCATION_RELATIVE_TOOLTIP.get(),
              LabelFieldDescriptor.FieldType.READ_ONLY,
              LabelFieldDescriptor.LabelType.PRIMARY, 0));
    }

    hm.put(FieldName.HOST_NAME, new LabelFieldDescriptor(
            INFO_HOST_NAME_LABEL.get(),
            INFO_HOST_NAME_TOOLTIP.get(),
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.SERVER_PORT, new LabelFieldDescriptor(
            INFO_SERVER_PORT_LABEL.get(),
            INFO_SERVER_PORT_TOOLTIP.get(),
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.ADMIN_CONNECTOR_PORT, new LabelFieldDescriptor(
        INFO_ADMIN_CONNECTOR_PORT_LABEL.get(),
        INFO_ADMIN_CONNECTOR_PORT_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.READ_ONLY,
        LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.SECURITY_OPTIONS, new LabelFieldDescriptor(
            INFO_SERVER_SECURITY_LABEL.get(),
            INFO_SERVER_SECURITY_TOOLTIP.get(),
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.DIRECTORY_MANAGER_DN, new LabelFieldDescriptor(
            INFO_SERVER_DIRECTORY_MANAGER_DN_LABEL.get(),
            INFO_SERVER_DIRECTORY_MANAGER_DN_TOOLTIP.get(),
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_UID, new LabelFieldDescriptor(
            INFO_GLOBAL_ADMINISTRATOR_UID_LABEL.get(), null,
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.DATA_OPTIONS, new LabelFieldDescriptor(
            INFO_DIRECTORY_DATA_LABEL.get(), null,
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.REPLICATION_PORT, new LabelFieldDescriptor(
            INFO_REPLICATION_PORT_LABEL.get(), null,
            LabelFieldDescriptor.FieldType.READ_ONLY,
            LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.SERVER_JAVA_ARGUMENTS, new LabelFieldDescriptor(
        INFO_RUNTIME_OPTIONS_LABEL.get(), null,
        LabelFieldDescriptor.FieldType.READ_ONLY,
        LabelFieldDescriptor.LabelType.PRIMARY, 0));

    for (FieldName fieldName : hm.keySet())
    {
      LabelFieldDescriptor desc = hm.get(fieldName);

      JTextComponent field = UIFactory.makeJTextComponent(desc, null);
      field.setOpaque(false);
      JLabel label = UIFactory.makeJLabel(desc);

      hmFields.put(fieldName, field);
      label.setLabelFor(field);

      hmLabels.put(fieldName, label);
    }
  }

  /**
   * Returns the label associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the JLabel.
   * @return the label associated with the given field name.
   */
  private JLabel getLabel(FieldName fieldName)
  {
    return hmLabels.get(fieldName);
  }

  /**
   * Returns the JTextComponent associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the
   * JTextComponent.
   * @return the JTextComponent associated with the given field name.
   */
  private JTextComponent getField(FieldName fieldName)
  {
    return hmFields.get(fieldName);
  }

  /**
   * Updates the JTextComponent associated with a FieldName with a text value.
   * @param fieldName the field name of the JTextComponent that we want to
   * update.
   * @param value the value to be set.
   */
  private void setFieldValue(FieldName fieldName, String value)
  {
    getField(fieldName).setText(value);
  }

   /**
    * Returns the String representing the replication port configuration.
    * @param userInstallData the install data provided of the user.
    * @return the localized string describing the Replication Ports chosen by
    * the user.
    */
  private String getReplicationPortString(UserData userInstallData)
  {
    MessageBuilder buf = new MessageBuilder();

    DataReplicationOptions repl =
      userInstallData.getReplicationOptions();

    SuffixesToReplicateOptions suf =
      userInstallData.getSuffixesToReplicateOptions();

    Map<ServerDescriptor, AuthenticationData> remotePorts =
      userInstallData.getRemoteWithNoReplicationPort();

    if ((repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY) &&
      (suf.getType() ==
        SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES) &&
        remotePorts.size() > 0)
    {
      String serverToConnectDisplay;
      AuthenticationData authData =
        userInstallData.getReplicationOptions().getAuthenticationData();
      if (authData != null)
      {
        serverToConnectDisplay = authData.getHostName()+":"+authData.getPort();
      }
      else
      {
        serverToConnectDisplay = "";
      }
      String s;
      if (userInstallData.getReplicationOptions().useSecureReplication())
      {
        s = INFO_SECURE_REPLICATION_PORT_LABEL.get(
            String.valueOf(userInstallData.getReplicationOptions()
                .getReplicationPort())).toString();
      }
      else
      {
        s = String.valueOf(userInstallData.getReplicationOptions()
            .getReplicationPort());
      }
      buf.append(s);
      TreeSet<Message> remoteServerLines = new TreeSet<Message>();
      for (ServerDescriptor server : remotePorts.keySet())
      {
        String serverDisplay;
        if (server.getHostPort(false).equalsIgnoreCase(serverToConnectDisplay))
        {
          serverDisplay = serverToConnectDisplay;
        }
        else
        {
          serverDisplay = server.getHostPort(true);
        }
        AuthenticationData repPort = remotePorts.get(server);
        if (repPort.useSecureConnection())
        {
          s = INFO_SECURE_REPLICATION_PORT_LABEL.get(
              String.valueOf(repPort.getPort())).toString();
        }
        else
        {
          s = String.valueOf(repPort.getPort());
        }
        remoteServerLines.add(INFO_REMOTE_SERVER_REPLICATION_PORT.get(s,
                serverDisplay));
      }
      for (Message line : remoteServerLines)
      {
        buf.append("\n").append(line);
      }
    }
    else
    {
      buf.append(userInstallData.getReplicationOptions().getReplicationPort());
    }
    return buf.toString();
  }

  /**
   * Returns the String representing the runtime configuration.
   * @param userData the DataOptions of the user.
   * @return the localized string describing the runtime options chosen by the
   * user.
   */
 private String getRuntimeString(UserData userData)
 {
   String s;
   JavaArguments serverArguments =
     userData.getJavaArguments(UserData.SERVER_SCRIPT_NAME);
   JavaArguments importArguments =
     userData.getJavaArguments(UserData.IMPORT_SCRIPT_NAME);


   boolean defaultServer =
     userData.getDefaultJavaArguments(UserData.SERVER_SCRIPT_NAME).equals(
         serverArguments);
   boolean defaultImport =
   userData.getDefaultJavaArguments(UserData.IMPORT_SCRIPT_NAME).equals(
       importArguments);

   if (defaultServer && defaultImport)
   {
     s = INFO_DEFAULT_JAVA_ARGUMENTS.get().toString();
   }
   else if (defaultServer)
   {
     s = INFO_USE_CUSTOM_IMPORT_RUNTIME.get(
         importArguments.getStringArguments()).toString();
   }
   else if (defaultImport)
   {
     s = INFO_USE_CUSTOM_SERVER_RUNTIME.get(
         serverArguments.getStringArguments()).toString();
   }
   else
   {
     s = INFO_USE_CUSTOM_SERVER_RUNTIME.get(
         serverArguments.getStringArguments())+"\n"+
         INFO_USE_CUSTOM_IMPORT_RUNTIME.get(
             importArguments.getStringArguments());
   }
   return s;
 }

  /**
   * Returns and creates the fields panel.
   * @return the fields panel.
   */
  protected JPanel createFieldsPanel()
  {
    JPanel fieldsPanel = new JPanel(new GridBagLayout());
    fieldsPanel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();

    cardLayoutPanel = new JPanel(new CardLayout());
    cardLayoutPanel.setOpaque(false);

    JComponent p = createReadOnlyPanel();
    p.setBorder(new EmptyBorder(UIFactory.TOP_INSET_SECONDARY_FIELD,
        UIFactory.LEFT_INSET_SECONDARY_FIELD,
        UIFactory.BOTTOM_INSET_SECONDARY_FIELD,
        UIFactory.LEFT_INSET_SECONDARY_FIELD));

    JScrollPane scroll = new JScrollPane(p);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getViewport().setBackground(UIFactory.DEFAULT_BACKGROUND);
    scroll.setBackground(UIFactory.DEFAULT_BACKGROUND);

    cardLayoutPanel.add(scroll, DISPLAY_TEXT.toString());
    scroll = new JScrollPane();
    createEquivalentCommandPanel(scroll);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getViewport().setBackground(UIFactory.DEFAULT_BACKGROUND);
    scroll.setBackground(UIFactory.DEFAULT_BACKGROUND);
    cardLayoutPanel.add(scroll, DISPLAY_EQUIVALENT_COMMAND.toString());

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 3;
    gbc.fill = GridBagConstraints.BOTH;
    fieldsPanel.add(cardLayoutPanel, gbc);
    return fieldsPanel;
  }

  private JComponent createReadOnlyPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    GridBagConstraints gbc = new GridBagConstraints();

    FieldName[] fieldNames;
    if (displayServerLocation)
    {
      fieldNames =
        new FieldName[]
          {
            FieldName.SERVER_LOCATION, FieldName.HOST_NAME,
            FieldName.SERVER_PORT, FieldName.ADMIN_CONNECTOR_PORT,
            FieldName.SECURITY_OPTIONS,
            FieldName.DIRECTORY_MANAGER_DN, FieldName.GLOBAL_ADMINISTRATOR_UID,
            FieldName.DATA_OPTIONS, FieldName.REPLICATION_PORT,
            FieldName.SERVER_JAVA_ARGUMENTS
          };
    }
    else
    {
      fieldNames =
        new FieldName[]
          {
            FieldName.HOST_NAME, FieldName.SERVER_PORT,
            FieldName.ADMIN_CONNECTOR_PORT,
            FieldName.SECURITY_OPTIONS, FieldName.DIRECTORY_MANAGER_DN,
            FieldName.GLOBAL_ADMINISTRATOR_UID, FieldName.DATA_OPTIONS,
            FieldName.REPLICATION_PORT, FieldName.SERVER_JAVA_ARGUMENTS
          };
    }

    for (int i = 0; i < fieldNames.length; i++)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      if (i > 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      } else
      {
        gbc.insets.top = 0;
      }
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      panel.add(getLabel(fieldNames[i]), gbc);

      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      if (i > 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      } else
      {
        gbc.insets.top = 0;
      }
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;

      gbc.gridwidth = GridBagConstraints.REMAINDER;
      panel.add(getField(fieldNames[i]), gbc);
    }

    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  private Component createEquivalentCommandPanel(JScrollPane scroll)
  {
    equivalentCommandPane = UIFactory.makeProgressPane(scroll);
    equivalentCommandPane.setAutoscrolls(true);
    scroll.setViewportView(equivalentCommandPane);
    equivalentCommandPane.setOpaque(false);
    return equivalentCommandPane;
  }

  /**
   * {@inheritDoc}
   */
  protected JComponent getBottomComponent()
  {
    if (bottomComponent == null)
    {
      bottomComponent = new JPanel(new GridBagLayout());
      bottomComponent.setOpaque(false);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.WEST;
      JPanel auxPanel = new JPanel(new GridBagLayout());
      auxPanel.setOpaque(false);
      gbc.gridwidth = 3;
      auxPanel.add(getStartCheckBox(), gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      auxPanel.add(getWarningLabel(), gbc);
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = 0;
      gbc.weightx = 1.0;
      auxPanel.add(Box.createHorizontalGlue(), gbc);
      bottomComponent.add(auxPanel, gbc);
      if (Utils.isWindows())
      {
        gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
        bottomComponent.add(getEnableWindowsServiceCheckBox(), gbc);
      }
    }
    return bottomComponent;
  }

  private JLabel getWarningLabel()
  {
    if (warningLabel == null)
    {
      warningLabel = UIFactory.makeJLabel(UIFactory.IconType.WARNING,
          INFO_INSTALL_SERVER_MUST_BE_TEMPORARILY_STARTED.get(),
          UIFactory.TextStyle.READ_ONLY);
    }
    return warningLabel;
  }

  private JCheckBox getStartCheckBox()
  {
    if (startCheckBox == null)
    {
      startCheckBox =
          UIFactory.makeJCheckBox(INFO_START_SERVER_LABEL.get(),
              INFO_START_SERVER_TOOLTIP.get(), UIFactory.TextStyle.CHECKBOX);
      startCheckBox.setSelected(
          getApplication().getUserData().getStartServer());
      startCheckBox.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          checkStartWarningLabel();
          lastUserData.setStartServer(startCheckBox.isSelected());
          updateEquivalentCommand(lastUserData);
        }
      });
    }
    return startCheckBox;
  }

  private JCheckBox getEnableWindowsServiceCheckBox()
  {
    if (enableWindowsServiceCheckBox == null)
    {
      enableWindowsServiceCheckBox =
          UIFactory.makeJCheckBox(INFO_ENABLE_WINDOWS_SERVICE_LABEL.get(),
              INFO_ENABLE_WINDOWS_SERVICE_TOOLTIP.get(),
              UIFactory.TextStyle.CHECKBOX);
      enableWindowsServiceCheckBox.setSelected(
          getApplication().getUserData().getEnableWindowsService());
      enableWindowsServiceCheckBox.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          if (Utils.isWindows())
          {
            lastUserData.setEnableWindowsService(
                enableWindowsServiceCheckBox.isSelected());
            updateEquivalentCommand(lastUserData);
          }
        }
      });
    }
    return enableWindowsServiceCheckBox;
  }

  /**
   * Depending on whether we want to replicate or not, we do have to start
   * the server temporarily to update its configuration and initialize data.
   */
  private void checkStartWarningLabel()
  {
    boolean visible = !getStartCheckBox().isSelected();
    if (visible)
    {
      UserData userData = getApplication().getUserData();
      DataReplicationOptions rep = userData.getReplicationOptions();
      visible = rep.getType() != DataReplicationOptions.Type.STANDALONE;
    }
    getWarningLabel().setVisible(visible);
  }

  private void updateEquivalentCommand(UserData userData)
  {
    HtmlProgressMessageFormatter formatter =
      new HtmlProgressMessageFormatter();
    StringBuilder sb = new StringBuilder();

    String s = getEquivalentJavaPropertiesProcedure(userData, formatter);
    if (s != null && s.length() > 0)
    {
      sb.append(s);
      sb.append(formatter.getTaskSeparator());
    }

    sb.append(formatter.getFormattedProgress(
        INFO_INSTALL_SETUP_EQUIVALENT_COMMAND_LINE.get()));
    sb.append(formatter.getLineBreak());
    sb.append(Constants.HTML_BOLD_OPEN)
        .append(Utils.getFormattedEquivalentCommandLine(
            Utils.getSetupEquivalentCommandLine(userData), formatter))
        .append(Constants.HTML_BOLD_CLOSE);
    if (userData.getReplicationOptions().getType() ==
      DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      sb.append(formatter.getTaskSeparator());
      ArrayList<ArrayList<String>> cmdLines =
        Utils.getDsReplicationEnableEquivalentCommandLines(userData);
      if (cmdLines.size() == 1)
      {
        sb.append(formatter.getFormattedProgress(
          INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINE.get()));
      }
      else if (cmdLines.size() > 1)
      {
        sb.append(formatter.getFormattedProgress(
            INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      }
      for (ArrayList<String> cmdLine : cmdLines)
      {
        sb.append(formatter.getLineBreak());
        sb.append(Constants.HTML_BOLD_OPEN)
            .append(Utils.getFormattedEquivalentCommandLine(cmdLine,
                formatter))
            .append(Constants.HTML_BOLD_CLOSE);
      }

      sb.append(formatter.getLineBreak());
      sb.append(formatter.getLineBreak());
      cmdLines =
        Utils.getDsReplicationInitializeEquivalentCommandLines(userData);
      if (cmdLines.size() == 1)
      {
        sb.append(formatter.getFormattedProgress(
            INFO_INSTALL_INITIALIZE_REPLICATION_EQUIVALENT_COMMAND_LINE.get()));
      }
      else if (cmdLines.size() > 1)
      {
        sb.append(formatter.getFormattedProgress(
           INFO_INSTALL_INITIALIZE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      }

      for (ArrayList<String> cmdLine : cmdLines)
      {
        sb.append(formatter.getLineBreak());
        sb.append(Constants.HTML_BOLD_OPEN)
            .append(Utils.getFormattedEquivalentCommandLine(cmdLine,
                formatter))
            .append(Constants.HTML_BOLD_CLOSE);
      }
    }
    else if (userData.getReplicationOptions().getType() ==
      DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      sb.append(formatter.getTaskSeparator());
      sb.append(formatter.getFormattedProgress(
          INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      ArrayList<ArrayList<String>> cmdLines =
        Utils.getDsConfigReplicationEnableEquivalentCommandLines(userData);
      for (ArrayList<String> cmdLine : cmdLines)
      {
        sb.append(formatter.getLineBreak());
        sb.append(Constants.HTML_BOLD_OPEN)
            .append(Utils.getFormattedEquivalentCommandLine(cmdLine,
                formatter))
            .append(Constants.HTML_BOLD_CLOSE);
      }
    }

    if (userData.getReplicationOptions().getType() !=
      DataReplicationOptions.Type.STANDALONE &&
      !userData.getStartServer())
    {
      sb.append(formatter.getTaskSeparator());
      String cmd =
        Utils.getPath(Installation.getLocal().getServerStopCommandFile());
      sb.append(formatter.getFormattedProgress(
          INFO_INSTALL_STOP_SERVER_EQUIVALENT_COMMAND_LINE.get()));
      sb.append(formatter.getLineBreak());
      sb.append(Constants.HTML_BOLD_OPEN)
          .append(formatter.getFormattedProgress(Message.raw(cmd)))
          .append(Constants.HTML_BOLD_CLOSE);
    }
    equivalentCommandPane.setText(sb.toString());
  }

  private String getEquivalentJavaPropertiesProcedure(
      UserData userData,
      ProgressMessageFormatter formatter)
  {
    StringBuilder sb = new StringBuilder();
    JavaArguments serverArguments =
      userData.getJavaArguments(UserData.SERVER_SCRIPT_NAME);
    JavaArguments importArguments =
      userData.getJavaArguments(UserData.IMPORT_SCRIPT_NAME);

    ArrayList<String> linesToAdd = new ArrayList<String>();

    boolean defaultServer =
      userData.getDefaultJavaArguments(UserData.SERVER_SCRIPT_NAME).equals(
          serverArguments);
    boolean defaultImport =
    userData.getDefaultJavaArguments(UserData.IMPORT_SCRIPT_NAME).equals(
        importArguments);

    if (!defaultServer)
    {
      linesToAdd.add(getJavaArgPropertyForScript(UserData.SERVER_SCRIPT_NAME)
          +": "+serverArguments.getStringArguments());
    }
    if (!defaultImport)
    {
      linesToAdd.add(getJavaArgPropertyForScript(UserData.IMPORT_SCRIPT_NAME)+
          ": "+importArguments.getStringArguments());
    }

    if (linesToAdd.size() == 1)
    {
      String arg0 = getJavaPropertiesFilePath(userData);
      String arg1 = linesToAdd.get(0);
      sb.append(formatter.getFormattedProgress(
          INFO_EDIT_JAVA_PROPERTIES_LINE.get(arg0, arg1)));
    }
    else if (linesToAdd.size() > 1)
    {
      String arg0 = getJavaPropertiesFilePath(userData);
      String arg1 = Utils.getStringFromCollection(linesToAdd, "\n");
      sb.append(
          formatter.getFormattedProgress(INFO_EDIT_JAVA_PROPERTIES_LINES.get(
              arg0, arg1)));
    }

    return sb.toString();
  }

  /**
   * Returns the java argument property for a given script.
   * @param scriptName the script name.
   * @return the java argument property for a given script.
   */
  private static String getJavaArgPropertyForScript(String scriptName)
  {
    return scriptName+".java-args";
  }

  private String getJavaPropertiesFilePath(UserData userData)
  {
    String path;
    if (isWebStart())
    {
      path = userData.getServerLocation();
    }
    else
    {
      path = Utils.getInstallPathFromClasspath();
      path = Utils.getInstancePathFromInstallPath(path);
    }
    return Utils.getPath(
        Utils.getPath(path, Installation.CONFIG_PATH_RELATIVE),
        Installation.DEFAULT_JAVA_PROPERTIES_FILE);
  }
}
