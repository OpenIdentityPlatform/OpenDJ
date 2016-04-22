/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.StaticUtils.isOEMVersion;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.guitools.controlpanel.browser.IconPool;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.GeneralMonitoringTreeNode;
import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;

/** The pane that is displayed when the user clicks on 'General Monitoring'. */
class BrowseGeneralMonitoringPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 6462914563746678830L;

  /** The panel containing the tree. */
  private TreePanel treePane;

  private JScrollPane treeScroll;

  private ServerDescriptor lastServer;

  private String lastServerName;

  private boolean ignoreSelectionEvents;

  private final LocalizableMessage NO_ELEMENT_SELECTED =
    INFO_CTRL_PANEL_GENERAL_MONITORING_NO_ITEM_SELECTED.get();
  private final LocalizableMessage MULTIPLE_ITEMS_SELECTED =
    INFO_CTRL_PANEL_MULTIPLE_ITEMS_SELECTED_LABEL.get();

  /** The enumeration used to define the different static nodes of the tree. */
  protected enum NodeType
  {
    /** Root node. */
    ROOT,
    /** System information node. */
    SYSTEM_INFORMATION,
    /** Java information node. */
    JAVA_INFORMATION,
    /** Work queue node. */
    WORK_QUEUE,
    /** Entry caches node. */
    ENTRY_CACHES,
    /** JE Databases information node. */
    JE_DATABASES_INFORMATION,
    /** PDB databases information node. */
    PDB_DATABASES_INFORMATION
  }

  /** The panel displaying the informations about the selected node. */
  private GeneralMonitoringRightPanel entryPane;

  /** Default constructor. */
  public BrowseGeneralMonitoringPanel()
  {
    super();
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
      ((GenericDialog)w).getRootPane().setDefaultButton(null);
    }
    else if (w instanceof GenericFrame)
    {
      ((GenericFrame)w).getRootPane().setDefaultButton(null);
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
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

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_GENERAL_MONITORING_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return treePane;
  }

  @Override
  public void okClicked()
  {
    // No ok button
  }

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
      @Override
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
    repopulateTree(tree);
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
    ServerDescriptor server = ev.getNewDescriptor();
    if (serverChanged(server))
    {
      final boolean firstTimeCalled = lastServer == null;
      lastServer = server;

      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          String serverName = getServerName(lastServer);
          // Repopulate the tree to display a root node with server information
          if (!serverName.equals(lastServerName))
          {
            repopulateTree(treePane.getTree());
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
    LocalizableMessage errorTitle = LocalizableMessage.EMPTY;
    LocalizableMessage errorDetails = LocalizableMessage.EMPTY;
    ServerDescriptor.ServerStatus status = server.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!server.isAuthenticated())
      {
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        mb.append(INFO_CTRL_PANEL_AUTH_REQUIRED_TO_BROWSE_MONITORING_SUMMARY.get());
        mb.append("<br><br>").append(getAuthenticateHTML());
        errorDetails = mb.toMessage();
        errorTitle = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();

        displayErrorPane = true;
      }
    }
    else if (status == ServerDescriptor.ServerStatus.NOT_CONNECTED_TO_REMOTE)
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(server.getHostname()));
      mb.append("<br><br>").append(getAuthenticateHTML());
      errorDetails = mb.toMessage();
      errorTitle = INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_SUMMARY.get();
      displayErrorPane = true;
    }
    else
    {
      errorTitle = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(INFO_CTRL_PANEL_SERVER_MUST_RUN_TO_BROWSE_MONITORING_SUMMARY.get());
      mb.append("<br><br>");
      mb.append(getStartServerHTML());
      errorDetails = mb.toMessage();
      displayErrorPane = true;
    }
    final boolean fDisplayErrorPane = displayErrorPane;
    final LocalizableMessage fErrorTitle = errorTitle;
    final LocalizableMessage fErrorDetails = errorDetails;
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
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
   */
  private void repopulateTree(JTree tree)
  {
    ignoreSelectionEvents = true;

    ViewPositions pos = Utilities.getViewPositions(treeScroll);

    GeneralMonitoringTreeNode root = new GeneralMonitoringTreeNode(getServerName(), NodeType.ROOT, true);

    LocalizableMessage[] messages = {
          INFO_CTRL_PANEL_SYSTEM_INFORMATION.get(),
          INFO_CTRL_PANEL_JAVA_INFORMATION.get(),
          INFO_CTRL_PANEL_WORK_QUEUE.get(),
          INFO_CTRL_PANEL_ENTRY_CACHES.get(),
          INFO_CTRL_PANEL_JE_DB_INFO.get(),
          INFO_CTRL_PANEL_PDB_DB_INFO.get()
    };
    NodeType[] identifiers = {
          NodeType.SYSTEM_INFORMATION,
          NodeType.JAVA_INFORMATION,
          NodeType.WORK_QUEUE,
          NodeType.ENTRY_CACHES,
          NodeType.JE_DATABASES_INFORMATION,
          NodeType.PDB_DATABASES_INFORMATION
    };
    for (int i=0; i < messages.length; i++)
    {
      if (isVisible(identifiers[i]))
      {
        root.add(new GeneralMonitoringTreeNode(messages[i].toString(), identifiers[i], false));
      }
    }

    tree.setModel(new DefaultTreeModel(root));

    Utilities.updateViewPositions(pos);
    ignoreSelectionEvents = false;
  }

  private String getServerName() {
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
      if (server != null)
      {
        return getServerName(server);
      }
    }
    return INFO_CTRL_PANEL_GENERAL_MONITORING_ROOT.get().toString();
}

  /** Updates the right entry panel. */
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
      if (paths != null && paths.length == 1)
      {
        path = paths[0];
      }
      if (path != null)
      {
        GeneralMonitoringTreeNode node =
          (GeneralMonitoringTreeNode)path.getLastPathComponent();
        entryPane.update((NodeType) node.getIdentifier());
      }
      else if (paths != null && paths.length > 1)
      {
        entryPane.displayMessage(MULTIPLE_ITEMS_SELECTED);
      }
      else
      {
        entryPane.displayMessage(NO_ELEMENT_SELECTED);
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
    if (lastServer == null)
    {
      return true;
    }

    // Just compare the elements interesting for this panel
    return !Objects.equals(desc.getBackends(), lastServer.getBackends())
        && !Objects.equals(lastServer.getEntryCachesMonitor(), desc.getEntryCachesMonitor())
        && !Objects.equals(lastServer.getJvmMemoryUsageMonitor(), desc.getJvmMemoryUsageMonitor())
        && !Objects.equals(lastServer.getRootMonitor(), desc.getRootMonitor())
        && !Objects.equals(lastServer.getSystemInformationMonitor(), desc.getSystemInformationMonitor())
        && !Objects.equals(lastServer.getWorkQueueMonitor(), desc.getWorkQueueMonitor());
  }

  private final Map<Object, ImageIcon> hmImages = new HashMap<>();
  {
    NodeType[] identifiers = {
        NodeType.ROOT,
        NodeType.SYSTEM_INFORMATION,
        NodeType.JAVA_INFORMATION,
        NodeType.WORK_QUEUE,
        NodeType.ENTRY_CACHES,
        NodeType.JE_DATABASES_INFORMATION,
        NodeType.PDB_DATABASES_INFORMATION
    };
    LocalizableMessage[] ocPaths = {
        INFO_CTRL_PANEL_GENERAL_MONITORING_ROOT_TREE_NODE.get(),
        INFO_CTRL_PANEL_SYSTEM_INFORMATION_TREE_NODE.get(),
        INFO_CTRL_PANEL_JVM_MEMORY_USAGE_TREE_NODE.get(),
        INFO_CTRL_PANEL_WORK_QUEUE_TREE_NODE.get(),
        INFO_CTRL_PANEL_ENTRY_CACHES_TREE_NODE.get(),
        INFO_CTRL_PANEL_DB_ENVIRONMENT_TREE_NODE.get(),
        INFO_CTRL_PANEL_DB_ENVIRONMENT_TREE_NODE.get()
    };
    for (int i=0; i<identifiers.length; i++)
    {
      hmImages.put(identifiers[i], createImageIcon(ocPaths[i]));
    }
  }

  private ImageIcon createImageIcon(LocalizableMessage msg)
  {
    return Utilities.createImageIcon(IconPool.IMAGE_PATH + "/" + msg, getClass().getClassLoader());
  }

  private String getServerName(ServerDescriptor server)
  {
    if (server.getAdminConnector() != null)
    {
      return server.getHostname() + ":" + server.getAdminConnector().getPort();
    }
    return server.getHostname();
  }

  /** Specific class used to render the nodes in the tree. It uses specific icons for the nodes. */
  private class GeneralMonitoringTreeCellRenderer extends TreeCellRenderer
  {
    private static final long serialVersionUID = -3390566664259441766L;

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
      if (!(value instanceof GeneralMonitoringTreeNode))
      {
        throw new RuntimeException("Unexpected tree node: "+value);
      }
      return hmImages.get(((GeneralMonitoringTreeNode) value).getIdentifier());
    }
  }

  private boolean isVisible(NodeType nodetype)
  {
    return !isOEMVersion() || nodetype != NodeType.JE_DATABASES_INFORMATION;
  }
}
