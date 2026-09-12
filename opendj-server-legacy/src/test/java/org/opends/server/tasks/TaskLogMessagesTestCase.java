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
package org.opends.server.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.BackendMessages.NOTE_TASK_FINISHED;
import static org.opends.messages.BackendMessages.NOTE_TASK_STARTED;
import static org.opends.messages.ToolMessages.ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID;
import static org.opends.server.config.ConfigConstants.ATTR_TASK_LOG_MESSAGES;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.quicksetup.installer.InstallerHelper;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.TaskState;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests what the messages a task logs into its entry look like once the task is over, since the
 * quick setup and {@code dsreplication} read them to report and to classify the failure of the
 * tasks they run - see {@code InstallerHelper} and issue #995.
 */
@SuppressWarnings("javadoc")
public class TaskLogMessagesTestCase extends TasksTestCase
{
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * The failure of a task is reported by neither the first nor the last message it logged: the
   * task scheduler frames the execution with a start and a completion notice, and both of them are
   * in the entry before the terminal task state is, so a reader that waits for that state always
   * sees them. The cause has to be looked up among all the messages.
   */
  @Test
  public void failureOfATaskIsReportedBetweenTheNoticesOfTheScheduler() throws Exception
  {
    final String taskDN = "ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks";
    // Exporting an unknown backend fails the task without touching any data.
    final Entry taskEntry = TestCaseUtils.makeEntry(
        "dn: " + taskDN,
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-export",
        "ds-task-class-name: org.opends.server.tasks.ExportTask",
        "ds-task-export-backend-id: no-such-backend",
        "ds-task-export-ldif-file: " + TestCaseUtils.createTempFile());
    testTask(taskEntry, TaskState.STOPPED_BY_ERROR, 60);

    final List<String> logMsgs = getLogMessages(DN.valueOf(taskDN));
    assertThat(logMsgs.size()).isGreaterThanOrEqualTo(3);
    assertThat(logMsgs.get(0)).contains(msgIdField(NOTE_TASK_STARTED.resourceName(), NOTE_TASK_STARTED.ordinal()));
    assertThat(logMsgs.get(logMsgs.size() - 1))
        .contains(msgIdField(NOTE_TASK_FINISHED.resourceName(), NOTE_TASK_FINISHED.ordinal()));

    final String failure = InstallerHelper.getRelevantLogMessage(logMsgs);
    assertThat(failure).contains("severity=\"ERROR\"")
        .contains(msgIdField(ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID.resourceName(),
            ERR_LDIFEXPORT_NO_BACKENDS_FOR_ID.ordinal()));
    assertThat(logMsgs.indexOf(failure)).isGreaterThan(0).isLessThan(logMsgs.size() - 1);
    assertThat(new InstallerHelper().isPeersNotFoundError(logMsgs)).isFalse();
  }

  /**
   * The id a message is logged with, in the form rendered by
   * {@code org.opends.server.backends.task.Task#addLogMessage}. This is what the classification of
   * a task failure matches on, so the two must agree.
   */
  private String msgIdField(String resourceName, int ordinal)
  {
    return "msgID=" + resourceName + "-" + ordinal;
  }

  private List<String> getLogMessages(DN taskDN)
  {
    final InternalSearchOperation searchOperation =
        getRootConnection().processSearch(newSearchRequest(taskDN, SearchScope.BASE_OBJECT));
    final Entry taskEntry = searchOperation.getSearchEntries().getFirst();

    final List<String> logMsgs = new ArrayList<>();
    for (Attribute attribute : taskEntry.getAllAttributes(ATTR_TASK_LOG_MESSAGES))
    {
      for (ByteString value : attribute)
      {
        logMsgs.add(value.toString());
      }
    }
    return logMsgs;
  }
}
