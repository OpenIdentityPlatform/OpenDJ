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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.AuthorizationIdentityResponseControl;
import org.opends.server.controls.PasswordExpiredControl;
import org.opends.server.controls.PasswordExpiringControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.PasswordPolicyWarningType;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




/**
 * This class defines an operation that may be used to authenticate a user to
 * the Directory Server.  Note that for security restrictions, response messages
 * that may be returned to the client must be carefully cleaned to ensure that
 * they do not provide a malicious client with information that may be useful in
 * an attack.  This does impact the debugability of the server, but that can
 * be addressed by calling the <CODE>setAuthFailureReason</CODE> method, which
 * can provide a reason for a failure in a form that will not be returned to the
 * client but may be written to a log file.
 */
public class BindOperation
             extends Operation
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.BindOperation";



  // The credentials used for SASL authentication.
  private ASN1OctetString saslCredentials;

  // The server SASL credentials provided to the client in the response.
  private ASN1OctetString serverSASLCredentials;

  // The authentication type used for this bind operation.
  private AuthenticationType authType;

  // Indicates whether the warning notification that should be sent to the user
  // would be the first warning.
  private boolean isFirstWarning;

  // Indicates whether the authentication should use a grace login if it is
  // successful.
  private boolean isGraceLogin;

  // Indicates whether the user's password must be changed before any other
  // operations will be allowed.
  private boolean mustChangePassword;

  // Indicates whether the client included the password policy control in the
  // bind request.
  private boolean pwPolicyControlRequested;

  // The raw, unprocessed bind DN as contained in the client request.
  private ByteString rawBindDN;

  // The password used for simple authentication.
  private ByteString simplePassword;

  // The bind DN used for this bind operation.
  private DN bindDN;

  // The DN of the user as whom a SASL authentication was attempted (regardless
  // of whether the authentication was successful) for the purpose of updating
  // password policy state information.
  private Entry saslAuthUserEntry;

  // The unique ID associated with the failure reason message.
  private int authFailureID;

  // The password policy warning value that should be included in the response
  // control.
  private int pwPolicyWarningValue;

  // The set of response controls for this bind operation.
  private List<Control> responseControls;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

  // The password policy error type that should be included in the response
  // control
  PasswordPolicyErrorType pwPolicyErrorType;

  // The password policy warning type that should be included in the response
  // control
  PasswordPolicyWarningType pwPolicyWarningType;

  // The password policy state information for this bind operation.
  private PasswordPolicyState pwPolicyState;

  // A message explaining the reason for the authentication failure.
  private String authFailureReason;

  // The SASL mechanism used for SASL authentication.
  private String saslMechanism;



  /**
   * Creates a new simple bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawBindDN         The raw, unprocessed bind DN as provided in the
   *                           request from the client.
   * @param  simplePassword    The password to use for the simple
   *                           authentication.
   */
  public BindOperation(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       ByteString rawBindDN, ByteString simplePassword)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(rawBindDN),
                              String.valueOf(simplePassword)
                            });

    this.authType        = AuthenticationType.SIMPLE;
    this.rawBindDN       = rawBindDN;
    this.simplePassword  = simplePassword;
    this.saslMechanism   = null;
    this.saslCredentials = null;

    bindDN                   = null;
    responseControls         = new ArrayList<Control>(0);
    authFailureID            = 0;
    authFailureReason        = null;
    saslAuthUserEntry        = null;
    isFirstWarning           = false;
    isGraceLogin             = false;
    mustChangePassword       = false;
    pwPolicyControlRequested = false;
    pwPolicyErrorType        = null;
    pwPolicyWarningType      = null;
    pwPolicyWarningValue     = -1;
  }



  /**
   * Creates a new SASL bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawBindDN         The raw, unprocessed bind DN as provided in the
   *                           request from the client.
   * @param  saslMechanism     The SASL mechanism included in the request.
   * @param  saslCredentials   The optional SASL credentials included in the
   *                           request.
   */
  public BindOperation(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls,
                       ByteString rawBindDN, String saslMechanism,
                       ASN1OctetString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(operationID),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(rawBindDN),
                              String.valueOf(saslMechanism),
                              String.valueOf(saslCredentials)
                            });

    this.authType        = AuthenticationType.SASL;
    this.rawBindDN       = rawBindDN;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;
    this.simplePassword  = null;

    bindDN            = null;
    responseControls  = new ArrayList<Control>(0);
    authFailureID     = 0;
    authFailureReason = null;
    saslAuthUserEntry = null;
  }



  /**
   * Creates a new simple bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  bindDN            The bind DN for this bind operation.
   * @param  simplePassword    The password to use for the simple
   *                           authentication.
   */
  public BindOperation(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls, DN bindDN,
                       ByteString simplePassword)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(bindDN),
                              String.valueOf(simplePassword)
                            });

    this.authType        = AuthenticationType.SIMPLE;
    this.bindDN          = bindDN;
    this.simplePassword  = simplePassword;
    this.saslMechanism   = null;
    this.saslCredentials = null;

    if (bindDN == null)
    {
      rawBindDN = new ASN1OctetString();
    }
    else
    {
      rawBindDN = new ASN1OctetString(bindDN.toString());
    }

    responseControls         = new ArrayList<Control>(0);
    authFailureID            = 0;
    authFailureReason        = null;
    saslAuthUserEntry        = null;
    isFirstWarning           = false;
    isGraceLogin             = false;
    mustChangePassword       = false;
    pwPolicyControlRequested = false;
    pwPolicyErrorType        = null;
    pwPolicyWarningType      = null;
    pwPolicyWarningValue     = -1;
  }



  /**
   * Creates a new SASL bind operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  bindDN            The bind DN for this bind operation.
   * @param  saslMechanism     The SASL mechanism included in the request.
   * @param  saslCredentials   The optional SASL credentials included in the
   *                           request.
   */
  public BindOperation(ClientConnection clientConnection, long operationID,
                       int messageID, List<Control> requestControls, DN bindDN,
                       String saslMechanism, ASN1OctetString saslCredentials)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(operationID),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(bindDN),
                              String.valueOf(saslMechanism),
                              String.valueOf(saslCredentials)
                            });

    this.authType        = AuthenticationType.SASL;
    this.bindDN          = bindDN;
    this.saslMechanism   = saslMechanism;
    this.saslCredentials = saslCredentials;
    this.simplePassword  = null;

    if (bindDN == null)
    {
      rawBindDN = new ASN1OctetString();
    }
    else
    {
      rawBindDN = new ASN1OctetString(bindDN.toString());
    }

    responseControls  = new ArrayList<Control>(0);
    authFailureID     = 0;
    authFailureReason = null;
    saslAuthUserEntry = null;
  }



  /**
   * Retrieves the authentication type for this bind operation.
   *
   * @return  The authentication type for this bind operation.
   */
  public AuthenticationType getAuthenticationType()
  {
    assert debugEnter(CLASS_NAME, "getAuthenticationType");

    return authType;
  }



  /**
   * Specifies the authentication type for this bind operation.
   *
   * @param  authType  The authentication type for this bind operation.
   */
  public void setAuthenticationType(AuthenticationType authType)
  {
    assert debugEnter(CLASS_NAME, "setAuthenticationType",
                      String.valueOf(authType));

    this.authType = authType;
  }



  /**
   * Retrieves the raw, unprocessed bind DN for this bind operation as contained
   * in the client request.  The value may not actually contain a valid DN, as
   * no validation will have been performed.
   *
   * @return  The raw, unprocessed bind DN for this bind operation as contained
   *          in the client request.
   */
  public ByteString getRawBindDN()
  {
    assert debugEnter(CLASS_NAME, "getRawBindDN");

    return rawBindDN;
  }



  /**
   * Specifies the raw, unprocessed bind DN for this bind operation.  This
   * should only be called by pre-parse plugins; all other code that wishes to
   * alter the bind DN should use the <CODE>getBindDN</CODE> and
   * <CODE>setBindDN</CODE> methods.
   *
   * @param  rawBindDN  The raw, unprocessed bind DN for this bind operation.
   */
  public void setRawBindDN(ByteString rawBindDN)
  {
    assert debugEnter(CLASS_NAME, "setRawBindDN", String.valueOf(rawBindDN));

    this.rawBindDN = rawBindDN;

    bindDN = null;
  }



  /**
   * Retrieves the bind DN for this bind operation.  This method should not be
   * called by pre-parse plugins, as the raw value will not have been processed
   * by that time.  Instead, pre-parse plugins should call the
   * <CODE>getRawBindDN</CODE> method.
   *
   * @return  The bind DN for this bind operation, or <CODE>null</CODE> if the
   *          raw DN has not yet been processed.
   */
  public DN getBindDN()
  {
    assert debugEnter(CLASS_NAME, "getBindDN");

    return bindDN;
  }



  /**
   * Specifies the bind DN for this bind operation. This method should not be
   * called by pre-parse plugins, which should use <CODE>setRawBindDN</CODE>
   * instead.
   *
   * @param  bindDN  The bind DN for this bind operation.
   */
  public void setBindDN(DN bindDN)
  {
    assert debugEnter(CLASS_NAME, "setBindDN", String.valueOf(bindDN));

    this.bindDN = bindDN;
  }



  /**
   * Retrieves the simple authentication password for this bind operation.
   *
   * @return  The simple authentication password for this bind operation.
   */
  public ByteString getSimplePassword()
  {
    assert debugEnter(CLASS_NAME, "getSimplePassword");

    return simplePassword;
  }



  /**
   * Specifies the simple authentication password for this bind operation.
   *
   * @param  simplePassword  The simple authentication password for this bind
   *                         operation.
   */
  public void setSimplePassword(ByteString simplePassword)
  {
    assert debugEnter(CLASS_NAME, "setSimplePassword",
                      String.valueOf(simplePassword));

    this.simplePassword = simplePassword;
  }



  /**
   * Retrieves the SASL mechanism for this bind operation.
   *
   * @return  The SASL mechanism for this bind operation.
   */
  public String getSASLMechanism()
  {
    assert debugEnter(CLASS_NAME, "getSASLMechanism");

    return  saslMechanism;
  }



  /**
   * Specifies the SASL mechanism for this bind operation.
   *
   * @param  saslMechanism  The SASL mechanism for this bind operation.
   */
  public void setSASLMechanism(String saslMechanism)
  {
    assert debugEnter(CLASS_NAME, "setSASLMechanism",
                      String.valueOf(saslMechanism));

    this.saslMechanism = saslMechanism;
  }



  /**
   * Retrieves the SASL credentials for this bind operation.
   *
   * @return  The SASL credentials for this bind operation.
   */
  public ASN1OctetString getSASLCredentials()
  {
    assert debugEnter(CLASS_NAME, "getSASLCredentials");

    return saslCredentials;
  }



  /**
   * Specifies the SASL credentials for this bind operation.
   *
   * @param  saslCredentials  The SASL credentials for this bind operation.
   */
  public void setSASLCredentials(ASN1OctetString saslCredentials)
  {
    assert debugEnter(CLASS_NAME, "setSASLCredentials",
                      String.valueOf(saslCredentials));

    this.saslCredentials = saslCredentials;
  }



  /**
   * Retrieves the set of server SASL credentials to include in the bind
   * response.
   *
   * @return  The set of server SASL credentials to include in the bind
   *          response, or <CODE>null</CODE> if there are none.
   */
  public ASN1OctetString getServerSASLCredentials()
  {
    assert debugEnter(CLASS_NAME, "getServerSASLCredentials");

    return serverSASLCredentials;
  }



  /**
   * Specifies the set of server SASL credentials to include in the bind
   * response.
   *
   * @param  serverSASLCredentials  The set of server SASL credentials to
   *                                include in the bind response.
   */
  public void setServerSASLCredentials(ASN1OctetString serverSASLCredentials)
  {
    assert debugEnter(CLASS_NAME, "setServerSASLCredentials",
                      String.valueOf(serverSASLCredentials));

    this.serverSASLCredentials = serverSASLCredentials;
  }



  /**
   * Retrieves the user entry associated with the SASL authentication attempt.
   * This should be set by any SASL mechanism in which the processing was able
   * to get far enough to make this determination, regardless of whether the
   * authentication was ultimately successful.
   *
   * @return  The user entry associated with the SASL authentication attempt, or
   *          <CODE>null</CODE> if it was not a SASL authentication or the SASL
   *          processing was not able to map the request to a user.
   */
  public Entry getSASLAuthUserEntry()
  {
    assert debugEnter(CLASS_NAME, "getSASLAuthUserEntry");

    return saslAuthUserEntry;
  }



  /**
   * Specifies the user entry associated with the SASL authentication attempt.
   * This should be set by any SASL mechanism in which the processing was able
   * to get far enough to make this determination, regardless of whether the
   * authentication was ultimately successful.
   *
   * @param  saslAuthUserEntry  The user entry associated with the SASL
   *                            authentication attempt.
   */
  public void setSASLAuthUserEntry(Entry saslAuthUserEntry)
  {
    assert debugEnter(CLASS_NAME, "setSASLAuthUserEntry",
                      String.valueOf(saslAuthUserEntry));

    this.saslAuthUserEntry = saslAuthUserEntry;
  }



  /**
   * Retrieves a human-readable message providing the reason that the
   * authentication failed, if available.
   *
   * @return  A human-readable message providing the reason that the
   *          authentication failed, or <CODE>null</CODE> if none is available.
   */
  public String getAuthFailureReason()
  {
    assert debugEnter(CLASS_NAME, "getAuthFailureReason");

    return authFailureReason;
  }



  /**
   * Retrieves the unique identifier for the authentication failure reason, if
   * available.
   *
   * @return  The unique identifier for the authentication failure reason, or
   *          zero if none is available.
   */
  public int getAuthFailureID()
  {
    assert debugEnter(CLASS_NAME, "getAuthFailureID");

    return authFailureID;
  }



  /**
   * Specifies the reason that the authentication failed.
   *
   * @param  id      The unique identifier for the authentication failure
   *                 reason.
   * @param  reason  A human-readable message providing the reason that the
   *                 authentication failed.
   */
  public void setAuthFailureReason(int id, String reason)
  {
    assert debugEnter(CLASS_NAME, "setAuthFailureReason",
                      String.valueOf(id), String.valueOf(reason));

    if (id < 0)
    {
      authFailureID = 0;
    }
    else
    {
      authFailureID = id;
    }

    authFailureReason = reason;
  }



  /**
   * Retrieves the time that processing started for this operation.
   *
   * @return  The time that processing started for this operation.
   */
  public long getProcessingStartTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingStartTime");

    return processingStartTime;
  }



  /**
   * Retrieves the time that processing stopped for this operation.  This will
   * actually hold a time immediately before the response was sent to the
   * client.
   *
   * @return  The time that processing stopped for this operation.
   */
  public long getProcessingStopTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingStopTime");

    return processingStopTime;
  }



  /**
   * Retrieves the length of time in milliseconds that the server spent
   * processing this operation.  This should not be called until after the
   * server has sent the response to the client.
   *
   * @return  The length of time in milliseconds that the server spent
   *          processing this operation.
   */
  public long getProcessingTime()
  {
    assert debugEnter(CLASS_NAME, "getProcessingTime");

    return (processingStopTime - processingStartTime);
  }


  /**
   * Retrieves the operation type for this operation.
   *
   * @return  The operation type for this operation.
   */
  public OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.BIND;
  }



  /**
   * Retrieves a standard set of elements that should be logged in requests for
   * this type of operation.  Each element in the array will itself be a
   * two-element array in which the first element is the name of the field and
   * the second is a string representation of the value, or <CODE>null</CODE> if
   * there is no value for that field.
   *
   * @return  A standard set of elements that should be logged in requests for
   *          this type of operation.
   */
  public String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    if (authType == AuthenticationType.SASL)
    {
      return new String[][]
      {
        new String[] { LOG_ELEMENT_BIND_DN, String.valueOf(rawBindDN) },
        new String[] { LOG_ELEMENT_AUTH_TYPE, authType.toString() },
        new String[] { LOG_ELEMENT_SASL_MECHANISM, saslMechanism }
      };
    }
    else
    {
      return new String[][]
      {
        new String[] { LOG_ELEMENT_BIND_DN, String.valueOf(rawBindDN) },
        new String[] { LOG_ELEMENT_AUTH_TYPE, authType.toString() }
      };
    }
  }



  /**
   * Retrieves a standard set of elements that should be logged in responses for
   * this type of operation.  Each element in the array will itself be a
   * two-element array in which the first element is the name of the field and
   * the second is a string representation of the value, or <CODE>null</CODE> if
   * there is no value for that field.
   *
   * @return  A standard set of elements that should be logged in responses for
   *          this type of operation.
   */
  public String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    StringBuilder errorMessageBuffer = getErrorMessage();
    if (errorMessageBuffer == null)
    {
      errorMessage = null;
    }
    else
    {
      errorMessage = errorMessageBuffer.toString();
    }

    String matchedDNStr;
    DN matchedDN = getMatchedDN();
    if (matchedDN == null)
    {
      matchedDNStr = null;
    }
    else
    {
      matchedDNStr = matchedDN.toString();
    }

    String referrals;
    List<String> referralURLs = getReferralURLs();
    if ((referralURLs == null) || referralURLs.isEmpty())
    {
      referrals = null;
    }
    else
    {
      StringBuilder buffer = new StringBuilder();
      Iterator<String> iterator = referralURLs.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
      }

      referrals = buffer.toString();
    }

    String processingTime =
         String.valueOf(processingStopTime - processingStartTime);

    return new String[][]
    {
      new String[] { LOG_ELEMENT_RESULT_CODE, resultCode },
      new String[] { LOG_ELEMENT_ERROR_MESSAGE, errorMessage },
      new String[] { LOG_ELEMENT_MATCHED_DN, matchedDNStr },
      new String[] { LOG_ELEMENT_REFERRAL_URLS, referrals },
      new String[] { LOG_ELEMENT_PROCESSING_TIME, processingTime }
    };
  }


  /**
   * Retrieves the set of controls to include in the response to the client.
   * Note that the contents of this list should not be altered after
   * post-operation plugins have been called.
   *
   * @return  The set of controls to include in the response to the client.
   */
  public List<Control> getResponseControls()
  {
    assert debugEnter(CLASS_NAME, "getResponseControls");

    return responseControls;
  }



  /**
   * Performs the work of actually processing this operation.  This should
   * include all processing for the operation, including invoking plugins,
   * logging messages, performing access control, managing synchronization, and
   * any other work that might need to be done in the course of processing.
   */
  public void run()
  {
    assert debugEnter(CLASS_NAME, "run");


    // Start the processing timer and initially set the result to indicate that
    // the result is unknown.
    processingStartTime = System.currentTimeMillis();
    setResultCode(ResultCode.UNDEFINED);
    boolean returnAuthzID = false;
    int     sizeLimit     = DirectoryServer.getSizeLimit();
    int     timeLimit     = DirectoryServer.getTimeLimit();


    // Set a flag to indicate that a bind operation is in progress.  This should
    // ensure that no new operations will be accepted for this client until the
    // bind is complete.
    clientConnection.setBindInProgress(true);


    // Wipe out any existing authentication for the client connection and create
    // a placeholder that will be used if the bind is successful.
    clientConnection.setUnauthenticated();
    AuthenticationInfo authInfo = null;


    // Abandon any operations that may be in progress for the client.
    String cancelReason = getMessage(MSGID_CANCELED_BY_BIND_REQUEST);
    CancelRequest cancelRequest = new CancelRequest(true, cancelReason);
    clientConnection.cancelAllOperationsExcept(cancelRequest, getMessageID());


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
bindProcessing:
    {
      // Invoke the pre-parse bind plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseBindPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logBindRequest(this);
        logBindResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logBindRequest(this);
        break bindProcessing;
      }


      // Log the bind request message.
      logBindRequest(this);


      // Process the bind DN to convert it from the raw form as provided by the
      // client into the form required for the rest of the bind processing.
      try
      {
        if (bindDN == null)
        {
          bindDN = DN.decode(rawBindDN);
        }
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "run", de);

        setResultCode(ResultCode.INVALID_CREDENTIALS);
        setAuthFailureReason(de.getErrorMessageID(), de.getErrorMessage());
        break bindProcessing;
      }

      // Check to see if the client has permission to perform the
      // bind.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes any controls
      // specified.
      if (AccessControlConfigManager.getInstance()
          .getAccessControlHandler().isAllowed(this) == false) {
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

        int msgID = MSGID_BIND_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
        appendErrorMessage(getMessage(msgID, String.valueOf(bindDN)));

        skipPostOperation = true;
        break bindProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then see
      // if there is any special processing required.
      List<Control> requestControls = getRequestControls();
      if ((requestControls != null) && (! requestControls.isEmpty()))
      {
        for (int i=0; i < requestControls.size(); i++)
        {
          Control c   = requestControls.get(i);
          String  oid = c.getOID();

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
            setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

            int msgID = MSGID_BIND_UNSUPPORTED_CRITICAL_CONTROL;
            appendErrorMessage(getMessage(msgID, String.valueOf(oid)));

            break bindProcessing;
          }
        }
      }


      // Check to see if this is a simple bind or a SASL bind and process
      // accordingly.
      switch (authType)
      {
        case SIMPLE:
          // See if this is an anonymous bind.  If so, then determine whether
          // to allow it.
          if ((simplePassword == null) || (simplePassword.value().length == 0))
          {
            // If there is a bind DN, then wee whether that is acceptable.
            if (DirectoryServer.bindWithDNRequiresPassword() &&
                ((bindDN != null) && (! bindDN.isNullDN())))
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              int    msgID   = MSGID_BIND_DN_BUT_NO_PASSWORD;
              String message = getMessage(msgID);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }


            setResultCode(ResultCode.SUCCESS);
            authInfo = new AuthenticationInfo();
            break bindProcessing;
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
          Lock userLock = null;
          for (int i=0; i < 3; i++)
          {
            userLock = LockManager.lockRead(bindDN);
            if (userLock != null)
            {
              break;
            }
          }

          if (userLock == null)
          {
            int    msgID   = MSGID_BIND_OPERATION_CANNOT_LOCK_USER;
            String message = getMessage(msgID, String.valueOf(bindDN));

            setResultCode(DirectoryServer.getServerErrorResultCode());
            setAuthFailureReason(msgID, message);
            break bindProcessing;
          }

          try
          {
            Entry userEntry;
            try
            {
              userEntry = DirectoryServer.getEntry(bindDN);
            }
            catch (DirectoryException de)
            {
              assert debugException(CLASS_NAME, "run", de);

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(de.getErrorMessageID(),
                                   de.getErrorMessage());

              userEntry = null;
              break bindProcessing;
            }

            if (userEntry == null)
            {

              int    msgID   = MSGID_BIND_OPERATION_UNKNOWN_USER;
              String message = getMessage(msgID, String.valueOf(bindDN));

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }


            // Check to see if the user has a password.  If not, then fail.
            // FIXME -- We need to have a way to enable/disable debugging.
            pwPolicyState = new PasswordPolicyState(userEntry, false, false);
            AttributeType pwType = pwPolicyState.getPasswordAttribute();

            List<Attribute> pwAttr = userEntry.getAttribute(pwType);
            if ((pwAttr == null) || (pwAttr.isEmpty()))
            {
              int    msgID   = MSGID_BIND_OPERATION_NO_PASSWORD;
              String message = getMessage(msgID, String.valueOf(bindDN));

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }


            // Check to see if the authentication must be done in a secure
            // manner.  If so, then the client connection must be secure.
            if (pwPolicyState.requireSecureAuthentication() &&
                (! clientConnection.isSecure()))
            {
              int    msgID   = MSGID_BIND_OPERATION_INSECURE_SIMPLE_BIND;
              String message = getMessage(msgID, String.valueOf(bindDN));

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }


            // Check to see if the user is administratively disabled or locked.
            if (pwPolicyState.isDisabled())
            {
              int    msgID   = MSGID_BIND_OPERATION_ACCOUNT_DISABLED;
              String message = getMessage(msgID, String.valueOf(bindDN));

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }
            else if (pwPolicyState.isAccountExpired())
            {
              int    msgID   = MSGID_BIND_OPERATION_ACCOUNT_EXPIRED;
              String message = getMessage(msgID, String.valueOf(bindDN));

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToFailures())
            {
              int    msgID   = MSGID_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED;
              String message = getMessage(msgID, String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToMaximumResetAge())
            {
              int    msgID   = MSGID_BIND_OPERATION_ACCOUNT_RESET_LOCKED;
              String message = getMessage(msgID, String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }
            else if (pwPolicyState.lockedDueToIdleInterval())
            {
              int    msgID   = MSGID_BIND_OPERATION_ACCOUNT_IDLE_LOCKED;
              String message = getMessage(msgID, String.valueOf(bindDN));

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);
              break bindProcessing;
            }


            // Determine whether the password is expired, or whether the user
            // should be warned about an upcoming expiration.
            if (pwPolicyState.isPasswordExpired())
            {
              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.PASSWORD_EXPIRED;
              }

              int maxGraceLogins = pwPolicyState.getMaxAllowedGraceLogins();
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
                  int    msgID   = MSGID_BIND_OPERATION_PASSWORD_EXPIRED;
                  String message = getMessage(msgID, String.valueOf(bindDN));

                  setResultCode(ResultCode.INVALID_CREDENTIALS);
                  setAuthFailureReason(msgID, message);
                  break bindProcessing;
                }
              }
              else
              {
                int    msgID   = MSGID_BIND_OPERATION_PASSWORD_EXPIRED;
                String message = getMessage(msgID, String.valueOf(bindDN));

                setResultCode(ResultCode.INVALID_CREDENTIALS);
                setAuthFailureReason(msgID, message);
                break bindProcessing;
              }
            }
            else if (pwPolicyState.shouldWarn())
            {
              if (pwPolicyWarningType == null)
              {
                pwPolicyWarningType =
                     PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
                pwPolicyWarningValue =
                     pwPolicyState.getSecondsUntilExpiration();
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


            // Invoke the pre-operation bind plugins.
            PreOperationPluginResult preOpResult =
                 pluginConfigManager.invokePreOperationBindPlugins(this);
            if (preOpResult.connectionTerminated())
            {
              // There's no point in continuing with anything.  Log the result
              // and return.
              setResultCode(ResultCode.CANCELED);

              int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
              appendErrorMessage(getMessage(msgID));

              processingStopTime = System.currentTimeMillis();

              logBindResponse(this);
              return;
            }
            else if (preOpResult.sendResponseImmediately())
            {
              skipPostOperation = true;
              break bindProcessing;
            }


            // Determine whether the provided password matches any of the stored
            // passwords for the user.
            if (pwPolicyState.passwordMatches(simplePassword))
            {
              setResultCode(ResultCode.SUCCESS);

              boolean isRoot = DirectoryServer.isRootDN(userEntry.getDN());
              authInfo = new AuthenticationInfo(userEntry.getDN(),
                                                simplePassword, isRoot);


              // See if the user's entry contains a custom size limit.
              AttributeType attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_SIZE_LIMIT,
                                                 true);
              List<Attribute> attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    int msgID = MSGID_BIND_MULTIPLE_USER_SIZE_LIMITS;
                    String message =
                         getMessage(msgID, String.valueOf(userEntry.getDN()));
                    logError(ErrorLogCategory.CORE_SERVER,
                             ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                  }
                  else
                  {
                    try
                    {
                      sizeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      assert debugException(CLASS_NAME, "run", e);

                      int msgID = MSGID_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT;
                      String message =
                           getMessage(msgID, v.getStringValue(),
                                      String.valueOf(userEntry.getDN()));
                      logError(ErrorLogCategory.CORE_SERVER,
                               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom time limit.
              attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_TIME_LIMIT,
                                                 true);
              attrList = userEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    int msgID = MSGID_BIND_MULTIPLE_USER_TIME_LIMITS;
                    String message =
                         getMessage(msgID, String.valueOf(userEntry.getDN()));
                    logError(ErrorLogCategory.CORE_SERVER,
                             ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                  }
                  else
                  {
                    try
                    {
                      timeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      assert debugException(CLASS_NAME, "run", e);

                      int msgID = MSGID_BIND_CANNOT_PROCESS_USER_TIME_LIMIT;
                      String message =
                           getMessage(msgID, v.getStringValue(),
                                      String.valueOf(userEntry.getDN()));
                      logError(ErrorLogCategory.CORE_SERVER,
                               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                    }
                  }
                }
              }


              pwPolicyState.handleDeprecatedStorageSchemes(simplePassword);
              pwPolicyState.clearAuthFailureTimes();

              if (isFirstWarning)
              {
                pwPolicyState.setWarnedTime();
              }

              if (isGraceLogin)
              {
                pwPolicyState.updateGraceLoginTimes();
              }

              pwPolicyState.setLastLoginTime();
            }
            else
            {
              int    msgID   = MSGID_BIND_OPERATION_WRONG_PASSWORD;
              String message = getMessage(msgID);

              setResultCode(ResultCode.INVALID_CREDENTIALS);
              setAuthFailureReason(msgID, message);

              int maxAllowedFailures = pwPolicyState.getMaxAllowedFailures();
              if (maxAllowedFailures > 0)
              {
                pwPolicyState.updateAuthFailureTimes();
                if (pwPolicyState.getAuthFailureTimes().size() >
                    maxAllowedFailures)
                {
                  pwPolicyState.lockDueToFailures();
                }
              }
            }
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "run", e);

            int    msgID   = MSGID_BIND_OPERATION_PASSWORD_VALIDATION_EXCEPTION;
            String message = getMessage(msgID, stackTraceToSingleLineString(e));

            setResultCode(DirectoryServer.getServerErrorResultCode());
            setAuthFailureReason(msgID, message);
            break bindProcessing;
          }
          finally
          {
            // No matter what, make sure to unlock the user's entry.
            LockManager.unlock(bindDN, userLock);
          }

          break;


        case SASL:
          // Get the appropriate authentication handler for this request based
          // on the SASL mechanism.  If there is none, then fail.
          SASLMechanismHandler saslHandler =
               DirectoryServer.getSASLMechanismHandler(saslMechanism);
          if (saslHandler == null)
          {
            setResultCode(ResultCode.AUTH_METHOD_NOT_SUPPORTED);

            int    msgID   = MSGID_BIND_OPERATION_UNKNOWN_SASL_MECHANISM;
            String message = getMessage(msgID, saslMechanism);

            appendErrorMessage(message);
            setAuthFailureReason(msgID, message);
            break bindProcessing;
          }


          // Check to see if the client has sufficient permission to perform the
          // bind.
          // NYI


          // Invoke the pre-operation bind plugins.
          PreOperationPluginResult preOpResult =
               pluginConfigManager.invokePreOperationBindPlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            setResultCode(ResultCode.CANCELED);

            int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
            appendErrorMessage(getMessage(msgID));

            processingStopTime = System.currentTimeMillis();

            logBindResponse(this);
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break bindProcessing;
          }


          // Actually process the SASL bind.
          saslHandler.processSASLBind(this);


          // Create the password policy state object.
          String userDNString;
          if (saslAuthUserEntry == null)
          {
            pwPolicyState = null;
            userDNString  = null;
          }
          else
          {
            try
            {
              // FIXME -- Need to have a way to enable debugging.
              pwPolicyState = new PasswordPolicyState(saslAuthUserEntry, false,
                                                      false);
              userDNString = String.valueOf(saslAuthUserEntry.getDN());
            }
            catch (DirectoryException de)
            {
              assert debugException(CLASS_NAME, "run", de);

              setResponseData(de);
              break bindProcessing;
            }
          }


          // Perform password policy checks that will need to be completed
          // regardless of whether the authentication was successful.
          if (pwPolicyState != null)
          {
            if (pwPolicyState.isDisabled())
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_BIND_OPERATION_ACCOUNT_DISABLED;
              appendErrorMessage(getMessage(msgID, userDNString));
              break bindProcessing;
            }
            else if (pwPolicyState.isAccountExpired())
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_BIND_OPERATION_ACCOUNT_EXPIRED;
              appendErrorMessage(getMessage(msgID, userDNString));
              break bindProcessing;
            }

            if (pwPolicyState.requireSecureAuthentication() &&
                (! clientConnection.isSecure()) &&
                (! saslHandler.isSecure(saslMechanism)))
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              int msgID = MSGID_BIND_OPERATION_INSECURE_SASL_BIND;
              appendErrorMessage(getMessage(msgID, saslMechanism,
                                            userDNString));
              break bindProcessing;
            }

            if (pwPolicyState.lockedDueToFailures())
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              int msgID = MSGID_BIND_OPERATION_ACCOUNT_FAILURE_LOCKED;
              appendErrorMessage(getMessage(msgID, userDNString));
              break bindProcessing;
            }

            if (pwPolicyState.lockedDueToIdleInterval())
            {
              setResultCode(ResultCode.INVALID_CREDENTIALS);

              if (pwPolicyErrorType == null)
              {
                pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
              }

              int msgID = MSGID_BIND_OPERATION_ACCOUNT_IDLE_LOCKED;
              appendErrorMessage(getMessage(msgID, userDNString));
              break bindProcessing;
            }


            if (saslHandler.isPasswordBased(saslMechanism))
            {
              if (pwPolicyState.lockedDueToMaximumResetAge())
              {
                setResultCode(ResultCode.INVALID_CREDENTIALS);

                if (pwPolicyErrorType == null)
                {
                  pwPolicyErrorType = PasswordPolicyErrorType.ACCOUNT_LOCKED;
                }

                int msgID = MSGID_BIND_OPERATION_ACCOUNT_RESET_LOCKED;
                appendErrorMessage(getMessage(msgID, userDNString));
                break bindProcessing;
              }

              if (pwPolicyState.isPasswordExpired())
              {
                if (pwPolicyErrorType == null)
                {
                  pwPolicyErrorType = PasswordPolicyErrorType.PASSWORD_EXPIRED;
                }

                int maxGraceLogins = pwPolicyState.getMaxAllowedGraceLogins();
                if ((maxGraceLogins > 0) && pwPolicyState.mayUseGraceLogin())
                {
                  List<Long> graceLoginTimes =
                       pwPolicyState.getGraceLoginTimes();
                  if ((graceLoginTimes == null) ||
                      (graceLoginTimes.size() < maxGraceLogins))
                  {
                    isGraceLogin       = true;
                    mustChangePassword = true;

                    if (pwPolicyWarningType == null)
                    {
                      pwPolicyWarningType =
                           PasswordPolicyWarningType.GRACE_LOGINS_REMAINING;
                      pwPolicyWarningValue =
                           maxGraceLogins - (graceLoginTimes.size() + 1);
                    }
                  }
                  else
                  {
                    int    msgID   = MSGID_BIND_OPERATION_PASSWORD_EXPIRED;
                    String message = getMessage(msgID, String.valueOf(bindDN));

                    setResultCode(ResultCode.INVALID_CREDENTIALS);
                    setAuthFailureReason(msgID, message);
                    break bindProcessing;
                  }
                }
                else
                {
                  int    msgID   = MSGID_BIND_OPERATION_PASSWORD_EXPIRED;
                  String message = getMessage(msgID, String.valueOf(bindDN));

                  setResultCode(ResultCode.INVALID_CREDENTIALS);
                  setAuthFailureReason(msgID, message);
                  break bindProcessing;
                }
              }
              else if (pwPolicyState.shouldWarn())
              {
                if (pwPolicyWarningType == null)
                {
                  pwPolicyWarningType =
                       PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION;
                  pwPolicyWarningValue =
                       pwPolicyState.getSecondsUntilExpiration();
                }

                isFirstWarning = pwPolicyState.isFirstWarning();
              }
            }
          }


          // Determine whether the authentication was successful and perform
          // any remaining password policy processing accordingly.  Also check
          // for a custom size/time limit.
          ResultCode resultCode = getResultCode();
          if (resultCode == ResultCode.SUCCESS)
          {
            if (pwPolicyState != null)
            {
              if (saslHandler.isPasswordBased(saslMechanism) &&
                  pwPolicyState.mustChangePassword())
              {
                mustChangePassword = true;
              }

              if (isFirstWarning)
              {
                pwPolicyState.setWarnedTime();
              }

              if (isGraceLogin)
              {
                pwPolicyState.updateGraceLoginTimes();
              }

              pwPolicyState.setLastLoginTime();


              // See if the user's entry contains a custom size limit.
              AttributeType attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_SIZE_LIMIT,
                                                 true);
              List<Attribute> attrList =
                   saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    int msgID = MSGID_BIND_MULTIPLE_USER_SIZE_LIMITS;
                    String message = getMessage(msgID, userDNString);
                    logError(ErrorLogCategory.CORE_SERVER,
                             ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                  }
                  else
                  {
                    try
                    {
                      sizeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      assert debugException(CLASS_NAME, "run", e);

                      int msgID = MSGID_BIND_CANNOT_PROCESS_USER_SIZE_LIMIT;
                      String message =
                           getMessage(msgID, v.getStringValue(), userDNString);
                      logError(ErrorLogCategory.CORE_SERVER,
                               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                    }
                  }
                }
              }


              // See if the user's entry contains a custom time limit.
              attrType =
                   DirectoryServer.getAttributeType(OP_ATTR_USER_TIME_LIMIT,
                                                    true);
              attrList = saslAuthUserEntry.getAttribute(attrType);
              if ((attrList != null) && (attrList.size() == 1))
              {
                Attribute a = attrList.get(0);
                LinkedHashSet<AttributeValue>  values = a.getValues();
                Iterator<AttributeValue> iterator = values.iterator();
                if (iterator.hasNext())
                {
                  AttributeValue v = iterator.next();
                  if (iterator.hasNext())
                  {
                    int msgID = MSGID_BIND_MULTIPLE_USER_TIME_LIMITS;
                    String message = getMessage(msgID, userDNString);
                    logError(ErrorLogCategory.CORE_SERVER,
                             ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                  }
                  else
                  {
                    try
                    {
                      timeLimit = Integer.parseInt(v.getStringValue());
                    }
                    catch (Exception e)
                    {
                      assert debugException(CLASS_NAME, "run", e);

                      int msgID = MSGID_BIND_CANNOT_PROCESS_USER_TIME_LIMIT;
                      String message =
                           getMessage(msgID, v.getStringValue(), userDNString);
                      logError(ErrorLogCategory.CORE_SERVER,
                               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
                    }
                  }
                }
              }
            }
          }
          else if (resultCode == ResultCode.SASL_BIND_IN_PROGRESS)
          {
            // FIXME -- Is any special processing needed here?
          }
          else
          {
            if (pwPolicyState != null)
            {
              if (saslHandler.isPasswordBased(saslMechanism))
              {

                int maxAllowedFailures = pwPolicyState.getMaxAllowedFailures();
                if (maxAllowedFailures > 0)
                {
                  pwPolicyState.updateAuthFailureTimes();
                  if (pwPolicyState.getAuthFailureTimes().size() >
                      maxAllowedFailures)
                  {
                    pwPolicyState.lockDueToFailures();
                  }
                }
              }
            }
          }

          break;


        default:
          // Send a protocol error response to the client and disconnect.
          // NYI
          return;
      }
    }


    // Update the user's account with any password policy changes that may be
    // required.
    try
    {
      if (pwPolicyState != null)
      {
        pwPolicyState.updateUserEntry();
      }
    }
    catch (DirectoryException de)
    {
      assert debugException(CLASS_NAME, "run", de);

      setResponseData(de);
    }


    // Invoke the post-operation bind plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationBindPlugins(this);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result
        // and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logBindResponse(this);
        return;
      }
    }


    // Update the authentication information for the user.
    if ((getResultCode() == ResultCode.SUCCESS) && (authInfo != null))
    {
      clientConnection.setAuthenticationInfo(authInfo);
      clientConnection.setSizeLimit(sizeLimit);
      clientConnection.setTimeLimit(timeLimit);
      clientConnection.setMustChangePassword(mustChangePassword);

      if (returnAuthzID)
      {
        responseControls.add(new AuthorizationIdentityResponseControl(
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
        responseControls.add(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          responseControls.add(new PasswordExpiredControl());
        }
        else if (pwPolicyWarningType ==
                 PasswordPolicyWarningType.TIME_BEFORE_EXPIRATION)
        {
          responseControls.add(new PasswordExpiringControl(
                                        pwPolicyWarningValue));
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
        responseControls.add(pwpControl);
      }
      else
      {
        if (pwPolicyErrorType == PasswordPolicyErrorType.PASSWORD_EXPIRED)
        {
          responseControls.add(new PasswordExpiredControl());
        }
      }
    }


    // Unset the "bind in progress" flag to allow other operations to be
    // processed.
    // FIXME -- Make sure this also gets unset at every possible point at which
    // the bind could fail and this method could return early.
    clientConnection.setBindInProgress(false);


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the bind response to the client.
    clientConnection.sendResponse(this);


    // Log the bind response.
    logBindResponse(this);


    // Invoke the post-response bind plugins.
    pluginConfigManager.invokePostResponseBindPlugins(this);
  }



  /**
   * Attempts to cancel this operation before processing has completed.  Note
   * that a bind operation may not be canceled, so this should never do
   * anything.
   *
   * @param  cancelRequest  Information about the way in which the operation
   *                        should be canceled.
   *
   * @return  A code providing information on the result of the cancellation.
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancel", String.valueOf(cancelRequest));

    cancelRequest.addResponseMessage(getMessage(MSGID_CANNOT_CANCEL_BIND));
    return CancelResult.CANNOT_CANCEL;
  }



  /**
   * Retrieves the cancel request that has been issued for this operation, if
   * there is one.  Note that a bind operation may not be canceled, so this will
   * always return <CODE>null</CODE>.
   *
   * @return  The cancel request that has been issued for this operation, or
   *          <CODE>null</CODE> if there has not been any request to cancel.
   */
  public CancelRequest getCancelRequest()
  {
    assert debugEnter(CLASS_NAME, "getCancelRequest");

    return null;
  }



  /**
   * Appends a string representation of this operation to the provided buffer.
   *
   * @param  buffer  The buffer into which a string representation of this
   *                 operation should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    assert debugEnter(CLASS_NAME, "toString", "java.lang.StringBuilder");

    buffer.append("BindOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawBindDN);
    buffer.append(", authType=");
    buffer.append(authType);
    buffer.append(")");
  }
}

