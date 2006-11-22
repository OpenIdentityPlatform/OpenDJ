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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.server.synchronization;

import static org.opends.server.loggers.Error.logError;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.plugin.MultimasterSynchronization;
import org.opends.server.synchronization.plugin.PersistentServerState;
import org.opends.server.synchronization.protocol.AddMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.OperationType;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test the contructors, encoders and decoders of the synchronization AckMsg,
 * ModifyMsg, ModifyDnMsg, AddMsg and Delete Msg
 */
public class StressTest extends SynchronizationTestCase
{
  private static final String SYNCHRONIZATION_STRESS_TEST =
    "Synchronization Stress Test";

  /**
   * The internal connection used for operation
   */
  private InternalClientConnection connection;

  /**
   * Created entries that need to be deleted for cleanup
   */
  private ArrayList<Entry> entryList = new ArrayList<Entry>();

  /**
   * The Synchronization config manager entry
   */
  private String synchroStringDN;

  /**
   * The synchronization plugin entry
   */
  private String synchroPluginStringDN;

  private Entry synchroPluginEntry;

  /**
   * The Server synchro entry
   */
  private String synchroServerStringDN;

  private Entry synchroServerEntry;

  /**
   * The Change log entry
   */
  private String changeLogStringDN;

  private Entry changeLogEntry;

  /**
   * A "person" entry
   */
  private Entry personEntry;

  /**
   * schema check flag
   */
  private boolean schemaCheck;

  // WORKAROUND FOR BUG #639 - BEGIN -
  /**
   *
   */
  MultimasterSynchronization mms;

  private BrokerReader reader = null;

  // WORKAROUND FOR BUG #639 - END -

