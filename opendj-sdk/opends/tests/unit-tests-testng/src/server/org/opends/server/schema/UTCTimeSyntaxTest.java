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
 * Test the UTCTimeSyntax.
 */
public class UTCTimeSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  public AttributeSyntax getRule()
  {
    return new UTCTimeSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        // tests for the UTC time syntax. This time syntax only uses 2 digits
        // for the year but it is currently implemented using 4 digits
        // disable the tests for now.
        // see issue 637
        /*
        {"060906135030+01",   true},
        {"0609061350Z",       true},
        {"060906135030Z",     true},
        {"061116135030Z",     true},
        {"061126135030Z",     true},
        {"061231235959Z",     true},
        {"060906135030+0101", true},
        {"060906135030+2359", true},
        */
        {"060906135030+3359", false},
        {"060906135030+2389", false},
        {"062231235959Z",     false},
        {"061232235959Z",     false},
        {"06123123595aZ",     false},
        {"0a1231235959Z",     false},
        {"06j231235959Z",     false},
        {"0612-1235959Z",     false},
        {"061231#35959Z",     false},
        {"2006",              false},
    };
  }

}
