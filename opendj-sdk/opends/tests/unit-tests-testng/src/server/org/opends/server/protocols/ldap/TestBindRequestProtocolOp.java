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

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1Long;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.types.AuthenticationType;
import org.testng.annotations.Test;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
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
		ASN1OctetString bindDn=new ASN1OctetString(dn);
		ASN1OctetString pw=new ASN1OctetString(pwd);
		BindRequestProtocolOp simple =
			new BindRequestProtocolOp(bindDn, 3, pw);
		BindRequestProtocolOp sasl =
			new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
		ASN1Element simpleElement = simple.encode();
		ASN1Element saslElement = sasl.encode();
		// Decode to a new protocol op.
		ProtocolOp simpleDecodedOp = ProtocolOp.decode(simpleElement);
		ProtocolOp saslDecodedOp = ProtocolOp.decode(saslElement);
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
		ASN1OctetString bindDn=new ASN1OctetString(dn);
		ASN1OctetString pw=new ASN1OctetString(pwd);
		BindRequestProtocolOp sasl =
			new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
		StringBuilder sb = new StringBuilder();
		sasl.toString(sb);
		sasl.toString(sb, 1);
	}
	
	 @Test (expectedExceptions = LDAPException.class)
	  public void testBadBindRequestSequence() throws Exception
	  {
	    ProtocolOp.decode(new ASN1Integer(OP_TYPE_BIND_REQUEST, 0));
	  }
	 
	 @Test (expectedExceptions = LDAPException.class)
	 public void testInvalidBindRequestTooManyElements() throws Exception
	 {
	     ASN1OctetString bindDn=new ASN1OctetString(dn);
	     ASN1OctetString pw=new ASN1OctetString(pwd);
	     BindRequestProtocolOp sasl =
	         new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
	     tooManyElements(sasl, OP_TYPE_BIND_REQUEST);
	 }

	 @Test (expectedExceptions = LDAPException.class)
	 public void testInvalidBindRequestTooFewElements() throws Exception
	 {
	     ASN1OctetString bindDn=new ASN1OctetString(dn);
	     ASN1OctetString pw=new ASN1OctetString(pwd);
	     BindRequestProtocolOp sasl =
	         new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
	     tooFewElements(sasl, OP_TYPE_BIND_REQUEST);
	 }

	 @Test (expectedExceptions = LDAPException.class)
	 public void testInvalidBindRequestProtoVersion() throws Exception
	 {
		 ASN1OctetString bindDn=new ASN1OctetString(dn);
		 ASN1OctetString pw=new ASN1OctetString(pwd);
		 BindRequestProtocolOp sasl =
			 new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
		 ASN1Element element = sasl.encode();
		 ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
		 elements.set(0, new ASN1Long(Long.MAX_VALUE));
		 ProtocolOp.decode(new ASN1Sequence(OP_TYPE_BIND_REQUEST, elements));
	 }

	 @Test()
	 public void testBindRequestSetters()  throws Exception {
	     ASN1OctetString bindDn=new ASN1OctetString(dn);
	     ASN1OctetString pw=new ASN1OctetString(pwd);
	     ASN1OctetString newBindDn=new ASN1OctetString(newDn);
	     ASN1OctetString newPw=new ASN1OctetString(newPwd);
         ASN1OctetString saslCreds=new ASN1OctetString(creds);
         
	     BindRequestProtocolOp sasl =
	         new BindRequestProtocolOp(bindDn, SASL_MECHANISM_PLAIN, pw);
	     BindRequestProtocolOp simple =
	         new BindRequestProtocolOp(bindDn, 3, pw);
	     simple.encode();
	     sasl.encode();
	     sasl.setDN(newBindDn);
	     simple.setDN(newBindDn);
	     simple.setSimplePassword(newPw);
         sasl.setProtocolVersion(2);
         simple.setProtocolVersion(2);
         sasl.setSASLAuthenticationInfo(SASL_MECHANISM_GSSAPI, saslCreds);
         sasl.setAuthenticationType(AuthenticationType.INTERNAL);
         
	     ASN1Element simpleElement = simple.encode();
	     ASN1Element saslElement = sasl.encode();
	     // Decode to a new protocol op.
	     ProtocolOp simpleDecodedOp = ProtocolOp.decode(simpleElement);
	     ProtocolOp saslDecodedOp = ProtocolOp.decode(saslElement);
	     assertTrue(saslDecodedOp instanceof BindRequestProtocolOp);
	     assertTrue(simpleDecodedOp instanceof BindRequestProtocolOp);
	     BindRequestProtocolOp simpleOp =
	         (BindRequestProtocolOp)simpleDecodedOp;
	     BindRequestProtocolOp saslOp =
	         (BindRequestProtocolOp)saslDecodedOp;
         
	     assertTrue(saslOp.getDN().equals(sasl.getDN()));
	     assertTrue(simpleOp.getDN().equals(simple.getDN()));
	     ASN1OctetString sPwd=simple.getSimplePassword();
         int sProtoVer=simple.getProtocolVersion();
	     assertTrue(simpleOp.getSimplePassword().equals(sPwd));
         assertTrue(simpleOp.getProtocolVersion() == sProtoVer);
         assertTrue(saslOp.getProtocolVersion() == sasl.getProtocolVersion());
         String  saslTypeStr=sasl.getAuthenticationType().toString();
         String saslOpTypeStr=saslOp.getAuthenticationType().toString();
 //        Reporter.log(saslTypeStr);
 //        Reporter.log(saslOpTypeStr);
         assertFalse(saslOpTypeStr.equals(saslTypeStr));
	 }
}
