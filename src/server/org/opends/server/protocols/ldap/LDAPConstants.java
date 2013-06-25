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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



/**
 * This class defines a number of constants used in the LDAP protocol.
 */
public class LDAPConstants
{
  /**
   * The protocol op type for bind requests.
   */
  public static final byte OP_TYPE_BIND_REQUEST = 0x60;



  /**
   * The protocol op type for bind responses.
   */
  public static final byte OP_TYPE_BIND_RESPONSE = 0x61;



  /**
   * The protocol op type for unbind requests.
   */
  public static final byte OP_TYPE_UNBIND_REQUEST = 0x42;



  /**
   * The protocol op type for search requests.
   */
  public static final byte OP_TYPE_SEARCH_REQUEST = 0x63;



  /**
   * The protocol op type for search result entries.
   */
  public static final byte OP_TYPE_SEARCH_RESULT_ENTRY = 0x64;



  /**
   * The protocol op type for search result references.
   */
  public static final byte OP_TYPE_SEARCH_RESULT_REFERENCE = 0x73;



  /**
   * The protocol op type for search result done elements.
   */
  public static final byte OP_TYPE_SEARCH_RESULT_DONE = 0x65;



  /**
   * The protocol op type for modify requests.
   */
  public static final byte OP_TYPE_MODIFY_REQUEST = 0x66;



  /**
   * The protocol op type for modify responses.
   */
  public static final byte OP_TYPE_MODIFY_RESPONSE = 0x67;



  /**
   * The protocol op type for add requests.
   */
  public static final byte OP_TYPE_ADD_REQUEST = 0x68;



  /**
   * The protocol op type for add responses.
   */
  public static final byte OP_TYPE_ADD_RESPONSE = 0x69;



  /**
   * The protocol op type for delete requests.
   */
  public static final byte OP_TYPE_DELETE_REQUEST = 0x4A;



  /**
   * The protocol op type for delete responses.
   */
  public static final byte OP_TYPE_DELETE_RESPONSE = 0x6B;



  /**
   * The protocol op type for modify DN requests.
   */
  public static final byte OP_TYPE_MODIFY_DN_REQUEST = 0x6C;



  /**
   * The protocol op type for modify DN responses.
   */
  public static final byte OP_TYPE_MODIFY_DN_RESPONSE = 0x6D;



  /**
   * The protocol op type for compare requests.
   */
  public static final byte OP_TYPE_COMPARE_REQUEST = 0x6E;



  /**
   * The protocol op type for compare responses.
   */
  public static final byte OP_TYPE_COMPARE_RESPONSE = 0x6F;



  /**
   * The protocol op type for abandon requests.
   */
  public static final byte OP_TYPE_ABANDON_REQUEST = 0x50;



  /**
   * The protocol op type for extended requests.
   */
  public static final byte OP_TYPE_EXTENDED_REQUEST = 0x77;



  /**
   * The protocol op type for extended responses.
   */
  public static final byte OP_TYPE_EXTENDED_RESPONSE = 0x78;



  /**
   * The protocol op type for intermediate responses.
   */
  public static final byte OP_TYPE_INTERMEDIATE_RESPONSE = 0x79;



  /**
   * The BER type to use for encoding the sequence of controls in an LDAP
   * message.
   */
  public static final byte TYPE_CONTROL_SEQUENCE = (byte) 0xA0;



  /**
   * The BER type to use for encoding the sequence of referral URLs in an
   * LDAPResult element.
   */
  public static final byte TYPE_REFERRAL_SEQUENCE = (byte) 0xA3;



  /**
   * The BER type to use for the AuthenticationChoice element in a bind request
   * when simple authentication is to be used.
   */
  public static final byte TYPE_AUTHENTICATION_SIMPLE = (byte) 0x80;



  /**
   * The BER type to use for the AuthenticationChoice element in a bind request
   * when SASL authentication is to be used.
   */
  public static final byte TYPE_AUTHENTICATION_SASL = (byte) 0xA3;



  /**
   * The BER type to use for the server SASL credentials in a bind response.
   */
  public static final byte TYPE_SERVER_SASL_CREDENTIALS = (byte) 0x87;



  /**
   * The BER type to use for AND filter components.
   */
  public static final byte TYPE_FILTER_AND = (byte) 0xA0;



  /**
   * The BER type to use for OR filter components.
   */
  public static final byte TYPE_FILTER_OR = (byte) 0xA1;



  /**
   * The BER type to use for NOT filter components.
   */
  public static final byte TYPE_FILTER_NOT = (byte) 0xA2;



  /**
   * The BER type to use for equality filter components.
   */
  public static final byte TYPE_FILTER_EQUALITY = (byte) 0xA3;



  /**
   * The BER type to use for substring filter components.
   */
  public static final byte TYPE_FILTER_SUBSTRING = (byte) 0xA4;



  /**
   * The BER type to use for greater than or equal to filter components.
   */
  public static final byte TYPE_FILTER_GREATER_OR_EQUAL = (byte) 0xA5;



  /**
   * The BER type to use for less than or equal to filter components.
   */
  public static final byte TYPE_FILTER_LESS_OR_EQUAL = (byte) 0xA6;



  /**
   * The BER type to use for presence filter components.
   */
  public static final byte TYPE_FILTER_PRESENCE = (byte) 0x87;



  /**
   * The BER type to use for approximate filter components.
   */
  public static final byte TYPE_FILTER_APPROXIMATE = (byte) 0xA8;



  /**
   * The BER type to use for extensible matching filter components.
   */
  public static final byte TYPE_FILTER_EXTENSIBLE_MATCH = (byte) 0xA9;



