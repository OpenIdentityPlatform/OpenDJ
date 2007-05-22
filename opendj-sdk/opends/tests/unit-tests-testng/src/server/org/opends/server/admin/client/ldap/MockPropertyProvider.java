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
package org.opends.server.admin.client.ldap;



import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.util.Validator;



/**
 * A mock property provider which simplifies creation of managed
 * objects and LDAP attribute comparisons.
 */
public final class MockPropertyProvider implements PropertyProvider {

  // The properties.
  private final Map<String, List<String>> properties = new HashMap<String, List<String>>();



  /**
   * Default constructor.
   */
  public MockPropertyProvider() {
    // No implementation required.
  }



  /**
   * Add a property.
   *
   * @param name
   *          The name of the property.
   * @param values
   *          The property's values.
   */
  public void addProperty(String name, String... values) {
    Validator.ensureNotNull(name);
    Validator.ensureNotNull(values);
    Validator.ensureTrue(values.length > 0);
    properties.put(name, Arrays.asList(values));
  }



  /**
   * {@inheritDoc}
   */
  public <T> Collection<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    List<String> values = properties.get(d.getName());

    if (values == null) {
      return Collections.<T> emptySet();
    }

    List<T> encodedValues = new ArrayList<T>(values.size());
    for (String value : values) {
      encodedValues.add(d.decodeValue(value));
    }

    return encodedValues;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return properties.toString();
  }

}
