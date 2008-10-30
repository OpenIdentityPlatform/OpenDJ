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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;


import org.opends.server.core.*;
import java.util.ArrayList;
import java.util.List;
import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import
 org.opends.server.admin.std.meta.NetworkGroupCriteriaCfgDefn.AllowedAuthMethod;
import
 org.opends.server.admin.std.meta.NetworkGroupCriteriaCfgDefn.AllowedLDAPPort;
import org.opends.server.admin.std.server.NetworkGroupCriteriaCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AddressMask;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;


/**
 * This class defines the network group criteria. A criterion is used
 * by the network groups to determine whether a client connection belongs
 * to the network group or not.
 */
public class NetworkGroupCriteria
        implements ConfigurationAddListener<NetworkGroupCriteriaCfg>,
                   ConfigurationDeleteListener<NetworkGroupCriteriaCfg>,
                   ConfigurationChangeListener<NetworkGroupCriteriaCfg>,
                   NetworkGroupCriterion

{
  // Indicates whether the criteria are defined through the config
  private boolean isConfigured = false;

  // The network group criteria.
  private AuthMethodCriteria authMethodCriteria;
  private BindDnCriteria bindDnCriteria;
  private IpFilterCriteria ipFilterCriteria;
  private PortCriteria portCriteria;
  private SecurityCriteria securityCriteria;

  // The current config
  private NetworkGroupCriteriaCfg config;

  /**
   * Constructor.
   *
   * @param criteriaCfg the configuration object used to build the Network
   *        Group Criteria.
   * @throws DirectoryException If the criteria could not be created because
   *                            of an invalid configuration parameter
   */
  public NetworkGroupCriteria(NetworkGroupCriteriaCfg criteriaCfg)
          throws DirectoryException {
    createCriteria(criteriaCfg);
  }

  /**
   * Resets all the fields.
   */
  private void resetCriteria() {
    authMethodCriteria = null;
    bindDnCriteria = null;
    ipFilterCriteria = null;
    portCriteria = null;
    securityCriteria = null;
    isConfigured = false;
    if (config != null) {
      config.removeChangeListener(this);
      config = null;
    }
  }

  /**
   * Creates a new NetworkGroupCriteria based on the configuration object.
   *
   * @param criteriaCfg the configuration
   * @throws DirectoryException If the bind-dn-filter is not a valid DN filter
   */
  private void createCriteria(NetworkGroupCriteriaCfg criteriaCfg)
          throws DirectoryException {
    if (criteriaCfg != null) {
      if (!criteriaCfg.getAllowedAuthMethod().isEmpty()) {
        authMethodCriteria = new AuthMethodCriteria();
        for (AllowedAuthMethod method: criteriaCfg.getAllowedAuthMethod()) {
          authMethodCriteria.addAuthMethod(method);
        }
      } else {
        authMethodCriteria = null;
      }
      if (!criteriaCfg.getBindDNFilter().isEmpty()) {
        bindDnCriteria = new BindDnCriteria();
        for (String filter: criteriaCfg.getBindDNFilter()) {
          bindDnCriteria.addBindDnFilter(filter);
        }
      } else {
        bindDnCriteria = null;
      }
      if (!criteriaCfg.getIPAddressFilter().isEmpty()) {
        ipFilterCriteria = new IpFilterCriteria();
        for (AddressMask filter: criteriaCfg.getIPAddressFilter()) {
          ipFilterCriteria.addIpFilter(filter);
        }
      } else {
        ipFilterCriteria = null;
      }
      if (!criteriaCfg.getAllowedLDAPPort().isEmpty()) {
        portCriteria = new PortCriteria();
        for (AllowedLDAPPort port : criteriaCfg.getAllowedLDAPPort()) {
          portCriteria.addPort(port);
        }
      } else {
        portCriteria = null;
      }
      if (criteriaCfg.isIsSecurityMandatory()) {
        securityCriteria = new SecurityCriteria(true);
      } else {
        securityCriteria = null;
      }
      isConfigured = true;
      if (config == null) {
        criteriaCfg.addChangeListener(this);
      }
      config = criteriaCfg;
    } else {
      resetCriteria();
    }
  }

  /**
   * Sets the authentication method criteria.
   * @param criteria The authentication method criteria
   */
  public void setAuthMethodCriteria(AuthMethodCriteria criteria) {
    authMethodCriteria = criteria;
    isConfigured = true;
  }

  /**
   * Sets the bind dn criteria.
   * @param criteria The bind DN criteria
   */
  public void setBindDnCriteria(BindDnCriteria criteria) {
    bindDnCriteria = criteria;
    isConfigured = true;
  }

  /**
   * Sets the IP filter criteria.
   * @param criteria The IP filter criteria
   */
  public void setIpFilterCriteria(IpFilterCriteria criteria) {
    ipFilterCriteria = criteria;
    isConfigured = true;
  }

  /**
   * Sets the port criteria.
   * @param criteria The IP filter criteria
   */
  public void setPortCriteria(PortCriteria criteria) {
    portCriteria = criteria;
    isConfigured = true;
  }

  /**
   * Sets the IP filter criteria.
   * @param criteria The IP filter criteria
   */
  public void setSecurityCriteria(SecurityCriteria criteria) {
    securityCriteria = criteria;
    isConfigured = true;
  }

  /**
   * {@inheritDoc}
   */
  public boolean match(ClientConnection connection) {
    if ((authMethodCriteria != null)
    && (!authMethodCriteria.match(connection))) {
      return (false);
    }
    if ((bindDnCriteria != null) && (!bindDnCriteria.match(connection))) {
      return (false);
    }
    if ((ipFilterCriteria != null) && (!ipFilterCriteria.match(connection))) {
      return (false);
    }
    if ((portCriteria != null) && (!portCriteria.match(connection))) {
      return (false);
    }
    if ((securityCriteria != null) && (!securityCriteria.match(connection))) {
      return (false);
    }
    return (true);
  }

  /**
   * {@inheritDoc}
   */
  public boolean matchAfterBind(ClientConnection connection,
          DN bindDN,
          AuthenticationType authType,
          boolean isSecure) {
    if ((authMethodCriteria != null) && (!authMethodCriteria.matchAfterBind(
            connection, bindDN, authType, isSecure))) {
      return (false);
    }
    if ((bindDnCriteria != null) && (!bindDnCriteria.matchAfterBind(
            connection, bindDN, authType, isSecure))) {
      return (false);
    }
    if ((ipFilterCriteria != null) && (!ipFilterCriteria.matchAfterBind(
            connection, bindDN, authType, isSecure))) {
      return (false);
    }
    if ((portCriteria != null) && (!portCriteria.matchAfterBind(
            connection, bindDN, authType, isSecure))) {
      return (false);
    }
    if ((securityCriteria != null) && (!securityCriteria.matchAfterBind(
            connection, bindDN, authType, isSecure))) {
      return (false);
    }
    return (true);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
          NetworkGroupCriteriaCfg configuration,
          List<Message> unacceptableReasons) {
    return (!isConfigured);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
          NetworkGroupCriteriaCfg cfg) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    try {
      createCriteria(cfg);
    } catch (DirectoryException de) {
      if (resultCode == ResultCode.SUCCESS) {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      messages.add(de.getMessageObject());
    }
    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
          NetworkGroupCriteriaCfg configuration,
          List<Message> unacceptableReasons) {
    return (isConfigured);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
          NetworkGroupCriteriaCfg configuration) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
      new ConfigChangeResult(resultCode, adminActionRequired, messages);

    resetCriteria();

    return configChangeResult;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
          NetworkGroupCriteriaCfg configuration,
          List<Message> unacceptableReasons) {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
          NetworkGroupCriteriaCfg cfg) {
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ConfigChangeResult configChangeResult =
          new ConfigChangeResult(resultCode, adminActionRequired, messages);
    try {
      createCriteria(cfg);
    } catch (DirectoryException de) {
      if (resultCode == ResultCode.SUCCESS) {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      messages.add(de.getMessageObject());
    }
    return configChangeResult;
  }
}
