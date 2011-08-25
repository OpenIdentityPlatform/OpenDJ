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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS.
 */
package org.opends.server.core;



import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn.*;
import org.opends.server.api.*;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;



/**
 * This class defines a data structure that holds information about a Directory
 * Server password policy.
 */
public abstract class PasswordPolicy extends AuthenticationPolicy
{

  /**
   * Creates a new password policy.
   */
  protected PasswordPolicy()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  public abstract DN getDN();



  /**
   * Indicates whether the associated password attribute uses the auth password
   * syntax.
   *
   * @return <CODE>true</CODE> if the associated password attribute uses the
   *         auth password syntax, or <CODE>false</CODE> if not.
   */
  public abstract boolean isAuthPasswordSyntax();



  /**
   * Retrieves the default set of password storage schemes that will be used for
   * this password policy. The returned set should not be modified by the
   * caller.
   *
   * @return The default set of password storage schemes that will be used for
   *         this password policy.
   */
  public abstract List<PasswordStorageScheme<?>>
    getDefaultPasswordStorageSchemes();



  /**
   * Gets the "deprecated-password-storage-scheme" property.
   * <p>
   * Specifies the names of the password storage schemes that are considered
   * deprecated for this password policy.
   * <p>
   * If a user with this password policy authenticates to the server and
   * his/her password is encoded with a deprecated scheme, those values are
   * removed and replaced with values encoded using the default password
   * storage scheme(s).
   *
   * @return Returns an unmodifiable set containing the values of the
   *         "deprecated-password-storage-scheme" property.
   */
  public abstract Set<String> getDeprecatedPasswordStorageSchemes();



  /**
   * Indicates whether the specified storage scheme is a default scheme for this
   * password policy.
   *
   * @param name
   *          The name of the password storage scheme for which to make the
   *          determination.
   * @return <CODE>true</CODE> if the storage scheme is a default scheme for
   *         this password policy, or <CODE>false</CODE> if not.
   */
  public abstract boolean isDefaultPasswordStorageScheme(String name);



  /**
   * Indicates whether the specified storage scheme is deprecated.
   *
   * @param name
   *          The name of the password storage scheme for which to make the
   *          determination.
   * @return <CODE>true</CODE> if the storage scheme is deprecated, or
   *         <CODE>false</CODE> if not.
   */
  public abstract boolean isDeprecatedPasswordStorageScheme(String name);



  /**
   * Retrieves the set of password validators for this password policy. The
   * returned list should not be altered by the caller.
   *
   * @return The set of password validators for this password policy.
   */
  public abstract Collection<PasswordValidator<?>> getPasswordValidators();



  /**
   * Retrieves the set of account status notification handlers that should be
   * used with this password policy. The returned list should not be altered by
   * the caller.
   *
   * @return The set of account status notification handlers that should be used
   *         with this password policy.
   */
  public abstract Collection<AccountStatusNotificationHandler<?>>
    getAccountStatusNotificationHandlers();



  /**
   * Retrieves the password generator that will be used with this password
   * policy.
   *
   * @return The password generator that will be used with this password policy,
   *         or <CODE>null</CODE> if there is none.
   */
  public abstract PasswordGenerator<?> getPasswordGenerator();



  /**
   * Retrieves the time by which all users will be required to change their
   * passwords, expressed in the number of milliseconds since midnight of
   * January 1, 1970 (i.e., the zero time for
   * <CODE>System.currentTimeMillis()</CODE>). Any passwords not changed before
   * this time will automatically enter a state in which they must be changed
   * before any other operation will be allowed.
   *
   * @return The time by which all users will be required to change their
   *         passwords, or zero if no such constraint is in effect.
   */
  public abstract long getRequireChangeByTime();



  /**
   * Gets the "allow-expired-password-changes" property.
   * <p>
   * Indicates whether a user whose password is expired is still allowed to
   * change that password using the password modify extended operation.
   *
   * @return Returns the value of the "allow-expired-password-changes" property.
   */
  public abstract boolean isAllowExpiredPasswordChanges();



