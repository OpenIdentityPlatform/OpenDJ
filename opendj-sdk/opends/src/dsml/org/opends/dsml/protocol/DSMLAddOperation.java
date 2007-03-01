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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.dsml.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;



/**
 * This class provides the functionality for the performing an
 * LDAP ADD operation based on the specified DSML request.
 */
public class DSMLAddOperation
{

  private LDAPConnection connection;

  /**
   * Create the instance with the specified LDAP connection.
   *
   * @param connection     The LDAP connection to the server.
   */
  public DSMLAddOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }

  /**
   * Perform the LDAP ADD operation and return the result to the client.
   *
   * @param  objFactory  The object factory for this operation.
   * @param  addRequest  The add request for this operation.
   *
   * @return  The result of the add operation.
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
        AddRequest addRequest)
    throws IOException, LDAPException, ASN1Exception
  {
    LDAPResult addResponse = objFactory.createLDAPResult();

    String requestID = addRequest.getRequestID();
    int reqID = 1;
    try
    {
      reqID = Integer.parseInt(requestID);
    } catch (NumberFormatException nfe)
    {
      throw new IOException(nfe.getMessage());
    }

    // Set the response id.
    addResponse.setRequestID(requestID);

    ASN1OctetString dnStr = new ASN1OctetString(addRequest.getDn());
    ArrayList<LDAPAttribute> attributes = new ArrayList<LDAPAttribute>();

    List<DsmlAttr> addList = addRequest.getAttr();
    for(DsmlAttr attr : addList)
    {
      ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
      List<String> vals = attr.getValue();
      for(String val : vals)
      {
        values.add(new ASN1OctetString(val));
      }
      LDAPAttribute ldapAttribute = new LDAPAttribute(attr.getName(), values);
      attributes.add(ldapAttribute);
    }

    // Create and send the LDAP request to the server.
    ProtocolOp op = new AddRequestProtocolOp(dnStr, attributes);
    LDAPMessage msg = new LDAPMessage(reqID, op);
    int numBytes = connection.getASN1Writer().writeElement(msg.encode());

    // Read and decode the LDAP response from the server.
    ASN1Element element = connection.getASN1Reader().readElement();
    LDAPMessage responseMessage =
         LDAPMessage.decode(ASN1Sequence.decodeAsSequence(element));

    AddResponseProtocolOp addOp = responseMessage.getAddResponseProtocolOp();
    int resultCode = addOp.getResultCode();
    String errorMessage = addOp.getErrorMessage();

    // Set the result code and error message for the DSML response.
    addResponse.setErrorMessage(errorMessage);
    ResultCode code = objFactory.createResultCode();
    code.setCode(resultCode);
    addResponse.setResultCode(code);

    return addResponse;
  }

}

