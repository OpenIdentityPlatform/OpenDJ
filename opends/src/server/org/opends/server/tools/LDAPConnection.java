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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import java.io.PrintStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.controls.PasswordExpiringControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.CoreMessages.
                   MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;



/**
 * This class provides a tool that can be used to issue search requests to the
 * Directory Server.
 */
public class LDAPConnection
{

  // The hostname to connect to.
  private String hostName = null;

  // The port number on which the directory server is accepting requests.
  private int portNumber = 389;

  private LDAPConnectionOptions connectionOptions = null;
  private ASN1Writer asn1Writer;
  private ASN1Reader asn1Reader;
  private int versionNumber = 3;

  private PrintStream out;
  private PrintStream err;

  /**
   * Constructor for the LDAPConnection object.
   *
   * @param   host    The hostname to send the request to.
   * @param   port    The port number on which the directory server is accepting
   *                  requests.
   * @param  options  The set of options for this connection.
   */
  public LDAPConnection(String host, int port, LDAPConnectionOptions options)
  {
    this(host, port, options, System.out, System.err);
  }

  /**
   * Constructor for the LDAPConnection object.
   *
   * @param   host    The hostname to send the request to.
   * @param   port    The port number on which the directory server is accepting
   *                  requests.
   * @param  options  The set of options for this connection.
   * @param  out      The print stream to use for standard output.
   * @param  err      The print stream to use for standard error.
   */
  public LDAPConnection(String host, int port, LDAPConnectionOptions options,
                        PrintStream out, PrintStream err)
  {
    this.hostName = host;
    this.portNumber = port;
    this.connectionOptions = options;
    this.versionNumber = options.getVersionNumber();
    this.out = out;
    this.err = err;
  }

  /**
   * Connects to the directory server instance running on specified hostname
   * and port number.
   *
   * @param  bindDN        The DN to bind with.
   * @param  bindPassword  The password to bind with.
   *
   * @throws  LDAPConnectionException  If a problem occurs while attempting to
   *                                   establish the connection to the server.
   */
  public void connectToHost(String bindDN, String bindPassword)
         throws LDAPConnectionException
  {
    connectToHost(bindDN, bindPassword, new AtomicInteger(1));
  }