  /**
   * Gets the "allow-multiple-password-values" property.
   * <p>
   * Indicates whether user entries can have multiple distinct values for the
   * password attribute.
   * <p>
   * This is potentially dangerous because many mechanisms used to change the
   * password do not work well with such a configuration. If multiple password
   * values are allowed, then any of them can be used to authenticate, and they
   * are all subject to the same policy constraints.
   *
   * @return Returns the value of the "allow-multiple-password-values" property.
   */
  public abstract boolean isAllowMultiplePasswordValues();



  /**
   * Gets the "allow-pre-encoded-passwords" property.
   * <p>
   * Indicates whether users can change their passwords by providing a
   * pre-encoded value.
   * <p>
   * This can cause a security risk because the clear-text version of the
   * password is not known and therefore validation checks cannot be applied to
   * it.
   *
   * @return Returns the value of the "allow-pre-encoded-passwords" property.
   */
  public abstract boolean isAllowPreEncodedPasswords();



  /**
   * Gets the "allow-user-password-changes" property.
   * <p>
   * Indicates whether users can change their own passwords.
   * <p>
   * This check is made in addition to access control evaluation. Both must
   * allow the password change for it to occur.
   *
   * @return Returns the value of the "allow-user-password-changes" property.
   */
  public abstract boolean isAllowUserPasswordChanges();



  /**
   * Gets the "expire-passwords-without-warning" property.
   * <p>
   * Indicates whether the directory server allows a user's password to expire
   * even if that user has never seen an expiration warning notification.
   * <p>
   * If this property is true, accounts always expire when the expiration time
   * arrives. If this property is false or disabled, the user always receives at
   * least one warning notification, and the password expiration is set to the
   * warning time plus the warning interval.
   *
   * @return Returns the value of the "expire-passwords-without-warning"
   *         property.
   */
  public abstract boolean isExpirePasswordsWithoutWarning();



  /**
   * Gets the "force-change-on-add" property.
   * <p>
   * Indicates whether users are forced to change their passwords upon first
   * authenticating to the directory server after their account has been
   * created.
   *
   * @return Returns the value of the "force-change-on-add" property.
   */
  public abstract boolean isForceChangeOnAdd();



  /**
   * Gets the "force-change-on-reset" property.
   * <p>
   * Indicates whether users are forced to change their passwords if they are
   * reset by an administrator.
   * <p>
   * For this purpose, anyone with permission to change a given user's password
   * other than that user is considered an administrator.
   *
   * @return Returns the value of the "force-change-on-reset" property.
   */
  public abstract boolean isForceChangeOnReset();



  /**
   * Gets the "grace-login-count" property.
   * <p>
   * Specifies the number of grace logins that a user is allowed after the
   * account has expired to allow that user to choose a new password.
   * <p>
   * A value of 0 indicates that no grace logins are allowed.
   *
   * @return Returns the value of the "grace-login-count" property.
   */
  public abstract int getGraceLoginCount();



  /**
   * Gets the "idle-lockout-interval" property.
   * <p>
   * Specifies the maximum length of time that an account may remain idle (that
   * is, the associated user does not authenticate to the server) before that
   * user is locked out.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds indicates that idle
   * accounts are not automatically locked out. This feature is available only
   * if the last login time is maintained.
   *
   * @return Returns the value of the "idle-lockout-interval" property.
   */
  public abstract long getIdleLockoutInterval();



  /**
   * Gets the "last-login-time-attribute" property.
   * <p>
   * Specifies the name or OID of the attribute type that is used to hold the
   * last login time for users with the associated password policy.
   * <p>
   * This attribute type must be defined in the directory server schema and must
   * either be defined as an operational attribute or must be allowed by the set
   * of objectClasses for all users with the associated password policy.
   *
   * @return Returns the value of the "last-login-time-attribute" property.
   */
  public abstract AttributeType getLastLoginTimeAttribute();



