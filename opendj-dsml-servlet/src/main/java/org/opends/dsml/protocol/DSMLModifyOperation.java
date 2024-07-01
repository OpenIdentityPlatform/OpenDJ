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
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawModification;



/**
 * This class provides the functionality for the performing an
 * LDAP MODIFY operation based on the specified DSML request.
 */
class DSMLModifyOperation
{
  private final LDAPConnection connection;

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
   * @param  controls       Any required controls (e.g. for proxy authz).
   *
   * @return  The result of the modify operation.
   *
   * @throws  IOException  If an I/O problem occurs.
   *
   * @throws  LDAPException  If an error occurs while interacting with an LDAP
   *                         element.
   *
   * @throws  DecodeException  If an error occurs while interacting with an ASN.1
   *                         element.
   */
  public LDAPResult doOperation(ObjectFactory objFactory,
        ModifyRequest modifyRequest,
        List<org.opends.server.types.Control> controls)
        throws IOException, LDAPException, DecodeException
  {
    LDAPResult modResponse = objFactory.createLDAPResult();
    modResponse.setRequestID(modifyRequest.getRequestID());

    ArrayList<RawModification> modifications = new ArrayList<>();

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
      ArrayList<ByteString> values = new ArrayList<>();

      for (Object val : attr.getValue())
      {
        values.add(ByteStringUtility.convertValue(val));
      }
      LDAPAttribute ldapAttr = new LDAPAttribute(attrType, values);

      LDAPModification ldapMod = new LDAPModification(type, ldapAttr);
      modifications.add(ldapMod);

    }

    ByteString dnStr = ByteString.valueOfUtf8(modifyRequest.getDn());

    // Create and send the LDAP request to the server.
    ProtocolOp op = new ModifyRequestProtocolOp(dnStr, modifications);
    LDAPMessage msg = new LDAPMessage(DSMLServlet.nextMessageID(), op,
        controls);
    connection.getLDAPWriter().writeMessage(msg);

    // Read and parse the LDAP response from the server.
    LDAPMessage responseMessage = connection.getLDAPReader().readMessage();

    ModifyResponseProtocolOp modOp =
         responseMessage.getModifyResponseProtocolOp();
    int resultCode = modOp.getResultCode();
    LocalizableMessage errorMessage = modOp.getErrorMessage();

    // Set the result code and error message for the DSML response.
    modResponse.setErrorMessage(
            errorMessage != null ? errorMessage.toString() : null);
    ResultCode code = ResultCodeFactory.create(objFactory, resultCode);
    modResponse.setResultCode(code);

    return modResponse;
  }

}

