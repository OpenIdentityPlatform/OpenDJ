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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin;



import java.util.SortedSet;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.config.ConfigException;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * A server-side interface for querying Test Parent settings.
 * <p>
 * A configuration for testing components that have child components.
 * It re-uses the virtual-attribute configuration LDAP profile.
 */
public interface TestParentCfg extends Configuration {

  /**
   * Get the configuration class associated with this Test Parent.
   *
   * @return Returns the configuration class associated with this Test Parent.
   */
  Class<? extends TestParentCfg> configurationClass();



  /**
   * Register to be notified when this Test Parent is changed.
   *
   * @param listener
   *          The Test Parent configuration change listener.
   */
  void addChangeListener(ConfigurationChangeListener<TestParentCfg> listener);



  /**
   * Deregister an existing Test Parent configuration change listener.
   *
   * @param listener
   *          The Test Parent configuration change listener.
   */
  void removeChangeListener(ConfigurationChangeListener<TestParentCfg> listener);



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
   * Get the "optional-multi-valued-dn-property" property.
   * <p>
   * An optional multi-valued DN property with a defined default
   * behavior.
   *
   * @return Returns the values of the "optional-multi-valued-dn-property" property.
   */
  SortedSet<DN> getOptionalMultiValuedDNProperty();



  /**
   * Lists the Test Children.
   *
   * @return Returns an array containing the names of the
   *         Test Children.
   */
  String[] listTestChildren();



  /**
   * Gets the named Test Child.
   *
   * @param name
   *          The name of the Test Child to retrieve.
   * @return Returns the named Test Child.
   * @throws ConfigException
   *           If the Test Child could not be found or it
   *           could not be successfully decoded.
   */
  TestChildCfg getTestChild(String name) throws ConfigException;



  /**
   * Registers to be notified when new Test Children are added.
   *
   * @param listener
   *          The Test Child configuration add listener.
   * @throws ConfigException
   *          If the add listener could not be registered.
   */
  void addTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) throws ConfigException;



  /**
   * Deregisters an existing Test Child configuration add listener.
   *
   * @param listener
   *          The Test Child configuration add listener.
   */
  void removeTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener);



  /**
   * Registers to be notified when existing Test Children are deleted.
   *
   * @param listener
   *          The Test Child configuration delete listener.
   * @throws ConfigException
   *          If the delete listener could not be registered.
   */
  void addTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException;



  /**
   * Deregisters an existing Test Child configuration delete listener.
   *
   * @param listener
   *          The Test Child configuration delete listener.
   */
  void removeTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener);



  /**
   * Determines whether or not the Optional Test Child exists.
   *
   * @return Returns <true> if the Optional Test Child exists.
   */
  boolean hasOptionalTestChild();



  /**
   * Gets the Optional Test Child if it is present.
   *
   * @return Returns the Optional Test Child if it is present.
   * @throws ConfigException
   *           If the Optional Test Child does not exist or it could not
   *           be successfully decoded.
   */
  TestChildCfg getOptionalTestChild() throws ConfigException;



  /**
   * Registers to be notified when the Optional Test Child is added.
   *
   * @param listener
   *          The Optional Test Child configuration add listener.
   * @throws ConfigException
   *          If the add listener could not be registered.
   */
  void addOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) throws ConfigException;



  /**
   * Deregisters an existing Optional Test Child configuration add listener.
   *
   * @param listener
   *          The Optional Test Child configuration add listener.
   */
  void removeOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener);



  /**
   * Registers to be notified the Optional Test Child is deleted.
   *
   * @param listener
   *          The Optional Test Child configuration delete listener.
   * @throws ConfigException
   *          If the delete listener could not be registered.
   */
  void addOptionalChildTestDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException;



  /**
   * Deregisters an existing Optional Test Child configuration delete listener.
   *
   * @param listener
   *          The Optional Test Child configuration delete listener.
   */
  void removeOptionalTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener);

}
