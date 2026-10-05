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
package org.opends.quicksetup.installer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that the installer deletes the configuration of the backend it names, whatever its ID holds (issue #1153).
 */
@SuppressWarnings("javadoc")
public class DeleteBackendTestCase extends DirectoryServerTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** Concatenated into a DN, the ID "a,b" would name "ds-cfg-backend-id=a,b,cn=Backends,cn=config". */
  @Test
  public void deletesTheBackendWhoseIDADNHasToEscape() throws Exception
  {
    final Entry backend = TestCaseUtils.makeEntry(
        "dn: ds-cfg-backend-id=a\\,b,cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-backend",
        "objectClass: ds-cfg-pluggable-backend",
        "objectClass: ds-cfg-pdb-backend",
        "ds-cfg-enabled: false",
        "ds-cfg-java-class: org.opends.server.backends.pdb.PDBBackend",
        "ds-cfg-backend-id: a,b",
        "ds-cfg-writability-mode: enabled",
        "ds-cfg-base-dn: o=delete backend test",
        "ds-cfg-db-directory: db_delete_backend_test");
    final Entry indexBranch = TestCaseUtils.makeEntry(
        "dn: cn=Index,ds-cfg-backend-id=a\\,b,cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Index");
    try
    {
      TestCaseUtils.addEntry(backend);
      TestCaseUtils.addEntry(indexBranch);

      new InstallerHelper().deleteBackend("a,b");

      assertThat(DirectoryServer.entryExists(indexBranch.getName())).isFalse();
      assertThat(DirectoryServer.entryExists(backend.getName())).isFalse();
    }
    finally
    {
      for (final DN dn : Arrays.asList(indexBranch.getName(), backend.getName()))
      {
        if (DirectoryServer.entryExists(dn))
        {
          TestCaseUtils.deleteEntry(dn);
        }
      }
    }
  }
}
