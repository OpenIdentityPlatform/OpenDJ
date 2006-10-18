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

package org.opends.server.monitors;

import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Attribute;

import java.util.List;
import java.util.Collections;

/**
 * This test monitor provider has a DN embedded in its instance name.
 */
class TestMonitorProvider extends MonitorProvider
{
  public TestMonitorProvider()
  {
    super("Test Monitor Thread");
  }

  public void initializeMonitorProvider(ConfigEntry configEntry)
       throws ConfigException, InitializationException
  {
    // No implementation required.
  }

  public String getMonitorInstanceName()
  {
    return "Test monitor for dc=example,dc=com";
  }

  public long getUpdateInterval()
  {
    return 0;
  }

  public void updateMonitorData()
  {
    // No implementation required.
  }

  public List<Attribute> getMonitorData()
  {
    return Collections.emptyList();
  }
}
