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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.File;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPResultCode;

import static org.testng.Assert.*;




/**
 * A set of test cases for the EncodePassword tool.
 */
public class EncodePasswordTestCase
       extends ToolsTestCase
{
  // The path to the Directory Server configuration file.
  private String configFilePath;

  // The path to the temporary file containing a clear-text password.
  private String passwordFilePath;

  // The path to the temporary file containing an encoded password.
  private String encodedPasswordFilePath;



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

    configFilePath = DirectoryServer.getInstanceRoot() + File.separator +
                     "config" + File.separator + "config.ldif";

    passwordFilePath = TestCaseUtils.createTempFile("password");

    encodedPasswordFilePath =
         TestCaseUtils.createTempFile("{SHA}C5wmJdwh7wX2rU3fR8XyA4N6oyw=");
  }



  /**
   * Retrieves sets of invalid arguments that may not be used to initialize
   * the EncodePassword tool.
   *
   * @return  Sets of invalid arguments that may not be used to initialize the
   *          EncodePassword tool.
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
      "-C",
    };
    argLists.add(args);
    reasonList.add("No value for '-C' argument");

    args = new String[]
    {
      "-f",
    };
    argLists.add(args);
    reasonList.add("No value for '-f' argument");

    args = new String[]
    {
      "-c",
    };
    argLists.add(args);
    reasonList.add("No value for '-c' argument");

    args = new String[]
    {
      "-F",
    };
    argLists.add(args);
    reasonList.add("No value for '-F' argument");

    args = new String[]
    {
      "-e",
    };
    argLists.add(args);
    reasonList.add("No value for '-e' argument");

    args = new String[]
    {
      "-E",
    };
    argLists.add(args);
    reasonList.add("No value for '-E' argument");

    args = new String[]
    {
      "-s",
    };
    argLists.add(args);
    reasonList.add("No value for '-s' argument");

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
      "--configFile", configFilePath
    };
    argLists.add(args);
    reasonList.add("No clear password");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--clearPassword", "password"
    };
    argLists.add(args);
    reasonList.add("No storage scheme or encoded password");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--clearPasswordFile", passwordFilePath,
      "--storageScheme", "SSHA"
    };
    argLists.add(args);
    reasonList.add("Both clear password and clear password file");

    args = new String[]
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "password",
      "--encodedPasswordFile", passwordFilePath
    };
    argLists.add(args);
    reasonList.add("Both encoded password and encoded password file");


    Object[][] returnArray = new Object[argLists.size()][2];
    for (int i=0; i < argLists.size(); i++)
    {
      returnArray[i][0] = argLists.get(i);
      returnArray[i][1] = reasonList.get(i);
    }
    return returnArray;
  }



  /**
   * Tests the EncodePassword tool with sets of invalid arguments.
   *
   * @param  args           The set of arguments to use for the ListBackends
   *                        tool.
   * @param  invalidReason  The reason the provided set of arguments is invalid.
   */
  @Test(dataProvider = "invalidArgs")
  public void testInvalidArguments(String[] args, String invalidReason)
  {
    assertFalse((EncodePassword.encodePassword(args, false, null, null) == 0),
                "Should have been invalid because:  " + invalidReason);
  }



  /**
   * Tests the EncodePassword tool with the --listSchemes argument.
   */
  @Test()
  public void testListSchemes()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--listSchemes"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --listSchemes and
   * --authPasswordSyntax arguments.
   */
  @Test()
  public void testListAuthSchemes()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--listSchemes",
      "--authPasswordSyntax"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPassword and --storageScheme
   * arguments.
   */
  @Test()
  public void testEncodeClearPassword()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "SSHA"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPassword, --storageScheme,
   * and --authPasswordSyntax arguments.
   */
  @Test()
  public void testEncodeClearAuthPassword()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "SHA1",
      "--authPasswordSyntax"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPasswordFile and
   * --storageScheme arguments.
   */
  @Test()
  public void testEncodeClearPasswordFromFile()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPasswordFile", passwordFilePath,
      "--storageScheme", "SSHA"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPasswordFile, --storageScheme,
   * and --authPasswordSyntax arguments.
   */
  @Test()
  public void testEncodeClearAuthPasswordFromFile()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPasswordFile", passwordFilePath,
      "--storageScheme", "SHA1",
      "--authPasswordSyntax"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPassword and --storageScheme
   * arguments using an invalid storage scheme.
   */
  @Test()
  public void testEncodeClearPasswordWithInvalidScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "invalid"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPassword, --storageScheme,
   * and --authPasswordSyntax arguments using an invalid storage scheme.
   */
  @Test()
  public void testEncodeClearPasswordWithInvalidAuthScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "invalid",
      "--authPasswordSyntax"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPasswordFile and
   * --storageScheme arguments using an a password file that doesn't exist.
   */
  @Test()
  public void testEncodeClearPasswordFromMissingFile()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPasswordFile", passwordFilePath + ".missing",
      "--storageScheme", "invalid"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool with the --clearPasswordFile,
   * --storageScheme, and --authPasswordSyntax arguments using an a password
   * file that doesn't exist.
   */
  @Test()
  public void testEncodeClearAuthPasswordFromMissingFile()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPasswordFile", passwordFilePath + ".missing",
      "--storageScheme", "invalid",
      "--authPasswordSyntax"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching encoded password.
   */
  @Test()
  public void testCompareMatchingPasswordsNoScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "{CLEAR}password"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching encoded password.
   */
  @Test()
  public void testCompareMatchingPasswordsWithScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "CLEAR",
      "--encodedPassword", "password"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching encoded password.
   */
  @Test()
  public void testCompareMatchingEncodedPasswordsFromFile()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPasswordFile", passwordFilePath,
      "--encodedPasswordFile", encodedPasswordFilePath
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching auth password.
   */
  @Test()
  public void testCompareMatchingAuthPasswords()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "SHA1$dcKtMsOgc30=$MtHvXqXXJIRgxxw4xRXIY6ZLkQo=",
      "--authPasswordSyntax"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching encoded password.
   */
  @Test()
  public void testCompareNonMatchingPasswordsNoScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--encodedPassword", "{CLEAR}password"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching encoded password.
   */
  @Test()
  public void testCompareNonMatchingPasswordsWithScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--storageScheme", "CLEAR",
      "--encodedPassword", "password"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching auth password.
   */
  @Test()
  public void testCompareNonMatchingAuthPasswords()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--encodedPassword", "SHA1$dcKtMsOgc30=$MtHvXqXXJIRgxxw4xRXIY6ZLkQo=",
      "--authPasswordSyntax"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching encoded password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareMatchingPasswordsNoSchemeCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "{CLEAR}password",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching encoded password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareMatchingPasswordsWithSchemeCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "CLEAR",
      "--encodedPassword", "password",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a valid matching auth password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareMatchingAuthPasswordsCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "SHA1$dcKtMsOgc30=$MtHvXqXXJIRgxxw4xRXIY6ZLkQo=",
      "--authPasswordSyntax",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_TRUE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching encoded password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareNonMatchingPasswordsNoSchemeCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--encodedPassword", "{CLEAR}password",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_FALSE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching encoded password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareNonMatchingPasswordsWithSchemeCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--storageScheme", "CLEAR",
      "--encodedPassword", "password",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_FALSE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a non-matching auth password using the LDAP compare result as an
   * exit code.
   */
  @Test()
  public void testCompareNonMatchingAuthPasswordsCompareResult()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "wrongpassword",
      "--encodedPassword", "SHA1$dcKtMsOgc30=$MtHvXqXXJIRgxxw4xRXIY6ZLkQo=",
      "--authPasswordSyntax",
      "--useCompareResultCode"
    };

    assertEquals(EncodePassword.encodePassword(args, false, null, null),
                 LDAPResultCode.COMPARE_FALSE);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with a malformed encoded auth password.
   */
  @Test()
  public void testCompareInvalidEncodedAuthPassword()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "malformedencodedauthpassword",
      "--authPasswordSyntax"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with an encoded password that uses an unknown scheme.
   */
  @Test()
  public void testCompareEncodedPasswordWithUnknownScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "{UNKNOWN}unknownscheme"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with an encoded password that uses an unknown scheme.
   */
  @Test()
  public void testCompareEncodedPasswordWithUnknownSeparateScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--storageScheme", "unknown",
      "--encodedPassword", "unknownscheme"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool by performing a comparison of clear-text
   * with an encoded auth password that uses an unknown scheme.
   */
  @Test()
  public void testCompareEncodedAuthPasswordWithUnknownScheme()
  {
    String[] args =
    {
      "--configFile", configFilePath,
      "--clearPassword", "password",
      "--encodedPassword", "UNKNOWN$AUTH$SCHEME",
      "--authPasswordSyntax"
    };

    assertFalse(EncodePassword.encodePassword(args, false, null, null) == 0);
  }



  /**
   * Tests the EncodePassword tool with the help options.
   */
  @Test()
  public void testHelp()
  {
    String[] args = { "--help" };
    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);

    args = new String[] { "-H" };
    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);

    args = new String[] { "-?" };
    assertEquals(EncodePassword.encodePassword(args, false, null, null), 0);
  }
}