  /**
   * The BER type to use for the subInitial component of a substring filter.
   */
  public static final byte TYPE_SUBINITIAL = (byte) 0x80;



  /**
   * The BER type to use for the subAny component(s) of a substring filter.
   */
  public static final byte TYPE_SUBANY = (byte) 0x81;



  /**
   * The BER type to use for the subFinal components of a substring filter.
   */
  public static final byte TYPE_SUBFINAL = (byte) 0x82;



  /**
   * The BER type to use for the matching rule OID in a matching rule assertion.
   */
  public static final byte TYPE_MATCHING_RULE_ID = (byte) 0x81;



  /**
   * The BER type to use for the attribute type in a matching rule assertion.
   */
  public static final byte TYPE_MATCHING_RULE_TYPE = (byte) 0x82;



  /**
   * The BER type to use for the assertion value in a matching rule assertion.
   */
  public static final byte TYPE_MATCHING_RULE_VALUE = (byte) 0x83;



  /**
   * The BER type to use for the DN attributes flag in a matching rule
   * assertion.
   */
  public static final byte TYPE_MATCHING_RULE_DN_ATTRIBUTES = (byte) 0x84;



  /**
   * The BER type to use for the newSuperior component of a modify DN request.
   */
  public static final byte TYPE_MODIFY_DN_NEW_SUPERIOR = (byte) 0x80;



  /**
   * The BER type to use for the OID of an extended request.
   */
  public static final byte TYPE_EXTENDED_REQUEST_OID = (byte) 0x80;



  /**
   * The BER type to use for the value of an extended request.
   */
  public static final byte TYPE_EXTENDED_REQUEST_VALUE = (byte) 0x81;



  /**
   * The BER type to use for the OID of an extended response.
   */
  public static final byte TYPE_EXTENDED_RESPONSE_OID = (byte) 0x8A;



  /**
   * The BER type to use for the value of an extended response.
   */
  public static final byte TYPE_EXTENDED_RESPONSE_VALUE = (byte) 0x8B;



  /**
   * The BER type to use for the OID of an intermediate response message.
   */
  public static final byte TYPE_INTERMEDIATE_RESPONSE_OID = (byte) 0x80;



  /**
   * The BER type to use for the value of an intermediate response message.
   */
  public static final byte TYPE_INTERMEDIATE_RESPONSE_VALUE = (byte) 0x81;



  /**
   * The enumerated type for modify operations that add one or more values for
   * an attribute.
   */
  public static final int MOD_TYPE_ADD = 0;



  /**
   * The enumerated type for modify operations that remove one or more values
   * from an attribute.
   */
  public static final int MOD_TYPE_DELETE = 1;



  /**
   * The enumerated type for modify operations that replace the set of values
   * for an attribute.
   */
  public static final int MOD_TYPE_REPLACE = 2;



  /**
   * The enumerated type for modify operations that increment the value for an
   * attribute.
   */
  public static final int MOD_TYPE_INCREMENT = 3;



  /**
   * The search scope value that will be used for base-level searches.
   */
  public static final int SCOPE_BASE_OBJECT = 0;



  /**
   * The search scope value that will be used for single-level searches.
   */
  public static final int SCOPE_SINGLE_LEVEL = 1;



  /**
   * The search scope value that will be used for whole subtree searches.
   */
  public static final int SCOPE_WHOLE_SUBTREE = 2;



  /**
   * The search scope value that will be used for subordinate subtree searches.
   */
  public static final int SCOPE_SUBORDINATE_SUBTREE = 3;



  /**
   * The alias dereferencing policy value that will be used for cases in which
   * aliases are never to be dereferenced.
   */
  public static final int DEREF_NEVER = 0;



  /**
   * The alias dereferencing policy value that will be used for cases in which
   * any aliases encountered while finding matching entries should be
   * dereferenced.
   */
  public static final int DEREF_IN_SEARCHING = 1;



  /**
   * The alias dereferencing policy value that will be used for cases in which
   * the search base should be dereferenced if it is an alias.
   */
  public static final int DEREF_FINDING_BASE = 2;



  /**
   * The alias dereferencing policy value that will be used for cases in which
   * all aliases encountered should be dereferenced.
   */
  public static final int DEREF_ALWAYS = 3;



  /**
   * The OID for the Kerberos V GSSAPI mechanism.
   */
  public static final String OID_GSSAPI_KERBEROS_V = "1.2.840.113554.1.2.2";



  /**
   * The OID for the LDAP notice of disconnection extended operation.
   */
  public static final String OID_NOTICE_OF_DISCONNECTION =
       "1.3.6.1.4.1.1466.20036";



  /**
   * The ASN.1 element decoding state that indicates that the next byte read
   * should be the BER type for a new element.
   */
  public static final int ELEMENT_READ_STATE_NEED_TYPE = 0;



  /**
   * The ASN.1 element decoding state that indicates that the next byte read
   * should be the first byte for the element length.
   */
  public static final int ELEMENT_READ_STATE_NEED_FIRST_LENGTH_BYTE = 1;



  /**
   * The ASN.1 element decoding state that indicates that the next byte read
   * should be additional bytes of a multi-byte length.
   */
  public static final int ELEMENT_READ_STATE_NEED_ADDITIONAL_LENGTH_BYTES = 2;



  /**
   * The ASN.1 element decoding state that indicates that the next byte read
   * should be applied to the value of the element.
   */
  public static final int ELEMENT_READ_STATE_NEED_VALUE_BYTES = 3;
}