  /**
   * Connects to the directory server instance running on specified hostname
   * and port number.
   *
   * @param  bindDN         The DN to bind with.
   * @param  bindPassword   The password to bind with.
   * @param  nextMessageID  The message ID counter that should be used for
   *                        operations performed while establishing the
   *                        connection.
   *
   * @throws  LDAPConnectionException  If a problem occurs while attempting to
   *                                   establish the connection to the server.
   */
  public void connectToHost(String bindDN, String bindPassword,
                            AtomicInteger nextMessageID)
                            throws LDAPConnectionException
  {
    Socket socket = null;
    Socket startTLSSocket = null;
    int resultCode = -1;
    ArrayList<LDAPControl> requestControls = new ArrayList<LDAPControl> ();
    ArrayList<LDAPControl> responseControls = new ArrayList<LDAPControl> ();

    if(connectionOptions.useStartTLS())
    {
      try
      {
        startTLSSocket = new Socket(hostName, portNumber);
        asn1Writer = new ASN1Writer(startTLSSocket);
        asn1Reader = new ASN1Reader(startTLSSocket);
      } catch(UnknownHostException uhe)
      {
        int msgID = MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR;
        String msg = getMessage(msgID);
        throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, uhe);
      } catch(ConnectException ce)
      {
        int msgID = MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR;
        String msg = getMessage(msgID);
        throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, ce);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, ex);
        }
        throw new LDAPConnectionException(ex.getMessage(), ex);
      }

      // Send the StartTLS extended request.
      ExtendedRequestProtocolOp extendedRequest =
           new ExtendedRequestProtocolOp(OID_START_TLS_REQUEST);

      LDAPMessage msg = new LDAPMessage(nextMessageID.getAndIncrement(),
                                        extendedRequest);
      try
      {
        asn1Writer.writeElement(msg.encode());

        // Read the response from the server.
        msg = LDAPMessage.decode(asn1Reader.readElement().decodeAsSequence());
      } catch (Exception ex1)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, ex1);
        }
        throw new LDAPConnectionException(ex1.getMessage(), ex1);
      }
      ExtendedResponseProtocolOp res = msg.getExtendedResponseProtocolOp();
      resultCode = res.getResultCode();
      if(resultCode != SUCCESS)
      {
        String message = res.getErrorMessage();
        if(message == null)
        {
          message = "Response code:" + resultCode;
        }
        throw new LDAPConnectionException(message);
      }
    }
    SSLConnectionFactory sslConnectionFactory =
                         connectionOptions.getSSLConnectionFactory();
    try
    {
      if(sslConnectionFactory != null)
      {
        if(connectionOptions.useStartTLS())
        {
          // Use existing socket.
          socket = sslConnectionFactory.createSocket(startTLSSocket, hostName,
            portNumber, true);
        } else
        {
          socket = sslConnectionFactory.createSocket(hostName, portNumber);
        }
      } else
      {
        socket = new Socket(hostName, portNumber);
      }
      asn1Writer = new ASN1Writer(socket);
      asn1Reader = new ASN1Reader(socket);
    } catch(UnknownHostException uhe)
    {
      int msgID = MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR;
      String msg = getMessage(msgID);
      throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, uhe);
    } catch(ConnectException ce)
    {
      int msgID = MSGID_RESULT_CLIENT_SIDE_CONNECT_ERROR;
      String msg = getMessage(msgID);
      throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, ce);
    } catch(Exception ex2)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ex2);
      }
      throw new LDAPConnectionException(ex2.getMessage(), ex2);
    }

    // We need this so that we don't run out of addresses when the tool
    // commands are called A LOT, as in the unit tests.
    try
    {
      socket.setSoLinger(true, 1);
      socket.setReuseAddress(true);
    } catch(IOException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      // It doesn't matter too much if this throws, so ignore it.
    }

    if (connectionOptions.getReportAuthzID())
    {
      requestControls.add(new LDAPControl(OID_AUTHZID_REQUEST));
    }

    if (connectionOptions.usePasswordPolicyControl())
    {
      requestControls.add(new LDAPControl(OID_PASSWORD_POLICY_CONTROL));
    }

    LDAPAuthenticationHandler handler = new LDAPAuthenticationHandler(
            asn1Reader, asn1Writer, hostName, nextMessageID);
    try
    {
      ASN1OctetString bindPW;
      if (bindPassword == null)
      {
        bindPW = null;
      }
      else
      {
        bindPW = new ASN1OctetString(bindPassword);
      }

      String result = null;
      if (connectionOptions.useSASLExternal())
      {
        result = handler.doSASLExternal(new ASN1OctetString(bindDN),
                                        connectionOptions.getSASLProperties(),
                                        requestControls, responseControls);
      }
      else if (connectionOptions.getSASLMechanism() != null)
      {
            result = handler.doSASLBind(new ASN1OctetString(bindDN), bindPW,
            connectionOptions.getSASLMechanism(),
            connectionOptions.getSASLProperties(),
            requestControls, responseControls);
      }
      else if(bindDN != null)
      {
              result = handler.doSimpleBind(versionNumber,
                  new ASN1OctetString(bindDN), bindPW,
              requestControls, responseControls);
      }
      if(result != null)
      {
        out.println(result);
      }

      for (LDAPControl c : responseControls)
      {
        if (c.getOID().equals(OID_AUTHZID_RESPONSE))
        {
          ASN1OctetString controlValue = c.getValue();
          if (controlValue != null)
          {
            int    msgID   = MSGID_BIND_AUTHZID_RETURNED;
            String message = getMessage(msgID, controlValue.stringValue());
            out.println(message);
          }
        }
        else if (c.getOID().equals(OID_NS_PASSWORD_EXPIRED))
        {
          int    msgID   = MSGID_BIND_PASSWORD_EXPIRED;
          String message = getMessage(msgID);
          out.println(message);
        }
        else if (c.getOID().equals(OID_NS_PASSWORD_EXPIRING))
        {
          PasswordExpiringControl expiringControl =
               PasswordExpiringControl.decodeControl(new Control(c.getOID(),
                                                                 c.isCritical(),
                                                                 c.getValue()));
          String timeString =
               secondsToTimeString(expiringControl.getSecondsUntilExpiration());

          int    msgID   = MSGID_BIND_PASSWORD_EXPIRING;
          String message = getMessage(msgID, timeString);
          out.println(message);
        }
        else if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwPolicyControl =
               PasswordPolicyResponseControl.decodeControl(new Control(
                    c.getOID(), c.isCritical(), c.getValue()));

          PasswordPolicyErrorType errorType = pwPolicyControl.getErrorType();
          if (errorType != null)
          {
            switch (errorType)
            {
              case PASSWORD_EXPIRED:
                int    msgID   = MSGID_BIND_PASSWORD_EXPIRED;
                String message = getMessage(msgID);
                out.println(message);
                break;
              case ACCOUNT_LOCKED:
                msgID   = MSGID_BIND_ACCOUNT_LOCKED;
                message = getMessage(msgID);
                out.println(message);
                break;
              case CHANGE_AFTER_RESET:
                msgID   = MSGID_BIND_MUST_CHANGE_PASSWORD;
                message = getMessage(msgID);
                out.println(message);
                break;
            }
          }

          PasswordPolicyWarningType warningType =
               pwPolicyControl.getWarningType();
          if (warningType != null)
          {
            switch (warningType)
            {
              case TIME_BEFORE_EXPIRATION:
                String timeString =
                     secondsToTimeString(pwPolicyControl.getWarningValue());

                int    msgID   = MSGID_BIND_PASSWORD_EXPIRING;
                String message = getMessage(msgID, timeString);
                out.println(message);
                break;
              case GRACE_LOGINS_REMAINING:
                msgID   = MSGID_BIND_GRACE_LOGINS_REMAINING;
                message = getMessage(msgID, pwPolicyControl.getWarningValue());
                out.println(message);
                break;
            }
          }
        }
      }
    } catch(ClientException ce)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new LDAPConnectionException(ce.getMessage(), ce.getExitCode(), ce);
    } catch(Exception ex)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, ex);
      }
      throw new LDAPConnectionException(ex.getMessage(), ex);
    }

  }

  /**
   * Close the underlying ASN1 reader and writer.
   *
   */
  public void close()
  {
    if(asn1Writer != null)
    {
      asn1Writer.close();
    }
    if(asn1Reader != null)
    {
      asn1Reader.close();
    }
  }

  /**
   * Get the underlying ASN1 writer.
   *
   * @return  The underlying ASN.1 writer.
   */
  public ASN1Writer getASN1Writer()
  {
    return asn1Writer;
  }

  /**
   * Get the underlying ASN1 reader.
   *
   * @return  The underlying ASN.1 reader.
   */
  public ASN1Reader getASN1Reader()
  {
    return asn1Reader;
  }


}

