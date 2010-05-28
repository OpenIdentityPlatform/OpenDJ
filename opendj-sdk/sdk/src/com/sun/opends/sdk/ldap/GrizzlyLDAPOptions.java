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

package com.sun.opends.sdk.ldap;



import org.opends.sdk.LDAPOptions;

import com.sun.grizzly.nio.transport.TCPNIOTransport;



/**
 * Common options for LDAP client connections, including the Grizzly TCP
 * transport.
 */
public final class GrizzlyLDAPOptions extends LDAPOptions
{
  private TCPNIOTransport transport = null;



  /**
   * Creates a new set of connection options with default settings. SSL will not
   * be enabled, a default set of decode options will be used, and the
   * {@link GlobalTransportFactory} should be used to obtain a TCP transport.
   */
  public GrizzlyLDAPOptions()
  {
    // Nothing to do.
  }



  /**
   * Creates a new set of connection options having the same initial set of
   * options as the provided set of connection options.
   *
   * @param options
   *          The set of connection options to be copied.
   */
  public GrizzlyLDAPOptions(final LDAPOptions options)
  {
    super(options);
    if (options instanceof GrizzlyLDAPOptions)
    {
      this.transport = ((GrizzlyLDAPOptions) options).getTCPNIOTransport();
    }
  }



  /**
   * Returns the Grizzly TCP transport which will be used when initiating
   * connections with the Directory Server. By default this method will return
   * {@code null} indicating that the {@link GlobalTransportFactory} should be
   * used to obtain a TCP transport.
   *
   * @return The Grizzly TCP transport which will be used when initiating
   *         connections with the Directory Server, or {@code null} if the
   *         {@link GlobalTransportFactory} should be used to obtain a TCP
   *         transport.
   */
  public final TCPNIOTransport getTCPNIOTransport()
  {
    return transport;
  }



  /**
   * Sets the Grizzly TCP transport which will be used when initiating
   * connections with the Directory Server. By default this method will return
   * {@code null} indicating that the {@link GlobalTransportFactory} should be
   * used to obtain a TCP transport.
   *
   * @param transport
   *          The Grizzly TCP transport which will be used when initiating
   *          connections with the Directory Server, or {@code null} if the
   *          {@link GlobalTransportFactory} should be used to obtain a TCP
   *          transport.
   * @return A reference to this connection options.
   */
  public final GrizzlyLDAPOptions setTCPNIOTransport(
      final TCPNIOTransport transport)
  {
    this.transport = transport;
    return this;
  }

}
