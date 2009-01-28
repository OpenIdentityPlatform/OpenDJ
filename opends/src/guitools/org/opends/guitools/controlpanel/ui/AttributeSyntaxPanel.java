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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.TreeSet;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Schema;

/**
 * Panel containing information about an attribute syntax.
 *
 */
public class AttributeSyntaxPanel extends SchemaElementPanel
{
  private static final long serialVersionUID = -2426247742251904863L;
  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY,
      Message.EMPTY);
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel oid = Utilities.createDefaultLabel();
  private JLabel description = Utilities.createDefaultLabel();
  private JList usedByAttributes = new JList(new DefaultListModel());

  /**
   * Default constructor.
   *
   */
  public AttributeSyntaxPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_TITLE.get();
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
    titlePanel.setTitle(INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_DETAILS.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(titlePanel, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 8;

    Message[] labels = {INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_NAME.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_OID.get(),
        INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_DESCRIPTION.get()};
    JLabel[] values = {name, oid, description};
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
        INFO_CTRL_PANEL_USED_BY_ATTRIBUTES.get());
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
      /**
       * {@inheritDoc}
       */
      public void keyTyped(KeyEvent ev)
      {
        if ((ev.getKeyChar() == KeyEvent.VK_SPACE) ||
            (ev.getKeyChar() == KeyEvent.VK_ENTER))
        {
          usedBySelected();
        }
      }
    };
    usedByAttributes.addKeyListener(keyListener);

    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel.
   * @param syntax the attribute syntax that the panel must display.
   * @param schema the schema.
   */
  public void update(AttributeSyntax syntax, Schema schema)
  {
    String n = syntax.getSyntaxName();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);
    oid.setText(syntax.getOID());

    n = syntax.getDescription();
    if (n == null)
    {
      n = NOT_APPLICABLE.toString();
    }
    description.setText(n);

    Comparator<String> lowerCaseComparator = new LowerCaseComparator();
    TreeSet<String> attributes = new TreeSet<String>(lowerCaseComparator);
    for (AttributeType attr : schema.getAttributeTypes().values())
    {
      if (syntax == attr.getSyntax())
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
