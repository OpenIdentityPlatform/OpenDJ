package org.opends.sdk.extensions;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.opends.sdk.*;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.requests.AbstractExtendedRequest;
import org.opends.sdk.responses.AbstractExtendedResult;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements an LDAP extended operation that can be used to
 * query and update elements of the Directory Server password policy
 * state for a given user. The ASN.1 definition for the value of the
 * extended request is: <BR>
 * 
 * <PRE>
 * PasswordPolicyStateValue ::= SEQUENCE {
 *      targetUser     LDAPDN
 *      operations     SEQUENCE OF PasswordPolicyStateOperation OPTIONAL }
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
 * 
 * <BR>
 * Both the request and response values use the same encoded form, and
 * they both use the same OID of "1.3.6.1.4.1.26027.1.6.1". The response
 * value will only include get* elements. If the request did not include
 * any operations, then the response will include all get* elements;
 * otherwise, the response will only include the get* elements that
 * correspond to the state fields referenced in the request (regardless
 * of whether that operation was included in a get*, set*, add*,
 * remove*, or clear* operation).
 */
public final class PasswordPolicyStateExtendedOperation
{
  /**
   * The OID for the password policy state extended operation (both the
   * request and response types).
   */
  static final String OID_PASSWORD_POLICY_STATE_EXTOP = "1.3.6.1.4.1.26027.1.6.1";



  public interface Operation
  {
    public OperationType getOperationType();



    public Iterable<ByteString> getValues();
  }



  public static enum OperationType implements Operation
  {
    GET_PASSWORD_POLICY_DN(PASSWORD_POLICY_DN_NAME),

    GET_ACCOUNT_DISABLED_STATE(ACCOUNT_DISABLED_STATE_NAME), SET_ACCOUNT_DISABLED_STATE(
        ACCOUNT_DISABLED_STATE_NAME), CLEAR_ACCOUNT_DISABLED_STATE(
        ACCOUNT_DISABLED_STATE_NAME),

    GET_ACCOUNT_EXPIRATION_TIME(ACCOUNT_EXPIRATION_TIME_NAME), SET_ACCOUNT_EXPIRATION_TIME(
        ACCOUNT_EXPIRATION_TIME_NAME), CLEAR_ACCOUNT_EXPIRATION_TIME(
        ACCOUNT_EXPIRATION_TIME_NAME),

    GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION(
        SECONDS_UNTIL_ACCOUNT_EXPIRATION_NAME),

    GET_PASSWORD_CHANGED_TIME(PASSWORD_CHANGED_TIME_NAME), SET_PASSWORD_CHANGED_TIME(
        PASSWORD_CHANGED_TIME_NAME), CLEAR_PASSWORD_CHANGED_TIME(
        PASSWORD_CHANGED_TIME_NAME),

    GET_PASSWORD_EXPIRATION_WARNED_TIME(
        PASSWORD_EXPIRATION_WARNED_TIME_NAME), SET_PASSWORD_EXPIRATION_WARNED_TIME(
        PASSWORD_EXPIRATION_WARNED_TIME_NAME), CLEAR_PASSWORD_EXPIRATION_WARNED_TIME(
        PASSWORD_EXPIRATION_WARNED_TIME_NAME),

    GET_SECONDS_UNTIL_PASSWORD_EXPIRATION(
        SECONDS_UNTIL_PASSWORD_EXPIRATION_NAME),

    GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING(
        SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING_NAME),

    GET_AUTHENTICATION_FAILURE_TIMES(AUTHENTICATION_FAILURE_TIMES_NAME), ADD_AUTHENTICATION_FAILURE_TIMES(
        AUTHENTICATION_FAILURE_TIMES_NAME), SET_AUTHENTICATION_FAILURE_TIMES(
        AUTHENTICATION_FAILURE_TIMES_NAME), CLEAR_AUTHENTICATION_FAILURE_TIMES(
        AUTHENTICATION_FAILURE_TIMES_NAME),

    GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK(
        SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK_NAME),

    GET_REMAINING_AUTHENTICATION_FAILURE_COUNT(
        REMAINING_AUTHENTICATION_FAILURE_COUNT_NAME),

