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



/**
 * This class defines a number of constants used by Directory Server schema
 * elements, like matching rules, syntaxes, attribute types, and objectclasses.
 */
public class SchemaConstants
{
  /**
   * The IANA-assigned base OID for all things under the OpenDS umbrella.
   */
  public static final String OID_OPENDS_BASE = "1.3.6.1.4.1.26027";



  /**
   * The base OID that will be used for the OpenDS Directory Server project.
   */
  public static final String OID_OPENDS_SERVER_BASE = OID_OPENDS_BASE + ".1";



  /**
   * The base OID that will be used for OpenDS Directory Server attribute type
   * definitions.
   */
  public static final String OID_OPENDS_SERVER_ATTRIBUTE_TYPE_BASE =
       OID_OPENDS_SERVER_BASE + ".1";



  /**
   * The base OID that will be used for OpenDS Directory Server object class
   * definitions.
   */
  public static final String OID_OPENDS_SERVER_OBJECT_CLASS_BASE =
       OID_OPENDS_SERVER_BASE + ".2";



  /**
   * The base OID that will be used for OpenDS Directory Server attribute
   * syntax definitions.
   */
  public static final String OID_OPENDS_SERVER_ATTRIBUTE_SYNTAX_BASE =
       OID_OPENDS_SERVER_BASE + ".3";



  /**
   * The base OID that will be used for OpenDS Directory Server matching rule
   * definitions.
   */
  public static final String OID_OPENDS_SERVER_MATCHING_RULE_BASE =
       OID_OPENDS_SERVER_BASE + ".4";



  /**
   * The base OID that will be used for OpenDS Directory Server control
   * definitions.
   */
  public static final String OID_OPENDS_SERVER_CONTROL_BASE =
       OID_OPENDS_SERVER_BASE + ".5";



  /**
   * The base OID that will be used for OpenDS Directory Server extended
   * operation definitions.
   */
  public static final String OID_OPENDS_SERVER_EXTENDED_OPERATION_BASE =
       OID_OPENDS_SERVER_BASE + ".6";



  /**
   * The base OID that will be used for general-purpose (i.e., "other") types
   * of OIDs that need to be allocated for the OpenDS Directory Server.
   */
  public static final String OID_OPENDS_SERVER_GENERAL_USE_BASE =
       OID_OPENDS_SERVER_BASE + ".9";



  /**
   * The base OID that will be used for temporary or experimental OIDs within
   * the OpenDS Directory Server.
   */
  public static final String OID_OPENDS_SERVER_EXPERIMENTAL_BASE =
       OID_OPENDS_SERVER_BASE + ".999";



  /**
   * The description for the doubleMetaphoneApproximateMatch approximate
   * matching rule.
   */
  public static final String AMR_DOUBLE_METAPHONE_DESCRIPTION =
       "Double Metaphone Approximate Match";



  /**
   * The name for the doubleMetaphoneApproximateMatch approximate matching rule.
   */
  public static final String AMR_DOUBLE_METAPHONE_NAME =
       "ds-mr-double-metaphone-approx";



  /**
   * The OID for the doubleMetaphoneApproximateMatch approximate matching rule.
   */
  public static final String AMR_DOUBLE_METAPHONE_OID =
       OID_OPENDS_SERVER_MATCHING_RULE_BASE + ".1";


  /**
   * The description for the authPasswordExactMatch matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_EXACT_DESCRIPTION =
       "authentication password exact matching rule";



  /**
   * The name for the authPasswordExactMatch equality matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_EXACT_NAME =
       "authPasswordExactMatch";



  /**
   * The OID for the authPasswordExactMatch equality matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_EXACT_OID =
       "1.3.6.1.4.1.4203.1.2.2";


  /**
   * The description for the authPasswordMatch matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_DESCRIPTION =
       "authentication password matching rule";



  /**
   * The name for the authPasswordMatch equality matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_NAME = "authPasswordMatch";



  /**
   * The OID for the authPasswordMatch equality matching rule.
   */
  public static final String EMR_AUTH_PASSWORD_OID = "1.3.6.1.4.1.4203.1.2.3";



  /**
   * The name for the bitStringMatch equality matching rule.
   */
  public static final String EMR_BIT_STRING_NAME = "bitStringMatch";



  /**
   * The OID for the bitStringMatch equality matching rule.
   */
  public static final String EMR_BIT_STRING_OID = "2.5.13.16";



  /**
   * The name for the booleanMatch equality matching rule.
   */
  public static final String EMR_BOOLEAN_NAME = "booleanMatch";



  /**
   * The OID for the booleanMatch equality matching rule.
   */
  public static final String EMR_BOOLEAN_OID = "2.5.13.13";



  /**
   * The name for the caseExactMatch equality matching rule.
   */
  public static final String EMR_CASE_EXACT_NAME = "caseExactMatch";



  /**
   * The OID for the caseExactMatch equality matching rule.
   */
  public static final String EMR_CASE_EXACT_OID = "2.5.13.5";



  /**
   * The name for the caseExactIA5Match equality matching rule.
   */
  public static final String EMR_CASE_EXACT_IA5_NAME = "caseExactIA5Match";



  /**
   * The OID for the caseExactIA5Match equality matching rule.
   */
  public static final String EMR_CASE_EXACT_IA5_OID =
       "1.3.6.1.4.1.1466.109.114.1";



