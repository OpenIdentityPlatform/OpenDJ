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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.installer.NewSuffixOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.UserData;

/**
 * This is the panel that contains the Data Options: the suffix dn, whether
 * to import data to the suffix or not, etc.
 *
 */
public class DataOptionsPanel extends QuickSetupStepPanel
{
  private Component lastFocusComponent;

  private static final long serialVersionUID = 1815782841921928118L;

  private UserData defaultUserData;

  private HashMap<FieldName, JLabel> hmLabels =
      new HashMap<FieldName, JLabel>();

  private HashMap<FieldName, JTextComponent> hmFields =
      new HashMap<FieldName, JTextComponent>();

  private HashMap<NewSuffixOptions.Type, JRadioButton> hmRadioButtons =
      new HashMap<NewSuffixOptions.Type, JRadioButton>();

  private JButton ldifBrowseButton;

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel
   * the fields of the panel.
   */
  public DataOptionsPanel(GuiApplication application)
  {
    super(application);
    this.defaultUserData = application.getUserData();
    populateComponentMaps();
    addDocumentListeners();
    addFocusListeners();
    addActionListeners();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.DATA_OPTIONS)
    {
      for (NewSuffixOptions.Type type : hmRadioButtons.keySet())
      {
        if (hmRadioButtons.get(type).isSelected())
        {
          value = type;
          break;
        }
      }

    } else
    {
      JTextComponent field = getField(fieldName);
      if (field != null)
      {
        value = field.getText();
      }
    }

