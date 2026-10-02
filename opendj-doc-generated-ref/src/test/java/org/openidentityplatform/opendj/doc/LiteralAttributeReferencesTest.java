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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Renders pages with the configuration of the check-attribute-references execution of this module's
 * pom - its extensions, its attributes and its failIf - and checks which of them would fail the build.
 * Reading the configuration from the pom keeps the execution itself under test, not only
 * literal-attribute-references.rb.
 */
@Test
public class LiteralAttributeReferencesTest extends ForgeRockTestCase {
    private static final String EXECUTION = "check-attribute-references";
    /** A page that unsets an attribute and then names it in a listing, kept among the sources. */
    private static final String UNSET_PAGE = "= Title\n:u: 1\n\n== Section\n:u!:\n\n----\nx-{u}\n----\n";

    private final List<LogRecord> records = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private Asciidoctor asciidoctor;
    private String backend;
    private boolean sourcemap;
    private Severity failSeverity;
    private String failText;

    @BeforeClass
    public void readExecution() throws Exception {
        final File module = new File(System.getProperty("basedir", "."));
        // The pre-processed sources sit under the build directory. Braces and brackets in its path
        // must not turn it into a glob pattern.
        final Path buildDirectory = Files.createTempDirectory(
                Files.createDirectories(new File(module, "target").toPath()), "attribute-check-{1}[1]-");
        final Path sources = Files.createDirectories(buildDirectory.resolve("asciidoc/source/other-guide"));
        Files.write(sources.resolve("chap-other.adoc"), ":elsewhere: 1\n".getBytes(StandardCharsets.UTF_8));
        // The build renders the pages of the directory it collects the entries from, so a page is among them.
        Files.write(sources.resolve("chap-unset.adoc"), UNSET_PAGE.getBytes(StandardCharsets.UTF_8));

        final XPath xpath = XPathFactory.newInstance().newXPath();
        final Element configuration = (Element) xpath.evaluate(
                "//execution[id='" + EXECUTION + "']/configuration",
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(module, "pom.xml")),
                XPathConstants.NODE);
        assertThat(configuration).as("configuration of the " + EXECUTION + " execution").isNotNull();
        // The fixture stands for both directories, which holds only while they are one.
        assertThat(xpath.evaluate("sourceDirectory", configuration).trim())
                .as("the check renders the directory whose attribute entries it collects")
                .isEqualTo(xpath.evaluate("attributes/literal-attribute-sources", configuration).trim());

