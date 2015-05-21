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

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;
import static org.opends.server.backends.pluggable.SuffixContainer.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Utils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.backends.persistit.PersistItStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.ImportLDIFReader.EntryInformation;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.Storage.AccessMode;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.util.Platform;

/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
final class OnDiskMergeBufferImporter
{
  /**
   * Shim that allows properly constructing an {@link OnDiskMergeBufferImporter} without polluting
   * {@link ImportStrategy} and {@link RootContainer} with this importer inner workings.
   */
  @SuppressWarnings("javadoc")
  static final class StrategyImpl implements ImportStrategy
  {
    private final PluggableBackendCfg backendCfg;

    StrategyImpl(PluggableBackendCfg backendCfg)
    {
      this.backendCfg = backendCfg;
    }

    @Override
    public LDIFImportResult importLDIF(LDIFImportConfig importConfig, RootContainer rootContainer,
        ServerContext serverContext) throws DirectoryException, InitializationException
    {
      try
      {
        return new OnDiskMergeBufferImporter(rootContainer, importConfig, backendCfg, serverContext).processImport();
      }
      catch (DirectoryException | InitializationException e)
      {
        logger.traceException(e);
        throw e;
      }
      catch (ConfigException e)
      {
        logger.traceException(e);
        throw new DirectoryException(getServerErrorResultCode(), e.getMessageObject(), e);
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new DirectoryException(getServerErrorResultCode(),
            LocalizableMessage.raw(stackTraceToSingleLineString(e)), e);
      }
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int TIMER_INTERVAL = 10000;
  private static final String DEFAULT_TMP_DIR = "import-tmp";
  private static final String DN_CACHE_DIR = "dn-cache";

  /** Defaults for DB cache. */
  private static final int MAX_DB_CACHE_SIZE = 8 * MB;
  private static final int MAX_DB_LOG_SIZE = 10 * MB;
  private static final int MIN_DB_CACHE_SIZE = 4 * MB;

  /**
   * Defaults for LDIF reader buffers, min memory required to import and default
   * size for byte buffers.
   */
  private static final int READER_WRITER_BUFFER_SIZE = 8 * KB;
  private static final int MIN_DB_CACHE_MEMORY = MAX_DB_CACHE_SIZE + MAX_DB_LOG_SIZE;

  /** Max size of phase one buffer. */
  private static final int MAX_BUFFER_SIZE = 2 * MB;
  /** Min size of phase one buffer. */
  private static final int MIN_BUFFER_SIZE = 4 * KB;
  /** Min size of phase two read-ahead cache. */
  private static final int MIN_READ_AHEAD_CACHE_SIZE = 2 * KB;
  /** Small heap threshold used to give more memory to JVM to attempt OOM errors. */
  private static final int SMALL_HEAP_SIZE = 256 * MB;

  /** The DN attribute type. */
  private static final AttributeType DN_TYPE;
  static
  {
    AttributeType attrType = DirectoryServer.getAttributeType("dn");
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType("dn");
    }
    DN_TYPE = attrType;
  }

  /** Root container. */
  private final RootContainer rootContainer;
  /** Import configuration. */
  private final LDIFImportConfig importCfg;
  private final ServerContext serverContext;

  /** LDIF reader. */
  private ImportLDIFReader reader;
  /** Phase one buffer count. */
  private final AtomicInteger bufferCount = new AtomicInteger(0);
  /** Phase one imported entries count. */
  private final AtomicLong importCount = new AtomicLong(0);
  /** Migrated entry count. */
  private int migratedCount;

  /** Phase one buffer size in bytes. */
  private int bufferSize;
  /** Index count. */
  private final int indexCount;
  /** Thread count. */
  private int threadCount;

  /** Whether DN validation should be performed. If true, then it is performed during phase one. */
  private final boolean validateDNs;

  /** Temp scratch directory. */
  private final File tempDir;
  /** DN cache used when DN validation is done in first phase. */
  private final DNCache dnCache;
  /** Size in bytes of DN cache. */
  private long dnCacheSize;
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

  /** The index managers used to start phase 2. */
  private final List<IndexManager> indexMgrList = new LinkedList<>();

  /**
   * Futures used to indicate when the index file writers are done flushing
   * their work queues and have exited. End of phase one.
   */
  private final List<Future<Void>> scratchFileWriterFutures = new CopyOnWriteArrayList<>();
  /**
   * List of index file writer tasks. Used to signal stopScratchFileWriters to
   * the index file writer tasks when the LDIF file has been done.
   */
  private final List<ScratchFileWriterTask> scratchFileWriterList;

  /** Map of DNs to Suffix objects. */
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<>();
  /**
   * Map of indexIDs to indexes.
   * <p>
   * Mainly used to support multiple suffixes. Each index in each suffix gets a unique ID to
   * identify which tree it needs to go to in phase two processing.
   */
  private final ConcurrentHashMap<Integer, Index> indexIDToIndexMap = new ConcurrentHashMap<>();
  /** Map of indexIDs to entry containers. */
  private final ConcurrentHashMap<Integer, EntryContainer> indexIDToECMap = new ConcurrentHashMap<>();

  /** Used to synchronize when a scratch file index writer is first setup. */
  private final Object synObj = new Object();

  /** Rebuild index manager used when rebuilding indexes. */
  private final RebuildIndexManager rebuildManager;

  /** Set to true if the backend was cleared. */
  private final boolean clearedBackend;

  /** Used to shutdown import if an error occurs in phase one. */
  private volatile boolean isCanceled;

  /** Number of phase one buffers. */
  private int phaseOneBufferCount;

  @SuppressWarnings("javadoc")
  OnDiskMergeBufferImporter(RootContainer rootContainer, RebuildConfig rebuildConfig, PluggableBackendCfg cfg,
      ServerContext serverContext) throws InitializationException, StorageRuntimeException, ConfigException
  {
    this.rootContainer = rootContainer;
    this.importCfg = null;
    this.serverContext = serverContext;
    this.threadCount = 1;
    this.rebuildManager = new RebuildIndexManager(rootContainer.getStorage(), rebuildConfig, cfg);
    this.indexCount = rebuildManager.getIndexCount();
    this.clearedBackend = false;
    this.scratchFileWriterList = new ArrayList<>(indexCount);

    this.tempDir = prepareTempDir(cfg, rebuildConfig.getTmpDirectory());
    computeMemoryRequirements();
    this.validateDNs = false;
    this.dnCache = null;
  }

  private OnDiskMergeBufferImporter(RootContainer rootContainer, LDIFImportConfig importCfg,
      PluggableBackendCfg backendCfg, ServerContext serverContext)
      throws InitializationException, ConfigException, StorageRuntimeException
  {
    this.rootContainer = rootContainer;
    this.rebuildManager = null;
    this.importCfg = importCfg;
    this.serverContext = serverContext;

    if (importCfg.getThreadCount() == 0)
    {
      this.threadCount = Runtime.getRuntime().availableProcessors() * 2;
    }
    else
    {
      this.threadCount = importCfg.getThreadCount();
    }

    // Determine the number of indexes.
    this.indexCount = getTotalIndexCount(backendCfg);

    this.clearedBackend = mustClearBackend(importCfg, backendCfg);
    this.scratchFileWriterList = new ArrayList<>(indexCount);

    validateDNs = !importCfg.getSkipDNValidation();
    this.tempDir = prepareTempDir(backendCfg, importCfg.getTmpDirectory());
    // be careful: requires that a few data has been set
    computeMemoryRequirements();

    if (validateDNs)
    {
      final File dnCachePath = new File(tempDir, DN_CACHE_DIR);
      dnCachePath.mkdirs();
      this.dnCache = new DNCacheImpl(dnCachePath);
    }
    else
    {
      this.dnCache = null;
    }
  }

  private File prepareTempDir(PluggableBackendCfg backendCfg, String tmpDirectory) throws InitializationException
  {
    File parentDir = getFileForPath(tmpDirectory != null ? tmpDirectory : DEFAULT_TMP_DIR);
    File tempDir = new File(parentDir, backendCfg.getBackendId());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      throw new InitializationException(ERR_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
    }
    return tempDir;
  }

  /**
   * Returns whether the backend must be cleared.
   *
   * @param importCfg
   *          the import configuration object
   * @param backendCfg
   *          the backend configuration object
   * @return true if the backend must be cleared, false otherwise
   * @see #prepareSuffix(WriteableTransaction, EntryContainer) for per-suffix cleanups.
   */
  static boolean mustClearBackend(LDIFImportConfig importCfg, PluggableBackendCfg backendCfg)
  {
    return !importCfg.appendToExistingData()
        && (importCfg.clearBackend() || backendCfg.getBaseDN().size() <= 1);
    /*
     * Why do we clear when there is only one baseDN?
     * any baseDN for which data is imported will be cleared anyway (see getSuffix()),
     * so if there is only one baseDN for this backend, then clear it now.
     */
  }

