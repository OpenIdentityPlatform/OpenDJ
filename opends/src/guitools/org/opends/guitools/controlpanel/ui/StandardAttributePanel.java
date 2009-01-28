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
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * The panel that displays a standard attribute definition.
 *
 */
public class StandardAttributePanel extends SchemaElementPanel
{
  private static final long serialVersionUID = -7922968631524763675L;
  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY,
      Message.EMPTY);
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel parent = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel aliases = Utilities.createDefaultLabel();
  private JLabel origin = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JLabel usage = Utilities.createDefaultLabel();
  private JLabel syntax = Utilities.createDefaultLabel();
  private JLabel approximate = Utilities.createDefaultLabel();
  private JLabel equality = Utilities.createDefaultLabel();
  private JLabel ordering = Utilities.createDefaultLabel();
  private JLabel substring = Utilities.createDefaultLabel();
  private JLabel type = Utilities.createDefaultLabel();
  private JList requiredBy = new JList(new DefaultListModel());
  private JList optionalBy = new JList(new DefaultListModel());

  /**
   * Default constructor of the panel.
   *
   */
  public StandardAttributePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_STANDARD_ATTRIBUTE_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return requiredBy;
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
  protected void createLayout()
  {
    createBasicLayout(this, new GridBagConstraints());
    setBorder(PANEL_BORDER);
  }

  /**
   * Creates the basic layout of the panel.
   * @param c the container where all the components will be layed out.
   * @param gbc the grid bag constraints.
   */
  protected void createBasicLayout(Container c, GridBagConstraints gbc)
  {
    requiredBy.setVisibleRowCount(5);
    optionalBy.setVisibleRowCount(9);

    Message[] labels = {
        INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_PARENT_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_OID_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ALIASES_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ORIGIN_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_DESCRIPTION_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_USAGE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_APPROXIMATE_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_EQUALITY_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ORDERING_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SUBSTRING_MATCHING_RULE_LABEL.get()
    };
    JLabel[] values = {name, parent, oid, aliases, origin, description, usage,
        syntax, approximate, equality, ordering, substring, type};
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    addErrorPane(c, gbc);
    gbc.gridy ++;

    gbc.anchor = GridBagConstraints.WEST;
    titlePanel.setTitle(INFO_CTRL_PANEL_ATTRIBUTE_DETAILS.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    c.add(titlePanel, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 8;
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    for (int i=0; i < labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.gridx = 0;
      JLabel l = Utilities.createPrimaryLabel(labels[i]);
      c.add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      c.add(values[i], gbc);
      gbc.gridy ++;
    }
    labels = new Message[] {
        INFO_CTRL_PANEL_REQUIRED_BY_LABEL.get(),
        INFO_CTRL_PANEL_ALLOWED_BY_LABEL.get()
        };
    JList[] lists = {requiredBy, optionalBy};
    gbc.anchor = GridBagConstraints.NORTHWEST;
    for (int i=0; i<2; i++)
    {
      gbc.insets.left = 0;
      gbc.gridx = 0;
      JLabel l = Utilities.createPrimaryLabel(labels[i]);
      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      c.add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      if (i == 0)
      {
        gbc.weighty = 0.35;
      }
      else
      {
        gbc.weighty = 0.65;
      }
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets.top = 10;
      c.add(Utilities.createScrollPane(lists[i]), gbc);
      gbc.gridy ++;

      final JList list = lists[i];
      MouseAdapter clickListener = new MouseAdapter()
      {
        /**
         * {@inheritDoc}
         */
        public void mouseClicked(MouseEvent ev)
        {
          if (ev.getClickCount() == 1)
          {
            objectClassSelected(list);
          }
        }
      };
      list.addMouseListener(clickListener);

      KeyAdapter keyListener = new KeyAdapter()
      {
        /**
         * {@inheritDoc}
         */
        public void keyTyped(KeyEvent ev)
        {
          if ((ev.getKeyChar() == KeyEvent.VK_SPACE) ||
              (ev.getKeyChar() == KeyEvent.VK_ENTER))
          {
            objectClassSelected(list);
          }
        }
      };
      list.addKeyListener(keyListener);
    }
  }

  /**
   * Updates the contents of the panel with the provided attribute.
   * @param attr the attribute.
   * @param schema the schema.
   */
  public void update(AttributeType attr, Schema schema)
  {
    String n = attr.getPrimaryName();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);
    AttributeType superior = attr.getSuperiorType();
    if (superior == null)
    {
      n = null;
    }
    else
    {
      n = superior.getPrimaryName();
    }
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    parent.setText(n);
    oid.setText(attr.getOID());
    origin.setText(StandardObjectClassPanel.getOrigin(attr).toString());
    n = attr.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);
    if (attr.getUsage() == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    else
    {
      n = attr.getUsage().toString();
    }
    usage.setText(n);
    ArrayList<String> otherNames = new ArrayList<String>();
    Iterable<String> ocNames = attr.getNormalizedNames();
    String primaryName = attr.getPrimaryName();
    if (primaryName == null)
    {
      primaryName = "";
    }
    for (String name : ocNames)
    {
      if (!name.equalsIgnoreCase(primaryName))
      {
        otherNames.add(name);
      }
    }
    if (otherNames.size() > 0)
    {
      n = Utilities.getStringFromCollection(otherNames, ", ");
    }
    else
    {
      n = NOT_APPLICABLE.toString();
    }
    aliases.setText(n);
    syntax.setText(Utilities.getSyntaxText(attr.getSyntax()));
    JLabel[] labels = {approximate, equality, ordering, substring};
    MatchingRule[] rules = {attr.getApproximateMatchingRule(),
        attr.getEqualityMatchingRule(), attr.getOrderingMatchingRule(),
        attr.getSubstringMatchingRule()
    };
    for (int i=0; i<labels.length; i++)
    {
      if (rules[i] != null)
      {
        labels[i].setText(Utilities.getMatchingRuleText(rules[i]));
      }
      else
      {
        labels[i].setText(NOT_APPLICABLE.toString());
      }
    }

    type.setText(getTypeValue(attr).toString());

    Comparator<String> lowerCaseComparator = new LowerCaseComparator();
    SortedSet<String> requiredByOcs = new TreeSet<String>(lowerCaseComparator);
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (oc.getRequiredAttributeChain().contains(attr))
      {
        requiredByOcs.add(oc.getNameOrOID());
      }
    }

    DefaultListModel model = (DefaultListModel)requiredBy.getModel();
    model.clear();
    for (String oc : requiredByOcs)
    {
      model.addElement(oc);
    }

    SortedSet<String> optionalByOcs = new TreeSet<String>(lowerCaseComparator);
    for (ObjectClass oc : schema.getObjectClasses().values())
    {
      if (oc.getOptionalAttributeChain().contains(attr))
      {
        optionalByOcs.add(oc.getNameOrOID());
      }
    }

    model = (DefaultListModel)optionalBy.getModel();
    model.clear();
    for (String oc : optionalByOcs)
    {
      model.addElement(oc);
    }
  }

  /**
   * Returns the message describing the attribute type (operational, single
   * valued, etc.).
   * @param attr the attribute.
   * @return the message describing the attribute type (operational, single
   * valued, etc.).
   */
  static Message getTypeValue(AttributeType attr)
  {
    MessageBuilder mb = new MessageBuilder();
    Boolean[] props = {attr.isOperational(), attr.isSingleValue(),
        attr.isNoUserModification(), attr.isCollective(),
        attr.isObsolete()};
    Message[][] values = {
        {INFO_CTRL_PANEL_ATTRIBUTE_OPERATIONAL_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_SINGLE_VALUED_LABEL.get(),
          INFO_CTRL_PANEL_ATTRIBUTE_MULTI_VALUED_LABEL.get()},
        {INFO_CTRL_PANEL_ATTRIBUTE_NON_MODIFIABLE_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_COLLECTIVE_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_OBSOLETE_LABEL.get(), null}};
    int i = 0;
    for (Boolean prop : props)
    {
      Message value = prop ? values[i][0] : values[i][1];
      if (value != null)
      {
        if (mb.length() > 0)
        {
          mb.append(", ");
        }
        mb.append(value);
      }
      i++;
    }
    return mb.toMessage();
  }

  private void objectClassSelected(JList list)
  {
    String o = (String)list.getSelectedValue();
    if (o != null)
    {
      Schema schema = getInfo().getServerDescriptor().getSchema();
      if (schema != null)
      {
        ObjectClass oc = schema.getObjectClass(o.toLowerCase());
        if (oc != null)
        {
          notifySchemaSelectionListeners(oc);
        }
      }
    }
  }
}
