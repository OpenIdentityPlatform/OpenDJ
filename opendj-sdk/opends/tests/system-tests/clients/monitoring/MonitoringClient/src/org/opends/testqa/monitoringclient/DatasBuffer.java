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

/**
 * Buffer to store the datas retrieved by the producers.
 */
public class DatasBuffer {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * Date of the datas.
   */
  private Date date;

  /**
   * Datas retrieved by the producers.
   */
  private Vector<Data> datas;

  /**
   * Number of consumers who have cloned the buffer.
   */
  private int consumers;

  /**
   * True if the timeout have expired.
   */
  private boolean timeout;


  /**
   * Construct a DatasBuffer object.
   *
   * @param client  The main class of the client.
   */
  public DatasBuffer (MonitoringClient client) {
    this.client = client;
    this.date = new Date();
    this.datas = new Vector<Data>();
    this.consumers = 0;
    this.timeout = false;
  }

  /**
   * Constructor used to clone the DatasBuffer.
   *
   * @param client  The main class of the client.
   * @param date    The date of the datas.
   */
  private DatasBuffer (MonitoringClient client, Date date) {
    this.client = client;
    this.date = date;
    this.datas = new Vector<Data>();
    this.consumers = 0;
    this.timeout = false;
  }

  /**
   * Add an attribute to monitor.
   *
   * @param params  The parameters to monitor the attribute.
   * @param attribute   The name of the attibute to monitor.
   * @return  true if the attribute have been add; false otherwise.
   */
  public synchronized boolean addAttributeToMonitor (String attribute,
          Properties params) {
    return ((!this.containsData(attribute, params)) ?
      datas.add(new Data (attribute, params)) : false);
  }

  /**
   * Remove an attribute to monitor.
   *
   * @param attribute The name of the attibute to monitor.
   * @param params  The parameters to monitor the attribute.
   * @return  true if the attribute have been removed; false otherwise.
   */
  public synchronized Data removeAttributeToMonitor (String attribute,
          Properties params) {
    return datas.remove(this.indexOf(attribute, params));
  }

  /**
   * Returns the attributes to monitor for a well known protocol.
   *
   * @param protocol  The name of the protocol.
   * @return  The attributes to monitor.
   */
  public synchronized Vector<Data> getAttributesToMonitor(String protocol) {
    Vector<Data> attributesToMonitor = new Vector<Data>();
    for (Data d : datas) {
      if (d.getProtocol().equals(protocol)) {
        attributesToMonitor.add(d);
      }
    }
    return attributesToMonitor;
  }

  /**
   * Test if the DatasBuffer contains a data.
   *
   * @param attribute The name of the attibute monitored.
   * @param parameters  The parameters to monitor the attribute.
   * @return  true if the DatasBuffer contains a data, false otherwise.
   */
  public synchronized boolean containsData (String attribute,
          Properties parameters) {
    return (this.indexOf(attribute,  parameters) != -1);
  }

  /**
   * Returns the specified data.
   *
   * @param attribute The name of the attibute monitored.
   * @param parameters  The parameters to monitor the attribute.
   * @return  The data with the specified protocol and attribute
   */
  public synchronized Data getData (String attribute, Properties parameters) {
    int i = this.indexOf(attribute,  parameters);
    return ((i != -1) ? datas.get(i) : null );
  }

  /**
   * Sets a data in the buffer.
   *
   * @param d         The data to set
   * @param value     The value of the attribute monitored.
   * @param timer     The number of milliseconds beetween the launch of the
   * request and the creation of the data.
   */
  public synchronized void setData (Data d, String value, int timer) {
    if (!datas.contains(d)) {
      client.getErrorsBuffer().addError(
              d.getParameters().getProperty("protocol"), d.getAttribute(),
              "Unknown Attribute");
    } else if (timer > client.getInterval()) {
      client.getErrorsBuffer().addError(
              d.getParameters().getProperty("protocol"), d.getAttribute(),
              "Timeout exceed: " + timer + " ms");
    } else {
      d.setValue(value);
      d.setTimer(timer);
    }
  }

