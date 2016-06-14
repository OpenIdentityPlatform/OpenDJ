/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static java.nio.channels.FileChannel.*;
import static java.nio.file.StandardOpenOption.*;
import static org.forgerock.util.Utils.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.forgerock.util.promise.PromiseImpl;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.forgerock.opendj.server.config.server.BackendIndexCfg;
import org.forgerock.opendj.server.config.server.PluggableBackendCfg;
import org.opends.server.api.CompressedSchema;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.CursorTransformer.SequentialCursorAdapter;
import org.opends.server.backends.pluggable.DN2ID.TreeVisitor;
import org.opends.server.backends.pluggable.ImportLDIFReader.EntryInformation;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk.InMemorySortedChunk;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.util.Platform;

import com.forgerock.opendj.util.OperatingSystem;
import com.forgerock.opendj.util.PackedLong;

// @Checkstyle:ignore
import sun.misc.Unsafe;

/**
 * Imports LDIF data contained in files into the database. Because of the B-Tree structure used in backend, import is
 * faster when records are inserted in ascending order. This prevents node locking/re-writing due to B-Tree inner nodes
 * split. This is why import is performed in two phases: the first phase encode and sort all records while the second
 * phase copy the sorted records into the database. Entries are read from an LDIF file by the {@link ImportLDIFReader}.
 * Then, each entry are optionally validated and finally imported into a {@link Chunk} by the {@link EntryContainer}
 * using a {@link PhaseOneWriteableTransaction}. Once all entries have been processed,
 * {@link PhaseOneWriteableTransaction#getChunks()} get all the chunks which will be copied into the database
 * concurrently using tasks created by the {@link ImporterTaskFactory}.
 */
final class OnDiskMergeImporter
{
  private static final String DEFAULT_TMP_DIR = "import-tmp";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Shim that allows properly constructing an {@link OnDiskMergeImporter} without polluting {@link ImportStrategy} and
   * {@link RootContainer} with this importer inner workings.
   */
  static class StrategyImpl implements ImportStrategy
  {
    private static final String PHASE1_REBUILDER_THREAD_NAME = "PHASE1-REBUILDER-%d";

    private static final String PHASE2_REBUILDER_THREAD_NAME = "PHASE2-REBUILDER-%d";

    private static final String PHASE1_IMPORTER_THREAD_NAME = "PHASE1-IMPORTER-%d";

    private static final String PHASE2_IMPORTER_THREAD_NAME = "PHASE2-IMPORTER-%d";

    private static final String SORTER_THREAD_NAME = "PHASE1-SORTER-%d";

    /** Small heap threshold used to give more memory to JVM to attempt OOM errors. */
    private static final int SMALL_HEAP_SIZE = 256 * MB;

    private final ServerContext serverContext;
    private final RootContainer rootContainer;
    private final PluggableBackendCfg backendCfg;

    StrategyImpl(ServerContext serverContext, RootContainer rootContainer, PluggableBackendCfg backendCfg)
    {
      this.serverContext = serverContext;
      this.rootContainer = rootContainer;
      this.backendCfg = backendCfg;
    }

    @Override
    public LDIFImportResult importLDIF(LDIFImportConfig importConfig) throws Exception
    {
      final int threadCount =
          importConfig.getThreadCount() == 0 ? Runtime.getRuntime().availableProcessors()
              : importConfig.getThreadCount();
      final int indexCount = getIndexCount();

      final int nbBuffer = threadCount * indexCount * 2;
      final int bufferSize;
      if (BufferPool.SUPPORTS_OFF_HEAP && importConfig.getOffHeapSize() > 0)
      {
        final long offHeapSize = importConfig.getOffHeapSize();
        bufferSize = (int) ((offHeapSize * MB) / nbBuffer);
        if (bufferSize < MIN_BUFFER_SIZE)
        {
          // Not enough memory.
          throw new InitializationException(ERR_IMPORT_LDIF_LACK_MEM.get(offHeapSize * MB, nbBuffer * MIN_BUFFER_SIZE));
        }
        logger.info(NOTE_IMPORT_LDIF_OFFHEAP_MEM_BUF_INFO, DB_CACHE_SIZE, offHeapSize, nbBuffer, bufferSize / KB);
      }
      else
      {
        bufferSize = computeBufferSize(nbBuffer);
        logger.info(NOTE_IMPORT_LDIF_DB_MEM_BUF_INFO, DB_CACHE_SIZE, bufferSize);
      }
      logger.info(NOTE_IMPORT_STARTING, DirectoryServer.getVersionString(), BUILD_ID, REVISION);
      logger.info(NOTE_IMPORT_THREAD_COUNT, threadCount);

      final long startTime = System.currentTimeMillis();
      final OnDiskMergeImporter importer;
      final ExecutorService sorter = Executors.newFixedThreadPool(
          Runtime.getRuntime().availableProcessors(),
          newThreadFactory(null, SORTER_THREAD_NAME, true));
      final LDIFReaderSource source =
          new LDIFReaderSource(rootContainer, importConfig, PHASE1_IMPORTER_THREAD_NAME, threadCount);
      final File tempDir = prepareTempDir(backendCfg, importConfig.getTmpDirectory());
      try (final Importer dbStorage = rootContainer.getStorage().startImport();
           final BufferPool bufferPool = new BufferPool(nbBuffer, bufferSize))
      {
        final Collection<EntryContainer> entryContainers = rootContainer.getEntryContainers();
        final AbstractTwoPhaseImportStrategy importStrategy = importConfig.getSkipDNValidation()
            ? new SortAndImportWithoutDNValidation(entryContainers, dbStorage, tempDir, bufferPool, sorter)
            : new SortAndImportWithDNValidation(entryContainers, dbStorage, tempDir, bufferPool, sorter);

        importer = new OnDiskMergeImporter(PHASE2_IMPORTER_THREAD_NAME, importStrategy);
        importer.doImport(source);
      }
      finally
      {
        sorter.shutdownNow();
        if (OperatingSystem.isWindows())
        {
          // Try to force the JVM to close mmap()ed file so that they can be deleted.
          // (see http://bugs.java.com/view_bug.do?bug_id=4715154)
          System.gc();
        }
        recursiveDelete(tempDir);
      }
      logger.info(NOTE_IMPORT_PHASE_STATS, importer.getTotalTimeInMillis() / 1000, importer.getPhaseOneTimeInMillis()
          / 1000, importer.getPhaseTwoTimeInMillis() / 1000);

      final long importTime = System.currentTimeMillis() - startTime;
      float rate = 0;
      if (importTime > 0)
      {
        rate = 1000f * source.getEntriesRead() / importTime;
      }
      logger.info(NOTE_IMPORT_FINAL_STATUS, source.getEntriesRead(), importer.getImportedCount(), source
          .getEntriesIgnored(), source.getEntriesRejected(), 0, importTime / 1000, rate);

      return new LDIFImportResult(source.getEntriesRead(), source.getEntriesRejected(), source
          .getEntriesIgnored());
    }

    private int getIndexCount() throws ConfigException
    {
      int indexCount = 2; // dn2id, dn2uri
      for (String indexName : backendCfg.listBackendIndexes())
      {
        final BackendIndexCfg index = backendCfg.getBackendIndex(indexName);
        final SortedSet<IndexType> types = index.getIndexType();
        if (types.contains(IndexType.EXTENSIBLE))
        {
          indexCount += types.size() - 1 + index.getIndexExtensibleMatchingRule().size();
        }
        else
        {
          indexCount += types.size();
        }
      }
      indexCount += backendCfg.listBackendVLVIndexes().length;
      return indexCount;
    }

    @Override
    public void rebuildIndex(final RebuildConfig rebuildConfig) throws Exception
    {
      final EntryContainer entryContainer = rootContainer.getEntryContainer(rebuildConfig.getBaseDN());
      final long totalEntries = rootContainer.getStorage().read(new ReadOperation<Long>()
      {
        @Override
        public Long run(ReadableTransaction txn) throws Exception
        {
          return entryContainer.getID2Entry().getRecordCount(txn);
        }
      });

      final Set<String> indexesToRebuild = selectIndexesToRebuild(entryContainer, rebuildConfig, totalEntries);
      if (rebuildConfig.isClearDegradedState())
      {
        clearDegradedState(entryContainer, indexesToRebuild);
        logger.info(NOTE_REBUILD_CLEARDEGRADEDSTATE_FINAL_STATUS, rebuildConfig.getRebuildList());
      }
      else
      {
        rebuildIndex(entryContainer, rebuildConfig.getTmpDirectory(), indexesToRebuild, totalEntries);
      }
    }

