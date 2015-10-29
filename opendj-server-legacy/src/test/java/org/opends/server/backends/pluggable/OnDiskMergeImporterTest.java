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
 *      Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.backends.pluggable.EntryIDSet.*;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ForkJoinPool;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Pair;
import org.mockito.Mockito;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.BufferPool;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.Chunk;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.Collector;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.EntryIDSetsCollector;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk.CollectorCursor;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk.CompositeCursor;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk.FileRegionChunk;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.ExternalSortChunk.InMemorySortedChunk;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.MeteredCursor;
import org.opends.server.backends.pluggable.OnDiskMergeImporter.UniqueValueCollector;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.SequentialCursor;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.PackedLong;

public class OnDiskMergeImporterTest extends DirectoryServerTestCase
{
  @Test
  @SuppressWarnings(value = "resource")
  public void testCollectCursor()
  {
    final MeteredCursor<ByteString, ByteString> source =
        cursorOf(content(new String[][] {
          { "key1", "value1key1" },
          { "key1", "value2key1" },
          { "key2", "value1key2" },
          { "key3", "value1key3" },
          { "key3", "value2key3" } }));

    final MeteredCursor<ByteString, ByteString> result = new CollectorCursor<>(source, StringConcatCollector.INSTANCE);

    assertThat(toPairs(result)).containsExactlyElementsOf(content(new String[][] {
      { "key1", "value1key1-value2key1" },
      { "key2", "value1key2" },
      { "key3", "value1key3-value2key3" } }));
  }

  @Test
  @SuppressWarnings(value = "resource")
  public void testCompositeCursor()
  {
    final Collection<MeteredCursor<ByteString, ByteString>> sources = new ArrayList<>();
    sources.add(cursorOf(content(new String[][] {
      { "A", "value1" },
      { "C", "value3" },
      { "D", "value4" },
      { "F", "value6" },
      { "I", "value9" } })));

    sources.add(cursorOf(content(new String[][] { { "B", "value2" } })));
    sources.add(cursorOf(Collections.<Pair<ByteString, ByteString>> emptyList()));
    sources.add(cursorOf(content(new String[][] {
      { "A", "value1" }, { "E", "value5" }, { "G", "value7" }, { "H", "value8" } })));

    final SequentialCursor<ByteString, ByteString> result = new CompositeCursor<>("name", sources);

    assertThat(toPairs(result)).containsExactlyElementsOf(content(new String[][] {
      { "A", "value1" },
      { "A", "value1" },
      { "B", "value2" },
      { "C", "value3" },
      { "D", "value4" },
      { "E", "value5" },
      { "F", "value6" },
      { "G", "value7" },
      { "H", "value8" },
      { "I", "value9" } }));
  }

  @Test
  @SuppressWarnings(value = { "unchecked", "resource" })
  public void testCounterCollector()
  {
    final MeteredCursor<String, ByteString> source = cursorOf(
        Pair.of("key1", ShardedCounter.encodeValue(10)),
        Pair.of("key1", ShardedCounter.encodeValue(20)),
        Pair.of("key2", ShardedCounter.encodeValue(5)),
        Pair.of("key3", ShardedCounter.encodeValue(6)),
        Pair.of("key3", ShardedCounter.encodeValue(4)));

    final SequentialCursor<String, ByteString> expected = cursorOf(
        Pair.of("key1", ShardedCounter.encodeValue(30)),
        Pair.of("key2", ShardedCounter.encodeValue(5)),
        Pair.of("key3", ShardedCounter.encodeValue(10)));

    final SequentialCursor<String, ByteString> result =
        new CollectorCursor<>(source, ID2ChildrenCount.getSumLongCollectorInstance());

    assertThat(toPairs(result)).containsExactlyElementsOf(toPairs(expected));
  }

