/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/** Test the AttributeTypeSyntax. */
@RemoveOnceSDKSchemaIsUsed
public class AttributeTypeSyntaxTest extends SchemaTestCase
{

  /**
   * Tests the use of the "X-APPROX" extension to specify a particular
   * approximate matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testXAPPROXExtension() throws Exception
  {
    Schema schema = DirectoryServer.getInstance().getServerContext().getSchema();
    Syntax attrTypeSyntax = schema.getSyntax("1.3.6.1.4.1.1466.115.121.1.3");
    assertNotNull(attrTypeSyntax);

    // Create an attribute type definition and verify that it is acceptable.
    ByteString definition = ByteString.valueOfUtf8(
      "( testxapproxtype-oid NAME 'testXApproxType' " +
           "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
           "X-APPROX 'ds-mr-double-metaphone-approx' )");
    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    assertTrue(attrTypeSyntax.valueIsAcceptable(definition, invalidReason), invalidReason.toString());

    // Verify that we can decode the attribute type and that it has the
    // correct approximate matching rule.
    Schema newSchema = new SchemaBuilder(schema)
      .addAttributeType(definition.toString(), false)
      .toSchema();

    AttributeType attrType = newSchema.getAttributeType("testXApproxType");
    assertNotNull(attrType.getApproximateMatchingRule());
    assertEquals(attrType.getApproximateMatchingRule(), schema.getMatchingRule("ds-mr-double-metaphone-approx"));
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

