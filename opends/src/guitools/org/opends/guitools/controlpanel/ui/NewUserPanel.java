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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * The panel used to create a new user.
 *
 */
public class NewUserPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -2450090053404111892L;
  private JLabel lFirstName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_FIRST_NAME_LABEL.get());
  private JLabel lLastName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_LAST_NAME_LABEL.get());
  private JLabel lCommonNames = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_COMMON_NAMES_LABEL.get());
  private JLabel lUserID = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_UID_LABEL.get());
  private JLabel lPassword = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_PASSWORD_LABEL.get());
  private JLabel lConfirmPassword = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_CONFIRM_PASSWORD_LABEL.get());
  private JLabel lEmail = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_EMAIL_LABEL.get());
  private JLabel lTelephoneNumber = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_TELEPHONE_NUMBER_LABEL.get());
  private JLabel lFaxNumber = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_FAX_NUMBER_LABEL.get());
  private JLabel lNamingAttribute = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_NAMING_ATTRIBUTE_LABEL.get());
  private JLabel lEntryDN = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_USER_ENTRY_DN_LABEL.get());

  private JLabel[] labels = {lFirstName, lLastName, lCommonNames, lUserID,
      lPassword, lConfirmPassword, lEmail, lTelephoneNumber, lFaxNumber,
      lNamingAttribute, lEntryDN
  };

  private JTextField firstName = Utilities.createLongTextField();
  private JTextField lastName = Utilities.createLongTextField();
  private JTextField commonName = Utilities.createLongTextField();
  private JTextField userID = Utilities.createLongTextField();
  private JPasswordField password = Utilities.createPasswordField();
  private JPasswordField confirmPassword = Utilities.createPasswordField(30);
  private JTextField eMail = Utilities.createLongTextField();
  private JTextField telephoneNumber = Utilities.createLongTextField();
  private JTextField faxNumber = Utilities.createLongTextField();
  private JComboBox namingAttribute = Utilities.createComboBox();
  private JLabel dn = Utilities.createDefaultLabel();

  Component[] comps = {firstName, lastName, commonName, userID,
      password, confirmPassword, eMail, telephoneNumber, faxNumber,
      namingAttribute, dn};

  private final JTextField[] NAMING_ATTRIBUTE_TEXTFIELDS =
  {commonName, firstName, lastName, userID};
  private final String[] NAMING_ATTRIBUTES = {"cn", "givenName", "sn", "uid"};

  /**
   * Default constructor.
   *
   */
  public NewUserPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    super.setParent(parentNode, controller);
    dn.setText(namingAttribute.getSelectedItem()+"=,"+parentNode.getDN());
    for (Component comp : comps)
    {
      if (comp instanceof JTextField)
      {
        ((JTextField)comp).setText("");
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_USER_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return firstName;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_NEW_USER_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  protected void checkSyntax(ArrayList<Message> errors)
  {
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }

    JTextField[] requiredFields = {lastName, commonName};
    Message[] msgs = {ERR_CTRL_PANEL_USER_LAST_NAME_REQUIRED.get(),
        ERR_CTRL_PANEL_USER_COMMON_NAME_REQUIRED.get()
    };
    for (int i=0; i<requiredFields.length; i++)
    {
      String v = requiredFields[i].getText().trim();
      if (v.length() == 0)
      {
        errors.add(msgs[i]);
      }
    }

    String attr = (String)namingAttribute.getSelectedItem();
    for (int i=0 ; i<NAMING_ATTRIBUTE_TEXTFIELDS.length; i++)
    {
      boolean isRequired = false;
      for (JTextField tf : requiredFields)
      {
        if (tf == NAMING_ATTRIBUTE_TEXTFIELDS[i])
        {
          isRequired = true;
          break;
        }
      }
      if (!isRequired)
      {
        if (attr.equalsIgnoreCase(NAMING_ATTRIBUTES[i]))
        {
          String value = NAMING_ATTRIBUTE_TEXTFIELDS[i].getText().trim();
          if (value.length() == 0)
          {
            errors.add(ERR_CTRL_PANEL_USER_NAMING_ATTRIBUTE_REQUIRED.get(attr));
          }
          break;
        }
      }
    }

    char[] pwd1 = password.getPassword();
    char[] pwd2 = confirmPassword.getPassword();
    String sPwd1 = new String(pwd1);
    String sPwd2 = new String(pwd2);
    if (!sPwd1.equals(sPwd2))
    {
      errors.add(ERR_CTRL_PANEL_PASSWORD_DO_NOT_MATCH.get());
    }

    if (errors.size() == 0)
    {
      try
      {
        getEntry();
      }
      catch (OpenDsException ode)
      {
        errors.add(ode.getMessageObject());
      }
      catch (IOException ioe)
      {
        // This should not occur
        throw new IllegalStateException("Unexpected error: "+ioe, ioe);
      }
    }
  }


  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    Utilities.setRequiredIcon(lLastName);
    Utilities.setRequiredIcon(lCommonNames);

    gbc.gridwidth = 2;
    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weighty = 0.0;
    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JLabel requiredLabel = createRequiredLabel();
    gbc.insets.bottom = 10;
    add(requiredLabel, gbc);

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.bottom = 0;

    Component[] inlineHelp = {null, null, null, null, null,
        null, null, null, null, null, null};

    for (int i=0 ; i< labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      add(labels[i], gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      if (comps[i] instanceof JComboBox)
      {
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
      }
      else
      {
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
      }
      add(comps[i], gbc);
      if (inlineHelp[i] != null)
      {
        gbc.insets.top = 3;
        gbc.gridy ++;
        add(inlineHelp[i], gbc);
      }
      gbc.insets.top = 10;
      gbc.gridy ++;
    }
    addBottomGlue(gbc);

    DocumentListener listener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        updateDNValue();
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        insertUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        insertUpdate(ev);
      }
    };
    JTextField[] toAddListener = {firstName, lastName, commonName, userID};
    for (JTextField tf : toAddListener)
    {
      tf.getDocument().addDocumentListener(listener);
    }

    DefaultComboBoxModel model = new DefaultComboBoxModel(NAMING_ATTRIBUTES);
    namingAttribute.setModel(model);
    namingAttribute.setSelectedItem(NAMING_ATTRIBUTES[0]);
    namingAttribute.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        updateDNValue();
      }
    });
  }

  /**
   * Updates the contents of DN value to reflect the data that the user
   * is providing.
   *
   */
  private void updateDNValue()
  {
    String attr = (String)namingAttribute.getSelectedItem();
    for (int i=0 ; i<NAMING_ATTRIBUTE_TEXTFIELDS.length; i++)
    {
      if (attr.equalsIgnoreCase(NAMING_ATTRIBUTES[i]))
      {
        String value = NAMING_ATTRIBUTE_TEXTFIELDS[i].getText().trim();
        String rdn = Utilities.getRDNString(attr, value);
        dn.setText(rdn+","+parentNode.getDN());
        break;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: "+dn.getText()+"\n");
    String[] attrNames = {"givenName", "sn", "cn", "uid", "userPassword",
        "mail", "telephoneNumber", "facsimileTelephoneNumber"};
    JTextField[] textFields = {firstName, lastName, commonName, userID,
        password, eMail, telephoneNumber, faxNumber};
    sb.append("objectclass: top\n");
    sb.append("objectclass: person\n");
    sb.append("objectclass: inetOrgPerson\n");
    for (int i=0; i<attrNames.length; i++)
    {
      String value = textFields[i].getText().trim();
      if (value.length() > 0)
      {
        sb.append(attrNames[i]+": "+value+"\n");
      }
    }
    return sb.toString();
  }
}
