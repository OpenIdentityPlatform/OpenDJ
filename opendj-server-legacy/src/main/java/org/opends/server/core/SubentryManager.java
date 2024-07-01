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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackendInitializationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DITCacheMap;
import org.opends.server.api.SubentryChangeListener;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginResult.PostOperation;
import org.opends.server.api.plugin.PluginResult.PreOperation;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.controls.SubentriesControl;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SubEntry;
import org.opends.server.types.SubtreeSpecification;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PostSynchronizationDeleteOperation;
import org.opends.server.types.operation.PostSynchronizationModifyDNOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class provides a mechanism for interacting with subentries defined in
 * the Directory Server.  It will handle all necessary processing at server
 * startup to identify and load subentries within the server.
 * <BR><BR>
 * FIXME:  At the present time, it assumes that all of the necessary
 * information about subentries defined in the server can be held in
 * memory.  If it is determined that this approach is not workable
 * in all cases, then we will need an alternate strategy.
 */
public class SubentryManager extends InternalDirectoryServerPlugin
        implements LocalBackendInitializationListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Dummy configuration DN for Subentry Manager. */
  private static final String CONFIG_DN = "cn=Subentry Manager,cn=config";

  /** A mapping between the DNs and applicable subentries. */
  private final Map<DN, List<SubEntry>> dn2SubEntry = new HashMap<>();
  /** A mapping between the DNs and applicable collective subentries. */
  private final Map<DN, List<SubEntry>> dn2CollectiveSubEntry = new HashMap<>();
  /** A mapping between subentry DNs and subentry objects. */
  private final DITCacheMap<SubEntry> dit2SubEntry = new DITCacheMap<>();
  /** Internal search all operational attributes. */
  private final Set<String> requestAttrs = newLinkedHashSet("*", "+");
  /** Lock to protect internal data structures. */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  /** The set of change notification listeners. */
  private final List<SubentryChangeListener> changeListeners = new CopyOnWriteArrayList<>();

  /**
   * Creates a new instance of this subentry manager.
   *
   * @throws DirectoryException If a problem occurs while
   *                            creating an instance of
   *                            the subentry manager.
   */
  public SubentryManager() throws DirectoryException
  {
    super(DN.valueOf(CONFIG_DN), EnumSet.of(
          PluginType.PRE_OPERATION_ADD,
          PluginType.PRE_OPERATION_DELETE,
          PluginType.PRE_OPERATION_MODIFY,
          PluginType.PRE_OPERATION_MODIFY_DN,
          PluginType.POST_OPERATION_ADD,
          PluginType.POST_OPERATION_DELETE,
          PluginType.POST_OPERATION_MODIFY,
          PluginType.POST_OPERATION_MODIFY_DN,
          PluginType.POST_SYNCHRONIZATION_ADD,
          PluginType.POST_SYNCHRONIZATION_DELETE,
          PluginType.POST_SYNCHRONIZATION_MODIFY,
          PluginType.POST_SYNCHRONIZATION_MODIFY_DN),
          true);

    DirectoryServer.registerInternalPlugin(this);
    DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
      .registerLocalBackendInitializationListener(this);
  }

  /**
   * Perform any required finalization tasks for Subentry Manager.
   * This should only be called at Directory Server shutdown.
   */
  public void finalizeSubentryManager()
  {
    // Deregister as internal plugin and
    // backend initialization listener.
    DirectoryServer.deregisterInternalPlugin(this);
    DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
      .deregisterLocalBackendInitializationListener(this);
  }

  /**
   * Registers the provided change notification listener with this manager
   * so that it will be notified of any add, delete, modify, or modify DN
   * operations that are performed.
   *
   * @param  changeListener  The change notification listener to register
   *                         with this manager.
   */
  public void registerChangeListener(SubentryChangeListener changeListener)
  {
    changeListeners.add(changeListener);
  }

  /**
   * Deregisters the provided change notification listener with this manager
   * so that it will no longer be notified of any add, delete, modify, or
   * modify DN operations that are performed.
   *
   * @param  changeListener  The change notification listener to deregister
   *                         with this manager.
   */
  public void deregisterChangeListener(SubentryChangeListener changeListener)
  {
    changeListeners.remove(changeListener);
  }

  /**
   * Add a given entry to this subentry manager.
   * @param entry to add.
   */
  private void addSubentry(Entry entry) throws DirectoryException
  {
    SubEntry subEntry = new SubEntry(entry);
    SubtreeSpecification subSpec = subEntry.getSubTreeSpecification();
    DN subDN = subSpec.getBaseDN();
    lock.writeLock().lock();
    try
    {
      Map<DN, List<SubEntry>> subEntryMap = getSubEntryMap(subEntry);
      List<SubEntry> subList = subEntryMap.get(subDN);
      if (subList == null)
      {
        subList = new ArrayList<>();
        subEntryMap.put(subDN, subList);
      }
      dit2SubEntry.put(entry.getName(), subEntry);
      subList.add(subEntry);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private Map<DN, List<SubEntry>> getSubEntryMap(SubEntry subEntry)
  {
    return (subEntry.isCollective() || subEntry.isInheritedCollective()) ? dn2CollectiveSubEntry : dn2SubEntry;
  }

  /**
   * Remove a given entry from this subentry manager.
   *
   * @param entry
   *          to remove.
   */
  private void removeSubentry(Entry entry)
  {
    lock.writeLock().lock();
    try
    {
      if (!removeSubEntry(dn2SubEntry, entry))
      {
        removeSubEntry(dn2CollectiveSubEntry, entry);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private boolean removeSubEntry(Map<DN, List<SubEntry>> subEntryMap, Entry entry)
  {
    Iterator<List<SubEntry>> subEntryListsIt = subEntryMap.values().iterator();
    while (subEntryListsIt.hasNext())
    {
      List<SubEntry> subEntries = subEntryListsIt.next();
      Iterator<SubEntry> subEntriesIt = subEntries.iterator();
      while (subEntriesIt.hasNext())
      {
        SubEntry subEntry = subEntriesIt.next();
        if (subEntry.getDN().equals(entry.getName()))
        {
          dit2SubEntry.remove(entry.getName());
          subEntriesIt.remove();
          if (subEntries.isEmpty())
          {
            subEntryListsIt.remove();
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * {@inheritDoc}  In this case, the server will search the backend to find
   * all subentries that it may contain and register them with this manager.
   */
  @Override
  public void performBackendPreInitializationProcessing(LocalBackend<?> backend)
  {
    InternalClientConnection conn = getRootConnection();
    SubentriesControl control = new SubentriesControl(true, true);

    SearchFilter filter = null;
    try
    {
      filter = SearchFilter.createFilterFromString("(|" +
            "(" + ATTR_OBJECTCLASS + "=" + OC_SUBENTRY + ")" +
            "(" + ATTR_OBJECTCLASS + "=" + OC_LDAP_SUBENTRY + ")" +
            ")");
      if (backend.getEntryCount() > 0 && ! backend.isIndexed(filter))
      {
        logger.warn(WARN_SUBENTRY_FILTER_NOT_INDEXED, filter, backend.getBackendID());
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    for (DN baseDN : backend.getBaseDNs())
    {
      try
      {
        if (! backend.entryExists(baseDN))
        {
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
          .addAttribute(requestAttrs)
          .addControl(control);
      InternalSearchOperation internalSearch =
          new InternalSearchOperation(conn, nextOperationID(), nextMessageID(), request);
      LocalBackendSearchOperation localSearch = new LocalBackendSearchOperation(internalSearch);

      try
      {
        backend.search(localSearch);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      for (SearchResultEntry entry : internalSearch.getSearchEntries())
      {
        if (isSubEntry(entry))
        {
          try
          {
            addSubentry(entry);
            notifySubentryAdded(entry);
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
      }
    }
  }

  private void notifySubentryAdded(final Entry entry)
  {
    for (SubentryChangeListener changeListener : changeListeners)
    {
      try
      {
        changeListener.handleSubentryAdd(entry);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  private void notifySubentryDeleted(final Entry entry)
  {
    for (SubentryChangeListener changeListener : changeListeners)
    {
      try
      {
        changeListener.handleSubentryDelete(entry);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  private void notifySubentryModified(final Entry oldEntry, final Entry newEntry)
  {
    for (SubentryChangeListener changeListener : changeListeners)
    {
      try
      {
        changeListener.handleSubentryModify(oldEntry, newEntry);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Return all subentries for this manager.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @return all subentries for this manager.
   */
  public List<SubEntry> getSubentries()
  {
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<>();

    lock.readLock().lock();
    try
    {
      for (List<SubEntry> subList : dn2SubEntry.values())
      {
        subentries.addAll(subList);
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * Return subentries applicable to specific DN.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @param  dn for which to retrieve applicable subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getSubentries(DN dn)
  {
    return getSubentries(dn2SubEntry, dn);
  }

  private List<SubEntry> getSubentries(Map<DN, List<SubEntry>> subEntryMap, DN dn)
  {
    if (subEntryMap.isEmpty())
    {
      return Collections.emptyList();
    }

    lock.readLock().lock();
    try
    {
      List<SubEntry> subentries = new ArrayList<>();
      for (DN subDN = dn; subDN != null && !subDN.isRootDN(); subDN = subDN.parent())
      {
        List<SubEntry> subList = subEntryMap.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec = subEntry.getSubTreeSpecification();
            if (subSpec.isDNWithinScope(dn))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
      return subentries;
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /**
   * Return subentries applicable to specific entry.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @param  entry for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getSubentries(Entry entry)
  {
    return getSubentries(dn2SubEntry, entry);
  }

  private List<SubEntry> getSubentries(Map<DN, List<SubEntry>> subEntryMap, Entry entry)
  {
    if (subEntryMap.isEmpty())
    {
      return Collections.emptyList();
    }

    lock.readLock().lock();
    try
    {
      List<SubEntry> subentries = new ArrayList<>();
      for (DN subDN = entry.getName(); subDN != null && !subDN.isRootDN(); subDN = subDN.parent())
      {
        List<SubEntry> subList = subEntryMap.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            SubtreeSpecification subSpec = subEntry.getSubTreeSpecification();
            if (subSpec.isWithinScope(entry))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
      return subentries;
    }
    finally
    {
      lock.readLock().unlock();
    }
  }

  /**
   * Return collective subentries applicable to specific DN.
   * Note that this getter will skip any regular subentries,
   * returning only applicable collective subentries.
   * @param  dn for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getCollectiveSubentries(DN dn)
  {
    return getSubentries(dn2CollectiveSubEntry, dn);
  }

  /**
   * Return collective subentries applicable to specific entry.
   * Note that this getter will skip any regular subentries,
   * returning only applicable collective subentries.
   * @param  entry for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getCollectiveSubentries(Entry entry)
  {
    return getSubentries(dn2CollectiveSubEntry, entry);
  }

  /**
   * {@inheritDoc}  In this case, the server will de-register
   * all subentries associated with the provided backend.
   */
  @Override
  public void performBackendPostFinalizationProcessing(LocalBackend<?> backend)
  {
    lock.writeLock().lock();
    try
    {
      performBackendPostFinalizationProcessing(dn2SubEntry, backend);
      performBackendPostFinalizationProcessing(dn2CollectiveSubEntry, backend);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private void performBackendPostFinalizationProcessing(Map<DN, List<SubEntry>> subEntryMap, LocalBackend<?> backend)
  {
    Iterator<List<SubEntry>> subEntryListsIt = subEntryMap.values().iterator();
    while (subEntryListsIt.hasNext())
    {
      List<SubEntry> subEntryList = subEntryListsIt.next();
      Iterator<SubEntry> subEntriesIt = subEntryList.iterator();
      while (subEntriesIt.hasNext())
      {
        SubEntry subEntry = subEntriesIt.next();
        if (backend.handlesEntry(subEntry.getDN()))
        {
          dit2SubEntry.remove(subEntry.getDN());
          subEntriesIt.remove();
          notifySubentryDeleted(subEntry.getEntry());
        }
      }
      if (subEntryList.isEmpty())
      {
        subEntryListsIt.remove();
      }
    }
  }

  @Override
  public void performBackendPostInitializationProcessing(LocalBackend<?> backend) {
    // Nothing to do.
  }

  @Override
  public void performBackendPreFinalizationProcessing(LocalBackend<?> backend) {
    // Nothing to do.
  }

  private void doPostAdd(Entry entry)
  {
    if (isSubEntry(entry))
    {
      lock.writeLock().lock();
      try
      {
        try
        {
          addSubentry(entry);
          notifySubentryAdded(entry);
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
      finally
      {
        lock.writeLock().unlock();
      }
    }
  }

  private void doPostDelete(Entry entry)
  {
    // Fast-path for deleted entries which do not have subordinate sub-entries.
    lock.readLock().lock();
    try
    {
      final Collection<SubEntry> subtree = dit2SubEntry.getSubtree(entry.getName());
      if (subtree.isEmpty())
      {
        return;
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    // Slow-path.
    lock.writeLock().lock();
    try
    {
      for (SubEntry subEntry : dit2SubEntry.getSubtree(entry.getName()))
      {
        removeSubentry(subEntry.getEntry());
        notifySubentryDeleted(subEntry.getEntry());
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private void doPostModify(Entry oldEntry, Entry newEntry)
  {
    final boolean oldEntryIsSubentry = isSubEntry(oldEntry);
    final boolean newEntryIsSubentry = isSubEntry(newEntry);
    if (!oldEntryIsSubentry && !newEntryIsSubentry)
    {
      return; // Nothing to do.
    }

    boolean notify = false;
    lock.writeLock().lock();
    try
    {
      if (oldEntryIsSubentry)
      {
        removeSubentry(oldEntry);
        notify = true;
      }
      if (newEntryIsSubentry)
      {
        try
        {
          addSubentry(newEntry);
          notify = true;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // FIXME -- Handle this.
        }
      }

      if (notify)
      {
        notifySubentryModified(oldEntry, newEntry);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  private boolean isSubEntry(final Entry e)
  {
    return e.isSubentry() || e.isLDAPSubentry();
  }

  private void doPostModifyDN(final Entry oldEntry, final Entry newEntry)
  {
    lock.writeLock().lock();
    try
    {
      Collection<SubEntry> setToDelete = dit2SubEntry.getSubtree(oldEntry.getName());
      for (SubEntry subentry : setToDelete)
      {
        final Entry currentSubentry = subentry.getEntry();
        removeSubentry(currentSubentry);

        Entry renamedSubentry = null;
        try
        {
          renamedSubentry = currentSubentry.duplicate(false);
          final DN renamedDN = currentSubentry.getName().rename(oldEntry.getName(), newEntry.getName());
          renamedSubentry.setDN(renamedDN);
          addSubentry(renamedSubentry);
        }
        catch (Exception e)
        {
          // Shouldnt happen.
          logger.traceException(e);
        }

        notifySubentryModified(currentSubentry, renamedSubentry);
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  @Override
  public PreOperation doPreOperation(PreOperationAddOperation addOperation)
  {
    Entry entry = addOperation.getEntryToAdd();

    if (isSubEntry(entry))
    {
      ClientConnection conn = addOperation.getClientConnection();
      if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE, conn.getOperationInProgress(addOperation.getMessageID())))
      {
        return PluginResult.PreOperation.stopProcessing(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
      }
      for (SubentryChangeListener changeListener : changeListeners)
      {
        try
        {
          changeListener.checkSubentryAddAcceptable(entry);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          return PluginResult.PreOperation.stopProcessing(de.getResultCode(), de.getMessageObject());
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PreOperation doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    Entry entry = deleteOperation.getEntryToDelete();
    boolean hasSubentryWritePrivilege = false;

    lock.readLock().lock();
    try
    {
      for (SubEntry subEntry : dit2SubEntry.getSubtree(entry.getName()))
      {
        if (!hasSubentryWritePrivilege)
        {
          ClientConnection conn = deleteOperation.getClientConnection();
          if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
                                 conn.getOperationInProgress(deleteOperation.getMessageID())))
          {
            return PluginResult.PreOperation.stopProcessing(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                    ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
          }
          hasSubentryWritePrivilege = true;
        }
        for (SubentryChangeListener changeListener : changeListeners)
        {
          try
          {
            changeListener.checkSubentryDeleteAcceptable(subEntry.getEntry());
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);
            return PluginResult.PreOperation.stopProcessing(de.getResultCode(), de.getMessageObject());
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PreOperation doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    Entry oldEntry = modifyOperation.getCurrentEntry();
    Entry newEntry = modifyOperation.getModifiedEntry();

    if (isSubEntry(newEntry) || isSubEntry(oldEntry))
    {
      ClientConnection conn = modifyOperation.getClientConnection();
      if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
                             conn.getOperationInProgress(modifyOperation.getMessageID())))
      {
        return PluginResult.PreOperation.stopProcessing(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
      }
      for (SubentryChangeListener changeListener : changeListeners)
      {
        try
        {
          changeListener.checkSubentryModifyAcceptable(oldEntry, newEntry);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);
          return PluginResult.PreOperation.stopProcessing(de.getResultCode(), de.getMessageObject());
        }
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PreOperation doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    boolean hasSubentryWritePrivilege = false;

    lock.readLock().lock();
    try
    {
      final Entry oldEntry = modifyDNOperation.getOriginalEntry();
      Collection<SubEntry> setToDelete = dit2SubEntry.getSubtree(oldEntry.getName());
      for (SubEntry subentry : setToDelete)
      {
        if (!hasSubentryWritePrivilege)
        {
          ClientConnection conn = modifyDNOperation.getClientConnection();
          if (!conn.hasPrivilege(Privilege.SUBENTRY_WRITE,
                                 conn.getOperationInProgress(modifyDNOperation.getMessageID())))
          {
            return PluginResult.PreOperation.stopProcessing(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                    ERR_SUBENTRY_WRITE_INSUFFICIENT_PRIVILEGES.get());
          }
          hasSubentryWritePrivilege = true;
        }

        final Entry newEntry = modifyDNOperation.getUpdatedEntry();
        final Entry currentSubentry = subentry.getEntry();
        final Entry renamedSubentry = currentSubentry.duplicate(false);
        final DN renamedDN = currentSubentry.getName().rename(oldEntry.getName(), newEntry.getName());
        renamedSubentry.setDN(renamedDN);

        for (SubentryChangeListener changeListener : changeListeners)
        {
          try
          {
            changeListener.checkSubentryModifyAcceptable(currentSubentry, renamedSubentry);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);
            return PluginResult.PreOperation.stopProcessing(de.getResultCode(), de.getMessageObject());
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(PostOperationAddOperation addOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (addOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostAdd(addOperation.getEntryToAdd());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (deleteOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostDelete(deleteOperation.getEntryToDelete());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModify(modifyOperation.getCurrentEntry(), modifyOperation.getModifiedEntry());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public PostOperation doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    // Only do something if the operation is successful, meaning there
    // has been a change.
    if (modifyDNOperation.getResultCode() == ResultCode.SUCCESS)
    {
      doPostModifyDN(modifyDNOperation.getOriginalEntry(), modifyDNOperation.getUpdatedEntry());
    }

    // If we've gotten here, then everything is acceptable.
    return PluginResult.PostOperation.continueOperationProcessing();
  }

  @Override
  public void doPostSynchronization(PostSynchronizationAddOperation addOperation)
  {
    Entry entry = addOperation.getEntryToAdd();
    if (entry != null)
    {
      doPostAdd(entry);
    }
  }

  @Override
  public void doPostSynchronization(PostSynchronizationDeleteOperation deleteOperation)
  {
    Entry entry = deleteOperation.getEntryToDelete();
    if (entry != null)
    {
      doPostDelete(entry);
    }
  }

  @Override
  public void doPostSynchronization(PostSynchronizationModifyOperation modifyOperation)
  {
    Entry entry = modifyOperation.getCurrentEntry();
    Entry modEntry = modifyOperation.getModifiedEntry();
    if (entry != null && modEntry != null)
    {
      doPostModify(entry, modEntry);
    }
  }

  @Override
  public void doPostSynchronization(PostSynchronizationModifyDNOperation modifyDNOperation)
  {
    Entry oldEntry = modifyDNOperation.getOriginalEntry();
    Entry newEntry = modifyDNOperation.getUpdatedEntry();
    if (oldEntry != null && newEntry != null)
    {
      doPostModifyDN(oldEntry, newEntry);
    }
  }
}
