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
package org.opends.server.tools.upgrade;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.opends.messages.ToolMessages.INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_CONSISTENT;
import static org.opends.messages.ToolMessages.INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_INCONSISTENT;
import static org.opends.messages.ToolMessages.INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_STARTS;
import static org.opends.messages.ToolMessages.WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_FAILED;
import static org.opends.messages.ToolMessages.WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_SKIPPED;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextOutputCallback;

import com.forgerock.opendj.cli.ClientException;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.pluggable.IndexKeyRemover;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.tools.VerifyIndex;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;

/**
 * Tests the upgrade task that verifies the equality indexes of the attributes holding DNs, and rebuilds them where
 * they miss a key (issue #1153).
 */
@SuppressWarnings("javadoc")
public class DNEqualityIndexesUpgradeTestCase extends DirectoryServerTestCase
{
  private static final String BACKEND_ID = "dnEqualityIndexes";
  private static final String BACKEND_DN = "ds-cfg-backend-id=" + BACKEND_ID + ",cn=Backends,cn=config";
  private static final String BASE_DN = "o=dn equality indexes";
  private static final Set<String> MEMBER = Collections.singleton("member");
  private static final String MY_MANAGER_AS_DN =
      "( 1.3.6.1.4.1.26027.1.999.1153 NAME 'myManager' SUP distinguishedName )";
  private static final String MY_MANAGER_AS_STRING = "( 1.3.6.1.4.1.26027.1.999.1153 NAME 'myManager' "
      + "EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )";

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * Only the equality indexes whose matching rule compares DNs are found, in enabled pluggable backends; the
   * matching rule of an attribute of a custom schema is read from the schema files.
   */
  @Test
  public void findsTheEqualityIndexesThatCompareDNs() throws Exception
  {
    final File directory = Files.createTempDirectory("dn-equality-indexes").toFile();
    try
    {
      final File config = writeConfig(directory,
          backend("userRoot", "userRoot", "true"),
          index("userRoot", "member", "equality"),
          index("userRoot", "uniqueMember", "equality", "presence"),
          index("userRoot", "seeAlso", "Equality"),
          index("userRoot", "myManager", "equality"),
          index("userRoot", "owner", "presence"),
          index("userRoot", "cn", "equality", "substring"),
          // A backend ID that a DN has to escape
          backend("a\\,b", "a,b", "true"),
          index("a\\,b", "member", "equality"),
          // A disabled backend, and one without an index that compares DNs
          backend("disabledRoot", "disabledRoot", "false"),
          index("disabledRoot", "member", "equality"),
          backend("otherRoot", "otherRoot", "true"),
          index("otherRoot", "cn", "equality"));
      final File schemaDirectory = new File(directory, "schema");
      writeSchema(schemaDirectory, "99-user.ldif", MY_MANAGER_AS_DN);

      final Map<String, Set<String>> attributes =
          UpgradeUtils.getDNEqualityIndexedAttributesPerBackend(config, schemaDirectory);

      assertThat(attributes.keySet()).containsExactly("a,b", "userRoot");
      assertThat(attributes.get("userRoot")).containsExactly("member", "myManager", "seeAlso", "uniqueMember");
      assertThat(attributes.get("a,b")).containsExactly("member");
    }
    finally
    {
      StaticUtils.recursiveDelete(directory);
    }
  }

  /**
   * The schema files are read in the order of their names, as the server reads them, so the definition of an
   * attribute in a later file replaces the one in an earlier file.
   */
  @DataProvider
  public Object[][] schemaFilesInOrder()
  {
    return new Object[][] {
      { MY_MANAGER_AS_STRING, MY_MANAGER_AS_DN, true },
      { MY_MANAGER_AS_DN, MY_MANAGER_AS_STRING, false },
    };
  }

