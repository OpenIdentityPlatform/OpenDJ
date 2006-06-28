/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.DirectoryServerTestCase;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Environment;

import java.io.File;

import junit.framework.AssertionFailedError;

/**
 * EnvManager Tester.
 *
 * @author Andy Coulbeck
 */
public class TestEnvManager extends DirectoryServerTestCase
{
  private String homeDirName;

  public TestEnvManager(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();

    File tempFile = File.createTempFile("jebtest", "");
    tempFile.delete();
    homeDirName = tempFile.getAbsolutePath();
  }

  public void tearDown() throws Exception
  {
    super.tearDown();
    File homeDir = new File(homeDirName);
    homeDir.delete();
  }

  public void testValidHomeDir() throws Exception
  {
    File homeDir = new File(homeDirName);

    EnvManager.createHomeDir(homeDirName);
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setTransactional(true);
    envConfig.setAllowCreate(true);
    Environment env = new Environment(new File(homeDirName), envConfig);
    env.close();

    try
    {
      assertTrue(homeDir.list().length > 0);
    }
    catch (AssertionFailedError e)
    {
      printError("No files created in homedir " + homeDirName +
                 " during environment open");
    }

    EnvManager.removeFiles(homeDirName);

    try
    {
      assertTrue(homeDir.list().length == 0);
    }
    catch (AssertionFailedError e)
    {
      printError("Files remaining in homedir " + homeDirName + " after removal");
    }
  }

  public void testInvalidHomeDir() throws Exception
  {
    File tempFile = File.createTempFile("jebtest", "");
    tempFile.deleteOnExit();

    String invalidHomeDirName = tempFile.getAbsolutePath();

    boolean exceptionCaught = false;
    try
    {
      EnvManager.createHomeDir(invalidHomeDirName);
    }
    catch (JebException e)
    {
      exceptionCaught = true;
    }

    try
    {
      assertTrue(exceptionCaught);
    }
    catch (AssertionFailedError e)
    {
      printError("Unexpected successful creation of invalid home directory");
    }

  }

}
