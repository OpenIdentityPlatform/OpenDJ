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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.GeneralMonitoringTreeNode;
import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * The pane that is displayed when the user clicks on 'General Monitoring'.
 *
 */
public class BrowseGeneralMonitoringPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 6462914563746678830L;

  /**
   * The panel containing the tree.
   */
  private TreePanel treePane;

  private JScrollPane treeScroll;

  private ServerDescriptor lastServer;

  private String lastServerName;

  private boolean ignoreSelectionEvents;

  private Message NO_ELEMENT_SELECTED =
    INFO_CTRL_PANEL_GENERAL_MONITORING_NO_ITEM_SELECTED.get();
  private Message MULTIPLE_ITEMS_SELECTED =
    INFO_CTRL_PANEL_MULTIPLE_ITEMS_SELECTED_LABEL.get();

  /**
   * The enumeration used to define the different static nodes of the tree.
   *
   */
  protected enum NodeType
  {
    /**
     * Root node.
     */
    ROOT,
    /**
     * System information node.
     */
    SYSTEM_INFORMATION,
    /**
     * Java information node.
     */
    JAVA_INFORMATION,
    /**
     * Work queue node.
     */
    WORK_QUEUE,
    /**
     * Entry caches node.
     */
    ENTRY_CACHES,
    /**
     * Database environment node.
     */
    DB_ENVIRONMENT
  }

  /**
   * The panel displaying the informations about the selected node.
   */
  protected GeneralMonitoringRightPanel entryPane;

  /**
   * Default constructor.
   *
   */
  public BrowseGeneralMonitoringPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean requiresBorder()
  {
    return false;
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
  @Override
  public boolean callConfigurationChangedInBackground()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
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
    gbc.gridwidth = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    addErrorPane(gbc);

    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 7;
    add(createSplitPane(), gbc);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_GENERAL_MONITORING_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Component getPreferredFocusComponent()
  {
    return treePane;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void okClicked()
  {
    // No ok button
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * Creates the browser right panel.
   * @return the created browser right panel.
   */
  private GeneralMonitoringRightPanel createBrowserRightPanel()
  {
    return new GeneralMonitoringRightPanel();
  }

  private Component createSplitPane()
  {
    treePane = new TreePanel();

    entryPane = createBrowserRightPanel();

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
    treeScroll = Utilities.createScrollPane(p);

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
          updateEntryPane();
          ignoreSelectionEvents = false;
        }
      }
    });
    JTree tree = treePane.getTree();
    repopulateTree(tree, true);
    tree.setRootVisible(true);
    tree.setVisibleRowCount(20);
    tree.expandPath(new TreePath(getRoot(tree)));
    tree.setCellRenderer(new GeneralMonitoringTreeCellRenderer());
    treeScroll.setPreferredSize(
        new Dimension(treeScroll.getPreferredSize().width + 30,
            3 * treeScroll.getPreferredSize().height));
    entryPane.displayMessage(NO_ELEMENT_SELECTED);
    entryPane.setBorder(getRightPanelBorder());
    entryPane.setPreferredSize(
        new Dimension(treeScroll.getPreferredSize().width * 2,
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
  @Override
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
    ServerDescriptor server = ev.getNewDescriptor();
    if (serverChanged(server))
    {
      final boolean firstTimeCalled = lastServer == null;
      lastServer = server;

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          String serverName = getServerName(lastServer);
          // Repopulate the tree to display a root node with server information
          if (!serverName.equals(lastServerName))
          {
            repopulateTree(treePane.getTree(), false);
            lastServerName = serverName;
          }
          if (firstTimeCalled)
          {
            // Select the root
            treePane.getTree().setSelectionInterval(0, 0);
          }
          else
          {
            // Reselect
            updateEntryPane();
          }
        }
      });
    }
    else
    {
      lastServer = server;
    }

    boolean displayErrorPane = false;
    Message errorTitle = Message.EMPTY;
    Message errorDetails = Message.EMPTY;
    ServerDescriptor.ServerStatus status = server.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!server.isAuthenticated())
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(
   INFO_CTRL_PANEL_AUTH_REQUIRED_TO_BROWSE_MONITORING_SUMMARY.
   get());
        mb.append("<br><br>"+getAuthenticateHTML());
        errorDetails = mb.toMessage();
        errorTitle = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();

        displayErrorPane = true;
      }
    }
    else
    {
      errorTitle = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      MessageBuilder mb = new MessageBuilder();
      mb.append(
          INFO_CTRL_PANEL_SERVER_MUST_RUN_TO_BROWSE_MONITORING_SUMMARY.
          get());
      mb.append("<br><br>");
      mb.append(getStartServerHTML());
      errorDetails = mb.toMessage();
      displayErrorPane = true;
    }
    final boolean fDisplayErrorPane = displayErrorPane;
    final Message fErrorTitle = errorTitle;
    final Message fErrorDetails = errorDetails;
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        errorPane.setVisible(fDisplayErrorPane);
        if (fDisplayErrorPane)
        {
          updateErrorPane(errorPane, fErrorTitle,
              ColorAndFontConstants.errorTitleFont, fErrorDetails,
              ColorAndFontConstants.defaultFont);
        }
      }
    });
  }

  /**
   * Populates the tree.  Should be called only once since the tree in this
   * panel is static.
   * @param tree the tree to be repopulated.
   * @param forceScroll whether the scroll must be reset or not.
   */
  private void repopulateTree(JTree tree, boolean forceScroll)
  {
    ignoreSelectionEvents = true;

    ViewPositions pos = Utilities.getViewPositions(treeScroll);

    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    GeneralMonitoringTreeNode root;
    if (server == null)
    {
      root =
        new GeneralMonitoringTreeNode(
            INFO_CTRL_PANEL_GENERAL_MONITORING_ROOT.get().toString(),
            NodeType.ROOT,
            true);
    }
    else
    {
      root =
        new GeneralMonitoringTreeNode(
            getServerName(server),
            NodeType.ROOT,
            true);
    }

    Message[] messages = getNodeMessages();
    NodeType[] identifiers = getNodeTypes();
    for (int i=0; i < messages.length; i++)
    {
      root.add(new GeneralMonitoringTreeNode(messages[i].toString(),
          identifiers[i], false));
    }

    DefaultTreeModel model = new DefaultTreeModel(root);
    tree.setModel(model);

    Utilities.updateViewPositions(pos);
    ignoreSelectionEvents = false;
  }

  /**
   * Updates the right entry panel.
   *
   */
  private void updateEntryPane()
  {
    ViewPositions pos = Utilities.getViewPositions(entryPane);
    boolean canDisplayMonitorInformation = true;
    if (getInfo() == null)
    {
      return;
    }
    ServerDescriptor server = getInfo().getServerDescriptor();
    ServerDescriptor.ServerStatus status = server.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!server.isAuthenticated())
      {
        canDisplayMonitorInformation = false;
        entryPane.displayMessage(
            INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get());
      }
    }
    else
    {
      canDisplayMonitorInformation = false;
      entryPane.displayMessage(
          INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get());
    }

    if (canDisplayMonitorInformation)
    {
      TreePath[] paths = treePane.getTree().getSelectionPaths();
      TreePath path = null;
      if ((paths != null) && (paths.length == 1))
      {
        path = paths[0];
      }
      if (path != null)
      {
        GeneralMonitoringTreeNode node =
          (GeneralMonitoringTreeNode)path.getLastPathComponent();
        NodeType type = (NodeType)node.getIdentifier();
        switch (type)
        {
        case ROOT:
          entryPane.updateRoot();
          break;
        case SYSTEM_INFORMATION:
          entryPane.updateSystemInformation();
          break;
        case WORK_QUEUE:
          entryPane.updateWorkQueue();
          break;
        case ENTRY_CACHES:
          entryPane.updateEntryCaches();
          break;
        case DB_ENVIRONMENT:
          entryPane.updateDBEnvironment();
          break;
        case JAVA_INFORMATION:
          entryPane.updateJavaInformation();
          break;
        default:
          throw new IllegalStateException("Unknown node type: "+type);
        }
      }
      else
      {
        if ((paths != null) && (paths.length > 1))
        {
          entryPane.displayMessage(MULTIPLE_ITEMS_SELECTED);
        }
        else
        {
          entryPane.displayMessage(NO_ELEMENT_SELECTED);
        }
      }
    }
    Utilities.updateViewPositions(pos);
  }

  private DefaultMutableTreeNode getRoot(JTree tree)
  {
    return (DefaultMutableTreeNode)tree.getModel().getRoot();
  }

  private boolean serverChanged(ServerDescriptor desc)
  {
    boolean changed = false;
    if (lastServer != null)
    {
      // Just compare the elements interesting for this panel
      changed =
        !desc.getBackends().equals(lastServer.getBackends());
      if (!changed)
      {
        CustomSearchResult[] monitor1 =
        {
            lastServer.getEntryCachesMonitor(),
            lastServer.getJvmMemoryUsageMonitor(),
            lastServer.getRootMonitor(),
            lastServer.getSystemInformationMonitor(),
            lastServer.getWorkQueueMonitor()
        };
        CustomSearchResult[] monitor2 =
        {
            desc.getEntryCachesMonitor(),
            desc.getJvmMemoryUsageMonitor(),
            desc.getRootMonitor(),
            desc.getSystemInformationMonitor(),
            desc.getWorkQueueMonitor()
        };
        for (int i=0; i<monitor1.length && !changed; i++)
        {
          if (monitor1[i] == null)
          {
            changed = monitor2[i] != null;
          }
          else
          {
            changed = !monitor1[i].equals(monitor2[i]);
          }
        }
      }
    }
    else
    {
      changed = true;
    }
    return changed;
  }

  private HashMap<Object, ImageIcon> hmImages =
    new HashMap<Object, ImageIcon>();
  {
    NodeType[] identifiers = {
        NodeType.ROOT,
        NodeType.SYSTEM_INFORMATION,
        NodeType.JAVA_INFORMATION,
        NodeType.WORK_QUEUE,
        NodeType.ENTRY_CACHES,
        NodeType.DB_ENVIRONMENT
    };
    Message[] ocPaths = {
        INFO_CTRL_PANEL_GENERAL_MONITORING_ROOT_TREE_NODE.get(),
        INFO_CTRL_PANEL_SYSTEM_INFORMATION_TREE_NODE.get(),
        INFO_CTRL_PANEL_JVM_MEMORY_USAGE_TREE_NODE.get(),
        INFO_CTRL_PANEL_WORK_QUEUE_TREE_NODE.get(),
        INFO_CTRL_PANEL_ENTRY_CACHES_TREE_NODE.get(),
        INFO_CTRL_PANEL_DB_ENVIRONMENT_TREE_NODE.get()
    };
    for (int i=0; i<identifiers.length; i++)
    {
      hmImages.put(identifiers[i],
          Utilities.createImageIcon(IconPool.IMAGE_PATH+"/"+ocPaths[i],
              getClass().getClassLoader()));
    }
  };

  private String getServerName(ServerDescriptor server)
  {
    String serverName = server.getHostname()+":"+
    server.getAdminConnector().getPort();
    return serverName;
  }

  /**
   * Specific class used to render the nodes in the tree.  It uses specific
   * icons for the nodes.
   *
   */
  protected class GeneralMonitoringTreeCellRenderer extends TreeCellRenderer
  {
    private static final long serialVersionUID = -3390566664259441766L;

    /**
     * {@inheritDoc}
     */
    @Override
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
      if (value instanceof GeneralMonitoringTreeNode)
      {
        icon = hmImages.get(
            ((GeneralMonitoringTreeNode)value).getIdentifier());
      }
      else
      {
        throw new IllegalStateException("Unexpected tree node: "+value);
      }
      return icon;
    }
  }

  /**
   * Returns the labels of the nodes to be displayed.
   * @return the labels of the nodes to be displayed.
   */
  protected Message[] getNodeMessages()
  {
    return new Message[] {
      INFO_CTRL_PANEL_SYSTEM_INFORMATION.get(),
      INFO_CTRL_PANEL_JAVA_INFORMATION.get(),
      INFO_CTRL_PANEL_WORK_QUEUE.get(),
      INFO_CTRL_PANEL_ENTRY_CACHES.get(),
      INFO_CTRL_PANEL_DB_ENVIRONMENT.get()
    };
  }

  /**
   * Returns the node types to be displayed.
   * @return the node types to be displayed.
   */
  protected NodeType[] getNodeTypes()
  {
    return new NodeType[] {
        NodeType.SYSTEM_INFORMATION,
        NodeType.JAVA_INFORMATION,
        NodeType.WORK_QUEUE,
        NodeType.ENTRY_CACHES,
        NodeType.DB_ENVIRONMENT
    };
  }
}

