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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
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
          index("otherRoot", "cn", "equality")).getBytes(UTF_8));

      final File schemaDirectory = new File(directory, "schema");
      schemaDirectory.mkdir();
      Files.write(new File(schemaDirectory, "99-user.ldif").toPath(), String.join("\n",
          "dn: cn=schema",
          "objectClass: top",
          "objectClass: ldapSubentry",
          "objectClass: subschema",
          "attributeTypes: ( 1.3.6.1.4.1.26027.1.999.1153 NAME 'myManager' SUP distinguishedName )",
          "").getBytes(UTF_8));

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
        "ds-cfg-db-directory: db_dn_equality_indexes",
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
      final PrintStream out = new PrintStream(output, true, "UTF-8");
      final Set<String> attributes = Collections.singleton("member");
      final String configFile = DirectoryServer.getConfigFile();

      assertThat(UpgradeTasks.verifyAndRebuildIndexes(configFile, BASE_DN, attributes, false, out))
          .as("rebuilt an index missing keys; output: %s", output).isTrue();
      assertThat(UpgradeTasks.verifyAndRebuildIndexes(configFile, BASE_DN, attributes, false, out))
          .as("rebuilt an index matching its entries; output: %s", output).isFalse();
    }
    finally
    {
      setBackendEnabled(false);
      TestCaseUtils.deleteEntry(memberIndex.getName());
      TestCaseUtils.deleteEntry(indexBranch.getName());
      TestCaseUtils.deleteEntry(backend.getName());
    }
  }

  private static void setBackendEnabled(boolean enabled)
  {
    final ModifyRequest modifyRequest = Requests.newModifyRequest(BACKEND_DN)
        .addModification(ModificationType.REPLACE, "ds-cfg-enabled", Boolean.toString(enabled));
    final ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertThat(modifyOperation.getResultCode()).isEqualTo(ResultCode.SUCCESS);
  }
}
