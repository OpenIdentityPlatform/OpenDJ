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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.MatchingRule;
import org.opends.server.types.AttributeType;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Schema;

/**
 * The pane that is displayed when the user clicks on 'Browse Schema'.
 *
 */
public class BrowseSchemaPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -6462914563743569830L;
  private JComboBox filterAttribute;
  private FilterTextField filter;
  private JButton applyButton;
  private JButton newAttribute;
  private JButton newObjectClass;

  private JLabel lNumberOfElements;

  private JLabel lFilter;

  private SchemaBrowserRightPanel entryPane;
  private TreePanel treePane;

  private TreePath lastEntryTreePath;

  private Schema lastSchema;

  private GenericDialog newAttributeDialog;
  private GenericDialog newObjectClassDialog;

  private JMenuItem deleteMenuItem;

  private JPopupMenu popup;

  private CommonSchemaElements lastCreatedElement;

  private final Message NAME = INFO_CTRL_PANEL_SCHEMA_ELEMENT_NAME.get();
  private final Message TYPE = INFO_CTRL_PANEL_SCHEMA_ELEMENT_TYPE.get();
  private final Message PARENT_CLASS = INFO_CTRL_PANEL_PARENT_CLASS.get();
  private final Message CHILD_CLASS = INFO_CTRL_PANEL_CHILD_CLASS.get();
  private final Message REQUIRED_ATTRIBUTES =
    INFO_CTRL_PANEL_REQUIRED_ATTRIBUTES.get();
  private final Message OPTIONAL_ATTRIBUTES =
    INFO_CTRL_PANEL_OPTIONAL_ATTRIBUTES.get();

  private CategoryTreeNode attributes =
    new CategoryTreeNode(INFO_CTRL_PANEL_ATTRIBUTES_CATEGORY_NODE.get());
  private CategoryTreeNode objectClasses =
    new CategoryTreeNode(INFO_CTRL_PANEL_OBJECTCLASSES_CATEGORY_NODE.get());
  private CategoryTreeNode standardObjectClasses =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_STANDARD_OBJECTCLASSES_CATEGORY_NODE.get());
  private CategoryTreeNode standardAttributes =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_STANDARD_ATTRIBUTES_CATEGORY_NODE.get());
  private CategoryTreeNode configurationObjectClasses =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_CONFIGURATION_OBJECTCLASSES_CATEGORY_NODE.get());
  private CategoryTreeNode configurationAttributes =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_CONFIGURATION_ATTRIBUTES_CATEGORY_NODE.get());
  private CategoryTreeNode customObjectClasses =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_CUSTOM_OBJECTCLASSES_CATEGORY_NODE.get());
  private CategoryTreeNode customAttributes =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_CUSTOM_ATTRIBUTES_CATEGORY_NODE.get());
  private CategoryTreeNode matchingRules =
    new CategoryTreeNode(INFO_CTRL_PANEL_MATCHING_RULES_CATEGORY_NODE.get());
  private CategoryTreeNode syntaxes =
    new CategoryTreeNode(
        INFO_CTRL_PANEL_ATTRIBUTE_SYNTAXES_CATEGORY_NODE.get());

  private CategoryTreeNode[] underRootNodes =
  {
      objectClasses, attributes, matchingRules, syntaxes
  };

  private CategoryTreeNode[] categoryNodes = {
      standardObjectClasses, standardAttributes, customObjectClasses,
      customAttributes, configurationObjectClasses,
      configurationAttributes, matchingRules, syntaxes
  };

  private JLabel lNoMatchFound;

  private boolean ignoreSelectionEvents;

  private Message NO_SCHEMA_ITEM_SELECTED =
    INFO_CTRL_PANEL_NO_SCHEMA_ITEM_SELECTED.get();
  private Message CATEGORY_ITEM_SELECTED =
    INFO_CTRL_PANEL_CATEGORY_ITEM_SELECTED.get();
  private Message MULTIPLE_ITEMS_SELECTED =
    INFO_CTRL_PANEL_MULTIPLE_ITEMS_SELECTED.get();

  /**
   * Default constructor.
   *
   */
  public BrowseSchemaPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresBorder()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean requiresScroll()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean callConfigurationChangedInBackground()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    ((GenericDialog)Utilities.getParentDialog(this)).getRootPane().
      setDefaultButton(null);
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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

    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(10, 10, 0, 0);

    newObjectClass = Utilities.createButton(
        INFO_CTRL_PANEL_NEW_OBJECTCLASS_BUTTON.get());
    newObjectClass.setOpaque(false);
    gbc.weightx = 0.0;
    add(newObjectClass, gbc);
    newObjectClass.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newObjectClassClicked();
      }
    });

    newAttribute = Utilities.createButton(
        INFO_CTRL_PANEL_NEW_ATTRIBUTE_BUTTON.get());
    newAttribute.setOpaque(false);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    gbc.insets.left = 10;
    add(newAttribute, gbc);
    newAttribute.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newAttributeClicked();
      }
    });

    gbc.gridx ++;
    JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
    gbc.fill = GridBagConstraints.VERTICAL;
    add(sep, gbc);

    lFilter = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_FILTER_LABEL.get());
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridx ++;
    add(lFilter, gbc);

    filterAttribute = Utilities.createComboBox();
    filterAttribute.setModel(
        new DefaultComboBoxModel(new Message[]{
            NAME,
            TYPE,
            PARENT_CLASS,
            CHILD_CLASS,
            REQUIRED_ATTRIBUTES,
            OPTIONAL_ATTRIBUTES}));
    filterAttribute.setRenderer(new CustomListCellRenderer(filterAttribute));
    gbc.insets.left = 5;
    gbc.gridx ++;
    add(filterAttribute, gbc);

    filter = new FilterTextField();
    filter.addKeyListener(new KeyAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void keyReleased(KeyEvent e)
      {
        if ((e.getKeyCode() == KeyEvent.VK_ENTER) && applyButton.isEnabled())
        {
          filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
          repopulateTree(treePane.getTree());
        }
      }
    });
    filter.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
        repopulateTree(treePane.getTree());
      }
    });
    gbc.gridx ++;
    gbc.weightx = 1.0;
    add(filter, gbc);

    applyButton =
      Utilities.createButton(INFO_CTRL_PANEL_APPLY_BUTTON_LABEL.get());
    applyButton.setOpaque(false);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    gbc.insets.right = 10;
    add(applyButton, gbc);
    applyButton.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(FilterTextField.DEFAULT_REFRESH_ICON_TIME);
        repopulateTree(treePane.getTree());
      }
    });

    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 7;
    add(createSplitPane(), gbc);

    // The button panel
    gbc.gridy ++;
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
    gbc.gridx ++;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    buttonsPanel.setOpaque(true);
    buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
    gbc.insets.left = 5;
    gbc.insets.right = 10;
    gbc.gridx ++;
    gbc.weightx = 0.0;
    JButton closeButton =
      Utilities.createButton(INFO_CTRL_PANEL_CLOSE_BUTTON_LABEL.get());
    closeButton.setOpaque(false);
    buttonsPanel.add(closeButton, gbc);
    closeButton.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        closeClicked();
      }
    });

    buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
        ColorAndFontConstants.defaultBorderColor));

    return buttonsPanel;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_MANAGE_SCHEMA_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return filter;
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked()
  {
    setSecondaryValid(lFilter);
    super.closeClicked();
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

  private Component createSplitPane()
  {
    treePane = new TreePanel();

    lNoMatchFound =Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_MATCHES_FOUND_LABEL.get());
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
    JScrollPane treeScroll = Utilities.createScrollPane(p);

    entryPane.addSchemaElementSelectionListener(
        new SchemaElementSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void schemaElementSelected(SchemaElementSelectionEvent ev)
      {
        Object element = ev.getSchemaElement();
        DefaultTreeModel model =
          (DefaultTreeModel)treePane.getTree().getModel();
        Object root = model.getRoot();
        selectElementUnder(root, element, model);
      }
    });

    treePane.getTree().addTreeSelectionListener(new TreeSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(TreeSelectionEvent ev)
      {
        if (!ignoreSelectionEvents)
        {
          ignoreSelectionEvents = true;
          TreePath[] paths = treePane.getTree().getSelectionPaths();

          if (entryPane.mustCheckUnsavedChanges())
          {
            ignoreSelectionEvents = true;
            treePane.getTree().setSelectionPath(lastEntryTreePath);
            switch (entryPane.checkUnsavedChanges())
            {
            case DO_NOT_SAVE:
              break;
            case SAVE:
              break;
            case CANCEL:
              ignoreSelectionEvents = false;
              return;
            }
            if (paths != null)
            {
              treePane.getTree().setSelectionPaths(paths);
            }
            else
            {
              treePane.getTree().clearSelection();
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
              else if ((node instanceof CustomObjectClassTreeNode) ||
                  (node instanceof CustomAttributeTreeNode))
              {
                deletableElementsSelected = true;
              }
              else if (node instanceof SchemaElementTreeNode)
              {
                nonDeletableElementsSelected = true;
              }
            }
          }
          deleteMenuItem.setEnabled(deletableElementsSelected &&
              !nonDeletableElementsSelected);
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
    treeScroll.setPreferredSize(
        new Dimension((3 * treeScroll.getPreferredSize().width) / 2,
            5 * treeScroll.getPreferredSize().height));
    entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
    entryPane.setBorder(treeScroll.getBorder());
    entryPane.setPreferredSize(
        new Dimension(treeScroll.getPreferredSize().width,
        treeScroll.getPreferredSize().height));
    JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    pane.setOpaque(true); //content panes must be opaque
    pane.setLeftComponent(treeScroll);
    pane.setRightComponent(entryPane);
    pane.setResizeWeight(0.0);
    pane.setDividerLocation(treeScroll.getPreferredSize().width);
    return pane;
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    treePane.setInfo(info);
    entryPane.setInfo(info);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    if ((lastSchema == null) ||
        !ServerDescriptor.areSchemasEqual(lastSchema, desc.getSchema())||
        true)
    {
      lastSchema = desc.getSchema();
      if (lastSchema != null)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            repopulateTree(treePane.getTree());
          }
        });
      }
      else
      {
        updateErrorPane(errorPane,
            ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
            ColorAndFontConstants.errorTitleFont,
            ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
            ColorAndFontConstants.defaultFont);
        if (!errorPane.isVisible())
        {
          errorPane.setVisible(true);
        }
      }
    }
  }

  /**
   * Selects the node in the tree that corresponds to a given schema element.
   * @param root the node we must start searching for the node to be selected.
   * @param element the schema element.
   * @param model the tree model.
   * @return <CODE>true</CODE> if the node was found and selected and
   * <CODE>false</CODE> otherwise.
   */
  private boolean selectElementUnder(Object root, Object element,
      DefaultTreeModel model)
  {
    int n = model.getChildCount(root);
    boolean found = false;
    for (int i=0; i<n && !found; i++)
    {
      Object node = model.getChild(root, i);
      if (node instanceof SchemaElementTreeNode)
      {
        SchemaElementTreeNode schemaNode = (SchemaElementTreeNode)node;
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
   * @param tree the tree to be repopulated.
   */
  private void repopulateTree(JTree tree)
  {
    if (lastSchema == null)
    {
      return;
    }
    ignoreSelectionEvents = true;
    DefaultMutableTreeNode root = getRoot(tree);

    TreePath path = tree.getSelectionPath();
    DefaultMutableTreeNode lastSelectedNode = null;
    if (path != null)
    {
      lastSelectedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    }
    TreePath newSelectionPath = null;

    /**
     * {@inheritDoc}
     */
    Comparator<String> lowerCaseComparator = new Comparator<String>()
    {
      public int compare(String s1, String s2)
      {
        return s1.toLowerCase().compareTo(s2.toLowerCase());
      }
    };

    TreeSet<String> standardOcNames = new TreeSet<String>(lowerCaseComparator);
    HashMap<String, StandardObjectClassTreeNode> hmStandardOcs =
      new HashMap<String, StandardObjectClassTreeNode>();
    TreeSet<String> configurationOcNames =
      new TreeSet<String>(lowerCaseComparator);
    HashMap<String, ConfigurationObjectClassTreeNode> hmConfigurationOcs =
      new HashMap<String, ConfigurationObjectClassTreeNode>();
    TreeSet<String> customOcNames = new TreeSet<String>(lowerCaseComparator);
    HashMap<String, CustomObjectClassTreeNode> hmCustomOcs =
      new HashMap<String, CustomObjectClassTreeNode>();
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
          hmConfigurationOcs.put(name,
              new ConfigurationObjectClassTreeNode(name, oc));
        }
        else
        {
          customOcNames.add(name);
          hmCustomOcs.put(name, new CustomObjectClassTreeNode(name, oc));
        }
      }
    }


    TreeSet<String> standardAttrNames =
      new TreeSet<String>(lowerCaseComparator);
    HashMap<String, StandardAttributeTreeNode> hmStandardAttrs =
      new HashMap<String, StandardAttributeTreeNode>();
    TreeSet<String> configurationAttrNames =
      new TreeSet<String>(lowerCaseComparator);
    HashMap<String, ConfigurationAttributeTreeNode> hmConfigurationAttrs =
      new HashMap<String, ConfigurationAttributeTreeNode>();
    TreeSet<String> customAttrNames = new TreeSet<String>(lowerCaseComparator);
    HashMap<String, CustomAttributeTreeNode> hmCustomAttrs =
      new HashMap<String, CustomAttributeTreeNode>();
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
          hmConfigurationAttrs.put(name,
              new ConfigurationAttributeTreeNode(name, attr));
        }
        else
        {
          customAttrNames.add(name);
          hmCustomAttrs.put(name, new CustomAttributeTreeNode(name, attr));
        }
      }
    }

    TreeSet<String> matchingRuleNames =
      new TreeSet<String>(lowerCaseComparator);
    HashMap<String, MatchingRuleTreeNode> hmMatchingRules =
      new HashMap<String, MatchingRuleTreeNode>();
    for (MatchingRule matchingRule : lastSchema.getMatchingRules().values())
    {
      if (mustAdd(matchingRule))
      {
        String name = matchingRule.getNameOrOID();
        matchingRuleNames.add(name);
        hmMatchingRules.put(name, new MatchingRuleTreeNode(name, matchingRule));
      }
    }

    TreeSet<String> syntaxNames = new TreeSet<String>(lowerCaseComparator);
    HashMap<String, AttributeSyntaxTreeNode> hmSyntaxes =
      new HashMap<String, AttributeSyntaxTreeNode>();
    for (AttributeSyntax syntax : lastSchema.getSyntaxes().values())
    {
      if (mustAdd(syntax))
      {
        String name = syntax.getSyntaxName();
        if (name == null)
        {
          name = syntax.getOID();
        }
        syntaxNames.add(name);
        hmSyntaxes.put(name, new AttributeSyntaxTreeNode(name, syntax));
      }
    }


    ArrayList<TreeSet<String>> names = new ArrayList<TreeSet<String>>();
    names.add(standardOcNames);
    names.add(standardAttrNames);
    names.add(customOcNames);
    names.add(customAttrNames);
    names.add(configurationOcNames);
    names.add(configurationAttrNames);
    names.add(matchingRuleNames);
    names.add(syntaxNames);

    int size = 0;
    for (TreeSet<String> set : names)
    {
      size += set.size();
    }

    ArrayList<HashMap<String, ? extends DefaultMutableTreeNode>> nodes =
      new ArrayList<HashMap<String, ? extends DefaultMutableTreeNode>>();
    nodes.add(hmStandardOcs);
    nodes.add(hmStandardAttrs);
    nodes.add(hmCustomOcs);
    nodes.add(hmCustomAttrs);
    nodes.add(hmConfigurationOcs);
    nodes.add(hmConfigurationAttrs);
    nodes.add(hmMatchingRules);
    nodes.add(hmSyntaxes);

    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();

    String f = filter.getText().trim();
    boolean filterProvided = f.length() > 0;

    ArrayList<TreePath> toExpand = new ArrayList<TreePath>();

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
    positionUnderRoot ++;

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
    positionUnderRoot ++;
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
        if ((parent == standardObjectClasses) || (parent == customObjectClasses)
            || (parent == configurationObjectClasses))
        {
          if (objectClasses.getIndex(parent) == -1)
          {
            model.insertNodeInto(parent, objectClasses,
                positionUnderObjectClass);
          }
          else
          {
            expand = tree.isExpanded(new TreePath(parent.getPath()));
            parent.removeAllChildren();
          }
          positionUnderObjectClass ++;
        }
        else if ((parent == standardAttributes) || (parent == customAttributes)
            || (parent == configurationAttributes))
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
          positionUnderAttributes ++;
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
          positionUnderRoot ++;
        }

        for (String name : names.get(i))
        {
          DefaultMutableTreeNode node = nodes.get(i).get(name);
          parent.add(node);
          if ((newSelectionPath == null) &&
              ((lastSelectedNode != null) || (lastCreatedElement != null)))
          {
            if (lastCreatedElement != null)
            {
              if ((node instanceof CustomObjectClassTreeNode) &&
                  (lastCreatedElement instanceof ObjectClass))
              {
                if (name.equals(lastCreatedElement.getNameOrOID()))
                {
                  newSelectionPath = new TreePath(node.getPath());
                  lastCreatedElement = null;
                }
              }
              else if ((node instanceof CustomAttributeTreeNode) &&
                  (lastCreatedElement instanceof AttributeType))
              {
                if (name.equals(lastCreatedElement.getNameOrOID()))
                {
                  newSelectionPath = new TreePath(node.getPath());
                  lastCreatedElement = null;
                }
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

    DefaultMutableTreeNode[] ocAndAttrs = {objectClasses, attributes};
    for (DefaultMutableTreeNode node : ocAndAttrs)
    {
      if (node.getParent() != null)
      {
        if (node.getChildCount() == 0)
        {
          model.removeNodeFromParent(node);
          model.nodeStructureChanged(node);
        }
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
    int nElements = hmStandardOcs.size() + hmConfigurationOcs.size() +
    hmCustomOcs.size() + hmStandardAttrs.size() + hmConfigurationAttrs.size() +
    hmCustomAttrs.size() + hmMatchingRules.size() + hmSyntaxes.size();
    lNoMatchFound.setVisible(nElements == 0);
    treePane.setVisible(nElements > 0);
    if (nElements > 0)
    {
      lNumberOfElements.setText("Number of elements: "+nElements);
      lNumberOfElements.setVisible(true);
    }
    else
    {
      lNumberOfElements.setVisible(false);
    }
    if ((newSelectionPath == null) && (f.length() > 0))
    {
      for (i=0; i<tree.getRowCount(); i++)
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
  }

  /**
   * Updates the right entry panel.
   *
   */
  private void updateEntryPane()
  {
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    TreePath path = null;
    if ((paths != null) && (paths.length == 1))
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
            ((StandardObjectClassTreeNode)node).getObjectClass(), lastSchema);
      }
      else if (node instanceof ConfigurationObjectClassTreeNode)
      {
        entryPane.updateConfigurationObjectClass(
            ((ConfigurationObjectClassTreeNode)node).getObjectClass(),
            lastSchema);
      }
      else if (node instanceof CustomObjectClassTreeNode)
      {
        entryPane.updateCustomObjectClass(
            ((CustomObjectClassTreeNode)node).getObjectClass(), lastSchema);
      }
      else if (node instanceof StandardAttributeTreeNode)
      {
        entryPane.updateStandardAttribute(
            ((StandardAttributeTreeNode)node).getAttribute(), lastSchema);
      }
      else if (node instanceof ConfigurationAttributeTreeNode)
      {
        entryPane.updateConfigurationAttribute(
            ((ConfigurationAttributeTreeNode)node).getAttribute(), lastSchema);
      }
      else if (node instanceof CustomAttributeTreeNode)
      {
        entryPane.updateCustomAttribute(
            ((CustomAttributeTreeNode)node).getAttribute(), lastSchema);
      }
      else if (node instanceof MatchingRuleTreeNode)
      {
        entryPane.updateMatchingRule(
            ((MatchingRuleTreeNode)node).getMatchingRule(), lastSchema);
      }
      else if (node instanceof AttributeSyntaxTreeNode)
      {
        entryPane.updateAttributeSyntax(
            ((AttributeSyntaxTreeNode)node).getAttributeSyntax(), lastSchema);
      }
      else
      {
        entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
      }
    }
    else
    {
      if ((paths != null) && (paths.length > 1))
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
            nNonCategory ++;
          }
        }
        if (nNonCategory == 0)
        {
          entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
        }
        else if (categorySelected)
        {
          entryPane.displayMessage(CATEGORY_ITEM_SELECTED);
        }
        else
        {
          entryPane.displayMessage(MULTIPLE_ITEMS_SELECTED);
        }
      }
      else
      {
        entryPane.displayMessage(NO_SCHEMA_ITEM_SELECTED);
      }
    }
  }

  /**
   * Adds a popup menu.
   *
   */
  private void addPopupMenu()
  {
    popup = new JPopupMenu();
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_OBJECTCLASS_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newObjectClassClicked();
      }
    });
    popup.add(menuItem);
    menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_ATTRIBUTE_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newAttributeClicked();
      }
    });
    popup.add(menuItem);
    popup.add(new JSeparator());
    deleteMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_DELETE_SCHEMA_ELEMENT_MENU.get());
    deleteMenuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteClicked();
      }
    });
    popup.add(deleteMenuItem);
    deleteMenuItem.setEnabled(false);

    popup.setOpaque(true);

    ((CustomTree)treePane.getTree()).setPopupMenu(popup);
  }

  private void deleteClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    ArrayList<ObjectClass> ocsToDelete = new ArrayList<ObjectClass>();
    ArrayList<AttributeType> attrsToDelete = new ArrayList<AttributeType>();
    if (paths != null)
    {
      for (TreePath path : paths)
      {
        Object node = path.getLastPathComponent();
        if (node instanceof CustomObjectClassTreeNode)
        {
          ocsToDelete.add(((CustomObjectClassTreeNode)node).getObjectClass());
        }
        else if (node instanceof CustomAttributeTreeNode)
        {
          attrsToDelete.add(((CustomAttributeTreeNode)node).getAttribute());
        }
      }
    }

    Schema schema = getInfo().getServerDescriptor().getSchema();
    ArrayList<String> ocNames = new ArrayList<String>();
    ArrayList<String> attrNames = new ArrayList<String>();
    if (schema != null)
    {
//    Analyze objectClasses
      for (ObjectClass objectClass : ocsToDelete)
      {
        ArrayList<ObjectClass> childClasses = new ArrayList<ObjectClass>();
        for (ObjectClass o : schema.getObjectClasses().values())
        {
          if (objectClass.equals(o.getSuperiorClass()))
          {
            childClasses.add(o);
          }
        }
        childClasses.removeAll(ocsToDelete);
        if (!childClasses.isEmpty())
        {
          ArrayList<String> childNames = new ArrayList<String>();
          for (ObjectClass oc : childClasses)
          {
            childNames.add(oc.getNameOrOID());
          }
          String ocName = objectClass.getNameOrOID();
          errors.add(ERR_CANNOT_DELETE_PARENT_OBJECTCLASS.get(ocName,
              Utilities.getStringFromCollection(childNames, ", "), ocName));
        }
        ocNames.add(objectClass.getNameOrOID());
      }
//    Analyze attributes
      for (AttributeType attribute : attrsToDelete)
      {
        String attrName = attribute.getNameOrOID();
        ArrayList<AttributeType> childAttributes =
          new ArrayList<AttributeType>();
        for (AttributeType attr : schema.getAttributeTypes().values())
        {
          if (attribute.equals(attr.getSuperiorType()))
          {
            childAttributes.add(attr);
          }
        }
        childAttributes.removeAll(attrsToDelete);
        if (!childAttributes.isEmpty())
        {
          ArrayList<String> childNames = new ArrayList<String>();
          for (AttributeType attr : childAttributes)
          {
            childNames.add(attr.getNameOrOID());
          }
          errors.add(ERR_CANNOT_DELETE_PARENT_ATTRIBUTE.get(attrName,
              Utilities.getStringFromCollection(childNames, ", "), attrName));
        }

        ArrayList<String> dependentClasses = new ArrayList<String>();
        for (ObjectClass o : schema.getObjectClasses().values())
        {
          if (o.getRequiredAttributeChain().contains(attribute))
          {
            dependentClasses.add(o.getNameOrOID());
          }
        }
        dependentClasses.removeAll(ocsToDelete);
        if (!dependentClasses.isEmpty())
        {
          errors.add(ERR_CANNOT_DELETE_ATTRIBUTE_WITH_DEPENDENCIES.get(
              attrName,
              Utilities.getStringFromCollection(dependentClasses, ", "),
              attrName));
        }
        attrNames.add(attribute.getNameOrOID());
      }
    }
    else
    {
      errors.add(ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get());
    }
    if (errors.isEmpty())
    {
      // Reorder objectClasses and attributes to delete them in the proper
      // order.
      ArrayList<ObjectClass> orderedObjectClasses =
        new ArrayList<ObjectClass>();
      for (ObjectClass oc : ocsToDelete)
      {
        int index = -1;
        for (int i=0; i<orderedObjectClasses.size(); i++)
        {
          ObjectClass parent = orderedObjectClasses.get(i).getSuperiorClass();
          while ((parent != null) && (index == -1))
          {
            if (parent.equals(oc))
            {
              index = i+1;
            }
            else
            {
              parent = parent.getSuperiorClass();
            }
          }
        }
        if (index == -1)
        {
          orderedObjectClasses.add(oc);
        }
        else
        {
          orderedObjectClasses.add(index, oc);
        }
      }

      ArrayList<AttributeType> orderedAttributes =
        new ArrayList<AttributeType>();
      for (AttributeType attr : attrsToDelete)
      {
        int index = -1;
        for (int i=0; i<orderedAttributes.size(); i++)
        {
          AttributeType parent = orderedAttributes.get(i).getSuperiorType();
          while ((parent != null) && (index == -1))
          {
            if (parent.equals(attr))
            {
              index = i+1;
            }
            else
            {
              parent = parent.getSuperiorType();
            }
          }
        }
        if (index == -1)
        {
          orderedAttributes.add(attr);
        }
        else
        {
          orderedAttributes.add(index, attr);
        }
      }
      Message title;
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
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this), title, getInfo());
      DeleteSchemaElementsTask newTask =
        new DeleteSchemaElementsTask(getInfo(), dlg, orderedObjectClasses,
            orderedAttributes);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        ArrayList<String> allNames = new ArrayList<String>();
        allNames.addAll(ocNames);
        allNames.addAll(attrNames);
        Message confirmationMessage =
          INFO_CTRL_PANEL_CONFIRMATION_DELETE_SCHEMA_ELEMENTS_DETAILS.get(
          Utilities.getStringFromCollection(allNames, ", "));
        if (displayConfirmationDialog(
            INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
            confirmationMessage))
        {
          launchOperation(newTask,
              INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_SUMMARY.get(),
              INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_COMPLETE.get(),
              INFO_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_SUCCESSFUL.get(
                  Utilities.getStringFromCollection(allNames, ", ")),
              ERR_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_DELETING_SCHEMA_ELEMENTS_ERROR_DETAILS.get(),
              null,
              dlg);
          dlg.setVisible(true);
        }
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Checks whether a given attribute type must be added or not.  Method used to
   * do the filtering based on the name.
   * @param attr the attribute type.
   * @param attrName the name provided by the user.
   * @return <CODE>true</CODE> if the attribute must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAddAttributeName(AttributeType attr, String attrName)
  {
    ArrayList<String> values = new ArrayList<String>();
    String oid = attr.getOID();
    values.add(oid);

    String name = attr.getPrimaryName();
    if (name != null)
    {
      values.add(name);
    }
    Iterable<String> names = attr.getNormalizedNames();
    for (String v : names)
    {
      values.add(v);
    }
    return  matchFilter(values, attrName, false);
  }

  /**
   * Checks whether a given object class must be added or not.  Method used to
   * do the filtering based on the name.
   * @param oc the object class.
   * @param ocName the name provided by the user.
   * @return <CODE>true</CODE> if the objectclass must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAddObjectClassName(ObjectClass oc, String ocName)
  {
    ArrayList<String> values = new ArrayList<String>();
    String oid = oc.getOID();
    values.add(oid);

    String name = oc.getPrimaryName();
    if (name != null)
    {
      values.add(name);
    }
    Iterable<String> names = oc.getNormalizedNames();
    for (String v : names)
    {
      values.add(v);
    }
    return  matchFilter(values, ocName, false);
  }

  /**
   * Check whether the provided attribute must be added or not.
   * @param attr the attribute.
   * @return <CODE>true</CODE> if the attribute must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(AttributeType attr)
  {
    boolean mustAdd = true;

    String f = filter.getText().trim();
    if (f.length () > 0)
    {
      Object filterType = filterAttribute.getSelectedItem();

      if (NAME.equals(filterType))
      {
        mustAdd = mustAddAttributeName(attr, f);
      }
      else if (TYPE.equals(filterType))
      {
        String[] elements = f.split("[ ,]");
        String text =
          StandardAttributePanel.getTypeValue(attr).toString().toLowerCase();
        for (int i=0; i<elements.length && mustAdd; i++)
        {
          mustAdd = text.indexOf(elements[i].toLowerCase()) != -1;
        }
      }
      else
      {
        mustAdd = false;
      }
    }
    return mustAdd;
  }

  /**
   * Check whether the provided object class must be added or not.
   * @param oc the objectclass.
   * @return <CODE>true</CODE> if the objectclass must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(ObjectClass oc)
  {
    boolean mustAdd = true;
    String f = filter.getText().trim();
    if (f.length () > 0)
    {
      Object filterType = filterAttribute.getSelectedItem();

      if (NAME.equals(filterType))
      {
        mustAdd = mustAddObjectClassName(oc, f);
      }
      else if (TYPE.equals(filterType))
      {
        String[] elements = f.split("[ ,]");
        String text =
          StandardObjectClassPanel.getTypeValue(oc).toString().toLowerCase();
        for (int i=0; i<elements.length && mustAdd; i++)
        {
          mustAdd = text.indexOf(elements[i].toLowerCase()) != -1;
        }
      }
      else if (REQUIRED_ATTRIBUTES.equals(filterType) ||
          OPTIONAL_ATTRIBUTES.equals(filterType))
      {
        String[] attrValues = f.split(" ");
        Set<AttributeType> definedAttrs;
        if (REQUIRED_ATTRIBUTES.equals(filterType))
        {
          definedAttrs = oc.getRequiredAttributeChain();
        }
        else
        {
          definedAttrs = oc.getOptionalAttributeChain();
        }
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
      }
      else if (CHILD_CLASS.equals(filterType))
      {
        mustAdd = false;
        for (ObjectClass o : lastSchema.getObjectClasses().values())
        {
          boolean isChild = false;
          ObjectClass parent = o.getSuperiorClass();
          while (!isChild && (parent != null))
          {
            isChild = parent == oc;
            parent = parent.getSuperiorClass();
          }
          if (isChild)
          {
            mustAdd = mustAddObjectClassName(o, f);
            if (mustAdd)
            {
              break;
            }
          }
        }
      }
      else if (PARENT_CLASS.equals(filterType))
      {
        mustAdd = false;
        ObjectClass parentClass = oc.getSuperiorClass();
        while (!mustAdd && (parentClass != null))
        {
          mustAdd = mustAddObjectClassName(parentClass, f);
          parentClass = parentClass.getSuperiorClass();
        }
      }
      else
      {
        mustAdd = false;
      }
    }
    return mustAdd;
  }

  /**
   * Check whether the provided matching rule must be added or not.
   * @param matchingRule the matching rule.
   * @return <CODE>true</CODE> if the matching rule must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(MatchingRule matchingRule)
  {
    boolean mustAdd = true;
    String f = filter.getText().trim();
    if (f.length () > 0)
    {
      if (NAME.equals(filterAttribute.getSelectedItem()))
      {
        ArrayList<String> values = new ArrayList<String>();
        String oid = matchingRule.getOID();
        values.add(oid);

        String name = matchingRule.getName();
        if (name != null)
        {
          values.add(name);
        }
        mustAdd = matchFilter(values, f, false);
      }
      else if (TYPE.equals(filterAttribute.getSelectedItem()))
      {
        String[] elements = f.split("[ ,]");
        String text =
          MatchingRulePanel.getTypeValue(matchingRule).toString().toLowerCase();
        for (int i=0; i<elements.length && mustAdd; i++)
        {
          mustAdd = text.indexOf(elements[i].toLowerCase()) != -1;
        }
      }
      else
      {
        mustAdd = false;
      }
    }
    return mustAdd;
  }

  /**
   * Check whether the provided attribute syntax must be added or not.
   * @param syntax the attribute syntax.
   * @return <CODE>true</CODE> if the attribute syntax must be added and
   * <CODE>false</CODE> otherwise.
   */
  private boolean mustAdd(AttributeSyntax syntax)
  {
    boolean mustAdd = true;
    String f = filter.getText().trim();
    if (f.length () > 0)
    {
      if (NAME.equals(filterAttribute.getSelectedItem()))
      {
        ArrayList<String> values = new ArrayList<String>();
        String oid = syntax.getOID();
        values.add(oid);

        String name = syntax.getSyntaxName();
        if (name != null)
        {
          values.add(name);
        }
        mustAdd = matchFilter(values, f, false);
      }
      else
      {
        mustAdd = false;
      }
    }
    return mustAdd;
  }

  private boolean matchFilter(Collection<String> values, String filter,
      boolean exact)
  {
    boolean matchFilter = false;
    for (String value : values)
    {
      if (exact)
      {
        matchFilter = value.equalsIgnoreCase(filter);
      }
      else
      {
        matchFilter = value.toLowerCase().indexOf(filter.toLowerCase()) != -1;
      }
      if (matchFilter)
      {
        break;
      }
    }
    return matchFilter;
  }

  private DefaultMutableTreeNode getRoot(JTree tree)
  {
    return (DefaultMutableTreeNode)tree.getModel().getRoot();
  }

  private void newAttributeClicked()
  {
    if (newAttributeDialog == null)
    {
      NewAttributePanel panel = new NewAttributePanel(
          Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newAttributeDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newAttributeDialog,
          Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(
          new ConfigurationElementCreatedListener()
          {
            public void elementCreated(ConfigurationElementCreatedEvent ev)
            {
              Object o = ev.getConfigurationObject();
              if (o instanceof CommonSchemaElements)
              {
                lastCreatedElement = (CommonSchemaElements)o;
              }
            }
          });
    }
    newAttributeDialog.setVisible(true);
  }

  private void newObjectClassClicked()
  {
    if (newObjectClassDialog == null)
    {
      NewObjectClassPanel panel = new NewObjectClassPanel(
          Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newObjectClassDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newObjectClassDialog,
          Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(
          new ConfigurationElementCreatedListener()
          {
            public void elementCreated(ConfigurationElementCreatedEvent ev)
            {
              Object o = ev.getConfigurationObject();
              if (o instanceof CommonSchemaElements)
              {
                lastCreatedElement = (CommonSchemaElements)o;
              }
            }
          });
    }
    newObjectClassDialog.setVisible(true);
  }

  private HashMap<Object, ImageIcon> hmCategoryImages =
    new HashMap<Object, ImageIcon>();
  private HashMap<Class, ImageIcon> hmImages = new HashMap<Class, ImageIcon>();
  {
    Object[] nodes = {attributes, objectClasses, standardObjectClasses,
        standardAttributes, configurationObjectClasses, configurationAttributes,
        customObjectClasses, customAttributes, matchingRules, syntaxes};
    String[] paths = {"ds-attr-folder.png", "ds-class-folder.png",
        "ds-folder.png",
        "ds-folder.png", "ds-folder.png", "ds-folder.png", "ds-folder.png",
        "ds-folder.png", "ds-rule-folder.png", "ds-syntax-folder.png"};
    for (int i=0; i<nodes.length; i++)
    {
      hmCategoryImages.put(nodes[i],
          Utilities.createImageIcon(IconPool.IMAGE_PATH+"/"+paths[i]));
    }
    Class[] classes = {ConfigurationAttributeTreeNode.class,
        StandardAttributeTreeNode.class, CustomAttributeTreeNode.class,
        ConfigurationObjectClassTreeNode.class,
        StandardObjectClassTreeNode.class, CustomObjectClassTreeNode.class,
        MatchingRuleTreeNode.class, AttributeSyntaxTreeNode.class};
    String[] ocPaths = {"ds-attr.png", "ds-attr.png", "ds-attr.png",
        "ds-class.png", "ds-class.png", "ds-class.png", "ds-rule.png",
        "ds-syntax.png"};
    for (int i=0; i<classes.length; i++)
    {
      hmImages.put(classes[i],
          Utilities.createImageIcon(IconPool.IMAGE_PATH+"/"+ocPaths[i]));
    }
  };
  /**
   * Specific class used to render the nodes in the tree.  It uses specific
   * icons for the nodes.
   *
   */
  protected class SchemaTreeCellRenderer extends TreeCellRenderer
  {
    private static final long serialVersionUID = -3390568254259441766L;

    /**
     * {@inheritDoc}
     */
    public Component getTreeCellRendererComponent(JTree tree, Object value,
        boolean isSelected, boolean isExpanded, boolean isLeaf, int row,
        boolean hasFocus)
    {
      super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded,
          isLeaf, row, hasFocus);
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
}
