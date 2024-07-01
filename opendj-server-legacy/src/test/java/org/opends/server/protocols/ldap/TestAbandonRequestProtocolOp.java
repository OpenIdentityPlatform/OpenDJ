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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.types.LDAPException;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.testng.annotations.Test;
import static org.opends.server.protocols.ldap.LDAPConstants.OP_TYPE_ABANDON_REQUEST;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.io.ASN1Reader;
import static org.testng.Assert.*;

public class
TestAbandonRequestProtocolOp extends LdapTestCase {

  static int id=56;

  @Test
  public void testAbandonRequestToString() throws Exception
  {
    toString(new AbandonRequestProtocolOp(id));
  }

  @Test
  public void testAbandonRequestEncodeDecode() throws Exception {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    AbandonRequestProtocolOp req = new AbandonRequestProtocolOp(id);
    req.write(writer);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    ProtocolOp reqOp= LDAPReader.readProtocolOp(reader);
    assertSame(reqOp.getProtocolOpName(), req.getProtocolOpName());
    assertEquals(reqOp.getType(), req.getType());
  }

  @Test (expectedExceptions = LDAPException.class)
  public void testAbandonRequestBadID() throws Exception {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeOctetString(OP_TYPE_ABANDON_REQUEST, "invalid value");

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
  }
}
