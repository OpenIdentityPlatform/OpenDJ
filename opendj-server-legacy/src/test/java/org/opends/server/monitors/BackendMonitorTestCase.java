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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.monitors;

import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.Test;

/** This class defines a set of tests for the {@link BackendMonitor} class. */
@Test
public class BackendMonitorTestCase extends GenericMonitorTestCase
{
  /**
   * Creates a new instance of this test case class.
   *
   * @throws  Exception  If an unexpected problem occurred.
   */
  public BackendMonitorTestCase() throws Exception
  {
    super(null);
  }

  /**
   * Retrieves an initialized instance of the associated monitor provider.
   *
   * @return  An initialized instance of the associated monitor provider.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Override
  protected MonitorProvider<?> getMonitorInstance() throws Exception
  {
    MonitorProvider<?> provider = DirectoryServer.getMonitorProviders().get("userroot backend");
    provider.initializeMonitorProvider(null);
    return provider;
  }
}
