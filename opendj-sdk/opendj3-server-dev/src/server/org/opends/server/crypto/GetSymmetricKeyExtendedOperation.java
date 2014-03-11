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
package org.opends.server.crypto;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.
GetSymmetricKeyExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
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
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ExtensionMessages.*;

/**
 * This class implements the get symmetric key extended operation, an OpenDS
 * proprietary extension used for distribution of symmetric keys amongst
 * servers.
 */
public class GetSymmetricKeyExtendedOperation
     extends ExtendedOperationHandler<
                  GetSymmetricKeyExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The BER type value for the symmetric key element of the operation value.
   */
  public static final byte TYPE_SYMMETRIC_KEY_ELEMENT = (byte) 0x80;

  /**
   * The BER type value for the instance key ID element of the operation value.
   */
  public static final byte TYPE_INSTANCE_KEY_ID_ELEMENT = (byte) 0x81;

  /**
   * Create an instance of this symmetric key extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public GetSymmetricKeyExtendedOperation()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeExtendedOperationHandler(
       GetSymmetricKeyExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // Initialize the variables associated with components that may be included
    // in the request.
    String requestSymmetricKey = null;
    String instanceKeyID       = null;



    // Parse the encoded request, if there is one.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue == null)
    {
      // The request must always have a value.
      LocalizableMessage message = ERR_GET_SYMMETRIC_KEY_NO_VALUE.get();
      operation.appendErrorMessage(message);
      return;
    }

    try
    {
      ASN1Reader reader = ASN1.getReader(requestValue);
      reader.readStartSequence();
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_SYMMETRIC_KEY_ELEMENT)
      {
        requestSymmetricKey = reader.readOctetStringAsString();
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_INSTANCE_KEY_ID_ELEMENT)
      {
        instanceKeyID = reader.readOctetStringAsString();
      }
      reader.readEndSequence();
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      operation.appendErrorMessage(ERR_GET_SYMMETRIC_KEY_ASN1_DECODE_EXCEPTION.get(e.getMessage()));
      return;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      operation.setResultCode(ResultCode.PROTOCOL_ERROR);

      LocalizableMessage message = ERR_GET_SYMMETRIC_KEY_DECODE_EXCEPTION.get(
           StaticUtils.getExceptionMessage(e));
      operation.appendErrorMessage(message);
      return;
    }

    CryptoManagerImpl cm = DirectoryServer.getCryptoManager();
    try
    {
      String responseSymmetricKey = cm.reencodeSymmetricKeyAttribute(
           requestSymmetricKey, instanceKeyID);

      operation.setResponseOID(
           ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP);
      operation.setResponseValue(ByteString.valueOf(responseSymmetricKey));
      operation.setResultCode(ResultCode.SUCCESS);
    }
    catch (CryptoManagerException e)
    {
      operation.setResultCode(DirectoryServer.getServerErrorResultCode());
      operation.appendErrorMessage(e.getMessageObject());
    }
    catch (Exception e)
    {
      operation.setResultCode(DirectoryServer.getServerErrorResultCode());
      operation.appendErrorMessage(StaticUtils.getExceptionMessage(e));
    }
  }

  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the value for this extended operation.
   *
   * @param  symmetricKey   The wrapped key to use for this request control.
   * @param  instanceKeyID  The requesting server instance key ID to use for
   *                        this request control.
   *
   * @return  An ASN.1 octet string containing the encoded request value.
   */
  public static ByteString encodeRequestValue(
       String symmetricKey,
       String instanceKeyID)
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);

    try
    {
      writer.writeStartSequence();
      writer.writeOctetString(TYPE_SYMMETRIC_KEY_ELEMENT, symmetricKey);
      writer.writeOctetString(TYPE_INSTANCE_KEY_ID_ELEMENT, instanceKeyID);
      writer.writeEndSequence();
    }
    catch (IOException e)
    {
      // TODO: DO something
    }

    return builder.toByteString();
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationOID()
  {
    return ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP;
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationName()
  {
    return "Get Symmetric Key";
  }
}
