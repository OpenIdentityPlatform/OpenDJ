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

import static com.forgerock.opendj.security.KeystoreMessages.*;
import static org.forgerock.opendj.ldap.Entries.diffEntries;
import static org.forgerock.opendj.ldap.Filter.and;
import static org.forgerock.opendj.ldap.Filter.equality;
import static org.forgerock.opendj.ldap.SearchScope.SINGLE_LEVEL;
import static org.forgerock.opendj.ldap.requests.Requests.newDeleteRequest;
import static org.forgerock.opendj.ldap.requests.Requests.newSearchRequest;
import static org.forgerock.opendj.security.KeyStoreObject.*;
import static org.forgerock.opendj.security.KeyStoreParameters.CACHE;
import static org.forgerock.opendj.security.KeyStoreParameters.GLOBAL_PASSWORD;
import static org.forgerock.opendj.security.KeyStoreParameters.newKeyStoreParameters;
import static org.forgerock.opendj.security.OpenDJProvider.newClearTextPasswordFactory;
import static org.forgerock.opendj.security.OpenDJProviderSchema.ATTR_CERTIFICATE_BINARY;
import static org.forgerock.util.Options.copyOf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConstraintViolationException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Options;

/** LDAP Key Store implementation. This class should not be used directly. */
final class KeyStoreImpl extends KeyStoreSpi {
    private static final LocalizedLogger logger = LocalizedLogger.getLocalizedLogger(KeyStoreImpl.class);

    private static final String[] SEARCH_ATTR_LIST = { "*", "createTimeStamp", "modifyTimeStamp" };
    private static final Filter FILTER_KEYSTORE_OBJECT = Filter.valueOf("(objectClass=ds-keystore-object)");

    private final OpenDJProvider provider;
    private KeyStoreParameters config;
    private KeyStoreObjectCache cache;
    private KeyProtector keyProtector;

    KeyStoreImpl(final OpenDJProvider provider) {
        this.provider = provider;
    }

    @Override
    public Key engineGetKey(final String alias, final char[] password)
            throws NoSuchAlgorithmException, UnrecoverableKeyException {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null ? object.getKey(keyProtector, password) : null;
    }

    @Override
    public Certificate[] engineGetCertificateChain(final String alias) {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null ? object.getCertificateChain() : null;
    }

    @Override
    public Certificate engineGetCertificate(final String alias) {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null ? object.getCertificate() : null;
    }

    @Override
    public Date engineGetCreationDate(final String alias) {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null ? object.getCreationDate() : null;
    }

    @Override
    public void engineSetKeyEntry(final String alias, final Key key, final char[] password, final Certificate[] chain)
            throws KeyStoreException {
        writeKeyStoreObject(newKeyObject(alias, key, chain, keyProtector, password));
    }

    @Override
    public void engineSetKeyEntry(final String alias, final byte[] key, final Certificate[] chain)
            throws KeyStoreException {
        // TODO: It's unclear how this method should be implemented as well as who or what calls it. This method does
        // not seem to be called from JDK code.
        throw new UnsupportedOperationException();
    }

    @Override
    public void engineSetCertificateEntry(final String alias, final Certificate cert) throws KeyStoreException {
        final KeyStoreObject object = readKeyStoreObject(alias);
        if (object != null && !object.isTrustedCertificate()) {
            throw new LocalizedKeyStoreException(KEYSTORE_KEY_ENTRY_ALREADY_EXISTS.get(alias));
        }
        writeKeyStoreObject(newTrustedCertificateObject(alias, cert));
    }

