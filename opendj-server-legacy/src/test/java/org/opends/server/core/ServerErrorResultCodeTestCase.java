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

import static org.opends.server.TestCaseUtils.applyModifications;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

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
  public void tearDown() throws Exception
  {
    if (resultCodeToRestore != null)
    {
      final int resultCode = resultCodeToRestore;
      resultCodeToRestore = null;
      assertEquals(setServerErrorResultCode(resultCode), 0,
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
      throws Exception
  {
    final ResultCode inForce = getServerErrorResultCode();

    assertNotEquals(setServerErrorResultCode(resultCode.intValue()), 0,
        "the server accepted " + resultCode + " as the code it puts on an internal error");
    assertEquals(getServerErrorResultCode(), inForce,
        "a refused change to the server error result code was applied all the same");
  }

  @Test
  public void serverErrorResultCodeCanBeSetToAnErrorCode() throws Exception
  {
    resultCodeToRestore = getServerErrorResultCode().intValue();

    assertEquals(setServerErrorResultCode(ResultCode.UNWILLING_TO_PERFORM.intValue()), 0,
        "the server refused an error result code");
    assertEquals(getServerErrorResultCode(), ResultCode.UNWILLING_TO_PERFORM);
  }

  /**
   * A code {@code ResultCode} does not know is a failure - it answers an unknown code
   * which reports one - so the administrator keeps the freedom to put a private code on
   * an internal error.
   */
  @Test
  public void serverErrorResultCodeCanBeSetToACodeWhichIsNotRegistered() throws Exception
  {
    resultCodeToRestore = getServerErrorResultCode().intValue();

    assertEquals(setServerErrorResultCode(9999), 0,
        "the server refused a result code it does not know");
    assertEquals(getServerErrorResultCode().intValue(), 9999);
  }

  private static ResultCode getServerErrorResultCode()
  {
    return DirectoryServer.getCoreConfigManager().getServerErrorResultCode();
  }

  private static int setServerErrorResultCode(int resultCode) throws Exception
  {
    return applyModifications(true,
        "dn: cn=config",
        "changetype: modify",
        "replace: ds-cfg-server-error-result-code",
        "ds-cfg-server-error-result-code: " + resultCode);
  }
}
