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
 */
package org.opends.server.workflowelement.localbackend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.Backend;
import org.opends.server.backends.RootDSEBackend;
import org.opends.server.controls.LDAPPostReadRequestControl;
import org.opends.server.controls.LDAPPostReadResponseControl;
import org.opends.server.controls.LDAPPreReadRequestControl;
import org.opends.server.controls.LDAPPreReadResponseControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.AdditionalLogItem;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.WritabilityMode;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ProtocolMessages.ERR_PROXYAUTH_AUTHZ_NOT_PERMITTED;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a local backend workflow element; e-g an entity that
 * handle the processing of an operation against a local backend.
 */
public class LocalBackendWorkflowElement
{
  /**
   * This class implements the workflow result code. The workflow result code
   * contains an LDAP result code along with an LDAP error message.
   */
  private static class SearchResultCode
  {
    /** The global result code. */
    private ResultCode resultCode = ResultCode.UNDEFINED;

    /** The global error message. */
    private LocalizableMessageBuilder errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);

    /**
     * Creates a new instance of a workflow result code and initializes it with
     * a result code and an error message.
     *
     * @param resultCode
     *          the initial value for the result code
     * @param errorMessage
     *          the initial value for the error message
     */
    SearchResultCode(ResultCode resultCode, LocalizableMessageBuilder errorMessage)
    {
      this.resultCode = resultCode;
      this.errorMessage = errorMessage;
    }

