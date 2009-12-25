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
package org.opends.server.core;



import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.controls.SubentriesControl;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SubEntry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import org.opends.server.workflowelement.localbackend.
            LocalBackendSearchOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.config.ConfigConstants.*;



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
public class SubentryManager
        implements BackendInitializationListener, ChangeNotificationListener
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // A mapping between the DNs and applicable subentries.
  private HashMap<DN,List<SubEntry>> dn2SubEntry;

  // A mapping between the DNs and applicable collective subentries.
  private HashMap<DN,List<SubEntry>> dn2CollectiveSubEntry;

  // Internal search all operational attributes.
  private LinkedHashSet<String> requestAttrs;

  // Lock to protect internal data structures.
  private final ReentrantReadWriteLock lock;

  /**
   * Creates a new instance of this group manager.
   */
  public SubentryManager()
  {
    lock = new ReentrantReadWriteLock();

    dn2SubEntry = new HashMap<DN,List<SubEntry>>();
    dn2CollectiveSubEntry = new HashMap<DN,List<SubEntry>>();

    requestAttrs = new LinkedHashSet<String>();
    requestAttrs.add("subtreespecification");
    requestAttrs.add("*");

    DirectoryServer.registerBackendInitializationListener(this);
    DirectoryServer.registerChangeNotificationListener(this);
  }

  /**
   * Add a given entry to this subentry manager.
   * @param entry to add.
   */
  private void addSubEntry(Entry entry) throws DirectoryException
  {
    SubEntry subEntry = new SubEntry(entry);
    RFC3672SubtreeSpecification subSpec =
            subEntry.getSubTreeSpecification();
    DN subDN = subSpec.getBaseDN();
    List<SubEntry> subList = null;
    lock.writeLock().lock();
    try
    {
      if (subEntry.isCollective())
      {
        subList = dn2CollectiveSubEntry.get(subDN);
      }
      else
      {
        subList = dn2SubEntry.get(subDN);
      }
      if (subList == null)
      {
        subList = new ArrayList<SubEntry>();
        if (subEntry.isCollective())
        {
          dn2CollectiveSubEntry.put(subDN, subList);
        }
        else
        {
          dn2SubEntry.put(subDN, subList);
        }
      }
      subList.add(subEntry);
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * Remove a given entry from this subentry manager.
   * @param entry to remove.
   */
  private void removeSubEntry(Entry entry)
  {
    lock.writeLock().lock();
    try
    {
      boolean removed = false;
      Iterator<Map.Entry<DN, List<SubEntry>>> iterator =
              dn2SubEntry.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = iterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        for (SubEntry subEntry : subList)
        {
          if (subEntry.getDN().equals(entry.getDN()))
          {
            removed = subList.remove(subEntry);
            break;
          }
        }
        if (subList.isEmpty())
        {
          iterator.remove();
        }
        if (removed)
        {
          return;
        }
      }
      iterator = dn2CollectiveSubEntry.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = iterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        for (SubEntry subEntry : subList)
        {
          if (subEntry.getDN().equals(entry.getDN()))
          {
            removed = subList.remove(subEntry);
            break;
          }
        }
        if (subList.isEmpty())
        {
          iterator.remove();
        }
        if (removed)
        {
          return;
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * {@inheritDoc}  In this case, the server will search the backend to find
   * all subentries that it may contain and register them with this manager.
   */
  public void performBackendInitializationProcessing(Backend backend)
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    LinkedList<Control> requestControls = new LinkedList<Control>();
    requestControls.add(new SubentriesControl(true, true));

    SearchFilter filter = null;
    try
    {
      filter = SearchFilter.createFilterFromString("(" +
            ATTR_OBJECTCLASS + "=" + OC_SUBENTRY + ")");
      if (backend.getEntryCount() > 0 && ! backend.isIndexed(filter))
      {
        logError(WARN_SUBENTRY_FILTER_NOT_INDEXED.get(
                String.valueOf(filter), backend.getBackendID()));
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      InternalSearchOperation internalSearch = new InternalSearchOperation(
              conn, InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              requestControls, baseDN, SearchScope.WHOLE_SUBTREE,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              filter, requestAttrs, null);
      LocalBackendSearchOperation localSearch =
              new LocalBackendSearchOperation(internalSearch);

      try
      {
        backend.search(localSearch);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Is there anything that we need to do here?
        continue;
      }

      for (SearchResultEntry entry : internalSearch.getSearchEntries())
      {
        if (entry.isSubentry())
        {
          try
          {
            addSubEntry(entry);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // FIXME -- Handle this.
            continue;
          }
        }
      }
    }
  }

  /**
   * Return subentries applicable to specific DN.
   * Note that this getter will skip any collective subentries,
   * returning only applicable regular subentries.
   * @param  dn for which to retrieve applicable
   *         subentries.
   * @return applicable subentries.
   */
  public List<SubEntry> getSubentries(DN dn)
  {
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = dn; subDN != null;
           subDN = subDN.getParent())
      {
        List<SubEntry> subList = dn2SubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            RFC3672SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isDNWithinScope(dn))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
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
    if (dn2SubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = entry.getDN(); subDN != null;
           subDN = subDN.getParent())
      {
        List<SubEntry> subList = dn2SubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            RFC3672SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isWithinScope(entry))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
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
    if (dn2CollectiveSubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = dn; subDN != null;
           subDN = subDN.getParent())
      {
        List<SubEntry> subList = dn2CollectiveSubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            RFC3672SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isDNWithinScope(dn))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
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
    if (dn2CollectiveSubEntry.isEmpty())
    {
      return Collections.emptyList();
    }

    List<SubEntry> subentries = new ArrayList<SubEntry>();

    lock.readLock().lock();
    try
    {
      for (DN subDN = entry.getDN(); subDN != null;
           subDN = subDN.getParent())
      {
        List<SubEntry> subList = dn2CollectiveSubEntry.get(subDN);
        if (subList != null)
        {
          for (SubEntry subEntry : subList)
          {
            RFC3672SubtreeSpecification subSpec =
                    subEntry.getSubTreeSpecification();
            if (subSpec.isWithinScope(entry))
            {
              subentries.add(subEntry);
            }
          }
        }
      }
    }
    finally
    {
      lock.readLock().unlock();
    }

    return subentries;
  }

  /**
   * {@inheritDoc}  In this case, the server will de-register
   * all subentries associated with the provided backend.
   */
  public void performBackendFinalizationProcessing(Backend backend)
  {
    lock.writeLock().lock();
    try
    {
      Iterator<Map.Entry<DN, List<SubEntry>>> iterator =
              dn2SubEntry.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = iterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        for (SubEntry subEntry : subList)
        {
          if (backend.handlesEntry(subEntry.getDN()))
          {
            subList.remove(subEntry);
          }
        }
        if (subList.isEmpty())
        {
          iterator.remove();
        }
      }
      iterator = dn2CollectiveSubEntry.entrySet().iterator();
      while (iterator.hasNext())
      {
        Map.Entry<DN, List<SubEntry>> mapEntry = iterator.next();
        List<SubEntry> subList = mapEntry.getValue();
        for (SubEntry subEntry : subList)
        {
          if (backend.handlesEntry(subEntry.getDN()))
          {
            subList.remove(subEntry);
          }
        }
        if (subList.isEmpty())
        {
          iterator.remove();
        }
      }
    }
    finally
    {
      lock.writeLock().unlock();
    }
  }

  /**
   * {@inheritDoc}  In this case, each entry is checked to see if it is
   * a subentry, and if so it will be registered with this manager.
   */
  public void handleAddOperation(PostResponseAddOperation addOperation,
                                 Entry entry)
  {
    if (entry.isSubentry())
    {
      try
      {
        addSubEntry(entry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Handle this.
      }
    }
  }

  /**
   * {@inheritDoc}  In this case, each entry is checked to see if it is
   * a subentry, and if so it will be deregistered with this manager.
   */
  public void handleDeleteOperation(PostResponseDeleteOperation deleteOperation,
                                    Entry entry)
  {
    if (entry.isSubentry())
    {
      removeSubEntry(entry);
    }
  }

  /**
   * {@inheritDoc}  In this case, if the entry is a registered subentry
   * then it will be recreated from the contents of the provided entry
   * and re-registered with this manager.
   */
  public void handleModifyOperation(PostResponseModifyOperation modifyOperation,
                                    Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isSubentry())
    {
      removeSubEntry(oldEntry);
    }
    if (newEntry.isSubentry())
    {
      try
      {
        addSubEntry(newEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Handle this.
      }
    }
  }

  /**
   * {@inheritDoc}  In this case, if the subentry is registered then it
   * will be recreated from the contents of the provided entry and re-
   * registered with this manager under the new DN and the old instance
   * will be deregistered.
   */
  public void handleModifyDNOperation(
                   PostResponseModifyDNOperation modifyDNOperation,
                   Entry oldEntry, Entry newEntry)
  {
    if (oldEntry.isSubentry())
    {
      removeSubEntry(oldEntry);
      try
      {
        addSubEntry(newEntry);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // FIXME -- Handle this.
      }
    }
  }
}
