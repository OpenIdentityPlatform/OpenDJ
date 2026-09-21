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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.quicksetup;

import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that a {@link TempLogFile} can be placed in a directory of the caller's choosing (the
 * instance {@code logs/} directory for setup, so that {@code start-ds} does not sweep it away
 * with the rest of {@code tmp/}, see issue #1030), and that it tells whether the file is still
 * there to be read.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "quicksetup" }, sequential = true)
public class TempLogFileTest extends DirectoryServerTestCase
{
  private static final String PREFIX = "opendj-setup-";

  private File tempDir;
  private final List<TempLogFile> created = new ArrayList<>();

  @BeforeClass
  public void setUp() throws IOException
  {
    tempDir = TestCaseUtils.createTemporaryDirectory("tempLogFileTest");
  }

  @AfterClass
  public void tearDown() throws IOException
  {
    for (TempLogFile logFile : created)
    {
      logFile.deleteLogFileAfterSuccess();
    }
    TestCaseUtils.deleteDirectory(tempDir);
  }

  private TempLogFile track(TempLogFile logFile)
  {
    created.add(logFile);
    return logFile;
  }

  private static File parentOf(TempLogFile logFile) throws IOException
  {
    return logFile.getLogFile().getCanonicalFile().getParentFile();
  }

  /** The directory does not exist before setup lays the instance down: it has to be created. */
  @Test
  public void testLogFileIsCreatedInTheRequestedDirectory() throws Exception
  {
    final File logs = new File(tempDir, "not-yet-laid-down/logs");
    assertFalse(logs.exists());

    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, logs));

    assertTrue(logFile.isEnabled());
    assertTrue(logFile.isReadable());
    assertEquals(parentOf(logFile), logs.getCanonicalFile());
    assertTrue(logFile.getLogFile().getName().startsWith(PREFIX), logFile.getPath());
    assertTrue(logFile.getLogFile().getName().endsWith(".log"), logFile.getPath());
  }

  @Test
  public void testReadContentsReturnsWhatIsInTheFile() throws Exception
  {
    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, new File(tempDir, "logs")));
    // The log's own stream is not in append mode and sits at the end of its own bytes, so a
    // record written after the marker would be written over it: shut the writer first, and
    // the marker is the last thing in the file whatever else the JVM logs.
    logFile.writer.shutdown();
    final String marker = "the last line written before the failure";
    Files.write(logFile.getLogFile().toPath(), (marker + "\n").getBytes(defaultCharset()), APPEND);

    assertTrue(logFile.readContents().endsWith(marker + "\n"));
  }

  /** Being enabled means messages are logged; being readable means the file is there to hand over. */
  @Test
  public void testIsReadableFollowsTheFileNotTheLogger() throws Exception
  {
    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, new File(tempDir, "logs")));
    assertTrue(logFile.isReadable());

    // Not File.delete(): the writer still holds the file, and Windows does not delete a file
    // that is open. deleteLogFileAfterSuccess() shuts the writer first, as setup does.
    logFile.deleteLogFileAfterSuccess();
    assertFalse(logFile.getLogFile().exists());

    assertTrue(logFile.isEnabled());
    assertFalse(logFile.isReadable());
    try
    {
      logFile.readContents();
      fail("reading a deleted log must fail");
    }
    catch (IOException expected)
    {
      // the caller reports it instead of promising the file
    }
  }

  /** A directory where the log was is not a log: there is nothing to hand over either. */
  @Test
  public void testADirectoryAtTheLogPathIsNotReadable() throws Exception
  {
    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, new File(tempDir, "logs")));
    logFile.deleteLogFileAfterSuccess();
    assertTrue(logFile.getLogFile().mkdir());

    assertTrue(Files.isReadable(logFile.getLogFile().toPath()));
    assertFalse(logFile.isReadable());
  }

  @Test
  public void testNoDirectoryMeansTheTemporaryDirectory() throws Exception
  {
    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, null));

    assertTrue(logFile.isEnabled());
    assertEquals(parentOf(logFile), new File(System.getProperty("java.io.tmpdir")).getCanonicalFile());
  }

  /** A directory that cannot be used must not cost the log: fall back to the temporary directory. */
  @Test
  public void testUnusableDirectoryFallsBackToTheTemporaryDirectory() throws Exception
  {
    final File notADirectory = new File(tempDir, "not-a-directory");
    assertTrue(notADirectory.createNewFile());

    final TempLogFile logFile = track(TempLogFile.newTempLogFile(PREFIX, notADirectory));

    assertTrue(logFile.isEnabled());
    assertTrue(logFile.isReadable());
    assertNotEquals(parentOf(logFile), notADirectory.getCanonicalFile());
    assertEquals(parentOf(logFile), new File(System.getProperty("java.io.tmpdir")).getCanonicalFile());
  }
}
