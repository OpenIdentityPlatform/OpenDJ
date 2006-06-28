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
import org.opends.server.DirectoryServerAcceptanceAdmin;

/**
 * This class contains the JUnit tests for the Backend functional tests for import
 */
public class ImportTests extends DirectoryServerAcceptanceTestCase
{
  public String import_datafiledir = acceptance_test_home + "/backend/data";
  public String import_args[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", " "};
  public String import_args_1param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", " ", " "};
  public String import_args_2param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", " ", " ", "--append"};
  public String import_args_3param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", " ", " ", " ", "--append"};
  public String import_args_4param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/import.out", " ", " "};
  public String import_args_7param[] = {"--configClass", "org.opends.server.config.ConfigFileHandler", "--configFile", dsee_home + "/config/config.ldif", "--backendID", "com", "--ldifFile", "/tmp/import.out", " ", " ", " ", " ", " ", " ", "--append"};

  public String backup_mod_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String backup_mod_datafiledir = acceptance_test_home + "/backend/data";

  public String search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", " ", "objectclass=*"};
  public String search_args_1param[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", " ", "objectclass=*", " "};

  public ImportTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();
    prepDBEnv();
  }

  public void tearDown() throws Exception
  {
    undoDBEnv();
    super.tearDown();
  }

  public void testImport1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1");

    stopDirectoryServer();

    import_args[7] = import_datafiledir + "/import.ldif.01";
    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport1_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1 check entries 1");

    search_args[9] = "uid=scarter, ou=People, o=test one, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport1_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 1 check entries 2");

