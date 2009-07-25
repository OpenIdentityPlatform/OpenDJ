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
import org.opends.server.backends.jeb.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.util.*;
import com.sleepycat.je.*;


/**
 * Performs a LDIF import.
 */
public class Importer
{
  private final int DRAIN_TO = 3;
  private final int TIMER_INTERVAL = 10000;
  private final int MB =  (1024 * 1024);
  private final int LDIF_READER_BUF_SIZE = 2 * MB;
  private final int MIN_IMPORT_MEM_REQUIRED = 16 * MB;
  private final int MAX_BUFFER_SIZE = 48 * MB;
  private final int MIN_BUFFER_SIZE = 1024 * 100;
  private final int MIN_READ_AHEAD_CACHE_SIZE = 4096;
  private final int MAX_DB_CACHE_SIZE = 128 * MB;
  private final int MIN_DB_CACHE_SIZE = 16 * MB;
  private final int MAX_DB_LOG_BUF_BYTES = 100 * MB;
  private final int MEM_PCT_PHASE_1 = 60;
  private final int MEM_PCT_PHASE_2 = 50;

  private final String DIRECT_PROPERTY = "import.directphase2";

  private final AtomicInteger bufferCount = new AtomicInteger(0);
  private final File tempDir;
  private final int indexCount, threadCount;
  private final boolean dn2idPhase2;
  private final LDIFImportConfig config;
  private final ByteBuffer directBuffer;

  private RootContainer rootContainer;
  private LDIFReader reader;
  private int bufferSize;
  private long dbCacheSize = 0, dbLogBufSize = 0;


  //The executor service used for the sort tasks.
  private ExecutorService sortService;

  //The executor service used for the index processing tasks.
  private ExecutorService indexProcessService;

  //Queue of free index buffers -- used to re-cycle index buffers;
  private final BlockingQueue<IndexBuffer> freeBufQue =
          new LinkedBlockingQueue<IndexBuffer>();

  //Map of DB containers to que of index buffers.  Used to allocate sorted
  //index buffers to a index writer thread.
  private final
  Map<DatabaseContainer, BlockingQueue<IndexBuffer>> containerQueMap =
          new LinkedHashMap<DatabaseContainer, BlockingQueue<IndexBuffer>>();

  //Map of DB containers to index managers. Used to start phase 2.
  private final Map<DatabaseContainer, IndexManager> containerIndexMgrMap =
          new LinkedHashMap<DatabaseContainer, IndexManager>();

  //Futures used to indicate when the index file writers are done flushing
  //their work queues and have exited. End of phase one.
  private final List<Future<?>> indexWriterFutures;

  //List of index file writer tasks. Used to signal stopIndexWriterTasks to the
  //index file writer tasks when the LDIF file has been done.
  private final List<IndexFileWriterTask> indexWriterList;

  //Map of DNs to Suffix objects. Placeholder for when multiple suffixes are
  //supported.
  private final Map<DN, Suffix> dnSuffixMap = new LinkedHashMap<DN, Suffix>();

  /**
   * Create a new import job with the specified ldif import config.
   *
   * @param config The LDIF import config.
   * @param cfg The local DB backend config.
   * @throws IOException  If a problem occurs while opening the LDIF file for
   *                      reading.
   */
  public Importer(LDIFImportConfig config,
                  LocalDBBackendCfg cfg )
          throws IOException
  {
    this.config = config;
    threadCount = cfg.getImportThreadCount();
    indexCount = cfg.listLocalDBIndexes().length + 2;
    indexWriterList = new ArrayList<IndexFileWriterTask>(indexCount);
    indexWriterFutures = new CopyOnWriteArrayList<Future<?>>();
    File parentDir;
    if(config.getTmpDirectory() == null)
    {
      parentDir = getFileForPath("import-tmp");
    }
    else
    {
       parentDir = getFileForPath(config.getTmpDirectory());
    }
    tempDir = new File(parentDir, cfg.getBackendId());
    if(!tempDir.exists() && !tempDir.mkdirs())
    {
      Message msg = ERR_JEB_IMPORT_CREATE_TMPDIR_ERROR.get(
              String.valueOf(tempDir));
      throw new IOException(msg.toString());
    }
    if (tempDir.listFiles() != null)
    {
      for (File f : tempDir.listFiles())
      {
        f.delete();
      }
    }
    dn2idPhase2 = config.getDNCheckPhase2();
    String propString = System.getProperty(DIRECT_PROPERTY);
    if(propString != null)
    {
      int directSize = Integer.valueOf(propString);
      directBuffer = ByteBuffer.allocateDirect(directSize);
    }
    else
    {
     directBuffer = null;
    }
  }

