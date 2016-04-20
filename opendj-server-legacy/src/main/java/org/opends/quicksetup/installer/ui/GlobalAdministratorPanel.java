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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup.installer.ui;

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;


import org.opends.quicksetup.UserData;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;

/** This class is used to set the global administrator parameters. */
public class GlobalAdministratorPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 4266485298770553875L;

  private UserData defaultUserData;

  private Component lastFocusComponent;

  private HashMap<FieldName, JLabel> hmLabels = new HashMap<>();
  private HashMap<FieldName, JTextComponent> hmFields = new HashMap<>();

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public GlobalAdministratorPanel(GuiApplication application)
  {
    super(application);
    this.defaultUserData = application.getUserData();
    populateLabelAndFieldMaps();
    addFocusListeners();
  }

  @Override
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;
    JTextComponent field = getField(fieldName);
    if (field != null)
    {
      value = field.getText();
    }
    return value;
  }

  @Override
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    JLabel label = getLabel(fieldName);
    if (label != null)
    {
      if (invalid)
      {
        UIFactory.setTextStyle(label,
            UIFactory.TextStyle.PRIMARY_FIELD_INVALID);
      } else
      {
        UIFactory
            .setTextStyle(label, UIFactory.TextStyle.PRIMARY_FIELD_VALID);
      }
    }
  }

  @Override
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();

    // Add the server location widgets
    FieldName[] fields =
    {
      FieldName.GLOBAL_ADMINISTRATOR_UID,
      FieldName.GLOBAL_ADMINISTRATOR_PWD,
      FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM
    };

    gbc.insets = UIFactory.getEmptyInsets();
    for (int i=0; i<fields.length; i++)
    {
      if (i != 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
      }
      else
      {
        gbc.insets.top = 0;
      }
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.WEST;
      panel.add(getLabel(fields[i]), gbc);

      JPanel auxPanel = new JPanel(new GridBagLayout());
      auxPanel.setOpaque(false);
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 0.0;
      auxPanel.add(getField(fields[i]), gbc);

      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      auxPanel.add(Box.createHorizontalGlue(), gbc);

      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = UIFactory.getEmptyInsets();
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      panel.add(auxPanel, gbc);
    }

    addVerticalGlue(panel);

    return panel;
  }

  @Override
  protected LocalizableMessage getInstructions()
  {
    return INFO_GLOBAL_ADMINISTRATOR_PANEL_INSTRUCTIONS.get();
  }

  @Override
  protected LocalizableMessage getTitle()
  {
    return INFO_GLOBAL_ADMINISTRATOR_PANEL_TITLE.get();
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
   * @param fieldName the field name for which we want to get the default
   * value.
   * @return the default value for the provided field Name.
   */
  private String getDefaultValue(FieldName fieldName)
  {
    String value;
    switch (fieldName)
    {
    case GLOBAL_ADMINISTRATOR_UID:
      value = defaultUserData.getGlobalAdministratorUID();
      break;

    case GLOBAL_ADMINISTRATOR_PWD:
      value = defaultUserData.getGlobalAdministratorPassword();
      break;

    case GLOBAL_ADMINISTRATOR_PWD_CONFIRM:
      value = defaultUserData.getGlobalAdministratorPassword();
      break;

    default:
      throw new IllegalArgumentException("Unknown field name: " +
          fieldName);
    }

    return value;
  }

  /** Creates the components and populates the Maps with them. */
  private void populateLabelAndFieldMaps()
  {
    HashMap<FieldName, LabelFieldDescriptor> hm = new HashMap<>();

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_UID, new LabelFieldDescriptor(
        INFO_GLOBAL_ADMINISTRATOR_UID_LABEL.get(),
        INFO_GLOBAL_ADMINISTRATOR_UID_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.UID_FIELD_SIZE));

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_PWD, new LabelFieldDescriptor(
        INFO_GLOBAL_ADMINISTRATOR_PWD_LABEL.get(),
        INFO_GLOBAL_ADMINISTRATOR_PWD_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.PASSWORD_FIELD_SIZE));

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM,
        new LabelFieldDescriptor(
        INFO_GLOBAL_ADMINISTRATOR_PWD_CONFIRM_LABEL.get(),
        INFO_GLOBAL_ADMINISTRATOR_PWD_CONFIRM_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.PRIMARY,
        UIFactory.PASSWORD_FIELD_SIZE));

    for (FieldName fieldName : hm.keySet())
    {
      LabelFieldDescriptor desc = hm.get(fieldName);
      String defaultValue = getDefaultValue(fieldName);
      JTextComponent field = UIFactory.makeJTextComponent(desc, defaultValue);
      JLabel label = UIFactory.makeJLabel(desc);

      hmFields.put(fieldName, field);
      label.setLabelFor(field);

      hmLabels.put(fieldName, label);
    }
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

  /** Adds the required focus listeners to the fields. */
  private void addFocusListeners()
  {
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

    for (JTextComponent tf : hmFields.values())
    {
      tf.addFocusListener(l);
    }
    lastFocusComponent = getField(FieldName.GLOBAL_ADMINISTRATOR_PWD);
  }
}
