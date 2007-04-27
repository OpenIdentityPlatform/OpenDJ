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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.event.BrowseActionListener;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.SecurityOptions;
import org.opends.quicksetup.UserData;

/**
 * This is the panel that contains the Server Settings: the port, the Directory
 * Manager DN, etc.
 *
 */
public class ServerSettingsPanel extends QuickSetupStepPanel
{
  private UserData defaultUserData;

  private Component lastFocusComponent;

  private JLabel lSecurity;

  private JButton secureAccessButton;

  private JButton browseButton;

  private boolean displayServerLocation;

  private boolean canUpdateSecurity;

  private SecurityOptions securityOptions;

  private HashMap<FieldName, JLabel> hmLabels =
      new HashMap<FieldName, JLabel>();

  private HashMap<FieldName, JTextComponent> hmFields =
      new HashMap<FieldName, JTextComponent>();

  private JTextComponent tfServerLocationParent;

  private JTextComponent tfServerLocationRelativePath;

  private JLabel lServerLocation;

  private SecurityOptionsDialog dlg;

  private static final long serialVersionUID = -15911406930993035L;

  /**
   * Constructor of the panel.
   * @param application Application this panel represents
   * the fields of the panel.
   */
  public ServerSettingsPanel(GuiApplication application)
  {
    super(application);
    this.defaultUserData = application.getUserData();
    this.displayServerLocation = isWebStart();
    canUpdateSecurity =
      org.opends.server.util.CertificateManager.mayUseCertificateManager();
    securityOptions = defaultUserData.getSecurityOptions();
    populateLabelAndFieldMaps();
    addFocusListeners();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.SERVER_LOCATION)
    {
      String parent = tfServerLocationParent.getText();
      String relative = tfServerLocationRelativePath.getText();
      if ((parent != null) && (parent.length() > 0))
      {
        value = parent;
      }
      if ((relative != null) && (relative.length() > 0))
      {
        if (value == null)
        {
          value = File.separator + relative;
        } else
        {
          value = value + File.separator + relative;
        }
      }

    }
    else if (fieldName == FieldName.SECURITY_OPTIONS)
    {
      value = securityOptions;
    }
    else
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

    FieldName[] fieldNames =
    {
        FieldName.SERVER_PORT,
        FieldName.SECURITY_OPTIONS,
        FieldName.DIRECTORY_MANAGER_DN,
        FieldName.DIRECTORY_MANAGER_PWD,
        FieldName.DIRECTORY_MANAGER_PWD_CONFIRM
    };

    JPanel auxPanel;
    // Add the server location widgets
    if (displayServerLocation)
    {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.top = 0;
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.NORTHWEST;
      panel.add(lServerLocation, gbc);

      gbc.anchor = GridBagConstraints.WEST;
      auxPanel = new JPanel(new GridBagLayout());
      auxPanel.setOpaque(false);
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets.top = 0;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      panel.add(auxPanel, gbc);

      gbc.gridwidth = 3;
      gbc.insets = UIFactory.getEmptyInsets();
      gbc.weightx = 0.7;
      auxPanel.add(tfServerLocationParent, gbc);

      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      auxPanel.add(UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
          File.separator, UIFactory.TextStyle.TEXTFIELD), gbc);

      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 0.3;
      auxPanel.add(tfServerLocationRelativePath, gbc);

