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
package org.opends.quicksetup.util;

import static com.forgerock.opendj.util.OperatingSystem.*;
import static org.opends.messages.QuickSetupMessages.INFO_ERROR_STARTING_SERVER_CODE;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.ReturnCode;
import org.opends.server.DirectoryServerTestCase;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A failed start reports what the start command printed (issue #1160), unless the listeners of
 * the application have already shown it. The installation is a bare directory whose start command
 * is a stand-in script that prints and exits with 1.
 */
@SuppressWarnings("javadoc")
@Test(sequential = true)
public class ServerControllerStartFailureTest extends DirectoryServerTestCase
{
  private File root;

  @BeforeMethod
  public void createInstallation() throws IOException
  {
    root = Files.createTempDirectory("server-controller-start-failure").toFile();
  }

  @AfterMethod(alwaysRun = true)
  public void deleteInstallation() throws ApplicationException
  {
    new FileManager().deleteRecursively(root);
  }

  @Test
  public void failedStartReportsBothOutputStreams() throws Exception
  {
    writeStartCommandPrintingToBothStreams();

    String message = startAndExpectFailure(null, false);

    assertTrue(message.startsWith(exitCodeMessage()), message);
    assertTrue(message.contains("cause on stdout"), message);
    assertTrue(message.contains("cause on stderr"), message);
  }

  @Test
  public void failedStartReportsOnlyTheLastLines() throws Exception
  {
    if (isWindows())
    {
      writeStartCommand("@for /L %%i in (1,1,150) do @echo L%%iL", "@exit /b 1");
    }
    else
    {
      writeStartCommand("#!/bin/sh", "i=1", "while [ $i -le 150 ]; do echo \"L${i}L\"; i=$((i+1)); done", "exit 1");
    }

    String message = startAndExpectFailure(null, false);

    assertFalse(message.contains("L50L"), message);
    assertTrue(message.contains("L51L"), message);
    assertTrue(message.contains("L150L"), message);
  }

  @Test
  public void failedStartReportsWhatIsPrintedAfterTheExit() throws Exception
  {
    if (isWindows())
    {
      throw new SkipException("needs a child process that outlives the start command");
    }
    // The child holds the inherited pipes after sh exits, so its line arrives after waitFor().
    // When the process exits, the JDK drains what is already in a pipe and closes it, unless a
    // reader is blocked in read() on that stream, which the drain then waits for. The first
    // line and the pause before the exit leave the stdout reader blocked there, so that the
    // line of the child is read rather than cut off by the drain.
    writeStartCommand("#!/bin/sh", "echo 'printed before the exit'", "sleep 1",
        "(sleep 1; echo 'printed after the exit') &", "exit 1");

    String message = startAndExpectFailure(null, false);

    assertTrue(message.contains("printed before the exit"), message);
    assertTrue(message.contains("printed after the exit"), message);
  }

  @Test
  public void silentFailedStartKeepsTheExitCodeMessage() throws Exception
  {
    if (isWindows())
    {
      writeStartCommand("@exit /b 1");
    }
    else
    {
      writeStartCommand("#!/bin/sh", "exit 1");
    }

    assertEquals(startAndExpectFailure(null, false), exitCodeMessage());
  }

  @Test
  public void failedStartShownToTheListenersKeepsTheExitCodeMessage() throws Exception
  {
    writeStartCommandPrintingToBothStreams();
    ListeningApplication application = new ListeningApplication();

    assertEquals(startAndExpectFailure(application, false), exitCodeMessage());
    assertTrue(application.getShown().contains("cause on stdout"), application.getShown());
    assertTrue(application.getShown().contains("cause on stderr"), application.getShown());
  }

  @Test
  public void failedStartHiddenFromTheListenersReportsBothOutputStreams() throws Exception
  {
    writeStartCommandPrintingToBothStreams();
    ListeningApplication application = new ListeningApplication();

    String message = startAndExpectFailure(application, true);

    assertTrue(message.startsWith(exitCodeMessage()), message);
    assertTrue(message.contains("cause on stdout"), message);
    assertTrue(message.contains("cause on stderr"), message);
    assertEquals(application.getShown(), "");
  }

  private static String exitCodeMessage()
  {
    return INFO_ERROR_STARTING_SERVER_CODE.get(1).toString();
  }

  private void writeStartCommandPrintingToBothStreams() throws IOException
  {
    if (isWindows())
    {
      writeStartCommand("@echo cause on stdout", "@echo cause on stderr 1>&2", "@exit /b 1");
    }
    else
    {
      writeStartCommand("#!/bin/sh", "echo 'cause on stdout'", "echo 'cause on stderr' >&2", "exit 1");
    }
  }

  private void writeStartCommand(String... lines) throws IOException
  {
    Installation installation = new Installation(root, root);
    File command = installation.getServerStartCommandFile();
    assertTrue(command.getParentFile().mkdirs());
    Files.write(command.toPath(), String.join(isWindows() ? "\r\n" : "\n", lines).concat("\n")
        .getBytes(StandardCharsets.US_ASCII));
    assertTrue(command.setExecutable(true));
  }

  private String startAndExpectFailure(Application application, boolean suppressOutput)
  {
    try
    {
      new ServerController(application, new Installation(root, root)).startServer(suppressOutput);
      fail("The start command exited with 1, but the start did not fail");
      return null;
    }
    catch (ApplicationException e)
    {
      assertEquals(e.getType(), ReturnCode.START_ERROR);
      return e.getMessage();
    }
  }

  /** An application whose listener keeps the log details it is shown. */
  private static final class ListeningApplication extends Application
  {
    private final StringBuilder shown = new StringBuilder();

    ListeningApplication()
    {
      setProgressMessageFormatter(new PlainTextProgressMessageFormatter());
      addProgressUpdateListener(ev ->
      {
        synchronized (shown)
        {
          shown.append(ev.getNewLogs());
        }
      });
    }

    String getShown()
    {
      synchronized (shown)
      {
        return shown.toString();
      }
    }

    @Override
    public String getInstallationPath()
    {
      return null;
    }

    @Override
    public String getInstancePath()
    {
      return null;
    }

    @Override
    public ProgressStep getCurrentProgressStep()
    {
      return null;
    }

    @Override
    public Integer getRatio(ProgressStep step)
    {
      return null;
    }

    @Override
    public LocalizableMessage getSummary(ProgressStep step)
    {
      return null;
    }

    @Override
    public boolean isFinished()
    {
      return false;
    }

    @Override
    public boolean isCancellable()
    {
      return false;
    }

    @Override
    public void cancel()
    {
      // Nothing to cancel.
    }

    @Override
    public void run()
    {
      // Never run: the test drives the ServerController directly.
    }
  }
}