  /**
   * The name for the caseIgnoreMatch equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_NAME = "caseIgnoreMatch";



  /**
   * The OID for the caseIgnoreMatch equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_OID = "2.5.13.2";



  /**
   * The name for the caseIgnoreIA5Match equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_IA5_NAME = "caseIgnoreIA5Match";



  /**
   * The OID for the caseIgnoreIA5Match equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_IA5_OID =
       "1.3.6.1.4.1.1466.109.114.2";



  /**
   * The name for the caseIgnoreListMatch equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_LIST_NAME = "caseIgnoreListMatch";



  /**
   * The OID for the caseIgnoreListMatch equality matching rule.
   */
  public static final String EMR_CASE_IGNORE_LIST_OID = "2.5.13.11";



  /**
   * The name for the directoryStringFirstComponentMatch equality matching rule.
   */
  public static final String EMR_DIRECTORY_STRING_FIRST_COMPONENT_NAME =
       "directoryStringFirstComponentMatch";



  /**
   * The OID for the directoryStringFirstComponentMatch equality matching rule.
   */
  public static final String EMR_DIRECTORY_STRING_FIRST_COMPONENT_OID =
       "2.5.13.31";



  /**
   * The name for the distinguishedNameMatch equality matching rule.
   */
  public static final String EMR_DN_NAME = "distinguishedNameMatch";



  /**
   * The OID for the distinguishedNameMatch equality matching rule.
   */
  public static final String EMR_DN_OID = "2.5.13.1";



  /**
   * The name for the generalizedTimeMatch equality matching rule.
   */
  public static final String EMR_GENERALIZED_TIME_NAME = "generalizedTimeMatch";



  /**
   * The OID for the generalizedTimeMatch equality matching rule.
   */
  public static final String EMR_GENERALIZED_TIME_OID = "2.5.13.27";



  /**
   * The name for the integerMatch equality matching rule.
   */
  public static final String EMR_INTEGER_NAME = "integerMatch";



  /**
   * The OID for the integerMatch equality matching rule.
   */
  public static final String EMR_INTEGER_OID = "2.5.13.14";



  /**
   * The name for the integerFirstComponentMatch equality matching rule.
   */
  public static final String EMR_INTEGER_FIRST_COMPONENT_NAME =
       "integerFirstComponentMatch";



  /**
   * The OID for the integerFirstComponentMatch equality matching rule.
   */
  public static final String EMR_INTEGER_FIRST_COMPONENT_OID = "2.5.13.29";



  /**
   * The name for the keywordMatch equality matching rule.
   */
  public static final String EMR_KEYWORD_NAME = "keywordMatch";



  /**
   * The OID for the keywordMatch equality matching rule.
   */
  public static final String EMR_KEYWORD_OID = "2.5.13.33";



  /**
   * The name for the numericStringMatch equality matching rule.
   */
  public static final String EMR_NUMERIC_STRING_NAME = "numericStringMatch";



  /**
   * The OID for the numericStringMatch equality matching rule.
   */
  public static final String EMR_NUMERIC_STRING_OID = "2.5.13.8";



  /**
   * The name for the octetStringMatch equality matching rule.
   */
  public static final String EMR_OCTET_STRING_NAME = "octetStringMatch";



  /**
   * The OID for the octetStringMatch equality matching rule.
   */
  public static final String EMR_OCTET_STRING_OID = "2.5.13.17";



  /**
   * The name for the objectIdentifierMatch equality matching rule.
   */
  public static final String EMR_OID_NAME = "objectIdentifierMatch";



  /**
   * The OID for the objectIdentifierMatch equality matching rule.
   */
  public static final String EMR_OID_OID = "2.5.13.0";



  /**
   * The name for the objectIdentifierFirstComponentMatch equality matching
   * rule.
   */
  public static final String EMR_OID_FIRST_COMPONENT_NAME =
       "objectIdentifierFirstComponentMatch";



  /**
   * The OID for the objectIdentifierFirstComponentMatch equality matching rule.
   */
  public static final String EMR_OID_FIRST_COMPONENT_OID = "2.5.13.30";



  /**
   * The name for the presentationAddressMatch equality matching rule.
   */
  public static final String EMR_PRESENTATION_ADDRESS_NAME =
       "presentationAddressMatch";



  /**
   * The OID for the presentationAddressMatch equality matching rule.
   */
  public static final String EMR_PRESENTATION_ADDRESS_OID = "2.5.13.22";



  /**
   * The name for the protocolInformationMatch equality matching rule.
   */
  public static final String EMR_PROTOCOL_INFORMATION_NAME =
       "protocolInformationMatch";



  /**
   * The OID for the protocolInformationMatch equality matching rule.
   */
  public static final String EMR_PROTOCOL_INFORMATION_OID = "2.5.13.24";



  /**
   * The name for the telephoneNumberMatch equality matching rule.
   */
  public static final String EMR_TELEPHONE_NAME = "telephoneNumberMatch";



  /**
   * The OID for the telephoneNumberMatch equality matching rule.
   */
  public static final String EMR_TELEPHONE_OID = "2.5.13.20";



