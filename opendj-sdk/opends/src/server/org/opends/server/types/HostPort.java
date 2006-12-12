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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that combines an address and
 * port number, as may be used to accept a connection from or initiate
 * a connection to a remote system.
 */
public final class HostPort
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.HostPort";



  // The host for this object.
  private String host;

  // The port for this object;
  private int port;



  /**
   * Creates a new {@code HostPort} object with the specified port
   * number but no host.
   *
   * @param  port  The port number for this {@code HostPort} object.
   */
  public HostPort(int port)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(port));

    this.host = null;
    this.port = port;
  }



  /**
   * Creates a new {@code HostPort} object with the specified port
   * number but no explicit host.
   *
   * @param  host  The host address or name for this {@code HostPort}
   *               object, or {@code null} if there is none.
   * @param  port  The port number for this {@code HostPort} object.
   */
  public HostPort(String host, int port)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(host),
                            String.valueOf(port));

    this.host = host;
    this.port = port;
  }



  /**
   * Retrieves the host for this {@code HostPort} object.
   *
   * @return  The host for this {@code HostPort} object, or
   *          {@code null} if none was provided.
   */
  public String getHost()
  {
    assert debugEnter(CLASS_NAME, "getHost");

    return host;
  }



  /**
   * Retrieves the port number for this {@code HostPort} object.
   *
   * @return  The port number for this {@code HostPort} object.
   */
  public int getPort()
  {
    assert debugEnter(CLASS_NAME, "getPort");

    return port;
  }



  /**
   * Retrieves a string representation of this {@code HostPort}
   * object.  It will be the host element (or nothing if no host was
   * given) followed by a colon and the port number.
   *
   * @return  A string representation of this {@code HostPort} object.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

    if (host ==  null)
    {
      return ":" + port;
    }
    else
    {
      return host + ":" + port;
    }
  }
}

