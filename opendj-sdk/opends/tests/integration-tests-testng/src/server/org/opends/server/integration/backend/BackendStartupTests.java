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
package org.opends.server.integration.backend;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.tools.*;

/**
 * This class contains the TestNG tests for the Backend startup.
 */
@Test
public class BackendStartupTests extends BackendTests
{
/**
 *  Setup for backend tests
*/
  @Parameters({ "hostname", "port", "bindDN", "bindPW", "integration_test_home", "logDir" })
  @Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.schema.SchemaRFCTests.testSchemaRFC35" })
  //@Test(alwaysRun=true, dependsOnMethods = { "org.opends.server.integration.ssl.jks.JKSStartupTests.testJKSStartup1" })
  public void testBackendStartup1(String hostname, String port, String bindDN, String bindPW, String integration_test_home, String logDir) throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Backend Startup test 1");
    String datafile = integration_test_home + "/backend/data/backend_start.ldif";
    String backend_args[] = {"-a", "-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-f", datafile};
 
    ds_output.redirectOutput(logDir, "BackendStartup1.txt");
    int retCode = LDAPModify.mainModify(backend_args);
    ds_output.resetOutput();
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }
  
}
