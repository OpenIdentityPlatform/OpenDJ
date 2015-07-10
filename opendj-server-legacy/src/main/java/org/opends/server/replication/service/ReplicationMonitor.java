/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.service.ReplicationDomain.ImportExportContext;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;

/**
 * Class used to generate monitoring information for the replication.
 */
public class ReplicationMonitor extends MonitorProvider<MonitorProviderCfg>
{
  private final ReplicationDomain domain;

  /**
   * Create a new replication monitor.
   * @param domain the plugin which created the monitor
   */
  ReplicationMonitor(ReplicationDomain domain)
  {
    this.domain = domain;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  {
    // no implementation needed.
  }

  /**
   * Retrieves the name of this monitor provider.  It should be unique among all
   * monitor providers, including all instances of the same monitor provider.
   *
   * @return  The name of this monitor provider.
   */
  @Override
  public String getMonitorInstanceName()
  {
    return "Directory server DS(" + domain.getServerId() + ") "
        + domain.getLocalUrl()
        + ",cn=" + domain.getBaseDN().toString().replace(',', '_').replace('=', '_')
        + ",cn=Replication";
  }

  /**
   * Retrieves a set of attributes containing monitor data that should be
   * returned to the client if the corresponding monitor entry is requested.
   *
   * @return  A set of attributes containing monitor data that should be
   *          returned to the client if the corresponding monitor entry is
   *          requested.
   */
  @Override
  public List<Attribute> getMonitorData()
  {
    List<Attribute> attributes = new ArrayList<>();

    attributes.add(Attributes.create("domain-name", String.valueOf(domain.getBaseDN())));
    attributes.add(Attributes.create("connected-to", domain.getReplicationServer()));
    addMonitorData(attributes, "lost-connections", domain.getNumLostConnections());
    addMonitorData(attributes, "received-updates", domain.getNumRcvdUpdates());
    addMonitorData(attributes, "sent-updates", domain.getNumSentUpdates());

    // get number of changes replayed
    addMonitorData(attributes, "replayed-updates", domain.getNumProcessedUpdates());

    addMonitorData(attributes, "server-id", domain.getServerId());

    // get window information
    addMonitorData(attributes, "max-rcv-window", domain.getMaxRcvWindow());
    addMonitorData(attributes, "current-rcv-window", domain.getCurrentRcvWindow());
    addMonitorData(attributes, "max-send-window", domain.getMaxSendWindow());
    addMonitorData(attributes, "current-send-window", domain.getCurrentSendWindow());

    // get the Server State
    final String ATTR_SERVER_STATE = "server-state";
    AttributeType type = DirectoryServer.getDefaultAttributeType(ATTR_SERVER_STATE);
    AttributeBuilder builder = new AttributeBuilder(type, ATTR_SERVER_STATE);
    builder.addAllStrings(domain.getServerState().toStringSet());
    attributes.add(builder.toAttribute());

    attributes.add(Attributes.create("ssl-encryption", String.valueOf(domain.isSessionEncrypted())));
    attributes.add(Attributes.create("generation-id", String.valueOf(domain.getGenerationID())));

    // Add import/export monitoring attributes
    final ImportExportContext ieContext = domain.getImportExportContext();
    if (ieContext != null)
    {
      addMonitorData(attributes, "total-update", ieContext.importInProgress() ? "import" : "export");
      addMonitorData(attributes, "total-update-entry-count", ieContext.getTotalEntryCount());
      addMonitorData(attributes, "total-update-entry-left", ieContext.getLeftEntryCount());
    }


    // Add the concrete Domain attributes
    attributes.addAll(domain.getAdditionalMonitoring());

    /*
     * Add assured replication related monitoring fields
     * (see domain.getXXX() method comment for field meaning)
     */
    addMonitorData(attributes, "assured-sr-sent-updates", domain.getAssuredSrSentUpdates());
    addMonitorData(attributes, "assured-sr-acknowledged-updates", domain.getAssuredSrAcknowledgedUpdates());
    addMonitorData(attributes, "assured-sr-not-acknowledged-updates", domain.getAssuredSrNotAcknowledgedUpdates());
    addMonitorData(attributes, "assured-sr-timeout-updates", domain.getAssuredSrTimeoutUpdates());
    addMonitorData(attributes, "assured-sr-wrong-status-updates", domain.getAssuredSrWrongStatusUpdates());
    addMonitorData(attributes, "assured-sr-replay-error-updates", domain.getAssuredSrReplayErrorUpdates());
    addMonitorData(attributes, "assured-sr-server-not-acknowledged-updates", domain
        .getAssuredSrServerNotAcknowledgedUpdates());
    addMonitorData(attributes, "assured-sr-received-updates", domain.getAssuredSrReceivedUpdates());
    addMonitorData(attributes, "assured-sr-received-updates-acked", domain.getAssuredSrReceivedUpdatesAcked());
    addMonitorData(attributes, "assured-sr-received-updates-not-acked", domain.getAssuredSrReceivedUpdatesNotAcked());
    addMonitorData(attributes, "assured-sd-sent-updates", domain.getAssuredSdSentUpdates());
    addMonitorData(attributes, "assured-sd-acknowledged-updates", domain.getAssuredSdAcknowledgedUpdates());
    addMonitorData(attributes, "assured-sd-timeout-updates", domain.getAssuredSdTimeoutUpdates());
    addMonitorData(attributes, "assured-sd-server-timeout-updates", domain.getAssuredSdServerTimeoutUpdates());

    // Status related monitoring fields
    addMonitorData(attributes, "last-status-change-date", domain.getLastStatusChangeDate().toString());

    addMonitorData(attributes, "status", domain.getStatus().toString());

    return attributes;
  }

  private void addMonitorData(List<Attribute> attributes, String attrType,
      Map<Integer, Integer> serverIdToNb)
  {
    if (serverIdToNb.size() > 0)
    {
      AttributeType type = DirectoryServer.getDefaultAttributeType(attrType);
      final AttributeBuilder builder = new AttributeBuilder(type, attrType);
      for (Entry<Integer, Integer> entry : serverIdToNb.entrySet())
      {
        final Integer serverId = entry.getKey();
        final Integer nb = entry.getValue();
        builder.add(serverId + ":" + nb);
      }
      attributes.add(builder.toAttribute());
    }
  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The integer value of he attribute to add.
   */
  public static void addMonitorData(List<Attribute> attributes, String name, int value)
  {
    addMonitorData(attributes, name, String.valueOf(value));
  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The integer value of he attribute to add.
   */
  private static void addMonitorData(List<Attribute> attributes, String name, long value)
  {
    addMonitorData(attributes, name, String.valueOf(value));
  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The String value of he attribute to add.
   */
  private static void addMonitorData(List<Attribute> attributes, String name, String value)
  {
    AttributeType type = DirectoryServer.getDefaultAttributeType(name);
    attributes.add(Attributes.create(type, value));
  }
}
