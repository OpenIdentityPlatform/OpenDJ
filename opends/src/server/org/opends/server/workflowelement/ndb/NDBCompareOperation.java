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
package org.opends.server.workflowelement.ndb;



import java.util.HashSet;
import java.util.List;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;

import
  org.opends.server.workflowelement.localbackend.LocalBackendCompareOperation;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an operation that may be used to determine whether a
 * specified entry in the Directory Server contains a given attribute-value
 * pair.
 */
public class NDBCompareOperation
       extends LocalBackendCompareOperation
       implements PreOperationCompareOperation, PostOperationCompareOperation,
                  PostResponseCompareOperation
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Creates a new compare operation based on the provided compare operation.
   *
   * @param compare  the compare operation
   */
  public NDBCompareOperation(CompareOperation compare)
  {
    super(compare);
    NDBWorkflowElement.attachLocalOperation (compare, this);
  }



  /**
   * Process this compare operation in a NDB backend.
   *
   * @param  wfe The local backend work-flow element.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  @Override
  public void processLocalCompare(LocalBackendWorkflowElement wfe)
    throws CanceledOperationException {
    boolean executePostOpPlugins = false;

    this.backend = wfe.getBackend();

    clientConnection  = getClientConnection();

    // Get the plugin config manager that will be used for invoking plugins.
    PluginConfigManager pluginConfigManager =
         DirectoryServer.getPluginConfigManager();


    // Get a reference to the client connection
    ClientConnection clientConnection = getClientConnection();


    // Check for a request to cancel this operation.
    checkIfCanceled(false);


    // Create a labeled block of code that we can break out of if a problem is
    // detected.
compareProcessing:
    {
      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the compare processing.
      entryDN = getEntryDN();
      if (entryDN == null)
      {
        break compareProcessing;
      }


      // If the target entry is in the server configuration, then make sure the
      // requester has the CONFIG_READ privilege.
      if (DirectoryServer.getConfigHandler().handlesEntry(entryDN) &&
          (! clientConnection.hasPrivilege(Privilege.CONFIG_READ, this)))
      {
        appendErrorMessage(ERR_COMPARE_CONFIG_INSUFFICIENT_PRIVILEGES.get());
        setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        break compareProcessing;
      }


      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Get the entry.  If it does not exist, then fail.
      try {
        entry = DirectoryServer.getEntry(entryDN);

        if (entry == null) {
          setResultCode(ResultCode.NO_SUCH_OBJECT);
          appendErrorMessage(
            ERR_COMPARE_NO_SUCH_ENTRY.get(String.valueOf(entryDN)));

          // See if one of the entry's ancestors exists.
          DN parentDN = entryDN.getParentDNInSuffix();
          while (parentDN != null) {
            try {
              if (DirectoryServer.entryExists(parentDN)) {
                setMatchedDN(parentDN);
                break;
              }
            } catch (Exception e) {
              if (debugEnabled()) {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }
              break;
            }

            parentDN = parentDN.getParentDNInSuffix();
          }

          break compareProcessing;
        }
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResultCode(de.getResultCode());
        appendErrorMessage(de.getMessageObject());
        break compareProcessing;
      }

      // Check to see if there are any controls in the request.  If so, then
      // see if there is any special processing required.
      try {
        handleRequestControls();
      } catch (DirectoryException de) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, de);
        }

        setResponseData(de);
        break compareProcessing;
      }


      // Check to see if the client has permission to perform the
      // compare.

      // FIXME: for now assume that this will check all permission
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists may
      // have already exposed sensitive information to the client.
      try
      {
        if (!AccessControlConfigManager.getInstance()
            .getAccessControlHandler().isAllowed(this))
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS
              .get(String.valueOf(entryDN)));
          break compareProcessing;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        break compareProcessing;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);


      // Invoke the pre-operation compare plugins.
      executePostOpPlugins = true;
      PluginResult.PreOperation preOpResult =
        pluginConfigManager.invokePreOperationComparePlugins(this);
      if (!preOpResult.continueProcessing()) {
        setResultCode(preOpResult.getResultCode());
        appendErrorMessage(preOpResult.getErrorMessage());
        setMatchedDN(preOpResult.getMatchedDN());
        setReferralURLs(preOpResult.getReferralURLs());
        break compareProcessing;
      }


      // Get the base attribute type and set of options.
      String baseName;
      HashSet<String> options;
      String rawAttributeType = getRawAttributeType();
      int semicolonPos = rawAttributeType.indexOf(';');
      if (semicolonPos > 0) {
        baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

        options = new HashSet<String>();
        int nextPos = rawAttributeType.indexOf(';', semicolonPos + 1);
        while (nextPos > 0) {
          options.add(rawAttributeType.substring(semicolonPos + 1, nextPos));
          semicolonPos = nextPos;
          nextPos = rawAttributeType.indexOf(';', semicolonPos + 1);
        }

        options.add(rawAttributeType.substring(semicolonPos + 1));
      } else {
        baseName = toLowerCase(rawAttributeType);
        options = null;
      }


      // Actually perform the compare operation.
      AttributeType attrType = getAttributeType();
      if (attrType == null) {
        attrType = DirectoryServer.getAttributeType(baseName, true);
        setAttributeType(attrType);
      }

      List<Attribute> attrList = entry.getAttribute(attrType, options);
      if ((attrList == null) || attrList.isEmpty()) {
        setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
        if (options == null) {
          appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR.get(
            String.valueOf(entryDN), baseName));
        } else {
          appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS.get(
            String.valueOf(entryDN), baseName));
        }
      } else {
        AttributeValue value = AttributeValues.create(attrType,
          getAssertionValue());

        boolean matchFound = false;
        for (Attribute a : attrList) {
          if (a.contains(value)) {
            matchFound = true;
            break;
          }
        }

        if (matchFound) {
          setResultCode(ResultCode.COMPARE_TRUE);
        } else {
          setResultCode(ResultCode.COMPARE_FALSE);
        }
      }
    }


    // Check for a request to cancel this operation.
    checkIfCanceled(false);


    // Invoke the post-operation compare plugins.
    if (executePostOpPlugins)
    {
      PluginResult.PostOperation postOpResult =
           pluginConfigManager.invokePostOperationComparePlugins(this);
      if (!postOpResult.continueProcessing())
      {
        setResultCode(postOpResult.getResultCode());
        appendErrorMessage(postOpResult.getErrorMessage());
        setMatchedDN(postOpResult.getMatchedDN());
        setReferralURLs(postOpResult.getReferralURLs());
      }
    }
  }
}
