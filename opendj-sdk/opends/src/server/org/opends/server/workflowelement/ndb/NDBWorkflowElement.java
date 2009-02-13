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



import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.LocalBackendWorkflowElementCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
import
  org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;

import static org.opends.server.config.ConfigConstants.*;



/**
 * This class defines a NDB backend workflow element; e-g an entity that
 * handle the processing of an operation against a NDB backend.
 */
public class NDBWorkflowElement extends LocalBackendWorkflowElement
{
  // The backend associated with the NDB workflow element.
  private Backend backend;


  // The set of NDB backend workflow elements registered with the server.
  private static TreeMap<String, NDBWorkflowElement>
       registeredNDBBackends =
            new TreeMap<String, NDBWorkflowElement>();


  // The lock to guarantee safe concurrent access to the
  // registeredNDBBackends variable.
  private static final Object registeredNDBBackendsLock = new Object();


  // The string indicating the type of the workflow element.
  private final String BACKEND_WORKFLOW_ELEMENT = "Backend";


  /**
   * Creates a new instance of the NDB backend workflow element.
   */
  public NDBWorkflowElement()
  {
    // There is nothing to do in this constructor.
  }


  /**
   * Initializes a new instance of the NDB backend workflow element.
   * This method is intended to be called by DirectoryServer when
   * workflow configuration mode is auto as opposed to
   * initializeWorkflowElement which is invoked when workflow
   * configuration mode is manual.
   *
   * @param workflowElementID  the workflow element identifier
   * @param backend  the backend associated to that workflow element
   */
  private void initialize(String workflowElementID, Backend backend)
  {
    // Initialize the workflow ID
    super.initialize(workflowElementID, BACKEND_WORKFLOW_ELEMENT);

    this.backend  = backend;

    if (this.backend != null)
    {
      setPrivate(this.backend.isPrivateBackend());
    }
  }


  /**
   * Initializes a new instance of the NDB backend workflow element.
   * This method is intended to be called by DirectoryServer when
   * workflow configuration mode is manual as opposed to
   * initialize(String,Backend) which is invoked when workflow
   * configuration mode is auto.
   *
   * @param  configuration  The configuration for this NDB backend
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
  @Override
  public void initializeWorkflowElement(
      LocalBackendWorkflowElementCfg configuration
      ) throws ConfigException, InitializationException
  {
    configuration.addLocalBackendChangeListener(this);

    // Read configuration and apply changes.
    processWorkflowElementConfig(configuration, true);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise
    // an NPE
    super.initialize(null, null);
    backend = null;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      LocalBackendWorkflowElementCfg configuration,
      List<Message>                  unacceptableReasons
      )
  {
    boolean isAcceptable =
      processWorkflowElementConfig(configuration, false);

    return isAcceptable;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      LocalBackendWorkflowElementCfg configuration
      )
  {
    // Returned result.
    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<Message>()
        );

    processWorkflowElementConfig(configuration, true);

    return changeResult;
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
      Backend newBackend  = DirectoryServer.getBackend(newBackendID);

      // If the backend is null (i.e. not found in the list of
      // registered backends, this is probably because we are looking
      // for the config backend
      if (newBackend == null) {
        ServerManagementContext context = ServerManagementContext.getInstance();
        RootCfg root = context.getRootConfiguration();
        try {
          BackendCfg backendCfg = root.getBackend(newBackendID);
          if (backendCfg.getBaseDN().contains(DN.decode(DN_CONFIG_ROOT))) {
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
          configuration.getWorkflowElementId(), BACKEND_WORKFLOW_ELEMENT);
        backend = newBackend;
      }
    }

    return isAcceptable;
  }


  /**
   * Creates and registers a NDB backend with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to create
   * @param backend            the backend to associate with the NDB backend
   *                           workflow element
   *
   * @return the existing NDB backend workflow element if it was
   *         already created or a newly created NDB backend workflow
   *         element.
   */
  public static NDBWorkflowElement createAndRegister(
      String workflowElementID,
      Backend backend)
  {
    NDBWorkflowElement ndbBackend = null;

    // If the requested workflow element does not exist then create one.
    ndbBackend = registeredNDBBackends.get(workflowElementID);
    if (ndbBackend == null)
    {
      ndbBackend = new NDBWorkflowElement();
      ndbBackend.initialize(workflowElementID, backend);

      // store the new NDB backend in the list of registered backends
      registerNDBBackend(ndbBackend);
    }

    return ndbBackend;
  }



