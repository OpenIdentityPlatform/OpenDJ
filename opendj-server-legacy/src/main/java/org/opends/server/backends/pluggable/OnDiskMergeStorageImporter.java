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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.admin.std.meta.BackendIndexCfgDefn.IndexType;
import org.opends.server.admin.std.server.BackendIndexCfg;
import org.opends.server.admin.std.server.PluggableBackendCfg;
import org.opends.server.backends.pluggable.AttributeIndex.MatchingRuleIndex;
import org.opends.server.backends.pluggable.ImportLDIFReader.EntryInformation;
import org.opends.server.backends.pluggable.OnDiskMergeBufferImporter.DNCache;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.Importer;
import org.opends.server.backends.pluggable.spi.ReadOperation;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.Storage.AccessMode;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.StorageStatus;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteOperation;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.Platform;

/**
 * This class provides the engine that performs both importing of LDIF files and
 * the rebuilding of indexes.
 */
final class OnDiskMergeStorageImporter
{
  private static UnsupportedOperationException notImplemented()
  {
    return new UnsupportedOperationException("Not implemented");
  }

  /** Data to put into id2entry tree. */
  private static final class Id2EntryData
  {
    private final Suffix suffix;
    private final EntryID entryID;
    private final Entry entry;

    public Id2EntryData(Suffix suffix, EntryID entryID, Entry entry)
    {
      this.suffix = suffix;
      this.entryID = entryID;
      this.entry = entry;
    }

    private void put(WriteableTransaction txn) throws DirectoryException
    {
      suffix.getID2Entry().put(txn, entryID, entry);
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName()
          + "(suffix=" + suffix
          + ", entryID=" + entryID
          + ", entry=" + entry + ")";
    }
  }

  /** Runnable putting data into id2entry tree in batches. */
  private final class Id2EntryPutTask implements Runnable
  {
    private static final int BATCH_SIZE = 100;

    private volatile boolean moreData = true;
    private final Storage storage;
    private final NavigableSet<Id2EntryData> dataToPut = new ConcurrentSkipListSet<>(new Comparator<Id2EntryData>()
    {
      @Override
      public int compare(Id2EntryData o1, Id2EntryData o2)
      {
        return o1.entryID.compareTo(o2.entryID);
      }
    });

    private Id2EntryPutTask(Storage storage)
    {
      this.storage = storage;
    }

    private void put(Suffix suffix, EntryID entryID, Entry entry)
    {
      dataToPut.add(new Id2EntryData(suffix, entryID, entry));

      if (enoughDataToPut())
      {
        synchronized (dataToPut)
        {
          dataToPut.notify();
        }
      }
    }

    @Override
    public void run()
    {
      try
      {
        while (!isCanceled() && moreData)
        {
          if (enoughDataToPut())
          {
            put(BATCH_SIZE);
          }
          else
          {
            synchronized (dataToPut)
            {
              if (moreData)
              {
                dataToPut.wait();
              }
            }
          }
        }
        while (!isCanceled() && !dataToPut.isEmpty())
        {
          put(BATCH_SIZE);
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    private boolean enoughDataToPut()
    {
      return dataToPut.size() > BATCH_SIZE;
    }

    private void put(final int batchSize) throws Exception
    {
      storage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          int count = 0;
          while (!dataToPut.isEmpty() && count < batchSize)
          {
            dataToPut.pollFirst().put(txn);
            count++;
          }
        }
      });
    }