  /**
   * The name for the uniqueMemberMatch equality matching rule.
   */
  public static final String EMR_UNIQUE_MEMBER_NAME = "uniqueMemberMatch";



  /**
   * The OID for the uniqueMemberMatch equality matching rule.
   */
  public static final String EMR_UNIQUE_MEMBER_OID = "2.5.13.23";


  /**
   * The description for the userPasswordExactMatch matching rule.
   */
  public static final String EMR_USER_PASSWORD_EXACT_DESCRIPTION =
       "user password exact matching rule";



  /**
   * The name for the userPasswordExactMatch equality matching rule.
   */
  public static final String EMR_USER_PASSWORD_EXACT_NAME =
       "ds-mr-user-password-exact";



  /**
   * The OID for the userPasswordExactMatch equality matching rule.
   */
  public static final String EMR_USER_PASSWORD_EXACT_OID =
       OID_OPENDS_SERVER_MATCHING_RULE_BASE + ".2";


  /**
   * The description for the userPasswordMatch matching rule.
   */
  public static final String EMR_USER_PASSWORD_DESCRIPTION =
       "user password matching rule";



  /**
   * The name for the userPasswordMatch equality matching rule.
   */
  public static final String EMR_USER_PASSWORD_NAME =
       "ds-mr-user-password-equality";



  /**
   * The OID for the userPasswordMatch equality matching rule.
   */
  public static final String EMR_USER_PASSWORD_OID =
       OID_OPENDS_SERVER_MATCHING_RULE_BASE + ".3";



  /**
   * The name for the uuidMatch equality matching rule.
   */
  public static final String EMR_UUID_NAME = "uuidMatch";



  /**
   * The OID for the uuidMatch equality matching rule.
   */
  public static final String EMR_UUID_OID = "1.3.6.1.1.16.2";



  /**
   * The name for the wordMatch equality matching rule.
   */
  public static final String EMR_WORD_NAME = "wordMatch";



  /**
   * The OID for the wordMatch equality matching rule.
   */
  public static final String EMR_WORD_OID = "2.5.13.32";



  /**
   * The name for the caseExactOrderingMatch ordering matching rule.
   */
  public static final String OMR_CASE_EXACT_NAME = "caseExactOrderingMatch";



  /**
   * The OID for the caseExactOrderingMatch ordering matching rule.
   */
  public static final String OMR_CASE_EXACT_OID = "2.5.13.6";



  /**
   * The name for the caseIgnoreOrderingMatch ordering matching rule.
   */
  public static final String OMR_CASE_IGNORE_NAME = "caseIgnoreOrderingMatch";



  /**
   * The OID for the caseIgnoreOrderingMatch ordering matching rule.
   */
  public static final String OMR_CASE_IGNORE_OID = "2.5.13.3";



  /**
   * The name for the generalizedTimeOrderingMatch ordering matching rule.
   */
  public static final String OMR_GENERALIZED_TIME_NAME =
                                  "generalizedTimeOrderingMatch";



  /**
   * The OID for the generalizedTimeOrderingMatch ordering matching rule.
   */
  public static final String OMR_GENERALIZED_TIME_OID = "2.5.13.28";



  /**
   * The name for the integerOrderingMatch ordering matching rule.
   */
  public static final String OMR_INTEGER_NAME = "integerOrderingMatch";



  /**
   * The OID for the integerOrderingMatch ordering matching rule.
   */
  public static final String OMR_INTEGER_OID = "2.5.13.15";



  /**
   * The name for the numericStringOrderingMatch ordering matching rule.
   */
  public static final String OMR_NUMERIC_STRING_NAME =
       "numericStringOrderingMatch";



  /**
   * The OID for the numericStringOrderingMatch ordering matching rule.
   */
  public static final String OMR_NUMERIC_STRING_OID = "2.5.13.9";



  /**
   * The name for the octetStringOrderingMatch ordering matching rule.
   */
  public static final String OMR_OCTET_STRING_NAME = "octetStringOrderingMatch";



  /**
   * The OID for the octetStringOrderingMatch ordering matching rule.
   */
  public static final String OMR_OCTET_STRING_OID = "2.5.13.18";



  /**
   * The name for the uuidOrderingMatch ordering matching rule.
   */
  public static final String OMR_UUID_NAME = "uuidOrderingMatch";



  /**
   * The OID for the uuidOrderingMatch ordering matching rule.
   */
  public static final String OMR_UUID_OID = "1.3.6.1.1.16.3";



  /**
   * The name for the caseExactSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_EXACT_NAME = "caseExactSubstringsMatch";



  /**
   * The OID for the caseExactSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_EXACT_OID = "2.5.13.7";



  /**
   * The name for the caseExactIA5SubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_EXACT_IA5_NAME =
       "caseExactIA5SubstringsMatch";



  /**
   * The OID for the caseExactIA5SubstringsMatch substring matching rule.
   * // FIXME -- This needs to be updated once a real OID is assigned.
   */
  public static final String SMR_CASE_EXACT_IA5_OID =
       OID_OPENDS_SERVER_MATCHING_RULE_BASE + ".902";



