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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 *      Portions copyright 2014 Manuel Gaupp
 */
package org.forgerock.opendj.ldap.schema;

import static java.util.Collections.*;

import static org.forgerock.opendj.ldap.schema.CollationMatchingRulesImpl.*;
import static org.forgerock.opendj.ldap.schema.ObjectClassType.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.TimeBasedMatchingRulesImpl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

final class CoreSchemaImpl {
    private static final Map<String, List<String>> X500_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("X.500"));

    private static final Map<String, List<String>> RFC2252_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 2252"));
    private static final Map<String, List<String>> RFC3045_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 3045"));
    private static final Map<String, List<String>> RFC3112_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 3112"));
    private static final Map<String, List<String>> RFC4512_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4512"));
    private static final Map<String, List<String>> RFC4517_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4517"));
    private static final Map<String, List<String>> RFC4519_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4519"));
    private static final Map<String, List<String>> RFC4523_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4523"));
    private static final Map<String, List<String>> RFC4530_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 4530"));
    private static final Map<String, List<String>> RFC5020_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("RFC 5020"));

    static final Map<String, List<String>> OPENDS_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("OpenDS Directory Server"));
    private static final Map<String, List<String>> OPENDJ_ORIGIN = Collections.singletonMap(
            SCHEMA_PROPERTY_ORIGIN, Collections.singletonList("OpenDJ Directory Server"));

    private static final Schema SINGLETON;

    public static final Map<String, String> JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS =
            CoreSchemaSupportedLocales.getJvmSupportedLocaleNamesToOids();

    static {
        final SchemaBuilder builder = new SchemaBuilder("Core Schema");
        defaultSyntaxes(builder);
        defaultMatchingRules(builder);
        defaultAttributeTypes(builder);
        defaultObjectClasses(builder);

        addRFC4519(builder);
        addRFC4523(builder);
        addRFC4530(builder);
        addRFC3045(builder);
        addRFC3112(builder);
        addRFC5020(builder);
        addSunProprietary(builder);
        addForgeRockProprietary(builder);

        SINGLETON = builder.toSchema().asNonStrictSchema();
    }

    static Schema getInstance() {
        return SINGLETON;
    }

    private static void addRFC3045(final SchemaBuilder builder) {
        builder.buildAttributeType("1.3.6.1.1.4")
               .names("vendorName")
               .equalityMatchingRule(EMR_CASE_EXACT_IA5_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC3045_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.1.5")
               .names("vendorVersion")
               .equalityMatchingRule(EMR_CASE_EXACT_IA5_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC3045_ORIGIN)
               .addToSchema();
    }

    private static void addRFC3112(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_AUTH_PASSWORD_OID)
               .description(SYNTAX_AUTH_PASSWORD_DESCRIPTION)
               .extraProperties(RFC3112_ORIGIN)
               .implementation(new AuthPasswordSyntaxImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID)
                .names(EMR_AUTH_PASSWORD_EXACT_NAME)
                .description(EMR_AUTH_PASSWORD_EXACT_DESCRIPTION)
                .syntaxOID(SYNTAX_AUTH_PASSWORD_OID)
                .extraProperties(RFC3112_ORIGIN)
                .implementation(new AuthPasswordExactEqualityMatchingRuleImpl())
                .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.4203.1.3.3")
               .names("supportedAuthPasswordSchemes")
               .description("supported password storage schemes")
               .equalityMatchingRule(EMR_CASE_EXACT_IA5_OID)
               .syntax(SYNTAX_IA5_STRING_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC3112_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.4203.1.3.4")
               .names("authPassword")
               .description("password authentication information")
               .equalityMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID)
               .syntax(SYNTAX_AUTH_PASSWORD_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC3112_ORIGIN)
               .addToSchema();

        builder.buildObjectClass("1.3.6.1.4.1.4203.1.4.7")
                .names("authPasswordObject")
                .type(AUXILIARY)
                .description("authentication password mix in class")
                .optionalAttributes("authPassword")
                .extraProperties(RFC3112_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4519(final SchemaBuilder builder) {
        builder.buildAttributeType("2.5.4.15")
               .names("businessCategory")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.41")
               .names("name")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.6")
               .names("c", "countryName")
               .superiorType("name")
               .syntax(SYNTAX_COUNTRY_STRING_OID)
               .singleValue(true)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.3")
               .names("cn", "commonName")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("0.9.2342.19200300.100.1.25")
               .names("dc", "domainComponent")
               .equalityMatchingRule(EMR_CASE_IGNORE_IA5_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_IA5_OID)
               .syntax(SYNTAX_IA5_STRING_OID)
               .singleValue(true)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.13")
               .names("description")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.27")
               .names("destinationIndicator")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_PRINTABLE_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.49")
               .names("distinguishedName")
               .equalityMatchingRule(EMR_DN_OID)
               .syntax(SYNTAX_DN_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.46")
               .names("dnQualifier")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .orderingMatchingRule(OMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_PRINTABLE_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.47")
               .names("enhancedSearchGuide")
               .syntax(SYNTAX_ENHANCED_GUIDE_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.23")
               .names("facsimileTelephoneNumber")
               .syntax(SYNTAX_FAXNUMBER_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.44")
               .names("generationQualifier")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.42")
               .names("givenName")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.51")
               .names("houseIdentifier")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.43")
               .names("initials")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.25")
               .names("internationalISDNNumber")
               .equalityMatchingRule(EMR_NUMERIC_STRING_OID)
               .substringMatchingRule(SMR_NUMERIC_STRING_OID)
               .syntax(SYNTAX_NUMERIC_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.7")
               .names("l", "localityName")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.31")
               .names("member")
               .superiorType("distinguishedName")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.10")
               .names("o", "organizationName")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.11")
               .names("ou", "organizationalUnitName")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.32")
               .names("owner")
               .superiorType("distinguishedName")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.19")
               .names("physicalDeliveryOfficeName")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.16")
               .names("postalAddress")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.17")
               .names("postalCode")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.18")
               .names("postOfficeBox")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.28")
               .names("preferredDeliveryMethod")
               .syntax(SYNTAX_DELIVERY_METHOD_OID)
               .singleValue(true)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.26")
               .names("registeredAddress")
               .superiorType("postalAddress")
               .syntax(SYNTAX_POSTAL_ADDRESS_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.33")
               .names("roleOccupant")
               .superiorType("distinguishedName")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.14")
               .names("searchGuide")
               .syntax(SYNTAX_GUIDE_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.34")
               .names("seeAlso")
               .superiorType("distinguishedName")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.5")
               .names("serialNumber")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_PRINTABLE_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.4")
               .names("sn", "surname")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.8")
               .names("st", "stateOrProvinceName")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.9")
               .names("street", "streetAddress")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.20")
               .names("telephoneNumber")
               .equalityMatchingRule(EMR_TELEPHONE_OID)
               .substringMatchingRule(SMR_TELEPHONE_OID)
               .syntax(SYNTAX_TELEPHONE_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.22")
               .names("teletexTerminalIdentifier")
               .syntax(SYNTAX_TELETEX_TERM_ID_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.21")
               .names("telexNumber")
               .syntax(SYNTAX_TELEX_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.12")
               .names("title")
               .superiorType("name")
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("0.9.2342.19200300.100.1.1")
               .names("uid", "userid")
               .equalityMatchingRule(EMR_CASE_IGNORE_OID)
               .substringMatchingRule(SMR_CASE_IGNORE_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.50")
               .names("uniqueMember")
               .equalityMatchingRule(EMR_UNIQUE_MEMBER_OID)
               .syntax(SYNTAX_NAME_AND_OPTIONAL_UID_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.35")
               .names("userPassword")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_OCTET_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.24")
               .names("x121Address")
               .equalityMatchingRule(EMR_NUMERIC_STRING_OID)
               .substringMatchingRule(SMR_NUMERIC_STRING_OID)
               .syntax(SYNTAX_NUMERIC_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.45")
               .names("x500UniqueIdentifier")
               .equalityMatchingRule(EMR_BIT_STRING_OID)
               .syntax(SYNTAX_BIT_STRING_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4519_ORIGIN)
               .addToSchema();

        builder.buildObjectClass("2.5.6.11")
                .names("applicationProcess")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("seeAlso", "ou", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.2")
                .names("country")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("c")
                .optionalAttributes("searchGuide", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("1.3.6.1.4.1.1466.344")
                .names("dcObject")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("dc")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.14")
                .names("device")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("serialNumber", "seeAlso", "owner", "ou", "o", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.9")
                .names("groupOfNames")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("member", "cn")
                .optionalAttributes("businessCategory", "seeAlso", "owner", "ou", "o", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.17")
                .names("groupOfUniqueNames")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("member", "cn")
                .optionalAttributes("businessCategory", "seeAlso", "owner", "ou", "o", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.3")
                .names("locality")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("street", "seeAlso", "searchGuide", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.4")
                .names("organization")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("o")
                .optionalAttributes("userPassword", "searchGuide", "seeAlso", "businessCategory", "x121Address",
                        "registeredAddress", "destinationIndicator", "preferredDeliveryMethod", "telexNumber",
                        "teletexTerminalIdentifier", "telephoneNumber", "internationalISDNNumber",
                        "facsimileTelephoneNumber", "street", "postOfficeBox", "postalCode", "postalAddress",
                        "physicalDeliveryOfficeName", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.7")
                .names("organizationalPerson")
                .superiorObjectClasses("person")
                .optionalAttributes("title", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "street", "postOfficeBox",
                        "postalCode", "postalAddress", "physicalDeliveryOfficeName", "ou", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.8")
                .names("organizationalRole")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "seeAlso", "roleOccupant",
                        "preferredDeliveryMethod", "street", "postOfficeBox", "postalCode", "postalAddress",
                        "physicalDeliveryOfficeName", "ou", "st", "l", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.5")
                .names("organizationalUnit")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("ou")
                .optionalAttributes("businessCategory", "description", "destinationIndicator",
                        "facsimileTelephoneNumber", "internationalISDNNumber", "l", "physicalDeliveryOfficeName",
                        "postalAddress", "postalCode", "postOfficeBox", "preferredDeliveryMethod",
                        "registeredAddress", "searchGuide", "seeAlso", "st", "street", "telephoneNumber",
                        "teletexTerminalIdentifier", "telexNumber", "userPassword", "x121Address")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.6")
                .names("person")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("sn", "cn")
                .optionalAttributes("userPassword", "telephoneNumber", "destinationIndicator", "seeAlso", "description")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.10")
                .names("residentialPerson")
                .superiorObjectClasses("person")
                .requiredAttributes("l")
                .optionalAttributes("businessCategory", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "preferredDeliveryMethod", "street",
                        "postOfficeBox", "postalCode", "postalAddress", "physicalDeliveryOfficeName", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("1.3.6.1.1.3.1")
                .names("uidObject")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("uid")
                .optionalAttributes("businessCategory", "x121Address", "registeredAddress", "destinationIndicator",
                        "preferredDeliveryMethod", "telexNumber", "teletexTerminalIdentifier", "telephoneNumber",
                        "internationalISDNNumber", "facsimileTelephoneNumber", "preferredDeliveryMethod", "street",
                        "postOfficeBox", "postalCode", "postalAddress", "physicalDeliveryOfficeName", "st", "l")
                .extraProperties(RFC4519_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4523(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_CERTLIST_OID)
               .description(SYNTAX_CERTLIST_DESCRIPTION)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new CertificateListSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_CERTPAIR_OID)
               .description(SYNTAX_CERTPAIR_DESCRIPTION)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new CertificatePairSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_CERTIFICATE_OID)
               .description(SYNTAX_CERTIFICATE_DESCRIPTION)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new CertificateSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID)
               .description(SYNTAX_CERTIFICATE_EXACT_ASSERTION_DESCRIPTION)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new CertificateExactAssertionSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_SUPPORTED_ALGORITHM_OID)
               .description(SYNTAX_SUPPORTED_ALGORITHM_DESCRIPTION)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new SupportedAlgorithmSyntaxImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CERTIFICATE_EXACT_OID)
               .names(EMR_CERTIFICATE_EXACT_NAME)
               .syntaxOID(SYNTAX_CERTIFICATE_EXACT_ASSERTION_OID)
               .extraProperties(RFC4523_ORIGIN)
               .implementation(new CertificateExactMatchingRuleImpl())
               .addToSchema();

        builder.buildAttributeType("2.5.4.36")
               .names("userCertificate")
               .description("X.509 user certificate")
               .equalityMatchingRule(EMR_CERTIFICATE_EXACT_OID)
               .syntax(SYNTAX_CERTIFICATE_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.37")
               .names("cACertificate")
               .description("X.509 CA certificate")
               .equalityMatchingRule(EMR_CERTIFICATE_EXACT_OID)
               .syntax(SYNTAX_CERTIFICATE_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.38")
               .names("authorityRevocationList")
               .description("X.509 authority revocation list")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_CERTLIST_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.39")
               .names("certificateRevocationList")
               .description("X.509 certificate revocation list")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_CERTLIST_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.40")
               .names("crossCertificatePair")
               .description("X.509 cross certificate pair")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_CERTPAIR_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.52")
               .names("supportedAlgorithms")
               .description("X.509 supported algorithms")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_SUPPORTED_ALGORITHM_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.53")
               .names("deltaRevocationList")
               .description("X.509 delta revocation list")
               .equalityMatchingRule(EMR_OCTET_STRING_OID)
               .syntax(SYNTAX_CERTLIST_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4523_ORIGIN)
               .addToSchema();

        builder.buildObjectClass("2.5.6.21")
                .names("pkiUser")
                .type(AUXILIARY)
                .description("X.509 PKI User")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("userCertificate")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.22")
                .names("pkiCA")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .description("X.509 PKI Certificate Authority")
                .optionalAttributes("cACertificate", "certificateRevocationList",
                    "authorityRevocationList", "crossCertificatePair")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.19")
                .names("cRLDistributionPoint")
                .description("X.509 CRL distribution point")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("cn")
                .optionalAttributes("certificateRevocationList", "authorityRevocationList", "deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.23")
                .names("deltaCRL")
                .type(AUXILIARY)
                .description("X.509 delta CRL")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.15")
                .names("strongAuthenticationUser")
                .type(AUXILIARY)
                .description("X.521 strong authentication user")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("userCertificate")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.18")
                .names("userSecurityInformation")
                .type(AUXILIARY)
                .description("X.521 user security information")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("supportedAlgorithms")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.16")
                .names("certificationAuthority")
                .type(AUXILIARY)
                .description("X.509 certificate authority")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("authorityRevocationList", "certificateRevocationList", "cACertificate")
                .optionalAttributes("crossCertificatePair")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.16.2")
                .names("certificationAuthority-V2")
                .type(AUXILIARY)
                .description("X.509 certificate authority, version 2")
                .superiorObjectClasses("certificationAuthority")
                .optionalAttributes("deltaRevocationList")
                .extraProperties(RFC4523_ORIGIN)
                .addToSchema();
    }

    private static void addRFC4530(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_UUID_OID)
               .description(SYNTAX_UUID_DESCRIPTION)
               .extraProperties(RFC4530_ORIGIN)
               .implementation(new UUIDSyntaxImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_UUID_OID)
               .names(EMR_UUID_NAME)
               .syntaxOID(SYNTAX_UUID_OID)
               .extraProperties(RFC4530_ORIGIN)
               .implementation(new UUIDEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_UUID_OID)
               .names(OMR_UUID_NAME)
               .syntaxOID(SYNTAX_UUID_OID)
               .extraProperties(RFC4530_ORIGIN)
               .implementation(new UUIDOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.1.16.4")
               .names("entryUUID")
               .description("UUID of the entry")
               .equalityMatchingRule(EMR_UUID_OID)
               .orderingMatchingRule(OMR_UUID_OID)
               .syntax(SYNTAX_UUID_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4530_ORIGIN)
               .addToSchema();
    }

    private static void addRFC5020(final SchemaBuilder builder) {
        builder.buildAttributeType("1.3.6.1.1.20")
               .names("entryDN")
               .description("DN of the entry")
               .equalityMatchingRule(EMR_DN_OID)
               .syntax(SYNTAX_DN_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC5020_ORIGIN)
               .addToSchema();
    }

    private static void addSunProprietary(final SchemaBuilder builder) {
        builder.buildSyntax(SYNTAX_USER_PASSWORD_OID)
               .description(SYNTAX_USER_PASSWORD_DESCRIPTION)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(new UserPasswordSyntaxImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_USER_PASSWORD_EXACT_OID)
               .names(singletonList(EMR_USER_PASSWORD_EXACT_NAME))
               .description(EMR_USER_PASSWORD_EXACT_DESCRIPTION)
               .syntaxOID(SYNTAX_USER_PASSWORD_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(new UserPasswordExactEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(AMR_DOUBLE_METAPHONE_OID)
               .names(singletonList(AMR_DOUBLE_METAPHONE_NAME))
               .description(AMR_DOUBLE_METAPHONE_DESCRIPTION)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(new DoubleMetaphoneApproximateMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_RELATIVE_TIME_GREATER_THAN_OID)
               .names(OMR_RELATIVE_TIME_GREATER_THAN_NAME, OMR_RELATIVE_TIME_GREATER_THAN_ALT_NAME)
               .description(OMR_RELATIVE_TIME_GREATER_THAN_DESCRIPTION)
               .syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(relativeTimeGTOMatchingRule())
               .addToSchema();

        builder.buildMatchingRule(OMR_RELATIVE_TIME_LESS_THAN_OID)
               .names(OMR_RELATIVE_TIME_LESS_THAN_NAME, OMR_RELATIVE_TIME_LESS_THAN_ALT_NAME)
               .description(OMR_RELATIVE_TIME_LESS_THAN_DESCRIPTION)
               .syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(relativeTimeLTOMatchingRule())
               .addToSchema();

        builder.buildMatchingRule(MR_PARTIAL_DATE_AND_TIME_OID)
               .names(singletonList(MR_PARTIAL_DATE_AND_TIME_NAME))
               .description(MR_PARTIAL_DATE_AND_TIME_DESCRIPTION)
               .syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(partialDateAndTimeMatchingRule())
               .addToSchema();

        addCollationMatchingRules(builder);
    }

    private static void addForgeRockProprietary(SchemaBuilder builder) {
        builder.buildAttributeType("1.3.6.1.4.1.36733.2.1.1.141")
               .names("fullVendorVersion")
               .equalityMatchingRule(EMR_CASE_EXACT_IA5_OID)
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(OPENDJ_ORIGIN)
               .addToSchema();
    }

    /**
     * Adds the collation matching rules.
     * <p>
     * A set of collation matching rules is registered for each locale that is both available in the java runtime
     * environment and has an oid defined in the {@code LOCALE_NAMES_TO_OIDS} map. Note that the same oid can be used
     * for multiple locales (e.g., matching rule for "en" and "en-US" uses the same oid).
     * <p>
     * To add support for a new locale, add a corresponding entry in the {@code LOCALE_NAMES_TO_OIDS} map.
     */
    private static void addCollationMatchingRules(final SchemaBuilder builder) {
        // Build an intermediate map to ensure each locale name appears only once
        final Map<String, Locale> localesCache = new TreeMap<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            localesCache.put(localeName(locale), locale);
        }

        // Build a intermediate map to list all available oids with their locale names
        // An oid can be associated to multiple locale names
        final Map<String, List<String>> oidsCache = new HashMap<>();
        for (final String localeName: localesCache.keySet()) {
            String oid = JVM_SUPPORTED_LOCALE_NAMES_TO_OIDS.get(localeName);
            if (oid != null) {
                List<String> names = oidsCache.get(oid);
                if (names == null) {
                    names = new ArrayList<>(5);
                    oidsCache.put(oid, names);
                }
                names.add(localeName);
            }
        }

        // Now build the matching rules from all available oids
        for (final Entry<String, List<String>> entry : oidsCache.entrySet()) {
            final String oid = entry.getKey();
            final List<String> names = entry.getValue();
            // take locale from first name - all locales of names are considered equivalent here
            final Locale locale = localesCache.get(names.get(0));
            addCollationMatchingRule(builder, oid, names, 1, "lt", collationLessThanMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 2, "lte", collationLessThanOrEqualMatchingRule(locale));
            MatchingRuleImpl collationEqualityMatchingRule = collationEqualityMatchingRule(locale);
            addCollationMatchingRule(builder, oid, names, 3, "eq", collationEqualityMatchingRule);
            // the default oid is registered with equality matching rule
            final int ignored = 0;
            addCollationMatchingRule(builder, oid, names, ignored, "", collationEqualityMatchingRule);
            addCollationMatchingRule(builder, oid, names, 4, "gte", collationGreaterThanOrEqualToMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 5, "gt", collationGreaterThanMatchingRule(locale));
            addCollationMatchingRule(builder, oid, names, 6, "sub", collationSubstringMatchingRule(locale));
        }
    }

    /** Add a specific collation matching rule to the schema. */
    private static void addCollationMatchingRule(final SchemaBuilder builder, final String baseOid,
            final List<String> names, final int numericSuffix, final String symbolicSuffix,
            final MatchingRuleImpl matchingRuleImplementation) {
        final String oid = symbolicSuffix.isEmpty() ? baseOid : baseOid + "." + numericSuffix;
        builder.buildMatchingRule(oid)
               .names(collationMatchingRuleNames(names, numericSuffix, symbolicSuffix))
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(OPENDS_ORIGIN)
               .implementation(matchingRuleImplementation)
               .addToSchema();
    }

    /**
     * Build the complete list of names for a collation matching rule.
     *
     * @param localeNames
     *            List of locale names that correspond to the matching rule (e.g., "en", "en-US")
     * @param numSuffix
     *            numeric suffix corresponding to type of matching rule (e.g, 5 for greater than matching rule). It is
     *            ignored if equal to zero (0).
     * @param symbolicSuffix
     *            symbolic suffix corresponding to type of matching rule (e.g, "gt" for greater than matching rule). It
     *            may be empty ("") to indicate the default rule.
     * @return the names list (e.g, "en.5", "en.gt", "en-US.5", "en-US.gt")
     */
    private static String[] collationMatchingRuleNames(final List<String> localeNames, final int numSuffix,
        final String symbolicSuffix) {
        final List<String> names = new ArrayList<>();
        for (String localeName : localeNames) {
            if (symbolicSuffix.isEmpty()) {
                // the default rule
                names.add(localeName);
            } else {
                names.add(localeName + "." + numSuffix);
                names.add(localeName + "." + symbolicSuffix);
            }
        }
        return names.toArray(new String[names.size()]);
    }

    /**
     * Returns the name corresponding to the provided locale.
     * <p>
     * The name is using format:
     * <pre>
     *   language code (lower case) + "-" + country code (upper case) + "-" + variant (upper case)
     * </pre>
     * Country code and variant are optional, they may not appear.
     * See LOCALE_NAMES_TO_OIDS keys for examples of names
     *
     * @param locale
     *          The locale
     * @return the name associated to the locale
     */
    private static String localeName(final Locale locale) {
        final StringBuilder name = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!country.isEmpty()) {
            name.append('-').append(country);
        }
        final String variant = locale.getVariant();
        if (!variant.isEmpty()) {
            name.append('-').append(variant.toUpperCase());
        }
        return name.toString();
    }

    private static void defaultAttributeTypes(final SchemaBuilder builder) {
        builder.buildAttributeType("2.5.4.0")
               .names("objectClass")
               .equalityMatchingRule(EMR_OID_NAME)
               .syntax(SYNTAX_OID_OID)
               .usage(AttributeUsage.USER_APPLICATIONS)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.4.1")
               .names("aliasedObjectName")
               .equalityMatchingRule(EMR_DN_NAME)
               .syntax(SYNTAX_DN_OID)
               .singleValue(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.18.1")
               .names("createTimestamp")
               .equalityMatchingRule(EMR_GENERALIZED_TIME_NAME)
               .orderingMatchingRule(OMR_GENERALIZED_TIME_NAME)
               .syntax(SYNTAX_GENERALIZED_TIME_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.18.2")
               .names("modifyTimestamp")
               .equalityMatchingRule(EMR_GENERALIZED_TIME_NAME)
               .orderingMatchingRule(OMR_GENERALIZED_TIME_NAME)
               .syntax(SYNTAX_GENERALIZED_TIME_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.18.3")
               .names("creatorsName")
               .equalityMatchingRule(EMR_DN_NAME)
               .syntax(SYNTAX_DN_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.18.4")
               .names("modifiersName")
               .equalityMatchingRule(EMR_DN_NAME)
               .syntax(SYNTAX_DN_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.18.10")
               .names("subschemaSubentry")
               .equalityMatchingRule(EMR_DN_NAME)
               .syntax(SYNTAX_DN_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.5")
               .names("attributeTypes")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_ATTRIBUTE_TYPE_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.6")
               .names("objectClasses")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_OBJECTCLASS_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.4")
               .names("matchingRules")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_MATCHING_RULE_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.8")
               .names("matchingRuleUse")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_MATCHING_RULE_USE_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.9")
               .names("structuralObjectClass")
               .equalityMatchingRule(EMR_OID_NAME)
               .syntax(SYNTAX_OID_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.10")
               .names("governingStructureRule")
               .equalityMatchingRule(EMR_INTEGER_NAME)
               .syntax(SYNTAX_INTEGER_OID)
               .singleValue(true)
               .noUserModification(true)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.5")
               .names("namingContexts")
               .syntax(SYNTAX_DN_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.6")
               .names("altServer")
               .syntax(SYNTAX_IA5_STRING_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.7")
               .names("supportedExtension")
               .syntax(SYNTAX_OID_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.13")
               .names("supportedControl")
               .syntax(SYNTAX_OID_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.14")
               .names("supportedSASLMechanisms")
               .syntax(SYNTAX_DIRECTORY_STRING_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.4203.1.3.5")
               .names("supportedFeatures")
               .equalityMatchingRule(EMR_OID_NAME)
               .syntax(SYNTAX_OID_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.15")
               .names("supportedLDAPVersion")
               .syntax(SYNTAX_INTEGER_OID)
               .usage(AttributeUsage.DSA_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("1.3.6.1.4.1.1466.101.120.16")
               .names("ldapSyntaxes")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_LDAP_SYNTAX_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.1")
               .names("ditStructureRules")
               .equalityMatchingRule(EMR_INTEGER_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_DIT_STRUCTURE_RULE_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.7")
               .names("nameForms")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_NAME_FORM_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();

        builder.buildAttributeType("2.5.21.2")
               .names("ditContentRules")
               .equalityMatchingRule(EMR_OID_FIRST_COMPONENT_NAME)
               .syntax(SYNTAX_DIT_CONTENT_RULE_OID)
               .usage(AttributeUsage.DIRECTORY_OPERATION)
               .extraProperties(RFC4512_ORIGIN)
               .addToSchema();
    }

    private static void defaultMatchingRules(final SchemaBuilder builder) {
        builder.buildMatchingRule(EMR_BIT_STRING_OID)
               .names(EMR_BIT_STRING_NAME)
               .syntaxOID(SYNTAX_BIT_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new BitStringEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_BOOLEAN_OID)
               .names(EMR_BOOLEAN_NAME)
               .syntaxOID(SYNTAX_BOOLEAN_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new BooleanEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CASE_EXACT_IA5_OID)
               .names(EMR_CASE_EXACT_IA5_NAME)
               .syntaxOID(SYNTAX_IA5_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseExactIA5EqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_CASE_EXACT_IA5_OID)
               .names(SMR_CASE_EXACT_IA5_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseExactIA5SubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CASE_EXACT_OID)
               .names(EMR_CASE_EXACT_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseExactEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_CASE_EXACT_OID)
               .names(OMR_CASE_EXACT_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseExactOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_CASE_EXACT_OID)
               .names(SMR_CASE_EXACT_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseExactSubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CASE_IGNORE_IA5_OID)
               .names(EMR_CASE_IGNORE_IA5_NAME)
               .syntaxOID(SYNTAX_IA5_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreIA5EqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_CASE_IGNORE_IA5_OID)
               .names(SMR_CASE_IGNORE_IA5_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreIA5SubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CASE_IGNORE_LIST_OID)
               .names(EMR_CASE_IGNORE_LIST_NAME)
               .syntaxOID(SYNTAX_POSTAL_ADDRESS_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreListEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_CASE_IGNORE_LIST_OID)
               .names(SMR_CASE_IGNORE_LIST_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreListSubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_CASE_IGNORE_OID)
               .names(EMR_CASE_IGNORE_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_CASE_IGNORE_OID)
               .names(OMR_CASE_IGNORE_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_CASE_IGNORE_OID)
               .names(SMR_CASE_IGNORE_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CaseIgnoreSubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_DIRECTORY_STRING_FIRST_COMPONENT_OID)
               .names(singletonList(EMR_DIRECTORY_STRING_FIRST_COMPONENT_NAME))
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DirectoryStringFirstComponentEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_DN_OID)
               .names(EMR_DN_NAME)
               .syntaxOID(SYNTAX_DN_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DistinguishedNameEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_GENERALIZED_TIME_OID)
               .names(EMR_GENERALIZED_TIME_NAME)
               .syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new GeneralizedTimeEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_GENERALIZED_TIME_OID)
               .names(OMR_GENERALIZED_TIME_NAME)
               .syntaxOID(SYNTAX_GENERALIZED_TIME_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new GeneralizedTimeOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_INTEGER_FIRST_COMPONENT_OID)
               .names(EMR_INTEGER_FIRST_COMPONENT_NAME)
               .syntaxOID(SYNTAX_INTEGER_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new IntegerFirstComponentEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_INTEGER_OID)
               .names(EMR_INTEGER_NAME)
               .syntaxOID(SYNTAX_INTEGER_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new IntegerEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_INTEGER_OID)
               .names(OMR_INTEGER_NAME)
               .syntaxOID(SYNTAX_INTEGER_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new IntegerOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_KEYWORD_OID)
               .names(EMR_KEYWORD_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new KeywordEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_NUMERIC_STRING_OID)
               .names(EMR_NUMERIC_STRING_NAME)
               .syntaxOID(SYNTAX_NUMERIC_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new NumericStringEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_NUMERIC_STRING_OID)
               .names(OMR_NUMERIC_STRING_NAME)
               .syntaxOID(SYNTAX_NUMERIC_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new NumericStringOrderingMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_NUMERIC_STRING_OID)
               .names(SMR_NUMERIC_STRING_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new NumericStringSubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_OID_FIRST_COMPONENT_OID)
               .names(EMR_OID_FIRST_COMPONENT_NAME)
               .syntaxOID(SYNTAX_OID_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new ObjectIdentifierFirstComponentEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_OID_OID)
               .names(EMR_OID_NAME)
               .syntaxOID(SYNTAX_OID_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new ObjectIdentifierEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_OCTET_STRING_OID)
               .names(EMR_OCTET_STRING_NAME)
               .syntaxOID(SYNTAX_OCTET_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new OctetStringEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(OMR_OCTET_STRING_OID)
               .names(OMR_OCTET_STRING_NAME)
               .syntaxOID(SYNTAX_OCTET_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new OctetStringOrderingMatchingRuleImpl())
               .addToSchema();

        // SMR octet string is not in any LDAP RFC and its from X.500
        builder.buildMatchingRule(SMR_OCTET_STRING_OID)
               .names(SMR_OCTET_STRING_NAME)
               .syntaxOID(SYNTAX_OCTET_STRING_OID)
               .extraProperties(X500_ORIGIN)
               .implementation(new OctetStringSubstringMatchingRuleImpl())
               .addToSchema();

        // Depreciated in RFC 4512
        builder.buildMatchingRule(EMR_PROTOCOL_INFORMATION_OID)
               .names(EMR_PROTOCOL_INFORMATION_NAME)
               .syntaxOID(SYNTAX_PROTOCOL_INFORMATION_OID)
               .extraProperties(RFC2252_ORIGIN)
               .implementation(new ProtocolInformationEqualityMatchingRuleImpl())
               .addToSchema();

        // Depreciated in RFC 4512
        builder.buildMatchingRule(EMR_PRESENTATION_ADDRESS_OID)
               .names(EMR_PRESENTATION_ADDRESS_NAME)
               .syntaxOID(SYNTAX_PRESENTATION_ADDRESS_OID)
               .extraProperties(RFC2252_ORIGIN)
               .implementation(new PresentationAddressEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_TELEPHONE_OID)
               .names(EMR_TELEPHONE_NAME)
               .syntaxOID(SYNTAX_TELEPHONE_OID)
               .extraProperties(RFC2252_ORIGIN)
               .implementation(new TelephoneNumberEqualityMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(SMR_TELEPHONE_OID)
               .names(SMR_TELEPHONE_NAME)
               .syntaxOID(SYNTAX_SUBSTRING_ASSERTION_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new TelephoneNumberSubstringMatchingRuleImpl())
               .addToSchema();

        builder.buildMatchingRule(EMR_UNIQUE_MEMBER_OID)
                .names(EMR_UNIQUE_MEMBER_NAME)
                .syntaxOID(SYNTAX_NAME_AND_OPTIONAL_UID_OID)
                .extraProperties(RFC4512_ORIGIN)
                .implementation(new UniqueMemberEqualityMatchingRuleImpl())
                .addToSchema();

        builder.buildMatchingRule(EMR_WORD_OID)
               .names(EMR_WORD_NAME)
               .syntaxOID(SYNTAX_DIRECTORY_STRING_OID)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new KeywordEqualityMatchingRuleImpl())
               .addToSchema();
    }

    private static void defaultObjectClasses(final SchemaBuilder builder) {
        builder.buildObjectClass(TOP_OBJECTCLASS_OID)
                .names(TOP_OBJECTCLASS_NAME)
                .type(ABSTRACT)
                .description(TOP_OBJECTCLASS_DESCRIPTION)
                .requiredAttributes("objectClass")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.6.1")
                .names("alias")
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .requiredAttributes("aliasedObjectName")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID)
                .names(EXTENSIBLE_OBJECT_OBJECTCLASS_NAME)
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();

        builder.buildObjectClass("2.5.20.1")
                .names("subschema")
                .type(AUXILIARY)
                .superiorObjectClasses(TOP_OBJECTCLASS_NAME)
                .optionalAttributes("dITStructureRules", "nameForms", "ditContentRules", "objectClasses",
                    "attributeTypes", "matchingRules", "matchingRuleUse")
                .extraProperties(RFC4512_ORIGIN)
                .addToSchema();
    }

    private static void defaultSyntaxes(final SchemaBuilder builder) {
        // All RFC 4512 / 4517
        builder.buildSyntax(SYNTAX_ATTRIBUTE_TYPE_OID)
               .description(SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new AttributeTypeSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_BINARY_OID)
               .description(SYNTAX_BINARY_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new BinarySyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_BIT_STRING_OID)
               .description(SYNTAX_BIT_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new BitStringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_BOOLEAN_OID)
               .description(SYNTAX_BOOLEAN_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new BooleanSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_COUNTRY_STRING_OID)
               .description(SYNTAX_COUNTRY_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new CountryStringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_DELIVERY_METHOD_OID)
               .description(SYNTAX_DELIVERY_METHOD_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DeliveryMethodSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_DIRECTORY_STRING_OID)
               .description(SYNTAX_DIRECTORY_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DirectoryStringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_DIT_CONTENT_RULE_OID)
               .description(SYNTAX_DIT_CONTENT_RULE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DITContentRuleSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_DIT_STRUCTURE_RULE_OID)
               .description(SYNTAX_DIT_STRUCTURE_RULE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DITStructureRuleSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_DN_OID)
               .description(SYNTAX_DN_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new DistinguishedNameSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_ENHANCED_GUIDE_OID)
               .description(SYNTAX_ENHANCED_GUIDE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new EnhancedGuideSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_FAXNUMBER_OID)
               .description(SYNTAX_FAXNUMBER_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new FacsimileNumberSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_FAX_OID)
               .description(SYNTAX_FAX_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new FaxSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_GENERALIZED_TIME_OID)
               .description(SYNTAX_GENERALIZED_TIME_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new GeneralizedTimeSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_GUIDE_OID)
               .description(SYNTAX_GUIDE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new GuideSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_IA5_STRING_OID)
               .description(SYNTAX_IA5_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new IA5StringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_INTEGER_OID)
               .description(SYNTAX_INTEGER_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new IntegerSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_JPEG_OID)
               .description(SYNTAX_JPEG_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new JPEGSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_MATCHING_RULE_OID)
               .description(SYNTAX_MATCHING_RULE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new MatchingRuleSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_MATCHING_RULE_USE_OID)
               .description(SYNTAX_MATCHING_RULE_USE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new MatchingRuleUseSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_LDAP_SYNTAX_OID)
               .description(SYNTAX_LDAP_SYNTAX_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new LDAPSyntaxDescriptionSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_NAME_AND_OPTIONAL_UID_OID)
               .description(SYNTAX_NAME_AND_OPTIONAL_UID_DESCRIPTION)
               .extraProperties(RFC4517_ORIGIN)
               .implementation(new NameAndOptionalUIDSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_NAME_FORM_OID)
               .description(SYNTAX_NAME_FORM_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new NameFormSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_NUMERIC_STRING_OID)
               .description(SYNTAX_NUMERIC_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new NumericStringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_OBJECTCLASS_OID)
               .description(SYNTAX_OBJECTCLASS_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new ObjectClassSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_OCTET_STRING_OID)
               .description(SYNTAX_OCTET_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new OctetStringSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_OID_OID)
               .description(SYNTAX_OID_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new OIDSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_OTHER_MAILBOX_OID)
               .description(SYNTAX_OTHER_MAILBOX_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new OtherMailboxSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_POSTAL_ADDRESS_OID)
               .description(SYNTAX_POSTAL_ADDRESS_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new PostalAddressSyntaxImpl())
               .addToSchema();

        // Depreciated in RFC 4512
        builder.buildSyntax(SYNTAX_PRESENTATION_ADDRESS_OID)
               .description(SYNTAX_PRESENTATION_ADDRESS_DESCRIPTION)
               .extraProperties(RFC2252_ORIGIN)
               .implementation(new PresentationAddressSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_PRINTABLE_STRING_OID)
               .description(SYNTAX_PRINTABLE_STRING_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new PrintableStringSyntaxImpl())
               .addToSchema();

        // Depreciated in RFC 4512
        builder.buildSyntax(SYNTAX_PROTOCOL_INFORMATION_OID)
               .description(SYNTAX_PROTOCOL_INFORMATION_DESCRIPTION)
               .extraProperties(RFC2252_ORIGIN)
               .implementation(new ProtocolInformationSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_SUBSTRING_ASSERTION_OID)
               .description(SYNTAX_SUBSTRING_ASSERTION_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new SubstringAssertionSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_TELEPHONE_OID)
               .description(SYNTAX_TELEPHONE_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new TelephoneNumberSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_TELETEX_TERM_ID_OID)
               .description(SYNTAX_TELETEX_TERM_ID_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new TeletexTerminalIdentifierSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_TELEX_OID)
               .description(SYNTAX_TELEX_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new TelexNumberSyntaxImpl())
               .addToSchema();

        builder.buildSyntax(SYNTAX_UTC_TIME_OID)
               .description(SYNTAX_UTC_TIME_DESCRIPTION)
               .extraProperties(RFC4512_ORIGIN)
               .implementation(new UTCTimeSyntaxImpl())
               .addToSchema();

    }

    private CoreSchemaImpl() {
        // Prevent instantiation.
    }
}
