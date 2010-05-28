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

package org.opends.sdk.requests;



import static com.sun.opends.sdk.messages.Messages.ERR_SASL_PROTOCOL_ERROR;
import static com.sun.opends.sdk.util.StaticUtils.getExceptionMessage;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.*;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.Responses;

import com.sun.opends.sdk.util.Validator;



/**
 * Digest-MD5 SASL bind request implementation.
 */
final class DigestMD5SASLBindRequestImpl extends
    AbstractSASLBindRequest<DigestMD5SASLBindRequest> implements
    DigestMD5SASLBindRequest
{
  private final static class Client extends SASLBindClientImpl
  {
    private final SaslClient saslClient;
    private final String authenticationID;
    private final ByteString password;
    private final String realm;



    private Client(final DigestMD5SASLBindRequestImpl initialBindRequest,
        final String serverName) throws ErrorResultException
    {
      super(initialBindRequest);

      this.authenticationID = initialBindRequest.getAuthenticationID();
      this.password = initialBindRequest.getPassword();
      this.realm = initialBindRequest.getRealm();

      final Map<String, String> props = new HashMap<String, String>();
      props.put(Sasl.QOP, "auth-conf,auth-int,auth");

      try
      {
        saslClient = Sasl.createSaslClient(
            new String[] { SASL_MECHANISM_NAME }, initialBindRequest
                .getAuthorizationID(), SASL_DEFAULT_PROTOCOL, serverName,
            props, this);
        if (saslClient.hasInitialResponse())
        {
          setNextSASLCredentials(saslClient.evaluateChallenge(new byte[0]));
        }
        else
        {
          setNextSASLCredentials((ByteString) null);
        }
      }
      catch (final SaslException e)
      {
        throw ErrorResultException.wrap(Responses.newResult(
            ResultCode.CLIENT_SIDE_LOCAL_ERROR).setCause(e));
      }
    }



    @Override
    public void dispose()
    {
      try
      {
        saslClient.dispose();
      }
      catch (final SaslException ignored)
      {
        // Ignore the SASL exception.
      }
    }



    @Override
    public boolean evaluateResult(final BindResult result)
        throws ErrorResultException
    {
      if (result.getResultCode() == ResultCode.SASL_BIND_IN_PROGRESS
          && result.getServerSASLCredentials() != null)
      {
        try
        {
          setNextSASLCredentials(saslClient.evaluateChallenge(result
              .getServerSASLCredentials().toByteArray()));
          return false;
        }
        catch (final SaslException e)
        {
          // FIXME: I18N need to have a better error message.
          // FIXME: Is this the best result code?
          throw ErrorResultException.wrap(Responses.newResult(
              ResultCode.CLIENT_SIDE_LOCAL_ERROR).setDiagnosticMessage(
              "An error occurred during multi-stage authentication")
              .setCause(e));
        }
      }
      return true;
    }



    @Override
    public ConnectionSecurityLayer getConnectionSecurityLayer()
    {
      final String qop = (String) saslClient.getNegotiatedProperty(Sasl.QOP);
      if (qop.equalsIgnoreCase("auth-int") || qop.equalsIgnoreCase("auth-conf"))
      {
        return this;
      }
      else
      {
        return null;
      }
    }



    @Override
    public byte[] unwrap(final byte[] incoming, final int offset, final int len)
        throws ErrorResultException
    {
      try
      {
        return saslClient.unwrap(incoming, offset, len);
      }
      catch (final SaslException e)
      {
        final LocalizableMessage msg = ERR_SASL_PROTOCOL_ERROR.get(
            SASL_MECHANISM_NAME, getExceptionMessage(e));
        throw ErrorResultException.wrap(Responses.newResult(
            ResultCode.CLIENT_SIDE_DECODING_ERROR).setDiagnosticMessage(
            msg.toString()).setCause(e));
      }
    }



    @Override
    public byte[] wrap(final byte[] outgoing, final int offset, final int len)
        throws ErrorResultException
    {
      try
      {
        return saslClient.wrap(outgoing, offset, len);
      }
      catch (final SaslException e)
      {
        final LocalizableMessage msg = ERR_SASL_PROTOCOL_ERROR.get(
            SASL_MECHANISM_NAME, getExceptionMessage(e));
        throw ErrorResultException.wrap(Responses.newResult(
            ResultCode.CLIENT_SIDE_ENCODING_ERROR).setDiagnosticMessage(
            msg.toString()).setCause(e));
      }
    }



    @Override
    void handle(final NameCallback callback)
        throws UnsupportedCallbackException
    {
      callback.setName(authenticationID);
    }



    @Override
    void handle(final PasswordCallback callback)
        throws UnsupportedCallbackException
    {
      callback.setPassword(password.toString().toCharArray());
    }



    @Override
    void handle(final RealmCallback callback)
        throws UnsupportedCallbackException
    {
      if (realm == null)
      {
        callback.setText(callback.getDefaultText());
      }
      else
      {
        callback.setText(realm);
      }
    }

  }



  private String authenticationID;
  private String authorizationID = null;
  private ByteString password;

  private String realm = null;



  DigestMD5SASLBindRequestImpl(final String authenticationID,
      final ByteString password)
  {
    Validator.ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.password = password;
  }



  /**
   * {@inheritDoc}
   */
  public BindClient createBindClient(final String serverName)
      throws ErrorResultException
  {
    return new Client(this, serverName);
  }



  /**
   * {@inheritDoc}
   */
  public String getAuthenticationID()
  {
    return authenticationID;
  }



  /**
   * {@inheritDoc}
   */
  public String getAuthorizationID()
  {
    return authorizationID;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getPassword()
  {
    return password;
  }



  /**
   * {@inheritDoc}
   */
  public String getRealm()
  {
    return realm;
  }



  /**
   * {@inheritDoc}
   */
  public String getSASLMechanism()
  {
    return SASL_MECHANISM_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setAuthenticationID(
      final String authenticationID) throws NullPointerException
  {
    Validator.ensureNotNull(authenticationID);
    this.authenticationID = authenticationID;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setAuthorizationID(
      final String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setPassword(final ByteString password)
      throws NullPointerException
  {
    Validator.ensureNotNull(password);
    this.password = password;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setPassword(final String password)
      throws NullPointerException
  {
    Validator.ensureNotNull(password);
    this.password = ByteString.valueOf(password);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setRealm(final String realm)
  {
    this.realm = realm;
    return this;
  }



  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("DigestMD5SASLBindRequest(bindDN=");
    builder.append(getName());
    builder.append(", authentication=SASL");
    builder.append(", saslMechanism=");
    builder.append(getSASLMechanism());
    builder.append(", authenticationID=");
    builder.append(authenticationID);
    builder.append(", authorizationID=");
    builder.append(authorizationID);
    builder.append(", realm=");
    builder.append(realm);
    builder.append(", password=");
    builder.append(password);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
