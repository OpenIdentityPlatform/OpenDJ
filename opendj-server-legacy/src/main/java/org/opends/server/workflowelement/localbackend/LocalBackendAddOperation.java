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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2024-2025 3A Systems,LLC.
 */
package org.opends.server.workflowelement.localbackend;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.types.AbstractOperation.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.RelaxRulesControl;
import org.forgerock.opendj.ldap.controls.TransactionSpecificationRequestControl;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationWrapper;
import org.opends.server.core.BackendConfigManager;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicy;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.ServerContext;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LockManager.DNLock;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.*;
import org.opends.server.util.TimeThread;

/**
 * This class defines an operation used to add an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendAddOperation
       extends AddOperationWrapper
       implements PreOperationAddOperation, PostOperationAddOperation,
                  PostResponseAddOperation, PostSynchronizationAddOperation,RollbackOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the entry is to be added. */
  private LocalBackend<?> backend;

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
  /** Indicates whether the request included the RelaxRules request control. */
  private boolean RelaxRulesControlRequested=false;
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

  @Override
  public boolean isSynchronizationOperation() {
    return super.isSynchronizationOperation()||RelaxRulesControlRequested;
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
   * @param backend
   *          The backend on which operation is performed.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalAdd(final LocalBackend<?> backend)
      throws CanceledOperationException
  {
    this.backend = backend;
    ClientConnection clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processAdd(clientConnection, executePostOpPlugins);

      // Invoke the post-operation or post-synchronization add plugins.
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          getPluginConfigManager().invokePostSynchronizationAddPlugins(this);
        }
      }
      else if (executePostOpPlugins.get())
      {
        // FIXME -- Should this also be done while holding the locks?
        if (!processOperationResult(this, getPluginConfigManager().invokePostOperationAddPlugins(this)))
        {
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
          for (PersistentSearch psearch : backend.getPersistentSearches())
          {
            psearch.processAdd(entry);
          }
        }
      });
    }
  }

  private void processAdd(ClientConnection clientConnection,
      AtomicBoolean executePostOpPlugins) throws CanceledOperationException
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

    // Grab a write lock on the target entry. We'll need to do this
    // eventually anyway, and we want to make sure that the two locks are
    // always released when exiting this method, no matter what. Since
    // the entry shouldn't exist yet, locking earlier than necessary
    // shouldn't cause a problem.
    final DNLock entryLock = DirectoryServer.getLockManager().tryWriteLockEntry(entryDN);
    try
    {
      if (entryLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_ADD_CANNOT_LOCK_ENTRY.get(entryDN));
        return;
      }

      ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
      BackendConfigManager backendConfigManager =
          serverContext.getBackendConfigManager();
      DN parentDN = backendConfigManager.getParentDNInSuffix(entryDN);
      if (parentDN == null && !backendConfigManager.containsLocalNamingContext(entryDN))
      {
        if (entryDN.isRootDN())
        {
          // This is not fine.  The root DSE cannot be added.
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_ADD_CANNOT_ADD_ROOT_DSE.get());
        }
        else
        {
          // The entry doesn't have a parent but isn't a suffix. This is not allowed.
          throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_ADD_ENTRY_NOT_SUFFIX.get(entryDN));
        }
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Invoke any conflict resolution processing that might be needed by the
      // synchronization provider.
      for (SynchronizationProvider<?> provider : getSynchronizationProviders())
      {
        try
        {
          if (!processOperationResult(this, provider.handleConflictResolution(this)))
          {
            return;
          }
        }
        catch (DirectoryException de)
        {
          logger.error(ERR_ADD_SYNCH_CONFLICT_RESOLUTION_FAILED,
              getConnectionID(), getOperationID(), getExceptionMessage(de));
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
            ERR_ADD_ENTRY_ALREADY_EXISTS.get(entryDN));
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
                ResultCode.NO_SUCH_OBJECT, ERR_ADD_NO_PARENT.get(entryDN, parentDN));
          }
          else
          {
            // no matched DN either, so let's return normal error code
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(ERR_ADD_NO_PARENT.get(entryDN, parentDN));
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
      AttributeType privType = serverContext.getSchema().getAttributeType(OP_ATTR_PRIVILEGE_NAME);
      if (entry.hasAttribute(privType)
          && !clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this))
      {
        appendErrorMessage(ERR_ADD_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
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
      if (DirectoryServer.getCoreConfigManager().isCheckSchema() && !isSynchronizationOperation())
      {
        checkSchema(parentEntry);
      }

      // Get the backend in which the add is to be performed.
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(LocalizableMessage.raw("No backend for entry " + entryDN)); // TODO: i18n
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
        if (!getAccessControlHandler().isAllowed(this) || (RelaxRulesControlRequested && !clientConnection.hasPrivilege(Privilege.BYPASS_ACL, this)))
        {
          setResultCodeAndMessageNoInfoDisclosure(entryDN,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
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
        executePostOpPlugins.set(true);
        if (!processOperationResult(this, getPluginConfigManager().invokePreOperationAddPlugins(this)))
        {
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
        for (SynchronizationProvider<?> provider : getSynchronizationProviders())
        {
          try
          {
            if (!processOperationResult(this, provider.doPreOperation(this)))
            {
              return;
            }
          }
          catch (DirectoryException de)
          {
            logger.error(ERR_ADD_SYNCH_PREOP_FAILED, getConnectionID(),
                getOperationID(), getExceptionMessage(de));
            throw de;
          }
        }

        backend.addEntry(entry, this);
        if (trx!=null) {
          trx.success(this);
        }
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
      logger.traceException(de);

      setResponseData(de);
    }
    finally
    {
      if (entryLock != null)
      {
        entryLock.unlock();
      }
      processSynchPostOperationPlugins();
    }
  }

  @Override
  public void rollback() throws CanceledOperationException, DirectoryException {
    backend.deleteEntry(entryDN,null);
  }

  private void processSynchPostOperationPlugins()
  {
    for (SynchronizationProvider<?> provider : getSynchronizationProviders())
    {
      try
      {
        provider.doPostOperation(this);
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);
        logger.error(ERR_ADD_SYNCH_POSTOP_FAILED, getConnectionID(),
            getOperationID(), getExceptionMessage(de));
        setResponseData(de);
        break;
      }
    }
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
            ERR_ADD_ATTR_IS_NO_USER_MOD.get(entryDN, at.getNameOrOID()));
        return true;
      }
    }
    return false;
  }

  private DirectoryException newDirectoryException(DN entryDN,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, null,
        entryDN, resultCode, message, ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
        ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(DN entryDN,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        null, entryDN, resultCode, message,
        ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
        ERR_ADD_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
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
    for (AVA ava : entryDN.rdn())
    {
      AttributeType t = ava.getAttributeType();
      addRDNAttributesIfNecessary(t.isOperational() ? operationalAttributes : userAttributes, ava);
    }
  }



  private void addRDNAttributesIfNecessary(Map<AttributeType, List<Attribute>> attributes, AVA ava)
      throws DirectoryException
  {
    AttributeType  t = ava.getAttributeType();
    String         n = ava.getAttributeName();
    ByteString     v = ava.getAttributeValue();
    final List<Attribute> attrList = attributes.get(t);
    if (attrList == null)
    {
      if (!isSynchronizationOperation()
          && !DirectoryServer.getCoreConfigManager().isAddMissingRDNAttributes())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
            ERR_ADD_MISSING_RDN_ATTRIBUTE.get(entryDN, n));
      }
      attributes.put(t, newArrayList(Attributes.create(t, n, v)));
      return;
    }

    for (int j = 0; j < attrList.size(); j++) {
      Attribute a = attrList.get(j);
      if (a.getAttributeDescription().hasOptions())
      {
        continue;
      }

      if (!a.contains(v))
      {
        AttributeBuilder builder = new AttributeBuilder(a);
        builder.add(v);
        attrList.set(j, builder.toAttribute());
      }

      return;
    }

    // not found
    if (!isSynchronizationOperation() && !DirectoryServer.getCoreConfigManager().isAddMissingRDNAttributes())
    {
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_ADD_MISSING_RDN_ATTRIBUTE.get(entryDN, n));
    }
    attrList.add(Attributes.create(t, n, v));
  }

  /**
   * Performs all password policy processing necessary for the provided add
   * operation.
   *
   * @throws  DirectoryException  If a problem occurs while performing password
   *                              policy processing for the add operation.
   */
  private final void handlePasswordPolicy()
         throws DirectoryException
  {
    // Construct any virtual/collective attributes which might
    // contain a value for the OP_ATTR_PWPOLICY_POLICY_DN attribute.
    Entry copy = entry.duplicate(true);
    AuthenticationPolicy policy = AuthenticationPolicy.forUser(copy, false);
    if (!policy.isPasswordPolicy())
    {
      // The entry doesn't have a locally managed password, so no action is required.
      return;
    }
    PasswordPolicy passwordPolicy = (PasswordPolicy) policy;

    // See if a password was specified.
    AttributeType passwordAttribute = passwordPolicy.getPasswordAttribute();
    List<Attribute> attrList = entry.getAllAttributes(passwordAttribute);
    if (attrList.isEmpty())
    {
      // The entry doesn't have a password, so no action is required.
      return;
    }
    else if (attrList.size() > 1)
    {
      // This must mean there are attribute options, which we won't allow for
      // passwords.
      LocalizableMessage message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
          passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    Attribute passwordAttr = attrList.get(0);
    if (passwordAttr.getAttributeDescription().hasOptions())
    {
      LocalizableMessage message = ERR_PWPOLICY_ATTRIBUTE_OPTIONS_NOT_ALLOWED.get(
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
      // FIXME -- What if they're pre-encoded and might all be the same?
      addPWPolicyControl(PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED);

      LocalizableMessage message = ERR_PWPOLICY_MULTIPLE_PW_VALUES_NOT_ALLOWED
          .get(passwordAttribute.getNameOrOID());
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }

    List<PasswordStorageScheme<?>> defaultStorageSchemes =
         passwordPolicy.getDefaultPasswordStorageSchemes();
    AttributeBuilder builder = new AttributeBuilder(passwordAttr.getAttributeDescription());
    for (ByteString value : passwordAttr)
    {
      // See if the password is pre-encoded.
      boolean isPreEncoded = passwordPolicy.isAuthPasswordSyntax()
          ? AuthPasswordSyntax.isEncoded(value)
          : UserPasswordSyntax.isEncoded(value);
      if (isPreEncoded)
      {
        if (isInternalOperation() || passwordPolicy.isAllowPreEncodedPasswords())
        {
          builder.add(value);
          continue;
        }
        else
        {
          addPWPolicyControl(PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY);

          LocalizableMessage msg = ERR_PWPOLICY_PREENCODED_NOT_ALLOWED.get(passwordAttribute.getNameOrOID());
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, msg);
        }
      }


      // See if the password passes validation.  We should only do this if
      // validation should be performed for administrators.
      if (! passwordPolicy.isSkipValidationForAdministrators())
      {
        // There are never any current passwords for an add operation.
        HashSet<ByteString> currentPasswords = new HashSet<>(0);
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
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

            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_PWPOLICY_VALIDATION_FAILED.get(passwordAttribute.getNameOrOID(), invalidReason));
          }
        }
      }


      // Encode the password.
      if (passwordPolicy.isAuthPasswordSyntax())
      {
        for (PasswordStorageScheme<?> s : defaultStorageSchemes)
        {
          builder.add(s.encodeAuthPassword(value));
        }
      }
      else
      {
        for (PasswordStorageScheme<?> s : defaultStorageSchemes)
        {
          builder.add(s.encodePasswordWithScheme(value));
        }
      }
    }


    // Put the new encoded values in the entry.
    entry.replaceAttribute(builder.toAttribute());


    // Set the password changed time attribute.
    Attribute changedTime = Attributes.create(
        OP_ATTR_PWPOLICY_CHANGED_TIME, TimeThread.getGeneralizedTime());
    entry.putAttribute(changedTime.getAttributeDescription().getAttributeType(), newArrayList(changedTime));


    // If we should force change on add, then set the appropriate flag.
    if (passwordPolicy.isForceChangeOnAdd())
    {
      addPWPolicyControl(PasswordPolicyErrorType.CHANGE_AFTER_RESET);

      Attribute reset = Attributes.create(OP_ATTR_PWPOLICY_RESET_REQUIRED, "TRUE");
      entry.putAttribute(reset.getAttributeDescription().getAttributeType(), newArrayList(reset));
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
    LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
    if (! entry.conformsToSchema(parentEntry, true, true, true, invalidReason))
    {
      throw new DirectoryException(entry.getTypeConformsToSchemaError(),
                                   invalidReason.toMessage());
    }

    invalidReason = new LocalizableMessageBuilder();
    checkAttributesConformToSyntax(invalidReason, userAttributes);
    checkAttributesConformToSyntax(invalidReason, operationalAttributes);


    // See if the entry contains any attributes or object classes marked
    // OBSOLETE.  If so, then reject the entry.
    for (AttributeType at : userAttributes.keySet())
    {
      if (at.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
            WARN_ADD_ATTR_IS_OBSOLETE.get(entryDN, at.getNameOrOID()));
      }
    }

    for (AttributeType at : operationalAttributes.keySet())
    {
      if (at.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
            WARN_ADD_ATTR_IS_OBSOLETE.get(entryDN, at.getNameOrOID()));
      }
    }

    for (ObjectClass oc : objectClasses.keySet())
    {
      if (oc.isObsolete())
      {
        throw newDirectoryException(entryDN, ResultCode.CONSTRAINT_VIOLATION,
            WARN_ADD_OC_IS_OBSOLETE.get(entryDN, oc.getNameOrOID()));
      }
    }
  }


  private void checkAttributesConformToSyntax(LocalizableMessageBuilder invalidReason,
      Map<AttributeType, List<Attribute>> attributes) throws DirectoryException
  {
    for (List<Attribute> attrList : attributes.values())
    {
      for (Attribute a : attrList)
      {
        Syntax syntax = a.getAttributeDescription().getAttributeType().getSyntax();
        if (syntax != null)
        {
          for (ByteString v : a)
          {
            if (!syntax.valueIsAcceptable(v, invalidReason))
            {
              LocalizableMessage message;
              if (!syntax.isHumanReadable() || syntax.isBEREncodingRequired())
              {
                // Value is not human-readable
                message = WARN_ADD_OP_INVALID_SYNTAX_NO_VALUE.get(entryDN, a.getAttributeDescription(), invalidReason);
              }
              else
              {
                message = WARN_ADD_OP_INVALID_SYNTAX.get(entryDN, v, a.getAttributeDescription(), invalidReason);
              }

              switch (DirectoryServer.getCoreConfigManager().getSyntaxEnforcementPolicy())
              {
              case REJECT:
                throw new DirectoryException(
                    ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
              case WARN:
                logger.error(message);
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
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(parentDN, this);

    for (Control c : getRequestControls())
    {
      final String oid = c.getOID();

      if (OID_LDAP_ASSERTION.equals(oid))
      {
        // RFC 4528 mandates support for Add operation basically
        // suggesting an assertion on self. As daft as it may be
        // we gonna have to support this for RFC compliance.
        LDAPAssertionRequestControl assertControl = getRequestControl(LDAPAssertionRequestControl.DECODER);

        SearchFilter filter;
        try
        {
          filter = assertControl.getSearchFilter();
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          throw newDirectoryException(entryDN, de.getResultCode(),
              ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }

        // Check if the current user has permission to make this determination.
        if (!getAccessControlHandler().isAllowed(this, entry, filter))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

        try
        {
          if (!filter.matchesEntry(entry))
          {
            throw newDirectoryException(entryDN, ResultCode.ASSERTION_FAILED, ERR_ADD_ASSERTION_FAILED.get(entryDN));
          }
        }
        catch (DirectoryException de)
        {
          if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
          {
            throw de;
          }

          logger.traceException(de);

          throw newDirectoryException(entryDN, de.getResultCode(),
              ERR_ADD_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }
      }
      else if (OID_LDAP_NOOP_OPENLDAP_ASSIGNED.equals(oid))
      {
        noOp = true;
      }
      else if (OID_LDAP_READENTRY_POSTREAD.equals(oid))
      {
        postReadRequest = getRequestControl(LDAPPostReadRequestControl.DECODER);
      }
      else if (LocalBackendWorkflowElement.isProxyAuthzControl(oid))
      {
        continue;
      }
      else if (OID_PASSWORD_POLICY_CONTROL.equals(oid))
      {
        // We don't need to do anything here because it's already handled
        // in LocalBackendAddOperation.handlePasswordPolicy().
      }
      else if (RelaxRulesControl.OID.equals(oid))
      {
        RelaxRulesControlRequested = true;
      }
      else if (TransactionSpecificationRequestControl.OID.equals(oid))
      {
        trx=getClientConnection().getTransaction(((LDAPControl)c).getValue().toString());
      }
      else if (c.isCritical() && !backend.supportsControl(oid))
      {
        throw newDirectoryException(entryDN, ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            ERR_ADD_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
      }
    }
  }
  ClientConnection.Transaction trx=null;

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

}
