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

package org.opends.sdk;



import javax.net.ssl.SSLContext;

import com.sun.opends.sdk.util.Validator;



/**
 * Common options for LDAP listeners.
 */
public class LDAPListenerOptions
{

  private SSLContext sslContext;

  private DecodeOptions decodeOptions;

  private int backlog;



  /**
   * Creates a new set of listener options with default settings. SSL will not
   * be enabled, and a default set of decode options will be used.
   */
  public LDAPListenerOptions()
  {
    this.sslContext = null;
    this.backlog = 0;
    this.decodeOptions = new DecodeOptions();
  }



  /**
   * Creates a new set of listener options having the same initial set of
   * options as the provided set of listener options.
   *
   * @param options
   *          The set of listener options to be copied.
   */
  public LDAPListenerOptions(final LDAPListenerOptions options)
  {
    this.sslContext = options.sslContext;
    this.backlog = options.backlog;
    this.decodeOptions = new DecodeOptions(options.decodeOptions);
  }



  /**
   * Returns the maximum queue length for incoming connections requests. If a
   * connection request arrives when the queue is full, the connection is
   * refused. If the backlog is less than {@code 1} then a default value of
   * {@code 50} will be used.
   *
   * @return The maximum queue length for incoming connections requests.
   */
  public final int getBacklog()
  {
    return backlog;
  }



  /**
   * Returns the decoding options which will be used to control how requests and
   * responses are decoded.
   *
   * @return The decoding options which will be used to control how requests and
   *         responses are decoded (never {@code null}).
   */
  public final DecodeOptions getDecodeOptions()
  {
    return decodeOptions;
  }



  /**
   * Returns the SSL context which will be used when initiating connections with
   * the Directory Server. By default no SSL context will be used, indicating
   * that connections will not be secured. If a non-{@code null} SSL context is
   * returned then connections will be secured using either SSL or StartTLS.
   *
   * @return The SSL context which will be used when initiating secure
   *         connections with the Directory Server, which may be {@code null}
   *         indicating that connections will not be secured.
   */
  public final SSLContext getSSLContext()
  {
    return sslContext;
  }



  /**
   * Sets the maximum queue length for incoming connections requests. If a
   * connection request arrives when the queue is full, the connection is
   * refused. If the backlog is less than {@code 1} then a default value of
   * {@code 50} will be used.
   *
   * @param backlog
   *          The maximum queue length for incoming connections requests.
   * @return A reference to this LDAP listener options.
   */
  public final LDAPListenerOptions setBacklog(final int backlog)
  {
    this.backlog = backlog;
    return this;
  }



  /**
   * Sets the decoding options which will be used to control how requests and
   * responses are decoded.
   *
   * @param decodeOptions
   *          The decoding options which will be used to control how requests
   *          and responses are decoded (never {@code null}).
   * @return A reference to this LDAP listener options.
   * @throws NullPointerException
   *           If {@code decodeOptions} was {@code null}.
   */
  public final LDAPListenerOptions setDecodeOptions(
      final DecodeOptions decodeOptions) throws NullPointerException
  {
    Validator.ensureNotNull(decodeOptions);
    this.decodeOptions = decodeOptions;
    return this;
  }



  /**
   * Sets the SSL context which will be used when initiating connections with
   * the Directory Server. By default no SSL context will be used, indicating
   * that connections will not be secured. If a non-{@code null} SSL context is
   * returned then connections will be secured using either SSL or StartTLS.
   *
   * @param sslContext
   *          The SSL context which will be used when initiating secure
   *          connections with the Directory Server, which may be {@code null}
   *          indicating that connections will not be secured.
   * @return A reference to this LDAP listener options.
   */
  public final LDAPListenerOptions setSSLContext(final SSLContext sslContext)
  {
    this.sslContext = sslContext;
    return this;
  }
}
