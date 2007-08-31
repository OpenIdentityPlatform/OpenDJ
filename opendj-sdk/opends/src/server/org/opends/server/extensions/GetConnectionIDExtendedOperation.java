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



import org.opends.server.admin.std.server.
            GetConnectionIdExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Long;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class implements the "Get Connection ID" extended operation that can be
 * used to get the connection ID of the associated client connection.
 */
public class GetConnectionIDExtendedOperation
       extends ExtendedOperationHandler<
                    GetConnectionIdExtendedOperationHandlerCfg>
{
  /**
   * Create an instance of this "Get Connection ID" extended operation.  All
   * initialization should be performed in the
   * {@code initializeExtendedOperationHandler} method.
   */
  public GetConnectionIDExtendedOperation()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeExtendedOperationHandler(
                   GetConnectionIdExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    // No special configuration is required.

    DirectoryServer.registerSupportedExtension(OID_GET_CONNECTION_ID_EXTOP,
                                               this);

    registerControlsAndFeatures();
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(OID_GET_CONNECTION_ID_EXTOP);

    deregisterControlsAndFeatures();
  }



  /**
   * {@inheritDoc}
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {
    operation.setResponseOID(OID_GET_CONNECTION_ID_EXTOP);
    operation.setResponseValue(
         encodeResponseValue(operation.getConnectionID()));
    operation.setResultCode(ResultCode.SUCCESS);
  }



  /**
   * Encodes the provided connection ID in an octet string suitable for use as
   * the value for this extended operation.
   *
   * @param  connectionID  The connection ID to be encoded.
   *
   * @return  The ASN.1 octet string containing the encoded connection ID.
   */
  public static ASN1OctetString encodeResponseValue(long connectionID)
  {
    return new ASN1OctetString(new ASN1Long(connectionID).encode());
  }



  /**
   * Decodes the provided ASN.1 octet string to extract the connection ID.
   *
   * @param  responseValue  The response value to be decoded.
   *
   * @return  The connection ID decoded from the provided response value.
   *
   * @throws  ASN1Exception  If an error occurs while trying to decode the
   *                         response value.
   */
  public static long decodeResponseValue(ASN1OctetString responseValue)
         throws ASN1Exception
  {
    return ASN1Long.decodeAsLong(responseValue.value()).longValue();
  }
}