  private void getBufferSizes(long availMem, int buffers)
  {
    long mem = availMem - (MAX_DB_CACHE_SIZE + MAX_DB_LOG_BUF_BYTES);
    bufferSize = (int) (mem/buffers);
    if(bufferSize >= MIN_BUFFER_SIZE)
    {
      dbCacheSize =  MAX_DB_CACHE_SIZE;
      dbLogBufSize = MAX_DB_LOG_BUF_BYTES;
      if(bufferSize > MAX_BUFFER_SIZE)
      {
        bufferSize = MAX_BUFFER_SIZE;
      }
    }
    else
    {
      mem = availMem - MIN_DB_CACHE_SIZE - (MIN_DB_CACHE_SIZE * 7) / 100;
      bufferSize = (int) (mem/buffers);
      dbCacheSize =  MIN_DB_CACHE_SIZE;
      if(bufferSize < MIN_BUFFER_SIZE)
      {
        System.out.println("Log size less than default -- give it a try");
        bufferSize = MIN_BUFFER_SIZE;
      }
      else
      {
        long constrainedMem = mem - (buffers * MIN_BUFFER_SIZE);
        bufferSize = (int) ((buffers * MIN_BUFFER_SIZE) +
                            (constrainedMem * 50/100));
        bufferSize /= buffers;
        dbCacheSize = MIN_DB_CACHE_SIZE + (constrainedMem * 50/100);
      }
    }
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

  /**
   * Calculate buffer sizes and initialize JEB properties based on memory.
   *
   * @param envConfig The environment config to use in the calculations.
   *
   * @throws InitializationException If a problem occurs during calculation.
   */
  public void init(EnvironmentConfig envConfig)
          throws InitializationException
  {
    Message msg;
    Runtime runtime = Runtime.getRuntime();
    long freeMemory = runtime.freeMemory();
    long availMemImport = (freeMemory * MEM_PCT_PHASE_1) / 100;
    int phaseOneBuffers = 2 * (indexCount * threadCount);
    msg = NOTE_JEB_IMPORT_LDIF_TOT_MEM_BUF.get(availMemImport, phaseOneBuffers);
    logError(msg);
    if (availMemImport < MIN_IMPORT_MEM_REQUIRED)
    {
      msg = ERR_IMPORT_LDIF_LACK_MEM.get(16);
      throw new InitializationException(msg);
    }
    getBufferSizes(availMemImport, phaseOneBuffers);
    envConfig.setConfigParam("je.maxMemory", Long.toString(dbCacheSize));
    msg = NOTE_JEB_IMPORT_LDIF_DB_MEM_BUF_INFO.get(dbCacheSize, bufferSize);
    logError(msg);
    if(dbLogBufSize != 0)
    {
      envConfig.setConfigParam("je.log.totalBufferBytes",
              Long.toString(dbLogBufSize));
      msg = NOTE_JEB_IMPORT_LDIF_LOG_BYTES.get(dbLogBufSize);
      logError(msg);
    }
    return;
  }


  private void initIndexBuffers(int threadCount)
  {
    int bufferCount = 2 * (indexCount * threadCount);
    for(int i = 0; i < bufferCount; i++)
    {
      IndexBuffer b = IndexBuffer.createIndexBuffer(bufferSize);
      freeBufQue.add(b);
    }
  }



  private void initSuffixes()
          throws ConfigException, InitializationException
  {
    Iterator<EntryContainer> i = rootContainer.getEntryContainers().iterator();
    EntryContainer ec = i.next();
    Suffix suffix = Suffix.createSuffixContext(ec, config, rootContainer);
    dnSuffixMap.put(ec.getBaseDN(), suffix);
  }



  /**
   * Import a ldif using the specified root container.
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
   */
  public LDIFImportResult
  processImport(RootContainer rootContainer) throws ConfigException,
          InitializationException, IOException, JebException,
          InterruptedException, ExecutionException
  {
    this.rootContainer = rootContainer;
    this.reader = new LDIFReader(config, rootContainer, LDIF_READER_BUF_SIZE);
    Message message =
            NOTE_JEB_IMPORT_STARTING.get(DirectoryServer.getVersionString(),
                    BUILD_ID, REVISION_NUMBER);
    logError(message);
    message = NOTE_JEB_IMPORT_THREAD_COUNT.get(threadCount);
    logError(message);
    RuntimeInformation.logInfo();
    initSuffixes();
    long startTime = System.currentTimeMillis();
    processPhaseOne();
    processPhaseTwo();
    setIndexesTrusted();
    tempDir.delete();
    long finishTime = System.currentTimeMillis();
    long importTime = (finishTime - startTime);
    float rate = 0;
    if (importTime > 0)
      rate = 1000f * reader.getEntriesRead() / importTime;
    message = NOTE_JEB_IMPORT_FINAL_STATUS.get(reader.getEntriesRead(),
            reader.getEntriesRead(), reader.getEntriesIgnored(), reader
                    .getEntriesRejected(), 0, importTime / 1000, rate);
    logError(message);
    return new LDIFImportResult(reader.getEntriesRead(), reader
            .getEntriesRejected(), reader.getEntriesIgnored());
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
      Message msg = NOTE_JEB_IMPORT_LDIF_TRUSTED_FAILED.get(ex.getMessage());
      throw new JebException(msg);
    }
  }


  private void processPhaseOne() throws InterruptedException, ExecutionException
  {
    initIndexBuffers(threadCount);
    FirstPhaseProgressTask progressTask = new FirstPhaseProgressTask();
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(progressTask, TIMER_INTERVAL, TIMER_INTERVAL);
    indexProcessService = Executors.newFixedThreadPool(2 * indexCount);
    sortService = Executors.newFixedThreadPool(threadCount);

    //Import tasks are collective tasks.
    List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(threadCount);
    for (int i = 0; i < threadCount; i++)
    {
      tasks.add(new ImportTask());
    }
    ExecutorService execService = Executors.newFixedThreadPool(threadCount);
      List<Future<Void>> results = execService.invokeAll(tasks);
      for (Future<Void> result : results)
        assert result.isDone();
    stopIndexWriterTasks();
    for (Future<?> result : indexWriterFutures)
    {
        result.get();
    }
    execService.shutdown();
    freeBufQue.clear();
    sortService.shutdown();
    timer.cancel();
  }



  private void processPhaseTwo() throws InterruptedException
  {
    SecondPhaseProgressTask progress2Task =
            new SecondPhaseProgressTask(containerIndexMgrMap);
    Timer timer2 = new Timer();
    timer2.scheduleAtFixedRate(progress2Task, TIMER_INTERVAL, TIMER_INTERVAL);
    processIndexFiles();
    timer2.cancel();
  }



  private void processIndexFiles() throws InterruptedException
  {
    List<Callable<Void>> tasks = new ArrayList<Callable<Void>>(indexCount);
    if(bufferCount.get() == 0)
    {
      return;
    }
    int cacheSize =  cacheSizeFromFreeMemory();
    int p = 0;
    int offSet = 0;
    if(directBuffer != null)
    {
      cacheSize = cacheSizeFromDirectMemory();
    }
    for(Map.Entry<DatabaseContainer, IndexManager> e :
            containerIndexMgrMap.entrySet())
    {
      DatabaseContainer container = e.getKey();
      IndexManager indexMgr = e.getValue();
      boolean isDN2ID = false;
      if(container instanceof DN2ID)
      {
        isDN2ID = true;
      }
      if(directBuffer != null)
      {
        int cacheSizes = cacheSize * indexMgr.getBufferList().size();
        offSet += cacheSizes;
        directBuffer.limit(offSet);
        directBuffer.position(p);
        ByteBuffer b = directBuffer.slice();
        tasks.add(new IndexWriteDBTask(indexMgr, isDN2ID, b, cacheSize));
        p += cacheSizes;
      }
      else
      {
        tasks.add(new IndexWriteDBTask(indexMgr, isDN2ID, cacheSize));
      }
    }
    List<Future<Void>> results = indexProcessService.invokeAll(tasks);
    for (Future<Void> result : results)
      assert result.isDone();
    indexProcessService.shutdown();
  }


  private int cacheSizeFromDirectMemory()
  {
    int cap = directBuffer.capacity();
    int cacheSize = cap/bufferCount.get();
    if(cacheSize > bufferSize)
    {
      cacheSize = bufferSize;
    }
    System.out.println("Direct indexes begin Total bufferCount: " +
            bufferCount.get() + " cacheSize: " + cacheSize);
    return cacheSize;
  }

