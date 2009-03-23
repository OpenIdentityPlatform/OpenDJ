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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.browser;

import java.awt.Font;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
import org.opends.quicksetup.Constants;
import org.opends.server.config.ConfigConstants;
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
  /**
   * The mask used to display the number of ACIs or not.
   */
  public final static int DISPLAY_ACI_COUNT        = 0x01;

  /**
   * The list of attributes that are used to sort the entries (if the sorting
   * option is used).
   */
  public static final String[] SORT_ATTRIBUTES = {"cn", "givenname", "o", "ou",
    "sn", "uid"};

  /**
   * This is a key value.  It is used to specify that the attribute that should
   * be used to display the entry is the RDN attribute.
   */
  public static final String RDN_ATTRIBUTE = "rdn attribute";

  /**
   * The filter used to retrieve all the entries.
   */
  public static final String ALL_OBJECTS_FILTER =
    "(|(objectClass=*)(objectClass=ldapsubentry))";

  private JTree tree;
  private DefaultTreeModel treeModel;
  private RootNode rootNode;
  private int displayFlags;
  private String displayAttribute;
  private boolean showAttributeName;
  private InitialLdapContext ctxConfiguration;
  private InitialLdapContext ctxUserData;
  boolean followReferrals;
  boolean sorted;
  boolean showContainerOnly;
  private boolean automaticExpand;
  private boolean automaticallyExpandedNode;
  private String[] containerClasses;
  private NumSubordinateHacker numSubordinateHacker;
  private int queueTotalSize;
  private int maxChildren = 0;
  private Collection<BrowserEventListener> listeners =
    new ArrayList<BrowserEventListener>();
  private LDAPConnectionPool connectionPool;
  private IconPool iconPool;

  private NodeSearcherQueue refreshQueue;

  private String filter;

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
    displayAttribute = RDN_ATTRIBUTE;
    followReferrals = true;
    sorted = false;
    showContainerOnly = true;
    containerClasses = new String[0];
    queueTotalSize = 0;
    connectionPool = cpool;
    connectionPool.addReferralAuthenticationListener(this);

    refreshQueue = new NodeSearcherQueue("New red", 2);

    // NUMSUBORDINATE HACK
    // Create an empty hacker to avoid null value test.
    // However this value will be overriden by full hacker.
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
      InitialLdapContext ctxConfiguration,
      InitialLdapContext ctxUserData) throws NamingException {
    String rootNodeName;
    if (ctxConfiguration != null)
    {
      this.ctxConfiguration = ctxConfiguration;
      this.ctxUserData = ctxUserData;

      this.ctxConfiguration.setRequestControls(
          getConfigurationRequestControls());
      this.ctxUserData.setRequestControls(getRequestControls());
      rootNodeName = server.getHostname() + ":" +
      ConnectionUtils.getPort(ctxConfiguration);
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
   * Tells wether the given suffix is in the tree or not.
   * @param suffixDn the DN of the suffix to be analyzed.
   * @return <CODE>true</CODE> if the provided String is the DN of a suffix
   * and <CODE>false</CODE> otherwise.
   */
  public boolean hasSuffix(String suffixDn) {
    return (findSuffixNode(suffixDn, rootNode) != null);
  }

  /**
   * Add an LDAP suffix to this controller.
   * A new node is added in the JTree and a refresh is started.
   * @param suffixDn the DN of the suffix.
   * @param parentSuffixDn the DN of the parent suffix (or <CODE>null</CODE> if
   * there is no parent DN).
   * @return the TreePath of the new node.
   */
  public TreePath addSuffix(String suffixDn, String parentSuffixDn) {
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
    else {
      index = - (index + 1);
    }
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
    if (index >= 0) { // A node has alreay this dn -> bug
      throw new IllegalArgumentException("Duplicate node dn " + nodeDn);
    }
    else {
      index = - (index + 1);
    }
    BasicNode newNode = new BasicNode(nodeDn);
    treeModel.insertNodeInto(newNode, parentNode, index);
    startRefreshNode(newNode, null, true);

    return new TreePath(treeModel.getPathToRoot(newNode));
  }



  /**
   * Remove the suffix from this controller.
   * The controller updates the JTree and returns the TreePath
   * of the parent node.
   * @param suffixDn the DN of the suffix to be removed.
   * @return the TreePath of the parent node of the removed node.
   */
  public TreePath removeSuffix(String suffixDn) {
    TreePath result = null;
    BasicNode node = findSuffixNode(suffixDn, rootNode);
    TreeNode parentNode = node.getParent();
    /* If the parent is null... the node is no longer in the tree */
    if (parentNode != null) {
      removeOneNode(node);
      result = new TreePath(treeModel.getPathToRoot(parentNode));
    }
    return result;
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
   * Says wether to show the attribute name or not.
   * This routine collapses the JTree and invokes startRefresh().
   * @param showAttributeName whether to show the attribute name or not.
   */
  public void showAttributeName(boolean showAttributeName) {
    this.showAttributeName = showAttributeName;
    stopRefresh();
    removeAllChildNodes(rootNode, true /* Keep suffixes */);
    startRefresh(null);
  }

  /**
   * Says wether we are showing the attribute name or not.
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
   */
  public void setFollowReferrals(boolean followReferrals) {
    this.followReferrals = followReferrals;
    startRefreshReferralNodes(rootNode);
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
   * Remove a BrowserEventListener from this controller.
   * @param l the listener to be removed.
   */
  public void removeBrowserEventListener(BrowserEventListener l) {
    listeners.remove(l);
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
      else {
        childIndex = - (childIndex + 1);
      }
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
    TreePath result = null;
    BasicNode node = nodeInfo.getNode();
    if (node == rootNode) {
      throw new IllegalArgumentException("Root node cannot be removed");
    }
    TreeNode parentNode = node.getParent();

    /* If the parent is null... the node is no longer in the tree */
    if (parentNode != null) {
      removeOneNode(node);
      result = new TreePath(treeModel.getPathToRoot(parentNode));
    }
    return result;
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

  /**
   * Notify this controller that a child entry has changed.
   * The controller has to refresh the corresponding node and (if necessary)
   * itself.
   * @param nodeInfo the parent of the node that changed.
   * @param dn the DN of the entry that changed.
   */
  public void notifyChildEntryChanged(BrowserNodeInfo nodeInfo, String dn) {
    BasicNode node = nodeInfo.getNode();
    startRefreshNode(node, null, true);
  }

  /**
   * Notify this controller that a child entry has been added.
   * The controller has to refresh the corresponding node and (if necessary)
   * itself.
   * @param nodeInfo the parent of the node that was added.
   * @param dn the DN of the entry that was added.
   */
  public void notifyChildEntryAdded(BrowserNodeInfo nodeInfo, String dn) {
    BasicNode node = nodeInfo.getNode();
    startRefreshNode(node, null, true);
  }

  /**
   * Notify this controller that a child entry has been deleted.
   * The controller has to refresh the corresponding node and (if necessary)
   * itself.
   * @param nodeInfo the parent of the node that was deleted.
   * @param dn the DN of the entry that was deleted.
   */
  public void notifyChildEntryDeleted(BrowserNodeInfo nodeInfo, String dn) {
    BasicNode node = nodeInfo.getNode();
    if (node.getParent() != null) {
      startRefreshNode((BasicNode) node.getParent(), null, true);
    } else {
      startRefreshNode(node, null, true);
    }
  }


  /**
   * Notify this controller that authentication data have changed in the
   * connection pool.
   */
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
  public void notifyAuthDataChanged(LDAPURL url) {
    // TODO: temporary implementation
    //    we should refresh only nodes :
    //    - whose URL matches 'url'
    //    - whose errorType == ERROR_SOLVING_REFERRAL and
    //      errorArg == url
    startRefreshReferralNodes(rootNode);
  }


  /**
   * Start a refresh from the specified node.
   * If some refresh are on-going on descendent nodes, they are stopped.
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


  /**
   * Equivalent to startRefresh(null).
   */
  public void startRefresh() {
    startRefresh(null);
  }


  /**
   * Stop the current refreshing.
   * Nodes being expanded are collapsed.
   */
  public void stopRefresh() {
    stopRefreshNode(rootNode);
    // TODO: refresh must be stopped in a clean state.
  }


  /**
   * Shutdown the controller : all the backgroup threads are stopped.
   * After this call, the controller is no longer usable.
   */
  public void shutDown() {
    tree.removeTreeExpansionListener(this);
    refreshQueue.shutdown();
    connectionPool.flush();
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
  void startRefreshNode(BasicNode node, SearchResult localEntry,
      boolean recursive) {
    if (node == rootNode) {
      // For the root node, readBaseEntry is meaningless.
      if (recursive) {
        // The root cannot be queued directly.
        // We need to queue each child individually.
        Enumeration e = rootNode.children();
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
      // the subsuffixes if any.
      if (recursive && (node instanceof SuffixNode)) {
        Enumeration e = node.children();
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
  void stopRefreshNode(BasicNode node) {
    if (node == rootNode) {
      refreshQueue.cancelAll();
    }
    else {
      Enumeration e = node.children();
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
  void startRefreshReferralNodes(BasicNode parentNode) {
    Enumeration e = parentNode.children();
    while (e.hasMoreElements()) {
      BasicNode child = (BasicNode)e.nextElement();
      if ((child.getReferral() != null) || (child.getRemoteUrl() != null)) {
        startRefreshNode(child, null, true);
      }
      else {
        startRefreshReferralNodes(child);
      }
    }
  }



  /**
   * Remove all the children below parentNode *without changing the leaf state*.
   * If specified, it keeps the SuffixNode and recurse on them. Inform the tree
   * model.
   * @param parentNode the parent node.
   * @param keepSuffixes whether the suffixes should be kept or not.
   */
  void removeAllChildNodes(BasicNode parentNode, boolean keepSuffixes) {
    for (int i = parentNode.getChildCount() - 1; i >= 0; i--) {
      BasicNode child = (BasicNode)parentNode.getChildAt(i);
      if ((child instanceof SuffixNode) && keepSuffixes) {
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
  public void treeCollapsed(TreeExpansionEvent event) {
    Object node = event.getPath().getLastPathComponent();
    if (!(node instanceof RootNode)) {
      BasicNode basicNode = (BasicNode)node;
      stopRefreshNode(basicNode);
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
      for (int i = 0; i < containerClasses.length; i++) {
        result += "(objectClass=" + containerClasses[i] + ")";
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
  InitialLdapContext findConnectionForLocalEntry(BasicNode node,
      boolean isConfigurationNode)
  throws NamingException {
    InitialLdapContext result;
    if (node == rootNode) {
      result = ctxConfiguration;
    }
    else  {
      BasicNode parent = (BasicNode)node.getParent();
      if (parent != null) {
        result = findConnectionForDisplayedEntry(parent, isConfigurationNode);
      } else {
        if (isConfigurationNode)
        {
          result = ctxConfiguration;
        }
        else
        {
          result = ctxUserData;
        }
      }
    }
    return result;
  }

  /**
   * Returns whether a given node is a configuration node or not.
   * @param node the node to analyze.
   * @return <CODE>true</CODE> if the node is a configuration node and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isConfigurationNode(BasicNode node)
  {
    boolean isConfigurationNode = false;
    if (node instanceof SuffixNode)
    {
      String dn = node.getDN();
      if (Utilities.areDnsEqual(dn, ADSContext.getAdministrationSuffixDN()) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_DEFAULT_SCHEMA_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_TASK_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_CONFIG_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_MONITOR_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_TRUST_STORE_ROOT) ||
          Utilities.areDnsEqual(dn, ConfigConstants.DN_BACKUP_ROOT) ||
          Utilities.areDnsEqual(dn, Constants.REPLICATION_CHANGES_DN))
      {
        isConfigurationNode = true;
      }
    }
    else if (node instanceof RootNode)
    {
      isConfigurationNode = true;
    }
    else
    {
      BasicNode parentNode = (BasicNode)node.getParent();
      return isConfigurationNode(parentNode);
    }

    return isConfigurationNode;
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
  InitialLdapContext findConnectionForDisplayedEntry(BasicNode node,
      boolean isConfigurationNode)
  throws NamingException {
    InitialLdapContext result;
    if (followReferrals && (node.getRemoteUrl() != null)) {
      result = connectionPool.getConnection(node.getRemoteUrl());
    }
    else {
      result = findConnectionForLocalEntry(node, isConfigurationNode);
    }
    return result;
  }



  /**
   * Release a connection returned by selectConnectionForChildEntries() or
   * selectConnectionForBaseEntry().
   * @param ctx the connection to be released.
   */
  void releaseLDAPConnection(InitialLdapContext ctx) {
    if ((ctx != this.ctxConfiguration) &&
        (ctx != this.ctxUserData))
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
    LDAPURL result;
    if (node == rootNode) {
      result = LDAPConnectionPool.makeLDAPUrl(ctxConfiguration, "");
    }
    else {
      BasicNode parent = (BasicNode)node.getParent();
      if (parent != null) {
        LDAPURL parentUrl = findUrlForDisplayedEntry(parent);
        result = LDAPConnectionPool.makeLDAPUrl(parentUrl, node.getDN());
      } else {
        result = LDAPConnectionPool.makeLDAPUrl(ctxConfiguration, node.getDN());
      }
    }
    return result;
  }


  /**
   * Returns the displayed entry URL for a given node.
   * @param node the node.
   * @return the displayed entry URL for a given node.
   */
  LDAPURL findUrlForDisplayedEntry(BasicNode node) {
    LDAPURL result;
    if (followReferrals && (node.getRemoteUrl() != null)) {
      result = node.getRemoteUrl();
    }
    else {
      result = findUrlForLocalEntry(node);
    }
    return result;
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
    String result;

    if (followReferrals && (node.getRemoteUrl() != null)) {
      result = node.getRemoteUrl().getRawBaseDN();
    }
    else {
      result = node.getDN();
    }
    return result;
  }



  /**
   * Tells whether a node is displaying a remote entry.
   * @param node the node.
   * @return <CODE>true</CODE> if the node displays a remote entry and
   * <CODE>false</CODE> otherwise.
   */
  boolean isDisplayedEntryRemote(BasicNode node) {
    boolean result = false;
    if (followReferrals) {
      if (node == rootNode) {
        result = false;
      }
      else if (node.getRemoteUrl() != null) {
        result = true;
      }
      else {
        BasicNode parent = (BasicNode)node.getParent();
        if (parent != null) {
          result = isDisplayedEntryRemote(parent);
        }
      }
    }

    return result;
  }


  /**
   * Returns the list of attributes for the red search.
   * @return the list of attributes for the red search.
   */
  String[] getAttrsForRedSearch() {
    ArrayList<String> v = new ArrayList<String>();

    v.add("objectClass");
    v.add("numsubordinates");
    v.add("ref");
    if ((displayFlags & DISPLAY_ACI_COUNT) != 0) {
      v.add("aci");
    }
    if (!displayAttribute.equals(RDN_ATTRIBUTE)) {
      v.add(displayAttribute);
    }

    String[] result = new String[v.size()];
    v.toArray(result);
    return result;
  }


  /**
   * Returns the list of attributes for the green search.
   * @return the list of attributes for the green search.
   */
  String[] getAttrsForGreenSearch() {
    if (!displayAttribute.equals(RDN_ATTRIBUTE)) {
      return new String[] {
          "aci",
          displayAttribute};
    } else {
      return new String[] {
          "aci"
      };
    }
  }

  /**
   * Returns the list of attributes for the black search.
   * @return the list of attributes for the black search.
   */
  String[] getAttrsForBlackSearch() {
    if (!displayAttribute.equals(RDN_ATTRIBUTE)) {
      return new String[] {
          "objectClass",
          "numsubordinates",
          "ref",
          "aci",
          displayAttribute};
    } else {
      return new String[] {
          "objectClass",
          "numsubordinates",
          "ref",
          "aci"
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
  Control[] getRequestControls()
  {
    Control ctls[] = new Control[sorted ? 2 : 1];
    ctls[0] = new ManageReferralControl(true);
    if (sorted) {
      SortKey[] keys = new SortKey[SORT_ATTRIBUTES.length];
      for (int i=0; i<keys.length; i++) {
        keys[i] = new SortKey(SORT_ATTRIBUTES[i]);
      }
      try
      {
        ctls[1] = new SortControl(keys, true);
      }
      catch (IOException ioe)
      {
        // Bug
        throw new IllegalStateException("Unexpected encoding exception: "+ioe,
            ioe);
      }
    }
    return ctls;
  }

  /**
   * Returns the request controls to search configuration data.
   * @return the request controls to search configuration data.
   */
  Control[] getConfigurationRequestControls()
  {
    Control ctls[] = new Control[0];
    return ctls;
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
   *
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
      if (isNameNotFoundException(task.getException()) &&
          (oldState != NodeRefresher.State.SOLVING_REFERRAL)) {
        removeOneNode(node);
      }
      else {
        if (oldState == NodeRefresher.State.SOLVING_REFERRAL) {
          node.setRemoteUrl(task.getRemoteUrl());
          if (task.getRemoteEntry() != null) {
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
    else if ((newState == NodeRefresher.State.CANCELLED) &&
        (newState == NodeRefresher.State.INTERRUPTED)) {

      // Let's collapse task.getNode()
      tree.collapsePath(new TreePath(treeModel.getPathToRoot(node)));

      // TODO: should we reflect this situation visually ?
    }
    else {

      if ((oldState != NodeRefresher.State.SEARCHING_CHILDREN) &&
          (newState == NodeRefresher.State.SEARCHING_CHILDREN)) {
        // The children search is going to start
        if (canDoDifferentialUpdate(task)) {
          Enumeration e = node.children();
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
            /* We didn't detect any child: remove the previously existing
             * ones */
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

      if (newState == NodeRefresher.State.FINISHED) {
        if (node.getError() != null) {
          node.setError(null);
          nodeChanged = updateNodeRendering(node, task.getDisplayedEntry());
        }
      }
    }


    if (nodeChanged) {
      treeModel.nodeChanged(task.getNode());
    }

    if (node.isLeaf() && (node.getChildCount() >= 1)) {
      throw new IllegalStateException("Inconsistent node: " + node.getDN());
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
      public void run() {
        try {
          refreshTaskDidProgress(task, oldState, newState);
        }
        catch(Exception x) {
          x.printStackTrace();
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
    ArrayList<Integer> insertIndex = new ArrayList<Integer>();
    ArrayList<Integer> changedIndex = new ArrayList<Integer>();
    boolean differential = canDoDifferentialUpdate(task);

    // NUMSUBORDINATE HACK
    // To avoid testing each child to the hacker,
    // we verify here if the parent node is parent of
    // any entry listed in the hacker.
    // In most case, the dontTrust flag will false and
    // no overhead will be caused in the child loop.
    LDAPURL parentUrl = findUrlForDisplayedEntry(parent);
    boolean dontTrust = numSubordinateHacker.containsChildrenOf(parentUrl);

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
        insertIndex.add(new Integer(index));
//      System.out.println("Inserted " + child.getDN() + " at " + index);
      }
      else { // Else we update the existing one
        child = (BasicNode)parent.getChildAt(index);
        if (updateNodeRendering(child, entry)) {
          changedIndex.add(new Integer(index));
        }
        // The node is no longer obsolete
        child.setObsolete(false);
      }

      // NUMSUBORDINATE HACK
      // Let's see if child has subordinates or not.
      // Thanks to slapd, we cannot always trust the
      // numSubOrdinates attribute. If the child entry's DN
      // is found in the hacker's list, then we ignore
      // the numSubordinate attribute... :((
      int numSubOrdinates = child.getNumSubOrdinates();
      boolean hasNoSubOrdinates;
      if ((numSubOrdinates == 0) && dontTrust) {
        LDAPURL childUrl = findUrlForDisplayedEntry(child);
        if (numSubordinateHacker.contains(childUrl)) {
          // The numSubOrdinates we have is unreliable.
          // child may potentially have subordinates.
          hasNoSubOrdinates = false;
//        System.out.println("numSubordinates of " + childUrl +
//        " is not reliable");
        }
        else {
          // We can trust this 0 value
          hasNoSubOrdinates = true;
        }
      }
      else {
        hasNoSubOrdinates = (numSubOrdinates == 0);
      }



      // Propagate the refresh
      // Note: logically we should unconditionaly call:
      //  startRefreshNode(child, false, true);
      //
      // However doing that saturates _refreshQueue
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
      if (!hasNoSubOrdinates ||
          (child.getReferral() != null) ||
          (child.getChildCount() > 0)) {
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
   * Tells wheter a differential update can be made in the provided task.
   * @param task the task.
   * @return <CODE>true</CODE> if a differential update can be made and
   * <CODE>false</CODE> otherwise.
   */
  private boolean canDoDifferentialUpdate(NodeRefresher task) {
    return (
        (task.getNode().getChildCount() >= 1) &&
        (task.getNode().getNumSubOrdinates() <= 100)
    );
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
      // Get the numsubordinates
      node.setNumSubOrdinates(getNumSubOrdinates(entry));
      node.setReferral(getReferral(entry));
      Set<String> ocValues = ConnectionUtils.getValues(entry, "objectClass");
      if (ocValues != null) {
        String[] array = new String[ocValues.size()];
        ocValues.toArray(array);
        node.setObjectClassValues(array);
      }
    }
    // Get the aci count
    int aciCount;

    if (((displayFlags & DISPLAY_ACI_COUNT) != 0) && (entry != null)) {
      Set<String> aciValues = ConnectionUtils.getValues(entry, "aci");
      if (aciValues != null) {
        aciCount = aciValues.size();
      }
      else {
        aciCount = 0;
      }
    }
    else {
      aciCount = 0;
    }

    // Select the icon according the objectClass,...
    int modifiers = 0;
    if (node.isLeaf() && (node.getNumSubOrdinates() <= 0)) {
      modifiers |= IconPool.MODIFIER_LEAF;
    }
    if (node.getReferral() != null) {
      modifiers |= IconPool.MODIFIER_REFERRAL;
    }
    if (node.getError() != null) {
      if (node.getError().getException() != null)
      {
        node.getError().getException().printStackTrace();
      }
      modifiers |= IconPool.MODIFIER_ERROR;
    }
    SortedSet<String> objectClasses = new TreeSet<String>();
    if (entry != null) {
      Set<String> ocs = ConnectionUtils.getValues(entry, "objectClass");
      if (ocs != null)
      {
        objectClasses.addAll(ocs);
      }
    }
    Icon newIcon;
    if (node instanceof SuffixNode)
    {
      newIcon = iconPool.getSuffixIcon();
    }
    else
    {
      newIcon = iconPool.getIcon(objectClasses, modifiers);
    }

    // Contruct the icon text according the dn, the aci count...
    StringBuilder sb2 = new StringBuilder();
    if (aciCount >= 1) {
      sb2.append(String.valueOf(aciCount));
      if (aciCount == 1) {
        sb2.append(" aci");
      }
      else {
        sb2.append(" acis");
      }
    }

    StringBuilder sb1 = new StringBuilder();
    if (node instanceof SuffixNode) {
      if (entry != null) {
        sb1.append(entry.getName());
      }
    } else {
      boolean useRdn = true;
      if (!displayAttribute.equals(RDN_ATTRIBUTE) &&
          (entry != null)) {
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
        if (followReferrals && (node.getRemoteUrl() != null)) {
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
    boolean changed = (
        (node.getIcon() != newIcon) ||
        (node.getDisplayName() != newDisplayName) ||
        (node.getFontStyle() != newStyle)
    );
    if (changed) {
      node.setIcon(newIcon);
      node.setDisplayName(newDisplayName);
      node.setFontStyle(newStyle);
    }


    return changed;
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
    while ((i < childCount) &&
        !childDn.equals(((BasicNode)parent.getChildAt(i)).getDN())) {
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
      if ((queueTotalSize == 0) && (newSize >= 1)) {
        fireEvent(BrowserEvent.Type.UPDATE_START);
      }
      else if ((queueTotalSize >= 1) && (newSize == 0)) {
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
   */
  SuffixNode findSuffixNode(String suffixDn, SuffixNode suffixNode) {
    SuffixNode result;

    if (Utilities.areDnsEqual(suffixNode.getDN(), suffixDn)) {
      result = suffixNode;
    }
    else {
      int childCount = suffixNode.getChildCount();
      if (childCount == 0) {
        result = null;
      }
      else {
        BasicNode child;
        int i = 0;
        boolean found = false;
        do {
          child = (BasicNode)suffixNode.getChildAt(i) ;
          if (Utilities.areDnsEqual(child.getDN(), suffixDn)) {
            found = true;
          }
          i++;
        }
        while ((i < childCount) && !found);
        if (!found) {
          result = null;
        }
        else if (child instanceof SuffixNode) {
          result = (SuffixNode)child;
        }
        else {
          // A node matches suffixDn however it's not a suffix node.
          // There's a bug in the caller.
          throw new IllegalArgumentException(suffixDn +" is not a suffix node");
        }
      }
    }

    return result;
  }



  /**
   * Return <CODE>true</CODE> if x is a non <code>null</code>
   * NameNotFoundException.
   * @return <CODE>true</CODE> if x is a non <code>null</code>
   * NameNotFoundException.
   */
  private boolean isNameNotFoundException(Object x) {
    boolean result;
    if ((x != null) && (x instanceof NameNotFoundException))
    {
      result = true;
    }
    else {
      result = false;
    }
    return result;
  }



  /**
   * Get the value of the numsubordinates attribute.
   * If numsubordinates is not present, returns 0.
   * @param entry the entry to analyze.
   * @throws NamingException if an error occurs.
   * @return the value of the numsubordinate attribute.  0 if the attribute
   * could not be found.
   */
  public static int getNumSubOrdinates(SearchResult entry)
  throws NamingException
  {
    int result;

    String v = ConnectionUtils.getFirstValue(entry, "numsubordinates");
    if (v == null) {
      result = 0;
    }
    else {
      try {
        result = Integer.parseInt(v);
      }
      catch(NumberFormatException x) {
        result = 0;
      }
    }

    return result;
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
    Set<String> values = ConnectionUtils.getValues(entry, "objectClass");
    if (values != null) {
      for (String value : values)
      {
        boolean isReferral = value.equalsIgnoreCase("referral");
        if (isReferral)
        {
          Set<String> refValues = ConnectionUtils.getValues(entry, "ref");
          if (refValues != null)
          {
            result = new String[refValues.size()];
            refValues.toArray(result);
            break;
          }
        }
        break;
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



  /**
   * Collection utilities
   */
  /**
   * Returns an array of integer from a Collection of Integer objects.
   * @param v the Collection of Integer objects.
   * @return an array of int from a Collection of Integer objects.
   */
  static int[] intArrayFromCollection(Collection<Integer> v) {
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
   * Returns an array of SearchResult from a Collection of SearchResult objects.
   * @param v the Collection of SearchResult objects.
   * @return an array of SearchResult from a Collection of SearchResult objects.
   */
  static SearchResult[] entryArrayFromCollection(Collection<SearchResult> v) {
    SearchResult[] result = new SearchResult[v.size()];
    v.toArray(result);
    return result;
  }

  /**
   * Returns an array of BasicNode from a Collection of BasicNode objects.
   * @param v the Collection of BasicNode objects.
   * @return an array of BasicNode from a Collection of BasicNode objects.
   */
  static BasicNode[] nodeArrayFromCollection(Collection<BasicNode> v) {
    BasicNode[] result = new BasicNode[v.size()];
    v.toArray(result);
    return result;
  }



  /**
   * For debugging purpose: allows to switch easily
   * between invokeLater() and invokeAndWait() for
   * experimentation...
   * @param r the runnable to be invoked.
   * @throws InterruptedException if there is an error invoking SwingUtilities.
   */
  static void swingInvoke(Runnable r)
  throws InterruptedException {
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


  /**
   * The default implementaion of the BrowserNodeInfo interface.
   */
  class BrowserNodeInfoImpl implements BrowserNodeInfo
  {
    BasicNode node;
    LDAPURL url;
    boolean isRemote;
    boolean isSuffix;
    boolean isRootNode;
    String[] referral;
    int numSubOrdinates;
    int errorType;
    Exception errorException;
    Object errorArg;
    String[] objectClassValues;
    String toString;

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
    public BasicNode getNode() {
      return node;
    }

    /**
     * Returns the LDAP URL associated with this object.
     * @return the LDAP URL associated with this object.
     */
    public LDAPURL getURL() {
      return url;
    }

    /**
     * Tells whether this is a root node or not.
     * @return <CODE>true</CODE> if this is a root node and <CODE>false</CODE>
     * otherwise.
     */
    public boolean isRootNode() {
      return isRootNode;
    }

    /**
     * Tells whether this is a suffix node or not.
     * @return <CODE>true</CODE> if this is a suffix node and <CODE>false</CODE>
     * otherwise.
     */
    public boolean isSuffix() {
      return isSuffix;
    }

    /**
     * Tells whether this is a remote node or not.
     * @return <CODE>true</CODE> if this is a remote node and <CODE>false</CODE>
     * otherwise.
     */
    public boolean isRemote() {
      return isRemote;
    }

    /**
     * Returns the list of referral associated with this node.
     * @return the list of referral associated with this node.
     */
    public String[] getReferral() {
      return referral;
    }

    /**
     * Returns the number of subordinates of the entry associated with this
     * node.
     * @return the number of subordinates of the entry associated with this
     * node.
     */
    public int getNumSubOrdinates() {
      return numSubOrdinates;
    }

    /**
     * Returns the error type associated we got when refreshing the node.
     * <CODE>null</CODE> if no error was found.
     * @return the error type associated we got when refreshing the node.
     * <CODE>null</CODE> if no error was found.
     */
    public int getErrorType() {
      return errorType;
    }

    /**
     * Returns the exception associated we got when refreshing the node.
     * <CODE>null</CODE> if no exception was found.
     * @return the exception associated we got when refreshing the node.
     * <CODE>null</CODE> if no exception was found.
     */
    public Exception getErrorException() {
      return errorException;
    }

    /**
     * Returns the error argument associated we got when refreshing the node.
     * <CODE>null</CODE> if no error argument was found.
     * @return the error argument associated we got when refreshing the node.
     * <CODE>null</CODE> if no error argument was found.
     */
    public Object getErrorArg() {
      return errorArg;
    }

    /**
     * Return the tree path associated with the node in the tree.
     * @return the tree path associated with the node in the tree.
     */
    public TreePath getTreePath() {
      return new TreePath(treeModel.getPathToRoot(node));
    }

    /**
     * Returns the object class values of the entry associated with the node.
     * @return the object class values of the entry associated with the node.
     */
    public String[] getObjectClassValues() {
      return objectClassValues;
    }

    /**
     * Returns a String representation of the object.
     * @return a String representation of the object.
     */
    public String toString() {
      return toString;
    }

    /**
     * Compares the provide node with this object.
     * @param node the node.
     * @return <CODE>true</CODE> if the node info represents the same node as
     * this and <CODE>false</CODE> otherwise.
     */
    public boolean representsSameNode(BrowserNodeInfo node) {
      boolean representsSameNode = false;
      if (node != null) {
        representsSameNode = node.getNode() == node;
      }
      return representsSameNode;
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
