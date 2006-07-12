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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;

import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to modify an entry in the
 * Directory Server.
 */
public class ModifyOperation
       extends Operation
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.ModifyOperation";



  // The raw, unprocessed entry DN as included by the client request.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this modify operation.
  private CancelRequest cancelRequest;

  // The DN of the entry for the modify operation.
  private DN entryDN;

  // The modified entry that will be stored in the backend.
  private Entry modifiedEntry;

  // The set of response controls for this modify operation.
  private List<Control> responseControls;

  // The raw, unprocessed set of modifications as included in the client
  // request.
  private List<LDAPModification> rawModifications;

  // The set of modifications for this modify operation.
  private List<Modification> modifications;

  // The change number that has been assigned to this operation.
  private long changeNumber;

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;



  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to modify,
   *                           as included in the client request.
   * @param  rawModifications  The raw, unprocessed set of modifications for
   *                           this modify operation as included in the client
   *                           request.
   */
  public ModifyOperation(ClientConnection clientConnection, long operationID,
                         int messageID, List<Control> requestControls,
                         ByteString rawEntryDN,
                         List<LDAPModification> rawModifications)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(operationID),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(rawEntryDN),
                              String.valueOf(rawModifications)
                            });

    this.rawEntryDN       = rawEntryDN;
    this.rawModifications = rawModifications;

    entryDN          = null;
    modifications    = null;
    modifiedEntry    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
  }



  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for the modify operation.
   * @param  modifications     The set of modifications for this modify
   *                           operation.
   */
  public ModifyOperation(ClientConnection clientConnection, long operationID,
                         int messageID, List<Control> requestControls,
                         DN entryDN, List<Modification> modifications)
  {
    super(clientConnection, operationID, messageID, requestControls);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(clientConnection),
                              String.valueOf(operationID),
                              String.valueOf(messageID),
                              String.valueOf(requestControls),
                              String.valueOf(rawEntryDN),
                              String.valueOf(rawModifications)
                            });

    this.entryDN       = entryDN;
    this.modifications = modifications;

    rawEntryDN = new ASN1OctetString(entryDN.toString());

    rawModifications = new ArrayList<LDAPModification>(modifications.size());
    for (Modification m : modifications)
    {
      rawModifications.add(new LDAPModification(m.getModificationType(),
                                    new LDAPAttribute(m.getAttribute())));
    }

    modifiedEntry    = null;
    responseControls = new ArrayList<Control>();
    cancelRequest    = null;
    changeNumber     = -1;
  }



  /**
   * Retrieves the raw, unprocessed entry DN as included in the client request.
   * The DN that is returned may or may not be a valid DN, since no validation
   * will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client request.
   */
  public ByteString getRawEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getRawEntryDN");

    return rawEntryDN;
  }



  /**
   * Specifies the raw, unprocessed entry DN as included in the client request.
   * This should only be called by pre-parse plugins.  All other code that needs
   * to set the entry DN should use the <CODE>setEntryDN</CODE> method.
   *
   * @param  rawEntryDN  The raw, unprocessed entry DN as included in the client
   *                     request.
   */
  public void setRawEntryDN(ByteString rawEntryDN)
  {
    assert debugEnter(CLASS_NAME, "setRawEntryDN");

    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * Retrieves the DN of the entry to modify.  This should not be called by
   * pre-parse plugins because the processed DN will not be available yet.
   * Instead, they should call the <CODE>getRawEntryDN</CODE> method.
   *
   * @return  The DN of the entry to modify, or <CODE>null</CODE> if the raw
   *          entry DN has not yet been processed.
   */
  public DN getEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getEntryDN");

    return entryDN;
  }



  /**
   * Specifies the DN of the entry to modify.  This should not be called by
   * pre-parse plugins, since they should use <CODE>setRawEntryDN</CODE>
   * instead.
   *
   * @param  entryDN  The DN of the entry to modify.
   */
  public void setEntryDN(DN entryDN)
  {
    assert debugEnter(CLASS_NAME, "setEntryDN", String.valueOf(entryDN));

    this.entryDN = entryDN;
  }



  /**
   * Retrieves the set of raw, unprocessed modifications as included in the
   * client request.  Note that this may contain one or more invalid
   * modifications, as no validation will have been performed on this
   * information.  The list returned must not be altered by the caller.
   *
   * @return  The set of raw, unprocessed modifications as included in the
   *          client request.
   */
  public List<LDAPModification> getRawModifications()
  {
    assert debugEnter(CLASS_NAME, "getRawModifications");

    return rawModifications;
  }



  /**
   * Adds the provided modification to the set of raw modifications for this
   * modify operation.  This must only be called by pre-parse plugins.
   *
   * @param  rawModification  The modification to add to the set of raw
   *                          modifications for this modify operation.
   */
  public void addRawModification(LDAPModification rawModification)
  {
    assert debugEnter(CLASS_NAME, "addRawModification",
                      String.valueOf(rawModification));

    rawModifications.add(rawModification);

    modifications = null;
  }



  /**
   * Specifies the raw modifications for this modify operation.
   *
   * @param  rawModifications  The raw modifications for this modify operation.
   */
  public void setRawModifications(List<LDAPModification> rawModifications)
  {
    assert debugEnter(CLASS_NAME, "setRawModifications",
                      String.valueOf(rawModifications));

    this.rawModifications = rawModifications;

    modifications = null;
  }



  /**
   * Retrieves the set of modifications for this modify operation.  Its
   * contents may be altered in pre-operation plugins.  It must not be accessed
   * by pre-parse plugins, which should use the <CODE>getRawModifications</CODE>
   * method instead.
   *
   * @return  The set of modifications for this modify operation, or
   *          <CODE>null</CODE> if the modifications have not yet been
   *          processed.
   */
  public List<Modification> getModifications()
  {
    assert debugEnter(CLASS_NAME, "getModifications");

    return modifications;
  }



  /**
   * Retrieves the modified entry that is to be written to the backend.  This
   * will be available to pre-operation plugins, and if such a plugin does make
   * a change to this entry, then it is also necessary to add that change to
   * the set of modifications to ensure that the update will be consistent.
   *
   * @return  The modified entry that is to be written to the backend, or
   *          <CODE>null</CODE> if it is not yet available.
   */
  public Entry getModifiedEntry()
  {
    assert debugEnter(CLASS_NAME, "getModifiedEntry");

    return modifiedEntry;
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
   * Retrieves the change number that has been assigned to this operation.
   *
   * @return  The change number that has been assigned to this operation, or -1
   *          if none has been assigned yet or if there is no applicable
   *          synchronization mechanism in place that uses change numbers.
   */
  public long getChangeNumber()
  {
    assert debugEnter(CLASS_NAME, "getChangeNumber");

    return changeNumber;
  }



  /**
   * Specifies the change number that has been assigned to this operation by the
   * synchronization mechanism.
   *
   * @param  changeNumber  The change number that has been assigned to this
   *                       operation by the synchronization mechanism.
   */
  public void setChangeNumber(long changeNumber)
  {
    assert debugEnter(CLASS_NAME, "setChangeNumber",
                      String.valueOf(changeNumber));

    this.changeNumber = changeNumber;
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

    return OperationType.MODIFY;
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

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
    };
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

    setResultCode(ResultCode.UNDEFINED);
    Entry currentEntry = null;


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      setCancelResult(CancelResult.CANCELED);

      if (cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        setResultCode(ResultCode.CANCELED);

        String cancelReason = cancelRequest.getCancelReason();
        if (cancelReason != null)
        {
          appendErrorMessage(cancelReason);
        }

        clientConnection.sendResponse(this);
      }

      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
modifyProcessing:
    {
      // Invoke the pre-parse modify plugins.
      PreParsePluginResult preParseResult =
           pluginConfigManager.invokePreParseModifyPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logModifyRequest(this);
        logModifyResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logModifyRequest(this);
        break modifyProcessing;
      }


      // Log the modify request message.
      logModifyRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        setCancelResult(CancelResult.CANCELED);

        if (cancelRequest.notifyOriginalRequestor() ||
            DirectoryServer.notifyAbandonedOperations())
        {
          setResultCode(ResultCode.CANCELED);

          String cancelReason = cancelRequest.getCancelReason();
          if (cancelReason != null)
          {
            appendErrorMessage(cancelReason);
          }

          clientConnection.sendResponse(this);
        }

        processingStopTime = System.currentTimeMillis();
        logModifyResponse(this);
        return;
      }


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the modify processing.
      try
      {
        if (entryDN == null)
        {
          entryDN = DN.decode(rawEntryDN);
        }
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "run", de);

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getErrorMessage());
        skipPostOperation = true;

        break modifyProcessing;
      }


      // Process the modifications to convert them from their raw form to the
      // form required for the rest of the modify processing.
      if (modifications == null)
      {
        modifications = new ArrayList<Modification>(rawModifications.size());
        for (LDAPModification m : rawModifications)
        {
          try
          {
            modifications.add(m.toModification());
          }
          catch (LDAPException le)
          {
            assert debugException(CLASS_NAME, "run", le);

            setResultCode(ResultCode.valueOf(le.getResultCode()));
            appendErrorMessage(le.getMessage());

            break modifyProcessing;
          }
        }
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        setCancelResult(CancelResult.CANCELED);

        if (cancelRequest.notifyOriginalRequestor() ||
            DirectoryServer.notifyAbandonedOperations())
        {
          setResultCode(ResultCode.CANCELED);

          String cancelReason = cancelRequest.getCancelReason();
          if (cancelReason != null)
          {
            appendErrorMessage(cancelReason);
          }

          clientConnection.sendResponse(this);
        }

        processingStopTime = System.currentTimeMillis();
        logModifyResponse(this);
        return;
      }


      // Acquire a write lock on the target entry.
      Lock entryLock = null;
      for (int i=0; i < 3; i++)
      {
        entryLock = LockManager.lockWrite(entryDN);
        if (entryLock != null)
        {
          break;
        }
      }

      if (entryLock == null)
      {
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(getMessage(MSGID_MODIFY_CANNOT_LOCK_ENTRY,
                                      String.valueOf(entryDN)));

        skipPostOperation = true;
        break modifyProcessing;
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        setCancelResult(CancelResult.CANCELED);

        if (cancelRequest.notifyOriginalRequestor() ||
            DirectoryServer.notifyAbandonedOperations())
        {
          setResultCode(ResultCode.CANCELED);

          String cancelReason = cancelRequest.getCancelReason();
          if (cancelReason != null)
          {
            appendErrorMessage(cancelReason);
          }

          clientConnection.sendResponse(this);
        }

        processingStopTime = System.currentTimeMillis();
        logModifyResponse(this);
        return;
      }


      try
      {
        // Get the entry to modify.  If it does not exist, then fail.
        try
        {
          currentEntry = DirectoryServer.getEntry(entryDN);
        }
        catch (DirectoryException de)
        {
          assert debugException(CLASS_NAME, "run", de);

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());

          break modifyProcessing;
        }

        if (currentEntry == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(getMessage(MSGID_MODIFY_NO_SUCH_ENTRY,
                                        String.valueOf(entryDN)));

          // See if one of the entry's ancestors exists.
          DN parentDN = entryDN.getParent();
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
              assert debugException(CLASS_NAME, "run", e);
              break;
            }

            parentDN = parentDN.getParent();
          }

          break modifyProcessing;
        }

        // Check to see if the client has permission to perform the
        // modify.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists may
        // have already exposed sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this) == false) {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          int msgID = MSGID_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
          appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

          skipPostOperation = true;
          break modifyProcessing;
        }

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        boolean                    noOp            = false;
        LDAPPreReadRequestControl  preReadRequest  = null;
        LDAPPostReadRequestControl postReadRequest = null;
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
                  assert debugException(CLASS_NAME, "run", le);

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyProcessing;
                }
              }

              SearchFilter filter = assertControl.getSearchFilter();
              try
              {
                // FIXME -- We need to determine whether the current user has
                //          permission to make this determination.
                if (! filter.matchesEntry(currentEntry))
                {
                  setResultCode(ResultCode.ASSERTION_FAILED);

                  appendErrorMessage(getMessage(MSGID_MODIFY_ASSERTION_FAILED,
                                                String.valueOf(entryDN)));

                  break modifyProcessing;
                }
              }
              catch (DirectoryException de)
              {
                assert debugException(CLASS_NAME, "run", de);

                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                break modifyProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
            }
            else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
            {
              if (c instanceof LDAPAssertionRequestControl)
              {
                preReadRequest = (LDAPPreReadRequestControl) c;
              }
              else
              {
                try
                {
                  preReadRequest = LDAPPreReadRequestControl.decodeControl(c);
                  requestControls.set(i, preReadRequest);
                }
                catch (LDAPException le)
                {
                  assert debugException(CLASS_NAME, "run", le);

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyProcessing;
                }
              }
            }
            else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
            {
              if (c instanceof LDAPAssertionRequestControl)
              {
                postReadRequest = (LDAPPostReadRequestControl) c;
              }
              else
              {
                try
                {
                  postReadRequest = LDAPPostReadRequestControl.decodeControl(c);
                  requestControls.set(i, postReadRequest);
                }
                catch (LDAPException le)
                {
                  assert debugException(CLASS_NAME, "run", le);

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyProcessing;
                }
              }
            }
            else if (oid.equals(OID_PROXIED_AUTH_V1))
            {
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
                  assert debugException(CLASS_NAME, "run", le);

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyProcessing;
                }
              }


              DN authzDN;
              try
              {
                authzDN = proxyControl.getValidatedAuthorizationDN();
              }
              catch (DirectoryException de)
              {
                assert debugException(CLASS_NAME, "run", de);

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break modifyProcessing;
              }


              // FIXME -- Should we specifically check permissions here, or let
              //          the earlier access control checks handle it?
              setAuthorizationDN(authzDN);
            }
            else if (oid.equals(OID_PROXIED_AUTH_V2))
            {
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
                  assert debugException(CLASS_NAME, "run", le);

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break modifyProcessing;
                }
              }


              DN authzDN;
              try
              {
                authzDN = proxyControl.getValidatedAuthorizationDN();
              }
              catch (DirectoryException de)
              {
                assert debugException(CLASS_NAME, "run", de);

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break modifyProcessing;
              }


              // FIXME -- Should we specifically check permissions here, or let
              //          the earlier access control checks handle it?
              setAuthorizationDN(authzDN);
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                int msgID = MSGID_MODIFY_UNSUPPORTED_CRITICAL_CONTROL;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              oid));

                break modifyProcessing;
              }
            }
          }
        }


        // Create a duplicate of the entry and apply the changes to it.
        modifiedEntry = currentEntry.duplicate();

        if (! noOp)
        {
          // Invoke any conflict resolution processing that might be needed by
          // the synchronization provider.
          for (SynchronizationProvider provider :
               DirectoryServer.getSynchronizationProviders())
          {
            try
            {
              SynchronizationProviderResult result =
                   provider.handleConflictResolution(this);
              if (! result.continueOperationProcessing())
              {
                break modifyProcessing;
              }
            }
            catch (DirectoryException de)
            {
              assert debugException(CLASS_NAME, "run", de);

              logError(ErrorLogCategory.SYNCHRONIZATION,
                       ErrorLogSeverity.SEVERE_ERROR,
                       MSGID_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED,
                       getConnectionID(), getOperationID(),
                       stackTraceToSingleLineString(de));

              setResponseData(de);
              break modifyProcessing;
            }
          }
        }

        for (Modification m : modifications)
        {
          Attribute     a = m.getAttribute();
          AttributeType t = a.getAttributeType();


          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          if (t.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(getMessage(MSGID_MODIFY_ATTR_IS_NO_USER_MOD,
                                            String.valueOf(entryDN),
                                            a.getName()));
              break modifyProcessing;
            }
          }


          switch (m.getModificationType())
          {
            case ADD:
              // Make sure that one or more values have been provided for the
              // attribute.
              LinkedHashSet<AttributeValue> newValues = a.getValues();
              if ((newValues == null) || newValues.isEmpty())
              {
                setResultCode(ResultCode.PROTOCOL_ERROR);
                appendErrorMessage(getMessage(MSGID_MODIFY_ADD_NO_VALUES,
                                              String.valueOf(entryDN),
                                              a.getName()));
                break modifyProcessing;
              }


              // Make sure that all the new values are valid according to the
              // associated syntax.
              if (DirectoryServer.checkSchema())
              {
                AcceptRejectWarn syntaxPolicy =
                     DirectoryServer.getSyntaxEnforcementPolicy();
                AttributeSyntax syntax = t.getSyntax();

                if (syntaxPolicy == AcceptRejectWarn.REJECT)
                {
                  StringBuilder invalidReason = new StringBuilder();

                  for (AttributeValue v : newValues)
                  {
                    if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                    {
                      setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                      int msgID = MSGID_MODIFY_ADD_INVALID_SYNTAX;
                      appendErrorMessage(getMessage(msgID,
                                                    String.valueOf(entryDN),
                                                    a.getName(),
                                                    v.getStringValue(),
                                                    invalidReason.toString()));

                      break modifyProcessing;
                    }
                  }
                }
                else if (syntaxPolicy == AcceptRejectWarn.WARN)
                {
                  StringBuilder invalidReason = new StringBuilder();

                  for (AttributeValue v : newValues)
                  {
                    if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                    {
                      setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                      int msgID = MSGID_MODIFY_ADD_INVALID_SYNTAX;
                      logError(ErrorLogCategory.SCHEMA,
                               ErrorLogSeverity.SEVERE_WARNING, msgID,
                               String.valueOf(entryDN), a.getName(),
                               v.getStringValue(), invalidReason.toString());

                      invalidReason = new StringBuilder();
                    }
                  }
                }
              }


              // Add the provided attribute or merge an existing attribute with
              // the values of the new attribute.  If there are any duplicates,
              // then fail.
              LinkedList<AttributeValue> duplicateValues =
                   new LinkedList<AttributeValue>();
              if (a.getAttributeType().isObjectClassType())
              {
                try
                {
                  modifiedEntry.addObjectClasses(newValues);
                  break;
                }
                catch (DirectoryException de)
                {
                  assert debugException(CLASS_NAME, "run", de);

                  setResponseData(de);
                  break modifyProcessing;
                }
              }
              else
              {
                modifiedEntry.addAttribute(a, duplicateValues);
                if (duplicateValues.isEmpty())
                {
                  break;
                }
                else
                {
                  StringBuilder buffer = new StringBuilder();
                  Iterator<AttributeValue> iterator =
                       duplicateValues.iterator();
                  buffer.append(iterator.next().getStringValue());
                  while (iterator.hasNext())
                  {
                    buffer.append(", ");
                    buffer.append(iterator.next().getStringValue());
                  }

                  setResultCode(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS);

                  int msgID = MSGID_MODIFY_ADD_DUPLICATE_VALUE;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName(),
                                                buffer.toString()));

                  break modifyProcessing;
                }
              }


            case DELETE:
              // Remove the specified attribute values or the entire attribute
              // from the value.  If there are any specified values that were
              // not present, then fail.  If the RDN attribute value would be
              // removed, then fail.
              LinkedList<AttributeValue> missingValues =
                   new LinkedList<AttributeValue>();
              boolean attrExists =
                   modifiedEntry.removeAttribute(a, missingValues);

              if (attrExists)
              {
                if (missingValues.isEmpty())
                {
                  RDN rdn = modifiedEntry.getDN().getRDN();
                  if (rdn.hasAttributeType(t) &&
                      (! modifiedEntry.hasValue(t, a.getOptions(),
                                                rdn.getAttributeValue(t))))
                  {
                    setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                    int msgID = MSGID_MODIFY_DELETE_RDN_ATTR;
                    appendErrorMessage(getMessage(msgID,
                                                  String.valueOf(entryDN),
                                                  a.getName()));
                    break modifyProcessing;
                  }

                  break;
                }
                else
                {
                  StringBuilder buffer = new StringBuilder();
                  Iterator<AttributeValue> iterator = missingValues.iterator();
                  buffer.append(iterator.next().getStringValue());
                  while (iterator.hasNext())
                  {
                    buffer.append(", ");
                    buffer.append(iterator.next().getStringValue());
                  }

                  setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                  int msgID = MSGID_MODIFY_DELETE_MISSING_VALUES;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName(),
                                                buffer.toString()));

                  break modifyProcessing;
                }
              }
              else
              {
                setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);

                int msgID = MSGID_MODIFY_DELETE_NO_SUCH_ATTR;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));
                break modifyProcessing;
              }


            case REPLACE:
              // If it is the objectclass attribute, then treat that separately.
              if (a.getAttributeType().isObjectClassType())
              {
                try
                {
                  modifiedEntry.setObjectClasses(a.getValues());
                  break;
                }
                catch (DirectoryException de)
                {
                  assert debugException(CLASS_NAME, "run", de);

                  setResponseData(de);
                  break modifyProcessing;
                }
              }


              // If the provided attribute does not have any values, then we
              // will simply remove the attribute from the entry (if it exists).
              if (! a.hasValue())
              {
                modifiedEntry.removeAttribute(t, a.getOptions());
                RDN rdn = modifiedEntry.getDN().getRDN();
                if (rdn.hasAttributeType(t) &&
                    (! modifiedEntry.hasValue(t, a.getOptions(),
                                              rdn.getAttributeValue(t))))
                {
                  setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                  int msgID = MSGID_MODIFY_DELETE_RDN_ATTR;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));
                  break modifyProcessing;
                }
                break;
              }


              // Make sure that all the new values are valid according to the
              // associated syntax.
              newValues = a.getValues();
              if (DirectoryServer.checkSchema())
              {
                AcceptRejectWarn syntaxPolicy =
                     DirectoryServer.getSyntaxEnforcementPolicy();
                AttributeSyntax syntax = t.getSyntax();

                if (syntaxPolicy == AcceptRejectWarn.REJECT)
                {
                  StringBuilder invalidReason = new StringBuilder();

                  for (AttributeValue v : newValues)
                  {
                    if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                    {
                      setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                      int msgID = MSGID_MODIFY_REPLACE_INVALID_SYNTAX;
                      appendErrorMessage(getMessage(msgID,
                                                    String.valueOf(entryDN),
                                                    a.getName(),
                                                    v.getStringValue(),
                                                    invalidReason.toString()));

                      break modifyProcessing;
                    }
                  }
                }
                else if (syntaxPolicy == AcceptRejectWarn.WARN)
                {
                  StringBuilder invalidReason = new StringBuilder();

                  for (AttributeValue v : newValues)
                  {
                    if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                    {
                      setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                      int msgID = MSGID_MODIFY_REPLACE_INVALID_SYNTAX;
                      logError(ErrorLogCategory.SCHEMA,
                               ErrorLogSeverity.SEVERE_WARNING, msgID,
                               String.valueOf(entryDN), a.getName(),
                               v.getStringValue(), invalidReason.toString());

                      invalidReason = new StringBuilder();
                    }
                  }
                }
              }


              // If the provided attribute does not have any options, then we
              // will simply use it in place of any existing attribute of the
              // provided type (or add it if it doesn't exist).
              if (! a.hasOptions())
              {
                List<Attribute> attrList = new ArrayList<Attribute>(1);
                attrList.add(a);
                modifiedEntry.putAttribute(t, attrList);

                RDN rdn = modifiedEntry.getDN().getRDN();
                if (rdn.hasAttributeType(t) &&
                    (! modifiedEntry.hasValue(t, a.getOptions(),
                                              rdn.getAttributeValue(t))))
                {
                  setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                  int msgID = MSGID_MODIFY_DELETE_RDN_ATTR;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));
                  break modifyProcessing;
                }
                break;
              }


              // See if there is an existing attribute of the provided type.  If
              // not, then we'll use the new one.
              List<Attribute> attrList = modifiedEntry.getAttribute(t);
              if ((attrList == null) || attrList.isEmpty())
              {
                attrList = new ArrayList<Attribute>(1);
                attrList.add(a);
                modifiedEntry.putAttribute(t, attrList);

                RDN rdn = modifiedEntry.getDN().getRDN();
                if (rdn.hasAttributeType(t) &&
                    (! modifiedEntry.hasValue(t, a.getOptions(),
                                              rdn.getAttributeValue(t))))
                {
                  setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                  int msgID = MSGID_MODIFY_DELETE_RDN_ATTR;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                a.getName()));
                  break modifyProcessing;
                }
                break;
              }


              // There must be an existing occurrence of the provided attribute
              // in the entry.  If there is a version with exactly the set of
              // options provided, then replace it.  Otherwise, add a new one.
              boolean found = false;
              for (int i=0; i < attrList.size(); i++)
              {
                if (attrList.get(i).optionsEqual(a.getOptions()))
                {
                  attrList.set(i, a);
                  found = true;
                  break;
                }
              }

              if (! found)
              {
                attrList.add(a);
              }

              RDN rdn = modifiedEntry.getDN().getRDN();
              if (rdn.hasAttributeType(t) &&
                  (! modifiedEntry.hasValue(t, a.getOptions(),
                                            rdn.getAttributeValue(t))))
              {
                setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);

                int msgID = MSGID_MODIFY_DELETE_RDN_ATTR;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));
                break modifyProcessing;
              }
              break;


            case INCREMENT:
              // The specified attribute type must not be an RDN attribute.
              rdn = modifiedEntry.getDN().getRDN();
              if (rdn.hasAttributeType(t))
              {
                setResultCode(ResultCode.NOT_ALLOWED_ON_RDN);
                appendErrorMessage(getMessage(MSGID_MODIFY_INCREMENT_RDN,
                                              String.valueOf(entryDN),
                                              a.getName()));
              }


              // The provided attribute must have a single value, and it must be
              // an integer.
              LinkedHashSet<AttributeValue> values = a.getValues();
              if ((values == null) || values.isEmpty())
              {
                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_MODIFY_INCREMENT_REQUIRES_VALUE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));

                break modifyProcessing;
              }
              else if (values.size() > 1)
              {
                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));
              }

              AttributeValue v = values.iterator().next();

              long incrementValue;
              try
              {
                incrementValue = Long.parseLong(v.getNormalizedStringValue());
              }
              catch (Exception e)
              {
                assert debugException(CLASS_NAME, "run", e);

                setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                int msgID = MSGID_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName(), v.getStringValue()));

                break modifyProcessing;
              }


              // Get the corresponding attribute from the entry and make sure
              // that it has a single integer value.
              attrList = modifiedEntry.getAttribute(t, a.getOptions());
              if ((attrList == null) || attrList.isEmpty())
              {
                setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                int msgID = MSGID_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));

                break modifyProcessing;
              }

              boolean updated = false;
              for (Attribute attr : attrList)
              {
                LinkedHashSet<AttributeValue> valueList = attr.getValues();
                if ((valueList == null) || valueList.isEmpty())
                {
                  continue;
                }

                LinkedHashSet<AttributeValue> newValueList =
                     new LinkedHashSet<AttributeValue>(valueList.size());
                for (AttributeValue existingValue : valueList)
                {
                  long newIntValue;
                  try
                  {
                    long existingIntValue =
                         Long.parseLong(existingValue.getStringValue());
                    newIntValue = existingIntValue + incrementValue;
                  }
                  catch (Exception e)
                  {
                    assert debugException(CLASS_NAME, "run", e);

                    setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);

                    int msgID = MSGID_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE;
                    appendErrorMessage(getMessage(msgID,
                                            String.valueOf(entryDN),
                                            a.getName(),
                                            existingValue.getStringValue()));
                    break modifyProcessing;
                  }

                  ByteString newValue =
                       new ASN1OctetString(String.valueOf(newIntValue));
                  newValueList.add(new AttributeValue(t, newValue));
                }

                attr.setValues(newValueList);
                updated = true;
              }

              if (! updated)
              {
                setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                int msgID = MSGID_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              a.getName()));

                break modifyProcessing;
              }

              break;

            default:
          }
        }


        // Make sure that the new entry is valid per the server schema.
        if (DirectoryServer.checkSchema())
        {
          StringBuilder invalidReason = new StringBuilder();
          if (! modifiedEntry.conformsToSchema(null, false, invalidReason))
          {
            setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            appendErrorMessage(getMessage(MSGID_MODIFY_VIOLATES_SCHEMA,
                                          String.valueOf(entryDN),
                                          invalidReason.toString()));
            break modifyProcessing;
          }
        }


        // Check to see if there are any passwords included in the request and
        // if they are valid in accordance with the password policies associated
        // with the user.  Also perform any encoding that might be required by
        // the password storage schemes.
        // NYI


        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          setCancelResult(CancelResult.CANCELED);

          if (cancelRequest.notifyOriginalRequestor() ||
              DirectoryServer.notifyAbandonedOperations())
          {
            setResultCode(ResultCode.CANCELED);

            String cancelReason = cancelRequest.getCancelReason();
            if (cancelReason != null)
            {
              appendErrorMessage(cancelReason);
            }

            clientConnection.sendResponse(this);
          }

          processingStopTime = System.currentTimeMillis();
          logModifyResponse(this);
          return;
        }

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify plugins.
        if (!isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationModifyPlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            setResultCode(ResultCode.CANCELED);

            int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
            appendErrorMessage(getMessage(msgID));

            processingStopTime = System.currentTimeMillis();

            logModifyResponse(this);
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break modifyProcessing;
          }
        }


        // Actually perform the modify operation.  This should also include
        // taking care of any synchronization that might be needed.
        Backend backend = DirectoryServer.getBackend(entryDN);
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(getMessage(MSGID_MODIFY_NO_BACKEND_FOR_ENTRY,
                                        String.valueOf(entryDN)));
          break modifyProcessing;
        }

        try
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! backend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_MODIFY_SERVER_READONLY,
                                              String.valueOf(entryDN)));
                break modifyProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_MODIFY_SERVER_READONLY,
                                                String.valueOf(entryDN)));
                  break modifyProcessing;
                }
            }

            switch (backend.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_MODIFY_BACKEND_READONLY,
                                              String.valueOf(entryDN)));
                break modifyProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_MODIFY_BACKEND_READONLY,
                                                String.valueOf(entryDN)));
                  break modifyProcessing;
                }
            }
          }


          if (noOp)
          {
            appendErrorMessage(getMessage(MSGID_MODIFY_NOOP));

            // FIXME -- We must set a result code other than SUCCESS.
          }
          else
          {
            for (SynchronizationProvider provider :
                 DirectoryServer.getSynchronizationProviders())
            {
              try
              {
                SynchronizationProviderResult result =
                     provider.doPreOperation(this);
                if (! result.continueOperationProcessing())
                {
                  break modifyProcessing;
                }
              }
              catch (DirectoryException de)
              {
                assert debugException(CLASS_NAME, "run", de);

                logError(ErrorLogCategory.SYNCHRONIZATION,
                         ErrorLogSeverity.SEVERE_ERROR,
                         MSGID_MODIFY_SYNCH_PREOP_FAILED, getConnectionID(),
                         getOperationID(), stackTraceToSingleLineString(de));

                setResponseData(de);
                break modifyProcessing;
              }
            }

            backend.replaceEntry(modifiedEntry, this);
          }

          if (preReadRequest != null)
          {
            Entry entry = currentEntry.duplicate();

            if (! preReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! preReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! preReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! preReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPreReadResponseControl responseControl =
                 new LDAPPreReadResponseControl(preReadRequest.getOID(),
                                                preReadRequest.isCritical(),
                                                searchEntry);

            responseControls.add(responseControl);
          }

          if (postReadRequest != null)
          {
            Entry entry = modifiedEntry.duplicate();

            if (! postReadRequest.allowsAttribute(
                       DirectoryServer.getObjectClassAttributeType()))
            {
              entry.removeAttribute(
                   DirectoryServer.getObjectClassAttributeType());
            }

            if (! postReadRequest.returnAllUserAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getUserAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            if (! postReadRequest.returnAllOperationalAttributes())
            {
              Iterator<AttributeType> iterator =
                   entry.getOperationalAttributes().keySet().iterator();
              while (iterator.hasNext())
              {
                AttributeType attrType = iterator.next();
                if (! postReadRequest.allowsAttribute(attrType))
                {
                  iterator.remove();
                }
              }
            }

            // FIXME -- Check access controls on the entry to see if it should
            //          be returned or if any attributes need to be stripped
            //          out..
            SearchResultEntry searchEntry = new SearchResultEntry(entry);
            LDAPPostReadResponseControl responseControl =
                 new LDAPPostReadResponseControl(postReadRequest.getOID(),
                                                 postReadRequest.isCritical(),
                                                 searchEntry);

            responseControls.add(responseControl);
          }

          setResultCode(ResultCode.SUCCESS);
        }
        catch (DirectoryException de)
        {
          assert debugException(CLASS_NAME, "run", de);

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());

          break modifyProcessing;
        }
        catch (CancelledOperationException coe)
        {
          assert debugException(CLASS_NAME, "run", coe);

          CancelResult cancelResult = coe.getCancelResult();

          setCancelResult(cancelResult);
          setResultCode(cancelResult.getResultCode());

          String message = coe.getMessage();
          if ((message != null) && (message.length() > 0))
          {
            appendErrorMessage(message);
          }

          break modifyProcessing;
        }
      }
      finally
      {
        LockManager.unlock(entryDN, entryLock);

        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(this);
          }
          catch (DirectoryException de)
          {
            assert debugException(CLASS_NAME, "run", de);

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_MODIFY_SYNCH_POSTOP_FAILED, getConnectionID(),
                     getOperationID(), stackTraceToSingleLineString(de));

            setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation modify plugins.
    if (! skipPostOperation)
    {
      // FIXME -- Should this also be done while holding the locks?
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationModifyPlugins(this);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logModifyResponse(this);
        return;
      }
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the modify response to the client.
    clientConnection.sendResponse(this);


    // Log the modify response.
    logModifyResponse(this);


    // Notify any change listeners and/or persistent searches that might be
    // registered with the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleModifyOperation(this, currentEntry,
                                               modifiedEntry);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "run", e);

          int    msgID   = MSGID_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER;
          String message = getMessage(msgID, stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }

      for (PersistentSearch persistentSearch :
           DirectoryServer.getPersistentSearches())
      {
        try
        {
          persistentSearch.processModify(this, currentEntry, modifiedEntry);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "run", e);

          int    msgID   = MSGID_MODIFY_ERROR_NOTIFYING_PERSISTENT_SEARCH;
          String message = getMessage(msgID, String.valueOf(persistentSearch),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);

          DirectoryServer.deregisterPersistentSearch(persistentSearch);
        }
      }
    }


    // Invoke the post-response modify plugins.
    pluginConfigManager.invokePostResponseModifyPlugins(this);
  }



  /**
   * Attempts to cancel this operation before processing has completed.
   *
   * @param  cancelRequest  Information about the way in which the operation
   *                        should be canceled.
   *
   * @return  A code providing information on the result of the cancellation.
   */
  public CancelResult cancel(CancelRequest cancelRequest)
  {
    assert debugEnter(CLASS_NAME, "cancel", String.valueOf(cancelRequest));

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
        assert debugException(CLASS_NAME, "cancel", e);
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
   * Retrieves the cancel request that has been issued for this operation, if
   * there is one.
   *
   * @return  The cancel request that has been issued for this operation, or
   *          <CODE>null</CODE> if there has not been any request to cancel.
   */
  public CancelRequest getCancelRequest()
  {
    assert debugEnter(CLASS_NAME, "getCancelRequest");

    return cancelRequest;
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

    buffer.append("ModifyOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }
}

