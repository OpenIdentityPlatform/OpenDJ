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
package org.opends.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.ModificationType.REPLACE;
import static org.forgerock.opendj.ldap.requests.Requests.newModifyRequest;
import static org.opends.messages.ConfigMessages.ERR_CONFIG_CORE_SERVER_ERROR_RESULT_CODE_NOT_A_FAILURE;
import static org.opends.messages.ConfigMessages.WARN_CONFIG_CORE_SERVER_ERROR_RESULT_CODE_NOT_A_FAILURE;
import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the validation of {@code ds-cfg-server-error-result-code}, the result code this
 * server puts on an internal error.
 * <p>
 * The setting says "this server failed". A code which does not mean a failure says
 * something else to every reader of a result code, and the readers which act on what it
 * usually means then act on an operation which failed: the replay of a replication domain
 * reads {@code NO_OPERATION} as "conflict resolution found the change already applied"
 * and records a change which never reached the backend as replayed (issue #953), and
 * {@code SUCCESS} has {@code LDAPReplicationDomain.synchronize()} publish an operation
 * which failed to the whole topology. Neither reader can tell the two apart once they are
 * the same integer, so the value is kept out of the configuration instead.
 */
@SuppressWarnings("javadoc")
public class ServerErrorResultCodeTestCase extends CoreTestCase
{
  /** The code to put back, or {@code null} when this test did not change it. */
  private Integer resultCodeToRestore;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @AfterMethod
  public void tearDown()
  {
    if (resultCodeToRestore != null)
    {
      final int resultCode = resultCodeToRestore;
      resultCodeToRestore = null;
      assertEquals(setServerErrorResultCode(resultCode).getResultCode(), ResultCode.SUCCESS,
          "the server error result code could not be put back");
    }
  }

  /**
   * The result codes which do not report a failure: {@code ResultCode} registers exactly
   * these five as success codes, and every other value - including one it does not know,
   * which it answers with an unknown code of its own - reports a failure.
   */
  @DataProvider
  public Object[][] resultCodesWhichAreNotAFailure()
  {
    return new Object[][] {
      { ResultCode.SUCCESS },
      { ResultCode.COMPARE_FALSE },
      { ResultCode.COMPARE_TRUE },
      { ResultCode.SASL_BIND_IN_PROGRESS },
      { ResultCode.NO_OPERATION },
    };
  }

  @Test(dataProvider = "resultCodesWhichAreNotAFailure")
  public void serverErrorResultCodeCanNotBeSetToACodeWhichIsNotAFailure(ResultCode resultCode)
  {
    final ResultCode inForce = getServerErrorResultCode();
    // Remembered although the change is expected to be refused: the day it is not, the
    // failure must show here and not as a success code left in force for every test class
    // which runs after this one.
    resultCodeToRestore = inForce.intValue();

    final ModifyOperation refusal = setServerErrorResultCode(resultCode.intValue());
    assertEquals(refusal.getResultCode(), ResultCode.UNWILLING_TO_PERFORM,
        "the server accepted " + resultCode + " as the code it puts on an internal error");
    assertThat(refusal.getErrorMessage().toString())
        .as("the refusal does not name the attribute and the code it turned down")
        .contains(ERR_CONFIG_CORE_SERVER_ERROR_RESULT_CODE_NOT_A_FAILURE.get(resultCode.intValue(), resultCode)
            .toString());
    assertEquals(getServerErrorResultCode(), inForce,
        "a refused change to the server error result code was applied all the same");
  }

  /**
   * The start-up path does not go through the acceptability check, so a configuration
   * written before the check existed - or edited outside the server - can still hold a
   * code which does not report a failure: the server starts on the default of the setting
   * rather than on that code, and rather than not at all. A code which reports a failure,
   * registered or not, is taken as it is.
   */
  @Test(dataProvider = "resultCodesWhichAreNotAFailure")
  public void aCodeWhichIsNotAFailureFallsBackOnTheDefaultAtStartUp(ResultCode resultCode)
  {
    assertEquals(CoreConfigManager.serverErrorResultCode(resultCode.intValue()), ResultCode.OTHER,
        "the server started on " + resultCode + " as the code it puts on an internal error");
    assertThat(errorLogRecords(
        WARN_CONFIG_CORE_SERVER_ERROR_RESULT_CODE_NOT_A_FAILURE.get(resultCode.intValue(), ResultCode.OTHER)))
        .as("the server did not say which value it ignored")
        .isNotEmpty();
  }

  @Test
  public void aCodeWhichIsAFailureIsTakenAsItIsAtStartUp()
  {
    assertEquals(CoreConfigManager.serverErrorResultCode(ResultCode.UNWILLING_TO_PERFORM.intValue()),
        ResultCode.UNWILLING_TO_PERFORM);
    assertEquals(CoreConfigManager.serverErrorResultCode(9999), ResultCode.valueOf(9999),
        "the server did not start on a result code it does not know");
    assertThat(errorLogRecords(WARN_CONFIG_CORE_SERVER_ERROR_RESULT_CODE_NOT_A_FAILURE.get(9999, ResultCode.OTHER)))
        .as("the server warned about a code it took as it is")
        .isEmpty();
  }

  @Test
  public void serverErrorResultCodeCanBeSetToAnErrorCode()
  {
    resultCodeToRestore = getServerErrorResultCode().intValue();

    assertEquals(setServerErrorResultCode(ResultCode.UNWILLING_TO_PERFORM.intValue()).getResultCode(),
        ResultCode.SUCCESS, "the server refused an error result code");
    assertEquals(getServerErrorResultCode(), ResultCode.UNWILLING_TO_PERFORM);
  }

  /**
   * A code {@code ResultCode} does not know is a failure - it answers an unknown code
   * which reports one - so the administrator keeps the freedom to put a private code on
   * an internal error.
   */
  @Test
  public void serverErrorResultCodeCanBeSetToACodeWhichIsNotRegistered()
  {
    resultCodeToRestore = getServerErrorResultCode().intValue();

    assertEquals(setServerErrorResultCode(9999).getResultCode(), ResultCode.SUCCESS,
        "the server refused a result code it does not know");
    assertEquals(getServerErrorResultCode().intValue(), 9999);
  }

  private static ResultCode getServerErrorResultCode()
  {
    return DirectoryServer.getCoreConfigManager().getServerErrorResultCode();
  }

  /**
   * Changes the code through an internal operation rather than through {@code ldapmodify},
   * so that a refusal can be read in full: the result code and the reason the server gives
   * for it, not only an exit code which is not zero.
   */
  private static ModifyOperation setServerErrorResultCode(int resultCode)
  {
    return getRootConnection().processModify(newModifyRequest("cn=config")
        .addModification(REPLACE, "ds-cfg-server-error-result-code", String.valueOf(resultCode)));
  }

  /**
   * Returns the records of the error log which carry the given message, by its ID and its
   * text. The test writer is fed by both start-up publishers, so a message it holds is there
   * more than once: what matters is whether it is there at all.
   */
  private static List<String> errorLogRecords(LocalizableMessage message)
  {
    final String record = "msgID=" + message.ordinal() + " msg=" + message;
    final List<String> records = new ArrayList<>();
    for (String logged : TestCaseUtils.ERROR_TEXT_WRITER.getMessages())
    {
      if (logged.contains(record))
      {
        records.add(logged);
      }
    }
    return records;
  }
}