    private void finishedWrites()
    {
      moreData = false;

      synchronized (dataToPut)
      {
        dataToPut.notify();
      }
    }

    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder("[");
      Iterator<Id2EntryData> it = dataToPut.iterator();
      if (it.hasNext())
      {
        sb.append(it.next().entryID.longValue());
      }
      while (it.hasNext())
      {
        sb.append(",");
        sb.append(it.next().entryID.longValue());
      }
      sb.append("]");
      return super.toString();
    }
  }

  /**
   * Represents an on-disk buffer file, accessed via memory mapped files.
   * <p>
   * Data to write is appended in-memory before being dumped into a {@link MappedByteBuffer}
   * for writing to disk.
   */
  private static final class Buffer
  {
    private final File file;
    private final FileChannel fileChannel;
    private final List<Integer> bufferPositions = new ArrayList<>();
    /** TODO JNR offer configuration for this. */
    private int bufferSize = 1024;

    // FIXME this is not thread safe yet!!!
    /**
     * Maps {@link ByteSequence} keys to (conflicting) values.
     * <p>
     * This will be persisted once {@link #maximumExpectedSizeOnDisk} reaches the
     * {@link #bufferSize}.
     */
    private ConcurrentNavigableMap<ByteSequence, Set<ByteSequence>> inMemoryStore = new ConcurrentSkipListMap<>();
    /** Projected occupied disk for the data stored in {@link #inMemoryStore}. */
    private int maximumExpectedSizeOnDisk;

    private Buffer(File file) throws FileNotFoundException
    {
      file.getParentFile().mkdirs();
      this.file = file;
      this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
      this.bufferPositions.add(0);
    }

    void putKeyValue(ByteSequence key, ByteSequence value) throws IOException
    {
      int recordSize = INT_SIZE + key.length() + INT_SIZE + value.length();
      if (bufferSize < maximumExpectedSizeOnDisk + recordSize)
      {
        copyToDisk();
        inMemoryStore.clear();
        maximumExpectedSizeOnDisk = 0;
      }

      Set<ByteSequence> values = inMemoryStore.get(key);
      if (values == null)
      {
        values = new ConcurrentSkipListSet<>();
        Set<ByteSequence> existingValues = inMemoryStore.putIfAbsent(key, values);
        if (existingValues != null)
        {
          values = existingValues;
        }
      }
      values.add(value);
      maximumExpectedSizeOnDisk += recordSize;
    }

    private void copyToDisk() throws IOException
    {
      MappedByteBuffer byteBuffer = nextBuffer();
      for (Map.Entry<ByteSequence, Set<ByteSequence>> mapEntry : inMemoryStore.entrySet())
      {
        ByteSequence key = mapEntry.getKey();
        for (ByteSequence value : mapEntry.getValue())
        {
          put(byteBuffer, key);
          put(byteBuffer, value);
        }
      }
      if (byteBuffer.position() != maximumExpectedSizeOnDisk)
      {
        logger.trace("Expected to write %d bytes, but actually wrote %d bytes",
            maximumExpectedSizeOnDisk, byteBuffer.position());
      }

      byteBuffer.force();

      addPosition(bufferPositions, byteBuffer);
    }

    private MappedByteBuffer nextBuffer() throws IOException
    {
      // FIXME JNR bufferSize is an acceptable over approximation
      return fileChannel.map(MapMode.READ_WRITE, getLastPosition(bufferPositions), bufferSize);
    }

    private int getLastPosition(List<Integer> l)
    {
      return l.get(l.size() - 1);
    }

    private void addPosition(List<Integer> l, MappedByteBuffer byteBuffer)
    {
      l.add(getLastPosition(l) + byteBuffer.position());
    }

    private void put(ByteBuffer byteBuffer, ByteSequence b)
    {
      byteBuffer.putInt(b.length());
      // Need to do all of this because b.copyTo(byteBuffer) calls ByteBuffer.flip().
      // Why does it do that?
      final int posBeforeFlip = byteBuffer.position();
      b.copyTo(byteBuffer);
      byteBuffer.limit(bufferSize);
      byteBuffer.position(posBeforeFlip + b.length());
    }

    void flush()
    {
      writeBufferIndexFile();
    }

    private void writeBufferIndexFile()
    {
      final File bufferIndexFile = new File(file.getParent(), file.getName() + ".index");
      try (PrintWriter writer = new PrintWriter(bufferIndexFile))
      {
        writer.print(Utils.joinAsString(" ", this.bufferPositions));
      }
      catch (FileNotFoundException e)
      {
        logger.traceException(e);
      }
    }

    @Override
    public String toString()
    {
      String treeName = "/" + file.getParentFile().getName() + "/" + file.getName();
      return getClass().getSimpleName()
          + "(treeName=\"" + treeName + "\""
          + ", currentBuffer has " + inMemoryStore.size() + " record(s)"
          + " and " + (bufferSize - maximumExpectedSizeOnDisk) + " byte(s) remaining)";
    }
  }

  /** A cursor implementation aggregating several cursors and ordering them by their key value. */
  private static final class CompositeCursor<K extends Comparable<? super K>, V> implements Cursor<K, V>
  {
    private static final byte UNINITIALIZED = 0;
    private static final byte READY = 1;
    private static final byte CLOSED = 2;

    /**
     * The state of this cursor. One of {@link #UNINITIALIZED}, {@link #READY} or {@link #CLOSED}
     */
    private byte state = UNINITIALIZED;

    /**
     * The cursors are sorted based on the key change of each cursor to consider the next change
     * across all cursors.
     */
    private final NavigableSet<Cursor<K, V>> cursors = new TreeSet<>(new Comparator<Cursor<K, V>>()
        {
          @Override
          public int compare(Cursor<K, V> c1, Cursor<K, V> c2)
          {
            final int cmp = c1.getKey().compareTo(c2.getKey());
            if (cmp == 0)
            {
              // Never return 0. Otherwise both cursors are considered equal
              // and only one of them is kept by this set
              return System.identityHashCode(c1) - System.identityHashCode(c2);
            }
            return cmp;
          }
        });

    private CompositeCursor(Collection<Cursor<K, V>> cursors)
    {
      Reject.ifNull(cursors);

      for (Iterator<Cursor<K, V>> it = cursors.iterator(); it.hasNext();)
      {
        Cursor<K, V> cursor = it.next();
        if (!cursor.isDefined() && !cursor.next())
        {
          it.remove();
        }
      }

      this.cursors.addAll(cursors);
    }

    @Override
    public boolean next()
    {
      if (state == CLOSED)
      {
        return false;
      }

      // If previous state was ready, then we must advance the first cursor
      // To keep consistent the cursors' order in the SortedSet, it is necessary
      // to remove the first cursor, then add it again after moving it forward.
      if (state == UNINITIALIZED)
      {
        state = READY;
      }
      else if (state == READY)
      {
        final Cursor<K, V> cursorToAdvance = cursors.pollFirst();
        if (cursorToAdvance != null && cursorToAdvance.next())
        {
          this.cursors.add(cursorToAdvance);
        }
      }
      return isDefined();
    }

    @Override
    public boolean isDefined()
    {
      return state == READY && !cursors.isEmpty();
    }

    @Override
    public K getKey() throws NoSuchElementException
    {
      throwIfNotDefined();
      return cursors.first().getKey();
    }

    @Override
    public V getValue() throws NoSuchElementException
    {
      throwIfNotDefined();
      return cursors.first().getValue();
    }

    private void throwIfNotDefined()
    {
      if (!isDefined())
      {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void close()
    {
      state = CLOSED;
      Utils.closeSilently(cursors);
      cursors.clear();
    }

    @Override
    public String toString()
    {
      if (isDefined())
      {
        return cursors.first().toString();
      }
      return "not defined";
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionToKey(ByteSequence key)
    {
      throw notImplemented();
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionToKeyOrNext(ByteSequence key)
    {
      throw notImplemented();
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionToLastKey()
    {
      throw notImplemented();
    }

    /** {@inheritDoc} */
    @Override
    public boolean positionToIndex(int index)
    {
      throw notImplemented();
    }
  }

  /** A cursor implementation reading key/value pairs from memory mapped files, a.k.a {@link MappedByteBuffer}. */
  private static final class ByteBufferCursor implements Cursor<ByteString, ByteString>
  {
    private final ByteBuffer byteBuffer;
    private final int startPos;
    private final int endPos;
    private final ByteStringBuilder keyBuffer = new ByteStringBuilder();//FIXME JNR  bad: do zero copy?
    private final ByteStringBuilder valueBuffer = new ByteStringBuilder();//FIXME JNR  bad: do zero copy?
    private int currentPos;
    private boolean isDefined;

    private ByteBufferCursor(ByteBuffer byteBuffer, int startPos, int endPos)
    {
      this.byteBuffer = byteBuffer;
      this.startPos = startPos;
      this.endPos = endPos;
      this.currentPos = startPos;
    }

    @Override
    public boolean next()
    {
      isDefined = false;
      if (currentPos >= endPos)
      {
        return isDefined = false;
      }
      read(keyBuffer);
      read(valueBuffer);
      return isDefined = true;
    }

    private void read(ByteStringBuilder buffer)
    {
      int length = byteBuffer.getInt(currentPos);
      currentPos += INT_SIZE;
      byteBuffer.position(currentPos);

      buffer.clear();
      buffer.setLength(length);
      byteBuffer.get(buffer.getBackingArray(), 0, length);
      currentPos += length;
    }

    @Override
    public boolean isDefined()
    {
      return isDefined;
    }

    @Override
    public ByteString getKey() throws NoSuchElementException
    {
      throwIfNotDefined();
      return keyBuffer.toByteString();
    }

    @Override
    public ByteString getValue() throws NoSuchElementException
    {
      throwIfNotDefined();
      return valueBuffer.toByteString();
    }

    private void throwIfNotDefined()
    {
      if (!isDefined())
      {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void close()
    {
      throw notImplemented();
    }

    @Override
    public String toString()
    {
      if (isDefined())
      {
        final ByteString key = getKey();
        final ByteString value = getValue();
        return "<key=" + key + "(" + key.toHexString() + "), value=" + value + "(" + value.toHexString() + ")>";
      }
      return "not defined";
    }

    @Override
    public boolean positionToKey(ByteSequence key)
    {
      throw notImplemented();
    }

    @Override
    public boolean positionToKeyOrNext(ByteSequence key)
    {
      throw notImplemented();
    }

    @Override
    public boolean positionToLastKey()
    {
      throw notImplemented();
    }

    @Override
    public boolean positionToIndex(int index)
    {
      throw notImplemented();
    }
  }

  /** A storage using memory mapped files, a.k.a {@link MappedByteBuffer}. */
  private static final class MemoryMappedStorage implements Storage
  {
    private final File bufferDir;

    private MemoryMappedStorage(File bufferDir)
    {
      this.bufferDir = bufferDir;
    }

    @Override
    public Importer startImport() throws ConfigException, StorageRuntimeException
    {
      return new MemoryMappedBufferImporter(bufferDir);
    }

    @Override
    public void open(AccessMode accessMode) throws Exception
    {
      throw notImplemented();
    }

    @Override
    public <T> T read(ReadOperation<T> readOperation) throws Exception
    {
      return readOperation.run(new ReadableTransaction()
      {
        @Override
        public Cursor<ByteString, ByteString> openCursor(TreeName treeName)
        {
          try
          {
            List<Integer> bufferPositions = readBufferPositions(treeName);

            // TODO JNR build ByteSequence implementation reading from memory mapped files?
            return getCursors(treeName, bufferPositions);
          }
          catch (IOException e)
          {
            throw new StorageRuntimeException(e);
          }
        }

        private List<Integer> readBufferPositions(TreeName treeName) throws IOException
        {
          // TODO JNR move to Buffer class?
          File indexFile = new File(bufferDir, treeName + ".index");
          List<String> indexLines = Files.readAllLines(indexFile.toPath(), Charset.defaultCharset());
          if (indexLines.size() != 1)
          {
            throw new IllegalStateException("Not implemented");// TODO JNR
          }

          final String[] bufferPositions = indexLines.get(0).split(" ");
          final List<Integer> results = new ArrayList<>(bufferPositions.length);
          for (String bufferPos : bufferPositions)
          {
            results.add(Integer.valueOf(bufferPos));
          }
          return results;
        }

        private Cursor<ByteString, ByteString> getCursors(TreeName treeName, List<Integer> bufferPositions)
            throws IOException
        {
          // TODO JNR move to Buffer class?
          File bufferFile = new File(bufferDir, treeName.toString());
          FileChannel fileChannel = new RandomAccessFile(bufferFile, "r").getChannel();
          long fileSize = Files.size(bufferFile.toPath());
          final MappedByteBuffer byteBuffer = fileChannel.map(MapMode.READ_ONLY, 0, fileSize);

          final List<Cursor<ByteString, ByteString>> cursors = new ArrayList<>(bufferPositions.size() - 1);
          Iterator<Integer> it = bufferPositions.iterator();
          if (it.hasNext())
          {
            int lastPos = it.next();
            while (it.hasNext())
            {
              final int bufferPos = it.next();
              cursors.add(new ByteBufferCursor(byteBuffer, lastPos, bufferPos));
              lastPos = bufferPos;
            }
          }
          return new CompositeCursor<ByteString, ByteString>(cursors);
        }

        @Override
        public ByteString read(TreeName treeName, ByteSequence key)
        {
          throw notImplemented();
        }

        @Override
        public long getRecordCount(TreeName treeName)
        {
          throw notImplemented();
        }
      });
    }

    @Override
    public void write(WriteOperation writeOperation) throws Exception
    {
      throw notImplemented();
    }

    @Override
    public void removeStorageFiles() throws StorageRuntimeException
    {
      throw notImplemented();
    }

    @Override
    public StorageStatus getStorageStatus()
    {
      throw notImplemented();
    }

    @Override
    public boolean supportsBackupAndRestore()
    {
      throw notImplemented();
    }

    @Override
    public void close()
    {
      throw notImplemented();
    }

    @Override
    public void createBackup(BackupConfig backupConfig) throws DirectoryException
    {
      throw notImplemented();
    }

    @Override
    public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
    {
      throw notImplemented();
    }

    @Override
    public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
    {
      throw notImplemented();
    }

    @Override
    public Set<TreeName> listTrees()
    {
      final Set<TreeName> results = new HashSet<>();
      for (File baseDN : this.bufferDir.listFiles())
      {
        for (File index : baseDN.listFiles())
        {
          if (!index.getName().endsWith(".index"))
          {
            results.add(new TreeName(baseDN.getName(), index.getName()));
          }
        }
      }
      return results;
    }
  }

  /** An importer using memory mapped files, a.k.a {@link MappedByteBuffer}. */
  private static final class MemoryMappedBufferImporter implements Importer
  {
    private final File bufferDir;
    private final Map<TreeName, Buffer> treeNameToBufferMap = new HashMap<>();

    private MemoryMappedBufferImporter(File bufferDir)
    {
      this.bufferDir = bufferDir;
    }

    @Override
    public void put(TreeName treeName, ByteSequence key, ByteSequence value)
    {
      try
      {
        getBuffer(treeName).putKeyValue(key, value);
      }
      catch (IOException e)
      {
        logger.traceException(e);
      }
    }

    private Buffer getBuffer(TreeName treeName) throws IOException
    {
      Buffer buffer = treeNameToBufferMap.get(treeName);
      if (buffer == null)
      {
        // Creates sub directories for each suffix
        // FIXME JNR cannot directly use DN names as directory + file names
        buffer = new Buffer(new File(bufferDir, treeName.toString()));
        treeNameToBufferMap.put(treeName, buffer);
      }
      return buffer;
    }

    @Override
    public void close()
    {
      for (Buffer buffer : treeNameToBufferMap.values())
      {
        buffer.flush();
      }
    }

    @Override
    public ByteString read(TreeName treeName, ByteSequence key)
    {
      throw notImplemented();
    }

    @Override
    public boolean delete(TreeName treeName, ByteSequence key)
    {
      throw notImplemented();
    }

    @Override
    public void createTree(TreeName name)
    {
      throw notImplemented();
    }
  }

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

    if (System.getProperty(PROPERTY_RUNNING_UNIT_TESTS) != null)
    {
      dbCacheSize = 500 * KB;
    }
    // We need caching when doing DN validation
    else if (usableMemory < MIN_DB_CACHE_MEMORY + (validateDNs ? MIN_DB_CACHE_SIZE : 0))
    {
      dbCacheSize = MIN_DB_CACHE_SIZE;
    }
    else
    {
      dbCacheSize = MAX_DB_CACHE_SIZE;
    }

    final long phaseOneBufferMemory = usableMemory - dbCacheSize;
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
              dbCacheSize += extraMemory;
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
              usableMemory, minimumPhaseOneBufferMemory + dbCacheSize));
        }
      }
    }

    if (oldThreadCount != threadCount)
    {
      logger.info(NOTE_IMPORT_ADJUST_THREAD_COUNT, oldThreadCount, threadCount);
    }

    logger.info(NOTE_IMPORT_LDIF_TOT_MEM_BUF, availableMemory, phaseOneBufferCount);
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

  private boolean isCanceled()
  {
    return isCanceled || (importCfg != null && importCfg.isCancelled());
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

      final Storage backendStorage = rootContainer.getStorage();
      backendStorage.write(new WriteOperation()
      {
        @Override
        public void run(WriteableTransaction txn) throws Exception
        {
          initializeSuffixes(txn);
          setIndexesTrusted(txn, false);
        }
      });

      final MemoryMappedStorage tmpStorage = new MemoryMappedStorage(tempDir);

      final long startTime = System.currentTimeMillis();
      importPhaseOne(backendStorage, tmpStorage);
      final long phaseOneFinishTime = System.currentTimeMillis();

      if (isCanceled())
      {
        throw new InterruptedException("Import processing canceled.");
      }

      backendStorage.close();

      final long phaseTwoTime = System.currentTimeMillis();
      importPhaseTwo(backendStorage, tmpStorage);
      if (isCanceled())
      {
        throw new InterruptedException("Import processing canceled.");
      }
      final long phaseTwoFinishTime = System.currentTimeMillis();

      backendStorage.open(AccessMode.READ_WRITE);
      backendStorage.write(new WriteOperation()
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
   * TODO JNR fix all javadocs
   */
  private void importPhaseOne(Storage backendStorage, Storage tmpStorage) throws Exception
  {
    final ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new FirstPhaseProgressTask());
    threadCount = 2; // FIXME JNR id2entry + another task
    final ExecutorService execService = Executors.newFixedThreadPool(threadCount);

    try (Importer tmpImporter = tmpStorage.startImport())
    {
      final Id2EntryPutTask id2EntryPutTask = new Id2EntryPutTask(backendStorage);
      final Future<?> dn2IdPutFuture = execService.submit(id2EntryPutTask);
      execService.submit(new MigrateExistingEntriesTask(backendStorage, tmpImporter, id2EntryPutTask)).get();

      final List<Callable<Void>> tasks = new ArrayList<>(threadCount);
      if (!importCfg.appendToExistingData() || !importCfg.replaceExistingEntries())
      {
        for (int i = 0; i < threadCount - 1; i++)
        {
          tasks.add(new ImportTask(tmpImporter, id2EntryPutTask));
        }
      }
      execService.invokeAll(tasks);
      tasks.clear();

      execService.submit(new MigrateExcludedTask(backendStorage, tmpImporter, id2EntryPutTask)).get();
      id2EntryPutTask.finishedWrites();
      dn2IdPutFuture.get();
    }

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

  private void importPhaseTwo(final Storage outStorage, Storage inStorage) throws Exception
  {
    ScheduledThreadPoolExecutor timerService = new ScheduledThreadPoolExecutor(1);
    scheduleAtFixedRate(timerService, new SecondPhaseProgressTask());

    final Set<TreeName> treeNames = inStorage.listTrees(); // FIXME JNR rename to listTreeNames()?
    ExecutorService dbService = Executors.newFixedThreadPool(treeNames.size());
    try (Importer importer = outStorage.startImport())
    {
      for (final TreeName treeName : treeNames)
      {
        copyTo(treeName, inStorage, importer);// FIXME JNR use dbService
      }
    }
    finally
    {
      shutdownAll(timerService, dbService);
    }
  }

  private void copyTo(final TreeName treeName, Storage input, final Importer output) throws Exception
  {
    input.read(new ReadOperation<Void>()
    {
      @Override
      public Void run(ReadableTransaction txn) throws Exception
      {
        try (Cursor<ByteString, ByteString> cursor = txn.openCursor(treeName))
        {
          while (cursor.next())
          {// FIXME JNR add merge phase
            output.put(treeName, cursor.getKey(), cursor.getValue());
          }
        }
        return null;
      }
    });
  }

  /** Task used to migrate excluded branch. */
  private final class MigrateExcludedTask extends ImportTask
  {
    private final Storage storage;

    private MigrateExcludedTask(Storage storage, Importer importer, Id2EntryPutTask id2EntryPutTask)
    {
      super(importer, id2EntryPutTask);
      this.storage = storage;
    }

    @Override
    public Void call() throws Exception
    {
      storage.read(new ReadOperation<Void>()
      {
        @Override
        public Void run(ReadableTransaction txn) throws Exception
        {
          call0(txn);
          return null;
        }
      });
      return null;
    }

    private void call0(ReadableTransaction txn) throws Exception
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
                  processEntry(entry, rootContainer.getNextEntryID(), suffix);
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
  private final class MigrateExistingEntriesTask extends ImportTask
  {
    private final Storage storage;

    private MigrateExistingEntriesTask(final Storage storage, Importer importer, Id2EntryPutTask id2EntryPutTask)
    {
      super(importer, id2EntryPutTask);
      this.storage = storage;
    }

    @Override
    public Void call() throws Exception
    {
      storage.read(new ReadOperation<Void>()
      {
        @Override
        public Void run(ReadableTransaction txn) throws Exception
        {
          call0(txn);
          return null;
        }
      });
      return null;
    }

    private void call0(ReadableTransaction txn) throws Exception
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
                processEntry(entry, rootContainer.getNextEntryID(), suffix);
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
    private final Importer importer;
    private final Id2EntryPutTask id2EntryPutTask;

    public ImportTask(final Importer importer, Id2EntryPutTask id2EntryPutTask)
    {
      this.importer = importer;
      this.id2EntryPutTask = id2EntryPutTask;
    }

    @Override
    public Void call() throws Exception
    {
      call0();
      return null;
    }

    void call0() throws Exception
    {
      try
      {
        EntryInformation entryInfo;
        while ((entryInfo = reader.readEntry(dnSuffixMap)) != null)
        {
          if (isCanceled())
          {
            return;
          }
          processEntry(entryInfo.getEntry(), entryInfo.getEntryID(), entryInfo.getSuffix());
        }
      }
      catch (Exception e)
      {
        logger.error(ERR_IMPORT_LDIF_IMPORT_TASK_ERR, e.getMessage());
        isCanceled = true;
        throw e;
      }
    }

    void processEntry(Entry entry, EntryID entryID, Suffix suffix)
        throws DirectoryException, StorageRuntimeException, InterruptedException
    {
      try
      {
        if (validateDNs && !dnSanityCheck(entry, entryID, suffix))
        {
          return;
        }
      }
      finally
      {
        suffix.removePending(entry.getName());
      }

      if (!validateDNs)
      {
        processDN2ID(suffix, entry.getName(), entryID);
      }
      processDN2URI(suffix, entry);
      processIndexes(suffix, entry, entryID);
      processVLVIndexes(suffix, entry, entryID);
      id2EntryPutTask.put(suffix, entryID, entry);
      importCount.getAndIncrement();
    }

    /**
     * Examine the DN for duplicates and missing parents.
     *
     * @return true if the import operation can proceed with the provided entry, false otherwise
     */
    @SuppressWarnings("javadoc")
    boolean dnSanityCheck(Entry entry, EntryID entryID, Suffix suffix)
        throws StorageRuntimeException, InterruptedException
    {
      //Perform parent checking.
      DN entryDN = entry.getName();
      DN parentDN = suffix.getEntryContainer().getParentWithinBase(entryDN);
      DNCache dnCache = new Dn2IdDnCache(suffix, rootContainer.getStorage());
      if (parentDN != null && !suffix.isParentProcessed(parentDN, dnCache))
      {
        reader.rejectEntry(entry, ERR_IMPORT_PARENT_NOT_FOUND.get(parentDN));
        return false;
      }
      if (!dnCache.insert(entryDN, entryID))
      {
        reader.rejectEntry(entry, WARN_IMPORT_ENTRY_EXISTS.get());
        return false;
      }
      return true;
    }

    void processDN2ID(Suffix suffix, DN dn, EntryID entryID)
    {
      DN2ID dn2id = suffix.getDN2ID();
      importer.put(dn2id.getName(), dn2id.toKey(dn), entryID.toByteString());
    }

    private void processDN2URI(Suffix suffix, Entry entry)
    {
      DN2URI dn2uri = suffix.getDN2URI();
      DN entryDN = entry.getName();
      ByteSequence value = dn2uri.toValue(entryDN, entry);
      if (value != null)
      {
        importer.put(dn2uri.getName(), dn2uri.toKey(entryDN), value);
      }
    }

    void processIndexes(Suffix suffix, Entry entry, EntryID entryID)
        throws StorageRuntimeException, InterruptedException
    {
      final ByteString value = entryID.toByteString();
      for (Map.Entry<AttributeType, AttributeIndex> mapEntry : suffix.getAttrIndexMap().entrySet())
      {
        final AttributeType attrType = mapEntry.getKey();
        final AttributeIndex attrIndex = mapEntry.getValue();
        if (entry.hasAttribute(attrType))
        {
          for (MatchingRuleIndex index : attrIndex.getNameToIndexes().values())
          {
            for (ByteString key : index.indexEntry(entry))
            {
              importer.put(index.getName(), key, value);
            }
          }
        }
      }
    }

    void processVLVIndexes(Suffix suffix, Entry entry, EntryID entryID) throws DirectoryException
    {
      for (VLVIndex vlvIndex : suffix.getEntryContainer().getVLVIndexes())
      {
        ByteString key = vlvIndex.toKey(entry, entryID);
        importer.put(vlvIndex.getName(), key, ByteString.empty());
      }
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

  /** Used to check DN's when DN validation is performed during phase one processing. */
  private final class Dn2IdDnCache implements DNCache
  {
    private Suffix suffix;
    private Storage storage;

    private Dn2IdDnCache(Suffix suffix, Storage storage)
    {
      this.suffix = suffix;
      this.storage = storage;
    }

    @Override
    public boolean insert(final DN dn, final EntryID entryID)
    {
      final AtomicBoolean result = new AtomicBoolean();
      try
      {
        storage.write(new WriteOperation()
        {
          @Override
          public void run(WriteableTransaction txn) throws Exception
          {
            result.set(suffix.getDN2ID().insert(txn, dn, entryID));
          }
        });
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
      return result.get();
    }

    @Override
    public boolean contains(final DN dn) throws StorageRuntimeException
    {
      try
      {
        return storage.read(new ReadOperation<Boolean>()
        {
          @Override
          public Boolean run(ReadableTransaction txn) throws Exception
          {
            return suffix.getDN2ID().get(txn, dn) != null;
          }
        });
      }
      catch (Exception e)
      {
        throw new StorageRuntimeException(e);
      }
    }

    @Override
    public void close()
    {
      // Nothing to do
    }
  }
}
