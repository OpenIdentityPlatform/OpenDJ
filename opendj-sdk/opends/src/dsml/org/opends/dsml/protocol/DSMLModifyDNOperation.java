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
import org.opends.server.tools.LDAPConnection;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;



/**
 * This class provides the functionality for the performing an
 * LDAP MODIFY_DN operation based on the specified DSML request.
 */
public class DSMLModifyDNOperation
{

  private LDAPConnection connection;

  /**
   * Create the instance with the specified connection.
   *
   * @param connection    The LDAP connection to send the request on.
   */
  public DSMLModifyDNOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Perform the LDAP Modify DN operation and send the result back to the
   * client.
   *
   * @param  objFactory       The object factory for this operation.
   * @param  modifyDNRequest  The modify DN request for this operation.
   *
   * @return  The result of the modify DN operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   *
   * @throws  ASN1Exception  If an error occurs while interacting with an ASN.1
   *                         element.
   */
  public LDAPResult doOperation(ObjectFactory objFactory,
        ModifyDNRequest modifyDNRequest)
    throws IOException, LDAPException, ASN1Exception
  {
    LDAPResult modDNResponse = objFactory.createLDAPResult();
    modDNResponse.setRequestID(modifyDNRequest.getRequestID());
    ByteString dnStr = ByteString.valueOf(modifyDNRequest.getDn());
    ProtocolOp op = null;

    if (modifyDNRequest.getNewSuperior() != null)
    {
      op = new ModifyDNRequestProtocolOp(dnStr, ByteString
          .valueOf(modifyDNRequest.getNewrdn()), modifyDNRequest
          .isDeleteoldrdn(), ByteString.valueOf(modifyDNRequest
          .getNewSuperior()));
    }
    else
    {
      op = new ModifyDNRequestProtocolOp(dnStr, ByteString
          .valueOf(modifyDNRequest.getNewrdn()), modifyDNRequest
          .isDeleteoldrdn());
    }

    // Create and send the LDAP request to the server.
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and decode the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    ModifyDNResponseProtocolOp modDNOp =
         responseMessage.getModifyDNResponseProtocolOp();
    int resultCode = modDNOp.getResultCode();
    Message errorMessage = modDNOp.getErrorMessage();

    modDNResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = objFactory.createResultCode();
    code.setCode(resultCode);
    modDNResponse.setResultCode(code);

    return modDNResponse;
  }
}

