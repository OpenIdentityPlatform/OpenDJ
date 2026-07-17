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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.protocols.jmx;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Regression test for GHSA-qj63-3vrg-vcfx / OPENDJ-002 — incomplete fix of
 * CVE-2026-46495.
 * <p>
 * The CVE-2026-46495 fix scoped its JEP 290 deserialization filter to the
 * {@code credentials} object only
 * ({@code jmx.remote.rmi.server.credentials.filter.pattern}), leaving the
 * arguments of MBean operations ({@code Object[] params}) to be unmarshalled
 * after authentication with no serial filter at all. The follow-up fix also
 * installs the connector-wide
 * {@code jmx.remote.rmi.server.serial.filter.pattern}, restricting MBean
 * operation arguments to a small allowlist of JDK / JMX management types.
 * <p>
 * This test exercises the surface end-to-end. It:
 * <ol>
 * <li>registers a do-nothing target MBean in the server's MBean server (so the
 *     JDK's {@code RMIConnectionImpl.invoke} passes its {@code getClassLoaderFor}
 *     check and reaches the argument-unmarshalling step with a class loader that
 *     can see the canary type);</li>
 * <li>authenticates over JMX/RMI as a user holding {@code JMX_READ};</li>
 * <li>invokes an operation passing a custom {@link Serializable} "canary" as an
 *     argument.</li>
 * </ol>
 * With the fix in place the connector-wide serial filter rejects the canary
 * type during unmarshalling: the call fails with a filter rejection and the
 * canary's {@code readObject()} never runs. Because the test server runs
 * in-process, that server-side deserialization (had it happened) would have
 * flipped a static flag the test observes directly. Before the fix this test
 * fails (the flag is set), which is exactly the vulnerability it guards
 * against.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "jmx" }, sequential = true)
public class JmxMBeanArgDeserializationTest extends JmxTestCase
{
  /** DN of the JMX_READ user created for this test. */
  private static final String JMX_USER_DN = "cn=Privileged JMX User,o=test";

  /** ObjectName of the helper target MBean registered for the duration of the test. */
  private static final String TARGET_MBEAN = "org.opends.server.test:type=JmxDeserCanaryTarget";

  /** Minimal Standard MBean used only as a registered {@code invoke()} target. */
  public interface CanaryTargetMBean
  {
    // No attributes or operations: the operation we invoke deliberately does
    // not exist, so dispatch fails -- but only after the arguments have been
    // deserialized.
  }

  /** Loaded by the test/app class loader, which can resolve {@link DeserializationCanary}. */
  public static final class CanaryTarget implements CanaryTargetMBean
  {
  }

  /**
   * A serializable marker whose custom {@code readObject} records that it was
   * deserialized. It stands in for an attacker-supplied gadget: a connector-wide
   * serial filter restricting MBean argument types to the small set OpenDJ
   * actually accepts would reject this class before {@code readObject} ever ran.
   */
  public static final class DeserializationCanary implements Serializable
  {
    private static final long serialVersionUID = 1L;

    /** Set true server-side when this object is unmarshalled. */
    static volatile boolean deserialized;

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      in.defaultReadObject();
      deserialized = true;
    }
  }

  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    TestCaseUtils.addEntries(
        "dn: " + JMX_USER_DN,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: Privileged JMX User",
        "givenName: Privileged",
        "sn: User",
        "uid: privileged.jmx.user",
        "userPassword: password",
        "ds-privilege-name: jmx-read",
        "ds-privilege-name: jmx-write",
        "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy,cn=Password Policies,cn=config");
  }

  @AfterClass
  public void afterClass() throws Exception
  {
    DeleteOperation deleteOperation = getRootConnection().processDelete(DN.valueOf(JMX_USER_DN));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * An authenticated client's MBean operation argument of an arbitrary type is
   * rejected by the connector-wide serial filter during unmarshalling, before
   * its {@code readObject()} can run.
   */
  @Test
  public void mbeanOperationArgumentsAreFilteredBeforeDeserialization() throws Exception
  {
    DeserializationCanary.deserialized = false;

    MBeanServer serverMBeans = DirectoryServer.getJMXMBeanServer();
    ObjectName target = new ObjectName(TARGET_MBEAN);
    if (serverMBeans.isRegistered(target))
    {
      serverMBeans.unregisterMBean(target);
    }
    serverMBeans.registerMBean(new CanaryTarget(), target);

    Exception thrown = null;
    OpendsJmxConnector connector = connect(JMX_USER_DN, "password", TestCaseUtils.getServerJmxPort());
    assertNotNull(connector, "JMX_READ user should be able to authenticate to the JMX connector");
    try
    {
      MBeanServerConnection mbsc = connector.getMBeanServerConnection();

      // getClassLoaderFor(target) succeeds (target is registered), so the call
      // reaches argument unmarshalling. The connector-wide serial filter must
      // reject the canary type there, before readObject() runs.
      try
      {
        mbsc.invoke(target,
            "thisOperationDoesNotExist",
            new Object[] { new DeserializationCanary() },
            new String[] { "java.lang.Object" });
        fail("Expected the non-allowlisted argument to be rejected by the serial filter");
      }
      catch (Exception expected)
      {
        thrown = expected;
      }
    }
    finally
    {
      connector.close();
      if (serverMBeans.isRegistered(target))
      {
        serverMBeans.unregisterMBean(target);
      }
    }

    assertFalse(DeserializationCanary.deserialized,
        "VULNERABLE (GHSA-qj63-3vrg-vcfx): an authenticated client's MBean operation argument "
            + "was deserialized server-side. The connector-wide serial filter "
            + "(jmx.remote.rmi.server.serial.filter.pattern) must reject non-allowlisted types "
            + "before readObject() runs.");
    assertTrue(isSerialFilterRejection(thrown),
        "Expected a JEP 290 serial-filter rejection of the argument, but got: " + thrown);
  }

  /** True if the throwable chain shows a JEP 290 serial-filter rejection. */
  private static boolean isSerialFilterRejection(Throwable t)
  {
    for (Throwable c = t; c != null; c = c.getCause())
    {
      if (c instanceof java.io.InvalidClassException)
      {
        return true;
      }
      String message = c.getMessage();
      if (message != null
          && (message.contains("REJECTED") || message.contains("filter status")
              || message.contains("serial filter")))
      {
        return true;
      }
    }
    return false;
  }

  /** Connects to the in-process JMX/RMI connector with the given credentials. */
  private OpendsJmxConnector connect(String user, String password, int jmxPort) throws IOException
  {
    Map<String, Object> env = new HashMap<>();
    env.put("jmx.remote.credentials", new String[] { user, password });
    env.put("jmx.remote.x.client.connection.check.period", 0);
    try
    {
      OpendsJmxConnector connector = new OpendsJmxConnector("localhost", jmxPort, env);
      connector.connect();
      return connector;
    }
    catch (SecurityException | IOException e)
    {
      return null;
    }
  }
}
