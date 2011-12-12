/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldif;



import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Iterator;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy.Policy;
import org.testng.annotations.Test;



/**
 * This class tests the LDIFChangeRecordReader functionality.
 */
public final class LDIFChangeRecordReaderTestCase extends LDIFTestCase
{
  /**
   * Tests reading a valid add change record with a changetype.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadAddRecordWithChangeType() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(AddRequest.class);
    AddRequest addRequest = (AddRequest) record;
    assertThat((Object) addRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
    assertThat(
        addRequest.containsAttribute("objectClass", "top", "domainComponent"))
        .isTrue();
    assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
    assertThat(addRequest.getAttributeCount()).isEqualTo(2);
  }



  /**
   * Tests reading a valid add change record without a changetype.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadAddRecordWithoutChangeType() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(AddRequest.class);
    AddRequest addRequest = (AddRequest) record;
    assertThat((Object) addRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
    assertThat(
        addRequest.containsAttribute("objectClass", "top", "domainComponent"))
        .isTrue();
    assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
    assertThat(addRequest.getAttributeCount()).isEqualTo(2);
  }



  /**
   * Tests reading a valid modify change record.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadModifyRecord() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: modify",
        "add: description",
        "-",
        "add: description",
        "description: value1",
        "-",
        "add: description",
        "description: value1",
        "description: value2",
        "-",
        "delete: description",
        "-",
        "delete: description",
        "description: value1",
        "-",
        "delete: description",
        "description: value1",
        "description: value2",
        "-",
        "replace: description",
        "-",
        "replace: description",
        "description: value1",
        "-",
        "replace: description",
        "description: value1",
        "description: value2",
        "-",
        "increment: description",
        "description: 1"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(ModifyRequest.class);
    ModifyRequest modifyRequest = (ModifyRequest) record;
    assertThat((Object) modifyRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));

    Iterator<Modification> changes = modifyRequest.getModifications()
        .iterator();
    Modification modification;

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.ADD);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.ADD);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.ADD);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1", "value2"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.DELETE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.DELETE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.DELETE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1", "value2"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.REPLACE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.REPLACE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.REPLACE);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "value1", "value2"));

    modification = changes.next();
    assertThat(modification.getModificationType()).isEqualTo(
        ModificationType.INCREMENT);
    assertThat(modification.getAttribute()).isEqualTo(
        new LinkedAttribute("description", "1"));

    assertThat(changes.hasNext()).isFalse();
  }



  /**
   * Tests reading a valid delete change record.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadDeleteRecord() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: delete"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(DeleteRequest.class);
    DeleteRequest deleteRequest = (DeleteRequest) record;
    assertThat((Object) deleteRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
  }



  /**
   * Tests reading a valid moddn change record.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadModdnRecordWithoutNewSuperior() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: moddn",
        "newrdn: dc=eggsample",
        "deleteoldrdn: true"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(ModifyDNRequest.class);
    ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
    assertThat((Object) modifyDNRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
    assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(
        RDN.valueOf("dc=eggsample"));
    assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
    assertThat(modifyDNRequest.getNewSuperior()).isNull();
  }



  /**
   * Tests reading a valid moddn change record.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testReadModdnRecordWithNewSuperior() throws Exception
  {
    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: moddn",
        "newrdn: dc=eggsample",
        "deleteoldrdn: true",
        "newsuperior: dc=org"
        );
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();
    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(ModifyDNRequest.class);
    ModifyDNRequest modifyDNRequest = (ModifyDNRequest) record;
    assertThat((Object) modifyDNRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
    assertThat((Object) modifyDNRequest.getNewRDN()).isEqualTo(
        RDN.valueOf("dc=eggsample"));
    assertThat(modifyDNRequest.isDeleteOldRDN()).isTrue();
    assertThat((Object) modifyDNRequest.getNewSuperior()).isEqualTo(
        DN.valueOf("dc=org"));
  }



  /**
   * Tests reading a malformed record invokes the rejected record listener.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testRejectedRecordListenerMalformedFirstRecord() throws Exception
  {
    RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: baddn",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example"
        ).setRejectedLDIFListener(listener);
    // @formatter:on

    assertThat(reader.hasNext()).isFalse();

    verify(listener).handleMalformedRecord(
        eq(1L),
        eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
            "objectClass: domainComponent", "dc: example")),
        any(LocalizableMessage.class));
  }



  /**
   * Tests reading a malformed record invokes the rejected record listener.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testRejectedRecordListenerMalformedSecondRecord()
      throws Exception
  {
    RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example",
        "",
        "dn: baddn",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example"
        ).setRejectedLDIFListener(listener);
    // @formatter:on

    reader.readChangeRecord(); // Skip good record.
    assertThat(reader.hasNext()).isFalse();

    verify(listener).handleMalformedRecord(
        eq(7L),
        eq(Arrays.asList("dn: baddn", "changetype: add", "objectClass: top",
            "objectClass: domainComponent", "dc: example")),
        any(LocalizableMessage.class));
  }



  /**
   * Tests reading a skipped record invokes the rejected record listener.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testRejectedRecordListenerSkipsRecord() throws Exception
  {
    RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example"
        ).setRejectedLDIFListener(listener).setExcludeBranch(DN.valueOf("dc=com"));
    // @formatter:on

    assertThat(reader.hasNext()).isFalse();

    verify(listener)
        .handleSkippedRecord(
            eq(1L),
            eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add",
                "objectClass: top", "objectClass: domainComponent",
                "dc: example")), any(LocalizableMessage.class));
  }



  /**
   * Tests reading a record which does not conform to the schema invokes the
   * rejected record listener.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testRejectedRecordListenerRejectsBadSchemaRecord()
      throws Exception
  {
    RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example",
        "xxx: unknown attribute"
        ).setRejectedLDIFListener(listener)
         .setSchemaValidationPolicy(
             SchemaValidationPolicy.ignoreAll()
             .checkAttributesAndObjectClasses(Policy.REJECT));
    // @formatter:on

    assertThat(reader.hasNext()).isFalse();

    verify(listener).handleSchemaValidationFailure(
        eq(1L),
        eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add",
            "objectClass: top", "objectClass: domainComponent", "dc: example",
            "xxx: unknown attribute")), anyListOf(LocalizableMessage.class));
  }



  /**
   * Tests reading a record which does not conform to the schema invokes the
   * warning record listener.
   *
   * @throws Exception
   *           if an unexpected error occurred.
   */
  @Test
  public void testRejectedRecordListenerWarnsBadSchemaRecord()
      throws Exception
  {
    RejectedLDIFListener listener = mock(RejectedLDIFListener.class);

    // @formatter:off
    LDIFChangeRecordReader reader = new LDIFChangeRecordReader(
        "dn: dc=example,dc=com",
        "changetype: add",
        "objectClass: top",
        "objectClass: domainComponent",
        "dc: example",
        "xxx: unknown attribute"
        ).setRejectedLDIFListener(listener)
         .setSchemaValidationPolicy(
             SchemaValidationPolicy.ignoreAll()
             .checkAttributesAndObjectClasses(Policy.WARN));
    // @formatter:on

    assertThat(reader.hasNext()).isTrue();

    ChangeRecord record = reader.readChangeRecord();
    assertThat(record).isInstanceOf(AddRequest.class);
    AddRequest addRequest = (AddRequest) record;
    assertThat((Object) addRequest.getName()).isEqualTo(
        DN.valueOf("dc=example,dc=com"));
    assertThat(
        addRequest.containsAttribute("objectClass", "top", "domainComponent"))
        .isTrue();
    assertThat(addRequest.containsAttribute("dc", "example")).isTrue();
    assertThat(addRequest.getAttributeCount()).isEqualTo(2);

    verify(listener).handleSchemaValidationWarning(
        eq(1L),
        eq(Arrays.asList("dn: dc=example,dc=com", "changetype: add",
            "objectClass: top", "objectClass: domainComponent", "dc: example",
            "xxx: unknown attribute")), anyListOf(LocalizableMessage.class));
  }
}
