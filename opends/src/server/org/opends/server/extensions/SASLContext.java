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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.messages.Message;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPClientConnection;
import org.opends.server.types.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines the SASL context needed to process GSSAPI and DIGEST-MD5
 * bind requests from clients.
 *
 */
public class
SASLContext implements CallbackHandler, PrivilegedExceptionAction<Boolean> {


    // The tracer object for the debug logger.
    private static final DebugTracer TRACER = getTracer();

    // The SASL server to use in the authentication.
    private SaslServer saslServer=null;

    // The identity mapper to use when mapping identities.
    private final IdentityMapper<?> identityMapper;

    //The  property set to use when creating the SASL server.
    private HashMap<String, String>saslProps;

    //The fully qualified domain name to use when creating the SASL server.
    private String serverFQDN;

    //The SASL mechanism name.
    private final String mechanism;

    //The authorization entry used in the authentication.
    private Entry authEntry=null;

    //The authorization entry used in the authentication.
    private Entry authzEntry=null;

    //The user name used in the authentication taken from the name callback.
    private String userName;

    //Error message used by callbacks.
    private Message cbMsg;

    //The current bind operation used by the callbacks.
    private BindOperation bindOp;

    //Used to check if negotiated QOP is confidentiality or integrity.
    private final String confidentiality = "auth-conf";
    private final String integrity = "auth-int";


    /**
     * Create a SASL context using the specified parameters. A SASL server will
     * be instantiated only for the DIGEST-MD5 mechanism. The GSSAPI mechanism
     * must instantiate the SASL server as the login context in a separate step.
     *
     * @param saslProps The properties to use in creating the SASL server.
     * @param serverFQDN The fully qualified domain name to use in creating the
     *                   SASL server.
     * @param mechanism The SASL mechanism name.
     * @param identityMapper The identity mapper to use in mapping identities.
     *
     * @throws SaslException If the SASL server can not be instantiated.
     */
    private SASLContext(HashMap<String, String>saslProps, String serverFQDN,
                          String mechanism, IdentityMapper<?> identityMapper)
                          throws SaslException {
        this.identityMapper = identityMapper;
        this.mechanism = mechanism;
        this.saslProps = saslProps;
        this.serverFQDN = serverFQDN;
        if(mechanism.equals(SASL_MECHANISM_DIGEST_MD5)) {
            initSASLServer();
        }
    }


    /**
     * Instantiate a GSSAPI/DIGEST-MD5 SASL context using the specified
     * parameters.
     *
     * @param saslProps The properties to use in creating the SASL server.
     * @param serverFQDN The fully qualified domain name to use in creating the
     *                   SASL server.
     * @param mechanism The SASL mechanism name.
     * @param identityMapper The identity mapper to use in mapping identities.
     * @return A fully instantiated SASL context to use in processing a SASL
     *         bind for the GSSAPI or DIGEST-MD5 mechanisms.
     *
     * @throws SaslException If the SASL server can not be instantiated.
     */
    public static
    SASLContext createSASLContext(HashMap<String,String>saslProps,
                        String serverFQDN, String mechanism,
                        IdentityMapper<?> identityMapper) throws SaslException {
      return (new SASLContext(saslProps,serverFQDN, mechanism, identityMapper));
    }


    /**
     * Initialize the SASL server using parameters specified in the
     * constructor.
     */
    private void initSASLServer() throws SaslException {
       this.saslServer = Sasl.createSaslServer(mechanism, SASL_DEFAULT_PROTOCOL,
                                               serverFQDN, saslProps, this);
    }


    /**
     * Wrap the specified clear byte array using the provided offset and length
     * values. Used only when the SASL server has negotiated
     * confidentiality/integrity  processing.
     *
     * @param clearBytes The clear byte array to wrap.
     * @param offset The offset into the clear byte array..
     * @param len The length from the offset of the number of bytes to wrap.
     * @return A byte array containing the wrapped bytes.
     *
     * @throws SaslException If the clear bytes cannot be wrapped.
     */
    byte[] wrap(byte[] clearBytes, int offset, int len)
    throws SaslException {
        return saslServer.wrap(clearBytes, offset, len);
    }


    /**
     * Unwrap the specified byte array using the provided offset and length
     * values. Used only when the SASL server has negotiated
     * confidentiality or integrity processing.
     *
     * @param bytes The byte array to unwrap.
     * @param offset The offset in the array.
     * @param len The length from the offset of the number of bytes to unwrap.
     * @return A byte array containing the clear or unwrapped bytes.
     *
     * @throws SaslException If the bytes cannot be unwrapped.
     */
    byte[] unwrap(byte[] bytes, int offset, int len)
    throws SaslException {
        return saslServer.unwrap(bytes, offset, len);
    }


    /**
     * Dispose of the SASL server instance.
     */
    void dispose() {
        try {
            saslServer.dispose();
        } catch (SaslException e) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
        }
    }


    /**
     * Return the negotiated buffer size.
     *
     * @param prop The buffer size property to return.
     * @return The value of the negotiated buffer size.
     */
    int getBufSize(String prop) {
          String sizeStr =
              (String) saslServer.getNegotiatedProperty(prop);
          return Integer.parseInt(sizeStr);
    }

    /**
     * Return the Security Strength Factor of the cipher if the QOP property
     * is confidentiality, or, 1 if it is integrity.
     *
     * @return The SSF of the cipher used during confidentiality or
     *         integrity processing.
     */
    int getSSF() {
        int ssf = 0;
        String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
        if(qop.equalsIgnoreCase(integrity)) {
            ssf = 1;
        } else {
            String negStrength =
                (String) saslServer.getNegotiatedProperty(Sasl.STRENGTH);
            if(negStrength.equalsIgnoreCase("low"))
                ssf = 40;
            else if (negStrength.equalsIgnoreCase("medium"))
                ssf = 56;
            else
                ssf = 128;
        }
        return ssf;
    }

    /**
     * Return {@code true} if the bind has been completed. If the context is
     * supporting confidentiality or integrity, the security provider will need
     * to check if the context has completed the handshake with the client
     * and is ready to process confidentiality or integrity messages.
     *
     * @return {@code true} if the handshaking is complete.
     */
    boolean isBindComplete() {
          return saslServer.isComplete();
    }


    /**
     * Return true if the SASL server has negotiated with the client to support
     * confidentiality or integrity.
     *
     * @return {@code true} if the context supports confidentiality or
     *         integrity.
     */
    private boolean isConfidentialIntegrity() {
      boolean ret = false;
      String qop = (String) saslServer.getNegotiatedProperty(Sasl.QOP);
      if(qop.equalsIgnoreCase(confidentiality) ||
         qop.equalsIgnoreCase(integrity))
           ret = true;
      return ret;
    }


    /**
     * Helper routine to call the SASL server evaluateResponse method with the
     * specified byte array.
     *
     * @param bytes The byte array to pass to the SASL server.
     * @return A byte array containing the result of the evaluation.
     *
     * @throws SaslException If the SASL server cannot evaluate the byte array.
     */
    private byte[] evaluateResponse(byte[] bytes) throws SaslException {
          return saslServer.evaluateResponse(bytes);
      }


    /**
     * This method is used to process an exception that is thrown during bind
     * processing. It will try to determine if the exception is a result of
     * callback processing, and if it is, will try to use a more informative
     * failure message set by the callback.
     *
     * If the exception is a result of a error during the the SASL server
     * processing, the callback message will be null, and
     * the method will use the specified message parameter as the
     * failure reason. This is a more cryptic exception message hard-coded
     * in the SASL server internals.
     *
     * The method also disposes of the SASL server, clears the authentication
     * state  and sets the result code to INVALID_CREDENTIALs
     *
     * @param msg The message to use if the callback message is not null.
     */
    private void handleError(Message msg) {
        dispose();
        ClientConnection clientConn = bindOp.getClientConnection();
        clientConn.setSASLAuthStateInfo(null);
        //Check if the callback message is null and use that message if not.
        if(cbMsg != null)
            bindOp.setAuthFailureReason(cbMsg);
        else
            bindOp.setAuthFailureReason(msg);
        bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
    }


    /**
     * Checks the specified authentication information parameter against the
     * privilege subsystem to see if it has PROXIED_AUTH privileges.
     *
     * @param authInfo The authentication information to use in the check.
     * @return {@code true} if the authentication information has
     *         PROXIED_AUTH privileges.
     */
    private boolean
    hasPrivilege(AuthenticationInfo authInfo) {
        boolean ret = true;
          InternalClientConnection tempConn =
               new InternalClientConnection(authInfo);
          if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOp)) {
              setCallbackMsg(ERR_SASL_AUTHZID_INSUFFICIENT_PRIVILEGES.get(
                             String.valueOf(authEntry.getDN())));
              ret = false;
          }
          return ret;
    }


    /**
     * Checks the specified authentication information parameter against the
     * access control subsystem to see if it has the "proxy" right.
     *
     * @param authInfo The authentication information to check access on.
     * @return {@code true} if the authentication information has
     *         proxy access.
     */
    private boolean
    hasPermission(AuthenticationInfo authInfo) {
        boolean ret = true;
        Entry e = authzEntry;
        //If the authz entry is null, use the entry associated with the NULL DN.
        if(e == null) {
            try {
                e = DirectoryServer.getEntry(DN.nullDN());
            } catch (DirectoryException ex) {
                return false;
            }
        }
        if (AccessControlConfigManager.getInstance().getAccessControlHandler().
               mayProxy(authInfo.getAuthenticationEntry(), e,
                        bindOp) == false) {
            setCallbackMsg(ERR_SASL_AUTHZID_INSUFFICIENT_ACCESS.get(
                    String.valueOf(authEntry.getDN())));
            ret = false;
        }
        return ret;
    }


    /**
     * Sets the callback message to the specified message.
     *
     * @param cbMsg The message to set the callback message to.
     */
    private void setCallbackMsg(Message cbMsg) {
        this.cbMsg = cbMsg;
    }


   /**
     * Process the specified callback array.
     *
     *@param callbacks An array of callbacks that need processing.
     *@throws UnsupportedCallbackException If a callback is not supported.
     */
    public void handle(Callback[] callbacks)
    throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                nameCallback((NameCallback) callback);
            } else if (callback instanceof PasswordCallback) {
                passwordCallback((PasswordCallback) callback);
            } else if (callback instanceof RealmCallback) {
                realmCallback((RealmCallback) callback);
            } else if (callback instanceof AuthorizeCallback)  {
                authorizeCallback((AuthorizeCallback) callback);
            } else {
                Message message =
                    INFO_SASL_UNSUPPORTED_CALLBACK.get(mechanism,
                                                      String.valueOf(callback));
                throw new UnsupportedCallbackException(callback,
                        message.toString());
            }
        }
    }


    /**
     * This callback is used to process realm information. It is not used.
     *
     * @param callback The realm callback instance to process.
     */
    private void realmCallback(RealmCallback callback) {
    }


    /**
     * This callback is used to process the authorize callback. It is used
     * during both GSSAPI and DIGEST-MD5 processing. When processing the GSSAPI
     * mechanism, this is the only callback invoked. When processing the
     * DIGEST-MD5 mechanism, it is the last callback invoked after the name
     * and password callbacks respectively.
     *
     * @param callback The authorize callback instance to process.
     */
    private void authorizeCallback(AuthorizeCallback callback) {
        String  responseAuthzID = callback.getAuthorizationID();
        //If the authEntry is null, then we are processing a GSSAPI SASL bind,
        //and first need to try to map the authentication ID to an user entry.
        //The authEntry is never null, when processing a DIGEST-MD5 SASL bind.
        if(authEntry == null) {
            String  authid = callback.getAuthenticationID();
            try {
                authEntry = identityMapper.getEntryForID(authid);
                if (authEntry == null) {
                 setCallbackMsg(ERR_SASL_AUTHENTRY_NO_MAPPED_ENTRY.get(authid));
                 callback.setAuthorized(false);
                 return;
                }
            } catch (DirectoryException de) {
                if (debugEnabled()) {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }
                setCallbackMsg(ERR_SASL_CANNOT_MAP_AUTHENTRY.get(authid,
                        de.getMessage()));
                callback.setAuthorized(false);
                return;
            }
            userName=authid;
        }
        if (responseAuthzID.length() == 0) {
            setCallbackMsg(ERR_SASLDIGESTMD5_EMPTY_AUTHZID.get());
            callback.setAuthorized(false);
            return;
        } else if (!responseAuthzID.equals(userName))  {
            String lowerAuthzID = toLowerCase(responseAuthzID);
            //Process the callback differently depending on if the authzid
            //string begins with the string "dn:" or not.
            if (lowerAuthzID.startsWith("dn:")) {
                authzDNCheck(callback);
            } else {
                authzIDCheck(callback);
            }
        } else {
            authzEntry = authEntry;
            callback.setAuthorized(true);
        }
    }


    /**
     * Process the specified authorize callback. This method is called if the
     * callback's authorization ID does not begin with the string "dn:".
     *
     * @param callback The authorize callback to process.
     */
    private void authzIDCheck(AuthorizeCallback callback) {
        String  authzid = callback.getAuthorizationID();
        String lowerAuthzID = toLowerCase(authzid);
        String idStr;
        callback.setAuthorized(true);
        if (lowerAuthzID.startsWith("u:")) {
            idStr = authzid.substring(2);
        } else {
            idStr = authzid;
        }
        if (idStr.length() == 0) {
            authzEntry = null;
        } else {
            try {
                authzEntry = identityMapper.getEntryForID(idStr);
                if (authzEntry == null) {
                  setCallbackMsg(ERR_SASL_AUTHZID_NO_MAPPED_ENTRY.get(authzid));
                  callback.setAuthorized(false);
                  return;
                }
            } catch (DirectoryException e) {
                if (debugEnabled()) {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }
                setCallbackMsg(ERR_SASL_AUTHZID_NO_MAPPED_ENTRY.get(authzid));
                callback.setAuthorized(false);
                return;
            }
        }
        if ((authzEntry == null) ||
                (!authzEntry.getDN().equals(authEntry.getDN()))) {
            //Create temporary authorization information and run it both
            //through the privilege and then the access control subsystems.
            AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
                    DirectoryServer.isRootDN(authEntry.getDN()));
            if(!hasPrivilege(authInfo)) {
                callback.setAuthorized(false);
            } else {
                callback.setAuthorized(hasPermission(authInfo));
            }
        }
    }

    /**
     * Process the specified authorize callback. This method is called if the
     * callback's authorization ID begins with the string "dn:".
     *
     * @param callback The authorize callback to process.
     */
    private void authzDNCheck(AuthorizeCallback callback) {
        String  responseAuthzID = callback.getAuthorizationID();
        DN authzDN;
        callback.setAuthorized(true);
        try {
            authzDN = DN.decode(responseAuthzID.substring(3));
        } catch (DirectoryException e) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            setCallbackMsg(ERR_SASL_AUTHZID_INVALID_DN.get(responseAuthzID,
                                                        e.getMessageObject()));
            callback.setAuthorized(false);
            return;
        }
        DN actualAuthzDN =
            DirectoryServer.getActualRootBindDN(authzDN);
        if (actualAuthzDN != null) {
            authzDN = actualAuthzDN;
        }
        if (!authzDN.equals(authEntry.getDN())) {
            if (authzDN.isNullDN()) {
                authzEntry = null;
            } else {
                try {
                  if((authzEntry = DirectoryServer.getEntry(authzDN)) == null) {
                        setCallbackMsg(ERR_SASL_AUTHZID_NO_SUCH_ENTRY.get(
                                                      String.valueOf(authzDN)));
                        callback.setAuthorized(false);
                        return;
                    }
                } catch (DirectoryException e) {
                    if (debugEnabled()) {
                        TRACER.debugCaught(DebugLogLevel.ERROR, e);
                    }
                    setCallbackMsg(ERR_SASL_AUTHZID_CANNOT_GET_ENTRY
                          .get(String.valueOf(authzDN), e.getMessageObject()));
                    callback.setAuthorized(false);
                    return;
                }
            }
            AuthenticationInfo authInfo = new AuthenticationInfo(authEntry,
                                  DirectoryServer.isRootDN(authEntry.getDN()));
            if(!hasPrivilege(authInfo)) {
                callback.setAuthorized(false);
            } else
                callback.setAuthorized(hasPermission(authInfo));
        }
    }

    /**
     * Process the specified password callback. Used only for the DIGEST-MD5
     * SASL mechanism. The password callback is processed after the name
     * callback.
     *
     * @param passwordCallback The password callback to process.
     */
    private void passwordCallback(PasswordCallback passwordCallback) {
        //If there is no authEntry this is an error.
        if(authEntry == null) {
            setCallbackMsg(ERR_SASL_NO_MATCHING_ENTRIES.get(userName));
            return;
        }
        //Try to get a clear password to use.
        List<ByteString> clearPasswords;
        try {
          PasswordPolicyState pwPolicyState =
                                    new PasswordPolicyState(authEntry, false);
          clearPasswords = pwPolicyState.getClearPasswords();
          if ((clearPasswords == null) || clearPasswords.isEmpty()) {
              setCallbackMsg(
                 ERR_SASL_NO_REVERSIBLE_PASSWORDS.get(mechanism,
                                            String.valueOf(authEntry.getDN())));
            return;
          }
        }
        catch (Exception e) {
            if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
            setCallbackMsg(ERR_SASL_CANNOT_GET_REVERSIBLE_PASSWORDS.get(
                    String.valueOf(authEntry.getDN()),mechanism,
                    String.valueOf(e)));
          return;
        }
        //Use the first password.
        char[] password = clearPasswords.get(0).toString().toCharArray();
        passwordCallback.setPassword(password);
        return;
    }

    /**
     * Process the specified name callback. Used only for DIGEST-MD5 SASL
     * mechanism.
     *
     * @param nameCallback The name callback to process.
     */
    private void nameCallback(NameCallback nameCallback) {
        userName= nameCallback.getDefaultName();
        String lowerUserName = toLowerCase(userName);
        //Process the user name differently if it starts with the string "dn:".
        if (lowerUserName.startsWith("dn:")) {
            DN userDN;
            try {
                userDN = DN.decode(userName.substring(3));
            } catch (DirectoryException e) {
                if (debugEnabled()) {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }
                 setCallbackMsg(ERR_SASL_CANNOT_DECODE_USERNAME_AS_DN.get(
                                      mechanism,
                                      userName, e.getMessageObject()));
                return;
            }
            if (userDN.isNullDN()) {
              setCallbackMsg(ERR_SASL_USERNAME_IS_NULL_DN.get(
                                                      mechanism));
              return;
            }
            DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
            if (rootDN != null) {
                userDN = rootDN;
            }
            getAuthEntry(userDN);
        } else {
            //The entry name is not a DN, try to map it using the identity
            //mapper.
            String entryID = userName;
            if (lowerUserName.startsWith("u:")) {
                if (lowerUserName.equals("u:")) {
                    setCallbackMsg(ERR_SASL_ZERO_LENGTH_USERNAME.get(
                            mechanism,mechanism));
                    return;
                }
                entryID = userName.substring(2);
            }
            try {
                authEntry = identityMapper.getEntryForID(entryID);
            } catch (DirectoryException e) {
                if (debugEnabled()) {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                }
               setCallbackMsg(ERR_SASLDIGESTMD5_CANNOT_MAP_USERNAME.get(
                      String.valueOf(userName), e.getMessageObject()));
                return;
            }
        }
        if (authEntry == null) {
            //The authEntry is null, this is an error. The password callback
            //will catch this error. There is no way to stop the processing
            //from the name callback.
            return;
        }
    }

    /**
     * Try to get a entry from the directory using the specified DN. Used only
     * for DIGEST-MD5 SASL mechanism.
     *
     * @param userDN The DN of the entry to retrieve from the server.
     */
   private void getAuthEntry(DN userDN) {
       Lock readLock = null;
       for (int i=0; i < 3; i++) {
           readLock = LockManager.lockRead(userDN);
           if (readLock != null) {
               break;
           }
       }
       if (readLock == null) {
           setCallbackMsg(INFO_SASL_CANNOT_LOCK_ENTRY.get(
                                                       String.valueOf(userDN)));
           return;
       } try {
           authEntry = DirectoryServer.getEntry(userDN);
       } catch (DirectoryException e) {
           if (debugEnabled()) {
               TRACER.debugCaught(DebugLogLevel.ERROR, e);
           }
           setCallbackMsg(ERR_SASL_CANNOT_GET_ENTRY_BY_DN.get(
                   String.valueOf(userDN), SASL_MECHANISM_DIGEST_MD5,
                   e.getMessageObject()));
           return;
       } finally {
           LockManager.unlock(userDN, readLock);
       }
   }

   /**
    * The method performs all GSSAPI processing. It is run as the context of
    * the login context performed by the GSSAPI mechanism handler. See comments
    * for processing overview.
    * @return {@code true} if the authentication processing was successful.
    */
   public Boolean run() {
       ClientConnection clientConn = bindOp.getClientConnection();
       //If the SASL server is null then this is the first handshake and the
       //server needs to be initialized before any processing can be performed.
       //If the SASL server cannot be created then all processing is abandoned
       //and INVALID_CREDENTIALS is returned to the client.
       if(saslServer == null) {
           try {
               initSASLServer();
           } catch (SaslException e) {
               if (debugEnabled()) {
                   TRACER.debugCaught(DebugLogLevel.ERROR, e);
               }
               Message msg =
                   ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_DIGEST_MD5,
                                                     getExceptionMessage(e));
               clientConn.setSASLAuthStateInfo(null);
               bindOp.setAuthFailureReason(msg);
               bindOp.setResultCode(ResultCode.INVALID_CREDENTIALS);
               return false;
           }
       }
       byte[] clientCredBytes = new byte[0];
       ASN1OctetString clientCredentials = bindOp.getSASLCredentials();
       if(clientCredentials != null) {
           clientCredBytes = clientCredentials.value();
       }
       clientConn.setSASLAuthStateInfo(null);
       try {
           byte[] responseBytes =
               evaluateResponse(clientCredBytes);
           ASN1OctetString responseAuthStr =
               new ASN1OctetString(responseBytes);
           //If the bind has not been completed,then
           //more handshake is needed and SASL_BIND_IN_PROGRESS is returned back
           //to the client.
           if (isBindComplete()) {
               bindOp.setResultCode(ResultCode.SUCCESS);
               bindOp.setSASLAuthUserEntry(authEntry);
               AuthenticationInfo authInfo =
                    new AuthenticationInfo(authEntry, authzEntry,
                                    mechanism,
                                   DirectoryServer.isRootDN(authEntry.getDN()));
               bindOp.setAuthenticationInfo(authInfo);
               //If confidentiality/integrity has been negotiated then
               //create a SASL security provider and save it in the client
               //connection. If confidentiality/integrity has not been
               //negotiated, dispose of the SASL server.
               if(isConfidentialIntegrity()) {
                   SASLSecurityProvider secProvider =
                       new SASLSecurityProvider(clientConn, mechanism, this);
                   LDAPClientConnection ldapConn =
                       (LDAPClientConnection) clientConn;
                       ldapConn.setSASLConnectionSecurityProvider(secProvider);
               } else {
                   dispose();
                   clientConn.setSASLAuthStateInfo(null);
               }
           } else {
               bindOp.setServerSASLCredentials(responseAuthStr);
               clientConn.setSASLAuthStateInfo(this);
               bindOp.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
           }
       } catch (SaslException e) {
           if (debugEnabled()) {
               TRACER.debugCaught(DebugLogLevel.ERROR, e);
           }
           Message msg =
               ERR_SASL_PROTOCOL_ERROR.get(mechanism, getExceptionMessage(e));
           handleError(msg);
           return false;
       }
       return true;
   }


   /**
    * Perform the authentication as the specified login context. The specified
    * bind operation needs to be saved so the callbacks have access to it.
    * Only used by the GSSAPI mechanism.
    *
    * @param loginContext The login context to perform the authentication
    *                     as.
    * @param bindOp The bind operation needed by the callbacks to process the
    *               authentication.
    */
   void
   performAuthentication(LoginContext loginContext, BindOperation bindOp) {
       this.bindOp = bindOp;
       try {
           Subject.doAs(loginContext.getSubject(), this);
       } catch (PrivilegedActionException e) {
           if (debugEnabled()) {
               TRACER.debugCaught(DebugLogLevel.ERROR, e);
           }
           Message msg =
               ERR_SASL_PROTOCOL_ERROR.get(mechanism, getExceptionMessage(e));
           handleError(msg);
       }
   }

   /**
    * Process the initial stage of a DIGEST-MD5 SASL bind using the specified
    * bind operation.
    *
    * @param bindOp The bind operation to use in processing.
    */
   void
   evaluateInitialStage(BindOperation bindOp) {
       this.bindOp = bindOp;
       ClientConnection clientConn = bindOp.getClientConnection();
       try {
           byte[] challengeBuffer = evaluateResponse(new byte[0]);
           ASN1OctetString challenge = new ASN1OctetString(challengeBuffer);
           bindOp.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
           bindOp.setServerSASLCredentials(challenge);
           clientConn.setSASLAuthStateInfo(this);
       } catch (SaslException e) {
           if (debugEnabled()) {
               TRACER.debugCaught(DebugLogLevel.ERROR, e);
           }
           Message msg =
               ERR_SASL_PROTOCOL_ERROR.get(mechanism,getExceptionMessage(e));
           handleError(msg);
       }
   }

   /**
    * Evaluate the final stage of a DIGEST-MD5 SASL bind using the specified
    * bind operation.
    *
    * @param bindOp The bind operation to use in processing.
    */
   void
   evaluateFinalStage(BindOperation bindOp) {
      this.bindOp = bindOp;
       ASN1OctetString clientCredentials = bindOp.getSASLCredentials();
       if ((clientCredentials == null) ||
               (clientCredentials.value().length == 0)) {
           Message msg =
               ERR_SASL_NO_CREDENTIALS.get(mechanism, mechanism);
           handleError(msg);
           return;
       }
       ClientConnection clientConn = bindOp.getClientConnection();
       clientConn.setSASLAuthStateInfo(null);
       try {
           byte[] responseBytes =
                        evaluateResponse(clientCredentials.value());
           ASN1OctetString responseAuthStr =
               new ASN1OctetString(responseBytes);
           bindOp.setResultCode(ResultCode.SUCCESS);
           bindOp.setServerSASLCredentials(responseAuthStr);
           bindOp.setSASLAuthUserEntry(authEntry);
           AuthenticationInfo authInfo =
                new AuthenticationInfo(authEntry, authzEntry,
                                       mechanism,
                                  DirectoryServer.isRootDN(authEntry.getDN()));
           bindOp.setAuthenticationInfo(authInfo);
           //If confidentiality/integrity has been negotiated, then create a
           //SASL security provider and save it in the client connection for
           //use in later processing.
           if(isConfidentialIntegrity()) {
               SASLSecurityProvider secProvider =
                   new SASLSecurityProvider(clientConn, mechanism, this);
               LDAPClientConnection ldapConn =
                   (LDAPClientConnection) clientConn;
               ldapConn.setSASLConnectionSecurityProvider(secProvider);
           } else {
               dispose();
               clientConn.setSASLAuthStateInfo(null);
           }
       } catch (SaslException e) {
           if (debugEnabled()) {
               TRACER.debugCaught(DebugLogLevel.ERROR, e);
           }
           Message msg =
               ERR_SASL_PROTOCOL_ERROR.get(mechanism, getExceptionMessage(e));
           handleError(msg);
       }
   }
}