  /**
   * Stress test from LDAP server to client using the ChangelogBroker API.
   */
  @Test(enabled=true, groups="slow")
  public void fromServertoBroker() throws Exception
  {
    logError(ErrorLogCategory.SYNCHRONIZATION,
        ErrorLogSeverity.NOTICE,
        "Starting Synchronization StressTest : fromServertoBroker" , 1);
    
    final DN baseDn = DN.decode("ou=People,dc=example,dc=com");
    final int TOTAL_MESSAGES = 1000;
    cleanEntries();

    ChangelogBroker broker = openChangelogSession(baseDn, (short) 18);
    Monitor monitor = new Monitor("stress test monitor");
    DirectoryServer.registerMonitorProvider(monitor);

    try {
      /*
       * loop receiving update until there is nothing left
       * to make sure that message from previous tests have been consumed.
       */
      try
      {
        while (true)
        {
          broker.receive();
        }
      }
      catch (Exception e)
      { }
      /*
       * Test that operations done on this server are sent to the
       * changelog server and forwarded to our changelog broker session.
       */

      // Create an Entry (add operation) that will be later used in the test.
      Entry tmp = personEntry.duplicate();
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
          .nextMessageID(), null, tmp.getDN(),
          tmp.getObjectClasses(), tmp.getUserAttributes(),
          tmp.getOperationalAttributes());
      addOp.run();
      entryList.add(personEntry);
      assertTrue(DirectoryServer.entryExists(personEntry.getDN()),
        "The Add Entry operation failed");

      // Check if the client has received the msg
      SynchronizationMessage msg = broker.receive();
      assertTrue(msg instanceof AddMsg,
        "The received synchronization message is not an ADD msg");
      AddMsg addMsg =  (AddMsg) msg;

      Operation receivedOp = addMsg.createOperation(connection);
      assertTrue(OperationType.ADD.compareTo(receivedOp.getOperationType()) == 0,
        "The received synchronization message is not an ADD msg");

      assertEquals(DN.decode(addMsg.getDn()),personEntry.getDN(),
        "The received ADD synchronization message is not for the excepted DN");

      reader = new BrokerReader(broker);
      reader.start();

      long startTime = TimeThread.getTime();
      int count = TOTAL_MESSAGES;

      // Create a number of writer thread that will loop modifying the entry
      List<Thread> writerThreadList = new LinkedList<Thread>();
      for (int n = 0; n < 1; n++)
      {
        BrokerWriter writer = new BrokerWriter(count);
        writerThreadList.add(writer);
      }
      for (Thread thread : writerThreadList)
      {
        thread.start();
      }
      // wait for all the threads to finish.
      for (Thread thread : writerThreadList)
      {
        thread.join();
      }

      long afterSendTime = TimeThread.getTime();

      int rcvCount = reader.getCount();
      
      long afterReceiveTime = TimeThread.getTime();

      if (rcvCount != TOTAL_MESSAGES)
      {
        fail("some messages were lost : expected : " +TOTAL_MESSAGES +
            " received : " + rcvCount);
      }

    }
    finally {
      DirectoryServer.deregisterMonitorProvider(SYNCHRONIZATION_STRESS_TEST);
      broker.stop();
    }
  }

  /**
   * Set up the environment for performing the tests in this Class.
   * synchronization
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available.
    TestCaseUtils.startServer();

    // Disable schema check
    schemaCheck = DirectoryServer.checkSchema();
    DirectoryServer.setCheckSchema(false);

    // Create an internal connection
    connection = new InternalClientConnection();

    // Create backend top level entries
    String[] topEntries = new String[2];
    topEntries[0] = "dn: dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: domain\n";
    topEntries[1] = "dn: ou=People,dc=example,dc=com\n" + "objectClass: top\n"
        + "objectClass: organizationalUnit\n"
        + "entryUUID: 11111111-1111-1111-1111-111111111111\n";
    Entry entry;
    for (int i = 0; i < topEntries.length; i++)
    {
      entry = TestCaseUtils.entryFromLdifString(topEntries[i]);
      AddOperation addOp = new AddOperation(connection,
          InternalClientConnection.nextOperationID(), InternalClientConnection
              .nextMessageID(), null, entry.getDN(), entry.getObjectClasses(),
          entry.getUserAttributes(), entry.getOperationalAttributes());
      addOp.setInternalOperation(true);
      addOp.run();
      entryList.add(entry);
    }

    // top level synchro provider
    synchroStringDN = "cn=Synchronization Providers,cn=config";

    // Multimaster Synchro plugin
    synchroPluginStringDN = "cn=Multimaster Synchronization, "
        + synchroStringDN;
    String synchroPluginLdif = "dn: "
        + synchroPluginStringDN
        + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider\n"
        + "ds-cfg-synchronization-provider-enabled: true\n"
        + "ds-cfg-synchronization-provider-class: org.opends.server.synchronization.MultimasterSynchronization\n";
    synchroPluginEntry = TestCaseUtils.entryFromLdifString(synchroPluginLdif);

    // Change log
    changeLogStringDN = "cn=Changelog Server, " + synchroPluginStringDN;
    String changeLogLdif = "dn: " + changeLogStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n" + "ds-cfg-changelog-port: 8989\n"
        + "ds-cfg-changelog-server-id: 1\n";
    changeLogEntry = TestCaseUtils.entryFromLdifString(changeLogLdif);

    // suffix synchronized
    synchroServerStringDN = "cn=example, " + synchroPluginStringDN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-provider-config\n"
        + "cn: example\n"
        + "ds-cfg-synchronization-dn: ou=People,dc=example,dc=com\n"
        + "ds-cfg-changelog-server: localhost:8989\n"
        + "ds-cfg-directory-server-id: 1\n" + "ds-cfg-receive-status: true\n";
    synchroServerEntry = TestCaseUtils.entryFromLdifString(synchroServerLdif);

    String personLdif = "dn: uid=user.1,ou=People,dc=example,dc=com\n"
        + "objectClass: top\n" + "objectClass: person\n"
        + "objectClass: organizationalPerson\n"
        + "objectClass: inetOrgPerson\n" + "uid: user.1\n"
        + "homePhone: 951-245-7634\n"
        + "description: This is the description for Aaccf Amar.\n" + "st: NC\n"
        + "mobile: 027-085-0537\n"
        + "postalAddress: Aaccf Amar$17984 Thirteenth Street"
        + "$Rockford, NC  85762\n" + "mail: user.1@example.com\n"
        + "cn: Aaccf Amar\n" + "l: Rockford\n" + "pager: 508-763-4246\n"
        + "street: 17984 Thirteenth Street\n"
        + "telephoneNumber: 216-564-6748\n" + "employeeNumber: 1\n"
        + "sn: Amar\n" + "givenName: Aaccf\n" + "postalCode: 85762\n"
        + "userPassword: password\n" + "initials: AA\n";
    personEntry = TestCaseUtils.entryFromLdifString(personLdif);

    configureSynchronization();
  }

  /**
   * Clean up the environment. return null;
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  public void classCleanUp() throws Exception
  {
    DirectoryServer.setCheckSchema(schemaCheck);

    // WORKAROUND FOR BUG #639 - BEGIN -
    DirectoryServer.deregisterSynchronizationProvider(mms);
    mms.finalizeSynchronizationProvider();
    // WORKAROUND FOR BUG #639 - END -

    cleanEntries();
  }

  /**
   * suppress all the entries created by the tests in this class
   */
  private void cleanEntries()
  {
    DeleteOperation op;
    // Delete entries
    Entry entries[] = entryList.toArray(new Entry[0]);
    for (int i = entries.length - 1; i != 0; i--)
    {
      try
      {
        op = new DeleteOperation(connection, InternalClientConnection
            .nextOperationID(), InternalClientConnection.nextMessageID(), null,
            entries[i].getDN());
        op.run();
      } catch (Exception e)
      {
      }
    }
  }

  /**
   * @return
   */
  private List<Modification> generatemods(String attrName, String attrValue)
  {
    AttributeType attrType =
      DirectoryServer.getAttributeType(attrName.toLowerCase(), true);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(attrType, attrValue));
    Attribute attr = new Attribute(attrType, attrName, values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    return mods;
  }

  /**
   * Open a changelog session to the local Changelog server.
   *
   */
  private ChangelogBroker openChangelogSession(final DN baseDn, short serverId)
          throws Exception, SocketException
  {
    PersistentServerState state = new PersistentServerState(baseDn);
    state.loadState();
    ChangelogBroker broker = new ChangelogBroker(state, baseDn,
                                                 serverId, 0, 0, 0, 0, 100);
    ArrayList<String> servers = new ArrayList<String>(1);
    servers.add("localhost:8989");
    broker.start(servers);
    broker.setSoTimeout(5000);
    return broker;
  }

  /**
   * Configure the Synchronization for this test.
   */
  private void configureSynchronization() throws Exception
  {
    //
    // Add the Multimaster synchronization plugin
    DirectoryServer.getConfigHandler().addEntry(synchroPluginEntry, null);
    entryList.add(synchroPluginEntry);
    assertNotNull(DirectoryServer.getConfigEntry(DN
        .decode(synchroPluginStringDN)),
        "Unable to add the Multimaster synchronization plugin");

    // WORKAROUND FOR BUG #639 - BEGIN -
    DN dn = DN.decode(synchroPluginStringDN);
    ConfigEntry mmsConfigEntry = DirectoryServer.getConfigEntry(dn);
    mms = new MultimasterSynchronization();
    try
    {
      mms.initializeSynchronizationProvider(mmsConfigEntry);
    }
    catch (ConfigException e)
    {
      assertTrue(false,
          "Unable to initialize the Multimaster synchronization plugin");
    }
    DirectoryServer.registerSynchronizationProvider(mms);
    // WORKAROUND FOR BUG #639 - END -

    //
    // Add the changelog server
    DirectoryServer.getConfigHandler().addEntry(changeLogEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(changeLogEntry.getDN()),
        "Unable to add the changeLog server");
    entryList.add(changeLogEntry);

    //
    // We also have a replicated suffix (synchronization domain)
    DirectoryServer.getConfigHandler().addEntry(synchroServerEntry, null);
    assertNotNull(DirectoryServer.getConfigEntry(synchroServerEntry.getDN()),
        "Unable to add the synchronized server");
    entryList.add(synchroServerEntry);
  }

  private class BrokerWriter extends Thread
  {
    int count;

    /**
     * Creates a new Stress Test Reader
     * @param broker
     */
    public BrokerWriter(int count)
    {
      this.count = count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      while (count>0)
      {
        count--;
        // must generate the mods for every operation because they are modified
        // by processModify.
        List<Modification> mods = generatemods("telephonenumber", "01 02 45");

        ModifyOperation modOp =
          connection.processModify(personEntry.getDN(), mods);
        assertEquals(modOp.getResultCode(), ResultCode.SUCCESS);
      }
    }
  }

  /**
   * Continuously reads messages from a changelog broker until there is nothing
   * left. Count the number of received messages.
   */
  private class BrokerReader extends Thread
  {
    private ChangelogBroker broker;
    private int count = 0;
    private Boolean finished = false;

    /**
     * Creates a new Stress Test Reader
     * @param broker
     */
    public BrokerReader(ChangelogBroker broker)
    {
      this.broker = broker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      // loop receiving messages until either we get a timeout
      // because there is nothing left or an error condition happens.
      try
      {
        while (true)
        {
          SynchronizationMessage msg = broker.receive();
          if (msg == null)
            break;
          count ++;
        }
      } catch (Exception e) {
        synchronized (this)
        {
          finished = true;
          this.notify();
        }
      }
    }

    /**
     * wait until the thread has finished its job then return the number of
     * received messages.
     */
    public int getCount()
    {
      synchronized (this)
      {
        if (finished == true)
          return count;
        try
        {
          this.wait(60);
          return count;
        } catch (InterruptedException e)
        {
          return -1;
        }
      }
    }

    public int getCurrentCount()
    {
      return count;
    }
  }
  
  private class Monitor extends MonitorProvider
  {
    protected Monitor(String threadName)
    {
      super(threadName);
    }

    @Override
    public List<Attribute> getMonitorData()
    {
      Attribute attr;
      if (reader == null)
        attr = new Attribute("received-messages", "not yet started");
      else
        attr = new Attribute("received-messages",
                             String.valueOf(reader.getCurrentCount()));
      List<Attribute>  list = new LinkedList<Attribute>();
      list.add(attr);
      attr = new Attribute("base-dn", "ou=People,dc=example,dc=com");
      list.add(attr);
      return list;
    }

    @Override
    public String getMonitorInstanceName()
    {
      return SYNCHRONIZATION_STRESS_TEST;
    }

    @Override
    public void updateMonitorData()
    {
      // nothing to do
    
    }

    @Override
    public void initializeMonitorProvider(ConfigEntry configEntry)
    throws ConfigException, InitializationException
    {
      // nothing to do
    
    }

    @Override
    public long getUpdateInterval()
    {
      // we don't wont to do polling on this monitor
      return 0;
    }

    
    
  }
}
