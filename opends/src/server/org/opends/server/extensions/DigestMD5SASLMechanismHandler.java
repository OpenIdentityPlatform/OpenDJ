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



import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.DigestMD5SASLMechanismHandlerCfg;
import org.opends.server.admin.std.server.SASLMechanismHandlerCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Base64;

import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a SASL mechanism that uses digest
 * authentication via DIGEST-MD5.  This is a password-based mechanism that does
 * not expose the password itself over the wire but rather uses an MD5 hash that
 * proves the client knows the password.  This is similar to the CRAM-MD5
 * mechanism, and the primary differences are that CRAM-MD5 only obtains random
 * data from the server whereas DIGEST-MD5 uses random data from both the
 * server and the client, CRAM-MD5 does not allow for an authorization ID in
 * addition to the authentication ID where DIGEST-MD5 does, and CRAM-MD5 does
 * not define any integrity and confidentiality mechanisms where DIGEST-MD5
 * does.  This implementation is based on the specification in RFC 2831 and
 * updates from draft-ietf-sasl-rfc2831bis-06.
 */
public class DigestMD5SASLMechanismHandler
       extends SASLMechanismHandler<DigestMD5SASLMechanismHandlerCfg>
       implements ConfigurationChangeListener<
                       DigestMD5SASLMechanismHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current configuration for this SASL mechanism handler.
  private DigestMD5SASLMechanismHandlerCfg currentConfig;

  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The identity mapper that will be used to map ID strings to user entries.
  private IdentityMapper identityMapper;

  // The message digest engine that will be used to create the MD5 digests.
  private MessageDigest md5Digest;

  // The lock that will be used to provide threadsafe access to the message
  // digest.
  private ReentrantLock digestLock;

  // The random number generator that we will use to create the nonce.
  private SecureRandom randomGenerator;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public DigestMD5SASLMechanismHandler()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(
                   DigestMD5SASLMechanismHandlerCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addDigestMD5ChangeListener(this);

    currentConfig = configuration;
    configEntryDN = configuration.dn();


    // Initialize the variables needed for the MD5 digest creation.
    digestLock      = new ReentrantLock();
    randomGenerator = new SecureRandom();

    try
    {
      md5Digest = MessageDigest.getInstance("MD5");
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_GET_MESSAGE_DIGEST;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new InitializationException(msgID, message, e);
    }


    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
    if (identityMapper == null)
    {
      int    msgID   = MSGID_SASLDIGESTMD5_NO_SUCH_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(identityMapperDN),
                                  String.valueOf(configEntryDN));
      throw new ConfigException(msgID, message);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5,
                                                 this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    currentConfig.removeDigestMD5ChangeListener(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {
    DigestMD5SASLMechanismHandlerCfg config = currentConfig;
    IdentityMapper identityMapper = this.identityMapper;
    String realm = config.getRealm();


    // The DIGEST-MD5 bind process uses two stages.  See if the client provided
    // any credentials.  If not, then this is an initial authentication so we
    // will send a challenge to the client.
    ByteString       clientCredentials = bindOperation.getSASLCredentials();
    ClientConnection clientConnection  = bindOperation.getClientConnection();
    if ((clientCredentials == null) || (clientCredentials.value().length == 0))
    {
      // Create a buffer to hold the challenge.
      StringBuilder challengeBuffer = new StringBuilder();


      // Add the realm to the challenge.  If we have a configured realm, then
      // use it.  Otherwise, add a realm for each suffix defined in the server.
      if (realm == null)
      {
        Map<DN,Backend> suffixes = DirectoryServer.getPublicNamingContexts();
        if (! suffixes.isEmpty())
        {
          Iterator<DN> iterator = suffixes.keySet().iterator();
          challengeBuffer.append("realm=\"");
          challengeBuffer.append(iterator.next().toNormalizedString());
          challengeBuffer.append("\"");

          while (iterator.hasNext())
          {
            challengeBuffer.append(",realm=\"");
            challengeBuffer.append(iterator.next().toNormalizedString());
            challengeBuffer.append("\"");
          }
        }
      }
      else
      {
        challengeBuffer.append("realm=\"");
        challengeBuffer.append(realm);
        challengeBuffer.append("\"");
      }


      // Generate the nonce.  Add it to the challenge and remember it for future
      // use.
      String nonce = generateNonce();
      if (challengeBuffer.length() > 0)
      {
        challengeBuffer.append(",");
      }
      challengeBuffer.append("nonce=\"");
      challengeBuffer.append(nonce);
      challengeBuffer.append("\"");


      // Generate the qop-list and add it to the challenge.
      // FIXME -- Add support for integrity and confidentiality.  Once we do,
      //          we'll also want to add the maxbuf and cipher options.
      challengeBuffer.append(",qop=\"auth\"");


      // Add the charset option to indicate that we support UTF-8 values.
      challengeBuffer.append(",charset=utf-8");


      // Add the algorithm, which will always be "md5-sess".
      challengeBuffer.append(",algorithm=md5-sess");


      // Encode the challenge as an ASN.1 element.  The total length of the
      // encoded value must be less than 2048 bytes, which should not be a
      // problem, but we'll add a safety check just in case....  In the event
      // that it does happen, we'll also log an error so it is more noticeable.
      ASN1OctetString challenge =
           new ASN1OctetString(challengeBuffer.toString());
      if (challenge.value().length >= 2048)
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_CHALLENGE_TOO_LONG;
        String message = getMessage(msgID, challenge.value().length);
        bindOperation.setAuthFailureReason(msgID, message);

        logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
        return;
      }


      // Store the state information with the client connection so we can use it
      // for later validation.
      DigestMD5StateInfo stateInfo = new DigestMD5StateInfo(nonce, "00000000");
      clientConnection.setSASLAuthStateInfo(stateInfo);


      // Prepare the response and return so it will be sent to the client.
      bindOperation.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
      bindOperation.setServerSASLCredentials(challenge);
      return;
    }


    // If we've gotten here, then the client did provide credentials.  This can
    // be either an initial or subsequent authentication, but they will both be
    // handled identically.  First, get the stored client SASL state.  If it's
    // not there, then fail.
    Object saslStateInfo = clientConnection.getSASLAuthStateInfo();
    if (saslStateInfo == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_STORED_STATE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    if (! (saslStateInfo instanceof DigestMD5StateInfo))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_INVALID_STORED_STATE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    DigestMD5StateInfo stateInfo = (DigestMD5StateInfo) saslStateInfo;


    // Create variables to hold values stored in the client's response.  We'll
    // also store the base DN because we might need to override it later.
    String responseUserName      = null;
    String responseRealm         = null;
    String responseNonce         = null;
    String responseCNonce        = null;
    int    responseNonceCount    = -1;
    String responseNonceCountStr = null;
    String responseQoP           = "auth";
    String responseDigestURI     = null;
    byte[] responseDigest        = null;
    String responseCharset       = "ISO-8859-1";
    String responseAuthzID       = null;


    // Get a temporary string representation of the SASL credentials using the
    // ISO-8859-1 encoding and see if it contains "charset=utf-8".  If so, then
    // re-parse the credentials using that character set.
    byte[] credBytes  = clientCredentials.value();
    String credString = null;
    String lowerCreds = null;
    try
    {
      credString = new String(credBytes, responseCharset);
      lowerCreds = toLowerCase(credString);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This isn't necessarily fatal because we're going to retry using UTF-8,
      // but we want to log it anyway.
      logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.SEVERE_WARNING,
               MSGID_SASLDIGESTMD5_CANNOT_PARSE_ISO_CREDENTIALS,
               responseCharset, getExceptionMessage(e));
    }

    if ((credString == null) ||
        (lowerCreds.indexOf("charset=utf-8") >= 0))
    {
      try
      {
        credString = new String(credBytes, "UTF-8");
        lowerCreds = toLowerCase(credString);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        // This is fatal because either we can't parse the credentials as a
        // string at all, or we know we need to do so using UTF-8 and can't.
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_PARSE_UTF8_CREDENTIALS;
        String message = getMessage(msgID, getExceptionMessage(e));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }


    // Iterate through the credentials string, parsing the property names and
    // their corresponding values.
    int pos    = 0;
    int length = credString.length();
    while (pos < length)
    {
      int equalPos = credString.indexOf('=', pos+1);
      if (equalPos < 0)
      {
        // This is bad because we're not at the end of the string but we don't
        // have a name/value delimiter.
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_INVALID_TOKEN_IN_CREDENTIALS;
        String message = getMessage(msgID, pos);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }


      String tokenName  = lowerCreds.substring(pos, equalPos);

      String tokenValue;
      try
      {
        StringBuilder valueBuffer = new StringBuilder();
        pos = readToken(credString, equalPos+1, length, valueBuffer);
        tokenValue = valueBuffer.toString();
      }
      catch (DirectoryException de)
      {
        // We couldn't parse the token value, so it must be malformed.
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);
        bindOperation.setAuthFailureReason(de.getMessageID(),
                                           de.getErrorMessage());
        return;
      }

      if (tokenName.equals("charset"))
      {
        // The value must be the string "utf-8".  If not, that's an error.
        if (! tokenValue.equalsIgnoreCase("utf-8"))
        {
          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_INVALID_CHARSET;
          String message = getMessage(msgID, tokenValue);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }
      }
      else if (tokenName.equals("username"))
      {
        responseUserName = tokenValue;
      }
      else if (tokenName.equals("realm"))
      {
        responseRealm = tokenValue;
        if (realm != null)
        {
          if (! responseRealm.equals(realm))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int   msgID   = MSGID_SASLDIGESTMD5_INVALID_REALM;
            String message = getMessage(msgID, responseRealm);
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
      }
      else if (tokenName.equals("nonce"))
      {
        responseNonce = tokenValue;
        String requestNonce = stateInfo.getNonce();
        if (! responseNonce.equals(requestNonce))
        {
          // The nonce provided by the client is incorrect.  This could be an
          // attempt at a replay or chosen plaintext attack, so we'll close the
          // connection.  We will put a message in the log but will not send it
          // to the client.
          int    msgID   = MSGID_SASLDIGESTMD5_INVALID_NONCE;
          String message = getMessage(msgID);
          clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM, false,
                                      msgID, message);
          return;
        }
      }
      else if (tokenName.equals("cnonce"))
      {
        responseCNonce = tokenValue;
      }
      else if (tokenName.equals("nc"))
      {
        try
        {
          responseNonceCountStr = tokenValue;
          responseNonceCount    = Integer.parseInt(responseNonceCountStr, 16);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_DECODE_NONCE_COUNT;
          String message = getMessage(msgID, tokenValue);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }

        int storedNonce;
        try
        {
          storedNonce = Integer.parseInt(stateInfo.getNonceCount(), 16);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int msgID = MSGID_SASLDIGESTMD5_CANNOT_DECODE_STORED_NONCE_COUNT;
          String message = getMessage(msgID, getExceptionMessage(e));
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }

        if (responseNonceCount != (storedNonce + 1))
        {
          // The nonce count provided by the client is incorrect.  This
          // indicates a replay attack, so we'll close the connection.  We will
          // put a message in the log but we will not send it to the client.
          int    msgID   = MSGID_SASLDIGESTMD5_INVALID_NONCE_COUNT;
          String message = getMessage(msgID);
          clientConnection.disconnect(DisconnectReason.SECURITY_PROBLEM, false,
                                      msgID, message);
          return;
        }
      }
      else if (tokenName.equals("qop"))
      {
        responseQoP = tokenValue;

        if (responseQoP.equals("auth"))
        {
          // No action necessary.
        }
        else if (responseQoP.equals("auth-int"))
        {
          // FIXME -- Add support for integrity protection.
          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_INTEGRITY_NOT_SUPPORTED;
          String message = getMessage(msgID);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }
        else if (responseQoP.equals("auth-conf"))
        {
          // FIXME -- Add support for confidentiality protection.
          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_CONFIDENTIALITY_NOT_SUPPORTED;
          String message = getMessage(msgID);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }
        else
        {
          // This is an invalid QoP value.
          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_INVALID_QOP;
          String message = getMessage(msgID, responseQoP);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }
      }
      else if (tokenName.equals("digest-uri"))
      {
        responseDigestURI = tokenValue;

        String serverFQDN = config.getServerFqdn();
        if ((serverFQDN != null) && (serverFQDN.length() > 0))
        {
          // If a server FQDN is populated, then we'll use it to validate the
          // digest-uri, which should be in the form "ldap/serverfqdn".
          String expectedDigestURI = "ldap/" + serverFQDN;
          if (! expectedDigestURI.equalsIgnoreCase(responseDigestURI))
          {
            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLDIGESTMD5_INVALID_DIGEST_URI;
            String message = getMessage(msgID, responseDigestURI,
                                        expectedDigestURI);
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }
        }
      }
      else if (tokenName.equals("response"))
      {
        try
        {
          responseDigest = hexStringToByteArray(tokenValue);
        }
        catch (ParseException pe)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, pe);
          }

          int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_PARSE_RESPONSE_DIGEST;
          String message = getMessage(msgID, getExceptionMessage(pe));
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }
      }
      else if (tokenName.equals("authzid"))
      {
        responseAuthzID = tokenValue;

        // FIXME -- This must always be parsed in UTF-8 even if the charset for
        // other elements is ISO 8859-1.
      }
      else if (tokenName.equals("maxbuf") || tokenName.equals("cipher"))
      {
        // FIXME -- Add support for confidentiality and integrity protection.
      }
      else
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_INVALID_RESPONSE_TOKEN;
        String message = getMessage(msgID, tokenName);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }


    // Make sure that all required properties have been specified.
    if ((responseUserName == null) || (responseUserName.length() == 0))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_USERNAME_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else if (responseNonce == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_NONCE_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else if (responseCNonce == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_CNONCE_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else if (responseNonceCount < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_NONCE_COUNT_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else if (responseDigestURI == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_DIGEST_URI_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else if (responseDigest == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_DIGEST_IN_RESPONSE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // If a realm has not been specified, then use the empty string.
    // FIXME -- Should we reject this if a specific realm is defined?
    if (responseRealm == null)
    {
      responseRealm = "";
    }


    // Get the user entry for the authentication ID.  Allow for an
    // authentication ID that is just a username (as per the DIGEST-MD5 spec),
    // but also allow a value in the authzid form specified in RFC 2829.
    Entry  userEntry    = null;
    String lowerUserName = toLowerCase(responseUserName);
    if (lowerUserName.startsWith("dn:"))
    {
      // Try to decode the user DN and retrieve the corresponding entry.
      DN userDN;
      try
      {
        userDN = DN.decode(responseUserName.substring(3));
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_DECODE_USERNAME_AS_DN;
        String message = getMessage(msgID, responseUserName,
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      if (userDN.isNullDN())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_USERNAME_IS_NULL_DN;
        String message = getMessage(msgID);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
      if (rootDN != null)
      {
        userDN = rootDN;
      }

      // Acquire a read lock on the user entry.  If this fails, then so will the
      // authentication.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(userDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());

        int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_LOCK_ENTRY;
        String message = getMessage(msgID, String.valueOf(userDN));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      try
      {
        userEntry = DirectoryServer.getEntry(userDN);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_GET_ENTRY_BY_DN;
        String message = getMessage(msgID, String.valueOf(userDN),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
      finally
      {
        LockManager.unlock(userDN, readLock);
      }
    }
    else
    {
      // Use the identity mapper to resolve the username to an entry.
      String userName = responseUserName;
      if (lowerUserName.startsWith("u:"))
      {
        if (lowerUserName.equals("u:"))
        {
          bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

          int    msgID   = MSGID_SASLDIGESTMD5_ZERO_LENGTH_USERNAME;
          String message = getMessage(msgID);
          bindOperation.setAuthFailureReason(msgID, message);
          return;
        }

        userName = responseUserName.substring(2);
      }


      try
      {
        userEntry = identityMapper.getEntryForID(userName);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_MAP_USERNAME;
        String message = getMessage(msgID, String.valueOf(responseUserName),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }


    // At this point, we should have a user entry.  If we don't then fail.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_NO_MATCHING_ENTRIES;
      String message = getMessage(msgID, responseUserName);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    Entry authZEntry = userEntry;
    if (responseAuthzID != null)
    {
      if (responseAuthzID.length() == 0)
      {
        // The authorization ID must not be an empty string.
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLDIGESTMD5_EMPTY_AUTHZID;
        String message = getMessage(msgID);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
      else if (! responseAuthzID.equals(responseUserName))
      {
        String lowerAuthzID = toLowerCase(responseAuthzID);

        if (lowerAuthzID.startsWith("dn:"))
        {
          DN authzDN;
          try
          {
            authzDN = DN.decode(responseAuthzID.substring(3));
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

            int    msgID   = MSGID_SASLDIGESTMD5_AUTHZID_INVALID_DN;
            String message = getMessage(msgID, responseAuthzID,
                                        de.getErrorMessage());
            bindOperation.setAuthFailureReason(msgID, message);
            return;
          }

          DN actualAuthzDN = DirectoryServer.getActualRootBindDN(authzDN);
          if (actualAuthzDN != null)
          {
            authzDN = actualAuthzDN;
          }

          if (! authzDN.equals(userEntry.getDN()))
          {
            AuthenticationInfo tempAuthInfo =
              new AuthenticationInfo(userEntry,
                       DirectoryServer.isRootDN(userEntry.getDN()));
            InternalClientConnection tempConn =
                 new InternalClientConnection(tempAuthInfo);
            if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_SASLDIGESTMD5_AUTHZID_INSUFFICIENT_PRIVILEGES;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }

            if (authzDN.isNullDN())
            {
              authZEntry = null;
            }
            else
            {
              try
              {
                authZEntry = DirectoryServer.getEntry(authzDN);
                if (authZEntry == null)
                {
                  bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                  int msgID = MSGID_SASLDIGESTMD5_AUTHZID_NO_SUCH_ENTRY;
                  String message = getMessage(msgID, String.valueOf(authzDN));
                  bindOperation.setAuthFailureReason(msgID, message);
                  return;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                int msgID = MSGID_SASLDIGESTMD5_AUTHZID_CANNOT_GET_ENTRY;
                String message = getMessage(msgID, String.valueOf(authzDN),
                                            de.getErrorMessage());
                bindOperation.setAuthFailureReason(msgID, message);
                return;
              }
            }
          }
        }
        else
        {
          String idStr;
          if (lowerAuthzID.startsWith("u:"))
          {
            idStr = responseAuthzID.substring(2);
          }
          else
          {
            idStr = responseAuthzID;
          }

          if (idStr.length() == 0)
          {
            authZEntry = null;
          }
          else
          {
            try
            {
              authZEntry = identityMapper.getEntryForID(idStr);
              if (authZEntry == null)
              {
                bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

                int    msgID   = MSGID_SASLDIGESTMD5_AUTHZID_NO_MAPPED_ENTRY;
                String message = getMessage(msgID, responseAuthzID);
                bindOperation.setAuthFailureReason(msgID, message);
                return;
              }
            }
            catch (DirectoryException de)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, de);
              }

              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_MAP_AUTHZID;
              String message = getMessage(msgID, responseAuthzID,
                                          de.getErrorMessage());
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }

          if ((authZEntry == null) ||
              (! authZEntry.getDN().equals(userEntry.getDN())))
          {
            AuthenticationInfo tempAuthInfo =
              new AuthenticationInfo(userEntry,
                       DirectoryServer.isRootDN(userEntry.getDN()));
            InternalClientConnection tempConn =
                 new InternalClientConnection(tempAuthInfo);
            if (! tempConn.hasPrivilege(Privilege.PROXIED_AUTH, bindOperation))
            {
              bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_SASLDIGESTMD5_AUTHZID_INSUFFICIENT_PRIVILEGES;
              String message = getMessage(msgID,
                                          String.valueOf(userEntry.getDN()));
              bindOperation.setAuthFailureReason(msgID, message);
              return;
            }
          }
        }
      }
    }


    // Get the clear-text passwords from the user entry, if there are any.
    List<ByteString> clearPasswords;
    try
    {
      PasswordPolicyState pwPolicyState =
           new PasswordPolicyState(userEntry, false, false);
      clearPasswords = pwPolicyState.getClearPasswords();
      if ((clearPasswords == null) || clearPasswords.isEmpty())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int msgID = MSGID_SASLDIGESTMD5_NO_REVERSIBLE_PASSWORDS;
        String message = getMessage(msgID, String.valueOf(userEntry.getDN()));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }
    catch (Exception e)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_GET_REVERSIBLE_PASSWORDS;
      String message = getMessage(msgID, String.valueOf(userEntry.getDN()),
                                  String.valueOf(e));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Iterate through the clear-text values and see if any of them can be used
    // in conjunction with the challenge to construct the provided digest.
    boolean matchFound    = false;
    byte[]  passwordBytes = null;
    for (ByteString clearPassword : clearPasswords)
    {
      byte[] generatedDigest;
      try
      {
        generatedDigest =
             generateResponseDigest(responseUserName, responseAuthzID,
                                    clearPassword.value(), responseRealm,
                                    responseNonce, responseCNonce,
                                    responseNonceCountStr, responseDigestURI,
                                    responseQoP, responseCharset);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(ErrorLogCategory.EXTENSIONS,
                 ErrorLogSeverity.SEVERE_WARNING,
                 MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_DIGEST,
                 getExceptionMessage(e));
        continue;
      }

      if (Arrays.equals(responseDigest, generatedDigest))
      {
        matchFound    = true;
        passwordBytes = clearPassword.value();
        break;
      }
    }

    if (! matchFound)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_INVALID_CREDENTIALS;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Generate the response auth element to include in the response to the
    // client.
    byte[] responseAuth;
    try
    {
      responseAuth =
           generateResponseAuthDigest(responseUserName, responseAuthzID,
                                      passwordBytes, responseRealm,
                                      responseNonce, responseCNonce,
                                      responseNonceCountStr, responseDigestURI,
                                      responseQoP, responseCharset);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLDIGESTMD5_CANNOT_GENERATE_RESPONSE_AUTH_DIGEST;
      String message = getMessage(msgID, getExceptionMessage(e));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    ASN1OctetString responseAuthStr =
         new ASN1OctetString("rspauth=" + getHexString(responseAuth));


    // Make sure to store the updated nonce count with the client connection to
    // allow for correct subsequent authentication.
    stateInfo.setNonceCount(responseNonceCountStr);


    // If we've gotten here, then the authentication was successful.  We'll also
    // need to include the response auth string in the server SASL credentials.
    bindOperation.setResultCode(ResultCode.SUCCESS);
    bindOperation.setServerSASLCredentials(responseAuthStr);


    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry, authZEntry,
                                SASL_MECHANISM_DIGEST_MD5,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    return;
  }



  /**
   * Generates a new nonce value to use during the DIGEST-MD5 authentication
   * process.
   *
   * @return  The nonce that should be used for DIGEST-MD5 authentication.
   */
  private String generateNonce()
  {
    byte[] nonceBytes = new byte[16];

    digestLock.lock();

    try
    {
      randomGenerator.nextBytes(nonceBytes);
    }
    finally
    {
      digestLock.unlock();
    }

    return Base64.encode(nonceBytes);
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
   * @throws  DirectoryException  If a problem occurs while attempting to read
   *                              the token.
   */
  private int readToken(String credentials, int startPos, int length,
                        StringBuilder token)
          throws DirectoryException
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
              int    msgID   = MSGID_SASLDIGESTMD5_INVALID_CLOSING_QUOTE_POS;
              String message = getMessage(msgID, (pos-2));
              throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                                           message, msgID);
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
   * Generates the appropriate DIGEST-MD5 response for the provided set of
   * information.
   *
   * @param  userName    The username from the authentication request.
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
  public byte[] generateResponseDigest(String userName, String authzID,
                                       byte[] password, String realm,
                                       String nonce, String cnonce,
                                       String nonceCount, String digestURI,
                                       String qop, String charset)
         throws UnsupportedEncodingException
  {
    digestLock.lock();

    try
    {
      // First, get a hash of "username:realm:password".
      StringBuilder a1String1 = new StringBuilder();
      a1String1.append(userName);
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
      byte[] a2Bytes = ("AUTHENTICATE:" + digestURI).getBytes(charset);
      byte[] a2Hash  = md5Digest.digest(a2Bytes);


      // Get hex string representations of the last two hashes.
      String a1HashHex = getHexString(a1Hash);
      String a2HashHex = getHexString(a2Hash);


      // Put together the final string to hash, consisting of
      // "a1HashHex:nonce:nonceCount:cnonce:qop:a2HashHex" and get its digest.
      StringBuilder kdString = new StringBuilder();
      kdString.append(a1HashHex);
      kdString.append(':');
      kdString.append(nonce);
      kdString.append(':');
      kdString.append(nonceCount);
      kdString.append(':');
      kdString.append(cnonce);
      kdString.append(':');
      kdString.append(qop);
      kdString.append(':');
      kdString.append(a2HashHex);
      return md5Digest.digest(kdString.toString().getBytes(charset));
    }
    finally
    {
      digestLock.unlock();
    }
  }



  /**
   * Generates the appropriate DIGEST-MD5 rspauth digest using the provided
   * information.
   *
   * @param  userName    The username from the authentication request.
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
  public byte[] generateResponseAuthDigest(String userName, String authzID,
                                           byte[] password, String realm,
                                           String nonce, String cnonce,
                                           String nonceCount, String digestURI,
                                           String qop, String charset)
         throws UnsupportedEncodingException
  {
    digestLock.lock();

    try
    {
      // First, get a hash of "username:realm:password".
      StringBuilder a1String1 = new StringBuilder();
      a1String1.append(userName);
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
      StringBuilder kdString = new StringBuilder();
      kdString.append(a1HashHex);
      kdString.append(':');
      kdString.append(nonce);
      kdString.append(':');
      kdString.append(nonceCount);
      kdString.append(':');
      kdString.append(cnonce);
      kdString.append(':');
      kdString.append(qop);
      kdString.append(':');
      kdString.append(a2HashHex);
      return md5Digest.digest(kdString.toString().getBytes(charset));
    }
    finally
    {
      digestLock.unlock();
    }
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
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    // This is a password-based mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    // This may be considered a secure mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(
                      SASLMechanismHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    DigestMD5SASLMechanismHandlerCfg config =
         (DigestMD5SASLMechanismHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      DigestMD5SASLMechanismHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    boolean configAcceptable = true;
    DN cfgEntryDN = configuration.dn();

    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    IdentityMapper newIdentityMapper =
         DirectoryServer.getIdentityMapper(identityMapperDN);
    if (newIdentityMapper == null)
    {
      int    msgID   = MSGID_SASLDIGESTMD5_NO_SUCH_IDENTITY_MAPPER;
      unacceptableReasons.add(getMessage(msgID,
                                         String.valueOf(identityMapperDN),
                                         String.valueOf(cfgEntryDN)));
      configAcceptable = false;
    }


    return configAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
              DigestMD5SASLMechanismHandlerCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Get the identity mapper that should be used to find users.
    DN identityMapperDN = configuration.getIdentityMapperDN();
    IdentityMapper newIdentityMapper =
         DirectoryServer.getIdentityMapper(identityMapperDN);
    if (newIdentityMapper == null)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }

      int    msgID   = MSGID_SASLDIGESTMD5_NO_SUCH_IDENTITY_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(identityMapperDN),
                              String.valueOf(configEntryDN)));
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      identityMapper = newIdentityMapper;
      currentConfig  = configuration;
    }


   return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

