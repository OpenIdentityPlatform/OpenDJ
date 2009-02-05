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
package org.opends.server.protocols.ldap;


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ByteString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP bind request
 * protocol op, which is used to authenticate a user to the Directory Server.
 */
public class BindRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The bind DN for this request.
  private ByteString dn;

  // The SASL credentials for this request.
  private ByteString saslCredentials;

  // The simple authentication password for this request.
  private ByteString simplePassword;

  // The authentication type for this request.
  private AuthenticationType authenticationType;

  // The protocol version for this bind request.
  private int protocolVersion;

  // The SASL mechanism for this request.
  private String saslMechanism;



  /**
   * Creates a new bind request protocol op to perform simple authentication
   * with the provided DN and password.
   *
   * @param  dn               The DN for this bind request.
   * @param  protocolVersion  The LDAP protocol version for this bind request.
   * @param  simplePassword   The password for this bind request.
   */
  public BindRequestProtocolOp(ByteString dn, int protocolVersion,
                               ByteString simplePassword)
  {
    this.dn              = dn;
    this.protocolVersion = protocolVersion;
    this.simplePassword  = simplePassword;

    authenticationType = AuthenticationType.SIMPLE;
    saslMechanism      = null;
    saslCredentials    = null;
  }



  /**
   * Creates a new bind request protocol op to perform SASL authentication with
   * the provided information.
   *
   * @param  dn               The DN for this bind request.
   * @param  saslMechanism    The SASL mechanism for this bind request.
   * @param  saslCredentials  The SASL credentials for this bind request.
   */
  public BindRequestProtocolOp(ByteString dn, String saslMechanism,
                               ByteString saslCredentials)
  {
    this.dn              = dn;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    authenticationType = AuthenticationType.SASL;
    protocolVersion    = 3;
    simplePassword     = null;
  }



  /**
   * Retrieves the DN for this bind request.
   *
   * @return  The DN for this bind request.
   */
  public ByteString getDN()
  {
    return dn;
  }



  /**
   * Retrieves the protocol version for this bind request.
   *
   * @return  The protocol version for this bind request.
   */
  public int getProtocolVersion()
  {
    return protocolVersion;
  }



  /**
   * Retrieves the authentication type for this bind request.
   *
   * @return  The authentication type for this bind request.
   */
  public AuthenticationType getAuthenticationType()
  {
    return authenticationType;
  }



  /**
   * Retrieves the simple authentication password for this bind request.
   *
   * @return  The simple authentication password for this bind request, or
   *          <CODE>null</CODE> if this is a SASL bind request.
   */
  public ByteString getSimplePassword()
  {
    return simplePassword;
  }



  /**
   * Retrieves the SASL mechanism for this bind request.
   *
   * @return  The SASL mechanism for this bind request, or <CODE>null</CODE> if
   *          this is a simple bind request.
   */
  public String getSASLMechanism()
  {
    return saslMechanism;
  }



  /**
   * Retrieves the SASL credentials for this bind request.
   *
   * @return  The SASL credentials for this bind request, or <CODE>null</CODE>
   *          if there are none or if this is a simple bind request.
   */
  public ByteString getSASLCredentials()
  {
    return saslCredentials;
  }




  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_BIND_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Bind Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_BIND_REQUEST);
    stream.writeInteger(protocolVersion);
    stream.writeOctetString(dn);

    if(authenticationType == AuthenticationType.SIMPLE)
    {
      stream.writeOctetString(TYPE_AUTHENTICATION_SIMPLE, simplePassword);
    }
    else
    {
      stream.writeStartSequence(TYPE_AUTHENTICATION_SASL);
      stream.writeOctetString(saslMechanism);
      if(saslCredentials != null)
      {
        stream.writeOctetString(saslCredentials);
      }
      stream.writeEndSequence();
    }

    stream.writeEndSequence();
  }


  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("BindRequest(version=");
    buffer.append(protocolVersion);
    buffer.append(", dn=");

    if (dn != null)
    {
      buffer.append(dn.toString());
    }

    if (authenticationType == AuthenticationType.SIMPLE)
    {
      buffer.append(", password=");
      buffer.append(simplePassword.toString());
    }
    else
    {
      buffer.append(", saslMechanism=");
      buffer.append(saslMechanism);

      if (saslCredentials != null)
      {
        buffer.append(", saslCredentials=");
        buffer.append(saslCredentials.toString());
      }
    }

    buffer.append(")");
  }



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Bind Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Protocol Version:  ");
    buffer.append(protocolVersion);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  DN:  ");
    if (dn != null)
    {
      buffer.append(dn.toString());
    }
    buffer.append(EOL);

    if (authenticationType == AuthenticationType.SIMPLE)
    {
      buffer.append(indentBuf);
      buffer.append("  Simple Password:  ");
      buffer.append(String.valueOf(simplePassword));
      buffer.append(EOL);
    }
    else
    {
      buffer.append(indentBuf);
      buffer.append("  SASL Mechanism:  ");
      buffer.append(saslMechanism);
      buffer.append(EOL);

      if (saslCredentials != null)
      {
        buffer.append(indentBuf);
        buffer.append("  SASL Credentials:");
        buffer.append(EOL);
        saslCredentials.toHexPlusAscii(buffer, indent+4);
      }
    }
  }
}

