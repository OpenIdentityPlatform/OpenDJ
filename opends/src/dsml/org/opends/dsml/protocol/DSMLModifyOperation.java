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
import java.util.ArrayList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;



/**
 * This class provides the functionality for the performing an
 * LDAP MODIFY operation based on the specified DSML request.
 */
public class DSMLModifyOperation
{
  private LDAPConnection connection;

  /**
   * Create the instance with the specified LDAP connection.
   *
   * @param connection    The LDAP connection to send the request.
   */
  public DSMLModifyOperation(LDAPConnection connection)
  {
    this.connection = connection;
  }


  /**
   * Perform the LDAP Modify operation and send the result back to the client.
   *
   * @param  objFactory     The object factory for this operation.
   * @param  modifyRequest  The modify request for this operation.
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
        ModifyRequest modifyRequest)
        throws IOException, LDAPException, ASN1Exception
  {
    LDAPResult modResponse = objFactory.createLDAPResult();
    modResponse.setRequestID(modifyRequest.getRequestID());

    ArrayList<RawModification> modifications =
         new ArrayList<RawModification> ();

    // Read the modification type from the DSML request.
    List<DsmlModification> mods = modifyRequest.getModification();
    for(DsmlModification attr : mods)
    {
      String operation = attr.getOperation();
      ModificationType type = ModificationType.ADD;
      if(operation.equals("delete"))
      {
        type = ModificationType.DELETE;
      } else if(operation.equals("replace"))
      {
        type = ModificationType.REPLACE;
      }

      // Read the attribute name and values.
      String attrType = attr.getName();
      ArrayList<ByteString> values = new ArrayList<ByteString> ();

      List<String> vals = attr.getValue();
      for(String val : vals)
      {
        values.add(ByteString.valueOf(val));
      }
      LDAPAttribute ldapAttr = new LDAPAttribute(attrType, values);

      LDAPModification ldapMod = new LDAPModification(type, ldapAttr);
      modifications.add(ldapMod);

    }

    ByteString dnStr = ByteString.valueOf(modifyRequest.getDn());

    // Create and send the LDAP request to the server.
    ProtocolOp op = new ModifyRequestProtocolOp(dnStr, modifications);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and parse the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    ModifyResponseProtocolOp modOp =
         responseMessage.getModifyResponseProtocolOp();
    int resultCode = modOp.getResultCode();
    Message errorMessage = modOp.getErrorMessage();

    // Set the result code and error message for the DSML response.
    modResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = objFactory.createResultCode();
    code.setCode(resultCode);
    modResponse.setResultCode(code);

    return modResponse;
  }

}

