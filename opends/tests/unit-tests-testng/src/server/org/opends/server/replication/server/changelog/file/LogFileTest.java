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

import java.io.File;

import org.opends.messages.Message;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("javadoc")
@Test(sequential=true)
public class LogFileTest extends DirectoryServerTestCase
{
  private static final String TEST_DIRECTORY_CHANGELOG = "test-output/changelog";

  private static final StringRecordParser RECORD_PARSER = new StringRecordParser();

  private static final RecordParser<String,String> RECORD_PARSER_FAILING_TO_READ = new StringRecordParser() {
      @Override
      public Record<String, String> decodeRecord(ByteString data) throws DecodingException
      {
        throw new DecodingException(Message.raw("Error when parsing record"));
      }
  };

  @BeforeMethod
  /** Create a new log file with ten records starting from (key1, value1) until (key10, value10). */
  public void initialize() throws Exception
  {
    File theLogFile = new File(TEST_DIRECTORY_CHANGELOG, LogFile.LOG_FILE_NAME);
    if (theLogFile.exists())
    {
      theLogFile.delete();
    }
    LogFile<String, String> logFile = getLogFile(RECORD_PARSER);

    for (int i = 1; i <= 10; i++)
    {
      logFile.addRecord("key"+i, "value"+i);
    }
    logFile.close();
  }

  @AfterMethod
  public void cleanTestChangelogDirectory()
  {
    final File rootPath = new File(TEST_DIRECTORY_CHANGELOG);
    if (rootPath.exists())
    {
      StaticUtils.recursiveDelete(rootPath);
    }
  }

  private LogFile<String, String> getLogFile(RecordParser<String, String> parser) throws ChangelogException
  {
    LogFile<String, String> logFile = LogFile.newAppendableLogFile(new File(TEST_DIRECTORY_CHANGELOG), parser);
    return logFile;
  }

  @Test
  public void testCursor() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getCursor();

