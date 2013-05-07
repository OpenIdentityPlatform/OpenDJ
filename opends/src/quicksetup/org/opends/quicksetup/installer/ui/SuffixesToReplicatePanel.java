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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup.installer.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;

import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This class is used to provide a data model for the list of suffixes that
 * we have to replicate on the new server.
 */
public class SuffixesToReplicatePanel extends QuickSetupStepPanel
implements Comparator<SuffixDescriptor>
{
  private static final long serialVersionUID = -8051367953737385327L;
  private TreeSet<SuffixDescriptor> orderedSuffixes =
    new TreeSet<SuffixDescriptor>(this);
  private HashMap<String, JCheckBox> hmCheckBoxes =
    new HashMap<String, JCheckBox>();
  // The display of the server the user provided in the replication options
  // panel
  private String serverToConnectDisplay = null;

  private JLabel noSuffixLabel;
  private Component labelGlue;
  private JPanel checkBoxPanel;
  private JScrollPane scroll;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public SuffixesToReplicatePanel(GuiApplication application)
  {
    super(application);
    createComponents();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.SUFFIXES_TO_REPLICATE_OPTIONS)
    {
      value = SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
    }
    else if (fieldName == FieldName.SUFFIXES_TO_REPLICATE)
    {
      Set<SuffixDescriptor> suffixes = new HashSet<SuffixDescriptor>();
      for (SuffixDescriptor suffix:orderedSuffixes)
      {
        if (hmCheckBoxes.get(suffix.getId()).isSelected())
        {
          suffixes.add(suffix);
        }
      }
      value = suffixes;
    }

    return value;
  }

  /**
   * {@inheritDoc}
   */
  public int compare(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    int result = compareSuffixDN(desc1, desc2);
    if (result == 0)
    {
      result = compareSuffixStrings(desc1, desc2);
    }
    return result;
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

    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_SUBPANEL_SUBORDINATE;

    // Add the checkboxes
    checkBoxPanel = new JPanel(new GridBagLayout());
    checkBoxPanel.setOpaque(false);
    gbc.insets.top = 0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    scroll = UIFactory.createBorderLessScrollBar(checkBoxPanel);

    panel.add(scroll, gbc);

    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.anchor = GridBagConstraints.NORTHEAST;
    panel.add(noSuffixLabel, gbc);
    noSuffixLabel.setVisible(false);

    labelGlue = Box.createVerticalGlue();
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.weighty = 1.0;
    panel.add(labelGlue, gbc);
    labelGlue.setVisible(false);

    return panel;
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
  protected Message getInstructions()
  {
    return INFO_SUFFIXES_TO_REPLICATE_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_SUFFIXES_TO_REPLICATE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data)
  {
    TreeSet<SuffixDescriptor> array = orderSuffixes(
        data.getSuffixesToReplicateOptions().getAvailableSuffixes());

    AuthenticationData authData =
      data.getReplicationOptions().getAuthenticationData();
    String newServerDisplay;
    if (authData != null)
    {
      newServerDisplay = authData.getHostName()+":"+authData.getPort();
    }
    else
    {
      newServerDisplay = "";
    }
    if (!array.equals(orderedSuffixes) ||
        !newServerDisplay.equals(serverToConnectDisplay))
    {
      serverToConnectDisplay = newServerDisplay;
      HashMap<String, Boolean> hmOldValues = new HashMap<String, Boolean>();
      for (String id : hmCheckBoxes.keySet())
      {
        hmOldValues.put(id, hmCheckBoxes.get(id).isSelected());
      }
      orderedSuffixes.clear();
      for (SuffixDescriptor suffix : array)
      {
        if (!Utils.areDnsEqual(suffix.getDN(),
            ADSContext.getAdministrationSuffixDN()) &&
            !Utils.areDnsEqual(suffix.getDN(), Constants.SCHEMA_DN) &&
            !Utils.areDnsEqual(suffix.getDN(),
                Constants.REPLICATION_CHANGES_DN))
        {
          orderedSuffixes.add(suffix);
        }
      }
      hmCheckBoxes.clear();
      for (SuffixDescriptor suffix : orderedSuffixes)
      {
        JCheckBox cb = UIFactory.makeJCheckBox(Message.raw(suffix.getDN()),
            INFO_SUFFIXES_TO_REPLICATE_DN_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
        cb.setOpaque(false);
        Boolean v = hmOldValues.get(suffix.getId());
        if (v != null)
        {
          cb.setSelected(v);
        }
        hmCheckBoxes.put(suffix.getId(), cb);
      }
      populateCheckBoxPanel();
    }
    boolean display = orderedSuffixes.size() > 0;

    noSuffixLabel.setVisible(!display);
    labelGlue.setVisible(!display);
    scroll.setVisible(display);
  }

  /**
   * Creates the components of this panel.
   */
  private void createComponents()
  {
    noSuffixLabel = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        INFO_SUFFIX_LIST_EMPTY.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
  }

  private void populateCheckBoxPanel()
  {
    checkBoxPanel.removeAll();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.NORTH;
    boolean first = true;
    for (SuffixDescriptor suffix : orderedSuffixes)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      if (!first)
      {
        gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
      }
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      JCheckBox cb = hmCheckBoxes.get(suffix.getId());
      cb.setVerticalAlignment(SwingConstants.TOP);
      gbc.gridx = 0;
      checkBoxPanel.add(cb, gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      JEditorPane l = UIFactory.makeTextPane(
              Message.raw(getSuffixString(suffix)),
              UIFactory.TextStyle.SECONDARY_FIELD_VALID);
      l.setOpaque(false);

      /* Use a prototype label to get the additional insets */
      JEditorPane proto = UIFactory.makeTextPane(
              Message.raw(suffix.getDN()),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);

      gbc.insets.top += Math.abs(cb.getPreferredSize().height -
          proto.getPreferredSize().height) / 2;
      gbc.gridx = 1;
      checkBoxPanel.add(l, gbc);
      first = false;
      gbc.gridy ++;
    }
    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.VERTICAL;
    checkBoxPanel.add(Box.createVerticalGlue(), gbc);
  }

  private String getSuffixString(SuffixDescriptor desc)
  {
    TreeSet<Message> replicaDisplays = new TreeSet<Message>();
    for (ReplicaDescriptor rep: desc.getReplicas())
    {
      replicaDisplays.add(getReplicaDisplay(rep));
    }
    MessageBuilder buf = new MessageBuilder();
    for (Message display: replicaDisplays)
    {
      if (buf.length() > 0)
      {
        buf.append("\n");
      }
      buf.append(display);
    }
    return buf.toString();
  }

  private Message getReplicaDisplay(ReplicaDescriptor replica)
  {
    Message display;

    ServerDescriptor server = replica.getServer();

    String serverDisplay;
    if (server.getHostPort(false).equalsIgnoreCase(serverToConnectDisplay))
    {
      serverDisplay = serverToConnectDisplay;
    }
    else
    {
      serverDisplay = server.getHostPort(true);
    }

    int nEntries = replica.getEntries();

    if (nEntries > 0)
    {
      display = INFO_SUFFIX_LIST_REPLICA_DISPLAY_ENTRIES.get(
              serverDisplay, String.valueOf(nEntries));
    }
    else if (nEntries == 0)
    {
      display = INFO_SUFFIX_LIST_REPLICA_DISPLAY_NO_ENTRIES.get(serverDisplay);
    }
    else
    {
      display = INFO_SUFFIX_LIST_REPLICA_DISPLAY_ENTRIES_NOT_AVAILABLE.get(
              serverDisplay);
    }

    return display;
  }

  private TreeSet<SuffixDescriptor> orderSuffixes(
      Set<SuffixDescriptor> suffixes)
  {
    TreeSet<SuffixDescriptor> ordered = new TreeSet<SuffixDescriptor>(this);
    ordered.addAll(suffixes);

    return ordered;
  }

  private int compareSuffixDN(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    return desc1.getDN().compareTo(desc2.getDN());
  }

  private int compareSuffixStrings(SuffixDescriptor desc1,
      SuffixDescriptor desc2)
  {
    return getSuffixString(desc1).compareTo(getSuffixString(desc2));
  }
}

