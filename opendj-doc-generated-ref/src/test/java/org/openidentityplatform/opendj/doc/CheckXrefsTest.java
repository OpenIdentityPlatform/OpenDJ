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
import org.forgerock.testng.ForgeRockTestCase;
import org.jruby.embed.ScriptingContainer;
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
        } finally {
            ruby.terminate();
        }
    }
}
