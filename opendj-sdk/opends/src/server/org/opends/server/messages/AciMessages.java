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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.messages;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;

/**
 * The AciMessages class defines the set of message IDs and default format
 * strings for messages associated with the dseecompat access control
 * implementation.
 */
public class AciMessages {

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value cannot be parsed because it failed the non-specific regular
     * expression check during the initial ACI decode process. This takes one
     * argument, which is the string representation of the "aci" attribute
     * type value.
     */
    public static final int MSGID_ACI_SYNTAX_GENERAL_PARSE_FAILED =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 1;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid version string. This
     * takes one argument, which is the version string parsed from the
     * "aci" attribute type value.
     */
    public static final int MSGID_ACI_SYNTAX_INVAILD_VERSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 2;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid access type string. This
     * takes one argument, which is the access type string parsed from the
     * "aci" attribute type value.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_ACCESS_TYPE_VERSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 3;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid rights string. This
     * takes one argument, which is the rights string parsed from the
     * "aci" attribute type value.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_RIGHTS_SYNTAX =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 4;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid rights keyword. This
     * takes one argument, which is the rights keyword string parsed from the
     * rights string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_RIGHTS_KEYWORD =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 5;

    /**
     * The message ID for the message that will be used if an ACI bind rule
     * value failed parsing because it starts with an open parenthesis,
     * but does not contain a matching close parenthesis.  This takes one
     * argument, which is the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_BIND_RULE_MISSING_CLOSE_PAREN =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_MILD_ERROR | 6;

    /**
     * The message ID for the message that will be used if an ACI bind rule
     * value failed parsing because it is an invalid bind rule syntax. This
     * takes one argument, which is the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_BIND_RULE_SYNTAX =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_MILD_ERROR | 7;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid bind rule keyword. This
     * takes one argument, which is the bind rule keyword string parsed from
     * the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 8;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid bind rule operator. This
     * takes one argument, which is the bind rule operator string parsed
     * from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_BIND_RULE_OPERATOR =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 9;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a missing bind rule expression
     * string. This takes one argument, which is the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_MISSING_BIND_RULE_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 10;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid bind rule boolean
     * operator. This takes one argument, which is the bind rule boolean
     * operator string parsed from the bind rule string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_BIND_RULE_BOOLEAN_OPERATOR =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 11;


    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid bind rule keyword,
     * keyword operation combination. This takes two arguments, which are the
     * bind rule keyword string and the bind rule keyword operator parsed from
     * the bind rule string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD_OPERATOR_COMBO =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 12;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userdn LDAP URL failed
     * to decode.  This takes one argument the message from the LDAP
     * URL decode DirectoryException.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_USERDN_URL =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 13;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule roledn expression failed
     * to parse.  This takes one argument, which is the roledn expression
     * string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_ROLEDN_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 14;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule roledn LDAP URL failed
     * to decode.  This takes one argument the message from the LDAP
     * URL decode DirectoryException.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_ROLEDN_URL =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 15;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule groupdn expression failed
     * to parse.  This takes one argument, which is the groupdn expression
     * string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_GROUPDN_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 16;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule groupdn LDAP URL failed
     * to decode.  This takes one argument the message from the LDAP
     * URL decode DirectoryException.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_GROUPDN_URL =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 17;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule ip keyword expression
     * network mask value did not match the expression network address value.
     * For example, the ACI has a IPV6 network mask; but the internet
     * address part is IPV4. This takes two arguments, which are the
     * bind rule ip netmask string and the bind rule ip inet address
     * parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_ADDRESS_FAMILY_MISMATCH =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 18;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule ip keyword expression
     * failed to parse because the number of bits specified to match the
     * network was not valid for the inet address specified. This takes
     * two arguments, which an string specifying the address type
     * (inet6address, inet4address) and an error message.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_NETWORK_BIT_MATCH =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 19;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule ip expression failed
     * to decode.  This takes one argument, the message from the
     * thrown exception.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_IP_CRITERIA_DECODE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 20;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule ip expression failed
     * to parse.  This takes one argument, which is the ip expression
     * string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_IP_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 21;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule dns expression failed
     * to parse.  This takes one argument, which is the dns expression
     * string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_DNS_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 22;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule dns expression failed
     * to parse because a wild-card was not in the leftmost position.
     * This takes one argument, which is the dns expression string parsed
     * from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_DNS_WILDCARD =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 23;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule dayofweek expression failed
     * to parse.  This takes one argument, which is the dayofweek expression
     * string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_DAYOFWEEK =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING |24;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule timeofday expression failed
     * to parse.  This takes one argument, which is the timeofday expression
     * string parsed from the bind rule string.
     */

