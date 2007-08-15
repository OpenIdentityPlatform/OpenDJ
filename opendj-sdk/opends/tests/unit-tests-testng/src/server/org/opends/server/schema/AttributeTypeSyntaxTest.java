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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.schema;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringFactory;
import org.opends.messages.MessageBuilder;

import static org.testng.Assert.*;

/**
 * Test the AttributeTypeSyntax.
 */
public class AttributeTypeSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
  {
    return new AttributeTypeSyntax();
  }

  /**
   * {@inheritDoc}
   */
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
        {"(1.2.8.5 NAME 'testtype' DESC 'full type')",
              true},
        {"(1.2.8.5 USAGE directoryOperation )",
              true},
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE userApplications )",
          false}, // Collective can't inherit from non-collective
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " COLLECTIVE USAGE directoryOperation )",
          false}, // Collective can't be operational
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " NO-USER-MODIFICATION USAGE directoryOperation )",
          false}, // directoryOperation can't inherit from userApplications
        {"(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE " +
          " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch" +
          " SUBSTR caseIgnoreSubstringsMatch" +
          " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE" +
          " NO-USER-MODIFICATION USAGE userApplications )",
          false}, // NO-USER-MODIFICATION can't have non-operational usage
    };
  }



  /**
   * Tests the use of the "X-APPROX" extension to specify a particular
   * approximate matching rule.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testXAPPROXExtension()
         throws Exception
  {
    // Create and register the approximate matching rule for testing purposes.
    EqualLengthApproximateMatchingRule testApproxRule =
         new EqualLengthApproximateMatchingRule();
    testApproxRule.initializeMatchingRule(null);
    DirectoryServer.registerApproximateMatchingRule(testApproxRule, false);


    // Get a reference to the attribute type syntax implementation in the
    // server.
    AttributeTypeSyntax attrTypeSyntax =
      (AttributeTypeSyntax)
      DirectoryServer.getAttributeSyntax("1.3.6.1.4.1.1466.115.121.1.3", false);
    assertNotNull(attrTypeSyntax);


    // Create an attribute type definition and verify that it is acceptable.
    ByteString definition = ByteStringFactory.create(
      "( testxapproxtype-oid NAME 'testXApproxType' " +
           "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 " +
           "X-APPROX 'equalLengthApproximateMatch' )");
    MessageBuilder invalidReason = new MessageBuilder();
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
    assertEquals(attrType.getApproximateMatchingRule(), testApproxRule);
  }
}

