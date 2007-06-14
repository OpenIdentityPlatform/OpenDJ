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

package org.opends.quicksetup;

import org.testng.annotations.*;

/**
 * Installation Tester.
 *
 */
@Test(groups = { "slow" })
public class InstallationTest extends QuickSetupTestCase
{
    Installation installation;

    @BeforeClass
    public void setUp() throws Exception {
      Utils.extractServer();
      installation = new Installation(Utils.getQuickSetupTestServerRootDir());
    }

    @Test
    public void testValidateRootDirectory()
    {
      Installation.validateRootDirectory(Utils.getQuickSetupTestServerRootDir());
    }

//
//    @Test
//    public void testGetRootDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetRootDirectory not implemented.";
//    }
//
//    @Test
//    public void testSetRootDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testSetRootDirectory not implemented.";
//    }
//
//    @Test
//    public void testIsValid()
//    {
//        //TODO: Test goes here...
//        assert false : "testIsValid not implemented.";
//    }
//
//    @Test
//    public void testGetInvalidityReason()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetInvalidityReason not implemented.";
//    }
//
//    @Test
//    public void testGetCurrentConfiguration()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetCurrentConfiguration not implemented.";
//    }
//
//    @Test
//    public void testGetBaseConfiguration()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBaseConfiguration not implemented.";
//    }
//
//    @Test
//    public void testGetStatus()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetStatus not implemented.";
//    }
//
//    @Test
//    public void testGetLibrariesDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetLibrariesDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetSchemaConcatFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetSchemaConcatFile not implemented.";
//    }
//
//    @Test
//    public void testGetBaseSchemaFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBaseSchemaFile not implemented.";
//    }
//
//    @Test
//    public void testGetBaseConfigurationFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBaseConfigurationFile not implemented.";
//    }
//
//    @Test
//    public void testGetSvnRev()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetSvnRev not implemented.";
//    }
//
//    @Test
//    public void testGetCurrentConfigurationFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetCurrentConfigurationFile not implemented.";
//    }
//
//    @Test
//    public void testGetBinariesDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBinariesDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetDatabasesDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetDatabasesDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetBackupDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBackupDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetConfigurationDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetConfigurationDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetLogsDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetLogsDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetLocksDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetLocksDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetTemporaryDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetTemporaryDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetHistoryDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetHistoryDirectory not implemented.";
//    }
//
//    @Test
//    public void testCreateHistoryBackupDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testCreateHistoryBackupDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetHistoryLogFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetHistoryLogFile not implemented.";
//    }
//
//    @Test
//    public void testGetConfigurationUpgradeDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetConfigurationUpgradeDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetTemporaryUpgradeDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetTemporaryUpgradeDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetCommandFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetCommandFile not implemented.";
//    }
//
//    @Test
//    public void testGetServerStartCommandFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetServerStartCommandFile not implemented.";
//    }
//
//    @Test
//    public void testGetServerStopCommandFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetServerStopCommandFile not implemented.";
//    }
//
//    @Test
//    public void testGetLdifDirectory()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetLdifDirectory not implemented.";
//    }
//
//    @Test
//    public void testGetQuicksetupJarFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetQuicksetupJarFile not implemented.";
//    }
//
//    @Test
//    public void testGetOpenDSJarFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetOpenDSJarFile not implemented.";
//    }
//
//    @Test
//    public void testGetUninstallBatFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetUninstallBatFile not implemented.";
//    }
//
//    @Test
//    public void testGetStatusPanelCommandFile()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetStatusPanelCommandFile not implemented.";
//    }
//
//    @Test
//    public void testGetBuildInformation()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBuildInformation not implemented.";
//    }
//
//    @Test
//    public void testGetBuildInformation1()
//    {
//        //TODO: Test goes here...
//        assert false : "testGetBuildInformation1 not implemented.";
//    }
//
//    @Test
//    public void testToString()
//    {
//        //TODO: Test goes here...
//        assert false : "testToString not implemented.";
//    }

}
