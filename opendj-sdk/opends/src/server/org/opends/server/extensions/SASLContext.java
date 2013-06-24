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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.*;

import org.ietf.jgss.GSSException;
import org.opends.messages.Message;
import org.opends.server.api.AuthenticationPolicyState;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.*;



/**
 * This class defines the SASL context needed to process GSSAPI and DIGEST-MD5
 * bind requests from clients.
 */
public class SASLContext implements CallbackHandler,
    PrivilegedExceptionAction<Boolean>
{

  // The tracer object for the debug logger.
  private static final DebugTracer TRACER = getTracer();



  /**
   * Instantiate a GSSAPI/DIGEST-MD5 SASL context using the specified
   * parameters.
   *
   * @param saslProps
   *          The properties to use in creating the SASL server.
   * @param serverFQDN
   *          The fully qualified domain name to use in creating the SASL
   *          server.
   * @param mechanism
   *          The SASL mechanism name.
   * @param identityMapper
   *          The identity mapper to use in mapping identities.
   * @return A fully instantiated SASL context to use in processing a SASL bind
   *         for the GSSAPI or DIGEST-MD5 mechanisms.
   * @throws SaslException
   *           If the SASL server can not be instantiated.
   */
  public static SASLContext createSASLContext(
      final HashMap<String, String> saslProps, final String serverFQDN,
      final String mechanism, final IdentityMapper<?> identityMapper)
      throws SaslException
  {
    return (new SASLContext(saslProps, serverFQDN, mechanism, identityMapper));
  }



  // The SASL server to use in the authentication.
  private SaslServer saslServer = null;

  // The identity mapper to use when mapping identities.
  private final IdentityMapper<?> identityMapper;

  // The property set to use when creating the SASL server.
  private final HashMap<String, String> saslProps;

  // The fully qualified domain name to use when creating the SASL server.
  private final String serverFQDN;

  // The SASL mechanism name.
  private final String mechanism;

  // The authorization entry used in the authentication.
  private Entry authEntry = null;

  // The authorization entry used in the authentication.
  private Entry authzEntry = null;

  // The user name used in the authentication taken from the name callback.
  private String userName;

  // Error message used by callbacks.
  private Message cbMsg;

  // Error code used by callbacks.
  private ResultCode cbResultCode;

  // The current bind operation used by the callbacks.
  private BindOperation bindOp;

  // Used to check if negotiated QOP is confidentiality or integrity.
  private static final String confidentiality = "auth-conf";
  private static final String integrity = "auth-int";



  /**
   * Create a SASL context using the specified parameters. A SASL server will be
   * instantiated only for the DIGEST-MD5 mechanism. The GSSAPI mechanism must
   * instantiate the SASL server as the login context in a separate step.
   *
   * @param saslProps
   *          The properties to use in creating the SASL server.
   * @param serverFQDN
   *          The fully qualified domain name to use in creating the SASL
   *          server.
   * @param mechanism
   *          The SASL mechanism name.
   * @param identityMapper
   *          The identity mapper to use in mapping identities.
   * @throws SaslException
   *           If the SASL server can not be instantiated.
   */
  private SASLContext(final HashMap<String, String> saslProps,
      final String serverFQDN, final String mechanism,
      final IdentityMapper<?> identityMapper) throws SaslException
  {
    this.identityMapper = identityMapper;
    this.mechanism = mechanism;
    this.saslProps = saslProps;
    this.serverFQDN = serverFQDN;

    if (mechanism.equals(SASL_MECHANISM_DIGEST_MD5))
    {
      initSASLServer();
    }
  }



  /**
   * Process the specified callback array.
   *
   * @param callbacks
   *          An array of callbacks that need processing.
   * @throws UnsupportedCallbackException
   *           If a callback is not supported.
   */
  @Override
  public void handle(final Callback[] callbacks)
      throws UnsupportedCallbackException
  {
    for (final Callback callback : callbacks)
    {
      if (callback instanceof NameCallback)
      {
        nameCallback((NameCallback) callback);
      }
      else if (callback instanceof PasswordCallback)
      {
        passwordCallback((PasswordCallback) callback);
      }
      else if (callback instanceof RealmCallback)
      {
        realmCallback((RealmCallback) callback);
      }
      else if (callback instanceof AuthorizeCallback)
      {
        authorizeCallback((AuthorizeCallback) callback);
      }
      else
      {
        final Message message = INFO_SASL_UNSUPPORTED_CALLBACK.get(mechanism,
            String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message.toString());
      }
    }
  }



  /**
   * The method performs all GSSAPI processing. It is run as the context of the
   * login context performed by the GSSAPI mechanism handler. See comments for
   * processing overview.
   *
   * @return {@code true} if the authentication processing was successful.
   */
  @Override
  public Boolean run()
  {
    final ClientConnection clientConn = bindOp.getClientConnection();

    // If the SASL server is null then this is the first handshake and the
    // server needs to be initialized before any processing can be performed.
    // If the SASL server cannot be created then all processing is abandoned
    // and INVALID_CREDENTIALS is returned to the client.
    if (saslServer == null)
    {
      try
      {
        initSASLServer();
      }
      catch (final SaslException ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        final GSSException gex = (GSSException) ex.getCause();

        final Message msg;
        if (gex != null)
        {
          msg = ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_GSSAPI,
              GSSAPISASLMechanismHandler.getGSSExceptionMessage(gex));
        }
        else
        {
          msg = ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_GSSAPI,
              getExceptionMessage(ex));
        }

        clientConn.setSASLAuthStateInfo(null);
        bindOp.setAuthFailureReason(msg);
        bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
        return false;
      }
    }

    final ByteString clientCredentials = bindOp.getSASLCredentials();
    clientConn.setSASLAuthStateInfo(null);
    try
    {
      final ByteString responseAuthStr = evaluateResponse(clientCredentials);

      // If the bind has not been completed,then
      // more handshake is needed and SASL_BIND_IN_PROGRESS is returned back
      // to the client.
      if (isBindComplete())
      {
        bindOp.setResultCode(ResultCode.SUCCESS);
        bindOp.setSASLAuthUserEntry(authEntry);
        final AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
            authzEntry, mechanism, clientCredentials,
            DirectoryServer.isRootDN(authEntry.getDN()));
        bindOp.setAuthenticationInfo(authInfo);

        // If confidentiality/integrity has been negotiated then
        // create a SASL security provider and save it in the client
        // connection. If confidentiality/integrity has not been
        // negotiated, dispose of the SASL server.
        if (isConfidentialIntegrity())
        {
          final SASLByteChannel saslByteChannel = SASLByteChannel
              .getSASLByteChannel(clientConn, mechanism, this);
          final LDAPClientConnection ldapConn =
              (LDAPClientConnection) clientConn;
          ldapConn.setSASLPendingProvider(saslByteChannel);
        }
        else
        {
          dispose();
          clientConn.setSASLAuthStateInfo(null);
        }
      }
      else
      {
        bindOp.setServerSASLCredentials(responseAuthStr);
        clientConn.setSASLAuthStateInfo(this);
        bindOp.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
      }
    }
    catch (final SaslException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final Message msg = ERR_SASL_PROTOCOL_ERROR.get(mechanism,
          getExceptionMessage(e));
      handleError(msg);
      return false;
    }

    return true;
  }



  /**
   * Dispose of the SASL server instance.
   */
  void dispose()
  {
    try
    {
      saslServer.dispose();
    }
    catch (final SaslException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Evaluate the final stage of a DIGEST-MD5 SASL bind using the specified bind
   * operation.
   *
   * @param bindOp
   *          The bind operation to use in processing.
   */
  void evaluateFinalStage(final BindOperation bindOp)
  {
    this.bindOp = bindOp;
    final ByteString clientCredentials = bindOp.getSASLCredentials();

    if ((clientCredentials == null) || (clientCredentials.length() == 0))
    {
      final Message msg = ERR_SASL_NO_CREDENTIALS.get(mechanism, mechanism);
      handleError(msg);
      return;
    }

    final ClientConnection clientConn = bindOp.getClientConnection();
    clientConn.setSASLAuthStateInfo(null);

    try
    {
      final ByteString responseAuthStr = evaluateResponse(clientCredentials);
      bindOp.setResultCode(ResultCode.SUCCESS);
      bindOp.setServerSASLCredentials(responseAuthStr);
      bindOp.setSASLAuthUserEntry(authEntry);
      final AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
          authzEntry, mechanism, clientCredentials,
          DirectoryServer.isRootDN(authEntry.getDN()));
      bindOp.setAuthenticationInfo(authInfo);

      // If confidentiality/integrity has been negotiated, then create a
      // SASL security provider and save it in the client connection for
      // use in later processing.
      if (isConfidentialIntegrity())
      {
        final SASLByteChannel saslByteChannel = SASLByteChannel
            .getSASLByteChannel(clientConn, mechanism, this);
        final LDAPClientConnection ldapConn = (LDAPClientConnection) clientConn;
        ldapConn.setSASLPendingProvider(saslByteChannel);
      }
      else
      {
        dispose();
        clientConn.setSASLAuthStateInfo(null);
      }
    }
    catch (final SaslException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final Message msg = ERR_SASL_PROTOCOL_ERROR.get(mechanism,
          getExceptionMessage(e));
      handleError(msg);
    }
  }



  /**
   * Process the initial stage of a DIGEST-MD5 SASL bind using the specified
   * bind operation.
   *
   * @param bindOp
   *          The bind operation to use in processing.
   */
  void evaluateInitialStage(final BindOperation bindOp)
  {
    this.bindOp = bindOp;
    final ClientConnection clientConn = bindOp.getClientConnection();

    try
    {
      final ByteString challenge = evaluateResponse(ByteString.empty());
      bindOp.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
      bindOp.setServerSASLCredentials(challenge);
      clientConn.setSASLAuthStateInfo(this);
    }
    catch (final SaslException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      final Message msg = ERR_SASL_PROTOCOL_ERROR.get(mechanism,
          getExceptionMessage(e));
      handleError(msg);
    }
  }



  /**
   * Returns the negotiated maximum size of protected data which can be received
   * from the client.
   *
   * @return The negotiated maximum size of protected data which can be received
   *         from the client.
   */
  int getMaxReceiveBufferSize()
  {
    String str = (String) saslServer.getNegotiatedProperty(Sasl.MAX_BUFFER);
    if (str != null)
    {
      try
      {
        return Integer.parseInt(str);
      }
      catch (NumberFormatException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Default buffer size if not specified according to Java SASL
    // documentation.
    return 65536;
  }



  /**
   * Returns the negotiated maximum size of raw data which can be sent to the
   * client.
   *
   * @return The negotiated maximum size of raw data which can be sent to the
   *         client.
   */
  int getMaxRawSendBufferSize()
  {
    String str = (String) saslServer.getNegotiatedProperty(Sasl.RAW_SEND_SIZE);
    if (str != null)
    {
      try
      {
        return Integer.parseInt(str);
      }
      catch (NumberFormatException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Default buffer size if not specified according to Java SASL
    // documentation.
    return 65536;
  }



  /**
   * Return the Security Strength Factor of the cipher if the QOP property is
   * confidentiality, or, 1 if it is integrity.
   *
   * @return The SSF of the cipher used during confidentiality or integrity
   *         processing.
   */
  int getSSF()
  {
    int ssf = 0;
    final String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
    if (qop.equalsIgnoreCase(integrity))
    {
      ssf = 1;
    }
    else
    {
      final String negStrength = (String) saslServer
          .getNegotiatedProperty(Sasl.STRENGTH);
      if (negStrength.equalsIgnoreCase("low"))
      {
        ssf = 40;
      }
      else if (negStrength.equalsIgnoreCase("medium"))
      {
        ssf = 56;
      }
      else
      {
        ssf = 128;
      }
    }
    return ssf;
  }



  /**
   * Return {@code true} if the bind has been completed. If the context is
   * supporting confidentiality or integrity, the security provider will need to
   * check if the context has completed the handshake with the client and is
   * ready to process confidentiality or integrity messages.
   *
   * @return {@code true} if the handshaking is complete.
   */
  boolean isBindComplete()
  {
    return saslServer.isComplete();
  }



  /**
   * Perform the authentication as the specified login context. The specified
   * bind operation needs to be saved so the callbacks have access to it. Only
   * used by the GSSAPI mechanism.
   *
   * @param loginContext
   *          The login context to perform the authentication as.
   * @param bindOp
   *          The bind operation needed by the callbacks to process the
   *          authentication.
   */
  void performAuthentication(final LoginContext loginContext,
      final BindOperation bindOp)
  {
    this.bindOp = bindOp;
    try
    {
      Subject.doAs(loginContext.getSubject(), this);
    }
    catch (final PrivilegedActionException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      final Message msg = ERR_SASL_PROTOCOL_ERROR.get(mechanism,
          getExceptionMessage(e));
      handleError(msg);
    }
  }



  /**
   * Unwrap the specified byte array using the provided offset and length
   * values. Used only when the SASL server has negotiated confidentiality or
   * integrity processing.
   *
   * @param bytes
   *          The byte array to unwrap.
   * @param offset
   *          The offset in the array.
   * @param len
   *          The length from the offset of the number of bytes to unwrap.
   * @return A byte array containing the clear or unwrapped bytes.
   * @throws SaslException
   *           If the bytes cannot be unwrapped.
   */
  byte[] unwrap(final byte[] bytes, final int offset, final int len)
      throws SaslException
  {
    return saslServer.unwrap(bytes, offset, len);
  }



  /**
   * Wrap the specified clear byte array using the provided offset and length
   * values. Used only when the SASL server has negotiated
   * confidentiality/integrity processing.
   *
   * @param clearBytes
   *          The clear byte array to wrap.
   * @param offset
   *          The offset into the clear byte array..
   * @param len
   *          The length from the offset of the number of bytes to wrap.
   * @return A byte array containing the wrapped bytes.
   * @throws SaslException
   *           If the clear bytes cannot be wrapped.
   */
  byte[] wrap(final byte[] clearBytes, final int offset, final int len)
      throws SaslException
  {
    return saslServer.wrap(clearBytes, offset, len);
  }



  /**
   * This callback is used to process the authorize callback. It is used during
   * both GSSAPI and DIGEST-MD5 processing. When processing the GSSAPI
   * mechanism, this is the only callback invoked. When processing the
   * DIGEST-MD5 mechanism, it is the last callback invoked after the name and
   * password callbacks respectively.
   *
   * @param callback
   *          The authorize callback instance to process.
   */
  private void authorizeCallback(final AuthorizeCallback callback)
  {
    final String responseAuthzID = callback.getAuthorizationID();

    // If the authEntry is null, then we are processing a GSSAPI SASL bind,
    // and first need to try to map the authentication ID to an user entry.
    // The authEntry is never null, when processing a DIGEST-MD5 SASL bind.
    if (authEntry == null)
    {
      final String authid = callback.getAuthenticationID();
      try
      {
        authEntry = identityMapper.getEntryForID(authid);
        if (authEntry == null)
        {
          setCallbackMsg(ERR_SASL_AUTHENTRY_NO_MAPPED_ENTRY.get(authid));
          callback.setAuthorized(false);
          return;
        }
      }
      catch (final DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }
        setCallbackMsg(ERR_SASL_CANNOT_MAP_AUTHENTRY.get(authid,
            de.getMessage()));
        callback.setAuthorized(false);
        return;
      }
      userName = authid;
    }

    if (responseAuthzID.length() == 0)
    {
      setCallbackMsg(ERR_SASLDIGESTMD5_EMPTY_AUTHZID.get());
      callback.setAuthorized(false);
      return;
    }
    else if (!responseAuthzID.equals(userName))
    {
      final String lowerAuthzID = toLowerCase(responseAuthzID);

      // Process the callback differently depending on if the authzid
      // string begins with the string "dn:" or not.
      if (lowerAuthzID.startsWith("dn:"))
      {
        authzDNCheck(callback);
      }
      else
      {
        authzIDCheck(callback);
      }
    }
    else
    {
      authzEntry = authEntry;
      callback.setAuthorized(true);
    }
  }



  /**
   * Process the specified authorize callback. This method is called if the
   * callback's authorization ID begins with the string "dn:".
   *
   * @param callback
   *          The authorize callback to process.
   */
  private void authzDNCheck(final AuthorizeCallback callback)
  {
    final String responseAuthzID = callback.getAuthorizationID();
    DN authzDN;
    callback.setAuthorized(true);

    try
    {
      authzDN = DN.decode(responseAuthzID.substring(3));
    }
    catch (final DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      setCallbackMsg(ERR_SASL_AUTHZID_INVALID_DN.get(responseAuthzID,
          e.getMessageObject()));
      callback.setAuthorized(false);
      return;
    }

    final DN actualAuthzDN = DirectoryServer.getActualRootBindDN(authzDN);
    if (actualAuthzDN != null)
    {
      authzDN = actualAuthzDN;
    }

    if (!authzDN.equals(authEntry.getDN()))
    {
      if (authzDN.isNullDN())
      {
        authzEntry = null;
      }
      else
      {
        try
        {
          if ((authzEntry = DirectoryServer.getEntry(authzDN)) == null)
          {
            setCallbackMsg(ERR_SASL_AUTHZID_NO_SUCH_ENTRY.get(String
                .valueOf(authzDN)));
            callback.setAuthorized(false);
            return;
          }
        }
        catch (final DirectoryException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          setCallbackMsg(ERR_SASL_AUTHZID_CANNOT_GET_ENTRY.get(
              String.valueOf(authzDN), e.getMessageObject()));
          callback.setAuthorized(false);
          return;
        }
      }
      final AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
          DirectoryServer.isRootDN(authEntry.getDN()));
      if (!hasPrivilege(authInfo))
      {
        callback.setAuthorized(false);
      }
      else
      {
        callback.setAuthorized(hasPermission(authInfo));
      }
    }
  }



  /**
   * Process the specified authorize callback. This method is called if the
   * callback's authorization ID does not begin with the string "dn:".
   *
   * @param callback
   *          The authorize callback to process.
   */
  private void authzIDCheck(final AuthorizeCallback callback)
  {
    final String authzid = callback.getAuthorizationID();
    final String lowerAuthzID = toLowerCase(authzid);
    String idStr;
    callback.setAuthorized(true);

    if (lowerAuthzID.startsWith("u:"))
    {
      idStr = authzid.substring(2);
    }
    else
    {
      idStr = authzid;
    }

    if (idStr.length() == 0)
    {
      authzEntry = null;
    }
    else
    {
      try
      {
        authzEntry = identityMapper.getEntryForID(idStr);
        if (authzEntry == null)
        {
          setCallbackMsg(ERR_SASL_AUTHZID_NO_MAPPED_ENTRY.get(authzid));
          callback.setAuthorized(false);
          return;
        }
      }
      catch (final DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        setCallbackMsg(ERR_SASL_AUTHZID_NO_MAPPED_ENTRY.get(authzid));
        callback.setAuthorized(false);
        return;
      }
    }

    if ((authzEntry == null) || (!authzEntry.getDN().equals(authEntry.getDN())))
    {
      // Create temporary authorization information and run it both
      // through the privilege and then the access control subsystems.
      final AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
          DirectoryServer.isRootDN(authEntry.getDN()));
      if (!hasPrivilege(authInfo))
      {
        callback.setAuthorized(false);
      }
      else
      {
        callback.setAuthorized(hasPermission(authInfo));
      }
    }
  }



  /**
   * Helper routine to call the SASL server evaluateResponse method with the
   * specified byte array.
   *
   * @param bytes
   *          The byte array to pass to the SASL server.
   * @return A byte array containing the result of the evaluation.
   * @throws SaslException
   *           If the SASL server cannot evaluate the byte array.
   */
  private ByteString evaluateResponse(ByteString response) throws SaslException
  {
    if (response == null)
    {
      response = ByteString.empty();
    }

    final byte[] evalResponse = saslServer.evaluateResponse(response
        .toByteArray());
    if (evalResponse == null)
    {
      return ByteString.empty();
    }
    else
    {
      return ByteString.wrap(evalResponse);
    }
  }



  /**
   * Try to get a entry from the directory using the specified DN. Used only for
   * DIGEST-MD5 SASL mechanism.
   *
   * @param userDN
   *          The DN of the entry to retrieve from the server.
   */
  private void getAuthEntry(final DN userDN)
  {
    final Lock readLock = LockManager.lockRead(userDN);
    if (readLock == null)
    {
      setCallbackMsg(INFO_SASL_CANNOT_LOCK_ENTRY.get(String.valueOf(userDN)));
      return;
    }

    try
    {
      authEntry = DirectoryServer.getEntry(userDN);
    }
    catch (final DirectoryException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      setCallbackMsg(ERR_SASL_CANNOT_GET_ENTRY_BY_DN.get(
          String.valueOf(userDN), SASL_MECHANISM_DIGEST_MD5,
          e.getMessageObject()));
      return;
    }
    finally
    {
      LockManager.unlock(userDN, readLock);
    }
  }



  /**
   * This method is used to process an exception that is thrown during bind
   * processing. It will try to determine if the exception is a result of
   * callback processing, and if it is, will try to use a more informative
   * failure message set by the callback. If the exception is a result of a
   * error during the the SASL server processing, the callback message will be
   * null, and the method will use the specified message parameter as the
   * failure reason. This is a more cryptic exception message hard-coded in the
   * SASL server internals. The method also disposes of the SASL server, clears
   * the authentication state and sets the result code to INVALID_CREDENTIALs
   *
   * @param msg
   *          The message to use if the callback message is not null.
   */
  private void handleError(final Message msg)
  {
    dispose();
    final ClientConnection clientConn = bindOp.getClientConnection();
    clientConn.setSASLAuthStateInfo(null);

    // Check if the callback message is null and use that message if not.
    if (cbResultCode != null)
    {
      bindOp.setResultCode(cbResultCode);
    }
    else
    {
      bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
    }

    if (cbMsg != null)
    {
      bindOp.setAuthFailureReason(cbMsg);
    }
    else
    {
      bindOp.setAuthFailureReason(msg);
    }
  }



  /**
   * Checks the specified authentication information parameter against the
   * access control subsystem to see if it has the "proxy" right.
   *
   * @param authInfo
   *          The authentication information to check access on.
   * @return {@code true} if the authentication information has proxy access.
   */
  private boolean hasPermission(final AuthenticationInfo authInfo)
  {
    boolean ret = true;
    Entry e = authzEntry;

    // If the authz entry is null, use the entry associated with the NULL DN.
    if (e == null)
    {
      try
      {
        e = DirectoryServer.getEntry(DN.nullDN());
      }
      catch (final DirectoryException ex)
      {
        return false;
      }
    }

    if (AccessControlConfigManager.getInstance().getAccessControlHandler()
        .mayProxy(authInfo.getAuthenticationEntry(), e, bindOp) == false)
    {
      setCallbackMsg(ERR_SASL_AUTHZID_INSUFFICIENT_ACCESS.get(String
          .valueOf(authEntry.getDN())));
      ret = false;
    }

    return ret;
  }



  /**
   * Checks the specified authentication information parameter against the
   * privilege subsystem to see if it has PROXIED_AUTH privileges.
   *
   * @param authInfo
   *          The authentication information to use in the check.
   * @return {@code true} if the authentication information has PROXIED_AUTH
   *         privileges.
   */
  private boolean hasPrivilege(final AuthenticationInfo authInfo)
  {
    boolean ret = true;
    final InternalClientConnection tempConn = new InternalClientConnection(
        authInfo);
    if (!tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOp))
    {
      setCallbackMsg(ERR_SASL_AUTHZID_INSUFFICIENT_PRIVILEGES.get(String
          .valueOf(authEntry.getDN())));
      ret = false;
    }
    return ret;
  }



  /**
   * Initialize the SASL server using parameters specified in the constructor.
   */
  private void initSASLServer() throws SaslException
  {
    saslServer = Sasl.createSaslServer(mechanism, SASL_DEFAULT_PROTOCOL,
        serverFQDN, saslProps, this);
    if (saslServer == null)
    {
      final Message msg = ERR_SASL_CREATE_SASL_SERVER_FAILED.get(mechanism,
          serverFQDN);
      throw new SaslException(Message.toString(msg));
    }
  }



  /**
   * Return true if the SASL server has negotiated with the client to support
   * confidentiality or integrity.
   *
   * @return {@code true} if the context supports confidentiality or integrity.
   */
  private boolean isConfidentialIntegrity()
  {
    boolean ret = false;
    final String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
    if (qop.equalsIgnoreCase(confidentiality)
        || qop.equalsIgnoreCase(integrity))
    {
      ret = true;
    }
    return ret;
  }



  /**
   * Process the specified name callback. Used only for DIGEST-MD5 SASL
   * mechanism.
   *
   * @param nameCallback
   *          The name callback to process.
   */
  private void nameCallback(final NameCallback nameCallback)
  {
    userName = nameCallback.getDefaultName();
    final String lowerUserName = toLowerCase(userName);

    // Process the user name differently if it starts with the string "dn:".
    if (lowerUserName.startsWith("dn:"))
    {
      DN userDN;
      try
      {
        userDN = DN.decode(userName.substring(3));
      }
      catch (final DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        setCallbackMsg(ERR_SASL_CANNOT_DECODE_USERNAME_AS_DN.get(mechanism,
            userName, e.getMessageObject()));
        return;
      }

      if (userDN.isNullDN())
      {
        setCallbackMsg(ERR_SASL_USERNAME_IS_NULL_DN.get(mechanism));
        return;
      }

      final DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
      if (rootDN != null)
      {
        userDN = rootDN;
      }
      getAuthEntry(userDN);
    }
    else
    {
      // The entry name is not a DN, try to map it using the identity
      // mapper.
      String entryID = userName;
      if (lowerUserName.startsWith("u:"))
      {
        if (lowerUserName.equals("u:"))
        {
          setCallbackMsg(ERR_SASL_ZERO_LENGTH_USERNAME
              .get(mechanism, mechanism));
          return;
        }
        entryID = userName.substring(2);
      }
      try
      {
        authEntry = identityMapper.getEntryForID(entryID);
      }
      catch (final DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        setCallbackMsg(ERR_SASLDIGESTMD5_CANNOT_MAP_USERNAME.get(
            String.valueOf(userName), e.getMessageObject()));
        return;
      }
    }

    if (authEntry == null)
    {
      // The authEntry is null, this is an error. The password callback
      // will catch this error. There is no way to stop the processing
      // from the name callback.
      return;
    }
  }



  /**
   * Process the specified password callback. Used only for the DIGEST-MD5 SASL
   * mechanism. The password callback is processed after the name callback.
   *
   * @param passwordCallback
   *          The password callback to process.
   */
  private void passwordCallback(final PasswordCallback passwordCallback)
  {
    // If there is no authEntry this is an error.
    if (authEntry == null)
    {
      setCallbackMsg(ERR_SASL_NO_MATCHING_ENTRIES.get(userName));
      return;
    }

    // Try to get a clear password to use.
    List<ByteString> clearPasswords;
    try
    {
      final AuthenticationPolicyState authState = AuthenticationPolicyState
          .forUser(authEntry, false);

      if (!authState.isPasswordPolicy())
      {
        final Message message = ERR_SASL_ACCOUNT_NOT_LOCAL.get(mechanism,
            String.valueOf(authEntry.getDN()));
        setCallbackMsg(ResultCode.INAPPROPRIATE_AUTHENTICATION, message);
        return;
      }

      final PasswordPolicyState pwPolicyState = (PasswordPolicyState) authState;

      clearPasswords = pwPolicyState.getClearPasswords();
      if ((clearPasswords == null) || clearPasswords.isEmpty())
      {
        setCallbackMsg(ERR_SASL_NO_REVERSIBLE_PASSWORDS.get(mechanism,
            String.valueOf(authEntry.getDN())));
        return;
      }
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      setCallbackMsg(ERR_SASL_CANNOT_GET_REVERSIBLE_PASSWORDS.get(
          String.valueOf(authEntry.getDN()), mechanism, String.valueOf(e)));
      return;
    }

    // Use the first password.
    final char[] password = clearPasswords.get(0).toString().toCharArray();
    passwordCallback.setPassword(password);
    return;
  }



  /**
   * This callback is used to process realm information. It is not used.
   *
   * @param callback
   *          The realm callback instance to process.
   */
  private void realmCallback(final RealmCallback callback)
  {
  }



  /**
   * Sets the callback message to the specified message.
   *
   * @param cbMsg
   *          The message to set the callback message to.
   */
  private void setCallbackMsg(final Message cbMsg)
  {
    setCallbackMsg(ResultCode.INVALID_CREDENTIALS, cbMsg);
  }



  /**
   * Sets the callback message to the specified message.
   *
   * @param cbResultCode
   *          The result code.
   * @param cbMsg
   *          The message.
   */
  private void setCallbackMsg(final ResultCode cbResultCode,
      final Message cbMsg)
  {
    this.cbResultCode = cbResultCode;
    this.cbMsg = cbMsg;
  }
}
