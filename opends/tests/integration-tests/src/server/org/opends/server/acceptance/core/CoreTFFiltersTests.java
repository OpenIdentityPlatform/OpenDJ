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
package org.opends.server.acceptance.core;

import org.opends.server.tools.*;
import org.opends.server.DirectoryServerAcceptanceTestCase;

/**
 * This class contains the JUnit tests for the Core true/false filter tests. 
 */
public class CoreTFFiltersTests extends DirectoryServerAcceptanceTestCase
{
  public String core_args[] = {"-h", hostname, "-p", port, "-D", bindDN, "-w", bindPW, "-b", "cn=version,cn=monitor", " "};

  public CoreTFFiltersTests(String name)
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

  public void testCoreTFFilters1() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 1");
    core_args[10]="&";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }

  public void testCoreTFFilters2() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 2");
    core_args[10]="|";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 0;

    compareExitCode(retCode, expCode);
  }


  public void testCoreTFFilters3() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 3");
    core_args[9] = "cn=bad,cn=monitor";
    core_args[10]="&";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

  public void testCoreTFFilters4() throws Exception
  {
    System.out.println("*********************************************");
    System.out.println("Core True/False Filters test 4");
    core_args[9] = "cn=bad,cn=monitor";
    core_args[10]="|";

    int retCode = LDAPSearch.mainSearch(core_args);
    int expCode = 32;

    compareExitCode(retCode, expCode);
  }

}
