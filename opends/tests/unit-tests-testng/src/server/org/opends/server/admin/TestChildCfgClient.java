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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.Collection;
import java.util.SortedSet;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * A client-side interface for reading and modifying Test Child
 * settings.
 * <p>
 * A configuration for testing components that are subordinate to a
 * parent component. It re-uses the virtual-attribute configuration
 * LDAP profile.
 */
public interface TestChildCfgClient extends ConfigurationClient {

  /**
   * Get the configuration definition associated with this Test Child.
   *
   * @return Returns the configuration definition associated with this Test Child.
   */
  ManagedObjectDefinition<? extends TestChildCfgClient, ? extends TestChildCfg> definition();



  /**
   * Get the "aggregation-property" property.
   * <p>
   * An aggregation property which references connection handlers.
   *
   * @return Returns the values of the "aggregation-property" property.
   */
  SortedSet<String> getAggregationProperty();



  /**
   * Set the "aggregation-property" property.
   * <p>
   * An aggregation property which references connection handlers.
   *
   * @param values The values of the "aggregation-property" property.
   * @throws IllegalPropertyValueException
   *           If one or more of the new values are invalid.
   */
  void setAggregationProperty(Collection<String> values) throws IllegalPropertyValueException;



  /**
   * Get the "mandatory-boolean-property" property.
   * <p>
   * A mandatory boolean property.
   *
   * @return Returns the value of the "mandatory-boolean-property" property.
   */
  Boolean isMandatoryBooleanProperty();



  /**
   * Set the "mandatory-boolean-property" property.
   * <p>
   * A mandatory boolean property.
   *
   * @param value The value of the "mandatory-boolean-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMandatoryBooleanProperty(boolean value) throws IllegalPropertyValueException;



  /**
   * Get the "mandatory-class-property" property.
   * <p>
   * A mandatory Java-class property requiring a component restart.
   *
   * @return Returns the value of the "mandatory-class-property" property.
   */
  String getMandatoryClassProperty();



  /**
   * Set the "mandatory-class-property" property.
   * <p>
   * A mandatory Java-class property requiring a component restart.
   *
   * @param value The value of the "mandatory-class-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   */
  void setMandatoryClassProperty(String value) throws IllegalPropertyValueException;



  /**
   * Get the "mandatory-read-only-attribute-type-property" property.
   * <p>
   * A mandatory read-only attribute type property.
   *
   * @return Returns the value of the "mandatory-read-only-attribute-type-property" property.
   */
  AttributeType getMandatoryReadOnlyAttributeTypeProperty();



  /**
   * Set the "mandatory-read-only-attribute-type-property" property.
   * <p>
   * A mandatory read-only attribute type property.
   * <p>
   * This property is read-only and can only be modified during
   * creation of a Test Child.
   *
   * @param value The value of the "mandatory-read-only-attribute-type-property" property.
   * @throws IllegalPropertyValueException
   *           If the new value is invalid.
   * @throws PropertyIsReadOnlyException
   *           If this Test Child is not being initialized.
   */
  void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws IllegalPropertyValueException, PropertyIsReadOnlyException;



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
   * Set the "optional-multi-valued-dn-property1" property.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property in the parent.
   *
   * @param values The values of the "optional-multi-valued-dn-property1" property.
   * @throws IllegalPropertyValueException
   *           If one or more of the new values are invalid.
   */
  void setOptionalMultiValuedDNProperty1(Collection<DN> values) throws IllegalPropertyValueException;



  /**
   * Get the "optional-multi-valued-dn-property2" property.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property1.
   *
   * @return Returns the values of the "optional-multi-valued-dn-property2" property.
   */
  SortedSet<DN> getOptionalMultiValuedDNProperty2();



  /**
   * Set the "optional-multi-valued-dn-property2" property.
   * <p>
   * An optional multi-valued DN property which inherits its values
   * from optional-multi-valued-dn-property1.
   *
   * @param values The values of the "optional-multi-valued-dn-property2" property.
   * @throws IllegalPropertyValueException
   *           If one or more of the new values are invalid.
   */
  void setOptionalMultiValuedDNProperty2(Collection<DN> values) throws IllegalPropertyValueException;

}
