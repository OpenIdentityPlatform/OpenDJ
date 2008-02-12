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
package org.opends.dsml.protocol;

import java.io.IOException;
import org.opends.messages.Message;

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
public class DSMLAbandonOperation
{
  private LDAPConnection connection;

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
   *
   * @return  The result of the abandon operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   */
  public LDAPResult doOperation(ObjectFactory objFactory,
        AbandonRequest abandonRequest)
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
                              Message.raw(nfe.getMessage()));
    }

    // Create and send an LDAP request to the server.
    ProtocolOp op = new AbandonRequestProtocolOp(abandonId);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op);
    connection.getLDAPWriter().writeMessage(msg);

    return abandonResponse;
  }

}

