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
 * Portions Copyright 2017 Rosie Applications, Inc.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.forgerock.http.routing.RouteMatchers.newResourceApiVersionBehaviourManager;
import static org.forgerock.http.routing.RoutingMode.STARTS_WITH;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.http.util.Json.readJsonLenient;
import static org.forgerock.json.JsonValueFunctions.enumConstant;
import static org.forgerock.json.JsonValueFunctions.pointer;
import static org.forgerock.json.JsonValueFunctions.setOf;
import static org.forgerock.json.resource.RouteMatchers.requestUriMatcher;
import static org.forgerock.json.resource.RouteMatchers.resourceApiVersionContextFilter;
import static org.forgerock.opendj.ldap.Connections.LOAD_BALANCER_MONITORING_INTERVAL;
import static org.forgerock.opendj.ldap.Connections.newCachedConnectionPool;
import static org.forgerock.opendj.ldap.Connections.newFailoverLoadBalancer;
import static org.forgerock.opendj.ldap.Connections.newRoundRobinLoadBalancer;
import static org.forgerock.opendj.ldap.KeyManagers.useJvmDefaultKeyStore;
import static org.forgerock.opendj.ldap.KeyManagers.useKeyStoreFile;
import static org.forgerock.opendj.ldap.KeyManagers.usePKCS11Token;
import static org.forgerock.opendj.ldap.KeyManagers.useSingleCertificate;
import static org.forgerock.opendj.ldap.LDAPConnectionFactory.*;
import static org.forgerock.opendj.ldap.TrustManagers.checkUsingTrustStore;
import static org.forgerock.opendj.ldap.TrustManagers.trustAll;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.Utils.newJsonValueException;
import static org.forgerock.util.Utils.joinAsString;
import static org.forgerock.util.time.Duration.duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.forgerock.http.routing.ResourceApiVersionBehaviourManager;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.FilterChain;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SSLContextBuilder;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.services.context.Context;
import org.forgerock.util.Options;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.time.Duration;

/** Provides core factory methods and builders for constructing Rest2Ldap endpoints from JSON configuration. */
public final class Rest2LdapJsonConfigurator {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * Parses Rest2Ldap configuration options. The JSON configuration must have the following format:
     * <p>
     * <pre>
     * {
     *      "readOnUpdatePolicy": "controls",
     *      "useSubtreeDelete": true,
     *      "usePermissiveModify": true,
     *      "useMvcc": true
     *      "mvccAttribute": "etag"
     * }
     * </pre>
     * <p>
     * See the sample configuration file for a detailed description of its content.
     *
     * @param config
     *         The JSON configuration.
     * @return The parsed Rest2Ldap configuration options.
     * @throws IllegalArgumentException
     *         If the configuration is invalid.
     */
    public static Options configureOptions(final JsonValue config) {
        final Options options = Options.defaultOptions();

        options.set(READ_ON_UPDATE_POLICY,
                    config.get("readOnUpdatePolicy").defaultTo(CONTROLS).as(enumConstant(ReadOnUpdatePolicy.class)));

        // Default to false, even though it is supported by OpenDJ, because it requires additional permissions.
        options.set(USE_SUBTREE_DELETE, config.get("useSubtreeDelete").defaultTo(false).asBoolean());

        // Default to true because it is supported by OpenDJ and does not require additional permissions.
        options.set(USE_PERMISSIVE_MODIFY, config.get("usePermissiveModify").defaultTo(false).asBoolean());

        options.set(USE_MVCC, config.get("useMvcc").defaultTo(true).asBoolean());
        options.set(MVCC_ATTRIBUTE, config.get("mvccAttribute").defaultTo("etag").asString());

        return options;
    }