  @Test(dataProvider = "schemaFilesInOrder")
  public void aLaterSchemaFileDecidesTheMatchingRule(String first, String last, boolean comparesDNs) throws Exception
  {
    final File directory = Files.createTempDirectory("dn-equality-indexes").toFile();
    try
    {
      final File config = writeConfig(directory,
          backend("userRoot", "userRoot", "true"),
          index("userRoot", "myManager", "equality"));
      final File schemaDirectory = new File(directory, "schema");
      // The order of the directory listing depends on the file system, see schemaFilesAreReadInNameOrder
      writeSchema(schemaDirectory, "99-user.ldif", last);
      writeSchema(schemaDirectory, "10-first.ldif", first);

      final Map<String, Set<String>> attributes =
          UpgradeUtils.getDNEqualityIndexedAttributesPerBackend(config, schemaDirectory);

      if (comparesDNs)
      {
        assertThat(attributes).containsOnlyKeys("userRoot");
        assertThat(attributes.get("userRoot")).containsExactly("myManager");
      }
      else
      {
        assertThat(attributes).isEmpty();
      }
    }
    finally
    {
      StaticUtils.recursiveDelete(directory);
    }
  }

  /**
   * The schema files are read in the order of their names whatever order the file system lists them in: with 26
   * files, a directory listing that happens to be sorted is unlikely.
   */
  @Test
  public void schemaFilesAreReadInNameOrder() throws Exception
  {
    final File directory = Files.createTempDirectory("dn-equality-indexes").toFile();
    try
    {
      for (char c = 'z'; c >= 'a'; c--)
      {
        assertThat(new File(directory, "50-" + c + ".ldif").createNewFile()).isTrue();
      }
      assertThat(new File(directory, "50-not-a-schema-file.txt").createNewFile()).isTrue();

      final List<String> names = new ArrayList<>();
      for (final File file : UpgradeUtils.schemaFilesInReadOrder(directory))
      {
        names.add(file.getName());
      }

      assertThat(names).hasSize(26).isSorted();
    }
    finally
    {
      StaticUtils.recursiveDelete(directory);
    }
  }

  /** Each base DN gets the attributes of its backend; a backend without known base DNs is left out. */
  @Test
  public void eachBaseDNGetsTheIndexesOfItsBackend()
  {
    final Map<String, Set<String>> attributesPerBackend = new HashMap<>();
    attributesPerBackend.put("a,b", Collections.singleton("member"));
    attributesPerBackend.put("c", Collections.singleton("uniqueMember"));
    final Map<String, Set<String>> baseDNsPerBackend = new HashMap<>();
    baseDNsPerBackend.put("a,b", new HashSet<>(Arrays.asList("o=a", "o=b")));
    baseDNsPerBackend.put("d", Collections.singleton("o=d"));

    final Map<String, Set<String>> indexes =
        UpgradeTasks.getDNEqualityIndexesToVerify(attributesPerBackend, baseDNsPerBackend);

    assertThat(indexes).containsOnlyKeys("o=a", "o=b");
    assertThat(indexes.get("o=a")).containsExactly("member");
    assertThat(indexes.get("o=b")).containsExactly("member");
  }

  private static File writeConfig(File directory, String... entries) throws Exception
  {
    final File config = new File(directory, "config.ldif");
    Files.write(config.toPath(), String.join("\n",
        "dn: cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-root-config",
        "cn: config",
        "",
        "dn: cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Backends",
        "",
        String.join("\n", entries)).getBytes(UTF_8));
    return config;
  }

  private static void writeSchema(File schemaDirectory, String fileName, String attributeType) throws Exception
  {
    schemaDirectory.mkdir();
    Files.write(new File(schemaDirectory, fileName).toPath(), String.join("\n",
        "dn: cn=schema",
        "objectClass: top",
        "objectClass: ldapSubentry",
        "objectClass: subschema",
        "attributeTypes: " + attributeType,
        "").getBytes(UTF_8));
  }

  private static String backend(String rdnValue, String backendID, String enabled)
  {
    return String.join("\n",
        "dn: ds-cfg-backend-id=" + rdnValue + ",cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-pluggable-backend",
        "objectClass: ds-cfg-pdb-backend",
        "ds-cfg-backend-id: " + backendID,
        "ds-cfg-enabled: " + enabled,
        "ds-cfg-base-dn: o=" + backendID.replace(",", "\\,"),
        "",
        "dn: cn=Index,ds-cfg-backend-id=" + rdnValue + ",cn=Backends,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Index",
        "");
  }

