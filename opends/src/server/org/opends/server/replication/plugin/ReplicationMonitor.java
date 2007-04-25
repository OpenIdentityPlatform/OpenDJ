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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;

/**
 * Class used to generate monitoring information for the replication.
 */
public class ReplicationMonitor extends MonitorProvider
{
  private ReplicationDomain domain;  // the replication plugin

  /**
   * Create a new replication monitor.
   * @param domain the plugin which created the monitor
   */
  public ReplicationMonitor(ReplicationDomain domain)
  {
    super("Replication monitor " + domain.getBaseDN().toString());
    this.domain = domain;
  }

  /**
   * initialization function for the Monitor.
   * Not used for now
   * @param configEntry the entry to use for initialization
   */
  @Override
  public void initializeMonitorProvider(ConfigEntry configEntry)
  {
    // TODO Auto-generated method stub
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
    return "Replication plugin "  + domain.getBaseDN().toString();
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
    Attribute attr = new Attribute("base-dn", domain.getBaseDN().toString());
    attributes.add(attr);

    /* get the base dn */
    attr = new Attribute("connected-to", domain.getChangelogServer());
    attributes.add(attr);

    /* get number of lost connections */
    addMonitorData(attributes, "lost-connections",
                   domain.getNumLostConnections());

    /* get number of received updates */
    addMonitorData(attributes, "received-updates", domain.getNumRcvdUpdates());

    /* get number of updates sent */
    addMonitorData(attributes, "sent-updates", domain.getNumSentUpdates());

    /* get number of changes in the pending list */
    addMonitorData(attributes, "pending-updates",
                   domain.getPendingUpdatesCount());

    /* get number of changes replayed */
    addMonitorData(attributes, "replayed-updates",
                   domain.getNumProcessedUpdates());

    /* get number of changes successfully */
    addMonitorData(attributes, "replayed-updates-ok",
                   domain.getNumReplayedPostOpCalled());

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
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    for (String str : domain.getServerState().toStringSet())
    {
      values.add(new AttributeValue(type,str));
    }
    attr = new Attribute(type, ATTR_SERVER_STATE, values);
    attributes.add(attr);

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
  private void addMonitorData(ArrayList<Attribute> attributes,
       String name, int value)
  {
    Attribute attr;
    AttributeType type;
    LinkedHashSet<AttributeValue> values;
    type =  DirectoryServer.getDefaultAttributeType(name);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type, String.valueOf(value)));
    attr = new Attribute(type, name, values);
    attributes.add(attr);
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
