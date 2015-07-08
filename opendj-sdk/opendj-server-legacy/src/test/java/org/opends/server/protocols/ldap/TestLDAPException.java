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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.server.protocols.ldap;

import org.testng.annotations.Test;

import static org.opends.messages.ProtocolMessages.ERR_ECN_INVALID_ELEMENT_TYPE;
import static org.opends.messages.ProtocolMessages.ERR_ECN_CANNOT_DECODE_VALUE;

import org.forgerock.i18n.LocalizableMessageDescriptor;

import static org.testng.Assert.*;

import org.opends.server.types.LDAPException;
import org.opends.server.util.StaticUtils;

@SuppressWarnings("javadoc")
public class TestLDAPException extends LdapTestCase
{

  @Test
  public void testLDAPException()
  {
    LocalizableMessageDescriptor.Arg1 msgDesc = ERR_ECN_INVALID_ELEMENT_TYPE;
    LDAPException ex =
        new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgDesc.get(""));
    assertEquals(ex.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    assertTrue(StaticUtils.hasDescriptor(ex.getMessageObject(), msgDesc));
  }

  @Test
  public void testLDAPExceptionThrowable()
  {
    LocalizableMessageDescriptor.Arg1 msgID = ERR_ECN_INVALID_ELEMENT_TYPE;
    LDAPException ex = new LDAPException(LDAPResultCode.OTHER, msgID.get(""));
    LocalizableMessageDescriptor.Arg1<Object> msgID1 =
        ERR_ECN_CANNOT_DECODE_VALUE;
    new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID1.get(""), ex);
    assertEquals(ex.getResultCode(), LDAPResultCode.OTHER);
    assertTrue(StaticUtils.hasDescriptor(ex.getMessageObject(), msgID));
  }
}
