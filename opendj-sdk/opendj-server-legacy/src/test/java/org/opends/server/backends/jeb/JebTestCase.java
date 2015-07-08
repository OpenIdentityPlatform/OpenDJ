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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeMap;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.makeldif.MakeLDIFInputStream;
import org.opends.server.tools.makeldif.TemplateFile;
import org.opends.server.types.DN;
import org.opends.server.types.LDIFImportConfig;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.Entry;
import org.opends.server.util.LDIFReader;
import org.testng.annotations.Test;

/**
 * An abstract base class for all Jeb backend test cases.
 */
@Test(groups = { "precommit", "jeb" }, sequential = true)
public abstract class JebTestCase extends DirectoryServerTestCase {
    private TreeMap<DN,Entry> entryTreeMap = new TreeMap<>();
    private int numEntries;

    /**
     * This method takes an MakeLDIF template and a number of entries to create
     * and adds the created entries into server.
     *
     * @param template MakeLDIF template to use.
     * @param numEntries Number of entries to create and add.
     * @throws Exception if the entries cannot be created or if the add
     *                                   fails.
     */
    protected void
    createLoadEntries(String[] template, int numEntries) throws Exception {
        InternalClientConnection connection =
            InternalClientConnection.getRootConnection();
        String makeLDIFPath =
            System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) +
        File.separator + "resource"+File.separator+"MakeLDIF";
        TemplateFile templateFile =
            new TemplateFile(makeLDIFPath, new Random());
        ArrayList<LocalizableMessage> warnings = new ArrayList<>();
        templateFile.parse(template, warnings);
        MakeLDIFInputStream ldifEntryStream =
            new MakeLDIFInputStream(templateFile);
        LDIFReader reader =
            new LDIFReader(new LDIFImportConfig(ldifEntryStream));
        for(int i =0; i<numEntries;i++) {
            Entry entry = reader.readEntry(false);
            entryTreeMap.put(entry.getName(), entry);
            AddOperation addOperation = connection.processAdd(entry);
            assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
            "Add of this entry was not successful");
        }
        reader.close();
        this.numEntries=numEntries;
    }

    /**This method should be used to remove the entries created in the
     * above loadEntries method.
     * Note that it starts at the last key and works backwards so that the leaf
     * entries are removed first before the top level nodes.
     *
     * @throws Exception if the entries cannot be removed.
     */
    protected void
    removeLoadedEntries() throws Exception {
        InternalClientConnection connection =
            InternalClientConnection.getRootConnection();
        for(int j =0; j < numEntries; j++) {
            DN entryDN = entryTreeMap.lastKey();
            DeleteOperation deleteOperation =
                connection.processDelete(entryDN);
            entryTreeMap.remove(entryDN);
            assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
        }
    }
}
