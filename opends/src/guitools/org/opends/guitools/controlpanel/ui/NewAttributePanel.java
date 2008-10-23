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
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.task.SchemaTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import
org.opends.guitools.controlpanel.ui.renderer.SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.Attributes;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.Entry;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * The panel displayed when the user wants to define a new attribute in the
 * schema.
 *
 */
public class NewAttributePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2340170241535771321L;
  private JLabel lName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get());
  private JLabel lParent = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_PARENT_LABEL.get());
  private JLabel lOID = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_OID_LABEL.get());
  private JLabel lAliases = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_ALIASES_LABEL.get());
  private JLabel lOrigin = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_ORIGIN_LABEL.get());
  private JLabel lFile = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_FILE_LABEL.get());
  private JLabel lDescription = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_DESCRIPTION_LABEL.get());
  private JLabel lUsage = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_USAGE_LABEL.get());
  private JLabel lSyntax = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_SYNTAX_LABEL.get());
  private JLabel lApproximate = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_APPROXIMATE_MATCHING_RULE_LABEL.get());
  private JLabel lEquality = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_EQUALITY_MATCHING_RULE_LABEL.get());
  private JLabel lOrdering = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_ORDERING_MATCHING_RULE_LABEL.get());
  private JLabel lSubstring = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_SUBSTRING_MATCHING_RULE_LABEL.get());
  private JLabel lType = Utilities.createPrimaryLabel();

  private JLabel[] labels = {lName, lParent, lOID, lAliases, lOrigin, lFile,
      lDescription, lUsage, lSyntax, lApproximate,
      lEquality, lOrdering, lSubstring, lType
  };

  private JTextField name = Utilities.createMediumTextField();
  private JComboBox parent = Utilities.createComboBox();
  private JTextField oid = Utilities.createMediumTextField();
  private JTextField aliases = Utilities.createLongTextField();
  private JTextField description = Utilities.createLongTextField();
  private JTextField origin = Utilities.createLongTextField();
  private JTextField file = Utilities.createLongTextField();
  private JComboBox usage = Utilities.createComboBox();
  private JComboBox syntax = Utilities.createComboBox();
  private JComboBox approximate = Utilities.createComboBox();
  private JComboBox equality = Utilities.createComboBox();
  private JComboBox ordering = Utilities.createComboBox();
  private JComboBox substring = Utilities.createComboBox();
  private JCheckBox nonModifiable = Utilities.createCheckBox(
      INFO_CTRL_PANEL_ATTRIBUTE_NON_MODIFIABLE_LABEL.get());
  private JCheckBox singleValued = Utilities.createCheckBox(
      INFO_CTRL_PANEL_ATTRIBUTE_SINGLE_VALUED_LABEL.get());
  private JCheckBox collective = Utilities.createCheckBox(
      INFO_CTRL_PANEL_ATTRIBUTE_COLLECTIVE_LABEL.get());
  private JCheckBox obsolete = Utilities.createCheckBox(
      INFO_CTRL_PANEL_ATTRIBUTE_OBSOLETE_LABEL.get());

  private Schema schema;

  private Component relativeComponent;

  private Message NO_PARENT = INFO_CTRL_PANEL_NO_PARENT_FOR_ATTRIBUTE.get();
  private Message NO_MATCHING_RULE =
    INFO_CTRL_PANEL_NO_MATCHING_RULE_FOR_ATTRIBUTE.get();

  /**
   * Constructor of the new attribute panel.
   * @param relativeComponent the component relative to which the dialog
   * containing this panel must be centered.
   */
  public NewAttributePanel(Component relativeComponent)
  {
    super();
    this.relativeComponent = relativeComponent;
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_ATTRIBUTE_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ArrayList<AttributeSyntax> newSyntaxes = new ArrayList<AttributeSyntax>();

    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();

    final boolean firstSchema = schema == null;
    final boolean[] repack = {firstSchema};
    final boolean[] error = {false};

    if (s != null)
    {
      schema = s;

     HashMap<String, AttributeSyntax> syntaxNameMap = new HashMap<String,
     AttributeSyntax>();
     for (String key : schema.getSyntaxes().keySet())
     {
       AttributeSyntax syntax = schema.getSyntax(key);
       String name = syntax.getSyntaxName();
       if (name == null)
       {
         name = syntax.getOID();
       }
       syntaxNameMap.put(name, syntax);
     }

      SortedSet<String> orderedKeys =
        new TreeSet<String>(syntaxNameMap.keySet());
      for (String key : orderedKeys)
      {
        newSyntaxes.add(syntaxNameMap.get(key));
      }
      updateComboBoxModel(newSyntaxes,
          ((DefaultComboBoxModel)syntax.getModel()));

      HashMap<String, AttributeType> attributeNameMap = new HashMap<String,
      AttributeType>();
      for (String key : schema.getAttributeTypes().keySet())
      {
        AttributeType attr = schema.getAttributeType(key);
        attributeNameMap.put(attr.getNameOrOID(), attr);
      }
      orderedKeys.clear();
      orderedKeys.addAll(attributeNameMap.keySet());
      ArrayList<Object> newParents = new ArrayList<Object>();
      for (String key : orderedKeys)
      {
        newParents.add(attributeNameMap.get(key));
      }
      newParents.add(0, NO_PARENT);
      updateComboBoxModel(newParents,
          ((DefaultComboBoxModel)parent.getModel()));

      ArrayList<MatchingRule> approximateElements =
        new ArrayList<MatchingRule>();
      ArrayList<MatchingRule> equalityElements = new ArrayList<MatchingRule>();
      ArrayList<MatchingRule> orderingElements = new ArrayList<MatchingRule>();
      ArrayList<MatchingRule> substringElements = new ArrayList<MatchingRule>();

      HashMap<String, MatchingRule> matchingRuleNameMap = new HashMap<String,
      MatchingRule>();
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
        if (matchingRule instanceof ApproximateMatchingRule)
        {
          approximateElements.add(matchingRule);
        }
        else if (matchingRule instanceof EqualityMatchingRule)
        {
          equalityElements.add(matchingRule);
        }
        else if (matchingRule instanceof OrderingMatchingRule)
        {
          orderingElements.add(matchingRule);
        }
        else if (matchingRule instanceof SubstringMatchingRule)
        {
          substringElements.add(matchingRule);
        }
      }
      JComboBox[] combos = {approximate, equality, ordering, substring};
      ArrayList<ArrayList<MatchingRule>> ruleNames =
        new ArrayList<ArrayList<MatchingRule>>();
      ruleNames.add(approximateElements);
      ruleNames.add(equalityElements);
      ruleNames.add(orderingElements);
      ruleNames.add(substringElements);
      for (int i=0; i<combos.length; i++)
      {
        DefaultComboBoxModel model = (DefaultComboBoxModel)combos[i].getModel();
        ArrayList<Object> el = new ArrayList<Object>();
        el.addAll(ruleNames.get(i));
        if (model.getSize() == 0)
        {
          el.add(0, NO_MATCHING_RULE);
        }
        else
        {
          el.add(0, model.getElementAt(0));
        }
        updateComboBoxModel(el, model);
      }
    }
    else
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
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        setEnabledOK(!error[0]);
        errorPane.setVisible(error[0]);
        if (firstSchema)
        {
          for (int i=0; i<syntax.getModel().getSize(); i++)
          {
            AttributeSyntax syn =
              (AttributeSyntax)syntax.getModel().getElementAt(i);
            if ("DirectoryString".equals(syn.getSyntaxName()))
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
            Utilities.centerGoldenMean(
                Utilities.getParentDialog(NewAttributePanel.this),
                relativeComponent);
          }
        }
      }
    });
    if (!error[0])
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
     INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_TO_CREATE_ATTRIBUTE_SUMMARY.get());
    }
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getAttributeName();
    MessageBuilder err = new MessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_REQUIRED.get());
    }
    else if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
    {
      errors.add(ERR_CTRL_PANEL_INVALID_ATTRIBUTE_NAME.get(err.toString()));
      err = new MessageBuilder();
    }
    else
    {
      Message elementType = getSchemaElementType(n, schema);
      if (elementType != null)
      {
        errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_ALREADY_IN_USE.get(n,
            elementType.toString()));
      }
    }

    n = oid.getText().trim();
    if (n.length() > 0)
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err.toString()));
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n,
              elementType.toString()));
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
          }
          else
          {
            Message elementType = getSchemaElementType(alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n,
                  elementType.toString()));
            }
          }
        }
      }
    }

    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_NEW_ATTRIBUTE_PANEL_TITLE.get(), getInfo());
    NewAttributeTask newTask = null;
    if (errors.size() == 0)
    {
      newTask = new NewAttributeTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
    }
    if (errors.size() == 0)
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
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Returns the message representing the schema element type.
   * @param name the name of the schema element.
   * @param schema the schema.
   * @return the message representing the schema element type.
   */
  static Message getSchemaElementType(String name, Schema schema)
  {
    if (schema.getAttributeType(name.toLowerCase()) != null)
    {
      return INFO_CTRL_PANEL_TYPE_ATTRIBUTE.get();
    }
    else if (schema.getObjectClass(name.toLowerCase()) != null)
    {
      return INFO_CTRL_PANEL_TYPE_OBJECT_CLASS.get();
    }
    else if (schema.getSyntax(name.toLowerCase()) != null)
    {
      return INFO_CTRL_PANEL_TYPE_ATTRIBUTE_SYNTAX.get();
    }
    else if (schema.getMatchingRule(name.toLowerCase()) != null)
    {
      return INFO_CTRL_PANEL_TYPE_MATCHING_RULE.get();
    }

    for (AttributeSyntax attr : schema.getSyntaxes().values())
    {
      String n = attr.getSyntaxName();
      if (n != null)
      {
        if (n.equalsIgnoreCase(name))
        {
          return INFO_CTRL_PANEL_TYPE_ATTRIBUTE_SYNTAX.get();
        }
      }
    }

    for (MatchingRule rule : schema.getMatchingRules().values())
    {
      String n = rule.getName();
      if (n != null)
      {
        if (n.equalsIgnoreCase(name))
        {
          return INFO_CTRL_PANEL_TYPE_MATCHING_RULE.get();
        }
      }
    }

    return null;
  }


  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    Utilities.setRequiredIcon(lName);

    gbc.gridwidth = 2;
    gbc.gridy = 0;
    addErrorPane(gbc);

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weighty = 0.0;
    gbc.gridx = 1;
    gbc.anchor = GridBagConstraints.EAST;
    gbc.fill = GridBagConstraints.NONE;
    JLabel requiredLabel = createRequiredLabel();
    gbc.insets.bottom = 10;
    add(requiredLabel, gbc);

    gbc.gridy ++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets.bottom = 0;

    JComboBox[] comboBoxes = {parent, syntax, approximate,
        equality, ordering, substring};
    Message[] defaultValues = {NO_PARENT, Message.EMPTY, NO_MATCHING_RULE,
        NO_MATCHING_RULE, NO_MATCHING_RULE, NO_MATCHING_RULE
    };
    SchemaElementComboBoxCellRenderer renderer = new
    SchemaElementComboBoxCellRenderer(syntax);
    for (int i=0; i<comboBoxes.length; i++)
    {
      DefaultComboBoxModel model = new DefaultComboBoxModel(
          new Object[]{defaultValues[i]});
      comboBoxes[i].setModel(model);
      comboBoxes[i].setRenderer(renderer);
    }

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (AttributeUsage us : AttributeUsage.values())
    {
      model.addElement(us);
    }
    usage.setModel(model);
    usage.setSelectedItem(AttributeUsage.USER_APPLICATIONS);
    usage.setRenderer(renderer);

    Component[] basicComps = {name, oid, description,
        syntax};
    JLabel[] basicLabels = {lName, lOID, lDescription, lSyntax};
    JLabel[] basicInlineHelp = new JLabel[] {null, null, null,
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_SYNTAX_INLINE_HELP.get())};
    add(basicLabels, basicComps, basicInlineHelp, this, gbc);

    BasicExpander[] expanders = new BasicExpander[] {
        new BasicExpander(INFO_CTRL_PANEL_EXTRA_OPTIONS_EXPANDER.get()),
        new BasicExpander(
            INFO_CTRL_PANEL_ATTRIBUTE_TYPE_OPTIONS_EXPANDER.get()),
        new BasicExpander(INFO_CTRL_PANEL_MATCHING_RULE_OPTIONS_EXPANDER.get())
    };

    Component[][] comps = {{parent, aliases, origin, file},
        {usage, singleValued, nonModifiable, collective, obsolete},
        {approximate, equality, ordering, substring}};
    JLabel[][] labels = {{lParent, lAliases, lOrigin, lFile},
        {lUsage, lType, null, null, null},
        {lApproximate, lEquality, lOrdering, lSubstring}};
    JLabel[][] inlineHelps = {{null,
      Utilities.createInlineHelpLabel(
          INFO_CTRL_PANEL_SEPARATED_WITH_COMMAS_HELP.get()), null,
      Utilities.createInlineHelpLabel(
          INFO_CTRL_PANEL_SCHEMA_FILE_ATTRIBUTE_HELP.get(File.separator))},
      {null, null, null, null, null, null},
      {Utilities.createInlineHelpLabel(
          INFO_CTRL_PANEL_MATCHING_RULE_APPROXIMATE_HELP.get()),
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_MATCHING_RULE_EQUALITY_HELP.get()),
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_MATCHING_RULE_ORDERING_HELP.get()),
        Utilities.createInlineHelpLabel(
            INFO_CTRL_PANEL_MATCHING_RULE_SUBSTRING_HELP.get())
      }
    };
    for (int i=0; i<expanders.length; i++)
    {
      gbc.gridwidth = 2;
      gbc.gridx = 0;
      gbc.insets.left = 0;
      add(expanders[i], gbc);
      final JPanel p = new JPanel(new GridBagLayout());
      gbc.insets.left = 15;
      gbc.gridy ++;
      add(p, gbc);
      gbc.gridy ++;
      p.setOpaque(false);

      GridBagConstraints gbc1 = new GridBagConstraints();
      gbc1.fill = GridBagConstraints.HORIZONTAL;
      gbc1.gridy = 0;

      add(labels[i], comps[i], inlineHelps[i], p, gbc1);
      final BasicExpander expander = expanders[i];
      ChangeListener changeListener = new ChangeListener()
      {
        /**
         * {@inheritDoc}
         */
        public void stateChanged(ChangeEvent e)
        {
          p.setVisible(expander.isSelected());
          packParentDialog();
        }
      };
      expander.addChangeListener(changeListener);
      expander.setSelected(false);
      changeListener.stateChanged(null);
    }
    addBottomGlue(gbc);

    ItemListener itemListener = new ItemListener()
    {
      /**
       * {@inheritDoc}
       */
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
    AttributeSyntax syn = (AttributeSyntax)syntax.getSelectedItem();
    if (syn != null)
    {
      MatchingRule[] rules = {syn.getApproximateMatchingRule(),
          syn.getSubstringMatchingRule(),
          syn.getEqualityMatchingRule(),
          syn.getOrderingMatchingRule()};
      JComboBox[] combos = {approximate, substring, equality, ordering};
      for (int i=0; i<rules.length; i++)
      {
        DefaultComboBoxModel model = (DefaultComboBoxModel)combos[i].getModel();
        int index = combos[i].getSelectedIndex();
        if (rules[i] != null)
        {
          if (model.getSize() > 0)
          {
            model.removeElementAt(0);
          }
          model.insertElementAt(INFO_CTRL_PANEL_DEFAULT_DEFINED_IN_SYNTAX.get(
              rules[i].getNameOrOID()), 0);
        }
        else
        {
          if (model.getSize() > 0)
          {
            model.removeElementAt(0);
          }
          model.insertElementAt(NO_MATCHING_RULE, 0);
        }
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
      o = getAttributeName()+"-oid";
    }
    return o;
  }

  private ArrayList<String> getAliases()
  {
    ArrayList<String> al = new ArrayList<String>();
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

  private ArrayList<String> getAllNames()
  {
    ArrayList<String> al = new ArrayList<String>();
    al.add(getAttributeName());
    al.addAll(getAliases());
    return al;
  }

  private AttributeType getSuperior()
  {
    Object o = parent.getSelectedItem();
    if (NO_PARENT.equals(o))
    {
      return null;
    }
    else
    {
      return (AttributeType)o;
    }
  }

  private ApproximateMatchingRule getApproximateMatchingRule()
  {
    if (approximate.getSelectedIndex() == 0)
    {
      return null;
    }
    else
    {
      return (ApproximateMatchingRule)approximate.getSelectedItem();
    }
  }

  private EqualityMatchingRule getEqualityMatchingRule()
  {
    if (equality.getSelectedIndex() == 0)
    {
      return null;
    }
    else
    {
      return (EqualityMatchingRule)equality.getSelectedItem();
    }
  }

  private SubstringMatchingRule getSubstringMatchingRule()
  {
    if (substring.getSelectedIndex() == 0)
    {
      return null;
    }
    else
    {
      return (SubstringMatchingRule)substring.getSelectedItem();
    }
  }

  private OrderingMatchingRule getOrderingMatchingRule()
  {
    if (ordering.getSelectedIndex() == 0)
    {
      return null;
    }
    else
    {
      return (OrderingMatchingRule)ordering.getSelectedItem();
    }
  }

  private Map<String, List<String>> getExtraProperties()
  {
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    String f = file.getText().trim();
    if (f.length() > 0)
    {
      ArrayList<String> list = new ArrayList<String>();
      list.add(f);
      map.put(ServerConstants.SCHEMA_PROPERTY_FILENAME, list);
    }
    String or = origin.getText().trim();
    if (or.length() > 0)
    {
      ArrayList<String> list = new ArrayList<String>();
      list.add(or);
      map.put(ServerConstants.SCHEMA_PROPERTY_ORIGIN, list);
    }
    return map;
  }

  private AttributeType getAttribute()
  {
    AttributeType attr = new AttributeType("", getAttributeName(),
        getAllNames(),
        getOID(), description.getText().trim(),
        getSuperior(),
        (AttributeSyntax)syntax.getSelectedItem(),
        getApproximateMatchingRule(),
        getEqualityMatchingRule(),
        getOrderingMatchingRule(),
        getSubstringMatchingRule(),
        (AttributeUsage)usage.getSelectedItem(),
        collective.isSelected(), nonModifiable.isSelected(),
        obsolete.isSelected(), singleValued.isSelected(),
        getExtraProperties());

    return attr;
  }

  /**
   * The task in charge of creating the attribute.
   *
   */
  protected class NewAttributeTask extends SchemaTask
  {
    private AttributeType attribute;
    private String attributeName;
    private String attributeDefinition;
    private String attributeWithoutFileDefinition;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public NewAttributeTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      attributeName = getAttributeName();
      attributeDefinition = attribute.toString();
      AttributeType attr = getAttribute();
      attr.setExtraProperty(ServerConstants.SCHEMA_PROPERTY_FILENAME,
          (String)null);
      attributeWithoutFileDefinition = attr.toString();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.NEW_ATTRIBUTE;
    }

    /**
     * {@inheritDoc}
     */
    protected CommonSchemaElements getSchemaElement()
    {
      if (attribute == null)
      {
        attribute = getAttribute();
      }
      return attribute;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_NEW_ATTRIBUTE_TASK_DESCRIPTION.get(attributeName);
    }

    /**
     * {@inheritDoc}
     */
    protected String getSchemaFileAttributeName()
    {
      return "attributeTypes";
    }

    /**
     * {@inheritDoc}
     */
    protected String getSchemaFileAttributeValue()
    {
      if (isServerRunning())
      {
        return attributeDefinition;
      }
      else
      {
        return attributeWithoutFileDefinition;
      }
    }

    /**
     * {@inheritDoc}
     */
    protected void updateSchema() throws OpenDsException
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          printEquivalentCommandToAdd();
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_ATTRIBUTE_PROGRESS.get(
                      attributeName),
                  ColorAndFontConstants.progressFont));
        }
      });

      if (isServerRunning())
      {
        try
        {
          BasicAttribute attr =
            new BasicAttribute(getSchemaFileAttributeName());
          attr.add(getSchemaFileAttributeValue());
          ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE,
              attr);
          getInfo().getDirContext().modifyAttributes(
              ConfigConstants.DN_DEFAULT_SCHEMA_ROOT,
              new ModificationItem[]  { mod });
        }
        catch (NamingException ne)
        {
          throw new OnlineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(ne.toString()), ne);
        }
      }
      else
      {
        updateSchemaFile();
      }
      notifyConfigurationElementCreated(attribute);
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
        }
      });
    }

    /**
     * Updates the contents of the schema file.
     * @throws OpenDsException if an error occurs updating the schema file.
     */
    private void updateSchemaFile() throws OpenDsException
    {
      if (isSchemaFileDefined)
      {
        LDIFExportConfig exportConfig =
          new LDIFExportConfig(schemaFile,
                               ExistingFileBehavior.OVERWRITE);
        LDIFReader reader = null;
        Entry schemaEntry = null;
        try
        {
          reader = new LDIFReader(new LDIFImportConfig(schemaFile));
          schemaEntry = reader.readEntry();

          Modification mod = new Modification(ModificationType.ADD,
              Attributes.create(getSchemaFileAttributeName().toLowerCase(),
                  getSchemaFileAttributeValue()));
          schemaEntry.applyModification(mod);
          LDIFWriter writer = new LDIFWriter(exportConfig);
          writer.writeEntry(schemaEntry);
          exportConfig.getWriter().newLine();
        }
        catch (Throwable t)
        {
        }
        finally
        {
          if (reader != null)
          {
            try
            {
              reader.close();
            }
            catch (Throwable t)
            {
            }
          }
          if (exportConfig != null)
          {
            try
            {
              exportConfig.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
      }
      else
      {
        LDIFExportConfig exportConfig =
          new LDIFExportConfig(schemaFile,
                               ExistingFileBehavior.FAIL);
        try
        {
          ArrayList<String> lines = getSchemaEntryLines();
          for (String line : lines)
          {
            LDIFWriter.writeLDIFLine(new StringBuilder(line),
                exportConfig.getWriter(), exportConfig.getWrapColumn() > 1,
                exportConfig.getWrapColumn());
          }
          exportConfig.getWriter().newLine();
        }
        catch (Throwable t)
        {
          throw new OfflineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_SCHEMA.get(t.toString()), t);
        }
        finally
        {
          if (exportConfig != null)
          {
            try
            {
              exportConfig.close();
            }
            catch (Throwable t)
            {
            }
          }
        }
      }
    }
  }
}
