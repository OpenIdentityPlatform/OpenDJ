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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.workflowelement.localbackend;



import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.LocalBackendWorkflowElementCfg;
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
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;
import org.opends.server.workflowelement.LeafWorkflowElement;



/**
 * This class defines a local backend workflow element; e-g an entity that
 * handle the processing of an operation aginst a local backend.
 */
public class LocalBackendWorkflowElement extends
    LeafWorkflowElement<LocalBackendWorkflowElementCfg>
    implements ConfigurationChangeListener<LocalBackendWorkflowElementCfg>
{
  // the backend associated with the local workflow element
  private Backend backend;


  // the set of local backend workflow elements registered with the server
  private static TreeMap<String, LocalBackendWorkflowElement>
       registeredLocalBackends =
            new TreeMap<String, LocalBackendWorkflowElement>();


  // a lock to guarantee safe concurrent access to the registeredLocalBackends
  // variable
  private static Object registeredLocalBackendsLock = new Object();


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
  private void initialize(String workflowElementID, Backend backend)
  {
    // Initialize the workflow ID
    super.initialize(workflowElementID);

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


  /**
   * {@inheritDoc}
   */
  public void finalizeWorkflowElement()
  {
    // null all fields so that any use of the finalized object will raise
    // an NPE
    super.initialize(null);
    backend = null;
  }


  /**
   * {@inheritDoc}
   */
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
    // config manager could have finalized the object right before.
    if (configuration.isEnabled())
    {
      // Read configuration.
      String newBackendID = configuration.getBackend();
      Backend newBackend  = DirectoryServer.getBackend(newBackendID);

      // Get the new config
      if (applyChanges)
      {
        super.initialize(configuration.getWorkflowElementId());
        backend = newBackend;
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
      String workflowElementID,
      Backend backend)
  {
    LocalBackendWorkflowElement localBackend = null;

    // If the requested workflow element does not exist then create one.
    localBackend = registeredLocalBackends.get(workflowElementID);
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
   * {@inheritDoc}
   */
  public void execute(Operation operation)
  {
    switch (operation.getOperationType())
    {
      case BIND:
        LocalBackendBindOperation bindOperation =
             new LocalBackendBindOperation((BindOperation) operation);
        bindOperation.processLocalBind(backend);
        break;

      case SEARCH:
        LocalBackendSearchOperation searchOperation =
             new LocalBackendSearchOperation((SearchOperation) operation);
        searchOperation.processLocalSearch(backend);
        break;

      case ADD:
        LocalBackendAddOperation addOperation =
             new LocalBackendAddOperation((AddOperation) operation);
        addOperation.processLocalAdd(backend);
        break;

      case DELETE:
        LocalBackendDeleteOperation deleteOperation =
             new LocalBackendDeleteOperation((DeleteOperation) operation);
        deleteOperation.processLocalDelete(backend);
        break;

      case MODIFY:
        LocalBackendModifyOperation modifyOperation =
             new LocalBackendModifyOperation((ModifyOperation) operation);
        modifyOperation.processLocalModify(backend);
        break;

      case MODIFY_DN:
        LocalBackendModifyDNOperation modifyDNOperation =
             new LocalBackendModifyDNOperation((ModifyDNOperation) operation);
        modifyDNOperation.processLocalModifyDN(backend);
        break;

      case COMPARE:
        LocalBackendCompareOperation compareOperation =
             new LocalBackendCompareOperation((CompareOperation) operation);
        compareOperation.processLocalCompare(backend);
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
  public static final <O extends Operation,L> void
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
}

