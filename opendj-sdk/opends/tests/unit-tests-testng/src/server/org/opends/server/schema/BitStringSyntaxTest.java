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
package org.opends.server.schema;

import static org.testng.Assert.*;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the BitStringSyntax.
 */
public class BitStringSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
  {
    return new BitStringSyntax();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"'0101'B",  true},
        {"'1'B",     true},
        { "'0'B",    true},
        { "invalid", false},
        { "1",       false},
        {"'010100000111111010101000'B",  true},
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(String value, Boolean result) throws Exception
  {
    BitStringSyntax syntax = new BitStringSyntax();
    syntax.initializeSyntax(null);

    ByteString byteStringValue = new ASN1OctetString(value);

    StringBuilder reason = new StringBuilder();
    Boolean liveResult =
      syntax.valueIsAcceptable(byteStringValue, reason);

    if (liveResult != result)
      fail(syntax + ".valueIsAcceptable gave bad result for " + value +
          "reason : " + reason);

    // call the getters
    syntax.getApproximateMatchingRule();
    syntax.getDescription();

    syntax.getOID();
    syntax.getOrderingMatchingRule();
    syntax.getSubstringMatchingRule();
    syntax.getSyntaxName();
    syntax.toString();
  }
}
