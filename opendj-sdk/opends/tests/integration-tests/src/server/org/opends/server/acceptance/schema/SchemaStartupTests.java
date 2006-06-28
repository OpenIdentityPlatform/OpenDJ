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
 * This class contains the JUnit tests for the Schema startup.
 */
public class SchemaStartupTests extends DirectoryServerAcceptanceTestCase
{
  public String schema_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", " "};
  public String schema_startup_datafiledir = acceptance_test_home + "/schema/data";

  public SchemaStartupTests(String name)
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

/**
 *  Setup for schema tests
*/
  public void testSchemaStartup1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Schema Startup test 1");
    String datafile = schema_startup_datafiledir + "/schema_start.ldif";
    schema_args[10] = datafile; 

    int retCode = LDAPModify.mainModify(schema_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
