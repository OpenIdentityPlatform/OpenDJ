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
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.opends.quicksetup.ApplicationException;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ReturnCode;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A failed start reports what the start command printed (issue #1160). The installation is a
 * bare directory whose start command is a stand-in script that prints and exits with 1.
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
    if (isWindows())
    {
      writeStartCommand("@echo cause on stdout", "@echo cause on stderr 1>&2", "@exit /b 1");
    }
    else
    {
      writeStartCommand("#!/bin/sh", "echo 'cause on stdout'", "echo 'cause on stderr' >&2", "exit 1");
    }

    String message = startAndExpectFailure();

    assertTrue(message.contains("Error code: 1."), message);
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

    String message = startAndExpectFailure();

    assertFalse(message.contains("L50L"), message);
    assertTrue(message.contains("L51L"), message);
    assertTrue(message.contains("L150L"), message);
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

    assertEquals(startAndExpectFailure(), "Error Starting Directory Server. Error code: 1.");
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

  private String startAndExpectFailure()
  {
    try
    {
      new ServerController(new Installation(root, root)).startServer();
      fail("The start command exited with 1, but the start did not fail");
      return null;
    }
    catch (ApplicationException e)
    {
      assertEquals(e.getType(), ReturnCode.START_ERROR);
      return e.getMessage();
    }
  }
}
