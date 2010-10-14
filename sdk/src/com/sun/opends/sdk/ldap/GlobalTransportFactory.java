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

import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import com.sun.opends.sdk.util.StaticUtils;



/**
 * A static Grizzly transport that can be used by default globally in the SDK.
 */
public final class GlobalTransportFactory extends TransportFactory
{
  private static boolean isInitialized = false;



  /**
   * Returns the global Grizzly transport factory.
   *
   * @return The global Grizzly transport factory.
   */
  public static synchronized TransportFactory getInstance()
  {
    if (!isInitialized)
    {
      TransportFactory.setInstance(new GlobalTransportFactory());
      isInitialized = true;
    }
    return TransportFactory.getInstance();
  }



  private int selectors;

  private int linger = -1;

  private boolean tcpNoDelay = true;

  private boolean reuseAddress = true;

  private TCPNIOTransport globalTCPNIOTransport = null;



  private GlobalTransportFactory()
  {
    // Prevent instantiation.
  }



  /**
   * Close the {@link org.glassfish.grizzly.TransportFactory} and release all
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
   * Create instance of TCP {@link org.glassfish.grizzly.Transport}.
   *
   * @return instance of TCP {@link org.glassfish.grizzly.Transport}.
   */
  @Override
  public synchronized TCPNIOTransport createTCPTransport()
  {
    if (globalTCPNIOTransport == null)
    {
      globalTCPNIOTransport = setupTransport(new TCPNIOTransport());
      globalTCPNIOTransport.setSelectorRunnersCount(selectors);
      globalTCPNIOTransport.setLinger(linger);
      globalTCPNIOTransport.setTcpNoDelay(tcpNoDelay);
      globalTCPNIOTransport.setReuseAddress(reuseAddress);

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
   * Creating an UDP transport is unsupported with this factory. A
   * {@code UnsupportedOperationException} will be thrown when this method is
   * called.
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

    final String lingerStr = System
        .getProperty("org.opends.sdk.ldap.transport.linger");
    if (lingerStr != null)
    {
      linger = Integer.parseInt(lingerStr);
    }

    final String tcpNoDelayStr = System
        .getProperty("org.opends.sdk.ldap.transport.tcpNoDelay");
    if (tcpNoDelayStr != null)
    {
      tcpNoDelay = Integer.parseInt(tcpNoDelayStr) != 0;
    }

    final String reuseAddressStr = System
        .getProperty("org.opends.sdk.ldap.transport.reuseAddress");
    if (reuseAddressStr != null)
    {
      reuseAddress = Integer.parseInt(reuseAddressStr) != 0;
    }

    super.initialize();
  }

}
