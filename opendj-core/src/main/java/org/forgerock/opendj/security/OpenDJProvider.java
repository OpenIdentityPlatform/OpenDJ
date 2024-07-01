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

import static java.util.Collections.singletonList;
import static java.util.Collections.synchronizedMap;
import static org.forgerock.opendj.ldap.Connections.newCachedConnectionPool;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.AUTHN_BIND_REQUEST;
import static org.forgerock.opendj.ldap.LdapException.newLdapException;
import static org.forgerock.opendj.ldap.requests.Requests.newSimpleBindRequest;
import static org.forgerock.opendj.ldif.LDIF.copyTo;
import static org.forgerock.opendj.ldif.LDIF.newEntryIteratorReader;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreParameters.newKeyStoreParameters;
import static org.forgerock.opendj.security.OpenDJProviderSchema.SCHEMA;
import static org.forgerock.util.Options.defaultOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.ProviderException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.util.Factory;
import org.forgerock.util.Options;

/**
 * The OpenDJ LDAP security provider which exposes an LDAP/LDIF based {@link java.security.KeyStore KeyStore}
 * service, as well as providing utility methods facilitating construction of LDAP/LDIF based key stores. See the
 * package documentation for more information.
 */
public final class OpenDJProvider extends Provider {
    private static final long serialVersionUID = -1;
    // Security provider configuration property names.
    private static final String PREFIX = "org.forgerock.opendj.security.";
    private static final String LDIF_PROPERTY = PREFIX + "ldif";
    private static final String HOST_PROPERTY = PREFIX + "host";
    private static final String PORT_PROPERTY = PREFIX + "port";
    private static final String BIND_DN_PROPERTY = PREFIX + "bindDN";
    private static final String BIND_PASSWORD_PROPERTY = PREFIX + "bindPassword";
    private static final String KEYSTORE_BASE_DN_PROPERTY = PREFIX + "keyStoreBaseDN";
    private static final String KEYSTORE_PASSWORD_PROPERTY = PREFIX + "keyStorePassword";
    // Default key store configuration or null if key stores need explicit configuration.
    private final KeyStoreParameters defaultConfig;

    /** Creates a default LDAP security provider with no default key store configuration. */
    public OpenDJProvider() {
        this((KeyStoreParameters) null);
    }

    /**
     * Creates a LDAP security provider with provided default key store configuration.
     *
     * @param configFile
     *         The configuration file, which may be {@code null} indicating that key stores will be configured when they
     *         are instantiated.
     */
    public OpenDJProvider(final String configFile) {
        this(new File(configFile).toURI());
    }

    /**
     * Creates a LDAP security provider with provided default key store configuration.
     *
     * @param configFile
     *         The configuration file, which may be {@code null} indicating that key stores will be configured when they
     *         are instantiated.
     */
    public OpenDJProvider(final URI configFile) {
        this(configFile != null ? parseConfig(configFile) : null);
    }

