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
 *      Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.loggers;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.AccessLogFilteringCriteriaCfgDefn.*;
import org.opends.server.admin.std.meta.AccessLogPublisherCfgDefn.*;
import org.opends.server.admin.std.server.AccessLogFilteringCriteriaCfg;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.Group;
import org.opends.server.authorization.dseecompat.PatternDN;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;



/**
 * This class provides the base implementation of the access loggers used by the
 * directory server.
 *
 * @param <T>
 *          The type of access log publisher configuration.
 */
public abstract class AbstractTextAccessLogPublisher
    <T extends AccessLogPublisherCfg> extends AccessLogPublisher<T>
{
  /**
   * Criteria based filter.
   */
  static final class CriteriaFilter implements Filter
  {
    private final AccessLogFilteringCriteriaCfg cfg;
    private final boolean logConnectRecords;
    private final boolean logDisconnectRecords;
    private final EnumSet<OperationType> logOperationRecords;
    private final Collection<AddressMask> clientAddressEqualTo;
    private final Collection<AddressMask> clientAddressNotEqualTo;
    private final int[] clientPorts;
    private final String[] clientProtocols;
    private final PatternDN[] userDNEqualTo;
    private final PatternDN[] userDNNotEqualTo;
    private final PatternDN[] targetDNEqualTo;
    private final PatternDN[] targetDNNotEqualTo;
    private final DN[] userIsMemberOf;
    private final DN[] userIsNotMemberOf;
    private final String attachmentName;



    /**
     * Creates a new criteria based filter.
     *
     * @param cfg
     *          The access log filter criteria.
     * @throws ConfigException
     *           If the configuration cannot be parsed.
     */
    CriteriaFilter(final AccessLogFilteringCriteriaCfg cfg)
        throws ConfigException
    {
      this.cfg = cfg;

      // Generate a unique identifier for attaching partial results to
      // operations.
      attachmentName = this.getClass().getName() + "#" + hashCode();

      // Pre-parse the log record types for more efficient queries.
      if (cfg.getLogRecordType().isEmpty())
      {
        logConnectRecords = true;
        logDisconnectRecords = true;

        logOperationRecords = EnumSet.allOf(OperationType.class);
      }
      else
      {
        logConnectRecords = cfg.getLogRecordType().contains(
            LogRecordType.CONNECT);
        logDisconnectRecords = cfg.getLogRecordType().contains(
            LogRecordType.DISCONNECT);

        logOperationRecords = EnumSet.noneOf(OperationType.class);
        for (final LogRecordType type : cfg.getLogRecordType())
        {
          switch (type)
          {
          case ABANDON:
            logOperationRecords.add(OperationType.ABANDON);
            break;
          case ADD:
            logOperationRecords.add(OperationType.ADD);
            break;
          case BIND:
            logOperationRecords.add(OperationType.BIND);
            break;
          case COMPARE:
            logOperationRecords.add(OperationType.COMPARE);
            break;
          case DELETE:
            logOperationRecords.add(OperationType.DELETE);
            break;
          case EXTENDED:
            logOperationRecords.add(OperationType.EXTENDED);
            break;
          case MODIFY:
            logOperationRecords.add(OperationType.MODIFY);
            break;
          case RENAME:
            logOperationRecords.add(OperationType.MODIFY_DN);
            break;
          case SEARCH:
            logOperationRecords.add(OperationType.SEARCH);
            break;
          case UNBIND:
            logOperationRecords.add(OperationType.UNBIND);
            break;
          default: // Ignore CONNECT/DISCONNECT
            break;
          }
        }
      }

      // The list of ports is likely to be small and a simple array lookup will
      // be more efficient, avoiding auto-boxing conversions as well.
      clientPorts = new int[cfg.getConnectionPortEqualTo().size()];
      int i = 0;
      for (final Integer port : cfg.getConnectionPortEqualTo())
      {
        clientPorts[i++] = port;
      }

      clientProtocols = new String[cfg.getConnectionProtocolEqualTo().size()];
      i = 0;
      for (final String protocol : cfg.getConnectionProtocolEqualTo())
      {
        clientProtocols[i++] = toLowerCase(protocol);
      }

      clientAddressEqualTo = cfg.getConnectionClientAddressEqualTo();
      clientAddressNotEqualTo = cfg.getConnectionClientAddressNotEqualTo();

      userDNEqualTo = new PatternDN[cfg.getUserDNEqualTo().size()];
      i = 0;
      for (final String s : cfg.getUserDNEqualTo())
      {
        try
        {
          userDNEqualTo[i++] = PatternDN.decode(s);
        }
        catch (final DirectoryException e)
        {
          final Message m = ERR_CONFIG_LOGGING_INVALID_USER_DN_PATTERN.get(
              String.valueOf(cfg.dn()), s);
          throw new ConfigException(m);
        }
      }

      userDNNotEqualTo = new PatternDN[cfg.getUserDNNotEqualTo().size()];
      i = 0;
      for (final String s : cfg.getUserDNNotEqualTo())
      {
        try
        {
          userDNNotEqualTo[i++] = PatternDN.decode(s);
        }
        catch (final DirectoryException e)
        {
          final Message m = ERR_CONFIG_LOGGING_INVALID_USER_DN_PATTERN.get(
              String.valueOf(cfg.dn()), s);
          throw new ConfigException(m);
        }
      }

      userIsMemberOf = cfg.getUserIsMemberOf().toArray(new DN[0]);
      userIsNotMemberOf = cfg.getUserIsNotMemberOf().toArray(new DN[0]);

      targetDNEqualTo = new PatternDN[cfg.getRequestTargetDNEqualTo().size()];
      i = 0;
      for (final String s : cfg.getRequestTargetDNEqualTo())
      {
        try
        {
          targetDNEqualTo[i++] = PatternDN.decode(s);
        }
        catch (final DirectoryException e)
        {
          final Message m = ERR_CONFIG_LOGGING_INVALID_TARGET_DN_PATTERN.get(
              String.valueOf(cfg.dn()), s);
          throw new ConfigException(m);
        }
      }

      targetDNNotEqualTo = new PatternDN[cfg.getRequestTargetDNNotEqualTo()
          .size()];
      i = 0;
      for (final String s : cfg.getRequestTargetDNNotEqualTo())
      {
        try
        {
          targetDNNotEqualTo[i++] = PatternDN.decode(s);
        }
        catch (final DirectoryException e)
        {
          final Message m = ERR_CONFIG_LOGGING_INVALID_TARGET_DN_PATTERN.get(
              String.valueOf(cfg.dn()), s);
          throw new ConfigException(m);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      if (!logConnectRecords)
      {
        return false;
      }

      if (!filterClientConnection(connection))
      {
        return false;
      }

      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      if (!logDisconnectRecords)
      {
        return false;
      }

      if (!filterClientConnection(connection))
      {
        return false;
      }

      if (!filterUser(connection))
      {
        return false;
      }

      return true;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestLoggable(final Operation operation)
    {
      final ClientConnection connection = operation.getClientConnection();
      final boolean matches = logOperationRecords.contains(operation
          .getOperationType())
          && filterClientConnection(connection)
          && filterUser(connection) && filterRequest(operation);

      // Cache the result so that it does not need to be recomputed for the
      // response.
      operation.setAttachment(attachmentName, matches);

      return matches;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseLoggable(final Operation operation)
    {
      // First check the result that was computed for the initial request.
      Boolean requestMatched = (Boolean) operation
          .getAttachment(attachmentName);
      if (requestMatched == null)
      {
        // This should not happen.
        if (debugEnabled())
        {
          TRACER.debugWarning(
              "Operation attachment %s not found while logging response",
              attachmentName);
        }
        requestMatched = isRequestLoggable(operation);
      }

      if (!requestMatched)
      {
        return false;
      }

      // Check the response parameters.
      if (!filterResponse(operation))
      {
        return false;
      }

      return true;
    }



    private boolean filterClientConnection(final ClientConnection connection)
    {
      // Check protocol.
      if (clientProtocols.length > 0)
      {
        boolean found = false;
        final String protocol = toLowerCase(connection.getProtocol());
        for (final String p : clientProtocols)
        {
          if (protocol.equals(p))
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          return false;
        }
      }

      // Check server port.
      if (clientPorts.length > 0)
      {
        boolean found = false;
        final int port = connection.getServerPort();
        for (final int p : clientPorts)
        {
          if (port == p)
          {
            found = true;
            break;
          }
        }
        if (!found)
        {
          return false;
        }
      }

      // Check client address.
      final InetAddress ipAddr = connection.getRemoteAddress();
      if (!clientAddressNotEqualTo.isEmpty()
          && AddressMask.maskListContains(ipAddr, clientAddressNotEqualTo))
      {
        return false;
      }
      if (!clientAddressEqualTo.isEmpty()
          && !AddressMask.maskListContains(ipAddr, clientAddressEqualTo))
      {
        return false;
      }

      return true;
    }



    private boolean filterRequest(final Operation operation)
    {
      // Check target DN.
      if (targetDNNotEqualTo.length > 0 || targetDNEqualTo.length > 0)
      {
        if (!filterRequestTargetDN(operation))
        {
          return false;
        }
      }

      // TODO: check required controls.

      return true;
    }



    private boolean filterRequestTargetDN(final Operation operation)
    {
      // Obtain both the parsed and unparsed target DNs. Requests are logged
      // before parsing so usually only the raw unparsed target DN will be
      // present, and it may even be invalid.
      DN targetDN = null;
      ByteString rawTargetDN = null;

      switch (operation.getOperationType())
      {
      case ABANDON:
      case UNBIND:
        // These operations don't have parameters which we can filter so
        // always match them.
        return true;
      case EXTENDED:
        // These operations could have parameters which can be filtered but
        // we'd need to decode the request in order to find out. This is
        // beyond the scope of the access log. Therefore, treat extended
        // operations like abandon/unbind.
        return true;
      case ADD:
        targetDN = ((AddOperation) operation).getEntryDN();
        rawTargetDN = ((AddOperation) operation).getRawEntryDN();
        break;
      case BIND:
        // For SASL bind operations the bind DN, if provided, will require the
        // SASL credentials to be decoded which is beyond the scope of the
        // access log.
        targetDN = ((BindOperation) operation).getBindDN();
        rawTargetDN = ((BindOperation) operation).getRawBindDN();
        break;
      case COMPARE:
        targetDN = ((CompareOperation) operation).getEntryDN();
        rawTargetDN = ((CompareOperation) operation).getRawEntryDN();
        break;
      case DELETE:
        targetDN = ((DeleteOperation) operation).getEntryDN();
        rawTargetDN = ((DeleteOperation) operation).getRawEntryDN();
        break;
      case MODIFY:
        targetDN = ((ModifyOperation) operation).getEntryDN();
        rawTargetDN = ((ModifyOperation) operation).getRawEntryDN();
        break;
      case MODIFY_DN:
        targetDN = ((ModifyDNOperation) operation).getEntryDN();
        rawTargetDN = ((ModifyDNOperation) operation).getRawEntryDN();
        break;
      case SEARCH:
        targetDN = ((SearchOperation) operation).getBaseDN();
        rawTargetDN = ((SearchOperation) operation).getRawBaseDN();
        break;
      }

      // Attempt to parse the raw target DN if needed.
      if (targetDN == null)
      {
        try
        {
          targetDN = DN.decode(rawTargetDN);
        }
        catch (final DirectoryException e)
        {
          // The DN raw target DN was invalid. It will never match any
          // not-equal-to nor equal-to patterns, so return appropriate result.
          if (targetDNEqualTo.length != 0)
          {
            // Invalid DN will never match equal-to patterns.
            return false;
          }
          else
          {
            // Invalid DN does not match any not-equal-to patterns.
            return true;
          }
        }
      }

      if (targetDNNotEqualTo.length > 0)
      {
        for (final PatternDN pattern : targetDNNotEqualTo)
        {
          if (pattern.matchesDN(targetDN))
          {
            return false;
          }
        }
      }

      if (targetDNEqualTo.length > 0)
      {
        for (final PatternDN pattern : targetDNEqualTo)
        {
          if (pattern.matchesDN(targetDN))
          {
            return true;
          }
        }
      }

      // The target DN did not match.
      return false;
    }



    private boolean filterResponse(final Operation operation)
    {
      // Check response code.
      final Integer resultCode = operation.getResultCode().getIntValue();

      if (!cfg.getResponseResultCodeNotEqualTo().isEmpty())
      {
        if (cfg.getResponseResultCodeNotEqualTo().contains(resultCode))
        {
          return false;
        }
      }

      if (!cfg.getResponseResultCodeEqualTo().isEmpty())
      {
        if (!cfg.getResponseResultCodeNotEqualTo().contains(resultCode))
        {
          return false;
        }
      }

      // Check etime.
      final long etime = operation.getProcessingTime();

      final Integer etimeGT = cfg.getResponseEtimeLessThan();
      if (etimeGT != null)
      {
        if (etime <= ((long) etimeGT))
        {
          return false;
        }
      }

      final Integer etimeLT = cfg.getResponseEtimeLessThan();
      if (etimeLT != null)
      {
        if (etime >= ((long) etimeLT))
        {
          return false;
        }
      }

      // Check search response fields.
      if (operation instanceof SearchOperation)
      {
        final SearchOperation searchOperation = (SearchOperation) operation;
        final Boolean isIndexed = cfg.isSearchResponseIsIndexed();
        if (isIndexed != null)
        {
          boolean wasUnindexed = false;
          for (final AdditionalLogItem item : operation.getAdditionalLogItems())
          {
            if (item.getKey().equals("unindexed"))
            {
              wasUnindexed = true;
              break;
            }
          }

          if (isIndexed)
          {
            if (wasUnindexed)
            {
              return false;
            }
          }
          else
          {
            if (!wasUnindexed)
            {
              return false;
            }
          }
        }

        final int nentries = searchOperation.getEntriesSent();

        final Integer nentriesGT = cfg.getSearchResponseNentriesGreaterThan();
        if (nentriesGT != null)
        {
          if (nentries <= nentriesGT)
          {
            return false;
          }
        }

        final Integer nentriesLT = cfg.getSearchResponseNentriesLessThan();
        if (nentriesLT != null)
        {
          if (nentries >= nentriesLT)
          {
            return false;
          }
        }
      }

      return true;
    }



    private boolean filterUser(final ClientConnection connection)
    {
      // Check user DN.
      if (userDNNotEqualTo.length > 0 || userDNEqualTo.length > 0)
      {
        if (!filterUserBindDN(connection))
        {
          return false;
        }
      }

      // Check group membership.
      if (userIsNotMemberOf.length > 0 || userIsMemberOf.length > 0)
      {
        if (!filterUserIsMemberOf(connection))
        {
          return false;
        }
      }

      return true;
    }



    private boolean filterUserBindDN(final ClientConnection connection)
    {
      final DN userDN = connection.getAuthenticationInfo()
          .getAuthenticationDN();

      // Fast-path for unauthenticated clients.
      if (userDN == null)
      {
        return userDNEqualTo.length == 0;
      }

      if (userDNNotEqualTo.length > 0)
      {
        for (final PatternDN pattern : userDNNotEqualTo)
        {
          if (pattern.matchesDN(userDN))
          {
            return false;
          }
        }
      }

      if (userDNEqualTo.length > 0)
      {
        for (final PatternDN pattern : userDNEqualTo)
        {
          if (pattern.matchesDN(userDN))
          {
            return true;
          }
        }
      }

      // The user DN did not match.
      return false;
    }



    private boolean filterUserIsMemberOf(final ClientConnection connection)
    {
      final Entry userEntry = connection.getAuthenticationInfo()
          .getAuthenticationEntry();

      // Fast-path for unauthenticated clients.
      if (userEntry == null)
      {
        return userIsMemberOf.length == 0;
      }

      final GroupManager groupManager = DirectoryServer.getGroupManager();
      if (userIsNotMemberOf.length > 0)
      {
        for (final DN groupDN : userIsNotMemberOf)
        {
          final Group<?> group = groupManager.getGroupInstance(groupDN);
          try
          {
            if ((group != null) && group.isMember(userEntry))
            {
              return false;
            }
          }
          catch (final DirectoryException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }

      if (userIsMemberOf.length > 0)
      {
        for (final DN groupDN : userIsMemberOf)
        {
          final Group<?> group = groupManager.getGroupInstance(groupDN);
          try
          {
            if ((group != null) && group.isMember(userEntry))
            {
              return true;
            }
          }
          catch (final DirectoryException e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }

      // The user entry did not match.
      return false;
    }

  }



  /**
   * Log message filter predicate.
   */
  static interface Filter
  {
    /**
     * Returns {@code true} if the provided client connect should be logged.
     *
     * @param connection
     *          The client connection.
     * @return {@code true} if the provided client connect should be logged.
     */
    boolean isConnectLoggable(ClientConnection connection);



    /**
     * Returns {@code true} if the provided client disconnect should be logged.
     *
     * @param connection
     *          The client connection.
     * @return {@code true} if the provided client disconnect should be logged.
     */
    boolean isDisconnectLoggable(ClientConnection connection);



    /**
     * Returns {@code true} if the provided request should be logged.
     *
     * @param operation
     *          The request.
     * @return {@code true} if the provided request should be logged.
     */
    boolean isRequestLoggable(Operation operation);



    /**
     * Returns {@code true} if the provided response should be logged.
     *
     * @param operation
     *          The response.
     * @return {@code true} if the provided response should be logged.
     */
    boolean isResponseLoggable(Operation operation);
  }



  /**
   * A filter which performs a logical OR over a set of sub-filters.
   */
  static final class OrFilter implements Filter
  {
    private final Filter[] subFilters;



    /**
     * Creates a new OR filter.
     *
     * @param subFilters
     *          The sub-filters.
     */
    OrFilter(final Filter[] subFilters)
    {
      this.subFilters = subFilters;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isConnectLoggable(connection))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isDisconnectLoggable(connection))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestLoggable(final Operation operation)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isRequestLoggable(operation))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseLoggable(final Operation operation)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isResponseLoggable(operation))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }

  }



  /**
   * The root filter which first checks the logger configuration, delegating to
   * a sub-filter if needed.
   */
  static final class RootFilter implements Filter
  {
    private final Filter subFilter;
    private final boolean suppressInternalOperations;
    private final boolean suppressSynchronizationOperations;
    private final FilteringPolicy policy;



    /**
     * Creates a new root filter.
     *
     * @param suppressInternal
     *          Indicates whether internal operations should be suppressed.
     * @param suppressSynchronization
     *          Indicates whether sync operations should be suppressed.
     * @param policy
     *          The filtering policy.
     * @param subFilter
     *          The sub-filters.
     */
    RootFilter(final boolean suppressInternal,
        final boolean suppressSynchronization, final FilteringPolicy policy,
        final Filter subFilter)
    {
      this.suppressInternalOperations = suppressInternal;
      this.suppressSynchronizationOperations = suppressSynchronization;
      this.policy = policy;
      this.subFilter = subFilter;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      if (!connection.isInnerConnection() || !suppressInternalOperations)
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isConnectLoggable(connection);
        case EXCLUSIVE:
          return !subFilter.isConnectLoggable(connection);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      if (!connection.isInnerConnection() || !suppressInternalOperations)
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isDisconnectLoggable(connection);
        case EXCLUSIVE:
          return !subFilter.isDisconnectLoggable(connection);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestLoggable(final Operation operation)
    {
      if (isLoggable(operation))
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isRequestLoggable(operation);
        case EXCLUSIVE:
          return !subFilter.isRequestLoggable(operation);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseLoggable(final Operation operation)
    {
      if (isLoggable(operation))
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isResponseLoggable(operation);
        case EXCLUSIVE:
          return !subFilter.isResponseLoggable(operation);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * Determines whether the provided operation should be logged.
     *
     * @param operation
     *          the operation to check
     * @return true if the operation is loggable, false otherwise
     */
    boolean isLoggable(final Operation operation)
    {
      if (operation.isSynchronizationOperation())
      {
        return !suppressSynchronizationOperations;
      }
      else if (operation.isInnerOperation())
      {
        return !suppressInternalOperations;
      }
      return true;
    }
  }



  /**
   * Configuration change listener.
   */
  private final class ChangeListener implements
      ConfigurationChangeListener<AccessLogPublisherCfg>
  {
    /**
     * {@inheritDoc}
     */
    @Override
    public final ConfigChangeResult applyConfigurationChange(
        final AccessLogPublisherCfg configuration)
    {
      // Update the configuration.
      cfg = configuration;

      // Rebuild the filter using the new configuration and criteria.
      buildFilters();

      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isConfigurationChangeAcceptable(
        final AccessLogPublisherCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return true;
    }
  }



  /**
   * Filter criteria configuration listener.
   */
  private final class FilterListener implements
      ConfigurationChangeListener<AccessLogFilteringCriteriaCfg>,
      ConfigurationAddListener<AccessLogFilteringCriteriaCfg>,
      ConfigurationDeleteListener<AccessLogFilteringCriteriaCfg>
  {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationAdd(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      configuration.addChangeListener(this);
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationChange(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigChangeResult applyConfigurationDelete(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationAddAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return validateConfiguration(configuration, unacceptableReasons);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationChangeAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return validateConfiguration(configuration, unacceptableReasons);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigurationDeleteAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      // Always allow criteria to be deleted.
      return true;
    }



    private boolean validateConfiguration(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      try
      {
        new CriteriaFilter(configuration);
        return true;
      }
      catch (final ConfigException e)
      {
        unacceptableReasons.add(e.getMessageObject());
        return false;
      }
    }
  }



  /**
   * The tracer object for the debug logger.
   */
  protected static final DebugTracer TRACER = getTracer();
  private AccessLogPublisherCfg cfg = null;
  private Filter filter = null;
  private final ChangeListener changeListener = new ChangeListener();
  private final FilterListener filterListener = new FilterListener();



  /**
   * {@inheritDoc}
   */
  @Override
  public final void close()
  {
    try
    {
      close0();
    }
    finally
    {
      if (cfg != null)
      {
        cfg.removeAccessChangeListener(changeListener);
        for (final String criteriaName : cfg.listAccessLogFilteringCriteria())
        {
          try
          {
            cfg.getAccessLogFilteringCriteria(criteriaName)
                .removeChangeListener(filterListener);
          }
          catch (final ConfigException e)
          {
            // Ignore.
          }
        }
        cfg.removeAccessLogFilteringCriteriaAddListener(filterListener);
        cfg.removeAccessLogFilteringCriteriaDeleteListener(filterListener);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final DN getDN()
  {
    return cfg != null ? cfg.dn() : null;
  }



  /**
   * For startup access logger.
   *
   * @param suppressInternal
   *          {@code true} if internal operations should be suppressed.
   */
  protected void buildFilters(final boolean suppressInternal)
  {
    buildFilters(suppressInternal, false, FilteringPolicy.NO_FILTERING);
  }



  /**
   * Release any resources owned by the sub-implementation.
   */
  protected abstract void close0();



  /**
   * Initializes the filter configuration. This method must be called by
   * sub-classes during initialization.
   *
   * @param config
   *          The access publisher configuration that contains the information
   *          to use to initialize this access publisher.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of performing
   *           the initialization as a result of the server configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not related to
   *           the server configuration.
   */
  protected final void initializeFilters(final T config)
      throws ConfigException, InitializationException
  {
    // Now initialize filters and listeners.
    cfg = config;

    // Rebuild the filter using the new configuration and criteria.
    buildFilters();

    // Add change listeners.
    for (final String criteriaName : cfg.listAccessLogFilteringCriteria())
    {
      try
      {
        cfg.getAccessLogFilteringCriteria(criteriaName).addChangeListener(
            filterListener);
      }
      catch (final ConfigException e)
      {
        // Ignore.
      }
    }
    cfg.addAccessLogFilteringCriteriaAddListener(filterListener);
    cfg.addAccessLogFilteringCriteriaDeleteListener(filterListener);
    cfg.addAccessChangeListener(changeListener);
  }



  /**
   * Returns {@code true} if the provided client connect should be logged.
   *
   * @param c
   *          The client connection.
   * @return {@code true} if the provided client connect should be logged.
   */
  protected final boolean isConnectLoggable(final ClientConnection c)
  {
    return filter.isConnectLoggable(c);
  }



  /**
   * Returns {@code true} if the provided client disconnect should be logged.
   *
   * @param c
   *          The client connection.
   * @return {@code true} if the provided client disconnect should be logged.
   */
  protected final boolean isDisconnectLoggable(final ClientConnection c)
  {
    return filter.isDisconnectLoggable(c);
  }



  /**
   * Perform any initialization required by the sub-implementation.
   *
   * @param config
   *          The access publisher configuration that contains the information
   *          to use to initialize this access publisher.
   * @param unacceptableReasons
   *          A list that may be used to hold the reasons that the provided
   *          configuration is not acceptable.
   * @return {@code true} if the provided configuration is acceptable for this
   *         access log publisher, or {@code false} if not.
   */
  protected final boolean isFilterConfigurationAcceptable(final T config,
      final List<Message> unacceptableReasons)
  {
    for (final String criteriaName : config.listAccessLogFilteringCriteria())
    {
      try
      {
        final AccessLogFilteringCriteriaCfg criteriaCfg = config
            .getAccessLogFilteringCriteria(criteriaName);
        new CriteriaFilter(criteriaCfg);
      }
      catch (final ConfigException e)
      {
        unacceptableReasons.add(e.getMessageObject());
        return false;
      }
    }
    return true;
  }



  /**
   * Returns {@code true} if the provided request should be logged.
   *
   * @param o
   *          The request.
   * @return {@code true} if the provided request should be logged.
   */
  protected final boolean isRequestLoggable(final Operation o)
  {
    return filter.isRequestLoggable(o);
  }



  /**
   * Returns {@code true} if the provided response should be logged.
   *
   * @param o
   *          The response.
   * @return {@code true} if the provided response should be logged.
   */
  protected final boolean isResponseLoggable(final Operation o)
  {
    return filter.isResponseLoggable(o);
  }



  // Build an appropriate set of filters based on the configuration.
  private void buildFilters()
  {
    buildFilters(cfg.isSuppressInternalOperations(),
        cfg.isSuppressSynchronizationOperations(), cfg.getFilteringPolicy());
  }



  private void buildFilters(final boolean suppressInternal,
      final boolean suppressSynchronization, final FilteringPolicy policy)
  {
    final ArrayList<Filter> subFilters = new ArrayList<Filter>();
    if (cfg != null)
    {
      for (final String criteriaName : cfg.listAccessLogFilteringCriteria())
      {
        try
        {
          final AccessLogFilteringCriteriaCfg criteriaCfg = cfg
              .getAccessLogFilteringCriteria(criteriaName);
          subFilters.add(new CriteriaFilter(criteriaCfg));
        }
        catch (final ConfigException e)
        {
          // This should not happen if the configuration has already been
          // validated.
          throw new RuntimeException(e);
        }
      }
    }
    final Filter orFilter = new OrFilter(subFilters.toArray(new Filter[0]));
    filter = new RootFilter(suppressInternal, suppressSynchronization, policy,
        orFilter);
  }

}
