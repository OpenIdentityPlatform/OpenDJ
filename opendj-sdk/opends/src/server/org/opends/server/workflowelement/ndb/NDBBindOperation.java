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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.ndb;



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AccountStatusNotification;
import org.opends.server.types.AccountStatusNotificationType;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;

import org.opends.server.workflowelement.localbackend.LocalBackendBindOperation;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation used to bind against the Directory Server,
 * with the bound user entry within a local backend.
 */
public class NDBBindOperation
       extends LocalBackendBindOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new operation that may be used to bind where
   * the bound user entry is stored in a local backend of the Directory Server.
   *
   * @param bind The operation to enhance.
   */
  public NDBBindOperation(BindOperation bind)
  {
    super(bind);
    NDBWorkflowElement.attachLocalOperation (bind, this);
  }



  /**
   * Performs the processing necessary for a simple bind operation.
   *
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the bind
   *                              operation to fail.
   */
  @Override
  protected boolean processSimpleBind()
          throws DirectoryException
  {
    // See if this is an anonymous bind.  If so, then determine whether
    // to allow it.
    ByteString simplePassword = getSimplePassword();
    if ((simplePassword == null) || (simplePassword.length() == 0))
    {
      return processAnonymousSimpleBind();
    }

    // See if the bind DN is actually one of the alternate root DNs
    // defined in the server.  If so, then replace it with the actual DN
    // for that user.
    DN actualRootDN = DirectoryServer.getActualRootBindDN(bindDN);
    if (actualRootDN != null)
    {
      bindDN = actualRootDN;
    }

    // Get the user entry based on the bind DN.  If it does not exist,
    // then fail.
    Entry userEntry;
    try {
      userEntry = backend.getEntry(bindDN);
    } catch (DirectoryException de) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      userEntry = null;
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
        de.getMessageObject());
    }

    if (userEntry == null) {
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
        ERR_BIND_OPERATION_UNKNOWN_USER.get(
        String.valueOf(bindDN)));
    } else {
      setUserEntryDN(userEntry.getDN());
    }


    // Check to see if the user has a password.  If not, then fail.
    // FIXME -- We need to have a way to enable/disable debugging.
    pwPolicyState = new PasswordPolicyState(userEntry, false);
    policy = pwPolicyState.getPolicy();
    AttributeType pwType = policy.getPasswordAttribute();

    List<Attribute> pwAttr = userEntry.getAttribute(pwType);
    if ((pwAttr == null) || (pwAttr.isEmpty())) {
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
        ERR_BIND_OPERATION_NO_PASSWORD.get(
        String.valueOf(bindDN)));
    }


    // Perform a number of password policy state checks for the user.
    checkPasswordPolicyState(userEntry, null);


    // Invoke the pre-operation bind plugins.
    executePostOpPlugins = true;
    PluginResult.PreOperation preOpResult =
      pluginConfigManager.invokePreOperationBindPlugins(this);
    if (!preOpResult.continueProcessing()) {
      setResultCode(preOpResult.getResultCode());
      appendErrorMessage(preOpResult.getErrorMessage());
      setMatchedDN(preOpResult.getMatchedDN());
      setReferralURLs(preOpResult.getReferralURLs());
      return false;
    }


    // Determine whether the provided password matches any of the stored
    // passwords for the user.
    if (pwPolicyState.passwordMatches(simplePassword)) {
      setResultCode(ResultCode.SUCCESS);

      boolean isRoot = DirectoryServer.isRootDN(userEntry.getDN());
      if (DirectoryServer.lockdownMode() && (!isRoot)) {
        throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
          ERR_BIND_REJECTED_LOCKDOWN_MODE.get());
      }
      setAuthenticationInfo(new AuthenticationInfo(userEntry,
        simplePassword,
        isRoot));


      // Set resource limits for the authenticated user.
      setResourceLimits(userEntry);


      // Perform any remaining processing for a successful simple
      // authentication.
      pwPolicyState.handleDeprecatedStorageSchemes(simplePassword);
      pwPolicyState.clearFailureLockout();

      if (isFirstWarning) {
        pwPolicyState.setWarnedTime();

        int numSeconds = pwPolicyState.getSecondsUntilExpiration();
        Message m = WARN_BIND_PASSWORD_EXPIRING.get(
          secondsToTimeString(numSeconds));

        pwPolicyState.generateAccountStatusNotification(
          AccountStatusNotificationType.PASSWORD_EXPIRING, userEntry, m,
          AccountStatusNotification.createProperties(pwPolicyState,
          false, numSeconds, null, null));
      }

      if (isGraceLogin) {
        pwPolicyState.updateGraceLoginTimes();
      }

      pwPolicyState.setLastLoginTime();
    } else {
      setResultCode(ResultCode.INVALID_CREDENTIALS);
      setAuthFailureReason(ERR_BIND_OPERATION_WRONG_PASSWORD.get());

      if (policy.getLockoutFailureCount() > 0) {
        pwPolicyState.updateAuthFailureTimes();
        if (pwPolicyState.lockedDueToFailures()) {
          AccountStatusNotificationType notificationType;
          Message m;

          boolean tempLocked;
          int lockoutDuration = pwPolicyState.getSecondsUntilUnlock();
          if (lockoutDuration > -1) {
            notificationType =
              AccountStatusNotificationType.ACCOUNT_TEMPORARILY_LOCKED;
            tempLocked = true;

            m = ERR_BIND_ACCOUNT_TEMPORARILY_LOCKED.get(
              secondsToTimeString(lockoutDuration));
          } else {
            notificationType =
              AccountStatusNotificationType.ACCOUNT_PERMANENTLY_LOCKED;
            tempLocked = false;

            m = ERR_BIND_ACCOUNT_PERMANENTLY_LOCKED.get();
          }

          pwPolicyState.generateAccountStatusNotification(
            notificationType, userEntry, m,
            AccountStatusNotification.createProperties(pwPolicyState,
            tempLocked, -1, null, null));
        }
      }
    }

    return true;
  }
}

