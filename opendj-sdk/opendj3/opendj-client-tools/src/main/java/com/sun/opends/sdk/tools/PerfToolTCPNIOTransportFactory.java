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

package com.sun.opends.sdk.tools;



import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.nio.DefaultNIOTransportFactory;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;



/**
 * The TCPNIOTransportFactory which performance tools will use.
 */
public final class PerfToolTCPNIOTransportFactory extends
    DefaultNIOTransportFactory
{
  private int selectors;

  private int linger = 0;

  private boolean tcpNoDelay = true;

  private boolean reuseAddress = true;

  private TCPNIOTransport singletonTransport = null;



  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized TCPNIOTransport createTCPTransport()
  {
    if (singletonTransport == null)
    {
      singletonTransport = super.createTCPTransport();

      singletonTransport.setSelectorRunnersCount(selectors);
      singletonTransport.setLinger(linger);
      singletonTransport.setTcpNoDelay(tcpNoDelay);
      singletonTransport.setReuseAddress(reuseAddress);
    }

    return singletonTransport;
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



  /**
   * {@inheritDoc}
   */
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
    if (selectorsStr != null)
    {
      selectors = Integer.parseInt(selectorsStr);
    }

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

    // Copied from TransportFactory.
    defaultAttributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
    defaultMemoryManager = new HeapMemoryManager();
    defaultWorkerThreadPool = GrizzlyExecutorService
        .createInstance(ThreadPoolConfig.defaultConfig()
            .setMemoryManager(defaultMemoryManager).setCorePoolSize(threads)
            .setMaxPoolSize(threads).setPoolName("OpenDS SDK Worker(Grizzly)"));

    // Copied from NIOTransportFactory.
    defaultSelectorHandler = new DefaultSelectorHandler();
    defaultSelectionKeyHandler = new DefaultSelectionKeyHandler();

    /*
     * By default TemporarySelector pool size should be equal to the number of
     * processing threads
     */
    int selectorPoolSize = TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
    if (defaultWorkerThreadPool instanceof AbstractThreadPool)
    {
      selectorPoolSize = Math.min(
          ((AbstractThreadPool) defaultWorkerThreadPool).getConfig()
              .getMaxPoolSize(), selectorPoolSize);
    }
    defaultTemporarySelectorPool = new TemporarySelectorPool(selectorPoolSize);
  }

}
