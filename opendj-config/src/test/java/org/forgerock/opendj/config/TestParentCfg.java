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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.SortedSet;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * A server-side interface for querying Test Parent settings.
 * <p>
 * A configuration for testing components that have child components. It re-uses
 * the virtual-attribute configuration LDAP profile.
 */
public interface TestParentCfg extends Configuration {

    /**
     * Get the configuration class associated with this Test Parent.
     *
     * @return Returns the configuration class associated with this Test Parent.
     */
    @Override
    Class<? extends TestParentCfg> configurationClass();

    /**
     * Register to be notified when this Test Parent is changed.
     *
     * @param listener
     *            The Test Parent configuration change listener.
     */
    void addChangeListener(ConfigurationChangeListener<TestParentCfg> listener);

    /**
     * Deregister an existing Test Parent configuration change listener.
     *
     * @param listener
     *            The Test Parent configuration change listener.
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
     * @return Returns the value of the
     *         "mandatory-read-only-attribute-type-property" property.
     */
    AttributeType getMandatoryReadOnlyAttributeTypeProperty();

    /**
     * Get the "optional-multi-valued-dn-property" property.
     * <p>
     * An optional multi-valued DN property with a defined default behavior.
     *
     * @return Returns the values of the "optional-multi-valued-dn-property"
     *         property.
     */
    SortedSet<DN> getOptionalMultiValuedDNProperty();

    /**
     * Lists the Test Children.
     *
     * @return Returns an array containing the names of the Test Children.
     */
    String[] listTestChildren();

    /**
     * Gets the named Test Child.
     *
     * @param name
     *            The name of the Test Child to retrieve.
     * @return Returns the named Test Child.
     * @throws ConfigException
     *             If the Test Child could not be found or it could not be
     *             successfully decoded.
     */
    TestChildCfg getTestChild(String name) throws ConfigException;

    /**
     * Registers to be notified when new Test Children are added.
     *
     * @param listener
     *            The Test Child configuration add listener.
     * @throws ConfigException
     *             If the add listener could not be registered.
     */
    void addTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) throws ConfigException;

    /**
     * Deregisters an existing Test Child configuration add listener.
     *
     * @param listener
     *            The Test Child configuration add listener.
     */
    void removeTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener);

    /**
     * Registers to be notified when existing Test Children are deleted.
     *
     * @param listener
     *            The Test Child configuration delete listener.
     * @throws ConfigException
     *             If the delete listener could not be registered.
     */
    void addTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException;

    /**
     * Deregisters an existing Test Child configuration delete listener.
     *
     * @param listener
     *            The Test Child configuration delete listener.
     */
    void removeTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener);

    /**
     * Determines whether the Optional Test Child exists.
     *
     * @return Returns <true> if the Optional Test Child exists.
     */
    boolean hasOptionalTestChild();

    /**
     * Gets the Optional Test Child if it is present.
     *
     * @return Returns the Optional Test Child if it is present.
     * @throws ConfigException
     *             If the Optional Test Child does not exist or it could not be
     *             successfully decoded.
     */
    TestChildCfg getOptionalTestChild() throws ConfigException;

    /**
     * Registers to be notified when the Optional Test Child is added.
     *
     * @param listener
     *            The Optional Test Child configuration add listener.
     * @throws ConfigException
     *             If the add listener could not be registered.
     */
    void addOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener) throws ConfigException;

    /**
     * Deregisters an existing Optional Test Child configuration add listener.
     *
     * @param listener
     *            The Optional Test Child configuration add listener.
     */
    void removeOptionalTestChildAddListener(ConfigurationAddListener<TestChildCfg> listener);

    /**
     * Registers to be notified the Optional Test Child is deleted.
     *
     * @param listener
     *            The Optional Test Child configuration delete listener.
     * @throws ConfigException
     *             If the delete listener could not be registered.
     */
    void addOptionalChildTestDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener) throws ConfigException;

    /**
     * Deregisters an existing Optional Test Child configuration delete
     * listener.
     *
     * @param listener
     *            The Optional Test Child configuration delete listener.
     */
    void removeOptionalTestChildDeleteListener(ConfigurationDeleteListener<TestChildCfg> listener);

}
