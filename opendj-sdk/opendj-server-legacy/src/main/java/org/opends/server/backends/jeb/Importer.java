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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static com.sleepycat.je.EnvironmentConfig.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType.*;
import static org.opends.server.backends.pluggable.SuffixContainer.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Utils;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.util.Platform;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DiskOrderedCursor;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.util.PackedInteger;

/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
final class Importer implements DiskSpaceMonitorHandler
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int TIMER_INTERVAL = 10000;
  private static final String DEFAULT_TMP_DIR = "import-tmp";
  private static final String TMPENV_DIR = "tmp-env";

  /** Defaults for DB cache. */
  private static final int MAX_DB_CACHE_SIZE = 8 * MB;
  private static final int MAX_DB_LOG_SIZE = 10 * MB;
  private static final int MIN_DB_CACHE_SIZE = 4 * MB;

  /**
   * Defaults for LDIF reader buffers, min memory required to import and default
   * size for byte buffers.
   */
  private static final int READER_WRITER_BUFFER_SIZE = 8 * KB;
  private static final int MIN_DB_CACHE_MEMORY = MAX_DB_CACHE_SIZE
      + MAX_DB_LOG_SIZE;
  private static final int BYTE_BUFFER_CAPACITY = 128;

  /** Max size of phase one buffer. */
  private static final int MAX_BUFFER_SIZE = 2 * MB;
  /** Min size of phase one buffer. */
  private static final int MIN_BUFFER_SIZE = 4 * KB;
  /** Min size of phase two read-ahead cache. */
  private static final int MIN_READ_AHEAD_CACHE_SIZE = 2 * KB;
  /** Small heap threshold used to give more memory to JVM to attempt OOM errors. */
  private static final int SMALL_HEAP_SIZE = 256 * MB;
  /** Minimum memory needed for import */
  private static final int MINIMUM_AVAILABLE_MEMORY = 32 * MB;

  /** The DN attribute type. */
  private static final AttributeType dnType;
  static final IndexOutputBuffer.IndexComparator indexComparator =
      new IndexOutputBuffer.IndexComparator();

  /** Phase one buffer count. */
  private final AtomicInteger bufferCount = new AtomicInteger(0);
  /** Phase one imported entries count. */
  private final AtomicLong importCount = new AtomicLong(0);

  /** Phase one buffer size in bytes. */
  private int bufferSize;

  /** Temp scratch directory. */
  private final File tempDir;

  /** Index count. */
  private final int indexCount;
  /** Thread count. */
  private int threadCount;

  /** Set to true when validation is skipped. */
  private final boolean skipDNValidation;

  /** Temporary environment used when DN validation is done in first phase. */
  private final TmpEnv tmpEnv;

  /** Root container. */
  private RootContainer rootContainer;

  /** Import configuration. */
  private final LDIFImportConfig importConfiguration;
  /** Backend configuration. */
  private final LocalDBBackendCfg backendConfiguration;

  /** LDIF reader. */
  private ImportLDIFReader reader;

  /** Migrated entry count. */
  private int migratedCount;

  /** Size in bytes of temporary env. */
  private long tmpEnvCacheSize;
  /** Available memory at the start of the import. */
  private long availableMemory;
  /** Size in bytes of DB cache. */
  private long dbCacheSize;

  /** The executor service used for the buffer sort tasks. */
  private ExecutorService bufferSortService;
  /** The executor service used for the scratch file processing tasks. */
  private ExecutorService scratchFileWriterService;

  /** Queue of free index buffers -- used to re-cycle index buffers. */
  private final BlockingQueue<IndexOutputBuffer> freeBufferQueue = new LinkedBlockingQueue<>();

  /**
   * Map of index keys to index buffers. Used to allocate sorted index buffers
   * to a index writer thread.
   */
  private final Map<IndexKey, BlockingQueue<IndexOutputBuffer>> indexKeyQueueMap = new ConcurrentHashMap<>();

  /** Map of DB containers to index managers. Used to start phase 2. */
  private final List<IndexManager> indexMgrList = new LinkedList<>();
  /** Map of DB containers to DN-based index managers. Used to start phase 2. */
  private final List<IndexManager> DNIndexMgrList = new LinkedList<>();

  /**
   * Futures used to indicate when the index file writers are done flushing
   * their work queues and have exited. End of phase one.
   */
  private final List<Future<Void>> scratchFileWriterFutures;
  /**
   * List of index file writer tasks. Used to signal stopScratchFileWriters to
   * the index file writer tasks when the LDIF file has been done.
   */
  private final List<ScratchFileWriterTask> scratchFileWriterList;

  /** Map of DNs to Suffix objects. */
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<>();
  /** Map of container ids to database containers. */
  private final ConcurrentHashMap<Integer, Index> idContainerMap = new ConcurrentHashMap<>();
  /** Map of container ids to entry containers. */
  private final ConcurrentHashMap<Integer, Suffix> idSuffixMap = new ConcurrentHashMap<>();

  /** Used to synchronize when a scratch file index writer is first setup. */
  private final Object synObj = new Object();

  /** Rebuild index manager used when rebuilding indexes. */
  private final RebuildIndexManager rebuildManager;

  /** Set to true if the backend was cleared. */
  private final boolean clearedBackend;

  /** Used to shutdown import if an error occurs in phase one. */
  private volatile boolean isCanceled;
  private volatile boolean isPhaseOneDone;

  /** Number of phase one buffers. */
  private int phaseOneBufferCount;

  private final DiskSpaceMonitor diskSpaceMonitor;

  static
  {
    AttributeType attrType = DirectoryServer.getAttributeType("dn");
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType("dn");
    }
    dnType = attrType;
  }

  /**
   * Create a new import job with the specified rebuild index config.
   *
   * @param rebuildConfig
   *          The rebuild index configuration.
   * @param cfg
   *          The local DB back-end configuration.
   * @param envConfig
   *          The JEB environment config.
   * @param serverContext
   *          The ServerContext for this Directory Server instance
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws JebException
   *           If an error occurred when opening the DB.
   * @throws ConfigException
   *           If a problem occurs during initialization.
   */
  public Importer(RebuildConfig rebuildConfig, LocalDBBackendCfg cfg, EnvironmentConfig envConfig,
      ServerContext serverContext) throws InitializationException,
      JebException, ConfigException
  {
    this.importConfiguration = null;
    this.backendConfiguration = cfg;
    this.tmpEnv = null;
    this.threadCount = 1;
    this.diskSpaceMonitor = serverContext.getDiskSpaceMonitor();
    this.rebuildManager = new RebuildIndexManager(rebuildConfig, cfg);
    this.indexCount = rebuildManager.getIndexCount();
    this.clearedBackend = false;
    this.scratchFileWriterList = new ArrayList<>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<>();

    this.tempDir = getTempDir(cfg, rebuildConfig.getTmpDirectory());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      throw new InitializationException(ERR_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
    }
    this.skipDNValidation = true;
    initializeDBEnv(envConfig);
  }

  /**
   * Create a new import job with the specified ldif import config.
   *
   * @param importConfiguration
   *          The LDIF import configuration.
   * @param localDBBackendCfg
   *          The local DB back-end configuration.
   * @param envConfig
   *          The JEB environment config.
   * @param serverContext
   *          The ServerContext for this Directory Server instance
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws ConfigException
   *           If a problem occurs reading the configuration.
   * @throws DatabaseException
   *           If an error occurred when opening the DB.
   */
  public Importer(LDIFImportConfig importConfiguration, LocalDBBackendCfg localDBBackendCfg,
      EnvironmentConfig envConfig, ServerContext serverContext)
      throws InitializationException, ConfigException, DatabaseException
  {
    this.rebuildManager = null;
    this.importConfiguration = importConfiguration;
    this.backendConfiguration = localDBBackendCfg;
    this.diskSpaceMonitor = serverContext.getDiskSpaceMonitor();

    if (importConfiguration.getThreadCount() == 0)
    {
      this.threadCount = Runtime.getRuntime().availableProcessors() * 2;
    }
    else
    {
      this.threadCount = importConfiguration.getThreadCount();
    }

    // Determine the number of indexes.
    this.indexCount = getTotalIndexCount(localDBBackendCfg);

    this.clearedBackend = mustClearBackend(importConfiguration, localDBBackendCfg);
    this.scratchFileWriterList = new ArrayList<>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<>();

    this.tempDir = getTempDir(localDBBackendCfg, importConfiguration.getTmpDirectory());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      throw new InitializationException(ERR_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
    }
    skipDNValidation = importConfiguration.getSkipDNValidation();
    initializeDBEnv(envConfig);

    // Set up temporary environment.
    if (!skipDNValidation)
    {
      File envPath = new File(tempDir, TMPENV_DIR);
      envPath.mkdirs();
      this.tmpEnv = new TmpEnv(envPath);
    }
    else
    {
      this.tmpEnv = null;
    }
  }

  /**
   * Returns whether the backend must be cleared.
   *
   * @param importCfg the import configuration object
   * @param backendCfg the backend configuration object
   * @return true if the backend must be cleared, false otherwise
   */
  public static boolean mustClearBackend(LDIFImportConfig importCfg, LocalDBBackendCfg backendCfg)
  {
    return !importCfg.appendToExistingData()
        && (importCfg.clearBackend() || backendCfg.getBaseDN().size() <= 1);
  }

  private File getTempDir(LocalDBBackendCfg localDBBackendCfg, String tmpDirectory)
  {
    File parentDir;
    if (tmpDirectory != null)
    {
      parentDir = getFileForPath(tmpDirectory);
    }
    else
    {
      parentDir = getFileForPath(DEFAULT_TMP_DIR);
    }
    return new File(parentDir, localDBBackendCfg.getBackendId());
  }

  private int getTotalIndexCount(LocalDBBackendCfg localDBBackendCfg)
      throws ConfigException
  {
    int indexes = 2; // dn2id, dn2uri
    for (String indexName : localDBBackendCfg.listLocalDBIndexes())
    {
      LocalDBIndexCfg index = localDBBackendCfg.getLocalDBIndex(indexName);
      SortedSet<IndexType> types = index.getIndexType();
      if (types.contains(IndexType.EXTENSIBLE))
      {
        indexes += types.size() - 1 + index.getIndexExtensibleMatchingRule().size();
      }
      else
      {
        indexes += types.size();
      }
    }
    return indexes;
  }

  /**
   * Return the suffix instance in the specified map that matches the specified
   * DN.
   *
   * @param dn
   *          The DN to search for.
   * @param map
   *          The map to search.
   * @return The suffix instance that matches the DN, or null if no match is
   *         found.
   */
  public static Suffix getMatchSuffix(DN dn, Map<DN, Suffix> map)
  {
    Suffix suffix = null;
    DN nodeDN = dn;

    while (suffix == null && nodeDN != null)
    {
      suffix = map.get(nodeDN);
      if (suffix == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }
    return suffix;
  }

  /**
   * Calculate buffer sizes and initialize JEB properties based on memory.
   *
   * @param envConfig
   *          The environment config to use in the calculations.
   * @throws InitializationException
   *           If a problem occurs during calculation.
   */
  private void initializeDBEnv(EnvironmentConfig envConfig) throws InitializationException
  {
    // Calculate amount of usable memory. This will need to take into account
    // various fudge factors, including the number of IO buffers used by the
    // scratch writers (1 per index).
    calculateAvailableMemory();

    final long usableMemory = availableMemory - (indexCount * READER_WRITER_BUFFER_SIZE);

    // We need caching when doing DN validation or rebuilding indexes.
    if (!skipDNValidation || rebuildManager != null)
    {
      // No DN validation: calculate memory for DB cache, DN2ID temporary cache,
      // and buffers.
      if (System.getProperty(PROPERTY_RUNNING_UNIT_TESTS) != null)
      {
        dbCacheSize = 500 * KB;
        tmpEnvCacheSize = 500 * KB;
      }
      else if (usableMemory < (MIN_DB_CACHE_MEMORY + MIN_DB_CACHE_SIZE))
      {
        dbCacheSize = MIN_DB_CACHE_SIZE;
        tmpEnvCacheSize = MIN_DB_CACHE_SIZE;
      }
      else if (!clearedBackend)
      {
        // Appending to existing data so reserve extra memory for the DB cache
        // since it will be needed for dn2id queries.
        dbCacheSize = usableMemory * 33 / 100;
        tmpEnvCacheSize = usableMemory * 33 / 100;
      }
      else
      {
        dbCacheSize = MAX_DB_CACHE_SIZE;
        tmpEnvCacheSize = usableMemory * 66 / 100;
      }
    }
    else
    {
      // No DN validation: calculate memory for DB cache and buffers.

      // No need for DN2ID cache.
      tmpEnvCacheSize = 0;

      if (System.getProperty(PROPERTY_RUNNING_UNIT_TESTS) != null)
      {
        dbCacheSize = 500 * KB;
      }
      else if (usableMemory < MIN_DB_CACHE_MEMORY)
      {
        dbCacheSize = MIN_DB_CACHE_SIZE;
      }
      else
      {
        // No need to differentiate between append/clear backend, since dn2id is
        // not being queried.
        dbCacheSize = MAX_DB_CACHE_SIZE;
      }
    }

    final long phaseOneBufferMemory = usableMemory - dbCacheSize - tmpEnvCacheSize;
    final int oldThreadCount = threadCount;
    if (indexCount != 0) // Avoid / by zero
    {
      while (true)
      {
        phaseOneBufferCount = 2 * indexCount * threadCount;

        // Scratch writers allocate 4 buffers per index as well.
        final int totalPhaseOneBufferCount = phaseOneBufferCount + (4 * indexCount);
        long longBufferSize = phaseOneBufferMemory / totalPhaseOneBufferCount;
        // We need (2 * bufferSize) to fit in an int for the insertByteStream
        // and deleteByteStream constructors.
        bufferSize = (int) Math.min(longBufferSize, Integer.MAX_VALUE / 2);

        if (bufferSize > MAX_BUFFER_SIZE)
        {
          if (!skipDNValidation)
          {
            // The buffers are big enough: the memory is best used for the DN2ID temp DB
            bufferSize = MAX_BUFFER_SIZE;

            final long extraMemory = phaseOneBufferMemory - (totalPhaseOneBufferCount * bufferSize);
            if (!clearedBackend)
            {
              dbCacheSize += extraMemory / 2;
              tmpEnvCacheSize += extraMemory / 2;
            }
            else
            {
              tmpEnvCacheSize += extraMemory;
            }
          }

          break;
        }
        else if (bufferSize > MIN_BUFFER_SIZE)
        {
          // This is acceptable.
          break;
        }
        else if (threadCount > 1)
        {
          // Retry using less threads.
          threadCount--;
        }
        else
        {
          // Not enough memory.
          final long minimumPhaseOneBufferMemory = totalPhaseOneBufferCount * MIN_BUFFER_SIZE;
          LocalizableMessage message =
              ERR_IMPORT_LDIF_LACK_MEM.get(usableMemory,
                  minimumPhaseOneBufferMemory + dbCacheSize + tmpEnvCacheSize);
          throw new InitializationException(message);
        }
      }
    }

    if (oldThreadCount != threadCount)
    {
      logger.info(NOTE_IMPORT_ADJUST_THREAD_COUNT, oldThreadCount, threadCount);
    }

    logger.info(NOTE_IMPORT_LDIF_TOT_MEM_BUF, availableMemory, phaseOneBufferCount);
    if (tmpEnvCacheSize > 0)
    {
      logger.info(NOTE_IMPORT_LDIF_TMP_ENV_MEM, tmpEnvCacheSize);
    }
    envConfig.setConfigParam(MAX_MEMORY, Long.toString(dbCacheSize));
    logger.info(NOTE_IMPORT_LDIF_DB_MEM_BUF_INFO, dbCacheSize, bufferSize);
  }

  /**
   * Calculates the amount of available memory which can be used by this import,
   * taking into account whether or not the import is running offline or online
   * as a task.
   */
  private void calculateAvailableMemory()
  {
    final long totalAvailableMemory;
    if (DirectoryServer.isRunning())
    {
      // Online import/rebuild.
      Runtime runTime = Runtime.getRuntime();
      // call twice gc to ensure finalizers are called
      // and young to old gen references are properly gc'd
      runTime.gc();
      runTime.gc();
      final long usedMemory = runTime.totalMemory() - runTime.freeMemory();
      final long maxUsableMemory = Platform.getUsableMemoryForCaching();
      final long usableMemory = maxUsableMemory - usedMemory;

      final long configuredMemory;
      if (backendConfiguration.getDBCacheSize() > 0)
      {
        configuredMemory = backendConfiguration.getDBCacheSize();
      }
      else
      {
        configuredMemory = backendConfiguration.getDBCachePercent() * Runtime.getRuntime().maxMemory() / 100;
      }

      // Round up to minimum of 32MB (e.g. unit tests only use a small cache).
      totalAvailableMemory = Math.max(Math.min(usableMemory, configuredMemory), MINIMUM_AVAILABLE_MEMORY);
    }
    else
    {
      // Offline import/rebuild.
      totalAvailableMemory = Platform.getUsableMemoryForCaching();
    }

    // Now take into account various fudge factors.
    int importMemPct = 90;
    if (totalAvailableMemory <= SMALL_HEAP_SIZE)
    {
      // Be pessimistic when memory is low.
      importMemPct -= 25;
    }
    if (rebuildManager != null)
    {
      // Rebuild seems to require more overhead.
      importMemPct -= 15;
    }

    availableMemory = totalAvailableMemory * importMemPct / 100;
  }

  private void initializeIndexBuffers()
  {
    for (int i = 0; i < phaseOneBufferCount; i++)
    {
      freeBufferQueue.add(new IndexOutputBuffer(bufferSize));
    }
  }

  private void initializeSuffixes() throws DatabaseException, ConfigException,
      InitializationException
  {
    for (EntryContainer ec : rootContainer.getEntryContainers())
    {
      Suffix suffix = getSuffix(ec);
      if (suffix != null)
      {
        dnSuffixMap.put(ec.getBaseDN(), suffix);
      }
    }
  }

  /**
   * Mainly used to support multiple suffixes. Each index in each suffix gets an
   * unique ID to identify which DB it needs to go to in phase two processing.
   */
  private void generateIndexID(Suffix suffix)
  {
    for (AttributeIndex attributeIndex : suffix.getAttributeIndexes())
    {
      for(Index index : attributeIndex.getAllIndexes()) {
        putInIdContainerMap(index);
      }
    }
  }

  private void putInIdContainerMap(Index index)
  {
    if (index != null)
    {
      idContainerMap.putIfAbsent(getIndexID(index), index);
    }
  }

  private static int getIndexID(DatabaseContainer index)
  {
    return System.identityHashCode(index);
  }

  private Suffix getSuffix(EntryContainer entryContainer)
      throws ConfigException, InitializationException
  {
    DN baseDN = entryContainer.getBaseDN();
    EntryContainer sourceEntryContainer = null;
    List<DN> includeBranches = new ArrayList<>();
    List<DN> excludeBranches = new ArrayList<>();

    if (!importConfiguration.appendToExistingData()
        && !importConfiguration.clearBackend())
    {
      for (DN dn : importConfiguration.getExcludeBranches())
      {
        if (baseDN.equals(dn))
        {
          // This entire base DN was explicitly excluded. Skip.
          return null;
        }
        if (baseDN.isAncestorOf(dn))
        {
          excludeBranches.add(dn);
        }
      }

      if (!importConfiguration.getIncludeBranches().isEmpty())
      {
        for (DN dn : importConfiguration.getIncludeBranches())
        {
          if (baseDN.isAncestorOf(dn))
          {
            includeBranches.add(dn);
          }
        }

        if (includeBranches.isEmpty())
        {
          /*
           * There are no branches in the explicitly defined include list under
           * this base DN. Skip this base DN all together.
           */
          return null;
        }

        // Remove any overlapping include branches.
        Iterator<DN> includeBranchIterator = includeBranches.iterator();
        while (includeBranchIterator.hasNext())
        {
          DN includeDN = includeBranchIterator.next();
          if (!isAnyNotEqualAndAncestorOf(includeBranches, includeDN))
          {
            includeBranchIterator.remove();
          }
        }

        // Remove any exclude branches that are not are not under a include
        // branch since they will be migrated as part of the existing entries
        // outside of the include branches anyways.
        Iterator<DN> excludeBranchIterator = excludeBranches.iterator();
        while (excludeBranchIterator.hasNext())
        {
          DN excludeDN = excludeBranchIterator.next();
          if (!isAnyAncestorOf(includeBranches, excludeDN))
          {
            excludeBranchIterator.remove();
          }
        }

        if (excludeBranches.isEmpty()
            && includeBranches.size() == 1
            && includeBranches.get(0).equals(baseDN))
        {
          // This entire base DN is explicitly included in the import with
          // no exclude branches that we need to migrate. Just clear the entry
          // container.
          clearSuffix(entryContainer);
        }
        else
        {
          // Create a temp entry container
          sourceEntryContainer = entryContainer;
          entryContainer = rootContainer.openEntryContainer(baseDN, baseDN.toNormalizedUrlSafeString()
              + "_importTmp");
        }
      }
    }
    return new Suffix(entryContainer, sourceEntryContainer, includeBranches, excludeBranches);
  }

  private void clearSuffix(EntryContainer entryContainer)
  {
    entryContainer.lock();
    entryContainer.clear();
    entryContainer.unlock();
  }

  private boolean isAnyNotEqualAndAncestorOf(List<DN> dns, DN childDN)
  {
    for (DN dn : dns)
    {
      if (!dn.equals(childDN) && dn.isAncestorOf(childDN))
      {
        return false;
      }
    }
    return true;
  }

  private boolean isAnyAncestorOf(List<DN> dns, DN childDN)
  {
    for (DN dn : dns)
    {
      if (dn.isAncestorOf(childDN))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Rebuild the indexes using the specified root container.
   *
   * @param rootContainer
   *          The root container to rebuild indexes in.
   * @throws ConfigException
   *           If a configuration error occurred.
   * @throws InitializationException
   *           If an initialization error occurred.
   * @throws JebException
   *           If the JEB database had an error.
   * @throws InterruptedException
   *           If an interrupted error occurred.
   * @throws ExecutionException
   *           If an execution error occurred.
   */
  public void rebuildIndexes(RootContainer rootContainer)
      throws ConfigException, InitializationException, JebException,
      InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    long startTime = System.currentTimeMillis();

    updateDiskMonitor(tempDir, "backend index rebuild tmp directory");
    File parentDirectory = getFileForPath(backendConfiguration.getDBDirectory());
    File backendDirectory = new File(parentDirectory, backendConfiguration.getBackendId());
    updateDiskMonitor(backendDirectory, "backend index rebuild DB directory");

    try
    {
      rebuildManager.initialize();
      rebuildManager.printStartMessage();
      rebuildManager.rebuildIndexes();
      recursiveDelete(tempDir);
      rebuildManager.printStopMessage(startTime);
    }
    finally
    {
      diskSpaceMonitor.deregisterMonitoredDirectory(tempDir, this);
      diskSpaceMonitor.deregisterMonitoredDirectory(backendDirectory, this);
    }
  }

  /**
   * Import a LDIF using the specified root container.
   *
   * @param rootContainer
   *          The root container to use during the import.
   * @return A LDIF result.
   * @throws ConfigException
   *           If the import failed because of an configuration error.
   * @throws InitializationException
   *           If the import failed because of an initialization error.
   * @throws JebException
   *           If the import failed due to a database error.
   * @throws InterruptedException
   *           If the import failed due to an interrupted error.
   * @throws ExecutionException
   *           If the import failed due to an execution error.
   */
  public LDIFImportResult processImport(RootContainer rootContainer)
      throws ConfigException, InitializationException, JebException,
      InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    File parentDirectory = getFileForPath(backendConfiguration.getDBDirectory());
    File backendDirectory = new File(parentDirectory, backendConfiguration.getBackendId());
    try {
      try
      {
        reader = new ImportLDIFReader(importConfiguration, rootContainer);
      }
      catch (IOException ioe)
      {
        throw new InitializationException(ERR_IMPORT_LDIF_READER_IO_ERROR.get(), ioe);
      }

      updateDiskMonitor(tempDir, "backend import tmp directory");
      updateDiskMonitor(backendDirectory, "backend import DB directory");

      logger.info(NOTE_IMPORT_STARTING, DirectoryServer.getVersionString(), BUILD_ID, REVISION_NUMBER);
      logger.info(NOTE_IMPORT_THREAD_COUNT, threadCount);
      initializeSuffixes();
      setupIndexesForImport();

      final long startTime = System.currentTimeMillis();
      phaseOne();
      isPhaseOneDone = true;
      final long phaseOneFinishTime = System.currentTimeMillis();

      if (!skipDNValidation)
      {
        tmpEnv.shutdown();
      }
      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }

      final long phaseTwoTime = System.currentTimeMillis();
      phaseTwo();
      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }
      final long phaseTwoFinishTime = System.currentTimeMillis();

      setIndexesTrusted();
      switchEntryContainers();
      recursiveDelete(tempDir);
      final long finishTime = System.currentTimeMillis();
      final long importTime = finishTime - startTime;
      logger.info(NOTE_IMPORT_PHASE_STATS, importTime / 1000,
              (phaseOneFinishTime - startTime) / 1000,
              (phaseTwoFinishTime - phaseTwoTime) / 1000);
      float rate = 0;
      if (importTime > 0)
      {
        rate = 1000f * reader.getEntriesRead() / importTime;
      }
      logger.info(NOTE_IMPORT_FINAL_STATUS, reader.getEntriesRead(), importCount.get(),
          reader.getEntriesIgnored(), reader.getEntriesRejected(),
          migratedCount, importTime / 1000, rate);
      return new LDIFImportResult(reader.getEntriesRead(),
          reader.getEntriesRejected(), reader.getEntriesIgnored());
    }
    finally
    {
      close(reader);
      if (!skipDNValidation)
      {
        try
        {
          tmpEnv.shutdown();
        }
        catch (Exception ignored)
        {
          // Do nothing.
        }
      }
      diskSpaceMonitor.deregisterMonitoredDirectory(tempDir, this);
      diskSpaceMonitor.deregisterMonitoredDirectory(backendDirectory, this);
    }
  }

  private void updateDiskMonitor(File dir, String backendSuffix)
  {
    diskSpaceMonitor.registerMonitoredDirectory(backendConfiguration.getBackendId() + " " + backendSuffix, dir,
        backendConfiguration.getDiskLowThreshold(), backendConfiguration.getDiskFullThreshold(), this);
  }

  private void recursiveDelete(File dir)
  {
    if (dir.listFiles() != null)
    {
      for (File f : dir.listFiles())
      {
        if (f.isDirectory())
        {
          recursiveDelete(f);
        }
        f.delete();
      }
    }
    dir.delete();
  }

  private void switchEntryContainers() throws DatabaseException, JebException, InitializationException
  {
    for (Suffix suffix : dnSuffixMap.values())
    {
      DN baseDN = suffix.getBaseDN();
      EntryContainer entryContainer = suffix.getSrcEntryContainer();
      if (entryContainer != null)
      {
        final EntryContainer toDelete = rootContainer.unregisterEntryContainer(baseDN);
        toDelete.lock();
        toDelete.close();
        toDelete.delete();
        toDelete.unlock();

        final EntryContainer replacement = suffix.getEntryContainer();
        replacement.lock();
        replacement.setDatabasePrefix(baseDN.toNormalizedUrlSafeString());
        replacement.unlock();
        rootContainer.registerEntryContainer(baseDN, replacement);
      }
    }
  }

  private void setIndexesTrusted() throws JebException
  {
    try
    {
      for (Suffix s : dnSuffixMap.values())
      {
        s.setIndexesTrusted();
      }
    }
    catch (DatabaseException ex)
    {
      throw new JebException(NOTE_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()));
    }
  }

  private void setupIndexesForImport()
  {
    for (Suffix s : dnSuffixMap.values())
    {
      s.setIndexesNotTrusted(importConfiguration.appendToExistingData());
      generateIndexID(s);
    }
  }

  private void phaseOne() throws InterruptedException, ExecutionException
  {
    initializeIndexBuffers();

    final ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new FirstPhaseProgressTask());
    scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
    bufferSortService = Executors.newFixedThreadPool(threadCount);
    final ExecutorService execService = Executors.newFixedThreadPool(threadCount);

    final List<Callable<Void>> tasks = new ArrayList<>(threadCount);
    tasks.add(new MigrateExistingTask());
    getAll(execService.invokeAll(tasks));
    tasks.clear();

    if (importConfiguration.appendToExistingData()
        && importConfiguration.replaceExistingEntries())
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new AppendReplaceTask());
      }
    }
    else
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new ImportTask());
      }
    }
    getAll(execService.invokeAll(tasks));
    tasks.clear();

    tasks.add(new MigrateExcludedTask());
    getAll(execService.invokeAll(tasks));

    stopScratchFileWriters();
    getAll(scratchFileWriterFutures);

    shutdownAll(timerService, execService, bufferSortService, scratchFileWriterService);

    // Try to clear as much memory as possible.
    clearAll(scratchFileWriterList, scratchFileWriterFutures, freeBufferQueue);
    indexKeyQueueMap.clear();
  }

  private void scheduleAtFixedRate(ScheduledThreadPoolExecutor timerService, Runnable task)
  {
    timerService.scheduleAtFixedRate(task, TIMER_INTERVAL, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
  }

  private void shutdownAll(ExecutorService... executorServices) throws InterruptedException
  {
    for (ExecutorService executorService : executorServices)
    {
      executorService.shutdown();
    }
    for (ExecutorService executorService : executorServices)
    {
      executorService.awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  private void clearAll(Collection<?>... cols)
  {
    for (Collection<?> col : cols)
    {
      col.clear();
    }
  }

  private void phaseTwo() throws InterruptedException, ExecutionException
  {
    ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new SecondPhaseProgressTask(reader.getEntriesRead()));
    try
    {
      processIndexFiles();
    }
    finally
    {
      shutdownAll(timerService);
    }
  }

  private void processIndexFiles() throws InterruptedException, ExecutionException
  {
    if (bufferCount.get() == 0)
    {
      return;
    }
    int dbThreads = Runtime.getRuntime().availableProcessors();
    if (dbThreads < 4)
    {
      dbThreads = 4;
    }

    // Calculate memory / buffer counts.
    final long usableMemory = availableMemory - dbCacheSize;
    int readAheadSize;
    int buffers;
    while (true)
    {
      final List<IndexManager> allIndexMgrs = new ArrayList<>(DNIndexMgrList);
      allIndexMgrs.addAll(indexMgrList);
      Collections.sort(allIndexMgrs, Collections.reverseOrder());

      buffers = 0;
      final int limit = Math.min(dbThreads, allIndexMgrs.size());
      for (int i = 0; i < limit; i++)
      {
        buffers += allIndexMgrs.get(i).numberOfBuffers;
      }

      readAheadSize = (int) (usableMemory / buffers);
      if (readAheadSize > bufferSize)
      {
        // Cache size is never larger than the buffer size.
        readAheadSize = bufferSize;
        break;
      }
      else if (readAheadSize > MIN_READ_AHEAD_CACHE_SIZE)
      {
        // This is acceptable.
        break;
      }
      else if (dbThreads > 1)
      {
        // Reduce thread count.
        dbThreads--;
      }
      else
      {
        // Not enough memory - will need to do batching for the biggest indexes.
        readAheadSize = MIN_READ_AHEAD_CACHE_SIZE;
        buffers = (int) (usableMemory / readAheadSize);

        logger.warn(WARN_IMPORT_LDIF_LACK_MEM_PHASE_TWO, usableMemory);
        break;
      }
    }

    // Ensure that there are minimum two threads available for parallel
    // processing of smaller indexes.
    dbThreads = Math.max(2, dbThreads);

    logger.info(NOTE_IMPORT_LDIF_PHASE_TWO_MEM_REPORT, availableMemory, readAheadSize, buffers);

    // Start indexing tasks.
    List<Future<Void>> futures = new LinkedList<>();
    ExecutorService dbService = Executors.newFixedThreadPool(dbThreads);
    Semaphore permits = new Semaphore(buffers);

    // Start DN processing first.
    submitIndexDBWriteTasks(DNIndexMgrList, dbService, permits, buffers, readAheadSize, futures);
    submitIndexDBWriteTasks(indexMgrList, dbService, permits, buffers, readAheadSize, futures);
    getAll(futures);
    shutdownAll(dbService);
  }

  private void submitIndexDBWriteTasks(List<IndexManager> indexMgrs, ExecutorService dbService, Semaphore permits,
      int buffers, int readAheadSize, List<Future<Void>> futures)
  {
    for (IndexManager indexMgr : indexMgrs)
    {
      futures.add(dbService.submit(new IndexDBWriteTask(indexMgr, permits, buffers, readAheadSize)));
    }
  }

  private <T> void getAll(List<Future<T>> futures) throws InterruptedException, ExecutionException
  {
    for (Future<?> result : futures)
    {
      result.get();
    }
  }

  private void stopScratchFileWriters()
  {
    final IndexOutputBuffer stopProcessing = IndexOutputBuffer.poison();
    for (ScratchFileWriterTask task : scratchFileWriterList)
    {
      task.queue.add(stopProcessing);
    }
  }

  /** Task used to migrate excluded branch. */
  private final class MigrateExcludedTask extends ImportTask
  {

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getExcludeBranches().isEmpty())
        {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          logger.info(NOTE_IMPORT_MIGRATION_START, "excluded", suffix.getBaseDN());
          Cursor cursor = entryContainer.getDN2ID().openCursor(null, CursorConfig.READ_COMMITTED);
          Comparator<byte[]> comparator = entryContainer.getDN2ID().getComparator();
          try
          {
            for (DN excludedDN : suffix.getExcludeBranches())
            {
              byte[] bytes = JebFormat.dnToDNKey(excludedDN, suffix.getBaseDN().size());
              key.setData(bytes);
              OperationStatus status = cursor.getSearchKeyRange(key, data, lockMode);
              if (status == OperationStatus.SUCCESS
                  && Arrays.equals(key.getData(), bytes))
              {
                // This is the base entry for a branch that was excluded in the
                // import so we must migrate all entries in this branch over to
                // the new entry container.
                byte[] end = Arrays.copyOf(bytes, bytes.length + 1);
                end[end.length - 1] = 0x01;

                while (status == OperationStatus.SUCCESS
                    && comparator.compare(key.getData(), end) < 0
                    && !importConfiguration.isCancelled() && !isCanceled)
                {
                  EntryID id = new EntryID(data);
                  Entry entry = entryContainer.getID2Entry().get(null, id, LockMode.DEFAULT);
                  processEntry(entry, rootContainer.getNextEntryID(), suffix);
                  migratedCount++;
                  status = cursor.getNext(key, data, lockMode);
                }
              }
            }
            flushIndexBuffers();
          }
          catch (Exception e)
          {
            logger.error(ERR_IMPORT_LDIF_MIGRATE_EXCLUDED_TASK_ERR, e.getMessage());
            isCanceled = true;
            throw e;
          }
          finally
          {
            close(cursor);
          }
        }
      }
      return null;
    }
  }

  /** Task to migrate existing entries. */
  private final class MigrateExistingTask extends ImportTask
  {

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getIncludeBranches().isEmpty())
        {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          logger.info(NOTE_IMPORT_MIGRATION_START, "existing", suffix.getBaseDN());
          Cursor cursor = entryContainer.getDN2ID().openCursor(null, null);
          try
          {
            final List<byte[]> includeBranches = includeBranchesAsBytes(suffix);
            OperationStatus status = cursor.getFirst(key, data, lockMode);
            while (status == OperationStatus.SUCCESS
                && !importConfiguration.isCancelled() && !isCanceled)
            {
              if (!find(includeBranches, key.getData()))
              {
                EntryID id = new EntryID(data);
                Entry entry = entryContainer.getID2Entry().get(null, id, LockMode.DEFAULT);
                processEntry(entry, rootContainer.getNextEntryID(), suffix);
                migratedCount++;
                status = cursor.getNext(key, data, lockMode);
              }
              else
              {
                // This is the base entry for a branch that will be included
                // in the import so we don't want to copy the branch to the
                //  new entry container.

                /*
                 * Advance the cursor to next entry at the same level in the DIT
                 * skipping all the entries in this branch. Set the next
                 * starting value to a value of equal length but slightly
                 * greater than the previous DN. Since keys are compared in
                 * reverse order we must set the first byte (the comma). No
                 * possibility of overflow here.
                 */
                byte[] begin = Arrays.copyOf(key.getData(), key.getSize() + 1);
                begin[begin.length - 1] = 0x01;
                key.setData(begin);
                status = cursor.getSearchKeyRange(key, data, lockMode);
              }
            }
            flushIndexBuffers();
          }
          catch (Exception e)
          {
            logger.error(ERR_IMPORT_LDIF_MIGRATE_EXISTING_TASK_ERR, e.getMessage());
            isCanceled = true;
            throw e;
          }
          finally
          {
            close(cursor);
          }
        }
      }
      return null;
    }

    private List<byte[]> includeBranchesAsBytes(Suffix suffix)
    {
      List<byte[]> includeBranches = new ArrayList<>(suffix.getIncludeBranches().size());
      for (DN includeBranch : suffix.getIncludeBranches())
      {
        if (includeBranch.isDescendantOf(suffix.getBaseDN()))
        {
          includeBranches.add(JebFormat.dnToDNKey(includeBranch, suffix.getBaseDN().size()));
        }
      }
      return includeBranches;
    }

    private boolean find(List<byte[]> arrays, byte[] arrayToFind)
    {
      for (byte[] array : arrays)
      {
        if (Arrays.equals(array, arrayToFind))
        {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Task to perform append/replace processing.
   */
  private class AppendReplaceTask extends ImportTask
  {
    private final Set<ByteString> insertKeySet = new HashSet<>();
    private final Set<ByteString> deleteKeySet = new HashSet<>();
    private final EntryInformation entryInfo = new EntryInformation();
    private Entry oldEntry;
    private EntryID entryID;

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isCanceled)
          {
            freeBufferQueue.add(IndexOutputBuffer.poison());
            return null;
          }
          oldEntry = null;
          Entry entry = reader.readEntry(dnSuffixMap, entryInfo);
          if (entry == null)
          {
            break;
          }
          entryID = entryInfo.getEntryID();
          Suffix suffix = entryInfo.getSuffix();
          processEntry(entry, suffix);
        }
        flushIndexBuffers();
        return null;
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_APPEND_REPLACE_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(Entry entry, Suffix suffix)
        throws DatabaseException, DirectoryException, JebException, InterruptedException
    {
      DN entryDN = entry.getName();
      DN2ID dn2id = suffix.getDN2ID();
      EntryID oldID = dn2id.get(null, entryDN, LockMode.DEFAULT);
      if (oldID != null)
      {
        oldEntry = suffix.getID2Entry().get(null, oldID, LockMode.DEFAULT);
      }
      if (oldEntry == null)
      {
        if (!skipDNValidation && !dnSanityCheck(entryDN, entry, suffix))
        {
          suffix.removePending(entryDN);
          return;
        }
        suffix.removePending(entryDN);
        processDN2ID(suffix, entryDN, entryID);
      }
      else
      {
        suffix.removePending(entryDN);
        entryID = oldID;
      }
      processDN2URI(suffix, oldEntry, entry);
      suffix.getID2Entry().put(null, entryID, entry);
      if (oldEntry != null)
      {
        processAllIndexes(suffix, entry, entryID);
      }
      else
      {
        processIndexes(suffix, entry, entryID);
      }
      importCount.getAndIncrement();
    }

    void processAllIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException, InterruptedException
    {
      for (AttributeIndex attrIndex : suffix.getAttributeIndexes())
      {
        fillIndexKey(suffix, attrIndex, entry, attrIndex.getAttributeType(), entryID);
      }
    }

    @Override
    void processAttribute(Index index, Entry entry, EntryID entryID, IndexKey indexKey)
        throws DatabaseException, InterruptedException
    {
      if (oldEntry != null)
      {
        deleteKeySet.clear();
        index.indexEntry(oldEntry, deleteKeySet);
        for (ByteString delKey : deleteKeySet)
        {
          processKey(index, delKey.toByteArray(), entryID, indexKey, false);
        }
      }
      insertKeySet.clear();
      index.indexEntry(entry, insertKeySet);
      for (ByteString key : insertKeySet)
      {
        processKey(index, key.toByteArray(), entryID, indexKey, true);
      }
    }
  }

  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private class ImportTask implements Callable<Void>
  {
    private final Map<IndexKey, IndexOutputBuffer> indexBufferMap = new HashMap<>();
    private final Set<ByteString> insertKeySet = new HashSet<>();
    private final EntryInformation entryInfo = new EntryInformation();
    private final IndexKey dnIndexKey = new IndexKey(dnType, ImportIndexType.DN.toString(), 1);
    private DatabaseEntry keyEntry = new DatabaseEntry();
    private DatabaseEntry valEntry = new DatabaseEntry();

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isCanceled)
          {
            freeBufferQueue.add(IndexOutputBuffer.poison());
            return null;
          }
          Entry entry = reader.readEntry(dnSuffixMap, entryInfo);
          if (entry == null)
          {
            break;
          }
          EntryID entryID = entryInfo.getEntryID();
          Suffix suffix = entryInfo.getSuffix();
          processEntry(entry, entryID, suffix);
        }
        flushIndexBuffers();
        return null;
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_IMPORT_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(Entry entry, EntryID entryID, Suffix suffix)
        throws DatabaseException, DirectoryException, JebException, InterruptedException
    {
      DN entryDN = entry.getName();
      if (!skipDNValidation && !dnSanityCheck(entryDN, entry, suffix))
      {
        suffix.removePending(entryDN);
        return;
      }
      suffix.removePending(entryDN);
      processDN2ID(suffix, entryDN, entryID);
      processDN2URI(suffix, null, entry);
      processIndexes(suffix, entry, entryID);
      suffix.getID2Entry().put(null, entryID, entry);
      importCount.getAndIncrement();
    }

    /** Examine the DN for duplicates and missing parents. */
    boolean dnSanityCheck(DN entryDN, Entry entry, Suffix suffix)
        throws JebException, InterruptedException
    {
      //Perform parent checking.
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      if (parentDN != null && !suffix.isParentProcessed(parentDN, tmpEnv, clearedBackend))
      {
        reader.rejectEntry(entry, ERR_IMPORT_PARENT_NOT_FOUND.get(parentDN));
        return false;
      }
      //If the backend was not cleared, then the dn2id needs to checked first
      //for DNs that might not exist in the DN cache. If the DN is not in
      //the suffixes dn2id DB, then the dn cache is used.
      if (!clearedBackend)
      {
        EntryID id = suffix.getDN2ID().get(null, entryDN, LockMode.DEFAULT);
        if (id != null || !tmpEnv.insert(entryDN, keyEntry, valEntry))
        {
          reader.rejectEntry(entry, WARN_IMPORT_ENTRY_EXISTS.get());
          return false;
        }
      }
      else if (!tmpEnv.insert(entryDN, keyEntry, valEntry))
      {
        reader.rejectEntry(entry, WARN_IMPORT_ENTRY_EXISTS.get());
        return false;
      }
      return true;
    }

    void processIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException, InterruptedException
    {
      for (AttributeIndex attrIndex : suffix.getAttributeIndexes())
      {
        AttributeType attributeType = attrIndex.getAttributeType();
        if (entry.hasAttribute(attributeType))
        {
          fillIndexKey(suffix, attrIndex, entry, attributeType, entryID);
        }
      }
    }

    void fillIndexKey(Suffix suffix, AttributeIndex attrIndex, Entry entry, AttributeType attrType, EntryID entryID)
        throws DatabaseException, InterruptedException, DirectoryException, JebException
    {
      for(Index index : attrIndex.getAllIndexes()) {
        processAttribute(index,  entry, attrType, entryID);
      }

      for (VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes())
      {
        Transaction transaction = null;
        vlvIdx.addEntry(transaction, entryID, entry);
      }
    }

    private void processAttribute(Index index, Entry entry, AttributeType attributeType, EntryID entryID)
        throws InterruptedException
    {
      if (index != null)
      {
        processAttribute(index, entry, entryID,
            new IndexKey(attributeType, index.getName(), index.getIndexEntryLimit()));
      }
    }

    private void processAttributes(Collection<Index> indexes, Entry entry, AttributeType attributeType, EntryID entryID)
        throws InterruptedException
    {
      if (indexes != null)
      {
        for (Index index : indexes)
        {
          processAttribute(index, entry, entryID,
              new IndexKey(attributeType, index.getName(), index.getIndexEntryLimit()));
        }
      }
    }

    void processAttribute(Index index, Entry entry, EntryID entryID, IndexKey indexKey)
        throws DatabaseException, InterruptedException
    {
      insertKeySet.clear();
      index.indexEntry(entry, insertKeySet);
      for (ByteString key : insertKeySet)
      {
        processKey(index, key.toByteArray(), entryID, indexKey, true);
      }
    }

    void flushIndexBuffers() throws InterruptedException, ExecutionException
    {
      final ArrayList<Future<Void>> futures = new ArrayList<>();
      Iterator<Map.Entry<IndexKey, IndexOutputBuffer>> it = indexBufferMap.entrySet().iterator();
      while (it.hasNext())
      {
        Map.Entry<IndexKey, IndexOutputBuffer> e = it.next();
        IndexKey indexKey = e.getKey();
        IndexOutputBuffer indexBuffer = e.getValue();
        it.remove();
        indexBuffer.setIndexKey(indexKey);
        indexBuffer.discard();
        futures.add(bufferSortService.submit(new SortTask(indexBuffer)));
      }
      getAll(futures);
    }

    int processKey(DatabaseContainer container, byte[] key, EntryID entryID,
        IndexKey indexKey, boolean insert) throws InterruptedException
    {
      int sizeNeeded = IndexOutputBuffer.getRequiredSize(key.length, entryID.longValue());
      IndexOutputBuffer indexBuffer = indexBufferMap.get(indexKey);
      if (indexBuffer == null)
      {
        indexBuffer = getNewIndexBuffer(sizeNeeded);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      else if (!indexBuffer.isSpaceAvailable(key, entryID.longValue()))
      {
        // complete the current buffer...
        indexBuffer.setIndexKey(indexKey);
        bufferSortService.submit(new SortTask(indexBuffer));
        // ... and get a new one
        indexBuffer = getNewIndexBuffer(sizeNeeded);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      int indexID = getIndexID(container);
      indexBuffer.add(key, entryID, indexID, insert);
      return indexID;
    }

    IndexOutputBuffer getNewIndexBuffer(int size) throws InterruptedException
    {
      IndexOutputBuffer indexBuffer;
      if (size > bufferSize)
      {
        indexBuffer = new IndexOutputBuffer(size);
        indexBuffer.discard();
      }
      else
      {
        indexBuffer = freeBufferQueue.take();
        if (indexBuffer == null)
        {
          throw new InterruptedException("Index buffer processing error.");
        }
      }
      if (indexBuffer.isPoison())
      {
        throw new InterruptedException("Cancel processing received.");
      }
      return indexBuffer;
    }

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID) throws InterruptedException
    {
      DN2ID dn2id = suffix.getDN2ID();
      byte[] dnBytes = JebFormat.dnToDNKey(dn, suffix.getBaseDN().size());
      int id = processKey(dn2id, dnBytes, entryID, dnIndexKey, true);
      idSuffixMap.putIfAbsent(id, suffix);
    }

    void processDN2URI(Suffix suffix, Entry oldEntry, Entry newEntry) throws DatabaseException
    {
      DN2URI dn2uri = suffix.getDN2URI();
      if (oldEntry != null)
      {
        dn2uri.replaceEntry(null, oldEntry, newEntry);
      }
      else
      {
        dn2uri.addEntry(null, newEntry);
      }
    }
  }

  /**
   * This task reads sorted records from the temporary index scratch files,
   * processes the records and writes the results to the index database. The DN
   * index is treated differently then non-DN indexes.
   */
  private final class IndexDBWriteTask implements Callable<Void>
  {
    private final IndexManager indexMgr;
    private final DatabaseEntry dbKey, dbValue;
    private final int cacheSize;
    private final Map<Integer, DNState> dnStateMap = new HashMap<>();
    private final Semaphore permits;
    private final int maxPermits;
    private final AtomicLong bytesRead = new AtomicLong();
    private long lastBytesRead;
    private final AtomicInteger keyCount = new AtomicInteger();
    private RandomAccessFile bufferFile;
    private DataInputStream bufferIndexFile;
    private int remainingBuffers;
    private volatile int totalBatches;
    private AtomicInteger batchNumber = new AtomicInteger();
    private int nextBufferID;
    private int ownedPermits;
    private volatile boolean isRunning;

    /**
     * Creates a new index DB writer.
     *
     * @param indexMgr
     *          The index manager.
     * @param permits
     *          The semaphore used for restricting the number of buffer
     *          allocations.
     * @param maxPermits
     *          The maximum number of buffers which can be allocated.
     * @param cacheSize
     *          The buffer cache size.
     */
    public IndexDBWriteTask(IndexManager indexMgr, Semaphore permits,
        int maxPermits, int cacheSize)
    {
      this.indexMgr = indexMgr;
      this.permits = permits;
      this.maxPermits = maxPermits;
      this.cacheSize = cacheSize;

      this.dbKey = new DatabaseEntry();
      this.dbValue = new DatabaseEntry();
    }

    /**
     * Initializes this task.
     *
     * @throws IOException
     *           If an IO error occurred.
     */
    public void beginWriteTask() throws IOException
    {
      bufferFile = new RandomAccessFile(indexMgr.getBufferFile(), "r");
      bufferIndexFile =
          new DataInputStream(new BufferedInputStream(new FileInputStream(
              indexMgr.getBufferIndexFile())));

      remainingBuffers = indexMgr.getNumberOfBuffers();
      totalBatches = (remainingBuffers / maxPermits) + 1;
      batchNumber.set(0);
      nextBufferID = 0;
      ownedPermits = 0;

      logger.info(NOTE_IMPORT_LDIF_INDEX_STARTED, indexMgr.getBufferFileName(), remainingBuffers, totalBatches);

      indexMgr.setIndexDBWriteTask(this);
      isRunning = true;
    }

    /**
     * Returns the next batch of buffers to be processed, blocking until enough
     * buffer permits are available.
     *
     * @return The next batch of buffers, or {@code null} if there are no more
     *         buffers to be processed.
     * @throws Exception
     *           If an exception occurred.
     */
    public NavigableSet<IndexInputBuffer> getNextBufferBatch() throws Exception
    {
      // First release any previously acquired permits.
      if (ownedPermits > 0)
      {
        permits.release(ownedPermits);
        ownedPermits = 0;
      }

      // Block until we can either get enough permits for all buffers, or the
      // maximum number of permits.
      final int permitRequest = Math.min(remainingBuffers, maxPermits);
      if (permitRequest == 0)
      {
        // No more work to do.
        return null;
      }
      permits.acquire(permitRequest);

      // Update counters.
      ownedPermits = permitRequest;
      remainingBuffers -= permitRequest;
      batchNumber.incrementAndGet();

      // Create all the index buffers for the next batch.
      final NavigableSet<IndexInputBuffer> buffers = new TreeSet<>();
      for (int i = 0; i < permitRequest; i++)
      {
        final long bufferBegin = bufferIndexFile.readLong();
        final long bufferEnd = bufferIndexFile.readLong();
        final IndexInputBuffer b =
            new IndexInputBuffer(indexMgr, bufferFile.getChannel(),
                bufferBegin, bufferEnd, nextBufferID++, cacheSize);
        buffers.add(b);
      }

      return buffers;
    }

    /**
     * Finishes this task.
     */
    public void endWriteTask()
    {
      isRunning = false;

      // First release any previously acquired permits.
      if (ownedPermits > 0)
      {
        permits.release(ownedPermits);
        ownedPermits = 0;
      }

      try
      {
        if (indexMgr.isDN2ID())
        {
          for (DNState dnState : dnStateMap.values())
          {
            dnState.flush();
          }
          if (!isCanceled)
          {
            logger.info(NOTE_IMPORT_LDIF_DN_CLOSE, indexMgr.getDNCount());
          }
        }
        else
        {
          if (!isCanceled)
          {
            logger.info(NOTE_IMPORT_LDIF_INDEX_CLOSE, indexMgr.getBufferFileName());
          }
        }
      }
      finally
      {
        close(bufferFile, bufferIndexFile);

        indexMgr.getBufferFile().delete();
        indexMgr.getBufferIndexFile().delete();
      }
    }

    /**
     * Print out progress stats.
     *
     * @param deltaTime
     *          The time since the last update.
     */
    public void printStats(long deltaTime)
    {
      if (isRunning)
      {
        final long bufferFileSize = indexMgr.getBufferFileSize();
        final long tmpBytesRead = bytesRead.get();
        final int currentBatch = batchNumber.get();

        final long bytesReadInterval = tmpBytesRead - lastBytesRead;
        final int bytesReadPercent =
            Math.round((100f * tmpBytesRead) / bufferFileSize);

        // Kilo and milli approximately cancel out.
        final long kiloBytesRate = bytesReadInterval / deltaTime;
        final long kiloBytesRemaining = (bufferFileSize - tmpBytesRead) / 1024;

        logger.info(NOTE_IMPORT_LDIF_PHASE_TWO_REPORT, indexMgr.getBufferFileName(),
            bytesReadPercent, kiloBytesRemaining, kiloBytesRate, currentBatch, totalBatches);

        lastBytesRead = tmpBytesRead;
      }
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception, DirectoryException
    {
      ByteBuffer key = null;
      ImportIDSet insertIDSet = null;
      ImportIDSet deleteIDSet = null;

      if (isCanceled)
      {
        return null;
      }

      try
      {
        beginWriteTask();

        NavigableSet<IndexInputBuffer> bufferSet;
        while ((bufferSet = getNextBufferBatch()) != null)
        {
          if (isCanceled)
          {
            return null;
          }

          Integer indexID = null;
          while (!bufferSet.isEmpty())
          {
            IndexInputBuffer b = bufferSet.pollFirst();
            if (key == null)
            {
              indexID = b.getIndexID();

              if (indexMgr.isDN2ID())
              {
                insertIDSet = new ImportIDSet(1, 1, false);
                deleteIDSet = new ImportIDSet(1, 1, false);
              }
              else
              {
                Index index = idContainerMap.get(indexID);
                int limit = index.getIndexEntryLimit();
                boolean doCount = index.getMaintainCount();
                insertIDSet = new ImportIDSet(1, limit, doCount);
                deleteIDSet = new ImportIDSet(1, limit, doCount);
              }

              key = ByteBuffer.allocate(b.getKeyLen());
              key.flip();
              b.fetchKey(key);

              b.mergeIDSet(insertIDSet);
              b.mergeIDSet(deleteIDSet);
              insertIDSet.setKey(key);
              deleteIDSet.setKey(key);
            }
            else if (b.compare(key, indexID) != 0)
            {
              addToDB(indexID, insertIDSet, deleteIDSet);
              keyCount.incrementAndGet();

              indexID = b.getIndexID();

              if (indexMgr.isDN2ID())
              {
                insertIDSet = new ImportIDSet(1, 1, false);
                deleteIDSet = new ImportIDSet(1, 1, false);
              }
              else
              {
                Index index = idContainerMap.get(indexID);
                int limit = index.getIndexEntryLimit();
                boolean doCount = index.getMaintainCount();
                insertIDSet = new ImportIDSet(1, limit, doCount);
                deleteIDSet = new ImportIDSet(1, limit, doCount);
              }

              key.clear();
              if (b.getKeyLen() > key.capacity())
              {
                key = ByteBuffer.allocate(b.getKeyLen());
              }
              key.flip();
              b.fetchKey(key);

              b.mergeIDSet(insertIDSet);
              b.mergeIDSet(deleteIDSet);
              insertIDSet.setKey(key);
              deleteIDSet.setKey(key);
            }
            else
            {
              b.mergeIDSet(insertIDSet);
              b.mergeIDSet(deleteIDSet);
            }

            if (b.hasMoreData())
            {
              b.fetchNextRecord();
              bufferSet.add(b);
            }
          }

          if (key != null)
          {
            addToDB(indexID, insertIDSet, deleteIDSet);
          }
        }
        return null;
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_INDEX_WRITE_DB_ERR, indexMgr.getBufferFileName(), e.getMessage());
        throw e;
      }
      finally
      {
        endWriteTask();
      }
    }

    private void addToDB(int indexID, ImportIDSet insertSet, ImportIDSet deleteSet) throws DirectoryException
    {
      if (indexMgr.isDN2ID())
      {
        addDN2ID(indexID, insertSet);
      }
      else
      {
        if (deleteSet.size() > 0 || !deleteSet.isDefined())
        {
          dbKey.setData(deleteSet.getKey().array(), 0, deleteSet.getKey().limit());
          final Index index = idContainerMap.get(indexID);
          index.delete(dbKey, deleteSet, dbValue);
        }
        if (insertSet.size() > 0 || !insertSet.isDefined())
        {
          dbKey.setData(insertSet.getKey().array(), 0, insertSet.getKey().limit());
          final Index index = idContainerMap.get(indexID);
          index.insert(dbKey, insertSet, dbValue);
        }
      }
    }

    private void addDN2ID(int indexID, ImportIDSet record) throws DirectoryException
    {
      DNState dnState;
      if (!dnStateMap.containsKey(indexID))
      {
        dnState = new DNState(idSuffixMap.get(indexID));
        dnStateMap.put(indexID, dnState);
      }
      else
      {
        dnState = dnStateMap.get(indexID);
      }
      if (dnState.checkParent(record))
      {
        dnState.writeToDB();
      }
    }

    private void addBytesRead(int bytesRead)
    {
      this.bytesRead.addAndGet(bytesRead);
    }

    /**
     * This class is used to by a index DB merge thread performing DN processing
     * to keep track of the state of individual DN2ID index processing.
     */
    class DNState
    {
      private static final int DN_STATE_CACHE_SIZE = 64 * KB;

      private ByteBuffer parentDN, lastDN;
      private EntryID parentID, lastID, entryID;
      private final DatabaseEntry dnKey, dnValue;
      private final TreeMap<ByteBuffer, EntryID> parentIDMap;
      private final EntryContainer entryContainer;
      private final boolean isSubordinatesEnabled;
      // Fields below are only needed if the isSubordinatesEnabled boolean is true.
      private Map<byte[], ImportIDSet> id2childTree;
      private Map<byte[], ImportIDSet> id2subtreeTree;
      private int childLimit, subTreeLimit;
      private boolean childDoCount, subTreeDoCount;
      private boolean updateID2Children, updateID2Subtree;

      DNState(Suffix suffix)
      {
        this.entryContainer = suffix.getEntryContainer();
        parentIDMap = new TreeMap<>();

        isSubordinatesEnabled =  backendConfiguration.isSubordinateIndexesEnabled();
        if (suffix.isProcessID2Children())
        {
          childLimit = entryContainer.getID2Children().getIndexEntryLimit();
          childDoCount = isSubordinatesEnabled && entryContainer.getID2Children().getMaintainCount();
          id2childTree = new TreeMap<>(entryContainer.getID2Children().getComparator());
          updateID2Children = true;
        }
        if (suffix.isProcessID2Subtree())
        {
          subTreeLimit = entryContainer.getID2Subtree().getIndexEntryLimit();
          subTreeDoCount = isSubordinatesEnabled && entryContainer.getID2Subtree().getMaintainCount();
          id2subtreeTree = new TreeMap<>(entryContainer.getID2Subtree().getComparator());
          updateID2Subtree = true;
        }
        dnKey = new DatabaseEntry();
        dnValue = new DatabaseEntry();
        lastDN = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
      }

      private ByteBuffer getParent(ByteBuffer buffer)
      {
        int parentIndex = JebFormat.findDNKeyParent(buffer.array(), 0, buffer.limit());
        if (parentIndex < 0)
        {
          // This is the root or base DN
          return null;
        }
        ByteBuffer parent = buffer.duplicate();
        parent.limit(parentIndex);
        return parent;
      }

      private ByteBuffer deepCopy(ByteBuffer srcBuffer, ByteBuffer destBuffer)
      {
        if (destBuffer == null
            || destBuffer.clear().remaining() < srcBuffer.limit())
        {
          byte[] bytes = new byte[srcBuffer.limit()];
          System.arraycopy(srcBuffer.array(), 0, bytes, 0, srcBuffer.limit());
          return ByteBuffer.wrap(bytes);
        }
        else
        {
          destBuffer.put(srcBuffer);
          destBuffer.flip();
          return destBuffer;
        }
      }

      /** Why do we still need this if we are checking parents in the first phase? */
      private boolean checkParent(ImportIDSet record) throws DatabaseException
      {
        dnKey.setData(record.getKey().array(), 0, record.getKey().limit());
        byte[] v = record.toDatabase();
        long v1 = JebFormat.entryIDFromDatabase(v);
        dnValue.setData(v);

        entryID = new EntryID(v1);
        parentDN = getParent(record.getKey());

        //Bypass the cache for append data, lookup the parent in DN2ID and return.
        if (importConfiguration != null
            && importConfiguration.appendToExistingData())
        {
          //If null is returned than this is a suffix DN.
          if (parentDN != null)
          {
            DatabaseEntry key = new DatabaseEntry(parentDN.array(), 0, parentDN.limit());
            DatabaseEntry value = new DatabaseEntry();
            OperationStatus status = entryContainer.getDN2ID().read(null, key, value, LockMode.DEFAULT);
            if (status == OperationStatus.SUCCESS)
            {
              parentID = new EntryID(value);
            }
            else
            {
              // We have a missing parent. Maybe parent checking was turned off?
              // Just ignore.
              parentID = null;
              return false;
            }
          }
        }
        else if (parentIDMap.isEmpty())
        {
          parentIDMap.put(deepCopy(record.getKey(), null), entryID);
          return true;
        }
        else if (lastDN != null && lastDN.equals(parentDN))
        {
          parentIDMap.put(deepCopy(lastDN, null), lastID);
          parentID = lastID;
          lastDN = deepCopy(record.getKey(), lastDN);
          lastID = entryID;
          return true;
        }
        else if (parentIDMap.lastKey().equals(parentDN))
        {
          parentID = parentIDMap.get(parentDN);
          lastDN = deepCopy(record.getKey(), lastDN);
          lastID = entryID;
          return true;
        }
        else if (parentIDMap.containsKey(parentDN))
        {
          EntryID newParentID = parentIDMap.get(parentDN);
          ByteBuffer key = parentIDMap.lastKey();
          while (!parentDN.equals(key))
          {
            parentIDMap.remove(key);
            key = parentIDMap.lastKey();
          }
          parentIDMap.put(deepCopy(record.getKey(), null), entryID);
          parentID = newParentID;
          lastDN = deepCopy(record.getKey(), lastDN);
          lastID = entryID;
        }
        else
        {
          // We have a missing parent. Maybe parent checking was turned off?
          // Just ignore.
          parentID = null;
          return false;
        }
        return true;
      }

      private void id2child(EntryID childID) throws DirectoryException
      {
        if (parentID != null)
        {
          ImportIDSet idSet;
          byte[] parentIDBytes = parentID.getDatabaseEntry().getData();
          if (!id2childTree.containsKey(parentIDBytes))
          {
            idSet = new ImportIDSet(1, childLimit, childDoCount);
            id2childTree.put(parentIDBytes, idSet);
          }
          else
          {
            idSet = id2childTree.get(parentIDBytes);
          }
          idSet.addEntryID(childID);
          if (id2childTree.size() > DN_STATE_CACHE_SIZE)
          {
            flushMapToDB(id2childTree, entryContainer.getID2Children(), true);
          }
        }
        else
        {
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_PARENT_ENTRY_IS_MISSING.get());
        }
      }

      private EntryID getParentID(ByteBuffer dn) throws DatabaseException
      {
        // Bypass the cache for append data, lookup the parent DN in the DN2ID db
        if (importConfiguration == null || !importConfiguration.appendToExistingData())
        {
          return parentIDMap.get(dn);
        }
        DatabaseEntry key = new DatabaseEntry(dn.array(), 0, dn.limit());
        DatabaseEntry value = new DatabaseEntry();
        OperationStatus status = entryContainer.getDN2ID().read(null, key, value, LockMode.DEFAULT);
        if (status == OperationStatus.SUCCESS)
        {
          return new EntryID(value);
        }
        return null;
      }

      private void id2SubTree(EntryID childID) throws DirectoryException
      {
        if (parentID != null)
        {
          ImportIDSet idSet;
          byte[] parentIDBytes = parentID.getDatabaseEntry().getData();
          if (!id2subtreeTree.containsKey(parentIDBytes))
          {
            idSet = new ImportIDSet(1, subTreeLimit, subTreeDoCount);
            id2subtreeTree.put(parentIDBytes, idSet);
          }
          else
          {
            idSet = id2subtreeTree.get(parentIDBytes);
          }
          idSet.addEntryID(childID);
          // TODO:
          // Instead of doing this,
          // we can just walk to parent cache if available
          for (ByteBuffer dn = getParent(parentDN); dn != null; dn = getParent(dn))
          {
            EntryID nodeID = getParentID(dn);
            if (nodeID == null)
            {
              // We have a missing parent. Maybe parent checking was turned off?
              // Just ignore.
              break;
            }

            byte[] nodeIDBytes = nodeID.getDatabaseEntry().getData();
            if (!id2subtreeTree.containsKey(nodeIDBytes))
            {
              idSet = new ImportIDSet(1, subTreeLimit, subTreeDoCount);
              id2subtreeTree.put(nodeIDBytes, idSet);
            }
            else
            {
              idSet = id2subtreeTree.get(nodeIDBytes);
            }
            idSet.addEntryID(childID);
          }
          if (id2subtreeTree.size() > DN_STATE_CACHE_SIZE)
          {
            flushMapToDB(id2subtreeTree, entryContainer.getID2Subtree(), true);
          }
        }
        else
        {
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_PARENT_ENTRY_IS_MISSING.get());
        }
      }

      public void writeToDB() throws DirectoryException
      {
        entryContainer.getDN2ID().put(null, dnKey, dnValue);
        indexMgr.addTotDNCount(1);
        if (isSubordinatesEnabled && parentDN != null)
        {
          if (updateID2Children)
          {
            id2child(entryID);
          }
          if (updateID2Subtree)
          {
            id2SubTree(entryID);
          }
        }
      }

      private void flushMapToDB(Map<byte[], ImportIDSet> map, Index index,
          boolean clearMap)
      {
        for (Map.Entry<byte[], ImportIDSet> e : map.entrySet())
        {
          byte[] key = e.getKey();
          ImportIDSet idSet = e.getValue();
          dnKey.setData(key);
          index.insert(dnKey, idSet, dnValue);
        }
        if (clearMap)
        {
          map.clear();
        }
      }

      public void flush()
      {
        if (isSubordinatesEnabled)
        {
          if (updateID2Children)
          {
            flushMapToDB(id2childTree, entryContainer.getID2Children(), false);
          }
          if (updateID2Subtree)
          {
            flushMapToDB(id2subtreeTree, entryContainer.getID2Subtree(), false);
          }
        }
      }
    }
  }

  /**
   * This task writes the temporary scratch index files using the sorted buffers
   * read from a blocking queue private to each index.
   */
  private final class ScratchFileWriterTask implements Callable<Void>
  {
    private static final int DRAIN_TO = 3;

    private final IndexManager indexMgr;
    private final BlockingQueue<IndexOutputBuffer> queue;
    private final ByteArrayOutputStream insertByteStream = new ByteArrayOutputStream(2 * bufferSize);
    private final ByteArrayOutputStream deleteByteStream = new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream bufferStream;
    private final DataOutputStream bufferIndexStream;
    private final byte[] tmpArray = new byte[8];
    private final TreeSet<IndexOutputBuffer> indexSortedSet = new TreeSet<>();
    private int insertKeyCount, deleteKeyCount;
    private int bufferCount;
    private boolean poisonSeen;

    public ScratchFileWriterTask(BlockingQueue<IndexOutputBuffer> queue,
        IndexManager indexMgr) throws FileNotFoundException
    {
      this.queue = queue;
      this.indexMgr = indexMgr;
      this.bufferStream = newDataOutputStream(indexMgr.getBufferFile());
      this.bufferIndexStream = newDataOutputStream(indexMgr.getBufferIndexFile());
    }

    private DataOutputStream newDataOutputStream(File file) throws FileNotFoundException
    {
      return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), READER_WRITER_BUFFER_SIZE));
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws IOException, InterruptedException
    {
      long offset = 0;
      List<IndexOutputBuffer> l = new LinkedList<>();
      try
      {
        while (true)
        {
          final IndexOutputBuffer indexBuffer = queue.take();
          long beginOffset = offset;
          long bufferLen;
          if (!queue.isEmpty())
          {
            queue.drainTo(l, DRAIN_TO);
            l.add(indexBuffer);
            bufferLen = writeIndexBuffers(l);
            for (IndexOutputBuffer id : l)
            {
              if (!id.isDiscarded())
              {
                id.reset();
                freeBufferQueue.add(id);
              }
            }
            l.clear();
          }
          else
          {
            if (indexBuffer.isPoison())
            {
              break;
            }
            bufferLen = writeIndexBuffer(indexBuffer);
            if (!indexBuffer.isDiscarded())
            {
              indexBuffer.reset();
              freeBufferQueue.add(indexBuffer);
            }
          }
          offset += bufferLen;

          // Write buffer index information.
          bufferIndexStream.writeLong(beginOffset);
          bufferIndexStream.writeLong(offset);

          bufferCount++;
          Importer.this.bufferCount.incrementAndGet();

          if (poisonSeen)
          {
            break;
          }
        }
        return null;
      }
      catch (IOException e)
      {
        logger.error(ERR_IMPORT_LDIF_INDEX_FILEWRITER_ERR, indexMgr.getBufferFile().getAbsolutePath(), e.getMessage());
        isCanceled = true;
        throw e;
      }
      finally
      {
        close(bufferStream, bufferIndexStream);
        indexMgr.setBufferInfo(bufferCount, indexMgr.getBufferFile().length());
      }
    }

    private long writeIndexBuffer(IndexOutputBuffer indexBuffer) throws IOException
    {
      indexBuffer.setPosition(-1);
      resetStreams();

      long bufferLen = 0;
      final int numberKeys = indexBuffer.getNumberKeys();
      for (int i = 0; i < numberKeys; i++)
      {
        if (indexBuffer.getPosition() == -1)
        {
          indexBuffer.setPosition(i);
          insertOrDeleteKey(indexBuffer, i);
          continue;
        }
        if (!indexBuffer.compare(i))
        {
          bufferLen += writeRecord(indexBuffer);
          indexBuffer.setPosition(i);
          resetStreams();
        }
        insertOrDeleteKeyCheckEntryLimit(indexBuffer, i);
      }
      if (indexBuffer.getPosition() != -1)
      {
        bufferLen += writeRecord(indexBuffer);
      }
      return bufferLen;
    }

    private long writeIndexBuffers(List<IndexOutputBuffer> buffers) throws IOException
    {
      resetStreams();

      long bufferID = 0;
      long bufferLen = 0;
      for (IndexOutputBuffer b : buffers)
      {
        if (b.isPoison())
        {
          poisonSeen = true;
        }
        else
        {
          b.setPosition(0);
          b.setBufferID(bufferID++);
          indexSortedSet.add(b);
        }
      }
      byte[] saveKey = null;
      int saveIndexID = 0;
      while (!indexSortedSet.isEmpty())
      {
        final IndexOutputBuffer b = indexSortedSet.pollFirst();
        if (saveKey == null)
        {
          saveKey = b.getKey();
          saveIndexID = b.getIndexID();
          insertOrDeleteKey(b, b.getPosition());
        }
        else if (!b.compare(saveKey, saveIndexID))
        {
          bufferLen += writeRecord(saveKey, saveIndexID);
          resetStreams();
          saveKey = b.getKey();
          saveIndexID = b.getIndexID();
          insertOrDeleteKey(b, b.getPosition());
        }
        else
        {
          insertOrDeleteKeyCheckEntryLimit(b, b.getPosition());
        }
        if (b.hasMoreData())
        {
          b.nextRecord();
          indexSortedSet.add(b);
        }
      }
      if (saveKey != null)
      {
        bufferLen += writeRecord(saveKey, saveIndexID);
      }
      return bufferLen;
    }

    private void resetStreams()
    {
      insertByteStream.reset();
      insertKeyCount = 0;
      deleteByteStream.reset();
      deleteKeyCount = 0;
    }

    private void insertOrDeleteKey(IndexOutputBuffer indexBuffer, int position)
    {
      if (indexBuffer.isInsertRecord(position))
      {
        indexBuffer.writeEntryID(insertByteStream, position);
        insertKeyCount++;
      }
      else
      {
        indexBuffer.writeEntryID(deleteByteStream, position);
        deleteKeyCount++;
      }
    }

    private void insertOrDeleteKeyCheckEntryLimit(IndexOutputBuffer indexBuffer, int position)
    {
      if (indexBuffer.isInsertRecord(position))
      {
        if (insertKeyCount++ <= indexMgr.getLimit())
        {
          indexBuffer.writeEntryID(insertByteStream, position);
        }
      }
      else
      {
        indexBuffer.writeEntryID(deleteByteStream, position);
        deleteKeyCount++;
      }
    }

    private int writeByteStreams() throws IOException
    {
      if (insertKeyCount > indexMgr.getLimit())
      {
        // special handling when index entry limit has been exceeded
        insertKeyCount = 1;
        insertByteStream.reset();
        writePackedLong(insertByteStream, IndexInputBuffer.UNDEFINED_SIZE);
      }

      int insertSize = writePackedInt(bufferStream, insertKeyCount);
      if (insertByteStream.size() > 0)
      {
        insertByteStream.writeTo(bufferStream);
      }

      int deleteSize = writePackedInt(bufferStream, deleteKeyCount);
      if (deleteByteStream.size() > 0)
      {
        deleteByteStream.writeTo(bufferStream);
      }
      return insertSize + insertByteStream.size() + deleteSize + deleteByteStream.size();
    }

    private int writeHeader(int indexID, int keySize) throws IOException
    {
      bufferStream.writeInt(indexID);
      return INT_SIZE + writePackedInt(bufferStream, keySize);
    }

    private int writeRecord(IndexOutputBuffer b) throws IOException
    {
      int keySize = b.getKeySize();
      int headerSize = writeHeader(b.getIndexID(), keySize);
      b.writeKey(bufferStream);
      int bodySize = writeByteStreams();
      return headerSize + keySize + bodySize;
    }

    private int writeRecord(byte[] k, int indexID) throws IOException
    {
      int keySize = k.length;
      int headerSize = writeHeader(indexID, keySize);
      bufferStream.write(k);
      int bodySize = writeByteStreams();
      return headerSize + keySize + bodySize;
    }

    private int writePackedInt(OutputStream stream, int value) throws IOException
    {
      int writeSize = PackedInteger.getWriteIntLength(value);
      PackedInteger.writeInt(tmpArray, 0, value);
      stream.write(tmpArray, 0, writeSize);
      return writeSize;
    }

    private int writePackedLong(OutputStream stream, long value) throws IOException
    {
      int writeSize = PackedInteger.getWriteLongLength(value);
      PackedInteger.writeLong(tmpArray, 0, value);
      stream.write(tmpArray, 0, writeSize);
      return writeSize;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "(" + indexMgr.getBufferFileName() + ": " + indexMgr.getBufferFile() + ")";
    }
  }

  /**
   * This task main function is to sort the index buffers given to it from the
   * import tasks reading the LDIF file. It will also create a index file writer
   * task and corresponding queue if needed. The sorted index buffers are put on
   * the index file writer queues for writing to a temporary file.
   */
  private final class SortTask implements Callable<Void>
  {

    private final IndexOutputBuffer indexBuffer;

    public SortTask(IndexOutputBuffer indexBuffer)
    {
      this.indexBuffer = indexBuffer;
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      if ((importConfiguration != null && importConfiguration.isCancelled())
          || isCanceled)
      {
        isCanceled = true;
        return null;
      }
      indexBuffer.sort();
      final IndexKey indexKey = indexBuffer.getIndexKey();
      if (!indexKeyQueueMap.containsKey(indexKey))
      {
        createIndexWriterTask(indexKey);
      }
      indexKeyQueueMap.get(indexKey).add(indexBuffer);
      return null;
    }

    private void createIndexWriterTask(IndexKey indexKey) throws FileNotFoundException
    {
      synchronized (synObj)
      {
        if (indexKeyQueueMap.containsKey(indexKey))
        {
          return;
        }
        boolean isDN2ID = ImportIndexType.DN.toString().equals(indexKey.getIndexName());
        IndexManager indexMgr = new IndexManager(indexKey.getName(), isDN2ID, indexKey.getEntryLimit());
        if (isDN2ID)
        {
          DNIndexMgrList.add(indexMgr);
        }
        else
        {
          indexMgrList.add(indexMgr);
        }
        BlockingQueue<IndexOutputBuffer> newQueue = new ArrayBlockingQueue<>(phaseOneBufferCount);
        ScratchFileWriterTask indexWriter = new ScratchFileWriterTask(newQueue, indexMgr);
        scratchFileWriterList.add(indexWriter);
        scratchFileWriterFutures.add(scratchFileWriterService.submit(indexWriter));
        indexKeyQueueMap.put(indexKey, newQueue);
      }
    }
  }

  /**
   * The index manager class has several functions:
   * <ol>
   * <li>It is used to carry information about index processing created in phase one to phase two</li>
   * <li>It collects statistics about phase two processing for each index</li>
   * <li>It manages opening and closing the scratch index files</li>
   * </ol>
   */
  final class IndexManager implements Comparable<IndexManager>
  {
    private final File bufferFile;
    private final String bufferFileName;
    private final File bufferIndexFile;
    private final boolean isDN2ID;
    private final int limit;

    private int numberOfBuffers;
    private long bufferFileSize;
    private long totalDNs;
    private volatile IndexDBWriteTask writer;

    private IndexManager(String fileName, boolean isDN2ID, int limit)
    {
      this.bufferFileName = fileName;
      this.bufferFile = new File(tempDir, bufferFileName);
      this.bufferIndexFile = new File(tempDir, bufferFileName + ".index");

      this.isDN2ID = isDN2ID;
      this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
    }

    private void setIndexDBWriteTask(IndexDBWriteTask writer)
    {
      this.writer = writer;
    }

    private File getBufferFile()
    {
      return bufferFile;
    }

    private long getBufferFileSize()
    {
      return bufferFileSize;
    }

    private File getBufferIndexFile()
    {
      return bufferIndexFile;
    }

    private void setBufferInfo(int numberOfBuffers, long bufferFileSize)
    {
      this.numberOfBuffers = numberOfBuffers;
      this.bufferFileSize = bufferFileSize;
    }

    /**
     * Updates the bytes read counter.
     *
     * @param bytesRead
     *          The number of bytes read.
     */
    void addBytesRead(int bytesRead)
    {
      if (writer != null)
      {
        writer.addBytesRead(bytesRead);
      }
    }

    private void addTotDNCount(int delta)
    {
      totalDNs += delta;
    }

    private long getDNCount()
    {
      return totalDNs;
    }

    private boolean isDN2ID()
    {
      return isDN2ID;
    }

    private void printStats(long deltaTime)
    {
      if (writer != null)
      {
        writer.printStats(deltaTime);
      }
    }

    /**
     * Returns the file name associated with this index manager.
     *
     * @return The file name associated with this index manager.
     */
    String getBufferFileName()
    {
      return bufferFileName;
    }

    private int getLimit()
    {
      return limit;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(IndexManager mgr)
    {
      return numberOfBuffers - mgr.numberOfBuffers;
    }

    private int getNumberOfBuffers()
    {
      return numberOfBuffers;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "(" + bufferFileName + ": " + bufferFile + ")";
    }
  }

  /**
   * The rebuild index manager handles all rebuild index related processing.
   */
  private class RebuildIndexManager extends ImportTask implements
      DiskSpaceMonitorHandler
  {

    /** Rebuild index configuration. */
    private final RebuildConfig rebuildConfig;
    /** Local DB backend configuration. */
    private final LocalDBBackendCfg cfg;

    /** Map of index keys to indexes. */
    private final Map<IndexKey, Index> indexMap = new LinkedHashMap<>();
    /** Map of index keys to extensible indexes. */
    private final Map<IndexKey, Collection<Index>> extensibleIndexMap = new LinkedHashMap<>();
    /** List of VLV indexes. */
    private final List<VLVIndex> vlvIndexes = new LinkedList<>();

    /** Total entries to be processed. */
    private long totalEntries;

    /** Total entries processed. */
    private final AtomicLong entriesProcessed = new AtomicLong(0);

    /** The suffix instance. */
    private Suffix suffix;

    /** The entry container. */
    private EntryContainer entryContainer;

    private boolean reBuildDN2ID;
    private boolean reBuildDN2URI;


    /**
     * Create an instance of the rebuild index manager using the specified
     * parameters.
     *
     * @param rebuildConfig
     *          The rebuild configuration to use.
     * @param cfg
     *          The local DB configuration to use.
     */
    public RebuildIndexManager(RebuildConfig rebuildConfig,
        LocalDBBackendCfg cfg)
    {
      this.rebuildConfig = rebuildConfig;
      this.cfg = cfg;
    }

    /**
     * Initialize a rebuild index manager.
     *
     * @throws ConfigException
     *           If an configuration error occurred.
     * @throws InitializationException
     *           If an initialization error occurred.
     */
    public void initialize() throws ConfigException, InitializationException
    {
      entryContainer = rootContainer.getEntryContainer(rebuildConfig.getBaseDN());
      suffix = new Suffix(entryContainer, null, null, null);
    }

    /**
     * Print start message.
     *
     * @throws DatabaseException
     *           If an database error occurred.
     */
    public void printStartMessage() throws DatabaseException
    {
      totalEntries = suffix.getID2Entry().getRecordCount();

      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        logger.info(NOTE_REBUILD_ALL_START, totalEntries);
        break;
      case DEGRADED:
        logger.info(NOTE_REBUILD_DEGRADED_START, totalEntries);
        break;
      default:
        if (!rebuildConfig.isClearDegradedState()
            && logger.isInfoEnabled())
        {
          String indexes = Utils.joinAsString(", ", rebuildConfig.getRebuildList());
          logger.info(NOTE_REBUILD_START, indexes, totalEntries);
        }
        break;
      }
    }

    /**
     * Print stop message.
     *
     * @param startTime
     *          The time the rebuild started.
     */
    public void printStopMessage(long startTime)
    {
      long finishTime = System.currentTimeMillis();
      long totalTime = finishTime - startTime;
      float rate = 0;
      if (totalTime > 0)
      {
        rate = 1000f * entriesProcessed.get() / totalTime;
      }

      if (!rebuildConfig.isClearDegradedState())
      {
        logger.info(NOTE_REBUILD_FINAL_STATUS, entriesProcessed.get(), totalTime / 1000, rate);
      }
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      ID2Entry id2entry = entryContainer.getID2Entry();
      DiskOrderedCursor cursor =
          id2entry.openCursor(DiskOrderedCursorConfig.DEFAULT);
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      try
      {
        while (cursor.getNext(key, data, null) == OperationStatus.SUCCESS)
        {
          if (isCanceled)
          {
            return null;
          }
          EntryID entryID = new EntryID(key);
          Entry entry =
              ID2Entry.entryFromDatabase(ByteString.wrap(data.getData()),
                  entryContainer.getRootContainer().getCompressedSchema());
          processEntry(entry, entryID);
          entriesProcessed.getAndIncrement();
        }
        flushIndexBuffers();
        return null;
      }
      catch (Exception e)
      {
        logger.traceException(e);
        logger.error(ERR_IMPORT_LDIF_REBUILD_INDEX_TASK_ERR, stackTraceToSingleLineString(e));
        isCanceled = true;
        throw e;
      }
      finally
      {
        close(cursor);
      }
    }

    /**
     * Perform rebuild index processing.
     *
     * @throws DatabaseException
     *           If an database error occurred.
     * @throws InterruptedException
     *           If an interrupted error occurred.
     * @throws ExecutionException
     *           If an Execution error occurred.
     * @throws JebException
     *           If an JEB error occurred.
     */
    public void rebuildIndexes() throws DatabaseException,
        InterruptedException, ExecutionException, JebException
    {
      // Sets only the needed indexes.
      setIndexesListsToBeRebuilt();

      if (!rebuildConfig.isClearDegradedState())
      {
        // If not in a 'clear degraded state' operation,
        // need to rebuild the indexes.
        setRebuildListIndexesTrusted(false);
        clearIndexes();
        phaseOne();
        if (isCanceled)
        {
          throw new InterruptedException("Rebuild Index canceled.");
        }
        phaseTwo();
      }
      else
      {
        logger.info(NOTE_REBUILD_CLEARDEGRADEDSTATE_FINAL_STATUS, rebuildConfig.getRebuildList());
      }

      setRebuildListIndexesTrusted(true);
    }

    @SuppressWarnings("fallthrough")
    private void setIndexesListsToBeRebuilt() throws JebException
    {
      // Depends on rebuild mode, (re)building indexes' lists.
      final RebuildMode mode = rebuildConfig.getRebuildMode();
      switch (mode)
      {
      case ALL:
        rebuildIndexMap(false);
        // falls through
      case DEGRADED:
        if (mode == RebuildMode.ALL
            || !entryContainer.getID2Children().isTrusted()
            || !entryContainer.getID2Subtree().isTrusted())
        {
          reBuildDN2ID = true;
        }
        if (mode == RebuildMode.ALL || entryContainer.getDN2URI() == null)
        {
          reBuildDN2URI = true;
        }
        if (mode == RebuildMode.DEGRADED
            || entryContainer.getAttributeIndexes().isEmpty())
        {
          rebuildIndexMap(true); // only degraded.
        }
        if (mode == RebuildMode.ALL || vlvIndexes.isEmpty())
        {
          vlvIndexes.addAll(entryContainer.getVLVIndexes());
        }
        break;

      case USER_DEFINED:
        // false may be required if the user wants to rebuild specific index.
        rebuildIndexMap(false);
        break;
      default:
        break;
      }
    }

    private void rebuildIndexMap(final boolean onlyDegraded)
    {
      // rebuildList contains the user-selected index(in USER_DEFINED mode).
      final List<String> rebuildList = rebuildConfig.getRebuildList();
      for (AttributeIndex attributeIndex : entryContainer.getAttributeIndexes())
      {
        final AttributeType attributeType = attributeIndex.getAttributeType();
        if (rebuildConfig.getRebuildMode() == RebuildMode.ALL
            || rebuildConfig.getRebuildMode() == RebuildMode.DEGRADED)
        {
          // Get all existing indexes for all && degraded mode.
          rebuildAttributeIndexes(attributeIndex, attributeType, onlyDegraded);
        }
        else if (!rebuildList.isEmpty())
        {
          // Get indexes for user defined index.
          for (final String index : rebuildList)
          {
            if (attributeType.getNameOrOID().toLowerCase().equals(index.toLowerCase()))
            {
              rebuildAttributeIndexes(attributeIndex, attributeType, onlyDegraded);
            }
          }
        }
      }
    }

    private void rebuildAttributeIndexes(final AttributeIndex attrIndex,
        final AttributeType attrType, final boolean onlyDegraded)
        throws DatabaseException
    {
      for(Index index : attrIndex.getAllIndexes()) {
        fillIndexMap(attrType, index, onlyDegraded);
      }
    }

    private void fillIndexMap(final AttributeType attrType, final Index index, final boolean onlyDegraded)
    {
      if (index != null
          && (!onlyDegraded || !index.isTrusted())
          && (!rebuildConfig.isClearDegradedState() || index.getRecordCount() == 0))
      {
        putInIdContainerMap(index);
        final IndexKey key = new IndexKey(attrType, index.getName(), index.getIndexEntryLimit());
        indexMap.put(key, index);
      }
    }

    private void clearIndexes() throws DatabaseException
    {
      if (reBuildDN2URI)
      {
        entryContainer.clearDatabase(entryContainer.getDN2URI());
      }
      if (reBuildDN2ID)
      {
        entryContainer.clearDatabase(entryContainer.getDN2ID());
        entryContainer.clearDatabase(entryContainer.getID2Children());
        entryContainer.clearDatabase(entryContainer.getID2Subtree());
      }

      if (!indexMap.isEmpty())
      {
        for (final Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet())
        {
          if (!mapEntry.getValue().isTrusted())
          {
            entryContainer.clearDatabase(mapEntry.getValue());
          }
        }
      }

      if (!extensibleIndexMap.isEmpty())
      {
        for (final Collection<Index> subIndexes : extensibleIndexMap.values())
        {
          if (subIndexes != null)
          {
            for (final Index subIndex : subIndexes)
            {
              entryContainer.clearDatabase(subIndex);
            }
          }
        }
      }

      for (final VLVIndex vlvIndex : entryContainer.getVLVIndexes())
      {
        if (!vlvIndex.isTrusted())
        {
          entryContainer.clearDatabase(vlvIndex);
        }
      }
    }

    private void setRebuildListIndexesTrusted(boolean trusted)
        throws JebException
    {
      try
      {
        if (reBuildDN2ID)
        {
          suffix.forceTrustedDN2IDRelated(trusted);
        }
        setTrusted(indexMap.values(), trusted);
        if (!vlvIndexes.isEmpty())
        {
          for (VLVIndex vlvIndex : vlvIndexes)
          {
            vlvIndex.setTrusted(null, trusted);
          }
        }
        if (!extensibleIndexMap.isEmpty())
        {
          for (Collection<Index> subIndexes : extensibleIndexMap.values())
          {
            setTrusted(subIndexes, trusted);
          }
        }
      }
      catch (DatabaseException ex)
      {
        throw new JebException(NOTE_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()));
      }
    }

    private void setTrusted(Collection<Index> indexes, boolean trusted)
    {
      if (indexes != null && !indexes.isEmpty())
      {
        for (Index index : indexes)
        {
          index.setTrusted(null, trusted);
        }
      }
    }

    private void phaseOne() throws DatabaseException, InterruptedException,
        ExecutionException
    {
      initializeIndexBuffers();
      Timer timer = scheduleAtFixedRate(new RebuildFirstPhaseProgressTask());
      scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
      bufferSortService = Executors.newFixedThreadPool(threadCount);
      ExecutorService rebuildIndexService = Executors.newFixedThreadPool(threadCount);
      List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(this);
      }
      List<Future<Void>> results = rebuildIndexService.invokeAll(tasks);
      getAll(results);
      stopScratchFileWriters();
      getAll(scratchFileWriterFutures);

      // Try to clear as much memory as possible.
      shutdownAll(rebuildIndexService, bufferSortService, scratchFileWriterService);
      timer.cancel();

      clearAll(tasks, results, scratchFileWriterList, scratchFileWriterFutures, freeBufferQueue);
      indexKeyQueueMap.clear();
    }

    private void phaseTwo() throws InterruptedException, ExecutionException
    {
      final Timer timer = scheduleAtFixedRate(new SecondPhaseProgressTask(entriesProcessed.get()));
      try
      {
        processIndexFiles();
      }
      finally
      {
        timer.cancel();
      }
    }

    private Timer scheduleAtFixedRate(TimerTask task)
    {
      final Timer timer = new Timer();
      timer.scheduleAtFixedRate(task, TIMER_INTERVAL, TIMER_INTERVAL);
      return timer;
    }

    private int getIndexCount() throws ConfigException, JebException,
        InitializationException
    {
      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        return getTotalIndexCount(cfg);
      case DEGRADED:
        // FIXME: since the environment is not started we cannot determine which
        // indexes are degraded. As a workaround, be conservative and assume all
        // indexes need rebuilding.
        return getTotalIndexCount(cfg);
      default:
        return getRebuildListIndexCount(cfg);
      }
    }

    private int getRebuildListIndexCount(LocalDBBackendCfg cfg)
        throws JebException, ConfigException, InitializationException
    {
      final List<String> rebuildList = rebuildConfig.getRebuildList();
      if (rebuildList.isEmpty())
      {
        return 0;
      }

      int indexCount = 0;
      for (String index : rebuildList)
      {
        final String lowerName = index.toLowerCase();
        if (DN2ID_INDEX_NAME.equals(lowerName))
        {
          indexCount += 3;
        }
        else if (DN2URI_INDEX_NAME.equals(lowerName))
        {
          indexCount++;
        }
        else if (lowerName.startsWith("vlv."))
        {
          if (lowerName.length() < 5)
          {
            throw new JebException(ERR_VLV_INDEX_NOT_CONFIGURED.get(lowerName));
          }
          indexCount++;
        }
        else if (ID2SUBTREE_INDEX_NAME.equals(lowerName)
            || ID2CHILDREN_INDEX_NAME.equals(lowerName))
        {
          throw attributeIndexNotConfigured(index);
        }
        else
        {
          final String[] attrIndexParts = lowerName.split("\\.");
          if (attrIndexParts.length <= 0 || attrIndexParts.length > 3)
          {
            throw attributeIndexNotConfigured(index);
          }
          AttributeType attrType = DirectoryServer.getAttributeType(attrIndexParts[0]);
          if (attrType == null)
          {
            throw attributeIndexNotConfigured(index);
          }
          if (attrIndexParts.length != 1)
          {
            final String indexType = attrIndexParts[1];
            if (attrIndexParts.length == 2)
            {
              if ("presence".equals(indexType)
                  || "equality".equals(indexType)
                  || "ordering".equals(indexType)
                  || "substring".equals(indexType)
                  || "approximate".equals(indexType))
              {
                indexCount++;
              }
              else
              {
                throw attributeIndexNotConfigured(index);
              }
            }
            else // attrIndexParts.length == 3
            {
              if (!findExtensibleMatchingRule(cfg, indexType + "." + attrIndexParts[2]))
              {
                throw attributeIndexNotConfigured(index);
              }
              indexCount++;
            }
          }
          else
          {
            boolean found = false;
            for (final String idx : cfg.listLocalDBIndexes())
            {
              if (idx.equalsIgnoreCase(index))
              {
                found = true;
                final LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
                indexCount += getAttributeIndexCount(indexCfg.getIndexType(),
                    PRESENCE, EQUALITY, ORDERING, SUBSTRING, APPROXIMATE);
                indexCount += getExtensibleIndexCount(indexCfg);
              }
            }
            if (!found)
            {
              throw attributeIndexNotConfigured(index);
            }
          }
        }
      }
      return indexCount;
    }

    private InitializationException attributeIndexNotConfigured(String index)
    {
      return new InitializationException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index));
    }

    private boolean findExtensibleMatchingRule(LocalDBBackendCfg cfg, String indexExRuleName) throws ConfigException
    {
      for (String idx : cfg.listLocalDBIndexes())
      {
        LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
        if (indexCfg.getIndexType().contains(EXTENSIBLE))
        {
          for (String exRule : indexCfg.getIndexExtensibleMatchingRule())
          {
            if (exRule.equalsIgnoreCase(indexExRuleName))
            {
              return true;
            }
          }
        }
      }
      return false;
    }

    private int getAttributeIndexCount(SortedSet<IndexType> indexTypes, IndexType... toFinds)
    {
      int result = 0;
      for (IndexType toFind : toFinds)
      {
        if (indexTypes.contains(toFind))
        {
          result++;
        }
      }
      return result;
    }

    private int getExtensibleIndexCount(LocalDBIndexCfg indexCfg)
    {
      int result = 0;
      if (indexCfg.getIndexType().contains(EXTENSIBLE))
      {
        boolean shared = false;
        for (final String exRule : indexCfg.getIndexExtensibleMatchingRule())
        {
          if (exRule.endsWith(".sub"))
          {
            result++;
          }
          else if (!shared)
          {
            shared = true;
            result++;
          }
        }
      }
      return result;
    }

    private void processEntry(Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException,
        InterruptedException
    {
      if (reBuildDN2ID)
      {
        processDN2ID(suffix, entry.getName(), entryID);
      }
      if (reBuildDN2URI)
      {
        processDN2URI(suffix, null, entry);
      }
      processIndexes(entry, entryID);
      processExtensibleIndexes(entry, entryID);
      processVLVIndexes(entry, entryID);
    }

    private void processVLVIndexes(Entry entry, EntryID entryID)
        throws DatabaseException, JebException, DirectoryException
    {
      for (VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes())
      {
        Transaction transaction = null;
        vlvIdx.addEntry(transaction, entryID, entry);
      }
    }

    private void processExtensibleIndexes(Entry entry, EntryID entryID)
        throws InterruptedException
    {
      for (Map.Entry<IndexKey, Collection<Index>> mapEntry :
        this.extensibleIndexMap.entrySet())
      {
        IndexKey key = mapEntry.getKey();
        AttributeType attrType = key.getAttributeType();
        if (entry.hasAttribute(attrType))
        {
          for (Index index : mapEntry.getValue())
          {
            processAttribute(index, entry, entryID, key);
          }
        }
      }
    }

    private void processIndexes(Entry entry, EntryID entryID)
        throws DatabaseException, InterruptedException
    {
      for (Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet())
      {
        IndexKey key = mapEntry.getKey();
        AttributeType attrType = key.getAttributeType();
        if (entry.hasAttribute(attrType))
        {
          processAttribute(mapEntry.getValue(), entry, entryID, key);
        }
      }
    }

    /**
     * Return the number of entries processed by the rebuild manager.
     *
     * @return The number of entries processed.
     */
    public long getEntriesProcessed()
    {
      return this.entriesProcessed.get();
    }

    /**
     * Return the total number of entries to process by the rebuild manager.
     *
     * @return The total number for entries to process.
     */
    public long getTotalEntries()
    {
      return this.totalEntries;
    }

    @Override
    public void diskLowThresholdReached(File directory, long thresholdInBytes)
    {
      diskFullThresholdReached(directory, thresholdInBytes);
    }

    @Override
    public void diskFullThresholdReached(File directory, long thresholdInBytes)
    {
      isCanceled = true;
      logger.error(ERR_REBUILD_INDEX_LACK_DISK, directory.getAbsolutePath(), thresholdInBytes);
    }

    @Override
    public void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes)
    {
      // Do nothing
    }
  }

  /**
   * This class reports progress of rebuild index processing at fixed intervals.
   */
  private class RebuildFirstPhaseProgressTask extends TimerTask
  {
    /**
     * The number of records that had been processed at the time of the previous
     * progress report.
     */
    private long previousProcessed;
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;
    /** The environment statistics at the time of the previous report. */
    private EnvironmentStats prevEnvStats;

    /**
     * Create a new rebuild index progress task.
     *
     * @throws DatabaseException
     *           If an error occurred while accessing the JE database.
     */
    public RebuildFirstPhaseProgressTask() throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats = rootContainer.getEnvironmentStats(new StatsConfig());
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run()
    {
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }
      long entriesProcessed = rebuildManager.getEntriesProcessed();
      long deltaCount = entriesProcessed - previousProcessed;
      float rate = 1000f * deltaCount / deltaTime;
      float completed = 0;
      if (rebuildManager.getTotalEntries() > 0)
      {
        completed = 100f * entriesProcessed / rebuildManager.getTotalEntries();
      }
      logger.info(NOTE_REBUILD_PROGRESS_REPORT, completed, entriesProcessed, rebuildManager.getTotalEntries(), rate);
      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / MB;
        EnvironmentStats envStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss = envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss / (float) deltaCount;
        }
        logger.info(INFO_CACHE_AND_MEMORY_REPORT, freeMemory, cacheMissRate);
        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }
      previousProcessed = entriesProcessed;
      previousTime = latestTime;
    }
  }

  /**
   * This class reports progress of first phase of import processing at fixed
   * intervals.
   */
  private final class FirstPhaseProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been read at the time of the previous
     * progress report.
     */
    private long previousCount;
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;
    /** The environment statistics at the time of the previous report. */
    private EnvironmentStats previousStats;
    /** Determines if eviction has been detected. */
    private boolean evicting;
    /** Entry count when eviction was detected. */
    private long evictionEntryCount;

    /** Create a new import progress task. */
    public FirstPhaseProgressTask()
    {
      previousTime = System.currentTimeMillis();
      try
      {
        previousStats = rootContainer.getEnvironmentStats(new StatsConfig());
      }
      catch (DatabaseException e)
      {
        throw new RuntimeException(e);
      }
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long entriesRead = reader.getEntriesRead();
      long entriesIgnored = reader.getEntriesIgnored();
      long entriesRejected = reader.getEntriesRejected();
      long deltaCount = entriesRead - previousCount;

      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }
      float rate = 1000f * deltaCount / deltaTime;
      logger.info(NOTE_IMPORT_PROGRESS_REPORT, entriesRead, entriesIgnored, entriesRejected, rate);
      try
      {
        Runtime runTime = Runtime.getRuntime();
        long freeMemory = runTime.freeMemory() / MB;
        EnvironmentStats environmentStats;

        //If first phase skip DN validation is specified use the root container
        //stats, else use the temporary environment stats.
        if (skipDNValidation)
        {
          environmentStats =
              rootContainer.getEnvironmentStats(new StatsConfig());
        }
        else
        {
          environmentStats = tmpEnv.getEnvironmentStats(new StatsConfig());
        }
        long nCacheMiss =
            environmentStats.getNCacheMiss() - previousStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss / (float) deltaCount;
        }
        logger.info(INFO_CACHE_AND_MEMORY_REPORT, freeMemory, cacheMissRate);
        long evictPasses = environmentStats.getNEvictPasses();
        long evictNodes = environmentStats.getNNodesExplicitlyEvicted();
        long evictBinsStrip = environmentStats.getNBINsStripped();
        long cleanerRuns = environmentStats.getNCleanerRuns();
        long cleanerDeletions = environmentStats.getNCleanerDeletions();
        long cleanerEntriesRead = environmentStats.getNCleanerEntriesRead();
        long cleanerINCleaned = environmentStats.getNINsCleaned();
        long checkPoints = environmentStats.getNCheckpoints();
        if (evictPasses != 0)
        {
          if (!evicting)
          {
            evicting = true;
            evictionEntryCount = reader.getEntriesRead();
            logger.info(NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED, evictionEntryCount);
          }
          logger.info(NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS, evictPasses,
                  evictNodes, evictBinsStrip);
        }
        if (cleanerRuns != 0)
        {
          logger.info(NOTE_JEB_IMPORT_LDIF_CLEANER_STATS, cleanerRuns,
                  cleanerDeletions, cleanerEntriesRead, cleanerINCleaned);
        }
        if (checkPoints > 1)
        {
          logger.info(NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS, checkPoints);
        }
        previousStats = environmentStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }
      previousCount = entriesRead;
      previousTime = latestTime;
    }
  }

  /**
   * This class reports progress of the second phase of import processing at
   * fixed intervals.
   */
  private class SecondPhaseProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been read at the time of the previous
     * progress report.
     */
    private long previousCount;
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;
    /** The environment statistics at the time of the previous report. */
    private EnvironmentStats previousStats;
    /** Determines if eviction has been detected. */
    private boolean evicting;
    private long latestCount;

    /**
     * Create a new import progress task.
     *
     * @param latestCount
     *          The latest count of entries processed in phase one.
     */
    public SecondPhaseProgressTask(long latestCount)
    {
      previousTime = System.currentTimeMillis();
      this.latestCount = latestCount;
      try
      {
        previousStats = rootContainer.getEnvironmentStats(new StatsConfig());
      }
      catch (DatabaseException e)
      {
        throw new RuntimeException(e);
      }
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }
      try
      {
        Runtime runTime = Runtime.getRuntime();
        long freeMemory = runTime.freeMemory() / MB;
        EnvironmentStats environmentStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss =
            environmentStats.getNCacheMiss() - previousStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss / (float) deltaCount;
        }
        logger.info(INFO_CACHE_AND_MEMORY_REPORT, freeMemory, cacheMissRate);
        long evictPasses = environmentStats.getNEvictPasses();
        long evictNodes = environmentStats.getNNodesExplicitlyEvicted();
        long evictBinsStrip = environmentStats.getNBINsStripped();
        long cleanerRuns = environmentStats.getNCleanerRuns();
        long cleanerDeletions = environmentStats.getNCleanerDeletions();
        long cleanerEntriesRead = environmentStats.getNCleanerEntriesRead();
        long cleanerINCleaned = environmentStats.getNINsCleaned();
        long checkPoints = environmentStats.getNCheckpoints();
        if (evictPasses != 0)
        {
          if (!evicting)
          {
            evicting = true;
          }
          logger.info(NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS, evictPasses,
                  evictNodes, evictBinsStrip);
        }
        if (cleanerRuns != 0)
        {
          logger.info(NOTE_JEB_IMPORT_LDIF_CLEANER_STATS, cleanerRuns,
                  cleanerDeletions, cleanerEntriesRead, cleanerINCleaned);
        }
        if (checkPoints > 1)
        {
          logger.info(NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS, checkPoints);
        }
        previousStats = environmentStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }
      previousCount = latestCount;
      previousTime = latestTime;

      //Do DN index managers first.
      for (IndexManager indexMgrDN : DNIndexMgrList)
      {
        indexMgrDN.printStats(deltaTime);
      }
      //Do non-DN index managers.
      for (IndexManager indexMgr : indexMgrList)
      {
        indexMgr.printStats(deltaTime);
      }
    }
  }

  /**
   * A class to hold information about the entry determined by the LDIF reader.
   * Mainly the suffix the entry belongs under and the ID assigned to it by the
   * reader.
   */
  public class EntryInformation
  {
    private EntryID entryID;
    private Suffix suffix;

    /**
     * Return the suffix associated with the entry.
     *
     * @return Entry's suffix instance;
     */
    public Suffix getSuffix()
    {
      return suffix;
    }

    /**
     * Set the suffix instance associated with the entry.
     *
     * @param suffix
     *          The suffix associated with the entry.
     */
    public void setSuffix(Suffix suffix)
    {
      this.suffix = suffix;
    }

    /**
     * Set the entry's ID.
     *
     * @param entryID
     *          The entry ID to set the entry ID to.
     */
    public void setEntryID(EntryID entryID)
    {
      this.entryID = entryID;
    }

    /**
     * Return the entry ID associated with the entry.
     *
     * @return The entry ID associated with the entry.
     */
    public EntryID getEntryID()
    {
      return entryID;
    }
  }

  /**
   * This class defines the individual index type available.
   */
  private enum ImportIndexType
  {
    /** The DN index type. */
    DN,
    /** The equality index type. */
    EQUALITY,
    /** The presence index type. */
    PRESENCE,
    /** The sub-string index type. */
    SUBSTRING,
    /** The ordering index type. */
    ORDERING,
    /** The approximate index type. */
    APPROXIMATE,
    /** The extensible sub-string index type. */
    EX_SUBSTRING,
    /** The extensible shared index type. */
    EX_SHARED,
    /** The vlv index type. */
    VLV
  }

  /**
   * This class is used as an index key for hash maps that need to process
   * multiple suffix index elements into a single queue and/or maps based on
   * both attribute type and index type (ie., cn.equality, sn.equality,...).
   */
  public static class IndexKey
  {

    private final AttributeType attributeType;
    private final String indexName;
    private final int entryLimit;

    /**
     * Create index key instance using the specified attribute type, index type
     * and index entry limit.
     *
     * @param attributeType
     *          The attribute type.
     * @param indexType
     *          The index type.
     * @param entryLimit
     *          The entry limit for the index.
     */
    private IndexKey(AttributeType attributeType, String indexName, int entryLimit)
    {
      this.attributeType = attributeType;
      this.indexName = indexName;
      this.entryLimit = entryLimit;
    }

    /**
     * An equals method that uses both the attribute type and the index type.
     * Only returns {@code true} if the attribute type and index type are equal.
     *
     * @param obj
     *          the object to compare.
     * @return {@code true} if the objects are equal, or {@code false} if they
     *         are not.
     */
    @Override
    public boolean equals(Object obj)
    {
      if (obj instanceof IndexKey)
      {
        IndexKey oKey = (IndexKey) obj;
        if (attributeType.equals(oKey.getAttributeType())
            && indexName.equals(oKey.indexName))
        {
          return true;
        }
      }
      return false;
    }

    /**
     * A hash code method that adds the hash codes of the attribute type and
     * index type and returns that value.
     *
     * @return The combined hash values of attribute type hash code and the
     *         index type hash code.
     */
    @Override
    public int hashCode()
    {
      return attributeType.hashCode() + indexName.hashCode();
    }

    /**
     * Return the attribute type.
     *
     * @return The attribute type.
     */
    public AttributeType getAttributeType()
    {
      return attributeType;
    }

    /**
     * Return the index type.
     *
     * @return The index type.
     */
    public String getIndexName()
    {
      return indexName;
    }

    /**
     * Return the index key name, which is the attribute type primary name, a
     * period, and the index type name. Used for building file names and
     * progress output.
     *
     * @return The index key name.
     */
    public String getName()
    {
      return attributeType.getPrimaryName() + "."
          + StaticUtils.toLowerCase(indexName);
    }

    /**
     * Return the entry limit associated with the index.
     *
     * @return The entry limit.
     */
    public int getEntryLimit()
    {
      return entryLimit;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName()
          + "(index=" + attributeType.getNameOrOID() + "." + indexName
          + ", entryLimit=" + entryLimit
          + ")";
    }
  }

  /**
   * The temporary environment will be shared when multiple suffixes are being
   * processed. This interface is used by those suffix instance to do parental
   * checking of the DN cache.
   */
  public static interface DNCache
  {

    /**
     * Returns {@code true} if the specified DN is contained in the DN cache, or
     * {@code false} otherwise.
     *
     * @param dn
     *          The DN to check the presence of.
     * @return {@code true} if the cache contains the DN, or {@code false} if it
     *         is not.
     * @throws DatabaseException
     *           If an error occurs reading the database.
     */
    boolean contains(DN dn) throws DatabaseException;
  }

  /**
   * Temporary environment used to check DN's when DN validation is performed
   * during phase one processing. It is deleted after phase one processing.
   */
  private final class TmpEnv implements DNCache
  {
    private final String envPath;
    private final Environment environment;
    private static final String DB_NAME = "dn_cache";
    private final Database dnCache;

    /**
     * Create a temporary DB environment and database to be used as a cache of
     * DNs when DN validation is performed in phase one processing.
     *
     * @param envPath
     *          The file path to create the environment under.
     * @throws DatabaseException
     *           If an error occurs either creating the environment or the DN
     *           database.
     */
    private TmpEnv(File envPath) throws DatabaseException
    {
      EnvironmentConfig envConfig = new EnvironmentConfig();
      envConfig.setConfigParam(ENV_RUN_CLEANER, "true");
      envConfig.setReadOnly(false);
      envConfig.setAllowCreate(true);
      envConfig.setTransactional(false);
      envConfig.setConfigParam(ENV_IS_LOCKING, "true");
      envConfig.setConfigParam(ENV_RUN_CHECKPOINTER, "false");
      envConfig.setConfigParam(EVICTOR_LRU_ONLY, "false");
      envConfig.setConfigParam(EVICTOR_NODES_PER_SCAN, "128");
      envConfig.setConfigParam(MAX_MEMORY, Long.toString(tmpEnvCacheSize));
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setAllowCreate(true);
      dbConfig.setTransactional(false);
      dbConfig.setTemporary(true);
      environment = new Environment(envPath, envConfig);
      dnCache = environment.openDatabase(null, DB_NAME, dbConfig);
      this.envPath = envPath.getPath();
    }

    private static final long FNV_INIT = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Hash the DN bytes. Uses the FNV-1a hash. */
    private byte[] hashCode(byte[] b)
    {
      long hash = FNV_INIT;
      for (byte aB : b)
      {
        hash ^= aB;
        hash *= FNV_PRIME;
      }
      return JebFormat.entryIDToDatabase(hash);
    }

    /**
     * Shutdown the temporary environment.
     *
     * @throws JebException
     *           If error occurs.
     */
    private void shutdown() throws JebException
    {
      dnCache.close();
      environment.close();
      EnvManager.removeFiles(envPath);
    }

    /**
     * Insert the specified DN into the DN cache. It will return {@code true} if
     * the DN does not already exist in the cache and was inserted, or
     * {@code false} if the DN exists already in the cache.
     *
     * @param dn
     *          The DN to insert in the cache.
     * @param val
     *          A database entry to use in the insert.
     * @param key
     *          A database entry to use in the insert.
     * @return {@code true} if the DN was inserted in the cache, or
     *         {@code false} if the DN exists in the cache already and could not
     *         be inserted.
     * @throws JebException
     *           If an error occurs accessing the database.
     */
    private boolean insert(DN dn, DatabaseEntry val, DatabaseEntry key)
        throws JebException
    {
      // Use a compact representation for key
      byte[] dnBytesForKey = dn.toNormalizedByteString().toByteArray();
      key.setData(hashCode(dnBytesForKey));

      // Use a reversible representation for value
      byte[] dnBytesForValue = StaticUtils.getBytes(dn.toString());
      int len = PackedInteger.getWriteIntLength(dnBytesForValue.length);
      byte[] dataBytes = new byte[dnBytesForValue.length + len];
      int pos = PackedInteger.writeInt(dataBytes, 0, dnBytesForValue.length);
      System.arraycopy(dnBytesForValue, 0, dataBytes, pos, dnBytesForValue.length);
      val.setData(dataBytes);

      return insert(key, val, dnBytesForValue);
    }

    private boolean insert(DatabaseEntry key, DatabaseEntry val, byte[] dnBytesForValue)
        throws JebException
    {
      Cursor cursor = null;
      try
      {
        cursor = dnCache.openCursor(null, CursorConfig.DEFAULT);
        OperationStatus status = cursor.putNoOverwrite(key, val);
        if (status == OperationStatus.KEYEXIST)
        {
          DatabaseEntry dns = new DatabaseEntry();
          status = cursor.getSearchKey(key, dns, LockMode.RMW);
          if (status == OperationStatus.NOTFOUND)
          {
            throw new JebException(LocalizableMessage.raw("Search DN cache failed."));
          }
          if (!isDNMatched(dns.getData(), dnBytesForValue))
          {
            addDN(dns.getData(), cursor, dnBytesForValue);
            return true;
          }
          return false;
        }
        return true;
      }
      finally
      {
        close(cursor);
      }
    }

    /** Add the DN to the DNs as because of a hash collision. */
    private void addDN(byte[] readDnBytes, Cursor cursor, byte[] dnBytesForValue) throws JebException
    {
      int pLen = PackedInteger.getWriteIntLength(dnBytesForValue.length);
      int totLen = readDnBytes.length + pLen + dnBytesForValue.length;
      byte[] newRec = new byte[totLen];
      System.arraycopy(readDnBytes, 0, newRec, 0, readDnBytes.length);
      int pos = PackedInteger.writeInt(newRec, readDnBytes.length, dnBytesForValue.length);
      System.arraycopy(dnBytesForValue, 0, newRec, pos, dnBytesForValue.length);
      DatabaseEntry newVal = new DatabaseEntry(newRec);
      OperationStatus status = cursor.putCurrent(newVal);
      if (status != OperationStatus.SUCCESS)
      {
        throw new JebException(LocalizableMessage.raw("Add of DN to DN cache failed."));
      }
    }

    /** Return true if the specified DN is in the DNs saved as a result of hash collisions. */
    private boolean isDNMatched(byte[] readDnBytes, byte[] dnBytes)
    {
      int pos = 0;
      while (pos < readDnBytes.length)
      {
        int pLen = PackedInteger.getReadIntLength(readDnBytes, pos);
        int len = PackedInteger.readInt(readDnBytes, pos);
        if (indexComparator.compare(readDnBytes, pos + pLen, len, dnBytes, dnBytes.length) == 0)
        {
          return true;
        }
        pos += pLen + len;
      }
      return false;
    }

    /**
     * Check if the specified DN is contained in the temporary DN cache.
     *
     * @param dn
     *          A DN check for.
     * @return {@code true} if the specified DN is in the temporary DN cache, or
     *         {@code false} if it is not.
     */
    @Override
    public boolean contains(DN dn)
    {
      Cursor cursor = null;
      DatabaseEntry key = new DatabaseEntry();
      byte[] dnBytesForKey = dn.toNormalizedByteString().toByteArray();
      key.setData(hashCode(dnBytesForKey));
      try
      {
        cursor = dnCache.openCursor(null, CursorConfig.DEFAULT);
        DatabaseEntry dns = new DatabaseEntry();
        OperationStatus status = cursor.getSearchKey(key, dns, LockMode.DEFAULT);
        byte[] dnBytesForValue = StaticUtils.getBytes(dn.toString());
        return status == OperationStatus.SUCCESS && isDNMatched(dns.getData(), dnBytesForValue);
      }
      finally
      {
        close(cursor);
      }
    }

    /**
     * Return temporary environment stats.
     *
     * @param statsConfig
     *          A stats configuration instance.
     * @return Environment stats.
     * @throws DatabaseException
     *           If an error occurs retrieving the stats.
     */
    private EnvironmentStats getEnvironmentStats(StatsConfig statsConfig)
        throws DatabaseException
    {
      return environment.getStats(statsConfig);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(File directory, long thresholdInBytes)
  {
    diskFullThresholdReached(directory, thresholdInBytes);
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(File directory, long thresholdInBytes)
  {
    isCanceled = true;
    Arg2<Object, Number> argMsg = !isPhaseOneDone
        ? ERR_IMPORT_LDIF_LACK_DISK_PHASE_ONE
        : ERR_IMPORT_LDIF_LACK_DISK_PHASE_TWO;
    logger.error(argMsg.get(directory.getAbsolutePath(), thresholdInBytes));
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes)
  {
    // Do nothing.
  }
}
