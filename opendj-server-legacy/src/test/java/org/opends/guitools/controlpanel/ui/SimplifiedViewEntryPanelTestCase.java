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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the object class counting done by {@link SimplifiedViewEntryPanel}. */
@SuppressWarnings("javadoc")
public class SimplifiedViewEntryPanelTestCase extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] objectClassValues()
  {
    return new Object[][] {
      { Collections.<String> emptyList(), 0 },
      { list("top"), 0 },
      // Object class values are case insensitive: "top" must be filtered out whatever its case is.
      { list("TOP"), 0 },
      { list("Top"), 0 },
      { list("top", "person"), 1 },
      { list("TOP", "person"), 1 },
      { list("top", "person", "organizationalPerson"), 2 },
      { list("person", "organizationalPerson"), 2 },
    };
  }

  @Test(dataProvider = "objectClassValues")
  public void testCountObjectClassesBesidesTop(List<String> objectClasses, int expectedCount)
  {
    assertThat(SimplifiedViewEntryPanel.countObjectClassesBesidesTop(byteStrings(objectClasses)))
        .isEqualTo(expectedCount);
  }

  private static List<String> list(String... values)
  {
    List<String> result = new ArrayList<>();
    Collections.addAll(result, values);
    return result;
  }

  private static List<ByteString> byteStrings(List<String> values)
  {
    List<ByteString> result = new ArrayList<>();
    for (String value : values)
    {
      result.add(ByteString.valueOfUtf8(value));
    }
    return result;
  }
}
