/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.server.config.server.
            GetConnectionIdExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements the "Get Connection ID" extended operation that can be
 * used to get the connection ID of the associated client connection.
 */
public class GetConnectionIDExtendedOperation
       extends ExtendedOperationHandler<
                    GetConnectionIdExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Create an instance of this "Get Connection ID" extended operation.  All
   * initialization should be performed in the
   * {@code initializeExtendedOperationHandler} method.
   */
  public GetConnectionIDExtendedOperation()
  {
    super();
  }

  @Override
  public void initializeExtendedOperationHandler(
                   GetConnectionIdExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

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
      logger.traceException(e);
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
   * @throws DecodeException  If an error occurs while trying to decode the
   *                         response value.
   */
  public static long decodeResponseValue(ByteString responseValue)
         throws DecodeException
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

  @Override
  public String getExtendedOperationOID()
  {
    return OID_GET_CONNECTION_ID_EXTOP;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "Get Connection ID";
  }
}
