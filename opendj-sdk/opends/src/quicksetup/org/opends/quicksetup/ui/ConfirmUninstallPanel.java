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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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

import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.installer.FieldName;
import org.opends.quicksetup.util.Utils;

/**
 * This is the panel displayed when the user is uninstalling Open DS.  It is
 * basically a panel with the text informing of the consequences of uninstalling
 * the server and asking for confirmation.
 *
 */
public class ConfirmUninstallPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 81730510134697056L;

  private CurrentInstallStatus installStatus;
  private Set<String> outsideDbs;
  private Set<String> outsideLogs;

  private HashMap<FieldName, JCheckBox> hmCbs =
    new HashMap<FieldName, JCheckBox>();

  /**
   * The constructor of this class.
   * @param installStatus the object describing the current installation status.
   *
   */
  public ConfirmUninstallPanel(CurrentInstallStatus installStatus)
  {
    this.installStatus = installStatus;
    createLayout();
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
  protected String getTitle()
  {
    return getMsg("confirm-uninstall-panel-title");
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

    String[] labels = {
        getMsg("remove-libraries-and-tools-label"),
        getMsg("remove-databases-label"),
        getMsg("remove-logs-label"),
        getMsg("remove-schema-and-configuration-label"),
        getMsg("remove-backups-label"),
        getMsg("remove-ldifs-label"),
    };

    String[] tooltips = {
        getMsg("remove-libraries-and-tools-tooltip"),
        getMsg("remove-databases-tooltip"),
        getMsg("remove-logs-tooltip"),
        getMsg("remove-schema-and-configuration-tooltip"),
        getMsg("remove-backups-tooltip"),
        getMsg("remove-ldifs-tooltip"),
    };

    for (int i=0; i<fieldNames.length; i++)
    {
      JCheckBox cb = UIFactory.makeJCheckBox(labels[i], tooltips[i],
          UIFactory.TextStyle.INSTRUCTIONS);
      cb.setOpaque(false);
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
        getMsg("server-path-label"),
        UIFactory.TextStyle.PRIMARY_FIELD_VALID), gbc);
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    p.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        Utils.getInstallPathFromClasspath(), UIFactory.TextStyle.INSTRUCTIONS),
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

    outsideDbs = Utils.getOutsideDbs(installStatus);
    outsideLogs = Utils.getOutsideLogs(installStatus);

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
  protected String getInstructions()
  {
    return getMsg("confirm-uninstall-panel-instructions");
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
        getMsg("delete-outside-dbs-label"),
        getMsg("delete-outside-dbs-tooltip"), UIFactory.TextStyle.INSTRUCTIONS);
    cbOutsideDbs.setOpaque(false);
    cbOutsideDbs.setSelected(true);
    hmCbs.put(FieldName.EXTERNAL_DB_DIRECTORIES, cbOutsideDbs);

    return createOutsidePathPanel(cbOutsideDbs, outsideDbs,
        "delete-outside-dbs-msg");
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
        getMsg("delete-outside-logs-label"),
        getMsg("delete-outside-logs-tooltip"),
        UIFactory.TextStyle.INSTRUCTIONS);
    cbOutsideLogs.setOpaque(false);
    cbOutsideLogs.setSelected(true);
    hmCbs.put(FieldName.EXTERNAL_LOG_FILES, cbOutsideLogs);

    return createOutsidePathPanel(cbOutsideLogs, outsideLogs,
        "delete-outside-logs-msg");
  }

  private JPanel createOutsidePathPanel(JCheckBox cb, Set<String> paths,
      String msgKey)
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    panel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, getMsg(msgKey),
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
