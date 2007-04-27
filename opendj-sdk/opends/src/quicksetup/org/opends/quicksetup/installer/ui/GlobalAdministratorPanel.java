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

/**
 * This class is used to set the global administrator parameters.
 */
public class GlobalAdministratorPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = 4266485298770553875L;

  private UserData defaultUserData;

  private Component lastFocusComponent;

  private HashMap<FieldName, JLabel> hmLabels =
      new HashMap<FieldName, JLabel>();

  private HashMap<FieldName, JTextComponent> hmFields =
      new HashMap<FieldName, JTextComponent>();

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

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  protected String getInstructions()
  {
    return getMsg("global-administrator-panel-instructions");
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle()
  {
    return getMsg("global-administrator-panel-title");
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
  private String getDefaultValue(FieldName fieldName)
  {
    String value = null;
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

  /**
   * Creates the components and populates the Maps with them.
   */
  private void populateLabelAndFieldMaps()
  {
    HashMap<FieldName, LabelFieldDescriptor> hm =
        new HashMap<FieldName, LabelFieldDescriptor>();

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_UID, new LabelFieldDescriptor(
        getMsg("global-administrator-uid-label"),
        getMsg("global-administrator-uid-tooltip"),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.UID_FIELD_SIZE));

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_PWD, new LabelFieldDescriptor(
        getMsg("global-administrator-pwd-label"),
        getMsg("global-administrator-pwd-tooltip"),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.PASSWORD_FIELD_SIZE));

    hm.put(FieldName.GLOBAL_ADMINISTRATOR_PWD_CONFIRM,
        new LabelFieldDescriptor(
        getMsg("global-administrator-pwd-confirm-label"),
        getMsg("global-administrator-pwd-confirm-tooltip"),
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

    for (JTextComponent tf : hmFields.values())
    {
      tf.addFocusListener(l);
    }
    lastFocusComponent = getField(FieldName.GLOBAL_ADMINISTRATOR_PWD);
  }
}
