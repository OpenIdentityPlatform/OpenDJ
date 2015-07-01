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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.replication.server.changelog.file.BlockLogReader.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Pair;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class BlockLogReaderWriterTest extends DirectoryServerTestCase
{
  private static final File TEST_DIRECTORY = new File(TestCaseUtils.getUnitTestRootPath(), "changelog-unit");
  private static final File TEST_FILE = new File(TEST_DIRECTORY, "file");
  private static final RecordParser<Integer, Integer> RECORD_PARSER = new IntRecordParser();
  private static final int INT_RECORD_SIZE = 12;

  @BeforeClass
  void createTestDirectory()
  {
    TEST_DIRECTORY.mkdirs();
  }

  @BeforeMethod
  void ensureTestFileIsEmpty() throws Exception
  {
    StaticUtils.recursiveDelete(TEST_FILE);
  }

  @AfterClass
  void cleanTestDirectory()
  {
    StaticUtils.recursiveDelete(TEST_DIRECTORY);
  }

  @DataProvider(name = "recordsData")
  Object[][] recordsData()
  {
    return new Object[][]
    {
      // raw size taken by each record is: 4 (record size) + 4 (key) + 4 (value) = 12 bytes

      // size of block, expected size of file after all records are written, records
      { 12, 12, records(1) }, // zero block marker
      { 10, 16, records(1) }, // one block marker
      { 8, 16, records(1) },  // one block marker
      { 7, 20, records(1) },  // two block markers
      { 6, 24, records(1) },  // three block markers
      { 5, 40, records(1) },  // seven block markers

      { 16, 28, records(1,2) }, // one block marker
      { 12, 32, records(1,2) }, // two block markers
      { 10, 36, records(1,2) }, // three block markers
    };
  }

  /**
   * Tests that records can be written then read correctly for different block sizes.
   */
  @Test(dataProvider="recordsData")
  public void testWriteThenRead(int blockSize, int expectedSizeOfFile, List<Record<Integer, Integer>> records)
      throws Exception
  {
    writeRecords(blockSize, records);

    try (BlockLogReader<Integer, Integer> reader = newReader(blockSize))
    {
      for (int i = 0; i < records.size(); i++)
      {
         Record<Integer, Integer> record = reader.readRecord();
         assertThat(record).isEqualTo(records.get(i));
      }
      assertThat(reader.readRecord()).isNull();
      assertThat(reader.getFilePosition()).isEqualTo(expectedSizeOfFile);
    }
  }

  @DataProvider(name = "recordsForSeek")
  Object[][] recordsForSeek()
  {
    Object[][] data = new Object[][] {
      // records, key, key matching strategy, position strategy, expectedRecord, should be found ?

      // no record
      { records(), 1, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(), 1, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(), 1, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },

      // 1 record
      { records(1), 0, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1), 1, EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1), 0, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1), 1, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },

      { records(1), 0, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1), 1, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1), 0, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(1), true },
      { records(1), 1, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },

      // 3 records equal matching
      { records(1,2,3), 0, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1,2,3), 1, EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3), 2, EQUAL_TO_KEY, ON_MATCHING_KEY, record(2), true },
      { records(1,2,3), 3, EQUAL_TO_KEY, ON_MATCHING_KEY, record(3), true },
      { records(1,2,3), 4, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },

      { records(1,2,3), 0, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1,2,3), 2, EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(3), true },
      { records(1,2,3), 3, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },

      // 3 records less than or equal matching
      { records(1,2,3), 0, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1,2,3), 1, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3), 2, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(2), true },
      { records(1,2,3), 3, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(3), true },
      { records(1,2,3), 4, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(3), true },

      { records(1,2,3), 0, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1,2,3), 1, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(2), true },
      { records(1,2,3), 2, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(3), true },
      { records(1,2,3), 3, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },
      { records(1,2,3), 4, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },

      // 3 records greater or equal matching
      { records(1,2,3), 0, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3), 2, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(2), true },
      { records(1,2,3), 3, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(3), true },

      { records(1,2,3), 0, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(1), true },
      { records(1,2,3), 1, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(2), true },
      { records(1,2,3), 2, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(3), true },
      { records(1,2,3), 3, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },
      { records(1,2,3), 4, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },

      // 10 records equal matching
      { records(1,2,3,4,5,6,7,8,9,10), 0, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 1, EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, EQUAL_TO_KEY, ON_MATCHING_KEY, record(5), true },
      { records(1,2,3,4,5,7,8,9,10), 6, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 10, EQUAL_TO_KEY, ON_MATCHING_KEY, record(10), true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },

      { records(1,2,3,4,5,6,7,8,9,10), 0, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 1, EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(2), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(6), true },
      { records(1,2,3,4,5,7,8,9,10), 6, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 10, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },

      // 10 records less than or equal matching
      { records(1,2,3,4,5,6,7,8,9,10), 0, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 1, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(5), true },
      { records(1,2,3,4,5,7,8,9,10), 6, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(5), true },
      { records(1,2,3,4,5,6,7,8,9,10), 10, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(10), true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(10), true },

      { records(1,2,3,4,5,6,7,8,9,10), 0, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
      { records(1,2,3,4,5,6,7,8,9,10), 1, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(2), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(6), true },
      { records(1,2,3,4,5,7,8,9,10), 6, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(7), true },
      { records(1,2,3,4,5,6,7,8,9,10), 10, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },

      // 10 records greater or equal matching
      { records(1,2,3,4,5,6,7,8,9,10), 0, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3,4,5,6,7,8,9,10), 1, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(1), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(5), true },
      { records(1,2,3,4,5,7,8,9,10), 6, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(7), true },
      { records(1,2,3,4,5,6,7,8,9,10), 10, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, record(10), true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, null, false },

      { records(1,2,3,4,5,6,7,8,9,10), 0, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(1), true },
      { records(1,2,3,4,5,6,7,8,9,10), 1, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(2), true },
      { records(1,2,3,4,5,6,7,8,9,10), 5, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(6), true },
      { records(1,2,3,4,5,7,8,9,10), 6, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, record(7), true },
      { records(1,2,3,4,5,6,7,8,9,10), 10, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, true },
      { records(1,2,3,4,5,6,7,8,9,10), 11, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, null, false },
    };

    // For each test case, do a test with various block sizes to ensure algorithm is not broken
    // on a given size
    int[] sizes = new int[] { 500, 100, 50, 30, 25, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10 };
    Object[][] finalData = new Object[sizes.length * data.length][7];
    for (int i = 0; i < data.length; i++)
    {
      for (int j = 0; j < sizes.length; j++)
      {
        Object[] a = data[i];
        // add the block size at beginning of each test case
        finalData[sizes.length*i+j] = new Object[] { sizes[j], a[0], a[1], a[2], a[3], a[4], a[5]};
      }
    }
    return finalData;
  }

  @Test(dataProvider = "recordsForSeek")
  public void testSeekToRecord(int blockSize, List<Record<Integer, Integer>> records, int key,
      KeyMatchingStrategy matchingStrategy, PositionStrategy positionStrategy, Record<Integer, Integer> expectedRecord,
      boolean shouldBeFound) throws Exception
  {
    writeRecords(blockSize, records);

    try (BlockLogReader<Integer, Integer> reader = newReader(blockSize))
    {
      Pair<Boolean, Record<Integer, Integer>> result = reader.seekToRecord(key, matchingStrategy, positionStrategy);

      assertThat(result.getFirst()).isEqualTo(shouldBeFound);
      assertThat(result.getSecond()).isEqualTo(expectedRecord);
    }
  }

  @Test
  public void testGetClosestMarkerBeforeOrAtPosition() throws Exception
  {
    final int blockSize = 10;
    BlockLogReader<Integer, Integer> reader = newReaderWithNullFile(blockSize);

    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(0)).isEqualTo(0);
    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(5)).isEqualTo(0);
    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(9)).isEqualTo(0);
    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(10)).isEqualTo(10);
    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(15)).isEqualTo(10);
    assertThat(reader.getClosestBlockStartBeforeOrAtPosition(20)).isEqualTo(20);
  }

  @Test
  public void testGetClosestMarkerStrictlyAfterPosition() throws Exception
  {
    final int blockSize = 10;
    BlockLogReader<Integer, Integer> reader = newReaderWithNullFile(blockSize);

    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(0)).isEqualTo(10);
    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(5)).isEqualTo(10);
    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(10)).isEqualTo(20);
    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(11)).isEqualTo(20);
    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(15)).isEqualTo(20);
    assertThat(reader.getClosestBlockStartStrictlyAfterPosition(20)).isEqualTo(30);
  }

  @Test
  public void testSearchClosestMarkerToKey() throws Exception
  {
    int blockSize = 20;
    writeRecords(blockSize, records(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20));

    try (BlockLogReader<Integer, Integer> reader = newReader(blockSize))
    {
      assertThat(reader.searchClosestBlockStartToKey(0)).isEqualTo(0);
      assertThat(reader.searchClosestBlockStartToKey(1)).isEqualTo(0);
      assertThat(reader.searchClosestBlockStartToKey(2)).isEqualTo(20);
      assertThat(reader.searchClosestBlockStartToKey(3)).isEqualTo(20);
      assertThat(reader.searchClosestBlockStartToKey(4)).isEqualTo(40);
      assertThat(reader.searchClosestBlockStartToKey(5)).isEqualTo(60);
      assertThat(reader.searchClosestBlockStartToKey(6)).isEqualTo(80);
      assertThat(reader.searchClosestBlockStartToKey(7)).isEqualTo(80);
      assertThat(reader.searchClosestBlockStartToKey(8)).isEqualTo(100);
      assertThat(reader.searchClosestBlockStartToKey(9)).isEqualTo(120);
      assertThat(reader.searchClosestBlockStartToKey(10)).isEqualTo(140);
      assertThat(reader.searchClosestBlockStartToKey(19)).isEqualTo(260);
      assertThat(reader.searchClosestBlockStartToKey(20)).isEqualTo(280);
      // out of reach keys
      assertThat(reader.searchClosestBlockStartToKey(21)).isEqualTo(280);
      assertThat(reader.searchClosestBlockStartToKey(22)).isEqualTo(280);
    }
  }

  @Test
  public void testLengthOfStoredRecord() throws Exception
  {
    final int blockSize = 100;
    BlockLogReader<Integer, Integer> reader = newReaderWithNullFile(blockSize);

    int recordLength = 10;
    assertThat(reader.getLengthOfStoredRecord(recordLength, 99)).isEqualTo(recordLength);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 20)).isEqualTo(recordLength);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 10)).isEqualTo(recordLength);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 9)).isEqualTo(recordLength + SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 0)).isEqualTo(recordLength + SIZE_OF_BLOCK_OFFSET);

    recordLength = 150;
    assertThat(reader.getLengthOfStoredRecord(recordLength, 99)).isEqualTo(recordLength + SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 60)).isEqualTo(recordLength + SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 54)).isEqualTo(recordLength + SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 53)).isEqualTo(recordLength + 2 * SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 0)).isEqualTo(recordLength + 2 * SIZE_OF_BLOCK_OFFSET);

    recordLength = 200;
    assertThat(reader.getLengthOfStoredRecord(recordLength, 99)).isEqualTo(recordLength + 2 * SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 8)).isEqualTo(recordLength + 2 * SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 7)).isEqualTo(recordLength + 3 * SIZE_OF_BLOCK_OFFSET);
    assertThat(reader.getLengthOfStoredRecord(recordLength, 0)).isEqualTo(recordLength + 3 * SIZE_OF_BLOCK_OFFSET);
  }

  /**
   * This test is intended to be run only manually to check the performance between binary search
   * and sequential access.
   * Note that sequential run may be extremely long when using high values.
   */
  @Test(enabled=false)
  public void seekPerformanceComparison() throws Exception
  {
    // You may change these values
    long fileSizeInBytes = 100*1024*1024;
    int numberOfValuesToSeek = 50000;
    int blockSize = 256;

    writeRecordsToReachFileSize(blockSize, fileSizeInBytes);
    try (BlockLogReader<Integer, Integer> reader = newReader(blockSize))
    {
      List<Integer> keysToSeek = getShuffledKeys(fileSizeInBytes, numberOfValuesToSeek);
      System.out.println("File size: " + TEST_FILE.length() + " bytes");

      System.out.println("\n---- BINARY SEARCH");
      long minTime = Long.MAX_VALUE;
      long maxTime = Long.MIN_VALUE;
      final long t0 = System.nanoTime();
      for (Integer key : keysToSeek)
      {
        final long ts = System.nanoTime();
        Pair<Boolean, Record<Integer, Integer>> result =
            reader.seekToRecord(key, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY);
        final long te = System.nanoTime() - ts;
        if (te < minTime) minTime = te;
        if (te > maxTime) maxTime = te;
        // show time for seeks that last more than N microseconds (tune as needed)
        if (te/1000 > 1000)
        {
          System.out.println("TIME! key:" + result.getSecond().getKey() + ", time=" + te/1000 + " microseconds");
        }
        assertThat(result.getSecond()).isEqualTo(record(key));
      }
      System.out.println("Time taken: " + ((System.nanoTime() - t0)/1000000) + " milliseconds");
      System.out.println("Min time for a search: " + minTime/1000 + " microseconds");
      System.out.println("Max time for a search: " + maxTime/1000 + " microseconds");
      System.out.println("Max difference for a search: " + (maxTime - minTime)/1000 + " microseconds");

      System.out.println("\n---- SEQUENTIAL SEARCH");
      minTime = Long.MAX_VALUE;
      maxTime = Long.MIN_VALUE;
      final long t1 = System.nanoTime();
      for (Integer val : keysToSeek)
      {
        long ts = System.nanoTime();
        Pair<Boolean, Record<Integer, Integer>> result =
            reader.positionToKeySequentially(0, val, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY);
        assertThat(result.getSecond()).isEqualTo(Record.from(val, val));
        long te = System.nanoTime() - ts;
        if (te < minTime) minTime = te;
        if (te > maxTime) maxTime = te;
      }
      System.out.println("Time taken: " + ((System.nanoTime() - t1)/1000000) + " milliseconds");
      System.out.println("Min time for a search: " + minTime/1000 + " microseconds");
      System.out.println("Max time for a search: " + maxTime/1000000 + " milliseconds");
      System.out.println("Max difference for a search: " + (maxTime - minTime)/1000000 + " milliseconds");
    }
  }

  /** Write provided records with the provided block size. */
  private void writeRecords(int blockSize, List<Record<Integer, Integer>> records) throws ChangelogException
  {
    try (BlockLogWriter<Integer, Integer> writer = newWriter(blockSize))
    {
      for (Record<Integer, Integer> record : records)
      {
        writer.write(record);
      }
    }
  }

  /** Write as many records as needed to reach provided file size. Records goes from 1 up to N. */
  private void writeRecordsToReachFileSize(int blockSize, long sizeInBytes) throws Exception
  {
      final int numberOfValues = (int) sizeInBytes / INT_RECORD_SIZE;
      final int[] values = new int[numberOfValues];
      for (int i = 0; i < numberOfValues; i++)
      {
        values[i] = i+1;
      }
      writeRecords(blockSize, records(values));
  }

  /** Returns provided number of keys to seek in random order, for a file of provided size. */
  private List<Integer> getShuffledKeys(long fileSizeInBytes, int numberOfKeys)
  {
    final int numberOfValues = (int) fileSizeInBytes / INT_RECORD_SIZE;
    final List<Integer> values = new ArrayList<Integer>(numberOfValues);
    for (int i = 0; i < numberOfValues; i++)
    {
      values.add(i+1);
    }
    Collections.shuffle(values);
    return values.subList(0, numberOfKeys);
  }

  private BlockLogWriter<Integer, Integer> newWriter(int sizeOfBlock) throws ChangelogException
  {
    return BlockLogWriter.newWriterForTests(new LogWriter(TEST_FILE), RECORD_PARSER, sizeOfBlock);
  }

  private BlockLogReader<Integer, Integer> newReader(int blockSize) throws FileNotFoundException
  {
    return BlockLogReader.newReaderForTests(TEST_FILE, new RandomAccessFile(TEST_FILE, "r"),
        RECORD_PARSER, blockSize);
  }

  private BlockLogReader<Integer, Integer> newReaderWithNullFile(int blockSize) throws FileNotFoundException
  {
    return BlockLogReader.newReaderForTests(null, null, RECORD_PARSER, blockSize);
  }

  /** Helper to build a list of records. */
  private List<Record<Integer, Integer>> records(int...keys)
  {
    List<Record<Integer, Integer>> records = new ArrayList<Record<Integer, Integer>>();
    for (int key : keys)
    {
      records.add(Record.from(key, key));
    }
    return records;
  }

  /** Helper to build a record. */
  private Record<Integer, Integer> record(int key)
  {
    return Record.from(key, key);
  }

  /**
   * Record parser implementation for records with keys and values as integers to be used in tests.
   * Using integer allow to know precisely the size of the records (4 bytes for key + 4 bytes for value),
   * which is useful for some tests.
   */
  private static class IntRecordParser implements RecordParser<Integer, Integer>
  {
    @Override
    public Record<Integer, Integer> decodeRecord(final ByteString data) throws DecodingException
    {
      ByteSequenceReader reader = data.asReader();
      int key = reader.getInt();
      int value = reader.getInt();
      return Record.from(key, value);
    }

    @Override
    public ByteString encodeRecord(Record<Integer, Integer> record)
    {
      return new ByteStringBuilder().append((int) record.getKey()).append((int) record.getValue()).toByteString();
    }

    @Override
    public Integer decodeKeyFromString(String key) throws ChangelogException
    {
      return Integer.valueOf(key);
    }

    @Override
    public String encodeKeyToString(Integer key)
    {
      return String.valueOf(key);
    }

    @Override
    public Integer getMaxKey()
    {
      return Integer.MAX_VALUE;
    }
  }
}
