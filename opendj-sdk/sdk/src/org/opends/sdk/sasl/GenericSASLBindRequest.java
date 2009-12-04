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



import javax.security.sasl.SaslException;

import org.opends.sdk.ByteString;

import com.sun.opends.sdk.util.Validator;


/**
 * Generic SASL bind request.
 */
public class GenericSASLBindRequest extends
    SASLBindRequest<GenericSASLBindRequest> implements SASLContext
{
  // The SASL credentials.
  private ByteString saslCredentials;

  // The SASL mechanism.
  private String saslMechanism;



  public GenericSASLBindRequest(String saslMechanism,
      ByteString saslCredentials)
  {
    Validator.ensureNotNull(saslMechanism);
    this.saslCredentials = saslCredentials;
    this.saslMechanism = saslMechanism;
  }

  public void dispose() throws SaslException
  {
    // Nothing needed.
  }

  public boolean evaluateCredentials(ByteString incomingCredentials)
      throws SaslException
  {
    // This is a single stage SASL bind.
    return true;
  }


  public boolean isComplete()
  {
    return true;
  }

  public boolean isSecure()
  {
    return false;
  }


  public byte[] unwrap(byte[] incoming, int offset, int len)
      throws SaslException
  {
    byte[] copy = new byte[len];
    System.arraycopy(incoming, offset, copy, 0, len);
    return copy;
  }


  public byte[] wrap(byte[] outgoing, int offset, int len)
      throws SaslException
  {
    byte[] copy = new byte[len];
    System.arraycopy(outgoing, offset, copy, 0, len);
    return copy;
  }

  public ByteString getSASLCredentials()
  {
    return saslCredentials;
  }

  public String getSASLMechanism()
  {
    return saslMechanism;
  }

  public SASLContext getClientContext(String serverName) throws SaslException
  {
    return this;
  }

  public GenericSASLBindRequest getSASLBindRequest() {
    return this;
  }

  /**
   * Sets the SASL credentials for this bind request.
   *
   * @param saslCredentials
   *          The SASL credentials for this bind request, or {@code
   *          null} if there are none or if the bind does not use SASL
   *          authentication.
   * @return This raw bind request.
   */
  public GenericSASLBindRequest setSASLCredentials(
      ByteString saslCredentials)
  {
    this.saslCredentials = saslCredentials;
    return this;
  }



  /**
   * Sets The SASL mechanism for this bind request.
   *
   * @param saslMechanism
   *          The SASL mechanism for this bind request, or {@code null}
   *          if there are none or if the bind does not use SASL
   *          authentication.
   * @return This raw bind request.
   */
  public GenericSASLBindRequest setSASLMechanism(String saslMechanism)
  {
    Validator.ensureNotNull(saslMechanism);
    this.saslMechanism = saslMechanism;
    return this;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append("SASLBindRequest(bindDN=");
    builder.append(getName());
    builder.append(", authentication=SASL");
    builder.append(", saslMechanism=");
    builder.append(saslMechanism);
    builder.append(", saslCredentials=");
    builder.append(saslCredentials);
    builder.append(", controls=");
    builder.append(getControls());
    builder.append(")");
    return builder.toString();
  }
}
