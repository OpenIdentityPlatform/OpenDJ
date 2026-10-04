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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_TOOL_INVALID_CONTROL_STRING;
import static com.forgerock.opendj.util.OperatingSystem.isWindows;
import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.StringArgument;

/** Tests the parsing of the {@code -J/--control} argument by {@link Utils#readControls(StringArgument)}. */
@Test
public final class ControlArgumentTestCase extends ForgeRockTestCase {

    @DataProvider
    public Object[][] validControls() {
        return new Object[][] {
            { "1.2.3.4", "1.2.3.4", false, null },
            { "1.2.3.4:true", "1.2.3.4", true, null },
            { "1.2.3.4:FALSE:value", "1.2.3.4", false, "value" },
            { "1.2.3.4:true:", "1.2.3.4", true, "" },
            { "pwpolicy:true", "1.3.6.1.4.1.42.2.27.8.5.1", true, null },
            // Everything after the second colon is the value, colons included.
            { "2.16.840.1.113730.3.4.18:true:dn:uid=bjensen,ou=People,dc=example,dc=com",
              "2.16.840.1.113730.3.4.18", true, "dn:uid=bjensen,ou=People,dc=example,dc=com" },
            { "2.16.840.1.113730.3.4.18:true:u:bjensen", "2.16.840.1.113730.3.4.18", true, "u:bjensen" },
            { "1.2.3.4:false:urn:example:value", "1.2.3.4", false, "urn:example:value" },
            { "1.2.3.4:false:ldap://host:1389/dc=example", "1.2.3.4", false, "ldap://host:1389/dc=example" },
            // The base64 form decodes to a value that can itself hold colons.
            { "1.2.3.4:true::" + base64("dn:uid=bjensen"), "1.2.3.4", true, "dn:uid=bjensen" },
            { "1.2.3.4:true::", "1.2.3.4", true, "" },
        };
    }

    @Test(dataProvider = "validControls")
    public void testValidControl(final String argument, final String oid, final boolean critical,
            final String value) throws Exception {
        final Control control = readSingleControl(argument);
        assertThat(control.getOID()).isEqualTo(oid);
        assertThat(control.isCritical()).isEqualTo(critical);
        if (value == null) {
            assertThat(control.hasValue()).isFalse();
        } else {
            assertThat(control.hasValue()).isTrue();
            assertThat(control.getValue().toString()).isEqualTo(value);
        }
    }

    @Test
    public void testValueReadFromFileWhosePathHoldsAColon() throws Exception {
        // On Windows the drive letter puts a colon in every absolute path; elsewhere the file name carries one.
        final File dir = Files.createTempDirectory("control-value").toFile();
        final File file = new File(dir, isWindows() ? "control-value.ber" : "control:value.ber");
        try {
            final byte[] bytes = { 0x04, 0x03, 'a', ':', 'b' };
            Files.write(file.toPath(), bytes);

            final Control control = readSingleControl("1.2.3.4:true:<" + file.getAbsolutePath());
            assertThat(control.getOID()).isEqualTo("1.2.3.4");
            assertThat(control.isCritical()).isTrue();
            assertThat(control.getValue()).isEqualTo(ByteString.wrap(bytes));
        } finally {
            file.delete();
            dir.delete();
        }
    }

    @DataProvider
    public Object[][] invalidControls() {
        final String missingFile = new File(System.getProperty("java.io.tmpdir"), "no-such-dir-1156/value.ber")
                .getAbsolutePath();
        return new Object[][] {
            { "1.2.3.4:invalidcriticality" },
            { "1.2.3.4:invalidcriticality:value" },
            { "1.2.3.4:true:<" + missingFile },
            { "1.2.3.4:true::not*base64" },
        };
    }

    @Test(dataProvider = "invalidControls")
    public void testInvalidControlIsReportedAsAnInvalidControlString(final String argument) throws Exception {
        try {
            readControls(argument);
            fail("Expected the control '" + argument + "' to be rejected");
        } catch (final LDAPToolException e) {
            assertThat(e.getResultCode()).isEqualTo(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            assertThat(e.getMessage()).isEqualTo(ERR_TOOL_INVALID_CONTROL_STRING.get(argument).toString());
        }
    }

    private static Control readSingleControl(final String argument) throws Exception {
        final List<Control> controls = readControls(argument);
        assertThat(controls).hasSize(1);
        return controls.get(0);
    }

    private static List<Control> readControls(final String argument) throws Exception {
        final StringArgument controlArg = CommonArguments.controlArgument();
        controlArg.addValue(argument);
        controlArg.setPresent(true);
        return Utils.readControls(controlArg);
    }

    private static String base64(final String value) {
        return ByteString.valueOfBytes(value.getBytes(StandardCharsets.UTF_8)).toBase64String();
    }
}
