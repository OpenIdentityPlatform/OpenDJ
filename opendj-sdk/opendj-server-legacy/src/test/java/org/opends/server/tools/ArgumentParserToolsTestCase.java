/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS.
 */
package org.opends.server.tools;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.cli.CliMessages.*;

import java.io.PrintStream;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.backends.jeb.DBTest;
import org.opends.server.tools.dsreplication.ReplicationCliMain;
import org.opends.server.tools.makeldif.MakeLDIF;
import org.opends.server.tools.status.StatusCli;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class tests that help reference message is prompted for all tools when
 * no arguments are provided or if they failed to be parsed.
 */
public class ArgumentParserToolsTestCase extends ToolsTestCase
{
  private static final boolean ERRORS_ON_STDOUT = false;

  private ByteStringBuilder out;
  private ByteStringBuilder err;
  private PrintStream outStream;
  private PrintStream errStream;

  @BeforeMethod
  private void refreshStreams()
  {
    out = new ByteStringBuilder();
    err = new ByteStringBuilder();
    outStream = new PrintStream(out.asOutputStream());
    errStream = new PrintStream(err.asOutputStream());
  }

  @AfterMethod
  private void validateAndCloseStreams()
  {
    closeSilently(outStream, errStream);
  }

  private void assertToolFailsWithUsage(final int returnCode)
  {
    assertToolFailsWithUsage(returnCode, true);
  }

  private void assertToolFailsWithUsage(final int returnCode, boolean errorsOnStdErr)
  {
    assertThat(returnCode).isNotEqualTo(0);
    assertThat((errorsOnStdErr ? out : err).toString()).isEmpty();
    final String streamToCheck = (errorsOnStdErr ? err : out).toString()
                                                             .replace(System.getProperty("line.separator"), " ");
    assertThat(streamToCheck).matches(".*" + INFO_GLOBAL_HELP_REFERENCE.get("(.*)") + ".*");
    assertThat(streamToCheck).contains(ERR_ERROR_PARSING_ARGS.get(""));
  }

  @DataProvider
  public Object[][] invalidArg() throws Exception
  {
    return new Object[][] { { new String[] { "-42" } } };
  }

  @DataProvider
  public Object[][] invalidArgs() throws Exception
  {
    return new Object[][] { { new String[] { } }, { new String[] { "-42" } } };
  }

  @Test(dataProvider = "invalidArgs")
  public void testBackup(final String[] args)
  {
    assertToolFailsWithUsage(BackUpDB.mainBackUpDB(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testCreateRCScript(final String[] args)
  {
    assertToolFailsWithUsage(CreateRCScript.main(args, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testDBTest(final String[] args)
  {
    assertToolFailsWithUsage(DBTest.main(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testDSJavaProperties(final String[] args)
  {
    assertToolFailsWithUsage(JavaPropertiesTool.mainCLI(args, outStream, errStream, null));
  }

  @Test(dataProvider = "invalidArgs")
  public void testDSReplication(final String[] args)
  {
    assertToolFailsWithUsage(ReplicationCliMain.mainCLI(args, false, outStream, errStream), ERRORS_ON_STDOUT);
  }

  @Test(dataProvider = "invalidArgs")
  public void testEncodePassword(final String[] args)
  {
    assertToolFailsWithUsage(EncodePassword.encodePassword(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testExportLDIF(final String[] args)
  {
    assertToolFailsWithUsage(ExportLDIF.mainExportLDIF(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testImportLDIF(final String[] args)
  {
    assertToolFailsWithUsage(ImportLDIF.mainImportLDIF(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testLDAPCompare(final String[] args)
  {
    assertToolFailsWithUsage(LDAPCompare.mainCompare(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testLDAPDelete(final String[] args)
  {
    assertToolFailsWithUsage(LDAPDelete.mainDelete(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testLDAPModify(final String[] args)
  {
    assertToolFailsWithUsage(LDAPModify.mainModify(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testLDAPPasswordModify(final String[] args)
  {
    assertToolFailsWithUsage(LDAPPasswordModify.mainPasswordModify(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testLDAPSearch(final String[] args)
  {
    assertToolFailsWithUsage(LDAPSearch.mainSearch(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testLDIFDiff(final String[] args)
  {
    assertToolFailsWithUsage(LDIFDiff.mainDiff(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testLDIFModify(final String[] args)
  {
    assertToolFailsWithUsage(LDIFModify.ldifModifyMain(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testLDIFSearch(final String[] args)
  {
    assertToolFailsWithUsage(LDIFSearch.mainSearch(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testListBackends(final String[] args)
  {
    assertToolFailsWithUsage(ListBackends.listBackends(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testMakeLDIF(final String[] args)
  {
    assertToolFailsWithUsage(MakeLDIF.main(args, outStream, errStream));
  }

  @Test(dataProvider = "invalidArgs")
  public void testManageAccount(final String[] args)
  {
    assertToolFailsWithUsage(ManageAccount.main(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testManageTasks(final String[] args)
  {
    assertToolFailsWithUsage(ManageTasks.mainTaskInfo(args, null, outStream, errStream, false), ERRORS_ON_STDOUT);
  }

  @Test(dataProvider = "invalidArgs")
  public void testRebuildIndex(final String[] args)
  {
    assertToolFailsWithUsage(RebuildIndex.mainRebuildIndex(args, false, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testStopDS(final String[] args)
  {
    assertToolFailsWithUsage(StopDS.stopDS(args, outStream, errStream));
  }

  @Test(dataProvider = "invalidArg")
  public void testStatus(final String[] args)
  {
    assertToolFailsWithUsage(StatusCli.mainCLI(args, false, outStream, errStream, null), ERRORS_ON_STDOUT);
  }

  @Test(dataProvider = "invalidArgs")
  public void testVerifyIndex(final String[] args)
  {
    assertToolFailsWithUsage(VerifyIndex.mainVerifyIndex(args, false, outStream, errStream));
  }

}
