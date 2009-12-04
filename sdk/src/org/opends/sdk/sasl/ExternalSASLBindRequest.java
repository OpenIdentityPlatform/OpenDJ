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

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.ByteString;
import org.opends.sdk.DN;

import com.sun.opends.sdk.util.Validator;



/**
 * External SASL bind request.
 */
public final class ExternalSASLBindRequest extends
    SASLBindRequest<ExternalSASLBindRequest>
{
  /**
   * The name of the SASL mechanism based on external authentication.
   */
  public static final String SASL_MECHANISM_EXTERNAL = "EXTERNAL";

  private String authorizationID;

  private class ExternalSASLContext extends AbstractSASLContext
  {
    private SaslClient saslClient;
    private ByteString outgoingCredentials = null;

    private ExternalSASLContext(String serverName) throws SaslException {
      saslClient =
          Sasl.createSaslClient(new String[] { SASL_MECHANISM_EXTERNAL },
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

    public ExternalSASLBindRequest getSASLBindRequest() {
      return ExternalSASLBindRequest.this;
    }   
  }

  public ExternalSASLBindRequest()
  {
  }



  public ExternalSASLBindRequest(DN authorizationDN)
  {
    Validator.ensureNotNull(authorizationDN);
    this.authorizationID = "dn:" + authorizationDN.toString();
  }



  public ExternalSASLBindRequest(String authorizationID)
  {
    this.authorizationID = authorizationID;
  }



  public String getAuthorizationID()
  {
    return authorizationID;
  }



  public String getSASLMechanism()
  {
    return SASL_MECHANISM_EXTERNAL;
  }



  public SASLContext getClientContext(String serverName) throws SaslException
  {
    return new ExternalSASLContext(serverName);
  }



  public ExternalSASLBindRequest setAuthorizationID(
      String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }



  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("ExternalSASLBindRequest(bindDN=");
    builder.append(getName());
    builder.append(", authentication=SASL");
    builder.append(", saslMechanism=");
    builder.append(getSASLMechanism());
    builder.append(", authorizationID=");
    builder.append(authorizationID);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
