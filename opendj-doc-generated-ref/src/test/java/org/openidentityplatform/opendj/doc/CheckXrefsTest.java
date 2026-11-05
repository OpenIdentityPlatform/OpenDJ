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
package org.openidentityplatform.opendj.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.forgerock.testng.ForgeRockTestCase;
import org.jruby.embed.ScriptingContainer;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Exercises the resolver and its CLI with actual AsciiDoc fixtures. */
@Test
public class CheckXrefsTest extends ForgeRockTestCase {
    @Test
    public void validatesCrossGuideReferences() {
        final ScriptingContainer ruby = new ScriptingContainer();
        try {
            ruby.runScriptlet("require 'rubygems'; Gem.use_paths('uri:classloader:/')");
            ruby.put("test_file", new File(System.getProperty("basedir", "."),
                    "src/test/resources/asciidoc/check-xrefs-test.rb").getAbsolutePath());
            ruby.runScriptlet("require test_file");
            assertThat(ruby.runScriptlet("check_xrefs_test").toString()).endsWith("xref regression checks passed");
            assertThat(ruby.runScriptlet("check_xrefs_publication_test").toString()).endsWith("publication checks passed");
        } finally {
            ruby.terminate();
        }
    }

    @DataProvider(name = "cliCases")
    public Object[][] cliCases() {
        return new Object[][] {
            { "= Valid\n", "*.adoc", 0, "Checked 0 file xrefs" },
            { "= Broken\n\nxref:missing.adoc[]\n", "*.adoc", 1, "missing.adoc" },
            { "= Valid\n", "absent/*.adoc", 2, "no input files match" }
        };
    }

    /** A successful JVM launch must not hide a nonzero checker exit from Maven. */
    @Test(dataProvider = "cliCases")
    public void returnsCliExitStatus(String source, String pattern, int status, String diagnostic) throws Exception {
        final Path root = Files.createTempDirectory("xref-cli-");
        final Path page = root.resolve("page.adoc");
        final Path output = root.resolve("output.log");
        Process process = null;
        try {
            Files.write(page, source.getBytes(StandardCharsets.UTF_8));
            final String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            process = new ProcessBuilder(
                    new File(System.getProperty("java.home"), "bin/" + executable).getAbsolutePath(),
                    "-Xmx256m", "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    "org.jruby.Main",
                    new File(System.getProperty("basedir", "."),
                            "src/main/resources/asciidoc/check-xrefs.rb").getAbsolutePath(),
                    root.toString(), pattern)
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("checker process finished").isTrue();
            final String report = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(report).isEqualTo(status);
            assertThat(report).contains(diagnostic);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
            Files.deleteIfExists(page);
            Files.delete(root);
        }
    }
}
