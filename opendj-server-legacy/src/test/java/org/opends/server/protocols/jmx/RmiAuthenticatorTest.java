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

import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests JMX RMI credential deserialization hardening. */
@Test(groups = { "precommit", "jmx" }, sequential = true)
public class RmiAuthenticatorTest extends DirectoryServerTestCase
{
  /** Invalid credential shapes rejected before any bind attempt. */
  @DataProvider(name = "invalidCredentials")
  public Object[][] invalidCredentials()
  {
    return new Object[][]
    {
      { null },
      { new Object[] { "cn=Directory Manager", "password" } },
      { new Date() },
      { new String[0] },
      { new String[] { "cn=Directory Manager" } },
      { new String[] { "cn=Directory Manager", "password", "extra" } },
      { new String[] { null, "password" } },
      { new String[] { "cn=Directory Manager", null } }
    };
  }

  /** Verifies that RmiAuthenticator only accepts a two-element String array. */
  @Test(dataProvider = "invalidCredentials", expectedExceptions = SecurityException.class)
  public void rejectsInvalidCredentialShapes(Object credentials)
  {
    new RmiAuthenticator(null).authenticate(credentials);
  }

  /** Verifies that RMI connector environment constrains credential unmarshalling. */
  @Test
  public void configuresCredentialDeserializationProtection()
  {
    Map<String, Object> env = new HashMap<>();
    RmiConnector.configureJmxDeserializationProtection(env);

    assertEquals(env.get(RmiConnector.JMX_REMOTE_RMI_SERVER_CREDENTIAL_TYPES),
        new String[] { String.class.getName(), String[].class.getName() });
    assertEquals(env.get(RmiConnector.JMX_REMOTE_RMI_SERVER_CREDENTIALS_FILTER_PATTERN),
        "maxdepth=3;maxarray=2;java.lang.String;!*");
    // The connector-wide filter must NOT be set, so legitimate JMX traffic
    // (MBean operations, notifications) is not affected by the allowlist.
    assertNull(env.get("jmx.remote.rmi.server.serial.filter.pattern"));
  }

  /** Verifies that each environment receives its own credential type array. */
  @Test
  public void credentialTypesAreDefensivelyCopied()
  {
    Map<String, Object> env = new HashMap<>();
    RmiConnector.configureJmxDeserializationProtection(env);
    String[] credentialTypes =
        (String[]) env.get(RmiConnector.JMX_REMOTE_RMI_SERVER_CREDENTIAL_TYPES);
    credentialTypes[0] = Date.class.getName();

    Map<String, Object> env2 = new HashMap<>();
    RmiConnector.configureJmxDeserializationProtection(env2);
    assertEquals(((String[]) env2.get(RmiConnector.JMX_REMOTE_RMI_SERVER_CREDENTIAL_TYPES))[0],
        String.class.getName());
  }

  /** Verifies the configured filter allows only the expected credential payload. */
  @Test
  public void serialFilterAllowsOnlyTwoElementStringArray() throws Exception
  {
    Map<String, Object> env = new HashMap<>();
    RmiConnector.configureJmxDeserializationProtection(env);
    String filterPattern = (String) env.get(RmiConnector.JMX_REMOTE_RMI_SERVER_CREDENTIALS_FILTER_PATTERN);

    assertEquals(readWithFilter(new String[] { "uid", "password" }, filterPattern),
        new String[] { "uid", "password" });
    assertRejectedByFilter(new Object[] { "uid", "password" }, filterPattern);
    assertRejectedByFilter(new String[] { "uid", "password", "extra" }, filterPattern);
    assertRejectedByFilter(new Date(), filterPattern);
  }

  private Object readWithFilter(Object object, String filterPattern) throws Exception
  {
    byte[] bytes = serialize(object);
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)))
    {
      in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(filterPattern));
      return in.readObject();
    }
  }

  private byte[] serialize(Object object) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes))
    {
      out.writeObject(object);
    }
    return bytes.toByteArray();
  }

  private void assertRejectedByFilter(Object object, String filterPattern) throws Exception
  {
    try
    {
      readWithFilter(object, filterPattern);
      fail("Expected object to be rejected by the JMX credential serial filter");
    }
    catch (InvalidClassException expected)
    {
      // Expected: ObjectInputFilter rejected the class or array length.
    }
  }
}
