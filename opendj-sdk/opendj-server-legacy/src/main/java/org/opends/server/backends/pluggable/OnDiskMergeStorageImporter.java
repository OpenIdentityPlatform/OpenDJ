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
import static org.opends.server.backends.pluggable.DnKeyFormat.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PersistitBackendCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.backends.persistit.PersistItStorage;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.ImportLDIFReader.EntryInformation;
import org.opends.server.backends.pluggable.OnDiskMergeBufferImporter.DNCache;
import org.opends.server.backends.pluggable.OnDiskMergeBufferImporter.IndexKey;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
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
final class OnDiskMergeStorageImporter
{
  /**
   * Shim that allows properly constructing an {@link OnDiskMergeStorageImporter} without polluting
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
        return new OnDiskMergeStorageImporter(rootContainer, importConfig, backendCfg, serverContext).processImport();
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
  /** Small heap threshold used to give more memory to JVM to attempt OOM errors. */
  private static final int SMALL_HEAP_SIZE = 256 * MB;

  /** Root container. */
  private final RootContainer rootContainer;
  /** Import configuration. */
  private final LDIFImportConfig importCfg;
  private final ServerContext serverContext;

  /** LDIF reader. */
  private ImportLDIFReader reader;
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

  /** Map of DNs to Suffix objects. */
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<>();

  /** Set to true if the backend was cleared. */
  private final boolean clearedBackend;

  /** Used to shutdown import if an error occurs in phase one. */
  private volatile boolean isCanceled;

  /** Number of phase one buffers. */
  private int phaseOneBufferCount;

  private OnDiskMergeStorageImporter(RootContainer rootContainer, LDIFImportConfig importCfg,
      PluggableBackendCfg backendCfg, ServerContext serverContext)
      throws InitializationException, ConfigException, StorageRuntimeException
  {
    this.rootContainer = rootContainer;
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
  private static boolean mustClearBackend(LDIFImportConfig importCfg, PluggableBackendCfg backendCfg)
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

    // We need caching when doing DN validation
    if (validateDNs)
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

    availableMemory = totalAvailableMemory * importMemPct / 100;
  }

  private void initializeSuffixes(WriteableTransaction txn) throws ConfigException, DirectoryException
  {
    for (EntryContainer ec : rootContainer.getEntryContainers())
    {
      Suffix suffix = getSuffix(txn, ec);
      if (suffix != null)
      {
        dnSuffixMap.put(ec.getBaseDN(), suffix);
      }
    }
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

      if (isCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }

      final long phaseTwoTime = System.currentTimeMillis();
      importPhaseTwo();
      if (isCanceled)
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
    final ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new FirstPhaseProgressTask());
    final ExecutorService execService = Executors.newFixedThreadPool(threadCount);

    final Storage storage = rootContainer.getStorage();
    execService.submit(new MigrateExistingTask(storage)).get();

    final List<Callable<Void>> tasks = new ArrayList<>(threadCount);
    if (!importCfg.appendToExistingData() || !importCfg.replaceExistingEntries())
    {
      for (int i = 0; i < threadCount; i++)
      {
        tasks.add(new ImportTask(storage));
      }
    }
    execService.invokeAll(tasks);
    tasks.clear();

    execService.submit(new MigrateExcludedTask(storage)).get();

    shutdownAll(timerService, execService);
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

  private void importPhaseTwo() throws Exception
  {
    ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new SecondPhaseProgressTask());
    try
    {
      // TODO JNR
    }
    finally
    {
      shutdownAll(timerService);
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
                    && ByteSequence.COMPARATOR.compare(key, end) < 0
                    && !importCfg.isCancelled()
                    && !isCanceled)
                {
                  EntryID id = new EntryID(cursor.getValue());
                  Entry entry = entryContainer.getID2Entry().get(txn, id);
                  processEntry(txn, entry, rootContainer.getNextEntryID(), suffix);
                  migratedCount++;
                  success = cursor.next();
                }
              }
            }
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
                && !importCfg.isCancelled()
                && !isCanceled)
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

  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private class ImportTask implements Callable<Void>
  {
    private final Storage storage;

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
          if (importCfg.isCancelled() || isCanceled)
          {
            return;
          }
          processEntry(txn, entryInfo.getEntry(), entryInfo.getEntryID(), entryInfo.getSuffix());
        }
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
      if (validateDNs && !dnSanityCheck(txn, entry, suffix))
      {
        suffix.removePending(entryDN);
        return;
      }
      suffix.removePending(entryDN);
      processDN2ID(suffix, entryDN, entryID);
      processDN2URI(suffix, entry);
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
    boolean dnSanityCheck(WriteableTransaction txn, Entry entry, Suffix suffix)
        throws StorageRuntimeException, InterruptedException
    {
      //Perform parent checking.
      DN entryDN = entry.getName();
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      if (parentDN != null && !suffix.isParentProcessed(txn, parentDN, dnCache, clearedBackend))
      {
        reader.rejectEntry(entry, ERR_IMPORT_PARENT_NOT_FOUND.get(parentDN));
        return false;
      }
      if (!insert(txn, entryDN, suffix, dnCache))
      {
        reader.rejectEntry(entry, WARN_IMPORT_ENTRY_EXISTS.get());
        return false;
      }
      return true;
    }

    private boolean insert(WriteableTransaction txn, DN entryDN, Suffix suffix, DNCache dnCache)
    {
      //If the backend was not cleared, then first check dn2id
      //for DNs that might not exist in the DN cache.
      if (!clearedBackend && suffix.getDN2ID().get(txn, entryDN) != null)
      {
        return false;
      }
      return dnCache.insert(entryDN);
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
      for (ByteString key : index.indexEntry(entry))
      {
        processKey(index, key, entryID, indexKey);
      }
    }

    final int processKey(Tree tree, ByteString key, EntryID entryID, IndexKey indexKey) throws InterruptedException
    {
      // TODO JNR implement
      return -1;
    }

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
    {
      // TODO JNR implement
    }

    private void processDN2URI(Suffix suffix, Entry entry)
    {
      // TODO JNR implement
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
      // TODO JNR
    }
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
        storage.open();
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
    public boolean insert(DN dn) throws StorageRuntimeException
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
}
