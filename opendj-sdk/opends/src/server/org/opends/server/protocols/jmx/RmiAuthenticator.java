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
package org.opends.server.protocols.jmx;

import java.util.*;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;

import org.opends.server.core.BindOperation;
import org.opends.server.messages.CoreMessages;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DN;
import org.opends.server.types.AuthenticationInfo;

import static org.opends.server.loggers.Debug.*;

/**
 * A <code>RMIAuthenticator</code> manages authentication for the secure
 * RMI connectors. It receives authentication requests from clients as a
 * SASL/PLAIN challenge and relies on a SASL server plus the local LDAP
 * authentication accept or reject the user being connected.
 */
public class RmiAuthenticator implements JMXAuthenticator
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
        "org.opends.server.protocols.jmx.RmiAuthenticator";

    /**
     * The client authencation mode. <code>true</code> indicates that the
     * client will be authenticated by its certificate (SSL protocol).
     * <code>true</code> indicate , that we have to perform an lDAP
     * authentication
     */
    private boolean needClientCertificate = false;

    /**
     * Indicate if the we are in the finalized phase.
     *
     * @see JmxConnectionHandler
     */
    private boolean finalizedPhase = false;

  /**
   * The JMX Client connection to be used to perform the bind (auth)
   * call.
   */
  private JmxConnectionHandler jmxConnectionHandler;

  /**
   * Constructs a <code>RmiAuthenticator</code>.
   *
   * @param jmxConnectionHandler
   *        The jmxConnectionHandler associated to this RmiAuthenticator
   */
  public RmiAuthenticator(JmxConnectionHandler jmxConnectionHandler)
  {
    assert debugConstructor(CLASS_NAME);

    this.jmxConnectionHandler = jmxConnectionHandler;
  }

  /**
   * Set that we are in the finalized phase.
   *
   * @param finalizedPhase Set to true, it indicates that we are in
   * the finalized phase that that we other connection should be accepted.
   *
   * @see JmxConnectionHandler
   */
  public synchronized void setFinalizedPhase(boolean finalizedPhase)
  {
    this.finalizedPhase = finalizedPhase;
  }

  /**
   * Authenticates a RMI client. The credentials received are composed of
   * a SASL/PLAIN authentication id and a password.
   *
   * @param credentials
   *            the SASL/PLAIN credentials to validate
   * @return a <code>Subject</code> holding the principal(s)
   *         authenticated
   */
  public Subject authenticate(Object credentials)
  {
    assert debugEnter(CLASS_NAME, "RmiAuthenticator");

    //
    // If we are in the finalized phase, we should not accept
    // new connection
    if (finalizedPhase)
    {
      SecurityException se = new SecurityException();
      throw se;
    }

    //
    // Credentials are null !!!
    if (credentials == null)
    {
      SecurityException se = new SecurityException();
      throw se;
    }
    Object c[] = (Object[]) credentials;
    String authcID = (String) c[0];
    String password = (String) c[1];

    //
    // The authcID is used at forwarder level to identify the calling
    // client
    if (authcID == null)
    {
      assert debugMessage(
          DebugLogCategory.CONNECTION_HANDLING,
          DebugLogSeverity.VERBOSE,
          CLASS_NAME,
          "RmiAuthenticator",
          "User name is Null ");
      SecurityException se = new SecurityException();
      throw se;
    }
    if (password == null)
    {
      assert debugMessage(
          DebugLogCategory.CONNECTION_HANDLING,
          DebugLogSeverity.VERBOSE,
          CLASS_NAME,
          "RmiAuthenticator",
          "User password is Null ");

      SecurityException se = new SecurityException();
      throw se;
    }

    assert debugMessage(
        DebugLogCategory.CONNECTION_HANDLING,
        DebugLogSeverity.VERBOSE,
        CLASS_NAME,
        "RmiAuthenticator",
        "UserName  =" + authcID);

    //
    // Declare the client connection
    JmxClientConnection jmxClientConnection;

    //
    // Try to see if we have an Ldap Authentication
    // Which should be the case in the current implementation
    try
    {
      jmxClientConnection = bind(authcID, password);
    }
    catch (Exception e)
    {
      assert debugException(
          CLASS_NAME, "RmiAuthenticator", e);
      SecurityException se = new SecurityException();
      se.initCause(e);
      throw se;
    }

    //
    // If we've gotten here, then the authentication was
    // successful.

    // initialize a subject
    Subject s = new Subject();

    //
    // Add the Principal. The current implementation doesn't use it

    s.getPrincipals().add(new OpendsJmxPrincipal(authcID));

    // add the connection client object
    // this connection client is used at forwarder level to identify the
    // calling client
    s.getPrivateCredentials().add(new Credential(jmxClientConnection));

    return s;

  }

  /**
   * Process bind operation.
   *
   * @param authcID
   *            The LDAP user.
   * @param password
   *            The Ldap password associated to the user.
   */
  private JmxClientConnection bind(String authcID, String password)
  {
    ArrayList<Control> requestControls = new ArrayList<Control>();

    //
    // We have a new client connection
    DN bindDN;
    try
    {
      bindDN = DN.decode(authcID);
    }
    catch (Exception e)
    {
      LDAPException ldapEx = new LDAPException(
          LDAPResultCode.INVALID_CREDENTIALS,
          CoreMessages.MSGID_RESULT_INVALID_CREDENTIALS, null);
      SecurityException se = new SecurityException();
      se.initCause(ldapEx);
      throw se;
    }
    ASN1OctetString bindPW;
    if (password == null)
    {
      bindPW = null;
    }
    else
    {
      bindPW = new ASN1OctetString(password);
    }

    AuthenticationInfo authInfo = new AuthenticationInfo(bindDN, bindPW, false);
    JmxClientConnection jmxClientConnection = new JmxClientConnection(
        jmxConnectionHandler, authInfo);

    BindOperation bindOp = new BindOperation(jmxClientConnection,
        jmxClientConnection.nextOperationID(), jmxClientConnection
            .nextMessageID(), requestControls, new ASN1OctetString(authcID),
        bindPW);

    bindOp.run();
    if (bindOp.getResultCode() == ResultCode.SUCCESS)
    {
      assert debugMessage(
          DebugLogCategory.CONNECTION_HANDLING,
          DebugLogSeverity.VERBOSE,
          CLASS_NAME,
          "bind",
          "User is authenticated");

      return jmxClientConnection;
    }
    else
    {
      //
      // Set the initcause.
      LDAPException ldapEx = new LDAPException(
          LDAPResultCode.INVALID_CREDENTIALS,
          CoreMessages.MSGID_RESULT_INVALID_CREDENTIALS, null);
      SecurityException se = new SecurityException("return code: "
          + bindOp.getResultCode());
      se.initCause(ldapEx);
      throw se;
    }
  }
}
