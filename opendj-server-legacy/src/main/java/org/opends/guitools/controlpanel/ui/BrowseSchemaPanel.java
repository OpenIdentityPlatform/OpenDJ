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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.*;
import org.opends.guitools.controlpanel.task.DeleteSchemaElementsTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.CustomTree;
import org.opends.guitools.controlpanel.ui.components.FilterTextField;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.*;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.opends.guitools.controlpanel.util.LowerCaseComparator;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/** The pane that is displayed when the user clicks on 'Browse Schema'. */
class BrowseSchemaPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -6462914563743569830L;

  private static final LocalizableMessage FILTER_NAME = INFO_CTRL_PANEL_SCHEMA_ELEMENT_NAME.get();
  private static final LocalizableMessage FILTER_TYPE = INFO_CTRL_PANEL_SCHEMA_ELEMENT_TYPE.get();
  private static final LocalizableMessage FILTER_PARENT_CLASS = INFO_CTRL_PANEL_PARENT_CLASS.get();
  private static final LocalizableMessage FILTER_CHILD_CLASS = INFO_CTRL_PANEL_CHILD_CLASS.get();
  private static final LocalizableMessage FILTER_REQUIRED_ATTRIBUTES = INFO_CTRL_PANEL_REQUIRED_ATTRIBUTES.get();
  private static final LocalizableMessage FILTER_OPTIONAL_ATTRIBUTES = INFO_CTRL_PANEL_OPTIONAL_ATTRIBUTES.get();
  private static final LocalizableMessage NO_SCHEMA_ITEM_SELECTED = INFO_CTRL_PANEL_NO_SCHEMA_ITEM_SELECTED.get();

  private JComboBox<LocalizableMessage> filterAttribute;
  private FilterTextField filter;
  private JButton applyButton;
  private JButton newAttribute;
  private JButton newObjectClass;

  private JLabel lNumberOfElements;
  private JLabel lFilter;

  private SchemaBrowserRightPanel entryPane;
  private TreePanel treePane;

  private JScrollPane treeScroll;

  private TreePath lastEntryTreePath;

  private Schema lastSchema;

  private GenericDialog newAttributeDialog;
  private GenericDialog newObjectClassDialog;

  private JMenuItem deleteMenuItem;

  private JPopupMenu popup;

  private CommonSchemaElements lastCreatedElement;


  private final CategoryTreeNode attributes = new CategoryTreeNode(INFO_CTRL_PANEL_ATTRIBUTES_CATEGORY_NODE.get());
  private final CategoryTreeNode objectClasses =
      new CategoryTreeNode(INFO_CTRL_PANEL_OBJECTCLASSES_CATEGORY_NODE.get());
  private final CategoryTreeNode standardObjectClasses =
      new CategoryTreeNode(INFO_CTRL_PANEL_STANDARD_OBJECTCLASSES_CATEGORY_NODE.get());
  private final CategoryTreeNode standardAttributes =
      new CategoryTreeNode(INFO_CTRL_PANEL_STANDARD_ATTRIBUTES_CATEGORY_NODE.get());
  private final CategoryTreeNode configurationObjectClasses =
      new CategoryTreeNode(INFO_CTRL_PANEL_CONFIGURATION_OBJECTCLASSES_CATEGORY_NODE.get());
  private final CategoryTreeNode configurationAttributes =
      new CategoryTreeNode(INFO_CTRL_PANEL_CONFIGURATION_ATTRIBUTES_CATEGORY_NODE.get());
  private final CategoryTreeNode customObjectClasses =
      new CategoryTreeNode(INFO_CTRL_PANEL_CUSTOM_OBJECTCLASSES_CATEGORY_NODE.get());
  private final CategoryTreeNode customAttributes =
      new CategoryTreeNode(INFO_CTRL_PANEL_CUSTOM_ATTRIBUTES_CATEGORY_NODE.get());
  private final CategoryTreeNode matchingRules =
      new CategoryTreeNode(INFO_CTRL_PANEL_MATCHING_RULES_CATEGORY_NODE.get());
  private final CategoryTreeNode syntaxes =
      new CategoryTreeNode(INFO_CTRL_PANEL_ATTRIBUTE_SYNTAXES_CATEGORY_NODE.get());
  private final CategoryTreeNode[] underRootNodes = { objectClasses, attributes, matchingRules, syntaxes };
  private final CategoryTreeNode[] categoryNodes = { standardObjectClasses, standardAttributes, customObjectClasses,
    customAttributes, configurationObjectClasses, configurationAttributes, matchingRules, syntaxes };

  private JLabel lNoMatchFound;
  private boolean ignoreSelectionEvents;

  public BrowseSchemaPanel()
  {
    createLayout();
  }

  @Override
  public boolean requiresBorder()
  {
    return false;
  }

  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  @Override
  public boolean callConfigurationChangedInBackground()
  {
    return true;
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    Window w = Utilities.getParentDialog(this);
    if (w instanceof GenericDialog)
    {
      ((GenericDialog) w).getRootPane().setDefaultButton(null);
    }
    else if (w instanceof GenericFrame)
    {
      ((GenericFrame) w).getRootPane().setDefaultButton(null);
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  @SuppressWarnings("unchecked")
  private void createLayout()
  {
    setBackground(ColorAndFontConstants.greyBackground);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 7;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    addErrorPane(gbc);

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(10, 10, 0, 0);

    newObjectClass = Utilities.createButton(INFO_CTRL_PANEL_NEW_OBJECTCLASS_BUTTON.get());
    newObjectClass.setOpaque(false);
    gbc.weightx = 0.0;
    add(newObjectClass, gbc);
    newObjectClass.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        newObjectClassClicked();
      }
    });

    newAttribute = Utilities.createButton(INFO_CTRL_PANEL_NEW_ATTRIBUTE_BUTTON.get());
    newAttribute.setOpaque(false);
    gbc.gridx++;
    gbc.weightx = 0.0;
    gbc.insets.left = 10;
    add(newAttribute, gbc);
    newAttribute.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        newAttributeClicked();
      }
    });

    gbc.gridx++;
    JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
    gbc.fill = GridBagConstraints.VERTICAL;
    add(sep, gbc);

    lFilter = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_FILTER_LABEL.get());
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx++;
    add(lFilter, gbc);

    filterAttribute = Utilities.createComboBox();
    filterAttribute.setModel(
        new DefaultComboBoxModel<>(new LocalizableMessage[]{
            FILTER_NAME,
            FILTER_TYPE,
            FILTER_PARENT_CLASS,
            FILTER_CHILD_CLASS,
            FILTER_REQUIRED_ATTRIBUTES,
            FILTER_OPTIONAL_ATTRIBUTES}));
    filterAttribute.setRenderer(new CustomListCellRenderer(filterAttribute));
    gbc.insets.left = 5;
    gbc.gridx++;
    add(filterAttribute, gbc);

    filter = new FilterTextField();
    filter.addKeyListener(new KeyAdapter()
    {
      @Override
      public void keyReleased(KeyEvent e)
      {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && applyButton.isEnabled())
        {
          filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
          repopulateTree(treePane.getTree(), false);
        }
      }
    });
    filter.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
        repopulateTree(treePane.getTree(), false);
      }
    });
    gbc.gridx++;
    gbc.weightx = 1.0;
    add(filter, gbc);

    applyButton = Utilities.createButton(INFO_CTRL_PANEL_APPLY_BUTTON_LABEL.get());
    applyButton.setOpaque(false);
    gbc.gridx++;
    gbc.weightx = 0.0;
    gbc.insets.right = 10;
    add(applyButton, gbc);
    applyButton.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
        repopulateTree(treePane.getTree(), false);
      }
    });

    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 7;
    add(createSplitPane(), gbc);

    // The button panel
    gbc.gridy++;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    add(createButtonsPanel(), gbc);
  }

  private JPanel createButtonsPanel()
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 1;
    gbc.gridy = 0;
    lNumberOfElements = Utilities.createDefaultLabel();
    gbc.insets = new Insets(10, 10, 10, 10);
    buttonsPanel.add(lNumberOfElements, gbc);
    gbc.weightx = 1.0;
    gbc.gridx++;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    buttonsPanel.setOpaque(true);
    buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
    gbc.insets.left = 5;
    gbc.insets.right = 10;
    gbc.gridx++;
    gbc.weightx = 0.0;
    JButton closeButton = Utilities.createButton(INFO_CTRL_PANEL_CLOSE_BUTTON_LABEL.get());
    closeButton.setOpaque(false);
    buttonsPanel.add(closeButton, gbc);
    closeButton.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        closeClicked();
      }
    });

    buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorAndFontConstants.defaultBorderColor));

    return buttonsPanel;
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_MANAGE_SCHEMA_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return filter;
  }

  @Override
  public void closeClicked()
  {
    setSecondaryValid(lFilter);
    super.closeClicked();
  }

  @Override
  public void okClicked()
  {
    // No ok button
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  private Component createSplitPane()
  {
    treePane = new TreePanel();

    lNoMatchFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_MATCHES_FOUND_LABEL.get());
    lNoMatchFound.setVisible(false);

    entryPane = new SchemaBrowserRightPanel();
    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(ColorAndFontConstants.background);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    Utilities.setBorder(treePane, new EmptyBorder(10, 0, 10, 0));
    p.add(treePane, gbc);
    Utilities.setBorder(lNoMatchFound, new EmptyBorder(15, 15, 15, 15));
    gbc.fill = GridBagConstraints.HORIZONTAL;
    p.add(lNoMatchFound, gbc);
    treeScroll = Utilities.createScrollPane(p);

    entryPane.addSchemaElementSelectionListener(new SchemaElementSelectionListener()
    {
      @Override
      public void schemaElementSelected(SchemaElementSelectionEvent ev)
      {
        Object element = ev.getSchemaElement();
        DefaultTreeModel model = (DefaultTreeModel) treePane.getTree().getModel();
        Object root = model.getRoot();
        selectElementUnder(root, element, model);
      }
    });
    entryPane.addConfigurationElementCreatedListener(new ConfigurationElementCreatedListener()
    {
      @Override
      public void elementCreated(ConfigurationElementCreatedEvent ev)
      {
        configurationElementCreated(ev);
      }
    });

    treePane.getTree().addTreeSelectionListener(new TreeSelectionListener()
    {
      @Override
      public void valueChanged(TreeSelectionEvent ev)
      {
        if (!ignoreSelectionEvents)
        {
          ignoreSelectionEvents = true;
          final JTree tree = treePane.getTree();
          TreePath[] paths = tree.getSelectionPaths();

          if (entryPane.mustCheckUnsavedChanges())
          {
            ignoreSelectionEvents = true;
            tree.setSelectionPath(lastEntryTreePath);
            switch (entryPane.checkUnsavedChanges())
            {
            case DO_NOT_SAVE:
            case SAVE:
              break;
            case CANCEL:
              ignoreSelectionEvents = false;
              return;
            }
            if (paths != null)
            {
              tree.setSelectionPaths(paths);
            }
            else
            {
              tree.clearSelection();
            }
          }

          boolean deletableElementsSelected = false;
          boolean nonDeletableElementsSelected = false;
          if (paths != null)
          {
            for (TreePath path : paths)
            {
              Object node = path.getLastPathComponent();
              if (node instanceof CategoryTreeNode)
              {
                nonDeletableElementsSelected = true;
              }
              else if (node instanceof CustomObjectClassTreeNode || node instanceof CustomAttributeTreeNode)
              {
                deletableElementsSelected = true;
              }
              else if (node instanceof SchemaElementTreeNode)
              {
                nonDeletableElementsSelected = true;
              }
            }
          }
          deleteMenuItem.setEnabled(deletableElementsSelected && !nonDeletableElementsSelected);
          updateEntryPane();
          ignoreSelectionEvents = false;
        }
      }
    });
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Tree root");
    for (DefaultMutableTreeNode node : underRootNodes)
    {
      root.add(node);
    }
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = treePane.getTree();
    tree.setModel(model);
    tree.setRootVisible(false);
    tree.setVisibleRowCount(20);
    tree.expandPath(new TreePath(root));
    tree.setCellRenderer(new SchemaTreeCellRenderer());
    addPopupMenu();
    treeScroll.setPreferredSize(new Dimension(
        (3 * treeScroll.getPreferredSize().width) / 2, 5 * treeScroll.getPreferredSize().height));
    entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
    entryPane.setBorder(getRightPanelBorder());
    entryPane.setPreferredSize(new Dimension(
        treeScroll.getPreferredSize().width, treeScroll.getPreferredSize().height));
    JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    pane.setOpaque(true); //content panes must be opaque
    pane.setLeftComponent(treeScroll);
    pane.setRightComponent(entryPane);
    pane.setResizeWeight(0.0);
    pane.setDividerLocation(treeScroll.getPreferredSize().width);
    return pane;
  }

  @Override
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    treePane.setInfo(info);
    entryPane.setInfo(info);
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    final boolean forceScroll = lastSchema == null;
    Schema schema = desc.getSchema();
    boolean schemaChanged;
    if (schema != null && lastSchema != null)
    {
      schemaChanged = !ServerDescriptor.areSchemasEqual(lastSchema, schema);
    }
    else if (schema == null && lastSchema != null)
    {
      schemaChanged = false;
    }
    else
    {
      schemaChanged = schema != null && lastSchema == null;
    }
    if (schemaChanged)
    {
      lastSchema = schema;
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          repopulateTree(treePane.getTree(), forceScroll);
          if (errorPane.isVisible())
          {
            errorPane.setVisible(false);
          }
        }
      });
    }
    else if (lastSchema == null)
    {
      updateErrorPane(errorPane, ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(), ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(), ColorAndFontConstants.defaultFont);
      if (!errorPane.isVisible())
      {
        errorPane.setVisible(true);
      }
    }
  }

  /**
   * Selects the node in the tree that corresponds to a given schema element.
   *
   * @param root
   *          the node we must start searching for the node to be selected.
   * @param element
   *          the schema element.
   * @param model
   *          the tree model.
   * @return <CODE>true</CODE> if the node was found and selected and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean selectElementUnder(Object root, Object element, DefaultTreeModel model)
  {
    int n = model.getChildCount(root);
    boolean found = false;
    for (int i = 0; i < n && !found; i++)
    {
      Object node = model.getChild(root, i);
      if (node instanceof SchemaElementTreeNode)
      {
        SchemaElementTreeNode schemaNode = (SchemaElementTreeNode) node;
        if (schemaNode.getSchemaElement().equals(element))
        {
          found = true;
          TreePath newSelectionPath = new TreePath(schemaNode.getPath());
          treePane.getTree().setSelectionPath(newSelectionPath);
          treePane.getTree().scrollPathToVisible(newSelectionPath);
        }
      }
      if (!found)
      {
        found = selectElementUnder(node, element, model);
      }
    }
    return found;
  }

  /**
   * Repopulates the tree.
   *
   * @param tree
   *          the tree to be repopulated.
   * @param forceScroll
   *          whether the scroll must be reset or not.
   */
  private void repopulateTree(JTree tree, final boolean forceScroll)
  {
    if (lastSchema == null)
    {
      return;
    }
    ignoreSelectionEvents = true;

    final Point currentPosition = treeScroll.getViewport().getViewPosition();
    DefaultMutableTreeNode root = getRoot(tree);
    TreePath path = tree.getSelectionPath();
    DefaultMutableTreeNode lastSelectedNode = null;
    if (path != null)
    {
      lastSelectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
    }
    TreePath newSelectionPath = null;

    Comparator<String> lowerCaseComparator = new LowerCaseComparator();
    Set<String> standardOcNames = new TreeSet<>(lowerCaseComparator);
    Map<String, StandardObjectClassTreeNode> hmStandardOcs = new HashMap<>();
    Set<String> configurationOcNames = new TreeSet<>(lowerCaseComparator);
    Map<String, ConfigurationObjectClassTreeNode> hmConfigurationOcs = new HashMap<>();
    Set<String> customOcNames = new TreeSet<>(lowerCaseComparator);
    Map<String, CustomObjectClassTreeNode> hmCustomOcs = new HashMap<>();
    for (ObjectClass oc : lastSchema.getObjectClasses().values())
    {
      if (mustAdd(oc))
      {
        String name = oc.getPrimaryName();
        if (Utilities.isStandard(oc))
        {
          standardOcNames.add(name);
          hmStandardOcs.put(name, new StandardObjectClassTreeNode(name, oc));
        }
        else if (Utilities.isConfiguration(oc))
        {
          configurationOcNames.add(name);
          hmConfigurationOcs.put(name, new ConfigurationObjectClassTreeNode(name, oc));
        }
        else
        {
          customOcNames.add(name);
          hmCustomOcs.put(name, new CustomObjectClassTreeNode(name, oc));
        }
      }
    }

    Set<String> standardAttrNames = new TreeSet<>(lowerCaseComparator);
    Map<String, StandardAttributeTreeNode> hmStandardAttrs = new HashMap<>();
    Set<String> configurationAttrNames = new TreeSet<>(lowerCaseComparator);
    Map<String, ConfigurationAttributeTreeNode> hmConfigurationAttrs = new HashMap<>();
    Set<String> customAttrNames = new TreeSet<>(lowerCaseComparator);
    Map<String, CustomAttributeTreeNode> hmCustomAttrs = new HashMap<>();
    for (AttributeType attr : lastSchema.getAttributeTypes().values())
    {
      if (mustAdd(attr))
      {
        String name = attr.getPrimaryName();
        if (Utilities.isStandard(attr))
        {
          standardAttrNames.add(name);
          hmStandardAttrs.put(name, new StandardAttributeTreeNode(name, attr));
        }
        else if (Utilities.isConfiguration(attr))
        {
          configurationAttrNames.add(name);
          hmConfigurationAttrs.put(name, new ConfigurationAttributeTreeNode(name, attr));
        }
        else
        {
          customAttrNames.add(name);
          hmCustomAttrs.put(name, new CustomAttributeTreeNode(name, attr));
        }
      }
    }

    Set<String> matchingRuleNames = new TreeSet<>(lowerCaseComparator);
    Map<String, MatchingRuleTreeNode> hmMatchingRules = new HashMap<>();
    for (MatchingRule matchingRule : lastSchema.getMatchingRules().values())
    {
      if (mustAdd(matchingRule))
      {
        String name = matchingRule.getNameOrOID();
        matchingRuleNames.add(name);
        hmMatchingRules.put(name, new MatchingRuleTreeNode(name, matchingRule));
      }
    }

    Set<String> syntaxNames = new TreeSet<>(lowerCaseComparator);
    Map<String, AttributeSyntaxTreeNode> hmSyntaxes = new HashMap<>();
    for (Syntax syntax : lastSchema.getSyntaxes().values())
    {
      if (mustAdd(syntax))
      {
        String name = syntax.getName();
        if (name == null)
        {
          name = syntax.getOID();
        }
        syntaxNames.add(name);
        hmSyntaxes.put(name, new AttributeSyntaxTreeNode(name, syntax));
      }
    }

    List<Set<String>> names = new ArrayList<>();
    names.add(standardOcNames);
    names.add(standardAttrNames);
    names.add(customOcNames);
    names.add(customAttrNames);
    names.add(configurationOcNames);
    names.add(configurationAttrNames);
    names.add(matchingRuleNames);
    names.add(syntaxNames);

    List<Map<String, ? extends DefaultMutableTreeNode>> nodes = new ArrayList<>();
    nodes.add(hmStandardOcs);
    nodes.add(hmStandardAttrs);
    nodes.add(hmCustomOcs);
    nodes.add(hmCustomAttrs);
    nodes.add(hmConfigurationOcs);
    nodes.add(hmConfigurationAttrs);
    nodes.add(hmMatchingRules);
    nodes.add(hmSyntaxes);

    DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
    String f = filter.getText().trim();
    boolean filterProvided = f.length() > 0;

    ArrayList<TreePath> toExpand = new ArrayList<>();

    int i = 0;
    int positionUnderRoot = 0;
    boolean rootWasEmpty = root.getChildCount() == 0;
    boolean expand = filterProvided;
    if (root.getIndex(objectClasses) == -1)
    {
      model.insertNodeInto(objectClasses, root, positionUnderRoot);
    }
    else if (!expand)
    {
      expand = tree.isExpanded(new TreePath(objectClasses.getPath()));
    }
    if (expand)
    {
      toExpand.add(new TreePath(objectClasses.getPath()));
    }
    positionUnderRoot++;

    expand = filterProvided;
    if (root.getIndex(attributes) == -1)
    {
      model.insertNodeInto(attributes, root, positionUnderRoot);
    }
    else if (!expand)
    {
      expand = tree.isExpanded(new TreePath(attributes.getPath()));
    }
    if (expand)
    {
      toExpand.add(new TreePath(attributes.getPath()));
    }
    positionUnderRoot++;
    int positionUnderAttributes = 0;
    int positionUnderObjectClass = 0;

    for (DefaultMutableTreeNode parent : categoryNodes)
    {
      if (nodes.get(i).size() == 0)
      {
        if (parent.getParent() != null)
        {
          parent.removeAllChildren();
          model.removeNodeFromParent(parent);
        }
      }
      else
      {
        expand = false;
        if (parent == standardObjectClasses || parent == customObjectClasses || parent == configurationObjectClasses)
        {
          if (objectClasses.getIndex(parent) == -1)
          {
            model.insertNodeInto(parent, objectClasses, positionUnderObjectClass);
          }
          else
          {
            expand = tree.isExpanded(new TreePath(parent.getPath()));
            parent.removeAllChildren();
          }
          positionUnderObjectClass++;
        }
        else if (parent == standardAttributes || parent == customAttributes || parent == configurationAttributes)
        {
          if (attributes.getIndex(parent) == -1)
          {
            model.insertNodeInto(parent, attributes, positionUnderAttributes);
          }
          else
          {
            expand = tree.isExpanded(new TreePath(parent.getPath()));
            parent.removeAllChildren();
          }
          positionUnderAttributes++;
        }
        else
        {
          if (root.getIndex(parent) == -1)
          {
            model.insertNodeInto(parent, root, positionUnderRoot);
          }
          else
          {
            expand = tree.isExpanded(new TreePath(parent.getPath()));
            parent.removeAllChildren();
          }
          positionUnderRoot++;
        }

        for (String name : names.get(i))
        {
          DefaultMutableTreeNode node = nodes.get(i).get(name);
          parent.add(node);
          if (newSelectionPath == null && (lastSelectedNode != null || lastCreatedElement != null))
          {
            if (lastCreatedElement != null)
            {
              if (node instanceof CustomObjectClassTreeNode && lastCreatedElement instanceof ObjectClass)
              {
                if (name.equals(lastCreatedElement.getNameOrOID()))
                {
                  newSelectionPath = new TreePath(node.getPath());
                  lastCreatedElement = null;
                }
              }
              else if (node instanceof CustomAttributeTreeNode && lastCreatedElement instanceof AttributeType
                  && name.equals(lastCreatedElement.getNameOrOID()))
              {
                newSelectionPath = new TreePath(node.getPath());
                lastCreatedElement = null;
              }
            }
            else if (name.equals(lastSelectedNode.getUserObject()))
            {
              newSelectionPath = new TreePath(node.getPath());
            }
          }
        }
        model.nodeStructureChanged(parent);
        if (expand || filterProvided)
        {
          toExpand.add(new TreePath(parent.getPath()));
        }
      }
      i++;
    }

    DefaultMutableTreeNode[] ocAndAttrs = { objectClasses, attributes };
    for (DefaultMutableTreeNode node : ocAndAttrs)
    {
      if (node.getParent() != null && node.getChildCount() == 0)
      {
        model.removeNodeFromParent(node);
        model.nodeStructureChanged(node);
      }
    }

    if (newSelectionPath != null)
    {
      tree.setSelectionPath(newSelectionPath);
      tree.scrollPathToVisible(newSelectionPath);
    }
    TreePath rootPath = new TreePath(root.getPath());
    if (rootWasEmpty || !tree.isVisible(rootPath))
    {
      tree.expandPath(rootPath);
    }
    for (TreePath p : toExpand)
    {
      tree.expandPath(p);
    }
    updateEntryPane();
    ignoreSelectionEvents = false;
    int nElements =
        hmStandardOcs.size() + hmConfigurationOcs.size() + hmCustomOcs.size() + hmStandardAttrs.size()
            + hmConfigurationAttrs.size() + hmCustomAttrs.size() + hmMatchingRules.size() + hmSyntaxes.size();
    lNoMatchFound.setVisible(nElements == 0);
    treePane.setVisible(nElements > 0);
    if (nElements > 0)
    {
      lNumberOfElements.setText(INFO_CTRL_PANEL_SCHEMA_ELEMENT_NUMBER.get(nElements).toString());
      lNumberOfElements.setVisible(true);
    }
    else
    {
      lNumberOfElements.setVisible(false);
    }
    if (newSelectionPath == null && f.length() > 0)
    {
      for (i = 0; i < tree.getRowCount(); i++)
      {
        newSelectionPath = tree.getPathForRow(i);
        Object node = newSelectionPath.getLastPathComponent();
        if (!(node instanceof CategoryTreeNode))
        {
          tree.setSelectionPath(newSelectionPath);
          break;
        }
      }
    }
    repaint();

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        treeScroll.getViewport().setViewPosition(forceScroll ? new Point(0, 0) : currentPosition);
      }
    });
  }

  /** Updates the right entry panel. */
  private void updateEntryPane()
  {
    ViewPositions pos = Utilities.getViewPositions(entryPane);
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    TreePath path = null;
    if (paths != null && paths.length == 1)
    {
      path = paths[0];
    }
    lastEntryTreePath = path;
    if (path != null)
    {
      Object node = path.getLastPathComponent();
      if (node instanceof StandardObjectClassTreeNode)
      {
        entryPane.updateStandardObjectClass(
            ((StandardObjectClassTreeNode) node).getObjectClass(), lastSchema);
      }
      else if (node instanceof ConfigurationObjectClassTreeNode)
      {
        entryPane.updateConfigurationObjectClass(
            ((ConfigurationObjectClassTreeNode) node).getObjectClass(), lastSchema);
      }
      else if (node instanceof CustomObjectClassTreeNode)
      {
        entryPane.updateCustomObjectClass(((CustomObjectClassTreeNode) node).getObjectClass(), lastSchema);
      }
      else if (node instanceof StandardAttributeTreeNode)
      {
        entryPane.updateStandardAttribute(((StandardAttributeTreeNode) node).getAttribute(), lastSchema);
      }
      else if (node instanceof ConfigurationAttributeTreeNode)
      {
        entryPane.updateConfigurationAttribute(((ConfigurationAttributeTreeNode) node).getAttribute(), lastSchema);
      }
      else if (node instanceof CustomAttributeTreeNode)
      {
        entryPane.updateCustomAttribute(((CustomAttributeTreeNode) node).getAttribute(), lastSchema);
      }
      else if (node instanceof MatchingRuleTreeNode)
      {
        entryPane.updateMatchingRule(((MatchingRuleTreeNode) node).getMatchingRule(), lastSchema);
      }
      else if (node instanceof AttributeSyntaxTreeNode)
      {
        entryPane.updateAttributeSyntax(((AttributeSyntaxTreeNode) node).getAttributeSyntax(), lastSchema);
      }
      else
      {
        entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
      }
    }
    else if (paths != null && paths.length > 1)
    {
      boolean categorySelected = false;
      int nNonCategory = 0;
      for (TreePath p : paths)
      {
        Object node = p.getLastPathComponent();
        if (node instanceof CategoryTreeNode)
        {
          categorySelected = true;
        }
        else
        {
          nNonCategory++;
        }
      }
      if (nNonCategory == 0)
      {
        entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
      }
      else if (categorySelected)
      {
        entryPane.displayMessage(INFO_CTRL_PANEL_CATEGORY_ITEM_SELECTED.get());
      }
      else
      {
        entryPane.displayMessage(INFO_CTRL_PANEL_MULTIPLE_SCHEMA_ITEMS_SELECTED.get());
      }
    }
    else
    {
      entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
    }
    Utilities.updateViewPositions(pos);
  }

  /** Adds a popup menu. */
  private void addPopupMenu()
  {
    popup = new JPopupMenu();
    JMenuItem menuItem = Utilities.createMenuItem(INFO_CTRL_PANEL_NEW_OBJECTCLASS_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        newObjectClassClicked();
      }
    });
    popup.add(menuItem);
    menuItem = Utilities.createMenuItem(INFO_CTRL_PANEL_NEW_ATTRIBUTE_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        newAttributeClicked();
      }
    });
    popup.add(menuItem);
    popup.add(new JSeparator());
    deleteMenuItem = Utilities.createMenuItem(INFO_CTRL_PANEL_DELETE_SCHEMA_ELEMENT_MENU.get());
    deleteMenuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        deleteClicked();
      }
    });
    popup.add(deleteMenuItem);
    deleteMenuItem.setEnabled(false);
    popup.setOpaque(true);
    ((CustomTree) treePane.getTree()).setPopupMenu(popup);
  }

  private void deleteClicked()
  {
    List<LocalizableMessage> errors = new ArrayList<>();
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    List<ObjectClass> ocsToDelete = new ArrayList<>();
    List<AttributeType> attrsToDelete = new ArrayList<>();
    if (paths != null)
    {
      for (TreePath path : paths)
      {
        Object node = path.getLastPathComponent();
        if (node instanceof CustomObjectClassTreeNode)
        {
          ocsToDelete.add(((CustomObjectClassTreeNode) node).getObjectClass());
        }
        else if (node instanceof CustomAttributeTreeNode)
        {
          attrsToDelete.add(((CustomAttributeTreeNode) node).getAttribute());
        }
      }
    }

    Schema schema = getInfo().getServerDescriptor().getSchema();
    if (schema == null)
    {
      errors.add(ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get());
    }
    if (errors.isEmpty())
    {
      LocalizableMessage confirmationMessage = getConfirmationMessage(ocsToDelete, attrsToDelete, schema);
      Set<AttributeType> orderedAttributes = getOrderedAttributesToDelete(attrsToDelete);
      Set<ObjectClass> orderedObjectClasses = getOrderedObjectClassesToDelete(ocsToDelete);

      LocalizableMessage title;
      if (orderedAttributes.isEmpty())
      {
        title = INFO_CTRL_PANEL_DELETE_OBJECTCLASSES_TITLE.get();
      }
      else if (orderedObjectClasses.isEmpty())
      {
        title = INFO_CTRL_PANEL_DELETE_ATTRIBUTES_TITLE.get();
      }
      else
      {
        title = INFO_CTRL_PANEL_DELETE_OBJECTCLASSES_AND_ATTRIBUTES_TITLE.get();
      }
      ProgressDialog dlg =
          new ProgressDialog(Utilities.createFrame(), Utilities.getParentDialog(this), title, getInfo());
      DeleteSchemaElementsTask newTask =
          new DeleteSchemaElementsTask(getInfo(), dlg, orderedObjectClasses, orderedAttributes);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        List<String> allNames = new ArrayList<>();
        if (displayConfirmationDialog(INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(), confirmationMessage))
        {
          launchOperation(newTask, INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_SUMMARY.get(),
              INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_COMPLETE.get(),
              INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_SUCCESSFUL
                  .get(Utilities.getStringFromCollection(allNames, ", ")),
              ERR_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_ERROR_DETAILS.get(), null, dlg);
          dlg.setVisible(true);
        }
      }
    }
    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  private boolean mustAddAttributeName(AttributeType attr, String attrName)
  {
    return mustAdd(attrName, attr.getOID(), attr.getPrimaryName(), attr.getNormalizedNames());
  }

  private boolean mustAddObjectClassName(ObjectClass oc, String ocName)
  {
    return mustAdd(ocName, oc.getOID(), oc.getPrimaryName(), oc.getNormalizedNames());
  }

  private boolean mustAdd(String name, String oid, String primaryName, Iterable<String> names)
  {
    List<String> values = new ArrayList<>();
    values.add(oid);
    if (primaryName != null)
    {
      values.add(primaryName);
    }
    for (String v : names)
    {
      values.add(v);
    }

    return matchFilter(values, name, false);
  }

  /**
   * Check whether the provided attribute must be added or not.
   *
   * @param attr
   *          the attribute.
   * @return <CODE>true</CODE> if the attribute must be added and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(AttributeType attr)
  {
    String f = filter.getText().trim();
    if (f.length() > 0)
    {
      Object filterType = filterAttribute.getSelectedItem();

      if (FILTER_NAME.equals(filterType))
      {
        return mustAddAttributeName(attr, f);
      }
      else if (FILTER_TYPE.equals(filterType))
      {
        return mustAddType(f, StandardAttributePanel.getTypeValue(attr));
      }
      else
      {
        return false;
      }
    }
    return true;
  }

  private boolean mustAddType(String filter, LocalizableMessage typeValue)
  {
    String[] elements = filter.split("[ ,]");
    String text = typeValue.toString().toLowerCase();
    return mustAdd(elements, text);
  }

  private boolean mustAdd(String[] elements, String text)
  {
    for (String elem : elements)
    {
      if (!text.contains(elem.toLowerCase()))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Check whether the provided object class must be added or not.
   *
   * @param oc
   *          the objectclass.
   * @return <CODE>true</CODE> if the objectclass must be added and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(ObjectClass oc)
  {
    final String filterText = filter.getText().trim();
    if (filterText.isEmpty())
    {
      return true;
    }

    final Object filterType = filterAttribute.getSelectedItem();
    if (FILTER_NAME.equals(filterType))
    {
      return mustAddObjectClassName(oc, filterText);
    }
    else if (FILTER_TYPE.equals(filterType))
    {
      return mustAddType(filterText, StandardObjectClassPanel.getTypeValue(oc));
    }
    else if (FILTER_REQUIRED_ATTRIBUTES.equals(filterType) || FILTER_OPTIONAL_ATTRIBUTES.equals(filterType))
    {
      return mustAddAttributeName(oc, filterText, filterType);
    }
    else if (FILTER_CHILD_CLASS.equals(filterType))
    {
      return mustAddAnyObjectClassName(oc, filterText);
    }
    else if (FILTER_PARENT_CLASS.equals(filterType))
    {
      return mustAddParentObjectClassName(oc, filterText);
    }
    return false;
  }

  private boolean mustAddAnyObjectClassName(ObjectClass oc, String f)
  {
    for (ObjectClass o : lastSchema.getObjectClasses().values())
    {
      if (isDescendant(oc, o) && mustAddObjectClassName(o, f))
      {
        return true;
      }
    }
    return false;
  }

  private boolean mustAddAttributeName(ObjectClass oc, String f, Object filterType)
  {
    Set<AttributeType> definedAttrs = FILTER_REQUIRED_ATTRIBUTES.equals(filterType) ? oc.getRequiredAttributeChain()
                                                                                    : oc.getOptionalAttributeChain();
    return mustAddAttributeName(f, definedAttrs);
  }

  private boolean mustAddAttributeName(String f, Set<AttributeType> definedAttrs)
  {
    boolean mustAdd = true;
    String[] attrValues = f.split(" ");
    for (String attrName : attrValues)
    {
      mustAdd = false;
      for (AttributeType attr : definedAttrs)
      {
        mustAdd = mustAddAttributeName(attr, attrName);
        if (mustAdd)
        {
          break;
        }
      }
      if (!mustAdd)
      {
        break;
      }
    }
    return mustAdd;
  }

  private boolean mustAddParentObjectClassName(ObjectClass oc, String f)
  {
    for (ObjectClass parent : oc.getSuperiorClasses())
    {
      if (mustAddObjectClassName(parent, f) || mustAddParentObjectClassName(parent, f))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds out if a class is descendant of another class using equality of
   * pointers.
   *
   * @param ocParent
   *          the parent object class.
   * @param oChild
   *          the (potentially) descendant object class.
   * @return {@code true} if the class is a descendant of the parent class and
   *         {@code false} otherwise.
   */
  private boolean isDescendant(ObjectClass ocParent, ObjectClass oChild)
  {
    Set<ObjectClass> superiors = oChild.getSuperiorClasses();
    if (superiors != null)
    {
      for (ObjectClass o : superiors)
      {
        if (ocParent == o || isDescendant(ocParent, o))
        {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Check whether the provided matching rule must be added or not.
   *
   * @param matchingRule
   *          the matching rule.
   * @return <CODE>true</CODE> if the matching rule must be added and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(MatchingRule matchingRule)
  {
    String f = filter.getText().trim();
    return f.length () <= 0 || (FILTER_NAME.equals(filterAttribute.getSelectedItem())
                                 && mustAdd(f, matchingRule.getOID(), matchingRule.getNameOrOID()));
  }

  /**
   * Check whether the provided attribute syntax must be added or not.
   *
   * @param syntax
   *          the attribute syntax.
   * @return <CODE>true</CODE> if the attribute syntax must be added and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(Syntax syntax)
  {
    String f = filter.getText().trim();
    return f.length() <= 0
        || (FILTER_NAME.equals(filterAttribute.getSelectedItem()) && mustAdd(f, syntax.getOID(), syntax.getName()));
  }

  private boolean mustAdd(String f, String oid, String name)
  {
    List<String> values = new ArrayList<>(2);
    values.add(oid);
    if (name != null)
    {
      values.add(name);
    }

    return matchFilter(values, f, false);
  }

  private boolean matchFilter(Collection<String> values, String filter, boolean exact)
  {
    for (String value : values)
    {
      boolean matchFilter = exact ? value.equalsIgnoreCase(filter) : value.toLowerCase().contains(filter.toLowerCase());
      if (matchFilter)
      {
        return true;
      }
    }
    return false;
  }

  private DefaultMutableTreeNode getRoot(JTree tree)
  {
    return (DefaultMutableTreeNode) tree.getModel().getRoot();
  }

  private void newAttributeClicked()
  {
    if (newAttributeDialog == null)
    {
      NewAttributePanel panel = new NewAttributePanel(Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newAttributeDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newAttributeDialog, Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(new ConfigurationElementCreatedListener()
      {
        @Override
        public void elementCreated(ConfigurationElementCreatedEvent ev)
        {
          configurationElementCreated(ev);
        }
      });
    }
    newAttributeDialog.setVisible(true);
  }

  private void newObjectClassClicked()
  {
    if (newObjectClassDialog == null)
    {
      NewObjectClassPanel panel = new NewObjectClassPanel(Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newObjectClassDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newObjectClassDialog, Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(new ConfigurationElementCreatedListener()
      {
        @Override
        public void elementCreated(ConfigurationElementCreatedEvent ev)
        {
          configurationElementCreated(ev);
        }
      });
    }
    newObjectClassDialog.setVisible(true);
  }

  private void configurationElementCreated(ConfigurationElementCreatedEvent ev)
  {
    Object o = ev.getConfigurationObject();
    if (o instanceof CommonSchemaElements)
    {
      lastCreatedElement = (CommonSchemaElements) o;
    }
  }

  private final Map<Object, ImageIcon> hmCategoryImages = new HashMap<>();
  private final Map<Class<?>, ImageIcon> hmImages = new HashMap<>();
  {
    Object[] nodes = {attributes, objectClasses, standardObjectClasses,
        standardAttributes, configurationObjectClasses, configurationAttributes,
        customObjectClasses, customAttributes, matchingRules, syntaxes};
    String[] paths = {"ds-attr-folder.png", "ds-class-folder.png", "ds-folder.png",
        "ds-folder.png", "ds-folder.png", "ds-folder.png", "ds-folder.png",
        "ds-folder.png", "ds-rule-folder.png", "ds-syntax-folder.png"};
    for (int i=0; i<nodes.length; i++)
    {
      hmCategoryImages.put(nodes[i], Utilities.createImageIcon(IconPool.IMAGE_PATH + "/" + paths[i]));
    }
    Class<?>[] classes =
        { ConfigurationAttributeTreeNode.class, StandardAttributeTreeNode.class, CustomAttributeTreeNode.class,
          ConfigurationObjectClassTreeNode.class, StandardObjectClassTreeNode.class, CustomObjectClassTreeNode.class,
          MatchingRuleTreeNode.class, AttributeSyntaxTreeNode.class };
    String[] ocPaths = { "ds-attr.png", "ds-attr.png", "ds-attr.png",
      "ds-class.png", "ds-class.png", "ds-class.png", "ds-rule.png", "ds-syntax.png" };
    for (int i = 0; i < classes.length; i++)
    {
      hmImages.put(classes[i], Utilities.createImageIcon(IconPool.IMAGE_PATH + "/" + ocPaths[i]));
    }
  }

  /** Specific class used to render the nodes in the tree. It uses specific icons for the nodes. */
  private class SchemaTreeCellRenderer extends TreeCellRenderer
  {
    private static final long serialVersionUID = -3390568254259441766L;

    @Override
    public Component getTreeCellRendererComponent(
        JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus)
    {
      super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
      setIcon(getIcon(value));
      return this;
    }

    private ImageIcon getIcon(Object value)
    {
      ImageIcon icon = hmImages.get(value.getClass());
      if (icon == null)
      {
        icon = hmCategoryImages.get(value);
      }
      return icon;
    }
  }

  private Set<ObjectClass> getOrderedObjectClassesToDelete(Collection<ObjectClass> ocsToDelete)
  {
    List<ObjectClass> lOrderedOcs = new ArrayList<>();
    // Reorder objectClasses and attributes to delete them in the proper order.
    for (ObjectClass oc : ocsToDelete)
    {
      int index = -1;
      for (int i = 0; i < lOrderedOcs.size(); i++)
      {
        if (lOrderedOcs.get(i).isDescendantOf(oc))
        {
          index = i + 1;
        }
      }
      if (index == -1)
      {
        lOrderedOcs.add(oc);
      }
      else
      {
        lOrderedOcs.add(index, oc);
      }
    }
    return new LinkedHashSet<>(lOrderedOcs);
  }

  private Set<AttributeType> getOrderedAttributesToDelete(Collection<AttributeType> attrsToDelete)
  {
    List<AttributeType> lOrderedAttributes = new ArrayList<>();
    for (AttributeType attr : attrsToDelete)
    {
      int index = -1;
      for (int i = 0; i < lOrderedAttributes.size(); i++)
      {
        AttributeType parent = lOrderedAttributes.get(i).getSuperiorType();
        while (parent != null && index == -1)
        {
          if (parent.equals(attr))
          {
            index = i + 1;
          }
          else
          {
            parent = parent.getSuperiorType();
          }
        }
      }
      if (index == -1)
      {
        lOrderedAttributes.add(attr);
      }
      else
      {
        lOrderedAttributes.add(index, attr);
      }
    }
    return new LinkedHashSet<>(lOrderedAttributes);
  }

  private LocalizableMessage getConfirmationMessage(
      Collection<ObjectClass> ocsToDelete, Collection<AttributeType> attrsToDelete, Schema schema)
  {
    List<ObjectClass> childClasses = new ArrayList<>();
    // Analyze objectClasses
    for (ObjectClass objectClass : ocsToDelete)
    {
      for (ObjectClass o : schema.getObjectClasses().values())
      {
        if (o.getSuperiorClasses().contains(objectClass))
        {
          childClasses.add(o);
        }
      }
      childClasses.removeAll(ocsToDelete);
    }

    List<AttributeType> childAttributes = new ArrayList<>();
    Set<String> dependentClasses = new TreeSet<>();
    // Analyze attributes
    for (AttributeType attribute : attrsToDelete)
    {
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        if (attribute.equals(attr.getSuperiorType()))
        {
          childAttributes.add(attr);
        }
      }
      childAttributes.removeAll(attrsToDelete);

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
      for (ObjectClass oc : ocsToDelete)
      {
        dependentClasses.remove(oc.getNameOrOID());
      }
    }

    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    if (!childClasses.isEmpty())
    {
      TreeSet<String> childNames = new TreeSet<>();
      for (ObjectClass oc : childClasses)
      {
        childNames.add(oc.getNameOrOID());
      }
      if (ocsToDelete.size() == 1)
      {
        mb.append(INFO_OBJECTCLASS_IS_SUPERIOR.get(ocsToDelete.iterator().next().getNameOrOID(),
            Utilities.getStringFromCollection(childNames, ", ")));
      }
      else
      {
        mb.append(INFO_OBJECTCLASSES_ARE_SUPERIOR.get(Utilities.getStringFromCollection(childNames, ", ")));
      }
      mb.append("<br>");
    }
    if (!childAttributes.isEmpty())
    {
      Set<String> childNames = new TreeSet<>();
      for (AttributeType attr : childAttributes)
      {
        childNames.add(attr.getNameOrOID());
      }
      if (attrsToDelete.size() == 1)
      {
        mb.append(INFO_ATTRIBUTE_IS_SUPERIOR.get(attrsToDelete.iterator().next().getNameOrOID(),
            Utilities.getStringFromCollection(childNames, ", ")));
      }
      else
      {
        mb.append(INFO_ATTRIBUTES_ARE_SUPERIOR.get(Utilities.getStringFromCollection(childNames, ", ")));
      }
      mb.append("<br>");
    }
    if (!dependentClasses.isEmpty())
    {
      if (attrsToDelete.size() == 1)
      {
        mb.append(INFO_ATTRIBUTE_WITH_DEPENDENCIES.get(attrsToDelete.iterator().next().getNameOrOID(),
            Utilities.getStringFromCollection(dependentClasses, ", ")));
      }
      else
      {
        mb.append(INFO_ATTRIBUTES_WITH_DEPENDENCIES.get(Utilities.getStringFromCollection(dependentClasses, ", ")));
      }
      mb.append("<br>");
    }

    List<String> allNames = new ArrayList<>();
    for (ObjectClass ocToDelete : ocsToDelete)
    {
      allNames.add(ocToDelete.getNameOrOID());
    }
    for (AttributeType attrToDelete : attrsToDelete)
    {
      allNames.add(attrToDelete.getNameOrOID());
    }
    LocalizableMessage confirmationMessage =
        INFO_CTRL_PANEL_CONFIRMATION_DELETE_SCHEMA_ELEMENTS_MSG.get(Utilities.getStringFromCollection(allNames, ", "));
    mb.append(confirmationMessage);
    return mb.toMessage();
  }

}
