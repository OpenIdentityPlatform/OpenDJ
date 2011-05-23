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
 */

package com.forgerock.opendj.ldap;



import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.glassfish.grizzly.utils.LinkedTransferQueue;

import com.forgerock.opendj.util.StaticUtils;



/**
 * Checks connection for pending requests that have timed out.
 */
final class TimeoutChecker
{
  static final TimeoutChecker INSTANCE = new TimeoutChecker();

  private final LinkedTransferQueue<LDAPConnection> connections;
  private transient final ReentrantLock lock;
  private transient final Condition available;



  private TimeoutChecker()
  {
    this.connections = new LinkedTransferQueue<LDAPConnection>();
    this.lock = new ReentrantLock();
    this.available = lock.newCondition();

    final Thread checkerThread = new Thread("Timeout Checker")
    {
      @Override
      public void run()
      {
        StaticUtils.DEBUG_LOG.fine("Timeout Checker Starting");
        final ReentrantLock lock = TimeoutChecker.this.lock;
        lock.lock();
        try
        {
          while (true)
          {
            final long currentTime = System.currentTimeMillis();
            long delay = 0;

            for (final LDAPConnection connection : connections)
            {
              StaticUtils.DEBUG_LOG.finer("Checking connection " + connection
                  + " delay = " + delay);
              final long newDelay = connection
                  .cancelExpiredRequests(currentTime);
              if (newDelay > 0)
              {
                if (delay > 0)
                {
                  delay = Math.min(newDelay, delay);
                }
                else
                {
                  delay = newDelay;
                }
              }
            }

            try
            {
              if (delay <= 0)
              {
                StaticUtils.DEBUG_LOG.finer("There are no connections with "
                    + "timeout specified. Sleeping");
                available.await();
              }
              else
              {
                StaticUtils.DEBUG_LOG.finer("Sleeping for " + delay + "ms");
                available.await(delay, TimeUnit.MILLISECONDS);
              }
            }
            catch (final InterruptedException e)
            {
              // Just go around again.
            }
          }
        }
        finally
        {
          lock.unlock();
        }
      }
    };

    checkerThread.setDaemon(true);
    checkerThread.start();
  }



  void addConnection(final LDAPConnection connection)
  {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try
    {
      connections.add(connection);
      available.signalAll();
    }
    finally
    {
      lock.unlock();
    }
  }



  void removeConnection(final LDAPConnection connection)
  {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try
    {
      connections.remove(connection);
    }
    finally
    {
      lock.unlock();
    }
  }
}
