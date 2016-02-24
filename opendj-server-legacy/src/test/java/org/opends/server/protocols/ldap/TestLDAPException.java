/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.server.protocols.ldap;

import org.testng.annotations.Test;

import static org.opends.messages.ProtocolMessages.ERR_LDAP_MESSAGE_DECODE_MESSAGE_ID;
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
    LocalizableMessageDescriptor.Arg1 msgDesc = ERR_LDAP_MESSAGE_DECODE_MESSAGE_ID;
    LDAPException ex = new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgDesc.get(""));
    assertEquals(ex.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    assertTrue(StaticUtils.hasDescriptor(ex.getMessageObject(), msgDesc));
  }

  @Test
  public void testLDAPExceptionThrowable()
  {
    LocalizableMessageDescriptor.Arg1 msgID = ERR_LDAP_MESSAGE_DECODE_MESSAGE_ID;
    LDAPException ex = new LDAPException(LDAPResultCode.OTHER, msgID.get(""));
    LocalizableMessageDescriptor.Arg1<Object> msgID1 = ERR_ECN_CANNOT_DECODE_VALUE;
    new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID1.get(""), ex);
    assertEquals(ex.getResultCode(), LDAPResultCode.OTHER);
    assertTrue(StaticUtils.hasDescriptor(ex.getMessageObject(), msgID));
  }
}
