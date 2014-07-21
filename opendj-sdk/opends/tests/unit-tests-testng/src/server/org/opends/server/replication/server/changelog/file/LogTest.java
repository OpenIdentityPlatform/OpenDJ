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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.replication.server.changelog.file.LogFileTest.*;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.file.LogFileTest.FailingStringRecordParser;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(sequential=true)
public class LogTest extends DirectoryServerTestCase
{
  // Use a directory dedicated to this test class
  private static final File LOG_DIRECTORY = new File(TestCaseUtils.getUnitTestRootPath(), "changelog-unit");

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
    int sizeLimitPerFileInBytes = 30;

    return Log.openLog(LOG_DIRECTORY, parser, sizeLimitPerFileInBytes);
  }

  @Test
  public void testCursor() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = log.getCursor();

      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testCursorWhenGivenAnExistingKey() throws Exception
  {
    Log<String, String> log = openLog(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = log.getCursor("key005");

      assertThatCursorCanBeFullyReadFromStart(cursor, 5, 10);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testCursorWhenGivenAnUnexistingKey() throws Exception
  {
    Log<String, String> log = openLog(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      // key is between key005 and key006
      cursor = log.getCursor("key005000");

      assertThat(cursor).isNotNull();
      assertThat(cursor.getRecord()).isNull();
      assertThat(cursor.next()).isFalse();
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testCursorWhenGivenANullKey() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = log.getCursor(null);

      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testNearestCursorWhenGivenAnExistingKey() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor1 = null, cursor2 = null, cursor3 = null;
    try {
      // this key is the first key of the log file "key1_key2.log"
      cursor1 = log.getNearestCursor("key001", AFTER_MATCHING_KEY);
      assertThatCursorCanBeFullyReadFromStart(cursor1, 2, 10);

      // this key is the last key of the log file "key3_key4.log"
      cursor2 = log.getNearestCursor("key004", AFTER_MATCHING_KEY);
      assertThatCursorCanBeFullyReadFromStart(cursor2, 5, 10);

      cursor3 = log.getNearestCursor("key009", AFTER_MATCHING_KEY);
      assertThatCursorCanBeFullyReadFromStart(cursor3, 10, 10);
    }
    finally {
      StaticUtils.close(cursor1, cursor2, cursor3, log);
    }
  }

  @Test
  public void testNearestCursorWhenGivenAnExistingKey_KeyIsTheLastOne() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = log.getNearestCursor("key010", AFTER_MATCHING_KEY);

      // lowest higher key does not exist
      assertThatCursorIsExhausted(cursor);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testNearestCursorWhenGivenAnUnexistingKey() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      // key is between key005 and key006
      cursor = log.getNearestCursor("key005000", AFTER_MATCHING_KEY);

      assertThatCursorCanBeFullyReadFromStart(cursor, 6, 10);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testNearestCursorWhenGivenANullKey() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = log.getNearestCursor(null, null);

      assertThatCursorCanBeFullyReadFromStart(cursor, 1, 10);
    }
    finally {
      StaticUtils.close(cursor, log);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testCursorWhenParserFailsToRead() throws Exception
  {
    FailingStringRecordParser parser = new FailingStringRecordParser();
    Log<String, String> log = openLog(parser);
    parser.setFailToRead(true);
    try {
      log.getCursor("key");
    }
    finally {
      StaticUtils.close(log);
    }
  }

  @Test
  public void testGetOldestRecord() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    try
    {
      Record<String, String> record = log.getOldestRecord();

      assertThat(record).isEqualTo(Record.from("key001", "value1"));
    }
    finally {
      StaticUtils.close(log);
    }
  }

  @Test
  public void testGetNewestRecord() throws Exception
  {
    Log<String, String> log = openLog(LogFileTest.RECORD_PARSER);
    try
    {
      Record<String, String> record = log.getNewestRecord();

      assertThat(record).isEqualTo(Record.from("key010", "value10"));
    }
    finally {
      StaticUtils.close(log);
    }
  }

  /**
   * Test that changes are visible immediately to a reader after a write.
   */
  @Test
  public void testWriteAndReadOnSameLog() throws Exception
  {
    Log<String, String> writeLog = null;
    Log<String, String> readLog = null;
    try
    {
      writeLog = openLog(LogFileTest.RECORD_PARSER);
      readLog = openLog(LogFileTest.RECORD_PARSER);

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
    finally
    {
      StaticUtils.close(writeLog, readLog);
    }
  }

  @Test
  public void testTwoConcurrentWrite() throws Exception
  {
    Log<String, String> writeLog1 = null;
    Log<String, String> writeLog2 = null;
    DBCursor<Record<String, String>> cursor = null;
    try
    {
      writeLog1 = openLog(LogFileTest.RECORD_PARSER);
      writeLog2 = openLog(LogFileTest.RECORD_PARSER);
      writeLog1.append(Record.from("key020", "starting record"));
      AtomicReference<ChangelogException> exceptionRef = new AtomicReference<ChangelogException>();
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
      cursor = writeLog1.getCursor("key020");
      for (int i = 1; i <= 61; i++)
      {
         assertThat(cursor.next()).isTrue();
      }
      assertThat(cursor.getRecord()).isIn(Record.from("nkb030", "vb30"), Record.from("nka030", "va30"));
    }
    finally
    {
      StaticUtils.close(cursor, writeLog1, writeLog2);
    }
  }

  /**
   *  This test should be disabled.
   *  Enable it locally when you need to have an rough idea of write performance.
   */
  @Test(enabled=false)
  public void logWriteSpeed() throws Exception
  {
    Log<String, String> writeLog = null;
    try
    {
      long sizeOf1MB = 1024*1024;
      writeLog = Log.openLog(LOG_DIRECTORY, LogFileTest.RECORD_PARSER, sizeOf1MB);

      for (int i = 1; i < 1000000; i++)
      {
        writeLog.append(Record.from(String.format("key%010d", i), "value" + i));
      }
    }
    finally
    {
      StaticUtils.close(writeLog);
    }
  }

  @Test
  public void testWriteWhenCursorIsOpenedAndAheadLogFileIsRotated() throws Exception
  {
    DBCursor<Record<String, String>> cursor = null;
    Log<String, String> log = null;
    try
    {
      log = openLog(LogFileTest.RECORD_PARSER);
      cursor = log.getCursor();
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
    finally
    {
      StaticUtils.close(cursor, log);
    }
  }

  @Test
  public void testWriteWhenMultiplesCursorsAreOpenedAndAheadLogFileIsRotated() throws Exception
  {
    DBCursor<Record<String, String>> cursor1 = null, cursor2 = null, cursor3 = null, cursor4 = null;
    Log<String, String> log = null;
    try
    {
      log = openLog(LogFileTest.RECORD_PARSER);
      cursor1 = log.getCursor();
      advanceCursorUpTo(cursor1, 1, 1);
      cursor2 = log.getCursor();
      advanceCursorUpTo(cursor2, 1, 4);
      cursor3 = log.getCursor();
      advanceCursorUpTo(cursor3, 1, 9);
      cursor4 = log.getCursor();
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
    finally
    {
      StaticUtils.close(cursor1, cursor2, cursor3, cursor4, log);
    }
  }

  @Test
  public void testClear() throws Exception
  {
    DBCursor<Record<String, String>> cursor = null;
    Log<String, String> log = null;
    try
    {
      log = openLog(LogFileTest.RECORD_PARSER);
      log.clear();

      cursor = log.getCursor();
      assertThatCursorIsExhausted(cursor);
    }
    finally
    {
      StaticUtils.close(cursor, log);
    }
  }

  // TODO : Should be re-enabled once the issue with robot functional test replication/totalupdate.txt is solved
  @Test(enabled=false, expectedExceptions=ChangelogException.class)
  public void testClearWhenCursorIsOpened() throws Exception
  {
    DBCursor<Record<String, String>> cursor = null;
    Log<String, String> log = null;
    try
    {
      log = openLog(LogFileTest.RECORD_PARSER);
      cursor = log.getCursor();
      log.clear();
    }
    finally
    {
      StaticUtils.close(cursor, log);
    }
  }

  @DataProvider(name = "purgeKeys")
  Object[][] purgeKeys()
  {
    // purge key, first record expected in the cursor, startIndex + endIndex to fully read the cursor
    return new Object[][]
    {
      // lowest key of the read-only log file "key005_key006.log"
      { "key005", Record.from("key005", "value5"), 6, 10},
      // key that is not the lowest of the read-only log file "key005_key006.log"
      { "key006", Record.from("key005", "value5"), 6, 10},
      // lowest key of the ahead log file "ahead.log"
      { "key009", Record.from("key009", "value9"), 10, 10},
      // key that is not the lowest of the ahead log file "ahead.log"
      { "key010", Record.from("key009", "value9"), 10, 10},

      // key not present in log, which is between key005 and key006
      { "key005a", Record.from("key005", "value5"), 6, 10},
      // key not present in log, which is between key006 and key007
      { "key006a", Record.from("key007", "value7"), 8, 10},
      // key not present in log, which is lower than oldest key key001
      { "key000", Record.from("key001", "value1"), 2, 10},
      // key not present in log, which is higher than newest key key010
      // should return the lowest key present in ahead log
      { "key011", Record.from("key009", "value9"), 10, 10},
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
    Log<String, String> log = null;
    DBCursor<Record<String, String>> cursor = null;
    try
    {
      log = openLog(LogFileTest.RECORD_PARSER);

      log.purgeUpTo(purgeKey);

      cursor = log.getCursor();
      assertThat(cursor.next()).isTrue();
      assertThat(cursor.getRecord()).isEqualTo(firstRecordExpectedAfterPurge);
      assertThatCursorCanBeFullyRead(cursor, cursorStartIndex, cursorEndIndex);
    }
    finally
    {
      StaticUtils.close(cursor, log);
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

  /**
   * Read the cursor until exhaustion, beginning at start of cursor.
   */
  private void assertThatCursorCanBeFullyReadFromStart(DBCursor<Record<String, String>> cursor, int fromIndex, int endIndex)
      throws Exception
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