  private int cacheSizeFromFreeMemory()
  {
    Runtime runtime = Runtime.getRuntime();
    long availMemory = runtime.freeMemory()  * MEM_PCT_PHASE_2 / 100;
    int avgBufSize = (int)(availMemory / bufferCount.get());
    int cacheSize = Math.max(MIN_READ_AHEAD_CACHE_SIZE, avgBufSize);
    if(cacheSize > bufferSize)
    {
      cacheSize = bufferSize;
    }
    System.out.println("Indirect indexes begin Total bufferCount: " +
            bufferCount.get() + " avgBufSize: "
            + avgBufSize + " cacheSize: " + cacheSize);
    return cacheSize;
  }


  private void stopIndexWriterTasks()
  {
    IndexBuffer idxBuffer = IndexBuffer.createIndexBuffer(0);
    for(IndexFileWriterTask task : indexWriterList)
    {
      task.que.add(idxBuffer);
    }
  }


  /**
   * This task processes the LDIF file during phase 1.
   */
  private final class ImportTask implements Callable<Void> {
    private final Map<Suffix, Map<DatabaseContainer, IndexBuffer>> suffixMap =
            new HashMap<Suffix, Map<DatabaseContainer, IndexBuffer>>();
    private final Set<byte[]> insertKeySet = new HashSet<byte[]>();
    private final IndexBuffer.DNComparator dnComparator
            = new IndexBuffer.DNComparator();
    private final IndexBuffer.IndexComparator indexComparator =
            new IndexBuffer.IndexComparator();


    /**
     * {@inheritDoc}
     */
    public Void call() throws Exception
    {
      Suffix suffix = null;
      while (true)
      {
        if (config.isCancelled())
        {
          IndexBuffer idxBuffer = IndexBuffer.createIndexBuffer(0);
          freeBufQue.add(idxBuffer);
          return null;
        }
        Entry entry = reader.readEntry(dnSuffixMap);

        if (entry == null)
        {
          break;
        }
        DN entryDN = entry.getDN();
        EntryID entryID = (EntryID) entry.getAttachment();
        //Temporary until multiple suffixes supported.
        if(suffix == null)
        {
             suffix = getMatchSuffix(entryDN, dnSuffixMap);
        }
        if(!suffixMap.containsKey(suffix))
        {
          suffixMap.put(suffix, new HashMap<DatabaseContainer, IndexBuffer>());
        }
        if(!dn2idPhase2)
        {
          if(!processParent(entryDN, entryID, entry, suffix))
          {
            suffix.removePending(entryDN);
            continue;
          }
          if(!suffix.getDN2ID().insert(null, entryDN, entryID))
          {
            suffix.removePending(entryDN);
             Message msg = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
             reader.rejectEntry(entry, msg);
            continue;
          }
          suffix.removePending(entryDN);
          processID2SC(entryID, entry, suffix);
        }
        else
        {
          processDN2ID(suffix, entryDN, entryID);
          suffix.removePending(entryDN);
        }
        suffix.getID2Entry().put(null, entryID, entry);
        processIndexes(suffix, entry, entryID);
      }
      flushIndexBuffers();
      if(!dn2idPhase2)
      {
        suffix.getEntryContainer().getID2Children().closeCursor();
        suffix.getEntryContainer().getID2Subtree().closeCursor();
      }
      return null;
    }


    private boolean processParent(DN entryDN, EntryID entryID, Entry entry,
                                  Suffix suffix) throws DatabaseException
    {
      EntryID parentID = null;
      DN parentDN =
              suffix.getEntryContainer().getParentWithinBase(entryDN);
      DN2ID dn2id = suffix.getDN2ID();
      if(dn2id.get(null, entryDN, LockMode.DEFAULT) != null)
      {
        Message msg = WARN_JEB_IMPORT_ENTRY_EXISTS.get();
        reader.rejectEntry(entry, msg);
        return false;
      }

      if (parentDN != null) {
        parentID = suffix.getParentID(parentDN);
        if (parentID == null) {
          dn2id.remove(null, entryDN);
          Message msg =
                      ERR_JEB_IMPORT_PARENT_NOT_FOUND.get(parentDN.toString());
           reader.rejectEntry(entry, msg);
          return false;
        }
      }
      ArrayList<EntryID> IDs;
      if (parentDN != null && suffix.getParentDN() != null &&
              parentDN.equals(suffix.getParentDN())) {
        IDs = new ArrayList<EntryID>(suffix.getIDs());
        IDs.set(0, entryID);
      }
      else
      {
        EntryID nodeID;
        IDs = new ArrayList<EntryID>(entryDN.getNumComponents());
        IDs.add(entryID);
        if (parentID != null)
        {
          IDs.add(parentID);
          EntryContainer ec = suffix.getEntryContainer();
          for (DN dn = ec.getParentWithinBase(parentDN); dn != null;
               dn = ec.getParentWithinBase(dn)) {
            if((nodeID =  getAncestorID(dn2id, dn)) == null) {
              return false;
            } else {
              IDs.add(nodeID);
            }
          }
        }
      }
      suffix.setParentDN(parentDN);
      suffix.setIDs(IDs);
      entry.setAttachment(IDs);
      return true;
    }

    private void processID2SC(EntryID entryID, Entry entry, Suffix suffix)
            throws DatabaseException
    {
      Set<byte[]> childKeySet = new HashSet<byte[]>();
      Set<byte[]> subtreeKeySet = new HashSet<byte[]>();
      Index id2children = suffix.getEntryContainer().getID2Children();
      Index id2subtree = suffix.getEntryContainer().getID2Subtree();
      id2children.indexer.indexEntry(entry, childKeySet);
      id2subtree.indexer.indexEntry(entry, subtreeKeySet);

      DatabaseEntry dbKey = new DatabaseEntry();
      DatabaseEntry dbVal = new DatabaseEntry();
      ImportIDSet idSet = new ImportIDSet();
      idSet.addEntryID(entryID, id2children.getIndexEntryLimit(),
              id2children.getMaintainCount());
      id2children.insert(idSet, childKeySet, dbKey, dbVal);

      DatabaseEntry dbSubKey = new DatabaseEntry();
      DatabaseEntry dbSubVal = new DatabaseEntry();
      ImportIDSet idSubSet = new ImportIDSet();
      idSubSet.addEntryID(entryID, id2subtree.getIndexEntryLimit(),
              id2subtree.getMaintainCount());
      id2subtree.insert(idSubSet, subtreeKeySet, dbSubKey, dbSubVal);
    }

