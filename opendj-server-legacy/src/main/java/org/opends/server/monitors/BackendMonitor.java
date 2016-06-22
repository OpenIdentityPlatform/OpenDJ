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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;

/**
 * This class implements a monitor provider that will report generic information
 * for an enabled Directory Server backend, including its backend ID, base DNs,
 * writability mode, and the number of entries it contains.
 */
public class BackendMonitor
       extends MonitorProvider<MonitorProviderCfg>
{
  /** The backend with which this monitor is associated. */
  private Backend<?> backend;

  /** The name for this monitor. */
  private String monitorName;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this backend monitor provider that will work with
   * the provided backend.  Most of the initialization should be handled in the
   * {@code initializeMonitorProvider} method.
   *
   * @param  backend  The backend with which this monitor is associated.
   */
  public BackendMonitor(Backend<?> backend)
  {
    this.backend = backend;
  }

  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
  {
    monitorName = backend.getBackendID() + " Backend";
  }

  @Override
  public String getMonitorInstanceName()
  {
    return monitorName;
  }

  /**
   * Retrieves the objectclass that should be included in the monitor entry
   * created from this monitor provider.
   *
   * @return  The objectclass that should be included in the monitor entry
   *          created from this monitor provider.
   */
  @Override
  public ObjectClass getMonitorObjectClass()
  {
    return DirectoryServer.getSchema().getObjectClass(OC_MONITOR_BACKEND);
  }

  @Override
  public MonitorData getMonitorData()
  {
    Set<DN> baseDNs = backend.getBaseDNs();

    MonitorData attrs = new MonitorData(6);
    attrs.add(ATTR_MONITOR_BACKEND_ID, backend.getBackendID());
    attrs.add(ATTR_MONITOR_BACKEND_BASE_DN, baseDNs);
    attrs.add(ATTR_MONITOR_BACKEND_IS_PRIVATE, backend.isPrivateBackend());
    attrs.add(ATTR_MONITOR_BACKEND_ENTRY_COUNT, backend.getEntryCount());
    attrs.add(ATTR_MONITOR_BASE_DN_ENTRY_COUNT, getBackendEntryCounts(baseDNs));
    attrs.add(ATTR_MONITOR_BACKEND_WRITABILITY_MODE, backend.getWritabilityMode());
    return attrs;
  }

  private Collection<String> getBackendEntryCounts(Set<DN> baseDNs)
  {
    Collection<String> results = new ArrayList<>();
    if (baseDNs.size() == 1)
    {
      // This is done to avoid recalculating the number of entries
      // using the numSubordinates method in the case where the
      // backend has a single base DN.
      results.add(backend.getEntryCount() + " " + baseDNs.iterator().next());
    }
    else
    {
      for (DN dn : baseDNs)
      {
        long entryCount = -1;
        try
        {
          entryCount = backend.getNumberOfEntriesInBaseDN(dn);
        }
        catch (Exception ex)
        {
          logger.traceException(ex);
        }
        results.add(entryCount + " " + dn);
      }
    }
    return results;
  }
}