  /**
   * Removes a NDB backend that was registered with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to remove
   */
  public static void remove(String workflowElementID)
  {
    deregisterNDBBackend(workflowElementID);
  }



  /**
   * Removes all the NDB backends that were registered with the server.
   * This function is intended to be called when the server is shutting down.
   */
  public static void removeAll()
  {
    synchronized (registeredNDBBackendsLock)
    {
      for (NDBWorkflowElement ndbBackend:
           registeredNDBBackends.values())
      {
        deregisterNDBBackend(ndbBackend.getWorkflowElementID());
      }
    }
  }



  /**
   * Registers a NDB backend with the server.
   *
   * @param ndbBackend  the NDB backend to register with the server
   */
  private static void registerNDBBackend(
                           NDBWorkflowElement ndbBackend)
  {
    synchronized (registeredNDBBackendsLock)
    {
      String ndbBackendID = ndbBackend.getWorkflowElementID();
      NDBWorkflowElement existingNDBBackend =
        registeredNDBBackends.get(ndbBackendID);

      if (existingNDBBackend == null)
      {
        TreeMap<String, NDBWorkflowElement> newNDBBackends =
          new TreeMap
            <String, NDBWorkflowElement>(registeredNDBBackends);
        newNDBBackends.put(ndbBackendID, ndbBackend);
        registeredNDBBackends = newNDBBackends;
      }
    }
  }



  /**
   * Deregisters a NDB backend with the server.
   *
   * @param workflowElementID  the identifier of the workflow element to remove
   */
  private static void deregisterNDBBackend(String workflowElementID)
  {
    synchronized (registeredNDBBackendsLock)
    {
      NDBWorkflowElement existingNDBBackend =
        registeredNDBBackends.get(workflowElementID);

      if (existingNDBBackend != null)
      {
        TreeMap<String, NDBWorkflowElement> newNDBBackends =
             new TreeMap<String, NDBWorkflowElement>(
                      registeredNDBBackends);
        newNDBBackends.remove(workflowElementID);
        registeredNDBBackends = newNDBBackends;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void execute(Operation operation) throws CanceledOperationException {
    switch (operation.getOperationType())
    {
      case BIND:
        NDBBindOperation bindOperation =
             new NDBBindOperation((BindOperation) operation);
        bindOperation.processLocalBind(this);
        break;

      case SEARCH:
        NDBSearchOperation searchOperation =
             new NDBSearchOperation((SearchOperation) operation);
        searchOperation.processLocalSearch(this);
        break;

      case ADD:
        NDBAddOperation addOperation =
             new NDBAddOperation((AddOperation) operation);
        addOperation.processLocalAdd(this);
        break;

      case DELETE:
        NDBDeleteOperation deleteOperation =
             new NDBDeleteOperation((DeleteOperation) operation);
        deleteOperation.processLocalDelete(this);
        break;

      case MODIFY:
        NDBModifyOperation modifyOperation =
             new NDBModifyOperation((ModifyOperation) operation);
        modifyOperation.processLocalModify(this);
        break;

      case MODIFY_DN:
        NDBModifyDNOperation modifyDNOperation =
             new NDBModifyDNOperation((ModifyDNOperation) operation);
        modifyDNOperation.processLocalModifyDN(this);
        break;

      case COMPARE:
        NDBCompareOperation compareOperation =
             new NDBCompareOperation((CompareOperation) operation);
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
   * Gets the backend associated with this NDB backend workflow element.
   *
   * @return The backend associated with this NDB backend workflow
   *         element.
   */
  @Override
  public Backend getBackend()
  {
    return backend;
  }
}
