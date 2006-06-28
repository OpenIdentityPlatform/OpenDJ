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
package org.opends.server.acceptance.schema;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;
/**
 * This class contains the JUnit tests for the Schema RFC tests.
 */
public class SchemaRFCTests extends DirectoryServerAcceptanceTestCase
{
  public String schema_search_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-s", "base", "-b", " ", " "};
  public String schema_add_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String schema_mod_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String schema_add_datafiledir = acceptance_test_home + "/schema/data";
  public String schema_mod_datafiledir = acceptance_test_home + "/schema/data";

  public SchemaRFCTests(String name)
  {
    super(name);
  }

  public void setUp() throws Exception
  {
    super.setUp();
  }

  public void tearDown() throws Exception
  {
    super.tearDown();
  }

  public void testSchemaRFC1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 1");
    String datafile = schema_add_datafiledir + "/rfc2079.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 2");
    String datafile = schema_add_datafiledir + "/rfc2247_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 3");
    String datafile = schema_add_datafiledir + "/rfc2247_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 4");
    String datafile = schema_add_datafiledir + "/rfc2247_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 5");
    String datafile = schema_add_datafiledir + "/rfc2247_4.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 6");
    String datafile = schema_add_datafiledir + "/rfc2247_5.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 7");
    String datafile = schema_add_datafiledir + "/rfc2377.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 8");
    String datafile = schema_add_datafiledir + "/rfc2798.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 9");
    String datafile = schema_mod_datafiledir + "/rfc3045_1.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 10");
    String datafile = schema_mod_datafiledir + "/rfc3045_2.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC11() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 11");
    String datafile = schema_add_datafiledir + "/rfc4403_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC12() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 12");
    String datafile = schema_add_datafiledir + "/rfc4403_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC13() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 13");
    String datafile = schema_add_datafiledir + "/rfc4403_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC14() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 14");
    String datafile = schema_add_datafiledir + "/rfc4403_4.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC15() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 15");
    String datafile = schema_add_datafiledir + "/rfc4403_5.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC16() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 16");
    String datafile = schema_add_datafiledir + "/rfc2307bis_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC17() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 17");
    String datafile = schema_add_datafiledir + "/rfc2307bis_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC18() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 18");
    String datafile = schema_add_datafiledir + "/rfc2307bis_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC19() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 19");
    String datafile = schema_add_datafiledir + "/rfc2307bis_4.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC20() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 20");
    String datafile = schema_add_datafiledir + "/untypedobject.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC21() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 21");
    String datafile = schema_add_datafiledir + "/changelog_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC22() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 22");
    String datafile = schema_add_datafiledir + "/changelog_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC23() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 23");
    String datafile = schema_add_datafiledir + "/changelog_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC24() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 24");
    String datafile = schema_add_datafiledir + "/namedobject_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC25() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 25");
    String datafile = schema_add_datafiledir + "/namedobject_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC26() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 26");
    String datafile = schema_add_datafiledir + "/ldup_subentry_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC27() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 27");
    String datafile = schema_add_datafiledir + "/ldup_subentry_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC28() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 28");
    String datafile = schema_add_datafiledir + "/ldup_subentry_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 53;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC29() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 29");
    String datafile = schema_mod_datafiledir + "/disable_schema_checking.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC30() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 30");
    String datafile = schema_mod_datafiledir + "/enable_schema_checking.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC31() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 31");
    String datafile = schema_mod_datafiledir + "/disable_syntax_checking.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC32() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 32");
    String datafile = schema_mod_datafiledir + "/enable_syntax_checking.ldif";
    schema_mod_args[9] = datafile;

    int retCode = LDAPModify.mainModify(schema_mod_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC33() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 33");
    String datafile = schema_add_datafiledir + "/ldap_cosine_1.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC34() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 34");
    String datafile = schema_add_datafiledir + "/ldap_cosine_2.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testSchemaRFC35() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema RFC test 35");
    String datafile = schema_add_datafiledir + "/ldap_cosine_3.ldif";
    schema_add_args[10] = datafile;

    int retCode = LDAPModify.mainModify(schema_add_args);
    int expCode = 65;

    compareExitCode(retCode, expCode);
  }


}
