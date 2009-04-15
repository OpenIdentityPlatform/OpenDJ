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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;



import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.util.TimeThread;



/**
 * This class defines an operation used to add an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendAddOperation
       extends AddOperationWrapper
       implements PreOperationAddOperation, PostOperationAddOperation,
                  PostResponseAddOperation, PostSynchronizationAddOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * The backend in which the entry is to be added.
   */
  protected Backend backend;

  /**
   * Indicates whether the request includes the LDAP no-op control.
   */
  protected boolean noOp;

  /**
   * The DN of the entry to be added.
   */
  protected DN entryDN;

  /**
   * The entry being added to the server.
   */
  protected Entry entry;

  /**
   * The post-read request control included in the request, if applicable.
   */
  protected LDAPPostReadRequestControl postReadRequest;

  /**
   * The set of object classes for the entry to add.
   */
  protected Map<ObjectClass, String> objectClasses;

  /**
   * The set of operational attributes for the entry to add.
   */
  protected Map<AttributeType,List<Attribute>> operationalAttributes;

  /**
   * The set of user attributes for the entry to add.
   */
  protected Map<AttributeType,List<Attribute>> userAttributes;



  /**
   * Creates a new operation that may be used to add a new entry in a
   * local backend of the Directory Server.
   *
   * @param add The operation to enhance.
   */
  public LocalBackendAddOperation(AddOperation add)
  {
    super(add);

    LocalBackendWorkflowElement.attachLocalOperation (add, this);
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
   * Process this add operation against a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalAdd(final LocalBackendWorkflowElement wfe)
      throws CanceledOperationException
  {
    boolean executePostOpPlugins = false;

    this.backend = wfe.getBackend();
    ClientConnection clientConnection = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Create a labeled block of code that we can break out of if a problem is
    // detected.
addProcessing:
    {
      // Process the entry DN and set of attributes to convert them from their
      // raw forms as provided by the client to the forms required for the rest
      // of the add processing.
      entryDN = getEntryDN();
      if (entryDN == null)
      {
        break addProcessing;
      }

      objectClasses = getObjectClasses();
      userAttributes = getUserAttributes();
      operationalAttributes = getOperationalAttributes();

      if ((objectClasses == null ) || (userAttributes == null) ||
          (operationalAttributes == null))
      {
        break addProcessing;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Grab a read lock on the parent entry, if there is one.  We need to do
      // this to ensure that the parent is not deleted or renamed while this add
      // is in progress, and we could also need it to check the entry against
      // a DIT structure rule.
      Lock parentLock = null;
      Lock entryLock  = null;

      DN parentDN = entryDN.getParentDNInSuffix();
      try
      {
        parentLock = lockParent(parentDN);
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

      try
      {
        // Check for a request to cancel this operation.
        checkIfCanceled(false);


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
          appendErrorMessage(ERR_ADD_CANNOT_LOCK_ENTRY.get(
                                  String.valueOf(entryDN)));

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
            if (! result.continueProcessing())
            {
              setResultCode(result.getResultCode());
              appendErrorMessage(result.getErrorMessage());
              setMatchedDN(result.getMatchedDN());
              setReferralURLs(result.getReferralURLs());
              break addProcessing;
            }
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            logError(ERR_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED.get(
                          getConnectionID(), getOperationID(),
                          getExceptionMessage(de)));

            setResponseData(de);
            break addProcessing;
          }
        }

        for (AttributeType at : userAttributes.keySet())
        {
          // If the attribute type is marked "NO-USER-MODIFICATION" then fail
          // unless this is an internal operation or is related to
          // synchronization in some way.
          // This must be done before running the password policy code
          // and any other code that may add attributes marked as
          // "NO-USER-MODIFICATION"
          //
          // Note that doing this checks at this time
          // of the processing does not make it possible for pre-parse plugins
          // to add NO-USER-MODIFICATION attributes to the entry.
          if (at.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                                      String.valueOf(entryDN),
                                      at.getNameOrOID()));

              break addProcessing;
            }
          }
        }

        for (AttributeType at : operationalAttributes.keySet())
        {
          if (at.isNoUserModification())
          {
            if (! (isInternalOperation() || isSynchronizationOperation()))
            {
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                                      String.valueOf(entryDN),
                                      at.getNameOrOID()));

              break addProcessing;
            }
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
            appendErrorMessage(ERR_ADD_ENTRY_ALREADY_EXISTS.get(
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

          setResponseData(de);
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
              appendErrorMessage(ERR_ADD_NO_PARENT.get(String.valueOf(entryDN),
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

            setResponseData(de);
            break addProcessing;
          }
        }


        // Check to make sure that all of the RDN attributes are included as
        // attribute values.  If not, then either add them or report an error.
        try
        {
          addRDNAttributesIfNecessary();
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

        //Add any superior objectclass(s) missing in an entries
        //objectclass map.
        addSuperiorObjectClasses(objectClasses);

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

          appendErrorMessage(
               ERR_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
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
          try
          {
            handlePasswordPolicy();
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


        // If the server is configured to check schema and the
        // operation is not a synchronization operation,
        // check to see if the entry is valid according to the server schema,
        // and also whether its attributes are valid according to their syntax.
        if ((DirectoryServer.checkSchema()) && (! isSynchronizationOperation()))
        {
          try
          {
            checkSchema(parentEntry);
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


        // Get the backend in which the add is to be performed.
        if (backend == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(Message.raw("No backend for entry " +
                                         entryDN.toString())); // TODO: i18n
          break addProcessing;
        }


        // Check to see if there are any controls in the request. If so, then
        // see if there is any special processing required.
        try
        {
          processControls(parentDN);
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


        // Check to see if the client has permission to perform the add.

        // FIXME: for now assume that this will check all permission
        // pertinent to the operation. This includes proxy authorization
        // and any other controls specified.

        // FIXME: earlier checks to see if the entry already exists or
        // if the parent entry does not exist may have already exposed
        // sensitive information to the client.
        if (AccessControlConfigManager.getInstance().getAccessControlHandler().
                 isAllowed(this) == false)
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                                  String.valueOf(entryDN)));
          break addProcessing;
        }

        // Check for a request to cancel this operation.
        checkIfCanceled(false);

        // If the operation is not a synchronization operation,
        // Invoke the pre-operation add plugins.
        if (! isSynchronizationOperation())
        {
          executePostOpPlugins = true;
          PluginResult.PreOperation preOpResult =
            pluginConfigManager.invokePreOperationAddPlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break addProcessing;
          }
        }


        // If it is not a private backend, then check to see if the server or
        // backend is operating in read-only mode.
        if (! backend.isPrivateBackend())
        {
          switch (DirectoryServer.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                                      String.valueOf(entryDN)));
              break addProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_ADD_SERVER_READONLY.get(
                                        String.valueOf(entryDN)));
                break addProcessing;
              }
              break;
          }

          switch (backend.getWritabilityMode())
          {
            case DISABLED:
              setResultCode(ResultCode.UNWILLING_TO_PERFORM);
              appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                                      String.valueOf(entryDN)));
              break addProcessing;

            case INTERNAL_ONLY:
              if (! (isInternalOperation() || isSynchronizationOperation()))
              {
                setResultCode(ResultCode.UNWILLING_TO_PERFORM);
                appendErrorMessage(ERR_ADD_BACKEND_READONLY.get(
                                        String.valueOf(entryDN)));
                break addProcessing;
              }
              break;
          }
        }


        try
        {
          if (noOp)
          {
            appendErrorMessage(INFO_ADD_NOOP.get());
            setResultCode(ResultCode.NO_OPERATION);
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
                if (! result.continueProcessing())
                {
                  setResultCode(result.getResultCode());
                  appendErrorMessage(result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  break addProcessing;
                }
              }
              catch (DirectoryException de)
              {
                if (debugEnabled())
                {
                  TRACER.debugCaught(DebugLogLevel.ERROR, de);
                }

                logError(ERR_ADD_SYNCH_PREOP_FAILED.get(getConnectionID(),
                              getOperationID(), getExceptionMessage(de)));
                setResponseData(de);
                break addProcessing;
              }
            }

            backend.addEntry(entry, this);
          }

          if (postReadRequest != null)
          {
            addPostReadResponse();
          }


          if (! noOp)
          {
            setResultCode(ResultCode.SUCCESS);
          }
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
      finally
      {
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

            logError(ERR_ADD_SYNCH_POSTOP_FAILED.get(getConnectionID(),
                getOperationID(), getExceptionMessage(de)));
            setResponseData(de);
            break;
          }
        }

        if (entryLock != null)
        {
          LockManager.unlock(entryDN, entryLock);
        }

        if (parentLock != null)
        {
          LockManager.unlock(parentDN, parentLock);
        }
      }
    }

    // Invoke the post-operation or post-synchronization add plugins.
    if (isSynchronizationOperation())
    {
      if (getResultCode() == ResultCode.SUCCESS)
      {
        pluginConfigManager.invokePostSynchronizationAddPlugins(this);
      }
    }
    else if (executePostOpPlugins)
    {
      // FIXME -- Should this also be done while holding the locks?
      PluginResult.PostOperation postOpResult =
          pluginConfigManager.invokePostOperationAddPlugins(this);
      if(!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
        return;
      }
    }

    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {
        public void run()
        {
          // Notify persistent searches.
          for (PersistentSearch psearch : wfe.getPersistentSearches()) {
            psearch.processAdd(entry, getChangeNumber());
          }

          // Notify change listeners.
          for (ChangeNotificationListener changeListener : DirectoryServer
              .getChangeNotificationListeners())
          {
            try
            {
              changeListener.handleAddOperation(LocalBackendAddOperation.this,
                  entry);
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              logError(ERR_ADD_ERROR_NOTIFYING_CHANGE_LISTENER
                  .get(getExceptionMessage(e)));
            }
          }
        }
      });
    }
  }



  /**
   * Acquire a read lock on the parent of the entry to add.
   *
   * @return  The acquired read lock.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              acquire the lock.
   */
  private Lock lockParent(DN parentDN)
          throws DirectoryException
  {
    Lock parentLock = null;

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
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     ERR_ADD_CANNOT_ADD_ROOT_DSE.get());
      }
      else
      {
        // The entry doesn't have a parent but isn't a suffix.  This is not
        // allowed.
        throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
                                     ERR_ADD_ENTRY_NOT_SUFFIX.get(
                                          String.valueOf(entryDN)));
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
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     ERR_ADD_CANNOT_LOCK_PARENT.get(
                                          String.valueOf(entryDN),
                                          String.valueOf(parentDN)));
      }
    }

    return parentLock;
  }



  /**
   * Adds any missing RDN attributes to the entry.
   *
   * @throws  DirectoryException  If the entry is missing one or more RDN
   *                              attributes and the server is configured to
   *                              reject such entries.
   */
  protected void addRDNAttributesIfNecessary()
          throws DirectoryException
  {
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
            attrList = new ArrayList<Attribute>();
            attrList.add(Attributes.create(t, n, v));
            operationalAttributes.put(t, attrList);
          }
          else
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                                              String.valueOf(entryDN), n));
          }
        }
        else
        {
          boolean found = false;
          for (int j = 0; j < attrList.size(); j++) {
            Attribute a = attrList.get(j);

            if (a.hasOptions())
            {
              continue;
            }

            if (!a.contains(v))
            {
              AttributeBuilder builder = new AttributeBuilder(a);
              builder.add(v);
              attrList.set(j, builder.toAttribute());
            }

            found = true;
            break;
          }

          if (!found)
          {
            if (isSynchronizationOperation() ||
                DirectoryServer.addMissingRDNAttributes())
            {
              attrList.add(Attributes.create(t, n, v));
            }
            else
            {
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                           ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                                                String.valueOf(entryDN), n));
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
            attrList = new ArrayList<Attribute>();
            attrList.add(Attributes.create(t, n, v));
            userAttributes.put(t, attrList);
          }
          else
          {
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                                              String.valueOf(entryDN),n));
          }
        }
        else
        {
          boolean found = false;
          for (int j = 0; j < attrList.size(); j++) {
            Attribute a = attrList.get(j);

            if (a.hasOptions())
            {
              continue;
            }

            if (!a.contains(v))
            {
              AttributeBuilder builder = new AttributeBuilder(a);
              builder.add(v);
              attrList.set(j, builder.toAttribute());
            }

            found = true;
            break;
          }

          if (!found)
          {
            if (isSynchronizationOperation() ||
                DirectoryServer.addMissingRDNAttributes())
            {
              attrList.add(Attributes.create(t, n, v));
            }
            else
            {
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                           ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                                                String.valueOf(entryDN),n));
            }
          }
        }
      }
    }
  }



  /**
   * Adds the provided objectClass to the entry, along with its superior classes
   * if appropriate.
   *
   * @param  objectClass  The objectclass to add to the entry.
   */
  public final void addObjectClassChain(ObjectClass objectClass)
  {
    Map<ObjectClass, String> objectClasses = getObjectClasses();
    if (objectClasses != null){
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
  }



  /**
   * Performs all password policy processing necessary for the provided add
   * operation.
   *
   * @throws  DirectoryException  If a problem occurs while performing password
   *                              policy processing for the add operation.
   */
  public final void handlePasswordPolicy()
         throws DirectoryException
  {
    // FIXME -- We need to check to see if the password policy subentry
    //          might be specified virtually rather than as a real
    //          attribute.
    PasswordPolicy passwordPolicy = null;
    List<Attribute> pwAttrList =
         entry.getAttribute(OP_ATTR_PWPOLICY_POLICY_DN);
    if ((pwAttrList != null) && (! pwAttrList.isEmpty()))
    {
      Attribute a = pwAttrList.get(0);
      Iterator<AttributeValue> iterator = a.iterator();
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

          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       ERR_ADD_INVALID_PWPOLICY_DN_SYNTAX.get(
                                            String.valueOf(entryDN),
                                           de.getMessageObject()));
        }

        passwordPolicy = DirectoryServer.getPasswordPolicy(policyDN);
        if (passwordPolicy == null)
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                       ERR_ADD_NO_SUCH_PWPOLICY.get(
                                            String.valueOf(entryDN),
                                         String.valueOf(policyDN)));
        }
      }
    }

    if (passwordPolicy == null)
    {
      passwordPolicy = DirectoryServer.getDefaultPasswordPolicy();
    }

    // See if a password was specified.
    AttributeType passwordAttribute = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = entry.getAttribute(passwordAttribute);
    if ((attrList == null) || attrList.isEmpty())
    {
      // The entry doesn't have a password, so no action is required.
      return;
    }
    else if (attrList.size() > 1)
    {
      // This must mean there are attribute options, which we won't allow for
      // passwords.
      Message message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    Attribute passwordAttr = attrList.get(0);
    if (passwordAttr.hasOptions())
    {
      Message message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    if (passwordAttr.isEmpty())
    {
      // This will be treated the same as not having a password.
      return;
    }

    if ((!passwordPolicy.allowMultiplePasswordValues())
        && (passwordAttr.size() > 1))
    {
      // FIXME -- What if they're pre-encoded and might all be the
      // same?
      addPWPolicyControl(PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED);

      Message message = ERR_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED
          .get(passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }

    CopyOnWriteArrayList<PasswordStorageScheme<?>> defaultStorageSchemes =
         passwordPolicy.getDefaultStorageSchemes();
    AttributeBuilder builder = new AttributeBuilder(passwordAttr, true);
    builder.setInitialCapacity(defaultStorageSchemes.size());
    for (AttributeValue v : passwordAttr)
    {
      ByteString value = v.getValue();

      // See if the password is pre-encoded.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        if (AuthPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            builder.add(v);
            continue;
          }
          else
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(
                passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }
      else
      {
        if (UserPasswordSyntax.isEncoded(value))
        {
          if (passwordPolicy.allowPreEncodedPasswords())
          {
            builder.add(v);
            continue;
          }
          else
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(
                passwordAttribute.getNameOrOID());
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }


      // See if the password passes validation.  We should only do this if
      // validation should be performed for administrators.
      if (! passwordPolicy.skipValidationForAdministrators())
      {
        // There are never any current passwords for an add operation.
        HashSet<ByteString> currentPasswords = new HashSet<ByteString>(0);
        MessageBuilder invalidReason = new MessageBuilder();
        for (PasswordValidator<?> validator :
             passwordPolicy.getPasswordValidators().values())
        {
          if (! validator.passwordIsAcceptable(value, currentPasswords, this,
                                               entry, invalidReason))
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_VALIDATION_FAILED.
                get(passwordAttribute.getNameOrOID(),
                    String.valueOf(invalidReason));
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
        }
      }


      // Encode the password.
      if (passwordPolicy.usesAuthPasswordSyntax())
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodeAuthPassword(value);
          builder.add(AttributeValues.create(
              passwordAttribute, encodedValue));
        }
      }
      else
      {
        for (PasswordStorageScheme s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodePasswordWithScheme(value);
          builder.add(AttributeValues.create(
              passwordAttribute, encodedValue));
        }
      }
    }


    // Put the new encoded values in the entry.
    entry.replaceAttribute(builder.toAttribute());


    // Set the password changed time attribute.
    ArrayList<Attribute> changedTimeList = new ArrayList<Attribute>(1);
    Attribute changedTime = Attributes.create(
        OP_ATTR_PWPOLICY_CHANGED_TIME, TimeThread.getGeneralizedTime());
    changedTimeList.add(changedTime);
    entry.putAttribute(changedTime.getAttributeType(), changedTimeList);


    // If we should force change on add, then set the appropriate flag.
    if (passwordPolicy.forceChangeOnAdd())
    {
      addPWPolicyControl(PasswordPolicyErrorType.CHANGE_AFTER_RESET);

      ArrayList<Attribute> resetList = new ArrayList<Attribute>(1);
      Attribute reset = Attributes.create(
          OP_ATTR_PWPOLICY_RESET_REQUIRED, "TRUE");
      resetList.add(reset);
      entry.putAttribute(reset.getAttributeType(), resetList);
    }
  }



  /**
   * Adds a password policy response control if the corresponding request
   * control was included.
   *
   * @param  errorType  The error type to use for the response control.
   */
  private void addPWPolicyControl(PasswordPolicyErrorType errorType)
  {
    for (Control c : getRequestControls())
    {
      if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
      {
        addResponseControl(new PasswordPolicyResponseControl(null, 0,
                                                             errorType));
      }
    }
  }



  /**
   * Verifies that the entry to be added conforms to the server schema.
   *
   * @param  parentEntry  The parent of the entry to add.
   *
   * @throws  DirectoryException  If the entry violates the server schema
   *                              configuration.
   */
  protected void checkSchema(Entry parentEntry)
          throws DirectoryException
  {
    MessageBuilder invalidReason = new MessageBuilder();
    if (! entry.conformsToSchema(parentEntry, true, true, true, invalidReason))
    {
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION,
                                   invalidReason.toMessage());
    }
    else
    {
      switch (DirectoryServer.getSyntaxEnforcementPolicy())
      {
        case REJECT:
          invalidReason = new MessageBuilder();
          for (List<Attribute> attrList : userAttributes.values())
          {
            for (Attribute a : attrList)
            {
              AttributeSyntax<?> syntax = a.getAttributeType().getSyntax();
              if (syntax != null)
              {
                for (AttributeValue v : a)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(), invalidReason))
                  {
                    Message message = WARN_ADD_OP_INVALID_SYNTAX.get(
                                        String.valueOf(entryDN),
                                        String.valueOf(v.getValue().toString()),
                                        String.valueOf(a.getName()),
                                        String.valueOf(invalidReason));

                    throw new DirectoryException(
                                   ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message);
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
              AttributeSyntax<?> syntax = a.getAttributeType().getSyntax();
              if (syntax != null)
              {
                for (AttributeValue v : a)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(),
                                                 invalidReason))
                  {
                    Message message = WARN_ADD_OP_INVALID_SYNTAX.
                        get(String.valueOf(entryDN),
                            String.valueOf(v.getValue().toString()),
                            String.valueOf(a.getName()),
                            String.valueOf(invalidReason));

                    throw new DirectoryException(
                                   ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message);
                  }
                }
              }
            }
          }

          break;


        case WARN:
          invalidReason = new MessageBuilder();
          for (List<Attribute> attrList : userAttributes.values())
          {
            for (Attribute a : attrList)
            {
              AttributeSyntax<?> syntax = a.getAttributeType().getSyntax();
              if (syntax != null)
              {
                for (AttributeValue v : a)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(),
                                                 invalidReason))
                  {
                    logError(WARN_ADD_OP_INVALID_SYNTAX.get(
                                  String.valueOf(entryDN),
                                  String.valueOf(v.getValue().toString()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason)));
                  }
                }
              }
            }
          }

          for (List<Attribute> attrList : operationalAttributes.values())
          {
            for (Attribute a : attrList)
            {
              AttributeSyntax<?> syntax = a.getAttributeType().getSyntax();
              if (syntax != null)
              {
                for (AttributeValue v : a)
                {
                  if (! syntax.valueIsAcceptable(v.getValue(),
                                                 invalidReason))
                  {
                    logError(WARN_ADD_OP_INVALID_SYNTAX.get(
                                  String.valueOf(entryDN),
                                  String.valueOf(v.getValue().toString()),
                                  String.valueOf(a.getName()),
                                  String.valueOf(invalidReason)));
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
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     WARN_ADD_ATTR_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          at.getNameOrOID()));
      }
    }

    for (AttributeType at : operationalAttributes.keySet())
    {
      if (at.isObsolete())
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     WARN_ADD_ATTR_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          at.getNameOrOID()));
      }
    }

    for (ObjectClass oc : objectClasses.keySet())
    {
      if (oc.isObsolete())
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     WARN_ADD_OC_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          oc.getNameOrOID()));
      }
    }
  }



  /**
   * Processes the set of controls contained in the add request.
   *
   * @param  parentDN  The DN of the parent of the entry to add.
   *
   * @throws  DirectoryException  If there is a problem with any of the
   *                              request controls.
   */
  protected void processControls(DN parentDN)
          throws DirectoryException
  {
    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (!AccessControlConfigManager.getInstance().
                getAccessControlHandler().isAllowed(parentDN, this, c))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        if (oid.equals(OID_LDAP_ASSERTION))
        {
          LDAPAssertionRequestControl assertControl =
            getRequestControl(LDAPAssertionRequestControl.DECODER);

          try
          {
            // FIXME -- We need to determine whether the current user has
            //          permission to make this determination.
            SearchFilter filter = assertControl.getSearchFilter();
            if (! filter.matchesEntry(entry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_ADD_ASSERTION_FAILED.get(
                                                String.valueOf(entryDN)));
            }
          }
          catch (DirectoryException de)
          {
            if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
            {
              throw de;
            }

            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                           ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));
          }
        }
        else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
        {
          noOp = true;
        }
        else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
        {
          postReadRequest =
                getRequestControl(LDAPPostReadRequestControl.DECODER);
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! getClientConnection().hasPrivilege(Privilege.PROXIED_AUTH,
                                                   this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV1Control proxyControl =
              getRequestControl(ProxiedAuthV1Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V2))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! getClientConnection().hasPrivilege(Privilege.PROXIED_AUTH,
                                                   this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV2Control proxyControl =
              getRequestControl(ProxiedAuthV2Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.nullDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getDN());
          }
        }
        else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
        {
          // We don't need to do anything here because it's already handled
          // in LocalBackendAddOperation.handlePasswordPolicy().
        }

        // NYI -- Add support for additional controls.
        else if (c.isCritical())
        {
          if ((backend == null) || (! backend.supportsControl(oid)))
          {
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_ADD_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN), oid));
          }
        }
      }
    }
  }



  /**
   * Adds the post-read response control to the response.
   */
  protected void addPostReadResponse()
  {
    Entry addedEntry = entry.duplicate(true);

    if (! postReadRequest.allowsAttribute(
               DirectoryServer.getObjectClassAttributeType()))
    {
      addedEntry.removeAttribute(DirectoryServer.getObjectClassAttributeType());
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
         new LDAPPostReadResponseControl(searchEntry);
    addResponseControl(responseControl);
  }
}

