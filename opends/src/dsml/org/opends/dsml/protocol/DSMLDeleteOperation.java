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
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;


/**
 * This class provides the functionality for the performing an
 * LDAP DELETE operation based on the specified DSML request.
 *
 *
 * @author   Vivek Nagar
 */


public class DSMLDeleteOperation
{
  private LDAPConnection connection;

  /**
   * Create an instance with the specified LDAP connection.
   *
   * @param connection    The LDAP connection to send the request on.
   */
  public DSMLDeleteOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Perform the LDAP DELETE operation and send the result back to the
   * client.
   *
   * @param  objFactory     The object factory for this operation.
   * @param  deleteRequest  The delete request for this operation.
   *
   * @return  The result of the delete operation.
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
        DelRequest deleteRequest)
    throws IOException, LDAPException, ASN1Exception
  {
    LDAPResult delResponse = objFactory.createLDAPResult();
    delResponse.setRequestID(deleteRequest.getRequestID());

    // Create and send the LDAP delete request to the server.
    ASN1OctetString dnStr = new ASN1OctetString(deleteRequest.getDn());
    ProtocolOp op = new DeleteRequestProtocolOp(dnStr);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and decode the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    DeleteResponseProtocolOp delOp =
          responseMessage.getDeleteResponseProtocolOp();
    int resultCode = delOp.getResultCode();
    Message errorMessage = delOp.getErrorMessage();

    // Set the result code and error message for the DSML response.
    delResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = objFactory.createResultCode();
    code.setCode(resultCode);
    delResponse.setResultCode(code);

    // set the match DN
    DN dn = delOp.getMatchedDN();
    if ( dn != null ) {
      delResponse.setMatchedDN(dn.toString());
    }

    return delResponse;
  }

}

