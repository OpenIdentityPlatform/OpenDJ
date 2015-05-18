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
 *      Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.dsml.protocol;

import java.util.HashMap;

/**
 * A utility class to help creating ResultCode objects containing a
 * code value (integer) and a descr value (String).
 */
public class ResultCodeFactory
{
  static HashMap<Integer,LDAPResultCode> codeToDescr = new HashMap<>();
  static
  {
    codeToDescr.put(0, LDAPResultCode.SUCCESS);
    codeToDescr.put(1, LDAPResultCode.OPERATIONS_ERROR);
    codeToDescr.put(2, LDAPResultCode.PROTOCOL_ERROR);
    codeToDescr.put(3, LDAPResultCode.TIME_LIMIT_EXCEEDED);
    codeToDescr.put(4, LDAPResultCode.SIZE_LIMIT_EXCEEDED);
    codeToDescr.put(5, LDAPResultCode.COMPARE_FALSE);
    codeToDescr.put(6, LDAPResultCode.COMPARE_TRUE);
    codeToDescr.put(7, LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED);
    // Note not STRONGER_AUTH_REQUIRED, that's the RFC 4511 name
    codeToDescr.put(8, LDAPResultCode.STRONG_AUTH_REQUIRED);
    codeToDescr.put(10, LDAPResultCode.REFERRAL);
    codeToDescr.put(11, LDAPResultCode.ADMIN_LIMIT_EXCEEDED);
    codeToDescr.put(12, LDAPResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
    codeToDescr.put(13, LDAPResultCode.CONFIDENTIALITY_REQUIRED);
    codeToDescr.put(14, LDAPResultCode.SASL_BIND_IN_PROGRESS);
    codeToDescr.put(16, LDAPResultCode.NO_SUCH_ATTRIBUTE);
    codeToDescr.put(17, LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE);
    codeToDescr.put(18, LDAPResultCode.INAPPROPRIATE_MATCHING);
    codeToDescr.put(19, LDAPResultCode.CONSTRAINT_VIOLATION);
    codeToDescr.put(20, LDAPResultCode.ATTRIBUTE_OR_VALUE_EXISTS);
    codeToDescr.put(21, LDAPResultCode.INVALID_ATTRIBUTE_SYNTAX);
    codeToDescr.put(32, LDAPResultCode.NO_SUCH_OBJECT);
    codeToDescr.put(33, LDAPResultCode.ALIAS_PROBLEM);
    codeToDescr.put(34, LDAPResultCode.INVALID_DN_SYNTAX);
    codeToDescr.put(36, LDAPResultCode.ALIAS_DEREFERENCING_PROBLEM);
    codeToDescr.put(48, LDAPResultCode.INAPPROPRIATE_AUTHENTICATION);
    codeToDescr.put(49, LDAPResultCode.INVALID_CREDENTIALS);
    codeToDescr.put(50, LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    codeToDescr.put(51, LDAPResultCode.BUSY);
    codeToDescr.put(52, LDAPResultCode.UNAVAILABLE);
    codeToDescr.put(53, LDAPResultCode.UNWILLING_TO_PERFORM);
    codeToDescr.put(54, LDAPResultCode.LOOP_DETECT);
    codeToDescr.put(64, LDAPResultCode.NAMING_VIOLATION);
    codeToDescr.put(65, LDAPResultCode.OBJECT_CLASS_VIOLATION);
    codeToDescr.put(66, LDAPResultCode.NOT_ALLOWED_ON_NON_LEAF);
    codeToDescr.put(67, LDAPResultCode.NOT_ALLOWED_ON_RDN);
    codeToDescr.put(68, LDAPResultCode.ENTRY_ALREADY_EXISTS);
    codeToDescr.put(69, LDAPResultCode.OBJECT_CLASS_MODS_PROHIBITED);
    // Note not AFFECTS_MULTIPLE_DSAS, xjc mangles the string.
    codeToDescr.put(71, LDAPResultCode.AFFECT_MULTIPLE_DS_AS);
    codeToDescr.put(80, LDAPResultCode.OTHER);
  }

  /**
   * Create a ResultCode object that contains the resultCode, and, if valid,
   * a text description (from RFC 2251) of the resultCode.
   *
   * @param objFactory
   *                  The JAXB factory used to create the underlying object.
   * @param resultCode
   *                  The LDAP result code.
   * @return A ResultCode object with a code and possibly a description.
   */
  public static ResultCode create(ObjectFactory objFactory, int resultCode)
  {
    ResultCode result = objFactory.createResultCode();
    result.setCode(resultCode);
    Integer r = Integer.valueOf(resultCode);
    if (ResultCodeFactory.codeToDescr.containsKey(r))
    {
      result.setDescr(ResultCodeFactory.codeToDescr.get(r));
    }
    return result;
  }
}
