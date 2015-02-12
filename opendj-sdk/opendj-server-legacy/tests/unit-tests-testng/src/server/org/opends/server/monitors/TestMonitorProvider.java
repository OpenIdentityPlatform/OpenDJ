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
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.server.monitors;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Attribute;

import java.util.List;
import java.util.Collections;

/**
 * This test monitor provider has a DN embedded in its instance name.
 */
class TestMonitorProvider extends MonitorProvider<MonitorProviderCfg>
{
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
       throws ConfigException, InitializationException
  {
    // No implementation required.
  }

  public String getMonitorInstanceName()
  {
    return "Test monitor for dc=example,dc=com";
  }

  public List<Attribute> getMonitorData()
  {
    return Collections.emptyList();
  }
}
