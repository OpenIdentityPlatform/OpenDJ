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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
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
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

import org.opends.admin.ads.ServerDescriptor;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;

/**
 * This class is used to provide a data model for the list of servers for which
 * we must provide a replication port.
 */
public class RemoteReplicationPortsPanel extends QuickSetupStepPanel
implements Comparator<ServerDescriptor>
{
  private static final long serialVersionUID = -3742350600617826375L;
  private Component lastFocusComponent;
  private HashMap<String, JLabel> hmLabels =
    new HashMap<String, JLabel>();
  private HashMap<String, JTextComponent> hmFields =
    new HashMap<String, JTextComponent>();
  private HashMap<String, JCheckBox> hmCbs =
    new HashMap<String, JCheckBox>();
  private JScrollPane scroll;
  private JPanel fieldsPanel;
  private TreeSet<ServerDescriptor> orderedServers =
    new TreeSet<ServerDescriptor>(this);
  //The display of the server the user provided in the replication options
  // panel
  private String serverToConnectDisplay = null;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public RemoteReplicationPortsPanel(GuiApplication application)
  {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.REMOTE_REPLICATION_PORT)
    {
      Map<String, String> hm = new HashMap<String, String>();
      for (String id : hmFields.keySet())
      {
        hm.put(id, hmFields.get(id).getText());
      }
      value = hm;
    }
    else if (fieldName == FieldName.REMOTE_REPLICATION_SECURE)
    {
      Map<String, Boolean> hm = new HashMap<String, Boolean>();
      for (String id : hmCbs.keySet())
      {
        hm.put(id, hmCbs.get(id).isSelected());
      }
      value = hm;
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
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
          boolean isValid = false;
          try
          {
            int replicationPort = Integer.parseInt(sPort);
            if ((replicationPort >= 1) &&
                (replicationPort <= 65535))
            {
              isValid = true;
            }
          }
          catch (Throwable t)
          {
          }
          if (!isValid)
          {
            UIFactory.setTextStyle(hmLabels.get(id),
              UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public int compare(ServerDescriptor desc1, ServerDescriptor desc2)
  {
    return desc1.getHostPort(true).compareTo(desc2.getHostPort(true));
  }

  /**
   * {@inheritDoc}
   */
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
    scroll = new JScrollPane(fieldsPanel);
    scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);

    panel.add(scroll, gbc);

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return INFO_REMOTE_REPLICATION_PORT_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_REMOTE_REPLICATION_PORT_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data)
  {
    TreeSet<ServerDescriptor> array = orderServers(
        data.getRemoteWithNoReplicationPort().keySet());
    AuthenticationData authData =
      data.getReplicationOptions().getAuthenticationData();
    String newServerDisplay;
    if (data != null)
    {
      newServerDisplay = authData.getHostName()+":"+authData.getPort();
    }
    else
    {
      newServerDisplay = "";
    }
    if (!array.equals(orderedServers) ||
        !newServerDisplay.equals(serverToConnectDisplay))
    {
      serverToConnectDisplay = newServerDisplay;
      /**
       * Adds the required focus listeners to the fields.
       */
      final FocusListener l = new FocusListener()
      {
        public void focusGained(FocusEvent e)
        {
          lastFocusComponent = e.getComponent();
        }

        public void focusLost(FocusEvent e)
        {
        }
      };
      lastFocusComponent = null;
      HashMap<String, String> hmOldValues = new HashMap<String, String>();
      for (String id : hmFields.keySet())
      {
        hmOldValues.put(id, hmFields.get(id).getText());
      }
      HashMap<String, Boolean> hmOldSecureValues =
        new HashMap<String, Boolean>();
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
        String serverDisplay;
        if (server.getHostPort(false).equalsIgnoreCase(serverToConnectDisplay))
        {
          serverDisplay = serverToConnectDisplay;
        }
        else
        {
          serverDisplay = server.getHostPort(true);
        }
        LabelFieldDescriptor desc = new LabelFieldDescriptor(
                Message.raw(serverDisplay),
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

  /**
   * {@inheritDoc}
   */
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

  private TreeSet<ServerDescriptor> orderServers(
  Set<ServerDescriptor> servers)
  {
    TreeSet<ServerDescriptor> ordered = new TreeSet<ServerDescriptor>(this);
    ordered.addAll(servers);

    return ordered;
  }
}


