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

package org.opends.sdk;



import com.sun.opends.sdk.util.Validator;
import com.sun.opends.sdk.util.StaticUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;



/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Dec 15, 2009 Time:
 * 3:49:17 PM To change this template use File | Settings | File
 * Templates.
 */
public abstract class AbstractLoadBalancingAlgorithm implements
    LoadBalancingAlgorithm
{
  protected final List<MonitoredConnectionFactory> factoryList;



  protected AbstractLoadBalancingAlgorithm(
      ConnectionFactory<?>... factories)
  {
    Validator.ensureNotNull((Object[]) factories);
    factoryList = new ArrayList<MonitoredConnectionFactory>(
        factories.length);
    for (ConnectionFactory<?> f : factories)
    {
      factoryList.add(new MonitoredConnectionFactory(f));
    }

    new MonitorThread().start();
  }



  protected class MonitoredConnectionFactory extends
      AbstractConnectionFactory<AsynchronousConnection> implements
      ResultHandler<AsynchronousConnection>
  {
    private final ConnectionFactory<?> factory;

    private volatile boolean isOperational;

    private volatile FutureResult<?> pendingConnectFuture;



    private MonitoredConnectionFactory(ConnectionFactory<?> factory)
    {
      this.factory = factory;
      this.isOperational = true;
    }



    public boolean isOperational()
    {
      return isOperational;
    }



    public void handleErrorResult(ErrorResultException error)
    {
      isOperational = false;
    }



    public void handleResult(AsynchronousConnection result)
    {
      isOperational = true;
      // TODO: Notify the server is back up
      result.close();
    }



    public FutureResult<? extends AsynchronousConnection> getAsynchronousConnection(
        final ResultHandler<? super AsynchronousConnection> resultHandler)
    {
      ResultHandler<AsynchronousConnection> handler = new ResultHandler<AsynchronousConnection>()
      {
        public void handleErrorResult(ErrorResultException error)
        {
          isOperational = false;
          if (resultHandler != null)
          {
            resultHandler.handleErrorResult(error);
          }
          if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
          {
            StaticUtils.DEBUG_LOG.warning(String
                .format("Connection factory " + factory
                    + " is no longer operational: "
                    + error.getMessage()));
          }
        }



        public void handleResult(AsynchronousConnection result)
        {
          isOperational = true;
          if (resultHandler != null)
          {
            resultHandler.handleResult(result);
          }
          if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
          {
            StaticUtils.DEBUG_LOG.warning(String
                .format("Connection factory " + factory
                    + " is now operational"));
          }
        }
      };
      return factory.getAsynchronousConnection(handler);
    }
  }



  private class MonitorThread extends Thread
  {
    private MonitorThread()
    {
      super("Connection Factory Health Monitor");
      this.setDaemon(true);
    }



    public void run()
    {
      while (true)
      {
        for (MonitoredConnectionFactory f : factoryList)
        {
          if (!f.isOperational
              && (f.pendingConnectFuture == null || f.pendingConnectFuture
                  .isDone()))
          {
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
            {
              StaticUtils.DEBUG_LOG.finest(String
                  .format("Attempting connect on factory " + f));
            }
            f.pendingConnectFuture = f.factory
                .getAsynchronousConnection(f);
          }
        }
        try
        {
          sleep(10000);
        }
        catch (InterruptedException e)
        {
          // Ignore and just go around again...
        }
      }
    }
  }
}
