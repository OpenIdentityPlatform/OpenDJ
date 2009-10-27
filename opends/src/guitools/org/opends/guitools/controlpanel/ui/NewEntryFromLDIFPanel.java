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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
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

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.util.LDIFException;

/**
 * The panel used to create a new entry using an LDIF representation.
 *
 */
public class NewEntryFromLDIFPanel extends AbstractNewEntryPanel
{
  private static final long serialVersionUID = -3923907357481784964L;
  private JTextArea ldif;
  private JButton checkSyntax;
  private JLabel lSyntaxCorrect;

  /**
   * Default constructor.
   *
   */
  public NewEntryFromLDIFPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return ldif;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void setParent(BasicNode parentNode, BrowserController controller)
  {
    super.setParent(parentNode, controller);
    StringBuilder sb = new StringBuilder();
    final String emptyDn = "dn: ";
    sb.append(emptyDn);
    if (parentNode != null)
    {
      sb.append(","+parentNode.getDN());
    }
    sb.append("\nobjectClass: top");
    ldif.setText(sb.toString());
    ldif.setCaretPosition(emptyDn.length());
  }

  /**
   * {@inheritDoc}
   */
  protected Message getProgressDialogTitle()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_FROM_LDIF_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_FROM_LDIF_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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

    ldif = Utilities.createTextArea(Message.EMPTY, 20, 50);
    ldif.getDocument().addDocumentListener(new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        lSyntaxCorrect.setVisible(false);
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        removeUpdate(ev);
      }

      /**
       * {@inheritDoc}
       */
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
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        ArrayList<Message> errors = new ArrayList<Message>();
        checkSyntax(errors);
        if (errors.size() > 0)
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

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    lSyntaxCorrect.setVisible(false);
  }

  /**
   * {@inheritDoc}
   */
  protected void checkSyntax(ArrayList<Message> errors)
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

  /**
   * {@inheritDoc}
   */
  protected String getLDIF()
  {
    return ldif.getText();
  }
}
