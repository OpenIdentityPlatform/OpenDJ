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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb.importLDIF;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.opends.server.util.StaticUtils.getFileForPath;
import org.opends.messages.Message;
import org.opends.messages.Category;
import org.opends.messages.Severity;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.admin.std.server.LocalDBIndexCfg;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn;
import org.opends.server.backends.jeb.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.*;
import com.sleepycat.je.*;
import com.sleepycat.util.PackedInteger;


/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
public class Importer
{
  private static final int TIMER_INTERVAL = 10000;
  final static int KB = 1024;
  private static final int MB =  (KB * KB);
  private static final String DEFAULT_TMP_DIR = "import-tmp";
  private static final String TMPENV_DIR = "tmp-env";

  //Defaults for DB cache.
  private static final int MAX_DB_CACHE_SIZE = 8 * MB;
  private static final int MAX_DB_LOG_SIZE = 10 * MB;

  //Defaults for LDIF reader buffers, min memory required to import and default
  //size for byte buffers.
  private static final int READER_WRITER_BUFFER_SIZE = 2 * MB;
  private static final int MIN_IMPORT_MEMORY_REQUIRED = 12 * MB;
  private static final int BYTE_BUFFER_CAPACITY = 128;

  //Min and MAX sizes of phase one buffer.
  private static final int MAX_BUFFER_SIZE = 48 * MB;
  private static final int MIN_BUFFER_SIZE = 64 * KB;

  //Min size of phase two read-ahead cache.
  private static final int MIN_READ_AHEAD_CACHE_SIZE = 1 * KB;

  //Set aside this much for the JVM from free memory.
  private static final int JVM_MEM_PCT = 45;

  //Percent of import memory to use for temporary environment if the
  //skip DN validation flag isn't specified.
  private static final int TMPENV_MEM_PCT = 50;
  //Small heap threshold used to give more memory to JVM to attempt OOM errors.
  private static final int SMALL_HEAP_SIZE = 256 * MB;

  //The DN attribute type.
  private static AttributeType dnType;

  //Comparators for DN and indexes respectively.
  private static final IndexBuffer.DNComparator dnComparator
          = new IndexBuffer.DNComparator();
  private static final IndexBuffer.IndexComparator indexComparator =
          new IndexBuffer.IndexComparator();

  //Phase one buffer and imported entries counts.
  private final AtomicInteger bufferCount = new AtomicInteger(0);
  private final AtomicLong importCount = new AtomicLong(0);

 //Phase one buffer size in bytes.
  private int bufferSize;

  //Temp scratch directory.
  private final File tempDir;

  //Index and thread counts.
  private final int indexCount, threadCount;

  //Set to true when validation is skipped.
  private final boolean skipDNValidation;

  //Temporary environment used when DN validation is done in first phase.
  private final TmpEnv tmpEnv;

  //Root container.
  private RootContainer rootContainer;

  //Import configuration.
  private final LDIFImportConfig importConfiguration;

  //LDIF reader.
  private LDIFReader reader;

  //Migrated entry count.
  private int migratedCount;

  //Size in bytes of temporary env and DB cache.
  private long tmpEnvCacheSize = 0, dbCacheSize = MAX_DB_CACHE_SIZE;

  //The executor service used for the buffer sort tasks.
  private ExecutorService bufferSortService;

  //The executor service used for the scratch file processing tasks.
  private ExecutorService scratchFileWriterService;

  //Queue of free index buffers -- used to re-cycle index buffers;
  private final BlockingQueue<IndexBuffer> freeBufferQueue =
          new LinkedBlockingQueue<IndexBuffer>();

  //Map of index keys to index buffers.  Used to allocate sorted
  //index buffers to a index writer thread.
  private final
  Map<IndexKey, BlockingQueue<IndexBuffer>> indexKeyQueMap =
          new ConcurrentHashMap<IndexKey, BlockingQueue<IndexBuffer>>();

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

  //Rebuld index manager used when rebuilding indexes.
  private final RebuildIndexManager rebuildManager;

  //Set to true if the backend was cleared.
  private boolean clearedBackend = false;

  //Used to shutdown import if an error occurs in phase one.
  private volatile boolean isPhaseOneCanceled = false;

  //Number of phase one buffers
  private int phaseOneBufferCount;

  static
  {
    if ((dnType = DirectoryServer.getAttributeType("dn")) == null)
    {
      dnType = DirectoryServer.getDefaultAttributeType("dn");
    }
  }

  //Rebuild-index instance.
  private
  Importer(RebuildConfig rebuildConfig, LocalDBBackendCfg cfg,
            EnvironmentConfig envConfig) throws IOException,
          InitializationException, JebException, ConfigException
  {
    importConfiguration = null;
    tmpEnv = null;
    threadCount = 1;
    rebuildManager = new RebuildIndexManager(rebuildConfig, cfg);
    indexCount = rebuildManager.getIndexCount();
    scratchFileWriterList = new ArrayList<ScratchFileWriterTask>(indexCount);
    scratchFileWriterFutures = new CopyOnWriteArrayList<Future<?>>();
    File parentDir;
    if(rebuildConfig.getTmpDirectory() == null)
    {
      parentDir = getFileForPath(DEFAULT_TMP_DIR);
    }
    else
    {
       parentDir = getFileForPath(rebuildConfig.getTmpDirectory());
    }
    tempDir = new File(parentDir, cfg.getBackendId());
    if(!tempDir.exists() && !tempDir.mkdirs())
    {
      Message message =
                ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(String.valueOf(tempDir));
      throw new IOException(message.toString());
    }
    if (tempDir.listFiles() != null)
    {
      for (File f : tempDir.listFiles())
      {
        f.delete();
      }
    }
    skipDNValidation = true;
    if(envConfig != null)
    {
      initializeDBEnv(envConfig);
    }
  }


  /**
   * Create a new import job with the specified ldif import config.
   *
   * @param importConfiguration The LDIF import configuration.
   * @param localDBBackendCfg The local DB back-end configuration.
   * @param envConfig The JEB environment config.
   * @throws IOException  If a problem occurs while opening the LDIF file for
   *                      reading.
   * @throws  InitializationException If a problem occurs during initialization.
   */
  private Importer(LDIFImportConfig importConfiguration,
                   LocalDBBackendCfg localDBBackendCfg,
                   EnvironmentConfig envConfig) throws IOException,
          InitializationException, DatabaseException
  {
    rebuildManager = null;
    this.importConfiguration = importConfiguration;
    if(importConfiguration.getThreadCount() == 0)
    {
      threadCount = Runtime.getRuntime().availableProcessors() * 2;
    }
    else
    {
      threadCount = importConfiguration.getThreadCount();
    }
    indexCount = localDBBackendCfg.listLocalDBIndexes().length + 2;
    if(!importConfiguration.appendToExistingData()) {
      if(importConfiguration.clearBackend() ||
              localDBBackendCfg.getBaseDN().size() <= 1) {
        clearedBackend = true;
      }
    }
    scratchFileWriterList = new ArrayList<ScratchFileWriterTask>(indexCount);
    scratchFileWriterFutures = new CopyOnWriteArrayList<Future<?>>();
    File parentDir;
    if(importConfiguration.getTmpDirectory() == null)
    {
      parentDir = getFileForPath(DEFAULT_TMP_DIR);
    }
    else
    {
       parentDir = getFileForPath(importConfiguration.getTmpDirectory());
    }
    tempDir = new File(parentDir, localDBBackendCfg.getBackendId());
    if(!tempDir.exists() && !tempDir.mkdirs())
    {
      Message message =
                ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(String.valueOf(tempDir));
      throw new IOException(message.toString());
    }
    if (tempDir.listFiles() != null)
    {
      for (File f : tempDir.listFiles())
      {
        f.delete();
      }
    }
    skipDNValidation = importConfiguration.getSkipDNValidation();
    initializeDBEnv(envConfig);
    //Set up temporary environment.
    if(!skipDNValidation)
    {
      File p = getFileForPath(localDBBackendCfg.getDBDirectory());
      File envPath = new File(p, TMPENV_DIR);
      envPath.mkdirs();
      tmpEnv = new TmpEnv(envPath);
    }
    else
    {
      tmpEnv = null;
    }
  }


  /**
   * Return and import LDIF instance using the specified arguments.
   *
   * @param importCfg The import config to use.
   * @param localDBBackendCfg The local DB backend config to use.
   * @param envCfg The JEB environment config to use.
   * @return A import LDIF instance.
   *
   * @throws IOException If an I/O error occurs.
   * @throws InitializationException If the instance cannot be initialized.
   */
  public static
  Importer getInstance(LDIFImportConfig importCfg,
                       LocalDBBackendCfg localDBBackendCfg,
                       EnvironmentConfig envCfg)
          throws IOException, InitializationException
  {
     return  new Importer(importCfg, localDBBackendCfg, envCfg);
  }


  /**
   * Return an import rebuild index instance using the specified arguments.
   *
   * @param rebuildCfg The rebuild config to use.
   * @param localDBBackendCfg The local DB backend config to use.
   * @param envCfg The JEB environment config to use.
   * @return An import rebuild index instance.
   *
   * @throws IOException If an I/O error occurs.
   * @throws InitializationException If the instance cannot be initialized.
   * @throws JebException If a JEB exception occurs.
   * @throws ConfigException If the instance cannot be configured.
   */
  public static synchronized
  Importer getInstance(RebuildConfig rebuildCfg,
                       LocalDBBackendCfg localDBBackendCfg,
                       EnvironmentConfig envCfg)
  throws IOException, InitializationException, JebException, ConfigException
  {
      return new Importer(rebuildCfg, localDBBackendCfg, envCfg);
  }


  private boolean getBufferSizes(long availMem)
  {
    boolean maxBuf = false;
    long memory = availMem - (MAX_DB_CACHE_SIZE + MAX_DB_LOG_SIZE);
    bufferSize = (int) (memory/ phaseOneBufferCount);
    if(bufferSize >= MIN_BUFFER_SIZE)
    {
      if(bufferSize > MAX_BUFFER_SIZE)
      {
        bufferSize = MAX_BUFFER_SIZE;
        maxBuf = true;
      }
    }
    else if(bufferSize < MIN_BUFFER_SIZE)
    {
      Message message =
              NOTE_JEB_IMPORT_LDIF_BUFF_SIZE_LESS_DEFAULT.get(MIN_BUFFER_SIZE);
      logError(message);
      bufferSize = MIN_BUFFER_SIZE;
    }
    return maxBuf;
  }


  /**
   * Return the suffix instance in the specified map that matches the specified
   * DN.
   *
   * @param dn The DN to search for.
   * @param map The map to search.
   * @return The suffix instance that matches the DN, or null if no match is
   *         found.
   */
  public static Suffix getMatchSuffix(DN dn, Map<DN, Suffix> map)
  {
    Suffix suffix = null;
    DN nodeDN = dn;

    while (suffix == null && nodeDN != null) {
      suffix = map.get(nodeDN);
      if (suffix == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }
    return suffix;
  }


  private long getTmpEnvironmentMemory(long availableMemoryImport)
  {
    int tmpMemPct = TMPENV_MEM_PCT;
    tmpEnvCacheSize = (availableMemoryImport * tmpMemPct) / 100;
    availableMemoryImport -= tmpEnvCacheSize;
    if(!clearedBackend)
    {
      long additionalDBCache = (tmpEnvCacheSize * 85) / 100;
      tmpEnvCacheSize -= additionalDBCache;
      dbCacheSize += additionalDBCache;
    }
    return availableMemoryImport;
  }


  //Used for large heap sizes when the buffer max size has been identified. Any
  //extra memory can be given to the temporary environment in that case.
  private void adjustTmpEnvironmentMemory(long availableMemoryImport)
  {
    long additionalMem = availableMemoryImport -
                         (phaseOneBufferCount * MAX_BUFFER_SIZE);
    tmpEnvCacheSize += additionalMem;
    if(!clearedBackend)
    {
      //The DN cache probably needs to be smaller and the DB cache bigger
      //because the dn2id is checked if the backend has not been cleared.
      long additionalDBCache = (tmpEnvCacheSize * 85) / 100;
      tmpEnvCacheSize -= additionalDBCache;
      dbCacheSize += additionalDBCache;
    }
  }


  /**
   * Calculate buffer sizes and initialize JEB properties based on memory.
   *
   * @param envConfig The environment config to use in the calculations.
   *
   * @throws InitializationException If a problem occurs during calculation.
   */
  private void initializeDBEnv(EnvironmentConfig envConfig)
          throws InitializationException
  {
      Message message;
      phaseOneBufferCount = 2 * (indexCount * threadCount);
      Runtime runTime = Runtime.getRuntime();
      long totFreeMemory = runTime.freeMemory() +
                            (runTime.maxMemory() - runTime.totalMemory());
      int importMemPct = (100 - JVM_MEM_PCT);
      if(totFreeMemory <= SMALL_HEAP_SIZE)
      {
        importMemPct -= 15;
      }
      if(rebuildManager != null)
      {
        importMemPct -= 15;
      }
      long availableMemoryImport = (totFreeMemory * importMemPct) / 100;
      if(!skipDNValidation)
      {
        availableMemoryImport = getTmpEnvironmentMemory(availableMemoryImport);
      }
      boolean maxBuffers = getBufferSizes(availableMemoryImport);
      if(!skipDNValidation && maxBuffers)
      {
        adjustTmpEnvironmentMemory(availableMemoryImport);
      }
      if (System.getProperty(PROPERTY_RUNNING_UNIT_TESTS) == null)
      {
          if (availableMemoryImport < MIN_IMPORT_MEMORY_REQUIRED)
          {
              message = ERR_IMPORT_LDIF_LACK_MEM.get(16);
              throw new InitializationException(message);
          }
      }
      message = NOTE_JEB_IMPORT_LDIF_TOT_MEM_BUF.get(availableMemoryImport,
              phaseOneBufferCount);
      logError(message);
      if(tmpEnvCacheSize > 0)
      {
         message = NOTE_JEB_IMPORT_LDIF_TMP_ENV_MEM.get(tmpEnvCacheSize);
        logError(message);
      }
      envConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "true");
      envConfig.setConfigParam(EnvironmentConfig.MAX_MEMORY,
                                                    Long.toString(dbCacheSize));
      message = NOTE_JEB_IMPORT_LDIF_DB_MEM_BUF_INFO.get(dbCacheSize,
                                                         bufferSize);
      logError(message);
      envConfig.setConfigParam(EnvironmentConfig.LOG_TOTAL_BUFFER_BYTES,
                               Long.toString(MAX_DB_LOG_SIZE));
      message = NOTE_JEB_IMPORT_LDIF_LOG_BYTES.get(MAX_DB_LOG_SIZE);
      logError(message);
  }


