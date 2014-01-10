/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import org.opends.server.admin.std.server.
            GetConnectionIdExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import static org.opends.server.loggers.debug.DebugLogger.*;
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
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Create an instance of this "Get Connection ID" extended operation.  All
   * initialization should be performed in the
   * {@code initializeExtendedOperationHandler} method.
   */
  public GetConnectionIDExtendedOperation()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeExtendedOperationHandler(
                   GetConnectionIdExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  /**
   * {@inheritDoc}
   */
  @Override
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
  public static ByteString encodeResponseValue(long connectionID)
  {
    ByteStringBuilder builder = new ByteStringBuilder(8);
    ASN1Writer writer = ASN1.getWriter(builder);

    try
    {
      writer.writeInteger(connectionID);
    }
    catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return builder.toByteString();
  }



  /**
   * Decodes the provided ASN.1 octet string to extract the connection ID.
   *
   * @param  responseValue  The response value to be decoded.
   *
   * @return  The connection ID decoded from the provided response value.
   *
   * @throws ASN1Exception  If an error occurs while trying to decode the
   *                         response value.
   */
  public static long decodeResponseValue(ByteString responseValue)
         throws ASN1Exception
  {
    ASN1Reader reader = ASN1.getReader(responseValue);
    try
    {
      return reader.readInteger();
    }
    catch(Exception e)
    {
      // TODO: DO something
      return 0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationOID()
  {
    return OID_GET_CONNECTION_ID_EXTOP;
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationName()
  {
    return "Get Connection ID";
  }
}
