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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.snmp;

import com.sun.management.snmp.SnmpDefinitions;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpOidTableSupport;
import com.sun.management.snmp.SnmpVarBindList;
import com.sun.management.snmp.manager.SnmpParameters;
import com.sun.management.snmp.manager.SnmpPeer;
import com.sun.management.snmp.manager.SnmpRequest;
import com.sun.management.snmp.manager.SnmpSession;
import java.net.InetAddress;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * SNMP tests.
 */
@Test(enabled=false, groups = {"precommit", "snmp"}, sequential = true)
public class SNMPSyncManagerV2AccessTest extends SNMPConnectionManager {

    @BeforeClass
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @AfterClass
    public void afterClass() throws Exception {
        super.shutdownServer();
    }

    @DataProvider(name = "listAttributes")
    Object[][] listAttributes() {
        return new Object[][]{
            {"dsServerType"},
            {"dsServerDescription"},
            {"dsMasterEntries"},
            {"dsCopyEntries"},
            {"dsCacheEntries"},
            {"dsCacheHits"},
            {"dsSlaveHits"}};
    }

    @Test(enabled=false,dataProvider = "listAttributes")
    public void checkAttribute(String attributeName) {

        // get the SNMP peer agent
        SnmpPeer agent = this.getSnmpV2Peer(this.getSnmpPort());
        assertNotNull(agent);

        // Create parameters to associate to the entity to communicate
        // with.
        // When creating the parameter object, you can specify the read
        // and write community to be used when querying the agent.

        final SnmpParameters params =
                new SnmpParameters();

        // Set to the allowed the community string
        params.setRdCommunity("OpenDS@OpenDS");

        // The newly created parameter must be associated to the agent.
        //
        agent.setParams(params);

        // Build the session. A session creates, controls and
        // manages one or more requests.
        //
        try {

            final SnmpSession session =
                    new SnmpSession("SyncManager session");

            // A default peer (agent) can be associated to a SnmpSession.
            // When invoking a service provided by the SnmpSession, if the
            // agent is not specified, the session will perform the service
            // using the default peer as the target of the service.
            //
            session.setDefaultPeer(agent);

            // Build the list of variables you want to query.
            // For debug purposes, you can associate a name to your list.
            //
            final SnmpVarBindList list =
                    new SnmpVarBindList("SyncManager varbind list");

            // We want to read the "dsServerDescription" variable.
            //
            // We will thus query "dsServerDescription"
            //
            list.addVarBind(attributeName);

            // Make the SNMP get request and wait for the result.
            //
            SnmpRequest request = session.snmpGetNextRequest(null, list);

            final boolean completed = request.waitForCompletion(0);

            // Check for a timeout of the request.
            //
            if (completed == false) {
                fail("SyncManager::main: Request timed out." +
                        " Check reachability of agent");
                return;
            }

            // Now we have a response. Check if the response contains
            // an error.
            //
            final int errorStatus = request.getErrorStatus();
            if (errorStatus != SnmpDefinitions.snmpRspNoError) {
                fail("Error status = " +
                        SnmpRequest.snmpErrorToString(errorStatus));
                fail("Error index = " + request.getErrorIndex());
                return;
            }

            // Now we shall display the content of the result.
            //
            final SnmpVarBindList result = request.getResponseVarBindList();
            assertNotNull(result);
            assertEquals(result.getVarBindCount(), 1);


            // Nicely stop the session
            //
            session.destroySession();

        } catch (Exception exception) {
            fail("CheckAttribute");
        }

        //
        // That's all !
        //
        return;
    }

    /**
     * Build some data for the simpleGet test.
     */
    @DataProvider(name = "listCommunities")
    Object[][] listCommunities() {
        return new Object[][]{
            {"public", false},
            {"private", false},
            {"OpenDS@OpenDS", true},
            {"dummy", false},
            {"", false}};
    }

    @Test(enabled = false,dataProvider = "listCommunities")
    public void checkCommunity(String community, boolean expectedResult) {

        try {

            String host = InetAddress.getLocalHost().getCanonicalHostName();
            // Initialize the SNMP Manager API.
            // Specify the OidTable containing all the MIB II knowledge.
            // Use the OidTable generated by mibgen when compiling MIB II.
            //
            final SnmpOidTableSupport oidTable = new
                    DIRECTORY_SERVER_MIBOidTable();

            SnmpOid.setSnmpOidTable(oidTable);

            // At any time, it is possible to add knowledge in the OidTable
            // using the SnmpOidDatabaseSupport.
            // For instance, we could start the Manager API like that:
            //    SnmpOidDatabaseSupport oidDB =
            //                      new SnmpOidDatabaseSupport();
            //    SnmpOid.setSnmpOidTable(oidDB);
            // Then over time, MIB knowledge can be added as follows:
            //    oidDB.add(oidTable);
            //

            // Create a SnmpPeer object for representing the entity to
            // communicate with.
            //
            final SnmpPeer agent =
                    new SnmpPeer(host, this.getSnmpPort());

            // Create parameters to associate to the entity to communicate
            // with.
            // When creating the parameter object, you can specify the read
            // and write community to be used when querying the agent.
            //
            final SnmpParameters params =
                    new SnmpParameters();
            params.setRdCommunity(community);

            // The newly created parameter must be associated to the agent.
            //
            agent.setParams(params);

            // Build the session. A session creates, controls and
            // manages one or more requests.
            //
            final SnmpSession session =
                    new SnmpSession("SyncManager session");

            // A default peer (agent) can be associated to a SnmpSession.
            // When invoking a service provided by the SnmpSession, if the
            // agent is not specified, the session will perform the service
            // using the default peer as the target of the service.
            //
            session.setDefaultPeer(agent);

            // Build the list of variables you want to query.
            // For debug purposes, you can associate a name to your list.
            //
            final SnmpVarBindList list =
                    new SnmpVarBindList("SyncManager varbind list");

            // We want to read the "dsServerDescription" variable.
            //
            // We will thus query "dsServerDescription"
            //
            list.addVarBind("dsServerDescription");

            // Make the SNMP get request and wait for the result.
            //
            SnmpRequest request = session.snmpGetNextRequest(null, list);

            final boolean completed = request.waitForCompletion(0);

            // Check for a timeout of the request.
            //
            if (completed == false) {
                fail("SyncManager::main: Request timed out." +
                        " Check reachability of agent");
                return;
            }

            // Now we have a response. Check if the response contains
            // an error.
            //
            final int errorStatus = request.getErrorStatus();
            if (errorStatus != SnmpDefinitions.snmpRspNoError) {
                fail("Error status = " +
                        SnmpRequest.snmpErrorToString(errorStatus));
                fail("Error index = " + request.getErrorIndex());
                return;
            }

            // Now we shall display the content of the result.
            //
            final SnmpVarBindList result = request.getResponseVarBindList();
            assertNotNull(result);
            assertEquals(result.getVarBindCount(), 1);
            assertEquals(result.getVarBindAt(0).isValidValue(), expectedResult);


            // Nicely stop the session
            //
            session.destroySession();

            //
            // That's all !
            //
            return;

        } catch (Exception e) {
            fail("SyncManager::main: " + "Exception occurred:", e);
        }
    }
}
