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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import java.util.ResourceBundle;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;

/** Common methods for hooking in the test components. */
public final class TestCfg {

    /** A one-to-many relation between the root and test-parent components. */
    private static final InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> RD_TEST_ONE_TO_MANY_PARENT;

    /** A one-to-zero-or-one relation between the root and a test-parent component. */
    // @Checkstyle:off
    private static final OptionalRelationDefinition<TestParentCfgClient, TestParentCfg>
        RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;
    // @Checkstyle:on

    /** Create a one-to-many relation for test-parent components. */
    static {
        InstantiableRelationDefinition.Builder<TestParentCfgClient, TestParentCfg> builder =
            new InstantiableRelationDefinition.Builder<>(
                RootCfgDefn.getInstance(), "test-one-to-many-parent", "test-one-to-many-parents",
                TestParentCfgDefn.getInstance());
        RD_TEST_ONE_TO_MANY_PARENT = builder.getInstance();
    }

    /** Create a one-to-many relation for test-parent components. */
    static {
        OptionalRelationDefinition.Builder<TestParentCfgClient, TestParentCfg> builder =
            new OptionalRelationDefinition.Builder<>(
                RootCfgDefn.getInstance(), "test-one-to-zero-or-one-parent", TestParentCfgDefn.getInstance());
        RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT = builder.getInstance();
    }
    /**
     * Registers test parent and child object class definitions and any required
     * resource bundles.
     * <p>
     * Unit tests which call this method <b>must</b> call {@link #cleanup()} on
     * completion.
     *
     * @throws Exception
     *             If an unexpected error occurred.
     */
    public static synchronized void setUp() throws Exception {
        initializeAndRegisterBundle(TestParentCfgDefn.getInstance());
        initializeAndRegisterBundle(TestChildCfgDefn.getInstance());

        // Ensure that the relations are registered (do this after things
        // that can fail and leave tests in a bad state).
        RootCfgDefn.getInstance().registerRelationDefinition(RD_TEST_ONE_TO_MANY_PARENT);
        RootCfgDefn.getInstance().registerRelationDefinition(RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT);
        LDAPProfile.getInstance().pushWrapper(new MockLDAPProfile());
    }

    private static void initializeAndRegisterBundle(ManagedObjectDefinition<?, ?> definition) throws Exception {
        definition.initialize();
        String baseName = definition.getClass().getName();
        ResourceBundle resourceBundle = ResourceBundle.getBundle(baseName);
        ManagedObjectDefinitionI18NResource.getInstance().setResourceBundle(definition, resourceBundle);
    }

    /** Deregisters the test configurations from the administration framework. */
    public static synchronized void cleanup() {
        LDAPProfile.getInstance().popWrapper();

        AbstractManagedObjectDefinition<?, ?> root = RootCfgDefn.getInstance();
        root.deregisterRelationDefinition(RD_TEST_ONE_TO_MANY_PARENT);
        root.deregisterRelationDefinition(RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT);

        TestParentCfgDefn parentDef = TestParentCfgDefn.getInstance();
        ManagedObjectDefinitionI18NResource.getInstance().removeResourceBundle(parentDef);

        TestChildCfgDefn childDef = TestChildCfgDefn.getInstance();
        ManagedObjectDefinitionI18NResource.getInstance().removeResourceBundle(childDef);
    }

    /**
     * Gets the one-to-many relation between the root and test-parent
     * components.
     * <p>
     * Unit tests which call this method <b>must</b> have already called
     * {@link #setUp()}.
     *
     * @return Returns the one-to-many relation between the root and test-parent
     *         components.
     */
    // @Checkstyle:off
    public static InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>
        getTestOneToManyParentRelationDefinition() {
            return RD_TEST_ONE_TO_MANY_PARENT;
    }
    // @Checkstyle:on

    /**
     * Gets the one-to-zero-or-one relation between the root and a test-parent
     * component.
     * <p>
     * Unit tests which call this method <b>must</b> have already called
     * {@link #setUp()}.
     *
     * @return Returns the one-to-zero-or-one relation between the root and a
     *         test-parent component.
     */
    // @Checkstyle:off
    public static OptionalRelationDefinition<TestParentCfgClient, TestParentCfg>
        getTestOneToZeroOrOneParentRelationDefinition() {
            return RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;
    }
    // @Checkstyle:on

    /**
     * Initializes a property definition and its default behavior.
     *
     * @param propertyDef
     *            The property definition to be initialized.
     * @throws Exception
     *             If the property definition could not be initialized.
     */
    public static void initializePropertyDefinition(PropertyDefinition<?> propertyDef) throws Exception {
        propertyDef.initialize();
        propertyDef.getDefaultBehaviorProvider().initialize();
    }

    /**
     * Adds a constraint temporarily with test child definition.
     *
     * @param constraint
     *            The constraint.
     */
    public static void addConstraint(Constraint constraint) {
        TestChildCfgDefn.getInstance().registerConstraint(constraint);
    }

    /**
     * Adds a property definition temporarily with test child definition,
     * replacing any existing property definition with the same name.
     *
     * @param pd
     *            The property definition.
     */
    public static void addPropertyDefinition(PropertyDefinition<?> pd) {
        TestChildCfgDefn.getInstance().registerPropertyDefinition(pd);
    }

    /**
     * Removes a constraint from the test child definition.
     *
     * @param constraint
     *            The constraint.
     */
    public static void removeConstraint(Constraint constraint) {
        TestChildCfgDefn.getInstance().deregisterConstraint(constraint);
    }

    /** Prevent instantiation. */
    private TestCfg() {
        // No implementation required.
    }

}
