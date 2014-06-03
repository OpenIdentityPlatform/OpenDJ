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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.replication.server.changelog.je.ChangeNumberIndexer;
import org.opends.server.replication.server.changelog.je.CompositeDBCursor;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;
import com.forgerock.opendj.util.Pair;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.messages.ReplicationMessages.*;

/**
 * Log file implementation of the ChangelogDB interface.
 */
public class FileChangelogDB implements ChangelogDB, ReplicationDomainDB
{
  private static final DebugTracer TRACER = getTracer();

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
   * When creating a FileReplicaDB, synchronize on the domainMap to avoid
   * concurrent shutdown.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, FileReplicaDB>>
      domainToReplicaDBs = new ConcurrentHashMap<DN, ConcurrentMap<Integer, FileReplicaDB>>();
  private ReplicationEnvironment replicationEnv;
  private final ReplicationServerCfg config;
  private final File dbDirectory;

  /**
   * The handler of the changelog database, the database stores the relation
   * between a change number and the associated cookie.
   * <p>
   * @GuardedBy("cnIndexDBLock")
   */
  private FileChangeNumberIndexDB cnIndexDB;
  private final AtomicReference<ChangeNumberIndexer> cnIndexer = new AtomicReference<ChangeNumberIndexer>();

  /** Used for protecting {@link ChangeNumberIndexDB} related state. */
  private final Object cnIndexDBLock = new Object();

  /**
   * The purge delay (in milliseconds). Records in the changelog DB that are
   * older than this delay might be removed.
   */
  private long purgeDelayInMillis;
  private final AtomicReference<ChangelogDBPurger> cnPurger = new AtomicReference<ChangelogDBPurger>();
  private volatile long latestPurgeDate;

  /** The local replication server. */
  private final ReplicationServer replicationServer;

  private final AtomicBoolean shutdown = new AtomicBoolean();

