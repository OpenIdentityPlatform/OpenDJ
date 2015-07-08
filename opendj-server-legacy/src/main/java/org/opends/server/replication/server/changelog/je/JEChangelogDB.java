/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Pair;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.backends.ChangelogBackend;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.CursorOptions;
import org.opends.server.replication.server.changelog.api.ReplicaId;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.file.ChangeNumberIndexer;
import org.opends.server.replication.server.changelog.file.DomainDBCursor;
import org.opends.server.replication.server.changelog.file.MultiDomainDBCursor;
import org.opends.server.replication.server.changelog.file.ReplicaCursor;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

/**
 * JE implementation of the ChangelogDB interface.
 */
public class JEChangelogDB implements ChangelogDB, ReplicationDomainDB
{
  /** The tracer object for the debug logger. */
  protected static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * This map contains the List of updates received from each LDAP server.
   * <p>
   * When removing a domainMap, code:
   * <ol>
   * <li>first get the domainMap</li>
   * <li>synchronized on the domainMap</li>
   * <li>remove the domainMap</li>
   * <li>then check it's not null</li>
   * <li>then close all inside</li>
   * </ol>
   * When creating a replicaDB, synchronize on the domainMap to avoid
   * concurrent shutdown.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, JEReplicaDB>> domainToReplicaDBs =
      new ConcurrentHashMap<>();
  private final ConcurrentSkipListMap<DN, CopyOnWriteArrayList<DomainDBCursor>> registeredDomainCursors =
      new ConcurrentSkipListMap<>();
  private final CopyOnWriteArrayList<MultiDomainDBCursor> registeredMultiDomainCursors = new CopyOnWriteArrayList<>();
  private final ConcurrentSkipListMap<ReplicaId, CopyOnWriteArrayList<ReplicaCursor>> replicaCursors =
      new ConcurrentSkipListMap<>();
  private ReplicationDbEnv replicationEnv;
  private final ReplicationServerCfg config;
  private final File dbDirectory;

  /**
   * The handler of the changelog database, the database stores the relation
   * between a change number and the associated cookie.
   * <p>
   * @GuardedBy("cnIndexDBLock")
   */
  private JEChangeNumberIndexDB cnIndexDB;
  private final AtomicReference<ChangeNumberIndexer> cnIndexer = new AtomicReference<>();

  /** Used for protecting {@link ChangeNumberIndexDB} related state. */
  private final Object cnIndexDBLock = new Object();

  /**
   * The purge delay (in milliseconds). Records in the changelog DB that are
   * older than this delay might be removed.
   */
  private volatile long purgeDelayInMillis;
  private final AtomicReference<ChangelogDBPurger> cnPurger = new AtomicReference<>();

  /** The local replication server. */
  private final ReplicationServer replicationServer;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  private static final DBCursor<UpdateMsg> EMPTY_CURSOR_REPLICA_DB =
      new DBCursor<UpdateMsg>()
  {

    @Override
    public boolean next()
    {
      return false;
    }

    @Override
    public UpdateMsg getRecord()
    {
      return null;
    }

    @Override
    public void close()
    {
      // empty
    }

    @Override
    public String toString()
    {
      return "EmptyDBCursor<UpdateMsg>";
    }
  };

  /**
   * Creates a new changelog DB.
   *
   * @param replicationServer
   *          the local replication server.
   * @param config
   *          the replication server configuration
   * @throws ConfigException
   *           if a problem occurs opening the supplied directory
   */
  public JEChangelogDB(final ReplicationServer replicationServer, final ReplicationServerCfg config)
      throws ConfigException
  {
    this.config = config;
    this.replicationServer = replicationServer;
    this.dbDirectory = makeDir(config.getReplicationDBDirectory());
  }

