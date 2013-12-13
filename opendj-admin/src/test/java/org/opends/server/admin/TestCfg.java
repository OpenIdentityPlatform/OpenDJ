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
 */
package org.opends.server.admin;

import java.util.ResourceBundle;

import org.forgerock.opendj.admin.meta.RootCfgDefn;

/**
 * Common methods for hooking in the test components.
 */
public final class TestCfg {

    /**
     * A one-to-many relation between the root and test-parent components.
     */
    private static final InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg> RD_TEST_ONE_TO_MANY_PARENT;

    /**
     * A one-to-zero-or-one relation between the root and a test-parent
     * component.
     */
    private static final OptionalRelationDefinition<TestParentCfgClient, TestParentCfg>
        RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;

    // Create a one-to-many relation for test-parent components.
    static {
        InstantiableRelationDefinition.Builder<TestParentCfgClient, TestParentCfg> builder =
            new InstantiableRelationDefinition.Builder<TestParentCfgClient, TestParentCfg>(
                RootCfgDefn.getInstance(), "test-one-to-many-parent", "test-one-to-many-parents",
                TestParentCfgDefn.getInstance());
        RD_TEST_ONE_TO_MANY_PARENT = builder.getInstance();
    }

    // Create a one-to-many relation for test-parent components.
    static {
        OptionalRelationDefinition.Builder<TestParentCfgClient, TestParentCfg> builder =
            new OptionalRelationDefinition.Builder<TestParentCfgClient, TestParentCfg>(
                RootCfgDefn.getInstance(), "test-one-to-zero-or-one-parent", TestParentCfgDefn.getInstance());
        RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT = builder.getInstance();
    }

    //private static ObjectClass TEST_PARENT_OBJECTCLASS = null;
    //private static ObjectClass TEST_CHILD_OBJECTCLASS = null;

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
    public synchronized static void setUp() throws Exception {
//        SchemaBuilder schemaBuilder = new SchemaBuilder(Schema.getDefaultSchema());
//        if (TEST_PARENT_OBJECTCLASS == null || TEST_CHILD_OBJECTCLASS == null) {
//            String def1 = "( 1.3.6.1.4.1.26027.1.2.4455114401 " + "NAME 'ds-cfg-test-parent-dummy' "
//                    + "SUP top STRUCTURAL " + "MUST ( cn $ ds-cfg-java-class $ "
//                    + "ds-cfg-enabled $ ds-cfg-attribute-type ) " + "MAY ( ds-cfg-base-dn $ ds-cfg-group-dn $ "
//                    + "ds-cfg-filter $ ds-cfg-conflict-behavior ) " + "X-ORIGIN 'OpenDS Directory Server' )";
//            schemaBuilder.addObjectClass(def1, false);
//
//            String def2 = "( 1.3.6.1.4.1.26027.1.2.4455114402 " + "NAME 'ds-cfg-test-child-dummy' "
//                    + "SUP top STRUCTURAL " + "MUST ( cn $ ds-cfg-java-class $ "
//                    + "ds-cfg-enabled $ ds-cfg-attribute-type ) " + "MAY ( ds-cfg-base-dn $ ds-cfg-group-dn $ "
//                    + "ds-cfg-filter $ ds-cfg-conflict-behavior $" + "ds-cfg-rotation-policy) "
//                    + "X-ORIGIN 'OpenDS Directory Server' )";
//            schemaBuilder.addObjectClass(def2, false);
//            Schema schema = schemaBuilder.toSchema();
//            TEST_PARENT_OBJECTCLASS = schema.getObjectClass("ds-cfg-test-parent-dummy");
//            TEST_CHILD_OBJECTCLASS = schema.getObjectClass("ds-cfg-test-child-dummy");
//        }


        {
            // Register the test parent resource bundle.
            TestParentCfgDefn def = TestParentCfgDefn.getInstance();
            def.initialize();
            String baseName = def.getClass().getName();
            ResourceBundle resourceBundle = ResourceBundle.getBundle(baseName);
            ManagedObjectDefinitionI18NResource.getInstance().setResourceBundle(def, resourceBundle);
        }

        {
            // Register the test child resource bundle.
            TestChildCfgDefn def = TestChildCfgDefn.getInstance();
            def.initialize();
            String baseName = def.getClass().getName();
            ResourceBundle resourceBundle = ResourceBundle.getBundle(baseName);
            ManagedObjectDefinitionI18NResource.getInstance().setResourceBundle(def, resourceBundle);
        }

        // Ensure that the relations are registered (do this after things
        // that can fail and leave tests in a bad state).
        RootCfgDefn.getInstance().registerRelationDefinition(RD_TEST_ONE_TO_MANY_PARENT);
        RootCfgDefn.getInstance().registerRelationDefinition(RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT);
        LDAPProfile.getInstance().pushWrapper(new MockLDAPProfile());
    }

    /**
     * Deregisters the test configurations from the administration framework.
     */
    public static void cleanup() {
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
    public static InstantiableRelationDefinition<TestParentCfgClient, TestParentCfg>
        getTestOneToManyParentRelationDefinition() {
            return RD_TEST_ONE_TO_MANY_PARENT;
    }

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
    public static OptionalRelationDefinition<TestParentCfgClient, TestParentCfg>
        getTestOneToZeroOrOneParentRelationDefinition() {
            return RD_TEST_ONE_TO_ZERO_OR_ONE_PARENT;
    }

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

    // Prevent instantiation.
    private TestCfg() {
        // No implementation required.
    }

}
