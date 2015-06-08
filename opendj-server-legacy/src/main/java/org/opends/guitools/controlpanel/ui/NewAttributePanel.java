/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.schema.AttributeUsage;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ConfigurationElementCreatedListener;
import org.opends.guitools.controlpanel.task.NewSchemaElementsTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.ui.renderer.SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * The panel displayed when the user wants to define a new attribute in the
 * schema.
 */
public class NewAttributePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2340170241535771321L;

  private static final LocalizableMessage NO_PARENT = INFO_CTRL_PANEL_NO_PARENT_FOR_ATTRIBUTE.get();
  private static final LocalizableMessage NO_MATCHING_RULE = INFO_CTRL_PANEL_NO_MATCHING_RULE_FOR_ATTRIBUTE.get();

  private final JLabel lName = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get());
  private final JLabel lParent = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_PARENT_LABEL.get());
  private final JLabel lOID = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_OID_LABEL.get());
  private final JLabel lAliases = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_ALIASES_LABEL.get());
  private final JLabel lOrigin = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_ORIGIN_LABEL.get());
  private final JLabel lFile = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_FILE_LABEL.get());
  private final JLabel lDescription = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_DESCRIPTION_LABEL.get());
  private final JLabel lUsage = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_USAGE_LABEL.get());
  private final JLabel lSyntax = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_LABEL.get());
  private final JLabel lApproximate = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_APPROXIMATE_MATCHING_RULE_LABEL.get());
  private final JLabel lEquality = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_EQUALITY_MATCHING_RULE_LABEL.get());
  private final JLabel lOrdering = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_ORDERING_MATCHING_RULE_LABEL.get());
  private final JLabel lSubstring = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_SUBSTRING_MATCHING_RULE_LABEL.get());
  private final JLabel lType = Utilities.createPrimaryLabel();

  private final JLabel[] labels = { lName, lParent, lOID, lAliases, lOrigin, lFile, lDescription, lUsage, lSyntax,
    lApproximate, lEquality, lOrdering, lSubstring, lType };

  private final JTextField name = Utilities.createMediumTextField();
  private final JComboBox parent = Utilities.createComboBox();
  private final JTextField oid = Utilities.createMediumTextField();
  private final JTextField aliases = Utilities.createLongTextField();
  private final JTextField description = Utilities.createLongTextField();
  private final JTextField origin = Utilities.createLongTextField();
  private final JTextField file = Utilities.createLongTextField();
  private final JComboBox<AttributeUsage> usage = Utilities.createComboBox();
  private final JComboBox syntax = Utilities.createComboBox();
  private final JComboBox approximate = Utilities.createComboBox();
  private final JComboBox equality = Utilities.createComboBox();
  private final JComboBox ordering = Utilities.createComboBox();
  private final JComboBox substring = Utilities.createComboBox();
  private final JCheckBox nonModifiable = Utilities.createCheckBox(
      INFO_CTRL_PANEL_ATTRIBUTE_NON_MODIFIABLE_LABEL.get());
  private final JCheckBox singleValued = Utilities.createCheckBox(INFO_CTRL_PANEL_ATTRIBUTE_SINGLE_VALUED_LABEL.get());
  private final JCheckBox collective = Utilities.createCheckBox(INFO_CTRL_PANEL_ATTRIBUTE_COLLECTIVE_LABEL.get());
  private final JCheckBox obsolete = Utilities.createCheckBox(INFO_CTRL_PANEL_ATTRIBUTE_OBSOLETE_LABEL.get());

  private Schema schema;

  private final Component relativeComponent;

  /**
   * Constructor of the new attribute panel.
   *
   * @param relativeComponent
   *          the component relative to which the dialog containing this panel
   *          must be centered.
   */
  public NewAttributePanel(Component relativeComponent)
  {
    this.relativeComponent = relativeComponent;
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_NEW_ATTRIBUTE_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    List<Syntax> newSyntaxes = new ArrayList<>();
    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();

    final boolean firstSchema = schema == null;
    final boolean[] repack = { firstSchema };
    final boolean[] error = { false };

    boolean schemaChanged;
    if (schema != null && s != null)
    {
      schemaChanged = !ServerDescriptor.areSchemasEqual(s, schema);
    }
    else if (schema == null && s != null)
    {
      schemaChanged = true;
    }
    else if (s == null && schema != null)
    {
      schemaChanged = false;
    }
    else
    {
      schemaChanged = false;
    }
    if (schemaChanged)
    {
      schema = s;
      Map<String, Syntax> syntaxNameMap = new HashMap<>();

      for (String key : schema.getSyntaxes().keySet())
      {
        Syntax syntax = schema.getSyntax(key);
        String name = syntax.getName();
        if (name == null)
        {
          name = syntax.getOID();
        }
        syntaxNameMap.put(name, syntax);
      }

      SortedSet<String> orderedKeys = new TreeSet<>(new LowerCaseComparator());
      orderedKeys.addAll(syntaxNameMap.keySet());
      for (String key : orderedKeys)
      {
        newSyntaxes.add(syntaxNameMap.get(key));
      }
      updateComboBoxModel(newSyntaxes, (DefaultComboBoxModel) syntax.getModel());

      Map<String, AttributeType> attributeNameMap = new HashMap<>();
      for (String key : schema.getAttributeTypes().keySet())
      {
        AttributeType attr = schema.getAttributeType(key);
        attributeNameMap.put(attr.getNameOrOID(), attr);
      }
      orderedKeys.clear();
      orderedKeys.addAll(attributeNameMap.keySet());
      List<Object> newParents = new ArrayList<>();
      for (String key : orderedKeys)
      {
        newParents.add(attributeNameMap.get(key));
      }
      newParents.add(0, NO_PARENT);
      updateComboBoxModel(newParents, (DefaultComboBoxModel) parent.getModel());

      List<MatchingRule> approximateElements = new ArrayList<>();
      List<MatchingRule> equalityElements = new ArrayList<>();
      List<MatchingRule> orderingElements = new ArrayList<>();
      List<MatchingRule> substringElements = new ArrayList<>();
      Map<String, MatchingRule> matchingRuleNameMap = new HashMap<>();
      for (String key : schema.getMatchingRules().keySet())
      {
        MatchingRule rule = schema.getMatchingRule(key);
        matchingRuleNameMap.put(rule.getNameOrOID(), rule);
      }

      orderedKeys.clear();
      orderedKeys.addAll(matchingRuleNameMap.keySet());
      for (String key : orderedKeys)
      {
        MatchingRule matchingRule = matchingRuleNameMap.get(key);
        if (Utilities.isApproximateMatchingRule(matchingRule))
        {
          approximateElements.add(matchingRule);
        }
        else if (Utilities.isEqualityMatchingRule(matchingRule))
        {
          equalityElements.add(matchingRule);
        }
        else if (Utilities.isOrderingMatchingRule(matchingRule))
        {
          orderingElements.add(matchingRule);
        }
        else if (Utilities.isSubstringMatchingRule(matchingRule))
        {
          substringElements.add(matchingRule);
        }
      }
      JComboBox[] combos = { approximate, equality, ordering, substring };
      List<List<MatchingRule>> ruleNames = new ArrayList<>();
      ruleNames.add(approximateElements);
      ruleNames.add(equalityElements);
      ruleNames.add(orderingElements);
      ruleNames.add(substringElements);
      for (int i = 0; i < combos.length; i++)
      {
        DefaultComboBoxModel model = (DefaultComboBoxModel) combos[i].getModel();
        List<Object> el = new ArrayList<Object>(ruleNames.get(i));
        el.add(0, model.getSize() == 0 ? NO_MATCHING_RULE : model.getElementAt(0));
        updateComboBoxModel(el, model);
      }
    }
    else if (schema == null)
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
      repack[0] = true;
      error[0] = true;
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        setEnabledOK(!error[0]);
        errorPane.setVisible(error[0]);
        if (firstSchema)
        {
          for (int i = 0; i < syntax.getModel().getSize(); i++)
          {
            Syntax syn = (Syntax) syntax.getModel().getElementAt(i);
            if ("DirectoryString".equals(syn.getName()))
            {
              syntax.setSelectedIndex(i);
              break;
            }
          }
        }
        else
        {
          updateDefaultMatchingRuleNames();
        }

        if (repack[0])
        {
          packParentDialog();
          if (relativeComponent != null)
          {
            Utilities.centerGoldenMean(Utilities.getParentDialog(NewAttributePanel.this), relativeComponent);
          }
        }
      }
    });
    if (!error[0])
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
          isLocal() ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_TO_CREATE_ATTRIBUTE_SUMMARY.get()
                    : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    }
  }

  @Override
  public void okClicked()
  {
    List<LocalizableMessage> errors = new ArrayList<>();
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getAttributeName();
    LocalizableMessageBuilder err = new LocalizableMessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_REQUIRED.get());
      setPrimaryInvalid(lName);
    }
    else if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
    {
      errors.add(ERR_CTRL_PANEL_INVALID_ATTRIBUTE_NAME.get(err));
      setPrimaryInvalid(lName);
      err = new LocalizableMessageBuilder();
    }
    else
    {
      LocalizableMessage elementType = getSchemaElementType(n, schema);
      if (elementType != null)
      {
        errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_ALREADY_IN_USE.get(n, elementType));
        setPrimaryInvalid(lName);
      }
    }

    n = oid.getText().trim();
    if (n.length() > 0)
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err));
        setPrimaryInvalid(lOID);
        err = new LocalizableMessageBuilder();
      }
      else
      {
        LocalizableMessage elementType = getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n, elementType));
          setPrimaryInvalid(lOID);
        }
      }
    }

    if (aliases.getText().trim().length() > 0)
    {
      String[] al = aliases.getText().split(",");
      if (al.length > 0)
      {
        for (String alias : al)
        {
          if (alias.trim().length() == 0)
          {
            errors.add(ERR_CTRL_PANEL_EMPTY_ALIAS.get());
            setPrimaryInvalid(lAliases);
          }
          else
          {
            LocalizableMessage elementType = getSchemaElementType(alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n, elementType));
              setPrimaryInvalid(lAliases);
            }
          }
        }
      }
    }

    setPrimaryValid(lUsage);
    if (nonModifiable.isSelected() && AttributeUsage.USER_APPLICATIONS.equals(usage.getSelectedItem()))
    {
      errors.add(ERR_NON_MODIFIABLE_CANNOT_BE_USER_APPLICATIONS.get());
      setPrimaryInvalid(lUsage);
    }

    ProgressDialog dlg = new ProgressDialog(Utilities.createFrame(), Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_NEW_ATTRIBUTE_PANEL_TITLE.get(), getInfo());
    NewSchemaElementsTask newTask = null;
    if (errors.isEmpty())
    {
      Set<AttributeType> attributes = new LinkedHashSet<>();
      attributes.add(getAttribute());
      Set<ObjectClass> ocs = new LinkedHashSet<>(0);
      newTask = new NewSchemaElementsTask(getInfo(), dlg, ocs, attributes);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      for (ConfigurationElementCreatedListener listener : getConfigurationElementCreatedListeners())
      {
        newTask.addConfigurationElementCreatedListener(listener);
      }
    }
    if (errors.isEmpty())
    {
      String attrName = getAttributeName();
      launchOperation(newTask,
                      INFO_CTRL_PANEL_CREATING_ATTRIBUTE_SUMMARY.get(attrName),
                      INFO_CTRL_PANEL_CREATING_ATTRIBUTE_COMPLETE.get(),
                      INFO_CTRL_PANEL_CREATING_ATTRIBUTE_SUCCESSFUL.get(attrName),
                      ERR_CTRL_PANEL_CREATING_ATTRIBUTE_ERROR_SUMMARY.get(),
                      ERR_CTRL_PANEL_CREATING_ATTRIBUTE_ERROR_DETAILS.get(attrName),
                      null,
                      dlg);
      dlg.setVisible(true);
      name.setText("");
      oid.setText("");
      description.setText("");
      aliases.setText("");
      name.grabFocus();
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the message representing the schema element type.
   *
   * @param name
   *          the name of the schema element.
   * @param schema
   *          the schema.
   * @return the message representing the schema element type.
   */
  static LocalizableMessage getSchemaElementType(String name, Schema schema)
  {
    final String lowerCase = name.toLowerCase();
    if (schema.getAttributeType(lowerCase) != null)
    {
      return INFO_CTRL_PANEL_TYPE_ATTRIBUTE.get();
    }
    else if (schema.getObjectClass(lowerCase) != null)
    {
      return INFO_CTRL_PANEL_TYPE_OBJECT_CLASS.get();
    }
    else if (schema.getSyntax(lowerCase) != null)
    {
      return INFO_CTRL_PANEL_TYPE_ATTRIBUTE_SYNTAX.get();
    }
    else if (schema.getMatchingRule(lowerCase) != null)
    {
      return INFO_CTRL_PANEL_TYPE_MATCHING_RULE.get();
    }

    for (Syntax attr : schema.getSyntaxes().values())
    {
      if (name.equalsIgnoreCase(attr.getName()))
      {
        return INFO_CTRL_PANEL_TYPE_ATTRIBUTE_SYNTAX.get();
      }
    }

    for (MatchingRule rule : schema.getMatchingRules().values())
    {
      String n = rule.getNameOrOID();
      if (n != null && n.equalsIgnoreCase(name))
      {
        return INFO_CTRL_PANEL_TYPE_MATCHING_RULE.get();
      }
    }

    return null;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    Utilities.setRequiredIcon(lName);

    gbc.gridwidth = 2;
    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.weighty = 0.0;
    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JLabel requiredLabel = createRequiredLabel();
    gbc.insets.bottom = 10;
    add(requiredLabel, gbc);

    gbc.gridy++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.bottom = 0;

    JComboBox[] comboBoxes = { parent, syntax, approximate, equality, ordering, substring };
    LocalizableMessage[] defaultValues =
        { NO_PARENT, LocalizableMessage.EMPTY, NO_MATCHING_RULE, NO_MATCHING_RULE, NO_MATCHING_RULE, NO_MATCHING_RULE };
    SchemaElementComboBoxCellRenderer renderer = new SchemaElementComboBoxCellRenderer(syntax);
    for (int i = 0; i < comboBoxes.length; i++)
    {
      DefaultComboBoxModel model = new DefaultComboBoxModel(new Object[] { defaultValues[i] });
      comboBoxes[i].setModel(model);
      comboBoxes[i].setRenderer(renderer);
    }

    DefaultComboBoxModel<AttributeUsage> model = new DefaultComboBoxModel<AttributeUsage>();
    for (AttributeUsage us : AttributeUsage.values())
    {
      model.addElement(us);
    }
    usage.setModel(model);
    usage.setSelectedItem(AttributeUsage.USER_APPLICATIONS);
    usage.setRenderer(renderer);

    Component[] basicComps = { name, oid, description, syntax };
    JLabel[] basicLabels = { lName, lOID, lDescription, lSyntax };
    JLabel[] basicInlineHelp = new JLabel[] {
      null, null, null, Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_SYNTAX_INLINE_HELP.get()) };
    add(basicLabels, basicComps, basicInlineHelp, this, gbc);

    BasicExpander[] expanders = new BasicExpander[] {
          new BasicExpander(INFO_CTRL_PANEL_EXTRA_OPTIONS_EXPANDER.get()),
          new BasicExpander(INFO_CTRL_PANEL_ATTRIBUTE_TYPE_OPTIONS_EXPANDER.get()),
          new BasicExpander(INFO_CTRL_PANEL_MATCHING_RULE_OPTIONS_EXPANDER.get()) };

    Component[][] comps = { { parent, aliases, origin, file },
                            { usage, singleValued, nonModifiable, collective, obsolete },
                            { approximate, equality, ordering, substring } };
    JLabel[][] labels ={ { lParent, lAliases, lOrigin, lFile },
                         { lUsage, lType, null, null, null },
                         { lApproximate, lEquality, lOrdering, lSubstring } };
    JLabel[][] inlineHelps = {
          { null, Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_SEPARATED_WITH_COMMAS_HELP.get()), null,
            Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_SCHEMA_FILE_ATTRIBUTE_HELP.get(File.separator)) },
          { null, null, null, null, null, null },
          { Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_MATCHING_RULE_APPROXIMATE_HELP.get()),
            Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_MATCHING_RULE_EQUALITY_HELP.get()),
            Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_MATCHING_RULE_ORDERING_HELP.get()),
            Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_MATCHING_RULE_SUBSTRING_HELP.get()) } };
    for (int i = 0; i < expanders.length; i++)
    {
      gbc.gridwidth = 2;
      gbc.gridx = 0;
      gbc.insets.left = 0;
      add(expanders[i], gbc);
      final JPanel p = new JPanel(new GridBagLayout());
      gbc.insets.left = 15;
      gbc.gridy++;
      add(p, gbc);
      gbc.gridy++;
      p.setOpaque(false);

      GridBagConstraints gbc1 = new GridBagConstraints();
      gbc1.fill = GridBagConstraints.HORIZONTAL;
      gbc1.gridy = 0;

      add(labels[i], comps[i], inlineHelps[i], p, gbc1);
      final BasicExpander expander = expanders[i];
      ChangeListener changeListener = new ChangeListener()
      {
        @Override
        public void stateChanged(ChangeEvent e)
        {
          p.setVisible(expander.isSelected());
        }
      };
      expander.addChangeListener(changeListener);
      expander.setSelected(false);
      changeListener.stateChanged(null);
    }
    addBottomGlue(gbc);

    ItemListener itemListener = new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        if (ev.getStateChange() == ItemEvent.SELECTED)
        {
          updateDefaultMatchingRuleNames();
          approximate.setSelectedIndex(0);
          substring.setSelectedIndex(0);
          equality.setSelectedIndex(0);
          ordering.setSelectedIndex(0);
        }
      }
    };
    syntax.addItemListener(itemListener);

    file.setText(ConfigConstants.FILE_USER_SCHEMA_ELEMENTS);
  }

  private void updateDefaultMatchingRuleNames()
  {
    Syntax syn = (Syntax) syntax.getSelectedItem();
    if (syn != null)
    {
      MatchingRule[] rules = { syn.getApproximateMatchingRule(), syn.getSubstringMatchingRule(),
        syn.getEqualityMatchingRule(), syn.getOrderingMatchingRule() };
      JComboBox[] combos = { approximate, substring, equality, ordering };
      for (int i = 0; i < rules.length; i++)
      {
        DefaultComboBoxModel model = (DefaultComboBoxModel) combos[i].getModel();
        int index = combos[i].getSelectedIndex();
        if (model.getSize() > 0)
        {
          model.removeElementAt(0);
        }

        final LocalizableMessage msg =
            rules[i] != null ? INFO_CTRL_PANEL_DEFAULT_DEFINED_IN_SYNTAX.get(rules[i].getNameOrOID())
                             : NO_MATCHING_RULE;
        model.insertElementAt(msg, 0);
        combos[i].setSelectedIndex(index);
      }
    }
  }

  private String getAttributeName()
  {
    return name.getText().trim();
  }

  private String getOID()
  {
    String o = oid.getText().trim();
    if (o.length() == 0)
    {
      o = getAttributeName() + "-oid";
    }
    return o;
  }

  private List<String> getAliases()
  {
    List<String> al = new ArrayList<>();
    String s = aliases.getText().trim();
    if (s.length() > 0)
    {
      String[] a = s.split(",");
      for (String alias : a)
      {
        al.add(alias.trim());
      }
    }
    return al;
  }

  private List<String> getAllNames()
  {
    List<String> al = new ArrayList<>();
    al.add(getAttributeName());
    al.addAll(getAliases());
    return al;
  }

  private AttributeType getSuperior()
  {
    Object o = parent.getSelectedItem();
    if (NO_PARENT.equals(o))
    {
      return (AttributeType) o;
    }
    return null;
  }

  private MatchingRule getApproximateMatchingRule()
  {
    return getMatchingRule(approximate);
  }

  private MatchingRule getEqualityMatchingRule()
  {
    return getMatchingRule(equality);
  }

  private MatchingRule getSubstringMatchingRule()
  {
    return getMatchingRule(substring);
  }

  private MatchingRule getOrderingMatchingRule()
  {
    return getMatchingRule(ordering);
  }

  private MatchingRule getMatchingRule(JComboBox comboBox)
  {
    if (comboBox.getSelectedIndex() != 0)
    {
      return (MatchingRule) comboBox.getSelectedItem();
    }
    return null;
  }

  private Map<String, List<String>> getExtraProperties()
  {
    final Map<String, List<String>> map = new HashMap<>();
    addExtraPropertyFromTextField(file, ServerConstants.SCHEMA_PROPERTY_FILENAME, map);
    addExtraPropertyFromTextField(origin, ServerConstants.SCHEMA_PROPERTY_ORIGIN, map);
    return map;
  }

  private void addExtraPropertyFromTextField(
      final JTextField value, final String key, final Map<String, List<String>> map)
  {
    final String trimmedValue = value.getText().trim();
    if (!trimmedValue.trim().isEmpty())
    {
      map.put(key, Arrays.asList(trimmedValue));
    }
  }

  private String getDescription()
  {
    return description.getText().trim();
  }

  private AttributeType getAttribute()
  {
    return new AttributeType("",
                             getAttributeName(),
                             getAllNames(),
                             getOID(),
                             getDescription(),
                             getSuperior(),
                             (Syntax) syntax.getSelectedItem(),
                             getApproximateMatchingRule(),
                             getEqualityMatchingRule(),
                             getOrderingMatchingRule(),
                             getSubstringMatchingRule(),
                             (AttributeUsage) usage.getSelectedItem(),
                             collective.isSelected(),
                             nonModifiable.isSelected(),
                             obsolete.isSelected(),
                             singleValued.isSelected(),
                             getExtraProperties());
  }
}
