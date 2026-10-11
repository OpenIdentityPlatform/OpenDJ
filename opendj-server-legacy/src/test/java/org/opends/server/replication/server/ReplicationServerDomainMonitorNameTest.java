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
package org.opends.server.replication.server;

import static org.assertj.core.api.Assertions.*;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the monitor entry that the name of a {@link ReplicationServerDomain} gives (issue #1153). */
@SuppressWarnings("javadoc")
public class ReplicationServerDomainMonitorNameTest extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] baseDNs()
  {
    return new Object[][] {
      { "dc=example,dc=com", "dc_example_dc_com" },
      // An RDN with two AVAs: the '+' must not start a second AVA in the monitor entry RDN
      { "cn=a+sn=b,dc=x", "cn_a+sn_b_dc_x" },
      { "o=a\\,b", "o_a\\_b" },
    };
  }

  @Test(dataProvider = "baseDNs")
  public void monitorNameIsTheRelativeDNOfTheDomainMonitorEntry(String baseDN, String domainName)
  {
    final String name = ReplicationServerDomain.getMonitorInstanceName(2, "host:8989", DN.valueOf(baseDN));

    assertThat((Object) DN.valueOf("cn=" + name + ",cn=monitor")).isEqualTo(DN.valueOf("cn=monitor")
        .child("cn", "Replication").child("cn", domainName).child("cn", "Replication server RS(2) host:8989"));
  }
}
