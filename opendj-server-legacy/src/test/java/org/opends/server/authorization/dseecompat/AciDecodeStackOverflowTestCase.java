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
package org.opends.server.authorization.dseecompat;

import java.util.regex.Pattern;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression tests for issue #665: an ACI with thousands of repeated target
 * rules used to overflow the regex engine's recursion in
 * {@code Pattern.matches(aciRegex, ...)}, throwing StackOverflowError instead
 * of the expected AciException.
 */
@SuppressWarnings("javadoc")
public class AciDecodeStackOverflowTestCase extends DirectoryServerTestCase
{
  /** Enough repeated target rules to overflow an unbounded regex recursion. */
  private static final int REPEATED_TARGETS = 10000;
  /**
   * A small bounded stack makes any per-repetition regex recursion overflow
   * deterministically, while the iterative (possessive) match needs far less.
   */
  private static final long DECODE_STACK_SIZE_BYTES = 256 * 1024;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws DirectoryException
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @Test
  public void repeatedTargetsAciIsRejectedWithoutStackOverflow() throws Exception
  {
    StringBuilder aci = new StringBuilder();
    for (int i = 0; i < REPEATED_TARGETS; i++)
    {
      aci.append("(targetscope=\"&O\")");
    }
    aci.append("(version 3.0; acl \"\"; allow (read) userdn=\"ldap:///anyone\";)");
    final String input = aci.toString();

    Throwable thrown = runOnBoundedStack(new Task()
    {
      @Override
      public void run() throws Exception
      {
        Aci.decode(ByteString.valueOfUtf8(input), DN.rootDN());
      }
    });
    assertThat(thrown)
        .as("pathological ACI must be rejected with AciException")
        .isInstanceOf(AciException.class);
  }

  /**
   * Checks the regex itself matches iteratively, independently of the
   * defense-in-depth catch in Aci.decode.
   */
  @Test
  public void targetsRegexMatchesIterativelyOnBoundedStack() throws Exception
  {
    StringBuilder targets = new StringBuilder();
    for (int i = 0; i < REPEATED_TARGETS; i++)
    {
      targets.append("(targetscope=\"&O\")");
    }
    final String input = targets.toString();

    final boolean[] matches = new boolean[1];
    Throwable thrown = runOnBoundedStack(new Task()
    {
      @Override
      public void run()
      {
        matches[0] = Pattern.matches(AciTargets.targetsRegex, input);
      }
    });
    assertThat(thrown).as("targets regex must not recurse per repetition").isNull();
    assertThat(matches[0]).isTrue();
  }

  @Test
  public void validAciWithSeveralTargetsStillDecodes() throws Exception
  {
    String aci = "(targetattr=\"cn\")(targetscope=\"onelevel\")"
        + "(version 3.0; acl \"test\"; allow (read,search) userdn=\"ldap:///anyone\";)";
    assertThat(Aci.decode(ByteString.valueOfUtf8(aci), DN.rootDN()).toString()).isEqualTo(aci);
  }

  /** The possessive target regex must still reject malformed target syntax. */
  @Test
  public void malformedTargetStillRejected() throws Exception
  {
    final String aci = "(targetscope \"onelevel\")"
        + "(version 3.0; acl \"test\"; allow (read) userdn=\"ldap:///anyone\";)";
    assertThatThrownBy(() -> Aci.decode(ByteString.valueOfUtf8(aci), DN.rootDN()))
        .isInstanceOf(AciException.class);
  }

  /** A runnable that may throw a checked exception (e.g. AciException). */
  private interface Task
  {
    void run() throws Exception;
  }

  /**
   * Runs the task on a thread with a small fixed stack and returns whatever
   * it threw, including Errors, or null if it completed.
   */
  private Throwable runOnBoundedStack(final Task task) throws InterruptedException
  {
    final Throwable[] thrown = new Throwable[1];
    Runnable guarded = new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          task.run();
        }
        catch (Throwable t)
        {
          thrown[0] = t;
        }
      }
    };
    Thread worker = new Thread(null, guarded, "issue-665-bounded-stack", DECODE_STACK_SIZE_BYTES);
    worker.start();
    worker.join();
    return thrown[0];
  }
}
