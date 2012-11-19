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
 *      Portions Copyright 2012 ForgeRock AS.
 */
package org.opends.dsml.protocol;



import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;


/**
 * This class provides the functionality for the performing an
 * LDAP EXTENDED operation based on the specified DSML request.
 */
public class DSMLExtendedOperation
{
  private LDAPConnection connection;
  private Set<String> stringResponses;

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
   * @throws  ASN1Exception  If an error occurs while interacting with an ASN.1
   *                         element.
   */
  public ExtendedResponse doOperation(ObjectFactory objFactory,
              ExtendedRequest extendedRequest,
              List<org.opends.server.types.Control> controls)
    throws IOException, LDAPException, ASN1Exception
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
    Message errorMessage = extendedOp.getErrorMessage();

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

