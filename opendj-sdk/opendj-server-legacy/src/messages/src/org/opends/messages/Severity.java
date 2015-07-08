/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
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
  INFORMATION("INFO"),

  /**
   * The severity that will be used for warning messages.
   */
  WARNING("WARN"),

  /**
   * The severity that will be used for warning messages.
   */
  ERROR("ERR"),

  /**
   * The severity that will be used for debug messages.
   */
  DEBUG("DEBUG"),

  /**
   * The severity that will be used for important informational
   * messages.
   */
  NOTICE("NOTE");

  private static Set<String> PROPERTY_KEY_FORM_VALUES_SET;

  private static Map<String,Severity> PROPERTY_KEY_FORM_MAP;

  static {
    PROPERTY_KEY_FORM_MAP = new HashMap<>();
    PROPERTY_KEY_FORM_VALUES_SET = new HashSet<>();
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
  public static Set<String> getPropertyKeyFormSet() {
    return Collections.unmodifiableSet(PROPERTY_KEY_FORM_VALUES_SET);
  }

  /**
   * Returns the <code>Severity</code> associated with the input
   * string <code>s</code> which can either be a severity's name
   * or messageDescriptorForm.
   * @param s Severity name or messageDescriptorForm
   * @return Severity associated with <code>s</code>
   */
  public static Severity parseString(String s) {
    Severity sev = PROPERTY_KEY_FORM_MAP.get(s);
    if (sev == null) {
      sev = valueOf(s);
    }
    return sev;
  }

  private final String name;

  /**
   * Gets the abbreviated form of this <code>Severity</code>.
   * @return String abbreviated form
   */
  public String messageDesciptorName() {
    return name;
  }

  /**
   * Gets the name of this severity as it must appear in the
   * property key name in a messages file.
   *
   * @return name of this severity
   */
  public String propertyKeyFormName() {
    return name;
  }

  private Severity(String propertyKeyForm) {
    this.name = propertyKeyForm;
  }

}
