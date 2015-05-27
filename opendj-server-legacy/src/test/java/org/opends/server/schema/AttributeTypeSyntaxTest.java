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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.schema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.AttributeType;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * Test the AttributeTypeSyntax.
 */
@RemoveOnceSDKSchemaIsUsed
public class AttributeTypeSyntaxTest extends AttributeSyntaxTest
{

  /** {@inheritDoc} */
  @Override
  protected AttributeSyntax getRule()
  {
    return new AttributeTypeSyntax();
  }

  /** {@inheritDoc} */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " USAGE userApplications )",
          true},
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE userApplications )",
          true},
          {"(1.2.8.5 NAME 'testtype')", true},
        {"(1.2.8.5 NAME 'testtype' DESC 'full type')",
              true},
        {"(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch " +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15)",
              true},
        {"(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch " +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' )",
              true},
        {"(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch " +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test')",
              true},
        {"(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch " +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' " +
          " X-SCHEMA-FILE '33-test.ldif' )",
              true},
        {"(1.2.8.5 USAGE directoryOperation )",
              true},
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE userApplications )",
          true}, // Collective can inherit from non-collective
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE directoryOperation )",
          true}, // Collective can be operational
        {"(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch " +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' " +
          " X-SCHEMA-FILE '33-test.ldif' X-NAME )",
          false}, // X-NAME is invalid extension (no value)
    };
  }



  /**
   * Tests the use of the "X-APPROX" extension to specify a particular
   * approximate matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testXAPPROXExtension() throws Exception
  {
    MatchingRule mrule = Schema.getCoreSchema().getMatchingRule("ds-mr-double-metaphone-approx");

    // Get a reference to the attribute type syntax implementation in the
    // server.
    Syntax attrTypeSyntax = DirectoryServer.getSchema().getSyntax("1.3.6.1.4.1.1466.115.121.1.3", false);
    assertNotNull(attrTypeSyntax);


    // Create an attribute type definition and verify that it is acceptable.
    ByteString definition = ByteString.valueOf(
      "( testxapproxtype-oid NAME 'testXApproxType' " +
           "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
           "X-APPROX 'ds-mr-double-metaphone-approx' )");
    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    assertTrue(attrTypeSyntax.valueIsAcceptable(definition, invalidReason),
            invalidReason.toString());


    // Verify that we can decode the attribute type and that it has the
    // correct approximate matching rule.
    AttributeType attrType =
         AttributeTypeSyntax.decodeAttributeType(definition,
                                                 DirectoryServer.getSchema(),
                                                 false);
    assertNotNull(attrType);
    assertNotNull(attrType.getApproximateMatchingRule());
    assertEquals(attrType.getApproximateMatchingRule(), mrule);
  }



  /**
   * Tests a situation when two radically different equality and substring
   * matching rules (such as Case Ignore and Case Exact) are used to define an
   * attribute description. For more information, look at the issue# 4468 in
   * Issue Tracker.
   *
   * @throws Exception In case of an error.
   */
  @Test
  public void testMixedEqualityAndSubstringMatchingRules() throws Exception
  {
    //Add an attribute with directory string syntax.
    int  resultCode = TestCaseUtils.applyModifications(true,
      "dn: cn=schema",
      "changetype: modify",
      "add: attributetypes",
      "attributeTypes: ( gvRights-oid NAME 'gvRights' DESC 'x attr' EQUALITY " +
      "caseIgnoreMatch ORDERING caseIgnoreOrderingMatch SUBSTR " +
      "caseExactSubstringsMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
      "USAGE userApplications )",
      "-",
      "add: objectclasses",
      "objectClasses: ( gvRightsTest-oid NAME 'gvRightsTest' DESC 'Test' SUP top AUXILIARY " +
      "MUST ( objectClass $ gvRights ) )");
    assertEquals(resultCode, 0);
    TestCaseUtils.initializeTestBackend(true);
    //add the test entry.
    TestCaseUtils.addEntry(
      "dn: cn=gvrightstest,o=test",
      "objectclass: person",
      "objectclass: gvRightsTest",
      "cn: gvrightstest",
      "sn: test",
      "gvRights: gvApplId=test2,ou=Applications,dc=bla$test2-T");

    //Search for the entry using substring matching rule filter.
    String filter = "(&(gvrights=*ApplId=test2,ou=*)" + "(gvrights=*test2,ou=A*))";
    SearchRequest request = newSearchRequest("cn=gvrightstest,o=test", SearchScope.WHOLE_SUBTREE, filter);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getEntriesSent(), 1);
  }
}

