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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.opends.server.core.networkgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

  private AtomicLong abandonRequests = new AtomicLong(0);
  private AtomicLong addRequests = new AtomicLong(0);
  private AtomicLong bindRequests = new AtomicLong(0);
  private AtomicLong compareRequests = new AtomicLong(0);
  private AtomicLong deleteRequests = new AtomicLong(0);
  private AtomicLong extendedRequests = new AtomicLong(0);
  private AtomicLong modifyRequests = new AtomicLong(0);
  private AtomicLong modifyDNRequests = new AtomicLong(0);
  private AtomicLong searchOneRequests = new AtomicLong(0);
  private AtomicLong searchSubRequests = new AtomicLong(0);
  private AtomicLong unbindRequests = new AtomicLong(0);

  /**
   * Constructor.
   * @param networkGroup The network group owning these stats
   */
  public NetworkGroupStatistics(NetworkGroup networkGroup) {
    this.instanceName = networkGroup.getID();
    this.networkGroup = networkGroup;
    DirectoryServer.registerMonitorProvider(this);
  }


  /**
   * Finalize the statistics.
   */
  public void finalizeStatistics() {
    DirectoryServer.deregisterMonitorProvider(this);
  }


  /**
   * Increments the number of operations managed by this network group.
   * @param message The LDAP Message containing the operation to be
   * managed by the network group.
   */
  public void updateMessageRead(LDAPMessage message) {
    switch (message.getProtocolOp().getType())
    {
      case OP_TYPE_ABANDON_REQUEST:
        abandonRequests.getAndIncrement();
        break;
      case OP_TYPE_ADD_REQUEST:
        addRequests.getAndIncrement();
        break;
      case OP_TYPE_BIND_REQUEST:
        bindRequests.getAndIncrement();
        break;
      case OP_TYPE_COMPARE_REQUEST:
        compareRequests.getAndIncrement();
        break;
      case OP_TYPE_DELETE_REQUEST:
        deleteRequests.getAndIncrement();
        break;
      case OP_TYPE_EXTENDED_REQUEST:
        extendedRequests.getAndIncrement();
        break;
      case OP_TYPE_MODIFY_REQUEST:
        modifyRequests.getAndIncrement();
        break;
      case OP_TYPE_MODIFY_DN_REQUEST:
        modifyDNRequests.getAndIncrement();
        break;
      case OP_TYPE_SEARCH_REQUEST:
        SearchScope scope = message.getSearchRequestProtocolOp().getScope();
        if (scope == SearchScope.BASE_OBJECT
            || scope == SearchScope.SINGLE_LEVEL) {
          searchOneRequests.getAndIncrement();
        } else {
          searchSubRequests.getAndIncrement();
        }
        break;
      case OP_TYPE_UNBIND_REQUEST:
        unbindRequests.getAndIncrement();
        break;
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

    attrs.add(Attributes.create("ds-mon-abandon-operations-total-count",
        String.valueOf(abandonRequests.get())));
    attrs.add(Attributes.create("ds-mon-add-operations-total-count",
        String.valueOf(addRequests.get())));
    attrs.add(Attributes.create("ds-mon-bind-operations-total-count",
        String.valueOf(bindRequests.get())));
    attrs.add(Attributes.create("ds-mon-compare-operations-total-count",
        String.valueOf(compareRequests.get())));
    attrs.add(Attributes.create("ds-mon-delete-operations-total-count",
        String.valueOf(deleteRequests.get())));
    attrs.add(Attributes.create("ds-mon-extended-operations-total-count",
        String.valueOf(extendedRequests.get())));
    attrs.add(Attributes.create("ds-mon-mod-operations-total-count",
        String.valueOf(modifyRequests.get())));
    attrs.add(Attributes.create("ds-mon-moddn-operations-total-count",
        String.valueOf(modifyDNRequests.get())));
    attrs.add(Attributes.create(
        "ds-mon-searchonelevel-operations-total-count",
        String.valueOf(searchOneRequests.get())));
    attrs.add(Attributes.create("ds-mon-searchsubtree-operations-total-count",
        String.valueOf(searchSubRequests.get())));
    attrs.add(Attributes.create("ds-mon-unbind-operations-total-count",
        String.valueOf(unbindRequests.get())));

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