    GET_LAST_LOGIN_TIME(LAST_LOGIN_TIME_NAME), SET_LAST_LOGIN_TIME(
        LAST_LOGIN_TIME_NAME), CLEAR_LAST_LOGIN_TIME(
        LAST_LOGIN_TIME_NAME),

    GET_SECONDS_UNTIL_IDLE_LOCKOUT(SECONDS_UNTIL_IDLE_LOCKOUT_NAME),

    GET_PASSWORD_RESET_STATE(PASSWORD_RESET_STATE_NAME), SET_PASSWORD_RESET_STATE(
        PASSWORD_RESET_STATE_NAME), CLEAR_PASSWORD_RESET_STATE(
        PASSWORD_RESET_STATE_NAME),

    GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT(
        SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT_NAME),

    GET_GRACE_LOGIN_USE_TIMES(GRACE_LOGIN_USE_TIMES_NAME), ADD_GRACE_LOGIN_USE_TIME(
        GRACE_LOGIN_USE_TIMES_NAME), SET_GRACE_LOGIN_USE_TIMES(
        GRACE_LOGIN_USE_TIMES_NAME), CLEAR_GRACE_LOGIN_USE_TIMES(
        GRACE_LOGIN_USE_TIMES_NAME),

    GET_REMAINING_GRACE_LOGIN_COUNT(REMAINING_GRACE_LOGIN_COUNT_NAME),

    GET_PASSWORD_CHANGED_BY_REQUIRED_TIME(
        PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME), SET_PASSWORD_CHANGED_BY_REQUIRED_TIME(
        PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME), CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME(
        PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME),

    GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME(
        SECONDS_UNTIL_REQUIRED_CHANGE_TIME_NAME),

    GET_PASSWORD_HISTORY(PASSWORD_HISTORY_NAME), CLEAR_PASSWORD_HISTORY(
        PASSWORD_HISTORY_NAME);

    private String propertyName;



    OperationType(String propertyName)
    {
      this.propertyName = propertyName;
    }



    public OperationType getOperationType()
    {
      return this;
    }



    public String getPropertyName()
    {
      return propertyName;
    }



    public Iterable<ByteString> getValues()
    {
      return null;
    }



    @Override
    public String toString()
    {
      return propertyName;
    }
  }



