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
 *  Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS;
import static com.forgerock.opendj.ldap.tools.LDIFDiff.DIFFERENCES_FOUND;
import static com.forgerock.opendj.ldap.tools.LDIFDiff.NO_DIFFERENCES_FOUND;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_LDIFDIFF_MULTIPLE_USES_OF_STDIN;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.calcChecksum;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.toDataProviderArray;
import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of test cases for the LDIFDiff tool. */
@Test
public class LDIFDiffTestCase extends ToolsTestCase {

    private static final String LDIF_RESOURCES_PATH =
            Paths.get("src", "test", "resources", "ldifdiff").toAbsolutePath().toString();

    /**
     * Retrieves sets of invalid arguments that may not be used to initialize the LDAPSearch tool.
     *
     * @return Sets of invalid arguments that may not be used to initialize the LDAPSearch tool.
     */
    @DataProvider
    public Object[][] invalidArgs() {
        final List<List<String>> argLists = new ArrayList<>();
        final List<LocalizableMessage> reasonList = new ArrayList<>();

        argLists.add(args());
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(2));

        addValueNeededShortArgs(argLists, reasonList, "o");
        addValueNeededLongArgs(argLists, reasonList, "outputLdif");

        return toDataProviderArray(argLists, reasonList);
    }

    /**
     * Retrieves the names of the files that should be used when testing the
     * ldifdiff tool.  Each element of the outer array should be an array
     * containing the following elements:
     * <OL>
     * <LI>The path to the source LDIF file</LI>
     * <LI>The path to the target LDIF file</LI>
     * <LI>The path to the diff file, or {@code null} if the diff is supposed
     * to fail</LI>
     * </OL>
     */
    @DataProvider
    public Object[][] testDataNoDiffs() {
        return new Object[][] {
            // Both files are empty.
            { "source-empty.ldif", "target-empty.ldif" },

            // Both files are the single-entry source.
            { "source-singleentry.ldif", "source-singleentry.ldif" },

            // Both files are the single-entry target.
            { "target-singleentry.ldif", "target-singleentry.ldif" },

            // Both files are the multiple-entry source.
            { "source-multipleentries.ldif", "source-multipleentries.ldif" },

            // Both files are the multiple-entry target.
            { "target-multipleentries.ldif", "target-multipleentries.ldif" },
        };
    }

    @Test(dataProvider = "testDataNoDiffs")
    public void testLdifDiffWithoutDiffs(final String sourceFileName, final String targetFileName) throws Exception {
        testLdifDiff(sourceFileName, targetFileName, "diff-nochanges.ldif");
    }

    /**
     * Retrieves the names of the files that should be used when testing the
     * ldifdiff tool.  Each element of the outer array should be an array
     * containing the following elements:
     * <OL>
     * <LI>The path to the source LDIF file</LI>
     * <LI>The path to the target LDIF file</LI>
     * <LI>The path to the diff file, or {@code null} if the diff is supposed
     * to fail</LI>
     * </OL>
     */
    @DataProvider
    public Object[][] testDataWithDiffs() {
        return new Object[][] {
            // The source is empty but the target has a single entry.
            { "source-empty.ldif", "target-singleentry.ldif", "diff-emptytosingle.ldif" },

            // The source has a single entry but the target is empty.
            { "source-singleentry.ldif", "target-empty.ldif", "diff-singletoempty.ldif" },

            // Make a change to only a single entry in the source->target direction.
            { "source-singleentry.ldif", "target-singleentry.ldif", "diff-singleentry.ldif" },

            // Make a change to only a single entry in the target->source direction.
            { "target-singleentry.ldif", "source-singleentry.ldif", "diff-singleentry-reverse.ldif" },

            // Make changes to multiple entries in the source->target direction.
            { "source-multipleentries.ldif", "target-multipleentries.ldif", "diff-multipleentries.ldif" },

            // Make changes to multiple entries in the target->source direction.
            { "target-multipleentries.ldif", "source-multipleentries.ldif", "diff-multipleentries-reverse.ldif" },

            // Go from one entry to multiple in the source->target direction.
            { "source-singleentry.ldif", "target-multipleentries.ldif", "diff-singletomultiple.ldif" },

            // Go from one entry to multiple in the target->source direction.
            { "target-singleentry.ldif", "source-multipleentries.ldif", "diff-singletomultiple-reverse.ldif" },

            // Go from multiple entries to one in the source->target direction.
            { "source-multipleentries.ldif", "target-singleentry.ldif", "diff-multipletosingle.ldif" },

            // Go from multiple entries to one in the target->source direction.
            { "target-multipleentries.ldif", "source-singleentry.ldif", "diff-multipletosingle-reverse.ldif" }
        };
    }

    @Test(dataProvider = "testDataWithDiffs")
    public void testReconstructWithLdifModify(final String sourceFileName,
                                              final String targetFileName,
                                              final String expectedResultFileName) throws Exception {
        final String ldifDiffResultFilePath = testLdifDiff(sourceFileName, targetFileName, expectedResultFileName);

        final File ldifModifyResultFile = new File(LDIF_RESOURCES_PATH, "ldifDiffAndModifyTestCase.ldif");
        ldifModifyResultFile.deleteOnExit();

        final int ldifModifyRes = Utils.runTool(new LDIFModify(outStream, errStream),
                                                "-o", ldifModifyResultFile.getAbsolutePath(),
                                                absolutePath(sourceFileName),
                                                ldifDiffResultFilePath);
        assertThat(ldifModifyRes).isEqualTo(ResultCode.SUCCESS.intValue());

        testLdifDiff(targetFileName, "ldifDiffAndModifyTestCase.ldif", "diff-nochanges.ldif");
    }

    private String testLdifDiff(final String sourceFileName,
                                final String targetFileName,
                                final String expectedResultFileName) throws Exception {
        final String ldifDiffOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool("-o", ldifDiffOutputFilePath,
                                absolutePath(sourceFileName),
                                absolutePath(targetFileName));

        final boolean expectNoDiffs = expectedResultFileName.equals("diff-nochanges.ldif");
        assertThat(res).isEqualTo(expectNoDiffs ? NO_DIFFERENCES_FOUND
                                                : DIFFERENCES_FOUND);
        assertThat(calcChecksum(ldifDiffOutputFilePath)).isEqualTo(
                   calcChecksum(absolutePath(expectedResultFileName)));

        return ldifDiffOutputFilePath;
    }

    @Test
    public void testLdifDiffWithoutWrapping() throws Exception {
        final String ldifDiffOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool("-o", ldifDiffOutputFilePath,
                                "-t", "0",
                                absolutePath("source-singleentry.ldif"),
                                absolutePath("target-multipleentries.ldif"));
        assertThat(res).isEqualTo(DIFFERENCES_FOUND);
        assertThat(calcChecksum(ldifDiffOutputFilePath)).isEqualTo(
                   calcChecksum(absolutePath("diff-singletomultiple-no-wrapping.ldif")));
    }

    @Test
    public void testLdifDiffWithBothSourceAndTargetOnStdin() throws Exception {
        final String ldifDiffOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool("-o", ldifDiffOutputFilePath,
                                "--",
                                "-",
                                "-");
        assertThat(res).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(ERR_LDIFDIFF_MULTIPLE_USES_OF_STDIN.get().toString());
    }

    private String absolutePath(final String fileName) {
        return LDIF_RESOURCES_PATH + File.separator + fileName;
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDIFDiff(outStream, errStream);
    }
}