    public static final int MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING |25;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule timeofday expression failed
     * to parse because the timeofday was not in a valid range.  This takes one
     * argument, which is the timeofday expression string parsed from the
     * bind rule string.
     */

    public static final int MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY_RANGE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING |26;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule authmethod expression failed
     * to parse.  This takes one argument, which is the authmethod expression
     * string parsed from the bind rule string.
     */

    public static final int MSGID_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING |27;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr expression failed
     * to decode.  This takes one argument, the message from the
     * thrown exception.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_USERATTR_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 28;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr expression value
     * is not supported.  This takes one argument, which is the userattr
     * expression string parsed from the bind rule string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_USERATTR_KEYWORD =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 29;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr expression
     * inheritance pattern did not parse.  This takes one argument, which
     * is the userattr expression inheritance pattern string parsed
     * from the bind rule string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_USERATTR_INHERITANCE_PATTERN =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 30;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr expression
     * inheritance level exceeded the max value.  This takes two arguments,
     * which are the userattr expression inheritance pattern string parsed
     * from the bind rule string and the max leval value.
     */
    public static
    final int MSGID_ACI_SYNTAX_MAX_USERATTR_INHERITANCE_LEVEL_EXCEEDED =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 31;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr expression
     * inheritance level was non-numeric.  This takes one argument,
     * which is the userattr expression inheritance level pattern string
     * parsed from the bind rule string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_INHERITANCE_VALUE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 32;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a target rule had an invalid syntax.
     * This takes one argument, which is the target rule string
     * parsed from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGET_SYNTAX =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 33;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid target keyword. This
     * takes one argument, which is the target keyword string parsed
     * from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGET_KEYWORD =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 34;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid target keyword operator.
     * This takes two arguments, which  are the target keyword operator string
     * parsed from the "aci" attribute type value string and the keyword string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGET_NOT_OPERATOR =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 35;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a target keyword is not supported
     * at this time. This takes one argument, which is the unsupported target
     * keyword string parsed from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_TARGET_KEYWORD_NOT_SUPPORTED =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 36;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a target keyword was seen multiple
     * times in the value. This takes two arguments, which are the target
     * keyword string parsed from the "aci" attribute type value string and
     * the "aci" attribute type value string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGET_DUPLICATE_KEYWORDS =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 37;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid target keyword
     * operator. This takes one argument, which is the target keyword
     * operator string parsed from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGETS_OPERATOR =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 38;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid targetscope expression.
     * This takes one argument, which is the targetscope expression
     * string parsed from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGETSCOPE_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 39;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of an invalid target keyword
     * expression.  This takes one argument, which is the target keyword
     * expression string parsed from the "aci" attribute type value string.
     */
    public static final int MSGID_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 40;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a target keyword DN is not a
     * descendant of the ACI entry DN. This takes two arguments, which are
     * the target keyword DN string parsed from the "aci" attribute type value
     * string and the DN of the "aci" attribute type entry.
     */
    public static
    final int MSGID_ACI_SYNTAX_TARGET_DN_NOT_DESCENDENTOF =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 41;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a targetattr keyword expression
     * is invalid. This takes one argument, which is the targetattr
     * keyword expression string parsed from the "aci" attribute type value
     * string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGETATTRKEYWORD_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 42;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value failed parsing because of a targetfilter keyword expression
     * string is invalid. This takes one argument, which is the targetfilter
     * keyword expression string parsed from the "aci" attribute type value
     * string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGETFILTERKEYWORD_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 43;

    /**
     * The message ID for the ACI message that will be generated when a client
     * attempts to add an entry with the "aci" attribute type
     * and they do not have the required "modify-acl"privilege.  This takes two
     * arguments, which are the string representation of the entry DN of the
     * entry being added, and the string representation of the
     * authorization DN.
     */
    public static final int MSGID_ACI_ADD_FAILED_PRIVILEGE =
        CATEGORY_MASK_ACCESS_CONTROL  | 44;


    /**
     * The message ID for the ACI message that will be generated when a client
     * attempts to perform a modification on an "aci" attribute type
     * and they do not have the required "modify-acl"privilege.  This takes two
     * arguments, which are the string representation of the entry DN of the
     * entry being modified, and the string representation of the
     * authorization DN.
     */
    public static final int MSGID_ACI_MODIFY_FAILED_PRIVILEGE =
        CATEGORY_MASK_ACCESS_CONTROL  | 45;

    /**
     * The message ID for the ACI message that will be generated when a client
     * attempts to add an entry with the "aci" attribute type
     * and the ACI decode failed because of an syntax error.  This takes one
     * argument, which is the message string thrown by the AciException.
     */
    public static final int MSGID_ACI_ADD_FAILED_DECODE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 46;

