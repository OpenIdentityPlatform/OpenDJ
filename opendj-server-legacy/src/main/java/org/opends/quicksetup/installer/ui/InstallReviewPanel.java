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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.util.Utils.*;

import static com.forgerock.opendj.util.OperatingSystem.isWindows;

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
import org.opends.server.types.HostPort;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** This is the panel that contains the Review Panel. */
public class InstallReviewPanel extends ReviewPanel {

  private static final long serialVersionUID = -7356174829193265699L;

  private final HashMap<FieldName, JLabel> hmLabels = new HashMap<>();
  private final HashMap<FieldName, JTextComponent> hmFields = new HashMap<>();
  private JPanel bottomComponent;
  private JCheckBox startCheckBox;
  private JCheckBox enableWindowsServiceCheckBox;
  private JLabel warningLabel;

  private JComboBox<LocalizableMessage> viewCombo;
  private final LocalizableMessage DISPLAY_TEXT = INFO_REVIEW_DISPLAY_TEXT.get();
  private final LocalizableMessage DISPLAY_EQUIVALENT_COMMAND = INFO_REVIEW_DISPLAY_EQUIVALENT_COMMAND.get();

  private JComponent cardLayoutPanel;

  private JEditorPane equivalentCommandPane;

  private UserData lastUserData;

  /**
   * Constructor of the panel.
   *
   * @param application
   *          Application represented by this panel the fields of the panel.
   */
  public InstallReviewPanel(GuiApplication application)
  {
    super(application);
    populateLabelAndFieldsMap();
  }

