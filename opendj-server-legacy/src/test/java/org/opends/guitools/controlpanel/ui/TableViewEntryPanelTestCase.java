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
package org.opends.guitools.controlpanel.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.util.CollectionUtils.newHashSet;

import java.util.Set;

import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests how {@link TableViewEntryPanel} decides what to display for a given attribute. */
@SuppressWarnings("javadoc")
public class TableViewEntryPanelTestCase extends DirectoryServerTestCase
{
  private static final Schema SCHEMA = Schema.getCoreSchema();
  private static final AttributeDescription OBJECT_CLASS = AttributeDescription.valueOf("objectClass", SCHEMA);

  @Test
  public void testObjectClassIsDisplayedAsADescriptorWhenTheSchemaIsAvailable()
  {
    assertThat(TableViewEntryPanel.isObjectClassWithSchema(OBJECT_CLASS, SCHEMA)).isTrue();
  }

  /**
   * The object class descriptor cannot be built without the schema: the raw values must then be
   * displayed instead, otherwise the object class attribute disappears from the table.
   */
  @Test
  public void testObjectClassFallsBackToItsRawValuesWithoutSchema()
  {
    assertThat(TableViewEntryPanel.isObjectClassWithSchema(OBJECT_CLASS, null)).isFalse();
  }

  @Test
  public void testOtherAttributesAreNeverDisplayedAsADescriptor()
  {
    assertThat(TableViewEntryPanel.isObjectClassWithSchema(AttributeDescription.valueOf("cn", SCHEMA), SCHEMA))
        .isFalse();
  }

  /** Attribute names are case insensitive, and the required attributes are stored in lower case. */
  @Test
  public void testRequiredAttributesAreMatchedCaseInsensitively()
  {
    Set<String> requiredAttrs = newHashSet("objectclass", "sn", "cn");

    assertThat(TableViewEntryPanel.isRequired(requiredAttrs, OBJECT_CLASS)).isTrue();
    assertThat(TableViewEntryPanel.isRequired(requiredAttrs, AttributeDescription.valueOf("sn", SCHEMA))).isTrue();
    assertThat(TableViewEntryPanel.isRequired(requiredAttrs, AttributeDescription.valueOf("description", SCHEMA)))
        .isFalse();
  }
}
