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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.Collection;
import java.util.SortedSet;

import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.IllegalManagedObjectNameException;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.schema.AttributeType;

/**
 * A client-side interface for reading and modifying Test Parent settings.
 * <p>
 * A configuration for testing components that have child components. It re-uses
 * the virtual-attribute configuration LDAP profile.
 */
public interface TestParentCfgClient extends ConfigurationClient {

    /**
     * Get the configuration definition associated with this Test Parent.
     *
     * @return Returns the configuration definition associated with this Test
     *         Parent.
     */
    @Override
    ManagedObjectDefinition<? extends TestParentCfgClient, ? extends TestParentCfg> definition();

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
     * Test Parent.
     *
     * @param value
     *            The value of the "mandatory-read-only-attribute-type-property"
     *            property.
     * @throws PropertyException
     *             If the new value is invalid.
     * @throws PropertyException
     *             If this Test Parent is not being initialized.
     */
    void setMandatoryReadOnlyAttributeTypeProperty(AttributeType value) throws PropertyException,
            PropertyException;

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
     * Set the "optional-multi-valued-dn-property" property.
     * <p>
     * An optional multi-valued DN property with a defined default behavior.
     *
     * @param values
     *            The values of the "optional-multi-valued-dn-property"
     *            property.
     * @throws PropertyException
     *             If one or more of the new values are invalid.
     */
    void setOptionalMultiValuedDNProperty(Collection<DN> values) throws PropertyException;

    /**
     * Lists the Test Children.
     *
     * @return Returns an array containing the names of the Test Children.
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If an error occurs
     */
    String[] listTestChildren() throws ConcurrentModificationException, LdapException;

    /**
     * Gets the named Test Child.
     *
     * @param name
     *            The name of the Test Child to retrieve.
     * @return Returns the named Test Child.
     * @throws DefinitionDecodingException
     *             If the named Test Child was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the named Test Child was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the named Test Child was not found on the server.
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If an error occurs.
     */
    TestChildCfgClient getTestChild(String name) throws DefinitionDecodingException, ManagedObjectDecodingException,
            ManagedObjectNotFoundException, ConcurrentModificationException, LdapException;

    /**
     * Creates a new Test Child. The new Test Child will initially not contain
     * any property values (including mandatory properties). Once the Test Child
     * has been configured it can be added to the server using the
     * {@link #commit()} method.
     *
     * @param <C>
     *            The type of the Test Child being created.
     * @param d
     *            The definition of the Test Child to be created.
     * @param name
     *            The name of the new Test Child.
     * @param exceptions
     *            An optional collection in which to place any
     *            {@link PropertyException}s that occurred whilst
     *            attempting to determine the default values of the Test Child.
     *            This argument can be <code>null<code>.
     * @return Returns a new Test Child configuration instance.
     * @throws IllegalManagedObjectNameException
     *             If the name is invalid.
     */
    <C extends TestChildCfgClient> C createTestChild(ManagedObjectDefinition<C, ? extends TestChildCfg> d, String name,
            Collection<PropertyException> exceptions) throws IllegalManagedObjectNameException;

    /**
     * Removes the named Test Child.
     *
     * @param name
     *            The name of the Test Child to remove.
     * @throws ManagedObjectNotFoundException
     *             If the Test Child does not exist.
     * @throws OperationRejectedException
     *             If the server refuses to remove the Test Child due to some
     *             server-side constraint which cannot be satisfied (for
     *             example, if it is referenced by another managed object).
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *          If an errors occurs.
     */
    void removeTestChild(String name) throws ManagedObjectNotFoundException, OperationRejectedException,
            ConcurrentModificationException, LdapException;

    /**
     * Determines whether the Optional Test Child exists.
     *
     * @return Returns <true> if the Optional Test Child exists.
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *          If an errors occurs.
     */
    boolean hasOptionalTestChild() throws ConcurrentModificationException, LdapException;

    /**
     * Gets the Optional Test Child if it is present.
     *
     * @return Returns the Optional Test Child if it is present.
     * @throws DefinitionDecodingException
     *             If the Optional Test Child was found but its type could not
     *             be determined.
     * @throws ManagedObjectDecodingException
     *             If the Optional Test Child was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the Optional Test Child is not present.
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If an errors occurs.
     */
    TestChildCfgClient getOptionalChild() throws DefinitionDecodingException, ManagedObjectDecodingException,
            ManagedObjectNotFoundException, ConcurrentModificationException, LdapException;

    /**
     * Creates a new Optional Test Child. The new Optional Test Child will
     * initially not contain any property values (including mandatory
     * properties). Once the Optional Test Child has been configured it can be
     * added to the server using the {@link #commit()} method.
     *
     * @param <C>
     *            The type of the Optional Test Child being created.
     * @param d
     *            The definition of the Optional Test Child to be created.
     * @param exceptions
     *            An optional collection in which to place any
     *            {@link PropertyException}s that occurred whilst
     *            attempting to determine the default values of the Optional
     *            Test Child. This argument can be <code>null<code>.
     * @return Returns a new Optional Test Child configuration instance.
     */
    <C extends TestChildCfgClient> C createOptionalTestChild(ManagedObjectDefinition<C, ? extends TestChildCfg> d,
            Collection<PropertyException> exceptions);

    /**
     * Removes the Optional Test Child if it exists.
     *
     * @throws ManagedObjectNotFoundException
     *             If the Optional Test Child does not exist.
     * @throws OperationRejectedException
     *             If the server refuses to remove the Optional Test Child due
     *             to some server-side constraint which cannot be satisfied (for
     *             example, if it is referenced by another managed object).
     * @throws ConcurrentModificationException
     *             If this Test Parent has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If an errors occurs.
     */
    void removeOptionalTestChild() throws ManagedObjectNotFoundException, OperationRejectedException,
            ConcurrentModificationException, LdapException;

}