  private static String index(String backendRDNValue, String attribute, String... indexTypes)
  {
    final StringBuilder entry = new StringBuilder()
        .append("dn: ds-cfg-attribute=").append(attribute)
        .append(",cn=Index,ds-cfg-backend-id=").append(backendRDNValue).append(",cn=Backends,cn=config\n")
        .append("objectClass: top\n")
        .append("objectClass: ds-cfg-backend-index\n")
        .append("ds-cfg-attribute: ").append(attribute).append('\n');
    for (String indexType : indexTypes)
    {
      entry.append("ds-cfg-index-type: ").append(indexType).append('\n');
    }
    return entry.append('\n').toString();
  }

  /**
   * A trusted equality index that misses the keys of its entries is rebuilt, and an index that matches its
   * entries is only verified.
   */
  @Test
  public void rebuildsAnIndexOnlyWhenItMissesKeys() throws Exception
  {
    withAGroupWhoseMemberIndexMissesItsKeys("db_dn_equality_indexes", (configFile, out, output) -> {
      final List<TextOutputCallback> rebuildNotifications = new ArrayList<>();
      UpgradeTasks.verifyAndRebuildOrWarn(newContext(rebuildNotifications), configFile, BASE_DN, MEMBER, false, out);
      assertThat(messagesOf(rebuildNotifications)).as("output: %s", output)
          .contains(INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_INCONSISTENT.get("member", BASE_DN).toString());
      // The rebuild closed `out`: the calls after it write to a stream of their own
      final PrintStream afterRebuild = new PrintStream(output, true, UTF_8);
      assertThat(UpgradeTasks.verifyAndRebuildIndexes(configFile, BASE_DN, MEMBER, false, afterRebuild))
          .as("verification of the rebuilt index; output: %s", output)
          .isEqualTo(UpgradeTasks.IndexVerification.CONSISTENT);

      final List<TextOutputCallback> notifications = new ArrayList<>();
      UpgradeTasks.verifyAndRebuildOrWarn(newContext(notifications), configFile, BASE_DN, MEMBER, false,
          afterRebuild);
      assertThat(messagesOf(notifications))
          .contains(INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_CONSISTENT.get("member", BASE_DN).toString())
          .doesNotContain(INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_INCONSISTENT.get("member", BASE_DN).toString())
          .doesNotContain(WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_FAILED.get("member", BASE_DN).toString());
    });
  }

  /**
   * A rebuild that fails, after the verification found missing keys, fails the upgrade: the rebuild may have left
   * the indexes untrusted, so that searches cannot use them, and a warning would let the upgrade succeed.
   */
  @Test
  public void aRebuildThatFailsFailsTheUpgrade() throws Exception
  {
    withAGroupWhoseMemberIndexMissesItsKeys("db_dn_equality_indexes_failed_rebuild", (configFile, out, output) ->
      withTheRebuildFailing(() -> {
        final List<TextOutputCallback> notifications = new ArrayList<>();
        final Throwable failure = catchThrowable(() -> UpgradeTasks.verifyAndRebuildOrWarn(
            newContext(notifications), configFile, BASE_DN, MEMBER, false, out));

        assertThat(failure).as("output: %s", output).isInstanceOf(ClientException.class);
        assertThat(output.toString("UTF-8")).as("the rebuild failed, not the verification")
            .contains("An error occurs during the rebuild index process in " + BASE_DN);
        assertThat(messagesOf(notifications))
            .doesNotContain(WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_FAILED.get("member", BASE_DN).toString());
      }));
  }

  /**
   * After a rebuild that failed, the indexes of the next base DNs are neither verified nor rebuilt, since the cause
   * of the failure, such as a full temporary directory, would most likely make their rebuild fail too, after it had
   * deleted them: the user is told which indexes were left, and the upgrade fails.
   */
  @Test
  public void aFailedRebuildLeavesTheIndexesOfTheNextBaseDNsUnverified() throws Exception
  {
    withAGroupWhoseMemberIndexMissesItsKeys("db_dn_equality_indexes_next_base_dns", (configFile, out, output) ->
      withTheRebuildFailing(() -> {
        final String nextBaseDN = "o=held by no backend";
        final Map<String, Set<String>> indexesPerBaseDN = new TreeMap<>();
        indexesPerBaseDN.put(nextBaseDN, MEMBER);
        indexesPerBaseDN.put(BASE_DN, MEMBER);
        assertThat(indexesPerBaseDN.keySet()).as("the rebuild fails first").containsExactly(BASE_DN, nextBaseDN);

        final List<TextOutputCallback> notifications = new ArrayList<>();
        final Throwable failure = catchThrowable(() -> UpgradeTasks.verifyAndRebuildOrWarn(newContext(notifications),
            configFile, indexesPerBaseDN, false, () -> new PrintStream(output, true, UTF_8)));

        assertThat(failure).as("output: %s", output).isInstanceOf(ClientException.class);
        assertThat(messagesOf(notifications))
            .contains(WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_SKIPPED.get("member", nextBaseDN).toString())
            .doesNotContain(INFO_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_STARTS.get("member", nextBaseDN).toString())
            .doesNotContain(WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_SKIPPED.get("member", BASE_DN).toString());
      }));
  }

