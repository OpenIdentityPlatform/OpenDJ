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
package org.opends.server.protocols.jmx;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.DirectoryException;
import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * A simple test for JMX connection establishment.
 *
 */
public class JmxConnectTest extends JmxTestCase
{
  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();
  }

  @DataProvider(name="simpleConnect")
  Object[][] createCredentials()
  {
    return new Object[][] {
        {"cn=directory manager", "password", true},
        {"cn=directory manager", "wrongPassword", false},
        {"cn=wrong user", "password", false},
        {"invalid DN", "password", false},
        {"cn=directory manager", null, false},
        {null, "password", false},
        {null, null, false},
        };
  }

  /**
   * That that simple (ot using SSL) connections to the JMX service are
   * accepted when the given
   * credentials are OK and refused when the credentials are invalid.
   *
   * @param user
   * @param password
   * @param expectedResult
   * @throws Exception
   */
  @Test(dataProvider="simpleConnect")
  public void simpleConnect(String user, String password,
      boolean expectedResult) throws Exception
  {
    JMXConnector jmxc = connect(user, password);

    assertEquals((jmxc!= null), expectedResult);
  }

  @DataProvider(name="simpleGet")
  Object[][] createNames()
  {
    return new Object[][] {
        {"cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
            "ds-cfg-listen-port", new Long(1689)},
        {"cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
              "objectclass", null},
        {"cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
              "ds-cfg-ssl-cert-nickname", "adm-server-cert"},
    };
  }

  /**
   * Test simple JMX get.
   *
   * @throws Exception
   */
  @Test(dataProvider="simpleGet")
  public void simpleGet(String dn, String attributeName, Object value)
     throws Exception
  {
    JMXConnector jmxc = connect("cn=directory manager", "password");
    assertNotNull(jmxc);
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

    // Get status of the JMX connection handler
    String jmxName = JMXMBean.getJmxName(DN.decode(dn));
    ObjectName name = ObjectName.getInstance(jmxName);
    Attribute status = (Attribute) mbsc.getAttribute(name, attributeName);
    
    if (value != null)
    {
      Object val = status.getValue();
      assertEquals(val, value);
    }
    else
    {
      assertTrue(status == null);
    }
  }

  /**
   * Test that disabling JMX connection handler does its job by
   *  - opening a JMX connection
   *  - changing the JMX connection handler state to disable
   *  - trying to open a new JMX connection and check that it fails.
   *
   * @throws Exception
   */
  @Test()
  public void disable() throws Exception
  {
    JMXConnector jmxc = connect("cn=directory manager", "password");
    assertNotNull(jmxc);
    // This test can not pass at the moment
    // because disabling JMX through JMX is not possible
    // disableJmx(jmxc);
    // JMXConnector jmxcDisabled = connect("cn=directory manager", "password");
    // assertNull(jmxcDisabled);
  }

  /**
   * Connect to the JMX service.
   * @param user
   * @param password
   * @return
   * @throws MalformedURLException
   * @throws IOException
   */
  private JMXConnector connect(String user, String password)
           throws MalformedURLException, IOException
  {
    HashMap<String, String[]> env = new HashMap<String, String[]>();

    // Provide the credentials required by the server to successfully
    // perform user authentication
    //
    String[] credentials;
    if ((user == null) && (password == null))
    {
      credentials = null;
    }
    else
      credentials = new String[] { user , password };
    env.put("jmx.remote.credentials", credentials);

    // Create an RMI connector client and
    // connect it to the RMI connector server
    //

    JMXServiceURL url = new JMXServiceURL(
      "service:jmx:rmi:///jndi/rmi://localhost:1689/org.opends.server.protocols.jmx.client-unknown");

    JMXConnector jmxc = null;
    try
    {
      jmxc = JMXConnectorFactory.connect(url, env);
    } catch (SecurityException e)
    {}
    return jmxc;
  }

  /**
   * disable the JMX front-end thorugh JMX operation.
   * @throws IOException
   * @throws DirectoryException
   * @throws NullPointerException
   * @throws MalformedObjectNameException
   * @throws ReflectionException
   * @throws MBeanException
   * @throws InstanceNotFoundException
   * @throws AttributeNotFoundException
   *
   */
  private void disableJmx(JMXConnector jmxc)
     throws Exception
  {
    MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

    // Get status of the JMX connection handler
    String jmxName = JMXMBean.getJmxName(
      DN.decode("cn=JMX Connection Handler,cn=Connection Handlers,cn=config"));
    ObjectName name = ObjectName.getInstance(jmxName);
    Attribute status = (Attribute) mbsc.getAttribute(name,
                      "ds-cfg-connection-handler-enabled");
    if (status != null)
      status.getValue();
    Attribute attr = new Attribute("ds-cfg-connection-handler-enabled", false);
    mbsc.setAttribute(name, attr);
    status = (Attribute) mbsc.getAttribute(name,
         "ds-cfg-connection-handler-enabled");

    status = null;
  }
}