    private EntryID getAncestorID(DN2ID dn2id, DN dn)
            throws DatabaseException
    {
      int i=0;
      EntryID nodeID = dn2id.get(null, dn, LockMode.DEFAULT);
      if(nodeID == null) {
        while((nodeID = dn2id.get(null, dn, LockMode.DEFAULT)) == null) {
          try {
            Thread.sleep(50);
            if(i == 3) {
              return null;
            }
            i++;
          } catch (Exception e) {
            return null;
          }
        }
      }
      return nodeID;
    }



    private void
    processIndexes(Suffix ctx, Entry entry, EntryID entryID) throws
            DatabaseException, DirectoryException, JebException, ConfigException
    {
      Transaction txn = null;
      Map<AttributeType, AttributeIndex> attrMap = ctx.getAttrIndexMap();
      for(Map.Entry<AttributeType, AttributeIndex> mapEntry :
              attrMap.entrySet()) {
        AttributeType attrType = mapEntry.getKey();
        if(entry.hasAttribute(attrType)) {
          AttributeIndex attributeIndex = mapEntry.getValue();
          Index index;
          if((index=attributeIndex.getEqualityIndex()) != null) {
            indexAttr(ctx, index, entry, entryID);
          }
          if((index=attributeIndex.getPresenceIndex()) != null) {
            indexAttr(ctx, index, entry, entryID);
          }
          if((index=attributeIndex.getSubstringIndex()) != null) {
            indexAttr(ctx, index, entry, entryID);
          }
          if((index=attributeIndex.getOrderingIndex()) != null) {
            indexAttr(ctx, index, entry, entryID);
          }
          if((index=attributeIndex.getApproximateIndex()) != null) {
            indexAttr(ctx, index, entry, entryID);
          }
          for(VLVIndex vlvIdx : ctx.getEntryContainer().getVLVIndexes()) {
            vlvIdx.addEntry(txn, entryID, entry);
          }
          Map<String,Collection<Index>> extensibleMap =
                  attributeIndex.getExtensibleIndexes();
          if(!extensibleMap.isEmpty()) {
            Collection<Index> subIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SUBSTRING);
            if(subIndexes != null) {
              for(Index subIndex: subIndexes) {
                indexAttr(ctx, subIndex, entry, entryID);
              }
            }
            Collection<Index> sharedIndexes =
                    attributeIndex.getExtensibleIndexes().get(
                            EXTENSIBLE_INDEXER_ID_SHARED);
            if(sharedIndexes !=null) {
              for(Index sharedIndex:sharedIndexes) {
                indexAttr(ctx, sharedIndex, entry, entryID);
              }
            }
          }
        }
      }
    }



    private void indexAttr(Suffix ctx, Index index, Entry entry,
                           EntryID entryID)
            throws DatabaseException, ConfigException
    {
      insertKeySet.clear();
      index.indexer.indexEntry(entry, insertKeySet);
      for(byte[] key : insertKeySet)
      {
        processKey(ctx, index, key, entryID, indexComparator, null);
      }
    }


    private void flushIndexBuffers() throws InterruptedException,
                 ExecutionException
    {
      Iterator<Suffix> i  = dnSuffixMap.values().iterator();
      Suffix suffix = i.next();
      for(Map<DatabaseContainer, IndexBuffer> map : suffixMap.values())
      {
        for(Map.Entry<DatabaseContainer, IndexBuffer> e : map.entrySet())
        {
          DatabaseContainer container = e.getKey();
          IndexBuffer indexBuffer = e.getValue();
          if(container instanceof DN2ID)
          {
            indexBuffer.setComparator(dnComparator);
          }
          else
          {
            indexBuffer.setComparator(indexComparator);
          }
          indexBuffer.setContainer(container);
          indexBuffer.setEntryContainer(suffix.getEntryContainer());
          Future<Void> future = sortService.submit(new SortTask(indexBuffer));
          future.get();
        }
      }
    }


    private void
    processKey(Suffix ctx, DatabaseContainer container, byte[] key,
               EntryID entryID,IndexBuffer.ComparatorBuffer<byte[]> comparator,
               EntryContainer entryContainer) throws ConfigException
    {
      IndexBuffer indexBuffer;
      Map<DatabaseContainer, IndexBuffer> conMap = suffixMap.get(ctx);
      if(!conMap.containsKey(container))
      {
        indexBuffer = getNewIndexBuffer();
        conMap.put(container, indexBuffer);
      }
      else
      {
        indexBuffer = conMap.get(container);
      }
      if(!indexBuffer.isSpaceAvailable(key))
      {
        indexBuffer.setContainer(container);
        indexBuffer.setComparator(comparator);
        indexBuffer.setEntryContainer(entryContainer);
        sortService.submit(new SortTask(indexBuffer));
        indexBuffer = getNewIndexBuffer();
        conMap.remove(container);
        conMap.put(container, indexBuffer);
      }
      indexBuffer.add(key, entryID);
    }


    private IndexBuffer getNewIndexBuffer() throws ConfigException
    {
      IndexBuffer indexBuffer = freeBufQue.poll();
      if(indexBuffer.isPoison())
      {
        Message msg = Message.raw(Category.JEB, Severity.SEVERE_ERROR,
                "Abort import - MPD");
        throw new ConfigException(msg);
      }
      return indexBuffer;
    }


