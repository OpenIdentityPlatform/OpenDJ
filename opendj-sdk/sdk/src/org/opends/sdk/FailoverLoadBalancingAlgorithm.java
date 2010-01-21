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

package org.opends.sdk;



import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.opends.sdk.responses.Responses;

import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.Validator;



/**
 * A fail-over load balancing algorithm provides fault tolerance across
 * multiple underlying connection factories.
 * <p>
 * If a problem occurs that temporarily prevents connections from being
 * obtained for one of the connection factories, then this algorithm
 * "fails over" to another operational connection factory in the list.
 * If none of the connection factories are operational then a {@code
 * ConnectionException} is returned to the client.
 * <p>
 * The implementation periodically attempts to connect to failed
 * connection factories in order to determine if they have become
 * available again.
 */
class FailoverLoadBalancingAlgorithm implements LoadBalancingAlgorithm
{
  private final List<MonitoredConnectionFactory> monitoredFactories;



  private static final class MonitoredConnectionFactory extends
      AbstractConnectionFactory implements
      ResultHandler<AsynchronousConnection>
  {
    private final ConnectionFactory factory;

    private volatile boolean isOperational;

    private volatile FutureResult<?> pendingConnectFuture;



    private MonitoredConnectionFactory(ConnectionFactory factory)
    {
      this.factory = factory;
      this.isOperational = true;
    }



    private boolean isOperational()
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



    public FutureResult<AsynchronousConnection> getAsynchronousConnection(
        final ResultHandler<AsynchronousConnection> resultHandler)
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
        for (MonitoredConnectionFactory f : monitoredFactories)
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
          // Termination requested - exit.
          break;
        }
      }
    }
  }



  /**
   * Creates a new fail-over load balancing algorithm which will
   * fail-over across the provided list of connection factories.
   *
   * @param factories
   *          The connection factories which will be used for fail-over.
   */
  public FailoverLoadBalancingAlgorithm(ConnectionFactory... factories)
  {
    Validator.ensureNotNull((Object[]) factories);

    monitoredFactories = new ArrayList<MonitoredConnectionFactory>(
        factories.length);
    for (ConnectionFactory f : factories)
    {
      monitoredFactories.add(new MonitoredConnectionFactory(f));
    }

    new MonitorThread().start();
  }



  /**
   * Creates a new fail-over load balancing algorithm which will
   * fail-over across the provided collection of connection factories.
   *
   * @param factories
   *          The connection factories which will be used for fail-over.
   */
  public FailoverLoadBalancingAlgorithm(
      Collection<ConnectionFactory> factories)
  {
    Validator.ensureNotNull(factories);

    monitoredFactories = new ArrayList<MonitoredConnectionFactory>(
        factories.size());
    for (ConnectionFactory f : factories)
    {
      monitoredFactories.add(new MonitoredConnectionFactory(f));
    }

    new MonitorThread().start();
  }



  public ConnectionFactory getNextConnectionFactory()
      throws ErrorResultException
  {
    for (MonitoredConnectionFactory f : monitoredFactories)
    {
      if (f.isOperational())
      {
        return f;
      }
    }

    throw ErrorResultException.wrap(Responses.newResult(
        ResultCode.CLIENT_SIDE_CONNECT_ERROR).setDiagnosticMessage(
        "No operational connection factories available"));
  }
}
