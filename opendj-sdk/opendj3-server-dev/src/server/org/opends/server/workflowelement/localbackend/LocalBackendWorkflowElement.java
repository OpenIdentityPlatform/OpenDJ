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
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.Backend;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.workflowelement.ObservableWorkflowElementState;

import static org.opends.messages.CoreMessages.*;

/**
 * This class defines a workflow element, i.e. a task in a workflow.
 *
 * [outdated]
 * A workflow element can wrap a physical
 * repository such as a local backend, a remote LDAP server or a local LDIF
 * file. A workflow element can also be used to route operations.
 * This is the case for load balancing and distribution.
 * And workflow element can be used in a virtual environment to transform data
 * (DN and attribute renaming, attribute value renaming...).
 * [/outdated]
 *
 * This class defines a local backend workflow element; e-g an entity that
 * handle the processing of an operation against a local backend.
 */
public class LocalBackendWorkflowElement implements Observer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * An information indicating the type of the current workflow element. This
   * information is for debug and tooling purpose only.
   */
  private String workflowElementTypeInfo = "not defined";

  /** The workflow element identifier. */
  private String workflowElementID;

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


  /** The observable state of the workflow element. */
  private ObservableWorkflowElementState observableState = new ObservableWorkflowElementState(this);

  /**
   * The list of observers who want to be notified when a workflow element
   * required by the observer is created. The key of the map is a string that
   * identifies the newly created workflow element.
   */
  private static ConcurrentMap<String, List<Observer>> newWorkflowElementNotificationList =
      new ConcurrentHashMap<String, List<Observer>>();

  /**
   * Registers with a specific workflow element to be notified when the workflow
   * element state has changed. This notification system is mainly used to be
   * warned when a workflow element is enabled or disabled.
   * <p>
   * If the workflow element <code>we</code> is not <code>null</code> then the
   * <code>observer</code> is registered with the list of objects to notify when
   * <code>we</code> has changed.
   * <p>
   * If the workflow element <code>we</code> is <code>null</code> then the
   * <code>observer</code> is registered with a static list of objects to notify
   * when a workflow element named <code>weid</code> is created.
   *
   * @param we
   *          the workflow element. If <code>null</code> then observer is
   *          registered with a list of workflow element identifiers.
   * @param weid
   *          the identifier of the workflow element. This parameter is useless
   *          when <code>we</code> is not <code>null</code>
   * @param observer
   *          the observer to notify when the workflow element state has been
   *          modified
   */
  // TODO JNR rename
  public static void registerForStateUpdate(LocalBackendWorkflowElement we, String weid, Observer observer)
  {
    // If the workflow element "we" exists then register the observer with "we"
    // else register the observer with a static list of workflow element
    // identifiers
    if (we != null)
    {
      we.observableState.addObserver(observer);
    }
    else
    {
      if (weid == null)
      {
        return;
      }

      List<Observer> observers = newWorkflowElementNotificationList.get(weid);
      if (observers == null)
      {
        // create the list of observers
        observers = new CopyOnWriteArrayList<Observer>();
        observers.add(observer);
        newWorkflowElementNotificationList.put(weid, observers);
      }
      else
      {
        // update the observer list
        observers.add(observer);
      }
    }
  }

  /**
   * Deregisters an observer that was registered with a specific workflow
   * element.
   * <p>
   * If the workflow element <code>we</code> is not <code>null</code> then the
   * <code>observer</code> is deregistered with the list of objects to notify
   * when <code>we</code> has changed.
   * <p>
   * If the workflow element <code>we</code> is <code>null</code> then the
   * <code>observer</code> is deregistered with a static list of objects to
   * notify when a workflow element named <code>weid</code> is created.
   *
   * @param we
   *          the workflow element. If <code>null</code> then observer is
   *          deregistered with a list of workflow element identifiers.
   * @param weid
   *          the identifier of the workflow element. This parameter is useless
   *          when <code>we</code> is not <code>null</code>
   * @param observer
   *          the observer to deregister
   */
  public static void deregisterForStateUpdate(LocalBackendWorkflowElement we, String weid, Observer observer)
  {
    // If the workflow element "we" exists then deregister the observer
    // with "we" else deregister the observer with a static list of
    // workflow element identifiers
    if (we != null)
    {
      we.observableState.deleteObserver(observer);
    }

    if (weid != null)
    {
      List<Observer> observers = newWorkflowElementNotificationList.get(weid);
      if (observers != null)
      {
        observers.remove(observer);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void update(Observable o, Object arg)
  {
    // By default, do nothing when notification hits the workflow element.
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
    this.workflowElementID = workflowElementID;
    this.workflowElementTypeInfo = BACKEND_WORKFLOW_ELEMENT;
    this.backend  = backend;
  }

  /**
   * Indicates whether the workflow element encapsulates a private local
   * backend.
   *
   * @return <code>true</code> if the workflow element encapsulates a private
   *         local backend, <code>false</code> otherwise
   */
  public boolean isPrivate()
  {
    return this.backend != null && this.backend.isPrivateBackend();
  }

  /**
   * Performs any finalization that might be required when this workflow element
   * is unloaded. No action is taken in the default implementation.
   */
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise a NPE
    this.workflowElementID = null;
    this.workflowElementTypeInfo = null;
    this.backend = null;
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

  /**
   * Executes the workflow element for an operation.
   *
   * @param operation
   *          the operation to execute
   * @throws CanceledOperationException
   *           if this operation should be canceled
   */
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
  static <O extends Operation, L> void attachLocalOperation(O globalOperation, L currentLocalOperation)
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
   * Provides the workflow element identifier.
   *
   * @return the workflow element identifier
   */
  public String getWorkflowElementID()
  {
    return workflowElementID;
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
        + " workflowElementID=" + this.workflowElementID
        + " workflowElementTypeInfo=" + this.workflowElementTypeInfo;
  }
}
