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
 *      Copyright 2011 ForgeRock AS.
 */
package org.opends.server.api;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Test authentication policy interaction.
 */
public class AuthenticationPolicyTestCase extends APITestCase
{

  /**
   * A mock policy which records which methods have been called and their
   * parameters.
   */
  private final class MockPolicy extends AuthenticationPolicy
  {
    private final boolean isDisabled;

    private boolean isPolicyFinalized = false;

    private boolean isStateFinalized = false;

    private final boolean matches;

    private ByteString matchedPassword = null;



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



    /**
     * {@inheritDoc}
     */
    public DN getDN()
    {
      return policyDN;
    }



    /**
     * {@inheritDoc}
     */
    public AuthenticationPolicyState createAuthenticationPolicyState(
        Entry userEntry, long time) throws DirectoryException
    {
      return new AuthenticationPolicyState(userEntry)
      {

        /**
         * {@inheritDoc}
         */
        public boolean passwordMatches(ByteString password)
            throws DirectoryException
        {
          matchedPassword = password;
          return matches;
        }



        /**
         * {@inheritDoc}
         */
        public boolean isDisabled()
        {
          return MockPolicy.this.isDisabled;
        }



        /**
         * {@inheritDoc}
         */
        public void finalizeStateAfterBind() throws DirectoryException
        {
          isStateFinalized = true;
        }



        /**
         * {@inheritDoc}
         */
        public AuthenticationPolicy getAuthenticationPolicy()
        {
          return MockPolicy.this;
        }
      };
    }



    /**
     * {@inheritDoc}
     */
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
  @BeforeClass()
  public void beforeClass() throws Exception
  {
    TestCaseUtils.startServer();

    policyDN = DN.decode(policyDNString);
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

      /*
       * The test user which who will be authenticated.
       */
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

      /*
       * The test user which who will be authenticated.
       */
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
      credentials.append((byte) 0);
      credentials.append("dn:" + userDNString);
      credentials.append((byte) 0);
      credentials.append("password");

      BindOperation bind = conn.processSASLBind(DN.nullDN(), "PLAIN",
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
