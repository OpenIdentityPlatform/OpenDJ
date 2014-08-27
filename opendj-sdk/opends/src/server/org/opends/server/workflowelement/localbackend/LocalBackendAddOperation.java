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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.*;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostSynchronizationAddOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.util.TimeThread;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines an operation used to add an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendAddOperation
       extends AddOperationWrapper
       implements PreOperationAddOperation, PostOperationAddOperation,
                  PostResponseAddOperation, PostSynchronizationAddOperation
{
  /** The tracer object for the debug logger. */
  private static final DebugTracer TRACER = getTracer();

  /** The backend in which the entry is to be added. */
  private Backend<?> backend;

  /** Indicates whether the request includes the LDAP no-op control. */
  private boolean noOp;

  /** The DN of the entry to be added. */
  private DN entryDN;

  /** The entry being added to the server. */
  private Entry entry;

  /** The post-read request control included in the request, if applicable. */
  private LDAPPostReadRequestControl postReadRequest;

  /** The set of object classes for the entry to add. */
  private Map<ObjectClass, String> objectClasses;

  /** The set of operational attributes for the entry to add. */
  private Map<AttributeType, List<Attribute>> operationalAttributes;

  /** The set of user attributes for the entry to add. */
  private Map<AttributeType, List<Attribute>> userAttributes;

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
  @Override
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
    this.backend = wfe.getBackend();
    ClientConnection clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      BooleanHolder executePostOpPlugins = new BooleanHolder(false);
      processAdd(clientConnection, executePostOpPlugins);

      PluginConfigManager pluginConfigManager =
          DirectoryServer.getPluginConfigManager();

      // Invoke the post-operation or post-synchronization add plugins.
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          pluginConfigManager.invokePostSynchronizationAddPlugins(this);
        }
      }
      else if (executePostOpPlugins.value)
      {
        // FIXME -- Should this also be done while holding the locks?
        PluginResult.PostOperation postOpResult =
            pluginConfigManager.invokePostOperationAddPlugins(this);
        if (!postOpResult.continueProcessing())
        {
          setResultCode(postOpResult.getResultCode());
          appendErrorMessage(postOpResult.getErrorMessage());
          setMatchedDN(postOpResult.getMatchedDN());
          setReferralURLs(postOpResult.getReferralURLs());
          return;
        }
      }
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }

    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {
        @Override
        public void run()
        {
          for (PersistentSearch psearch : wfe.getPersistentSearches())
          {
            psearch.processAdd(entry);
          }
        }
      });
    }
  }

  private void processAdd(ClientConnection clientConnection,
      BooleanHolder executePostOpPlugins) throws CanceledOperationException
  {
    // Process the entry DN and set of attributes to convert them from their
    // raw forms as provided by the client to the forms required for the rest
    // of the add processing.
    entryDN = getEntryDN();
    if (entryDN == null)
    {
      return;
    }

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Grab a read lock on the parent entry, if there is one. We need to do
    // this to ensure that the parent is not deleted or renamed while this add
    // is in progress, and we could also need it to check the entry against
    // a DIT structure rule.
    Lock entryLock = null;
    Lock parentLock = null;
    DN parentDN = null;

    try
    {
      parentDN = entryDN.getParentDNInSuffix();
      parentLock = lockParent(parentDN);

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Grab a write lock on the target entry. We'll need to do this
      // eventually anyway, and we want to make sure that the two locks are
      // always released when exiting this method, no matter what. Since
      // the entry shouldn't exist yet, locking earlier than necessary
      // shouldn't cause a problem.
      entryLock = LockManager.lockWrite(entryDN);
      if (entryLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_ADD_CANNOT_LOCK_ENTRY.get(
            String.valueOf(entryDN)));
        return;
      }

      // Invoke any conflict resolution processing that might be needed by the
      // synchronization provider.
      for (SynchronizationProvider<?> provider : DirectoryServer
          .getSynchronizationProviders())
      {
        try
        {
          SynchronizationProviderResult result =
              provider.handleConflictResolution(this);
          if (!result.continueProcessing())
          {
            setResultCode(result.getResultCode());
            appendErrorMessage(result.getErrorMessage());
            setMatchedDN(result.getMatchedDN());
            setReferralURLs(result.getReferralURLs());
            return;
          }
        }
        catch (DirectoryException de)
        {
          logError(ERR_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED.get(
              getConnectionID(), getOperationID(), getExceptionMessage(de)));
          throw de;
        }
      }

      objectClasses = getObjectClasses();
      userAttributes = getUserAttributes();
      operationalAttributes = getOperationalAttributes();

      if (objectClasses == null
          || userAttributes == null
          || operationalAttributes == null)
      {
        return;
      }

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
      if (checkHasReadOnlyAttributes(userAttributes)
          || checkHasReadOnlyAttributes(operationalAttributes))
      {
        return;
      }


      // Check to see if the entry already exists. We do this before
      // checking whether the parent exists to ensure a referral entry
      // above the parent results in a correct referral.
      if (DirectoryServer.entryExists(entryDN))
      {
        setResultCodeAndMessageNoInfoDisclosure(entryDN,
            ResultCode.ENTRY_ALREADY_EXISTS,
            ERR_ADD_ENTRY_ALREADY_EXISTS.get(String.valueOf(entryDN)));
        return;
      }

      // Get the parent entry, if it exists.
      Entry parentEntry = null;
      if (parentDN != null)
      {
        parentEntry = DirectoryServer.getEntry(parentDN);

        if (parentEntry == null)
        {
          final DN matchedDN = findMatchedDN(parentDN);
          setMatchedDN(matchedDN);

          // The parent doesn't exist, so this add can't be successful.
          if (matchedDN != null)
          {
            // check whether matchedDN allows to disclose info
            setResultCodeAndMessageNoInfoDisclosure(matchedDN,
                ResultCode.NO_SUCH_OBJECT, ERR_ADD_NO_PARENT.get(String
                    .valueOf(entryDN), String.valueOf(parentDN)));
          }
          else
          {
            // no matched DN either, so let's return normal error code
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(ERR_ADD_NO_PARENT.get(String.valueOf(entryDN),
                String.valueOf(parentDN)));
          }
          return;
        }
      }

      // Check to make sure that all of the RDN attributes are included as
      // attribute values. If not, then either add them or report an error.
      addRDNAttributesIfNecessary();

      // Add any superior objectclass(s) missing in an entries
      // objectclass map.
      addSuperiorObjectClasses(objectClasses);

      // Create an entry object to encapsulate the set of attributes and
      // objectclasses.
      entry = new Entry(entryDN, objectClasses, userAttributes,
              operationalAttributes);

      // Check to see if the entry includes a privilege specification. If so,
      // then the requester must have the PRIVILEGE_CHANGE privilege.
      AttributeType privType =
          DirectoryServer.getAttributeType(OP_ATTR_PRIVILEGE_NAME, true);
      if (entry.hasAttribute(privType)
          && !clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this))
      {
        appendErrorMessage(ERR_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES
            .get());
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        return;
      }

      // If it's not a synchronization operation, then check
      // to see if the entry contains one or more passwords and if they
      // are valid in accordance with the password policies associated with
      // the user. Also perform any encoding that might be required by
      // password storage schemes.
      if (!isSynchronizationOperation())
      {
        handlePasswordPolicy();
      }

      // If the server is configured to check schema and the
      // operation is not a synchronization operation,
      // check to see if the entry is valid according to the server schema,
      // and also whether its attributes are valid according to their syntax.
      if (DirectoryServer.checkSchema() && !isSynchronizationOperation())
      {
        checkSchema(parentEntry);
      }

      // Get the backend in which the add is to be performed.
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(Message.raw("No backend for entry " + entryDN)); // TODO: i18n
        return;
      }

      // Check to see if there are any controls in the request. If so, then
      // see if there is any special processing required.
      processControls(parentDN);

      // Check to see if the client has permission to perform the add.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists or
      // if the parent entry does not exist may have already exposed
      // sensitive information to the client.
      try
      {
        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(this))
        {
          setResultCodeAndMessageNoInfoDisclosure(entryDN,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                  String.valueOf(entryDN)));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // If the operation is not a synchronization operation,
      // Invoke the pre-operation add plugins.
      if (!isSynchronizationOperation())
      {
        executePostOpPlugins.value = true;
        PluginResult.PreOperation preOpResult =
            DirectoryServer.getPluginConfigManager()
                .invokePreOperationAddPlugins(this);
        if (!preOpResult.continueProcessing())
        {
          setResultCode(preOpResult.getResultCode());
          appendErrorMessage(preOpResult.getErrorMessage());
          setMatchedDN(preOpResult.getMatchedDN());
          setReferralURLs(preOpResult.getReferralURLs());
          return;
        }
      }

      LocalBackendWorkflowElement.checkIfBackendIsWritable(backend, this,
          entryDN, ERR_ADD_SERVER_READONLY, ERR_ADD_BACKEND_READONLY);

      if (noOp)
      {
        appendErrorMessage(INFO_ADD_NOOP.get());
        setResultCode(ResultCode.NO_OPERATION);
      }
      else
      {
        for (SynchronizationProvider<?> provider : DirectoryServer
            .getSynchronizationProviders())
        {
          try
          {
            SynchronizationProviderResult result =
                provider.doPreOperation(this);
            if (!result.continueProcessing())
            {
              setResultCode(result.getResultCode());
              appendErrorMessage(result.getErrorMessage());
              setMatchedDN(result.getMatchedDN());
              setReferralURLs(result.getReferralURLs());
              return;
            }
          }
          catch (DirectoryException de)
          {
            logError(ERR_ADD_SYNCH_PREOP_FAILED.get(getConnectionID(),
                getOperationID(), getExceptionMessage(de)));
            throw de;
          }
        }

        backend.addEntry(entry, this);
      }

      LocalBackendWorkflowElement.addPostReadResponse(this, postReadRequest,
          entry);

      if (!noOp)
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
    }
    finally
    {
      for (SynchronizationProvider<?> provider : DirectoryServer
          .getSynchronizationProviders())
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

  private DN findMatchedDN(DN entryDN)
  {
    try
    {
      DN matchedDN = entryDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (DirectoryServer.entryExists(matchedDN))
        {
          return matchedDN;
        }

        matchedDN = matchedDN.getParentDNInSuffix();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
    return null;
  }

  private boolean checkHasReadOnlyAttributes(
      Map<AttributeType, List<Attribute>> attributes) throws DirectoryException
  {
    for (AttributeType at : attributes.keySet())
    {
      if (at.isNoUserModification()
          && !isInternalOperation()
          && !isSynchronizationOperation())
      {
        setResultCodeAndMessageNoInfoDisclosure(entryDN,
            ResultCode.CONSTRAINT_VIOLATION,
            ERR_ADD_ATTR_IS_NO_USER_MOD.get(
                String.valueOf(entryDN), at.getNameOrOID()));
        return true;
      }
    }
    return false;
  }

  private DirectoryException newDirectoryException(DN entryDN,
      ResultCode resultCode, Message message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, null,
        entryDN, resultCode, message, ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
        ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(String.valueOf(entryDN)));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(DN entryDN,
      ResultCode resultCode, Message message) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        null, entryDN, resultCode, message,
        ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
        ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(String.valueOf(entryDN)));
  }

  /**
   * Acquire a read lock on the parent of the entry to add.
   *
   * @return  The acquired read lock.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              acquire the lock.
   */
  private Lock lockParent(DN parentDN) throws DirectoryException
  {
    if (parentDN != null)
    {
      final Lock parentLock = LockManager.lockRead(parentDN);
      if (parentLock == null)
      {
        throw newDirectoryException(parentDN, ResultCode.BUSY,
            ERR_ADD_CANNOT_LOCK_PARENT.get(
                String.valueOf(entryDN),
                String.valueOf(parentDN)));
      }
      return parentLock;
    }

    // Either this entry is a suffix or doesn't belong in the directory.
    if (DirectoryServer.isNamingContext(entryDN))
    {
      // This is fine.  This entry is one of the configured suffixes.
      return null;
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
          ERR_ADD_ENTRY_NOT_SUFFIX.get(String.valueOf(entryDN)));
    }
  }



  /**
   * Adds any missing RDN attributes to the entry.
   *
   * @throws  DirectoryException  If the entry is missing one or more RDN
   *                              attributes and the server is configured to
   *                              reject such entries.
   */
  private void addRDNAttributesIfNecessary() throws DirectoryException
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
        addRDNAttributesIfNecessary(operationalAttributes, t, v, n);
      }
      else
      {
        addRDNAttributesIfNecessary(userAttributes, t, v, n);
      }
    }
  }



  private void addRDNAttributesIfNecessary(
      Map<AttributeType, List<Attribute>> attributes, AttributeType t,
      AttributeValue v, String n) throws DirectoryException
  {
    List<Attribute> attrList = attributes.get(t);
    if (attrList == null)
    {
      if (isSynchronizationOperation() ||
          DirectoryServer.addMissingRDNAttributes())
      {
        attrList = new ArrayList<Attribute>();
        attrList.add(Attributes.create(t, n, v));
        attributes.put(t, attrList);
      }
      else
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
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
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       ERR_ADD_MISSING_RDN_ATTRIBUTE.get(
                                            String.valueOf(entryDN), n));
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

      for(ObjectClass superiorClass : objectClass.getSuperiorClasses())
      {
        if (!objectClasses.containsKey(superiorClass))
        {
          addObjectClassChain(superiorClass);
        }
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
    // Construct any virtual/collective attributes which might
    // contain a value for the OP_ATTR_PWPOLICY_POLICY_DN attribute.
    Entry copy = entry.duplicate(true);
    AuthenticationPolicy policy = AuthenticationPolicy.forUser(copy, false);
    if (!policy.isPasswordPolicy())
    {
      // The entry doesn't have a locally managed password, so no action is
      // required.
      return;
    }
    PasswordPolicy passwordPolicy = (PasswordPolicy) policy;

    // See if a password was specified.
    AttributeType passwordAttribute = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = entry.getAttribute(passwordAttribute);
    if (attrList == null || attrList.isEmpty())
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
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    Attribute passwordAttr = attrList.get(0);
    if (passwordAttr.hasOptions())
    {
      Message message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    if (passwordAttr.isEmpty())
    {
      // This will be treated the same as not having a password.
      return;
    }

    if (!isInternalOperation()
        && !passwordPolicy.isAllowMultiplePasswordValues()
        && passwordAttr.size() > 1)
    {
      // FIXME -- What if they're pre-encoded and might all be the
      // same?
      addPWPolicyControl(PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED);

      Message message = ERR_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED
          .get(passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    List<PasswordStorageScheme<?>> defaultStorageSchemes =
         passwordPolicy.getDefaultPasswordStorageSchemes();
    AttributeBuilder builder = new AttributeBuilder(passwordAttr, true);
    builder.setInitialCapacity(defaultStorageSchemes.size());
    for (AttributeValue v : passwordAttr)
    {
      ByteString value = v.getValue();

      // See if the password is pre-encoded.
      if (passwordPolicy.isAuthPasswordSyntax())
      {
        if (AuthPasswordSyntax.isEncoded(value))
        {
          if (isInternalOperation()
              || passwordPolicy.isAllowPreEncodedPasswords())
          {
            builder.add(v);
            continue;
          }
          else
          {
            addPWPolicyControl(PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(passwordAttribute.getNameOrOID()));
          }
        }
      }
      else if (UserPasswordSyntax.isEncoded(value))
      {
        if (isInternalOperation()
            || passwordPolicy.isAllowPreEncodedPasswords())
        {
          builder.add(v);
          continue;
        }
        else
        {
          addPWPolicyControl(PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(passwordAttribute.getNameOrOID()));
        }
      }


      // See if the password passes validation.  We should only do this if
      // validation should be performed for administrators.
      if (! passwordPolicy.isSkipValidationForAdministrators())
      {
        // There are never any current passwords for an add operation.
        HashSet<ByteString> currentPasswords = new HashSet<ByteString>(0);
        MessageBuilder invalidReason = new MessageBuilder();
        // Work on a copy of the entry without the password to avoid
        // false positives from some validators.
        copy.removeAttribute(passwordAttribute);
        for (PasswordValidator<?> validator :
          passwordPolicy.getPasswordValidators())
        {
          if (! validator.passwordIsAcceptable(value, currentPasswords, this,
                                               copy, invalidReason))
          {
            addPWPolicyControl(
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

            Message message = ERR_PWPOLICY_VALIDATION_FAILED.
                get(passwordAttribute.getNameOrOID(),
                    String.valueOf(invalidReason));
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }
        }
      }


      // Encode the password.
      if (passwordPolicy.isAuthPasswordSyntax())
      {
        for (PasswordStorageScheme<?> s : defaultStorageSchemes)
        {
          ByteString encodedValue = s.encodeAuthPassword(value);
          builder.add(AttributeValues.create(
              passwordAttribute, encodedValue));
        }
      }
      else
      {
        for (PasswordStorageScheme<?> s : defaultStorageSchemes)
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
    if (passwordPolicy.isForceChangeOnAdd())
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
      if (OID_PASSWORD_POLICY_CONTROL.equals(c.getOID()))
      {
        addResponseControl(new PasswordPolicyResponseControl(null, 0, errorType));
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
  private void checkSchema(Entry parentEntry) throws DirectoryException
  {
    MessageBuilder invalidReason = new MessageBuilder();
    if (! entry.conformsToSchema(parentEntry, true, true, true, invalidReason))
    {
      throw new DirectoryException(ResultCode.OBJECTCLASS_VIOLATION,
                                   invalidReason.toMessage());
    }

    invalidReason = new MessageBuilder();
    checkAttributes(invalidReason, userAttributes);
    checkAttributes(invalidReason, operationalAttributes);


    // See if the entry contains any attributes or object classes marked
    // OBSOLETE.  If so, then reject the entry.
    for (AttributeType at : userAttributes.keySet())
    {
      if (at.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
                                     WARN_ADD_ATTR_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          at.getNameOrOID()));
      }
    }

    for (AttributeType at : operationalAttributes.keySet())
    {
      if (at.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
                                     WARN_ADD_ATTR_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          at.getNameOrOID()));
      }
    }

    for (ObjectClass oc : objectClasses.keySet())
    {
      if (oc.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
                                     WARN_ADD_OC_IS_OBSOLETE.get(
                                          String.valueOf(entryDN),
                                          oc.getNameOrOID()));
      }
    }
  }


  private void checkAttributes(MessageBuilder invalidReason,
      Map<AttributeType, List<Attribute>> attributes) throws DirectoryException
  {
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        AttributeSyntax<?> syntax = a.getAttributeType().getSyntax();
        if (syntax != null)
        {
          for (AttributeValue v : a)
          {
            if (!syntax.valueIsAcceptable(v.getValue(), invalidReason))
            {
              Message message;
              if (!syntax.isHumanReadable() || syntax.isBinary())
              {
                // Value is not human-readable
                message = WARN_ADD_OP_INVALID_SYNTAX_NO_VALUE.
                    get(String.valueOf(entryDN),
                        String.valueOf(a.getName()),
                        String.valueOf(invalidReason));
              }
              else
              {
                message = WARN_ADD_OP_INVALID_SYNTAX.
                    get(String.valueOf(entryDN),
                        String.valueOf(v.getValue().toString()),
                        String.valueOf(a.getName()),
                        String.valueOf(invalidReason));

              }

              switch (DirectoryServer.getSyntaxEnforcementPolicy())
              {
              case REJECT:
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
              case WARN:
                logError(message);
              }
            }
          }
        }
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
  private void processControls(DN parentDN) throws DirectoryException
  {
    LocalBackendWorkflowElement.removeAllDisallowedControls(parentDN, this);

    List<Control> requestControls = getRequestControls();
    if (requestControls != null && !requestControls.isEmpty())
    {
      for (Control c : requestControls)
      {
        String  oid = c.getOID();

        if (OID_LDAP_ASSERTION.equals(oid))
        {
          // RFC 4528 mandates support for Add operation basically
          // suggesting an assertion on self. As daft as it may be
          // we gonna have to support this for RFC compliance.
          LDAPAssertionRequestControl assertControl =
            getRequestControl(LDAPAssertionRequestControl.DECODER);

          SearchFilter filter;
          try
          {
            filter = assertControl.getSearchFilter();
          }
          catch (DirectoryException de)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, de);
            }

            throw newDirectoryException(entryDN, de.getResultCode(),
                ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(
                    String.valueOf(entryDN),
                    de.getMessageObject()));
          }

          // Check if the current user has permission to make
          // this determination.
          if (!AccessControlConfigManager.getInstance().
              getAccessControlHandler().isAllowed(this, entry, filter))
          {
            throw new DirectoryException(
                ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
          }

          try
          {
            if (!filter.matchesEntry(entry))
            {
              throw newDirectoryException(entryDN, ResultCode.ASSERTION_FAILED,
                  ERR_ADD_ASSERTION_FAILED.get(String
                      .valueOf(entryDN)));
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

            throw newDirectoryException(entryDN, de.getResultCode(),
                ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(
                    String.valueOf(entryDN),
                    de.getMessageObject()));
          }
        }
        else if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
        {
          noOp = true;
        }
        else if (OID_LDAP_READENTRY_POSTREAD.equals(oid))
        {
          postReadRequest =
                getRequestControl(LDAPPostReadRequestControl.DECODER);
        }
        else if (OID_PROXIED_AUTH_V1.equals(oid))
        {
          // Log usage of legacy proxy authz V1 control.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteProxiedAuthzV1Control"));

          // The requester must have the PROXIED_AUTH privilege in order to
          // be able to use this control.
          if (!getClientConnection().hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV1Control proxyControl =
              getRequestControl(ProxiedAuthV1Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          setProxiedAuthorizationDN(getDN(authorizationEntry));
        }
        else if (OID_PROXIED_AUTH_V2.equals(oid))
        {
          // The requester must have the PROXIED_AUTH privilege in order to
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
          setProxiedAuthorizationDN(getDN(authorizationEntry));
        }
        else if (OID_PASSWORD_POLICY_CONTROL.equals(oid))
        {
          // We don't need to do anything here because it's already handled
          // in LocalBackendAddOperation.handlePasswordPolicy().
        }
        // NYI -- Add support for additional controls.
        else if (c.isCritical()
            && (backend == null || !backend.supportsControl(oid)))
        {
          throw newDirectoryException(entryDN,
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_ADD_UNSUPPORTED_CRITICAL_CONTROL.get(String.valueOf(entryDN), oid));
        }
      }
    }
  }

  private DN getDN(Entry e)
  {
    return e != null ? e.getDN() : DN.nullDN();
  }
}
