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

import java.util.Properties;

/**
 * The classe Data represent an attribute monitored by a producer.
 */
public class Data {

  /**
   * Name of the attibute monitored.
   */
  private final String attribute;

  private final Properties parameters;

  /**
   * Value of the attribute monitored.
   */
  private String value;

  /**
   * Number of milliseconds beetween the launch of the request and the creation
   * of the data (default 0). If the data isn't complete at the end of the
   * interval, the timer value is set to -1.
   */
  private int timer;

  /**
   * Create a data without value and timer.
   *
   * @param attribute   the name of the attibute monitored.
   * @param parameters  the parameters to monitor the attribute.
   */
  public Data (String attribute, Properties parameters) {
    this.attribute = attribute;
    this.parameters = (Properties)parameters.clone();
    this.value = "";
    this.timer = 0;
  }

  /**
   * Reset a data.
   */
  public void reset () {
    value = "";
    if (timer != -1) {
      timer = 0;
    }
  }

  /**
   * Return the name of the attribute monitored.
   *
   * @return  The name of the attribute monitored.
   */
  public String getAttribute () {
    return attribute;
  }

  /**
   * Return the parameters of the data.
   *
   * @return the parameters of the data.
   */
  public Properties getParameters () {
    return parameters;
  }

  /**
   * Return the name of the protocol used to retrieve the data.
   *
   * @return  The name of the protocol used to retrieve the data.
   */
  public String getProtocol () {
    return this.parameters.getProperty("protocol");
  }

  /**
   * Test is the specified protocol is the protocol of the data.
   *
   * @param protocol  Name of the protocol.
   * @return true if the protocols are equals; false otherwise.
   */
  public boolean isProtocol (String protocol) {
    return this.parameters.getProperty("protocol").equals(protocol);
  }

  /**
   * Return the value of the attribute monitored.
   *
   * @return  The value of the attribute monitored.
   */
  public String getValue () {
    return value;
  }

  /**
   * Sets the value of the attribute monitored.
   *
   * @param value The value of the attribute monitored.
   */
  public void setValue (String value) {
    this.value = value;
  }

  /**
   * Test if the data has no value.
   *
   * @return true if the value equals the empty strings; false otherwise.
   */
  public boolean hasEmptyValue() {
    return value.equals("");
  }

  /**
   * Return the timer of the data.
   *
   * @return  The Number of milliseconds beetween the launch of the request and
   * the creation of the data.
   */
  public int getTimer () {
    return timer;
  }

  /**
   * Set the timer of the data.
   *
   * @param timer Number of milliseconds beetween the launch of the request and
   * the creation of the data.
   */
  public void setTimer (int timer) {
    this.timer = timer;
  }

}
