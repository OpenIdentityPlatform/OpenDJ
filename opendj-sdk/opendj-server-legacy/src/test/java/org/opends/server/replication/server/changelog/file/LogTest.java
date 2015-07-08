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
import static org.mockito.Mockito.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.replication.server.changelog.file.LogFileTest.*;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.server.changelog.api.AbortedChangelogCursorException;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.replication.server.changelog.file.Log.LogRotationParameters;
import org.opends.server.replication.server.changelog.file.LogFileTest.FailingStringRecordParser;
import org.opends.server.replication.server.changelog.file.Record.Mapper;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(sequential=true)
public class LogTest extends DirectoryServerTestCase
{
  /** Use a directory dedicated to this test class. */
  private static final File LOG_DIRECTORY = new File(TestCaseUtils.getUnitTestRootPath(), "changelog-unit");

  private static final long NO_TIME_BASED_LOG_ROTATION = 0;

  @BeforeMethod
  public void initialize() throws Exception
  {
    // Delete any previous log
    if (LOG_DIRECTORY.exists())
    {
      StaticUtils.recursiveDelete(LOG_DIRECTORY);
    }

    // Build a log with 10 records with String keys and String values
    // Keys are using the format keyNNN where N is a figure
    // You should always ensure keys are correctly ordered otherwise tests may break unexpectedly
    Log<String, String> log = openLog(RECORD_PARSER);
    for (int i = 1; i <= 10; i++)
    {
      log.append(Record.from(String.format("key%03d", i), "value" + i));
    }
    log.close();
  }

  private Log<String, String> openLog(RecordParser<String, String> parser) throws ChangelogException
  {
    // Each string record has a length of approximately 18 bytes
    // This size is set in order to have 2 records per log file before the rotation happens
    // This allow to ensure rotation mechanism is thoroughly tested
    // Some tests rely on having 2 records per log file (especially the purge tests), so take care
    // if this value has to be changed
    final int sizeLimitPerFileInBytes = 30;
    final LogRotationParameters rotationParams = new LogRotationParameters(sizeLimitPerFileInBytes,
        NO_TIME_BASED_LOG_ROTATION, NO_TIME_BASED_LOG_ROTATION);
    final ReplicationEnvironment replicationEnv = mock(ReplicationEnvironment.class);

    return Log.openLog(replicationEnv, LOG_DIRECTORY, parser, rotationParams);
  }

