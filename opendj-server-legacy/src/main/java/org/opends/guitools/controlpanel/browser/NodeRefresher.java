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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.admin.ads.util.ConnectionUtils.*;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreeNode;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.messages.AdminToolMessages;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.HostPort;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.OpenDsException;

/**
 * The class that is in charge of doing the LDAP searches required to update a
 * node: search the local entry, detect if it has children, retrieve the
 * attributes required to render the node, etc.
 */
public class NodeRefresher extends AbstractNodeTask {
  /** The enumeration containing all the states the refresher can have. */
  public enum State
  {
    /** The refresher is queued, but not started. */
    QUEUED,
    /** The refresher is reading the local entry. */
    READING_LOCAL_ENTRY,
    /** The refresher is solving a referral. */
    SOLVING_REFERRAL,
    /** The refresher is detecting whether the entry has children or not. */
    DETECTING_CHILDREN,
    /** The refresher is searching for the children of the entry. */
    SEARCHING_CHILDREN,
    /** The refresher is finished. */
    FINISHED,
    /** The refresher is cancelled. */
    CANCELLED,
    /** The refresher has been interrupted. */
    INTERRUPTED,
    /** The refresher has failed. */
    FAILED
  }

  private final BrowserController controller;
  private State state;
  private final boolean recursive;

  private SearchResultEntry localEntry;
  private SearchResultEntry remoteEntry;
  private LDAPURL remoteUrl;
  private boolean isLeafNode;
  private final List<SearchResultEntry> childEntries = new ArrayList<>();
  private final boolean differential;
  private Exception exception;
  private Object exceptionArg;

  /**
   * The constructor of the refresher object.
   * @param node the node on the tree to be updated.
   * @param ctlr the BrowserController.
   * @param localEntry the local entry corresponding to the node.
   * @param recursive whether this task is recursive or not (children must be searched).
   */
  NodeRefresher(BasicNode node, BrowserController ctlr, SearchResultEntry localEntry, boolean recursive) {
    super(node);
    controller = ctlr;
    state = State.QUEUED;
    this.recursive = recursive;

    this.localEntry = localEntry;
    differential = false;
  }

  /**
   * Returns the local entry the refresher is handling.
   * @return the local entry the refresher is handling.
   */
  public SearchResultEntry getLocalEntry() {
    return localEntry;
  }

  /**
   * Returns the remote entry for the node.  It will be {@code null} if
   * the entry is not a referral.
   * @return the remote entry for the node.
   */
  public SearchResultEntry getRemoteEntry() {
    return remoteEntry;
  }

  /**
   * Returns the URL of the remote entry.  It will be {@code null} if
   * the entry is not a referral.
   * @return the URL of the remote entry.
   */
  public LDAPURL getRemoteUrl() {
    return remoteUrl;
  }

  /**
   * Tells whether the node is a leaf or not.
   * @return {@code true} if the node is a leaf and {@code false} otherwise.
   */
  public boolean isLeafNode() {
    return isLeafNode;
  }

  /**
   * Returns the child entries of the node.
   * @return the child entries of the node.
   */
  public List<SearchResultEntry> getChildEntries() {
    return childEntries;
  }

  /**
   * Returns whether this refresher object is working on differential mode or not.
   * @return {@code true} if the refresher is working on differential
   * mode and {@code false} otherwise.
   */
  public boolean isDifferential() {
    return differential;
  }

  /**
   * Returns the exception that occurred during the processing.  It returns
   * {@code null} if no exception occurred.
   * @return the exception that occurred during the processing.
   */
  public Exception getException() {
    return exception;
  }

  /**
   * Returns the argument of the exception that occurred during the processing.
   * It returns {@code null} if no exception occurred or if the exception
   * has no arguments.
   * @return the argument exception that occurred during the processing.
   */
  public Object getExceptionArg() {
    return exceptionArg;
  }

  /**
   * Returns the displayed entry in the browser.  This depends on the
   * visualization options in the BrowserController.
   * @return the remote entry if the entry is a referral and the
   * BrowserController is following referrals and the local entry otherwise.
   */
  public SearchResultEntry getDisplayedEntry() {
    if (controller.isFollowReferrals() && remoteEntry != null)
    {
      return remoteEntry;
    }
    else {
      return localEntry;
    }
  }