  public static class Request extends
      AbstractExtendedRequest<Request, Response> implements
      OperationContainer
  {
    private String targetUser;

    private List<Operation> operations = new ArrayList<Operation>();



    public Request(DN targetUser)
    {
      Validator.ensureNotNull(targetUser);
      this.targetUser = targetUser.toString();
    }



    public Request(String targetUser)
    {
      Validator.ensureNotNull(targetUser);
      this.targetUser = targetUser;
    }



    /**
     * {@inheritDoc}
     */
    public String getRequestName()
    {
      return OID_PASSWORD_POLICY_STATE_EXTOP;
    }



    public void addAuthenticationFailureTime(Date date)
    {
      if (date == null)
      {
        operations.add(OperationType.ADD_AUTHENTICATION_FAILURE_TIMES);
      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.ADD_AUTHENTICATION_FAILURE_TIMES, ByteString
                .valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void addGraceLoginUseTime(Date date)
    {
      if (date == null)
      {
        operations.add(OperationType.ADD_GRACE_LOGIN_USE_TIME);
      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.ADD_GRACE_LOGIN_USE_TIME, ByteString
                .valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void addOperation(Operation operation)
    {
      operations.add(operation);
    }



    public void clearAccountDisabledState()
    {
      operations.add(OperationType.CLEAR_ACCOUNT_DISABLED_STATE);
    }



    public void clearAccountExpirationTime()
    {
      operations.add(OperationType.CLEAR_ACCOUNT_EXPIRATION_TIME);
    }



    public void clearAuthenticationFailureTimes()
    {
      operations.add(OperationType.CLEAR_AUTHENTICATION_FAILURE_TIMES);
    }



    public void clearGraceLoginUseTimes()
    {
      operations.add(OperationType.CLEAR_GRACE_LOGIN_USE_TIMES);
    }



    public void clearLastLoginTime()
    {
      operations.add(OperationType.CLEAR_LAST_LOGIN_TIME);
    }



    public void clearPasswordChangedByRequiredTime()
    {
      operations
          .add(OperationType.CLEAR_PASSWORD_CHANGED_BY_REQUIRED_TIME);
    }



    public void clearPasswordChangedTime()
    {
      operations.add(OperationType.CLEAR_PASSWORD_CHANGED_TIME);
    }



    public void clearPasswordExpirationWarnedTime()
    {
      operations
          .add(OperationType.CLEAR_PASSWORD_EXPIRATION_WARNED_TIME);
    }



    public void clearPasswordHistory()
    {
      operations.add(OperationType.CLEAR_PASSWORD_HISTORY);
    }



    public void clearPasswordResetState()
    {
      operations.add(OperationType.CLEAR_PASSWORD_RESET_STATE);
    }



    @Override
    public OperationImpl getExtendedOperation()
    {
      return OPERATION_IMPL;
    }



    public Iterable<Operation> getOperations()
    {
      return operations;
    }



    public ByteString getRequestValue()
    {
      return encode(targetUser, operations);
    }



    public void requestAccountDisabledState()
    {
      operations.add(OperationType.GET_ACCOUNT_DISABLED_STATE);
    }



    public void requestAccountExpirationTime()
    {
      operations.add(OperationType.GET_ACCOUNT_EXPIRATION_TIME);
    }



    public void requestAuthenticationFailureTimes()
    {
      operations.add(OperationType.GET_AUTHENTICATION_FAILURE_TIMES);
    }



    public void requestGraceLoginUseTimes()
    {
      operations.add(OperationType.GET_GRACE_LOGIN_USE_TIMES);
    }



    public void requestLastLoginTime()
    {
      operations.add(OperationType.GET_LAST_LOGIN_TIME);
    }



    public void requestPasswordChangedByRequiredTime()
    {
      operations
          .add(OperationType.GET_PASSWORD_CHANGED_BY_REQUIRED_TIME);
    }



    public void requestPasswordChangedTime()
    {
      operations.add(OperationType.GET_PASSWORD_CHANGED_TIME);
    }



    public void requestPasswordExpirationWarnedTime()
    {
      operations.add(OperationType.GET_PASSWORD_EXPIRATION_WARNED_TIME);
    }



    public void requestPasswordHistory()
    {
      operations.add(OperationType.GET_PASSWORD_HISTORY);
    }



    public void requestPasswordPolicyDN()
    {
      operations.add(OperationType.GET_PASSWORD_POLICY_DN);
    }



    public void requestPasswordResetState()
    {
      operations.add(OperationType.GET_PASSWORD_RESET_STATE);
    }



    public void requestRemainingAuthenticationFailureCount()
    {
      operations
          .add(OperationType.GET_REMAINING_AUTHENTICATION_FAILURE_COUNT);
    }



    public void requestRemainingGraceLoginCount()
    {
      operations.add(OperationType.GET_REMAINING_GRACE_LOGIN_COUNT);
    }



    public void requestSecondsUntilAccountExpiration()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_ACCOUNT_EXPIRATION);
    }



    public void requestSecondsUntilAuthenticationFailureUnlock()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK);
    }



    public void requestSecondsUntilIdleLockout()
    {
      operations.add(OperationType.GET_SECONDS_UNTIL_IDLE_LOCKOUT);
    }



    public void requestSecondsUntilPasswordExpiration()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_PASSWORD_EXPIRATION);
    }



    public void requestSecondsUntilPasswordExpirationWarning()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING);
    }



    public void requestSecondsUntilPasswordResetLockout()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT);
    }



    public void requestSecondsUntilRequiredChangeTime()
    {
      operations
          .add(OperationType.GET_SECONDS_UNTIL_REQUIRED_CHANGE_TIME);
    }



    public void setAccountDisabledState(boolean state)
    {
      operations.add(new MultiValueOperation(
          OperationType.SET_ACCOUNT_DISABLED_STATE, ByteString
              .valueOf(String.valueOf(state))));
    }



    public void setAccountExpirationTime(Date date)
    {
      if (date == null)
      {
        operations.add(OperationType.SET_ACCOUNT_EXPIRATION_TIME);
      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.SET_ACCOUNT_EXPIRATION_TIME, ByteString
                .valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void setAuthenticationFailureTimes(Date... dates)
    {
      if (dates == null)
      {
        operations.add(OperationType.SET_AUTHENTICATION_FAILURE_TIMES);
      }
      else
      {
        ArrayList<ByteString> times = new ArrayList<ByteString>(
            dates.length);
        for (Date date : dates)
        {
          times.add(ByteString.valueOf(formatAsGeneralizedTime(date)));
        }
        operations.add(new MultiValueOperation(
            OperationType.SET_AUTHENTICATION_FAILURE_TIMES, times));
      }
    }



    public void setGraceLoginUseTimes(Date... dates)
    {
      if (dates == null)
      {
        operations.add(OperationType.SET_GRACE_LOGIN_USE_TIMES);
      }
      else
      {
        ArrayList<ByteString> times = new ArrayList<ByteString>(
            dates.length);
        for (Date date : dates)
        {
          times.add(ByteString.valueOf(formatAsGeneralizedTime(date)));
        }
        operations.add(new MultiValueOperation(
            OperationType.SET_GRACE_LOGIN_USE_TIMES, times));
      }
    }



    public void setLastLoginTime(Date date)
    {
      if (date == null)
      {
        operations.add(OperationType.SET_LAST_LOGIN_TIME);

      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.SET_LAST_LOGIN_TIME, ByteString
                .valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void setPasswordChangedByRequiredTime(boolean state)
    {
      operations.add(new MultiValueOperation(
          OperationType.SET_PASSWORD_CHANGED_BY_REQUIRED_TIME,
          ByteString.valueOf(String.valueOf(state))));
    }



    public void setPasswordChangedTime(Date date)
    {
      if (date == null)
      {
        operations.add(OperationType.SET_PASSWORD_CHANGED_TIME);
      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.SET_PASSWORD_CHANGED_TIME, ByteString
                .valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void setPasswordExpirationWarnedTime(Date date)
    {
      if (date == null)
      {
        operations
            .add(OperationType.SET_PASSWORD_EXPIRATION_WARNED_TIME);

      }
      else
      {
        operations.add(new MultiValueOperation(
            OperationType.SET_PASSWORD_EXPIRATION_WARNED_TIME,
            ByteString.valueOf(formatAsGeneralizedTime(date))));
      }
    }



    public void setPasswordResetState(boolean state)
    {
      operations.add(new MultiValueOperation(
          OperationType.SET_LAST_LOGIN_TIME, ByteString.valueOf(String
              .valueOf(state))));
    }



    @Override
    public String toString()
    {
      StringBuilder builder = new StringBuilder();
      builder.append("PasswordPolicyStateExtendedRequest(requestName=");
      builder.append(getRequestName());
      builder.append(", targetUser=");
      builder.append(targetUser);
      builder.append(", operations=");
      builder.append(operations);
      builder.append(", controls=");
      builder.append(getControls());
      builder.append(")");
      return builder.toString();
    }
  }



  public static class Response extends AbstractExtendedResult<Response>
      implements OperationContainer
  {
    private String targetUser;

    private List<Operation> operations = new ArrayList<Operation>();



    public Response(ResultCode resultCode, DN targetUser)
    {
      this(resultCode, String.valueOf(targetUser));
    }



    public Response(ResultCode resultCode, String targetUser)
    {
      super(resultCode);
      this.targetUser = targetUser;
    }



    /**
     * {@inheritDoc}
     */
    public String getResponseName()
    {
      // No response name defined.
      return OID_PASSWORD_POLICY_STATE_EXTOP;
    }



    public void addOperation(Operation operation)
    {
      operations.add(operation);
    }



    public Iterable<Operation> getOperations()
    {
      return operations;
    }



    public ByteString getResponseValue()
    {
      return encode(targetUser, operations);
    }



    @Override
    public String toString()
    {
      StringBuilder builder = new StringBuilder();
      builder.append("PasswordPolicyStateExtendedResponse(resultCode=");
      builder.append(getResultCode());
      builder.append(", matchedDN=");
      builder.append(getMatchedDN());
      builder.append(", diagnosticMessage=");
      builder.append(getDiagnosticMessage());
      builder.append(", referrals=");
      builder.append(getReferralURIs());
      builder.append(", responseName=");
      builder.append(getResponseName());
      builder.append(", targetUser=");
      builder.append(targetUser);
      builder.append(", operations=");
      builder.append(operations);
      builder.append(", controls=");
      builder.append(getControls());
      builder.append(")");
      return builder.toString();
    }
  }



  private static class MultiValueOperation implements Operation
  {
    private OperationType property;

    private List<ByteString> values;



    private MultiValueOperation(OperationType property, ByteString value)
    {
      this.property = property;
      this.values = Collections.singletonList(value);
    }



    private MultiValueOperation(OperationType property,
        List<ByteString> values)
    {
      this.property = property;
      this.values = values;
    }



    public OperationType getOperationType()
    {
      return property;
    }



    public Iterable<ByteString> getValues()
    {
      return values;
    }



    @Override
    public String toString()
    {
      return property.getPropertyName() + ": " + values;
    }
  }



  private interface OperationContainer
  {
    public void addOperation(Operation operation);



    public Iterable<Operation> getOperations();
  }



  private static final String PASSWORD_POLICY_DN_NAME = "Password Policy DN";

  private static final String ACCOUNT_DISABLED_STATE_NAME = "Account Disabled State";

  private static final String ACCOUNT_EXPIRATION_TIME_NAME = "Account Expiration Time";

  private static final String SECONDS_UNTIL_ACCOUNT_EXPIRATION_NAME = "Seconds Until Account Expiration";

  private static final String PASSWORD_CHANGED_TIME_NAME = "Password Changed Time";

  private static final String PASSWORD_EXPIRATION_WARNED_TIME_NAME = "Password Expiration Warned Time";

  private static final String SECONDS_UNTIL_PASSWORD_EXPIRATION_NAME = "Seconds Until Password Expiration";

  private static final String SECONDS_UNTIL_PASSWORD_EXPIRATION_WARNING_NAME = "Seconds Until Password Expiration Warning";

  private static final String AUTHENTICATION_FAILURE_TIMES_NAME = "Authentication Failure Times";

  private static final String SECONDS_UNTIL_AUTHENTICATION_FAILURE_UNLOCK_NAME = "Seconds Until Authentication Failure Unlock";

  private static final String REMAINING_AUTHENTICATION_FAILURE_COUNT_NAME = "Remaining Authentication Failure Count";

  private static final String LAST_LOGIN_TIME_NAME = "Last Login Time";

  private static final String SECONDS_UNTIL_IDLE_LOCKOUT_NAME = "Seconds Until Idle Lockout";

  private static final String PASSWORD_RESET_STATE_NAME = "Password Reset State";

  private static final String SECONDS_UNTIL_PASSWORD_RESET_LOCKOUT_NAME = "Seconds Until Password Reset Lockout";

  private static final String GRACE_LOGIN_USE_TIMES_NAME = "Grace Login Use Times";

  private static final String REMAINING_GRACE_LOGIN_COUNT_NAME = "Remaining Grace Login Count";

  private static final String PASSWORD_CHANGED_BY_REQUIRED_TIME_NAME = "Password Changed By Required Time";

  private static final String SECONDS_UNTIL_REQUIRED_CHANGE_TIME_NAME = "Seconds Until Required Change Time";

  private static final String PASSWORD_HISTORY_NAME = "Password History";



  private static void decodeOperations(ASN1Reader reader,
      OperationContainer container) throws IOException, DecodeException
  {
    // See if we have operations
    if (reader.hasNextElement())
    {
      reader.readStartSequence();
      int opType;
      OperationType type;
      while (reader.hasNextElement())
      {
        reader.readStartSequence();
        // Read the opType
        opType = reader.readEnumerated();
        try
        {
          type = OperationType.values()[opType];
        }
        catch (IndexOutOfBoundsException iobe)
        {
          throw DecodeException.error(
              ERR_PWPSTATE_EXTOP_UNKNOWN_OP_TYPE.get(String
                  .valueOf(opType)), iobe);
        }

        // See if we have any values
        if (reader.hasNextElement())
        {
          reader.readStartSequence();
          ArrayList<ByteString> values = new ArrayList<ByteString>();
          while (reader.hasNextElement())
          {
            values.add(reader.readOctetString());
          }
          reader.readEndSequence();
          container.addOperation(new MultiValueOperation(type, values));
        }
        else
        {
          container.addOperation(type);
        }
        reader.readEndSequence();
      }
      reader.readEndSequence();
    }
  }



  private static ByteString encode(String targetUser,
      List<Operation> operations)
  {
    ByteStringBuilder buffer = new ByteStringBuilder(6);
    ASN1Writer writer = ASN1.getWriter(buffer);

    try
    {
      writer.writeStartSequence();
      writer.writeOctetString(targetUser);
      if (!operations.isEmpty())
      {
        writer.writeStartSequence();
        for (Operation operation : operations)
        {
          writer.writeStartSequence();
          writer
              .writeEnumerated(operation.getOperationType().ordinal());
          if (operation.getValues() != null)
          {
            writer.writeStartSequence();
            for (ByteString value : operation.getValues())
            {
              writer.writeOctetString(value);
            }
            writer.writeEndSequence();
          }
          writer.writeEndSequence();
        }
        writer.writeEndSequence();
      }
      writer.writeEndSequence();
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }

    return buffer.toByteString();
  }



  private static final class OperationImpl implements
      ExtendedOperation<Request, Response>
  {

    public Request decodeRequest(String requestName,
        ByteString requestValue) throws DecodeException
    {
      if ((requestValue == null) || (requestValue.length() <= 0))
      {
        throw DecodeException
            .error(ERR_PWPSTATE_EXTOP_NO_REQUEST_VALUE.get());
      }

      try
      {
        ASN1Reader reader = ASN1.getReader(requestValue);
        reader.readStartSequence();

        // Read the target user DN
        Request request = new Request(reader.readOctetStringAsString());

        decodeOperations(reader, request);
        reader.readEndSequence();
        return request;
      }
      catch (IOException ioe)
      {
        Message message = ERR_PWPSTATE_EXTOP_DECODE_FAILURE
            .get(getExceptionMessage(ioe));
        throw DecodeException.error(message, ioe);
      }
    }



    public Response decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage,
        String responseName, ByteString responseValue)
        throws DecodeException
    {
      if (!resultCode.isExceptional()
          && ((responseValue == null) || (responseValue.length() <= 0)))
      {
        throw DecodeException
            .error(ERR_PWPSTATE_EXTOP_NO_REQUEST_VALUE.get());
      }

      try
      {
        ASN1Reader reader = ASN1.getReader(responseValue);
        reader.readStartSequence();

        // Read the target user DN
        Response response = new Response(resultCode, reader
            .readOctetStringAsString()).setMatchedDN(matchedDN)
            .setDiagnosticMessage(diagnosticMessage);

        decodeOperations(reader, response);
        reader.readEndSequence();
        return response;
      }
      catch (IOException ioe)
      {
        Message message = ERR_PWPSTATE_EXTOP_DECODE_FAILURE
            .get(getExceptionMessage(ioe));
        throw DecodeException.error(message, ioe);
      }
    }



    /**
     * {@inheritDoc}
     */
    public Response decodeResponse(ResultCode resultCode,
        String matchedDN, String diagnosticMessage)
    {
      if (!resultCode.isExceptional())
      {
        // A successful response must contain a response name and
        // value.
        throw new IllegalArgumentException(
            "No response name and value for result code "
                + resultCode.intValue());
      }

      return new Response(resultCode, (String) null).setMatchedDN(
          matchedDN).setDiagnosticMessage(diagnosticMessage);
    }
  }



  // Singleton instance.
  private static final OperationImpl OPERATION_IMPL = new OperationImpl();
}
