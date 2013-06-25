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



import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.
  RequestFilteringQOSPolicyCfgDefn.AllowedOperations;
import org.opends.server.admin.std.meta.
  RequestFilteringQOSPolicyCfgDefn.AllowedSearchScopes;
import org.opends.server.admin.std.server.RequestFilteringQOSPolicyCfg;
import org.opends.server.api.QOSPolicyFactory;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.types.operation.PreParseSearchOperation;



/**
 * This class defines a factory for creating user configurable request
 * filtering policies.
 */
public final class RequestFilteringPolicyFactory implements
    QOSPolicyFactory<RequestFilteringQOSPolicyCfg>
{

  /**
   * Policy implementation.
   */
  private static final class Policy extends RequestFilteringPolicy
      implements
      ConfigurationChangeListener<RequestFilteringQOSPolicyCfg>
  {

    // The list of allowed attributes
    private Set<String> allowedAttributes = null;

    // The list of allowed operations
    private Set<AllowedOperations> allowedOperations = null;

    // The list of allowed search scopes
    private Set<AllowedSearchScopes> allowedSearchScopes = null;

    // The list of allowed subtrees
    private Set<DN> allowedSubtrees = null;

    // The list of prohibited attributes
    private Set<String> prohibitedAttributes = null;

    // The list of prohibited subtrees
    private Set<DN> prohibitedSubtrees = null;

    // The statistics for the request filtering policy
    private final RequestFilteringPolicyStatistics statistics =
        new RequestFilteringPolicyStatistics();



    /**
     * Creates a new request filtering policy.
     */
    private Policy()
    {
      // Nothing to do.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        RequestFilteringQOSPolicyCfg configuration)
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
        RequestFilteringQOSPolicyCfg configuration,
        List<Message> unacceptableReasons)
    {
      return RequestFilteringPolicyFactory.validateConfiguration(
          configuration, unacceptableReasons);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    RequestFilteringPolicyStatistics getStatistics()
    {
      return statistics;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    boolean isAllowed(PreParseOperation operation,
        List<Message> messages)
    {
      boolean allowRequest = true;

      // Check the allowed operations
      if (!allowedOperations.isEmpty())
      {
        switch (operation.getOperationType())
        {
        case ABANDON:
          allowRequest = true;
          break;
        case ADD:
          allowRequest =
              allowedOperations.contains(AllowedOperations.ADD);
          break;
        case BIND:
          allowRequest =
              allowedOperations.contains(AllowedOperations.BIND);
          break;
        case COMPARE:
          allowRequest =
              allowedOperations.contains(AllowedOperations.COMPARE);
          break;
        case DELETE:
          allowRequest =
              allowedOperations.contains(AllowedOperations.DELETE);
          break;
        case EXTENDED:
          allowRequest =
              allowedOperations.contains(AllowedOperations.EXTENDED);
          break;
        case MODIFY:
          allowRequest =
              allowedOperations.contains(AllowedOperations.MODIFY);
          break;
        case MODIFY_DN:
          allowRequest =
              allowedOperations.contains(AllowedOperations.RENAME);
          break;
        case SEARCH:
          allowRequest =
              allowedOperations.contains(AllowedOperations.SEARCH);

          // If inequality search are prohibited, need to check
          if (allowRequest
              && !allowedOperations
                  .contains(AllowedOperations.INEQUALITY_SEARCH))
          {
            RawFilter flt =
                ((PreParseSearchOperation) operation).getRawFilter();
            allowRequest = !containsInequalitySearch(flt);
          }
          break;
        case UNBIND:
          allowRequest = true;
          break;
        }

        if (!allowRequest)
        {
          statistics.updateRejectedOperations();
          messages.add(INFO_ERROR_OPERATION_NOT_ALLOWED.get());
          return allowRequest;
        }
      }

      // For search operations:
      if (operation.getOperationType().equals(OperationType.SEARCH))
      {
        PreParseSearchOperation searchOp =
            (PreParseSearchOperation) operation;

        // Check the allowed/prohibited attributes in search filter
        if (!prohibitedAttributes.isEmpty())
        {
          // The attributes specified in prohibitedAttributes are not OK
          allowRequest =
              !containsProhibitedAttribute(searchOp.getRawFilter());
        }

        if (!allowRequest)
        {
          statistics.updateRejectedAttributes();
          messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
          return allowRequest;
        }

        if (!allowedAttributes.isEmpty())
        {
          // Only the attributes specified in allowedAttributes are OK
          allowRequest =
              containsOnlyAllowedAttributes(searchOp.getRawFilter());
        }

        if (!allowRequest)
        {
          statistics.updateRejectedAttributes();
          messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
          return allowRequest;
        }

        // Check the search scope
        if (!allowedSearchScopes.isEmpty())
        {
          switch (searchOp.getScope())
          {
          case BASE_OBJECT:
            allowRequest =
                allowedSearchScopes.contains(AllowedSearchScopes.BASE);
            break;
          case SINGLE_LEVEL:
            allowRequest =
                allowedSearchScopes.contains(AllowedSearchScopes.ONE);
            break;
          case WHOLE_SUBTREE:
            allowRequest =
                allowedSearchScopes.contains(AllowedSearchScopes.SUB);
            break;
          case SUBORDINATE_SUBTREE:
            allowRequest =
                allowedSearchScopes
                    .contains(AllowedSearchScopes.CHILDREN);
            break;
          }

          if (!allowRequest)
          {
            statistics.updateRejectedScopes();
            messages.add(INFO_ERROR_SEARCH_SCOPE_NOT_ALLOWED.get());
            return allowRequest;
          }
        }
      }

      // For compare operation
      if (operation.getOperationType().equals(OperationType.COMPARE))
      {
        PreParseCompareOperation compareOp =
            (PreParseCompareOperation) operation;

        // Check the allowed/prohibited attributes
        if (!prohibitedAttributes.isEmpty())
        {
          allowRequest =
              !prohibitedAttributes.contains(compareOp
                  .getRawAttributeType());
        }

        if (!allowRequest)
        {
          statistics.updateRejectedAttributes();
          messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
          return allowRequest;
        }

        if (!allowedAttributes.isEmpty())
        {
          allowRequest =
              allowedAttributes.contains(compareOp
                  .getRawAttributeType());
        }

        if (!allowRequest)
        {
          statistics.updateRejectedAttributes();
          messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
          return allowRequest;
        }
      }

      DN entryDN = null;
      DN newEntryDN = null;

      try
      {
        switch (operation.getOperationType())
        {
        case ADD:
          entryDN =
              DN.decode(((PreParseAddOperation) operation)
                  .getRawEntryDN());
          break;
        case COMPARE:
          entryDN =
              DN.decode(((PreParseCompareOperation) operation)
                  .getRawEntryDN());
          break;
        case DELETE:
          entryDN =
              DN.decode(((PreParseDeleteOperation) operation)
                  .getRawEntryDN());
          break;
        case EXTENDED:
          break;
        case MODIFY:
          entryDN =
              DN.decode(((PreParseModifyOperation) operation)
                  .getRawEntryDN());
          break;
        case MODIFY_DN:
          entryDN =
              DN.decode(((PreParseModifyDNOperation) operation)
                  .getRawEntryDN());
          newEntryDN =
              DN.decode(((PreParseModifyDNOperation) operation)
                  .getRawNewRDN());
          break;
        case SEARCH:
          entryDN =
              DN.decode(((PreParseSearchOperation) operation)
                  .getRawBaseDN());
          break;
        default:
          break;
        }

        if (entryDN != null)
        {
          allowRequest =
              isInAllowedSubtrees(entryDN)
                  && !isInProhibitedSubtrees(entryDN);
        }

        if (newEntryDN != null)
        {
          allowRequest =
              isInAllowedSubtrees(newEntryDN)
                  && !isInProhibitedSubtrees(newEntryDN);
        }
      }
      catch (DirectoryException e)
      {
        // Invalid DN - reject the request.
        allowRequest = true;
      }

      if (!allowRequest)
      {
        statistics.updateRejectedSubtrees();
        messages.add(INFO_ERROR_SUBTREE_NOT_ALLOWED.get());
        return allowRequest;
      }

      return true;

    }



    /**
     * Checks whether a filter contains an inequality search filter
     * (i.e. either a greater_or_equal or a less_or_equal filter).
     *
     * @param filter
     *          The filter to be tested
     * @return boolean indicating whether the filter contains an
     *         inequality search filter
     */
    private boolean containsInequalitySearch(RawFilter filter)
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
            if (containsInequalitySearch(element))
            {
              return true;
            }
          }
        }
        return false;
      case NOT:
        return containsInequalitySearch(filter.getNOTComponent());
      case GREATER_OR_EQUAL:
      case LESS_OR_EQUAL:
        return true;
      default:
        return false;
      }

    }



    /**
     * Checks whether a filter contains unallowed attributes.
     *
     * @param filter
     *          The filter to be tested
     * @return boolean indicating whether the filter contains at least
     *         one attribute which is not in the allowed list
     */
    private boolean containsOnlyAllowedAttributes(RawFilter filter)
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
            if (!containsOnlyAllowedAttributes(element))
            {
              return false;
            }
          }
        }
        return true;
      case NOT:
        return containsOnlyAllowedAttributes(filter.getNOTComponent());
      default:
        return allowedAttributes.contains(filter.getAttributeType());
      }
    }



    /**
     * Checks whether a filter contains one of the prohibited
     * attributes.
     *
     * @param filter
     *          The filter to be tested
     * @return boolean indicating whether the filter contains at least
     *         one of the prohibited attributes
     */
    private boolean containsProhibitedAttribute(RawFilter filter)
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
            if (containsProhibitedAttribute(element))
            {
              return true;
            }
          }
        }
        return false;
      case NOT:
        return containsProhibitedAttribute(filter.getNOTComponent());
      default:
        return prohibitedAttributes.contains(filter.getAttributeType());
      }
    }



    /**
     * Checks whether a DN is in a branch of the allowed subtrees.
     *
     * @param dn
     *          The DN to be tested
     * @return boolean indicating whether the dn is in a branch of the
     *         allowed subtrees
     */
    private boolean isInAllowedSubtrees(DN dn)
    {
      boolean result = false;
      // If the variable is not set, consider allowedSubtrees = rootDSE
      if (allowedSubtrees.isEmpty())
      {
        return true;
      }
      for (DN branch : allowedSubtrees)
      {
        if (dn.isDescendantOf(branch))
        {
          result = true;
          break;
        }
      }
      return result;
    }



    /**
     * Checks whether a DN is in a branch of the prohibited subtrees.
     *
     * @param dn
     *          The Dn to be tested
     * @return boolean indicating whether the dn is in a branch of the
     *         prohibited subtrees
     */
    private boolean isInProhibitedSubtrees(DN dn)
    {
      boolean result = false;
      for (DN branch : prohibitedSubtrees)
      {
        if (dn.isDescendantOf(branch))
        {
          result = true;
          break;
        }
      }
      return result;
    }



    // Updates this policy's configuration.
    private void updateConfiguration(
        RequestFilteringQOSPolicyCfg configuration)
    {
      this.allowedOperations = configuration.getAllowedOperations();
      this.allowedAttributes = configuration.getAllowedAttributes();
      this.prohibitedAttributes =
          configuration.getProhibitedAttributes();
      this.allowedSearchScopes = configuration.getAllowedSearchScopes();
      this.allowedSubtrees = configuration.getAllowedSubtrees();
      this.prohibitedSubtrees = configuration.getProhibitedSubtrees();
    }
  }



  // Validates a configuration.
  private static boolean validateConfiguration(
      RequestFilteringQOSPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    // Check that allowed-attributes does not contain any attribute
    // also configured in prohibited-attributes
    for (String allowedAttr : configuration.getAllowedAttributes())
    {
      if (configuration.getProhibitedAttributes().contains(allowedAttr))
      {
        unacceptableReasons
            .add(ERR_CONFIG_NETWORKGROUPREQUESTFILTERINGPOLICY_INVALID_ATTRIBUTE
                .get(allowedAttr, configuration.dn().toString()));
        return false;
      }
    }

    // Check that allowed-subtrees does not contain any subtree also
    // configured in prohibited-subtrees
    for (DN allowedSubtree : configuration.getAllowedSubtrees())
    {
      if (configuration.getProhibitedSubtrees()
          .contains(allowedSubtree))
      {
        unacceptableReasons
            .add(ERR_CONFIG_NETWORKGROUPREQUESTFILTERINGPOLICY_INVALID_SUBTREE
                .get(allowedSubtree.toString(), configuration.dn()
                    .toString()));
        return false;
      }
    }

    return true;
  }



  /**
   * Creates a new request filtering policy factory.
   */
  public RequestFilteringPolicyFactory()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  public RequestFilteringPolicy createQOSPolicy(
      RequestFilteringQOSPolicyCfg configuration)
      throws ConfigException, InitializationException
  {
    Policy policy = new Policy();

    // Save the configuration.
    policy.updateConfiguration(configuration);

    // Register change listener.
    configuration.addRequestFilteringChangeListener(policy);

    return policy;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAcceptable(
      RequestFilteringQOSPolicyCfg configuration,
      List<Message> unacceptableReasons)
  {
    return validateConfiguration(configuration, unacceptableReasons);
  }
}