    search_args[9] = "uid=scarter, ou=People, o=backend tests,dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 2");

    stopDirectoryServer();

    import_args_1param[7] = import_datafiledir + "/import.ldif.02";
    import_args_1param[8] = "--append";
    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_1param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport2_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 2 check entries 1");

    search_args[9] = "uid=scarter, ou=People, o=test two, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport2_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 2 check entries 2");

    search_args[9] = "uid=scarter, ou=People, o=test one, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 3");

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.03";
    import_args_7param[8] = import_args_7param[10] = import_args_7param[12] = "--includeAttribute";
    import_args_7param[9] = "sn";
    import_args_7param[11] = "cn";
    import_args_7param[13] = "ou";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport3_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 3 check entries 1");

    search_args[9] = "uid=prigden3,ou=People,o=test one,o=import tests,dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport3_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 3 check entries 2");

    search_args[9] = "uid=scarter, ou=People, o=test one, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 4");

    stopDirectoryServer();

    import_args_3param[7] = import_datafiledir + "/import.ldif.04";
    import_args_3param[8] = "--excludeAttribute";
    import_args_3param[9] = "telephonenumber";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_3param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport4_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 4 check entries 1");

    search_args[9] = "uid=prigden4, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport4_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 4 check entries 2");

    search_args[9] = "uid=scarter, ou=People, o=test one, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 5");

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.05";
    import_args_7param[8] = import_args_7param[10] = import_args_7param[12] = "--excludeAttribute";
    import_args_7param[9] = "telephonenumber";
    import_args_7param[11] = "mail";
    import_args_7param[13] = "roomnumber";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport5_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 5 check entries 1");

    search_args[9] = "uid=prigden5, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport5_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 5 check entries 2");

    search_args[9] = "uid=scarter, ou=People, o=test one, o=import tests, dc=com"; 
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 6");

    stopDirectoryServer();

    import_args_3param[7] = import_datafiledir + "/import.ldif.06";
    import_args_3param[8] = "--includeFilter";
    import_args_3param[9] = "(&(uid=prigden6)(telephonenumber=*))";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_3param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport6_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 6 check entries 1");

    search_args[9] = "uid=prigden6, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport6_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 6 check entries 2");

    search_args[9] = "uid=brigden6, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 7");

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.07";
    import_args_7param[8] = import_args_7param[10] = import_args_7param[12] = "--includeFilter";
    import_args_7param[9] = "(&(uid=prigden7)(telephonenumber=*))";
    import_args_7param[11] = "(&(uid=prigden7)(l=Sunnyvale))";
    import_args_7param[13] = "(&(uid=brigden7)(roomnumber=*))"; 

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport7_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 7 check entries 1");

    search_args[9] = "uid=prigden7, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport7_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 7 check entries 2");

    search_args[9] = "uid=trigden7, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 8");

    stopDirectoryServer();

    import_args_3param[7] = import_datafiledir + "/import.ldif.08";
    import_args_3param[8] = "--excludeFilter";
    import_args_3param[9] = "(&(uid=prigden8)(telephonenumber=*))";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_3param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport8_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 8 check entries 1");

    search_args[9] = "uid=brigden8, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport8_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 8 check entries 2");

    search_args[9] = "uid=prigden8, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9");

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.09";
    import_args_7param[8] = import_args_7param[10] = import_args_7param[12] = "--excludeFilter";
    import_args_7param[9] = "(&(uid=prigden9)(telephonenumber=*))";
    import_args_7param[11] = "(&(uid=prigden9)(l=Sunnyvale))";
    import_args_7param[13] = "(&(uid=brigden9)(roomnumber=*))"; 

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport9_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 1");

    search_args[9] = "uid=trigden9, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport9_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 2");

    search_args[9] = "uid=prigden9, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport9_check3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 9 check entries 3");

    search_args[9] = "uid=brigden9, ou=People, o=test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 10");

    stopDirectoryServer();

    import_args_3param[7] = import_datafiledir + "/import.ldif.10";
    import_args_3param[8] = "--includeBranch";
    import_args_3param[9] = "o=branch test two, o=import tests, dc=com";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_3param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport10_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 10 check entries 1");

    search_args[9] = " uid=scarter, ou=People, o=branch test two, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport10_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 10 check entries 2");

    search_args[9] = " uid=scarter, ou=People, o=branch test one, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 11");

    stopDirectoryServer();

    import_args_3param[7] = import_datafiledir + "/import.ldif.11";
    import_args_3param[8] = "--excludeBranch";
    import_args_3param[9] = "o=branch test four, o=import tests, dc=com";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_3param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport11_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 11 check entries 1");

    search_args[9] = " uid=scarter, ou=People, o=branch test three, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport11_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 11 check entries 2");

    search_args[9] = " uid=scarter, ou=People, o=branch test four, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 12");

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.12";
    import_args_7param[8] = "--excludeFilter";
    import_args_7param[9] = "(&(uid=prigden)(roomnumber=*))";
    import_args_7param[10] = "--excludeAttribute";
    import_args_7param[11] = "telephonenumber";
    import_args_7param[12] = "--includeBranch";
    import_args_7param[13] = "o=branch test six, o=import tests, dc=com";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport12_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 12 check entries 1");

    search_args[9] = " uid=scarter, ou=People, o=branch test six, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport12_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 12 check entries 2");

    search_args[9] = " uid=prigden, ou=People, o=branch test six, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport12_check3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 12 check entries 3");

    search_args[9] = " uid=scarter, ou=People, o=branch test five, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport13() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 13");

    String datafile = backup_mod_datafiledir + "/branchTestAdd.ldif";
    backup_mod_args[10] = datafile;

    LDAPModify.mainModify(backup_mod_args);

    stopDirectoryServer();

    import_args_7param[7] = import_datafiledir + "/import.ldif.13";
    import_args_7param[8] = "--includeFilter";
    import_args_7param[9] = "(&(uid=prigden)(roomnumber=*))";
    import_args_7param[10] = "--excludeAttribute";
    import_args_7param[11] = "telephonenumber";
    import_args_7param[12] = "--excludeBranch";
    import_args_7param[13] = "o=branch test eight, o=import tests, dc=com";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_7param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport13_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 13 check entries 1");

    search_args[9] = " uid=prigden, ou=People, o=branch test seven, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testImport13_check2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 13 check entries 2");

    search_args[9] = " uid=prigden, ou=People, o=branch test eight, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport13_check3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 13 check entries 3");

    search_args[9] = " uid=scarter, ou=People, o=branch test eight, o=import tests, dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testImport14() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 14");

    stopDirectoryServer();

    import_args_2param[7] = import_datafiledir + "/import.compressed.ldif.gz";
    import_args_2param[8] = "--isCompressed";

    DirectoryServerAcceptanceAdmin.prepEnv(dsee_home);
    int retCode = ImportLDIF.mainImportLDIF(import_args_2param);
    int expCode = 0;
    DirectoryServerAcceptanceAdmin.undoEnv();

    compareExitCode(retCode, expCode);

    startDirectoryServer();
  }

  public void testImport14_check() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Import Test 14 check entries 1");

    search_args[9] = "uid=scarte2,ou=People,o=test fourteen,o=import tests,dc=com";
    int retCode = LDAPSearch.mainSearch(search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
