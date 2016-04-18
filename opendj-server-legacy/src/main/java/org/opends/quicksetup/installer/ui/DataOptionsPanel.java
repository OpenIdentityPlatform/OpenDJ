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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;
import java.util.List;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.server.tools.BackendTypeHelper;
import org.opends.server.tools.BackendTypeHelper.BackendTypeUIAdapter;

/**
 * This is the panel that contains the Data Options: the suffix dn, whether to
 * import data to the suffix or not, etc.
 */
public class DataOptionsPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 1815782841921928118L;

  private Component lastFocusComponent;
  private UserData defaultUserData;

  private HashMap<FieldName, JLabel> hmLabels = new HashMap<>();
  private HashMap<FieldName, JTextComponent> hmFields = new HashMap<>();
  private HashMap<NewSuffixOptions.Type, JRadioButton> hmRadioButtons = new HashMap<>();

  private JButton ldifBrowseButton;
  private JComboBox<BackendTypeUIAdapter> backendTypeComboBox;

  /**
   * Constructor of the panel.
   *
   * @param application
   *          Application represented by this panel the fields of the panel.
   */
  public DataOptionsPanel(GuiApplication application)
  {
    super(application);
    this.defaultUserData = application.getUserData();
    populateComponentMaps();
    createBackendTypeComboBox();
    addDocumentListeners();
    addFocusListeners();
    addActionListeners();
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    if (fieldName == FieldName.DATA_OPTIONS)
    {
      for (NewSuffixOptions.Type type : hmRadioButtons.keySet())
      {
        if (hmRadioButtons.get(type).isSelected())
        {
          return type;
        }
      }
    }
    else if (FieldName.BACKEND_TYPE == fieldName)
    {
      return ((BackendTypeUIAdapter) backendTypeComboBox.getSelectedItem()).getBackend();
    }
    else
    {
      final JTextComponent field = getField(fieldName);
      if (field != null)
      {
        return field.getText();
      }
    }

    return null;
  }

  @Override
  public void displayFieldInvalid(final FieldName fieldName, final boolean invalid)
  {
    final JLabel label = getLabel(fieldName);
    if (label != null)
    {
      final UIFactory.TextStyle style;

      if (fieldName != FieldName.DIRECTORY_BASE_DN)
      {
        style = invalid ? UIFactory.TextStyle.SECONDARY_FIELD_INVALID : UIFactory.TextStyle.SECONDARY_FIELD_VALID;
      }
      else
      {
        style = invalid ? UIFactory.TextStyle.PRIMARY_FIELD_INVALID : UIFactory.TextStyle.PRIMARY_FIELD_VALID;
      }

      UIFactory.setTextStyle(label, style);
    }
  }

  @Override
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    // Add the server location widgets
    addBackendTypeSection(panel, gbc);
    addBaseDNSection(panel, gbc);

    int h1 = getLabel(FieldName.DATA_OPTIONS).getPreferredSize().height;
    int h2 = getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY).getPreferredSize().height;
    int additionalInset = Math.abs(h2 - h1) / 2;
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD + additionalInset;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    panel.add(getLabel(FieldName.DATA_OPTIONS), gbc);

    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(createRadioButtonPanel(), gbc);

    addVerticalGlue(panel);

    return panel;
  }

  private void addBackendTypeSection(final JPanel panel, final GridBagConstraints gbc)
  {
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = 0;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(getLabel(FieldName.BACKEND_TYPE), gbc);

    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    auxPanel.add(backendTypeComboBox, gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    auxPanel.add(Box.createHorizontalGlue(), gbc);

    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(auxPanel, gbc);
  }

  private void addBaseDNSection(final JPanel panel, final GridBagConstraints gbc)
  {
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(getLabel(FieldName.DIRECTORY_BASE_DN), gbc);

    final JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(auxPanel, gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 0.0;
    auxPanel.add(getField(FieldName.DIRECTORY_BASE_DN), gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    auxPanel.add(Box.createHorizontalGlue(), gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = 0;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(Box.createHorizontalGlue(), gbc);

    gbc.insets.top = 3;
    gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    final JLabel noBaseDNLabel = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, INFO_NO_BASE_DN_INLINE_HELP.get(),
                                                      UIFactory.TextStyle.INLINE_HELP);
    panel.add(noBaseDNLabel, gbc);
  }

  /**
   * Returns and creates the radio buttons panel.
   *
   * @return the radio buttons panel.
   */
  private JPanel createRadioButtonPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    panel.setOpaque(false);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(getRadioButton(NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON;
    panel.add(getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY), gbc);
    panel.add(getRadioButton(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE), gbc);

    JPanel auxPanel = createBrowseButtonPanel(FieldName.LDIF_PATH, getLDIFBrowseButton());

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(auxPanel, gbc);

    gbc.insets.left = 0;
    panel.add(getRadioButton(NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA), gbc);

    auxPanel = createNumberEntriesPanel();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(auxPanel, gbc);

    return panel;
  }

  /**
   * Returns the number entries panel.
   *
   * @return the number entries panel.
   */
  private JPanel createNumberEntriesPanel()
  {
    JPanel panel;

    GridBagConstraints gbc = new GridBagConstraints();

    panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);
    gbc.gridwidth = 3;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 0.0;
    panel.add(getLabel(FieldName.NUMBER_ENTRIES), gbc);

    gbc.gridwidth--;
    gbc.weightx = 0.1;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    panel.add(getField(FieldName.NUMBER_ENTRIES), gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(Box.createHorizontalGlue(), gbc);

    return panel;
  }

  /**
   * Creates a panel with a field and a browse button.
   *
   * @param fieldName
   *          the field name of the field.
   * @param browseButton
   *          the browse button.
   * @return the created panel.
   */
  private JPanel createBrowseButtonPanel(FieldName fieldName, JButton browseButton)
  {
    return Utilities.createBrowseButtonPanel(getLabel(fieldName), getField(fieldName), browseButton);
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_DATA_OPTIONS_PANEL_INSTRUCTIONS.get();
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_DATA_OPTIONS_PANEL_TITLE.get();
  }

  @Override
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  /**
   * Returns the default value for the provided field Name.
   *
   * @param fieldName
   *          the field name for which we want to get the default value.
   * @return the default value for the provided field Name.
   */
  private String getDefaultValue(FieldName fieldName)
  {
    final NewSuffixOptions suffixOptions = defaultUserData.getNewSuffixOptions();
    switch (fieldName)
    {
    case DIRECTORY_BASE_DN:
      return firstElementOrNull(suffixOptions.getBaseDns());

    case LDIF_PATH:
      return firstElementOrNull(suffixOptions.getLDIFPaths());

    default:
      throw new IllegalArgumentException("Unknown field name: " + fieldName);
    }
  }

  private String firstElementOrNull(final List<String> list)
  {
    if (list != null && !list.isEmpty())
    {
      return list.get(0);
    }

    return null;
  }

  /** Creates the components and populates the Maps with them. */
  private void populateComponentMaps()
  {
    final HashMap<FieldName, LabelFieldDescriptor> hm = new HashMap<>();

    final LabelFieldDescriptor baseDNLabelDescriptor = new LabelFieldDescriptor(
        INFO_BASE_DN_LABEL.get(), INFO_BASE_DN_TOOLTIP.get(), LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.DN_FIELD_SIZE);
    hm.put(FieldName.DIRECTORY_BASE_DN, baseDNLabelDescriptor);

    final LabelFieldDescriptor importPathLabelDescriptor = new LabelFieldDescriptor(
        INFO_IMPORT_PATH_LABEL.get(), INFO_IMPORT_PATH_TOOLTIP.get(), LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY, UIFactory.PATH_FIELD_SIZE);
    hm.put(FieldName.LDIF_PATH, importPathLabelDescriptor);

    final LabelFieldDescriptor entryNumberLabelDescriptor = new LabelFieldDescriptor(
        INFO_NUMBER_ENTRIES_LABEL.get(), INFO_NUMBER_ENTRIES_TOOLTIP.get(), LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY, UIFactory.NUMBER_ENTRIES_FIELD_SIZE);
    hm.put(FieldName.NUMBER_ENTRIES, entryNumberLabelDescriptor);

    for (final FieldName fieldName : hm.keySet())
    {
      final LabelFieldDescriptor desc = hm.get(fieldName);
      final String defaultValue = fieldName == FieldName.NUMBER_ENTRIES ?
                                            Integer.toString(defaultUserData.getNewSuffixOptions().getNumberEntries())
                                          : getDefaultValue(fieldName);
      final JTextComponent field = UIFactory.makeJTextComponent(desc, defaultValue);
      final JLabel label = UIFactory.makeJLabel(desc);
      label.setLabelFor(field);
      hmFields.put(fieldName, field);
      hmLabels.put(fieldName, label);
    }

    final JLabel dataLabel = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, INFO_DIRECTORY_DATA_LABEL.get(),
                                                  UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    hmLabels.put(FieldName.DATA_OPTIONS, dataLabel);

    final JLabel backendTypeLabel = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON, INFO_BACKEND_TYPE_LABEL.get(),
                                                         UIFactory.TextStyle.PRIMARY_FIELD_VALID);
    hmLabels.put(FieldName.BACKEND_TYPE, backendTypeLabel);
    createDirectoryDataChoiceRadioButton(dataLabel);
    checkEnablingState();
  }

  private void createBackendTypeComboBox()
  {
    final BackendTypeHelper backendTypeHelper = new BackendTypeHelper();
    backendTypeComboBox = new JComboBox<>(backendTypeHelper.getBackendTypeUIAdaptors());
  }

  private void createDirectoryDataChoiceRadioButton(final JLabel dataLabel)
  {
    final JRadioButton createBaseEntryRB = UIFactory.makeJRadioButton(
        INFO_CREATE_BASE_ENTRY_LABEL.get(getDefaultValue(FieldName.DIRECTORY_BASE_DN)),
        INFO_CREATE_BASE_ENTRY_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.CREATE_BASE_ENTRY, createBaseEntryRB);

    final JRadioButton leaveDataBaseEmptyRB = UIFactory.makeJRadioButton(
        INFO_LEAVE_DATABASE_EMPTY_LABEL.get(),
        INFO_LEAVE_DATABASE_EMPTY_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY, leaveDataBaseEmptyRB);
    dataLabel.setLabelFor(leaveDataBaseEmptyRB);

    final JRadioButton importFileDataRB = UIFactory.makeJRadioButton(
        INFO_IMPORT_DATA_FROM_LDIF_LABEL.get(),
        INFO_IMPORT_DATA_FROM_LDIF_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE, importFileDataRB);

    final JRadioButton importGeneratedDataRB = UIFactory.makeJRadioButton(
        INFO_IMPORT_AUTOMATICALLY_GENERATED_LABEL.get(),
        INFO_IMPORT_AUTOMATICALLY_GENERATED_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA, importGeneratedDataRB);

    final NewSuffixOptions.Type defaultType = defaultUserData.getNewSuffixOptions().getType();
    final ButtonGroup buttonGroup = new ButtonGroup();
    for (NewSuffixOptions.Type type : hmRadioButtons.keySet())
    {
      final JRadioButton radioButton = hmRadioButtons.get(type);
      radioButton.setSelected(type == defaultType);
      buttonGroup.add(radioButton);
    }
  }

  private JButton getLDIFBrowseButton()
  {
    if (ldifBrowseButton == null)
    {
      ldifBrowseButton = UIFactory.makeJButton(INFO_BROWSE_BUTTON_LABEL.get(), INFO_BROWSE_BUTTON_TOOLTIP.get());

      final BrowseActionListener listener = new BrowseActionListener(
          getField(FieldName.LDIF_PATH), BrowseActionListener.BrowseType.OPEN_LDIF_FILE, getMainWindow());
      ldifBrowseButton.addActionListener(listener);
    }

    return ldifBrowseButton;
  }

  /** Adds all the required document listeners to the fields. */
  private void addDocumentListeners()
  {
    final DocumentListener docListener = new DocumentListener()
    {
      @Override
      public void changedUpdate(DocumentEvent ev)
      {
        final LocalizableMessage newLabel =
            INFO_CREATE_BASE_ENTRY_LABEL.get(getFieldValue(FieldName.DIRECTORY_BASE_DN));
        getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY).setText(newLabel.toString());
      }

      @Override
      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      @Override
      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    };

    getField(FieldName.DIRECTORY_BASE_DN).getDocument().addDocumentListener(docListener);
  }

  /** Adds the required focus listeners to the fields. */
  private void addFocusListeners()
  {
    final FocusListener focusListener = new FocusListener()
    {
      @Override
      public void focusGained(FocusEvent e)
      {
        lastFocusComponent = e.getComponent();
        if (lastFocusComponent == getField(FieldName.LDIF_PATH))
        {
          getRadioButton(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE).setSelected(true);
        }
        else if (lastFocusComponent == getField(FieldName.NUMBER_ENTRIES))
        {
          getRadioButton(NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA).setSelected(true);
        }
      }

      @Override
      public void focusLost(FocusEvent e)
      {
      }
    };

    for (JTextComponent tf : hmFields.values())
    {
      tf.addFocusListener(focusListener);
    }
    for (JRadioButton rb : hmRadioButtons.values())
    {
      rb.addFocusListener(focusListener);
    }
    getLDIFBrowseButton().addFocusListener(focusListener);

    lastFocusComponent = getField(FieldName.DIRECTORY_BASE_DN);
  }

  /** Adds the required focus listeners to the fields. */
  private void addActionListeners()
  {
    final ActionListener l = new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent e)
      {
        checkEnablingState();
      }
    };

    for (final JRadioButton radioButton : hmRadioButtons.values())
    {
      radioButton.addActionListener(l);
    }
  }

  /** Enables/disables the fields. */
  private void checkEnablingState()
  {
    boolean importLDIF = getRadioButton(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE).isSelected();
    boolean automaticData = getRadioButton(NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA).isSelected();

    getField(FieldName.LDIF_PATH).setEnabled(importLDIF);
    getLDIFBrowseButton().setEnabled(importLDIF);
    getField(FieldName.NUMBER_ENTRIES).setEnabled(automaticData);

    getLabel(FieldName.LDIF_PATH).setEnabled(importLDIF);
    getLabel(FieldName.NUMBER_ENTRIES).setEnabled(automaticData);
  }

  private JLabel getLabel(FieldName fieldName)
  {
    return hmLabels.get(fieldName);
  }

  private JTextComponent getField(FieldName fieldName)
  {
    return hmFields.get(fieldName);
  }

  private JRadioButton getRadioButton(NewSuffixOptions.Type type)
  {
    return hmRadioButtons.get(type);
  }
}
