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

import org.opends.server.types.LDAPException;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.testng.annotations.Test;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;
//import org.testng.Reporter;

public class TestBindRequestProtocolOp extends LdapTestCase {
  private static String dn="cn=some dn, dc=example, dc=com";
  private static String pwd="password";
    private static String newPwd="newpassword";
    private static String newDn="cn=new dn, dc=example,dc=com";
    private static String creds="some creds";

  @Test()
  public void testBindRequestEncodeDecode() throws Exception {
    ByteStringBuilder simpleBuilder = new ByteStringBuilder();
    ASN1Writer simpleWriter = ASN1.getWriter(simpleBuilder);
    ByteStringBuilder saslBuilder = new ByteStringBuilder();
    ASN1Writer saslWriter = ASN1.getWriter(saslBuilder);
    ByteString bindDn=ByteString.valueOf(dn);
    ByteString pw=ByteString.valueOf(pwd);
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
    assertTrue(saslOp.getDN().equals(sasl.getDN()));
    assertTrue(simpleOp.getDN().equals(simple.getDN()));

    String simpleOpPwd=simpleOp.getSimplePassword().toString();
    String simplePwd=simple.getSimplePassword().toString();
    assertTrue(simpleOpPwd.equals(simplePwd));

    assertTrue(saslOp.getProtocolOpName() == sasl.getProtocolOpName());
    assertTrue(simpleOp.getProtocolOpName() == simple.getProtocolOpName());

    assertTrue(simpleOp.getProtocolVersion() == simple.getProtocolVersion());
    assertTrue(saslOp.getProtocolVersion() == sasl.getProtocolVersion());

    assertTrue(simpleOp.getType() == simple.getType());
    assertTrue(saslOp.getType() == sasl.getType());

    assertTrue(saslOp.getAuthenticationType().getBERType() ==
      sasl.getAuthenticationType().getBERType());
    assertTrue(simpleOp.getAuthenticationType().getBERType() ==
      simple.getAuthenticationType().getBERType());

    assertTrue(saslOp.getSASLMechanism().equals(sasl.getSASLMechanism()));
    String saslOpCreds=saslOp.getSASLCredentials().toString();
    String saslCreds=sasl.getSASLCredentials().toString();
    assertTrue(saslOpCreds.equals(saslCreds));
  }

  @Test ()
  public void testBindRequestToString() throws Exception
  {
    ByteString bindDn=ByteString.valueOf(dn);
    ByteString pw=ByteString.valueOf(pwd);
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
