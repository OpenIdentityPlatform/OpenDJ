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
package org.opends.quicksetup.installer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opends.messages.QuickSetupMessages.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.TempLogFile;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.PlainTextProgressMessageFormatter;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests what a failed installation tells its listeners about the log file.
 * <p>
 * This report is the one place the diagnosis of a failed setup lives, and it used to promise
 * the file without looking at it - printing a {@code NoSuchFileException} stack when the file
 * was gone (issue #1030). The roads below are the whole of that decision.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "quicksetup" }, sequential = true)
public class InstallerTest extends DirectoryServerTestCase
{
  private static final String PREFIX = "opendj-setup-";
  /** Marks a line the report sends out as a warning, see {@link MarkedArmsFormatter}. */
  private static final String WARNING = "[W]";
  /** Marks a line the report sends out as progress. */
  private static final String PROGRESS = "[P]";

  private File tempDir;
  private final List<TempLogFile> created = new ArrayList<>();

  @BeforeClass
  public void setUp() throws IOException
  {
    tempDir = TestCaseUtils.createTemporaryDirectory("installerTest");
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

  /** A log that is there is named, as before, and named as progress rather than as a warning. */
  @Test
  public void testAReadableLogIsHandedOver() throws Exception
  {
    final TempLogFile logFile = newLogFile();

    final String report = reportOf(logFile);

    assertContains(report, PROGRESS, INFO_GENERAL_PROVIDE_LOG_IN_ERROR.get(logFile.getPath()));
    assertDoesNotContain(report, INFO_GENERAL_LOG_IN_ERROR_MISSING.get(logFile.getPath()));
  }

  /**
   * The contents of the log reach the report: attaching the file is up to whoever reads it,
   * so the report carries the log itself.
   * <p>
   * What the file holds is not the point here and is mocked away - with
   * {@code OPENDJ_LOG_TO_STDOUT} set, a real log is empty and would pin nothing.
   */
  @Test
  public void testTheContentsOfTheLogReachTheReport() throws Exception
  {
    final String contents = "QuickSetup application launched\nthe last line before the failure\n";
    final TempLogFile logFile = mock(TempLogFile.class);
    when(logFile.isEnabled()).thenReturn(true);
    when(logFile.isReadable()).thenReturn(true);
    when(logFile.getPath()).thenReturn(new File(tempDir, "readable.log").getAbsolutePath());
    when(logFile.readContents()).thenReturn(contents);

    final String report = reportOf(logFile);

    assertTrue(report.contains(contents), report);
  }

  /** A log something else removed is reported as gone, instead of being asked for. */
  @Test
  public void testAMissingLogIsReportedAsMissing() throws Exception
  {
    final TempLogFile logFile = newLogFile();
    final String path = logFile.getPath();
    logFile.deleteLogFileAfterSuccess();
    assertFalse(logFile.isReadable());

    final String report = reportOf(logFile);

    assertContains(report, WARNING, INFO_GENERAL_LOG_IN_ERROR_MISSING.get(path));
    // The line that asks for the file must not go out when there is no file to provide.
    assertDoesNotContain(report, INFO_GENERAL_PROVIDE_LOG_IN_ERROR.get(path));
  }

  /** A log which is there but cannot be read costs the report its contents, not its diagnosis. */
  @Test
  public void testAnUnreadableLogIsReportedAsUnreadable() throws Exception
  {
    final String path = new File(tempDir, "unreadable.log").getAbsolutePath();
    final IOException failure = new IOException("Input/output error");
    final TempLogFile logFile = mock(TempLogFile.class);
    when(logFile.isEnabled()).thenReturn(true);
    when(logFile.isReadable()).thenReturn(true);
    when(logFile.getPath()).thenReturn(path);
    when(logFile.readContents()).thenThrow(failure);

    final String report = reportOf(logFile);

    assertContains(report, WARNING, INFO_GENERAL_LOG_IN_ERROR_UNREADABLE.get(path, failure));
    assertDoesNotContain(report, INFO_GENERAL_LOG_IN_ERROR_MISSING.get(path));
  }

  /** Nothing is said about a log that was never created. */
  @Test
  public void testNoLogMeansNoReport() throws Exception
  {
    final TempLogFile logFile = mock(TempLogFile.class);
    when(logFile.isEnabled()).thenReturn(false);

    assertTrue(reportOf(logFile).isEmpty(), "a launcher without a log has nothing to report");
  }

  /**
   * A cancelled install takes its log with it.
   * <p>
   * Nothing names the log on that road - the report belongs to the failure road - and the
   * cancel has just taken the installation back, so a log kept there is a report nobody is
   * ever pointed at (issue #1030).
   */
  @Test
  public void testACancelledInstallTakesItsLogWithIt() throws Exception
  {
    final TempLogFile logFile = newLogFile();
    final File instance = new File(tempDir, "cancelled");
    // A locks directory the lock file can be taken in: without it the cancel road reads the
    // server as running and goes off to stop it.
    assertTrue(new File(instance, "locks").mkdirs());

    // An installation under the temporary directory rather than the one the class path names:
    // the installer takes both paths from there, and in a test run there is none.
    final Installer installer = new Installer()
    {
      @Override
      public String getInstallationPath()
      {
        return instance.getAbsolutePath();
      }

      @Override
      public String getInstancePath()
      {
        return instance.getAbsolutePath();
      }
    };
    installer.setProgressMessageFormatter(new MarkedArmsFormatter());
    installer.setTempLogFile(logFile);
    installer.setUserData(new UserData());
    installer.cancel();

    installer.run();

    assertEquals(installer.getCurrentProgressStep(), InstallProgressStep.FINISHED_CANCELED);
    assertFalse(logFile.getLogFile().exists(), logFile.getPath());
  }

  private TempLogFile newLogFile()
  {
    final TempLogFile logFile = TempLogFile.newTempLogFile(PREFIX, new File(tempDir, "logs"));
    created.add(logFile);
    return logFile;
  }

  /** What the listeners of a failed installation are told about the log file. */
  private static String reportOf(final TempLogFile logFile)
  {
    final Installer installer = new Installer();
    installer.setProgressMessageFormatter(new MarkedArmsFormatter());
    final StringBuilder report = new StringBuilder();
    installer.addProgressUpdateListener(new ProgressUpdateListener()
    {
      @Override
      public void progressUpdate(final ProgressUpdateEvent ev)
      {
        if (ev.getNewLogs() != null)
        {
          report.append(ev.getNewLogs());
        }
      }
    });
    installer.setTempLogFile(logFile);
    installer.notifyListenersOfExistingLogFile();
    return report.toString();
  }

  private static void assertContains(final String report, final String arm, final LocalizableMessage expected)
  {
    assertTrue(report.contains(arm + expected), "expected <" + arm + expected + "> in <" + report + ">");
  }

  private static void assertDoesNotContain(final String report, final LocalizableMessage unexpected)
  {
    assertFalse(report.contains(unexpected.toString()), "unexpected <" + unexpected + "> in <" + report + ">");
  }

  /**
   * A formatter that marks the arm each line goes out on.
   * <p>
   * The plain text formatter returns warnings and progress messages unchanged - only the
   * wizard's HTML formatter tells them apart - so without a mark a report which says "the log
   * is gone" as an ordinary progress line reads exactly like one which warns about it.
   */
  private static final class MarkedArmsFormatter extends PlainTextProgressMessageFormatter
  {
    @Override
    public LocalizableMessage getFormattedWarning(final LocalizableMessage text, final boolean applyMargin)
    {
      return LocalizableMessage.raw(WARNING + super.getFormattedWarning(text, applyMargin));
    }

    @Override
    public LocalizableMessage getFormattedProgress(final LocalizableMessage text)
    {
      return LocalizableMessage.raw(PROGRESS + super.getFormattedProgress(text));
    }
  }
}