  @Test
  public void testCursor() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor())
    {
      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
  }

  @Test
  public void testCursorWhenGivenAnExistingKey() throws Exception
  {
    try (Log<String, String> log = openLog(RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor("key005"))
    {
      assertThatCursorCanBeFullyReadFromStart(cursor, 5, 10);
    }
  }

  @Test
  public void testCursorWhenGivenAnUnexistingKey() throws Exception
  {
    try (Log<String, String> log = openLog(RECORD_PARSER);
        // key is between key005 and key006
        DBCursor<Record<String, String>> cursor = log.getCursor("key005000"))
    {
      assertThat(cursor).isNotNull();
      assertThat(cursor.getRecord()).isNull();
      assertThat(cursor.next()).isFalse();
    }
  }

  @Test
  public void testCursorWhenGivenANullKey() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor(null))
    {
      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
  }

  @DataProvider
  Object[][] cursorData()
  {
    return new Object[][] {
      // 3 first values are input data : key to position to, key matching strategy, position strategy,
      // 2 last values are expected output :
      //    first index of cursor (-1 if cursor should be exhausted), last index of cursor
      { "key000", EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },
      { "key001", EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 10 },
      { "key004", EQUAL_TO_KEY, ON_MATCHING_KEY, 4, 10 },
      { "key0050", EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },
      { "key009", EQUAL_TO_KEY, ON_MATCHING_KEY, 9, 10 },
      { "key010", EQUAL_TO_KEY, ON_MATCHING_KEY, 10, 10 },
      { "key011", EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },

      { "key000", EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { "key001", EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 10 },
      { "key004", EQUAL_TO_KEY, AFTER_MATCHING_KEY, 5, 10 },
      { "key0050", EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { "key009", EQUAL_TO_KEY, AFTER_MATCHING_KEY, 10, 10 },
      { "key010", EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { "key011", EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },

      { "key000", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 10 },
      { "key001", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 10 },
      { "key004", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 4, 10 },
      { "key005", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 5, 10 },
      { "key0050", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 5, 10 },
      { "key006", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 6, 10 },
      { "key009", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 9, 10 },
      { "key010", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 10, 10 },
      { "key011", LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 10, 10 },

      { "key000", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 1, 10 },
      { "key001", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 10 },
      { "key004", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 5, 10 },
      { "key005", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 6, 10 },
      { "key0050", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 6, 10 },
      { "key006", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 7, 10 },
      { "key009", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 10, 10 },
      { "key010", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { "key011", LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },

      { "key000", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 10 },
      { "key001", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 10 },
      { "key004", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 4, 10 },
      { "key0050", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 6, 10 },
      { "key009", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 9, 10 },
      { "key010", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 10, 10 },
      { "key011", GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },

      { "key000", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 1, 10 },
      { "key001", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 10 },
      { "key004", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 5, 10 },
      { "key0050", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 6, 10 },
      { "key009", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 10, 10 },
      { "key010", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { "key011", GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
    };
  }

  @Test(dataProvider="cursorData")
  public void testCursorWithStrategies(String key, KeyMatchingStrategy matchingStrategy,
      PositionStrategy positionStrategy, int cursorShouldStartAt, int cursorShouldEndAt) throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor(key, matchingStrategy, positionStrategy))
    {
      if (cursorShouldStartAt != -1)
      {
        assertThatCursorCanBeFullyReadFromStart(cursor, cursorShouldStartAt, cursorShouldEndAt);
      }
      else
      {
        assertThatCursorIsExhausted(cursor);
      }
    }
  }

  @Test
  public void testCursorMatchingAnyPositioningAnyWhenGivenANullKey() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor(null, null, null))
    {
      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testCursorWhenParserFailsToRead() throws Exception
  {
    FailingStringRecordParser parser = new FailingStringRecordParser();
    try (Log<String, String> log = openLog(parser))
    {
      parser.setFailToRead(true);
      log.getCursor("key");
    }
  }

  @Test
  public void testGetOldestRecord() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER))
    {
      Record<String, String> record = log.getOldestRecord();

      assertThat(record).isEqualTo(Record.from("key001", "value1"));
    }
  }

  @Test
  public void testGetNewestRecord() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER))
    {
      Record<String, String> record = log.getNewestRecord();

      assertThat(record).isEqualTo(Record.from("key010", "value10"));
    }
  }

  /** Test that changes are visible immediately to a reader after a write. */
  @Test
  public void testWriteAndReadOnSameLog() throws Exception
  {
    try (Log<String, String> writeLog = openLog(LogFileTest.RECORD_PARSER);
        Log<String, String> readLog = openLog(LogFileTest.RECORD_PARSER))
    {
      for (int i = 1; i <= 10; i++)
      {
        Record<String, String> record = Record.from(String.format("nkey%03d", i), "nvalue" + i);
        writeLog.append(record);
        assertThat(writeLog.getNewestRecord()).as("write changelog " + i).isEqualTo(record);
        assertThat(writeLog.getOldestRecord()).as("write changelog " + i).isEqualTo(Record.from("key001", "value1"));
        assertThat(readLog.getNewestRecord()).as("read changelog " + i).isEqualTo(record);
        assertThat(readLog.getOldestRecord()).as("read changelog " + i).isEqualTo(Record.from("key001", "value1"));
      }
    }
  }

  @Test
  public void testTwoConcurrentWrite() throws Exception
  {
    try (Log<String, String> writeLog1 = openLog(LogFileTest.RECORD_PARSER);
        Log<String, String> writeLog2 = openLog(LogFileTest.RECORD_PARSER))
    {
      writeLog1.append(Record.from("key020", "starting record"));
      AtomicReference<ChangelogException> exceptionRef = new AtomicReference<>();
      Thread write1 = getWriteLogThread(writeLog1, "a", exceptionRef);
      Thread write2 = getWriteLogThread(writeLog2, "b", exceptionRef);
      write1.run();
      write2.run();

      write1.join();
      write2.join();
      if (exceptionRef.get() != null)
      {
        throw exceptionRef.get();
      }
      writeLog1.syncToFileSystem();

      try (DBCursor<Record<String, String>> cursor = writeLog1.getCursor("key020"))
      {
        for (int i = 1; i <= 61; i++)
        {
          assertThat(cursor.next()).isTrue();
        }
        assertThat(cursor.getRecord()).isIn(Record.from("nkb030", "vb30"), Record.from("nka030", "va30"));
      }
    }
  }

  /**
   *  This test should be disabled.
   *  Enable it locally when you need to have an rough idea of write performance.
   */
  @Test(enabled=false)
  public void logWriteSpeed() throws Exception
  {
    long sizeOf10MB = 10 * 1024 * 1024;
    final LogRotationParameters rotationParams = new LogRotationParameters(
        sizeOf10MB, NO_TIME_BASED_LOG_ROTATION, NO_TIME_BASED_LOG_ROTATION);
    final ReplicationEnvironment replicationEnv = mock(ReplicationEnvironment.class);

    try (Log<String, String> writeLog =
        Log.openLog(replicationEnv, LOG_DIRECTORY, LogFileTest.RECORD_PARSER, rotationParams))
    {
      for (int i = 1; i < 1000000; i++)
      {
        writeLog.append(Record.from(String.format("key%010d", i), "value" + i));
      }
    }
  }

  @Test
  public void testWriteWhenCursorIsOpenedAndAheadLogFileIsRotated() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor())
    {
      // advance cursor to last record to ensure it is pointing to ahead log file
      advanceCursorUpTo(cursor, 1, 10);

      // add new records to ensure the ahead log file is rotated
      for (int i = 11; i <= 20; i++)
      {
        log.append(Record.from(String.format("key%03d", i), "value" + i));
      }

      // check that cursor can fully read the new records
      assertThatCursorCanBeFullyRead(cursor, 11, 20);
    }
  }

  @Test
  public void testWriteWhenMultiplesCursorsAreOpenedAndAheadLogFileIsRotated() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor1 = log.getCursor();
        DBCursor<Record<String, String>> cursor2 = log.getCursor();
        DBCursor<Record<String, String>> cursor3 = log.getCursor();
        DBCursor<Record<String, String>> cursor4 = log.getCursor())
    {
      advanceCursorUpTo(cursor1, 1, 1);
      advanceCursorUpTo(cursor2, 1, 4);
      advanceCursorUpTo(cursor3, 1, 9);
      advanceCursorUpTo(cursor4, 1, 10);

      // add new records to ensure the ahead log file is rotated
      for (int i = 11; i <= 20; i++)
      {
        log.append(Record.from(String.format("key%03d", i), "value" + i));
      }

      // check that cursors can fully read the new records
      assertThatCursorCanBeFullyRead(cursor1, 2, 20);
      assertThatCursorCanBeFullyRead(cursor2, 5, 20);
      assertThatCursorCanBeFullyRead(cursor3, 10, 20);
      assertThatCursorCanBeFullyRead(cursor4, 11, 20);
    }
  }

  @Test
  public void testClear() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER))
    {
      log.clear();

      try (DBCursor<Record<String, String>> cursor = log.getCursor())
      {
        assertThatCursorIsExhausted(cursor);
      }
    }
  }

  /** TODO : Should be re-enabled once the issue with robot functional test replication/totalupdate.txt is solved */
  @Test(enabled=false, expectedExceptions=ChangelogException.class)
  public void testClearWhenCursorIsOpened() throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> cursor = log.getCursor())
    {
      log.clear();
    }
  }

  @DataProvider(name = "purgeKeys")
  Object[][] purgeKeys()
  {
    // purge key, first record expected in the cursor, startIndex + endIndex to fully read the cursor
    return new Object[][]
    {
      // lowest key of the read-only log file "key005_key006.log"
      { "key005", Record.from("key005", "value5"), 6, 10 },
      // key that is not the lowest of the read-only log file "key005_key006.log"
      { "key006", Record.from("key005", "value5"), 6, 10 },
      // lowest key of the ahead log file "ahead.log"
      { "key009", Record.from("key009", "value9"), 10, 10 },
      // key that is not the lowest of the ahead log file "ahead.log"
      { "key010", Record.from("key009", "value9"), 10, 10 },

      // key not present in log, which is between key005 and key006
      { "key005a", Record.from("key005", "value5"), 6, 10 },
      // key not present in log, which is between key006 and key007
      { "key006a", Record.from("key007", "value7"), 8, 10 },
      // key not present in log, which is lower than oldest key key001
      { "key000", Record.from("key001", "value1"), 2, 10 },
      // key not present in log, which is higher than newest key key010
      // should return the lowest key present in ahead log
      { "key011", Record.from("key009", "value9"), 10, 10 },
    };
  }

  /**
   * Given a purge key, after purge is done, expects a new cursor to point on first record provided and
   * then to be fully read starting at provided start index and finishing at provided end index.
   */
  @Test(dataProvider="purgeKeys")
  public void testPurge(String purgeKey, Record<String,String> firstRecordExpectedAfterPurge,
      int cursorStartIndex, int cursorEndIndex) throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER))
    {
      log.purgeUpTo(purgeKey);

      try (DBCursor<Record<String, String>> cursor = log.getCursor())
      {
        assertThat(cursor.next()).isTrue();
        assertThat(cursor.getRecord()).isEqualTo(firstRecordExpectedAfterPurge);
        assertThatCursorCanBeFullyRead(cursor, cursorStartIndex, cursorEndIndex);
      }
    }
  }

  /**
   * Similar to testPurge() test but with a concurrent cursor opened before starting the purge.
   * <p>
   * For all keys but "key000" the concurrent cursor should be aborted because the corresponding log file
   * has been purged.
   */
  @Test(dataProvider="purgeKeys")
  public void testPurgeWithConcurrentCursorOpened(String purgeKey, Record<String,String> firstRecordExpectedAfterPurge,
      int cursorStartIndex, int cursorEndIndex) throws Exception
  {
    DBCursor<Record<String, String>> cursor = null;
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
        DBCursor<Record<String, String>> concurrentCursor = log.getCursor())
    {
      concurrentCursor.next();
      assertThat(concurrentCursor.getRecord()).isEqualTo(Record.from("key001", "value1"));

      log.purgeUpTo(purgeKey);

      cursor = log.getCursor();
      assertThat(cursor.next()).isTrue();
      assertThat(cursor.getRecord()).isEqualTo(firstRecordExpectedAfterPurge);
      assertThatCursorCanBeFullyRead(cursor, cursorStartIndex, cursorEndIndex);

      // concurrent cursor is expected to be aborted on the next() call for all cases but one
      assertThat(concurrentCursor.getRecord()).isEqualTo(Record.from("key001", "value1"));
      if (purgeKey.equals("key000"))
      {
        // in that case no purge has been done, so cursor should not be aborted
        assertThatCursorCanBeFullyRead(concurrentCursor, cursorStartIndex, cursorEndIndex);
      }
      else
      {
        // in other cases cursor should be aborted
        try
        {
          concurrentCursor.next();
          fail("Expected an AbortedChangelogCursorException");
        }
        catch (AbortedChangelogCursorException e) {
          // nothing to do
        }
      }
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  static final Mapper<String, Integer> MAPPER = new Record.Mapper<String, Integer>()
      {
        @Override
        public Integer map(String value)
        {
          // extract numeric value, e.g. from "value10" return 10
          return Integer.valueOf(value.substring("value".length()));
        }
      };

  @DataProvider
  Object[][] findBoundaryKeyData()
  {
    return new Object[][] {
       // limit value, expected key
       { 0, null },
       { 1, "key001" },
       { 2, "key001" },
       { 3, "key003" },
       { 4, "key003" },
       { 5, "key005" },
       { 6, "key005" },
       { 7, "key007" },
       { 8, "key007" },
       { 9, "key009" },
       { 10, "key009" },
       { 11, "key009" },
       { 12, "key009" },
    };
  }

  @Test(dataProvider = "findBoundaryKeyData")
  public void testFindBoundaryKeyFromRecord(int limitValue, String expectedKey) throws Exception
  {
    try (Log<String, String> log = openLog(LogFileTest.RECORD_PARSER))
    {
      assertThat(log.findBoundaryKeyFromRecord(MAPPER, limitValue)).isEqualTo(expectedKey);
    }
  }

  private void advanceCursorUpTo(DBCursor<Record<String, String>> cursor, int fromIndex, int endIndex)
      throws Exception
  {
    for (int i = fromIndex; i <= endIndex; i++)
    {
      assertThat(cursor.next()).as("next() value when i=" + i).isTrue();
      assertThat(cursor.getRecord()).isEqualTo(Record.from(String.format("key%03d", i), "value" + i));
    }
  }

  /**
   * Read the cursor until exhaustion, ensuring that its first value is fromIndex and its last value
   * endIndex, using (keyN, valueN) where N is the index.
   */
  private void assertThatCursorCanBeFullyRead(DBCursor<Record<String, String>> cursor, int fromIndex, int endIndex)
      throws Exception
  {
    advanceCursorUpTo(cursor, fromIndex, endIndex);
    assertThatCursorIsExhausted(cursor);
  }

  /** Read the cursor until exhaustion, beginning at start of cursor. */
  private void assertThatCursorCanBeFullyReadFromStart(DBCursor<Record<String, String>> cursor, int fromIndex,
      int endIndex) throws Exception
  {
    assertThat(cursor.getRecord()).isNull();
    assertThatCursorCanBeFullyRead(cursor, fromIndex, endIndex);
  }

  private void assertThatCursorIsExhausted(DBCursor<Record<String, String>> cursor) throws Exception
  {
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  /** Returns a thread that write N records to the provided log. */
  private Thread getWriteLogThread(final Log<String, String> writeLog, final String recordPrefix,
      final AtomicReference<ChangelogException> exceptionRef)
  {
    return new Thread() {
      @Override
      public void run()
      {
        for (int i = 1; i <= 30; i++)
        {
          Record<String, String> record = Record.from(
              String.format("nk%s%03d", recordPrefix, i), "v" + recordPrefix + i);
          try
          {
            writeLog.append(record);
          }
          catch (ChangelogException e)
          {
            // keep the first exception only
            exceptionRef.compareAndSet(null, e);
          }
        }
      }
    };
  }
}
