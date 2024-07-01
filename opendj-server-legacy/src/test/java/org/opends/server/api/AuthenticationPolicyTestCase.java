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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.api;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Test authentication policy interaction. */
public class AuthenticationPolicyTestCase extends APITestCase
{
  /** A mock policy which records which methods have been called and their parameters. */
  private final class MockPolicy extends AuthenticationPolicy
  {
    private final boolean isDisabled;
    private final boolean matches;
    private boolean isPolicyFinalized;
    private boolean isStateFinalized;
    private ByteString matchedPassword;

    /**
     * Returns {@code true} if {@code finalizeAuthenticationPolicy} was called.
     *
     * @return {@code true} if {@code finalizeAuthenticationPolicy} was called.
     */
    public boolean isPolicyFinalized()
    {
      return isPolicyFinalized;
    }

    /**
     * Returns {@code true} if {@code finalizeStateAfterBind} was called.
     *
     * @return {@code true} if {@code finalizeStateAfterBind} was called.
     */
    public boolean isStateFinalized()
    {
      return isStateFinalized;
    }

    /**
     * Returns the password which was tested.
     *
     * @return The password which was tested.
     */
    public ByteString getMatchedPassword()
    {
      return matchedPassword;
    }

    /**
     * Creates a new mock policy.
     *
     * @param matches
     *          The result to always return from {@code passwordMatches}.
     * @param isDisabled
     *          The result to return from {@code isDisabled}.
     */
    public MockPolicy(boolean matches, boolean isDisabled)
    {
      this.matches = matches;
      this.isDisabled = isDisabled;
    }

    @Override
    public DN getDN()
    {
      return policyDN;
    }

    @Override
    public AuthenticationPolicyState createAuthenticationPolicyState(
        Entry userEntry, long time) throws DirectoryException
    {
      return new AuthenticationPolicyState(userEntry)
      {

        @Override
        public boolean passwordMatches(ByteString password)
            throws DirectoryException
        {
          matchedPassword = password;
          return matches;
        }

        @Override
        public boolean isDisabled()
        {
          return MockPolicy.this.isDisabled;
        }

        @Override
        public void finalizeStateAfterBind() throws DirectoryException
        {
          isStateFinalized = true;
        }

        @Override
        public AuthenticationPolicy getAuthenticationPolicy()
        {
          return MockPolicy.this;
        }
      };
    }

    @Override
    public void finalizeAuthenticationPolicy()
    {
      isPolicyFinalized = true;
    }
  }

  private final String policyDNString = "cn=test policy,o=test";
  private final String userDNString = "cn=test user,o=test";
  private DN policyDN;

  /**
   * Ensures that the Directory Server is running and creates a test backend
   * containing a single test user.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();

    policyDN = DN.valueOf(policyDNString);
  }

  /**
   * Returns test data for the simple/sasl tests.
   *
   * @return Test data for the simple/sasl tests.
   */
  @DataProvider
  public Object[][] testBindData()
  {
    // @formatter:off
    return new Object[][] {
        /* password matches, account is disabled */
        { false, false },
        { false,  true },
        {  true, false },
        {  true,  true },
    };
    // @formatter:on
  }

  /**
   * Test simple authentication where password validation succeeds.
   *
   * @param matches
   *          The result to always return from {@code passwordMatches}.
   * @param isDisabled
   *          The result to return from {@code isDisabled}.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testBindData")
  public void testSimpleBind(boolean matches, boolean isDisabled)
      throws Exception
  {
    MockPolicy policy = new MockPolicy(matches, isDisabled);
    DirectoryServer.registerAuthenticationPolicy(policyDN, policy);
    try
    {
      // Create an empty test backend 'o=test'
      TestCaseUtils.initializeTestBackend(true);

      /* The test user which who will be authenticated. */
      TestCaseUtils.addEntries(
          /* @formatter:off */
          "dn: " + userDNString,
          "objectClass: top",
          "objectClass: person",
          "ds-pwp-password-policy-dn: " + policyDNString,
          "userPassword: password",
          "sn: user",
          "cn: test user"
          /* @formatter:on */
      );

      // Perform the simple bind.
      InternalClientConnection conn = InternalClientConnection
          .getRootConnection();
      BindOperation bind = conn.processSimpleBind(userDNString, "password");

      // Check authentication result.
      assertEquals(bind.getResultCode(),
          matches & !isDisabled ? ResultCode.SUCCESS
              : ResultCode.INVALID_CREDENTIALS);

      // Verify interaction with the policy/state.
      assertTrue(policy.isStateFinalized());
      assertFalse(policy.isPolicyFinalized());
      if (!isDisabled)
      {
        assertEquals(policy.getMatchedPassword().toString(), "password");
      }
      else
      {
        // If the account is disabled then the password should not have been
        // checked. This is important because we want to avoid potentially
        // expensive password fetches (e.g. PTA).
        assertNull(policy.getMatchedPassword());
      }
    }
    finally
    {
      DirectoryServer.deregisterAuthenticationPolicy(policyDN);
      assertTrue(policy.isPolicyFinalized());
    }
  }

  /**
   * Test simple authentication where password validation succeeds.
   *
   * @param matches
   *          The result to always return from {@code passwordMatches}.
   * @param isDisabled
   *          The result to return from {@code isDisabled}.
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test(dataProvider = "testBindData")
  public void testSASLPLAINBind(boolean matches, boolean isDisabled)
      throws Exception
  {
    MockPolicy policy = new MockPolicy(matches, isDisabled);
    DirectoryServer.registerAuthenticationPolicy(policyDN, policy);
    try
    {
      // Create an empty test backend 'o=test'
      TestCaseUtils.initializeTestBackend(true);

      /* The test user which who will be authenticated. */
      TestCaseUtils.addEntries(
          /* @formatter:off */
          "dn: " + userDNString,
          "objectClass: top",
          "objectClass: person",
          "ds-pwp-password-policy-dn: " + policyDNString,
          "userPassword: password",
          "sn: user",
          "cn: test user"
          /* @formatter:on */
      );

      // Perform the simple bind.
      InternalClientConnection conn = InternalClientConnection
          .getRootConnection();

      ByteStringBuilder credentials = new ByteStringBuilder();
      credentials.appendByte(0);
      credentials.appendUtf8("dn:" + userDNString);
      credentials.appendByte(0);
      credentials.appendUtf8("password");

      BindOperation bind = conn.processSASLBind(DN.rootDN(), "PLAIN",
          credentials.toByteString());

      // Check authentication result.
      assertEquals(bind.getResultCode(),
          matches & !isDisabled ? ResultCode.SUCCESS
              : ResultCode.INVALID_CREDENTIALS);

      // Verify interaction with the policy/state.
      assertTrue(policy.isStateFinalized());
      assertFalse(policy.isPolicyFinalized());
      if (!isDisabled)
      {
        assertEquals(policy.getMatchedPassword().toString(), "password");
      }
      else
      {
        // If the account is disabled then the password should not have been
        // checked. This is important because we want to avoid potentially
        // expensive password fetches (e.g. PTA).
        assertNull(policy.getMatchedPassword());
      }
    }
    finally
    {
      DirectoryServer.deregisterAuthenticationPolicy(policyDN);
      assertTrue(policy.isPolicyFinalized());
    }
  }
}
