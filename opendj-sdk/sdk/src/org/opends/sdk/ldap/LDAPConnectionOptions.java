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

package org.opends.sdk.ldap;



import javax.net.ssl.SSLContext;

import org.opends.sdk.schema.Schema;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.SearchScope;

import com.sun.opends.sdk.util.Validator;



/**
 * Common connection options for LDAP connections.
 */
public final class LDAPConnectionOptions
{
  private static final SearchRequest DEFAULT_PING = Requests
      .newSearchRequest("", SearchScope.BASE_OBJECT, "(objectClass=*)",
                        "1.1");

  private Schema schema = Schema.getDefaultSchema();

  private SSLContext sslContext = null;

  private boolean useStartTLS = false;

  private SearchRequest pingRequest = DEFAULT_PING;



  /**
   * Creates a copy of the provided connection options.
   *
   * @param options
   *          The options to be copied.
   * @return The copy of the provided connection options.
   */
  public static LDAPConnectionOptions copyOf(
      LDAPConnectionOptions options)
  {
    return defaultOptions().assign(options);
  }



  /**
   * Creates a new set of connection options with default settings. SSL
   * will not be enabled, nor will key or trust managers be defined.
   *
   * @return The new connection options.
   */
  public static LDAPConnectionOptions defaultOptions()
  {
    return new LDAPConnectionOptions();
  }



  // Prevent direct instantiation.
  private LDAPConnectionOptions()
  {
    // Nothing to do.
  }



  /**
   * Returns the schema which will be used to decode responses from the
   * server. By default the schema returned by
   * {@link Schema#getDefaultSchema()} will be used.
   *
   * @return The schema which will be used to decode responses from the
   *         server.
   */
  public Schema getSchema()
  {
    return schema;
  }



  /**
   * Sets the schema which will be used to decode responses from the
   * server. By default the schema returned by
   * {@link Schema#getDefaultSchema()} will be used.
   *
   * @param schema
   *          The schema which will be used to decode responses from the
   *          server.
   * @return A reference to this LDAP connection options.
   * @throws NullPointerException
   *           If {@code schema} was {@code null}.
   */
  public LDAPConnectionOptions setSchema(Schema schema)
      throws NullPointerException
  {
    Validator.ensureNotNull(schema);
    this.schema = schema;
    return this;
  }



  /**
   * Returns the SSL context which will be used when initiating
   * connections with the Directory Server. By default no SSL context
   * will be used, indicating that connections will not be secured. If a
   * non-{@code null} SSL context is returned then connections will be
   * secured using either SSL or StartTLS depending on
   * {@link #useStartTLS()}.
   *
   * @return The SSL context which will be used when initiating secure
   *         connections with the Directory Server, which may be {@code
   *         null} indicating that connections will not be secured.
   */
  public SSLContext getSSLContext()
  {
    return sslContext;
  }



  /**
   * Sets the SSL context which will be used when initiating connections
   * with the Directory Server. By default no SSL context will be used,
   * indicating that connections will not be secured. If a non-{@code
   * null} SSL context is returned then connections will be secured
   * using either SSL or StartTLS depending on {@link #useStartTLS()}.
   *
   * @param sslContext
   *          The SSL context which will be used when initiating secure
   *          connections with the Directory Server, which may be
   *          {@code null} indicating that connections will not be
   *          secured.
   * @return A reference to this LDAP connection options.
   */
  public LDAPConnectionOptions setSSLContext(SSLContext sslContext)
  {
    this.sslContext = sslContext;
    return this;
  }



  /**
   * Indicates whether or not SSL or StartTLS should be used for
   * securing connections when an SSL context is specified. By default
   * SSL will be used in preference to StartTLS.
   *
   * @return {@code true} if StartTLS should be used for securing
   *         connections when an SSL context is specified, otherwise
   *         {@code false} indicating that SSL should be used.
   */
  public boolean useStartTLS()
  {
    return useStartTLS;
  }



  /**
   * Specifies whether or not SSL or StartTLS should be used for
   * securing connections when an SSL context is specified. By default
   * SSL will be used in preference to StartTLS.
   *
   * @param useStartTLS
   *          {@code true} if StartTLS should be used for securing
   *          connections when an SSL context is specified, otherwise
   *          {@code false} indicating that SSL should be used.
   * @return A reference to this LDAP connection options.
   */
  public LDAPConnectionOptions setUseStartTLS(boolean useStartTLS)
  {
    this.useStartTLS = useStartTLS;
    return this;
  }

  /**
   * Retrieves the search request used to verify the validity of a
   * LDAP connection. By default, the root DSE is retrieved without any
   * attributes.
   *
   * @return The search request.
   */
  public SearchRequest getPingRequest()
  {
    return pingRequest;
  }

  /**
   * Sets the search request used to verify the validity of a
   * LDAP connection.
   *
   * @param pingRequest The search request that can be used to verify the
   *                    validity of a LDAP connection.
   */
  public void setPingRequest(SearchRequest pingRequest)
  {
    this.pingRequest = pingRequest;
  }

  // Assigns the provided options to this set of options.
  LDAPConnectionOptions assign(LDAPConnectionOptions options)
  {
    this.schema = options.schema;
    this.sslContext = options.sslContext;
    this.useStartTLS = options.useStartTLS;
    return this;
  }

}