  /**
   * The name for the caseIgnoreSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_NAME = "caseIgnoreSubstringsMatch";



  /**
   * The OID for the caseIgnoreSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_OID = "2.5.13.4";



  /**
   * The name for the caseIgnoreIA5SubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_IA5_NAME =
       "caseIgnoreIA5SubstringsMatch";



  /**
   * The OID for the caseIgnoreIA5SubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_IA5_OID =
       "1.3.6.1.4.1.1466.109.114.3";



  /**
   * The name for the caseIgnoreListSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_LIST_NAME =
       "caseIgnoreListSubstringsMatch";



  /**
   * The OID for the caseIgnoreListSubstringsMatch substring matching rule.
   */
  public static final String SMR_CASE_IGNORE_LIST_OID = "2.5.13.12";



  /**
   * The name for the numericStringSubstringsMatch substring matching rule.
   */
  public static final String SMR_NUMERIC_STRING_NAME =
       "numericStringSubstringsMatch";



  /**
   * The OID for the numericStringSubstringsMatch substring matching rule.
   */
  public static final String SMR_NUMERIC_STRING_OID = "2.5.13.10";



  /**
   * The name for the octetStringSubstringsMatch substring matching rule.
   */
  public static final String SMR_OCTET_STRING_NAME =
       "octetStringSubstringsMatch";



  /**
   * The OID for the octetStringSubstringsMatch substring matching rule.
   */
  public static final String SMR_OCTET_STRING_OID = "2.5.13.19";



  /**
   * The name for the telephoneNumberSubstringsMatch substring matching rule.
   */
  public static final String SMR_TELEPHONE_NAME =
       "telephoneNumberSubstringsMatch";



  /**
   * The OID for the telephoneNumberSubstringsMatch substring matching rule.
   */
  public static final String SMR_TELEPHONE_OID = "2.5.13.21";



  /**
   * The OID for the absolute subtree specification attribute syntax.
   */
  public static final String SYNTAX_ABSOLUTE_SUBTREE_SPECIFICATION_OID =
       OID_OPENDS_SERVER_ATTRIBUTE_SYNTAX_BASE + ".3";



  /**
   * The description for the absolute subtree specification attribute syntax.
   */
  public static final String SYNTAX_ABSOLUTE_SUBTREE_SPECIFICATION_DESCRIPTION =
    "Absolute Subtree Specification";



  /**
   * The name for the absolute subtree specification attribute syntax.
   */
  public static final String SYNTAX_ABSOLUTE_SUBTREE_SPECIFICATION_NAME =
    "ds-absolute-subtree-specification";



   /**
    * The OID for the aci attribute syntax.
    */
   public static final String SYNTAX_ACI_OID =
        OID_OPENDS_SERVER_ATTRIBUTE_SYNTAX_BASE + ".4";



  /**
   * The description for aci attribute syntax.
   */
  public static final String SYNTAX_ACI_DESCRIPTION =
       "Sun-defined Access Control Information";



  /**
   * The name for the aci attribute syntax.
   */
  public static final String SYNTAX_ACI_NAME = "ds-syntax-dseecompat-aci";



  /**
   * The description for the attribute type description attribute syntax.
   */
  public static final String SYNTAX_ATTRIBUTE_TYPE_DESCRIPTION =
       "Attribute Type Description";



  /**
   * The name for the attribute type description attribute syntax.
   */
  public static final String SYNTAX_ATTRIBUTE_TYPE_NAME =
       "AttributeTypeDescription";



  /**
   * The OID for the attribute type description attribute syntax.
   */
  public static final String SYNTAX_ATTRIBUTE_TYPE_OID =
       "1.3.6.1.4.1.1466.115.121.1.3";



  /**
   * The description for the auth password attribute syntax.
   */
  public static final String SYNTAX_AUTH_PASSWORD_DESCRIPTION =
       "Authentication Password Syntax";



  /**
   * The name for the auth password attribute syntax.
   */
  public static final String SYNTAX_AUTH_PASSWORD_NAME =
       "AuthenticationPasswordSyntax";



  /**
   * The OID for the auth password attribute syntax.
   */
  public static final String SYNTAX_AUTH_PASSWORD_OID =
       "1.3.6.1.4.1.4203.1.1.2";



  /**
   * The description for the binary attribute syntax.
   */
  public static final String SYNTAX_BINARY_DESCRIPTION = "Binary";



  /**
   * The name for the binary attribute syntax.
   */
  public static final String SYNTAX_BINARY_NAME = "Binary";



  /**
   * The OID for the binary attribute syntax.
   */
  public static final String SYNTAX_BINARY_OID = "1.3.6.1.4.1.1466.115.121.1.5";



  /**
   * The description for the bit string attribute syntax.
   */
  public static final String SYNTAX_BIT_STRING_DESCRIPTION = "Bit String";



  /**
   * The name for the bit string attribute syntax.
   */
  public static final String SYNTAX_BIT_STRING_NAME = "BitString";



  /**
   * The OID for the bit string attribute syntax.
   */
  public static final String SYNTAX_BIT_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.6";



  /**
   * The description for the Boolean attribute syntax.
   */
  public static final String SYNTAX_BOOLEAN_DESCRIPTION = "Boolean";



  /**
   * The name for the Boolean attribute syntax.
   */
  public static final String SYNTAX_BOOLEAN_NAME = "Boolean";



  /**
   * The OID for the Boolean attribute syntax.
   */
  public static final String SYNTAX_BOOLEAN_OID =
       "1.3.6.1.4.1.1466.115.121.1.7";



