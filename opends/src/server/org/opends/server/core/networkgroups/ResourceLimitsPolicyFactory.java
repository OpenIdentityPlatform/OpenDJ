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
 *    Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import static org.opends.messages.CoreMessages.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.ResourceLimitsQOSPolicyCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.QOSPolicyFactory;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.types.operation.PreParseSearchOperation;



/**
 * This class defines a factory for creating user configurable resource
 * limits policies.
 */
public final class ResourceLimitsPolicyFactory implements
    QOSPolicyFactory<ResourceLimitsQOSPolicyCfg>
{

  /**
   * Policy implementation.
   */
  private static final class Policy extends ResourceLimitsPolicy
      implements
      ConfigurationChangeListener<ResourceLimitsQOSPolicyCfg>
  {
    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        ResourceLimitsQOSPolicyCfg configuration)
    {
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;
      ArrayList<Message> messages = new ArrayList<Message>();

      // Save the configuration.
      updateConfiguration(configuration);

      return new ConfigChangeResult(resultCode, adminActionRequired,
          messages);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        ResourceLimitsQOSPolicyCfg configuration,
        List<Message> unacceptableReasons)
    {
      return ResourceLimitsPolicyFactory.validateConfiguration(
          configuration, unacceptableReasons);
    }



    // Map containing the connections sorted by incoming IP address.
    private final HashMap<String, Integer> connectionsPerIPMap =
        new HashMap<String, Integer>();

    // The maximum number of concurrent operations per connection.
    private int maxConcurrentOpsPerConnection;

    // The maximum number of connections in the network group.
    private int maxConnections;

    // The maximum number of connections coming from the same IP
    // address.
    private int maxConnectionsFromSameIP;

    // The maximum number of operations per connection.
    private int maxOpsPerConnection;

    // The minimum substring length in a search.
    private int minSearchSubstringLength;

    // The lock for connections per IP map.
    private final Object mutex = new Object();

    // The maximum size for a search.
    private int sizeLimit;

    // The statistics for the resource limits policy.
    private final ResourceLimitsPolicyStatistics statistics =
        new ResourceLimitsPolicyStatistics();

    // The maximum time for a search.
    private int timeLimit;



    /**
     * Creates a new resource limits policy.
     */
    private Policy()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    void addConnection(ClientConnection connection)
    {
      synchronized (mutex)
      {
        // Update the statistics.
        statistics.addClientConnection();

        // Increment the number of connections from the given IP
        // address.
        String ip = connection.getClientAddress();
        Integer currentCount = connectionsPerIPMap.get(ip);
        if (currentCount == null)
        {
          connectionsPerIPMap.put(ip, 1);
        }
        else
        {
          connectionsPerIPMap.put(ip, currentCount + 1);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    int getMinSubstring()
    {
      return minSearchSubstringLength;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    int getSizeLimit()
    {
      return sizeLimit;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    ResourceLimitsPolicyStatistics getStatistics()
    {
      return statistics;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    int getTimeLimit()
    {
      return timeLimit;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean isAllowed(ClientConnection connection,
        PreParseOperation operation, boolean fullCheck,
        List<Message> messages)
    {
      boolean result = true;

      if (fullCheck)
      {
        // Check the total number of connections in the resource group
        synchronized (mutex)
        {
          if (maxConnections > 0
              && statistics.getClientConnections() > maxConnections)
          {
            messages.add(INFO_ERROR_MAX_CONNECTIONS_LIMIT_EXCEEDED
                .get());
            result = false;
          }
        }
        if (!result)
        {
          return result;
        }

        // Check the number of connections coming from the same IP
        synchronized (mutex)
        {
          // Add the connection in the map
          String ip = connection.getClientAddress();

          Integer currentCount = connectionsPerIPMap.get(ip);
          if (currentCount == null)
          {
            currentCount = new Integer(0);
          }

          if (maxConnectionsFromSameIP > 0
              && currentCount.intValue() > maxConnectionsFromSameIP)
          {
            messages
                .add(INFO_ERROR_MAX_CONNECTIONS_FROM_SAME_IP_LIMIT_EXCEEDED
                    .get());
            result = false;
          }
        }
        if (!result)
        {
          return result;
        }
      }

      // Check the max number of operations per connection
      if (maxOpsPerConnection > 0
          && connection.getNumberOfOperations() > maxOpsPerConnection)
      {
        messages
            .add(INFO_ERROR_MAX_OPERATIONS_PER_CONNECTION_LIMIT_EXCEEDED
                .get());
        return false;
      }

      // Check the max number of concurrent operations per connection
      if (maxConcurrentOpsPerConnection > 0
          && connection.getOperationsInProgress().size()
          > maxConcurrentOpsPerConnection)
      {
        messages.add(
          INFO_ERROR_MAX_CONCURRENT_OPERATIONS_PER_CONNECTION_LIMIT_EXCEEDED
            .get());
        return false;
      }

      // If the operation is a search, check the min search substring
      // length
      if (operation != null
          && operation instanceof PreParseSearchOperation)
      {
        if (!checkSubstringFilter(((PreParseSearchOperation) operation)
            .getRawFilter()))
        {
          messages
              .add(INFO_ERROR_MIN_SEARCH_SUBSTRING_LENGTH_LIMIT_EXCEEDED
                  .get());
          return false;
        }
      }

      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    void removeConnection(ClientConnection connection)
    {
      synchronized (mutex)
      {
        // Update the statistics.
        statistics.removeClientConnection();

        // Decrement the number of connections from the given IP
        // address.
        String ip = connection.getClientAddress();
        Integer currentCount = connectionsPerIPMap.get(ip);
        if (currentCount != null)
        {
          if (currentCount == 1)
          {
            // This was the last connection.
            connectionsPerIPMap.remove(ip);
          }
          else
          {
            connectionsPerIPMap.put(ip, currentCount - 1);
          }
        }
      }
    }



    /**
     * Checks whether a filter enforces minimum substring length. If the
     * filter is a composed filter (AND, OR, NOT filters), each
     * component of the filter is recursively checked. When the filter
     * is a substring filter, this routine checks that the substring
     * length is greater or equal to the minimum substring length. For
     * other search filter types, true is returned.
     *
     * @param filter
     *          The LDAP search filter to be tested
     * @return boolean indicating whether the filter conforms to the
     *         minimum substring length rule.
     */
    private boolean checkSubstringFilter(RawFilter filter)
    {
      switch (filter.getFilterType())
      {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents =
            filter.getFilterComponents();
        if (filterComponents != null)
        {
          for (RawFilter element : filterComponents)
          {
            if (!checkSubstringFilter(element))
            {
              return false;
            }
          }
        }
        return true;
      case NOT:
        return checkSubstringFilter(filter.getNOTComponent());
      case SUBSTRING:
        int length = 0;
        ByteString subInitialElement = filter.getSubInitialElement();
        if (subInitialElement != null)
        {
          length += subInitialElement.stringValue().length();
        }
        ArrayList<ByteString> subAnyElements =
            filter.getSubAnyElements();
        if (subAnyElements != null)
        {
          for (ByteString element : subAnyElements)
          {
            length += element.stringValue().length();
          }
        }
        ByteString subFinalElement = filter.getSubFinalElement();
        if (subFinalElement != null)
        {
          length += subFinalElement.stringValue().length();
        }
        return length >= minSearchSubstringLength;
      default:
        return true;
      }
    }



    // Updates this policy's configuration.
    private void updateConfiguration(
        ResourceLimitsQOSPolicyCfg configuration)
    {
      maxConnections = configuration.getMaxConnections();
      maxConnectionsFromSameIP =
          configuration.getMaxConnectionsFromSameIP();
      maxOpsPerConnection = configuration.getMaxOpsPerConnection();
      maxConcurrentOpsPerConnection =
          configuration.getMaxConcurrentOpsPerConnection();

      Integer tmpSizeLimit = configuration.getSizeLimit();
      if (tmpSizeLimit != null)
      {
        sizeLimit = tmpSizeLimit;
      }
      else
      {
        sizeLimit = DirectoryServer.getSizeLimit();
      }

      Long tmpTimeLimit = configuration.getTimeLimit();
      if (tmpTimeLimit != null)
      {
        timeLimit = tmpTimeLimit.intValue();
      }
      else
      {
        timeLimit = DirectoryServer.getTimeLimit();
      }

      minSearchSubstringLength = configuration.getMinSubstringLength();
    }
  }



  // Validates a configuration.
  private static boolean validateConfiguration(
      ResourceLimitsQOSPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    // Always valid.
    return true;
  }



  /**
   * Creates a new resource limits policy factory.
   */
  public ResourceLimitsPolicyFactory()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  public ResourceLimitsPolicy createQOSPolicy(
      ResourceLimitsQOSPolicyCfg configuration) throws ConfigException,
      InitializationException
  {
    Policy policy = new Policy();

    // Save the configuration.
    policy.updateConfiguration(configuration);

    // Register change listener.
    configuration.addResourceLimitsChangeListener(policy);

    return policy;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAcceptable(
      ResourceLimitsQOSPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    return validateConfiguration(configuration, unacceptableReasons);
  }
}
