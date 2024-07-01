/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.replication.service.ReplicationDomain.ImportExportContext;

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

  @Override
  public MonitorData getMonitorData()
  {
    final MonitorData attributes = new MonitorData(41);

    attributes.add("domain-name", domain.getBaseDN());
    attributes.add("server-id", domain.getServerId());
    attributes.add("connected-to", domain.getReplicationServer());
    attributes.add("lost-connections", domain.getNumLostConnections());

    attributes.add("received-updates", domain.getNumRcvdUpdates());
    attributes.add("sent-updates", domain.getNumSentUpdates());
    attributes.add("replayed-updates", domain.getNumProcessedUpdates());

    // get window information
    attributes.add("max-rcv-window", domain.getMaxRcvWindow());
    attributes.add("current-rcv-window", domain.getCurrentRcvWindow());
    attributes.add("max-send-window", domain.getMaxSendWindow());
    attributes.add("current-send-window", domain.getCurrentSendWindow());

    attributes.add("server-state", domain.getServerState().toStringSet());
    attributes.add("ssl-encryption", domain.isSessionEncrypted());
    attributes.add("generation-id", domain.getGenerationID());

    // Add import/export monitoring attributes
    final ImportExportContext ieContext = domain.getImportExportContext();
    if (ieContext != null)
    {
      attributes.add("total-update", ieContext.importInProgress() ? "import" : "export");
      attributes.add("total-update-entry-count", ieContext.getTotalEntryCount());
      attributes.add("total-update-entry-left", ieContext.getLeftEntryCount());
    }


    // Add the concrete Domain attributes
    domain.addAdditionalMonitoring(attributes);

    /*
     * Add assured replication related monitoring fields
     * (see domain.getXXX() method comment for field meaning)
     */
    attributes.add("assured-sr-sent-updates", domain.getAssuredSrSentUpdates());
    attributes.add("assured-sr-acknowledged-updates", domain.getAssuredSrAcknowledgedUpdates());
    attributes.add("assured-sr-not-acknowledged-updates", domain.getAssuredSrNotAcknowledgedUpdates());
    attributes.add("assured-sr-timeout-updates", domain.getAssuredSrTimeoutUpdates());
    attributes.add("assured-sr-wrong-status-updates", domain.getAssuredSrWrongStatusUpdates());
    attributes.add("assured-sr-replay-error-updates", domain.getAssuredSrReplayErrorUpdates());
    addMonitorData(attributes,
        "assured-sr-server-not-acknowledged-updates",
        domain.getAssuredSrServerNotAcknowledgedUpdates());
    attributes.add("assured-sr-received-updates", domain.getAssuredSrReceivedUpdates());
    attributes.add("assured-sr-received-updates-acked", domain.getAssuredSrReceivedUpdatesAcked());
    attributes.add("assured-sr-received-updates-not-acked", domain.getAssuredSrReceivedUpdatesNotAcked());
    attributes.add("assured-sd-sent-updates", domain.getAssuredSdSentUpdates());
    attributes.add("assured-sd-acknowledged-updates", domain.getAssuredSdAcknowledgedUpdates());
    attributes.add("assured-sd-timeout-updates", domain.getAssuredSdTimeoutUpdates());
    addMonitorData(attributes, "assured-sd-server-timeout-updates", domain.getAssuredSdServerTimeoutUpdates());

    // Status related monitoring fields
    attributes.add("last-status-change-date", domain.getLastStatusChangeDate());
    attributes.add("status", domain.getStatus());

    return attributes;
  }

  private void addMonitorData(MonitorData attributes, String attrName, Map<Integer, Integer> serverIdToNb)
  {
    if (!serverIdToNb.isEmpty())
    {
      Collection<String> values = new ArrayList<>();
      for (Entry<Integer, Integer> entry : serverIdToNb.entrySet())
      {
        final Integer serverId = entry.getKey();
        final Integer nb = entry.getValue();
        values.add(serverId + ":" + nb);
      }
      attributes.add(attrName, values);
    }
  }
}
