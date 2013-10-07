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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.config.ConfigException;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.*;
import org.opends.server.types.DN;
import org.opends.server.util.Pair;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * JE implementation of the ChangelogDB.
 */
public class JEChangelogDB implements ChangelogDB, ReplicationDomainDB
{

  /**
   * This map contains the List of updates received from each LDAP server.
   */
  private final Map<DN, Map<Integer, JEReplicaDB>> domainToReplicaDBs =
      new ConcurrentHashMap<DN, Map<Integer, JEReplicaDB>>();
  private ReplicationDbEnv dbEnv;
  private final String dbDirectoryName;
  private final File dbDirectory;

  /**
   * The handler of the changelog database, the database stores the relation
   * between a change number and the associated cookie.
   * <p>
   * Guarded by cnIndexDBLock
   */
  private ChangeNumberIndexDB cnIndexDB;

  /** Used for protecting {@link ChangeNumberIndexDB} related state. */
  private final Object cnIndexDBLock = new Object();

  /** The local replication server. */
  private final ReplicationServer replicationServer;

  private static final ReplicaDBCursor EMPTY_CURSOR = new ReplicaDBCursor()
  {

    @Override
    public int compareTo(ReplicaDBCursor o)
    {
      if (o == null)
      {
        throw new NullPointerException(); // as per javadoc
      }
      return o == this ? 0 : -1; // equal to self, but less than all the rest
    }

    @Override
    public boolean next()
    {
      return false;
    }

    @Override
    public UpdateMsg getChange()
    {
      return null;
    }

    @Override
    public void close()
    {
      // empty
    }
  };

  /**
   * Builds an instance of this class.
   *
   * @param replicationServer
   *          the local replication server.
   * @param dbDirName
   *          the directory for use by the replication database
   * @throws ConfigException
   *           if a problem occurs opening the supplied directory
   */
  public JEChangelogDB(ReplicationServer replicationServer, String dbDirName)
      throws ConfigException
  {
    this.replicationServer = replicationServer;
    this.dbDirectoryName = dbDirName != null ? dbDirName : "changelogDb";
    this.dbDirectory = makeDir(this.dbDirectoryName);
  }

  private File makeDir(String dbDirName) throws ConfigException
  {
    // Check that this path exists or create it.
    File dbDirectory = getFileForPath(dbDirName);
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
      MessageBuilder mb = new MessageBuilder();
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
   * @param rs
   *          the ReplicationServer
   * @return a Pair with the JEReplicaDB and a boolean indicating whether it had
   *         to be created
   * @throws ChangelogException
   *           if a problem occurred with the database
   */
  Pair<JEReplicaDB, Boolean> getOrCreateReplicaDB(DN baseDN,
      int serverId, ReplicationServer rs) throws ChangelogException
  {
    synchronized (domainToReplicaDBs)
    {
      Map<Integer, JEReplicaDB> domainMap = domainToReplicaDBs.get(baseDN);
      if (domainMap == null)
      {
        domainMap = new ConcurrentHashMap<Integer, JEReplicaDB>();
        domainToReplicaDBs.put(baseDN, domainMap);
      }

      JEReplicaDB replicaDB = domainMap.get(serverId);
      if (replicaDB == null)
      {
        replicaDB =
            new JEReplicaDB(serverId, baseDN, rs, dbEnv, rs.getQueueSize());
        domainMap.put(serverId, replicaDB);
        return Pair.of(replicaDB, true);
      }
      return Pair.of(replicaDB, false);
    }
  }


  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    try
    {
      dbEnv = new ReplicationDbEnv(
          getFileForPath(dbDirectoryName).getAbsolutePath(), replicationServer);
      initializeChangelogState(dbEnv.readChangelogState());
    }
    catch (ChangelogException e)
    {
      logError(ERR_COULD_NOT_READ_DB.get(this.dbDirectory.getAbsolutePath(),
          e.getLocalizedMessage()));
    }
  }

