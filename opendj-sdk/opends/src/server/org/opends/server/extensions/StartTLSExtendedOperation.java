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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of the StartTLS extended operation as
 * defined in RFC 2830.  It can enable the TLS connection security provider on
 * an established connection upon receiving an appropriate request from a
 * client.
 */
public class StartTLSExtendedOperation
       extends ExtendedOperationHandler
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




  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeExtendedOperationHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    // FIXME -- Are there any configurable options that we should support?
    DirectoryServer.registerSupportedExtension(OID_START_TLS_REQUEST, this);
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(OID_START_TLS_REQUEST);
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
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

      int msgID = MSGID_STARTTLS_NO_CLIENT_CONNECTION;
      operation.appendErrorMessage(getMessage(msgID));
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

      int msgID = MSGID_STARTTLS_NOT_TLS_CAPABLE;
      operation.appendErrorMessage(getMessage(msgID));
      return;
    }

    StringBuilder unavailableReason = new StringBuilder();
    if (! tlsCapableConnection.tlsProtectionAvailable(unavailableReason))
    {
      operation.setResultCode(ResultCode.UNAVAILABLE);
      operation.setErrorMessage(unavailableReason);
      return;
    }


    // Actually enable TLS protection on the client connection.  This may fail,
    // but if it does then the connection will be closed so we'll just need to
    // log it.
    try
    {
      tlsCapableConnection.enableTLSConnectionSecurityProvider();
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, de);
      }

      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.MILD_ERROR,
               MSGID_STARTTLS_ERROR_ON_ENABLE,
               stackTraceToSingleLineString(de));
    }


    // TLS was successfully enabled on the client connection, but we need to
    // send the response in the clear.
    operation.setResultCode(ResultCode.SUCCESS);

    try
    {
      tlsCapableConnection.sendClearResponse(operation);
      operation.setResponseSent();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.MILD_ERROR,
               MSGID_STARTTLS_ERROR_SENDING_CLEAR_RESPONSE,
               stackTraceToSingleLineString(e));

      clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM, false,
                                  MSGID_STARTTLS_ERROR_SENDING_CLEAR_RESPONSE,
                                  stackTraceToSingleLineString(e));
    }
  }
}

