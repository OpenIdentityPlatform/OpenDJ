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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Entry;
import org.opends.server.types.OperatingSystem;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * A set of test cases for the ListBackends tool.
 */
public class ListBackendsTestCase
       extends ToolsTestCase
{
  // The path to the Directory Server configuration file.
  private String configFilePath;



  /**
   * Ensures that the Directory Server is running and gets the config file path.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    configFilePath = DirectoryServer.getServerRoot() + File.separator +
                     "config" + File.separator + "config.ldif";
  }



  /**
   * Retrieves sets of invalid arguments that may not be used to initialize
   * the ListBackends tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          ListBackends tool.
   */
  @DataProvider(name = "invalidArgs")
  public Object[][] getInvalidArgumentLists()
  {
    ArrayList<String[]> argLists   = new ArrayList<String[]>();
    ArrayList<String>   reasonList = new ArrayList<String>();

    String[] args = new String[] {};
    argLists.add(args);
    reasonList.add("No arguments");

    args = new String[]
    {
      "-c",
    };
    argLists.add(args);
    reasonList.add("No value for '-c' argument");

    args = new String[]
    {
      "-f",
    };
    argLists.add(args);
    reasonList.add("No value for '-f' argument");

    args = new String[]
    {
      "-n",
    };
    argLists.add(args);
    reasonList.add("No value for '-n' argument");

    args = new String[]
    {
      "-b",
    };
    argLists.add(args);
    reasonList.add("No value for '-b' argument");

    args = new String[]
    {
      "-I"
    };
    argLists.add(args);
    reasonList.add("Invalid short argument");

    args = new String[]
    {
      "--invalidLongArgument"
    };
    argLists.add(args);
    reasonList.add("Invalid long argument");

    args = new String[]
    {
      "--backendID", "nosuchbackend"
    };
    argLists.add(args);
    reasonList.add("No config file argument");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--backendID", "nosuchbackend"
    };
    argLists.add(args);
    reasonList.add("Invalid backend ID");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--baseDN", "invaliddn"
    };
    argLists.add(args);
    reasonList.add("Invalid base DN");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--backendID", "userRoot",
      "--baseDN", "dc=example,dc=com"
    };
    argLists.add(args);
    reasonList.add("Both backend ID and base DN");


    Object[][] returnArray = new Object[argLists.size()][2];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
      returnArray[i][1] = reasonList.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the ListBackends tool with sets of invalid arguments.
   *
   * @param  args           The set of arguments to use for the ListBackends
   *                        tool.
   * @param  invalidReason  The reason the provided set of arguments is invalid.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args, String invalidReason)
  {
    assertFalse((ListBackends.listBackends(args, false, null, null) == 0),
                "Should have been invalid because:  " + invalidReason);
  }



  /**
   * Tests the ListBackends tool with the no arguments.
   */
  @Test()
  public void testNoArguments()
  {
    String[] args =
    {
      "--configFile", configFilePath
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with one instance of the --backendID argument
   * and a valid backend ID.
   */
  @Test()
  public void testSingleBackendID()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--backendID", "userRoot"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with multiple instances of the --backendID
   * argument valid backend IDs.
   */
  @Test()
  public void testMultipleBackendIDs()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--backendID", "userRoot",
      "--backendID", "schema"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with multiple instances of the --backendID
   * argument in which one is valid and one is not.
   */
  @Test()
  public void testMultipleBackendIDsPartiallyValid()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--backendID", "userRoot",
      "--backendID", "invalid"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with multiple instances of the --backendID
   * argument in which all are invalid.
   */
  @Test()
  public void testMultipleBackendIDsAllInvalid()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--backendID", "invalid1",
      "--backendID", "invalid2"
    };

    assertFalse(ListBackends.listBackends(args, false, null, null) == 0);
  }



  /**
   * Tests the ListBackends tool with one instance of the --baseDN argument
   * and a valid DN that is a base DN.
   */
  @Test()
  public void testSingleBaseDN()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--baseDN", "dc=example,dc=com"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with one instance of the --baseDN argument
   * and a valid DN that is not a base DN but is directly below a valid base DN.
   */
  @Test()
  public void testSingleBaseDNBelowActualBaseDN()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--baseDN", "ou=notbase,dc=example,dc=com"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with one instance of the --baseDN argument
   * and a valid DN that is not a base DN but is two levels below a valid base
   * DN.
   */
  @Test()
  public void testSingleBaseDNTwoLevelsBelowActualBaseDN()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--baseDN", "ou=notbase,ou=alsonotbase,dc=example,dc=com"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with one instance of the --baseDN argument
   * and a valid DN that is not associated with any backend in the server
   */
  @Test()
  public void testSingleBaseDNNotBelowAnyBaseDN()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--baseDN", "ou=nonexistent"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with multiple instances of the "--baseDN"
   * argument with valid base DNs.
   */
  @Test()
  public void testMultipleBaseDNs()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--baseDN", "dc=example,dc=com",
      "--baseDN", "cn=schema"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }



  /**
   * Tests the ListBackends tool with the "--help" option.
   */
  @Test()
  public void testHelp()
  {
    String[] args =
    {
      "--help"
    };

    assertEquals(ListBackends.listBackends(args, false, null, null), 0);
  }
}

