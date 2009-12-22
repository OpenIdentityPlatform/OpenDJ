package org.opends.sdk;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Dec 15, 2009 Time: 3:37:03
 * PM To change this template use File | Settings | File Templates.
 */
public interface LoadBalancingAlgorithm
{
  public ConnectionFactory<?> getNextConnectionFactory();
}
