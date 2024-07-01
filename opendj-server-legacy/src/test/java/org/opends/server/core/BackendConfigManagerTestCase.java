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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.LocalBackendCfg;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.LocalBackend;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.LOCAL;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.PRIVATE;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.PUBLIC;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.TOP_LEVEL;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;
import static org.assertj.core.api.Assertions.*;

/**
 * A set of generic test cases that cover adding, modifying, and removing
 * Directory Server backends.
 */
public class BackendConfigManagerTestCase extends CoreTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** Does not require the server to be started .*/
  @Test
  public void testBackendHierarchy() throws Exception
  {
    ServerContext serverContext = mock(ServerContext.class);
    BackendConfigManager manager = new BackendConfigManager(serverContext);

    // define 3 local backends, including one private, and one non-local backend
    LocalBackend<? extends LocalBackendCfg> backend1 = mockLocalBackend("user1", "o=parent");
    Backend<? extends BackendCfg> backend2 = mockBackend("user2", "ou=child,o=parent");
    LocalBackend<? extends LocalBackendCfg> backend3 = mockLocalBackend("user3", "ou=grandchild,ou=child,o=parent");
    LocalBackend<? extends LocalBackendCfg> backend4 = mockLocalBackend("private", "o=private");

    manager.registerBaseDN(dn("o=parent"), backend1, false);
    manager.registerBaseDN(dn("ou=child,o=parent"), backend2, false);
    manager.registerBaseDN(dn("ou=grandchild,ou=child,o=parent"), backend3, false);
    manager.registerBaseDN(dn("o=private"), backend4, true);

    assertThat(manager.getAllBackends()).containsOnly(backend1, backend2, backend3, backend4);
    assertThat(manager.getLocalBackends()).containsOnly(backend1, backend3, backend4);
    assertThat(manager.getNamingContexts(PUBLIC)).containsOnly(
        dn("o=parent"), dn("ou=child,o=parent"), dn("ou=grandchild,ou=child,o=parent"));
    assertThat(manager.getNamingContexts(PUBLIC, TOP_LEVEL)).containsOnly(dn("o=parent"));
    assertThat(manager.getNamingContexts(PRIVATE)).containsOnly(dn("o=private"));
    assertThat(manager.getNamingContexts(PUBLIC, LOCAL)).containsOnly(
        dn("o=parent"), dn("ou=grandchild,ou=child,o=parent"));
    assertThat(manager.getNamingContexts(PUBLIC, LOCAL, TOP_LEVEL)).containsOnly(dn("o=parent"));
    assertThat(manager.containsLocalNamingContext(dn("o=parent"))).isTrue();
    assertThat(manager.containsLocalNamingContext(dn("ou=child,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=grandchild,ou=child,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("o=private"))).isTrue();

    assertThat(manager.getSubordinateBackends(backend1)).containsOnly(backend2);
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("o=parent"))).isEmpty();

    assertThat(manager.getSubordinateBackends(backend2)).containsOnly(backend3);
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=child,o=parent")))
      .containsOnly(dn("ou=grandchild,ou=child,o=parent"));

    assertThat(manager.getSubordinateBackends(backend3)).isEmpty();
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=grandchild,ou=child,o=parent"))).isEmpty();

    assertThat(manager.getSubordinateBackends(backend4)).isEmpty();
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("o=private"))).isEmpty();

    assertThat(manager.findBackendForEntry(dn("o=anotherparent"))).isNull();
    assertThat(manager.findBackendForEntry(dn("o=parent"))).isEqualTo(backend1);
    assertThat(manager.findNamingContextForEntry(dn("o=parent")).toString()).isEqualTo("o=parent");
    assertThat(manager.findLocalBackendForEntry(dn("o=parent"))).isEqualTo(backend1);

    assertThat(manager.findBackendForEntry(dn("ou=anotherchild,o=parent"))).isEqualTo(backend1);
    assertThat(manager.findNamingContextForEntry(dn("ou=anotherchild,o=parent")).toString()).isEqualTo("o=parent");
    assertThat(manager.findLocalBackendForEntry(dn("ou=anotherchild,o=parent"))).isEqualTo(backend1);

    assertThat(manager.findBackendForEntry(dn("ou=child,o=parent"))).isEqualTo(backend2);
    assertThat(manager.findNamingContextForEntry(dn("ou=child,o=parent")).toString()).isEqualTo("ou=child,o=parent");
    assertThat(manager.findLocalBackendForEntry(dn("ou=child,o=parent"))).isNull();
    assertThat(manager.findBackendForEntry(dn("ou=anothergrandchild,ou=child,o=parent"))).isEqualTo(backend2);
    assertThat(manager.findNamingContextForEntry(dn("ou=anothergrandchild,ou=child,o=parent")).toString())
      .isEqualTo("ou=child,o=parent");

    assertThat(manager.findBackendForEntry(dn("ou=grandchild,ou=child,o=parent"))).isEqualTo(backend3);
    assertThat(manager.findNamingContextForEntry(dn("ou=grandchild,ou=child,o=parent")).toString())
      .isEqualTo("ou=grandchild,ou=child,o=parent");
    assertThat(manager.findLocalBackendForEntry(dn("ou=grandchild,ou=child,o=parent"))).isEqualTo(backend3);
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=grandchild,ou=child,o=parent"))).isEqualTo(backend3);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=grandchild,ou=child,o=parent")).toString())
      .isEqualTo("ou=grandchild,ou=child,o=parent");

    assertThat(manager.findBackendForEntry(dn("o=private"))).isEqualTo(backend4);
    assertThat(manager.findNamingContextForEntry(dn("ou=privchild,o=private")).toString()).isEqualTo("o=private");
    assertThat(manager.findBackendForEntry(dn("ou=privchild,o=private"))).isEqualTo(backend4);

  }

  /** Does not require the server to be started .*/
  @Test
  public void testBackendHierarchyMultiple() throws Exception
  {
    ServerContext serverContext = mock(ServerContext.class);
    BackendConfigManager manager = new BackendConfigManager(serverContext);

    // define 4 local backends and one non-local backend at the bottom of the hierarchy
    LocalBackend<? extends LocalBackendCfg> backend1 = mockLocalBackend("user1", "o=parent", "o=parent2");
    LocalBackend<? extends LocalBackendCfg> backend2 =
        mockLocalBackend("user2", "ou=child21,o=parent", "ou=child22,o=parent");
    LocalBackend<? extends LocalBackendCfg> backend3 =
        mockLocalBackend("user3", "ou=child31,o=parent", "ou=child32,o=parent");
    LocalBackend<? extends LocalBackendCfg> backend4 = mockLocalBackend("user4", "ou=grandchild,ou=child21,o=parent");
    Backend<? extends BackendCfg> backend5 = mockBackend("user5", "ou=grandchild,ou=child31,o=parent");

    manager.registerBaseDN(dn("o=parent"), backend1, false);
    manager.registerBaseDN(dn("o=parent2"), backend1, false);
    manager.registerBaseDN(dn("ou=child21,o=parent"), backend2, false);
    manager.registerBaseDN(dn("ou=child22,o=parent"), backend2, false);
    manager.registerBaseDN(dn("ou=child31,o=parent"), backend3, false);
    manager.registerBaseDN(dn("ou=child32,o=parent"), backend3, false);
    manager.registerBaseDN(dn("ou=grandchild,ou=child21,o=parent"), backend4, false);
    manager.registerBaseDN(dn("ou=grandchild,ou=child31,o=parent"), backend5, false);

    assertThat(manager.getAllBackends()).containsOnly(backend1, backend2, backend3, backend4, backend5);
    assertThat(manager.getLocalBackends()).containsOnly(backend1, backend2, backend3, backend4);
    assertThat(manager.getNamingContexts(PUBLIC)).containsOnly(
        dn("o=parent"), dn("o=parent2"), dn("ou=child21,o=parent"), dn("ou=child22,o=parent"),
        dn("ou=child31,o=parent"), dn("ou=child32,o=parent"), dn("ou=grandchild,ou=child21,o=parent"),
        dn("ou=grandchild,ou=child31,o=parent"));
    assertThat(manager.getNamingContexts(PUBLIC, TOP_LEVEL))
      .containsOnly(dn("o=parent"), dn("o=parent2"));
    assertThat(manager.getNamingContexts(PRIVATE)).isEmpty();
    assertThat(manager.getNamingContexts(PUBLIC, LOCAL)).containsOnly(
        dn("o=parent"), dn("o=parent2"), dn("ou=child21,o=parent"), dn("ou=child22,o=parent"),
        dn("ou=child31,o=parent"), dn("ou=child32,o=parent"), dn("ou=grandchild,ou=child21,o=parent"));
    assertThat(manager.getNamingContexts(PUBLIC, LOCAL, TOP_LEVEL))
      .containsOnly(dn("o=parent"), dn("o=parent2"));
    assertThat(manager.containsLocalNamingContext(dn("o=parent"))).isTrue();
    assertThat(manager.containsLocalNamingContext(dn("o=parent2"))).isTrue();
    assertThat(manager.containsLocalNamingContext(dn("ou=child21,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=child22,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=child31,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=child32,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=grandchild,ou=child21,o=parent"))).isFalse();
    assertThat(manager.containsLocalNamingContext(dn("ou=grandchild,ou=child31,o=parent"))).isFalse();

    assertThat(manager.getSubordinateBackends(backend1)).containsOnly(backend2, backend3);
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("o=parent"))).containsOnly(
        dn("ou=child21,o=parent"), dn("ou=child22,o=parent"),
        dn("ou=child31,o=parent"), dn("ou=child32,o=parent"));
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("o=parent"))).containsOnly(
        dn("ou=child21,o=parent"), dn("ou=child22,o=parent"),
        dn("ou=child31,o=parent"), dn("ou=child32,o=parent"));

    assertThat(manager.getSubordinateBackends(backend2)).containsOnly(backend4);
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=child21,o=parent")))
      .containsExactly(dn("ou=grandchild,ou=child21,o=parent"));
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=child22,o=parent"))).isEmpty();

    assertThat(manager.getSubordinateBackends(backend3)).containsExactly(backend5);
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=child31,o=parent"))).isEmpty();
    assertThat(manager.findSubordinateLocalNamingContextsForEntry(dn("ou=child32,o=parent"))).isEmpty();

    assertThat(manager.getSubordinateBackends(backend4)).isEmpty();
    assertThat(manager.getSubordinateBackends(backend5)).isEmpty();

    assertThat(manager.findBackendForEntry(dn("o=parent"))).isEqualTo(backend1);
    assertThat(manager.findNamingContextForEntry(dn("o=parent")).toString()).isEqualTo("o=parent");
    assertThat(manager.findBackendForEntry(dn("o=parent2"))).isEqualTo(backend1);
    assertThat(manager.findNamingContextForEntry(dn("o=parent2")).toString()).isEqualTo("o=parent2");

    assertThat(manager.findBackendForEntry(dn("ou=child21,o=parent"))).isEqualTo(backend2);
    assertThat(manager.findNamingContextForEntry(dn("ou=child21,o=parent")).toString())
      .isEqualTo("ou=child21,o=parent");
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=child21,o=parent"))).isEqualTo(backend2);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=child21,o=parent")).toString())
      .isEqualTo("ou=child21,o=parent");
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=child22,o=parent"))).isEqualTo(backend2);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=child22,o=parent")).toString())
      .isEqualTo("ou=child22,o=parent");
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=child31,o=parent"))).isEqualTo(backend3);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=child31,o=parent")).toString())
      .isEqualTo("ou=child31,o=parent");
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=child32,o=parent"))).isEqualTo(backend3);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=child32,o=parent")).toString())
      .isEqualTo("ou=child32,o=parent");

    assertThat(manager.findBackendForEntry(dn("ou=another,ou=grandchild,ou=child21,o=parent"))).isEqualTo(backend4);
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=grandchild,ou=child21,o=parent")).toString())
      .isEqualTo("ou=grandchild,ou=child21,o=parent");
    assertThat(manager.findLocalBackendForEntry(dn("ou=another,ou=grandchild,ou=child21,o=parent")))
      .isEqualTo(backend4);
    assertThat(manager.findBackendForEntry(dn("ou=another,ou=grandchild,ou=child31,o=parent")))
      .isEqualTo(backend5);
    assertThat(manager.findLocalBackendForEntry(dn("ou=another,ou=grandchild,ou=child31,o=parent")))
      .isNull();
    assertThat(manager.findNamingContextForEntry(dn("ou=another,ou=grandchild,ou=child31,o=parent")).toString())
      .isEqualTo("ou=grandchild,ou=child31,o=parent");
  }

  private DN dn(String dn)
  {
    return DN.valueOf(dn);
  }

  private Set<DN> toDNs(String... dns)
  {
    HashSet<DN> set = new HashSet<DN>();
    for (String dn : dns)
    {
      set.add(dn(dn));
    }
    return set;
  }

  private Backend<? extends BackendCfg> mockBackend(final String backendId, final String... baseDNs)
  {
    return new BackendMock(backendId, toDNs(baseDNs));
  }

  private LocalBackend<? extends LocalBackendCfg> mockLocalBackend(String backendId, String... baseDNs)
  {
    return new LocalBackendMock(backendId, toDNs(baseDNs));
  }

  /**
   * Tests that the server will reject an attempt to register a base DN that is
   * already defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRegisterBaseThatAlreadyExists() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    DN baseDN = DN.valueOf("o=test");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    AddOperation addOperation = getRootConnection().processAdd(backendEntry);
    assertNotEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the server will reject an attempt to deregister a base DN that
   * is not defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDeregisterNonExistentBaseDN() throws Exception
  {
    getBackendConfigManager()
      .deregisterBaseDN(DN.valueOf("o=unregistered"));
  }



  /**
   * Tests that the server will reject an attempt to register a base DN using a
   * backend with a backend ID that is already defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRegisterBackendIDThatAlreadyExists() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    DN baseDN = DN.valueOf("o=test");
    String backendID = "test";
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    AddOperation addOperation = getRootConnection().processAdd(backendEntry);
    assertNotEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the ability of the server to create and remove a backend that is
   * never enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndRemoveDisabledBackend() throws Exception
  {
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    processAdd(backendEntry);
    assertNull(getBackendConfigManager().getLocalBackendById(backendID));
    assertNull(getBackendConfigManager().getLocalBackendWithBaseDN(baseDN));

    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the ability of the server to create and remove a backend that is
   * enabled.  It will also test the ability of that backend to hold entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndRemoveEnabledBackend() throws Exception
  {
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, true, baseDN);

    processAdd(backendEntry);

    LocalBackend<?> backend = getBackendConfigManager().getLocalBackendById(backendID);
    assertBackend(baseDN, backend);
    createEntry(baseDN, backend);

    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackendById(backendID));
  }



  /**
   * Tests the ability of the server to create a backend that is disabled and
   * then enable it through a configuration change, and then subsequently
   * disable it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnableAndDisableBackend() throws Exception
  {
    // Create the backend and make it disabled.
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    processAdd(backendEntry);
    assertNull(getLocalBackend(backendID));
    assertFalse(getServerContext().getBackendConfigManager().containsLocalNamingContext(baseDN));

    // Modify the backend to enable it.
    enableBackend(backendEntry, true);

    LocalBackend<?> backend = getLocalBackend(backendID);
    assertBackend(baseDN, backend);
    createEntry(baseDN, backend);

    // Modify the backend to disable it.
    enableBackend(backendEntry, false);
    assertNull(getLocalBackend(backendID));
    assertFalse(DirectoryServer.entryExists(baseDN));
    assertFalse(getServerContext().getBackendConfigManager().containsLocalNamingContext(baseDN));


    // Delete the disabled backend.
    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private LocalBackend<?> getLocalBackend(String backendID)
  {
    return getServerContext().getBackendConfigManager().getLocalBackendById(backendID);
  }



  /**
   * Tests the ability of the Directory Server to work properly when adding
   * nested backends in which the parent is added first and the child second.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNestedBackendParentFirst() throws Exception
  {
    // Create the parent backend and the corresponding base entry.
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true,
                                                  parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = getBackendConfigManager().getLocalBackendById(parentBackendID);
    assertBackend(parentBaseDN, parentBackend);
    createEntry(parentBaseDN, parentBackend);


    // Create the child backend and the corresponding base entry.
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true,
                                                 childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = getBackendConfigManager().getLocalBackendById(childBackendID);
    assertNotNull(childBackend);
    assertEquals(childBackend, getBackendConfigManager().getLocalBackendWithBaseDN(childBaseDN));
    assertThat(getBackendConfigManager().getSubordinateBackends(parentBackend)).containsExactly(childBackend);
    assertFalse(childBackend.entryExists(childBaseDN));
    assertFalse(getBackendConfigManager().containsLocalNamingContext(childBaseDN));

    createEntry(childBaseDN, childBackend);


    InternalClientConnection conn = getRootConnection();
    // Make sure that both entries exist.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    InternalSearchOperation internalSearch = conn.processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), 2);



    // Make sure that we can't remove the parent backend with the child still in place.
    DeleteOperation deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(getBackendConfigManager().getLocalBackendById(parentBackendID));

    // Delete the child and then delete the parent.
    deleteOperation = conn.processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackendById(childBackendID));
    assertThat(getBackendConfigManager().getSubordinateBackends(parentBackend)).isEmpty();

    deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackendById(parentBackendID));
  }

  private BackendConfigManager getBackendConfigManager()
  {
    return TestCaseUtils.getServerContext().getBackendConfigManager();
  }

  /**
   * Tests the ability of the Directory Server to work properly when adding
   * nested backends in which the child is added first and the parent second.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNestedBackendChildFirst() throws Exception
  {
    BackendConfigManager manager = getBackendConfigManager();
    // Create the child backend and the corresponding base entry (at the time
    // of the creation, it will be a naming context).
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true, childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = manager.getLocalBackendById(childBackendID);
    assertBackend(childBaseDN, childBackend);
    createEntry(childBaseDN, childBackend);
    assertTrue(manager.containsLocalNamingContext(childBaseDN));


    // Create the parent backend and the corresponding entry (and verify that
    // its DN is now a naming context and the child's is not).
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true, parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = manager.getLocalBackendById(parentBackendID);
    assertNotNull(parentBackend);
    assertEquals(parentBackend, manager.getLocalBackendWithBaseDN(parentBaseDN));
    assertThat(manager.getSubordinateBackends(parentBackend)).containsExactly(childBackend);

    createEntry(parentBaseDN, parentBackend);
    assertTrue(manager.containsLocalNamingContext(parentBaseDN));
    assertFalse(manager.containsLocalNamingContext(childBaseDN));


    // Verify that we can see both entries with a subtree search.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), 2);


    // Delete the backends from the server.
    DeleteOperation deleteOperation = getRootConnection().processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(manager.getLocalBackendById(childBackendID));
    assertThat(manager.getSubordinateBackends(parentBackend)).isEmpty();

    deleteOperation = getRootConnection().processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(manager.getLocalBackendById(parentBackendID));
  }

  private void assertBackend(DN baseDN, LocalBackend<?> backend) throws DirectoryException
  {
    assertNotNull(backend);
    assertEquals(backend, getBackendConfigManager().getLocalBackendWithBaseDN(baseDN));
    assertFalse(backend.entryExists(baseDN));
    assertThat(getBackendConfigManager().getSubordinateBackends(backend)).isEmpty();
    assertTrue(getBackendConfigManager().containsLocalNamingContext(baseDN));
  }

  /**
   * Tests the ability of the Directory Server to work properly when inserting
   * an intermediate backend between a parent backend and an existing nested
   * backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testInsertIntermediateBackend() throws Exception
  {
    BackendConfigManager manager = getBackendConfigManager();
    // Add the parent backend to the server and its corresponding base entry.
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true, parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = manager.getLocalBackendById(parentBackendID);
    assertBackend(parentBaseDN, parentBackend);
    createEntry(parentBaseDN, parentBackend);
    assertTrue(getServerContext().getBackendConfigManager().containsLocalNamingContext(parentBaseDN));

    // Add the grandchild backend to the server.
    DN grandchildBaseDN = DN.valueOf("ou=grandchild,ou=child,o=parent");
    String grandchildBackendID = createBackendID(grandchildBaseDN);
    Entry grandchildBackendEntry = createBackendEntry(grandchildBackendID, true, grandchildBaseDN);
    processAdd(grandchildBackendEntry);

    LocalBackend<?> grandchildBackend = manager.getLocalBackendById(grandchildBackendID);
    assertNotNull(grandchildBackend);
    assertEquals(grandchildBackend, manager.getLocalBackendWithBaseDN(grandchildBaseDN));
    assertThat(manager.getSubordinateBackends(parentBackend)).containsExactly(grandchildBackend);
    assertFalse(grandchildBackend.entryExists(grandchildBaseDN));

    // Verify that we can't create the grandchild base entry because its parent doesn't exist.
    Entry e = StaticUtils.createEntry(grandchildBaseDN);
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertFalse(grandchildBackend.entryExists(grandchildBaseDN));

    // Add the child backend to the server and create its base entry.
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true, childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = manager.getLocalBackendById(childBackendID);
    assertThat(childBackend).isNotNull();
    assertThat(manager.getLocalBackendWithBaseDN(childBaseDN)).isEqualTo(childBackend);
    assertThat(manager.getSubordinateBackends(parentBackend)).containsExactly(childBackend);
    assertThat(manager.getSubordinateBackends(childBackend)).containsExactly(grandchildBackend);
    createEntry(childBaseDN, childBackend);

    // Now we can create the grandchild base entry.
    createEntry(grandchildBaseDN, grandchildBackend);

    InternalClientConnection conn = getRootConnection();
    // Verify that a subtree search can see all three entries.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    assertSearchResultsSize(request, 3);

    // Disable the intermediate (child) backend.  This should be allowed.
    enableBackend(childBackendEntry, false);

    assertSearchResultsSize(request, 2);

    // Re-enable the intermediate backend.
    enableBackend(childBackendEntry, true);

    // Update our reference to the child backend since the old one is no longer
    // valid, and make sure that it got re-inserted back into the same place in
    // the hierarchy.
    childBackend = manager.getLocalBackendById(childBackendID);
    assertNotNull(childBackend);
    assertEquals(childBackend, manager.getLocalBackendWithBaseDN(childBaseDN));
    assertThat(manager.getSubordinateBackends(parentBackend)).containsExactly(childBackend);
    assertThat(manager.getSubordinateBackends(childBackend)).containsExactly(grandchildBackend);

    // Since the memory backend that we're using for this test doesn't retain
    // entries across stops and restarts, a subtree search below the parent
    // should still only return two entries, which means that it's going through
    // the entire chain of backends.
    assertSearchResultsSize(request, 2);

    // Add the child entry back into the server to get things back to the way
    // they were before we disabled the backend.
    createEntry(childBaseDN, childBackend);

    // We should again be able to see all three entries when performing a search
    assertSearchResultsSize(request, 3);

    // Get rid of the entries in the proper order.
    DeleteOperation deleteOperation = conn.processDelete(grandchildBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(manager.getLocalBackendById(grandchildBackendID));
    assertThat(manager.getSubordinateBackends(parentBackend)).containsExactly(childBackend);
    assertThat(manager.getSubordinateBackends(childBackend)).isEmpty();

    deleteOperation = conn.processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(manager.getLocalBackendById(childBackendID));
    assertThat(manager.getSubordinateBackends(parentBackend)).isEmpty();

    deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(manager.getLocalBackendById(parentBackendID));
  }

  private void enableBackend(Entry entry, boolean enabled)
  {
    ModifyRequest modifyRequest = newModifyRequest(entry.getName())
        .addModification(REPLACE, "ds-cfg-enabled", Boolean.toString(enabled));
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private void assertSearchResultsSize(final SearchRequest request, int expected)
  {
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), expected);
  }

  private void createEntry(DN baseDN, LocalBackend<?> backend) throws DirectoryException
  {
    Entry e = StaticUtils.createEntry(baseDN);
    processAdd(e);
    assertTrue(backend.entryExists(baseDN));
  }

  private void processAdd(Entry e)
  {
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Creates an entry that may be used to add a new backend to the server.  It
   * will be an instance of the memory backend.
   *
   * @param  backendID  The backend ID to use for the backend.
   * @param  enabled    Indicates whether the backend should be enabled.
   * @param  baseDNs    The set of base DNs to use for the new backend.
   *
   * @return  An entry that may be used to add a new backend to the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private Entry createBackendEntry(String backendID, boolean enabled,
                                   DN... baseDNs)
          throws Exception
  {
    assertNotNull(baseDNs);
    assertFalse(baseDNs.length == 0);

    ArrayList<String> lines = new ArrayList<>();
    lines.add("dn: ds-cfg-backend-id=" + backendID + ",cn=Backends,cn=config");
    lines.add("objectClass: top");
    lines.add("objectClass: ds-cfg-backend");
    lines.add("objectClass: ds-cfg-local-backend");
    lines.add("objectClass: ds-cfg-memory-backend");
    lines.add("ds-cfg-backend-id: " + backendID);
    lines.add("ds-cfg-java-class: org.opends.server.backends.MemoryBackend");
    lines.add("ds-cfg-enabled: " + enabled);
    lines.add("ds-cfg-writability-mode: enabled");

    for (DN dn : baseDNs)
    {
      lines.add("ds-cfg-base-dn: " + dn);
    }

    String[] lineArray = new String[lines.size()];
    lines.toArray(lineArray);
    return TestCaseUtils.makeEntry(lineArray);
  }



  /**
   * Constructs a backend ID to use for a backend with the provided set of base
   * DNs.
   *
   * @param  baseDNs  The set of base DNs to use when creating the backend ID.
   *
   * @return  The constructed backend ID based on the given base DNs.
   */
  private String createBackendID(DN... baseDNs)
  {
    StringBuilder buffer = new StringBuilder();

    for (DN dn : baseDNs)
    {
      if (buffer.length() > 0)
      {
        buffer.append("___");
      }

      String ndn = dn.toNormalizedUrlSafeString();
      for (int i=0; i < ndn.length(); i++)
      {
        char c = ndn.charAt(i);
        if (Character.isLetterOrDigit(c))
        {
          buffer.append(c);
        }
        else
        {
          buffer.append('_');
        }
      }
    }

    return buffer.toString();
  }

  /** Mockito can not be used to provide a mock with a backend id because getBackendID() is final. */
  static class BackendMock extends Backend<BackendCfg>
  {
    private final Set<DN> baseDNs;

    BackendMock(String backendId, Set<DN> baseDNs)
    {
      this.baseDNs = baseDNs;
      setBackendID(backendId);
    }

    @Override
    public void configureBackend(BackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      // do nothing
    }

    @Override
    public void openBackend() throws ConfigException, InitializationException
    {
      // do nothing
    }

    @Override
    public void finalizeBackend()
    {
      // do nothing
    }

    @Override
    public Set<DN> getBaseDNs()
    {
      return baseDNs;
    }

    @Override
    public boolean isDefaultRoute()
    {
      return false;
    }

    @Override
    public Set<String> getSupportedControls()
    {
      return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedFeatures()
    {
      return Collections.emptySet();
    }

    @Override
    public boolean isPrivateBackend()
    {
      return false;
    }

    @Override
    public String toString()
    {
      return "BackendMock [backendId=" + getBackendID() + ", baseDNs=" + baseDNs + "]";
    }
  }

  /** Mockito can not be used to provide a mock with a backend id because getBackendID() is final. */
  static class LocalBackendMock extends LocalBackend<LocalBackendCfg>
  {
    private final Set<DN> baseDNs;

    LocalBackendMock(String backendId, Set<DN> baseDNs)
    {
      this.baseDNs = baseDNs;
      setBackendID(backendId);
    }

    @Override
    public void openBackend() throws ConfigException, InitializationException
    {
      // do nothing
    }

    @Override
    public boolean isIndexed(AttributeType attributeType, IndexType indexType)
    {
      return false;
    }

    @Override
    public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
    {
      return null;
    }

    @Override
    public long getNumberOfChildren(DN parentDN) throws DirectoryException
    {
      return 0;
    }

    @Override
    public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
    {
      return 0;
    }

    @Override
    public Entry getEntry(DN entryDN) throws DirectoryException
    {
      return null;
    }

    @Override
    public void addEntry(Entry entry, AddOperation addOperation) throws DirectoryException, CanceledOperationException
    {
      // do nothing
    }

    @Override
    public void deleteEntry(DN entryDN, DeleteOperation deleteOperation) throws DirectoryException,
        CanceledOperationException
    {
      // do nothing
    }

    @Override
    public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation) throws DirectoryException,
        CanceledOperationException
    {
      // do nothing
    }

    @Override
    public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation) throws DirectoryException,
        CanceledOperationException
    {
      // do nothing
    }

    @Override
    public void search(SearchOperation searchOperation) throws DirectoryException, CanceledOperationException
    {
      // do nothing
    }

    @Override
    public boolean supports(org.opends.server.api.LocalBackend.BackendOperation backendOperation)
    {
      return false;
    }

    @Override
    public void exportLDIF(LDIFExportConfig exportConfig) throws DirectoryException
    {
      // do nothing
    }

    @Override
    public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
        throws DirectoryException
    {
      return null;
    }

    @Override
    public void createBackup(BackupConfig backupConfig) throws DirectoryException
    {
      // do nothing
    }

    @Override
    public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
    {
      // do nothing
    }

    @Override
    public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
    {
      // do nothing
    }

    @Override
    public long getEntryCount()
    {
      return 0;
    }

    @Override
    public void configureBackend(LocalBackendCfg cfg, ServerContext serverContext) throws ConfigException
    {
      // do nothing
    }

    @Override
    public Set<DN> getBaseDNs()
    {
      return baseDNs;
    }

    @Override
    public Set<String> getSupportedControls()
    {
      return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedFeatures()
    {
      return Collections.emptySet();
    }

    @Override
    public String toString()
    {
      return "LocalBackendMock [backendId=" + getBackendID() + ", baseDNs=" + baseDNs + "]";
    }
  }
}

