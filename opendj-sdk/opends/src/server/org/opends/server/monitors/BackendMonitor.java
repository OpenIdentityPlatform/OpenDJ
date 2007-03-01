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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.monitors;



import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteStringFactory;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DN;
import org.opends.server.types.ObjectClass;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class implements a monitor provider that will report generic information
 * for an enabled Directory Server backend, including its backend ID, base DNs,
 * writability mode, and the number of entries it contains.
 */
public class BackendMonitor
       extends MonitorProvider
{



  // The attribute type that will be used to report the backend ID.
  private AttributeType backendIDType;

  // The attribute type that will be used to report the set of base DNs.
  private AttributeType baseDNType;

  // The attribute type that will be used to report the number of entries.
  private AttributeType entryCountType;

  // The attribute type that will be used to indicate if a backend is private.
  private AttributeType isPrivateType;

  // The attribute type that will be used to report the writability mode.
  private AttributeType writabilityModeType;

  // The backend with which this monitor is associated.
  private Backend backend;

  // The name for this monitor.
  private String monitorName;



  /**
   * Creates a new instance of this backend monitor provider that will work with
   * the provided backend.  Most of the initialization should be handled in the
   * {@code initializeMonitorProvider} method.
   *
   * @param  backend  The backend with which this monitor is associated.
   */
  public BackendMonitor(Backend backend)
  {
    super(backend.getBackendID() + " Backend");


    this.backend = backend;
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMonitorProvider(ConfigEntry configEntry)
  {

    monitorName = backend.getBackendID() + " Backend";

    backendIDType = DirectoryConfig.getAttributeType(ATTR_MONITOR_BACKEND_ID,
                                                     true);

    baseDNType = DirectoryConfig.getAttributeType(ATTR_MONITOR_BACKEND_BASE_DN,
                                                  true);

    entryCountType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_BACKEND_ENTRY_COUNT,
                                          true);

    isPrivateType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_BACKEND_IS_PRIVATE,
                                          true);

    writabilityModeType =
         DirectoryConfig.getAttributeType(ATTR_MONITOR_BACKEND_WRITABILITY_MODE,
                                          true);
  }



  /**
   * {@inheritDoc}
   */
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
  public ObjectClass getMonitorObjectClass()
  {

    return DirectoryConfig.getObjectClass(OC_MONITOR_BACKEND, true);
  }



  /**
   * {@inheritDoc}
   */
  public long getUpdateInterval()
  {

    // We don't need do anything on a periodic basis.
    return 0;
  }



  /**
   * {@inheritDoc}
   */
  public void updateMonitorData()
  {

    // No implementaiton is required.
  }



  /**
   * {@inheritDoc}
   */
  public List<Attribute> getMonitorData()
  {

    LinkedList<Attribute> attrs = new LinkedList<Attribute>();

    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(backendIDType,
                        ByteStringFactory.create(backend.getBackendID())));
    attrs.add(new Attribute(backendIDType, ATTR_MONITOR_BACKEND_ID, values));

    values = new LinkedHashSet<AttributeValue>();
    DN[] baseDNs = backend.getBaseDNs();
    for (DN dn : baseDNs)
    {
      values.add(new AttributeValue(baseDNType,
                                    ByteStringFactory.create(dn.toString())));
    }
    attrs.add(new Attribute(baseDNType, ATTR_MONITOR_BACKEND_BASE_DN, values));

    values = new LinkedHashSet<AttributeValue>();
    values.add(BooleanSyntax.createBooleanValue(backend.isPrivateBackend()));
    attrs.add(new Attribute(isPrivateType, ATTR_MONITOR_BACKEND_IS_PRIVATE,
                            values));

    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(entryCountType,
         ByteStringFactory.create(String.valueOf(backend.getEntryCount()))));
    attrs.add(new Attribute(entryCountType, ATTR_MONITOR_BACKEND_ENTRY_COUNT,
                            values));

    values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(writabilityModeType,
         ByteStringFactory.create(
              String.valueOf(backend.getWritabilityMode()))));
    attrs.add(new Attribute(writabilityModeType,
                            ATTR_MONITOR_BACKEND_WRITABILITY_MODE, values));

    return attrs;
  }
}

