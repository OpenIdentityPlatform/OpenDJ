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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.ServerDescriptor;
import org.opends.admin.ads.SuffixDescriptor;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

/**
 * This class is used to provide a data model for the list of suffixes that
 * we have to replicate on the new server.
 */
public class SuffixesToReplicatePanel extends QuickSetupStepPanel
implements Comparator<SuffixDescriptor>
{
  private static final long serialVersionUID = -8051367953737385327L;
  private Component lastFocusComponent;
  private UserData defaultUserData;
  private TreeSet<SuffixDescriptor> orderedSuffixes =
    new TreeSet<SuffixDescriptor>(this);
  private HashMap<String, JCheckBox> hmCheckBoxes =
    new HashMap<String, JCheckBox>();
  private Set<JEditorPane> suffixLabels = new HashSet<JEditorPane>();

  private JRadioButton rbCreateNewSuffix;
  private JRadioButton rbReplicate;
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
    this.defaultUserData = application.getUserData();
    createComponents();
    addFocusListeners();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.SUFFIXES_TO_REPLICATE_OPTIONS)
    {
      if (rbCreateNewSuffix.isSelected())
      {
        value = SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY;
      }
      else
      {
        value =
          SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
      }
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
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    if (fieldName == FieldName.SUFFIXES_TO_REPLICATE)
    {
      UIFactory.TextStyle style;
      if (invalid)
      {
        style = UIFactory.TextStyle.SECONDARY_FIELD_INVALID;
      } else
      {
        style = UIFactory.TextStyle.SECONDARY_FIELD_VALID;
      }

      UIFactory.setTextStyle(rbReplicate, style);
    }
  }

  /**
   * {@inheritDoc}
   */
  public int compare(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    int result = 0;
    result = compareSuffixDN(desc1, desc2);
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
    panel.add(rbCreateNewSuffix, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON;
    panel.add(rbReplicate, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_SUBPANEL_SUBORDINATE;

    // Add the checkboxes
    checkBoxPanel = new JPanel(new GridBagLayout());
    checkBoxPanel.setOpaque(false);
    gbc.insets.top = 0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    scroll = new JScrollPane(checkBoxPanel);
    scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setViewportBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);

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
  protected String getInstructions()
  {
    return getMsg("suffixes-to-replicate-panel-instructions");
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle()
  {
    return getMsg("suffixes-to-replicate-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data)
  {
    TreeSet<SuffixDescriptor> array = orderSuffixes(
        data.getSuffixesToReplicateOptions().getAvailableSuffixes());

    if (!array.equals(orderedSuffixes))
    {
      HashMap<String, Boolean> hmOldValues = new HashMap<String, Boolean>();
      for (String id : hmCheckBoxes.keySet())
      {
        hmOldValues.put(id, hmCheckBoxes.get(id).isSelected());
      }
      orderedSuffixes.clear();
      for (SuffixDescriptor suffix : array)
      {
        if (!Utils.areDnsEqual(suffix.getDN(),
            ADSContext.getAdministrationSuffixDN()))
        {
          orderedSuffixes.add(suffix);
        }
      }
      hmCheckBoxes.clear();
      for (SuffixDescriptor suffix : orderedSuffixes)
      {
        JCheckBox cb = UIFactory.makeJCheckBox(suffix.getDN(),
            getMsg("suffixes-to-replicate-dn-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
        cb.setOpaque(false);
        Boolean v = hmOldValues.get(suffix.getId());
        if (v != null)
        {
          cb.setSelected(v);
        }
        cb.addActionListener(new ActionListener()
        {
          public void actionPerformed(ActionEvent ev)
          {
            if (((JCheckBox)ev.getSource()).isSelected())
            {
              rbReplicate.setSelected(true);
            }
          }
        });
        hmCheckBoxes.put(suffix.getId(), cb);
      }
      populateCheckBoxPanel();
    }
    boolean display = orderedSuffixes.size() > 0;

    noSuffixLabel.setVisible(!display);
    labelGlue.setVisible(!display);
    scroll.setVisible(display);

    checkEnablingState();
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

  /**
   * Creates the components of this panel.
   */
  private void createComponents()
  {
    ButtonGroup buttonGroup = new ButtonGroup();
    rbCreateNewSuffix =
      UIFactory.makeJRadioButton(getMsg("create-new-suffix-label"),
          getMsg("create-new-suffix-tooltip"),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbCreateNewSuffix.setOpaque(false);
    rbReplicate =
      UIFactory.makeJRadioButton(getMsg("replicate-with-suffixes-label"),
          getMsg("replicate-with-suffixes-tooltip"),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbReplicate.setOpaque(false);
    buttonGroup.add(rbCreateNewSuffix);
    buttonGroup.add(rbReplicate);

    SuffixesToReplicateOptions.Type type =
      defaultUserData.getSuffixesToReplicateOptions().getType();
    rbCreateNewSuffix.setSelected(type ==
      SuffixesToReplicateOptions.Type.NEW_SUFFIX_IN_TOPOLOGY);
    rbReplicate.setSelected(type ==
      SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES);

    noSuffixLabel = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
        getMsg("suffix-list-empty"),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        checkEnablingState();
      }
    };
    rbCreateNewSuffix.addActionListener(l);
    rbReplicate.addActionListener(l);
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addFocusListeners()
  {
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
    rbReplicate.addFocusListener(l);
    rbCreateNewSuffix.addFocusListener(l);

    lastFocusComponent = rbReplicate;
  }

  private void populateCheckBoxPanel()
  {
    checkBoxPanel.removeAll();
    suffixLabels.clear();
    GridBagConstraints gbc = new GridBagConstraints();
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
      checkBoxPanel.add(cb, gbc);
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      JEditorPane l = UIFactory.makeTextPane(getSuffixString(suffix),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);
      l.setOpaque(false);
      suffixLabels.add(l);

      /* Use a prototype label to get the additional insets */
      JEditorPane proto = UIFactory.makeTextPane(suffix.getDN(),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);

      gbc.insets.top += Math.abs(cb.getPreferredSize().height -
          proto.getPreferredSize().height) / 2;
      checkBoxPanel.add(l, gbc);
      first = false;
    }
    addVerticalGlue(checkBoxPanel);
  }

  private String getSuffixString(SuffixDescriptor desc)
  {
    TreeSet<String> replicaDisplays = new TreeSet<String>();
    for (ReplicaDescriptor rep: desc.getReplicas())
    {
      replicaDisplays.add(getReplicaDisplay(rep));
    }
    StringBuilder buf = new StringBuilder();
    for (String display: replicaDisplays)
    {
      if (buf.length() > 0)
      {
        buf.append("\n");
      }
      buf.append(display);
    }
    return buf.toString();
  }

  private String getReplicaDisplay(ReplicaDescriptor replica)
  {
    String display;

    ServerDescriptor server = replica.getServer();

    String serverDisplay = server.getHostPort(true);

    int nEntries = replica.getEntries();

    if (nEntries > 0)
    {
      String[] args = {serverDisplay, String.valueOf(nEntries)};
      display = getMsg("suffix-list-replica-display-entries", args);
    }
    else if (nEntries == 0)
    {
      String[] arg = {serverDisplay};
      display = getMsg("suffix-list-replica-display-no-entries", arg);
    }
    else
    {
      String[] arg = {serverDisplay};
      display = getMsg("suffix-list-replica-display-entries-not-available",
          arg);
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

  private void checkEnablingState()
  {
    boolean enable = rbReplicate.isSelected();
    for (JCheckBox cb : hmCheckBoxes.values())
    {
      cb.setEnabled(enable);
    }

    for (JEditorPane p : suffixLabels)
    {
      p.setEnabled(enable);
    }

    noSuffixLabel.setEnabled(enable);
  }
}

