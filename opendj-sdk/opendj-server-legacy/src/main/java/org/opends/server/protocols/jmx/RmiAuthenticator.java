/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.jmx;

import static org.opends.messages.ProtocolMessages.*;

import java.util.ArrayList;

import javax.management.remote.JMXAuthenticator;
import javax.security.auth.Subject;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.messages.CoreMessages;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.BindOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Privilege;

/**
 * A <code>RMIAuthenticator</code> manages authentication for the secure
 * RMI connectors. It receives authentication requests from clients as a
 * SASL/PLAIN challenge and relies on a SASL server plus the local LDAP
 * authentication accept or reject the user being connected.
 */
public class RmiAuthenticator implements JMXAuthenticator
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Indicate if the we are in the finalized phase.
     *
     * @see JmxConnectionHandler
     */
    private boolean finalizedPhase;

  /** The JMX Client connection to be used to perform the bind (auth) call. */
  private JmxConnectionHandler jmxConnectionHandler;

  /**
   * Constructs a <code>RmiAuthenticator</code>.
   *
   * @param jmxConnectionHandler
   *        The jmxConnectionHandler associated to this RmiAuthenticator
   */
  public RmiAuthenticator(JmxConnectionHandler jmxConnectionHandler)
  {
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
  @Override
  public Subject authenticate(Object credentials)
  {
    // If we are in the finalized phase, we should not accept new connection
    if (finalizedPhase
        || credentials == null)
    {
      throw new SecurityException();
    }
    Object c[] = (Object[]) credentials;
    String authcID = (String) c[0];
    String password = (String) c[1];

    // The authcID is used at forwarder level to identify the calling client
    if (authcID == null)
    {
      logger.trace("User name is Null");
      throw new SecurityException();
    }
    if (password == null)
    {
      logger.trace("User password is Null ");
      throw new SecurityException();
    }

    logger.trace("UserName = %s", authcID);

    // Try to see if we have an Ldap Authentication
    // Which should be the case in the current implementation
    JmxClientConnection jmxClientConnection;
    try
    {
      jmxClientConnection = bind(authcID, password);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      SecurityException se = new SecurityException(e.getMessage());
      throw se;
    }

    // If we've gotten here, then the authentication was successful.
    // We'll take the connection so invoke the post-connect plugins.
    PluginConfigManager pluginManager = DirectoryServer.getPluginConfigManager();
    PluginResult.PostConnect pluginResult = pluginManager.invokePostConnectPlugins(jmxClientConnection);
    if (!pluginResult.continueProcessing())
    {
      jmxClientConnection.disconnect(pluginResult.getDisconnectReason(),
          pluginResult.sendDisconnectNotification(),
          pluginResult.getErrorMessage());

      if (logger.isTraceEnabled())
      {
        logger.trace("Disconnect result from post connect plugins: " +
            "%s: %s ", pluginResult.getDisconnectReason(),
            pluginResult.getErrorMessage());
      }

      throw new SecurityException();
    }

    // initialize a subject
    Subject s = new Subject();

    // Add the Principal. The current implementation doesn't use it
    s.getPrincipals().add(new OpendsJmxPrincipal(authcID));

    // add the connection client object
    // this connection client is used at forwarder level to identify the calling client
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
    try
    {
      DN.valueOf(authcID);
    }
    catch (Exception e)
    {
      LDAPException ldapEx = new LDAPException(
          LDAPResultCode.INVALID_CREDENTIALS,
          CoreMessages.INFO_RESULT_INVALID_CREDENTIALS.get());
      throw new SecurityException(ldapEx);
    }

    ArrayList<Control> requestControls = new ArrayList<>();
    ByteString bindPW = password != null ? ByteString.valueOf(password) : null;

    AuthenticationInfo authInfo = new AuthenticationInfo();
    JmxClientConnection jmxClientConnection = new JmxClientConnection(
        jmxConnectionHandler, authInfo);

    BindOperationBasis bindOp = new BindOperationBasis(jmxClientConnection,
        jmxClientConnection.nextOperationID(),
        jmxClientConnection.nextMessageID(), requestControls,
        jmxConnectionHandler.getRMIConnector().getProtocolVersion(),
        ByteString.valueOf(authcID), bindPW);

    bindOp.run();
    if (bindOp.getResultCode() == ResultCode.SUCCESS)
    {
      logger.trace("User is authenticated");

      authInfo = bindOp.getAuthenticationInfo();
      jmxClientConnection.setAuthenticationInfo(authInfo);

      // Check JMX_READ privilege.
      if (! jmxClientConnection.hasPrivilege(Privilege.JMX_READ, null))
      {
        LocalizableMessage message = ERR_JMX_INSUFFICIENT_PRIVILEGES.get();

        jmxClientConnection.disconnect(DisconnectReason.CONNECTION_REJECTED,
            false, message);

        throw new SecurityException(message.toString());
      }
      return jmxClientConnection;
    }
    else
    {
      // Set the initcause.
      LDAPException ldapEx = new LDAPException(
          LDAPResultCode.INVALID_CREDENTIALS,
          CoreMessages.INFO_RESULT_INVALID_CREDENTIALS.get());
      SecurityException se = new SecurityException("return code: " + bindOp.getResultCode());
      se.initCause(ldapEx);
      throw se;
    }
  }
}