  /**
   * Gets the "last-login-time-format" property.
   * <p>
   * Specifies the format string that is used to generate the last login time
   * value for users with the associated password policy.
   * <p>
   * This format string conforms to the syntax described in the API
   * documentation for the java.text.SimpleDateFormat class.
   *
   * @return Returns the value of the "last-login-time-format" property.
   */
  public abstract String getLastLoginTimeFormat();



  /**
   * Gets the "lockout-duration" property.
   * <p>
   * Specifies the length of time that an account is locked after too many
   * authentication failures.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds indicates that the
   * account must remain locked until an administrator resets the password.
   *
   * @return Returns the value of the "lockout-duration" property.
   */
  public abstract long getLockoutDuration();



  /**
   * Gets the "lockout-failure-count" property.
   * <p>
   * Specifies the maximum number of authentication failures that a user is
   * allowed before the account is locked out.
   * <p>
   * A value of 0 indicates that accounts are never locked out due to failed
   * attempts.
   *
   * @return Returns the value of the "lockout-failure-count" property.
   */
  public abstract int getLockoutFailureCount();



  /**
   * Gets the "lockout-failure-expiration-interval" property.
   * <p>
   * Specifies the length of time before an authentication failure is no longer
   * counted against a user for the purposes of account lockout.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds indicates that the
   * authentication failures must never expire. The failure count is always
   * cleared upon a successful authentication.
   *
   * @return Returns the value of the "lockout-failure-expiration-interval"
   *         property.
   */
  public abstract long getLockoutFailureExpirationInterval();



  /**
   * Gets the "max-password-age" property.
   * <p>
   * Specifies the maximum length of time that a user can continue using the
   * same password before it must be changed (that is, the password expiration
   * interval).
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds disables password
   * expiration.
   *
   * @return Returns the value of the "max-password-age" property.
   */
  public abstract long getMaxPasswordAge();



  /**
   * Gets the "max-password-reset-age" property.
   * <p>
   * Specifies the maximum length of time that users have to change passwords
   * after they have been reset by an administrator before they become locked.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds disables this feature.
   *
   * @return Returns the value of the "max-password-reset-age" property.
   */
  public abstract long getMaxPasswordResetAge();



  /**
   * Gets the "min-password-age" property.
   * <p>
   * Specifies the minimum length of time after a password change before the
   * user is allowed to change the password again.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. This setting can be used to prevent users
   * from changing their passwords repeatedly over a short period of time to
   * flush an old password from the history so that it can be re-used.
   *
   * @return Returns the value of the "min-password-age" property.
   */
  public abstract long getMinPasswordAge();



  /**
   * Gets the "password-attribute" property.
   * <p>
   * Specifies the attribute type used to hold user passwords.
   * <p>
   * This attribute type must be defined in the server schema, and it must have
   * either the user password or auth password syntax.
   *
   * @return Returns the value of the "password-attribute" property.
   */
  public abstract AttributeType getPasswordAttribute();



  /**
   * Gets the "password-change-requires-current-password" property.
   * <p>
   * Indicates whether user password changes must use the password modify
   * extended operation and must include the user's current password before the
   * change is allowed.
   *
   * @return Returns the value of the
   *         "password-change-requires-current-password" property.
   */
  public abstract boolean isPasswordChangeRequiresCurrentPassword();



  /**
   * Gets the "password-expiration-warning-interval" property.
   * <p>
   * Specifies the maximum length of time before a user's password actually
   * expires that the server begins to include warning notifications in bind
   * responses for that user.
   * <p>
   * The value of this attribute is an integer followed by a unit of seconds,
   * minutes, hours, days, or weeks. A value of 0 seconds disables the warning
   * interval.
   *
   * @return Returns the value of the "password-expiration-warning-interval"
   *         property.
   */
  public abstract long getPasswordExpirationWarningInterval();



