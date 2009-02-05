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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.service;

import java.util.Collection;

import java.util.ArrayList;

import java.util.Map;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.Attributes;

/**
 * Class used to generate monitoring information for the replication.
 */
public class ReplicationMonitor extends MonitorProvider<MonitorProviderCfg>
{
  private ReplicationDomain domain;  // the replication plugin

  /**
   * Create a new replication monitor.
   * @param domain the plugin which created the monitor
   */
  public ReplicationMonitor(ReplicationDomain domain)
  {
    super("Replication monitor " + domain.getServiceID()
        + " " + domain.getServerId());
    this.domain = domain;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
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
    return "Replication Domain "  + domain.getServiceID()
       + " " + domain.getServerId();
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
  public ArrayList<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();

    /* get the base dn */
    Attribute attr = Attributes.create("base-dn", domain.getServiceID());
    attributes.add(attr);

    /* get the base dn */
    attr = Attributes.create("connected-to", domain
        .getReplicationServer());
    attributes.add(attr);

    /* get number of lost connections */
    addMonitorData(attributes, "lost-connections",
                   domain.getNumLostConnections());

    /* get number of received updates */
    addMonitorData(attributes, "received-updates", domain.getNumRcvdUpdates());

    /* get number of updates sent */
    addMonitorData(attributes, "sent-updates", domain.getNumSentUpdates());

    /* get number of changes replayed */
    addMonitorData(attributes, "replayed-updates",
                   domain.getNumProcessedUpdates());

    /* get server-id */
    addMonitorData(attributes, "server-id",
                   domain.getServerId());

    /* get window information */
    addMonitorData(attributes, "max-rcv-window", domain.getMaxRcvWindow());
    addMonitorData(attributes, "current-rcv-window",
                               domain.getCurrentRcvWindow());
    addMonitorData(attributes, "max-send-window",
                               domain.getMaxSendWindow());
    addMonitorData(attributes, "current-send-window",
                               domain.getCurrentSendWindow());

    /* get the Server State */
    final String ATTR_SERVER_STATE = "server-state";
    AttributeType type =
      DirectoryServer.getDefaultAttributeType(ATTR_SERVER_STATE);
    AttributeBuilder builder = new AttributeBuilder(type, ATTR_SERVER_STATE);
    for (String str : domain.getServerState().toStringSet())
    {
      builder.add(AttributeValues.create(type,str));
    }
    attributes.add(builder.toAttribute());

    attributes.add(Attributes.create("ssl-encryption",
        String.valueOf(domain.isSessionEncrypted())));

    attributes.add(Attributes.create("generation-id",
        String.valueOf(domain.getGenerationID())));

    /*
     * Add import/export monitoring attribute
     */
    if (domain.importInProgress())
    {
      addMonitorData(attributes, "total-update", "import");
      addMonitorData(
          attributes, "total-update-entry-count", domain.getTotalEntryCount());
      addMonitorData(
          attributes, "total-update-entry-left", domain.getLeftEntryCount());
    }
    if (domain.exportInProgress())
    {
      addMonitorData(attributes, "total-update", "export");
      addMonitorData(
          attributes, "total-update-entry-count", domain.getTotalEntryCount());
      addMonitorData(
          attributes, "total-update-entry-left", domain.getLeftEntryCount());
    }


    /* Add the concrete Domain attributes */
    Collection<Attribute> additionalMonitoring =
      domain.getAdditionalMonitoring();
    attributes.addAll(additionalMonitoring);

    /*
     * Add assured replication related monitoring fields
     * (see domain.getXXX() method comment for field meaning)
     */

    addMonitorData(attributes, "assured-sr-sent-updates",
      domain.getAssuredSrSentUpdates());

    addMonitorData(attributes, "assured-sr-acknowledged-updates",
      domain.getAssuredSrAcknowledgedUpdates());

    addMonitorData(attributes, "assured-sr-not-acknowledged-updates",
      domain.getAssuredSrNotAcknowledgedUpdates());

    addMonitorData(attributes, "assured-sr-timeout-updates",
      domain.getAssuredSrTimeoutUpdates());

    addMonitorData(attributes, "assured-sr-wrong-status-updates",
      domain.getAssuredSrWrongStatusUpdates());

    addMonitorData(attributes, "assured-sr-replay-error-updates",
      domain.getAssuredSrReplayErrorUpdates());

    final String ATTR_ASS_SR_SRV = "assured-sr-server-not-acknowledged-updates";
    type = DirectoryServer.getDefaultAttributeType(ATTR_ASS_SR_SRV);
    builder = new AttributeBuilder(type, ATTR_ASS_SR_SRV);
    Map<Short, Integer> srSrvNotAckUps =
      domain.getAssuredSrServerNotAcknowledgedUpdates();
    if (srSrvNotAckUps.size() > 0)
    {
      for (Short serverId : srSrvNotAckUps.keySet())
      {
        String str = serverId + ":" + srSrvNotAckUps.get(serverId);
        builder.add(AttributeValues.create(type, str));
      }
      attributes.add(builder.toAttribute());
    }

    addMonitorData(attributes, "assured-sr-received-updates",
      domain.getAssuredSrReceivedUpdates());

    addMonitorData(attributes, "assured-sr-received-updates-acked",
      domain.getAssuredSrReceivedUpdatesAcked());

    addMonitorData(attributes, "assured-sr-received-updates-not-acked",
      domain.getAssuredSrReceivedUpdatesNotAcked());

    addMonitorData(attributes, "assured-sd-sent-updates",
      domain.getAssuredSdSentUpdates());

    addMonitorData(attributes, "assured-sd-acknowledged-updates",
      domain.getAssuredSdAcknowledgedUpdates());

    addMonitorData(attributes, "assured-sd-timeout-updates",
      domain.getAssuredSdTimeoutUpdates());

    final String ATTR_ASS_SD_SRV = "assured-sd-server-timeout-updates";
    type = DirectoryServer.getDefaultAttributeType(ATTR_ASS_SD_SRV);
    builder = new AttributeBuilder(type, ATTR_ASS_SD_SRV);
    Map<Short, Integer> sdSrvTimUps =
      domain.getAssuredSdServerTimeoutUpdates();
    if (sdSrvTimUps.size() > 0)
    {
      for (Short serverId : sdSrvTimUps.keySet())
      {
        String str = serverId + ":" + sdSrvTimUps.get(serverId);
        builder.add(AttributeValues.create(type, str));
      }
      attributes.add(builder.toAttribute());
    }

    /*
     * Status related monitoring fields
     */

    addMonitorData(attributes, "last-status-change-date",
      domain.getLastStatusChangeDate().toString());

    addMonitorData(attributes, "status", domain.getStatus().toString());

    return attributes;

  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The integer value of he attribute to add.
   */
  public static void addMonitorData(
      ArrayList<Attribute> attributes,
      String name,
      int value)
  {
    AttributeType type = DirectoryServer.getDefaultAttributeType(name);
    attributes.add(Attributes.create(type, AttributeValues.create(type,
        String.valueOf(value))));
  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The integer value of he attribute to add.
   */
  public static void addMonitorData(
      ArrayList<Attribute> attributes,
      String name,
      long value)
  {
    AttributeType type = DirectoryServer.getDefaultAttributeType(name);
    attributes.add(Attributes.create(type, AttributeValues.create(type,
        String.valueOf(value))));
  }

  /**
   * Add an attribute with an integer value to the list of monitoring
   * attributes.
   *
   * @param attributes the list of monitoring attributes
   * @param name the name of the attribute to add.
   * @param value The String value of he attribute to add.
   */
  public static void addMonitorData(
      ArrayList<Attribute> attributes,
      String name,
      String value)
  {
    AttributeType type = DirectoryServer.getDefaultAttributeType(name);
    attributes
        .add(Attributes.create(type, AttributeValues.create(type, value)));
  }

  /**
   * Retrieves the length of time in milliseconds that should elapse between
   * calls to the <CODE>updateMonitorData()</CODE> method.  A negative or zero
   * return value indicates that the <CODE>updateMonitorData()</CODE> method
   * should not be periodically invoked.
   *
   * @return  The length of time in milliseconds that should elapse between
   *          calls to the <CODE>updateMonitorData()</CODE> method.
   */
  @Override
  public long getUpdateInterval()
  {
    /* we don't wont to do polling on this monitor */
    return 0;
  }

  /**
   * Performs any processing periodic processing that may be desired to update
   * the information associated with this monitor.  Note that best-effort
   * attempts will be made to ensure that calls to this method come
   * <CODE>getUpdateInterval()</CODE> milliseconds apart, but no guarantees will
   * be made.
   */
  @Override
  public void updateMonitorData()
  {
    //  As long as getUpdateInterval() returns 0, this will never get called
  }
}
