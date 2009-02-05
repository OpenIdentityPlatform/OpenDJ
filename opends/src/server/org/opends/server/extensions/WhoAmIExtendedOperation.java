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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.HashSet;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.WhoAmIExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class implements the "Who Am I?" extended operation defined in RFC 4532.
 * It simply returns the authorized ID of the currently-authenticated user.
 */
public class WhoAmIExtendedOperation
       extends ExtendedOperationHandler<WhoAmIExtendedOperationHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Create an instance of this "Who Am I?" extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public WhoAmIExtendedOperation()
  {
    super();
  }


  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  config       The configuration that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  @Override
  public void initializeExtendedOperationHandler(
                   WhoAmIExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    // No special configuration is required.

    DirectoryServer.registerSupportedExtension(OID_WHO_AM_I_REQUEST, this);

    registerControlsAndFeatures();
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  @Override()
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(OID_WHO_AM_I_REQUEST);

    deregisterControlsAndFeatures();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<String> getSupportedControls()
  {
    HashSet<String> supportedControls = new HashSet<String>(2);

    supportedControls.add(OID_PROXIED_AUTH_V1);
    supportedControls.add(OID_PROXIED_AUTH_V2);

    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void processExtendedOperation(ExtendedOperation operation)
  {
    // Process any supported controls for this operation, including the
    // proxied authorization control.
    ClientConnection clientConnection = operation.getClientConnection();
    Entry authorizationEntry;
    try
    {
      ProxiedAuthV1Control proxyControlV1 =
          operation.getRequestControl(ProxiedAuthV1Control.DECODER);
      ProxiedAuthV2Control proxyControlV2 =
          operation.getRequestControl(ProxiedAuthV2Control.DECODER);
      if(proxyControlV1 != null || proxyControlV2 != null)
      {
        // The requester must have the PROXIED_AUTH privilige in order to
        // be able to use this control.
        if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH,
            operation))
        {
          operation.appendErrorMessage(
              ERR_EXTOP_WHOAMI_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          operation.setResultCode(ResultCode.AUTHORIZATION_DENIED);
          return;
        }

        if(proxyControlV2 != null)
        {
          authorizationEntry = proxyControlV2.getAuthorizationEntry();
        }
        else
        {
          authorizationEntry = proxyControlV1.getAuthorizationEntry();
        }
        operation.setAuthorizationEntry(authorizationEntry);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      operation.setResultCode(de.getResultCode());
      operation.appendErrorMessage(de.getMessageObject());
      return;
    }


    // Get the authorization DN for the operation and add it to the response
    // value.
    String authzID;
    DN authzDN = operation.getAuthorizationDN();
    if (authzDN == null)
    {
      authzID = "";
    }
    else
    {
      authzID = "dn:" + authzDN.toString();
    }

    operation.setResponseValue(ByteString.valueOf(authzID));
    operation.appendAdditionalLogMessage(
            Message.raw("authzID=\"" + authzID + "\""));
    operation.setResultCode(ResultCode.SUCCESS);
  }
}

