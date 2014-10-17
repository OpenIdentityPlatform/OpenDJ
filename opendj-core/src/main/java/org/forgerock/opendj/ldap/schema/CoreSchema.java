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
 *      Portions copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;


// DON'T EDIT THIS FILE!
// It is automatically generated using GenerateCoreSchema class.

/**
 * The OpenDJ SDK core schema contains standard LDAP RFC schema elements. These include:
 * <ul>
 * <li><a href="http://tools.ietf.org/html/rfc4512">RFC 4512 -
 * Lightweight Directory Access Protocol (LDAP): Directory Information
 * Models </a>
 * <li><a href="http://tools.ietf.org/html/rfc4517">RFC 4517 -
 * Lightweight Directory Access Protocol (LDAP): Syntaxes and Matching
 * Rules </a>
 * <li><a href="http://tools.ietf.org/html/rfc4519">RFC 4519 -
 * Lightweight Directory Access Protocol (LDAP): Schema for User
 * Applications </a>
 * <li><a href="http://tools.ietf.org/html/rfc4530">RFC 4530 -
 * Lightweight Directory Access Protocol (LDAP): entryUUID Operational
 * Attribute </a>
 * <li><a href="http://tools.ietf.org/html/rfc3045">RFC 3045 - Storing
 * Vendor Information in the LDAP Root DSE </a>
 * <li><a href="http://tools.ietf.org/html/rfc3112">RFC 3112 - LDAP
 * Authentication Password Schema </a>
 * </ul>
 * <p>
 * The core schema is non-strict: attempts to retrieve
 * non-existent Attribute Types will return a temporary
 * Attribute Type having the Octet String syntax.
 */
public final class CoreSchema {
    // Core Syntaxes
    private static final Syntax ATTRIBUTE_TYPE_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.3");
    private static final Syntax AUTHENTICATION_PASSWORD_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.4203.1.1.2");
    private static final Syntax BINARY_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.5");
    private static final Syntax BIT_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.6");
    private static final Syntax BOOLEAN_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.7");
    private static final Syntax CERTIFICATE_LIST_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.9");
    private static final Syntax CERTIFICATE_PAIR_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.10");
    private static final Syntax CERTIFICATE_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.8");
    private static final Syntax COUNTRY_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.11");
    private static final Syntax DELIVERY_METHOD_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.14");
    private static final Syntax DIRECTORY_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.15");
    private static final Syntax DIT_CONTENT_RULE_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.16");
    private static final Syntax DIT_STRUCTURE_RULE_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.17");
    private static final Syntax DN_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.12");
    private static final Syntax ENHANCED_GUIDE_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.21");
    private static final Syntax FACSIMILE_TELEPHONE_NUMBER_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.22");
    private static final Syntax FAX_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.23");
    private static final Syntax GENERALIZED_TIME_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.24");
    private static final Syntax GUIDE_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.25");
    private static final Syntax IA5_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.26");
    private static final Syntax INTEGER_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.27");
    private static final Syntax JPEG_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.28");
    private static final Syntax LDAP_SYNTAX_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.54");
    private static final Syntax MATCHING_RULE_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.30");
    private static final Syntax MATCHING_RULE_USE_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.31");
    private static final Syntax NAME_AND_OPTIONAL_UID_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.34");
    private static final Syntax NAME_FORM_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.35");
    private static final Syntax NUMERIC_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.36");
    private static final Syntax OBJECT_CLASS_DESCRIPTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.37");
    private static final Syntax OCTET_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.40");
    private static final Syntax OID_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.38");
    private static final Syntax OTHER_MAILBOX_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.39");
    private static final Syntax POSTAL_ADDRESS_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.41");
    private static final Syntax PRESENTATION_ADDRESS_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.43");
    private static final Syntax PRINTABLE_STRING_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.44");
    private static final Syntax PROTOCOL_INFORMATION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.42");
    private static final Syntax SUBSTRING_ASSERTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.58");
    private static final Syntax SUPPORTED_ALGORITHM_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.49");
    private static final Syntax TELEPHONE_NUMBER_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.50");
    private static final Syntax TELETEX_TERMINAL_IDENTIFIER_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.51");
    private static final Syntax TELEX_NUMBER_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.52");
    private static final Syntax UTC_TIME_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.4.1.1466.115.121.1.53");
    private static final Syntax UUID_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.1.16.1");
    private static final Syntax X509_CERTIFICATE_EXACT_ASSERTION_SYNTAX =
        CoreSchemaImpl.getInstance().getSyntax("1.3.6.1.1.15.1");

    // Core Matching Rules
    private static final MatchingRule AUTH_PASSWORD_EXACT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.4.1.4203.1.2.2");
    private static final MatchingRule BIT_STRING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.16");
    private static final MatchingRule BOOLEAN_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.13");
    private static final MatchingRule CASE_EXACT_IA5_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.4.1.1466.109.114.1");
    private static final MatchingRule CASE_EXACT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.5");
    private static final MatchingRule CASE_EXACT_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.6");
    private static final MatchingRule CASE_EXACT_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.7");
    private static final MatchingRule CASE_IGNORE_IA5_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.4.1.1466.109.114.2");
    private static final MatchingRule CASE_IGNORE_IA5_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.4.1.1466.109.114.3");
    private static final MatchingRule CASE_IGNORE_LIST_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.11");
    private static final MatchingRule CASE_IGNORE_LIST_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.12");
    private static final MatchingRule CASE_IGNORE_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.2");
    private static final MatchingRule CASE_IGNORE_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.3");
    private static final MatchingRule CASE_IGNORE_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.4");
    private static final MatchingRule CERTIFICATE_EXACT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.34");
    private static final MatchingRule DIRECTORY_STRING_FIRST_COMPONENT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.31");
    private static final MatchingRule DISTINGUISHED_NAME_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.1");
    private static final MatchingRule GENERALIZED_TIME_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.27");
    private static final MatchingRule GENERALIZED_TIME_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.28");
    private static final MatchingRule INTEGER_FIRST_COMPONENT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.29");
    private static final MatchingRule INTEGER_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.14");
    private static final MatchingRule INTEGER_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.15");
    private static final MatchingRule KEYWORD_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.33");
    private static final MatchingRule NUMERIC_STRING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.8");
    private static final MatchingRule NUMERIC_STRING_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.9");
    private static final MatchingRule NUMERIC_STRING_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.10");
    private static final MatchingRule OBJECT_IDENTIFIER_FIRST_COMPONENT_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.30");
    private static final MatchingRule OBJECT_IDENTIFIER_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.0");
    private static final MatchingRule OCTET_STRING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.17");
    private static final MatchingRule OCTET_STRING_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.18");
    private static final MatchingRule OCTET_STRING_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.19");
    private static final MatchingRule PRESENTATION_ADDRESS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.22");
    private static final MatchingRule PROTOCOL_INFORMATION_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.24");
    private static final MatchingRule TELEPHONE_NUMBER_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.20");
    private static final MatchingRule TELEPHONE_NUMBER_SUBSTRINGS_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.21");
    private static final MatchingRule UNIQUE_MEMBER_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.23");
    private static final MatchingRule UUID_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.1.16.2");
    private static final MatchingRule UUID_ORDERING_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("1.3.6.1.1.16.3");
    private static final MatchingRule WORD_MATCHING_RULE =
        CoreSchemaImpl.getInstance().getMatchingRule("2.5.13.32");