    /**
     * The message ID for the ACI message that will be generated when a client
     * attempts to perform a modification on an "aci" attribute type
     * and the ACI decode failed because of a syntax error.  This takes one
     * argument, which is the message string thrown by the AciException.
     */
    public static final int MSGID_ACI_MODIFY_FAILED_DECODE =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 47;

    /**
     * The message ID for the ACI message that will be generated when
     * an ACI decode failed because of an syntax error. This message is usually
     * generated by an invalid ACI that was added during import which
     * fails the decode at server startup. This takes one
     * argument, which is the message string thrown by the AciException.
     */
    public static final int MSGID_ACI_ADD_LIST_FAILED_DECODE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 48;

    /**
     * The message ID for the ACI message that will be generated the server
     * searches an directory context for "aci" attribute types and finds none.
     * This takes one argument, which is the DN of the directory context.
     */
    public static final int MSGID_ACI_ADD_LIST_NO_ACIS =
        CATEGORY_MASK_ACCESS_CONTROL | 49;

    /**
     * The message ID for the ACI message that will be generated the server
     * searches an directory context for "aci" attribute types and finds some.
     * This takes two arguments, which are the DN of the directory context,
     * the number of valid ACIs decoded.
     */
    public static final int MSGID_ACI_ADD_LIST_ACIS =
        CATEGORY_MASK_ACCESS_CONTROL | 50;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a bind rule userattr roledn expression
     * inheritance pattern did not parse.  This takes one argument, which
     * is the userattr expression inheritance pattern string parsed
     * from the bind rule string.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_USERATTR_ROLEDN_INHERITANCE_PATTERN =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 51;

   /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a targattrfilters keyword expression
     * did not parse because the operation was invalid.  This takes two
     * arguments, which are the targattrfilters expression parsed from the ACI
     * and a message further clarifying the error.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_OPERATION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 52;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because a targattrfiltesr keyword expression
     * did not parse.  This takes one argument, which is the targattrfilters
     * expression parsed from the ACI.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_EXPRESSION =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 53;

      /**
     * The message ID for the message that will be used if an "aci" attribute
     * type value parse failed because the operation tokens targattrfilters
     * in the expresssion are the same.  This takes one argument, which is
     * the targattrfilters expression parsed from the ACI.
     */
    public static
    final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_OPS_MATCH =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 54;

    /**
    * The message ID for the message that will be used if an "aci" attribute
    * type value parse failed because the there are two many targattrfilters
    * filter list statements in the ACI.  This takes one argument, which is
    * the targattrfilters expression parsed from the ACI.
    */
   public static
   final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_MAX_FILTER_LISTS =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 55;

  /**
    * The message ID for the message that will be used if an "aci" attribute
    * type value parse failed because the targattrfilters expression statement
    * is in the wrong format.  This takes one argument, which is
    * the targattrfilters expression parsed from the ACI.
    */
   public static
   final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LIST_FORMAT =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 56;

    /**
      * The message ID for the message that will be used if an "aci" attribute
      * type value parse failed because one or more targattrfilters filter
      * statements are invalid.  This takes two arguments, which are
      * the targattrfilters expression parsed from the ACI and an error
      * message from the createFilterFromString method.
      */
     public static
     final int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_FILTER =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 57;

     /**
      * The message ID for the message that will be used if an "aci" attribute
      * type value parse failed because one or more targattrfilters filter
      * statements contain invalid attribute type names.  This takes one
      * argument, which is the targattrfilters expression parsed from the ACI.
      */
     public static final
        int MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_ATTR_FILTER =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 58;

     /**
      * The message ID for the message that will be used if an "aci" attribute
      * type name is invalid.  This takes one
      * argument, which is the attribute type name parsed from the ACI.
      */
     public static final
        int MSGID_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 59;

     /**
      * The message ID for the message that will be used if a bind rule
      * authmethod expression contains a SASL mechanism that is not currrently
      * registered in the server.  This takes one argument, which is the
      * SASL mechanism string parsed from the authmethod expression.
      */
     public static final
        int MSGID_ACI_SYNTAX_DUBIOUS_AUTHMETHOD_SASL_MECHANISM =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_NOTICE | 60;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * includes a dns hostname of "localhost" that does not match the canonical
     * representation which means that it will likely never match.  This takes
     * three arguments, which are the DNS bind rule expression, the hostname
     * used in the access control rule, and the canonical hostname associated
     * with that name.
     */
    public static final int MSGID_ACI_LOCALHOST_DOESNT_MATCH_CANONICAL_VALUE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 61;

    /**
     * The message ID for the message that will be used if an "aci" attribute
     * includes a dns hostname that does not match the canonical representation,
     * which means that it will likely never match.  This takes four arguments,
     * which are the DNS bind rule expression, the hostname used in the access
     * control rule, an IP address to which that name resolves, and the
     * canonical hostname associated with that IP address.
     */
    public static final int MSGID_ACI_HOSTNAME_DOESNT_MATCH_CANONICAL_VALUE =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 62;

