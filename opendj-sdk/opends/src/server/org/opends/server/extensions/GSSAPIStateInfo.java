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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;

import org.opends.server.api.ClientConnection;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure that holds state information needed for
 * processing a SASL GSSAPI bind from a client.
 */
public class GSSAPIStateInfo
       implements PrivilegedExceptionAction<Boolean>, CallbackHandler
{



  // The bind operation with which this state is associated.
  private BindOperation bindOperation;

  // The client connection with which this state is associated.
  private ClientConnection clientConnection;

  // The entry of the user that authenticated in this session.
  private Entry userEntry;

  // The GSSAPI authentication handler that created this state information.
  private GSSAPISASLMechanismHandler gssapiHandler;

  // The login context used to perform server-side authentication.
  private LoginContext loginContext;

  // The SASL server that will be used to actually perform the authentication.
  private SaslServer saslServer;

  // The protocol that the client is using to communicate with the server.
  private String protocol;

  // The FQDN of this system to use in the authentication process.
  private String serverFQDN;




  /**
   * Creates a new GSSAPI state info structure with the provided information.
   *
   * @param  gssapiHandler  The GSSAPI authentication handler that created this
   *                        state information.
   * @param  bindOperation  The bind operation with which this state is
   *                        associated.
   * @param  serverFQDN     The fully-qualified domain name for the server to
   *                        use in the authentication process.
   *
   * @throws  InitializationException  If it is not possible to authenticate to
   *                                   the KDC to verify the client credentials.
   */
  public GSSAPIStateInfo(GSSAPISASLMechanismHandler gssapiHandler,
                         BindOperation bindOperation, String serverFQDN)
         throws InitializationException
  {
    this.gssapiHandler = gssapiHandler;
    this.bindOperation = bindOperation;
    this.serverFQDN    = serverFQDN;

    clientConnection = bindOperation.getClientConnection();
    protocol         = toLowerCase(clientConnection.getProtocol());
    userEntry        = null;


    // Create the LoginContext and do the server-side authentication.
    // FIXME -- Can this be moved to a one-time call in the GSSAPI handler
    //          rather than once per GSSAPI bind attempt?
    try
    {
      loginContext =
           new LoginContext(GSSAPISASLMechanismHandler.class.getName(), this);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SASLGSSAPI_CANNOT_CREATE_LOGIN_CONTEXT;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    try
    {
      loginContext.login();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SASLGSSAPI_CANNOT_AUTHENTICATE_SERVER;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    saslServer = null;
  }



  /**
   * Sets the bind operation for the next stage of processing in the GSSAPI
   * authentication.  This must be called before the processing is performed so
   * that the appropriate response may be sent to the client.
   *
   * @param  bindOperation  The bind operation for the next stage of processing
   *                        in the GSSAPI authentication.
   */
  public void setBindOperation(BindOperation bindOperation)
  {
    this.bindOperation = bindOperation;
  }



  /**
   * Retrieves the entry of the user that has authenticated on this GSSAPI
   * session.  This should only be available after a successful GSSAPI
   * authentication.  The return value of this method should be considered
   * unreliable if GSSAPI authentication has not yet completed successfully.
   *
   * @return  x
   */
  public Entry getUserEntry()
  {
    return userEntry;
  }



  /**
   * Destroys any sensitive information that might be associated with the SASL
   * server instance.
   */
  public void dispose()
  {
    try
    {
      saslServer.dispose();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Processes the next stage of the GSSAPI bind process.  This may be used for
   * the first stage or any stage thereafter until the authentication is
   * complete.  It will automatically take care of the JAAS processing behind
   * the scenes as necessary.
   */
  public void processAuthenticationStage()
  {
    try
    {
      Subject.doAs(loginContext.getSubject(), this);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Processes a stage of the SASL GSSAPI bind request.  The
   * <CODE>setBindOperation</CODE> method must have been called to update the
   * reference to the latest bind request before invoking this method through
   * <CODE>doAs</CODE> or <CODE>doAsPrivileged</CODE>.
   *
   * @return  <CODE>true</CODE> if there was no error during this stage of the
   *          bind and processing can continue, or <CODE>false</CODE> if an
   *          error occurred and and processing should not continue.
   */
  public Boolean run()
  {
    if (saslServer == null)
    {
      // Create the SASL server instance for use with this authentication
      // attempt.
      try
      {
        HashMap<String,String> saslProperties = new HashMap<String,String>();

        // FIXME -- We need to add support for auth-int and auth-conf.
        // propertyMap.put(Sasl.QOP, "auth,auth-int,auth-conf");
        saslProperties.put(Sasl.QOP, "auth");

        saslProperties.put(Sasl.REUSE, "false");

        saslServer = Sasl.createSaslServer(SASL_MECHANISM_GSSAPI, protocol,
                                           serverFQDN, saslProperties, this);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }

        int    msgID   = MSGID_SASLGSSAPI_CANNOT_CREATE_SASL_SERVER;
        String message = getMessage(msgID, stackTraceToSingleLineString(e));

        clientConnection.setSASLAuthStateInfo(null);
        bindOperation.setAuthFailureReason(msgID, message);
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        return false;
      }
    }


    // Get the SASL credentials from the bind request.
    byte[] clientCredBytes;
    ByteString clientCredentials = bindOperation.getSASLCredentials();
    if (clientCredentials == null)
    {
      clientCredBytes = new byte[0];
    }
    else
    {
      clientCredBytes = clientCredentials.value();
    }


    // Process the client SASL credentials and get the data to include in the
    // server SASL credentials of the response.
    ASN1OctetString serverSASLCredentials;
    try
    {
      byte[] serverCredBytes = saslServer.evaluateResponse(clientCredBytes);

      if (serverCredBytes == null)
      {
        serverSASLCredentials = null;
      }
      else
      {
        serverSASLCredentials = new ASN1OctetString(serverCredBytes);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      try
      {
        saslServer.dispose();
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e2);
        }
      }

      int    msgID   = MSGID_SASLGSSAPI_CANNOT_EVALUATE_RESPONSE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));

      clientConnection.setSASLAuthStateInfo(null);
      bindOperation.setAuthFailureReason(msgID, message);
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return false;
    }


    // If the authentication is not yet complete, then send a "SASL bind in
    // progress" response to the client.
    if (! saslServer.isComplete())
    {
      clientConnection.setSASLAuthStateInfo(saslServer);
      bindOperation.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
      bindOperation.setServerSASLCredentials(serverSASLCredentials);
      return true;
    }


    // If the authentication is complete, then get the authorization ID from the
    // SASL server and map that to a user in the directory.
    String authzID = saslServer.getAuthorizationID();
    if ((authzID == null) || (authzID.length() == 0))
    {
      try
      {
        saslServer.dispose();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }

      int    msgID   = MSGID_SASLGSSAPI_NO_AUTHZ_ID;
      String message = getMessage(msgID);

      clientConnection.setSASLAuthStateInfo(null);
      bindOperation.setAuthFailureReason(msgID, message);
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return false;
    }


    try
    {
      userEntry = gssapiHandler.getUserForAuthzID(bindOperation, authzID);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, de);
      }

      try
      {
        saslServer.dispose();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }

      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      bindOperation.setAuthFailureReason(de.getErrorMessageID(),
                                         de.getErrorMessage());
      clientConnection.setSASLAuthStateInfo(null);
      return false;
    }


    // If the user entry is null, then we couldn't map the authorization ID to
    // a user.
    if (userEntry == null)
    {
      try
      {
        saslServer.dispose();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }

      int    msgID   = MSGID_SASLGSSAPI_CANNOT_MAP_AUTHZID;
      String message = getMessage(msgID, authzID);

      clientConnection.setSASLAuthStateInfo(null);
      bindOperation.setAuthFailureReason(msgID, message);
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
      return false;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // The authentication was successful, so set the proper state information
    // in the client connection and return success.
    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, SASL_MECHANISM_GSSAPI,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    bindOperation.setResultCode(ResultCode.SUCCESS);

    // FIXME -- If we're using integrity or confidentiality, then we can't do
    // this.
    clientConnection.setSASLAuthStateInfo(null);
    try
    {
      saslServer.dispose();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }

    return true;
  }



  /**
   * Handles any callbacks that might be required in order to process a SASL
   * GSSAPI bind on the server.  In this case, if an authorization ID was
   * provided, then a callback may be used to determine whether it is
   * acceptable.
   *
   * @param  callbacks  The callbacks needed to provide information for the
   *                    GSSAPI authentication process.
   *
   * @throws  UnsupportedCallbackException  If an unexpected callback is
   *                                        included in the provided set.
   */
  public void handle(Callback[] callbacks)
         throws UnsupportedCallbackException
  {
    for (Callback callback : callbacks)
    {
      if (callback instanceof NameCallback)
      {
        String authID = toLowerCase(clientConnection.getProtocol()) + "/" +
                        serverFQDN;
        ((NameCallback) callback).setName(authID);
      }
      else if (callback instanceof AuthorizeCallback)
      {
        // FIXME -- Should we allow an authzID different from the authID?
        // FIXME -- Do we need to do anything else here?
        AuthorizeCallback authzCallback = (AuthorizeCallback) callback;
        String authID  = authzCallback.getAuthenticationID();
        String authzID = authzCallback.getAuthorizationID();

        if (authID.equals(authzID))
        {
          authzCallback.setAuthorizedID(authzID);
          authzCallback.setAuthorized(true);
        }
        else
        {
          int msgID = MSGID_SASLGSSAPI_DIFFERENT_AUTHID_AND_AUTHZID;
          String message = getMessage(msgID, authID, authzID);
          bindOperation.setAuthFailureReason(msgID, message);
          authzCallback.setAuthorized(false);
        }
      }
      else
      {
        // We weren't prepared for this type of callback.
        int    msgID   = MSGID_SASLGSSAPI_UNEXPECTED_CALLBACK;
        String message = getMessage(msgID, String.valueOf(callback));
        throw new UnsupportedCallbackException(callback, message);
      }
    }
  }
}

