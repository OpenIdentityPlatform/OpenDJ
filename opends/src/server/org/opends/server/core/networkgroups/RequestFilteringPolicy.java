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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.
        NetworkGroupRequestFilteringPolicyCfg;
import org.opends.server.admin.std.meta.
        NetworkGroupRequestFilteringPolicyCfgDefn.AllowedOperations;
import org.opends.server.admin.std.meta.
        NetworkGroupRequestFilteringPolicyCfgDefn.AllowedSearchScopes;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
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
import static org.opends.messages.CoreMessages.*;


/**
 * This class defines the request filtering policy applicable to all
 * connections inside the same network group.
 */
public class RequestFilteringPolicy
implements ConfigurationAddListener<NetworkGroupRequestFilteringPolicyCfg>,
           ConfigurationDeleteListener<NetworkGroupRequestFilteringPolicyCfg>,
           ConfigurationChangeListener<NetworkGroupRequestFilteringPolicyCfg>
{
  // The request filtering policy is defined through the config
  private boolean isConfigured = false;

  // The list of allowed operations
  Set<AllowedOperations> allowedOperations = null;

  // The list of allowed attributes
  Set<String> allowedAttributes = null;

  // The list of prohibited attributes
  Set<String> prohibitedAttributes = null;

  // The list of allowed search scopes
  Set<AllowedSearchScopes> allowedSearchScopes = null;

  // The list of allowed subtrees
  Set<DN> allowedSubtrees = null;

  // The list of prohibited subtrees
  Set<DN> prohibitedSubtrees = null;


  /**
   * Constructor.
   *
   * @param policyCfg configuration
   */
  public RequestFilteringPolicy(
          NetworkGroupRequestFilteringPolicyCfg policyCfg)
  {
    createPolicy(policyCfg);
  }

  /**
   * Resets all the fields.
   */
  private void resetPolicy() {
    allowedOperations = Collections.emptySet();
    allowedAttributes = Collections.emptySet();
    prohibitedAttributes = Collections.emptySet();
    allowedSearchScopes = Collections.emptySet();
    allowedSubtrees = Collections.emptySet();
    prohibitedSubtrees = Collections.emptySet();

    isConfigured = false;
  }

  /**
   * Creates a RequestFilteringPolicy from a configuration object.
   *
   * @param policyCfg the configuration
   */
  private void createPolicy(
          NetworkGroupRequestFilteringPolicyCfg policyCfg)
  {
    if (policyCfg != null) {
      allowedOperations = policyCfg.getAllowedOperations();
      allowedAttributes = policyCfg.getAllowedAttributes();
      prohibitedAttributes = policyCfg.getProhibitedAttributes();
      allowedSearchScopes = policyCfg.getAllowedSearchScopes();
      allowedSubtrees = policyCfg.getAllowedSubtrees();
      prohibitedSubtrees = policyCfg.getProhibitedSubtrees();

      policyCfg.addChangeListener(this);

      isConfigured = true;
    } else {
      resetPolicy();
    }
  }

  /**
   * Configures the set of allowed operations.
   * @param allowedOps The set of allowed operations
   */
  public void setAllowedOperations(Set<AllowedOperations> allowedOps) {
    if (allowedOps == null) {
      allowedOperations = Collections.emptySet();
    } else {
      allowedOperations = allowedOps;
    }
  }

  /**
   * Configures the set of allowed attributes in search and compare operations.
   * @param allowedAttrs The set of allowed attributes
   */
  public void setAllowedAttributes(Set<String> allowedAttrs) {
    if (allowedAttrs == null) {
      allowedAttributes = Collections.emptySet();
    } else {
      allowedAttributes = allowedAttrs;
    }
  }

  /**
   * Configures the set of prohibited attributes in search and compare
   * operations.
   * @param prohibitedAttrs The set of prohibited attributes
   */
  public void setProhibitedAttributes(Set<String> prohibitedAttrs) {
    if (prohibitedAttrs == null) {
      prohibitedAttributes = Collections.emptySet();
    } else {
      prohibitedAttributes = prohibitedAttrs;
    }
  }

  /**
   * Configures the set of allowed search scopes.
   * @param allowedScopes The set of scopes
   */
  public void setAllowedSearchScopes(Set<AllowedSearchScopes> allowedScopes) {
    if (allowedScopes == null) {
      allowedSearchScopes = Collections.emptySet();
    } else {
      allowedSearchScopes = allowedScopes;
    }
  }

  /**
   * Configures the set of subtrees allowed in search operations.
   * @param allowedSubt The set of allowed subtrees
   */
  public void setAllowedSubtrees(Set<DN> allowedSubt) {
    if (allowedSubt == null) {
      allowedSubtrees = Collections.emptySet();
    } else {
      allowedSubtrees = allowedSubt;
    }
  }

  /**
   * Configures the set of subtrees prohibited in search operations.
   * @param prohibitedSubt The set of prohibited subtrees
   */
  public void setProhibitedSubtrees(Set<DN> prohibitedSubt) {
    if (prohibitedSubt == null) {
      prohibitedSubtrees = Collections.emptySet();
    } else {
      prohibitedSubtrees = prohibitedSubt;
    }
  }


  /**
   * Checks the request filtering policy.
   *
   * @param operation the ongoing operation
   * @param messages the messages to include in the disconnect notification
   *                response.  It may be <CODE>null</CODE> if no message
   *                is to be sent.
   * @return a boolean indicating whether the operation is allowed
   */
  public boolean checkPolicy(
          PreParseOperation operation,
          List<Message> messages)
  {
    boolean result = true;

    // Check the allowed operations
    if (!allowedOperations.isEmpty()) {
      switch (operation.getOperationType()) {
        case ABANDON:
          result= true;
          break;
        case ADD:
          result = allowedOperations.contains(AllowedOperations.ADD);
          break;
        case BIND:
          result = allowedOperations.contains(AllowedOperations.BIND);
          break;
        case COMPARE:
          result = allowedOperations.contains(AllowedOperations.COMPARE);
          break;
        case DELETE:
          result = allowedOperations.contains(AllowedOperations.DELETE);
          break;
        case EXTENDED:
          result = allowedOperations.contains(AllowedOperations.EXTENDED);
          break;
        case MODIFY:
          result = allowedOperations.contains(AllowedOperations.MODIFY);
          break;
        case MODIFY_DN:
          result = allowedOperations.contains(AllowedOperations.RENAME);
          break;
        case SEARCH:
          result = allowedOperations.contains(AllowedOperations.SEARCH);

          // If inequality search are prohibited, need to check
          if (result && !allowedOperations.contains(
                  AllowedOperations.INEQUALITY_SEARCH)) {
              RawFilter flt =
                      ((PreParseSearchOperation) operation).getRawFilter();
              result = (!containsInequalitySearch(flt));
          }
          break;
        case UNBIND:
          result = true;
          break;
      }

      if (!result) {
        messages.add(INFO_ERROR_OPERATION_NOT_ALLOWED.get());
        return result;
      }
    }

    // For search operations:
    if (operation.getOperationType().equals(OperationType.SEARCH)) {
      PreParseSearchOperation searchOp = (PreParseSearchOperation) operation;

      // Check the allowed/prohibited attributes in search filter
      if (!prohibitedAttributes.isEmpty()) {
        // The attributes specified in prohibitedAttributes are not OK
        result = (!containsProhibitedAttribute(searchOp.getRawFilter()));
      }
      if (!allowedAttributes.isEmpty()) {
        // Only the attributes specified in allowedAttributes are OK
        result = (containsOnlyAllowedAttributes(searchOp.getRawFilter()));
      }
      if (!result) {
        messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
        return result;
      }

      // Check the search scope
      if (!allowedSearchScopes.isEmpty()) {
        switch (searchOp.getScope()) {
          case BASE_OBJECT:
            result = allowedSearchScopes.contains(AllowedSearchScopes.BASE);
            break;
          case SINGLE_LEVEL:
            result = allowedSearchScopes.contains(AllowedSearchScopes.ONE);
            break;
          case WHOLE_SUBTREE:
            result = allowedSearchScopes.contains(AllowedSearchScopes.SUB);
            break;
          case SUBORDINATE_SUBTREE:
            result = allowedSearchScopes.contains(AllowedSearchScopes.CHILDREN);
            break;
        }

        if (!result) {
          messages.add(INFO_ERROR_SEARCH_SCOPE_NOT_ALLOWED.get());
          return result;
        }
      }
    }

    // For compare operation
    if (operation.getOperationType().equals(OperationType.COMPARE)) {
      PreParseCompareOperation compareOp = (PreParseCompareOperation) operation;

      // Check the allowed/prohibited attributes
      if (!prohibitedAttributes.isEmpty()) {
        result = (!prohibitedAttributes.contains(
                compareOp.getRawAttributeType()));
      }
      if (!allowedAttributes.isEmpty()) {
        result = (allowedAttributes.contains(compareOp.getRawAttributeType()));
      }
      if (!result) {
        messages.add(INFO_ERROR_ATTRIBUTE_NOT_ALLOWED.get());
        return result;
      }
    }

    DN entryDN = null;
    DN newEntryDN = null;
    try {
      switch (operation.getOperationType()) {
        case ADD:
          entryDN = DN.decode(
                  ((PreParseAddOperation) operation).getRawEntryDN());
          break;
        case COMPARE:
          entryDN = DN.decode(
                  ((PreParseCompareOperation) operation).getRawEntryDN());
          break;
        case DELETE:
          entryDN = DN.decode(
                  ((PreParseDeleteOperation) operation).getRawEntryDN());
          break;
        case EXTENDED:
          break;
        case MODIFY:
          entryDN = DN.decode(
                  ((PreParseModifyOperation) operation).getRawEntryDN());
          break;
        case MODIFY_DN:
          entryDN = DN.decode(
                  ((PreParseModifyDNOperation) operation).getRawEntryDN());
          newEntryDN = DN.decode(
                  ((PreParseModifyDNOperation) operation).getRawNewRDN());
          break;
        case SEARCH:
          entryDN = DN.decode(
                  ((PreParseSearchOperation) operation).getRawBaseDN());
          break;
        default:
          break;
      }
      if (entryDN != null) {
        result = ((isInAllowedSubtrees(entryDN))
                    && !(isInProhibitedSubtrees(entryDN)));
      }
      if (newEntryDN != null) {
        result = ((isInAllowedSubtrees(newEntryDN))
                    && !(isInProhibitedSubtrees(newEntryDN)));
      }

    } catch (DirectoryException ex) {
      Logger.getLogger(RequestFilteringPolicy.class.getName())
                  .log(Level.SEVERE, null, ex);
    }
    if (!result) {
      messages.add(INFO_ERROR_SUBTREE_NOT_ALLOWED.get());
      return result;
    }

    return (true);
  }


  /**
   * Checks whether a filter contains an inequality search filter
   * (i.e. either a greater_or_equal or a less_or_equal filter).
   * @param filter The filter to be tested
   * @return boolean indicating whether the filter contains an inequality
   *         search filter
   */
  private boolean containsInequalitySearch(RawFilter filter) {
    boolean result = false;
      switch (filter.getFilterType()) {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents = filter.getFilterComponents();
        if (filterComponents != null) {
          for (RawFilter element : filterComponents) {
            if (containsInequalitySearch(element)) {
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
   * Checks whether a filter contains one of the prohibited attributes.
   * @param filter The filter to be tested
   * @return boolean indicating whether the filter contains at least one of
   *         the prohibited attributes
   */
  private boolean containsProhibitedAttribute(
          RawFilter filter) {
    boolean result = false;
    switch (filter.getFilterType()) {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents = filter.getFilterComponents();
        if (filterComponents != null) {
          for (RawFilter element : filterComponents) {
            if (containsProhibitedAttribute(element)) {
              return true;
            }
          }
        }
        return false;
      case NOT:
        return (containsProhibitedAttribute(filter.getNOTComponent()));
      default:
        return (prohibitedAttributes.contains(filter.getAttributeType()));
    }
  }

  /**
   * Checks whether a filter contains unallowed attributes.
   * @param filter The filter to be tested
   * @return boolean indicating whether the filter contains at least one
   *         attribute which is not in the allowed list
   */
  private boolean containsOnlyAllowedAttributes(
          RawFilter filter) {
    switch (filter.getFilterType()) {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents = filter.getFilterComponents();
        if (filterComponents != null) {
          for (RawFilter element : filterComponents) {
            if (!containsOnlyAllowedAttributes(element)) {
              return false;
            }
          }
        }
        return true;
      case NOT:
        return (containsOnlyAllowedAttributes(filter.getNOTComponent()));
      default:
        return (allowedAttributes.contains(filter.getAttributeType()));
    }
  }

  /**
   * Checks whether a DN is in a branch of the allowed subtrees.
   * @param dn The DN to be tested
   * @return boolean indicating whether the dn is in a branch of the allowed
   *         subtrees
   */
  private boolean isInAllowedSubtrees(DN dn) {
    boolean result = false;
    // If the variable is not set, consider allowedSubtrees = rootDSE
    if (allowedSubtrees.isEmpty()) {
      return true;
    }
    for (DN branch:allowedSubtrees) {
      if (dn.isDescendantOf(branch)) {
        result = true;
        break;
      }
    }
    return result;
  }

  /**
   * Checks whether a DN is in a branch of the prohibited subtrees.
   * @param dn The Dn to be tested
   * @return boolean indicating whether the dn is in a branch of the prohibited
   *         subtrees
   */
  private boolean isInProhibitedSubtrees(DN dn) {
    boolean result = false;
    for (DN branch:prohibitedSubtrees) {
      if (dn.isDescendantOf(branch)) {
        result = true;
        break;
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
          NetworkGroupRequestFilteringPolicyCfg configuration,
          List<Message> unacceptableReasons) {
    return (!isConfigured);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
          NetworkGroupRequestFilteringPolicyCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    createPolicy(configuration);
    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
          NetworkGroupRequestFilteringPolicyCfg configuration,
          List<Message> unacceptableReasons) {
    return isConfigured;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
          NetworkGroupRequestFilteringPolicyCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
      new ConfigChangeResult(resultCode, adminActionRequired, messages);

    resetPolicy();

    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          NetworkGroupRequestFilteringPolicyCfg configuration,
          List<Message> unacceptableReasons) {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
          NetworkGroupRequestFilteringPolicyCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    createPolicy(configuration);
    return configChangeResult;
  }
}
