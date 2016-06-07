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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.api.ClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendAddOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation that may be used to add a new entry to the
 * Directory Server.
 */
public class AddOperationBasis
       extends AbstractOperation
       implements PreParseAddOperation, AddOperation, PostResponseAddOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of response controls to send to the client. */
  private final ArrayList<Control> responseControls = new ArrayList<>();

  /** The raw, unprocessed entry DN as provided in the request. This may or may not be a valid DN. */
  private ByteString rawEntryDN;
  /** The processed DN of the entry to add. */
  private DN entryDN;
  /** The proxied authorization target DN for this operation. */
  private DN proxiedAuthorizationDN;

  /**
   * The set of attributes (including the objectclass attribute) in a raw,
   * unprocessed form as provided in the request. One or more of these
   * attributes may be invalid.
   */
  private List<RawAttribute> rawAttributes;
  /** The set of operational attributes for the entry to add. */
  private Map<AttributeType,List<Attribute>> operationalAttributes;
  /** The set of user attributes for the entry to add. */
  private Map<AttributeType,List<Attribute>> userAttributes;
  /** The set of objectclasses for the entry to add. */
  private Map<ObjectClass,String> objectClasses;

  /** The flag indicates if an LDAP error was reported. */
  private boolean ldapError;

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

    entryDN               = null;
    userAttributes        = null;
    operationalAttributes = null;
    objectClasses         = null;
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

    rawEntryDN = ByteString.valueOfUtf8(entryDN.toString());

    ArrayList<String> values = new ArrayList<>(objectClasses.values());
    rawAttributes = new ArrayList<>();
    rawAttributes.add(new LDAPAttribute(ATTR_OBJECTCLASS, values));
    addAll(rawAttributes, userAttributes);
    addAll(rawAttributes, operationalAttributes);
  }

  private void addAll(List<RawAttribute> rawAttributes, Map<AttributeType, List<Attribute>> attributesToAdd)
  {
    for (List<Attribute> attrList : attributesToAdd.values())
    {
      for (Attribute a : attrList)
      {
        rawAttributes.add(new LDAPAttribute(a));
      }
    }
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
    }
    return entryDN;
  }

  @Override
  public final List<RawAttribute> getRawAttributes()
  {
    return rawAttributes;
  }

  @Override
  public final void addRawAttribute(RawAttribute rawAttribute)
  {
    rawAttributes.add(rawAttribute);

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }

  @Override
  public final void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    this.rawAttributes = rawAttributes;

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }

  @Override
  public final Map<ObjectClass,String> getObjectClasses()
  {
    if (objectClasses == null){
      computeObjectClassesAndAttributes();
    }
    return objectClasses;
  }

  @Override
  public final void addObjectClass(ObjectClass objectClass, String name)
  {
    objectClasses.put(objectClass, name);
  }

  @Override
  public final void removeObjectClass(ObjectClass objectClass)
  {
    objectClasses.remove(objectClass);
  }

  @Override
  public final Map<AttributeType,List<Attribute>> getUserAttributes()
  {
    if (userAttributes == null){
      computeObjectClassesAndAttributes();
    }
    return userAttributes;
  }

  @Override
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
    if (!ldapError
        && (objectClasses == null || userAttributes == null
            || operationalAttributes == null))
    {
      objectClasses         = new HashMap<>();
      userAttributes        = new HashMap<>();
      operationalAttributes = new HashMap<>();

      for (RawAttribute a : rawAttributes)
      {
        try
        {
          Attribute attr = a.toAttribute();
          AttributeDescription attrDesc = attr.getAttributeDescription();
          AttributeType attrType = attrDesc.getAttributeType();

          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          if (attrType.isNoUserModification()
              && !isInternalOperation()
              && !isSynchronizationOperation())
          {
            throw new LDAPException(LDAPResultCode.UNWILLING_TO_PERFORM,
                ERR_ADD_ATTR_IS_NO_USER_MOD.get(entryDN, attrDesc));
          }

          boolean hasBinaryOption = attrDesc.hasOption("binary");
          if (attrType.getSyntax().isBEREncodingRequired())
          {
            if (!hasBinaryOption)
            {
              //A binary option wasn't provided by the client so add it.
              AttributeBuilder builder = new AttributeBuilder(attr);
              builder.setOption("binary");
              attr = builder.toAttribute();
            }
          }
          else if (hasBinaryOption)
          {
            // binary option is not honored for non-BER-encodable attributes.
            throw new LDAPException(LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE,
                ERR_ADD_ATTR_IS_INVALID_OPTION.get(entryDN, attrDesc));
          }

          if (attrType.isObjectClass())
          {
            for (ByteString os : a.getValues())
            {
              String ocName = os.toString();
              objectClasses.put(getSchema().getObjectClass(ocName), ocName);
            }
          }
          else if (attrType.isOperational())
          {
            List<Attribute> attrs = operationalAttributes.get(attrType);
            if (attrs == null)
            {
              attrs = new ArrayList<>(1);
              operationalAttributes.put(attrType, attrs);
            }
            attrs.add(attr);
          }
          else
          {
            List<Attribute> attrs = userAttributes.get(attrType);
            if (attrs == null)
            {
              attrs = newArrayList(attr);
              userAttributes.put(attrType, attrs);
            }
            else
            {
              // Check to see if any of the existing attributes in the list
              // have the same set of options.  If so, then add the values
              // to that attribute.
              boolean attributeSeen = false;
              for (int i = 0; i < attrs.size(); i++) {
                Attribute ea = attrs.get(i);
                if (ea.getAttributeDescription().equals(attrDesc))
                {
                  AttributeBuilder builder = new AttributeBuilder(ea);
                  builder.addAll(attr);
                  attrs.set(i, builder.toAttribute());
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
          ldapError = true;
          return;
        }
      }
    }
  }

  @Override
  public final void setAttribute(AttributeType attributeType,
                                 List<Attribute> attributeList)
  {
    Map<AttributeType, List<Attribute>> attributes =
        getAttributes(attributeType.isOperational());
    if (attributeList == null || attributeList.isEmpty())
    {
      attributes.remove(attributeType);
    }
    else
    {
      attributes.put(attributeType, attributeList);
    }
  }

  @Override
  public final void removeAttribute(AttributeType attributeType)
  {
    getAttributes(attributeType.isOperational()).remove(attributeType);
  }

  private Map<AttributeType, List<Attribute>> getAttributes(boolean isOperational)
  {
    if (isOperational)
    {
      return operationalAttributes;
    }
    return userAttributes;
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ADD;
  }

  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  @Override
  public final ArrayList<Control> getResponseControls()
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

  @Override
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

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    logAddRequest(this);

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;
    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse add plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseAddPlugins(this)))
      {
        return;
      }

      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
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

      // Log the add response message.
      logAddResponse(this);

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.notifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }


      // Invoke the post-response callbacks.
      if (workflowExecuted) {
        invokePostResponseCallbacks();
      }

      // Invoke the post-response add plugins.
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
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      List<LocalBackendAddOperation> localOperations =
          (List) getAttachment(Operation.LOCALBACKENDOPERATIONS);

      if (localOperations != null)
      {
        for (LocalBackendAddOperation localOp : localOperations)
        {
          getPluginConfigManager().invokePostResponseAddPlugins(localOp);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      getPluginConfigManager().invokePostResponseAddPlugins(this);
    }
  }

  @Override
  public void updateOperationErrMsgAndResCode()
  {
    DN entryDN = getEntryDN();
    DN parentDN = DirectoryServer.getParentDNInSuffix(entryDN);
    if (parentDN == null)
    {
      // Either this entry is a suffix or doesn't belong in the directory.
      if (DirectoryServer.isNamingContext(entryDN))
      {
        // This is fine.  This entry is one of the configured suffixes.
        return;
      }
      if (entryDN.isRootDN())
      {
        // This is not fine.  The root DSE cannot be added.
        setResultCode(ResultCode.UNWILLING_TO_PERFORM);
        appendErrorMessage(ERR_ADD_CANNOT_ADD_ROOT_DSE.get());
        return;
      }
      // The entry doesn't have a parent but isn't a suffix. This is not allowed.
      setResultCode(ResultCode.NO_SUCH_OBJECT);
      appendErrorMessage(ERR_ADD_ENTRY_NOT_SUFFIX.get(entryDN));
      return;
    }
    // The suffix does not exist
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_ADD_ENTRY_UNKNOWN_SUFFIX.get(entryDN));
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public Entry getEntryToAdd()
  {
    return null;
  }
}
