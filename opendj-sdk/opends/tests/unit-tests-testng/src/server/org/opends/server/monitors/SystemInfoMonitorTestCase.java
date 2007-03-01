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
package org.opends.server.monitors;



import org.opends.server.api.MonitorProvider;



/**
 * This class defines a set of tests for the
 * org.opends.server.monitors.SystemInfoMonitorProvider class.
 */
public class SystemInfoMonitorTestCase
       extends GenericMonitorTestCase
{
  /**
   * Creates a new instance of this test case class.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  public SystemInfoMonitorTestCase()
         throws Exception
  {
    super("cn=System Info,cn=Monitor Providers,cn=config");
  }



  /**
   * Retrieves an initialized instance of the associated monitor provider.
   *
   * @return  An initialized instance of the associated monitor provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public MonitorProvider getMonitorInstance()
         throws Exception
  {
    SystemInfoMonitorProvider monitorProvider = new SystemInfoMonitorProvider();
    monitorProvider.initializeMonitorProvider(configEntry);
    return monitorProvider;
  }
}

