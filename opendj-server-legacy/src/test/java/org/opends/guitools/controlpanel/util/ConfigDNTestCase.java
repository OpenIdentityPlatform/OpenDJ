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
package org.opends.guitools.controlpanel.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the configuration DNs that the control panel and the installer build from a backend ID and an index name
 * (issue #1153). Neither has a pattern, so they may hold any character a DN has to escape.
 */
@SuppressWarnings("javadoc")
public class ConfigDNTestCase extends DirectoryServerTestCase
{
  private static final DN BACKENDS = DN.valueOf("cn=Backends,cn=config");

  @DataProvider
  public Object[][] names()
  {
    return new Object[][] {
      { "userRoot" },
      { "monitor,ou=a b" },
      { "a+sn=b" },
      { "a\\b" },
      { " a " },
    };
  }

  @Test(dataProvider = "names")
  public void backendConfigDNNamesTheBackendBelowCnBackends(String backendID)
  {
    final DN dn = Utilities.getBackendConfigDN(backendID);
    assertThat((Object) dn.parent()).isEqualTo(BACKENDS);
    assertThat((Object) dn.rdn()).isEqualTo(new RDN("ds-cfg-backend-id", backendID));
  }

  @Test(dataProvider = "names")
  public void indexConfigDNNamesTheIndexBelowItsBackend(String name)
  {
    final DN backendDN = BACKENDS.child("ds-cfg-backend-id", name);

    final DN index = Utilities.getIndexConfigDN(name, name, false);
    assertThat((Object) index.parent(2)).isEqualTo(backendDN);
    assertThat((Object) index.parent().rdn()).isEqualTo(new RDN("cn", "Index"));
    assertThat((Object) index.rdn()).isEqualTo(new RDN("ds-cfg-attribute", name));

    final DN vlvIndex = Utilities.getIndexConfigDN(name, name, true);
    assertThat((Object) vlvIndex.parent(2)).isEqualTo(backendDN);
    assertThat((Object) vlvIndex.parent().rdn()).isEqualTo(new RDN("cn", "VLV Index"));
    assertThat((Object) vlvIndex.rdn()).isEqualTo(new RDN("ds-cfg-name", name));
  }
}
