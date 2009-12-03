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



import static com.sun.opends.sdk.util.Messages.*;
import static org.opends.sdk.util.StaticUtils.*;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.DN;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.Validator;

import com.sun.opends.sdk.util.Message;
import com.sun.security.auth.callback.TextCallbackHandler;
import com.sun.security.auth.module.Krb5LoginModule;


/**
 * GSSAPI SASL bind request.
 */
public final class GSSAPISASLBindRequest extends
    SASLBindRequest<GSSAPISASLBindRequest>
{
  /**
   * The name of the SASL mechanism based on GSS-API authentication.
   */
  public static final String SASL_MECHANISM_GSSAPI = "GSSAPI";

  private final Subject subject;
  private String authorizationID;

  private class GSSAPISASLContext extends AbstractSASLContext
  {
    private String serverName;
    private SaslClient saslClient;
    private ByteString outgoingCredentials = null;

    private ByteString incomingCredentials;

    PrivilegedExceptionAction<Boolean> evaluateAction =
        new PrivilegedExceptionAction<Boolean>()
        {
          public Boolean run() throws Exception
          {
            byte[] bytes =
                saslClient.evaluateChallenge(incomingCredentials
                    .toByteArray());
            if (bytes != null)
            {
              outgoingCredentials = ByteString.wrap(bytes);
            }
            else
            {
              outgoingCredentials = null;
            }

            return isComplete();
          }
        };

    PrivilegedExceptionAction<Object> invokeAction =
        new PrivilegedExceptionAction<Object>()
        {
          public Object run() throws Exception
          {
            Map<String, String> props = new HashMap<String, String>();
            props.put(Sasl.QOP, "auth-conf,auth-int,auth");
            saslClient =
                Sasl.createSaslClient(
                    new String[] { SASL_MECHANISM_GSSAPI },
                    authorizationID, SASL_DEFAULT_PROTOCOL, serverName,
                    props, GSSAPISASLContext.this);

            if (saslClient.hasInitialResponse())
            {
              byte[] bytes = saslClient.evaluateChallenge(new byte[0]);
              if (bytes != null)
              {
                outgoingCredentials = ByteString.wrap(bytes);
              }
            }
            return null;
          }
        };

    private GSSAPISASLContext(String serverName) throws SaslException {
      this.serverName = serverName;

      try
      {
        Subject.doAs(subject, invokeAction);
      }
      catch (PrivilegedActionException e)
      {
        if (e.getCause() instanceof SaslException)
        {
          throw (SaslException) e.getCause();
        }

        // This should not happen. Must be a bug.
        Message msg =
            ERR_SASL_CONTEXT_CREATE_ERROR.get(SASL_MECHANISM_GSSAPI,
                getExceptionMessage(e));
        throw new SaslException(msg.toString(), e.getCause());
      }
    }

    public void dispose() throws SaslException
    {
      saslClient.dispose();
    }



    public boolean evaluateCredentials(ByteString incomingCredentials)
        throws SaslException
    {
      this.incomingCredentials = incomingCredentials;
      try
      {
        return Subject.doAs(subject, evaluateAction);
      }
      catch (PrivilegedActionException e)
      {
        if (e.getCause() instanceof SaslException)
        {
          throw (SaslException) e.getCause();
        }

        // This should not happen. Must be a bug.
        Message msg =
            ERR_SASL_PROTOCOL_ERROR.get(SASL_MECHANISM_GSSAPI,
                getExceptionMessage(e));
        throw new SaslException(msg.toString(), e.getCause());
      }
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

    public GSSAPISASLBindRequest getSASLBindRequest() {
      return GSSAPISASLBindRequest.this;
    }
  }


  public GSSAPISASLBindRequest(Subject subject)
  {
    Validator.ensureNotNull(subject);
    this.subject = subject;
  }



  public GSSAPISASLBindRequest(Subject subject, DN authorizationDN)
  {
    Validator.ensureNotNull(subject, authorizationDN);
    this.subject = subject;
    this.authorizationID = "dn:" + authorizationDN.toString();
  }



  public GSSAPISASLBindRequest(Subject subject, String authorizationID)
  {
    Validator.ensureNotNull(subject);
    this.subject = subject;
    this.authorizationID = authorizationID;
  }



  public String getAuthorizationID()
  {
    return authorizationID;
  }



  public String getSASLMechanism()
  {
    return SASL_MECHANISM_GSSAPI;
  }



  public SASLContext getClientContext(String serverName) throws SaslException
  {
    return new GSSAPISASLContext(serverName);
  }



  public GSSAPISASLBindRequest setAuthorizationID(String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }



  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("GSSAPISASLBindRequest(bindDN=");
    builder.append(getName());
    builder.append(", authentication=SASL");
    builder.append(", saslMechanism=");
    builder.append(getSASLMechanism());
    builder.append(", subject=");
    builder.append(subject);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }

  public static Subject Kerberos5Login(String authenticationID,
                                       ByteString password,
                                       String realm, String kdc)
      throws LoginException {
    Map<String, Object> state = new HashMap<String, Object>();
    state.put("javax.security.auth.login.name", authenticationID);
    state.put("javax.security.auth.login.password",
        password.toString().toCharArray());
    state.put("javax.security.auth.useSubjectCredsOnly", "true");
    state.put("java.security.krb5.realm", realm);
    state.put("java.security.krb5.kdc", kdc);

    Map<String, Object> options = new HashMap<String, Object>();
    options.put("debug", "true");
    options.put("tryFirstPass", "true");
    options.put("useTicketCache", "true");
    options.put("doNotPrompt", "true");
    options.put("storePass", "false");
    options.put("forwardable", "true");

    Subject subject = new Subject();
    Krb5LoginModule login = new Krb5LoginModule();
    login.initialize(subject, new TextCallbackHandler(), state,
        options);
    if(login.login()){
      login.commit();
    }
    return subject;
  }
}