  private void initializeIndexBuffers()
  {
    for(int i = 0; i < phaseOneBufferCount; i++)
    {
      IndexBuffer b = IndexBuffer.createIndexBuffer(bufferSize);
      freeBufferQueue.add(b);
    }
  }


  private void initializeSuffixes() throws DatabaseException, JebException,
           ConfigException, InitializationException
  {
    for(EntryContainer ec : rootContainer.getEntryContainers())
    {
      Suffix suffix = getSuffix(ec);
      if(suffix != null)
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
    for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
            suffix.getAttrIndexMap().entrySet()) {
      AttributeIndex attributeIndex = mapEntry.getValue();
      DatabaseContainer container;
      if((container=attributeIndex.getEqualityIndex()) != null) {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if((container=attributeIndex.getPresenceIndex()) != null) {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if((container=attributeIndex.getSubstringIndex()) != null) {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if((container=attributeIndex.getOrderingIndex()) != null) {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      if((container=attributeIndex.getApproximateIndex()) != null) {
        int id = System.identityHashCode(container);
        idContainerMap.putIfAbsent(id, container);
      }
      Map<String,Collection<Index>> extensibleMap =
              attributeIndex.getExtensibleIndexes();
      if(!extensibleMap.isEmpty()) {
        Collection<Index> subIndexes =
                attributeIndex.getExtensibleIndexes().get(
                        EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if(subIndexes != null) {
          for(DatabaseContainer subIndex : subIndexes) {
            int id = System.identityHashCode(subIndex);
            idContainerMap.putIfAbsent(id, subIndex);
          }
        }
        Collection<Index> sharedIndexes =
                attributeIndex.getExtensibleIndexes().get(
                        EXTENSIBLE_INDEXER_ID_SHARED);
        if(sharedIndexes !=null) {
          for(DatabaseContainer sharedIndex : sharedIndexes) {
            int id = System.identityHashCode(sharedIndex);
            idContainerMap.putIfAbsent(id, sharedIndex);
          }
        }
      }
    }
  }


  private Suffix getSuffix(EntryContainer entryContainer)
     throws DatabaseException, JebException, ConfigException,
            InitializationException {
   DN baseDN = entryContainer.getBaseDN();
   EntryContainer sourceEntryContainer = null;
   List<DN> includeBranches = new ArrayList<DN>();
   List<DN> excludeBranches = new ArrayList<DN>();

   if(!importConfiguration.appendToExistingData() &&
       !importConfiguration.clearBackend())
   {
     for(DN dn : importConfiguration.getExcludeBranches())
     {
       if(baseDN.equals(dn))
       {
         // This entire base DN was explicitly excluded. Skip.
         return null;
       }
       if(baseDN.isAncestorOf(dn))
       {
         excludeBranches.add(dn);
       }
     }

     if(!importConfiguration.getIncludeBranches().isEmpty())
     {
       for(DN dn : importConfiguration.getIncludeBranches())
       {
         if(baseDN.isAncestorOf(dn))
         {
           includeBranches.add(dn);
         }
       }

       if(includeBranches.isEmpty())
       {
           /*
            There are no branches in the explicitly defined include list under
            this base DN. Skip this base DN all together.
           */

           return null;
       }

       // Remove any overlapping include branches.
       Iterator<DN> includeBranchIterator = includeBranches.iterator();
       while(includeBranchIterator.hasNext())
       {
         DN includeDN = includeBranchIterator.next();
         boolean keep = true;
         for(DN dn : includeBranches)
         {
           if(!dn.equals(includeDN) && dn.isAncestorOf(includeDN))
           {
             keep = false;
             break;
           }
         }
         if(!keep)
         {
           includeBranchIterator.remove();
         }
       }

       // Remove any exclude branches that are not are not under a include
       // branch since they will be migrated as part of the existing entries
       // outside of the include branches anyways.
       Iterator<DN> excludeBranchIterator = excludeBranches.iterator();
       while(excludeBranchIterator.hasNext())
       {
         DN excludeDN = excludeBranchIterator.next();
         boolean keep = false;
         for(DN includeDN : includeBranches)
         {
           if(includeDN.isAncestorOf(excludeDN))
           {
             keep = true;
             break;
           }
         }
         if(!keep)
         {
           excludeBranchIterator.remove();
         }
       }

       if(includeBranches.size() == 1 && excludeBranches.size() == 0 &&
           includeBranches.get(0).equals(baseDN))
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
             rootContainer.openEntryContainer(baseDN,
                                              baseDN.toNormalizedString() +
                                                  "_importTmp");
       }
     }
   }
   return Suffix.createSuffixContext(entryContainer, sourceEntryContainer,
                                     includeBranches, excludeBranches);
 }


  /**
   * Rebuild the indexes using the specified rootcontainer.
   *
   * @param rootContainer The rootcontainer to rebuild indexes in.
   *
   * @throws ConfigException If a configuration error occurred.
   * @throws InitializationException If an initialization error occurred.
   * @throws IOException If an IO error occurred.
   * @throws JebException If the JEB database had an error.
   * @throws DatabaseException If a database error occurred.
   * @throws InterruptedException If an interrupted error occurred.
   * @throws ExecutionException If an execution error occurred.
   */
  public void
  rebuildIndexes(RootContainer rootContainer) throws ConfigException,
          InitializationException, IOException, JebException, DatabaseException,
          InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    long startTime = System.currentTimeMillis();
    rebuildManager.initialize();
    rebuildManager.printStartMessage();
    rebuildManager.rebuldIndexes();
    tempDir.delete();
    rebuildManager.printStopMessage(startTime);
  }


  /**
   * Import a LDIF using the specified root container.
   *
   * @param rootContainer The root container to use during the import.
   *
   * @return A LDIF result.
   * @throws ConfigException If the import failed because of an configuration
   *                         error.
   * @throws IOException If the import failed because of an IO error.
   * @throws InitializationException If the import failed because of an
   *               initialization error.
   * @throws JebException If the import failed due to a database error.
   * @throws InterruptedException If the import failed due to an interrupted
   *                              error.
   * @throws ExecutionException If the import failed due to an execution error.
   * @throws DatabaseException If the import failed due to a database error.
   */
  public LDIFImportResult
  processImport(RootContainer rootContainer) throws ConfigException,
          InitializationException, IOException, JebException, DatabaseException,
          InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    reader = new LDIFReader(importConfiguration, rootContainer,
                                 READER_WRITER_BUFFER_SIZE);
    try
    {
      Message message =
              NOTE_JEB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
                      BUILD_ID, REVISION_NUMBER);
      logError(message);
      message = NOTE_JEB_IMPORT_THREAD_COUNT.get(threadCount);
      logError(message);
      initializeSuffixes();
      long startTime = System.currentTimeMillis();
      phaseOne();
      long phaseOneFinishTime = System.currentTimeMillis();
      if(!skipDNValidation)
      {
         tmpEnv.shutdown();
      }
      if(isPhaseOneCanceled)
      {
        throw new InterruptedException("Import processing canceled.");
      }
      long phaseTwoTime = System.currentTimeMillis();
      phaseTwo();
      long phaseTwoFinishTime = System.currentTimeMillis();
      setIndexesTrusted();
      switchContainers();
      tempDir.delete();
      long finishTime = System.currentTimeMillis();
      long importTime = (finishTime - startTime);
      float rate = 0;
      message = NOTE_JEB_IMPORT_PHASE_STATS.get(importTime/1000,
                        (phaseOneFinishTime - startTime)/1000,
                        (phaseTwoFinishTime - phaseTwoTime)/1000);
      logError(message);
      if (importTime > 0)
        rate = 1000f * reader.getEntriesRead() / importTime;
        message = NOTE_JEB_IMPORT_FINAL_STATUS.get(reader.getEntriesRead(),
                  importCount.get(), reader.getEntriesIgnored(),
                  reader.getEntriesRejected(), migratedCount,
                  importTime / 1000, rate);
      logError(message);
    }
    finally
    {
      reader.close();
    }
    return new LDIFImportResult(reader.getEntriesRead(), reader
            .getEntriesRejected(), reader.getEntriesIgnored());
  }


  private void switchContainers()
    throws DatabaseException, JebException, InitializationException
  {

     for(Suffix suffix : dnSuffixMap.values()) {
       DN baseDN = suffix.getBaseDN();
       EntryContainer entryContainer =
               suffix.getSrcEntryContainer();
       if(entryContainer != null) {
         EntryContainer needRegisterContainer =
                 rootContainer.unregisterEntryContainer(baseDN);
         //Make sure the unregistered EC for the base DN is the same as
         //the one in the import context.
         if(needRegisterContainer != needRegisterContainer) {
           rootContainer.registerEntryContainer(baseDN, needRegisterContainer);
           continue;
         }
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



  private void setIndexesTrusted() throws JebException
  {
    try {
      for(Suffix s : dnSuffixMap.values()) {
        s.setIndexesTrusted();
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
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(progressTask, TIMER_INTERVAL, TIMER_INTERVAL);
    scratchFileWriterService = Executors.newFixedThreadPool(2 * indexCount);
    bufferSortService = Executors.newFixedThreadPool(threadCount);
    ExecutorService execService = Executors.newFixedThreadPool(threadCount);
    List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
    tasks.add(new MigrateExistingTask());
    List<Future<Void>> results = execService.invokeAll(tasks);
    for (Future<Void> result : results) {
      if(!result.isDone()) {
        result.get();
      }
    }
    tasks.clear();
    results.clear();
    if (importConfiguration.appendToExistingData() &&
            importConfiguration.replaceExistingEntries())
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
      if(!result.isDone()) {
        result.get();
      }
    tasks.clear();
    results.clear();
    tasks.add(new MigrateExcludedTask());
    results = execService.invokeAll(tasks);
    for (Future<Void> result : results)
      if(!result.isDone()) {
        result.get();
      }
    stopScratchFileWriters();
    for (Future<?> result : scratchFileWriterFutures)
    {
     if(!result.isDone()) {
        result.get();
      }
    }
    //Try to clear as much memory as possible.
    scratchFileWriterList.clear();
    scratchFileWriterFutures.clear();
    indexKeyQueMap.clear();
    execService.shutdown();
    freeBufferQueue.clear();
    bufferSortService.shutdown();
    scratchFileWriterService.shutdown();
    timer.cancel();
  }



  private void phaseTwo() throws InterruptedException, JebException,
          ExecutionException
  {
    SecondPhaseProgressTask progress2Task =
            new SecondPhaseProgressTask(reader.getEntriesRead());
    Timer timer2 = new Timer();
    timer2.scheduleAtFixedRate(progress2Task, TIMER_INTERVAL, TIMER_INTERVAL);
    processIndexFiles();
    timer2.cancel();
  }


  private int getBufferCount(int dbThreads)
  {
    int buffers = 0;

    List<IndexManager> totList = new LinkedList<IndexManager>(DNIndexMgrList);
    totList.addAll(indexMgrList);
    Collections.sort(totList, Collections.reverseOrder());
    int limit = Math.min(dbThreads, totList.size());
    for(int i = 0; i < limit; i ++)
    {
      buffers += totList.get(i).getBufferList().size();
    }
    return buffers;
  }


  private void processIndexFiles() throws InterruptedException,
          JebException, ExecutionException
  {
    ExecutorService dbService;
    if(bufferCount.get() == 0)
    {
      return;
    }
    int dbThreads = Runtime.getRuntime().availableProcessors();
    if(dbThreads < 4)
    {
      dbThreads = 4;
    }
    int readAheadSize =  cacheSizeFromFreeMemory(getBufferCount(dbThreads));
    List<Future<Void>> futures = new LinkedList<Future<Void>>();
    dbService = Executors.newFixedThreadPool(dbThreads);
    //Start DN processing first.
    for(IndexManager dnMgr : DNIndexMgrList)
    {
      futures.add(dbService.submit(new IndexDBWriteTask(dnMgr, readAheadSize)));
    }
    for(IndexManager mgr : indexMgrList)
    {
       futures.add(dbService.submit(new IndexDBWriteTask(mgr, readAheadSize)));
    }
    for (Future<Void> result : futures)
      if(!result.isDone()) {
        result.get();
      }
    dbService.shutdown();
  }


  private int cacheSizeFromFreeMemory(int buffers)
  {
    Runtime runTime = Runtime.getRuntime();
    runTime.gc();
    runTime.gc();
    long freeMemory = runTime.freeMemory();
    long maxMemory = runTime.maxMemory();
    long totMemory = runTime.totalMemory();
    long totFreeMemory = (freeMemory + (maxMemory - totMemory));
    int importMemPct = (100 - JVM_MEM_PCT);
    //For very small heaps, give more memory to the JVM.
    if(totFreeMemory <= SMALL_HEAP_SIZE)
    {
        importMemPct -= 35;
    }
    long availableMemory = (totFreeMemory * importMemPct) / 100;
    int averageBufferSize = (int)(availableMemory /buffers);
    int cacheSize = Math.max(MIN_READ_AHEAD_CACHE_SIZE, averageBufferSize);
    //Cache size is never larger than the buffer size.
    if(cacheSize > bufferSize)
    {
      cacheSize = bufferSize;
    }
    Message message =
     NOTE_JEB_IMPORT_LDIF_PHASE_TWO_MEM_REPORT.get(availableMemory,
                                                   cacheSize, buffers);
    logError(message);
    return cacheSize;
  }


  private void stopScratchFileWriters()
  {
    IndexBuffer indexBuffer = IndexBuffer.createIndexBuffer(0);
    for(ScratchFileWriterTask task : scratchFileWriterList)
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
    public Void call() throws Exception
    {
      for(Suffix suffix : dnSuffixMap.values()) {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if(entryContainer != null &&
                !suffix.getExcludeBranches().isEmpty()) {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          OperationStatus status;
          Message message = NOTE_JEB_IMPORT_MIGRATION_START.get(
                  "excluded", String.valueOf(suffix.getBaseDN()));
          logError(message);
          Cursor cursor =
                  entryContainer.getDN2ID().openCursor(null,
                          CursorConfig.READ_COMMITTED);
          Comparator<byte[]> comparator =
                  entryContainer.getDN2ID().getComparator();
          try {
            for(DN excludedDN : suffix.getExcludeBranches()) {
              byte[] bytes =
                      StaticUtils.getBytes(excludedDN.toNormalizedString());
              key.setData(bytes);
              status = cursor.getSearchKeyRange(key, data, lockMode);
              if(status == OperationStatus.SUCCESS &&
                      Arrays.equals(key.getData(), bytes)) {
                // This is the base entry for a branch that was excluded in the
                // import so we must migrate all entries in this branch over to
                // the new entry container.
                byte[] end = StaticUtils.getBytes("," +
                                excludedDN.toNormalizedString());
                end[0] = (byte) (end[0] + 1);

                while(status == OperationStatus.SUCCESS &&
                      comparator.compare(key.getData(), end) < 0 &&
                      !importConfiguration.isCancelled() &&
                      !isPhaseOneCanceled) {
                  EntryID id = new EntryID(data);
                  Entry entry = entryContainer.getID2Entry().get(null,
                          id, LockMode.DEFAULT);
                  processEntry(entry, rootContainer.getNextEntryID(),
                          suffix);
                  migratedCount++;
                  status = cursor.getNext(key, data, lockMode);
                }
              }
            }
            cursor.close();
            flushIndexBuffers();
          }
          catch (Exception e)
          {
            message =
              ERR_JEB_IMPORT_LDIF_MIGRATE_EXCLUDED_TASK_ERR.get(e.getMessage());
            logError(message);
            isPhaseOneCanceled =true;
            throw e;
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
    public Void call() throws Exception
    {
      for(Suffix suffix : dnSuffixMap.values()) {
        EntryContainer entryContainer = suffix.getSrcEntryContainer();
        if(entryContainer != null &&
                !suffix.getIncludeBranches().isEmpty()) {
          DatabaseEntry key = new DatabaseEntry();
          DatabaseEntry data = new DatabaseEntry();
          LockMode lockMode = LockMode.DEFAULT;
          OperationStatus status;
          Message message = NOTE_JEB_IMPORT_MIGRATION_START.get(
                  "existing", String.valueOf(suffix.getBaseDN()));
          logError(message);
          Cursor cursor =
                  entryContainer.getDN2ID().openCursor(null,
                          null);
          try {
            status = cursor.getFirst(key, data, lockMode);
            while(status == OperationStatus.SUCCESS &&
                    !importConfiguration.isCancelled() && !isPhaseOneCanceled) {
              DN dn = DN.decode(ByteString.wrap(key.getData()));
              if(!suffix.getIncludeBranches().contains(dn)) {
                EntryID id = new EntryID(data);
                Entry entry =
                        entryContainer.getID2Entry().get(null,
                                id, LockMode.DEFAULT);
                processEntry(entry, rootContainer.getNextEntryID(),suffix);
                migratedCount++;
                status = cursor.getNext(key, data, lockMode);
              }  else {
                // This is the base entry for a branch that will be included
                // in the import so we don't want to copy the branch to the
                //  new entry container.

                /**
                 * Advance the cursor to next entry at the same level in the
                 * DIT
                 * skipping all the entries in this branch.
                 * Set the next starting value to a value of equal length but
                 * slightly greater than the previous DN. Since keys are
                 * compared in reverse order we must set the first byte
                 * (the comma).
                 * No possibility of overflow here.
                 */
                byte[] begin =
                        StaticUtils.getBytes("," + dn.toNormalizedString());
                begin[0] = (byte) (begin[0] + 1);
                key.setData(begin);
                status = cursor.getSearchKeyRange(key, data, lockMode);
              }
            }
            cursor.close();
            flushIndexBuffers();
          }
          catch(Exception e)
          {
            message =
              ERR_JEB_IMPORT_LDIF_MIGRATE_EXISTING_TASK_ERR.get(e.getMessage());
            logError(message);
            isPhaseOneCanceled =true;
            throw e;
          }
        }
      }
      return null;
    }
  }

  /**
   * Task to perform append/replace processing.
   */
  private  class AppendReplaceTask extends ImportTask
  {
    private final Set<byte[]> insertKeySet = new HashSet<byte[]>(),
                              deleteKeySet = new HashSet<byte[]>();
    private final EntryInformation entryInfo = new EntryInformation();
    private Entry oldEntry;
    private EntryID entryID;


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isPhaseOneCanceled)
          {
            IndexBuffer indexBuffer = IndexBuffer.createIndexBuffer(0);
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
      catch(Exception e)
      {
        Message message =
                ERR_JEB_IMPORT_LDIF_APPEND_REPLACE_TASK_ERR.get(e.getMessage());
        logError(message);
        isPhaseOneCanceled = true;
        throw e;
      }
      return null;
    }


    void processEntry(Entry entry, Suffix suffix)
            throws DatabaseException, ConfigException, DirectoryException,
            JebException, InterruptedException

    {
      DN entryDN = entry.getDN();
      DN2ID dn2id = suffix.getDN2ID();
      EntryID oldID = dn2id.get(null, entryDN, LockMode.DEFAULT);
      if(oldID != null)
      {
        oldEntry = suffix.getID2Entry().get(null, oldID, LockMode.DEFAULT);
      }
      if(oldEntry == null)
      {
        if(!skipDNValidation)
        {
          if(!dnSanityCheck(entryDN, entry, suffix))
          {
            suffix.removePending(entryDN);
            return;
          }
          suffix.removePending(entryDN);
        }
        else
        {
          processDN2ID(suffix, entryDN, entryID);
          suffix.removePending(entryDN);
        }
      }
      else
      {
        suffix.removePending(entryDN);
        entryID = oldID;
      }
      processDN2URI(suffix, oldEntry, entry);
      suffix.getID2Entry().put(null, entryID, entry);
      if(oldEntry == null)
      {
        processIndexes(suffix, entry, entryID);
      }
      else
      {
        processAllIndexes(suffix, entry, entryID);
      }
    }


    void
    processAllIndexes(Suffix suffix, Entry entry, EntryID entryID) throws
            DatabaseException, DirectoryException, JebException,
            ConfigException, InterruptedException
    {

      for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
              suffix.getAttrIndexMap().entrySet()) {
        AttributeType attributeType = mapEntry.getKey();
          AttributeIndex attributeIndex = mapEntry.getValue();
          Index index;
          if((index=attributeIndex.getEqualityIndex()) != null) {
            processAttribute(index, entry, entryID,
            new IndexKey(attributeType, ImportIndexType.EQUALITY,
                         index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getPresenceIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.PRESENCE,
                                   index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getSubstringIndex()) != null) {
            processAttribute(index, entry, entryID,
                new IndexKey(attributeType, ImportIndexType.SUBSTRING,
                             index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getOrderingIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.ORDERING,
                                  index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getApproximateIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.APPROXIMATE,
                                   index.getIndexEntryLimit()));
          }
          for(VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes()) {
            Transaction transaction = null;
            vlvIdx.addEntry(transaction, entryID, entry);
          }
          Map<String,Collection<Index>> extensibleMap =
                  attributeIndex.getExtensibleIndexes();
          if(!extensibleMap.isEmpty()) {
            Collection<Index> subIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SUBSTRING);
            if(subIndexes != null) {
              for(Index subIndex: subIndexes) {
                processAttribute(subIndex, entry, entryID,
                     new IndexKey(attributeType, ImportIndexType.EX_SUBSTRING,
                                  subIndex.getIndexEntryLimit()));
              }
            }
            Collection<Index> sharedIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SHARED);
            if(sharedIndexes !=null) {
              for(Index sharedIndex:sharedIndexes) {
                processAttribute(sharedIndex, entry, entryID,
                       new IndexKey(attributeType, ImportIndexType.EX_SHARED,
                                    sharedIndex.getIndexEntryLimit()));
              }
            }
          }
      }
    }


    void processAttribute(Index index, Entry entry, EntryID entryID,
                   IndexKey indexKey) throws DatabaseException,
            ConfigException, InterruptedException
    {
      if(oldEntry != null)
      {
        deleteKeySet.clear();
        index.indexer.indexEntry(oldEntry, deleteKeySet);
        for(byte[] delKey : deleteKeySet)
        {
          processKey(index, delKey, entryID, indexComparator, indexKey, false);
        }
      }
      insertKeySet.clear();
      index.indexer.indexEntry(entry, insertKeySet);
      for(byte[] key : insertKeySet)
      {
        processKey(index, key, entryID, indexComparator, indexKey, true);
      }
    }
  }


  /**
   * This task performs phase reading and processing of the entries read from
   * the LDIF file(s). This task is used if the append flag wasn't specified.
   */
  private  class ImportTask implements Callable<Void>
  {
    private final Map<IndexKey, IndexBuffer> indexBufferMap =
                                     new HashMap<IndexKey, IndexBuffer>();
    private final Set<byte[]> insertKeySet = new HashSet<byte[]>();
    private final EntryInformation entryInfo = new EntryInformation();
    private DatabaseEntry keyEntry = new DatabaseEntry(),
                          valEntry = new DatabaseEntry();


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      try
      {
        while (true)
        {
          if (importConfiguration.isCancelled() || isPhaseOneCanceled)
          {
            IndexBuffer indexBuffer = IndexBuffer.createIndexBuffer(0);
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
        isPhaseOneCanceled = true;
        throw e;
      }
      return null;
    }


    void processEntry(Entry entry, EntryID entryID, Suffix suffix)
            throws DatabaseException, ConfigException, DirectoryException,
            JebException, InterruptedException

    {
      DN entryDN = entry.getDN();
      if(!skipDNValidation)
      {
        if(!dnSanityCheck(entryDN, entry, suffix))
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
      //If the backend was not cleared, then the dn2id needs to checked first
      //for DNs that might not exist in the DN cache. If the DN is not in
      //the suffixes dn2id DB, then the dn cache is used.
      if(!clearedBackend)
      {
        EntryID id = suffix.getDN2ID().get(null, entryDN, LockMode.DEFAULT);
        if(id != null || !tmpEnv.insert(entryDN, keyEntry, valEntry) )
        {
          Message message = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
          reader.rejectEntry(entry, message);
          return false;
        }
      }
      else if(!tmpEnv.insert(entryDN, keyEntry, valEntry))
      {
          Message message = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
          reader.rejectEntry(entry, message);
          return false;
      }
      //Perform parent checking.
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      if (parentDN != null) {
        if (!suffix.isParentProcessed(parentDN, tmpEnv, clearedBackend)) {
          Message message =
                  ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
          reader.rejectEntry(entry, message);
          return false;
        }
      }
      return true;
    }


    void
    processIndexes(Suffix suffix, Entry entry, EntryID entryID) throws
            DatabaseException, DirectoryException, JebException,
            ConfigException, InterruptedException
    {
      for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
              suffix.getAttrIndexMap().entrySet()) {
        AttributeType attributeType = mapEntry.getKey();
        if(entry.hasAttribute(attributeType)) {
          AttributeIndex attributeIndex = mapEntry.getValue();
          Index index;
          if((index=attributeIndex.getEqualityIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.EQUALITY,
                                   index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getPresenceIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.PRESENCE,
                                   index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getSubstringIndex()) != null) {
            processAttribute(index, entry, entryID,
                new IndexKey(attributeType, ImportIndexType.SUBSTRING,
                             index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getOrderingIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.ORDERING,
                                   index.getIndexEntryLimit()));
          }
          if((index=attributeIndex.getApproximateIndex()) != null) {
            processAttribute(index, entry, entryID,
                      new IndexKey(attributeType, ImportIndexType.APPROXIMATE,
                                   index.getIndexEntryLimit()));
          }
          for(VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes()) {
            Transaction transaction = null;
            vlvIdx.addEntry(transaction, entryID, entry);
          }
          Map<String,Collection<Index>> extensibleMap =
                  attributeIndex.getExtensibleIndexes();
          if(!extensibleMap.isEmpty()) {
            Collection<Index> subIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SUBSTRING);
            if(subIndexes != null) {
              for(Index subIndex: subIndexes) {
                processAttribute(subIndex, entry, entryID,
                     new IndexKey(attributeType, ImportIndexType.EX_SUBSTRING,
                                  subIndex.getIndexEntryLimit()));
              }
            }
            Collection<Index> sharedIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SHARED);
            if(sharedIndexes !=null) {
              for(Index sharedIndex:sharedIndexes) {
                processAttribute(sharedIndex, entry, entryID,
                        new IndexKey(attributeType, ImportIndexType.EX_SHARED,
                                     sharedIndex.getIndexEntryLimit()));
              }
            }
          }
        }
      }
    }



   void processAttribute(Index index, Entry entry, EntryID entryID,
                           IndexKey indexKey) throws DatabaseException,
            ConfigException, InterruptedException
    {
      insertKeySet.clear();
      index.indexer.indexEntry(entry, insertKeySet);
      for(byte[] key : insertKeySet)
      {
        processKey(index, key, entryID, indexComparator, indexKey, true);
      }
    }


    void flushIndexBuffers() throws InterruptedException,
                 ExecutionException
    {
       Set<Map.Entry<IndexKey, IndexBuffer>> set = indexBufferMap.entrySet();
       Iterator<Map.Entry<IndexKey, IndexBuffer>> setIterator = set.iterator();
       while(setIterator.hasNext())
        {
          Map.Entry<IndexKey, IndexBuffer> e = setIterator.next();
          IndexKey indexKey = e.getKey();
          IndexBuffer indexBuffer = e.getValue();
          setIterator.remove();
          ImportIndexType indexType = indexKey.getIndexType();
          if(indexType.equals(ImportIndexType.DN))
          {
            indexBuffer.setComparator(dnComparator);
          }
          else
          {
            indexBuffer.setComparator(indexComparator);
          }
          indexBuffer.setIndexKey(indexKey);
          indexBuffer.setDiscard();
          Future<Void> future =
                  bufferSortService.submit(new SortTask(indexBuffer));
          future.get();
        }
    }


    int
    processKey(DatabaseContainer container, byte[] key, EntryID entryID,
         IndexBuffer.ComparatorBuffer<byte[]> comparator, IndexKey indexKey,
         boolean insert)
         throws ConfigException, InterruptedException
    {
      IndexBuffer indexBuffer;
      if(!indexBufferMap.containsKey(indexKey))
      {
        indexBuffer = getNewIndexBuffer();
        indexBufferMap.put(indexKey, indexBuffer);
      }
      else
      {
        indexBuffer = indexBufferMap.get(indexKey);
      }
      if(!indexBuffer.isSpaceAvailable(key, entryID.longValue()))
      {
        indexBuffer.setComparator(comparator);
        indexBuffer.setIndexKey(indexKey);
        bufferSortService.submit(new SortTask(indexBuffer));
        indexBuffer = getNewIndexBuffer();
        indexBufferMap.remove(indexKey);
        indexBufferMap.put(indexKey, indexBuffer);
      }
      int id = System.identityHashCode(container);
      indexBuffer.add(key, entryID, id, insert);
      return id;
    }


    IndexBuffer getNewIndexBuffer() throws ConfigException, InterruptedException
    {
      IndexBuffer indexBuffer = freeBufferQueue.take();
        if(indexBuffer == null)
        {
         Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                                       "Index buffer processing error.");
           throw new InterruptedException(message.toString());
        }
        if(indexBuffer.isPoison())
        {
          Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                  "Cancel processing received.");
          throw new InterruptedException(message.toString());
        }
      return indexBuffer;
    }


    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
            throws ConfigException, InterruptedException
    {
      DatabaseContainer dn2id = suffix.getDN2ID();
      byte[] dnBytes = StaticUtils.getBytes(dn.toNormalizedString());
      int id = processKey(dn2id, dnBytes, entryID, dnComparator,
                 new IndexKey(dnType, ImportIndexType.DN, 1), true);
      idECMap.putIfAbsent(id, suffix.getEntryContainer());
    }


    void processDN2URI(Suffix suffix, Entry oldEntry, Entry newEntry)
            throws DatabaseException
    {
      DN2URI dn2uri = suffix.getDN2URI();
      if(oldEntry != null)
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
   * processes the records and writes the results to the index database. The
   * DN index is treated differently then non-DN indexes.
   */
  private final class IndexDBWriteTask implements Callable<Void>
  {
    private final IndexManager indexMgr;
    private final DatabaseEntry dbKey, dbValue;
    private final int cacheSize;
    private final Map<Integer, DNState> dnStateMap =
                                               new HashMap<Integer, DNState>();
    private final Map<Integer, Index> indexMap = new HashMap<Integer, Index>();


    public IndexDBWriteTask(IndexManager indexMgr, int cacheSize)
    {
      this.indexMgr = indexMgr;
      this.dbKey = new DatabaseEntry();
      this.dbValue = new DatabaseEntry();
      this.cacheSize = cacheSize;
    }


    private SortedSet<Buffer> initializeBuffers() throws IOException
    {
      SortedSet<Buffer> bufferSet = new TreeSet<Buffer>();
      for(Buffer b : indexMgr.getBufferList())
      {
        b.initializeCache(indexMgr, null, cacheSize);
        bufferSet.add(b);
      }
      indexMgr.getBufferList().clear();
      return bufferSet;
    }


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      ByteBuffer cKey = null;
      ImportIDSet cInsertIDSet =  new ImportIDSet(),
                  cDeleteIDSet =  new ImportIDSet();
      Thread.setDefaultUncaughtExceptionHandler(
             new DefaultExceptionHandler());
      indexMgr.setStarted();
      Message message =
              NOTE_JEB_IMPORT_LDIF_INDEX_STARTED.get(indexMgr.getFileName(),
                         indexMgr.getBufferList().size());
      logError(message);
      Integer cIndexID = null;
      try
      {
        indexMgr.openIndexFile();
        SortedSet<Buffer> bufferSet = initializeBuffers();
        while(!bufferSet.isEmpty())
        {
          Buffer b;
          b = bufferSet.first();
          bufferSet.remove(b);
          if(cKey == null)
          {
            cKey = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);
            cIndexID =  b.getIndexID();
            cKey.clear();
            if(b.getKeyLen() > cKey.capacity())
            {
              cKey = ByteBuffer.allocate(b.getKeyLen());
            }
            cKey.flip();
            b.getKey(cKey);
            cInsertIDSet.merge(b.getInsertIDSet());
            cDeleteIDSet.merge(b.getDeleteIDSet());
            cInsertIDSet.setKey(cKey);
            cDeleteIDSet.setKey(cKey);
          }
          else
          {
            if(b.compare(cKey, cIndexID) != 0)
            {
              addToDB(cInsertIDSet, cDeleteIDSet, cIndexID);
              indexMgr.incrementKeyCount();
              cIndexID =  b.getIndexID();
              cKey.clear();
              if(b.getKeyLen() > cKey.capacity())
              {
                 cKey = ByteBuffer.allocate(b.getKeyLen());
              }
              cKey.flip();
              b.getKey(cKey);
              cInsertIDSet.clear(true);
              cDeleteIDSet.clear(true);
              cInsertIDSet.merge(b.getInsertIDSet());
              cDeleteIDSet.merge(b.getDeleteIDSet());
              cInsertIDSet.setKey(cKey);
              cDeleteIDSet.setKey(cKey);
            }
            else
            {
              cInsertIDSet.merge(b.getInsertIDSet());
              cDeleteIDSet.merge(b.getDeleteIDSet());
            }
          }
          if(b.hasMoreData())
          {
            b.getNextRecord();
            bufferSet.add(b);
          }
        }
        if(cKey != null)
        {
          addToDB(cInsertIDSet, cDeleteIDSet, cIndexID);
        }
        cleanUP();
      }
      catch (Exception e)
      {
        message =
              ERR_JEB_IMPORT_LDIF_INDEX_WRITE_DB_ERR.get(indexMgr.getFileName(),
                                                         e.getMessage());
        logError(message);
        e.printStackTrace();
        throw e;
      }
      return null;
    }


    private void cleanUP() throws DatabaseException, DirectoryException,
      IOException
    {
      if(indexMgr.isDN2ID())
      {
        for(DNState dnState : dnStateMap.values())
        {
          dnState.flush();
        }
        Message msg = NOTE_JEB_IMPORT_LDIF_DN_CLOSE.get(indexMgr.getDNCount());
        logError(msg);
      }
      else
      {
        for(Index index : indexMap.values())
        {
          index.closeCursor();
        }
        Message message =
                NOTE_JEB_IMPORT_LDIF_INDEX_CLOSE.get(indexMgr.getFileName());
        logError(message);
      }
      indexMgr.setDone();
      indexMgr.close();
      indexMgr.deleteIndexFile();
    }


    private void addToDB(ImportIDSet insertSet, ImportIDSet deleteSet,
                         int indexID) throws InterruptedException,
            DatabaseException, DirectoryException
    {
      if(!indexMgr.isDN2ID())
      {
        Index index;
        if((deleteSet.size() > 0) || (!deleteSet.isDefined()))
        {
          dbKey.setData(deleteSet.getKey().array(), 0,
                        deleteSet.getKey().limit());
          index =  (Index)idContainerMap.get(indexID);
          index.delete(dbKey, deleteSet, dbValue);
          if(!indexMap.containsKey(indexID))
          {
            indexMap.put(indexID, index);
          }
        }
        if((insertSet.size() > 0) || (!insertSet.isDefined()))
        {
          dbKey.setData(insertSet.getKey().array(), 0,
                        insertSet.getKey().limit());
          index =  (Index)idContainerMap.get(indexID);
          index.insert(dbKey, insertSet, dbValue);
          if(!indexMap.containsKey(indexID))
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
            throws DatabaseException, DirectoryException
    {
      DNState dnState;
      if(!dnStateMap.containsKey(indexID))
      {
        dnState = new DNState(idECMap.get(indexID));
        dnStateMap.put(indexID, dnState);
      }
      else
      {
        dnState = dnStateMap.get(indexID);
      }
      if(!dnState.checkParent(record))
      {
        return;
      }
      dnState.writeToDB();
    }


      /**
     * This class is used to by a index DB merge thread performing DN processing
     * to keep track of the state of individual DN2ID index processing.
     */
    class DNState
    {
      private final int DN_STATE_CACHE_SIZE = 64 * KB;

      private DN parentDN, lastDN;
      private EntryID parentID, lastID, entryID;
      private final DatabaseEntry DNKey, DNValue;
      private final TreeMap<DN, EntryID> parentIDMap =
                    new TreeMap<DN, EntryID>();
      private final EntryContainer entryContainer;
      private final Map<byte[], ImportIDSet> id2childTree;
      private final Map<byte[], ImportIDSet> id2subtreeTree;
      private final int childLimit, subTreeLimit;
      private final boolean childDoCount, subTreeDoCount;


      DNState(EntryContainer entryContainer)
      {
        this.entryContainer = entryContainer;
        Comparator<byte[]> childComparator =
                entryContainer.getID2Children().getComparator();
        id2childTree = new TreeMap<byte[], ImportIDSet>(childComparator);
        childLimit = entryContainer.getID2Children().getIndexEntryLimit();
        childDoCount = entryContainer.getID2Children().getMaintainCount();
        Comparator<byte[]> subComparator =
                entryContainer.getID2Subtree().getComparator();
        subTreeLimit = entryContainer.getID2Subtree().getIndexEntryLimit();
        subTreeDoCount = entryContainer.getID2Subtree().getMaintainCount();
        id2subtreeTree =  new TreeMap<byte[], ImportIDSet>(subComparator);
        DNKey = new DatabaseEntry();
        DNValue = new DatabaseEntry();
      }


      private boolean checkParent(ImportIDSet record) throws DirectoryException,
              DatabaseException
      {
        DN dn = DN.decode(new String(record.getKey().array(), 0 ,
                                     record.getKey().limit()));
        DNKey.setData(record.getKey().array(), 0 , record.getKey().limit());
        byte[] v = record.toDatabase();
        long v1 = JebFormat.entryIDFromDatabase(v);
        DNValue.setData(v);

        entryID = new EntryID(v1);
        //Bypass the cache for append data, lookup the parent in DN2ID and
        //return.
        if(importConfiguration != null &&
           importConfiguration.appendToExistingData())
        {
         parentDN = entryContainer.getParentWithinBase(dn);
          parentID =
             entryContainer.getDN2ID().get(null, parentDN, LockMode.DEFAULT);
        }
        else
        {
          if(parentIDMap.isEmpty())
          {
            parentIDMap.put(dn, entryID);
            return true;
          }
          else if(lastDN != null && lastDN.isAncestorOf(dn))
          {
            parentIDMap.put(lastDN, lastID);
            parentDN = lastDN;
            parentID = lastID;
            lastDN = dn;
            lastID = entryID;
            return true;
          }
          else if(parentIDMap.lastKey().isAncestorOf(dn))
          {
            parentDN = parentIDMap.lastKey();
            parentID = parentIDMap.get(parentDN);
            lastDN = dn;
            lastID = entryID;
            return true;
          }
          else
          {
            DN newParentDN = entryContainer.getParentWithinBase(dn);
            if(parentIDMap.containsKey(newParentDN))
            {
              EntryID newParentID = parentIDMap.get(newParentDN);
              DN lastDN = parentIDMap.lastKey();
              while(!newParentDN.equals(lastDN)) {
                parentIDMap.remove(lastDN);
                lastDN = parentIDMap.lastKey();
              }
              parentIDMap.put(dn, entryID);
              parentDN = newParentDN;
              parentID = newParentID;
              lastDN = dn;
              lastID = entryID;
            }
            else
            {
              Message message =
                      NOTE_JEB_IMPORT_LDIF_DN_NO_PARENT.get(dn.toString());
              Entry e = new Entry(dn, null, null, null);
              reader.rejectEntry(e, message);
              return false;
            }
          }
        }
        return true;
      }


      private void id2child(EntryID childID)
              throws DatabaseException, DirectoryException
      {
        ImportIDSet idSet;
        if(!id2childTree.containsKey(parentID.getDatabaseEntry().getData()))
        {
          idSet = new ImportIDSet(1,childLimit, childDoCount);
          id2childTree.put(parentID.getDatabaseEntry().getData(), idSet);
        }
        else
        {
          idSet = id2childTree.get(parentID.getDatabaseEntry().getData());
        }
        idSet.addEntryID(childID);
        if(id2childTree.size() > DN_STATE_CACHE_SIZE)
        {
           flushMapToDB(id2childTree, entryContainer.getID2Children(), true);
        }
      }


      private EntryID getParentID(DN dn) throws DatabaseException
      {
        EntryID nodeID;
        //Bypass the cache for append data, lookup the parent DN in the DN2ID
        //db.
        if (importConfiguration != null &&
            importConfiguration.appendToExistingData())
        {
          nodeID = entryContainer.getDN2ID().get(null, dn, LockMode.DEFAULT);
        }
        else
        {
          nodeID = parentIDMap.get(dn);
        }
        return nodeID;
      }


      private void id2SubTree(EntryID childID)
              throws DatabaseException, DirectoryException
      {
        ImportIDSet idSet;
        if(!id2subtreeTree.containsKey(parentID.getDatabaseEntry().getData()))
        {
          idSet = new ImportIDSet(1, subTreeLimit, subTreeDoCount);
          id2subtreeTree.put(parentID.getDatabaseEntry().getData(), idSet);
        }
        else
        {
          idSet = id2subtreeTree.get(parentID.getDatabaseEntry().getData());
        }
        idSet.addEntryID(childID);
        for (DN dn = entryContainer.getParentWithinBase(parentDN); dn != null;
             dn = entryContainer.getParentWithinBase(dn))
        {
          EntryID nodeID = getParentID(dn);
          if(!id2subtreeTree.containsKey(nodeID.getDatabaseEntry().getData()))
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
          flushMapToDB(id2subtreeTree,  entryContainer.getID2Subtree(), true);
        }
      }


      public void writeToDB() throws DatabaseException, DirectoryException
      {
        entryContainer.getDN2ID().putRaw(null, DNKey, DNValue);
        indexMgr.addTotDNCount(1);
        if(parentDN != null)
        {
          id2child(entryID);
          id2SubTree(entryID);
        }
      }


      private void flushMapToDB(Map<byte[], ImportIDSet> map, Index index,
                                boolean clearMap)
              throws DatabaseException, DirectoryException
      {
        for(Map.Entry<byte[], ImportIDSet> e : map.entrySet())
        {
          byte[] key = e.getKey();
          ImportIDSet idSet = e.getValue();
          DNKey.setData(key);
          index.insert(DNKey, idSet, DNValue);
        }
        index.closeCursor();
        if(clearMap)
        {
           map.clear();
        }
      }


      public void flush() throws DatabaseException, DirectoryException
      {
        flushMapToDB(id2childTree, entryContainer.getID2Children(), false);
        flushMapToDB(id2subtreeTree,  entryContainer.getID2Subtree(), false);
      }
    }
  }


  /**
   * This task writes the temporary scratch index files using the sorted
   * buffers read from a blocking queue private to each index.
   */
  private final class ScratchFileWriterTask implements Callable<Void>
  {
    private final int DRAIN_TO = 3;
    private final IndexManager indexMgr;
    private final BlockingQueue<IndexBuffer> queue;
    private final ByteArrayOutputStream insetByteStream =
            new ByteArrayOutputStream(2 * bufferSize);
    private final ByteArrayOutputStream deleteByteStream =
            new ByteArrayOutputStream(2 * bufferSize);
    private final byte[] tmpArray = new byte[8];
    private int insertKeyCount = 0, deleteKeyCount = 0;
    private final DataOutputStream dataStream;
    private long bufferCount = 0;
    private final File file;
    private final SortedSet<IndexBuffer> indexSortedSet;
    private boolean poisonSeen = false;
    ByteBuffer keyBuf = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);


    public ScratchFileWriterTask(BlockingQueue<IndexBuffer> queue,
                             IndexManager indexMgr) throws FileNotFoundException
    {
      this.queue = queue;
      file = indexMgr.getFile();
      this.indexMgr = indexMgr;
      BufferedOutputStream bufferedStream =
              new BufferedOutputStream(new FileOutputStream(file),
                                       READER_WRITER_BUFFER_SIZE);
      dataStream = new DataOutputStream(bufferedStream);
      indexSortedSet = new TreeSet<IndexBuffer>();
    }


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      long offset = 0;
      List<IndexBuffer> l = new LinkedList<IndexBuffer>();
      try {
        while(true)
        {
          IndexBuffer indexBuffer = queue.poll();
          if(indexBuffer != null)
          {
            long beginOffset = offset;
            long bufferLen;
            if(!queue.isEmpty())
            {
              queue.drainTo(l, DRAIN_TO);
              l.add(indexBuffer);
              bufferLen = writeIndexBuffers(l);
              for(IndexBuffer id : l)
              {
                if(!id.isDiscard())
                {
                  id.reset();
                  freeBufferQueue.add(id);
                }
              }
              l.clear();
            }
            else
            {
              if(indexBuffer.isPoison())
              {
                break;
              }
              bufferLen = writeIndexBuffer(indexBuffer);
              if(!indexBuffer.isDiscard())
              {
                indexBuffer.reset();
                freeBufferQueue.add(indexBuffer);
              }
            }
            offset += bufferLen;
            indexMgr.addBuffer(new Buffer(beginOffset, offset, bufferCount));
            bufferCount++;
            Importer.this.bufferCount.incrementAndGet();
            if(poisonSeen)
            {
              break;
            }
          }
        }
      }
      catch (Exception e)
      {
        Message message =
                ERR_JEB_IMPORT_LDIF_INDEX_FILEWRITER_ERR.get(file.getName(),
                        e.getMessage());
        logError(message);
        isPhaseOneCanceled = true;
        throw e;
      }
      finally
      {
        dataStream.close();
        indexMgr.setFileLength();
      }
      return null;
    }


    private long writeIndexBuffer(IndexBuffer indexBuffer) throws IOException
    {
      int numberKeys = indexBuffer.getNumberKeys();
      indexBuffer.setPosition(-1);
      long bufferLen = 0;
      insetByteStream.reset(); insertKeyCount = 0;
      deleteByteStream.reset(); deleteKeyCount = 0;
      for(int i = 0; i < numberKeys; i++)
      {
        if(indexBuffer.getPosition() == -1)
        {
          indexBuffer.setPosition(i);
          if(indexBuffer.isInsert(i))
          {
            indexBuffer.writeID(insetByteStream, i);
            insertKeyCount++;
          }
          else
          {
            indexBuffer.writeID(deleteByteStream, i);
            deleteKeyCount++;
          }
          continue;
        }
        if(!indexBuffer.compare(i))
        {
          bufferLen += writeRecord(indexBuffer);
          indexBuffer.setPosition(i);
          insetByteStream.reset();insertKeyCount = 0;
          deleteByteStream.reset();deleteKeyCount = 0;
        }
        if(indexBuffer.isInsert(i))
        {
          if(insertKeyCount++ <= indexMgr.getLimit())
          {
            indexBuffer.writeID(insetByteStream, i);
          }
        }
        else
        {
          indexBuffer.writeID(deleteByteStream, i);
          deleteKeyCount++;
        }
      }
      if(indexBuffer.getPosition() != -1)
      {
        bufferLen += writeRecord(indexBuffer);
      }
      return bufferLen;
    }


    private long writeIndexBuffers(List<IndexBuffer> buffers)
            throws IOException
    {
      long id = 0;
      long bufferLen = 0;
      insetByteStream.reset(); insertKeyCount = 0;
      deleteByteStream.reset(); deleteKeyCount = 0;
      for(IndexBuffer b : buffers)
      {
        if(b.isPoison())
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
      while(!indexSortedSet.isEmpty())
      {
        IndexBuffer b = indexSortedSet.first();
        indexSortedSet.remove(b);
        if(saveKey == null)
        {
          saveKey =  b.getKey();
          saveIndexID = b.getIndexID();
          if(b.isInsert(b.getPosition()))
          {
            b.writeID(insetByteStream, b.getPosition());
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
          if(!b.compare(saveKey, saveIndexID))
          {
            bufferLen += writeRecord(saveKey, saveIndexID);
            insetByteStream.reset();
            deleteByteStream.reset();
            insertKeyCount = 0;
            deleteKeyCount = 0;
            saveKey = b.getKey();
            saveIndexID =  b.getIndexID();
            if(b.isInsert(b.getPosition()))
            {
              b.writeID(insetByteStream, b.getPosition());
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
            if(b.isInsert(b.getPosition()))
            {
              if(insertKeyCount++ <= indexMgr.getLimit())
              {
                b.writeID(insetByteStream, b.getPosition());
              }
            }
            else
            {
              b.writeID(deleteByteStream, b.getPosition());
              deleteKeyCount++;
            }
          }
        }
        if(b.hasMoreData())
        {
          b.getNextRecord();
          indexSortedSet.add(b);
        }
      }
      if(saveKey != null)
      {
        bufferLen += writeRecord(saveKey, saveIndexID);
      }
      return bufferLen;
    }


    private int writeByteStreams() throws IOException
    {
      if(insertKeyCount > indexMgr.getLimit())
      {
        insertKeyCount = 1;
        insetByteStream.reset();
        PackedInteger.writeInt(tmpArray, 0, -1);
        insetByteStream.write(tmpArray, 0, 1);
      }
      int insertSize = PackedInteger.getWriteIntLength(insertKeyCount);
      PackedInteger.writeInt(tmpArray, 0, insertKeyCount);
      dataStream.write(tmpArray, 0, insertSize);
      if(insetByteStream.size() > 0)
      {
        insetByteStream.writeTo(dataStream);
      }
      int deleteSize = PackedInteger.getWriteIntLength(deleteKeyCount);
      PackedInteger.writeInt(tmpArray, 0, deleteKeyCount);
      dataStream.write(tmpArray, 0, deleteSize);
      if(deleteByteStream.size() > 0)
      {
        deleteByteStream.writeTo(dataStream);
      }
      return insertSize + deleteSize;
    }


    private int writeHeader(int indexID, int keySize) throws IOException
    {
      dataStream.writeInt(indexID);
      int packedSize = PackedInteger.getWriteIntLength(keySize);
      PackedInteger.writeInt(tmpArray, 0, keySize);
      dataStream.write(tmpArray, 0, packedSize);
      return packedSize;
    }


    private int writeRecord(IndexBuffer b) throws IOException
    {
      int keySize = b.getKeySize();
      int packedSize = writeHeader(b.getIndexID(), keySize);
      b.writeKey(dataStream);
      packedSize += writeByteStreams();
      return (packedSize + keySize + insetByteStream.size() +
              deleteByteStream.size() + 4);
    }


    private int writeRecord(byte[] k, int indexID) throws IOException
    {
      int packedSize = writeHeader(indexID, k.length);
      dataStream.write(k);
      packedSize += writeByteStreams();
      return (packedSize + k.length + insetByteStream.size() +
              deleteByteStream.size() + 4);
    }
  }


  /**
   * This task main function is to sort the index buffers given to it from
   * the import tasks reading the LDIF file. It will also create a index
   * file writer task and corresponding queue if needed. The sorted index
   * buffers are put on the index file writer queues for writing to a temporary
   * file.
   */
  private final class SortTask implements Callable<Void>
  {

    private final IndexBuffer indexBuffer;

    public SortTask(IndexBuffer indexBuffer)
    {
      this.indexBuffer = indexBuffer;
    }

    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      if (importConfiguration != null &&
          importConfiguration.isCancelled() || isPhaseOneCanceled)
      {
        isPhaseOneCanceled =true;
        return null;
      }
      indexBuffer.sort();
      if(indexKeyQueMap.containsKey(indexBuffer.getIndexKey())) {
        BlockingQueue<IndexBuffer> q =
                indexKeyQueMap.get(indexBuffer.getIndexKey());
        q.add(indexBuffer);
      }
      else
      {
        createIndexWriterTask(indexBuffer.getIndexKey());
        BlockingQueue<IndexBuffer> q =
                                 indexKeyQueMap.get(indexBuffer.getIndexKey());
        q.add(indexBuffer);
      }
      return null;
    }

    private void createIndexWriterTask(IndexKey indexKey)
            throws FileNotFoundException
    {
      boolean isDN = false;
      synchronized(synObj)
      {
        if(indexKeyQueMap.containsKey(indexKey))
        {
          return;
        }
        if(indexKey.getIndexType().equals(ImportIndexType.DN))
        {
          isDN = true;
        }
        IndexManager indexMgr = new IndexManager(indexKey.getName(), isDN,
                                                 indexKey.getEntryLimit());
        if(isDN)
        {
          DNIndexMgrList.add(indexMgr);
        }
        else
        {
          indexMgrList.add(indexMgr);
        }
        BlockingQueue<IndexBuffer> newQue =
                new ArrayBlockingQueue<IndexBuffer>(phaseOneBufferCount);
        ScratchFileWriterTask indexWriter =
                new ScratchFileWriterTask(newQue, indexMgr);
        scratchFileWriterList.add(indexWriter);
        scratchFileWriterFutures.add(
                              scratchFileWriterService.submit(indexWriter));
        indexKeyQueMap.put(indexKey, newQue);
      }
    }
  }

  /**
   * The buffer class is used to process a buffer from the temporary index files
   * during phase 2 processing.
   */
  private final class Buffer implements Comparable<Buffer>
  {
    private IndexManager indexMgr;
    private final long begin, end, id;
    private long offset;
    private ByteBuffer cache;
    private int limit;;
    private ImportIDSet insertIDSet = null, deleteIDSet = null;
    private Integer indexID = null;
    private boolean doCount;
    private ByteBuffer keyBuf = ByteBuffer.allocate(BYTE_BUFFER_CAPACITY);


    public Buffer(long begin, long end, long id)
    {
      this.begin = begin;
      this.end = end;
      this.offset = 0;
      this.id = id;
    }


    private void initializeCache(IndexManager indexMgr, ByteBuffer b,
                      long cacheSize) throws IOException
    {
      this.indexMgr = indexMgr;
      if(b == null)
      {
        cache = ByteBuffer.allocate((int)cacheSize);
      }
      else
      {
        cache = b;
      }
      loadCache();
      cache.flip();
      keyBuf.flip();
    }


    private void loadCache() throws IOException
    {
      FileChannel fileChannel = indexMgr.getChannel();
      fileChannel.position(begin + offset);
      long leftToRead =  end - (begin + offset);
      long bytesToRead;
      if(leftToRead < cache.remaining())
      {
        cache.limit((int) (cache.position() + leftToRead));
        bytesToRead = (int)leftToRead;
      }
      else
      {
        bytesToRead = Math.min((end - offset),cache.remaining());
      }
      int bytesRead = 0;
      while(bytesRead < bytesToRead)
      {
        bytesRead += fileChannel.read(cache);
      }
      offset += bytesRead;
      indexMgr.addBytesRead(bytesRead);
    }

      public boolean hasMoreData() throws IOException
      {
          boolean ret = ((begin + offset) >= end) ? true: false;
          if(cache.remaining() == 0 && ret)
          {
              return false;
          }
          else
          {
              return true;
          }
      }

    public int getKeyLen()
    {
      return keyBuf.limit();
    }

    public void getKey(ByteBuffer b)
    {
      keyBuf.get(b.array(), 0, keyBuf.limit());
      b.limit(keyBuf.limit());
    }

    ByteBuffer getKeyBuf()
    {
      return keyBuf;
    }

    public ImportIDSet getInsertIDSet()
    {
      return insertIDSet;
    }

    public ImportIDSet getDeleteIDSet()
    {
      return deleteIDSet;
    }

    public long getBufferID()
    {
      return id;
    }

    public Integer getIndexID()
    {
      if(indexID == null)
      {
        try {
          getNextRecord();
        } catch(IOException ex) {
           Message message = ERR_JEB_IO_ERROR.get(ex.getMessage());
           logError(message);
           ex.printStackTrace();
           System.exit(1);
        }
      }
      return indexID;
    }

    public void getNextRecord()  throws IOException
    {
      getNextIndexID();
      getContainerParameters();
      getNextKey();
      getNextIDSet(true);  //get insert ids
      getNextIDSet(false); //get delete ids
    }

    private void getContainerParameters()
    {
      limit = 1;
      doCount = false;
      if(!indexMgr.isDN2ID())
      {
        Index index = (Index) idContainerMap.get(indexID);
        limit = index.getIndexEntryLimit();
        doCount = index.getMaintainCount();
        if(insertIDSet == null)
        {
          insertIDSet = new ImportIDSet(128, limit, doCount);
          deleteIDSet = new ImportIDSet(128, limit, doCount);
        }
      }
      else
      {
        if(insertIDSet == null)
        {
            insertIDSet = new ImportIDSet(1, limit, doCount);
            deleteIDSet = new ImportIDSet(1, limit, doCount);
        }
      }
    }


    private int getInt()  throws IOException
    {
      ensureData(4);
      return cache.getInt();
    }

    private void getNextIndexID() throws IOException, BufferUnderflowException
     {
       indexID = getInt();
     }

    private void getNextKey() throws IOException, BufferUnderflowException
    {
      ensureData(20);
      byte[] ba = cache.array();
      int p = cache.position();
      int len = PackedInteger.getReadIntLength(ba, p);
      int keyLen = PackedInteger.readInt(ba, p);
      cache.position(p + len);
      if(keyLen > keyBuf.capacity())
      {
        keyBuf = ByteBuffer.allocate(keyLen);
      }
      ensureData(keyLen);
      keyBuf.clear();
      cache.get(keyBuf.array(), 0, keyLen);
      keyBuf.limit(keyLen);
    }

    private void getNextIDSet(boolean insert)
            throws IOException, BufferUnderflowException
    {
      ensureData(20);
      int p = cache.position();
      byte[] ba = cache.array();
      int len = PackedInteger.getReadIntLength(ba, p);
      int keyCount = PackedInteger.readInt(ba, p);
      p += len;
      cache.position(p);
      if(insert)
      {
        insertIDSet.clear(false);
      }
      else
      {
        deleteIDSet.clear(false);
      }
      for(int k = 0; k < keyCount; k++)
      {
        if(ensureData(9))
        {
          p = cache.position();
        }
        len = PackedInteger.getReadLongLength(ba, p);
        long l = PackedInteger.readLong(ba, p);
        p += len;
        cache.position(p);
        if(insert)
        {
          insertIDSet.addEntryID(l);
        }
        else
        {
          deleteIDSet.addEntryID(l);
        }
      }
    }


    private boolean ensureData(int len) throws IOException
    {
      boolean ret = false;
      if(cache.remaining() == 0)
      {
        cache.clear();
        loadCache();
        cache.flip();
        ret = true;
      }
      else if(cache.remaining() < len)
      {
        cache.compact();
        loadCache();
        cache.flip();
        ret = true;
      }
      return ret;
    }


    private int compare(ByteBuffer cKey, Integer cIndexID)
    {
      int returnCode, rc = 0;
      if(keyBuf.limit() == 0)
      {
        getIndexID();
      }
      if(indexMgr.isDN2ID())
      {
        rc = dnComparator.compare(keyBuf.array(), 0, keyBuf.limit(),
                                 cKey.array(), cKey.limit());
      }
      else
      {
        rc = indexComparator.compare(keyBuf.array(), 0, keyBuf.limit(),
                                     cKey.array(), cKey.limit());
      }
      if(rc != 0) {
        returnCode = 1;
      }
      else
      {
        returnCode = (indexID.intValue() == cIndexID.intValue()) ? 0 : 1;
      }
      return returnCode;
    }



    public int compareTo(Buffer o) {
      //used in remove.
      if(this.equals(o))
      {
        return 0;
      }
      if(keyBuf.limit() == 0) {
        getIndexID();
      }
      if(o.getKeyBuf().limit() == 0)
      {
        o.getIndexID();
      }
      int returnCode = 0;
      byte[] oKey = o.getKeyBuf().array();
      int oLen = o.getKeyBuf().limit();
      if(indexMgr.isDN2ID())
      {
        returnCode = dnComparator.compare(keyBuf.array(), 0, keyBuf.limit(),
                                          oKey, oLen);
      }
      else
      {
        returnCode = indexComparator.compare(keyBuf.array(), 0, keyBuf.limit(),
                                             oKey, oLen);
      }
      if(returnCode == 0)
      {
        if(indexID.intValue() == o.getIndexID().intValue())
        {
          if(insertIDSet.isDefined())
          {
            returnCode = -1;
          }
          else if(o.getInsertIDSet().isDefined())
          {
            returnCode = 1;
          }
          else if(insertIDSet.size() == o.getInsertIDSet().size())
          {
            returnCode = id > o.getBufferID() ? 1 : -1;
          }
          else
          {
            returnCode = insertIDSet.size() - o.getInsertIDSet().size();
          }
        }
        else if(indexID > o.getIndexID())
        {
          returnCode = 1;
        }
        else
        {
          returnCode = -1;
        }
      }
      return returnCode;
    }
  }


  /**
   * The index manager class has several functions:
   *
   *   1. It used to carry information about index processing created in phase
   *      one to phase two.
   *
   *   2. It collects statistics about phase two processing for each index.
   *
   *   3. It manages opening and closing the scratch index files.
   */
  private final class IndexManager implements Comparable<IndexManager>
  {
    private final File file;
    private RandomAccessFile rFile = null;
    private final List<Buffer> bufferList = new LinkedList<Buffer>();
    private long fileLength, bytesRead = 0;
    private boolean done = false, started = false;
    private long totalDNS;
    private AtomicInteger keyCount = new AtomicInteger(0);
    private final String fileName;
    private final boolean isDN;
    private final int limit;


    IndexManager(String fileName, boolean isDN, int limit)
    {
      file = new File(tempDir, fileName);
      this.fileName = fileName;
      this.isDN = isDN;
      this.limit = limit;
    }


    void openIndexFile() throws FileNotFoundException
    {
      rFile = new RandomAccessFile(file, "r");
    }


    public FileChannel getChannel()
    {
      return rFile.getChannel();
    }


    public void addBuffer(Buffer o)
    {
      this.bufferList.add(o);
    }


    public List<Buffer> getBufferList()
    {
      return bufferList;
    }


    public File getFile()
    {
      return file;
    }


    public boolean deleteIndexFile()
    {
      return file.delete();
    }


    public void close() throws IOException
    {
      rFile.close();
    }


    public void setFileLength()
    {
      this.fileLength = file.length();
    }


    public void addBytesRead(int bytesRead)
    {
      this.bytesRead += bytesRead;
    }


    public void setDone()
    {
      this.done = true;
    }


    public void setStarted()
    {
      started = true;
    }


    public void addTotDNCount(int delta)
    {
      this.totalDNS += delta;
    }


    public long getDNCount()
    {
      return totalDNS;
    }


    public boolean isDN2ID()
    {
      return isDN;
    }


    public void printStats(long deltaTime)
    {
      if(!done && started)
      {
        float rate = 1000f * keyCount.getAndSet(0) / deltaTime;
        Message message = NOTE_JEB_IMPORT_LDIF_PHASE_TWO_REPORT.get(fileName,
                (fileLength - bytesRead), rate);
        logError(message);
      }
    }


    public void incrementKeyCount()
    {
      keyCount.incrementAndGet();
    }


    public String getFileName()
    {
      return fileName;
    }


    public int getLimit()
    {
      return limit;
    }


    public int compareTo(IndexManager mgr)
    {
      if(bufferList.size() == mgr.getBufferList().size())
      {
         return 0;
      }
      else if (bufferList.size() < mgr.getBufferList().size())
      {
        return -1;
      }
      else
      {
        return 1;
      }
    }
  }


  /**
   * The rebuild index manager handles all rebuild index related processing.
   */
  class RebuildIndexManager extends ImportTask {

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
   private long totalEntries =0;

   //Total entries processed.
   private final AtomicLong entriesProcessed = new AtomicLong(0);

   //The suffix instance.
   private Suffix suffix = null;

   //Set to true if the rebuild all flag was specified.
   private final boolean rebuildAll;

   //The entry container.
   private EntryContainer entryContainer;


    /**
     * Create an instance of the rebuild index manager using the specified
     * parameters.
     *
     * @param rebuildConfig  The rebuild configuration to use.
     * @param cfg The local DB configuration to use.
     */
    public RebuildIndexManager(RebuildConfig rebuildConfig,
                               LocalDBBackendCfg cfg)
    {
      this.rebuildConfig = rebuildConfig;
      this.cfg = cfg;
      rebuildAll = rebuildConfig.isRebuildAll();
    }


    /**
     * Initialize a rebuild index manager.
     *
     * @throws ConfigException If an configuration error occurred.
     * @throws InitializationException If an initialization error occurred.
     */
    public void initialize() throws ConfigException, InitializationException
    {
      entryContainer =
                  rootContainer.getEntryContainer(rebuildConfig.getBaseDN());
      suffix = Suffix.createSuffixContext(entryContainer, null, null, null);
      if(suffix == null)
      {
        Message msg = ERR_JEB_REBUILD_SUFFIX_ERROR.get(rebuildConfig.
                getBaseDN().toString());
        throw new InitializationException(msg);
      }
    }


    /**
     * Print start message.
     *
     * @throws DatabaseException If an database error occurred.
     */
    public void printStartMessage() throws DatabaseException
    {
      StringBuilder sb = new StringBuilder();
      List<String> rebuildList = rebuildConfig.getRebuildList();
      for(String index : rebuildList)
      {
        if(sb.length() > 0)
        {
          sb.append(", ");
        }
        sb.append(index);
      }
      totalEntries = suffix.getID2Entry().getRecordCount();
      Message message = NOTE_JEB_REBUILD_START.get(sb.toString(), totalEntries);
      if(rebuildAll) {
        message = NOTE_JEB_REBUILD_ALL_START.get(totalEntries);
      }
      logError(message);
    }


    /**
     * Print stop message.
     *
     * @param startTime The time the rebuild started.
     */
    public void printStopMessage(long startTime)
    {
      long finishTime = System.currentTimeMillis();
      long totalTime = (finishTime - startTime);
      float rate = 0;
      if (totalTime > 0)
      {
        rate = 1000f* entriesProcessed.get() / totalTime;
      }
      Message message =
              NOTE_JEB_REBUILD_FINAL_STATUS.get(entriesProcessed.get(),
                      totalTime/1000, rate);
      logError(message);
    }


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      ID2Entry id2entry = entryContainer.getID2Entry();
      Cursor cursor = id2entry.openCursor(null, CursorConfig.READ_COMMITTED);
      DatabaseEntry key = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();
      LockMode lockMode = LockMode.DEFAULT;
      OperationStatus status;
      try {
        for (status = cursor.getFirst(key, data, lockMode);
             status == OperationStatus.SUCCESS;
             status = cursor.getNext(key, data, lockMode))
        {
          if(isPhaseOneCanceled)
          {
            return null;
          }
          EntryID entryID = new EntryID(key);
          Entry entry = ID2Entry.entryFromDatabase(
                  ByteString.wrap(data.getData()),
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
        isPhaseOneCanceled = true;
        throw e;
      }
      return null;
    }


    /**
     * Perform rebuild index processing.
     *
     * @throws DatabaseException If an database error occurred.
     * @throws InterruptedException If an interrupted error occurred.
     * @throws ExecutionException If an Excecution error occurred.
     * @throws JebException If an JEB error occurred.
     */
    public void rebuldIndexes() throws DatabaseException, InterruptedException,
            ExecutionException, JebException
    {
      phaseOne();
      if(isPhaseOneCanceled)
      {
        throw new InterruptedException("Rebuild Index canceled.");
      }
      phaseTwo();
      if(rebuildAll)
      {
        setAllIndexesTrusted();
      }
      else
      {
        setRebuildListIndexesTrusted();
      }
    }


    private void setRebuildListIndexesTrusted()  throws JebException
    {
      try
      {
        if(dn2id != null)
        {
          EntryContainer ec = suffix.getEntryContainer();
          ec.getID2Children().setTrusted(null,true);
          ec.getID2Subtree().setTrusted(null, true);
        }
        if(!indexMap.isEmpty())
        {
          for(Map.Entry<IndexKey, Index> mapEntry : indexMap.entrySet()) {
            Index index = mapEntry.getValue();
            index.setTrusted(null, true);
          }
        }
        if(!vlvIndexes.isEmpty())
        {
          for(VLVIndex vlvIndex : vlvIndexes)
          {
            vlvIndex.setTrusted(null, true);
          }
        }
        if(!extensibleIndexMap.isEmpty())
        {
          Collection<Index> subIndexes =
                  extensibleIndexMap.get(EXTENSIBLE_INDEXER_ID_SUBSTRING);
          if(subIndexes != null) {
            for(Index subIndex : subIndexes) {
              subIndex.setTrusted(null, true);
            }
          }
          Collection<Index> sharedIndexes =
                  extensibleIndexMap.get(EXTENSIBLE_INDEXER_ID_SHARED);
          if(sharedIndexes !=null) {
            for(Index sharedIndex : sharedIndexes) {
              sharedIndex.setTrusted(null, true);
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


    private void setAllIndexesTrusted() throws JebException
    {
      try {
        suffix.setIndexesTrusted();
      }
      catch (DatabaseException ex)
      {
        Message message =
                NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage());
        throw new JebException(message);
      }
    }


    private void phaseOne() throws DatabaseException,
            InterruptedException, ExecutionException {
      if(rebuildAll)
      {
        clearAllIndexes();
      }
      else
      {
        clearRebuildListIndexes();
      }
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
      for (Future<Void> result : results) {
        if(!result.isDone()) {
          result.get();
        }
      }
      stopScratchFileWriters();
      for (Future<?> result : scratchFileWriterFutures)
      {
        if(!result.isDone()) {
          result.get();
        }
      }
      //Try to clear as much memory as possible.
      tasks.clear();
      results.clear();
      rebuildIndexService.shutdown();
      freeBufferQueue.clear();
      bufferSortService.shutdown();
      timer.cancel();
    }


    private void phaseTwo() throws InterruptedException, JebException,
            ExecutionException
    {
      SecondPhaseProgressTask progressTask =
              new SecondPhaseProgressTask(entriesProcessed.get());
      Timer timer2 = new Timer();
      timer2.scheduleAtFixedRate(progressTask, TIMER_INTERVAL, TIMER_INTERVAL);
      processIndexFiles();
      timer2.cancel();
    }


    private int getIndexCount() throws ConfigException, JebException
    {
      int indexCount;
      if(!rebuildAll)
      {
        indexCount = getRebuildListIndexCount(cfg);
      }
      else
      {
        indexCount = getAllIndexesCount(cfg);
      }
      return indexCount;
    }


    private int getAllIndexesCount(LocalDBBackendCfg cfg)
    {
      int indexCount = cfg.listLocalDBIndexes().length;
      indexCount += cfg.listLocalDBVLVIndexes().length;
      //Add four for: DN, id2subtree, id2children and dn2uri.
      indexCount += 4;
      return indexCount;
    }


    private int getRebuildListIndexCount(LocalDBBackendCfg cfg)
            throws JebException, ConfigException
    {
      int indexCount = 0;
      List<String> rebuildList = rebuildConfig.getRebuildList();
      if(!rebuildList.isEmpty())
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
            if(lowerName.length() < 5)
            {
              Message msg = ERR_JEB_VLV_INDEX_NOT_CONFIGURED.get(lowerName);
              throw new JebException(msg);
            }
            indexCount++;
          } else if(lowerName.equals("id2subtree") ||
                  lowerName.equals("id2children"))
          {
            Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
            throw new JebException(msg);
          }
          else
          {
            String[] attrIndexParts = lowerName.split("\\.");
            if((attrIndexParts.length <= 0) || (attrIndexParts.length > 3))
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }
            AttributeType attrType =
                    DirectoryServer.getAttributeType(attrIndexParts[0]);
            if (attrType == null)
            {
              Message msg = ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
              throw new JebException(msg);
            }
            if(attrIndexParts.length != 1)
            {
              if(attrIndexParts.length == 2)
              {
                if(attrIndexParts[1].equals("presence"))
                {
                  indexCount++;
                }
                else if(attrIndexParts[1].equals("equality"))
                {
                  indexCount++;
                }
                else if(attrIndexParts[1].equals("substring"))
                {
                  indexCount++;
                }
                else if(attrIndexParts[1].equals("ordering"))
                {
                  indexCount++;
                }
                else if(attrIndexParts[1].equals("approximate"))
                {
                  indexCount++;
                } else {
                  Message msg =
                          ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                  throw new JebException(msg);
                }
              }
              else
              {
                boolean found = false;
                String s = attrIndexParts[1] + "." + attrIndexParts[2];
                for (String idx : cfg.listLocalDBIndexes())
                {
                  LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
                  if (indexCfg.getIndexType().
                          contains(LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
                  {
                    Set<String> extensibleRules =
                            indexCfg.getIndexExtensibleMatchingRule();
                    for(String exRule : extensibleRules)
                    {
                      if(exRule.equalsIgnoreCase(s))
                      {
                        found = true;
                        break;
                      }
                    }
                  }
                  if(found)
                  {
                    break;
                  }
                }
                if(!found) {
                  Message msg =
                          ERR_JEB_ATTRIBUTE_INDEX_NOT_CONFIGURED.get(index);
                  throw new JebException(msg);
                }
                indexCount++;
              }
            }
            else
            {
              for (String idx : cfg.listLocalDBIndexes())
              {
                if(!idx.equalsIgnoreCase(index))
                {
                  continue;
                }
                LocalDBIndexCfg indexCfg = cfg.getLocalDBIndex(idx);
                if(indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.EQUALITY))
                {
                  indexCount++;
                }
                if(indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.ORDERING))
                {
                  indexCount++;
                }
                if(indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.PRESENCE))
                {
                  indexCount++;
                }
                if(indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.SUBSTRING))
                {
                  indexCount++;
                }
                if(indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.APPROXIMATE))
                {
                  indexCount++;
                }
                if (indexCfg.getIndexType().
                        contains(LocalDBIndexCfgDefn.IndexType.EXTENSIBLE))
                {
                  Set<String> extensibleRules =
                          indexCfg.getIndexExtensibleMatchingRule();
                  boolean shared = false;
                  for(String exRule : extensibleRules)
                  {
                    if(exRule.endsWith(".sub"))
                    {
                      indexCount++;
                    }
                    else
                    {
                      if(!shared)
                      {
                        shared=true;
                        indexCount++;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      return indexCount;
    }


    private void clearRebuildListIndexes() throws DatabaseException
    {
      List<String> rebuildList = rebuildConfig.getRebuildList();
      if(!rebuildList.isEmpty())
      {
        for (String index : rebuildList)
        {
          String lowerName = index.toLowerCase();
          if (lowerName.equals("dn2id"))
          {
            clearDN2IDIndexes();
          }
          else if (lowerName.equals("dn2uri"))
          {
            clearDN2URI();
          }
          else if (lowerName.startsWith("vlv."))
          {
            clearVLVIndex(lowerName.substring(4));
          }
          else
          {
            String[] attrIndexParts = lowerName.split("\\.");
            AttributeType attrType =
                    DirectoryServer.getAttributeType(attrIndexParts[0]);
            AttributeIndex attrIndex =
                                   entryContainer.getAttributeIndex(attrType);
            if(attrIndexParts.length != 1)
            {
              Index partialAttrIndex;
              if(attrIndexParts[1].equals("presence"))
              {
                partialAttrIndex = attrIndex.getPresenceIndex();
                int id = System.identityHashCode(partialAttrIndex);
                idContainerMap.putIfAbsent(id, partialAttrIndex);
                entryContainer.clearDatabase(partialAttrIndex);
                IndexKey indexKey =
                        new IndexKey(attrType, ImportIndexType.PRESENCE,
                                partialAttrIndex.getIndexEntryLimit());
                indexMap.put(indexKey, partialAttrIndex);
              }
              else if(attrIndexParts[1].equals("equality"))
              {
                partialAttrIndex = attrIndex.getEqualityIndex();
                int id = System.identityHashCode(partialAttrIndex);
                idContainerMap.putIfAbsent(id, partialAttrIndex);
                entryContainer.clearDatabase(partialAttrIndex);
                IndexKey indexKey =
                        new IndexKey(attrType, ImportIndexType.EQUALITY,
                                partialAttrIndex.getIndexEntryLimit());
                indexMap.put(indexKey, partialAttrIndex);
              }
              else if(attrIndexParts[1].equals("substring"))
              {
                partialAttrIndex = attrIndex.getSubstringIndex();
                int id = System.identityHashCode(partialAttrIndex);
                idContainerMap.putIfAbsent(id, partialAttrIndex);
                entryContainer.clearDatabase(partialAttrIndex);
                IndexKey indexKey =
                        new IndexKey(attrType, ImportIndexType.SUBSTRING,
                                partialAttrIndex.getIndexEntryLimit());
                indexMap.put(indexKey, partialAttrIndex);
              }
              else if(attrIndexParts[1].equals("ordering"))
              {
                partialAttrIndex = attrIndex.getOrderingIndex();
                int id = System.identityHashCode(partialAttrIndex);
                idContainerMap.putIfAbsent(id, partialAttrIndex);
                entryContainer.clearDatabase(partialAttrIndex);
                IndexKey indexKey =
                        new IndexKey(attrType, ImportIndexType.ORDERING,
                                partialAttrIndex.getIndexEntryLimit());
                indexMap.put(indexKey, partialAttrIndex);
              }
              else if(attrIndexParts[1].equals("approximate"))
              {
                partialAttrIndex = attrIndex.getApproximateIndex();
                int id = System.identityHashCode(partialAttrIndex);
                idContainerMap.putIfAbsent(id, partialAttrIndex);
                entryContainer.clearDatabase(partialAttrIndex);
                IndexKey indexKey =
                        new IndexKey(attrType, ImportIndexType.APPROXIMATE,
                                partialAttrIndex.getIndexEntryLimit());
                indexMap.put(indexKey, partialAttrIndex);
              }
              else
              {
                String dbPart = "shared";
                if(attrIndexParts[2].startsWith("sub"))
                {
                  dbPart = "substring";
                }
                StringBuilder nameBldr = new StringBuilder();
                nameBldr.append(entryContainer.getDatabasePrefix());
                nameBldr.append("_");
                nameBldr.append(attrIndexParts[0]);
                nameBldr.append(".");
                nameBldr.append(attrIndexParts[1]);
                nameBldr.append(".");
                nameBldr.append(dbPart);
                String indexName = nameBldr.toString();
                Map<String,Collection<Index>> extensibleMap =
                        attrIndex.getExtensibleIndexes();
                if(!extensibleMap.isEmpty()) {
                  Collection<Index> subIndexes =
                          attrIndex.getExtensibleIndexes().get(
                                  EXTENSIBLE_INDEXER_ID_SUBSTRING);
                  if(subIndexes != null) {
                    for(Index subIndex : subIndexes) {
                      String name = subIndex.getName();
                      if(name.equalsIgnoreCase(indexName))
                      {
                        entryContainer.clearDatabase(subIndex);
                        int id = System.identityHashCode(subIndex);
                        idContainerMap.putIfAbsent(id, subIndex);
                        Collection<Index> substring = new ArrayList<Index>();
                        substring.add(subIndex);
                        extensibleIndexMap.put(new IndexKey(attrType,
                                ImportIndexType.EX_SUBSTRING, 0),substring);
                        break;
                      }
                    }
                    Collection<Index> sharedIndexes =
                            attrIndex.getExtensibleIndexes().
                                              get(EXTENSIBLE_INDEXER_ID_SHARED);
                    if(sharedIndexes !=null) {
                      for(Index sharedIndex : sharedIndexes) {
                        String name = sharedIndex.getName();
                        if(name.equalsIgnoreCase(indexName))
                        {
                          entryContainer.clearDatabase(sharedIndex);
                          Collection<Index> shared = new ArrayList<Index>();
                          int id = System.identityHashCode(sharedIndex);
                          idContainerMap.putIfAbsent(id, sharedIndex);
                          shared.add(sharedIndex);
                          extensibleIndexMap.put(new IndexKey(attrType,
                                  ImportIndexType.EX_SHARED, 0), shared);
                          break;
                        }
                      }
                    }
                  }
                }
              }
            }
            else
            {
              clearAttributeIndexes(attrIndex, attrType);
            }
          }
        }
      }
    }


    private void clearAllIndexes() throws DatabaseException
    {
      for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
              suffix.getAttrIndexMap().entrySet()) {
        AttributeType attributeType = mapEntry.getKey();
        AttributeIndex attributeIndex = mapEntry.getValue();
        clearAttributeIndexes(attributeIndex, attributeType);
      }
      for(VLVIndex vlvIndex : suffix.getEntryContainer().getVLVIndexes()) {
        entryContainer.clearDatabase(vlvIndex);
      }
      clearDN2IDIndexes();
      if(entryContainer.getDN2URI() != null)
      {
        clearDN2URI();
      }
    }


    private void clearVLVIndex(String name)
            throws DatabaseException
    {
      VLVIndex vlvIndex = entryContainer.getVLVIndex(name);
      entryContainer.clearDatabase(vlvIndex);
      vlvIndexes.add(vlvIndex);
    }


    private void clearDN2URI() throws DatabaseException
    {
      entryContainer.clearDatabase(entryContainer.getDN2URI());
      dn2uri = entryContainer.getDN2URI();
    }


    private void clearDN2IDIndexes() throws DatabaseException
    {
      entryContainer.clearDatabase(entryContainer.getDN2ID());
      entryContainer.clearDatabase(entryContainer.getID2Children());
      entryContainer.clearDatabase(entryContainer.getID2Subtree());
      dn2id = entryContainer.getDN2ID();
    }


    private void clearAttributeIndexes(AttributeIndex attrIndex,
                                      AttributeType attrType)
            throws DatabaseException
    {
      Index partialAttrIndex;
      if(attrIndex.getSubstringIndex() != null)
      {
        partialAttrIndex = attrIndex.getSubstringIndex();
        int id = System.identityHashCode(partialAttrIndex);
        idContainerMap.putIfAbsent(id, partialAttrIndex);
        entryContainer.clearDatabase(partialAttrIndex);
        IndexKey indexKey =
                new IndexKey(attrType, ImportIndexType.SUBSTRING,
                        partialAttrIndex.getIndexEntryLimit());
        indexMap.put(indexKey, partialAttrIndex);
      }
      if(attrIndex.getOrderingIndex() != null)
      {
        partialAttrIndex = attrIndex.getOrderingIndex();
        int id = System.identityHashCode(partialAttrIndex);
        idContainerMap.putIfAbsent(id, partialAttrIndex);
        entryContainer.clearDatabase(partialAttrIndex);
        IndexKey indexKey =
                new IndexKey(attrType, ImportIndexType.ORDERING,
                        partialAttrIndex.getIndexEntryLimit());
        indexMap.put(indexKey, partialAttrIndex);
      }
      if(attrIndex.getEqualityIndex() != null)
      {
        partialAttrIndex = attrIndex.getEqualityIndex();
        int id = System.identityHashCode(partialAttrIndex);
        idContainerMap.putIfAbsent(id, partialAttrIndex);
        entryContainer.clearDatabase(partialAttrIndex);
        IndexKey indexKey =
                new IndexKey(attrType, ImportIndexType.EQUALITY,
                        partialAttrIndex.getIndexEntryLimit());
        indexMap.put(indexKey, partialAttrIndex);
      }
      if(attrIndex.getPresenceIndex() != null)
      {
        partialAttrIndex = attrIndex.getPresenceIndex();
        int id = System.identityHashCode(partialAttrIndex);
        idContainerMap.putIfAbsent(id, partialAttrIndex);
        entryContainer.clearDatabase(partialAttrIndex);
        IndexKey indexKey =
                new IndexKey(attrType, ImportIndexType.PRESENCE,
                        partialAttrIndex.getIndexEntryLimit());
        indexMap.put(indexKey, partialAttrIndex);

      }
      if(attrIndex.getApproximateIndex() != null)
      {
        partialAttrIndex = attrIndex.getApproximateIndex();
        int id = System.identityHashCode(partialAttrIndex);
        idContainerMap.putIfAbsent(id, partialAttrIndex);
        entryContainer.clearDatabase(partialAttrIndex);
        IndexKey indexKey =
                new IndexKey(attrType, ImportIndexType.APPROXIMATE,
                        partialAttrIndex.getIndexEntryLimit());
        indexMap.put(indexKey, partialAttrIndex);
      }
      Map<String,Collection<Index>> extensibleMap =
              attrIndex.getExtensibleIndexes();
      if(!extensibleMap.isEmpty()) {
        Collection<Index> subIndexes =
                attrIndex.getExtensibleIndexes().get(
                        EXTENSIBLE_INDEXER_ID_SUBSTRING);
        if(subIndexes != null) {
          for(Index subIndex : subIndexes) {
            entryContainer.clearDatabase(subIndex);
            int id = System.identityHashCode(subIndex);
            idContainerMap.putIfAbsent(id, subIndex);
          }
          extensibleIndexMap.put(new IndexKey(attrType,
                  ImportIndexType.EX_SUBSTRING, 0), subIndexes);
        }
        Collection<Index> sharedIndexes =
                attrIndex.getExtensibleIndexes().
                                             get(EXTENSIBLE_INDEXER_ID_SHARED);
        if(sharedIndexes !=null) {
          for(Index sharedIndex : sharedIndexes) {
            entryContainer.clearDatabase(sharedIndex);
            int id = System.identityHashCode(sharedIndex);
            idContainerMap.putIfAbsent(id, sharedIndex);
          }
          extensibleIndexMap.put(new IndexKey(attrType,
                  ImportIndexType.EX_SHARED, 0), sharedIndexes);
        }
      }
    }


    private
    void processEntry(Entry entry, EntryID entryID) throws DatabaseException,
            ConfigException, DirectoryException, JebException,
            InterruptedException
    {
      if(dn2id != null)
      {
        processDN2ID(suffix, entry.getDN(), entryID);
      }
      if(dn2uri != null)
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
      for(VLVIndex vlvIdx : suffix.getEntryContainer().getVLVIndexes()) {
        Transaction transaction = null;
        vlvIdx.addEntry(transaction, entryID, entry);
      }
    }


    private
    void processExtensibleIndexes(Entry entry, EntryID entryID) throws
            DatabaseException, DirectoryException, JebException,
            ConfigException, InterruptedException
    {
      for(Map.Entry<IndexKey, Collection<Index>> mapEntry :
              this.extensibleIndexMap.entrySet()) {
        IndexKey key = mapEntry.getKey();
        AttributeType attrType = key.getAttributeType();
        if(entry.hasAttribute(attrType)) {
          Collection<Index> indexes = mapEntry.getValue();
          for(Index index : indexes) {
            processAttribute(index, entry, entryID, key);
          }
        }
      }
    }


    private void
    processIndexes(Entry entry, EntryID entryID) throws
            DatabaseException, DirectoryException, JebException,
            ConfigException, InterruptedException
    {
      for(Map.Entry<IndexKey, Index> mapEntry :
              indexMap.entrySet()) {
        IndexKey key = mapEntry.getKey();
        AttributeType attrType = key.getAttributeType();
        if(entry.hasAttribute(attrType)) {
          ImportIndexType indexType = key.getIndexType();
          Index index = mapEntry.getValue();
          if(indexType == ImportIndexType.SUBSTRING)
          {
            processAttribute(index, entry, entryID,
                    new IndexKey(attrType, ImportIndexType.SUBSTRING,
                            index.getIndexEntryLimit()));
          }
          else
          {
            processAttribute(index, entry, entryID,
                    new IndexKey(attrType, indexType,
                                 index.getIndexEntryLimit()));
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
  }

  /**
   * This class reports progress of rebuild index processing at fixed
   * intervals.
   */
  class RebuildFirstPhaseProgressTask extends TimerTask
  {
    /**
     * The number of records that had been processed at the time of the
     * previous progress report.
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
     * @throws DatabaseException If an error occurred while accessing the JE
     *                           database.
     */
    public RebuildFirstPhaseProgressTask() throws DatabaseException
    {
      previousTime = System.currentTimeMillis();
      prevEnvStats = rootContainer.getEnvironmentStats(new StatsConfig());
    }

    /**
     * The action to be performed by this timer task.
     */
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
      float rate = 1000f*deltaCount / deltaTime;
      float completed = 0;
      if(rebuildManager.getTotEntries() > 0)
      {
        completed = 100f*entriesProcessed / rebuildManager.getTotEntries();
      }
      Message message = NOTE_JEB_REBUILD_PROGRESS_REPORT.get(completed,
                      entriesProcessed, rebuildManager.getTotEntries(), rate);
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
          cacheMissRate = nCacheMiss/(float)deltaCount;
        }
        message = NOTE_JEB_REBUILD_CACHE_AND_MEMORY_REPORT.get(
                freeMemory, cacheMissRate);
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
   * This class reports progress of first phase of import processing at
   * fixed intervals.
   */
  private final class FirstPhaseProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been read at the time of the
     * previous progress report.
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
        previousStats =
                rootContainer.getEnvironmentStats(new StatsConfig());
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
      message = NOTE_JEB_IMPORT_PROGRESS_REPORT.get(entriesRead,
              entriesIgnored, entriesRejected, 0, rate);
      logError(message);
      try
      {
        Runtime runTime = Runtime.getRuntime();
        long freeMemory = runTime.freeMemory()/MB;
        EnvironmentStats environmentStats;

        //If first phase skip DN validation is specified use the root container
        //stats, else use the temporary environment stats.
        if(skipDNValidation)
        {
          environmentStats =
                  rootContainer.getEnvironmentStats(new StatsConfig());
        }
        else
        {
          environmentStats = tmpEnv.getEnvironmentStats(new StatsConfig());
        }
        long nCacheMiss = environmentStats.getNCacheMiss() -
                previousStats.getNCacheMiss();

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
        long cleanerEntriesRead =
                environmentStats.getNCleanerEntriesRead();
        long cleanerINCleaned = environmentStats.getNINsCleaned();
        long checkPoints = environmentStats.getNCheckpoints();
        if (evictPasses != 0)
        {
          if (!evicting)
          {
            evicting = true;
            evictionEntryCount = reader.getEntriesRead();
            message =
                    NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED
                            .get(evictionEntryCount);
            logError(message);
          }
          message =
                  NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS.get(
                          evictPasses, evictNodes, evictBinsStrip);
          logError(message);
        }
        if (cleanerRuns != 0)
        {
          message =
                  NOTE_JEB_IMPORT_LDIF_CLEANER_STATS.get(cleanerRuns,
                          cleanerDeletions, cleanerEntriesRead,
                          cleanerINCleaned);
          logError(message);
        }
        if (checkPoints > 1)
        {
          message =
                  NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS.get(checkPoints);
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
  class SecondPhaseProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been read at the time of the
     * previous progress report.
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
     * @param  latestCount The latest count of entries processed in phase one.
     */
    public SecondPhaseProgressTask (long latestCount)
    {
      previousTime = System.currentTimeMillis();
      this.latestCount = latestCount;
      try
      {
        previousStats =
                rootContainer.getEnvironmentStats(new StatsConfig());
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
        long nCacheMiss = environmentStats.getNCacheMiss() -
                          previousStats.getNCacheMiss();

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
                  NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED_STATS.get(
                          evictPasses, evictNodes, evictBinsStrip);
          logError(message);
        }
        if (cleanerRuns != 0)
        {
          message =
                  NOTE_JEB_IMPORT_LDIF_CLEANER_STATS.get(cleanerRuns,
                          cleanerDeletions, cleanerEntriesRead,
                          cleanerINCleaned);
          logError(message);
        }
        if (checkPoints > 1)
        {
          message =
                  NOTE_JEB_IMPORT_LDIF_BUFFER_CHECKPOINTS.get(checkPoints);
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
      for(IndexManager indexMgrDN : DNIndexMgrList)
      {
        indexMgrDN.printStats(deltaTime);
      }
      //Do non-DN index managers.
      for(IndexManager indexMgr : indexMgrList)
      {
        indexMgr.printStats(deltaTime);
      }
    }
  }


  /**
   * A class to hold information about the entry determined by the LDIF reader.
   * Mainly the suffix the entry belongs under and the ID assigned to it by the
   * reader.
   *
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
     * @param suffix The suffix associated with the entry.
     */
    public void setSuffix(Suffix suffix)
    {
      this.suffix = suffix;
    }

    /**
     * Set the entry's ID.
     *
     * @param entryID The entry ID to set the entry ID to.
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
   *
   */
  public enum ImportIndexType {
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
     * The extensible sub-string  index type.
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
   * This class is used as an index key for hash maps that need to
   * process multiple suffix index elements into a single queue and/or maps
   * based on both attribute type and index type
   * (ie., cn.equality, sn.equality,...).
   */
  public class IndexKey {

    private final AttributeType attributeType;
    private final ImportIndexType indexType;
    private final int entryLimit;


   /**
     * Create index key instance using the specified attribute type, index type
     * and index entry limit.
     *
     * @param attributeType The attribute type.
     * @param indexType The index type.
     * @param entryLimit The entry limit for the index.
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
     * Only returns {@code true} if the attribute type and index type are
     * equal.
     *
     * @param obj the object to compare.
     * @return {@code true} if the objects are equal, or {@code false} if they
     *         are not.
     */
    public boolean equals(Object obj)
    {
      if (obj instanceof IndexKey) {
        IndexKey oKey = (IndexKey) obj;
        if(attributeType.equals(oKey.getAttributeType()) &&
           indexType.equals(oKey.getIndexType()))
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
     * Return the index key name, which is the attribute type primary name,
     * a period, and the index type name. Used for building file names and
     * progress output.
     *
     * @return  The index key name.
     */
    public String getName()
    {
      return attributeType.getPrimaryName() + "." +
              StaticUtils.toLowerCase(indexType.name());
    }

    /**
     * Return the entry limit associated with the index.
     *
     * @return  The entry limit.
     */
    public int getEntryLimit()
    {
      return entryLimit;
    }
  }


  /**
   * The temporary enviroment will be shared when multiple suffixes are being
   * processed. This interface is used by those suffix instance to do parental
   * checking of the DN cache.
   */
  public static interface DNCache {

    /**
     * Returns {@code true} if the specified DN is contained in the DN cache,
     * or {@code false} otherwise.
     *
     * @param dn  The DN to check the presence of.
     * @return {@code true} if the cache contains the DN, or {@code false} if it
     *                      is not.
     * @throws DatabaseException If an error occurs reading the database.
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
     * @param envPath The file path to create the enviroment under.
     * @throws DatabaseException If an error occurs either creating the
     *                           environment or the DN database.
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
      envConfig.setConfigParam(EnvironmentConfig.MAX_MEMORY,
                                 Long.toString(tmpEnvCacheSize));
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
      for (int i = 0; i < b.length; i++)
      {
        hash ^= b[i];
        hash *= FNV_PRIME;
      }
      return JebFormat.entryIDToDatabase(hash);
    }

    /**
     * Shutdown the temporary environment.
     * @throws JebException If error occurs.
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
     * @param dn The DN to insert in the cache.
     * @param val A database entry to use in the insert.
     * @param key A database entry to use in the insert.
     * @return  {@code true} if the DN was inserted in the cache, or
     *          {@code false} if the DN exists in the cache already and could
     *           not be inserted.
     * @throws JebException If an error occurs accessing the database.
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
        if(status == OperationStatus.KEYEXIST)
        {
          DatabaseEntry dns = new DatabaseEntry();
          inserted = false;
          status = cursor.getSearchKey(key, dns, LockMode.RMW);
          if(status == OperationStatus.NOTFOUND)
          {
            Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                    "Search DN cache failed.");
            throw new JebException(message);
          }
          if(!isDNMatched(dns, dnBytes))
          {
            addDN(dns, cursor, dnBytes);
            inserted = true;
          }
        }
      }
      finally
      {
        if(cursor != null)
        {
          cursor.close();
        }
      }
      return inserted;
    }

    //Add the DN to the DNs as because of a hash collision.
    private void addDN(DatabaseEntry val, Cursor cursor,
                       byte[] dnBytes) throws JebException
    {
      int pos = 0;
      byte[] bytes = val.getData();
      int pLen = PackedInteger.getWriteIntLength(dnBytes.length);
      int totLen = bytes.length + (pLen + dnBytes.length);
      byte[] newRec = new byte[totLen];
      System.arraycopy(bytes, 0, newRec, 0, bytes.length);
      pos = bytes.length;
      pos = PackedInteger.writeInt(newRec, pos, dnBytes.length);
      System.arraycopy(dnBytes, 0, newRec, pos, dnBytes.length);
      DatabaseEntry newVal = new DatabaseEntry(newRec);
      OperationStatus status = cursor.putCurrent(newVal);
      if(status != OperationStatus.SUCCESS)
      {
        Message message = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                "Add of DN to DN cache failed.");
        throw new JebException(message);
      }
    }

    //Return true if the specified DN is in the DNs saved as a result of hash
    //collisions.
    private boolean isDNMatched(DatabaseEntry dns, byte[] dnBytes)
    {
      int pos = 0, len = 0;
      byte[] bytes = dns.getData();
      while(pos < dns.getData().length)
      {
        int pLen = PackedInteger.getReadIntLength(bytes, pos);
        len =  PackedInteger.readInt(bytes, pos);
        if(dnComparator.compare(bytes, pos + pLen, len, dnBytes,
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
     * @param dn A DN check for.
     * @return  {@code true} if the specified DN is in the temporary DN cache,
     *          or {@code false} if it is not.
     */
    public boolean contains(DN dn)
    {
      boolean dnExists = false;
      Cursor cursor = null;
      DatabaseEntry key = new DatabaseEntry();
      byte[] dnBytes = StaticUtils.getBytes(dn.toNormalizedString());
      key.setData(hashCode(dnBytes));
      try {
        cursor = dnCache.openCursor(null, CursorConfig.DEFAULT);
        DatabaseEntry dns = new DatabaseEntry();
        OperationStatus status =
                cursor.getSearchKey(key, dns, LockMode.DEFAULT);
        if(status == OperationStatus.SUCCESS)
        {
          dnExists = isDNMatched(dns, dnBytes);
        }
      }
      finally
      {
        if(cursor != null)
        {
          cursor.close();
        }
      }
      return dnExists;
    }

    /**
     * Return temporary environment stats.
     *
     * @param statsConfig A stats configuration instance.
     *
     * @return Environment stats.
     * @throws DatabaseException  If an error occurs retrieving the stats.
     */
    public EnvironmentStats getEnvironmentStats(StatsConfig statsConfig)
            throws DatabaseException
    {
      return environment.getStats(statsConfig);
    }
  }


  /**
   * Uncaught exception handler. Try and catch any uncaught exceptions, log
   * them and print a stack trace.
   */
  public
  class DefaultExceptionHandler implements Thread.UncaughtExceptionHandler {

    /**
     * {@inheritDoc}
     */
    public void uncaughtException(Thread t, Throwable e) {
      Message message =  ERR_JEB_IMPORT_UNCAUGHT_EXCEPTION.get(e.getMessage());
      logError(message);
      e.printStackTrace();
      System.exit(1);
    }
  }
}
