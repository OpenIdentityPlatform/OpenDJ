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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.SortedSet;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * A server-side interface for querying Test Child settings.
 * <p>
 * A configuration for testing components that are subordinate to a
 * parent component. It re-uses the virtual-attribute configuration
 * LDAP profile.
 */
public interface TestChildCfg extends Configuration {

  /**
   * Get the configuration class associated with this Test Child.
   *
   * @return Returns the configuration class associated with this Test Child.
   */
  Class<? extends TestChildCfg> configurationClass();



  /**
   * Register to be notified when this Test Child is changed.
   *
   * @param listener
   *          The Test Child configuration change listener.
   */
  void addChangeListener(ConfigurationChangeListener<TestChildCfg> listener);



  /**
   * Deregister an existing Test Child configuration change listener.
   *
   * @param listener
   *          The Test Child configuration change listener.
   */
  void removeChangeListener(ConfigurationChangeListener<TestChildCfg> listener);



  /**
   * Get the "aggregation-property" property.
   * <p>
   * An aggregation property which references connection handlers.
   *
   * @return Returns the values of the "aggregation-property" property.
   */
  SortedSet<String> getAggregationProperty();



  /**
   * Get the "mandatory-boolean-property" property.
   * <p>
   * A mandatory boolean property.
   *
   * @return Returns the value of the "mandatory-boolean-property" property.
   */
  boolean isMandatoryBooleanProperty();



  /**
   * Get the "mandatory-class-property" property.
   * <p>
   * A mandatory Java-class property requiring a component restart.
   *
   * @return Returns the value of the "mandatory-class-property" property.
   */
  String getMandatoryClassProperty();



  /**
   * Get the "mandatory-read-only-attribute-type-property" property.
   * <p>
   * A mandatory read-only attribute type property.
   *
   * @return Returns the value of the "mandatory-read-only-attribute-type-property" property.
   */
  AttributeType getMandatoryReadOnlyAttributeTypeProperty();



  /**
   * Get the "optional-multi-valued-dn-property1" property.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property in the parent.
   *
   * @return Returns the values of the "optional-multi-valued-dn-property1" property.
   */
  SortedSet<DN> getOptionalMultiValuedDNProperty1();



  /**
   * Get the "optional-multi-valued-dn-property2" property.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property1.
   *
   * @return Returns the values of the "optional-multi-valued-dn-property2" property.
   */
  SortedSet<DN> getOptionalMultiValuedDNProperty2();

}
