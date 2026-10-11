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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.tools.tasks;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.config.ConfigConstants.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.types.RawAttribute;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the task entry DN that {@link TaskClient#getTaskDN(List)} builds from the task ID (issue #1153). */
@SuppressWarnings("javadoc")
public class TaskClientTaskDNTest extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] taskIDs()
  {
    return new Object[][] { { "daily" }, { "daily,full" }, { "a+b" }, { "#1" }, { "a\\b" }, { " lead" } };
  }

  @Test(dataProvider = "taskIDs")
  public void recurringTaskDNKeepsTheWholeID(String taskID)
  {
    final List<RawAttribute> attributes = Arrays.asList(
        RawAttribute.create(ATTR_TASK_ID, taskID), RawAttribute.create(ATTR_RECURRING_TASK_ID, taskID));

    final DN taskDN = DN.valueOf(TaskClient.getTaskDN(attributes));

    assertThat((Object) taskDN.parent()).isEqualTo(DN.valueOf(RECURRING_TASK_BASE_RDN + "," + DN_TASK_ROOT));
    assertSingleValuedRDN(taskDN.rdn(), ATTR_RECURRING_TASK_ID, taskID);
  }

  @Test(dataProvider = "taskIDs")
  public void scheduledTaskDNKeepsTheWholeID(String taskID)
  {
    final List<RawAttribute> attributes = Collections.singletonList(RawAttribute.create(ATTR_TASK_ID, taskID));

    final DN taskDN = DN.valueOf(TaskClient.getTaskDN(attributes));

    assertThat((Object) taskDN.parent()).isEqualTo(DN.valueOf(SCHEDULED_TASK_BASE_RDN + "," + DN_TASK_ROOT));
    assertSingleValuedRDN(taskDN.rdn(), ATTR_TASK_ID, taskID);
  }

  private static void assertSingleValuedRDN(RDN rdn, String attributeName, String value)
  {
    assertThat(rdn.size()).isEqualTo(1);
    assertThat(rdn.getFirstAVA().getAttributeType().hasName(attributeName)).isTrue();
    assertThat((Object) rdn.getFirstAVA().getAttributeValue()).isEqualTo(ByteString.valueOfUtf8(value));
  }
}
