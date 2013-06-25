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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */

package org.opends.server.backends.jeb.importLDIF;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.util.DynamicConstants.BUILD_ID;
import static org.opends.server.util.DynamicConstants.REVISION_NUMBER;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.backends.jeb.*;
import org.opends.server.backends.jeb.RebuildConfig.RebuildMode;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.types.*;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.Platform;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;
import com.sleepycat.util.PackedInteger;

/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
public final class Importer implements DiskSpaceMonitorHandler
{
  private static final int TIMER_INTERVAL = 10000;
  private static final int KB = 1024;
  private static final int MB = (KB * KB);
  private static final String DEFAULT_TMP_DIR = "import-tmp";
  private static final String TMPENV_DIR = "tmp-env";

  //Defaults for DB cache.
  private static final int MAX_DB_CACHE_SIZE = 8 * MB;
  private static final int MAX_DB_LOG_SIZE = 10 * MB;
  private static final int MIN_DB_CACHE_SIZE = 4 * MB;

  //Defaults for LDIF reader buffers, min memory required to import and default
  //size for byte buffers.
  private static final int READER_WRITER_BUFFER_SIZE = 8 * KB;
  private static final int MIN_DB_CACHE_MEMORY = MAX_DB_CACHE_SIZE
      + MAX_DB_LOG_SIZE;
  private static final int BYTE_BUFFER_CAPACITY = 128;

  //Min and MAX sizes of phase one buffer.
  private static final int MAX_BUFFER_SIZE = 2 * MB;
  private static final int MIN_BUFFER_SIZE = 4 * KB;

  //Min size of phase two read-ahead cache.
  private static final int MIN_READ_AHEAD_CACHE_SIZE = 2 * KB;

  //Small heap threshold used to give more memory to JVM to attempt OOM errors.
  private static final int SMALL_HEAP_SIZE = 256 * MB;

  //The DN attribute type.
  private static AttributeType dnType;
  static final IndexOutputBuffer.IndexComparator indexComparator =
      new IndexOutputBuffer.IndexComparator();

  //Phase one buffer and imported entries counts.
  private final AtomicInteger bufferCount = new AtomicInteger(0);
  private final AtomicLong importCount = new AtomicLong(0);

  //Phase one buffer size in bytes.
  private int bufferSize;

  //Temp scratch directory.
  private final File tempDir;

  //Index and thread counts.
  private final int indexCount;
  private int threadCount;

  //Set to true when validation is skipped.
  private final boolean skipDNValidation;

  //Temporary environment used when DN validation is done in first phase.
  private final TmpEnv tmpEnv;

  //Root container.
  private RootContainer rootContainer;

  //Import configuration.
  private final LDIFImportConfig importConfiguration;

  //Backend configuration.
  private final LocalDBBackendCfg backendConfiguration;

  //LDIF reader.
  private LDIFReader reader;

  //Migrated entry count.
  private int migratedCount;

  // Size in bytes of temporary env.
  private long tmpEnvCacheSize;

  // Available memory at the start of the import.
  private long availableMemory;

  // Size in bytes of DB cache.
  private long dbCacheSize;

  //The executor service used for the buffer sort tasks.
  private ExecutorService bufferSortService;

  //The executor service used for the scratch file processing tasks.
  private ExecutorService scratchFileWriterService;

  //Queue of free index buffers -- used to re-cycle index buffers;
  private final BlockingQueue<IndexOutputBuffer> freeBufferQueue =
      new LinkedBlockingQueue<IndexOutputBuffer>();

  //Map of index keys to index buffers.  Used to allocate sorted
  //index buffers to a index writer thread.
  private final Map<IndexKey, BlockingQueue<IndexOutputBuffer>> indexKeyQueMap =
      new ConcurrentHashMap<IndexKey, BlockingQueue<IndexOutputBuffer>>();

  //Map of DB containers to index managers. Used to start phase 2.
  private final List<IndexManager> indexMgrList =
      new LinkedList<IndexManager>();

  //Map of DB containers to DN-based index managers. Used to start phase 2.
  private final List<IndexManager> DNIndexMgrList =
      new LinkedList<IndexManager>();

  //Futures used to indicate when the index file writers are done flushing
  //their work queues and have exited. End of phase one.
  private final List<Future<?>> scratchFileWriterFutures;

  //List of index file writer tasks. Used to signal stopScratchFileWriters to
  //the index file writer tasks when the LDIF file has been done.
  private final List<ScratchFileWriterTask> scratchFileWriterList;

  //Map of DNs to Suffix objects.
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<DN, Suffix>();

  //Map of container ids to database containers.
  private final ConcurrentHashMap<Integer, DatabaseContainer> idContainerMap =
      new ConcurrentHashMap<Integer, DatabaseContainer>();

  //Map of container ids to entry containers
  private final ConcurrentHashMap<Integer, EntryContainer> idECMap =
      new ConcurrentHashMap<Integer, EntryContainer>();

  //Used to synchronize when a scratch file index writer is first setup.
  private final Object synObj = new Object();

  //Rebuild index manager used when rebuilding indexes.
  private final RebuildIndexManager rebuildManager;

  //Set to true if the backend was cleared.
  private boolean clearedBackend = false;

  //Used to shutdown import if an error occurs in phase one.
  private volatile boolean isCanceled = false;

  private volatile boolean isPhaseOneDone = false;

  //Number of phase one buffers
  private int phaseOneBufferCount;