  @Test
  @SuppressWarnings(value = { "unchecked", "resource" })
  public void testEntryIDSetCollector()
  {
    final MeteredCursor<String, ByteString> source = cursorOf(
        Pair.of("key1", EntryIDSet.CODEC_V2.encode(newDefinedSet(2))),
        Pair.of("key1", EntryIDSet.CODEC_V2.encode(newDefinedSet(1))),

        Pair.of("key2", EntryIDSet.CODEC_V2.encode(newDefinedSet(1))),

        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(1))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(2))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(3))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(4))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(5))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(6))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(7))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(8))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(9))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newDefinedSet(10))),

        Pair.of("key4", EntryIDSet.CODEC_V2.encode(newDefinedSet(10))),
        Pair.of("key4", EntryIDSet.CODEC_V2.encode(newUndefinedSet())));

    final SequentialCursor<String, ByteString> expected = cursorOf(
        Pair.of("key1", EntryIDSet.CODEC_V2.encode(newDefinedSet(1, 2))),
        Pair.of("key2", EntryIDSet.CODEC_V2.encode(newDefinedSet(1))),
        Pair.of("key3", EntryIDSet.CODEC_V2.encode(newUndefinedSet())),
        Pair.of("key4", EntryIDSet.CODEC_V2.encode(newUndefinedSet())));

    final SequentialCursor<String, ByteString> result =
        new CollectorCursor<>(source, new EntryIDSetsCollector(new DummyIndex(10)));

    assertThat(toPairs(result)).containsExactlyElementsOf(toPairs(expected));
  }

  @Test
  @SuppressWarnings(value = "resource")
  public void testUniqueValueCollectorAcceptUniqueValues()
  {
    final MeteredCursor<ByteString, ByteString> source =
        cursorOf(content(new String[][] { { "key1", "value1" }, { "key2", "value2" }, { "key3", "value3" }, }));

    final SequentialCursor<ByteString, ByteString> result =
        new CollectorCursor<>(source, UniqueValueCollector.<ByteString> getInstance());

    assertThat(toPairs(result)).containsExactlyElementsOf(content(new String[][] {
      { "key1", "value1" },
      { "key2", "value2" },
      { "key3", "value3" }, }));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  @SuppressWarnings(value = "resource")
  public void testUniqueValueCollectorDoesNotAcceptMultipleValues()
  {
    final MeteredCursor<ByteString, ByteString> source =
        cursorOf(content(new String[][] {
          { "key1", "value1" },
          { "key2", "value2" },
          { "key2", "value22" },
          { "key3", "value3" } }));

    toPairs(new CollectorCursor<>(source, new UniqueValueCollector<ByteString>()));
  }

  @Test
  public void testInMemorySortedChunkSortUnsignedOnFlip() throws Exception
  {
    try(final BufferPool bufferPool = new BufferPool(1, 1024)) {
      final Chunk chunk = new InMemorySortedChunk("test", bufferPool);
      populate(chunk, content(new String[][] {
        { new String(new byte[] { (byte) 0xFF }), "value0xFF" },
        { "key1", "value1" },
        { "key2", "value2" },
        { "key3", "value3" },
        { new String(new byte[] { (byte) 0x00 }), "value0x00" } }));

      assertThat(toPairs(chunk.flip())).containsExactlyElementsOf(content(new String[][] {
        { new String(new byte[] { (byte) 0x00 }), "value0x00" },
        { "key1", "value1" },
        { "key2", "value2" },
        { "key3", "value3" },
        { new String( new byte[] { (byte) 0xFF }), "value0xFF" } }));
    }
  }

  @Test
  @SuppressWarnings("resource")
  public void testFileRegionChunk() throws Exception
  {
    final int NB_REGION = 10;
    final int NB_RECORDS = 15;
    final File tempDir = TestCaseUtils.createTemporaryDirectory("testFileRegionChunk");
    final FileChannel channel =
        FileChannel.open(tempDir.toPath().resolve("region-chunk"), StandardOpenOption.CREATE_NEW,
            StandardOpenOption.SPARSE, StandardOpenOption.READ, StandardOpenOption.WRITE);

    // Generate content
    final List<Chunk> memoryChunks = new ArrayList<>(NB_REGION);
    final String[][][] contents = new String[NB_REGION][NB_RECORDS][];
    for (int region = 0; region < NB_REGION; region++)
    {
      for (int record = 0; record < NB_RECORDS; record++)
      {
        contents[region][record] =
            new String[] { String.format("key-%d-%d", region, record), String.format("value-%d", record) };
      }
      final Chunk memoryChunk = new ArrayListChunk();
      populate(memoryChunk, content(contents[region]));
      memoryChunks.add(memoryChunk);
    }

    // Copy content into file regions
    final List<Chunk> regionChunks = new ArrayList<>(memoryChunks.size());
    long offset = 0;
    for (Chunk source : memoryChunks)
    {
      final Chunk region = new FileRegionChunk("test", channel, offset, source.size());
      offset += source.size();
      populate(region, toPairs(source.flip()));
      regionChunks.add(region);
    }

    // Verify file regions contents
    int regionNumber = 0;
    for (Chunk region : regionChunks)
    {
      assertThat(toPairs(region.flip())).containsExactlyElementsOf(content(contents[regionNumber]));
      regionNumber++;
    }
  }

  @Test
  public void testExternalSortChunk() throws Exception
  {
    final int NB_REGION = 10;
    final ByteString KEY = ByteString.valueOfUtf8("key");
    final File tempDir = TestCaseUtils.createTemporaryDirectory("testExternalSortChunk");
    try(final BufferPool bufferPool = new BufferPool(2, 4 + 1 + KEY.length() + 1 + 4)) {
      // 4: record offset, 1: key length, 1: value length, 4: value
      final ExternalSortChunk chunk =
          new ExternalSortChunk(tempDir, "test", bufferPool, StringConcatCollector.INSTANCE, new ForkJoinPool());

      List<ByteString> expected = new ArrayList<>(NB_REGION);
      for (int i = 0; i < NB_REGION; i++)
      {
        final ByteString value = ByteString.valueOfUtf8(String.format("%02d", i));
        chunk.put(KEY, value);
        expected.add(value);
      }

      assertThat(chunk.getNbSortedChunks()).isEqualTo(NB_REGION);
      try (final SequentialCursor<ByteString, ByteString> cursor = chunk.flip())
      {
        assertThat(toPairs(cursor)).containsExactly(Pair.of(KEY, StringConcatCollector.INSTANCE.merge(expected)));
      }
    }
  }

  private final static List<Pair<ByteString, ByteString>> content(String[]... data)
  {
    final List<Pair<ByteString, ByteString>> content = new ArrayList<>(data.length);
    for (String[] keyValue : data)
    {
      content.add(Pair.of(ByteString.valueOfUtf8(keyValue[0]), ByteString.valueOfUtf8(keyValue[1])));
    }
    return content;
  }

  private static void populate(Chunk chunk, Collection<Pair<ByteString, ByteString>> content)
  {
    for (Pair<ByteString, ByteString> keyValue : content)
    {
      chunk.put(keyValue.getFirst(), keyValue.getSecond());
    }
  }

  private static <K, V> Collection<Pair<K, V>> toPairs(SequentialCursor<K, V> source)
  {
    final Collection<Pair<K, V>> collection = new LinkedList<>();
    while (source.next())
    {
      collection.add(Pair.of(source.getKey(), source.getValue()));
    }
    return collection;
  }

  private final static <K, V> MeteredCursor<K, V> cursorOf(@SuppressWarnings("unchecked") Pair<K, V>... pairs)
  {
    return cursorOf(Arrays.asList(pairs));
  }

  private static final <K, V> MeteredCursor<K, V> cursorOf(Iterable<Pair<K, V>> pairs)
  {
    return new IteratorCursorAdapter<>(pairs.iterator());
  }

  private static final class StringConcatCollector implements Collector<List<ByteString>, ByteString>
  {
    static Collector<List<ByteString>, ByteString> INSTANCE = new StringConcatCollector();

    private StringConcatCollector()
    {
    }

    @Override
    public List<ByteString> get()
    {
      return new LinkedList<>();
    }

    @Override
    public List<ByteString> accept(List<ByteString> resultContainer, ByteString value)
    {
      resultContainer.add(value);
      return resultContainer;
    }

    @Override
    public ByteString merge(List<ByteString> resultContainer)
    {
      final ByteStringBuilder builder = new ByteStringBuilder();
      Collections.sort(resultContainer);
      for (ByteString s : resultContainer)
      {
        builder.appendBytes(s);
        builder.appendUtf8(new char[] { '-' });
      }
      builder.setLength(builder.length() - 1);
      return builder.toByteString();
    }
  }

  private static final class IteratorCursorAdapter<K, V> implements MeteredCursor<K, V>
  {
    private final Iterator<Pair<K, V>> it;
    private Pair<K, V> entry;

    IteratorCursorAdapter(Iterator<Pair<K, V>> it)
    {
      this.it = it;
    }

    @Override
    public boolean next()
    {
      if (it.hasNext())
      {
        entry = it.next();
        return true;
      }
      entry = null;
      return false;
    }

    @Override
    public boolean isDefined()
    {
      return entry != null;
    }

    @Override
    public K getKey() throws NoSuchElementException
    {
      return entry.getFirst();
    }

    @Override
    public V getValue() throws NoSuchElementException
    {
      return entry.getSecond();
    }

    @Override
    public void delete() throws NoSuchElementException, UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
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

    @Override
    public String getMetricName()
    {
      return "iterator-adapter";
    }
  }

  private static final class DummyIndex extends DefaultIndex
  {
    private static final State state;
    private static final EntryContainer entryContainer;

    static
    {
      entryContainer = Mockito.mock(EntryContainer.class);
      Mockito.when(entryContainer.getHighestEntryID(Mockito.any(WriteableTransaction.class))).thenReturn(new EntryID(
          1));

      state = Mockito.mock(State.class);
      Mockito.when(state.getIndexFlags(Mockito.any(ReadableTransaction.class), Mockito.any(TreeName.class))).thenReturn(
          EnumSet.of(State.IndexFlag.COMPACTED));
    };

    DummyIndex(int indexEntryLimit) throws StorageRuntimeException
    {
      super(TreeName.valueOf("/dumy/dummy"), state, indexEntryLimit, entryContainer);
      open(Mockito.mock(WriteableTransaction.class), false);
    }
  }

  private static final class ArrayListChunk implements Chunk
  {
    private final List<Pair<ByteString, ByteString>> content = new ArrayList<>();
    private long size;

    @Override
    public boolean put(ByteSequence key, ByteSequence value)
    {
      size +=
          PackedLong.getEncodedSize(key.length()) + key.length() + PackedLong.getEncodedSize(value.length()) + value
              .length();
      content.add(Pair.of(key.toByteString(), value.toByteString()));
      return true;
    }

    @Override
    public MeteredCursor<ByteString, ByteString> flip()
    {
      return cursorOf(content);
    }

    @Override
    public long size()
    {
      return size;
    }

    @Override
    public void delete()
    {
    }
  }
}
