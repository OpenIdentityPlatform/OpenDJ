/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.messages;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.util.Set;

/**
 * Severity Tester.
 */
public class SeverityTest extends MessagesTestCase {

  @DataProvider(name = "messageDescriptors")
  public Object[][] getMessageDescriptors() {
    return new Object[][]{
            {CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION}
    };
  }

  @DataProvider(name = "severities")
  public Object[][] getSeverities() {
    return new Object[][]{
            {Severity.DEBUG},
            {Severity.FATAL_ERROR},
            {Severity.INFORMATION},
            {Severity.MILD_ERROR},
            {Severity.MILD_WARNING},
            {Severity.NOTICE},
            {Severity.SEVERE_ERROR},
            {Severity.SEVERE_WARNING}
    };
  }

  @DataProvider(name = "severities and pk names")
  public Object[][] getSeveritiesAndPropertyKeyNames() {
    return new Object[][]{
            {Severity.DEBUG, "DEBUG"},
            {Severity.FATAL_ERROR, "FATAL_ERR"},
            {Severity.INFORMATION, "INFO"},
            {Severity.MILD_ERROR, "MILD_ERR"},
            {Severity.MILD_WARNING, "MILD_WARN"},
            {Severity.NOTICE, "NOTICE"},
            {Severity.SEVERE_ERROR, "SEVERE_ERR"},
            {Severity.SEVERE_WARNING, "SEVERE_WARN"}
    };
  }

  @DataProvider(name = "severities and md names")
  public Object[][] getSeveritiesAndMessageDescriptorNames() {
    return new Object[][]{
            {Severity.DEBUG, "DEBUG"},
            {Severity.FATAL_ERROR, "ERR"},
            {Severity.INFORMATION, "INFO"},
            {Severity.MILD_ERROR, "ERR"},
            {Severity.MILD_WARNING, "WARN"},
            {Severity.NOTICE, "NOTE"},
            {Severity.SEVERE_ERROR, "ERR"},
            {Severity.SEVERE_WARNING, "WARN"}
    };
  }

  @DataProvider(name = "severities and masks")
  public Object[][] getSeveritiesAndMasks() {
    return new Object[][]{
            {Severity.DEBUG, 0x00060000},
            {Severity.FATAL_ERROR, 0x00050000},
            {Severity.INFORMATION, 0x00000000},
            {Severity.MILD_ERROR, 0x00030000},
            {Severity.MILD_WARNING, 0x00010000},
            {Severity.NOTICE, 0x00070000},
            {Severity.SEVERE_ERROR, 0x00040000},
            {Severity.SEVERE_WARNING, 0x00020000}
    };
  }

  @DataProvider(name = "severities and strings")
  public Object[][] getSeveritiesAndStrings() {
    return new Object[][]{
            {Severity.DEBUG, "DEBUG"},
            {Severity.FATAL_ERROR, "FATAL_ERR"},
            {Severity.FATAL_ERROR, "FATAL_ERROR"},
            {Severity.INFORMATION, "INFO"},
            {Severity.INFORMATION, "INFORMATION"},
            {Severity.MILD_ERROR, "MILD_ERR"},
            {Severity.MILD_ERROR, "MILD_ERROR"},
            {Severity.MILD_WARNING, "MILD_WARN"},
            {Severity.MILD_WARNING, "MILD_WARNING"},
            {Severity.NOTICE, "NOTICE"},
            {Severity.SEVERE_ERROR, "SEVERE_ERR"},
            {Severity.SEVERE_ERROR, "SEVERE_ERROR"},
            {Severity.SEVERE_WARNING, "SEVERE_WARN"},
            {Severity.SEVERE_WARNING, "SEVERE_WARNING"}
    };
  }

  @Test(dataProvider = "severities and pk names")
  public void testGetPropertyKeyFormSet(Severity severity, String name) {
    Set<String> s = Severity.getPropertyKeyFormSet();
    assertTrue(s.contains(name));
  }

  @Test(dataProvider = "severities and masks")
  public void testParseMask(Severity s, int mask) {
    assertEquals(Severity.parseMask(mask), s);
  }

  @Test(dataProvider = "severities and strings")
  public void testParseString(Severity sev, String s) {
    assertEquals(Severity.parseString(s), sev);
  }

  @Test(dataProvider = "messageDescriptors")
  public void testParseMessageId(MessageDescriptor md) {
    assertEquals(Severity.parseMessageId(md.getId()), md.getSeverity());
  }

  @Test(dataProvider = "severities and masks")
  public void testGetMask(Severity s, int mask) {
    assertEquals(s.getMask(), mask);
  }

  @Test(dataProvider = "severities and md names")
  public void testMessageDesciptorName(Severity s, String name) {
    assertEquals(s.messageDesciptorName(), name);
  }

  @Test(dataProvider = "severities and pk names")
  public void testPropertyKeyFormName(Severity sev, String s) {
    assertEquals(sev.propertyKeyFormName(), s);
  }

}
