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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.util.RemoveOnceSDKSchemaIsUsed;
import org.testng.annotations.DataProvider;

/**
 * Test the IA5StringSyntax.
 */
@RemoveOnceSDKSchemaIsUsed
public class IntegerSyntaxTest extends AttributeSyntaxTest
{

  /** {@inheritDoc} */
  @Override
  protected AttributeSyntax getRule()
  {
    return new IntegerSyntax();
  }

  /** {@inheritDoc} */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"123", true},
        {"987654321", true},
        {"-1", true},
        {"10001", true},
        {"001", false},
        {"-01", false},
        {"12345678\u2163", false},
        {" 123", false},
        {"123 ", false}
    };
  }

}
