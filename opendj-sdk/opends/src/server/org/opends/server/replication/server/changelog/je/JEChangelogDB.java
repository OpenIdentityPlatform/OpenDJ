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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ReplicaDBCursor;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.util.Pair;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * JE implementation of the ChangelogDB.
 */
public class JEChangelogDB implements ChangelogDB
{

  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /**
   * This map contains the List of updates received from each LDAP server.
   */
  private final Map<String, Map<Integer, DbHandler>> sourceDbHandlers =
      new ConcurrentHashMap<String, Map<Integer, DbHandler>>();
  private ReplicationDbEnv dbEnv;
  private String dbDirName = null;
  private File dbDirectory;

  /** The local replication server. */
  private final ReplicationServer replicationServer;

  /**
   * Builds an instance of this class.
   *
   * @param replicationServer
   *          the local replication server.
   */
  public JEChangelogDB(ReplicationServer replicationServer)
  {
    this.replicationServer = replicationServer;
  }

  private Map<Integer, DbHandler> getDomainMap(String baseDn)
  {
    final Map<Integer, DbHandler> domainMap = sourceDbHandlers.get(baseDn);
    if (domainMap != null)
    {
      return domainMap;
    }
    return Collections.emptyMap();
  }

  private DbHandler getDbHandler(String baseDn, int serverId)
  {
    return getDomainMap(baseDn).get(serverId);
  }

  /**
   * Provision resources for the specified serverId in the specified replication
   * domain.
   *
   * @param baseDn
   *          the replication domain where to add the serverId
   * @param serverId
   *          the server Id to add to the replication domain
   * @throws ChangelogException
   *           If a database error happened.
   */
  private void commission(String baseDn, int serverId, ReplicationServer rs)
      throws ChangelogException
  {
    getOrCreateDbHandler(baseDn, serverId, rs);
  }

  private Pair<DbHandler, Boolean> getOrCreateDbHandler(String baseDn,
      int serverId, ReplicationServer rs) throws ChangelogException
  {
    synchronized (sourceDbHandlers)
    {
      Map<Integer, DbHandler> domainMap = sourceDbHandlers.get(baseDn);
      if (domainMap == null)
      {
        domainMap = new ConcurrentHashMap<Integer, DbHandler>();
        sourceDbHandlers.put(baseDn, domainMap);
      }

      DbHandler dbHandler = domainMap.get(serverId);
      if (dbHandler == null)
      {
        dbHandler =
            new DbHandler(serverId, baseDn, rs, dbEnv, rs.getQueueSize());
        domainMap.put(serverId, dbHandler);
        return Pair.of(dbHandler, true);
      }
      return Pair.of(dbHandler, false);
    }
  }


  /** {@inheritDoc} */
  @Override
  public void initializeDB()
  {
    try
    {
      dbEnv = new ReplicationDbEnv(getFileForPath(dbDirName).getAbsolutePath(),
          replicationServer);
      initializeChangelogState(dbEnv.readChangelogState());
    }
    catch (ChangelogException e)
    {
      Message message =
          ERR_COULD_NOT_READ_DB.get(this.dbDirectory.getAbsolutePath(), e
              .getLocalizedMessage());
      logError(message);
    }
  }

