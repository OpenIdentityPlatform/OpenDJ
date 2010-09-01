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

package org.opends.sdk;



import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import com.sun.opends.sdk.util.Validator;



/**
 * Common options for LDAP client connections.
 */
public class LDAPOptions
{
  private SSLContext sslContext;

  private boolean useStartTLS;

  private long timeoutInMillis;

  private DecodeOptions decodeOptions;

  /**
   * The list of cipher suite
   */
  private String[] enabledCipherSuites = null;

  /**
   * the list of protocols
   */
  private String[] enabledProtocols = null;



  /**
   * Creates a new set of connection options with default settings. SSL will not
   * be enabled, and a default set of decode options will be used.
   */
  public LDAPOptions()
  {
    super();
    this.sslContext = null;
    this.timeoutInMillis = 0;
    this.useStartTLS = false;
    this.decodeOptions = new DecodeOptions();
  }



  /**
   * Creates a new set of connection options having the same initial set of
   * options as the provided set of connection options.
   *
   * @param options
   *          The set of connection options to be copied.
   */
  public LDAPOptions(final LDAPOptions options)
  {
    this.sslContext = options.sslContext;
    this.timeoutInMillis = options.timeoutInMillis;
    this.useStartTLS = options.useStartTLS;
    this.decodeOptions = new DecodeOptions(options.decodeOptions);
    this.enabledCipherSuites = options.enabledCipherSuites;
    this.enabledProtocols = options.enabledProtocols;
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
   * returned then connections will be secured using either SSL or StartTLS
   * depending on {@link #useStartTLS()}.
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
   * Returns the operation timeout in the specified unit.
   *
   * @param unit
   *          The time unit of use.
   * @return The operation timeout.
   */
  public final long getTimeout(final TimeUnit unit)
  {
    return unit.convert(timeoutInMillis, TimeUnit.MILLISECONDS);
  }



  /**
   * Sets the decoding options which will be used to control how requests and
   * responses are decoded.
   *
   * @param decodeOptions
   *          The decoding options which will be used to control how requests
   *          and responses are decoded (never {@code null}).
   * @return A reference to this LDAP connection options.
   * @throws NullPointerException
   *           If {@code decodeOptions} was {@code null}.
   */
  public final LDAPOptions setDecodeOptions(final DecodeOptions decodeOptions)
      throws NullPointerException
  {
    Validator.ensureNotNull(decodeOptions);
    this.decodeOptions = decodeOptions;
    return this;
  }



  /**
   * Sets the SSL context which will be used when initiating connections with
   * the Directory Server. By default no SSL context will be used, indicating
   * that connections will not be secured. If a non-{@code null} SSL context is
   * returned then connections will be secured using either SSL or StartTLS
   * depending on {@link #useStartTLS()}.
   *
   * @param sslContext
   *          The SSL context which will be used when initiating secure
   *          connections with the Directory Server, which may be {@code null}
   *          indicating that connections will not be secured.
   * @return A reference to this LDAP connection options.
   */
  public final LDAPOptions setSSLContext(final SSLContext sslContext)
  {
    this.sslContext = sslContext;
    return this;
  }



  /**
   * Sets the operation timeout. If the response is not received from the
   * Directory Server in the timeout period, the operation will be abandoned and
   * an error result returned. A timeout setting of 0 disables timeout limits.
   *
   * @param timeout
   *          The operation timeout to use.
   * @param unit
   *          the time unit of the time argument.
   * @return A reference to this LDAP connection options.
   */
  public final LDAPOptions setTimeout(final long timeout, final TimeUnit unit)
  {
    this.timeoutInMillis = unit.toMillis(timeout);
    return this;
  }



  /**
   * Specifies whether or not SSL or StartTLS should be used for securing
   * connections when an SSL context is specified. By default SSL will be used
   * in preference to StartTLS.
   *
   * @param useStartTLS
   *          {@code true} if StartTLS should be used for securing connections
   *          when an SSL context is specified, otherwise {@code false}
   *          indicating that SSL should be used.
   * @return A reference to this LDAP connection options.
   */
  public final LDAPOptions setUseStartTLS(final boolean useStartTLS)
  {
    this.useStartTLS = useStartTLS;
    return this;
  }



  /**
   * Indicates whether or not SSL or StartTLS should be used for securing
   * connections when an SSL context is specified. By default SSL will be used
   * in preference to StartTLS.
   *
   * @return {@code true} if StartTLS should be used for securing connections
   *         when an SSL context is specified, otherwise {@code false}
   *         indicating that SSL should be used.
   */
  public final boolean useStartTLS()
  {
    return useStartTLS;
  }

  /**
   * Set the protocol versions enabled for secure connections with the
   * Directory Server.
   *
   * The protocols must be supported by the SSLContext specified in
   * {@link #setSSLContext(SSLContext)}. Following a successful call to
   * this method, only the protocols listed in the protocols parameter are
   * enabled for use.
   *
   * @param protocols Names of all the protocols to enable or {@code null} to
   *                  use the default protocols.
   * @return A reference to this LDAP connection options.
   */
  public final LDAPOptions setEnabledProtocols(String[] protocols)
  {
    this.enabledProtocols = protocols;
    return this;
  }

  /**
   * Set the cipher suites enabled for secure connections with the
   * Directory Server.
   *
   * The suites must be supported by the SSLContext specified in
   * {@link #setSSLContext(SSLContext)}. Following a successful call to
   * this method, only the suites listed in the protocols parameter are
   * enabled for use.
   *
   * @param suites Names of all the suites to enable or {@code null} to
   *                  use the default cipher suites.
   * @return A reference to this LDAP connection options.
   */
  public final LDAPOptions setEnabledCipherSuites(String[] suites)
  {
    this.enabledCipherSuites = suites;
    return this;
  }

  /**
   * Returns the names of the protocol versions which are currently enabled
   * for secure connections with the Directory Server.
   *
   * @return an array of protocols or {@code null} if the default protocols
   * are to be used.
   */
  public final String[] getEnabledProtocols()
  {
    return this.enabledProtocols;
  }

  /**
   * Returns the names of the protocol versions which are currently enabled
   * for secure connections with the Directory Server.
   *
   * @return an array of protocols or {@code null} if the default protocols
   * are to be used.
   */
  public final  String[] getEnabledCipherSuites()
  {
    return this.enabledCipherSuites;
  }

}
