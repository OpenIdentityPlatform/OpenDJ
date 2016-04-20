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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;

import org.opends.admin.ads.ServerDescriptor;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.server.types.HostPort;

/**
 * This class is used to provide a data model for the list of servers for which
 * we must provide a replication port.
 */
public class RemoteReplicationPortsPanel extends QuickSetupStepPanel
implements Comparator<ServerDescriptor>
{
  private static final long serialVersionUID = -3742350600617826375L;
  private Component lastFocusComponent;
  private HashMap<String, JLabel> hmLabels = new HashMap<>();
  private HashMap<String, JTextComponent> hmFields = new HashMap<>();
  private HashMap<String, JCheckBox> hmCbs = new HashMap<>();
  private JScrollPane scroll;
  private JPanel fieldsPanel;
  private TreeSet<ServerDescriptor> orderedServers = new TreeSet<>();
  /** The display of the server the user provided in the replication options panel. */
  private HostPort serverToConnectDisplay;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public RemoteReplicationPortsPanel(GuiApplication application)
  {
    super(application);
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.REMOTE_REPLICATION_PORT)
    {
      Map<String, String> hm = new HashMap<>();
      for (String id : hmFields.keySet())
      {
        hm.put(id, hmFields.get(id).getText());
      }
      value = hm;
    }
    else if (fieldName == FieldName.REMOTE_REPLICATION_SECURE)
    {
      Map<String, Boolean> hm = new HashMap<>();
      for (String id : hmCbs.keySet())
      {
        hm.put(id, hmCbs.get(id).isSelected());
      }
      value = hm;
    }
    return value;
  }

  @Override
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    if (fieldName == FieldName.REMOTE_REPLICATION_PORT)
    {
      for (String id : hmLabels.keySet())
      {
        UIFactory.setTextStyle(hmLabels.get(id),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
      }
      if (invalid)
      {
        for (String id : hmLabels.keySet())
        {
          String sPort = hmFields.get(id).getText();
          if (!isValid(sPort))
          {
            UIFactory.setTextStyle(hmLabels.get(id),
              UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
          }
        }
      }
    }
  }

  private boolean isValid(String sPort)
  {
    try
    {
      int port = Integer.parseInt(sPort);
      if (port >= 1 && port <= 65535)
      {
        return true;
      }
    }
    catch (Throwable t)
    {
    }
    return false;
  }

  @Override
  protected boolean requiresScroll()
  {
    return false;
  }

  @Override
  public int compare(ServerDescriptor desc1, ServerDescriptor desc2)
  {
    return desc1.getHostPort(true).toString().compareTo(desc2.getHostPort(true).toString());
  }

  @Override
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    fieldsPanel = new JPanel(new GridBagLayout());
    fieldsPanel.setOpaque(false);
    scroll = UIFactory.createBorderLessScrollBar(fieldsPanel);

    panel.add(scroll, gbc);

    return panel;
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_REMOTE_REPLICATION_PORT_INSTRUCTIONS.get();
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_REMOTE_REPLICATION_PORT_TITLE.get();
  }

  @Override
  public void beginDisplay(UserData data)
  {
    TreeSet<ServerDescriptor> array = orderServers(
        data.getRemoteWithNoReplicationPort().keySet());
    AuthenticationData authData =
      data.getReplicationOptions().getAuthenticationData();
    HostPort newServerDisplay = authData != null ? authData.getHostPort() : new HostPort(null, 0);
    if (!array.equals(orderedServers) ||
        !newServerDisplay.equals(serverToConnectDisplay))
    {
      serverToConnectDisplay = newServerDisplay;
      // Adds the required focus listeners to the fields.
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
      lastFocusComponent = null;
      HashMap<String, String> hmOldValues = new HashMap<>();
      for (String id : hmFields.keySet())
      {
        hmOldValues.put(id, hmFields.get(id).getText());
      }
      HashMap<String, Boolean> hmOldSecureValues = new HashMap<>();
      for (String id : hmCbs.keySet())
      {
        hmOldSecureValues.put(id, hmCbs.get(id).isSelected());
      }
      orderedServers.clear();
      orderedServers.addAll(array);
      hmFields.clear();
      hmCbs.clear();
      hmLabels.clear();
      for (ServerDescriptor server : orderedServers)
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
        LabelFieldDescriptor desc = new LabelFieldDescriptor(
                LocalizableMessage.raw(serverDisplay.toString()),
                INFO_REPLICATION_PORT_TOOLTIP.get(),
                LabelFieldDescriptor.FieldType.TEXTFIELD,
                LabelFieldDescriptor.LabelType.PRIMARY,
                UIFactory.PORT_FIELD_SIZE);
        AuthenticationData auth =
          data.getRemoteWithNoReplicationPort().get(server);
        JTextComponent field = UIFactory.makeJTextComponent(desc,
            String.valueOf(auth.getPort()));
        String oldValue = hmOldValues.get(server.getId());
        if (oldValue != null)
        {
          field.setText(oldValue);
        }

        JLabel label = UIFactory.makeJLabel(desc);

        hmFields.put(server.getId(), field);
        label.setLabelFor(field);
        field.addFocusListener(l);
        if (lastFocusComponent == null)
        {
          lastFocusComponent = field;
        }

        hmLabels.put(server.getId(), label);

        JCheckBox cb = UIFactory.makeJCheckBox(
            INFO_SECURE_REPLICATION_LABEL.get(),
            INFO_SECURE_REPLICATION_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
        cb.setSelected(auth.useSecureConnection());
        Boolean oldSecureValue = hmOldSecureValues.get(server.getId());
        if (oldSecureValue != null)
        {
          cb.setSelected(oldSecureValue);
        }
        hmCbs.put(server.getId(), cb);
      }
      populateFieldsPanel();
    }
  }

  @Override
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  private void populateFieldsPanel()
  {
    fieldsPanel.removeAll();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    boolean first = true;
    for (ServerDescriptor server : orderedServers)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      if (!first)
      {
        gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
      }
      gbc.gridwidth = 4;
      fieldsPanel.add(hmLabels.get(server.getId()), gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.gridwidth--;
      fieldsPanel.add(hmFields.get(server.getId()), gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      fieldsPanel.add(hmCbs.get(server.getId()), gbc);
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1.0;
      fieldsPanel.add(Box.createHorizontalGlue(), gbc);
      first = false;
    }
    addVerticalGlue(fieldsPanel);
  }

  private TreeSet<ServerDescriptor> orderServers(Set<ServerDescriptor> servers)
  {
    TreeSet<ServerDescriptor> ordered = new TreeSet<>(this);
    ordered.addAll(servers);
    return ordered;
  }
}
