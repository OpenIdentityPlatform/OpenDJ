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
package org.opends.server.acceptance.backend;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Backend functional tests for export
 */
public class ExportTests extends DirectoryServerAcceptanceTestCase
{
  public String export_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/export_test_1_and_2.out"};
  public String export_args_1param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/export_test_1_and_2.out", " "};
  public String export_args_2param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", " ", " ", " "};
  public String export_args_4param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/export.out", " ", " "};
  public String export_args_6param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/export.out", " ", " ", " ", " ", " ", " "};

  public ExportTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();

    String exec_cmd = "ln -s " + dsee_home + "/db db";
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec(exec_cmd);
    child.waitFor();
  }

  public void tearDown() throws Exception
  {
    Runtime rtime = Runtime.getRuntime();
    Process child = rtime.exec("rm db");
    child.waitFor();

    super.tearDown();
  }

  public void testExport1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 1");
    export_args[7] = "/tmp/export_test_1_and_2.out";

    int retCode = ExportLDIF.mainExportLDIF(export_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 2");
    export_args_1param[7] = "/tmp/export_test_1_and_2.out";
    export_args_1param[8] = "--appendToLDIF";

    int retCode = ExportLDIF.mainExportLDIF(export_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 3");
    export_args_2param[7] = "/tmp/export_test_3.out";
    export_args_2param[8] = "--includeAttribute";
    export_args_2param[9] = "telephonenumber";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 4");
    export_args_6param[7] = "/tmp/export_test_4.out";
    export_args_6param[8] = export_args_6param[10] = export_args_6param[12] = "--includeAttribute";
    export_args_6param[9] = "telephonenumber";
    export_args_6param[11] = "mail";
    export_args_6param[13] = "roomnumber";

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 5");
    export_args_2param[7] = "/tmp/export_test_5.out";
    export_args_2param[8] = "--excludeAttribute";
    export_args_2param[9] = "telephonenumber";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 6");
    export_args_6param[7] = "/tmp/export_test_6.out";
    export_args_6param[8] = export_args_6param[10] = export_args_6param[12] = "--excludeAttribute";
    export_args_6param[9] = "telephonenumber";
    export_args_6param[11] = "mail";
    export_args_6param[13] = "roomnumber";

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 7");
    export_args_2param[7] = "/tmp/export_test_7.out";
    export_args_2param[8] = "--includeFilter";
    export_args_2param[9] = "(&(uid=jwalker)(roomnumber=*))";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 8");
    export_args_6param[7] = "/tmp/export_test_8.out";
    export_args_6param[8] = export_args_6param[10] = export_args_6param[12] = "--includeFilter";
    export_args_6param[9] = "(&(uid=jwalker)(roomnumber=*))";
    export_args_6param[11] = "(&(uid=jwalker)(l=Cupertino))";
    export_args_6param[13] = "(&(uid=jwallace)(roomnumber=*))"; 

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 9");
    export_args_2param[7] = "/tmp/export_test_9.out";
    export_args_2param[8] = "--excludeFilter";
    export_args_2param[9] = "(&(uid=jwalker)(roomnumber=*))";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 10");
    export_args_6param[7] = "/tmp/export_test_10.out";
    export_args_6param[8] = export_args_6param[10] = export_args_6param[12] = "--excludeFilter";
    export_args_6param[9] = "(&(uid=jwalker)(roomnumber=*))";
    export_args_6param[11] = "(&(uid=jwalker)(l=Cupertino))";
    export_args_6param[13] = "(&(uid=jwallace)(roomnumber=*))"; 

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 11");
    export_args_2param[7] = "/tmp/export_test_11.out";
    export_args_2param[8] = "--includeBranch";
    export_args_2param[9] = "o=backend tests,dc=com";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 12");
    export_args_2param[7] = "/tmp/export_test_12.out";
    export_args_2param[8] = "--excludeBranch";
    export_args_2param[9] = "ou=People,o=backend tests,dc=com";

    int retCode = ExportLDIF.mainExportLDIF(export_args_2param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport13() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 13");
    export_args_6param[7] = "/tmp/export_test_13.out";
    export_args_6param[8] = "--excludeFilter";
    export_args_6param[9] = "(&(uid=jwalker)(roomnumber=*))";
    export_args_6param[10] = "--includeAttribute";
    export_args_6param[11] = "telephonenumber";
    export_args_6param[12] = "--includeBranch";
    export_args_6param[13] = "o=backend tests,dc=com";

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport14() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 14");
    export_args_6param[7] = "/tmp/export_test_14.out";
    export_args_6param[8] = "--includeFilter";
    export_args_6param[9] = "(&(uid=jwalker)(roomnumber=*))";
    export_args_6param[10] = "--excludeAttribute";
    export_args_6param[11] = "telephonenumber";
    export_args_6param[12] = "--excludeBranch";
    export_args_6param[13] = "ou=groups,o=backend tests,dc=com";

    int retCode = ExportLDIF.mainExportLDIF(export_args_6param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testExport15() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Export Test 15");
    export_args_1param[7] = "/tmp/export_test_15.out";
    export_args_1param[8] = "--compressLDIF";

    int retCode = ExportLDIF.mainExportLDIF(export_args_1param);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
