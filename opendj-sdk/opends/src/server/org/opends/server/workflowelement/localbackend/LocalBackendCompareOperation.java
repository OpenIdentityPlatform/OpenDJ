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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;



import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.LDAPAssertionRequestControl;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AccessControlConfigManager;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.CompareOperationWrapper;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PluginConfigManager;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationCompareOperation;
import org.opends.server.types.operation.PostResponseCompareOperation;
import org.opends.server.types.operation.PreOperationCompareOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The backend in which the comparison is to be performed.
  private Backend backend;

  // The client connection for this operation.
  private ClientConnection clientConnection;

  // The DN of the entry to compare.
  private DN entryDN;

  // The entry to be compared.
  private Entry entry = null;



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
  public Entry getEntryToCompare()
  {
    return entry;
  }



  /**
   * Process this compare operation in a local backend.
   *
   * @param  backend  The backend in which the compare operation should be
   *                  processed.
   *
   * @throws CanceledOperationException if this operation should be
   * cancelled
   */
  void processLocalCompare(Backend backend) throws CanceledOperationException {
    boolean executePostOpPlugins = false;

    this.backend = backend;

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


      // Grab a read lock on the entry.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(entryDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        setResultCode(DirectoryServer.getServerErrorResultCode());
        appendErrorMessage(ERR_COMPARE_CANNOT_LOCK_ENTRY.get(
                                String.valueOf(entryDN)));

        break compareProcessing;
      }

      try
      {
        // Get the entry.  If it does not exist, then fail.
        try
        {
          entry = DirectoryServer.getEntry(entryDN);

          if (entry == null)
          {
            setResultCode(ResultCode.NO_SUCH_OBJECT);
            appendErrorMessage(
                    ERR_COMPARE_NO_SUCH_ENTRY.get(String.valueOf(entryDN)));

            // See if one of the entry's ancestors exists.
            DN parentDN = entryDN.getParentDNInSuffix();
            while (parentDN != null)
            {
              try
              {
                if (DirectoryServer.entryExists(parentDN))
                {
                  setMatchedDN(parentDN);
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

              parentDN = parentDN.getParentDNInSuffix();
            }

            break compareProcessing;
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          setResultCode(de.getResultCode());
          appendErrorMessage(de.getMessageObject());
          break compareProcessing;
        }

        // Check to see if there are any controls in the request.  If so, then
        // see if there is any special processing required.
        try
        {
          handleRequestControls();
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
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
        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(this))
        {
          setResultCode(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
          appendErrorMessage(ERR_COMPARE_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(
                                  String.valueOf(entryDN)));
          break compareProcessing;
        }

        // Check for a request to cancel this operation.
        checkIfCanceled(false);


        // Invoke the pre-operation compare plugins.
        executePostOpPlugins = true;
        PluginResult.PreOperation preOpResult =
             pluginConfigManager.invokePreOperationComparePlugins(this);
          if (!preOpResult.continueProcessing())
          {
            setResultCode(preOpResult.getResultCode());
            appendErrorMessage(preOpResult.getErrorMessage());
            setMatchedDN(preOpResult.getMatchedDN());
            setReferralURLs(preOpResult.getReferralURLs());
            break compareProcessing;
          }


        // Get the base attribute type and set of options.
        String          baseName;
        HashSet<String> options;
        String rawAttributeType = getRawAttributeType();
        int             semicolonPos = rawAttributeType.indexOf(';');
        if (semicolonPos > 0)
        {
          baseName = toLowerCase(rawAttributeType.substring(0, semicolonPos));

          options = new HashSet<String>();
          int nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          while (nextPos > 0)
          {
            options.add(rawAttributeType.substring(semicolonPos+1, nextPos));
            semicolonPos = nextPos;
            nextPos = rawAttributeType.indexOf(';', semicolonPos+1);
          }

          options.add(rawAttributeType.substring(semicolonPos+1));
        }
        else
        {
          baseName = toLowerCase(rawAttributeType);
          options  = null;
        }


        // Actually perform the compare operation.
        AttributeType attrType = getAttributeType();
        if (attrType == null)
        {
          attrType = DirectoryServer.getAttributeType(baseName, true);
          setAttributeType(attrType);
        }

        List<Attribute> attrList = entry.getAttribute(attrType, options);
        if ((attrList == null) || attrList.isEmpty())
        {
          setResultCode(ResultCode.NO_SUCH_ATTRIBUTE);
          if (options == null)
          {
            appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR.get(
                                    String.valueOf(entryDN), baseName));
          }
          else
          {
            appendErrorMessage(WARN_COMPARE_OP_NO_SUCH_ATTR_WITH_OPTIONS.get(
                                    String.valueOf(entryDN), baseName));
          }
        }
        else
        {
          AttributeValue value = new AttributeValue(attrType,
                                                    getAssertionValue());

          boolean matchFound = false;
          for (Attribute a : attrList)
          {
            if (a.hasValue(value))
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
      finally
      {
        LockManager.unlock(entryDN, readLock);
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



  /**
   * Performs any processing required for the controls included in the request.
   *
   * @throws  DirectoryException  If a problem occurs that should prevent the
   *                              operation from succeeding.
   */
  private void handleRequestControls()
          throws DirectoryException
  {
    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (! AccessControlConfigManager.getInstance().
                   getAccessControlHandler().isAllowed(entryDN, this, c))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                         ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
        }

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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }

          try
          {
            // FIXME -- We need to determine whether the current user has
            //          permission to make this determination.
            SearchFilter filter = assertControl.getSearchFilter();
            if (! filter.matchesEntry(entry))
            {
              throw new DirectoryException(ResultCode.ASSERTION_FAILED,
                                           ERR_COMPARE_ASSERTION_FAILED.get(
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
                           ERR_COMPARE_CANNOT_PROCESS_ASSERTION_FILTER.get(
                                String.valueOf(entryDN),
                                de.getMessageObject()));
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // The requester must have the PROXIED_AUTH privilige in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


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
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
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

              throw new DirectoryException(
                             ResultCode.valueOf(le.getResultCode()),
                             le.getMessageObject());
            }
          }


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

        // NYI -- Add support for additional controls.
        else if (c.isCritical())
        {
          if ((backend == null) || (! backend.supportsControl(oid)))
          {
            throw new DirectoryException(
                           ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                           ERR_COMPARE_UNSUPPORTED_CRITICAL_CONTROL.get(
                                String.valueOf(entryDN), oid));
          }
        }
      }
    }
  }
}

