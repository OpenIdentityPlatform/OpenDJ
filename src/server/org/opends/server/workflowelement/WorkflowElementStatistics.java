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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement;

import java.util.ArrayList;
import java.util.List;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.InitializationException;


/**
 * This class implements the statistics associated to a workflow element.
 * The workflow element provides a saturation index.
 */
public class WorkflowElementStatistics
    extends MonitorProvider<MonitorProviderCfg> {

  // The instance name for this monitor provider instance.
  private final String instanceName;
  private final WorkflowElement<?> workflowElement;

  /**
   * Constructor.
   * @param workflowElement The workflowElement owning these stats
   */
  public WorkflowElementStatistics(WorkflowElement<?> workflowElement)
  {
    this.instanceName = workflowElement.getWorkflowElementID();
    this.workflowElement = workflowElement;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException, InitializationException
  {
    // No initialization required
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName()
  {
    return this.instanceName + ",cn=Workflow Elements";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Attribute> getMonitorData()
  {
    ArrayList<Attribute> attrs = new ArrayList<Attribute>();

    attrs.add(Attributes.create(
        "ds-mon-saturation-index",
        String.valueOf(this.workflowElement.getSaturationIndex())));

    return attrs;
  }
}
