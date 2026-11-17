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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.config;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.config.PasswordPropertyDefinition;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.server.config.meta.LDAPPassThroughAuthenticationPolicyCfgDefn;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.forgerock.opendj.server.embedded.EmbeddedDirectoryServer;
import org.forgerock.opendj.server.config.server.LDAPPassThroughAuthenticationPolicyCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.extensions.AESPasswordStorageScheme;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class ConfigurationPasswordTestCase extends DirectoryServerTestCase {
    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
    }

    @Test
    public void addingMappedSearchPasswordEncryptsBeforePersistence() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Encrypted Password Test,cn=Password Policies,cn=config");
        Entry entry = policy(dn, "clear-test-secret");
        AtomicReference<String> validationValue = new AtomicReference<>();
        AtomicReference<String> applicationValue = new AtomicReference<>();
        ConfigAddListener listener = new ConfigAddListener() {
            @Override
            public boolean configAddIsAcceptable(Entry configEntry, LocalizableMessageBuilder reason) {
                validationValue.set(configEntry.getAttribute("ds-cfg-mapped-search-bind-password")
                        .firstValueAsString());
                return true;
            }

            @Override
            public ConfigChangeResult applyConfigurationAdd(Entry configEntry) {
                applicationValue.set(configEntry.getAttribute("ds-cfg-mapped-search-bind-password")
                        .firstValueAsString());
                return new ConfigChangeResult();
            }
        };
        handler.registerAddListener(dn.parent(), listener);
        try {
            handler.addEntry(entry);
            String stored = handler.getEntry(dn).getAttribute("ds-cfg-mapped-search-bind-password")
                    .firstValueAsString();
            assertTrue(stored.startsWith("{AES}"), stored);
            assertEquals(validationValue.get(), stored);
            assertEquals(applicationValue.get(), stored);
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("clear-test-secret"));
            assertEquals(configuration("Encrypted Password Test").getMappedSearchBindPassword(), "clear-test-secret");
        } finally {
            handler.deregisterAddListener(dn.parent(), listener);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void legacyPasswordMigratesOnlyWhenAccessed() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Legacy Password Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "legacy-secret"));
            installLegacyValue(handler, dn, "legacy-secret");
            Entry concurrentRead = rawBackend(handler).get(dn);
            LDAPPassThroughAuthenticationPolicyCfg cfg = configuration("Legacy Password Test");
            assertEquals(password(handler, dn), "legacy-secret", "Constructing a configuration must not migrate it");
            assertEquals(cfg.getMappedSearchBindPassword(), "legacy-secret");
            assertTrue(password(handler, dn).startsWith("{AES}"));
            assertEquals(concurrentRead.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString(),
                    "legacy-secret", "A reader holding the old entry must not see it mutated during migration");
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("legacy-secret"));
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void legacyMigrationDoesNotOverwriteAdministratorUpdate() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Password Race Test,cn=Password Policies,cn=config");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch configurationRead = new CountDownLatch(1);
        CountDownLatch allowPasswordRead = new CountDownLatch(1);
        try {
            handler.addEntry(policy(dn, "Legacy-Secret"));
            installLegacyValue(handler, dn, "Legacy-Secret");
            Future<String> oldPassword = executor.submit(() -> {
                LDAPPassThroughAuthenticationPolicyCfg oldCfg = configuration("Password Race Test");
                configurationRead.countDown();
                if (!allowPasswordRead.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for the administrator update");
                }
                return oldCfg.getMappedSearchBindPassword();
            });
            assertTrue(configurationRead.await(10, TimeUnit.SECONDS));
            Entry oldEntry = handler.getEntry(dn);
            Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            newEntry.replaceAttribute("ds-cfg-mapped-search-bind-password", "legacy-secret");
            handler.replaceEntry(oldEntry, newEntry);
            String updatedCiphertext = password(handler, dn);
            assertTrue(updatedCiphertext.startsWith("{AES}"));
            allowPasswordRead.countDown();
            assertEquals(oldPassword.get(10, TimeUnit.SECONDS), "Legacy-Secret");
            assertEquals(password(handler, dn), updatedCiphertext);
            assertEquals(configuration("Password Race Test").getMappedSearchBindPassword(), "legacy-secret");
        } finally {
            allowPasswordRead.countDown();
            executor.shutdownNow();
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void administratorUpdatePreparedBeforeMigrationStillSucceeds() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Migration First Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "old-legacy-value"));
            installLegacyValue(handler, dn, "old-legacy-value");
            Entry oldEntry = handler.getEntryPreservingPasswordValues(dn);
            Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            newEntry.replaceAttribute("ds-cfg-mapped-search-bind-password", "administrator-value");
            assertEquals(configuration("Migration First Test").getMappedSearchBindPassword(), "old-legacy-value");
            assertTrue(password(handler, dn).startsWith("{AES}"));
            handler.replaceEntry(oldEntry, newEntry, false, Collections.singleton(
                    oldEntry.getAttribute("ds-cfg-mapped-search-bind-password").getAttributeDescription()));
            assertEquals(configuration("Migration First Test").getMappedSearchBindPassword(), "administrator-value");
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void staleAdministratorUpdateDoesNotOverwriteNewPassword() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Stale Update Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "original-value"));
            Entry original = handler.getEntryPreservingPasswordValues(dn);
            Entry first = LinkedHashMapEntry.deepCopyOfEntry(original);
            Entry stale = LinkedHashMapEntry.deepCopyOfEntry(original);
            first.replaceAttribute("ds-cfg-mapped-search-bind-password", "first-administrator-value");
            stale.replaceAttribute("ds-cfg-mapped-search-bind-password", "stale-administrator-value");
            handler.replaceEntry(original, first);
            try {
                handler.replaceEntry(original, stale);
                fail("A stale password update must be rejected");
            } catch (DirectoryException expected) {
                assertEquals(expected.getResultCode(), ResultCode.BUSY);
            }
            assertEquals(configuration("Stale Update Test").getMappedSearchBindPassword(),
                    "first-administrator-value");
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void staleUnrelatedUpdateNotifiesListenersWithCurrentPassword() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Concurrent Other Property,cn=Password Policies,cn=config");
        AtomicReference<String> validated = new AtomicReference<>();
        AtomicReference<String> applied = new AtomicReference<>();
        ConfigChangeListener listener = new ConfigChangeListener() {
            public boolean configChangeIsAcceptable(Entry entry, LocalizableMessageBuilder reason) {
                validated.set(entry.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString());
                return true;
            }
            public ConfigChangeResult applyConfigurationChange(Entry entry) {
                applied.set(entry.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString());
                return new ConfigChangeResult();
            }
        };
        try {
            handler.addEntry(policy(dn, "old-secret"));
            Entry snapshot = handler.getEntryPreservingPasswordValues(dn);
            Entry otherUpdate = LinkedHashMapEntry.deepCopyOfEntry(snapshot);
            otherUpdate.replaceAttribute("ds-cfg-primary-remote-ldap-server", "localhost:1389");
            Entry passwordUpdate = LinkedHashMapEntry.deepCopyOfEntry(snapshot);
            passwordUpdate.replaceAttribute("ds-cfg-mapped-search-bind-password", "new-secret");
            handler.replaceEntry(snapshot, passwordUpdate);
            String current = password(handler, dn);
            handler.registerChangeListener(dn, listener);
            handler.replaceEntry(snapshot, otherUpdate);
            assertEquals(validated.get(), current);
            assertEquals(applied.get(), current);
            assertEquals(password(handler, dn), current);
        } finally {
            handler.deregisterChangeListener(dn, listener);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void failedConfigReplacementPreservesOriginalFile() throws Exception {
        Path directory = Files.createTempDirectory("opendj-config-replace");
        Path target = directory.resolve("config.ldif");
        try {
            Files.write(target, "original".getBytes(StandardCharsets.UTF_8));
            try {
                ConfigurationHandler.replaceConfigFile(directory.resolve("missing.tmp").toFile(), target.toFile());
                fail("Missing replacement must fail");
            } catch (java.io.IOException expected) {
                assertEquals(new String(Files.readAllBytes(target), StandardCharsets.UTF_8), "original");
            }
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void migrationDuringValidationIsPreservedWhenAnotherPasswordOptionChanges() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Migration During Validation,cn=Password Policies,cn=config");
        AtomicReference<String> applied = new AtomicReference<>();
        handler.addEntry(policy(dn, "legacy-secret"));
        installLegacyValue(handler, dn, "legacy-secret");
        LDAPPassThroughAuthenticationPolicyCfg cfg = configuration("Migration During Validation");
        ConfigChangeListener listener = new ConfigChangeListener() {
            public boolean configChangeIsAcceptable(Entry entry, LocalizableMessageBuilder reason) {
                assertEquals(cfg.getMappedSearchBindPassword(), "legacy-secret");
                return true;
            }
            public ConfigChangeResult applyConfigurationChange(Entry entry) {
                applied.set(entry.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString());
                return new ConfigChangeResult();
            }
        };
        handler.registerChangeListener(dn, listener);
        try {
            Entry oldEntry = handler.getEntryPreservingPasswordValues(dn);
            Entry updated = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            updated.addAttribute("ds-cfg-mapped-search-bind-password;lang-en", "option-secret");
            handler.replaceEntry(oldEntry, updated);
            assertTrue(password(handler, dn).startsWith("{AES}"));
            assertTrue(applied.get().startsWith("{AES}"));
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("legacy-secret"));
            assertFalse(ldif.contains("option-secret"));
        } finally {
            handler.deregisterChangeListener(dn, listener);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void preEncryptedPasswordIsPreserved() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Encoded Password Test,cn=Password Policies,cn=config");
        PasswordStorageScheme<?> aes = DirectoryServer.getPasswordStorageScheme("aes");
        String encoded = aes.encodePasswordWithScheme(ByteString.valueOfUtf8("already-protected")).toString();
        try {
            handler.addEntry(policy(dn, encoded));
            assertEquals(password(handler, dn), encoded);
            assertEquals(configuration("Encoded Password Test").getMappedSearchBindPassword(), "already-protected");
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void differentSchemeIsRejectedBeforePersistence() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Wrong Scheme Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "{SHA}not-a-password"));
            fail("A value encoded with another scheme must be rejected");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(dn));
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void differentSchemeCannotBypassValidationUsingExpression() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Scheme Expression Test,cn=Password Policies,cn=config");
        String key = "opendj1295scheme";
        String previous = System.getProperty(key);
        System.setProperty(key, "suffix");
        try {
            handler.addEntry(policy(dn, "{SHA}encoded${system['opendj1295scheme']}"));
            fail("An expression must not bypass scheme validation");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(dn));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void unavailableSchemeRejectsPlaintextBeforePersistence() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN policyDN = DN.valueOf("cn=Unavailable Scheme Test,cn=Password Policies,cn=config");
        DN schemeDN = DN.valueOf("cn=AES,cn=Password Storage Schemes,cn=config");
        PasswordStorageScheme<?> scheme = DirectoryServer.getPasswordStorageScheme(schemeDN);
        assertNotNull(scheme);
        DirectoryServer.deregisterPasswordStorageScheme(schemeDN);
        try {
            handler.addEntry(policy(policyDN, "must-not-persist"));
            fail("An unavailable encryption scheme must reject the update");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(policyDN));
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("must-not-persist"));
        } finally {
            DirectoryServer.registerPasswordStorageScheme(schemeDN, scheme);
            if (handler.hasEntry(policyDN)) {
                handler.deleteEntry(policyDN);
            }
        }
    }

    @Test
    public void unrelatedUpdateDoesNotMigrateLegacyPassword() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Unrelated Update Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "legacy-unrelated"));
            installLegacyValue(handler, dn, "legacy-unrelated");
            Entry oldEntry = handler.getEntry(dn);
            Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            newEntry.replaceAttribute("description", "unrelated change");
            handler.replaceEntry(oldEntry, newEntry);
            assertEquals(password(handler, dn), "legacy-unrelated");
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void explicitlyReplacingLegacyPasswordWithSameValueProtectsIt() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Same Password Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "same-legacy-password"));
            installLegacyValue(handler, dn, "same-legacy-password");
            ModifyOperation result = InternalClientConnection.getRootConnection().processModify(
                    Requests.newModifyRequest(dn).addModification(ModificationType.REPLACE,
                            "ds-cfg-mapped-search-bind-password", "same-legacy-password"));
            assertEquals(result.getResultCode(), ResultCode.SUCCESS, result.getErrorMessage().toString());
            assertTrue(password(handler, dn).startsWith("{AES}"));
            assertEquals(configuration("Same Password Test").getMappedSearchBindPassword(), "same-legacy-password");
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void allPasswordAttributeOptionsAreProtected() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Password Options Test,cn=Password Policies,cn=config");
        Entry entry = policy(dn, "unused");
        entry.removeAttribute("ds-cfg-mapped-search-bind-password");
        entry.addAttribute("ds-cfg-mapped-search-bind-password;lang-en", "english-secret");
        entry.addAttribute("ds-cfg-mapped-search-bind-password;lang-fr", "french-secret");
        try {
            handler.addEntry(entry);
            Entry stored = handler.getEntry(dn);
            assertTrue(stored.getAttribute("ds-cfg-mapped-search-bind-password;lang-en").firstValueAsString()
                    .startsWith("{AES}"));
            assertTrue(stored.getAttribute("ds-cfg-mapped-search-bind-password;lang-fr").firstValueAsString()
                    .startsWith("{AES}"));
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("english-secret"));
            assertFalse(ldif.contains("french-secret"));
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void unknownObjectClassCannotPersistKnownPasswordAttribute() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Unknown Password Test,cn=Default Password Policy,cn=Password Policies,cn=config");
        Entry entry = new LinkedHashMapEntry("dn: " + dn, "objectClass: top", "cn: Unknown Password Test",
                "ds-cfg-mapped-search-bind-password: must-not-leak");
        try {
            handler.addEntry(entry);
            fail("A password attribute without a matching configuration type must be rejected");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(dn));
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void sameAttributeUsedByNonPasswordDefinitionIsNotProtected() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Non Password Test,cn=Default Password Policy,cn=Password Policies,cn=config");
        LDAPProfile profile = LDAPProfile.getInstance();
        profile.pushWrapper(new LDAPProfile.Wrapper() {
            @Override
            public String getAttributeName(AbstractManagedObjectDefinition<?, ?> definition, PropertyDefinition<?> pd) {
                if (definition == GlobalCfgDefn.getInstance() && pd.getName().equals("allowed-task")) {
                    return "ds-cfg-mapped-search-bind-password";
                }
                return null;
            }
        });
        try {
            handler.addEntry(new LinkedHashMapEntry("dn: " + dn, "objectClass: top",
                    "objectClass: ds-cfg-root-config", "cn: Non Password Test",
                    "ds-cfg-mapped-search-bind-password: ordinary-string"));
            assertEquals(password(handler, dn), "ordinary-string");
        } finally {
            profile.popWrapper();
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void failedMigrationWriteDoesNotReportSuccess() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Migration Write Failure,cn=Password Policies,cn=config");
        Field field = ConfigurationHandler.class.getDeclaredField("configFile");
        field.setAccessible(true);
        File configFile = (File) field.get(handler);
        Path temporary = Files.createTempDirectory("opendj-password-test");
        try {
            handler.addEntry(policy(dn, "legacy-write-failure"));
            installLegacyValue(handler, dn, "legacy-write-failure");
            LDAPPassThroughAuthenticationPolicyCfg cfg = configuration("Migration Write Failure");
            File missingConfig = temporary.resolve("missing").resolve("config.ldif").toFile();
            field.set(handler, missingConfig);
            try {
                cfg.getMappedSearchBindPassword();
                fail("A failed migration write must be reported");
            } catch (IllegalStateException expected) {
                assertEquals(password(handler, dn), "legacy-write-failure");
            }
        } finally {
            field.set(handler, configFile);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
            Files.deleteIfExists(temporary);
        }
    }

    @Test
    public void failedPasswordAddWriteDoesNotReportSuccessOrKeepEntry() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Password Add Write Failure,cn=Password Policies,cn=config");
        Field field = ConfigurationHandler.class.getDeclaredField("configFile");
        field.setAccessible(true);
        File configFile = (File) field.get(handler);
        Path temporary = Files.createTempDirectory("opendj-password-add-test");
        try {
            field.set(handler, temporary.resolve("missing").resolve("config.ldif").toFile());
            try {
                handler.addEntry(policy(dn, "not-persisted-on-failure"));
                fail("A failed password configuration write must be reported");
            } catch (DirectoryException expected) {
                assertFalse(handler.hasEntry(dn));
            }
        } finally {
            field.set(handler, configFile);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
            Files.deleteIfExists(temporary);
        }
    }

    @Test
    public void failedPasswordUpdateWriteRestoresPreviousEntry() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Password Update Write Failure,cn=Password Policies,cn=config");
        Field field = ConfigurationHandler.class.getDeclaredField("configFile");
        field.setAccessible(true);
        File configFile = (File) field.get(handler);
        Path temporary = Files.createTempDirectory("opendj-password-update-test");
        try {
            handler.addEntry(policy(dn, "old-protected-secret"));
            Entry oldEntry = handler.getEntry(dn);
            Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            newEntry.replaceAttribute("ds-cfg-mapped-search-bind-password", "new-secret");
            String oldCiphertext = password(handler, dn);
            field.set(handler, temporary.resolve("missing").resolve("config.ldif").toFile());
            try {
                handler.replaceEntry(oldEntry, newEntry);
                fail("A failed password configuration write must be reported");
            } catch (DirectoryException expected) {
                assertEquals(password(handler, dn), oldCiphertext);
            }
        } finally {
            field.set(handler, configFile);
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
            Files.deleteIfExists(temporary);
        }
    }

    @Test
    public void invalidEncryptedValueIsRejected() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Invalid Ciphertext Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "{AES}invalid-ciphertext"));
            fail("Invalid ciphertext must be rejected before persistence");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(dn));
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void decryptionFailureIsReportedToServerConsumer() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Decryption Failure Test,cn=Password Policies,cn=config");
        try {
            handler.addEntry(policy(dn, "valid-before-corruption"));
            installLegacyValue(handler, dn, "{AES}invalid-ciphertext");
            LDAPPassThroughAuthenticationPolicyCfg cfg = configuration("Decryption Failure Test");
            try {
                cfg.getMappedSearchBindPassword();
                fail("A decryption failure must not return ciphertext or plaintext");
            } catch (IllegalStateException expected) {
                assertEquals(password(handler, dn), "{AES}invalid-ciphertext");
            }
        } finally {
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void encryptionFailureRejectsUpdateBeforePersistence() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN policyDN = DN.valueOf("cn=Encryption Failure Test,cn=Password Policies,cn=config");
        DN schemeDN = DN.valueOf("cn=AES,cn=Password Storage Schemes,cn=config");
        PasswordStorageScheme<?> original = DirectoryServer.getPasswordStorageScheme(schemeDN);
        assertNotNull(original);
        PasswordStorageScheme<?> failing = new AESPasswordStorageScheme() {
            @Override
            public ByteString encodePasswordWithScheme(ByteSequence value) throws DirectoryException {
                throw new DirectoryException(ResultCode.OTHER, LocalizableMessage.raw("Simulated encryption failure"));
            }
        };
        DirectoryServer.deregisterPasswordStorageScheme(schemeDN);
        DirectoryServer.registerPasswordStorageScheme(schemeDN, failing);
        try {
            handler.addEntry(policy(policyDN, "must-not-write-after-failure"));
            fail("An encryption failure must reject the update");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(policyDN));
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertFalse(ldif.contains("must-not-write-after-failure"));
        } finally {
            DirectoryServer.deregisterPasswordStorageScheme(schemeDN);
            DirectoryServer.registerPasswordStorageScheme(schemeDN, original);
            if (handler.hasEntry(policyDN)) {
                handler.deleteEntry(policyDN);
            }
        }
    }

    @Test
    public void nonDefaultReversibleSchemeIsUsed() throws Exception {
        PasswordPropertyDefinition.Builder builder = PasswordPropertyDefinition.createBuilder(
                LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance(), "test-scheme-override");
        builder.setEncryptionScheme("3DES");
        PasswordPropertyDefinition definition = builder.getInstance();
        Field field = ConfigurationHandler.class.getDeclaredField("PASSWORD_CODEC");
        field.setAccessible(true);
        PasswordPropertyDefinition.Codec codec = (PasswordPropertyDefinition.Codec) field.get(null);
        String encoded = definition.encodeForStorage("non-default-secret", codec);
        assertTrue(encoded.startsWith("{3DES}"));
        assertEquals(definition.decodeFromStorage(encoded, codec), "non-default-secret");
    }

    @Test
    public void expressionRemainsDynamicAndIsNotMigrated() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Password Expression Test,cn=Password Policies,cn=config");
        String key = "opendj1295pw";
        String previous = System.getProperty(key);
        System.setProperty(key, "expression-secret-one");
        try {
            handler.addEntry(policy(dn, "${system['opendj1295pw']}"));
            assertEquals(configuration("Password Expression Test").getMappedSearchBindPassword(),
                    "expression-secret-one");
            System.setProperty(key, "expression-secret-two");
            assertEquals(configuration("Password Expression Test").getMappedSearchBindPassword(),
                    "expression-secret-two");
            String ldif = new String(Files.readAllBytes(handler.getConfigurationFile().toPath()), StandardCharsets.UTF_8);
            assertTrue(ldif.contains("${system['opendj1295pw']}"));
            assertFalse(ldif.contains("expression-secret-one"));
            assertFalse(ldif.contains("expression-secret-two"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void ldapSearchDoesNotRevealResolvedPasswordExpression() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Expression Search Test,cn=Password Policies,cn=config");
        String key = "opendj1295search";
        String previous = System.getProperty(key);
        System.setProperty(key, "search-secret");
        try {
            handler.addEntry(policy(dn, "${system['opendj1295search']}"));
            InternalSearchOperation search = InternalClientConnection.getRootConnection().processSearch(
                    org.opends.server.protocols.internal.Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT));
            assertEquals(search.getResultCode(), ResultCode.SUCCESS);
            assertEquals(search.getSearchEntries().size(), 1);
            org.opends.server.types.Entry result = search.getSearchEntries().get(0);
            org.forgerock.opendj.ldap.schema.AttributeType type = TestCaseUtils.getServerContext().getSchema()
                    .getAttributeType("ds-cfg-mapped-search-bind-password");
            assertTrue(result.hasValue(type, ByteString.valueOfUtf8("${system['opendj1295search']}")));
            assertFalse(result.hasValue(type, ByteString.valueOfUtf8("search-secret")));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void ldapCanReplacePasswordExpression() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Expression Replacement Test,cn=Password Policies,cn=config");
        String key = "opendj1295replace";
        String previous = System.getProperty(key);
        System.setProperty(key, "old-expression-secret");
        try {
            handler.addEntry(policy(dn, "${system['opendj1295replace']}"));
            ModifyOperation result = InternalClientConnection.getRootConnection().processModify(
                    Requests.newModifyRequest(dn).addModification(ModificationType.REPLACE,
                            "ds-cfg-mapped-search-bind-password", "replacement-secret"));
            assertEquals(result.getResultCode(), ResultCode.SUCCESS, result.getErrorMessage().toString());
            assertTrue(password(handler, dn).startsWith("{AES}"));
            assertEquals(configuration("Expression Replacement Test").getMappedSearchBindPassword(),
                    "replacement-secret");
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    @Test
    public void unavailableSchemeRejectsPasswordExpressionMutation() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN policyDN = DN.valueOf("cn=Unavailable Expression Test,cn=Password Policies,cn=config");
        DN schemeDN = DN.valueOf("cn=AES,cn=Password Storage Schemes,cn=config");
        PasswordStorageScheme<?> scheme = DirectoryServer.getPasswordStorageScheme(schemeDN);
        assertNotNull(scheme);
        String key = "opendj1295offline";
        String previous = System.getProperty(key);
        System.setProperty(key, "protected-value");
        DirectoryServer.deregisterPasswordStorageScheme(schemeDN);
        try {
            handler.addEntry(policy(policyDN, "${system['opendj1295offline']}"));
            fail("An offline password property mutation must be rejected");
        } catch (DirectoryException expected) {
            assertFalse(handler.hasEntry(policyDN));
        } finally {
            DirectoryServer.registerPasswordStorageScheme(schemeDN, scheme);
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
            if (handler.hasEntry(policyDN)) {
                handler.deleteEntry(policyDN);
            }
        }
    }

    @Test
    public void unavailableSchemeRejectsPasswordRemoval() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN policyDN = DN.valueOf("cn=Unavailable Removal Test,cn=Password Policies,cn=config");
        DN schemeDN = DN.valueOf("cn=AES,cn=Password Storage Schemes,cn=config");
        PasswordStorageScheme<?> scheme = DirectoryServer.getPasswordStorageScheme(schemeDN);
        assertNotNull(scheme);
        try {
            handler.addEntry(policy(policyDN, "existing-secret"));
            Entry oldEntry = handler.getEntry(policyDN);
            Entry newEntry = LinkedHashMapEntry.deepCopyOfEntry(oldEntry);
            newEntry.removeAttribute("ds-cfg-mapped-search-bind-password");
            DirectoryServer.deregisterPasswordStorageScheme(schemeDN);
            try {
                handler.replaceEntry(oldEntry, newEntry, false, Collections.singleton(
                        oldEntry.getAttribute("ds-cfg-mapped-search-bind-password").getAttributeDescription()));
                fail("A password property mutation must fail when the scheme is unavailable");
            } catch (DirectoryException expected) {
                assertEquals(password(handler, policyDN),
                        oldEntry.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString());
            }
            try {
                handler.replaceEntry(oldEntry, newEntry);
                fail("Direct offline password removal must also require the selected scheme");
            } catch (DirectoryException expected) {
                assertEquals(password(handler, policyDN),
                        oldEntry.getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString());
            }
        } finally {
            DirectoryServer.registerPasswordStorageScheme(schemeDN, scheme);
            if (handler.hasEntry(policyDN)) {
                handler.deleteEntry(policyDN);
            }
        }
    }

    @Test(priority = 100)
    public void manuallyEditedLegacyValueSurvivesRestartAndMigratesOnUse() throws Exception {
        ConfigurationHandler handler = TestCaseUtils.getServerContext().getConfigurationHandler();
        DN dn = DN.valueOf("cn=Manual Legacy Test,cn=Password Policies,cn=config");
        Field serverField = TestCaseUtils.class.getDeclaredField("server");
        serverField.setAccessible(true);
        EmbeddedDirectoryServer server = (EmbeddedDirectoryServer) serverField.get(null);
        boolean running = true;
        try {
            handler.addEntry(policy(dn, "manual-legacy-secret"));
            File configFile = handler.getConfigurationFile();
            server.stop(getClass().getSimpleName(), LocalizableMessage.raw("Test legacy configuration"));
            running = false;
            String ldif = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            String attribute = "ds-cfg-mapped-search-bind-password:";
            int start = ldif.indexOf(attribute);
            assertTrue(start >= 0);
            int end = ldif.indexOf('\n', start);
            assertTrue(end >= 0);
            while (end + 1 < ldif.length() && ldif.charAt(end + 1) == ' ') {
                end = ldif.indexOf('\n', end + 1);
                assertTrue(end >= 0);
            }
            String edited = ldif.substring(0, start) + attribute + " manual-legacy-secret\n"
                    + ldif.substring(end + 1);
            Files.write(configFile.toPath(), edited.getBytes(StandardCharsets.UTF_8));
            server.start();
            running = true;

            handler = TestCaseUtils.getServerContext().getConfigurationHandler();
            assertEquals(password(handler, dn), "manual-legacy-secret", "Startup must not rewrite legacy values");
            assertEquals(configuration("Manual Legacy Test").getMappedSearchBindPassword(), "manual-legacy-secret");
            assertTrue(password(handler, dn).startsWith("{AES}"));
            assertFalse(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8)
                    .contains("manual-legacy-secret"));

            server.stop(getClass().getSimpleName(), LocalizableMessage.raw("Test encrypted configuration restart"));
            running = false;
            server.start();
            running = true;
            assertEquals(configuration("Manual Legacy Test").getMappedSearchBindPassword(), "manual-legacy-secret");
        } finally {
            if (!running) {
                server.start();
            }
            handler = TestCaseUtils.getServerContext().getConfigurationHandler();
            if (handler.hasEntry(dn)) {
                handler.deleteEntry(dn);
            }
        }
    }

    private static Entry policy(DN dn, String secret) {
        return new LinkedHashMapEntry(
                "dn: " + dn,
                "objectClass: top",
                "objectClass: ds-cfg-authentication-policy",
                "objectClass: ds-cfg-ldap-pass-through-authentication-policy",
                "cn: " + dn.rdn().getFirstAVA().getAttributeValue(),
                "ds-cfg-java-class: org.opends.server.extensions.LDAPPassThroughAuthenticationPolicyFactory",
                "ds-cfg-primary-remote-ldap-server: localhost:389",
                "ds-cfg-mapping-policy: unmapped",
                "ds-cfg-use-password-caching: false",
                "ds-cfg-mapped-search-bind-password: " + secret);
    }

    private static LDAPPassThroughAuthenticationPolicyCfg configuration(String name) throws Exception {
        return (LDAPPassThroughAuthenticationPolicyCfg) TestCaseUtils.getServerContext()
                .getServerManagementContext().getRootConfiguration().getPasswordPolicy(name);
    }

    private static String password(ConfigurationHandler handler, DN dn) throws Exception {
        return handler.getEntry(dn).getAttribute("ds-cfg-mapped-search-bind-password").firstValueAsString();
    }

    private static void installLegacyValue(ConfigurationHandler handler, DN dn, String value) throws Exception {
        // Simulates an unprefixed value loaded from a manually edited legacy config.ldif.
        rawBackend(handler).get(dn).replaceAttribute("ds-cfg-mapped-search-bind-password", value);
    }

    private static MemoryBackend rawBackend(ConfigurationHandler handler) throws Exception {
        Field field = ConfigurationHandler.class.getDeclaredField("backend");
        field.setAccessible(true);
        return (MemoryBackend) field.get(handler);
    }
}
