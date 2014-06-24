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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.messages.Message;
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
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

import com.forgerock.opendj.util.Pair;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * JE implementation of the ChangelogDB interface.
 */
public class JEChangelogDB implements ChangelogDB, ReplicationDomainDB
{
  /** The tracer object for the debug logger. */
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
   * When creating a JEReplicaDB, synchronize on the domainMap to avoid
   * concurrent shutdown.
   */
  private final ConcurrentMap<DN, ConcurrentMap<Integer, JEReplicaDB>>
      domainToReplicaDBs = new ConcurrentHashMap<DN, ConcurrentMap<Integer, JEReplicaDB>>();
  private ReplicationDbEnv dbEnv;
  private ReplicationServerCfg config;
  private final File dbDirectory;

  /**
   * The handler of the changelog database, the database stores the relation
   * between a change number and the associated cookie.
   * <p>
   * @GuardedBy("cnIndexDBLock")
   */
  private JEChangeNumberIndexDB cnIndexDB;
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
  private AtomicBoolean shutdown = new AtomicBoolean();

  private static final DBCursor<UpdateMsg> EMPTY_CURSOR =
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
   * Builds an instance of this class.
   *
   * @param replicationServer
   *          the local replication server.
   * @param config
   *          the replication server configuration
   * @throws ConfigException
   *           if a problem occurs opening the supplied directory
   */
  public JEChangelogDB(ReplicationServer replicationServer,
      ReplicationServerCfg config) throws ConfigException
  {
    this.config = config;
    this.replicationServer = replicationServer;
    this.dbDirectory = makeDir(config.getReplicationDBDirectory());
  }

  private File makeDir(String dbDirName) throws ConfigException
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
      if (debugEnabled())
        TRACER.debugCaught(DebugLogLevel.ERROR, e);

