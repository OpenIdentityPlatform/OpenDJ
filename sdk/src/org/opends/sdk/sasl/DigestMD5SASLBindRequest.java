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

package org.opends.sdk.sasl;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;

import com.sun.opends.sdk.util.Validator;



/**
 * Digest-MD5 SASL bind request.
 */
public final class DigestMD5SASLBindRequest extends
    SASLBindRequest<DigestMD5SASLBindRequest>
{
  /**
   * The name of the SASL mechanism based on DIGEST-MD5 authentication.
   */
  public static final String SASL_MECHANISM_DIGEST_MD5 = "DIGEST-MD5";

  private String authenticationID;
  private String authorizationID;
  private ByteString password;
  private String realm;

  private NameCallbackHandler authIDHandler;
  private PasswordCallbackHandler passHandler;
  private TextInputCallbackHandler realmHandler;

  private class DigestMD5SASLContext extends AbstractSASLContext
  {
    private SaslClient saslClient;
    private ByteString outgoingCredentials = null;

    private DigestMD5SASLContext(String serverName) throws SaslException {
      Map<String, String> props = new HashMap<String, String>();
      props.put(Sasl.QOP, "auth-conf,auth-int,auth");
      saslClient =
          Sasl.createSaslClient(
              new String[] { SASL_MECHANISM_DIGEST_MD5 },
              authorizationID, SASL_DEFAULT_PROTOCOL, serverName, props,
              this);

      if (saslClient.hasInitialResponse())
      {
        byte[] bytes = saslClient.evaluateChallenge(new byte[0]);
        if (bytes != null)
        {
          this.outgoingCredentials = ByteString.wrap(bytes);
        }
      }
    }

    public void dispose() throws SaslException
    {
      saslClient.dispose();
    }

    public boolean evaluateCredentials(ByteString incomingCredentials)
        throws SaslException
    {
      byte[] bytes =
          saslClient.evaluateChallenge(incomingCredentials.toByteArray());
      if (bytes != null)
      {
        this.outgoingCredentials = ByteString.wrap(bytes);
      }
      else
      {
        this.outgoingCredentials = null;
      }

      return isComplete();
    }

    public ByteString getSASLCredentials()
    {
      return outgoingCredentials;
    }


    public boolean isComplete()
    {
      return saslClient.isComplete();
    }



    public boolean isSecure()
    {
      String qop = (String) saslClient.getNegotiatedProperty(Sasl.QOP);
      return (qop.equalsIgnoreCase("auth-int") || qop
          .equalsIgnoreCase("auth-conf"));
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len)
        throws SaslException
    {
      return saslClient.unwrap(incoming, offset, len);
    }



    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len)
        throws SaslException
    {
      return saslClient.wrap(outgoing, offset, len);
    }



    @Override
    protected void handle(NameCallback callback)
        throws UnsupportedCallbackException
    {
      if (authIDHandler == null)
      {
        callback.setName(authenticationID);
      }
      else
      {
        if(authIDHandler.handle(callback))
        {
          authenticationID = callback.getName();
          authIDHandler = null;
        }
      }
    }



    @Override
    protected void handle(PasswordCallback callback)
        throws UnsupportedCallbackException
    {
      if (passHandler == null)
      {
        callback.setPassword(password.toString().toCharArray());
      }
      else
      {
        if(passHandler.handle(callback))
        {
          password = ByteString.valueOf(callback.getPassword());
          passHandler = null;
        }
      }
    }



    @Override
    protected void handle(RealmCallback callback)
        throws UnsupportedCallbackException
    {
      if (realmHandler == null)
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
      else
      {
        if(realmHandler.handle(callback))
        {
          realm = callback.getText();
          realmHandler = null;
        }
      }
    }

    public DigestMD5SASLBindRequest getSASLBindRequest() {
      return DigestMD5SASLBindRequest.this;
    }
  }

  public DigestMD5SASLBindRequest(DN authenticationDN,
                                  ByteString password)
  {
    Validator.ensureNotNull(authenticationDN, password);
    this.authenticationID = "dn:" + authenticationDN.toString();
    this.password = password;
  }



  public DigestMD5SASLBindRequest(DN authenticationDN,
                                  DN authorizationDN, ByteString password)
  {
    Validator
        .ensureNotNull(authenticationDN, authorizationDN, password);
    this.authenticationID = "dn:" + authenticationDN.toString();
    this.authorizationID = "dn:" + authorizationDN.toString();
    this.password = password;
  }



  public DigestMD5SASLBindRequest(DN authenticationDN,
                                  DN authorizationDN, ByteString password, String realm)
  {
    Validator.ensureNotNull(authenticationDN, authorizationDN,
        password);
    this.authenticationID = "dn:" + authenticationDN.toString();
    this.authorizationID = "dn:" + authorizationDN.toString();
    this.password = password;
    this.realm = realm;
  }



  public DigestMD5SASLBindRequest(String authenticationID,
                                  ByteString password)
  {
    Validator.ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.password = password;
  }



  public DigestMD5SASLBindRequest(String authenticationID,
                                  String authorizationID, ByteString password)
  {
    Validator
        .ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.authorizationID = authorizationID;
    this.password = password;
  }



  public DigestMD5SASLBindRequest(String authenticationID,
                                  String authorizationID, ByteString password, String realm)
  {
    Validator.ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.authorizationID = authorizationID;
    this.password = password;
    this.realm = realm;
  }



  public String getAuthenticationID()
  {
    return authenticationID;
  }



  public NameCallbackHandler getAuthIDHandler()
  {
    return authIDHandler;
  }



  public String getAuthorizationID()
  {
    return authorizationID;
  }



  public PasswordCallbackHandler getPassHandler()
  {
    return passHandler;
  }



  public ByteString getPassword()
  {
    return password;
  }



  public String getRealm()
  {
    return realm;
  }



  public TextInputCallbackHandler getRealmHandler()
  {
    return realmHandler;
  }


  @Override
  public String getSASLMechanism()
  {
    return SASL_MECHANISM_DIGEST_MD5;
  }



  public SASLContext getClientContext(String serverName) throws SaslException
  {
    return new DigestMD5SASLContext(serverName);
  }



  public DigestMD5SASLBindRequest setAuthenticationID(
      String authenticationID)
  {
    Validator.ensureNotNull(authenticationID);
    this.authenticationID = authenticationID;
    return this;
  }



  public DigestMD5SASLBindRequest setAuthIDHandler(
      NameCallbackHandler authIDHandler)
  {
    this.authIDHandler = authIDHandler;
    return this;
  }



  public DigestMD5SASLBindRequest setAuthorizationID(
      String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }



  public DigestMD5SASLBindRequest setPassHandler(
      PasswordCallbackHandler passHandler)
  {
    this.passHandler = passHandler;
    return this;
  }



  public DigestMD5SASLBindRequest setPassword(ByteString password)
  {
    Validator.ensureNotNull(password);
    this.password = password;
    return this;
  }



  public DigestMD5SASLBindRequest setRealm(String realm)
  {
    this.realm = realm;
    return this;
  }



  public void setRealmHandler(TextInputCallbackHandler realmHandler)
  {
    this.realmHandler = realmHandler;
  }



  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
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