    /**
     * Elaborates a global result code. A workflow may execute an operation on
     * several subordinate workflows. In such case, the parent workflow has to
     * take into account all the subordinate result codes to elaborate a global
     * result code. Sometimes, a referral result code has to be turned into a
     * reference entry. When such case is occurring the
     * elaborateGlobalResultCode method will return true. The global result code
     * is elaborated as follows:
     *
     * <PRE>
     *  -----------+------------+------------+-------------------------------
     *  new        | current    | resulting  |
     *  resultCode | resultCode | resultCode | action
     *  -----------+------------+------------+-------------------------------
     *  SUCCESS      NO_SUCH_OBJ  SUCCESS      -
     *               REFERRAL     SUCCESS      send reference entry to client
     *               other        [unchanged]  -
     *  ---------------------------------------------------------------------
     *  NO_SUCH_OBJ  SUCCESS      [unchanged]  -
     *               REFERRAL     [unchanged]  -
     *               other        [unchanged]  -
     *  ---------------------------------------------------------------------
     *  REFERRAL     SUCCESS      [unchanged]  send reference entry to client
     *               REFERRAL     SUCCESS      send reference entry to client
     *               NO_SUCH_OBJ  REFERRAL     -
     *               other        [unchanged]  send reference entry to client
     *  ---------------------------------------------------------------------
     *  others       SUCCESS      other        -
     *               REFERRAL     other        send reference entry to client
     *               NO_SUCH_OBJ  other        -
     *               other2       [unchanged]  -
     *  ---------------------------------------------------------------------
     * </PRE>
     *
     * @param newResultCode
     *          the new result code to take into account
     * @param newErrorMessage
     *          the new error message associated to the new error code
     * @return <code>true</code> if a referral result code must be turned into a
     *         reference entry
     */
    private boolean elaborateGlobalResultCode(ResultCode newResultCode, LocalizableMessageBuilder newErrorMessage)
    {
      // if global result code has not been set yet then just take the new
      // result code as is
      if (resultCode == ResultCode.UNDEFINED)
      {
        resultCode = newResultCode;
        errorMessage = new LocalizableMessageBuilder(newErrorMessage);
        return false;
      }

      // Elaborate the new result code (see table in the description header).
      switch (newResultCode.asEnum())
      {
      case SUCCESS:
        switch (resultCode.asEnum())
        {
        case NO_SUCH_OBJECT:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return false;
        case REFERRAL:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return true;
        default:
          // global resultCode remains the same
          return false;
        }

      case NO_SUCH_OBJECT:
        // global resultCode remains the same
        return false;

      case REFERRAL:
        switch (resultCode.asEnum())
        {
        case REFERRAL:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return true;
        case NO_SUCH_OBJECT:
          resultCode = ResultCode.REFERRAL;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return false;
        default:
          // global resultCode remains the same
          return true;
        }

      default:
        switch (resultCode.asEnum())
        {
        case REFERRAL:
          resultCode = newResultCode;
          errorMessage = new LocalizableMessageBuilder(newErrorMessage);
          return true;
        case SUCCESS:
        case NO_SUCH_OBJECT:
          resultCode = newResultCode;
          errorMessage = new LocalizableMessageBuilder(newErrorMessage);
          return false;
        default:
          // Do nothing (we don't want to override the first error)
          return false;
        }
      }
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend's baseDN mapped by this object. */
  private final DN baseDN;

  /** The backend associated with the local workflow element. */
  private final Backend<?> backend;

  /** The set of local backend workflow elements registered with the server. */
  private static TreeMap<DN, LocalBackendWorkflowElement> registeredLocalBackends = new TreeMap<>();

  /** A lock to guarantee safe concurrent access to the registeredLocalBackends variable. */
  private static final Object registeredLocalBackendsLock = new Object();

  /**
   * Creates a new instance of the local backend workflow element.
   *
   * @param baseDN
   *          the backend's baseDN mapped by this object
   * @param backend
   *          the backend associated to that workflow element
   */
  private LocalBackendWorkflowElement(DN baseDN, Backend<?> backend)
  {
    this.baseDN = baseDN;
    this.backend  = backend;
  }

  /**
   * Indicates whether the workflow element encapsulates a private local backend.
   *
   * @return <code>true</code> if the workflow element encapsulates a private
   *         local backend, <code>false</code> otherwise
   */
  public boolean isPrivate()
  {
    return this.backend != null && this.backend.isPrivateBackend();
  }

  /**
   * Creates and registers a local backend with the server.
   *
   * @param baseDN
   *          the backend's baseDN mapped by this object
   * @param backend
   *          the backend to associate with the local backend workflow element
   * @return the existing local backend workflow element if it was already
   *         created or a newly created local backend workflow element.
   */
  public static LocalBackendWorkflowElement createAndRegister(DN baseDN, Backend<?> backend)
  {
    LocalBackendWorkflowElement localBackend = registeredLocalBackends.get(baseDN);
    if (localBackend == null)
    {
      localBackend = new LocalBackendWorkflowElement(baseDN, backend);
      registerLocalBackend(localBackend);
    }
    return localBackend;
  }

  /**
   * Removes a local backend that was registered with the server.
   *
   * @param baseDN
   *          the identifier of the workflow to remove
   */
  public static void remove(DN baseDN)
  {
    deregisterLocalBackend(baseDN);
  }

  /**
   * Removes all the local backends that were registered with the server.
   * This function is intended to be called when the server is shutting down.
   */
  public static void removeAll()
  {
    synchronized (registeredLocalBackendsLock)
    {
      for (LocalBackendWorkflowElement localBackend : registeredLocalBackends.values())
      {
        deregisterLocalBackend(localBackend.getBaseDN());
      }
    }
  }

  /**
   * Check if an OID is for a proxy authorization control.
   *
   * @param oid The OID to check
   * @return <code>true</code> if the OID is for a proxy auth v1 or v2 control,
   * <code>false</code> otherwise.
   */
  static boolean isProxyAuthzControl(String oid)
  {
    return OID_PROXIED_AUTH_V1.equals(oid) || OID_PROXIED_AUTH_V2.equals(oid);
  }

  /**
   * Removes all the disallowed request controls from the provided operation.
   * <p>
   * As per RFC 4511 4.1.11, if a disallowed request control is critical, then a
   * DirectoryException is thrown with unavailableCriticalExtension. Otherwise,
   * if the disallowed request control is non critical, it is removed because we
   * do not want the backend to process it.
   *
   * @param operation
   *          the operation currently processed
   * @throws DirectoryException
   *           If a disallowed request control is critical, thrown with
   *           unavailableCriticalExtension. If an error occurred while
   *           performing the access control check. For example, if an attribute
   *           could not be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  static void removeAllDisallowedControls(DN targetDN, Operation operation) throws DirectoryException
  {
    for (Iterator<Control> iter = operation.getRequestControls().iterator(); iter.hasNext();)
    {
      final Control control = iter.next();
      if (isProxyAuthzControl(control.getOID()))
      {
        continue;
      }

      if (!getAccessControlHandler().isAllowed(targetDN, operation, control))
      {
        // As per RFC 4511 4.1.11.
        if (control.isCritical())
        {
          throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(control.getOID()));
        }

        // We do not want the backend to process this non-critical control, so remove it.
        iter.remove();
      }
    }
  }

  /**
   * Evaluate all aci and privilege checks for any proxy auth controls.
   * This must be done before evaluating all other controls so that their aci
   * can then be checked correctly.
   *
   * @param operation  The operation containing the controls
   * @throws DirectoryException if a proxy auth control is found but cannot
   * be used.
   */
  static void evaluateProxyAuthControls(Operation operation) throws DirectoryException
  {
    for (Control control : operation.getRequestControls())
    {
      final String oid = control.getOID();
      if (isProxyAuthzControl(oid))
      {
        DN authDN = operation.getClientConnection().getAuthenticationInfo().getAuthenticationDN();
        if (getAccessControlHandler().isAllowed(authDN, operation, control))
        {
          processProxyAuthControls(operation, oid);
        }
        else
        {
          // As per RFC 4511 4.1.11.
          if (control.isCritical())
          {
            throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(control.getOID()));
          }
        }
      }
    }
  }