  /**
   * Add a data in the buffer.
   *
   * @param attribute   The name of the attribute
   * @param parameters  The parameters of the data
   * @param value       The value of the data
   * @param timer       The timer of the data
   * @return true if the data have been add; false otherwise;
   */
  private synchronized boolean addData (String attribute, Properties parameters,
          String value, int timer) {
    Data d = new Data(attribute, parameters);
    d.setValue(value);
    d.setTimer(timer);
    return datas.add(d);
  }

  /**
   * Verify if all the attribute to monitor of a protocol have been set.
   *
   * @param protocol  The name of the protocol used to retrieve the datas.
   */
  public synchronized void verifyDatas(String protocol) {
    for (Data d : datas) {
      if (d.isProtocol(protocol) && d.hasEmptyValue() &&
              d.getTimer() != -1) {
        this.dataError(d, "This attribute couldn't be retrieved");
      }
    }
    if (this.isFull()) {
      notifyAll();
    }
  }

  /**
   * If the buffer isn't full, fill the empty data with the error code and wake
   * up the consumers; else, reset the date and the number of consumers.
   */
  public synchronized void timeoutExpired() {
    if (consumers == 0) {
      if (!this.isFull()) {
         for (Data d : datas) {
          if (d.hasEmptyValue()) {
            client.getErrorsBuffer().addError(d.getProtocol(), d.getAttribute(),
                    "This attribute couldn't be retrieved because the " +
                    "timeout has expired", date);
            this.setData(d, MonitoringClient.ERROR_CODE, -1);
          }
        }
      }
      timeout = true;
      notifyAll();
    } else {
      date.setTime(System.currentTimeMillis());
      consumers = 0;
    }

  }

  /**
   * Fill all the datas of a well known protocol with the error code and create
   * a general error.
   *
   * @param protocol  The name of the protocol.
   * @param message   The message of the error.
   */
  public synchronized void protocolError(String protocol, String message) {
    client.getErrorsBuffer().addError(protocol, message, date);
    for (Data d : datas) {
      if (d.isProtocol(protocol) && d.hasEmptyValue()) {
        this.setData(d, MonitoringClient.ERROR_CODE, 0);
      }
    }
    if (this.isFull()) {
      notifyAll();
    }
  }

  /**
   * Fill a data with the error code and create a new error.
   *
   * @param d       The data who has the error.
   * @param message The message of the error.
   */
  public synchronized void dataError(Data d, String message) {
    client.getErrorsBuffer().addError(d.getParameters().getProperty("protocol"),
            d.getAttribute(), message, date);
    this.setData(d, MonitoringClient.ERROR_CODE, 0);
  }

  /**
   * Return the datas retrieved by the producers.
   *
   * @return The datas retrieved by the producers.
   */
  public synchronized Vector<Data> getAllDatas () {
    return datas;
  }

  /**
   * Return the date of the datas.
   *
   * @return  The date of the datas.
   */
  public synchronized Date getDate () {
    return date;
  }

  /**
   * Clone the DatasBuffer and reset it if all the consumers have cloned it.
   *
   * @return  A clone of the DatasBuffer.
   */
  @Override
  public synchronized DatasBuffer clone () {
    DatasBuffer result = new DatasBuffer(client, new Date(date.getTime()));
    for (Data d : datas) {
      result.addData(d.getAttribute(), d.getParameters(), d.getValue(),
              d.getTimer());
    }
    if (++consumers == client.getNbConsumers()) {
      for (Data d : datas) {
        d.reset();
      }
      if (timeout) {
        consumers = 0;
        date.setTime(System.currentTimeMillis());
        timeout = false;
      }
    }
    return result;
  }

  /**
   * Return the index of the specified data.
   *
   * @param protocol  The name of the protocol used to retrieve the data.
   * @param attribute The name of the attibute monitored.
   * @return  The index of the specified data.
   */
  private int indexOf(String attribute, Properties parameters) {
    int i = 0;
    while(i<datas.size() &&
            (!datas.get(i).getAttribute().equals(attribute) ||
            !datas.get(i).getParameters().equals(parameters))) {
      i++;
    }
    return (i<datas.size() ? i : -1);
  }

  /**
   * Test if the DatasBuffer is full.
   *
   * @return true if the DatasBuffer is full; false otherwise.
   */
  public synchronized boolean isFull() {
    boolean result = true;
    for (Data d : datas) {
      if (d.hasEmptyValue()) {
        result = false;
      }
    }
    return result;
  }

}