  private File makeDir(final String dbDirName) throws ConfigException
  {
    // Check that this path exists or create it.
    final File dbDirectory = getFileForPath(dbDirName);
    try
    {
      if (!dbDirectory.exists())
      {
        dbDirectory.mkdir();
      }
      return dbDirectory;
    }
    catch (Exception e)
    {
      final LocalizableMessageBuilder mb = new LocalizableMessageBuilder(
          e.getLocalizedMessage()).append(" ").append(String.valueOf(dbDirectory));
      throw new ConfigException(ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString()), e);
    }
  }

  private Map<Integer, JEReplicaDB> getDomainMap(final DN baseDN)
  {
    final Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private JEReplicaDB getReplicaDB(final DN baseDN, final int serverId)
  {
    return getDomainMap(baseDN).get(serverId);
  }

  /**
   * Returns a {@link JEReplicaDB}, possibly creating it.
   *
   * @param baseDN
   *          the baseDN for which to create a ReplicaDB
   * @param serverId
   *          the serverId for which to create a ReplicaDB
   * @param server
   *          the ReplicationServer
   * @return a Pair with the JEReplicaDB and a boolean indicating whether it has been created
   * @throws ChangelogException
   *           if a problem occurred with the database
   */
  Pair<JEReplicaDB, Boolean> getOrCreateReplicaDB(final DN baseDN, final int serverId,
      final ReplicationServer server) throws ChangelogException
  {
    while (!shutdown.get())
    {
      final ConcurrentMap<Integer, JEReplicaDB> domainMap = getExistingOrNewDomainMap(baseDN);
      final Pair<JEReplicaDB, Boolean> result = getExistingOrNewReplicaDB(domainMap, serverId, baseDN, server);
      if (result != null)
      {
        final Boolean dbWasCreated = result.getSecond();
        if (dbWasCreated)
        { // new replicaDB => update all cursors with it
          final List<DomainDBCursor> cursors = registeredDomainCursors.get(baseDN);
          if (cursors != null && !cursors.isEmpty())
          {
            for (DomainDBCursor cursor : cursors)
            {
              cursor.addReplicaDB(serverId, null);
            }
          }
        }

        return result;
      }
    }
    throw new ChangelogException(ERR_CANNOT_CREATE_REPLICA_DB_BECAUSE_CHANGELOG_DB_SHUTDOWN.get());
  }

  private ConcurrentMap<Integer, JEReplicaDB> getExistingOrNewDomainMap(final DN baseDN)
  {
    // happy path: the domainMap already exists
    final ConcurrentMap<Integer, JEReplicaDB> currentValue = domainToReplicaDBs.get(baseDN);
    if (currentValue != null)
    {
      return currentValue;
    }

    // unlucky, the domainMap does not exist: take the hit and create the
    // newValue, even though the same could be done concurrently by another thread
    final ConcurrentMap<Integer, JEReplicaDB> newValue = new ConcurrentHashMap<>();
    final ConcurrentMap<Integer, JEReplicaDB> previousValue = domainToReplicaDBs.putIfAbsent(baseDN, newValue);
    if (previousValue != null)
    {
      // there was already a value associated to the key, let's use it
      return previousValue;
    }

    // we just created a new domain => update all cursors
    for (MultiDomainDBCursor cursor : registeredMultiDomainCursors)
    {
      cursor.addDomain(baseDN, null);
    }
    return newValue;
  }

  private Pair<JEReplicaDB, Boolean> getExistingOrNewReplicaDB(final ConcurrentMap<Integer, JEReplicaDB> domainMap,
      final int serverId, final DN baseDN, final ReplicationServer server) throws ChangelogException
  {
    // happy path: the replicaDB already exists
    JEReplicaDB currentValue = domainMap.get(serverId);
    if (currentValue != null)
    {
      return Pair.of(currentValue, false);
    }

    // unlucky, the replicaDB does not exist: take the hit and synchronize
    // on the domainMap to create a new ReplicaDB
    synchronized (domainMap)
    {
      // double-check
      currentValue = domainMap.get(serverId);
      if (currentValue != null)
      {
        return Pair.of(currentValue, false);
      }

      if (domainToReplicaDBs.get(baseDN) != domainMap)
      {
        // The domainMap could have been concurrently removed because
        // 1) a shutdown was initiated or 2) an initialize was called.
        // Return will allow the code to:
        // 1) shutdown properly or 2) lazily recreate the replicaDB
        return null;
      }

      final JEReplicaDB newDB = new JEReplicaDB(serverId, baseDN, server, replicationEnv);
      domainMap.put(serverId, newDB);
      return Pair.of(newDB, true);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    try
    {
      final File dbDir = getFileForPath(config.getReplicationDBDirectory());
      replicationEnv = new ReplicationDbEnv(dbDir.getAbsolutePath(), replicationServer);
      final ChangelogState changelogState = replicationEnv.getChangelogState();
      initializeToChangelogState(changelogState);
      if (config.isComputeChangeNumber())
      {
        startIndexer(changelogState);
      }
      setPurgeDelay(replicationServer.getPurgeDelay());
    }
    catch (ChangelogException e)
    {
      logger.traceException(e);
      logger.error(ERR_COULD_NOT_READ_DB, this.dbDirectory.getAbsolutePath(), e.getLocalizedMessage());
    }
  }

  private void initializeToChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<DN, Long> entry : changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true).initGenerationID(entry.getValue());
    }
    for (Map.Entry<DN, Set<Integer>> entry : changelogState.getDomainToServerIds().entrySet())
    {
      for (int serverId : entry.getValue())
      {
        getOrCreateReplicaDB(entry.getKey(), serverId, replicationServer);
      }
    }
  }

  private void shutdownChangeNumberIndexDB() throws ChangelogException
  {
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        cnIndexDB.shutdown();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDB() throws ChangelogException
  {
    if (!this.shutdown.compareAndSet(false, true))
    { // shutdown has already been initiated
      return;
    }

    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    final ChangeNumberIndexer indexer = cnIndexer.getAndSet(null);
    if (indexer != null)
    {
      indexer.initiateShutdown();
    }
    final ChangelogDBPurger purger = cnPurger.getAndSet(null);
    if (purger != null)
    {
      purger.initiateShutdown();
    }

    try
    {
      shutdownChangeNumberIndexDB();
    }
    catch (ChangelogException e)
    {
      firstException = e;
    }

    for (Iterator<ConcurrentMap<Integer, JEReplicaDB>> it =
        this.domainToReplicaDBs.values().iterator(); it.hasNext();)
    {
      final ConcurrentMap<Integer, JEReplicaDB> domainMap = it.next();
      synchronized (domainMap)
      {
        it.remove();
        for (JEReplicaDB replicaDB : domainMap.values())
        {
          replicaDB.shutdown();
        }
      }
    }

    if (replicationEnv != null)
    {
      // wait for shutdown of the threads holding cursors
      try
      {
        if (indexer != null)
        {
          indexer.join();
        }
        if (purger != null)
        {
          purger.join();
        }
      }
      catch (InterruptedException e)
      {
        // do nothing: we are already shutting down
      }

      replicationEnv.shutdown();
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /**
   * Clears all records from the changelog (does not remove the changelog itself).
   *
   * @throws ChangelogException
   *           If an error occurs when clearing the changelog.
   */
  public void clearDB() throws ChangelogException
  {
    if (!dbDirectory.exists())
    {
      return;
    }

    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    for (DN baseDN : this.domainToReplicaDBs.keySet())
    {
      removeDomain(baseDN);
    }

    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        try
        {
          cnIndexDB.clear();
        }
        catch (ChangelogException e)
        {
          firstException = e;
        }

        try
        {
          shutdownChangeNumberIndexDB();
        }
        catch (ChangelogException e)
        {
          if (firstException == null)
          {
            firstException = e;
          }
          else
          {
            logger.traceException(e);
          }
        }

        cnIndexDB = null;
      }
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void removeDB() throws ChangelogException
  {
    shutdownDB();
    StaticUtils.recursiveDelete(dbDirectory);
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainOldestCSNs(DN baseDN)
  {
    final ServerState result = new ServerState();
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      result.update(replicaDB.getOldestCSN());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public ServerState getDomainNewestCSNs(DN baseDN)
  {
    final ServerState result = new ServerState();
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      result.update(replicaDB.getNewestCSN());
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void removeDomain(DN baseDN) throws ChangelogException
  {
    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    // 1- clear the replica DBs
    Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      final ChangeNumberIndexer indexer = this.cnIndexer.get();
      if (indexer != null)
      {
        indexer.clear(baseDN);
      }
      synchronized (domainMap)
      {
        domainMap = domainToReplicaDBs.remove(baseDN);
        for (JEReplicaDB replicaDB : domainMap.values())
        {
          try
          {
            replicaDB.clear();
          }
          catch (ChangelogException e)
          {
            firstException = e;
          }
          replicaDB.shutdown();
        }
      }
    }

    // 2- clear the ChangeNumber index DB
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        try
        {
          cnIndexDB.removeDomain(baseDN);
        }
        catch (ChangelogException e)
        {
          if (firstException == null)
          {
            firstException = e;
          }
          else
          {
            logger.traceException(e);
          }
        }
      }
    }

    // 3- clear the changelogstate DB
    try
    {
      replicationEnv.clearGenerationId(baseDN);
    }
    catch (ChangelogException e)
    {
      if (firstException == null)
      {
        firstException = e;
      }
      else
      {
        logger.traceException(e);
      }
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(final long purgeDelayInMillis)
  {
    this.purgeDelayInMillis = purgeDelayInMillis;
    if (purgeDelayInMillis > 0)
    {
      final ChangelogDBPurger newPurger = new ChangelogDBPurger();
      if (cnPurger.compareAndSet(null, newPurger))
      { // no purger was running, run this new one
        newPurger.start();
      }
      else
      { // a purger was already running, just wake that one up
        // to verify if some entries can be purged with the new purge delay
        final ChangelogDBPurger currentPurger = cnPurger.get();
        synchronized (currentPurger)
        {
          currentPurger.notify();
        }
      }
    }
    else
    {
      final ChangelogDBPurger purgerToStop = cnPurger.getAndSet(null);
      if (purgerToStop != null)
      { // stop this purger
        purgerToStop.initiateShutdown();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setComputeChangeNumber(final boolean computeChangeNumber)
      throws ChangelogException
  {
    if (computeChangeNumber)
    {
      startIndexer(replicationEnv.getChangelogState());
    }
    else
    {
      final ChangeNumberIndexer indexer = cnIndexer.getAndSet(null);
      if (indexer != null)
      {
        indexer.initiateShutdown();
      }
    }
  }

  private void startIndexer(final ChangelogState changelogState)
  {
    final ChangeNumberIndexer indexer = new ChangeNumberIndexer(this, changelogState);
    if (cnIndexer.compareAndSet(null, indexer))
    {
      indexer.start();
    }
  }

  /** {@inheritDoc} */
  @Override
  public ChangeNumberIndexDB getChangeNumberIndexDB()
  {
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB == null)
      {
        try
        {
          cnIndexDB = new JEChangeNumberIndexDB(replicationEnv);
        }
        catch (Exception e)
        {
          logger.traceException(e);
          logger.error(ERR_CHANGENUMBER_DATABASE, e.getLocalizedMessage());
        }
      }
      return cnIndexDB;
    }
  }

  /** {@inheritDoc} */
  @Override
  public ReplicationDomainDB getReplicationDomainDB()
  {
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public MultiDomainDBCursor getCursorFrom(final MultiDomainServerState startState, CursorOptions options)
      throws ChangelogException
  {
    final Set<DN> excludedDomainDns = Collections.emptySet();
    return getCursorFrom(startState, options, excludedDomainDns);
  }

  /** {@inheritDoc} */
  @Override
  public MultiDomainDBCursor getCursorFrom(final MultiDomainServerState startState,
      CursorOptions options, final Set<DN> excludedDomainDns) throws ChangelogException
  {
    final MultiDomainDBCursor cursor = new MultiDomainDBCursor(this, options);
    registeredMultiDomainCursors.add(cursor);
    for (DN baseDN : domainToReplicaDBs.keySet())
    {
      if (!excludedDomainDns.contains(baseDN))
      {
        cursor.addDomain(baseDN, startState.getServerState(baseDN));
      }
    }
    return cursor;
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final ServerState startState, CursorOptions options)
      throws ChangelogException
  {
    final DomainDBCursor cursor = newDomainDBCursor(baseDN, options);
    for (int serverId : getDomainMap(baseDN).keySet())
    {
      // get the last already sent CSN from that server to get a cursor
      final CSN lastCSN = startState != null ? startState.getCSN(serverId) : null;
      cursor.addReplicaDB(serverId, lastCSN);
    }
    return cursor;
  }

  private DomainDBCursor newDomainDBCursor(final DN baseDN, CursorOptions options)
  {
    final DomainDBCursor cursor = new DomainDBCursor(baseDN, this, options);
    putCursor(registeredDomainCursors, baseDN, cursor);
    return cursor;
  }

  private CSN getOfflineCSN(DN baseDN, int serverId, CSN startAfterCSN)
  {
    final MultiDomainServerState offlineReplicas =
        replicationEnv.getChangelogState().getOfflineReplicas();
    final CSN offlineCSN = offlineReplicas.getCSN(baseDN, serverId);
    if (offlineCSN != null
        && (startAfterCSN == null || startAfterCSN.isOlderThan(offlineCSN)))
    {
      return offlineCSN;
    }
    return null;
  }

  @Override
  public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final int serverId, final CSN startCSN,
      CursorOptions options) throws ChangelogException
  {
    final JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      final CSN actualStartCSN = startCSN != null ? startCSN : options.getDefaultCSN();
      final DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(
          actualStartCSN, options.getKeyMatchingStrategy(), options.getPositionStrategy());
      final CSN offlineCSN = getOfflineCSN(baseDN, serverId, actualStartCSN);
      final ReplicaId replicaId = ReplicaId.of(baseDN, serverId);
      final ReplicaCursor replicaCursor = new ReplicaCursor(cursor, offlineCSN, replicaId, this);

      putCursor(replicaCursors, replicaId, replicaCursor);

      return replicaCursor;
    }
    return EMPTY_CURSOR_REPLICA_DB;
  }

  private <K, V> void putCursor(ConcurrentSkipListMap<K, CopyOnWriteArrayList<V>> map, final K key, final V cursor)
  {
    CopyOnWriteArrayList<V> cursors = map.get(key);
    if (cursors == null)
    {
      cursors = new CopyOnWriteArrayList<>();
      CopyOnWriteArrayList<V> previousValue = map.putIfAbsent(key, cursors);
      if (previousValue != null)
      {
        cursors = previousValue;
      }
    }
    cursors.add(cursor);
  }

  /** {@inheritDoc} */
  @Override
  public void unregisterCursor(final DBCursor<?> cursor)
  {
    if (cursor instanceof MultiDomainDBCursor)
    {
      registeredMultiDomainCursors.remove(cursor);
    }
    else if (cursor instanceof DomainDBCursor)
    {
      final DomainDBCursor domainCursor = (DomainDBCursor) cursor;
      final List<DomainDBCursor> cursors = registeredDomainCursors.get(domainCursor.getBaseDN());
      if (cursors != null)
      {
        cursors.remove(cursor);
      }
    }
    else if (cursor instanceof ReplicaCursor)
    {
      final ReplicaCursor replicaCursor = (ReplicaCursor) cursor;
      final List<ReplicaCursor> cursors = replicaCursors.get(replicaCursor.getReplicaId());
      if (cursors != null)
      {
        cursors.remove(cursor);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(final DN baseDN, final UpdateMsg updateMsg) throws ChangelogException
  {
    final CSN csn = updateMsg.getCSN();
    final Pair<JEReplicaDB, Boolean> pair = getOrCreateReplicaDB(baseDN,
        csn.getServerId(), replicationServer);
    final JEReplicaDB replicaDB = pair.getFirst();
    replicaDB.add(updateMsg);

    ChangelogBackend.getInstance().notifyCookieEntryAdded(baseDN, updateMsg);

    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      notifyReplicaOnline(indexer, baseDN, csn.getServerId());
      indexer.publishUpdateMsg(baseDN, updateMsg);
    }
    return pair.getSecond(); // replica DB was created
  }

  /** {@inheritDoc} */
  @Override
  public void replicaHeartbeat(final DN baseDN, final CSN heartbeatCSN) throws ChangelogException
  {
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      notifyReplicaOnline(indexer, baseDN, heartbeatCSN.getServerId());
      indexer.publishHeartbeat(baseDN, heartbeatCSN);
    }
  }

  private void notifyReplicaOnline(final ChangeNumberIndexer indexer, final DN baseDN, final int serverId)
      throws ChangelogException
  {
    if (indexer.isReplicaOffline(baseDN, serverId))
    {
      replicationEnv.notifyReplicaOnline(baseDN, serverId);
    }
    updateCursorsWithOfflineCSN(baseDN, serverId, null);
  }

  /** {@inheritDoc} */
  @Override
  public void notifyReplicaOffline(final DN baseDN, final CSN offlineCSN) throws ChangelogException
  {
    replicationEnv.notifyReplicaOffline(baseDN, offlineCSN);
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.replicaOffline(baseDN, offlineCSN);
    }
    updateCursorsWithOfflineCSN(baseDN, offlineCSN.getServerId(), offlineCSN);
  }

  private void updateCursorsWithOfflineCSN(final DN baseDN, final int serverId, final CSN offlineCSN)
  {
    final List<ReplicaCursor> cursors = replicaCursors.get(ReplicaId.of(baseDN, serverId));
    if (cursors != null)
    {
      for (ReplicaCursor cursor : cursors)
      {
        cursor.setOfflineCSN(offlineCSN);
      }
    }
  }

  /**
   * The thread purging the changelogDB on a regular interval. Records are
   * purged from the changelogDB if they are older than a delay specified in
   * seconds. The purge process works in two steps:
   * <ol>
   * <li>first purge the changeNumberIndexDB and retrieve information to drive
   * replicaDBs purging</li>
   * <li>proceed to purge each replicaDBs based on the information collected
   * when purging the changeNumberIndexDB</li>
   * </ol>
   */
  private final class ChangelogDBPurger extends DirectoryThread
  {
    private static final int DEFAULT_SLEEP = 500;

    protected ChangelogDBPurger()
    {
      super("changelog DB purger");
    }

    /** {@inheritDoc} */
    @Override
    public void run()
    {
      // initialize CNIndexDB
      getChangeNumberIndexDB();
      while (!isShutdownInitiated())
      {
        try
        {
          final long purgeTimestamp = TimeThread.getTime() - purgeDelayInMillis;
          final CSN purgeCSN = new CSN(purgeTimestamp, 0, 0);
          final CSN oldestNotPurgedCSN;

          // next code assumes that the compute-change-number config
          // never changes during the life time of an RS
          if (!config.isComputeChangeNumber())
          {
            oldestNotPurgedCSN = purgeCSN;
          }
          else
          {
            final JEChangeNumberIndexDB localCNIndexDB = cnIndexDB;
            if (localCNIndexDB == null)
            { // shutdown has been initiated
              return;
            }

            oldestNotPurgedCSN = localCNIndexDB.purgeUpTo(purgeCSN);
            if (oldestNotPurgedCSN == null)
            { // shutdown may have been initiated...
              // ... or the change number index DB is empty,
              // wait for new changes to come in.

              // Note we cannot sleep for as long as the purge delay
              // (3 days default), because we might receive late updates
              // that will have to be purged before the purge delay elapses.
              // This can particularly happen in case of network partitions.
              if (!isShutdownInitiated())
              {
                synchronized (this)
                {
                  if (!isShutdownInitiated())
                  {
                    wait(DEFAULT_SLEEP);
                  }
                }
              }
              continue;
            }
          }

          for (final Map<Integer, JEReplicaDB> domainMap : domainToReplicaDBs.values())
          {
            for (final JEReplicaDB replicaDB : domainMap.values())
            {
              replicaDB.purgeUpTo(oldestNotPurgedCSN);
            }
          }

          if (!isShutdownInitiated())
          {
            synchronized (this)
            {
              if (!isShutdownInitiated())
              {
                wait(computeSleepTimeUntilNextPurge(oldestNotPurgedCSN));
              }
            }
          }
        }
        catch (InterruptedException e)
        {
          // shutdown initiated?
        }
        catch (Exception e)
        {
          logger.error(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH, stackTraceToSingleLineString(e));
          if (replicationServer != null)
          {
            replicationServer.shutdown();
          }
        }
      }
    }

    private long computeSleepTimeUntilNextPurge(CSN notPurgedCSN)
    {
      final long nextPurgeTime = notPurgedCSN.getTime();
      final long currentPurgeTime = TimeThread.getTime() - purgeDelayInMillis;
      if (currentPurgeTime <= nextPurgeTime)
      {
        // sleep till the next CSN to purge,
        return nextPurgeTime - currentPurgeTime;
      }
      // wait a bit before purging more
      return DEFAULT_SLEEP;
    }

    /** {@inheritDoc} */
    @Override
    public void initiateShutdown()
    {
      super.initiateShutdown();
      synchronized (this)
      {
        notify(); // wake up the purger thread for faster shutdown
      }
    }
  }
}
