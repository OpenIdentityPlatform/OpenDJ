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
package org.opends.server.synchronization.changelog;

import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigEntry;
import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.DeleteMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.util.TimeThread;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the changelog service code.
 */

public class ChangelogTest extends SynchronizationTestCase
{
  /**
   * The changelog server that will be used in this test.
   */
  private Changelog changelog = null;

  /**
   * The port of the changelog server.
   */
  private int changelogPort;

  private ChangeNumber firstChangeNumberServer1 = null;
  private ChangeNumber secondChangeNumberServer1 = null;
  private ChangeNumber firstChangeNumberServer2 = null;
  private ChangeNumber secondChangeNumberServer2 = null;
  

  /**
   * Before starting the tests, start the server and configure a
   * changelog server.
   */
  @BeforeClass()
  public void configure() throws Exception
  {
    TestCaseUtils.startServer();

    //  find  a free port for the changelog server
    ServerSocket socket = TestCaseUtils.bindFreePort();
    changelogPort = socket.getLocalPort();
    socket.close();

    String changelogLdif =
      "dn: cn=Changelog Server\n"
        + "objectClass: top\n"
        + "objectClass: ds-cfg-synchronization-changelog-server-config\n"
        + "cn: Changelog Server\n"
        + "ds-cfg-changelog-port: "+ changelogPort + "\n"
        + "ds-cfg-changelog-server-id: 1\n";
    Entry tmp = TestCaseUtils.entryFromLdifString(changelogLdif);
    ConfigEntry changelogConfig = new ConfigEntry(tmp, null);
    changelog = new Changelog(changelogConfig);
  }

  /**
   * Basic test of the changelog code :
   *  Connect 2 clients to the changelog server and exchange messages
   *  between the clients.
   *
   * Note : Other tests in this file depends on this test and may need to
   *        change if this test is modified.
   */
  @Test()
  public void changelogBasic() throws Exception
  {
    ChangelogBroker server1 = null;
    ChangelogBroker server2 = null;

    try {
      /*
       * Open a sender session and a receiver session to the changelog
       */
      server1 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 1, 100, changelogPort,
          1000, true);
      server2 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 2, 100, changelogPort,
          1000, true);
      
      /*
       * Create change numbers for the messages sent from server 1
       * with current time  sequence 1 and with current time + 2 sequence 2 
       */
      long time = TimeThread.getTime();
      firstChangeNumberServer1 = new ChangeNumber(time, 1, (short) 1);
      secondChangeNumberServer1 = new ChangeNumber(time + 2, 2, (short) 1);
      
      /*
       * Create change numbers for the messages sent from server 2
       * with current time  sequence 1 and with current time + 3 sequence 2 
       */
      firstChangeNumberServer2 = new ChangeNumber(time+ 1, 1, (short) 2);
      secondChangeNumberServer2 = new ChangeNumber(time + 3, 2, (short) 2);
      
      /*
       * Send and receive a Delete Msg from server 1 to server 2
       */ 
      DeleteMsg msg =
        new DeleteMsg("o=test,dc=example,dc=com", firstChangeNumberServer1,
                      "uid");
      server1.publish(msg);
      SynchronizationMessage msg2 = server2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");      
      
      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg("o=test", secondChangeNumberServer1, "uid"); 
      server1.publish(msg);
      msg2 = server2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");
      
      /*
       * Send and receive a Delete Msg from server 1 to server 2
       */ 
      msg =
        new DeleteMsg("o=test,dc=example,dc=com", firstChangeNumberServer2,
                      "other-uid");
      server2.publish(msg);
      msg2 = server1.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");      
      
      /*
       * Send and receive a second Delete Msg
       */
      msg = new DeleteMsg("o=test", secondChangeNumberServer2, "uid"); 
      server2.publish(msg);
      msg2 = server1.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg.toString()),
            "Changelog basic : incorrect message body received.");
      }
      else
        fail("Changelog basic : incorrect message type received.");
    }
    finally
    {
      if (server1 != null)
        server1.stop();
      if (server2 != null)
        server2.stop();
    }
  }

  /**
   * Test that a new client see the change that was sent in the
   * previous test.
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void newClient() throws Exception
  {
    ChangelogBroker broker = null;

    try {
      broker =
        openChangelogSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, changelogPort, 1000, false);

      SynchronizationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("Changelog basic transmission failed");
      else
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.getChangeNumber().equals(firstChangeNumberServer1),
            "The first message received by a new client was the wrong one."
            + del.getChangeNumber() + " " + firstChangeNumberServer1);
      }
    }
    finally
    {
      if (broker != null)
        broker.stop();
    }
  }
  
  

  /**
   * Test that a client that has already seen some changes now receive
   * the correct next change.
   */
  private void newClientWithChanges(
      ServerState state, ChangeNumber nextChangeNumber) throws Exception
  {
    ChangelogBroker broker = null;
    
    /*
     * Connect to the changelog server using the state created above.
     */
    try {
      broker =
        openChangelogSession(DN.decode("dc=example,dc=com"), (short) 3,
                             100, changelogPort, 1000, state);

      SynchronizationMessage msg2 = broker.receive();
      if (!(msg2 instanceof DeleteMsg))
        fail("Changelog basic transmission failed");
      else
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.getChangeNumber().equals(nextChangeNumber),
            "The second message received by a new client was the wrong one."
            + del.getChangeNumber() + " " + nextChangeNumber);
      }
    }
    finally
    {
      if (broker != null)
        broker.stop();
    }
  }
  
  /**
   * Test that a client that has already seen the first change now see the
   * second change
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void newClientWithFirstChanges() throws Exception
  {
    /*
     * Create a ServerState updated with the first changes from both servers
     * done in test changelogBasic.
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);
    state.update(firstChangeNumberServer2);
    
    newClientWithChanges(state, secondChangeNumberServer1);
  }
  
  /**
   * Test that a client that has already seen the first change from server 1
   * now see the first change from server 2
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void newClientWithChangefromServer1() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer1);
    
    newClientWithChanges(state, firstChangeNumberServer2);
  }
  
  /**
   * Test that a client that has already seen the first chaneg from server 2
   * now see the first change from server 1
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void newClientWithChangefromServer2() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(firstChangeNumberServer2);
    
    newClientWithChanges(state, firstChangeNumberServer1);
  }
  
  /**
   * Test that a client that has not seen the second change from server 1
   * now receive it.
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void newClientLateServer1() throws Exception
  {
    /*
     * Create a ServerState updated with the first change from server 1
     */
    ServerState state = new ServerState();
    state.update(secondChangeNumberServer2);
    state.update(firstChangeNumberServer1);
    
    newClientWithChanges(state, secondChangeNumberServer1);
  }
  
  /**
   * Test that newClient() and newClientWithFirstChange() still works
   * after stopping and restarting the changelog server.
   */
  @Test(dependsOnMethods = { "changelogBasic" })
  public void stopChangelog() throws Exception
  {
    changelog.shutdown();
    configure();
    newClient();
    newClientWithFirstChanges();
    newClientWithChangefromServer1();
    newClientWithChangefromServer2();
  }

  /**
   * After the tests stop the changelog server.
   */
  @AfterClass()
  public void shutdown() throws Exception
  {
    if (changelog != null)
      changelog.shutdown();
  }
}
