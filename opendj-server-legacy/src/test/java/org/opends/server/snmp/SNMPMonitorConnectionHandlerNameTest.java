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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.snmp;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.modelmbean.RequiredModelMBean;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.JMXMBean;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Verifies that the SNMP extension still finds a connection handler and its statistics by the JMX names that
 * {@link JMXMBean#getJmxName(DN)} gives their monitor entries, including a handler whose name holds an IP address
 * and is therefore percent-encoded (issue #1153).
 */
@SuppressWarnings("javadoc")
public class SNMPMonitorConnectionHandlerNameTest extends DirectoryServerTestCase
{
  /** {@link SNMPMonitor} acts through the root internal connection, which needs a started server. */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @DataProvider
  public Object[][] handlerNames()
  {
    return new Object[][] {
      { "LDAP Connection Handler 0.0.0.0 port 1389" },
      { "LDAPS Connection Handler 192.168.0.1 port 1636" },
      { "LDAP Connection Handler 0:0:0:0:0:0:0:0 port 1389" },
      { "LDAP Connection Handler localhost port 1389" },
    };
  }

  @Test(dataProvider = "handlerNames")
  public void connectionHandlerAndItsStatisticsAreFound(String handlerName) throws Exception
  {
    // A server of its own, which MBeanServerFactory does not keep a reference to.
    final MBeanServer server = MBeanServerFactory.newMBeanServer();
    final ObjectName handler = register(server, handlerName);
    final ObjectName statistics = register(server, handlerName + " Statistics");
    final SNMPMonitor monitor = newMonitor(server);

    assertThat(monitor.getConnectionHandlers()).containsExactly(handler);
    assertThat(monitor.getConnectionHandlersStatistics()).containsExactly(statistics);
    assertThat(monitor.getConnectionHandlerStatistics(handler)).isEqualTo(statistics);
    assertThat(monitor.getConnectionHandler(statistics)).isEqualTo(handler);
  }

  private static ObjectName register(MBeanServer server, String monitorName) throws Exception
  {
    final ObjectName name =
        new ObjectName(JMXMBean.getJmxName(DN.valueOf("cn=monitor").child("cn", monitorName)));
    server.registerMBean(new RequiredModelMBean(), name);
    return name;
  }

  /** {@link SNMPMonitor#getMonitor(MBeanServer)} keeps the first server it is given, so build one per server. */
  private static SNMPMonitor newMonitor(MBeanServer server) throws Exception
  {
    final Constructor<SNMPMonitor> constructor = SNMPMonitor.class.getDeclaredConstructor(MBeanServer.class);
    constructor.setAccessible(true);
    return constructor.newInstance(server);
  }
}
