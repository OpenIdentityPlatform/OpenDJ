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

// OpenDMK Imports
//
import com.ibm.staf.STAFHandle;
import com.sun.management.snmp.SnmpPduTrap;
import com.sun.management.snmp.SnmpPduRequest;
import com.sun.management.snmp.SnmpScopedPduRequest;
import com.sun.management.snmp.manager.SnmpTrapListener;
import com.sun.management.snmp.SnmpEventReportDispatcher;

/**
 * This class perform a SNMP trap listener.
 */
public class SNMPTrapListener implements SnmpTrapListener {

  /**
   * Callback for SNMP v1 traps.
   *
   * @param trap trap information
   */
  public void processSnmpTrapV1(SnmpPduTrap trap) {
    trapCommunity = new String(trap.community);

    if (trapCommunity.compareTo(community) == 0) {
      nbTrapV1_community++;
    } else {
      nbTrapV1_otherCommunity++;
    }
  }

  /**
   * Callback for SNMP v2c traps.
   *
   * @param trap trap information
   */
  public void processSnmpTrapV2(SnmpPduRequest trap) {
    trapCommunity = new String(trap.community);

    if (trapCommunity.compareTo(community) == 0) {
      nbTrapV2_community++;
    } else {
      nbTrapV2_otherCommunity++;
    }
  }

  /**
   * Callback for SNMP v3 traps.
   *
   * @param trap trap information
   */
  public void processSnmpTrapV3(SnmpScopedPduRequest trap) {
    trapCommunity = new String(trap.contextName);

    if (trapCommunity.compareTo(community) == 0) {
      nbTrapV3_community++;
    } else {
      nbTrapV3_otherCommunity++;
    }
  }

  /**
   * Gets the calling arguments.
   *
   * @param args SNMP agent version + SNMP agent host + SNMP agent port
   * @return 0 if the init succeeded or 1 if the init failed
   */
  public int init(String[] args) {
    int rc = 0;

    System.out.println("\n");

    if (args.length < 3) {
      // Missing arguments
      System.out.println(
              "usage: " +
              " -h <remoteHost>" +
              " -p <port>" +
              " -t <number>" +
              " -c <community>");
      rc = 1;
    } else {
      for (int i = 0; i < args.length; i++) {
        String opt = args[i];
        String val = args[i + 1];

        switch (opt.charAt(1)) {
          case 'h':
            remoteHost = val;
            break;
          case 'p':
            port = new Integer(val).intValue();
            break;
          case 't':
            expectedTrapList = val;
            break;
          case 'c':
            community = val;
            break;
          default:
            System.out.println("Unknown option -" + opt.charAt(1) + ".");
            rc = 1;
        }

        if (rc == 1) {
          break;
        }

        i = i + 1;
      }
    }

    if (rc == 0) {
      System.out.println("init() of SNMPTrapListener succeeded");
    } else {
      System.out.println("init() of SNMPTrapListener failed");
    }

    return rc;
  }

  /**
   * Run the trap listener.
   *
   * @return 0 if the run succeeded or 1 if the run failed
   */
  public int run() {
    int rc = 0;

    try {
      SnmpEventReportDispatcher trapAgent = new SnmpEventReportDispatcher(port);
      trapAgent.addTrapListener(this);

      Thread t = new Thread(trapAgent);
      t.start();

      System.out.println(
              "run() of SNMPTrapListener: Listen traps on port " + port);

      System.out.println("run() of SNMPTrapListener: Wait 10s.");
      Thread.sleep(10000);

      // Register with STAF
      STAFHandle handle = new STAFHandle("SNMPTrapListener");

      // Post event to notice that the trap listener is ready
      System.out.println(
              "run() of SNMPTrapListener: Send \"SNMPTrapListener/Ready\"" +
              " event to " + remoteHost + ".");
      handle.submit2(remoteHost, "SEM", "PULSE EVENT SNMPTrapListener/Ready");

      // Wait event which notices that all the traps have been sent
      System.out.println(
              "run() of SNMPTrapListener: Wait \"SendTrap/Complete\" event " +
              "from " + remoteHost + ".");
      handle.submit2(remoteHost, "SEM", "WAIT EVENT SendTrap/Complete");

      // Delte event
      System.out.println(
              "run() of SNMPTrapListener: Delte \"SendTrap/Complete\" event " +
              "on " + remoteHost + ".");
      handle.submit2(remoteHost, "SEM", "DELETE EVENT SendTrap/Complete");

      // Display status of the traps
      currentTrapNumber =
              nbTrapV1_community +
              nbTrapV1_otherCommunity +
              nbTrapV2_community +
              nbTrapV2_otherCommunity +
              nbTrapV3_community +
              nbTrapV3_otherCommunity;
      System.out.println(
              "run() of SNMPTrapListener has catched " +
              currentTrapNumber + " traps.");

      String[] tmp = expectedTrapList.split(",");
      System.out.println("Number of trap V1 in " + community + " community: " +
              nbTrapV1_community + "/" + tmp[0]);
      System.out.println("Number of trap V1 in other community: " +
              nbTrapV1_otherCommunity + "/" + tmp[1]);
      System.out.println("Number of trap V2 in " + community + " community: " +
              nbTrapV2_community + "/" + tmp[2]);
      System.out.println("Number of trap V2 in other community: " +
              nbTrapV2_otherCommunity + "/" + tmp[3]);
      System.out.println("Number of trap V3 in " + community + " community: " +
              nbTrapV3_community + "/" + tmp[4]);
      System.out.println("Number of trap V3 in other community: " +
              nbTrapV3_otherCommunity + "/" + tmp[5]);

      // Status of the trap listener
      currentTrapList =
              nbTrapV1_community + "," +
              nbTrapV1_otherCommunity + "," +
              nbTrapV2_community + "," +
              nbTrapV2_otherCommunity + "," +
              nbTrapV3_community + "," +
              nbTrapV3_otherCommunity;
      if (currentTrapList.compareTo(expectedTrapList) != 0) {
        rc = 1;
      }
    } catch (Exception e) {
      System.out.println(
              "connect() of SNMPGet catched execption: " + e.toString() + "\"");

      rc = 1;
    }

    if (rc == 0) {
      System.out.println("run() of SNMPTrapListener succeeded");
    } else {
      System.out.println("run() of SNMPTrapListener failed");
    }

    return rc;
  }

  /**
   * Main.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    SNMPTrapListener client = new SNMPTrapListener();

    int rc = 0;

    // Retrieve parameters
    rc = client.init(args);

    // If init() succeeded then run the trap listener
    if (rc == 0) {
      rc = client.run();
    }

    System.exit(rc);
  }

  // Arguments
  String remoteHost = null;
  int port = 0;
  String community = null;
  String expectedTrapList = null;

  String trapCommunity = "";
  String currentTrapList = null;
  int currentTrapNumber = 0;
  int nbTrapV1_community = 0;
  int nbTrapV1_otherCommunity = 0;
  int nbTrapV2_community = 0;
  int nbTrapV2_otherCommunity = 0;
  int nbTrapV3_community = 0;
  int nbTrapV3_otherCommunity = 0;
}