  static
  {
    if ((dnType = DirectoryServer.getAttributeType("dn")) == null)
    {
      dnType = DirectoryServer.getDefaultAttributeType("dn");
    }
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
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws JebException
   *           If an error occurred when opening the DB.
   * @throws ConfigException
   *           If a problem occurs during initialization.
   */
  public Importer(RebuildConfig rebuildConfig, LocalDBBackendCfg cfg,
      EnvironmentConfig envConfig) throws InitializationException,
      JebException, ConfigException
  {
    this.importConfiguration = null;
    this.backendConfiguration = cfg;
    this.tmpEnv = null;
    this.threadCount = 1;
    this.rebuildManager = new RebuildIndexManager(rebuildConfig, cfg);
    this.indexCount = rebuildManager.getIndexCount();
    this.scratchFileWriterList =
        new ArrayList<ScratchFileWriterTask>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<Future<?>>();

    File parentDir;
    if (rebuildConfig.getTmpDirectory() == null)
    {
      parentDir = getFileForPath(DEFAULT_TMP_DIR);
    }
    else
    {
      parentDir = getFileForPath(rebuildConfig.getTmpDirectory());
    }

    this.tempDir = new File(parentDir, cfg.getBackendId());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      Message message =
          ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(String.valueOf(tempDir));
      throw new InitializationException(message);
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
   * @throws InitializationException
   *           If a problem occurs during initialization.
   * @throws ConfigException
   *           If a problem occurs reading the configuration.
   * @throws DatabaseException
   *           If an error occurred when opening the DB.
   */
  public Importer(LDIFImportConfig importConfiguration,
      LocalDBBackendCfg localDBBackendCfg, EnvironmentConfig envConfig)
      throws InitializationException, ConfigException, DatabaseException
  {
    this.rebuildManager = null;
    this.importConfiguration = importConfiguration;
    this.backendConfiguration = localDBBackendCfg;

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

    if (!importConfiguration.appendToExistingData())
    {
      if (importConfiguration.clearBackend()
          || localDBBackendCfg.getBaseDN().size() <= 1)
      {
        this.clearedBackend = true;
      }
    }
    this.scratchFileWriterList =
        new ArrayList<ScratchFileWriterTask>(indexCount);
    this.scratchFileWriterFutures = new CopyOnWriteArrayList<Future<?>>();
    File parentDir;
    if (importConfiguration.getTmpDirectory() == null)
    {
      parentDir = getFileForPath(DEFAULT_TMP_DIR);
    }
    else
    {
      parentDir = getFileForPath(importConfiguration.getTmpDirectory());
    }
    this.tempDir = new File(parentDir, localDBBackendCfg.getBackendId());
    recursiveDelete(tempDir);
    if (!tempDir.exists() && !tempDir.mkdirs())
    {
      Message message =
          ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(String.valueOf(tempDir));
      throw new InitializationException(message);
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
        indexes +=
            types.size() - 1 + index.getIndexExtensibleMatchingRule().size();
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
  private void initializeDBEnv(EnvironmentConfig envConfig)
      throws InitializationException
  {
    // Calculate amount of usable memory. This will need to take into account
    // various fudge factors, including the number of IO buffers used by the
    // scratch writers (1 per index).
    calculateAvailableMemory();

    final long usableMemory =
        availableMemory - (indexCount * READER_WRITER_BUFFER_SIZE);

    // We need caching when doing DN validation or rebuilding indexes.
    if (!skipDNValidation || (rebuildManager != null))
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

    final long phaseOneBufferMemory =
        usableMemory - dbCacheSize - tmpEnvCacheSize;
    final int oldThreadCount = threadCount;
    if (indexCount != 0) // Avoid / by zero
    {
      while (true)
      {
        phaseOneBufferCount = 2 * indexCount * threadCount;

        // Scratch writers allocate 4 buffers per index as well.
        final int totalPhaseOneBufferCount =
            phaseOneBufferCount + (4 * indexCount);
        bufferSize = (int) (phaseOneBufferMemory / totalPhaseOneBufferCount);

        if (bufferSize > MAX_BUFFER_SIZE)
        {
          if (!skipDNValidation)
          {
            // The buffers are big enough: the memory is best used for the DN2ID
            // temp DB.
            bufferSize = MAX_BUFFER_SIZE;

            final long extraMemory =
                phaseOneBufferMemory - (totalPhaseOneBufferCount * bufferSize);
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
          final long minimumPhaseOneBufferMemory =
              totalPhaseOneBufferCount * MIN_BUFFER_SIZE;
          Message message =
              ERR_IMPORT_LDIF_LACK_MEM.get(usableMemory,
                  minimumPhaseOneBufferMemory + dbCacheSize + tmpEnvCacheSize);
          throw new InitializationException(message);
        }
      }
    }

    if (oldThreadCount != threadCount)
    {
      Message message =
          NOTE_JEB_IMPORT_ADJUST_THREAD_COUNT.get(oldThreadCount, threadCount);
      logError(message);
    }

    Message message =
        NOTE_JEB_IMPORT_LDIF_TOT_MEM_BUF.get(availableMemory,
            phaseOneBufferCount);
    logError(message);
    if (tmpEnvCacheSize > 0)
    {
      message = NOTE_JEB_IMPORT_LDIF_TMP_ENV_MEM.get(tmpEnvCacheSize);
      logError(message);
    }
    envConfig.setConfigParam(EnvironmentConfig.MAX_MEMORY, Long
        .toString(dbCacheSize));
    message = NOTE_JEB_IMPORT_LDIF_DB_MEM_BUF_INFO.get(dbCacheSize, bufferSize);
    logError(message);
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
        configuredMemory =
            backendConfiguration.getDBCachePercent()
                * Runtime.getRuntime().maxMemory() / 100;
      }

      // Round up to minimum of 16MB (e.g. unit tests only use 2% cache).
      totalAvailableMemory =
          Math.max(Math.min(usableMemory, configuredMemory), 16 * MB);
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

    availableMemory = (totalAvailableMemory * importMemPct / 100);
  }

  private void initializeIndexBuffers()
  {
    for (int i = 0; i < phaseOneBufferCount; i++)
    {
      IndexOutputBuffer b = new IndexOutputBuffer(bufferSize);
      freeBufferQueue.add(b);
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
        generateIndexID(suffix);
      }
    }
  }

  //Mainly used to support multiple suffixes. Each index in each suffix gets
  //an unique ID to identify which DB it needs to go to in phase two processing.
  private void generateIndexID(Suffix suffix)
  {
    for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix
        .getAttrIndexMap().entrySet())
    {
      AttributeIndex attributeIndex = mapEntry.getValue();
      DatabaseContainer container;
      if ((container = attributeIndex.getEqualityIndex()) != null)
      {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if ((container = attributeIndex.getPresenceIndex()) != null)
      {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if ((container = attributeIndex.getSubstringIndex()) != null)
      {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if ((container = attributeIndex.getOrderingIndex()) != null)
      {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if ((container = attributeIndex.getApproximateIndex()) != null)
      {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      Map<String, Collection<Index>> extensibleMap =
          attributeIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        Collection<Index> subIndexes =
            attributeIndex.getExtensibleIndexes().get(
                EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if (subIndexes != null)
        {
          for (DatabaseContainer subIndex : subIndexes)
          {
            int id = System.identityHashCode(subIndex);
            idContainerMap.putIfAbsent(id, subIndex);
          }
        }
        Collection<Index> sharedIndexes =
            attributeIndex.getExtensibleIndexes().get(
                EXTENSIBLE_INDEXER_ID_SHARED);
        if (sharedIndexes != null)
        {
          for (DatabaseContainer sharedIndex : sharedIndexes)
          {
            int id = System.identityHashCode(sharedIndex);
            idContainerMap.putIfAbsent(id, sharedIndex);
          }
        }
      }
    }
  }

  private Suffix getSuffix(EntryContainer entryContainer)
      throws ConfigException, InitializationException
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
          boolean keep = true;
          for (DN dn : includeBranches)
          {
            if (!dn.equals(includeDN) && dn.isAncestorOf(includeDN))
            {
              keep = false;
              break;
            }
          }
          if (!keep)
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
          boolean keep = false;
          for (DN includeDN : includeBranches)
          {
            if (includeDN.isAncestorOf(excludeDN))
            {
              keep = true;
              break;
            }
          }
          if (!keep)
          {
            excludeBranchIterator.remove();
          }
        }

        if ((includeBranches.size() == 1) && excludeBranches.isEmpty()
            && includeBranches.get(0).equals(baseDN))
        {
          // This entire base DN is explicitly included in the import with
          // no exclude branches that we need to migrate. Just clear the entry
          // container.
          entryContainer.lock();
          entryContainer.clear();
          entryContainer.unlock();
        }
        else
        {
          // Create a temp entry container
          sourceEntryContainer = entryContainer;
          entryContainer =
              rootContainer.openEntryContainer(baseDN, baseDN
                  .toNormalizedString()
                  + "_importTmp");
        }
      }
    }
    return Suffix.createSuffixContext(entryContainer, sourceEntryContainer,
        includeBranches, excludeBranches);
  }

  /**
   * Rebuild the indexes using the specified rootcontainer.
   *
   * @param rootContainer
   *          The rootcontainer to rebuild indexes in.
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

    DiskSpaceMonitor tmpMonitor =
        new DiskSpaceMonitor(backendConfiguration.getBackendId()
            + " backend index rebuild tmp directory", tempDir,
            backendConfiguration.getDiskLowThreshold(), backendConfiguration
                .getDiskFullThreshold(), 5, TimeUnit.SECONDS, this);
    tmpMonitor.initializeMonitorProvider(null);
    DirectoryServer.registerMonitorProvider(tmpMonitor);
    File parentDirectory =
        getFileForPath(backendConfiguration.getDBDirectory());
    File backendDirectory =
        new File(parentDirectory, backendConfiguration.getBackendId());
    DiskSpaceMonitor dbMonitor =
        new DiskSpaceMonitor(backendConfiguration.getBackendId()
            + " backend index rebuild DB directory", backendDirectory,
            backendConfiguration.getDiskLowThreshold(), backendConfiguration
                .getDiskFullThreshold(), 5, TimeUnit.SECONDS, this);
    dbMonitor.initializeMonitorProvider(null);
    DirectoryServer.registerMonitorProvider(dbMonitor);

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
      DirectoryServer.deregisterMonitorProvider(tmpMonitor);
      DirectoryServer.deregisterMonitorProvider(dbMonitor);
      tmpMonitor.finalizeMonitorProvider();
      dbMonitor.finalizeMonitorProvider();
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
    try
    {
      reader =
          new LDIFReader(importConfiguration, rootContainer,
              READER_WRITER_BUFFER_SIZE);
    }
    catch (IOException ioe)
    {
      Message message = ERR_JEB_IMPORT_LDIF_READER_IO_ERROR.get();
      throw new InitializationException(message, ioe);
    }

    DiskSpaceMonitor tmpMonitor =
        new DiskSpaceMonitor(backendConfiguration.getBackendId()
            + " backend import tmp directory", tempDir, backendConfiguration
            .getDiskLowThreshold(),
            backendConfiguration.getDiskFullThreshold(), 5, TimeUnit.SECONDS,
            this);
    tmpMonitor.initializeMonitorProvider(null);
    DirectoryServer.registerMonitorProvider(tmpMonitor);
    File parentDirectory =
        getFileForPath(backendConfiguration.getDBDirectory());
    File backendDirectory =
        new File(parentDirectory, backendConfiguration.getBackendId());
    DiskSpaceMonitor dbMonitor =
        new DiskSpaceMonitor(backendConfiguration.getBackendId()
            + " backend import DB directory", backendDirectory,
            backendConfiguration.getDiskLowThreshold(), backendConfiguration
                .getDiskFullThreshold(), 5, TimeUnit.SECONDS, this);
    dbMonitor.initializeMonitorProvider(null);
    DirectoryServer.registerMonitorProvider(dbMonitor);

    try
    {
      Message message =
          NOTE_JEB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
              BUILD_ID, REVISION_NUMBER);
      logError(message);
      message = NOTE_JEB_IMPORT_THREAD_COUNT.get(threadCount);
      logError(message);
      initializeSuffixes();
      setIndexesTrusted(false);
      long startTime = System.currentTimeMillis();
      phaseOne();
      isPhaseOneDone = true;
      long phaseOneFinishTime = System.currentTimeMillis();
      if (!skipDNValidation)
      {
        tmpEnv.shutdown();
      }
      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }
      long phaseTwoTime = System.currentTimeMillis();
      phaseTwo();
      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }
      long phaseTwoFinishTime = System.currentTimeMillis();
      setIndexesTrusted(true);
      switchContainers();
      recursiveDelete(tempDir);
      long finishTime = System.currentTimeMillis();
      long importTime = (finishTime - startTime);
      float rate = 0;
      message =
          NOTE_JEB_IMPORT_PHASE_STATS.get(importTime / 1000,
              (phaseOneFinishTime - startTime) / 1000,
              (phaseTwoFinishTime - phaseTwoTime) / 1000);
      logError(message);
      if (importTime > 0)
        rate = 1000f * reader.getEntriesRead() / importTime;
      message =
          NOTE_JEB_IMPORT_FINAL_STATUS.get(reader.getEntriesRead(), importCount
              .get(), reader.getEntriesIgnored(), reader.getEntriesRejected(),
              migratedCount, importTime / 1000, rate);
      logError(message);
    }
    finally
    {
      reader.close();
      DirectoryServer.deregisterMonitorProvider(tmpMonitor);
      DirectoryServer.deregisterMonitorProvider(dbMonitor);
      tmpMonitor.finalizeMonitorProvider();
      dbMonitor.finalizeMonitorProvider();
    }
    return new LDIFImportResult(reader.getEntriesRead(), reader
        .getEntriesRejected(), reader.getEntriesIgnored());
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

  private void switchContainers() throws DatabaseException, JebException,
      InitializationException
  {

    for (Suffix suffix : dnSuffixMap.values())
    {
      DN baseDN = suffix.getBaseDN();
      EntryContainer entryContainer = suffix.getSrcEntryContainer();
      if (entryContainer != null)
      {
        EntryContainer needRegisterContainer =
            rootContainer.unregisterEntryContainer(baseDN);

        needRegisterContainer.lock();
        needRegisterContainer.close();
        needRegisterContainer.delete();
        needRegisterContainer.unlock();
        EntryContainer newEC = suffix.getEntryContainer();
        newEC.lock();
        newEC.setDatabasePrefix(baseDN.toNormalizedString());
        newEC.unlock();
        rootContainer.registerEntryContainer(baseDN, newEC);
      }
    }
  }

  private void setIndexesTrusted(boolean trusted) throws JebException
  {
    try
    {
      for (Suffix s : dnSuffixMap.values())
      {
        s.setIndexesTrusted(trusted);
      }
    }
    catch (DatabaseException ex)
    {
      Message message =
          NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage());
      throw new JebException(message);
    }
  }

  private void phaseOne() throws InterruptedException, ExecutionException
  {
    initializeIndexBuffers();
    FirstPhaseProgressTask progressTask = new FirstPhaseProgressTask();
    ScheduledThreadPoolExecutor timerService =
        new ScheduledThreadPoolExecutor(1);
    timerService.scheduleAtFixedRate(progressTask, TIMER_INTERVAL,
        TIMER_INTERVAL, TimeUnit.MILLISECONDS);
    scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
    bufferSortService = Executors.newFixedThreadPool(threadCount);
    ExecutorService execService = Executors.newFixedThreadPool(threadCount);
    List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
    tasks.add(new MigrateExistingTask());
    List<Future<Void>> results = execService.invokeAll(tasks);
    for (Future<Void> result : results)
    {
      if (!result.isDone())
      {
        result.get();
      }
    }
    tasks.clear();
    results.clear();
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
    results = execService.invokeAll(tasks);
    for (Future<Void> result : results)
    {
      if (!result.isDone())
      {
        result.get();
      }
    }
    tasks.clear();
    results.clear();
    tasks.add(new MigrateExcludedTask());
    results = execService.invokeAll(tasks);
    for (Future<Void> result : results)
    {
      if (!result.isDone())
      {
        result.get();
      }
    }
    stopScratchFileWriters();
    for (Future<?> result : scratchFileWriterFutures)
    {
      if (!result.isDone())
      {
        result.get();
      }
    }

    // Shutdown the executor services
    timerService.shutdown();
    timerService.awaitTermination(30, TimeUnit.SECONDS);
    execService.shutdown();
    execService.awaitTermination(30, TimeUnit.SECONDS);
    bufferSortService.shutdown();
    bufferSortService.awaitTermination(30, TimeUnit.SECONDS);
    scratchFileWriterService.shutdown();
    scratchFileWriterService.awaitTermination(30, TimeUnit.SECONDS);

    // Try to clear as much memory as possible.
    scratchFileWriterList.clear();
    scratchFileWriterFutures.clear();
    indexKeyQueMap.clear();
    freeBufferQueue.clear();
  }

  private void phaseTwo() throws InterruptedException, ExecutionException
  {
    SecondPhaseProgressTask progress2Task =
        new SecondPhaseProgressTask(reader.getEntriesRead());
    ScheduledThreadPoolExecutor timerService =
        new ScheduledThreadPoolExecutor(1);
    timerService.scheduleAtFixedRate(progress2Task, TIMER_INTERVAL,
        TIMER_INTERVAL, TimeUnit.MILLISECONDS);
    try
    {
      processIndexFiles();
    }
    finally
    {
      timerService.shutdown();
      timerService.awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  private void processIndexFiles() throws InterruptedException,
      ExecutionException
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
      final List<IndexManager> totList =
          new ArrayList<IndexManager>(DNIndexMgrList);
      totList.addAll(indexMgrList);
      Collections.sort(totList, Collections.reverseOrder());

      buffers = 0;
      final int limit = Math.min(dbThreads, totList.size());
      for (int i = 0; i < limit; i++)
      {
        buffers += totList.get(i).numberOfBuffers;
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

        Message message = WARN_IMPORT_LDIF_LACK_MEM_PHASE_TWO.get(usableMemory);
        logError(message);
        break;
      }
    }

    // Ensure that there are always two threads available for parallel
    // processing of smaller indexes.
    dbThreads = Math.max(2, dbThreads);

    Message message =
        NOTE_JEB_IMPORT_LDIF_PHASE_TWO_MEM_REPORT.get(availableMemory,
            readAheadSize, buffers);
    logError(message);

    // Start indexing tasks.
    List<Future<Void>> futures = new LinkedList<Future<Void>>();
    ExecutorService dbService = Executors.newFixedThreadPool(dbThreads);
    Semaphore permits = new Semaphore(buffers);

    // Start DN processing first.
    for (IndexManager dnMgr : DNIndexMgrList)
    {
      futures.add(dbService.submit(new IndexDBWriteTask(dnMgr, permits,
          buffers, readAheadSize)));
    }
    for (IndexManager mgr : indexMgrList)
    {
      futures.add(dbService.submit(new IndexDBWriteTask(mgr, permits, buffers,
          readAheadSize)));
    }

    for (Future<Void> result : futures)
    {
      if (!result.isDone())
      {
        result.get();
      }
    }

    dbService.shutdown();
  }

  private void stopScratchFileWriters()
  {
    IndexOutputBuffer indexBuffer = new IndexOutputBuffer(0);
    for (ScratchFileWriterTask task : scratchFileWriterList)
    {
      task.queue.add(indexBuffer);
    }
  }

  /**
   * Task used to migrate excluded branch.
   */
  private final class MigrateExcludedTask extends ImportTask
  {

    /**
     * {@inheritDoc}
     */
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
          OperationStatus status;
          Message message =
              NOTE_JEB_IMPORT_MIGRATION_START.get("excluded", String
                  .valueOf(suffix.getBaseDN()));
          logError(message);
          Cursor cursor =
              entryContainer.getDN2ID().openCursor(null,
                  CursorConfig.READ_COMMITTED);
          Comparator<byte[]> comparator =
              entryContainer.getDN2ID().getComparator();
          try
          {
            for (DN excludedDN : suffix.getExcludeBranches())
            {
              byte[] bytes =
                  JebFormat.dnToDNKey(excludedDN, suffix.getBaseDN()
                      .getNumComponents());
              key.setData(bytes);
              status = cursor.getSearchKeyRange(key, data, lockMode);
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
                  Entry entry =
                      entryContainer.getID2Entry().get(null, id,
                          LockMode.DEFAULT);
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
            message =
                ERR_JEB_IMPORT_LDIF_MIGRATE_EXCLUDED_TASK_ERR.get(e
                    .getMessage());
            logError(message);
            isCanceled = true;
            throw e;
          }
          finally
          {
            cursor.close();
          }
        }
      }
      return null;
    }
  }

  /**
   * Task to migrate existing entries.
   */
  private final class MigrateExistingTask extends ImportTask
  {

    /**
     * {@inheritDoc}
     */
    @Override
    public Void call() throws Exception
    {
      for (Suffix suffix : dnSuffixMap.values())
      {
        List<byte[]> includeBranches =
            new ArrayList<byte[]>(suffix.getIncludeBranches().size());
        for (DN includeBranch : suffix.getIncludeBranches())
        {
          if (includeBranch.isDescendantOf(suffix.getBaseDN()))
          {
            includeBranches.add(JebFormat.dnToDNKey(includeBranch, suffix
                .getBaseDN().getNumComponents()));
          }
        }

        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if (entryContainer != null && !suffix.getIncludeBranches().isEmpty())
        {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          OperationStatus status;
          Message message =
              NOTE_JEB_IMPORT_MIGRATION_START.get("existing", String
                  .valueOf(suffix.getBaseDN()));
          logError(message);
          Cursor cursor = entryContainer.getDN2ID().openCursor(null, null);
          try
          {
            status = cursor.getFirst(key, data, lockMode);
            while (status == OperationStatus.SUCCESS
                && !importConfiguration.isCancelled() && !isCanceled)
            {

              boolean found = false;
              for (byte[] includeBranch : includeBranches)
              {
                if (Arrays.equals(includeBranch, key.getData()))
                {
                  found = true;
                  break;
                }
              }
              if (!found)
              {
                EntryID id = new EntryID(data);
                Entry entry =
                    entryContainer.getID2Entry()
                        .get(null, id, LockMode.DEFAULT);
                processEntry(entry, rootContainer.getNextEntryID(), suffix);
                migratedCount++;
                status = cursor.getNext(key, data, lockMode);
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
            message =
                ERR_JEB_IMPORT_LDIF_MIGRATE_EXISTING_TASK_ERR.get(e
                    .getMessage());
            logError(message);
            isCanceled = true;
            throw e;
          }
          finally
          {
            cursor.close();
          }
        }
      }
      return null;
    }
  }

  /**
   * Task to perform append/replace processing.
   */
  private class AppendReplaceTask extends ImportTask
  {
    private final Set<byte[]> insertKeySet = new HashSet<byte[]>(),
        deleteKeySet = new HashSet<byte[]>();
    private final EntryInformation entryInfo = new EntryInformation();
    private Entry oldEntry;
    private EntryID entryID;

    /**
     * {@inheritDoc}
     */
    @Override
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isCanceled)
          {
            IndexOutputBuffer indexBuffer = new IndexOutputBuffer(0);
            freeBufferQueue.add(indexBuffer);
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
      }
      catch (Exception e)
      {
        Message message =
            ERR_JEB_IMPORT_LDIF_APPEND_REPLACE_TASK_ERR.get(e.getMessage());
        logError(message);
        isCanceled = true;
        throw e;
      }
      return null;
    }

    void processEntry(Entry entry, Suffix suffix) throws DatabaseException,
        DirectoryException, JebException, InterruptedException

    {
      DN entryDN = entry.getDN();
      DN2ID dn2id = suffix.getDN2ID();
      EntryID oldID = dn2id.get(null, entryDN, LockMode.DEFAULT);
      if (oldID != null)
      {
        oldEntry = suffix.getID2Entry().get(null, oldID, LockMode.DEFAULT);
      }
      if (oldEntry == null)
      {
        if (!skipDNValidation)
        {
          if (!dnSanityCheck(entryDN, entry, suffix))
          {
            suffix.removePending(entryDN);
            return;
          }
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
      if (oldEntry == null)
      {
        processIndexes(suffix, entry, entryID);
      }
      else
      {
        processAllIndexes(suffix, entry, entryID);
      }
      importCount.getAndIncrement();
    }

    void processAllIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException,
        InterruptedException
    {
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix
          .getAttrIndexMap().entrySet())
      {
        AttributeType attributeType = mapEntry.getKey();
        fillIndexKey(suffix, mapEntry, entry, attributeType, entryID);
      }
    }

    @Override
    void processAttribute(Index index, Entry entry, EntryID entryID,
        IndexKey indexKey) throws DatabaseException, InterruptedException
    {
      if (oldEntry != null)
      {
        deleteKeySet.clear();
        index.indexer.indexEntry(oldEntry, deleteKeySet);
        for (byte[] delKey : deleteKeySet)
        {
          processKey(index, delKey, entryID, indexComparator, indexKey, false);
        }
      }
      insertKeySet.clear();
      index.indexer.indexEntry(entry, insertKeySet);
      for (byte[] key : insertKeySet)
      {
        processKey(index, key, entryID, indexComparator, indexKey, true);
      }
    }
  }

  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private class ImportTask implements Callable<Void>
  {
    private final Map<IndexKey, IndexOutputBuffer> indexBufferMap =
        new HashMap<IndexKey, IndexOutputBuffer>();
    private final Set<byte[]> insertKeySet = new HashSet<byte[]>();
    private final EntryInformation entryInfo = new EntryInformation();
    private DatabaseEntry keyEntry = new DatabaseEntry(),
        valEntry = new DatabaseEntry();

    /**
     * {@inheritDoc}
     */
    @Override
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isCanceled)
          {
            IndexOutputBuffer indexBuffer = new IndexOutputBuffer(0);
            freeBufferQueue.add(indexBuffer);
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
      }
      catch (Exception e)
      {
        Message message =
            ERR_JEB_IMPORT_LDIF_IMPORT_TASK_ERR.get(e.getMessage());
        logError(message);
        isCanceled = true;
        throw e;
      }
      return null;
    }

    void processEntry(Entry entry, EntryID entryID, Suffix suffix)
        throws DatabaseException, DirectoryException, JebException,
        InterruptedException

    {
      DN entryDN = entry.getDN();
      if (!skipDNValidation)
      {
        if (!dnSanityCheck(entryDN, entry, suffix))
        {
          suffix.removePending(entryDN);
          return;
        }
      }
      suffix.removePending(entryDN);
      processDN2ID(suffix, entryDN, entryID);
      processDN2URI(suffix, null, entry);
      processIndexes(suffix, entry, entryID);
      suffix.getID2Entry().put(null, entryID, entry);
      importCount.getAndIncrement();
    }

    //Examine the DN for duplicates and missing parents.
    boolean dnSanityCheck(DN entryDN, Entry entry, Suffix suffix)
        throws JebException, InterruptedException
    {
      //Perform parent checking.
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      if (parentDN != null)
      {
        if (!suffix.isParentProcessed(parentDN, tmpEnv, clearedBackend))
        {
          Message message =
              ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
          reader.rejectEntry(entry, message);
          return false;
        }
      }
      //If the backend was not cleared, then the dn2id needs to checked first
      //for DNs that might not exist in the DN cache. If the DN is not in
      //the suffixes dn2id DB, then the dn cache is used.
      if (!clearedBackend)
      {
        EntryID id = suffix.getDN2ID().get(null, entryDN, LockMode.DEFAULT);
        if (id != null || !tmpEnv.insert(entryDN, keyEntry, valEntry))
        {
          Message message = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
          reader.rejectEntry(entry, message);
          return false;
        }
      }
      else if (!tmpEnv.insert(entryDN, keyEntry, valEntry))
      {
        Message message = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
        reader.rejectEntry(entry, message);
        return false;
      }
      return true;
    }

    void processIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException,
        InterruptedException
    {
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix
          .getAttrIndexMap().entrySet())
      {
        AttributeType attributeType = mapEntry.getKey();
        if (entry.hasAttribute(attributeType))
        {
          fillIndexKey(suffix, mapEntry, entry, attributeType, entryID);
        }
      }
    }

