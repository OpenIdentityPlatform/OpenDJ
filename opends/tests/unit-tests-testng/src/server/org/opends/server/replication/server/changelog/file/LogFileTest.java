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
import java.io.RandomAccessFile;

import org.opends.messages.Message;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("javadoc")
@Test(sequential=true)
public class LogFileTest extends DirectoryServerTestCase
{
  private static final File TEST_DIRECTORY = new File(TestCaseUtils.getUnitTestRootPath(), "changelog-unit");

  private static final File TEST_LOG_FILE = new File(TEST_DIRECTORY, Log.HEAD_LOG_FILE_NAME);

  static final StringRecordParser RECORD_PARSER = new StringRecordParser();

  @BeforeClass
  public void createTestDirectory()
  {
    TEST_DIRECTORY.mkdirs();
  }

  @BeforeMethod
  /** Create a new log file with ten records starting from (key1, value1) until (key10, value10). */
  public void initialize() throws Exception
  {
    if (TEST_LOG_FILE.exists())
    {
      TEST_LOG_FILE.delete();
    }
    LogFile<String, String> logFile =  null;
    try
    {
      logFile = getLogFile(RECORD_PARSER);

      for (int i = 1; i <= 10; i++)
      {
        logFile.append(Record.from(String.format("key%02d", i), "value"+i));
      }
    }
    finally
    {
      StaticUtils.close(logFile);
    }
  }

  @AfterClass
  public void cleanTestChangelogDirectory()
  {
    StaticUtils.recursiveDelete(TEST_DIRECTORY);
  }

  private LogFile<String, String> getLogFile(RecordParser<String, String> parser) throws ChangelogException
  {
    return LogFile.newAppendableLogFile(TEST_LOG_FILE, parser);
  }

  @Test
  public void testCursor() throws Exception
  {
    LogFile<String, String> changelog = getLogFile(RECORD_PARSER);
    DBCursor<Record<String, String>> cursor = null;
    try {
      cursor = changelog.getCursor();

      assertThatCursorCanBeFullyRead(cursor, 1, 10);
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
      cursor = changelog.getCursor("key05");

      assertThatCursorCanBeFullyRead(cursor, 5, 10);
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

      assertThatCursorCanBeFullyRead(cursor, 1, 10);
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
      cursor = changelog.getNearestCursor("key01");

      assertThatCursorCanBeFullyRead(cursor, 2, 10);
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
      cursor = changelog.getNearestCursor("key00");

      assertThatCursorCanBeFullyRead(cursor, 1, 10);
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

      assertThatCursorCanBeFullyRead(cursor, 1, 10);
    }
    finally {
      StaticUtils.close(cursor, changelog);
    }
  }

