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
package org.opends.server.backends.pluggable;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType.*;
import static org.opends.server.backends.pluggable.IndexOutputBuffer.*;
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
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg3;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.forgerock.util.Utils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.backends.persistit.PersistItStorage;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.core.DirectoryServer;
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

/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
final class Importer implements DiskSpaceMonitorHandler
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int TIMER_INTERVAL = 10000;
  private static final int KB = 1024;
  private static final int MB = KB * KB;
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
  private final PersistitBackendCfg backendConfiguration;

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
  private final BlockingQueue<IndexOutputBuffer> freeBufferQueue =
      new LinkedBlockingQueue<IndexOutputBuffer>();

  /**
   * Map of index keys to index buffers. Used to allocate sorted index buffers
   * to a index writer thread.
   */
  private final Map<IndexKey, BlockingQueue<IndexOutputBuffer>> indexKeyQueueMap =
      new ConcurrentHashMap<IndexKey, BlockingQueue<IndexOutputBuffer>>();

  /** Map of DB containers to index managers. Used to start phase 2. */
  private final List<IndexManager> indexMgrList = new LinkedList<IndexManager>();
  /** Map of DB containers to DN-based index managers. Used to start phase 2. */
  private final List<IndexManager> DNIndexMgrList = new LinkedList<IndexManager>();

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
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<DN, Suffix>();
  /** Map of container ids to database containers. */
  private final ConcurrentHashMap<Integer, Index> idContainerMap = new ConcurrentHashMap<Integer, Index>();
  /** Map of container ids to entry containers. */
  private final ConcurrentHashMap<Integer, EntryContainer> idECMap =
      new ConcurrentHashMap<Integer, EntryContainer>();

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
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws StorageRuntimeException
   *           If an error occurred when opening the DB.
   * @throws ConfigException
   *           If a problem occurs during initialization.
   */
  public Importer(RebuildConfig rebuildConfig, PersistitBackendCfg cfg) throws InitializationException,
      StorageRuntimeException, ConfigException
  {
    this.importConfiguration = null;
    this.backendConfiguration = cfg;
    this.tmpEnv = null;
    this.threadCount = 1;
    this.rebuildManager = new RebuildIndexManager(rebuildConfig, cfg);
    this.indexCount = rebuildManager.getIndexCount();
    this.clearedBackend = false;
    this.scratchFileWriterList =
        new ArrayList<ScratchFileWriterTask>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<Future<Void>>();

    this.tempDir = getTempDir(cfg, rebuildConfig.getTmpDirectory());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      throw new InitializationException(ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
    }
    this.skipDNValidation = true;
    initializeDBEnv();
  }

  /**
   * Create a new import job with the specified ldif import config.
   *
   * @param importConfiguration
   *          The LDIF import configuration.
   * @param backendCfg
   *          The local DB back-end configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws ConfigException
   *           If a problem occurs reading the configuration.
   * @throws StorageRuntimeException
   *           If an error occurred when opening the DB.
   */
  public Importer(LDIFImportConfig importConfiguration, PersistitBackendCfg backendCfg)
      throws InitializationException, ConfigException, StorageRuntimeException
  {
    this.rebuildManager = null;
    this.importConfiguration = importConfiguration;
    this.backendConfiguration = backendCfg;

    if (importConfiguration.getThreadCount() == 0)
    {
      this.threadCount = Runtime.getRuntime().availableProcessors() * 2;
    }
    else
    {
      this.threadCount = importConfiguration.getThreadCount();
    }

    // Determine the number of indexes.
    this.indexCount = getTotalIndexCount(backendCfg);

    this.clearedBackend = mustClearBackend(importConfiguration, backendCfg);
    this.scratchFileWriterList =
        new ArrayList<ScratchFileWriterTask>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<Future<Void>>();

    this.tempDir = getTempDir(backendCfg, importConfiguration.getTmpDirectory());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      throw new InitializationException(ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
    }
    skipDNValidation = importConfiguration.getSkipDNValidation();
    initializeDBEnv();

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
   * @param importCfg
   *          the import configuration object
   * @param backendCfg
   *          the backend configuration object
   * @return true if the backend must be cleared, false otherwise
   * @see Importer#getSuffix(WriteableStorage, EntryContainer) for per-suffix cleanups.
   */
  static boolean mustClearBackend(LDIFImportConfig importCfg, PluggableBackendCfg backendCfg)
  {
    return !importCfg.appendToExistingData()
        && (importCfg.clearBackend() || backendCfg.getBaseDN().size() <= 1);
  }

  private File getTempDir(PluggableBackendCfg backendCfg, String tmpDirectory)
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
    return new File(parentDir, backendCfg.getBackendId());
  }

  private int getTotalIndexCount(PluggableBackendCfg backendCfg)
      throws ConfigException
  {
    int indexes = 2; // dn2id, dn2uri
    for (String indexName : backendCfg.listBackendIndexes())
    {
      BackendIndexCfg index = backendCfg.getBackendIndex(indexName);
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
   * @throws InitializationException
   *           If a problem occurs during calculation.
   */
  private void initializeDBEnv() throws InitializationException
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
      logger.info(NOTE_JEB_IMPORT_ADJUST_THREAD_COUNT, oldThreadCount, threadCount);
    }

    logger.info(NOTE_JEB_IMPORT_LDIF_TOT_MEM_BUF, availableMemory, phaseOneBufferCount);
    if (tmpEnvCacheSize > 0)
    {
      logger.info(NOTE_JEB_IMPORT_LDIF_TMP_ENV_MEM, tmpEnvCacheSize);
    }
    logger.info(NOTE_JEB_IMPORT_LDIF_DB_MEM_BUF_INFO, dbCacheSize, bufferSize);
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

      // Round up to minimum of 16MB (e.g. unit tests only use 2% cache).
      totalAvailableMemory = Math.max(Math.min(usableMemory, configuredMemory), 16 * MB);
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

  private void initializeSuffixes(WriteableStorage txn) throws StorageRuntimeException,
      ConfigException
  {
    for (EntryContainer ec : rootContainer.getEntryContainers())
    {
      Suffix suffix = getSuffix(txn, ec);
      if (suffix != null)
      {
        dnSuffixMap.put(ec.getBaseDN(), suffix);
        generateIndexID(suffix);
      }
    }
  }

  /**
   * Mainly used to support multiple suffixes. Each index in each suffix gets an
   * unique ID to identify which DB it needs to go to in phase two processing.
   */
  private void generateIndexID(Suffix suffix)
  {
    for (AttributeIndex attributeIndex : suffix.getAttrIndexMap().values())
    {
      putInIdContainerMap(attributeIndex.getEqualityIndex());
      putInIdContainerMap(attributeIndex.getPresenceIndex());
      putInIdContainerMap(attributeIndex.getSubstringIndex());
      putInIdContainerMap(attributeIndex.getOrderingIndex());
      putInIdContainerMap(attributeIndex.getApproximateIndex());
      Map<String, Collection<Index>> extensibleMap = attributeIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        putInIdContainerMap(extensibleMap.get(EXTENSIBLE_INDEXER_ID_SUBSTRING));
        putInIdContainerMap(extensibleMap.get(EXTENSIBLE_INDEXER_ID_SHARED));
      }
    }
  }

  private void putInIdContainerMap(Collection<Index> indexes)
  {
    if (indexes != null)
    {
      for (Index index : indexes)
      {
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

  private Suffix getSuffix(WriteableStorage txn, EntryContainer entryContainer)
      throws ConfigException
  {
    DN baseDN = entryContainer.getBaseDN();
    EntryContainer sourceEntryContainer = null;
    List<DN> includeBranches = new ArrayList<DN>();
    List<DN> excludeBranches = new ArrayList<DN>();

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
          entryContainer = createEntryContainer(txn, baseDN);
        }
      }
    }
    return new Suffix(entryContainer, sourceEntryContainer, includeBranches, excludeBranches);
  }

  private EntryContainer createEntryContainer(WriteableStorage txn, DN baseDN) throws ConfigException
  {
    try
    {
      DN tempDN = baseDN.child(DN.valueOf("dc=importTmp"));
      return rootContainer.openEntryContainer(tempDN, txn);
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject());
    }
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
   * @throws StorageRuntimeException
   *           If the JEB database had an error.
   * @throws InterruptedException
   *           If an interrupted error occurred.
   * @throws ExecutionException
   *           If an execution error occurred.
   */
  public void rebuildIndexes(RootContainer rootContainer)
      throws ConfigException, InitializationException, StorageRuntimeException,
      InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    final long startTime = System.currentTimeMillis();

    try
    {
      rootContainer.getStorage().write(new WriteOperation()
      {
        @Override
        public void run(WriteableStorage txn) throws Exception
        {
          rebuildManager.initialize();
          rebuildManager.printStartMessage(txn);
          rebuildManager.rebuildIndexes(txn);
          recursiveDelete(tempDir);
          rebuildManager.printStopMessage(startTime);
        }
      });
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  /**
   * Import a LDIF using the specified root container.
   *
   * @param rootContainer
   *          The root container to use during the import.
   * @param txn
   *          The database transaction
   * @return A LDIF result.
   * @throws ConfigException
   *           If the import failed because of an configuration error.
   * @throws InitializationException
   *           If the import failed because of an initialization error.
   * @throws StorageRuntimeException
   *           If the import failed due to a database error.
   * @throws InterruptedException
   *           If the import failed due to an interrupted error.
   * @throws ExecutionException
   *           If the import failed due to an execution error.
   */
  public LDIFImportResult processImport(RootContainer rootContainer, WriteableStorage txn)
      throws ConfigException, InitializationException, StorageRuntimeException,
      InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    try {
      try
      {
        reader = new ImportLDIFReader(importConfiguration, rootContainer);
      }
      catch (IOException ioe)
      {
        LocalizableMessage message = ERR_JEB_IMPORT_LDIF_READER_IO_ERROR.get();
        throw new InitializationException(message, ioe);
      }

      logger.info(NOTE_JEB_IMPORT_STARTING, DirectoryServer.getVersionString(),
              BUILD_ID, REVISION_NUMBER);
      logger.info(NOTE_JEB_IMPORT_THREAD_COUNT, threadCount);
      initializeSuffixes(txn);
      setIndexesTrusted(false);

      final long startTime = System.currentTimeMillis();
      phaseOne(txn);
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
      phaseTwo(txn);
      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }
      final long phaseTwoFinishTime = System.currentTimeMillis();

      setIndexesTrusted(true);
      switchEntryContainers(txn);
      recursiveDelete(tempDir);
      final long finishTime = System.currentTimeMillis();
      final long importTime = finishTime - startTime;
      logger.info(NOTE_JEB_IMPORT_PHASE_STATS, importTime / 1000,
              (phaseOneFinishTime - startTime) / 1000,
              (phaseTwoFinishTime - phaseTwoTime) / 1000);
      float rate = 0;
      if (importTime > 0)
      {
        rate = 1000f * reader.getEntriesRead() / importTime;
      }
      logger.info(NOTE_JEB_IMPORT_FINAL_STATUS, reader.getEntriesRead(), importCount.get(),
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
    }
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

  private void switchEntryContainers(WriteableStorage txn) throws StorageRuntimeException, InitializationException
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
        toDelete.delete(txn);
        toDelete.unlock();

        final EntryContainer replacement = suffix.getEntryContainer();
        replacement.lock();
        replacement.setDatabasePrefix(baseDN.toNormalizedUrlSafeString());
        replacement.unlock();
        rootContainer.registerEntryContainer(baseDN, replacement);
      }
    }
  }

  private void setIndexesTrusted(boolean trusted) throws StorageRuntimeException
  {
    try
    {
      for (Suffix s : dnSuffixMap.values())
      {
        s.setIndexesTrusted(trusted);
      }
    }
    catch (StorageRuntimeException ex)
    {
      throw new StorageRuntimeException(NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()).toString());
    }
  }

  private void phaseOne(WriteableStorage txn) throws InterruptedException, ExecutionException
  {
    initializeIndexBuffers();

    final ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new FirstPhaseProgressTask());
    scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
    bufferSortService = Executors.newFixedThreadPool(threadCount);
    final ExecutorService execService = Executors.newFixedThreadPool(threadCount);

    final List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
    tasks.add(new MigrateExistingTask(txn));
    getAll(execService.invokeAll(tasks));
    tasks.clear();

    if (importConfiguration.appendToExistingData()
        && importConfiguration.replaceExistingEntries())
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new AppendReplaceTask(txn));
      }
    }
    else
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new ImportTask(txn));
      }
    }
    getAll(execService.invokeAll(tasks));
    tasks.clear();

    tasks.add(new MigrateExcludedTask(txn));
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

  private void phaseTwo(WriteableStorage txn) throws InterruptedException, ExecutionException
  {
    ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new SecondPhaseProgressTask(reader.getEntriesRead()));
    try
    {
      processIndexFiles(txn);
    }
    finally
    {
      shutdownAll(timerService);
    }
  }

  private void processIndexFiles(WriteableStorage txn) throws InterruptedException, ExecutionException
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
      final List<IndexManager> allIndexMgrs = new ArrayList<IndexManager>(DNIndexMgrList);
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
    dbThreads = 1; // FIXME JNR

    logger.info(NOTE_JEB_IMPORT_LDIF_PHASE_TWO_MEM_REPORT, availableMemory, readAheadSize, buffers);

    // Start indexing tasks.
    List<Future<Void>> futures = new LinkedList<Future<Void>>();
    ExecutorService dbService = Executors.newFixedThreadPool(dbThreads);
    Semaphore permits = new Semaphore(buffers);

    // Start DN processing first.
    submitIndexDBWriteTasks(DNIndexMgrList, txn, dbService, permits, buffers, readAheadSize, futures);
    submitIndexDBWriteTasks(indexMgrList, txn, dbService, permits, buffers, readAheadSize, futures);
    getAll(futures);
    shutdownAll(dbService);
  }

  private void submitIndexDBWriteTasks(List<IndexManager> indexMgrs, WriteableStorage txn, ExecutorService dbService,
      Semaphore permits, int buffers, int readAheadSize, List<Future<Void>> futures)
  {
    for (IndexManager indexMgr : indexMgrs)
    {
      futures.add(dbService.submit(new IndexDBWriteTask(indexMgr, txn, permits, buffers, readAheadSize)));
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
    private MigrateExcludedTask(final WriteableStorage txn)
    {
      super(txn);
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getExcludeBranches().isEmpty())
        {
          logger.info(NOTE_JEB_IMPORT_MIGRATION_START, "excluded", suffix.getBaseDN());
          Cursor cursor = txn.openCursor(entryContainer.getDN2ID().getName());
          try
          {
            for (DN excludedDN : suffix.getExcludeBranches())
            {
              final ByteString key = JebFormat.dnToDNKey(excludedDN, suffix.getBaseDN().size());
              boolean success = cursor.positionToKeyOrNext(key);
              if (success && key.equals(cursor.getKey()))
              {
                // This is the base entry for a branch that was excluded in the
                // import so we must migrate all entries in this branch over to
                // the new entry container.
                ByteStringBuilder end = new ByteStringBuilder(key.length() + 1);
                end.append((byte) 0x01);

                while (success
                    && ByteSequence.COMPARATOR.compare(key, end) < 0
                    && !importConfiguration.isCancelled() && !isCanceled)
                {
                  EntryID id = new EntryID(cursor.getValue());
                  Entry entry = entryContainer.getID2Entry().get(txn, id);
                  processEntry(entry, rootContainer.getNextEntryID(), suffix);
                  migratedCount++;
                  success = cursor.next();
                }
              }
            }
            flushIndexBuffers();
          }
          catch (Exception e)
          {
            logger.error(ERR_JEB_IMPORT_LDIF_MIGRATE_EXCLUDED_TASK_ERR, e.getMessage());
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
    private MigrateExistingTask(final WriteableStorage txn)
    {
      super(txn);
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getIncludeBranches().isEmpty())
        {
          logger.info(NOTE_JEB_IMPORT_MIGRATION_START, "existing", suffix.getBaseDN());
          Cursor cursor = txn.openCursor(entryContainer.getDN2ID().getName());
          try
          {
            final List<ByteString> includeBranches = includeBranchesAsBytes(suffix);
            boolean success = cursor.next();
            while (success
                && !importConfiguration.isCancelled() && !isCanceled)
            {
              final ByteString key = cursor.getKey();
              if (!includeBranches.contains(key))
              {
                EntryID id = new EntryID(key);
                Entry entry = entryContainer.getID2Entry().get(txn, id);
                processEntry(entry, rootContainer.getNextEntryID(), suffix);
                migratedCount++;
                success = cursor.next();
              }
              else
              {
                // This is the base entry for a branch that will be included
                // in the import so we don't want to copy the branch to the
                //  new entry container.

                /**
                 * Advance the cursor to next entry at the same level in the DIT
                 * skipping all the entries in this branch. Set the next
                 * starting value to a value of equal length but slightly
                 * greater than the previous DN. Since keys are compared in
                 * reverse order we must set the first byte (the comma). No
                 * possibility of overflow here.
                 */
                ByteStringBuilder begin = new ByteStringBuilder(key.length() + 1);
                begin.append(key);
                begin.append((byte) 0x01);
                success = cursor.positionToKeyOrNext(begin);
              }
            }
            flushIndexBuffers();
          }
          catch (Exception e)
          {
            logger.error(ERR_JEB_IMPORT_LDIF_MIGRATE_EXISTING_TASK_ERR, e.getMessage());
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

    private List<ByteString> includeBranchesAsBytes(Suffix suffix)
    {
      List<ByteString> includeBranches = new ArrayList<ByteString>(suffix.getIncludeBranches().size());
      for (DN includeBranch : suffix.getIncludeBranches())
      {
        if (includeBranch.isDescendantOf(suffix.getBaseDN()))
        {
          includeBranches.add(JebFormat.dnToDNKey(includeBranch, suffix.getBaseDN().size()));
        }
      }
      return includeBranches;
    }
  }

  /**
   * Task to perform append/replace processing.
   */
  private class AppendReplaceTask extends ImportTask
  {
    public AppendReplaceTask(final WriteableStorage txn)
    {
      super(txn);
    }

    private final Set<ByteString> insertKeySet = new HashSet<ByteString>();
    private final Set<ByteString> deleteKeySet = new HashSet<ByteString>();
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
        logger.error(ERR_JEB_IMPORT_LDIF_APPEND_REPLACE_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(Entry entry, Suffix suffix)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      DN entryDN = entry.getName();
      DN2ID dn2id = suffix.getDN2ID();
      EntryID oldID = dn2id.get(txn, entryDN);
      if (oldID != null)
      {
        oldEntry = suffix.getID2Entry().get(txn, oldID);
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
      suffix.getID2Entry().put(txn, entryID, entry);
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
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        AttributeType attributeType = mapEntry.getKey();
        fillIndexKey(suffix, mapEntry.getValue(), entry, attributeType, entryID);
      }
    }

    @Override
    void processAttribute(Index index, Entry entry, EntryID entryID, IndexingOptions options,
        IndexKey indexKey) throws StorageRuntimeException, InterruptedException
    {
      if (oldEntry != null)
      {
        deleteKeySet.clear();
        index.indexEntry(oldEntry, deleteKeySet, options);
        for (ByteString delKey : deleteKeySet)
        {
          processKey(index, delKey, entryID, indexKey, false);
        }
      }
      insertKeySet.clear();
      index.indexEntry(entry, insertKeySet, options);
      for (ByteString key : insertKeySet)
      {
        processKey(index, key, entryID, indexKey, true);
      }
    }
  }

  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private class ImportTask implements Callable<Void>
  {
    WriteableStorage txn;
    private final Map<IndexKey, IndexOutputBuffer> indexBufferMap = new HashMap<IndexKey, IndexOutputBuffer>();
    private final Set<ByteString> insertKeySet = new HashSet<ByteString>();
    private final EntryInformation entryInfo = new EntryInformation();
    private final IndexKey dnIndexKey = new IndexKey(dnType, ImportIndexType.DN, 1);

    public ImportTask(final WriteableStorage txn)
    {
      this.txn = txn;
    }

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
        logger.error(ERR_JEB_IMPORT_LDIF_IMPORT_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(Entry entry, EntryID entryID, Suffix suffix)
        throws DirectoryException, StorageRuntimeException, InterruptedException
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
      suffix.getID2Entry().put(txn, entryID, entry);
      importCount.getAndIncrement();
    }

    /** Examine the DN for duplicates and missing parents. */
    boolean dnSanityCheck(DN entryDN, Entry entry, Suffix suffix)
        throws StorageRuntimeException, InterruptedException
    {
      //Perform parent checking.
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      if (parentDN != null && !suffix.isParentProcessed(txn, parentDN, tmpEnv, clearedBackend))
      {
        reader.rejectEntry(entry, ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN));
        return false;
      }
      //If the backend was not cleared, then the dn2id needs to checked first
      //for DNs that might not exist in the DN cache. If the DN is not in
      //the suffixes dn2id DB, then the dn cache is used.
      if (!clearedBackend)
      {
        EntryID id = suffix.getDN2ID().get(txn, entryDN);
        if (id != null || !tmpEnv.insert(entryDN))
        {
          reader.rejectEntry(entry, WARN_JEB_IMPORT_ENTRY_EXISTS.get());
          return false;
        }
      }
      else if (!tmpEnv.insert(entryDN))
      {
        reader.rejectEntry(entry, WARN_JEB_IMPORT_ENTRY_EXISTS.get());
        return false;
      }
      return true;
    }

    void processIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        AttributeType attributeType = mapEntry.getKey();
        if (entry.hasAttribute(attributeType))
        {
          fillIndexKey(suffix, mapEntry.getValue(), entry, attributeType, entryID);
        }
      }
    }

    void fillIndexKey(Suffix suffix, AttributeIndex attrIndex, Entry entry, AttributeType attrType, EntryID entryID)
        throws InterruptedException, DirectoryException, StorageRuntimeException
    {
      final IndexingOptions options = attrIndex.getIndexingOptions();

      processAttribute(attrIndex.getEqualityIndex(), ImportIndexType.EQUALITY, entry, attrType, entryID, options);
      processAttribute(attrIndex.getPresenceIndex(), ImportIndexType.PRESENCE, entry, attrType, entryID, options);
      processAttribute(attrIndex.getSubstringIndex(), ImportIndexType.SUBSTRING, entry, attrType, entryID, options);
      processAttribute(attrIndex.getOrderingIndex(), ImportIndexType.ORDERING, entry, attrType, entryID, options);
      processAttribute(attrIndex.getApproximateIndex(), ImportIndexType.APPROXIMATE, entry, attrType, entryID, options);

      final EntryContainer entryContainer = suffix.getEntryContainer();
      final IndexBuffer buffer = new IndexBuffer(entryContainer);
      for (VLVIndex vlvIdx : entryContainer.getVLVIndexes())
      {
        vlvIdx.addEntry(buffer, entryID, entry);
      }
      buffer.flush(txn);

      Map<String, Collection<Index>> extensibleMap = attrIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        Collection<Index> subIndexes = extensibleMap.get(EXTENSIBLE_INDEXER_ID_SUBSTRING);
        processAttributes(subIndexes, ImportIndexType.EX_SUBSTRING, entry, attrType, entryID, options);
        Collection<Index> sharedIndexes = extensibleMap.get(EXTENSIBLE_INDEXER_ID_SHARED);
        processAttributes(sharedIndexes, ImportIndexType.EX_SHARED, entry, attrType, entryID, options);
      }
    }

    private void processAttribute(Index index, ImportIndexType presence, Entry entry,
        AttributeType attributeType, EntryID entryID, IndexingOptions options) throws InterruptedException
    {
      if (index != null)
      {
        IndexKey indexKey = new IndexKey(attributeType, presence, index.getIndexEntryLimit());
        processAttribute(index, entry, entryID, options, indexKey);
      }
    }

    private void processAttributes(Collection<Index> indexes, ImportIndexType indexType, Entry entry,
        AttributeType attributeType, EntryID entryID, IndexingOptions options) throws InterruptedException
    {
      if (indexes != null)
      {
        for (Index index : indexes)
        {
          IndexKey indexKey = new IndexKey(attributeType, indexType, index.getIndexEntryLimit());
          processAttribute(index, entry, entryID, options, indexKey);
        }
      }
    }

    void processAttribute(Index index, Entry entry, EntryID entryID, IndexingOptions options,
        IndexKey indexKey) throws StorageRuntimeException, InterruptedException
    {
      insertKeySet.clear();
      index.indexEntry(entry, insertKeySet, options);
      for (ByteString key : insertKeySet)
      {
        processKey(index, key, entryID, indexKey, true);
      }
    }

    void flushIndexBuffers() throws InterruptedException, ExecutionException
    {
      final ArrayList<Future<Void>> futures = new ArrayList<Future<Void>>();
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

    int processKey(DatabaseContainer container, ByteString key, EntryID entryID,
        IndexKey indexKey, boolean insert) throws InterruptedException
    {
      int sizeNeeded = IndexOutputBuffer.getRequiredSize(key.length(), entryID.longValue());
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

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
        throws InterruptedException
    {
      DN2ID dn2id = suffix.getDN2ID();
      ByteString dnBytes = JebFormat.dnToDNKey(dn, suffix.getBaseDN().size());
      int id = processKey(dn2id, dnBytes, entryID, dnIndexKey, true);
      idECMap.putIfAbsent(id, suffix.getEntryContainer());
    }

    void processDN2URI(Suffix suffix, Entry oldEntry, Entry newEntry) throws StorageRuntimeException
    {
      DN2URI dn2uri = suffix.getDN2URI();
      if (oldEntry != null)
      {
        dn2uri.replaceEntry(txn, oldEntry, newEntry);
      }
      else
      {
        dn2uri.addEntry(txn, newEntry);
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
    private final int cacheSize;
    private final Map<Integer, DNState> dnStateMap = new HashMap<Integer, DNState>();
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
    private final WriteableStorage txn;

    /**
     * Creates a new index DB writer.
     *
     * @param indexMgr
     *          The index manager.
     * @param txn
     *          The database transaction
     * @param permits
     *          The semaphore used for restricting the number of buffer allocations.
     * @param maxPermits
     *          The maximum number of buffers which can be allocated.
     * @param cacheSize
     *          The buffer cache size.
     */
    public IndexDBWriteTask(IndexManager indexMgr, WriteableStorage txn, Semaphore permits, int maxPermits,
        int cacheSize)
    {
      this.indexMgr = indexMgr;
      this.txn = txn;
      this.permits = permits;
      this.maxPermits = maxPermits;
      this.cacheSize = cacheSize;
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

      logger.info(NOTE_JEB_IMPORT_LDIF_INDEX_STARTED, indexMgr.getBufferFileName(),
              remainingBuffers, totalBatches);

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
      final NavigableSet<IndexInputBuffer> buffers = new TreeSet<IndexInputBuffer>();
      for (int i = 0; i < permitRequest; i++)
      {
        final long bufferBegin = bufferIndexFile.readLong();
        final long bufferEnd = bufferIndexFile.readLong();
        buffers.add(
            new IndexInputBuffer(indexMgr, bufferFile.getChannel(),
                bufferBegin, bufferEnd, nextBufferID++, cacheSize));
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
            logger.info(NOTE_JEB_IMPORT_LDIF_DN_CLOSE, indexMgr.getDNCount());
          }
        }
        else
        {
          if (!isCanceled)
          {
            logger.info(NOTE_JEB_IMPORT_LDIF_INDEX_CLOSE, indexMgr.getBufferFileName());
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

        logger.info(NOTE_JEB_IMPORT_LDIF_PHASE_TWO_REPORT, indexMgr.getBufferFileName(),
            bytesReadPercent, kiloBytesRemaining, kiloBytesRate, currentBatch, totalBatches);

        lastBytesRead = tmpBytesRead;
      }
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
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
                final Index index = idContainerMap.get(indexID);
                int limit = index.getIndexEntryLimit();
                boolean maintainCount = index.getMaintainCount();
                insertIDSet = new ImportIDSet(1, limit, maintainCount);
                deleteIDSet = new ImportIDSet(1, limit, maintainCount);
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
                final Index index = idContainerMap.get(indexID);
                int limit = index.getIndexEntryLimit();
                boolean maintainCount = index.getMaintainCount();
                insertIDSet = new ImportIDSet(1, limit, maintainCount);
                deleteIDSet = new ImportIDSet(1, limit, maintainCount);
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
        logger.error(ERR_JEB_IMPORT_LDIF_INDEX_WRITE_DB_ERR, indexMgr.getBufferFileName(), e.getMessage());
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
          ByteString key = deleteSet.keyToByteString();
          final Index index = idContainerMap.get(indexID);
          index.delete(txn, key, deleteSet);
        }
        if (insertSet.size() > 0 || !insertSet.isDefined())
        {
          ByteString key = insertSet.keyToByteString();
          final Index index = idContainerMap.get(indexID);
          index.insert(txn, key, insertSet);
        }
      }
    }

    private void addDN2ID(int indexID, ImportIDSet record) throws DirectoryException
    {
      DNState dnState;
      if (!dnStateMap.containsKey(indexID))
      {
        dnState = new DNState(idECMap.get(indexID));
        dnStateMap.put(indexID, dnState);
      }
      else
      {
        dnState = dnStateMap.get(indexID);
      }
      if (dnState.checkParent(txn, record))
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
      private ByteString dnKey, dnValue;
      private final TreeMap<ByteBuffer, EntryID> parentIDMap = new TreeMap<ByteBuffer, EntryID>();
      private final EntryContainer entryContainer;
      private final Map<ByteString, ImportIDSet> id2childTree = new TreeMap<ByteString, ImportIDSet>();
      private final Map<ByteString, ImportIDSet> id2subtreeTree = new TreeMap<ByteString, ImportIDSet>();
      private final int childLimit, subTreeLimit;
      private final boolean childDoCount, subTreeDoCount;

      DNState(EntryContainer entryContainer)
      {
        this.entryContainer = entryContainer;
        final Index id2c = entryContainer.getID2Children();
        childLimit = id2c.getIndexEntryLimit();
        childDoCount = id2c.getMaintainCount();
        final Index id2s = entryContainer.getID2Subtree();
        subTreeLimit = id2s.getIndexEntryLimit();
        subTreeDoCount = id2s.getMaintainCount();
        lastDN = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
      }

      private ByteBuffer getParent(ByteBuffer buffer)
      {
        int parentIndex = JebFormat.findDNKeyParent(toByteString(buffer));
        if (parentIndex < 0)
        {
          // This is the root or base DN
          return null;
        }
        ByteBuffer parent = buffer.duplicate();
        parent.limit(parentIndex);
        return parent;
      }

      private ByteString toByteString(ByteBuffer buffer)
      {
        return ByteString.wrap(buffer.array(), 0, buffer.limit());
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
      private boolean checkParent(ReadableStorage txn, ImportIDSet record) throws StorageRuntimeException
      {
        dnKey = record.keyToByteString();
        dnValue = record.valueToByteString();

        entryID = new EntryID(dnValue);
        parentDN = getParent(record.getKey());

        //Bypass the cache for append data, lookup the parent in DN2ID and return.
        if (importConfiguration != null
            && importConfiguration.appendToExistingData())
        {
          //If null is returned than this is a suffix DN.
          if (parentDN != null)
          {
            ByteString key = toByteString(parentDN);
            ByteString value = txn.read(entryContainer.getDN2ID().getName(), key);
            if (value != null)
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
          final ByteString parentIDBytes = parentID.toByteString();
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

      private EntryID getParentID(ReadableStorage txn, ByteBuffer dn) throws StorageRuntimeException
      {
        // Bypass the cache for append data, lookup the parent DN in the DN2ID db
        if (importConfiguration == null || !importConfiguration.appendToExistingData())
        {
          return parentIDMap.get(dn);
        }
        ByteString key = toByteString(dn);
        ByteString value = txn.read(entryContainer.getDN2ID().getName(), key);
        return value != null ? new EntryID(value) : null;
      }

      private void id2SubTree(ReadableStorage txn, EntryID childID) throws DirectoryException
      {
        if (parentID != null)
        {
          ImportIDSet idSet;
          final ByteString parentIDBytes = parentID.toByteString();
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
            EntryID nodeID = getParentID(txn, dn);
            if (nodeID == null)
            {
              // We have a missing parent. Maybe parent checking was turned off?
              // Just ignore.
              break;
            }

            final ByteString nodeIDBytes = nodeID.toByteString();
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
        txn.create(entryContainer.getDN2ID().getName(), dnKey, dnValue);
        indexMgr.addTotDNCount(1);
        if (parentDN != null)
        {
          id2child(entryID);
          id2SubTree(txn, entryID);
        }
      }

      private void flushMapToDB(Map<ByteString, ImportIDSet> map, Index index,
          boolean clearMap)
      {
        for (Map.Entry<ByteString, ImportIDSet> e : map.entrySet())
        {
          dnKey = e.getKey();
          ImportIDSet idSet = e.getValue();
          index.insert(txn, dnKey, idSet);
        }
        if (clearMap)
        {
          map.clear();
        }
      }

      public void flush()
      {
        flushMapToDB(id2childTree, entryContainer.getID2Children(), false);
        flushMapToDB(id2subtreeTree, entryContainer.getID2Subtree(), false);
      }
    }
  }

  /**
   * This task writes the temporary scratch index files using the sorted buffers
   * read from a blocking queue private to each index.
   */
  private final class ScratchFileWriterTask implements Callable<Void>
  {
    private final int DRAIN_TO = 3;
    private final IndexManager indexMgr;
    private final BlockingQueue<IndexOutputBuffer> queue;
    /** Stream where to output insert ImportIDSet data. */
    private final ByteArrayOutputStream insertByteStream = new ByteArrayOutputStream(2 * bufferSize);
    /** Stream where to output delete ImportIDSet data. */
    private final ByteArrayOutputStream deleteByteStream = new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream bufferStream;
    private final DataOutputStream bufferIndexStream;
    private final TreeSet<IndexOutputBuffer> indexSortedSet = new TreeSet<IndexOutputBuffer>();
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
      List<IndexOutputBuffer> l = new LinkedList<IndexOutputBuffer>();
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
        logger.error(ERR_JEB_IMPORT_LDIF_INDEX_FILEWRITER_ERR,
            indexMgr.getBufferFile().getAbsolutePath(), e.getMessage());
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
        if (!indexBuffer.byteArraysEqual(i))
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
        else if (!b.recordsEqual(saveKey, saveIndexID))
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
        insertKeyCount = 1;
        insertByteStream.reset();
        insertByteStream.write(-1);
      }

      int insertSize = INT_SIZE;
      bufferStream.writeInt(insertKeyCount);
      if (insertByteStream.size() > 0)
      {
        insertByteStream.writeTo(bufferStream);
      }

      int deleteSize = INT_SIZE;
      bufferStream.writeInt(deleteKeyCount);
      if (deleteByteStream.size() > 0)
      {
        deleteByteStream.writeTo(bufferStream);
      }
      return insertSize + insertByteStream.size() + deleteSize + deleteByteStream.size();
    }

    private int writeHeader(int indexID, int keySize) throws IOException
    {
      bufferStream.writeInt(indexID);
      bufferStream.writeInt(keySize);
      return 2 * INT_SIZE;
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
        boolean isDN2ID = ImportIndexType.DN.equals(indexKey.getIndexType());
        IndexManager indexMgr = new IndexManager(indexKey.getName(), isDN2ID, indexKey.getEntryLimit());
        if (isDN2ID)
        {
          DNIndexMgrList.add(indexMgr);
        }
        else
        {
          indexMgrList.add(indexMgr);
        }
        BlockingQueue<IndexOutputBuffer> newQueue =
            new ArrayBlockingQueue<IndexOutputBuffer>(phaseOneBufferCount);
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
    private final PluggableBackendCfg cfg;

    /** Map of index keys to indexes. */
    private final Map<IndexKey, Index> indexMap =
        new LinkedHashMap<IndexKey, Index>();

    /** Map of index keys to extensible indexes. */
    private final Map<IndexKey, Collection<Index>> extensibleIndexMap =
        new LinkedHashMap<IndexKey, Collection<Index>>();

    /** List of VLV indexes. */
    private final List<VLVIndex> vlvIndexes = new LinkedList<VLVIndex>();

    /** The DN2ID index. */
    private DN2ID dn2id;

    /** The DN2URI index. */
    private DN2URI dn2uri;

    /** Total entries to be processed. */
    private long totalEntries;

    /** Total entries processed. */
    private final AtomicLong entriesProcessed = new AtomicLong(0);

    /** The suffix instance. */
    private Suffix suffix;

    /** The entry container. */
    private EntryContainer entryContainer;

    /**
     * Create an instance of the rebuild index manager using the specified
     * parameters.
     *
     * @param rebuildConfig
     *          The rebuild configuration to use.
     * @param cfg
     *          The local DB configuration to use.
     */
    public RebuildIndexManager(RebuildConfig rebuildConfig, PluggableBackendCfg cfg)
    {
      super(null);
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
      if (suffix == null)
      {
        throw new InitializationException(
            ERR_JEB_REBUILD_SUFFIX_ERROR.get(rebuildConfig.getBaseDN()));
      }
    }

    /**
     * Print start message.
     */
    void printStartMessage(WriteableStorage txn) throws StorageRuntimeException
    {
      this.txn = txn;
      totalEntries = suffix.getID2Entry().getRecordCount(txn);

      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        logger.info(NOTE_JEB_REBUILD_ALL_START, totalEntries);
        break;
      case DEGRADED:
        logger.info(NOTE_JEB_REBUILD_DEGRADED_START, totalEntries);
        break;
      default:
        if (!rebuildConfig.isClearDegradedState()
            && logger.isInfoEnabled())
        {
          String indexes = Utils.joinAsString(", ", rebuildConfig.getRebuildList());
          logger.info(NOTE_JEB_REBUILD_START, indexes, totalEntries);
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
        logger.info(NOTE_JEB_REBUILD_FINAL_STATUS, entriesProcessed.get(), totalTime / 1000, rate);
      }
    }

    /** {@inheritDoc} */
    @Override
    public Void call() throws Exception
    {
      ID2Entry id2entry = entryContainer.getID2Entry();
      Cursor cursor = txn.openCursor(id2entry.getName());
      try
      {
        while (cursor.next())
        {
          if (isCanceled)
          {
            return null;
          }
          EntryID entryID = new EntryID(cursor.getKey());
          Entry entry =
              ID2Entry.entryFromDatabase(cursor.getValue(),
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
        logger.error(ERR_JEB_IMPORT_LDIF_REBUILD_INDEX_TASK_ERR, stackTraceToSingleLineString(e));
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
     * @param txn
     *          The database transaction
     * @throws InterruptedException
     *           If an interrupted error occurred.
     * @throws ExecutionException
     *           If an Execution error occurred.
     * @throws StorageRuntimeException
     *           If an JEB error occurred.
     */
    public void rebuildIndexes(WriteableStorage txn)
        throws InterruptedException, ExecutionException, StorageRuntimeException
    {
      this.txn = txn;
      // Sets only the needed indexes.
      setIndexesListsToBeRebuilt();

      if (!rebuildConfig.isClearDegradedState())
      {
        // If not in a 'clear degraded state' operation,
        // need to rebuild the indexes.
        setRebuildListIndexesTrusted(false);
        clearIndexes(txn, true);
        phaseOne();
        if (isCanceled)
        {
          throw new InterruptedException("Rebuild Index canceled.");
        }
        phaseTwo();
      }
      else
      {
        logger.info(NOTE_JEB_REBUILD_CLEARDEGRADEDSTATE_FINAL_STATUS, rebuildConfig.getRebuildList());
      }

      setRebuildListIndexesTrusted(true);
    }

    @SuppressWarnings("fallthrough")
    private void setIndexesListsToBeRebuilt() throws StorageRuntimeException
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
          dn2id = entryContainer.getDN2ID();
        }
        if (mode == RebuildMode.ALL || entryContainer.getDN2URI() == null)
        {
          dn2uri = entryContainer.getDN2URI();
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
      for (final Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        final AttributeType attributeType = mapEntry.getKey();
        final AttributeIndex attributeIndex = mapEntry.getValue();
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

    private void rebuildAttributeIndexes(final AttributeIndex attrIndex, final AttributeType attrType,
        final boolean onlyDegraded) throws StorageRuntimeException
    {
      fillIndexMap(attrType, attrIndex.getSubstringIndex(), ImportIndexType.SUBSTRING, onlyDegraded);
      fillIndexMap(attrType, attrIndex.getOrderingIndex(), ImportIndexType.ORDERING, onlyDegraded);
      fillIndexMap(attrType, attrIndex.getEqualityIndex(), ImportIndexType.EQUALITY, onlyDegraded);
      fillIndexMap(attrType, attrIndex.getPresenceIndex(), ImportIndexType.PRESENCE, onlyDegraded);
      fillIndexMap(attrType, attrIndex.getApproximateIndex(), ImportIndexType.APPROXIMATE, onlyDegraded);

      final Map<String, Collection<Index>> extensibleMap = attrIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        final Collection<Index> subIndexes = extensibleMap.get(EXTENSIBLE_INDEXER_ID_SUBSTRING);
        fillIndexMap(attrType, subIndexes, ImportIndexType.EX_SUBSTRING, onlyDegraded);
        final Collection<Index> sharedIndexes = extensibleMap.get(EXTENSIBLE_INDEXER_ID_SHARED);
        fillIndexMap(attrType, sharedIndexes, ImportIndexType.EX_SHARED, onlyDegraded);
      }
    }

    private void fillIndexMap(final AttributeType attrType, final Collection<Index> indexes,
        final ImportIndexType importIndexType, final boolean onlyDegraded)
    {
      if (indexes != null && !indexes.isEmpty())
      {
        final List<Index> mutableCopy = new LinkedList<Index>(indexes);
        for (final Iterator<Index> it = mutableCopy.iterator(); it.hasNext();)
        {
          final Index sharedIndex = it.next();
          if (!onlyDegraded || !sharedIndex.isTrusted())
          {
            if (!rebuildConfig.isClearDegradedState() || sharedIndex.getRecordCount(txn) == 0)
            {
              putInIdContainerMap(sharedIndex);
            }
          }
          else
          {
            // This index is not a candidate for rebuilding.
            it.remove();
          }
        }
        if (!mutableCopy.isEmpty())
        {
          extensibleIndexMap.put(new IndexKey(attrType, importIndexType, 0), mutableCopy);
        }
      }
    }

    private void fillIndexMap(final AttributeType attrType, final Index index,
        final ImportIndexType importIndexType, final boolean onlyDegraded)
    {
      if (index != null && (!onlyDegraded || !index.isTrusted())
          && (!rebuildConfig.isClearDegradedState() || index.getRecordCount(txn) == 0))
      {
        putInIdContainerMap(index);
        final IndexKey key = new IndexKey(attrType, importIndexType, index.getIndexEntryLimit());
        indexMap.put(key, index);
      }
    }

    private void clearIndexes(WriteableStorage txn, boolean onlyDegraded) throws StorageRuntimeException
    {
      // Clears all the entry's container databases which are containing the indexes
      if (!onlyDegraded)
      {
        // dn2uri does not have a trusted status.
        entryContainer.clearDatabase(txn, entryContainer.getDN2URI());
      }

      if (!onlyDegraded
          || !entryContainer.getID2Children().isTrusted()
          || !entryContainer.getID2Subtree().isTrusted())
      {
        entryContainer.clearDatabase(txn, entryContainer.getDN2ID());
        entryContainer.clearDatabase(txn, entryContainer.getID2Children());
        entryContainer.clearDatabase(txn, entryContainer.getID2Subtree());
      }

      if (!indexMap.isEmpty())
      {
        for (final Index index : indexMap.values())
        {
          if (!onlyDegraded || !index.isTrusted())
          {
            entryContainer.clearDatabase(txn, index);
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
              entryContainer.clearDatabase(txn, subIndex);
            }
          }
        }
      }

      for (final VLVIndex vlvIndex : entryContainer.getVLVIndexes())
      {
        if (!onlyDegraded || !vlvIndex.isTrusted())
        {
          entryContainer.clearDatabase(txn, vlvIndex);
        }
      }
    }

    private void setRebuildListIndexesTrusted(boolean trusted) throws StorageRuntimeException
    {
      try
      {
        if (dn2id != null)
        {
          EntryContainer ec = suffix.getEntryContainer();
          ec.getID2Children().setTrusted(txn, trusted);
          ec.getID2Subtree().setTrusted(txn, trusted);
        }
        setTrusted(indexMap.values(), trusted);
        if (!vlvIndexes.isEmpty())
        {
          for (VLVIndex vlvIndex : vlvIndexes)
          {
            vlvIndex.setTrusted(txn, trusted);
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
      catch (StorageRuntimeException ex)
      {
        throw new StorageRuntimeException(NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()).toString());
      }
    }

    private void setTrusted(final Collection<Index> indexes, boolean trusted)
    {
      if (indexes != null && !indexes.isEmpty())
      {
        for (Index index : indexes)
        {
          index.setTrusted(txn, trusted);
        }
      }
    }

    private void phaseOne() throws StorageRuntimeException, InterruptedException,
        ExecutionException
    {
      initializeIndexBuffers();
      Timer timer = scheduleAtFixedRate(new RebuildFirstPhaseProgressTask());
      scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
      bufferSortService = Executors.newFixedThreadPool(threadCount);
      ExecutorService rebuildIndexService = Executors.newFixedThreadPool(threadCount);
      List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
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
        processIndexFiles(txn);
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

    private int getIndexCount() throws ConfigException, StorageRuntimeException,
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

    private int getRebuildListIndexCount(PluggableBackendCfg cfg)
        throws StorageRuntimeException, ConfigException, InitializationException
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
        if ("dn2id".equals(lowerName))
        {
          indexCount += 3;
        }
        else if ("dn2uri".equals(lowerName))
        {
          indexCount++;
        }
        else if (lowerName.startsWith("vlv."))
        {
          if (lowerName.length() < 5)
          {
            throw new StorageRuntimeException(ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName).toString());
          }
          indexCount++;
        }
        else if ("id2subtree".equals(lowerName)
            || "id2children".equals(lowerName))
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
            for (final String idx : cfg.listBackendIndexes())
            {
              if (idx.equalsIgnoreCase(index))
              {
                found = true;
                final BackendIndexCfg indexCfg = cfg.getBackendIndex(idx);
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
      return new InitializationException(ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index));
    }

    private boolean findExtensibleMatchingRule(PluggableBackendCfg cfg, String indexExRuleName) throws ConfigException
    {
      for (String idx : cfg.listBackendIndexes())
      {
        BackendIndexCfg indexCfg = cfg.getBackendIndex(idx);
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

    private int getExtensibleIndexCount(BackendIndexCfg indexCfg)
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
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      if (dn2id != null)
      {
        processDN2ID(suffix, entry.getName(), entryID);
      }
      if (dn2uri != null)
      {
        processDN2URI(suffix, null, entry);
      }
      processIndexes(entry, entryID);
      processExtensibleIndexes(entry, entryID);
      processVLVIndexes(entry, entryID);
    }

    private void processVLVIndexes(Entry entry, EntryID entryID)
        throws StorageRuntimeException, DirectoryException
    {
      final IndexBuffer buffer = new IndexBuffer(entryContainer);
      for (VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes())
      {
        vlvIdx.addEntry(buffer, entryID, entry);
      }
      buffer.flush(txn);
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
          AttributeIndex attributeIndex = entryContainer.getAttributeIndex(attrType);
          IndexingOptions options = attributeIndex.getIndexingOptions();
          for (Index index : mapEntry.getValue())
          {
            processAttribute(index, entry, entryID, options, key);
          }
        }
      }
    }

    private void processIndexes(Entry entry, EntryID entryID)
        throws StorageRuntimeException, InterruptedException
    {
      for (Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet())
      {
        IndexKey key = mapEntry.getKey();
        AttributeType attrType = key.getAttributeType();
        if (entry.hasAttribute(attrType))
        {
          AttributeIndex attributeIndex = entryContainer.getAttributeIndex(attrType);
          IndexingOptions options = attributeIndex.getIndexingOptions();
          Index index = mapEntry.getValue();
          processAttribute(index, entry, entryID, options, key);
        }
      }
    }

    /**
     * Return the number of entries processed by the rebuild manager.
     *
     * @return The number of entries processed.
     */
    public long getEntriesProcess()
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
    public void diskLowThresholdReached(DiskSpaceMonitor monitor)
    {
      diskFullThresholdReached(monitor);
    }

    @Override
    public void diskFullThresholdReached(DiskSpaceMonitor monitor)
    {
      isCanceled = true;
      logger.error(ERR_REBUILD_INDEX_LACK_DISK, monitor.getDirectory().getPath(),
              monitor.getFreeSpace(), monitor.getLowThreshold());
    }

    @Override
    public void diskSpaceRestored(DiskSpaceMonitor monitor)
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

    /**
     * Create a new rebuild index progress task.
     *
     * @throws StorageRuntimeException
     *           If an error occurred while accessing the JE database.
     */
    public RebuildFirstPhaseProgressTask() throws StorageRuntimeException
    {
      previousTime = System.currentTimeMillis();
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
      long entriesProcessed = rebuildManager.getEntriesProcess();
      long deltaCount = entriesProcessed - previousProcessed;
      float rate = 1000f * deltaCount / deltaTime;
      float completed = 0;
      if (rebuildManager.getTotalEntries() > 0)
      {
        completed = 100f * entriesProcessed / rebuildManager.getTotalEntries();
      }
      logger.info(NOTE_JEB_REBUILD_PROGRESS_REPORT, completed, entriesProcessed,
          rebuildManager.getTotalEntries(), rate);

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

    /** Create a new import progress task. */
    public FirstPhaseProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long latestCount = reader.getEntriesRead() + 0;
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }
      long entriesRead = reader.getEntriesRead();
      long entriesIgnored = reader.getEntriesIgnored();
      long entriesRejected = reader.getEntriesRejected();
      float rate = 1000f * deltaCount / deltaTime;
      logger.info(NOTE_JEB_IMPORT_PROGRESS_REPORT, entriesRead, entriesIgnored, entriesRejected, rate);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }

  /**
   * This class reports progress of the second phase of import processing at
   * fixed intervals.
   */
  private class SecondPhaseProgressTask extends TimerTask
  {
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;
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
    }

    /** The action to be performed by this timer task. */
    @Override
    public void run()
    {
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      if (deltaTime == 0)
      {
        return;
      }

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
    private Suffix getSuffix()
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
    private EntryID getEntryID()
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
    private final ImportIndexType indexType;
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
    private IndexKey(AttributeType attributeType, ImportIndexType indexType, int entryLimit)
    {
      this.attributeType = attributeType;
      this.indexType = indexType;
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
            && indexType.equals(oKey.getIndexType()))
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
      return attributeType.hashCode() + indexType.hashCode();
    }

    /**
     * Return the attribute type.
     *
     * @return The attribute type.
     */
    private AttributeType getAttributeType()
    {
      return attributeType;
    }

    /**
     * Return the index type.
     *
     * @return The index type.
     */
    private ImportIndexType getIndexType()
    {
      return indexType;
    }

    /**
     * Return the index key name, which is the attribute type primary name, a
     * period, and the index type name. Used for building file names and
     * progress output.
     *
     * @return The index key name.
     */
    private String getName()
    {
      return attributeType.getPrimaryName() + "."
          + StaticUtils.toLowerCase(indexType.name());
    }

    /**
     * Return the entry limit associated with the index.
     *
     * @return The entry limit.
     */
    private int getEntryLimit()
    {
      return entryLimit;
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
      return getClass().getSimpleName()
          + "(attributeType=" + attributeType
          + ", indexType=" + indexType
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
     * @throws StorageRuntimeException
     *           If an error occurs reading the database.
     */
    boolean contains(DN dn) throws StorageRuntimeException;
  }

  /** Invocation handler for the {@link PluggableBackendCfg} proxy. */
  private static final class BackendCfgHandler implements InvocationHandler
  {
    private final Map<String, Object> returnValues;

    private BackendCfgHandler(final Map<String, Object> returnValues)
    {
      this.returnValues = returnValues;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
      final Object returnValue = returnValues.get(method.getName());
      if (returnValue != null)
      {
        return returnValue;
      }
      throw new IllegalArgumentException("Unhandled method call on object (" + proxy
          + ") for method (" + method
          + ") with arguments (" + Arrays.toString(args) + ")");
    }
  }

  /**
   * Temporary environment used to check DN's when DN validation is performed
   * during phase one processing. It is deleted after phase one processing.
   */
  private final class TmpEnv implements DNCache
  {
    private Storage storage;
    private WriteableStorage txn;
    private org.opends.server.backends.pluggable.spi.Importer importer;
    private static final String DB_NAME = "dn_cache";
    private TreeName dnCache = new TreeName("", DB_NAME);

    /**
     * Create a temporary DB environment and database to be used as a cache of
     * DNs when DN validation is performed in phase one processing.
     *
     * @param envPath
     *          The file path to create the environment under.
     * @throws StorageRuntimeException
     *           If an error occurs either creating the environment or the DN database.
     */
    private TmpEnv(File envPath) throws StorageRuntimeException
    {
      final Map<String, Object> returnValues = new HashMap<String, Object>();
      returnValues.put("getDBDirectory", envPath.getAbsolutePath());
      returnValues.put("getBackendId", DB_NAME);
      // returnValues.put("getDBCacheSize", 10L);
      returnValues.put("getDBCachePercent", 10);
      returnValues.put("isDBTxnNoSync", true);
      try
      {
        storage = new PersistItStorage(newPersistitBackendCfgProxy(returnValues));
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }

      importer.createTree(dnCache);
    }

    private PersistitBackendCfg newPersistitBackendCfgProxy(Map<String, Object> returnValues)
    {
      return (PersistitBackendCfg) Proxy.newProxyInstance(
          getClass().getClassLoader(),
          new Class<?>[] { PersistitBackendCfg.class },
          new BackendCfgHandler(returnValues));
    }

    private static final long FNV_INIT = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Hash the DN bytes. Uses the FNV-1a hash. */
    private ByteString hashCode(ByteString b)
    {
      long hash = FNV_INIT;
      for (int i = 0; i < b.length(); i++)
      {
        hash ^= b.byteAt(i);
        hash *= FNV_PRIME;
      }
      return ByteString.valueOf(hash);
    }

    /**
     * Shutdown the temporary environment.
     *
     * @throws StorageRuntimeException
     *           If error occurs.
     */
    public void shutdown() throws StorageRuntimeException
    {
      try
      {
        importer.close();
      }
      finally
      {
        storage.removeStorageFiles();
      }
    }

    /**
     * Insert the specified DN into the DN cache. It will return {@code true} if
     * the DN does not already exist in the cache and was inserted, or
     * {@code false} if the DN exists already in the cache.
     *
     * @param dn
     *          The DN to insert in the cache.
     * @return {@code true} if the DN was inserted in the cache, or
     *         {@code false} if the DN exists in the cache already and could not
     *         be inserted.
     * @throws StorageRuntimeException
     *           If an error occurs accessing the database.
     */
    public boolean insert(DN dn) throws StorageRuntimeException
    {
      // Use a compact representation for key
      // and a reversible representation for value
      final ByteString key = hashCode(dn.toNormalizedByteString());
      final ByteStringBuilder dnValue = new ByteStringBuilder().append(dn.toString());

      return insert(key, dnValue);
    }

    private boolean insert(ByteString key, final ByteStringBuilder dn) throws StorageRuntimeException
    {
      Cursor cursor = null;
      try
      {
        final AtomicBoolean result = new AtomicBoolean();
        txn.update(dnCache, key, new UpdateFunction()
        {
          @Override
          public ByteSequence computeNewValue(ByteSequence existingDns)
          {
            if (existingDns != null)
            {
              if (isDNMatched(existingDns, dn))
              {
                // dn is already present, no change
                result.set(false);
                return existingDns;
              }
              else
              {
                // dn is not present in the list, add it
                result.set(true);
                return addDN(existingDns, dn);
              }
            }
            else
            {
              // no previous data, create a new list
              result.set(true);
              return singletonList(dn);
            }
          }

          /** Add the DN to the DNs because of a hash collision. */
          private ByteSequence addDN(final ByteSequence dnList, final ByteSequence dntoAdd)
          {
            final ByteStringBuilder builder = new ByteStringBuilder(dnList.length() + INT_SIZE + dntoAdd.length());
            builder.append(dnList);
            builder.append(dntoAdd.length());
            builder.append(dntoAdd);
            return builder;
          }

          /** Create a list of dn made of one element */
          private ByteSequence singletonList(final ByteSequence dntoAdd)
          {
            final ByteStringBuilder singleton = new ByteStringBuilder(dntoAdd.length() + INT_SIZE);
            singleton.append(dntoAdd.length());
            singleton.append(dntoAdd);
            return singleton;
          }
        });

        return result.get();
      }
      finally
      {
        close(cursor);
      }
    }

    /** Return true if the specified DN is in the DNs saved as a result of hash collisions. */
    private boolean isDNMatched(ByteSequence existingDns, ByteStringBuilder dn)
    {
      final ByteSequenceReader reader = existingDns.asReader();
      int previousPos = 0;
      while (reader.remaining() != 0)
      {
        int pLen = INT_SIZE;
        int len = reader.getInt();
        // TODO JNR remove call to toByteArray() on next line
        final byte[] existingDnsBytes = existingDns.toByteArray();
        if (indexComparator.compare(existingDnsBytes, previousPos + pLen, len, dn.getBackingArray(), dn.length()) == 0)
        {
          return true;
        }
        previousPos = reader.position();
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
      final ByteString key = hashCode(dn.toNormalizedByteString());
      final ByteString existingDns = txn.read(dnCache, key);
      if (existingDns != null)
      {
        final ByteStringBuilder dnBytes = new ByteStringBuilder().append(dn.toString());
        return isDNMatched(existingDns, dnBytes);
      }
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void diskLowThresholdReached(DiskSpaceMonitor monitor)
  {
    diskFullThresholdReached(monitor);
  }

  /** {@inheritDoc} */
  @Override
  public void diskFullThresholdReached(DiskSpaceMonitor monitor)
  {
    isCanceled = true;
    Arg3<Object, Number, Number> argMsg = !isPhaseOneDone
        ? ERR_IMPORT_LDIF_LACK_DISK_PHASE_ONE
        : ERR_IMPORT_LDIF_LACK_DISK_PHASE_TWO;
    logger.error(argMsg.get(monitor.getDirectory().getPath(), monitor.getFreeSpace(), monitor.getLowThreshold()));
  }

  /** {@inheritDoc} */
  @Override
  public void diskSpaceRestored(DiskSpaceMonitor monitor)
  {
    // Do nothing.
  }
}
