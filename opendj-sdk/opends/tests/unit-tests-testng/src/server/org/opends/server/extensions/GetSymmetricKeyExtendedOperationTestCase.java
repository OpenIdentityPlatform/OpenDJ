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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.ResultCode;
import org.opends.server.types.CryptoManager;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.ServerConstants;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

/**
 * A set of test cases for the symmetric key extended operation.
 */
public class GetSymmetricKeyExtendedOperationTestCase
     extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  @Test(enabled=false)
  public void testValidRequest() throws Exception
  {
    CryptoManager cm = DirectoryServer.getCryptoManager();

    // TODO use a proper symmetric key value
    String symmetricKey = cm.getInstanceKeyID();
    String instanceKeyID =  cm.getInstanceKeyID();

    ASN1OctetString requestValue =
         GetSymmetricKeyExtendedOperation.encodeRequestValue(
              symmetricKey, instanceKeyID);

    InternalClientConnection internalConnection =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extendedOperation =
         internalConnection.processExtendedOperation(
              ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP, requestValue);

    assertEquals(extendedOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(extendedOperation.getResponseValue().stringValue(),
                 symmetricKey);
  }


  @Test(enabled=false)
  public void testInvalidRequest() throws Exception
  {
    CryptoManager cm = DirectoryServer.getCryptoManager();

    String symmetricKey = "1";
    String instanceKeyID = cm.getInstanceKeyID();

    ASN1OctetString requestValue =
         GetSymmetricKeyExtendedOperation.encodeRequestValue(
              symmetricKey, instanceKeyID);

    InternalClientConnection internalConnection =
         InternalClientConnection.getRootConnection();
    ExtendedOperation extendedOperation =
         internalConnection.processExtendedOperation(
              ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP, requestValue);

    assertFalse(extendedOperation.getResultCode() == ResultCode.SUCCESS);
  }
}
