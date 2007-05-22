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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.CancelResult;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LockManager;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.util.TimeThread;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.CoreConstants.*;
import static org.opends.server.loggers.AccessLogger.*;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;




/**
 * This class defines an operation that may be used to add a new entry to the
 * Directory Server.
 */
public class AddOperation
       extends Operation
       implements PreParseAddOperation, PreOperationAddOperation,
                  PostOperationAddOperation, PostResponseAddOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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

  // The entry being added to the server.
  private Entry entry;

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

  // The time that processing started on this operation.
  private long processingStartTime;

  // The time that processing ended on this operation.
  private long processingStopTime;



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
  public AddOperation(ClientConnection clientConnection, long operationID,
                      int messageID, List<Control> requestControls,
                      ByteString rawEntryDN, List<RawAttribute> rawAttributes)
  {
    super(clientConnection, operationID, messageID, requestControls);


    this.rawEntryDN    = rawEntryDN;
    this.rawAttributes = rawAttributes;

    responseControls       = new ArrayList<Control>();
    cancelRequest          = null;
    entry                  = null;
    entryDN                = null;
    userAttributes         = null;
    operationalAttributes  = null;
    objectClasses          = null;
    proxiedAuthorizationDN = null;
    changeNumber           = -1;
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
  public AddOperation(ClientConnection clientConnection, long operationID,
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

    entry = null;

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

    responseControls       = new ArrayList<Control>();
    proxiedAuthorizationDN = null;
    cancelRequest          = null;
    changeNumber           = -1;
  }



  /**
   * Retrieves the DN of the entry to add in a raw, unparsed form as it was
   * included in the request.  This may or may not actually contain a valid DN,
   * since no validation will have been performed on it.
   *
   * @return  The DN of the entry in a raw, unparsed form.
   */
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }



  /**
   * Specifies the raw entry DN for the entry to add.  This should only be
   * called by pre-parse plugins to alter the DN before it has been processed.
   * If the entry DN needs to be altered later in the process, then it should
   * be done using the <CODE>getEntryDN</CODE> and <CODE>setEntryDN</CODE>
   * methods.
   *
   * @param  rawEntryDN  The raw entry DN for the entry to add.
   */
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }



  /**
   * Retrieves the DN of the entry to add.  This method should not be called
   * by pre-parse plugins because the parsed DN will not be available at that
   * time.
   *
   * @return  The DN of the entry to add, or <CODE>null</CODE> if it has not yet
   *          been parsed from the raw DN.
   */
  public final DN getEntryDN()
  {
    return entryDN;
  }



  /**
   * Retrieves the set of attributes in their raw, unparsed form as read from
   * the client request.  Some of these attributes may be invalid as no
   * validation will have been performed on them.  The returned list must not be
   * altered by the caller.
   *
   * @return  The set of attributes in their raw, unparsed form as read from the
   *          client request.
   */
  public final List<RawAttribute> getRawAttributes()
  {
    return rawAttributes;
  }



  /**
   * Adds the provided attribute to the set of raw attributes for this add
   * operation.  This should only be called by pre-parse plugins.
   *
   * @param  rawAttribute  The attribute to add to the set of raw attributes for
   *                       this add operation.
   */
  public final void addRawAttribute(RawAttribute rawAttribute)
  {
    rawAttributes.add(rawAttribute);

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }



  /**
   * Replaces the set of raw attributes for this add operation.  This should
   * only be called by pre-parse plugins.
   *
   * @param  rawAttributes  The set of raw attributes for this add operation.
   */
  public final void setRawAttributes(List<RawAttribute> rawAttributes)
  {
    this.rawAttributes = rawAttributes;

    objectClasses         = null;
    userAttributes        = null;
    operationalAttributes = null;
  }



  /**
   * Retrieves the set of processed objectclasses for the entry to add.  This
   * should not be called by pre-parse plugins because this information will not
   * yet be available.  The contents of the returned map may not be altered by
   * the caller.
   *
   * @return  The set of processed objectclasses for the entry to add, or
   *          <CODE>null</CODE> if that information is not yet available.
   */
  public final Map<ObjectClass,String> getObjectClasses()
  {
    return objectClasses;
  }



  /**
   * Adds the provided objectclass to the entry to add.  This should only be
   * called from pre-operation plugins.  Note that pre-operation plugin
   * processing is invoked after access control and schema validation, so
   * plugins should be careful to only make changes that will not violate either
   * schema or access control rules.
   *
   * @param  objectClass  The objectclass to add to the entry.
   * @param  name         The name to use for the objectclass.
   */
  public final void addObjectClass(ObjectClass objectClass, String name)
  {
    objectClasses.put(objectClass, name);
  }



  /**
   * Removes the provided objectclass from the entry to add.  This should only
   * be called from pre-operation plugins.  Note that pre-operation plugin
   * processing is invoked after access control and schema validation, so
   * plugins should be careful to only make changes that will not violate either
   * schema or access control rules.
   *
   * @param  objectClass  The objectclass to remove from the entry.
   */
  public final void removeObjectClass(ObjectClass objectClass)
  {
    objectClasses.remove(objectClass);
  }



  /**
   * Retrieves the set of processed user attributes for the entry to add.  This
   * should not be called by pre-parse plugins because this information will not
   * yet be available.  The contents of the returned map may be altered by the
   * caller.
   *
   * @return  The set of processed user attributes for the entry to add, or
   *          <CODE>null</CODE> if that information is not yet available.
   */
  public final Map<AttributeType,List<Attribute>> getUserAttributes()
  {
    return userAttributes;
  }



  /**
   * Retrieves the set of processed operational attributes for the entry to add.
   * This should not be called by pre-parse plugins because this information
   * will not yet be available.  The contents of the returned map may be altered
   * by the caller.
   *
   * @return  The set of processed operational attributes for the entry to add,
   *          or <CODE>null</CODE> if that information is not yet available.
   */
  public final Map<AttributeType,List<Attribute>> getOperationalAttributes()
  {
    return operationalAttributes;
  }



  /**
   * Sets the specified attribute in the entry to add, overwriting any existing
   * attribute of the specified type if necessary.  This should only be called
   * from pre-operation plugins.  Note that pre-operation plugin processing is
   * invoked after access control and schema validation, so plugins should be
   * careful to only make changes that will not violate either schema or access
   * control rules.
   *
   * @param  attributeType  The attribute type for the attribute.
   * @param  attributeList  The attribute list for the provided attribute type.
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
   * Removes the specified attribute from the entry to add. This should only be
   * called from pre-operation plugins.  Note that pre-operation processing is
   * invoked after access control and schema validation, so plugins should be
   * careful to only make changes that will not violate either schema or access
   * control rules.
   *
   * @param  attributeType  The attribute tyep for the attribute to remove.
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
   * Retrieves the entry to be added to the server.  Note that this will not be
   * available to pre-parse plugins or during the conflict resolution portion of
   * the synchronization processing.
   *
   * @return  The entry to be added to the server, or <CODE>null</CODE> if it is
   *          not yet available.
   */
  public final Entry getEntryToAdd()
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
   * Retrieves the change number that has been assigned to this operation.
   *
   * @return  The change number that has been assigned to this operation, or -1
   *          if none has been assigned yet or if there is no applicable
   *          synchronization mechanism in place that uses change numbers.
   */
  public final long getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Specifies the change number that has been assigned to this operation by the
   * synchronization mechanism.
   *
   * @param  changeNumber  The change number that has been assigned to this
   *                       operation by the synchronization mechanism.
   */
  public final void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.ADD;
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
      new String[] { LOG_ELEMENT_ENTRY_DN, String.valueOf(rawEntryDN) }
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
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public final ArrayList<Control> getResponseControls()
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
    // Start the processing timer.
    processingStartTime = System.currentTimeMillis();
    setResultCode(ResultCode.UNDEFINED);


    // Check for and handle a request to cancel this operation.
    if (cancelRequest != null)
    {
      indicateCancelled(cancelRequest);
      processingStopTime = System.currentTimeMillis();
      return;
    }


    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();
    boolean skipPostOperation = false;


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

        int msgID = MSGID_CANCELED_BY_PREPARSE_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logAddRequest(this);
        logAddResponse(this);
        return;
      }
      else if (preParseResult.sendResponseImmediately())
      {
        skipPostOperation = true;
        logAddRequest(this);
        break addProcessing;
      }


      // Log the add request message.
      logAddRequest(this);


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logAddResponse(this);
        return;
      }


      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
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
        appendErrorMessage(de.getErrorMessage());
        setMatchedDN(de.getMatchedDN());
        setReferralURLs(de.getReferralURLs());

        break addProcessing;
      }


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
                appendErrorMessage(getMessage(MSGID_ADD_ATTR_IS_NO_USER_MOD,
                                              String.valueOf(entryDN),
                                              attr.getName()));
                break addProcessing;
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
            appendErrorMessage(le.getMessage());

            break addProcessing;
          }
        }
      }


      // Check for and handle a request to cancel this operation.
      if (cancelRequest != null)
      {
        indicateCancelled(cancelRequest);
        processingStopTime = System.currentTimeMillis();
        logAddResponse(this);
        return;
      }


      // Grab a read lock on the parent entry, if there is one.  We need to do
      // this to ensure that the parent is not deleted or renamed while this add
      // is in progress, and we could also need it to check the entry against
      // a DIT structure rule.
      Lock parentLock = null;
      Lock entryLock  = null;

      DN parentDN = entryDN.getParentDNInSuffix();
      if (parentDN == null)
      {
        // Either this entry is a suffix or doesn't belong in the directory.
        if (DirectoryServer.isNamingContext(entryDN))
        {
          // This is fine.  This entry is one of the configured suffixes.
          parentLock = null;
        }
        else if (entryDN.isNullDN())
        {
          // This is not fine.  The root DSE cannot be added.
          setResultCode(ResultCode.UNWILLING_TO_PERFORM);
          appendErrorMessage(getMessage(MSGID_ADD_CANNOT_ADD_ROOT_DSE));
          break addProcessing;
        }
        else
        {
          // The entry doesn't have a parent but isn't a suffix.  This is not
          // allowed.
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(getMessage(MSGID_ADD_ENTRY_NOT_SUFFIX,
                                        String.valueOf(entryDN)));
          break addProcessing;
        }
      }
      else
      {
        for (int i=0; i < 3; i++)
        {
          parentLock = LockManager.lockRead(parentDN);
          if (parentLock != null)
          {
            break;
          }
        }

        if (parentLock == null)
        {
          setResultCode(DirectoryServer.getServerErrorResultCode());
          appendErrorMessage(getMessage(MSGID_ADD_CANNOT_LOCK_PARENT,
                                        String.valueOf(entryDN),
                                        String.valueOf(parentDN)));

          skipPostOperation = true;
          break addProcessing;
        }
      }


      try
      {
        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logAddResponse(this);
          return;
        }


        // Grab a write lock on the target entry.  We'll need to do this
        // eventually anyway, and we want to make sure that the two locks are
        // always released when exiting this method, no matter what.  Since
        // the entry shouldn't exist yet, locking earlier than necessary
        // shouldn't cause a problem.
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
          appendErrorMessage(getMessage(MSGID_ADD_CANNOT_LOCK_ENTRY,
                                        String.valueOf(entryDN)));

          skipPostOperation = true;
          break addProcessing;
        }


        // Invoke any conflict resolution processing that might be needed by the
        // synchronization provider.
        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                 provider.handleConflictResolution(this);
            if (! result.continueOperationProcessing())
            {
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED,
                     getConnectionID(), getOperationID(),
                     getExceptionMessage(de));

            setResponseData(de);
            break addProcessing;
          }
        }


        // Check to see if the entry already exists.  We do this before
        // checking whether the parent exists to ensure a referral entry
        // above the parent results in a correct referral.
        try
        {
          if (DirectoryServer.entryExists(entryDN))
          {
            setResultCode(ResultCode.ENTRY_ALREADY_EXISTS);
            appendErrorMessage(getMessage(MSGID_ADD_ENTRY_ALREADY_EXISTS,
                                          String.valueOf(entryDN)));
            break addProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getErrorMessage());
          setMatchedDN(de.getMatchedDN());
          setReferralURLs(de.getReferralURLs());
          break addProcessing;
        }


        // Get the parent entry, if it exists.
        Entry parentEntry = null;
        if (parentDN != null)
        {
          try
          {
            parentEntry = DirectoryServer.getEntry(parentDN);

            if (parentEntry == null)
            {
              DN matchedDN = parentDN.getParentDNInSuffix();
              while (matchedDN != null)
              {
                try
                {
                  if (DirectoryServer.entryExists(matchedDN))
                  {
                    setMatchedDN(matchedDN);
                    break;
                  }
                }
                catch (Exception e)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, e);
                  }
                  break;
                }

                matchedDN = matchedDN.getParentDNInSuffix();
              }


              // The parent doesn't exist, so this add can't be successful.
              setResultCode(ResultCode.NO_SUCH_OBJECT);
              appendErrorMessage(getMessage(MSGID_ADD_NO_PARENT,
                                            String.valueOf(entryDN),
                                            String.valueOf(parentDN)));
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResultCode(de.getResultCode());
            appendErrorMessage(de.getErrorMessage());
            setMatchedDN(de.getMatchedDN());
            setReferralURLs(de.getReferralURLs());
            break addProcessing;
          }
        }


        // Check to make sure that all of the RDN attributes are included as
        // attribute values.  If not, then either add them or report an error.
        RDN rdn = entryDN.getRDN();
        int numAVAs = rdn.getNumValues();
        for (int i=0; i < numAVAs; i++)
        {
          AttributeType  t = rdn.getAttributeType(i);
          AttributeValue v = rdn.getAttributeValue(i);
          String         n = rdn.getAttributeName(i);
          if (t.isOperational())
          {
            List<Attribute> attrList = operationalAttributes.get(t);
            if (attrList == null)
            {
              if (isSynchronizationOperation() ||
                  DirectoryServer.addMissingRDNAttributes())
              {
                LinkedHashSet<AttributeValue> valueList =
                     new LinkedHashSet<AttributeValue>(1);
                valueList.add(v);

                attrList = new ArrayList<Attribute>();
                attrList.add(new Attribute(t, n, valueList));

                operationalAttributes.put(t, attrList);
              }
              else
              {
                setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                int msgID = MSGID_ADD_MISSING_RDN_ATTRIBUTE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              n));

                break addProcessing;
              }
            }
            else
            {
              boolean found = false;
              for (Attribute a : attrList)
              {
                if (a.hasOptions())
                {
                  continue;
                }
                else
                {
                  if (! a.hasValue(v))
                  {
                    a.getValues().add(v);
                  }

                  found = true;
                  break;
                }
              }

              if (! found)
              {
                if (isSynchronizationOperation() ||
                    DirectoryServer.addMissingRDNAttributes())
                {
                  LinkedHashSet<AttributeValue> valueList =
                       new LinkedHashSet<AttributeValue>(1);
                  valueList.add(v);
                  attrList.add(new Attribute(t, n, valueList));
                }
                else
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_ADD_MISSING_RDN_ATTRIBUTE;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                n));

                  break addProcessing;
                }
              }
            }
          }
          else
          {
            List<Attribute> attrList = userAttributes.get(t);
            if (attrList == null)
            {
              if (isSynchronizationOperation() ||
                  DirectoryServer.addMissingRDNAttributes())
              {
                LinkedHashSet<AttributeValue> valueList =
                     new LinkedHashSet<AttributeValue>(1);
                valueList.add(v);

                attrList = new ArrayList<Attribute>();
                attrList.add(new Attribute(t, n, valueList));

                userAttributes.put(t, attrList);
              }
              else
              {
                setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                int msgID = MSGID_ADD_MISSING_RDN_ATTRIBUTE;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              n));

                break addProcessing;
              }
            }
            else
            {
              boolean found = false;
              for (Attribute a : attrList)
              {
                if (a.hasOptions())
                {
                  continue;
                }
                else
                {
                  if (! a.hasValue(v))
                  {
                    a.getValues().add(v);
                  }

                  found = true;
                  break;
                }
              }

              if (! found)
              {
                if (isSynchronizationOperation() ||
                    DirectoryServer.addMissingRDNAttributes())
                {
                  LinkedHashSet<AttributeValue> valueList =
                       new LinkedHashSet<AttributeValue>(1);
                  valueList.add(v);
                  attrList.add(new Attribute(t, n, valueList));
                }
                else
                {
                  setResultCode(ResultCode.CONSTRAINT_VIOLATION);

                  int msgID = MSGID_ADD_MISSING_RDN_ATTRIBUTE;
                  appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                                n));

                  break addProcessing;
                }
              }
            }
          }
        }


        // Check to make sure that all objectclasses have their superior classes
        // listed in the entry.  If not, then add them.
        HashSet<ObjectClass> additionalClasses = null;
        for (ObjectClass oc : objectClasses.keySet())
        {
          ObjectClass superiorClass = oc.getSuperiorClass();
          if ((superiorClass != null) &&
              (! objectClasses.containsKey(superiorClass)))
          {
            if (additionalClasses == null)
            {
              additionalClasses = new HashSet<ObjectClass>();
            }

            additionalClasses.add(superiorClass);
          }
        }

        if (additionalClasses != null)
        {
          for (ObjectClass oc : additionalClasses)
          {
            addObjectClassChain(oc);
          }
        }


        // Create an entry object to encapsulate the set of attributes and
        // objectclasses.
        entry = new Entry(entryDN, objectClasses, userAttributes,
                          operationalAttributes);


        // Check to see if the entry includes a privilege specification.  If so,
        // then the requester must have the PRIVILEGE_CHANGE privilege.
        AttributeType privType =
             DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME, true);
        if (entry.hasAttribute(privType) &&
            (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this)))
        {
          int msgID = MSGID_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES;
          appendErrorMessage(getMessage(msgID));
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          break addProcessing;
        }

        // If it's not a synchronization operation, then check
        // to see if the entry contains one or more passwords and if they
        // are valid in accordance with the password policies associated with
        // the user.  Also perform any encoding that might be required by
        // password storage schemes.
        if (! isSynchronizationOperation())
        {
          // FIXME -- We need to check to see if the password policy subentry
          //          might be specified virtually rather than as a real
          //          attribute.
          PasswordPolicy pwPolicy = null;
          List<Attribute> pwAttrList =
               entry.getAttribute(OP_ATTR_PWPOLICY_POLICY_DN);
          if ((pwAttrList != null) && (! pwAttrList.isEmpty()))
          {
            Attribute a = pwAttrList.get(0);
            LinkedHashSet<AttributeValue> valueSet = a.getValues();
            Iterator<AttributeValue> iterator = valueSet.iterator();
            if (iterator.hasNext())
            {
              DN policyDN;
              try
              {
                policyDN = DN.decode(iterator.next().getValue());
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                int msgID = MSGID_ADD_INVALID_PWPOLICY_DN_SYNTAX;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                break addProcessing;
              }

              pwPolicy = DirectoryServer.getPasswordPolicy(policyDN);
              if (pwPolicy == null)
              {
                int msgID = MSGID_ADD_NO_SUCH_PWPOLICY;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              String.valueOf(policyDN)));

                setResultCode(ResultCode.CONSTRAINT_VIOLATION);
                break addProcessing;
              }
            }
          }

          if (pwPolicy == null)
          {
            pwPolicy = DirectoryServer.getDefaultPasswordPolicy();
          }

          try
          {
            handlePasswordPolicy(pwPolicy, entry);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResponseData(de);
            break addProcessing;
          }
        }


        // Check to see if the entry is valid according to the server schema,
        // and also whether its attributes are valid according to their syntax.
        if (DirectoryServer.checkSchema())
        {
          StringBuilder invalidReason = new StringBuilder();
          if (! entry.conformsToSchema(parentEntry, true, true, true,
                                       invalidReason))
          {
            setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
            setErrorMessage(invalidReason);
            break addProcessing;
          }
          else
          {
            switch (DirectoryServer.getSyntaxEnforcementPolicy())
            {
              case REJECT:
                invalidReason = new StringBuilder();
                for (List<Attribute> attrList : userAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          String message =
                               getMessage(MSGID_ADD_OP_INVALID_SYNTAX,
                                          String.valueOf(entryDN),
                                          String.valueOf(v.getStringValue()),
                                          String.valueOf(a.getName()),
                                          String.valueOf(invalidReason));
                          invalidReason = new StringBuilder(message);

                          setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                          setErrorMessage(invalidReason);
                          break addProcessing;
                        }
                      }
                    }
                  }
                }

                for (List<Attribute> attrList :
                     operationalAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          String message =
                               getMessage(MSGID_ADD_OP_INVALID_SYNTAX,
                                          String.valueOf(entryDN),
                                          String.valueOf(v.getStringValue()),
                                          String.valueOf(a.getName()),
                                          String.valueOf(invalidReason));
                          invalidReason = new StringBuilder(message);

                          setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
                          setErrorMessage(invalidReason);
                          break addProcessing;
                        }
                      }
                    }
                  }
                }

                break;


              case WARN:
                invalidReason = new StringBuilder();
                for (List<Attribute> attrList : userAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          logError(ErrorLogCategory.SCHEMA,
                                   ErrorLogSeverity.SEVERE_WARNING,
                                   MSGID_ADD_OP_INVALID_SYNTAX,
                                   String.valueOf(entryDN),
                                   String.valueOf(v.getStringValue()),
                                   String.valueOf(a.getName()),
                                   String.valueOf(invalidReason));
                        }
                      }
                    }
                  }
                }

                for (List<Attribute> attrList : operationalAttributes.values())
                {
                  for (Attribute a : attrList)
                  {
                    AttributeSyntax syntax = a.getAttributeType().getSyntax();
                    if (syntax != null)
                    {
                      for (AttributeValue v : a.getValues())
                      {
                        if (! syntax.valueIsAcceptable(v.getValue(),
                                                       invalidReason))
                        {
                          logError(ErrorLogCategory.SCHEMA,
                                   ErrorLogSeverity.SEVERE_WARNING,
                                   MSGID_ADD_OP_INVALID_SYNTAX,
                                   String.valueOf(entryDN),
                                   String.valueOf(v.getStringValue()),
                                   String.valueOf(a.getName()),
                                   String.valueOf(invalidReason));
                        }
                      }
                    }
                  }
                }

                break;
            }
          }


          // See if the entry contains any attributes or object classes marked
          // OBSOLETE.  If so, then reject the entry.
          for (AttributeType at : userAttributes.keySet())
          {
            if (at.isObsolete())
            {
              int    msgID   = MSGID_ADD_ATTR_IS_OBSOLETE;
              String message = getMessage(msgID, String.valueOf(entryDN),
                                          at.getNameOrOID());
              appendErrorMessage(message);
              setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }

          for (AttributeType at : operationalAttributes.keySet())
          {
            if (at.isObsolete())
            {
              int    msgID   = MSGID_ADD_ATTR_IS_OBSOLETE;
              String message = getMessage(msgID, String.valueOf(entryDN),
                                          at.getNameOrOID());
              appendErrorMessage(message);
              setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }

          for (ObjectClass oc : objectClasses.keySet())
          {
            if (oc.isObsolete())
            {
              int    msgID   = MSGID_ADD_OC_IS_OBSOLETE;
              String message = getMessage(msgID, String.valueOf(entryDN),
                                          oc.getNameOrOID());
              appendErrorMessage(message);
              setResultCode(ResultCode.CONSTRAINT_VIOLATION);
              break addProcessing;
            }
          }
        }

        // Check to see if there are any controls in the request. If so,
        // then
        // see if there is any special processing required.
        boolean                    noOp            = false;
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
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break addProcessing;
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

                  appendErrorMessage(getMessage(MSGID_ADD_ASSERTION_FAILED,
                                                String.valueOf(entryDN)));

                  break addProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(ResultCode.PROTOCOL_ERROR);

                int msgID = MSGID_ADD_CANNOT_PROCESS_ASSERTION_FILTER;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              de.getErrorMessage()));

                break addProcessing;
              }
            }
            else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
            {
              noOp = true;
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
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break addProcessing;
                }
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
                break addProcessing;
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
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break addProcessing;
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
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break addProcessing;
              }

              if (AccessControlConfigManager.getInstance()
                      .getAccessControlHandler().isProxiedAuthAllowed(this,
                      authorizationEntry) == false) {
                setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

                int msgID = MSGID_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

                skipPostOperation = true;
                break addProcessing;
              }
              setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                proxiedAuthorizationDN = DN.nullDN();
              }
              else
              {
                proxiedAuthorizationDN = authorizationEntry.getDN();
              }
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
                break addProcessing;
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
                    TRACER.debugCaught(DebugLogLevel.ERROR, le);
                  }

                  setResultCode(ResultCode.valueOf(le.getResultCode()));
                  appendErrorMessage(le.getMessage());

                  break addProcessing;
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
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                setResultCode(de.getResultCode());
                appendErrorMessage(de.getErrorMessage());

                break addProcessing;
              }

              if (AccessControlConfigManager.getInstance()
                      .getAccessControlHandler().isProxiedAuthAllowed(this,
                      authorizationEntry) == false) {
                setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

                int msgID = MSGID_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

                skipPostOperation = true;
                break addProcessing;
              }
              setAuthorizationEntry(authorizationEntry);
              if (authorizationEntry == null)
              {
                proxiedAuthorizationDN = DN.nullDN();
              }
              else
              {
                proxiedAuthorizationDN = authorizationEntry.getDN();
              }
            }

            // NYI -- Add support for additional controls.
            else if (c.isCritical())
            {
              Backend backend = DirectoryServer.getBackend(entryDN);
              if ((backend == null) || (! backend.supportsControl(oid)))
              {
                setResultCode(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);

                int msgID = MSGID_ADD_UNSUPPORTED_CRITICAL_CONTROL;
                appendErrorMessage(getMessage(msgID, String.valueOf(entryDN),
                                              oid));

                break addProcessing;
              }
            }
          }
        }


        // Check to see if the client has permission to perform the add.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists or
        // if the parent entry does not exist may have already exposed
        // sensitive information to the client.
        if (AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this) == false) {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

          int msgID = MSGID_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS;
          appendErrorMessage(getMessage(msgID, String.valueOf(entryDN)));

          skipPostOperation = true;
          break addProcessing;
        }

        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logAddResponse(this);
          return;
        }


        // If the operation is not a synchronization operation,
        // Invoke the pre-operation modify plugins.
        if (!isSynchronizationOperation())
        {
          PreOperationPluginResult preOpResult =
            pluginConfigManager.invokePreOperationAddPlugins(this);
          if (preOpResult.connectionTerminated())
          {
            // There's no point in continuing with anything.  Log the result
            // and return.
            setResultCode(ResultCode.CANCELED);

            int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
            appendErrorMessage(getMessage(msgID));

            processingStopTime = System.currentTimeMillis();

            logAddResponse(this);
            return;
          }
          else if (preOpResult.sendResponseImmediately())
          {
            skipPostOperation = true;
            break addProcessing;
          }
        }


        // Check for and handle a request to cancel this operation.
        if (cancelRequest != null)
        {
          indicateCancelled(cancelRequest);
          processingStopTime = System.currentTimeMillis();
          logAddResponse(this);
          return;
        }


        // Actually perform the add operation.  This should also include taking
        // care of any synchronization that might be needed.
        Backend backend = DirectoryServer.getBackend(entryDN);
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage("No backend for entry " + entryDN.toString());
        }
        else
        {
          // If it is not a private backend, then check to see if the server or
          // backend is operating in read-only mode.
          if (! backend.isPrivateBackend())
          {
            switch (DirectoryServer.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_ADD_SERVER_READONLY,
                                              String.valueOf(entryDN)));
                break addProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_ADD_SERVER_READONLY,
                                                String.valueOf(entryDN)));
                  break addProcessing;
                }
            }

            switch (backend.getWritabilityMode())
            {
              case DISABLED:
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(getMessage(MSGID_ADD_BACKEND_READONLY,
                                              String.valueOf(entryDN)));
                break addProcessing;

              case INTERNAL_ONLY:
                if (! (isInternalOperation() || isSynchronizationOperation()))
                {
                  setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                  appendErrorMessage(getMessage(MSGID_ADD_BACKEND_READONLY,
                                                String.valueOf(entryDN)));
                  break addProcessing;
                }
            }
          }


          try
          {
            if (noOp)
            {
              appendErrorMessage(getMessage(MSGID_ADD_NOOP));

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
                    break addProcessing;
                  }
                }
                catch (DirectoryException de)
                {
                  if (debugEnabled())
                  {
                    TRACER.debugCaught(DebugLogLevel.ERROR, de);
                  }

                  logError(ErrorLogCategory.SYNCHRONIZATION,
                           ErrorLogSeverity.SEVERE_ERROR,
                           MSGID_ADD_SYNCH_PREOP_FAILED, getConnectionID(),
                           getOperationID(), getExceptionMessage(de));

                  setResponseData(de);
                  break addProcessing;
                }
              }

              backend.addEntry(entry, this);
            }

            if (postReadRequest != null)
            {
              Entry addedEntry = entry.duplicate(true);

              if (! postReadRequest.allowsAttribute(
                         DirectoryServer.getObjectClassAttributeType()))
              {
                addedEntry.removeAttribute(
                     DirectoryServer.getObjectClassAttributeType());
              }

              if (! postReadRequest.returnAllUserAttributes())
              {
                Iterator<AttributeType> iterator =
                     addedEntry.getUserAttributes().keySet().iterator();
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
                     addedEntry.getOperationalAttributes().keySet().iterator();
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
              SearchResultEntry searchEntry = new SearchResultEntry(addedEntry);
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
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            setResultCode(de.getResultCode());
            appendErrorMessage(de.getErrorMessage());
            setMatchedDN(de.getMatchedDN());
            setReferralURLs(de.getReferralURLs());

            break addProcessing;
          }
          catch (CancelledOperationException coe)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, coe);
            }

            CancelResult cancelResult = coe.getCancelResult();

            setCancelResult(cancelResult);
            setResultCode(cancelResult.getResultCode());

            String message = coe.getMessage();
            if ((message != null) && (message.length() > 0))
            {
              appendErrorMessage(message);
            }

            break addProcessing;
          }
        }
      }
      finally
      {
        if (entryLock != null)
        {
          LockManager.unlock(entryDN, entryLock);
        }

        if (parentLock != null)
        {
          LockManager.unlock(parentDN, parentLock);
        }


        for (SynchronizationProvider provider :
             DirectoryServer.getSynchronizationProviders())
        {
          try
          {
            provider.doPostOperation(this);
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ErrorLogCategory.SYNCHRONIZATION,
                     ErrorLogSeverity.SEVERE_ERROR,
                     MSGID_ADD_SYNCH_POSTOP_FAILED, getConnectionID(),
                     getOperationID(), getExceptionMessage(de));

            setResponseData(de);
            break;
          }
        }
      }
    }


    // Indicate that it is now too late to attempt to cancel the operation.
    setCancelResult(CancelResult.TOO_LATE);


    // Invoke the post-operation add plugins.
    if (! skipPostOperation)
    {
      // FIXME -- Should this also be done while holding the locks?
      PostOperationPluginResult postOpResult =
           pluginConfigManager.invokePostOperationAddPlugins(this);
      if (postOpResult.connectionTerminated())
      {
        // There's no point in continuing with anything.  Log the result and
        // return.
        setResultCode(ResultCode.CANCELED);

        int msgID = MSGID_CANCELED_BY_PREOP_DISCONNECT;
        appendErrorMessage(getMessage(msgID));

        processingStopTime = System.currentTimeMillis();

        logAddResponse(this);
        return;
      }
    }


    // Notify any change notification listeners that might be registered with
    // the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (ChangeNotificationListener changeListener :
           DirectoryServer.getChangeNotificationListeners())
      {
        try
        {
          changeListener.handleAddOperation(this, entry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ADD_ERROR_NOTIFYING_CHANGE_LISTENER;
          String message = getMessage(msgID, getExceptionMessage(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
        }
      }
    }


    // Stop the processing timer.
    processingStopTime = System.currentTimeMillis();


    // Send the add response to the client.
    getClientConnection().sendResponse(this);


    // Log the add response.
    logAddResponse(this);


    // Notify any persistent searches that might be registered with the server.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      for (PersistentSearch persistentSearch :
           DirectoryServer.getPersistentSearches())
      {
        try
        {
          persistentSearch.processAdd(this, entry);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_ADD_ERROR_NOTIFYING_PERSISTENT_SEARCH;
          String message = getMessage(msgID, String.valueOf(persistentSearch),
                                      getExceptionMessage(e));
          logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);

          DirectoryServer.deregisterPersistentSearch(persistentSearch);
        }
      }
    }


    // Invoke the post-response add plugins.
    pluginConfigManager.invokePostResponseAddPlugins(this);
  }



  /**
   * Adds the provided objectClass to the entry, along with its superior classes
   * if appropriate.
   *
   * @param  objectClass  The objectclass to add to the entry.
   */
  private final void addObjectClassChain(ObjectClass objectClass)
  {
    if (! objectClasses.containsKey(objectClass))
    {
      objectClasses.put(objectClass, objectClass.getNameOrOID());
    }

    ObjectClass superiorClass = objectClass.getSuperiorClass();
    if ((superiorClass != null) &&
        (! objectClasses.containsKey(superiorClass)))
    {
      addObjectClassChain(superiorClass);
    }
  }



  /**
   * Performs all password policy processing necessary for the provided add
   * operation.
   *
   * @param  passwordPolicy  The password policy associated with the entry to be
   *                         added.
   * @param  userEntry       The user entry being added.
   *
   * @throws  DirectoryException  If a problem occurs while performing password
   *                              policy processing for the add operation.
   */
  private final void handlePasswordPolicy(PasswordPolicy passwordPolicy,
                                          Entry userEntry)
         throws DirectoryException
  {
    // See if a password was specified.
    AttributeType passwordAttribute = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = userEntry.getAttribute(passwordAttribute);
    if ((attrList == null) || attrList.isEmpty())
    {
      // The entry doesn't have a password, so no action is required.
      return;
    }
    else if (attrList.size() > 1)
    {
      // This must mean there are attribute options, which we won't allow for
      // passwords.
      int msgID = MSGID_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED;
      String message = getMessage(msgID, passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    Attribute passwordAttr = attrList.get(0);
    if (passwordAttr.hasOptions())
    {
      int msgID = MSGID_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED;
      String message = getMessage(msgID, passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    LinkedHashSet<AttributeValue> values = passwordAttr.getValues();
    if (values.isEmpty())
    {
      // This will be treated the same as not having a password.
      return;
    }

    if ((! passwordPolicy.allowMultiplePasswordValues()) && (values.size() > 1))
    {
      // FIXME -- What if they're pre-encoded and might all be the same?
      int    msgID   = MSGID_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED;
      String message = getMessage(msgID, passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
    }

    CopyOnWriteArrayList<PasswordStorageScheme> defaultStorageSchemes =
         passwordPolicy.getDefaultStorageSchemes();
    LinkedHashSet<AttributeValue> newValues =
         new LinkedHashSet<AttributeValue>(defaultStorageSchemes.size());
    for (AttributeValue v : values)
    {
      ByteString value = v.getValue();

      // See if the password is pre-encoded.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        if (AuthPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            newValues.add(v);
            continue;
          }
          else
          {
            int    msgID   = MSGID_PWPOLICY_PREENCODED_NOT_ALLOWED;
            String message = getMessage(msgID,
                                        passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }
      }
      else
      {
        if (UserPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            newValues.add(v);
            continue;
          }
          else
          {
            int    msgID   = MSGID_PWPOLICY_PREENCODED_NOT_ALLOWED;
            String message = getMessage(msgID,
                                        passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }
      }


      // See if the password passes validation.  We should only do this if
      // validation should be performed for administrators.
      if (! passwordPolicy.skipValidationForAdministrators())
      {
        // There are never any current passwords for an add operation.
        HashSet<ByteString> currentPasswords = new HashSet<ByteString>(0);
        StringBuilder invalidReason = new StringBuilder();
        for (PasswordValidator<?> validator :
             passwordPolicy.getPasswordValidators().values())
        {
          if (! validator.passwordIsAcceptable(value, currentPasswords, this,
                                               userEntry, invalidReason))
          {
            int    msgID   = MSGID_PWPOLICY_VALIDATION_FAILED;
            String message = getMessage(msgID, passwordAttribute.getNameOrOID(),
                                        String.valueOf(invalidReason));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
          }
        }
      }


      // Encode the password.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodeAuthPassword(value);
          newValues.add(new AttributeValue(passwordAttribute, encodedValue));
        }
      }
      else
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodePasswordWithScheme(value);
          newValues.add(new AttributeValue(passwordAttribute, encodedValue));
        }
      }
    }


    // Put the new encoded values in the entry.
    passwordAttr.setValues(newValues);


    // Set the password changed time attribute.
    ByteString timeString =
         new ASN1OctetString(TimeThread.getGeneralizedTime());
    AttributeType changedTimeType =
         DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_CHANGED_TIME_LC);
    if (changedTimeType == null)
    {
      changedTimeType = DirectoryServer.getDefaultAttributeType(
                                             OP_ATTR_PWPOLICY_CHANGED_TIME);
    }

    LinkedHashSet<AttributeValue> changedTimeValues =
         new LinkedHashSet<AttributeValue>(1);
    changedTimeValues.add(new AttributeValue(changedTimeType, timeString));

    ArrayList<Attribute> changedTimeList = new ArrayList<Attribute>(1);
    changedTimeList.add(new Attribute(changedTimeType,
                                      OP_ATTR_PWPOLICY_CHANGED_TIME,
                                      changedTimeValues));

    userEntry.putAttribute(changedTimeType, changedTimeList);


    // If we should force change on add, then set the appropriate flag.
    if (passwordPolicy.forceChangeOnAdd())
    {
      AttributeType resetType =
           DirectoryServer.getAttributeType(OP_ATTR_PWPOLICY_RESET_REQUIRED_LC);
      if (resetType == null)
      {
        resetType = DirectoryServer.getDefaultAttributeType(
                                         OP_ATTR_PWPOLICY_RESET_REQUIRED);
      }

      LinkedHashSet<AttributeValue> resetValues = new
           LinkedHashSet<AttributeValue>(1);
      resetValues.add(BooleanSyntax.createBooleanValue(true));

      ArrayList<Attribute> resetList = new ArrayList<Attribute>(1);
      resetList.add(new Attribute(resetType, OP_ATTR_PWPOLICY_RESET_REQUIRED,
                                  resetValues));
      userEntry.putAttribute(resetType, resetList);
    }
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
  @Override()
  public final CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  protected boolean setCancelRequest(CancelRequest cancelRequest)
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
    buffer.append("AddOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }
}

