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

import java.util.ArrayList;

import java.util.List;
import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class TestBindResponseProtocolOp  extends LdapTestCase {

    private static Message message = Message.raw("This is a message");
    private static Message message2 = Message.raw("This is a second message");
    ResultCode        okCode          = ResultCode.SUCCESS;
    ResultCode  busyCode = ResultCode.BUSY;
    ResultCode invalidSyntaxCode = ResultCode.INVALID_ATTRIBUTE_SYNTAX;
    private static String dn = "cn=dn, dc=example,dc=com";
    private static String dn2 = "cn=dn2, dc=example,dc=com";
    private static String saslCreds = "sasl credentials";
    private static String saslCreds2 = "sasl2 credentials";
    private static String url = "ldap://somewhere.example.com";
    private static String url2 = "ldap://somewhere2.example.com";

    /**
     * Once-only initialization.
     *
     * @throws Exception
     *           If an unexpected error occurred.
     */
    @BeforeClass
    public void setUp() throws Exception {
      // This test suite depends on having the schema available, so we'll
      // start the server.
      TestCaseUtils.startServer();
    }


    @Test ()
    public void testBindRequestToString() throws Exception
    {
        List<String> referralURLs=new ArrayList<String>();
        referralURLs.add(url);
        DN responseDn = DN.decode(dn);
        ByteString serverSASLCredentials =
            ByteString.valueOf(saslCreds);
        BindResponseProtocolOp r =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    serverSASLCredentials);
        toString(r);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseTooFew() throws Exception {
      ByteStringBuilder bsb = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(bsb);
      writer.writeStartSequence(OP_TYPE_BIND_RESPONSE);
      writer.writeOctetString((String)null);
      writer.writeOctetString((String)null);
      writer.writeEndSequence();

      ASN1Reader reader = ASN1.getReader(bsb.toByteString());
      LDAPReader.readProtocolOp(reader);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseTooMany() throws Exception {      
      ByteStringBuilder bsb = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(bsb);
      writer.writeStartSequence(OP_TYPE_BIND_RESPONSE);
      writer.writeInteger(okCode.getIntValue());
      writer.writeOctetString((String)null);
      writer.writeOctetString((String)null);
      writer.writeBoolean(true);
      writer.writeEndSequence();

      ASN1Reader reader = ASN1.getReader(bsb.toByteString());
      LDAPReader.readProtocolOp(reader);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseBadResult() throws Exception {
      ByteStringBuilder bsb = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(bsb);
      writer.writeStartSequence(OP_TYPE_BIND_RESPONSE);
      writer.writeOctetString("invalid element");
      writer.writeOctetString((String)null);
      writer.writeOctetString((String)null);
      writer.writeEndSequence();

      ASN1Reader reader = ASN1.getReader(bsb.toByteString());
      LDAPReader.readProtocolOp(reader);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseBadReferral() throws Exception {
      DN responseDn = DN.decode(dn);
      ByteString serverSASLCredentials =
          ByteString.valueOf(saslCreds);

      ByteStringBuilder bsb = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(bsb);
      writer.writeStartSequence(OP_TYPE_BIND_RESPONSE);
      writer.writeInteger(okCode.getIntValue());
      writer.writeOctetString(responseDn.toString());
      writer.writeOctetString(message.toString());
      writer.writeInteger(Long.MAX_VALUE);
      writer.writeOctetString(TYPE_SERVER_SASL_CREDENTIALS,
          serverSASLCredentials);
      writer.writeEndSequence();

      ASN1Reader reader = ASN1.getReader(bsb.toByteString());
      LDAPReader.readProtocolOp(reader);
    }

    @Test
    public void testBindResponseEncodeDecode() throws Exception {
        List<String> referralURLs=new ArrayList<String>();
        referralURLs.add(url);
        DN responseDn = DN.decode(dn);
        ByteString serverSASLCredentials =
            ByteString.valueOf(saslCreds);

        BindResponseProtocolOp saslOkResp =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    serverSASLCredentials);
        BindResponseProtocolOp busyResp =
            new BindResponseProtocolOp(busyCode.getIntValue());
        BindResponseProtocolOp invalidSyntaxResp =
            new BindResponseProtocolOp(invalidSyntaxCode.getIntValue(),
                                                              message);

        ByteStringBuilder saslOkBuilder = new ByteStringBuilder();
        ASN1Writer saslOkWriter = ASN1.getWriter(saslOkBuilder);
        ByteStringBuilder busyBuilder = new ByteStringBuilder();
        ASN1Writer busyWriter = ASN1.getWriter(busyBuilder);
        ByteStringBuilder invalidSyntaxBuilder = new ByteStringBuilder();
        ASN1Writer invalidSyntaxWriter = ASN1.getWriter(invalidSyntaxBuilder);

        saslOkResp.write(saslOkWriter);
        busyResp.write(busyWriter);
        invalidSyntaxResp.write(invalidSyntaxWriter);

        ASN1Reader saslOkReader = ASN1.getReader(saslOkBuilder.toByteString());
        ASN1Reader busyReader = ASN1.getReader(busyBuilder.toByteString());
        ASN1Reader invalidSyntaxReader = ASN1.getReader(invalidSyntaxBuilder.toByteString());
        ProtocolOp saslOkDec= LDAPReader.readProtocolOp(saslOkReader);
        ProtocolOp busyDec = LDAPReader.readProtocolOp(busyReader);
        ProtocolOp invalidSyntaxDec = LDAPReader.readProtocolOp(invalidSyntaxReader);

        assertTrue(saslOkDec instanceof BindResponseProtocolOp);
        assertTrue(busyDec instanceof BindResponseProtocolOp);
        assertTrue(invalidSyntaxDec instanceof BindResponseProtocolOp);

        BindResponseProtocolOp saslOkOp =
             (BindResponseProtocolOp)saslOkDec;
        BindResponseProtocolOp busyOp =
             (BindResponseProtocolOp)busyDec;
        BindResponseProtocolOp invalidSyntaxOp =
             (BindResponseProtocolOp)invalidSyntaxDec;

        assertTrue(saslOkOp.getProtocolOpName() == saslOkResp.getProtocolOpName());
        assertTrue(busyOp.getProtocolOpName() == busyResp.getProtocolOpName());
        assertTrue(invalidSyntaxOp.getProtocolOpName() == invalidSyntaxResp.getProtocolOpName());

        assertTrue(saslOkOp.getType() == saslOkResp.getType());
        assertTrue(busyOp.getType() == busyResp.getType());
        assertTrue(invalidSyntaxOp.getType() == invalidSyntaxResp.getType());

        assertTrue(saslOkOp.getResultCode() == saslOkResp.getResultCode());
        assertTrue(busyOp.getResultCode() == busyResp.getResultCode());
        assertTrue(invalidSyntaxOp.getResultCode() == invalidSyntaxResp.getResultCode());

        assertTrue(saslOkOp.getErrorMessage().equals(saslOkResp.getErrorMessage()));
        assertTrue(invalidSyntaxOp.getErrorMessage().equals(invalidSyntaxResp.getErrorMessage()));

        String str1=saslOkOp.getServerSASLCredentials().toString();
        String str2=saslOkResp.getServerSASLCredentials().toString();
        assertTrue(str1.equals(str2));
        List<String> list1 = saslOkOp.getReferralURLs();
        List<String> list2 = saslOkResp.getReferralURLs();
        assertTrue(list1.equals(list2));
        DN dn1=saslOkOp.getMatchedDN();
        DN dn2=saslOkResp.getMatchedDN();
        assertTrue(dn1.equals(dn2));
    }
}
