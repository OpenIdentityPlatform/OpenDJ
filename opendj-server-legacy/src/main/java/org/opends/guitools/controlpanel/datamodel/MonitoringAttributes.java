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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.datamodel;

import org.forgerock.i18n.LocalizableMessage;

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
  LocalizableMessage getMessage();

  /**
   * Returns the name of the attribute.
   * @return the name of the attribute.
   */
  String getAttributeName();

  /**
   * Return whether this attribute contains a numeric value or not.
   * @return <CODE>true</CODE> if the value is numeric and <CODE>false</CODE>
   * otherwise.
   */
  boolean isNumeric();

  /**
   * Return whether this attribute contains a time value or not.
   * @return <CODE>true</CODE> if the value is a time and <CODE>false</CODE>
   * otherwise.
   */
  boolean isTime();

  /**
   * Return whether this attribute contains a numeric date value or not.
   * The date is a long value in milliseconds.
   * @return <CODE>true</CODE> if the value is date and <CODE>false</CODE>
   * otherwise.
   */
  boolean isNumericDate();

  /**
   * Return whether this attribute contains a GMT date value or not.  The date
   * has a format of type ServerConstants.DATE_FORMAT_GMT_TIME.
   * @return <CODE>true</CODE> if the value is a GMT date and <CODE>false</CODE>
   * otherwise.
   */
  boolean isGMTDate();

  /**
   * Return whether this attribute represents a value in bytes or not.
   * @return <CODE>true</CODE> if the value represents a value in bytes and
   * <CODE>false</CODE> otherwise.
   */
  boolean isValueInBytes();

  /**
   * Returns <CODE>true</CODE> if the average for this attribute makes sense
   * and <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the average for this attribute makes sense
   * and <CODE>false</CODE> otherwise.
   */
  boolean canHaveAverage();
}
