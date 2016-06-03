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

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;

/** The panel that displays a standard object class definition. */
public class StandardObjectClassPanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 5561268287795223026L;
  private TitlePanel titlePanel = new TitlePanel(LocalizableMessage.EMPTY, LocalizableMessage.EMPTY);

  private JLabel lParent;

  private JLabel name = Utilities.createDefaultLabel();
  private JLabel parent = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel origin = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JLabel aliases = Utilities.createDefaultLabel();
  private JLabel type = Utilities.createDefaultLabel();
  private JList requiredAttributes = new JList(new DefaultListModel());
  private JList optionalAttributes = new JList(new DefaultListModel());

  private static LocalizableMessage ABSTRACT_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_ABSTRACT_LABEL.get();
  private static LocalizableMessage STRUCTURAL_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_STRUCTURAL_LABEL.get();
  private static LocalizableMessage AUXILIARY_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_AUXILIARY_LABEL.get();
  private static LocalizableMessage OBSOLETE_VALUE =
    INFO_CTRL_PANEL_OBJECTCLASS_OBSOLETE_LABEL.get();

  private Map<String, AttributeType> hmAttrs = new HashMap<>();

  /** Default constructor of the panel. */
  public StandardObjectClassPanel()
  {
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_STANDARD_OBJECTCLASS_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return requiredAttributes;
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

    requiredAttributes.setVisibleRowCount(5);
    optionalAttributes.setVisibleRowCount(9);

    LocalizableMessage[] labels = {
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
      if (i == 1)
      {
        lParent = l;
      }
      c.add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      c.add(values[i], gbc);
      gbc.gridy ++;
    }
    labels = new LocalizableMessage[] {
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
      MouseAdapter clickListener = new MouseAdapter()
      {
        @Override
        public void mouseClicked(MouseEvent ev)
        {
          if (ev.getClickCount() == 1)
          {
            attrSelected(list);
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
            attrSelected(list);
          }
        }
      };
      list.addKeyListener(keyListener);
    }
  }

  /**
   * Returns the message describing the schema element origin (file, RFC, etc.).
   * @param element the schema element.
   * @return the message describing the schema element origin (file, RFC, etc.).
   */
  static LocalizableMessage getOrigin(SomeSchemaElement element)
  {
    LocalizableMessageBuilder returnValue = new LocalizableMessageBuilder();
    String fileName = element.getSchemaFile();
    String xOrigin = element.getOrigin();
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
    if (oc == null || schema == null)
    {
      // Ignore: this is called to get an initial panel size.
      return;
    }
    hmAttrs.clear();
    String n = oc.getNameOrOID();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(LocalizableMessage.raw(n));
    name.setText(n);
    parent.setText(getSuperiorText(oc));
    oid.setText(oc.getOID());
    origin.setText(getOrigin(new SomeSchemaElement(oc)).toString());
    n = oc.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);
    ArrayList<String> otherNames = new ArrayList<>();
    Iterable<String> ocNames = oc.getNames();
    String primaryName = oc.getNameOrOID();
    if (primaryName == null)
    {
      primaryName = "";
    }
    for (String name : ocNames)
    {
      if (!name.equalsIgnoreCase(primaryName))
      {
        otherNames.add(toLowerCase(name));
      }
    }
    if (!otherNames.isEmpty())
    {
      n = Utilities.getStringFromCollection(otherNames, ", ");
    }
    else
    {
      n = NOT_APPLICABLE.toString();
    }
    aliases.setText(n);

    type.setText(getTypeValue(oc).toString());

    Comparator<String> lowerCaseComparator = new LowerCaseComparator();
    SortedSet<String> requiredAttrs = new TreeSet<>(lowerCaseComparator);
    Set<String> inheritedAttrs = new HashSet<>();
    for (AttributeType attr : oc.getRequiredAttributes())
    {
      requiredAttrs.add(attr.getNameOrOID());
    }
    Set<ObjectClass> parents = oc.getSuperiorClasses();
    if (parents != null)
    {
      if (parents.size() > 1)
      {
        lParent.setText(
            INFO_CTRL_PANEL_OBJECTCLASS_PARENTS_LABEL.get().toString());
      }
      else
      {
        lParent.setText(
            INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get().toString());
      }
      for (ObjectClass parent : parents)
      {
        for (AttributeType attr : parent.getRequiredAttributes())
        {
          inheritedAttrs.add(attr.getNameOrOID());
        }
      }
    }
    else
    {
      lParent.setText(
          INFO_CTRL_PANEL_OBJECTCLASS_PARENT_LABEL.get().toString());
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

    SortedSet<String> optionalAttrs = new TreeSet<>(lowerCaseComparator);
    inheritedAttrs = new HashSet<>();
    for (AttributeType attr : oc.getOptionalAttributes())
    {
      optionalAttrs.add(attr.getNameOrOID());
    }
    if (parents != null)
    {
      for (ObjectClass parent : parents)
      {
        for (AttributeType attr : parent.getOptionalAttributes())
        {
          inheritedAttrs.add(attr.getNameOrOID());
        }
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

  private String getSuperiorText(ObjectClass oc)
  {
    String n;
    Set<ObjectClass> superiors = oc.getSuperiorClasses();
    if (superiors == null)
    {
      n = null;
    }
    else
    {
      if (superiors.isEmpty())
      {
        n = NOT_APPLICABLE.toString();
      }
      else if (superiors.size() == 1)
      {
        n = superiors.iterator().next().getNameOrOID();
      }
      else
      {
        SortedSet<String> names = new TreeSet<>();
        for (ObjectClass superior : superiors)
        {
          names.add(superior.getNameOrOID());
        }
        n = Utilities.getStringFromCollection(names, ", ");
      }
    }
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    return n;
  }

  /**
   * Returns the message describing the object class type (structural, obsolete,
   * etc.) of a given object class.
   * @param oc the object class.
   * @return the message describing the object class type (structural, obsolete,
   * etc.) of the provided object class.
   */
  static LocalizableMessage getTypeValue(ObjectClass oc)
  {
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
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

  private void attrSelected(JList list)
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
