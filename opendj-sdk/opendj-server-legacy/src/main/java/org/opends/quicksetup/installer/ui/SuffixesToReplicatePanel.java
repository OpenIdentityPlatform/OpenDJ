/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ReplicaDescriptor;
import org.opends.admin.ads.SuffixDescriptor;
import org.opends.quicksetup.Constants;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.SuffixesToReplicateOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.UIFactory.IconType;
import org.opends.quicksetup.util.Utils;
import org.opends.server.config.ConfigConstants;
import org.opends.server.tools.BackendTypeHelper;
import org.opends.server.tools.BackendTypeHelper.BackendTypeUIAdapter;

/**
 * This class is used to provide a data model for the list of suffixes that we
 * have to replicate on the new server.
 */
public class SuffixesToReplicatePanel extends QuickSetupStepPanel implements Comparator<SuffixDescriptor>
{
  private static final long serialVersionUID = -8051367953737385327L;

  private static final Insets SUFFIXES_TO_REPLICATE_INSETS = new Insets(4, 4, 4, 4);

  private final Set<SuffixDescriptor> orderedSuffixes = new TreeSet<>(this);
  private final Map<String, JCheckBox> hmCheckBoxes = new HashMap<>();
  private final Map<String, JComboBox<BackendTypeUIAdapter>> backendTypeComboBoxes = new HashMap<>();
  /**
   * The display of the server the user provided in the replication options
   * panel.
   */
  private String serverToConnectDisplay;

  private JLabel noSuffixLabel;
  private Component labelGlue;
  private JPanel checkBoxPanel;
  private JScrollPane scroll;

  /**
   * Constructor of the panel.
   *
   * @param application
   *          Application represented by this panel and used to initialize the
   *          fields of the panel.
   */
  public SuffixesToReplicatePanel(GuiApplication application)
  {
    super(application);
    createComponents();
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    if (fieldName == FieldName.SUFFIXES_TO_REPLICATE_OPTIONS)
    {
      return SuffixesToReplicateOptions.Type.REPLICATE_WITH_EXISTING_SUFFIXES;
    }
    else if (fieldName == FieldName.SUFFIXES_TO_REPLICATE)
    {
      return getSelectedSuffixes();
    }
    else if (fieldName == FieldName.SUFFIXES_TO_REPLICATE_BACKEND_TYPE)
    {
      return getSelectedSuffixBackendTypes();
    }

    return null;
  }

  private Set<SuffixDescriptor> getSelectedSuffixes()
  {
    Set<SuffixDescriptor> suffixes = new HashSet<>();
    for (SuffixDescriptor suffix : orderedSuffixes)
    {
      if (hmCheckBoxes.get(suffix.getId()).isSelected())
      {
        suffixes.add(suffix);
      }
    }
    return suffixes;
  }

  private Map<String, BackendTypeUIAdapter> getSelectedSuffixBackendTypes()
  {
    final Map<String, BackendTypeUIAdapter> backendTypes = new HashMap<>();
    for (SuffixDescriptor suffix : getSelectedSuffixes())
    {
      final String backendName = suffix.getReplicas().iterator().next().getBackendName();
      backendTypes.put(backendName, (BackendTypeUIAdapter) backendTypeComboBoxes.get(backendName).getSelectedItem());
    }
    return backendTypes;
  }

