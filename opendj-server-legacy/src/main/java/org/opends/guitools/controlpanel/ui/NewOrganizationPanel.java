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
 * Portions Copyright 2014-2016 ForgeRock AS
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;

/** The panel used to create a new organization. */
class NewOrganizationPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = 6560126551083160773L;
  /** The label for the name. */
  private final JLabel lName = Utilities.createPrimaryLabel();
  /** The label for the description. */
  private final JLabel lDescription = Utilities.createPrimaryLabel();
  /** The label for the DN. */
  private final JLabel lDn = Utilities.createPrimaryLabel();

  /** An array containing all the labels. */
  protected final JLabel[] labels = {lName, lDescription, lDn};

  /** The field containing the name. */
  protected final JTextField name = Utilities.createLongTextField();
  /** The field containing the description. */
  protected final JTextField description = Utilities.createLongTextField();
  /** The label containing the DN value. */
  protected final JLabel dn = Utilities.createDefaultLabel();

  /** An array containing all the components. */
  private final Component[] comps = { name, description, dn };

  /** Default constructor. */
  public NewOrganizationPanel()
  {
    super();
    createLayout();
  }

  @Override
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

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_NEW_ORGANIZATION_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * Returns the title of the progress dialog.
   * @return the title of the progress dialog.
   */
  @Override
  protected LocalizableMessage getProgressDialogTitle()
  {
    return INFO_CTRL_NEW_ORGANIZATION_PANEL_TITLE.get();
  }

  @Override
  protected void checkSyntax(ArrayList<LocalizableMessage> errors)
  {
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }

    JTextField[] requiredFields = {name};
    LocalizableMessage[] msgs = {ERR_CTRL_PANEL_NAME_OF_ORGANIZATION_REQUIRED.get()};
    for (int i=0; i<requiredFields.length; i++)
    {
      String v = requiredFields[i].getText().trim();
      if (v.length() == 0)
      {
        errors.add(msgs[i]);
      }
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    LocalizableMessage[] ls = {
        INFO_CTRL_PANEL_NEW_ORGANIZATION_NAME_LABEL.get(),
        INFO_CTRL_PANEL_NEW_ORGANIZATION_DESCRIPTION_LABEL.get(),
        INFO_CTRL_PANEL_NEW_ORGANIZATION_ENTRY_DN_LABEL.get()};
    int i = 0;
    for (LocalizableMessage l : ls)
    {
      labels[i].setText(l.toString());
      i++;
    }
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

    Component[] inlineHelp = {null, null, null};

    for (i=0 ; i< labels.length; i++)
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
      @Override
      public void insertUpdate(DocumentEvent ev)
      {
        updateDNValue();
      }

      @Override
      public void changedUpdate(DocumentEvent ev)
      {
        insertUpdate(ev);
      }

      @Override
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

  /** Updates the contents of DN value to reflect the data that the user is providing. */
  protected void updateDNValue()
  {
    String value = name.getText().trim();
    if (value.length() > 0)
    {
      dn.setText("o" + "=" + value + "," + parentNode.getDN());
    }
    else
    {
      dn.setText(","+parentNode.getDN());
    }
  }

  @Override
  protected String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: ").append(dn.getText()).append("\n");
    String[] attrNames = {"o", "description"};
    JTextField[] textFields = {name, description};
    sb.append("objectclass: top\n");
    sb.append("objectclass: organization\n");
    for (int i=0; i<attrNames.length; i++)
    {
      String value = textFields[i].getText().trim();
      if (value.length() > 0)
      {
        sb.append(attrNames[i]).append(": ").append(value).append("\n");
      }
    }
    return sb.toString();
  }
}
