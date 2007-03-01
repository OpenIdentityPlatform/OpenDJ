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
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements the "Who Am I?" extended operation defined in RFC 4532.
 * It simply returns the authorized ID of the currently-authenticated user.
 */
public class WhoAmIExtendedOperation
       extends ExtendedOperationHandler
{



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

    // No special configuration is required.

    DirectoryServer.registerSupportedExtension(OID_WHO_AM_I_REQUEST, this);
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {

    DirectoryServer.deregisterSupportedExtension(OID_WHO_AM_I_REQUEST);
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {


    // Get the client connection and determine the DN of the user associated
    // with it.
    ClientConnection clientConnection = operation.getClientConnection();
    if (clientConnection == null)
    {
      // There is no client connection, so we can't make the determination.
      operation.setResultCode(ResultCode.UNWILLING_TO_PERFORM);

      int msgID = MSGID_EXTOP_WHOAMI_NO_CLIENT_CONNECTION;
      operation.appendErrorMessage(getMessage(msgID));
      return;
    }

    // Get the auth info from the client connection.
    AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
    if ((authInfo == null) || (authInfo.getAuthenticationDN() == null))
    {
      // The user must not be authenticated, so we can send back an empty
      // response value.
      operation.setResultCode(ResultCode.SUCCESS);
      operation.setResponseValue(new ASN1OctetString());
      return;
    }

    // Get the DN of the authenticated user and put that in the response.
    // FIXME -- Do we need to support the use of an authorization ID that is
    //          different from the authentication ID?
    operation.setResultCode(ResultCode.SUCCESS);
    operation.setResponseValue(new ASN1OctetString("dn:" +
                                    authInfo.getAuthenticationDN().toString()));
  }
}

