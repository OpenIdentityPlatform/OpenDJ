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
import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests GSSAPI SASL Bind requests.
 */
public class GSSAPISASLBindRequestTestCase extends BindRequestTestCase
{
  @DataProvider(name = "GSSAPISASLBindRequests")
  public Object[][] getGSSAPISASLBindRequests() throws Exception
  {
    final GSSAPISASLBindRequest[] requests = {
        Requests.newGSSAPISASLBindRequest("id1", ByteString.empty()),
        Requests
            .newGSSAPISASLBindRequest("id2", ByteString.valueOf("password")) };
    final Object[][] objArray = new Object[requests.length][1];
    for (int i = 0; i < requests.length; i++)
    {
      objArray[i][0] = requests[i];
    }
    return objArray;
  }



  @Override
  protected GSSAPISASLBindRequest[] createTestRequests() throws Exception
  {
    final Object[][] objs = getGSSAPISASLBindRequests();
    final GSSAPISASLBindRequest[] ops = new GSSAPISASLBindRequest[objs.length];
    for (int i = 0; i < objs.length; i++)
    {
      ops[i] = (GSSAPISASLBindRequest) objs[i][0];
    }
    return ops;
  }

  @Test(enabled = false)
  public void testBindClient(BindRequest request) throws Exception {
    // Should setup a test krb server...
    super.testBindClient(request);
  }

  @Test(dataProvider = "GSSAPISASLBindRequests")
  public void testQOP(GSSAPISASLBindRequest request) throws Exception
  {
    GSSAPISASLBindRequest.QOPOption [] options =
        new GSSAPISASLBindRequest.QOPOption[]{
            GSSAPISASLBindRequest.QOPOption.AUTH,
            GSSAPISASLBindRequest.QOPOption.AUTH_INT,
            GSSAPISASLBindRequest.QOPOption.AUTH_CONF};
    request.setQOP(options);
    assertTrue(Arrays.deepEquals(options, request.getQOP()));
  }

  @Test(dataProvider = "GSSAPISASLBindRequests")
  public void testServerAuth(GSSAPISASLBindRequest request) throws Exception
  {
    request.setServerAuth(true);
    assertEquals(request.getServerAuth(), true);
  }

  @Test(dataProvider = "GSSAPISASLBindRequests")
  public void testSendBuffer(GSSAPISASLBindRequest request) throws Exception
  {
    request.setMaxSendBufferSize(512);
    assertEquals(request.getMaxSendBufferSize(), 512);
  }

  @Test(dataProvider = "GSSAPISASLBindRequests")
  public void testRecieveBuffer(GSSAPISASLBindRequest request) throws Exception
  {
    request.setMaxReceiveBufferSize(512);
    assertEquals(request.getMaxReceiveBufferSize(), 512);
  }
}
