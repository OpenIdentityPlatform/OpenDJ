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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools;
import org.opends.messages.Message;

import java.io.PrintStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.controls.*;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.UnbindRequestProtocolOp;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ByteString;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.TraceSettings;
import static org.opends.messages.CoreMessages.
                   INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;



/**
 * This class provides a tool that can be used to issue search requests to the
 * Directory Server.
 */
public class LDAPConnection
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The hostname to connect to.
  private String hostName = null;

  // The port number on which the directory server is accepting requests.
  private int portNumber = 389;

  private LDAPConnectionOptions connectionOptions = null;
  private LDAPWriter ldapWriter;
  private LDAPReader ldapReader;
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
    Socket socket;
    Socket startTLSSocket = null;
    int resultCode;
    ArrayList<Control> requestControls = new ArrayList<Control> ();
    ArrayList<Control> responseControls = new ArrayList<Control> ();

    if(connectionOptions.isVerbose())
    {
      ConsoleDebugLogPublisher publisher = new ConsoleDebugLogPublisher(err);
      publisher.addTraceSettings(null,
          new TraceSettings(DebugLogLevel.VERBOSE));
      DebugLogger.addDebugLogPublisher(publisher);
    }

    if(connectionOptions.useStartTLS())
    {
      try
      {
        startTLSSocket = new Socket(hostName, portNumber);
        ldapWriter = new LDAPWriter(startTLSSocket);
        ldapReader = new LDAPReader(startTLSSocket);
      } catch(UnknownHostException uhe)
      {
        Message msg = INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get();
        throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, null,
                                          uhe);
      } catch(ConnectException ce)
      {
        Message msg = INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get();
        throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, null,
                                          ce);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        throw new LDAPConnectionException(Message.raw(ex.getMessage()), ex);
      }

      // Send the StartTLS extended request.
      ExtendedRequestProtocolOp extendedRequest =
           new ExtendedRequestProtocolOp(OID_START_TLS_REQUEST);

      LDAPMessage msg = new LDAPMessage(nextMessageID.getAndIncrement(),
                                        extendedRequest);
      try
      {
        ldapWriter.writeMessage(msg);

        // Read the response from the server.
        msg = ldapReader.readMessage();
      } catch (Exception ex1)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex1);
        }
        throw new LDAPConnectionException(Message.raw(ex1.getMessage()), ex1);
      }
      ExtendedResponseProtocolOp res = msg.getExtendedResponseProtocolOp();
      resultCode = res.getResultCode();
      if(resultCode != SUCCESS)
      {
        throw new LDAPConnectionException(res.getErrorMessage(),
                                          resultCode,
                                          res.getErrorMessage(),
                                          res.getMatchedDN(), null);
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
      ldapWriter = new LDAPWriter(socket);
      ldapReader = new LDAPReader(socket);
    } catch(UnknownHostException uhe)
    {
      Message msg = INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get();
      throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, null,
                                        uhe);
    } catch(ConnectException ce)
    {
      Message msg = INFO_RESULT_CLIENT_SIDE_CONNECT_ERROR.get();
      throw new LDAPConnectionException(msg, CLIENT_SIDE_CONNECT_ERROR, null,
                                        ce);
    } catch(Exception ex2)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex2);
      }
      throw new LDAPConnectionException(Message.raw(ex2.getMessage()), ex2);
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
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
         ldapReader, ldapWriter, hostName, nextMessageID);
    try
    {
      ByteString bindDNBytes;
      if(bindDN == null)
      {
        bindDNBytes = ByteString.empty();
      }
      else
      {
        bindDNBytes = ByteString.valueOf(bindDN);
      }

      ByteString bindPW;
      if (bindPassword == null)
      {
        bindPW =  ByteString.empty();
      }
      else
      {
        bindPW = ByteString.valueOf(bindPassword);
      }

      String result = null;
      if (connectionOptions.useSASLExternal())
      {
        result = handler.doSASLExternal(bindDNBytes,
                                        connectionOptions.getSASLProperties(),
                                        requestControls, responseControls);
      }
      else if (connectionOptions.getSASLMechanism() != null)
      {
            result = handler.doSASLBind(bindDNBytes, bindPW,
            connectionOptions.getSASLMechanism(),
            connectionOptions.getSASLProperties(),
            requestControls, responseControls);
      }
      else if(bindDN != null)
      {
              result = handler.doSimpleBind(versionNumber, bindDNBytes, bindPW,
              requestControls, responseControls);
      }
      if(result != null)
      {
        out.println(result);
      }

      for (Control c : responseControls)
      {
        if (c.getOID().equals(OID_AUTHZID_RESPONSE))
        {
          AuthorizationIdentityResponseControl control;
          if (c instanceof LDAPControl)
          {
            // We have to decode this control.
            control = AuthorizationIdentityResponseControl.DECODER.decode(c
                .isCritical(), ((LDAPControl) c).getValue());
          }
          else
          {
            // Control should already have been decoded.
            control = (AuthorizationIdentityResponseControl)c;
          }

          Message message =
              INFO_BIND_AUTHZID_RETURNED.get(
                  control.getAuthorizationID());
          out.println(message);
        }
        else if (c.getOID().equals(OID_NS_PASSWORD_EXPIRED))
        {

          Message message = INFO_BIND_PASSWORD_EXPIRED.get();
          out.println(message);
        }
        else if (c.getOID().equals(OID_NS_PASSWORD_EXPIRING))
        {
          PasswordExpiringControl control;
          if(c instanceof LDAPControl)
          {
            // We have to decode this control.
            control = PasswordExpiringControl.DECODER.decode(c.isCritical(),
                ((LDAPControl) c).getValue());
          }
          else
          {
            // Control should already have been decoded.
            control = (PasswordExpiringControl)c;
          }
          Message timeString =
               secondsToTimeString(control.getSecondsUntilExpiration());


          Message message = INFO_BIND_PASSWORD_EXPIRING.get(timeString);
          out.println(message);
        }
        else if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
        {
          PasswordPolicyResponseControl pwPolicyControl;
          if(c instanceof LDAPControl)
          {
            pwPolicyControl = PasswordPolicyResponseControl.DECODER.decode(c
                .isCritical(), ((LDAPControl) c).getValue());
          }
          else
          {
            pwPolicyControl = (PasswordPolicyResponseControl)c;
          }


          PasswordPolicyErrorType errorType = pwPolicyControl.getErrorType();
          if (errorType != null)
          {
            switch (errorType)
            {
              case PASSWORD_EXPIRED:

                Message message = INFO_BIND_PASSWORD_EXPIRED.get();
                out.println(message);
                break;
              case ACCOUNT_LOCKED:

                message = INFO_BIND_ACCOUNT_LOCKED.get();
                out.println(message);
                break;
              case CHANGE_AFTER_RESET:

                message = INFO_BIND_MUST_CHANGE_PASSWORD.get();
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
                Message timeString =
                     secondsToTimeString(pwPolicyControl.getWarningValue());


                Message message = INFO_BIND_PASSWORD_EXPIRING.get(timeString);
                out.println(message);
                break;
              case GRACE_LOGINS_REMAINING:

                message = INFO_BIND_GRACE_LOGINS_REMAINING.get(
                        pwPolicyControl.getWarningValue());
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
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new LDAPConnectionException(ce.getMessageObject(), ce.getExitCode(),
                                        null, ce);
    } catch (LDAPException le) {
        throw new LDAPConnectionException(le.getMessageObject(),
                le.getResultCode(),
                le.getErrorMessage(),
                le.getMatchedDN(),
                le.getCause());
    } catch (DirectoryException de)
    {
      throw new LDAPConnectionException(de.getMessageObject(), de
          .getResultCode().getIntValue(), null, de.getMatchedDN(), de
          .getCause());
    } catch(Exception ex)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      throw new LDAPConnectionException(
              Message.raw(ex.getLocalizedMessage()),ex);
    }

  }

  /**
   * Close the underlying ASN1 reader and writer, optionally sending an unbind
   * request before disconnecting.
   *
   * @param  nextMessageID  The message ID counter that should be used for
   *                        the unbind request, or {@code null} if the
   *                        connection should be closed without an unbind
   *                        request.
   */
  public void close(AtomicInteger nextMessageID)
  {
    if(ldapWriter != null)
    {
      if (nextMessageID != null)
      {
        try
        {
          LDAPMessage message = new LDAPMessage(nextMessageID.getAndIncrement(),
                                                new UnbindRequestProtocolOp());
          ldapWriter.writeMessage(message);
        } catch (Exception e) {}
      }

      ldapWriter.close();
    }
    if(ldapReader != null)
    {
      ldapReader.close();
    }
  }

  /**
   * Get the underlying LDAP writer.
   *
   * @return  The underlying LDAP writer.
   */
  public LDAPWriter getLDAPWriter()
  {
    return ldapWriter;
  }

  /**
   * Get the underlying LDAP reader.
   *
   * @return  The underlying LDAP reader.
   */
  public LDAPReader getLDAPReader()
  {
    return ldapReader;
  }

}

