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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.LDIFConnectionHandler class.
 */
public class LDIFConnectionHandlerTestCase
       extends DirectoryServerTestCase
{
  /**
   * Ensures that the server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the ability of the LDIF connection handler to process a valid LDIF
   * file containing user data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testValidUserLDIF()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    File tempDir = TestCaseUtils.createTemporaryDirectory("testValidUserLDIF");

    TestCaseUtils.dsconfig(
      "set-connection-handler-prop",
      "--handler-name", "LDIF Connection Handler",
      "--set", "ldif-directory:" + tempDir.getAbsolutePath());

    try
    {
      String path = TestCaseUtils.createTempFile(
        "dn: o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: organization",
        "o: test",
        "",
        "dn: cn=test,o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: device",
        "objectClass: extensibleObject",
        "cn: test",
        "",
        "dn: cn=test,o=test",
        "changetype: modify",
        "replace: description",
        "description: foo",
        "-",
        "add: givenName",
        "givenName: bar",
        "",
        "dn: cn=test,o=test",
        "changetype: moddn",
        "newrdn: uid=test",
        "deleteoldrdn: 0",
        "",
        "dn: uid=test,o=test",
        "changetype: delete");

      File tempFile = new File(path);
      File newFile = new File(tempDir, "testValidUser.ldif");
      assertTrue(tempFile.renameTo(newFile));

      boolean found = false;
      long stopTime  = System.currentTimeMillis() + 10000L;
      while (System.currentTimeMillis() < stopTime)
      {
        if (! newFile.exists())
        {
          // The file should have been processed.  Make sure that there is
          // a new file with the appropriate prefix.
          for (File f : tempDir.listFiles())
          {
            found = f.getName().startsWith("testValidUser.ldif.applied.");
            if (! found)
            {
              System.err.println("Contents of file " + f.getName() + ":");
              BufferedReader reader = new BufferedReader(new FileReader(f));
              while (true)
              {
                String line = reader.readLine();
                if (line == null)
                {
                  break;
                }

                System.err.println(line);
              }

              throw new AssertionError("Found unexpected file " + f.getName() +
                             " instead of testValidUser.ldif.applied.*");
            }
          }

          break;
        }

        Thread.sleep(10);
      }

      assertTrue(found);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tempDir);
    }
  }



  /**
   * Tests the ability of the LDIF connection handler to process a valid LDIF
   * file containing configuration changes
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testValidConfigLDIF()
         throws Exception
  {
    assertEquals(DirectoryServer.getSizeLimit(), 1000);
    TestCaseUtils.initializeTestBackend(false);

    File tempDir =
         TestCaseUtils.createTemporaryDirectory("testValidConfigLDIF");

    TestCaseUtils.dsconfig(
      "set-connection-handler-prop",
      "--handler-name", "LDIF Connection Handler",
      "--set", "ldif-directory:" + tempDir.getAbsolutePath());

    try
    {
      String path = TestCaseUtils.createTempFile(
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-size-limit",
        "ds-cfg-size-limit: 100");

      File tempFile = new File(path);
      File newFile = new File(tempDir, "testValidConfig.ldif");
      assertTrue(tempFile.renameTo(newFile));

      boolean found = false;
      long stopTime  = System.currentTimeMillis() + 10000L;
      while (System.currentTimeMillis() < stopTime)
      {
        if (! newFile.exists())
        {
          // The file should have been processed.  Make sure that there is
          // a new file with the appropriate prefix.
          for (File f : tempDir.listFiles())
          {
            found = f.getName().startsWith("testValidConfig.ldif.applied.");
            if (! found)
            {
              System.err.println("Contents of file " + f.getName() + ":");
              BufferedReader reader = new BufferedReader(new FileReader(f));
              while (true)
              {
                String line = reader.readLine();
                if (line == null)
                {
                  break;
                }

                System.err.println(line);
              }

              throw new AssertionError("Found unexpected file " + f.getName() +
                             " instead of testValidConfig.ldif.applied.*");
            }
          }

          break;
        }

        Thread.sleep(10);
      }

      assertTrue(found);
      assertEquals(DirectoryServer.getSizeLimit(), 100);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tempDir);

      TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "size-limit:1000");
      assertEquals(DirectoryServer.getSizeLimit(), 1000);
    }
  }



  /**
   * Tests the ability of the LDIF connection handler to properly deal with an
   * unparseable LDIF file.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testUnparseableLDIF()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    File tempDir = TestCaseUtils.createTemporaryDirectory("testValidUserLDIF");

    TestCaseUtils.dsconfig(
      "set-connection-handler-prop",
      "--handler-name", "LDIF Connection Handler",
      "--set", "ldif-directory:" + tempDir.getAbsolutePath());

    try
    {
      String path = TestCaseUtils.createTempFile(
        "unparseable");

      File tempFile = new File(path);
      File newFile = new File(tempDir, "testUnparseable.ldif");
      assertTrue(tempFile.renameTo(newFile));

      boolean appliedFound = false;
      boolean errorsFound  = false;

      long stopTime  = System.currentTimeMillis() + 10000L;
      while (System.currentTimeMillis() < stopTime)
      {
        if (! newFile.exists())
        {
          // The file should have been processed.  Make sure that there is an
          // applied file, and that there is an errors file.
          for (File f : tempDir.listFiles())
          {
            if (f.getName().startsWith("testUnparseable.ldif.applied."))
            {
              appliedFound = true;
            }
            else if (f.getName().startsWith(
                          "testUnparseable.ldif.errors-encountered."))
            {
              errorsFound = true;
            }
            else
            {
              System.err.println("Contents of file " + f.getName() + ":");
              BufferedReader reader = new BufferedReader(new FileReader(f));
              while (true)
              {
                String line = reader.readLine();
                if (line == null)
                {
                  break;
                }

                System.err.println(line);
              }

              throw new AssertionError("Found unexpected file " + f.getName());
            }
          }

          break;
        }

        Thread.sleep(10);
      }

      assertFalse(newFile.exists());
      assertTrue(appliedFound);
      assertTrue(errorsFound);
    }
    finally
    {
      TestCaseUtils.deleteDirectory(tempDir);
    }
  }



  /**
   * Tests a number of methods that are part of the generic connection handler
   * API.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGenericConnectionHandlerMethods()
         throws Exception
  {
    // Get the connection handler from the server.
    LDIFConnectionHandler connHandler = null;
    for (ConnectionHandler handler : DirectoryServer.getConnectionHandlers())
    {
      if (handler instanceof LDIFConnectionHandler)
      {
        assertNull(connHandler);
        connHandler = (LDIFConnectionHandler) handler;
      }
    }
    assertNotNull(connHandler);


    // Generic connection handler methods.
    assertNotNull(connHandler.getConnectionHandlerName());
    assertNotNull(connHandler.getProtocol());
    assertNotNull(connHandler.toString());

    assertNotNull(connHandler.getListeners());
    assertTrue(connHandler.getListeners().isEmpty());

    assertNotNull(connHandler.getClientConnections());
    assertTrue(connHandler.getClientConnections().isEmpty());


    // Alert handler methods.
    assertNotNull(connHandler.getComponentEntryDN());

    assertNotNull(connHandler.getClassName());
    assertEquals(connHandler.getClassName(), connHandler.getClass().getName());

    assertNotNull(connHandler.getAlerts());
    assertFalse(connHandler.getAlerts().isEmpty());
  }
}