      gbc.gridwidth = 3;
      gbc.anchor = GridBagConstraints.NORTHEAST;
      gbc.insets.top = UIFactory.TOP_INSET_BROWSE;
      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.NONE;
      auxPanel.add(getBrowseButton(), gbc);
    }

    // Add the other widgets
    for (FieldName fieldName : fieldNames) {
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.insets.left = 0;
      boolean isSecurityField = fieldName == FieldName.SECURITY_OPTIONS;

      int securityInsetsTop = Math.abs(
          getLDAPSecureAccessButton().getPreferredSize().height -
          getLabel(fieldName).getPreferredSize().height) / 2;

      if (isSecurityField)
      {
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top += securityInsetsTop;
      }
      else
      {
        gbc.anchor = GridBagConstraints.WEST;
      }
      panel.add(getLabel(fieldName), gbc);

      auxPanel = new JPanel(new GridBagLayout());
      auxPanel.setOpaque(false);
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets.top = UIFactory.TOP_INSET_PRIMARY_FIELD;
      gbc.insets.left = UIFactory.LEFT_INSET_PRIMARY_FIELD;
      gbc.gridwidth = GridBagConstraints.REMAINDER;

      panel.add(auxPanel, gbc);

      boolean isPortField = fieldName == FieldName.SERVER_PORT;
      gbc.insets = UIFactory.getEmptyInsets();
      if (isPortField || (isSecurityField && canUpdateSecurity))
      {
        gbc.gridwidth = 3;
      }
      else
      {
        gbc.gridwidth = GridBagConstraints.RELATIVE;
      }
      gbc.weightx = 0.0;
      if (isSecurityField)
      {
        gbc.insets.top = securityInsetsTop;
        if (canUpdateSecurity)
        {
          auxPanel.add(lSecurity, gbc);
        }
        else
        {
          auxPanel.add(UIFactory.makeJLabel(UIFactory.IconType.WARNING,
              getMsg("cannot-update-security-warning"),
              UIFactory.TextStyle.SECONDARY_FIELD_VALID), gbc);
        }
      }
      else
      {
        auxPanel.add(getField(fieldName), gbc);
      }

      if (isPortField)
      {
        JLabel l =
                UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
                        getPortHelpMessage(),
                        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
        auxPanel.add(l, gbc);
      }
      else if (isSecurityField && canUpdateSecurity)
      {
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        gbc.insets.left = UIFactory.LEFT_INSET_BROWSE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top = 0;
        auxPanel.add(getLDAPSecureAccessButton(), gbc);
      }
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      auxPanel.add(Box.createHorizontalGlue(), gbc);
    }
    addVerticalGlue(panel);
    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions()
  {
    if (Utils.isWebStart())
    {
      return getMsg("server-settings-panel-instructions-webstart");
    }
    else
    {
      return getMsg("server-settings-panel-instructions");
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle()
  {
    return getMsg("server-settings-panel-title");
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
    String value;
    value = null;
    switch (fieldName)
    {
    case SERVER_LOCATION:
      value = defaultUserData.getServerLocation();
      break;

    case SERVER_PORT:
      if (defaultUserData.getServerPort() > 0)
      {
        value = String.valueOf(defaultUserData.getServerPort());
      }
      else
      {
        value = "";
      }
      break;

    case DIRECTORY_MANAGER_DN:
      value = defaultUserData.getDirectoryManagerDn();
      break;

    case DIRECTORY_MANAGER_PWD:
      value = defaultUserData.getDirectoryManagerPwd();
      break;

    case DIRECTORY_MANAGER_PWD_CONFIRM:
      value = defaultUserData.getDirectoryManagerPwd();
      break;

    case SECURITY_OPTIONS:
      value = getSecurityOptionsString(defaultUserData.getSecurityOptions(),
          true);
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

    hm.put(FieldName.SERVER_PORT, new LabelFieldDescriptor(
        getMsg("server-port-label"), getMsg("server-port-tooltip"),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.PORT_FIELD_SIZE));

    hm.put(FieldName.SECURITY_OPTIONS, new LabelFieldDescriptor(
        getMsg("server-security-label"), getMsg("server-security-tooltip"),
        LabelFieldDescriptor.FieldType.READ_ONLY,
        LabelFieldDescriptor.LabelType.PRIMARY, 0));

    hm.put(FieldName.DIRECTORY_MANAGER_DN, new LabelFieldDescriptor(
        getMsg("server-directory-manager-dn-label"),
        getMsg("server-directory-manager-dn-tooltip"),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.DN_FIELD_SIZE));

    hm.put(FieldName.DIRECTORY_MANAGER_PWD, new LabelFieldDescriptor(
        getMsg("server-directory-manager-pwd-label"),
        getMsg("server-directory-manager-pwd-tooltip"),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.PASSWORD_FIELD_SIZE));

    hm.put(FieldName.DIRECTORY_MANAGER_PWD_CONFIRM,
        new LabelFieldDescriptor(
        getMsg("server-directory-manager-pwd-confirm-label"),
        getMsg("server-directory-manager-pwd-confirm-tooltip"),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.PRIMARY,
        UIFactory.PASSWORD_FIELD_SIZE));

    for (FieldName fieldName : hm.keySet())
    {
      LabelFieldDescriptor desc = hm.get(fieldName);
      String defaultValue = getDefaultValue(fieldName);

      JLabel label = UIFactory.makeJLabel(desc);

      if (fieldName != FieldName.SECURITY_OPTIONS)
      {
        JTextComponent field = UIFactory.makeJTextComponent(desc, defaultValue);
        hmFields.put(fieldName, field);
        label.setLabelFor(field);
      }
      else
      {
        lSecurity = UIFactory.makeJLabel(UIFactory.IconType.NO_ICON,
            defaultValue, UIFactory.TextStyle.SECONDARY_FIELD_VALID);
      }

      hmLabels.put(fieldName, label);
    }

    /* Create the elements for the location */
    LabelFieldDescriptor desc =
        new LabelFieldDescriptor(getMsg("server-location-label"),
            getMsg("server-location-parent-tooltip"),
            LabelFieldDescriptor.FieldType.TEXTFIELD,
            LabelFieldDescriptor.LabelType.PRIMARY, UIFactory.PATH_FIELD_SIZE);
    lServerLocation = UIFactory.makeJLabel(desc);
    tfServerLocationParent = UIFactory.makeJTextComponent(desc, "");
    lServerLocation.setLabelFor(tfServerLocationParent);

    desc =
        new LabelFieldDescriptor(getMsg("server-location-label"),
            getMsg("server-location-relative-tooltip"),
            LabelFieldDescriptor.FieldType.TEXTFIELD,
            LabelFieldDescriptor.LabelType.PRIMARY,
            UIFactory.RELATIVE_PATH_FIELD_SIZE);
    tfServerLocationRelativePath = UIFactory.makeJTextComponent(desc, "");
    String defaultPath = getDefaultValue(FieldName.SERVER_LOCATION);
    if (defaultPath != null)
    {
      int index = defaultPath.lastIndexOf(File.separator);
      if (index != -1)
      {
        String parent = defaultPath.substring(0, index);
        String relativeDir = defaultPath.substring(index + 1);

        tfServerLocationParent.setText(parent);
        tfServerLocationRelativePath.setText(relativeDir);
      }
    }
  }

  /**
   * Returns the browse button.
   * If it does not exist creates the browse button.
   * @return the browse button.
   */
  private JButton getBrowseButton()
  {
    if (browseButton == null)
    {
      browseButton =
          UIFactory.makeJButton(getMsg("browse-button-label"),
              getMsg("browse-button-tooltip"));

      BrowseActionListener l =
          new BrowseActionListener(tfServerLocationParent,
              BrowseActionListener.BrowseType.LOCATION_DIRECTORY,
              getMainWindow());
      browseButton.addActionListener(l);
    }
    return browseButton;
  }

  /**
   * Returns the configure secure access button.
   * If it does not exist creates the secure access button.
   * @return the secure access button.
   */
  private JButton getLDAPSecureAccessButton()
  {
    if (secureAccessButton == null)
    {
      secureAccessButton =
          UIFactory.makeJButton(getMsg("server-security-button-label"),
              getMsg("server-security-button-tooltip"));

      secureAccessButton.addActionListener(new ActionListener()
      {
        public void actionPerformed(ActionEvent ev)
        {
          getConfigureSecureAccessDialog().display(securityOptions);
          if (!getConfigureSecureAccessDialog().isCancelled())
          {
            securityOptions =
              getConfigureSecureAccessDialog().getSecurityOptions();
            lSecurity.setText(getSecurityOptionsString(securityOptions, true));
          }
        }
      });
    }
    return secureAccessButton;
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
    getLDAPSecureAccessButton().addFocusListener(l);
    getBrowseButton().addFocusListener(l);
    if (Utils.isWebStart())
    {
      lastFocusComponent = tfServerLocationRelativePath;
    }
    else
    {
      lastFocusComponent = getField(FieldName.DIRECTORY_MANAGER_PWD);
    }
  }

  /**
   * Returns the port help message that we display when we cannot use the
   * default port (389).
   * @return the port help message that we display when we cannot use the
   * default port (389).
   */
  private String getPortHelpMessage()
  {
    String s = "";
    if (defaultUserData.getServerPort() != 389)
    {
      s = getMsg("cannot-use-default-port");
    }
    return s;
  }

  private SecurityOptionsDialog getConfigureSecureAccessDialog()
  {
    if (dlg == null)
    {
      dlg = new SecurityOptionsDialog((JFrame)getMainWindow(), securityOptions);
      dlg.setModal(true);
    }
    return dlg;
  }
}
