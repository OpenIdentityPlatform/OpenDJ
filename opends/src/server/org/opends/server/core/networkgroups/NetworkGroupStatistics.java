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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.core.networkgroups;

import java.util.ArrayList;
import java.util.List;
import org.opends.messages.Message;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;

import org.opends.server.types.SearchScope;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.messages.ProtocolMessages.*;

/**
 * This class implements the statistics associated to a network group.
 */
public class NetworkGroupStatistics
       extends MonitorProvider<MonitorProviderCfg> {

  // The instance name for this monitor provider instance.
  private final String instanceName;
  private final NetworkGroup networkGroup;

  private final Object lock = new Object();
  private long abandonRequests = 0;
  private long addRequests = 0;
  private long bindRequests = 0;
  private long compareRequests = 0;
  private long deleteRequests = 0;
  private long extendedRequests = 0;
  private long modifyRequests = 0;
  private long modifyDNRequests = 0;
  private long searchOneRequests = 0;
  private long searchSubRequests = 0;
  private long unbindRequests = 0;

  /**
   * Constructor.
   * @param networkGroup The network group owning these stats
   */
  public NetworkGroupStatistics(NetworkGroup networkGroup) {
    super(networkGroup.getID());
    this.instanceName = networkGroup.getID();
    this.networkGroup = networkGroup;
    DirectoryServer.registerMonitorProvider(this);
  }


  /**
   * Finalize the statistics.
   */
  public void finalizeStatistics() {
    DirectoryServer.deregisterMonitorProvider(getMonitorInstanceName());
  }


  /**
   * Increments the number of operations managed by this network group.
   * @param message The LDAP Message containing the operation to be
   * managed by the network group.
   */
  public void updateMessageRead(LDAPMessage message) {
    synchronized (lock)
    {
      switch (message.getProtocolOp().getType())
      {
        case OP_TYPE_ABANDON_REQUEST:
          abandonRequests++;
          break;
        case OP_TYPE_ADD_REQUEST:
          addRequests++;
          break;
        case OP_TYPE_BIND_REQUEST:
          bindRequests++;
          break;
        case OP_TYPE_COMPARE_REQUEST:
          compareRequests++;
          break;
        case OP_TYPE_DELETE_REQUEST:
          deleteRequests++;
          break;
        case OP_TYPE_EXTENDED_REQUEST:
          extendedRequests++;
          break;
        case OP_TYPE_MODIFY_REQUEST:
          modifyRequests++;
          break;
        case OP_TYPE_MODIFY_DN_REQUEST:
          modifyDNRequests++;
          break;
        case OP_TYPE_SEARCH_REQUEST:
          SearchScope scope = message.getSearchRequestProtocolOp().getScope();
          if (scope == SearchScope.BASE_OBJECT
              || scope == SearchScope.SINGLE_LEVEL) {
            searchOneRequests++;
          } else {
            searchSubRequests++;
          }
          break;
        case OP_TYPE_UNBIND_REQUEST:
          unbindRequests++;
          break;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
         throws ConfigException, InitializationException {
    // Throw an exception, because this monitor is not intended to be
    // dynamically loaded from the configuration.  Rather, it should be
    // explicitly created and registered by the LDAP connection handler or an
    // LDAP client connection.
    Message message = ERR_LDAP_STATS_INVALID_MONITOR_INITIALIZATION.get(
        String.valueOf(configuration.dn()));
    throw new ConfigException(message);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName() {
      return this.instanceName+",cn=Network Groups";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getUpdateInterval() {
    // This monitor should not run periodically.
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void updateMonitorData() {
    // No implementation is required since this does not do periodic updates.
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Attribute> getMonitorData() {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>();

    RequestFilteringPolicyStatistics rfpStatistics =
        networkGroup.getRequestFilteringPolicyStatistics();
    if (rfpStatistics != null)
    {
      attrs.add(Attributes.create(
          "ds-mon-rejected-attributes-total-count", String
              .valueOf(rfpStatistics.getRejectedAttributes())));
      attrs.add(Attributes.create(
          "ds-mon-rejected-operations-total-count", String
              .valueOf(rfpStatistics.getRejectedOperations())));
      attrs.add(Attributes.create(
          "ds-mon-rejected-search-scopes-total-count", String
              .valueOf(rfpStatistics.getRejectedScopes())));
      attrs.add(Attributes.create(
          "ds-mon-rejected-subtrees-total-count", String
              .valueOf(rfpStatistics.getRejectedSubtrees())));
    }

    ResourceLimitsPolicyStatistics rlpStatistics =
        networkGroup.getResourceLimitsPolicyStatistics();
    if (rlpStatistics != null)
    {
      attrs.add(Attributes.create("ds-mon-client-connection-count",
          String.valueOf(rlpStatistics.getClientConnections())));
      attrs.add(Attributes.create("ds-mon-client-connection-max-count",
          String.valueOf(rlpStatistics.getMaxClientConnections())));
      attrs.add(Attributes.create(
          "ds-mon-client-connection-total-count", String
              .valueOf(rlpStatistics.getTotalClientConnections())));
    }

    synchronized(lock) {
      attrs.add(Attributes.create("ds-mon-abandon-operations-total-count",
          String.valueOf(abandonRequests)));
      attrs.add(Attributes.create("ds-mon-add-operations-total-count",
          String.valueOf(addRequests)));
      attrs.add(Attributes.create("ds-mon-bind-operations-total-count",
          String.valueOf(bindRequests)));
      attrs.add(Attributes.create("ds-mon-compare-operations-total-count",
          String.valueOf(compareRequests)));
      attrs.add(Attributes.create("ds-mon-delete-operations-total-count",
          String.valueOf(deleteRequests)));
      attrs.add(Attributes.create("ds-mon-extended-operations-total-count",
          String.valueOf(extendedRequests)));
      attrs.add(Attributes.create("ds-mon-mod-operations-total-count",
          String.valueOf(modifyRequests)));
      attrs.add(Attributes.create("ds-mon-moddn-operations-total-count",
          String.valueOf(modifyDNRequests)));
      attrs.add(Attributes.create(
          "ds-mon-searchonelevel-operations-total-count",
          String.valueOf(searchOneRequests)));
      attrs.add(Attributes.create("ds-mon-searchsubtree-operations-total-count",
          String.valueOf(searchSubRequests)));
      attrs.add(Attributes.create("ds-mon-unbind-operations-total-count",
          String.valueOf(unbindRequests)));
    }

    attrs.add(Attributes.create("ds-mon-discarded-referrals-total-count",
        "Not implemented"));
    attrs.add(Attributes.create("ds-mon-forwarded-referrals-total-count",
        "Not implemented"));
    attrs.add(Attributes.create("ds-mon-followed-referrals-total-count",
        "Not implemented"));
    attrs.add(Attributes.create("ds-mon-failed-referrals-total-count",
        "Not implemented"));
    attrs.add(Attributes.create("ds-mon-violations-schema-total-count",
        "Not implemented"));
    attrs.add(Attributes.create("ds-mon-persistent-searchs-count",
        "Not implemented"));

    return attrs;
  }

}
