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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.fail;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.Task;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.service.FakeReplicationDomain;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Various tests around fractional replication */
@SuppressWarnings("javadoc")
public class FractionalReplicationTest extends ReplicationTestCase {

  /** The RS */
  private ReplicationServer replicationServer;
  /** RS port */
  private int replServerPort = -1;
  /** Represents the real domain to test (replays and filters) */
  private Entry fractionalDomainCfgEntry;
  /** The domain used to send updates to the real domain */
  private FakeReplicationDomain replicationDomain;

  /** Ids of servers */
  private static final int DS1_ID = 1; // fractional domain
  private static final int DS2_ID = 2; // fake domain
  private static final int RS_ID = 91; // replication server

  private final String testName = getClass().getSimpleName();

  /** Fractional mode */
  private static final int EXCLUDE_FRAC_MODE = 0;
  private static final int INCLUDE_FRAC_MODE = 1;

  private CSNGenerator gen;

  /** The tracer object for the debug logger */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Number of seconds before generating an error if some conditions not met */
  private static final int TIMEOUT = 10000;

  /** Uuid of the manipulated entry */
  private static final String ENTRY_UUID =
    "11111111-1111-1111-1111-111111111111";
  private static final String ENTRY_UUID2 =
    "22222222-2222-2222-2222-222222222222";
  private static final String ENTRY_UUID3 =
    "33333333-3333-3333-3333-333333333333";
  /** Dn of the manipulated entry */
  private static final String ENTRY_DN = "uid=1," + TEST_ROOT_DN_STRING;

  /**
   * Optional attribute not part of concerned attributes of the fractional
   * configuration during tests. It should not be impacted by fractional
   * mechanism
   */
  private static final String OPTIONAL_ATTR = "description";

  /**
   * Optional attribute used as synchronization attribute to know when the
   * modify operation has been processed (used as add new attribute in the
   * modify operation) It may or may not be part of the filtered attributes,
   * depending on the fractional test mode : exclusive or inclusive
   */
  private static final String SYNCHRO_OPTIONAL_ATTR = "seeAlso";

  /** Second test backend */
  private static final String TEST2_ROOT_DN_STRING = "dc=example,dc=com";
  private static final String TEST2_ORG_DN_STRING = "o=test2," + TEST2_ROOT_DN_STRING;
  private static final String ENTRY_DN2 = "uid=1," + TEST2_ORG_DN_STRING;

