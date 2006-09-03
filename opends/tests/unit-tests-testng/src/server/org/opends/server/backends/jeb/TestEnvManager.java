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

import static org.testng.AssertJUnit.assertTrue;

import java.io.File;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;

import org.opends.server.TestCaseUtils;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

/**
 * EnvManager Tester.
 */
public class TestEnvManager extends JebTestCase {
  private File tempDir;
  private String homeDirName;

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception {
    tempDir = TestCaseUtils.createTemporaryDirectory("jebtest");
    homeDirName = tempDir.getAbsolutePath();
  }

  /**
   * Tears down the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be finalized.
   */
  @AfterClass
  public void tearDown() throws Exception {
    TestCaseUtils.deleteDirectory(tempDir);
  }

  /**
   * Test for valid home directory.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test()
  public void testValidHomeDir() throws Exception {
    File homeDir = new File(homeDirName);

    EnvManager.createHomeDir(homeDirName);
    EnvironmentConfig envConfig = new EnvironmentConfig();
    envConfig.setTransactional(true);
    envConfig.setAllowCreate(true);
    Environment env = new Environment(new File(homeDirName), envConfig);
    env.close();

    assertTrue(homeDir.list().length > 0);

    EnvManager.removeFiles(homeDirName);

    assertTrue(homeDir.list().length == 0);
  }

  /**
   * Test for invalid home directory.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = JebException.class)
  public void testInvalidHomeDir() throws Exception {
    File tempFile = File.createTempFile("jebtest", "");
    tempFile.deleteOnExit();

    String invalidHomeDirName = tempFile.getAbsolutePath();

    EnvManager.createHomeDir(invalidHomeDirName);
  }

}
