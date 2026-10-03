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
package org.opends.server.monitors;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.types.Entry;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A monitor provider whose name holds ',', '+' or '\' must get the monitor entry it names, and must not
 * break the rest of cn=monitor (issue #1153).
 */
@SuppressWarnings("javadoc")
public class MonitorDNTestCase extends MonitorTestCase
{
  private static final DN MONITOR_ROOT = DN.valueOf("cn=monitor");

  /** A monitor provider of a third party, which gives its name as is. */
  private static final class NamedMonitorProvider extends MonitorProvider<MonitorProviderCfg>
  {
    private final String name;

    NamedMonitorProvider(String name)
    {
      this.name = name;
    }

    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
    {
      // No implementation required.
    }

    @Override
    public String getMonitorInstanceName()
    {
      return name;
    }

    @Override
    public MonitorData getMonitorData()
    {
      return new MonitorData(0);
    }
  }

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** The name of {@link TestMonitorProvider} is a relative DN: it still names an entry two levels down. */
  @Test
  public void relativeDNNameKeepsItsTree()
  {
    assertThat((Object) DirectoryServer.getMonitorProviderDN(new TestMonitorProvider()))
        .isEqualTo(DN.valueOf("cn=Test monitor for dc=example,dc=com,cn=monitor"));
  }

  @DataProvider
  public Object[][] namesThatAreNotRelativeDNs()
  {
    return new Object[][] { { "LDAP, internal 0.0.0.0 port 41390" }, { "a+b" }, { "a;b" }, { "x\\" } };
  }

  @Test(dataProvider = "namesThatAreNotRelativeDNs")
  public void nameThatIsNotARelativeDNNamesOneEntry(String name) throws Exception
  {
    final NamedMonitorProvider provider = new NamedMonitorProvider(name);
    assertThat((Object) DirectoryServer.getMonitorProviderDN(provider)).isEqualTo(MONITOR_ROOT.child("cn", name));

    DirectoryServer.registerMonitorProvider(provider);
    try
    {
      assertMonitorIsSearchable();
      assertEntryExists(MONITOR_ROOT.child("cn", name));
    }
    finally
    {
      DirectoryServer.deregisterMonitorProvider(provider);
    }
  }

  @DataProvider
  public Object[][] connectionHandlerClasses()
  {
    return new Object[][] { { LDAPConnectionHandler.class.getName() }, { LDAPConnectionHandler2.class.getName() } };
  }

  /** The connection handler, its client connections and its statistics each get the entry they name. */
  @Test(dataProvider = "connectionHandlerClasses")
  public void connectionHandlerWithACommaInItsName(String javaClass) throws Exception
  {
    // Unescaped, "LDAP,ou=internal 127.0.0.1 port N" would be a relative DN two levels down
    final String name = "LDAP,ou=internal";
    final int port = TestCaseUtils.findFreePort();
    final Entry handlerEntry = TestCaseUtils.makeEntry(
        "dn: cn=LDAP\\,ou\\=internal,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-ldap-connection-handler",
        "cn: " + name,
        "ds-cfg-java-class: " + javaClass,
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 127.0.0.1",
        "ds-cfg-listen-port: " + port,
        "ds-cfg-allow-ldap-v2: false",
        "ds-cfg-use-ssl: false",
        "ds-cfg-allow-start-tls: false");
    TestCaseUtils.addEntry(handlerEntry);
    try
    {
      final String handlerName = connectionHandlerName(name);
      assertMonitorIsSearchable();
      assertEntryExists(MONITOR_ROOT.child("cn", handlerName));
      assertEntryExists(MONITOR_ROOT.child("cn", handlerName).child("cn", "Client Connections"));
      assertEntryExists(MONITOR_ROOT.child("cn", handlerName + " Statistics"));
    }
    finally
    {
      TestCaseUtils.deleteEntry(handlerEntry);
    }
  }

  @DataProvider
  public Object[][] storageBackends()
  {
    return new Object[][] {
      { "ds-cfg-je-backend", "org.opends.server.backends.jeb.JEBackend", " JE Database" },
      { "ds-cfg-pdb-backend", "org.opends.server.backends.pdb.PDBBackend", " PDB Database" },
    };
  }

  /** A backend whose ID holds ',' and '=' gets its backend, storage, database and disk space monitor entries. */
  @Test(dataProvider = "storageBackends")
  public void backendWithACommaInItsID(String objectClass, String javaClass, String databaseSuffix) throws Exception
  {
    // Unescaped, "monitor,ou=a b Backend" would be a relative DN two levels down
    final String backendID = "monitor,ou=a b";
    final Entry backendEntry = TestCaseUtils.makeEntry(
        "dn: ds-cfg-backend-id=monitor\\,ou\\=a b,cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-backend",
        "objectClass: ds-cfg-pluggable-backend",
        "objectClass: " + objectClass,
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: " + javaClass,
        "ds-cfg-backend-id: " + backendID,
        "ds-cfg-writability-mode: enabled",
        "ds-cfg-base-dn: o=monitor dn test",
        "ds-cfg-db-directory: db_monitor_dn_test",
        "ds-cfg-db-cache-percent: 2");
    TestCaseUtils.addEntry(backendEntry);
    try
    {
      assertMonitorIsSearchable();
      assertEntryExists(MONITOR_ROOT.child("cn", backendID + " Backend"));
      assertEntryExists(MONITOR_ROOT.child("cn", backendID + " Storage"));
      assertEntryExists(MONITOR_ROOT.child("cn", backendID + databaseSuffix));
      assertEntryExists(MONITOR_ROOT.child("cn", "Disk Space Monitor").child("cn", backendID + " backend"));
    }
    finally
    {
      TestCaseUtils.deleteEntry(backendEntry);
    }
  }

  /** An entry cache whose name holds ',' and '=' gets its monitor entry. */
  @Test
  public void entryCacheWithACommaInItsName() throws Exception
  {
    // Unescaped, "FIFO,ou=a b Entry Cache" would be a relative DN two levels down
    final String cacheName = "FIFO,ou=a b";
    final Entry cacheEntry = TestCaseUtils.makeEntry(
        "dn: cn=FIFO\\,ou\\=a b,cn=Entry Caches,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-entry-cache",
        "objectClass: ds-cfg-fifo-entry-cache",
        "cn: " + cacheName,
        "ds-cfg-cache-level: 7",
        "ds-cfg-java-class: org.opends.server.extensions.FIFOEntryCache",
        "ds-cfg-enabled: true",
        "ds-cfg-max-entries: 10");
    TestCaseUtils.addEntry(cacheEntry);
    try
    {
      assertMonitorIsSearchable();
      assertEntryExists(MONITOR_ROOT.child("cn", cacheName + " Entry Cache"));
    }
    finally
    {
      TestCaseUtils.deleteEntry(cacheEntry);
    }
  }

  /** The name of a connection handler starts with the name of its configuration entry, then its address and port. */
  private static String connectionHandlerName(String configName)
  {
    for (ConnectionHandler<?> handler : DirectoryServer.getConnectionHandlers())
    {
      if (handler.getConnectionHandlerName().startsWith(configName + " "))
      {
        return handler.getConnectionHandlerName();
      }
    }
    return fail("no connection handler named " + configName);
  }

  private static void assertMonitorIsSearchable() throws Exception
  {
    final InternalSearchOperation subtree = getRootConnection().processSearch(newSearchRequest(MONITOR_ROOT, WHOLE_SUBTREE));
    assertThat(subtree.getResultCode()).as(String.valueOf(subtree.getErrorMessage())).isEqualTo(ResultCode.SUCCESS);
    assertEntryExists(DN.valueOf("cn=Version,cn=monitor"));
  }

  /** Asserts that a monitor provider gives the entry: a branch (glue) entry would not do. */
  private static void assertEntryExists(DN dn) throws Exception
  {
    final InternalSearchOperation base = getRootConnection().processSearch(
        newSearchRequest(dn, BASE_OBJECT, "(!(objectClass=ds-mon-branch))"));
    assertThat(base.getResultCode()).as(dn + ": " + base.getErrorMessage()).isEqualTo(ResultCode.SUCCESS);
    assertThat(base.getSearchEntries()).hasSize(1);
  }
}
