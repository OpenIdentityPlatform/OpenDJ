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

import java.io.IOException;

import java.rmi.ConnectException;
import java.rmi.UnknownHostException;
import java.rmi.UnmarshalException;

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;

import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.CommunicationException;

/**
 * Producer who monitor an OpenDS server with JMX.
 */
public class JMXMonitor extends Thread {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  private Properties params;

  /**
   * Construct a JMXMonitor thread with the specified values.
   *
   * @param client  The main class of the client.
   * @param params  The parameters of the thread.
   */
  public JMXMonitor(MonitoringClient client, Properties params) {
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

    // Initialise the JMX environnement
    HashMap<String,Object> envJMX = new HashMap<String,Object>();
    if (params.containsKey("bindDN")) {
      String[] credentials = new String[] { params.getProperty("bindDN"),
              params.getProperty("bindPW") };
      envJMX.put("jmx.remote.credentials", credentials);
    }

    Vector<Data> attributesToMonitor =
            client.getDatasBuffer().getAttributesToMonitor(
            params.getProperty("name"));

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
        JMXServiceURL url;
        if (params.getProperty("name").equals("JMX")) {
          url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" +
                  params.getProperty("host") + ":" +
                  params.getProperty("port") +
                  "/org.opends.server.protocols.jmx.client-unknown");
        } else {
          url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" +
                  params.getProperty("host") + ":" +
                  params.getProperty("port") +
                  "/jmxrmi");
        }

        JMXConnector jmxc = JMXConnectorFactory.connect(url, envJMX);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

        // Simulate an important charge for the server
        try {
          sleep(Integer.parseInt(params.getProperty("charge")) *
                  client.getTimeUnit());
        } catch (InterruptedException e) {
          System.out.println(e.getLocalizedMessage());
        }

        // Retrieve the attributes
        for (Data d : attributesToMonitor) {
          try {
            Object attr = mbsc.getAttribute(
                    new ObjectName(d.getParameters().getProperty("MBeanName")),
                    d.getAttribute());
            if (attr != null) {
              try {
                client.getDatasBuffer().setData(d,
                        ((Attribute)attr).getValue().toString(),
                        (int)(System.currentTimeMillis() - date.getTime()));
              } catch (ClassCastException e) {
                client.getDatasBuffer().setData(d, attr.toString(),
                        (int)(System.currentTimeMillis() - date.getTime()));
              }

            } else {
              client.getDatasBuffer().dataError(d, "This attribute couldn't " +
                      "be retrieved");
            }
          } catch (InstanceNotFoundException e) {
            client.getDatasBuffer().dataError(d, "The MBean " +
                    d.getParameters().getProperty("MBeanName") + " does not " +
                    "exist in the repository");
          }

        }

        // Close the JMX connection
        jmxc.close();

        // Processing of the errors
        client.getDatasBuffer().verifyDatas(params.getProperty("name"));

      } catch (UnmarshalException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                "Invalid credentials");
      } catch (SecurityException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                "Invalid credentials");
      } catch (IOException e) {
        if (e.getCause().getCause() instanceof ConnectException) {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "Wrong port number");
        } else if (e.getCause().getCause() instanceof UnknownHostException ||
                e.getCause() instanceof CommunicationException) {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "Unknown host");
        } else {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  e.getLocalizedMessage());
        }
      } catch (JMException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                e.getLocalizedMessage());
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