  /**
   * Gets the "password-history-count" property.
   * <p>
   * Specifies the maximum number of former passwords to maintain in the
   * password history.
   * <p>
   * When choosing a new password, the proposed password is checked to ensure
   * that it does not match the current password, nor any other password in the
   * history list. A value of zero indicates that either no password history is
   * to be maintained (if the password history duration has a value of zero
   * seconds), or that there is no maximum number of passwords to maintain in
   * the history (if the password history duration has a value greater than zero
   * seconds).
   *
   * @return Returns the value of the "password-history-count" property.
   */
  public abstract int getPasswordHistoryCount();



  /**
   * Gets the "password-history-duration" property.
   * <p>
   * Specifies the maximum length of time that passwords remain in the password
   * history.
   * <p>
   * When choosing a new password, the proposed password is checked to ensure
   * that it does not match the current password, nor any other password in the
   * history list. A value of zero seconds indicates that either no password
   * history is to be maintained (if the password history count has a value of
   * zero), or that there is no maximum duration for passwords in the history
   * (if the password history count has a value greater than zero).
   *
   * @return Returns the value of the "password-history-duration" property.
   */
  public abstract long getPasswordHistoryDuration();



  /**
   * Gets the "previous-last-login-time-format" property.
   * <p>
   * Specifies the format string(s) that might have been used with the last
   * login time at any point in the past for users associated with the password
   * policy.
   * <p>
   * These values are used to make it possible to parse previous values, but are
   * not used to set new values. The format strings conform to the syntax
   * described in the API documentation for the java.text.SimpleDateFormat
   * class.
   *
   * @return Returns an unmodifiable set containing the values of the
   *         "previous-last-login-time-format" property.
   */
  public abstract SortedSet<String> getPreviousLastLoginTimeFormats();



  /**
   * Gets the "require-secure-authentication" property.
   * <p>
   * Indicates whether users with the associated password policy are required to
   * authenticate in a secure manner.
   * <p>
   * This might mean either using a secure communication channel between the
   * client and the server, or using a SASL mechanism that does not expose the
   * credentials.
   *
   * @return Returns the value of the "require-secure-authentication" property.
   */
  public abstract boolean isRequireSecureAuthentication();



  /**
   * Gets the "require-secure-password-changes" property.
   * <p>
   * Indicates whether users with the associated password policy are required to
   * change their password in a secure manner that does not expose the
   * credentials.
   *
   * @return Returns the value of the "require-secure-password-changes"
   *         property.
   */
  public abstract boolean isRequireSecurePasswordChanges();



  /**
   * Gets the "skip-validation-for-administrators" property.
   * <p>
   * Indicates whether passwords set by administrators are allowed to bypass the
   * password validation process that is required for user password changes.
   *
   * @return Returns the value of the "skip-validation-for-administrators"
   *         property.
   */
  public abstract boolean isSkipValidationForAdministrators();



  /**
   * Gets the "state-update-failure-policy" property.
   * <p>
   * Specifies how the server deals with the inability to update password policy
   * state information during an authentication attempt.
   * <p>
   * In particular, this property can be used to control whether an otherwise
   * successful bind operation fails if a failure occurs while attempting to
   * update password policy state information (for example, to clear a record of
   * previous authentication failures or to update the last login time). It can
   * also be used to control whether to reject a bind request if it is known
   * ahead of time that it will not be possible to update the authentication
   * failure times in the event of an unsuccessful bind attempt (for example, if
   * the backend writability mode is disabled).
   *
   * @return Returns the value of the "state-update-failure-policy" property.
   */
  public abstract StateUpdateFailurePolicy getStateUpdateFailurePolicy();



  /**
   * {@inheritDoc}
   */
  public boolean isPasswordPolicy()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public PasswordPolicyState createAuthenticationPolicyState(Entry userEntry,
      long time) throws DirectoryException
  {
    return new PasswordPolicyState(this, userEntry, time);
  }
}
