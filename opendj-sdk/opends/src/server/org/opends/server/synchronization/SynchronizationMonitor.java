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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;

/**
 * Class used to generate monitoring information for the Synchronization.
 */
public class SynchronizationMonitor extends MonitorProvider
{
  private SynchronizationDomain domain;  // the synchronization plugin

  /**
   * Create a new Synchronization monitor.
   * @param domain the plugin which created the monitor
   */
  public SynchronizationMonitor(SynchronizationDomain domain)
  {
    super("Synchronization monitor " + domain.getBaseDN().toString());
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
    return "synchronization plugin "  + domain.getBaseDN().toString();
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

    /* get number of received updates */
    final String ATTR_UPDATE_RECVD = "received-updates";
    AttributeType type =
                    DirectoryServer.getDefaultAttributeType(ATTR_UPDATE_RECVD);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                                  String.valueOf(domain.getNumRcvdUpdates())));
    attr = new Attribute(type, "received-updates", values);
    attributes.add(attr);

    /* get number of updates sent */
    final String ATTR_UPDATE_SENT = "sent-updates";
    type =  DirectoryServer.getDefaultAttributeType(ATTR_UPDATE_SENT);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                                  String.valueOf(domain.getNumSentUpdates())));
    attr = new Attribute(type, "sent-updates", values);
    attributes.add(attr);

    /* get number of changes in the pending list */
    final String ATTR_UPDATE_PENDING = "pending-updates";
    type =  DirectoryServer.getDefaultAttributeType(ATTR_UPDATE_PENDING);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                              String.valueOf(domain.getPendingUpdatesCount())));
    attr = new Attribute(type, "pending-updates", values);
    attributes.add(attr);

    /* get number of changes replayed */
    final String ATTR_REPLAYED_UPDATE = "replayed-updates";
    type =  DirectoryServer.getDefaultAttributeType(ATTR_REPLAYED_UPDATE);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                              String.valueOf(domain.getNumProcessedUpdates())));
    attr = new Attribute(type, ATTR_REPLAYED_UPDATE, values);
    attributes.add(attr);

    /* get number of changes successfully */
    final String ATTR_REPLAYED_UPDATE_OK = "replayed-updates-ok";
    type =  DirectoryServer.getDefaultAttributeType(ATTR_REPLAYED_UPDATE_OK);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                          String.valueOf(domain.getNumReplayedPostOpCalled())));
    attr = new Attribute(type, ATTR_REPLAYED_UPDATE_OK, values);
    attributes.add(attr);

    /* get debugCount */
    final String DEBUG_COUNT = "debug-count";
    type =  DirectoryServer.getDefaultAttributeType(DEBUG_COUNT);
    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(type,
                          String.valueOf(domain.getDebugCount())));
    attr = new Attribute(type, DEBUG_COUNT, values);
    attributes.add(attr);

    /* get the Server State */
    final String ATTR_SERVER_STATE = "server-state";
    type =  DirectoryServer.getDefaultAttributeType(ATTR_SERVER_STATE);
    values = new LinkedHashSet<AttributeValue>();
    for (String str : domain.getServerState().toStringSet())
    {
      values.add(new AttributeValue(type,str));
    }
    attr = new Attribute(type, ATTR_SERVER_STATE, values);
    attributes.add(attr);

    return attributes;

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
