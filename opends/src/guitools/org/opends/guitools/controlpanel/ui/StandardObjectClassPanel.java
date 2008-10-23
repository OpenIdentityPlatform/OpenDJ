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
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;

/**
 * The panel that displays a standard object class definition.
 *
 */
public class StandardObjectClassPanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 5561268287795223026L;
  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY, Message.EMPTY);
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel parent = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel origin = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JLabel aliases = Utilities.createDefaultLabel();
  private JLabel type = Utilities.createDefaultLabel();
  private JList requiredAttributes = new JList(new DefaultListModel());
  private JList optionalAttributes = new JList(new DefaultListModel());

  private static Message ABSTRACT_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_ABSTRACT_LABEL.get();
  private static Message STRUCTURAL_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_STRUCTURAL_LABEL.get();
  private static Message AUXILIARY_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_AUXILIARY_LABEL.get();
  private static Message OBSOLETE_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_OBSOLETE_LABEL.get();

  private Map<String, AttributeType> hmAttrs =
    new HashMap<String, AttributeType>();

  /**
   * Default constructor of the panel.
   *
   */
  public StandardObjectClassPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_STANDARD_OBJECTCLASS_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return requiredAttributes;
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

    requiredAttributes.setVisibleRowCount(5);
    optionalAttributes.setVisibleRowCount(9);

    Message[] labels = {
        INFO_CTRL_PANEL_OBJECTCLASS_NAME_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_OID_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_ALIASES_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_ORIGIN_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_DESCRIPTION_LABEL.get(),
        INFO_CTRL_PANEL_OBJECTCLASS_TYPE_LABEL.get()
    };

    JLabel[] values = {name, parent, oid, aliases, origin, description, type};
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    addErrorPane(c, gbc);
    gbc.gridy ++;
    titlePanel.setTitle(INFO_CTRL_PANEL_OBJECTCLASS_DETAILS.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
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
        INFO_CTRL_PANEL_REQUIRED_ATTRIBUTES_LABEL.get(),
        INFO_CTRL_PANEL_OPTIONAL_ATTRIBUTES_LABEL.get()
        };
    JList[] lists = {requiredAttributes, optionalAttributes};
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
      gbc.weighty = 0.0;
      JLabel explanation = Utilities.createInlineHelpLabel(
          INFO_CTRL_PANEL_INHERITED_ATTRIBUTES_HELP.get());
      gbc.insets.top = 3;
      c.add(explanation, gbc);
      gbc.gridy ++;

      final JList list = lists[i];
      MouseAdapter doubleClickListener = new MouseAdapter()
      {
        /**
         * {@inheritDoc}
         */
        public void mouseClicked(MouseEvent ev)
        {
          if (ev.getClickCount() > 1)
          {
            String o = (String)list.getSelectedValue();
            if (o != null)
            {
              AttributeType attr = hmAttrs.get(o);
              if (attr != null)
              {
                notifySchemaSelectionListeners(attr);
              }
            }
          }
        }
      };
      list.addMouseListener(doubleClickListener);
    }
  }

  /**
   * Returns the message describing the schema element origin (file, RFC, etc.).
   * @param element the schema element.
   * @return the message describing the schema element origin (file, RFC, etc.).
   */
  static Message getOrigin(CommonSchemaElements element)
  {
    MessageBuilder returnValue = new MessageBuilder();
    String fileName = element.getSchemaFile();
    String xOrigin = null;
    Iterable<String> it =
      element.getExtraProperty(ServerConstants.SCHEMA_PROPERTY_ORIGIN);
    if (it != null)
    {
      Iterator<String> iterator = it.iterator();
      if (iterator.hasNext())
      {
        xOrigin = iterator.next();
      }
    }
    if (xOrigin != null)
    {
      returnValue.append(xOrigin);
      if (fileName != null)
      {
        returnValue.append(" -");
        returnValue.append(
            INFO_CTRL_PANEL_DEFINED_IN_SCHEMA_FILE.get(fileName));
      }
    }
    else if (fileName != null)
    {
      returnValue.append(INFO_CTRL_PANEL_DEFINED_IN_SCHEMA_FILE.get(fileName));
    }
    else
    {
      returnValue.append(NOT_APPLICABLE);
    }
    return returnValue.toMessage();
  }

  /**
   * Updates the contents of the panel with the provided object class.
   * @param oc the object class.
   * @param schema the schema.
   */
  public void update(ObjectClass oc, Schema schema)
  {
    if ((oc == null) || (schema == null))
    {
      // Ignore: this is called to get an initial panel size.
      return;
    }
    hmAttrs.clear();
    String n = oc.getPrimaryName();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);
    ObjectClass superior = oc.getSuperiorClass();
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
    oid.setText(oc.getOID());
    origin.setText(getOrigin(oc).toString());
    n = oc.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);
    ArrayList<String> otherNames = new ArrayList<String>();
    Iterable<String> ocNames = oc.getNormalizedNames();
    String primaryName = oc.getPrimaryName();
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

    type.setText(getTypeValue(oc).toString());

    SortedSet<String> requiredAttrs = new TreeSet<String>();
    Set<String> inheritedAttrs = new HashSet<String>();
    for (AttributeType attr : oc.getRequiredAttributeChain())
    {
      requiredAttrs.add(attr.getNameOrOID());
    }
    ObjectClass parent = oc.getSuperiorClass();
    if (parent != null)
    {
      for (AttributeType attr : parent.getRequiredAttributeChain())
      {
        inheritedAttrs.add(attr.getNameOrOID());
      }
    }

    DefaultListModel model = (DefaultListModel)requiredAttributes.getModel();
    model.clear();
    for (String attr : requiredAttrs)
    {
      String v;
      if (inheritedAttrs.contains(attr))
      {
        v = attr+" (*)";
      }
      else
      {
        v = attr;
      }
      model.addElement(v);
      hmAttrs.put(v, schema.getAttributeType(attr.toLowerCase()));
    }

    SortedSet<String> optionalAttrs = new TreeSet<String>();
    inheritedAttrs = new HashSet<String>();
    for (AttributeType attr : oc.getOptionalAttributeChain())
    {
      optionalAttrs.add(attr.getNameOrOID());
    }
    parent = oc.getSuperiorClass();
    if (parent != null)
    {
      for (AttributeType attr : parent.getOptionalAttributeChain())
      {
        inheritedAttrs.add(attr.getNameOrOID());
      }
    }
    model = (DefaultListModel)optionalAttributes.getModel();
    model.clear();
    for (String attr : optionalAttrs)
    {
      String v;
      if (inheritedAttrs.contains(attr))
      {
        v = attr+" (*)";
      }
      else
      {
        v = attr;
      }
      model.addElement(v);
      hmAttrs.put(v, schema.getAttributeType(attr.toLowerCase()));
    }
  }

  /**
   * Returns the message describing the object class type (structural, obsolete,
   * etc.) of a given object class.
   * @param oc the object class.
   * @return the message describing the object class type (structural, obsolete,
   * etc.) of the provided object class.
   */
  static Message getTypeValue(ObjectClass oc)
  {
    MessageBuilder mb = new MessageBuilder();
    switch (oc.getObjectClassType())
    {
    case ABSTRACT:
      mb.append(ABSTRACT_VALUE);
      break;
    case STRUCTURAL:
      mb.append(STRUCTURAL_VALUE);
      break;
    case AUXILIARY:
      mb.append(AUXILIARY_VALUE);
      break;
    }
    if (oc.isObsolete())
    {
      if (mb.length() > 0)
      {
        mb.append(", ");
      }
      mb.append(OBSOLETE_VALUE);
    }
    return mb.toMessage();
  }
}
