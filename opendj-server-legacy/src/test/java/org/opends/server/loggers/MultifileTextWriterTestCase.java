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
package org.opends.server.loggers;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.FilePermission;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Tests the rotation of {@link MultifileTextWriter}, in particular when the rename fails. */
@SuppressWarnings("javadoc")
public class MultifileTextWriterTestCase extends DirectoryServerTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final DN PUBLISHER_DN = DN.valueOf("cn=Test Logger,cn=Loggers,cn=config");
  /** Small enough for two records to overflow it, large enough for one not to. */
  private static final long SIZE_LIMIT = 64;
  private static final String RECORD_A = "a".repeat(32);
  private static final String RECORD_B = "b".repeat(32);
  /** Long enough to leave the re-opened stream over the size limit together with the first record. */
  private static final String STAND_IN_WARNING =
      "stand-in for the permission warnings logged while the writer is re-opened";

  /** Naming policy with fixed names, so that the test does not depend on the current second. */
  private static final class FixedNamingPolicy implements FileNamingPolicy
  {
    private final File initialFile;
    private final File nextFile;

    private FixedNamingPolicy(File initialFile, File nextFile)
    {
      this.initialFile = initialFile;
      this.nextFile = nextFile;
    }

    @Override
    public File getInitialName()
    {
      return initialFile;
    }

    @Override
    public File getNextName()
    {
      return nextFile;
    }

    @Override
    public FilenameFilter getFilenameFilter()
    {
      return new FilenameFilter()
      {
        @Override
        public boolean accept(File dir, String name)
        {
          return name.equals(nextFile.getName());
        }
      };
    }

    @Override
    public File[] listFiles()
    {
      return new File[0];
    }
  }

  /**
   * A file which can run a hook from within constructWriter(): after a failed rotation the writer
   * is re-opened, and {@code FilePermission.setPermissions} checks {@code exists()} at the exact
   * point where the permission warnings are logged.
   */
  private static final class HookedFile extends File
  {
    private static final long serialVersionUID = 1L;
    private transient Runnable onExists;

    private HookedFile(File parent, String child)
    {
      super(parent, child);
    }

    @Override
    public boolean exists()
    {
      if (onExists != null)
      {
        onExists.run();
      }
      return super.exists();
    }
  }

  private File tempDir;
  private File logFile;
  private File rotatedFile;
  private MultifileTextWriter writer;

  @BeforeClass
  public void startServer() throws Exception
  {
    // The writer registers itself as a shutdown listener, which requires a bootstrapped server.
    TestCaseUtils.startServer();
  }

  @BeforeMethod
  public void setUp() throws Exception
  {
    tempDir = Files.createTempDirectory("MultifileTextWriterTestCase").toFile();
    logFile = new File(tempDir, "test.log");
    rotatedFile = new File(tempDir, "test.log.rotated");
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    if (writer != null)
    {
      writer.shutdown();
      writer = null;
    }
    deleteRecursively(tempDir);
  }

  /**
   * Makes the rename attempted by the rotation fail on every platform: renaming a file onto a
   * non-empty directory is refused both on POSIX systems and on Windows.
   */
  private void breakRotation() throws Exception
  {
    assertTrue(rotatedFile.mkdir());
    assertTrue(new File(rotatedFile, "blocker").createNewFile());
  }

  private MultifileTextWriter newWriter() throws Exception
  {
    MultifileTextWriter newWriter = new MultifileTextWriter("Multifile Text Writer for " + PUBLISHER_DN,
        Long.MAX_VALUE, new FixedNamingPolicy(logFile, rotatedFile), FilePermission.decodeUNIXMode("600"),
        new LogPublisherErrorHandler(PUBLISHER_DN), "UTF-8", true, true, 0);

    SizeLimitLogRotationPolicyCfg config = mock(SizeLimitLogRotationPolicyCfg.class);
    when(config.getFileSizeLimit()).thenReturn(SIZE_LIMIT);
    SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy();
    policy.initializeLogRotationPolicy(config);
    newWriter.addRotationPolicy(policy);

    // The rotater thread is deliberately not started: the rotations under test are the ones
    // triggered inline by writeRecord().
    return newWriter;
  }

  private List<String> linesOf(File file) throws Exception
  {
    return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
  }

  private static void deleteRecursively(File file)
  {
    if (file.isDirectory())
    {
      for (File child : file.listFiles())
      {
        deleteRecursively(child);
      }
    }
    file.delete();
  }

  @Test
  public void testSuccessfulRotationRenamesTheFile() throws Exception
  {
    writer = newWriter();

    writer.writeRecord(RECORD_A);
    writer.writeRecord(RECORD_B);
    writer.flush();

    assertEquals(writer.getTotalFilesRotated(), 1);
    assertEquals(linesOf(rotatedFile), List.of(RECORD_A));
    assertEquals(linesOf(logFile), List.of(RECORD_B));
  }

  @Test
  public void testFailedRotationAppendsInsteadOfTruncating() throws Exception
  {
    breakRotation();
    writer = newWriter();

    writer.writeRecord(RECORD_A);
    assertEquals(writer.getTotalFilesRotated(), 0);

    // Overflows the size limit, so a rotation is attempted and fails.
    writer.writeRecord(RECORD_B);
    writer.flush();

    assertEquals(writer.getTotalFilesRotated(), 0, "a failed rotation must not be counted");
    assertTrue(rotatedFile.isDirectory(), "the rotation target must have been left alone");
    assertEquals(linesOf(logFile), List.of(RECORD_A, RECORD_B),
        "the log file must have been appended to rather than truncated");
  }

  /**
   * A failed rotation of the file backing the error log used to come back into writeRecord() on the
   * same thread, from a writer which had just been closed and was still over the size limit, and to
   * recurse until the stack blew up.
   */
  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testFailedRotationOfTheErrorLogDoesNotRecurse() throws Exception
  {
    breakRotation();
    writer = newWriter();

    MultifileTextWriter publishing = writer;
    ErrorLogPublisher publisher = TextErrorLogPublisher.getServerStartupTextErrorPublisher(publishing);
    ErrorLogger.getInstance().addLogPublisher(publisher);
    try
    {
      publishing.writeRecord(RECORD_A);
      // Overflows the size limit: the rotation fails and reports the failure through the very
      // logger this writer is backing.
      publishing.writeRecord(RECORD_B);
      publishing.flush();
    }
    finally
    {
      // Removing the publisher closes it, which already shuts this writer down.
      ErrorLogger.getInstance().removeLogPublisher(publisher);
      writer = null;
    }

    assertEquals(publishing.getTotalFilesRotated(), 0);

    List<String> lines = linesOf(logFile);
    assertEquals(lines.get(0), RECORD_A, "the log file must have been appended to");
    assertTrue(lines.contains(RECORD_B), "the record which triggered the rotation must not be lost");
    assertTrue(lines.stream().anyMatch(line -> line.contains("rotating log file")),
        "the rotation failure must be reported in the log file, but it contained: " + lines);
  }

  /**
   * constructWriter() logs permission warnings of its own, between re-seeding the stream with the
   * over-limit file length and the point where the failed rotation used to set its latch. When the
   * writer backs the error log, such a warning used to re-enter writeRecord() and to recurse until
   * the stack blew up. The hook stands in for the permission warning, firing at the same point of
   * the re-open.
   */
  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void testWarningDuringReopenAfterFailedRotationDoesNotRecurse() throws Exception
  {
    breakRotation();
    HookedFile hookedLogFile = new HookedFile(tempDir, logFile.getName());
    logFile = hookedLogFile;
    writer = newWriter();

    MultifileTextWriter publishing = writer;
    ErrorLogPublisher publisher = TextErrorLogPublisher.getServerStartupTextErrorPublisher(publishing);
    ErrorLogger.getInstance().addLogPublisher(publisher);
    try
    {
      publishing.writeRecord(RECORD_A);
      hookedLogFile.onExists = () -> logger.warn(LocalizableMessage.raw(STAND_IN_WARNING));
      // Overflows the size limit: the rotation fails, and while the writer is re-opened the hook
      // logs through the very logger this writer is backing, like the permission warnings do.
      publishing.writeRecord(RECORD_B);
      publishing.flush();
    }
    finally
    {
      // Removing the publisher closes it, which already shuts this writer down.
      ErrorLogger.getInstance().removeLogPublisher(publisher);
      writer = null;
    }

    assertEquals(publishing.getTotalFilesRotated(), 0);

    List<String> lines = linesOf(logFile);
    assertEquals(lines.get(0), RECORD_A, "the log file must have been appended to");
    assertTrue(lines.stream().anyMatch(line -> line.contains(STAND_IN_WARNING)),
        "the warning logged during the re-open must not be lost, but the log file contained: " + lines);
    assertTrue(lines.contains(RECORD_B), "the record which triggered the rotation must not be lost");
    assertEquals(lines.stream().filter(line -> line.contains("rotating log file")).count(), 1L,
        "the rotation failure must be reported exactly once, but the log file contained: " + lines);
  }
}