    private void clearDegradedState(final EntryContainer entryContainer, final Set<String> indexes) throws Exception
    {
      rootContainer.getStorage().write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          visitIndexes(entryContainer, visitOnlyIndexes(indexes, setTrust(true, txn)));
        }
      });
    }

    private void rebuildIndex(EntryContainer entryContainer, String tmpDirectory, Set<String> indexesToRebuild,
        long totalEntries) throws Exception
    {
      if (indexesToRebuild.isEmpty())
      {
        logger.info(NOTE_REBUILD_NOTHING_TO_REBUILD);
        return;
      }
      rootContainer.getStorage().close();
      final int threadCount = Runtime.getRuntime().availableProcessors();
      final int nbBuffer = 2 * indexesToRebuild.size() * threadCount;
      final int bufferSize;
      if (BufferPool.SUPPORTS_OFF_HEAP)
      {
        bufferSize = MAX_BUFFER_SIZE;
        logger.info(NOTE_IMPORT_LDIF_OFFHEAP_MEM_BUF_INFO, 
            DB_CACHE_SIZE, (((long) bufferSize) * nbBuffer) / MB, nbBuffer, bufferSize / KB);
      }
      else
      {
        bufferSize = computeBufferSize(nbBuffer);
        logger.info(NOTE_IMPORT_LDIF_DB_MEM_BUF_INFO, DB_CACHE_SIZE, bufferSize);
      }

      final ExecutorService sorter = Executors.newFixedThreadPool(
          Runtime.getRuntime().availableProcessors(),
          newThreadFactory(null, SORTER_THREAD_NAME, true));

      final OnDiskMergeImporter importer;
      final File tempDir = prepareTempDir(backendCfg, tmpDirectory);
      try (final Importer dbStorage = rootContainer.getStorage().startImport();
           final BufferPool bufferPool = new BufferPool(nbBuffer, bufferSize))
      {
        final AbstractTwoPhaseImportStrategy strategy = new RebuildIndexStrategy(
            rootContainer.getEntryContainers(), dbStorage, tempDir, bufferPool, sorter, indexesToRebuild);

        importer = new OnDiskMergeImporter(PHASE2_REBUILDER_THREAD_NAME, strategy);
        importer.doImport(
            new ID2EntrySource(entryContainer, dbStorage, PHASE1_REBUILDER_THREAD_NAME, threadCount, totalEntries));
      }
      finally
      {
        sorter.shutdown();
        recursiveDelete(tempDir);
      }

      final long totalTime = importer.getTotalTimeInMillis();
      final float rate = totalTime > 0 ? 1000f * importer.getImportedCount() / totalTime : 0;
      logger.info(NOTE_REBUILD_FINAL_STATUS, importer.getImportedCount(), totalTime / 1000, rate);
    }

    private static final Set<String> selectIndexesToRebuild(EntryContainer entryContainer, RebuildConfig rebuildConfig,
        long totalEntries) throws InitializationException
    {
      final SelectIndexName selector = new SelectIndexName();
      switch (rebuildConfig.getRebuildMode())
      {
      case ALL:
        visitIndexes(entryContainer, selector);
        logger.info(NOTE_REBUILD_ALL_START, totalEntries);
        break;
      case DEGRADED:
        visitIndexes(entryContainer, visitOnlyDegraded(selector));
        logger.info(NOTE_REBUILD_DEGRADED_START, totalEntries);
        break;
      case USER_DEFINED:
        // User defined format is attributeType(.indexType)
        visitIndexes(entryContainer,
            visitOnlyIndexes(buildUserDefinedIndexNames(entryContainer, rebuildConfig.getRebuildList()), selector));
        if (!rebuildConfig.isClearDegradedState())
        {
          logger.info(NOTE_REBUILD_START, Utils.joinAsString(", ", rebuildConfig.getRebuildList()), totalEntries);
        }
        break;
      default:
        throw new UnsupportedOperationException("Unsupported rebuild mode " + rebuildConfig.getRebuildMode());
      }
      final Set<String> indexesToRebuild = selector.getSelectedIndexNames();
      if (indexesToRebuild.contains(SuffixContainer.DN2ID_INDEX_NAME))
      {
        // Always rebuild id2childrencount with dn2id.
        indexesToRebuild.add(SuffixContainer.ID2CHILDREN_COUNT_NAME);
      }
      return selector.getSelectedIndexNames();
    }

    /**
     * Translate attributeType(.indexType|matchingRuleOid) into attributeType.matchingRuleOid index name.
     *
     * @throws InitializationException
     *           if rebuildList contains an invalid/non-existing attribute/index name.
     */
    private static final Set<String> buildUserDefinedIndexNames(EntryContainer entryContainer,
        Collection<String> rebuildList) throws InitializationException
    {
      final Set<String> indexNames = new HashSet<>();
      for (String name : rebuildList)
      {
        final String parts[] = name.split("\\.");
        if (parts.length == 1)
        {
          // Add all indexes of this attribute
          // for example: "cn" or "sn"
          final AttributeIndex attrIndex = findAttributeIndex(entryContainer, parts[0]);
          for (Tree index : attrIndex.getNameToIndexes().values())
          {
            indexNames.add(index.getName().getIndexId());
          }
        }
        else if (parts.length == 2)
        {
          // First, assume the supplied name is a valid index name ...
          // for example: "cn.substring", "vlv.someVlvIndex", "cn.caseIgnoreMatch"
          // or "cn.caseIgnoreSubstringsMatch:6"
          final SelectIndexName selector = new SelectIndexName();
          visitIndexes(entryContainer, visitOnlyIndexes(Arrays.asList(name), selector));
          indexNames.addAll(selector.getSelectedIndexNames());
          if (selector.getSelectedIndexNames().isEmpty())
          {
            // ... if not, assume the supplied name identify an attributeType.indexType
            // for example: aliases like "cn.substring" could not be found by the previous step
            try
            {
              final AttributeIndex attrIndex = findAttributeIndex(entryContainer, parts[0]);
              indexNames.addAll(getIndexNames(IndexType.valueOf(parts[1].toUpperCase()), attrIndex));
            }
            catch (IllegalArgumentException e)
            {
              throw new InitializationException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(name), e);
            }
          }
        }
        else
        {
          throw new InitializationException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(name));
        }
      }
      return indexNames;
    }

    private static final AttributeIndex findAttributeIndex(EntryContainer entryContainer, String name)
        throws InitializationException
    {
      for (AttributeIndex index : entryContainer.getAttributeIndexes())
      {
        if (index.getAttributeType().hasNameOrOID(name.toLowerCase()))
        {
          return index;
        }
      }
      throw new InitializationException(ERR_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(name));
    }


    private static Collection<String> getIndexNames(IndexType indexType, AttributeIndex attrIndex)
    {
      if (indexType.equals(IndexType.PRESENCE))
      {
        if (!attrIndex.isIndexed(org.opends.server.types.IndexType.PRESENCE))
        {
          throw new IllegalArgumentException("No index found for type " + indexType);
        }
        return Collections.singletonList(IndexType.PRESENCE.toString());
      }
      final Set<String> indexNames = new HashSet<>();
      for (Indexer indexer : AttributeIndex.getMatchingRule(indexType, attrIndex.getAttributeType())
                                           .createIndexers(attrIndex.getIndexingOptions()))
      {
        final Tree indexTree = attrIndex.getNameToIndexes().get(indexer.getIndexID());
        if (indexTree == null)
        {
          throw new IllegalArgumentException("No index found for type " + indexType);
        }
        indexNames.add(indexTree.getName().getIndexId());
      }
      return indexNames;
    }

    private static File prepareTempDir(PluggableBackendCfg backendCfg, String tmpDirectory)
        throws InitializationException
    {
      final File tempDir =
          new File(getFileForPath(tmpDirectory != null ? tmpDirectory : DEFAULT_TMP_DIR), backendCfg.getBackendId());
      recursiveDelete(tempDir);
      if (!tempDir.exists() && !tempDir.mkdirs())
      {
        throw new InitializationException(ERR_IMPORT_CREATE_TMPDIR_ERROR.get(tempDir));
      }
      return tempDir;
    }

    private int computeBufferSize(int nbBuffer) throws InitializationException
    {
      final long availableMemory = calculateAvailableHeapMemoryForBuffers();
      logger.info(NOTE_IMPORT_LDIF_TOT_MEM_BUF, availableMemory, nbBuffer);

      final long minimumRequiredMemory = nbBuffer * MIN_BUFFER_SIZE + DB_CACHE_SIZE + REQUIRED_FREE_MEMORY;
      if (availableMemory < minimumRequiredMemory)
      {
        // Not enough memory.
        throw new InitializationException(ERR_IMPORT_LDIF_LACK_MEM.get(availableMemory, minimumRequiredMemory));
      }
      return Math.min((int) (availableMemory / nbBuffer), MAX_BUFFER_SIZE);
    }

    /**
     * Calculates the amount of available memory which can be used by this import, taking into account whether
     * the import is running offline or online as a task.
     */
    private long calculateAvailableHeapMemoryForBuffers()
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
      return (totalAvailableMemory * importMemPct / 100);
    }
  }

  /** Source of LDAP {@link Entry}s to process. */
  private interface Source
  {
    /** Process {@link Entry}s extracted from a {@link Source}. */
    interface EntryProcessor
    {
      void processEntry(EntryContainer container, EntryID entryID, Entry entry) throws Exception;
    }

    void processAllEntries(EntryProcessor processor) throws Exception;

    boolean isCancelled();
  }

  /** Extract LDAP {@link Entry}s from an LDIF file. */
  private static final class LDIFReaderSource implements Source
  {
    private static final String PHASE1_REPORTER_THREAD_NAME = "PHASE1-REPORTER-%d";

    private final Map<DN, EntryContainer> entryContainers;
    private final LDIFImportConfig importConfig;
    private final ImportLDIFReader reader;
    private final ExecutorService executor;
    private final int nbThreads;

    LDIFReaderSource(RootContainer rootContainer, LDIFImportConfig importConfig, String threadNameTemplate,
        int nbThreads) throws IOException
    {
      this.importConfig = importConfig;
      this.reader = new ImportLDIFReader(importConfig, rootContainer);
      this.entryContainers = new HashMap<>();
      for (EntryContainer container : rootContainer.getEntryContainers())
      {
        this.entryContainers.put(container.getBaseDN(), container);
      }
      this.nbThreads = nbThreads;
      this.executor = Executors.newFixedThreadPool(nbThreads, newThreadFactory(null, threadNameTemplate, true));
    }

    @Override
    public void processAllEntries(final EntryProcessor entryProcessor) throws Exception
    {
      final ScheduledExecutorService scheduler =
          Executors.newSingleThreadScheduledExecutor(newThreadFactory(null, PHASE1_REPORTER_THREAD_NAME, true));
      scheduler.scheduleAtFixedRate(new PhaseOneProgressReporter(), 10, 10, TimeUnit.SECONDS);
      final CompletionService<Void> completion = new ExecutorCompletionService<>(executor);
      try
      {
        for (int i = 0; i < nbThreads; i++)
        {
          completion.submit(new Callable<Void>()
          {
            @Override
            public Void call() throws Exception
            {
              checkThreadNotInterrupted();
              EntryInformation entryInfo;
              while ((entryInfo = reader.readEntry(entryContainers)) != null && !importConfig.isCancelled())
              {
                final EntryContainer entryContainer = entryInfo.getEntryContainer();
                final Entry entry = entryInfo.getEntry();
                final DN entryDN = entry.getName();
                final DN parentDN = entryContainer.getParentWithinBase(entryDN);

                if (parentDN != null)
                {
                  reader.waitIfPending(parentDN);
                }
                try
                {
                  entryProcessor.processEntry(entryContainer, entryInfo.getEntryID(), entry);
                }
                catch (DirectoryException e)
                {
                  reader.rejectEntry(entry, e.getMessageObject());
                }
                catch (Exception e)
                {
                  reader.rejectEntry(entry, ERR_EXECUTION_ERROR.get(e));
                }
                finally
                {
                  reader.removePending(entry.getName());
                }
                checkThreadNotInterrupted();
              }
              return null;
            }
          });
        }
        waitTasksTermination(completion, nbThreads);
      }
      finally
      {
        scheduler.shutdown();
        executor.shutdown();
      }
    }

    long getEntriesRead()
    {
      return reader.getEntriesRead();
    }

    long getEntriesIgnored()
    {
      return reader.getEntriesIgnored();
    }

    long getEntriesRejected()
    {
      return reader.getEntriesRejected();
    }

    @Override
    public boolean isCancelled()
    {
      return importConfig.isCancelled();
    }

    /** This class reports progress of first phase of import processing at fixed intervals. */
    private final class PhaseOneProgressReporter extends TimerTask
    {
      /** The number of entries that had been read at the time of the previous progress report. */
      private long previousCount;
      /** The time in milliseconds of the previous progress report. */
      private long previousTime;

      /** Create a new import progress task. */
      public PhaseOneProgressReporter()
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
  }

  /** Extract LDAP {@link Entry}s from an existing database. */
  private static final class ID2EntrySource implements Source
  {
    private static final String PHASE1_REPORTER_THREAD_NAME = "REPORTER-%d";

    private final EntryContainer entryContainer;
    private final CompressedSchema schema;
    private final Importer importer;
    private final ExecutorService executor;
    private final long nbTotalEntries;
    private final AtomicLong nbEntriesProcessed = new AtomicLong();
    private volatile boolean interrupted;

    ID2EntrySource(EntryContainer entryContainer, Importer importer, String threadNameTemplate, int nbThread,
        long nbTotalEntries)
    {
      this.nbTotalEntries = nbTotalEntries;
      this.entryContainer = entryContainer;
      this.importer = importer;
      this.schema = entryContainer.getRootContainer().getCompressedSchema();
      // by default (unfortunately) the ThreadPoolExecutor will throw an exception when queue is full.
      this.executor =
          new ThreadPoolExecutor(nbThread, nbThread, 0, TimeUnit.SECONDS,
              new ArrayBlockingQueue<Runnable>(nbThread * 2),
              newThreadFactory(null, threadNameTemplate, true),
              new RejectedExecutionHandler()
              {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
                {
                  // this will block if the queue is full
                  try
                  {
                    executor.getQueue().put(r);
                  }
                  catch (InterruptedException e)
                  {
                  }
                }
              });
    }

    @Override
    public void processAllEntries(final EntryProcessor entryProcessor) throws Exception
    {
      final ScheduledExecutorService scheduler =
          Executors.newSingleThreadScheduledExecutor(newThreadFactory(null, PHASE1_REPORTER_THREAD_NAME, true));
      scheduler.scheduleAtFixedRate(new PhaseOneProgressReporter(), 10, 10, TimeUnit.SECONDS);
      final PromiseImpl<Void, Exception> promise = PromiseImpl.create();
      final ID2Entry id2Entry = entryContainer.getID2Entry();
      try (final SequentialCursor<ByteString, ByteString> cursor = importer.openCursor(id2Entry.getName()))
      {
        while (cursor.next())
        {
          final ByteString key = cursor.getKey();
          final ByteString value = cursor.getValue();
          executor.submit(new Runnable()
          {
            @Override
            public void run()
            {
              try
              {
                entryProcessor.processEntry(entryContainer,
                    new EntryID(key), id2Entry.entryFromDatabase(value, schema));
                nbEntriesProcessed.incrementAndGet();
              }
              catch (Exception e)
              {
                interrupted = true;
                promise.handleException(e);
              }
            }
          });
        }
      }
      finally
      {
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        scheduler.shutdown();
      }

      // Forward exception if any
      if (promise.isDone())
      {
        promise.getOrThrow(0, TimeUnit.SECONDS);
      }
    }

    @Override
    public boolean isCancelled()
    {
      return interrupted;
    }

    /** This class reports progress of first phase of import processing at fixed intervals. */
    private final class PhaseOneProgressReporter extends TimerTask
    {
      /** The number of entries that had been read at the time of the previous progress report. */
      private long previousCount;
      /** The time in milliseconds of the previous progress report. */
      private long previousTime;

      /** Create a new import progress task. */
      public PhaseOneProgressReporter()
      {
        previousTime = System.currentTimeMillis();
      }

      /** The action to be performed by this timer task. */
      @Override
      public void run()
      {
        long entriesRead = nbEntriesProcessed.get();
        long deltaCount = entriesRead - previousCount;

        long latestTime = System.currentTimeMillis();
        long deltaTime = latestTime - previousTime;
        final float progressPercent = nbTotalEntries > 0 ? Math.round((100f * entriesRead) / nbTotalEntries) : 0;
        if (deltaTime == 0)
        {
          return;
        }
        float rate = 1000f * deltaCount / deltaTime;
        logger.info(NOTE_REBUILD_PROGRESS_REPORT, progressPercent, entriesRead, nbTotalEntries, rate);
        previousCount = entriesRead;
        previousTime = latestTime;
      }
    }
  }

  /** Max size of phase one buffer. */
  private static final int MAX_BUFFER_SIZE = 2 * MB;
  /** Min size of phase one buffer. */
  private static final int MIN_BUFFER_SIZE = 4 * KB;
  /** DB cache size to use during import. */
  private static final int DB_CACHE_SIZE = 32 * MB;
  /** Required free memory for this importer. */
  private static final int REQUIRED_FREE_MEMORY = 50 * MB;
  /** LDIF reader. */
  /** Map of DNs to Suffix objects. */
  private final AbstractTwoPhaseImportStrategy importStrategy;

  private final String phase2ThreadNameTemplate;
  private final AtomicLong importedCount = new AtomicLong();
  private long phaseOneTimeMs;
  private long phaseTwoTimeMs;

  private OnDiskMergeImporter(String phase2ThreadNameTemplate, AbstractTwoPhaseImportStrategy importStrategy)
  {
    this.phase2ThreadNameTemplate = phase2ThreadNameTemplate;
    this.importStrategy = importStrategy;
  }

  private void doImport(final Source source) throws Exception
  {
    final long phaseOneStartTime = System.currentTimeMillis();
    final PhaseOneWriteableTransaction transaction = new PhaseOneWriteableTransaction(importStrategy);
    importedCount.set(0);

    final ConcurrentMap<EntryContainer, CountDownLatch> importedContainers = new ConcurrentHashMap<>();

    // Start phase one
    source.processAllEntries(new Source.EntryProcessor()
    {
      @Override
      public void processEntry(EntryContainer container, EntryID entryID, Entry entry) throws DirectoryException,
          InterruptedException
      {
        CountDownLatch latch = importedContainers.get(container);
        if (latch == null)
        {
          final CountDownLatch newLatch = new CountDownLatch(1);
          if (importedContainers.putIfAbsent(container, newLatch) == null)
          {
            try
            {
              importStrategy.beforePhaseOne(container);
            }
            finally
            {
              newLatch.countDown();
            }
          }
          latch = importedContainers.get(container);
        }
        latch.await();

        importStrategy.validate(container, entryID, entry);
        container.importEntry(transaction, entryID, entry);
        importedCount.incrementAndGet();
      }
    });
    phaseOneTimeMs = System.currentTimeMillis() - phaseOneStartTime;

    if (source.isCancelled())
    {
      throw new InterruptedException("Import processing canceled.");
    }

    importStrategy.afterPhaseOne();

    // Start phase two
    final long phaseTwoStartTime = System.currentTimeMillis();
    try (final PhaseTwoProgressReporter progressReporter = new PhaseTwoProgressReporter())
    {
      final List<Callable<Void>> tasks = new ArrayList<>();
      final Set<String> importedBaseDNs = new HashSet<>();
      for (Map.Entry<TreeName, Chunk> treeChunk : transaction.getChunks().entrySet())
      {
        importedBaseDNs.add(treeChunk.getKey().getBaseDN());
        tasks.add(importStrategy.newPhaseTwoTask(treeChunk.getKey(), treeChunk.getValue(), progressReporter));
      }
      invokeParallel(phase2ThreadNameTemplate, tasks);
    }

    // Finish import
    for(EntryContainer entryContainer : importedContainers.keySet())
    {
      importStrategy.afterPhaseTwo(entryContainer);
    }
    phaseTwoTimeMs = System.currentTimeMillis() - phaseTwoStartTime;
  }

  public long getImportedCount()
  {
    return importedCount.get();
  }

  public long getPhaseOneTimeInMillis()
  {
    return phaseOneTimeMs;
  }

  public long getPhaseTwoTimeInMillis()
  {
    return phaseTwoTimeMs;
  }

  public long getTotalTimeInMillis()
  {
    return phaseOneTimeMs + phaseTwoTimeMs;
  }

  /** Create {@link Chunk} depending on the {@link TreeName}. */
  private interface ChunkFactory
  {
    Chunk newChunk(TreeName treeName) throws Exception;
  }

  /** Provides default behavior for two phases strategies. */
  private static abstract class AbstractTwoPhaseImportStrategy implements ChunkFactory
  {
    protected final Map<String, EntryContainer> entryContainers;
    protected final Executor sorter;
    protected final Importer importer;
    protected final BufferPool bufferPool;
    protected final File tempDir;

    AbstractTwoPhaseImportStrategy(Collection<EntryContainer> entryContainers, Importer importer, File tempDir,
        BufferPool bufferPool, Executor sorter)
    {
      this.entryContainers = new HashMap<>(entryContainers.size());
      for (EntryContainer container : entryContainers)
      {
        this.entryContainers.put(container.getTreePrefix(), container);
      }
      this.importer = importer;
      this.tempDir = tempDir;
      this.bufferPool = bufferPool;
      this.sorter = sorter;
    }

    abstract void validate(EntryContainer entryContainer, EntryID entryID, Entry entry) throws DirectoryException;

    void beforePhaseOne(EntryContainer entryContainer)
    {
      entryContainer.delete(asWriteableTransaction(importer));
      visitIndexes(entryContainer, setTrust(false, importer));
    }

    void afterPhaseOne()
    {
      closeSilently(bufferPool);
    }

    abstract Callable<Void> newPhaseTwoTask(TreeName treeName, Chunk source, PhaseTwoProgressReporter progressReporter);

    void afterPhaseTwo(EntryContainer entryContainer)
    {
      visitIndexes(entryContainer, setTrust(true, importer));
    }

    final Chunk newExternalSortChunk(TreeName treeName) throws Exception
    {
      return new ExternalSortChunk(tempDir, treeName.toString(), bufferPool,
          newPhaseOneCollector(entryContainers.get(treeName.getBaseDN()), treeName),
          newPhaseTwoCollector(entryContainers.get(treeName.getBaseDN()), treeName), sorter);
    }

    final Callable<Void> newChunkCopierTask(TreeName treeName, final Chunk source,
        PhaseTwoProgressReporter progressReporter)
    {
      return new ChunkCopierTask(progressReporter, source, treeName, importer);
    }

    final Callable<Void> newDN2IDImporterTask(TreeName treeName, final Chunk source,
        PhaseTwoProgressReporter progressReporter, boolean dn2idAlreadyImported)
    {
      final EntryContainer entryContainer = entryContainers.get(treeName.getBaseDN());
      final ID2ChildrenCount id2count = entryContainer.getID2ChildrenCount();

      return new DN2IDImporterTask(progressReporter, importer, tempDir, bufferPool, entryContainer.getDN2ID(), source,
          id2count, newPhaseTwoCollector(entryContainer, id2count.getName()), dn2idAlreadyImported);
    }

    final Callable<Void> newVLVIndexImporterTask(VLVIndex vlvIndex, final Chunk source,
        PhaseTwoProgressReporter progressReporter)
    {
      return new VLVIndexImporterTask(progressReporter, source, vlvIndex, importer);
    }

    static final Callable<Void> newFlushTask(final Chunk source)
    {
      return new Callable<Void>()
      {
        @Override
        public Void call() throws Exception
        {
          checkThreadNotInterrupted();
          try (final MeteredCursor<ByteString, ByteString> unusued = source.flip())
          {
            // force flush
          }
          return null;
        }
      };
    }
  }

  /**
   * No validation is performed, every {@link TreeName} (but id2entry) are imported into dedicated
   * {@link ExternalSortChunk} before being imported into the {@link Importer}. id2entry which is directly copied into
   * the database through {@link ImporterToChunkAdapter}.
   */
  private static final class SortAndImportWithoutDNValidation extends AbstractTwoPhaseImportStrategy
  {
    SortAndImportWithoutDNValidation(Collection<EntryContainer> entryContainers, Importer importer, File tempDir,
        BufferPool bufferPool, Executor sorter)
    {
      super(entryContainers, importer, tempDir, bufferPool, sorter);
    }

    @Override
    public void validate(EntryContainer entryContainer, EntryID entryID, Entry entry)
    {
      // No validation performed. All entries are considered valid.
    }

    @Override
    public Chunk newChunk(TreeName treeName) throws Exception
    {
      if (isID2Entry(treeName))
      {
        return new MostlyOrderedChunk(asChunk(treeName, importer));
      }
      return newExternalSortChunk(treeName);
    }

    @Override
    public Callable<Void> newPhaseTwoTask(TreeName treeName, final Chunk source,
        PhaseTwoProgressReporter progressReporter)
    {
      final EntryContainer entryContainer = entryContainers.get(treeName.getBaseDN());

      if (isID2Entry(treeName))
      {
        return newFlushTask(source);
      }
      else if (isDN2ID(treeName))
      {
        return newDN2IDImporterTask(treeName, source, progressReporter, false);
      }
      else if (isVLVIndex(entryContainer, treeName))
      {
        return newVLVIndexImporterTask(getVLVIndex(entryContainer, treeName), source, progressReporter);
      }
      return newChunkCopierTask(treeName, source, progressReporter);
    }
  }

  /**
   * This strategy performs two validations by ensuring that there is no duplicate entry (entry with same DN) and that
   * the given entry has an existing parent. To do so, the dn2id is directly imported into the database in addition of
   * id2entry. Others tree are externally sorted before being imported into the database.
   */
  private static final class SortAndImportWithDNValidation extends AbstractTwoPhaseImportStrategy implements
      ReadableTransaction
  {
    private static final int DN_CACHE_SIZE = 16;
    private final LRUPresenceCache<DN> dnCache = new LRUPresenceCache<>(DN_CACHE_SIZE);

    SortAndImportWithDNValidation(Collection<EntryContainer> entryContainers, Importer importer, File tempDir,
        BufferPool bufferPool, Executor sorter)
    {
      super(entryContainers, importer, tempDir, bufferPool, sorter);
    }

    @Override
    public Chunk newChunk(TreeName treeName) throws Exception
    {
      if (isID2Entry(treeName))
      {
        return new MostlyOrderedChunk(asChunk(treeName, importer));
      }
      else if (isDN2ID(treeName))
      {
        return asChunk(treeName, importer);
      }
      return newExternalSortChunk(treeName);
    }

    @Override
    public Callable<Void> newPhaseTwoTask(TreeName treeName, final Chunk source,
        PhaseTwoProgressReporter progressReporter)
    {
      final EntryContainer entryContainer = entryContainers.get(treeName.getBaseDN());

      if (isID2Entry(treeName))
      {
        return newFlushTask(source);
      }
      else if (isDN2ID(treeName))
      {
        return newDN2IDImporterTask(treeName, source, progressReporter, true);
      }
      else if (isVLVIndex(entryContainer, treeName))
      {
        return newVLVIndexImporterTask(getVLVIndex(entryContainer, treeName), source, progressReporter);
      }
      return newChunkCopierTask(treeName, source, progressReporter);
    }

    @Override
    public void validate(EntryContainer entryContainer, EntryID entryID, Entry entry) throws DirectoryException
    {
      final DN2ID dn2Id = entryContainer.getDN2ID();
      final DN entryDN = entry.getName();
      final DN parentDN = entryContainer.getParentWithinBase(entryDN);

      if (parentDN != null && !dnCache.contains(parentDN) && dn2Id.get(this, parentDN) == null)
      {
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_IMPORT_PARENT_NOT_FOUND.get(parentDN));
      }

      if (dn2Id.get(this, entryDN) != null)
      {
        throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_ADD_ENTRY_ALREADY_EXISTS.get(entry));
      }
      dnCache.add(entryDN);
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      return importer.read(treeName, key);
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }
  }

  /** Import only a specific indexes list while ignoring everything else. */
  private static final class RebuildIndexStrategy extends AbstractTwoPhaseImportStrategy
  {
    private final Set<String> indexesToRebuild;

    RebuildIndexStrategy(Collection<EntryContainer> entryContainers, Importer importer, File tempDir,
        BufferPool bufferPool, Executor sorter, Collection<String> indexNames)
    {
      super(entryContainers, importer, tempDir, bufferPool, sorter);
      this.indexesToRebuild = new HashSet<>(indexNames.size());
      for(String indexName : indexNames)
      {
        this.indexesToRebuild.add(indexName.toLowerCase());
      }
    }

    @Override
    void beforePhaseOne(EntryContainer entryContainer)
    {
      visitIndexes(entryContainer, visitOnlyIndexes(indexesToRebuild, setTrust(false, importer)));
      visitIndexes(entryContainer, visitOnlyIndexes(indexesToRebuild, deleteDatabase(importer)));
    }

    @Override
    void afterPhaseTwo(EntryContainer entryContainer)
    {
      visitIndexes(entryContainer, visitOnlyIndexes(indexesToRebuild, setTrust(true, importer)));
    }

    @Override
    public Chunk newChunk(TreeName treeName) throws Exception
    {
      if (indexesToRebuild.contains(treeName.getIndexId().toLowerCase()))
      {
        return newExternalSortChunk(treeName);
      }
      // Ignore
      return nullChunk();
    }

    @Override
    public Callable<Void> newPhaseTwoTask(TreeName treeName, Chunk source, PhaseTwoProgressReporter progressReporter)
    {
      final EntryContainer entryContainer = entryContainers.get(treeName.getBaseDN());

      if (indexesToRebuild.contains(treeName.getIndexId().toLowerCase()))
      {
        if (isDN2ID(treeName))
        {
          return newDN2IDImporterTask(treeName, source, progressReporter, false);
        }
        else if (isVLVIndex(entryContainer, treeName))
        {
          return newVLVIndexImporterTask(getVLVIndex(entryContainer, treeName), source, progressReporter);
        }
        return newChunkCopierTask(treeName, source, progressReporter);
      }
      // Do nothing (flush null chunk)
      return newFlushTask(source);
    }

    @Override
    public void validate(EntryContainer entryContainer, EntryID entryID, Entry entry) throws DirectoryException
    {
      // No validation performed. All entries are considered valid.
    }
  }

  private static <V> List<V> invokeParallel(String threadNameTemplate, Collection<Callable<V>> tasks)
      throws InterruptedException, ExecutionException
  {
    final ExecutorService executor = Executors.newCachedThreadPool(newThreadFactory(null, threadNameTemplate, true));
    try
    {
      final CompletionService<V> completionService = new ExecutorCompletionService<>(executor);
      for (Callable<V> task : tasks)
      {
        completionService.submit(task);
      }
      return waitTasksTermination(completionService, tasks.size());
    }
    finally
    {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * A {@link WriteableTransaction} delegates the storage of data to {@link Chunk}s which are created on-demand for each
   * {@link TreeName} through the provided {@link ChunkFactory}. Once there is no more data to import, call
   * {@link #getChunks()} to get the resulting {@link Chunk}s containing the sorted data to import into database.
   * {@link #put(TreeName, ByteSequence, ByteSequence)} is thread-safe. Since there is only one {@link Chunk} created
   * per {@link TreeName}, the {@link Chunk#put(ByteSequence, ByteSequence)} method of returned {@link Chunk} must be
   * thread-safe.
   */
  private static final class PhaseOneWriteableTransaction implements WriteableTransaction
  {
    /**  Must be power of 2 because of fast-modulo computing. */
    private static final int LOCKTABLE_SIZE = 64;
    private final ConcurrentMap<TreeName, Chunk> chunks = new ConcurrentHashMap<>();
    private final ChunkFactory chunkFactory;
    private final Object[] lockTable = new Object[LOCKTABLE_SIZE];

    PhaseOneWriteableTransaction(ChunkFactory chunkFactory)
    {
      this.chunkFactory = chunkFactory;
      for (int i = 0; i < LOCKTABLE_SIZE; i++)
      {
        lockTable[i] = new Object();
      }
    }

    Map<TreeName, Chunk> getChunks()
    {
      return chunks;
    }

    /**
     * Store record into a {@link Chunk}. Creating one if none is existing for the given treeName. This method is
     * thread-safe.
     */
    @Override
    public void put(final TreeName treeName, ByteSequence key, ByteSequence value)
    {
      try
      {
        getOrCreateChunk(treeName).put(key, value);
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private Chunk getOrCreateChunk(final TreeName treeName) throws Exception
    {
      Chunk alreadyExistingChunk = chunks.get(treeName);
      if (alreadyExistingChunk != null)
      {
        return alreadyExistingChunk;
      }

      // Fast modulo computing.
      final int lockIndex = treeName.hashCode() & (LOCKTABLE_SIZE - 1);
      synchronized (lockTable[lockIndex])
      {
        alreadyExistingChunk = chunks.get(treeName);
        if (alreadyExistingChunk != null)
        {
          return alreadyExistingChunk;
        }
        final Chunk newChunk = chunkFactory.newChunk(treeName);
        chunks.put(treeName, newChunk);
        return newChunk;
      }
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteTree(TreeName name)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Chunk implementations are a data storage with an optional limited capacity. Chunk are typically used by first
   * adding data to the storage using {@link #put(ByteSequence, ByteSequence)} later on data can be sequentially
   * accessed using {@link #flip()}.
   */
  interface Chunk
  {
    /**
     * Add data to the storage. Wherever this method is thread-safe or not is implementation dependent.
     *
     * @return true if the data were added to the storage, false if the chunk is full.
     */
    boolean put(ByteSequence key, ByteSequence value);

    /**
     * Flip this chunk from write-only to read-only in order to get the previously stored data. This method must be
     * called only once. After flip is called, Chunk instance must not be used anymore.
     *
     * @return a {@link MeteredCursor} to access the data
     */
    MeteredCursor<ByteString, ByteString> flip();

    /**
     * Return size of data contained in this chunk. This size is guaranteed to be consistent only if there is no pending
     * {@link #put(ByteSequence, ByteSequence)} operations.
     */
    long size();
  }

  /**
   * Store and sort data into multiple chunks. Thanks to the chunk rolling mechanism, this chunk can sort and store an
   * unlimited amount of data. This class uses double-buffering: data are firstly stored in a
   * {@link InMemorySortedChunk} which, once full, will be asynchronously sorted and copied into a
   * {@link FileRegion}. Duplicate keys are reduced by a {@link Collector}.
   * {@link #put(ByteSequence, ByteSequence))} is thread-safe.
   * This class is used in phase-one. There is one {@link ExternalSortChunk} per
   * database tree, shared across all phase-one importer threads, in charge of storing/sorting records.
   */
  static final class ExternalSortChunk implements Chunk
  {
    /** Name reported by the {@link MeteredCursor} after {@link #flip()}. */
    private final String name;
    /** Provides buffer used to store and sort chunk of data. */
    private final BufferPool bufferPool;
    /** File containing the regions used to store the data. */
    private final FileChannel channel;
    /** Pointer to the next available region in the file, typically at end of file. */
    private final AtomicLong filePosition = new AtomicLong();
    /** Collector used to reduces the number of duplicate keys during sort. */
    private final Collector<?, ByteString> phaseOneDeduplicator;
    private final Collector<?, ByteString> phaseTwoDeduplicator;
    /** Keep track of pending sorting tasks. */
    private final CompletionService<Region> sorter;
    /** Keep track of currently opened chunks. */
    private final Set<Chunk> activeChunks = Collections.synchronizedSet(new HashSet<Chunk>());
    /** Keep track of the number of chunks created. */
    private final AtomicInteger nbSortedChunks = new AtomicInteger();
    /** Size approximation of data contained in this chunk. */
    private final AtomicLong size = new AtomicLong();
    /** Active chunk for the current thread. */
    private final ThreadLocal<Chunk> currentChunk = new ThreadLocal<Chunk>()
    {
      @Override
      protected Chunk initialValue()
      {
        return nullChunk();
      }
    };

    ExternalSortChunk(File tempDir, String name, BufferPool bufferPool, Collector<?, ByteString> phaseOneDeduplicator,
        Collector<?, ByteString> phaseTwoDeduplicator, Executor sortExecutor) throws IOException
    {
      this.name = name;
      this.bufferPool = bufferPool;
      this.phaseOneDeduplicator = phaseOneDeduplicator;
      this.phaseTwoDeduplicator = phaseTwoDeduplicator;
      final File file = new File(tempDir, name.replaceAll("\\W+", "_") + "_" + UUID.randomUUID().toString());
      this.channel = open(file.toPath(), CREATE_NEW, SPARSE, READ, WRITE);
      this.sorter = new ExecutorCompletionService<>(sortExecutor);
    }

    @Override
    public boolean put(final ByteSequence key, final ByteSequence value)
    {
      final Chunk chunk = currentChunk.get();
      if (!chunk.put(key, value))
      {
        sortAndAppendChunkAsync(chunk);
        activeChunks.remove(chunk);

        final Chunk newChunk = new InMemorySortedChunk(name, bufferPool);
        activeChunks.add(newChunk);
        currentChunk.set(newChunk);
        newChunk.put(key, value);
      }
      return true;
    }

    @Override
    public MeteredCursor<ByteString, ByteString> flip()
    {
      for (Chunk chunk : activeChunks)
      {
        sortAndAppendChunkAsync(chunk);
      }

      final List<MeteredCursor<ByteString, ByteString>> cursors = new ArrayList<>();
      try
      {
        final List<Region> regions = waitTasksTermination(sorter, nbSortedChunks.get());
        Collections.sort(regions); // Sort regions by their starting offsets.
        long mmapPosition = 0;
        // Create as big as possible memory are (handling 2Gb limit) and create as many cursors as regions from
        // these area.
        MappedByteBuffer mmap = channel.map(MapMode.READ_ONLY, mmapPosition, Math.min(size.get(), Integer.MAX_VALUE));
        for (Region region : regions)
        {
          if ((region.offset + region.size) > (mmapPosition + Integer.MAX_VALUE))
          {
            // Handle the 2Gb ByteBuffer limit
            mmapPosition = region.offset;
            mmap = channel.map(MapMode.READ_ONLY, mmapPosition, Math.min(size.get() - mmapPosition, Integer.MAX_VALUE));
          }
          final ByteBuffer regionBuffer = mmap.duplicate();
          final int relativeRegionOffset = (int) (region.offset - mmapPosition);
          regionBuffer.position(relativeRegionOffset).limit(regionBuffer.position() + region.size);
          cursors.add(new FileRegion.Cursor(name, regionBuffer.slice()));
        }
      }
      catch (ExecutionException | InterruptedException | IOException e)
      {
        throw new StorageRuntimeException(e);
      }

      return new CollectorCursor<>(new CompositeCursor<ByteString, ByteString>(name, cursors)
      {
        @Override
        public void close()
        {
          super.close();
          if (OperatingSystem.isWindows())
          {
            // Windows might not be able to delete the file (see http://bugs.java.com/view_bug.do?bug_id=4715154)
            // To prevent these not deleted files to waste space, we empty it.
            try
            {
              channel.truncate(0);
            }
            catch (IOException e)
            {
              // This is best effort, it's safe to ignore the exception here.
            }
          }
          closeSilently(channel);
        }
      }, (Collector<?, ByteString>) phaseTwoDeduplicator);
    }

    @Override
    public long size()
    {
      long activeSize = 0;
      for (Chunk chunk : activeChunks)
      {
        activeSize += chunk.size();
      }
      return size.get() + activeSize;
    }

    int getNbSortedChunks()
    {
      return nbSortedChunks.get();
    }

    private void sortAndAppendChunkAsync(final Chunk chunk)
    {
      size.addAndGet(chunk.size());

      final long startOffset = filePosition.getAndAdd(chunk.size());
      nbSortedChunks.incrementAndGet();

      sorter.submit(new Callable<Region>()
      {
        @Override
        public Region call() throws Exception
        {
          /*
           * NOTE: The resulting size of the FileRegion might be less than chunk.size() because of key de-duplication
           * performed by the CollectorCursor.
           */
          checkThreadNotInterrupted();
          final FileRegion region = new FileRegion(channel, startOffset, chunk.size());
          final int regionSize;
          try (final SequentialCursor<ByteString, ByteString> source =
              new CollectorCursor<>(chunk.flip(), phaseOneDeduplicator))
          {
            regionSize = region.write(source);
          }
          return new Region(startOffset, regionSize);
        }
      });
    }

    /** Define a region inside a file. */
    private static final class Region implements Comparable<Region>
    {
      private final long offset;
      private final int size;

      Region(long offset, int size)
      {
        this.offset = offset;
        this.size = size;
      }

      @Override
      public int compareTo(Region o)
      {
        return Long.compare(offset, o.offset);
      }
    }

    /**
     * Store data inside fixed-size byte arrays. Data stored in this chunk are sorted by key during the flip() so that
     * they can be cursored ascendantly. Byte arrays are supplied through a {@link BufferPool}. To allow sort operation,
     * data must be accessible randomly. To do so, offsets of each key/value records are stored in the buffer. To
     * maximize space occupation, buffer content is split in two parts: one contains records offset, the other contains
     * the records themselves:
     *
     * <pre>
     * ----------> offset writer direction ----------------> |<- free ->| <---- record writer direction ---
     * +-----------------+-----------------+-----------------+----------+----------+----------+----------+
     * | offset record 1 | offset record 2 | offset record n | .........| record n | record 2 | record 1 |
     * +-----------------+-----------------+-----------------+----------+----------+----------+----------+
     * </pre>
     *
     * Each record is the concatenation of a key/value (length are encoded using {@link PackedLong} representation)
     *
     * <pre>
     * +------------+--------------+--------------+----------------+
     * | key length | key bytes... | value length | value bytes... |
     * +------------+--------------+--------------+----------------+
     * </pre>
     */
    static final class InMemorySortedChunk implements Chunk, Comparator<Integer>
    {
      private final String metricName;
      private final BufferPool bufferPool;
      private final Buffer buffer;
      private long totalBytes;
      private int indexPos;
      private int dataPos;
      private int nbRecords;

      InMemorySortedChunk(String name, BufferPool bufferPool)
      {
        this.metricName = name;
        this.bufferPool = bufferPool;
        this.buffer = bufferPool.get();
        this.dataPos = buffer.length();
      }

      @Override
      public boolean put(ByteSequence key, ByteSequence value)
      {
        final int keyRecordSize = INT_SIZE + key.length();
        final int recordSize = keyRecordSize + INT_SIZE + value.length();

        dataPos -= recordSize;
        int recordDataPos = dataPos;

        final int recordIndexPos = indexPos;
        indexPos += INT_SIZE;

        if (indexPos > dataPos)
        {
          // Chunk is full
          return false;
        }

        nbRecords++;
        totalBytes += recordSize;

        // Write record offset
        buffer.writeInt(recordIndexPos, recordDataPos);

        buffer.writeInt(recordDataPos, key.length());
        recordDataPos += INT_SIZE;
        buffer.writeInt(recordDataPos, value.length());
        recordDataPos += INT_SIZE;
        buffer.writeByteSequence(recordDataPos, key);
        recordDataPos += key.length();
        buffer.writeByteSequence(recordDataPos, value);

        return true;
      }

      @Override
      public long size()
      {
        return totalBytes;
      }

      @Override
      public MeteredCursor<ByteString, ByteString> flip()
      {
        Collections.sort(new AbstractList<Integer>()
        {
          @Override
          public Integer get(int index)
          {
            return getOffsetAtPosition(index * INT_SIZE);
          }

          private Integer getOffsetAtPosition(int pos)
          {
            return buffer.readInt(pos);
          }

          @Override
          public Integer set(int index, Integer element)
          {
            final int pos = index * INT_SIZE;
            final Integer valueA = getOffsetAtPosition(pos);
            buffer.writeInt(pos, element);
            return valueA;
          }

          @Override
          public int size()
          {
            return nbRecords;
          }
        }, this);

        return new InMemorySortedChunkCursor();
      }

      @Override
      public int compare(Integer offsetA, Integer offsetB)
      {
        final int iOffsetA = offsetA.intValue();
        final int iOffsetB = offsetB.intValue();
        if (iOffsetA == iOffsetB)
        {
          return 0;
        }
        // Compare Keys
        final int keyLengthA = buffer.readInt(iOffsetA);
        final int keyLengthB = buffer.readInt(iOffsetB);
        final int keyOffsetA = iOffsetA + 2 * INT_SIZE;
        final int keyOffsetB = iOffsetB + 2 * INT_SIZE;

        return buffer.compare(keyOffsetA, keyLengthA, keyOffsetB, keyLengthB);
      }

      /** Cursor of the in-memory chunk. */
      private final class InMemorySortedChunkCursor implements MeteredCursor<ByteString, ByteString>
      {
        private ByteString key;
        private ByteString value;
        private volatile long bytesRead;
        private int indexOffset;

        @Override
        public boolean next()
        {
          if (bytesRead >= totalBytes)
          {
            key = value = null;
            return false;
          }
          int recordOffset = buffer.readInt(indexOffset);

          final int keyLength = buffer.readInt(recordOffset);
          recordOffset += 4;
          final int valueLength = buffer.readInt(recordOffset);
          recordOffset += 4;

          key = buffer.readByteString(recordOffset, keyLength);
          recordOffset += key.length();
          value = buffer.readByteString(recordOffset, valueLength);

          indexOffset += INT_SIZE;
          bytesRead += (2 * INT_SIZE) + keyLength + valueLength;

          return true;
        }

        @Override
        public boolean isDefined()
        {
          return key != null;
        }

        @Override
        public ByteString getKey() throws NoSuchElementException
        {
          throwIfUndefined(this);
          return key;
        }

        @Override
        public ByteString getValue() throws NoSuchElementException
        {
          throwIfUndefined(this);

          return value;
        }

        @Override
        public void delete() throws NoSuchElementException, UnsupportedOperationException
        {
          throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
          key = value = null;
          bufferPool.release(buffer);
        }

        @Override
        public String getMetricName()
        {
          return metricName;
        }

        @Override
        public long getNbBytesRead()
        {
          return bytesRead;
        }

        @Override
        public long getNbBytesTotal()
        {
          return totalBytes;
        }
      }
    }

    /**
     * Store data inside a region contained in a file. A regions is delimited by an offset and a length. The region is
     * memory-mapped and the data are appended in the memory-mapped region until it is full. Region store a
     * concatenation of key/value records: (Key & value sizes are stored using {@link PackedLong} format.)
     *
     * <pre>
     * +------------+--------------+--------------+----------------+
     * | key length | value length | key bytes... | value bytes... |
     * +------------+--------------+--------------+----------------+
     * </pre>
     */
    static final class FileRegion
    {
      private final MappedByteBuffer mmapBuffer;
      private final OutputStream mmapBufferOS = new OutputStream()
      {
        @Override
        public void write(int arg0) throws IOException
        {
          mmapBuffer.put((byte) arg0);
        }
      };

      FileRegion(FileChannel channel, long startOffset, long size) throws IOException
      {
        if (size > 0)
        {
          // Make sure that the file is big-enough to encapsulate this memory-mapped region.
          channel.write(ByteBuffer.wrap(new byte[] { 0 }), (startOffset + size) - 1);
        }
        mmapBuffer = channel.map(MapMode.READ_WRITE, startOffset, size);
      }

      public int write(SequentialCursor<ByteString, ByteString> source) throws IOException, InterruptedException
      {
        checkThreadNotInterrupted();
        while (source.next())
        {
          final ByteSequence key = source.getKey();
          final ByteSequence value = source.getValue();
          PackedLong.writeCompactUnsigned(mmapBufferOS, key.length());
          PackedLong.writeCompactUnsigned(mmapBufferOS, value.length());
          key.copyTo(mmapBuffer);
          value.copyTo(mmapBuffer);
          checkThreadNotInterrupted();
        }
        return mmapBuffer.position();
      }

      /** Cursor through the specific memory-mapped file's region. */
      static final class Cursor implements MeteredCursor<ByteString, ByteString>
      {
        private final InputStream asInputStream = new InputStream()
        {
          @Override
          public int read() throws IOException
          {
            return region.get() & 0xFF;
          }
        };
        private final String metricName;
        private ByteBuffer region;
        private ByteString key, value;

        Cursor(String metricName, ByteBuffer region)
        {
          this.metricName = metricName;
          this.region = region;
        }

        @Override
        public boolean next()
        {
          if (!region.hasRemaining())
          {
            key = value = null;
            return false;
          }

          final int keyLength;
          final int valueLength;
          try
          {
            keyLength = (int) PackedLong.readCompactUnsignedLong(asInputStream);
            valueLength = (int) PackedLong.readCompactUnsignedLong(asInputStream);
          }
          catch (IOException e)
          {
            throw new StorageRuntimeException(e);
          }
          final int recordSize = keyLength + valueLength;

          final byte[] keyValueData = new byte[recordSize];
          region.get(keyValueData, 0, recordSize);

          key = ByteString.wrap(keyValueData, 0, keyLength);
          value = ByteString.wrap(keyValueData, keyLength, valueLength);

          return true;
        }

        @Override
        public boolean isDefined()
        {
          return key != null;
        }

        @Override
        public ByteString getKey() throws NoSuchElementException
        {
          throwIfUndefined(this);
          return key;
        }

        @Override
        public ByteString getValue() throws NoSuchElementException
        {
          throwIfUndefined(this);
          return value;
        }

        @Override
        public void delete() throws NoSuchElementException, UnsupportedOperationException
        {
          throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
          key = value = null;
          region = null;
        }

        @Override
        public String getMetricName()
        {
          return metricName;
        }

        @Override
        public long getNbBytesRead()
        {
          return region.position();
        }

        @Override
        public long getNbBytesTotal()
        {
          return region.limit();
        }
      }
    }

    /** A cursor de-duplicating data with the same keys from a sorted cursor. */
    static final class CollectorCursor<A, K, V> implements MeteredCursor<K, V>
    {
      private final MeteredCursor<K, ? extends V> delegate;
      private final Collector<A, V> collector;
      private boolean isDefined;
      private K key;
      private V value;

      CollectorCursor(MeteredCursor<K, ? extends V> cursor, Collector<A, V> collector)
      {
        this.delegate = cursor;
        this.collector = collector;
        if (!delegate.isDefined())
        {
          delegate.next();
        }
      }

      @Override
      public boolean next()
      {
        isDefined = delegate.isDefined();
        if (isDefined)
        {
          key = delegate.getKey();
          accumulateValues();
        }
        return isDefined;
      }

      private void accumulateValues()
      {
        throwIfUndefined(this);
        A resultContainer = collector.get();
        do
        {
          resultContainer = collector.accept(resultContainer, delegate.getValue());
        }
        while (delegate.next() && key.equals(delegate.getKey()));
        value = collector.merge(resultContainer);
        // Delegate is one step beyond. When delegate.isDefined() return false, we have to return true once more.
        isDefined = true;
      }

      @Override
      public boolean isDefined()
      {
        return isDefined;
      }

      @Override
      public K getKey() throws NoSuchElementException
      {
        throwIfUndefined(this);
        return key;
      }

      @Override
      public V getValue() throws NoSuchElementException
      {
        throwIfUndefined(this);
        return value;
      }

      @Override
      public void delete() throws NoSuchElementException, UnsupportedOperationException
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close()
      {
        key = null;
        delegate.close();
      }

      @Override
      public String getMetricName()
      {
        return delegate.getMetricName();
      }

      @Override
      public long getNbBytesRead()
      {
        return delegate.getNbBytesRead();
      }

      @Override
      public long getNbBytesTotal()
      {
        return delegate.getNbBytesTotal();
      }
    }

    /** Provides a globally sorted cursor from multiple sorted cursors. */
    static class CompositeCursor<K extends Comparable<? super K>, V> implements MeteredCursor<K, V>
    {
      /** Contains the non empty and sorted cursors ordered in regards of their current key. */
      private final NavigableSet<MeteredCursor<K, V>> orderedCursors;
      private final String metricName;
      private final long totalBytes;
      private volatile long bytesRead;
      private K key;
      private V value;

      CompositeCursor(String metricName, Collection<MeteredCursor<K, V>> cursors)
      {
        this.metricName = metricName;
        this.orderedCursors = new TreeSet<>(new Comparator<MeteredCursor<K, V>>()
        {
          @Override
          public int compare(MeteredCursor<K, V> o1, MeteredCursor<K, V> o2)
          {
            final int cmp = o1.getKey().compareTo(o2.getKey());
            // Never return 0. Otherwise both cursors are considered equal and only one of them is kept by this set
            return cmp == 0 ? Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2)) : cmp;
          }
        });

        long totalBytesSum = 0;
        for (MeteredCursor<K, V> cursor : cursors)
        {
          long previousBytesRead = cursor.getNbBytesRead();
          if (cursor.isDefined() || cursor.next())
          {
            if (orderedCursors.add(cursor))
            {
              bytesRead += (cursor.getNbBytesRead() - previousBytesRead);
              totalBytesSum += cursor.getNbBytesTotal();
            }
          }
          else
          {
            cursor.close();
          }
        }
        this.totalBytes = totalBytesSum;
      }

      /**
       * Try to get the next record from the cursor containing the lowest entry. If it reaches the end of the lowest
       * cursor, it calls the close method and begins reading from the next lowest cursor.
       */
      @Override
      public boolean next()
      {
        final MeteredCursor<K, V> lowestCursor = orderedCursors.pollFirst();
        if (lowestCursor == null)
        {
          key = null;
          value = null;
          return false;
        }

        key = lowestCursor.getKey();
        value = lowestCursor.getValue();

        long previousBytesRead = lowestCursor.getNbBytesRead();
        if (lowestCursor.next())
        {
          bytesRead += (lowestCursor.getNbBytesRead() - previousBytesRead);
          orderedCursors.add(lowestCursor);
        }
        else
        {
          lowestCursor.close();
        }
        return true;
      }

      @Override
      public boolean isDefined()
      {
        return key != null;
      }

      @Override
      public K getKey() throws NoSuchElementException
      {
        throwIfUndefined(this);
        return key;
      }

      @Override
      public V getValue() throws NoSuchElementException
      {
        throwIfUndefined(this);
        return value;
      }

      @Override
      public void delete() throws NoSuchElementException, UnsupportedOperationException
      {
        throw new UnsupportedOperationException();
      }

      @Override
      public void close()
      {
        closeSilently(orderedCursors);
      }

      @Override
      public String getMetricName()
      {
        return metricName;
      }

      @Override
      public long getNbBytesRead()
      {
        return bytesRead;
      }

      @Override
      public long getNbBytesTotal()
      {
        return totalBytes;
      }
    }
  }

  private static Chunk asChunk(TreeName treeName, Importer importer)
  {
    return new ImporterToChunkAdapter(treeName, importer);
  }

  /** Task to copy one {@link Chunk} into a database tree through an {@link Importer}. */
  private static final class ChunkCopierTask implements Callable<Void>
  {
    private final PhaseTwoProgressReporter reporter;
    private final TreeName treeName;
    private final Importer destination;
    private final Chunk source;

    ChunkCopierTask(PhaseTwoProgressReporter reporter, Chunk source, TreeName treeName, Importer destination)
    {
      this.source = source;
      this.treeName = treeName;
      this.destination = destination;
      this.reporter = reporter;
    }

    @Override
    public Void call() throws InterruptedException
    {
      checkThreadNotInterrupted();
      try (final SequentialCursor<ByteString, ByteString> sourceCursor = trackCursorProgress(reporter, source.flip()))
      {
        copyIntoChunk(sourceCursor, asChunk(treeName, destination));
      }
      return null;
    }
  }

  /** Task to copy VLV's counter chunks into a database tree. */
  private static final class VLVIndexImporterTask implements Callable<Void>
  {
    private final PhaseTwoProgressReporter reporter;
    private final VLVIndex vlvIndex;
    private final Importer destination;
    private final Chunk source;

    VLVIndexImporterTask(PhaseTwoProgressReporter reporter, Chunk source, VLVIndex vlvIndex, Importer destination)
    {
      this.source = source;
      this.vlvIndex = vlvIndex;
      this.destination = destination;
      this.reporter = reporter;
    }

    @Override
    public Void call() throws InterruptedException
    {
      checkThreadNotInterrupted();
      try (final SequentialCursor<ByteString, ByteString> sourceCursor = trackCursorProgress(reporter, source.flip()))
      {
        final long nbRecords = copyIntoChunk(sourceCursor, asChunk(vlvIndex.getName(), destination));
        vlvIndex.importCount(destination, nbRecords);
        return null;
      }
    }
  }

  private static long copyIntoChunk(SequentialCursor<ByteString, ByteString> source, Chunk destination)
      throws InterruptedException
  {
    long nbRecords = 0;
    checkThreadNotInterrupted();
    while (source.next())
    {
      if (!destination.put(source.getKey(), source.getValue()))
      {
        throw new IllegalStateException("Destination chunk is full");
      }
      nbRecords++;
      checkThreadNotInterrupted();
    }
    return nbRecords;
  }

  /**
   * This task optionally copy the dn2id chunk into the database and takes advantages of it's cursoring to compute the
   * {@link ID2ChildrenCount} index.
   */
  private static final class DN2IDImporterTask implements Callable<Void>
  {
    private final PhaseTwoProgressReporter reporter;
    private final Importer importer;
    private final File tempDir;
    private final BufferPool bufferPool;
    private final DN2ID dn2id;
    private final ID2ChildrenCount id2count;
    private final Collector<?, ByteString> id2countCollector;
    private final Chunk dn2IdSourceChunk;
    private final Chunk dn2IdDestination;

    DN2IDImporterTask(PhaseTwoProgressReporter progressReporter, Importer importer, File tempDir, BufferPool bufferPool,
        DN2ID dn2id, Chunk dn2IdChunk, ID2ChildrenCount id2count, Collector<?, ByteString> id2countCollector,
        boolean dn2idAlreadyImported)
    {
      this.reporter = progressReporter;
      this.importer = importer;
      this.tempDir = tempDir;
      this.bufferPool = bufferPool;
      this.dn2id = dn2id;
      this.dn2IdSourceChunk = dn2IdChunk;
      this.id2count = id2count;
      this.id2countCollector = id2countCollector;
      this.dn2IdDestination = dn2idAlreadyImported ? nullChunk() : asChunk(dn2id.getName(), importer);
    }

    @Override
    public Void call() throws Exception
    {
      final Chunk id2CountChunk =
          new ExternalSortChunk(tempDir, id2count.getName().toString(), bufferPool, id2countCollector,
              id2countCollector, sameThreadExecutor());
      long totalNumberOfEntries = 0;

      final TreeVisitor<ChildrenCount> visitor = new ID2CountTreeVisitorImporter(asImporter(id2CountChunk));
      try (final MeteredCursor<ByteString, ByteString> chunkCursor = dn2IdSourceChunk.flip();
          final SequentialCursor<ByteString, ByteString> dn2idCursor =
              dn2id.openCursor(trackCursorProgress(reporter, chunkCursor), visitor))
      {
        checkThreadNotInterrupted();
        while (dn2idCursor.next())
        {
          dn2IdDestination.put(dn2idCursor.getKey(), dn2idCursor.getValue());
          totalNumberOfEntries++;
          checkThreadNotInterrupted();
        }
      }
      id2count.importPutTotalCount(asImporter(id2CountChunk), Math.max(0, totalNumberOfEntries));

      new ChunkCopierTask(reporter, id2CountChunk, id2count.getName(), importer).call();
      return null;
    }

    /** TreeVisitor computing and importing the number of children per parent. */
    private final class ID2CountTreeVisitorImporter implements TreeVisitor<ChildrenCount>
    {
      private final Importer importer;

      ID2CountTreeVisitorImporter(Importer importer)
      {
        this.importer = importer;
      }

      @Override
      public ChildrenCount beginParent(EntryID parentID)
      {
        return new ChildrenCount(parentID);
      }

      @Override
      public void onChild(ChildrenCount parent, EntryID childID)
      {
        parent.numberOfChildren++;
      }

      @Override
      public void endParent(ChildrenCount parent)
      {
        if (parent.numberOfChildren > 0)
        {
          id2count.importPut(importer, parent.parentEntryID, parent.numberOfChildren);
        }
      }
    }

    /** Keep track of the number of children during the dn2id visit. */
    private static final class ChildrenCount
    {
      private final EntryID parentEntryID;
      private long numberOfChildren;

      private ChildrenCount(EntryID id)
      {
        this.parentEntryID = id;
      }
    }
  }

  private static Importer asImporter(Chunk chunk)
  {
    return new ChunkToImporterAdapter(chunk);
  }

  /**
   * Delegates the storage of data to the {@link Importer}. This class has same thread-safeness as the supplied
   * importer.
   */
  private static final class ImporterToChunkAdapter implements Chunk
  {
    private final TreeName treeName;
    private final Importer importer;
    private final AtomicLong size = new AtomicLong();

    ImporterToChunkAdapter(TreeName treeName, Importer importer)
    {
      this.treeName = treeName;
      this.importer = importer;
    }

    @Override
    public boolean put(ByteSequence key, ByteSequence value)
    {
      importer.put(treeName, key, value);
      size.addAndGet(key.length() + value.length());
      return true;
    }

    @Override
    public MeteredCursor<ByteString, ByteString> flip()
    {
      return asProgressCursor(importer.openCursor(treeName), treeName.toString(), size.get());
    }

    @Override
    public long size()
    {
      return size.get();
    }
  }

  /**
   * Delegates the {@link #put(TreeName, ByteSequence, ByteSequence)} method of {@link Importer} to a {@link Chunk}.
   * {@link #createTree(TreeName)} is a no-op, other methods throw {@link UnsupportedOperationException}. This class has
   * same thread-safeness as the supplied {@link Chunk}.
   */
  private static final class ChunkToImporterAdapter implements Importer
  {
    private final Chunk chunk;

    ChunkToImporterAdapter(Chunk chunk)
    {
      this.chunk = chunk;
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      try
      {
        chunk.put(key, value);
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void clearTree(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public SequentialCursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
      // nothing to do
    }
  }

  /**
   * Write records into a delegated {@link Chunk} after performing a reordering of those records in regards of their key
   * by using a best-effort algorithm. This class is intended to be used when records are initially ordered but might
   * actually hit a chunk slightly disordered due to scheduling occurring in a multi-threaded environment. Records are
   * buffered and sorted before being written to the delegated chunk. Because of the buffer mechanism, records might be
   * written into the chunk after some delay. It's guaranteed that all entries will be written into the chunk only after
   * the flip() method has been called. {@link #put(TreeName, ByteSequence, ByteSequence)} is thread-safe.
   */
  private static final class MostlyOrderedChunk implements Chunk
  {
    /**
     * Number of items to queue before writing them to the storage. This number must be at least equal to the number of
     * threads which will access the put() method. If underestimated, {@link #put(ByteSequence, ByteSequence)} might
     * lead to unordered copy. If overestimated, extra memory is wasted.
     */
    private static final int QUEUE_SIZE = 128;

    /**
     * Maximum queued entry size. Beyond this size, entry will not be queued but written directly to the storage in
     * order to limit the heap size requirement for import.
     */
    private static final int ENTRY_MAX_SIZE = 32 * KB;

    private final NavigableMap<ByteSequence, ByteSequence> pendingRecords = new TreeMap<>();
    private final int queueSize;
    private final Chunk delegate;

    MostlyOrderedChunk(Chunk delegate)
    {
      this.delegate = delegate;
      this.queueSize = QUEUE_SIZE;
    }

    @Override
    public synchronized boolean put(ByteSequence key, ByteSequence value)
    {
      if ((key.length() + value.length()) >= ENTRY_MAX_SIZE)
      {
          return delegate.put(key, value);
      }

      pendingRecords.put(key, value);
      if (pendingRecords.size() == queueSize)
      {
        /*
         * Maximum size reached, take the record with the smallest key and persist it in the delegate chunk. this
         * ensures records are (mostly) inserted in ascending key order, which is the optimal insert order for B-trees.
         */
        final Map.Entry<ByteSequence, ByteSequence> lowestEntry = pendingRecords.pollFirstEntry();
        return delegate.put(lowestEntry.getKey(), lowestEntry.getValue());
      }
      return true;
    }

    @Override
    public MeteredCursor<ByteString, ByteString> flip()
    {
      // Purge pending entries
      for (Map.Entry<ByteSequence, ByteSequence> lowestEntry : pendingRecords.entrySet())
      {
        delegate.put(lowestEntry.getKey(), lowestEntry.getValue());
      }
      return delegate.flip();
    }

    @Override
    public long size()
    {
      return delegate.size();
    }
  }

  private static Chunk nullChunk()
  {
    return NullChunk.INSTANCE;
  }

  /** An empty Chunk which cannot store data. */
  private static final class NullChunk implements Chunk
  {
    private static final Chunk INSTANCE = new NullChunk();

    @Override
    public boolean put(ByteSequence key, ByteSequence value)
    {
      return false;
    }

    @Override
    public long size()
    {
      return 0;
    }

    @Override
    public MeteredCursor<ByteString, ByteString> flip()
    {
      return new MeteredCursor<ByteString, ByteString>()
      {
        @Override
        public boolean next()
        {
          return false;
        }

        @Override
        public boolean isDefined()
        {
          return false;
        }

        @Override
        public ByteString getKey() throws NoSuchElementException
        {
          throw new NoSuchElementException();
        }

        @Override
        public ByteString getValue() throws NoSuchElementException
        {
          throw new NoSuchElementException();
        }

        @Override
        public void delete() throws NoSuchElementException, UnsupportedOperationException
        {
          throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
          // nothing to do
        }

        @Override
        public String getMetricName()
        {
          return NullChunk.class.getSimpleName();
        }

        @Override
        public long getNbBytesRead()
        {
          return 0;
        }

        @Override
        public long getNbBytesTotal()
        {
          return 0;
        }
      };
    }
  }

  /** Executor delegating the execution of task to the current thread. */
  private static Executor sameThreadExecutor()
  {
    return new Executor()
    {
      @Override
      public void execute(Runnable command)
      {
        command.run();
      }
    };
  }

  /** Collect the results of asynchronous tasks. */
  private static <K> List<K> waitTasksTermination(CompletionService<K> completionService, int nbTasks)
      throws InterruptedException, ExecutionException
  {
    final List<K> results = new ArrayList<>(nbTasks);
    for (int i = 0; i < nbTasks; i++)
    {
      results.add(completionService.take().get());
    }
    return results;
  }

  private static void checkThreadNotInterrupted() throws InterruptedException
  {
    if (Thread.interrupted())
    {
      throw new InterruptedException();
    }
  }

  /** Regularly report progress statistics from the registered list of {@link ProgressMetric}. */
  private static final class PhaseTwoProgressReporter implements Runnable, Closeable
  {
    private static final String PHASE2_REPORTER_THREAD_NAME = "PHASE2-REPORTER-%d";

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(newThreadFactory(null, PHASE2_REPORTER_THREAD_NAME, true));
    private final Map<MeteredCursor<?, ?>, Long> lastValues = new WeakHashMap<>();
    private ScheduledFuture<?> scheduledTask;
    private long lastRun = System.currentTimeMillis();

    synchronized void addCursor(MeteredCursor<?, ?> cursor)
    {
      if (lastValues.put(cursor, 0L) == null)
      {
        logger.info(NOTE_IMPORT_LDIF_INDEX_STARTED, cursor.getMetricName(), 1, 1);
      }
      if (scheduledTask == null)
      {
        scheduledTask = scheduler.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
      }
    }

    synchronized void removeCursor(MeteredCursor<?, ?> cursor)
    {
      if (lastValues.remove(cursor) != null)
      {
        logger.info(NOTE_IMPORT_LDIF_INDEX_CLOSE, cursor.getMetricName());
      }
    }

    @Override
    public synchronized void run()
    {
      final long deltaTime = System.currentTimeMillis() - lastRun;
      if (deltaTime == 0)
      {
        return;
      }
      for (Map.Entry<MeteredCursor<?, ?>, Long> metricLastValue : lastValues.entrySet())
      {
        final MeteredCursor<?, ?> cursor = metricLastValue.getKey();
        final long newValue = cursor.getNbBytesRead();

        final long totalBytes = cursor.getNbBytesTotal();
        final long valueProgress = newValue - metricLastValue.getValue();
        final int progressPercent = totalBytes > 0 ? Math.round((100f * newValue) / cursor.getNbBytesTotal()) : 0;

        final long progressRate = valueProgress / deltaTime;
        final long progressRemaining = (cursor.getNbBytesTotal() - newValue) / 1024;

        logger.info(NOTE_IMPORT_LDIF_PHASE_TWO_REPORT, cursor.getMetricName(), progressPercent, progressRemaining,
            progressRate, 1, 1);

        lastValues.put(cursor, newValue);
      }
      lastRun = System.currentTimeMillis();
    }

    @Override
    public synchronized void close()
    {
      scheduledTask = null;
      scheduler.shutdown();
    }
  }

  /** Buffer used by {@link InMemorySortedChunk} to store and sort data. */
  interface Buffer extends Closeable
  {
    void writeInt(int position, int value);

    int readInt(int position);

    ByteString readByteString(int position, int length);

    void writeByteSequence(int position, ByteSequence data);

    int length();

    int compare(int offsetA, int lengthA, int offsetB, int lengthB);
  }

  /**
   * Pre-allocate and maintain a fixed number of re-usable {@code Buffer}s. This allow to keep controls of heap memory
   * consumption and prevents the significant object allocation cost occurring for huge objects.
   */
  static final class BufferPool implements Closeable
  {
    private static final Object UNSAFE_OBJECT;
    static final boolean SUPPORTS_OFF_HEAP;
    static
    {
      Object unsafeObject = null;
      try
      {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafeField.setAccessible(true);
        unsafeObject = theUnsafeField.get(null);
      }
      catch (Throwable e)
      {
        // Unsupported.
      }
      UNSAFE_OBJECT = unsafeObject;
      SUPPORTS_OFF_HEAP = UNSAFE_OBJECT != null;
    }

    private final BlockingQueue<Buffer> pool;
    private final int bufferSize;

    BufferPool(int nbBuffer, int bufferSize)
    {
      this.pool = new ArrayBlockingQueue<>(nbBuffer);
      this.bufferSize = bufferSize;
      for (int i = 0; i < nbBuffer; i++)
      {
        pool.offer(SUPPORTS_OFF_HEAP ? new OffHeapBuffer(bufferSize) : new HeapBuffer(bufferSize));
      }
    }

    public int getBufferSize()
    {
      return bufferSize;
    }

    private Buffer get()
    {
      try
      {
        return pool.take();
      }
      catch (InterruptedException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    private void release(Buffer buffer)
    {
      try
      {
        pool.put(buffer);
      }
      catch (InterruptedException e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    public void setSize(int size)
    {
      while (pool.size() > size)
      {
        get();
      }
    }

    @Override
    public void close()
    {
      Buffer buffer;
      while ((buffer = pool.poll()) != null)
      {
        closeSilently(buffer);
      }
    }

    /** Off-heap buffer using Unsafe memory access. */
    @SuppressWarnings("restriction")
    static final class OffHeapBuffer implements Buffer
    {
      private static final Unsafe UNSAFE = (Unsafe) UNSAFE_OBJECT;
      private static final long BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

      private final long address;
      private final int size;
      private int position;
      private final OutputStream asOutputStream = new OutputStream()
      {
        @Override
        public void write(int value) throws IOException
        {
          UNSAFE.putByte(address + position++, (byte) (value & 0xFF));
        }

        @Override
        public void write(byte[] b) throws IOException {
            UNSAFE.copyMemory(b, BYTE_ARRAY_OFFSET, null, address + position, b.length);
            position += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            UNSAFE.copyMemory(b, BYTE_ARRAY_OFFSET + off, null, address + position, len);
            position += b.length;
        }
      };
      private boolean closed;

      OffHeapBuffer(int size)
      {
        this.size = size;
        this.address = UNSAFE.allocateMemory(size);
      }

      @Override
      public void writeInt(final int position, final int value)
      {
        UNSAFE.putInt(address + position, value);
      }

      @Override
      public int readInt(final int position)
      {
        return UNSAFE.getInt(address + position);
      }

      @Override
      public void writeByteSequence(final int position, ByteSequence data)
      {
        Reject.ifFalse(position + data.length() <= size);
        this.position = position;
        try
        {
          data.copyTo(asOutputStream);
        }
        catch(IOException e)
        {
          throw new StorageRuntimeException(e);
        }
      }

      @Override
      public int length()
      {
        return size;
      }

      @Override
      public ByteString readByteString(int position, int length)
      {
        Reject.ifFalse(position + length <= size);

        final byte[] data = new byte[length];
        UNSAFE.copyMemory(null, address + position, data, BYTE_ARRAY_OFFSET, length);
        return ByteString.wrap(data);
      }

      @Override
      public int compare(int offsetA, int lengthA, int offsetB, int lengthB)
      {
        final int len = Math.min(lengthA, lengthB);
        for(int i = 0 ; i < len ; i++)
        {
          final int a = UNSAFE.getByte(address + offsetA + i) & 0xFF;
          final int b = UNSAFE.getByte(address + offsetB + i) & 0xFF;
          if ( a != b )
          {
            return a - b;
          }
        }
        return lengthA - lengthB;
      }

      @Override
      public void close()
      {
        if (!closed)
        {
          UNSAFE.freeMemory(address);
        }
        closed = true;
      }
    }

    /** Heap buffer using ByteBuffer. */
    static final class HeapBuffer implements Buffer
    {
      private final ByteBuffer buffer;

      HeapBuffer(int size)
      {
        this.buffer = ByteBuffer.allocate(size);
      }

      @Override
      public void writeInt(final int position, final int value)
      {
        buffer.putInt(position, value);
      }

      @Override
      public int readInt(final int position)
      {
        return buffer.getInt(position);
      }

      @Override
      public void writeByteSequence(int position, ByteSequence data)
      {
        buffer.position(position);
        data.copyTo(buffer);
      }

      @Override
      public int length()
      {
        return buffer.capacity();
      }

      @Override
      public ByteString readByteString(int position, int length)
      {
        return ByteString.wrap(buffer.array(), buffer.arrayOffset() + position, length);
      }

      @Override
      public int compare(int offsetA, int lengthA, int offsetB, int lengthB)
      {
        return readByteString(offsetA, lengthA).compareTo(readByteString(offsetB, lengthB));
      }

      @Override
      public void close()
      {
        // Nothing to do
      }
    }
  }

  /** Extends {@link SequentialCursor} by providing metric related to cursor's progress. */
  interface MeteredCursor<K, V> extends SequentialCursor<K, V>
  {
    String getMetricName();

    long getNbBytesRead();

    long getNbBytesTotal();
  }

  /** Add the cursor to the reporter and remove it once closed. */
  private static <K, V> SequentialCursor<K, V> trackCursorProgress(final PhaseTwoProgressReporter reporter,
      final MeteredCursor<K, V> cursor)
  {
    reporter.addCursor(cursor);
    return new SequentialCursorDecorator<MeteredCursor<K, V>, K, V>(cursor)
    {
      @Override
      public void close()
      {
        reporter.removeCursor(cursor);
        cursor.close();
      }
    };
  }

  private static void throwIfUndefined(SequentialCursor<?, ?> cursor)
  {
    if (!cursor.isDefined())
    {
      throw new NoSuchElementException();
    }
  }

  /**
   * Get a new {@link Collector} which can be used to merge encoded values. The types of values to merged is deduced
   * from the {@link TreeName}
   */
  private static Collector<?, ByteString> newPhaseTwoCollector(final EntryContainer entryContainer,
      final TreeName treeName)
  {
    final DefaultIndex index = getIndex(entryContainer, treeName);
    if (index != null)
    {
      // key conflicts == merge EntryIDSets
      return new EntryIDSetsCollector(index);
    }
    else if (isID2ChildrenCount(treeName))
    {
      // key conflicts == sum values
      return ID2ChildrenCount.getSumLongCollectorInstance();
    }
    else if (isDN2ID(treeName) || isDN2URI(treeName) || isVLVIndex(entryContainer, treeName))
    {
      // key conflicts == exception
      return UniqueValueCollector.getInstance();
    }
    throw new IllegalArgumentException("Unknown tree: " + treeName);
  }

  private static Collector<?, ByteString> newPhaseOneCollector(final EntryContainer entryContainer,
      final TreeName treeName)
  {
    final DefaultIndex index = getIndex(entryContainer, treeName);
    if (index != null)
    {
      // key conflicts == merge EntryIDSets
      return new EntryIDsCollector(index);
    }
    return newPhaseTwoCollector(entryContainer, treeName);
  }

  private static boolean isDN2ID(TreeName treeName)
  {
    return SuffixContainer.DN2ID_INDEX_NAME.equals(treeName.getIndexId());
  }

  private static boolean isDN2URI(TreeName treeName)
  {
    return SuffixContainer.DN2URI_INDEX_NAME.equals(treeName.getIndexId());
  }

  private static boolean isID2Entry(TreeName treeName)
  {
    return SuffixContainer.ID2ENTRY_INDEX_NAME.equals(treeName.getIndexId());
  }

  private static boolean isID2ChildrenCount(TreeName treeName)
  {
    return SuffixContainer.ID2CHILDREN_COUNT_NAME.equals(treeName.getIndexId());
  }

  private static boolean isVLVIndex(EntryContainer entryContainer, TreeName treeName)
  {
    return getVLVIndex(entryContainer, treeName) != null;
  }

  private static VLVIndex getVLVIndex(EntryContainer entryContainer, TreeName treeName)
  {
    for (VLVIndex vlvIndex : entryContainer.getVLVIndexes())
    {
      if (treeName.equals(vlvIndex.getName()))
      {
        return vlvIndex;
      }
    }
    return null;
  }

  private static DefaultIndex getIndex(EntryContainer entryContainer, TreeName treeName)
  {
    for (AttributeIndex attrIndex : entryContainer.getAttributeIndexes())
    {
      for (MatchingRuleIndex index : attrIndex.getNameToIndexes().values())
      {
        if (treeName.equals(index.getName()))
        {
          return index;
        }
      }
    }
    return null;
  }

  /**
   * A mutable reduction operation that accumulates input elements into a mutable result container, optionally
   * transforming the accumulated result into a final representation after all input elements have been processed.
   * Reduction operations can be performed either sequentially or in parallel. A Collector is specified by three
   * functions that work together to accumulate entries into a mutable result container, and optionally perform a final
   * transform on the result. They are: Creation of a new result container (get()), incorporating a new data element
   * into a result container (accept()), performing an optional final transform on the container (merge)
   *
   * @param <A>
   *          Accumulator type
   * @param <R>
   *          Result type
   * @see java.util.stream.Collector
   */
  interface Collector<A, R>
  {
    /**
     * Creates and returns a new mutable result container. Equivalent to A java.util.function.Collector.supplier().get()
     */
    A get();

    /**
     * Accepts two partial results and merges them. The combiner function may fold state from one argument into the
     * other and return that, or may return a new result container. Equivalent to
     * java.util.function.Collector.accumulator().accept(A, R)
     */
    A accept(A resultContainer, R value);

    /**
     * Perform the final transformation from the intermediate accumulation type A to the final result type R. Equivalent
     * to R java.util.function.Collector.finisher().apply(A)
     */
    R merge(A resultContainer);
  }

  /** {@link Collector} that throws an exception if multiple values have to be merged. */
  static final class UniqueValueCollector<V> implements Collector<V, V>
  {
    private static final Collector<Object, Object> INSTANCE = new UniqueValueCollector<>();

    @SuppressWarnings("unchecked")
    static <V> Collector<V, V> getInstance()
    {
      return (Collector<V, V>) INSTANCE;
    }

    @Override
    public V get()
    {
      return null;
    }

    @Override
    public V accept(V previousValue, V value)
    {
      if (previousValue != null)
      {
        throw new IllegalArgumentException("Cannot accept multiple values (current=" + previousValue + ", new=" + value
            + ")");
      }
      return value;
    }

    @Override
    public V merge(V latestValue)
    {
      if (latestValue == null)
      {
        throw new IllegalArgumentException("No value to merge but expected one");
      }
      return latestValue;
    }
  }

  /**
   * {@link Collector} that accepts encoded {@link EntryIDSet} objects and
   * produces a {@link ByteString} representing the merged {@link EntryIDSet}.
   */
  static final class EntryIDsCollector implements Collector<LongArray, ByteString>
  {
    private final DefaultIndex index;
    private final int indexLimit;

    EntryIDsCollector(DefaultIndex index)
    {
      this.index = index;
      this.indexLimit = index.getIndexEntryLimit();
    }

    @Override
    public LongArray get()
    {
      return new LongArray();
    }

    @Override
    public LongArray accept(LongArray resultContainer, ByteString value)
    {
      if (resultContainer.size() < indexLimit)
      {
        resultContainer.add(index.importDecodeValue(value));
      }
      /*
       * else EntryIDSet is above index entry limits, discard additional values
       * to avoid blowing up memory now, then discard all entries in merge()
       */
      return resultContainer;
    }

    @Override
    public ByteString merge(LongArray resultContainer)
    {
      if (resultContainer.size() >= indexLimit)
      {
        return index.toValue(EntryIDSet.newUndefinedSet());
      }
      return index.toValue(EntryIDSet.newDefinedSet(resultContainer.get()));
    }
  }

  /** Simple long array primitive wrapper. */
  private static final class LongArray
  {
    private long[] values = new long[16];
    private int size;

    void add(long value)
    {
      if (size == values.length)
      {
        values = Arrays.copyOf(values, values.length * 2);
      }
      values[size++] = value;
    }

    int size()
    {
      return size;
    }

    long[] get()
    {
      values = Arrays.copyOf(values, size);
      Arrays.sort(values);
      return values;
    }
  }

  /**
   * {@link Collector} that accepts encoded {@link EntryIDSet} objects and produces a {@link ByteString} representing
   * the merged {@link EntryIDSet}.
   */
  static final class EntryIDSetsCollector implements Collector<Collection<ByteString>, ByteString>
  {
    private final DefaultIndex index;
    private final int indexLimit;

    EntryIDSetsCollector(DefaultIndex index)
    {
      this.index = index;
      this.indexLimit = index.getIndexEntryLimit();
    }

    @Override
    public Collection<ByteString> get()
    {
      // LinkedList is used for it's O(1) add method (while ArrayList is O(n) when resize is required).
      return new LinkedList<>();
    }

    @Override
    public Collection<ByteString> accept(Collection<ByteString> resultContainer, ByteString value)
    {
      if (resultContainer.size() < indexLimit)
      {
        resultContainer.add(value);
      }
      /*
       * else EntryIDSet is above index entry limits, discard additional values to avoid blowing up memory now, then
       * discard all entries in merge()
       */
      return resultContainer;
    }

    @Override
    public ByteString merge(Collection<ByteString> resultContainer)
    {
      if (resultContainer.size() >= indexLimit)
      {
        return index.toValue(EntryIDSet.newUndefinedSet());
      }
      else if (resultContainer.size() == 1)
      {
        // Avoids unnecessary decoding + encoding
        return resultContainer.iterator().next();
      }
      return index.toValue(buildEntryIDSet(resultContainer));
    }

    private EntryIDSet buildEntryIDSet(Collection<ByteString> encodedIDSets)
    {
      final List<EntryIDSet> idSets = new ArrayList<>(encodedIDSets.size());
      int mergedSize = 0;
      for(ByteString encodedIDSet :encodedIDSets) {
        final EntryIDSet entryIDSet = index.decodeValue(ByteString.empty(), encodedIDSet);
        mergedSize += entryIDSet.size();
        if (!entryIDSet.isDefined() || mergedSize >= indexLimit)
        {
          // above index entry limit
          return EntryIDSet.newUndefinedSet();
        }
        idSets.add(entryIDSet);
      }

      final long[] entryIDs = new long[mergedSize];
      int offset = 0;
      for(EntryIDSet idSet : idSets) {
        offset += idSet.copyTo(entryIDs, offset);
      }
      Arrays.sort(entryIDs);
      return EntryIDSet.newDefinedSet(entryIDs);
    }
  }

  private static MeteredCursor<ByteString, ByteString> asProgressCursor(
      SequentialCursor<ByteString, ByteString> delegate, String metricName, long totalSize)
  {
    return new MeteredSequentialCursorDecorator(delegate, metricName, totalSize);
  }

  /** Decorate {@link SequentialCursor} by providing progress information while cursoring. */
  private static final class MeteredSequentialCursorDecorator extends
      SequentialCursorDecorator<SequentialCursor<ByteString, ByteString>, ByteString, ByteString>implements
      MeteredCursor<ByteString, ByteString>
  {
    private final String metricName;
    private final long totalSize;
    private volatile long bytesRead;

    private MeteredSequentialCursorDecorator(SequentialCursor<ByteString, ByteString> delegate, String metricName,
        long totalSize)
    {
      super(delegate);
      this.metricName = metricName;
      this.totalSize = totalSize;
    }

    @Override
    public boolean next()
    {
      if (delegate.next())
      {
        bytesRead += delegate.getKey().length() + delegate.getValue().length();
        return true;
      }
      return false;
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getNbBytesRead()
    {
      return bytesRead;
    }

    @Override
    public String getMetricName()
    {
      return metricName;
    }

    @Override
    public long getNbBytesTotal()
    {
      return totalSize;
    }
  }

  /** Helper allowing to create {@link SequentialCursor} decorator without having to re-implement all methods. */
  static abstract class SequentialCursorDecorator<D extends SequentialCursor<K, V>, K, V> implements
      SequentialCursor<K, V>
  {
    protected final D delegate;

    SequentialCursorDecorator(D delegate)
    {
      this.delegate = delegate;
    }

    @Override
    public boolean next()
    {
      return delegate.next();
    }

    @Override
    public boolean isDefined()
    {
      return delegate.isDefined();
    }

    @Override
    public K getKey() throws NoSuchElementException
    {
      return delegate.getKey();
    }

    @Override
    public V getValue() throws NoSuchElementException
    {
      return delegate.getValue();
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      delegate.delete();
    }

    @Override
    public void close()
    {
      delegate.close();
    }
  }

  private static void visitIndexes(final EntryContainer entryContainer, IndexVisitor visitor)
  {
    for (AttributeIndex attribute : entryContainer.getAttributeIndexes())
    {
      for (MatchingRuleIndex index : attribute.getNameToIndexes().values())
      {
        visitor.visitAttributeIndex(index);
      }
    }
    for (VLVIndex index : entryContainer.getVLVIndexes())
    {
      visitor.visitVLVIndex(index);
    }
    visitor.visitSystemIndex(entryContainer.getDN2ID());
    visitor.visitSystemIndex(entryContainer.getID2ChildrenCount());
    visitor.visitSystemIndex(entryContainer.getDN2URI());
  }

  /** Visitor pattern allowing to process all type of indexes. */
  private interface IndexVisitor
  {
    void visitAttributeIndex(Index index);

    void visitVLVIndex(VLVIndex index);

    void visitSystemIndex(Tree index);
  }

  private static final IndexVisitor setTrust(boolean trustValue, Importer importer)
  {
    return setTrust(trustValue, asWriteableTransaction(importer));
  }

  private static final IndexVisitor setTrust(boolean trustValue, WriteableTransaction txn)
  {
    return new TrustModifier(txn, trustValue);
  }

  /** Update the trust state of the visited indexes. */
  private static final class TrustModifier implements IndexVisitor
  {
    private final WriteableTransaction txn;
    private final boolean trustValue;

    TrustModifier(WriteableTransaction txn, boolean trustValue)
    {
      this.txn = txn;
      this.trustValue = trustValue;
    }

    @Override
    public void visitAttributeIndex(Index index)
    {
      index.setTrusted(txn, trustValue);
    }

    @Override
    public void visitVLVIndex(VLVIndex index)
    {
      index.setTrusted(txn, trustValue);
    }

    @Override
    public void visitSystemIndex(Tree index)
    {
      // System indexes don't have trust status
    }
  }

  private static IndexVisitor deleteDatabase(Importer importer)
  {
    return new DeleteDatabase(importer);
  }

  /** Delete & recreate the database of the visited indexes. */
  private static final class DeleteDatabase implements IndexVisitor
  {
    private final Importer importer;

    DeleteDatabase(Importer importer)
    {
      this.importer = importer;
    }

    @Override
    public void visitAttributeIndex(Index index)
    {
      deleteTree(index);
    }

    @Override
    public void visitVLVIndex(VLVIndex index)
    {
      deleteTree(index);
    }

    @Override
    public void visitSystemIndex(Tree index)
    {
      deleteTree(index);
    }

    private void deleteTree(Tree index)
    {
      index.delete(asWriteableTransaction(importer));
    }
  }

  private static IndexVisitor visitOnlyDegraded(IndexVisitor delegate)
  {
    return new DegradedIndexFilter(delegate);
  }

  /** Visit indexes which are in a degraded state. */
  private static final class DegradedIndexFilter implements IndexVisitor
  {
    private final IndexVisitor delegate;

    DegradedIndexFilter(IndexVisitor delegate)
    {
      this.delegate = delegate;
    }

    @Override
    public void visitAttributeIndex(Index index)
    {
      if (!index.isTrusted())
      {
        delegate.visitAttributeIndex(index);
      }
    }

    @Override
    public void visitVLVIndex(VLVIndex index)
    {
      if (!index.isTrusted())
      {
        delegate.visitVLVIndex(index);
      }
    }

    @Override
    public void visitSystemIndex(Tree index)
    {
      // System indexes don't have trust status
    }
  }

  /** Maintain a list containing the names of the visited indexes. */
  private static final class SelectIndexName implements IndexVisitor
  {
    private final Set<String> indexNames;

    SelectIndexName()
    {
      this.indexNames = new HashSet<>();
    }

    public Set<String> getSelectedIndexNames()
    {
      return indexNames;
    }

    @Override
    public void visitAttributeIndex(Index index)
    {
      addIndex(index);
    }

    @Override
    public void visitVLVIndex(VLVIndex index)
    {
      addIndex(index);
    }

    @Override
    public void visitSystemIndex(Tree index)
    {
      addIndex(index);
    }

    private void addIndex(Tree index)
    {
      indexNames.add(index.getName().getIndexId());
    }
  }

  private static final IndexVisitor visitOnlyIndexes(Collection<String> indexNames, IndexVisitor delegate)
  {
    return new SpecificIndexFilter(delegate, indexNames);
  }

  /** Visit indexes only if their name match one contained in a list. */
  private static final class SpecificIndexFilter implements IndexVisitor
  {
    private final IndexVisitor delegate;
    private final Collection<String> indexNames;

    SpecificIndexFilter(IndexVisitor delegate, Collection<String> names)
    {
      this.delegate = delegate;
      this.indexNames = new HashSet<>(names.size());
      for(String indexName : names)
      {
        this.indexNames.add(indexName.toLowerCase());
      }
    }

    @Override
    public void visitAttributeIndex(Index index)
    {
      if (indexIncluded(index))
      {
        delegate.visitAttributeIndex(index);
      }
    }

    @Override
    public void visitVLVIndex(VLVIndex index)
    {
      if (indexIncluded(index))
      {
        delegate.visitVLVIndex(index);
      }
    }

    @Override
    public void visitSystemIndex(Tree index)
    {
      if (indexIncluded(index))
      {
        delegate.visitSystemIndex(index);
      }
    }

    private boolean indexIncluded(Tree index)
    {
      return indexNames.contains(index.getName().getIndexId().toLowerCase());
    }
  }

  /**
   * Thread-safe fixed-size cache which, once full, remove the least recently accessed entry. Composition is used here
   * to ensure that only methods generating entry-access in the LinkedHashMap are actually used. Otherwise, the least
   * recently used property of the cache would not be respected.
   */
  private static final class LRUPresenceCache<T>
  {
    private final Map<T, Object> cache;

    LRUPresenceCache(final int maxEntries)
    {
      // +1 because newly added entry is added before the least recently one is removed.
      this.cache = Collections.synchronizedMap(new LinkedHashMap<T, Object>(maxEntries + 1, 1.0f, true)
      {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<T, Object> eldest)
        {
          return size() >= maxEntries;
        }
      });
    }

    public boolean contains(T object)
    {
      return cache.get(object) != null;
    }

    public void add(T object)
    {
      cache.put(object, null);
    }
  }

  private static WriteableTransaction asWriteableTransaction(Importer importer)
  {
    return new ImporterToWriteableTransactionAdapter(importer);
  }

  /** Adapter allowing to use an {@link Importer} as a {@link WriteableTransaction}. */
  private static final class ImporterToWriteableTransactionAdapter implements WriteableTransaction
  {
    private final Importer importer;

    ImporterToWriteableTransactionAdapter(Importer importer)
    {
      this.importer = importer;
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      return importer.read(treeName, key);
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      importer.put(treeName, key, value);
    }

    @Override
    public boolean update(TreeName treeName, ByteSequence key, UpdateFunction f)
    {
      final ByteString value = importer.read(treeName, key);
      final ByteSequence newValue = f.computeNewValue(value);
      Reject.ifNull(newValue, "Importer cannot delete records.");
      if (!Objects.equals(value, newValue))
      {
        importer.put(treeName, key, newValue);
        return true;
      }
      return false;
    }

    @Override
    public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
    {
      return new SequentialCursorAdapter<>(importer.openCursor(treeName));
    }

    @Override
    public long getRecordCount(TreeName treeName)
    {
      long counter = 0;
      try (final SequentialCursor<ByteString, ByteString> cursor = importer.openCursor(treeName))
      {
        while (cursor.next())
        {
          counter++;
        }
      }
      return counter;
    }

    @Override
    public void openTree(TreeName name, boolean createOnDemand)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteTree(TreeName name)
    {
      importer.clearTree(name);
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      throw new UnsupportedOperationException();
    }
  }
}