    /**
     * The message ID for the message that will be used if an error occurs while
     * attempting to determine whether a DNS hostname used in an access control
     * rule matches its canonical representation.  This takes three arguments,
     * which are the hostname used in the access control rule, the DNS bind rule
     * expression, and a string representation of the exception that was caught.
     */
    public static final int MSGID_ACI_ERROR_CHECKING_CANONICAL_HOSTNAME =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 63;

    /**
     * The message ID for the message that will be used as the description of
     * the configuration attribute specifying a global ACI.
     */
    public static final
    int MSGID_ACI_DESCRIPTION_GLOBAL_ACI = CATEGORY_MASK_ACCESS_CONTROL | 64;


    /**
     * The message ID for the ACI message that will be generated the server
     * searches an directory context for Global "aci" attribute types and
     * finds none. This takes no arguments.
     */
    public static final int MSGID_ACI_ADD_LIST_NO_GLOBAL_ACIS =
        CATEGORY_MASK_ACCESS_CONTROL | 65;

    /**
     * The message ID for the ACI message that will be generated the server
     * searches the config entry for Global "aci" attribute types and
     * finds some. This takes one argument, which is the number of
     * valid Global ACIs decoded.
     */
    public static final int MSGID_ACI_ADD_LIST_GLOBAL_ACIS =
        CATEGORY_MASK_ACCESS_CONTROL | 66;

    /**
     * The message ID for the ACI message that will be generated when the server
     * searches the config entry for Global "aci" attribute types and
     * an error occurs. This takes one argument, which is the DN of the
     * access control configuration entry.
     */
    public static final int MSGID_ACI_HANDLER_FAIL_PROCESS_GLOBAL_ACI =
        CATEGORY_MASK_ACCESS_CONTROL | 67;

    /**
     * The message ID for the ACI message that will be generated when the server
     * searches the config system for "aci" attribute types and
     * an error occurs. This takes no arguments.
     */
    public static final int MSGID_ACI_HANDLER_FAIL_PROCESS_ACI =
        CATEGORY_MASK_ACCESS_CONTROL | 68;

    /**
     * The message ID for the message that will be used if a DN pattern failed
     * parsing because it contained consecutive wildcards in an attribute value.
     * This takes one argument, which is the invalid DN pattern string.
     */
    public static final int MSGID_PATTERN_DN_CONSECUTIVE_WILDCARDS_IN_VALUE =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 69;


    /**
     * The message ID for the message that will be used if a DN pattern failed
     * parsing because it uses wildcards for substring matching on an attribute
     * type.  This takes one argument, which is the invalid DN pattern string.
     */
    public static final int MSGID_PATTERN_DN_TYPE_CONTAINS_SUBSTRINGS =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 70;


    /**
     * The message ID for the message that will be used if a DN pattern failed
     * parsing because it contained a wildcard match on an attribute type
     * in a multi-valued RDN.  This takes one argument, which is the invalid
     * DN pattern string.
     */
    public static final int MSGID_PATTERN_DN_TYPE_WILDCARD_IN_MULTIVALUED_RDN =
         CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 71;

   /**
     * The message ID for the message that will be used if the server
     * is unable to obtain a lock on a ModifyDN new superior entry.
     * This takes a single argument, which is the DN of the new
     * superior entry.
     */
   public static final int MSGID_ACI_HANDLER_CANNOT_LOCK_NEW_SUPERIOR_USER =
        CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 72;

  /**
   * The message ID for the message that will be used if a attribute type with
   * a DN syntax failed to DN decode in the selfwrite access checking. This
   * takes one argument, which is the invalid DN string.
   */
  public static final int MSGID_ACI_NOT_VALID_DN =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 73;

    /**
   * The message ID for the message that will be used if a targetattr
   * keyword expression contains both operational and user attribute
   * types. This takes one argument, which is the targetattr expression string.
   */
  public static final int MSGID_ACI_TARGETATTR_INVALID_OP_USER_ATTR =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 74;

  /**
   * The message ID for the message that will be used if a targetattr
   * keyword expression performs both an inequality operation using
   * operational attribute types. This takes one argument, which is the
   * targetattr expression string.
   */
  public static final int MSGID_ACI_TARGATTR_INVALID_OP_ATTR_INEQUALITY =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 75;

  /**
   * The message ID for the message that will be used if a roledn
   * keyword expression is parsed. The roledn keyword is not supported.
   * This takes one argument, which is the roledn expression string.
   */
  public static final int MSGID_ACI_SYNTAX_ROLEDN_NOT_SUPPORTED =
       CATEGORY_MASK_ACCESS_CONTROL | SEVERITY_MASK_SEVERE_WARNING | 76;

