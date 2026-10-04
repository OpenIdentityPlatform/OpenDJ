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
package org.opends.server.config;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import javax.management.ObjectName;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the JMX object names that {@link JMXMBean#getJmxName(DN)} builds from DNs (issue #1153). */
@SuppressWarnings("javadoc")
public class JMXMBeanNameTest extends DirectoryServerTestCase
{
  /** Names whose values hold only letters, digits and spaces keep the name they always had. */
  @DataProvider
  public Object[][] unchangedNames()
  {
    return new Object[][] {
      { "cn=monitor", "org.opends.server:Name=rootDSE,Rdn1=cn-monitor" },
      { "ds-cfg-backend-id=userRoot,cn=Backends,cn=config",
        "org.opends.server:Name=rootDSE,Rdn1=cn-config,Rdn2=cn-Backends,Rdn3=dscfgbackendid-userRoot" },
      { "cn=JVM Memory Usage,cn=monitor", "org.opends.server:Name=rootDSE,Rdn1=cn-monitor,Rdn2=cn-JVM_Memory_Usage" },
    };
  }

  @Test(dataProvider = "unchangedNames")
  public void plainNamesAreUnchanged(String dn, String expectedName) throws Exception
  {
    assertThat(JMXMBean.getJmxName(DN.valueOf(dn))).isEqualTo(expectedName);
  }

  /**
   * The SNMP extension finds the connection handlers and their statistics by "Connection_Handler" and
   * "_Statistics" in these names, so a space stays '_' in a percent-encoded value too.
   */
  @DataProvider
  public Object[][] connectionHandlerNames()
  {
    return new Object[][] {
      { "cn=LDAP Connection Handler 0.0.0.0 port 1389,cn=monitor",
        "org.opends.server:Name=rootDSE,Rdn1=cn-monitor,Rdn2=cn-LDAP_Connection_Handler_0%2E0%2E0%2E0_port_1389" },
      { "cn=LDAP Connection Handler 0.0.0.0 port 1389 Statistics,cn=monitor",
        "org.opends.server:Name=rootDSE,Rdn1=cn-monitor,"
            + "Rdn2=cn-LDAP_Connection_Handler_0%2E0%2E0%2E0_port_1389_Statistics" },
    };
  }

  @Test(dataProvider = "connectionHandlerNames")
  public void encodedValuesKeepUnderscoreForSpace(String dn, String expectedName) throws Exception
  {
    assertThat(JMXMBean.getJmxName(DN.valueOf(dn))).isEqualTo(expectedName);
  }

  /** Each group holds DNs whose values only differ by characters that the old mapping dropped. */
  @DataProvider
  public Object[][] distinctDNs()
  {
    return new Object[][] {
      { new String[] { "ds-cfg-backend-id=user-root,cn=Backends,cn=config",
                       "ds-cfg-backend-id=userroot,cn=Backends,cn=config",
                       "ds-cfg-backend-id=user_root,cn=Backends,cn=config",
                       "ds-cfg-backend-id=user root,cn=Backends,cn=config",
                       "ds-cfg-backend-id=user%20root,cn=Backends,cn=config" } },
      { new String[] { "cn=a\\,b,cn=monitor", "cn=ab,cn=monitor", "cn=a,cn=b,cn=monitor", "cn=a+sn=b,cn=monitor",
                       "cn=a\\+sn\\=b,cn=monitor", "cn=asn\\=b,cn=monitor" } },
      { new String[] { "cn=J\\C3\\B6rg,cn=monitor", "cn=Jrg,cn=monitor", "cn=J\\C3\\A4rg,cn=monitor" } },
      { new String[] { "cn=a.b c,cn=monitor", "cn=a.b_c,cn=monitor", "cn=a.b%20c,cn=monitor",
                       "cn=a.b%5Fc,cn=monitor" } },
    };
  }

  @Test(dataProvider = "distinctDNs")
  public void distinctDNsGetDistinctValidNames(String[] dns) throws Exception
  {
    final Set<String> names = new HashSet<>();
    for (String dn : dns)
    {
      final String name = JMXMBean.getJmxName(DN.valueOf(dn));
      assertThat(name).as("JMX name of %s", dn).isNotNull();
      assertThat(new ObjectName(name).isPattern()).as("JMX name %s", name).isFalse();
      names.add(name);
    }
    assertThat(names).hasSize(dns.length);
  }
}
