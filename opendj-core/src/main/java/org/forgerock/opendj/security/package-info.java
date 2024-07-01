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

/**
 * An LDAP based security provider having the name "OpenDJ" and exposing an LDAP/LDIF based {@link
 * java.security.KeyStore KeyStore} service. The key store has the type "LDAP" and alias "OPENDJ" and can be created
 * using a number of approaches. Firstly, by directly calling one of the factory methods in {@link
 * org.forgerock.opendj.security.OpenDJProvider}:
 * <p>
 * <pre>
 * ConnectionFactory ldapServer = ...;
 * DN keyStoreBaseDN = DN.valueOf("ou=key store,dc=example,dc=com");
 * Options options = Options.defaultOptions();
 *
 * KeyStore ldapKeyStore = OpenDJProvider.newLDAPKeyStore(ldapServer, keyStoreBaseDN, options);
 * </pre>
 * <p>
 * Alternatively, if the OpenDJ security provider is registered with the JVM's JCA framework together with a suitable
 * configuration file, then an LDAP key store can be created like this:
 * <p>
 * <pre>
 * KeyStore ldapKeyStore = KeyStore.getInstance("LDAP");
 * ldapKeyStore.load(null);
 * </pre>
 * <p>
 * The configuration file should be specified as the provider argument in the JVM's security configuration. It supports
 * the following options:
 * <pre>
 * # If this option is set then the LDAP key store will be LDIF file based. This is useful for testing.
 * org.forgerock.opendj.security.ldif=/path/to/keystore.ldif
 *
 * # Otherwise use LDAP. Note that only a simple single-server configuration is supported for now since applications
 * # are expected to configure directly using KeyStore.load(KeyStoreParameters).
 * org.forgerock.opendj.security.host=localhost
 * org.forgerock.opendj.security.port=1389
 * org.forgerock.opendj.security.bindDN=cn=directory manager
 * org.forgerock.opendj.security.bindPassword=password
 *
 * # The base DN beneath which key store entries will be located.
 * org.forgerock.opendj.security.keyStoreBaseDN=ou=key store,dc=example,dc=com
 * </pre>
 * <p>
 * Interacting with an LDAP/LDIF key store using Java's "keytool" command is a little complicated if the OpenDJ provider
 * is not configured in the JVM due to the need to specify the class-path:
 * <p>
 * <pre>
 * # Generate an RSA private key entry:
 * keytool -J-cp -J/path/to/opendj/server/lib/bootstrap-client.jar \
 *         -providerName OpenDJ -providerClass org.forgerock.opendj.security.OpenDJProvider \
 *         -providerArg /path/to/keystore.conf \
 *         -storetype LDAP -keystore NONE -storepass changeit -keypass changeit \
 *         -genkey -alias "private-key" -keyalg rsa \
 *         -ext "san=dns:localhost.example.com" \
 *         -dname "CN=localhost.example.com,O=Example Corp,C=FR"
 *
 * # Generate an AES secret key entry:
 * keytool -J-cp -J/path/to/opendj/server/lib/bootstrap-client.jar \
 *         -providerName OpenDJ -providerClass org.forgerock.opendj.security.OpenDJProvider \
 *         -providerArg /path/to/keystore.conf \
 *         -storetype LDAP -keystore NONE -storepass changeit -keypass changeit \
 *         -genseckey -alias "secret-key" -keyalg AES -keysize 128
 *
 * # Import a trusted certificate from raw ASN1 content:
 * keytool -J-cp -J/path/to/opendj/server/lib/bootstrap-client.jar \
 *         -providerName OpenDJ -providerClass org.forgerock.opendj.security.OpenDJProvider \
 *         -providerArg /path/to/keystore.conf \
 *         -storetype LDAP -keystore NONE -storepass changeit -keypass changeit \
 *         -importcert -alias "trusted-cert" -file /path/to/cert.crt
 *
 * # Import a trusted certificate from PEM file:
 * keytool -J-cp -J/path/to/opendj/server/lib/bootstrap-client.jar \
 *         -providerName OpenDJ -providerClass org.forgerock.opendj.security.OpenDJProvider \
 *         -providerArg /path/to/keystore.conf \
 *         -storetype LDAP -keystore NONE -storepass changeit -keypass changeit \
 *         -importcert -alias "trusted-cert" -file /path/to/cert.pem
 *
 * # List the contents of the key store:
 * keytool -J-cp -J/path/to/opendj/server/lib/bootstrap-client.jar \
 *         -providerName OpenDJ -providerClass org.forgerock.opendj.security.OpenDJProvider \
 *         -providerArg /path/to/keystore.conf \
 *         -storetype LDAP -keystore NONE -storepass changeit -keypass changeit \
 *         -list -v
 * </pre>
 * <p>
 * The LDAP key store will store objects in entries directly beneath the key store's base DN. The base DN entry is
 * expected to already exist. Private key and secret key entries are protected by 128-bit AES symmetric key derived
 * using PBKDF2 from the key's password, if provided, and the key store's global password, if provided. If both
 * passwords are provided then the keys will be encrypted twice. This does not provide additional protection but does
 * provide more control over access to a single key store. For example, multiple applications may be able to access a
 * single key store, with each application protecting their sensitive data using their individual password.
 * <p>
 * The LDAP schema used for the key store is contained in this JAR as resource and can be obtained using {@link
 * org.forgerock.opendj.security.OpenDJProviderSchema#getSchemaLDIFResource()}. Alternatively, clients may build a
 * {@link org.forgerock.opendj.ldap.schema.Schema Schema} using the method
 * {@link org.forgerock.opendj.security.OpenDJProviderSchema#addOpenDJProviderSchema}.
 */
package org.forgerock.opendj.security;

