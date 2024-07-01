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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.dsml.protocol;

import java.io.IOException;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.protocols.ldap.AbandonRequestProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.LDAPException;



/**
 * This class provides the functionality for the performing an
 * LDAP ABANDON operation based on the specified DSML request.
 */
class DSMLAbandonOperation
{
  private final LDAPConnection connection;

  /**
   * Create an instance with the specified LDAP connection.
   *
   * @param connection    The LDAP connection to send the request on.
   */
  public DSMLAbandonOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Perform the LDAP ABANDON operation and send the result back to the
   * client.
   *
   * @param  objFactory      The object factory for this operation.
   * @param  abandonRequest  The abandon request for this operation.
   * @param  controls        Any required controls (e.g. for proxy authz).
   *
   * @return  The result of the abandon operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   */
  public LDAPResult doOperation(ObjectFactory objFactory,
        AbandonRequest abandonRequest,
        List<org.opends.server.types.Control> controls)
    throws LDAPException, IOException
  {
    LDAPResult abandonResponse = objFactory.createLDAPResult();

    String abandonIdStr = abandonRequest.getAbandonID();
    int abandonId = 0;
    try
    {
      abandonId = Integer.parseInt(abandonIdStr);
    } catch (NumberFormatException nfe)
    {
      throw new LDAPException(LDAPResultCode.UNWILLING_TO_PERFORM,
                              LocalizableMessage.raw(nfe.getMessage()));
    }

    // Create and send an LDAP request to the server.
    ProtocolOp op = new AbandonRequestProtocolOp(abandonId);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op,
        controls);
    connection.getLDAPWriter().writeMessage(msg);

    return abandonResponse;
  }

}

