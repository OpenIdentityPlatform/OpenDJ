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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.ConfigurationBackend;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.CompareOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Privilege;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.types.AbstractOperation.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value pair.
 */
public class LocalBackendCompareOperation
       extends CompareOperationWrapper
       implements PreOperationCompareOperation, PostOperationCompareOperation,
                  PostResponseCompareOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The backend in which the comparison is to be performed. */
  private Backend<?> backend;
  /** The client connection for this operation. */
  private ClientConnection clientConnection;
  /** The DN of the entry to compare. */
  private DN entryDN;
  /** The entry to be compared. */
  private Entry entry;



  /**
   * Creates a new compare operation based on the provided compare operation.
   *
   * @param compare  the compare operation
   */
  public LocalBackendCompareOperation(CompareOperation compare)
  {
    super(compare);
    LocalBackendWorkflowElement.attachLocalOperation (compare, this);
  }



  /**
   * Retrieves the entry to target with the compare operation.
   *
   * @return  The entry to target with the compare operation, or
   *          <CODE>null</CODE> if the entry is not yet available.
   */
  @Override
  public Entry getEntryToCompare()
  {
    return entry;
  }



  /**
   * Process this compare operation in a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalCompare(LocalBackendWorkflowElement wfe)
      throws CanceledOperationException
  {
    this.backend = wfe.getBackend();

    clientConnection  = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      AtomicBoolean executePostOpPlugins = new AtomicBoolean(false);
      processCompare(executePostOpPlugins);

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the post-operation compare plugins.
      if (executePostOpPlugins.get())
      {
        processOperationResult(this, getPluginConfigManager().invokePostOperationComparePlugins(this));
      }
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }
  }

  private void processCompare(AtomicBoolean executePostOpPlugins)
      throws CanceledOperationException
  {
    // Process the entry DN to convert it from the raw form to the form
    // required for the rest of the compare processing.
    entryDN = getEntryDN();
    if (entryDN == null)
    {
      return;
    }


    // If the target entry is in the server configuration, then make sure the
    // requester has the CONFIG_READ privilege.
    if (DirectoryServer.getBackend(ConfigurationBackend.CONFIG_BACKEND_ID).handlesEntry(entryDN)
        && !clientConnection.hasPrivilege(Privilege.CONFIG_READ, this))
    {
      appendErrorMessage(ERR_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES.get());
      setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
      return;
    }

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      // Get the entry. If it does not exist, then fail.
      try
      {
        entry = DirectoryServer.getEntry(entryDN);
        if (entry == null)
        {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(ERR_COMPARE_NO_SUCH_ENTRY.get(entryDN));

          // See if one of the entry's ancestors exists.
          setMatchedDN(findMatchedDN(entryDN));
          return;
        }
      }
      catch (DirectoryException de)
      {
        logger.traceException(de);

        setResultCodeAndMessageNoInfoDisclosure(entry, entryDN,
            de.getResultCode(), de.getMessageObject());
        return;
      }

      // Check to see if there are any controls in the request. If so, then
      // see if there is any special processing required.
      handleRequestControls();


      // Check to see if the client has permission to perform the
      // compare.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists may
      // have already exposed sensitive information to the client.
      try
      {
        if (!getAccessControlHandler().isAllowed(this))
        {
          setResultCodeAndMessageNoInfoDisclosure(entry, entryDN,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
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


      // Invoke the pre-operation compare plugins.
      executePostOpPlugins.set(true);
      if (!processOperationResult(this, getPluginConfigManager().invokePreOperationComparePlugins(this)))
      {
        return;
      }

      // Actually perform the compare operation.
      AttributeDescription attrDesc = getAttributeDescription();
      List<Attribute> attrList = entry.getAttribute(attrDesc);
      if (attrList.isEmpty())
      {
        setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
        Arg2<Object, Object> errorMsg = attrDesc.hasOptions()
            ? WARN_COMPARE_OP_NO_SUCH_ATTR
            : WARN_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS;
        appendErrorMessage(errorMsg.get(entryDN, getRawAttributeType()));
      }
      else
      {
        ByteString value = getAssertionValue();
        setResultCode(matchExists(attrList, value));
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);
      setResponseData(de);
    }
  }

  private ResultCode matchExists(List<Attribute> attrList, ByteString value)
  {
    for (Attribute a : attrList)
    {
      if (a.contains(value))
      {
        return ResultCode.COMPARE_TRUE;
      }
    }
    return ResultCode.COMPARE_FALSE;
  }

  private DirectoryException newDirectoryException(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, entry, null,
        resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_COMPARE_NO_SUCH_ENTRY.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(Entry entry, DN entryDN,
      ResultCode realResultCode, LocalizableMessage realMessage) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        entry, entryDN, realResultCode, realMessage, ResultCode.NO_SUCH_OBJECT,
        ERR_COMPARE_NO_SUCH_ENTRY.get(entryDN));
  }

  /**
   * Performs any processing required for the controls included in the request.
   *
   * @throws  DirectoryException  If a problem occurs that should prevent the
   *                              operation from succeeding.
   */
  private void handleRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.evaluateProxyAuthControls(this);
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    for (Control c : getRequestControls())
    {
      final String oid = c.getOID();

      if (OID_LDAP_ASSERTION.equals(oid))
      {
        LDAPAssertionRequestControl assertControl = getRequestControl(LDAPAssertionRequestControl.DECODER);

        SearchFilter filter;
        try
        {
          filter = assertControl.getSearchFilter();
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          throw newDirectoryException(entry, de.getResultCode(),
              ERR_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
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
            throw newDirectoryException(entry, ResultCode.ASSERTION_FAILED, ERR_COMPARE_ASSERTION_FAILED.get(entryDN));
          }
        }
        catch (DirectoryException de)
        {
          if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
          {
            throw de;
          }

          logger.traceException(de);

          throw newDirectoryException(entry, de.getResultCode(),
              ERR_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER.get(entryDN, de.getMessageObject()));
        }
      }
      else if (LocalBackendWorkflowElement.isProxyAuthzControl(oid))
      {
        continue;
      }
      else if (c.isCritical() && (backend == null || !backend.supportsControl(oid)))
      {
        throw new DirectoryException(ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
            ERR_COMPARE_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
      }
    }
  }

  private AccessControlHandler<?> getAccessControlHandler()
  {
    return AccessControlConfigManager.getInstance().getAccessControlHandler();
  }
}