  private static int getTotalIndexCount(PluggableBackendCfg backendCfg) throws ConfigException
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
   * Calculate buffer sizes and initialize properties based on memory.
   *
   * @throws InitializationException
   *           If a problem occurs during calculation.
   */
  private void computeMemoryRequirements() throws InitializationException
  {
    // Calculate amount of usable memory. This will need to take into account
    // various fudge factors, including the number of IO buffers used by the
    // scratch writers (1 per index).
    calculateAvailableMemory();

    final long usableMemory = availableMemory - (indexCount * READER_WRITER_BUFFER_SIZE);

    // We need caching when doing DN validation or rebuilding indexes.
    if (validateDNs || rebuildManager != null)
    {
      // DN validation: calculate memory for DB cache, DN2ID temporary cache, and buffers.
      if (System.getProperty(PROPERTY_RUNNING_UNIT_TESTS) != null)
      {
        dbCacheSize = 500 * KB;
        dnCacheSize = 500 * KB;
      }
      else if (usableMemory < (MIN_DB_CACHE_MEMORY + MIN_DB_CACHE_SIZE))
      {
        dbCacheSize = MIN_DB_CACHE_SIZE;
        dnCacheSize = MIN_DB_CACHE_SIZE;
      }
      else if (!clearedBackend)
      {
        // Appending to existing data so reserve extra memory for the DB cache
        // since it will be needed for dn2id queries.
        dbCacheSize = usableMemory * 33 / 100;
        dnCacheSize = usableMemory * 33 / 100;
      }
      else
      {
        dbCacheSize = MAX_DB_CACHE_SIZE;
        dnCacheSize = usableMemory * 66 / 100;
      }
    }
    else
    {
      // No DN validation: calculate memory for DB cache and buffers.

      // No need for DN2ID cache.
      dnCacheSize = 0;

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

    final long phaseOneBufferMemory = usableMemory - dbCacheSize - dnCacheSize;
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
          if (validateDNs)
          {
            // The buffers are big enough: the memory is best used for the DN2ID temp DB
            bufferSize = MAX_BUFFER_SIZE;

            final long extraMemory = phaseOneBufferMemory - (totalPhaseOneBufferCount * bufferSize);
            if (!clearedBackend)
            {
              dbCacheSize += extraMemory / 2;
              dnCacheSize += extraMemory / 2;
            }
            else
            {
              dnCacheSize += extraMemory;
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
          throw new InitializationException(ERR_IMPORT_LDIF_LACK_MEM.get(
              usableMemory, minimumPhaseOneBufferMemory + dbCacheSize + dnCacheSize));
        }
      }
    }

    if (oldThreadCount != threadCount)
    {
      logger.info(NOTE_IMPORT_ADJUST_THREAD_COUNT, oldThreadCount, threadCount);
    }

    logger.info(NOTE_IMPORT_LDIF_TOT_MEM_BUF, availableMemory, phaseOneBufferCount);
    if (dnCacheSize > 0)
    {
      logger.info(NOTE_IMPORT_LDIF_TMP_ENV_MEM, dnCacheSize);
    }
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
      final long availableMemory = serverContext.getMemoryQuota().getAvailableMemory();
      totalAvailableMemory = Math.max(availableMemory, 16 * MB);
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

  private boolean isCanceled()
  {
    return isCanceled || (importCfg != null && importCfg.isCancelled());
  }

  private void initializeIndexBuffers()
  {
    for (int i = 0; i < phaseOneBufferCount; i++)
    {
      freeBufferQueue.add(new IndexOutputBuffer(bufferSize));
    }
  }

  private void initializeSuffixes(WriteableTransaction txn) throws ConfigException, DirectoryException
  {
    for (EntryContainer ec : rootContainer.getEntryContainers())
    {
      Suffix suffix = getSuffix(txn, ec);
      if (suffix != null)
      {
        dnSuffixMap.put(ec.getBaseDN(), suffix);
        generateIndexIDs(suffix);
      }
    }
  }

  private void generateIndexIDs(Suffix suffix)
  {
    for (AttributeIndex attributeIndex : suffix.getAttrIndexMap().values())
    {
      for (Index index : attributeIndex.getNameToIndexes().values())
      {
        putInIndexIDToIndexMap(index);
      }
    }
  }

  private void putInIndexIDToIndexMap(Index index)
  {
    indexIDToIndexMap.putIfAbsent(getIndexID(index), index);
  }

  private static int getIndexID(Tree tree)
  {
    return System.identityHashCode(tree);
  }

  private Suffix getSuffix(WriteableTransaction txn, EntryContainer entryContainer)
      throws ConfigException, DirectoryException
  {
    if (importCfg.appendToExistingData() || importCfg.clearBackend())
    {
      return new Suffix(entryContainer);
    }

    final DN baseDN = entryContainer.getBaseDN();
    if (importCfg.getExcludeBranches().contains(baseDN))
    {
      // This entire base DN was explicitly excluded. Skip.
      return null;
    }

    EntryContainer sourceEntryContainer = null;
    List<DN> excludeBranches = getDescendants(baseDN, importCfg.getExcludeBranches());
    List<DN> includeBranches = null;
    if (!importCfg.getIncludeBranches().isEmpty())
    {
      includeBranches = getDescendants(baseDN, importCfg.getIncludeBranches());
      if (includeBranches.isEmpty())
      {
        // There are no branches in the explicitly defined include list under this base DN.
        // Skip this base DN altogether.
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

      // Remove any exclude branches that are not are not under a include branch
      // since they will be migrated as part of the existing entries
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
        // no exclude branches that we need to migrate.
        // Just clear the entry container.
        clearSuffix(entryContainer);
      }
      else
      {
        sourceEntryContainer = entryContainer;

        // Create a temp entry container
        DN tempDN = baseDN.child(DN.valueOf("dc=importTmp"));
        entryContainer = rootContainer.openEntryContainer(tempDN, txn);
      }
    }
    return new Suffix(entryContainer, sourceEntryContainer, includeBranches, excludeBranches);
  }

  private List<DN> getDescendants(DN baseDN, Set<DN> dns)
  {
    final List<DN> results = new ArrayList<>();
    for (DN dn : dns)
    {
      if (baseDN.isAncestorOf(dn))
      {
        results.add(dn);
      }
    }
    return results;
  }

  private static void clearSuffix(EntryContainer entryContainer)
  {
    entryContainer.lock();
    entryContainer.clear();
    entryContainer.unlock();
  }

  private static boolean isAnyNotEqualAndAncestorOf(List<DN> dns, DN childDN)
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

  private static boolean isAnyAncestorOf(List<DN> dns, DN childDN)
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
   * @throws ConfigException
   *           If a configuration error occurred.
   * @throws InitializationException
   *           If an initialization error occurred.
   * @throws StorageRuntimeException
   *           If the storage had an error.
   * @throws InterruptedException
   *           If an interrupted error occurred.
   * @throws ExecutionException
   *           If an execution error occurred.
   */
  public void rebuildIndexes() throws ConfigException, InitializationException, StorageRuntimeException,
      InterruptedException, ExecutionException
  {
    try
    {
      if (rebuildManager.rebuildConfig.isClearDegradedState())
      {
        clearDegradedState();
      }
      else
      {
        rebuildIndexes0();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  private void clearDegradedState() throws Exception
  {
    rootContainer.getStorage().write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        final long startTime = System.currentTimeMillis();
        rebuildManager.initialize();
        rebuildManager.printStartMessage(txn);
        rebuildManager.clearDegradedState(txn);
        recursiveDelete(tempDir);
        rebuildManager.printStopMessage(startTime);
      }
    });
  }

  private void rebuildIndexes0() throws Exception
  {
    final long startTime = System.currentTimeMillis();
    final Storage storage = rootContainer.getStorage();
    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        rebuildManager.initialize();
        rebuildManager.printStartMessage(txn);
        rebuildManager.preRebuildIndexes(txn);
      }
    });

    rebuildManager.rebuildIndexesPhaseOne();
    rebuildManager.throwIfCancelled();
    rebuildManager.rebuildIndexesPhaseTwo();

