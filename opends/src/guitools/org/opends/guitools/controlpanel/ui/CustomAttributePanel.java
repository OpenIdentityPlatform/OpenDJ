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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.
 ConfigurationElementCreatedListener;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteSchemaElementsTask;
import org.opends.guitools.controlpanel.task.ModifyAttributeTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.BasicExpander;
import org.opends.guitools.controlpanel.ui.components.TitlePanel;
import org.opends.guitools.controlpanel.ui.renderer.
 SchemaElementComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * The panel that displays a custom attribute definition.
 *
 */
public class CustomAttributePanel extends SchemaElementPanel
{
  private static final long serialVersionUID = 2850763193735843746L;
  private JButton delete;
  private JButton saveChanges;
  private AttributeType attribute;
  private String attrName;
  private ScrollPaneBorderListener scrollListener;

  private TitlePanel titlePanel = new TitlePanel(Message.EMPTY,
      Message.EMPTY);
  private JLabel lName = Utilities.createPrimaryLabel(
      INFO_CTRL_PANEL_ATTRIBUTE_NAME_LABEL.get());
  private JLabel lSuperior = Utilities.createPrimaryLabel(
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

  private JLabel[] labels = {lName, lSuperior, lOID, lAliases, lOrigin, lFile,
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
  private JList requiredBy = new JList(new DefaultListModel());
  private JList optionalBy = new JList(new DefaultListModel());

  private Set<String> lastAliases = new LinkedHashSet<String>();

  private Message NO_PARENT = INFO_CTRL_PANEL_NO_PARENT_FOR_ATTRIBUTE.get();
  private Message NO_MATCHING_RULE =
    INFO_CTRL_PANEL_NO_MATCHING_RULE_FOR_ATTRIBUTE.get();

  private Schema schema;

  private boolean ignoreChangeEvents;

  /**
   * Default constructor of the panel.
   *
   */
  public CustomAttributePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_CUSTOM_ATTRIBUTE_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  protected void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setBorder(PANEL_BORDER);
    createBasicLayout(p, gbc);
    gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    JScrollPane scroll = Utilities.createBorderLessScrollBar(p);
    scrollListener =
      ScrollPaneBorderListener.createBottomBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.gridwidth = 1;
    delete = Utilities.createButton(
        INFO_CTRL_PANEL_DELETE_ATTRIBUTE_BUTTON.get());
    delete.setOpaque(false);
    add(delete, gbc);
    delete.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteAttribute();
      }
    });

    gbc.anchor = GridBagConstraints.EAST;
    gbc.gridx ++;
    saveChanges =
      Utilities.createButton(INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
    saveChanges.setOpaque(false);
    add(saveChanges, gbc);
    saveChanges.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        ArrayList<Message> errors = new ArrayList<Message>();
        saveChanges(false, errors);
      }
    });
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
    add(basicLabels, basicComps, basicInlineHelp, c, gbc);

    BasicExpander[] expanders = new BasicExpander[] {
        new BasicExpander(INFO_CTRL_PANEL_EXTRA_OPTIONS_EXPANDER.get()),
        new BasicExpander(
            INFO_CTRL_PANEL_ATTRIBUTE_TYPE_OPTIONS_EXPANDER.get()),
        new BasicExpander(INFO_CTRL_PANEL_MATCHING_RULE_OPTIONS_EXPANDER.get())
    };

    Component[][] comps = {{parent, aliases, origin, file},
        {usage, singleValued, nonModifiable, collective, obsolete},
        {approximate, equality, ordering, substring}};
    JLabel[][] someLabels = {{lSuperior, lAliases, lOrigin, lFile},
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
      c.add(expanders[i], gbc);
      final JPanel p = new JPanel(new GridBagLayout());
      gbc.insets.left = 15;
      gbc.gridy ++;
      c.add(p, gbc);
      gbc.gridy ++;
      p.setOpaque(false);

      GridBagConstraints gbc1 = new GridBagConstraints();
      gbc1.fill = GridBagConstraints.HORIZONTAL;
      gbc1.gridy = 0;

      add(someLabels[i], comps[i], inlineHelps[i], p, gbc1);
      final BasicExpander expander = expanders[i];
      ChangeListener changeListener = new ChangeListener()
      {
        /**
         * {@inheritDoc}
         */
        public void stateChanged(ChangeEvent e)
        {
          p.setVisible(expander.isSelected());
        }
      };
      expander.addChangeListener(changeListener);
      expander.setSelected(false);
      changeListener.stateChanged(null);
    }

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

    Message[] msgs = new Message[] {
        INFO_CTRL_PANEL_REQUIRED_BY_LABEL.get(),
        INFO_CTRL_PANEL_ALLOWED_BY_LABEL.get()
        };
    JList[] lists = {requiredBy, optionalBy};

    gbc.anchor = GridBagConstraints.NORTHWEST;
    for (int i=0; i<msgs.length; i++)
    {
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      gbc.gridx = 0;
      if (i == 0)
      {
        gbc.insets.top = 15;
      }
      else
      {
        gbc.insets.top = 10;
      }
      c.add(Utilities.createPrimaryLabel(msgs[i]), gbc);
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
      c.add(Utilities.createScrollPane(lists[i]), gbc);
      gbc.gridy ++;

      final JList list = lists[i];
      MouseAdapter clickListener = new MouseAdapter()
      {
        /**
         * {@inheritDoc}
         */
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
        /**
         * {@inheritDoc}
         */
        @Override
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

    DocumentListener docListener = new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        checkEnableSaveChanges();
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent arg0)
      {
        checkEnableSaveChanges();
      }
    };
    JTextField[] tfs = {name, description, oid, aliases, origin, file};
    for (JTextField tf : tfs)
    {
      tf.getDocument().addDocumentListener(docListener);
    }

    ActionListener actionListener = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        checkEnableSaveChanges();
      }
    };

    JComboBox[] combos = {parent, usage, syntax, approximate, equality,
        ordering, substring};
    for (JComboBox combo : combos)
    {
      combo.addActionListener(actionListener);
    }

    JCheckBox[] checkBoxes = {nonModifiable, singleValued, collective,
        obsolete};
    for (JCheckBox cb : checkBoxes)
    {
      cb.addActionListener(actionListener);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean mustCheckUnsavedChanges()
  {
    return saveChanges.isEnabled();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(
          Utilities.getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
        INFO_CTRL_PANEL_UNSAVED_ATTRIBUTE_CHANGES_DETAILS.get(
           attribute.getNameOrOID()));
    Utilities.centerGoldenMean(unsavedChangesDlg,
          Utilities.getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      ArrayList<Message> errors = new ArrayList<Message>();
      saveChanges(true, errors);
      if (!errors.isEmpty())
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public void update(AttributeType attr, Schema schema)
  {
    ignoreChangeEvents = true;
    String n = attr.getPrimaryName();
    if (n == null)
    {
      n = "";
    }
    titlePanel.setDetails(Message.raw(n));
    name.setText(n);

    oid.setText(attr.getOID());
    n = attr.getDescription();
    if (n == null)
    {
      n = "";
    }
    description.setText(n);

    syntax.setSelectedItem(attr.getSyntax());

    AttributeType superior = attr.getSuperiorType();
    if (superior == null)
    {
      parent.setSelectedItem(NO_PARENT);
    }
    else
    {
      parent.setSelectedItem(superior);
    }
    Set<String> someAliases = getAliases(attr);
    lastAliases.clear();
    lastAliases.addAll(someAliases);
    this.aliases.setText(Utilities.getStringFromCollection(someAliases, ", "));

    String sOrigin = Utilities.getOrigin(attr);
    if (sOrigin == null)
    {
      sOrigin = "";
    }
    origin.setText(sOrigin);

    String sFile = attr.getSchemaFile();
    if (sFile == null)
    {
      sFile = "";
    }
    file.setText(sFile);

    usage.setSelectedItem(attr.getUsage());
    singleValued.setSelected(attr.isSingleValue());
    nonModifiable.setSelected(attr.isNoUserModification());
    collective.setSelected(attr.isCollective());
    obsolete.setSelected(attr.isObsolete());

    JComboBox[] matchingRules = {approximate, equality, ordering, substring};
    MatchingRule[] rules = {attr.getApproximateMatchingRule(),
        attr.getEqualityMatchingRule(), attr.getOrderingMatchingRule(),
        attr.getSubstringMatchingRule()
    };
    for (int i=0; i<matchingRules.length; i++)
    {
      if (rules[i] != null)
      {
        matchingRules[i].setSelectedItem(rules[i]);
      }
      else
      {
        matchingRules[i].setSelectedItem(NO_MATCHING_RULE);
      }
    }

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
    attribute = attr;
    attrName = attr.getNameOrOID();

    scrollListener.updateBorder();

    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    saveChanges.setEnabled(false);
    ignoreChangeEvents = false;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ArrayList<AttributeSyntax<?>> newSyntaxes =
      new ArrayList<AttributeSyntax<?>>();

    final ServerDescriptor desc = ev.getNewDescriptor();
    Schema s = desc.getSchema();

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
      HashMap<String, AttributeSyntax<?>> syntaxNameMap = new HashMap<String,
      AttributeSyntax<?>>();
      for (String key : schema.getSyntaxes().keySet())
      {
        AttributeSyntax<?> syntax = schema.getSyntax(key);
        String name = syntax.getSyntaxName();
        if (name == null)
        {
          name = syntax.getOID();
        }
        syntaxNameMap.put(name, syntax);
      }

      SortedSet<String> orderedKeys =
        new TreeSet<String>(new LowerCaseComparator());
      orderedKeys.addAll(syntaxNameMap.keySet());
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
    else if (schema == null)
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
    }
    if (schema != null)
    {
    updateErrorPaneIfAuthRequired(desc,
        isLocal() ?
      INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_ATTRIBUTE_DELETE.get() :
      INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        delete.setEnabled(!authenticationRequired(desc) &&
            delete.isEnabled() &&
            schema != null);
        checkEnableSaveChanges();
        saveChanges.setEnabled(saveChanges.isEnabled() &&
            !authenticationRequired(desc) &&
            schema != null);
      }
    });
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
  public void okClicked()
  {
  }

  private void deleteAttribute()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    Schema schema = getInfo().getServerDescriptor().getSchema();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(),
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_ATTRIBUTE_TITLE.get(), getInfo());

    LinkedHashSet<AttributeType> attrsToDelete =
      new LinkedHashSet<AttributeType>(1);
    attrsToDelete.add(attribute);

    Task newTask = new DeleteSchemaElementsTask(getInfo(), dlg,
        new LinkedHashSet<ObjectClass>(0), attrsToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    TreeSet<String> childAttributes = new TreeSet<String>();
    TreeSet<String> dependentClasses = new TreeSet<String>();
    if (schema != null)
    {
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        if (attribute.equals(attr.getSuperiorType()))
        {
          childAttributes.add(attr.getNameOrOID());
        }
      }

      for (ObjectClass o : schema.getObjectClasses().values())
      {
        if (o.getRequiredAttributeChain().contains(attribute))
        {
          dependentClasses.add(o.getNameOrOID());
        }
        else if (o.getOptionalAttributeChain().contains(attribute))
        {
          dependentClasses.add(o.getNameOrOID());
        }
      }
    }
    else
    {
      errors.add(ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get());
    }
    if (errors.isEmpty())
    {
      MessageBuilder mb = new MessageBuilder();

      if (!childAttributes.isEmpty())
      {
        mb.append(INFO_ATTRIBUTE_IS_SUPERIOR.get(attrName,
            Utilities.getStringFromCollection(childAttributes, ", ")));
        mb.append("<br>");
      }

      if (!dependentClasses.isEmpty())
      {
        mb.append(INFO_ATTRIBUTE_WITH_DEPENDENCIES.get(
            attrName,
            Utilities.getStringFromCollection(dependentClasses, ", ")));
        mb.append("<br>");
      }
      Message confirmationMessage =
        INFO_CTRL_PANEL_CONFIRMATION_DELETE_ATTRIBUTE_DETAILS.get(
            attribute.getNameOrOID());
      mb.append(confirmationMessage);
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          mb.toMessage()))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_SUMMARY.get(attrName),
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_ATTRIBUTE_SUCCESSFUL.get(attrName),
            ERR_CTRL_PANEL_DELETING_ATTRIBUTE_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_ATTRIBUTE_ERROR_DETAILS.get(attrName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  private void saveChanges(boolean modal, ArrayList<Message> errors)
  {
    // Check if the aliases or the name have changed
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }
    String n = getAttributeName();
    MessageBuilder err = new MessageBuilder();
    if (n.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_REQUIRED.get());
      setPrimaryInvalid(lName);
    }
    else if (!n.equalsIgnoreCase(attribute.getNameOrOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_INVALID_ATTRIBUTE_NAME.get(err.toString()));
        setPrimaryInvalid(lName);
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_ATTRIBUTE_NAME_ALREADY_IN_USE.get(n,
              elementType.toString()));
          setPrimaryInvalid(lName);
        }
      }
    }
    n = oid.getText().trim();
    if (n.length() > 0 && !n.equalsIgnoreCase(attribute.getOID()))
    {
      if (!StaticUtils.isValidSchemaElement(n, 0, n.length(), err))
      {
        errors.add(ERR_CTRL_PANEL_OID_NOT_VALID.get(err.toString()));
        setPrimaryInvalid(lOID);
        err = new MessageBuilder();
      }
      else
      {
        Message elementType = NewAttributePanel.getSchemaElementType(n, schema);
        if (elementType != null)
        {
          errors.add(ERR_CTRL_PANEL_OID_ALREADY_IN_USE.get(n,
              elementType.toString()));
          setPrimaryInvalid(lOID);
        }
      }
    }

    Collection<String> someAliases = getAliases();
    Collection<String> oldAliases = getAliases(attribute);

    if (!someAliases.equals(oldAliases))
    {
      for (String alias : someAliases)
      {
        if (alias.trim().length() == 0)
        {
          errors.add(ERR_CTRL_PANEL_EMPTY_ALIAS.get());
          setPrimaryInvalid(lAliases);
        }
        else
        {
          boolean notPreviouslyDefined = true;
          for (String oldAlias : oldAliases)
          {
            if (oldAlias.equalsIgnoreCase(alias))
            {
              notPreviouslyDefined = false;
              break;
            }
          }
          if (notPreviouslyDefined)
          {
            Message elementType =
              NewAttributePanel.getSchemaElementType(alias, schema);
            if (elementType != null)
            {
              errors.add(ERR_CTRL_PANEL_ALIAS_ALREADY_IN_USE.get(n,
                  elementType.toString()));
              setPrimaryInvalid(lAliases);
            }
          }
        }
      }
    }


    AttributeType superior = getSuperior();
    if (superior != null)
    {
      if (superior.getNameOrOID().equalsIgnoreCase(attribute.getNameOrOID()))
      {
        errors.add(ERR_CTRL_PANEL_ATTRIBUTE_CANNOT_BE_ITS_SUPERIOR.get());
        setPrimaryInvalid(lSuperior);
      }
      else
      {
        // Check whether this object class is defined as parent as the superior.
        superior = superior.getSuperiorType();
        while (superior != null)
        {
          if (superior.getNameOrOID().equalsIgnoreCase(
              attribute.getNameOrOID()))
          {
            errors.add(
                ERR_CTRL_PANEL_ATTRIBUTE_IS_SUPERIOR_OF_SUPERIOR.get(
                getSuperior().getNameOrOID()));
            setPrimaryInvalid(lSuperior);
            break;
          }
          superior = superior.getSuperiorType();
        }
      }
    }

    setPrimaryValid(lUsage);
    if (nonModifiable.isSelected())
    {
      if (AttributeUsage.USER_APPLICATIONS.equals(usage.getSelectedItem()))
      {
        errors.add(ERR_NON_MODIFIABLE_CANNOT_BE_USER_APPLICATIONS.get());
        setPrimaryInvalid(lUsage);
      }
    }

    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.createFrame(),
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_MODIFY_ATTRIBUTE_TITLE.get(), getInfo());

      ModifyAttributeTask newTask = new ModifyAttributeTask(getInfo(),
          dlg, attribute, getNewAttribute());
      for (ConfigurationElementCreatedListener listener :
        getConfigurationElementCreatedListeners())
      {
        newTask.addConfigurationElementCreatedListener(listener);
      }
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_MODIFYING_ATTRIBUTE_SUMMARY.get(attrName),
            INFO_CTRL_PANEL_MODIFYING_ATTRIBUTE_COMPLETE.get(),
            INFO_CTRL_PANEL_MODIFYING_ATTRIBUTE_SUCCESSFUL.get(attrName),
            ERR_CTRL_PANEL_MODIFYING_ATTRIBUTE_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_MODIFYING_ATTRIBUTE_ERROR_DETAILS.get(attrName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  private void checkEnableSaveChanges()
  {
    if (ignoreChangeEvents) return;
    boolean changed;

    if (attribute != null)
    {
      try
      {
        changed = !attribute.toString().equals(getNewAttribute().toString());
      }
      catch (Throwable t)
      {
        changed = true;
      }
    }
    else
    {
      changed = false;
    }
    saveChanges.setEnabled(changed);
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

  private String getDescription()
  {
    return description.getText().trim();
  }

  private AttributeType getNewAttribute()
  {
    AttributeType attr = new AttributeType("", getAttributeName(),
        getAllNames(),
        getOID(),
        getDescription(),
        getSuperior(),
        (AttributeSyntax<?>)syntax.getSelectedItem(),
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

  private void updateDefaultMatchingRuleNames()
  {
    AttributeSyntax<?> syn = (AttributeSyntax<?>)syntax.getSelectedItem();
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
}
