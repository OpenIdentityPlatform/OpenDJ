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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.ClassPropertyDefinition;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.TopCfgDefn;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Checks that the default value of every class property in the configuration definitions names
 * a class the server ships.
 * <p>
 * {@code java-class} is mandatory, so {@code dsconfig} writes its default into every entry it
 * creates without an explicit value. The client does not load the class, so a default naming a
 * class that does not exist is only noticed when the server refuses the new entry.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class DefaultClassPropertyValuesTestCase extends DirectoryServerTestCase {

    /** Definitions whose default class is not part of the code base, with the reason. */
    private static final Map<String, String> DEFINITIONS_WITHOUT_A_SHIPPED_DEFAULT_CLASS = new HashMap<>();
    static {
        DEFINITIONS_WITHOUT_A_SHIPPED_DEFAULT_CLASS.put("static-service-discovery-mechanism",
            "The proxy backend these mechanisms belong to is not part of this code base.");
        DEFINITIONS_WITHOUT_A_SHIPPED_DEFAULT_CLASS.put("replication-service-discovery-mechanism",
            "The proxy backend these mechanisms belong to is not part of this code base.");
    }

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
    }

    @DataProvider
    public Object[][] defaultClasses() {
        List<Object[]> rows = new ArrayList<>();
        Set<PropertyDefinition<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractManagedObjectDefinition<?, ?> definition : TopCfgDefn.getInstance().getAllChildren()) {
            if (DEFINITIONS_WITHOUT_A_SHIPPED_DEFAULT_CLASS.containsKey(definition.getName())) {
                continue;
            }
            for (PropertyDefinition<?> property : definition.getAllPropertyDefinitions()) {
                if (property instanceof ClassPropertyDefinition
                        && property.getDefaultBehaviorProvider() instanceof DefinedDefaultBehaviorProvider
                        && seen.add(property)) {
                    for (String className
                            : ((DefinedDefaultBehaviorProvider<?>) property.getDefaultBehaviorProvider())
                                .getDefaultValues()) {
                        rows.add(new Object[] { definition.getName(), property.getName(), className.trim(),
                            property });
                    }
                }
            }
        }
        return rows.toArray(new Object[rows.size()][]);
    }

    @Test(dataProvider = "defaultClasses")
    public void theDefaultClassLoadsAndImplementsTheRequiredInterfaces(String definitionName, String propertyName,
            String className, ClassPropertyDefinition property) {
        property.loadClass(className, Object.class);
    }

    @Test
    public void everyDefinitionLeftOutExists() {
        List<String> names = new ArrayList<>();
        for (AbstractManagedObjectDefinition<?, ?> definition : TopCfgDefn.getInstance().getAllChildren()) {
            names.add(definition.getName());
        }
        assertThat(names).containsAll(DEFINITIONS_WITHOUT_A_SHIPPED_DEFAULT_CLASS.keySet());
    }
}
