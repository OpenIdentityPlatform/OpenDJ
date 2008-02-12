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
package org.opends.server.schema;

import java.util.Date;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.types.AttributeValue;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the UTCTimeSyntax.
 */
public class UTCTimeSyntaxTest extends AttributeSyntaxTest
{

  /**
   * {@inheritDoc}
   */
  @Override
  protected AttributeSyntax getRule()
  {
    return new UTCTimeSyntax();
  }

  @Override
  @DataProvider(name="acceptableValues")
  public Object[][] createAcceptableValues()
  {
    return new Object [][] {
        // tests for the UTC time syntax.
        {"060906135030+01",   true},
        {"0609061350Z",       true},
        {"060906135030Z",     true},
        {"061116135030Z",     true},
        {"061126135030Z",     true},
        {"061231235959Z",     true},
        {"060906135030+0101", true},
        {"060906135030+2359", true},
        {"060906135060+0101", true},
        {"060906135061+0101", false},
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
        {"062106135030+0101", false},
        {"060A06135030+0101", false},
        {"061A06135030+0101", false},
        {"060936135030+0101", false},
        {"06090A135030+0101", false},
        {"06091A135030+0101", false},
        {"060900135030+0101", false},
        {"060906335030+0101", false},
        {"0609061A5030+0101", false},
        {"0609062A5030+0101", false},
        {"060906137030+0101", false},
        {"060906135A30+0101", false},
        {"060906135", false},
        {"0609061350", false},
        {"060906135070+0101", false},
        {"06090613503A+0101", false},
        {"06090613503", false},
        {"0609061350Z0", false},
        {"0609061350+0", false},
        {"0609061350+000A", false},
        {"0609061350+A00A", false},
        {"060906135030Z0", false},
        {"060906135030+010", false},
        {"060906135030+010A", false},
        {"060906135030+0A01", false},
        {"060906135030+2501", false},
        {"060906135030+0170", false},
        {"060906135030+010A", false},
        {"060906135030+A00A", false},
        {"060906135030Q", false},
        {"060906135030+", false},
    };
  }



  /**
   * Tests the {@code createUTCTimeValue} and {@code decodeUTCTimeValue}
   * methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testCreateAndDecodeUTCTimeValue()
         throws Exception
  {
    Date d = new Date();
    AttributeValue timeValue = UTCTimeSyntax.createUTCTimeValue(d);
    Date decodedDate = UTCTimeSyntax.decodeUTCTimeValue(timeValue.getValue());

    // UTCTime does not have support for sub-second values, so we need to make
    // sure that the decoded value is within 1000 milliseconds.
    assertTrue(Math.abs(d.getTime() - decodedDate.getTime()) < 1000);
  }
}
