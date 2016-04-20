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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static com.forgerock.opendj.cli.Utils.isDN;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.LDAPEntryReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.util.Base64;
import org.opends.server.util.LDIFException;
import org.opends.server.util.ServerConstants;

/** The panel used to duplicate an entry. */
public class DuplicateEntryPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -9879879123123123L;
  private JLabel lName;
  private JTextField name;
  private JLabel lParentDN;
  private JTextField parentDN;
  private JLabel lPassword;
  private JPasswordField password = Utilities.createPasswordField(25);
  private JLabel lconfirmPassword;
  private JPasswordField confirmPassword = Utilities.createPasswordField(25);
  private JLabel lPasswordInfo;
  private JLabel dn;

  private GenericDialog browseDlg;
  private LDAPEntrySelectionPanel browsePanel;

  private CustomSearchResult entryToDuplicate;
  private String rdnAttribute;

  /** Default constructor. */
  public DuplicateEntryPanel()
  {
    super();
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  @Override
  public boolean requiresScroll()
  {
    return true;
  }

  @Override
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    throw new IllegalArgumentException("this method must not be called");
  }

  /**
   * Sets the entry to be duplicated.
   * @param node the node to be duplicated.
   * @param controller the browser controller.
   */
  public void setEntryToDuplicate(BasicNode node,
      BrowserController controller)
  {
    if (node == null)
    {
      throw new IllegalArgumentException("node is null.");
    }

    displayMessage(INFO_CTRL_PANEL_READING_SUMMARY.get());
    setEnabledOK(false);

    entryToDuplicate = null;
    super.controller = controller;

    DN aParentDN;
    String aRdn;
    DN nodeDN = DN.valueOf(node.getDN());
    if (nodeDN.isRootDN())
    {
      aParentDN = nodeDN;
      aRdn = "(1)";
    }
    else
    {
      aParentDN = nodeDN.parent();
      aRdn = nodeDN.rdn().getFirstAVA().getAttributeValue() + "-1";
    }

    parentDN.setText(aParentDN != null ? aParentDN.toString() : "");
    name.setText(aRdn);
    password.setText("");
    confirmPassword.setText("");

    readEntry(node);
  }

  @Override
  protected LocalizableMessage getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_DUPLICATE_ENTRY_TITLE.get();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_DUPLICATE_ENTRY_TITLE.get();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;

    addErrorPane(gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.gridx = 0;
    gbc.insets.left = 0;
    lName = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_DUPLICATE_ENTRY_NAME_LABEL.get());
    add(lName, gbc);
    name = Utilities.createTextField("", 30);
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.weighty = 0.0;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    add(name, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;

    gbc.fill = GridBagConstraints.BOTH;
    lParentDN = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_DUPLICATE_ENTRY_PARENT_DN_LABEL.get());
    add(lParentDN, gbc);

    parentDN = Utilities.createTextField("", 30);
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    add(parentDN, gbc);

    JButton browse = Utilities.createButton(
            INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    gbc.weightx = 0.0;
    gbc.gridx = 2;
    add(browse, gbc);
    browse.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        browseClicked();
      }
    });

    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    lPassword = Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_DUPLICATE_ENTRY_NEWPASSWORD_LABEL.get());
    add(lPassword, gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.weighty = 0.0;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    add(password, gbc);

    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    lconfirmPassword = Utilities.createPrimaryLabel(
              INFO_CTRL_PANEL_DUPLICATE_ENTRY_CONFIRMNEWPASSWORD_LABEL.get());
    add(lconfirmPassword, gbc);
    gbc.weightx = 1.0;
    gbc.gridwidth = 2;
    gbc.weighty = 0.0;
    gbc.insets.left = 10;
    gbc.gridx = 1;
    add(confirmPassword, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    lPasswordInfo = Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_DUPLICATE_ENTRY_PASSWORD_INFO.get());
    gbc.gridwidth = 3;
    add(lPasswordInfo, gbc);

    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.insets.left = 0;
    add(Utilities.createPrimaryLabel(INFO_CTRL_PANEL_DUPLICATE_ENTRY_DN.get()),
        gbc);
    dn = Utilities.createDefaultLabel();

    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.insets.left = 10;
    add(dn, gbc);

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
    name.getDocument().addDocumentListener(listener);
    parentDN.getDocument().addDocumentListener(listener);

    addBottomGlue(gbc);
  }

  @Override
  protected void checkSyntax(ArrayList<LocalizableMessage> errors)
  {
    int origSize = errors.size();
    String name = this.name.getText().trim();
    setPrimaryValid(lName);
    setPrimaryValid(lParentDN);
    if (name.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_DUPLICATE_ENTRY_NAME_EMPTY.get());
      setPrimaryInvalid(lName);
    }
    String parentDN = this.parentDN.getText().trim();
    if (!isDN(parentDN))
    {
      errors.add(ERR_CTRL_PANEL_DUPLICATE_ENTRY_PARENT_DN_NOT_VALID.get());
      setPrimaryInvalid(lParentDN);
    }
    else if (!entryExists(parentDN))
    {
      errors.add(ERR_CTRL_PANEL_DUPLICATE_ENTRY_PARENT_DOES_NOT_EXIST.get());
      setPrimaryInvalid(lParentDN);
    }

    char[] pwd1 = password.getPassword();
    char[] pwd2 = confirmPassword.getPassword();
    String sPwd1 = new String(pwd1);
    String sPwd2 = new String(pwd2);
    if (!sPwd1.equals(sPwd2))
    {
          errors.add(ERR_CTRL_PANEL_PASSWORD_DO_NOT_MATCH.get());
    }

    if (errors.size() == origSize)
    {
      try
      {
        getEntry();
      }
      catch (IOException ioe)
      {
        errors.add(ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe));
      }
      catch (LDIFException le)
      {
        errors.add(le.getMessageObject());
      }
    }
  }

  @Override
  protected String getLDIF()
  {
    String dn = this.dn.getText();
    StringBuilder sb = new StringBuilder();
    sb.append("dn: ").append(dn);
    for (String attrName : entryToDuplicate.getAttributeNames())
    {
      List<Object> values = entryToDuplicate.getAttributeValues(attrName);
      if (attrName.equalsIgnoreCase(ServerConstants.ATTR_USER_PASSWORD))
      {
        sb.append("\n");
        String pwd = new String(password.getPassword());
        if (!pwd.isEmpty())
        {
          sb.append(attrName).append(": ").append(pwd);
        }
      }
      else if (!attrName.equalsIgnoreCase(rdnAttribute))
      {
        if (!ViewEntryPanel.isEditable(attrName,
            getInfo().getServerDescriptor().getSchema()))
        {
          continue;
        }
        for (Object value : values)
        {
          sb.append("\n");
          if (value instanceof byte[])
          {
            final String base64 = Base64.encode((byte[]) value);
            sb.append(attrName).append(":: ").append(base64);
          }
          else
          {
            sb.append(attrName).append(": ").append(value);
          }
        }
      }
      else
      {
        String newValue = getFirstValue(dn);
        if (values.size() == 1)
        {
          sb.append("\n");
          sb.append(attrName).append(": ").append(newValue);
        }
        else
        {
          String oldValue = getFirstValue(entryToDuplicate.getDN());
          for (Object value : values)
          {
            sb.append("\n");
            if (oldValue.equals(value))
            {
              sb.append(attrName).append(": ").append(newValue);
            }
            else
            {
              sb.append(attrName).append(": ").append(value);
            }
          }
        }
      }
    }
    return sb.toString();
  }

  private String getFirstValue(String dn)
  {
    return DN.valueOf(dn).rdn().getFirstAVA().getAttributeValue().toString();
  }

  private void browseClicked()
  {
    if (browseDlg == null)
    {
      browsePanel = new LDAPEntrySelectionPanel();
      browsePanel.setTitle(INFO_CTRL_PANEL_CHOOSE_PARENT_ENTRY_DN.get());
      browsePanel.setFilter(
          LDAPEntrySelectionPanel.Filter.DEFAULT);
      browsePanel.setMultipleSelection(false);
      browsePanel.setInfo(getInfo());
      browseDlg = new GenericDialog(Utilities.getFrame(this),
          browsePanel);
      Utilities.centerGoldenMean(browseDlg,
          Utilities.getParentDialog(this));
      browseDlg.setModal(true);
    }
    browseDlg.setVisible(true);
    String[] dns = browsePanel.getDNs();
    if (dns.length > 0)
    {
      for (String dn : dns)
      {
        parentDN.setText(dn);
      }
    }
  }

  private void readEntry(final BasicNode node)
  {
    final long t1 = System.currentTimeMillis();
    BackgroundTask<CustomSearchResult> task =
      new BackgroundTask<CustomSearchResult>()
    {
      @Override
      public CustomSearchResult processBackgroundTask() throws Throwable
      {
        InitialLdapContext ctx =
          controller.findConnectionForDisplayedEntry(node);
        LDAPEntryReader reader = new LDAPEntryReader(node.getDN(), ctx);
        sleepIfRequired(700, t1);
        return reader.processBackgroundTask();
      }

      @Override
      public void backgroundTaskCompleted(CustomSearchResult sr,
          Throwable throwable)
      {
        if (throwable != null)
        {
          LocalizableMessage title = INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
          LocalizableMessage details =
            ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(node.getDN(), throwable);
          displayErrorMessage(title, details);
        }
        else
        {
          entryToDuplicate = sr;
          try
          {
            DN dn = DN.valueOf(sr.getDN());
            rdnAttribute = dn.rdn().getFirstAVA().getAttributeType().getNameOrOID();

            updateDNValue();
            Boolean hasPassword = !sr.getAttributeValues(
                    ServerConstants.ATTR_USER_PASSWORD).isEmpty();
            lPassword.setVisible(hasPassword);
            password.setVisible(hasPassword);
            lconfirmPassword.setVisible(hasPassword);
            confirmPassword.setVisible(hasPassword);
            lPasswordInfo.setVisible(hasPassword);
            displayMainPanel();
            setEnabledOK(true);
          }
          catch (LocalizedIllegalArgumentException e)
          {
            displayErrorMessage(INFO_CTRL_PANEL_ERROR_DIALOG_TITLE.get(), e.getMessageObject());
          }
        }
      }
    };
    task.startBackgroundTask();
  }

  private void updateDNValue()
  {
    String value = name.getText().trim();
    // If it takes time to read the entry, the rdnAttribute might not be initialized yet. Don't try to use it then.
    if (value.length() > 0 && rdnAttribute != null)
    {
      dn.setText(rdnAttribute + "=" + value + "," + parentDN.getText().trim());
    }
    else
    {
      dn.setText(","+parentDN.getText().trim());
    }
  }

  private void sleepIfRequired(long sleepTime, long startTime)
  {
    long tSleep = sleepTime - (System.currentTimeMillis() - startTime);
    if (tSleep > 0)
    {
      try
      {
        Thread.sleep(tSleep);
      }
      catch (Throwable t)
      {
      }
    }
  }
}
