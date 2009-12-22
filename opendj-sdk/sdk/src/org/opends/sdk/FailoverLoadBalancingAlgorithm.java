package org.opends.sdk;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Dec 15, 2009 Time: 5:42:01
 * PM To change this template use File | Settings | File Templates.
 */
public class FailoverLoadBalancingAlgorithm
    extends AbstractLoadBalancingAlgorithm
{
  public FailoverLoadBalancingAlgorithm(ConnectionFactory<?>... factories)
  {
    super(factories);
  }

  public ConnectionFactory<?> getNextConnectionFactory()
  {
    for(MonitoredConnectionFactory f : factoryList)
    {
      if(f.isOperational())
      {
        return f;
      }
    }
    return null;
  }
}