    void fillIndexKey(Suffix suffix,
        Map.Entry<AttributeType, AttributeIndex> mapEntry, Entry entry,
        AttributeType attributeType, EntryID entryID) throws DatabaseException,
        InterruptedException, DirectoryException, JebException
    {
      AttributeIndex attributeIndex = mapEntry.getValue();
      Index index;
      if ((index = attributeIndex.getEqualityIndex()) != null)
      {
        processAttribute(index, entry, entryID, new IndexKey(attributeType,
            ImportIndexType.EQUALITY, index.getIndexEntryLimit()));
      }
      if ((index = attributeIndex.getPresenceIndex()) != null)
      {
        processAttribute(index, entry, entryID, new IndexKey(attributeType,
            ImportIndexType.PRESENCE, index.getIndexEntryLimit()));
      }
      if ((index = attributeIndex.getSubstringIndex()) != null)
      {
        processAttribute(index, entry, entryID, new IndexKey(attributeType,
            ImportIndexType.SUBSTRING, index.getIndexEntryLimit()));
      }
      if ((index = attributeIndex.getOrderingIndex()) != null)
      {
        processAttribute(index, entry, entryID, new IndexKey(attributeType,
            ImportIndexType.ORDERING, index.getIndexEntryLimit()));
      }
      if ((index = attributeIndex.getApproximateIndex()) != null)
      {
        processAttribute(index, entry, entryID, new IndexKey(attributeType,
            ImportIndexType.APPROXIMATE, index.getIndexEntryLimit()));
      }
      for (VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes())
      {
        Transaction transaction = null;
        vlvIdx.addEntry(transaction, entryID, entry);
      }
      Map<String, Collection<Index>> extensibleMap =
          attributeIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        Collection<Index> subIndexes =
            attributeIndex.getExtensibleIndexes().get(
                EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if (subIndexes != null)
        {
          for (Index subIndex : subIndexes)
          {
            processAttribute(subIndex, entry, entryID, new IndexKey(
                attributeType, ImportIndexType.EX_SUBSTRING, subIndex
                    .getIndexEntryLimit()));
          }
        }
        Collection<Index> sharedIndexes =
            attributeIndex.getExtensibleIndexes().get(
                EXTENSIBLE_INDEXER_ID_SHARED);
        if (sharedIndexes != null)
        {
          for (Index sharedIndex : sharedIndexes)
          {
            processAttribute(sharedIndex, entry, entryID, new IndexKey(
                attributeType, ImportIndexType.EX_SHARED, sharedIndex
                    .getIndexEntryLimit()));
          }
        }
      }
    }

