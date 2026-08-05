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
package org.opends.guitools.controlpanel.datamodel;

import static org.assertj.core.api.Assertions.*;

import java.util.Date;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests the {@code equals()} contract of {@link ScheduleType}. */
@Test(groups = { "precommit", "controlpanel" }, sequential = true)
@SuppressWarnings("javadoc")
public class ScheduleTypeTest extends DirectoryServerTestCase
{
  @Test
  public void schedulesOfTheSameTypeAreEqual()
  {
    ScheduleType schedule1 = ScheduleType.createCron("0 0 * * *");
    ScheduleType schedule2 = ScheduleType.createCron("0 0 * * *");

    assertThat(schedule1).isEqualTo(schedule2);
    assertThat(schedule1.hashCode()).isEqualTo(schedule2.hashCode());
  }

  @Test
  public void schedulesOfDifferentTypesAreNotEqual()
  {
    ScheduleType launchNow = ScheduleType.createLaunchNow();
    ScheduleType launchLater = ScheduleType.createLaunchLater(new Date(0));

    assertThat(launchNow).isNotEqualTo(launchLater);
    assertThat(launchNow).isNotEqualTo(ScheduleType.createCron("0 0 * * *"));
  }

  /**
   * A schedule must not compare equal to a plain object sharing its textual form: such a
   * comparison could never be symmetric.
   */
  @Test
  public void scheduleIsNotEqualToAStringWithTheSameTextualForm()
  {
    ScheduleType schedule = ScheduleType.createLaunchNow();
    String sameText = schedule.toString();

    assertThat(schedule.equals(sameText)).isFalse();
    assertThat(sameText.equals(schedule)).isFalse();
  }

  @Test
  public void scheduleIsNotEqualToNull()
  {
    assertThat(ScheduleType.createLaunchNow().equals(null)).isFalse();
  }

  @Test
  public void scheduleIsEqualToItself()
  {
    ScheduleType schedule = ScheduleType.createLaunchNow();

    assertThat(schedule).isEqualTo(schedule);
  }
}