    // Core Attribute Types
    private static final AttributeType ALIASED_OBJECT_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.1");
    private static final AttributeType ALT_SERVER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.6");
    private static final AttributeType ATTRIBUTE_TYPES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.5");
    private static final AttributeType AUTHORITY_REVOCATION_LIST_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.38");
    private static final AttributeType AUTH_PASSWORD_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.4203.1.3.4");
    private static final AttributeType BUSINESS_CATEGORY_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.15");
    private static final AttributeType CERTIFICATE_REVOCATION_LIST_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.39");
    private static final AttributeType CN_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.3");
    private static final AttributeType CREATE_TIMESTAMP_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.18.1");
    private static final AttributeType CREATORS_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.18.3");
    private static final AttributeType CROSS_CERTIFICATE_PAIR_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.40");
    private static final AttributeType C_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.6");
    private static final AttributeType C_A_CERTIFICATE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.37");
    private static final AttributeType DC_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("0.9.2342.19200300.100.1.25");
    private static final AttributeType DELTA_REVOCATION_LIST_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.53");
    private static final AttributeType DESCRIPTION_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.13");
    private static final AttributeType DESTINATION_INDICATOR_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.27");
    private static final AttributeType DISTINGUISHED_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.49");
    private static final AttributeType DIT_CONTENT_RULES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.2");
    private static final AttributeType DIT_STRUCTURE_RULES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.1");
    private static final AttributeType DN_QUALIFIER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.46");
    private static final AttributeType ENHANCED_SEARCH_GUIDE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.47");
    private static final AttributeType ENTRY_DN_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.1.20");
    private static final AttributeType ENTRY_UUID_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.1.16.4");
    private static final AttributeType FACSIMILE_TELEPHONE_NUMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.23");
    private static final AttributeType FULL_VENDOR_VERSION_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.36733.2.1.1.141");
    private static final AttributeType GENERATION_QUALIFIER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.44");
    private static final AttributeType GIVEN_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.42");
    private static final AttributeType GOVERNING_STRUCTURE_RULE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.10");
    private static final AttributeType HOUSE_IDENTIFIER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.51");
    private static final AttributeType INITIALS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.43");
    private static final AttributeType INTERNATIONAL_ISDN_NUMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.25");
    private static final AttributeType LDAP_SYNTAXES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.16");
    private static final AttributeType L_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.7");
    private static final AttributeType MATCHING_RULES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.4");
    private static final AttributeType MATCHING_RULE_USE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.8");
    private static final AttributeType MEMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.31");
    private static final AttributeType MODIFIERS_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.18.4");
    private static final AttributeType MODIFY_TIMESTAMP_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.18.2");
    private static final AttributeType NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.41");
    private static final AttributeType NAME_FORMS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.7");
    private static final AttributeType NAMING_CONTEXTS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.5");
    private static final AttributeType OBJECT_CLASSES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.6");
    private static final AttributeType OBJECT_CLASS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.0");
    private static final AttributeType OU_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.11");
    private static final AttributeType OWNER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.32");
    private static final AttributeType O_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.10");
    private static final AttributeType PHYSICAL_DELIVERY_OFFICE_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.19");
    private static final AttributeType POSTAL_ADDRESS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.16");
    private static final AttributeType POSTAL_CODE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.17");
    private static final AttributeType POST_OFFICE_BOX_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.18");
    private static final AttributeType PREFERRED_DELIVERY_METHOD_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.28");
    private static final AttributeType REGISTERED_ADDRESS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.26");
    private static final AttributeType ROLE_OCCUPANT_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.33");
    private static final AttributeType SEARCH_GUIDE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.14");
    private static final AttributeType SEE_ALSO_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.34");
    private static final AttributeType SERIAL_NUMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.5");
    private static final AttributeType SN_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.4");
    private static final AttributeType STREET_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.9");
    private static final AttributeType STRUCTURAL_OBJECT_CLASS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.21.9");
    private static final AttributeType ST_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.8");
    private static final AttributeType SUBSCHEMA_SUBENTRY_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.18.10");
    private static final AttributeType SUPPORTED_ALGORITHMS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.52");
    private static final AttributeType SUPPORTED_AUTH_PASSWORD_SCHEMES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.4203.1.3.3");
    private static final AttributeType SUPPORTED_CONTROL_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.13");
    private static final AttributeType SUPPORTED_EXTENSION_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.7");
    private static final AttributeType SUPPORTED_FEATURES_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.4203.1.3.5");
    private static final AttributeType SUPPORTED_LDAP_VERSION_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.15");
    private static final AttributeType SUPPORTED_SASL_MECHANISMS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.4.1.1466.101.120.14");
    private static final AttributeType TELEPHONE_NUMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.20");
    private static final AttributeType TELETEX_TERMINAL_IDENTIFIER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.22");
    private static final AttributeType TELEX_NUMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.21");
    private static final AttributeType TITLE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.12");
    private static final AttributeType UID_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("0.9.2342.19200300.100.1.1");
    private static final AttributeType UNIQUE_MEMBER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.50");
    private static final AttributeType USER_CERTIFICATE_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.36");
    private static final AttributeType USER_PASSWORD_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.35");
    private static final AttributeType VENDOR_NAME_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.1.4");
    private static final AttributeType VENDOR_VERSION_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("1.3.6.1.1.5");
    private static final AttributeType X121_ADDRESS_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.24");
    private static final AttributeType X500_UNIQUE_IDENTIFIER_ATTRIBUTE_TYPE =
        CoreSchemaImpl.getInstance().getAttributeType("2.5.4.45");

    // Core Object Classes
    private static final ObjectClass ALIAS_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.1");
    private static final ObjectClass APPLICATION_PROCESS_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.11");
    private static final ObjectClass AUTH_PASSWORD_OBJECT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("1.3.6.1.4.1.4203.1.4.7");
    private static final ObjectClass CERTIFICATION_AUTHORITY_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.16");
    private static final ObjectClass CERTIFICATION_AUTHORITY_V2_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.16.2");
    private static final ObjectClass COUNTRY_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.2");
    private static final ObjectClass C_RL_DISTRIBUTION_POINT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.19");
    private static final ObjectClass DC_OBJECT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("1.3.6.1.4.1.1466.344");
    private static final ObjectClass DELTA_CRL_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.23");
    private static final ObjectClass DEVICE_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.14");
    private static final ObjectClass EXTENSIBLE_OBJECT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("1.3.6.1.4.1.1466.101.120.111");
    private static final ObjectClass GROUP_OF_NAMES_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.9");
    private static final ObjectClass GROUP_OF_UNIQUE_NAMES_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.17");
    private static final ObjectClass LOCALITY_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.3");
    private static final ObjectClass ORGANIZATIONAL_PERSON_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.7");
    private static final ObjectClass ORGANIZATIONAL_ROLE_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.8");
    private static final ObjectClass ORGANIZATIONAL_UNIT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.5");
    private static final ObjectClass ORGANIZATION_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.4");
    private static final ObjectClass PERSON_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.6");
    private static final ObjectClass PKI_CA_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.22");
    private static final ObjectClass PKI_USER_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.21");
    private static final ObjectClass RESIDENTIAL_PERSON_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.10");
    private static final ObjectClass STRONG_AUTHENTICATION_USER_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.15");
    private static final ObjectClass SUBSCHEMA_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.20.1");
    private static final ObjectClass TOP_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.0");
    private static final ObjectClass UID_OBJECT_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("1.3.6.1.1.3.1");
    private static final ObjectClass USER_SECURITY_INFORMATION_OBJECT_CLASS =
        CoreSchemaImpl.getInstance().getObjectClass("2.5.6.18");

    // Prevent instantiation
    private CoreSchema() {
      // Nothing to do.
    }

    /**
     * Returns a reference to the singleton core schema.
     *
     * @return The core schema.
     */
    public static Schema getInstance() {
        return CoreSchemaImpl.getInstance();
    }

