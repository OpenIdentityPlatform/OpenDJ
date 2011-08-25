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



import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;



/**
 * The authentication policy context associated with a user's entry, which is
 * responsible for managing the user's account, their password, as well as
 * authenticating the user.
 */
public abstract class AuthenticationPolicyState
{
  /**
   * Returns the authentication policy state for the user provided user. This
   * method is equivalent to the following:
   *
   * <pre>
   * AuthenticationPolicy policy = AuthenticationPolicy.forUser(userEntry,
   *     useDefaultOnError);
   * AuthenticationPolicyState state = policy
   *     .createAuthenticationPolicyState(userEntry);
   * </pre>
   *
   * See the documentation of {@link AuthenticationPolicy#forUser} for a
   * description of the algorithm used to find a user's authentication policy.
   *
   * @param userEntry
   *          The user entry.
   * @param useDefaultOnError
   *          Indicates whether the server should fall back to using the default
   *          password policy if there is a problem with the configured policy
   *          for the user.
   * @return The password policy for the user.
   * @throws DirectoryException
   *           If a problem occurs while attempting to determine the password
   *           policy for the user.
   * @see AuthenticationPolicy#forUser(Entry, boolean)
   */
  public final static AuthenticationPolicyState forUser(Entry userEntry,
      boolean useDefaultOnError) throws DirectoryException
  {
    AuthenticationPolicy policy = AuthenticationPolicy.forUser(userEntry,
        useDefaultOnError);
    return policy.createAuthenticationPolicyState(userEntry);
  }



  /**
   * Creates a new abstract authentication policy context.
   */
  protected AuthenticationPolicyState()
  {
    // No implementation required.
  }



  /**
   * Returns {@code true} if the provided password value matches any of the
   * user's passwords.
   *
   * @param password
   *          The user-provided password to verify.
   * @return {@code true} if the provided password value matches any of the
   *         user's passwords.
   * @throws DirectoryException
   *           If verification unexpectedly failed.
   */
  public abstract boolean passwordMatches(ByteString password)
      throws DirectoryException;



  /**
   * Returns the authentication policy associated with this state.
   *
   * @return The authentication policy associated with this state.
   */
  public abstract AuthenticationPolicy getAuthenticationPolicy();



  /**
   * Performs any finalization required after a bind operation has completed.
   * Implementations may perform internal operations in order to persist
   * internal state to the user's entry if needed.
   *
   * @throws DirectoryException
   *           If a problem occurs during finalization.
   */
  public void finalizeStateAfterBind() throws DirectoryException
  {
    // Do nothing by default.
  }



  /**
   * Returns {@code true} if this authentication policy state is associated with
   * a password policy and the method {@link #getAuthenticationPolicy} will
   * return a {@code PasswordPolicy}.
   *
   * @return {@code true} if this authentication policy state is associated with
   *         a password policy, otherwise {@code false}.
   */
  public boolean isPasswordPolicy()
  {
    return getAuthenticationPolicy().isPasswordPolicy();
  }
}
