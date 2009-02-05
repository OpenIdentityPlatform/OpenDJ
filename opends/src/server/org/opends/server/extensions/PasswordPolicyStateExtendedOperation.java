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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.
            PasswordPolicyStateExtendedOperationHandlerCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.types.*;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an LDAP extended operation that can be used to query
 * and update elements of the Directory Server password policy state for a given
 * user.  The ASN.1 definition for the value of the extended request is:
 * <BR>
 * <PRE>
 * PasswordPolicyStateValue ::= SEQUENCE {
 *      targetUser     LDAPDN
 *      operations     SEQUENCE OF PasswordPolicyStateOperation OPTIONAL }
 *
 * PasswordPolicyStateOperation ::= SEQUENCE {
 *      opType       ENUMERATED {
 *           getPasswordPolicyDN                          (0),
 *           getAccountDisabledState                      (1),
 *           setAccountDisabledState                      (2),
 *           clearAccountDisabledState                    (3),
 *           getAccountExpirationTime                     (4),
 *           setAccountExpirationTime                     (5),
 *           clearAccountExpirationTime                   (6),
 *           getSecondsUntilAccountExpiration             (7),
 *           getPasswordChangedTime                       (8),
 *           setPasswordChangedTime                       (9),
 *           clearPasswordChangedTime                     (10),
 *           getPasswordExpirationWarnedTime              (11),
 *           setPasswordExpirationWarnedTime              (12),
 *           clearPasswordExpirationWarnedTime            (13),
 *           getSecondsUntilPasswordExpiration            (14),
 *           getSecondsUntilPasswordExpirationWarning     (15),
 *           getAuthenticationFailureTimes                (16),
 *           addAuthenticationFailureTime                 (17),
 *           setAuthenticationFailureTimes                (18),
 *           clearAuthenticationFailureTimes              (19),
 *           getSecondsUntilAuthenticationFailureUnlock   (20),
 *           getRemainingAuthenticationFailureCount       (21),
 *           getLastLoginTime                             (22),
 *           setLastLoginTime                             (23),
 *           clearLastLoginTime                           (24),
 *           getSecondsUntilIdleLockout                   (25),
 *           getPasswordResetState                        (26),
 *           setPasswordResetState                        (27),
 *           clearPasswordResetState                      (28),
 *           getSecondsUntilPasswordResetLockout          (29),
 *           getGraceLoginUseTimes                        (30),
 *           addGraceLoginUseTime                         (31),
 *           setGraceLoginUseTimes                        (32),
 *           clearGraceLoginUseTimes                      (33),
 *           getRemainingGraceLoginCount                  (34),
 *           getPasswordChangedByRequiredTime             (35),
 *           setPasswordChangedByRequiredTime             (36),
 *           clearPasswordChangedByRequiredTime           (37),
 *           getSecondsUntilRequiredChangeTime            (38),
 *           getPasswordHistory                           (39),
 *           clearPasswordHistory                         (40),
 *           ... },
 *      opValues     SEQUENCE OF OCTET STRING OPTIONAL }
 * </PRE>
 * <BR>
 * Both the request and response values use the same encoded form, and they both
 * use the same OID of "1.3.6.1.4.1.26027.1.6.1".  The response value will only
 * include get* elements.  If the request did not include any operations, then
 * the response will include all get* elements; otherwise, the response will
 * only include the get* elements that correspond to the state fields referenced
 * in the request (regardless of whether that operation was included in a get*,
 * set*, add*, remove*, or clear* operation).
 */
