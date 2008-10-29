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
package org.opends.server.core;


import static org.opends.server.util.StaticUtils.createEntry;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.UtilTestCase;
import org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class tests the 'manual' workflow configuration mode. The 'auto'
 * configuration mode does not require any specific unit test because by
 * default the server is running with the 'auto' mode.
 *
 * With the manual configuration mode, all the network groups, workflows
 * and workflow elements must be defined in the configuration file.
 */
public class WorkflowConfigurationTest extends UtilTestCase
{
  // The base DN of the config backend
  private static final String configBaseDN = ConfigConstants.DN_CONFIG_ROOT;

  // The base DN of the rootDSE backend
  private static final String rootDSEBaseDN = "";

  // The workflow configuration mode attribute
  private static final String workflowModeAttributeType =
      "ds-cfg-workflow-configuration-mode";

  // The suffix attribute in a backend
  private static final String suffixAttributeType =
      "ds-cfg-base-dn";

  // The auto/manual modes
  private static final String workflowConfigModeAuto   = "auto";
  private static final String workflowConfigModeManual = "manual";

  

  //===========================================================================
  //                      B E F O R E    C L A S S
  //===========================================================================

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp()
    throws Exception
  {
    // Start the server so that we can update the configuration and execute
    // some LDAP operations
    TestCaseUtils.startServer();

    // Add the attribute ds-cfg-workflow-configuration-mode with the
    // value 'auto
    initializeConfigurationMode();
    checkBackendIsAccessible("o=test");
  }



  //===========================================================================
  //                    D A T A    P R O V I D E R
  //===========================================================================



  //===========================================================================
  //                           U T I L S
  //===========================================================================

