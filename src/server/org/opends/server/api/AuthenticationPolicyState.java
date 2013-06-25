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



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.OP_ATTR_ACCOUNT_DISABLED;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;



/**
 * The authentication policy context associated with a user's entry, which is
 * responsible for managing the user's account, their password, as well as
 * authenticating the user.
 */
public abstract class AuthenticationPolicyState
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



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
  public final static AuthenticationPolicyState forUser(final Entry userEntry,
      final boolean useDefaultOnError) throws DirectoryException
  {
    final AuthenticationPolicy policy = AuthenticationPolicy.forUser(userEntry,
        useDefaultOnError);
    return policy.createAuthenticationPolicyState(userEntry);
  }



  /**
   * A utility method which may be used by implementations in order to obtain
   * the value of the specified attribute from the provided entry as a boolean.
   *
   * @param entry
   *          The entry whose attribute is to be parsed as a boolean.
   * @param attributeType
   *          The attribute type whose value should be parsed as a boolean.
   * @return The attribute's value represented as a ConditionResult value, or
   *         ConditionResult.UNDEFINED if the specified attribute does not exist
   *         in the entry.
   * @throws DirectoryException
   *           If the value cannot be decoded as a boolean.
   */
  protected static final ConditionResult getBoolean(final Entry entry,
      final AttributeType attributeType) throws DirectoryException
  {
    final List<Attribute> attrList = entry.getAttribute(attributeType);
    if (attrList != null)
    {
      for (final Attribute a : attrList)
      {
        if (a.isEmpty())
        {
          continue;
        }

        final String valueString = toLowerCase(a.iterator().next().getValue()
            .toString());

        if (valueString.equals("true") || valueString.equals("yes")
            || valueString.equals("on") || valueString.equals("1"))
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Attribute %s resolves to true for user entry "
                + "%s", attributeType.getNameOrOID(), entry.getDN().toString());
          }

          return ConditionResult.TRUE;
        }

        if (valueString.equals("false") || valueString.equals("no")
            || valueString.equals("off") || valueString.equals("0"))
        {
          if (debugEnabled())
          {
            TRACER.debugInfo("Attribute %s resolves to false for user "
                + "entry %s", attributeType.getNameOrOID(), entry.getDN()
                .toString());
          }

          return ConditionResult.FALSE;
        }

        if (debugEnabled())
        {
          TRACER.debugError("Unable to resolve value %s for attribute %s "
              + "in user entry %s as a Boolean.", valueString,
              attributeType.getNameOrOID(), entry.getDN().toString());
        }

        final Message message = ERR_PWPSTATE_CANNOT_DECODE_BOOLEAN
            .get(valueString, attributeType.getNameOrOID(), entry.getDN()
                .toString());
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
            message);
      }
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("Returning %s because attribute %s does not exist "
          + "in user entry %s", ConditionResult.UNDEFINED.toString(),
          attributeType.getNameOrOID(), entry.getDN().toString());
    }

    return ConditionResult.UNDEFINED;
  }



  /**
   * A utility method which may be used by implementations in order to obtain
   * the value of the specified attribute from the provided entry as a time in
   * generalized time format.
   *
   * @param entry
   *          The entry whose attribute is to be parsed as a boolean.
   * @param attributeType
   *          The attribute type whose value should be parsed as a generalized
   *          time value.
   * @return The requested time, or -1 if it could not be determined.
   * @throws DirectoryException
   *           If a problem occurs while attempting to decode the value as a
   *           generalized time.
   */
  protected static final long getGeneralizedTime(final Entry entry,
      final AttributeType attributeType) throws DirectoryException
  {
    long timeValue = -1;

    final List<Attribute> attrList = entry.getAttribute(attributeType);
    if (attrList != null)
    {
      for (final Attribute a : attrList)
      {
        if (a.isEmpty())
        {
          continue;
        }

        final AttributeValue v = a.iterator().next();
        try
        {
          timeValue = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(v
              .getNormalizedValue());
        }
        catch (final Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);

            TRACER.debugWarning("Unable to decode value %s for attribute %s "
                + "in user entry %s: %s", v.getValue().toString(),
                attributeType.getNameOrOID(), entry.getDN().toString(),
                stackTraceToSingleLineString(e));
          }

          final Message message = ERR_PWPSTATE_CANNOT_DECODE_GENERALIZED_TIME
              .get(v.getValue().toString(), attributeType.getNameOrOID(), entry
                  .getDN().toString(), String.valueOf(e));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message, e);
        }
        break;
      }
    }

    if (timeValue == -1)
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Returning -1 because attribute %s does not "
            + "exist in user entry %s", attributeType.getNameOrOID(), entry
            .getDN().toString());
      }
    }
    // FIXME: else to be consistent...

    return timeValue;
  }



  /**
   * A boolean indicating whether or not the account associated with this
   * authentication state has been administratively disabled.
   */
  protected ConditionResult isDisabled = ConditionResult.UNDEFINED;

  /**
   * The user entry associated with this authentication policy state.
   */
  protected final Entry userEntry;



  /**
   * Creates a new abstract authentication policy context.
   *
   * @param userEntry
   *          The user's entry.
   */
  protected AuthenticationPolicyState(final Entry userEntry)
  {
    this.userEntry = userEntry;
  }



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
   * Returns the authentication policy associated with this state.
   *
   * @return The authentication policy associated with this state.
   */
  public abstract AuthenticationPolicy getAuthenticationPolicy();



  /**
   * Returns {@code true} if this authentication policy state is associated with
   * a user whose account has been administratively disabled.
   * <p>
   * The default implementation is use the value of the "ds-pwp-account-disable"
   * attribute in the user's entry.
   *
   * @return {@code true} if this authentication policy state is associated with
   *         a user whose account has been administratively disabled.
   */
  public boolean isDisabled()
  {
    final AttributeType type = DirectoryServer.getAttributeType(
        OP_ATTR_ACCOUNT_DISABLED, true);
    try
    {
      isDisabled = getBoolean(userEntry, type);
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      isDisabled = ConditionResult.TRUE;
      if (debugEnabled())
      {
        TRACER.debugWarning("User %s is considered administratively "
            + "disabled because an error occurred while "
            + "attempting to make the determination: %s.", userEntry.getDN()
            .toString(), stackTraceToSingleLineString(e));
      }

      return true;
    }

    if (isDisabled == ConditionResult.UNDEFINED)
    {
      isDisabled = ConditionResult.FALSE;
      if (debugEnabled())
      {
        TRACER.debugInfo("User %s is not administratively disabled since "
            + "the attribute \"%s\" is not present in the entry.", userEntry
            .getDN().toString(), OP_ATTR_ACCOUNT_DISABLED);
      }
      return false;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("User %s %s administratively disabled.", userEntry
          .getDN().toString(), ((isDisabled == ConditionResult.TRUE) ? " is"
          : " is not"));
    }

    return isDisabled == ConditionResult.TRUE;
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
}