    OpenDJProvider(final KeyStoreParameters defaultConfig) {
        super("OpenDJ", 1.0D, "OpenDJ LDAP security provider");
        this.defaultConfig = defaultConfig;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                putService(new KeyStoreService());
                return null;
            }
        });
    }

    /**
     * Creates a new LDAP key store with default options. The returned key store will already have been
     * {@link KeyStore#load(KeyStore.LoadStoreParameter) loaded}.
     *
     * @param factory
     *         The LDAP connection factory.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @return The LDAP key store.
     */
    public static KeyStore newLDAPKeyStore(final ConnectionFactory factory, final DN baseDN) {
        return newLDAPKeyStore(factory, baseDN, defaultOptions());
    }

    /**
     * Creates a new LDAP key store with custom options. The returned key store will already have been
     * {@link KeyStore#load(KeyStore.LoadStoreParameter) loaded}.
     *
     * @param factory
     *         The LDAP connection factory.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @param options
     *         The optional key store parameters, including the cache configuration, key store password, and crypto
     *         parameters.
     * @return The LDAP key store.
     * @see KeyStoreParameters For the list of available key store options.
     */
    public static KeyStore newLDAPKeyStore(final ConnectionFactory factory, final DN baseDN, final Options options) {
        try {
            final KeyStore keyStore = KeyStore.getInstance("LDAP", new OpenDJProvider());
            keyStore.load(newKeyStoreParameters(factory, baseDN, options));
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new LDIF based key store which will read and write key store objects to the provided key store file.
     * The LDIF file will be read during construction and re-written after each update. The returned key store will
     * already have been {@link KeyStore#load(KeyStore.LoadStoreParameter) loaded}.
     *
     * @param ldifFile
     *         The name of the LDIF file containing the key store objects.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @return The LDIF key store.
     * @throws IOException
     *         If an error occurred while reading the LDIF file.
     */
    public static KeyStore newLDIFKeyStore(final File ldifFile, final DN baseDN) throws IOException {
        return newLDIFKeyStore(ldifFile, baseDN, defaultOptions());
    }

    /**
     * Creates a new LDIF based key store which will read and write key store objects to the provided key store file.
     * The LDIF file will be read during construction and re-written after each update. The returned key store will
     * already have been {@link KeyStore#load(KeyStore.LoadStoreParameter) loaded}.
     *
     * @param ldifFile
     *         The name of the LDIF file containing the key store objects.
     * @param baseDN
     *         The DN of the subtree containing the LDAP key store.
     * @param options
     *         The optional key store parameters, including the cache configuration, key store password, and crypto
     *         parameters.
     * @return The LDIF key store.
     * @throws IOException
     *         If an error occurred while reading the LDIF file.
     */
    public static KeyStore newLDIFKeyStore(final File ldifFile, final DN baseDN, final Options options)
            throws IOException {
        return newLDAPKeyStore(newLDIFConnectionFactory(ldifFile), baseDN, options);
    }

    private static ConnectionFactory newLDIFConnectionFactory(final File ldifFile) throws IOException {
        try (LDIFEntryReader reader = new LDIFEntryReader(new FileReader(ldifFile)).setSchema(SCHEMA)) {
            final MemoryBackend backend = new MemoryBackend(SCHEMA, reader).enableVirtualAttributes(true);
            return newInternalConnectionFactory(new WriteLDIFOnUpdateRequestHandler(backend, ldifFile));
        }
    }

    /**
     * Creates a new key store object cache which will delegate to the provided {@link Map}. It is the responsibility
     * of the map implementation to perform cache eviction if needed. The provided map MUST be thread-safe.
     *
     * @param map
     *         The thread-safe {@link Map} implementation in which key store objects will be stored.
     * @return The new key store object cache.
     */
    public static KeyStoreObjectCache newKeyStoreObjectCacheFromMap(final Map<String, KeyStoreObject> map) {
        return new KeyStoreObjectCache() {
            @Override
            public void put(final KeyStoreObject keyStoreObject) {
                map.put(keyStoreObject.getAlias(), keyStoreObject);
            }

            @Override
            public KeyStoreObject get(final String alias) {
                return map.get(alias);
            }
        };
    }

    /**
     * Creates a new fixed capacity key store object cache which will evict objects once it reaches the
     * provided capacity. This implementation is only intended for simple use cases and is not particularly scalable.
     *
     * @param capacity
     *         The maximum number of key store objects that will be cached before eviction occurs.
     * @return The new key store object cache.
     */
    public static KeyStoreObjectCache newCapacityBasedKeyStoreObjectCache(final int capacity) {
        return newKeyStoreObjectCacheFromMap(synchronizedMap(new LinkedHashMap<String, KeyStoreObject>() {
            private static final long serialVersionUID = -1;

            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, KeyStoreObject> eldest) {
                return size() > capacity;
            }
        }));
    }

    /**
     * Returns a password factory which will return a copy of the provided password for each invocation of
     * {@link Factory#newInstance()}, and which does not provide any protection of the in memory representation of
     * the password.
     *
     * @param password
     *         The password or {@code null} if no password should ever be returned.
     * @return A password factory which will return a copy of the provided password.
     */
    public static Factory<char[]> newClearTextPasswordFactory(final char[] password) {
        return new Factory<char[]>() {
            private final char[] clonedPassword = password != null ? password.clone() : null;

            @Override
            public char[] newInstance() {
                return clonedPassword != null ? clonedPassword.clone() : null;
            }
        };
    }

    KeyStoreParameters getDefaultConfig() {
        return defaultConfig;
    }

    private static KeyStoreParameters parseConfig(final URI configFile) {
        try (final Reader configFileReader = new InputStreamReader(configFile.toURL().openStream())) {
            final Properties properties = new Properties();
            properties.load(configFileReader);

            final String keyStoreBaseDNProperty = properties.getProperty(KEYSTORE_BASE_DN_PROPERTY);
            if (keyStoreBaseDNProperty == null) {
                throw new IllegalArgumentException("missing key store base DN");
            }
            final DN keyStoreBaseDN = DN.valueOf(keyStoreBaseDNProperty);

            final Options keystoreOptions = defaultOptions();
            final String keystorePassword = properties.getProperty(KEYSTORE_PASSWORD_PROPERTY);
            if (keystorePassword != null) {
                keystoreOptions.set(GLOBAL_PASSWORD, newClearTextPasswordFactory(keystorePassword.toCharArray()));
            }

            final ConnectionFactory factory;
            final String ldif = properties.getProperty(LDIF_PROPERTY);
            if (ldif != null) {
                factory = newLDIFConnectionFactory(new File(ldif));
            } else {
                final String host = properties.getProperty(HOST_PROPERTY, "localhost");
                final int port = Integer.parseInt(properties.getProperty(PORT_PROPERTY, "389"));
                final DN bindDN = DN.valueOf(properties.getProperty(BIND_DN_PROPERTY, ""));
                final String bindPassword = properties.getProperty(BIND_PASSWORD_PROPERTY);

                final Options factoryOptions = defaultOptions();
                if (!bindDN.isRootDN()) {
                    factoryOptions.set(AUTHN_BIND_REQUEST,
                                       newSimpleBindRequest(bindDN.toString(), bindPassword.toCharArray()));
                }
                factory = newCachedConnectionPool(new LDAPConnectionFactory(host, port, factoryOptions));
            }

            return newKeyStoreParameters(factory, keyStoreBaseDN, keystoreOptions);
        } catch (Exception e) {
            throw new ProviderException("Error parsing configuration in file '" + configFile + "'", e);
        }
    }

    private static final class WriteLDIFOnUpdateRequestHandler implements RequestHandler<RequestContext> {
        private final MemoryBackend backend;
        private final File ldifFile;

        private WriteLDIFOnUpdateRequestHandler(final MemoryBackend backend, final File ldifFile) {
            this.backend = backend;
            this.ldifFile = ldifFile;
        }

        @Override
        public void handleAdd(final RequestContext requestContext, final AddRequest request,
                              final IntermediateResponseHandler intermediateResponseHandler,
                              final LdapResultHandler<Result> resultHandler) {
            backend.handleAdd(requestContext, request, intermediateResponseHandler, saveAndForwardTo(resultHandler));
        }

        @Override
        public void handleBind(final RequestContext requestContext, final int version, final BindRequest request,
                               final IntermediateResponseHandler intermediateResponseHandler,
                               final LdapResultHandler<BindResult> resultHandler) {
            backend.handleBind(requestContext, version, request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleCompare(final RequestContext requestContext, final CompareRequest request,
                                  final IntermediateResponseHandler intermediateResponseHandler,
                                  final LdapResultHandler<CompareResult> resultHandler) {
            backend.handleCompare(requestContext, request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleDelete(final RequestContext requestContext, final DeleteRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            backend.handleDelete(requestContext, request, intermediateResponseHandler, saveAndForwardTo(resultHandler));
        }

        @Override
        public <R extends ExtendedResult> void handleExtendedRequest(final RequestContext requestContext,
                                                                     final ExtendedRequest<R> request,
                                                                     final IntermediateResponseHandler
                                                                                     intermediateResponseHandler,
                                                                     final LdapResultHandler<R> resultHandler) {
            backend.handleExtendedRequest(requestContext, request, intermediateResponseHandler, resultHandler);
        }

        @Override
        public void handleModify(final RequestContext requestContext, final ModifyRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            backend.handleModify(requestContext, request, intermediateResponseHandler, saveAndForwardTo(resultHandler));
        }

        @Override
        public void handleModifyDN(final RequestContext requestContext, final ModifyDNRequest request,
                                   final IntermediateResponseHandler intermediateResponseHandler,
                                   final LdapResultHandler<Result> resultHandler) {
            backend.handleModifyDN(requestContext,
                                   request,
                                   intermediateResponseHandler,
                                   saveAndForwardTo(resultHandler));
        }

        @Override
        public void handleSearch(final RequestContext requestContext, final SearchRequest request,
                                 final IntermediateResponseHandler intermediateResponseHandler,
                                 final SearchResultHandler entryHandler,
                                 final LdapResultHandler<Result> resultHandler) {
            backend.handleSearch(requestContext, request, intermediateResponseHandler, entryHandler, resultHandler);
        }

        private LdapResultHandler<Result> saveAndForwardTo(final LdapResultHandler<Result> resultHandler) {
            return new LdapResultHandler<Result>() {
                @Override
                public void handleException(final LdapException exception) {
                    resultHandler.handleException(exception);
                }

                @Override
                public void handleResult(final Result result) {
                    try {
                        writeLDIF(backend, ldifFile);
                        resultHandler.handleResult(result);
                    } catch (IOException e) {
                        final LdapException ldapException =
                                newLdapException(ResultCode.OTHER, "Unable to write LDIF file " + ldifFile, e);
                        resultHandler.handleException(ldapException);
                    }
                }
            };
        }

        private static void writeLDIF(final MemoryBackend backend, final File ldifFile) throws IOException {
            try (final FileWriter fileWriter = new FileWriter(ldifFile);
                 final BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                 final LDIFEntryWriter entryWriter = new LDIFEntryWriter(bufferedWriter)) {
                copyTo(newEntryIteratorReader(backend.getAll().iterator()), entryWriter);
            }
        }
    }

    private final class KeyStoreService extends Service {
        private KeyStoreService() {
            super(OpenDJProvider.this, "KeyStore", "LDAP", KeyStoreImpl.class.getName(), singletonList("OpenDJ"), null);
        }

        // Override the default constructor so that we can pass in this provider and any file based configuration.
        @Override
        public Object newInstance(final Object constructorParameter) {
            return new KeyStoreImpl(OpenDJProvider.this);
        }
    }
}
