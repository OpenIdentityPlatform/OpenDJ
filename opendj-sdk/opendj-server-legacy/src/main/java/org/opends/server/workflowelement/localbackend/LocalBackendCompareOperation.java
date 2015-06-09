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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.core.*;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public class LocalBackendCompareOperation
       extends CompareOperationWrapper
       implements PreOperationCompareOperation, PostOperationCompareOperation,
                  PostResponseCompareOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * The backend in which the comparison is to be performed.
   */
  private Backend<?> backend;

  /**
   * The client connection for this operation.
   */
  private ClientConnection clientConnection;

  /**
   * The DN of the entry to compare.
   */
  private DN entryDN;

  /**
   * The entry to be compared.
   */
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
        PluginResult.PostOperation postOpResult =
            DirectoryServer.getPluginConfigManager()
                .invokePostOperationComparePlugins(this);
        if (!postOpResult.continueProcessing())
        {
          setResultCode(postOpResult.getResultCode());
          appendErrorMessage(postOpResult.getErrorMessage());
          setMatchedDN(postOpResult.getMatchedDN());
          setReferralURLs(postOpResult.getReferralURLs());
        }
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
    if (DirectoryServer.getConfigHandler().handlesEntry(entryDN)
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
        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(this))
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
      PluginResult.PreOperation preOpResult =
          DirectoryServer.getPluginConfigManager()
              .invokePreOperationComparePlugins(this);
      if (!preOpResult.continueProcessing())
      {
        setResultCode(preOpResult.getResultCode());
        appendErrorMessage(preOpResult.getErrorMessage());
        setMatchedDN(preOpResult.getMatchedDN());
        setReferralURLs(preOpResult.getReferralURLs());
        return;
      }


      // Get the base attribute type and set of options.
      Set<String> options = getAttributeOptions();

      // Actually perform the compare operation.
      AttributeType attrType = getAttributeType();

      List<Attribute> attrList = entry.getAttribute(attrType, options);
      if ((attrList == null) || attrList.isEmpty())
      {
        setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
        if (options == null)
        {
          appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR.get(entryDN, getRawAttributeType()));
        }
        else
        {
          appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS.get(entryDN, getRawAttributeType()));
        }
      }
      else
      {
        ByteString value = getAssertionValue();

        boolean matchFound = false;
        for (Attribute a : attrList)
        {
          if (a.contains(value))
          {
            matchFound = true;
            break;
          }
        }

        if (matchFound)
        {
          setResultCode(ResultCode.COMPARE_TRUE);
        }
        else
        {
          setResultCode(ResultCode.COMPARE_FALSE);
        }
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);
      setResponseData(de);
    }
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
      logger.traceException(e);
    }
    return null;
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

    List<Control> requestControls = getRequestControls();
    if (requestControls != null && !requestControls.isEmpty())
    {
      for (Control c : requestControls)
      {
        final String  oid = c.getOID();

        if (OID_LDAP_ASSERTION.equals(oid))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

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
              throw newDirectoryException(entry, ResultCode.ASSERTION_FAILED,
                  ERR_COMPARE_ASSERTION_FAILED.get(entryDN));
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

        // NYI -- Add support for additional controls.
        else if (c.isCritical()
            && (backend == null || !backend.supportsControl(oid)))
        {
          throw new DirectoryException(
              ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
              ERR_COMPARE_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
        }
      }
    }
  }
}
