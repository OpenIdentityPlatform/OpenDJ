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
package org.opends.server.tools;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.util.Base64;
import org.opends.server.util.PasswordReader;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.tools.ToolConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides a generic interface that LDAP clients can use to perform
 * various kinds of authentication to the Directory Server.  This handles both
 * simple authentication as well as several SASL mechanisms including:
 * <UL>
 *   <LI>ANONYMOUS</LI>
 *   <LI>CRAM-MD5</LI>
 *   <LI>DIGEST-MD5</LI>
 *   <LI>EXTERNAL</LI>
 *   <LI>GSSAPI</LI>
 *   <LI>PLAIN</LI>
 * </UL>
 * <BR><BR>
 * Note that this implementation is not threadsafe, so if the same
 * <CODE>AuthenticationHandler</CODE> object is to be used concurrently by
 * multiple threads, it must be externally synchronized.
 */
public class LDAPAuthenticationHandler
       implements PrivilegedExceptionAction<Object>, CallbackHandler
{
  // The bind DN for GSSAPI authentication.
  private ASN1OctetString gssapiBindDN;

  // The ASN.1 reader that will be used to read data from the server.
  private ASN1Reader reader;

  // The ASN.1 writer that will be used to send data to the server.
  private ASN1Writer writer;

  // The atomic integer that will be used to obtain message IDs for request
  // messages.
  private AtomicInteger nextMessageID;

  // An array filled with the inner pad byte.
  private byte[] iPad;

  // An array filled with the outer pad byte.
  private byte[] oPad;

  // The authentication password for GSSAPI authentication.
  private char[] gssapiAuthPW;

  // The message digest that will be used to create MD5 hashes.
  private MessageDigest md5Digest;

  // The secure random number generator for use by this authentication handler.
  private SecureRandom secureRandom;

  // The authentication ID for GSSAPI authentication.
  private String gssapiAuthID;

  // The authorization ID for GSSAPI authentication.
  private String gssapiAuthzID;

  // The quality of protection for GSSAPI authentication.
  private String gssapiQoP;

  // The host name used to connect to the remote system.
  private String hostName;

  // The SASL mechanism that will be used for callback authentication.
  private String saslMechanism;



  /**
   * Creates a new instance of this authentication handler.  All initialization
   * will be done lazily to avoid unnecessary performance hits, particularly
   * for cases in which simple authentication will be used as it does not
   * require any particularly expensive processing.
   *
   * @param  reader         The ASN.1 reader that will be used to read data from
   *                        the server.
   * @param  writer         The ASN.1 writer that will be used to send data to
   *                        the server.
   * @param  hostName       The host name used to connect to the remote system
   *                        (fully-qualified if possible).
   * @param  nextMessageID  The atomic integer that will be used to obtain
   *                        message IDs for request messages.
   */
  public LDAPAuthenticationHandler(ASN1Reader reader, ASN1Writer writer,
                                   String hostName, AtomicInteger nextMessageID)
  {
    this.reader        = reader;
    this.writer        = writer;
    this.hostName      = hostName;
    this.nextMessageID = nextMessageID;

    md5Digest    = null;
    secureRandom = null;
    iPad         = null;
    oPad         = null;
  }



  /**
   * Retrieves a list of the SASL mechanisms that are supported by this client
   * library.
   *
   * @return  A list of the SASL mechanisms that are supported by this client
   *          library.
   */
  public static String[] getSupportedSASLMechanisms()
  {
    return new String[]
    {
      SASL_MECHANISM_ANONYMOUS,
      SASL_MECHANISM_CRAM_MD5,
      SASL_MECHANISM_DIGEST_MD5,
      SASL_MECHANISM_EXTERNAL,
      SASL_MECHANISM_GSSAPI,
      SASL_MECHANISM_PLAIN
    };
  }



  /**
   * Retrieves a list of the SASL properties that may be provided for the
   * specified SASL mechanism, mapped from the property names to their
   * corresponding descriptions.
   *
   * @param  mechanism  The name of the SASL mechanism for which to obtain the
   *                    list of supported properties.
   *
   * @return  A list of the SASL properties that may be provided for the
   *          specified SASL mechanism, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLProperties(String mechanism)
  {
    String upperName = toUpperCase(mechanism);
    if (upperName.equals(SASL_MECHANISM_ANONYMOUS))
    {
      return getSASLAnonymousProperties();
    }
    else if (upperName.equals(SASL_MECHANISM_CRAM_MD5))
    {
      return getSASLCRAMMD5Properties();
    }
    else if (upperName.equals(SASL_MECHANISM_DIGEST_MD5))
    {
      return getSASLDigestMD5Properties();
    }
    else if (upperName.equals(SASL_MECHANISM_EXTERNAL))
    {
      return getSASLExternalProperties();
    }
    else if (upperName.equals(SASL_MECHANISM_GSSAPI))
    {
      return getSASLGSSAPIProperties();
    }
    else if (upperName.equals(SASL_MECHANISM_PLAIN))
    {
      return getSASLPlainProperties();
    }
    else
    {
      // This is an unsupported mechanism.
      return null;
    }
  }



  /**
   * Processes a bind using simple authentication with the provided information.
   * If the bind fails, then an exception will be thrown with information about
   * the reason for the failure.  If the bind is successful but there may be
   * some special information that the client should be given, then it will be
   * returned as a String.
   *
   * @param  ldapVersion       The LDAP protocol version to use for the bind
   *                           request.
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if it is to be an anonymous
   *                           bind.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server, or <CODE>null</CODE> if it is to be an
   *                           anonymous bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSimpleBind(int ldapVersion, ASN1OctetString bindDN,
                             ASN1OctetString bindPassword,
                             ArrayList<LDAPControl> requestControls,
                             ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    // See if we need to prompt the user for the password.
    if (bindPassword == null)
    {
      if (bindDN == null)
      {
        bindPassword = new ASN1OctetString();
      }
      else
      {
        System.out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT,
                                    bindDN.stringValue()));
        char[] pwChars = PasswordReader.readPassword();
        if (pwChars == null)
        {
          bindPassword = new ASN1OctetString();
        }
        else
        {
          bindPassword = new ASN1OctetString(getBytes(pwChars));
          Arrays.fill(pwChars, '\u0000');
        }
      }
    }


    // Make sure that critical elements aren't null.
    if (bindDN == null)
    {
      bindDN = new ASN1OctetString();
    }


    // Create the bind request and send it to the server.
    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(bindDN, ldapVersion, bindPassword);
    LDAPMessage bindRequestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest,
                         requestControls);

    try
    {
      writer.writeElement(bindRequestMessage.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SIMPLE_BIND;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SIMPLE_BIND;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message =
             getMessage(msgID, String.valueOf(responseMessage.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    BindResponseProtocolOp bindResponse =
         responseMessage.getBindResponseProtocolOp();
    int resultCode = bindResponse.getResultCode();
    if (resultCode == LDAPResultCode.SUCCESS)
    {
      // FIXME -- Need to look for things like password expiration warning,
      // reset notice, etc.
      return null;
    }

    // FIXME -- Add support for referrals.

    int    msgID   = MSGID_LDAPAUTH_SIMPLE_BIND_FAILED;
    String message = getMessage(msgID, resultCode,
                                LDAPResultCode.toString(resultCode),
                                bindResponse.getErrorMessage());
    throw new LDAPException(bindResponse.getResultCode(), msgID, message);
  }



  /**
   * Processes a SASL bind using the provided information.  If the bind fails,
   * then an exception will be thrown with information about the reason for the
   * failure.  If the bind is successful but there may be some special
   * information that the client should be given, then it will be returned as a
   * String.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server, or <CODE>null</CODE> if this is not a
   *                           password-based SASL mechanism.
   * @param  mechanism         The name of the SASL mechanism to use to
   *                           authenticate to the Directory Server.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLBind(ASN1OctetString bindDN, ASN1OctetString bindPassword,
                           String mechanism,
                           Map<String,List<String>> saslProperties,
                           ArrayList<LDAPControl> requestControls,
                           ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    // Make sure that critical elements aren't null.
    if (bindDN == null)
    {
      bindDN = new ASN1OctetString();
    }

    if ((mechanism == null) || (mechanism.length() == 0))
    {
      int    msgID   = MSGID_LDAPAUTH_NO_SASL_MECHANISM;
      String message = getMessage(msgID);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // Look at the mechanism name and call the appropriate method to process
    // the request.
    saslMechanism = toUpperCase(mechanism);
    if (saslMechanism.equals(SASL_MECHANISM_ANONYMOUS))
    {
      return doSASLAnonymous(bindDN, saslProperties, requestControls,
                             responseControls);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_CRAM_MD5))
    {
      return doSASLCRAMMD5(bindDN, bindPassword, saslProperties,
                           requestControls, responseControls);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_DIGEST_MD5))
    {
      return doSASLDigestMD5(bindDN, bindPassword, saslProperties,
                             requestControls, responseControls);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_EXTERNAL))
    {
      return doSASLExternal(bindDN, saslProperties, requestControls,
                            responseControls);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_GSSAPI))
    {
      return doSASLGSSAPI(bindDN, bindPassword, saslProperties, requestControls,
                          responseControls);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_PLAIN))
    {
      return doSASLPlain(bindDN, bindPassword, saslProperties, requestControls,
                         responseControls);
    }
    else
    {
      int    msgID   = MSGID_LDAPAUTH_UNSUPPORTED_SASL_MECHANISM;
      String message = getMessage(msgID, mechanism);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_AUTH_UNKNOWN, msgID,
                                message);
    }
  }



  /**
   * Processes a SASL ANONYMOUS bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLAnonymous(ASN1OctetString bindDN,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    String trace = null;


    // Evaluate the properties provided.  The only one we'll allow is the trace
    // property, but it is not required.
    if ((saslProperties == null) || saslProperties.isEmpty())
    {
      // This is fine because there are no required properties for this
      // mechanism.
    }
    else
    {
      Iterator<String> propertyNames = saslProperties.keySet().iterator();
      while (propertyNames.hasNext())
      {
        String name = propertyNames.next();
        if (name.equalsIgnoreCase(SASL_PROPERTY_TRACE))
        {
          // This is acceptable, and we'll take any single value.
          List<String> values = saslProperties.get(SASL_PROPERTY_TRACE);
          Iterator<String> iterator = values.iterator();
          if (iterator.hasNext())
          {
            trace = iterator.next();

            if (iterator.hasNext())
            {
              int    msgID   = MSGID_LDAPAUTH_TRACE_SINGLE_VALUED;
              String message = getMessage(msgID);
              throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                        msgID, message);
            }
          }
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_INVALID_SASL_PROPERTY;
          String message = getMessage(msgID, name, SASL_MECHANISM_ANONYMOUS);
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                    msgID, message);
        }
      }
    }


    // Construct the bind request and send it to the server.
    ASN1OctetString saslCredentials;
    if (trace == null)
    {
      saslCredentials = null;
    }
    else
    {
      saslCredentials = new ASN1OctetString(trace);
    }

    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_ANONYMOUS,
                                   saslCredentials);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest,
                         requestControls);

    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_ANONYMOUS,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_ANONYMOUS,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message =
             getMessage(msgID, String.valueOf(responseMessage.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                  msgID, message);
    }


    BindResponseProtocolOp bindResponse =
         responseMessage.getBindResponseProtocolOp();
    int resultCode = bindResponse.getResultCode();
    if (resultCode == LDAPResultCode.SUCCESS)
    {
      // FIXME -- Need to look for things like password expiration warning,
      // reset notice, etc.
      return null;
    }

    // FIXME -- Add support for referrals.

    int    msgID   = MSGID_LDAPAUTH_SASL_BIND_FAILED;
    String message = getMessage(msgID, SASL_MECHANISM_ANONYMOUS, resultCode,
                                LDAPResultCode.toString(resultCode),
                                bindResponse.getErrorMessage());
    throw new LDAPException(bindResponse.getResultCode(), msgID, message);
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL ANONYMOUS bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL ANONYMOUS bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLAnonymousProperties()
  {
    LinkedHashMap<String,String> properties =
         new LinkedHashMap<String,String>(1);

    properties.put(SASL_PROPERTY_TRACE,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_TRACE));

    return properties;
  }



  /**
   * Processes a SASL CRAM-MD5 bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLCRAMMD5(ASN1OctetString bindDN,
                     ASN1OctetString bindPassword,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    String authID  = null;


    // Evaluate the properties provided.  The authID is required, no other
    // properties are allowed.
    if ((saslProperties == null) || saslProperties.isEmpty())
    {
      int    msgID   = MSGID_LDAPAUTH_NO_SASL_PROPERTIES;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }

    Iterator<String> propertyNames = saslProperties.keySet().iterator();
    while (propertyNames.hasNext())
    {
      String name      = propertyNames.next();
      String lowerName = toLowerCase(name);

      if (lowerName.equals(SASL_PROPERTY_AUTHID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          authID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else
      {
        int    msgID   = MSGID_LDAPAUTH_INVALID_SASL_PROPERTY;
        String message = getMessage(msgID, name, SASL_MECHANISM_CRAM_MD5);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                  message);
      }
    }


    // Make sure that the authID was provided.
    if ((authID == null) || (authID.length() == 0))
    {
      int    msgID   = MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // See if the password was null.  If so, then interactively prompt it from
    // the user.
    if (bindPassword == null)
    {
      System.out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, authID));
      char[] pwChars = PasswordReader.readPassword();
      if (pwChars == null)
      {
        bindPassword = new ASN1OctetString();
      }
      else
      {
        bindPassword = new ASN1OctetString(getBytes(pwChars));
        Arrays.fill(pwChars, '\u0000');
      }
    }


    // Construct the initial bind request to send to the server.  In this case,
    // we'll simply indicate that we want to use CRAM-MD5 so the server will
    // send us the challenge.
    BindRequestProtocolOp bindRequest1 =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_CRAM_MD5, null);
    // FIXME -- Should we include request controls in both stages or just the
    // second stage?
    LDAPMessage requestMessage1 =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest1);

    try
    {
      writer.writeElement(requestMessage1.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage1;
    try
    {
      ASN1Element responseElement1 = reader.readElement();
      if (responseElement1 == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage1 =
           LDAPMessage.decode(responseElement1.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage1.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage1.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message = getMessage(msgID,
                              String.valueOf(responseMessage1.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    // Make sure that the bind response has the "SASL bind in progress" result
    // code.
    BindResponseProtocolOp bindResponse1 =
         responseMessage1.getBindResponseProtocolOp();
    int resultCode1 = bindResponse1.getResultCode();
    if (resultCode1 != LDAPResultCode.SASL_BIND_IN_PROGRESS)
    {
      int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5, resultCode1,
                                  LDAPResultCode.toString(resultCode1),
                                  bindResponse1.getErrorMessage());
      throw new LDAPException(resultCode1, msgID, message);
    }


    // Make sure that the bind response contains SASL credentials with the
    // challenge to use for the next stage of the bind.
    ASN1OctetString serverChallenge = bindResponse1.getServerSASLCredentials();
    if (serverChallenge == null)
    {
      int    msgID   = MSGID_LDAPAUTH_NO_CRAMMD5_SERVER_CREDENTIALS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // Use the provided password and credentials to generate the CRAM-MD5
    // response.
    StringBuilder buffer = new StringBuilder();
    buffer.append(authID);
    buffer.append(' ');
    buffer.append(generateCRAMMD5Digest(bindPassword, serverChallenge));


    // Create and send the second bind request to the server.
    BindRequestProtocolOp bindRequest2 =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_CRAM_MD5,
                                   new ASN1OctetString(buffer.toString()));
    LDAPMessage requestMessage2 =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest2,
                         requestControls);

    try
    {
      writer.writeElement(requestMessage2.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage2;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage2 = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage2.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage2.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage2.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message = getMessage(msgID,
                              String.valueOf(responseMessage2.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    BindResponseProtocolOp bindResponse2 =
         responseMessage2.getBindResponseProtocolOp();
    int resultCode2 = bindResponse2.getResultCode();
    if (resultCode2 == LDAPResultCode.SUCCESS)
    {
      // FIXME -- Need to look for things like password expiration warning,
      // reset notice, etc.
      return null;
    }

    // FIXME -- Add support for referrals.

    int    msgID   = MSGID_LDAPAUTH_SASL_BIND_FAILED;
    String message = getMessage(msgID, SASL_MECHANISM_CRAM_MD5, resultCode2,
                                LDAPResultCode.toString(resultCode2),
                                bindResponse2.getErrorMessage());
    throw new LDAPException(bindResponse2.getResultCode(), msgID, message);
  }



  /**
   * Generates the appropriate HMAC-MD5 digest for a CRAM-MD5 authentication
   * with the given information.
   *
   * @param  password   The clear-text password to use when generating the
   *                    digest.
   * @param  challenge  The server-supplied challenge to use when generating the
   *                    digest.
   *
   * @return  The generated HMAC-MD5 digest for CRAM-MD5 authentication.
   *
   * @throws  ClientException  If a problem occurs while attempting to perform
   *                           the necessary initialization.
   */
  private String generateCRAMMD5Digest(ASN1OctetString password,
                                       ASN1OctetString challenge)
          throws ClientException
  {
    // Perform the necessary initialization if it hasn't been done yet.
    if (md5Digest == null)
    {
      try
      {
        md5Digest = MessageDigest.getInstance("MD5");
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_INITIALIZE_MD5_DIGEST;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message, e);
      }
    }

    if (iPad == null)
    {
      iPad = new byte[HMAC_MD5_BLOCK_LENGTH];
      oPad = new byte[HMAC_MD5_BLOCK_LENGTH];
      Arrays.fill(iPad, CRAMMD5_IPAD_BYTE);
      Arrays.fill(oPad, CRAMMD5_OPAD_BYTE);
    }


    // Get the byte arrays backing the password and challenge.
    byte[] p = password.value();
    byte[] c = challenge.value();


    // If the password is longer than the HMAC-MD5 block length, then use an
    // MD5 digest of the password rather than the password itself.
    if (p.length > HMAC_MD5_BLOCK_LENGTH)
    {
      p = md5Digest.digest(p);
    }


    // Create byte arrays with data needed for the hash generation.
    byte[] iPadAndData = new byte[HMAC_MD5_BLOCK_LENGTH + c.length];
    System.arraycopy(iPad, 0, iPadAndData, 0, HMAC_MD5_BLOCK_LENGTH);
    System.arraycopy(c, 0, iPadAndData, HMAC_MD5_BLOCK_LENGTH, c.length);

    byte[] oPadAndHash = new byte[HMAC_MD5_BLOCK_LENGTH + MD5_DIGEST_LENGTH];
    System.arraycopy(oPad, 0, oPadAndHash, 0, HMAC_MD5_BLOCK_LENGTH);


    // Iterate through the bytes in the key and XOR them with the iPad and
    // oPad as appropriate.
    for (int i=0; i < p.length; i++)
    {
      iPadAndData[i] ^= p[i];
      oPadAndHash[i] ^= p[i];
    }


    // Copy an MD5 digest of the iPad-XORed key and the data into the array to
    // be hashed.
    System.arraycopy(md5Digest.digest(iPadAndData), 0, oPadAndHash,
                     HMAC_MD5_BLOCK_LENGTH, MD5_DIGEST_LENGTH);


    // Calculate an MD5 digest of the resulting array and get the corresponding
    // hex string representation.
    byte[] digestBytes = md5Digest.digest(oPadAndHash);

    StringBuilder hexDigest = new StringBuilder(2*digestBytes.length);
    for (byte b : digestBytes)
    {
      hexDigest.append(byteToLowerHex(b));
    }

    return hexDigest.toString();
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL CRAM-MD5 bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL CRAM-MD5 bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLCRAMMD5Properties()
  {
    LinkedHashMap<String,String> properties =
         new LinkedHashMap<String,String>(1);

    properties.put(SASL_PROPERTY_AUTHID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID));

    return properties;
  }



  /**
   * Processes a SASL DIGEST-MD5 bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLDigestMD5(ASN1OctetString bindDN,
                     ASN1OctetString bindPassword,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    String  authID               = null;
    String  realm                = null;
    String  qop                  = "auth";
    String  digestURI            = "ldap/" + hostName;
    String  authzID              = null;
    boolean realmSetFromProperty = false;


    // Evaluate the properties provided.  The authID is required.  The realm,
    // QoP, digest URI, and authzID are optional.
    if ((saslProperties == null) || saslProperties.isEmpty())
    {
      int    msgID   = MSGID_LDAPAUTH_NO_SASL_PROPERTIES;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }

    Iterator<String> propertyNames = saslProperties.keySet().iterator();
    while (propertyNames.hasNext())
    {
      String name      = propertyNames.next();
      String lowerName = toLowerCase(name);

      if (lowerName.equals(SASL_PROPERTY_AUTHID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          authID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_REALM))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_REALM);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          realm                = iterator.next();
          realmSetFromProperty = true;

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_REALM_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_QOP))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_QOP);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          qop = toLowerCase(iterator.next());

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_QOP_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }

          if (qop.equals("auth"))
          {
            // This is always fine.
          }
          else if (qop.equals("auth-int") || qop.equals("auth-conf"))
          {
            // FIXME -- Add support for integrity and confidentiality.
            int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_QOP_NOT_SUPPORTED;
            String message = getMessage(msgID, qop);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
          else
          {
            // This is an illegal value.
            int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_INVALID_QOP;
            String message = getMessage(msgID, qop);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_DIGEST_URI))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_DIGEST_URI);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          digestURI = toLowerCase(iterator.next());

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_DIGEST_URI_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_AUTHZID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHZID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          authzID = toLowerCase(iterator.next());

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHZID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else
      {
        int    msgID   = MSGID_LDAPAUTH_INVALID_SASL_PROPERTY;
        String message = getMessage(msgID, name, SASL_MECHANISM_DIGEST_MD5);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                  message);
      }
    }


    // Make sure that the authID was provided.
    if ((authID == null) || (authID.length() == 0))
    {
      int    msgID   = MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // See if the password was null.  If so, then interactively prompt it from
    // the user.
    if (bindPassword == null)
    {
      System.out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, authID));
      char[] pwChars = PasswordReader.readPassword();
      if (pwChars == null)
      {
        bindPassword = new ASN1OctetString();
      }
      else
      {
        bindPassword = new ASN1OctetString(getBytes(pwChars));
        Arrays.fill(pwChars, '\u0000');
      }
    }


    // Construct the initial bind request to send to the server.  In this case,
    // we'll simply indicate that we want to use DIGEST-MD5 so the server will
    // send us the challenge.
    BindRequestProtocolOp bindRequest1 =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_DIGEST_MD5, null);
    // FIXME -- Should we include request controls in both stages or just the
    // second stage?
    LDAPMessage requestMessage1 =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest1);

    try
    {
      writer.writeElement(requestMessage1.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_INITIAL_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage1;
    try
    {
      ASN1Element responseElement1 = reader.readElement();
      if (responseElement1 == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage1 =
           LDAPMessage.decode(responseElement1.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage1.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage1.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message = getMessage(msgID,
                              String.valueOf(responseMessage1.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    // Make sure that the bind response has the "SASL bind in progress" result
    // code.
    BindResponseProtocolOp bindResponse1 =
         responseMessage1.getBindResponseProtocolOp();
    int resultCode1 = bindResponse1.getResultCode();
    if (resultCode1 != LDAPResultCode.SASL_BIND_IN_PROGRESS)
    {
      int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_INITIAL_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5, resultCode1,
                                  LDAPResultCode.toString(resultCode1),
                                  bindResponse1.getErrorMessage());
      throw new LDAPException(resultCode1, msgID, message);
    }


    // Make sure that the bind response contains SASL credentials with the
    // information to use for the next stage of the bind.
    ASN1OctetString serverCredentials =
         bindResponse1.getServerSASLCredentials();
    if (serverCredentials == null)
    {
      int    msgID   = MSGID_LDAPAUTH_NO_DIGESTMD5_SERVER_CREDENTIALS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // Parse the server SASL credentials to get the necessary information.  In
    // particular, look at the realm, the nonce, the QoP modes, and the charset.
    // We'll only care about the realm if none was provided in the SASL
    // properties and only one was provided in the server SASL credentials.
    String  credString = serverCredentials.stringValue();
    String  lowerCreds = toLowerCase(credString);
    String  nonce      = null;
    boolean useUTF8    = false;
    int     pos        = 0;
    int     length     = credString.length();
    while (pos < length)
    {
      int equalPos = credString.indexOf('=', pos+1);
      if (equalPos < 0)
      {
        // This is bad because we're not at the end of the string but we don't
        // have a name/value delimiter.
        int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS;
        String message = getMessage(msgID, pos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }


      String tokenName  = lowerCreds.substring(pos, equalPos);

      StringBuilder valueBuffer = new StringBuilder();
      pos = readToken(credString, equalPos+1, length, valueBuffer);
      String tokenValue = valueBuffer.toString();

      if (tokenName.equals("charset"))
      {
        // The value must be the string "utf-8".  If not, that's an error.
        if (! tokenValue.equalsIgnoreCase("utf-8"))
        {
          int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_INVALID_CHARSET;
          String message = getMessage(msgID, tokenValue);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
        }

        useUTF8 = true;
      }
      else if (tokenName.equals("realm"))
      {
        // This will only be of interest to us if there is only a single realm
        // in the server credentials and none was provided as a client-side
        // property.
        if (! realmSetFromProperty)
        {
          if (realm == null)
          {
            // No other realm was specified, so we'll use this one for now.
            realm = tokenValue;
          }
          else
          {
            // This must mean that there are multiple realms in the server
            // credentials.  In that case, we'll not provide any realm at all.
            // To make sure that happens, pretend that the client specified the
            // realm.
            realm                = null;
            realmSetFromProperty = true;
          }
        }
      }
      else if (tokenName.equals("nonce"))
      {
        nonce = tokenValue;
      }
      else if (tokenName.equals("qop"))
      {
        // The QoP modes provided by the server should be a comma-delimited
        // list.  Decode that list and make sure the QoP we have chosen is in
        // that list.
        StringTokenizer tokenizer = new StringTokenizer(tokenValue, ",");
        LinkedList<String> qopModes = new LinkedList<String>();
        while (tokenizer.hasMoreTokens())
        {
          qopModes.add(toLowerCase(tokenizer.nextToken().trim()));
        }

        if (! qopModes.contains(qop))
        {
          int    msgID   = MSGID_LDAPAUTH_REQUESTED_QOP_NOT_SUPPORTED_BY_SERVER;
          String message = getMessage(msgID, qop, tokenValue);
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                    msgID, message);
        }
      }
      else
      {
        // Other values may have been provided, but they aren't of interest to
        // us because they shouldn't change anything about the way we encode the
        // second part of the request.  Rather than attempt to examine them,
        // we'll assume that the server sent a valid response.
      }
    }


    // Make sure that the nonce was included in the response from the server.
    if (nonce == null)
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_NO_NONCE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // Generate the cnonce that we will use for this request.
    String cnonce = generateCNonce();


    // Generate the response digest, and initialize the necessary remaining
    // variables to use in the generation of that digest.
    String nonceCount = "00000001";
    String charset    = (useUTF8 ? "UTF-8" : "ISO-8859-1");
    String responseDigest;
    try
    {
      responseDigest = generateDigestMD5Response(authID, authzID,
                                                 bindPassword.value(), realm,
                                                 nonce, cnonce, nonceCount,
                                                 digestURI, qop, charset);
    }
    catch (ClientException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_CANNOT_CREATE_RESPONSE_DIGEST;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // Generate the SASL credentials for the second bind request.
    StringBuilder credBuffer = new StringBuilder();
    credBuffer.append("username=\"");
    credBuffer.append(authID);
    credBuffer.append("\"");

    if (realm != null)
    {
      credBuffer.append(",realm=\"");
      credBuffer.append(realm);
      credBuffer.append("\"");
    }

    credBuffer.append(",nonce=\"");
    credBuffer.append(nonce);
    credBuffer.append("\",cnonce=\"");
    credBuffer.append(cnonce);
    credBuffer.append("\",nc=");
    credBuffer.append(nonceCount);
    credBuffer.append(",qop=");
    credBuffer.append(qop);
    credBuffer.append(",digest-uri=\"");
    credBuffer.append(digestURI);
    credBuffer.append("\",response=");
    credBuffer.append(responseDigest);

    if (useUTF8)
    {
      credBuffer.append(",charset=utf-8");
    }

    if (authzID != null)
    {
      credBuffer.append(",authzid=\"");
      credBuffer.append(authzID);
      credBuffer.append("\"");
    }


    // Generate and send the second bind request.
    BindRequestProtocolOp bindRequest2 =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_DIGEST_MD5,
                                   new ASN1OctetString(credBuffer.toString()));
    LDAPMessage requestMessage2 =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest2,
                         requestControls);

    try
    {
      writer.writeElement(requestMessage2.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SECOND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage2;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage2 = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_SECOND_BIND_RESPONSE;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage2.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage2.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage2.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message = getMessage(msgID,
                              String.valueOf(responseMessage2.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    BindResponseProtocolOp bindResponse2 =
         responseMessage2.getBindResponseProtocolOp();
    int resultCode2 = bindResponse2.getResultCode();
    if (resultCode2 != LDAPResultCode.SUCCESS)
    {
      // FIXME -- Add support for referrals.

      int    msgID   = MSGID_LDAPAUTH_SASL_BIND_FAILED;
      String message = getMessage(msgID, SASL_MECHANISM_DIGEST_MD5, resultCode2,
                                  LDAPResultCode.toString(resultCode2),
                                  bindResponse2.getErrorMessage());
      throw new LDAPException(bindResponse2.getResultCode(), msgID, message);
    }


    // Make sure that the bind response included server SASL credentials with
    // the appropriate rspauth value.
    ASN1OctetString rspAuthCreds = bindResponse2.getServerSASLCredentials();
    if (rspAuthCreds == null)
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_NO_RSPAUTH_CREDS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    String credStr = toLowerCase(rspAuthCreds.stringValue());
    if (! credStr.startsWith("rspauth="))
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_NO_RSPAUTH_CREDS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    byte[] serverRspAuth;
    try
    {
      serverRspAuth = hexStringToByteArray(credStr.substring(8));
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_DECODE_RSPAUTH;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    byte[] clientRspAuth;
    try
    {
      clientRspAuth =
           generateDigestMD5RspAuth(authID, authzID, bindPassword.value(),
                                    realm, nonce, cnonce, nonceCount, digestURI,
                                    qop, charset);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_COULD_NOT_CALCULATE_RSPAUTH;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message);
    }

    if (! Arrays.equals(serverRspAuth, clientRspAuth))
    {
      int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_RSPAUTH_MISMATCH;
      String message = getMessage(msgID);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message);
    }

    // FIXME -- Need to look for things like password expiration warning,
    // reset notice, etc.
    return null;
  }



  /**
   * Reads the next token from the provided credentials string using the
   * provided information.  If the token is surrounded by quotation marks, then
   * the token returned will not include those quotation marks.
   *
   * @param  credentials  The credentials string from which to read the token.
   * @param  startPos     The position of the first character of the token to
   *                      read.
   * @param  length       The total number of characters in the credentials
   *                      string.
   * @param  token        The buffer into which the token is to be placed.
   *
   * @return  The position at which the next token should start, or a value
   *          greater than or equal to the length of the string if there are no
   *          more tokens.
   *
   * @throws  LDAPException  If a problem occurs while attempting to read the
   *                         token.
   */
  private int readToken(String credentials, int startPos, int length,
                        StringBuilder token)
          throws LDAPException
  {
    // If the position is greater than or equal to the length, then we shouldn't
    // do anything.
    if (startPos >= length)
    {
      return startPos;
    }


    // Look at the first character to see if it's an empty string or the string
    // is quoted.
    boolean isEscaped = false;
    boolean isQuoted  = false;
    int     pos       = startPos;
    char    c         = credentials.charAt(pos++);

    if (c == ',')
    {
      // This must be a zero-length token, so we'll just return the next
      // position.
      return pos;
    }
    else if (c == '"')
    {
      // The string is quoted, so we'll ignore this character, and we'll keep
      // reading until we find the unescaped closing quote followed by a comma
      // or the end of the string.
      isQuoted = true;
    }
    else if (c == '\\')
    {
      // The next character is escaped, so we'll take it no matter what.
      isEscaped = true;
    }
    else
    {
      // The string is not quoted, and this is the first character.  Store this
      // character and keep reading until we find a comma or the end of the
      // string.
      token.append(c);
    }


    // Enter a loop, reading until we find the appropriate criteria for the end
    // of the token.
    while (pos < length)
    {
      c = credentials.charAt(pos++);

      if (isEscaped)
      {
        // The previous character was an escape, so we'll take this no matter
        // what.
        token.append(c);
        isEscaped = false;
      }
      else if (c == ',')
      {
        // If this is a quoted string, then this comma is part of the token.
        // Otherwise, it's the end of the token.
        if (isQuoted)
        {
          token.append(c);
        }
        else
        {
          break;
        }
      }
      else if (c == '"')
      {
        if (isQuoted)
        {
          // This should be the end of the token, but in order for it to be
          // valid it must be followed by a comma or the end of the string.
          if (pos >= length)
          {
            // We have hit the end of the string, so this is fine.
            break;
          }
          else
          {
            char c2 = credentials.charAt(pos++);
            if (c2 == ',')
            {
              // We have hit the end of the token, so this is fine.
              break;
            }
            else
            {
              // We found the closing quote before the end of the token.  This
              // is not fine.
              int msgID = MSGID_LDAPAUTH_DIGESTMD5_INVALID_CLOSING_QUOTE_POS;
              String message = getMessage(msgID, (pos-2));
              throw new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                                      msgID, message);
            }
          }
        }
        else
        {
          // This must be part of the value, so we'll take it.
          token.append(c);
        }
      }
      else if (c == '\\')
      {
        // The next character is escaped.  We'll set a flag so we know to
        // accept it, but will not include the backspace itself.
        isEscaped = true;
      }
      else
      {
        token.append(c);
      }
    }


    return pos;
  }



  /**
   * Generates a cnonce value to use during the DIGEST-MD5 authentication
   * process.
   *
   * @return  The cnonce that should be used for DIGEST-MD5 authentication.
   */
  private String generateCNonce()
  {
    if (secureRandom == null)
    {
      secureRandom = new SecureRandom();
    }

    byte[] cnonceBytes = new byte[16];
    secureRandom.nextBytes(cnonceBytes);

    return Base64.encode(cnonceBytes);
  }



  /**
   * Generates the appropriate DIGEST-MD5 response for the provided set of
   * information.
   *
   * @param  authID    The username from the authentication request.
   * @param  authzID     The authorization ID from the request, or
   *                     <CODE>null</CODE> if there is none.
   * @param  password    The clear-text password for the user.
   * @param  realm       The realm for which the authentication is to be
   *                     performed.
   * @param  nonce       The random data generated by the server for use in the
   *                     digest.
   * @param  cnonce      The random data generated by the client for use in the
   *                     digest.
   * @param  nonceCount  The 8-digit hex string indicating the number of times
   *                     the provided nonce has been used by the client.
   * @param  digestURI   The digest URI that specifies the service and host for
   *                     which the authentication is being performed.
   * @param  qop         The quality of protection string for the
   *                     authentication.
   * @param  charset     The character set used to encode the information.
   *
   * @return  The DIGEST-MD5 response for the provided set of information.
   *
   * @throws  ClientException  If a problem occurs while attempting to
   *                           initialize the MD5 digest.
   *
   * @throws  UnsupportedEncodingException  If the specified character set is
   *                                        invalid for some reason.
   */
  private String generateDigestMD5Response(String authID, String authzID,
                                           byte[] password, String realm,
                                           String nonce, String cnonce,
                                           String nonceCount, String digestURI,
                                           String qop, String charset)
          throws ClientException, UnsupportedEncodingException
  {
    // Perform the necessary initialization if it hasn't been done yet.
    if (md5Digest == null)
    {
      try
      {
        md5Digest = MessageDigest.getInstance("MD5");
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_INITIALIZE_MD5_DIGEST;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message, e);
      }
    }


    // Get a hash of "username:realm:password".
    StringBuilder a1String1 = new StringBuilder();
    a1String1.append(authID);
    a1String1.append(':');
    a1String1.append((realm == null) ? "" : realm);
    a1String1.append(':');

    byte[] a1Bytes1a = a1String1.toString().getBytes(charset);
    byte[] a1Bytes1  = new byte[a1Bytes1a.length + password.length];
    System.arraycopy(a1Bytes1a, 0, a1Bytes1, 0, a1Bytes1a.length);
    System.arraycopy(password, 0, a1Bytes1, a1Bytes1a.length, password.length);
    byte[] urpHash = md5Digest.digest(a1Bytes1);


    // Next, get a hash of "urpHash:nonce:cnonce[:authzid]".
    StringBuilder a1String2 = new StringBuilder();
    a1String2.append(':');
    a1String2.append(nonce);
    a1String2.append(':');
    a1String2.append(cnonce);
    if (authzID != null)
    {
      a1String2.append(':');
      a1String2.append(authzID);
    }
    byte[] a1Bytes2a = a1String2.toString().getBytes(charset);
    byte[] a1Bytes2  = new byte[urpHash.length + a1Bytes2a.length];
    System.arraycopy(urpHash, 0, a1Bytes2, 0, urpHash.length);
    System.arraycopy(a1Bytes2a, 0, a1Bytes2, urpHash.length, a1Bytes2a.length);
    byte[] a1Hash = md5Digest.digest(a1Bytes2);


    // Next, get a hash of "AUTHENTICATE:digesturi".
    byte[] a2Bytes = ("AUTHENTICATE:" + digestURI).getBytes(charset);
    byte[] a2Hash  = md5Digest.digest(a2Bytes);


    // Get hex string representations of the last two hashes.
    String a1HashHex = getHexString(a1Hash);
    String a2HashHex = getHexString(a2Hash);


    // Put together the final string to hash, consisting of
    // "a1HashHex:nonce:nonceCount:cnonce:qop:a2HashHex" and get its digest.
    StringBuilder kdStr = new StringBuilder();
    kdStr.append(a1HashHex);
    kdStr.append(':');
    kdStr.append(nonce);
    kdStr.append(':');
    kdStr.append(nonceCount);
    kdStr.append(':');
    kdStr.append(cnonce);
    kdStr.append(':');
    kdStr.append(qop);
    kdStr.append(':');
    kdStr.append(a2HashHex);

    return getHexString(md5Digest.digest(kdStr.toString().getBytes(charset)));
  }



  /**
   * Generates the appropriate DIGEST-MD5 rspauth digest using the provided
   * information.
   *
   * @param  authID      The username from the authentication request.
   * @param  authzID     The authorization ID from the request, or
   *                     <CODE>null</CODE> if there is none.
   * @param  password    The clear-text password for the user.
   * @param  realm       The realm for which the authentication is to be
   *                     performed.
   * @param  nonce       The random data generated by the server for use in the
   *                     digest.
   * @param  cnonce      The random data generated by the client for use in the
   *                     digest.
   * @param  nonceCount  The 8-digit hex string indicating the number of times
   *                     the provided nonce has been used by the client.
   * @param  digestURI   The digest URI that specifies the service and host for
   *                     which the authentication is being performed.
   * @param  qop         The quality of protection string for the
   *                     authentication.
   * @param  charset     The character set used to encode the information.
   *
   * @return  The DIGEST-MD5 response for the provided set of information.
   *
   * @throws  UnsupportedEncodingException  If the specified character set is
   *                                        invalid for some reason.
   */
  public byte[] generateDigestMD5RspAuth(String authID, String authzID,
                                         byte[] password, String realm,
                                         String nonce, String cnonce,
                                         String nonceCount, String digestURI,
                                         String qop, String charset)
         throws UnsupportedEncodingException
  {
    // First, get a hash of "username:realm:password".
    StringBuilder a1String1 = new StringBuilder();
    a1String1.append(authID);
    a1String1.append(':');
    a1String1.append(realm);
    a1String1.append(':');

    byte[] a1Bytes1a = a1String1.toString().getBytes(charset);
    byte[] a1Bytes1  = new byte[a1Bytes1a.length + password.length];
    System.arraycopy(a1Bytes1a, 0, a1Bytes1, 0, a1Bytes1a.length);
    System.arraycopy(password, 0, a1Bytes1, a1Bytes1a.length,
                     password.length);
    byte[] urpHash = md5Digest.digest(a1Bytes1);


    // Next, get a hash of "urpHash:nonce:cnonce[:authzid]".
    StringBuilder a1String2 = new StringBuilder();
    a1String2.append(':');
    a1String2.append(nonce);
    a1String2.append(':');
    a1String2.append(cnonce);
    if (authzID != null)
    {
      a1String2.append(':');
      a1String2.append(authzID);
    }
    byte[] a1Bytes2a = a1String2.toString().getBytes(charset);
    byte[] a1Bytes2  = new byte[urpHash.length + a1Bytes2a.length];
    System.arraycopy(urpHash, 0, a1Bytes2, 0, urpHash.length);
    System.arraycopy(a1Bytes2a, 0, a1Bytes2, urpHash.length,
                     a1Bytes2a.length);
    byte[] a1Hash = md5Digest.digest(a1Bytes2);


    // Next, get a hash of "AUTHENTICATE:digesturi".
    String a2String = ":" + digestURI;
    if (qop.equals("auth-int") || qop.equals("auth-conf"))
    {
      a2String += ":00000000000000000000000000000000";
    }
    byte[] a2Bytes = a2String.getBytes(charset);
    byte[] a2Hash  = md5Digest.digest(a2Bytes);


    // Get hex string representations of the last two hashes.
    String a1HashHex = getHexString(a1Hash);
    String a2HashHex = getHexString(a2Hash);


    // Put together the final string to hash, consisting of
    // "a1HashHex:nonce:nonceCount:cnonce:qop:a2HashHex" and get its digest.
    StringBuilder kdStr = new StringBuilder();
    kdStr.append(a1HashHex);
    kdStr.append(':');
    kdStr.append(nonce);
    kdStr.append(':');
    kdStr.append(nonceCount);
    kdStr.append(':');
    kdStr.append(cnonce);
    kdStr.append(':');
    kdStr.append(qop);
    kdStr.append(':');
    kdStr.append(a2HashHex);
    return md5Digest.digest(kdStr.toString().getBytes(charset));
  }



  /**
   * Retrieves a hexadecimal string representation of the contents of the
   * provided byte array.
   *
   * @param  byteArray  The byte array for which to obtain the hexadecimal
   *                    string representation.
   *
   * @return  The hexadecimal string representation of the contents of the
   *          provided byte array.
   */
  private String getHexString(byte[] byteArray)
  {
    StringBuilder buffer = new StringBuilder(2*byteArray.length);
    for (byte b : byteArray)
    {
      buffer.append(byteToLowerHex(b));
    }

    return buffer.toString();
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL DIGEST-MD5 bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL DIGEST-MD5 bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLDigestMD5Properties()
  {
    LinkedHashMap<String,String> properties =
         new LinkedHashMap<String,String>(5);

    properties.put(SASL_PROPERTY_AUTHID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID));
    properties.put(SASL_PROPERTY_REALM,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_REALM));
    properties.put(SASL_PROPERTY_QOP,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_QOP));
    properties.put(SASL_PROPERTY_DIGEST_URI,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_DIGEST_URI));
    properties.put(SASL_PROPERTY_AUTHZID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHZID));

    return properties;
  }



  /**
   * Processes a SASL EXTERNAL bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.  SASL EXTERNAL does not
   *                           take any properties, so this should be empty or
   *                           <CODE>null</CODE>.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLExternal(ASN1OctetString bindDN,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    // Make sure that no SASL properties were provided.
    if ((saslProperties != null) && (! saslProperties.isEmpty()))
    {
      int    msgID   = MSGID_LDAPAUTH_NO_ALLOWED_SASL_PROPERTIES;
      String message = getMessage(msgID, SASL_MECHANISM_EXTERNAL);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // Construct the bind request and send it to the server.
    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_EXTERNAL, null);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest,
                         requestControls);

    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_EXTERNAL,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_EXTERNAL,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message =
             getMessage(msgID, String.valueOf(responseMessage.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    BindResponseProtocolOp bindResponse =
         responseMessage.getBindResponseProtocolOp();
    int resultCode = bindResponse.getResultCode();
    if (resultCode == LDAPResultCode.SUCCESS)
    {
      // FIXME -- Need to look for things like password expiration warning,
      // reset notice, etc.
      return null;
    }

    // FIXME -- Add support for referrals.

    int    msgID   = MSGID_LDAPAUTH_SASL_BIND_FAILED;
    String message = getMessage(msgID, SASL_MECHANISM_EXTERNAL, resultCode,
                                LDAPResultCode.toString(resultCode),
                                bindResponse.getErrorMessage());
    throw new LDAPException(bindResponse.getResultCode(), msgID, message);
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL EXTERNAL bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL EXTERNAL bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLExternalProperties()
  {
    // There are no properties for the SASL EXTERNAL mechanism.
    return new LinkedHashMap<String,String>(0);
  }



  /**
   * Processes a SASL GSSAPI bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.  SASL EXTERNAL does not
   *                           take any properties, so this should be empty or
   *                           <CODE>null</CODE>.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLGSSAPI(ASN1OctetString bindDN,
                     ASN1OctetString bindPassword,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    String kdc     = null;
    String realm   = null;

    gssapiBindDN  = bindDN;
    gssapiAuthID  = null;
    gssapiAuthzID = null;
    gssapiQoP     = "auth";

    if (bindPassword == null)
    {
      gssapiAuthPW = null;
    }
    else
    {
      gssapiAuthPW = bindPassword.stringValue().toCharArray();
    }


    // Evaluate the properties provided.  The authID is required.  The authzID,
    // KDC, QoP, and realm are optional.
    if ((saslProperties == null) || saslProperties.isEmpty())
    {
      int    msgID   = MSGID_LDAPAUTH_NO_SASL_PROPERTIES;
      String message = getMessage(msgID, SASL_MECHANISM_GSSAPI);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }

    Iterator<String> propertyNames = saslProperties.keySet().iterator();
    while (propertyNames.hasNext())
    {
      String name      = propertyNames.next();
      String lowerName = toLowerCase(name);

      if (lowerName.equals(SASL_PROPERTY_AUTHID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          gssapiAuthID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_AUTHZID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHZID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          gssapiAuthzID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHZID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_KDC))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_KDC);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          kdc = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_KDC_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_QOP))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_QOP);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          gssapiQoP = toLowerCase(iterator.next());

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_QOP_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }

          if (gssapiQoP.equals("auth"))
          {
            // This is always fine.
          }
          else if (gssapiQoP.equals("auth-int") ||
                   gssapiQoP.equals("auth-conf"))
          {
            // FIXME -- Add support for integrity and confidentiality.
            int    msgID   = MSGID_LDAPAUTH_DIGESTMD5_QOP_NOT_SUPPORTED;
            String message = getMessage(msgID, gssapiQoP);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
          else
          {
            // This is an illegal value.
            int    msgID   = MSGID_LDAPAUTH_GSSAPI_INVALID_QOP;
            String message = getMessage(msgID, gssapiQoP);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_REALM))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_REALM);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          realm = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_REALM_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else
      {
        int    msgID   = MSGID_LDAPAUTH_INVALID_SASL_PROPERTY;
        String message = getMessage(msgID, name, SASL_MECHANISM_GSSAPI);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                  message);
      }
    }


    // Make sure that the authID was provided.
    if ((gssapiAuthID == null) || (gssapiAuthID.length() == 0))
    {
      int    msgID   = MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED;
      String message = getMessage(msgID, SASL_MECHANISM_GSSAPI);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // See if an authzID was provided.  If not, then use the authID.
    if (gssapiAuthzID == null)
    {
      gssapiAuthzID = gssapiAuthID;
    }


    // See if the realm and/or KDC were specified.  If so, then set properties
    // that will allow them to be used.  Otherwise, we'll hope that the
    // underlying system has a valid Kerberos client configuration.
    if (realm != null)
    {
      System.setProperty(KRBV_PROPERTY_REALM, realm);
    }

    if (kdc != null)
    {
      System.setProperty(KRBV_PROPERTY_KDC, kdc);
    }


    // Since we're going to be using JAAS behind the scenes, we need to have a
    // JAAS configuration.  Rather than always requiring the user to provide it,
    // we'll write one to a temporary file that will be deleted when the JVM
    // exits.
    String configFileName;
    try
    {
      File tempFile = File.createTempFile("login", "conf");
      configFileName = tempFile.getAbsolutePath();
      tempFile.deleteOnExit();
      BufferedWriter w = new BufferedWriter(new FileWriter(tempFile, false));

      w.write(getClass().getName() + " {");
      w.newLine();

      w.write("  com.sun.security.auth.module.Krb5LoginModule required " +
              "client=TRUE useTicketCache=TRUE;");
      w.newLine();

      w.write("};");
      w.newLine();

      w.flush();
      w.close();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_JAAS_CONFIG;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }

    System.setProperty(JAAS_PROPERTY_CONFIG_FILE, configFileName);
    System.setProperty(JAAS_PROPERTY_SUBJECT_CREDS_ONLY, "true");


    // The rest of this code must be executed via JAAS, so it will have to go
    // in the "run" method.
    LoginContext loginContext;
    try
    {
      loginContext = new LoginContext(getClass().getName(), this);
      loginContext.login();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_GSSAPI_LOCAL_AUTHENTICATION_FAILED;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }

    try
    {
      Subject.doAs(loginContext.getSubject(), this);
    }
    catch (Exception e)
    {
      if (e instanceof ClientException)
      {
        throw (ClientException) e;
      }
      else if (e instanceof LDAPException)
      {
        throw (LDAPException) e;
      }

      int    msgID   = MSGID_LDAPAUTH_GSSAPI_REMOTE_AUTHENTICATION_FAILED;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // FIXME --  Need to make sure we handle request and response controls
    // properly, and also check for any possible message to send back to the
    // client.
    return null;
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL EXTERNAL bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL EXTERNAL bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLGSSAPIProperties()
  {
    LinkedHashMap<String,String> properties =
         new LinkedHashMap<String,String>(4);

    properties.put(SASL_PROPERTY_AUTHID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID));
    properties.put(SASL_PROPERTY_AUTHZID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHZID));
    properties.put(SASL_PROPERTY_KDC,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_KDC));
    properties.put(SASL_PROPERTY_REALM,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_REALM));

    return properties;
  }



  /**
   * Processes a SASL PLAIN bind with the provided information.
   *
   * @param  bindDN            The DN to use to bind to the Directory Server, or
   *                           <CODE>null</CODE> if the authentication identity
   *                           is to be set through some other means.
   * @param  bindPassword      The password to use to bind to the Directory
   *                           Server.
   * @param  saslProperties    A set of additional properties that may be needed
   *                           to process the SASL bind.
   * @param  requestControls   The set of controls to include the request to the
   *                           server.
   * @param  responseControls  A list to hold the set of controls included in
   *                           the response from the server.
   *
   * @return  A message providing additional information about the bind if
   *          appropriate, or <CODE>null</CODE> if there is no special
   *          information available.
   *
   * @throws  ClientException  If a client-side problem prevents the bind
   *                           attempt from succeeding.
   *
   * @throws  LDAPException  If the bind fails or some other server-side problem
   *                         occurs during processing.
   */
  public String doSASLPlain(ASN1OctetString bindDN,
                     ASN1OctetString bindPassword,
                     Map<String,List<String>> saslProperties,
                     ArrayList<LDAPControl> requestControls,
                     ArrayList<LDAPControl> responseControls)
         throws ClientException, LDAPException
  {
    String authID  = null;
    String authzID = null;


    // Evaluate the properties provided.  The authID is required, and authzID is
    // optional.
    if ((saslProperties == null) || saslProperties.isEmpty())
    {
      int    msgID   = MSGID_LDAPAUTH_NO_SASL_PROPERTIES;
      String message = getMessage(msgID, SASL_MECHANISM_PLAIN);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }

    Iterator<String> propertyNames = saslProperties.keySet().iterator();
    while (propertyNames.hasNext())
    {
      String name      = propertyNames.next();
      String lowerName = toLowerCase(name);

      if (lowerName.equals(SASL_PROPERTY_AUTHID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          authID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else if (lowerName.equals(SASL_PROPERTY_AUTHZID))
      {
        List<String> values = saslProperties.get(SASL_PROPERTY_AUTHZID);
        Iterator<String> iterator = values.iterator();
        if (iterator.hasNext())
        {
          authzID = iterator.next();

          if (iterator.hasNext())
          {
            int    msgID   = MSGID_LDAPAUTH_AUTHZID_SINGLE_VALUED;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR,
                                      msgID, message);
          }
        }
      }
      else
      {
        int    msgID   = MSGID_LDAPAUTH_INVALID_SASL_PROPERTY;
        String message = getMessage(msgID, name, SASL_MECHANISM_PLAIN);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                  message);
      }
    }


    // Make sure that at least the authID was provided.
    if ((authID == null) || (authID.length() == 0))
    {
      int    msgID   = MSGID_LDAPAUTH_SASL_AUTHID_REQUIRED;
      String message = getMessage(msgID, SASL_MECHANISM_PLAIN);
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_PARAM_ERROR, msgID,
                                message);
    }


    // See if the password was null.  If so, then interactively prompt it from
    // the user.
    if (bindPassword == null)
    {
      System.out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, authID));
      char[] pwChars = PasswordReader.readPassword();
      if (pwChars == null)
      {
        bindPassword = new ASN1OctetString();
      }
      else
      {
        bindPassword = new ASN1OctetString(getBytes(pwChars));
        Arrays.fill(pwChars, '\u0000');
      }
    }


    // Construct the bind request and send it to the server.
    StringBuilder credBuffer = new StringBuilder();
    if (authzID != null)
    {
      credBuffer.append(authzID);
    }
    credBuffer.append('\u0000');
    credBuffer.append(authID);
    credBuffer.append('\u0000');
    credBuffer.append(bindPassword.stringValue());

    ASN1OctetString saslCredentials =
         new ASN1OctetString(credBuffer.toString());
    BindRequestProtocolOp bindRequest =
         new BindRequestProtocolOp(bindDN, SASL_MECHANISM_PLAIN,
                                saslCredentials);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest,
                         requestControls);

    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_PLAIN,
                                  stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
      String message = getMessage(msgID, SASL_MECHANISM_PLAIN,
                                  stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // See if there are any controls in the response.  If so, then add them to
    // the response controls list.
    ArrayList<LDAPControl> respControls = responseMessage.getControls();
    if ((respControls != null) && (! respControls.isEmpty()))
    {
      responseControls.addAll(respControls);
    }


    // Look at the protocol op from the response.  If it's a bind response, then
    // continue.  If it's an extended response, then it could be a notice of
    // disconnection so check for that.  Otherwise, generate an error.
    switch (responseMessage.getProtocolOpType())
    {
      case OP_TYPE_BIND_RESPONSE:
        // We'll deal with this later.
        break;

      case OP_TYPE_EXTENDED_RESPONSE:
        ExtendedResponseProtocolOp extendedResponse =
             responseMessage.getExtendedResponseProtocolOp();
        String responseOID = extendedResponse.getOID();
        if ((responseOID != null) &&
            responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
        {
          int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
          String message = getMessage(msgID, extendedResponse.getResultCode(),
                                      extendedResponse.getErrorMessage());
          throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                  message);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
          String message = getMessage(msgID, String.valueOf(extendedResponse));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
        }

      default:
        int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
        String message =
             getMessage(msgID, String.valueOf(responseMessage.getProtocolOp()));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message);
    }


    BindResponseProtocolOp bindResponse =
         responseMessage.getBindResponseProtocolOp();
    int resultCode = bindResponse.getResultCode();
    if (resultCode == LDAPResultCode.SUCCESS)
    {
      // FIXME -- Need to look for things like password expiration warning,
      // reset notice, etc.
      return null;
    }

    // FIXME -- Add support for referrals.

    int    msgID   = MSGID_LDAPAUTH_SASL_BIND_FAILED;
    String message = getMessage(msgID, SASL_MECHANISM_PLAIN, resultCode,
                                LDAPResultCode.toString(resultCode),
                                bindResponse.getErrorMessage());
    throw new LDAPException(bindResponse.getResultCode(), msgID, message);
  }



  /**
   * Retrieves the set of properties that a client may provide when performing a
   * SASL PLAIN bind, mapped from the property names to their corresponding
   * descriptions.
   *
   * @return  The set of properties that a client may provide when performing a
   *          SASL PLAIN bind, mapped from the property names to their
   *          corresponding descriptions.
   */
  public static LinkedHashMap<String,String> getSASLPlainProperties()
  {
    LinkedHashMap<String,String> properties =
         new LinkedHashMap<String,String>(2);

    properties.put(SASL_PROPERTY_AUTHID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHID));
    properties.put(SASL_PROPERTY_AUTHZID,
                   getMessage(MSGID_LDAPAUTH_PROPERTY_DESCRIPTION_AUTHZID));

    return properties;
  }



  /**
   * Performs a privileged operation under JAAS so that the local authentication
   * information can be available for the SASL bind to the Directory Server.
   *
   * @return  A placeholder object in order to comply with the
   *          <CODE>PrivilegedExceptionAction</CODE> interface.
   *
   * @throws  ClientException  If a client-side problem occurs during the bind
   *                           processing.
   *
   * @throws  LDAPException  If a server-side problem occurs during the bind
   *                         processing.
   */
  public Object run()
         throws ClientException, LDAPException
  {
    if (saslMechanism == null)
    {
      int    msgID   = MSGID_LDAPAUTH_NONSASL_RUN_INVOCATION;
      String message = getMessage(msgID, getBacktrace());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_GSSAPI))
    {
      // Create the property map that will be used by the internal SASL handler.
      HashMap<String,String> saslProperties = new HashMap<String,String>();
      saslProperties.put(Sasl.QOP, gssapiQoP);
      saslProperties.put(Sasl.SERVER_AUTH, "true");


      // Create the SASL client that we will use to actually perform the
      // authentication.
      SaslClient saslClient;
      try
      {
        saslClient =
             Sasl.createSaslClient(new String[] { SASL_MECHANISM_GSSAPI },
                                   gssapiAuthzID, "ldap", hostName,
                                   saslProperties, this);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_SASL_CLIENT;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message, e);
      }


      // Get the SASL credentials to include in the initial bind request.
      ASN1OctetString saslCredentials;
      if (saslClient.hasInitialResponse())
      {
        try
        {
          byte[] credBytes = saslClient.evaluateChallenge(new byte[0]);
          saslCredentials = new ASN1OctetString(credBytes);
        }
        catch (Exception e)
        {
          int msgID = MSGID_LDAPAUTH_GSSAPI_CANNOT_CREATE_INITIAL_CHALLENGE;
          String message = getMessage(msgID, stackTraceToSingleLineString(e));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message, e);
        }
      }
      else
      {
        saslCredentials = null;
      }


      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(gssapiBindDN, SASL_MECHANISM_GSSAPI,
                                     saslCredentials);
      // FIXME -- Add controls here?
      LDAPMessage requestMessage =
           new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest);

      try
      {
        writer.writeElement(requestMessage.encode());
      }
      catch (IOException ioe)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
        String message = getMessage(msgID, SASL_MECHANISM_GSSAPI,
                                    stackTraceToSingleLineString(ioe));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                  message, ioe);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
        String message = getMessage(msgID, SASL_MECHANISM_GSSAPI,
                                    stackTraceToSingleLineString(e));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                  msgID, message, e);
      }


      // Read the response from the server.
      LDAPMessage responseMessage;
      try
      {
        ASN1Element responseElement = reader.readElement();
        if (responseElement == null)
        {
          int msgID = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
          String message = getMessage(msgID);
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                    msgID, message);
        }

        responseMessage =
             LDAPMessage.decode(responseElement.decodeAsSequence());
      }
      catch (IOException ioe)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
        String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                  message, ioe);
      }
      catch (ASN1Exception ae)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
        String message = getMessage(msgID, stackTraceToSingleLineString(ae));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                  msgID, message, ae);
      }
      catch (LDAPException le)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
        String message = getMessage(msgID, stackTraceToSingleLineString(le));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                  msgID, message, le);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                  message, e);
      }


      // FIXME -- Handle response controls.


      // Look at the protocol op from the response.  If it's a bind response,
      // then continue.  If it's an extended response, then it could be a notice
      // of disconnection so check for that.  Otherwise, generate an error.
      switch (responseMessage.getProtocolOpType())
      {
        case OP_TYPE_BIND_RESPONSE:
          // We'll deal with this later.
          break;

        case OP_TYPE_EXTENDED_RESPONSE:
          ExtendedResponseProtocolOp extendedResponse =
               responseMessage.getExtendedResponseProtocolOp();
          String responseOID = extendedResponse.getOID();
          if ((responseOID != null) &&
              responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
          {
            int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
            String message = getMessage(msgID, extendedResponse.getResultCode(),
                                        extendedResponse.getErrorMessage());
            throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                    message);
          }
          else
          {
            int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
            String message = getMessage(msgID,
                                        String.valueOf(extendedResponse));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                      msgID, message);
          }

        default:
          int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
          String message =
               getMessage(msgID,
                          String.valueOf(responseMessage.getProtocolOp()));
          throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                    msgID, message);
      }


      while (true)
      {
        BindResponseProtocolOp bindResponse =
             responseMessage.getBindResponseProtocolOp();
        int resultCode = bindResponse.getResultCode();
        if (resultCode == LDAPResultCode.SUCCESS)
        {
          // We should be done after this, but we still need to look for and
          // handle the server SASL credentials.
          ASN1OctetString serverSASLCredentials =
               bindResponse.getServerSASLCredentials();
          if (serverSASLCredentials != null)
          {
            try
            {
              saslClient.evaluateChallenge(serverSASLCredentials.value());
            }
            catch (Exception e)
            {
              int msgID = MSGID_LDAPAUTH_GSSAPI_CANNOT_VALIDATE_SERVER_CREDS;
              String message = getMessage(msgID,
                                          stackTraceToSingleLineString(e));
              throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                        msgID, message, e);
            }
          }


          // Just to be sure, check that the login really is complete.
          if (! saslClient.isComplete())
          {
            int    msgID   = MSGID_LDAPAUTH_GSSAPI_UNEXPECTED_SUCCESS_RESPONSE;
            String message = getMessage(msgID);
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                      msgID, message);
          }

          break;
        }
        else if (resultCode == LDAPResultCode.SASL_BIND_IN_PROGRESS)
        {
          // Read the response and process the server SASL credentials.
          ASN1OctetString serverSASLCredentials =
               bindResponse.getServerSASLCredentials();
          byte[] credBytes;
          try
          {
            if (serverSASLCredentials == null)
            {
              credBytes = saslClient.evaluateChallenge(new byte[0]);
            }
            else
            {
              credBytes =
                   saslClient.evaluateChallenge(serverSASLCredentials.value());
            }
          }
          catch (Exception e)
          {
            int msgID = MSGID_LDAPAUTH_GSSAPI_CANNOT_VALIDATE_SERVER_CREDS;
            String message = getMessage(msgID, stackTraceToSingleLineString(e));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                      msgID, message, e);
          }


          // Send the next bind in the sequence to the server.
          bindRequest =
               new BindRequestProtocolOp(gssapiBindDN, SASL_MECHANISM_GSSAPI,
                                         new ASN1OctetString(credBytes));
          // FIXME -- Add controls here?
          requestMessage =
               new LDAPMessage(nextMessageID.getAndIncrement(), bindRequest);


          try
          {
            writer.writeElement(requestMessage.encode());
          }
          catch (IOException ioe)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
            String message = getMessage(msgID, SASL_MECHANISM_GSSAPI,
                                        stackTraceToSingleLineString(ioe));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                      msgID, message, ioe);
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_SASL_BIND;
            String message = getMessage(msgID, SASL_MECHANISM_GSSAPI,
                                        stackTraceToSingleLineString(e));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                      msgID, message, e);
          }


          // Read the response from the server.
          try
          {
            ASN1Element responseElement = reader.readElement();
            if (responseElement == null)
            {
              int msgID =
                   MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
              String message = getMessage(msgID);
              throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                        msgID, message);
            }

            responseMessage =
                 LDAPMessage.decode(responseElement.decodeAsSequence());
          }
          catch (IOException ioe)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
            String message = getMessage(msgID,
                                        stackTraceToSingleLineString(ioe));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                      msgID, message, ioe);
          }
          catch (ASN1Exception ae)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
            String message = getMessage(msgID,
                                        stackTraceToSingleLineString(ae));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                      msgID, message, ae);
          }
          catch (LDAPException le)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
            String message = getMessage(msgID,
                                        stackTraceToSingleLineString(le));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                      msgID, message, le);
          }
          catch (Exception e)
          {
            int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_BIND_RESPONSE;
            String message = getMessage(msgID, stackTraceToSingleLineString(e));
            throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                      msgID, message, e);
          }


          // FIXME -- Handle response controls.


          // Look at the protocol op from the response.  If it's a bind
          // response, then continue.  If it's an extended response, then it
          // could be a notice of disconnection so check for that.  Otherwise,
          // generate an error.
          switch (responseMessage.getProtocolOpType())
          {
            case OP_TYPE_BIND_RESPONSE:
              // We'll deal with this later.
              break;

            case OP_TYPE_EXTENDED_RESPONSE:
              ExtendedResponseProtocolOp extendedResponse =
                   responseMessage.getExtendedResponseProtocolOp();
              String responseOID = extendedResponse.getOID();
              if ((responseOID != null) &&
                  responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
              {
                int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
                String message = getMessage(msgID,
                                            extendedResponse.getResultCode(),
                                            extendedResponse.getErrorMessage());
                throw new LDAPException(extendedResponse.getResultCode(), msgID,
                                        message);
              }
              else
              {
                int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_EXTENDED_RESPONSE;
                String message = getMessage(msgID,
                                            String.valueOf(extendedResponse));
                throw new ClientException(
                               LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                               message);
              }

            default:
              int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
              String message =
                   getMessage(msgID,
                              String.valueOf(responseMessage.getProtocolOp()));
              throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR,
                                        msgID, message);
          }
        }
        else
        {
          // This is an error.
          int    msgID   = MSGID_LDAPAUTH_GSSAPI_BIND_FAILED;
          String message = getMessage(msgID, resultCode,
                                      LDAPResultCode.toString(resultCode),
                                      bindResponse.getErrorMessage());
          throw new LDAPException(resultCode, msgID, message);
        }
      }
    }
    else
    {
      int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_RUN_INVOCATION;
      String message = getMessage(msgID, saslMechanism, getBacktrace());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message);
    }


    // FIXME -- Need to look for things like password expiration warning, reset
    // notice, etc.
    return null;
  }



  /**
   * Handles the authentication callbacks to provide information needed by the
   * JAAS login process.
   *
   * @param  callbacks  The callbacks needed to provide information for the JAAS
   *                    login process.
   *
   * @throws  UnsupportedCallbackException  If an unexpected callback is
   *                                        included in the provided set.
   */
  public void handle(Callback[] callbacks)
         throws UnsupportedCallbackException
  {
    if (saslMechanism ==  null)
    {
      int    msgID   = MSGID_LDAPAUTH_NONSASL_CALLBACK_INVOCATION;
      String message = getMessage(msgID, getBacktrace());
      throw new UnsupportedCallbackException(callbacks[0], message);
    }
    else if (saslMechanism.equals(SASL_MECHANISM_GSSAPI))
    {
      for (Callback cb : callbacks)
      {
        if (cb instanceof NameCallback)
        {
          ((NameCallback) cb).setName(gssapiAuthID);
        }
        else if (cb instanceof PasswordCallback)
        {
          if (gssapiAuthPW == null)
          {
            System.out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT,
                                        gssapiAuthID));
            gssapiAuthPW = PasswordReader.readPassword();
          }

          ((PasswordCallback) cb).setPassword(gssapiAuthPW);
        }
        else
        {
          int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_GSSAPI_CALLBACK;
          String message = getMessage(msgID, String.valueOf(cb));
          throw new UnsupportedCallbackException(cb, message);
        }
      }
    }
    else
    {
      int    msgID   = MSGID_LDAPAUTH_UNEXPECTED_CALLBACK_INVOCATION;
      String message = getMessage(msgID, saslMechanism, getBacktrace());
      throw new UnsupportedCallbackException(callbacks[0], message);
    }
  }



  /**
   * Uses the "Who Am I?" extended operation to request that the server provide
   * the client with the authorization identity for this connection.
   *
   * @return  An ASN.1 octet string containing the authorization identity, or
   *          <CODE>null</CODE> if the client is not authenticated or is
   *          authenticated anonymously.
   *
   * @throws  ClientException  If a client-side problem occurs during the
   *                           request processing.
   *
   * @throws  LDAPException  If a server-side problem occurs during the request
   *                         processing.
   */
  public ASN1OctetString requestAuthorizationIdentity()
         throws ClientException, LDAPException
  {
    // Construct the extended request and send it to the server.
    ExtendedRequestProtocolOp extendedRequest =
         new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST);
    LDAPMessage requestMessage =
         new LDAPMessage(nextMessageID.getAndIncrement(), extendedRequest);

    try
    {
      writer.writeElement(requestMessage.encode());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_WHOAMI_REQUEST;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_SEND_WHOAMI_REQUEST;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_ENCODING_ERROR,
                                msgID, message, e);
    }


    // Read the response from the server.
    LDAPMessage responseMessage;
    try
    {
      ASN1Element responseElement = reader.readElement();
      if (responseElement == null)
      {
        int    msgID   = MSGID_LDAPAUTH_CONNECTION_CLOSED_WITHOUT_BIND_RESPONSE;
        String message = getMessage(msgID);
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN,
                                  msgID, message);
      }

      responseMessage = LDAPMessage.decode(responseElement.decodeAsSequence());
    }
    catch (IOException ioe)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ioe));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msgID,
                                message, ioe);
    }
    catch (ASN1Exception ae)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(ae));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, ae);
    }
    catch (LDAPException le)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(le));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_DECODING_ERROR,
                                msgID, message, le);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDAPAUTH_CANNOT_READ_WHOAMI_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message, e);
    }


    // If the protocol op isn't an extended response, then that's a problem.
    if (responseMessage.getProtocolOpType() != OP_TYPE_EXTENDED_RESPONSE)
    {
      int msgID = MSGID_LDAPAUTH_UNEXPECTED_RESPONSE;
      String message =
           getMessage(msgID, String.valueOf(responseMessage.getProtocolOp()));
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_LOCAL_ERROR, msgID,
                                message);
    }


    // Get the extended response and see if it has the "notice of disconnection"
    // OID.  If so, then the server is closing the connection.
    ExtendedResponseProtocolOp extendedResponse =
         responseMessage.getExtendedResponseProtocolOp();
    String responseOID = extendedResponse.getOID();
    if ((responseOID != null) &&
        responseOID.equals(OID_NOTICE_OF_DISCONNECTION))
    {
      int    msgID   = MSGID_LDAPAUTH_SERVER_DISCONNECT;
      String message = getMessage(msgID, extendedResponse.getResultCode(),
                                  extendedResponse.getErrorMessage());
      throw new LDAPException(extendedResponse.getResultCode(), msgID, message);
    }


    // It isn't a notice of disconnection so it must be the "Who Am I?"
    // response and the value would be the authorization ID.  However, first
    // check that it was successful.  If it was not, then fail.
    int resultCode = extendedResponse.getResultCode();
    if (resultCode != LDAPResultCode.SUCCESS)
    {
      int msgID = MSGID_LDAPAUTH_WHOAMI_FAILED;
      String message = getMessage(msgID, resultCode,
                                  LDAPResultCode.toString(resultCode),
                                  extendedResponse.getErrorMessage());
      throw new LDAPException(resultCode, msgID, message);
    }


    // Get the authorization ID (if there is one) and return it to the caller.
    ASN1OctetString authzID = extendedResponse.getValue();
    if ((authzID == null) || (authzID.value() == null) ||
        (authzID.value().length == 0))
    {
      return null;
    }

    String valueString = authzID.stringValue();
    if ((valueString == null) || (valueString.length() == 0) ||
        valueString.equalsIgnoreCase("dn:"))
    {
      return null;
    }

    return authzID;
  }
}

