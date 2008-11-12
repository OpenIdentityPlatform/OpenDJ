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
 *    Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.NetworkGroupResourceLimitsCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;

import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.types.operation.PreParseSearchOperation;
import static org.opends.messages.CoreMessages.*;


/**
 * This class defines the resource limits applicable to all
 * connections inside the same network group.
 */
public class ResourceLimits
        implements ConfigurationAddListener<NetworkGroupResourceLimitsCfg>,
                   ConfigurationDeleteListener<NetworkGroupResourceLimitsCfg>,
                   ConfigurationChangeListener<NetworkGroupResourceLimitsCfg>
{
  // The resource limits are defined through the config
  private boolean isConfigured = false;

  // The maximum number of connections in the network group
  private int maxConnections;

  // The maximum number of connections coming from the same IP address
  private int maxConnectionsFromSameIP;

  // The maximum number of operations per connection
  private int maxOpsPerConnection;

  // The maximum number of concurrent operations per connection
  private int maxConcurrentOpsPerConnection;

  // The maximum size for a search
  private int searchSizeLimit;

  // The maximum duration for a search
  private int searchTimeLimit;

  // The minimum substring length in a search
  private int minSearchSubstringLength;

  // The number of connections in the group
  private int numConnections = 0;

  // The maximum number of simultaneous connections in the group
  // since group creation
  private int maxNumConnections = 0;

  // The total number of connections managed by the group
  private int totalNumConnections = 0;

  // Map containing the connections sorted by incoming IP address
  HashMap<String, Integer> connectionsPerIpMap = new HashMap<String, Integer>();

  // The lock for the counter numConnections and the map connectionsPerIpMap
  Object connMutex = new Object();

  // The current configuration
  private NetworkGroupResourceLimitsCfg config = null;

  /**
   * Constructor.
   *
   * @param resourceLimitsCfg configuration
   */
  public ResourceLimits(NetworkGroupResourceLimitsCfg resourceLimitsCfg) {
    createLimits(resourceLimitsCfg);
  }

  /**
   * Resets all the fields.
   */
  private void resetLimits() {
    maxConnections = 0;
    maxConnectionsFromSameIP = 0;
    maxOpsPerConnection = 0;
    maxConcurrentOpsPerConnection = 0;

    searchSizeLimit = -1;
    searchTimeLimit = -1;
    minSearchSubstringLength = 0;
    isConfigured = false;
    if (config != null) {
      config.removeChangeListener(this);
      config = null;
    }
  }

  /**
   * Creates a ResourceLimits from a configuration object.
   *
   * @param resourcesCfg the configuration
   */
  private void createLimits(NetworkGroupResourceLimitsCfg resourcesCfg) {
    if (resourcesCfg != null) {
      maxConnections = resourcesCfg.getMaxConnections();
      maxConnectionsFromSameIP = resourcesCfg.getMaxConnectionsFromSameIP();
      maxOpsPerConnection = resourcesCfg.getMaxOpsPerConnection();
      maxConcurrentOpsPerConnection =
            resourcesCfg.getMaxConcurrentOpsPerConnection();

      Integer tmpSizeLimit = resourcesCfg.getSearchSizeLimit();
      if (tmpSizeLimit != null) {
        searchSizeLimit = tmpSizeLimit;
      } else {
        searchSizeLimit = -1;
      }
      Long tmpTimeLimit = resourcesCfg.getSearchTimeLimit();
      if (tmpTimeLimit != null) {
        searchTimeLimit = tmpTimeLimit.intValue();
      } else {
        searchTimeLimit = -1;
      }
      minSearchSubstringLength = resourcesCfg.getMinSubstringLength();

      if (config == null) {
        resourcesCfg.addChangeListener(this);
      }
      config = resourcesCfg;
      isConfigured = true;
    } else {
      resetLimits();
    }
  }

  /**
   * Sets the maximum number of connections allowed in this network group.
   * @param maxConn The maximum number of connections handled by this
   *                network group
   */
  public void setMaxConnections(int maxConn) {
    maxConnections = maxConn;
  }

  /**
   * Sets the maximum number of connections coming from the same client
   * in this network group.
   * @param maxConnFromSameClient The maximum number of connections coming
   *                              from the same client in this network group
   */
  public void setMaxConnectionsFromSameIP(int maxConnFromSameClient) {
    maxConnectionsFromSameIP = maxConnFromSameClient;
  }

  /**
   * Sets the maximum number of operations performed on the same connection
   * in this network group.
   * @param maxOpsPerConn The maximum number of operations performed on
   *                      the same connection
   */
  public void setMaxOpsPerConnection(int maxOpsPerConn) {
    maxOpsPerConnection = maxOpsPerConn;
  }

  /**
   * Sets the maximum number of concurrent operations performed on the same
   * connection in this network group.
   * @param maxConcurrentOpsPerConn The maximum number of simultaneous
   *                                operations per connection on the same
   *                                connection
   */
  public void setMaxConcurrentOpsPerConnection(int maxConcurrentOpsPerConn) {
    maxConcurrentOpsPerConnection = maxConcurrentOpsPerConn;
  }

  /**
   * Sets the search time limit for operations performed in this network group.
   * @param maxSearchTime The search time limit
   */
  public void setSearchTimeLimit(int maxSearchTime) {
    searchTimeLimit = maxSearchTime;
  }

  /**
   * Sets the search size limit for operations performed in this network group.
   * @param maxSearchSize The search size limit
   */
  public void setSearchSizeLimit(int maxSearchSize) {
    searchSizeLimit = maxSearchSize;
  }

  /**
   * Sets the minimum substring length for a search filter in this network
   * group.
   * @param minLength The minimum substring length
   */
  public void setMinSearchSubstringLength(int minLength) {
    minSearchSubstringLength = minLength;
  }

  /**
   * Returns the maximum number of entries returned by a search operation
   * performed in this network group.
   * @return the maximum number of entries
   */
  public int getSizeLimit() {
      return searchSizeLimit;
  }

  /**
   * Returns the maximum duration for a search operation performed in this
   * network group.
   * @return the maximum duration in ms
   */
  public int getTimeLimit() {
      return searchTimeLimit;
  }

  /**
   * Returns the minimum string length for a substring filter.
   * @return minimum string length
   */
  public int getMinSubstring() {
      return minSearchSubstringLength;
  }

  /**
   * Adds a connection to the resource group.
   *
   * @param connection the ClientConnection to ad
   */
  public void addConnection(ClientConnection connection) {
    synchronized(connMutex) {
      // increment the number of connections managed by the network group
      numConnections++;
      totalNumConnections++;
      if (numConnections > maxNumConnections) {
        maxNumConnections = numConnections;
      }

      // increment the number of connections from the given IP address
      String ip = connection.getClientAddress();
      Integer currentCount = connectionsPerIpMap.get(ip);
      if (currentCount == null) {
        currentCount = new Integer(0);
      }

      connectionsPerIpMap.put(ip, currentCount + 1);
    }
  }

  /**
   * Removes a connection from the nerwork group.
   *
   * @param connection the ClientConnection to remove
   */
  public void removeConnection(ClientConnection connection) {
    synchronized(connMutex) {
      // decrement the number of connections managed by the network group
      numConnections--;

      // decrement the number of connections from the given IP address
      String ip = connection.getClientAddress();
      Integer currentCount = connectionsPerIpMap.get(ip);
      if (currentCount == null) {
        // Should be error!
        currentCount = new Integer(1);
      }
      if (currentCount == 1) {
        // This was the last connection
        connectionsPerIpMap.remove(ip);
      } else {
        connectionsPerIpMap.put(ip, currentCount - 1);
      }
    }
  }

  /**
   * Checks the resource limits.
   *
   * @param connection the ClientConnection to check
   * @param operation the ongoing operation
   * @param fullCheck a boolean indicating if full checks must be done
   * @param messages the messages to include in the disconnect notification
   *                response.  It may be <CODE>null</CODE> if no message
   *                is to be sent.
   * @return a boolean indicating whether the connection is allowed
   */
  public boolean checkLimits(
          ClientConnection connection,
          PreParseOperation operation,
          boolean fullCheck,
          List<Message> messages)
  {
    boolean result = true;

    if (fullCheck) {
      // Check the total number of connections in the resource group
      synchronized(connMutex) {
        if ((maxConnections > 0) && (numConnections > maxConnections)) {
          messages.add(INFO_ERROR_MAX_CONNECTIONS_LIMIT_EXCEEDED.get());
          result = false;
        }
      }
      if (! result) {
        return result;
      }

      // Check the number of connections coming from the same IP
      synchronized(connMutex) {
        // Add the connection in the map
        String ip = connection.getClientAddress();

        Integer currentCount = connectionsPerIpMap.get(ip);
        if (currentCount == null) {
          currentCount = new Integer(0);
        }

        if ((maxConnectionsFromSameIP > 0)
            && (currentCount.intValue() > maxConnectionsFromSameIP)) {
          messages.add(
                  INFO_ERROR_MAX_CONNECTIONS_FROM_SAME_IP_LIMIT_EXCEEDED.get());
          result = false;
        }
      }
      if (! result) {
        return result;
      }
    }

    // Check the max number of operations per connection
    if ((maxOpsPerConnection > 0)
        && (connection.getNumberOfOperations() > maxOpsPerConnection)) {
      messages.add(
              INFO_ERROR_MAX_OPERATIONS_PER_CONNECTION_LIMIT_EXCEEDED.get());
      return false;
    }

    // Check the max number of concurrent operations per connection
    if ((maxConcurrentOpsPerConnection > 0)
      && (connection.getOperationsInProgress().size()
          > maxConcurrentOpsPerConnection)) {
      messages.add(
            INFO_ERROR_MAX_CONCURRENT_OPERATIONS_PER_CONNECTION_LIMIT_EXCEEDED
            .get());
      return false;
    }

    // If the operation is a search, check the min search substring length
    if ((operation != null) && (operation instanceof PreParseSearchOperation)) {
      if (!checkSubstringFilter(
              ((PreParseSearchOperation)operation).getRawFilter())) {
        messages.add(
                INFO_ERROR_MIN_SEARCH_SUBSTRING_LENGTH_LIMIT_EXCEEDED.get());
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether a filter enforces minimum substring length. If the
   * filter is a composed filter (AND, OR, NOT filters), each component
   * of the filter is recursively checked.
   * When the filter is a substring filter, this routine checks that
   * the substring length is greater or equal to the minimum substring
   * length.
   * For other search filter types, true is returned.
   * @param filter The LDAP search filter to be tested
   * @return boolean indicating whether the filter conforms to the
   * minimum substring length rule.
   */
  private boolean checkSubstringFilter(RawFilter filter) {
    switch (filter.getFilterType()) {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents = filter.getFilterComponents();
        if (filterComponents != null) {
          for (RawFilter element : filterComponents) {
            if (!checkSubstringFilter(element)) {
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
        if (subInitialElement != null) {
          length += subInitialElement.stringValue().length();
        }
        ArrayList<ByteString> subAnyElements = filter.getSubAnyElements();
        if (subAnyElements != null) {
          for (ByteString element : subAnyElements) {
            length += element.stringValue().length();
          }
        }
        ByteString subFinalElement = filter.getSubFinalElement();
        if (subFinalElement != null) {
          length += subFinalElement.stringValue().length();
        }
        return (length >= minSearchSubstringLength);
      default:
        return true;
    }
  }

  /**
   * Retrieves the statistics associated to the resource limits.
   * @return the statistics
   */
  public ResourceLimitsStat getStat() {
    ResourceLimitsStat stat;
    synchronized(connMutex) {
      stat = new ResourceLimitsStat(
          numConnections, maxNumConnections, totalNumConnections);
    }
    return stat;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
          NetworkGroupResourceLimitsCfg configuration,
          List<Message> unacceptableReasons) {
    return (!isConfigured);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
          NetworkGroupResourceLimitsCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    createLimits(configuration);
    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
          NetworkGroupResourceLimitsCfg configuration,
          List<Message> unacceptableReasons) {
    return isConfigured;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
          NetworkGroupResourceLimitsCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
      new ConfigChangeResult(resultCode, adminActionRequired, messages);

    resetLimits();
    isConfigured = false;

    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          NetworkGroupResourceLimitsCfg configuration,
          List<Message> unacceptableReasons) {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
          NetworkGroupResourceLimitsCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    createLimits(configuration);
    return configChangeResult;
  }
}
