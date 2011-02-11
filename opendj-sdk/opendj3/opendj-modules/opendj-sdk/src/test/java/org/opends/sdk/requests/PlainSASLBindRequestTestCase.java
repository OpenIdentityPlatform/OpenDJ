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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import org.opends.sdk.ByteString;
import org.testng.annotations.DataProvider;



/**
 * Tests Plain SASL Bind requests.
 */
public class PlainSASLBindRequestTestCase extends BindRequestTestCase
{
  @DataProvider(name = "plainSASLBindRequests")
  public Object[][] getPlainSASLBindRequests() throws Exception
  {
    final PlainSASLBindRequest[] requests = {
        Requests.newPlainSASLBindRequest("id1", ByteString.empty()),
        Requests.newPlainSASLBindRequest("id2", ByteString.valueOf("password")) };
    final Object[][] objArray = new Object[requests.length][1];
    for (int i = 0; i < requests.length; i++)
    {
      objArray[i][0] = requests[i];
    }
    return objArray;
  }



  @Override
  protected PlainSASLBindRequest[] createTestRequests() throws Exception
  {
    final Object[][] objs = getPlainSASLBindRequests();
    final PlainSASLBindRequest[] ops = new PlainSASLBindRequest[objs.length];
    for (int i = 0; i < objs.length; i++)
    {
      ops[i] = (PlainSASLBindRequest) objs[i][0];
    }
    return ops;
  }
}
