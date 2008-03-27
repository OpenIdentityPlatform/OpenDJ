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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.snmp;

import com.sun.jdmk.tasks.DaemonTaskServer;
import com.sun.management.snmp.SnmpPduRequest;
import com.sun.management.snmp.SnmpPduTrap;
import com.sun.management.snmp.SnmpScopedPduRequest;
import com.sun.management.snmp.SnmpEventReportDispatcher;
import com.sun.management.snmp.manager.SnmpTrapListener;
import java.net.InetAddress;
import java.util.ArrayList;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * SNMP trap test.
 */
@Test(enabled=true, groups = {"precommit", "snmp"}, sequential = true)
public class SNMPTrapManagerTest extends SNMPConnectionManager {

    @BeforeClass
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @Test(enabled = true)
    public void checkTraps() {
        try {
            
            // Create a taskServer for processing traps.
            // This is an optional step. However using a DaemonTaskServer
            // to process incomming PDUs makes it possible to empty
            // the trap socket faster, thus reducing the hazards
            // of trap loss.
            // We set the priority of the DaemonTaskServer to 
            // Thread.NORM_PRIORITY so that emptying the socket takes 
            // precedence over trap processing.
            //
            final DaemonTaskServer taskServer = new DaemonTaskServer();
            taskServer.start(Thread.NORM_PRIORITY);

            // Create a listener and dispatcher for SNMP traps 
            // (SnmpEventReportDispatcher).
            // SnmpEventReportDispatcher is run as a thread and listens 
            // for traps in UDP port = agent port + 1.
            // Add TrapListenerImpl as SnmpTrapListener.
            // TrapListenerImpl will receive a callback when a valid trap
            // PDU is received.
            //
            final SnmpEventReportDispatcher trapAgent =
                    new SnmpEventReportDispatcher((this.getTrapSnmpPort()),
                    null, taskServer, null);
            SNMPTrapListenerImpl trapListener = new SNMPTrapListenerImpl();
            trapAgent.addTrapListener(trapListener);
            final Thread trapThread = new Thread(trapAgent);
            trapThread.setPriority(Thread.MAX_PRIORITY);
            trapThread.start();
            
            // One Trap
            this.setDown();
            
            int trapNumbers = trapListener.getNumberV1Traps();
            
            // Should received 1 traps
            assertEquals(trapNumbers, 1);
            
            // Nicely stop the SnmpEventReportDispatcher.
            //
            trapAgent.close();
            taskServer.terminate();

            return;

        } catch (Exception e) {
            fail("SyncManager::main: " +
                    "Exception occurred:" + e);
        }
    }
    
    private class SNMPTrapListenerImpl implements SnmpTrapListener {

        private int numberV1Traps=0;
        private int numberV2Traps=0;
        private int numberV3Traps=0;
        
        public void processSnmpTrapV1(SnmpPduTrap trap) {
            this.numberV1Traps++;
        }

        public void processSnmpTrapV2(SnmpPduRequest trap) {
            this.numberV2Traps++;
        }

        public void processSnmpTrapV3(SnmpScopedPduRequest trap) {
            this.numberV3Traps++;
        }
        
        public int getNumberV1Traps() {
            return this.numberV1Traps;
        }
        
        public int getNumberV2Traps() {
            return this.numberV3Traps;
        }
    }
    
}

