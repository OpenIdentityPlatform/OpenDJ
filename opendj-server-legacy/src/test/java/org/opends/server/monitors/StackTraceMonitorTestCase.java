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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import org.opends.server.api.MonitorProvider;

/**
 * This class defines a set of tests for the
 * org.opends.server.monitors.StackTraceMonitorProvider class.
 */
public class StackTraceMonitorTestCase
       extends GenericMonitorTestCase
{
  /**
   * Creates a new instance of this test case class.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  public StackTraceMonitorTestCase()
         throws Exception
  {
    super("cn=JVM Stack Trace,cn=Monitor Providers,cn=config");
  }

  @Override
  protected MonitorProvider<?> getMonitorInstance() throws Exception
  {
    StackTraceMonitorProvider monitorProvider = new StackTraceMonitorProvider();
    monitorProvider.initializeMonitorProvider(null);
    return monitorProvider;
  }
}