  /**
   * The description for the certificate attribute syntax.
   */
  public static final String SYNTAX_CERTIFICATE_DESCRIPTION = "Certificate";



  /**
   * The name for the certificate attribute syntax.
   */
  public static final String SYNTAX_CERTIFICATE_NAME = "Certificate";



  /**
   * The OID for the certificate attribute syntax.
   */
  public static final String SYNTAX_CERTIFICATE_OID =
       "1.3.6.1.4.1.1466.115.121.1.8";



  /**
   * The description for the certificate list attribute syntax.
   */
  public static final String SYNTAX_CERTLIST_DESCRIPTION = "Certificate List";



  /**
   * The name for the certificate list attribute syntax.
   */
  public static final String SYNTAX_CERTLIST_NAME = "CertificateList";



  /**
   * The OID for the certificate list attribute syntax.
   */
  public static final String SYNTAX_CERTLIST_OID =
       "1.3.6.1.4.1.1466.115.121.1.9";



  /**
   * The description for the certificate pair attribute syntax.
   */
  public static final String SYNTAX_CERTPAIR_DESCRIPTION = "Certificate Pair";



  /**
   * The name for the certificate pair attribute syntax.
   */
  public static final String SYNTAX_CERTPAIR_NAME = "CertificatePair";



  /**
   * The OID for the certificate pair attribute syntax.
   */
  public static final String SYNTAX_CERTPAIR_OID =
       "1.3.6.1.4.1.1466.115.121.1.10";



  /**
   * The description for the country string attribute syntax.
   */
  public static final String SYNTAX_COUNTRY_STRING_DESCRIPTION =
       "Country String";



  /**
   * The name for the country string attribute syntax.
   */
  public static final String SYNTAX_COUNTRY_STRING_NAME = "CountryString";



  /**
   * The OID for the country string attribute syntax.
   */
  public static final String SYNTAX_COUNTRY_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.11";



  /**
   * The description for the delivery method attribute syntax.
   */
  public static final String SYNTAX_DELIVERY_METHOD_DESCRIPTION =
       "Delivery Method";



  /**
   * The name for the delivery method attribute syntax.
   */
  public static final String SYNTAX_DELIVERY_METHOD_NAME = "DeliveryMethod";



  /**
   * The OID for the delivery method attribute syntax.
   */
  public static final String SYNTAX_DELIVERY_METHOD_OID =
       "1.3.6.1.4.1.1466.115.121.1.14";



  /**
   * The description for the Directory String attribute syntax.
   */
  public static final String SYNTAX_DIRECTORY_STRING_DESCRIPTION =
       "Directory String";



  /**
   * The name for the Directory String attribute syntax.
   */
  public static final String SYNTAX_DIRECTORY_STRING_NAME = "DirectoryString";



  /**
   * The OID for the Directory String attribute syntax.
   */
  public static final String SYNTAX_DIRECTORY_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.15";



  /**
   * The description for the DIT content rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_CONTENT_RULE_DESCRIPTION =
       "DIT Content Rule Description";



  /**
   * The name for the DIT content rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_CONTENT_RULE_NAME =
       "DITContentRuleDescription";



  /**
   * The OID for the DIT content rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_CONTENT_RULE_OID =
       "1.3.6.1.4.1.1466.115.121.1.16";



  /**
   * The description for the DIT structure rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_STRUCTURE_RULE_DESCRIPTION =
       "DIT Structure Rule Description";



  /**
   * The name for the DIT structure rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_STRUCTURE_RULE_NAME =
       "DITStructureRuleDescription";



  /**
   * The OID for the DIT structure rule description attribute syntax.
   */
  public static final String SYNTAX_DIT_STRUCTURE_RULE_OID =
       "1.3.6.1.4.1.1466.115.121.1.17";



  /**
   * The description for the distinguished name attribute syntax.
   */
  public static final String SYNTAX_DN_DESCRIPTION = "DN";



  /**
   * The name for the distinguished name attribute syntax.
   */
  public static final String SYNTAX_DN_NAME = "DN";



  /**
   * The OID for the distinguished name attribute syntax.
   */
  public static final String SYNTAX_DN_OID = "1.3.6.1.4.1.1466.115.121.1.12";



  /**
   * The description for the enhanced guide attribute syntax.
   */
  public static final String SYNTAX_ENHANCED_GUIDE_DESCRIPTION =
       "Enhanced Guide";



  /**
   * The name for the enhanced guide attribute syntax.
   */
  public static final String SYNTAX_ENHANCED_GUIDE_NAME = "EnhancedGuide";



  /**
   * The OID for the enhanced guide attribute syntax.
   */
  public static final String SYNTAX_ENHANCED_GUIDE_OID =
       "1.3.6.1.4.1.1466.115.121.1.21";



  /**
   * The description for the facsimile telephone number attribute syntax.
   */
  public static final String SYNTAX_FAXNUMBER_DESCRIPTION =
       "Facsimile Telephone Number";



  /**
   * The name for the facsimile telephone number attribute syntax.
   */
  public static final String SYNTAX_FAXNUMBER_NAME = "FacsimileTelephoneNumber";