        asciidoctor = Asciidoctor.Factory.create();
        asciidoctor.registerLogHandler(records::add);
        final NodeList requires = (NodeList) xpath.evaluate("requires/require", configuration, XPathConstants.NODESET);
        for (int i = 0; i < requires.getLength(); i++) {
            asciidoctor.requireLibrary(requires.item(i).getTextContent().trim()
                    .replace("${project.basedir}", module.getAbsolutePath()));
        }
        final NodeList entries = (NodeList) xpath.evaluate("attributes/*", configuration, XPathConstants.NODESET);
        for (int i = 0; i < entries.getLength(); i++) {
            final Node entry = entries.item(i);
            attributes.put(entry.getNodeName(), entry.getTextContent().trim()
                    .replace("${project.build.directory}", buildDirectory.toString()));
        }
        backend = xpath.evaluate("backend", configuration);
        sourcemap = Boolean.parseBoolean(xpath.evaluate("sourcemap", configuration));
        failSeverity = Severity.valueOf(xpath.evaluate("logHandler/failIf/severity", configuration));
        failText = xpath.evaluate("logHandler/failIf/containsText", configuration);
    }

    @AfterClass(alwaysRun = true)
    public void shutdown() {
        if (asciidoctor != null) {
            asciidoctor.shutdown();
        }
    }

    /** Returns the messages that make the execution fail the build on this page. */
    private List<String> failures(final String page) {
        records.clear();
        asciidoctor.convert(page, Options.builder()
                .safe(SafeMode.UNSAFE)
                .backend(backend)
                .sourcemap(sourcemap)
                .attributes(new LinkedHashMap<>(attributes))
                .toFile(false)
                .build());
        return records.stream()
                .filter(r -> r.getSeverity().ordinal() >= failSeverity.ordinal() && r.getMessage().contains(failText))
                .map(LogRecord::getMessage)
                .collect(Collectors.toList());
    }

    @DataProvider
    public Object[][] failingPages() {
        return new Object[][] {
            { ":v: 1\n\n----\nunzip x-{v}.zip\n----\n",
              "attribute {v} is published as literal text: the listing block does not substitute attributes, "
                      + "add subs=\"+attributes\"" },
            { ":v: 1\n\n literal x-{v}\n", "attribute {v} is published as literal text: the literal block" },
            { ":v: 1\n\n++++\n<p>{v}</p>\n++++\n", "attribute {v} is published as literal text: the pass block" },
            { ":v: 1\n\n|===\nl|cell x-{v}\n|===\n",
              "attribute {v} is published as literal text: the literal table cell does not substitute attributes, "
                      + "use an a| cell with a listing that has subs=\"+attributes\"" },
            { ":v: 1\n\n[cols=\"1l\"]\n|===\n|cell x-{v}\n|===\n", "the literal table cell" },
            { ":v: 1\n\n|===\na|\n----\nx-{v}\n----\n|===\n", "attribute {v} is published as literal text: the listing" },
            { ":v: 1\n\n----\nunzip x-\\{v}.zip\n----\n", "\\{v} is published with its backslash: the listing block" },
            { "----\nPATH{nbsp}x\n----\n", "attribute {nbsp} is published as literal text" },
            // A body entry counts from where it stands, not only a header one.
            { "= Title\n\n== Section\n:late: 1\n\n----\nx-{late}\n----\n", "attribute {late} is published as literal text" },
            // A page that neither defines nor substitutes an attribute another page defines.
            { "----\nx-{elsewhere}\n----\n", "attribute {elsewhere} is published as literal text" },
            // An entry of the page itself counts, even where the page has unset the attribute.
            { UNSET_PAGE, "attribute {u} is published as literal text" },
            { "tool {undefinedthing}\n", "undefinedthing" },
            // The converter starts from the compat mode of the header, not from that of the last entry.
            { "= Title\n\n`{undefinedthing}`\n\n:compat-mode:\n\ny\n", "undefinedthing" },
        };
    }

    @Test(dataProvider = "failingPages")
    public void pageFailsTheBuild(final String page, final String message) {
        assertThat(failures(page)).as(page).anySatisfy(failure -> assertThat(failure).contains(message));
    }

    @DataProvider
    public Object[][] passingPages() {
        return new Object[][] {
            { ":v: 1\n\n[subs=\"+attributes\"]\n----\nunzip x-{v}.zip\n----\n" },
            { ":v: 1\n\n[subs=\"+attributes\"]\n----\nunzip x-\\{v}.zip\n----\n" },
            { ":v: 1\n\nunzip x-{v}.zip and \\{v}\n" },
            { ":v: 1\n\n|===\na|\n[subs=\"+attributes\"]\n----\nx-{v}\n----\n|===\n" },
            // Braces that are no attribute anywhere are meant literally.
            { "----\nuserPassword: {SSHA}abc\ncn: {cn}\n----\n" },
            { "|===\nl|{SSHA}abc\n|===\n" },
            // A built-in attribute that no entry sets is no longer one where the body has unset it.
            { "= Title\n\n== Section\n:figure-caption!:\n\n----\nx-{figure-caption}\n----\n" },
            // The converter starts again from the header: a reference before a body unset still resolves.
            { "= Title\n:v: 1\n\nx {v}\n\n:v!:\n\ny\n" },
            { "= Title\n:compat-mode:\n\n`{cn}`\n\n:compat-mode!:\n\ny\n" },
        };
    }

    @Test(dataProvider = "passingPages")
    public void pagePassesTheBuild(final String page) {
        assertThat(failures(page)).as(page).isEmpty();
    }
}