    /**
     * Returns a reference to the {@code Attribute Type Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.3}.
     *
     * @return A reference to the {@code Attribute Type Description Syntax}.
     */
    public static Syntax getAttributeTypeDescriptionSyntax() {
        return ATTRIBUTE_TYPE_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Authentication Password Syntax}
     * which has the OID {@code 1.3.6.1.4.1.4203.1.1.2}.
     *
     * @return A reference to the {@code Authentication Password Syntax}.
     */
    public static Syntax getAuthenticationPasswordSyntax() {
        return AUTHENTICATION_PASSWORD_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Binary Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.5}.
     *
     * @return A reference to the {@code Binary Syntax}.
     */
    public static Syntax getBinarySyntax() {
        return BINARY_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Bit String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.6}.
     *
     * @return A reference to the {@code Bit String Syntax}.
     */
    public static Syntax getBitStringSyntax() {
        return BIT_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Boolean Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.7}.
     *
     * @return A reference to the {@code Boolean Syntax}.
     */
    public static Syntax getBooleanSyntax() {
        return BOOLEAN_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Certificate List Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.9}.
     *
     * @return A reference to the {@code Certificate List Syntax}.
     */
    public static Syntax getCertificateListSyntax() {
        return CERTIFICATE_LIST_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Certificate Pair Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.10}.
     *
     * @return A reference to the {@code Certificate Pair Syntax}.
     */
    public static Syntax getCertificatePairSyntax() {
        return CERTIFICATE_PAIR_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Certificate Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.8}.
     *
     * @return A reference to the {@code Certificate Syntax}.
     */
    public static Syntax getCertificateSyntax() {
        return CERTIFICATE_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Country String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.11}.
     *
     * @return A reference to the {@code Country String Syntax}.
     */
    public static Syntax getCountryStringSyntax() {
        return COUNTRY_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Delivery Method Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.14}.
     *
     * @return A reference to the {@code Delivery Method Syntax}.
     */
    public static Syntax getDeliveryMethodSyntax() {
        return DELIVERY_METHOD_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Directory String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.15}.
     *
     * @return A reference to the {@code Directory String Syntax}.
     */
    public static Syntax getDirectoryStringSyntax() {
        return DIRECTORY_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code DIT Content Rule Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.16}.
     *
     * @return A reference to the {@code DIT Content Rule Description Syntax}.
     */
    public static Syntax getDITContentRuleDescriptionSyntax() {
        return DIT_CONTENT_RULE_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code DIT Structure Rule Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.17}.
     *
     * @return A reference to the {@code DIT Structure Rule Description Syntax}.
     */
    public static Syntax getDITStructureRuleDescriptionSyntax() {
        return DIT_STRUCTURE_RULE_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code DN Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.12}.
     *
     * @return A reference to the {@code DN Syntax}.
     */
    public static Syntax getDNSyntax() {
        return DN_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Enhanced Guide Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.21}.
     *
     * @return A reference to the {@code Enhanced Guide Syntax}.
     */
    public static Syntax getEnhancedGuideSyntax() {
        return ENHANCED_GUIDE_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Facsimile Telephone Number Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.22}.
     *
     * @return A reference to the {@code Facsimile Telephone Number Syntax}.
     */
    public static Syntax getFacsimileTelephoneNumberSyntax() {
        return FACSIMILE_TELEPHONE_NUMBER_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Fax Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.23}.
     *
     * @return A reference to the {@code Fax Syntax}.
     */
    public static Syntax getFaxSyntax() {
        return FAX_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Generalized Time Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.24}.
     *
     * @return A reference to the {@code Generalized Time Syntax}.
     */
    public static Syntax getGeneralizedTimeSyntax() {
        return GENERALIZED_TIME_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Guide Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.25}.
     *
     * @return A reference to the {@code Guide Syntax}.
     */
    public static Syntax getGuideSyntax() {
        return GUIDE_SYNTAX;
    }

    /**
     * Returns a reference to the {@code IA5 String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.26}.
     *
     * @return A reference to the {@code IA5 String Syntax}.
     */
    public static Syntax getIA5StringSyntax() {
        return IA5_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Integer Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.27}.
     *
     * @return A reference to the {@code Integer Syntax}.
     */
    public static Syntax getIntegerSyntax() {
        return INTEGER_SYNTAX;
    }

    /**
     * Returns a reference to the {@code JPEG Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.28}.
     *
     * @return A reference to the {@code JPEG Syntax}.
     */
    public static Syntax getJPEGSyntax() {
        return JPEG_SYNTAX;
    }

    /**
     * Returns a reference to the {@code LDAP Syntax Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.54}.
     *
     * @return A reference to the {@code LDAP Syntax Description Syntax}.
     */
    public static Syntax getLDAPSyntaxDescriptionSyntax() {
        return LDAP_SYNTAX_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Matching Rule Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.30}.
     *
     * @return A reference to the {@code Matching Rule Description Syntax}.
     */
    public static Syntax getMatchingRuleDescriptionSyntax() {
        return MATCHING_RULE_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Matching Rule Use Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.31}.
     *
     * @return A reference to the {@code Matching Rule Use Description Syntax}.
     */
    public static Syntax getMatchingRuleUseDescriptionSyntax() {
        return MATCHING_RULE_USE_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Name and Optional UID Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.34}.
     *
     * @return A reference to the {@code Name and Optional UID Syntax}.
     */
    public static Syntax getNameAndOptionalUIDSyntax() {
        return NAME_AND_OPTIONAL_UID_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Name Form Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.35}.
     *
     * @return A reference to the {@code Name Form Description Syntax}.
     */
    public static Syntax getNameFormDescriptionSyntax() {
        return NAME_FORM_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Numeric String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.36}.
     *
     * @return A reference to the {@code Numeric String Syntax}.
     */
    public static Syntax getNumericStringSyntax() {
        return NUMERIC_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Object Class Description Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.37}.
     *
     * @return A reference to the {@code Object Class Description Syntax}.
     */
    public static Syntax getObjectClassDescriptionSyntax() {
        return OBJECT_CLASS_DESCRIPTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Octet String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.40}.
     *
     * @return A reference to the {@code Octet String Syntax}.
     */
    public static Syntax getOctetStringSyntax() {
        return OCTET_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code OID Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.38}.
     *
     * @return A reference to the {@code OID Syntax}.
     */
    public static Syntax getOIDSyntax() {
        return OID_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Other Mailbox Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.39}.
     *
     * @return A reference to the {@code Other Mailbox Syntax}.
     */
    public static Syntax getOtherMailboxSyntax() {
        return OTHER_MAILBOX_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Postal Address Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.41}.
     *
     * @return A reference to the {@code Postal Address Syntax}.
     */
    public static Syntax getPostalAddressSyntax() {
        return POSTAL_ADDRESS_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Presentation Address Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.43}.
     *
     * @return A reference to the {@code Presentation Address Syntax}.
     */
    public static Syntax getPresentationAddressSyntax() {
        return PRESENTATION_ADDRESS_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Printable String Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.44}.
     *
     * @return A reference to the {@code Printable String Syntax}.
     */
    public static Syntax getPrintableStringSyntax() {
        return PRINTABLE_STRING_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Protocol Information Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.42}.
     *
     * @return A reference to the {@code Protocol Information Syntax}.
     */
    public static Syntax getProtocolInformationSyntax() {
        return PROTOCOL_INFORMATION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Substring Assertion Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.58}.
     *
     * @return A reference to the {@code Substring Assertion Syntax}.
     */
    public static Syntax getSubstringAssertionSyntax() {
        return SUBSTRING_ASSERTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Supported Algorithm Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.49}.
     *
     * @return A reference to the {@code Supported Algorithm Syntax}.
     */
    public static Syntax getSupportedAlgorithmSyntax() {
        return SUPPORTED_ALGORITHM_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Telephone Number Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.50}.
     *
     * @return A reference to the {@code Telephone Number Syntax}.
     */
    public static Syntax getTelephoneNumberSyntax() {
        return TELEPHONE_NUMBER_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Teletex Terminal Identifier Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.51}.
     *
     * @return A reference to the {@code Teletex Terminal Identifier Syntax}.
     */
    public static Syntax getTeletexTerminalIdentifierSyntax() {
        return TELETEX_TERMINAL_IDENTIFIER_SYNTAX;
    }

    /**
     * Returns a reference to the {@code Telex Number Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.52}.
     *
     * @return A reference to the {@code Telex Number Syntax}.
     */
    public static Syntax getTelexNumberSyntax() {
        return TELEX_NUMBER_SYNTAX;
    }

