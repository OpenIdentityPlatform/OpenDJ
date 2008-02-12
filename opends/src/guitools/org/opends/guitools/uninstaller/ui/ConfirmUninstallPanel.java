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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.uninstaller.ui;

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.Configuration;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

import org.opends.messages.Message;
import static org.opends.messages.AdminToolMessages.*;

/**
 * This is the panel displayed when the user is uninstalling Open DS.  It is
 * basically a panel with the text informing of the consequences of uninstalling
 * the server and asking for confirmation.
 *
 */
public class ConfirmUninstallPanel extends QuickSetupStepPanel
{
  private static final Logger LOG =
          Logger.getLogger(ConfirmUninstallPanel.class.getName());

  private static final long serialVersionUID = 81730510134697056L;

  private CurrentInstallStatus installStatus;
  private Set<String> outsideDbs;
  private Set<String> outsideLogs;

  private HashMap<FieldName, JCheckBox> hmCbs =
    new HashMap<FieldName, JCheckBox>();

  /**
   * The constructor of this class.
   * @param application Application this panel represents
   * @param installStatus the object describing the current installation status.
   *
   */
  public ConfirmUninstallPanel(GuiApplication application,
                               CurrentInstallStatus installStatus)
  {
    super(application);
    this.installStatus = installStatus;
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    switch (fieldName)
    {
    case EXTERNAL_DB_DIRECTORIES:
      Set<String> s1 = new HashSet<String>();
      if (outsideDbs.size() > 0)
      {
        if (getCheckBox(FieldName.EXTERNAL_DB_DIRECTORIES).isSelected())
        {
          s1.addAll(outsideDbs);
        }
      }
      value = s1;
      break;

    case EXTERNAL_LOG_FILES:
      Set<String> s2 = new HashSet<String>();
      if (outsideLogs.size() > 0)
      {
        if (getCheckBox(FieldName.EXTERNAL_LOG_FILES).isSelected())
        {
          s2.addAll(outsideLogs);
        }
      }
      value = s2;
      break;
    default:
      JCheckBox cb = getCheckBox(fieldName);
    value = new Boolean(cb.isSelected());
    break;
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_CONFIRM_UNINSTALL_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
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

    Message[] labels = {
        INFO_REMOVE_LIBRARIES_AND_TOOLS_LABEL.get(),
        INFO_REMOVE_DATABASES_LABEL.get(),
        INFO_REMOVE_LOGS_LABEL.get(),
        INFO_REMOVE_SCHEMA_AND_CONFIGURATION_LABEL.get(),
        INFO_REMOVE_BACKUPS_LABEL.get(),
        INFO_REMOVE_LDIFS_LABEL.get()
    };

    Message[] tooltips = {
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
        Message.raw(Utils.getInstallPathFromClasspath()),
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

    for (int i=0; i<names.length; i++)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      p.add(Box.createHorizontalGlue(), gbc);
      gbc.insets.left = 0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      p.add(getCheckBox(names[i]), gbc);
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
      LOG.log(Level.INFO, "Unable to determin outside databases", ioe);
    }

    try {
      outsideLogs = config.getOutsideLogs();
    } catch (IOException ioe) {
      LOG.log(Level.INFO, "Unable to determin outside logs", ioe);
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

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
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
      Message msg)
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
