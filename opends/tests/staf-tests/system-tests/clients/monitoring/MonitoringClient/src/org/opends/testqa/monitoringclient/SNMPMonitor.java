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

package org.opends.testqa.monitoringclient;

import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import org.opends.server.snmp.DIRECTORY_SERVER_MIBOidTable;

import com.sun.management.snmp.SnmpDefinitions;
import com.sun.management.snmp.SnmpVarBindList;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpOidTableSupport;
import com.sun.management.snmp.SnmpStatusException;
import com.sun.management.snmp.manager.SnmpRequest;
import com.sun.management.snmp.manager.SnmpSession;
import com.sun.management.snmp.manager.SnmpPeer;
import com.sun.management.snmp.manager.SnmpParameters;
import java.net.UnknownHostException;

/**
 * Producer who monitor an OpenDS server with SNMP.
 */
public class SNMPMonitor extends Thread {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * The properties of the producer.
   */
  private Properties params;

  /**
   * Construct a SNMPMonitor thread with the specified values.
   *
   * @param client  The main class of the client.
   * @param params  The parameters of the thread.
   */
  public SNMPMonitor(MonitoringClient client, Properties params) {
    this.client = client;
    this.params = params;

    this.setName(params.getProperty("name"));
  }

  /**
   * Connect to the server, get the attributes to monitor,
   * and wait a notify from the main thread.
   */
  @Override
  public void run() {
    Date date = new Date();

    Vector<Data> attributesToMonitor =
            client.getDatasBuffer().getAttributesToMonitor(
            params.getProperty("name"));

    // Initialise the SNMP evironnement
    final SnmpOidTableSupport oidTable = new DIRECTORY_SERVER_MIBOidTable();
    SnmpOid.setSnmpOidTable(oidTable);
    int tableEntryIndex = 1;
    int applIfProtocol = 0;

    while(true) {
      try {

        // Allow to desynchronize the producers.
        try {
          sleep(Integer.parseInt(params.getProperty("delay")) *
                  client.getTimeUnit());
        } catch (InterruptedException e) {
          System.out.println(e.getLocalizedMessage());
        }

        // Connect to the server
        SnmpParameters prms = new SnmpParameters("OpenDS@OpenDS",
                "OpenDS@OpenDS");
        prms.setProtocolVersion(SnmpDefinitions.snmpVersionOne);

        SnmpPeer agent = new SnmpPeer(params.getProperty("host"),
                Integer.parseInt(params.getProperty("port")));
        agent.setTimeout(client.getInterval());
        agent.setMaxTries(1);
        agent.setParams(prms);

        SnmpSession session = new SnmpSession("Get V1 session");
        session.snmpOptions.setPduFixedOnError(false);
        session.setDefaultPeer(agent);

        // Retrieve the index of the LDAP connector
        int ind=0;
        while (applIfProtocol == 0) {
          ind++;
          SnmpVarBindList list = new SnmpVarBindList("Get varbind list");
          list.addVarBind("dsApplIfProtocol." + tableEntryIndex + "." + ind);

          SnmpRequest request = session.snmpGetRequest(null, list);

          if (request.waitForCompletion(client.getInterval())) {

            String errorStatus = SnmpRequest.snmpErrorToString(
                    request.getErrorStatus());

            if (errorStatus.equals("noError")) {

              SnmpVarBindList resp = request.getResponseVarBindList();
              if (resp != null) {
                if (resp.getVarBindAt(0).getStringValue().endsWith(
                        params.getProperty("LDAPport"))) {
                  applIfProtocol = ind;
                }
              } else {
                applIfProtocol = -1;
              }

            } else {
              applIfProtocol = -1;
            }
          }

        }

        // Simulate an important charge for the server
        try {
          sleep(Integer.parseInt(params.getProperty("charge")) *
                  client.getTimeUnit());
        } catch (InterruptedException e) {
          System.out.println(e.getLocalizedMessage());
        }

        // Retrieve the attributes
        if (applIfProtocol != -1) {
          for (Data d : attributesToMonitor) {
            SnmpVarBindList list = new SnmpVarBindList("Get varbind list");
            list.addVarBind(d.getAttribute() + "." + tableEntryIndex + "." +
                    applIfProtocol);

            SnmpRequest request = session.snmpGetRequest(null, list);

            if (request.waitForCompletion(client.getInterval())) {

              String errorStatus = SnmpRequest.snmpErrorToString(
                      request.getErrorStatus());

              if (errorStatus.equals("noError")) {

                SnmpVarBindList resp = request.getResponseVarBindList();
                if (resp != null) {

                client.getDatasBuffer().setData(d,
                        resp.getVarBindAt(0).getStringValue(),
                        (int)(System.currentTimeMillis() - date.getTime()));

                } else {
                  client.getDatasBuffer().dataError(d, "The entry " +
                        d.getAttribute() + " does not exist in the MIB");
                }

              } else {
                client.getDatasBuffer().dataError(d, errorStatus);
              }
            }

          }
        } else {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "The index couldn't be retrieved");
        }

        // Close the SNMP session
        session.destroySession();

      // Processing of the errors
      } catch (SnmpStatusException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                  e.getLocalizedMessage());

      } catch (UnknownHostException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "Unknown host");

      }

      // Wait for the next run
      try {
        synchronized(MonitoringClient.lock) {
          MonitoringClient.lock.wait();
        }
      } catch (InterruptedException e) {
        System.out.println(e.getLocalizedMessage());
      }

      // Update the date
      date.setTime(System.currentTimeMillis());

    }
  }

}
