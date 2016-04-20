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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import java.awt.Font;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.ManageReferralControl;
import javax.naming.ldap.SortControl;
import javax.naming.ldap.SortKey;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BrowserEvent;
import org.opends.guitools.controlpanel.event.BrowserEventListener;
import org.opends.guitools.controlpanel.event.ReferralAuthenticationListener;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.ui.nodes.RootNode;
import org.opends.guitools.controlpanel.ui.nodes.SuffixNode;
import org.opends.guitools.controlpanel.ui.renderer.BrowserCellRenderer;
import org.opends.guitools.controlpanel.util.NumSubordinateHacker;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.HostPort;
import org.opends.server.types.LDAPURL;

import static org.opends.admin.ads.util.ConnectionUtils.isSSL;
import static org.opends.server.util.ServerConstants.*;

/**
 * This is the main class of the LDAP entry browser.  It is in charge of
 * updating a tree that is passed as parameter.  Every instance of
 * BrowserController is associated with a unique JTree.
 * The different visualization options are passed to BrowserController using
 * some setter and getter methods (the user can specify for instance whether
 * the entries must be sorted or not).
 */
public class BrowserController
implements TreeExpansionListener, ReferralAuthenticationListener
{
  /** The mask used to display the number of ACIs or not. */
  private static final int DISPLAY_ACI_COUNT = 0x01;

  /** The list of attributes that are used to sort the entries (if the sorting option is used). */
  private static final String[] SORT_ATTRIBUTES =
      { "cn", "givenname", "o", "ou", "sn", "uid" };

  /**
   * This is a key value.  It is used to specify that the attribute that should
   * be used to display the entry is the RDN attribute.
   */
  private static final String RDN_ATTRIBUTE = "rdn attribute";

  /** The filter used to retrieve all the entries. */
  public static final String ALL_OBJECTS_FILTER =
    "(|(objectClass=*)(objectClass=ldapsubentry))";

  private static final String NUMSUBORDINATES_ATTR = "numsubordinates";
  private static final String HASSUBORDINATES_ATTR = "hassubordinates";
  private static final String ACI_ATTR = "aci";

  private final JTree tree;
  private final DefaultTreeModel treeModel;
  private final RootNode rootNode;
  private int displayFlags;
  private String displayAttribute;
  private final boolean showAttributeName;
  private ConnectionWrapper connConfig;
  private InitialLdapContext ctxConfiguration;
  private InitialLdapContext ctxUserData;
  private boolean followReferrals;
  private boolean sorted;
  private boolean showContainerOnly;
  private boolean automaticExpand;
  private boolean automaticallyExpandedNode;
  private String[] containerClasses;
  private NumSubordinateHacker numSubordinateHacker;
  private int queueTotalSize;
  private int maxChildren;
  private final Collection<BrowserEventListener> listeners = new ArrayList<>();
  private final LDAPConnectionPool connectionPool;
  private final IconPool iconPool;

  private final NodeSearcherQueue refreshQueue;

  private String filter;

  private static final Logger LOG =
    Logger.getLogger(BrowserController.class.getName());

  /**
   * Constructor of the BrowserController.
   * @param tree the tree that must be updated.
   * @param cpool the connection pool object that will provide the connections
   * to be used.
   * @param ipool the icon pool to be used to retrieve the icons that will be
   * used to render the nodes in the tree.
   */
  public BrowserController(JTree tree, LDAPConnectionPool cpool,
      IconPool ipool)
  {
    this.tree = tree;
    iconPool = ipool;
    rootNode = new RootNode();
    rootNode.setIcon(iconPool.getIconForRootNode());
    treeModel = new DefaultTreeModel(rootNode);
    tree.setModel(treeModel);
    tree.addTreeExpansionListener(this);
    tree.setCellRenderer(new BrowserCellRenderer());
    displayFlags = DISPLAY_ACI_COUNT;
    showAttributeName = false;
    displayAttribute = RDN_ATTRIBUTE;
    followReferrals = false;
    sorted = false;
    showContainerOnly = true;
    containerClasses = new String[0];
    queueTotalSize = 0;
    connectionPool = cpool;
    connectionPool.addReferralAuthenticationListener(this);

    refreshQueue = new NodeSearcherQueue("New red", 2);

    // NUMSUBORDINATE HACK
    // Create an empty hacker to avoid null value test.
    // However this value will be overridden by full hacker.
    numSubordinateHacker = new NumSubordinateHacker();
  }


  /**
   * Set the connection for accessing the directory.  Since we must use
   * different controls when searching the configuration and the user data,
   * two connections must be provided (this is done to avoid synchronization
   * issues).  We also pass the server descriptor corresponding to the
   * connections to have a proper rendering of the root node.
   * @param server the server descriptor.
   * @param ctxConfiguration the connection to be used to retrieve the data in
   * the configuration base DNs.
   * @param ctxUserData the connection to be used to retrieve the data in the
   * user base DNs.
   * @throws NamingException if an error occurs.
   */
  public void setConnections(
      ServerDescriptor server,
      ConnectionWrapper ctxConfiguration,
      InitialLdapContext ctxUserData) throws NamingException {
    String rootNodeName;
    if (ctxConfiguration != null)
    {
      this.connConfig = ctxConfiguration;
      this.ctxConfiguration = connConfig.getLdapContext();
      this.ctxUserData = ctxUserData;

      this.ctxConfiguration.setRequestControls(getConfigurationRequestControls());
      this.ctxUserData.setRequestControls(getRequestControls());
      rootNodeName = new HostPort(server.getHostname(), connConfig.getHostPort().getPort()).toString();
    }
    else {
      rootNodeName = "";
    }
    rootNode.setDisplayName(rootNodeName);
    startRefresh(null);
  }


  /**
   * Return the connection for accessing the directory configuration.
   * @return the connection for accessing the directory configuration.
   */
  public InitialLdapContext getConfigurationConnection() {
    return ctxConfiguration;
  }

  /**
   * Return the connection for accessing the directory user data.
   * @return the connection for accessing the directory user data.
   */
  public InitialLdapContext getUserDataConnection() {
    return ctxUserData;
  }


  /**
   * Return the JTree controlled by this controller.
   * @return the JTree controlled by this controller.
   */
  public JTree getTree() {
    return tree;
  }


  /**
   * Return the connection pool used by this controller.
   * If a client class adds authentication to the connection
   * pool, it must inform the controller by calling notifyAuthDataChanged().
   * @return the connection pool used by this controller.
   */
  public LDAPConnectionPool getConnectionPool() {
    return  connectionPool;
  }

  /**
   * Return the icon pool used by this controller.
   * @return the icon pool used by this controller.
   */
  public IconPool getIconPool() {
    return  iconPool;
  }

  /**
   * Tells whether the given suffix is in the tree or not.
   * @param suffixDn the DN of the suffix to be analyzed.
   * @return <CODE>true</CODE> if the provided String is the DN of a suffix
   * and <CODE>false</CODE> otherwise.
   * @throws IllegalArgumentException if a node with the given dn exists but
   * is not a suffix node.
   */
  public boolean hasSuffix(String suffixDn) throws IllegalArgumentException
  {
    return findSuffixNode(suffixDn, rootNode) != null;
  }

  /**
   * Add an LDAP suffix to this controller.
   * A new node is added in the JTree and a refresh is started.
   * @param suffixDn the DN of the suffix.
   * @param parentSuffixDn the DN of the parent suffix (or <CODE>null</CODE> if
   * there is no parent DN).
   * @return the TreePath of the new node.
   * @throws IllegalArgumentException if a node with the given dn exists.
   */
  public TreePath addSuffix(String suffixDn, String parentSuffixDn)
  throws IllegalArgumentException
  {
    SuffixNode parentNode;
    if (parentSuffixDn != null) {
      parentNode = findSuffixNode(parentSuffixDn, rootNode);
      if (parentNode == null) {
        throw new IllegalArgumentException("Invalid suffix dn " +
            parentSuffixDn);
      }
    }
    else {
      parentNode = rootNode;
    }
    int index = findChildNode(parentNode, suffixDn);
    if (index >= 0) { // A node has alreay this dn -> bug
      throw new IllegalArgumentException("Duplicate suffix dn " + suffixDn);
    }
    index = -(index + 1);
    SuffixNode newNode = new SuffixNode(suffixDn);
    treeModel.insertNodeInto(newNode, parentNode, index);
    startRefreshNode(newNode, null, true);

    return new TreePath(treeModel.getPathToRoot(newNode));
  }

  /**
   * Add an LDAP suffix to this controller.
   * A new node is added in the JTree and a refresh is started.
   * @param nodeDn the DN of the node to be added.
   * @return the TreePath of the new node.
   */
  public TreePath addNodeUnderRoot(String nodeDn) {
    SuffixNode parentNode = rootNode;
    int index = findChildNode(parentNode, nodeDn);
    if (index >= 0) { // A node has already this dn -> bug
      throw new IllegalArgumentException("Duplicate node dn " + nodeDn);
    }
    index = -(index + 1);
    BasicNode newNode = new BasicNode(nodeDn);
    treeModel.insertNodeInto(newNode, parentNode, index);
    startRefreshNode(newNode, null, true);

    return new TreePath(treeModel.getPathToRoot(newNode));
  }


  /**
   * Remove all the suffixes.
   * The controller removes all the nodes from the JTree except the root.
   * @return the TreePath of the root node.
   */
  public TreePath removeAllUnderRoot() {
    stopRefresh();
    removeAllChildNodes(rootNode, false /* Delete suffixes */);
    return new TreePath(treeModel.getPathToRoot(rootNode));
  }


  /**
   * Return the display flags.
   * @return the display flags.
   */
  public int getDisplayFlags() {
    return displayFlags;
  }


  /**
   * Set the display flags and call startRefresh().
   * @param flags the display flags to be set.
   */
  public void setDisplayFlags(int flags) {
    displayFlags = flags;
    startRefresh(null);
  }

  /**
   * Set the display attribute (the attribute that will be used to retrieve
   * the string that will appear in the tree when rendering the node).
   * This routine collapses the JTree and invokes startRefresh().
   * @param displayAttribute the display attribute to be used.
   */
  public void setDisplayAttribute(String displayAttribute) {
    this.displayAttribute = displayAttribute;
    stopRefresh();
    removeAllChildNodes(rootNode, true /* Keep suffixes */);
    startRefresh(null);
  }

  /**
   * Returns the attribute used to display the entry.
   * RDN_ATTRIBUTE is the rdn is used.
   * @return the attribute used to display the entry.
   */
  public String getDisplayAttribute() {
    return displayAttribute;
  }

  /**
   * Says whether we are showing the attribute name or not.
   * @return <CODE>true</CODE> if we are showing the attribute name and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isAttributeNameShown() {
    return showAttributeName;
  }

  /**
   * Sets the maximum number of children to display for a node.
   * 0 if there is no limit
   * @param maxChildren the maximum number of children to display for a node.
   */
  public void setMaxChildren(int maxChildren) {
    this.maxChildren = maxChildren;
  }

  /**
   * Return the maximum number of children to display.
   * @return the maximum number of children to display.
   */
  public int getMaxChildren() {
    return maxChildren;
  }

  /**
   * Return true if this controller follows referrals.
   * @return <CODE>true</CODE> if this controller follows referrals and
   * <CODE>false</CODE> otherwise.
   */
  public boolean getFollowReferrals() {
    return followReferrals;
  }


  /**
   * Enable/display the following of referrals.
   * This routine starts a refresh on each referral node.
   * @param followReferrals whether to follow referrals or not.
   * @throws NamingException if there is an error updating the request controls
   * of the internal connections.
   */
  public void setFollowReferrals(boolean followReferrals) throws NamingException
  {
    this.followReferrals = followReferrals;
    stopRefresh();
    removeAllChildNodes(rootNode, true /* Keep suffixes */);
    ctxConfiguration.setRequestControls(getConfigurationRequestControls());
    ctxUserData.setRequestControls(getRequestControls());
    connectionPool.setRequestControls(getRequestControls());
    startRefresh(null);
  }


  /**
   * Return true if entries are displayed sorted.
   * @return <CODE>true</CODE> if entries are displayed sorted and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isSorted() {
    return sorted;
  }


  /**
   * Enable/disable entry sort.
   * This routine collapses the JTree and invokes startRefresh().
   * @param sorted whether to sort the entries or not.
   * @throws NamingException if there is an error updating the request controls
   * of the internal connections.
   */
  public void setSorted(boolean sorted) throws NamingException {
    stopRefresh();
    removeAllChildNodes(rootNode, true /* Keep suffixes */);
    this.sorted = sorted;
    ctxConfiguration.setRequestControls(getConfigurationRequestControls());
    ctxUserData.setRequestControls(getRequestControls());
    connectionPool.setRequestControls(getRequestControls());
    startRefresh(null);
  }


  /**
   * Return true if only container entries are displayed.
   * An entry is a container if:
   *    - it has some children
   *    - or its class is one of the container classes
   *      specified with setContainerClasses().
   * @return <CODE>true</CODE> if only container entries are displayed and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isShowContainerOnly() {
    return showContainerOnly;
  }


  /**
   * Enable or disable container display and call startRefresh().
   * @param showContainerOnly whether to display only containers or all the
   * entries.
   */
  public void setShowContainerOnly(boolean showContainerOnly) {
    this.showContainerOnly = showContainerOnly;
    startRefresh(null);
  }


  /**
   * Find the BrowserNodeInfo associated to a TreePath and returns
   * the describing IBrowserNodeInfo.
   * @param path the TreePath associated with the node we are searching.
   * @return the BrowserNodeInfo associated to the TreePath.
   */
  public BrowserNodeInfo getNodeInfoFromPath(TreePath path) {
    BasicNode node = (BasicNode)path.getLastPathComponent();
    return new BrowserNodeInfoImpl(node);
  }


  /**
   * Return the array of container classes for this controller.
   * Warning: the returned array is not cloned.
   * @return the array of container classes for this controller.
   */
  public String[] getContainerClasses() {
    return containerClasses;
  }


  /**
   * Set the list of container classes and calls startRefresh().
   * Warning: the array is not cloned.
   * @param containerClasses the lis of container classes.
   */
  public void setContainerClasses(String[] containerClasses) {
    this.containerClasses = containerClasses;
    startRefresh(null);
  }


  /**
   * NUMSUBORDINATE HACK
   * Make the hacker public so that RefreshTask can use it.
   * @return the NumSubordinateHacker object used by the controller.
   */
  public NumSubordinateHacker getNumSubordinateHacker() {
    return numSubordinateHacker;
  }


  /**
   * NUMSUBORDINATE HACK
   * Set the hacker. Note this method does not trigger any
   * refresh. The caller is supposed to do it afterward.
   * @param h the  NumSubordinateHacker.
   */
  public void setNumSubordinateHacker(NumSubordinateHacker h) {
    if (h == null) {
      throw new IllegalArgumentException("hacker cannot be null");
    }
    numSubordinateHacker = h;
  }

  /**
   * Add a BrowserEventListener to this controller.
   * @param l the listener to be added.
   */
  public void addBrowserEventListener(BrowserEventListener l) {
    listeners.add(l);
  }

  /**
   * Notify this controller that an entry has been added.
   * The controller adds a new node in the JTree and starts refreshing this new
   * node.
   * This routine returns the tree path about the new entry.
   * @param parentInfo the parent node of the entry added.
   * @param newEntryDn the dn of the entry to be added.
   * @return the tree path associated with the new entry.
   */
  public TreePath notifyEntryAdded(BrowserNodeInfo parentInfo,
      String newEntryDn) {
    BasicNode parentNode = parentInfo.getNode();
    BasicNode childNode = new BasicNode(newEntryDn);
    int childIndex;
    if (sorted) {
      childIndex = findChildNode(parentNode, newEntryDn);
      if (childIndex >= 0) {
        throw new IllegalArgumentException("Duplicate DN " + newEntryDn);
      }
      childIndex = -(childIndex + 1);
    }
    else {
      childIndex = parentNode.getChildCount();
    }
    parentNode.setLeaf(false);
    treeModel.insertNodeInto(childNode, parentNode, childIndex);
    startRefreshNode(childNode, null, false);
    return new TreePath(treeModel.getPathToRoot(childNode));
  }


  /**
   * Notify this controller that a entry has been deleted.
   * The controller removes the corresponding node from the JTree and returns
   * the TreePath of the parent node.
   * @param nodeInfo the node to be deleted.
   * @return the tree path associated with the parent of the deleted node.
   */
  public TreePath notifyEntryDeleted(BrowserNodeInfo nodeInfo) {
    BasicNode node = nodeInfo.getNode();
    if (node == rootNode) {
      throw new IllegalArgumentException("Root node cannot be removed");
    }

    /* If the parent is null... the node is no longer in the tree */
    final TreeNode parentNode = node.getParent();
    if (parentNode != null) {
      removeOneNode(node);
      return new TreePath(treeModel.getPathToRoot(parentNode));
    }
    return null;
  }


  /**
   * Notify this controller that an entry has changed.
   * The controller starts refreshing the corresponding node.
   * Child nodes are not refreshed.
   * @param nodeInfo the node that changed.
   */
  public void notifyEntryChanged(BrowserNodeInfo nodeInfo) {
    BasicNode node = nodeInfo.getNode();
    startRefreshNode(node, null, false);
  }

  /** Notify this controller that authentication data have changed in the connection pool. */
  @Override
  public void notifyAuthDataChanged() {
    notifyAuthDataChanged(null);
  }

  /**
   * Notify this controller that authentication data have changed in the
   * connection pool for the specified url.
   * The controller starts refreshing the node which represent entries from the
   * url.
   * @param url the URL of the connection that changed.
   */
  private void notifyAuthDataChanged(LDAPURL url) {
    // TODO: temporary implementation
    //    we should refresh only nodes :
    //    - whose URL matches 'url'
    //    - whose errorType == ERROR_SOLVING_REFERRAL and
    //      errorArg == url
    startRefreshReferralNodes(rootNode);
  }


  /**
   * Start a refresh from the specified node.
   * If some refresh are on-going on descendant nodes, they are stopped.
   * If nodeInfo is null, refresh is started from the root.
   * @param nodeInfo the node to be refreshed.
   */
  public void startRefresh(BrowserNodeInfo nodeInfo) {
    BasicNode node;
    if (nodeInfo == null) {
      node = rootNode;
    }
    else {
      node = nodeInfo.getNode();
    }
    stopRefreshNode(node);
    startRefreshNode(node, null, true);
  }

  /** Stop the current refreshing. Nodes being expanded are collapsed. */
  private void stopRefresh() {
    stopRefreshNode(rootNode);
    // TODO: refresh must be stopped in a clean state.
  }

  /**
   * Start refreshing the whole tree from the specified node.
   * We queue a refresh which:
   *    - updates the base node
   *    - is recursive
   * @param node the parent node that will be refreshed.
   * @param localEntry the local entry corresponding to the node.
   * @param recursive whether the refresh must be executed recursively or not.
   */
  private void startRefreshNode(BasicNode node, SearchResult localEntry,
      boolean recursive) {
    if (node == rootNode) {
      // For the root node, readBaseEntry is meaningless.
      if (recursive) {
        // The root cannot be queued directly.
        // We need to queue each child individually.
        Enumeration<?> e = rootNode.children();
        while (e.hasMoreElements()) {
          BasicNode child = (BasicNode)e.nextElement();
          startRefreshNode(child, null, true);
        }
      }
    }
    else {
      refreshQueue.queue(new NodeRefresher(node, this, localEntry, recursive));
      // The task does not *see* suffixes.
      // So we need to propagate the refresh on
      // the sub-suffixes if any.
      if (recursive && node instanceof SuffixNode) {
        Enumeration<?> e = node.children();
        while (e.hasMoreElements()) {
          BasicNode child = (BasicNode)e.nextElement();
          if (child instanceof SuffixNode) {
            startRefreshNode(child, null, true);
          }
        }
      }
    }
  }




  /**
   * Stop refreshing below this node.
   * TODO: this method is very costly when applied to something else than the
   * root node.
   * @param node the node where the refresh must stop.
   */
  private void stopRefreshNode(BasicNode node) {
    if (node == rootNode) {
      refreshQueue.cancelAll();
    }
    else {
      Enumeration<?> e = node.children();
      while (e.hasMoreElements()) {
        BasicNode child = (BasicNode)e.nextElement();
        stopRefreshNode(child);
      }
      refreshQueue.cancelForNode(node);
    }
  }



  /**
   * Call startRefreshNode() on each referral node accessible from parentNode.
   * @param parentNode the parent node.
   */
  private void startRefreshReferralNodes(BasicNode parentNode) {
    Enumeration<?> e = parentNode.children();
    while (e.hasMoreElements()) {
      BasicNode child = (BasicNode)e.nextElement();
      if (child.getReferral() != null || child.getRemoteUrl() != null) {
        startRefreshNode(child, null, true);
      }
      else {
        startRefreshReferralNodes(child);
      }
    }
  }



  /**
   * Remove all the children below parentNode *without changing the leaf state*.
   * If specified, it keeps the SuffixNode and recurses on them. Inform the tree
   * model.
   * @param parentNode the parent node.
   * @param keepSuffixes whether the suffixes should be kept or not.
   */
  private void removeAllChildNodes(BasicNode parentNode, boolean keepSuffixes) {
    for (int i = parentNode.getChildCount() - 1; i >= 0; i--) {
      BasicNode child = (BasicNode)parentNode.getChildAt(i);
      if (child instanceof SuffixNode && keepSuffixes) {
        removeAllChildNodes(child, true);
        child.setRefreshNeededOnExpansion(true);
      }
      else {
        child.removeFromParent();
      }
    }
    treeModel.nodeStructureChanged(parentNode);
  }

  /**
   * For BrowserController private use.  When a node is expanded, refresh it
   * if it needs it (to search the children for instance).
   * @param event the tree expansion event.
   */
  @Override
  public void treeExpanded(TreeExpansionEvent event) {
    if (!automaticallyExpandedNode)
    {
      automaticExpand = false;
    }
    BasicNode basicNode = (BasicNode)event.getPath().getLastPathComponent();
    if (basicNode.isRefreshNeededOnExpansion()) {
      basicNode.setRefreshNeededOnExpansion(false);
      // Starts a recursive refresh which does not read the base entry
      startRefreshNode(basicNode, null, true);
    }
  }


  /**
   * For BrowserController private use.  When a node is collapsed the refresh
   * tasks on it are canceled.
   * @param event the tree collapse event.
   */
  @Override
  public void treeCollapsed(TreeExpansionEvent event) {
    Object node = event.getPath().getLastPathComponent();
    if (!(node instanceof RootNode)) {
      BasicNode basicNode = (BasicNode)node;
      stopRefreshNode(basicNode);
      synchronized (refreshQueue)
      {
        boolean isWorking = refreshQueue.isWorking(basicNode);
        refreshQueue.cancelForNode(basicNode);
        if (isWorking)
        {
          basicNode.setRefreshNeededOnExpansion(true);
        }
      }
    }
  }

  /**
   * Sets which is the inspected node.  This method simply marks the selected
   * node in the tree so that it can have a different rendering.  This is
   * useful for instance when the right panel has a list of entries to which
   * the menu action apply, to make a difference between the selected node in
   * the tree (to which the action in the main menu will not apply) and the
   * selected nodes in the right pane.
   * @param node the selected node.
   */
  public void setInspectedNode(BrowserNodeInfo node) {
    BrowserCellRenderer renderer = (BrowserCellRenderer)tree.getCellRenderer();
    if (node == null) {
      renderer.setInspectedNode(null);
    } else {
      renderer.setInspectedNode(node.getNode());
    }
  }


  /**
   * Routines for the task classes
   * =============================
   *
   * Note that these routines only read controller variables.
   * They do not alter any variable: so they can be safely
   * called by task threads without synchronize clauses.
   */


  /**
   * The tree model created by the controller and assigned
   * to the JTree.
   * @return the tree model.
   */
  public DefaultTreeModel getTreeModel() {
    return treeModel;
  }

  /**
   * Sets the filter that must be used by the browser controller to retrieve
   * entries.
   * @param filter the LDAP filter.
   */
  public void setFilter(String filter)
  {
    this.filter = filter;
  }

  /**
   * Returns the filter that is being used to search the entries.
   * @return the filter that is being used to search the entries.
   */
  public String getFilter()
  {
    return filter;
  }

  /**
   * Returns the filter used to make a object base search.
   * @return the filter used to make a object base search.
   */
  String getObjectSearchFilter()
  {
    return ALL_OBJECTS_FILTER;
  }


  /**
   * Return the LDAP search filter to use for searching child entries.
   * If showContainerOnly is true, the filter will select only the
   * container entries. If not, the filter will select all the children.
   * @return the LDAP search filter to use for searching child entries.
   */
  String getChildSearchFilter() {
    String result;
    if (showContainerOnly) {
      if (followReferrals) {
        /* In the case we are following referrals, we have to consider referrals
         as nodes.
         Suppose the following scenario: a referral points to a remote entry
         that has children (node), BUT the referral entry in the local server
         has no children.  It won't be included in the filter and it won't
         appear in the tree.  But what we are displaying is the remote entry,
         the result is that we have a NODE that does not appear in the tree and
         so the user cannot browse it.

         This has some side effects:
         If we cannot follow the referral, a leaf will appear on the tree (as it
         if were a node).
         If the referral points to a leaf entry, a leaf will appear on the tree
         (as if it were a node).

         This is minor compared to the impossibility of browsing a subtree with
         the NODE/LEAF layout.
         */
        result = "(|(&(hasSubordinates=true)"+filter+")(objectClass=referral)";
      } else {
        result = "(|(&(hasSubordinates=true)"+filter+")";
      }
      for (String containerClass : containerClasses)
      {
        result += "(objectClass=" + containerClass + ")";
      }
      result += ")";
    }
    else {
      result = filter;
    }

    return result;
  }




  /**
   * Return the LDAP connection to reading the base entry of a node.
   * @param node the node for which we want the LDAP connection.
   * @throws NamingException if there is an error retrieving the connection.
   * @return the LDAP connection to reading the base entry of a node.
   */
  InitialLdapContext findConnectionForLocalEntry(BasicNode node)
  throws NamingException {
    return findConnectionForLocalEntry(node, isConfigurationNode(node));
  }

  /**
   * Return the LDAP connection to reading the base entry of a node.
   * @param node the node for which we want toe LDAP connection.
   * @param isConfigurationNode whether the node is a configuration node or not.
   * @throws NamingException if there is an error retrieving the connection.
   * @return the LDAP connection to reading the base entry of a node.
   */
  private InitialLdapContext findConnectionForLocalEntry(BasicNode node,
      boolean isConfigurationNode) throws NamingException
  {
    if (node == rootNode) {
      return ctxConfiguration;
    }

    final BasicNode parent = (BasicNode) node.getParent();
    if (parent != null && parent != rootNode)
    {
      return findConnectionForDisplayedEntry(parent, isConfigurationNode);
    }
    return isConfigurationNode ? ctxConfiguration : ctxUserData;
  }

  /**
   * Returns whether a given node is a configuration node or not.
   * @param node the node to analyze.
   * @return <CODE>true</CODE> if the node is a configuration node and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isConfigurationNode(BasicNode node)
  {
    if (node instanceof RootNode)
    {
      return true;
    }
    if (node instanceof SuffixNode)
    {
      String dn = node.getDN();
      return Utilities.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_DEFAULT_SCHEMA_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_TASK_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_CONFIG_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_MONITOR_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_TRUST_STORE_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_BACKUP_ROOT) ||
          Utilities.areDnsEqual(dn, DN_EXTERNAL_CHANGELOG_ROOT);
    }
    else
    {
      BasicNode parentNode = (BasicNode)node.getParent();
      return isConfigurationNode(parentNode);
    }
  }

  /**
   * Return the LDAP connection to search the displayed entry (which can be the
   * local or remote entry).
   * @param node the node for which we want toe LDAP connection.
   * @return the LDAP connection to search the displayed entry.
   * @throws NamingException if there is an error retrieving the connection.
   */
  public InitialLdapContext findConnectionForDisplayedEntry(BasicNode node)
  throws NamingException {
    return findConnectionForDisplayedEntry(node, isConfigurationNode(node));
  }


  /**
   * Return the LDAP connection to search the displayed entry (which can be the
   * local or remote entry).
   * @param node the node for which we want toe LDAP connection.
   * @param isConfigurationNode whether the node is a configuration node or not.
   * @return the LDAP connection to search the displayed entry.
   * @throws NamingException if there is an error retrieving the connection.
   */
  private InitialLdapContext findConnectionForDisplayedEntry(BasicNode node,
      boolean isConfigurationNode) throws NamingException {
    if (followReferrals && node.getRemoteUrl() != null)
    {
      return connectionPool.getConnection(node.getRemoteUrl());
    }
    return findConnectionForLocalEntry(node, isConfigurationNode);
  }



  /**
   * Release a connection returned by selectConnectionForChildEntries() or
   * selectConnectionForBaseEntry().
   * @param ctx the connection to be released.
   */
  void releaseLDAPConnection(InitialLdapContext ctx) {
    if (ctx != this.ctxConfiguration && ctx != this.ctxUserData)
    {
      // Thus it comes from the connection pool
      connectionPool.releaseConnection(ctx);
    }
  }


  /**
   * Returns the local entry URL for a given node.
   * @param node the node.
   * @return the local entry URL for a given node.
   */
  LDAPURL findUrlForLocalEntry(BasicNode node) {
    if (node == rootNode) {
      return LDAPConnectionPool.makeLDAPUrl(connConfig.getHostPort(), "", isSSL(ctxConfiguration));
    }
    final BasicNode parent = (BasicNode) node.getParent();
    if (parent != null)
    {
      final LDAPURL parentUrl = findUrlForDisplayedEntry(parent);
      return LDAPConnectionPool.makeLDAPUrl(parentUrl, node.getDN());
    }
    return LDAPConnectionPool.makeLDAPUrl(connConfig.getHostPort(), node.getDN(), isSSL(ctxConfiguration));
  }


  /**
   * Returns the displayed entry URL for a given node.
   * @param node the node.
   * @return the displayed entry URL for a given node.
   */
  private LDAPURL findUrlForDisplayedEntry(BasicNode node)
  {
    if (followReferrals && node.getRemoteUrl() != null) {
      return node.getRemoteUrl();
    }
    return findUrlForLocalEntry(node);
  }


  /**
   * Returns the DN to use for searching children of a given node.
   * In most cases, it's node.getDN(). However if node has referral data
   * and _followReferrals is true, the result is calculated from the
   * referral resolution.
   *
   * @param node the node.
   * @return the DN to use for searching children of a given node.
   */
  String findBaseDNForChildEntries(BasicNode node) {
    if (followReferrals && node.getRemoteUrl() != null) {
      return node.getRemoteUrl().getRawBaseDN();
    }
    return node.getDN();
  }



  /**
   * Tells whether a node is displaying a remote entry.
   * @param node the node.
   * @return <CODE>true</CODE> if the node displays a remote entry and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isDisplayedEntryRemote(BasicNode node) {
    if (followReferrals) {
      if (node == rootNode) {
        return false;
      }
      if (node.getRemoteUrl() != null) {
        return true;
      }
      final BasicNode parent = (BasicNode)node.getParent();
      if (parent != null) {
        return isDisplayedEntryRemote(parent);
      }
    }
    return false;
  }


  /**
   * Returns the list of attributes for the red search.
   * @return the list of attributes for the red search.
   */
  String[] getAttrsForRedSearch() {
    ArrayList<String> v = new ArrayList<>();

    v.add(OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    v.add(NUMSUBORDINATES_ATTR);
    v.add(HASSUBORDINATES_ATTR);
    v.add(ATTR_REFERRAL_URL);
    if ((displayFlags & DISPLAY_ACI_COUNT) != 0) {
      v.add(ACI_ATTR);
    }
    if (!RDN_ATTRIBUTE.equals(displayAttribute)) {
      v.add(displayAttribute);
    }

    return v.toArray(new String[v.size()]);
  }

  /**
   * Returns the list of attributes for the black search.
   * @return the list of attributes for the black search.
   */
  String[] getAttrsForBlackSearch() {
    if (!RDN_ATTRIBUTE.equals(displayAttribute)) {
      return new String[] {
          OBJECTCLASS_ATTRIBUTE_TYPE_NAME,
          NUMSUBORDINATES_ATTR,
          HASSUBORDINATES_ATTR,
          ATTR_REFERRAL_URL,
          ACI_ATTR,
          displayAttribute};
    } else {
      return new String[] {
          OBJECTCLASS_ATTRIBUTE_TYPE_NAME,
          NUMSUBORDINATES_ATTR,
          HASSUBORDINATES_ATTR,
          ATTR_REFERRAL_URL,
          ACI_ATTR
      };
    }
  }

  /**
   * Returns the basic search controls.
   * @return the basic search controls.
   */
  SearchControls getBasicSearchControls() {
    SearchControls searchControls = new SearchControls();
    searchControls.setCountLimit(maxChildren);
    return searchControls;
  }

  /**
   * Returns the request controls to search user data.
   * @return the request controls to search user data.
   */
  private Control[] getRequestControls()
  {
    Control ctls[];
    if (followReferrals)
    {
      ctls = new Control[sorted ? 2 : 1];
    }
    else
    {
      ctls = new Control[sorted ? 1 : 0];
    }
    if (sorted)
    {
      SortKey[] keys = new SortKey[SORT_ATTRIBUTES.length];
      for (int i=0; i<keys.length; i++) {
        keys[i] = new SortKey(SORT_ATTRIBUTES[i]);
      }
      try
      {
        ctls[0] = new SortControl(keys, false);
      }
      catch (IOException ioe)
      {
        // Bug
        throw new RuntimeException("Unexpected encoding exception: "+ioe,
            ioe);
      }
    }
    if (followReferrals)
    {
      ctls[ctls.length - 1] = new ManageReferralControl(false);
    }
    return ctls;
  }

  /**
   * Returns the request controls to search configuration data.
   * @return the request controls to search configuration data.
   */
  private Control[] getConfigurationRequestControls()
  {
    return getRequestControls();
  }


  /**
   * Callbacks invoked by task classes
   * =================================
   *
   * The routines below are invoked by the task classes; they
   * update the nodes and the tree model.
   *
   * To ensure the consistency of the tree model, these routines
   * are not invoked directly by the task classes: they are
   * invoked using SwingUtilities.invokeAndWait() (each of the
   * methods XXX() below has a matching wrapper invokeXXX()).
   */

  /**
   * Invoked when the refresh task has finished the red operation.
   * It has read the attributes of the base entry ; the result of the
   * operation is:
   *    - an LDAPEntry if successful
   *    - an Exception if failed
   * @param task the task that progressed.
   * @param oldState the previous state of the task.
   * @param newState the new state of the task.
   * @throws NamingException if there is an error reading entries.
   */
  private void refreshTaskDidProgress(NodeRefresher task,
      NodeRefresher.State oldState,
      NodeRefresher.State newState) throws NamingException {
    BasicNode node = task.getNode();
    boolean nodeChanged = false;

    //task.dump();

    // Manage events
    if (oldState == NodeRefresher.State.QUEUED) {
      checkUpdateEvent(true);
    }
    if (task.isInFinalState()) {
      checkUpdateEvent(false);
    }

    if (newState == NodeRefresher.State.FAILED) {
      // In case of NameNotFoundException, we simply remove the node from the
      // tree.
      // Except when it's due a to referral resolution: we keep the node
      // in order the user can fix the referral.
      if (isNameNotFoundException(task.getException())
          && oldState != NodeRefresher.State.SOLVING_REFERRAL) {
        removeOneNode(node);
      }
      else {
        if (oldState == NodeRefresher.State.SOLVING_REFERRAL)
        {
          node.setRemoteUrl(task.getRemoteUrl());
          if (task.getRemoteEntry() != null)
          {
            /* This is the case when there are multiple hops in the referral
           and so we have a remote referral entry but not the entry that it
           points to */
            updateNodeRendering(node, task.getRemoteEntry());
          }
          /* It is a referral and we try to follow referrals.
         We remove its children (that are supposed to be
         entries on the remote server).
         If this referral entry has children locally (even if this goes
         against the recommendation of the standards) these children will
         NOT be displayed. */

          node.setLeaf(true);
          removeAllChildNodes(node, true /* Keep suffixes */);
        }
        node.setError(new BasicNodeError(oldState, task.getException(),
            task.getExceptionArg()));
        nodeChanged = updateNodeRendering(node, task.getDisplayedEntry());
      }
    }
    else if (newState == NodeRefresher.State.CANCELLED ||
        newState == NodeRefresher.State.INTERRUPTED) {

      // Let's collapse task.getNode()
      tree.collapsePath(new TreePath(treeModel.getPathToRoot(node)));

      // TODO: should we reflect this situation visually ?
    }
    else {

      if (oldState != NodeRefresher.State.SEARCHING_CHILDREN
          && newState == NodeRefresher.State.SEARCHING_CHILDREN) {
        // The children search is going to start
        if (canDoDifferentialUpdate(task)) {
          Enumeration<?> e = node.children();
          while (e.hasMoreElements()) {
            BasicNode child = (BasicNode)e.nextElement();
            child.setObsolete(true);
          }
        }
        else {
          removeAllChildNodes(node, true /* Keep suffixes */);
        }
      }

      if (oldState == NodeRefresher.State.READING_LOCAL_ENTRY) {
        /* The task is going to try to solve the referral if there's one.
         If succeeds we will update the remote url.  Set it to null for
         the case when there was a referral and it has been deleted */
        node.setRemoteUrl((String)null);
        SearchResult localEntry = task.getLocalEntry();
        nodeChanged = updateNodeRendering(node, localEntry);
      }
      else if (oldState == NodeRefresher.State.SOLVING_REFERRAL) {
        node.setRemoteUrl(task.getRemoteUrl());
        updateNodeRendering(node, task.getRemoteEntry());
        nodeChanged = true;
      }
      else if (oldState == NodeRefresher.State.DETECTING_CHILDREN) {
        if (node.isLeaf() != task.isLeafNode()) {
          node.setLeaf(task.isLeafNode());
          updateNodeRendering(node, task.getDisplayedEntry());
          nodeChanged = true;
          if (node.isLeaf()) {
            /* We didn't detect any child: remove the previously existing ones */
            removeAllChildNodes(node, false /* Remove suffixes */);
          }
        }
      }
      else if (oldState == NodeRefresher.State.SEARCHING_CHILDREN) {

        updateChildNodes(task);
        if (newState == NodeRefresher.State.FINISHED) {
          // The children search is finished
          if (canDoDifferentialUpdate(task)) {
            // Remove obsolete child nodes
            // Note: we scan in the reverse order to preserve indexes
            for (int i = node.getChildCount()-1; i >= 0; i--) {
              BasicNode child = (BasicNode)node.getChildAt(i);
              if (child.isObsolete()) {
                removeOneNode(child);
              }
            }
          }
          // The node may have become a leaf.
          if (node.getChildCount() == 0) {
            node.setLeaf(true);
            updateNodeRendering(node, task.getDisplayedEntry());
            nodeChanged = true;
          }
        }
        if (node.isSizeLimitReached())
        {
          fireEvent(BrowserEvent.Type.SIZE_LIMIT_REACHED);
        }
      }

      if (newState == NodeRefresher.State.FINISHED && node.getError() != null) {
        node.setError(null);
        nodeChanged = updateNodeRendering(node, task.getDisplayedEntry());
      }
    }


    if (nodeChanged) {
      treeModel.nodeChanged(task.getNode());
    }

    if (node.isLeaf() && node.getChildCount() >= 1) {
      throw new RuntimeException("Inconsistent node: " + node.getDN());
    }
  }


  /**
   * Commodity method that calls the method refreshTaskDidProgress in the event
   * thread.
   * @param task the task that progressed.
   * @param oldState the previous state of the task.
   * @param newState the new state of the task.
   * @throws InterruptedException if an errors occurs invoking the method.
   */
  void invokeRefreshTaskDidProgress(final NodeRefresher task,
      final NodeRefresher.State oldState,
      final NodeRefresher.State newState)
  throws InterruptedException {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        try {
          refreshTaskDidProgress(task, oldState, newState);
        }
        catch(Throwable t)
        {
          LOG.log(Level.SEVERE, "Error calling refreshTaskDidProgress: "+t, t);
        }
      }
    };
    swingInvoke(r);
  }



  /**
   * Core routines shared by the callbacks above
   * ===========================================
   */

  /**
   * Updates the child nodes for a given task.
   * @param task the task.
   * @throws NamingException if an error occurs.
   */
  private void updateChildNodes(NodeRefresher task) throws NamingException {
    BasicNode parent = task.getNode();
    ArrayList<Integer> insertIndex = new ArrayList<>();
    ArrayList<Integer> changedIndex = new ArrayList<>();
    boolean differential = canDoDifferentialUpdate(task);

    // NUMSUBORDINATE HACK
    // To avoid testing each child to the hacker,
    // we verify here if the parent node is parent of
    // any entry listed in the hacker.
    // In most case, the doNotTrust flag will false and
    // no overhead will be caused in the child loop.
    LDAPURL parentUrl = findUrlForDisplayedEntry(parent);
    boolean doNotTrust = numSubordinateHacker.containsChildrenOf(parentUrl);

    // Walk through the entries
    for (SearchResult entry : task.getChildEntries())
    {
      BasicNode child;

      // Search a child node matching the DN of the entry
      int index;
      if (differential) {
//      System.out.println("Differential mode -> starting to search");
        index = findChildNode(parent, entry.getName());
//      System.out.println("Differential mode -> ending to search");
      }
      else {
        index = - (parent.getChildCount() + 1);
      }

      // If no node matches, we create a new node
      if (index < 0) {
        // -(index + 1) is the location where to insert the new node
        index = -(index + 1);
        child = new BasicNode(entry.getName());
        parent.insert(child, index);
        updateNodeRendering(child, entry);
        insertIndex.add(index);
//      System.out.println("Inserted " + child.getDN() + " at " + index);
      }
      else { // Else we update the existing one
        child = (BasicNode)parent.getChildAt(index);
        if (updateNodeRendering(child, entry)) {
          changedIndex.add(index);
        }
        // The node is no longer obsolete
        child.setObsolete(false);
      }

      // NUMSUBORDINATE HACK
      // Let's see if child has subordinates or not.
      // Thanks to slapd, we cannot always trust the numSubOrdinates attribute.
      // If the child entry's DN is found in the hacker's list, then we ignore
      // the numSubordinate attribute... :((
      boolean hasNoSubOrdinates;
      if (!child.hasSubOrdinates() && doNotTrust) {
        hasNoSubOrdinates = !numSubordinateHacker.contains(
            findUrlForDisplayedEntry(child));
      }
      else {
        hasNoSubOrdinates = !child.hasSubOrdinates();
      }



      // Propagate the refresh
      // Note: logically we should unconditionally call:
      //  startRefreshNode(child, false, true);
      //
      // However doing that saturates refreshQueue
      // with many nodes. And, by design, RefreshTask
      // won't do anything on a node if:
      //    - this node has no subordinates
      //    - *and* this node has no referral data
      // So we test these conditions here and
      // skip the call to startRefreshNode() if
      // possible.
      //
      // The exception to this is the case where the
      // node had children (in the tree).  In this case
      // we force the refresh. See bug 5015115
      //
      if (!hasNoSubOrdinates
          || child.getReferral() != null
          || child.getChildCount() > 0) {
        startRefreshNode(child, entry, true);
      }
    }


    // Inform the tree model that we have created some new nodes
    if (insertIndex.size() >= 1) {
      treeModel.nodesWereInserted(parent, intArrayFromCollection(insertIndex));
    }
    if (changedIndex.size() >= 1) {
      treeModel.nodesChanged(parent, intArrayFromCollection(changedIndex));
    }
  }



  /**
   * Tells whether a differential update can be made in the provided task.
   * @param task the task.
   * @return <CODE>true</CODE> if a differential update can be made and
   * <CODE>false</CODE> otherwise.
   */
  private boolean canDoDifferentialUpdate(NodeRefresher task) {
    return task.getNode().getChildCount() >= 1
        && task.getNode().getNumSubOrdinates() <= 100;
  }


  /**
   * Recompute the rendering props of a node (text, style, icon) depending on.
   *    - the state of this node
   *    - the LDAPEntry displayed by this node
   * @param node the node to be rendered.
   * @param entry the search result for the entry that the node represents.
   */
  private boolean updateNodeRendering(BasicNode node, SearchResult entry)
  throws NamingException {
    if (entry != null) {
      node.setNumSubOrdinates(getNumSubOrdinates(entry));
      node.setHasSubOrdinates(
          node.getNumSubOrdinates() > 0 || getHasSubOrdinates(entry));
      node.setReferral(getReferral(entry));
      Set<String> ocValues = ConnectionUtils.getValues(entry,
          OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
      if (ocValues != null) {
        node.setObjectClassValues(ocValues.toArray(new String[ocValues.size()]));
      }
    }

    int aciCount = getAciCount(entry);
    Icon newIcon = getNewIcon(node, entry);

    // Construct the icon text according the dn, the aci count...
    StringBuilder sb2 = new StringBuilder();
    if (aciCount >= 1) {
      sb2.append(aciCount);
      sb2.append(" aci");
      if (aciCount != 1) {
        sb2.append("s");
      }
    }

    StringBuilder sb1 = new StringBuilder();
    if (node instanceof SuffixNode) {
      if (entry != null) {
        sb1.append(entry.getName());
      }
    } else {
      boolean useRdn = true;
      if (!RDN_ATTRIBUTE.equals(displayAttribute) && entry != null) {
        String value = ConnectionUtils.getFirstValue(entry,displayAttribute);
        if (value != null) {
          if (showAttributeName) {
            value = displayAttribute+"="+value;
          }
          sb1.append(value);
          useRdn = false;
        }
      }

      if (useRdn) {
        String rdn;
        if (followReferrals && node.getRemoteUrl() != null) {
          if (showAttributeName) {
            rdn = node.getRemoteRDNWithAttributeName();
          } else {
            rdn = node.getRemoteRDN();
          }
        }
        else {
          if (showAttributeName) {
            rdn = node.getRDNWithAttributeName();
          } else {
            rdn = node.getRDN();
          }
        }
        sb1.append(rdn);
      }
    }
    if (sb2.length() >= 1) {
      sb1.append("  (");
      sb1.append(sb2);
      sb1.append(")");
    }
    String newDisplayName = sb1.toString();

    // Select the font style according referral
    int newStyle = 0;
    if (isDisplayedEntryRemote(node)) {
      newStyle |= Font.ITALIC;
    }

    // Determine if the rendering needs to be updated
    boolean changed =
        node.getIcon() != newIcon
        || !node.getDisplayName().equals(newDisplayName)
        || node.getFontStyle() != newStyle;
    if (changed) {
      node.setIcon(newIcon);
      node.setDisplayName(newDisplayName);
      node.setFontStyle(newStyle);
    }
    return changed;
  }

  private int getAciCount(SearchResult entry) throws NamingException
  {
    if ((displayFlags & DISPLAY_ACI_COUNT) != 0 && entry != null) {
      Set<String> aciValues = ConnectionUtils.getValues(entry, "aci");
      if (aciValues != null) {
        return aciValues.size();
      }
    }
    return 0;
  }


  private Icon getNewIcon(BasicNode node, SearchResult entry)
      throws NamingException
  {
    // Select the icon according the objectClass,...
    int modifiers = 0;
    if (node.isLeaf() && !node.hasSubOrdinates()) {
      modifiers |= IconPool.MODIFIER_LEAF;
    }
    if (node.getReferral() != null) {
      modifiers |= IconPool.MODIFIER_REFERRAL;
    }
    if (node.getError() != null) {
      final Exception ex = node.getError().getException();
      if (ex != null)
      {
        LOG.log(Level.SEVERE, "node has error: " + ex, ex);
      }
      modifiers |= IconPool.MODIFIER_ERROR;
    }

    SortedSet<String> objectClasses = new TreeSet<>();
    if (entry != null) {
      Set<String> ocs = ConnectionUtils.getValues(entry, "objectClass");
      if (ocs != null)
      {
        objectClasses.addAll(ocs);
      }
    }

    if (node instanceof SuffixNode)
    {
      return iconPool.getSuffixIcon();
    }
    return iconPool.getIcon(objectClasses, modifiers);
  }

  /**
   * Find a child node matching a given DN.
   *
   * result >= 0    result is the index of the node matching childDn.
   * result < 0   -(result + 1) is the index at which the new node must be
   * inserted.
   * @param parent the parent node of the node that is being searched.
   * @param childDn the DN of the entry that is being searched.
   * @return the index of the node matching childDn.
   */
  public int findChildNode(BasicNode parent, String childDn) {
    int childCount = parent.getChildCount();
    int i = 0;
    while (i < childCount
        && !childDn.equals(((BasicNode)parent.getChildAt(i)).getDN())) {
      i++;
    }
    if (i >= childCount) { // Not found
      i = -(childCount + 1);
    }
    return i;
  }

  /**
   * Remove a single node from the tree model.
   * It takes care to cancel all the tasks associated to this node.
   * @param node the node to be removed.
   */
  private void removeOneNode(BasicNode node) {
    stopRefreshNode(node);
    treeModel.removeNodeFromParent(node);
  }


  /**
   * BrowserEvent management
   * =======================
   *
   * This method computes the total size of the queues,
   * compares this value with the last computed and
   * decides if an update event should be fired or not.
   *
   * It's invoked by task classes through SwingUtilities.invokeLater()
   * (see the wrapper below). That means the event handling routine
   * (processBrowserEvent) is executed in the event thread.
   * @param taskIsStarting whether the task is starting or not.
   */
  private void checkUpdateEvent(boolean taskIsStarting) {
    int newSize = refreshQueue.size();
    if (!taskIsStarting) {
      newSize = newSize - 1;
    }
    if (newSize != queueTotalSize) {
      if (queueTotalSize == 0 && newSize >= 1) {
        fireEvent(BrowserEvent.Type.UPDATE_START);
      }
      else if (queueTotalSize >= 1 && newSize == 0) {
        fireEvent(BrowserEvent.Type.UPDATE_END);
      }
      queueTotalSize = newSize;
    }
  }

  /**
   * Returns the size of the queue containing the different tasks.  It can be
   * used to know if there are search operations ongoing.
   * @return the number of RefreshTask operations ongoing (or waiting to start).
   */
  public int getQueueSize()
  {
    return refreshQueue.size();
  }


  /**
   * Fires a BrowserEvent.
   * @param type the type of the event.
   */
  private void fireEvent(BrowserEvent.Type type) {
    BrowserEvent event = new BrowserEvent(this, type);
    for (BrowserEventListener listener : listeners)
    {
      listener.processBrowserEvent(event);
    }
  }


  /**
   * Miscellaneous private routines
   * ==============================
   */


  /**
   * Find a SuffixNode in the tree model.
   * @param suffixDn the dn of the suffix node.
   * @param suffixNode the node from which we start searching.
   * @return the SuffixNode associated with the provided DN.  <CODE>null</CODE>
   * if nothing is found.
   * @throws IllegalArgumentException if a node with the given dn exists but
   * is not a suffix node.
   */
  private SuffixNode findSuffixNode(String suffixDn, SuffixNode suffixNode)
      throws IllegalArgumentException
  {
    if (Utilities.areDnsEqual(suffixNode.getDN(), suffixDn)) {
      return suffixNode;
    }

    int childCount = suffixNode.getChildCount();
    if (childCount == 0)
    {
      return null;
    }
    BasicNode child;
    int i = 0;
    boolean found = false;
    do
    {
      child = (BasicNode) suffixNode.getChildAt(i);
      if (Utilities.areDnsEqual(child.getDN(), suffixDn))
      {
        found = true;
      }
      i++;
    }
    while (i < childCount && !found);

    if (!found)
    {
      return null;
    }
    if (child instanceof SuffixNode)
    {
      return (SuffixNode) child;
    }

    // A node matches suffixDn however it's not a suffix node.
    // There's a bug in the caller.
    throw new IllegalArgumentException(suffixDn + " is not a suffix node");
  }



  /**
   * Return <CODE>true</CODE> if x is a non <code>null</code>
   * NameNotFoundException.
   * @return <CODE>true</CODE> if x is a non <code>null</code>
   * NameNotFoundException.
   */
  private boolean isNameNotFoundException(Object x) {
    return x instanceof NameNotFoundException;
  }



  /**
   * Get the value of the numSubordinates attribute.
   * If numSubordinates is not present, returns 0.
   * @param entry the entry to analyze.
   * @throws NamingException if an error occurs.
   * @return the value of the numSubordinates attribute.  0 if the attribute
   * could not be found.
   */
  private static int getNumSubOrdinates(SearchResult entry) throws NamingException
  {
    return toInt(ConnectionUtils.getFirstValue(entry, NUMSUBORDINATES_ATTR));
  }

  /**
   * Returns whether the entry has subordinates or not.  It uses an algorithm
   * based in hasSubordinates and numSubordinates attributes.
   * @param entry the entry to analyze.
   * @throws NamingException if an error occurs.
   * @return {@code true} if the entry has subordinates according to the values
   * of hasSubordinates and numSubordinates, returns {@code false} if none of
   * the attributes could be found.
   */
  public static boolean getHasSubOrdinates(SearchResult entry)
  throws NamingException
  {
    String v = ConnectionUtils.getFirstValue(entry, HASSUBORDINATES_ATTR);
    if (v != null) {
      return "true".equalsIgnoreCase(v);
    }
    return getNumSubOrdinates(entry) > 0;
  }

  /**
   * Get the value of the numSubordinates attribute.
   * If numSubordinates is not present, returns 0.
   * @param entry the entry to analyze.
   * @return the value of the numSubordinates attribute.  0 if the attribute
   * could not be found.
   */
  private static int getNumSubOrdinates(CustomSearchResult entry)
  {
    List<Object> vs = entry.getAttributeValues(NUMSUBORDINATES_ATTR);
    String v = null;
    if (vs != null && !vs.isEmpty())
    {
      v = vs.get(0).toString();
    }
    return toInt(v);
  }


  private static int toInt(String v)
  {
    if (v == null)
    {
      return 0;
    }
    try
    {
      return Integer.parseInt(v);
    }
    catch (NumberFormatException x)
    {
      return 0;
    }
  }

  /**
   * Returns whether the entry has subordinates or not.  It uses an algorithm
   * based in hasSubordinates and numSubordinates attributes.
   * @param entry the entry to analyze.
   * @return {@code true} if the entry has subordinates according to the values
   * of hasSubordinates and numSubordinates, returns {@code false} if none of
   * the attributes could be found.
   */
  public static boolean getHasSubOrdinates(CustomSearchResult entry)
  {
    List<Object> vs = entry.getAttributeValues(HASSUBORDINATES_ATTR);
    String v = null;
    if (vs != null && !vs.isEmpty())
    {
      v = vs.get(0).toString();
    }
    if (v != null)
    {
      return "true".equalsIgnoreCase(v);
    }
    return getNumSubOrdinates(entry) > 0;
  }


  /**
   * Returns the value of the 'ref' attribute.
   * <CODE>null</CODE> if the attribute is not present.
   * @param entry the entry to analyze.
   * @throws NamingException if an error occurs.
   * @return the value of the ref attribute.  <CODE>null</CODE> if the attribute
   * could not be found.
   */
  public static String[] getReferral(SearchResult entry) throws NamingException
  {
    String[] result = null;
    Set<String> values = ConnectionUtils.getValues(entry,
        OBJECTCLASS_ATTRIBUTE_TYPE_NAME);
    if (values != null)
    {
      for (String value : values)
      {
        boolean isReferral = "referral".equalsIgnoreCase(value);
        if (isReferral)
        {
          Set<String> refValues = ConnectionUtils.getValues(entry,
              ATTR_REFERRAL_URL);
          if (refValues != null)
          {
            result = new String[refValues.size()];
            refValues.toArray(result);
          }
          break;
        }
      }
    }
    return result;
  }


  /**
   * Returns true if the node is expanded.
   * @param node the node to analyze.
   * @return <CODE>true</CODE> if the node is expanded and <CODE>false</CODE>
   * otherwise.
   */
  public boolean nodeIsExpanded(BasicNode node) {
    TreePath tp = new TreePath(treeModel.getPathToRoot(node));
    return tree.isExpanded(tp);
  }

  /**
   * Expands node. Must be run from the event thread.  This is called
   * when the node is automatically expanded.
   * @param node the node to expand.
   */
  public void expandNode(BasicNode node) {
    automaticallyExpandedNode = true;
    TreePath tp = new TreePath(treeModel.getPathToRoot(node));
    tree.expandPath(tp);
    tree.fireTreeExpanded(tp);
    automaticallyExpandedNode = false;
  }



  /** Collection utilities. */
  /**
   * Returns an array of integer from a Collection of Integer objects.
   * @param v the Collection of Integer objects.
   * @return an array of int from a Collection of Integer objects.
   */
  private static int[] intArrayFromCollection(Collection<Integer> v) {
    int[] result = new int[v.size()];
    int i = 0;
    for (Integer value : v)
    {
      result[i] = value;
      i++;
    }
    return result;
  }


  /**
   * For debugging purpose: allows to switch easily
   * between invokeLater() and invokeAndWait() for
   * experimentation...
   * @param r the runnable to be invoked.
   * @throws InterruptedException if there is an error invoking SwingUtilities.
   */
  private static void swingInvoke(Runnable r) throws InterruptedException {
    try {
      SwingUtilities.invokeAndWait(r);
    }
    catch(InterruptedException x) {
      throw x;
    }
    catch(InvocationTargetException x) {
      // Probably a very big trouble...
      x.printStackTrace();
    }
  }


  /** The default implementation of the BrowserNodeInfo interface. */
  private class BrowserNodeInfoImpl implements BrowserNodeInfo
  {
    private BasicNode node;
    private LDAPURL url;
    private boolean isRemote;
    private boolean isSuffix;
    private boolean isRootNode;
    private String[] referral;
    private int numSubOrdinates;
    private boolean hasSubOrdinates;
    private int errorType;
    private Exception errorException;
    private Object errorArg;
    private String[] objectClassValues;
    private String toString;

    /**
     * The constructor of this object.
     * @param node the node in the tree that is used.
     */
    public BrowserNodeInfoImpl(BasicNode node) {
      this.node = node;
      url = findUrlForDisplayedEntry(node);

      isRootNode = node instanceof RootNode;
      isRemote = isDisplayedEntryRemote(node);
      isSuffix = node instanceof SuffixNode;
      referral = node.getReferral();
      numSubOrdinates = node.getNumSubOrdinates();
      hasSubOrdinates = node.hasSubOrdinates();
      objectClassValues = node.getObjectClassValues();
      if (node.getError() != null) {
        BasicNodeError error = node.getError();
        switch(error.getState()) {
        case READING_LOCAL_ENTRY:
          errorType = ERROR_READING_ENTRY;
          break;
        case SOLVING_REFERRAL:
          errorType = ERROR_SOLVING_REFERRAL;
          break;
        case DETECTING_CHILDREN:
        case SEARCHING_CHILDREN:
          errorType = ERROR_SEARCHING_CHILDREN;
          break;

        }
        errorException = error.getException();
        errorArg = error.getArg();
      }
      StringBuilder sb = new StringBuilder();
      sb.append(getURL());
      if (getReferral() != null) {
        sb.append(" -> ");
        sb.append(getReferral());
      }
      toString = sb.toString();
    }

    /**
     * Returns the node associated with this object.
     * @return  the node associated with this object.
     */
    @Override
    public BasicNode getNode() {
      return node;
    }

    /**
     * Returns the LDAP URL associated with this object.
     * @return the LDAP URL associated with this object.
     */
    @Override
    public LDAPURL getURL() {
      return url;
    }

    /**
     * Tells whether this is a root node or not.
     * @return <CODE>true</CODE> if this is a root node and <CODE>false</CODE>
     * otherwise.
     */
    @Override
    public boolean isRootNode() {
      return isRootNode;
    }

    /**
     * Tells whether this is a suffix node or not.
     * @return <CODE>true</CODE> if this is a suffix node and <CODE>false</CODE>
     * otherwise.
     */
    @Override
    public boolean isSuffix() {
      return isSuffix;
    }

    /**
     * Tells whether this is a remote node or not.
     * @return <CODE>true</CODE> if this is a remote node and <CODE>false</CODE>
     * otherwise.
     */
    @Override
    public boolean isRemote() {
      return isRemote;
    }

    /**
     * Returns the list of referral associated with this node.
     * @return the list of referral associated with this node.
     */
    @Override
    public String[] getReferral() {
      return referral;
    }

    /**
     * Returns the number of subordinates of the entry associated with this
     * node.
     * @return the number of subordinates of the entry associated with this
     * node.
     */
    @Override
    public int getNumSubOrdinates() {
      return numSubOrdinates;
    }

    /**
     * Returns whether the entry has subordinates or not.
     * @return {@code true} if the entry has subordinates and {@code false}
     * otherwise.
     */
    @Override
    public boolean hasSubOrdinates() {
      return hasSubOrdinates;
    }

    /**
     * Returns the error type associated we got when refreshing the node.
     * <CODE>null</CODE> if no error was found.
     * @return the error type associated we got when refreshing the node.
     * <CODE>null</CODE> if no error was found.
     */
    @Override
    public int getErrorType() {
      return errorType;
    }

    /**
     * Returns the exception associated we got when refreshing the node.
     * <CODE>null</CODE> if no exception was found.
     * @return the exception associated we got when refreshing the node.
     * <CODE>null</CODE> if no exception was found.
     */
    @Override
    public Exception getErrorException() {
      return errorException;
    }

    /**
     * Returns the error argument associated we got when refreshing the node.
     * <CODE>null</CODE> if no error argument was found.
     * @return the error argument associated we got when refreshing the node.
     * <CODE>null</CODE> if no error argument was found.
     */
    @Override
    public Object getErrorArg() {
      return errorArg;
    }

    /**
     * Return the tree path associated with the node in the tree.
     * @return the tree path associated with the node in the tree.
     */
    @Override
    public TreePath getTreePath() {
      return new TreePath(treeModel.getPathToRoot(node));
    }

    /**
     * Returns the object class values of the entry associated with the node.
     * @return the object class values of the entry associated with the node.
     */
    @Override
    public String[] getObjectClassValues() {
      return objectClassValues;
    }

    /**
     * Returns a String representation of the object.
     * @return a String representation of the object.
     */
    @Override
    public String toString() {
      return toString;
    }

    /**
     * Compares the provide node with this object.
     * @param node the node.
     * @return <CODE>true</CODE> if the node info represents the same node as
     * this and <CODE>false</CODE> otherwise.
     */
    @Override
    public boolean representsSameNode(BrowserNodeInfo node) {
      return node != null && node.getNode() == node;
    }
  }


  /**
   * Returns whether we are in automatic expand mode.  This mode is used when
   * the user specifies a filter and all the nodes are automatically expanded.
   * @return <CODE>true</CODE> if we are in automatic expand mode and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isAutomaticExpand()
  {
    return automaticExpand;
  }


  /**
   * Sets the automatic expand mode.
   * @param automaticExpand whether to expand automatically the nodes or not.
   */
  public void setAutomaticExpand(boolean automaticExpand)
  {
    this.automaticExpand = automaticExpand;
  }
}