  /**
   * The OID for the facsimile telephone number attribute syntax.
   */
  public static final String SYNTAX_FAXNUMBER_OID =
       "1.3.6.1.4.1.1466.115.121.1.22";



  /**
   * The description for the fax attribute syntax.
   */
  public static final String SYNTAX_FAX_DESCRIPTION = "Fax";



  /**
   * The name for the fax attribute syntax.
   */
  public static final String SYNTAX_FAX_NAME = "Fax";



  /**
   * The OID for the fax attribute syntax.
   */
  public static final String SYNTAX_FAX_OID = "1.3.6.1.4.1.1466.115.121.1.23";



  /**
   * The description for the generalized time attribute syntax.
   */
  public static final String SYNTAX_GENERALIZED_TIME_DESCRIPTION =
       "Generalized Time";



  /**
   * The name for the generalized time attribute syntax.
   */
  public static final String SYNTAX_GENERALIZED_TIME_NAME = "GeneralizedTime";



  /**
   * The OID for the generalized time attribute syntax.
   */
  public static final String SYNTAX_GENERALIZED_TIME_OID =
       "1.3.6.1.4.1.1466.115.121.1.24";



  /**
   * The description for the guide attribute syntax.
   */
  public static final String SYNTAX_GUIDE_DESCRIPTION = "Guide";



  /**
   * The name for the guide attribute syntax.
   */
  public static final String SYNTAX_GUIDE_NAME = "Guide";



  /**
   * The OID for the guide attribute syntax.
   */
  public static final String SYNTAX_GUIDE_OID = "1.3.6.1.4.1.1466.115.121.1.25";



  /**
   * The description for the IA5 string attribute syntax.
   */
  public static final String SYNTAX_IA5_STRING_DESCRIPTION = "IA5 String";



  /**
   * The name for the IA5 string attribute syntax.
   */
  public static final String SYNTAX_IA5_STRING_NAME = "IA5String";



  /**
   * The OID for the IA5 string attribute syntax.
   */
  public static final String SYNTAX_IA5_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.26";



  /**
   * The description for the integer attribute syntax.
   */
  public static final String SYNTAX_INTEGER_DESCRIPTION = "Integer";



  /**
   * The name for the integer attribute syntax.
   */
  public static final String SYNTAX_INTEGER_NAME = "Integer";



  /**
   * The OID for the integer attribute syntax.
   */
  public static final String SYNTAX_INTEGER_OID =
       "1.3.6.1.4.1.1466.115.121.1.27";



  /**
   * The description for the JPEG attribute syntax.
   */
  public static final String SYNTAX_JPEG_DESCRIPTION = "JPEG";



  /**
   * The name for the JPEG attribute syntax.
   */
  public static final String SYNTAX_JPEG_NAME = "JPEG";



  /**
   * The OID for the JPEG attribute syntax.
   */
  public static final String SYNTAX_JPEG_OID =
       "1.3.6.1.4.1.1466.115.121.1.28";



  /**
   * The description for the LDAP syntax description attribute syntax.
   */
  public static final String SYNTAX_LDAP_SYNTAX_DESCRIPTION =
       "LDAP Syntax Description";



  /**
   * The name for the LDAP syntax description attribute syntax.
   */
  public static final String SYNTAX_LDAP_SYNTAX_NAME = "LDAPSyntaxDescription";



  /**
   * The OID for the LDAP syntax description attribute syntax.
   */
  public static final String SYNTAX_LDAP_SYNTAX_OID =
       "1.3.6.1.4.1.1466.115.121.1.54";



  /**
   * The description for the matching rule description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_DESCRIPTION =
       "Matching Rule Description";



  /**
   * The name for the matching rule description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_NAME =
       "MatchingRuleDescription";



  /**
   * The OID for the matching rule description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_OID =
       "1.3.6.1.4.1.1466.115.121.1.30";



  /**
   * The description for the matching rule use description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_USE_DESCRIPTION =
       "Matching Rule Use Description";



  /**
   * The name for the matching rule use description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_USE_NAME =
       "MatchingRuleUseDescription";



  /**
   * The OID for the matching rule use description attribute syntax.
   */
  public static final String SYNTAX_MATCHING_RULE_USE_OID =
       "1.3.6.1.4.1.1466.115.121.1.31";



  /**
   * The description for the name and optional uid attribute syntax.
   */
  public static final String SYNTAX_NAME_AND_OPTIONAL_UID_DESCRIPTION =
       "Name and Optional UID";



  /**
   * The name for the name and optional uid attribute syntax.
   */
  public static final String SYNTAX_NAME_AND_OPTIONAL_UID_NAME =
       "NameAndOptionalUID";



  /**
   * The OID for the name and optional uid attribute syntax.
   */
  public static final String SYNTAX_NAME_AND_OPTIONAL_UID_OID =
       "1.3.6.1.4.1.1466.115.121.1.34";



  /**
   * The description for the name form description attribute syntax.
   */
  public static final String SYNTAX_NAME_FORM_DESCRIPTION =
       "Name Form Description";



  /**
   * The name for the name form description attribute syntax.
   */
  public static final String SYNTAX_NAME_FORM_NAME = "NameFormDescription";



  /**
   * The OID for the name form description attribute syntax.
   */
  public static final String SYNTAX_NAME_FORM_OID =
       "1.3.6.1.4.1.1466.115.121.1.35";



