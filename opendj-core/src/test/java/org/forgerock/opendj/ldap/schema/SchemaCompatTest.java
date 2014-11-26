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
 *      Copyright 2011 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

/**
 * Tests schema compatibility options.
 */
public class SchemaCompatTest extends AbstractSchemaTestCase {
    /**
     * Returns test data for valid attribute descriptions.
     *
     * @return The test data.
     */
    @DataProvider
    public Object[][] validAttributeDescriptions() {
        // @formatter:off
        return new Object[][] {
            // No options.
            { "cn", false },
            { "cn-xxx", false },
            { "cn", true },
            { "cn-xxx", true },
            { "cn_xxx", true },
            { "cn.xxx", true },
            // With options.
            { "cn;xxx", false },
            { "cn;xxx-yyy", false },
            { "cn;xxx", true },
            { "cn;xxx-yyy", true },
            { "cn;xxx_yyy", true },
            { "cn;xxx.yyy", true },
        };
        // @formatter:on
    }

    /**
     * Tests valid attribute description parsing behavior depends on compat
     * options.
     *
     * @param atd
     *            The attribute description to be parsed.
     * @param allowIllegalCharacters
     *            {@code true} if the attribute description requires the
     *            compatibility option to be set.
     */
    @Test(dataProvider = "validAttributeDescriptions")
    public void testValidAttributeDescriptions(String atd, boolean allowIllegalCharacters) {
        SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema())
            .setOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowIllegalCharacters);
        AttributeDescription.valueOf(atd, builder.toSchema().asNonStrictSchema());
    }

    /**
     * Returns test data for invalid attribute descriptions.
     *
     * @return The test data.
     */
    @DataProvider
    public Object[][] invalidAttributeDescriptions() {
        // @formatter:off
        return new Object[][] {
            // No options.
            { "cn+xxx", false }, // always invalid
            { "cn_xxx", false },
            { "cn.xxx", false },
            { "cn+xxx", true }, // always invalid
            // With options.
            { "cn;xxx+yyy", false }, // always invalid
            { "cn;xxx_yyy", false },
            { "cn;xxx.yyy", false },
            { "cn;xxx+yyy", true }, // always invalid
        };
        // @formatter:on
    }

    /**
     * Tests invalid attribute description parsing behavior depends on compat
     * options.
     *
     * @param atd
     *            The attribute description to be parsed.
     * @param allowIllegalCharacters
     *            {@code true} if the attribute description requires the
     *            compatibility option to be set.
     */
    @Test(dataProvider = "invalidAttributeDescriptions", expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testInvalidAttributeDescriptions(String atd, boolean allowIllegalCharacters) {
        SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.setOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowIllegalCharacters);
        AttributeDescription.valueOf(atd, builder.toSchema().asNonStrictSchema());
    }

    private static final Syntax ATD_SYNTAX = CoreSchema.getAttributeTypeDescriptionSyntax();
    private static final Syntax OCD_SYNTAX = CoreSchema.getObjectClassDescriptionSyntax();

    /**
     * Returns test data for invalid schema elements.
     *
     * @return The test data.
     */
    @DataProvider
    public Object[][] invalidSchemaElements() {
        // @formatter:off
        return new Object[][] {
            { "(testtype+oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(testtype_oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(testtype.oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(1.2.8.5 NAME 'test+type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(1.2.8.5 NAME 'test.type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(1.2.8.5 NAME 'test_type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(1.2.8.5 NAME 'test+type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              true
            },
        };
        // @formatter:on
    }

    /**
     * Test schema builder schema element parsing with compat chars.
     *
     * @param element
     *            The schema element.
     * @param syntax
     *            The type of element.
     * @param allowIllegalCharacters
     *            {@code true} if the element requires the compatibility option
     *            to be set.
     */
    @Test(dataProvider = "invalidSchemaElements", expectedExceptions = LocalizedIllegalArgumentException.class)
    public void testInvalidSchemaBuilderElementParsers(String element, Syntax syntax, boolean allowIllegalCharacters) {
        SchemaBuilder builder = new SchemaBuilder();
        builder.setOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowIllegalCharacters);

        if (syntax == ATD_SYNTAX) {
            builder.addAttributeType(element, false);
        } else if (syntax == OCD_SYNTAX) {
            builder.addObjectClass(element, false);
        }
    }

    /**
     * Returns test data for valid schema elements.
     *
     * @return The test data.
     */
    @DataProvider
    public Object[][] validSchemaElements() {
        // @formatter:off
        return new Object[][] {
            { "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(testtype-oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(testtype_oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              true
            },
            { "(testtype.oid NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                         + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                         + " SUBSTR caseIgnoreSubstringsMatch"
                         + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                         + " USAGE userApplications )",
              ATD_SYNTAX,
              true
            },
            { "(1.2.8.5 NAME 'test-type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              false
            },
            { "(1.2.8.5 NAME 'test.type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              true
            },
            { "(1.2.8.5 NAME 'test_type' DESC 'full type' OBSOLETE SUP cn "
                    + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                    + " SUBSTR caseIgnoreSubstringsMatch"
                    + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                    + " USAGE userApplications )",
              ATD_SYNTAX,
              true
            },
        };
        // @formatter:on
    }

    /**
     * Test schema builder schema element parsing with compat chars.
     *
     * @param element
     *            The schema element.
     * @param syntax
     *            The type of element.
     * @param allowIllegalCharacters
     *            {@code true} if the element requires the compatibility option
     *            to be set.
     */
    @Test(dataProvider = "validSchemaElements")
    public void testValidSchemaBuilderElementParsers(String element, Syntax syntax, boolean allowIllegalCharacters) {
        SchemaBuilder builder = new SchemaBuilder();
        builder.setOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowIllegalCharacters);

        if (syntax == ATD_SYNTAX) {
            builder.addAttributeType(element, false);
        } else if (syntax == OCD_SYNTAX) {
            builder.addObjectClass(element, false);
        }
    }
}
