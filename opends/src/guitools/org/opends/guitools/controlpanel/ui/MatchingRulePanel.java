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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Schema;

/**
 * Class displaying the contents of a matching rule.
 *
 */
public class MatchingRulePanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 2440493955626646008L;
  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY,
      Message.EMPTY);
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JLabel syntax = Utilities.createDefaultLabel();
  private JLabel type = Utilities.createDefaultLabel();
  private JList usedByAttributes = new JList(new DefaultListModel());

  /**
   * Default constructor.
   */
  public MatchingRulePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_MATCHING_RULE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return usedByAttributes;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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
    Message[] labels = {
        INFO_CTRL_PANEL_MATCHING_RULE_NAME.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_OID.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_DESCRIPTION.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_TYPE.get(),
        INFO_CTRL_PANEL_MATCHING_RULE_SYNTAX.get()
        };
    JLabel[] values = {name, oid, description, type, syntax};
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

    MouseAdapter doubleClickListener = new MouseAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void mouseClicked(MouseEvent ev)
      {
        if (ev.getClickCount() > 1)
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
    };
    usedByAttributes.addMouseListener(doubleClickListener);
    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel with the provided matching rule.
   * @param matchingRule the matching rule.
   * @param schema the schema.
   */
  public void update(MatchingRule matchingRule, Schema schema)
  {
    String n = matchingRule.getName();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);
    oid.setText(matchingRule.getOID());
    AttributeSyntax s = null;
    String syntaxOID = matchingRule.getSyntaxOID();
    for (AttributeSyntax candidate : schema.getSyntaxes().values())
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

    type.setText(getTypeValue(matchingRule).toString());

    TreeSet<String> attributes = new TreeSet<String>();
    for (AttributeType attr : schema.getAttributeTypes().values())
    {
      attributes.add(attr.getNameOrOID());
    }
    DefaultListModel model = (DefaultListModel)usedByAttributes.getModel();
    model.clear();
    for (String attr : attributes)
    {
      model.addElement(attr);
    }
  }

  /**
   * Returns the message for the type of the provided matching rule.
   * @param matchingRule the matching rule.
   * @return the message for the type of the provided matching rule.
   */
  static Message getTypeValue(MatchingRule matchingRule)
  {
    Message text;
    if (matchingRule instanceof EqualityMatchingRule)
    {
      text = INFO_CTRL_PANEL_INDEX_EQUALITY.get();
    }
    else if (matchingRule instanceof OrderingMatchingRule)
    {
      text = INFO_CTRL_PANEL_INDEX_ORDERING.get();
    }
    else if (matchingRule instanceof SubstringMatchingRule)
    {
      text = INFO_CTRL_PANEL_INDEX_SUBSTRING.get();
    }
    else if (matchingRule instanceof ApproximateMatchingRule)
    {
      text = INFO_CTRL_PANEL_INDEX_APPROXIMATE.get();
    }
    else
    {
      text = NOT_APPLICABLE;
    }
    return text;
  }
}