  /**
   * The description for the numeric string attribute syntax.
   */
  public static final String SYNTAX_NUMERIC_STRING_DESCRIPTION =
       "Numeric String";



  /**
   * The name for the numeric string attribute syntax.
   */
  public static final String SYNTAX_NUMERIC_STRING_NAME = "NumericString";



  /**
   * The OID for the numeric string attribute syntax.
   */
  public static final String SYNTAX_NUMERIC_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.36";



  /**
   * The description for the object class description attribute syntax.
   */
  public static final String SYNTAX_OBJECTCLASS_DESCRIPTION =
       "Object Class Description";



  /**
   * The name for the object class description attribute syntax.
   */
  public static final String SYNTAX_OBJECTCLASS_NAME =
       "ObjectClassDescription";



  /**
   * The OID for the object class description attribute syntax.
   */
  public static final String SYNTAX_OBJECTCLASS_OID =
       "1.3.6.1.4.1.1466.115.121.1.37";



  /**
   * The description for the octet string attribute syntax.
   */
  public static final String SYNTAX_OCTET_STRING_DESCRIPTION = "Octet String";



  /**
   * The name for the octet string attribute syntax.
   */
  public static final String SYNTAX_OCTET_STRING_NAME = "OctetString";



  /**
   * The OID for the octet string attribute syntax.
   */
  public static final String SYNTAX_OCTET_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.40";



  /**
   * The description for the object identifier attribute syntax.
   */
  public static final String SYNTAX_OID_DESCRIPTION = "OID";



  /**
   * The name for the object identifier attribute syntax.
   */
  public static final String SYNTAX_OID_NAME = "OID";



  /**
   * The OID for the object identifier attribute syntax.
   */
  public static final String SYNTAX_OID_OID =
       "1.3.6.1.4.1.1466.115.121.1.38";



  /**
   * The description for the other mailbox attribute syntax.
   */
  public static final String SYNTAX_OTHER_MAILBOX_DESCRIPTION = "Other Mailbox";



  /**
   * The name for the other mailbox attribute syntax.
   */
  public static final String SYNTAX_OTHER_MAILBOX_NAME = "OtherMailbox";



  /**
   * The OID for the other mailbox attribute syntax.
   */
  public static final String SYNTAX_OTHER_MAILBOX_OID =
       "1.3.6.1.4.1.1466.115.121.1.39";



  /**
   * The description for the postal address attribute syntax.
   */
  public static final String SYNTAX_POSTAL_ADDRESS_DESCRIPTION =
       "Postal Address";



  /**
   * The name for the postal address attribute syntax.
   */
  public static final String SYNTAX_POSTAL_ADDRESS_NAME = "PostalAddress";



  /**
   * The OID for the postal address attribute syntax.
   */
  public static final String SYNTAX_POSTAL_ADDRESS_OID =
       "1.3.6.1.4.1.1466.115.121.1.41";



  /**
   * The description for the presentation address attribute syntax.
   */
  public static final String SYNTAX_PRESENTATION_ADDRESS_DESCRIPTION =
       "Presentation Address";



  /**
   * The name for the presentation address attribute syntax.
   */
  public static final String SYNTAX_PRESENTATION_ADDRESS_NAME =
       "PresentationAddress";



  /**
   * The OID for the presentation address attribute syntax.
   */
  public static final String SYNTAX_PRESENTATION_ADDRESS_OID =
       "1.3.6.1.4.1.1466.115.121.1.43";



  /**
   * The description for the printable string attribute syntax.
   */
  public static final String SYNTAX_PRINTABLE_STRING_DESCRIPTION =
       "Printable String";



  /**
   * The name for the printable string attribute syntax.
   */
  public static final String SYNTAX_PRINTABLE_STRING_NAME = "PrintableString";



  /**
   * The OID for the printable string attribute syntax.
   */
  public static final String SYNTAX_PRINTABLE_STRING_OID =
       "1.3.6.1.4.1.1466.115.121.1.44";



  /**
   * The description for the protocol information attribute syntax.
   */
  public static final String SYNTAX_PROTOCOL_INFORMATION_DESCRIPTION =
       "Protocol Information";



  /**
   * The name for the protocol information attribute syntax.
   */
  public static final String SYNTAX_PROTOCOL_INFORMATION_NAME =
       "ProtocolInformation";



  /**
   * The OID for the protocol information attribute syntax.
   */
  public static final String SYNTAX_PROTOCOL_INFORMATION_OID =
       "1.3.6.1.4.1.1466.115.121.1.42";



  /**
   * The OID for the relative subtree specification attribute syntax.
   */
  public static final String SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_OID =
       OID_OPENDS_SERVER_ATTRIBUTE_SYNTAX_BASE + ".2";



  /**
   * The description for the relative subtree specification attribute syntax.
   */
  public static final String SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_DESCRIPTION =
    "Relative Subtree Specification";



  /**
   * The name for the relative subtree specification attribute syntax.
   */
  public static final String SYNTAX_RELATIVE_SUBTREE_SPECIFICATION_NAME =
    "ds-relative-subtree-specification";



  /**
   * The OID for the RFC3672 subtree specification attribute syntax.
   */
  public static final String SYNTAX_RFC3672_SUBTREE_SPECIFICATION_OID =
    "1.3.6.1.4.1.1466.115.121.1.45";



