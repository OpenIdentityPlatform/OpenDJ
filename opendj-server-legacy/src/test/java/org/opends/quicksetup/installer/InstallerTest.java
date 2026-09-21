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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.TempLogFile;
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

  /** A log that is there is named and written out, as before. */
  @Test
  public void testAReadableLogIsHandedOver() throws Exception
  {
    final TempLogFile logFile = newLogFile();

    final String report = reportOf(logFile);

    assertContains(report, INFO_GENERAL_PROVIDE_LOG_IN_ERROR.get(logFile.getPath()));
    // The line the constructor logs: the contents of the file reach the report.
    assertTrue(report.contains("QuickSetup application launched"), report);
    assertDoesNotContain(report, INFO_GENERAL_LOG_IN_ERROR_MISSING.get(logFile.getPath()));
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

    assertContains(report, INFO_GENERAL_LOG_IN_ERROR_MISSING.get(path));
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

    assertContains(report, INFO_GENERAL_LOG_IN_ERROR_UNREADABLE.get(path, failure));
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
    installer.setProgressMessageFormatter(new PlainTextProgressMessageFormatter());
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

  private static void assertContains(final String report, final LocalizableMessage expected)
  {
    assertTrue(report.contains(expected.toString()), "expected <" + expected + "> in <" + report + ">");
  }

  private static void assertDoesNotContain(final String report, final LocalizableMessage unexpected)
  {
    assertFalse(report.contains(unexpected.toString()), "unexpected <" + unexpected + "> in <" + report + ">");
  }
}
