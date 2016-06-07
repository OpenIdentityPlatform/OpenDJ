/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendCompareOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public class CompareOperationBasis
             extends AbstractOperation
             implements PreParseCompareOperation, CompareOperation,
                        PostResponseCompareOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The attribute description for this compare operation. */
  private AttributeDescription attributeDescription;

  /** The assertion value for the compare operation. */
  private ByteString assertionValue;

  /** The raw, unprocessed entry DN as included in the client request. */
  private ByteString rawEntryDN;

  /** The DN of the entry for the compare operation. */
  private DN entryDN;

  /** The proxied authorization target DN for this operation. */
  private DN proxiedAuthorizationDN;

  /** The set of response controls for this compare operation. */
  private List<Control> responseControls;

  /** The attribute type for the compare operation. */
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
  public CompareOperationBasis(
                          ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          ByteString rawEntryDN, String rawAttributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN       = rawEntryDN;
    this.rawAttributeType = rawAttributeType;
    this.assertionValue   = assertionValue;

    responseControls       = new ArrayList<>();
    entryDN                = null;
    attributeDescription = null;
    cancelRequest          = null;
    proxiedAuthorizationDN = null;
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
   * @param  attributeDescription The attribute description for this compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperationBasis(
                          ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          DN entryDN, AttributeDescription attributeDescription,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN        = entryDN;
    this.attributeDescription = attributeDescription;
    this.assertionValue = assertionValue;

    responseControls       = new ArrayList<>();
    rawEntryDN             = ByteString.valueOfUtf8(entryDN.toString());
    rawAttributeType       = attributeDescription.toString();
    cancelRequest          = null;
    proxiedAuthorizationDN = null;
  }

  @Override
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  @Override
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }

  @Override
  public final DN getEntryDN()
  {
    if (entryDN == null) {
      try
      {
        entryDN = DN.valueOf(rawEntryDN);
      }
      catch (LocalizedIllegalArgumentException e)
      {
        logger.traceException(e);

        setResultCode(ResultCode.INVALID_DN_SYNTAX);
        appendErrorMessage(e.getMessageObject());
      }
    }
    return entryDN;
  }

  @Override
  public final String getRawAttributeType()
  {
    return rawAttributeType;
  }

  @Override
  public final void setRawAttributeType(String rawAttributeType)
  {
    this.rawAttributeType = rawAttributeType;

    attributeDescription = null;
  }

  @Override
  public final AttributeDescription getAttributeDescription()
  {
    if (attributeDescription == null)
    {
      attributeDescription = getAttributeDescription0();
    }
    return attributeDescription;
  }

  private AttributeDescription getAttributeDescription0()
  {
    String baseName;
    int semicolonPos = rawAttributeType.indexOf(';');
    Set<String> attributeOptions;
    if (semicolonPos > 0) {
      baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

      attributeOptions = new LinkedHashSet<>();
      int nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        attributeOptions.add(
            rawAttributeType.substring(semicolonPos+1, nextPos));
        semicolonPos = nextPos;
        nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
      }

      attributeOptions.add(rawAttributeType.substring(semicolonPos+1));
    }
    else
    {
      baseName = toLowerCase(rawAttributeType);
      attributeOptions  = null;
    }
    return AttributeDescription.create(DirectoryServer.getSchema().getAttributeType(baseName), attributeOptions);
  }

  @Override
  public final ByteString getAssertionValue()
  {
    return assertionValue;
  }

  @Override
  public final void setAssertionValue(ByteString assertionValue)
  {
    this.assertionValue = assertionValue;
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.
    return OperationType.COMPARE;
  }



  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  /**
   * Performs the work of actually processing this operation.  This
   * should include all processing for the operation, including
   * invoking plugins, logging messages, performing access control,
   * managing synchronization, and any other work that might need to
   * be done in the course of processing.
   */
  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    logCompareRequest(this);

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;
    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse compare plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseComparePlugins(this)))
      {
        return;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the compare processing.
      try
      {
        if (entryDN == null)
        {
          entryDN = DN.valueOf(rawEntryDN);
        }
      }
      catch (LocalizedIllegalArgumentException e)
      {
        logger.traceException(e);

        setResultCode(ResultCode.INVALID_DN_SYNTAX);
        appendErrorMessage(e.getMessageObject());

        return;
      }

      workflowExecuted = execute(this, entryDN);
    }
    catch(CanceledOperationException coe)
    {
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);
      cancelResult = new CancelResult(ResultCode.CANCELLED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the compare response message.
      logCompareResponse(this);

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      // Invoke the post-response compare plugins.
      invokePostResponsePlugins(workflowExecuted);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }


  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the workflow, otherwise invoke the post response plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      List<LocalBackendCompareOperation> localOperations =
        (List)getAttachment(Operation.LOCALBACKENDOPERATIONS);

      if (localOperations != null)
      {
        for (LocalBackendCompareOperation localOperation : localOperations)
        {
          getPluginConfigManager().invokePostResponseComparePlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      getPluginConfigManager().invokePostResponseComparePlugins(this);
    }
  }


  /**
   * Updates the error message and the result code of the operation.
   *
   * This method is called because no workflow was found to process
   * the operation.
   */
  @Override
  public void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_COMPARE_NO_SUCH_ENTRY.get(getEntryDN()));
  }

  @Override
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


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public Entry getEntryToCompare()
  {
    return null;
  }

}