    /**
     * Associates a set of generic messages with the message IDs defined in
     * this class.
     */
    public static void registerMessages() {

        registerMessage(MSGID_ACI_SYNTAX_GENERAL_PARSE_FAILED,
                "The provided string  \"%s\" could not be parsed as a valid " +
                "Access Control Instruction (ACI) because it failed "+
                "general ACI syntax evaluation");

        registerMessage(MSGID_ACI_SYNTAX_INVAILD_VERSION,
                "The provided Access Control Instruction (ACI) version " +
                "value  \"%s\" is invalid, only the version 3.0 is " +
                "supported");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_ACCESS_TYPE_VERSION,
                "The provided Access Control Instruction access " +
                "type value  \"%s\" is invalid. A valid access type " +
                "value is either allow or deny");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_RIGHTS_SYNTAX,
                "The provided Access Control Instruction (ACI) rights " +
                "values \"%s\" are invalid. The rights must be a " +
                "list of 1 to 6 comma-separated keywords enclosed in " +
                "parentheses");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_RIGHTS_KEYWORD,
                "The provided Access Control Instruction (ACI) rights " +
                "keyword values \"%s\" are invalid. The valid rights " +
                "keyword values are one or more of the following: read, " +
                "write, add, delete, search, compare or the single value " +
                "all");

        registerMessage(MSGID_ACI_SYNTAX_BIND_RULE_MISSING_CLOSE_PAREN,
                "The provided Access Control Instruction (ACI) bind " +
                "rule value \"%s\" is invalid because it is missing a " +
                "close parenthesis that corresponded to the initial open " +
                "parenthesis");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_BIND_RULE_SYNTAX,
                "The provided Access Control Instruction (ACI) bind rule " +
                "value \"%s\" is invalid. A valid bind rule value must " +
                "be in the following form: " +
                "keyword operator \"expression\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD,
                "The provided Access Control Instruction (ACI) bind rule " +
                "keyword value \"%s\" is invalid. A valid keyword value is" +
                " one of the following: userdn, groupdn, roledn, userattr," +
                "ip, dns, dayofweek, timeofday or authmethod");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_BIND_RULE_OPERATOR ,
                "The provided Access Control Instruction (ACI) bind rule " +
                "operator value  \"%s\" is invalid. A valid bind rule " +
                "operator value is either '=' or \"!=\"");

        registerMessage(MSGID_ACI_SYNTAX_MISSING_BIND_RULE_EXPRESSION ,
                "The provided Access Control Instruction (ACI) bind rule " +
                "expression value corresponding to the keyword value " +
                "\"%s\" is missing an expression. A valid bind rule value " +
                "must be in the following form:" +
                " keyword operator \"expression\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_BIND_RULE_BOOLEAN_OPERATOR ,
                "The provided Access Control Instruction (ACI) bind rule " +
                "boolean operator value \"%s\" is invalid. A valid bind" +
                "rule boolean operator value is either \"OR\" or \"AND\"");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_BIND_RULE_KEYWORD_OPERATOR_COMBO,
                "The provided Access Control Instruction (ACI) bind rule " +
                "keyword string  \"%s\" is invalid for the bind rule " +
                "operator string \"%s\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_USERDN_URL,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userdn expression failed to URL decode for " +
                "the following reason: %s");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_ROLEDN_EXPRESSION,
                "The provided Access Control Instruction (ACI) bind rule " +
                "roledn expression value \"%s\" is invalid. A valid roledn " +
                "keyword expression value requires one or more LDAP URLs " +
                "in the following format: " +
                "ldap:///dn [|| ldap:///dn] ... [|| ldap:///dn]");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_ROLEDN_URL,
                "The provided Access Control Instruction (ACI) bind rule " +
                "roledn expression failed to URL decode for " +
                "the following reason: %s");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_GROUPDN_EXPRESSION,
         "The provided Access Control Instruction (ACI) bind rule " +
          "groupdn expression value \"%s\" is invalid. A valid groupdn " +
         "keyword expression  value requires one or more LDAP URLs in the" +
         " following format: " +
         "ldap:///groupdn [|| ldap:///groupdn] ... [|| ldap:///groupdn]");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_GROUPDN_URL,
                "The provided Access Control Instruction (ACI) bind rule " +
                "groupdn expression value failed to URL decode for " +
                "the following reason: %s");

        registerMessage(MSGID_ACI_SYNTAX_ADDRESS_FAMILY_MISMATCH,
                "The network mask value \"%s\" is not valid for " +
                "the ip expression network address \"%s\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_NETWORK_BIT_MATCH,
                "The bit mask for address type value \"%s\" is not valid." +
                "%s");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_IP_CRITERIA_DECODE,
                "The provided Access Control Instruction (ACI) bind rule " +
                "ip expression value failed to decode for " +
                "the following reason: %s");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_IP_EXPRESSION,
                "The provided Access Control Instruction (ACI) bind rule " +
                "ip expression value \"%s\" is invalid. A valid ip " +
                "keyword expression value requires one or more" +
                "comma-separated elements of an IP address list expression");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_DNS_EXPRESSION,
                "The provided Access Control Instruction (ACI) bind rule " +
                "dns expression value \"%s\" is invalid. A valid dns " +
                "keyword expression value requires a valid fully qualified"+
                " DNS domain name");


        registerMessage(MSGID_ACI_SYNTAX_INVALID_DNS_WILDCARD,
                "The provided Access Control Instruction (ACI) bind rule " +
                "dns expression value \"%s\" is invalid, because a wild-card" +
                " pattern was found in the wrong position. A valid dns " +
                "keyword wild-card expression value requires the '*' " +
                "character only be in the leftmost position of the " +
                "domain name");
        registerMessage(MSGID_ACI_LOCALHOST_DOESNT_MATCH_CANONICAL_VALUE,
                "The provided Access Control Instruction (ACI) bind rule " +
                "dns expression value \"%s\" references hostname %s, but " +
                "the canonical representation for that hostname is " +
                "configured to be %s.  The server will attempt to " +
                "automatically interpret the correct localhost value");
        registerMessage(MSGID_ACI_HOSTNAME_DOESNT_MATCH_CANONICAL_VALUE,
                "The provided Access Control Instruction (ACI) bind rule " +
                "dns expression value \"%s\" references hostname %s, which " +
                "resolves to IP address %s, but the canonical hostname for " +
                "that IP address is %s.  This likely means that the " +
                "provided hostname will never match any clients");
        registerMessage(MSGID_ACI_ERROR_CHECKING_CANONICAL_HOSTNAME,
                "An error occurred while attempting to determine whether " +
                "hostname %s referenced in dns expression bind rule \"%s\" " +
                "used the correct canonical representation:  %s.  This " +
                "likely means that the provided hostname will never match " +
                "any clients");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_DAYOFWEEK,
                "The provided Access Control Instruction (ACI) bind rule " +
                "dayofweek expression value \"%s\" is invalid, because of " +
                "an invalid day of week value. A valid dayofweek value " +
                "is one of the following English three-letter abbreviations" +
                "for the days of the week: sun, mon, tue, wed, thu, " +
                "fri, or sat");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY,
                "The provided Access Control Instruction (ACI) bind rule " +
                "timeofday expression value \"%s\" is invalid. A valid " +
                "timeofday value is expressed as four digits representing " +
                "hours and minutes in the 24-hour clock (0 to 2359)");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TIMEOFDAY_RANGE,
                "The provided Access Control Instruction (ACI) bind rule " +
                "timeofday expression value \"%s\" is not in the valid" +
                 " range. A valid timeofday value is expressed as four" +
                 " digits representing hours and minutes in the 24-hour" +
                 " clock (0 to 2359)");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_AUTHMETHOD_EXPRESSION,
                "The provided Access Control Instruction (ACI) bind rule " +
                "authmethod expression value \"%s\" is invalid. A valid " +
                "authmethod value is one of the following: none, simple," +
                "SSL, or \"sasl mechanism\", where mechanism is one of the" +
                "supported SASL mechanisms including CRAM-MD5, DIGEST-MD5, " +
                "and GSSAPI");

        registerMessage(MSGID_ACI_SYNTAX_DUBIOUS_AUTHMETHOD_SASL_MECHANISM,
                "The SASL mechanism \"%s\" provided in the Access Control " +
                "Instruction (ACI) bind rule authmethod expression is not " +
                "one of the currently registered mechanisms in the server");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_USERATTR_EXPRESSION,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression value \"%s\" is invalid");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_USERATTR_KEYWORD,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression value \"%s\" is not supported");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_USERATTR_INHERITANCE_PATTERN,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression inheritance pattern value \"%s\" is " +
                "invalid. A valid inheritance pattern value must have" +
                "the following format:" +
                " parent[inheritance_level].attribute#bindType");

        registerMessage(
                MSGID_ACI_SYNTAX_MAX_USERATTR_INHERITANCE_LEVEL_EXCEEDED,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression inheritance pattern value \"%s\" is " +
                "invalid. The inheritance level value cannot exceed the" +
                "max level limit of %s");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_INHERITANCE_VALUE,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression inheritance pattern value \"%s\" is" +
                " invalid because it is non-numeric");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGET_SYNTAX,
                "The provided Access Control Instruction (ACI) target rule" +
                "value \"%s\" is invalid. A valid target rule value must" +
                "be in the following form: " +
                "keyword operator \"expression\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGET_KEYWORD,
                "The provided Access Control Instruction (ACI) target " +
                "keyword value \"%s\" is invalid. A valid target keyword" +
                " value is one of the following: target, targetscope, " +
                "targetfilter, targetattr or targetattrfilters");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGETS_OPERATOR,
                "The provided Access Control Instruction (ACI) target " +
                "keyword operator value  \"%s\" is invalid. A valid target" +
                "keyword operator value is either '=' or \"!=\"");

        registerMessage(MSGID_ACI_SYNTAX_TARGET_KEYWORD_NOT_SUPPORTED,
                "The provided Access Control Instruction (ACI) " +
                "target keyword value \"%s\" is not supported at this time");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGET_DUPLICATE_KEYWORDS,
                "The provided Access Control Instruction (ACI) " +
                "target keyword value \"%s\" was seen multiple times in" +
                " the ACI \"%s\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGET_NOT_OPERATOR,
                "The provided Access Control Instruction (ACI) target" +
                " operator value \"%s\" is invalid. The only valid" +
                "target operator value for the \"%s\" keyword is '='");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGETSCOPE_EXPRESSION,
                "The provided Access Control Instruction (ACI) targetscope" +
                " expression operator value  \"%s\" is invalid. A valid" +
                " targetscope expression value is one of the following: one," +
                " onelevel or subtree");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION,
                "The provided Access Control Instruction (ACI)" +
                " target expression value \"%s\" is invalid. A valid target" +
                " keyword expression  value requires a LDAP URL in the" +
                " following format: ldap:///distinguished_name_pattern");

        registerMessage(MSGID_ACI_SYNTAX_TARGET_DN_NOT_DESCENDENTOF,
                "The provided Access Control Instruction (ACI) " +
                "target expression DN value \"%s\" is invalid. The target " +
                "expression DN value must be a descendant of the ACI entry" +
                " DN \"%s\", if no wild-card is specified in the target" +
                "expression DN");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGETATTRKEYWORD_EXPRESSION,
                "The provided Access Control Instruction (ACI) " +
                "targetattr expression value \"%s\" is invalid. A valid " +
                "targetattr keyword expression value requires one or more " +
                "valid attribute type names in the following format: " +
                "attribute1 [|| attribute1] ... [|| attributen]");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_TARGETFILTERKEYWORD_EXPRESSION,
                "The provided Access Control Instruction (ACI)" +
                " targetfilter expression value \"%s\" is invalid because it" +
                " is not a valid LDAP filter");

        registerMessage(MSGID_ACI_ADD_FAILED_PRIVILEGE,
                "An attempt to add the entry \"%s\" containing" +
                " an aci attribute type failed, because the authorization DN" +
                " \"%s\" lacked modify-acl privileges");

        registerMessage(MSGID_ACI_MODIFY_FAILED_PRIVILEGE,
                "An attempt to modify an aci "+
                "attribute type in the entry \"%s\" failed, because the" +
                "authorization DN \"%s\" lacked modify-acl privileges");

        registerMessage(MSGID_ACI_ADD_FAILED_DECODE,
                "An attempt to add the entry \"%s\" containing" +
                " an aci attribute type failed because of the following" +
                " reason: %s");

        registerMessage(MSGID_ACI_MODIFY_FAILED_DECODE,
               "An attempt to modify an aci "+
               "attribute type in the entry \"%s\" failed "+
               "because of the following reason: %s");

        registerMessage(MSGID_ACI_ADD_LIST_FAILED_DECODE,
                "An attempt to decode an Access Control Instruction (ACI)" +
                " failed because of the following reason: %s");

        registerMessage(MSGID_ACI_ADD_LIST_NO_ACIS,
                "No Access Control Instruction (ACI) attribute types were" +
                " found in context \"%s\"");

        registerMessage(MSGID_ACI_ADD_LIST_ACIS,
                "Added %s Access Control Instruction (ACI) attribute types" +
                " found in context \"%s\" to the access" +
                "control evaluation engine");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_USERATTR_ROLEDN_INHERITANCE_PATTERN,
                "The provided Access Control Instruction (ACI) bind rule " +
                "userattr expression inheritance pattern value " +
                "\"%s\" is invalid for the roledn keyword because it starts " +
                        "with the string \"parent[\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_OPERATION,
                "The provided Access Control Instruction (ACI) " +
                "targattrfilter expression value " +
                "%s is invalid because %s");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_EXPRESSION,
                "The provided Access Control Instruction (ACI) " +
                "targattrfilter expression value " +
                "%s is invalid because it is not in the correct format." +
                "A valid targattrsfilters expression value must be in " +
                "the following format: "+
               "\"add=attr1: F1 && attr2: F2 ... && attrn: Fn" +
                ",del= attr1: F1 && attr2: F2 ... && attrn: Fn\"");

        registerMessage(MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_OPS_MATCH,
                "The provided Access Control Instruction (ACI) " +
                   "targattrfilter expression value " +
                   "%s is invalid because the both operation tokens " +
                   "match in the two filter lists");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_MAX_FILTER_LISTS,
                "The provided Access Control Instruction (ACI) " +
                     "targattrfilters expression value " +
                     "%s is invalid because there are more than two" +
                      "filter list statements");

        registerMessage(
                MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LIST_FORMAT,
                "The provided Access Control Instruction (ACI) " +
                "targattrfilters expression value " +
                "%s is invalid because the provided filter list string " +
                "is in the wrong format. A valid targattrfilters filter " +
                "list must be in the following format: " +
                "add=attr1: F1 && attr2: F2 ... && attrn: Fn ");

        registerMessage(
             MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_FILTER,
             "The provided Access Control Instruction (ACI) " +
             "targattrfilters expression value " +
             "%s is invalid because one or more of the specified " +
             "filters are invalid for the following reason: " +
             "%s");

        registerMessage(
             MSGID_ACI_SYNTAX_INVALID_TARGATTRFILTERS_FILTER_LISTS_ATTR_FILTER,
             "The provided Access Control Instruction (ACI) " +
             "targattrfilters expression value " +
             "%s is invalid because one or more of the specified " +
             "filters are invalid because of non-matching attribute" +
             "type names in the filter");

        registerMessage(
             MSGID_ACI_SYNTAX_INVALID_ATTRIBUTE_TYPE_NAME,
             "The provided Access Control Instruction (ACI) " +
             "attribute name value " +
             "%s is invalid. A valid attribute type name must begin " +
             "with an ASCII letter and must contain only ASCII letters," +
              "digits or the \"-\" character");

        registerMessage(MSGID_ACI_DESCRIPTION_GLOBAL_ACI,
             "Specifies a global Access Control Instruction (ACI) "  +
             "attribute type that can be used to defined ACIs that have " +
             "global scope accross naming contexts");

        registerMessage(MSGID_ACI_ADD_LIST_NO_GLOBAL_ACIS,
            "No Global Access Control Instruction (ACI) attribute types were" +
           " found");

        registerMessage(MSGID_ACI_ADD_LIST_GLOBAL_ACIS,
                "Added %s Global Access Control Instruction (ACI) attribute " +
                "types to the access control evaluation engine");

        registerMessage(MSGID_ACI_HANDLER_FAIL_PROCESS_GLOBAL_ACI,
         "An unexpected error occurred while processing the " +
         ATTR_AUTHZ_GLOBAL_ACI + " attribute in configuration entry %s");

        registerMessage(MSGID_ACI_HANDLER_FAIL_PROCESS_ACI,
         "An unexpected error occurred while processing the " +
          " aci attributes in the configuration system");

        registerMessage(MSGID_PATTERN_DN_CONSECUTIVE_WILDCARDS_IN_VALUE,
          "The pattern DN %s is not valid because it contains two " +
               "consecutive wildcards in an attribute value");

        registerMessage(MSGID_PATTERN_DN_TYPE_CONTAINS_SUBSTRINGS,
          "The pattern DN %s is not valid because it uses wildcards for " +
               "substring matching on an attribute type.  A single wildcard " +
               "is allowed in place of an attribute type");

        registerMessage(MSGID_PATTERN_DN_TYPE_WILDCARD_IN_MULTIVALUED_RDN,
          "The pattern DN %s is not valid because it contains a wildcard in " +
               "an attribute type in a multi-valued RDN");

      registerMessage(MSGID_ACI_HANDLER_CANNOT_LOCK_NEW_SUPERIOR_USER,
          "Unable to obtain a lock on the ModifyDN new superior entry %s");

      registerMessage(MSGID_ACI_NOT_VALID_DN,
          "Selfwrite check skipped because an attribute \"%s\" with a " +
          "distinguished name syntax was not a valid DN");

      registerMessage(MSGID_ACI_TARGETATTR_INVALID_OP_USER_ATTR,
              "The provided Access Control Instruction (ACI) " +
              "targetattr expression value \"%s\" is invalid because" +
              " the expression contains both operational attribute types" +
              " and user attribute types");

      registerMessage(MSGID_ACI_TARGATTR_INVALID_OP_ATTR_INEQUALITY,
              "The provided Access Control Instruction (ACI) " +
              "targetattr expression value \"%s\" is invalid because" +
              " the expression performs an inequality operation using " +
              "operational attribute types");

      registerMessage(MSGID_ACI_SYNTAX_ROLEDN_NOT_SUPPORTED,
              "The provided Access Control Instruction (ACI) expression " +
              "value \"%s\" is invalid because it contains" +
              " the roledn keyword, which is not supported,  replace it with " +
              "the groupdn keyword");
    }
}
