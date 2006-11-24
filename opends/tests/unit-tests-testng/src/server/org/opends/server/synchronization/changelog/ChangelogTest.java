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
import org.opends.server.synchronization.plugin.ChangelogBroker;
import org.opends.server.synchronization.protocol.DeleteMsg;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Tests for the chngelog service code.
 */

public class ChangelogTest extends SynchronizationTestCase
{
  /**
   * Basic test of the changelog code.
   * Create a changelog server, connect 2 clients and exchange
   * messages between the clients.
   */
  @Test()
  public void changelogBasic() throws Exception
  {
    // find  a free port
    ServerSocket socket = TestCaseUtils.bindFreePort();
    int changelogPort = socket.getLocalPort();
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
    Changelog changelog = new Changelog(changelogConfig);

    ChangelogBroker broker1 = null;
    ChangelogBroker broker2 = null;
    
    try {
      broker1 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 1, 100, changelogPort, 1000);
      broker2 = openChangelogSession(
          DN.decode("dc=example,dc=com"), (short) 2, 100, changelogPort, 1000);

      ChangeNumber cn = new ChangeNumber((long) 1, 1, (short)1);
      DeleteMsg msg = new DeleteMsg("o=test,dc=example,dc=com", cn, "uid");
      broker1.publish(msg);
      SynchronizationMessage msg2 = broker2.receive();
      if (msg2 instanceof DeleteMsg)
      {
        DeleteMsg del = (DeleteMsg) msg2;
        assertTrue(del.toString().equals(msg2.toString()));
      }
      else
        fail("Changelog transmission failed");
    }
    finally
    {
      changelog.shutdown();
      if (broker1 != null)
        broker1.stop();
      if (broker2 != null)
        broker2.stop();
    }
  }
}
