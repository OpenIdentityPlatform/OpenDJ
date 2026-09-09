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
package org.opends.server.replication.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.replication.plugin.LDAPReplicationDomain.isServerFailure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests which result codes {@link LDAPReplicationDomain} counts as a failure of the server
 * rather than as a change only conflict resolution can apply.
 * <p>
 * {@code ds-cfg-server-error-result-code} is a plain integer which is not validated as a
 * result code, so an administrator can set it to a code {@code solveNamingConflict()} owns.
 * The replay must still leave such a change to conflict resolution: a replica which took
 * every one of them for a failure of the server would retry them in place, hold its
 * ServerState back over them and give up on them once the give-up delay is spent - for
 * conflicts conflict resolution would have solved on the attempt it never got.
 * <p>
 * Only {@code UNWILLING_TO_PERFORM} has an end-to-end test of that rule, because each of the
 * other codes would need a scenario which fails with exactly that code and which nothing but
 * conflict resolution can apply - and {@code OBJECTCLASS_VIOLATION} has none a replayed
 * operation reaches through the server's own paths, the schema checks which raise it being
 * skipped for synchronization operations. Forcing a code with {@code ShortCircuitPlugin} does
 * not stand in for one: the operation applies for real once that budget is spent, so such a
 * test passes with the code removed from the set (#910, #938). So the rule is pinned here on
 * the predicate itself, with no server in it.
 * <p>
 * The codes below are written out rather than read from {@code CONFLICT_RESULT_CODES}: a data
 * provider fed by the set under test agrees with it whatever it holds, which is exactly what
 * leaves the set unguarded today. Every result code which can be configured is then swept
 * through the predicate against that list, so a code put into the set is as visible as one
 * taken out of it, and the set itself stays private - nothing here reads it.
 * <p>
 * What this can not see is the set drifting away from the branches of
 * {@code solveNamingConflict()} it is the union of: a code dropped from both at once leaves
 * the table below still describing the predicate correctly.
 * <p>
 * Nor is the predicate the whole of the rule at the call site: {@code replay()} answers
 * {@code SUCCESS} and {@code NO_OPERATION} before it consults this at all, so a change which
 * came back with one of them never reaches the table below whatever is configured - that is
 * issue #953. {@code BUSY} is answered ahead of it too, but only inside the retry loop: once
 * the in-place attempts are spent, the predicate is asked about it after all.
 */
@SuppressWarnings("javadoc")
public class IsServerFailureTest extends DirectoryServerTestCase
{
  /**
   * The result codes the four {@code solveNamingConflict()} overloads dispatch on. Not
   * everything they resolve: the ModifyDN overload answers {@code NOTHING_TO_DO} from three
   * early returns before it ever looks at the result code.
   */
  private static final List<ResultCode> CONFLICT_CODES = Arrays.asList(
      ResultCode.NO_SUCH_OBJECT,          // 32, all four overloads
      ResultCode.ENTRY_ALREADY_EXISTS,    // 68, the Add and ModifyDN overloads
      ResultCode.NOT_ALLOWED_ON_RDN,      // 67, the Modify overload
      ResultCode.NOT_ALLOWED_ON_NONLEAF,  // 66, the Delete overload
      ResultCode.UNWILLING_TO_PERFORM,    // 53, the ModifyDN overload
      ResultCode.OBJECTCLASS_VIOLATION);  // 65, the ModifyDN overload

  /**
   * Codes conflict resolution does not solve. {@code OTHER} is the default of
   * {@code ds-cfg-server-error-result-code}, and the property is an integer with no upper
   * limit, so a value no result code is named after is a setting like any other.
   * {@code UNAVAILABLE} is not here: it is a failure of the server whatever is configured,
   * which {@link #unavailableIsAServerFailureWhateverIsConfigured()} covers.
   */
  private static final List<ResultCode> CODES_OUTSIDE_THE_CONFLICT_SET = Arrays.asList(
      ResultCode.OTHER,                       // 80, the default
      ResultCode.CONSTRAINT_VIOLATION,        // 19
      ResultCode.INSUFFICIENT_ACCESS_RIGHTS,  // 50
      ResultCode.valueOf(9999));              // a value no result code is named after

  @DataProvider(name = "conflictResultCodes")
  public Object[][] conflictResultCodes()
  {
    return rowsOf(CONFLICT_CODES);
  }

  @DataProvider(name = "codesOutsideTheConflictSet")
  public Object[][] codesOutsideTheConflictSet()
  {
    return rowsOf(CODES_OUTSIDE_THE_CONFLICT_SET);
  }

  /**
   * Every result code an administrator could put in {@code ds-cfg-server-error-result-code}.
   * The property is an integer with {@code lower-limit="0"}, so {@code UNDEFINED} (-1) is not
   * one of them - it is the null object of {@link ResultCode} rather than a setting - and a
   * value no result code is named after is.
   */
  private static List<ResultCode> configurableCodes()
  {
    final List<ResultCode> codes = new ArrayList<>();
    for (ResultCode code : ResultCode.values())
    {
      if (code.intValue() >= 0)
      {
        codes.add(code);
      }
    }
    codes.add(ResultCode.valueOf(9999));
    return codes;
  }

  private static Object[][] rowsOf(List<ResultCode> codes)
  {
    final Object[][] rows = new Object[codes.size()][];
    for (int i = 0; i < codes.size(); i++)
    {
      rows[i] = new Object[] { codes.get(i) };
    }
    return rows;
  }

  /** A result which is neither {@code UNAVAILABLE} nor the configured code. */
  @DataProvider(name = "resultAndAnotherConfiguredCode")
  public Object[][] resultAndAnotherConfiguredCode()
  {
    return new Object[][] {
      // a conflict under the default setting: the carve-out for CONFLICT_RESULT_CODES does
      // not come into it, the result simply is not what this server puts on an internal error
      { ResultCode.NO_SUCH_OBJECT, ResultCode.OTHER },
      { ResultCode.ENTRY_ALREADY_EXISTS, ResultCode.OTHER },
      { ResultCode.NOT_ALLOWED_ON_RDN, ResultCode.OTHER },
      { ResultCode.NOT_ALLOWED_ON_NONLEAF, ResultCode.OTHER },
      { ResultCode.UNWILLING_TO_PERFORM, ResultCode.OTHER },
      { ResultCode.OBJECTCLASS_VIOLATION, ResultCode.OTHER },
      // a conflict code while a different conflict code is configured - the deployment
      // this whole class is written about, meeting a conflict it did not configure
      { ResultCode.NO_SUCH_OBJECT, ResultCode.UNWILLING_TO_PERFORM },
      // and a failure which is not the configured code is not this server's either
      { ResultCode.OTHER, ResultCode.NO_SUCH_OBJECT },
      { ResultCode.CONSTRAINT_VIOLATION, ResultCode.UNWILLING_TO_PERFORM },
      { ResultCode.INSUFFICIENT_ACCESS_RIGHTS, ResultCode.OTHER },
      { ResultCode.valueOf(9999), ResultCode.OTHER },
    };
  }

  /**
   * Sweeps every registered result code through the predicate against the list above. The
   * named tables below say what the rule is on the codes which matter; this says the set
   * behind it holds those and nothing else, so that a code put into
   * {@code CONFLICT_RESULT_CODES} - which stops a storage failure carrying it from being
   * retried - fails here as loudly as a code taken out of it.
   * <p>
   * It speaks for the predicate, not for what the replay hands it: {@code SUCCESS} and
   * {@code NO_OPERATION} never reach the method, and what it answers for them is what the
   * rest of this sweep is written from rather than a rule of its own.
   * <p>
   * Soft assertions so that a drift in both directions at once - a code added and another
   * dropped - is reported in one run rather than one code per run.
   */
  @Test
  public void everyRegisteredCodeIsAServerFailureExactlyWhenConflictResolutionDoesNotOwnIt()
  {
    final SoftAssertions softly = new SoftAssertions();
    for (ResultCode code : configurableCodes())
    {
      softly.assertThat(isServerFailure(code, code))
          .as("%s (%d) set as server-error-result-code is a failure of this server unless "
                  + "conflict resolution is the only thing which can solve it",
              code, code.intValue())
          .isEqualTo(!CONFLICT_CODES.contains(code));
    }
    softly.assertAll();
  }

  @Test(dataProvider = "conflictResultCodes")
  public void conflictCodeConfiguredAsTheServerErrorCodeIsLeftToConflictResolution(ResultCode code)
  {
    assertThat(isServerFailure(code, code))
        .as("%s (%d) set as server-error-result-code must not take a change away from "
                + "solveNamingConflict(), which is the only thing which can solve it",
            code, code.intValue())
        .isFalse();
  }

  @Test(dataProvider = "codesOutsideTheConflictSet")
  public void codeOutsideTheConflictSetConfiguredAsTheServerErrorCodeIsAServerFailure(ResultCode code)
  {
    assertThat(isServerFailure(code, code))
        .as("%s (%d) set as server-error-result-code is this server reporting an internal "
                + "error, and conflict resolution can not solve it",
            code, code.intValue())
        .isTrue();
  }

  /**
   * "Whatever is configured" is every code which can be configured, rather than a handful of
   * them: the predicate reads the configured code only after {@code UNAVAILABLE} has not
   * matched, so a change which stops that short circuit for some code has to be looked for
   * across all of them.
   */
  @Test
  public void unavailableIsAServerFailureWhateverIsConfigured()
  {
    final SoftAssertions softly = new SoftAssertions();
    for (ResultCode serverErrorResultCode : configurableCodes())
    {
      softly.assertThat(isServerFailure(ResultCode.UNAVAILABLE, serverErrorResultCode))
          .as("the backend being offline or rebuilt is a failure of the server while "
                  + "server-error-result-code is %s (%d) just as much as it is by default",
              serverErrorResultCode, serverErrorResultCode.intValue())
          .isTrue();
    }
    softly.assertAll();
  }

  @Test(dataProvider = "resultAndAnotherConfiguredCode")
  public void codeWhichIsNotTheConfiguredOneIsNotAServerFailure(
      ResultCode result, ResultCode serverErrorResultCode)
  {
    assertThat(isServerFailure(result, serverErrorResultCode))
        .as("%s (%d) is not what this server puts on an internal error - it puts %s (%d) - "
                + "so it is the operation which failed rather than the server",
            result, result.intValue(), serverErrorResultCode, serverErrorResultCode.intValue())
        .isFalse();
  }
}
