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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS;
import static com.forgerock.opendj.ldap.CoreMessages.ERR_LDIF_NO_DN;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_LDIFMODIFY_MULTIPLE_USES_OF_STDIN;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededLongArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.addValueNeededShortArgs;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.args;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.buildArgs;
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

@SuppressWarnings("javadoc")
@Test
public class LDIFModifyTestCase extends ToolsTestCase {

    private static final String LDIF_RESOURCES_PATH =
            Paths.get("src", "test", "resources", "ldifmodify").toAbsolutePath().toString();

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
        reasonList.add(ERR_ARGPARSER_TOO_FEW_TRAILING_ARGUMENTS.get(1));

        addValueNeededShortArgs(argLists, reasonList, "o");
        addValueNeededLongArgs(argLists, reasonList, "outputLdif");

        return toDataProviderArray(argLists, reasonList);
    }

    @Test
    public void testSimpleLdifModifyWithMultipleChangeFiles() throws Exception {
        final String ldifModifyOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool(buildArgs().add("-o", ldifModifyOutputFilePath)
                                           .add(absolutePath("source.ldif"))
                                           .add(absolutePath("modifications_part_1.ldif"))
                                           .add(absolutePath("modifications_part_2.ldif"))
                                           .add(absolutePath("modifications_part_3.ldif"))
                                           .toArray());
        assertThat(res).isEqualTo(ResultCode.SUCCESS.intValue());
        assertThat(calcChecksum(absolutePath("expected-no-wrapping.ldif"))).isEqualTo(
                   calcChecksum(ldifModifyOutputFilePath));
    }

    @Test
    public void testSimpleLdifModifyWithWrapping() throws Exception {
        testSimpleLdifModify(ResultCode.SUCCESS, "modifications.ldif", "expected.ldif", "-t", "80");
    }

    @Test
    public void testLdifModifyWithBothSourceAndChangesOnStdin() throws Exception {
        final String ldifDiffOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool("-o", ldifDiffOutputFilePath,
                                "--",
                                "-",
                                "-");
        assertThat(res).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
        assertThat(errOnSingleLine()).contains(ERR_LDIFMODIFY_MULTIPLE_USES_OF_STDIN.get().toString());
    }

    @Test
    public void testSimpleLdifModifyWithoutWrapping() throws Exception {
        testSimpleLdifModify(ResultCode.SUCCESS, "modifications.ldif", "expected-no-wrapping.ldif", "-t", "0");
    }

    @Test
    public void testSimpleLdifModifyWithErrorInModifications() throws Exception {
        testSimpleLdifModify(ResultCode.CLIENT_SIDE_LOCAL_ERROR, "modifications-with-error.ldif", "Not Applicable");
    }

    @Test
    public void testSimpleLdifModifyWithContinueOnError() throws Exception {
        testSimpleLdifModify(ResultCode.SUCCESS,
                             "modifications-with-error.ldif",
                             "expected-continue-on-error.ldif",
                             "-c");
    }

    @Test
    public void testSimpleLdifModifyWithIncorrectLdifModifications() throws Exception {
        testSimpleLdifModify(ResultCode.CLIENT_SIDE_LOCAL_ERROR, "modifications-invalid.ldif", "Not Applicable");
        assertThat(errOnSingleLine()).contains(ERR_LDIF_NO_DN.get(1, "changetype: modify").toString());
        assertThat(out.toString()).isEmpty();
    }

    private void testSimpleLdifModify(final ResultCode expectedRC,
                                      final String modificationsFileName,
                                      final String expectedOutputFileName,
                                      final String... additionalArgs) throws Exception {
        final String ldifModifyOutputFilePath = ToolsTestUtils.createTempFile();
        final int res = runTool(buildArgs().add("-o", ldifModifyOutputFilePath)
                                           .addAll(additionalArgs)
                                           .add(absolutePath("source.ldif"))
                                           .add(absolutePath(modificationsFileName))
                                           .toArray());
        assertThat(res).isEqualTo(expectedRC.intValue());
        if (expectedRC == ResultCode.SUCCESS) {
            assertThat(calcChecksum(absolutePath(expectedOutputFileName))).isEqualTo(
                       calcChecksum(ldifModifyOutputFilePath));
        }
    }

    private String absolutePath(final String fileName) {
        return LDIF_RESOURCES_PATH + File.separator + fileName;
    }

    @Override
    ToolConsoleApplication createInstance() {
        return new LDIFModify(outStream, errStream);
    }
}
