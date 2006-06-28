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
 * This class contains the JUnit tests for the Frontend functional tests for relationships in DNs.
 */
public class FrontendRFC2253_relationships extends DirectoryServerAcceptanceTestCase
{
  public String frontend_search_args[] = {"-h", hostname, "-p", port, "-D", "cn=Directory Manager", "-w", "password", "-b", " ", " "};

  public FrontendRFC2253_relationships(String name)
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

  public void testFrontendUserRelationship1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 1");
    frontend_search_args[9]="uid=jvedder;ou=People;o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 2");
    frontend_search_args[9]=" uid=jvedder, ou=People,  o=frontend tests ,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 3");
    frontend_search_args[9]="uid=\"jvedder\",ou=People,o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 4");
    frontend_search_args[9]="uid=\"jvedder, and Sons\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship5() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 5");
    frontend_search_args[9]="uid=\"jvedder=superguy\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship6() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 6");
    frontend_search_args[9]="uid=\"jvedder+football=superguy\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship7() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 7");
    frontend_search_args[9]="uid=\"jvedder>Sons\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship8() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 8");
    frontend_search_args[9]="uid=\"jvedder<boss\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship9() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 9");
    frontend_search_args[9]="uid=\"jvedder#Sons\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testFrontendUserRelationship10() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Frontend User Relationship test 10");
    frontend_search_args[9]="uid=\"jvedder;Sons\", ou=People, o=frontend tests,dc=com";
    frontend_search_args[10]="objectclass=*";

    int retCode = LDAPSearch.mainSearch(frontend_search_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

}
