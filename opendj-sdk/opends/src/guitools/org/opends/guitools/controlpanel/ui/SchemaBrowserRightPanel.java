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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.SchemaElementSelectionListener;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * The panel on the right of the 'Manage Schema' panel.
 *
 */
public class SchemaBrowserRightPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 5294502011852239497L;
  private JPanel mainPanel;
  private StandardObjectClassPanel standardObjectClassPanel =
    new StandardObjectClassPanel();
  private ConfigurationObjectClassPanel configurationObjectClassPanel =
    new ConfigurationObjectClassPanel();
  private CustomObjectClassPanel customObjectClassPanel =
    new CustomObjectClassPanel();
  private StandardAttributePanel standardAttributePanel =
    new StandardAttributePanel();
  private ConfigurationAttributePanel configurationAttributePanel =
    new ConfigurationAttributePanel();
  private CustomAttributePanel customAttributePanel =
    new CustomAttributePanel();
  private MatchingRulePanel matchingRulePanel = new MatchingRulePanel();
  private AttributeSyntaxPanel attributeSyntaxPanel =
    new AttributeSyntaxPanel();

  private NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();

  private final String NOTHING_SELECTED = "Nothing Selected";


  private SchemaElementPanel schemaElementPanel;

  /**
   * Default constructor.
   *
   */
  public SchemaBrowserRightPanel()
  {
    super();
    createLayout();
  }

  /**
   * Displays a panel containing a message.
   * @param msg the message.
   *
   */
  public void displayMessage(Message msg)
  {
    schemaElementPanel = null;
    noEntryPanel.setMessage(msg);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, NOTHING_SELECTED);
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    StatusGenericPanel[] panels = {standardObjectClassPanel,
        configurationObjectClassPanel, customObjectClassPanel,
        standardAttributePanel, configurationAttributePanel,
        customAttributePanel, matchingRulePanel,
        attributeSyntaxPanel
    };
    for (StatusGenericPanel panel : panels)
    {
      panel.setInfo(info);
    }
  }

  /**
   * Adds an schema element selection listener.
   * @param listener the schema element selection listener.
   */
  public void addSchemaElementSelectionListener(
      SchemaElementSelectionListener listener)
  {
    standardObjectClassPanel.addSchemaElementSelectionListener(listener);
    configurationObjectClassPanel.addSchemaElementSelectionListener(listener);
    customObjectClassPanel.addSchemaElementSelectionListener(listener);
    standardAttributePanel.addSchemaElementSelectionListener(listener);
    configurationAttributePanel.addSchemaElementSelectionListener(listener);
    customAttributePanel.addSchemaElementSelectionListener(listener);
    matchingRulePanel.addSchemaElementSelectionListener(listener);
    attributeSyntaxPanel.addSchemaElementSelectionListener(listener);
  }

  /**
   * Removes an schema element selection listener.
   * @param listener the schema element selection listener.
   */
  public void removeSchemaElementSelectionListener(
      SchemaElementSelectionListener listener)
  {
    standardObjectClassPanel.removeSchemaElementSelectionListener(listener);
    configurationObjectClassPanel.removeSchemaElementSelectionListener(
        listener);
    customObjectClassPanel.removeSchemaElementSelectionListener(listener);
    standardAttributePanel.removeSchemaElementSelectionListener(listener);
    configurationAttributePanel.removeSchemaElementSelectionListener(listener);
    customAttributePanel.removeSchemaElementSelectionListener(listener);
    matchingRulePanel.removeSchemaElementSelectionListener(listener);
    attributeSyntaxPanel.removeSchemaElementSelectionListener(listener);
  }

  /**
   * Updates the contents of the panel with the provided standard object class.
   * @param oc the object class.
   * @param schema the schema.
   */
  public void updateStandardObjectClass(ObjectClass oc, Schema schema)
  {
    standardObjectClassPanel.update(oc, schema);
    schemaElementPanel = standardObjectClassPanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        standardObjectClassPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided configuration object
   * class.
   * @param oc the object class.
   * @param schema the schema.
   */
  public void updateConfigurationObjectClass(ObjectClass oc, Schema schema)
  {
    configurationObjectClassPanel.update(oc, schema);
    schemaElementPanel = configurationObjectClassPanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        configurationObjectClassPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided custom object class.
   * @param oc the object class.
   * @param schema the schema.
   */
  public void updateCustomObjectClass(ObjectClass oc, Schema schema)
  {
    customObjectClassPanel.update(oc, schema);
    schemaElementPanel = customObjectClassPanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        customObjectClassPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided standard attribute.
   * @param attr the attribute.
   * @param schema the schema.
   */
  public void updateStandardAttribute(AttributeType attr, Schema schema)
  {
    standardAttributePanel.update(attr, schema);
    schemaElementPanel = standardAttributePanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        standardAttributePanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided configuration
   * attribute.
   * @param attr the attribute.
   * @param schema the schema.
   */
  public void updateConfigurationAttribute(AttributeType attr, Schema schema)
  {
    configurationAttributePanel.update(attr, schema);
    schemaElementPanel = configurationAttributePanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        configurationAttributePanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided custom attribute.
   * @param attr the attribute.
   * @param schema the schema.
   */
  public void updateCustomAttribute(AttributeType attr, Schema schema)
  {
    customAttributePanel.update(attr, schema);
    schemaElementPanel = customAttributePanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        customAttributePanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided matching rule.
   * @param matchingRule the matching rule.
   * @param schema the schema.
   */
  public void updateMatchingRule(MatchingRule matchingRule, Schema schema)
  {
    matchingRulePanel.update(matchingRule, schema);
    schemaElementPanel = matchingRulePanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        matchingRulePanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the provided attribute syntax.
   * @param syntax the attribute syntax.
   * @param schema the schema.
   */
  public void updateAttributeSyntax(AttributeSyntax syntax, Schema schema)
  {
    attributeSyntaxPanel.update(syntax, schema);
    schemaElementPanel = attributeSyntaxPanel;
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        attributeSyntaxPanel.getTitle().toString());
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    CardLayout cardLayout = new CardLayout();
    mainPanel = new JPanel(cardLayout);
    mainPanel.setOpaque(false);
    noEntryPanel.setMessage(
        INFO_CTRL_PANEL_NO_SCHEMA_ITEM_SELECTED_LABEL.get());
    mainPanel.add(noEntryPanel, NOTHING_SELECTED);
    StatusGenericPanel[] panelsWithScroll =
    {
        standardObjectClassPanel,
        configurationObjectClassPanel,
        standardAttributePanel,
        configurationAttributePanel,
        matchingRulePanel,
        attributeSyntaxPanel
    };
    StatusGenericPanel[] panelsWithNoScroll =
    {
        customObjectClassPanel,
        customAttributePanel
    };
    for (StatusGenericPanel panel : panelsWithScroll)
    {
      mainPanel.add(Utilities.createBorderLessScrollBar(panel),
          panel.getTitle().toString());
    }
    for (StatusGenericPanel panel : panelsWithNoScroll)
    {
      mainPanel.add(panel, panel.getTitle().toString());
    }
    cardLayout.show(mainPanel, NOTHING_SELECTED);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mainPanel, gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No ok button
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_SCHEMA_BROWSER_RIGHT_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    // TODO
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Method used to know if there are unsaved changes or not.  It is used by
   * the schema selection listener when the user changes the selection.
   * @return <CODE>true</CODE> if there are unsaved changes (and so the
   * selection of the schema should be canceled) and <CODE>false</CODE>
   * otherwise.
   */
  public boolean mustCheckUnsavedChanges()
  {
    return (schemaElementPanel != null) &&
    schemaElementPanel.mustCheckUnsavedChanges();
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   * @return the value telling whether the user chose to save the changes in the
   * panel, to not save them or simply cancelled the selection in the tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    if (schemaElementPanel == null)
    {
     return UnsavedChangesDialog.Result.DO_NOT_SAVE;
    }
    else
    {
      return schemaElementPanel.checkUnsavedChanges();
    }
  }
}
