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

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.LDAPException;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TestBindRequestProtocolOp extends LdapTestCase {
  private static String dn="cn=some dn, dc=example, dc=com";
  private static String pwd="password";

  @Test
  public void testBindRequestEncodeDecode() throws Exception {
    ByteStringBuilder simpleBuilder = new ByteStringBuilder();
    ASN1Writer simpleWriter = ASN1.getWriter(simpleBuilder);
    ByteStringBuilder saslBuilder = new ByteStringBuilder();
    ASN1Writer saslWriter = ASN1.getWriter(saslBuilder);
    ByteString bindDn=ByteString.valueOfUtf8(dn);
    ByteString pw=ByteString.valueOfUtf8(pwd);
    BindRequestProtocolOp simple =
      new BindRequestProtocolOp(bindDn, 3, pw);
    BindRequestProtocolOp sasl =
      new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
    simple.write(simpleWriter);
    sasl.write(saslWriter);
    // Decode to a new protocol op.
    ASN1Reader simpleReader = ASN1.getReader(simpleBuilder.toByteString());
    ASN1Reader saslReader = ASN1.getReader(saslBuilder.toByteString());
    ProtocolOp simpleDecodedOp = LDAPReader.readProtocolOp(simpleReader);
    ProtocolOp saslDecodedOp = LDAPReader.readProtocolOp(saslReader);
    assertTrue(saslDecodedOp instanceof BindRequestProtocolOp);
    assertTrue(simpleDecodedOp instanceof BindRequestProtocolOp);
    BindRequestProtocolOp simpleOp =
             (BindRequestProtocolOp)simpleDecodedOp;
    BindRequestProtocolOp saslOp =
           (BindRequestProtocolOp)saslDecodedOp;
    assertEquals(saslOp.getDN(), sasl.getDN());
    assertEquals(simpleOp.getDN(), simple.getDN());

    String simpleOpPwd=simpleOp.getSimplePassword().toString();
    String simplePwd=simple.getSimplePassword().toString();
    assertEquals(simpleOpPwd, simplePwd);

    assertSame(saslOp.getProtocolOpName(), sasl.getProtocolOpName());
    assertSame(simpleOp.getProtocolOpName(), simple.getProtocolOpName());

    assertEquals(simpleOp.getProtocolVersion(), simple.getProtocolVersion());
    assertEquals(saslOp.getProtocolVersion(), sasl.getProtocolVersion());

    assertEquals(simpleOp.getType(), simple.getType());
    assertEquals(saslOp.getType(), sasl.getType());

    assertEquals(saslOp.getAuthenticationType().getBERType(), sasl.getAuthenticationType().getBERType());
    assertEquals(simpleOp.getAuthenticationType().getBERType(), simple.getAuthenticationType().getBERType());

    assertEquals(saslOp.getSASLMechanism(), sasl.getSASLMechanism());
    String saslOpCreds=saslOp.getSASLCredentials().toString();
    String saslCreds=sasl.getSASLCredentials().toString();
    assertEquals(saslOpCreds, saslCreds);
  }

  @Test
  public void testBindRequestToString() throws Exception
  {
    ByteString bindDn=ByteString.valueOfUtf8(dn);
    ByteString pw=ByteString.valueOfUtf8(pwd);
    BindRequestProtocolOp sasl =
      new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
    StringBuilder sb = new StringBuilder();
    sasl.toString(sb);
    sasl.toString(sb, 1);
  }

   @Test (expectedExceptions = LDAPException.class)
    public void testBadBindRequestSequence() throws Exception
    {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeInteger(OP_TYPE_BIND_REQUEST, 0);

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
    }

   @Test (expectedExceptions = LDAPException.class)
   public void testInvalidBindRequestTooManyElements() throws Exception
   {
     ByteStringBuilder builder = new ByteStringBuilder();
     ASN1Writer writer = ASN1.getWriter(builder);

     writer.writeStartSequence(OP_TYPE_BIND_REQUEST);
     writer.writeInteger(3);
     writer.writeOctetString(dn);
     writer.writeBoolean(true);
     writer.writeStartSequence(TYPE_AUTHENTICATION_SASL);
     writer.writeOctetString(SASL_MECHANISM_PLAIN);
     writer.writeOctetString(pwd);
     writer.writeEndSequence();
     writer.writeEndSequence();

     ASN1Reader reader = ASN1.getReader(builder.toByteString());
     LDAPReader.readProtocolOp(reader);
   }

   @Test (expectedExceptions = LDAPException.class)
   public void testInvalidBindRequestTooFewElements() throws Exception
   {
     ByteStringBuilder builder = new ByteStringBuilder();
     ASN1Writer writer = ASN1.getWriter(builder);

     writer.writeStartSequence(OP_TYPE_BIND_REQUEST);
     writer.writeOctetString(dn);
     writer.writeStartSequence(TYPE_AUTHENTICATION_SASL);
     writer.writeOctetString(SASL_MECHANISM_PLAIN);
     writer.writeOctetString(pwd);
     writer.writeEndSequence();
     writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
   }

   @Test (expectedExceptions = LDAPException.class)
   public void testInvalidBindRequestProtoVersion() throws Exception
   {
     ByteStringBuilder builder = new ByteStringBuilder();
     ASN1Writer writer = ASN1.getWriter(builder);

     writer.writeStartSequence(OP_TYPE_BIND_REQUEST);
     writer.writeOctetString("invalid element");
     writer.writeOctetString(dn);
     writer.writeStartSequence(TYPE_AUTHENTICATION_SASL);
     writer.writeOctetString(SASL_MECHANISM_PLAIN);
     writer.writeOctetString(pwd);
     writer.writeEndSequence();
     writer.writeEndSequence();

    ASN1Reader reader = ASN1.getReader(builder.toByteString());
    LDAPReader.readProtocolOp(reader);
   }
}
