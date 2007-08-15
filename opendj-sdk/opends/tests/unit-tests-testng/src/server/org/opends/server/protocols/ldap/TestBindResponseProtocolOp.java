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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import java.util.ArrayList;

import java.util.List;
import org.opends.server.TestCaseUtils;
import org.opends.messages.Message;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ResultCode;
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
        ASN1OctetString serverSASLCredentials =
            new ASN1OctetString(saslCreds);
        BindResponseProtocolOp r =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    serverSASLCredentials);
        toString(r);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseTooFew() throws Exception {
        BindResponseProtocolOp busyResp =
            new BindResponseProtocolOp(busyCode.getIntValue());
        tooFewElements(busyResp, OP_TYPE_BIND_RESPONSE);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseTooMany() throws Exception {
        BindResponseProtocolOp busyResp =
            new BindResponseProtocolOp(busyCode.getIntValue());
        tooManyElements(busyResp, OP_TYPE_BIND_RESPONSE);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseBadResult() throws Exception {
        BindResponseProtocolOp busyResp =
            new BindResponseProtocolOp(busyCode.getIntValue());
        badIntegerElement(busyResp, OP_TYPE_BIND_RESPONSE,0);
    }

    @Test (expectedExceptions = LDAPException.class)
    public void testBindResponseBadReferral() throws Exception {
        List<String> referralURLs=new ArrayList<String>();
        referralURLs.add(url);
        DN responseDn = DN.decode(dn);
        ASN1OctetString serverSASLCredentials =
            new ASN1OctetString(saslCreds);
        BindResponseProtocolOp r =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    serverSASLCredentials);
        badIntegerElement(r,OP_TYPE_BIND_RESPONSE,3);
    }

    @Test
    public void testBindResponseEncodeDecode() throws Exception {
        List<String> referralURLs=new ArrayList<String>();
        referralURLs.add(url);
        DN responseDn = DN.decode(dn);
        ASN1OctetString serverSASLCredentials =
            new ASN1OctetString(saslCreds);

        BindResponseProtocolOp saslOkResp =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    serverSASLCredentials);
        BindResponseProtocolOp busyResp =
            new BindResponseProtocolOp(busyCode.getIntValue());
        BindResponseProtocolOp invalidSyntaxResp =
            new BindResponseProtocolOp(invalidSyntaxCode.getIntValue(),
                                                              message);
        ASN1Element saslOkElem=saslOkResp.encode();
        ASN1Element busyElem=busyResp.encode();
        ASN1Element invalidSyntaxElem=invalidSyntaxResp.encode();

        ProtocolOp saslOkDec= ProtocolOp.decode(saslOkElem);
        ProtocolOp busyDec = ProtocolOp.decode(busyElem);
        ProtocolOp invalidSyntaxDec = ProtocolOp.decode(invalidSyntaxElem);

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
    @Test()
    public void testSetters() throws Exception {
        List<String> referralURLs=new ArrayList<String>();
        referralURLs.add(url);
        List<String> referralURL2s=new ArrayList<String>();
        referralURL2s.add(url2);
        DN responseDn = DN.decode(dn);
        DN responseDn2 = DN.decode(dn2);
        BindResponseProtocolOp resp =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs);
        resp.encode();
        resp.setErrorMessage(message2);
        resp.setMatchedDN(responseDn2);
        resp.setReferralURLs(referralURL2s);
        resp.setResultCode(busyCode.getIntValue());
        ASN1Element respElem=resp.encode();
        ProtocolOp respDec= ProtocolOp.decode(respElem);
        BindResponseProtocolOp respOp =
            (BindResponseProtocolOp)respDec;
        assertTrue(respOp.getResultCode() == resp.getResultCode());
        DN dn1=resp.getMatchedDN();
        DN dn2=respOp.getMatchedDN();
        assertTrue(dn1.equals(dn2));
        assertTrue(respOp.getErrorMessage().equals(resp.getErrorMessage()));
        List<String> list1 = resp.getReferralURLs();
        List<String> list2 = respOp.getReferralURLs();
        assertTrue(list1.equals(list2));
        ASN1OctetString creds =
            new ASN1OctetString(saslCreds);
        ASN1OctetString creds2 =
            new ASN1OctetString(saslCreds2);
        BindResponseProtocolOp sasl =
            new BindResponseProtocolOp(okCode.getIntValue(),
                    message, responseDn, referralURLs,
                    creds);
        sasl.encode();
        sasl.setServerSASLCredentials(creds2);
        ASN1Element saslElem=sasl.encode();
        ProtocolOp saslDec= ProtocolOp.decode(saslElem);
        BindResponseProtocolOp saslOp =
            (BindResponseProtocolOp)saslDec;
        String str1=sasl.getServerSASLCredentials().toString();
        String str2=saslOp.getServerSASLCredentials().toString();
        assertTrue(str1.equals(str2));
    }
}
