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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.event.*;
import org.opends.guitools.controlpanel.task.DeleteIndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.CustomTree;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.AbstractIndexTreeNode;
import org.opends.guitools.controlpanel.ui.nodes.CategoryTreeNode;
import org.opends.guitools.controlpanel.ui.nodes.IndexTreeNode;
import org.opends.guitools.controlpanel.ui.nodes.VLVIndexTreeNode;
import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The pane that is displayed when the user clicks on 'Browse Indexes'.
 *
 */
public class BrowseIndexPanel extends StatusGenericPanel
implements IndexModifiedListener
{
  private static final long serialVersionUID = 4560020571983291585L;

  private JComboBox backends;
  private JLabel lNoBackendsFound;

  private IndexBrowserRightPanel entryPane;
  private TreePanel treePane;

  private JButton newIndex;
  private JButton newVLVIndex;

  private CategoryTreeNode standardIndexes = new CategoryTreeNode(
      INFO_CTRL_PANEL_INDEXES_CATEGORY_NODE.get());
  private CategoryTreeNode vlvIndexes = new CategoryTreeNode(
      INFO_CTRL_PANEL_VLV_INDEXES_CATEGORY_NODE.get());

  private AbstractIndexDescriptor lastCreatedIndex;

  private TreePath lastIndexTreePath;

  private CategoryTreeNode[] categoryNodes = {
      standardIndexes, vlvIndexes
  };

  private JMenuItem deleteMenuItem;

  private GenericDialog newIndexDialog;
  private GenericDialog newVLVIndexDialog;

  private boolean ignoreSelectionEvents;

  private boolean firstTreeRepopulate = true;

  /**
   * Default constructor.
   *
   */
  public BrowseIndexPanel()
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
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    ((GenericDialog)Utilities.getParentDialog(this)).
      getRootPane().setDefaultButton(null);
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
    gbc.gridwidth = 5;
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
    JLabel lBackend =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BACKEND_LABEL.get());
    add(lBackend, gbc);

    backends = Utilities.createComboBox();
    backends.setModel(new DefaultComboBoxModel(new String[]{}));
    ItemListener comboListener = new ItemListener()
    {
      /**
       * {@inheritDoc}
       */
      public void itemStateChanged(ItemEvent ev)
      {
        if (!ignoreSelectionEvents &&
            (ev.getStateChange() == ItemEvent.SELECTED))
        {
          repopulateTree(treePane.getTree());
        }
      }
    };
    backends.addItemListener(comboListener);
    gbc.insets.left = 5;
    gbc.gridx ++;
    add(backends, gbc);
    lNoBackendsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_BACKENDS_FOUND_LABEL.get());
    add(lNoBackendsFound, gbc);
    lNoBackendsFound.setVisible(false);

    newIndex = Utilities.createButton(
        INFO_CTRL_PANEL_NEW_INDEX_BUTTON_LABEL.get());
    newIndex.setOpaque(false);
    newIndex.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newIndexClicked();
      }
    });
    gbc.gridx ++;
    gbc.insets.left = 10;
    add(newIndex, gbc);

    newVLVIndex = Utilities.createButton(
        INFO_CTRL_PANEL_NEW_VLV_INDEX_BUTTON_LABEL.get());
    newVLVIndex.setOpaque(false);
    newVLVIndex.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newVLVIndexClicked();
      }
    });
    gbc.gridx ++;
    gbc.insets.right = 10;
    add(newVLVIndex, gbc);
    gbc.gridx ++;
    gbc.weightx = 1.0;
    add(Box.createHorizontalGlue(), gbc);

    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 5;
    add(createSplitPane(), gbc);
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_MANAGE_INDEXES_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return backends;
  }

  /**
   * {@inheritDoc}
   */
  public void closeClicked()
  {
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
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * {@inheritDoc}
   */
  private Component createSplitPane()
  {
    JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    pane.setOpaque(true); //content panes must be opaque

    treePane = new TreePanel();
    Utilities.setBorder(treePane, new EmptyBorder(10, 0, 10, 0));

    entryPane = new IndexBrowserRightPanel();
    JScrollPane treeScroll = Utilities.createScrollPane(treePane);

    entryPane.addIndexSelectionListener(new IndexSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void indexSelected(IndexSelectionEvent ev)
      {
        AbstractIndexDescriptor index = ev.getIndex();
        TreeNode parentNode;
        if (index instanceof IndexDescriptor)
        {
          parentNode = standardIndexes;
        }
        else
        {
          parentNode = vlvIndexes;
        }
        DefaultTreeModel model =
          (DefaultTreeModel)treePane.getTree().getModel();
        int n = model.getChildCount(parentNode);
        for (int i=0; i<n; i++)
        {
          AbstractIndexTreeNode node =
            (AbstractIndexTreeNode)model.getChild(parentNode, i);
          if (node.getName().equals(index.getName()))
          {
            TreePath newSelectionPath = new TreePath(node.getPath());
            treePane.getTree().setSelectionPath(newSelectionPath);
            treePane.getTree().scrollPathToVisible(newSelectionPath);
            break;
          }
        }
      }
    });


//  Create a split pane
    pane.setLeftComponent(treeScroll);
    pane.setRightComponent(entryPane);
    pane.setResizeWeight(0.0);

    treePane.getTree().addTreeSelectionListener(new TreeSelectionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void valueChanged(TreeSelectionEvent ev)
      {
        if (!ignoreSelectionEvents)
        {
          TreePath[] paths = treePane.getTree().getSelectionPaths();

          if (entryPane.mustCheckUnsavedChanges())
          {
            ignoreSelectionEvents = true;
            treePane.getTree().setSelectionPath(lastIndexTreePath);
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
            ignoreSelectionEvents = false;
          }


          boolean deletableElementsSelected = false;
          boolean nonDeletableElementsSelected = false;
          if (paths != null)
          {
            for (TreePath path : paths)
            {
              Object node = path.getLastPathComponent();
              if (node instanceof IndexTreeNode)
              {
                IndexDescriptor index = ((IndexTreeNode)node).getIndex();
                if (index.isDatabaseIndex())
                {
                  nonDeletableElementsSelected = true;
                }
                else
                {
                  deletableElementsSelected = true;
                }
              }
              else if (node instanceof VLVIndexTreeNode)
              {
                deletableElementsSelected = true;
              }
            }
          }
          deleteMenuItem.setEnabled(deletableElementsSelected &&
              !nonDeletableElementsSelected);
          updateEntryPane();
        }
      }
    });
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("Tree root");
    for (DefaultMutableTreeNode node : categoryNodes)
    {
      root.add(node);
    }
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = treePane.getTree();
    tree.setModel(model);
    tree.setRootVisible(false);
    tree.setVisibleRowCount(20);
    tree.expandPath(new TreePath(root));
    tree.setCellRenderer(new IndexTreeCellRenderer());
    addPopupMenu();

    treeScroll.setPreferredSize(
        new Dimension(2 * treeScroll.getPreferredSize().width,
            8 * treeScroll.getPreferredSize().height));
    entryPane.setBorder(treeScroll.getBorder());
    entryPane.setPreferredSize(
        new Dimension((treeScroll.getPreferredSize().width * 5) / 2,
        treeScroll.getPreferredSize().height));
    pane.setDividerLocation(treeScroll.getPreferredSize().width);
    entryPane.displayVoid();
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
    info.addIndexModifiedListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    ignoreSelectionEvents = true;
    ServerDescriptor desc = ev.getNewDescriptor();
    updateSimpleBackendComboBoxModel(backends, lNoBackendsFound,
        desc);
    refreshContents(desc);
  }

  /**
   * Adds a pop up menu.
   *
   */
  private void addPopupMenu()
  {
    final JPopupMenu popup = new JPopupMenu();
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_INDEX_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newIndexClicked();
      }
    });
    popup.add(menuItem);
    menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_NEW_VLV_INDEX_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        newVLVIndexClicked();
      }
    });
    popup.add(menuItem);
    popup.add(new JSeparator());
    deleteMenuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_DELETE_INDEX_MENU.get());
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

    ((CustomTree)treePane.getTree()).setPopupMenu(popup);
  }

  /**
   * Refresh the contents of the tree.
   * @param desc the descriptor containing the index configuration.
   */
  private void refreshContents(final ServerDescriptor desc)
  {
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        repopulateTree(treePane.getTree());
        ignoreSelectionEvents = false;
        boolean userBackendsDefined = backends.getModel().getSize() > 0;
        newIndex.setEnabled(userBackendsDefined);
        newVLVIndex.setEnabled(userBackendsDefined);
        if (!userBackendsDefined)
        {
          entryPane.displayVoid();
          updateErrorPane(errorPane,
              ERR_CTRL_PANEL_NO_BACKENDS_FOUND_TITLE.get(),
              ColorAndFontConstants.errorTitleFont,
              ERR_CTRL_PANEL_NO_BACKENDS_FOUND_DETAILS.get(),
              ColorAndFontConstants.defaultFont);
          errorPane.setVisible(true);
        }
        else
        {
          errorPane.setVisible(false);
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void indexModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /**
   * {@inheritDoc}
   */
  public void backendIndexesModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /**
   * Repopulates the contents of the tree.
   * @param tree the tree to be repopulated.
   */
  private void repopulateTree(JTree tree)
  {
    ignoreSelectionEvents = true;
    DefaultMutableTreeNode root = getRoot(tree);

    TreePath path = tree.getSelectionPath();
    DefaultMutableTreeNode lastSelectedNode = null;
    if (path != null)
    {
      lastSelectedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    }
    TreePath newSelectionPath = null;

    BackendDescriptor backend = null;
    String backendName = (String)backends.getSelectedItem();
    if (backendName != null)
    {
      for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
      {
        if (b.getBackendID().equalsIgnoreCase(backendName))
        {
          backend = b;
          break;
        }
      }
    }

    ArrayList<ArrayList<? extends AbstractIndexTreeNode>> nodes =
      new ArrayList<ArrayList<? extends AbstractIndexTreeNode>>();
    ArrayList<IndexTreeNode> standardIndexNodes =
      new ArrayList<IndexTreeNode>();
    ArrayList<VLVIndexTreeNode> vlvIndexNodes =
      new ArrayList<VLVIndexTreeNode>();
    nodes.add(standardIndexNodes);
    nodes.add(vlvIndexNodes);

    if (backend != null)
    {
      for (IndexDescriptor index : backend.getIndexes())
      {
        standardIndexNodes.add(new IndexTreeNode(index.getName(), index));
      }
      for (VLVIndexDescriptor index : backend.getVLVIndexes())
      {
        vlvIndexNodes.add(new VLVIndexTreeNode(index.getName(), index));
      }
    }


    DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
    int i = 0;
    int positionUnderRoot = 0;
    for (DefaultMutableTreeNode parent : categoryNodes)
    {
      if (nodes.get(i).size() == 0)
      {
        if (root.getIndex(parent) != -1)
        {
          model.removeNodeFromParent(parent);
          parent.removeAllChildren();
        }
      }
      else
      {
        boolean expand = true;
        if (root.getIndex(parent) == -1)
        {
          model.insertNodeInto(parent, root, positionUnderRoot);
        }
        else
        {
          expand = tree.isExpanded(new TreePath(parent)) ||
          (parent.getChildCount() == 0);
          parent.removeAllChildren();
        }
        for (AbstractIndexTreeNode node : nodes.get(i))
        {
          parent.add(node);
          if ((newSelectionPath == null) &&
              ((lastSelectedNode != null) || (lastCreatedIndex != null)))
          {
            if (lastCreatedIndex != null)
            {
              if ((node instanceof IndexTreeNode) &&
                  (lastCreatedIndex instanceof IndexDescriptor))
              {
                if (node.getName().equals(lastCreatedIndex.getName()))
                {
                  newSelectionPath = new TreePath(node.getPath());
                  lastCreatedIndex = null;
                }
              }
              else if ((node instanceof VLVIndexTreeNode) &&
                  (lastCreatedIndex instanceof VLVIndexDescriptor))
              {
                if (node.getName().equals(lastCreatedIndex.getName()))
                {
                  newSelectionPath = new TreePath(node.getPath());
                  lastCreatedIndex = null;
                }
              }
            }
            else if (node.getName().equals(lastSelectedNode.getUserObject()))
            {
              newSelectionPath = new TreePath(node.getPath());
            }
          }
        }
        model.nodeStructureChanged(parent);
        if (expand)
        {
          tree.expandPath(new TreePath(parent.getPath()));
        }
        positionUnderRoot++;
      }
      i++;
    }

    if (newSelectionPath == null)
    {
      if (firstTreeRepopulate)
      {
        newSelectionPath = new TreePath(standardIndexes.getPath());
      }
    }
    if (newSelectionPath != null)
    {
      tree.setSelectionPath(newSelectionPath);
      tree.scrollPathToVisible(newSelectionPath);
    }

    updateEntryPane();

    firstTreeRepopulate = false;
    ignoreSelectionEvents = false;
  }

  /**
   * Updates the contents of the right panel.
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
    lastIndexTreePath = path;
    if (path != null)
    {
      Object node = path.getLastPathComponent();
      if (node instanceof IndexTreeNode)
      {
        entryPane.updateStandardIndex(
            ((IndexTreeNode)node).getIndex());
      }
      else if (node instanceof VLVIndexTreeNode)
      {
        entryPane.updateVLVIndex(((VLVIndexTreeNode)node).getIndex());
      }
      else if (node == standardIndexes)
      {
        String backendName = (String)backends.getSelectedItem();
        entryPane.updateBackendIndexes(backendName);
      }
      else if (node == vlvIndexes)
      {
        String backendName = (String)backends.getSelectedItem();
        entryPane.updateBackendVLVIndexes(backendName);
      }
      else
      {
        entryPane.displayVoid();
      }
    }
    else
    {
      if ((paths != null) && (paths.length > 1))
      {
        entryPane.displayMultiple();
      }
      else
      {
        entryPane.displayVoid();
      }
    }
  }

  private DefaultMutableTreeNode getRoot(JTree tree)
  {
    return (DefaultMutableTreeNode)tree.getModel().getRoot();
  }

  private void newIndexClicked()
  {
    if (newIndexDialog == null)
    {
      NewIndexPanel panel =
        new NewIndexPanel((String)backends.getSelectedItem(),
          Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newIndexDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newIndexDialog,
          Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(
          new ConfigurationElementCreatedListener()
          {
            public void elementCreated(ConfigurationElementCreatedEvent ev)
            {
              Object o = ev.getConfigurationObject();
              if (o instanceof AbstractIndexDescriptor)
              {
                lastCreatedIndex = (AbstractIndexDescriptor)o;
              }
            }
          });
    }
    newIndexDialog.setVisible(true);
  }

  private void newVLVIndexClicked()
  {
    if (newVLVIndexDialog == null)
    {
      NewVLVIndexPanel panel =
        new NewVLVIndexPanel((String)backends.getSelectedItem(),
          Utilities.getParentDialog(this));
      panel.setInfo(getInfo());
      newVLVIndexDialog = new GenericDialog(null, panel);
      Utilities.centerGoldenMean(newVLVIndexDialog,
          Utilities.getParentDialog(this));
      panel.addConfigurationElementCreatedListener(
          new ConfigurationElementCreatedListener()
          {
            /**
             * {@inheritDoc}
             */
            public void elementCreated(ConfigurationElementCreatedEvent ev)
            {
              Object o = ev.getConfigurationObject();
              if (o instanceof AbstractIndexDescriptor)
              {
                lastCreatedIndex = (AbstractIndexDescriptor)o;
              }
            }
          });
    }
    newVLVIndexDialog.setVisible(true);
  }

  private void deleteClicked()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    TreePath[] paths = treePane.getTree().getSelectionPaths();
    ArrayList<AbstractIndexDescriptor> indexesToDelete =
      new ArrayList<AbstractIndexDescriptor>();
    ArrayList<String> indexesNames = new ArrayList<String>();
    if (paths != null)
    {
      for (TreePath path : paths)
      {
        Object node = path.getLastPathComponent();
        if (node instanceof IndexTreeNode)
        {
          indexesToDelete.add(((IndexTreeNode)node).getIndex());
        }
        else if (node instanceof VLVIndexTreeNode)
        {
          indexesToDelete.add(((VLVIndexTreeNode)node).getIndex());
        }
      }
    }
    else
    {
      errors.add(ERR_CTRL_PANEL_NO_INDEX_SELECTED.get());
    }
    for (AbstractIndexDescriptor index : indexesToDelete)
    {
      indexesNames.add(index.getName());
    }
    String nameLabel = Utilities.getStringFromCollection(indexesNames, ", ");
    String backendName = indexesToDelete.get(0).getBackend().getBackendID();
    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_DELETE_INDEXES_TITLE.get(), getInfo());
      DeleteIndexTask newTask = new DeleteIndexTask(getInfo(), dlg,
          indexesToDelete);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        if (displayConfirmationDialog(
            INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
            INFO_CTRL_PANEL_CONFIRMATION_INDEXES_DELETE_DETAILS.get(nameLabel,
                backendName)))
        {
          launchOperation(newTask,
              INFO_CTRL_PANEL_DELETING_INDEXES_SUMMARY.get(),
              INFO_CTRL_PANEL_DELETING_INDEXES_COMPLETE.get(),
              INFO_CTRL_PANEL_DELETING_INDEXES_SUCCESSFUL.get(nameLabel,
                  backendName),
              ERR_CTRL_PANEL_DELETING_INDEXES_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_DELETING_INDEXES_ERROR_DETAILS.get(nameLabel),
              null,
              dlg);
          dlg.setVisible(true);
        }
      }
    }
    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  private HashMap<Object, ImageIcon> hmCategoryImages =
    new HashMap<Object, ImageIcon>();
  private HashMap<Class, ImageIcon> hmImages = new HashMap<Class, ImageIcon>();
  {
    Object[] nodes = {standardIndexes, vlvIndexes};
    String[] paths = {"ds-idx-folder.png", "ds-vlv-idx-folder.png"};
    for (int i=0; i<nodes.length; i++)
    {
      hmCategoryImages.put(nodes[i],
          Utilities.createImageIcon(IconPool.IMAGE_PATH+"/"+paths[i]));
    }
    Class[] classes = {IndexTreeNode.class, VLVIndexTreeNode.class};
    String[] ocPaths = {"ds-idx.png", "ds-vlv-idx.png"};
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
  protected class IndexTreeCellRenderer extends TreeCellRenderer
  {
    private ImageIcon readOnlyIndexIcon =
      Utilities.createImageIcon(IconPool.IMAGE_PATH+"/ds-idx-ro.png");

    private static final long serialVersionUID = -6953837045703643228L;

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
      ImageIcon icon = null;
      if (value instanceof IndexTreeNode)
      {
        if (((IndexTreeNode)value).getIndex().isDatabaseIndex())
        {
          icon = readOnlyIndexIcon;
        }
      }
      if (icon == null)
      {
        icon = hmImages.get(value.getClass());
        if (icon == null)
        {
          icon = hmCategoryImages.get(value);
        }
      }
      return icon;
    }
  }
}