  private void initializeChangelogState(final ChangelogState changelogState)
      throws ChangelogException
  {
    for (Map.Entry<String, Long> entry :
      changelogState.getDomainToGenerationId().entrySet())
    {
      replicationServer.getReplicationServerDomain(entry.getKey(), true)
          .initGenerationID(entry.getValue());
    }
    for (Map.Entry<String, List<Integer>> entry : changelogState
        .getDomainToServerIds().entrySet())
    {
      final String baseDn = entry.getKey();
      for (int serverId : entry.getValue())
      {
        commission(baseDn, serverId, replicationServer);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDB()
  {
    if (dbEnv != null)
    {
      dbEnv.shutdown();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Set<Integer> getDomainServerIds(String baseDn)
  {
    return getDomainMap(baseDn).keySet();
  }

  /** {@inheritDoc} */
  @Override
  public long getCount(String baseDn, int serverId, CSN from, CSN to)
  {
    DbHandler dbHandler = getDbHandler(baseDn, serverId);
    if (dbHandler != null)
    {
      return dbHandler.getCount(from, to);
    }
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainChangesCount(String baseDn)
  {
    long entryCount = 0;
    for (DbHandler dbHandler : getDomainMap(baseDn).values())
    {
      entryCount += dbHandler.getChangesCount();
    }
    return entryCount;
  }

  /** {@inheritDoc} */
  @Override
  public void shutdownDomain(String baseDn)
  {
    shutdownDbHandlers(getDomainMap(baseDn));
  }

  private void shutdownDbHandlers(Map<Integer, DbHandler> domainMap)
  {
    synchronized (domainMap)
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        dbHandler.shutdown();
      }
      domainMap.clear();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<Integer, CSN> getDomainFirstCSNs(String baseDn)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDn);
    final Map<Integer, CSN> results =
        new HashMap<Integer, CSN>(domainMap.size());
    for (DbHandler dbHandler : domainMap.values())
    {
      results.put(dbHandler.getServerId(), dbHandler.getFirstChange());
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public Map<Integer, CSN> getDomainLastCSNs(String baseDn)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDn);
    final Map<Integer, CSN> results =
        new HashMap<Integer, CSN>(domainMap.size());
    for (DbHandler dbHandler : domainMap.values())
    {
      results.put(dbHandler.getServerId(), dbHandler.getLastChange());
    }
    return results;
  }

  /** {@inheritDoc} */
  @Override
  public void clearDomain(String baseDn)
  {
    final Map<Integer, DbHandler> domainMap = getDomainMap(baseDn);
    synchronized (domainMap)
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        try
        {
          dbHandler.clear();
        }
        catch (Exception e)
        {
          // TODO: i18n
          MessageBuilder mb = new MessageBuilder();
          mb.append(ERR_ERROR_CLEARING_DB.get(dbHandler.toString(), e
              .getMessage()
              + " " + stackTraceToSingleLineString(e)));
          logError(mb.toMessage());
        }
      }
      shutdownDbHandlers(domainMap);
    }

    try
    {
      dbEnv.clearGenerationId(baseDn);
    }
    catch (Exception ignored)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.WARNING, ignored);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setPurgeDelay(long delay)
  {
    for (Map<Integer, DbHandler> domainMap : sourceDbHandlers.values())
    {
      for (DbHandler dbHandler : domainMap.values())
      {
        dbHandler.setPurgeDelay(delay);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getDomainLatestTrimDate(String baseDn)
  {
    long latest = 0;
    for (DbHandler dbHandler : getDomainMap(baseDn).values())
    {
      if (latest == 0 || latest < dbHandler.getLatestTrimDate())
      {
        latest = dbHandler.getLatestTrimDate();
      }
    }
    return latest;
  }

  /** {@inheritDoc} */
  @Override
  public CSN getCSNAfter(String baseDn, int serverId, CSN startAfterCSN)
  {
    final DbHandler dbHandler = getDbHandler(baseDn, serverId);

    ReplicaDBCursor cursor = null;
    try
    {
      cursor = dbHandler.generateCursorFrom(startAfterCSN);
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
  public ChangeNumberIndexDB newChangeNumberIndexDB() throws ChangelogException
  {
    return new DraftCNDbHandler(replicationServer, this.dbEnv);
  }

  /** {@inheritDoc} */
  @Override
  public void setReplicationDBDirectory(String dbDirName)
      throws ConfigException
  {
    if (dbDirName == null)
    {
      dbDirName = "changelogDb";
    }
    this.dbDirName = dbDirName;

    // Check that this path exists or create it.
    dbDirectory = getFileForPath(this.dbDirName);
    try
    {
      if (!dbDirectory.exists())
      {
        dbDirectory.mkdir();
      }
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

  /** {@inheritDoc} */
  @Override
  public String getDBDirName()
  {
    return this.dbDirName;
  }

  /** {@inheritDoc} */
  @Override
  public ReplicaDBCursor getCursorFrom(String baseDn, int serverId,
      CSN startAfterCSN)
  {
    DbHandler dbHandler = getDbHandler(baseDn, serverId);
    if (dbHandler == null)
    {
      return null;
    }

    ReplicaDBCursor it;
    try
    {
      it = dbHandler.generateCursorFrom(startAfterCSN);
    }
    catch (Exception e)
    {
      return null;
    }

    if (!it.next())
    {
      close(it);
      return null;
    }

    return it;
  }

  /** {@inheritDoc} */
  @Override
  public boolean publishUpdateMsg(String baseDn, int serverId,
      UpdateMsg updateMsg) throws ChangelogException
  {
    final Pair<DbHandler, Boolean> pair =
        getOrCreateDbHandler(baseDn, serverId, replicationServer);
    final DbHandler dbHandler = pair.getFirst();
    final boolean wasCreated = pair.getSecond();

    dbHandler.add(updateMsg);
    return wasCreated;
  }

}