  /**
   * Indexes under a base DN that no backend holds cannot be verified: they are not rebuilt, and do not fail the
   * upgrade, the user is warned instead.
   */
  @Test
  public void indexesThatCannotBeVerifiedOnlyWarn() throws Exception
  {
    final String baseDN = "o=held by no backend";
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final PrintStream out = new PrintStream(output, true, "UTF-8");
    final String configFile = DirectoryServer.getConfigFile();
    assertThat(UpgradeTasks.verifyAndRebuildIndexes(configFile, baseDN, MEMBER, false, out))
        .as("output: %s", output).isEqualTo(UpgradeTasks.IndexVerification.NOT_VERIFIED);

    final List<TextOutputCallback> notifications = new ArrayList<>();
    UpgradeTasks.verifyAndRebuildOrWarn(newContext(notifications), configFile, baseDN, MEMBER, false, out);

    final String warning = WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_FAILED.get("member", baseDN).toString();
    assertThat(messagesOf(notifications)).contains(warning);
    for (final TextOutputCallback notification : notifications)
    {
      if (notification.getMessage().equals(warning))
      {
        assertThat(((FormattedNotificationCallback) notification).getMessageSubType())
            .isEqualTo(TextOutputCallback.WARNING);
      }
    }
  }

  /**
   * Indexes that their backend fails to verify, as a JDBC or Cassandra backend whose database is down does, are
   * left as they were, and the user is warned. Here the backend fails on an index that it does not have.
   */
  @Test
  public void indexesThatTheBackendFailsToVerifyAreLeftAsTheyWere() throws Exception
  {
    withAGroupWhoseMemberIndexMissesItsKeys("db_dn_equality_indexes_failed_verify", (configFile, out, output) -> {
      final Set<String> attributes = new TreeSet<>(Arrays.asList("member", "uniqueMember"));
      final List<TextOutputCallback> notifications = new ArrayList<>();
      UpgradeTasks.verifyAndRebuildOrWarn(newContext(notifications), configFile, BASE_DN, attributes, false, out);

      assertThat(messagesOf(notifications)).as("output: %s", output)
          .contains(WARN_UPGRADE_VERIFY_DN_EQUALITY_INDEXES_FAILED.get("member, uniqueMember", BASE_DN).toString());
      final String[] verifyMember = { "--configFile", configFile, "--baseDN", BASE_DN, "--index", "member" };
      assertThat(VerifyIndex.countIndexErrors(verifyMember, false, out))
          .as("the member index still misses its keys; output: %s", output).isPositive();
    });
  }

  /** What a test runs against the backend of {@link #withAGroupWhoseMemberIndexMissesItsKeys}. */
  private interface IndexAction
  {
    void run(String configFile, PrintStream out, ByteArrayOutputStream output) throws Exception;
  }

  /** What a test runs while {@link #withTheRebuildFailing} makes the rebuilds fail. */
  private interface RebuildAction
  {
    void run() throws Exception;
  }

  /** Runs an action while a file where the rebuild creates its temporary directory makes every rebuild fail. */
  private static void withTheRebuildFailing(RebuildAction action) throws Exception
  {
    final File tmpDirectory = getFileForPath("import-tmp");
    final File setAside = new File(tmpDirectory.getPath() + ".set-aside");
    final boolean existed = tmpDirectory.exists();
    if (existed)
    {
      assertThat(tmpDirectory.renameTo(setAside)).isTrue();
    }
    try
    {
      assertThat(tmpDirectory.createNewFile()).isTrue();
      action.run();
    }
    finally
    {
      tmpDirectory.delete();
      if (existed)
      {
        assertThat(setAside.renameTo(tmpDirectory)).isTrue();
      }
    }
  }

