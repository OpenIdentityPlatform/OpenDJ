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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.WhoAmIExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.*;

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
    super(newHashSet(OID_PROXIED_AUTH_V1, OID_PROXIED_AUTH_V2));
  }

  @Override
  public void initializeExtendedOperationHandler(
                   WhoAmIExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

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

    operation.setResponseValue(ByteString.valueOfUtf8(authzID));
    operation.addAdditionalLogItem(AdditionalLogItem.quotedKeyValue(
        getClass(), "authzID", authzID));
    operation.setResultCode(ResultCode.SUCCESS);
  }

  @Override
  public String getExtendedOperationOID()
  {
    return OID_WHO_AM_I_REQUEST;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "Who Am I?";
  }
}
