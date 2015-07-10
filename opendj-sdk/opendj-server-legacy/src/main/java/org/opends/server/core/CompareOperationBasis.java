/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendCompareOperation;

import static org.opends.messages.CoreMessages.*;
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

  /** The attribute type for this compare operation. */
  private AttributeType attributeType;

  /** The assertion value for the compare operation. */
  private ByteString assertionValue;

  /** The set of attribute options. */
  private Set<String> attributeOptions;

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
    attributeType          = null;
    attributeOptions       = null;
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
   * @param  attributeType     The attribute type for this compare operation.
   * @param  assertionValue    The assertion value for the compare operation.
   */
  public CompareOperationBasis(
                          ClientConnection clientConnection, long operationID,
                          int messageID, List<Control> requestControls,
                          DN entryDN, AttributeType attributeType,
                          ByteString assertionValue)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.entryDN        = entryDN;
    this.attributeType  = attributeType;
    this.assertionValue = assertionValue;

    responseControls       = new ArrayList<>();
    rawEntryDN             = ByteString.valueOf(entryDN.toString());
    rawAttributeType       = attributeType.getNameOrOID();
    cancelRequest          = null;
    proxiedAuthorizationDN = null;
    attributeOptions       = new HashSet<>();
  }

  /** {@inheritDoc} */
  @Override
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  /** {@inheritDoc} */
  @Override
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }

  /** {@inheritDoc} */
  @Override
  public final DN getEntryDN()
  {
    if (entryDN == null) {
      try
      {
        entryDN = DN.decode(rawEntryDN);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getMessageObject());
      }
    }
    return entryDN;
  }

  /** {@inheritDoc} */
  @Override
  public final String getRawAttributeType()
  {
    return rawAttributeType;
  }

  /** {@inheritDoc} */
  @Override
  public final void setRawAttributeType(String rawAttributeType)
  {
    this.rawAttributeType = rawAttributeType;

    attributeType = null;
    attributeOptions = null;
  }

  private void getAttributeTypeAndOptions() {
    String baseName;
    int semicolonPos = rawAttributeType.indexOf(';');
    if (semicolonPos > 0) {
      baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

      attributeOptions = new HashSet<>();
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
    attributeType = DirectoryServer.getAttributeTypeOrDefault(baseName);
  }

  /** {@inheritDoc} */
  @Override
  public final AttributeType getAttributeType()
  {
    if (attributeType == null) {
      getAttributeTypeAndOptions();
    }
    return attributeType;
  }

  /** {@inheritDoc} */
  @Override
  public void setAttributeType(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getAttributeOptions()
  {
    if (attributeOptions == null) {
      getAttributeTypeAndOptions();
    }
    return attributeOptions;
  }

  /** {@inheritDoc} */
  @Override
  public void setAttributeOptions(Set<String> attributeOptions)
  {
    this.attributeOptions = attributeOptions;
  }

  /** {@inheritDoc} */
  @Override
  public final ByteString getAssertionValue()
  {
    return assertionValue;
  }

  /** {@inheritDoc} */
  @Override
  public final void setAssertionValue(ByteString assertionValue)
  {
    this.assertionValue = assertionValue;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  /** {@inheritDoc} */
  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  /** {@inheritDoc} */
  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  /** {@inheritDoc} */
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

    // Log the compare request message.
    logCompareRequest(this);

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
        DirectoryServer.getPluginConfigManager();

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;

    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse compare plugins.
      PluginResult.PreParse preParseResult =
          pluginConfigManager.invokePreParseComparePlugins(this);
      if(!preParseResult.continueProcessing())
      {
        setResultCode(preParseResult.getResultCode());
        appendErrorMessage(preParseResult.getErrorMessage());
        setMatchedDN(preParseResult.getMatchedDN());
        setReferralURLs(preParseResult.getReferralURLs());
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
          entryDN = DN.decode(rawEntryDN);
        }
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getMessageObject());

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
    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
      DirectoryServer.getPluginConfigManager();

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
          pluginConfigManager.invokePostResponseComparePlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      pluginConfigManager.invokePostResponseComparePlugins(this);
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

  /** {@inheritDoc} */
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
