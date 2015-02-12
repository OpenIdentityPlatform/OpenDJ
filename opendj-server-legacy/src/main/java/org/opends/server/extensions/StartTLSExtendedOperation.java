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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.opends.server.admin.std.server.StartTLSExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class provides an implementation of the StartTLS extended operation as
 * defined in RFC 2830.  It can enable the TLS connection security provider on
 * an established connection upon receiving an appropriate request from a
 * client.
 */
public class StartTLSExtendedOperation
       extends ExtendedOperationHandler<StartTLSExtendedOperationHandlerCfg>
{

  /**
   * Create an instance of this StartTLS extended operation handler.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public StartTLSExtendedOperation()
  {
    super();
  }

  /** {@inheritDoc} */
  @Override
  public void initializeExtendedOperationHandler(
                   StartTLSExtendedOperationHandlerCfg config)
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
    // We should always include the StartTLS OID in the response (the same OID
    // is used for both the request and the response), so make sure that it will
    // happen.
    operation.setResponseOID(OID_START_TLS_REQUEST);


    // Get the reference to the client connection.  If there is none, then fail.
    ClientConnection clientConnection = operation.getClientConnection();
    if (clientConnection == null)
    {
      operation.setResultCode(ResultCode.UNAVAILABLE);
      operation.appendErrorMessage(ERR_STARTTLS_NO_CLIENT_CONNECTION.get());
      return;
    }


    // Make sure that the client connection is capable of enabling TLS.  If not,
    // then fail.
    TLSCapableConnection tlsCapableConnection;
    if (clientConnection instanceof TLSCapableConnection)
    {
      tlsCapableConnection = (TLSCapableConnection) clientConnection;
    }
    else
    {
      operation.setResultCode(ResultCode.UNAVAILABLE);
      operation.appendErrorMessage(ERR_STARTTLS_NOT_TLS_CAPABLE.get());
      return;
    }

    LocalizableMessageBuilder unavailableReason = new LocalizableMessageBuilder();
    if (! tlsCapableConnection.prepareTLS(unavailableReason))
    {
      operation.setResultCode(ResultCode.UNAVAILABLE);
      operation.setErrorMessage(unavailableReason);
      return;
    }

    // TLS was successfully enabled on the client connection, but we need to
    // send the response in the clear.
    operation.setResultCode(ResultCode.SUCCESS);
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationOID()
  {
    return OID_START_TLS_REQUEST;
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationName()
  {
    return "StartTLS";
  }
}
