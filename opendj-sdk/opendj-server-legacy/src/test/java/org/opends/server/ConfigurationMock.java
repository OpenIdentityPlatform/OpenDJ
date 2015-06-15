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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.config.AbsoluteInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.AliasDefaultBehaviorProvider;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.DefaultBehaviorProvider;
import org.forgerock.opendj.config.DefaultBehaviorProviderVisitor;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelativeInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
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

    private static final LegacyConfigAnswer LEGACY_CONFIG_ANSWER = new LegacyConfigAnswer();

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
    public static <T extends org.opends.server.admin.Configuration> T legacyMockCfg(Class<T> configClass) {
        return mock(configClass, LEGACY_CONFIG_ANSWER);
    }

    /**
     * A stubbed answer for Configuration objects, allowing to return default
     * value for settings when available.
     */
    private static class ConfigAnswer extends ReturnsEmptyValues {

        private static final long serialVersionUID = 1L;

        /** {@inheritDoc} */
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
                if (defaultValue == null) {
                    return answerFromDefaultMockitoBehavior(invocation);
                }
                return defaultValue;
            } catch (Exception e) {
                return answerFromDefaultMockitoBehavior(invocation);
            }
        }

        private Object answerFromDefaultMockitoBehavior(InvocationOnMock invocation) {
            return super.answer(invocation);
        }

        private static boolean isGetterMethod(String invokedMethodName) {
            return invokedMethodName.startsWith("get") || invokedMethodName.startsWith("is");
        }

        private static Method getPropertyDefinitionMethod(Class<?> definitionClass, String invokedMethodName)
                throws SecurityException, NoSuchMethodException {
            // Methods for boolean starts with "is" in Cfg class but with "get" in CfgDefn class.
            return definitionClass.getMethod(invokedMethodName.replaceAll("^is", "get") + "PropertyDefinition");
        }

        /**
         * Returns the type of values returned by the property.
         */
        private static Class<?> getPropertyReturnType(Method getPropertyDefMethod) {
            Class<?> returnClass = getPropertyDefMethod.getReturnType();
            return ((ParameterizedType) returnClass.getGenericSuperclass())
                    .getActualTypeArguments()[0].getClass();
        }

        /**
         * Retrieve class name of definition from class name of configuration.
         * <p>
         * Convert class name "[package].server.FooCfg" to "[package].meta.FooCfgDef"
         */
        private static String toDefinitionClassName(String configClassName) {
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
        private static <T> Object getDefaultValue(ManagedObjectDefinition<?, ?> definition,
                Method getPropertyDefMethod, Class<T> propertyReturnClass) throws Exception {
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

    /**
     * A stubbed answer for Configuration objects, allowing to return default
     * value for settings when available.
     */
    private static class LegacyConfigAnswer extends ReturnsEmptyValues {

        private static final long serialVersionUID = 1L;

        /** {@inheritDoc} */
        @Override
        public Object answer(InvocationOnMock invocation) {
            try {
                String definitionClassName =
                    toDefinitionClassName(invocation.getMethod().getDeclaringClass().getName());
                Class<?> definitionClass = Class.forName(definitionClassName);
                org.opends.server.admin.ManagedObjectDefinition<?, ?> definition =
                    (org.opends.server.admin.ManagedObjectDefinition<?, ?>) definitionClass.getMethod("getInstance").invoke(null);
                String invokedMethodName = invocation.getMethod().getName();
                if (!isGetterMethod(invokedMethodName)) {
                    return answerFromDefaultMockitoBehavior(invocation);
                }
                Method getPropertyDefMethod = getPropertyDefinitionMethod(definitionClass, invokedMethodName);
                Class<?> propertyReturnType = getPropertyReturnType(getPropertyDefMethod);
                Object defaultValue = getDefaultValue(definition, getPropertyDefMethod, propertyReturnType);
                if (defaultValue == null) {
                    return answerFromDefaultMockitoBehavior(invocation);
                }
                return defaultValue;
            } catch (Exception e) {
                return answerFromDefaultMockitoBehavior(invocation);
            }
        }

        private Object answerFromDefaultMockitoBehavior(InvocationOnMock invocation) {
            return super.answer(invocation);
        }

        private static boolean isGetterMethod(String invokedMethodName) {
            return invokedMethodName.startsWith("get") || invokedMethodName.startsWith("is");
        }

        private static Method getPropertyDefinitionMethod(Class<?> definitionClass, String invokedMethodName)
                throws SecurityException, NoSuchMethodException {
            // Methods for boolean starts with "is" in Cfg class but with "get" in CfgDefn class.
            return definitionClass.getMethod(invokedMethodName.replaceAll("^is", "get") + "PropertyDefinition");
        }

        /**
         * Returns the type of values returned by the property.
         */
        private static Class<?> getPropertyReturnType(Method getPropertyDefMethod) {
            Class<?> returnClass = getPropertyDefMethod.getReturnType();
            return ((ParameterizedType) returnClass.getGenericSuperclass()).getActualTypeArguments()[0].getClass();
        }

        /**
         * Retrieve class name of definition from class name of configuration.
         * <p>
         * Convert class name "[package].server.FooCfg" to "[package].meta.FooCfgDef"
         */
        private static String toDefinitionClassName(String configClassName) {
            int finalDot = configClassName.lastIndexOf('.');
            return configClassName.substring(0, finalDot - 6) + "meta." + configClassName.substring(finalDot + 1)
                    + "Defn";
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
        private static <T> Object getDefaultValue(org.opends.server.admin.ManagedObjectDefinition<?, ?> definition,
                Method getPropertyDefMethod, Class<T> propertyReturnClass) throws Exception {
            org.opends.server.admin.PropertyDefinition<T> propertyDefinition = (org.opends.server.admin.PropertyDefinition<T>) getPropertyDefMethod.invoke(definition);
            org.opends.server.admin.DefaultBehaviorProvider<T> defaultBehaviorProvider = (org.opends.server.admin.DefaultBehaviorProvider<T>) propertyDefinition
                    .getClass().getMethod("getDefaultBehaviorProvider").invoke(propertyDefinition);
            LegacyMockProviderVisitor<T> visitor = new LegacyMockProviderVisitor<>(propertyDefinition);
            Collection<T> values = defaultBehaviorProvider.accept(visitor, null);

            if (values == null) {
                // No default behavior defined
                return null;
            } else if (propertyDefinition.hasOption(org.opends.server.admin.PropertyOption.MULTI_VALUED)) {
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

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /**
         * Returns the default value for the property as a collection.
         */
        @Override
        public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> provider, Void p) {
            SortedSet<T> values = new TreeSet<>();
            for (String stringValue : provider.getDefaultValues()) {
                values.add(propertyDef.decodeValue(stringValue));
            }
            return values;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }
    }

    /** Visitor used to retrieve the default value. */
    private static class LegacyMockProviderVisitor<T> implements org.opends.server.admin.DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

        /** The property definition used to decode the values. */
        private org.opends.server.admin.PropertyDefinition<T> propertyDef;

        LegacyMockProviderVisitor(org.opends.server.admin.PropertyDefinition<T> propertyDef) {
            this.propertyDef = propertyDef;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAbsoluteInherited(org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitAlias(org.opends.server.admin.AliasDefaultBehaviorProvider<T> provider, Void p) {
            // not handled
            return null;
        }

        /**
         * Returns the default value for the property as a collection.
         */
        @Override
        public Collection<T> visitDefined(org.opends.server.admin.DefinedDefaultBehaviorProvider<T> provider, Void p) {
            SortedSet<T> values = new TreeSet<>();
            for (String stringValue : provider.getDefaultValues()) {
                values.add(propertyDef.decodeValue(stringValue));
            }
            return values;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitRelativeInherited(org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }

        /** {@inheritDoc} */
        @Override
        public Collection<T> visitUndefined(org.opends.server.admin.UndefinedDefaultBehaviorProvider<T> d, Void p) {
            // not handled
            return null;
        }
    }
}
