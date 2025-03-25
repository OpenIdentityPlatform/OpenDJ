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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, singleThreaded = true)
public class ExpressionTest extends DirectoryServerTestCase {
    @DataProvider
    public static Object[][] expressions() {
        return new Object[][] { { "true", String.class, "true" },
                                { "true", Boolean.class, true },
                                { "false", Boolean.class, false },
                                { "123", Integer.class, 123 },
                                { "123", Long.class, 123L }, };
    }

    @Test(dataProvider = "expressions")
    public void expressionEvaluationShouldReturnExpectedValues(final String expression, final Class<?> type,
                                                               final Object expected) {
        assertThat(Expression.eval(expression, type)).isEqualTo(expected);
    }

    @Test
    public void expressionsCanAccessEnvironment() {
        assertThat(Expression.eval("${env['PATH']}", String.class)).isNotNull();
    }

    @Test
    public void expressionsCanAccessSystemProperties() {
        assertThat(Expression.eval("${system['user.home']}", String.class)).isNotNull();
    }

    @Test
    public void expressionsCanAccessBeanBinding() {
        final Map<String, Object> bindings = bindings("server", new Server("myhost", 1389));
        assertThat(Expression.eval("${server.hostName}", String.class, bindings)).isEqualTo("myhost");
        assertThat(Expression.eval("${server.hostName.length()}", Integer.class, bindings)).isEqualTo(6);
        assertThat(Expression.eval("${server.port}", Integer.class, bindings)).isEqualTo(1389);
    }

    @Test
    public void expressionsCanAccessMapBinding() {
        final Map<String, Object> bindings = bindings("map", Collections.singletonMap("name", "World"));
        assertThat(Expression.eval("Hello ${map.name}", String.class, bindings)).isEqualTo("Hello World");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void expressionsShouldThrowIllegalArgumentExceptionForBadType() {
        Expression.eval("${env['PATH']}", Integer.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void expressionsShouldThrowIllegalArgumentExceptionForMissingProperty() {
        Expression.eval("${missing}", String.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void expressionsShouldThrowIllegalArgumentExceptionForMissingFunction() {
        Expression.eval("${missingFunction()}", String.class);
    }

    private Map<String, Object> bindings(final String key, final Object value) {
        return Collections.singletonMap(key, value);
    }

    private final class Server {
        private String hostName;
        private int port;

        private Server(final String hostName, final int port) {
            this.hostName = hostName;
            this.port = port;
        }

        public String getHostName() {
            return hostName;
        }

        public int getPort() {
            return port;
        }
    }
}
