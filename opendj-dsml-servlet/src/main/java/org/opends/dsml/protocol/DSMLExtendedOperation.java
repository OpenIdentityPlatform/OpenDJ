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
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.LDAPException;


/**
 * This class provides the functionality for the performing an
 * LDAP EXTENDED operation based on the specified DSML request.
 */
class DSMLExtendedOperation
{
  private final LDAPConnection connection;
  private final Set<String> stringResponses;

  /**
   * Create an instance with the specified LDAP connection.
   *
   * @param connection    The LDAP connection to send the request on.
   * @param stringResponses The OIDs of any operations that have results that
   *                        should be returned as strings instead of binary.
   */
  public DSMLExtendedOperation(LDAPConnection connection,
      Set<String> stringResponses)
  {
    this.connection = connection;
    this.stringResponses = stringResponses;
  }



  /**
   * Determine if the response to a given LDAP extended operation (specified by
   * OID) should be treated as a string. The default is binary.
   *
   * @param oid The OID of the extended operation.
   * @return <CODE>true</CODE> if the extended operation is known to return a
   *         string, <CODE>false</CODE> otherwise.
   */
  public boolean responseIsString(String oid)
  {
    return stringResponses.contains(oid);
  }



  /**
   * Perform the LDAP EXTENDED operation and send the result back to the
   * client.
   *
   * @param  objFactory       The object factory for this operation.
   * @param  extendedRequest  The extended request for this operation.
   * @param  controls         Any required controls (e.g. for proxy authz).
   *
   * @return  The result of the extended operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   *
   * @throws  DecodeException  If an error occurs while interacting with an ASN.1
   *                         element.
   */
  public ExtendedResponse doOperation(ObjectFactory objFactory,
              ExtendedRequest extendedRequest,
              List<org.opends.server.types.Control> controls)
    throws IOException, LDAPException, DecodeException
  {
    ExtendedResponse extendedResponse = objFactory.createExtendedResponse();
    extendedResponse.setRequestID(extendedRequest.getRequestID());

    String requestName = extendedRequest.getRequestName();
    Object value = extendedRequest.getRequestValue();
    ByteString asnValue = ByteStringUtility.convertValue(value);

    // Create and send the LDAP request to the server.
    ProtocolOp op = new ExtendedRequestProtocolOp(requestName, asnValue);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op,
        controls);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and decode the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    ExtendedResponseProtocolOp extendedOp =
          responseMessage.getExtendedResponseProtocolOp();
    int resultCode = extendedOp.getResultCode();
    LocalizableMessage errorMessage = extendedOp.getErrorMessage();

    // Set the result code and error message for the DSML response.
    extendedResponse.setResponseName(extendedOp.getOID());

    ByteString rawValue = extendedOp.getValue();
    value = null;
    if (rawValue != null)
    {
      if (responseIsString(requestName))
      {
        value = rawValue.toString();
      }
      else
      {
        value = rawValue.toByteArray();
      }
    }
    extendedResponse.setResponse(value);
    extendedResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = ResultCodeFactory.create(objFactory, resultCode);
    extendedResponse.setResultCode(code);

    return extendedResponse;
  }

}