    return value;
  }

  /**
   * {@inheritDoc}
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    JLabel label = getLabel(fieldName);
    if (label != null)
    {
      UIFactory.TextStyle style;

      if (fieldName != FieldName.DIRECTORY_BASE_DN)
      {
        if (invalid)
        {
          style = UIFactory.TextStyle.SECONDARY_FIELD_INVALID;
        } else
        {
          style = UIFactory.TextStyle.SECONDARY_FIELD_VALID;
        }

      } else
      {
        if (invalid)
        {
          style = UIFactory.TextStyle.PRIMARY_FIELD_INVALID;
        } else
        {
          style = UIFactory.TextStyle.PRIMARY_FIELD_VALID;
        }
      }
      UIFactory.setTextStyle(label, style);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    // Add the server location widgets
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.weightx = 0.0;
    gbc.insets.top = 0;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    panel.add(getLabel(FieldName.DIRECTORY_BASE_DN), gbc);

    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    auxPanel.add(getField(FieldName.DIRECTORY_BASE_DN), gbc);

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

    int h1 = getLabel(FieldName.DATA_OPTIONS).getPreferredSize().height;
    int h2 = getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY).
    getPreferredSize().height;
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

  /**
   * Returns and creates the radio buttons panel.
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
    panel.add(getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY), gbc);
    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON;
    panel.add(getRadioButton(NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY), gbc);
    panel.add(getRadioButton(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE), gbc);

    JPanel auxPanel =
        createBrowseButtonPanel(FieldName.LDIF_PATH, getLDIFBrowseButton());

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(auxPanel, gbc);

    gbc.insets.left = 0;
    panel.add(getRadioButton(
            NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA),
        gbc);

    auxPanel = createNumberEntriesPanel();

    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(auxPanel, gbc);

    return panel;
  }

  /**
   * Returns the number entries panel.
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
   * @param fieldName the field name of the field.
   * @param browseButton the browse button.
   * @return the created panel.
   */
  private JPanel createBrowseButtonPanel(FieldName fieldName,
      JButton browseButton)
  {
    return Utilities.createBrowseButtonPanel(
            getLabel(fieldName),
            getField(fieldName),
            browseButton);
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return INFO_DATA_OPTIONS_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_DATA_OPTIONS_PANEL_TITLE.get();
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
   * Returns the default value for the provided field Name.
   * @param fieldName the field name for which we want to get the default
   * value.
   * @return the default value for the provided field Name.
   */
  private Object getDefaultValue(FieldName fieldName)
  {
    Object value;
    switch (fieldName)
    {
    case DIRECTORY_BASE_DN:
      LinkedList<String> defaults =
        defaultUserData.getNewSuffixOptions().getBaseDns();
      if ((defaults != null) && !defaults.isEmpty())
      {
        value = defaults.getFirst();
      }
      else
      {
        value = null;
      }
      break;

    case DATA_OPTIONS:
      value = defaultUserData.getNewSuffixOptions().getType();
      break;

    case LDIF_PATH:
      defaults =
        defaultUserData.getNewSuffixOptions().getLDIFPaths();
      if ((defaults != null) && !defaults.isEmpty())
      {
        value = defaults.getFirst();
      }
      else
      {
        value = null;
      }
      break;

    case NUMBER_ENTRIES:
      value = defaultUserData.getNewSuffixOptions().getNumberEntries();
      break;

    default:
      throw new IllegalArgumentException("Unknown field name: " +
          fieldName);
    }

    return value;
  }

  /**
   * Returns the default string value for the provided field Name.
   * @param fieldName the field name for which we want to get the default
   * string value.
   * @return the default value for the provided field Name.
   */
  private String getDefaultStringValue(FieldName fieldName)
  {
    String value = null;

    Object v = getDefaultValue(fieldName);
    if (v != null)
    {
      if (v instanceof String)
      {
        value = (String) v;
      } else
      {
        value = String.valueOf(v);
      }
    }
    return value;
  }

  /**
   * Creates the components and populates the Maps with them.
   */
  private void populateComponentMaps()
  {
    HashMap<FieldName, LabelFieldDescriptor> hm =
        new HashMap<FieldName, LabelFieldDescriptor>();

    hm.put(FieldName.DIRECTORY_BASE_DN, new LabelFieldDescriptor(
        INFO_BASE_DN_LABEL.get(), INFO_BASE_DN_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.DN_FIELD_SIZE));

    hm.put(FieldName.LDIF_PATH, new LabelFieldDescriptor(
        INFO_IMPORT_PATH_LABEL.get(), INFO_IMPORT_PATH_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY, UIFactory.PATH_FIELD_SIZE));

    hm.put(FieldName.NUMBER_ENTRIES, new LabelFieldDescriptor(
        INFO_NUMBER_ENTRIES_LABEL.get(), INFO_NUMBER_ENTRIES_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY,
        UIFactory.NUMBER_ENTRIES_FIELD_SIZE));

    for (FieldName fieldName : hm.keySet())
    {
      JTextComponent field;
      LabelFieldDescriptor desc = hm.get(fieldName);

      String defaultValue = getDefaultStringValue(fieldName);
      field = UIFactory.makeJTextComponent(desc, defaultValue);

      hmFields.put(fieldName, field);

      JLabel l = UIFactory.makeJLabel(desc);

      l.setLabelFor(field);

      hmLabels.put(fieldName, l);
    }

    JLabel dataLabel =
        UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            INFO_DIRECTORY_DATA_LABEL.get(),
            UIFactory.TextStyle.PRIMARY_FIELD_VALID);

    hmLabels.put(FieldName.DATA_OPTIONS, dataLabel);

    JRadioButton rb =
        UIFactory.makeJRadioButton(INFO_CREATE_BASE_ENTRY_LABEL.get(
                getDefaultStringValue(FieldName.DIRECTORY_BASE_DN)),
            INFO_CREATE_BASE_ENTRY_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.CREATE_BASE_ENTRY, rb);

    dataLabel.setLabelFor(rb);

    rb =
        UIFactory.makeJRadioButton(INFO_LEAVE_DATABASE_EMPTY_LABEL.get(),
            INFO_LEAVE_DATABASE_EMPTY_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.LEAVE_DATABASE_EMPTY, rb);

    rb =
        UIFactory.makeJRadioButton(INFO_IMPORT_DATA_FROM_LDIF_LABEL.get(),
            INFO_IMPORT_DATA_FROM_LDIF_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons.put(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE, rb);

    rb =
        UIFactory.makeJRadioButton(
            INFO_IMPORT_AUTOMATICALLY_GENERATED_LABEL.get(),
            INFO_IMPORT_AUTOMATICALLY_GENERATED_TOOLTIP.get(),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    hmRadioButtons
        .put(NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA, rb);

    NewSuffixOptions.Type defaultType =
        (NewSuffixOptions.Type) getDefaultValue(FieldName.DATA_OPTIONS);

    ButtonGroup buttonGroup = new ButtonGroup();
    for (NewSuffixOptions.Type type : hmRadioButtons.keySet())
    {
      rb = hmRadioButtons.get(type);
      rb.setSelected(type == defaultType);
      buttonGroup.add(rb);
    }
    checkEnablingState();
  }

  /**
   * Returns the browse button to browse LDIF files.
   * If it does not exist creates the browse button to browse LDIF files.
   * @return the browse button to browse LDIF files.
   */
  private JButton getLDIFBrowseButton()
  {
    if (ldifBrowseButton == null)
    {
      ldifBrowseButton =
          UIFactory.makeJButton(INFO_BROWSE_BUTTON_LABEL.get(),
              INFO_BROWSE_BUTTON_TOOLTIP.get());

      BrowseActionListener l =
          new BrowseActionListener(getField(FieldName.LDIF_PATH),
              BrowseActionListener.BrowseType.OPEN_LDIF_FILE, getMainWindow());
      ldifBrowseButton.addActionListener(l);
    }
    return ldifBrowseButton;
  }


  /**
   * Adds all the required document listeners to the fields.
   */
  private void addDocumentListeners()
  {
    JTextComponent tf = getField(FieldName.DIRECTORY_BASE_DN);
    tf.getDocument().addDocumentListener(new DocumentListener()
    {
      public void changedUpdate(DocumentEvent ev)
      {
        Message newLabel = INFO_CREATE_BASE_ENTRY_LABEL.get(
                (String) getFieldValue(FieldName.DIRECTORY_BASE_DN));
        JRadioButton rb =
          getRadioButton(NewSuffixOptions.Type.CREATE_BASE_ENTRY);
        rb.setText(newLabel.toString());
      }

      public void insertUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }

      public void removeUpdate(DocumentEvent ev)
      {
        changedUpdate(ev);
      }
    });
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
        if (lastFocusComponent == getField(FieldName.LDIF_PATH))
        {
          getRadioButton(NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE).
          setSelected(true);
        }
        else if (lastFocusComponent == getField(FieldName.NUMBER_ENTRIES))
        {
          getRadioButton(
              NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA)
              .setSelected(true);
        }
      }

      public void focusLost(FocusEvent e)
      {
      }
    };

    for (JTextComponent tf : hmFields.values())
    {
      tf.addFocusListener(l);
    }
    for (JRadioButton rb : hmRadioButtons.values())
    {
      rb.addFocusListener(l);
    }
    getLDIFBrowseButton().addFocusListener(l);

    lastFocusComponent = getField(FieldName.DIRECTORY_BASE_DN);
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addActionListeners()
  {
    final ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent e)
      {
        checkEnablingState();
      }
    };
    for (JRadioButton rb : hmRadioButtons.values())
    {
      rb.addActionListener(l);
    }
  }

  /**
   * Enables/disables the fields.
   */
  private void checkEnablingState()
  {
    boolean importLDIF = getRadioButton(
        NewSuffixOptions.Type.IMPORT_FROM_LDIF_FILE).isSelected();
    boolean automaticData = getRadioButton(
        NewSuffixOptions.Type.IMPORT_AUTOMATICALLY_GENERATED_DATA).
        isSelected();

    getField(FieldName.LDIF_PATH).setEnabled(importLDIF);
    getLDIFBrowseButton().setEnabled(importLDIF);
    getField(FieldName.NUMBER_ENTRIES).setEnabled(automaticData);

    getLabel(FieldName.LDIF_PATH).setEnabled(importLDIF);
    getLabel(FieldName.NUMBER_ENTRIES).setEnabled(automaticData);
  }

  /**
   * Returns the label associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the JLabel.
   * @return the label associated with the given field name.
   */
  private JLabel getLabel(FieldName fieldName)
  {
    return hmLabels.get(fieldName);
  }

  /**
   * Returns the JTextComponent associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the
   * JTextComponent.
   * @return the JTextComponent associated with the given field name.
   */
  private JTextComponent getField(FieldName fieldName)
  {
    return hmFields.get(fieldName);
  }

  /**
   * Returns the JRadioButton associated with the given DataOptions.Type.
   * @param type the DataOptions.Type object for which we want to retrieve the
   * JRadioButton.
   * @return the JRadioButton associated with the given DataOptions.Type object.
   */
  private JRadioButton getRadioButton(NewSuffixOptions.Type type)
  {
    return hmRadioButtons.get(type);
  }
}
