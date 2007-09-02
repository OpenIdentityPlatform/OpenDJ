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

import org.opends.server.api.AttributeSyntax;
import org.testng.annotations.DataProvider;

/**
 * Test the GuideSyntax.
 */
public class GuideSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax getRule()
  {
    return new GuideSyntax();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"sn$EQ|!(sn$EQ)", true},
        {"!(sn$EQ)", true},
        {"person#sn$EQ", true},
        {"(sn$EQ)", true},
        {"sn$EQ", true},
        {"sn$SUBSTR", true},
        {"sn$GE", true},
        {"sn$LE", true},
        {"sn$ME", false},
        {"?true", true},
        {"?false", true},
        {"true|sn$GE", false},
        {"sn$APPROX", true},
        {"sn$EQ|(sn$EQ)", true},
        {"sn$EQ|(sn$EQ", false},
        {"sn$EQ|(sn$EQ)|sn$EQ", true},
        {"sn$EQ|(cn$APPROX&?false)", true},
        {"sn$EQ|(cn$APPROX&|?false)", false},
    };
  }

}
