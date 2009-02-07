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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.tools.LDAPModify;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class provides a set of test cases for virtual attributes.
 */
public class DITContentRuleTestCase
       extends TypesTestCase
{



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the addition of a new DITConentRule with a conflicting
   * rule identifier.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testInvalidDITContentRule()
         throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
      "dn: cn=schema",
      "changetype: modify",
      "add: nameForms",
      "nameForms: ( 1.3.6.1.1.10.15.100 NAME 'domainNameForm' OC domain MUST ( dc ) )",
      "-",
      "add: dITStructureRules",
      "dITStructureRules: ( 1 NAME 'domainStructureRule' FORM domainNameForm )"
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    //Shouldn't perform this operation.
    assertEquals(err,53);
  }



  /**
   * Tests the addition of new DITContentRules with unique rule ids.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.types."+
  "DITContentRuleTestCase.testInvalidDITContentRule"})
  public void testValidDITRContentRules()
         throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
      "dn: cn=schema",
      "changetype: modify",
      "add: nameForms",
      "nameForms: ( 1.3.6.1.1.10.15.11 NAME 'domainNameForm' OC domain MUST ( dc ) )",
      "nameForms: ( 1.3.6.1.1.10.15.12 NAME 'organizationalUnitNameForm' OC organizationalUnit MUST ( ou ) )",
      "nameForms: ( 1.3.6.1.1.10.15.13 NAME 'inetOrgPersonNameForm' OC inetOrgPerson MUST ( uid ) )",
      "nameForms: ( 1.3.6.1.1.10.15.14 NAME 'groupOfNamesNameForm' OC groupOfNames MUST ( cn ) )",
      "-",
      "add: dITStructureRules",
      "dITStructureRules: ( 11 NAME 'domainStructureRule' FORM domainNameForm )",
      "dITStructureRules: ( 12 NAME 'organizationalUnitStructureRule' FORM organizationalUnitNameForm SUP 1 )",
      "dITStructureRules: ( 13 NAME 'inetOrgPersonStructureRule' FORM inetOrgPersonNameForm SUP 2 )",
      "dITStructureRules: ( 14 NAME 'groupOfNamesStructureRule' FORM groupOfNamesNameForm SUP 2 )"
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    //Should add the above entries.
    assertEquals(err,0);
  }



  /**
   * Cleans up the DITContentRules.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dependsOnMethods = {"org.opends.server.types."+
  "DITContentRuleTestCase.testValidDITRContentRules"})
  public void cleanUpDITRContentRules()
         throws Exception
  {
    String filePath = TestCaseUtils.createTempFile(
      "dn: cn=schema",
      "changetype: modify",
      "delete: dITStructureRules",
      "dITStructureRules: ( 11 NAME 'domainStructureRule' FORM domainNameForm )",
      "dITStructureRules: ( 12 NAME 'organizationalUnitStructureRule' FORM organizationalUnitNameForm SUP 1 )",
      "dITStructureRules: ( 13 NAME 'inetOrgPersonStructureRule' FORM inetOrgPersonNameForm SUP 2 )",
      "dITStructureRules: ( 14 NAME 'groupOfNamesStructureRule' FORM groupOfNamesNameForm SUP 2 )",
      "-",
      "delete: nameForms",
      "nameForms: ( 1.3.6.1.1.10.15.11 NAME 'domainNameForm' OC domain MUST ( dc ) )",
      "nameForms: ( 1.3.6.1.1.10.15.12 NAME 'organizationalUnitNameForm' OC organizationalUnit MUST ( ou ) )",
      "nameForms: ( 1.3.6.1.1.10.15.13 NAME 'inetOrgPersonNameForm' OC inetOrgPerson MUST ( uid ) )",
      "nameForms: ( 1.3.6.1.1.10.15.14 NAME 'groupOfNamesNameForm' OC groupOfNames MUST ( cn ) )"
      );
    String[] args = new String []
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D","cn=directory manager",
      "-w","password",
      "-a",
      "-f", filePath
    };
    int err = LDAPModify.mainModify(args, false, null,null);
    //Should delete the above entries.
    assertEquals(err,0);
  }
}