      final MessageBuilder mb = new MessageBuilder();
      mb.append(e.getLocalizedMessage());
      mb.append(" ");
      mb.append(String.valueOf(dbDirectory));
      Message msg = ERR_FILE_CHECK_CREATE_FAILED.get(mb.toString());
      throw new ConfigException(msg, e);
    }
  }

  private Map<Integer, JEReplicaDB> getDomainMap(DN baseDN)
  {
    final Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private JEReplicaDB getReplicaDB(DN baseDN, int serverId)
  {
    return getDomainMap(baseDN).get(serverId);
  }

  /**
   * Provision resources for the specified serverId in the specified replication
   * domain.
   *
   * @param baseDN
   *          the replication domain where to add the serverId
   * @param serverId
   *          the server Id to add to the replication domain
   * @throws ChangelogException
   *           If a database error happened.
   */
  private void commission(DN baseDN, int serverId, ReplicationServer rs)
      throws ChangelogException
  {
    getOrCreateReplicaDB(baseDN, serverId, rs);
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
   * @return a Pair with the JEReplicaDB and a boolean indicating whether it had
   *         to be created
   * @throws ChangelogException
   *           if a problem occurred with the database
   */
  Pair<JEReplicaDB, Boolean> getOrCreateReplicaDB(DN baseDN,
      int serverId, ReplicationServer server) throws ChangelogException
  {
    while (!shutdown.get())
    {
      final ConcurrentMap<Integer, JEReplicaDB> domainMap =
          getExistingOrNewDomainMap(baseDN);
      final Pair<JEReplicaDB, Boolean> result =
          getExistingOrNewReplicaDB(domainMap, serverId, baseDN, server);
      if (result != null)
      {
        return result;
      }
    }
    throw new ChangelogException(
        ERR_CANNOT_CREATE_REPLICA_DB_BECAUSE_CHANGELOG_DB_SHUTDOWN.get());
  }

  private ConcurrentMap<Integer, JEReplicaDB> getExistingOrNewDomainMap(
      DN baseDN)
  {
    // happy path: the domainMap already exists
    final ConcurrentMap<Integer, JEReplicaDB> currentValue =
        domainToReplicaDBs.get(baseDN);
    if (currentValue != null)
    {
      return currentValue;
    }

    // unlucky, the domainMap does not exist: take the hit and create the
    // newValue, even though the same could be done concurrently by another
    // thread
    final ConcurrentMap<Integer, JEReplicaDB> newValue =
        new ConcurrentHashMap<Integer, JEReplicaDB>();
    final ConcurrentMap<Integer, JEReplicaDB> previousValue =
        domainToReplicaDBs.putIfAbsent(baseDN, newValue);
    if (previousValue != null)
    {
      // there was already a value associated to the key, let's use it
      return previousValue;
    }
    return newValue;
  }

  private Pair<JEReplicaDB, Boolean> getExistingOrNewReplicaDB(
      final ConcurrentMap<Integer, JEReplicaDB> domainMap, int serverId,
      DN baseDN, ReplicationServer server) throws ChangelogException
  {
    // happy path: the JEReplicaDB already exists
    JEReplicaDB currentValue = domainMap.get(serverId);
    if (currentValue != null)
    {
      return Pair.of(currentValue, false);
    }

    // unlucky, the JEReplicaDB does not exist: take the hit and synchronize
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
        // 1) shutdown properly or 2) lazily recreate the JEReplicaDB
        return null;
      }

      final JEReplicaDB newDB = new JEReplicaDB(serverId, baseDN, server, dbEnv);
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
      dbEnv = new ReplicationDbEnv(dbDir.getAbsolutePath(), replicationServer);
      final ChangelogState changelogState = dbEnv.readChangelogState();
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
        commission(entry.getKey(), serverId, replicationServer);
      }
    }
  }

  private void shutdownCNIndexDB() throws ChangelogException
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
      shutdownCNIndexDB();
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

    if (dbEnv != null)
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

      dbEnv.shutdown();
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /**
   * Clears all content from the changelog database, but leaves its directory on
   * the filesystem.
   *
   * @throws ChangelogException
   *           If a database problem happened
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
          shutdownCNIndexDB();
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
          else if (debugEnabled())
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // 3- clear the changelogstate DB
    try
    {
      dbEnv.clearGenerationId(baseDN);
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
      startIndexer(dbEnv.readChangelogState());
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
          cnIndexDB = new JEChangeNumberIndexDB(this.dbEnv);
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
    // recycle exhausted cursors,
    // because client code will not manage the cursors itself
    return new CompositeDBCursor<Void>(cursors, true);
  }

  /** {@inheritDoc} */
  @Override
  public DBCursor<UpdateMsg> getCursorFrom(final DN baseDN, final int serverId, final CSN startAfterCSN)
      throws ChangelogException
  {
    JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(startAfterCSN);
      cursor.next();
      return cursor;
    }
    return EMPTY_CURSOR;
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(DN baseDN, UpdateMsg updateMsg)
      throws ChangelogException
  {
    final Pair<JEReplicaDB, Boolean> pair = getOrCreateReplicaDB(baseDN,
        updateMsg.getCSN().getServerId(), replicationServer);
    final JEReplicaDB replicaDB = pair.getFirst();
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
      dbEnv.notifyReplicaOnline(baseDN, serverId);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void notifyReplicaOffline(final DN baseDN, final CSN offlineCSN) throws ChangelogException
  {
    dbEnv.notifyReplicaOffline(baseDN, offlineCSN);
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
              jeFriendlySleep(DEFAULT_SLEEP);
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

          latestPurgeDate = purgeTimestamp;

          jeFriendlySleep(computeSleepTimeUntilNextPurge(oldestNotPurgedCSN));
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

    /**
     * This method implements a sleep() that is friendly to Berkeley JE.
     * <p>
     * Originally, {@link Thread#sleep(long)} was used , but waking up a
     * sleeping threads required calling {@link Thread#interrupt()}, and JE
     * threw exceptions when invoked on interrupted threads.
     * <p>
     * The solution is to replace:
     * <ol>
     * <li> {@link Thread#sleep()} with {@link Object#wait(long)}</li>
     * <li> {@link Thread#interrupt()} with {@link Object#notify()}</li>
     * </ol>
     */
    private void jeFriendlySleep(long millis) throws InterruptedException
    {
      if (!isShutdownInitiated())
      {
        synchronized (this)
        {
          if (!isShutdownInitiated())
          {
            wait(millis);
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
