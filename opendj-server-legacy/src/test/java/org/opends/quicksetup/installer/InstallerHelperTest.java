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
package org.opends.quicksetup.installer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ReplicationMessages.ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/**
 * Tests the classification of the messages a task logs into its entry, as read by
 * {@code dsreplication} and by the quick setup while they wait for a task to complete.
 * <p>
 * The messages below are the ones a real initialization task logs, in the form rendered by
 * {@code org.opends.server.backends.task.Task#addLogMessage}: a start notice from the task
 * scheduler, then whatever the task logged, then the completion notice the scheduler appends
 * once the task is over - all of them before the terminal task state becomes visible.
 */
@SuppressWarnings("javadoc")
public class InstallerHelperTest extends DirectoryServerTestCase
{
  private static final String TASK_STARTED =
      "[09/Sep/2026:12:06:48 +0000] severity=\"NOTICE\" msgCount=0 msgID=org.opends.messages.backend-413"
      + " message=\"Initialize From Replica task quicksetup-initialize3 started execution\"";
  private static final String PEERS_NOT_FOUND =
      "[09/Sep/2026:12:08:48 +0000] severity=\"ERROR\" msgCount=1 msgID=org.opends.messages.replication-47"
      + " message=\"Domain dc=example,dc=com: the server with serverId=12345 is unreachable\"";
  private static final String IMPORT_NOT_SUPPORTED =
      "[09/Sep/2026:12:08:48 +0000] severity=\"ERROR\" msgCount=2 msgID=org.opends.messages.replication-82"
      + " message=\" Initialization cannot be done because import is not supported by the backend userRoot\"";
  private static final String TASK_FINISHED =
      "[09/Sep/2026:12:08:48 +0000] severity=\"NOTICE\" msgCount=3 msgID=org.opends.messages.backend-414"
      + " message=\"Initialize From Replica task quicksetup-initialize3 finished execution in the state"
      + " Stopped by error\"";

  /**
   * The message id of the peers not found error, as a task renders it. The predicate under test is
   * built from the message descriptor: this pins the descriptor to the form the other tests use.
   */
  @Test
  public void peersNotFoundErrorIsIdentifiedByResourceNameAndOrdinal()
  {
    assertThat(ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.resourceName() + "-"
        + ERR_NO_REACHABLE_PEER_IN_THE_DOMAIN.ordinal()).isEqualTo("org.opends.messages.replication-47");
  }

  @Test
  public void peersNotFoundErrorIsRecognized()
  {
    assertThat(new InstallerHelper().isPeersNotFoundError(PEERS_NOT_FOUND)).isTrue();
  }

  /**
   * The ordinal alone identifies no message: it is unique within a message file only, and the
   * count of the messages a task logged carries the same digits.
   */
  @Test
  public void anotherMessageCarryingTheOrdinalOfThePeersNotFoundErrorIsNotRecognized()
  {
    final String otherError =
        "[09/Sep/2026:12:08:48 +0000] severity=\"ERROR\" msgCount=47 msgID=org.opends.messages.replication-45"
        + " message=\"On domain dc=example,dc=com, initialization of server with serverId:12345 has been"
        + " requested from a server with an invalid serverId:0. \"";
    assertThat(new InstallerHelper().isPeersNotFoundError(otherError)).isFalse();
  }

  @Test
  public void aNoticeIsNotAPeersNotFoundError()
  {
    assertThat(new InstallerHelper().isPeersNotFoundError(TASK_STARTED)).isFalse();
    assertThat(new InstallerHelper().isPeersNotFoundError((String) null)).isFalse();
  }

  /**
   * The failure of a task is reported neither by the first message it logs nor by the last one, so
   * all of them have to be tested. This is the sequence of the task of issue #995.
   */
  @Test
  public void peersNotFoundErrorIsFoundAmongAllTheMessagesOfTheTask()
  {
    final InstallerHelper helper = new InstallerHelper();
    assertThat(helper.isPeersNotFoundError(Arrays.asList(TASK_STARTED, PEERS_NOT_FOUND, TASK_FINISHED))).isTrue();
    assertThat(helper.isPeersNotFoundError(Arrays.asList(TASK_STARTED, IMPORT_NOT_SUPPORTED, TASK_FINISHED)))
        .isFalse();
    assertThat(helper.isPeersNotFoundError(Collections.<String> emptyList())).isFalse();
  }

  /** The completion notice appended by the task scheduler must not hide the cause of the failure. */
  @Test
  public void relevantLogMessageIsTheLastErrorRatherThanTheLastMessage()
  {
    assertThat(InstallerHelper.getRelevantLogMessage(
        Arrays.asList(TASK_STARTED, PEERS_NOT_FOUND, TASK_FINISHED))).isEqualTo(PEERS_NOT_FOUND);
    assertThat(InstallerHelper.getRelevantLogMessage(
        Arrays.asList(TASK_STARTED, PEERS_NOT_FOUND, IMPORT_NOT_SUPPORTED, TASK_FINISHED)))
        .isEqualTo(IMPORT_NOT_SUPPORTED);
  }

  @Test
  public void relevantLogMessageIsTheLastMessageWhenTheTaskLoggedNoError()
  {
    assertThat(InstallerHelper.getRelevantLogMessage(Arrays.asList(TASK_STARTED, TASK_FINISHED)))
        .isEqualTo(TASK_FINISHED);
    assertThat(InstallerHelper.getRelevantLogMessage(Collections.<String> emptyList())).isNull();
  }

  @Test
  public void taskLogMessagesAreReadInTheOrderTheyWereLogged()
  {
    final Entry taskEntry = new LinkedHashMapEntry("ds-task-id=quicksetup-initialize3,cn=Scheduled Tasks,cn=Tasks");
    taskEntry.addAttribute(new LinkedAttribute("ds-task-log-message",
        TASK_STARTED, PEERS_NOT_FOUND, TASK_FINISHED));

    final List<String> logMsgs = InstallerHelper.getTaskLogMessages(taskEntry);

    assertThat(logMsgs).containsExactly(TASK_STARTED, PEERS_NOT_FOUND, TASK_FINISHED);
    assertThat(new InstallerHelper().isPeersNotFoundError(logMsgs)).isTrue();
    assertThat(InstallerHelper.getRelevantLogMessage(logMsgs)).isEqualTo(PEERS_NOT_FOUND);
  }

  @Test
  public void taskWithoutLogMessagesReadsAsAnEmptyLog()
  {
    final Entry taskEntry = new LinkedHashMapEntry("ds-task-id=quicksetup-initialize3,cn=Scheduled Tasks,cn=Tasks");

    assertThat(InstallerHelper.getTaskLogMessages(taskEntry)).isEmpty();
    assertThat(InstallerHelper.getRelevantLogMessage(InstallerHelper.getTaskLogMessages(taskEntry))).isNull();
  }
}
