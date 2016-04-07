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
 * Copyright 2013-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static org.fest.assertions.Assertions.*;

import java.io.File;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;

@SuppressWarnings("javadoc")
public class ConnectionFactoryProviderTest extends ToolsTestCase {

    @Mock
    private ConsoleApplication app;

    private ArgumentParser argParser;

    private ConnectionFactoryProvider connectionFactoryProvider;

    @BeforeMethod
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
        argParser = new ArgumentParser("unused", new LocalizableMessageBuilder().toMessage(), false);
        connectionFactoryProvider = new ConnectionFactoryProvider(argParser, app);
    }

    /** Issue OPENDJ-734. */
    @Test
    public void getConnectionFactoryShouldAllowNullTrustStorePassword() throws Exception {
        // provide a trustStorePath but no password
        String trustStorePath = new File(getClass().getClassLoader().getResource("dummy-truststore").toURI())
                .getCanonicalPath();
        argParser.parseArguments(new String[] { "--useStartTLS", "--trustStorePath", trustStorePath });

        ConnectionFactory factory = connectionFactoryProvider.getUnauthenticatedConnectionFactory();

        assertThat(factory).isNotNull();
    }

}
