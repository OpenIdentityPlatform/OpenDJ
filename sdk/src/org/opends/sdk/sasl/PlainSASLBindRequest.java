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

import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;

import com.sun.opends.sdk.util.Validator;



/**
 * Plain SASL bind request.
 */
public final class PlainSASLBindRequest extends
    SASLBindRequest<PlainSASLBindRequest>
{
  /**
   * The name of the SASL mechanism based on PLAIN authentication.
   */
  public static final String SASL_MECHANISM_PLAIN = "PLAIN";

  private String authenticationID;
  private String authorizationID;
  private ByteString password;

  private NameCallbackHandler authIDHandler;
  private PasswordCallbackHandler passHandler;

  private class PlainSASLContext extends AbstractSASLContext
  {
    private SaslClient saslClient;
    private ByteString outgoingCredentials = null;

    private PlainSASLContext(String serverName) throws SaslException {
      saslClient =
          Sasl.createSaslClient(new String[] { SASL_MECHANISM_PLAIN },
              authorizationID, SASL_DEFAULT_PROTOCOL, serverName, null,
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
      return false;
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

    public PlainSASLBindRequest getSASLBindRequest() {
      return PlainSASLBindRequest.this;
    }
  }

  public PlainSASLBindRequest(DN authenticationDN, ByteString password)
  {
    Validator.ensureNotNull(authenticationDN, password);
    this.authenticationID = "dn:" + authenticationDN.toString();
    this.password = password;
  }



  public PlainSASLBindRequest(DN authenticationDN, DN authorizationDN,
                              ByteString password)
  {
    Validator
        .ensureNotNull(authenticationDN, authorizationDN, password);
    this.authenticationID = "dn:" + authenticationDN.toString();
    this.authorizationID = "dn:" + authorizationDN.toString();
    this.password = password;
  }



  public PlainSASLBindRequest(String authenticationID,
                              ByteString password)
  {
    Validator.ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.password = password;
  }



  public PlainSASLBindRequest(String authenticationID,
                              String authorizationID, ByteString password)
  {
    Validator
        .ensureNotNull(authenticationID, password);
    this.authenticationID = authenticationID;
    this.authorizationID = authorizationID;
    this.password = password;
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



  public String getSASLMechanism()
  {
    return SASL_MECHANISM_PLAIN;
  }



  public SASLContext getClientContext(String serverName) throws SaslException
  {
    return new PlainSASLContext(serverName);
  }



  public PlainSASLBindRequest setAuthenticationID(
      String authenticationID)
  {
    Validator.ensureNotNull(authenticationID);
    this.authenticationID = authenticationID;
    return this;
  }



  public PlainSASLBindRequest setAuthIDHandler(
      NameCallbackHandler authIDHandler)
  {
    this.authIDHandler = authIDHandler;
    return this;
  }



  public PlainSASLBindRequest setPassHandler(
      PasswordCallbackHandler passHandler)
  {
    this.passHandler = passHandler;
    return this;
  }



  public PlainSASLBindRequest setPassword(ByteString password)
  {
    Validator.ensureNotNull(password);
    this.password = password;
    return this;
  }



  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("PlainSASLBindRequest(bindDN=");
    builder.append(getName());
    builder.append(", authentication=SASL");
    builder.append(", saslMechanism=");
    builder.append(getSASLMechanism());
    builder.append(", authenticationID=");
    builder.append(authenticationID);
    builder.append(", authorizationID=");
    builder.append(authorizationID);
    builder.append(", password=");
    builder.append(password);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
