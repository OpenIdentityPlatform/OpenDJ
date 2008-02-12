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

import org.testng.annotations.Test;
import static org.opends.messages.ProtocolMessages.ERR_ECN_INVALID_ELEMENT_TYPE;
import static org.opends.messages.ProtocolMessages.ERR_ECN_CANNOT_DECODE_VALUE;
import org.opends.messages.MessageDescriptor;
import static org.testng.Assert.*;
import org.opends.server.types.LDAPException;

public class TestLDAPException extends LdapTestCase {

  @Test()
  public void testLDAPException() {
    MessageDescriptor.Arg1<CharSequence> msgDesc = ERR_ECN_INVALID_ELEMENT_TYPE;
    LDAPException ex=new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgDesc.get(""));
    assertTrue(ex.getResultCode() == LDAPResultCode.PROTOCOL_ERROR);
    assertTrue(ex.getMessageObject().getDescriptor() == msgDesc);
  }

  @Test()
  public void testLDAPExceptionThrowable() {
    MessageDescriptor.Arg1<CharSequence>    msgID   = ERR_ECN_INVALID_ELEMENT_TYPE;
    LDAPException ex=new LDAPException(LDAPResultCode.OTHER, msgID.get(""));
    MessageDescriptor.Arg1<CharSequence>    msgID1  = ERR_ECN_CANNOT_DECODE_VALUE;
    new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID1.get(""), ex);
    assertTrue(ex.getResultCode() == LDAPResultCode.OTHER);
    assertTrue(ex.getMessageObject().getDescriptor() == msgID);
  }
}