  /**
   * Returns the LDAP URL of the displayed entry in the browser.  This depends
   * on the visualization options in the BrowserController.
   * @return the remote entry LDAP URL if the entry is a referral and the
   * BrowserController is following referrals and the local entry LDAP URL
   * otherwise.
   */
  public LDAPURL getDisplayedUrl() {
    if (controller.isFollowReferrals() && remoteUrl != null)
    {
      return remoteUrl;
    }
    else
    {
      return controller.findUrlForLocalEntry(getNode());
    }
  }

  /**
   * Returns whether the refresh is over or not.
   *
   * @return {@code true} if the refresh is over and {@code false} otherwise.
   */
  public boolean isInFinalState() {
    return state == State.FINISHED || state == State.CANCELLED || state == State.FAILED || state == State.INTERRUPTED;
  }

  /** The method that actually does the refresh. */
  @Override
  public void run() {
    final BasicNode node = getNode();

    try {
      boolean checkExpand = false;
      if (localEntry == null) {
        changeStateTo(State.READING_LOCAL_ENTRY);
        runReadLocalEntry();
      }
      if (!isInFinalState()) {
        if (controller.isFollowReferrals() && isReferralEntry(localEntry)) {
          changeStateTo(State.SOLVING_REFERRAL);
          runSolveReferral();
        }
        if (node.isLeaf()) {
          changeStateTo(State.DETECTING_CHILDREN);
          runDetectChildren();
        }
        if (controller.nodeIsExpanded(node) && recursive) {
          changeStateTo(State.SEARCHING_CHILDREN);
          runSearchChildren();
          /* If the node is not expanded, we have to refresh its children when we expand it */
        } else if (recursive  && (!node.isLeaf() || !isLeafNode)) {
          node.setRefreshNeededOnExpansion(true);
          checkExpand = true;
        }
        changeStateTo(State.FINISHED);
        if (checkExpand && mustAutomaticallyExpand(node))
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              controller.expandNode(node);
            }
          });
        }
      }
    }
    catch (LdapException e)
    {
      exception = e;
      exceptionArg = null;
    }
    catch(SearchAbandonException x) {
      exception = x.getException();
      exceptionArg = x.getArg();
      try {
        changeStateTo(x.getState());
      }
      catch(SearchAbandonException xx) {
        // We've done all what we can...
      }
    }
  }

  /**
   * Tells whether a custom filter is being used (specified by the user in the
   * browser dialog) or not.
   * @return {@code true} if a custom filter is being used and {@code false} otherwise.
   */
  private boolean useCustomFilter()
  {
    return controller.getFilter() != null && !BrowserController.ALL_OBJECTS_FILTER.equals(controller.getFilter());
  }

  /**
   * Performs the search in the case the user specified a custom filter.
   *
   * @param node
   *          the parent node we perform the search from.
   * @param conn
   *          the connection to be used.
   * @throws IOException
   *           if a problem occurred.
   */
  private void searchForCustomFilter(BasicNode node, ConnectionWithControls conn) throws IOException
  {
    SearchRequest request =
        newSearchRequest(node.getDN(), WHOLE_SUBTREE, controller.getFilter(), NO_ATTRIBUTES)
            .setSizeLimit(1);
    try (ConnectionEntryReader s = conn.search(request))
    {
      if (!s.hasNext())
      {
        throw newLdapException(NO_SUCH_OBJECT, "Entry " + node.getDN() +
            " does not verify filter "+controller.getFilter());
      }
      while (s.hasNext())
      {
        s.readEntry();
      }
    }
    catch (LdapException e)
    {
      if (e.getResult().getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED)
      {
        // We are just searching for an entry, but if there is more than one
        // this exception will be thrown. We call sr.hasMore after the
        // first entry has been retrieved to avoid sending a systematic
        // abandon when closing the s NamingEnumeration.
        // See CR 6976906.
      }
      else
      {
        throw e;
      }
    }
  }

  /**
   * Performs the search in the case the user specified a custom filter.
   * @param dn the parent DN we perform the search from.
   * @param conn the connection to be used.
   * @throws IOException if a problem occurred.
   */
  private void searchForCustomFilter(DN dn, ConnectionWithControls conn) throws IOException
  {
    SearchRequest request = newSearchRequest(dn, WHOLE_SUBTREE, controller.getFilter())
        .setSizeLimit(1);
    try (ConnectionEntryReader entryReader = conn.search(request))
    {
      if (!entryReader.hasNext())
      {
        throw LdapException.newLdapException(ResultCode.NO_SUCH_OBJECT, "Entry " + dn +
            " does not verify filter "+controller.getFilter());
      }
      while (entryReader.hasNext())
      {
        entryReader.readEntry();
      }
    }
    catch (LdapException e)
    {
      if (!e.getResult().getResultCode().equals(ResultCode.SIZE_LIMIT_EXCEEDED))
      {
        throw e;
      }
      // We are just searching for an entry, but if there is more than one
      // this exception will be thrown.  We call sr.hasMore after the
      // first entry has been retrieved to avoid sending a systematic
      // abandon when closing the s NamingEnumeration.
      // See CR 6976906.
    }
  }

  /** Read the local entry associated to the current node. */
  private void runReadLocalEntry() throws SearchAbandonException {
    BasicNode node = getNode();
    ConnectionWithControls conn = null;
    try {
      conn = controller.findConnectionForLocalEntry(node);

      if (conn != null) {
        if (useCustomFilter())
        {
          // Check that the entry verifies the filter
          searchForCustomFilter(node, conn);
        }

        SearchRequest request =
            newSearchRequest(node.getDN(), BASE_OBJECT, controller.getObjectSearchFilter(), controller
                .getAttrsForRedSearch()).setSizeLimit(controller.getMaxChildren());
        localEntry = conn.searchSingleEntry(request);
        throwAbandonIfNeeded(null);
      } else {
          changeStateTo(State.FINISHED);
      }
    }
    catch (IOException x) {
      throwAbandonIfNeeded(x);
    }
    finally {
      if (conn != null) {
        controller.releaseLDAPConnection(conn);
      }
    }
  }

  /**
   * Solve the referral associated to the current node.
   * This routine assumes that node.getReferral() is non null
   * and that BrowserController.getFollowReferrals() == true.
   * It also protect the browser against looping referrals by
   * limiting the number of hops.
   * @throws SearchAbandonException if the hop count limit for referrals has
   * been exceeded.
   * @throws LdapException if an error occurred searching the entry.
   */
  private void runSolveReferral() throws SearchAbandonException, LdapException {
    int hopCount = 0;
    String[] referral = getNode().getReferral();
    while (referral != null && hopCount < 10)
    {
      readRemoteEntry(referral);
      referral = BrowserController.getReferral(remoteEntry);
      hopCount++;
    }
    if (referral != null)
    {
      throwAbandonIfNeeded(newLdapException(CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED,
          AdminToolMessages.ERR_REFERRAL_LIMIT_EXCEEDED.get(hopCount)));
    }
  }

  /**
   * Searches for the remote entry.
   * @param referral the referral list to be used to search the remote entry.
   * @throws SearchAbandonException if an error occurs.
   */
  private void readRemoteEntry(String[] referral)
  throws SearchAbandonException {
    LDAPConnectionPool connectionPool = controller.getConnectionPool();
    LDAPURL url = null;
    SearchResultEntry entry = null;
    DN remoteDn = null;
    Exception lastException = null;
    Object lastExceptionArg = null;

    int i = 0;
    while (i < referral.length && entry == null)
    {
      ConnectionWithControls conn = null;
      try {
        url = LDAPURL.decode(referral[i], false);
        if (url.getHost() == null)
        {
          // Use the local server connection.
          ConnectionWrapper userConn = controller.getUserDataConnection().getConnectionWrapper();
          HostPort hostPort = userConn.getHostPort();
          url.setHost(hostPort.getHost());
          url.setPort(hostPort.getPort());
          url.setScheme(userConn.isLdaps() ? "ldaps" : "ldap");
        }
        conn = connectionPool.getConnection(url);
        remoteDn = DN.valueOf(url.getRawBaseDN());
        if (remoteDn == null || "".equals(remoteDn))
        {
          /* The referral has not a target DN specified: we
             have to use the DN of the entry that contains the
             referral... */
          if (remoteEntry != null) {
            remoteDn = remoteEntry.getName();
          } else {
            remoteDn = localEntry.getName();
          }
          /* We have to recreate the url including the target DN we are using */
          url = new LDAPURL(url.getScheme(), url.getHost(), url.getPort(),
              remoteDn.toString(), url.getAttributes(), url.getScope(), url.getRawFilter(),
                 url.getExtensions());
        }
        if (useCustomFilter() && url.getScope() == SearchScope.BASE_OBJECT)
        {
          // Check that the entry verifies the filter
          searchForCustomFilter(remoteDn, conn);
        }

        Filter filter = getFilter(url);

        SearchRequest request =
            newSearchRequest(remoteDn, url.getScope(), filter, controller.getAttrsForBlackSearch()).setSizeLimit(
                controller.getMaxChildren());
        try (ConnectionEntryReader sr = conn.search(request))
        {
          boolean found = false;
          while (sr.hasNext())
          {
            entry = sr.readEntry();
            if (entry.getName().isRootDN())
            {
              entry.setName(remoteDn);
            }
            found = true;
          }
          if (!found)
          {
            throw newLdapException(NO_SUCH_OBJECT);
          }
        }
        catch (LdapException e)
        {
          if (e.getResult().getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED)
          {
            // We are just searching for an entry, but if there is more than one
            // this exception will be thrown. We call sr.hasMore after the
            // first entry has been retrieved to avoid sending a systematic
            // abandon when closing the sr NamingEnumeration.
            // See CR 6976906.
          }
          else
          {
            throw e;
          }
        }
        throwAbandonIfNeeded(null);
      }
      catch (IOException | LocalizedIllegalArgumentException | DirectoryException x)
      {
        lastException = x;
        lastExceptionArg = referral[i];
      }
      finally {
        if (conn != null) {
          connectionPool.releaseConnection(conn);
        }
      }
      i = i + 1;
    }
    if (entry == null) {
      throw new SearchAbandonException(State.FAILED, lastException, lastExceptionArg);
    }

    if (url.getScope() != SearchScope.BASE_OBJECT)
    {
      // The URL is to be transformed: the code assumes that the URL points
      // to the remote entry.
      url = new LDAPURL(url.getScheme(), url.getHost(),
          url.getPort(), entry.getName(), url.getAttributes(),
          SearchScope.BASE_OBJECT, null, url.getExtensions());
    }
    checkLoopInReferral(url, referral[i-1]);
    remoteUrl = url;
    remoteEntry = entry;
  }

  /**
   * Tells whether the provided node must be automatically expanded or not.
   * This is used when the user provides a custom filter, in this case we
   * expand automatically the tree.
   * @param node the node to analyze.
   * @return {@code true} if the node must be expanded and {@code false} otherwise.
   */
  private boolean mustAutomaticallyExpand(BasicNode node)
  {
    boolean mustAutomaticallyExpand = false;
    if (controller.isAutomaticExpand())
    {
      // Limit the number of expansion levels to 3
      int nLevels = 0;
      TreeNode parent = node;
      while (parent != null)
      {
        nLevels ++;
        parent = parent.getParent();
      }
      mustAutomaticallyExpand = nLevels <= 4;
    }
    return mustAutomaticallyExpand;
  }

  /**
   * Detects whether the entries has children or not.
   * @throws SearchAbandonException if the search was abandoned.
   * @throws LdapException if an error during the search occurred.
   */
  private void runDetectChildren() throws SearchAbandonException, LdapException {
    if (controller.isShowContainerOnly() || !isNumSubOrdinatesUsable()) {
      runDetectChildrenManually();
    }
    else {
      SearchResultEntry entry = getDisplayedEntry();
      isLeafNode = !BrowserController.getHasSubOrdinates(entry);
    }
  }

  /**
   * Detects whether the entry has children by performing a search using the
   * entry as base DN.
   * @throws SearchAbandonException if there is an error.
   */
  private void runDetectChildrenManually() throws SearchAbandonException {
    BasicNode parentNode = getNode();
    ConnectionWithControls conn = null;

    try {
      // We set the search constraints so that only one entry is returned.
      // It's enough to know if the entry has children or not.
      conn = controller.findConnectionForDisplayedEntry(parentNode);
      SearchRequest request = newSearchRequest(
          controller.findBaseDNForChildEntries(parentNode),
          useCustomFilter() ? WHOLE_SUBTREE : BASE_OBJECT,
          controller.getChildSearchFilter(),
          NO_ATTRIBUTES)
          .setSizeLimit(1);
      try (ConnectionEntryReader searchResults = conn.search(request))
      {
        throwAbandonIfNeeded(null);
        // Check if parentNode has children
        isLeafNode = !searchResults.hasNext();
      }
    }
    catch (LdapException e)
    {
      if (e.getResult().getResultCode().equals(ResultCode.SIZE_LIMIT_EXCEEDED))
      {
        // We are just searching for an entry, but if there is more than one
        // this exception will be thrown. We call sr.hasMore after the
        // first entry has been retrieved to avoid sending a systematic
        // abandon when closing the searchResults NamingEnumeration.
        // See CR 6976906.
      }
      else
      {
        throwAbandonIfNeeded(e);
      }
    }
    finally {
      if (conn != null) {
        controller.releaseLDAPConnection(conn);
      }
    }
  }

  /**
   * NUMSUBORDINATE HACK
   * numsubordinates is not usable if the displayed entry
   * is listed in in the hacker.
   * Note: *usable* means *usable for detecting children presence*.
   */
  private boolean isNumSubOrdinatesUsable() throws LdapException {
    SearchResultEntry entry = getDisplayedEntry();
    boolean hasSubOrdinates = BrowserController.getHasSubOrdinates(entry);
    if (!hasSubOrdinates)
    {
      LDAPURL url = getDisplayedUrl();
      return !controller.getNumSubordinateHacker().contains(url);
    }
    // Other values are usable
    return true;
  }

  /**
   * Searches for the children.
   * @throws SearchAbandonException if an error occurs.
   */
  private void runSearchChildren() throws SearchAbandonException {
    ConnectionWithControls conn = null;
    BasicNode parentNode = getNode();
    parentNode.setSizeLimitReached(false);

    try {
      // Send an LDAP search
      conn = controller.findConnectionForDisplayedEntry(parentNode);
      DN parentDn = controller.findBaseDNForChildEntries(parentNode);
      int parentComponents = parentDn.size();

      SearchRequest request = newSearchRequest(
          parentDn,
          useCustomFilter() ? WHOLE_SUBTREE : SINGLE_LEVEL,
          controller.getChildSearchFilter(),
          controller.getAttrsForRedSearch())
          .setSizeLimit(controller.getMaxChildren());

      try (ConnectionEntryReader entries = conn.search(request))
      {
        while (entries.hasNext())
        {
          SearchResultEntry r = entries.readEntry();
          if (r.getName().isRootDN())
          {
            continue;
          }

          boolean add = false;
          if (useCustomFilter())
          {
            // Check that is an immediate child: use a faster method by just
            // comparing the number of components.
            final DN dn = r.getName();
            add = dn.size() == parentComponents + 1;
            if (!add)
            {
              // Is not a direct child.  Check if the parent has been added,
              // if it is the case, do not add the parent.  If is not the case,
              // search for the parent and add it.
              RDN[] rdns = new RDN[parentComponents + 1];
              final DN parentToAddDN = dn.parent(dn.size() - rdns.length);
              boolean mustAddParent = mustAddParent(parentToAddDN) && mustAddParent2(parentToAddDN);
              if (mustAddParent)
              {
                SearchResultEntry parentResult = searchManuallyEntry(conn, parentToAddDN);
                childEntries.add(parentResult);
              }
            }
          }
          else
          {
            add = true;
          }
          if (add)
          {
            childEntries.add(r);
            // Time to time we update the display
            if (childEntries.size() >= 20) {
              changeStateTo(State.SEARCHING_CHILDREN);
              childEntries.clear();
            }
          }
          throwAbandonIfNeeded(null);
        }
      }
    }
    catch (LdapException e)
    {
      if (e.getResult().getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED)
      {
        parentNode.setSizeLimitReached(true);
      }
      else
      {
        throwAbandonIfNeeded(e);
      }
    }
    catch (IOException e)
    {
      throwAbandonIfNeeded(e);
    }
    finally {
      if (conn != null)
      {
        controller.releaseLDAPConnection(conn);
      }
    }
  }

  private boolean mustAddParent2(final DN parentToAddDN)
  {
    final boolean resultValue[] = {true};
    // Check the children added to the tree
    try
    {
      SwingUtilities.invokeAndWait(new Runnable()
      {
        @Override
        public void run()
        {
          for (int i=0; i<getNode().getChildCount(); i++)
          {
            BasicNode node = (BasicNode)getNode().getChildAt(i);
            if (node.getDN().equals(parentToAddDN))
            {
              resultValue[0] = false;
              break;
            }
          }
        }
      });
    }
    catch (Throwable t)
    {
      // Ignore
    }
    return resultValue[0];
  }

  private boolean mustAddParent(final DN parentToAddDN)
  {
    for (SearchResultEntry addedEntry : childEntries)
    {
      try
      {
        if (addedEntry.getName().equals(parentToAddDN))
        {
          return false;
        }
      }
      catch (Throwable t)
      {
        throw new RuntimeException("Error decoding dn: " + addedEntry.getName() + " . " + t, t);
      }
    }
    return true;
  }

  /**
   * Returns the entry for the given dn.
   * The code assumes that the request controls are set in the connection.
   * @param conn the connection to be used.
   * @param dn the DN of the entry to be searched.
   * @throws LdapException if an error occurs.
   */
  private SearchResultEntry searchManuallyEntry(ConnectionWithControls conn, DN dn) throws LdapException
  {
    SearchRequest request =
        newSearchRequest(dn, BASE_OBJECT, controller.getObjectSearchFilter(), controller.getAttrsForRedSearch())
            .setSizeLimit(controller.getMaxChildren());

    SearchResultEntry sr = conn.searchSingleEntry(request);
    sr.setName(dn);
    return sr;
  }

  /** Utilities. */

  /**
   * Change the state of the task and inform the BrowserController.
   * @param newState the new state for the refresher.
   */
  private void changeStateTo(State newState) throws SearchAbandonException {
    State oldState = state;
    state = newState;
    try {
      controller.invokeRefreshTaskDidProgress(this, oldState, newState);
    }
    catch(InterruptedException x) {
      throwAbandonIfNeeded(x);
    }
  }

  /**
   * Transform an exception into a TaskAbandonException.
   * If no exception is passed, the routine checks if the task has
   * been canceled and throws an TaskAbandonException accordingly.
   * @param x the exception.
   * @throws SearchAbandonException if the task/refresher must be abandoned.
   */
  private void throwAbandonIfNeeded(Exception x) throws SearchAbandonException {
    if (x != null) {
      if (x instanceof InterruptedException)
      {
        throw new SearchAbandonException(State.INTERRUPTED, x, null);
      }
      throw new SearchAbandonException(State.FAILED, x, null);
    }
    else if (isCanceled()) {
      throw new SearchAbandonException(State.CANCELLED, null, null);
    }
  }

  /** DEBUG : Dump the state of the task. */
  void dump() {
    System.out.println("=============");
    System.out.println("         node: " + getNode().getDN());
    System.out.println("    recursive: " + recursive);
    System.out.println(" differential: " + differential);

    System.out.println("        state: " + state);
    System.out.println("   localEntry: " + localEntry);
    System.out.println("  remoteEntry: " + remoteEntry);
    System.out.println("    remoteUrl: " + remoteUrl);
    System.out.println("   isLeafNode: " + isLeafNode);
    System.out.println("    exception: " + exception);
    System.out.println(" exceptionArg: " + exceptionArg);
    System.out.println("=============");
  }

  /**
   * Checks that the entry's objectClass contains 'referral' and that the
   * attribute 'ref' is present.
   * @param entry the search result.
   * @return {@code true} if the entry's objectClass contains 'referral'
   * and the attribute 'ref' is present and {@code false} otherwise.
   */
  private static boolean isReferralEntry(SearchResultEntry entry)
  {
    for (String value : entry.parseAttribute("objectClass").asSetOfString())
    {
      if ("referral".equalsIgnoreCase(value))
      {
        return firstValueAsString(entry, "ref") != null;
      }
    }
    return false;
  }

  /**
   * Returns the filter to be used in a LDAP request based on the information of an LDAP URL.
   *
   * @param url
   *          the LDAP URL.
   * @return the filter.
   */
  private Filter getFilter(LDAPURL url)
  {
    String filter = url.getRawFilter();
    return filter != null ? Filter.valueOf(filter) : controller.getObjectSearchFilter();
  }

  /**
   * Check that there is no loop in terms of DIT (the check basically identifies
   * whether we are pointing to an entry above in the same server).
   * @param url the URL to the remote entry.  It is assumed that the base DN
   * of the URL points to the remote entry.
   * @param referral the referral used to retrieve the remote entry.
   * @throws SearchAbandonException if there is a loop issue (the remoteEntry
   * is actually an entry in the same server as the local entry but above in the
   * DIT).
   */
  private void checkLoopInReferral(LDAPURL url, String referral) throws SearchAbandonException
  {
    try
    {
      if (url.getBaseDN().isSuperiorOrEqualTo(getNode().getDN()))
      {
        HostPort hp = new HostPort(url.getHost(), url.getPort());
        boolean checkSucceeded =
            hp.equals(controller.getConfigurationConnection().getConnectionWrapper().getHostPort())
            && hp.equals(controller.getUserDataConnection().getConnectionWrapper().getHostPort());
        if (!checkSucceeded)
        {
          LdapException cause = newLdapException(CLIENT_SIDE_REFERRAL_LIMIT_EXCEEDED,
              ERR_CTRL_PANEL_REFERRAL_LOOP.get(url.getRawBaseDN()));
          throw new SearchAbandonException(State.FAILED, cause, referral);
        }
      }
    }
    catch (OpenDsException ignore)
    {
      // Ignore
    }
  }
}
