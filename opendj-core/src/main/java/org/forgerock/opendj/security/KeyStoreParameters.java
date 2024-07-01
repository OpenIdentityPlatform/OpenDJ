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

import static org.forgerock.opendj.security.KeyStoreObjectCache.NONE;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.util.Options.defaultOptions;

import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStore.ProtectionParameter;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.util.Factory;
import org.forgerock.util.Option;
import org.forgerock.util.Options;

/**
 * The parameters which configure how the LDAP key store will be accessed. The connection factory should be configured
 * to return connections which are  already authenticated as a user having sufficient privileges to read and update LDAP
 * key store entries. In addition, the factory should use connection pooling in order to avoid excessive reconnection
 * when the key store is accessed frequently.
 */
public final class KeyStoreParameters implements LoadStoreParameter {
    /**
     * The optional password which is used to protect all private and secret keys. Note that individual keys may be
     * protected by a separate password. The default value for this option is a password factory which always
     * returns {@code null}, indicating that there is no global password and that separate passwords should be used
     * instead.
     * <p/>
     * Applications should provide a factory which always returns a new instance of the same password. The LDAP key
     * store will destroy the contents of the returned password after each use. It is the responsibility of the
     * factory to protect the in memory representation of the password between successive calls.
     *
     * @see OpenDJProvider#newClearTextPasswordFactory(char[])
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static final Option<Factory<char[]>> GLOBAL_PASSWORD =
            (Option) Option.of(Factory.class, newClearTextPasswordFactory(null));
    /**
     * The caching mechanism that the key store will use. Caching can significantly increase performance by reducing
     * interactions with the backend LDAP server(s), at the risk of returning stale key store objects for a period of
     * time. By default caching is disabled.
     *
     * @see OpenDJProvider#newKeyStoreObjectCacheFromMap(java.util.Map)
     */
    public static final Option<KeyStoreObjectCache> CACHE = Option.withDefault(NONE);
    /**
     * The number of iterations to use when deriving encryption keys from passwords using PBKDF2. The default is 10000
     * as recommended by NIST.
     */
    public static final Option<Integer> PBKDF2_ITERATIONS = Option.withDefault(10000);
    /**
     * The number of random bytes to use as the salt when deriving encryption keys from passwords using PBKDF2. The
     * default is 16.
     */
    public static final Option<Integer> PBKDF2_SALT_SIZE = Option.withDefault(16);
    /**
     * An alternative external mechanism for wrapping private and secret keys in the key store. By default, the key
     * store will use its own mechanism based on PBKDF2 and a global {@link #GLOBAL_PASSWORD password} if provided.
     */
    public static final Option<ExternalKeyWrappingStrategy> EXTERNAL_KEY_WRAPPING_STRATEGY =
            Option.of(ExternalKeyWrappingStrategy.class, null);

    private final ConnectionFactory factory;
    private final DN baseDN;
    private final Options options;

    /**
     * Creates a set of LDAP key store parameters with default options. See the class Javadoc for more information
     * about the parameters.
     *
     * @param factory
     *         The LDAP connection factory.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @return The key store parameters.
     */
    public static KeyStoreParameters newKeyStoreParameters(final ConnectionFactory factory, final DN baseDN) {
        return newKeyStoreParameters(factory, baseDN, defaultOptions());
    }

    /**
     * Creates a set of LDAP key store parameters with custom options. See the class Javadoc for more information about
     * the parameters.
     *
     * @param factory
     *         The LDAP connection factory.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @param options
     *         The optional key store parameters, including the cache configuration, key store password, and crypto
     *         parameters. The supported options are defined in this class.
     * @return The key store parameters.
     */
    public static KeyStoreParameters newKeyStoreParameters(final ConnectionFactory factory, final DN baseDN,
                                                           final Options options) {
        return new KeyStoreParameters(factory, baseDN, options);
    }

    private KeyStoreParameters(final ConnectionFactory factory, final DN baseDN, final Options options) {
        this.factory = factory;
        this.baseDN = baseDN;
        this.options = options;
    }

    @Override
    public ProtectionParameter getProtectionParameter() {
        throw new IllegalStateException(); // LDAP key store does not use this method.
    }

    Options getOptions() {
        return options;
    }

    Connection getConnection() throws LdapException {
        return getConnectionFactory().getConnection();
    }

    ConnectionFactory getConnectionFactory() {
        return factory;
    }

    DN getBaseDN() {
        return baseDN;
    }
}
