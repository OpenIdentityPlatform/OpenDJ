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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk.requests;



import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ErrorResultException;
import org.opends.sdk.ResultCode;
import org.opends.sdk.responses.BindResult;
import org.opends.sdk.responses.Responses;



/**
 * External SASL bind request implementation.
 */
final class ExternalSASLBindRequestImpl extends
    AbstractSASLBindRequest<ExternalSASLBindRequest> implements
    ExternalSASLBindRequest
{
  private final static class Client extends SASLBindClientImpl
  {
    private final SaslClient saslClient;



    private Client(final ExternalSASLBindRequestImpl initialBindRequest,
        final String serverName) throws ErrorResultException
    {
      super(initialBindRequest);

      try
      {
        saslClient = Sasl.createSaslClient(
            new String[] { SASL_MECHANISM_NAME }, initialBindRequest
                .getAuthorizationID(), SASL_DEFAULT_PROTOCOL, serverName, null,
            this);
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
  }



  private String authorizationID = null;



  ExternalSASLBindRequestImpl()
  {
    // Nothing to do.
  }



  /**
   * Creates a new external SASL bind request that is an exact copy of the
   * provided request.
   *
   * @param externalSASLBindRequest
   *          The external SASL bind request to be copied.
   * @throws NullPointerException
   *           If {@code externalSASLBindRequest} was {@code null} .
   */
  ExternalSASLBindRequestImpl(
      final ExternalSASLBindRequest externalSASLBindRequest)
      throws NullPointerException
  {
    super(externalSASLBindRequest);
    this.authorizationID = externalSASLBindRequest.getAuthorizationID();
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
  public String getAuthorizationID()
  {
    return authorizationID;
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
  public ExternalSASLBindRequest setAuthorizationID(final String authorizationID)
  {
    this.authorizationID = authorizationID;
    return this;
  }



  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
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
