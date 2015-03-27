/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.testng.Assert.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.backends.RebuildConfig;
import org.opends.server.backends.RebuildConfig.RebuildMode;
import org.opends.server.backends.VerifyConfig;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TestRebuildJob extends JebTestCase
{
  private String backendID = "rebuildRoot";
  private static String suffix="dc=rebuild,dc=jeb";
  private static  String vBranch="ou=rebuild tests," + suffix;
  private  String numUsersLine="define numusers= #numEntries#";

  private  DN[] baseDNs;
  private Backend<?> backend;

  @DataProvider(name = "systemIndexes")
  public Object[][] systemIndexes() {
    return new Object[][] {
        { "dn2id" },
        { "dn2uri" }
        // { "id2entry" } internal index
    };
  }

  @DataProvider(name = "attributeIndexes")
  public Object[][] attributeIndexes() {
    return new Object[][] {
        { "mail" },
        { "mail.presence" },
        { "mail.substring" },
        { "mail.ordering" },
        { "mail.equality" },
        { "mail.approximate" },
        { "vlv.testvlvindex" }
    };
  }

  @DataProvider(name = "badIndexes")
  public Object[][] badIndexes() {
    return new Object[][] {
        { "nonindex" },
        { "id2subtree" },
        { "id2children" },
        { "mail.nonindex" }
    };
  }

  private static String[] template = new String[] {
      "define suffix="+suffix,
      "define maildomain=example.com",
      "define numusers= #numEntries#",
      "",
      "branch: [suffix]",
      "",
      "branch: " + vBranch,
      "subordinateTemplate: person:[numusers]",
      "",
      "template: person",
      "rdnAttr: uid",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "givenName: ABOVE LIMIT",
      "sn: <last>",
      "cn: {givenName} {sn}",
      "initials: {givenName:1}<random:chars:" +
          "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
      "employeeNumber: <sequential:0>",
      "uid: user.{employeeNumber}",
      "mail: {uid}@[maildomain]",
      "userPassword: password",
      "telephoneNumber: <random:telephone>",
      "homePhone: <random:telephone>",
      "pager: <random:telephone>",
      "mobile: <random:telephone>",
      "street: <random:numeric:5> <file:streets> Street",
      "l: <file:cities>",
      "st: <file:states>",
      "postalCode: <random:numeric:5>",
      "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
      "description: This is the description for {cn}.",
      ""};

  @BeforeClass
  public void setup() throws Exception {
    TestCaseUtils.startServer();
    TestCaseUtils.enableBackend(backendID);
    baseDNs = new DN[] {
        DN.valueOf(suffix)
    };
  }

  @AfterClass
  public void cleanUp() throws Exception {
    TestCaseUtils.clearJEBackend(backendID);
    TestCaseUtils.disableBackend(backendID);
  }

  /**
   * Cleans verify backend and loads some number of entries.
   * @param numEntries number of entries to load into the backend.
   * @throws Exception if the entries are not loaded or created.
   */
  private void cleanAndLoad(int numEntries) throws Exception {
    TestCaseUtils.clearJEBackend(backendID);
    template[2] = numUsersLine.replaceAll("#numEntries#", String.valueOf(numEntries));
    createLoadEntries(template, numEntries);
  }

  /**
   * Runs rebuild against the system indexes.
   *
   * @throws Exception if
   */
  @Test(dataProvider = "attributeIndexes")
  public void testRebuildAttributeIndexes(String index) throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    backend = DirectoryServer.getBackend(backendID);
    backend.rebuildBackend(rebuildConfig, getServerContext());

    if(index.contains(".") && !index.startsWith("vlv."))
    {
      assertEquals(verifyBackend(index.split("\\.")[0]), 0);
    }
    else
    {
      assertEquals(verifyBackend(index), 0);
    }
  }

  /**
   * Try to rebuild the main system index id2entry.
   * (primary index from which all other indexes are derived).
   * It cannot ever be rebuilt. Online mode.
   *
   * @throws InitializationException There is no index configured
   *  for attribute type 'id2entry'.
   */
  @Test(expectedExceptions = InitializationException.class)
  public void testRebuildForbiddenSystemIndexId2EntryOnline() throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("id2entry");
    backend = DirectoryServer.getBackend(backendID);
    backend.rebuildBackend(rebuildConfig, getServerContext());
  }

  /**
   * Try to rebuild the main system index id2entry.
   * (primary index from which all other indexes are derived).
   * It cannot ever be rebuilt. Offline mode.
   *
   * @throws InitializationException There is no index configured
   *  for attribute type 'id2entry'.
   */
  @Test(expectedExceptions = InitializationException.class)
  public void testRebuildForbiddenSystemIndexId2EntryOffline() throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("id2entry");
    backend = DirectoryServer.getBackend(backendID);
    TaskUtils.disableBackend(backendID);

    try {
      backend.rebuildBackend(rebuildConfig, getServerContext());
    } finally {
      TaskUtils.enableBackend(backendID);
    }
  }

  @Test(dataProvider = "badIndexes",
        expectedExceptions = InitializationException.class)
  public void testRebuildBadIndexes(final String index) throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    backend = DirectoryServer.getBackend(backendID);
    backend.rebuildBackend(rebuildConfig, getServerContext());
  }

  @Test(dataProvider = "systemIndexes",
        expectedExceptions = DirectoryException.class)
  public void testRebuildSystemIndexesOnline(String index) throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    backend = DirectoryServer.getBackend(backendID);
    backend.rebuildBackend(rebuildConfig, getServerContext());
  }

  @Test(dataProvider = "systemIndexes")
  public void testRebuildSystemIndexesOffline(String index) throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);

    backend = DirectoryServer.getBackend(backendID);
    TaskUtils.disableBackend(backendID);
    backend.rebuildBackend(rebuildConfig, getServerContext());

    //TODO: Verify dn2uri database as well.
    if (!"dn2uri".equalsIgnoreCase(index))
    {
      assertEquals(verifyBackend(index), 0);
    }

    TaskUtils.enableBackend(backendID);
  }

  @Test
  public void testRebuildAll() throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.setRebuildMode(RebuildMode.ALL);

    rebuildIndexes(rebuildConfig);
  }

  @Test
  public void testRebuildDegraded() throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.setRebuildMode(RebuildMode.DEGRADED);

    rebuildIndexes(rebuildConfig);
  }

  @Test
  public void testRebuildDN2ID() throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("dn2id");

    rebuildIndexes(rebuildConfig);
  }

  private void rebuildIndexes(RebuildConfig rebuildConfig) throws Exception
  {
    backend = DirectoryServer.getBackend(backendID);
    TaskUtils.disableBackend(backendID);
    try
    {
      backend.rebuildBackend(rebuildConfig, getServerContext());
      assertEquals(verifyBackend(null), 0);
    }
    finally
    {
      TaskUtils.enableBackend(backendID);
    }
  }

  private ServerContext getServerContext()
  {
    return DirectoryServer.getInstance().getServerContext();
  }

  @Test
  public void testRebuildRedundentIndexes() throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("cn");
    rebuildConfig.addRebuildIndex("cn.presence");
    rebuildConfig.addRebuildIndex("uid.equality");
    rebuildConfig.addRebuildIndex("uid");

    assertEquals(rebuildConfig.getRebuildList().size(), 3);
    assertTrue(rebuildConfig.getRebuildList().contains("dn2id"));
    assertTrue(rebuildConfig.getRebuildList().contains("cn"));
    assertTrue(rebuildConfig.getRebuildList().contains("uid"));
  }

  @Test
  public void testRebuildMultipleJobs() throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("id2children");
    rebuildConfig.addRebuildIndex("cn");

    RebuildConfig rebuildConfig2 = new RebuildConfig();
    rebuildConfig2.setBaseDN(baseDNs[0]);
    rebuildConfig2.addRebuildIndex("dn2id");

    assertNotNull(rebuildConfig.checkConflicts(rebuildConfig2));
    assertNotNull(rebuildConfig2.checkConflicts(rebuildConfig));

    rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("cn");

    rebuildConfig2 = new RebuildConfig();
    rebuildConfig2.setBaseDN(baseDNs[0]);
    rebuildConfig2.addRebuildIndex("cn.presence");
    rebuildConfig2.addRebuildIndex("dn2id");

    assertNotNull(rebuildConfig.checkConflicts(rebuildConfig2));
    assertNotNull(rebuildConfig2.checkConflicts(rebuildConfig));
  }

  private long verifyBackend(String index) throws Exception
  {
    VerifyConfig verifyConfig = new VerifyConfig();
    verifyConfig.setBaseDN(baseDNs[0]);
    if(index != null)
    {
      verifyConfig.addCleanIndex(index);
    }
    return backend.verifyBackend(verifyConfig);
  }
}