public class PasswordPolicyStateExtendedOperation
       extends ExtendedOperationHandler<
                    PasswordPolicyStateExtendedOperationHandlerCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The enumerated value for the getPasswordPolicyDN operation.
   */
  public static final int OP_GET_PASSWORD_POLICY_DN = 0;



  /**
   * The enumerated value for the getAccountDisabledState operation.
   */
  public static final int OP_GET_ACCOUNT_DISABLED_STATE = 1;



  /**
   * The enumerated value for the setAccountDisabledState operation.
   */
  public static final int OP_SET_ACCOUNT_DISABLED_STATE = 2;



  /**
   * The enumerated value for the clearAccountDisabledState operation.
   */
  public static final int OP_CLEAR_ACCOUNT_DISABLED_STATE = 3;



  /**
   * The enumerated value for the getAccountExpirationTime operation.
   */
  public static final int OP_GET_ACCOUNT_EXPIRATION_TIME = 4;



  /**
   * The enumerated value for the setAccountExpirationTime operation.
   */
  public static final int OP_SET_ACCOUNT_EXPIRATION_TIME = 5;



  /**
   * The enumerated value for the clearAccountExpirationTime operation.
   */
  public static final int OP_CLEAR_ACCOUNT_EXPIRATION_TIME = 6;



  /**
   * The enumerated value for the getSecondsUntilAccountExpiration operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION = 7;



  /**
   * The enumerated value for the getPasswordChangedTime operation.
   */
  public static final int OP_GET_PASSWORD_CHANGED_TIME = 8;



  /**
   * The enumerated value for the setPasswordChangedTime operation.
   */
  public static final int OP_SET_PASSWORD_CHANGED_TIME = 9;



  /**
   * The enumerated value for the clearPasswordChangedTime operation.
   */
  public static final int OP_CLEAR_PASSWORD_CHANGED_TIME = 10;



  /**
   * The enumerated value for the getPasswordExpirationWarnedTime operation.
   */
  public static final int OP_GET_PASSWORD_EXPIRATION_WARNED_TIME = 11;



  /**
   * The enumerated value for the setPasswordExpirationWarnedTime operation.
   */
  public static final int OP_SET_PASSWORD_EXPIRATION_WARNED_TIME = 12;



  /**
   * The enumerated value for the clearPasswordExpirationWarnedTime operation.
   */
  public static final int OP_CLEAR_PASSWORD_EXPIRATION_WARNED_TIME = 13;



  /**
   * The enumerated value for the getSecondsUntilPasswordExpiration operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION = 14;



  /**
   * The enumerated value for the getSecondsUntilPasswordExpirationWarning
   * operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING = 15;



  /**
   * The enumerated value for the getAuthenticationFailureTimes operation.
   */
  public static final int OP_GET_AUTHENTICATION_FAILURE_TIMES = 16;



  /**
   * The enumerated value for the addAuthenticationFailureTime operation.
   */
  public static final int OP_ADD_AUTHENTICATION_FAILURE_TIME = 17;



  /**
   * The enumerated value for the setAuthenticationFailureTimes operation.
   */
  public static final int OP_SET_AUTHENTICATION_FAILURE_TIMES = 18;



  /**
   * The enumerated value for the clearAuthenticationFailureTimes operation.
   */
  public static final int OP_CLEAR_AUTHENTICATION_FAILURE_TIMES = 19;



  /**
   * The enumerated value for the getSecondsUntilAuthenticationFailureUnlock
   * operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK =
       20;



  /**
   * The enumerated value for the getRemainingAuthenticationFailureCount
   * operation.
   */
  public static final int OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT = 21;



  /**
   * The enumerated value for the getLastLoginTime operation.
   */
  public static final int OP_GET_LAST_LOGIN_TIME = 22;



  /**
   * The enumerated value for the setLastLoginTime operation.
   */
  public static final int OP_SET_LAST_LOGIN_TIME = 23;



  /**
   * The enumerated value for the clearLastLoginTime operation.
   */
  public static final int OP_CLEAR_LAST_LOGIN_TIME = 24;



  /**
   * The enumerated value for the getSecondsUntilIdleLockout operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT = 25;



  /**
   * The enumerated value for the getPasswordResetState operation.
   */
  public static final int OP_GET_PASSWORD_RESET_STATE = 26;



  /**
   * The enumerated value for the setPasswordResetState operation.
   */
  public static final int OP_SET_PASSWORD_RESET_STATE = 27;



  /**
   * The enumerated value for the clearPasswordResetState operation.
   */
  public static final int OP_CLEAR_PASSWORD_RESET_STATE = 28;



  /**
   * The enumerated value for the getSecondsUntilPasswordResetLockout operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT = 29;



  /**
   * The enumerated value for the getGraceLoginUseTimes operation.
   */
  public static final int OP_GET_GRACE_LOGIN_USE_TIMES = 30;



  /**
   * The enumerated value for the addGraceLoginUseTime operation.
   */
  public static final int OP_ADD_GRACE_LOGIN_USE_TIME = 31;



  /**
   * The enumerated value for the setGraceLoginUseTimes operation.
   */
  public static final int OP_SET_GRACE_LOGIN_USE_TIMES = 32;



  /**
   * The enumerated value for the clearGraceLoginUseTimes operation.
   */
  public static final int OP_CLEAR_GRACE_LOGIN_USE_TIMES = 33;



  /**
   * The enumerated value for the getRemainingGraceLoginCount operation.
   */
  public static final int OP_GET_REMAINING_GRACE_LOGIN_COUNT = 34;



  /**
   * The enumerated value for the getPasswordChangedByRequiredTime operation.
   */
  public static final int OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME = 35;



  /**
   * The enumerated value for the setPasswordChangedByRequiredTime operation.
   */
  public static final int OP_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME = 36;



  /**
   * The enumerated value for the clearPasswordChangedByRequiredTime operation.
   */
  public static final int OP_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME = 37;



  /**
   * The enumerated value for the getSecondsUntilRequiredChangeTime operation.
   */
  public static final int OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME = 38;



  /**
   * The enumerated value for the getPasswordHistory operation.
   */
  public static final int OP_GET_PASSWORD_HISTORY = 39;



  /**
   * The enumerated value for the clearPasswordHistory operation.
   */
  public static final int OP_CLEAR_PASSWORD_HISTORY = 40;



  // The set of attributes to request when retrieving a user's entry.
  private LinkedHashSet<String> requestAttributes;

  // The search filter that will be used to retrieve user entries.
  private SearchFilter userFilter;



  /**
   * Create an instance of this password policy state extended operation.  All
   * initialization should be performed in the
   * {@code initializeExtendedOperationHandler} method.
   */
  public PasswordPolicyStateExtendedOperation()
  {
    super();
  }


  /**
   * Initializes this extended operation handler based on the information in the
   * provided configuration entry.  It should also register itself with the
   * Directory Server for the particular kinds of extended operations that it
   * will process.
   *
   * @param  config       The configuration that contains the information
   *                      to use to initialize this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeExtendedOperationHandler(
                   PasswordPolicyStateExtendedOperationHandlerCfg config)
         throws ConfigException, InitializationException
  {
    // Construct the filter that will be used to retrieve user entries.
    try
    {
      userFilter = SearchFilter.createFilterFromString("(objectClass=*)");
    }
    catch (Exception e)
    {
      // This should never happen.
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }


    // Construct the set of request attributes.
    requestAttributes = new LinkedHashSet<String>(2);
    requestAttributes.add("*");
    requestAttributes.add("+");


    DirectoryServer.registerSupportedExtension(OID_PASSWORD_POLICY_STATE_EXTOP,
                                               this);
  }



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    DirectoryServer.deregisterSupportedExtension(OID_CANCEL_REQUEST);
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public void processExtendedOperation(ExtendedOperation operation)
  {
    operation.setResultCode(ResultCode.UNDEFINED);


    // The user must have the password-reset privilege in order to be able to do
    // anything with this extended operation.
    ClientConnection clientConnection = operation.getClientConnection();
    if (! clientConnection.hasPrivilege(Privilege.PASSWORD_RESET, operation))
    {
      Message message = ERR_PWPSTATE_EXTOP_NO_PRIVILEGE.get();
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
      return;
    }


    // There must be a request value, and it must be a sequence.  Decode it
    // into its components.
    ByteString requestValue = operation.getRequestValue();
    if (requestValue == null)
    {
      Message message = ERR_PWPSTATE_EXTOP_NO_REQUEST_VALUE.get();
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);
      return;
    }

    ByteString dnString;
    ASN1Reader reader = ASN1.getReader(requestValue);
    try
    {
      reader.readStartSequence();
      dnString   = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_PWPSTATE_EXTOP_DECODE_FAILURE.get(getExceptionMessage(e));
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);
      return;
    }


    // Decode the DN and get the corresponding user entry.
    DN targetDN;
    try
    {
      targetDN = DN.decode(dnString);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      operation.setResponseData(de);
      return;
    }

    DN rootDN = DirectoryServer.getActualRootBindDN(targetDN);
    if (rootDN != null)
    {
      targetDN = rootDN;
    }

    Entry userEntry;
    InternalClientConnection conn =
         new InternalClientConnection(clientConnection.getAuthenticationInfo());
    InternalSearchOperation internalSearch =
         conn.processSearch(targetDN, SearchScope.BASE_OBJECT,
                            DereferencePolicy.NEVER_DEREF_ALIASES, 1, 0,
                            false, userFilter, requestAttributes, null);
    if (internalSearch.getResultCode() != ResultCode.SUCCESS)
    {
      operation.setResultCode(internalSearch.getResultCode());
      operation.setErrorMessage(internalSearch.getErrorMessage());
      operation.setMatchedDN(internalSearch.getMatchedDN());
      operation.setReferralURLs(internalSearch.getReferralURLs());
      return;
    }

    List<SearchResultEntry> matchingEntries = internalSearch.getSearchEntries();
    if (matchingEntries.isEmpty())
    {
      operation.setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
      return;
    }
    else if (matchingEntries.size() > 1)
    {
      Message message = ERR_PWPSTATE_EXTOP_MULTIPLE_ENTRIES.get(
              String.valueOf(targetDN));
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      return;
    }
    else
    {
      userEntry = matchingEntries.get(0);
    }


    // Get the password policy state for the user entry.
    PasswordPolicyState pwpState;
    PasswordPolicy      policy;
    try
    {
      pwpState = new PasswordPolicyState(userEntry, false);
      policy   = pwpState.getPolicy();
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      operation.setResponseData(de);
      return;
    }


    // Create a hash set that will be used to hold the types of the return
    // types that should be included in the response.
    boolean returnAll;
    LinkedHashSet<Integer> returnTypes = new LinkedHashSet<Integer>();
    try
    {
      if (!reader.hasNextElement())
      {
        // There is no operations sequence.
        returnAll = true;
      }
      else if(reader.peekLength() <= 0)
      {
        // There is an operations sequence but its empty.
        returnAll = true;
        reader.readStartSequence();
        reader.readEndSequence();
      }
      else
      {
        returnAll = false;
        reader.readStartSequence();
        while(reader.hasNextElement())
        {
          int opType;
          ArrayList<String> opValues;

          reader.readStartSequence();
          opType = (int)reader.readInteger();

          if (!reader.hasNextElement())
          {
            // There is no values sequence
            opValues = null;
          }
          else if(reader.peekLength() <= 0)
          {
            // There is a values sequence but its empty
            opValues = null;
            reader.readStartSequence();
            reader.readEndSequence();
          }
          else
          {
            reader.readStartSequence();
            opValues = new ArrayList<String>();
            while (reader.hasNextElement())
            {
              opValues.add(reader.readOctetStringAsString());
            }
            reader.readEndSequence();
          }
          reader.readEndSequence();

          if(!processOp(opType, opValues, operation,
              returnTypes, pwpState, policy))
          {
            return;
          }
        }
        reader.readEndSequence();
      }
      reader.readEndSequence();


      // If there are any modifications that need to be made to the password
      // policy state, then apply them now.
      List<Modification> stateMods = pwpState.getModifications();
      if ((stateMods != null) && (! stateMods.isEmpty()))
      {
        ModifyOperation modifyOperation =
            conn.processModify(targetDN, stateMods);
        if (modifyOperation.getResultCode() != ResultCode.SUCCESS)
        {
          operation.setResultCode(modifyOperation.getResultCode());
          operation.setErrorMessage(modifyOperation.getErrorMessage());
          operation.setMatchedDN(modifyOperation.getMatchedDN());
          operation.setReferralURLs(modifyOperation.getReferralURLs());
          return;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_PWPSTATE_EXTOP_INVALID_OP_ENCODING.get(
          e.getLocalizedMessage());
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);
      return;
    }


    try
    {
      // Construct the sequence of values to return.
      ByteString responseValue =
          encodeResponse(dnString, returnAll, returnTypes, pwpState, policy);
      operation.setResponseOID(OID_PASSWORD_POLICY_STATE_EXTOP);
      operation.setResponseValue(responseValue);
      operation.setResultCode(ResultCode.SUCCESS);
    }
    catch(Exception e)
    {
      // TODO: Need a better message
      Message message = ERR_PWPSTATE_EXTOP_INVALID_OP_ENCODING.get(
          e.getLocalizedMessage());
      operation.appendErrorMessage(message);
      operation.setResultCode(ResultCode.PROTOCOL_ERROR);
    }
  }



  /**
   * Encodes the provided information in a form suitable for including in the
   * response value.
   *
   * @param  writer  The ASN1Writer to use to encode.
   * @param  opType  The operation type to use for the value.
   * @param  value   The single value to include in the response.
   *
   * @throws IOException if an error occurs while encoding.
   */
  public static void encode(ASN1Writer writer, int opType, String value)
      throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(opType);

    if ((value != null))
    {
      writer.writeStartSequence();
      writer.writeOctetString(value);
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }



  /**
   * Encodes the provided information in a form suitable for including in the
   * response value.
   *
   * @param  writer  The ASN1Writer to use to encode.
   * @param  opType  The operation type to use for the value.
   * @param  values  The set of string values to include in the response.
   *
   * @throws IOException if an error occurs while encoding.
   */
  public static void encode(ASN1Writer writer, int opType, String[] values)
      throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(opType);

    if ((values != null) && (values.length > 0))
    {
      writer.writeStartSequence();
      for (int i=0; i < values.length; i++)
      {
        writer.writeOctetString(values[i]);
      }
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }

  /**
   * Encodes the provided information in a form suitable for including in the
   * response value.
   *
   * @param  writer  The ASN1Writer to use to encode.
   * @param  opType  The operation type to use for the value.
   * @param  values  The set of timestamp values to include in the response.
   *
   * @throws IOException if an error occurs while encoding.
   */
  public static void encode(ASN1Writer writer, int opType, List<Long> values)
      throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(opType);

    if ((values != null) && (values.size() > 0))
    {
      writer.writeStartSequence();
      for (long l : values)
      {
        writer.writeOctetString(GeneralizedTimeSyntax.format(l));
      }
      writer.writeEndSequence();
    }

    writer.writeEndSequence();
  }

  private ByteString encodeResponse(ByteString dnString, boolean returnAll,
                                    LinkedHashSet<Integer> returnTypes,
                                    PasswordPolicyState pwpState,
                                    PasswordPolicy policy)
      throws IOException
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeOctetString(dnString);

    writer.writeStartSequence();
    if (returnAll || returnTypes.contains(OP_GET_PASSWORD_POLICY_DN))
    {
      encode(writer, OP_GET_PASSWORD_POLICY_DN,
                            policy.getConfigEntryDN().toString());
    }

    if (returnAll || returnTypes.contains(OP_GET_ACCOUNT_DISABLED_STATE))
    {
      encode(writer, OP_GET_ACCOUNT_DISABLED_STATE,
                            String.valueOf(pwpState.isDisabled()));
    }

    if (returnAll || returnTypes.contains(OP_GET_ACCOUNT_EXPIRATION_TIME))
    {
      String expTimeStr;
      long expTime = pwpState.getAccountExpirationTime();
      if (expTime < 0)
      {
        expTimeStr = null;
      }
      else
      {
        expTimeStr = GeneralizedTimeSyntax.format(expTime);
      }

      encode(writer, OP_GET_ACCOUNT_EXPIRATION_TIME, expTimeStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION))
    {
      String secondsStr;
      long expTime = pwpState.getAccountExpirationTime();
      if (expTime < 0)
      {
        secondsStr = null;
      }
      else
      {
        secondsStr =
             String.valueOf((expTime - pwpState.getCurrentTime()) / 1000);
      }

      encode(writer, OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION,
                            secondsStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_PASSWORD_CHANGED_TIME))
    {
      String timeStr;
      long changedTime = pwpState.getPasswordChangedTime();
      if (changedTime < 0)
      {
        timeStr = null;
      }
      else
      {
        timeStr = GeneralizedTimeSyntax.format(changedTime);
      }

      encode(writer, OP_GET_PASSWORD_CHANGED_TIME, timeStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_PASSWORD_EXPIRATION_WARNED_TIME))
    {
      String timeStr;
      long warnedTime = pwpState.getWarnedTime();
      if (warnedTime < 0)
      {
        timeStr = null;
      }
      else
      {
        timeStr = GeneralizedTimeSyntax.format(warnedTime);
      }

      encode(writer, OP_GET_PASSWORD_EXPIRATION_WARNED_TIME, timeStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION))
    {
      String secondsStr;
      int secondsUntilExp = pwpState.getSecondsUntilExpiration();
      if (secondsUntilExp < 0)
      {
        secondsStr = null;
      }
      else
      {
        secondsStr = String.valueOf(secondsUntilExp);
      }

      encode(writer, OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION,
                            secondsStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING))
    {
      String secondsStr;
      int secondsUntilExp = pwpState.getSecondsUntilExpiration();
      if (secondsUntilExp < 0)
      {
        secondsStr = null;
      }
      else
      {
        int secondsUntilWarning = secondsUntilExp - policy.getWarningInterval();
        if (secondsUntilWarning <= 0)
        {
          secondsStr = "0";
        }
        else
        {
          secondsStr = String.valueOf(secondsUntilWarning);
        }
      }

      encode(writer, OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING,
                            secondsStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_AUTHENTICATION_FAILURE_TIMES))
    {
      encode(writer, OP_GET_AUTHENTICATION_FAILURE_TIMES,
                            pwpState.getAuthFailureTimes());
    }

    if (returnAll || returnTypes.contains(
                          OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK))
    {
      // We have to check whether the account is locked due to failures before
      // we can get the length of time until the account is unlocked.
      String secondsStr;
      if (pwpState.lockedDueToFailures())
      {
        int seconds = pwpState.getSecondsUntilUnlock();
        if (seconds <= 0)
        {
          secondsStr = null;
        }
        else
        {
          secondsStr = String.valueOf(seconds);
        }
      }
      else
      {
        secondsStr = null;
      }

      encode(writer, OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK,
                            secondsStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT))
    {
      String remainingFailuresStr;
      int allowedFailureCount = policy.getLockoutFailureCount();
      if (allowedFailureCount > 0)
      {
        int remainingFailures =
                 allowedFailureCount - pwpState.getAuthFailureTimes().size();
        if (remainingFailures < 0)
        {
          remainingFailures = 0;
        }

        remainingFailuresStr = String.valueOf(remainingFailures);
      }
      else
      {
        remainingFailuresStr = null;
      }

      encode(writer, OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT,
                            remainingFailuresStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_LAST_LOGIN_TIME))
    {
      String timeStr;
      long lastLoginTime = pwpState.getLastLoginTime();
      if (lastLoginTime < 0)
      {
        timeStr = null;
      }
      else
      {
        timeStr = GeneralizedTimeSyntax.format(lastLoginTime);
      }

      encode(writer, OP_GET_LAST_LOGIN_TIME, timeStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT))
    {
      String secondsStr;
      int lockoutInterval = policy.getIdleLockoutInterval();
      if (lockoutInterval > 0)
      {
        long lastLoginTime = pwpState.getLastLoginTime();
        if (lastLoginTime < 0)
        {
          secondsStr = "0";
        }
        else
        {
          long lockoutTime = lastLoginTime + (lockoutInterval*1000);
          long currentTime = pwpState.getCurrentTime();
          int secondsUntilLockout = (int) ((lockoutTime - currentTime) / 1000);
          if (secondsUntilLockout <= 0)
          {
            secondsStr = "0";
          }
          else
          {
            secondsStr = String.valueOf(secondsUntilLockout);
          }
        }
      }
      else
      {
        secondsStr = null;
      }

      encode(writer, OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT, secondsStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_PASSWORD_RESET_STATE))
    {
      encode(writer, OP_GET_PASSWORD_RESET_STATE,
                            String.valueOf(pwpState.mustChangePassword()));
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT))
    {
      String secondsStr;
      if (pwpState.mustChangePassword())
      {
        int maxAge = policy.getMaximumPasswordResetAge();
        if (maxAge > 0)
        {
          long currentTime = pwpState.getCurrentTime();
          long changedTime = pwpState.getPasswordChangedTime();
          int changeAge = (int) ((currentTime - changedTime) / 1000);
          int timeToLockout = maxAge - changeAge;
          if (timeToLockout <= 0)
          {
            secondsStr = "0";
          }
          else
          {
            secondsStr = String.valueOf(timeToLockout);
          }
        }
        else
        {
          secondsStr = null;
        }
      }
      else
      {
        secondsStr = null;
      }

      encode(writer, OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT,
                            secondsStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_GRACE_LOGIN_USE_TIMES))
    {
      encode(writer, OP_GET_GRACE_LOGIN_USE_TIMES,
                            pwpState.getGraceLoginTimes());
    }

    if (returnAll || returnTypes.contains(OP_GET_REMAINING_GRACE_LOGIN_COUNT))
    {
      String remainingStr;
      int remainingGraceLogins = pwpState.getGraceLoginsRemaining();
      if (remainingGraceLogins <= 0)
      {
        remainingStr = "0";
      }
      else
      {
        remainingStr = String.valueOf(remainingGraceLogins);
      }

      encode(writer, OP_GET_REMAINING_GRACE_LOGIN_COUNT, remainingStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME))
    {
      String timeStr;
      long requiredChangeTime = pwpState.getRequiredChangeTime();
      if (requiredChangeTime < 0)
      {
        timeStr = null;
      }
      else
      {
        timeStr = GeneralizedTimeSyntax.format(requiredChangeTime);
      }

      encode(writer, OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME, timeStr);
    }

    if (returnAll ||
        returnTypes.contains(OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME))
    {
      String secondsStr;
      long policyRequiredChangeTime = policy.getRequireChangeByTime();
      if (policyRequiredChangeTime > 0)
      {
        long accountRequiredChangeTime = pwpState.getRequiredChangeTime();
        if (accountRequiredChangeTime >= policyRequiredChangeTime)
        {
          secondsStr = null;
        }
        else
        {
          long currentTime = pwpState.getCurrentTime();
          if (currentTime >= policyRequiredChangeTime)
          {
            secondsStr = "0";
          }
          else
          {
            secondsStr =
                 String.valueOf((policyRequiredChangeTime-currentTime) / 1000);

          }
        }
      }
      else
      {
        secondsStr = null;
      }

      encode(writer, OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME,
                            secondsStr);
    }

    if (returnAll || returnTypes.contains(OP_GET_PASSWORD_HISTORY))
    {
      encode(writer, OP_GET_PASSWORD_HISTORY,
                            pwpState.getPasswordHistoryValues());
    }
    writer.writeEndSequence();

    writer.writeEndSequence();

    return builder.toByteString();
  }

  private boolean processOp(int opType, ArrayList<String> opValues,
                         ExtendedOperation operation,
                         LinkedHashSet<Integer> returnTypes,
                         PasswordPolicyState pwpState,
                         PasswordPolicy policy)
  {
    switch (opType)
    {
      case OP_GET_PASSWORD_POLICY_DN:
        returnTypes.add(OP_GET_PASSWORD_POLICY_DN);
        break;

      case OP_GET_ACCOUNT_DISABLED_STATE:
        returnTypes.add(OP_GET_ACCOUNT_DISABLED_STATE);
        break;

      case OP_SET_ACCOUNT_DISABLED_STATE:
        if (opValues == null)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_NO_DISABLED_VALUE.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_DISABLED_VALUE_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          String value = opValues.get(0);
          if (value.equalsIgnoreCase("true"))
          {
            pwpState.setDisabled(true);
          }
          else if (value.equalsIgnoreCase("false"))
          {
            pwpState.setDisabled(false);
          }
          else
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_DISABLED_VALUE.get());
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_ACCOUNT_DISABLED_STATE);
        break;

      case OP_CLEAR_ACCOUNT_DISABLED_STATE:
        pwpState.setDisabled(false);
        returnTypes.add(OP_GET_ACCOUNT_DISABLED_STATE);
        break;

      case OP_GET_ACCOUNT_EXPIRATION_TIME:
        returnTypes.add(OP_GET_ACCOUNT_EXPIRATION_TIME);
        break;

      case OP_SET_ACCOUNT_EXPIRATION_TIME:
        if (opValues == null)
        {
          pwpState.setAccountExpirationTime(pwpState.getCurrentTime());
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_ACCT_EXP_VALUE_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            pwpState.setAccountExpirationTime(time);
          }
          catch (DirectoryException de)
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_ACCT_EXP_VALUE.get(
                    opValues.get(0),
                    de.getMessageObject()));
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_ACCOUNT_EXPIRATION_TIME);
        break;

      case OP_CLEAR_ACCOUNT_EXPIRATION_TIME:
        pwpState.clearAccountExpirationTime();
        returnTypes.add(OP_GET_ACCOUNT_EXPIRATION_TIME);
        break;

      case OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION:
        returnTypes.add(OP_GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION);
        break;

      case OP_GET_PASSWORD_CHANGED_TIME:
        returnTypes.add(OP_GET_PASSWORD_CHANGED_TIME);
        break;

      case OP_SET_PASSWORD_CHANGED_TIME:
        if (opValues == null)
        {
          pwpState.setPasswordChangedTime();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_PWCHANGETIME_VALUE_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            pwpState.setPasswordChangedTime(time);
          }
          catch (DirectoryException de)
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_PWCHANGETIME_VALUE.get(
                    opValues.get(0),
                    de.getMessageObject()));
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_PASSWORD_CHANGED_TIME);
        break;

      case OP_CLEAR_PASSWORD_CHANGED_TIME:
        pwpState.clearPasswordChangedTime();
        returnTypes.add(OP_GET_PASSWORD_CHANGED_TIME);
        break;

      case OP_GET_PASSWORD_EXPIRATION_WARNED_TIME:
        returnTypes.add(OP_GET_PASSWORD_EXPIRATION_WARNED_TIME);
        break;

      case OP_SET_PASSWORD_EXPIRATION_WARNED_TIME:
        if (opValues == null)
        {
          pwpState.setWarnedTime();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_PWWARNEDTIME_VALUE_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            pwpState.setWarnedTime(time);
          }
          catch (DirectoryException de)
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_PWWARNEDTIME_VALUE.get(
                    opValues.get(0),
                    de.getMessageObject()));
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_PASSWORD_EXPIRATION_WARNED_TIME);
        break;

      case OP_CLEAR_PASSWORD_EXPIRATION_WARNED_TIME:
        pwpState.clearWarnedTime();
        returnTypes.add(OP_GET_PASSWORD_EXPIRATION_WARNED_TIME);
        break;

      case OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION:
        returnTypes.add(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION);
        break;

      case OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING:
        returnTypes.add(OP_GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING);
        break;

      case OP_GET_AUTHENTICATION_FAILURE_TIMES:
        returnTypes.add(OP_GET_AUTHENTICATION_FAILURE_TIMES);
        break;

      case OP_ADD_AUTHENTICATION_FAILURE_TIME:
        if (opValues == null)
        {
          if (policy.getLockoutFailureCount() == 0)
          {
            returnTypes.add(OP_GET_AUTHENTICATION_FAILURE_TIMES);
            break;
          }

          pwpState.updateAuthFailureTimes();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_ADD_FAILURE_TIME_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            List<Long> authFailureTimes = pwpState.getAuthFailureTimes();
            ArrayList<Long> newFailureTimes =
                new ArrayList<Long>(authFailureTimes.size()+1);
            newFailureTimes.addAll(authFailureTimes);
            newFailureTimes.add(time);
            pwpState.setAuthFailureTimes(newFailureTimes);
          }
          catch (DirectoryException de)
          {
            Message message = ERR_PWPSTATE_EXTOP_BAD_AUTH_FAILURE_TIME.get(
                opValues.get(0),
                de.getMessageObject());
            operation.setResultCode(de.getResultCode());
            operation.appendErrorMessage(message);
            return false;
          }
        }

        returnTypes.add(OP_GET_AUTHENTICATION_FAILURE_TIMES);
        break;

      case OP_SET_AUTHENTICATION_FAILURE_TIMES:
        if (opValues == null)
        {
          ArrayList<Long> valueList = new ArrayList<Long>(1);
          valueList.add(pwpState.getCurrentTime());
          pwpState.setAuthFailureTimes(valueList);
        }
        else
        {
          ArrayList<Long> valueList = new ArrayList<Long>(opValues.size());
          for (String s : opValues)
          {
            try
            {
              valueList.add(
                  GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                      ByteString.valueOf(s)));
            }
            catch (DirectoryException de)
            {
              Message message =
                  ERR_PWPSTATE_EXTOP_BAD_AUTH_FAILURE_TIME.get(
                      s,
                      de.getMessageObject());
              operation.setResultCode(de.getResultCode());
              operation.appendErrorMessage(message);
              return false;
            }
          }
          pwpState.setAuthFailureTimes(valueList);
        }

        returnTypes.add(OP_GET_AUTHENTICATION_FAILURE_TIMES);
        break;

      case OP_CLEAR_AUTHENTICATION_FAILURE_TIMES:
        pwpState.clearFailureLockout();
        returnTypes.add(OP_GET_AUTHENTICATION_FAILURE_TIMES);
        break;

      case OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK:
        returnTypes.add(OP_GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK);
        break;

      case OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT:
        returnTypes.add(OP_GET_REMAINING_AUTHENTICATION_FAILURE_COUNT);
        break;

      case OP_GET_LAST_LOGIN_TIME:
        returnTypes.add(OP_GET_LAST_LOGIN_TIME);
        break;

      case OP_SET_LAST_LOGIN_TIME:
        if (opValues == null)
        {
          pwpState.setLastLoginTime();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_LAST_LOGIN_TIME_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            pwpState.setLastLoginTime(time);
          }
          catch (DirectoryException de)
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_LAST_LOGIN_TIME.get(
                    opValues.get(0),
                    de.getMessageObject()));
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_LAST_LOGIN_TIME);
        break;

      case OP_CLEAR_LAST_LOGIN_TIME:
        pwpState.clearLastLoginTime();
        returnTypes.add(OP_GET_LAST_LOGIN_TIME);
        break;

      case OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT:
        returnTypes.add(OP_GET_SECONDS_UNTIL_IDLE_LOCKOUT);
        break;

      case OP_GET_PASSWORD_RESET_STATE:
        returnTypes.add(OP_GET_PASSWORD_RESET_STATE);
        break;

      case OP_SET_PASSWORD_RESET_STATE:
        if (opValues == null)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_NO_RESET_STATE_VALUE.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_RESET_STATE_VALUE_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          String value = opValues.get(0);
          if (value.equalsIgnoreCase("true"))
          {
            pwpState.setMustChangePassword(true);
          }
          else if (value.equalsIgnoreCase("false"))
          {
            pwpState.setMustChangePassword(false);
          }
          else
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_RESET_STATE_VALUE.get());
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_PASSWORD_RESET_STATE);
        break;

      case OP_CLEAR_PASSWORD_RESET_STATE:
        pwpState.setMustChangePassword(false);
        returnTypes.add(OP_GET_PASSWORD_RESET_STATE);
        break;

      case OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT:
        returnTypes.add(OP_GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT);
        break;

      case OP_GET_GRACE_LOGIN_USE_TIMES:
        returnTypes.add(OP_GET_GRACE_LOGIN_USE_TIMES);
        break;

      case OP_ADD_GRACE_LOGIN_USE_TIME:
        if (opValues == null)
        {
          pwpState.updateGraceLoginTimes();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_ADD_GRACE_LOGIN_TIME_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            List<Long> authFailureTimes = pwpState.getGraceLoginTimes();
            ArrayList<Long> newGraceTimes =
                new ArrayList<Long>(authFailureTimes.size()+1);
            newGraceTimes.addAll(authFailureTimes);
            newGraceTimes.add(time);
            pwpState.setGraceLoginTimes(newGraceTimes);
          }
          catch (DirectoryException de)
          {
            Message message = ERR_PWPSTATE_EXTOP_BAD_GRACE_LOGIN_TIME.get(
                opValues.get(0),
                de.getMessageObject());
            operation.setResultCode(de.getResultCode());
            operation.appendErrorMessage(message);
            return false;
          }
        }

        returnTypes.add(OP_GET_GRACE_LOGIN_USE_TIMES);
        break;

      case OP_SET_GRACE_LOGIN_USE_TIMES:
        if (opValues == null)
        {
          ArrayList<Long> valueList = new ArrayList<Long>(1);
          valueList.add(pwpState.getCurrentTime());
          pwpState.setGraceLoginTimes(valueList);
        }
        else
        {
          ArrayList<Long> valueList = new ArrayList<Long>(opValues.size());
          for (String s : opValues)
          {
            try
            {
              valueList.add(
                  GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                      ByteString.valueOf(s)));
            }
            catch (DirectoryException de)
            {
              Message message = ERR_PWPSTATE_EXTOP_BAD_GRACE_LOGIN_TIME.get(
                  s, de.getMessageObject());
              operation.setResultCode(de.getResultCode());
              operation.appendErrorMessage(message);
              return false;
            }
          }
          pwpState.setGraceLoginTimes(valueList);
        }

        returnTypes.add(OP_GET_GRACE_LOGIN_USE_TIMES);
        break;

      case OP_CLEAR_GRACE_LOGIN_USE_TIMES:
        pwpState.clearGraceLoginTimes();
        returnTypes.add(OP_GET_GRACE_LOGIN_USE_TIMES);
        break;

      case OP_GET_REMAINING_GRACE_LOGIN_COUNT:
        returnTypes.add(OP_GET_REMAINING_GRACE_LOGIN_COUNT);
        break;

      case OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME:
        returnTypes.add(OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME);
        break;

      case OP_SET_PASSWORD_CHANGED_BY_REQUIRED_TIME:
        if (opValues == null)
        {
          pwpState.setRequiredChangeTime();
        }
        else if (opValues.size() != 1)
        {
          operation.appendErrorMessage(
              ERR_PWPSTATE_EXTOP_BAD_REQUIRED_CHANGE_TIME_COUNT.get());
          operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          return false;
        }
        else
        {
          try
          {
            ByteString valueString =
                ByteString.valueOf(opValues.get(0));
            long time = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(
                valueString);
            pwpState.setRequiredChangeTime(time);
          }
          catch (DirectoryException de)
          {
            operation.appendErrorMessage(
                ERR_PWPSTATE_EXTOP_BAD_REQUIRED_CHANGE_TIME.get(
                    opValues.get(0),
                    de.getMessageObject()));
            operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            return false;
          }
        }

        returnTypes.add(OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME);
        break;

      case OP_CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME:
        pwpState.clearRequiredChangeTime();
        returnTypes.add(OP_GET_PASSWORD_CHANGED_BY_REQUIRED_TIME);
        break;

      case OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME:
        returnTypes.add(OP_GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME);
        break;

      case OP_GET_PASSWORD_HISTORY:
        returnTypes.add(OP_GET_PASSWORD_HISTORY);
        break;

      case OP_CLEAR_PASSWORD_HISTORY:
        pwpState.clearPasswordHistory();
        returnTypes.add(OP_GET_PASSWORD_HISTORY);
        break;

      default:

        operation.appendErrorMessage(ERR_PWPSTATE_EXTOP_UNKNOWN_OP_TYPE.get(
            String.valueOf(opType)));
        operation.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        return false;
    }

    return true;
  }
}

