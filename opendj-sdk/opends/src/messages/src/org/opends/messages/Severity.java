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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.messages;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Defines values for message severity.  Severities contain an
 * integer value that can be used for bitwise operations as well
 * as a short abbreviated string form of each value.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=false,
    mayExtend=false,
    mayInvoke=true)
public enum Severity {

  /**
   * The severity that will be used for informational messages.
   */
  INFORMATION(0x00000000, "INFO", "INFO"),

  /**
   * The severity that will be used for mild warning messages.
   */
  MILD_WARNING(0x00010000, "MILD_WARN", "WARN"),

  /**
   * The severity that will be used for severe warning messages.
   */
  SEVERE_WARNING(0x00020000, "SEVERE_WARN", "WARN"),

  /**
   * The severity that will be used for mild error messages.
   */
  MILD_ERROR(0x00030000, "MILD_ERR", "ERR"),

  /**
   * The severity that will be used for severe error messages.
   */
  SEVERE_ERROR(0x00040000, "SEVERE_ERR", "ERR"),

  /**
   * The severity that will be used for fatal error messages.
   */
  FATAL_ERROR(0x00050000, "FATAL_ERR", "ERR"),

  /**
   * The severity that will be used for debug messages.
   */
  DEBUG(0x00060000, "DEBUG", "DEBUG"),

  /**
   * The severity that will be used for important informational
   * messages.
   */
  NOTICE(0x00070000, "NOTICE", "NOTE");

  static private Set<String> PROPERTY_KEY_FORM_VALUES_SET;

  static private Map<String,Severity> PROPERTY_KEY_FORM_MAP;

  static private Map<Integer,Severity> MASK_VALUE_MAP;

  static {
    MASK_VALUE_MAP = new HashMap<Integer,Severity>();
    for (Severity c : EnumSet.allOf(Severity.class)) {
      MASK_VALUE_MAP.put(c.mask, c);
    }
  }

  static {
    PROPERTY_KEY_FORM_MAP = new HashMap<String,Severity>();
    PROPERTY_KEY_FORM_VALUES_SET = new HashSet<String>();
    for (Severity s : EnumSet.allOf(Severity.class)) {
      PROPERTY_KEY_FORM_MAP.put(s.propertyKeyFormName(), s);
      PROPERTY_KEY_FORM_VALUES_SET.add(s.propertyKeyFormName());
    }
  }

  /**
   * Returns a set of string representing all <code>Severitys'</code>
   * abbreviated representations.
   * @return set of messageDescriptorForm strings
   */
  static public Set<String> getPropertyKeyFormSet() {
    return Collections.unmodifiableSet(PROPERTY_KEY_FORM_VALUES_SET);
  }

  /**
   * Obtains the <code>Severity</code> associated with a given mask
   * value.
   * @param mask for which a <code>Severity</code> is obtained.
   * @return Severity associated with <code>mask</code>
   */
  static public Severity parseMask(int mask) {
    Severity sev = MASK_VALUE_MAP.get(mask);
    if (sev == null) {
      throw new IllegalArgumentException(
              "No Severity defined with int value " + mask);
    }
    return sev;
  }

  /**
   * Returns the <code>Severity</code> associated with the input
   * string <code>s</code> which can either be a severity's name
   * or messageDescriptorForm.
   * @param s Severity name or messageDescriptorForm
   * @return Severity assocated with <code>s</code>
   */
  static public Severity parseString(String s) {
    Severity sev = PROPERTY_KEY_FORM_MAP.get(s);
    if (sev == null) {
      sev = valueOf(s);
    }
    return sev;
  }

  /**
   * Obtains the <code>Severity</code> associated with the the input
   * message ID <code>msgId</code>.
   * @param msgId int message ID
   * @return Severity assocated with the ID
   */
  static public Severity parseMessageId(int msgId) {
    return parseMask(msgId & 0x000F0000);
  }

  private final int mask;
  private final String propertyKeyForm;
  private final String messageDescriptorForm;

  /**
   * Returns the mask associated with this <code>Severity</code>.
   * @return mask for this severity
   */
  public int getMask() {
    return mask;
  }

  /**
   * Gets the abbreviated form of this <code>Severity</code>.
   * @return String abbreviated form
   */
  public String messageDesciptorName() {
    return messageDescriptorForm;
  }

  /**
   * Gets the name of this severity as it must appear in the
   * property key name in a messages file.
   *
   * @return name of this severity
   */
  public String propertyKeyFormName() {
    return propertyKeyForm;
  }

  private Severity(int mask, String propertyKeyForm,
                   String messageDescriptorName) {
    this.mask = mask;
    this.propertyKeyForm = propertyKeyForm;
    this.messageDescriptorForm = messageDescriptorName;
  }

}