  static final DBCursor<UpdateMsg> EMPTY_CURSOR_REPLICA_DB =
      new FileReplicaDBCursor(new Log.EmptyLogCursor<CSN, UpdateMsg>(), null);

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
  public FileChangelogDB(final ReplicationServer replicationServer, final ReplicationServerCfg config)
     throws ConfigException
  {
    this.replicationServer = replicationServer;
    this.config = config;
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
      final MessageBuilder mb = new MessageBuilder(e.getLocalizedMessage()).append(" ")
          .append(String.valueOf(dbDirectory));
      throw new ConfigException(ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString()), e);
    }
  }

  private Map<Integer, FileReplicaDB> getDomainMap(final DN baseDN)
  {
    final Map<Integer, FileReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private FileReplicaDB getReplicaDB(final DN baseDN, final int serverId)
  {
    return getDomainMap(baseDN).get(serverId);
  }

  /**
   * Returns a {@link FileReplicaDB}, possibly creating it.
   *
   * @param baseDN
   *          the baseDN for which to create a ReplicaDB
   * @param serverId
   *          the serverId for which to create a ReplicaDB
   * @param server
   *          the ReplicationServer
   * @return a Pair with the FileReplicaDB and a boolean indicating whether it had
   *         to be created
   * @throws ChangelogException
   *           if a problem occurred with the database
   */
  Pair<FileReplicaDB, Boolean> getOrCreateReplicaDB(final DN baseDN, final int serverId,
      final ReplicationServer server) throws ChangelogException
  {
    while (!shutdown.get())
    {
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = getExistingOrNewDomainMap(baseDN);
      final Pair<FileReplicaDB, Boolean> result = getExistingOrNewReplicaDB(domainMap, serverId, baseDN, server);
      if (result != null)
      {
        return result;
      }
    }
    throw new ChangelogException(ERR_CANNOT_CREATE_REPLICA_DB_BECAUSE_CHANGELOG_DB_SHUTDOWN.get());
  }

  private ConcurrentMap<Integer, FileReplicaDB> getExistingOrNewDomainMap(final DN baseDN)
  {
    // happy path: the domainMap already exists
    final ConcurrentMap<Integer, FileReplicaDB> currentValue = domainToReplicaDBs.get(baseDN);
    if (currentValue != null)
    {
      return currentValue;
    }

    // unlucky, the domainMap does not exist: take the hit and create the
    // newValue, even though the same could be done concurrently by another
    // thread
    final ConcurrentMap<Integer, FileReplicaDB> newValue = new ConcurrentHashMap<Integer, FileReplicaDB>();
    final ConcurrentMap<Integer, FileReplicaDB> previousValue = domainToReplicaDBs.putIfAbsent(baseDN, newValue);
    if (previousValue != null)
    {
      // there was already a value associated to the key, let's use it
      return previousValue;
    }
    return newValue;
  }

  private Pair<FileReplicaDB, Boolean> getExistingOrNewReplicaDB(final ConcurrentMap<Integer, FileReplicaDB> domainMap,
      final int serverId, final DN baseDN, final ReplicationServer server) throws ChangelogException
  {
    // happy path: the FileReplicaDB already exists
    FileReplicaDB currentValue = domainMap.get(serverId);
    if (currentValue != null)
    {
      return Pair.of(currentValue, false);
    }

    // unlucky, the FileReplicaDB does not exist: take the hit and synchronize
    // on the domainMap to create a new ReplicaDB
    synchronized (domainMap)
    {
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
        // 1) shutdown properly or 2) lazily recreate the FileReplicaDB
        return null;
      }

      final FileReplicaDB newDB = new FileReplicaDB(serverId, baseDN, server, replicationEnv);
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
      replicationEnv = new ReplicationEnvironment(dbDir.getAbsolutePath(), replicationServer);
      final ChangelogState changelogState = replicationEnv.readChangelogState();
      initializeToChangelogState(changelogState);
      if (config.isComputeChangeNumber())
      {
        startIndexer(changelogState);
      }
      setPurgeDelay(replicationServer.getPurgeDelay());
    }
    catch (ChangelogException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      logError(ERR_COULD_NOT_READ_DB.get(this.dbDirectory.getAbsolutePath(), e.getLocalizedMessage()));
    }
  }

  private void initializeToChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<DN, Long> entry : changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true).initGenerationID(entry.getValue());
    }
    for (Map.Entry<DN, List<Integer>> entry : changelogState.getDomainToServerIds().entrySet())
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
      indexer.interrupt();
    }
    final ChangelogDBPurger purger = cnPurger.getAndSet(null);
    if (purger != null)
    {
      purger.initiateShutdown();
      purger.interrupt();
    }

    try
    {
      shutdownChangeNumberIndexDB();
    }
    catch (ChangelogException e)
    {
      firstException = e;
    }

    for (Iterator<ConcurrentMap<Integer, FileReplicaDB>> it =
        this.domainToReplicaDBs.values().iterator(); it.hasNext();)
    {
      final ConcurrentMap<Integer, FileReplicaDB> domainMap = it.next();
      synchronized (domainMap)
      {
        it.remove();
        for (FileReplicaDB replicaDB : domainMap.values())
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
   * Clears all records from the changelog (does not remove the log itself).
   *
   * @throws ChangelogException
   *           If an error occurs when clearing the log.
   */
  public void clearDB() throws ChangelogException
  {
    if (debugEnabled())
    {
      TRACER.debugError("clear the FileChangelogDB");
    }
    if (!dbDirectory.exists())
    {
      return;
    }

    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      indexer.clear();
    }

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
          else if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
    for (FileReplicaDB replicaDB : getDomainMap(baseDN).values())
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
    for (FileReplicaDB replicaDB : getDomainMap(baseDN).values())
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
    Map<Integer, FileReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      synchronized (domainMap)
      {
        domainMap = domainToReplicaDBs.remove(baseDN);
        for (FileReplicaDB replicaDB : domainMap.values())
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


    // 2- clear the changelogstate DB
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
      else if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
    final ChangelogDBPurger purger;
    if (purgeDelayInMillis > 0)
    {
      purger = new ChangelogDBPurger();
      if (cnPurger.compareAndSet(null, purger))
      {
        purger.start();
      } // otherwise a purger was already running
    }
    else
    {
      purger = cnPurger.getAndSet(null);
      if (purger != null)
      {
        purger.initiateShutdown();
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
      startIndexer(replicationEnv.readChangelogState());
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
  public long getDomainLatestTrimDate(final DN baseDN)
  {
    return latestPurgeDate;
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
          cnIndexDB = new FileChangeNumberIndexDB(replicationEnv);
        }
        catch (Exception e)
        {
          if (debugEnabled())
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          logError(ERR_CHANGENUMBER_DATABASE.get(e.getLocalizedMessage()));
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
  public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final ServerState startAfterServerState)
      throws ChangelogException
  {
    final Set<Integer> serverIds = getDomainMap(baseDN).keySet();
    final Map<DBCursor<UpdateMsg>, Void> cursors = new HashMap<DBCursor<UpdateMsg>, Void>(serverIds.size());
    for (int serverId : serverIds)
    {
      // get the last already sent CSN from that server to get a cursor
      final CSN lastCSN = startAfterServerState != null ? startAfterServerState.getCSN(serverId) : null;
      cursors.put(getCursorFrom(baseDN, serverId, lastCSN), null);
    }
    return new CompositeDBCursor<Void>(cursors, true);
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final int serverId, final CSN startAfterCSN)
      throws ChangelogException
  {
    final FileReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(startAfterCSN);
      cursor.next();
      return cursor;
    }
    return EMPTY_CURSOR_REPLICA_DB;
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(final DN baseDN, final UpdateMsg updateMsg) throws ChangelogException
  {
    final Pair<FileReplicaDB, Boolean> pair = getOrCreateReplicaDB(baseDN,
        updateMsg.getCSN().getServerId(), replicationServer);
    final FileReplicaDB replicaDB = pair.getFirst();
    final boolean wasCreated = pair.getSecond();

    replicaDB.add(updateMsg);
    final ChangeNumberIndexer indexer = cnIndexer.get();
    if (indexer != null)
    {
      notifyReplicaOnline(indexer, baseDN, updateMsg.getCSN().getServerId());
      indexer.publishUpdateMsg(baseDN, updateMsg);
    }
    return wasCreated;
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
            final FileChangeNumberIndexDB localCNIndexDB = cnIndexDB;
            if (localCNIndexDB == null)
            { // shutdown has been initiated
              return;
            }

            oldestNotPurgedCSN = localCNIndexDB.purgeUpTo(purgeCSN);
            if (oldestNotPurgedCSN == null)
            { // shutdown may have been initiated...
              if (!isShutdownInitiated())
              {
                // ... or the change number index DB is empty,
                // wait for new changes to come in.

                // Note we cannot sleep for as long as the purge delay
                // (3 days default), because we might receive late updates
                // that will have to be purged before the purge delay elapses.
                // This can particularly happen in case of network partitions.
                sleep(DEFAULT_SLEEP);
              }
              continue;
            }
          }

          for (final Map<Integer, FileReplicaDB> domainMap: domainToReplicaDBs.values())
          {
            for (final FileReplicaDB replicaDB : domainMap.values())
            {
              replicaDB.purgeUpTo(oldestNotPurgedCSN);
            }
          }

          latestPurgeDate = purgeTimestamp;

          sleep(computeSleepTimeUntilNextPurge(oldestNotPurgedCSN));
        }
        catch (InterruptedException e)
        {
          // shutdown initiated?
        }
        catch (Exception e)
        {
          logError(ERR_EXCEPTION_CHANGELOG_TRIM_FLUSH.get(stackTraceToSingleLineString(e)));
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
  }
}