  private void initializeChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<DN, Long> entry :
      changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true)
          .initGenerationID(entry.getValue());
    }
    for (Map.Entry<DN, List<Integer>> entry :
      changelogState.getDomainToServerIds().entrySet())
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
    // Remember the first exception because :
    // - we want to try to remove everything we want to remove
    // - then throw the first encountered exception
    ChangelogException firstException = null;

    try
    {
      shutdownCNIndexDB();
    }
    catch (ChangelogException e)
    {
      firstException = e;
    }

    if (dbEnv != null)
    {
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
  public Set<Integer> getDomainServerIds(DN baseDN)
  {
    return Collections.unmodifiableSet(getDomainMap(baseDN).keySet());
  }

  /** {@inheritDoc} */
  @Override
  public long getCount(DN baseDN, int serverId, CSN from, CSN to)
  {
    JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      return replicaDB.getCount(from, to);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainChangesCount(DN baseDN)
  {
    long entryCount = 0;
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      entryCount += replicaDB.getChangesCount();
    }
    return entryCount;
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDomain(DN baseDN)
  {
    shutdownReplicaDBs(getDomainMap(baseDN));
    domainToReplicaDBs.remove(baseDN);
  }

  private void shutdownReplicaDBs(Map<Integer, JEReplicaDB> domainMap)
  {
    synchronized (domainMap)
    {
      for (JEReplicaDB replicaDB : domainMap.values())
      {
        replicaDB.shutdown();
      }
      domainMap.clear();
    }
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
    final Map<Integer, JEReplicaDB> domainMap = getDomainMap(baseDN);
    synchronized (domainMap)
    {
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
      }
      shutdownReplicaDBs(domainMap);
      domainToReplicaDBs.remove(baseDN);
    }

    // 2- clear the ChangeNumber index DB
    synchronized (cnIndexDBLock)
    {
      if (cnIndexDB != null)
      {
        try
        {
          cnIndexDB.clear(baseDN);
        }
        catch (ChangelogException e)
        {
          if (firstException == null)
          {
            firstException = e;
          }
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
    }

    if (firstException != null)
    {
      throw firstException;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(long delay)
  {
    for (Map<Integer, JEReplicaDB> domainMap : domainToReplicaDBs.values())
    {
      for (JEReplicaDB replicaDB : domainMap.values())
      {
        replicaDB.setPurgeDelay(delay);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainLatestTrimDate(DN baseDN)
  {
    long latest = 0;
    for (JEReplicaDB replicaDB : getDomainMap(baseDN).values())
    {
      if (latest == 0 || latest < replicaDB.getLatestTrimDate())
      {
        latest = replicaDB.getLatestTrimDate();
      }
    }
    return latest;
  }

  /** {@inheritDoc} */
  @Override
  public CSN getCSNAfter(DN baseDN, int serverId, CSN startAfterCSN)
  {
    final JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);

    ReplicaDBCursor cursor = null;
    try
    {
      cursor = replicaDB.generateCursorFrom(startAfterCSN);
      if (cursor != null && cursor.getChange() != null)
      {
        return cursor.getChange().getCSN();
      }
      return null;
    }
    catch (ChangelogException e)
    {
      // there's no change older than startAfterCSN
      return new CSN(0, 0, serverId);
    }
    finally
    {
      close(cursor);
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
          cnIndexDB = new JEChangeNumberIndexDB(replicationServer, this.dbEnv);
        }
        catch (Exception e)
        {
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
  public ReplicaDBCursor getCursorFrom(DN baseDN, int serverId,
      CSN startAfterCSN)
  {
    JEReplicaDB replicaDB = getReplicaDB(baseDN, serverId);
    if (replicaDB != null)
    {
      try
      {
        ReplicaDBCursor cursor = replicaDB.generateCursorFrom(startAfterCSN);
        cursor.next();
        return cursor;
      }
      catch (ChangelogException e)
      {
        // ignored
      }
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
    return wasCreated;
  }

}
