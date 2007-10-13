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

package org.opends.server.crypto;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.extensions.ExtensionsTestCase;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.config.ConfigConstants;
import org.opends.server.types.*;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.util.ServerConstants;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.admin.ads.ADSContext;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

import java.util.LinkedHashSet;

/**
 * A set of test cases for the symmetric key extended operation.
 */
public class GetSymmetricKeyExtendedOperationTestCase
     extends CryptoTestCase {
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



  @Test(enabled=true)
  public void testValidRequest() throws Exception
  {
    final CryptoManager cm = DirectoryServer.getCryptoManager();
    final String secretMessage = "zyxwvutsrqponmlkjihgfedcba";
    final String cipherTransformationName = "AES/CBC/PKCS5Padding";
    final int cipherKeyLength = 128;

    CryptoManager.publishInstanceKeyEntryInADS();

    // Initial encryption ensures a cipher key entry is in ADS.
    cm.encrypt(cipherTransformationName, cipherKeyLength,
            secretMessage.getBytes());

    // Retrieve all uncompromised cipher key entries corresponding to the
    // specified transformation and key length.
    final String baseDNStr // TODO: is this DN defined elsewhere as a constant?
            = "cn=secret keys," + ADSContext.getAdministrationSuffixDN();
    final DN baseDN = DN.decode(baseDNStr);
    final String FILTER_OC_INSTANCE_KEY
            = new StringBuilder("(objectclass=")
            .append(ConfigConstants.OC_CRYPTO_CIPHER_KEY)
            .append(")").toString();
    final String FILTER_NOT_COMPROMISED = new StringBuilder("(!(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME)
            .append("=*))").toString();
    final String FILTER_CIPHER_TRANSFORMATION_NAME = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME)
            .append("=").append(cipherTransformationName)
            .append(")").toString();
    final String FILTER_CIPHER_KEY_LENGTH = new StringBuilder("(")
            .append(ConfigConstants.ATTR_CRYPTO_KEY_LENGTH_BITS)
            .append("=").append(String.valueOf(cipherKeyLength))
            .append(")").toString();
    final String searchFilter = new StringBuilder("(&")
            .append(FILTER_OC_INSTANCE_KEY)
            .append(FILTER_NOT_COMPROMISED)
            .append(FILTER_CIPHER_TRANSFORMATION_NAME)
            .append(FILTER_CIPHER_KEY_LENGTH)
            .append(")").toString();
    final LinkedHashSet<String> requestedAttributes
            = new LinkedHashSet<String>();
    requestedAttributes.add(ConfigConstants.ATTR_CRYPTO_SYMMETRIC_KEY);
    final InternalClientConnection icc
            = InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOp = icc.processSearch(
            baseDN,
            SearchScope.SINGLE_LEVEL,
            DereferencePolicy.NEVER_DEREF_ALIASES,
            /* size limit */ 0, /* time limit */ 0,
            /* types only */ false,
            SearchFilter.createFilterFromString(searchFilter),
            requestedAttributes);
    assertTrue(0 < searchOp.getSearchEntries().size());

    final InternalClientConnection internalConnection =
         InternalClientConnection.getRootConnection();
    final String instanceKeyID = cm.getInstanceKeyID();
    final AttributeType attrSymmetricKey = DirectoryServer.getAttributeType(
         ConfigConstants.ATTR_CRYPTO_SYMMETRIC_KEY);
    for (Entry e : searchOp.getSearchEntries()) {
      final String symmetricKeyAttributeValue
              = e.getAttributeValue(attrSymmetricKey, DirectoryStringSyntax.DECODER);
      final ASN1OctetString requestValue =
           GetSymmetricKeyExtendedOperation.encodeRequestValue(
                symmetricKeyAttributeValue, instanceKeyID);
      final ExtendedOperation extendedOperation =
              internalConnection.processExtendedOperation(
                      ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP,
                      requestValue);
      assertEquals(extendedOperation.getResultCode(), ResultCode.SUCCESS);
      // The key should be re-wrapped, and hence have a different binary
      // representation....
      final String responseValue
              = extendedOperation.getResponseValue().stringValue();
      assertFalse(symmetricKeyAttributeValue.equals(responseValue));
      // ... but the keyIDs should be equal (ideally, the validity of
      // the returned value would be checked by decoding the
      // returned ds-cfg-symmetric-key attribute value; however, there
      // is no non-private method to call.
      assertEquals(responseValue.split(":")[0],
              symmetricKeyAttributeValue.split(":")[0]);
    }
  }


  @Test()
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
