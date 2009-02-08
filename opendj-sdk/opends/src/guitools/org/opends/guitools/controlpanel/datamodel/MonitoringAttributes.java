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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.datamodel;

import org.opends.messages.Message;

/**
 * An interface defining the different methods required by the UI components
 * to display monitoring attributes data.
 *
 */
public interface MonitoringAttributes
{
  /**
   * Returns the message associated with the attribute (basically is the
   * localized name of the operation associated with the attribute).
   * @return the message associated with the attribute.
   */
  public Message getMessage();

  /**
   * Returns the name of the attribute.
   * @return the name of the attribute.
   */
  public String getAttributeName();

  /**
   * Tells whether this is the number of aborted operations.
   * @return <CODE>true</CODE> if this corresponds to the number of aborted
   * operations and <CODE>false</CODE> otherwise.
   */
  public boolean isAborted();

  /**
   * Return whether this attribute contains a numeric value or not.
   * @return <CODE>true</CODE> if the value is numeric and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isNumeric();

  /**
   * Return whether this attribute contains a time value or not.
   * @return <CODE>true</CODE> if the value is a time and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isTime();

  /**
   * Return whether this attribute contains a numeric date value or not.
   * The date is a long value in miliseconds.
   * @return <CODE>true</CODE> if the value is date and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isNumericDate();

  /**
   * Return whether this attribute contains a GMT date value or not.  The date
   * has a format of type ServerConstants.DATE_FORMAT_GMT_TIME.
   * @return <CODE>true</CODE> if the value is a GMT date and <CODE>false</CODE>
   * otherwise.
   */
  public boolean isGMTDate();

  /**
   * Return whether this attribute represents a value in bytes or not.
   * @return <CODE>true</CODE> if the value represents a value in bytes and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isValueInBytes();

  /**
   * Returns <CODE>true</CODE> if the average for this attribute makes sense
   * and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the average for this attribute makes sense
   * and <CODE>false</CODE> otherwise.
   */
  public boolean canHaveAverage();
}
