/* * CDDL HEADER START
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
package org.opends.server.acceptance.frontend;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Frontend functional tests for standards.
 */
public class FrontendRFC2252_standards extends DirectoryServerAcceptanceTestCase
{
  public String frontend_compare_args[] = {"-h", hostname, "-p", port, " ", " "};
  public String frontend_mod_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-f", " "};
  public String frontend_datafiledir = acceptance_test_home + "/frontend/data";

  public FrontendRFC2252_standards(String name)
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

  public void testFrontendUserStandard1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 1");
    frontend_compare_args[4]="createTimestamp:2006";
    frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserStandard2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 2");
    frontend_compare_args[4]="creatorsName:cn=Directory Manager,cn=Root DNs,cn=config";
    frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

    int retCode = LDAPCompare.mainCompare(frontend_compare_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserStandard3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 3");
    frontend_mod_args[9]=frontend_datafiledir + "/standards/standards_1.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);

    if(retCode==0)
    {
      frontend_compare_args[4]="modifyTimestamp:2006";
      frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

      retCode = LDAPCompare.mainCompare(frontend_compare_args);
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserStandard4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Standard test 4");
    frontend_mod_args[9]=frontend_datafiledir + "/standards/standards_2.ldif";

    int retCode = LDAPModify.mainModify(frontend_mod_args);

    if(retCode==0)
    {
      frontend_compare_args[4]="modifiersName:cn=Directory Manager,cn=Root DNs,cn=config";
      frontend_compare_args[5]="uid=jreuter,ou=People,o=frontend tests,dc=com";

      retCode = LDAPCompare.mainCompare(frontend_compare_args);
    }
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
