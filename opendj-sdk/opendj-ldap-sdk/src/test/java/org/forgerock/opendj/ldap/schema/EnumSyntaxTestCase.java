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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_OID_GENERIC_ENUM;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Enum syntax tests.
 */
@SuppressWarnings("javadoc")
public class EnumSyntaxTestCase extends SyntaxTestCase {
    /**
     * {@inheritDoc}
     */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "arbit-day", false }, { "wednesday", true }, };
    }

    @Test
    public void testDecode() throws SchemaException, DecodeException {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 3.3.3  DESC 'Day Of The Week' "
                + " X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday' "
                + " 'saturday' 'sunday') )", true);
        final Schema schema = builder.toSchema();
        final Syntax syntax = schema.getSyntax("3.3.3");
        final MatchingRule rule = syntax.getOrderingMatchingRule();
        Assert.assertEquals(rule.getGreaterOrEqualAssertion(ByteString.valueOf("monday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("thursday"))), ConditionResult.TRUE);
        Assert.assertEquals(rule.getLessOrEqualAssertion(ByteString.valueOf("monday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("thursday"))),
                ConditionResult.FALSE);
        Assert.assertEquals(rule.getGreaterOrEqualAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("monday"))), ConditionResult.FALSE);
        Assert.assertEquals(rule.getLessOrEqualAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("monday"))), ConditionResult.TRUE);
        Assert.assertEquals(rule.getGreaterOrEqualAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("tuesday"))), ConditionResult.TRUE);
        Assert.assertEquals(rule.getLessOrEqualAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("tuesday"))), ConditionResult.TRUE);
        Assert.assertEquals(rule.getAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("monday"))), ConditionResult.TRUE);
        Assert.assertEquals(rule.getAssertion(ByteString.valueOf("monday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("thursday"))),
                ConditionResult.FALSE);
        Assert.assertEquals(rule.getAssertion(ByteString.valueOf("tuesday")).matches(
                rule.normalizeAttributeValue(ByteString.valueOf("tuesday"))), ConditionResult.FALSE);
        Assert.assertNotNull(schema.getMatchingRule(OMR_OID_GENERIC_ENUM + ".3.3.3"));
    }

    @Test
    public void testDuplicateEnum() throws SchemaException, DecodeException {
        // This should be handled silently.
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 3.3.3  DESC 'Day Of The Week' "
                + " X-ENUM  ( 'monday' 'tuesday'   'wednesday'  'thursday'  'friday' "
                + " 'saturday' 'monday') )", true);
        builder.toSchema();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Syntax getRule() throws SchemaException, DecodeException {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addEnumerationSyntax("3.3.3", "Day Of The Week", false, "monday", "tuesday",
                "wednesday", "thursday", "friday", "saturday", "sunday");
        return builder.toSchema().getSyntax("3.3.3");
    }
}
