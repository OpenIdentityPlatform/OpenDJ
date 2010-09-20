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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
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
      try
      {
        setNextSASLCredentials(saslClient.evaluateChallenge(result
            .getServerSASLCredentials() == null ? new byte[0] :
            result.getServerSASLCredentials().toByteArray()));
        return saslClient.isComplete();
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



  /**
   * {@inheritDoc}
   */
  public QOPOption[] getQOP() {
    String value = System.getProperty(Sasl.QOP);
    if(value == null || value.length() == 0)
    {
      return new QOPOption[]{QOPOption.AUTH};
    }
    String[] values = value.split(",");
    QOPOption[] options = new QOPOption[values.length];

    for(int i = 0; i < values.length; i++)
    {
      String v = values[i].trim();
      if(v.equalsIgnoreCase("auth"))
      {
        options[i] = QOPOption.AUTH;
      }
      else if(v.equalsIgnoreCase("auth-int"))
      {
        options[i] = QOPOption.AUTH_INT;
      }
      else if(v.equalsIgnoreCase("auth-conf"))
      {
        options[i] = QOPOption.AUTH_CONF;
      }
    }
    return options;
  }



  /**
   * {@inheritDoc}
   */
  public CipherOption[] getCipher() {
    String value = System.getProperty(Sasl.STRENGTH);
    if(value == null || value.length() == 0)
    {
      return new CipherOption[]{CipherOption.TRIPLE_DES_RC4,
          CipherOption.DES_RC4_56, CipherOption.RC4_40};
    }
    String[] values = value.split(",");
    CipherOption[] options = new CipherOption[values.length];

    for(int i = 0; i < values.length; i++)
    {
      String v = values[i].trim();
      if(v.equalsIgnoreCase("high"))
      {
        options[i] = CipherOption.TRIPLE_DES_RC4;
      }
      else if(v.equalsIgnoreCase("medium"))
      {
        options[i] = CipherOption.DES_RC4_56;
      }
      else if(v.equalsIgnoreCase("low"))
      {
        options[i] = CipherOption.RC4_40;
      }
    }
    return options;
  }



  /**
   * {@inheritDoc}
   */
  public boolean getServerAuth() {
    String value = System.getProperty(Sasl.SERVER_AUTH);
    return !(value == null || value.length() == 0) &&
        value.equalsIgnoreCase("true");

  }



  /**
   * {@inheritDoc}
   */
  public int getMaxReceiveBufferSize() {
    String value = System.getProperty(Sasl.MAX_BUFFER);
    if(value == null || value.length() == 0)
    {
      return 65536;
    }
    return Integer.parseInt(value);
  }



  /**
   * {@inheritDoc}
   */
  public int getMaxSendBufferSize() {
    String value = System.getProperty("javax.security.sasl.sendmaxbuffer");
    if(value == null || value.length() == 0)
    {
      return 65536;
    }
    return Integer.parseInt(value);
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setQOP(QOPOption... qopOptions) {
    String values = null;
    for(QOPOption option : qopOptions)
    {
      String value = null;
      if(option == QOPOption.AUTH)
      {
        value = "auth";
      }
      else if(option == QOPOption.AUTH_INT)
      {
        value = "auth-int";
      }
      else if(option == QOPOption.AUTH_CONF)
      {
        value = "auth-conf";
      }

      if(value != null)
      {
        if(values == null)
        {
          values = value;
        }
        else
        {
          values += (", " + value);
        }
      }
    }

    System.setProperty(Sasl.QOP, values);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setCipher(CipherOption... cipherOptions) {
    String values = null;
    for(CipherOption option : cipherOptions)
    {
      String value = null;
      if(option == CipherOption.TRIPLE_DES_RC4)
      {
        value = "high";
      }
      else if(option == CipherOption.DES_RC4_56)
      {
        value = "medium";
      }
      else if(option == CipherOption.RC4_40)
      {
        value = "low";
      }

      if(value != null)
      {
        if(values == null)
        {
          values = value;
        }
        else
        {
          values += (", " + value);
        }
      }
    }

    System.setProperty(Sasl.STRENGTH, values);
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setServerAuth(boolean serverAuth) {
    System.setProperty(Sasl.SERVER_AUTH, String.valueOf(serverAuth));
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setMaxReceiveBufferSize(int maxBuffer) {
    System.setProperty(Sasl.MAX_BUFFER, String.valueOf(maxBuffer));
    return this;
  }



  /**
   * {@inheritDoc}
   */
  public DigestMD5SASLBindRequest setMaxSendBufferSize(int maxBuffer) {
    System.setProperty("javax.security.sasl.sendmaxbuffer",
        String.valueOf(maxBuffer));
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