    /**
     * Parses a list of Rest2Ldap resource definitions. The JSON configuration must have the following format:
     * <p>
     * <pre>
     * "top": {
     *     "isAbstract": true,
     *     "properties": {
     *         "_rev": {
     *             "type": "simple"
     *             "ldapAttribute": "etag",
     *             "writability": "readOnly"
     *         },
     *         ...
     *     },
     *     ...
     * },
     * ...
     * </pre>
     * <p>
     * See the sample configuration file for a detailed description of its content.
     *
     * @param config
     *         The JSON configuration.
     * @return The parsed list of Rest2Ldap resource definitions.
     * @throws IllegalArgumentException
     *         If the configuration is invalid.
     */
    public static List<Resource> configureResources(final JsonValue config) {
        final JsonValue resourcesConfig = config.required().expect(Map.class);
        final List<Resource> resources = new LinkedList<>();
        for (final String resourceId : resourcesConfig.keys()) {
            resources.add(configureResource(resourceId, resourcesConfig.get(resourceId)));
        }
        return resources;
    }

    /**
     * Creates a new CREST {@link Router} using the provided endpoints configuration directory and Rest2Ldap options.
     * The Rest2Ldap configuration typically has the following structure on disk:
     * <ul>
     * <li> config.json - contains the configuration for the LDAP connection factories and authorization
     * <li> rest2ldap/rest2ldap.json - defines Rest2Ldap configuration options
     * <li> rest2ldap/endpoints/{api} - a directory containing the endpoint's resource definitions for endpoint {api}
     * <li> rest2ldap/endpoints/{api}/{resource-id}.json - the resource definitions for a specific version of API {api}.
     * The name of the file, {resource-id}, determines which resource type definition in the mapping file will be
     * used as the root resource.
     * </ul>
     *
     * @param endpointsDirectory The directory representing the Rest2Ldap "endpoints" directory.
     * @param options The Rest2Ldap configuration options.
     * @return A new CREST {@link Router} configured using the provided options and endpoints.
     * @throws IOException If the endpoints configuration cannot be read.
     * @throws IllegalArgumentException
     *         If the configuration is invalid.
     */
    public static Router configureEndpoints(final File endpointsDirectory, final Options options) throws IOException {
        final Router pathRouter = new Router();

        final File[] endpoints = endpointsDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.isDirectory() && pathname.canRead();
            }
        });

        if (endpoints == null) {
            throw new LocalizedIllegalArgumentException(ERR_INVALID_ENDPOINTS_DIRECTORY.get(endpointsDirectory));
        }

        for (final File endpoint : endpoints) {
            final RequestHandler endpointHandler = configureEndpoint(endpoint, options);
            pathRouter.addRoute(requestUriMatcher(STARTS_WITH, endpoint.getName()), endpointHandler);
        }
        return pathRouter;
    }

    /**
     * Creates a new CREST {@link RequestHandler} representing a single endpoint whose configuration is defined in the
     * provided {@code endpointDirectory} parameter. The directory should contain a separate file for each supported
     * version of the REST endpoint. The name of the file, excluding the suffix, identifies the resource definition
     * which acts as the entry point into the endpoint.
     *
     * @param endpointDirectory The directory containing the endpoint's resource definitions, e.g.
     *                          rest2ldap/routes/api would contain definitions for the "api" endpoint.
     * @param options The Rest2Ldap configuration options.
     * @return A new CREST {@link RequestHandler} configured using the provided options and endpoint mappings.
     * @throws IOException If the endpoint configuration cannot be read.
     * @throws IllegalArgumentException If the configuration is invalid.
     */
    public static RequestHandler configureEndpoint(File endpointDirectory, Options options) throws IOException {
        final Router versionRouter = new Router();
        final File[] endpointVersions = endpointDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.isFile() && pathname.canRead() && pathname.getName().endsWith(".json");
            }
        });

        if (endpointVersions == null) {
            throw new LocalizedIllegalArgumentException(ERR_INVALID_ENDPOINT_DIRECTORY.get(endpointDirectory));
        }

        final List<String> supportedVersions = new ArrayList<>();
        boolean hasWildCardVersion = false;
        for (final File endpointVersion : endpointVersions) {
            final JsonValue mappingConfig = readJson(endpointVersion);
            final String version = mappingConfig.get("version").defaultTo("*").asString();
            final List<Resource> resourceTypes = configureResources(mappingConfig.get("resourceTypes"));
            final Rest2Ldap rest2Ldap = rest2Ldap(options, resourceTypes);

            final String endpointVersionFileName = endpointVersion.getName();
            final int endIndex = endpointVersionFileName.lastIndexOf('.');
            final String rootResourceType = endpointVersionFileName.substring(0, endIndex);
            final RequestHandler handler = rest2Ldap.newRequestHandlerFor(rootResourceType);

            if (version.equals("*")) {
                versionRouter.setDefaultRoute(handler);
                hasWildCardVersion = true;
            } else {
                versionRouter.addRoute(version(version), handler);
                supportedVersions.add(version);
            }
            logger.debug(INFO_REST2LDAP_CREATING_ENDPOINT.get(endpointDirectory.getName(), version));
        }
        if (!hasWildCardVersion) {
            versionRouter.setDefaultRoute(new AbstractRequestHandler() {
                @Override
                protected <V> Promise<V, ResourceException> handleRequest(Context context, Request request) {
                    final String message = ERR_BAD_API_RESOURCE_VERSION.get(request.getResourceVersion(),
                                                                            joinAsString(", ", supportedVersions))
                                                                       .toString();
                    return new BadRequestException(message).asPromise();
                }
            });
        }

        // FIXME: Disable the warning header for now due to CREST-389 / CREST-390.
        final ResourceApiVersionBehaviourManager behaviourManager = newResourceApiVersionBehaviourManager();
        behaviourManager.setWarningEnabled(false);
        return new FilterChain(versionRouter, resourceApiVersionContextFilter(behaviourManager));
    }

    static JsonValue readJson(final File resource) throws IOException {
        try (InputStream in = new FileInputStream(resource)) {
            return new JsonValue(readJsonLenient(in));
        }
    }

    private static Resource configureResource(final String resourceId, final JsonValue config) {
        final Resource resource = resource(resourceId)
                .isAbstract(config.get("isAbstract").defaultTo(false).asBoolean())
                .superType(config.get("superType").asString())
                .objectClasses(config.get("objectClasses")
                                     .defaultTo(emptyList()).asList(String.class).toArray(new String[0]))
                .supportedActions(config.get("supportedActions")
                                        .defaultTo(emptyList())
                                        .as(setOf(enumConstant(Action.class))).toArray(new Action[0]))
                .resourceTypeProperty(config.get("resourceTypeProperty").as(pointer()))
                .includeAllUserAttributesByDefault(config.get("includeAllUserAttributesByDefault")
                                                         .defaultTo(false).asBoolean())
                .excludedDefaultUserAttributes(config.get("excludedDefaultUserAttributes")
                                                     .defaultTo(Collections.emptyList()).asList(String.class));

        final JsonValue properties = config.get("properties").expect(Map.class);
        for (final String property : properties.keys()) {
            resource.property(property, configurePropertyMapper(properties.get(property), property));
        }

        final JsonValue subResources = config.get("subResources").expect(Map.class);
        for (final String urlTemplate : subResources.keys()) {
            resource.subResource(configureSubResource(urlTemplate, subResources.get(urlTemplate)));
        }

        return resource;
    }

    private enum NamingStrategyType { CLIENTDNNAMING, CLIENTNAMING, SERVERNAMING }
    private enum SubResourceType { COLLECTION, SINGLETON }

    private static SubResource configureSubResource(final String urlTemplate,
                                                    final JsonValue config) {
        final String dnTemplate = config.get("dnTemplate").defaultTo("").asString();
        final Boolean isReadOnly = config.get("isReadOnly").defaultTo(false).asBoolean();
        final String resourceId = config.get("resource").required().asString();

        final SubResourceType subResourceType =
          config.get("type").required().as(enumConstant(SubResourceType.class));

        if (subResourceType == SubResourceType.COLLECTION) {
            return configureCollectionSubResource(
                config, resourceId, urlTemplate, dnTemplate, isReadOnly);
        } else {
            return configureSingletonSubResource(
                config, resourceId, urlTemplate, dnTemplate, isReadOnly);
        }
    }

    private static SubResource configureCollectionSubResource(final JsonValue config,
                                                              final String resourceId,
                                                              final String urlTemplate,
                                                              final String dnTemplate,
                                                              final Boolean isReadOnly) {
        final String[] glueObjectClasses =
            config.get("glueObjectClasses")
                .defaultTo(emptyList())
                .asList(String.class)
                .toArray(new String[0]);

        final Boolean flattenSubtree = config.get("flattenSubtree").defaultTo(false).asBoolean();
        final String searchFilter = config.get("baseSearchFilter").asString();

        final SubResourceCollection collection =
            collectionOf(resourceId)
                .urlTemplate(urlTemplate)
                .dnTemplate(dnTemplate)
                .isReadOnly(isReadOnly)
                .glueObjectClasses(glueObjectClasses)
                .flattenSubtree(flattenSubtree)
                .baseSearchFilter(searchFilter);

        configureCollectionNamingStrategy(config, collection);

        return collection;
    }

    private static void configureCollectionNamingStrategy(final JsonValue config,
                                                          final SubResourceCollection collection) {
        final JsonValue namingStrategy = config.get("namingStrategy").required();
        final NamingStrategyType namingStrategyType =
            namingStrategy.get("type").required().as(enumConstant(NamingStrategyType.class));

        switch (namingStrategyType) {
        case CLIENTDNNAMING:
            collection.useClientDnNaming(namingStrategy.get("dnAttribute").required().asString());
            break;
        case CLIENTNAMING:
            collection.useClientNaming(namingStrategy.get("dnAttribute").required().asString(),
                                       namingStrategy.get("idAttribute").required().asString());
            break;
        case SERVERNAMING:
            collection.useServerNaming(namingStrategy.get("dnAttribute").required().asString(),
                                       namingStrategy.get("idAttribute").required().asString());
            break;
        }
    }

    private static SubResource configureSingletonSubResource(final JsonValue config,
                                                             final String resourceId,
                                                             final String urlTemplate,
                                                             final String dnTemplate,
                                                             final Boolean isReadOnly) {
        return singletonOf(resourceId)
                    .urlTemplate(urlTemplate)
                    .dnTemplate(dnTemplate)
                    .isReadOnly(isReadOnly);
    }

    private static PropertyMapper configurePropertyMapper(final JsonValue mapper, final String defaultLdapAttribute) {
        switch (mapper.get("type").required().asString()) {
        case "resourceType":
            return resourceType();
        case "constant":
            return constant(mapper.get("value").getObject());
        case "simple":
            return simple(mapper.get("ldapAttribute").defaultTo(defaultLdapAttribute).required().asString())
                    .defaultJsonValue(mapper.get("defaultJsonValue").getObject())
                    .isBinary(mapper.get("isBinary").defaultTo(false).asBoolean())
                    .isRequired(mapper.get("isRequired").defaultTo(false).asBoolean())
                    .isMultiValued(mapper.get("isMultiValued").defaultTo(false).asBoolean())
                    .writability(parseWritability(mapper));
        case "json":
            return json(mapper.get("ldapAttribute").defaultTo(defaultLdapAttribute).required().asString())
                    .defaultJsonValue(mapper.get("defaultJsonValue").getObject())
                    .isRequired(mapper.get("isRequired").defaultTo(false).asBoolean())
                    .isMultiValued(mapper.get("isMultiValued").defaultTo(false).asBoolean())
                    .jsonSchema(mapper.isDefined("schema") ? mapper.get("schema") : null)
                    .writability(parseWritability(mapper));
        case "reference":
            final String ldapAttribute = mapper.get("ldapAttribute")
                                               .defaultTo(defaultLdapAttribute).required().asString();
            final String baseDN = mapper.get("baseDn").required().asString();
            final String primaryKey = mapper.get("primaryKey").required().asString();
            final PropertyMapper m = configurePropertyMapper(mapper.get("mapper").required(), primaryKey);
            return reference(ldapAttribute, baseDN, primaryKey, m)
                    .isRequired(mapper.get("isRequired").defaultTo(false).asBoolean())
                    .isMultiValued(mapper.get("isMultiValued").defaultTo(false).asBoolean())
                    .searchFilter(mapper.get("searchFilter").defaultTo("(objectClass=*)").asString())
                    .writability(parseWritability(mapper));
        case "object":
            final JsonValue properties = mapper.get("properties");
            final ObjectPropertyMapper object = object();
            for (final String attribute : properties.keys()) {
                object.property(attribute, configurePropertyMapper(properties.get(attribute), attribute));
            }
            return object;
        default:
            throw newJsonValueException(mapper, ERR_CONFIG_NO_MAPPING_IN_CONFIGURATION.get(
                    "constant, simple, reference, object"));
        }
    }

    private static WritabilityPolicy parseWritability(final JsonValue mapper) {
        return mapper.get("writability").defaultTo("readWrite").as(enumConstant(WritabilityPolicy.class));
    }

    /** Indicates whether LDAP client connections should use SSL or StartTLS. */
    private enum ConnectionSecurity { NONE, SSL, STARTTLS }

    /** Specifies the mechanism which will be used for trusting certificates presented by the LDAP server. */
    private enum TrustManagerType { TRUSTALL, JVM, FILE }

    /** Specifies the type of key-store to use when performing SSL client authentication. */
    private enum KeyManagerType { JVM, FILE, PKCS11 }

    /**
     * Configures a {@link X509KeyManager} using the provided JSON configuration.
     *
     * @param configuration
     *         The JSON object containing the key manager configuration.
     * @return The configured key manager.
     */
    public static X509KeyManager configureKeyManager(final JsonValue configuration) {
        try {
            return configureKeyManager(configuration, KeyManagerType.JVM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(ERR_CONFIG_INVALID_KEY_MANAGER.get(
                    configuration.getPointer(), e.getLocalizedMessage()).toString(), e);
        }
    }

    private static X509KeyManager configureKeyManager(JsonValue config, KeyManagerType defaultIfMissing)
            throws GeneralSecurityException, IOException {
        final KeyManagerType keyManagerType = config.get("keyManager")
                                                    .defaultTo(defaultIfMissing)
                                                    .as(enumConstant(KeyManagerType.class));
        switch (keyManagerType) {
        case JVM:
            return useJvmDefaultKeyStore();
        case FILE:
            final String fileName = config.get("fileBasedKeyManagerFile").required().asString();
            final String passwordFile = config.get("fileBasedKeyManagerPasswordFile").asString();
            final String password = passwordFile != null
                    ? readPasswordFromFile(passwordFile) : config.get("fileBasedKeyManagerPassword").asString();
            final String type = config.get("fileBasedKeyManagerType").asString();
            final String provider = config.get("fileBasedKeyManagerProvider").asString();
            return useKeyStoreFile(fileName, password != null ? password.toCharArray() : null, type, provider);
        case PKCS11:
            final String pkcs11PasswordFile = config.get("pkcs11KeyManagerPasswordFile").asString();
            return usePKCS11Token(pkcs11PasswordFile != null
                                          ? readPasswordFromFile(pkcs11PasswordFile).toCharArray() : null);
        default:
            throw new IllegalArgumentException("Unsupported key-manager type: " + keyManagerType);
        }
    }

    private static String readPasswordFromFile(String fileName) throws IOException {
        try (final BufferedReader reader = new BufferedReader(new FileReader(new File(fileName)))) {
            return reader.readLine();
        }
    }

    /**
     * Configures a {@link TrustManager} using the provided JSON configuration.
     *
     * @param configuration
     *         The JSON object containing the trust manager configuration.
     * @return The configured trust manager.
     */
    public static TrustManager configureTrustManager(final JsonValue configuration) {
        try {
            return configureTrustManager(configuration, TrustManagerType.JVM);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(ERR_CONFIG_INVALID_TRUST_MANAGER.get(
                    configuration.getPointer(), e.getLocalizedMessage()).toString(), e);
        }
    }

    private static TrustManager configureTrustManager(JsonValue config, TrustManagerType defaultIfMissing)
            throws GeneralSecurityException, IOException {
        final TrustManagerType trustManagerType = config.get("trustManager")
                                                        .defaultTo(defaultIfMissing)
                                                        .as(enumConstant(TrustManagerType.class));
        switch (trustManagerType) {
        case TRUSTALL:
            return trustAll();
        case JVM:
            return null;
        case FILE:
            final String fileName = config.get("fileBasedTrustManagerFile").required().asString();
            final String passwordFile = config.get("fileBasedTrustManagerPasswordFile").asString();
            final String password = passwordFile != null
                    ? readPasswordFromFile(passwordFile) : config.get("fileBasedTrustManagerPassword").asString();
            final String type = config.get("fileBasedTrustManagerType").asString();
            return checkUsingTrustStore(fileName, password != null ? password.toCharArray() : null, type);
        default:
            throw new IllegalArgumentException("Unsupported trust-manager type: " + trustManagerType);
        }
    }

    /**
     * Creates a new connection factory using the named configuration in the provided JSON list of factory
     * configurations. See the sample configuration file for a detailed description of its content.
     *
     * @param configuration
     *         The JSON configuration.
     * @param name
     *         The name of the connection factory configuration to be parsed.
     * @param trustManager
     *            The trust manager to use for secure connection. Can be {@code null}
     *            to use the default JVM trust manager.
     * @param keyManager
     *            The key manager to use for secure connection. Can be {@code null}
     *            to use the default JVM key manager.
     * @param providerClassLoader
     *         The {@link ClassLoader} used to fetch the {@link org.forgerock.opendj.ldap.spi.TransportProvider}. This
     *         can be useful in OSGI environments.
     * @return A new connection factory using the provided JSON configuration.
     * @throws IllegalArgumentException
     *         If the configuration is invalid.
     */
    public static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
                                                               final String name,
                                                               final TrustManager trustManager,
                                                               final X509KeyManager keyManager,
                                                               final ClassLoader providerClassLoader) {
        final JsonValue normalizedConfiguration = normalizeConnectionFactory(configuration, name, 0);
        return configureConnectionFactory(normalizedConfiguration, trustManager, keyManager, providerClassLoader);
    }

    /**
     * Creates a new connection factory using the named configuration in the provided JSON list of factory
     * configurations. See the sample configuration file for a detailed description of its content.
     *
     * @param configuration
     *         The JSON configuration.
     * @param name
     *         The name of the connection factory configuration to be parsed.
     * @param trustManager
     *            The trust manager to use for secure connection. Can be {@code null}
     *            to use the default JVM trust manager.
     * @param keyManager
     *            The key manager to use for secure connection. Can be {@code null}
     *            to use the default JVM key manager.
     * @return A new connection factory using the provided JSON configuration.
     * @throws IllegalArgumentException
     *         If the configuration is invalid.
     */
    public static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
                                                               final String name,
                                                               final TrustManager trustManager,
                                                               final X509KeyManager keyManager) {
        return configureConnectionFactory(configuration, name, trustManager, keyManager, null);
    }

    private static ConnectionFactory configureConnectionFactory(final JsonValue configuration,
                                                                final TrustManager trustManager,
                                                                final X509KeyManager keyManager,
                                                                final ClassLoader providerClassLoader) {
        final long heartBeatIntervalSeconds = configuration.get("heartBeatIntervalSeconds").defaultTo(30L).asLong();
        final Duration heartBeatInterval = duration(Math.max(heartBeatIntervalSeconds, 1L), TimeUnit.SECONDS);

        final long heartBeatTimeoutMillis = configuration.get("heartBeatTimeoutMilliSeconds").defaultTo(500L).asLong();
        final Duration heartBeatTimeout = duration(Math.max(heartBeatTimeoutMillis, 100L), TimeUnit.MILLISECONDS);

        final Options options = Options.defaultOptions()
                                       .set(TRANSPORT_PROVIDER_CLASS_LOADER, providerClassLoader)
                                       .set(HEARTBEAT_ENABLED, true)
                                       .set(HEARTBEAT_INTERVAL, heartBeatInterval)
                                       .set(HEARTBEAT_TIMEOUT, heartBeatTimeout)
                                       .set(LOAD_BALANCER_MONITORING_INTERVAL, heartBeatInterval);

        // Parse pool parameters,
        final int connectionPoolSize =
                Math.max(configuration.get("connectionPoolSize").defaultTo(10).asInteger(), 1);

        // Parse authentication parameters.
        if (configuration.isDefined("authentication")) {
            final JsonValue authn = configuration.get("authentication");
            if (authn.isDefined("simple")) {
                final JsonValue simple = authn.get("simple");
                final BindRequest bindRequest =
                        Requests.newSimpleBindRequest(simple.get("bindDn").required().asString(),
                                                      simple.get("bindPassword").required().asString().toCharArray());
                options.set(AUTHN_BIND_REQUEST, bindRequest);
            } else {
                throw new LocalizedIllegalArgumentException(ERR_CONFIG_INVALID_AUTHENTICATION.get());
            }
        }

        // Parse SSL/StartTLS parameters.
        final ConnectionSecurity connectionSecurity = configuration.get("connectionSecurity")
                                                                   .defaultTo(ConnectionSecurity.NONE)
                                                                   .as(enumConstant(ConnectionSecurity.class));
        if (connectionSecurity != ConnectionSecurity.NONE) {
            try {
                // Configure SSL.
                final SSLContextBuilder builder = new SSLContextBuilder();
                builder.setTrustManager(trustManager);
                final String sslCertAlias = configuration.get("sslCertAlias").asString();
                builder.setKeyManager(sslCertAlias != null
                                              ? useSingleCertificate(sslCertAlias, keyManager)
                                              : keyManager);
                options.set(SSL_CONTEXT, builder.getSSLContext());
                options.set(SSL_USE_STARTTLS, connectionSecurity == ConnectionSecurity.STARTTLS);
            } catch (GeneralSecurityException e) {
                // Rethrow as unchecked exception.
                throw new IllegalArgumentException(e);
            }
        }

        // Parse primary data center.
        final JsonValue primaryLdapServers = configuration.get("primaryLdapServers");
        if (!primaryLdapServers.isList() || primaryLdapServers.size() == 0) {
            throw new IllegalArgumentException("No primaryLdapServers");
        }
        final ConnectionFactory primary = parseLdapServers(primaryLdapServers, connectionPoolSize, options);

        // Parse secondary data center(s).
        final JsonValue secondaryLdapServers = configuration.get("secondaryLdapServers");
        ConnectionFactory secondary = null;
        if (secondaryLdapServers.isList()) {
            if (secondaryLdapServers.size() > 0) {
                secondary = parseLdapServers(secondaryLdapServers, connectionPoolSize, options);
            }
        } else if (!secondaryLdapServers.isNull()) {
            throw new LocalizedIllegalArgumentException(ERR_CONFIG_INVALID_SECONDARY_LDAP_SERVER.get());
        }

        // Create fail-over.
        if (secondary != null) {
            return newFailoverLoadBalancer(asList(primary, secondary), options);
        } else {
            return primary;
        }
    }

    private static JsonValue normalizeConnectionFactory(final JsonValue configuration,
                                                        final String name, final int depth) {
        // Protect against infinite recursion in the configuration.
        if (depth > 100) {
            throw new LocalizedIllegalArgumentException(ERR_CONFIG_SERVER_CIRCULAR_DEPENDENCIES.get(name));
        }

        final JsonValue current = configuration.get(name).required();
        if (current.isDefined("inheritFrom")) {
            // Inherit missing fields from inherited configuration.
            final JsonValue parent =
                    normalizeConnectionFactory(configuration,
                                               current.get("inheritFrom").asString(), depth + 1);
            final Map<String, Object> normalized = new LinkedHashMap<>(parent.asMap());
            normalized.putAll(current.asMap());
            normalized.remove("inheritFrom");
            return new JsonValue(normalized);
        } else {
            // No normalization required.
            return current;
        }
    }

    private static ConnectionFactory parseLdapServers(JsonValue config, int poolSize, Options options) {
        final List<ConnectionFactory> servers = new ArrayList<>(config.size());
        for (final JsonValue server : config) {
            final String host = server.get("hostname").required().asString();
            final int port = server.get("port").required().asInteger();
            final ConnectionFactory factory = new LDAPConnectionFactory(host, port, options);
            if (poolSize > 1) {
                servers.add(newCachedConnectionPool(factory, 0, poolSize, 60L, TimeUnit.SECONDS));
            } else {
                servers.add(factory);
            }
        }
        if (servers.size() > 1) {
            return newRoundRobinLoadBalancer(servers, options);
        } else {
            return servers.get(0);
        }
    }

    private Rest2LdapJsonConfigurator() {
        // Prevent instantiation.
    }
}
