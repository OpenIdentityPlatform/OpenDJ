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
import java.io.IOException;
import java.util.ArrayList;import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * The panel used to create a new organizational unit.
 *
 */
public class NewOrganizationalUnitPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -7145648120019856161L;
  private JLabel lName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_NAME_LABEL.get());
  private JLabel lDescription = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_DESCRIPTION_LABEL.get());
  private JLabel lAddress = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_ADDRESS_LABEL.get());
  private JLabel lTelephoneNumber = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_TELEPHONE_NUMBER_LABEL.get());
  private JLabel lFaxNumber = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_FAX_NUMBER_LABEL.get());
  private JLabel lEntryDN = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_NEW_OU_ENTRY_DN_LABEL.get());

  private JLabel[] labels = {lName, lDescription, lAddress,
      lTelephoneNumber, lFaxNumber, lEntryDN
  };

  private JTextField name = Utilities.createLongTextField();
  private JTextField description = Utilities.createLongTextField();
  private JTextField address = Utilities.createLongTextField();
  private JTextField telephoneNumber = Utilities.createLongTextField();
  private JTextField faxNumber = Utilities.createLongTextField();
  private JLabel dn = Utilities.createDefaultLabel();

  private Component[] comps = {name, description, address,
      telephoneNumber, faxNumber, dn};

  /**
   * Default constructor.
   *
   */
  public NewOrganizationalUnitPanel()
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
    dn.setText(","+parentNode.getDN());
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
    return INFO_CTRL_PANEL_NEW_OU_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_NEW_OU_PANEL_TITLE.get();
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

    JTextField[] requiredFields = {name};
    Message[] msgs = {ERR_CTRL_PANEL_NAME_OF_OU_REQUIRED.get()};
    for (int i=0; i<requiredFields.length; i++)
    {
      String v = requiredFields[i].getText().trim();
      if (v.length() == 0)
      {
        errors.add(msgs[i]);
      }
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
    Utilities.setRequiredIcon(lName);

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

    Component[] inlineHelp = {null, null, null, null,
        null, null};

    for (int i=0 ; i< labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      add(labels[i], gbc);
      gbc.insets.left = 10;
      gbc.weightx = 1.0;
      gbc.gridx = 1;
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
    JTextField[] toAddListener = {name};
    for (JTextField tf : toAddListener)
    {
      tf.getDocument().addDocumentListener(listener);
    }
  }

  /**
   * Updates the contents of DN value to reflect the data that the user
   * is providing.
   *
   */
  private void updateDNValue()
  {
    String value = name.getText().trim();
    if (value.length() > 0)
    {
       String rdn = Utilities.getRDNString("ou", value);
          dn.setText(rdn+","+parentNode.getDN());
    }
    else
    {
      dn.setText(","+parentNode.getDN());
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: "+dn.getText()+"\n");
    String[] attrNames = {"ou", "description", "postalAddress",
        "telephoneNumber", "facsimileTelephoneNumber"};
    JTextField[] textFields = {name, description, address,
        telephoneNumber, faxNumber};
    sb.append("objectclass: top\n");
    sb.append("objectclass: organizationalUnit\n");
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
