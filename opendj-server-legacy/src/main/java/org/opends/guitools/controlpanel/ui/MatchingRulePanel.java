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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.Schema;

import static org.opends.messages.AdminToolMessages.*;

/** Class displaying the contents of a matching rule. */
public class MatchingRulePanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 2440493955626646008L;
  private TitlePanel titlePanel = new TitlePanel(LocalizableMessage.EMPTY,
      LocalizableMessage.EMPTY);
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JLabel syntax = Utilities.createDefaultLabel();
  private JList usedByAttributes = new JList(new DefaultListModel());

  /** Default constructor. */
  public MatchingRulePanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_MATCHING_RULE_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return usedByAttributes;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  @Override
  public void okClicked()
  {
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy ++;
    titlePanel.setTitle(INFO_CTRL_PANEL_MATCHING_RULE_DETAILS.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(titlePanel, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 8;
    LocalizableMessage[] labels = {
        INFO_CTRL_PANEL_MATCHING_RULE_NAME.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_OID.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_DESCRIPTION.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_SYNTAX.get()
        };
    JLabel[] values = {name, oid, description, syntax};
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    for (int i=0; i < labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.gridx = 0;
      JLabel l = Utilities.createPrimaryLabel(labels[i]);
      add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      add(values[i], gbc);
      gbc.gridy ++;
    }

    usedByAttributes.setVisibleRowCount(15);
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    JLabel l = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_MATCHING_RULE_USED_BY.get());
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(l, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets.top = 10;
    add(Utilities.createScrollPane(usedByAttributes), gbc);

    MouseAdapter clickListener = new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent ev)
      {
        if (ev.getClickCount() == 1)
        {
          usedBySelected();
        }
      }
    };
    usedByAttributes.addMouseListener(clickListener);

    KeyAdapter keyListener = new KeyAdapter()
    {
      @Override
      public void keyTyped(KeyEvent ev)
      {
        if (ev.getKeyChar() == KeyEvent.VK_SPACE ||
            ev.getKeyChar() == KeyEvent.VK_ENTER)
        {
          usedBySelected();
        }
      }
    };
    usedByAttributes.addKeyListener(keyListener);
    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel with the provided matching rule.
   * @param matchingRule the matching rule.
   * @param schema the schema.
   */
  public void update(MatchingRule matchingRule, Schema schema)
  {
    String n = matchingRule.getNameOrOID();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(LocalizableMessage.raw(n));
    name.setText(n);
    oid.setText(matchingRule.getOID());
    Syntax s = null;
    String syntaxOID = matchingRule.getSyntax().getOID();
    for (Syntax candidate : schema.getSyntaxes())
    {
      if (candidate.getOID().equals(syntaxOID))
      {
        s = candidate;
        break;
      }
    }
    if (s != null)
    {
      syntax.setText(Utilities.getSyntaxText(s));
    }
    else
    {
      syntax.setText(syntaxOID);
    }

    n = matchingRule.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);

    TreeSet<String> attributes = new TreeSet<>(new LowerCaseComparator());
    for (AttributeType attr : schema.getAttributeTypes())
    {
      if (matchingRule.equals(attr.getApproximateMatchingRule()) ||
          matchingRule.equals(attr.getEqualityMatchingRule()) ||
          matchingRule.equals(attr.getSubstringMatchingRule()) ||
          matchingRule.equals(attr.getOrderingMatchingRule()))
      {
        attributes.add(attr.getNameOrOID());
      }
    }
    DefaultListModel model = (DefaultListModel)usedByAttributes.getModel();
    model.clear();
    for (String attr : attributes)
    {
      model.addElement(attr);
    }
  }

  private void usedBySelected()
  {
    String o = (String)usedByAttributes.getSelectedValue();
    if (o != null)
    {
      Schema schema = getInfo().getServerDescriptor().getSchema();
      if (schema != null)
      {
        AttributeType attr = schema.getAttributeType(o.toLowerCase());
        if (attr != null)
        {
          notifySchemaSelectionListeners(attr);
        }
      }
    }
  }
}
