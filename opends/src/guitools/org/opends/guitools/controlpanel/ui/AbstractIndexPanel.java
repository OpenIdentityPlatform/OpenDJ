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

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.types.AttributeType;

/**
 * Abstract class used to refactor some code between the classes that are used
 * to edit/create an index.
 *
 */
public abstract class AbstractIndexPanel extends StatusGenericPanel
{
  /**
   * Title panel.
   */
  protected TitlePanel titlePanel = new TitlePanel(Message.EMPTY,
      Message.EMPTY);
  /**
   * Attributes combo box.
   */
  protected JComboBox attributes = Utilities.createComboBox();
  /**
   * Name of the index label.
   */
  protected JLabel name = Utilities.createDefaultLabel();
  /**
   * Backends label.
   */
  protected JLabel lBackend = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_BACKEND_LABEL.get());
  /**
   * Read-only backend name label.
   */
  protected JLabel backendName = Utilities.createDefaultLabel();
  /**
   * Label for attribute.
   */
  protected JLabel lAttribute =
    Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_LABEL.get());
  /**
   * Label for entry limit.
   */
  protected JLabel lEntryLimit =
    Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ENTRY_LIMIT_LABEL.get());
  /**
   * Entry limit text field.
   */
  protected JTextField entryLimit = Utilities.createShortTextField();
  /**
   * Label for type.
   */
  protected JLabel lType =
    Utilities.createPrimaryLabel(INFO_CTRL_PANEL_INDEX_TYPE_LABEL.get());
  /**
   * Approximate index type check box.
   */
  protected JCheckBox approximate =
    Utilities.createCheckBox(INFO_CTRL_PANEL_APPROXIMATE_LABEL.get());
  /**
   * Equality index type check box.
   */
  protected JCheckBox equality =
    Utilities.createCheckBox(INFO_CTRL_PANEL_EQUALITY_LABEL.get());
  /**
   * Ordering index type check box.
   */
  protected JCheckBox ordering =
    Utilities.createCheckBox(INFO_CTRL_PANEL_ORDERING_LABEL.get());
  /**
   * Presence index type check box.
   */
  protected JCheckBox presence =
    Utilities.createCheckBox(INFO_CTRL_PANEL_PRESENCE_LABEL.get());
  /**
   * Substring index type check box.
   */
  protected JCheckBox substring =
    Utilities.createCheckBox(INFO_CTRL_PANEL_SUBSTRING_LABEL.get());
  /**
   * Delete index button.
   */
  protected JButton deleteIndex =
    Utilities.createButton(INFO_CTRL_PANEL_DELETE_INDEX_LABEL.get());
  /**
   * Save changes button.
   */
  protected JButton saveChanges =
    Utilities.createButton(INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
  /**
   * Label containing some warning information (such as the fact that the index
   * cannot be edited).
   */
  protected JLabel warning = Utilities.createDefaultLabel();
  /**
   * Panel containing all the index types.
   */
  protected JPanel typesPanel = new JPanel(new GridBagLayout());

  /**
   * Message to be displayed to indicate that an index is not configurable.
   */
  protected Message NON_CONFIGURABLE_INDEX =
    INFO_CTRL_PANEL_NON_CONFIGURABLE_INDEX_LABEL.get();

  /**
   * Message to be displayed to indicate that an index has been modified.
   */
  protected Message INDEX_MODIFIED = INFO_CTRL_PANEL_INDEX_MODIFIED_LABEL.get();

  /**
   * Array of checkboxes.
   */
  protected JCheckBox[] types = {approximate, equality, ordering, presence,
      substring};

  /**
   * Array of index types that matches the array of checkboxes (types).
   */
  protected IndexType[] configTypes = {IndexType.APPROXIMATE,
      IndexType.EQUALITY, IndexType.ORDERING, IndexType.PRESENCE,
      IndexType.SUBSTRING
  };

  /**
   * Custom attributes message.
   */
  protected Message CUSTOM_ATTRIBUTES =
    INFO_CTRL_PANEL_CUSTOM_ATTRIBUTES_LABEL.get();
  /**
   * Standard attributes message.
   */
  protected Message STANDARD_ATTRIBUTES =
    INFO_CTRL_PANEL_STANDARD_ATTRIBUTES_LABEL.get();

  /**
   * Minimum value for entry limit.
   */
  protected final int MIN_ENTRY_LIMIT =
    LocalDBIndexCfgDefn.getInstance().getIndexEntryLimitPropertyDefinition().
    getLowerLimit();
  /**
   * Maximum value for entry limit.
   */
  protected final int MAX_ENTRY_LIMIT =
    LocalDBIndexCfgDefn.getInstance().getIndexEntryLimitPropertyDefinition().
    getUpperLimit();

  /**
   * Default value for entry limit.
   */
  protected final int DEFAULT_ENTRY_LIMIT = 4000;

  /**
   * Repopulates the contents of the panel with the provided attribute type. It
   * will check the checkboxes for which the attribute has a matching rule.
   * @param attr the attribute.
   */
  protected void repopulateTypesPanel(AttributeType attr)
  {
    typesPanel.removeAll();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    if (attr != null)
    {
      if (attr.getApproximateMatchingRule() != null)
      {
        typesPanel.add(approximate, gbc);
        gbc.insets.top = 10;
        gbc.gridy ++;
      }
      if (attr.getEqualityMatchingRule() != null)
      {
        typesPanel.add(equality, gbc);
        gbc.insets.top = 10;
        gbc.gridy ++;
      }
      if (attr.getOrderingMatchingRule() != null)
      {
        typesPanel.add(ordering, gbc);
        gbc.insets.top = 10;
        gbc.gridy ++;
      }
      typesPanel.add(presence, gbc);
      gbc.gridx = 1;
      gbc.weightx = 1.0;
      typesPanel.add(Box.createHorizontalGlue(), gbc);
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      gbc.gridy ++;
      gbc.insets.top = 10;
      if (attr.getSubstringMatchingRule() != null)
      {
        typesPanel.add(substring, gbc);
        gbc.insets.top = 10;
      }
    }
    typesPanel.validate();
  }

  /**
   * Creates the basic layout of the panel.
   * @param c the container of the layout.
   * @param gbc the grid bag constraints to be used.
   * @param nameReadOnly whether the panel is read-only or not.
   */
  protected void createBasicLayout(Container c, GridBagConstraints gbc,
      boolean nameReadOnly)
  {
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    addErrorPane(c, gbc);

    if (nameReadOnly)
    {
      gbc.gridy ++;
      titlePanel.setTitle(INFO_CTRL_PANEL_INDEX_DETAILS_LABEL.get());
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.anchor = GridBagConstraints.WEST;
      gbc.insets.top = 10;
      gbc.weightx = 1.0;
      JPanel p = new JPanel(new GridBagLayout());
      p.setOpaque(false);
      c.add(p, gbc);
      GridBagConstraints gbc2 = new GridBagConstraints();
      gbc2.weightx = 0.0;
      gbc2.gridwidth = GridBagConstraints.RELATIVE;
      p.add(titlePanel, gbc2);
      gbc2.gridwidth = GridBagConstraints.REMAINDER;
      gbc2.fill = GridBagConstraints.HORIZONTAL;
      gbc2.weightx = 1.0;
      p.add(Box.createHorizontalGlue(), gbc2);
    }

    gbc.gridwidth = 1;
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    c.add(lAttribute, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    if (!nameReadOnly)
    {
      attributes.addItemListener(new IgnoreItemListener(attributes));
      attributes.setRenderer(new CustomListCellRenderer(attributes));
      c.add(attributes, gbc);
    }
    else
    {
      c.add(name, gbc);
    }
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    c.add(lBackend, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    c.add(backendName, gbc);

    gbc.gridy ++;
    gbc.insets.left = 0;
    gbc.gridx = 0;
    c.add(lEntryLimit, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    c.add(p, gbc);
    GridBagConstraints gbc2 = new GridBagConstraints();
    gbc2.weightx = 0.0;
    gbc2.gridwidth = GridBagConstraints.RELATIVE;
    p.add(entryLimit, gbc2);
    gbc2.gridwidth = GridBagConstraints.REMAINDER;
    gbc2.fill = GridBagConstraints.HORIZONTAL;
    gbc2.weightx = 1.0;
    p.add(Box.createHorizontalGlue(), gbc2);


    gbc.gridx = 0;
    gbc.insets.left = 0;
    gbc.gridy ++;
    gbc.weightx = 0.0;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    c.add(lType, gbc);

    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.left = 10;
    gbc.weightx = 1.0;
    JCheckBox[] types = {approximate, equality, ordering, presence, substring};
    typesPanel.setOpaque(false);
    c.add(typesPanel, gbc);
    gbc.gridy ++;
    gbc2 = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    for (int i=0; i<types.length; i++)
    {
      types[i].setOpaque(false);
      typesPanel.add(types[i], gbc2);
      gbc2.anchor = GridBagConstraints.WEST;
      gbc2.insets.top = 10;
    }

    gbc.weighty = 1.0;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.VERTICAL;
    c.add(Box.createVerticalGlue(), gbc);
  }

  /**
   * Returns a sorted set of index types (that matches what the user selected
   * on the check boxes).
   * @return a sorted set of indexes (that matches what the user selected
   * on the check boxes).
   */
  protected SortedSet<IndexType> getTypes()
  {
    SortedSet<IndexType> indexTypes = new TreeSet<IndexType>();
    for (int i=0; i<types.length; i++)
    {
      if (types[i].isSelected())
      {
        indexTypes.add(configTypes[i]);
      }
    }
    return indexTypes;
  }

  /**
   * Returns a list of error message with the problems encountered in the data
   * provided by the user.
   * @return a list of error message with the problems encountered in the data
   * provided by the user.
   */
  protected List<Message> getErrors()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    setPrimaryValid(lEntryLimit);
    setPrimaryValid(lType);

    String newEntryLimit = entryLimit.getText().trim();

    try
    {
      int n = Integer.parseInt(newEntryLimit);
      if ((n < MIN_ENTRY_LIMIT) ||
          (n > MAX_ENTRY_LIMIT))
      {
        errors.add(ERR_CTRL_PANEL_INVALID_ENTRY_LIMIT_LABEL.get(
            MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
        setPrimaryInvalid(lEntryLimit);
      }
    }
    catch (Throwable t)
    {
      errors.add(ERR_CTRL_PANEL_INVALID_ENTRY_LIMIT_LABEL.get(
          MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
      setPrimaryInvalid(lEntryLimit);
    }

    if (getTypes().isEmpty())
    {
      errors.add(ERR_CTRL_PANEL_NO_INDEX_TYPE_SELECTED.get());
      setPrimaryInvalid(lType);
    }

    return errors;
  }
}