    @Override
    public void engineDeleteEntry(final String alias) throws KeyStoreException {
        try (final Connection connection = config.getConnection()) {
            connection.delete(newDeleteRequest(dnOf(config.getBaseDN(), alias)));
        } catch (final EntryNotFoundException ignored) {
            // Ignore: this is what other key store implementations seem to do.
        } catch (final LdapException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_DELETE_FAILURE.get(alias), e);
        }
    }

    @Override
    public Enumeration<String> engineAliases() {
        final SearchRequest searchRequest = newSearchRequest(config.getBaseDN(),
                                                             SINGLE_LEVEL,
                                                             FILTER_KEYSTORE_OBJECT,
                                                             "1.1");
        try (final Connection connection = config.getConnection();
             final ConnectionEntryReader reader = connection.search(searchRequest)) {
            final List<String> aliases = new ArrayList<>();
            while (reader.hasNext()) {
                if (reader.isEntry()) {
                    aliases.add(aliasOf(reader.readEntry()));
                }
            }
            return Collections.enumeration(aliases);
        } catch (final IOException e) {
            // There's not much we can do here except log a warning and return an empty list of aliases.
            logger.warn(KEYSTORE_READ_FAILURE.get(), e);
            return Collections.emptyEnumeration();
        }
    }

    private String aliasOf(final Entry entry) {
        return entry.getName().rdn().getFirstAVA().getAttributeValue().toString();
    }

    @Override
    public boolean engineContainsAlias(final String alias) {
        return readKeyStoreObject(alias) != null;
    }

    @Override
    public int engineSize() {
        try (final Connection connection = config.getConnection()) {
            final Entry baseEntry = connection.readEntry(config.getBaseDN(), "numSubordinates");
            return baseEntry.parseAttribute("numSubordinates").asInteger(0);
        } catch (final LdapException e) {
            // There's not much we can do here except log a warning and return 0.
            logger.warn(KEYSTORE_READ_FAILURE.get(), e);
        }
        return 0;
    }

    @Override
    public boolean engineIsKeyEntry(final String alias) {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null && !object.isTrustedCertificate();
    }

    @Override
    public boolean engineIsCertificateEntry(final String alias) {
        final KeyStoreObject object = readKeyStoreObject(alias);
        return object != null && object.isTrustedCertificate();
    }

    @Override
    public String engineGetCertificateAlias(final Certificate cert) {
        final Filter filter = and(FILTER_KEYSTORE_OBJECT,
                                  equality(ATTR_CERTIFICATE_BINARY, getCertificateAssertion(cert)));
        final SearchRequest searchRequest = newSearchRequest(config.getBaseDN(), SINGLE_LEVEL, filter, "1.1");
        try (final Connection connection = config.getConnection();
             final ConnectionEntryReader reader = connection.search(searchRequest)) {
            while (reader.hasNext()) {
                if (reader.isEntry()) {
                    return aliasOf(reader.readEntry());
                }
            }
        } catch (IOException e) {
            // There's not much we can do here except log a warning and return null.
            logger.warn(KEYSTORE_READ_FAILURE.get(), e);
        }
        return null;
    }

    private String getCertificateAssertion(final Certificate cert) {
        final X509Certificate x509Certificate = (X509Certificate) cert;
        final BigInteger serialNumber = x509Certificate.getSerialNumber();
        final String issuerDn = x509Certificate.getIssuerX500Principal().getName().replaceAll("\"", "\"\"");
        return String.format("{serialNumber %s,issuer rdnSequence:\"%s\"}", serialNumber, issuerDn);
    }

    /** No op: the LDAP key store performs updates immediately against the backend LDAP server. */
    @Override
    public void engineStore(final OutputStream stream, final char[] password) {
        if (stream != null) {
            throw new IllegalArgumentException("the LDAP key store is not file based");
        }
        engineStore(null);
    }

    @Override
    public void engineStore(final LoadStoreParameter param) {
        // Do nothing - data is written immediately.
    }

    /**
     * The LDAP key store cannot be loaded from an input stream, so this method can only be called if the provider
     * has been configured.
     */
    @Override
    public void engineLoad(final InputStream stream, final char[] password) {
        if (stream != null) {
            throw new IllegalArgumentException("the LDAP key store is not file based");
        } else if (provider.getDefaultConfig() == null || password == null || password.length == 0) {
            engineLoad(null);
        } else {
            // Use the default options except for the provided password.
            final KeyStoreParameters defaultConfig = provider.getDefaultConfig();
            final Options options = copyOf(defaultConfig.getOptions())
                    .set(GLOBAL_PASSWORD, newClearTextPasswordFactory(password));
            engineLoad(newKeyStoreParameters(defaultConfig.getConnectionFactory(), defaultConfig.getBaseDN(), options));
        }
    }

    @Override
    public void engineLoad(final LoadStoreParameter param) {
        if (param != null) {
            try {
                config = (KeyStoreParameters) param;
            } catch (final ClassCastException e) {
                throw new IllegalArgumentException("load must be called with KeyStoreParameters class");
            }
        } else if (provider.getDefaultConfig() != null) {
            config = provider.getDefaultConfig();
        } else {
            throw new IllegalArgumentException("the LDAP key store must be configured using KeyStoreParameters "
                                                       + "or using the security provider's configuration file");
        }
        keyProtector = new KeyProtector(config.getOptions());
        cache = config.getOptions().get(CACHE);
    }

    private KeyStoreObject readKeyStoreObject(final String alias) {
        // See if it is in cache first.
        final KeyStoreObject cachedKeyStoreObject = readCache(alias);
        if (cachedKeyStoreObject != null) {
            return cachedKeyStoreObject;
        }
        try (final Connection connection = config.getConnection()) {
            final Entry ldapEntry = connection.readEntry(dnOf(config.getBaseDN(), alias), SEARCH_ATTR_LIST);
            return writeCache(KeyStoreObject.valueOf(ldapEntry));
        } catch (EntryNotFoundException ignored) {
            // The requested key does not exist - fall through.
        } catch (LocalizedKeyStoreException | IOException e) {
            // There's not much we can do here except log a warning and assume the key does not exist.
            logger.warn(KEYSTORE_READ_ALIAS_FAILURE.get(alias), e);
        }
        return null;
    }


    private void writeKeyStoreObject(final KeyStoreObject keyStoreObject) throws LocalizedKeyStoreException {
        try (final Connection connection = config.getConnection()) {
            final Entry newLdapEntry = keyStoreObject.toLDAPEntry(config.getBaseDN());
            try {
                connection.add(newLdapEntry);
            } catch (ConstraintViolationException e) {
                if (e.getResult().getResultCode() != ResultCode.ENTRY_ALREADY_EXISTS) {
                    throw e; // Give up.
                }
                final Entry oldLdapEntry = connection.readEntry(newLdapEntry.getName());
                connection.modify(diffEntries(oldLdapEntry, newLdapEntry));
            }
            writeCache(keyStoreObject);
        } catch (final IOException e) {
            throw new LocalizedKeyStoreException(KEYSTORE_UPDATE_ALIAS_FAILURE.get(keyStoreObject.getAlias()), e);
        }
    }

    private KeyStoreObject writeCache(final KeyStoreObject keyStoreObject) {
        cache.put(keyStoreObject);
        return keyStoreObject;
    }

    private KeyStoreObject readCache(final String alias) {
        return cache.get(alias);
    }
}