  /**
   * Runs an action against a disabled PDB backend holding a group whose trusted member equality index misses the
   * keys of its entries, then removes the backend. Each test gives its own database directory, since removing the
   * backend leaves its database behind.
   */
  private static void withAGroupWhoseMemberIndexMissesItsKeys(String dbDirectory, IndexAction action)
      throws Exception
  {
    final Entry backend = TestCaseUtils.makeEntry(
        "dn: " + BACKEND_DN,
        "objectClass: top",
        "objectClass: ds-cfg-backend",
        "objectClass: ds-cfg-local-backend",
        "objectClass: ds-cfg-pluggable-backend",
        "objectClass: ds-cfg-pdb-backend",
        "ds-cfg-enabled: true",
        "ds-cfg-java-class: org.opends.server.backends.pdb.PDBBackend",
        "ds-cfg-backend-id: " + BACKEND_ID,
        "ds-cfg-writability-mode: enabled",
        "ds-cfg-base-dn: " + BASE_DN,
        "ds-cfg-db-directory: " + dbDirectory,
        "ds-cfg-db-cache-percent: 2");
    final Entry indexBranch = TestCaseUtils.makeEntry(
        "dn: cn=Index," + BACKEND_DN,
        "objectClass: top",
        "objectClass: ds-cfg-branch",
        "cn: Index");
    final Entry memberIndex = TestCaseUtils.makeEntry(
        "dn: ds-cfg-attribute=member,cn=Index," + BACKEND_DN,
        "objectClass: top",
        "objectClass: ds-cfg-backend-index",
        "ds-cfg-attribute: member",
        "ds-cfg-index-type: equality");
    TestCaseUtils.addEntry(backend);
    try
    {
      TestCaseUtils.addEntry(indexBranch);
      TestCaseUtils.addEntry(memberIndex);
      TestCaseUtils.addEntries(
          "dn: " + BASE_DN,
          "objectClass: top",
          "objectClass: organization",
          "o: dn equality indexes",
          "",
          "dn: cn=group," + BASE_DN,
          "objectClass: top",
          "objectClass: groupOfNames",
          "cn: group",
          "member: cn=a," + BASE_DN,
          "member: cn=b;" + BASE_DN);
      // A trusted index without the keys of these entries, as one whose keys a previous version computed
      // differently
      IndexKeyRemover.removeAllKeys(
          DirectoryServer.getInstance().getServerContext().getBackendConfigManager().getLocalBackendById(BACKEND_ID),
          DN.valueOf(BASE_DN), "member");
      setBackendEnabled(false);

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      action.run(DirectoryServer.getConfigFile(), new PrintStream(output, true, "UTF-8"), output);
    }
    finally
    {
      // A failed setup or test must not be hidden by the cleanup, nor leave the backend configured for the next
      // tests: the backend may not take the change, as when a tool run in this process left it half initialized
      getRootConnection().processModify(enableBackend(false));
      for (final DN dn : Arrays.asList(memberIndex.getName(), indexBranch.getName(), backend.getName()))
      {
        if (DirectoryServer.entryExists(dn))
        {
          TestCaseUtils.deleteEntry(dn);
        }
      }
    }
  }

  /** Returns an upgrade context that records what it notifies. */
  private static UpgradeContext newContext(final List<TextOutputCallback> notifications) throws Exception
  {
    return new UpgradeContext(callbacks -> {
      for (final Callback callback : callbacks)
      {
        if (callback instanceof TextOutputCallback)
        {
          notifications.add((TextOutputCallback) callback);
        }
      }
    });
  }

  private static List<String> messagesOf(final List<TextOutputCallback> notifications)
  {
    final List<String> messages = new ArrayList<>();
    for (final TextOutputCallback notification : notifications)
    {
      messages.add(notification.getMessage());
    }
    return messages;
  }

  private static void setBackendEnabled(boolean enabled)
  {
    final ModifyOperation modifyOperation = getRootConnection().processModify(enableBackend(enabled));
    assertThat(modifyOperation.getResultCode()).isEqualTo(ResultCode.SUCCESS);
  }

  private static ModifyRequest enableBackend(boolean enabled)
  {
    return Requests.newModifyRequest(BACKEND_DN)
        .addModification(ModificationType.REPLACE, "ds-cfg-enabled", Boolean.toString(enabled));
  }
}
