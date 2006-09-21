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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.AuthenticationType;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP bind request
 * protocol op, which is used to authenticate a user to the Directory Server.
 */
public class BindRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The fully-qualified name of this class to use for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.protocols.ldap.BindRequestProtocolOp";



  // The bind DN for this request.
  private ASN1OctetString dn;

  // The SASL credentials for this request.
  private ASN1OctetString saslCredentials;

  // The simple authentication password for this request.
  private ASN1OctetString simplePassword;

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
  public BindRequestProtocolOp(ASN1OctetString dn, int protocolVersion,
                               ASN1OctetString simplePassword)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(protocolVersion),
                            String.valueOf(simplePassword));

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
  public BindRequestProtocolOp(ASN1OctetString dn, String saslMechanism,
                               ASN1OctetString saslCredentials)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(dn),
                            String.valueOf(saslMechanism),
                            String.valueOf(saslCredentials));

    this.dn              = dn;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;

    authenticationType = AuthenticationType.SASL;
    protocolVersion    = 3;
    simplePassword     = null;
  }



  /**
   * Creates a new bind request protocol op to perform SASL authentication with
   * the provided information.
   *
   * @param  dn                  The DN for this bind request.
   * @param  protocolVersion     The protocol version for this bind request.
   * @param  authenticationType  The authentication type for this bind request.
   * @param  simplePassword      The password for this bind request.
   * @param  saslMechanism       The SASL mechanism for this bind request.
   * @param  saslCredentials     The SASL credentials for this bind request.
   */
  private BindRequestProtocolOp(ASN1OctetString dn, int protocolVersion,
                                AuthenticationType authenticationType,
                                ASN1OctetString simplePassword,
                                String saslMechanism,
                                ASN1OctetString saslCredentials)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(dn),
                              String.valueOf(protocolVersion),
                              String.valueOf(authenticationType),
                              String.valueOf(simplePassword),
                              String.valueOf(saslMechanism),
                              String.valueOf(saslCredentials)
                            });

    this.dn                 = dn;
    this.protocolVersion    = protocolVersion;
    this.authenticationType = authenticationType;
    this.simplePassword     = simplePassword;
    this.saslMechanism      = saslMechanism;
    this.saslCredentials    = saslCredentials;
  }



  /**
   * Retrieves the DN for this bind request.
   *
   * @return  The DN for this bind request.
   */
  public ASN1OctetString getDN()
  {
    assert debugEnter(CLASS_NAME, "getDN");

    return dn;
  }



  /**
   * Specifies the DN for this bind request.
   *
   * @param  dn  The DN for this bind request.
   */
  public void setDN(ASN1OctetString dn)
  {
    assert debugEnter(CLASS_NAME, "setDN", String.valueOf(dn));

    this.dn = dn;
  }



  /**
   * Retrieves the protocol version for this bind request.
   *
   * @return  The protocol version for this bind request.
   */
  public int getProtocolVersion()
  {
    assert debugEnter(CLASS_NAME, "getProtocolVersion");

    return protocolVersion;
  }



  /**
   * Specifies the protocol version for this bind request.
   *
   * @param  protocolVersion  The protocol version for this bind request.
   */
  public void setProtocolVersion(int protocolVersion)
  {
    assert debugEnter(CLASS_NAME, "setProtocolVersion",
                      String.valueOf(protocolVersion));

    this.protocolVersion = protocolVersion;
  }



  /**
   * Retrieves the authentication type for this bind request.
   *
   * @return  The authentication type for this bind request.
   */
  public AuthenticationType getAuthenticationType()
  {
    assert debugEnter(CLASS_NAME, "getAuthenticationType");

    return authenticationType;
  }



  /**
   * Specifies the authentication type for this bind request.
   *
   * @param  authenticationType  The authentication type for this bind request.
   */
  public void setAuthenticationType(AuthenticationType authenticationType)
  {
    assert debugEnter(CLASS_NAME, "setAuthenticationType",
                      String.valueOf(authenticationType));

    this.authenticationType = authenticationType;
  }



  /**
   * Retrieves the simple authentication password for this bind request.
   *
   * @return  The simple authentication password for this bind request, or
   *          <CODE>null</CODE> if this is a SASL bind request.
   */
  public ASN1OctetString getSimplePassword()
  {
    assert debugEnter(CLASS_NAME, "getSimplePassword");

    return simplePassword;
  }



  /**
   * Indicates that this bind request should use simple authentication with the
   * provided password.
   *
   * @param  simplePassword  The simple authentication password for this bind
   *                         request.
   */
  public void setSimplePassword(ASN1OctetString simplePassword)
  {
    assert debugEnter(CLASS_NAME, "setSimplePassword",
                      String.valueOf(simplePassword));

    this.simplePassword = simplePassword;
    authenticationType  = AuthenticationType.SIMPLE;
    saslMechanism       = null;
    saslCredentials     = null;
  }



  /**
   * Retrieves the SASL mechanism for this bind request.
   *
   * @return  The SASL mechanism for this bind request, or <CODE>null</CODE> if
   *          this is a simple bind request.
   */
  public String getSASLMechanism()
  {
    assert debugEnter(CLASS_NAME, "getSASLMechanism");

    return saslMechanism;
  }



  /**
   * Retrieves the SASL credentials for this bind request.
   *
   * @return  The SASL credentials for this bind request, or <CODE>null</CODE>
   *          if there are none or if this is a simple bind request.
   */
  public ASN1OctetString getSASLCredentials()
  {
    assert debugEnter(CLASS_NAME, "getSASLCredentials");

    return saslCredentials;
  }



  /**
   * Indicates that this bind request should use SASL authentication with the
   * provided information.
   *
   * @param  saslMechanism    The SASL mechanism for this bind request.
   * @param  saslCredentials  The SASL credentials for this bind request.
   */
  public void setSASLAuthenticationInfo(String saslMechanism,
                                        ASN1OctetString saslCredentials)
  {
    assert debugEnter(CLASS_NAME, "setSASLAuthenticationInfo",
                      String.valueOf(saslMechanism),
                      String.valueOf(saslCredentials));

    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;
    authenticationType   = AuthenticationType.SASL;
    simplePassword       = null;
  }




  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    assert debugEnter(CLASS_NAME, "getType");

    return OP_TYPE_BIND_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    assert debugEnter(CLASS_NAME, "getProtocolOpName");

    return "Bind Request";
  }



  /**
   * Encodes this protocol op to an ASN.1 element suitable for including in an
   * LDAP message.
   *
   * @return  The ASN.1 element containing the encoded protocol op.
   */
  public ASN1Element encode()
  {
    assert debugEnter(CLASS_NAME, "encode");

    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(3);

    elements.add(new ASN1Integer(protocolVersion));
    elements.add(dn);

    if (authenticationType == AuthenticationType.SIMPLE)
    {
      simplePassword.setType(TYPE_AUTHENTICATION_SIMPLE);
      elements.add(simplePassword);
    }
    else
    {
      ArrayList<ASN1Element> saslElements = new ArrayList<ASN1Element>(2);
      saslElements.add(new ASN1OctetString(saslMechanism));
      if (saslCredentials != null)
      {
        saslElements.add(saslCredentials);
      }

      elements.add(new ASN1Sequence(TYPE_AUTHENTICATION_SASL, saslElements));
    }

    return new ASN1Sequence(OP_TYPE_BIND_REQUEST, elements);
  }



  /**
   * Decodes the provided ASN.1 element as an LDAP bind request protocol op.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded LDAP bind request protocol op.
   *
   * @throws  LDAPException  If a problem occurs while trying to decode the
   *                         provided ASN.1 element as an LDAP bind request.
   */
  public static BindRequestProtocolOp decodeBindRequest(ASN1Element element)
         throws LDAPException
  {
    assert debugEnter(CLASS_NAME, "decodeBindRequest", String.valueOf(element));

    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeBindRequest", e);

      int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    int numElements = elements.size();
    if (numElements != 3)
    {
      int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, String.valueOf(numElements));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    int protocolVersion;
    try
    {
      protocolVersion = elements.get(0).decodeAsInteger().intValue();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeBindRequest", e);

      int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_VERSION;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ASN1OctetString dn;
    try
    {
      dn = elements.get(1).decodeAsOctetString();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeBindRequest", e);

      int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_DN;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    AuthenticationType authenticationType;
    ASN1OctetString    simplePassword  = null;
    String             saslMechanism   = null;
    ASN1OctetString    saslCredentials = null;
    try
    {
      element = elements.get(2);
      switch (element.getType())
      {
        case TYPE_AUTHENTICATION_SIMPLE:
          authenticationType = AuthenticationType.SIMPLE;

          try
          {
            simplePassword = element.decodeAsOctetString();
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_PASSWORD;
            String message = getMessage(msgID, String.valueOf(e));
            throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
          }

          break;
        case TYPE_AUTHENTICATION_SASL:
          authenticationType = AuthenticationType.SASL;

          try
          {
            elements = element.decodeAsSequence().elements();

            saslMechanism = elements.get(0).decodeAsOctetString().stringValue();
            if (elements.size() == 2)
            {
              saslCredentials = elements.get(1).decodeAsOctetString();
            }
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_SASL_INFO;
            String message = getMessage(msgID, String.valueOf(e));
            throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
          }

          break;
        default:
          int msgID = MSGID_LDAP_BIND_REQUEST_DECODE_INVALID_CRED_TYPE;
          String message = getMessage(msgID, element.getType());
          throw new LDAPException(AUTH_METHOD_NOT_SUPPORTED, msgID, message);
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "decodeBindRequest", e);

      int    msgID   = MSGID_LDAP_BIND_REQUEST_DECODE_CREDENTIALS;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new BindRequestProtocolOp(dn, protocolVersion, authenticationType,
                                     simplePassword, saslMechanism,
                                     saslCredentials);
  }


  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("BindRequest(version=");
    buffer.append(protocolVersion);
    buffer.append(", dn=");

    if (dn != null)
    {
      dn.toString(buffer);
    }

    if (authenticationType == AuthenticationType.SIMPLE)
    {
      buffer.append(", password=");
      simplePassword.toString(buffer);
    }
    else
    {
      buffer.append(", saslMechanism=");
      buffer.append(saslMechanism);

      if (saslCredentials != null)
      {
        buffer.append(", saslCredentials=");
        saslCredentials.toString(buffer);
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
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder",
                      String.valueOf(indent));

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
      dn.toString(buffer);
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
        saslCredentials.toString(buffer, indent+4);
      }
    }
  }
}

