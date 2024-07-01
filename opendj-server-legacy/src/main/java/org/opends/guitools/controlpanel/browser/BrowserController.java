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

import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SortKey;
import org.forgerock.opendj.ldap.controls.ManageDsaITRequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.util.ConnectionWrapper;
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
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.HostPort;
import org.opends.server.types.LDAPURL;

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
  private static final Logger LOG = Logger.getLogger(BrowserController.class.getName());

  /** The mask used to display the number of ACIs or not. */
  private static final int DISPLAY_ACI_COUNT = 0x01;

  /** The list of attributes that are used to sort the entries (if the sorting option is used). */
  private static final SortKey[] SORT_ATTRIBUTES = {
    new SortKey("cn"),
    new SortKey("givenname"),
    new SortKey("o"),
    new SortKey("ou"),
    new SortKey("sn"),
    new SortKey("uid")
  };

  /**
   * This is a key value.  It is used to specify that the attribute that should
   * be used to display the entry is the RDN attribute.
   */
  private static final String RDN_ATTRIBUTE = "rdn attribute";

  /** The filter used to retrieve all the entries. */
  public static final Filter ALL_OBJECTS_FILTER = Filter.valueOf(
      "(|(objectClass=*)(objectClass=ldapsubentry))");

  private static final String NUMSUBORDINATES_ATTR = "numsubordinates";
  private static final String HASSUBORDINATES_ATTR = "hassubordinates";
  private static final String ACI_ATTR = "aci";

  private final JTree tree;
  private final DefaultTreeModel treeModel;
  private final RootNode rootNode;
  private int displayFlags = DISPLAY_ACI_COUNT;
  private String displayAttribute = RDN_ATTRIBUTE;
  private final boolean showAttributeName = false;
  private ConnectionWithControls connConfig;
  private ConnectionWithControls connUserData;
  private boolean showContainerOnly = true;
  private boolean automaticExpand;
  private boolean automaticallyExpandedNode;
  private String[] containerClasses = new String[0];
  private NumSubordinateHacker numSubordinateHacker;
  private int queueTotalSize;
  private int maxChildren;
  private final Collection<BrowserEventListener> listeners = new ArrayList<>();
  private final LDAPConnectionPool connectionPool;
  private final IconPool iconPool;

  private final NodeSearcherQueue refreshQueue;

  private ServerSideSortRequestControl sortControl;
  private ManageDsaITRequestControl followReferralsControl;
  private Filter filter;

  /**
   * Constructor of the BrowserController.
   * @param tree the tree that must be updated.
   * @param cpool the connection pool object that will provide the connections
   * to be used.
   * @param ipool the icon pool to be used to retrieve the icons that will be
   * used to render the nodes in the tree.
   */
  public BrowserController(JTree tree, LDAPConnectionPool cpool, IconPool ipool)
  {
    this.tree = tree;
    iconPool = ipool;
    rootNode = new RootNode();
    rootNode.setIcon(iconPool.getIconForRootNode());
    treeModel = new DefaultTreeModel(rootNode);
    tree.setModel(treeModel);
    tree.addTreeExpansionListener(this);
    tree.setCellRenderer(new BrowserCellRenderer());
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
   * @param connConfiguration the connection to be used to retrieve the data in
   * the configuration base DNs.
   * @param connUserData the connection to be used to retrieve the data in the
   * user base DNs.
   */
  public void setConnections(
      ServerDescriptor server,
      ConnectionWrapper connConfiguration,
      ConnectionWrapper connUserData) {
    String rootNodeName;
    if (connConfiguration != null)
    {
      this.connConfig = new ConnectionWithControls(connConfiguration, sortControl, followReferralsControl);
      this.connUserData = new ConnectionWithControls(connUserData, sortControl, followReferralsControl);
      rootNodeName = HostPort.toString(server.getHostname(),
                                       connConfig.getConnectionWrapper().getHostPort().getPort());
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
  public ConnectionWithControls getConfigurationConnection() {
    return connConfig;
  }

  /**
   * Return the connection for accessing the directory user data.
   * @return the connection for accessing the directory user data.
   */
  public ConnectionWithControls getUserDataConnection() {
    return connUserData;
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
   * @return {@code true} if the provided String is the DN of a suffix
   * and {@code false} otherwise.
   * @throws IllegalArgumentException if a node with the given dn exists but
   * is not a suffix node.
   */
  public boolean hasSuffix(DN suffixDn) throws IllegalArgumentException
  {
    return findSuffixNode(suffixDn, rootNode) != null;
  }

  /**
   * Add an LDAP suffix to this controller.
   * A new node is added in the JTree and a refresh is started.
   * @param suffixDn the DN of the suffix.
   * @param parentSuffixDn the DN of the parent suffix (or {@code null} if
   * there is no parent DN).
   * @return the TreePath of the new node.
   * @throws IllegalArgumentException if a node with the given dn exists.
   */
  public TreePath addSuffix(DN suffixDn, DN parentSuffixDn) throws IllegalArgumentException
  {
    SuffixNode parentNode;
    if (parentSuffixDn != null) {
      parentNode = findSuffixNode(parentSuffixDn, rootNode);
      if (parentNode == null) {
        throw new IllegalArgumentException("Invalid suffix dn " + parentSuffixDn);
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
  public TreePath addNodeUnderRoot(DN nodeDn) {
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
    restartRefresh();
  }

  private void restartRefresh()
  {
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
   * @return {@code true} if we are showing the attribute name and
   * {@code false} otherwise.
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
   *
   * @return {@code true} if this controller follows referrals, {@code false} otherwise.
   */
  public boolean isFollowReferrals() {
    return followReferralsControl != null;
  }

  /**
   * Enable/display the following of referrals.
   * This routine starts a refresh on each referral node.
   * @param followReferrals whether to follow referrals or not.
   */
  public void setFollowReferrals(boolean followReferrals) {
    followReferralsControl = followReferrals ? ManageDsaITRequestControl.newControl(false) : null;
    resetRequestControls();
    restartRefresh();
  }

  /**
   * Return true if entries are displayed sorted.
   *
   * @return {@code true} if entries are displayed sorted, {@code false} otherwise.
   */
  public boolean isSorted() {
    return sortControl != null;
  }

  /**
   * Enable/disable entry sort.
   * This routine collapses the JTree and invokes startRefresh().
   * @param sorted whether to sort the entries or not.
   */
  public void setSorted(boolean sorted) {
    sortControl = sorted ? ServerSideSortRequestControl.newControl(false, SORT_ATTRIBUTES) : null;
    resetRequestControls();
    restartRefresh();
  }

  private void resetRequestControls()
  {
    this.connConfig.setRequestControls(sortControl, followReferralsControl);
    this.connUserData.setRequestControls(sortControl, followReferralsControl);
    this.connectionPool.setRequestControls(sortControl, followReferralsControl);
  }

  /**
   * Return true if only container entries are displayed.
   * An entry is a container if:
   *    - it has some children
   *    - or its class is one of the container classes
   *      specified with setContainerClasses().
   * @return {@code true} if only container entries are displayed and
   * {@code false} otherwise.
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
  public TreePath notifyEntryAdded(BrowserNodeInfo parentInfo, DN newEntryDn) {
    BasicNode parentNode = parentInfo.getNode();
    BasicNode childNode = new BasicNode(newEntryDn);
    int childIndex;
    if (isSorted()) {
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
    BasicNode node = nodeInfo != null ? nodeInfo.getNode() : rootNode;
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
  private void startRefreshNode(BasicNode node, SearchResultEntry localEntry,
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
    BrowserCellRenderer renderer = (BrowserCellRenderer) tree.getCellRenderer();
    renderer.setInspectedNode(node != null ? node.getNode() : null);
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
  public void setFilter(Filter filter)
  {
    this.filter = filter;
  }

  /**
   * Returns the filter that is being used to search the entries.
   * @return the filter that is being used to search the entries.
   */
  public Filter getFilter()
  {
    return filter;
  }

  /**
   * Returns the filter used to make a object base search.
   * @return the filter used to make a object base search.
   */
  Filter getObjectSearchFilter()
  {
    return ALL_OBJECTS_FILTER;
  }

  /**
   * Return the LDAP search filter to use for searching child entries.
   * If showContainerOnly is true, the filter will select only the
   * container entries. If not, the filter will select all the children.
   * @return the LDAP search filter to use for searching child entries.
   */
  Filter getChildSearchFilter()
  {
    if (!showContainerOnly)
    {
      return filter;
    }

    String result = "(|(&(hasSubordinates=true)" + filter + ")";
    if (isFollowReferrals()) {
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
      result += "(objectClass=referral)";
    }
    for (String containerClass : containerClasses)
    {
      result += "(objectClass=" + containerClass + ")";
    }
    result += ")";

    return Filter.valueOf(result);
  }

  /**
   * Return the LDAP connection to reading the base entry of a node.
   * @param node the node for which we want the LDAP connection.
   * @throws LdapException if there is an error retrieving the connection.
   * @return the LDAP connection to reading the base entry of a node.
   */
  ConnectionWithControls findConnectionForLocalEntry(BasicNode node) throws LdapException {
    return findConnectionForLocalEntry(node, isConfigurationNode(node));
  }

  /**
   * Return the LDAP connection to reading the base entry of a node.
   * @param node the node for which we want toe LDAP connection.
   * @param isConfigurationNode whether the node is a configuration node or not.
   * @throws LdapException if there is an error retrieving the connection.
   * @return the LDAP connection to reading the base entry of a node.
   */
  private ConnectionWithControls findConnectionForLocalEntry(BasicNode node,
      boolean isConfigurationNode) throws LdapException
  {
    if (node == rootNode) {
      return connConfig;
    }

    final BasicNode parent = (BasicNode) node.getParent();
    if (parent != null && parent != rootNode)
    {
      return findConnectionForDisplayedEntry(parent, isConfigurationNode);
    }
    return isConfigurationNode ? connConfig : connUserData;
  }

  /**
   * Returns whether a given node is a configuration node or not.
   * @param node the node to analyze.
   * @return {@code true} if the node is a configuration node, {@code false} otherwise.
   */
  public boolean isConfigurationNode(BasicNode node)
  {
    if (node instanceof RootNode)
    {
      return true;
    }
    if (node instanceof SuffixNode)
    {
      DN dn = node.getDN();
      return dn.equals(ADSContext.getAdministrationSuffixDN())
          || dn.equals(DN.valueOf(ConfigConstants.DN_DEFAULT_SCHEMA_ROOT))
          || dn.equals(DN.valueOf(ConfigConstants.DN_TASK_ROOT))
          || dn.equals(DN.valueOf(ConfigConstants.DN_CONFIG_ROOT))
          || dn.equals(DN.valueOf(ConfigConstants.DN_MONITOR_ROOT))
          || dn.equals(DN.valueOf(ConfigConstants.DN_TRUST_STORE_ROOT))
          || dn.equals(DN.valueOf(ConfigConstants.DN_BACKUP_ROOT))
          || dn.equals(DN.valueOf(DN_EXTERNAL_CHANGELOG_ROOT));
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
   * @throws LdapException if there is an error retrieving the connection.
   */
  public ConnectionWithControls findConnectionForDisplayedEntry(BasicNode node) throws LdapException {
    return findConnectionForDisplayedEntry(node, isConfigurationNode(node));
  }

  /**
   * Return the LDAP connection to search the displayed entry (which can be the
   * local or remote entry).
   * @param node the node for which we want toe LDAP connection.
   * @param isConfigurationNode whether the node is a configuration node or not.
   * @return the LDAP connection to search the displayed entry.
   * @throws LdapException if there is an error retrieving the connection.
   */
  private ConnectionWithControls findConnectionForDisplayedEntry(BasicNode node,
      boolean isConfigurationNode) throws LdapException {
    if (isFollowReferrals() && node.getRemoteUrl() != null)
    {
      return connectionPool.getConnection(node.getRemoteUrl());
    }
    return findConnectionForLocalEntry(node, isConfigurationNode);
  }

  /**
   * Release a connection returned by selectConnectionForChildEntries() or
   * selectConnectionForBaseEntry().
   * @param conn the connection to be released.
   */
  void releaseLDAPConnection(ConnectionWithControls conn) {
    if (conn != connConfig && conn != connUserData)
    {
      // Thus it comes from the connection pool
      connectionPool.releaseConnection(conn);
    }
  }

  /**
   * Returns the local entry URL for a given node.
   * @param node the node.
   * @return the local entry URL for a given node.
   */
  LDAPURL findUrlForLocalEntry(BasicNode node) {
    ConnectionWrapper conn = connConfig.getConnectionWrapper();
    if (node == rootNode) {
      return LDAPConnectionPool.makeLDAPUrl(conn.getHostPort(), "", conn.isLdaps());
    }
    final BasicNode parent = (BasicNode) node.getParent();
    if (parent != null)
    {
      final LDAPURL parentUrl = findUrlForDisplayedEntry(parent);
      return LDAPConnectionPool.makeLDAPUrl(parentUrl, node.getDN().toString());
    }
    return LDAPConnectionPool.makeLDAPUrl(conn.getHostPort(), node.getDN().toString(), conn.isLdaps());
  }

  /**
   * Returns the displayed entry URL for a given node.
   * @param node the node.
   * @return the displayed entry URL for a given node.
   */
  private LDAPURL findUrlForDisplayedEntry(BasicNode node)
  {
    if (isFollowReferrals() && node.getRemoteUrl() != null) {
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
  DN findBaseDNForChildEntries(BasicNode node) {
    if (isFollowReferrals() && node.getRemoteUrl() != null) {
      return DN.valueOf(node.getRemoteUrl().getRawBaseDN());
    }
    return node.getDN();
  }

  /**
   * Tells whether a node is displaying a remote entry.
   * @param node the node.
   * @return {@code true} if the node displays a remote entry and
   * {@code false} otherwise.
   */
  private boolean isDisplayedEntryRemote(BasicNode node) {
    if (isFollowReferrals()) {
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
   */
  private void refreshTaskDidProgress(NodeRefresher task,
      NodeRefresher.State oldState,
      NodeRefresher.State newState) {
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
      // In case of EntryNotFoundException, we simply remove the node from the tree.
      // Except when it's due a to referral resolution: we keep the node
      // in order the user can fix the referral.
      if (task.getException() instanceof EntryNotFoundException
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
        SearchResultEntry localEntry = task.getLocalEntry();
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
   */
  private void updateChildNodes(NodeRefresher task) {
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
    for (SearchResultEntry entry : task.getChildEntries())
    {
      // Search a child node matching the DN of the entry
      int index;
      if (differential) {
        index = findChildNode(parent, entry.getName());
      }
      else {
        index = - (parent.getChildCount() + 1);
      }

      BasicNode child;
      // If no node matches, we create a new node
      if (index < 0) {
        // -(index + 1) is the location where to insert the new node
        index = -(index + 1);
        child = new BasicNode(entry.getName());
        parent.insert(child, index);
        updateNodeRendering(child, entry);
        insertIndex.add(index);
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
      // However doing that saturates refreshQueue with many nodes.
      // And, by design, RefreshTask won't do anything on a node if:
      //    - this node has no subordinates
      //    - *and* this node has no referral data
      // So we test these conditions here
      // and skip the call to startRefreshNode() if possible.
      //
      // The exception to this is the case where the node had children
      // (in the tree). In this case we force the refresh. See bug 5015115
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
   * @return {@code true} if a differential update can be made and
   * {@code false} otherwise.
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
   * @return whether the node display changed
   */
  private boolean updateNodeRendering(BasicNode node, SearchResultEntry entry) {
    if (entry != null) {
      node.setNumSubOrdinates(getNumSubOrdinates(entry));
      node.setHasSubOrdinates(
          node.getNumSubOrdinates() > 0 || getHasSubOrdinates(entry));
      node.setReferral(getReferral(entry));
      String[] ocValues = entry.parseAttribute(OBJECTCLASS_ATTRIBUTE_TYPE_NAME).asSetOfString().toArray(new String[0]);
      node.setObjectClassValues(ocValues);
    }

    int aciCount = getAciCount(entry);
    Icon newIcon = getNewIcon(node, entry);

    // Construct the icon text according the dn, the aci count...
    String newDisplayName = newDisplayName(node, entry, aciCount);

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
      return true;
    }
    return false;
  }

  private String newDisplayName(BasicNode node, SearchResultEntry entry, int aciCount)
  {
    StringBuilder result = new StringBuilder();
    if (node instanceof SuffixNode)
    {
      if (entry != null)
      {
        result.append(entry.getName());
      }
    }
    else
    {
      boolean useRdn = true;
      if (!RDN_ATTRIBUTE.equals(displayAttribute) && entry != null)
      {
        String value = firstValueAsString(entry, displayAttribute);
        if (value != null)
        {
          if (showAttributeName)
          {
            value = displayAttribute + "=" + value;
          }
          result.append(value);
          useRdn = false;
        }
      }

      if (useRdn)
      {
        result.append(getRDN(node));
      }
    }

    StringBuilder acis = new StringBuilder();
    if (aciCount >= 1)
    {
      acis.append(aciCount);
      acis.append(" aci");
      if (aciCount != 1)
      {
        acis.append("s");
      }
    }
    if (acis.length() >= 1)
    {
      result.append("  (");
      result.append(acis);
      result.append(")");
    }
    return result.toString();
  }

  private String getRDN(BasicNode node)
  {
    if (isFollowReferrals() && node.getRemoteUrl() != null) {
      if (showAttributeName) {
        return node.getRemoteRDNWithAttributeName();
      } else {
        return node.getRemoteRDN();
      }
    }
    else {
      if (showAttributeName) {
        return node.getRDNWithAttributeName();
      } else {
        return node.getRDN();
      }
    }
  }

  private int getAciCount(SearchResultEntry entry)
  {
    if ((displayFlags & DISPLAY_ACI_COUNT) != 0 && entry != null) {
      return entry.parseAttribute("aci").asSetOfByteString().size();
    }
    return 0;
  }

  private Icon getNewIcon(BasicNode node, SearchResultEntry entry)
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
      objectClasses.addAll(entry.parseAttribute("objectClass").asSetOfString());
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
  public static int findChildNode(BasicNode parent, DN childDn) {
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
   * @return the SuffixNode associated with the provided DN.  {@code null}
   * if nothing is found.
   * @throws IllegalArgumentException if a node with the given dn exists but
   * is not a suffix node.
   */
  private static SuffixNode findSuffixNode(DN suffixDn, SuffixNode suffixNode)
      throws IllegalArgumentException
  {
    if (suffixNode.getDN().equals(suffixDn)) {
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
      if (child.getDN().equals(suffixDn))
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
   * Get the value of the numSubordinates attribute.
   * If numSubordinates is not present, returns 0.
   * @param entry the entry to analyze.
   * @return the value of the numSubordinates attribute.  0 if the attribute
   * could not be found.
   */
  private static int getNumSubOrdinates(Entry entry)
  {
    try
    {
      return entry.parseAttribute(NUMSUBORDINATES_ATTR).asInteger(0);
    }
    catch (LocalizedIllegalArgumentException e)
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
  public static boolean getHasSubOrdinates(Entry entry)
  {
    Boolean val = entry.parseAttribute(HASSUBORDINATES_ATTR).asBoolean();
    return val != null ? val : getNumSubOrdinates(entry) > 0;
  }

  /**
   * Returns the value of the 'ref' attribute.
   * {@code null} if the attribute is not present.
   * @param entry the entry to analyze.
   * @return the value of the ref attribute.  {@code null} if the attribute
   * could not be found.
   */
  public static String[] getReferral(SearchResultEntry entry)
  {
    Set<String> values = entry.parseAttribute(OBJECTCLASS_ATTRIBUTE_TYPE_NAME).asSetOfString();
    for (String value : values)
    {
      if ("referral".equalsIgnoreCase(value))
      {
        Set<String> refValues = entry.parseAttribute(ATTR_REFERRAL_URL).asSetOfString();
        return !refValues.isEmpty() ? refValues.toArray(new String[0]) : null;
      }
    }
    return null;
  }

  /**
   * Returns true if the node is expanded.
   * @param node the node to analyze.
   * @return {@code true} if the node is expanded and {@code false} otherwise.
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
     * @return {@code true} if this is a root node and {@code false} otherwise.
     */
    @Override
    public boolean isRootNode() {
      return isRootNode;
    }

    /**
     * Tells whether this is a suffix node or not.
     * @return {@code true} if this is a suffix node and {@code false} otherwise.
     */
    @Override
    public boolean isSuffix() {
      return isSuffix;
    }

    /**
     * Tells whether this is a remote node or not.
     * @return {@code true} if this is a remote node and {@code false} otherwise.
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
     * {@code null} if no error was found.
     * @return the error type associated we got when refreshing the node.
     * {@code null} if no error was found.
     */
    @Override
    public int getErrorType() {
      return errorType;
    }

    /**
     * Returns the exception associated we got when refreshing the node.
     * {@code null} if no exception was found.
     * @return the exception associated we got when refreshing the node.
     * {@code null} if no exception was found.
     */
    @Override
    public Exception getErrorException() {
      return errorException;
    }

    /**
     * Returns the error argument associated we got when refreshing the node.
     * {@code null} if no error argument was found.
     * @return the error argument associated we got when refreshing the node.
     * {@code null} if no error argument was found.
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
     * @return {@code true} if the node info represents the same node as
     * this and {@code false} otherwise.
     */
    @Override
    public boolean representsSameNode(BrowserNodeInfo node) {
      return node != null && node.getNode() == node;
    }
  }

  /**
   * Returns whether we are in automatic expand mode.  This mode is used when
   * the user specifies a filter and all the nodes are automatically expanded.
   * @return {@code true} if we are in automatic expand mode and
   * {@code false} otherwise.
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
