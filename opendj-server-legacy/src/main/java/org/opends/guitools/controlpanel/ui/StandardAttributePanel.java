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
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;

/** The panel that displays a standard attribute definition. */
class StandardAttributePanel extends SchemaElementPanel
{
  private static final long serialVersionUID = -7922968631524763675L;
  private final TitlePanel titlePanel = new TitlePanel(LocalizableMessage.EMPTY, LocalizableMessage.EMPTY);
  private final JLabel name = Utilities.createDefaultLabel();
  private final JLabel parent = Utilities.createDefaultLabel();
  private final JLabel oid = Utilities.createDefaultLabel();
  private final JLabel aliases = Utilities.createDefaultLabel();
  private final JLabel origin = Utilities.createDefaultLabel();
  private final JLabel description = Utilities.createDefaultLabel();
  private final JLabel usage = Utilities.createDefaultLabel();
  private final JLabel syntax = Utilities.createDefaultLabel();
  private final JLabel approximate = Utilities.createDefaultLabel();
  private final JLabel equality = Utilities.createDefaultLabel();
  private final JLabel ordering = Utilities.createDefaultLabel();
  private final JLabel substring = Utilities.createDefaultLabel();
  private final JLabel type = Utilities.createDefaultLabel();
  private final JList<String> requiredBy = new JList<>(new DefaultListModel<String>());
  private final JList<String> optionalBy = new JList<>(new DefaultListModel<String>());

  /** Default constructor of the panel. */
  public StandardAttributePanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_STANDARD_ATTRIBUTE_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return requiredBy;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    // no-op
  }

  @Override
  public void okClicked()
  {
    // no-op
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    createBasicLayout(this, new GridBagConstraints());
    setBorder(PANEL_BORDER);
  }

  /**
   * Creates the basic layout of the panel.
   * @param c the container where all the components will be layed out.
   * @param gbc the grid bag constraints.
   */
  private void createBasicLayout(Container c, GridBagConstraints gbc)
  {
    requiredBy.setVisibleRowCount(5);
    optionalBy.setVisibleRowCount(9);

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

    LocalizableMessage[] labels = {
        INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_PARENT_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_OID_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ALIASES_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ORIGIN_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_DESCRIPTION_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_USAGE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_TYPE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_APPROXIMATE_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_EQUALITY_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_ORDERING_MATCHING_RULE_LABEL.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SUBSTRING_MATCHING_RULE_LABEL.get()
    };
    JLabel[] values = {name, parent, oid, aliases, origin, description, usage,
        syntax, type, approximate, equality, ordering, substring};

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
    labels = new LocalizableMessage[] {
        INFO_CTRL_PANEL_REQUIRED_BY_LABEL.get(),
        INFO_CTRL_PANEL_ALLOWED_BY_LABEL.get()
        };
    JList<?>[] lists = { requiredBy, optionalBy };
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

      final JList<?> list = lists[i];
      MouseAdapter clickListener = new MouseAdapter()
      {
        @Override
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
        @Override
        public void keyTyped(KeyEvent ev)
        {
          if (ev.getKeyChar() == KeyEvent.VK_SPACE ||
              ev.getKeyChar() == KeyEvent.VK_ENTER)
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
    String n = attr.getNameOrOID();
    titlePanel.setDetails(LocalizableMessage.raw(n));
    name.setText(n);
    AttributeType superior = attr.getSuperiorType();
    n = superior != null ? superior.getNameOrOID() : null;
    parent.setText(n);
    oid.setText(attr.getOID());
    origin.setText(StandardObjectClassPanel.getOrigin(new SomeSchemaElement(attr)).toString());
    n = attr.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);
    n = attr.getUsage() != null ? attr.getUsage().toString() : NOT_APPLICABLE.toString();
    usage.setText(n);
    Set<String> aliases = getAliases(attr);
    if (!aliases.isEmpty())
    {
      n = Utilities.getStringFromCollection(aliases, ", ");
    }
    else
    {
      n = NOT_APPLICABLE.toString();
    }
    this.aliases.setText(n);
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
    SortedSet<String> requiredByOcs = new TreeSet<>(lowerCaseComparator);
    for (ObjectClass oc : schema.getObjectClasses())
    {
      if (oc.getRequiredAttributes().contains(attr))
      {
        requiredByOcs.add(oc.getNameOrOID());
      }
    }

    DefaultListModel<String> model = (DefaultListModel<String>) requiredBy.getModel();
    model.clear();
    for (String oc : requiredByOcs)
    {
      model.addElement(oc);
    }

    SortedSet<String> optionalByOcs = new TreeSet<>(lowerCaseComparator);
    for (ObjectClass oc : schema.getObjectClasses())
    {
      if (oc.getOptionalAttributes().contains(attr))
      {
        optionalByOcs.add(oc.getNameOrOID());
      }
    }

    model = (DefaultListModel<String>) optionalBy.getModel();
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
  static LocalizableMessage getTypeValue(AttributeType attr)
  {
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    boolean[] props = {attr.isOperational(), attr.isSingleValue(),
        attr.isNoUserModification(), attr.isCollective(),
        attr.isObsolete()};
    LocalizableMessage[][] values = {
        {INFO_CTRL_PANEL_ATTRIBUTE_OPERATIONAL_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_SINGLE_VALUED_LABEL.get(),
          INFO_CTRL_PANEL_ATTRIBUTE_MULTI_VALUED_LABEL.get()},
        {INFO_CTRL_PANEL_ATTRIBUTE_NON_MODIFIABLE_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_COLLECTIVE_LABEL.get(), null},
        {INFO_CTRL_PANEL_ATTRIBUTE_OBSOLETE_LABEL.get(), null}};
    int i = 0;
    for (boolean prop : props)
    {
      LocalizableMessage value = prop ? values[i][0] : values[i][1];
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
}
