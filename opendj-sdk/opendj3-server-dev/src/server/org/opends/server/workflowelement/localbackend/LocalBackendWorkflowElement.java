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
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LocalBackendWorkflowElementCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.core.*;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.workflowelement.LeafWorkflowElement;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;

/**
 * This class defines a local backend workflow element; e-g an entity that
 * handle the processing of an operation against a local backend.
 */
public class LocalBackendWorkflowElement extends
    LeafWorkflowElement<LocalBackendWorkflowElementCfg>
    implements ConfigurationChangeListener<LocalBackendWorkflowElementCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** the backend associated with the local workflow element. */
  private Backend<?> backend;


  /** the set of local backend workflow elements registered with the server. */
  private static TreeMap<String, LocalBackendWorkflowElement>
       registeredLocalBackends =
            new TreeMap<String, LocalBackendWorkflowElement>();

  /**
   * A lock to guarantee safe concurrent access to the registeredLocalBackends
   * variable.
   */
  private static final Object registeredLocalBackendsLock = new Object();


  /** A string indicating the type of the workflow element. */
  private static final String BACKEND_WORKFLOW_ELEMENT = "Backend";


  /**
   * Creates a new instance of the local backend workflow element.
   */
  public LocalBackendWorkflowElement()
  {
    // There is nothing to do in this constructor.
  }


  /**
   * Initializes a new instance of the local backend workflow element.
   * This method is intended to be called by DirectoryServer when
   * workflow configuration mode is auto as opposed to
   * initializeWorkflowElement which is invoked when workflow
   * configuration mode is manual.
   *
   * @param workflowElementID  the workflow element identifier
   * @param backend  the backend associated to that workflow element
   */
  private void initialize(String workflowElementID, Backend<?> backend)
  {
    super.initialize(workflowElementID, BACKEND_WORKFLOW_ELEMENT);

    this.backend  = backend;

    if (this.backend != null)
    {
      setPrivate(this.backend.isPrivateBackend());
    }
  }


  /**
   * Initializes a new instance of the local backend workflow element.
   * This method is intended to be called by DirectoryServer when
   * workflow configuration mode is manual as opposed to
   * initialize(String,Backend) which is invoked when workflow
   * configuration mode is auto.
   *
   * @param  configuration  The configuration for this local backend
   *                        workflow element.
   *
   * @throws  ConfigException  If there is a problem with the provided
   *                           configuration.
   *
   * @throws  InitializationException  If an error occurs while trying
   *                                   to initialize this workflow
   *                                   element that is not related to
   *                                   the provided configuration.
   */
  public void initializeWorkflowElement(
      LocalBackendWorkflowElementCfg configuration
      ) throws ConfigException, InitializationException
  {
    configuration.addLocalBackendChangeListener(this);

    // Read configuration and apply changes.
    processWorkflowElementConfig(configuration, true);
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise a NPE
    super.initialize(null, null);
    backend = null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LocalBackendWorkflowElementCfg configuration,
      List<LocalizableMessage>                  unacceptableReasons
      )
  {
    return processWorkflowElementConfig(configuration, false);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      LocalBackendWorkflowElementCfg configuration
      )
  {
    processWorkflowElementConfig(configuration, true);

    return new ConfigChangeResult(ResultCode.SUCCESS, false,
        new ArrayList<LocalizableMessage>());
  }


  /**
   * Parses the provided configuration and configure the workflow element.
   *
   * @param configuration  The new configuration containing the changes.
   * @param applyChanges   If true then take into account the new configuration.
   *
   * @return  <code>true</code> if the configuration is acceptable.
   */
  private boolean processWorkflowElementConfig(
      LocalBackendWorkflowElementCfg configuration,
      boolean                        applyChanges
      )
  {
    // returned status
    boolean isAcceptable = true;

    // If the workflow element is disabled then do nothing. Note that the
    // configuration manager could have finalized the object right before.
    if (configuration.isEnabled())
    {
      // Read configuration.
      String newBackendID = configuration.getBackend();
      Backend<?> newBackend = DirectoryServer.getBackend(newBackendID);

      // If the backend is null (i.e. not found in the list of
      // registered backends, this is probably because we are looking
      // for the config backend
      if (newBackend == null) {
        ServerManagementContext context = ServerManagementContext.getInstance();
        RootCfg root = context.getRootConfiguration();
        try {
          BackendCfg backendCfg = root.getBackend(newBackendID);
          if (backendCfg.getBaseDN().contains(DN.valueOf(DN_CONFIG_ROOT))) {
            newBackend = DirectoryServer.getConfigHandler();
          }
        } catch (Exception ex) {
          // Unable to find the backend
          newBackend = null;
        }
      }

      // Get the new configuration
      if (applyChanges)
      {
        super.initialize(
          configuration.dn().rdn().getAttributeValue(0).toString(),
          BACKEND_WORKFLOW_ELEMENT);
        backend = newBackend;
        if (backend != null)
        {
          setPrivate(backend.isPrivateBackend());
        }
      }
    }

    return isAcceptable;
  }


  /**
   * Creates and registers a local backend with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to create
   * @param backend            the backend to associate with the local backend
   *                           workflow element
   *
   * @return the existing local backend workflow element if it was
   *         already created or a newly created local backend workflow
   *         element.
   */
  public static LocalBackendWorkflowElement createAndRegister(
      String workflowElementID, Backend<?> backend)
  {
    // If the requested workflow element does not exist then create one.
    LocalBackendWorkflowElement localBackend =
        registeredLocalBackends.get(workflowElementID);
    if (localBackend == null)
    {
      localBackend = new LocalBackendWorkflowElement();
      localBackend.initialize(workflowElementID, backend);

      // store the new local backend in the list of registered backends
      registerLocalBackend(localBackend);
    }

    return localBackend;
  }



  /**
   * Removes a local backend that was registered with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to remove
   */
  public static void remove(String workflowElementID)
  {
    deregisterLocalBackend(workflowElementID);
  }



  /**
   * Removes all the local backends that were registered with the server.
   * This function is intended to be called when the server is shutting down.
   */
  public static void removeAll()
  {
    synchronized (registeredLocalBackendsLock)
    {
      for (LocalBackendWorkflowElement localBackend:
           registeredLocalBackends.values())
      {
        deregisterLocalBackend(localBackend.getWorkflowElementID());
      }
    }
  }



  /**
   * Removes all the disallowed request controls from the provided operation.
   * <p>
   * As per RFC 4511 4.1.11, if a disallowed request control is critical, then a
   * DirectoryException is thrown with unavailableCriticalExtension. Otherwise,
   * if the disallowed request control is non critical, it is removed because we
   * do not want the backend to process it.
   *
   * @param targetDN
   *          the target DN on which the operation applies
   * @param op
   *          the operation currently processed
   * @throws DirectoryException
   *           If a disallowed request control is critical, thrown with
   *           unavailableCriticalExtension. If an error occurred while
   *           performing the access control check. For example, if an attribute
   *           could not be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  static void removeAllDisallowedControls(DN targetDN, Operation op)
      throws DirectoryException
  {
    final List<Control> requestControls = op.getRequestControls();
    if (requestControls != null && !requestControls.isEmpty())
    {
      for (Iterator<Control> iter = requestControls.iterator(); iter.hasNext();)
      {
        final Control control = iter.next();

        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(targetDN, op, control))
        {
          // As per RFC 4511 4.1.11.
          if (control.isCritical())
          {
            throw new DirectoryException(
                ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(control.getOID()));
          }

          // We do not want the backend to process this non-critical control, so
          // remove it.
          iter.remove();
        }
      }
    }
  }

  /**
   * Returns a new {@link DirectoryException} built from the provided
   * resultCodes and messages. Depending on whether ACIs prevent information
   * disclosure, the provided resultCode and message will be masked and
   * altResultCode and altMessage will be used instead.
   *
   * @param operation
   *          the operation for which to check if ACIs prevent information
   *          disclosure
   * @param entry
   *          the entry for which to check if ACIs prevent information
   *          disclosure, if null, then a fake entry will be created from the
   *          entryDN parameter
   * @param entryDN
   *          the entry dn for which to check if ACIs prevent information
   *          disclosure. Only used if entry is null.
   * @param resultCode
   *          the result code to put on the DirectoryException if ACIs allow
   *          disclosure. Otherwise it will be put on the DirectoryException as
   *          a masked result code.
   * @param message
   *          the message to put on the DirectoryException if ACIs allow
   *          disclosure. Otherwise it will be put on the DirectoryException as
   *          a masked message.
   * @param altResultCode
   *          the result code to put on the DirectoryException if ACIs do not
   *          allow disclosing the resultCode.
   * @param altMessage
   *          the result code to put on the DirectoryException if ACIs do not
   *          allow disclosing the message.
   * @return a new DirectoryException containing the provided resultCodes and
   *         messages depending on ACI allowing disclosure or not
   * @throws DirectoryException
   *           If an error occurred while performing the access control check.
   */
  static DirectoryException newDirectoryException(Operation operation,
      Entry entry, DN entryDN, ResultCode resultCode, LocalizableMessage message,
      ResultCode altResultCode, LocalizableMessage altMessage) throws DirectoryException
  {
    if (AccessControlConfigManager.getInstance().getAccessControlHandler()
        .canDiscloseInformation(entry, entryDN, operation))
    {
      return new DirectoryException(resultCode, message);
    }
    // replacement reason returned to the user
    final DirectoryException ex =
        new DirectoryException(altResultCode, altMessage);
    // real underlying reason
    ex.setMaskedResultCode(resultCode);
    ex.setMaskedMessage(message);
    return ex;
  }

  /**
   * Sets the provided resultCodes and messages on the provided operation.
   * Depending on whether ACIs prevent information disclosure, the provided
   * resultCode and message will be masked and altResultCode and altMessage will
   * be used instead.
   *
   * @param operation
   *          the operation for which to check if ACIs prevent information
   *          disclosure
   * @param entry
   *          the entry for which to check if ACIs prevent information
   *          disclosure, if null, then a fake entry will be created from the
   *          entryDN parameter
   * @param entryDN
   *          the entry dn for which to check if ACIs prevent information
   *          disclosure. Only used if entry is null.
   * @param resultCode
   *          the result code to put on the DirectoryException if ACIs allow
   *          disclosure. Otherwise it will be put on the DirectoryException as
   *          a masked result code.
   * @param message
   *          the message to put on the DirectoryException if ACIs allow
   *          disclosure. Otherwise it will be put on the DirectoryException as
   *          a masked message.
   * @param altResultCode
   *          the result code to put on the DirectoryException if ACIs do not
   *          allow disclosing the resultCode.
   * @param altMessage
   *          the result code to put on the DirectoryException if ACIs do not
   *          allow disclosing the message.
   * @throws DirectoryException
   *           If an error occurred while performing the access control check.
   */
  static void setResultCodeAndMessageNoInfoDisclosure(Operation operation,
      Entry entry, DN entryDN, ResultCode resultCode, LocalizableMessage message,
      ResultCode altResultCode, LocalizableMessage altMessage) throws DirectoryException
  {
    if (AccessControlConfigManager.getInstance().getAccessControlHandler()
        .canDiscloseInformation(entry, entryDN, operation))
    {
      operation.setResultCode(resultCode);
      operation.appendErrorMessage(message);
    }
    else
    {
      // replacement reason returned to the user
      operation.setResultCode(altResultCode);
      operation.appendErrorMessage(altMessage);
      // real underlying reason
      operation.setMaskedResultCode(resultCode);
      operation.appendMaskedErrorMessage(message);
    }
  }

  /**
   * Removes the matchedDN from the supplied operation if ACIs prevent its
   * disclosure.
   *
   * @param operation
   *          where to filter the matchedDN from
   */
  static void filterNonDisclosableMatchedDN(Operation operation)
  {
    if (operation.getMatchedDN() == null)
    {
      return;
    }

    try
    {
      if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
          .canDiscloseInformation(null, operation.getMatchedDN(), operation))
      {
        operation.setMatchedDN(null);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      operation.setResponseData(de);
      // At this point it is impossible to tell whether the matchedDN can be
      // disclosed. It is probably safer to hide it by default.
      operation.setMatchedDN(null);
    }
  }

  /**
   * Adds the post-read response control to the response if requested.
   *
   * @param operation
   *          The update operation.
   * @param postReadRequest
   *          The request control, if present.
   * @param entry
   *          The post-update entry.
   */
  static void addPostReadResponse(final Operation operation,
      final LDAPPostReadRequestControl postReadRequest, final Entry entry)
  {
    if (postReadRequest == null)
    {
      return;
    }

    /*
     * Virtual and collective attributes are only added to an entry when it is
     * read from the backend, not before it is written, so we need to add them
     * ourself.
     */
    final Entry fullEntry = entry.duplicate(true);

    /*
     * Even though the associated update succeeded, we should still check
     * whether or not we should return the entry.
     */
    final SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(
        fullEntry, null);
    if (AccessControlConfigManager.getInstance().getAccessControlHandler()
        .maySend(operation, unfilteredSearchEntry))
    {
      // Filter the entry based on the control's attribute list.
      final Entry filteredEntry = fullEntry.filterEntry(
          postReadRequest.getRequestedAttributes(), false, false, false);
      final SearchResultEntry filteredSearchEntry = new SearchResultEntry(
          filteredEntry, null);

      // Strip out any attributes which access control denies access to.
      AccessControlConfigManager.getInstance().getAccessControlHandler()
          .filterEntry(operation, unfilteredSearchEntry, filteredSearchEntry);

      final LDAPPostReadResponseControl responseControl =
          new LDAPPostReadResponseControl(filteredSearchEntry);
      operation.addResponseControl(responseControl);
    }
  }



  /**
   * Adds the pre-read response control to the response if requested.
   *
   * @param operation
   *          The update operation.
   * @param preReadRequest
   *          The request control, if present.
   * @param entry
   *          The pre-update entry.
   */
  static void addPreReadResponse(final Operation operation,
      final LDAPPreReadRequestControl preReadRequest, final Entry entry)
  {
    if (preReadRequest == null)
    {
      return;
    }

    /*
     * Even though the associated update succeeded, we should still check
     * whether or not we should return the entry.
     */
    final SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(
        entry, null);
    if (AccessControlConfigManager.getInstance().getAccessControlHandler()
        .maySend(operation, unfilteredSearchEntry))
    {
      // Filter the entry based on the control's attribute list.
      final Entry filteredEntry = entry.filterEntry(
          preReadRequest.getRequestedAttributes(), false, false, false);
      final SearchResultEntry filteredSearchEntry = new SearchResultEntry(
          filteredEntry, null);

      // Strip out any attributes which access control denies access to.
      AccessControlConfigManager.getInstance().getAccessControlHandler()
          .filterEntry(operation, unfilteredSearchEntry, filteredSearchEntry);

      final LDAPPreReadResponseControl responseControl =
          new LDAPPreReadResponseControl(filteredSearchEntry);
      operation.addResponseControl(responseControl);
    }
  }



  /**
   * Registers a local backend with the server.
   *
   * @param localBackend  the local backend to register with the server
   */
  private static void registerLocalBackend(
                           LocalBackendWorkflowElement localBackend)
  {
    synchronized (registeredLocalBackendsLock)
    {
      String localBackendID = localBackend.getWorkflowElementID();
      LocalBackendWorkflowElement existingLocalBackend =
        registeredLocalBackends.get(localBackendID);

      if (existingLocalBackend == null)
      {
        TreeMap<String, LocalBackendWorkflowElement> newLocalBackends =
          new TreeMap
            <String, LocalBackendWorkflowElement>(registeredLocalBackends);
        newLocalBackends.put(localBackendID, localBackend);
        registeredLocalBackends = newLocalBackends;
      }
    }
  }



  /**
   * Deregisters a local backend with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to remove
   */
  private static void deregisterLocalBackend(String workflowElementID)
  {
    synchronized (registeredLocalBackendsLock)
    {
      LocalBackendWorkflowElement existingLocalBackend =
        registeredLocalBackends.get(workflowElementID);

      if (existingLocalBackend != null)
      {
        TreeMap<String, LocalBackendWorkflowElement> newLocalBackends =
             new TreeMap<String, LocalBackendWorkflowElement>(
                      registeredLocalBackends);
        newLocalBackends.remove(workflowElementID);
        registeredLocalBackends = newLocalBackends;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void execute(Operation operation) throws CanceledOperationException {
    switch (operation.getOperationType())
    {
      case BIND:
        LocalBackendBindOperation bindOperation =
             new LocalBackendBindOperation((BindOperation) operation);
        bindOperation.processLocalBind(this);
        break;

      case SEARCH:
        LocalBackendSearchOperation searchOperation =
             new LocalBackendSearchOperation((SearchOperation) operation);
        searchOperation.processLocalSearch(this);
        break;

      case ADD:
        LocalBackendAddOperation addOperation =
             new LocalBackendAddOperation((AddOperation) operation);
        addOperation.processLocalAdd(this);
        break;

      case DELETE:
        LocalBackendDeleteOperation deleteOperation =
             new LocalBackendDeleteOperation((DeleteOperation) operation);
        deleteOperation.processLocalDelete(this);
        break;

      case MODIFY:
        LocalBackendModifyOperation modifyOperation =
             new LocalBackendModifyOperation((ModifyOperation) operation);
        modifyOperation.processLocalModify(this);
        break;

      case MODIFY_DN:
        LocalBackendModifyDNOperation modifyDNOperation =
             new LocalBackendModifyDNOperation((ModifyDNOperation) operation);
        modifyDNOperation.processLocalModifyDN(this);
        break;

      case COMPARE:
        LocalBackendCompareOperation compareOperation =
             new LocalBackendCompareOperation((CompareOperation) operation);
        compareOperation.processLocalCompare(this);
        break;

      case ABANDON:
        // There is no processing for an abandon operation.
        break;

      default:
        throw new AssertionError("Attempted to execute an invalid operation " +
                                 "type:  " + operation.getOperationType() +
                                 " (" + operation + ")");
    }
  }



  /**
   * Attaches the current local operation to the global operation so that
   * operation runner can execute local operation post response later on.
   *
   * @param <O>              subtype of Operation
   * @param <L>              subtype of LocalBackendOperation
   * @param globalOperation  the global operation to which local operation
   *                         should be attached to
   * @param currentLocalOperation  the local operation to attach to the global
   *                               operation
   */
  @SuppressWarnings("unchecked")
  public static <O extends Operation,L> void
              attachLocalOperation (O globalOperation, L currentLocalOperation)
  {
    List<?> existingAttachment =
      (List<?>) globalOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);

    List<L> newAttachment = new ArrayList<L>();

    if (existingAttachment != null)
    {
      // This line raises an unchecked conversion warning.
      // There is nothing we can do to prevent this warning
      // so let's get rid of it since we know the cast is safe.
      newAttachment.addAll ((List<L>) existingAttachment);
    }
    newAttachment.add (currentLocalOperation);
    globalOperation.setAttachment(Operation.LOCALBACKENDOPERATIONS,
                                  newAttachment);
  }



  /**
   * Gets the backend associated with this local backend workflow
   * element.
   *
   * @return The backend associated with this local backend workflow
   *         element.
   */
  public Backend<?> getBackend()
  {
    return backend;
  }

  /**
   * Checks if an update operation can be performed against a backend. The
   * operation will be rejected based on the server and backend writability
   * modes.
   *
   * @param backend
   *          The backend handling the update.
   * @param op
   *          The update operation.
   * @param entryDN
   *          The name of the entry being updated.
   * @param serverMsg
   *          The message to log if the update was rejected because the server
   *          is read-only.
   * @param backendMsg
   *          The message to log if the update was rejected because the backend
   *          is read-only.
   * @throws DirectoryException
   *           If the update operation has been rejected.
   */
  static void checkIfBackendIsWritable(Backend<?> backend, Operation op,
      DN entryDN, LocalizableMessageDescriptor.Arg1<Object> serverMsg,
      LocalizableMessageDescriptor.Arg1<Object> backendMsg)
      throws DirectoryException
  {
    if (!backend.isPrivateBackend())
    {
      switch (DirectoryServer.getWritabilityMode())
      {
      case DISABLED:
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            serverMsg.get(String.valueOf(entryDN)));

      case INTERNAL_ONLY:
        if (!(op.isInternalOperation() || op.isSynchronizationOperation()))
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              serverMsg.get(String.valueOf(entryDN)));
        }
      }

      switch (backend.getWritabilityMode())
      {
      case DISABLED:
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            backendMsg.get(String.valueOf(entryDN)));

      case INTERNAL_ONLY:
        if (!(op.isInternalOperation() || op.isSynchronizationOperation()))
        {
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
              backendMsg.get(String.valueOf(entryDN)));
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " backend=" + backend
        + " workflowElementID=" + getWorkflowElementID()
        + " workflowElementTypeInfo=" + getWorkflowElementTypeInfo();
  }
}
