/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;



import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.responses.BindResult;



/**
 * Bind client implementation.
 */
class BindClientImpl implements BindClient, ConnectionSecurityLayer
{
  private final GenericBindRequest nextBindRequest;



  /**
   * Creates a new abstract bind client. The next bind request will be a copy of
   * the provided initial bind request which should be updated in subsequent
   * bind requests forming part of this authentication.
   *
   * @param initialBindRequest
   *          The initial bind request.
   */
  BindClientImpl(final BindRequest initialBindRequest)
  {
    this.nextBindRequest = new GenericBindRequestImpl(
        initialBindRequest.getName(),
        initialBindRequest.getAuthenticationType(), new byte[0], this);
    for (final Control control : initialBindRequest.getControls())
    {
      this.nextBindRequest.addControl(control);
    }
  }



  /**
   * Default implementation does nothing.
   */
  public void dispose()
  {
    // Do nothing.
  }



  /**
   * Default implementation does nothing and always returns {@code true}.
   */
  public boolean evaluateResult(final BindResult result)
      throws ErrorResultException
  {
    return true;
  }



  /**
   * Default implementation always returns {@code null}.
   */
  public ConnectionSecurityLayer getConnectionSecurityLayer()
  {
    return null;
  }



  /**
   * Returns the next bind request.
   */
  public final GenericBindRequest nextBindRequest()
  {
    return nextBindRequest;
  }



  /**
   * Default implementation just returns the copy of the bytes.
   */
  public byte[] unwrap(final byte[] incoming, final int offset, final int len)
      throws ErrorResultException
  {
    final byte[] copy = new byte[len];
    System.arraycopy(incoming, offset, copy, 0, len);
    return copy;
  }



  /**
   * Default implementation just returns the copy of the bytes.
   */
  public byte[] wrap(final byte[] outgoing, final int offset, final int len)
      throws ErrorResultException
  {
    final byte[] copy = new byte[len];
    System.arraycopy(outgoing, offset, copy, 0, len);
    return copy;
  }



  /**
   * Sets the authentication value to be used in the next bind request.
   *
   * @param authenticationValue
   *          The authentication value to be used in the next bind request.
   * @return A reference to this bind client.
   */
  final BindClient setNextAuthenticationValue(final byte[] authenticationValue)
  {
    nextBindRequest.setAuthenticationValue(authenticationValue);
    return this;
  }

}
