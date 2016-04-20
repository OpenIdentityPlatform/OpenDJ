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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */

package org.opends.guitools.uninstaller.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.Configuration;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

/**
 * This is the panel displayed when the user is uninstalling Open DS.  It is
 * basically a panel with the text informing of the consequences of uninstalling
 * the server and asking for confirmation.
 */
public class ConfirmUninstallPanel extends QuickSetupStepPanel
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final long serialVersionUID = 81730510134697056L;

  private Set<String> outsideDbs;
  private Set<String> outsideLogs;

  private HashMap<FieldName, JCheckBox> hmCbs = new HashMap<>();

  /**
   * The constructor of this class.
   * @param application Application this panel represents
   */
  public ConfirmUninstallPanel(GuiApplication application)
  {
    super(application);
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    switch (fieldName)
    {
    case EXTERNAL_DB_DIRECTORIES:
      Set<String> s1 = new HashSet<>();
      if (outsideDbs.size() > 0
          && getCheckBox(FieldName.EXTERNAL_DB_DIRECTORIES).isSelected())
      {
        s1.addAll(outsideDbs);
      }
      return s1;

    case EXTERNAL_LOG_FILES:
      Set<String> s2 = new HashSet<>();
      if (outsideLogs.size() > 0
          && getCheckBox(FieldName.EXTERNAL_LOG_FILES).isSelected())
      {
        s2.addAll(outsideLogs);
      }
      return s2;
    default:
      JCheckBox cb = getCheckBox(fieldName);
      return cb.isSelected();
    }
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_CONFIRM_UNINSTALL_PANEL_TITLE.get();
  }

  @Override
  protected Component createInputPanel()
  {
    FieldName[] fieldNames = {
        FieldName.REMOVE_LIBRARIES_AND_TOOLS,
        FieldName.REMOVE_DATABASES,
        FieldName.REMOVE_LOGS,
        FieldName.REMOVE_CONFIGURATION_AND_SCHEMA,
        FieldName.REMOVE_BACKUPS,
        FieldName.REMOVE_LDIFS,
    };

    LocalizableMessage[] labels = {
        INFO_REMOVE_LIBRARIES_AND_TOOLS_LABEL.get(),
        INFO_REMOVE_DATABASES_LABEL.get(),
        INFO_REMOVE_LOGS_LABEL.get(),
        INFO_REMOVE_SCHEMA_AND_CONFIGURATION_LABEL.get(),
        INFO_REMOVE_BACKUPS_LABEL.get(),
        INFO_REMOVE_LDIFS_LABEL.get()
    };

    LocalizableMessage[] tooltips = {
        INFO_REMOVE_LIBRARIES_AND_TOOLS_TOOLTIP.get(),
        INFO_REMOVE_DATABASES_TOOLTIP.get(),
        INFO_REMOVE_LOGS_TOOLTIP.get(),
        INFO_REMOVE_SCHEMA_AND_CONFIGURATION_TOOLTIP.get(),
        INFO_REMOVE_BACKUPS_TOOLTIP.get(),
        INFO_REMOVE_LDIFS_TOOLTIP.get()
    };

    for (int i=0; i<fieldNames.length; i++)
    {
      JCheckBox cb = UIFactory.makeJCheckBox(labels[i], tooltips[i],
          UIFactory.TextStyle.INSTRUCTIONS);
      cb.setSelected(true);
      hmCbs.put(fieldNames[i], cb);
    }


    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();

    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    gbc.weightx = 0.0;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.anchor = GridBagConstraints.WEST;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_SERVER_PATH_LABEL.get(),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        LocalizableMessage.raw(Utils.getInstallPathFromClasspath()),
        UIFactory.TextStyle.INSTRUCTIONS),
        gbc);

    FieldName[] names = {
        FieldName.REMOVE_LIBRARIES_AND_TOOLS,
        FieldName.REMOVE_DATABASES,
        FieldName.REMOVE_LOGS,
        FieldName.REMOVE_CONFIGURATION_AND_SCHEMA,
        FieldName.REMOVE_BACKUPS,
        FieldName.REMOVE_LDIFS
    };

    for (FieldName fieldName : names)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      p.add(Box.createHorizontalGlue(), gbc);
      gbc.insets.left = 0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      p.add(getCheckBox(fieldName), gbc);
    }

    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.left = 0;

    panel.add(p, gbc);

    Installation installation = Installation.getLocal();
    Configuration config = installation.getCurrentConfiguration();
    try {
      outsideDbs = config.getOutsideDbs();
    } catch (IOException ioe) {
      logger.info(LocalizableMessage.raw("Unable to determin outside databases", ioe));
    }

    try {
      outsideLogs = config.getOutsideLogs();
    } catch (IOException ioe) {
      logger.info(LocalizableMessage.raw("Unable to determin outside logs", ioe));
    }


    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;
    if (outsideDbs.size() > 0)
    {
      JPanel dbPanel = createDbPanel();
      panel.add(dbPanel, gbc);
    }

    if (outsideLogs.size() > 0)
    {
      JPanel logPanel = createLogPanel();
      panel.add(logPanel, gbc);
    }

    addVerticalGlue(panel);

    return panel;
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_CONFIRM_UNINSTALL_PANEL_INSTRUCTIONS.get();
  }

  /**
   * Creates a panel to ask the user if (s)he wants to remove the databases
   * located outside the installation path.
   * @return a panel to ask the user if (s)he wants to remove the databases
   * located outside the installation path.
   */
  private JPanel createDbPanel()
  {
    JCheckBox cbOutsideDbs = UIFactory.makeJCheckBox(
        INFO_DELETE_OUTSIDE_DBS_LABEL.get(),
        INFO_DELETE_OUTSIDE_DBS_TOOLTIP.get(),
            UIFactory.TextStyle.INSTRUCTIONS);
    cbOutsideDbs.setSelected(true);
    hmCbs.put(FieldName.EXTERNAL_DB_DIRECTORIES, cbOutsideDbs);

    return createOutsidePathPanel(cbOutsideDbs, outsideDbs,
        INFO_DELETE_OUTSIDE_DBS_MSG.get());
  }

  /**
   * Creates a panel to ask the user if (s)he wants to remove the logs located
   * outside the installation path.
   * @return a panel to ask the user if (s)he wants to remove the logs located
   * outside the installation path.
   */
  private JPanel createLogPanel()
  {
    JCheckBox cbOutsideLogs = UIFactory.makeJCheckBox(
        INFO_DELETE_OUTSIDE_LOGS_LABEL.get(),
        INFO_DELETE_OUTSIDE_LOGS_TOOLTIP.get(),
        UIFactory.TextStyle.INSTRUCTIONS);
    cbOutsideLogs.setSelected(true);
    hmCbs.put(FieldName.EXTERNAL_LOG_FILES, cbOutsideLogs);

    return createOutsidePathPanel(cbOutsideLogs, outsideLogs,
        INFO_DELETE_OUTSIDE_LOGS_MSG.get());
  }

  private JPanel createOutsidePathPanel(JCheckBox cb, Set<String> paths,
      LocalizableMessage msg)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    panel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, msg,
        UIFactory.TextStyle.INSTRUCTIONS), gbc);
    DefaultListModel listModel = new DefaultListModel();
    for (String path : paths)
    {
      listModel.addElement(path);
    }
    JList list = UIFactory.makeJList(UIFactory.TextStyle.INSTRUCTIONS);
    list.setModel(listModel);
    list.setBackground(UIFactory.CURRENT_STEP_PANEL_BACKGROUND);
    list.setVisibleRowCount(Math.min(3, listModel.getSize()));
    JScrollPane scroll = new JScrollPane(list);
    scroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(scroll, gbc);

    gbc.insets.left = 0;
    panel.add(cb, gbc);

    return panel;
  }

  /**
   * Returns the checkbox corresponding to the provided FieldName.
   * @param fieldName the FieldName object.
   * @return the checkbox corresponding to the provided FieldName.
   */
  private JCheckBox getCheckBox(FieldName fieldName)
  {
    JCheckBox cb = hmCbs.get(fieldName);
    if (cb == null)
    {
      throw new IllegalArgumentException("The FieldName "+fieldName+
          " has no checkbox associated.");
    }
    return cb;
  }
}
