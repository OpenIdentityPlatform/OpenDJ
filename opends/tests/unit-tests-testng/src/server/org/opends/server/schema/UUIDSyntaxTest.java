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
package org.opends.server.schema;

import org.opends.server.api.AttributeSyntax;
import org.testng.annotations.DataProvider;

/**
 * Test the UUIDSyntax.
 */
public class UUIDSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
  {
    return new UUIDSyntax();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        {"12345678-9ABC-DEF0-1234-1234567890ab", true},
        {"12345678-9abc-def0-1234-1234567890ab", true},
        {"12345678-9abc-def0-1234-1234567890ab", true},
        {"12345678-9abc-def0-1234-1234567890ab", true},
        {"02345678-9abc-def0-1234-1234567890ab", true},
        {"12345678-9abc-def0-1234-1234567890ab", true},
        {"12345678-9abc-def0-1234-1234567890ab", true},
        {"02345678-9abc-def0-1234-1234567890ab", true},
        {"G2345678-9abc-def0-1234-1234567890ab", false},
        {"g2345678-9abc-def0-1234-1234567890ab", false},
        {"12345678/9abc/def0/1234/1234567890ab", false},
        {"12345678-9abc-def0-1234-1234567890a", false},
    };
  }

}
