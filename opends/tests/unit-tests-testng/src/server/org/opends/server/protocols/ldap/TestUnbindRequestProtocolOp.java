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

import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.types.ByteStringBuilder;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


public class TestUnbindRequestProtocolOp  extends LdapTestCase {

  @Test()
  public void testUnbindEncodeDecodeRequest() throws Exception {
      ByteStringBuilder builder = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(builder);
      UnbindRequestProtocolOp req = new UnbindRequestProtocolOp();
      req.write(writer);
      ASN1Reader reader = ASN1.getReader(builder.toByteString());
      ProtocolOp reqOp = LDAPReader.readProtocolOp(reader);
      assertTrue(reqOp.getProtocolOpName() == req.getProtocolOpName());
      assertTrue(reqOp.getType() == req.getType());
  }
  
  @Test ()
  public void testUnbindRequestToString() throws Exception
  {
      UnbindRequestProtocolOp r = 
          new UnbindRequestProtocolOp();
      toString(r);
  }
}
