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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.util.LDIFException;

/** The panel used to create a new entry using an LDIF representation. */
public class NewEntryFromLDIFPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -3923907357481784964L;
  private JTextArea ldif;
  private JButton checkSyntax;
  private JLabel lSyntaxCorrect;

  /** Default constructor. */
  public NewEntryFromLDIFPanel()
  {
    super();
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return ldif;
  }

  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  @Override
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    super.setParent(parentNode, controller);
    StringBuilder sb = new StringBuilder();
    final String emptyDn = "dn: ";
    sb.append(emptyDn);
    if (parentNode != null)
    {
      sb.append(",").append(parentNode.getDN());
    }
    sb.append("\nobjectClass: top");
    ldif.setText(sb.toString());
    ldif.setCaretPosition(emptyDn.length());
  }

  @Override
  protected LocalizableMessage getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_FROM_LDIF_TITLE.get();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_FROM_LDIF_TITLE.get();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;

    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy ++;

    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.gridx = 0;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    gbc.gridwidth = 3;
    JLabel label = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_LDIF_SYNTAX_LABEL.get());
    add(label, gbc);

    lSyntaxCorrect = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_SYNTAX_CORRECT_LABEL.get());
    lSyntaxCorrect.setIcon(Utilities.createImageIcon(
        "org/opends/quicksetup/images/info_small.gif"));

    ldif = Utilities.createTextArea(LocalizableMessage.EMPTY, 20, 50);
    ldif.getDocument().addDocumentListener(new DocumentListener()
    {
      @Override
      public void removeUpdate(DocumentEvent ev)
      {
        lSyntaxCorrect.setVisible(false);
      }

      @Override
      public void changedUpdate(DocumentEvent ev)
      {
        removeUpdate(ev);
      }

      @Override
      public void insertUpdate(DocumentEvent ev)
      {
       removeUpdate(ev);
      }
    });
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    JScrollPane scroll = Utilities.createScrollPane(ldif);
    gbc.gridy ++;
    gbc.insets.top = 5;
    gbc.fill = GridBagConstraints.BOTH;
    add(scroll, gbc);

    gbc.weighty = 0.0;
    gbc.weightx = 0.0;
    checkSyntax = Utilities.createButton(
        INFO_CTRL_PANEL_CHECK_SYNTAX_BUTTON.get());
    checkSyntax.setOpaque(false);
    checkSyntax.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        ArrayList<LocalizableMessage> errors = new ArrayList<>();
        checkSyntax(errors);
        if (!errors.isEmpty())
        {
          displayErrorDialog(errors);
        }
        else
        {
          lSyntaxCorrect.setVisible(true);
        }
      }
    });
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    add(lSyntaxCorrect, gbc);
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx = 1;
    add(Box.createHorizontalGlue(), gbc);
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridx = 2;
    add(checkSyntax, gbc);
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    lSyntaxCorrect.setVisible(false);
  }

  @Override
  protected void checkSyntax(ArrayList<LocalizableMessage> errors)
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

  @Override
  protected String getLDIF()
  {
    return ldif.getText();
  }
}
