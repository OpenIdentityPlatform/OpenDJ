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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import java.util.Arrays;
import java.util.HashSet;

import org.opends.server.admin.std.server.WhoAmIExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.ExtendedOperation;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.messages.ProtocolMessages.ERR_PROXYAUTH_AUTHZ_NOT_PERMITTED;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class implements the "Who Am I?" extended operation defined in RFC 4532.
 * It simply returns the authorized ID of the currently-authenticated user.
 */
public class WhoAmIExtendedOperation
       extends ExtendedOperationHandler<WhoAmIExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Create an instance of this "Who Am I?" extended operation.  All
   * initialization should be performed in the
   * <CODE>initializeExtendedOperationHandler</CODE> method.
   */
  public WhoAmIExtendedOperation()
  {
    super(new HashSet<String>(Arrays.asList(
        OID_PROXIED_AUTH_V1, OID_PROXIED_AUTH_V2)));
  }

  /** {@inheritDoc} */
  @Override
  public void initializeExtendedOperationHandler(
                   WhoAmIExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  /** {@inheritDoc} */
  @Override
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
        // The requester must have the PROXIED_AUTH privilege in order to be
        // able to use this control.
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
          // Log usage of legacy proxy authz V1 control.
          operation.addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteProxiedAuthzV1Control"));

          authorizationEntry = proxyControlV1.getAuthorizationEntry();
        }
        // Check the requester has the authz user in scope of their proxy aci.
        if (! AccessControlConfigManager.getInstance().getAccessControlHandler()
                .mayProxy(clientConnection.getAuthenticationInfo().getAuthenticationEntry(),
                        authorizationEntry, operation))
        {
          final DN dn = authorizationEntry.getName();
          throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
              ERR_PROXYAUTH_AUTHZ_NOT_PERMITTED.get(dn));
        }
        operation.setAuthorizationEntry(authorizationEntry);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

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
      authzID = "dn:" + authzDN;
    }

    operation.setResponseValue(ByteString.valueOf(authzID));
    operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(
        getClass(), "authzID", authzID));
    operation.setResultCode(ResultCode.SUCCESS);
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationOID()
  {
    return OID_WHO_AM_I_REQUEST;
  }

  /** {@inheritDoc} */
  @Override
  public String getExtendedOperationName()
  {
    return "Who Am I?";
  }
}