  @Test(expectedExceptions=ChangelogException.class)
  public void testCursorWhenParserFailsToRead() throws Exception
  {
    FailingStringRecordParser parser = new FailingStringRecordParser();
    LogFile<String, String> changelog = getLogFile(parser);
    parser.setFailToRead(true);
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

      assertThat(record).isEqualTo(Record.from("key01", "value1"));
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

  @DataProvider(name = "corruptedRecordData")
  Object[][] corruptedRecordData()
  {
    return new Object[][]
    {
      // write partial record size (should be 4 bytes)
      { 1, new ByteStringBuilder().append((byte) 0) },
      // write partial record size (should be 4 bytes)
      { 2, new ByteStringBuilder().append((byte) 0).append((byte) 0).append((byte) 0) },
      // write size only
      { 3, new ByteStringBuilder().append(10) },
      // write size + key
      { 4, new ByteStringBuilder().append(100).append("key") },
      // write size + key + separator
      { 5, new ByteStringBuilder().append(100).append("key").append(StringRecordParser.STRING_SEPARATOR) },
      // write size + key + separator + partial value
      { 6, new ByteStringBuilder().append(100).append("key").append(StringRecordParser.STRING_SEPARATOR).append("v") },
    };
  }

  @Test(dataProvider="corruptedRecordData")
  public void testRecoveryOnCorruptedLogFile(
      @SuppressWarnings("unused") int unusedId,
      ByteStringBuilder corruptedRecordData) throws Exception
  {
    LogFile<String, String> logFile = null;
    DBCursor<Record<String, String>> cursor = null;
    try
    {
      corruptTestLogFile(corruptedRecordData);

      // open the log file: the file should be repaired at this point
      logFile = getLogFile(RECORD_PARSER);

      // write a new valid record
      logFile.append(Record.from(String.format("key%02d", 11), "value"+ 11));

      // ensure log can be fully read including the new record
      cursor = logFile.getCursor("key05");
      assertThatCursorCanBeFullyRead(cursor, 5, 11);
    }
    finally
    {
      StaticUtils.close(logFile);
    }
  }

  /**
   * Append some raw data to the TEST_LOG_FILE. Intended to corrupt the log
   * file.
   */
  private void corruptTestLogFile(ByteStringBuilder corruptedRecordData) throws Exception
  {
    RandomAccessFile output = null;
    try {
      output = new RandomAccessFile(TEST_LOG_FILE, "rwd");
      output.seek(output.length());
      output.write(corruptedRecordData.toByteArray());
    }
    finally
    {
      StaticUtils.close(output);
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
        writeLog.append(record);
        assertThat(writeLog.getNewestRecord()).as("write changelog " + i).isEqualTo(record);
        assertThat(writeLog.getOldestRecord()).as("write changelog " + i).isEqualTo(Record.from("key01", "value1"));
        assertThat(readLog.getNewestRecord()).as("read changelog " + i).isEqualTo(record);
        assertThat(readLog.getOldestRecord()).as("read changelog " + i).isEqualTo(Record.from("key01", "value1"));
      }
    }
    finally
    {
      StaticUtils.close(writeLog, readLog);
    }
  }

  /**
   * Read the cursor until exhaustion, ensuring that its first value is fromIndex and its last value
   * endIndex, using (keyN, valueN) where N is the index.
   */
  private void assertThatCursorCanBeFullyRead(DBCursor<Record<String, String>> cursor, int fromIndex, int endIndex)
      throws Exception
  {
    assertThat(cursor.getRecord()).isNull();
    for (int i = fromIndex; i <= endIndex; i++)
    {
      assertThat(cursor.next()).as("next() value when i=" + i).isTrue();
      assertThat(cursor.getRecord()).isEqualTo(Record.from(String.format("key%02d", i), "value" + i));
    }
    assertThatCursorIsExhausted(cursor);
  }

  private void assertThatCursorIsExhausted(DBCursor<Record<String, String>> cursor) throws Exception
  {
    assertThat(cursor.next()).isFalse();
    assertThat(cursor.getRecord()).isNull();
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

    public ByteString encodeRecord(Record<String, String> record)
    {
      return new ByteStringBuilder()
        .append(record.getKey()).append(STRING_SEPARATOR)
        .append(record.getValue()).append(STRING_SEPARATOR).toByteString();
    }

    @Override
    public String decodeKeyFromString(String key) throws ChangelogException
    {
      return key;
    }

    @Override
    public String encodeKeyToString(String key)
    {
      return key;
    }

    /** {@inheritDoc} */
    @Override
    public String getMaxKey()
    {
      // '~' character has the highest ASCII value
      return "~~~~";
    }
  }

  /** A parser that can be set to fail when reading. */
  static class FailingStringRecordParser extends StringRecordParser
  {
    private boolean failToRead = false;

    @Override
    public Record<String, String> decodeRecord(ByteString data) throws DecodingException
    {
      if (failToRead)
      {
        throw new DecodingException(Message.raw("Error when parsing record"));
      }
      return super.decodeRecord(data);
    }

    void setFailToRead(boolean shouldFail)
    {
      failToRead = shouldFail;
    }
  }

}
