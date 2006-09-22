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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


public class TestUnbindRequestProtocolOp  extends LdapTestCase {

    /**
     * Once-only initialization.
     * 
     * @throws Exception
     *           If an unexpected error occurred.
     */
    @BeforeClass
    public void setUp() throws Exception {
        // This test suite depends on having the schema available, so we'll
        // start the server.TestBindResponseProtocolOp
        TestCaseUtils.startServer();
    }


  @Test()
  public void testUnbindRequest() throws Exception {
      UnbindRequestProtocolOp req = new UnbindRequestProtocolOp();
      ASN1Element reqElem=req.encode();
      ProtocolOp reqOp= ProtocolOp.decode(reqElem);
      assertTrue(reqOp.getProtocolOpName() == req.getProtocolOpName());
      assertTrue(reqOp.getType() == req.getType());
  }
  
  @Test ()
  public void testUnbindRequestToString() throws Exception
  {
      UnbindRequestProtocolOp r = 
          new UnbindRequestProtocolOp();
      StringBuilder sb = new StringBuilder();
      r.toString(sb);
      r.toString(sb, 1);
  }
}
