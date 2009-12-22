package org.opends.sdk;

import com.sun.opends.sdk.util.Validator;
import com.sun.opends.sdk.util.AbstractFutureResult;

import org.opends.sdk.responses.Responses;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Dec 15, 2009 Time: 3:23:52
 * PM To change this template use File | Settings | File Templates.
 */
public class LoadBalancingConnectionFactory
    extends AbstractConnectionFactory<AsynchronousConnection>
{
  private final LoadBalancingAlgorithm algorithm;

  public LoadBalancingConnectionFactory(LoadBalancingAlgorithm algorithm)
  {
    Validator.ensureNotNull(algorithm);
    this.algorithm = algorithm;
  }

  public FutureResult<? extends AsynchronousConnection>
  getAsynchronousConnection(
      ResultHandler<? super AsynchronousConnection> resultHandler)
  {
    ConnectionFactory<?> factory = algorithm.getNextConnectionFactory();
    if(factory == null)
    {
      AbstractFutureResult<AsynchronousConnection> future =
          new AbstractFutureResult<AsynchronousConnection>(resultHandler)
      {
        public int getRequestID()
        {
          return -1;
        }
      };
      future.handleErrorResult(new ErrorResultException(
          Responses.newResult(ResultCode.CLIENT_SIDE_CONNECT_ERROR).
              setDiagnosticMessage("No connection factories available")));
      return future;
    }

    return factory.getAsynchronousConnection(resultHandler);
  }
}
