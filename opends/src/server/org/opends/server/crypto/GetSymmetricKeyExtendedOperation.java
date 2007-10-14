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

import org.opends.server.admin.std.server.
            GetSymmetricKeyExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.types.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;
import org.opends.messages.Message;
import static org.opends.messages.ExtensionMessages.*;

import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * This class implements the get symmetric key extended operation, an OpenDS
 * proprietary extension used for distribution of symmetric keys amongst
 * servers.
 */
public class GetSymmetricKeyExtendedOperation
     extends ExtendedOperationHandler<
                  GetSymmetricKeyExtendedOperationHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();



  /**
   * The BER type value for the symmetric key element of the operation value.
   */
  public static final byte TYPE_SYMMETRIC_KEY_ELEMENT = (byte) 0x80;



  /**
   * The BER type value for the instance key ID element of the operation value.
   */
  public static final byte TYPE_INSTANCE_KEY_ID_ELEMENT = (byte) 0x81;



  // The default set of supported control OIDs for this extended operation.
  private Set<String> supportedControlOIDs = new HashSet<String>(0);



  /**
   * Create an instance of this symmetric key extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public GetSymmetricKeyExtendedOperation()
  {
    super();

  }




  /**
   * {@inheritDoc}
   */
  public void initializeExtendedOperationHandler(
       GetSymmetricKeyExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    supportedControlOIDs = new HashSet<String>();


    DirectoryServer.registerSupportedExtension(
         ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP, this);

    registerControlsAndFeatures();
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(
         ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP);

    deregisterControlsAndFeatures();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<String> getSupportedControls()
  {
    return supportedControlOIDs;
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
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
      Message message = ERR_GET_SYMMETRIC_KEY_NO_VALUE.get();
      operation.appendErrorMessage(message);
      return;
    }

    try
    {
      ASN1Sequence valueSequence =
           ASN1Sequence.decodeAsSequence(requestValue.value());
      for (ASN1Element e : valueSequence.elements())
      {
        switch (e.getType())
        {
          case TYPE_SYMMETRIC_KEY_ELEMENT:
            requestSymmetricKey =
                 ASN1OctetString.decodeAsOctetString(e).stringValue();
            break;

          case TYPE_INSTANCE_KEY_ID_ELEMENT:
            instanceKeyID =
                 ASN1OctetString.decodeAsOctetString(e).stringValue();
            break;

          default:
            Message message = ERR_GET_SYMMETRIC_KEY_INVALID_TYPE.get(
                 StaticUtils.byteToHex(e.getType()));
            operation.appendErrorMessage(message);
            return;
        }
      }
    }
    catch (ASN1Exception ae)
    {
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }

      Message message = ERR_GET_SYMMETRIC_KEY_ASN1_DECODE_EXCEPTION.get(
           ae.getMessage());
      operation.appendErrorMessage(message);
      return;
    }
    catch (Exception e)
    {
      if (DebugLogger.debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      operation.setResultCode(ResultCode.PROTOCOL_ERROR);

      Message message = ERR_GET_SYMMETRIC_KEY_DECODE_EXCEPTION.get(
           StaticUtils.getExceptionMessage(e));
      operation.appendErrorMessage(message);
      return;
    }

    CryptoManager cm = DirectoryServer.getCryptoManager();
    try
    {
      String responseSymmetricKey = cm.reencodeSymmetricKeyAttribute(
           requestSymmetricKey, instanceKeyID);

      operation.setResponseOID(
           ServerConstants.OID_GET_SYMMETRIC_KEY_EXTENDED_OP);
      operation.setResponseValue(new ASN1OctetString(responseSymmetricKey));
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
  public static ASN1OctetString encodeRequestValue(
       String symmetricKey,
       String instanceKeyID)
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);

    ASN1OctetString symmetricKeyElement =
         new ASN1OctetString(TYPE_SYMMETRIC_KEY_ELEMENT, symmetricKey);
    elements.add(symmetricKeyElement);

    ASN1OctetString instanceKeyIDElement =
         new ASN1OctetString(TYPE_INSTANCE_KEY_ID_ELEMENT,
                             instanceKeyID);
    elements.add(instanceKeyIDElement);

    ASN1Sequence valueSequence = new ASN1Sequence(elements);
    return new ASN1OctetString(valueSequence.encode());
  }


}