    /**
     * Returns a reference to the {@code UTC Time Syntax}
     * which has the OID {@code 1.3.6.1.4.1.1466.115.121.1.53}.
     *
     * @return A reference to the {@code UTC Time Syntax}.
     */
    public static Syntax getUTCTimeSyntax() {
        return UTC_TIME_SYNTAX;
    }

    /**
     * Returns a reference to the {@code UUID Syntax}
     * which has the OID {@code 1.3.6.1.1.16.1}.
     *
     * @return A reference to the {@code UUID Syntax}.
     */
    public static Syntax getUUIDSyntax() {
        return UUID_SYNTAX;
    }

    /**
     * Returns a reference to the {@code X.509 Certificate Exact Assertion Syntax}
     * which has the OID {@code 1.3.6.1.1.15.1}.
     *
     * @return A reference to the {@code X.509 Certificate Exact Assertion Syntax}.
     */
    public static Syntax getX509CertificateExactAssertionSyntax() {
        return X509_CERTIFICATE_EXACT_ASSERTION_SYNTAX;
    }

    /**
     * Returns a reference to the {@code authPasswordExactMatch} Matching Rule
     * which has the OID {@code 1.3.6.1.4.1.4203.1.2.2}.
     *
     * @return A reference to the {@code authPasswordExactMatch} Matching Rule.
     */
    public static MatchingRule getAuthPasswordExactMatchingRule() {
        return AUTH_PASSWORD_EXACT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code bitStringMatch} Matching Rule
     * which has the OID {@code 2.5.13.16}.
     *
     * @return A reference to the {@code bitStringMatch} Matching Rule.
     */
    public static MatchingRule getBitStringMatchingRule() {
        return BIT_STRING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code booleanMatch} Matching Rule
     * which has the OID {@code 2.5.13.13}.
     *
     * @return A reference to the {@code booleanMatch} Matching Rule.
     */
    public static MatchingRule getBooleanMatchingRule() {
        return BOOLEAN_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseExactIA5Match} Matching Rule
     * which has the OID {@code 1.3.6.1.4.1.1466.109.114.1}.
     *
     * @return A reference to the {@code caseExactIA5Match} Matching Rule.
     */
    public static MatchingRule getCaseExactIA5MatchingRule() {
        return CASE_EXACT_IA5_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseExactMatch} Matching Rule
     * which has the OID {@code 2.5.13.5}.
     *
     * @return A reference to the {@code caseExactMatch} Matching Rule.
     */
    public static MatchingRule getCaseExactMatchingRule() {
        return CASE_EXACT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseExactOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.6}.
     *
     * @return A reference to the {@code caseExactOrderingMatch} Matching Rule.
     */
    public static MatchingRule getCaseExactOrderingMatchingRule() {
        return CASE_EXACT_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseExactSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.7}.
     *
     * @return A reference to the {@code caseExactSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getCaseExactSubstringsMatchingRule() {
        return CASE_EXACT_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreIA5Match} Matching Rule
     * which has the OID {@code 1.3.6.1.4.1.1466.109.114.2}.
     *
     * @return A reference to the {@code caseIgnoreIA5Match} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreIA5MatchingRule() {
        return CASE_IGNORE_IA5_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreIA5SubstringsMatch} Matching Rule
     * which has the OID {@code 1.3.6.1.4.1.1466.109.114.3}.
     *
     * @return A reference to the {@code caseIgnoreIA5SubstringsMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreIA5SubstringsMatchingRule() {
        return CASE_IGNORE_IA5_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreListMatch} Matching Rule
     * which has the OID {@code 2.5.13.11}.
     *
     * @return A reference to the {@code caseIgnoreListMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreListMatchingRule() {
        return CASE_IGNORE_LIST_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreListSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.12}.
     *
     * @return A reference to the {@code caseIgnoreListSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreListSubstringsMatchingRule() {
        return CASE_IGNORE_LIST_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreMatch} Matching Rule
     * which has the OID {@code 2.5.13.2}.
     *
     * @return A reference to the {@code caseIgnoreMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreMatchingRule() {
        return CASE_IGNORE_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.3}.
     *
     * @return A reference to the {@code caseIgnoreOrderingMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreOrderingMatchingRule() {
        return CASE_IGNORE_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code caseIgnoreSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.4}.
     *
     * @return A reference to the {@code caseIgnoreSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getCaseIgnoreSubstringsMatchingRule() {
        return CASE_IGNORE_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code certificateExactMatch} Matching Rule
     * which has the OID {@code 2.5.13.34}.
     *
     * @return A reference to the {@code certificateExactMatch} Matching Rule.
     */
    public static MatchingRule getCertificateExactMatchingRule() {
        return CERTIFICATE_EXACT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code directoryStringFirstComponentMatch} Matching Rule
     * which has the OID {@code 2.5.13.31}.
     *
     * @return A reference to the {@code directoryStringFirstComponentMatch} Matching Rule.
     */
    public static MatchingRule getDirectoryStringFirstComponentMatchingRule() {
        return DIRECTORY_STRING_FIRST_COMPONENT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code distinguishedNameMatch} Matching Rule
     * which has the OID {@code 2.5.13.1}.
     *
     * @return A reference to the {@code distinguishedNameMatch} Matching Rule.
     */
    public static MatchingRule getDistinguishedNameMatchingRule() {
        return DISTINGUISHED_NAME_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code generalizedTimeMatch} Matching Rule
     * which has the OID {@code 2.5.13.27}.
     *
     * @return A reference to the {@code generalizedTimeMatch} Matching Rule.
     */
    public static MatchingRule getGeneralizedTimeMatchingRule() {
        return GENERALIZED_TIME_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code generalizedTimeOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.28}.
     *
     * @return A reference to the {@code generalizedTimeOrderingMatch} Matching Rule.
     */
    public static MatchingRule getGeneralizedTimeOrderingMatchingRule() {
        return GENERALIZED_TIME_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code integerFirstComponentMatch} Matching Rule
     * which has the OID {@code 2.5.13.29}.
     *
     * @return A reference to the {@code integerFirstComponentMatch} Matching Rule.
     */
    public static MatchingRule getIntegerFirstComponentMatchingRule() {
        return INTEGER_FIRST_COMPONENT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code integerMatch} Matching Rule
     * which has the OID {@code 2.5.13.14}.
     *
     * @return A reference to the {@code integerMatch} Matching Rule.
     */
    public static MatchingRule getIntegerMatchingRule() {
        return INTEGER_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code integerOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.15}.
     *
     * @return A reference to the {@code integerOrderingMatch} Matching Rule.
     */
    public static MatchingRule getIntegerOrderingMatchingRule() {
        return INTEGER_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code keywordMatch} Matching Rule
     * which has the OID {@code 2.5.13.33}.
     *
     * @return A reference to the {@code keywordMatch} Matching Rule.
     */
    public static MatchingRule getKeywordMatchingRule() {
        return KEYWORD_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code numericStringMatch} Matching Rule
     * which has the OID {@code 2.5.13.8}.
     *
     * @return A reference to the {@code numericStringMatch} Matching Rule.
     */
    public static MatchingRule getNumericStringMatchingRule() {
        return NUMERIC_STRING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code numericStringOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.9}.
     *
     * @return A reference to the {@code numericStringOrderingMatch} Matching Rule.
     */
    public static MatchingRule getNumericStringOrderingMatchingRule() {
        return NUMERIC_STRING_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code numericStringSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.10}.
     *
     * @return A reference to the {@code numericStringSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getNumericStringSubstringsMatchingRule() {
        return NUMERIC_STRING_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code objectIdentifierFirstComponentMatch} Matching Rule
     * which has the OID {@code 2.5.13.30}.
     *
     * @return A reference to the {@code objectIdentifierFirstComponentMatch} Matching Rule.
     */
    public static MatchingRule getObjectIdentifierFirstComponentMatchingRule() {
        return OBJECT_IDENTIFIER_FIRST_COMPONENT_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code objectIdentifierMatch} Matching Rule
     * which has the OID {@code 2.5.13.0}.
     *
     * @return A reference to the {@code objectIdentifierMatch} Matching Rule.
     */
    public static MatchingRule getObjectIdentifierMatchingRule() {
        return OBJECT_IDENTIFIER_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code octetStringMatch} Matching Rule
     * which has the OID {@code 2.5.13.17}.
     *
     * @return A reference to the {@code octetStringMatch} Matching Rule.
     */
    public static MatchingRule getOctetStringMatchingRule() {
        return OCTET_STRING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code octetStringOrderingMatch} Matching Rule
     * which has the OID {@code 2.5.13.18}.
     *
     * @return A reference to the {@code octetStringOrderingMatch} Matching Rule.
     */
    public static MatchingRule getOctetStringOrderingMatchingRule() {
        return OCTET_STRING_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code octetStringSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.19}.
     *
     * @return A reference to the {@code octetStringSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getOctetStringSubstringsMatchingRule() {
        return OCTET_STRING_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code presentationAddressMatch} Matching Rule
     * which has the OID {@code 2.5.13.22}.
     *
     * @return A reference to the {@code presentationAddressMatch} Matching Rule.
     */
    public static MatchingRule getPresentationAddressMatchingRule() {
        return PRESENTATION_ADDRESS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code protocolInformationMatch} Matching Rule
     * which has the OID {@code 2.5.13.24}.
     *
     * @return A reference to the {@code protocolInformationMatch} Matching Rule.
     */
    public static MatchingRule getProtocolInformationMatchingRule() {
        return PROTOCOL_INFORMATION_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code telephoneNumberMatch} Matching Rule
     * which has the OID {@code 2.5.13.20}.
     *
     * @return A reference to the {@code telephoneNumberMatch} Matching Rule.
     */
    public static MatchingRule getTelephoneNumberMatchingRule() {
        return TELEPHONE_NUMBER_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code telephoneNumberSubstringsMatch} Matching Rule
     * which has the OID {@code 2.5.13.21}.
     *
     * @return A reference to the {@code telephoneNumberSubstringsMatch} Matching Rule.
     */
    public static MatchingRule getTelephoneNumberSubstringsMatchingRule() {
        return TELEPHONE_NUMBER_SUBSTRINGS_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code uniqueMemberMatch} Matching Rule
     * which has the OID {@code 2.5.13.23}.
     *
     * @return A reference to the {@code uniqueMemberMatch} Matching Rule.
     */
    public static MatchingRule getUniqueMemberMatchingRule() {
        return UNIQUE_MEMBER_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code uuidMatch} Matching Rule
     * which has the OID {@code 1.3.6.1.1.16.2}.
     *
     * @return A reference to the {@code uuidMatch} Matching Rule.
     */
    public static MatchingRule getUUIDMatchingRule() {
        return UUID_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code uuidOrderingMatch} Matching Rule
     * which has the OID {@code 1.3.6.1.1.16.3}.
     *
     * @return A reference to the {@code uuidOrderingMatch} Matching Rule.
     */
    public static MatchingRule getUUIDOrderingMatchingRule() {
        return UUID_ORDERING_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code wordMatch} Matching Rule
     * which has the OID {@code 2.5.13.32}.
     *
     * @return A reference to the {@code wordMatch} Matching Rule.
     */
    public static MatchingRule getWordMatchingRule() {
        return WORD_MATCHING_RULE;
    }

    /**
     * Returns a reference to the {@code aliasedObjectName} Attribute Type
     * which has the OID {@code 2.5.4.1}.
     *
     * @return A reference to the {@code aliasedObjectName} Attribute Type.
     */
    public static AttributeType getAliasedObjectNameAttributeType() {
        return ALIASED_OBJECT_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code altServer} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.6}.
     *
     * @return A reference to the {@code altServer} Attribute Type.
     */
    public static AttributeType getAltServerAttributeType() {
        return ALT_SERVER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code attributeTypes} Attribute Type
     * which has the OID {@code 2.5.21.5}.
     *
     * @return A reference to the {@code attributeTypes} Attribute Type.
     */
    public static AttributeType getAttributeTypesAttributeType() {
        return ATTRIBUTE_TYPES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code authorityRevocationList} Attribute Type
     * which has the OID {@code 2.5.4.38}.
     *
     * @return A reference to the {@code authorityRevocationList} Attribute Type.
     */
    public static AttributeType getAuthorityRevocationListAttributeType() {
        return AUTHORITY_REVOCATION_LIST_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code authPassword} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.4203.1.3.4}.
     *
     * @return A reference to the {@code authPassword} Attribute Type.
     */
    public static AttributeType getAuthPasswordAttributeType() {
        return AUTH_PASSWORD_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code businessCategory} Attribute Type
     * which has the OID {@code 2.5.4.15}.
     *
     * @return A reference to the {@code businessCategory} Attribute Type.
     */
    public static AttributeType getBusinessCategoryAttributeType() {
        return BUSINESS_CATEGORY_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code certificateRevocationList} Attribute Type
     * which has the OID {@code 2.5.4.39}.
     *
     * @return A reference to the {@code certificateRevocationList} Attribute Type.
     */
    public static AttributeType getCertificateRevocationListAttributeType() {
        return CERTIFICATE_REVOCATION_LIST_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code cn} Attribute Type
     * which has the OID {@code 2.5.4.3}.
     *
     * @return A reference to the {@code cn} Attribute Type.
     */
    public static AttributeType getCNAttributeType() {
        return CN_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code createTimestamp} Attribute Type
     * which has the OID {@code 2.5.18.1}.
     *
     * @return A reference to the {@code createTimestamp} Attribute Type.
     */
    public static AttributeType getCreateTimestampAttributeType() {
        return CREATE_TIMESTAMP_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code creatorsName} Attribute Type
     * which has the OID {@code 2.5.18.3}.
     *
     * @return A reference to the {@code creatorsName} Attribute Type.
     */
    public static AttributeType getCreatorsNameAttributeType() {
        return CREATORS_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code crossCertificatePair} Attribute Type
     * which has the OID {@code 2.5.4.40}.
     *
     * @return A reference to the {@code crossCertificatePair} Attribute Type.
     */
    public static AttributeType getCrossCertificatePairAttributeType() {
        return CROSS_CERTIFICATE_PAIR_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code c} Attribute Type
     * which has the OID {@code 2.5.4.6}.
     *
     * @return A reference to the {@code c} Attribute Type.
     */
    public static AttributeType getCAttributeType() {
        return C_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code cACertificate} Attribute Type
     * which has the OID {@code 2.5.4.37}.
     *
     * @return A reference to the {@code cACertificate} Attribute Type.
     */
    public static AttributeType getCACertificateAttributeType() {
        return C_A_CERTIFICATE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code dc} Attribute Type
     * which has the OID {@code 0.9.2342.19200300.100.1.25}.
     *
     * @return A reference to the {@code dc} Attribute Type.
     */
    public static AttributeType getDCAttributeType() {
        return DC_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code deltaRevocationList} Attribute Type
     * which has the OID {@code 2.5.4.53}.
     *
     * @return A reference to the {@code deltaRevocationList} Attribute Type.
     */
    public static AttributeType getDeltaRevocationListAttributeType() {
        return DELTA_REVOCATION_LIST_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code description} Attribute Type
     * which has the OID {@code 2.5.4.13}.
     *
     * @return A reference to the {@code description} Attribute Type.
     */
    public static AttributeType getDescriptionAttributeType() {
        return DESCRIPTION_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code destinationIndicator} Attribute Type
     * which has the OID {@code 2.5.4.27}.
     *
     * @return A reference to the {@code destinationIndicator} Attribute Type.
     */
    public static AttributeType getDestinationIndicatorAttributeType() {
        return DESTINATION_INDICATOR_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code distinguishedName} Attribute Type
     * which has the OID {@code 2.5.4.49}.
     *
     * @return A reference to the {@code distinguishedName} Attribute Type.
     */
    public static AttributeType getDistinguishedNameAttributeType() {
        return DISTINGUISHED_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code ditContentRules} Attribute Type
     * which has the OID {@code 2.5.21.2}.
     *
     * @return A reference to the {@code ditContentRules} Attribute Type.
     */
    public static AttributeType getDITContentRulesAttributeType() {
        return DIT_CONTENT_RULES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code ditStructureRules} Attribute Type
     * which has the OID {@code 2.5.21.1}.
     *
     * @return A reference to the {@code ditStructureRules} Attribute Type.
     */
    public static AttributeType getDITStructureRulesAttributeType() {
        return DIT_STRUCTURE_RULES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code dnQualifier} Attribute Type
     * which has the OID {@code 2.5.4.46}.
     *
     * @return A reference to the {@code dnQualifier} Attribute Type.
     */
    public static AttributeType getDNQualifierAttributeType() {
        return DN_QUALIFIER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code enhancedSearchGuide} Attribute Type
     * which has the OID {@code 2.5.4.47}.
     *
     * @return A reference to the {@code enhancedSearchGuide} Attribute Type.
     */
    public static AttributeType getEnhancedSearchGuideAttributeType() {
        return ENHANCED_SEARCH_GUIDE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code entryDN} Attribute Type
     * which has the OID {@code 1.3.6.1.1.20}.
     *
     * @return A reference to the {@code entryDN} Attribute Type.
     */
    public static AttributeType getEntryDNAttributeType() {
        return ENTRY_DN_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code entryUUID} Attribute Type
     * which has the OID {@code 1.3.6.1.1.16.4}.
     *
     * @return A reference to the {@code entryUUID} Attribute Type.
     */
    public static AttributeType getEntryUUIDAttributeType() {
        return ENTRY_UUID_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code facsimileTelephoneNumber} Attribute Type
     * which has the OID {@code 2.5.4.23}.
     *
     * @return A reference to the {@code facsimileTelephoneNumber} Attribute Type.
     */
    public static AttributeType getFacsimileTelephoneNumberAttributeType() {
        return FACSIMILE_TELEPHONE_NUMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code fullVendorVersion} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.36733.2.1.1.141}.
     *
     * @return A reference to the {@code fullVendorVersion} Attribute Type.
     */
    public static AttributeType getFullVendorVersionAttributeType() {
        return FULL_VENDOR_VERSION_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code generationQualifier} Attribute Type
     * which has the OID {@code 2.5.4.44}.
     *
     * @return A reference to the {@code generationQualifier} Attribute Type.
     */
    public static AttributeType getGenerationQualifierAttributeType() {
        return GENERATION_QUALIFIER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code givenName} Attribute Type
     * which has the OID {@code 2.5.4.42}.
     *
     * @return A reference to the {@code givenName} Attribute Type.
     */
    public static AttributeType getGivenNameAttributeType() {
        return GIVEN_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code governingStructureRule} Attribute Type
     * which has the OID {@code 2.5.21.10}.
     *
     * @return A reference to the {@code governingStructureRule} Attribute Type.
     */
    public static AttributeType getGoverningStructureRuleAttributeType() {
        return GOVERNING_STRUCTURE_RULE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code houseIdentifier} Attribute Type
     * which has the OID {@code 2.5.4.51}.
     *
     * @return A reference to the {@code houseIdentifier} Attribute Type.
     */
    public static AttributeType getHouseIdentifierAttributeType() {
        return HOUSE_IDENTIFIER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code initials} Attribute Type
     * which has the OID {@code 2.5.4.43}.
     *
     * @return A reference to the {@code initials} Attribute Type.
     */
    public static AttributeType getInitialsAttributeType() {
        return INITIALS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code internationalISDNNumber} Attribute Type
     * which has the OID {@code 2.5.4.25}.
     *
     * @return A reference to the {@code internationalISDNNumber} Attribute Type.
     */
    public static AttributeType getInternationalISDNNumberAttributeType() {
        return INTERNATIONAL_ISDN_NUMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code ldapSyntaxes} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.16}.
     *
     * @return A reference to the {@code ldapSyntaxes} Attribute Type.
     */
    public static AttributeType getLDAPSyntaxesAttributeType() {
        return LDAP_SYNTAXES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code l} Attribute Type
     * which has the OID {@code 2.5.4.7}.
     *
     * @return A reference to the {@code l} Attribute Type.
     */
    public static AttributeType getLAttributeType() {
        return L_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code matchingRules} Attribute Type
     * which has the OID {@code 2.5.21.4}.
     *
     * @return A reference to the {@code matchingRules} Attribute Type.
     */
    public static AttributeType getMatchingRulesAttributeType() {
        return MATCHING_RULES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code matchingRuleUse} Attribute Type
     * which has the OID {@code 2.5.21.8}.
     *
     * @return A reference to the {@code matchingRuleUse} Attribute Type.
     */
    public static AttributeType getMatchingRuleUseAttributeType() {
        return MATCHING_RULE_USE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code member} Attribute Type
     * which has the OID {@code 2.5.4.31}.
     *
     * @return A reference to the {@code member} Attribute Type.
     */
    public static AttributeType getMemberAttributeType() {
        return MEMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code modifiersName} Attribute Type
     * which has the OID {@code 2.5.18.4}.
     *
     * @return A reference to the {@code modifiersName} Attribute Type.
     */
    public static AttributeType getModifiersNameAttributeType() {
        return MODIFIERS_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code modifyTimestamp} Attribute Type
     * which has the OID {@code 2.5.18.2}.
     *
     * @return A reference to the {@code modifyTimestamp} Attribute Type.
     */
    public static AttributeType getModifyTimestampAttributeType() {
        return MODIFY_TIMESTAMP_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code name} Attribute Type
     * which has the OID {@code 2.5.4.41}.
     *
     * @return A reference to the {@code name} Attribute Type.
     */
    public static AttributeType getNameAttributeType() {
        return NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code nameForms} Attribute Type
     * which has the OID {@code 2.5.21.7}.
     *
     * @return A reference to the {@code nameForms} Attribute Type.
     */
    public static AttributeType getNameFormsAttributeType() {
        return NAME_FORMS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code namingContexts} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.5}.
     *
     * @return A reference to the {@code namingContexts} Attribute Type.
     */
    public static AttributeType getNamingContextsAttributeType() {
        return NAMING_CONTEXTS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code objectClasses} Attribute Type
     * which has the OID {@code 2.5.21.6}.
     *
     * @return A reference to the {@code objectClasses} Attribute Type.
     */
    public static AttributeType getObjectClassesAttributeType() {
        return OBJECT_CLASSES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code objectClass} Attribute Type
     * which has the OID {@code 2.5.4.0}.
     *
     * @return A reference to the {@code objectClass} Attribute Type.
     */
    public static AttributeType getObjectClassAttributeType() {
        return OBJECT_CLASS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code ou} Attribute Type
     * which has the OID {@code 2.5.4.11}.
     *
     * @return A reference to the {@code ou} Attribute Type.
     */
    public static AttributeType getOUAttributeType() {
        return OU_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code owner} Attribute Type
     * which has the OID {@code 2.5.4.32}.
     *
     * @return A reference to the {@code owner} Attribute Type.
     */
    public static AttributeType getOwnerAttributeType() {
        return OWNER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code o} Attribute Type
     * which has the OID {@code 2.5.4.10}.
     *
     * @return A reference to the {@code o} Attribute Type.
     */
    public static AttributeType getOAttributeType() {
        return O_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code physicalDeliveryOfficeName} Attribute Type
     * which has the OID {@code 2.5.4.19}.
     *
     * @return A reference to the {@code physicalDeliveryOfficeName} Attribute Type.
     */
    public static AttributeType getPhysicalDeliveryOfficeNameAttributeType() {
        return PHYSICAL_DELIVERY_OFFICE_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code postalAddress} Attribute Type
     * which has the OID {@code 2.5.4.16}.
     *
     * @return A reference to the {@code postalAddress} Attribute Type.
     */
    public static AttributeType getPostalAddressAttributeType() {
        return POSTAL_ADDRESS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code postalCode} Attribute Type
     * which has the OID {@code 2.5.4.17}.
     *
     * @return A reference to the {@code postalCode} Attribute Type.
     */
    public static AttributeType getPostalCodeAttributeType() {
        return POSTAL_CODE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code postOfficeBox} Attribute Type
     * which has the OID {@code 2.5.4.18}.
     *
     * @return A reference to the {@code postOfficeBox} Attribute Type.
     */
    public static AttributeType getPostOfficeBoxAttributeType() {
        return POST_OFFICE_BOX_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code preferredDeliveryMethod} Attribute Type
     * which has the OID {@code 2.5.4.28}.
     *
     * @return A reference to the {@code preferredDeliveryMethod} Attribute Type.
     */
    public static AttributeType getPreferredDeliveryMethodAttributeType() {
        return PREFERRED_DELIVERY_METHOD_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code registeredAddress} Attribute Type
     * which has the OID {@code 2.5.4.26}.
     *
     * @return A reference to the {@code registeredAddress} Attribute Type.
     */
    public static AttributeType getRegisteredAddressAttributeType() {
        return REGISTERED_ADDRESS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code roleOccupant} Attribute Type
     * which has the OID {@code 2.5.4.33}.
     *
     * @return A reference to the {@code roleOccupant} Attribute Type.
     */
    public static AttributeType getRoleOccupantAttributeType() {
        return ROLE_OCCUPANT_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code searchGuide} Attribute Type
     * which has the OID {@code 2.5.4.14}.
     *
     * @return A reference to the {@code searchGuide} Attribute Type.
     */
    public static AttributeType getSearchGuideAttributeType() {
        return SEARCH_GUIDE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code seeAlso} Attribute Type
     * which has the OID {@code 2.5.4.34}.
     *
     * @return A reference to the {@code seeAlso} Attribute Type.
     */
    public static AttributeType getSeeAlsoAttributeType() {
        return SEE_ALSO_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code serialNumber} Attribute Type
     * which has the OID {@code 2.5.4.5}.
     *
     * @return A reference to the {@code serialNumber} Attribute Type.
     */
    public static AttributeType getSerialNumberAttributeType() {
        return SERIAL_NUMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code sn} Attribute Type
     * which has the OID {@code 2.5.4.4}.
     *
     * @return A reference to the {@code sn} Attribute Type.
     */
    public static AttributeType getSNAttributeType() {
        return SN_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code street} Attribute Type
     * which has the OID {@code 2.5.4.9}.
     *
     * @return A reference to the {@code street} Attribute Type.
     */
    public static AttributeType getStreetAttributeType() {
        return STREET_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code structuralObjectClass} Attribute Type
     * which has the OID {@code 2.5.21.9}.
     *
     * @return A reference to the {@code structuralObjectClass} Attribute Type.
     */
    public static AttributeType getStructuralObjectClassAttributeType() {
        return STRUCTURAL_OBJECT_CLASS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code st} Attribute Type
     * which has the OID {@code 2.5.4.8}.
     *
     * @return A reference to the {@code st} Attribute Type.
     */
    public static AttributeType getSTAttributeType() {
        return ST_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code subschemaSubentry} Attribute Type
     * which has the OID {@code 2.5.18.10}.
     *
     * @return A reference to the {@code subschemaSubentry} Attribute Type.
     */
    public static AttributeType getSubschemaSubentryAttributeType() {
        return SUBSCHEMA_SUBENTRY_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedAlgorithms} Attribute Type
     * which has the OID {@code 2.5.4.52}.
     *
     * @return A reference to the {@code supportedAlgorithms} Attribute Type.
     */
    public static AttributeType getSupportedAlgorithmsAttributeType() {
        return SUPPORTED_ALGORITHMS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedAuthPasswordSchemes} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.4203.1.3.3}.
     *
     * @return A reference to the {@code supportedAuthPasswordSchemes} Attribute Type.
     */
    public static AttributeType getSupportedAuthPasswordSchemesAttributeType() {
        return SUPPORTED_AUTH_PASSWORD_SCHEMES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedControl} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.13}.
     *
     * @return A reference to the {@code supportedControl} Attribute Type.
     */
    public static AttributeType getSupportedControlAttributeType() {
        return SUPPORTED_CONTROL_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedExtension} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.7}.
     *
     * @return A reference to the {@code supportedExtension} Attribute Type.
     */
    public static AttributeType getSupportedExtensionAttributeType() {
        return SUPPORTED_EXTENSION_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedFeatures} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.4203.1.3.5}.
     *
     * @return A reference to the {@code supportedFeatures} Attribute Type.
     */
    public static AttributeType getSupportedFeaturesAttributeType() {
        return SUPPORTED_FEATURES_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedLDAPVersion} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.15}.
     *
     * @return A reference to the {@code supportedLDAPVersion} Attribute Type.
     */
    public static AttributeType getSupportedLDAPVersionAttributeType() {
        return SUPPORTED_LDAP_VERSION_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code supportedSASLMechanisms} Attribute Type
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.14}.
     *
     * @return A reference to the {@code supportedSASLMechanisms} Attribute Type.
     */
    public static AttributeType getSupportedSASLMechanismsAttributeType() {
        return SUPPORTED_SASL_MECHANISMS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code telephoneNumber} Attribute Type
     * which has the OID {@code 2.5.4.20}.
     *
     * @return A reference to the {@code telephoneNumber} Attribute Type.
     */
    public static AttributeType getTelephoneNumberAttributeType() {
        return TELEPHONE_NUMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code teletexTerminalIdentifier} Attribute Type
     * which has the OID {@code 2.5.4.22}.
     *
     * @return A reference to the {@code teletexTerminalIdentifier} Attribute Type.
     */
    public static AttributeType getTeletexTerminalIdentifierAttributeType() {
        return TELETEX_TERMINAL_IDENTIFIER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code telexNumber} Attribute Type
     * which has the OID {@code 2.5.4.21}.
     *
     * @return A reference to the {@code telexNumber} Attribute Type.
     */
    public static AttributeType getTelexNumberAttributeType() {
        return TELEX_NUMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code title} Attribute Type
     * which has the OID {@code 2.5.4.12}.
     *
     * @return A reference to the {@code title} Attribute Type.
     */
    public static AttributeType getTitleAttributeType() {
        return TITLE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code uid} Attribute Type
     * which has the OID {@code 0.9.2342.19200300.100.1.1}.
     *
     * @return A reference to the {@code uid} Attribute Type.
     */
    public static AttributeType getUIDAttributeType() {
        return UID_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code uniqueMember} Attribute Type
     * which has the OID {@code 2.5.4.50}.
     *
     * @return A reference to the {@code uniqueMember} Attribute Type.
     */
    public static AttributeType getUniqueMemberAttributeType() {
        return UNIQUE_MEMBER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code userCertificate} Attribute Type
     * which has the OID {@code 2.5.4.36}.
     *
     * @return A reference to the {@code userCertificate} Attribute Type.
     */
    public static AttributeType getUserCertificateAttributeType() {
        return USER_CERTIFICATE_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code userPassword} Attribute Type
     * which has the OID {@code 2.5.4.35}.
     *
     * @return A reference to the {@code userPassword} Attribute Type.
     */
    public static AttributeType getUserPasswordAttributeType() {
        return USER_PASSWORD_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code vendorName} Attribute Type
     * which has the OID {@code 1.3.6.1.1.4}.
     *
     * @return A reference to the {@code vendorName} Attribute Type.
     */
    public static AttributeType getVendorNameAttributeType() {
        return VENDOR_NAME_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code vendorVersion} Attribute Type
     * which has the OID {@code 1.3.6.1.1.5}.
     *
     * @return A reference to the {@code vendorVersion} Attribute Type.
     */
    public static AttributeType getVendorVersionAttributeType() {
        return VENDOR_VERSION_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code x121Address} Attribute Type
     * which has the OID {@code 2.5.4.24}.
     *
     * @return A reference to the {@code x121Address} Attribute Type.
     */
    public static AttributeType getX121AddressAttributeType() {
        return X121_ADDRESS_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code x500UniqueIdentifier} Attribute Type
     * which has the OID {@code 2.5.4.45}.
     *
     * @return A reference to the {@code x500UniqueIdentifier} Attribute Type.
     */
    public static AttributeType getX500UniqueIdentifierAttributeType() {
        return X500_UNIQUE_IDENTIFIER_ATTRIBUTE_TYPE;
    }

    /**
     * Returns a reference to the {@code alias} Object Class
     * which has the OID {@code 2.5.6.1}.
     *
     * @return A reference to the {@code alias} Object Class.
     */
    public static ObjectClass getAliasObjectClass() {
        return ALIAS_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code applicationProcess} Object Class
     * which has the OID {@code 2.5.6.11}.
     *
     * @return A reference to the {@code applicationProcess} Object Class.
     */
    public static ObjectClass getApplicationProcessObjectClass() {
        return APPLICATION_PROCESS_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code authPasswordObject} Object Class
     * which has the OID {@code 1.3.6.1.4.1.4203.1.4.7}.
     *
     * @return A reference to the {@code authPasswordObject} Object Class.
     */
    public static ObjectClass getAuthPasswordObjectObjectClass() {
        return AUTH_PASSWORD_OBJECT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code certificationAuthority} Object Class
     * which has the OID {@code 2.5.6.16}.
     *
     * @return A reference to the {@code certificationAuthority} Object Class.
     */
    public static ObjectClass getCertificationAuthorityObjectClass() {
        return CERTIFICATION_AUTHORITY_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code certificationAuthority-V2} Object Class
     * which has the OID {@code 2.5.6.16.2}.
     *
     * @return A reference to the {@code certificationAuthority-V2} Object Class.
     */
    public static ObjectClass getCertificationAuthorityV2ObjectClass() {
        return CERTIFICATION_AUTHORITY_V2_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code country} Object Class
     * which has the OID {@code 2.5.6.2}.
     *
     * @return A reference to the {@code country} Object Class.
     */
    public static ObjectClass getCountryObjectClass() {
        return COUNTRY_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code cRLDistributionPoint} Object Class
     * which has the OID {@code 2.5.6.19}.
     *
     * @return A reference to the {@code cRLDistributionPoint} Object Class.
     */
    public static ObjectClass getCRlDistributionPointObjectClass() {
        return C_RL_DISTRIBUTION_POINT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code dcObject} Object Class
     * which has the OID {@code 1.3.6.1.4.1.1466.344}.
     *
     * @return A reference to the {@code dcObject} Object Class.
     */
    public static ObjectClass getDCObjectObjectClass() {
        return DC_OBJECT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code deltaCRL} Object Class
     * which has the OID {@code 2.5.6.23}.
     *
     * @return A reference to the {@code deltaCRL} Object Class.
     */
    public static ObjectClass getDeltaCrlObjectClass() {
        return DELTA_CRL_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code device} Object Class
     * which has the OID {@code 2.5.6.14}.
     *
     * @return A reference to the {@code device} Object Class.
     */
    public static ObjectClass getDeviceObjectClass() {
        return DEVICE_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code extensibleObject} Object Class
     * which has the OID {@code 1.3.6.1.4.1.1466.101.120.111}.
     *
     * @return A reference to the {@code extensibleObject} Object Class.
     */
    public static ObjectClass getExtensibleObjectObjectClass() {
        return EXTENSIBLE_OBJECT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code groupOfNames} Object Class
     * which has the OID {@code 2.5.6.9}.
     *
     * @return A reference to the {@code groupOfNames} Object Class.
     */
    public static ObjectClass getGroupOfNamesObjectClass() {
        return GROUP_OF_NAMES_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code groupOfUniqueNames} Object Class
     * which has the OID {@code 2.5.6.17}.
     *
     * @return A reference to the {@code groupOfUniqueNames} Object Class.
     */
    public static ObjectClass getGroupOfUniqueNamesObjectClass() {
        return GROUP_OF_UNIQUE_NAMES_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code locality} Object Class
     * which has the OID {@code 2.5.6.3}.
     *
     * @return A reference to the {@code locality} Object Class.
     */
    public static ObjectClass getLocalityObjectClass() {
        return LOCALITY_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code organizationalPerson} Object Class
     * which has the OID {@code 2.5.6.7}.
     *
     * @return A reference to the {@code organizationalPerson} Object Class.
     */
    public static ObjectClass getOrganizationalPersonObjectClass() {
        return ORGANIZATIONAL_PERSON_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code organizationalRole} Object Class
     * which has the OID {@code 2.5.6.8}.
     *
     * @return A reference to the {@code organizationalRole} Object Class.
     */
    public static ObjectClass getOrganizationalRoleObjectClass() {
        return ORGANIZATIONAL_ROLE_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code organizationalUnit} Object Class
     * which has the OID {@code 2.5.6.5}.
     *
     * @return A reference to the {@code organizationalUnit} Object Class.
     */
    public static ObjectClass getOrganizationalUnitObjectClass() {
        return ORGANIZATIONAL_UNIT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code organization} Object Class
     * which has the OID {@code 2.5.6.4}.
     *
     * @return A reference to the {@code organization} Object Class.
     */
    public static ObjectClass getOrganizationObjectClass() {
        return ORGANIZATION_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code person} Object Class
     * which has the OID {@code 2.5.6.6}.
     *
     * @return A reference to the {@code person} Object Class.
     */
    public static ObjectClass getPersonObjectClass() {
        return PERSON_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code pkiCA} Object Class
     * which has the OID {@code 2.5.6.22}.
     *
     * @return A reference to the {@code pkiCA} Object Class.
     */
    public static ObjectClass getPkiCaObjectClass() {
        return PKI_CA_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code pkiUser} Object Class
     * which has the OID {@code 2.5.6.21}.
     *
     * @return A reference to the {@code pkiUser} Object Class.
     */
    public static ObjectClass getPkiUserObjectClass() {
        return PKI_USER_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code residentialPerson} Object Class
     * which has the OID {@code 2.5.6.10}.
     *
     * @return A reference to the {@code residentialPerson} Object Class.
     */
    public static ObjectClass getResidentialPersonObjectClass() {
        return RESIDENTIAL_PERSON_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code strongAuthenticationUser} Object Class
     * which has the OID {@code 2.5.6.15}.
     *
     * @return A reference to the {@code strongAuthenticationUser} Object Class.
     */
    public static ObjectClass getStrongAuthenticationUserObjectClass() {
        return STRONG_AUTHENTICATION_USER_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code subschema} Object Class
     * which has the OID {@code 2.5.20.1}.
     *
     * @return A reference to the {@code subschema} Object Class.
     */
    public static ObjectClass getSubschemaObjectClass() {
        return SUBSCHEMA_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code top} Object Class
     * which has the OID {@code 2.5.6.0}.
     *
     * @return A reference to the {@code top} Object Class.
     */
    public static ObjectClass getTopObjectClass() {
        return TOP_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code uidObject} Object Class
     * which has the OID {@code 1.3.6.1.1.3.1}.
     *
     * @return A reference to the {@code uidObject} Object Class.
     */
    public static ObjectClass getUIDObjectObjectClass() {
        return UID_OBJECT_OBJECT_CLASS;
    }

    /**
     * Returns a reference to the {@code userSecurityInformation} Object Class
     * which has the OID {@code 2.5.6.18}.
     *
     * @return A reference to the {@code userSecurityInformation} Object Class.
     */
    public static ObjectClass getUserSecurityInformationObjectClass() {
        return USER_SECURITY_INFORMATION_OBJECT_CLASS;
    }
}