  /**
   * Adds an attribute ds-cfg-workflow-configuration-mode in the entry
   * cn=config. The added value is 'auto'.
   */
  private void initializeConfigurationMode()
      throws Exception
  {
    // Add the ds-cfg-workflow-configuration-mode attribute and set
    // its value to "auto"
    ModifyOperationBasis modifyOperation = getModifyOperation(
        configBaseDN,
        ModificationType.ADD,
        workflowModeAttributeType,
        workflowConfigModeAuto);

    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  
  /**
   * Checks that test backend is accessible as well as config backend
   * and rootDSE backend.
   *
   * @param baseDN  the baseDN of the backend to check
   */
  private void checkBackendIsAccessible(String baseDN)
      throws Exception
  {
    // The config backend and rootDSE backend should always be accessible
    doSearch(rootDSEBaseDN, SearchScope.BASE_OBJECT, ResultCode.SUCCESS);
    doSearch(configBaseDN,  SearchScope.BASE_OBJECT, ResultCode.SUCCESS);

    // The test backend should be accessible
    doSearch(baseDN, SearchScope.BASE_OBJECT, ResultCode.SUCCESS);
  }


  /**
   * Checks that test backend is not accessible while config backend
   * and rootDSE backend are.
   *
   * @param baseDN  the baseDN of the backend to check
   */
  private void checkBackendIsNotAccessible(String baseDN)
      throws Exception
  {
    // The config backend and rootDSE should always be accessible
    doSearch(rootDSEBaseDN, SearchScope.BASE_OBJECT, ResultCode.SUCCESS);
    doSearch(configBaseDN,  SearchScope.BASE_OBJECT, ResultCode.SUCCESS);

    // The test backend should be accessible
    doSearch(baseDN, SearchScope.BASE_OBJECT, ResultCode.NO_SUCH_OBJECT);
  }


  /**
   * Sets the ds-cfg-workflow-configuration-mode attribute to 'auto'
   */
  private void setModeAuto() throws Exception
  {
    ModifyOperationBasis modifyOperation = getModifyOperation(
        configBaseDN,
        ModificationType.REPLACE,
        workflowModeAttributeType,
        workflowConfigModeAuto);

    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
 

  /**
   * Sets the ds-cfg-workflow-configuration-mode attribute to 'auto'
   */
  private void setModeManual() throws Exception
  {
    ModifyOperationBasis modifyOperation = getModifyOperation(
        configBaseDN,
        ModificationType.REPLACE,
        workflowModeAttributeType,
        workflowConfigModeManual);

    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

 
  /**
   * Performs a search on a provided base DN.
   *
   * @param baseDN              the search base DN
   * @param scope               the scope of the search
   * @param expectedResultCode  the expected result code
   *
   * @return the search operation used for the test
   */
  private InternalSearchOperation doSearch(
      String      baseDN,
      SearchScope scope,
      ResultCode  expectedResultCode
      ) throws Exception
  {
    InternalSearchOperation searchOperation = new InternalSearchOperation(
       InternalClientConnection.getRootConnection(),
       InternalClientConnection.nextOperationID(),
       InternalClientConnection.nextMessageID(),
       new ArrayList<Control>(),
       new ASN1OctetString(baseDN),
       scope,
       DereferencePolicy.NEVER_DEREF_ALIASES,
       Integer.MAX_VALUE,
       Integer.MAX_VALUE,
       false,
       LDAPFilter.decode("(objectClass=*)"),
       null, null);

    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), expectedResultCode);
    
    return searchOperation;
  }


  /**
   * Provides a modify operation.
   *
   * @param entryDN        the DN of the entry targeted by the modify operation
   * @param modType        the type of the modification
   * @param attributeType  the type of the attribute to modify
   * @param attributeValue the value of the attribute to modify
   */
  private static ModifyOperationBasis getModifyOperation(
      String           entryDN,
      ModificationType modType,
      String           attributeType,
      String           attributeValue)
  {
    ArrayList<ASN1OctetString> ldapValues = new ArrayList<ASN1OctetString>();
    ldapValues.add(new ASN1OctetString(attributeValue));

    LDAPAttribute ldapAttr = new LDAPAttribute(attributeType, ldapValues);

    ArrayList<RawModification> ldapMods = new ArrayList<RawModification>();
    ldapMods.add(new LDAPModification(modType, ldapAttr));
    
    ModifyOperationBasis modifyOperation = new ModifyOperationBasis(
        InternalClientConnection.getRootConnection(),
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(),
        new ArrayList<Control>(),
        new ASN1OctetString(entryDN),
        ldapMods);

    return modifyOperation;
  }

  
  /**
   * Creates a workflow to handle a local backend. The internal network
   * group is used.
   *
   * @param baseDN     the base DN of the workflow
   * @param backendID  the backend which contains the baseDN
   *
   * @return the newly created workflow
   */
  private WorkflowImpl createWorkflow(String baseDN, String backendID)
      throws Exception
  {
    // Get the backend
    Backend backend = DirectoryServer.getBackend(backendID);
    assertNotNull(backend);
    
    // Create the workflow element that wraps the local backend
    String workflowElementID = baseDN + "#" + backendID;
    LocalBackendWorkflowElement workflowElement =
      LocalBackendWorkflowElement.createAndRegister(workflowElementID, backend);
    
    // Create a workflow and register it with the server
    String workflowID = baseDN + "#" + backendID;
    WorkflowImpl workflowImpl = new WorkflowImpl(
        workflowID, DN.decode(baseDN), workflowElement);
    workflowImpl.register();
    
    // Register the workflow with the internal network group
    NetworkGroup.getInternalNetworkGroup().registerWorkflow(workflowImpl);
    
    return workflowImpl;
  }
  
  
  /**
   * Removes a workflow.
   *
   * @param baseDN     the base DN of the workflow
   * @param backendID  the backend which contains the baseDN
   */
  private void removeWorkflow(String baseDN, String backendID)
      throws Exception
  {
    // Elaborate the workflow ID
    String workflowID = baseDN + "#" + backendID;

    // Deregister the workflow with the internal network group
    NetworkGroup.getInternalNetworkGroup().deregisterWorkflow(workflowID);

    // Deregister the workflow with the server
    Workflow workflow = WorkflowImpl.getWorkflow(workflowID);
    WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
    workflowImpl.deregister();
  }
  
  
  /**
   * Adds a new suffix in a backend.
   *
   * @param baseDN     the DN of the suffix to add
   * @param backendID  the identifier of the backend to which the suffix
   *                   is added
   */
  private void addSuffix(String baseDN, String backendID)
      throws Exception
  {
    // Elaborate the DN of the backend config entry
    String backendDN = elaborateBackendDN(backendID);

    // Add a new suffix in the backend
    ModifyOperationBasis modifyOperation = getModifyOperation(
        backendDN,
        ModificationType.ADD,
        suffixAttributeType,
        baseDN);

    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
  
  
  /**
   * Create a base entry for a new suffix.
   *
   * @param baseDN     the DN of the new base entry
   * @param backendID  the identifier of the backend
   */
  private void createBaseEntry(String baseDN, String backendID)
      throws Exception
  {
    Entry entry = StaticUtils.createEntry(DN.decode(baseDN));

    AddOperationBasis addOperation = new AddOperationBasis(
        InternalClientConnection.getRootConnection(),
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(),
        null,
        entry.getDN(),
        entry.getObjectClasses(),
        entry.getUserAttributes(),
        entry.getOperationalAttributes());

    addOperation.run();
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }
  
  
  /**
   * Removes a new suffix in a backend.
   *
   * @param baseDN     the DN of the suffix to remove
   * @param backendID  the identifier of the backend to which the suffix
   *                   is removed
   *
   * @throw Exception  if the backend does not exist or if the suffix
   *                   already exist in the backend
   */
  private void removeSuffix(String baseDN, String backendID)
      throws Exception
  {
    // Elaborate the DN of the backend config entry
    String backendDN = elaborateBackendDN(backendID);

    // Add a new suffix in the backend
    ModifyOperationBasis modifyOperation = getModifyOperation(
        backendDN,
        ModificationType.DELETE,
        suffixAttributeType,
        baseDN);

    modifyOperation.run();
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
  
  
  /**
   * Elaborates a DN for a backend config entry.
   *
   * @param backendID  the identifier of the backend to retrieve
   */
  private String elaborateBackendDN(String backendID)
  {
    String backendDN =
        "ds-cfg-backend-id=" + backendID + ",cn=Backends,cn=config";
    return backendDN;
  }

  
  /**
   * Initializes a memory-based backend.
   *
   * @param  backendID        the identifier of the backend to create
   * @param  baseDN           the DN of the suffix to create
   * @param  createBaseEntry  indicate whether to automatically create the base
   *                          entry and add it to the backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private static void createBackend(
      String  backendID,
      String  baseDN,
      boolean createBaseEntry
      ) throws Exception
  {
    TestCaseUtils.dsconfig(
        "create-backend",
        "--backend-name", backendID,
        "--type", "memory",
        "--set", "base-dn:" + baseDN,
        "--set", "writability-mode:enabled",
        "--set", "enabled:true");
    
    if (createBaseEntry)
    {
      Backend backend = DirectoryServer.getBackend(backendID);
      Entry e = createEntry(DN.decode(baseDN));
      backend.addEntry(e, null);
    }
  }


  /**
   * Remove a backend.
   *
   * @param  backendID  the identifier of the backend to remove
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private static void removeMemoryBackend(
      String backendID
      ) throws Exception
  {
    TestCaseUtils.dsconfig(
        "delete-backend",
        "--backend-name", backendID);
  }



  //===========================================================================
  //                      T E S T    C A S E S
  //===========================================================================

  /**
   * This test checks the transition from mode 'auto' to 'manual' and
   * 'manual' back to 'auto'. In this test there is no configuration for
   * network group, workflow and workflow element.
   */
  @Test
  public void transitionAutoManualAuto() throws Exception
  {
    // Settings
    String testBaseDN = "o=test";

    // The ds-cfg-workflow-configuration-mode attribute value is "auto"
    // (default value), let's put the same value again. Putting the same
    // value should have no impact and we should be able to perform a search
    // on the test backend.
    setModeAuto();
    checkBackendIsAccessible(testBaseDN);

    // Change the ds-cfg-workflow-configuration-mode attribute value
    // to "manual". The workflows should be fully reconfigured. But as
    // there is no configuration for the workflows, only cn=config and
    // rootDSE should be accessible.
    setModeManual();
    checkBackendIsNotAccessible(testBaseDN);

    // Change the ds-cfg-workflow-configuration-mode attribute value
    // back to "auto". All the local backends should be accessible again.
    setModeAuto();
    checkBackendIsAccessible(testBaseDN);
  }

 
  /**
   * This test checks the basic operation routing when configuration
   * mode is 'manual'. Few workflows are configured for the test.
   */
  @Test
  public void basicRoutingManualMode() throws Exception
  {
    // Settings
    String testBaseDN    = "o=test";
    String testBackendID = "test";

    // Workflow configuration mode is auto, so test backend should
    // be accessible
    setModeAuto();
    checkBackendIsAccessible(testBaseDN);

    // Set the workflow configuration mode to manual. In this mode
    // no there is no workflow by default (but the config and rootDSE
    // workflows) so seaarches on the test backend should fail.
    setModeManual();
    checkBackendIsNotAccessible(testBaseDN);
    
    // Create a workflow to handle o=test backend then check that test
    // backend is now accessible
    createWorkflow(testBaseDN, testBackendID);
    checkBackendIsAccessible(testBaseDN);
    
    // Change workflow configuration mode back to auto and check that
    // test backend is still accessible
    setModeAuto();
    checkBackendIsAccessible(testBaseDN);
  }

 
  /**
   * This test checks the add/remove of suffix in a backend in manual
   * configuration mode.
   */
  @Test
  public void addRemoveSuffix() throws Exception
  {
    // Settings
    String testBaseDN2    = "o=addRemoveSuffix_1";
    String testBaseDN3    = "o=addRemoveSuffix_2";
    String testBackendID2 = "userRoot";

    // make sure we are in auto mode and check that the new suffixes are
    // not already defined on the server.
    setModeAuto();
    checkBackendIsNotAccessible(testBaseDN2);
    checkBackendIsNotAccessible(testBaseDN3);

    // Add a new suffix to the test backend and check that the new
    // suffix is accessible (we are in auto mode).
    addSuffix(testBaseDN2, testBackendID2);
    createBaseEntry(testBaseDN2, testBackendID2);
    checkBackendIsAccessible(testBaseDN2);

    // Remove the suffix and check that the removed suffix is no
    // more accessible.
    removeSuffix(testBaseDN2, testBackendID2);
    checkBackendIsNotAccessible(testBaseDN2);

    // Now move to the manual mode.
    setModeManual();

    // Add a new suffix and configure a workflow to route operation
    // to this new suffix, then check that the new suffix is accessible.
    // Note that before we can create a base entry we need to configure
    // first a workflow to route the ADD operation to the right suffix.
    // This need not be to be done in auto mode because with the auto mode
    // the workflow is automatically created when a new suffix is added.
    addSuffix(testBaseDN3, testBackendID2);
    createWorkflow(testBaseDN3, testBackendID2);
    createBaseEntry(testBaseDN3, testBackendID2);
    checkBackendIsAccessible(testBaseDN3);

    // Finally remove the new workflow and suffix and check that the suffix
    // is no more accessible.
    removeWorkflow(testBaseDN3, testBackendID2);
    removeSuffix(testBaseDN3, testBackendID2);
    checkBackendIsNotAccessible(testBaseDN3);    
    
    // Back to the original configuration mode
    setModeAuto();
  }

 
  /**
   * This test checks the add/remove of a backend in manual configuration
   * mode.
   */
  @Test
  public void addRemoveBackend() throws Exception
  {
    // Local settings
    String backendID1 = "addRemoveBackend_1";
    String backendID2 = "addRemoveBackend_2";
    String baseDN1    = "o=addRemoveBackendBaseDN_1";
    String baseDN2    = "o=addRemoveBackendBaseDN_2";
    
    // Make sure we are in auto mode and check the suffix is not accessible
    setModeAuto();
    checkBackendIsNotAccessible(baseDN1);
    
    // Create a backend and check that the base entry is accessible.
    createBackend(backendID1, baseDN1, true);
    checkBackendIsAccessible(baseDN1);
    
    // Remove the backend and check that the suffix is no more accessible.
    removeMemoryBackend(backendID1);
    checkBackendIsNotAccessible(baseDN1);
    
    // Now move to the manual mode
    setModeManual();
    checkBackendIsNotAccessible(baseDN2);
    
    // Create a backend and create a workflow to route operations to that
    // new backend. Then check that the base entry is accessible.
    createBackend(backendID2, baseDN2, true);
    createWorkflow(baseDN2, backendID2);
    checkBackendIsAccessible(baseDN2);
    
    // Remove the workflow and the backend and check that the base entry
    // is no more accessible.
    removeWorkflow(baseDN2, backendID2);
    removeMemoryBackend(backendID2);
    checkBackendIsNotAccessible(baseDN2);

    // Back to the original configuration mode
    setModeAuto();
  }
  
  
  /**
   * This test checks the creation and utilization of network group
   * in the route process.
   */
  @Test
  public void useNetworkGroup() throws Exception
  {
    // Local settings
    String backendID = "test";
    String baseDN    = "o=test";

    // Move to the manual mode
    setModeManual();
    
    // Create a route for o=test suffix in the internal network group.
    // Search on o=test should succeed.
    WorkflowImpl workflowImpl = createWorkflow(baseDN, backendID);
    InternalSearchOperation searchOperation =
      doSearch(baseDN, SearchScope.BASE_OBJECT, ResultCode.SUCCESS);
    
    // Create a network group and store it in the client connection.
    // As the network group is empty, all searches should fail with a
    // no such object result code.
    String networkGroupID = "useNetworkGroupID";
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    ClientConnection clientConnection = searchOperation.getClientConnection();
    clientConnection.setNetworkGroup(networkGroup);
    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    
    // Now register the o=test workflow and search again. The search
    // should succeed.
    networkGroup.registerWorkflow(workflowImpl);
    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    
    // Put back the internal network group in the client conenction
    // and check that searches are still working.
    clientConnection.setNetworkGroup(NetworkGroup.getInternalNetworkGroup());
    searchOperation.run();
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    
    // Back to the original configuration mode
    setModeAuto();
  }
}