  @Override
  public void beginDisplay(UserData userData)
  {
    setFieldValue(FieldName.HOST_NAME, userData.getHostName());
    setFieldValue(FieldName.SERVER_PORT, Integer.toString(userData.getServerPort()));
    setFieldValue(FieldName.ADMIN_CONNECTOR_PORT, Integer.toString(userData.getAdminConnectorPort()));
    setFieldValue(FieldName.SECURITY_OPTIONS, Utils.getSecurityOptionsString(userData.getSecurityOptions(), false));
    setFieldValue(FieldName.DIRECTORY_MANAGER_DN, userData.getDirectoryManagerDn());
    setFieldValue(FieldName.DATA_OPTIONS, Utils.getDataDisplayString(userData));

    final boolean mustCreateAdministrator = userData.mustCreateAdministrator();
    if (mustCreateAdministrator)
    {
      setFieldValue(FieldName.GLOBAL_ADMINISTRATOR_UID, userData.getGlobalAdministratorUID());
    }
    getField(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(mustCreateAdministrator);
    getLabel(FieldName.GLOBAL_ADMINISTRATOR_UID).setVisible(mustCreateAdministrator);

    final boolean standalone = userData.getReplicationOptions().getType() == DataReplicationOptions.Type.STANDALONE;
    if (!standalone)
    {
      setFieldValue(FieldName.REPLICATION_PORT, getReplicationPortString(userData));
    }
    getField(FieldName.REPLICATION_PORT).setVisible(!standalone);
    getLabel(FieldName.REPLICATION_PORT).setVisible(!standalone);

    setFieldValue(FieldName.SERVER_JAVA_ARGUMENTS, getRuntimeString(userData));

    checkStartWarningLabel();
    updateEquivalentCommand(userData);

    lastUserData = userData;
  }

  /**
   * Creates and returns the instructions panel.
   *
   * @return the instructions panel.
   */
  @Override
  protected Component createInstructionsPanel()
  {
    final JPanel instructionsPanel = new JPanel(new GridBagLayout());
    instructionsPanel.setOpaque(false);
    final LocalizableMessage instructions = getInstructions();
    final JLabel l = new JLabel(instructions.toString());
    l.setFont(UIFactory.INSTRUCTIONS_FONT);

    viewCombo = new JComboBox<LocalizableMessage>();
    viewCombo.setModel(new DefaultComboBoxModel<>(new LocalizableMessage[] {
      DISPLAY_TEXT,
      DISPLAY_EQUIVALENT_COMMAND
    }));
    viewCombo.setSelectedIndex(0);

    viewCombo.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        updateInputPanel();
      }
    });

    final GridBagConstraints gbc = new GridBagConstraints();
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

  @Override
  protected boolean requiresScroll()
  {
    return false;
  }

  @Override
  protected Component createInputPanel()
  {
    final JPanel panel = UIFactory.makeJPanel();
    panel.setLayout(new GridBagLayout());

    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(createFieldsPanel(), gbc);

    final JComponent chk = getBottomComponent();
    if (chk != null) {
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.weighty = 0.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(chk, gbc);
    }

    return panel;
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    if (fieldName == FieldName.SERVER_START_INSTALLER)
    {
      return getStartCheckBox().isSelected();
    }
    else if (fieldName == FieldName.ENABLE_WINDOWS_SERVICE)
    {
      return getEnableWindowsServiceCheckBox().isSelected();
    }

    return null;
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_REVIEW_PANEL_INSTRUCTIONS.get();
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_REVIEW_PANEL_TITLE.get();
  }

  private void updateInputPanel()
  {
    final CardLayout cl = (CardLayout) cardLayoutPanel.getLayout();
    cl.show(cardLayoutPanel, viewCombo.getSelectedItem().toString());
  }

  /** Create the components and populate the Maps. */
  private void populateLabelAndFieldsMap()
  {
    final HashMap<FieldName, LabelFieldDescriptor> hm = new HashMap<>();

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

    for (final FieldName fieldName : hm.keySet())
    {
      final LabelFieldDescriptor desc = hm.get(fieldName);
      final JLabel label = UIFactory.makeJLabel(desc);
      final JTextComponent field = UIFactory.makeJTextComponent(desc, null);
      field.setOpaque(false);
      label.setLabelFor(field);

      hmFields.put(fieldName, field);
      hmLabels.put(fieldName, label);
    }
  }

  private JLabel getLabel(FieldName fieldName)
  {
    return hmLabels.get(fieldName);
  }

  private JTextComponent getField(FieldName fieldName)
  {
    return hmFields.get(fieldName);
  }

  private void setFieldValue(FieldName fieldName, String value)
  {
    getField(fieldName).setText(value);
  }

  private String getReplicationPortString(UserData userInstallData)
  {
    final LocalizableMessageBuilder buf = new LocalizableMessageBuilder();
    final DataReplicationOptions repl = userInstallData.getReplicationOptions();
    final SuffixesToReplicateOptions suf = userInstallData.getSuffixesToReplicateOptions();
    final Map<ServerDescriptor, AuthenticationData> remotePorts = userInstallData.getRemoteWithNoReplicationPort();

    if (repl.getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY
        && suf.getType() == SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES
        && !remotePorts.isEmpty())
    {
      final AuthenticationData authData = userInstallData.getReplicationOptions().getAuthenticationData();
      final HostPort serverToConnectDisplay = authData != null ? authData.getHostPort() : new HostPort(null, 0);
      String s;
      if (userInstallData.getReplicationOptions().useSecureReplication())
      {
        s = INFO_SECURE_REPLICATION_PORT_LABEL.get(
            userInstallData.getReplicationOptions().getReplicationPort()).toString();
      }
      else
      {
        s = Integer.toString(userInstallData.getReplicationOptions().getReplicationPort());
      }
      buf.append(s);

      final TreeSet<LocalizableMessage> remoteServerLines = new TreeSet<>();
      for (final ServerDescriptor server : remotePorts.keySet())
      {
        HostPort serverDisplay;
        if (server.getHostPort(false).equals(serverToConnectDisplay))
        {
          serverDisplay = serverToConnectDisplay;
        }
        else
        {
          serverDisplay = server.getHostPort(true);
        }

        final AuthenticationData repPort = remotePorts.get(server);
        if (repPort.useSecureConnection())
        {
          s = INFO_SECURE_REPLICATION_PORT_LABEL.get(repPort.getPort()).toString();
        }
        else
        {
          s = Integer.toString(repPort.getPort());
        }
        remoteServerLines.add(INFO_REMOTE_SERVER_REPLICATION_PORT.get(s, serverDisplay));
      }

      for (final LocalizableMessage line : remoteServerLines)
      {
        buf.append(Constants.LINE_SEPARATOR).append(line);
      }
    }
    else
    {
      buf.append(userInstallData.getReplicationOptions().getReplicationPort());
    }

    return buf.toString();
  }

 private String getRuntimeString(UserData userData)
 {
   final JavaArguments serverArguments = userData.getJavaArguments(UserData.SERVER_SCRIPT_NAME);
   final JavaArguments importArguments = userData.getJavaArguments(UserData.IMPORT_SCRIPT_NAME);
   final boolean defaultServer = userData.getDefaultJavaArguments(UserData.SERVER_SCRIPT_NAME).equals(serverArguments);
   final boolean defaultImport = userData.getDefaultJavaArguments(UserData.IMPORT_SCRIPT_NAME).equals(importArguments);

   if (defaultServer && defaultImport)
   {
     return INFO_DEFAULT_JAVA_ARGUMENTS.get().toString();
   }
   else if (defaultServer)
   {
     return INFO_USE_CUSTOM_IMPORT_RUNTIME.get(importArguments.getStringArguments()).toString();
   }
   else if (defaultImport)
   {
     return INFO_USE_CUSTOM_SERVER_RUNTIME.get(serverArguments.getStringArguments()).toString();
   }

   return INFO_USE_CUSTOM_SERVER_RUNTIME.get(serverArguments.getStringArguments()) + Constants.LINE_SEPARATOR
        + INFO_USE_CUSTOM_IMPORT_RUNTIME.get(importArguments.getStringArguments());
 }

  /**
   * Returns and creates the fields panel.
   *
   * @return the fields panel.
   */
  @Override
  protected JPanel createFieldsPanel()
  {
    final JPanel fieldsPanel = new JPanel(new GridBagLayout());
    fieldsPanel.setOpaque(false);

    final GridBagConstraints gbc = new GridBagConstraints();
    cardLayoutPanel = new JPanel(new CardLayout());
    cardLayoutPanel.setOpaque(false);

    final JComponent p = createReadOnlyPanel();
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
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    final GridBagConstraints gbc = new GridBagConstraints();

    final List<FieldName> fieldNames = new LinkedList<>();
    fieldNames.addAll(Arrays.asList(
        new FieldName[] {
          FieldName.HOST_NAME, FieldName.SERVER_PORT,
          FieldName.ADMIN_CONNECTOR_PORT, FieldName.SECURITY_OPTIONS,
          FieldName.DIRECTORY_MANAGER_DN, FieldName.GLOBAL_ADMINISTRATOR_UID,
          FieldName.DATA_OPTIONS, FieldName.REPLICATION_PORT,
          FieldName.SERVER_JAVA_ARGUMENTS
          }
     ));

    boolean isFirst = true;
    for (final FieldName fieldName : fieldNames)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.top = isFirst ? 0 : UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      panel.add(getLabel(fieldName), gbc);

      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets.top = isFirst ? 0 : UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.gridwidth = GridBagConstraints.REMAINDER;

      panel.add(getField(fieldName), gbc);
      isFirst = false;
    }

    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.VERTICAL;
    panel.add(Box.createVerticalGlue(), gbc);

    return panel;
  }

  private Component createEquivalentCommandPanel(final JScrollPane scroll)
  {
    equivalentCommandPane = UIFactory.makeProgressPane(scroll);
    equivalentCommandPane.setAutoscrolls(true);
    scroll.setViewportView(equivalentCommandPane);
    equivalentCommandPane.setOpaque(false);

    return equivalentCommandPane;
  }

  @Override
  protected JComponent getBottomComponent()
  {
    if (bottomComponent == null)
    {
      bottomComponent = new JPanel(new GridBagLayout());
      bottomComponent.setOpaque(false);

      final GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.WEST;

      final JPanel auxPanel = new JPanel(new GridBagLayout());
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

      if (isWindows())
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
      startCheckBox = UIFactory.makeJCheckBox(INFO_START_SERVER_LABEL.get(),
                                              INFO_START_SERVER_TOOLTIP.get(),
                                              UIFactory.TextStyle.CHECKBOX);
      startCheckBox.setSelected(getApplication().getUserData().getStartServer());
      startCheckBox.addActionListener(new ActionListener()
      {
        @Override
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
      enableWindowsServiceCheckBox = UIFactory.makeJCheckBox(INFO_ENABLE_WINDOWS_SERVICE_LABEL.get(),
                                                             INFO_ENABLE_WINDOWS_SERVICE_TOOLTIP.get(),
                                                             UIFactory.TextStyle.CHECKBOX);
      enableWindowsServiceCheckBox.setSelected(getApplication().getUserData().getEnableWindowsService());
      enableWindowsServiceCheckBox.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          if (isWindows())
          {
            lastUserData.setEnableWindowsService(enableWindowsServiceCheckBox.isSelected());
            updateEquivalentCommand(lastUserData);
          }
        }
      });
    }

    return enableWindowsServiceCheckBox;
  }

  /**
   * Depending on whether we want to replicate or not, we do have to start the
   * server temporarily to update its configuration and initialize data.
   */
  private void checkStartWarningLabel()
  {
    boolean visible = !getStartCheckBox().isSelected();
    if (visible)
    {
      final UserData userData = getApplication().getUserData();
      visible = userData.getReplicationOptions().getType() != DataReplicationOptions.Type.STANDALONE;
    }
    getWarningLabel().setVisible(visible);
  }

  private void updateEquivalentCommand(UserData userData)
  {
    final HtmlProgressMessageFormatter formatter = new HtmlProgressMessageFormatter();
    final StringBuilder sb = new StringBuilder();

    final String s = getEquivalentJavaPropertiesProcedure(userData, formatter);
    if (s != null && s.length() > 0)
    {
      sb.append(s)
        .append(formatter.getTaskSeparator());
    }

    sb.append(formatter.getFormattedProgress(INFO_INSTALL_SETUP_EQUIVALENT_COMMAND_LINE.get()));
    List<String> setupCmdLine = getSetupEquivalentCommandLine(userData);
    appendText(sb, formatter, getFormattedEquivalentCommandLine(setupCmdLine, formatter));

    if (userData.getReplicationOptions().getType() == DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY)
    {
      sb.append(formatter.getTaskSeparator());
      final List<List<String>> cmdLines = getDsReplicationEquivalentCommandLines("enable", userData);
      if (cmdLines.size() == 1)
      {
        sb.append(formatter.getFormattedProgress(INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINE.get()));
      }
      else if (cmdLines.size() > 1)
      {
        sb.append(formatter.getFormattedProgress(INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      }

      for (final List<String> cmdLine : cmdLines)
      {
        appendText(sb, formatter, getFormattedEquivalentCommandLine(cmdLine, formatter));
      }

      sb.append(formatter.getLineBreak());
      sb.append(formatter.getLineBreak());

      if (cmdLines.size() == 1)
      {
        sb.append(formatter.getFormattedProgress(INFO_INSTALL_INITIALIZE_REPLICATION_EQUIVALENT_COMMAND_LINE.get()));
      }
      else if (cmdLines.size() > 1)
      {
        sb.append(formatter.getFormattedProgress(INFO_INSTALL_INITIALIZE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      }

      final List<List<String>> dsReplicationCmdLines = getDsReplicationEquivalentCommandLines("initialize", userData);
      for (final List<String> cmdLine : dsReplicationCmdLines)
      {
        appendText(sb, formatter, getFormattedEquivalentCommandLine(cmdLine, formatter));
      }
    }
    else if (userData.getReplicationOptions().getType() == DataReplicationOptions.Type.FIRST_IN_TOPOLOGY)
    {
      sb.append(formatter.getTaskSeparator())
        .append(formatter.getFormattedProgress(INFO_INSTALL_ENABLE_REPLICATION_EQUIVALENT_COMMAND_LINES.get()));
      for (final List<String> cmdLine : getDsConfigReplicationEnableEquivalentCommandLines(userData))
      {
        appendText(sb, formatter, getFormattedEquivalentCommandLine(cmdLine, formatter));
      }
    }

    if (userData.getReplicationOptions().getType() != DataReplicationOptions.Type.STANDALONE
        && !userData.getStartServer())
    {
      sb.append(formatter.getTaskSeparator());
      sb.append(formatter.getFormattedProgress(INFO_INSTALL_STOP_SERVER_EQUIVALENT_COMMAND_LINE.get()));
      final String cmd = getPath(Installation.getLocal().getServerStopCommandFile());
      appendText(sb, formatter, formatter.getFormattedProgress(LocalizableMessage.raw(cmd)));
    }

    equivalentCommandPane.setText(sb.toString());
  }

  private void appendText(final StringBuilder sb, final HtmlProgressMessageFormatter formatter, CharSequence text)
  {
    sb.append(formatter.getLineBreak())
      .append(Constants.HTML_BOLD_OPEN)
      .append(text)
      .append(Constants.HTML_BOLD_CLOSE);
  }

  private String getEquivalentJavaPropertiesProcedure(final UserData userData,
      final ProgressMessageFormatter formatter)
  {
    final StringBuilder sb = new StringBuilder();
    final JavaArguments serverArguments = userData.getJavaArguments(UserData.SERVER_SCRIPT_NAME);
    final JavaArguments importArguments = userData.getJavaArguments(UserData.IMPORT_SCRIPT_NAME);
    final List<String> linesToAdd = new ArrayList<>();

    final boolean defaultServer =
        userData.getDefaultJavaArguments(UserData.SERVER_SCRIPT_NAME).equals(serverArguments);
    final boolean defaultImport =
        userData.getDefaultJavaArguments(UserData.IMPORT_SCRIPT_NAME).equals(importArguments);

    if (!defaultServer)
    {
      linesToAdd.add(getJavaArgPropertyForScript(UserData.SERVER_SCRIPT_NAME)
          + ": " + serverArguments.getStringArguments());
    }

    if (!defaultImport)
    {
      linesToAdd.add(getJavaArgPropertyForScript(UserData.IMPORT_SCRIPT_NAME)
          + ": " + importArguments.getStringArguments());
    }

    if (linesToAdd.size() == 1)
    {
      final String arg0 = getJavaPropertiesFilePath();
      final String arg1 = linesToAdd.get(0);
      sb.append(formatter.getFormattedProgress(INFO_EDIT_JAVA_PROPERTIES_LINE.get(arg0, arg1)));
    }
    else if (linesToAdd.size() > 1)
    {
      final String arg0 = getJavaPropertiesFilePath();
      final String arg1 = joinAsString(Constants.LINE_SEPARATOR, linesToAdd);
      sb.append(formatter.getFormattedProgress(INFO_EDIT_JAVA_PROPERTIES_LINES.get(arg0, arg1)));
    }

    return sb.toString();
  }

  private static String getJavaArgPropertyForScript(String scriptName)
  {
    return scriptName + ".java-args";
  }

  private String getJavaPropertiesFilePath()
  {
    final String path = Utils.getInstancePathFromInstallPath(Utils.getInstallPathFromClasspath());
    return getPath(getPath(path, Installation.CONFIG_PATH_RELATIVE), Installation.DEFAULT_JAVA_PROPERTIES_FILE);
  }

}
