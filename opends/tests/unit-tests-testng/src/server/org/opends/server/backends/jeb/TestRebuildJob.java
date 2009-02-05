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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.testng.annotations.DataProvider;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.tasks.TaskUtils;
import static org.opends.server.util.ServerConstants.OC_TOP;
import static org.opends.server.util.ServerConstants.OC_EXTENSIBLE_OBJECT;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class TestRebuildJob extends JebTestCase
{
  private  String beID="rebuildRoot";
  private static String suffix="dc=rebuild,dc=jeb";
  private static  String vBranch="ou=rebuild tests," + suffix;
  private  String numUsersLine="define numusers= #numEntries#";
  //Attribute type in stat entry containing error count
  private  String errorCount="verify-error-count";

  private  DN[] baseDNs;
  private BackendImpl be;

  @DataProvider(name = "systemIndexes")
  public Object[][] systemIndexes() {
    return new Object[][] {
        { "id2subtree" },
        { "id2children" },
        { "dn2id" },
        { "dn2uri" }
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
        { "id2entry" },
        { "nonindex" },
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
    baseDNs = new DN[] {
        DN.decode(suffix)
    };
  }

  @AfterClass
  public void cleanUp() throws Exception {
    TestCaseUtils.clearJEBackend(false, beID, suffix);
  }

  /**
   * Cleans verify backend and loads some number of entries.
   * @param numEntries number of entries to load into the backend.
   * @throws Exception if the entries are not loaded or created.
   */
  private void cleanAndLoad(int numEntries) throws Exception {
    TestCaseUtils.clearJEBackend(false, beID, suffix);
    template[2]=numUsersLine;
    template[2]=
        template[2].replaceAll("#numEntries#", String.valueOf(numEntries));
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
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    be.rebuildBackend(rebuildConfig);

    if(index.contains(".") && !index.startsWith("vlv."))
    {
      assertEquals(verifyBackend(index.split("\\.")[0]), 0);
    }
    else
    {
      assertEquals(verifyBackend(index), 0);
    }
  }

  @Test(dataProvider = "badIndexes",
        expectedExceptions = DirectoryException.class)
  public void testRebuildBadIndexes(String index) throws Exception
  {
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    be.rebuildBackend(rebuildConfig);
  }

  @Test(dataProvider = "systemIndexes",
        expectedExceptions = DirectoryException.class)
  public void testRebuildSystemIndexesOnline(String index) throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    be=(BackendImpl) DirectoryServer.getBackend(beID);
    be.rebuildBackend(rebuildConfig);
  }

  @Test(dataProvider = "systemIndexes")
  public void testRebuildSystemIndexesOffline(String index) throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex(index);
    be=(BackendImpl) DirectoryServer.getBackend(beID);

    TaskUtils.disableBackend(beID);

    be.rebuildBackend(rebuildConfig);

    //TODO: Verify dn2uri database as well.
    if(!index.equalsIgnoreCase("dn2uri"))
    {
      assertEquals(verifyBackend(index), 0);
    }

    TaskUtils.enableBackend(beID);
  }

  @Test
  public void testRebuildDependentIndexes() throws Exception
  {
    cleanAndLoad(10);
    RebuildConfig rebuildConfig = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("id2children");

    be=(BackendImpl) DirectoryServer.getBackend(beID);

    TaskUtils.disableBackend(beID);

    be.rebuildBackend(rebuildConfig);

    assertEquals(verifyBackend(null), 0);

    TaskUtils.enableBackend(beID);
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
    RebuildConfig rebuildConfig2 = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig2.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("id2children");
    rebuildConfig2.addRebuildIndex("dn2id");
    rebuildConfig.addRebuildIndex("cn");

    assertNotNull(rebuildConfig.checkConflicts(rebuildConfig2));
    assertNotNull(rebuildConfig2.checkConflicts(rebuildConfig));

    rebuildConfig = new RebuildConfig();
    rebuildConfig2 = new RebuildConfig();
    rebuildConfig.setBaseDN(baseDNs[0]);
    rebuildConfig2.setBaseDN(baseDNs[0]);
    rebuildConfig.addRebuildIndex("cn");
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
    Entry statEntry=bldStatEntry("");
    be.verifyBackend(verifyConfig, statEntry);

    return getStatEntryCount(statEntry, errorCount);
  }

  /**
   * Builds an entry suitable for using in the verify job to gather statistics about
   * the verify.
   * @param dn to put into the entry.
   * @return a suitable entry.
   * @throws DirectoryException if the cannot be created.
   */
  private Entry bldStatEntry(String dn) throws DirectoryException {
    DN entryDN = DN.decode(dn);
    HashMap<ObjectClass, String> ocs = new HashMap<ObjectClass, String>(2);
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP);
    if (topOC == null) {
      topOC = DirectoryServer.getDefaultObjectClass(OC_TOP);
    }
    ocs.put(topOC, OC_TOP);
    ObjectClass extensibleObjectOC = DirectoryServer
        .getObjectClass(OC_EXTENSIBLE_OBJECT);
    if (extensibleObjectOC == null) {
      extensibleObjectOC = DirectoryServer
          .getDefaultObjectClass(OC_EXTENSIBLE_OBJECT);
    }
    ocs.put(extensibleObjectOC, OC_EXTENSIBLE_OBJECT);
    return new Entry(entryDN, ocs,
                     new LinkedHashMap<AttributeType, List<Attribute>>(0),
                     new HashMap<AttributeType, List<Attribute>>(0));
  }
  /**
   * Gets information from the stat entry and returns that value as a Long.
   * @param e entry to search.
   * @param type attribute type
   * @return Long
   * @throws NumberFormatException if the attribute value cannot be parsed.
   */
  private long getStatEntryCount(Entry e, String type)
      throws NumberFormatException {
    AttributeType attrType =
        DirectoryServer.getAttributeType(type);
    if (attrType == null)
      attrType = DirectoryServer.getDefaultAttributeType(type);
    List<Attribute> attrList = e.getAttribute(attrType, null);
    AttributeValue v = attrList.get(0).iterator().next();
    long retVal = Long.parseLong(v.getValue().toString());
    return (retVal);
  }
}