  /**
   * Check the requester has the PROXIED_AUTH privilege in order to be able to use a proxy auth control.
   *
   * @param operation  The operation being checked
   * @throws DirectoryException  If insufficient privileges are detected
   */
  private static void checkPrivilegeForProxyAuthControl(Operation operation) throws DirectoryException
  {
    if (! operation.getClientConnection().hasPrivilege(Privilege.PROXIED_AUTH, operation))
    {
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
              ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
    }
  }

  /**
   * Check the requester has the authorization user in scope of proxy aci.
   *
   * @param operation  The operation being checked
   * @param authorizationEntry  The entry being authorized as (e.g. from a proxy auth control)
   * @throws DirectoryException  If no proxy permission is allowed
   */
  private static void checkAciForProxyAuthControl(Operation operation, Entry authorizationEntry)
      throws DirectoryException
  {
    if (! AccessControlConfigManager.getInstance().getAccessControlHandler()
            .mayProxy(operation.getClientConnection().getAuthenticationInfo().getAuthenticationEntry(),
                    authorizationEntry, operation))
    {
      throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
              ERR_PROXYAUTH_AUTHZ_NOT_PERMITTED.get(authorizationEntry.getName()));
    }
  }
  /**
   * Process the operation control with the given oid if it is a proxy auth control.
   *
   * Privilege and initial aci checks on the authenticating user are performed. The authenticating
   * user must have the proxied-auth privilege, and the authz user must be in the scope of aci
   * allowing the proxy right to the authenticating user.
   *
   * @param operation  The operation containing the control(s)
   * @param oid  The OID of the detected proxy auth control
   * @throws DirectoryException
   */
  private static void processProxyAuthControls(Operation operation, String oid)
          throws DirectoryException
  {
    final Entry authorizationEntry;

    if (OID_PROXIED_AUTH_V1.equals(oid))
    {
      final ProxiedAuthV1Control proxyControlV1 = operation.getRequestControl(ProxiedAuthV1Control.DECODER);
      // Log usage of legacy proxy authz V1 control.
      operation.addAdditionalLogItem(AdditionalLogItem.keyOnly(operation.getClass(),
              "obsoleteProxiedAuthzV1Control"));
      checkPrivilegeForProxyAuthControl(operation);
      authorizationEntry = proxyControlV1.getAuthorizationEntry();
    }
    else if (OID_PROXIED_AUTH_V2.equals(oid))
    {
      final ProxiedAuthV2Control proxyControlV2 = operation.getRequestControl(ProxiedAuthV2Control.DECODER);
      checkPrivilegeForProxyAuthControl(operation);
      authorizationEntry = proxyControlV2.getAuthorizationEntry();
    }
    else
    {
      return;
    }

    checkAciForProxyAuthControl(operation, authorizationEntry);
    operation.setAuthorizationEntry(authorizationEntry);

    operation.setProxiedAuthorizationDN(
      authorizationEntry != null ? authorizationEntry.getName() : DN.rootDN());
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
    if (getAccessControlHandler().canDiscloseInformation(entry, entryDN, operation))
    {
      return new DirectoryException(resultCode, message);
    }
    // replacement reason returned to the user
    final DirectoryException ex = new DirectoryException(altResultCode, altMessage);
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
    if (getAccessControlHandler().canDiscloseInformation(entry, entryDN, operation))
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
      if (!getAccessControlHandler().canDiscloseInformation(null, operation.getMatchedDN(), operation))
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

    // Even though the associated update succeeded,
    // we should still check whether we should return the entry.
    final SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(fullEntry, null);
    if (getAccessControlHandler().maySend(operation, unfilteredSearchEntry))
    {
      // Filter the entry based on the control's attribute list.
      final Entry filteredEntry = fullEntry.filterEntry(postReadRequest.getRequestedAttributes(), false, false, false);
      final SearchResultEntry filteredSearchEntry = new SearchResultEntry(filteredEntry, null);

      // Strip out any attributes which access control denies access to.
      getAccessControlHandler().filterEntry(operation, unfilteredSearchEntry, filteredSearchEntry);

      operation.addResponseControl(new LDAPPostReadResponseControl(filteredSearchEntry));
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

    // Even though the associated update succeeded,
    // we should still check whether we should return the entry.
    final SearchResultEntry unfilteredSearchEntry = new SearchResultEntry(entry, null);
    if (getAccessControlHandler().maySend(operation, unfilteredSearchEntry))
    {
      // Filter the entry based on the control's attribute list.
      final Entry filteredEntry = entry.filterEntry(preReadRequest.getRequestedAttributes(), false, false, false);
      final SearchResultEntry filteredSearchEntry = new SearchResultEntry(filteredEntry, null);

      // Strip out any attributes which access control denies access to.
      getAccessControlHandler().filterEntry(operation, unfilteredSearchEntry, filteredSearchEntry);

      operation.addResponseControl(new LDAPPreReadResponseControl(filteredSearchEntry));
    }
  }

  private static AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }

  /**
   * Registers a local backend with the server.
   *
   * @param localBackend  the local backend to register with the server
   */
  private static void registerLocalBackend(LocalBackendWorkflowElement localBackend)
  {
    synchronized (registeredLocalBackendsLock)
    {
      DN baseDN = localBackend.getBaseDN();
      LocalBackendWorkflowElement existingLocalBackend = registeredLocalBackends.get(baseDN);
      if (existingLocalBackend == null)
      {
        TreeMap<DN, LocalBackendWorkflowElement> newLocalBackends = new TreeMap<>(registeredLocalBackends);
        newLocalBackends.put(baseDN, localBackend);
        registeredLocalBackends = newLocalBackends;
      }
    }
  }

  /**
   * Deregisters a local backend with the server.
   *
   * @param baseDN
   *          the identifier of the local backend to remove
   */
  private static void deregisterLocalBackend(DN baseDN)
  {
    synchronized (registeredLocalBackendsLock)
    {
      LocalBackendWorkflowElement existingLocalBackend = registeredLocalBackends.get(baseDN);
      if (existingLocalBackend != null)
      {
        TreeMap<DN, LocalBackendWorkflowElement> newLocalBackends = new TreeMap<>(registeredLocalBackends);
        newLocalBackends.remove(baseDN);
        registeredLocalBackends = newLocalBackends;
      }
    }
  }

  /**
   * Executes the workflow for an operation.
   *
   * @param operation
   *          the operation to execute
   * @throws CanceledOperationException
   *           if this operation should be canceled
   */
  private void execute(Operation operation) throws CanceledOperationException {
    switch (operation.getOperationType())
    {
      case BIND:
        new LocalBackendBindOperation((BindOperation) operation).processLocalBind(this);
        break;

      case SEARCH:
        new LocalBackendSearchOperation((SearchOperation) operation).processLocalSearch(this);
        break;

      case ADD:
        new LocalBackendAddOperation((AddOperation) operation).processLocalAdd(this);
        break;

      case DELETE:
        new LocalBackendDeleteOperation((DeleteOperation) operation).processLocalDelete(this);
        break;

      case MODIFY:
        new LocalBackendModifyOperation((ModifyOperation) operation).processLocalModify(this);
        break;

      case MODIFY_DN:
        new LocalBackendModifyDNOperation((ModifyDNOperation) operation).processLocalModifyDN(this);
        break;

      case COMPARE:
        new LocalBackendCompareOperation((CompareOperation) operation).processLocalCompare(this);
        break;

      case ABANDON:
        // There is no processing for an abandon operation.
        break;

      default:
        throw new AssertionError("Attempted to execute an invalid operation type: "
            + operation.getOperationType() + " (" + operation + ")");
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
    List<?> existingAttachment = (List<?>) globalOperation.getAttachment(Operation.LOCALBACKENDOPERATIONS);
    List<L> newAttachment = new ArrayList<>();

    if (existingAttachment != null)
    {
      // This line raises an unchecked conversion warning.
      // There is nothing we can do to prevent this warning
      // so let's get rid of it since we know the cast is safe.
      newAttachment.addAll ((List<L>) existingAttachment);
    }
    newAttachment.add (currentLocalOperation);
    globalOperation.setAttachment(Operation.LOCALBACKENDOPERATIONS, newAttachment);
  }

  /**
   * Provides the workflow element identifier.
   *
   * @return the workflow element identifier
   */
  public DN getBaseDN()
  {
    return baseDN;
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
      checkIfWritable(DirectoryServer.getWritabilityMode(), op, serverMsg, entryDN);
      checkIfWritable(backend.getWritabilityMode(), op, backendMsg, entryDN);
    }
  }

  private static void checkIfWritable(WritabilityMode writabilityMode, Operation op,
      LocalizableMessageDescriptor.Arg1<Object> errorMsg, DN entryDN) throws DirectoryException
  {
    switch (writabilityMode)
    {
    case DISABLED:
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, errorMsg.get(entryDN));

    case INTERNAL_ONLY:
      if (!op.isInternalOperation() && !op.isSynchronizationOperation())
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, errorMsg.get(entryDN));
      }
    }
  }

  /**
   * Executes the supplied operation.
   *
   * @param operation
   *          the operation to execute
   * @param entryDN
   *          the entry DN whose backend will be used
   * @return true if the operation successfully executed, false otherwise
   * @throws CanceledOperationException
   *           if this operation should be cancelled.
   */
  public static boolean execute(Operation operation, DN entryDN) throws CanceledOperationException
  {
    LocalBackendWorkflowElement workflow = getLocalBackendWorkflowElement(entryDN);
    if (workflow == null)
    {
      // We have found no backend for the requested base DN,
      // just return a no such entry result code and stop the processing.
      if (operation instanceof AbstractOperation)
      {
        ((AbstractOperation) operation).updateOperationErrMsgAndResCode();
      }
      return false;
    }

    if (workflow.getBaseDN().isRootDN())
    {
      executeOnRootDSE(operation, workflow);
    }
    else
    {
      executeOnNonRootDSE(operation, workflow);
    }
    return true;
  }

  private static LocalBackendWorkflowElement getLocalBackendWorkflowElement(DN entryDN)
  {
    if (entryDN.isRootDN())
    {
      return registeredLocalBackends.get(entryDN);
    }
    /*
     * Try to minimize the number of lookups in the Map to find the backend containing the entry.
     * If the DN contains many RDNs it is faster to iterate through the list of registered backends,
     * otherwise iterating through the parents requires less lookups. It also avoids some attacks
     * where we would spend time going through the list of all parents to finally decide the
     * baseDN is absent.
     */
    if (entryDN.size() <= registeredLocalBackends.size())
    {
      while (!entryDN.isRootDN())
      {
        final LocalBackendWorkflowElement workflow = registeredLocalBackends.get(entryDN);
        if (workflow != null)
        {
          return workflow;
        }
        entryDN = entryDN.parent();
      }
      return null;
    }
    else
    {
      LocalBackendWorkflowElement workflow = null;
      int currentSize = 0;
      for (DN backendDN : registeredLocalBackends.keySet())
      {
        if (entryDN.isSubordinateOrEqualTo(backendDN) && backendDN.size() > currentSize)
        {
          workflow = registeredLocalBackends.get(backendDN);
          currentSize = backendDN.size();
        }
      }
      return workflow;
    }
  }

  /**
   * Executes an operation on the root DSE entry.
   *
   * @param operation
   *          the operation to execute
   * @param workflow
   *          the workflow where to execute the operation
   * @throws CanceledOperationException
   *           if this operation should be cancelled.
   */
  private static void executeOnRootDSE(Operation operation, LocalBackendWorkflowElement workflow)
      throws CanceledOperationException
  {
    OperationType operationType = operation.getOperationType();
    if (operationType == OperationType.SEARCH)
    {
      executeSearch((SearchOperation) operation, workflow);
    }
    else
    {
      workflow.execute(operation);
    }
  }

  /**
   * Executes a search operation on the the root DSE entry.
   *
   * @param searchOp
   *          the operation to execute
   * @param workflow
   *          the workflow where to execute the operation
   * @throws CanceledOperationException
   *           if this operation should be cancelled.
   */
  private static void executeSearch(SearchOperation searchOp, LocalBackendWorkflowElement workflow)
      throws CanceledOperationException
  {
    // Keep a the original search scope because we will alter it in the operation
    SearchScope originalScope = searchOp.getScope();

    // Search base?
    // The root DSE entry itself is never returned unless the operation
    // is a search base on the null suffix.
    if (originalScope == SearchScope.BASE_OBJECT)
    {
      workflow.execute(searchOp);
      return;
    }

    // Create a workflow result code in case we need to perform search in
    // subordinate workflows.
    SearchResultCode searchResultCode =
        new SearchResultCode(searchOp.getResultCode(), searchOp.getErrorMessage());

    // The search scope is not 'base', so let's do a search on all the public
    // naming contexts with appropriate new search scope and new base DN.
    SearchScope newScope = elaborateScopeForSearchInSubordinates(originalScope);
    searchOp.setScope(newScope);
    DN originalBaseDN = searchOp.getBaseDN();

    for (LocalBackendWorkflowElement subordinate : getRootDSESubordinates())
    {
      // We have to change the operation request base DN to match the
      // subordinate workflow base DN. Otherwise the workflow will
      // return a no such entry result code as the operation request
      // base DN is a superior of the workflow base DN!
      DN ncDN = subordinate.getBaseDN();

      // Set the new request base DN then do execute the operation
      // in the naming context workflow.
      searchOp.setBaseDN(ncDN);
      execute(searchOp, ncDN);
      boolean sendReferenceEntry = searchResultCode.elaborateGlobalResultCode(
          searchOp.getResultCode(), searchOp.getErrorMessage());
      if (sendReferenceEntry)
      {
        // TODO jdemendi - turn a referral result code into a reference entry
        // and send the reference entry to the client application
      }
    }

    // Now restore the original request base DN and original search scope
    searchOp.setBaseDN(originalBaseDN);
    searchOp.setScope(originalScope);

    // If the result code is still uninitialized (ie no naming context),
    // we should return NO_SUCH_OBJECT
    searchResultCode.elaborateGlobalResultCode(
        ResultCode.NO_SUCH_OBJECT, new LocalizableMessageBuilder(LocalizableMessage.EMPTY));

    // Set the operation result code and error message
    searchOp.setResultCode(searchResultCode.resultCode);
    searchOp.setErrorMessage(searchResultCode.errorMessage);
  }

  private static Collection<LocalBackendWorkflowElement> getRootDSESubordinates()
  {
    final RootDSEBackend rootDSEBackend = DirectoryServer.getRootDSEBackend();

    final List<LocalBackendWorkflowElement> results = new ArrayList<>();
    for (DN subordinateBaseDN : rootDSEBackend.getSubordinateBaseDNs().keySet())
    {
      results.add(registeredLocalBackends.get(subordinateBaseDN));
    }
    return results;
  }

  private static void executeOnNonRootDSE(Operation operation, LocalBackendWorkflowElement workflow)
      throws CanceledOperationException
  {
    workflow.execute(operation);

    // For subtree search operation we need to go through the subordinate nodes.
    if (operation.getOperationType() == OperationType.SEARCH)
    {
      executeSearchOnSubordinates((SearchOperation) operation, workflow);
    }
  }

  /**
   * Executes a search operation on the subordinate workflows.
   *
   * @param searchOp
   *          the search operation to execute
   * @param workflow
   *          the workflow element
   * @throws CanceledOperationException
   *           if this operation should be canceled.
   */
  private static void executeSearchOnSubordinates(SearchOperation searchOp, LocalBackendWorkflowElement workflow)
      throws CanceledOperationException {
    // If the scope of the search is 'base' then it's useless to search
    // in the subordinate workflows.
    SearchScope originalScope = searchOp.getScope();
    if (originalScope == SearchScope.BASE_OBJECT)
    {
      return;
    }

    // Elaborate the new search scope before executing the search operation
    // in the subordinate workflows.
    SearchScope newScope = elaborateScopeForSearchInSubordinates(originalScope);
    searchOp.setScope(newScope);

    // Let's search in the subordinate workflows.
    SearchResultCode searchResultCode = new SearchResultCode(searchOp.getResultCode(), searchOp.getErrorMessage());
    DN originalBaseDN = searchOp.getBaseDN();
    for (LocalBackendWorkflowElement subordinate : getSubordinates(workflow))
    {
      // We have to change the operation request base DN to match the
      // subordinate workflow base DN. Otherwise the workflow will
      // return a no such entry result code as the operation request
      // base DN is a superior of the subordinate workflow base DN.
      DN subordinateDN = subordinate.getBaseDN();

      // If the new search scope is 'base' and the search base DN does not
      // map the subordinate workflow then skip the subordinate workflow.
      if (newScope == SearchScope.BASE_OBJECT && !subordinateDN.parent().equals(originalBaseDN))
      {
        continue;
      }

      // If the request base DN is not a subordinate of the subordinate
      // workflow base DN then do not search in the subordinate workflow.
      if (!originalBaseDN.isSuperiorOrEqualTo(subordinateDN))
      {
        continue;
      }

      // Set the new request base DN and do execute the
      // operation in the subordinate workflow.
      searchOp.setBaseDN(subordinateDN);
      execute(searchOp, subordinateDN);
      boolean sendReferenceEntry = searchResultCode.elaborateGlobalResultCode(
          searchOp.getResultCode(), searchOp.getErrorMessage());
      if (sendReferenceEntry)
      {
        // TODO jdemendi - turn a referral result code into a reference entry
        // and send the reference entry to the client application
      }
    }

    // Now we are done with the operation, let's restore the original
    // base DN and search scope in the operation.
    searchOp.setBaseDN(originalBaseDN);
    searchOp.setScope(originalScope);

    // Update the operation result code and error message
    searchOp.setResultCode(searchResultCode.resultCode);
    searchOp.setErrorMessage(searchResultCode.errorMessage);
  }

  private static Collection<LocalBackendWorkflowElement> getSubordinates(LocalBackendWorkflowElement workflow)
  {
    final DN baseDN = workflow.getBaseDN();
    final Backend<?> backend = workflow.getBackend();

    final ArrayList<LocalBackendWorkflowElement> results = new ArrayList<>();
    for (Backend<?> subordinate : backend.getSubordinateBackends())
    {
      for (DN subordinateDN : subordinate.getBaseDNs())
      {
        if (subordinateDN.isSubordinateOrEqualTo(baseDN))
        {
          results.add(registeredLocalBackends.get(subordinateDN));
        }
      }
    }
    return results;
  }

  /**
   * Elaborates a new search scope according to the current search scope. The
   * new scope is intended to be used for searches on subordinate workflows.
   *
   * @param currentScope
   *          the current search scope
   * @return the new scope to use for searches on subordinate workflows,
   *         <code>null</code> when current scope is 'base'
   */
  private static SearchScope elaborateScopeForSearchInSubordinates(SearchScope currentScope)
  {
    switch (currentScope.asEnum())
    {
    case BASE_OBJECT:
      return null;
    case SINGLE_LEVEL:
      return SearchScope.BASE_OBJECT;
    case SUBORDINATES:
    case WHOLE_SUBTREE:
      return SearchScope.WHOLE_SUBTREE;
    default:
      return currentScope;
    }
  }

  static DN findMatchedDN(DN entryDN)
  {
    try
    {
      DN matchedDN = DirectoryServer.getParentDNInSuffix(entryDN);
      while (matchedDN != null)
      {
        if (DirectoryServer.entryExists(matchedDN))
        {
          return matchedDN;
        }

        matchedDN = DirectoryServer.getParentDNInSuffix(matchedDN);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName()
        + " backend=" + this.backend
        + " baseDN=" + this.baseDN;
  }
}