    private void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
            throws ConfigException
    {
      DatabaseContainer dn2id = suffix.getDN2ID();
      byte[] dnBytes = StaticUtils.getBytes(dn.toNormalizedString());
      processKey(suffix, dn2id, dnBytes, entryID, dnComparator,
              suffix.getEntryContainer());

    }
  }

  /**
   * The task reads the temporary index files and writes their results to the
   * index database.
   */
  private final class IndexWriteDBTask implements Callable<Void> {

    private final IndexManager indexMgr;
    private final boolean isDN2ID;
    private final DatabaseEntry dbKey, dbValue;
    private final DN2ID dn2id;
    private final Index index;

    private final EntryContainer entryContainer;
    private final int id2ChildLimit;
    private final boolean id2ChildMCount;

    private TreeMap<DN,EntryID> parentIDMap = new TreeMap<DN,EntryID>();
    private DN parentDN, lastDN;
    private EntryID parentID, lastID;
    private final Map<byte[], ImportIDSet> id2childTree;
    private final Map<byte[], ImportIDSet> id2subtreeTree;
    private final int cacheSize;
    private ByteBuffer directBuffer = null;

    public IndexWriteDBTask(IndexManager indexMgr, boolean isDN2ID,
                            ByteBuffer b, int cacheSize)
    {
      this(indexMgr, isDN2ID, cacheSize);
      directBuffer = b;
    }

    public IndexWriteDBTask(IndexManager indexMgr, boolean isDN2ID,
                            int cacheSize)
    {
      this.indexMgr = indexMgr;
      this.entryContainer = indexMgr.entryContainer;
      this.isDN2ID = isDN2ID;
      this.dbKey = new DatabaseEntry();
      this.dbValue = new DatabaseEntry();
      this.cacheSize = cacheSize;
      if(isDN2ID)
      {
        this.dn2id = indexMgr.dn2id;
        this.index = null;
        id2ChildLimit = entryContainer.getID2Children().getIndexEntryLimit();
        id2ChildMCount = entryContainer.getID2Subtree().getMaintainCount();
        Comparator<byte[]> id2ChildComparator =
                entryContainer.getID2Children().getComparator();
        Comparator<byte[]> id2SubtreeComparator =
                entryContainer.getID2Subtree().getComparator();
        id2childTree =
                new TreeMap<byte[], ImportIDSet>(id2ChildComparator);
        id2subtreeTree =
                new TreeMap<byte[], ImportIDSet>(id2SubtreeComparator);
      }
      else
      {
        this.dn2id = null;
        this.index = indexMgr.getIndex();
        id2subtreeTree = null;
        id2childTree = null;
        id2ChildLimit = 0;
        id2ChildMCount = false;
      }
    }


    public Void call() throws Exception
    {

      Comparator<byte[]> comparator = indexMgr.getComparator();
      int limit = indexMgr.getLimit();
      boolean maintainCount = indexMgr.getMaintainCount();
      byte[] cKey = null;
      ImportIDSet cIDSet = null;
      indexMgr.init();
      List<Buffer> bufferList = indexMgr.getBufferList();
      SortedSet<Buffer> bufferSet = new TreeSet<Buffer>();
      int p = 0;
      int offSet = cacheSize;
      for(Buffer b : bufferList)
      {
        if(directBuffer != null)
        {
          directBuffer.position(p);
          directBuffer.limit(offSet);
          ByteBuffer slice = directBuffer.slice();
          b.init(indexMgr, slice, cacheSize);
          p += cacheSize;
          offSet += cacheSize;
        }
        else
        {
          b.init(indexMgr, null, cacheSize);
        }
        bufferSet.add(b);
      }
      while(!bufferSet.isEmpty())
      {
        Buffer b;
        b = bufferSet.first();
        if(b == null) {
          System.out.println("null b");
        }
        bufferSet.remove(b);
        byte[] key = b.getKey();
        ImportIDSet idSet = b.getIDSet();
        if(cKey == null)
        {
          cKey = key;
          cIDSet = idSet;
        }
        else
        {
          if(comparator.compare(key, cKey) != 0)
          {
            addToDB(cKey, cIDSet);
            indexMgr.incrKeyCount();
            cKey = key;
            cIDSet = idSet;
          }
          else
          {
            cIDSet.setKey(cKey);
            cIDSet.merge(idSet, limit, maintainCount);
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
        addToDB(cKey, cIDSet);
      }
      cleanUP();
      return null;
    }


    private void cleanUP() throws DatabaseException, DirectoryException,
            IOException
    {
      if(!isDN2ID) {
        index.closeCursor();
        Message msg = NOTE_JEB_IMPORT_LDIF_INDEX_CLOSE.get(index.getName());
        logError(msg);

      }
      else
      {
        if(dn2idPhase2)
        {
          flushSubTreeChildIndexes();
        }
      }
      indexMgr.setDone();
      indexMgr.close();
      indexMgr.deleteIndexFile();
    }


    private void flushSubTreeChildIndexes()
            throws DatabaseException, DirectoryException
    {
      Index  id2child = entryContainer.getID2Children();
      Set<Map.Entry<byte[], ImportIDSet>> id2childSet =
              id2childTree.entrySet();
      for(Map.Entry<byte[], ImportIDSet> e : id2childSet)
      {
        byte[] key = e.getKey();
        ImportIDSet idSet = e.getValue();
        dbKey.setData(key);
        id2child.insert(dbKey, idSet, dbValue);
      }
      id2child.closeCursor();
      Index id2subtree = entryContainer.getID2Subtree();
      Set<Map.Entry<byte[], ImportIDSet>> subtreeSet =
              id2subtreeTree.entrySet();
      for(Map.Entry<byte[], ImportIDSet> e : subtreeSet)
      {
        byte[] key = e.getKey();
        ImportIDSet idSet = e.getValue();
        dbKey.setData(key);
        id2subtree.insert(dbKey, idSet, dbValue);
      }
      id2subtree.closeCursor();
      Message msg =
             NOTE_JEB_IMPORT_LDIF_DN_CLOSE.get(indexMgr.getTotDNCount());
      logError(msg);
    }


    private void addToDB(byte[] key, ImportIDSet record)
            throws InterruptedException, DatabaseException, DirectoryException
    {
      record.setKey(key);
      if(!this.isDN2ID)
      {
        addIndex(record);
      }
      else
      {
        if(dn2idPhase2)
        {
          addDN2ID(record);
        }
      }
    }


    private void id2Subtree(EntryContainer ec, EntryID childID,
                            int limit, boolean mCount) throws DatabaseException
    {
      ImportIDSet idSet;
      if(!id2subtreeTree.containsKey(parentID.getDatabaseEntry().getData()))
      {
        idSet = new ImportIDSet();
        id2subtreeTree.put(parentID.getDatabaseEntry().getData(), idSet);
      }
      else
      {
        idSet = id2subtreeTree.get(parentID.getDatabaseEntry().getData());
      }
      idSet.addEntryID(childID, limit, mCount);
      for (DN dn = ec.getParentWithinBase(parentDN); dn != null;
           dn = ec.getParentWithinBase(dn))
      {
        EntryID nodeID = parentIDMap.get(dn);
        if(!id2subtreeTree.containsKey(nodeID.getDatabaseEntry().getData()))
        {
          idSet = new ImportIDSet();
          id2subtreeTree.put(nodeID.getDatabaseEntry().getData(), idSet);
        }
        else
        {
          idSet = id2subtreeTree.get(nodeID.getDatabaseEntry().getData());
        }
        idSet.addEntryID(childID, limit, mCount);
      }
    }

    private void id2child(EntryID childID, int limit, boolean mCount)
    {
      ImportIDSet idSet;
      if(!id2childTree.containsKey(parentID.getDatabaseEntry().getData()))
      {
        idSet = new ImportIDSet();
        id2childTree.put(parentID.getDatabaseEntry().getData(), idSet);
      }
      else
      {
        idSet = id2childTree.get(parentID.getDatabaseEntry().getData());
      }
      idSet.addEntryID(childID, limit, mCount);
    }

    private boolean checkParent(DN dn, EntryID id, EntryContainer ec)
    {
      if(parentIDMap.isEmpty())
      {
        parentIDMap.put(dn, id);
        return true;
      }
      else if(lastDN != null && lastDN.isAncestorOf(dn))
      {
        parentIDMap.put(lastDN, lastID);
        parentDN = lastDN;
        parentID = lastID;
        lastDN = dn;
        lastID = id;
        return true;
      }
      else if(parentIDMap.lastKey().isAncestorOf(dn))
      {
        parentDN = parentIDMap.lastKey();
        parentID = parentIDMap.get(parentDN);
        lastDN = dn;
        lastID = id;
        return true;
      }
      else
      {
        DN pDN = ec.getParentWithinBase(dn);
        if(parentIDMap.containsKey(pDN)) {
          DN lastKey = parentIDMap.lastKey();
          Map<DN, EntryID> subMap = parentIDMap.subMap(pDN, lastKey);
          for(Map.Entry<DN, EntryID> e : subMap.entrySet())
          {
            subMap.remove(e.getKey());
          }
          parentDN = pDN;
          parentID = parentIDMap.get(pDN);
          lastDN = dn;
          lastID = id;
        }
        else
        {
          Message msg = NOTE_JEB_IMPORT_LDIF_DN_NO_PARENT.get(dn.toString());
          Entry e = new Entry(dn, null, null, null);
          reader.rejectEntry(e, msg);
          return false;
        }
      }
      return true;
    }

    private void addDN2ID(ImportIDSet record)
            throws DatabaseException, DirectoryException
    {
      DatabaseEntry idVal = new DatabaseEntry();
      dbKey.setData(record.getKey());
      idVal.setData(record.toDatabase());
      DN dn = DN.decode(ByteString.wrap(dbKey.getData()));
      EntryID entryID = new EntryID(idVal);
      if(!checkParent(dn, entryID, entryContainer))
      {
        return;
      }
      dn2id.putRaw(null, dbKey, idVal);
      indexMgr.addTotDNCount(1);
      if(parentDN != null)
      {
        id2child(entryID, id2ChildLimit, id2ChildMCount);
        id2Subtree(entryContainer,
                entryID, id2ChildLimit, id2ChildMCount);
      }
    }


    private void addIndex(ImportIDSet record) throws DatabaseException
    {
      dbKey.setData(record.getKey());
      index.insert(dbKey, record, dbValue);
    }
  }


  /**
   * This task writes the temporary index files using the sorted buffers read
   * from a blocking queue.
   */
  private final class IndexFileWriterTask implements Runnable
  {
    private final IndexManager indexMgr;
    private final BlockingQueue<IndexBuffer> que;
    private final ByteArrayOutputStream byteStream =
            new ByteArrayOutputStream(2 * bufferSize);
    private final DataOutputStream dataStream;
    private long bufCount = 0;
    private final File file;
    private final SortedSet<IndexBuffer> indexSortedSet;
    private boolean poisonSeen = false;

    public IndexFileWriterTask(BlockingQueue<IndexBuffer> que,
                            IndexManager indexMgr) throws FileNotFoundException
    {
      this.que = que;
      file = indexMgr.getFile();
      this.indexMgr = indexMgr;
      BufferedOutputStream bufferedStream =
                   new BufferedOutputStream(new FileOutputStream(file), 2 * MB);
      dataStream = new DataOutputStream(bufferedStream);
      indexSortedSet = new TreeSet<IndexBuffer>();
    }


    public void run()
    {
      long offset = 0;
      List<IndexBuffer> l = new LinkedList<IndexBuffer>();
      try {
        while(true)
        {
          IndexBuffer indexBuffer = que.poll();
          if(indexBuffer != null)
          {
            long beginOffset = offset;
            long bufLen;
            if(!que.isEmpty())
            {
              que.drainTo(l, DRAIN_TO);
              l.add(indexBuffer);
              bufLen = writeIndexBuffers(l);
              for(IndexBuffer id : l)
              {
                id.reset();
              }
              freeBufQue.addAll(l);
              l.clear();
              if(poisonSeen)
              {
                break;
              }
            }
            else
            {
              if(indexBuffer.isPoison())
              {
                break;
              }
              bufLen = writeIndexBuffer(indexBuffer);
              indexBuffer.reset();
              freeBufQue.add(indexBuffer);
            }
            offset += bufLen;
            indexMgr.addBuffer(new Buffer(beginOffset, offset, bufCount));
            bufCount++;
            bufferCount.incrementAndGet();
          }
        }
        dataStream.close();
        indexMgr.setFileLength();
      }
      catch (IOException e) {
        Message msg =
                ERR_JEB_IMPORT_LDIF_INDEX_FILEWRITER_ERR.get(file.getName(),
                                    e.getMessage());
        logError(msg);
      }
    }


    private long writeIndexBuffer(IndexBuffer indexBuffer) throws IOException
    {
      int numKeys = indexBuffer.getNumberKeys();
      indexBuffer.setPos(-1);
      long bufLen = 0;
      byteStream.reset();
      for(int i = 0; i < numKeys; i++)
      {
        if(indexBuffer.getPos() == -1)
        {
          indexBuffer.setPos(i);
          byteStream.write(indexBuffer.getID(i));
          continue;
        }

        if(!indexBuffer.compare(i))
        {
          int recLen = indexBuffer.getKeySize();
          recLen += byteStream.size();
          recLen += 8;
          bufLen += recLen;
          indexBuffer.writeKey(dataStream);
          dataStream.writeInt(byteStream.size());
          byteStream.writeTo(dataStream);
          indexBuffer.setPos(i);
          byteStream.reset();
        }
        byteStream.write(indexBuffer.getID(i));
      }

      if(indexBuffer.getPos() != -1)
      {
        int recLen = indexBuffer.getKeySize();
        recLen += byteStream.size();
        recLen += 8;
        bufLen += recLen;
        indexBuffer.writeKey(dataStream);
        dataStream.writeInt(byteStream.size());
        byteStream.writeTo(dataStream);
      }
      return bufLen;
    }


    private long writeIndexBuffers(List<IndexBuffer> buffers)
            throws IOException
    {
      long id = 0;
      long bufLen = 0;
      byteStream.reset();
      for(IndexBuffer b : buffers)
      {
        if(b.isPoison())
        {
          poisonSeen = true;
        }
        else
        {
          b.setPos(0);
          b.setID(id++);
          indexSortedSet.add(b);
        }
      }
      byte[] saveKey = null;
      while(!indexSortedSet.isEmpty())
      {
        IndexBuffer b = indexSortedSet.first();
        indexSortedSet.remove(b);
        byte[] key = b.getKeyBytes(b.getPos());
        if(saveKey == null)
        {
          saveKey = key;
          byteStream.write(b.getID(b.getPos()));
        }
        else
        {
          if(!b.compare(saveKey))
          {
            int recLen = saveKey.length;
            recLen += byteStream.size();
            recLen += 8;
            bufLen += recLen;
            dataStream.writeInt(saveKey.length);
            dataStream.write(saveKey);
            dataStream.writeInt(byteStream.size());
            byteStream.writeTo(dataStream);
            byteStream.reset();
            saveKey = key;
            byteStream.write(b.getID(b.getPos()));
          }
          else
          {
            byteStream.write(b.getID(b.getPos()));
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
        int recLen = saveKey.length;
        recLen += byteStream.size();
        recLen += 8;
        bufLen += recLen;
        dataStream.writeInt(saveKey.length);
        dataStream.write(saveKey);
        dataStream.writeInt(byteStream.size());
        byteStream.writeTo(dataStream);
      }
      return bufLen;
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
      if (config.isCancelled())
      {
        return null;
      }
      indexBuffer.sort();
      if(containerQueMap.containsKey(indexBuffer.getContainer())) {
        BlockingQueue<IndexBuffer> q =
                containerQueMap.get(indexBuffer.getContainer());
        q.add(indexBuffer);
      }
      else
      {
        DatabaseContainer container = indexBuffer.getContainer();
        EntryContainer entryContainer = indexBuffer.getEntryContainer();
        createIndexWriterTask(container, entryContainer);
        BlockingQueue<IndexBuffer> q = containerQueMap.get(container);
        q.add(indexBuffer);
      }
      return null;
    }

    private void createIndexWriterTask(DatabaseContainer container,
                                       EntryContainer entryContainer)
    throws FileNotFoundException
    {
      synchronized(container) {
        if(containerQueMap.containsKey(container))
        {
          return;
        }
        IndexManager indexMgr;
        if(container instanceof Index)
        {
          Index index = (Index) container;
          indexMgr = new IndexManager(index);
        }
        else
        {
          DN2ID dn2id = (DN2ID) container;
          indexMgr = new IndexManager(dn2id, entryContainer);
        }
        containerIndexMgrMap.put(container, indexMgr);
        BlockingQueue<IndexBuffer> newQue =
                new ArrayBlockingQueue<IndexBuffer>(threadCount + 5);
        IndexFileWriterTask indexWriter =
                new IndexFileWriterTask(newQue, indexMgr);
        indexWriterList.add(indexWriter);
        indexWriterFutures.add(indexProcessService.submit(indexWriter));
        containerQueMap.put(container, newQue);
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
    private int keyLen, idLen;
    private byte[] key;
    private ImportIDSet idSet;


    public Buffer(long begin, long end, long id)
    {
      this.begin = begin;
      this.end = end;
      this.offset = 0;
      this.id = id;
    }


    private void init(IndexManager indexMgr, ByteBuffer b,
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
      getNextRecord();
    }


    private void loadCache() throws IOException
    {
      FileChannel fileChannel = indexMgr.getChannel();
      fileChannel.position(begin + offset);
      long leftToRead =  end - (begin + offset);
      long bytesToRead;
      if(leftToRead < cache.remaining())
      {
        int pos = cache.position();
        cache.limit((int) (pos + leftToRead));
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

    public byte[] getKey()
    {
      return key;
    }

    public ImportIDSet getIDSet()
    {
      return idSet;
    }

    public long getBufID()
    {
      return id;
    }

    public void getNextRecord()  throws IOException
    {
      getNextKey();
      getNextIDSet();
    }

    private int getInt()  throws IOException
    {
      ensureData(4);
      return cache.getInt();
    }

    private long getLong()  throws IOException
    {
      ensureData(8);
      return cache.getLong();
    }

    private void getBytes(byte[] b) throws IOException
    {
      ensureData(b.length);
      cache.get(b);
    }

    private void getNextKey() throws IOException, BufferUnderflowException
    {
      keyLen = getInt();
      key = new byte[keyLen];
        getBytes(key);
    }


    private void getNextIDSet() throws IOException, BufferUnderflowException
    {
      idLen = getInt();
      int idCount = idLen/8;
      idSet = new ImportIDSet(idCount);
      for(int i = 0; i < idCount; i++)
      {
        long l = getLong();
        idSet.addEntryID(l, indexMgr.getLimit(), indexMgr.getMaintainCount());
      }
    }


    private void ensureData(int len) throws IOException
    {
      if(cache.remaining() == 0)
      {
        cache.clear();
        loadCache();
        cache.flip();
      }
      else if(cache.remaining() < len)
      {
        cache.compact();
        loadCache();
        cache.flip();
      }
    }

    public int compareTo(Buffer o) {
      if(key == null) {
        if(id == o.getBufID())
        {
          return 0;
        }
        else
        {
          return id > o.getBufID() ? 1 : -1;
        }
      }
      if(this.equals(o))
      {
        return 0;
      }
      int rc = indexMgr.getComparator().compare(key, o.getKey());
      if(rc == 0)
      {
        if(idSet.isDefined())
        {
          return -1;
        }
        else if(o.getIDSet().isDefined())
        {
          return 1;
        }
        else if(idSet.size() == o.getIDSet().size())
        {
          rc = id > o.getBufID() ? 1 : -1;
        }
        else
        {
          rc = idSet.size() - o.getIDSet().size();
        }
      }
      return rc;
    }
  }

  /**
   * The index manager class is used to carry information about index processing
   * from phase 1 to phase 2.
   */
  private final class IndexManager
  {
    private final Index index;
    private final DN2ID dn2id;
    private final EntryContainer entryContainer;
    private final File file;


    private RandomAccessFile raf = null;
    private final List<Buffer> bufferList = new LinkedList<Buffer>();
    private final int limit;
    private long fileLength, bytesRead = 0;
    private final boolean maintainCount;
    private final Comparator<byte[]> comparator;
    private boolean done = false;
    private long totalDNS;
    private AtomicInteger keyCount = new AtomicInteger(0);
    private final String name;

    public IndexManager(Index index)
    {
      this.index = index;
      dn2id = null;
      file = new File(tempDir, index.getName());
      name = index.getName();
      limit = index.getIndexEntryLimit();
      maintainCount = index.getMaintainCount();
      comparator = index.getComparator();
      entryContainer = null;
    }


    public IndexManager(DN2ID dn2id, EntryContainer entryContainer)
    {
      index = null;
      this.dn2id = dn2id;
      file = new File(tempDir, dn2id.getName());
      limit = 1;
      maintainCount = false;
      comparator = dn2id.getComparator();
      this.entryContainer = entryContainer;
      name = dn2id.getName();
    }

    public void init() throws FileNotFoundException
    {
      raf = new RandomAccessFile(file, "r");
    }

    public FileChannel getChannel()
    {
      return raf.getChannel();
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

    public void deleteIndexFile()
    {
       file.delete();
    }

    public void close() throws IOException
    {
        raf.close();
    }

    public int getLimit()
    {
      return limit;
    }

    public boolean getMaintainCount()
    {
      return maintainCount;
    }

    public Comparator<byte[]> getComparator()
    {
      return comparator;
    }

    public Index getIndex()
    {
      return index;
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

    public void addTotDNCount(int delta)
    {
      this.totalDNS += delta;
    }


    public long getTotDNCount()
    {
      return totalDNS;
    }


    public void printStats(long deltaTime)
    {
      if(!done)
      {
        float rate = 1000f * keyCount.getAndSet(0) / deltaTime;
        Message msg = NOTE_JEB_IMPORT_LDIF_PHASE_TWO_REPORT.get(name,
                       (fileLength - bytesRead), rate);
        logError(msg);
      }
    }

    public void incrKeyCount()
    {
      keyCount.incrementAndGet();
    }
  }

  /**
   * This class reports progress of the import job at fixed intervals.
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
    private EnvironmentStats prevEnvStats;

    /**
     * The number of bytes in a megabyte. Note that 1024*1024 bytes may
     * eventually become known as a mebibyte(MiB).
     */
    public static final int bytesPerMegabyte = 1024 * 1024;

    // Determines if the ldif is being read.
    private boolean ldifRead = false;

    // Determines if eviction has been detected.
    private boolean evicting = false;

    // Entry count when eviction was detected.
    private long evictionEntryCount = 0;

    // Suspend output.
    private boolean pause = false;



    /**
     * Create a new import progress task.
     */
    public FirstPhaseProgressTask()
    {
      previousTime = System.currentTimeMillis();
      try
      {
        prevEnvStats =
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
      if (pause)
      {
        return;
      }
      if (!ldifRead)
      {
        long numRead = reader.getEntriesRead();
        long numIgnored = reader.getEntriesIgnored();
        long numRejected = reader.getEntriesRejected();
        float rate = 1000f * deltaCount / deltaTime;
        message =
                NOTE_JEB_IMPORT_PROGRESS_REPORT.get(numRead, numIgnored,
                        numRejected, 0, rate);
        logError(message);
      }
      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;
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
                NOTE_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(freeMemory,
                        cacheMissRate);
        logError(message);
        long evictPasses = envStats.getNEvictPasses();
        long evictNodes = envStats.getNNodesExplicitlyEvicted();
        long evictBinsStrip = envStats.getNBINsStripped();
        long cleanerRuns = envStats.getNCleanerRuns();
        long cleanerDeletions = envStats.getNCleanerDeletions();
        long cleanerEntriesRead = envStats.getNCleanerEntriesRead();
        long cleanerINCleaned = envStats.getNINsCleaned();
        long checkPoints = envStats.getNCheckpoints();
        if (evictPasses != 0)
        {
          if (!evicting)
          {
            evicting = true;
            if (!ldifRead)
            {
              evictionEntryCount = reader.getEntriesRead();
              message =
                      NOTE_JEB_IMPORT_LDIF_EVICTION_DETECTED
                              .get(evictionEntryCount);
              logError(message);
            }
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
        prevEnvStats = envStats;
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
   * This class reports progress of the import job at fixed intervals.
   */
  private final class SecondPhaseProgressTask extends TimerTask
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
    private EnvironmentStats prevEnvStats;

    /**
     * The number of bytes in a megabyte. Note that 1024*1024 bytes may
     * eventually become known as a mebibyte(MiB).
     */
    public static final int bytesPerMegabyte = 1024 * 1024;

    // Determines if eviction has been detected.
    private boolean evicting = false;

    // Suspend output.
    private boolean pause = false;

    private final Map<DatabaseContainer, IndexManager> containerIndexMgrMap;


    /**
     * Create a new import progress task.
     * @param containerIndexMgrMap Map of database container objects to
     *                             index manager objects.
     */
    public SecondPhaseProgressTask(Map<DatabaseContainer,
            IndexManager> containerIndexMgrMap)
    {
      previousTime = System.currentTimeMillis();
      this.containerIndexMgrMap = containerIndexMgrMap;
      try
      {
        prevEnvStats =
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
      if (pause)
      {
        return;
      }

      try
      {
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / bytesPerMegabyte;
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
                NOTE_JEB_IMPORT_CACHE_AND_MEMORY_REPORT.get(freeMemory,
                        cacheMissRate);
        logError(message);
        long evictPasses = envStats.getNEvictPasses();
        long evictNodes = envStats.getNNodesExplicitlyEvicted();
        long evictBinsStrip = envStats.getNBINsStripped();
        long cleanerRuns = envStats.getNCleanerRuns();
        long cleanerDeletions = envStats.getNCleanerDeletions();
        long cleanerEntriesRead = envStats.getNCleanerEntriesRead();
        long cleanerINCleaned = envStats.getNINsCleaned();
        long checkPoints = envStats.getNCheckpoints();
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
        prevEnvStats = envStats;
      }
      catch (DatabaseException e)
      {
        // Unlikely to happen and not critical.
      }
      previousCount = latestCount;
      previousTime = latestTime;

      for(Map.Entry<DatabaseContainer, IndexManager> e :
              containerIndexMgrMap.entrySet())
      {
        IndexManager indexMgr = e.getValue();
        indexMgr.printStats(deltaTime);
      }
    }
  }
}
