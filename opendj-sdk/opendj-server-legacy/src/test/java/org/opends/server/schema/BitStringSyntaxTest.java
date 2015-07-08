/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.schema;

import static org.testng.Assert.*;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the BitStringSyntax.
 */
@RemoveOnceSDKSchemaIsUsed
public class BitStringSyntaxTest extends AttributeSyntaxTest
{

  /** {@inheritDoc} */
  @Override
  protected AttributeSyntax getRule()
  {
    return new BitStringSyntax();
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  @Test(dataProvider= "acceptableValues")
  public void testAcceptableValues(String value, Boolean result) throws Exception
  {
    Syntax syntax = new BitStringSyntax().getSDKSyntax(CoreSchema.getInstance());

    ByteString byteStringValue = ByteString.valueOf(value);

    LocalizableMessageBuilder reason = new LocalizableMessageBuilder();
    Boolean liveResult =
      syntax.valueIsAcceptable(byteStringValue, reason);

    if (liveResult != result)
    {
      fail(syntax + ".valueIsAcceptable gave bad result for " + value + " reason : " + reason);
    }
  }
}