    storage.write(new WriteOperation()
    {
      @Override
      public void run(WriteableTransaction txn) throws Exception
      {
        rebuildManager.postRebuildIndexes(txn);
      }
    });
    recursiveDelete(tempDir);
    rebuildManager.printStopMessage(startTime);
  }

  private LDIFImportResult processImport() throws Exception
  {
    try {
      try
      {
        reader = new ImportLDIFReader(importCfg, rootContainer);
      }
      catch (IOException ioe)
      {
        throw new InitializationException(ERR_IMPORT_LDIF_READER_IO_ERROR.get(), ioe);
      }

      logger.info(NOTE_IMPORT_STARTING, DirectoryServer.getVersionString(), BUILD_ID, REVISION_NUMBER);
      logger.info(NOTE_IMPORT_THREAD_COUNT, threadCount);

      final Storage storage = rootContainer.getStorage();
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          initializeSuffixes(txn);
          setIndexesTrusted(txn, false);
        }
      });

      final long startTime = System.currentTimeMillis();
      importPhaseOne();
      final long phaseOneFinishTime = System.currentTimeMillis();
      if (validateDNs)
      {
        dnCache.close();
      }

      if (isCanceled())
      {
        throw new InterruptedException("Import processing canceled.");
      }

      final long phaseTwoTime = System.currentTimeMillis();
      importPhaseTwo();
      if (isCanceled())
      {
        throw new InterruptedException("Import processing canceled.");
      }
      final long phaseTwoFinishTime = System.currentTimeMillis();

      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          setIndexesTrusted(txn, true);
          switchEntryContainers(txn);
        }
      });
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
      if (validateDNs)
      {
        close(dnCache);
      }
    }
  }

  private void switchEntryContainers(WriteableTransaction txn) throws StorageRuntimeException, InitializationException
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
        replacement.setTreePrefix(baseDN.toNormalizedUrlSafeString());
        replacement.unlock();
        rootContainer.registerEntryContainer(baseDN, replacement);
      }
    }
  }

  private void setIndexesTrusted(WriteableTransaction txn, boolean trusted) throws StorageRuntimeException
  {
    try
    {
      for (Suffix s : dnSuffixMap.values())
      {
        s.setIndexesTrusted(txn, trusted);
      }
    }
    catch (StorageRuntimeException ex)
    {
      throw new StorageRuntimeException(NOTE_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()).toString());
    }
  }

  /**
   * Reads all entries from id2entry, and:
   * <ol>
   * <li>compute how the entry is indexed for each index</li>
   * <li>store the result of indexing entries into in-memory index buffers</li>
   * <li>each time an in-memory index buffer is filled, sort it and write it to scratch files.
   * The scratch files will be read by phaseTwo to perform on-disk merge</li>
   * </ol>
   */
  private void importPhaseOne() throws Exception
  {
    initializeIndexBuffers();

    final ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new FirstPhaseProgressTask());
    scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
    bufferSortService = Executors.newFixedThreadPool(threadCount);
    final ExecutorService execService = Executors.newFixedThreadPool(threadCount);

    final Storage storage = rootContainer.getStorage();
    execService.submit(new MigrateExistingTask(storage)).get();

    final List<Callable<Void>> tasks = new ArrayList<>(threadCount);
    if (importCfg.appendToExistingData()
        && importCfg.replaceExistingEntries())
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new AppendReplaceTask(storage));
      }
    }
    else
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new ImportTask(storage));
      }
    }
    execService.invokeAll(tasks);
    tasks.clear();

    execService.submit(new MigrateExcludedTask(storage)).get();

    stopScratchFileWriters();
    getAll(scratchFileWriterFutures);

    shutdownAll(timerService, execService, bufferSortService, scratchFileWriterService);

    // Try to clear as much memory as possible.
    clearAll(scratchFileWriterList, scratchFileWriterFutures, freeBufferQueue);
    indexKeyQueueMap.clear();
  }

  private static void scheduleAtFixedRate(ScheduledThreadPoolExecutor timerService, Runnable task)
  {
    timerService.scheduleAtFixedRate(task, TIMER_INTERVAL, TIMER_INTERVAL, TimeUnit.MILLISECONDS);
  }

  private static void shutdownAll(ExecutorService... executorServices) throws InterruptedException
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

  private static void clearAll(Collection<?>... cols)
  {
    for (Collection<?> col : cols)
    {
      col.clear();
    }
  }

  private void importPhaseTwo() throws Exception
  {
    ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new SecondPhaseProgressTask());
    try
    {
      processIndexFiles();
    }
    finally
    {
      shutdownAll(timerService);
    }
  }

  /**
   * Performs on-disk merge by reading several scratch files at once
   * and write their ordered content into the target indexes.
   */
  private void processIndexFiles() throws Exception
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
      final List<IndexManager> allIndexMgrs = new ArrayList<>(indexMgrList);
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
    ExecutorService dbService = Executors.newFixedThreadPool(dbThreads);
    Semaphore permits = new Semaphore(buffers);

    // Start DN processing first.
    Storage storage = rootContainer.getStorage();
    storage.close();
    try (final Importer importer = storage.startImport())
    {
      submitIndexDBWriteTasks(indexMgrList, importer, dbService, permits, buffers, readAheadSize);
    }
    finally
    {
      storage.open(AccessMode.READ_WRITE);
    }

    shutdownAll(dbService);
  }

  private void submitIndexDBWriteTasks(List<IndexManager> indexMgrs, Importer importer,
      ExecutorService dbService, Semaphore permits, int buffers, int readAheadSize) throws InterruptedException
  {
    List<IndexDBWriteTask> tasks = new ArrayList<>(indexMgrs.size());
    for (IndexManager indexMgr : indexMgrs)
    {
      tasks.add(new IndexDBWriteTask(importer, indexMgr, permits, buffers, readAheadSize));
    }
    dbService.invokeAll(tasks);
  }

  private static <T> void getAll(List<Future<T>> futures) throws InterruptedException, ExecutionException
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
    private MigrateExcludedTask(final Storage storage)
    {
      super(storage);
    }

    @Override
    void call0(WriteableTransaction txn) throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getExcludeBranches().isEmpty())
        {
          logger.info(NOTE_IMPORT_MIGRATION_START, "excluded", suffix.getBaseDN());
          Cursor<ByteString, ByteString> cursor = txn.openCursor(entryContainer.getDN2ID().getName());
          try
          {
            for (DN excludedDN : suffix.getExcludeBranches())
            {
              final ByteString key = dnToDNKey(excludedDN, suffix.getBaseDN().size());
              boolean success = cursor.positionToKeyOrNext(key);
              if (success && key.equals(cursor.getKey()))
              {
                /*
                 * This is the base entry for a branch that was excluded in the
                 * import so we must migrate all entries in this branch over to
                 * the new entry container.
                 */
                ByteStringBuilder end = afterKey(key);

                while (success
                    && key.compareTo(end) < 0
                    && !isCanceled())
                {
                  EntryID id = new EntryID(cursor.getValue());
                  Entry entry = entryContainer.getID2Entry().get(txn, id);
                  processEntry(txn, entry, rootContainer.getNextEntryID(), suffix);
                  migratedCount++;
                  success = cursor.next();
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
    }
  }

  /** Task to migrate existing entries. */
  private final class MigrateExistingTask extends ImportTask
  {
    private MigrateExistingTask(final Storage storage)
    {
      super(storage);
    }

    @Override
    void call0(WriteableTransaction txn) throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getIncludeBranches().isEmpty())
        {
          logger.info(NOTE_IMPORT_MIGRATION_START, "existing", suffix.getBaseDN());
          Cursor<ByteString, ByteString> cursor = txn.openCursor(entryContainer.getDN2ID().getName());
          try
          {
            final List<ByteString> includeBranches = includeBranchesAsBytes(suffix);
            boolean success = cursor.next();
            while (success
                && !isCanceled())
            {
              final ByteString key = cursor.getKey();
              if (!includeBranches.contains(key))
              {
                EntryID id = new EntryID(key);
                Entry entry = entryContainer.getID2Entry().get(txn, id);
                processEntry(txn, entry, rootContainer.getNextEntryID(), suffix);
                migratedCount++;
                success = cursor.next();
              }
              else
              {
                /*
                 * This is the base entry for a branch that will be included
                 * in the import so we do not want to copy the branch to the
                 * new entry container.
                 */
                /*
                 * Advance the cursor to next entry at the same level in the DIT
                 * skipping all the entries in this branch.
                 */
                ByteStringBuilder begin = afterKey(key);
                success = cursor.positionToKeyOrNext(begin);
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
    }

    private List<ByteString> includeBranchesAsBytes(Suffix suffix)
    {
      List<ByteString> includeBranches = new ArrayList<>(suffix.getIncludeBranches().size());
      for (DN includeBranch : suffix.getIncludeBranches())
      {
        if (includeBranch.isDescendantOf(suffix.getBaseDN()))
        {
          includeBranches.add(dnToDNKey(includeBranch, suffix.getBaseDN().size()));
        }
      }
      return includeBranches;
    }
  }

  /** Task to perform append/replace processing. */
  private class AppendReplaceTask extends ImportTask
  {
    public AppendReplaceTask(final Storage storage)
    {
      super(storage);
    }

    private Entry oldEntry;

    @Override
    void call0(WriteableTransaction txn) throws Exception
    {
      try
      {
        EntryInformation entryInfo;
        while ((entryInfo = reader.readEntry(dnSuffixMap)) != null)
        {
          if (isCanceled())
          {
            freeBufferQueue.add(IndexOutputBuffer.poison());
            return;
          }
          processEntry(txn, entryInfo.getEntry(), entryInfo.getEntryID(), entryInfo.getSuffix());
        }
        flushIndexBuffers();
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_APPEND_REPLACE_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    @Override
    void processEntry(WriteableTransaction txn, Entry entry, EntryID entryID, Suffix suffix)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      DN entryDN = entry.getName();

      EntryID oldID = suffix.getDN2ID().get(txn, entryDN);
      oldEntry = oldID != null ? suffix.getID2Entry().get(txn, oldID) : null;
      if (oldEntry == null)
      {
        if (validateDNs && !dnSanityCheck(txn, entry, entryID, suffix))
        {
          suffix.removePending(entryDN);
          return;
        }
        suffix.removePending(entryDN);
        processDN2ID(suffix, entryDN, entryID);
        suffix.getDN2URI().addEntry(txn, entry);
      }
      else
      {
        suffix.removePending(entryDN);
        entryID = oldID;
        suffix.getDN2URI().replaceEntry(txn, oldEntry, entry);
      }

      suffix.getID2Entry().put(txn, entryID, entry);
      processIndexes(suffix, entry, entryID, oldEntry != null);
      processVLVIndexes(txn, suffix, entry, entryID);
      importCount.getAndIncrement();
    }

    @Override
    void processAttribute(MatchingRuleIndex index, Entry entry, EntryID entryID, IndexKey indexKey)
        throws StorageRuntimeException, InterruptedException
    {
      if (oldEntry != null)
      {
        processAttribute0(index, oldEntry, entryID, indexKey, false);
      }
      processAttribute0(index, entry, entryID, indexKey, true);
    }
  }

  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private class ImportTask implements Callable<Void>
  {
    private final Storage storage;
    private final Map<IndexKey, IndexOutputBuffer> indexBufferMap = new HashMap<>();
    private final IndexKey dnIndexKey = new IndexKey(DN_TYPE, DN2ID_INDEX_NAME, 1);

    public ImportTask(final Storage storage)
    {
      this.storage = storage;
    }

    /** {@inheritDoc} */
    @Override
    public final Void call() throws Exception
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          call0(txn);
        }
      });
      return null;
    }

    void call0(WriteableTransaction txn) throws Exception
    {
      try
      {
        EntryInformation entryInfo;
        while ((entryInfo = reader.readEntry(dnSuffixMap)) != null)
        {
          if (isCanceled())
          {
            freeBufferQueue.add(IndexOutputBuffer.poison());
            return;
          }
          processEntry(txn, entryInfo.getEntry(), entryInfo.getEntryID(), entryInfo.getSuffix());
        }
        flushIndexBuffers();
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_IMPORT_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(WriteableTransaction txn, Entry entry, EntryID entryID, Suffix suffix)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      DN entryDN = entry.getName();
      if (validateDNs && !dnSanityCheck(txn, entry, entryID, suffix))
      {
        suffix.removePending(entryDN);
        return;
      }
      suffix.removePending(entryDN);
      processDN2ID(suffix, entryDN, entryID);
      suffix.getDN2URI().addEntry(txn, entry);
      processIndexes(suffix, entry, entryID, false);
      processVLVIndexes(txn, suffix, entry, entryID);
      suffix.getID2Entry().put(txn, entryID, entry);
      importCount.getAndIncrement();
    }

    /**
     * Examine the DN for duplicates and missing parents.
     *
     * @return true if the import operation can proceed with the provided entry, false otherwise
     */
    @SuppressWarnings("javadoc")
    boolean dnSanityCheck(WriteableTransaction txn, Entry entry, EntryID entryID, Suffix suffix)
        throws StorageRuntimeException, InterruptedException
    {
      //Perform parent checking.
      DN entryDN = entry.getName();
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      DNCache localDnCache = clearedBackend ? dnCache : new Dn2IdDnCache(suffix, txn);
      if (parentDN != null && !suffix.isParentProcessed(parentDN, localDnCache))
      {
        reader.rejectEntry(entry, ERR_IMPORT_PARENT_NOT_FOUND.get(parentDN));
        return false;
      }
      if (!localDnCache.insert(entryDN, entryID))
      {
        reader.rejectEntry(entry, WARN_IMPORT_ENTRY_EXISTS.get());
        return false;
      }
      return true;
    }

    void processIndexes(Suffix suffix, Entry entry, EntryID entryID, boolean allIndexes)
        throws StorageRuntimeException, InterruptedException
    {
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        AttributeType attrType = mapEntry.getKey();
        AttributeIndex attrIndex = mapEntry.getValue();
        if (allIndexes || entry.hasAttribute(attrType))
        {
          for (Map.Entry<String, MatchingRuleIndex> mapEntry2 : attrIndex.getNameToIndexes().entrySet())
          {
            String indexID = mapEntry2.getKey();
            MatchingRuleIndex index = mapEntry2.getValue();

            IndexKey indexKey = new IndexKey(attrType, indexID, index.getIndexEntryLimit());
            processAttribute(index, entry, entryID, indexKey);
          }
        }
      }
    }

    void processVLVIndexes(WriteableTransaction txn, Suffix suffix, Entry entry, EntryID entryID)
        throws DirectoryException
    {
      final EntryContainer entryContainer = suffix.getEntryContainer();
      final IndexBuffer buffer = new IndexBuffer(entryContainer);
      for (VLVIndex vlvIdx : entryContainer.getVLVIndexes())
      {
        vlvIdx.addEntry(buffer, entryID, entry);
      }
      buffer.flush(txn);
    }

    void processAttribute(MatchingRuleIndex index, Entry entry, EntryID entryID, IndexKey indexKey)
        throws StorageRuntimeException, InterruptedException
    {
      processAttribute0(index, entry, entryID, indexKey, true);
    }

    void processAttribute0(MatchingRuleIndex index, Entry entry, EntryID entryID, IndexKey indexKey, boolean insert)
        throws InterruptedException
    {
      for (ByteString key : index.indexEntry(entry))
      {
        processKey(index, key, entryID, indexKey, insert);
      }
    }

    void flushIndexBuffers() throws InterruptedException, ExecutionException
    {
      final ArrayList<Future<Void>> futures = new ArrayList<>();
      for (IndexOutputBuffer indexBuffer : indexBufferMap.values())
      {
        indexBuffer.discard();
        futures.add(bufferSortService.submit(new SortTask(indexBuffer)));
      }
      indexBufferMap.clear();
      getAll(futures);
    }

    final int processKey(Tree tree, ByteString key, EntryID entryID, IndexKey indexKey, boolean insert)
        throws InterruptedException
    {
      int sizeNeeded = IndexOutputBuffer.getRequiredSize(key.length(), entryID.longValue());
      IndexOutputBuffer indexBuffer = indexBufferMap.get(indexKey);
      if (indexBuffer == null)
      {
        indexBuffer = getNewIndexBuffer(sizeNeeded, indexKey);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      else if (!indexBuffer.isSpaceAvailable(key, entryID.longValue()))
      {
        // complete the current buffer...
        bufferSortService.submit(new SortTask(indexBuffer));
        // ... and get a new one
        indexBuffer = getNewIndexBuffer(sizeNeeded, indexKey);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      int indexID = getIndexID(tree);
      indexBuffer.add(key, entryID, indexID, insert);
      return indexID;
    }

    IndexOutputBuffer getNewIndexBuffer(int size, IndexKey indexKey) throws InterruptedException
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
      indexBuffer.setIndexKey(indexKey);
      return indexBuffer;
    }

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
        throws InterruptedException
    {
      DN2ID dn2id = suffix.getDN2ID();
      ByteString dnBytes = dnToDNKey(dn, suffix.getBaseDN().size());
      int indexID = processKey(dn2id, dnBytes, entryID, dnIndexKey, true);
      indexIDToECMap.putIfAbsent(indexID, suffix.getEntryContainer());
    }
  }

  /**
   * This task reads sorted records from the temporary index scratch files,
   * processes the records and writes the results to the index tree. The DN
   * index is treated differently then non-DN indexes.
   */
  private final class IndexDBWriteTask implements Callable<Void>
  {
    private final Importer importer;
    private final IndexManager indexMgr;
    private final int cacheSize;
    /** indexID => DNState map */
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
     * @param importer
     *          The importer
     * @param indexMgr
     *          The index manager.
     * @param permits
     *          The semaphore used for restricting the number of buffer allocations.
     * @param maxPermits
     *          The maximum number of buffers which can be allocated.
     * @param cacheSize
     *          The buffer cache size.
     */
    public IndexDBWriteTask(Importer importer, IndexManager indexMgr, Semaphore permits, int maxPermits, int cacheSize)
    {
      this.importer = importer;
      this.indexMgr = indexMgr;
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
        buffers.add(
            new IndexInputBuffer(indexMgr, bufferFile.getChannel(),
                bufferBegin, bufferEnd, nextBufferID++, cacheSize));
      }

      return buffers;
    }

    /** Finishes this task. */
    private void endWriteTask(Importer importer)
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
            dnState.finalFlush(importer);
          }

          if (!isCanceled())
          {
            logger.info(NOTE_IMPORT_LDIF_DN_CLOSE, indexMgr.getDNCount());
          }
        }
        else
        {
          if (!isCanceled())
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
    public Void call() throws Exception
    {
      call0(importer);
      return null;
    }

    private void call0(Importer importer) throws Exception
    {
      if (isCanceled())
      {
        return;
      }

      ImportIDSet insertIDSet = null;
      ImportIDSet deleteIDSet = null;
      ImportRecord previousRecord = null;
      try
      {
        beginWriteTask();

        NavigableSet<IndexInputBuffer> bufferSet;
        while ((bufferSet = getNextBufferBatch()) != null)
        {
          if (isCanceled())
          {
            return;
          }

          while (!bufferSet.isEmpty())
          {
            IndexInputBuffer b = bufferSet.pollFirst();
            if (!b.currentRecord().equals(previousRecord))
            {
              if (previousRecord != null)
              {
                addToDB(importer, previousRecord.getIndexID(), insertIDSet, deleteIDSet);
              }

              // this is a new record
              final ImportRecord newRecord = b.currentRecord();
              insertIDSet = newImportIDSet(newRecord);
              deleteIDSet = newImportIDSet(newRecord);

              previousRecord = newRecord;
            }

            // merge all entryIds into the idSets
            b.mergeIDSet(insertIDSet);
            b.mergeIDSet(deleteIDSet);

            if (b.hasMoreData())
            {
              b.fetchNextRecord();
              bufferSet.add(b);
            }
          }

          if (previousRecord != null)
          {
            addToDB(importer, previousRecord.getIndexID(), insertIDSet, deleteIDSet);
          }
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_INDEX_WRITE_DB_ERR, indexMgr.getBufferFileName(), e.getMessage());
        throw e;
      }
      finally
      {
        endWriteTask(importer);
      }
    }

    private ImportIDSet newImportIDSet(ImportRecord record)
    {
      if (indexMgr.isDN2ID())
      {
        return new ImportIDSet(record.getKey(), newDefinedSet(), 1);
      }

      final Index index = indexIDToIndexMap.get(record.getIndexID());
      return new ImportIDSet(record.getKey(), newDefinedSet(), index.getIndexEntryLimit());
    }

    private void addToDB(Importer importer, int indexID, ImportIDSet insertSet, ImportIDSet deleteSet)
        throws DirectoryException
    {
      keyCount.incrementAndGet();
      if (indexMgr.isDN2ID())
      {
        addDN2ID(importer, indexID, insertSet);
      }
      else
      {
        if (!deleteSet.isDefined() || deleteSet.size() > 0)
        {
          final Index index = indexIDToIndexMap.get(indexID);
          index.importRemove(importer, deleteSet);
        }
        if (!insertSet.isDefined() || insertSet.size() > 0)
        {
          final Index index = indexIDToIndexMap.get(indexID);
          index.importPut(importer, insertSet);
        }
      }
    }

    private void addDN2ID(Importer importer, int indexID, ImportIDSet idSet) throws DirectoryException
    {
      DNState dnState = dnStateMap.get(indexID);
      if (dnState == null)
      {
        dnState = new DNState(indexIDToECMap.get(indexID));
        dnStateMap.put(indexID, dnState);
      }
      if (dnState.checkParent(importer, idSet))
      {
        dnState.writeToDN2ID(importer, idSet.getKey());
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
    private final class DNState
    {
      private static final int DN_STATE_CACHE_SIZE = 64 * KB;

      private final EntryContainer entryContainer;
      private final TreeName dn2id;
      private final TreeMap<ByteString, EntryID> parentIDMap = new TreeMap<>();
      private final Map<EntryID, AtomicLong> id2childrenCountTree = new TreeMap<>();
      private ByteSequence parentDN;
      private final ByteStringBuilder lastDN = new ByteStringBuilder();
      private EntryID parentID, lastID, entryID;
      private long totalNbEntries;

      private DNState(EntryContainer entryContainer)
      {
        this.entryContainer = entryContainer;
        dn2id = entryContainer.getDN2ID().getName();
      }

      private ByteSequence getParent(ByteSequence dn)
      {
        int parentIndex = findDNKeyParent(dn);
        if (parentIndex < 0)
        {
          // This is the root or base DN
          return null;
        }
        return dn.subSequence(0, parentIndex).toByteString();
      }

      /** Why do we still need this if we are checking parents in the first phase? */
      @SuppressWarnings("javadoc")
      boolean checkParent(Importer importer, ImportIDSet idSet) throws StorageRuntimeException
      {
        entryID = idSet.iterator().next();
        parentDN = getParent(idSet.getKey());

        if (bypassCacheForAppendMode())
        {
          // If null is returned then this is a suffix DN.
          if (parentDN != null)
          {
            parentID = get(importer, dn2id, parentDN);
            if (parentID == null)
            {
              // We have a missing parent. Maybe parent checking was turned off?
              // Just ignore.
              return false;
            }
          }
        }
        else if (parentIDMap.isEmpty())
        {
          parentIDMap.put(idSet.getKey().toByteString(), entryID);
          return true;
        }
        else if (lastID != null && lastDN.equals(parentDN))
        {
          parentIDMap.put(lastDN.toByteString(), lastID);
          parentID = lastID;
          lastDN.clear().append(idSet.getKey());
          lastID = entryID;
          return true;
        }
        else if (parentIDMap.lastKey().equals(parentDN))
        {
          parentID = parentIDMap.get(parentDN);
          lastDN.clear().append(idSet.getKey());
          lastID = entryID;
          return true;
        }
        else if (parentIDMap.containsKey(parentDN))
        {
          EntryID newParentID = parentIDMap.get(parentDN);
          ByteSequence key = parentIDMap.lastKey();
          while (!parentDN.equals(key))
          {
            parentIDMap.remove(key);
            key = parentIDMap.lastKey();
          }
          parentIDMap.put(idSet.getKey().toByteString(), entryID);
          parentID = newParentID;
          lastDN.clear().append(idSet.getKey());
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

      private AtomicLong getId2childrenCounter()
      {
        AtomicLong counter = id2childrenCountTree.get(parentID);
        if (counter == null)
        {
          counter = new AtomicLong();
          id2childrenCountTree.put(parentID, counter);
        }
        return counter;
      }

      /**
       * For append data, bypass the {@link #parentIDMap} cache, and lookup the parent DN in the
       * DN2ID index.
       */
      private boolean bypassCacheForAppendMode()
      {
        return importCfg != null && importCfg.appendToExistingData();
      }

      private EntryID get(Importer importer, TreeName dn2id, ByteSequence dn) throws StorageRuntimeException
      {
        ByteString value = importer.read(dn2id, dn);
        return value != null ? new EntryID(value) : null;
      }

      void writeToDN2ID(Importer importer, ByteSequence key) throws DirectoryException
      {
        importer.put(dn2id, key, entryID.toByteString());
        indexMgr.addTotDNCount(1);
        if (parentID != null)
        {
          incrementChildrenCounter(importer);
        }
      }

      private void incrementChildrenCounter(Importer importer)
      {
        final AtomicLong counter = getId2childrenCounter();
        counter.incrementAndGet();
        if (id2childrenCountTree.size() > DN_STATE_CACHE_SIZE)
        {
          flush(importer);
        }
      }

      private void flush(Importer importer)
      {
        for (Map.Entry<EntryID, AtomicLong> childrenCounter : id2childrenCountTree.entrySet())
        {
          final EntryID entryID = childrenCounter.getKey();
          final long totalForEntryID = childrenCounter.getValue().get();
          totalNbEntries += totalForEntryID;
          entryContainer.getID2ChildrenCount().importPut(importer, entryID, totalForEntryID);
        }
        id2childrenCountTree.clear();
      }

      void finalFlush(Importer importer)
      {
        flush(importer);

        entryContainer.getID2ChildrenCount().importPutTotalCount(importer, totalNbEntries);
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
    /** Stream where to output insert ImportIDSet data. */
    private final ByteArrayOutputStream insertByteStream = new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream insertByteDataStream = new DataOutputStream(insertByteStream);
    /** Stream where to output delete ImportIDSet data. */
    private final ByteArrayOutputStream deleteByteStream = new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream bufferStream;
    private final DataOutputStream bufferIndexStream;
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
          OnDiskMergeBufferImporter.this.bufferCount.incrementAndGet();

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
      long bufferLen = 0;
      final int numberKeys = indexBuffer.getNumberKeys();
      for (int i = 0; i < numberKeys; i++)
      {
        if (i == 0)
        {
          // first record, initialize all
          indexBuffer.setPosition(i);
          resetStreams();
        }
        else if (!indexBuffer.sameKeyAndIndexID(i))
        {
          // this is a new record, save previous record ...
          bufferLen += writeRecord(indexBuffer.currentRecord());
          // ... and reinitialize all
          indexBuffer.setPosition(i);
          resetStreams();
        }
        appendNextEntryIDToStream(indexBuffer, i);
      }
      if (numberKeys > 0)
      {
        // save the last record
        bufferLen += writeRecord(indexBuffer.currentRecord());
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
      ImportRecord previousRecord = null;
      while (!indexSortedSet.isEmpty())
      {
        final IndexOutputBuffer b = indexSortedSet.pollFirst();
        if (!b.currentRecord().equals(previousRecord))
        {
          if (previousRecord != null)
          {
            bufferLen += writeRecord(previousRecord);
            resetStreams();
          }
          // this is a new record
          previousRecord = b.currentRecord();
        }

        appendNextEntryIDToStream(b, b.getPosition());

        if (b.hasMoreData())
        {
          b.nextRecord();
          indexSortedSet.add(b);
        }
      }
      if (previousRecord != null)
      {
        bufferLen += writeRecord(previousRecord);
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

    private void appendNextEntryIDToStream(IndexOutputBuffer indexBuffer, int position)
    {
      if (indexBuffer.isInsertRecord(position))
      {
        if (insertKeyCount++ <= indexMgr.getIndexEntryLimit())
        {
          indexBuffer.writeEntryID(insertByteStream, position);
        }
        // else do not bother appending, this value will not be read.
        // instead, a special value will be written to show the index entry limit is exceeded
      }
      else
      {
        indexBuffer.writeEntryID(deleteByteStream, position);
        deleteKeyCount++;
      }
    }

    private int writeByteStreams() throws IOException
    {
      if (insertKeyCount > indexMgr.getIndexEntryLimit())
      {
        // special handling when index entry limit has been exceeded
        insertKeyCount = 1;
        insertByteStream.reset();
        insertByteDataStream.writeLong(IndexInputBuffer.UNDEFINED_SIZE);
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

    private int writeRecord(ImportRecord record) throws IOException
    {
      final ByteSequence key = record.getKey();
      int keySize = key.length();
      int headerSize = writeHeader(record.getIndexID(), keySize);
      key.copyTo(bufferStream);
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
      if (isCanceled())
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
        IndexManager indexMgr = new IndexManager(indexKey);
        indexMgrList.add(indexMgr);
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
    private final File bufferIndexFile;
    private final boolean isDN2ID;
    private final int indexEntryLimit;

    private int numberOfBuffers;
    private long bufferFileSize;
    private long totalDNs;
    private volatile IndexDBWriteTask writer;

    private IndexManager(IndexKey indexKey)
    {
      final String bufferFileName = indexKey.getName();
      final int entryLimit = indexKey.getEntryLimit();

      this.bufferFile = new File(tempDir, bufferFileName);
      this.bufferIndexFile = new File(tempDir, bufferFileName + ".index");

      this.isDN2ID = DN2ID_INDEX_NAME.equals(indexKey.getIndexID());
      this.indexEntryLimit = entryLimit > 0 ? entryLimit : Integer.MAX_VALUE;
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
      return bufferFile.getName();
    }

    private int getIndexEntryLimit()
    {
      return indexEntryLimit;
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
      return getClass().getSimpleName() + "(" + bufferFile + ")";
    }
  }

  /** The rebuild index manager handles all rebuild index related processing. */
  private class RebuildIndexManager extends ImportTask
  {
    /** Rebuild index configuration. */
    private final RebuildConfig rebuildConfig;
    /** Backend configuration. */
    private final PluggableBackendCfg cfg;

    /** Map of index keys to indexes to rebuild. */
    private final Map<IndexKey, MatchingRuleIndex> indexMap = new LinkedHashMap<>();
    /** List of VLV indexes to rebuild. */
    private final List<VLVIndex> vlvIndexes = new LinkedList<>();
    private boolean reBuildDn2id;
    private boolean rebuildDn2uri;

    /** The suffix instance. */
    private Suffix suffix;
    /** The entry container. */
    private EntryContainer entryContainer;

    /** Total entries to be processed. */
    private long totalEntries;
    /** Total entries processed. */
    private final AtomicLong entriesProcessed = new AtomicLong(0);

    RebuildIndexManager(Storage storage, RebuildConfig rebuildConfig, PluggableBackendCfg cfg)
    {
      super(storage);
      this.rebuildConfig = rebuildConfig;
      this.cfg = cfg;
    }

    void initialize() throws ConfigException, InitializationException
    {
      entryContainer = rootContainer.getEntryContainer(rebuildConfig.getBaseDN());
      suffix = new Suffix(entryContainer);
    }

    private void printStartMessage(WriteableTransaction txn) throws StorageRuntimeException
    {
      totalEntries = suffix.getID2Entry().getRecordCount(txn);

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

    void printStopMessage(long rebuildStartTime)
    {
      long finishTime = System.currentTimeMillis();
      long totalTime = finishTime - rebuildStartTime;
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

    @Override
    void call0(WriteableTransaction txn) throws Exception
    {
      ID2Entry id2entry = entryContainer.getID2Entry();
      Cursor<ByteString, ByteString> cursor = txn.openCursor(id2entry.getName());
      try
      {
        while (cursor.next())
        {
          if (isCanceled())
          {
            return;
          }
          EntryID entryID = new EntryID(cursor.getKey());
          Entry entry =
              ID2Entry.entryFromDatabase(cursor.getValue(),
                  entryContainer.getRootContainer().getCompressedSchema());
          processEntry(txn, entry, entryID);
          entriesProcessed.getAndIncrement();
        }
        flushIndexBuffers();
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

    private void clearDegradedState(WriteableTransaction txn)
    {
      setIndexesListsToBeRebuilt(txn);
      logger.info(NOTE_REBUILD_CLEARDEGRADEDSTATE_FINAL_STATUS, rebuildConfig.getRebuildList());
      postRebuildIndexes(txn);
    }

    private void preRebuildIndexes(WriteableTransaction txn)
    {
      setIndexesListsToBeRebuilt(txn);
      setRebuildListIndexesTrusted(txn, false);
      clearIndexesToBeRebuilt(txn);
    }

    private void throwIfCancelled() throws InterruptedException
    {
      if (isCanceled())
      {
        throw new InterruptedException("Rebuild Index canceled.");
      }
    }

    private void postRebuildIndexes(WriteableTransaction txn)
    {
      setRebuildListIndexesTrusted(txn, true);
    }

    private void setIndexesListsToBeRebuilt(WriteableTransaction txn) throws StorageRuntimeException
    {
      // Depends on rebuild mode, (re)building indexes' lists.
      final RebuildMode mode = rebuildConfig.getRebuildMode();
      switch (mode)
      {
      case ALL:
        reBuildDn2id = true;
        rebuildDn2uri = true;
        rebuildIndexMap(txn, false);
        vlvIndexes.addAll(entryContainer.getVLVIndexes());
        break;

      case DEGRADED:
        rebuildIndexMap(txn, true);
        if (vlvIndexes.isEmpty())
        {
          vlvIndexes.addAll(entryContainer.getVLVIndexes());
        }
        break;

      case USER_DEFINED:
        // false may be required if the user wants to rebuild specific index.
        rebuildIndexMap(txn, false);
        break;

      default:
        break;
      }
    }

    private void rebuildIndexMap(WriteableTransaction txn, boolean onlyDegraded)
    {
      for (final Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        final AttributeType attributeType = mapEntry.getKey();
        final AttributeIndex attributeIndex = mapEntry.getValue();
        if (mustRebuild(attributeType))
        {
          rebuildAttributeIndexes(txn, attributeIndex, attributeType, onlyDegraded);
        }
      }
    }

    private boolean mustRebuild(final AttributeType attrType)
    {
      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
      case DEGRADED:
        // Get all existing indexes
        return true;

      case USER_DEFINED:
        // Get the user selected indexes
        for (final String index : rebuildConfig.getRebuildList())
        {
          if (attrType.getNameOrOID().toLowerCase().equals(index.toLowerCase()))
          {
            return true;
          }
        }
        return false;

      default:
        return false;
      }
    }

    private void rebuildAttributeIndexes(WriteableTransaction txn, AttributeIndex attrIndex, AttributeType attrType,
        boolean onlyDegraded) throws StorageRuntimeException
    {
      for (Map.Entry<String, MatchingRuleIndex> mapEntry : attrIndex.getNameToIndexes().entrySet())
      {
        MatchingRuleIndex index = mapEntry.getValue();

        if ((!onlyDegraded || !index.isTrusted())
            && (!rebuildConfig.isClearDegradedState() || index.getRecordCount(txn) == 0))
        {
          putInIndexIDToIndexMap(index);

          final IndexKey key = new IndexKey(attrType, mapEntry.getKey(), index.getIndexEntryLimit());
          indexMap.put(key, index);
        }
      }
    }

    private void clearIndexesToBeRebuilt(WriteableTransaction txn) throws StorageRuntimeException
    {
      if (rebuildDn2uri)
      {
        entryContainer.clearTree(txn, entryContainer.getDN2URI());
      }

      if (reBuildDn2id)
      {
        entryContainer.clearTree(txn, entryContainer.getDN2ID());
        entryContainer.clearTree(txn, entryContainer.getID2ChildrenCount());
      }

      for (final Index index : indexMap.values())
      {
        if (!index.isTrusted())
        {
          entryContainer.clearTree(txn, index);
        }
      }

      for (final VLVIndex vlvIndex : vlvIndexes)
      {
        if (!vlvIndex.isTrusted())
        {
          entryContainer.clearTree(txn, vlvIndex);
        }
      }
    }

    private void setRebuildListIndexesTrusted(WriteableTransaction txn, boolean trusted) throws StorageRuntimeException
    {
      try
      {
        for (Index index : indexMap.values())
        {
          index.setTrusted(txn, trusted);
        }
        for (VLVIndex vlvIndex : vlvIndexes)
        {
          vlvIndex.setTrusted(txn, trusted);
        }
      }
      catch (StorageRuntimeException ex)
      {
        throw new StorageRuntimeException(NOTE_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage()).toString());
      }
    }

    /** @see #importPhaseOne(WriteableTransaction) */
    private void rebuildIndexesPhaseOne() throws StorageRuntimeException, InterruptedException,
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

    private void rebuildIndexesPhaseTwo() throws Exception
    {
      final Timer timer = scheduleAtFixedRate(new SecondPhaseProgressTask());
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

    private int getIndexCount() throws ConfigException, StorageRuntimeException, InitializationException
    {
      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        return getTotalIndexCount(cfg);
      case DEGRADED:
        // FIXME: since the storage is not opened we cannot determine which indexes are degraded.
        // As a workaround, be conservative and assume all indexes need rebuilding.
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
            throw new StorageRuntimeException(ERR_VLV_INDEX_NOT_CONFIGURED.get(lowerName).toString());
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
              if (PRESENCE.toString().equals(indexType)
                  || EQUALITY.toString().equals(indexType)
                  || ORDERING.toString().equals(indexType)
                  || SUBSTRING.toString().equals(indexType)
                  || APPROXIMATE.toString().equals(indexType))
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
      return new InitializationException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index));
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

    private void processEntry(WriteableTransaction txn, Entry entry, EntryID entryID)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      if (reBuildDn2id)
      {
        processDN2ID(suffix, entry.getName(), entryID);
      }
      if (rebuildDn2uri)
      {
        suffix.getDN2URI().addEntry(txn, entry);
      }
      processIndexes(entry, entryID);
      processVLVIndexes(txn, entry, entryID);
    }

    private void processVLVIndexes(WriteableTransaction txn, Entry entry, EntryID entryID)
        throws StorageRuntimeException, DirectoryException
    {
      final IndexBuffer buffer = new IndexBuffer(entryContainer);
      for (VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes())
      {
        vlvIdx.addEntry(buffer, entryID, entry);
      }
      buffer.flush(txn);
    }

    private void processIndexes(Entry entry, EntryID entryID)
        throws StorageRuntimeException, InterruptedException
    {
      for (Map.Entry<IndexKey, MatchingRuleIndex> mapEntry : indexMap.entrySet())
      {
        IndexKey indexKey = mapEntry.getKey();
        AttributeType attrType = indexKey.getAttributeType();
        if (entry.hasAttribute(attrType))
        {
          processAttribute(mapEntry.getValue(), entry, entryID, indexKey);
        }
      }
    }

    /**
     * Return the number of entries processed by the rebuild manager.
     *
     * @return The number of entries processed.
     */
    long getEntriesProcessed()
    {
      return this.entriesProcessed.get();
    }

    /**
     * Return the total number of entries to process by the rebuild manager.
     *
     * @return The total number for entries to process.
     */
    long getTotalEntries()
    {
      return this.totalEntries;
    }
  }

  /** This class reports progress of rebuild index processing at fixed intervals. */
  private class RebuildFirstPhaseProgressTask extends TimerTask
  {
    /** The number of records that had been processed at the time of the previous progress report. */
    private long previousProcessed;
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;

    /**
     * Create a new rebuild index progress task.
     *
     * @throws StorageRuntimeException
     *           If an error occurred while accessing the storage.
     */
    public RebuildFirstPhaseProgressTask() throws StorageRuntimeException
    {
      previousTime = System.currentTimeMillis();
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
      long entriesProcessed = rebuildManager.getEntriesProcessed();
      long deltaCount = entriesProcessed - previousProcessed;
      float rate = 1000f * deltaCount / deltaTime;
      float completed = 0;
      if (rebuildManager.getTotalEntries() > 0)
      {
        completed = 100f * entriesProcessed / rebuildManager.getTotalEntries();
      }
      logger.info(NOTE_REBUILD_PROGRESS_REPORT, completed, entriesProcessed, rebuildManager.getTotalEntries(), rate);

      previousProcessed = entriesProcessed;
      previousTime = latestTime;
    }
  }

  /** This class reports progress of first phase of import processing at fixed intervals. */
  private final class FirstPhaseProgressTask extends TimerTask
  {
    /** The number of entries that had been read at the time of the previous progress report. */
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

      previousCount = entriesRead;
      previousTime = latestTime;
    }
  }

  /** This class reports progress of the second phase of import processing at fixed intervals. */
  private class SecondPhaseProgressTask extends TimerTask
  {
    /** The time in milliseconds of the previous progress report. */
    private long previousTime;

    /** Create a new import progress task. */
    public SecondPhaseProgressTask()
    {
      previousTime = System.currentTimeMillis();
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

      // DN index managers first.
      printStats(deltaTime, true);
      // non-DN index managers second
      printStats(deltaTime, false);
    }

    private void printStats(long deltaTime, boolean dn2id)
    {
      for (IndexManager indexMgr : indexMgrList)
      {
        if (dn2id == indexMgr.isDN2ID())
        {
          indexMgr.printStats(deltaTime);
        }
      }
    }
  }

  /**
   * This class is used as an index key for hash maps that need to process multiple suffix index
   * elements into a single queue and/or maps based on both attribute type and index ID (ie.,
   * cn.equality, sn.equality,...).
   */
  static class IndexKey
  {
    private final AttributeType attributeType;
    private final String indexID;
    private final int entryLimit;

    /**
     * Create index key instance using the specified attribute type, index ID and index entry limit.
     *
     * @param attributeType
     *          The attribute type.
     * @param indexID
     *          The index ID taken from the matching rule's indexer.
     * @param entryLimit
     *          The entry limit for the index.
     */
    IndexKey(AttributeType attributeType, String indexID, int entryLimit)
    {
      this.attributeType = attributeType;
      this.indexID = indexID;
      this.entryLimit = entryLimit;
    }

    /**
     * An equals method that uses both the attribute type and the index ID. Only returns
     * {@code true} if the attribute type and index ID are equal.
     *
     * @param obj
     *          the object to compare.
     * @return {@code true} if the objects are equal, or {@code false} if they are not.
     */
    @Override
    public boolean equals(Object obj)
    {
      if (obj instanceof IndexKey)
      {
        IndexKey oKey = (IndexKey) obj;
        if (attributeType.equals(oKey.attributeType)
            && indexID.equals(oKey.indexID))
        {
          return true;
        }
      }
      return false;
    }

    /**
     * A hash code method that adds the hash codes of the attribute type and index ID and returns
     * that value.
     *
     * @return The combined hash values of attribute type hash code and the index ID hash code.
     */
    @Override
    public int hashCode()
    {
      return attributeType.hashCode() + indexID.hashCode();
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
     * Return the index ID.
     *
     * @return The index ID.
     */
    private String getIndexID()
    {
      return indexID;
    }

    /**
     * Return the index key name, which is the attribute type primary name, a period, and the index
     * ID name. Used for building file names and progress output.
     *
     * @return The index key name.
     */
    private String getName()
    {
      return attributeType.getPrimaryName() + "." + indexID;
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
          + "(index=" + attributeType.getNameOrOID() + "." + indexID
          + ", entryLimit=" + entryLimit
          + ")";
    }
  }

  /**
   * This interface is used by those suffix instance to do parental checking of the DN cache.
   * <p>
   * It will be shared when multiple suffixes are being processed.
   */
  public static interface DNCache extends Closeable
  {
    /**
     * Insert the specified DN into the DN cache. It will return {@code true} if the DN does not
     * already exist in the cache and was inserted, or {@code false} if the DN exists already in the
     * cache.
     *
     * @param dn
     *          The DN to insert in the cache.
     * @param entryID
     *          The entryID associated to the DN.
     * @return {@code true} if the DN was inserted in the cache, or {@code false} if the DN exists
     *         in the cache already and could not be inserted.
     * @throws StorageRuntimeException
     *           If an error occurs accessing the storage.
     */
    boolean insert(DN dn, EntryID entryID);

    /**
     * Returns whether the specified DN is contained in the DN cache.
     *
     * @param dn
     *          The DN to check the presence of.
     * @return {@code true} if the cache contains the DN, or {@code false} if it
     *         is not.
     * @throws StorageRuntimeException
     *           If an error occurs reading the storage.
     */
    boolean contains(DN dn) throws StorageRuntimeException;

    /**
     * Shuts the DN cache down.
     *
     * @throws StorageRuntimeException
     *           If error occurs.
     */
    @Override
    void close();
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
      final String methodName = method.getName();
      if ((methodName.startsWith("add") || methodName.startsWith("remove")) && methodName.endsWith("ChangeListener"))
      {
        // ignore calls to (add|remove)*ChangeListener() methods
        return null;
      }

      final Object returnValue = returnValues.get(methodName);
      if (returnValue != null)
      {
        return returnValue;
      }
      throw new IllegalArgumentException("Unhandled method call on proxy ("
          + BackendCfgHandler.class.getSimpleName()
          + ") for method (" + method
          + ") with arguments (" + Arrays.toString(args) + ")");
    }
  }

  /**
   * Used to check DN's when DN validation is performed during phase one processing.
   * It is deleted after phase one processing.
   */
  private final class DNCacheImpl implements DNCache
  {
    private static final String DB_NAME = "dn_cache";
    private final TreeName dnCache = new TreeName("", DB_NAME);
    private final Storage storage;

    private DNCacheImpl(File dnCachePath) throws StorageRuntimeException
    {
      final Map<String, Object> returnValues = new HashMap<>();
      returnValues.put("getDBDirectory", dnCachePath.getAbsolutePath());
      returnValues.put("getBackendId", DB_NAME);
      returnValues.put("getDBCacheSize", 0L);
      returnValues.put("getDBCachePercent", 10);
      returnValues.put("isDBTxnNoSync", true);
      returnValues.put("getDBDirectoryPermissions", "700");
      returnValues.put("getDiskLowThreshold", Long.valueOf(200 * MB));
      returnValues.put("getDiskFullThreshold", Long.valueOf(100 * MB));
      try
      {
        returnValues.put("dn", DN.valueOf("ds-cfg-backend-id=importDNCache,cn=Backends,cn=config"));
        storage = new PersistItStorage(newPersistitBackendCfgProxy(returnValues),
            DirectoryServer.getInstance().getServerContext());
        storage.open(AccessMode.READ_WRITE);
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            txn.openTree(dnCache);
          }
        });
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
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
    private ByteString fnv1AHashCode(DN dn)
    {
      final ByteString b = dn.toNormalizedByteString();

      long hash = FNV_INIT;
      for (int i = 0; i < b.length(); i++)
      {
        hash ^= b.byteAt(i);
        hash *= FNV_PRIME;
      }
      return ByteString.valueOf(hash);
    }

    @Override
    public void close() throws StorageRuntimeException
    {
      try
      {
        storage.close();
      }
      finally
      {
        storage.removeStorageFiles();
      }
    }

    @Override
    public boolean insert(DN dn, EntryID unused) throws StorageRuntimeException
    {
      // Use a compact representation for key
      // and a reversible representation for value
      final ByteString key = fnv1AHashCode(dn);
      final ByteString dnValue = ByteString.valueOf(dn);

      return insert(key, dnValue);
    }

    private boolean insert(final ByteString key, final ByteString dn) throws StorageRuntimeException
    {
      final AtomicBoolean updateResult = new AtomicBoolean();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            updateResult.set(txn.update(dnCache, key, new UpdateFunction()
            {
              @Override
              public ByteSequence computeNewValue(ByteSequence existingDns)
              {
                if (containsDN(existingDns, dn))
                {
                  // no change
                  return existingDns;
                }
                else if (existingDns != null)
                {
                  return addDN(existingDns, dn);
                }
                else
                {
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

              /** Create a list of dn made of one element. */
              private ByteSequence singletonList(final ByteSequence dntoAdd)
              {
                final ByteStringBuilder singleton = new ByteStringBuilder(dntoAdd.length() + INT_SIZE);
                singleton.append(dntoAdd.length());
                singleton.append(dntoAdd);
                return singleton;
              }
            }));
          }
        });
        return updateResult.get();
      }
      catch (StorageRuntimeException e)
      {
        throw e;
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    /** Return true if the specified DN is in the DNs saved as a result of hash collisions. */
    private boolean containsDN(ByteSequence existingDns, ByteString dnToFind)
    {
      if (existingDns != null && existingDns.length() > 0)
      {
        final ByteSequenceReader reader = existingDns.asReader();
        int pos = 0;
        while (reader.remaining() != 0)
        {
          int dnLength = reader.getInt();
          int dnStart = pos + INT_SIZE;
          ByteSequence existingDn = existingDns.subSequence(dnStart, dnStart + dnLength);
          if (dnToFind.equals(existingDn))
          {
            return true;
          }
          reader.skip(dnLength);
          pos = reader.position();
        }
      }
      return false;
    }

    @Override
    public boolean contains(final DN dn)
    {
      try
      {
        return storage.read(new ReadOperation<Boolean>()
        {
          @Override
          public Boolean run(ReadableTransaction txn) throws Exception
          {
            final ByteString key = fnv1AHashCode(dn);
            final ByteString existingDns = txn.read(dnCache, key);

            return containsDN(existingDns, ByteString.valueOf(dn));
          }
        });
      }
      catch (StorageRuntimeException e)
      {
        throw e;
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }
  }

  /** Cache used when the backend has not been cleared */
  private final class Dn2IdDnCache implements DNCache
  {
    private final Suffix suffix;
    private final ReadableTransaction txn;

    private Dn2IdDnCache(Suffix suffix, ReadableTransaction txn)
    {
      this.suffix = suffix;
      this.txn = txn;
    }

    @Override
    public boolean insert(DN dn, EntryID entryID)
    {
      return !existsInDN2ID(dn) && dnCache.insert(dn, entryID);
    }

    @Override
    public boolean contains(DN dn) throws StorageRuntimeException
    {
      return dnCache.contains(dn) || existsInDN2ID(dn);
    }

    private boolean existsInDN2ID(DN dn)
    {
      return suffix.getDN2ID().get(txn, dn) != null;
    }

    @Override
    public void close()
    {
      // Nothing to do
    }
  }
}