  /**
   * The description for the RFC3672 subtree specification attribute syntax.
   */
  public static final String SYNTAX_RFC3672_SUBTREE_SPECIFICATION_DESCRIPTION =
    "RFC3672 Subtree Specification";



  /**
   * The name for the RFC3672 subtree specification attribute syntax.
   */
  public static final String SYNTAX_RFC3672_SUBTREE_SPECIFICATION_NAME =
    "SubtreeSpecification";



  /**
   * The description for the substring assertion attribute syntax.
   */
  public static final String SYNTAX_SUBSTRING_ASSERTION_DESCRIPTION =
       "Substring Assertion";



  /**
   * The name for the substring assertion attribute syntax.
   */
  public static final String SYNTAX_SUBSTRING_ASSERTION_NAME =
       "SubstringAssertion";



  /**
   * The OID for the Substring Assertion syntax used for assertion values in
   * extensible match filters.
   */
  public static final String SYNTAX_SUBSTRING_ASSERTION_OID =
       "1.3.6.1.4.1.1466.115.121.1.58";



  /**
   * The description for the supported algorithm attribute syntax.
   */
  public static final String SYNTAX_SUPPORTED_ALGORITHM_DESCRIPTION =
       "Supported Algorithm";



  /**
   * The name for the supported algorithm attribute syntax.
   */
  public static final String SYNTAX_SUPPORTED_ALGORITHM_NAME =
       "SupportedAlgorithm";



  /**
   * The OID for the Substring Assertion syntax used for assertion values in
   * extensible match filters.
   */
  public static final String SYNTAX_SUPPORTED_ALGORITHM_OID =
       "1.3.6.1.4.1.1466.115.121.1.49";



  /**
   * The description for the telephone number attribute syntax.
   */
  public static final String SYNTAX_TELEPHONE_DESCRIPTION = "Telephone Number";



  /**
   * The name for the telephone number attribute syntax.
   */
  public static final String SYNTAX_TELEPHONE_NAME = "TelephoneNumber";



  /**
   * The OID for the telephone number attribute syntax.
   */
  public static final String SYNTAX_TELEPHONE_OID =
       "1.3.6.1.4.1.1466.115.121.1.50";



  /**
   * The description for the teletex terminal identifier attribute syntax.
   */
  public static final String SYNTAX_TELETEX_TERM_ID_DESCRIPTION =
       "Teletex Terminal Identifier";



  /**
   * The name for the teletex terminal identifier attribute syntax.
   */
  public static final String SYNTAX_TELETEX_TERM_ID_NAME =
       "TeletexTerminalIdentifier";



  /**
   * The OID for the teletex terminal identifier attribute syntax.
   */
  public static final String SYNTAX_TELETEX_TERM_ID_OID =
       "1.3.6.1.4.1.1466.115.121.1.51";



  /**
   * The description for the telex number attribute syntax.
   */
  public static final String SYNTAX_TELEX_DESCRIPTION = "Telex Number";



  /**
   * The name for the telex number attribute syntax.
   */
  public static final String SYNTAX_TELEX_NAME = "TelexNumber";



  /**
   * The OID for the telex number attribute syntax.
   */
  public static final String SYNTAX_TELEX_OID = "1.3.6.1.4.1.1466.115.121.1.52";



  /**
   * The description for the user password attribute syntax.
   */
  public static final String SYNTAX_USER_PASSWORD_DESCRIPTION =
       "User Password Syntax";



  /**
   * The name for the user password attribute syntax.
   */
  public static final String SYNTAX_USER_PASSWORD_NAME =
       "ds-syntax-user-password";



  /**
   * The OID for the user password attribute syntax.
   */
  public static final String SYNTAX_USER_PASSWORD_OID =
       OID_OPENDS_SERVER_ATTRIBUTE_SYNTAX_BASE + ".1";



  /**
   * The description for the UTC time attribute syntax.
   */
  public static final String SYNTAX_UTC_TIME_DESCRIPTION =
       "UTC Time";



  /**
   * The name for the UTC time attribute syntax.
   */
  public static final String SYNTAX_UTC_TIME_NAME = "UTCTime";



  /**
   * The OID for the UTC time attribute syntax.
   */
  public static final String SYNTAX_UTC_TIME_OID =
       "1.3.6.1.4.1.1466.115.121.1.53";



  /**
   * The description for the UUID attribute syntax.
   */
  public static final String SYNTAX_UUID_DESCRIPTION = "UUID";



  /**
   * The name for the UUID attribute syntax.
   */
  public static final String SYNTAX_UUID_NAME = "UUID";



  /**
   * The OID for the UUID attribute syntax.
   */
  public static final String SYNTAX_UUID_OID =
       "1.3.6.1.1.16.1";



  /**
   * The description for the "top" objectclass.
   */
  public static final String TOP_OBJECTCLASS_DESCRIPTION =
       "Topmost ObjectClass";



  /**
   * The name of the "top" objectclass.
   */
  public static final String TOP_OBJECTCLASS_NAME = "top";



  /**
   * The OID for the "top" objectclass.
   */
  public static final String TOP_OBJECTCLASS_OID = "2.5.6.0";
}

