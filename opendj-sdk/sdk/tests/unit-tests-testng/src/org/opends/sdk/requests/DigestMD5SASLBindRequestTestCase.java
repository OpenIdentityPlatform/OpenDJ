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

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;


/**
 * Tests Digest MD5 SASL requests.
 */
public class DigestMD5SASLBindRequestTestCase extends BindRequestTestCase
{
  @DataProvider(name = "DigestMD5SASLBindRequests")
  public Object[][] getDigestMD5SASLBindRequests() throws Exception
  {
    final DigestMD5SASLBindRequest[] requests = {
        Requests.newDigestMD5SASLBindRequest("id1", ByteString.empty()),
        Requests.newDigestMD5SASLBindRequest("id2", ByteString
            .valueOf("password")) };
    final Object[][] objArray = new Object[requests.length][1];
    for (int i = 0; i < requests.length; i++)
    {
      objArray[i][0] = requests[i];
    }
    return objArray;
  }



  @Override
  protected DigestMD5SASLBindRequest[] createTestRequests() throws Exception
  {
    final Object[][] objs = getDigestMD5SASLBindRequests();
    final DigestMD5SASLBindRequest[] ops = new DigestMD5SASLBindRequest[objs.length];
    for (int i = 0; i < objs.length; i++)
    {
      ops[i] = (DigestMD5SASLBindRequest) objs[i][0];
    }
    return ops;
  }

  @Test(dataProvider = "DigestMD5SASLBindRequests")
  public void testQOP(DigestMD5SASLBindRequest request) throws Exception
  {
    DigestMD5SASLBindRequest.QOPOption [] options =
        new DigestMD5SASLBindRequest.QOPOption[]{
            DigestMD5SASLBindRequest.QOPOption.AUTH,
            DigestMD5SASLBindRequest.QOPOption.AUTH_INT,
            DigestMD5SASLBindRequest.QOPOption.AUTH_CONF};
    request.setQOP(options);

    DigestMD5SASLBindRequest.QOPOption [] results = request.getQOP();
    for(int i = 0; i < options.length; i++)
    {
      assertEquals(options[i], results[i]);
    }
  }

  @Test(dataProvider = "DigestMD5SASLBindRequests" )
  public void testStrength(DigestMD5SASLBindRequest request) throws Exception
  {
    DigestMD5SASLBindRequest.CipherOption[] options =
        new DigestMD5SASLBindRequest.CipherOption[]{
            DigestMD5SASLBindRequest.CipherOption.RC4_40,
            DigestMD5SASLBindRequest.CipherOption.TRIPLE_DES_RC4,
            DigestMD5SASLBindRequest.CipherOption.DES_RC4_56};
    request.setCipher(options);
    assertTrue(Arrays.deepEquals(options, request.getCipher()));
  }

  @Test(dataProvider = "DigestMD5SASLBindRequests")
  public void testServerAuth(DigestMD5SASLBindRequest request) throws Exception
  {
    request.setServerAuth(true);
    assertEquals(request.getServerAuth(), true);
  }

  @Test(dataProvider = "DigestMD5SASLBindRequests")
  public void testSendBuffer(DigestMD5SASLBindRequest request) throws Exception
  {
    request.setMaxSendBufferSize(1024);
    assertEquals(request.getMaxSendBufferSize(), 1024);
  }

  @Test(dataProvider = "DigestMD5SASLBindRequests")
  public void testRecieveBuffer(DigestMD5SASLBindRequest request) throws Exception
  {;
    request.setMaxReceiveBufferSize(1024);
    assertEquals(request.getMaxReceiveBufferSize(), 1024);
  }
}
