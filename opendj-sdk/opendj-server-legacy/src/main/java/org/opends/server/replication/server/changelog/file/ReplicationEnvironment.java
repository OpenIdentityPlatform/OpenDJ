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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.time.TimeService;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ChangelogState;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.file.Log.LogRotationParameters;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ReplicationMessages.*;

/**
 * Represents the replication environment, which allows to manage the lifecycle
 * of the replication changelog.
 * <p>
 * A changelog has a root directory, under which the following directories and files are
 * created :
 * <ul>
 * <li>A "changenumberindex" directory containing the log files for
 * ChangeNumberIndexDB, and a file named "rotationtime[millis].last" where [millis] is
 * the time of the last log file rotation in milliseconds</li>
 * <li>A "domains.state" file containing a mapping of each domain DN to an id. The
 * id is used to name the corresponding domain directory.</li>
 * <li>One directory per domain, named after "[id].domain" where [id] is the id
 * assigned to the domain, as specified in the "domains.state" file.</li>
 * </ul>
 * <p>
 * Each domain directory contains the following directories and files :
 * <ul>
 * <li>A "generation[id].id" file, where [id] is the generation id</li>
 * <li>One directory per server id, named after "[id].server" where [id] is the
 * id of the server.</li>
 * </ul>
 * Each server id directory contains the following files :
 * <ul>
 * <li>The "head.log" file, which is the more recent log file where records are appended.</li>
 * <li>Zero to many read-only log files named after the lowest key
 * and highest key present in the log file (they all end with the ".log" suffix.</li>
 * <li>Optionally, a "offline.state" file that indicates that this particular server id
 *  of the domain is offline. This file contains the offline CSN, encoded as a String on a single line.</li>
 * </ul>
 * See {@code Log} class for details on the log files.
 *
 * <p>
 * Layout example with two domains "o=test1" and "o=test2", each having server
 * ids 22 and 33, with server id 33 for domain "o=test1" being offline :
 *
 * <pre>
 * +---changelog
 * |   \---domains.state  [contains mapping: 1 => "o=test1", 2 => "o=test2"]
 * |   \---changenumberindex
 * |      \--- head.log [contains last records written]
 * |      \--- 1_50.log [contains records with keys in interval [1, 50]]
 * |      \--- rotationtime198745512.last
 * |   \---1.domain
 * |       \---generation1.id
 * |       \---22.server
 * |           \---head.log [contains last records written]
 * |       \---33.server
 * |           \---head.log [contains last records written]
 *             \---offline.state
 * |   \---2.domain
 * |       \---generation1.id
 * |       \---22.server
 * |           \---head.log [contains last records written]
 * |       \---33.server
 * |           \---head.log [contains last records written]
 * </pre>
 */