      assertThat(cursor.getRecord()).isEqualTo(Record.from("key1", "value1"));
      assertThatCursorCanBeFullyRead(cursor, 2, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testCursorWhenGivenAnExistingKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getCursor("key5");

      assertThat(cursor.getRecord()).isEqualTo(Record.from("key5", "value5"));
      assertThatCursorCanBeFullyRead(cursor, 6, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testCursorWhenGivenAnUnexistingKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getCursor("key");

      assertThat(cursor).isNotNull();
      assertThat(cursor.getRecord()).isNull();
      assertThat(cursor.next()).isFalse();
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testCursorWhenGivenANullKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getCursor(null);

      // should start from start
      assertThat(cursor.getRecord()).isEqualTo(Record.from("key1", "value1"));
      assertThatCursorCanBeFullyRead(cursor, 2, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testNearestCursorWhenGivenAnExistingKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getNearestCursor("key1");

      // lowest higher key is key2
      assertThat(cursor.getRecord()).isEqualTo(Record.from("key2", "value2"));
      assertThatCursorCanBeFullyRead(cursor, 3, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testNearestCursorWhenGivenAnUnexistingKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getNearestCursor("key0");

      // lowest higher key is key1
      assertThat(cursor.getRecord()).isEqualTo(Record.from("key1", "value1"));
      assertThatCursorCanBeFullyRead(cursor, 2, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test
  public void testNearestCursorWhenGivenANullKey() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getNearestCursor(null);

      // should start from start
      assertThat(cursor.getRecord()).isEqualTo(Record.from("key1", "value1"));
      assertThatCursorCanBeFullyRead(cursor, 2, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testCursorWhenParserFailsToRead() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER_FAILING_TO_READ);
    try {
      changelog.getCursor("key");
    }
    finally {
      StaticUtils.close(changelog);
    }
  }

  @Test
  public void testGetOldestRecord() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    try
    {
      Record<String, String> record = changelog.getOldestRecord();

      assertThat(record).isEqualTo(Record.from("key1", "value1"));
    }
    finally {
      StaticUtils.close(changelog);
    }
  }

  @Test
  public void testGetNewestRecord() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    try
    {
      Record<String, String> record = changelog.getNewestRecord();

      assertThat(record).isEqualTo(Record.from("key10", "value10"));
    }
    finally {
      StaticUtils.close(changelog);
    }
  }

  @Test
  /**
   * Test that changes are visible immediately to a reader after a write.
   */
  public void testWriteAndReadOnSameLogFile() throws Exception
  {
    LogFile<String, String> writeLog = null;
    LogFile<String, String> readLog = null;
    try
    {
      writeLog = getLogFile(RECORD_PARSER);
      readLog = getLogFile(RECORD_PARSER);

      for (int i = 1; i <= 100; i++)
      {
        Record<String, String> record = Record.from("newkey" + i, "newvalue" + i);
        writeLog.addRecord(record);
        assertThat(writeLog.getNewestRecord()).as("write changelog " + i).isEqualTo(record);
        assertThat(writeLog.getOldestRecord()).as("write changelog " + i).isEqualTo(Record.from("key1", "value1"));
        assertThat(readLog.getNewestRecord()).as("read changelog " + i).isEqualTo(record);
        assertThat(readLog.getOldestRecord()).as("read changelog " + i).isEqualTo(Record.from("key1", "value1"));
      }
    }
    finally
    {
      StaticUtils.close(writeLog, readLog);
    }
  }

  @Test()
  public void testTwoConcurrentWrite() throws Exception
  {
    final LogFile<String, String> writeLog1 = getLogFile(RECORD_PARSER);
    final LogFile<String, String> writeLog2 = getLogFile(RECORD_PARSER);
    try
    {
      writeLog1.addRecord(Record.from("startkey", "startvalue"));
      Thread write1 = getWriteLogThread(writeLog1, "a");
      Thread write2 = getWriteLogThread(writeLog2, "b");
      write1.run();
      write2.run();

      write1.join();
      write2.join();
      writeLog1.syncToFileSystem();
      DBCursor<Record<String, String>> cursor = writeLog1.getCursor("startkey");
      for (int i = 1; i <= 200; i++)
      {
         assertThat(cursor.next()).isTrue();
      }
      assertThat(cursor.getRecord()).isIn(Record.from("k-b100", "v-b100"), Record.from("k-a100", "v-a100"));
    }
    finally
    {
      StaticUtils.close(writeLog1, writeLog2);
    }
  }

  /**
   * Read the cursor until exhaustion, ensuring that its first value is fromIndex and its last value
   * endIndex, using (keyN, valueN) where N is the index.
   */
  private void assertThatCursorCanBeFullyRead(DBCursor<Record<String, String>> cursor, int fromIndex, int endIndex)
      throws Exception
  {
    for (int i = fromIndex; i <= endIndex; i++)
    {
      assertThat(cursor.next()).as("next() value when i=" + i).isTrue();
      assertThat(cursor.getRecord()).isEqualTo(Record.from("key" + i, "value" + i));
    }
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
  }

  /** Returns a thread that write 100 records to the provided log. */
  private Thread getWriteLogThread(final LogFile<String, String> writeLog, final String recordPrefix)
  {
    return new Thread() {
      public void run()
      {
        for (int i = 1; i <= 100; i++)
        {
          Record<String, String> record = Record.from("k-" + recordPrefix + i, "v-" + recordPrefix + i);
          try
          {
            writeLog.addRecord(record);
          }
          catch (ChangelogException e)
          {
            e.printStackTrace();
          }
        }
      }
    };
  }

  /**
   * Record parser implementation for records with keys as String and values as
   * String, to be used in tests.
   */
  private static class StringRecordParser implements RecordParser<String, String>
  {
    private static final byte STRING_SEPARATOR = 0;

    public Record<String, String> decodeRecord(final ByteString data) throws DecodingException
    {
      ByteSequenceReader reader = data.asReader();
      String key = reader.getString(getNextStringLength(reader));
      reader.skip(1);
      String value = reader.getString(getNextStringLength(reader));
      return key.isEmpty() || value.isEmpty() ? null : Record.from(key, value);
    }

    /** Returns the length of next string by looking for the zero byte used as separator. */
    private int getNextStringLength(ByteSequenceReader reader)
    {
      int length = 0;
      while (reader.peek(length) != STRING_SEPARATOR)
      {
        length++;
      }
      return length;
    }

    public ByteString encodeRecord(String key, String value)
    {
      return new ByteStringBuilder()
        .append(key).append(STRING_SEPARATOR)
        .append(value).append(STRING_SEPARATOR).toByteString();
    }
  }

}
