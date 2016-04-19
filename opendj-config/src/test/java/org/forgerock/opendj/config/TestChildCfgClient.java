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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.Collection;
import java.util.SortedSet;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * A client-side interface for reading and modifying Test Child settings.
 * <p>
 * A configuration for testing components that are subordinate to a parent
 * component. It re-uses the virtual-attribute configuration LDAP profile.
 */
public interface TestChildCfgClient extends ConfigurationClient {

    /**
     * Get the configuration definition associated with this Test Child.
     *
     * @return Returns the configuration definition associated with this Test
     *         Child.
     */
    @Override
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
     * @param values
     *            The values of the "aggregation-property" property.
     * @throws PropertyException
     *             If one or more of the new values are invalid.
     */
    void setAggregationProperty(Collection<String> values) throws PropertyException;

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
     * @param value
     *            The value of the "mandatory-boolean-property" property.
     * @throws PropertyException
     *             If the new value is invalid.
     */
    void setMandatoryBooleanProperty(boolean value) throws PropertyException;

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
     * @param value
     *            The value of the "mandatory-class-property" property.
     * @throws PropertyException
     *             If the new value is invalid.
     */
    void setMandatoryClassProperty(String value) throws PropertyException;

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
     * Set the "mandatory-read-only-attribute-type-property" property.
     * <p>
     * A mandatory read-only attribute type property.
     * <p>
     * This property is read-only and can only be modified during creation of a
     * Test Child.
     *
     * @param value
     *            The value of the "mandatory-read-only-attribute-type-property"
     *            property.
     * @throws PropertyException
     *             If the new value is invalid.
     * @throws PropertyException
     *             If this Test Child is not being initialized.
     */
    void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws PropertyException,
            PropertyException;

    /**
     * Get the "optional-multi-valued-dn-property1" property.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property in the parent.
     *
     * @return Returns the values of the "optional-multi-valued-dn-property1"
     *         property.
     */
    SortedSet<DN> getOptionalMultiValuedDNProperty1();

    /**
     * Set the "optional-multi-valued-dn-property1" property.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property in the parent.
     *
     * @param values
     *            The values of the "optional-multi-valued-dn-property1"
     *            property.
     * @throws PropertyException
     *             If one or more of the new values are invalid.
     */
    void setOptionalMultiValuedDNProperty1(Collection<DN> values) throws PropertyException;

    /**
     * Get the "optional-multi-valued-dn-property2" property.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property1.
     *
     * @return Returns the values of the "optional-multi-valued-dn-property2"
     *         property.
     */
    SortedSet<DN> getOptionalMultiValuedDNProperty2();

    /**
     * Set the "optional-multi-valued-dn-property2" property.
     * <p>
     * An optional multi-valued DN property which inherits its values from
     * optional-multi-valued-dn-property1.
     *
     * @param values
     *            The values of the "optional-multi-valued-dn-property2"
     *            property.
     * @throws PropertyException
     *             If one or more of the new values are invalid.
     */
    void setOptionalMultiValuedDNProperty2(Collection<DN> values) throws PropertyException;

}