class ReplicationEnvironment
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final long CN_INDEX_DB_MAX_LOG_FILE_SIZE_IN_BYTES = 1024 * 1024;

  private static final long REPLICA_DB_MAX_LOG_FILE_SIZE_IN_BYTES = 10 * CN_INDEX_DB_MAX_LOG_FILE_SIZE_IN_BYTES;

  private static final int NO_GENERATION_ID = -1;

  /** Extension for the temporary file used when modifying an environment file. */
  private static final String FILE_EXTENSION_TEMP = ".tmp";

  private static final String CN_INDEX_DB_DIRNAME = "changenumberindex";

  private static final String DOMAINS_STATE_FILENAME = "domains.state";

  static final String REPLICA_OFFLINE_STATE_FILENAME = "offline.state";

  static final String LAST_ROTATION_TIME_FILE_PREFIX = "rotationtime";

  static final String LAST_ROTATION_TIME_FILE_SUFFIX = ".ms";

  private static final String DOMAIN_STATE_SEPARATOR = ":";

  private static final String DOMAIN_SUFFIX = ".dom";

  private static final String SERVER_ID_SUFFIX = ".server";

  private static final String GENERATION_ID_FILE_PREFIX = "generation";

  private static final String GENERATION_ID_FILE_SUFFIX = ".id";

  private static final String UTF8_ENCODING = "UTF-8";

  private static final FileFilter DOMAIN_FILE_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.isDirectory() && file.getName().endsWith(DOMAIN_SUFFIX);
    }
  };

  private static final FileFilter SERVER_ID_FILE_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.isDirectory() && file.getName().endsWith(SERVER_ID_SUFFIX);
    }
  };

  private static final FileFilter GENERATION_ID_FILE_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.isFile()
          && file.getName().startsWith(GENERATION_ID_FILE_PREFIX)
          && file.getName().endsWith(GENERATION_ID_FILE_SUFFIX);
    }
  };

  private static final FileFilter LAST_ROTATION_TIME_FILE_FILTER = new FileFilter()
  {
    @Override
    public boolean accept(File file)
    {
      return file.isFile()
          && file.getName().startsWith(LAST_ROTATION_TIME_FILE_PREFIX)
          && file.getName().endsWith(LAST_ROTATION_TIME_FILE_SUFFIX);
    }
  };

  /** Root path where the replication log is stored. */
  private final String replicationRootPath;
  /**
   * The current changelogState. This is in-memory version of what is inside the
   * on-disk changelogStateDB. It improves performances in case the
   * changelogState is read often.
   *
   * @GuardedBy("domainsLock")
   */
  private final ChangelogState changelogState;

  /** The list of logs that are in use for Replica DBs. */
  private final List<Log<CSN, UpdateMsg>> logsReplicaDB = new CopyOnWriteArrayList<>();

  /**
   * The list of logs that are in use for the CN Index DB.
   * There is a single CN Index DB for a ReplicationServer, but there can be multiple references opened on it.
   * This is the responsibility of Log class to handle properly these multiple references.
   */
  private List<Log<Long, ChangeNumberIndexRecord>> logsCNIndexDB = new CopyOnWriteArrayList<>();

  /**
   * Maps each domain DN to a domain id that is used to name directory in file system.
   *
   * @GuardedBy("domainsLock")
   */
  private final Map<DN, String> domains = new HashMap<>();

  /**
   * Exclusive lock to synchronize:
   * <ul>
   * <li>the domains mapping</li>
   * <li>changes to the in-memory changelogState</li>
   * <li>changes to the on-disk state of a domain</li>
   */
  private final Object domainsLock = new Object();

  /** The underlying replication server. */
  private final ReplicationServer replicationServer;

  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  /** The time service used for timing. */
  private final TimeService timeService;

  /**
   * For CN Index DB, a log file can be rotated once it has exceeded a given time interval.
   * <p>
   * It is disabled if the interval is equals to zero.
   * The interval can be modified at any time.
   */
  private long cnIndexDBRotationInterval;

  /**
   * For CN Index DB, the last time a log file was rotated.
   * It is persisted to file each time it changes and read at server start. */
  private long cnIndexDBLastRotationTime;

  /**
   * Creates the replication environment.
   *
   * @param rootPath
   *          Root path where replication log is stored.
   * @param replicationServer
   *          The underlying replication server.
   * @param timeService
   *          Time service to use for timing.
   * @throws ChangelogException
   *           If an error occurs during initialization.
   */
  ReplicationEnvironment(final String rootPath,
      final ReplicationServer replicationServer, final TimeService timeService) throws ChangelogException
  {
    this.replicationRootPath = rootPath;
    this.replicationServer = replicationServer;
    this.timeService = timeService;
    this.changelogState = readOnDiskChangelogState();
    this.cnIndexDBLastRotationTime = readOnDiskLastRotationTime();
  }

  /**
   * Sets the rotation time interval of a log file for the CN Index DB.
   *
   * @param timeInterval
   *          time interval for rotation of a log file.
   */
  void setCNIndexDBRotationInterval(long timeInterval)
  {
    cnIndexDBRotationInterval = timeInterval;
    for (Log<Long, ChangeNumberIndexRecord> log : logsCNIndexDB)
    {
      log.setRotationInterval(cnIndexDBRotationInterval);
    }
  }

  /**
   * Returns the state of the replication changelog.
   *
   * @return the {@link ChangelogState} read from the changelogState DB
   * @throws ChangelogException
   *           if a database problem occurs
   */
  ChangelogState readOnDiskChangelogState() throws ChangelogException
  {
    final ChangelogState state = new ChangelogState();
    final File changelogPath = new File(replicationRootPath);
    synchronized (domainsLock)
    {
      readDomainsStateFile();
      checkDomainDirectories(changelogPath);
      for (final Entry<DN, String> domainEntry : domains.entrySet())
      {
        readStateForDomain(domainEntry, state);
      }
    }
    return state;
  }

  /**
   * Returns the current state of the replication changelog.
   *
   * @return the current {@link ChangelogState}
   */
  ChangelogState getChangelogState()
  {
    return changelogState;
  }

  /**
   * Return the last rotation time for CN Index DB log files.
   *
   * @return the last rotation time in millis
   */
  long getCnIndexDBLastRotationTime()
  {
    return cnIndexDBLastRotationTime;
  }

  /**
   * Finds or creates the log used to store changes from the replication server
   * with the given serverId and the given baseDN.
   *
   * @param domainDN
   *          The DN that identifies the domain.
   * @param serverId
   *          The server id that identifies the server.
   * @param generationId
   *          The generationId associated to this domain.
   * @return the log.
   * @throws ChangelogException
   *           if an error occurs.
   */
  Log<CSN, UpdateMsg> getOrCreateReplicaDB(final DN domainDN, final int serverId, final long generationId)
      throws ChangelogException
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("ReplicationEnvironment.getOrCreateReplicaDB(%s, %s, %s)", domainDN, serverId, generationId);
    }

    try
    {
      ensureRootDirectoryExists();

      String domainId = null;
      synchronized (domainsLock)
      {
        domainId = domains.get(domainDN);
        if (domainId == null)
        {
          domainId = createDomainId(domainDN);
        }

        final File serverIdPath = getServerIdPath(domainId, serverId);
        ensureServerIdDirectoryExists(serverIdPath);
        changelogState.addServerIdToDomain(serverId, domainDN);

        final File generationIdPath = getGenerationIdPath(domainId, generationId);
        ensureGenerationIdFileExists(generationIdPath);
        changelogState.setDomainGenerationId(domainDN, generationId);

        return openLog(serverIdPath, FileReplicaDB.RECORD_PARSER,
            new LogRotationParameters(REPLICA_DB_MAX_LOG_FILE_SIZE_IN_BYTES, 0, 0), logsReplicaDB);
      }
    }
    catch (Exception e)
    {
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_CREATE_REPLICA_DB.get(domainDN.toString(), serverId, generationId), e);
    }
  }

  /**
   * Find or create the log to manage integer change number associated to
   * multidomain server state.
   * <p>
   * TODO: ECL how to manage compatibility of this db
   * with new domains added or removed ?
   *
   * @return the log.
   * @throws ChangelogException
   *           when a problem occurs.
   */
  Log<Long, ChangeNumberIndexRecord> getOrCreateCNIndexDB() throws ChangelogException
  {
    final File path = getCNIndexDBPath();
    try
    {
      final LogRotationParameters rotationParams = new LogRotationParameters(CN_INDEX_DB_MAX_LOG_FILE_SIZE_IN_BYTES,
          cnIndexDBRotationInterval, cnIndexDBLastRotationTime);
      return openLog(path, FileChangeNumberIndexDB.RECORD_PARSER, rotationParams, logsCNIndexDB);
    }
    catch (Exception e)
    {
      throw new ChangelogException(
          ERR_CHANGELOG_UNABLE_TO_CREATE_CN_INDEX_DB.get(replicationRootPath, path.getPath()), e);
    }
  }

  /**
   * Shutdown the environment.
   * <p>
   * The log DBs are not closed by this method. It assumes they are already
   * closed.
   */
  void shutdown()
  {
    if (isShuttingDown.compareAndSet(false, true))
    {
      logsReplicaDB.clear();
      logsCNIndexDB.clear();
    }
  }

  /**
   * Clears the generated id associated to the provided domain DN from the state
   * Db.
   * <p>
   * If generation id can't be found, it is not considered as an error, the
   * method will just return.
   *
   * @param domainDN
   *          The domain DN for which the generationID must be cleared.
   * @throws ChangelogException
   *           If a problem occurs during clearing.
   */
  void clearGenerationId(final DN domainDN) throws ChangelogException
  {
    synchronized (domainsLock)
    {
      final String domainId = domains.get(domainDN);
      if (domainId == null)
      {
        return; // unknown domain => no-op
      }
      final File idFile = retrieveGenerationIdFile(getDomainPath(domainId));
      if (idFile != null)
      {
        final boolean isDeleted = idFile.delete();
        if (!isDeleted)
        {
          throw new ChangelogException(
              ERR_CHANGELOG_UNABLE_TO_DELETE_GENERATION_ID_FILE.get(idFile.getPath(), domainDN.toString()));
        }
      }
      changelogState.setDomainGenerationId(domainDN, NO_GENERATION_ID);
    }
  }

  /**
   * Reset the generationId to the default value used when there is no
   * generation id.
   *
   * @param baseDN
   *          The baseDN for which the generationID must be reset.
   * @throws ChangelogException
   *           If a problem occurs during reset.
   */
  void resetGenerationId(final DN baseDN) throws ChangelogException
  {
    synchronized (domainsLock)
    {
      clearGenerationId(baseDN);
      final String domainId = domains.get(baseDN);
      if (domainId == null)
      {
        return; // unknown domain => no-op
      }
      final File generationIdPath = getGenerationIdPath(domainId, NO_GENERATION_ID);
      ensureGenerationIdFileExists(generationIdPath);
      changelogState.setDomainGenerationId(baseDN, NO_GENERATION_ID);
    }
  }

  /**
   * Notify that log file has been rotated for provided log.
   *
   * The last rotation time is persisted to a file and read at startup time.
   *
   * @param log
   *          the log that has a file rotated.
   * @throws ChangelogException
   *            If a problem occurs
   */
  void notifyLogFileRotation(Log<?, ?> log) throws ChangelogException
  {
    // only CN Index DB log rotation time is persisted
    if (logsCNIndexDB.contains(log))
    {
      updateCNIndexDBLastRotationTime(timeService.now());
    }
  }

  /**
   * Notify that the replica corresponding to provided domain and provided CSN
   * is offline.
   *
   * @param domainDN
   *          the domain of the offline replica
   * @param offlineCSN
   *          the offline replica serverId and offline timestamp
   * @throws ChangelogException
   *           if a problem occurs
   */
  void notifyReplicaOffline(DN domainDN, CSN offlineCSN) throws ChangelogException
  {
    synchronized (domainsLock)
    {
      final String domainId = domains.get(domainDN);
      if (domainId == null)
      {
        return; // unknown domain => no-op
      }
      final File serverIdPath = getServerIdPath(domainId, offlineCSN.getServerId());
      if (!serverIdPath.exists())
      {
        return; // no serverId anymore => no-op
      }
      final File offlineFile = new File(serverIdPath, REPLICA_OFFLINE_STATE_FILENAME);
      try (Writer writer = newTempFileWriter(offlineFile))
      {
        // Only the last sent offline CSN is kept
        writer.write(offlineCSN.toString());
        StaticUtils.close(writer);
        changelogState.addOfflineReplica(domainDN, offlineCSN);
        commitFile(offlineFile);
      }
      catch (IOException e)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_WRITE_REPLICA_OFFLINE_STATE_FILE.get(
            domainDN.toString(), offlineCSN.getServerId(), offlineFile.getPath(), offlineCSN.toString()), e);
      }
    }
  }

  /**
   * Notify that the replica corresponding to provided domain and server id
   * is online.
   *
   * @param domainDN
   *          the domain of the replica
   * @param serverId
   *          the replica serverId
   * @throws ChangelogException
   *           if a problem occurs
   */
  void notifyReplicaOnline(DN domainDN, int serverId) throws ChangelogException
  {
    synchronized (domainsLock)
    {
      final String domainId = domains.get(domainDN);
      if (domainId == null)
      {
        return; // unknown domain => no-op
      }
      final File offlineFile = new File(getServerIdPath(domainId, serverId), REPLICA_OFFLINE_STATE_FILENAME);
      if (offlineFile.exists())
      {
        final boolean isDeleted = offlineFile.delete();
        if (!isDeleted)
        {
          throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DELETE_REPLICA_OFFLINE_STATE_FILE.get(
              offlineFile.getPath(), domainDN.toString(), serverId));
        }
      }
      changelogState.removeOfflineReplica(domainDN, serverId);
    }
  }

  /** Reads the domain state file to find mapping between each domainDN and its associated domainId. */
  private void readDomainsStateFile() throws ChangelogException
  {
    final File domainsStateFile = new File(replicationRootPath, DOMAINS_STATE_FILENAME);
    if (domainsStateFile.exists())
    {
      BufferedReader reader = null;
      String line = null;
      try
      {
        reader = newFileReader(domainsStateFile);
        while ((line = reader.readLine()) != null)
        {
          final int separatorPos = line.indexOf(DOMAIN_STATE_SEPARATOR);
          final String domainId = line.substring(0, separatorPos);
          final DN domainDN = DN.valueOf(line.substring(separatorPos+1));
          domains.put(domainDN, domainId);
        }
      }
      catch(DirectoryException e)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DECODE_DN_FROM_DOMAIN_STATE_FILE.get(
            domainsStateFile.getPath(), line), e);
      }
      catch(Exception e)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_READ_DOMAIN_STATE_FILE.get(
            domainsStateFile.getPath()), e);
      }
      finally {
        StaticUtils.close(reader);
      }
    }
  }

  /**
   * Checks that domain directories in file system are consistent with
   * information from domains mapping.
   */
  private void checkDomainDirectories(final File changelogPath) throws ChangelogException
  {
    final File[] dnDirectories = changelogPath.listFiles(DOMAIN_FILE_FILTER);
    if (dnDirectories != null)
    {
      final Set<String> domainIdsFromFileSystem = new HashSet<>();
      for (final File dnDir : dnDirectories)
      {
        final String fileName = dnDir.getName();
        final String domainId = fileName.substring(0, fileName.length() - DOMAIN_SUFFIX.length());
        domainIdsFromFileSystem.add(domainId);
      }

      final Set<String> expectedDomainIds = new HashSet<>(domains.values());
      if (!domainIdsFromFileSystem.equals(expectedDomainIds))
      {
        throw new ChangelogException(ERR_CHANGELOG_INCOHERENT_DOMAIN_STATE.get(domains.values().toString(),
            domainIdsFromFileSystem.toString()));
      }
    }
  }

  /**
   * Update the changelog state with the state corresponding to the provided
   * domain DN.
   */
  private void readStateForDomain(final Entry<DN, String> domainEntry, final ChangelogState state)
      throws ChangelogException
  {
    final File domainDirectory = getDomainPath(domainEntry.getValue());
    final DN domainDN = domainEntry.getKey();
    final String generationId = retrieveGenerationId(domainDirectory);
    if (generationId != null)
    {
      state.setDomainGenerationId(domainDN, toGenerationId(generationId));
    }

    final File[] serverIds = domainDirectory.listFiles(SERVER_ID_FILE_FILTER);
    if (serverIds == null)
    {
      throw new ChangelogException(ERR_CHANGELOG_READ_STATE_CANT_READ_DOMAIN_DIRECTORY.get(
          replicationRootPath, domainDirectory.getPath()));
    }
    for (final File serverId : serverIds)
    {
      readStateForServerId(domainDN, serverId, state);
    }
  }

  private void readStateForServerId(DN domainDN, File serverIdPath, ChangelogState state) throws ChangelogException
  {
    state.addServerIdToDomain(toServerId(serverIdPath.getName()), domainDN);

    final File offlineFile = new File(serverIdPath, REPLICA_OFFLINE_STATE_FILENAME);
    if (offlineFile.exists())
    {
      final CSN offlineCSN = readOfflineStateFile(offlineFile, domainDN);
      state.addOfflineReplica(domainDN, offlineCSN);
    }
  }

  private CSN readOfflineStateFile(final File offlineFile, DN domainDN) throws ChangelogException
  {
    BufferedReader reader = null;
    try
    {
      reader = newFileReader(offlineFile);
      String line = reader.readLine();
      if (line == null || reader.readLine() != null)
      {
        throw new ChangelogException(ERR_CHANGELOG_INVALID_REPLICA_OFFLINE_STATE_FILE.get(
            domainDN.toString(), offlineFile.getPath()));
      }
      return new CSN(line);
    }
    catch(IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_READ_REPLICA_OFFLINE_STATE_FILE.get(
          domainDN.toString(), offlineFile.getPath()), e);
    }
    finally {
      StaticUtils.close(reader);
    }
  }

  private String createDomainId(final DN domainDN) throws ChangelogException
  {
    final String nextDomainId = findNextDomainId();
    domains.put(domainDN, nextDomainId);
    final File domainsStateFile = new File(replicationRootPath, DOMAINS_STATE_FILENAME);
    try (Writer writer = newTempFileWriter(domainsStateFile))
    {
      for (final Entry<DN, String> entry : domains.entrySet())
      {
        writer.write(String.format("%s%s%s%n", entry.getValue(), DOMAIN_STATE_SEPARATOR, entry.getKey()));
      }
      StaticUtils.close(writer);
      commitFile(domainsStateFile);
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_UPDATE_DOMAIN_STATE_FILE.get(nextDomainId,
          domainDN.toString(), domainsStateFile.getPath()), e);
    }
    return nextDomainId;
  }

  /** Find the next domain id to use. This is the lowest integer that is higher than all existing ids. */
  private String findNextDomainId()
  {
    int nextId = 1;
    for (final String domainId : domains.values())
    {
      final Integer id = Integer.valueOf(domainId);
      if (nextId <= id)
      {
        nextId = id + 1;
      }
    }
    return String.valueOf(nextId);
  }

  /** Open a log from the provided path and record parser. */
  private <K extends Comparable<K>, V> Log<K, V> openLog(final File serverIdPath, final RecordParser<K, V> parser,
      LogRotationParameters rotationParams, List<Log<K, V>> logsCache) throws ChangelogException
  {
    checkShutDownBeforeOpening(serverIdPath);

    final Log<K, V> log = Log.openLog(this, serverIdPath, parser, rotationParams);

    checkShutDownAfterOpening(serverIdPath, log);

    logsCache.add(log);

    return log;
  }

  private void checkShutDownAfterOpening(final File serverIdPath, final Log<?, ?> log) throws ChangelogException
  {
    if (isShuttingDown.get())
    {
      closeLog(log);
      throw new ChangelogException(WARN_CANNOT_OPEN_DATABASE_BECAUSE_SHUTDOWN_WAS_REQUESTED.get(serverIdPath.getPath(),
          replicationServer.getServerId()));
    }
  }

  private void checkShutDownBeforeOpening(final File serverIdPath) throws ChangelogException
  {
    if (isShuttingDown.get())
    {
      throw new ChangelogException(
          WARN_CANNOT_OPEN_DATABASE_BECAUSE_SHUTDOWN_WAS_REQUESTED.get(
              serverIdPath.getPath(), replicationServer.getServerId()));
    }
  }

  /**
   * Retrieve the generation id from the provided directory.
   *
   * @return the generation id or {@code null} if the corresponding file can't
   *         be found
   */
  private String retrieveGenerationId(final File directory)
  {
    final File generationId = retrieveGenerationIdFile(directory);
    if (generationId != null)
    {
      String filename = generationId.getName();
      return filename.substring(GENERATION_ID_FILE_PREFIX.length(),
         filename.length() - GENERATION_ID_FILE_SUFFIX.length());
    }
    return null;
  }

  /**
   * Retrieve the file named after the generation id from the provided
   * directory.
   *
   * @return the generation id file or {@code null} if the corresponding file
   *         can't be found
   */
  private File retrieveGenerationIdFile(final File directory)
  {
    File[] generationIds = directory.listFiles(GENERATION_ID_FILE_FILTER);
    return (generationIds != null && generationIds.length > 0) ? generationIds[0] : null;
  }

  /**
   * Retrieve the last rotation time from the disk.
   *
   * @return the last rotation time in millis (which is the current time if no
   *         rotation file is found or if a problem occurs).
   */
  private long readOnDiskLastRotationTime()
  {
    try
    {
      final File file = retrieveLastRotationTimeFile();
      if (file != null)
      {
        final String filename = file.getName();
        final String value = filename.substring(LAST_ROTATION_TIME_FILE_PREFIX.length(),
            filename.length() - LAST_ROTATION_TIME_FILE_SUFFIX.length());
        return Long.valueOf(value);
      }
    }
    catch (Exception e)
    {
      logger.trace(LocalizableMessage.raw("Error when retrieving last log file rotation time from file"), e);
    }
    // Default to current time
    return timeService.now();
  }

  /**
   * Retrieve the file named after the last rotation time from the provided
   * directory.
   *
   * @return the last rotation time file or {@code null} if the corresponding file
   *         can't be found
   */
  private File retrieveLastRotationTimeFile()
  {
    File[] files = getCNIndexDBPath().listFiles(LAST_ROTATION_TIME_FILE_FILTER);
    return (files != null && files.length > 0) ? files[0] : null;
  }

  private File getDomainPath(final String domainId)
  {
    return new File(replicationRootPath, domainId + DOMAIN_SUFFIX);
  }

  /**
   * Return the path for the provided domain id and server id.
   * Package private to be usable in tests.
   *
   * @param domainId
   *            The id corresponding to a domain DN
   * @param serverId
   *            The server id to retrieve
   * @return the path
   */
  File getServerIdPath(final String domainId, final int serverId)
  {
    return new File(getDomainPath(domainId), serverId + SERVER_ID_SUFFIX);
  }

  private File getGenerationIdPath(final String domainId, final long generationId)
  {
    return new File(getDomainPath(domainId), GENERATION_ID_FILE_PREFIX + generationId + GENERATION_ID_FILE_SUFFIX);
  }

  private File getCNIndexDBPath()
  {
    return new File(replicationRootPath, CN_INDEX_DB_DIRNAME);
  }

  private File getLastRotationTimePath(long lastRotationTime)
  {
    return new File(getCNIndexDBPath(),
        LAST_ROTATION_TIME_FILE_PREFIX + lastRotationTime + LAST_ROTATION_TIME_FILE_SUFFIX);
  }

  private void closeLog(final Log<?, ?> log)
  {
    logsReplicaDB.remove(log);
    logsCNIndexDB.remove(log);
    log.close();
  }

  private void ensureRootDirectoryExists() throws ChangelogException
  {
    final File rootDir = new File(replicationRootPath);
    if (!rootDir.exists())
    {
      final boolean created = rootDir.mkdirs();
      if (!created)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_CREATE_LOG_DIRECTORY.get(replicationRootPath));
      }
    }
  }

  private void ensureServerIdDirectoryExists(final File serverIdPath) throws ChangelogException
  {
    if (!serverIdPath.exists())
    {
      boolean created = false;
      try
      {
        created = serverIdPath.mkdirs();
      }
      catch (Exception e)
      {
        // nothing to do
      }

      if (!created)
      {
        throw new ChangelogException(
            ERR_CHANGELOG_UNABLE_TO_CREATE_SERVER_ID_DIRECTORY.get(serverIdPath.getPath(), 0));
      }
    }
  }

  private void updateCNIndexDBLastRotationTime(final long lastRotationTime) throws ChangelogException {
    final File previousRotationFile = retrieveLastRotationTimeFile();
    final File newRotationFile = getLastRotationTimePath(lastRotationTime);
    try
    {
      newRotationFile.createNewFile();
    }
    catch (IOException e)
    {
      throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_CREATE_LAST_LOG_ROTATION_TIME_FILE.get(
          newRotationFile.getPath(), lastRotationTime), e);
    }
    if (previousRotationFile != null)
    {
      final boolean isDeleted = previousRotationFile.delete();
      if (!isDeleted)
      {
        throw new ChangelogException(ERR_CHANGELOG_UNABLE_TO_DELETE_LAST_LOG_ROTATION_TIME_FILE.get(
            previousRotationFile.getPath()));
      }
    }
    cnIndexDBLastRotationTime = lastRotationTime;
  }

  private void ensureGenerationIdFileExists(final File generationIdPath)
      throws ChangelogException
  {
    if (!generationIdPath.exists())
    {
      try
      {
        boolean isCreated = generationIdPath.createNewFile();
        if (!isCreated)
        {
          throw new ChangelogException(
              ERR_CHANGELOG_UNABLE_TO_CREATE_GENERATION_ID_FILE.get(generationIdPath.getPath()));
        }
      }
      catch (IOException e)
      {
        throw new ChangelogException(
            ERR_CHANGELOG_UNABLE_TO_CREATE_GENERATION_ID_FILE.get(generationIdPath.getPath()));
      }
    }
  }

  private void debug(String message)
  {
    // Replication server may be null when testing
    String monitorInstanceName = replicationServer != null ? replicationServer.getMonitorInstanceName() :
      "no monitor [test]";
    logger.trace("In %s, %s", monitorInstanceName, message);
  }

  private int toServerId(final String serverIdName) throws ChangelogException
  {
    try
    {
      String serverId = serverIdName.substring(0, serverIdName.length() - SERVER_ID_SUFFIX.length());
      return Integer.parseInt(serverId);
    }
    catch (NumberFormatException e)
    {
      // should never happen
      throw new ChangelogException(ERR_CHANGELOG_SERVER_ID_FILENAME_WRONG_FORMAT.get(serverIdName), e);
    }
  }

  private long toGenerationId(final String data) throws ChangelogException
  {
    try
    {
      return Long.parseLong(data);
    }
    catch (NumberFormatException e)
    {
      // should never happen
      throw new ChangelogException(ERR_CHANGELOG_GENERATION_ID_WRONG_FORMAT.get(data), e);
    }
  }

  /**
   * Returns a buffered writer on the temp file (".tmp") corresponding to the provided file.
   * <p>
   * Once writes are finished, the {@code commitFile()} method should be called to finish the update
   * of the provided file.
   */
  private BufferedWriter newTempFileWriter(final File file) throws UnsupportedEncodingException, FileNotFoundException
  {
    File tempFile = getTempFileFor(file);
    return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile), UTF8_ENCODING));
  }

  /**
   * "Commit" the provided file by moving the ".tmp" file to its final location.
   * <p>
   * In order to prevent partially written environment files, update of files is always
   * performed by writing first a ".tmp" version and then switching the ".tmp" version to
   * the final version once update is finished.
   * <p>
   * This method effectively moves the ".tmp" version to the final version.
   *
   * @param file
   *          the final file location.
   */
  private void commitFile(final File file) throws IOException
  {
    File tempFile = getTempFileFor(file);
    try
    {
      Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }
    catch (FileAlreadyExistsException | AtomicMoveNotSupportedException e)
    {
      // The atomic move could fail depending on OS (mostly on old Windows versions)
      // See OPENDJ-1811 for details
      // Try to proceed with a non-atomic move
      if (file.exists())
      {
        file.delete();
      }
      Files.move(tempFile.toPath(), file.toPath());
    }
  }

  /** Returns a temporary file from provided file, by adding the ".tmp" suffix. */
  private File getTempFileFor(File file) {
    return new File(file.getParentFile(), file.getName() + FILE_EXTENSION_TEMP);
  }

  /** Returns a buffered reader on the provided file. */
  private BufferedReader newFileReader(final File file) throws UnsupportedEncodingException, FileNotFoundException
  {
    return new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8_ENCODING));
  }
}
