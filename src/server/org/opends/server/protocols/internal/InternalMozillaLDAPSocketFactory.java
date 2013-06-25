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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.protocols.internal;



import java.net.Socket;

import netscape.ldap.LDAPSocketFactory;



/**
 * This class provides an implementation of the
 * {{netscape.ldap.LDAPSocketFactory}} class that can be used to allow
 * the Mozilla LDAP SDK for Java to perform internal operations in
 * OpenDS.  To use it, simply provide an instance of this class to the
 * constructor of the {{netscape.ldap.LDAPConnection}} class, like:
 * <PRE>
 * LDAPConnection conn =
 *      new LDAPConnection(new InternalMozillaLDAPSocketFactory());
 * </PRE>
 */
public final class InternalMozillaLDAPSocketFactory
       implements LDAPSocketFactory
{
  /**
   * Creates a new instance of this internal Mozilla LDAP socket
   * factory.
   */
  public InternalMozillaLDAPSocketFactory()
  {
    // No implementation is required.
  }



  /**
   * Creates and returns a new internal LDAP socket, which can be used
   * by the Mozilla LDAP SDK for Java to perform internal operations
   * in OpenDS.
   *
   * @param  host  The address of the server to which the connection
   *               should be established.  This will be ignored, since
   *               there will not be any actual network communication.
   * @param  port  The port of the server to which the connection
   *               should be established.  This will be ignored, since
   *               there will not be any actual network communication.
   *
   * @return  An internal LDAP socket, which can be used by the
   *          Mozilla LDAP SDK for Java to perform internal operations
   *          in OpenDS.
   */
  public Socket makeSocket(String host, int port)
  {
    return new InternalLDAPSocket();
  }
}

