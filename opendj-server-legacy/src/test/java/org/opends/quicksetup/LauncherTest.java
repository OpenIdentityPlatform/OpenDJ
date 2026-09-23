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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ArgumentParser;

/**
 * Tests when a launcher creates its temporary log file.
 * <p>
 * Nothing removes that file unless the operation it belongs to succeeds, so a road which
 * attempts nothing - {@code setup --help}, {@code --version}, a usage error - must not create
 * one, nor the directory it would live in (issue #1030).
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "quicksetup" }, sequential = true)
public class LauncherTest extends DirectoryServerTestCase
{
  private static final String PREFIX = "opendj-setup-";

  private File tempDir;
  private final List<TempLogFile> created = new ArrayList<>();

  @BeforeClass
  public void setUp() throws IOException
  {
    tempDir = TestCaseUtils.createTemporaryDirectory("launcherTest");
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

  @Test
  public void testBuildingALauncherLeavesNothingOnDisk() throws Exception
  {
    final File instance = new File(tempDir, "not-yet-laid-down");
    final File logs = new File(instance, "logs");

    final TestLauncher launcher = new TestLauncher(logs);

    assertFalse(launcher.hasTempLogFile(), "the log must wait for a road that can fail");
    assertFalse(logs.exists(), logs.getPath());
    assertFalse(instance.exists(), instance.getPath());
  }

  @Test
  public void testAskingForTheLogCreatesItOnce() throws Exception
  {
    final File logs = new File(tempDir, "asked-for/logs");
    final TestLauncher launcher = new TestLauncher(logs);

    final TempLogFile logFile = launcher.getTempLogFile();
    created.add(logFile);

    assertTrue(logFile.isReadable(), logFile.getPath());
    assertEquals(logFile.getLogFile().getCanonicalFile().getParentFile(), logs.getCanonicalFile());
    assertTrue(launcher.hasTempLogFile());
    assertSame(launcher.getTempLogFile(), logFile, "a second ask must not create a second log");
  }

  /**
   * The wizard road: the splash screen comes up before the user has said anything, so the log
   * cannot be created on the way to it - it is the application that asks for one, when it
   * starts the install. A wizard quit at any step leaves nothing behind.
   */
  @Test
  public void testLaunchingTheGuiCreatesNoLog() throws Exception
  {
    final File instance = new File(tempDir, "quit-at-the-first-step");
    final File logs = new File(instance, "logs");
    final TestLauncher launcher = new TestLauncher(logs);

    // The wizard behind the splash screen quits without installing anything.
    launcher.launchGui(new String[0]);

    assertFalse(launcher.hasTempLogFile(), "the splash screen must not cost a log");
    assertFalse(logs.exists(), logs.getPath());
    assertFalse(instance.exists(), instance.getPath());

    // What the wizard was handed is the launcher's own log, made on the first ask.
    final TempLogFile logFile = launcher.splashLogFile.get();
    created.add(logFile);
    assertTrue(logFile.isReadable(), logFile.getPath());
    assertSame(logFile, launcher.getTempLogFile());
  }

  /** A launcher with nothing in it but the log file behaviour under test. */
  private static final class TestLauncher extends Launcher
  {
    /** What {@link Launcher#launchGui(String[])} handed the splash screen. */
    private Supplier<TempLogFile> splashLogFile;

    TestLauncher(final File tempLogFileDirectory)
    {
      super(new String[0], PREFIX, tempLogFileDirectory);
    }

    @Override
    void startSplashScreen(final Supplier<TempLogFile> tempLogFile, final String[] args)
    {
      // No display here, and no install either: the wizard is quit at its first step.
      splashLogFile = tempLogFile;
    }

    @Override
    public ArgumentParser getArgumentParser()
    {
      return null;
    }

    @Override
    protected LocalizableMessage getFrameTitle()
    {
      return LocalizableMessage.raw("test");
    }

    @Override
    protected CliApplication createCliApplication()
    {
      return null;
    }

    @Override
    protected void willLaunchGui()
    {
      // nothing is launched here
    }

    @Override
    protected void guiLaunchFailed()
    {
      // nothing is launched here
    }
  }
}