    void processAttribute(Index index, Entry entry, EntryID entryID,
        IndexKey indexKey) throws DatabaseException, InterruptedException
    {
      insertKeySet.clear();
      index.indexer.indexEntry(entry, insertKeySet);
      for (byte[] key : insertKeySet)
      {
        processKey(index, key, entryID, indexComparator, indexKey, true);
      }
    }

    void flushIndexBuffers() throws InterruptedException, ExecutionException
    {
      Set<Map.Entry<IndexKey, IndexOutputBuffer>> set =
          indexBufferMap.entrySet();
      Iterator<Map.Entry<IndexKey, IndexOutputBuffer>> setIterator =
          set.iterator();
      while (setIterator.hasNext())
      {
        Map.Entry<IndexKey, IndexOutputBuffer> e = setIterator.next();
        IndexKey indexKey = e.getKey();
        IndexOutputBuffer indexBuffer = e.getValue();
        setIterator.remove();
        indexBuffer.setComparator(indexComparator);
        indexBuffer.setIndexKey(indexKey);
        indexBuffer.setDiscard();
        Future<Void> future =
            bufferSortService.submit(new SortTask(indexBuffer));
        future.get();
      }
    }

    int processKey(DatabaseContainer container, byte[] key, EntryID entryID,
        IndexOutputBuffer.ComparatorBuffer<byte[]> comparator,
        IndexKey indexKey, boolean insert) throws InterruptedException
    {
      int sizeNeeded = IndexOutputBuffer.getRequiredSize(
          key.length, entryID.longValue());
      IndexOutputBuffer indexBuffer = indexBufferMap.get(indexKey);
      if (indexBuffer == null)
      {
        indexBuffer = getNewIndexBuffer(sizeNeeded);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      else if (!indexBuffer.isSpaceAvailable(key, entryID.longValue()))
      {
        indexBuffer.setComparator(comparator);
        indexBuffer.setIndexKey(indexKey);
        bufferSortService.submit(new SortTask(indexBuffer));
        indexBuffer = getNewIndexBuffer(sizeNeeded);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      int id = System.identityHashCode(container);
      indexBuffer.add(key, entryID, id, insert);
      return id;
    }

    IndexOutputBuffer getNewIndexBuffer(int size)
        throws InterruptedException
    {
      IndexOutputBuffer indexBuffer;
      if (size > bufferSize)
      {
        indexBuffer = new IndexOutputBuffer(size);
        indexBuffer.setDiscard();
      }
      else
      {
        indexBuffer = freeBufferQueue.take();
        if (indexBuffer == null)
        {
          Message message =
              Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                  "Index buffer processing error.");
          throw new InterruptedException(message.toString());
        }
      }
      if (indexBuffer.isPoison())
      {
        Message message =
            Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                "Cancel processing received.");
        throw new InterruptedException(message.toString());
      }
      return indexBuffer;
    }

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
        throws InterruptedException
    {
      DN2ID dn2id = suffix.getDN2ID();
      byte[] dnBytes =
          JebFormat.dnToDNKey(dn, suffix.getBaseDN().getNumComponents());
      int id =
          processKey(dn2id, dnBytes, entryID, indexComparator, new IndexKey(
              dnType, ImportIndexType.DN, 1), true);
      idECMap.putIfAbsent(id, suffix.getEntryContainer());
    }

    void processDN2URI(Suffix suffix, Entry oldEntry, Entry newEntry)
        throws DatabaseException
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
    private final Map<Integer, DNState> dnStateMap =
        new HashMap<Integer, DNState>();
    private final Map<Integer, Index> indexMap = new HashMap<Integer, Index>();
    private final Semaphore permits;
    private final int maxPermits;
    private final AtomicLong bytesRead = new AtomicLong();
    private long lastBytesRead = 0;
    private final AtomicInteger keyCount = new AtomicInteger();
    private RandomAccessFile bufferFile = null;
    private DataInputStream bufferIndexFile = null;
    private int remainingBuffers;
    private volatile int totalBatches;
    private AtomicInteger batchNumber = new AtomicInteger();
    private int nextBufferID;
    private int ownedPermits;
    private volatile boolean isRunning = false;

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

