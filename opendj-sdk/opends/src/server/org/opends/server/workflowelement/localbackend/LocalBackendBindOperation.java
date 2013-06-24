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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.messages.MessageDescriptor.Arg1;
import org.opends.messages.MessageDescriptor.Arg2;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn;
import org.opends.server.api.*;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationBindOperation;
import org.opends.server.types.operation.PostResponseBindOperation;
import org.opends.server.types.operation.PreOperationBindOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines an operation used to bind against the Directory Server,
 * with the bound user entry within a local backend.
 */
public class LocalBackendBindOperation
       extends BindOperationWrapper
       implements PreOperationBindOperation, PostOperationBindOperation,
                  PostResponseBindOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The backend in which the bind operation should be processed.
   */
  protected Backend backend;

  /**
   * Indicates whether the bind response should include the first warning
   * for an upcoming password expiration.
   */
  protected boolean isFirstWarning;

  /**
   * Indicates whether this bind is using a grace login for the user.
   */
  protected boolean isGraceLogin;

  // Indicates whether the user must change his/her password before doing
  // anything else.
  private boolean mustChangePassword;

  // Indicates whether the user requested the password policy control.
  private boolean pwPolicyControlRequested;

  // Indicates whether the server should return the authorization ID as a
  // control in the bind response.
  private boolean returnAuthzID;

  /**
   * Indicates whether to execute post-operation plugins.
   */
  protected boolean executePostOpPlugins;

  // The client connection associated with this bind operation.
  private ClientConnection clientConnection;

  /**
   * The bind DN provided by the client.
   */
  protected DN bindDN;

  // The lookthrough limit that should be enforced for the user.
  private int lookthroughLimit;

  // The value to use for the password policy warning.
  private int pwPolicyWarningValue;

  // The size limit that should be enforced for the user.
  private int sizeLimit;

  // The time limit that should be enforced for the user.
  private int timeLimit;

  // The idle time limit that should be enforced for the user.
  private long idleTimeLimit;

  // Authentication policy state.
  private AuthenticationPolicyState authPolicyState;

  // The password policy error type for this bind operation.
  private PasswordPolicyErrorType pwPolicyErrorType;

  // The password policy warning type for this bind operation.
  private PasswordPolicyWarningType pwPolicyWarningType;

  /**
   * The plugin config manager for the Directory Server.
   */
  protected PluginConfigManager pluginConfigManager;

  // The SASL mechanism used for this bind operation.
  private String saslMechanism;



  /**
   * Creates a new operation that may be used to bind where
   * the bound user entry is stored in a local backend of the Directory Server.
   *
   * @param bind The operation to enhance.
   */
  public LocalBackendBindOperation(BindOperation bind)
  {
    super(bind);
    LocalBackendWorkflowElement.attachLocalOperation (bind, this);
  }



  /**
   * Process this bind operation in a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   *
   */
  public void processLocalBind(LocalBackendWorkflowElement wfe)
  {
    this.backend = wfe.getBackend();

    // Initialize a number of variables for use during the bind processing.
    clientConnection         = getClientConnection();
    returnAuthzID            = false;
    executePostOpPlugins     = false;
    sizeLimit                = DirectoryServer.getSizeLimit();
    timeLimit                = DirectoryServer.getTimeLimit();
    lookthroughLimit         = DirectoryServer.getLookthroughLimit();
    idleTimeLimit            = DirectoryServer.getIdleTimeLimit();
    bindDN                   = getBindDN();
    saslMechanism            = getSASLMechanism();
    authPolicyState          = null;
    pwPolicyErrorType        = null;
    pwPolicyControlRequested = false;
    isGraceLogin             = false;
    isFirstWarning           = false;
    mustChangePassword       = false;
    pwPolicyWarningType      = null;
    pwPolicyWarningValue     = -1 ;
    pluginConfigManager      = DirectoryServer.getPluginConfigManager();

    processBind();

    // Update the user's account with any password policy changes that may be
    // required.
    try
    {
      if (authPolicyState != null)
      {
        authPolicyState.finalizeStateAfterBind();
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResponseData(de);
    }


    // Invoke the post-operation bind plugins.
    if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationBindPlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
      }
    }


    // Update the authentication information for the user.
    AuthenticationInfo authInfo = getAuthenticationInfo();
    if ((getResultCode() == ResultCode.SUCCESS) && (authInfo != null))
    {
      clientConnection.setAuthenticationInfo(authInfo);
      clientConnection.setSizeLimit(sizeLimit);
      clientConnection.setTimeLimit(timeLimit);
      clientConnection.setIdleTimeLimit(idleTimeLimit);
      clientConnection.setLookthroughLimit(lookthroughLimit);
      clientConnection.setMustChangePassword(mustChangePassword);

      if (returnAuthzID)
      {
        addResponseControl(new AuthorizationIdentityResponseControl(
                                    authInfo.getAuthorizationDN()));
      }
    }


    // See if we need to send a password policy control to the client.  If so,
    // then add it to the response.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      if (pwPolicyControlRequested)
      {
        PasswordPolicyResponseControl pwpControl =
             new PasswordPolicyResponseControl(pwPolicyWarningType,
                                               pwPolicyWarningValue,
                                               pwPolicyErrorType);
        addResponseControl(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          addResponseControl(new PasswordExpiredControl());
        }
        else if (pwPolicyWarningType ==
                 PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION)
        {
          addResponseControl(new PasswordExpiringControl(pwPolicyWarningValue));
        }
        else if (mustChangePassword)
        {
          addResponseControl(new PasswordExpiredControl());
        }
      }
    }
    else
    {
      if (pwPolicyControlRequested)
      {
        PasswordPolicyResponseControl pwpControl =
             new PasswordPolicyResponseControl(pwPolicyWarningType,
                                               pwPolicyWarningValue,
                                               pwPolicyErrorType);
        addResponseControl(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          addResponseControl(new PasswordExpiredControl());
        }
      }
    }
  }


  /**
   * Performs the checks and processing necessary for the current bind operation
   * (simple or SASL).
   */
  private void processBind()
  {
    // Check to see if the client has permission to perform the
    // bind.

    // FIXME: for now assume that this will check all permission
    // pertinent to the operation. This includes any controls
    // specified.
    try
    {
      if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
          .isAllowed(this))
      {
        setResultCode(ResultCode.INVALID_CREDENTIALS);
        setAuthFailureReason(ERR_BIND_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get());
        return;
      }
    }
    catch (DirectoryException e)
    {
      setResultCode(e.getResultCode());
      setAuthFailureReason(e.getMessageObject());
      return;
    }

    // Check to see if there are any controls in the request. If so, then see
    // if there is any special processing required.
    try
    {
      handleRequestControls();
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResponseData(de);
      return;
    }

    // Check to see if this is a simple bind or a SASL bind and process
    // accordingly.
    try
    {
      switch (getAuthenticationType())
      {
      case SIMPLE:
        processSimpleBind();
        break;

      case SASL:
        processSASLBind();
        break;

      default:
        // Send a protocol error response to the client and disconnect.
        // We should never come here.
        setResultCode(ResultCode.PROTOCOL_ERROR);
      }
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      if (de.getResultCode() == ResultCode.INVALID_CREDENTIALS)
      {
        setResultCode(ResultCode.INVALID_CREDENTIALS);
        setAuthFailureReason(de.getMessageObject());
      }
      else
      {
        setResponseData(de);
      }
    }
  }

  /**
   * Handles request control processing for this bind operation.
   *
   * @throws  DirectoryException  If there is a problem with any of the
   *                              controls.
   */
  private void handleRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (!LocalBackendWorkflowElement.isControlAllowed(bindDN, this, c))
        {
          // Skip disallowed non-critical controls.
          continue;
        }

        if (oid.equals(OID_AUTHZID_REQUEST))
        {
          returnAuthzID = true;
        }
        else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
        {
          pwPolicyControlRequested = true;
        }

        // NYI -- Add support for additional controls.

        else if (c.isCritical())
        {
          throw new DirectoryException(
                         ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                         ERR_BIND_UNSUPPORTED_CRITICAL_CONTROL.get(oid));
        }
      }
    }
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
    final Lock userLock = LockManager.lockRead(bindDN);
    if (userLock == null)
    {
      throw new DirectoryException(ResultCode.BUSY,
                                   ERR_BIND_OPERATION_CANNOT_LOCK_USER.get(
                                        String.valueOf(bindDN)));
    }

    try
    {
      Entry userEntry;
      try
      {
        userEntry = backend.getEntry(bindDN);
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        userEntry = null;

        if (de.getResultCode() == ResultCode.REFERRAL)
        {
          // Re-throw referral exceptions - these should be passed back
          // to the client.
          throw de;
        }
        else
        {
          // Replace other exceptions in case they expose any sensitive
          // information.
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
              de.getMessageObject());
        }
      }

      if (userEntry == null)
      {
        throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                                     ERR_BIND_OPERATION_UNKNOWN_USER.get());
      }
      else
      {
        setUserEntryDN(userEntry.getDN());
      }


      // Check to see if the user has a password. If not, then fail.
      // FIXME -- We need to have a way to enable/disable debugging.
      authPolicyState = AuthenticationPolicyState.forUser(userEntry, false);
      if (authPolicyState.isPasswordPolicy())
      {
        // Account is managed locally.
        PasswordPolicyState pwPolicyState =
          (PasswordPolicyState) authPolicyState;
        PasswordPolicy policy = pwPolicyState.getAuthenticationPolicy();

        AttributeType pwType = policy.getPasswordAttribute();

        List<Attribute> pwAttr = userEntry.getAttribute(pwType);
        if ((pwAttr == null) || (pwAttr.isEmpty()))
        {
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
              ERR_BIND_OPERATION_NO_PASSWORD.get());
        }

        // Perform a number of password policy state checks for the user.
        checkPasswordPolicyState(userEntry, null);

        // Invoke pre-operation plugins.
        if (!invokePreOpPlugins())
        {
          return false;
        }

        // Determine whether the provided password matches any of the stored
        // passwords for the user.
        if (pwPolicyState.passwordMatches(simplePassword))
        {
          setResultCode(ResultCode.SUCCESS);

          if (DirectoryServer.lockdownMode()
              && (!ClientConnection.hasPrivilege(userEntry,
                  Privilege.BYPASS_LOCKDOWN)))
          {
            throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                ERR_BIND_REJECTED_LOCKDOWN_MODE.get());
          }
          setAuthenticationInfo(new AuthenticationInfo(userEntry, getBindDN(),
              simplePassword, DirectoryServer.isRootDN(userEntry.getDN())));

          // Set resource limits for the authenticated user.
          setResourceLimits(userEntry);

          // Perform any remaining processing for a successful simple
          // authentication.
          pwPolicyState.handleDeprecatedStorageSchemes(simplePassword);
          pwPolicyState.clearFailureLockout();

          if (isFirstWarning)
          {
            pwPolicyState.setWarnedTime();

            int numSeconds = pwPolicyState.getSecondsUntilExpiration();
            Message m = WARN_BIND_PASSWORD_EXPIRING
                .get(secondsToTimeString(numSeconds));

            pwPolicyState.generateAccountStatusNotification(
                AccountStatusNotificationType.PASSWORD_EXPIRING, userEntry, m,
                AccountStatusNotification.createProperties(pwPolicyState,
                    false, numSeconds, null, null));
          }

          if (isGraceLogin)
          {
            pwPolicyState.updateGraceLoginTimes();
          }

          pwPolicyState.setLastLoginTime();
        }
        else
        {
          setResultCode(ResultCode.INVALID_CREDENTIALS);
          setAuthFailureReason(ERR_BIND_OPERATION_WRONG_PASSWORD.get());

          if (policy.getLockoutFailureCount() > 0)
          {
            generateAccountStatusNotificationForLockedBindAccount(userEntry,
                pwPolicyState);
          }
        }
      }
      else
      {
        // Check to see if the user is administratively disabled or locked.
        if (authPolicyState.isDisabled())
        {
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
              ERR_BIND_OPERATION_ACCOUNT_DISABLED.get());
        }

        // Invoke pre-operation plugins.
        if (!invokePreOpPlugins())
        {
          return false;
        }

        if (authPolicyState.passwordMatches(simplePassword))
        {
          setResultCode(ResultCode.SUCCESS);

          if (DirectoryServer.lockdownMode()
              && (!ClientConnection.hasPrivilege(userEntry,
                  Privilege.BYPASS_LOCKDOWN)))
          {
            throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                ERR_BIND_REJECTED_LOCKDOWN_MODE.get());
          }
          setAuthenticationInfo(new AuthenticationInfo(userEntry, getBindDN(),
              simplePassword, DirectoryServer.isRootDN(userEntry.getDN())));

          // Set resource limits for the authenticated user.
          setResourceLimits(userEntry);
        }
        else
        {
          setResultCode(ResultCode.INVALID_CREDENTIALS);
          setAuthFailureReason(ERR_BIND_OPERATION_WRONG_PASSWORD.get());
        }
      }

      return true;
    }
    finally
    {
      // No matter what, make sure to unlock the user's entry.
      LockManager.unlock(bindDN, userLock);
    }
  }



  /**
   * Performs the processing necessary for an anonymous simple bind.
   *
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   * @throws  DirectoryException  If a problem occurs that should cause the bind
   *                              operation to fail.
   */
  protected boolean processAnonymousSimpleBind()
          throws DirectoryException
  {
    // If the server is in lockdown mode, then fail.
    if (DirectoryServer.lockdownMode())
    {
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                                   ERR_BIND_REJECTED_LOCKDOWN_MODE.get());
    }

    // If there is a bind DN, then see whether that is acceptable.
    if (DirectoryServer.bindWithDNRequiresPassword() &&
        ((bindDN != null) && (! bindDN.isNullDN())))
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   ERR_BIND_DN_BUT_NO_PASSWORD.get());
    }


    // Invoke pre-operation plugins.
    if (!invokePreOpPlugins())
    {
      return false;
    }

    setResultCode(ResultCode.SUCCESS);
    setAuthenticationInfo(new AuthenticationInfo());
    return true;
  }



  /**
   * Performs the processing necessary for a SASL bind operation.
   *
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   *
   * @throws  DirectoryException  If a problem occurs that should cause the bind
   *                              operation to fail.
   */
  private boolean processSASLBind()
          throws DirectoryException
  {
    // Get the appropriate authentication handler for this request based
    // on the SASL mechanism.  If there is none, then fail.
    SASLMechanismHandler<?> saslHandler =
         DirectoryServer.getSASLMechanismHandler(saslMechanism);
    if (saslHandler == null)
    {
      throw new DirectoryException(ResultCode.AUTH_METHOD_NOT_SUPPORTED,
                     ERR_BIND_OPERATION_UNKNOWN_SASL_MECHANISM.get(
                          saslMechanism));
    }


    // Check to see if the client has sufficient permission to perform the bind.
    // NYI


    // Invoke pre-operation plugins.
    if (!invokePreOpPlugins())
    {
      return false;
    }

    // Actually process the SASL bind.
    saslHandler.processSASLBind(this);


    // If the server is operating in lockdown mode, then we will need to
    // ensure that the authentication was successful and performed as a
    // root user to continue.
    Entry saslAuthUserEntry = getSASLAuthUserEntry();
    if (DirectoryServer.lockdownMode())
    {
      ResultCode resultCode = getResultCode();
      if (resultCode != ResultCode.SASL_BIND_IN_PROGRESS)
      {
        if ((resultCode != ResultCode.SUCCESS) ||
            (saslAuthUserEntry == null) ||
            (! ClientConnection.hasPrivilege(saslAuthUserEntry,
                Privilege.BYPASS_LOCKDOWN)))
        {
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                                       ERR_BIND_REJECTED_LOCKDOWN_MODE.get());
        }
      }
    }

    // Create the password policy state object.
    if (saslAuthUserEntry != null)
    {
      setUserEntryDN(saslAuthUserEntry.getDN());

      // FIXME -- Need to have a way to enable debugging.
      authPolicyState = AuthenticationPolicyState.forUser(
          saslAuthUserEntry, false);
      if (authPolicyState.isPasswordPolicy())
      {
        // Account is managed locally: perform password policy checks that will
        // need to be completed regardless of whether the authentication was
        // successful.
        checkPasswordPolicyState(saslAuthUserEntry, saslHandler);
      }
    }


    // Determine whether the authentication was successful and perform
    // any remaining password policy processing accordingly.
    ResultCode resultCode = getResultCode();
    if (resultCode == ResultCode.SUCCESS)
    {
      if (authPolicyState != null && authPolicyState.isPasswordPolicy())
      {
        PasswordPolicyState pwPolicyState =
          (PasswordPolicyState) authPolicyState;

        if (saslHandler.isPasswordBased(saslMechanism) &&
            pwPolicyState.mustChangePassword())
        {
          mustChangePassword = true;
        }

        if (isFirstWarning)
        {
          pwPolicyState.setWarnedTime();

          int numSeconds = pwPolicyState.getSecondsUntilExpiration();
          Message m = WARN_BIND_PASSWORD_EXPIRING.get(
                                 secondsToTimeString(numSeconds));

          pwPolicyState.generateAccountStatusNotification(
               AccountStatusNotificationType.PASSWORD_EXPIRING,
               saslAuthUserEntry, m,
               AccountStatusNotification.createProperties(pwPolicyState,
                     false, numSeconds, null, null));
        }

        if (isGraceLogin)
        {
          pwPolicyState.updateGraceLoginTimes();
        }

        pwPolicyState.setLastLoginTime();
      }

      // Set appropriate resource limits for the user (note that SASL ANONYMOUS
      // does not have a user).
      if (saslAuthUserEntry != null)
      {
        setResourceLimits(saslAuthUserEntry);
      }
    }
    else if (resultCode == ResultCode.SASL_BIND_IN_PROGRESS)
    {
      // FIXME -- Is any special processing needed here?
      return false;
    }
    else
    {
      if (authPolicyState != null && authPolicyState.isPasswordPolicy())
      {
        PasswordPolicyState pwPolicyState =
          (PasswordPolicyState) authPolicyState;

        if (saslHandler.isPasswordBased(saslMechanism))
        {
          if (pwPolicyState.getAuthenticationPolicy()
              .getLockoutFailureCount() > 0)
          {
            generateAccountStatusNotificationForLockedBindAccount(
                saslAuthUserEntry, pwPolicyState);
          }
        }
      }
    }

    return true;
  }



  private void generateAccountStatusNotificationForLockedBindAccount(
      Entry userEntry, PasswordPolicyState pwPolicyState)
  {
    pwPolicyState.updateAuthFailureTimes();
    if (pwPolicyState.lockedDueToFailures())
    {
      AccountStatusNotificationType notificationType;
      boolean tempLocked;
      Message m;

      int lockoutDuration = pwPolicyState.getSecondsUntilUnlock();
      if (lockoutDuration > -1)
      {
        notificationType =
            AccountStatusNotificationType.ACCOUNT_TEMPORARILY_LOCKED;
        tempLocked = true;
        m =
            ERR_BIND_ACCOUNT_TEMPORARILY_LOCKED
                .get(secondsToTimeString(lockoutDuration));
      }
      else
      {
        notificationType =
            AccountStatusNotificationType.ACCOUNT_PERMANENTLY_LOCKED;
        tempLocked = false;
        m = ERR_BIND_ACCOUNT_PERMANENTLY_LOCKED.get();
      }

      pwPolicyState.generateAccountStatusNotification(notificationType,
          userEntry, m, AccountStatusNotification.createProperties(
              pwPolicyState, tempLocked, -1, null, null));
    }
  }


  private boolean invokePreOpPlugins()
  {
    executePostOpPlugins = true;
    PluginResult.PreOperation preOpResult = pluginConfigManager
        .invokePreOperationBindPlugins(this);
    if (!preOpResult.continueProcessing())
    {
      setResultCode(preOpResult.getResultCode());
      appendErrorMessage(preOpResult.getErrorMessage());
      setMatchedDN(preOpResult.getMatchedDN());
      setReferralURLs(preOpResult.getReferralURLs());
      return false;
    }
    else
    {
      return true;
    }
  }



  /**
   * Validates a number of password policy state constraints for the user.
   *
   * @param userEntry
   *          The entry for the user that is authenticating.
   * @param saslHandler
   *          The SASL mechanism handler if this is a SASL bind, or {@code null}
   *          for a simple bind.
   * @throws DirectoryException
   *           If a problem occurs that should cause the bind to fail.
   */
  protected void checkPasswordPolicyState(
      Entry userEntry, SASLMechanismHandler<?> saslHandler)
      throws DirectoryException
  {
    PasswordPolicyState pwPolicyState = (PasswordPolicyState) authPolicyState;
    PasswordPolicy policy = pwPolicyState.getAuthenticationPolicy();

    boolean isSASLBind = (saslHandler != null);

    // If the password policy is configured to track authentication failures or
    // keep the last login time and the associated backend is disabled, then we
    // may need to reject the bind immediately.
    if ((policy.getStateUpdateFailurePolicy() ==
         PasswordPolicyCfgDefn.StateUpdateFailurePolicy.PROACTIVE) &&
        ((policy.getLockoutFailureCount() > 0) ||
         ((policy.getLastLoginTimeAttribute() != null) &&
          (policy.getLastLoginTimeFormat() != null))) &&
        ((DirectoryServer.getWritabilityMode() == WritabilityMode.DISABLED) ||
         (backend.getWritabilityMode() == WritabilityMode.DISABLED)))
    {
      // This policy isn't applicable to root users, so if it's a root
      // user then ignore it.
      if (! DirectoryServer.isRootDN(userEntry.getDN()))
      {
        throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                       ERR_BIND_OPERATION_WRITABILITY_DISABLED.get(
                            String.valueOf(userEntry.getDN())));
      }
    }


    // Check to see if the authentication must be done in a secure
    // manner.  If so, then the client connection must be secure.
    if (policy.isRequireSecureAuthentication()
        && (!clientConnection.isSecure()))
    {
      if (isSASLBind)
      {
        if (! saslHandler.isSecure(saslMechanism))
        {
          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                         ERR_BIND_OPERATION_INSECURE_SASL_BIND.get(
                              saslMechanism,
                              String.valueOf(userEntry.getDN())));
        }
      }
      else
      {
        throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                       ERR_BIND_OPERATION_INSECURE_SIMPLE_BIND.get());
      }
    }


    // Check to see if the user is administratively disabled or locked.
    if (pwPolicyState.isDisabled())
    {
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                                   ERR_BIND_OPERATION_ACCOUNT_DISABLED.get());
    }
    else if (pwPolicyState.isAccountExpired())
    {
      Message m = ERR_BIND_OPERATION_ACCOUNT_EXPIRED.get();
      pwPolicyState.generateAccountStatusNotification(
           AccountStatusNotificationType.ACCOUNT_EXPIRED, userEntry, m,
           AccountStatusNotification.createProperties(pwPolicyState,
                 false, -1, null, null));

      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, m);
    }
    else if (pwPolicyState.lockedDueToFailures())
    {
      if (pwPolicyErrorType == null)
      {
        pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
      }

      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS,
                     ERR_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED.get());
    }
    else if (pwPolicyState.lockedDueToIdleInterval())
    {
      if (pwPolicyErrorType == null)
      {
        pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
      }

      Message m = ERR_BIND_OPERATION_ACCOUNT_IDLE_LOCKED.get();
      pwPolicyState.generateAccountStatusNotification(
           AccountStatusNotificationType.ACCOUNT_IDLE_LOCKED, userEntry, m,
           AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                                                      null, null));

      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, m);
    }


    // If it's a simple bind, or if it's a password-based SASL bind, then
    // perform a number of password-based checks.
    if ((! isSASLBind) || saslHandler.isPasswordBased(saslMechanism))
    {
      // Check to see if the account is locked due to the maximum reset age.
      if (pwPolicyState.lockedDueToMaximumResetAge())
      {
        if (pwPolicyErrorType == null)
        {
          pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
        }

        Message m = ERR_BIND_OPERATION_ACCOUNT_RESET_LOCKED.get();
        pwPolicyState.generateAccountStatusNotification(
             AccountStatusNotificationType.ACCOUNT_RESET_LOCKED, userEntry, m,
             AccountStatusNotification.createProperties(pwPolicyState, false,
                                                        -1, null, null));

        throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, m);
      }


      // Determine whether the password is expired, or whether the user
      // should be warned about an upcoming expiration.
      if (pwPolicyState.isPasswordExpired())
      {
        if (pwPolicyErrorType == null)
        {
          pwPolicyErrorType = PasswordPolicyErrorType.PASSWORD_EXPIRED;
        }

        int maxGraceLogins = policy.getGraceLoginCount();
        if ((maxGraceLogins > 0) && pwPolicyState.mayUseGraceLogin())
        {
          List<Long> graceLoginTimes = pwPolicyState.getGraceLoginTimes();
          if ((graceLoginTimes == null) ||
              (graceLoginTimes.size() < maxGraceLogins))
          {
            isGraceLogin       = true;
            mustChangePassword = true;

            if (pwPolicyWarningType == null)
            {
              pwPolicyWarningType =
                   PasswordPolicyWarningType.GRACE_LOGINS_REMAINING;
              pwPolicyWarningValue = maxGraceLogins -
                                     (graceLoginTimes.size() + 1);
            }
          }
          else
          {
            Message m = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get();

            pwPolicyState.generateAccountStatusNotification(
                 AccountStatusNotificationType.PASSWORD_EXPIRED, userEntry, m,
                 AccountStatusNotification.createProperties(pwPolicyState,
                                                            false, -1, null,
                                                            null));

            throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, m);
          }
        }
        else
        {
          Message m = ERR_BIND_OPERATION_PASSWORD_EXPIRED.get();

          pwPolicyState.generateAccountStatusNotification(
               AccountStatusNotificationType.PASSWORD_EXPIRED, userEntry, m,
               AccountStatusNotification.createProperties(pwPolicyState, false,
                                                          -1, null, null));

          throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, m);
        }
      }
      else if (pwPolicyState.shouldWarn())
      {
        int numSeconds = pwPolicyState.getSecondsUntilExpiration();

        if (pwPolicyWarningType == null)
        {
          pwPolicyWarningType =
               PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
          pwPolicyWarningValue = numSeconds;
        }

        isFirstWarning = pwPolicyState.isFirstWarning();
      }


      // Check to see if the user's password has been reset.
      if (pwPolicyState.mustChangePassword())
      {
        mustChangePassword = true;

        if (pwPolicyErrorType == null)
        {
          pwPolicyErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
        }
      }
    }
  }



  /**
   * Sets resource limits for the authenticated user.
   *
   * @param  userEntry  The entry for the authenticated user.
   */
  protected void setResourceLimits(Entry userEntry)
  {
    // See if the user's entry contains a custom size limit.
    Integer customSizeLimit =
        getIntegerUserAttribute(userEntry, OP_ATTR_USER_SIZE_LIMIT,
            WARN_BIND_MULTIPLE_USER_SIZE_LIMITS,
            WARN_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT);
    if (customSizeLimit != null)
    {
      sizeLimit = customSizeLimit;
    }

    // See if the user's entry contains a custom time limit.
    Integer customTimeLimit =
        getIntegerUserAttribute(userEntry, OP_ATTR_USER_TIME_LIMIT,
            WARN_BIND_MULTIPLE_USER_TIME_LIMITS,
            WARN_BIND_CANNOT_PROCESS_USER_TIME_LIMIT);
    if (customTimeLimit != null)
    {
      timeLimit = customTimeLimit;
    }

    // See if the user's entry contains a custom idle time limit.
    // idleTimeLimit = 1000L * Long.parseLong(v.getValue().toString());
    Integer customIdleTimeLimitInSec =
        getIntegerUserAttribute(userEntry, OP_ATTR_USER_IDLE_TIME_LIMIT,
            WARN_BIND_MULTIPLE_USER_IDLE_TIME_LIMITS,
            WARN_BIND_CANNOT_PROCESS_USER_IDLE_TIME_LIMIT);
    if (customIdleTimeLimitInSec != null)
    {
      idleTimeLimit = 1000L * customIdleTimeLimitInSec;
    }

    // See if the user's entry contains a custom lookthrough limit.
    Integer customLookthroughLimit =
        getIntegerUserAttribute(userEntry, OP_ATTR_USER_LOOKTHROUGH_LIMIT,
            WARN_BIND_MULTIPLE_USER_LOOKTHROUGH_LIMITS,
            WARN_BIND_CANNOT_PROCESS_USER_LOOKTHROUGH_LIMIT);
    if (customLookthroughLimit != null)
    {
      lookthroughLimit = customLookthroughLimit;
    }
  }

  private Integer getIntegerUserAttribute(Entry userEntry,
      String attributeTypeName,
      Arg1<CharSequence> nonUniqueAttributeMessage,
      Arg2<CharSequence, CharSequence> cannotProcessAttributeMessage)
  {
    AttributeType attrType =
        DirectoryServer.getAttributeType(attributeTypeName, true);
    List<Attribute> attrList = userEntry.getAttribute(attrType);
    if ((attrList != null) && (attrList.size() == 1))
    {
      Attribute a = attrList.get(0);
      if (a.size() == 1)
      {
        AttributeValue v = a.iterator().next();
        try
        {
          return Integer.valueOf(v.getValue().toString());
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          logError(cannotProcessAttributeMessage.get(v.getValue().toString(),
              String.valueOf(userEntry.getDN())));
        }
      }
      else if (a.size() > 1)
      {
        logError(nonUniqueAttributeMessage.get(String
            .valueOf(userEntry.getDN())));
      }
    }
    return null;
  }
}
