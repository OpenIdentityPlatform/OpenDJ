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

/**
 * Represent an error occured during the retrieving of the datas.
 */
public class Error {

  /**
   * The date of the error.
   */
  private final Date date;

  /**
   * The name of the protocol.
   */
  private final String protocol;

  /**
   * The name of the attribute who couldn't be retrieve.
   */
  private final String attribute;

  /**
   * The error message.
   */
  private final String message;

  /**
   * The constructor of the Error object.
   *
   * @param protocol  The name of the protocol
   * @param attribute The name of the attribute who couldn't be retrieve.
   * @param message   The error message.
   */
  public Error (String protocol, String attribute, String message) {
    this.date =  new Date();
    this.protocol = protocol;
    this.attribute = attribute;
    this.message = message;
  }

  /**
   * The constructor of the Error object.
   *
   * @param protocol  The name of the protocol
   * @param attribute The name of the attribute who couldn't be retrieve.
   * @param message   The error message.
   * @param date      The date of the error
   */
  public Error (String protocol, String attribute, String message, Date date) {
    this.date =  (Date)date.clone();
    this.protocol = protocol;
    this.attribute = attribute;
    this.message = message;
  }

  /**
   * Returns the date.
   *
   * @return  The date of the error.
   */
  public Date getDate () {
    return date;
  }

  /**
   * Returns the protocol.
   *
   * @return The name of the protocol.
   */
  public String getProtocol () {
    return protocol;
  }

  /**
   * Returns the attribute.
   *
   * @return  The attribute who couldn't be retrieve.
   */
  public String getAttribute () {
    return attribute;
  }

  /**
   * Returns the error message.
   * @return  The error message.
   */
  public String getMessage () {
    return message;
  }
}
