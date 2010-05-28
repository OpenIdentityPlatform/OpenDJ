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



import java.io.IOException;

import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.nio.NIOTransportFactory;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.nio.transport.UDPNIOTransport;
import com.sun.grizzly.threadpool.ThreadPoolConfig;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * A static Grizzly transport that can be used by default globally in the SDK.
 */
public final class GlobalTransportFactory extends NIOTransportFactory
{
  private static final GlobalTransportFactory INSTANCE = new GlobalTransportFactory();



  /**
   * Returns the global Grizzly transport factory.
   *
   * @return The global Grizzly transport factory.
   */
  public static TransportFactory getInstance()
  {
    return INSTANCE;
  }



  /**
   * Sets the global Grizzly transport factory.
   *
   * @param factory
   *          The global Grizzly transport factory.
   */
  public static void setInstance(final TransportFactory factory)
  {
    throw new UnsupportedOperationException("not yet implemented");
  }



  private int selectors;

  private TCPNIOTransport globalTCPNIOTransport = null;



  private GlobalTransportFactory()
  {
    // Prevent instantiation.
  }



  /**
   * Close the {@link com.sun.grizzly.TransportFactory} and release all
   * resources.
   */
  @Override
  public synchronized void close()
  {
    if (globalTCPNIOTransport != null)
    {
      try
      {
        globalTCPNIOTransport.stop();
      }
      catch (final IOException e)
      {
        StaticUtils.DEBUG_LOG
            .warning("Error shutting down Grizzly TCP NIO transport: " + e);
      }
    }
    super.close();
  }



  /**
   * Create instance of TCP {@link com.sun.grizzly.Transport}.
   *
   * @return instance of TCP {@link com.sun.grizzly.Transport}.
   */
  @Override
  public TCPNIOTransport createTCPTransport()
  {
    if (globalTCPNIOTransport == null)
    {
      globalTCPNIOTransport = setupTransport(new TCPNIOTransport());
      globalTCPNIOTransport.setSelectorRunnersCount(selectors);

      try
      {
        globalTCPNIOTransport.start();
      }
      catch (final IOException e)
      {
        throw new RuntimeException(
            "Unable to create default connection factory provider", e);
      }
    }
    return globalTCPNIOTransport;
  }



  /**
   * Creating an UDP transport is unsupported with this factory. A {@code
   * UnsupportedOperationException} will be thrown when this method is called.
   *
   * @return This method will always throw {@code UnsupportedOperationException}
   *         .
   */
  @Override
  public UDPNIOTransport createUDPTransport()
  {
    throw new UnsupportedOperationException();
  }



  @Override
  public void initialize()
  {
    final int cpus = Runtime.getRuntime().availableProcessors();
    int threads = Math.max(5, (cpus / 2) - 1);
    selectors = Math.max(2, cpus / 8);

    final String threadsStr = System
        .getProperty("org.opends.sdk.ldap.transport.threads");
    if (threadsStr != null)
    {
      threads = Integer.parseInt(threadsStr);
    }
    final String selectorsStr = System
        .getProperty("org.opends.sdk.ldap.transport.selectors");
    if (threadsStr != null)
    {
      selectors = Integer.parseInt(selectorsStr);
    }

    ThreadPoolConfig.DEFAULT.setCorePoolSize(threads);
    ThreadPoolConfig.DEFAULT.setMaxPoolSize(threads);
    ThreadPoolConfig.DEFAULT.setPoolName("OpenDS SDK Worker(Grizzly)");

    super.initialize();
  }
}
