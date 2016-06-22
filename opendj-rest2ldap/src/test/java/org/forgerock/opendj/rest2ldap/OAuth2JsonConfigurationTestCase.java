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
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.TestUtils.parseJson;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.forgerock.openig.oauth2.AccessTokenResolver;
import org.forgerock.json.JsonValueException;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.testng.ForgeRockTestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public class OAuth2JsonConfigurationTestCase extends ForgeRockTestCase {

    @Mock
    private AccessTokenResolver resolver;

    @Spy
    private Rest2LdapHttpApplication fakeApp;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        fakeApp = spy(Rest2LdapHttpApplication.class);
    }

    @DataProvider
    public Object[][] invalidOAuth2Configurations() {
        // @Checkstyle:off
        return new Object[][] {
                // Missing 'realm'
                {
                        "{}",
                },
                // Missing 'authzIdTemplate'
                {
                        "{'realm': 'example.com'}",
                },
                // Missing 'requiredScopes'
                {
                        "{'realm': 'example.com', "
                                + "'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'}",
                },
                // Missing 'resolver'
                {
                        "{'realm': 'example.com',"
                                + "'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com',"
                                + "'requiredScopes': ['read', 'write', 'dolphin']}",
                },
                // Missing 'openam/endpointURL'
                {
                        "{'realm': 'example.com',"
                                + "'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com',"
                                + "'requiredScopes': ['read', 'write', 'dolphin'],"
                                + "'resolver': 'openam',"
                                + "'openam': {}}",
                },
                // Invalid 'authzIdTemplate' content
                {
                        "{'realm': 'example.com',"
                                + "'requiredScopes': ['read', 'write', 'dolphin'],"
                                + "'resolver': 'openam',"
                                + "'openam': {"
                                + "    'endpointUrl': 'http://www.example.com/token-info',"
                                + "    'authzIdTemplate': 'userName: ou={/user/id},dc=example,dc=com'"
                                + "},"
                                + "'accessTokenCache': {'enabled': true, 'cacheExpiration': '42'}}",
                },
                // Invalid 'accessTokenCache/expiration' duration
                {
                        "{'realm': 'example.com',"
                                + "'requiredScopes': ['read', 'write', 'dolphin'],"
                                + "'resolver': 'openam',"
                                + "'openam': {"
                                + "    'endpointUrl': 'http://www.example.com/token-info',"
                                + "    'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'"
                                + "},"
                                + "'accessTokenCache': {'enabled': true, 'cacheExpiration': '42'}}",
                }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidOAuth2Configurations", expectedExceptions = JsonValueException.class)
    public void testInvalidOauth2Configurations(final String rawJson) throws Exception {
        fakeApp.buildOAuth2Filter(parseJson(rawJson));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*scopes set can not be empty.*")
    public void testOAuth2FilterWithEmptyScopes() throws Exception {
        final String config =
            "{'realm': 'example.com',"
                    + "'requiredScopes': [],"
                    + "'resolver': 'openam',"
                    + "'openam': {"
                    + "    'endpointUrl': 'http://www.example.com/token-info',"
                    + "    'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'"
                    + "}}";
        fakeApp.buildOAuth2Filter(parseJson(config));
    }

    @DataProvider
    public Object[][] invalidResolverConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
            {
                    "{}",
            },
            {
                    "{'resolver': 'rfc7662',"
                            + "'rfc7662': {}}",
            },
            {
                    "{'resolver': 'rfc7662',"
                            + "'rfc7662': { 'endpointURL': 'http:/example.com/introspect'}}",
            },
            {
                    "{'resolver': 'rfc7662',"
                            + "'rfc7662': { 'endpointURL': 'http:/example.com/introspect',"
                            + "               'clientId': 'client_app_id'}}",
            },
            {
                    "{'resolver': 'openam',"
                            + "'openam': {}}",
            },
            {
                    "{'resolver': 'cts',"
                            + "'cts': {}}",
            },
            {
                    "{'resolver': 'file',"
                            + "'file': {}}",
            }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidResolverConfigurations", expectedExceptions = JsonValueException.class)
    public void testInvalidResolverConfigurations(final String rawJson) throws Exception {
        fakeApp.parseUnderlyingResolver(parseJson(rawJson));
    }

    @DataProvider
    public Object[][] invalidCacheResolverConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
                {
                        "{'accessTokenCache': {"
                                + "'enabled': true,"
                                + "'cacheExpiration': '0 minutes'}}",
                },
                {
                        "{'accessTokenCache': {"
                                + "'enabled': true,"
                                + "'cacheExpiration': 'lorem ipsum'}}",
                }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidCacheResolverConfigurations", expectedExceptions = JsonValueException.class)
    public void testInvalidCacheResolverConfigurations(final String rawJson) throws Exception {
        fakeApp.createCachedTokenResolverIfNeeded(parseJson(rawJson), resolver);
    }

    @DataProvider
    public Object[][] ingnoredCacheResolverConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
                {
                        "{}"
                },
                {
                        "{'accessTokenCache': {"
                                + "'enabled': false,"
                                + "'cacheExpiration': '5 minutes'}}"
                }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "ingnoredCacheResolverConfigurations")
    public void testNoCacheFallbackOnResolver(final String rawJson) throws Exception {
        assertThat(fakeApp.createCachedTokenResolverIfNeeded(parseJson(rawJson), resolver)).isEqualTo(resolver);
    }

    @DataProvider
    public Object[][] validResolverConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
                {
                        "{'resolver': 'rfc7662',"
                                + "'rfc7662': { 'endpointUrl': 'http:/example.com/introspect',"
                                + "             'clientId': 'client_app_id',"
                                + "             'clientSecret': 'client_app_secret',"
                                + "             'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'}}"
                },
                {
                        "{'resolver': 'openam',"
                                + "'openam': { "
                                + "    'endpointUrl': 'http:/example.com/tokeninfo',"
                                + "    'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'}}"
                },
                {
                        "{'resolver': 'cts',"
                                + "'cts': { 'baseDn': 'coreTokenId={token},dc=com',"
                                + "         'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'}}"
                },
                {
                        "{'resolver': 'file',"
                                + "'file': { 'folderPath': '/path/to/test/folder',"
                                + "          'authzIdTemplate': 'dn: ou={/user/id},dc=example,dc=com'}}"
                }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "validResolverConfigurations")
    public void testValidResolverConfiguration(final String rawJson) throws Exception {
        when(fakeApp.getConnectionFactory(eq("root"))).thenReturn(mock(ConnectionFactory.class));
        assertThat(fakeApp.parseUnderlyingResolver(parseJson(rawJson))).isNotNull();
    }
}