  @Override
  public int compare(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    int result = compareSuffixDN(desc1, desc2);
    if (result == 0)
    {
      result = compareSuffixStrings(desc1, desc2);
    }
    return result;
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
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_BACKGROUND;

    // Add the checkboxes
    checkBoxPanel = new JPanel(new GridBagLayout());
    checkBoxPanel.setOpaque(false);
    gbc.insets.top = 0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    scroll = UIFactory.createBorderLessScrollBar(checkBoxPanel);
    panel.add(scroll, gbc);

    gbc.insets.left = UIFactory.LEFT_INSET_SUBPANEL_SUBORDINATE;
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

  @Override
  protected boolean requiresScroll()
  {
    return false;
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_SUFFIXES_TO_REPLICATE_PANEL_INSTRUCTIONS.get();
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_SUFFIXES_TO_REPLICATE_PANEL_TITLE.get();
  }

  @Override
  public void beginDisplay(UserData data)
  {
    Set<SuffixDescriptor> array = orderSuffixes(data.getSuffixesToReplicateOptions().getAvailableSuffixes());
    AuthenticationData authData = data.getReplicationOptions().getAuthenticationData();
    String newServerDisplay;
    newServerDisplay = authData != null ? authData.getHostName() + ":" + authData.getPort() : "";

    if (!array.equals(orderedSuffixes) || !newServerDisplay.equals(serverToConnectDisplay))
    {
      serverToConnectDisplay = newServerDisplay;
      Map<String, Boolean> hmOldValues = new HashMap<>();
      for (String id : hmCheckBoxes.keySet())
      {
        hmOldValues.put(id, hmCheckBoxes.get(id).isSelected());
      }
      orderedSuffixes.clear();
      for (SuffixDescriptor suffix : array)
      {
        if (!Utils.areDnsEqual(suffix.getDN(), ADSContext.getAdministrationSuffixDN())
            && !Utils.areDnsEqual(suffix.getDN(), Constants.SCHEMA_DN)
            && !Utils.areDnsEqual(suffix.getDN(), Constants.REPLICATION_CHANGES_DN))
        {
          orderedSuffixes.add(suffix);
        }
      }
      hmCheckBoxes.clear();
      for (SuffixDescriptor suffix : orderedSuffixes)
      {
        JCheckBox cb = UIFactory.makeJCheckBox(LocalizableMessage.raw(suffix.getDN()),
                INFO_SUFFIXES_TO_REPLICATE_DN_TOOLTIP.get(), UIFactory.TextStyle.SECONDARY_FIELD_VALID);
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

    boolean display = !orderedSuffixes.isEmpty();
    noSuffixLabel.setVisible(!display);
    labelGlue.setVisible(!display);
    scroll.setVisible(display);
  }

  /** Creates the components of this panel. */
  private void createComponents()
  {
    noSuffixLabel = UIFactory.makeJLabel(
        UIFactory.IconType.NO_ICON, INFO_SUFFIX_LIST_EMPTY.get(), UIFactory.TextStyle.SECONDARY_FIELD_VALID);
  }

  private void populateCheckBoxPanel()
  {
    checkBoxPanel.removeAll();
    final GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = SUFFIXES_TO_REPLICATE_INSETS;
    gbc.gridy = 0;

    final Map<String, Set<SuffixDescriptor>> backendToSuffixes = getSuffixesForBackends();
    for (Map.Entry<String, Set<SuffixDescriptor>> backendData : backendToSuffixes.entrySet())
    {
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.gridwidth = 1;
      gbc.gridheight = 1;
      for (SuffixDescriptor suffix : backendData.getValue())
      {
        gbc.gridx = 0;
        final JCheckBox cb = hmCheckBoxes.get(suffix.getId());
        checkBoxPanel.add(cb, gbc);
        printReplicaTooltipButton(suffix, gbc);
        gbc.gridy++;
      }
      printBackendInformations(backendData, gbc);
      printSeparatorLine(gbc);
    }
    gbc.weighty = 1.0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.VERTICAL;
    checkBoxPanel.add(Box.createVerticalGlue(), gbc);
  }

  private Map<String, Set<SuffixDescriptor>> getSuffixesForBackends()
  {
    final Map<String, Set<SuffixDescriptor>> backendToSuffixes = new HashMap<>();
    for (SuffixDescriptor suffix : orderedSuffixes)
    {
      final String backendName = suffix.getReplicas().iterator().next().getBackendName();
      if (!backendToSuffixes.containsKey(backendName))
      {
        backendToSuffixes.put(backendName, new LinkedHashSet<SuffixDescriptor>());
      }
      backendToSuffixes.get(backendName).add(suffix);
    }

    return backendToSuffixes;
  }

  private void printReplicaTooltipButton(SuffixDescriptor suffix, GridBagConstraints gbc)
  {
    gbc.gridx++;
    String imageDesc = "<html>";
    for (ReplicaDescriptor replica : suffix.getReplicas())
    {
      imageDesc += getServerDisplay(replica) + "<br>";
    }
    final int entriesNb = suffix.getReplicas().iterator().next().getEntries();
    final LocalizableMessage entriesNbToPrint = getNumberOfEntriesMsg(entriesNb);
    imageDesc += entriesNbToPrint + "</html>";

    final JLabel helpReplicasTooltip = new JLabel();
    helpReplicasTooltip.setIcon(UIFactory.getImageIcon(IconType.HELP_MEDIUM));
    helpReplicasTooltip.setToolTipText(imageDesc);
    UIFactory.setTextStyle(helpReplicasTooltip, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    checkBoxPanel.add(helpReplicasTooltip, gbc);
  }

  private LocalizableMessage getNumberOfEntriesMsg(int nEntries)
  {
    if (nEntries > 0)
    {
      return INFO_SUFFIX_LIST_REPLICA_DISPLAY_ENTRIES.get(nEntries);
    }
    else if (nEntries == 0)
    {
      return INFO_SUFFIX_LIST_REPLICA_DISPLAY_NO_ENTRIES.get();
    }
    else
    {
      return INFO_SUFFIX_LIST_REPLICA_DISPLAY_ENTRIES_NOT_AVAILABLE.get();
    }
  }

  private void printBackendInformations(Map.Entry<String, Set<SuffixDescriptor>> backendData, GridBagConstraints gbc)
  {
    final int nbSuffixForBackend = backendData.getValue().size();
    gbc.gridy -= nbSuffixForBackend;
    printBackendNameText(backendData, gbc);
    printComboBoxForSuffix(backendData.getValue().iterator().next(), gbc);
    gbc.gridy += nbSuffixForBackend;
  }

  private void printSeparatorLine(GridBagConstraints gbc)
  {
    gbc.gridwidth = gbc.gridx;
    gbc.gridx = 0;
    checkBoxPanel.add(new JSeparator(SwingConstants.HORIZONTAL), gbc);
    gbc.gridy++;
  }

  private void printBackendNameText(Entry<String, Set<SuffixDescriptor>> backendData, GridBagConstraints gbc)
  {
    gbc.gridx++;
    final JEditorPane backendNameText = UIFactory.makeTextPane(
        LocalizableMessage.raw(backendData.getKey()), UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    backendNameText.setToolTipText(INFO_REPLICATED_SUFFIXES_BACKEND_NAME_TOOLTIP.get().toString());
    gbc.anchor = GridBagConstraints.CENTER;
    checkBoxPanel.add(backendNameText, gbc);
  }

  private void printComboBoxForSuffix(SuffixDescriptor suffix, GridBagConstraints gbc)
  {
    gbc.gridx++;
    gbc.anchor = GridBagConstraints.LINE_END;
    gbc.insets = UIFactory.getEmptyInsets();
    final ReplicaDescriptor backendData = suffix.getReplicas().iterator().next();
    final JComboBox<BackendTypeUIAdapter> backendTypeComboBox =
        new JComboBox<>(new BackendTypeHelper().getBackendTypeUIAdaptors());
    backendTypeComboBox.setToolTipText(INFO_REPLICATED_SUFFIXES_BACKEND_TYPE_TOOLTIP.get().toString());
    final Set<String> objectClasses = backendData.getObjectClasses();
    backendTypeComboBox.setSelectedItem(getBackendTypeFromObjectClasses(objectClasses));
    backendTypeComboBoxes.put(backendData.getBackendName(), backendTypeComboBox);
    checkBoxPanel.add(backendTypeComboBox, gbc);
    gbc.insets = SUFFIXES_TO_REPLICATE_INSETS;
  }

  /**
   * Returns the concrete backend type corresponding to the provided object
   * classes. If the backend is not found, returns the default backend of this
   * server configuration.
   *
   * @param objectClasses
   *          The set of object class with one should be a concrete backend
   *          type.
   * @return The concrete backend type corresponding to object classes or this
   *         server default one.
   */
  private BackendTypeUIAdapter getBackendTypeFromObjectClasses(Set<String> objectClasses)
  {
    for (String objectClass : objectClasses)
    {
      BackendTypeUIAdapter adapter =
          BackendTypeHelper.getBackendTypeAdapter(objectClass.replace(ConfigConstants.NAME_PREFIX_CFG, ""));
      if (adapter != null)
      {
        return adapter;
      }
    }

    return new BackendTypeHelper().getBackendTypeUIAdaptors()[0];
  }

  private String getSuffixString(SuffixDescriptor desc)
  {
    Set<String> replicaDisplays = new TreeSet<>();
    for (ReplicaDescriptor rep : desc.getReplicas())
    {
      replicaDisplays.add(getServerDisplay(rep));
    }
    return joinAsString("\n", replicaDisplays);
  }

  private String getServerDisplay(ReplicaDescriptor replica)
  {
    final boolean isServerToConnect = replica.getServer().getHostPort(false).equalsIgnoreCase(serverToConnectDisplay);
    return isServerToConnect ? serverToConnectDisplay : replica.getServer().getHostPort(true);
  }

  private Set<SuffixDescriptor> orderSuffixes(Set<SuffixDescriptor> suffixes)
  {
    Set<SuffixDescriptor> ordered = new TreeSet<>(this);
    ordered.addAll(suffixes);

    return ordered;
  }

  private int compareSuffixDN(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    return desc1.getDN().compareTo(desc2.getDN());
  }

  private int compareSuffixStrings(SuffixDescriptor desc1, SuffixDescriptor desc2)
  {
    return getSuffixString(desc1).compareTo(getSuffixString(desc2));
  }

}
