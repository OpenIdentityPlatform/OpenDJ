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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

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

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.LDAPEntryReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.Base64;
import org.opends.server.util.LDIFException;
import org.opends.server.util.ServerConstants;

/**
 * The panel used to duplicate an entry.
 *
 */
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
  private JButton browse;
  private JLabel dn;

  private GenericDialog browseDlg;
  private LDAPEntrySelectionPanel browsePanel;

  private CustomSearchResult entryToDuplicate;
  private String rdnAttribute;

  /**
   * Default constructor.
   *
   */
  public DuplicateEntryPanel()
  {
    super();
    createLayout();
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
  public boolean requiresScroll()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
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
    try
    {
      DN nodeDN = DN.decode(node.getDN());
      if (nodeDN.isNullDN())
      {
        aParentDN = nodeDN;
        aRdn = "(1)";
      }
      else
      {
        aParentDN = nodeDN.getParent();
        aRdn = nodeDN.getRDN().getAttributeValue(0).toString()+"-1";
      }
    }
    catch (DirectoryException de)
    {
      throw new IllegalStateException("Unexpected error decoding dn: '"+
          node.getDN()+"' error: "+de, de);
    }
    parentDN.setText(aParentDN.toString());
    name.setText(aRdn);
    password.setText("");
    confirmPassword.setText("");

    readEntry(node);
  }

  /**
   * {@inheritDoc}
   */
  protected Message getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_DUPLICATE_ENTRY_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_DUPLICATE_ENTRY_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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

    browse = Utilities.createButton(
        INFO_CTRL_PANEL_BROWSE_BUTTON_LABEL.get());
    gbc.weightx = 0.0;
    gbc.gridx = 2;
    add(browse, gbc);
    browse.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
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
    name.getDocument().addDocumentListener(listener);
    parentDN.getDocument().addDocumentListener(listener);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  protected void checkSyntax(ArrayList<Message> errors)
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
    if (!Utils.isDn(parentDN))
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
        errors.add(ERR_CTRL_PANEL_ERROR_CHECKING_ENTRY.get(ioe.toString()));
      }
      catch (LDIFException le)
      {
        errors.add(le.getMessageObject());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  protected String getLDIF()
  {
    String dn = this.dn.getText();
    StringBuilder sb = new StringBuilder();
    sb.append("dn: "+dn);
    for (String attrName : entryToDuplicate.getAttributeNames())
    {
      List<Object> values = entryToDuplicate.getAttributeValues(attrName);
      if (attrName.equalsIgnoreCase(ServerConstants.ATTR_USER_PASSWORD))
      {
        sb.append("\n");
        String pwd = new String(password.getPassword());
        if (!pwd.isEmpty())
        {
          sb.append(attrName+": " + pwd);
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
          if (value instanceof String)
          {
            sb.append(attrName+": "+value);
          }
          else if (value instanceof byte[])
          {
            sb.append(attrName+":: "+Base64.encode((byte[])value));
          }
          else
          {
            sb.append(attrName+": "+value);
          }
        }
      }
      else
      {
        String newValue = null;
        try
        {
          DN theDN = DN.decode(dn);
          newValue = theDN.getRDN().getAttributeValue(0).toString();
        }
        catch (DirectoryException de)
        {
          throw new IllegalStateException("Unexpected error with dn: '"+dn+
              "' "+de, de);
        }
        if (values.size() == 1)
        {
          sb.append("\n");
          sb.append(attrName+": "+newValue);
        }
        else
        {
          String oldValue = null;
          try
          {
            DN oldDN = DN.decode(entryToDuplicate.getDN());
            oldValue = oldDN.getRDN().getAttributeValue(0).toString();
          }
          catch (DirectoryException de)
          {
            throw new IllegalStateException("Unexpected error with dn: '"+
                entryToDuplicate.getDN()+"' "+de, de);
          }
          for (Object value : values)
          {
            sb.append("\n");
            if (oldValue.equals(value))
            {
              sb.append(attrName+": "+newValue);
            }
            else
            {
              sb.append(attrName+": "+value);
            }
          }
        }
      }
    }
    return sb.toString();
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
      public CustomSearchResult processBackgroundTask() throws Throwable
      {
        InitialLdapContext ctx =
          controller.findConnectionForDisplayedEntry(node);
        LDAPEntryReader reader = new LDAPEntryReader(node.getDN(), ctx);
        sleepIfRequired(700, t1);
        return reader.processBackgroundTask();
      }

      public void backgroundTaskCompleted(CustomSearchResult sr,
          Throwable throwable)
      {
        if (throwable != null)
        {
          Message title = INFO_CTRL_PANEL_ERROR_SEARCHING_ENTRY_TITLE.get();
          Message details =
            ERR_CTRL_PANEL_ERROR_SEARCHING_ENTRY.get(node.getDN(),
                throwable.toString());
          displayErrorMessage(title, details);
        }
        else
        {
          entryToDuplicate = sr;
          try
          {
            DN dn = DN.decode(sr.getDN());
            rdnAttribute = dn.getRDN().getAttributeType(0).getNameOrOID();

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
          catch (DirectoryException de)
          {
            displayErrorMessage(INFO_CTRL_PANEL_ERROR_DIALOG_TITLE.get(),
                de.getMessageObject());
          }
        }
      }
    };
    task.startBackgroundTask();
  }

  private void updateDNValue()
  {
    String value = name.getText().trim();
    if (value.length() > 0)
    {
       String rdn = Utilities.getRDNString(rdnAttribute, value);
          dn.setText(rdn+","+parentDN.getText().trim());
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