  private void debugInfo(String s) {
    logger.error(LocalizableMessage.raw(s));
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST **" + s);
    }
  }

  /**
   * Before starting the tests configure some stuff
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    replServerPort = TestCaseUtils.findFreePort();
  }

  /**
   * Returns a bunch of single values for fractional-exclude configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testExcludePrecommitProvider")
  private Object[][] testExcludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional-exclude configuration
   * attribute
   */
  @DataProvider(name = "testExcludeNightlyProvider")
  private Object[][] testExcludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"INETORGPERSON", "DISPLAYNAME"}},
      { 2, new String[] {"inetOrgPerson", "2.16.840.1.113730.3.1.241"}},
      { 3, new String[] {"2.16.840.1.113730.3.2.2", "displayName"}},
      { 4, new String[] {"2.16.840.1.113730.3.2.2", "2.16.840.1.113730.3.1.241"}},
      { 5, new String[] {"inetOrgPerson", "displayName", "carLicense"}},
      { 6, new String[] {"organizationalPerson", "title", "postalCode"}},
      { 7, new String[] {"2.5.6.7", "title", "postalCode"}},
      { 8, new String[] {"2.5.6.7", "TITLE", "2.5.4.17"}},
      { 9, new String[] {"2.5.6.7", "2.5.4.12", "2.5.4.17"}},
      { 10, new String[] {"*", "roomNumber"}},
      { 11, new String[] {"*", "0.9.2342.19200300.100.1.6"}},
      { 12, new String[] {"*", "postOfficeBox", "0.9.2342.19200300.100.1.6"}},
      { 13, new String[] {"*", "2.5.4.18", "0.9.2342.19200300.100.1.6"}}
    };
  }

  /**
   * Calls the testExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testExcludePrecommitProvider")
  public void testExcludePrecommit(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testExclude(testProviderLineId, fractionalConf);
  }

  /**
   * Calls the testExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testExcludeNightlyProvider", groups = "slow")
  public void testExcludeNightly(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testExclude(testProviderLineId, fractionalConf);
  }

  /**
   * Performs Add and Modify operations including attributes that are excluded
   * with the passed fractional configuration and checks that these attributes
   * are not part of the concerned entry.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testExclude(int testProviderLineId,
    String... fractionalConf) throws Exception
  {

    String testcase = "testExclude" + testProviderLineId;

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, EXCLUDE_FRAC_MODE, fractionalConf);

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // perform add operation
      sendAddMsg(true, fractionalConf);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = getEntry(DN.valueOf(ENTRY_DN), TIMEOUT, true);
      checkEntryFilteredAfterAdd(newEntry, EXCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(true, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = waitTillEntryHasSynchroAttribute(ENTRY_DN);
      checkEntryFilteredAfterModify(entry, EXCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Returns a bunch of single values for fractional-include configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testIncludePrecommitProvider")
  private Object[][] testIncludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional-include configuration
   * attribute
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testIncludeNightlyProvider")
  private Object[][] testIncludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, new String[] {"INETORGPERSON", "DISPLAYNAME"}},
      { 2, new String[] {"inetOrgPerson", "2.16.840.1.113730.3.1.241"}},
      { 3, new String[] {"2.16.840.1.113730.3.2.2", "displayName"}},
      { 4, new String[] {"2.16.840.1.113730.3.2.2", "2.16.840.1.113730.3.1.241"}},
      { 5, new String[] {"inetOrgPerson", "displayName", "carLicense"}},
      { 6, new String[] {"organizationalPerson", "title", "postalCode"}},
      { 7, new String[] {"2.5.6.7", "title", "postalCode"}},
      { 8, new String[] {"2.5.6.7", "TITLE", "2.5.4.17"}},
      { 9, new String[] {"2.5.6.7", "2.5.4.12", "2.5.4.17"}},
      { 10, new String[] {"*", "roomNumber"}},
      { 11, new String[] {"*", "0.9.2342.19200300.100.1.6"}},
      { 12, new String[] {"*", "postOfficeBox", "0.9.2342.19200300.100.1.6"}},
      { 13, new String[] {"*", "2.5.4.18", "0.9.2342.19200300.100.1.6"}}
    };
  }

  /**
   * Calls the testInclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testIncludePrecommitProvider")
  public void testIncludePrecommit(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testInclude(testProviderLineId, fractionalConf);
  }

  /**
   * Calls the testInclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testIncludeNightlyProvider", groups = "slow")
  public void testIncludeNightly(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    testInclude(testProviderLineId, fractionalConf);
  }

  /**
   * Performs Add and Modify operations including attributes that are excluded
   * with the passed fractional configuration and checks that these attributes
   * are not part of the concerned entry.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInclude(int testProviderLineId,
    String... fractionalConf) throws Exception
  {
    String testcase = "testInclude" + testProviderLineId;

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, INCLUDE_FRAC_MODE, fractionalConf);

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // perform add operation
      sendAddMsg(true, fractionalConf);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = getEntry(DN.valueOf(ENTRY_DN), TIMEOUT, true);
      checkEntryFilteredAfterAdd(newEntry, INCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(true, fractionalConf);

      Entry entry = waitTillEntryHasSynchroAttribute(ENTRY_DN);
      checkEntryFilteredAfterModify(entry, INCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Creates connects (to the RS) and starts the fake replication domain
   * Use the passed generation id.
   */
  private void createFakeReplicationDomain(boolean firstBackend,
      long generationId) throws Exception
  {
    SortedSet<String> replicationServers = newTreeSet("localhost:" + replServerPort);

    DN baseDN = DN.valueOf(firstBackend ? TEST_ROOT_DN_STRING : TEST2_ROOT_DN_STRING);
    replicationDomain = new FakeReplicationDomain(baseDN, DS2_ID, replicationServers, 1000, generationId);

    // Test connection
    assertTrue(replicationDomain.isConnected());
    // Check connected server port
    String serverStr = replicationDomain.getReplicationServer();
    assertEquals(HostPort.valueOf(serverStr).getPort(), replServerPort);
  }

  private void initTest() throws Exception
  {
    replicationDomain = null;
    fractionalDomainCfgEntry = null;
    replicationServer = null;

    TestCaseUtils.initializeTestBackend(false);

    gen = new CSNGenerator(DS2_ID, 0L);
  }

  private void endTest() throws Exception
  {
    if (replicationDomain != null)
    {
      replicationDomain.disableService();
      replicationDomain = null;
    }

    removeDomain(fractionalDomainCfgEntry);
    fractionalDomainCfgEntry = null;

    remove(replicationServer);
    replicationServer = null;
  }

  /**
   * Creates a fractional domain with the passed configuration.
   * Before that, initializes the backend with the root entry and if requested
   * with the correct fractional configuration in it
   */
  private void createFractionalDomain(boolean initializeDomain,
      int fractionalMode, String... fractionalConf) throws Exception
  {
    {
      String fractModeAttrName = null;
      String opFractModeAttrName = null;
      boolean addSynchroAttribute = false;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          fractModeAttrName = "ds-cfg-fractional-exclude";
          opFractModeAttrName = "ds-sync-fractional-exclude";
          break;
        case INCLUDE_FRAC_MODE:
          fractModeAttrName = "ds-cfg-fractional-include";
          opFractModeAttrName = "ds-sync-fractional-include";
          // For inclusive mode, we use an attribute that is added in the modify
          // operation to know when the modify operation has been played. The added
          // attribute can only be part of the include config to be taken into account
          addSynchroAttribute = true;
          break;
        default:
          fail("Unexpected fractional mode.");
      }

      /**
       * Create a root entry with potentially with fractional configuration before domain creation
       */

      // Create base entry with correct fractional config
      String topEntryLdif = null;

      if (initializeDomain)
      {
        // Add first backend top entry
        topEntryLdif = "dn: " + TEST_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: " + TEST_BACKEND_ID + "\n" +
        "entryUUID: " + ENTRY_UUID3 + "\n";

        // Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            topEntryLdif += opFractModeAttrName + ": " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            String endString = addSynchroAttribute ? ("," + SYNCHRO_OPTIONAL_ATTR + "\n") : "\n";
            topEntryLdif += fracCfgValue + ( (i<size-1) ? "," : endString);
          }
          i++;
        }
      }
      else
      {
        // Add second backend top entry
        topEntryLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n";
      }
      addEntry(TestCaseUtils.entryFromLdifString(topEntryLdif));

      /**
       * Create the domain with the passed fractional configuration
       */

      // Create a config entry ldif, matching passed settings
      String configEntryLdif = "dn: cn=" + testName + ", cn=domains, " +
        SYNCHRO_PLUGIN_DN + "\n" + "objectClass: top\n" +
        "objectClass: ds-cfg-replication-domain\n" + "cn: " + testName + "\n" +
        "ds-cfg-base-dn: " + (initializeDomain ? TEST_ROOT_DN_STRING : TEST2_ROOT_DN_STRING) + "\n" +
        "ds-cfg-replication-server: localhost:" + replServerPort + "\n" +
        "ds-cfg-server-id: " + DS1_ID + "\n";

      int i=0;
      int size = fractionalConf.length;
      for (String fracCfgValue : fractionalConf) // Add fractional configuration attributes
      {
        if (i==0)
        {
          // First string is the class
          configEntryLdif += fractModeAttrName + ": " + fracCfgValue + ":";
        }
        else
        {
          // Other strings are attributes
          String endString = addSynchroAttribute ? ("," + SYNCHRO_OPTIONAL_ATTR + "\n") : "\n";
          configEntryLdif += fracCfgValue + ( (i<size-1) ? "," : endString);
        }
          i++;
      }
      fractionalDomainCfgEntry = TestCaseUtils.entryFromLdifString(configEntryLdif);

      // Add the config entry to create the replicated domain
      DirectoryServer.getConfigurationHandler().addEntry(Converters.from(fractionalDomainCfgEntry));
      assertNotNull(DirectoryServer.getEntry(fractionalDomainCfgEntry.getName()),
        "Unable to add the domain config entry: " + configEntryLdif);
    }
  }

  /**
   * Creates a new ReplicationServer.
   */
  private void createReplicationServer(String testCase) throws Exception
  {
    SortedSet<String> replServers = new TreeSet<>();

    String dir = testName + RS_ID + testCase + "Db";
    replicationServer = new ReplicationServer(
        new ReplServerFakeConfiguration(replServerPort, dir, 0, RS_ID, 0, 100, replServers));
  }

  private static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";
  private static final Task NO_INIT_TASK = null;

  private long readGenIdFromSuffixRootEntry(String rootDn) throws Exception
  {
    DN baseDN = DN.valueOf(rootDn);
    Entry resultEntry = getEntry(baseDN, 1000, true);
    if (resultEntry == null)
    {
      debugInfo("Entry not found <" + rootDn + ">");
    }
    else
    {
      debugInfo("Entry found <" + rootDn + ">");

      AttributeType synchronizationGenIDType =
          DirectoryServer.getSchema().getAttributeType(REPLICATION_GENERATION_ID);
      List<Attribute> attrs = resultEntry.getAttribute(synchronizationGenIDType);
      if (!attrs.isEmpty())
      {
        Attribute attr = attrs.get(0);
        if (attr.size() == 1)
        {
          return Long.decode(attr.iterator().next().toString());
        }
      }
    }
    return -1;
  }

  /**
   * Send the AddMsg (from the fake replication domain) for the passed entry
   * containing the attributes defined in the passed fractional configuration
   */
  private void sendAddMsg(boolean firstBackend, String... fractionalConf)
      throws Exception
  {
      String entryLdif = "dn: " + (firstBackend ? ENTRY_DN : ENTRY_DN2) + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n";
        String classStr = "";
        if ( fractionalConf[0].equalsIgnoreCase("inetOrgPerson") ||
        fractionalConf[0].equalsIgnoreCase("2.16.840.1.113730.3.2.2"))
        {
          classStr = "objectClass: " + fractionalConf[0] + "\n";
        }
        entryLdif += classStr + "uid: 1\n" +
        "entryUUID: " + ENTRY_UUID + "\n" +
        "sn: snValue\n" + "cn: cnValue\n" +
        OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";

      // Add attributes concerned by fractional configuration
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          entryLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
        }
        first = false;
      }

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(TestCaseUtils.entryFromLdifString(entryLdif), ENTRY_UUID));
  }

  /**
   * Send (from the fake replication domain) a ModifyMsg for the passed entry
   * modifying attributes defined in the passed fractional configuration
   */
  private void sendModifyMsg(boolean firstBackend, String... fractionalConf)
      throws Exception
  {
      // Create modifications on the fractional attributes
      List<Modification> mods = new ArrayList<>();
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          Attribute attr =
            Attributes.create(fracCfgValue.toLowerCase(), fracCfgValue + "NewValue");
          mods.add(new Modification(ModificationType.REPLACE, attr));
        }
        first = false;
      }

      // Add modification for the special attribute (modified attribute)
      Attribute attr =
        Attributes.create(OPTIONAL_ATTR.toLowerCase(), OPTIONAL_ATTR + "NewValue");
      mods.add(new Modification(ModificationType.REPLACE, attr));

      // Add modification for the synchro attribute (added attribute)
      attr = Attributes.create(SYNCHRO_OPTIONAL_ATTR.toLowerCase(), SYNCHRO_OPTIONAL_ATTR + "Value");
      mods.add(new Modification(ModificationType.ADD, attr));

      DN entryDn = DN.valueOf(firstBackend ? ENTRY_DN : ENTRY_DN2);
      ModifyMsg modifyMsg = new ModifyMsg(gen.newCSN(), entryDn, mods, ENTRY_UUID);
      replicationDomain.publish(modifyMsg);
  }

  /**
   * Utility method : Add an entry in the database
   */
  private void addEntry(Entry entry) throws Exception
  {
    connection.processAdd(entry);
    assertNotNull(getEntry(entry.getName(), 1000, true));
  }

  /**
   * Check that the just added entry (newEntry) meets the fractional criteria
   * regarding the passed configuration : mode and attributes to be filtered/not
   * filtered
   */
  private void checkEntryFilteredAfterAdd(Entry newEntry,
    int fractionalMode, String... fractionalConf) throws Exception
  {
    {
      // Is the added entry of the expected object class ?
      String objectClassStr = fractionalConf[0];
      if (!objectClassStr.equals("*"))
      {
        ObjectClass objectClass = DirectoryServer.getSchema().getObjectClass(objectClassStr);
        assertTrue(newEntry.hasObjectClass(objectClass));
      }

      // Go through each interesting attribute and check it is present or not
      // according to the fractional mode
      boolean first = true;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          // Exclude mode: attributes should not be there, but OPTIONAL_ATTR attribute should
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              assertFalse(newEntry.hasAttribute(DirectoryServer.getSchema().getAttributeType(fracAttr)));
            }
            first = false;
          }
          checkEntryAttributeValue(newEntry, OPTIONAL_ATTR, OPTIONAL_ATTR + "Value");
          break;
        case INCLUDE_FRAC_MODE:
          // Include mode: attributes should be there, but OPTIONAL_ATTR
          // attribute should not
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              checkEntryAttributeValue(newEntry, fracAttr, fracAttr + "Value");
            }
            first = false;
          }
          assertFalse(newEntry.hasAttribute(DirectoryServer.getSchema().getAttributeType(OPTIONAL_ATTR)));
          break;
        default:
          fail("Unexpected fractional mode.");
      }
    }
  }

  /**
   * Check that the just modified entry (entry) meets the fractional criteria
   * regarding the passed configuration : mode and attributes to be filtered/not
   * filtered
   */
  private void checkEntryFilteredAfterModify(Entry entry,
    int fractionalMode, String... fractionalConf) throws Exception
  {
    {
      // Is the added entry of the expected object class ?
      String objectClassStr = fractionalConf[0];
      if (!objectClassStr.equals("*"))
      {
        ObjectClass objectClass = DirectoryServer.getSchema().getObjectClass(objectClassStr);
        assertTrue(entry.hasObjectClass(objectClass));
      }

      // Go through each interesting attribute and check it has been modified or
      // not according to the fractional mode
      boolean first = true;
      switch (fractionalMode)
      {
        case EXCLUDE_FRAC_MODE:
          // Exclude mode: attributes should not be there, but OPTIONAL_ATTR
          // attribute should have been modified
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              assertFalse(entry.hasAttribute(DirectoryServer.getSchema().getAttributeType(fracAttr)));
            }
            first = false;
          }
          checkEntryAttributeValue(entry, OPTIONAL_ATTR, OPTIONAL_ATTR + "NewValue");
          break;
        case INCLUDE_FRAC_MODE:
          // Include mode: attributes should have been modified, but OPTIONAL_ATTR
          // attribute should not be there
          for (String fracAttr : fractionalConf)
          {
            if (!first)
            {
              checkEntryAttributeValue(entry, fracAttr, fracAttr + "NewValue");
            }
            first = false;
          }
          assertFalse(entry.hasAttribute(DirectoryServer.getSchema().getAttributeType(OPTIONAL_ATTR)));
          break;
        default:
          fail("Unexpected fractional mode.");
      }
      // In both modes, SYNCHRO_OPTIONAL_ATTR attribute should have been added
      checkEntryAttributeValue(entry, SYNCHRO_OPTIONAL_ATTR, SYNCHRO_OPTIONAL_ATTR + "Value");
    }
  }

  /**
   * Check that the provided entry has a single value attribute which has the
   * expected attribute value
   */
  private static void checkEntryAttributeValue(Entry entry, String attributeName, String attributeValue)
  {
    List<Attribute> attrs = entry.getAttribute(attributeName);
    assertThat(attrs).as("Was expecting attribute " + attributeName + "=" + attributeValue).hasSize(1);
    Attribute attr = attrs.get(0);
    Iterator<ByteString> attrValues = attr.iterator();
    assertTrue(attrValues.hasNext());
    ByteString attrValue = attrValues.next();
    assertFalse(attrValues.hasNext());
    assertEquals(attrValue.toString(), attributeValue, "Was expecting attribute " +
      attributeName + "=" + attributeValue + " but got value: " + attrValue);
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateExcludePrecommitProvider")
  private Object[][] testInitWithFullUpdateExcludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, true, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateExcludeNightlyProvider")
  private Object[][] testInitWithFullUpdateExcludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, false, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateExcludePrecommitProvider")
  public void testInitWithFullUpdateExcludePrecommit(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateExclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateExcludeNightlyProvider", groups = "slow")
  public void testInitWithFullUpdateExcludeNightly(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateExclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Configures a domain which is not fractional to fractional exclusive,
   * then emulates an online full update to initialize the fractional domain and
   * have it operational.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInitWithFullUpdateExclude(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    String testcase = "testInitWithFullUpdateExclude" + testProviderLineId;

    initTest();

    // We need a backend with a real configuration in cn=config as at import time
    // the real domain will check for backend existence in cn=config. So we use
    // dc=example,dc=com for this particular test.
    clearBackend("userRoot");

    try
    {
      /*
       * Create replication server and connect fractional domain to it then fake
       * domain
       */
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      // without initializing the backend
      createFractionalDomain(false, EXCLUDE_FRAC_MODE, fractionalConf);

      // The domain should go in bad gen as backend is not initialized with
      // fractional data
      LDAPReplicationDomain fractionalReplicationDomain =
        MultimasterReplication.findDomain(DN.valueOf(TEST2_ROOT_DN_STRING), null);
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.BAD_GEN_ID_STATUS, 5);

      // create fake domain to perform the full update
      long generationId = readGenIdFromSuffixRootEntry(TEST2_ROOT_DN_STRING);
      assertTrue(generationId != 0L);
      createFakeReplicationDomain(false, generationId);

      /*
       * Create the LDIF that will be used to initialize the domain from the
       * fake one. Initialize the fake domain with it.
       */

      //      Top Entry
      String exportLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n" +
        "ds-sync-generation-id: " + generationId + "\n";
      if (importedDomainIsFractional)
      {
        //                Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            exportLdif += "ds-sync-fractional-exclude: " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            exportLdif += fracCfgValue + ( (i<size-1) ? "," : "\n");
          }
            i++;
        }
      }
      //      Org Entry
      exportLdif += "\ndn: " + TEST2_ORG_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: test2\n\n";
      //      User entry
      exportLdif += "dn: " + ENTRY_DN2 + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" + "sn: snValue\n" + "cn: cnValue\n" +
        "uid: 1\n" + "entryUUID: " +
        ENTRY_UUID + "\n" + OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";
      if (!importedDomainIsFractional)
      {
        //                Add attributes concerned by fractional configuration
        boolean first = true;
        for (String fracCfgValue : fractionalConf)
        {
          if (!first)
          {
            // First string is the class
            exportLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
          }
          first = false;
        }
      }
      exportLdif += "\n"; // Needed ?

      replicationDomain.initExport(exportLdif, 2);

      // Perform full update from fake domain to fractional domain
      replicationDomain.initializeRemote(DS1_ID, NO_INIT_TASK);

      /*
       * Check fractional domain is operational and that filtering has been done
       * during the full update
       */

      // The domain should go back in normal status
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.NORMAL_STATUS, 15);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = getEntry(DN.valueOf(ENTRY_DN2), TIMEOUT, true);
      checkEntryFilteredAfterAdd(newEntry, EXCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(false, fractionalConf);

      // Wait for modify operation being replayed and
      // check that entry does not contain forbidden attributes
      Entry entry = waitTillEntryHasSynchroAttribute(ENTRY_DN2);
      checkEntryFilteredAfterModify(entry, EXCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  private Entry waitTillEntryHasSynchroAttribute(String entryDN)
      throws Exception
  {
    AttributeType synchroAttrType = DirectoryServer.getSchema().getAttributeType(SYNCHRO_OPTIONAL_ATTR);
    DN dn = DN.valueOf(entryDN);

    Entry entry = null;
    boolean synchroAttrFound = false;
    int timeout = TIMEOUT;
    while (timeout > 0)
    {
      entry = getEntry(dn, TIMEOUT, true);
      if (entry.hasAttribute(synchroAttrType))
      {
        synchroAttrFound = true;
        break;
      }
      Thread.sleep(1000);
      timeout--;
    }
    assertTrue(synchroAttrFound, "Modify operation not replayed");
    return entry;
  }

  /**
   * Wait for the passed domain to have the desired status or fail if timeout
   * waiting.
   */
  private void waitForDomainStatus(ReplicationDomain replicationDomain,
    ServerStatus expectedStatus, int nSec)
  {
    int toWait = nSec;
    ServerStatus serverStatus = null;
    while(nSec > 0)
    {
      serverStatus = replicationDomain.getStatus();
      if ( serverStatus ==  expectedStatus )
      {
        debugInfo("waitForDomainStatus: expected replication " +
          "domain status obtained after " + (toWait-nSec) + " second(s).");
        return;
      }
      TestCaseUtils.sleep(1000);
      nSec--;
    }
    fail("Did not get expected replication domain status: expected <" + expectedStatus +
      "> but got <" + serverStatus + ">, after " + toWait + " second(s)");
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateIncludePrecommitProvider")
  private Object[][] testInitWithFullUpdateIncludePrecommitProvider()
  {
    return new Object[][]
    {
      { 1, true, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Returns a bunch of single values for fractional configuration
   * attributes
   */
  @SuppressWarnings("unused")
  @DataProvider(name = "testInitWithFullUpdateIncludeNightlyProvider")
  private Object[][] testInitWithFullUpdateIncludeNightlyProvider()
  {
    return new Object[][]
    {
      { 1, false, new String[] {"inetOrgPerson", "displayName"}}
    };
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a small set of data, for precommit test
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateIncludePrecommitProvider")
  public void testInitWithFullUpdateIncludePrecommit(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateInclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Calls the testInitWithFullUpdateExclude test with a larger set of data, for nightly tests
   * purpose
   */
  @Test(dataProvider = "testInitWithFullUpdateIncludeNightlyProvider", groups = "slow")
  public void testInitWithFullUpdateIncludeNightly(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    testInitWithFullUpdateInclude(testProviderLineId, importedDomainIsFractional, fractionalConf);
  }

  /**
   * Configures a domain which is not fractional to fractional inclusive,
   * then emulates an online full update to initialize the fractional domain and
   * have it operational.
   * Note: testProviderLineId just here to know what is the provider problematic
   * line if the test fail: prevent some display like:
   *  [testng] parameter[0]: [Ljava.lang.String;@151e824
   * but have instead:
   *  [testng] parameter[0]: 6
   *  [testng] parameter[1]: [Ljava.lang.String;@151e824
   */
  private void testInitWithFullUpdateInclude(int testProviderLineId,
    boolean importedDomainIsFractional, String... fractionalConf) throws Exception
  {
    String testcase = "testInitWithFullUpdateInclude" + testProviderLineId;

    initTest();

    // We need a backend with a real configuration in cn=config as at import time
    // the real domain will check for backend existence in cn=config. So we use
    // dc=example,dc=com for this particular test.
    clearBackend("userRoot");

    try
    {
      /*
       * Create replication server and connect fractional domain to it then fake
       * domain
       */
      createReplicationServer(testcase);

      // create fractional domain with the passed fractional configuration
      // without initializing the backend
      createFractionalDomain(false, INCLUDE_FRAC_MODE, fractionalConf);

      // The domain should go in bad gen as backend is not initialized with
      // fractional data
      LDAPReplicationDomain fractionalReplicationDomain =
        MultimasterReplication.findDomain(DN.valueOf(TEST2_ROOT_DN_STRING), null);
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.BAD_GEN_ID_STATUS, 5);

      // create fake domain to perform the full update
      long generationId = readGenIdFromSuffixRootEntry(TEST2_ROOT_DN_STRING);
      assertTrue(generationId != 0L);
      createFakeReplicationDomain(false, generationId);

      /*
       * Create the LDIF that will be used to initialize the domain from the
       * fake one. Initialize the fake domain with it.
       */

      //      Top Entry
      String exportLdif = "dn: " + TEST2_ROOT_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: domain\n" +
        "dc: example\n" +
        "ds-sync-generation-id: " + generationId + "\n";
      if (importedDomainIsFractional)
      {
        //                Add fractional config
        int i=0;
        int size = fractionalConf.length;
        for (String fracCfgValue : fractionalConf) // Add fractional operational attributes
        {
          if (i==0)
          {
            // First string is the class
            exportLdif += "ds-sync-fractional-include: " + fracCfgValue + ":";
          }
          else
          {
            // Other strings are attributes
            exportLdif += fracCfgValue + ( (i<size-1) ? "," : "," + SYNCHRO_OPTIONAL_ATTR + "\n");
          }
            i++;
        }
      }
      //      Org Entry
      exportLdif += "\ndn: " + TEST2_ORG_DN_STRING + "\n" +
        "objectClass: top\n" +
        "objectClass: organization\n" +
        "o: test2\n\n";
      //      User entry
      exportLdif += "dn: " + ENTRY_DN2 + "\n" + "objectClass: top\n" +
        "objectClass: person\n" + "objectClass: organizationalPerson\n" +
        "objectClass: inetOrgPerson\n" +
        "sn: snValue\n" + "cn: cnValue\n" + "uid: 1\n" + "entryUUID: " +
        ENTRY_UUID + "\n" + OPTIONAL_ATTR + ": " + OPTIONAL_ATTR + "Value\n";
      //                Add attributes concerned by fractional configuration
      boolean first = true;
      for (String fracCfgValue : fractionalConf)
      {
        if (!first)
        {
          // First string is the class
          exportLdif += fracCfgValue + ": " + fracCfgValue + "Value\n";
        }
        first = false;
      }
      exportLdif += "\n"; // Needed ?

      replicationDomain.initExport(exportLdif, 2);

      // Perform full update from fake domain to fractional domain
      replicationDomain.initializeRemote(DS1_ID, NO_INIT_TASK);

      /*
       * Check fractional domain is operational and that filtering has been done
       * during the full update
       */

      // The domain should go back in normal status
      waitForDomainStatus(fractionalReplicationDomain,
        ServerStatus.NORMAL_STATUS, 15);

      // check that entry has been created and that it does not contain
      // forbidden attributes
      Entry newEntry = getEntry(DN.valueOf(ENTRY_DN2), TIMEOUT, true);
      checkEntryFilteredAfterAdd(newEntry, INCLUDE_FRAC_MODE, fractionalConf);

      // perform modify operation (modify forbidden attributes +
      // modify authorized attribute (not a no op))
      sendModifyMsg(false, fractionalConf);

      Entry entry = waitTillEntryHasSynchroAttribute(ENTRY_DN2);
      checkEntryFilteredAfterModify(entry, INCLUDE_FRAC_MODE, fractionalConf);
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests an add operation on an entry with RDN containing forbidden attribute
   * by fractional exclude configuration
   */
  @Test
  public void testAddWithForbiddenAttrInRDNExclude() throws Exception
  {
    String testcase = "testAddWithForbiddenAttrInRDNExclude";

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, EXCLUDE_FRAC_MODE, "inetOrgPerson",
          "displayName", "givenName");

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // Perform add operation with forbidden attribute in RDN
      // @formatter:off
      Entry entry = TestCaseUtils.makeEntry(
          "dn: displayName=ValueToBeKept," + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "sn: snValue",
          "cn: cnValue",
          "entryUUID: " + ENTRY_UUID,
          "displayName: ValueToBeKept",
          "displayName: displayNameValue");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID));

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */
      Entry newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");

      /**
       * Now perform same test, but with 2 forbidden attributes in RDN, using '+'
       */

      // Perform add operation with forbidden attribute in RDN
      // @formatter:off
      entry = TestCaseUtils.makeEntry(
          "dn: displayName=ValueToBeKept+givenName=ValueToBeKeptToo," + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "entryUUID: " + ENTRY_UUID2,
          "sn: snValue",
          "cn: cnValue",
          "displayName: ValueToBeKept",
          "displayName: displayNameValue",
          "givenName: descriptionValue",
          "givenName: ValueToBeKeptToo");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID2));

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */
      newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "givenName", "ValueToBeKeptToo");
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests an add operation on an entry with RDN containing forbidden attribute
   * by fractional include configuration
   */
  @Test
  public void testAddWithForbiddenAttrInRDNInclude() throws Exception
  {
    String testcase = "testAddWithForbiddenAttrInRDNInclude";

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, INCLUDE_FRAC_MODE, "inetOrgPerson",
          "carLicense");

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // @formatter:off
      Entry entry = TestCaseUtils.makeEntry(
          "dn: displayName=ValueToBeKept," + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "sn: snValue",
          "cn: cnValue",
          "entryUUID: " + ENTRY_UUID,
          "displayName: ValueToBeKept",
          "displayName: displayNameValue",
          "carLicense: cirLicenseValue");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID));

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */
      Entry newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "carLicense", "cirLicenseValue");

      /**
       * Now perform same test, but with 2 forbidden attributes in RDN, using '+'
       */

      // Perform add operation with forbidden attribute in RDN
      // @formatter:off
      entry = TestCaseUtils.makeEntry(
          "dn: displayName=ValueToBeKept+description=ValueToBeKeptToo," + TEST_ROOT_DN_STRING,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "sn: snValue",
          "cn: cnValue",
          "entryUUID: " + ENTRY_UUID2,
          "displayName: ValueToBeKept",
          "displayName: displayNameValue",
          "description: descriptionValue",
          "description: ValueToBeKeptToo",
          "carLicense: cirLicenseValue");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID2));

      /*
       * check that entry has been created and has attribute values from RDN
       * only
       */
      newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeKeptToo");
      checkEntryAttributeValue(newEntry, "carLicense", "cirLicenseValue");
    }
    finally
    {
      endTest();
    }
  }

  /**
   * Tests modify dn operation on an entry with old RDN containing forbidden
   * attribute by fractional exclude configuration
   */
  @Test
  public void testModifyDnWithForbiddenAttrInRDNExclude() throws Exception
  {
     String testcase = "testModifyDnWithForbiddenAttrInRDNExclude";

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, EXCLUDE_FRAC_MODE, "inetOrgPerson",
          "displayName", "givenName");

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // Perform add operation with forbidden attribute in RDN
      String entryName = "displayName=ValueToBeKept+givenName=ValueToBeRemoved," + TEST_ROOT_DN_STRING ;
      // @formatter:off
      Entry entry = TestCaseUtils.makeEntry(
          "dn: " + entryName,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "sn: snValue",
          "cn: cnValue",
          "entryUUID: " + ENTRY_UUID,
          "displayName: ValueToBeKept",
          "givenName: ValueToBeRemoved");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID));

      // check that entry has been created and has attribute values from RDN
      Entry newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "givenName", "ValueToBeRemoved");

      /*
       * Perform modify dn operation by renaming the entry keeping only one of
       * the forbidden attributes
       */
      String newEntryName = "displayName=ValueToBeKept," + TEST_ROOT_DN_STRING ;
      DN newEntryDn = DN.valueOf(newEntryName);

      // Create modify dn message to modify the entry.
      ModifyDNMsg modDnMsg = new ModifyDNMsg(DN.valueOf(entryName), gen.newCSN(),
        ENTRY_UUID, ENTRY_UUID3, false, TEST_ROOT_DN_STRING,
        "displayName=ValueToBeKept", null);

      replicationDomain.publish(modDnMsg);

      // check that entry has been renamed and has only attribute left in the new RDN
      newEntry = getEntry(newEntryDn, TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(newEntryDn, newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      assertThat(newEntry.getAttribute("givenName")).isEmpty();
    }
    finally
    {
      endTest();
    }
  }

  private AddMsg newAddMsg(Entry e, String entryUUID)
  {
    return new AddMsg(gen.newCSN(), e.getName(), entryUUID, null, e.getObjectClassAttribute(), e.getAttributes(), null);
  }

  /**
   * Tests modify dn operation on an entry with old RDN containing forbidden
   * attribute by fractional include configuration
   */
  @Test
  public void testModifyDnWithForbiddenAttrInRDNInclude() throws Exception
  {
    String testcase = "testModifyDnWithForbiddenAttrInRDNInclude";

    initTest();

    try
    {
      createReplicationServer(testcase);
      createFractionalDomain(true, INCLUDE_FRAC_MODE, "inetOrgPerson",
          "carLicense");

      // create fake domain to send operations
      createFakeReplicationDomain(true, readGenIdFromSuffixRootEntry(TEST_ROOT_DN_STRING));

      // Perform add operation with forbidden attribute in RDN
      String entryName = "displayName=ValueToBeKept+description=ValueToBeRemoved," + TEST_ROOT_DN_STRING ;
      // @formatter:off
      Entry entry = TestCaseUtils.makeEntry(
          "dn: " + entryName,
          "objectClass: top",
          "objectClass: person",
          "objectClass: organizationalPerson",
          "objectClass: inetOrgPerson",
          "sn: snValue",
          "cn: cnValue",
          "entryUUID: " + ENTRY_UUID,
          "displayName: ValueToBeKept",
          "description: ValueToBeRemoved");
      // @formatter:on

      // Create an update message to add an entry.
      replicationDomain.publish(newAddMsg(entry, ENTRY_UUID));

      // check that entry has been created and has attribute values from RDN
      Entry newEntry = getEntry(entry.getName(), TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(entry.getName(), newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      checkEntryAttributeValue(newEntry, "description", "ValueToBeRemoved");

      /*
       * Perform modify dn operation by renaming the entry keeping only one of
       * the forbidden attributes
       */
      String newEntryName = "displayName=ValueToBeKept," + TEST_ROOT_DN_STRING ;
      DN newEntryDn = DN.valueOf(newEntryName);

      // Create modify dn message to modify the entry.
      ModifyDNMsg modDnMsg = new ModifyDNMsg(DN.valueOf(entryName), gen.newCSN(),
        ENTRY_UUID, ENTRY_UUID3, false, TEST_ROOT_DN_STRING,
        "displayName=ValueToBeKept", null);

      replicationDomain.publish(modDnMsg);

      // check that entry has been renamed and has only attribute left in the * new RDN
      newEntry = getEntry(newEntryDn, TIMEOUT, true);
      assertNotNull(newEntry);
      assertEquals(newEntryDn, newEntry.getName());
      assertTrue(newEntry.hasObjectClass(getInetOrgPersonObjectClass()));
      checkEntryAttributeValue(newEntry, "displayName", "ValueToBeKept");
      assertThat(newEntry.getAttribute("description")).isEmpty();
    }
    finally
    {
      endTest();
    }
  }

  private ObjectClass getInetOrgPersonObjectClass()
  {
    return DirectoryServer.getSchema().getObjectClass("inetOrgPerson");
  }
}
