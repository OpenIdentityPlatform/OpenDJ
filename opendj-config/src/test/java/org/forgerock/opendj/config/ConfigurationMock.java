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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.mockito.internal.stubbing.defaultanswers.ReturnsEmptyValues;
import org.mockito.invocation.InvocationOnMock;

/**
 * Provides Mockito mocks for Configuration objects with default values
 * corresponding to those defined in xml configuration files.
 * <p>
 * These mocks can be used like any other mocks, e.g, you can define stubs using
 * {@code when} method or verify calls using {@code verify} method.
 * <p>
 * Example:
 *
 * <pre>
 *  LDAPConnectionHandlerCfg mockCfg = mockCfg(LDAPConnectionHandlerCfg.class);
 *  assertThat(mockCfg.getMaxRequestSize()).isEqualTo(5 * 1000 * 1000);
 * </pre>
 */
public final class ConfigurationMock {

    private static final ConfigAnswer CONFIG_ANSWER = new ConfigAnswer();

    /**
     * Returns a mock for the provided configuration class.
     * <p>
     * If a setting has a default value, the mock automatically returns the
     * default value when the getter is called on the setting.
     * <p>
     * It is possible to override this default behavior with the usual methods
     * calls with Mockito (e.g, {@code when} method).
     *
     * @param <T>
     *            The type of configuration.
     * @param configClass
     *            The configuration class.
     * @return a mock
     */
    public static <T extends Configuration> T mockCfg(Class<T> configClass) {
        return mock(configClass, CONFIG_ANSWER);
    }

    /**
     * A stubbed answer for Configuration objects, allowing to return default
     * value for settings when available.
     */
    private static class ConfigAnswer extends ReturnsEmptyValues {

        private static final long serialVersionUID = 1L;

        @Override
        public Object answer(InvocationOnMock invocation) {
            try {
                String definitionClassName =
                    toDefinitionClassName(invocation.getMethod().getDeclaringClass().getName());
                Class<?> definitionClass = Class.forName(definitionClassName);
                ManagedObjectDefinition<?, ?> definition =
                    (ManagedObjectDefinition<?, ?>) definitionClass.getMethod("getInstance").invoke(null);
                String invokedMethodName = invocation.getMethod().getName();
                if (!isGetterMethod(invokedMethodName)) {
                    return answerFromDefaultMockitoBehavior(invocation);
                }
                Method getPropertyDefMethod = getPropertyDefinitionMethod(definitionClass, invokedMethodName);
                Class<?> propertyReturnType = getPropertyReturnType(getPropertyDefMethod);
                Object defaultValue = getDefaultValue(definition, getPropertyDefMethod, propertyReturnType);
                if (defaultValue != null) {
                    return defaultValue;
                }
                return answerFromDefaultMockitoBehavior(invocation);
            } catch (Exception e) {
                return answerFromDefaultMockitoBehavior(invocation);
            }
        }

        private Object answerFromDefaultMockitoBehavior(InvocationOnMock invocation) {
            return super.answer(invocation);
        }

        private boolean isGetterMethod(String invokedMethodName) {
            return invokedMethodName.startsWith("get") || invokedMethodName.startsWith("is");
        }

        private Method getPropertyDefinitionMethod(Class<?> definitionClass, String invokedMethodName)
                throws SecurityException, NoSuchMethodException {
            // Methods for boolean starts with "is" in Cfg class but with "get" in CfgDefn class.
            return definitionClass.getMethod(invokedMethodName.replaceAll("^is", "get") + "PropertyDefinition");
        }

        /** Returns the type of values returned by the property. */
        private Class<?> getPropertyReturnType(Method getPropertyDefMethod) {
            Class<?> returnClass = getPropertyDefMethod.getReturnType();
            return ((ParameterizedType) returnClass.getGenericSuperclass())
                    .getActualTypeArguments()[0].getClass();
        }

        /**
         * Retrieve class name of definition from class name of configuration.
         * <p>
         * Convert class name "[package].server.FooCfg" to "[package].meta.FooCfgDef"
         */
        private String toDefinitionClassName(String configClassName) {
            int finalDot = configClassName.lastIndexOf('.');
            return configClassName.substring(0, finalDot - 6) + "meta."
                    + configClassName.substring(finalDot + 1) + "Defn";
        }

        /**
         * Returns the default value corresponding to the provided property
         * definition getter method from the provided managed object definition.
         *
         * @param <T>
         *            The data type of values provided by the property
         *            definition.
         * @param definition
         *            The definition of the managed object.
         * @param getPropertyDefMethod
         *            The method to retrieve the property definition from the
         *            definition.
         * @param propertyReturnClass
         *            The class of values provided by the property definition.
         * @return the default value of property definition, or
         *         {@code null} if there is no default value.
         * @throws Exception
         *             If an error occurs.
         */
        @SuppressWarnings("unchecked")
        private <T> Object getDefaultValue(ManagedObjectDefinition<?, ?> definition,
                Method getPropertyDefMethod, Class<T> propertyReturnClass)
                throws Exception {
            PropertyDefinition<T> propertyDefinition = (PropertyDefinition<T>) getPropertyDefMethod.invoke(definition);
            DefaultBehaviorProvider<T> defaultBehaviorProvider = (DefaultBehaviorProvider<T>) propertyDefinition
                    .getClass().getMethod("getDefaultBehaviorProvider").invoke(propertyDefinition);
            MockProviderVisitor<T> visitor = new MockProviderVisitor<>(propertyDefinition);
            Collection<T> values = defaultBehaviorProvider.accept(visitor, null);

            if (values == null) {
                // No default behavior defined
                return null;
            } else if (propertyDefinition.hasOption(PropertyOption.MULTI_VALUED)) {
                return values;
            } else {
                // Single value returned
                return values.iterator().next();
            }
        }

    }

    /** Visitor used to retrieve the default value. */
    private static class MockProviderVisitor<T> implements DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

        /** The property definition used to decode the values. */
        private PropertyDefinition<T> propertyDef;

        MockProviderVisitor(PropertyDefinition<T> propertyDef) {
            this.propertyDef = propertyDef;
        }

        @Override
        public Collection<T> visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        @Override
        public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /** Returns the default value for the property as a collection. */
        @Override
        public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> provider, Void p) {
            SortedSet<T> values = new TreeSet<>();
            for (String stringValue : provider.getDefaultValues()) {
                values.add(propertyDef.decodeValue(stringValue));
            }
            return values;
        }

        @Override
        public Collection<T> visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }

        @Override
        public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }
    }
}
