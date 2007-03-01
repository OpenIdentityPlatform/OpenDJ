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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;
import org.opends.server.types.operation.PreParseCompareOperation;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public class CompareOperation
       extends Operation
       implements PreParseCompareOperation, PreOperationCompareOperation,
                  PostOperationCompareOperation, PostResponseCompareOperation
{



  // The attribute type for this compare operation.
  private AttributeType attributeType;

  // The assertion value for the compare operation.
  private ByteString assertionValue;

  // The raw, unprocessed entry DN as included in the client request.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this compare operation.
  private CancelRequest cancelRequest;

  // The DN of the entry for the compare operation.
  private DN entryDN;

  // The entry to be compared.
  private Entry entry;

  // The set of response controls for this compare operation.
  private List<Control> responseControls;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;

  // The attribute type for the compare operation.
  private String rawAttributeType;



  /**
   * Creates a new compare operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed entry DN as provided in the
   *                           client request.  This may or may not be a valid
   *                           DN as no validation will have been performed yet.
   * @param  rawAttributeType  The raw attribute type for the compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperation(ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          ByteString rawEntryDN, String rawAttributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN       = rawEntryDN;
    this.rawAttributeType = rawAttributeType;
    this.assertionValue   = assertionValue;

    responseControls = new ArrayList<Control>();
    entry            = null;
    entryDN          = null;
    attributeType    = null;
    cancelRequest    = null;
  }



  /**
   * Creates a new compare operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for this compare operation.
   * @param  attributeType     The attribute type for this compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperation(ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          DN entryDN, AttributeType attributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN        = entryDN;
    this.attributeType  = attributeType;
    this.assertionValue = assertionValue;

    responseControls = new ArrayList<Control>();
    rawEntryDN       = new ASN1OctetString(entryDN.toString());
    rawAttributeType = attributeType.getNameOrOID();
    cancelRequest    = null;
    entry            = null;
  }



  /**
   * Retrieves the raw, unprocessed entry DN as included in the client request.
   * The DN that is returned may or may not be a valid DN, since no validation
   * will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client request.
   */
  public final ByteString getRawEntryDN()
  {

    return rawEntryDN;
  }



  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {

    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * Retrieves the DN of the entry to compare.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to compare, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  public final DN getEntryDN()
  {

    return entryDN;
  }



  /**
   * Retrieves the raw attribute type for this compare operation.
   *
   * @return  The raw attribute type for this compare operation.
   */
  public final String getRawAttributeType()
  {

    return rawAttributeType;
  }



  /**
   * Specifies the raw attribute type for this compare operation.  This should
   * only be called by pre-parse plugins.
   *
   * @param  rawAttributeType  The raw attribute type for this compare
   *                           operation.
   */
  public final void setRawAttributeType(String rawAttributeType)
  {

    this.rawAttributeType = rawAttributeType;

    attributeType = null;
  }



  /**
   * Retrieves the attribute type for this compare operation.  This should not
   * be called by pre-parse plugins because the processed attribute type will
   * not be available yet.
   *
   * @return  The attribute type for this compare operation.
   */
  public final AttributeType getAttributeType()
  {

    return attributeType;
  }



  /**
   * Retrieves the assertion value for this compare operation.
   *
   * @return  The assertion value for this compare operation.
   */
  public final ByteString getAssertionValue()
  {

    return assertionValue;
  }



  /**
   * Specifies the assertion value for this compare operation.  This should only
   * be called by pre-parse and pre-operation plugins.
   *
   * @param  assertionValue  The assertion value for this compare operation.
   */
  public final void setAssertionValue(ByteString assertionValue)
  {

    this.assertionValue = assertionValue;
  }



  /**
   * Retrieves the entry to target with the compare operation.  This should not
   * be called by pre-parse plugins.
   *
   * @return  The entry to target with the compare operation, or
   *          <CODE>null</CODE> if the entry is not yet available.
   */
  public final Entry getEntryToCompare()
  {

    return entry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStartTime()
  {

    return processingStartTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingStopTime()
  {

    return processingStopTime;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final long getProcessingTime()
  {

    return (processingStopTime - processingStartTime);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.COMPARE;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, String message,
                                     int messageID)
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

    clientConnection.disconnect(disconnectReason, sendNotification, message,
                                messageID);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) },
      new String[] { LOG_ELEMENT_COMPARE_ATTR, rawAttributeType }
    };
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final String[][] getResponseLogElements()
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
   * {@inheritDoc}
   */
  @Override()
  public final List<Control> getResponseControls()
  {

    return responseControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void run()
  {

    setResultCode(ResultCode.UNDEFINED);


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
compareProcessing:
    {
      // Invoke the pre-parse compare plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseComparePlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logCompareRequest(this);
        logCompareResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logCompareRequest(this);
        break compareProcessing;
      }


      // Log the compare request message.
      logCompareRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logCompareResponse(this);
        return;
      }


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the compare processing.
      try
      {
        if (entryDN == null)
        {
          entryDN = DN.decode(rawEntryDN);
        }
      }
      catch (DirectoryException de)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        skipPostOperation = true;

        break compareProcessing;
      }


      // If the target entry is in the server configuration, then make sure the
      // requester has the CONFIG_READ privilege.
      if (DirectoryServer.getConfigHandler().handlesEntry(entryDN) &&
          (! clientConnection.hasPrivilege(Privilege.CONFIG_READ, this)))
      {
        int msgID = MSGID_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES;
        appendErrorMessage(getMessage(msgID));
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        skipPostOperation = true;

        break compareProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logCompareResponse(this);
        return;
      }


      // Grab a read lock on the entry.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(entryDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        int    msgID   = MSGID_COMPARE_CANNOT_LOCK_ENTRY;
        String message = getMessage(msgID, String.valueOf(entryDN));

        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(message);
        skipPostOperation = true;
        break compareProcessing;
      }

      try
      {
        // Get the entry.  If it does not exist, then fail.
        try
        {
          entry = DirectoryServer.getEntry(entryDN);

          if (entry == null)
          {
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(getMessage(MSGID_COMPARE_NO_SUCH_ENTRY,
                                          String.valueOf(entryDN)));

            // See if one of the entry's ancestors exists.
            DN parentDN = entryDN.getParentDNInSuffix();
            while (parentDN != null)
            {
              try
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  setMatchedDN(parentDN);
                  break;
                }
              }
              catch (Exception e)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, e);
                }
                break;
              }

              parentDN = parentDN.getParentDNInSuffix();
            }

            break compareProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          break compareProcessing;
        }

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        List<Control> requestControls = getRequestControls();
        if ((requestControls != null) && (! requestControls.isEmpty()))
        {
          for (int i=0; i < requestControls.size(); i++)
          {
            Control c   = requestControls.get(i);
            String  oid = c.getOID();

            if (oid.equals(OID_LDAP_ASSERTION))
            {
              LDAPAssertionRequestControl assertControl;
              if (c instanceof LDAPAssertionRequestControl)
              {
                assertControl = (LDAPAssertionRequestControl) c;
              }
              else
              {
                try
                {
                  assertControl = LDAPAssertionRequestControl.decodeControl(c);
                  requestControls.set(i, assertControl);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    debugCought(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break compareProcessing;
                }
              }

              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                SearchFilter filter = assertControl.getSearchFilter();
                if (! filter.matchesEntry(entry))
                {
                  setResultCode(ResultCode.ASSERTION_FAILED);

                  appendErrorMessage(getMessage(MSGID_COMPARE_ASSERTION_FAILED,
                                                String.valueOf(entryDN)));

                  break compareProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, de);
                }

                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                break compareProcessing;
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break compareProcessing;
              }


              ProxiedAuthV1Control proxyControl;
              if (c instanceof ProxiedAuthV1Control)
              {
                proxyControl = (ProxiedAuthV1Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV1Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    debugCought(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break compareProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break compareProcessing;
              }


              setAuthorizationEntry(authorizationEntry);
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
              // The requester must have the PROXIED_AUTH privilige in order to
              // be able to use this control.
              if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
              {
                int msgID = MSGID_PROXYAUTH_INSUFFICIENT_PRIVILEGES;
                appendErrorMessage(getMessage(msgID));
                setResultCode(ResultCode.AUTHORIZATION_DENIED);
                break compareProcessing;
              }


              ProxiedAuthV2Control proxyControl;
              if (c instanceof ProxiedAuthV2Control)
              {
                proxyControl = (ProxiedAuthV2Control) c;
              }
              else
              {
                try
                {
                  proxyControl = ProxiedAuthV2Control.decodeControl(c);
                }
                catch (LDAPException le)
                {
                  if (debugEnabled())
                  {
                    debugCought(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break compareProcessing;
                }
              }


              Entry authorizationEntry;
              try
              {
                authorizationEntry = proxyControl.getAuthorizationEntry();
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  debugCought(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break compareProcessing;
              }


              setAuthorizationEntry(authorizationEntry);
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                int msgID = MSGID_COMPARE_UNSUPPORTED_CRITICAL_CONTROL;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              oid));

                break compareProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the
        // compare.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this) == false) {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          int msgID = MSGID_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
          appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

          skipPostOperation = true;
          break compareProcessing;
        }

        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logCompareResponse(this);
          return;
        }


        // Invoke the pre-operation compare plugins.
        PreOperationPluginResult preOpResult =
             pluginConfigManager.invokePreOperationComparePlugins(this);
        if (preOpResult.connectionTerminated())
        {
          // There's no point in continuing with anything.  Log the request and
          // result and return.
          setResultCode(ResultCode.CANCELED);

          int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
          appendErrorMessage(getMessage(msgID));

          processingStopTime = System.currentTimeMillis();
          logCompareResponse(this);
          return;
        }
        else if (preOpResult.sendResponseImmediately())
        {
          skipPostOperation = true;
          break compareProcessing;
        }


        // Get the base attribute type and set of options.
        String          baseName;
        HashSet<String> options;
        int             semicolonPos = rawAttributeType.indexOf(';');
        if (semicolonPos > 0)
        {
          baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

          options = new HashSet<String>();
          int nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          while (nextPos > 0)
          {
            options.add(rawAttributeType.substring(semicolonPos+1, nextPos));
            semicolonPos = nextPos;
            nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          }

          options.add(rawAttributeType.substring(semicolonPos+1));
        }
        else
        {
          baseName = toLowerCase(rawAttributeType);
          options  = null;
        }


        // Actually perform the compare operation.
        List<Attribute> attrList = null;
        if (attributeType == null)
        {
          attributeType = DirectoryServer.getAttributeType(baseName);
        }
        if (attributeType == null)
        {
          attrList = entry.getAttribute(baseName, options);
          attributeType = DirectoryServer.getDefaultAttributeType(baseName);
        }
        else
        {
          attrList = entry.getAttribute(attributeType, options);
        }

        if ((attrList == null) || attrList.isEmpty())
        {
          setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
          if (options == null)
          {
            appendErrorMessage(getMessage(MSGID_COMPARE_OP_NO_SUCH_ATTR,
                                          String.valueOf(entryDN), baseName));
          }
          else
          {
            appendErrorMessage(getMessage(
                                    MSGID_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS,
                                    String.valueOf(entryDN), baseName));
          }
        }
        else
        {
          AttributeValue value = new AttributeValue(attributeType,
                                                    assertionValue);

          boolean matchFound = false;
          for (Attribute a : attrList)
          {
            if (a.hasValue(value))
            {
              matchFound = true;
              break;
            }
          }

          if (matchFound)
          {
            setResultCode(ResultCode.COMPARE_TRUE);
          }
          else
          {
            setResultCode(ResultCode.COMPARE_FALSE);
          }
        }
      }
      finally
      {
        LockManager.unlock(entryDN, readLock);
      }
    }


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      logCompareResponse(this);
      return;
    }


    // Invoke the post-operation compare plugins.
    if (! skipPostOperation)
    {
      PostOperationPluginResult postOperationResult =
           pluginConfigManager.invokePostOperationComparePlugins(this);
      if (postOperationResult.connectionTerminated())
      {
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_POSTOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();
        logCompareResponse(this);
        return;
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the compare response to the client.
    clientConnection.sendResponse(this);


    // Log the compare response message.
    logCompareResponse(this);


    // Invoke the post-response compare plugins.
    pluginConfigManager.invokePostResponseComparePlugins(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelResult cancel(CancelRequest cancelRequest)
  {

    this.cancelRequest = cancelRequest;

    CancelResult cancelResult = getCancelResult();
    long stopWaitingTime = System.currentTimeMillis() + 5000;
    while ((cancelResult == null) &&
           (System.currentTimeMillis() < stopWaitingTime))
    {
      try
      {
        Thread.sleep(50);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }

      cancelResult = getCancelResult();
    }

    if (cancelResult == null)
    {
      // This can happen in some rare cases (e.g., if a client disconnects and
      // there is still a lot of data to send to that client), and in this case
      // we'll prevent the cancel thread from blocking for a long period of
      // time.
      cancelResult = CancelResult.CANNOT_CANCEL;
    }

    return cancelResult;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final CancelRequest getCancelRequest()
  {

    return cancelRequest;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  boolean setCancelRequest(CancelRequest cancelRequest)
  {

    this.cancelRequest = cancelRequest;
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final void toString(StringBuilder buffer)
  {

    buffer.append("CompareOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(", attr=");
    buffer.append(rawAttributeType);
    buffer.append(")");
  }
}

