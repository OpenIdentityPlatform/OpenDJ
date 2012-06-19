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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;

import static org.opends.server.util.ServerConstants.OID_ECL_COOKIE_EXCHANGE_CONTROL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPReader;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.types.ByteStringBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test ChangeNumber and ChangeNumberGenerator
 */
public class ExternalChangelogControlTest
    extends ControlsTestCase
{

  /**
   * Create values for External Changelog Request Control
   */
  @DataProvider(name = "eclRequestControl")
  public Object[][] createECLRequestControlTest()
  {

    return new Object[][]
        {
        {true,  "" },
        {false, "o=test:;" },
        {false, "o=test:000001210b6f21e904b100000002;" },
        {false, "o=test:000001210b6f21e904b100000001;o=test2:000001210b6f21e904b100000002;" },
        {false, "o=test:000001210b6f21e904b100000001 000001210b6f21e904b200000001;o=test2:000001210b6f21e904b100000002 000001210b6f21e904b200000002;" },
        };
  }

  /**
   * Test ExternalChangelogRequestControl
   */
  @Test(dataProvider = "eclRequestControl")
  public void checkECLRequestControlTest(boolean critical, String value)
      throws Exception
  {
    // Test contructor
    MultiDomainServerState mdss = new MultiDomainServerState(value);
    ExternalChangelogRequestControl eclrc 
      = new ExternalChangelogRequestControl(critical, mdss);
    assertNotNull(eclrc);
    assertEquals(critical, eclrc.isCritical());
    assertEquals(OID_ECL_COOKIE_EXCHANGE_CONTROL, eclrc.getOID());
    assertTrue(eclrc.getCookie().equalsTo(mdss));

    // Test encode/decode
    ByteStringBuilder bsb = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(bsb);
    bsb.clear();
    eclrc.write(writer);
    LDAPControl control = LDAPReader.readControl(ASN1.getReader(bsb));
    eclrc =
      ExternalChangelogRequestControl.DECODER.decode(control.isCritical(), control.getValue());
    assertNotNull(eclrc);
    assertEquals(critical, eclrc.isCritical());
    assertEquals(OID_ECL_COOKIE_EXCHANGE_CONTROL, eclrc.getOID());
    assertTrue(eclrc.getCookie().equalsTo(mdss),
       "Expect:"+value+", Got:"+eclrc.getCookie().toString());
  }
}