      Message message =
          NOTE_JEB_IMPORT_LDIF_INDEX_STARTED.get(indexMgr.getBufferFileName(),
              remainingBuffers, totalBatches);
      logError(message);

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
      final NavigableSet<IndexInputBuffer> buffers =
          new TreeSet<IndexInputBuffer>();
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
            Message msg =
                NOTE_JEB_IMPORT_LDIF_DN_CLOSE.get(indexMgr.getDNCount());
            logError(msg);
          }
        }
        else
        {
          for (Index index : indexMap.values())
          {
            index.closeCursor();
          }
          if (!isCanceled)
          {
            Message message =
                NOTE_JEB_IMPORT_LDIF_INDEX_CLOSE.get(indexMgr
                    .getBufferFileName());
            logError(message);
          }
        }
      }
      finally
      {
        if (bufferFile != null)
        {
          try
          {
            bufferFile.close();
          }
          catch (IOException ignored)
          {
            // Ignore.
          }
        }

        if (bufferIndexFile != null)
        {
          try
          {
            bufferIndexFile.close();
          }
          catch (IOException ignored)
          {
            // Ignore.
          }
        }

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

        Message message =
            NOTE_JEB_IMPORT_LDIF_PHASE_TWO_REPORT.get(indexMgr
                .getBufferFileName(), bytesReadPercent, kiloBytesRemaining,
                kiloBytesRate, currentBatch, totalBatches);
        logError(message);

        lastBytesRead = tmpBytesRead;
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Void call() throws Exception, DirectoryException
    {
      ByteBuffer key = null;
      ImportIDSet insertIDSet = null;
      ImportIDSet deleteIDSet = null;
      Integer indexID = null;

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
                Index index = (Index) idContainerMap.get(indexID);
                int limit = index.getIndexEntryLimit();
                boolean doCount = index.getMaintainCount();
                insertIDSet = new ImportIDSet(1, limit, doCount);
                deleteIDSet = new ImportIDSet(1, limit, doCount);
              }

              key = ByteBuffer.allocate(b.getKeyLen());
              key.flip();
              b.getKey(key);

              b.mergeIDSet(insertIDSet);
              b.mergeIDSet(deleteIDSet);
              insertIDSet.setKey(key);
              deleteIDSet.setKey(key);
            }
            else if (b.compare(key, indexID) != 0)
            {
              addToDB(insertIDSet, deleteIDSet, indexID);
              keyCount.incrementAndGet();

              indexID = b.getIndexID();

              if (indexMgr.isDN2ID())
              {
                insertIDSet = new ImportIDSet(1, 1, false);
                deleteIDSet = new ImportIDSet(1, 1, false);
              }
              else
              {
                Index index = (Index) idContainerMap.get(indexID);
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
              b.getKey(key);

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
              b.getNextRecord();
              bufferSet.add(b);
            }
          }

          if (key != null)
          {
            addToDB(insertIDSet, deleteIDSet, indexID);
          }
        }
      }
      catch (Exception e)
      {
        Message message =
            ERR_JEB_IMPORT_LDIF_INDEX_WRITE_DB_ERR.get(indexMgr
                .getBufferFileName(), e.getMessage());
        logError(message);
        throw e;
      }
      finally
      {
        endWriteTask();
      }
      return null;
    }

    private void addToDB(ImportIDSet insertSet, ImportIDSet deleteSet,
        int indexID) throws DirectoryException
    {
      if (!indexMgr.isDN2ID())
      {
        Index index;
        if ((deleteSet.size() > 0) || (!deleteSet.isDefined()))
        {
          dbKey.setData(deleteSet.getKey().array(), 0, deleteSet.getKey()
              .limit());
          index = (Index) idContainerMap.get(indexID);
          index.delete(dbKey, deleteSet, dbValue);
          if (!indexMap.containsKey(indexID))
          {
            indexMap.put(indexID, index);
          }
        }
        if ((insertSet.size() > 0) || (!insertSet.isDefined()))
        {
          dbKey.setData(insertSet.getKey().array(), 0, insertSet.getKey()
              .limit());
          index = (Index) idContainerMap.get(indexID);
          index.insert(dbKey, insertSet, dbValue);
          if (!indexMap.containsKey(indexID))
          {
            indexMap.put(indexID, index);
          }
        }
      }
      else
      {
        addDN2ID(insertSet, indexID);
      }
    }

    private void addDN2ID(ImportIDSet record, Integer indexID)
        throws DirectoryException
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
      if (!dnState.checkParent(record))
      {
        return;
      }
      dnState.writeToDB();
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
      private final Map<byte[], ImportIDSet> id2childTree;
      private final Map<byte[], ImportIDSet> id2subtreeTree;
      private final int childLimit, subTreeLimit;
      private final boolean childDoCount, subTreeDoCount;

      DNState(EntryContainer entryContainer)
      {
        this.entryContainer = entryContainer;
        parentIDMap = new TreeMap<ByteBuffer, EntryID>();
        Comparator<byte[]> childComparator =
            entryContainer.getID2Children().getComparator();
        id2childTree = new TreeMap<byte[], ImportIDSet>(childComparator);
        childLimit = entryContainer.getID2Children().getIndexEntryLimit();
        childDoCount = entryContainer.getID2Children().getMaintainCount();
        Comparator<byte[]> subComparator =
            entryContainer.getID2Subtree().getComparator();
        subTreeLimit = entryContainer.getID2Subtree().getIndexEntryLimit();
        subTreeDoCount = entryContainer.getID2Subtree().getMaintainCount();
        id2subtreeTree = new TreeMap<byte[], ImportIDSet>(subComparator);
        dnKey = new DatabaseEntry();
        dnValue = new DatabaseEntry();
        lastDN = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
      }

      private ByteBuffer getParent(ByteBuffer buffer)
      {
        int parentIndex =
            JebFormat.findDNKeyParent(buffer.array(), 0, buffer.limit());
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

      // Why do we still need this if we are checking parents in the first
      // phase?
      private boolean checkParent(ImportIDSet record) throws DatabaseException
      {
        dnKey.setData(record.getKey().array(), 0, record.getKey().limit());
        byte[] v = record.toDatabase();
        long v1 = JebFormat.entryIDFromDatabase(v);
        dnValue.setData(v);

        entryID = new EntryID(v1);
        parentDN = getParent(record.getKey());

        //Bypass the cache for append data, lookup the parent in DN2ID and
        //return.
        if (importConfiguration != null
            && importConfiguration.appendToExistingData())
        {
          //If null is returned than this is a suffix DN.
          if (parentDN != null)
          {
            DatabaseEntry key =
                new DatabaseEntry(parentDN.array(), 0, parentDN.limit());
            DatabaseEntry value = new DatabaseEntry();
            OperationStatus status;
            status =
                entryContainer.getDN2ID().read(null, key, value,
                    LockMode.DEFAULT);
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
        else
        {
          if (parentIDMap.isEmpty())
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
          else
          {
            if (parentIDMap.containsKey(parentDN))
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
          }
        }
        return true;
      }

      private void id2child(EntryID childID) throws DirectoryException
      {
        ImportIDSet idSet;
        if (parentID != null)
        {
          if (!id2childTree.containsKey(parentID.getDatabaseEntry().getData()))
          {
            idSet = new ImportIDSet(1, childLimit, childDoCount);
            id2childTree.put(parentID.getDatabaseEntry().getData(), idSet);
          }
          else
          {
            idSet = id2childTree.get(parentID.getDatabaseEntry().getData());
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
        EntryID nodeID;
        //Bypass the cache for append data, lookup the parent DN in the DN2ID
        //db.
        if (importConfiguration != null
            && importConfiguration.appendToExistingData())
        {
          DatabaseEntry key = new DatabaseEntry(dn.array(), 0, dn.limit());
          DatabaseEntry value = new DatabaseEntry();
          OperationStatus status;
          status =
              entryContainer.getDN2ID()
                  .read(null, key, value, LockMode.DEFAULT);
          if (status == OperationStatus.SUCCESS)
          {
            nodeID = new EntryID(value);
          }
          else
          {
            nodeID = null;
          }
        }
        else
        {
          nodeID = parentIDMap.get(dn);
        }
        return nodeID;
      }

      private void id2SubTree(EntryID childID) throws DirectoryException
      {
        if (parentID != null)
        {
          ImportIDSet idSet;
          if (!id2subtreeTree
              .containsKey(parentID.getDatabaseEntry().getData()))
          {
            idSet = new ImportIDSet(1, subTreeLimit, subTreeDoCount);
            id2subtreeTree.put(parentID.getDatabaseEntry().getData(), idSet);
          }
          else
          {
            idSet = id2subtreeTree.get(parentID.getDatabaseEntry().getData());
          }
          idSet.addEntryID(childID);
          // TODO:
          // Instead of doing this,
          // we can just walk to parent cache if available
          for (ByteBuffer dn = getParent(parentDN); dn != null; dn =
              getParent(dn))
          {
            EntryID nodeID = getParentID(dn);
            if (nodeID == null)
            {
              // We have a missing parent. Maybe parent checking was turned off?
              // Just ignore.
              break;
            }
            if (!id2subtreeTree
                .containsKey(nodeID.getDatabaseEntry().getData()))
            {
              idSet = new ImportIDSet(1, subTreeLimit, subTreeDoCount);
              id2subtreeTree.put(nodeID.getDatabaseEntry().getData(), idSet);
            }
            else
            {
              idSet = id2subtreeTree.get(nodeID.getDatabaseEntry().getData());
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
        if (parentDN != null)
        {
          id2child(entryID);
          id2SubTree(entryID);
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
        index.closeCursor();
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
    private final ByteArrayOutputStream insertByteStream =
        new ByteArrayOutputStream(2 * bufferSize);
    private final ByteArrayOutputStream deleteByteStream =
        new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream bufferStream;
    private final DataOutputStream bufferIndexStream;
    private final byte[] tmpArray = new byte[8];
    private int insertKeyCount = 0, deleteKeyCount = 0;
    private int bufferCount = 0;
    private final SortedSet<IndexOutputBuffer> indexSortedSet;
    private boolean poisonSeen = false;

    public ScratchFileWriterTask(BlockingQueue<IndexOutputBuffer> queue,
        IndexManager indexMgr) throws FileNotFoundException
    {
      this.queue = queue;
      this.indexMgr = indexMgr;
      this.bufferStream =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(
              indexMgr.getBufferFile()), READER_WRITER_BUFFER_SIZE));
      this.bufferIndexStream =
          new DataOutputStream(new BufferedOutputStream(new FileOutputStream(
              indexMgr.getBufferIndexFile()), READER_WRITER_BUFFER_SIZE));
      this.indexSortedSet = new TreeSet<IndexOutputBuffer>();
    }

    /**
     * {@inheritDoc}
     */
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
              if (!id.isDiscard())
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
            if (!indexBuffer.isDiscard())
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
      }
      catch (IOException e)
      {
        Message message =
            ERR_JEB_IMPORT_LDIF_INDEX_FILEWRITER_ERR.get(indexMgr
                .getBufferFile().getAbsolutePath(), e.getMessage());
        logError(message);
        isCanceled = true;
        throw e;
      }
      finally
      {
        bufferStream.close();
        bufferIndexStream.close();
        indexMgr.setBufferInfo(bufferCount, indexMgr.getBufferFile().length());
      }
      return null;
    }

    private long writeIndexBuffer(IndexOutputBuffer indexBuffer)
        throws IOException
    {
      int numberKeys = indexBuffer.getNumberKeys();
      indexBuffer.setPosition(-1);
      long bufferLen = 0;
      insertByteStream.reset();
      insertKeyCount = 0;
      deleteByteStream.reset();
      deleteKeyCount = 0;
      for (int i = 0; i < numberKeys; i++)
      {
        if (indexBuffer.getPosition() == -1)
        {
          indexBuffer.setPosition(i);
          if (indexBuffer.isInsert(i))
          {
            indexBuffer.writeID(insertByteStream, i);
            insertKeyCount++;
          }
          else
          {
            indexBuffer.writeID(deleteByteStream, i);
            deleteKeyCount++;
          }
          continue;
        }
        if (!indexBuffer.compare(i))
        {
          bufferLen += writeRecord(indexBuffer);
          indexBuffer.setPosition(i);
          insertByteStream.reset();
          insertKeyCount = 0;
          deleteByteStream.reset();
          deleteKeyCount = 0;
        }
        if (indexBuffer.isInsert(i))
        {
          if (insertKeyCount++ <= indexMgr.getLimit())
          {
            indexBuffer.writeID(insertByteStream, i);
          }
        }
        else
        {
          indexBuffer.writeID(deleteByteStream, i);
          deleteKeyCount++;
        }
      }
      if (indexBuffer.getPosition() != -1)
      {
        bufferLen += writeRecord(indexBuffer);
      }
      return bufferLen;
    }

    private long writeIndexBuffers(List<IndexOutputBuffer> buffers)
        throws IOException
    {
      long id = 0;
      long bufferLen = 0;
      insertByteStream.reset();
      insertKeyCount = 0;
      deleteByteStream.reset();
      deleteKeyCount = 0;
      for (IndexOutputBuffer b : buffers)
      {
        if (b.isPoison())
        {
          poisonSeen = true;
        }
        else
        {
          b.setPosition(0);
          b.setID(id++);
          indexSortedSet.add(b);
        }
      }
      byte[] saveKey = null;
      int saveIndexID = 0;
      while (!indexSortedSet.isEmpty())
      {
        IndexOutputBuffer b = indexSortedSet.first();
        indexSortedSet.remove(b);
        if (saveKey == null)
        {
          saveKey = b.getKey();
          saveIndexID = b.getIndexID();
          if (b.isInsert(b.getPosition()))
          {
            b.writeID(insertByteStream, b.getPosition());
            insertKeyCount++;
          }
          else
          {
            b.writeID(deleteByteStream, b.getPosition());
            deleteKeyCount++;
          }
        }
        else
        {
          if (!b.compare(saveKey, saveIndexID))
          {
            bufferLen += writeRecord(saveKey, saveIndexID);
            insertByteStream.reset();
            deleteByteStream.reset();
            insertKeyCount = 0;
            deleteKeyCount = 0;
            saveKey = b.getKey();
            saveIndexID = b.getIndexID();
            if (b.isInsert(b.getPosition()))
            {
              b.writeID(insertByteStream, b.getPosition());
              insertKeyCount++;
            }
            else
            {
              b.writeID(deleteByteStream, b.getPosition());
              deleteKeyCount++;
            }
          }
          else
          {
            if (b.isInsert(b.getPosition()))
            {
              if (insertKeyCount++ <= indexMgr.getLimit())
              {
                b.writeID(insertByteStream, b.getPosition());
              }
            }
            else
            {
              b.writeID(deleteByteStream, b.getPosition());
              deleteKeyCount++;
            }
          }
        }
        if (b.hasMoreData())
        {
          b.getNextRecord();
          indexSortedSet.add(b);
        }
      }
      if (saveKey != null)
      {
        bufferLen += writeRecord(saveKey, saveIndexID);
      }
      return bufferLen;
    }

    private int writeByteStreams() throws IOException
    {
      if (insertKeyCount > indexMgr.getLimit())
      {
        insertKeyCount = 1;
        insertByteStream.reset();
        PackedInteger.writeInt(tmpArray, 0, -1);
        insertByteStream.write(tmpArray, 0, 1);
      }
      int insertSize = PackedInteger.getWriteIntLength(insertKeyCount);
      PackedInteger.writeInt(tmpArray, 0, insertKeyCount);
      bufferStream.write(tmpArray, 0, insertSize);
      if (insertByteStream.size() > 0)
      {
        insertByteStream.writeTo(bufferStream);
      }
      int deleteSize = PackedInteger.getWriteIntLength(deleteKeyCount);
      PackedInteger.writeInt(tmpArray, 0, deleteKeyCount);
      bufferStream.write(tmpArray, 0, deleteSize);
      if (deleteByteStream.size() > 0)
      {
        deleteByteStream.writeTo(bufferStream);
      }
      return insertSize + deleteSize;
    }

    private int writeHeader(int indexID, int keySize) throws IOException
    {
      bufferStream.writeInt(indexID);
      int packedSize = PackedInteger.getWriteIntLength(keySize);
      PackedInteger.writeInt(tmpArray, 0, keySize);
      bufferStream.write(tmpArray, 0, packedSize);
      return packedSize;
    }

    private int writeRecord(IndexOutputBuffer b) throws IOException
    {
      int keySize = b.getKeySize();
      int packedSize = writeHeader(b.getIndexID(), keySize);
      b.writeKey(bufferStream);
      packedSize += writeByteStreams();
      return (packedSize + keySize + insertByteStream.size()
          + deleteByteStream.size() + 4);
    }

    private int writeRecord(byte[] k, int indexID) throws IOException
    {
      int packedSize = writeHeader(indexID, k.length);
      bufferStream.write(k);
      packedSize += writeByteStreams();
      return (packedSize + k.length + insertByteStream.size()
          + deleteByteStream.size() + 4);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Void call() throws Exception
    {
      if (importConfiguration != null && importConfiguration.isCancelled()
          || isCanceled)
      {
        isCanceled = true;
        return null;
      }
      indexBuffer.sort();
      if (indexKeyQueMap.containsKey(indexBuffer.getIndexKey()))
      {
        BlockingQueue<IndexOutputBuffer> q =
            indexKeyQueMap.get(indexBuffer.getIndexKey());
        q.add(indexBuffer);
      }
      else
      {
        createIndexWriterTask(indexBuffer.getIndexKey());
        BlockingQueue<IndexOutputBuffer> q =
            indexKeyQueMap.get(indexBuffer.getIndexKey());
        q.add(indexBuffer);
      }
      return null;
    }

    private void createIndexWriterTask(IndexKey indexKey)
        throws FileNotFoundException
    {
      boolean isDN = false;
      synchronized (synObj)
      {
        if (indexKeyQueMap.containsKey(indexKey))
        {
          return;
        }
        if (indexKey.getIndexType().equals(ImportIndexType.DN))
        {
          isDN = true;
        }
        IndexManager indexMgr =
            new IndexManager(indexKey.getName(), isDN,
                indexKey.getEntryLimit());
        if (isDN)
        {
          DNIndexMgrList.add(indexMgr);
        }
        else
        {
          indexMgrList.add(indexMgr);
        }
        BlockingQueue<IndexOutputBuffer> newQue =
            new ArrayBlockingQueue<IndexOutputBuffer>(phaseOneBufferCount);
        ScratchFileWriterTask indexWriter =
            new ScratchFileWriterTask(newQue, indexMgr);
        scratchFileWriterList.add(indexWriter);
        scratchFileWriterFutures.add(scratchFileWriterService
            .submit(indexWriter));
        indexKeyQueMap.put(indexKey, newQue);
      }
    }
  }

  /**
   * The index manager class has several functions: 1. It used to carry
   * information about index processing created in phase one to phase two. 2. It
   * collects statistics about phase two processing for each index. 3. It
   * manages opening and closing the scratch index files.
   */
  final class IndexManager implements Comparable<IndexManager>
  {
    private final File bufferFile;
    private final String bufferFileName;
    private final File bufferIndexFile;
    private final String bufferIndexFileName;
    private long bufferFileSize;
    private long totalDNS;
    private final boolean isDN;
    private final int limit;
    private int numberOfBuffers = 0;
    private volatile IndexDBWriteTask writer = null;

    private IndexManager(String fileName, boolean isDN, int limit)
    {
      this.bufferFileName = fileName;
      this.bufferIndexFileName = fileName + ".index";

      this.bufferFile = new File(tempDir, bufferFileName);
      this.bufferIndexFile = new File(tempDir, bufferIndexFileName);

      this.isDN = isDN;
      if (limit > 0)
      {
        this.limit = limit;
      }
      else
      {
        this.limit = Integer.MAX_VALUE;
      }
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
      totalDNS += delta;
    }

    private long getDNCount()
    {
      return totalDNS;
    }

    private boolean isDN2ID()
    {
      return isDN;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IndexManager mgr)
    {
      return numberOfBuffers - mgr.numberOfBuffers;
    }

    private int getNumberOfBuffers()
    {
      return numberOfBuffers;
    }
  }

  /**
   * The rebuild index manager handles all rebuild index related processing.
   */
  private class RebuildIndexManager extends ImportTask implements
      DiskSpaceMonitorHandler
  {

    //Rebuild index configuration.
    private final RebuildConfig rebuildConfig;

    //Local DB backend configuration.
    private final LocalDBBackendCfg cfg;

    //Map of index keys to indexes.
    private final Map<IndexKey, Index> indexMap =
        new LinkedHashMap<IndexKey, Index>();

    //Map of index keys to extensible indexes.
    private final Map<IndexKey, Collection<Index>> extensibleIndexMap =
        new LinkedHashMap<IndexKey, Collection<Index>>();

    //List of VLV indexes.
    private final List<VLVIndex> vlvIndexes = new LinkedList<VLVIndex>();

    //The DN2ID index.
    private DN2ID dn2id = null;

    //The DN2URI index.
    private DN2URI dn2uri = null;

    //Total entries to be processed.
    private long totalEntries = 0;

    //Total entries processed.
    private final AtomicLong entriesProcessed = new AtomicLong(0);

    //The suffix instance.
    private Suffix suffix = null;

    //The entry container.
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
      entryContainer =
          rootContainer.getEntryContainer(rebuildConfig.getBaseDN());
      suffix = Suffix.createSuffixContext(entryContainer, null, null, null);
      if (suffix == null)
      {
        Message msg =
            ERR_JEB_REBUILD_SUFFIX_ERROR.get(rebuildConfig.getBaseDN()
                .toString());
        throw new InitializationException(msg);
      }
    }

    /**
     * Print start message.
     *
     * @throws DatabaseException
     *           If an database error occurred.
     */
    public void printStartMessage() throws DatabaseException
    {
      StringBuilder sb = new StringBuilder();
      List<String> rebuildList = rebuildConfig.getRebuildList();
      for (String index : rebuildList)
      {
        if (sb.length() > 0)
        {
          sb.append(", ");
        }
        sb.append(index);
      }
      totalEntries = suffix.getID2Entry().getRecordCount();

      Message message = null;
      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        message = NOTE_JEB_REBUILD_ALL_START.get(totalEntries);
        break;
      case DEGRADED:
        message = NOTE_JEB_REBUILD_DEGRADED_START.get(totalEntries);
        break;
      default:
        if (!rebuildConfig.isClearDegradedState())
        {
          message = NOTE_JEB_REBUILD_START.get(sb.toString(), totalEntries);
        }
        break;
      }
      if ( message != null )
      {
        logError(message);
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
      long totalTime = (finishTime - startTime);
      float rate = 0;
      if (totalTime > 0)
      {
        rate = 1000f * entriesProcessed.get() / totalTime;
      }

      if (!rebuildConfig.isClearDegradedState())
      {
        Message message =
            NOTE_JEB_REBUILD_FINAL_STATUS.get(entriesProcessed.get(),
                totalTime / 1000, rate);
        logError(message);
      }
    }

    /**
     * {@inheritDoc}
     */
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
        cursor.close();
      }
      catch (Exception e)
      {
        Message message =
            ERR_JEB_IMPORT_LDIF_REBUILD_INDEX_TASK_ERR.get(e.getMessage());
        logError(message);
        isCanceled = true;
        throw e;
      }
      finally
      {
        cursor.close();
      }
      return null;
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
        clearIndexes(true);
        phaseOne();
        if (isCanceled)
        {
          throw new InterruptedException("Rebuild Index canceled.");
        }
        phaseTwo();
      }
      else
      {
        Message message =
            NOTE_JEB_REBUILD_CLEARDEGRADEDSTATE_FINAL_STATUS.get(rebuildConfig
                .getRebuildList().toString());
        logError(message);
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
        if ((mode == RebuildMode.ALL)
            || (!entryContainer.getID2Children().isTrusted() || !entryContainer
                .getID2Subtree().isTrusted()))
        {
          dn2id = entryContainer.getDN2ID();
        }
        if ((mode == RebuildMode.ALL) || entryContainer.getDN2URI() == null)
        {
          dn2uri = entryContainer.getDN2URI();
        }
        if ((mode == RebuildMode.DEGRADED)
            || entryContainer.getAttributeIndexes().isEmpty())
        {
          rebuildIndexMap(true); // only degraded.
        }
        if ((mode == RebuildMode.ALL) || vlvIndexes.isEmpty())
        {
          vlvIndexes.addAll(new LinkedList<VLVIndex>(entryContainer
              .getVLVIndexes()));
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
      for (final Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix
          .getAttrIndexMap().entrySet())
      {
        final AttributeType attributeType = mapEntry.getKey();
        final AttributeIndex attributeIndex = mapEntry.getValue();
        if (rebuildConfig.getRebuildMode() == RebuildMode.ALL
            || rebuildConfig.getRebuildMode() == RebuildMode.DEGRADED)
        {
          // Get all existing indexes for all && degraded mode.
          rebuildAttributeIndexes(attributeIndex, attributeType, onlyDegraded);
        }
        else
        {
          // Get indexes for user defined index.
          if (!rebuildList.isEmpty())
          {
            for (final String index : rebuildList)
            {
              if (attributeType.getNameOrOID().toLowerCase().equals(
                  index.toLowerCase()))
              {
                rebuildAttributeIndexes(attributeIndex, attributeType,
                    onlyDegraded);
              }
            }
          }
        }
      }
    }

    private void rebuildAttributeIndexes(final AttributeIndex attrIndex,
        final AttributeType attrType, final boolean onlyDegraded)
        throws DatabaseException
    {
      if (attrIndex.getSubstringIndex() != null)
      {
        fillIndexMap(attrType, attrIndex.getSubstringIndex(),
            ImportIndexType.SUBSTRING, onlyDegraded);
      }
      if (attrIndex.getOrderingIndex() != null)
      {
        fillIndexMap(attrType, attrIndex.getOrderingIndex(),
            ImportIndexType.ORDERING, onlyDegraded);
      }
      if (attrIndex.getEqualityIndex() != null)
      {
        fillIndexMap(attrType, attrIndex.getEqualityIndex(),
            ImportIndexType.EQUALITY, onlyDegraded);
      }
      if (attrIndex.getPresenceIndex() != null)
      {
        fillIndexMap(attrType, attrIndex.getPresenceIndex(),
            ImportIndexType.PRESENCE, onlyDegraded);
      }
      if (attrIndex.getApproximateIndex() != null)
      {
        fillIndexMap(attrType, attrIndex.getApproximateIndex(),
            ImportIndexType.APPROXIMATE, onlyDegraded);
      }
      final Map<String, Collection<Index>> extensibleMap =
          attrIndex.getExtensibleIndexes();
      if (!extensibleMap.isEmpty())
      {
        final Collection<Index> subIndexes =
            attrIndex.getExtensibleIndexes().get(
                EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if (subIndexes != null && !subIndexes.isEmpty())
        {
          final List<Index> mutableCopy = new LinkedList<Index>(subIndexes);
          final Iterator<Index> i = mutableCopy.iterator();
          while (i.hasNext())
          {
            final Index subIndex = i.next();
            if (!onlyDegraded || !subIndex.isTrusted())
            {
              if ((rebuildConfig.isClearDegradedState() && subIndex
                  .getRecordCount() == 0)
                  || !rebuildConfig.isClearDegradedState())
              {
                int id = System.identityHashCode(subIndex);
                idContainerMap.putIfAbsent(id, subIndex);
              }
            }
            else
            {
              // This index is not a candidate for rebuilding.
              i.remove();
            }
          }
          if (!mutableCopy.isEmpty())
          {
            extensibleIndexMap.put(new IndexKey(attrType,
                ImportIndexType.EX_SUBSTRING, 0), mutableCopy);
          }
        }
        final Collection<Index> sharedIndexes =
            attrIndex.getExtensibleIndexes().get(EXTENSIBLE_INDEXER_ID_SHARED);
        if (sharedIndexes != null && !sharedIndexes.isEmpty())
        {
          final List<Index> mutableCopy = new LinkedList<Index>(sharedIndexes);
          final Iterator<Index> i = mutableCopy.iterator();
          while (i.hasNext())
          {
            final Index sharedIndex = i.next();
            if (!onlyDegraded || !sharedIndex.isTrusted())
            {
              if ((rebuildConfig.isClearDegradedState() && sharedIndex
                  .getRecordCount() == 0)
                  || !rebuildConfig.isClearDegradedState())
              {
                int id = System.identityHashCode(sharedIndex);
                idContainerMap.putIfAbsent(id, sharedIndex);
              }
            }
            else
            {
              // This index is not a candidate for rebuilding.
              i.remove();
            }
          }
          if (!mutableCopy.isEmpty())
          {
            extensibleIndexMap.put(new IndexKey(attrType,
                ImportIndexType.EX_SHARED, 0), mutableCopy);
          }
        }
      }
    }

    private void fillIndexMap(final AttributeType attrType,
        final Index partialAttrIndex, final ImportIndexType importIndexType,
        final boolean onlyDegraded)
    {
      if ((!onlyDegraded || !partialAttrIndex.isTrusted()))
      {
        if ((rebuildConfig.isClearDegradedState() && partialAttrIndex
            .getRecordCount() == 0)
            || !rebuildConfig.isClearDegradedState())
        {
          final int id = System.identityHashCode(partialAttrIndex);
          idContainerMap.putIfAbsent(id, partialAttrIndex);
          final IndexKey indexKey =
              new IndexKey(attrType, importIndexType, partialAttrIndex
                  .getIndexEntryLimit());
          indexMap.put(indexKey, partialAttrIndex);
        }
      }
    }

    private void clearIndexes(boolean onlyDegraded) throws DatabaseException
    {
      // Clears all the entry's container databases
      // which are containing the indexes.

      if (!onlyDegraded)
      {
        // dn2uri does not have a trusted status.
        entryContainer.clearDatabase(entryContainer.getDN2URI());
      }

      if (!onlyDegraded || !entryContainer.getID2Children().isTrusted()
          || !entryContainer.getID2Subtree().isTrusted())
      {
        entryContainer.clearDatabase(entryContainer.getDN2ID());
        entryContainer.clearDatabase(entryContainer.getID2Children());
        entryContainer.clearDatabase(entryContainer.getID2Subtree());
      }

      if (!indexMap.isEmpty())
      {
        for (final Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet())
        {
          if (!onlyDegraded || !mapEntry.getValue().isTrusted())
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
        if (!onlyDegraded || !vlvIndex.isTrusted())
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
        if (dn2id != null)
        {
          EntryContainer ec = suffix.getEntryContainer();
          ec.getID2Children().setTrusted(null, trusted);
          ec.getID2Subtree().setTrusted(null, trusted);
        }
        if (!indexMap.isEmpty())
        {
          for (Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet())
          {
            Index index = mapEntry.getValue();
            index.setTrusted(null, trusted);
          }
        }
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
            if (subIndexes != null)
            {
              for (Index subIndex : subIndexes)
              {
                subIndex.setTrusted(null, trusted);
              }
            }
          }
        }
      }
      catch (DatabaseException ex)
      {
        Message message =
            NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage());
        throw new JebException(message);
      }
    }

    private void phaseOne() throws DatabaseException, InterruptedException,
        ExecutionException
    {
      initializeIndexBuffers();
      RebuildFirstPhaseProgressTask progressTask =
          new RebuildFirstPhaseProgressTask();
      Timer timer = new Timer();
      timer.scheduleAtFixedRate(progressTask, TIMER_INTERVAL, TIMER_INTERVAL);
      scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
      bufferSortService = Executors.newFixedThreadPool(threadCount);
      ExecutorService rebuildIndexService =
          Executors.newFixedThreadPool(threadCount);
      List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(this);
      }
      List<Future<Void>> results = rebuildIndexService.invokeAll(tasks);
      for (Future<Void> result : results)
      {
        if (!result.isDone())
        {
          result.get();
        }
      }
      stopScratchFileWriters();
      for (Future<?> result : scratchFileWriterFutures)
      {
        if (!result.isDone())
        {
          result.get();
        }
      }

      // Try to clear as much memory as possible.
      rebuildIndexService.shutdown();
      rebuildIndexService.awaitTermination(30, TimeUnit.SECONDS);
      bufferSortService.shutdown();
      bufferSortService.awaitTermination(30, TimeUnit.SECONDS);
      scratchFileWriterService.shutdown();
      scratchFileWriterService.awaitTermination(30, TimeUnit.SECONDS);
      timer.cancel();

      tasks.clear();
      results.clear();
      scratchFileWriterList.clear();
      scratchFileWriterFutures.clear();
      indexKeyQueMap.clear();
      freeBufferQueue.clear();
    }

    private void phaseTwo() throws InterruptedException, ExecutionException
    {
      SecondPhaseProgressTask progressTask =
          new SecondPhaseProgressTask(entriesProcessed.get());
      Timer timer2 = new Timer();
      timer2.scheduleAtFixedRate(progressTask, TIMER_INTERVAL, TIMER_INTERVAL);
      processIndexFiles();
      timer2.cancel();
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
      int indexCount = 0;
      List<String> rebuildList = rebuildConfig.getRebuildList();
      if (!rebuildList.isEmpty())
      {
        for (String index : rebuildList)
        {
          String lowerName = index.toLowerCase();
          if (lowerName.equals("dn2id"))
          {
            indexCount += 3;
          }
          else if (lowerName.equals("dn2uri"))
          {
            indexCount++;
          }
          else if (lowerName.startsWith("vlv."))
          {
            if (lowerName.length() < 5)
            {
              Message msg = ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName);
              throw new JebException(msg);
            }
            indexCount++;
          }
          else if (lowerName.equals("id2subtree")
              || lowerName.equals("id2children"))
          {
            Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
            throw new InitializationException(msg);
          }
          else
          {
            String[] attrIndexParts = lowerName.split("\\.");
            if ((attrIndexParts.length <= 0) || (attrIndexParts.length > 3))
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new InitializationException(msg);
            }
            AttributeType attrType =
                DirectoryServer.getAttributeType(attrIndexParts[0]);
            if (attrType == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new InitializationException(msg);
            }
            if (attrIndexParts.length != 1)
            {
              if (attrIndexParts.length == 2)
              {
                if (attrIndexParts[1].equals("presence"))
                {
                  indexCount++;
                }
                else if (attrIndexParts[1].equals("equality"))
                {
                  indexCount++;
                }
                else if (attrIndexParts[1].equals("substring"))
                {
                  indexCount++;
                }
                else if (attrIndexParts[1].equals("ordering"))
                {
                  indexCount++;
                }
                else if (attrIndexParts[1].equals("approximate"))
                {
                  indexCount++;
                }
                else
                {
                  Message msg =
                      ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                  throw new InitializationException(msg);
                }
              }
              else
              {
                boolean found = false;
                String s = attrIndexParts[1] + "." + attrIndexParts[2];
                for (String idx : cfg.listLocalDBIndexes())
                {
                  LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
                  if (indexCfg.getIndexType().contains(
                      LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
                  {
                    Set<String> extensibleRules =
                        indexCfg.getIndexExtensibleMatchingRule();
                    for (String exRule : extensibleRules)
                    {
                      if (exRule.equalsIgnoreCase(s))
                      {
                        found = true;
                        break;
                      }
                    }
                  }
                  if (found)
                  {
                    break;
                  }
                }
                if (!found)
                {
                  Message msg =
                      ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                  throw new InitializationException(msg);
                }
                indexCount++;
              }
            }
            else
            {
              boolean found = false;
              for (final String idx : cfg.listLocalDBIndexes())
              {
                if (!idx.equalsIgnoreCase(index))
                {
                  continue;
                }
                found = true;
                LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.EQUALITY))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.ORDERING))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.PRESENCE))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.SUBSTRING))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().contains(
                    LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
                {
                  Set<String> extensibleRules =
                      indexCfg.getIndexExtensibleMatchingRule();
                  boolean shared = false;
                  for (final String exRule : extensibleRules)
                  {
                    if (exRule.endsWith(".sub"))
                    {
                      indexCount++;
                    }
                    else
                    {
                      if (!shared)
                      {
                        shared = true;
                        indexCount++;
                      }
                    }
                  }
                }
              }
              if (!found)
              {
                Message msg =
                    ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                throw new InitializationException(msg);
              }
            }
          }
        }
      }
      return indexCount;
    }

    private void processEntry(Entry entry, EntryID entryID)
        throws DatabaseException, DirectoryException, JebException,
        InterruptedException
    {
      if (dn2id != null)
      {
        processDN2ID(suffix, entry.getDN(), entryID);
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
          Collection<Index> indexes = mapEntry.getValue();
          for (Index index : indexes)
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
          ImportIndexType indexType = key.getIndexType();
          Index index = mapEntry.getValue();
          if (indexType == ImportIndexType.SUBSTRING)
          {
            processAttribute(index, entry, entryID, new IndexKey(attrType,
                ImportIndexType.SUBSTRING, index.getIndexEntryLimit()));
          }
          else
          {
            processAttribute(index, entry, entryID, new IndexKey(attrType,
                indexType, index.getIndexEntryLimit()));
          }
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
    public long getTotEntries()
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
      Message msg =
          ERR_REBUILD_INDEX_LACK_DISK.get(monitor.getDirectory().getPath(),
              monitor.getFreeSpace(), monitor.getLowThreshold());
      logError(msg);
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
    private long previousProcessed = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * The environment statistics at the time of the previous report.
     */
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
      long entriesProcessed = rebuildManager.getEntriesProcess();
      long deltaCount = (entriesProcessed - previousProcessed);
      float rate = 1000f * deltaCount / deltaTime;
      float completed = 0;
      if (rebuildManager.getTotEntries() > 0)
      {
        completed = 100f * entriesProcessed / rebuildManager.getTotEntries();
      }
      Message message =
          NOTE_JEB_REBUILD_PROGRESS_REPORT.get(completed, entriesProcessed,
              rebuildManager.getTotEntries(), rate);
      logError(message);
      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / MB;
        EnvironmentStats envStats =
            rootContainer.getEnvironmentStats(new StatsConfig());
        long nCacheMiss =
            envStats.getNCacheMiss() - prevEnvStats.getNCacheMiss();

        float cacheMissRate = 0;
        if (deltaCount > 0)
        {
          cacheMissRate = nCacheMiss / (float) deltaCount;
        }
        message =
            NOTE_JEB_REBUILD_CACHE_AND_MEMORY_REPORT.get(freeMemory,
                cacheMissRate);
        logError(message);
        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {

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
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * The environment statistics at the time of the previous report.
     */
    private EnvironmentStats previousStats;

    // Determines if eviction has been detected.
    private boolean evicting = false;

    // Entry count when eviction was detected.
    private long evictionEntryCount = 0;

    /**
     * Create a new import progress task.
     */
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

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run()
    {
      long latestCount = reader.getEntriesRead() + 0;
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      Message message;
      if (deltaTime == 0)
      {
        return;
      }
      long entriesRead = reader.getEntriesRead();
      long entriesIgnored = reader.getEntriesIgnored();
      long entriesRejected = reader.getEntriesRejected();
      float rate = 1000f * deltaCount / deltaTime;
      message =
          NOTE_JEB_IMPORT_PROGRESS_REPORT.get(entriesRead, entriesIgnored,
              entriesRejected, 0, rate);
      logError(message);
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
        message =
            NOTE_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(freeMemory,
                cacheMissRate);
        logError(message);
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
            message =
                NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED.get(evictionEntryCount);
            logError(message);
          }
          message =
              NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS.get(evictPasses,
                  evictNodes, evictBinsStrip);
          logError(message);
        }
        if (cleanerRuns != 0)
        {
          message =
              NOTE_JEB_IMPORT_LDIF_CLEANER_STATS.get(cleanerRuns,
                  cleanerDeletions, cleanerEntriesRead, cleanerINCleaned);
          logError(message);
        }
        if (checkPoints > 1)
        {
          message = NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS.get(checkPoints);
          logError(message);
        }
        previousStats = environmentStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }
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
    /**
     * The number of entries that had been read at the time of the previous
     * progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * The environment statistics at the time of the previous report.
     */
    private EnvironmentStats previousStats;

    // Determines if eviction has been detected.
    private boolean evicting = false;

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

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run()
    {
      long deltaCount = (latestCount - previousCount);
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;
      Message message;
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
        message =
            NOTE_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(freeMemory,
                cacheMissRate);
        logError(message);
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
          message =
              NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS.get(evictPasses,
                  evictNodes, evictBinsStrip);
          logError(message);
        }
        if (cleanerRuns != 0)
        {
          message =
              NOTE_JEB_IMPORT_LDIF_CLEANER_STATS.get(cleanerRuns,
                  cleanerDeletions, cleanerEntriesRead, cleanerINCleaned);
          logError(message);
        }
        if (checkPoints > 1)
        {
          message = NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS.get(checkPoints);
          logError(message);
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
  public enum ImportIndexType
  {
    /**
     * The DN index type.
     **/
    DN,

    /**
     * The equality index type.
     **/
    EQUALITY,

    /**
     * The presence index type.
     **/
    PRESENCE,

    /**
     * The sub-string index type.
     **/
    SUBSTRING,

    /**
     * The ordering index type.
     **/
    ORDERING,

    /**
     * The approximate index type.
     **/
    APPROXIMATE,

    /**
     * The extensible sub-string index type.
     **/
    EX_SUBSTRING,

    /**
     * The extensible shared index type.
     **/
    EX_SHARED,

    /**
     * The vlv index type.
     */
    VLV
  }

  /**
   * This class is used as an index key for hash maps that need to process
   * multiple suffix index elements into a single queue and/or maps based on
   * both attribute type and index type (ie., cn.equality, sn.equality,...).
   */
  public class IndexKey
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
    IndexKey(AttributeType attributeType, ImportIndexType indexType,
        int entryLimit)
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
    public AttributeType getAttributeType()
    {
      return attributeType;
    }

    /**
     * Return the index type.
     *
     * @return The index type.
     */
    public ImportIndexType getIndexType()
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
    public String getName()
    {
      return attributeType.getPrimaryName() + "."
          + StaticUtils.toLowerCase(indexType.name());
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
    public boolean contains(DN dn) throws DatabaseException;
  }

  /**
   * Temporary environment used to check DN's when DN validation is performed
   * during phase one processing. It is deleted after phase one processing.
   */

  public final class TmpEnv implements DNCache
  {
    private String envPath;
    private Environment environment;
    private static final String DB_NAME = "dn_cache";
    private Database dnCache;

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
    public TmpEnv(File envPath) throws DatabaseException
    {
      EnvironmentConfig envConfig = new EnvironmentConfig();
      envConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "true");
      envConfig.setReadOnly(false);
      envConfig.setAllowCreate(true);
      envConfig.setTransactional(false);
      envConfig.setConfigParam(EnvironmentConfig.ENV_IS_LOCKING, "true");
      envConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false");
      envConfig.setConfigParam(EnvironmentConfig.EVICTOR_LRU_ONLY, "false");
      envConfig.setConfigParam(EnvironmentConfig.EVICTOR_NODES_PER_SCAN, "128");
      envConfig.setConfigParam(EnvironmentConfig.MAX_MEMORY, Long
          .toString(tmpEnvCacheSize));
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

    //Hash the DN bytes. Uses the FNV-1a hash.
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
    public void shutdown() throws JebException
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
    public boolean insert(DN dn, DatabaseEntry val, DatabaseEntry key)
        throws JebException
    {
      byte[] dnBytes = StaticUtils.getBytes(dn.toNormalizedString());
      int len = PackedInteger.getWriteIntLength(dnBytes.length);
      byte[] dataBytes = new byte[dnBytes.length + len];
      int pos = PackedInteger.writeInt(dataBytes, 0, dnBytes.length);
      System.arraycopy(dnBytes, 0, dataBytes, pos, dnBytes.length);
      val.setData(dataBytes);
      key.setData(hashCode(dnBytes));
      return insert(key, val, dnBytes);
    }

    private boolean insert(DatabaseEntry key, DatabaseEntry val, byte[] dnBytes)
        throws JebException
    {
      boolean inserted = true;
      Cursor cursor = null;
      try
      {
        cursor = dnCache.openCursor(null, CursorConfig.DEFAULT);
        OperationStatus status = cursor.putNoOverwrite(key, val);
        if (status == OperationStatus.KEYEXIST)
        {
          DatabaseEntry dns = new DatabaseEntry();
          inserted = false;
          status = cursor.getSearchKey(key, dns, LockMode.RMW);
          if (status == OperationStatus.NOTFOUND)
          {
            Message message =
                Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                    "Search DN cache failed.");
            throw new JebException(message);
          }
          if (!isDNMatched(dns, dnBytes))
          {
            addDN(dns, cursor, dnBytes);
            inserted = true;
          }
        }
      }
      finally
      {
        if (cursor != null)
        {
          cursor.close();
        }
      }
      return inserted;
    }

    //Add the DN to the DNs as because of a hash collision.
    private void addDN(DatabaseEntry val, Cursor cursor, byte[] dnBytes)
        throws JebException
    {
      byte[] bytes = val.getData();
      int pLen = PackedInteger.getWriteIntLength(dnBytes.length);
      int totLen = bytes.length + (pLen + dnBytes.length);
      byte[] newRec = new byte[totLen];
      System.arraycopy(bytes, 0, newRec, 0, bytes.length);
      int pos = bytes.length;
      pos = PackedInteger.writeInt(newRec, pos, dnBytes.length);
      System.arraycopy(dnBytes, 0, newRec, pos, dnBytes.length);
      DatabaseEntry newVal = new DatabaseEntry(newRec);
      OperationStatus status = cursor.putCurrent(newVal);
      if (status != OperationStatus.SUCCESS)
      {
        Message message =
            Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                "Add of DN to DN cache failed.");
        throw new JebException(message);
      }
    }

    //Return true if the specified DN is in the DNs saved as a result of hash
    //collisions.
    private boolean isDNMatched(DatabaseEntry dns, byte[] dnBytes)
    {
      int pos = 0;
      byte[] bytes = dns.getData();
      while (pos < dns.getData().length)
      {
        int pLen = PackedInteger.getReadIntLength(bytes, pos);
        int len = PackedInteger.readInt(bytes, pos);
        if (indexComparator.compare(bytes, pos + pLen, len, dnBytes,
            dnBytes.length) == 0)
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
      boolean dnExists = false;
      Cursor cursor = null;
      DatabaseEntry key = new DatabaseEntry();
      byte[] dnBytes = StaticUtils.getBytes(dn.toNormalizedString());
      key.setData(hashCode(dnBytes));
      try
      {
        cursor = dnCache.openCursor(null, CursorConfig.DEFAULT);
        DatabaseEntry dns = new DatabaseEntry();
        OperationStatus status =
            cursor.getSearchKey(key, dns, LockMode.DEFAULT);
        if (status == OperationStatus.SUCCESS)
        {
          dnExists = isDNMatched(dns, dnBytes);
        }
      }
      finally
      {
        if (cursor != null)
        {
          cursor.close();
        }
      }
      return dnExists;
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
    public EnvironmentStats getEnvironmentStats(StatsConfig statsConfig)
        throws DatabaseException
    {
      return environment.getStats(statsConfig);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void diskLowThresholdReached(DiskSpaceMonitor monitor)
  {
    diskFullThresholdReached(monitor);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void diskFullThresholdReached(DiskSpaceMonitor monitor)
  {
    isCanceled = true;
    Message msg;
    if (!isPhaseOneDone)
    {
      msg =
          ERR_IMPORT_LDIF_LACK_DISK_PHASE_ONE.get(monitor.getDirectory()
              .getPath(), monitor.getFreeSpace(), monitor.getLowThreshold());
    }
    else
    {
      msg =
          ERR_IMPORT_LDIF_LACK_DISK_PHASE_TWO.get(monitor.getDirectory()
              .getPath(), monitor.getFreeSpace(), monitor.getLowThreshold());
    }
    logError(msg);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void diskSpaceRestored(DiskSpaceMonitor monitor)
  {
    // Do nothing.
  }
}
