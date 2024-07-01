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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

import static java.util.Collections.list;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.security.KeyStoreObject.newTrustedCertificateObject;
import static org.forgerock.opendj.security.KeyStoreTestUtils.KEYSTORE_DN;
import static org.forgerock.opendj.security.KeyStoreTestUtils.PUBLIC_KEY_CERTIFICATE;
import static org.forgerock.opendj.security.KeyStoreTestUtils.createKeyStore;
import static org.forgerock.opendj.security.KeyStoreTestUtils.createKeyStoreMemoryBackend;
import static org.forgerock.opendj.security.OpenDJProvider.newCapacityBasedKeyStoreObjectCache;
import static org.forgerock.opendj.security.OpenDJProvider.newKeyStoreObjectCacheFromMap;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;

import java.net.URL;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.util.Factory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenDJProviderTest extends SdkTestCase {
    @Test
    public void testNewProviderWithoutConfigFile() throws Exception {
        final OpenDJProvider provider = new OpenDJProvider();
        assertThat((Object) provider.getDefaultConfig()).isNull();
    }

    @Test
    public void testNewProviderFromConfigFile() throws Exception {
        final URL configUrl = getClass().getResource("opendj-provider.conf");
        final OpenDJProvider provider = new OpenDJProvider(configUrl.toURI());

        assertThat((Object) provider.getDefaultConfig().getBaseDN()).isEqualTo(KEYSTORE_DN);
        assertThat(provider.getDefaultConfig().getConnectionFactory()).isNotNull();
        assertThat(provider.getDefaultConfig().getOptions()).isNotNull();
    }

    @Test
    public void testNewLDAPKeyStore() throws Exception {
        final MemoryBackend backend = createKeyStoreMemoryBackend();
        final KeyStore keystore = createKeyStore(backend);

        assertThat(keystore.getProvider()).isInstanceOf(OpenDJProvider.class);
        assertThat(keystore.getType()).isEqualTo("LDAP");
        assertThat(keystore.size()).isZero();
        assertThat(list(keystore.aliases())).isEmpty();
    }

    @Test
    public void testNewKeyStoreObjectCacheFromMap() throws Exception {
        final Map<String, KeyStoreObject> map = new HashMap<>();
        final KeyStoreObjectCache cache = newKeyStoreObjectCacheFromMap(map);
        final KeyStoreObject keyStoreObject = newTrustedCertificateObject("test", PUBLIC_KEY_CERTIFICATE);

        cache.put(keyStoreObject);
        assertThat(map).containsEntry("test", keyStoreObject);
        assertThat(cache.get("test")).isSameAs(keyStoreObject);
    }

    @Test
    public void testNewCapacityBasedKeyStoreObjectCache() throws Exception {
        final KeyStoreObject keyStoreObject1 = newTrustedCertificateObject("test1", PUBLIC_KEY_CERTIFICATE);
        final KeyStoreObject keyStoreObject2 = newTrustedCertificateObject("test2", PUBLIC_KEY_CERTIFICATE);
        final KeyStoreObject keyStoreObject3 = newTrustedCertificateObject("test3", PUBLIC_KEY_CERTIFICATE);
        final KeyStoreObject keyStoreObject4 = newTrustedCertificateObject("test4", PUBLIC_KEY_CERTIFICATE);

        final KeyStoreObjectCache cache = newCapacityBasedKeyStoreObjectCache(3);

        cache.put(keyStoreObject1);
        cache.put(keyStoreObject2);
        cache.put(keyStoreObject3);
        assertThat(cache.get("test1")).isSameAs(keyStoreObject1);
        assertThat(cache.get("test2")).isSameAs(keyStoreObject2);
        assertThat(cache.get("test3")).isSameAs(keyStoreObject3);
        cache.put(keyStoreObject4);
        assertThat(cache.get("test1")).isNull();
        assertThat(cache.get("test2")).isSameAs(keyStoreObject2);
        assertThat(cache.get("test3")).isSameAs(keyStoreObject3);
        assertThat(cache.get("test4")).isSameAs(keyStoreObject4);
    }

    @DataProvider
    public static Object[][] obfuscatedPasswords() {
        // @formatter:off
        return new Object[][] {
            { null },
            { "".toCharArray() },
            { "password".toCharArray() },
            { "\u0000\u007F\u0080\u00FF\uFFFF".toCharArray() },
        };
        // @formatter:on
    }

    @Test(dataProvider = "obfuscatedPasswords")
    public void testNewObfuscatedPasswordFactory(final char[] password) {
        final Factory<char[]> factory = newClearTextPasswordFactory(password);
        assertThat(factory.newInstance()).isEqualTo(password);
        if (password != null) {
            assertThat(factory.newInstance()).isNotSameAs(password);
        }
    }
}
