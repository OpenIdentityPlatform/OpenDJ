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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import static org.opends.server.config.ConfigConstants.ATTR_OBJECTCLASS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ENTRY_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_ERROR_MESSAGE;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_MATCHED_DN;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_PROCESSING_TIME;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_REFERRAL_URLS;
import static org.opends.server.core.CoreConstants.LOG_ELEMENT_RESULT_CODE;
import static org.opends.server.loggers.AccessLogger.logAddRequest;
import static org.opends.server.loggers.AccessLogger.logAddResponse;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;
import static org.opends.server.util.StaticUtils.toLowerCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;




/**
 * This class defines an operation that may be used to add a new entry to the
 * Directory Server.
 */
public class AddOperationBasis
       extends AbstractOperation
       implements PreParseAddOperation, AddOperation, Runnable,
                  PostResponseAddOperation
{

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = DebugLogger.getTracer();

  // The set of response controls to send to the client.
  private ArrayList<Control> responseControls;

  // The raw, unprocessed entry DN as provided in the request.  This may or may
  // not be a valid DN.
  private ByteString rawEntryDN;

  // The cancel request that has been issued for this add operation.
  private CancelRequest cancelRequest;

  // The processed DN of the entry to add.
  private DN entryDN;

  // The proxied authorization target DN for this operation.
  private DN proxiedAuthorizationDN;

  // The set of attributes (including the objectclass attribute) in a raw,
  // unprocessed form as provided in the request.  One or more of these
  // attributes may be invalid.
  private List<RawAttribute> rawAttributes;

  // The set of operational attributes for the entry to add.
  private Map<AttributeType,List<Attribute>> operationalAttributes;

  // The set of user attributes for the entry to add.
  private Map<AttributeType,List<Attribute>> userAttributes;

  // The set of objectclasses for the entry to add.
  private Map<ObjectClass,String> objectClasses;

  // The change number that has been assigned to this operation.
  private long changeNumber;


  /**
   * Creates a new add operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw DN of the entry to add from the client
   *                           request.  This may or may not be a valid DN.
   * @param  rawAttributes     The raw set of attributes from the client
   *                           request (including the objectclass attribute).
   *                           This may contain invalid attributes.
   */
  public AddOperationBasis(ClientConnection clientConnection, long operationID,
                      int messageID, List<Control> requestControls,
                      ByteString rawEntryDN, List<RawAttribute> rawAttributes)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN    = rawEntryDN;
    this.rawAttributes = rawAttributes;

    responseControls      = new ArrayList<Control>();
    cancelRequest         = null;
    entryDN               = null;
    userAttributes        = null;
    operationalAttributes = null;
    objectClasses         = null;
    proxiedAuthorizationDN = null;
    changeNumber          = -1;
  }



  /**
   * Creates a new add operation with the provided information.
   *
   * @param  clientConnection       The client connection with which this
   *                                operation is associated.
   * @param  operationID            The operation ID for this operation.
   * @param  messageID              The message ID of the request with which
   *                                this operation is associated.
   * @param  requestControls        The set of controls included in the request.
   * @param  entryDN                The DN for the entry.
   * @param  objectClasses          The set of objectclasses for the entry.
   * @param  userAttributes         The set of user attributes for the entry.
   * @param  operationalAttributes  The set of operational attributes for the
   *                                entry.
   */
  public AddOperationBasis(ClientConnection clientConnection, long operationID,
                      int messageID, List<Control> requestControls,
                      DN entryDN, Map<ObjectClass,String> objectClasses,
                      Map<AttributeType,List<Attribute>> userAttributes,
                      Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN               = entryDN;
    this.objectClasses         = objectClasses;
    this.userAttributes        = userAttributes;
    this.operationalAttributes = operationalAttributes;

    rawEntryDN = new ASN1OctetString(entryDN.toString());

    rawAttributes = new ArrayList<RawAttribute>();

    ArrayList<ASN1OctetString> ocValues = new ArrayList<ASN1OctetString>();
    for (String s : objectClasses.values())
    {
      ocValues.add(new ASN1OctetString(s));
    }

    LDAPAttribute ocAttr = new LDAPAttribute(ATTR_OBJECTCLASS, ocValues);
    rawAttributes.add(ocAttr);

    for (List<Attribute> attrList : userAttributes.values())
    {
      for (Attribute a : attrList)
      {
        rawAttributes.add(new LDAPAttribute(a));
      }
    }

    for (List<Attribute> attrList : operationalAttributes.values())
    {
      for (Attribute a : attrList)
      {
        rawAttributes.add(new LDAPAttribute(a));
      }
    }

    responseControls = new ArrayList<Control>();
    proxiedAuthorizationDN = null;
    cancelRequest    = null;
    changeNumber     = -1;
  }


  /**
   * {@inheritDoc}
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }


  /**
   * {@inheritDoc}
   */
  public final DN getEntryDN()
  {
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
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      setResultCode(de.getResultCode());
      appendErrorMessage(de.getMessageObject());
      setMatchedDN(de.getMatchedDN());
      setReferralURLs(de.getReferralURLs());
    }
    return entryDN;
  }


  /**
   * {@inheritDoc}
   */
  public final List<RawAttribute> getRawAttributes()
  {
    return rawAttributes;
  }


  /**
   * {@inheritDoc}
   */
  public final void addRawAttribute(RawAttribute rawAttribute)
  {
    rawAttributes.add(rawAttribute);

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }


  /**
   * {@inheritDoc}
   */
  public final void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    this.rawAttributes = rawAttributes;

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }



  /**
   * {@inheritDoc}
   */
  public final Map<ObjectClass,String> getObjectClasses()
  {
    if (objectClasses == null){
      computeObjectClassesAndAttributes();
    }
    return objectClasses;
  }



  /**
   * {@inheritDoc}
   */
  public final void addObjectClass(ObjectClass objectClass, String name)
  {
    objectClasses.put(objectClass, name);
  }



  /**
   * {@inheritDoc}
   */
  public final void removeObjectClass(ObjectClass objectClass)
  {
    objectClasses.remove(objectClass);
  }



  /**
   * {@inheritDoc}
   */
  public final Map<AttributeType,List<Attribute>> getUserAttributes()
  {
    if (userAttributes == null){
      computeObjectClassesAndAttributes();
    }
    return userAttributes;
  }


  /**
   * {@inheritDoc}
   */
  public final Map<AttributeType,List<Attribute>> getOperationalAttributes()
  {
    if (operationalAttributes == null){
      computeObjectClassesAndAttributes();
    }
    return operationalAttributes;
  }

  /**
   * Build the objectclasses, the user attributes and the operational attributes
   * if there are not already computed.
   */
  private final void computeObjectClassesAndAttributes()
  {
    if ((objectClasses == null) || (userAttributes == null) ||
        (operationalAttributes == null))
    {
      objectClasses         = new HashMap<ObjectClass,String>();
      userAttributes        = new HashMap<AttributeType,List<Attribute>>();
      operationalAttributes = new HashMap<AttributeType,List<Attribute>>();

      for (RawAttribute a : rawAttributes)
      {
        try
        {
          Attribute attr = a.toAttribute();
          AttributeType attrType = attr.getAttributeType();

          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          if (attrType.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                      String.valueOf(entryDN),
                      attr.getName()));

              objectClasses = null;
              userAttributes = null;
              operationalAttributes = null;
              return;
            }
          }

          if (attrType.isObjectClassType())
          {
            for (ByteString os : a.getValues())
            {
              String ocName = os.toString();
              ObjectClass oc =
                DirectoryServer.getObjectClass(toLowerCase(ocName));
              if (oc == null)
              {
                oc = DirectoryServer.getDefaultObjectClass(ocName);
              }

              objectClasses.put(oc,ocName);
            }
          }
          else if (attrType.isOperational())
          {
            List<Attribute> attrs = operationalAttributes.get(attrType);
            if (attrs == null)
            {
              attrs = new ArrayList<Attribute>(1);
              attrs.add(attr);
              operationalAttributes.put(attrType, attrs);
            }
            else
            {
              attrs.add(attr);
            }
          }
          else
          {
            List<Attribute> attrs = userAttributes.get(attrType);
            if (attrs == null)
            {
              attrs = new ArrayList<Attribute>(1);
              attrs.add(attr);
              userAttributes.put(attrType, attrs);
            }
            else
            {
              // Check to see if any of the existing attributes in the list
              // have the same set of options.  If so, then add the values
              // to that attribute.
              boolean attributeSeen = false;
              for (Attribute ea : attrs)
              {
                if (ea.optionsEqual(attr.getOptions()))
                {
                  LinkedHashSet<AttributeValue> valueSet = ea.getValues();
                  valueSet.addAll(attr.getValues());
                  attributeSeen = true;
                }
              }
              if (!attributeSeen)
              {
                // This is the first occurrence of the attribute and options.
                attrs.add(attr);
              }
            }
          }
        }
        catch (LDAPException le)
        {
          setResultCode(ResultCode.valueOf(le.getResultCode()));
          appendErrorMessage(le.getMessageObject());

          objectClasses = null;
          userAttributes = null;
          operationalAttributes = null;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public final void setAttribute(AttributeType attributeType,
                                 List<Attribute> attributeList)
  {
    if (attributeType.isOperational())
    {
      if ((attributeList == null) || (attributeList.isEmpty()))
      {
        operationalAttributes.remove(attributeType);
      }
      else
      {
        operationalAttributes.put(attributeType, attributeList);
      }
    }
    else
    {
      if ((attributeList == null) || (attributeList.isEmpty()))
      {
        userAttributes.remove(attributeType);
      }
      else
      {
        userAttributes.put(attributeType, attributeList);
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  public final void removeAttribute(AttributeType attributeType)
  {
    if (attributeType.isOperational())
    {
      operationalAttributes.remove(attributeType);
    }
    else
    {
      userAttributes.remove(attributeType);
    }
  }

  /**
   * {@inheritDoc}
   */
  public final long getChangeNumber()
  {
    return changeNumber;
  }


  /**
   * {@inheritDoc}
   */
  public final void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }


  /**
   * {@inheritDoc}
   */
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ADD;
  }



  /**
   * {@inheritDoc}
   */
  public final void disconnectClient(DisconnectReason disconnectReason,
                                     boolean sendNotification, Message message
  )
  {
    // Before calling clientConnection.disconnect, we need to mark this
    // operation as cancelled so that the attempt to cancel it later won't cause
    // an unnecessary delay.
    setCancelResult(CancelResult.CANCELED);

    clientConnection.disconnect(disconnectReason, sendNotification,
            message);
  }


  /**
   * {@inheritDoc}
   */
  public final String[][] getRequestLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return new String[][]
    {
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
    };
  }



  /**
   * {@inheritDoc}
   */
  public final String[][] getResponseLogElements()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    String resultCode = String.valueOf(getResultCode().getIntValue());

    String errorMessage;
    MessageBuilder errorMessageBuffer = getErrorMessage();
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
         String.valueOf(getProcessingTime());

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
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  public final ArrayList<Control> getResponseControls()
  {
    return responseControls;
  }



  /**
   * {@inheritDoc}
   */
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }



  /**
   * {@inheritDoc}
   */
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }


  /**
   * {@inheritDoc}
   */
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }



  /**
   * {@inheritDoc}
   */
  public
  boolean setCancelRequest(CancelRequest cancelRequest)
  {
    this.cancelRequest = cancelRequest;
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public final void toString(StringBuilder buffer)
  {
    buffer.append("AddOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }

  /**
   * {@inheritDoc}
   */
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /**
   * {@inheritDoc}
   */
  public final void run()
  {
    // Start the processing timer.
    setProcessingStartTime();
    setResultCode(ResultCode.UNDEFINED);


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      setProcessingStopTime();
      return;
    }


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();


    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
addProcessing:
    {
      // Invoke the pre-parse add plugins.
      PreParsePluginResult preParseResult =
        pluginConfigManager.invokePreParseAddPlugins(this);
      if (preParseResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the request and
        // result and return.
        setResultCode(ResultCode.CANCELED);

        appendErrorMessage(ERR_CANCELED_BY_PREPARSE_DISCONNECT.get());

        setProcessingStopTime();

        logAddRequest(this);
        logAddResponse(this);
        pluginConfigManager.invokePostResponseAddPlugins(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        logAddRequest(this);
        break addProcessing;
      }
      else if (preParseResult.skipCoreProcessing())
      {
        break addProcessing;
      }

      // Log the add request message.
      logAddRequest(this);


      // Check for a request to cancel this operation.
      if (cancelRequest != null)
      {
        break addProcessing;
      }


      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        break addProcessing;
      }


      // Retrieve the network group attached to the client connection
      // and get a workflow to process the operation.
      NetworkGroup ng = getClientConnection().getNetworkGroup();
      Workflow workflow = ng.getWorkflowCandidate(entryDN);
      if (workflow == null)
      {
        // We have found no workflow for the requested base DN, just return
        // a no such entry result code and stop the processing.
        updateOperationErrMsgAndResCode();
        break addProcessing;
      }
      workflow.execute(this);
      workflowExecuted = true;

    } // end of processing block


    // Check for a terminated connection.
    if (getCancelResult() == CancelResult.CANCELED)
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the add response message.
      logAddResponse(this);

      return;
    }

    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);

      // Stop the processing timer.
      setProcessingStopTime();

      // Log the add response message.
      logAddResponse(this);

      // Invoke the post-response add plugins.
      invokePostResponsePlugins(workflowExecuted);

      return;
    }

    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);

    // Stop the processing timer.
    setProcessingStopTime();

    // Send the add response to the client.
    clientConnection.sendResponse(this);

    // Log the add response message.
    logAddResponse(this);

    // Notifies any persistent searches that might be registered with the
    // server.
    notifyPersistentSearches(workflowExecuted);

    // Invoke the post-response add plugins.
    invokePostResponsePlugins(workflowExecuted);
  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the worklfow, otherwise invoke the post reponse plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      List localOperations =
        (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

      if (localOperations != null)
      {
        for (Object localOp : localOperations)
        {
          LocalBackendAddOperation localOperation =
            (LocalBackendAddOperation)localOp;
          pluginConfigManager.invokePostResponseAddPlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseAddPlugins(this);
    }
  }


  /**
   * Notifies any persistent searches that might be registered with the server.
   * If no workflow has been executed then don't notify persistent searches.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been
   *                         executed
   */
  private void notifyPersistentSearches(boolean workflowExecuted)
  {
    if (! workflowExecuted)
    {
      return;
    }

    List localOperations =
      (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

    if (localOperations != null)
    {
      for (Object localOp : localOperations)
      {
        LocalBackendAddOperation localOperation =
          (LocalBackendAddOperation)localOp;

        if ((getResultCode() == ResultCode.SUCCESS) &&
            (localOperation.getEntryToAdd() != null))
        {
          for (PersistentSearch persistentSearch :
            DirectoryServer.getPersistentSearches())
          {
            try
            {
              persistentSearch.processAdd(localOperation,
                  localOperation.getEntryToAdd());
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              Message message = ERR_ADD_ERROR_NOTIFYING_PERSISTENT_SEARCH.get(
                  String.valueOf(persistentSearch), getExceptionMessage(e));
              logError(message);

              DirectoryServer.deregisterPersistentSearch(persistentSearch);
            }
          }
        }
      }
    }
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflows were found to process
   * the operation.
   */
  private void updateOperationErrMsgAndResCode()
  {
    DN entryDN = getEntryDN();
    DN parentDN = entryDN.getParentDNInSuffix();
    if (parentDN == null)
    {
      // Either this entry is a suffix or doesn't belong in the directory.
      if (DirectoryServer.isNamingContext(entryDN))
      {
        // This is fine.  This entry is one of the configured suffixes.
      }
      else if (entryDN.isNullDN())
      {
        // This is not fine.  The root DSE cannot be added.
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_ADD_CANNOT_ADD_ROOT_DSE.get());
      }
      else
      {
        // The entry doesn't have a parent but isn't a suffix.  This is not
        // allowed.
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_ADD_ENTRY_NOT_SUFFIX.get(
                String.valueOf(entryDN)));
      }
    }
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  public Entry getEntryToAdd()
  {
    return null;
  }

}

