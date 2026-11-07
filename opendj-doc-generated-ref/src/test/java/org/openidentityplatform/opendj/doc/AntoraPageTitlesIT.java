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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.Section;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Checks the real Antora output after package and the chapter structure used by PDF generation. */
@Test
public class AntoraPageTitlesIT extends ForgeRockTestCase {
    private Asciidoctor asciidoctor;
    private File buildDirectory;

    @BeforeClass
    public void start() {
        buildDirectory = new File(System.getProperty("docBuildDirectory",
                new File(System.getProperty("basedir", "."), "target/asciidoc").getPath()));
        asciidoctor = Asciidoctor.Factory.create();
    }

    @AfterClass(alwaysRun = true)
    public void shutdown() {
        if (asciidoctor != null) {
            asciidoctor.shutdown();
        }
    }

    @DataProvider
    public Object[][] pages() {
        return new Object[][] {
            { "release-notes", "release-5.2.0", "OpenDJ 5.2.0",
              new String[] { "release-5-2-0", "_selected_changes", "_distributions" } },
            { "release-notes", "release-history", "Release History",
              new String[] { "release-history", "history-5-2-0" } },
            { "release-notes", "requirements-and-upgrade", "Requirements and Upgrade Considerations",
              new String[] { "release-requirements", "_java_and_installation_environment", "_plan_an_upgrade",
                             "_compatibility_checks", "_known_issues_and_help" } },
            { "getting-started", "overview", "Directory Concepts", new String[] { "getting-started-overview" } },
            { "getting-started", "first-steps", "Your First Directory",
              new String[] { "getting-started-first-directory", "_prepare_the_environment",
                             "_set_up_and_start_the_server", "_add_a_person", "_search_and_change_the_entry",
                             "_restart_and_remove_the_sample_data", "_next_steps" } }
        };
    }

    @Test(dataProvider = "pages")
    public void generatedPageHasDocumentTitle(String guide, String page, String title, String[] fragments) {
        final File input = new File(buildDirectory, "antora/modules/" + guide + "/pages/" + page + ".adoc");
        assertThat(input).isFile();
        // Antora extracts metadata from the header. A full parse can fall back to a section
        // title and conceal a missing document header, although Antora publishes "Untitled".
        final Document document = asciidoctor.loadFile(input,
                Options.builder().safe(SafeMode.SAFE).attributes(Map.of("env-site", ""))
                        .parseHeaderOnly(true).build());
        assertThat(document.getDoctitle()).as("document title of %s", input).isEqualTo(title);
        // Without a header, Asciidoctor can still return the title attribute, which Antora ignores.
        assertThat(document.hasAttribute("title"))
                .as("%s must have a document header rather than a :title: attribute", input).isFalse();
        final String html = asciidoctor.convertFile(input,
                Options.builder().safe(SafeMode.SAFE).attributes(Map.of("env-site", ""))
                        .standalone(false).toFile(false).build());
        for (final String fragment : fragments) {
            assertThat(html).as("published fragment %s in the rendered body of %s", fragment, input)
                    .contains("id=\"" + fragment + "\"");
        }
    }

    @Test
    public void releaseHistoryKeepsPublishedText() {
        final String html = renderReleaseHistory();
        assertThat(html).contains("8aac222...ab5cb61", "missing @Override annotations", "stale @param tags",
                "--help-&lt;subcommand&gt;", "Become a backer 🙏", "Become a sponsor ❤️", "PKCS#1 v1.5")
                .doesNotContain("href=\"https://github.com/Override\"", "href=\"https://github.com/param\"",
                        "href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/1\"");
    }

    @Test
    public void releaseHistoryLinksIssuesAndCommitRanges() {
        final String html = renderReleaseHistory();
        assertThat(html).contains(
                "[<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/747\">#747</a>]",
                "[<a href=\"https://github.com/OpenIdentityPlatform/OpenAM/issues/980\">"
                        + "OpenIdentityPlatform/OpenAM#980</a>]",
                "Addresses <a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/397\">#397</a>",
                "[<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/90\">#90</a>, "
                        + "<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/432\">#432</a>]",
                "[<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/148\">#148</a>,"
                        + "<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/261\">#261</a>,"
                        + "<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/issues/282\">#282</a>]",
                "<a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/compare/8aac222...ab5cb61\">"
                        + "8aac222...ab5cb61</a>",
                "compare <a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/compare/93e2ea0...00c8d42\">"
                        + "93e2ea0...00c8d42</a>",
                "compare: <a href=\"https://github.com/OpenIdentityPlatform/OpenDJ/compare/"
                        + "b769b7ec8c3e7d7a6d89bb76b53db4735141781c...ae848be258db66917554b6f2c24d03d7e207980f\">"
                        + "b769b7ec8c3e7d7a6d89bb76b53db4735141781c...ae848be258db66917554b6f2c24d03d7e207980f</a>");
    }

    @Test
    public void releaseHistoryUsesSearchableCveIdentifiers() {
        assertThat(renderReleaseHistory()).contains("CVE-2025-12194").doesNotContain("CVE\u20102025\u201012194");
    }

    private String renderReleaseHistory() {
        final File input = new File(buildDirectory, "antora/modules/release-notes/pages/release-history.adoc");
        assertThat(input).isFile();
        return asciidoctor.convertFile(input,
                Options.builder().safe(SafeMode.SAFE).attributes(Map.of("env-site", ""))
                        .standalone(false).toFile(false).build());
    }

    @DataProvider
    public Object[][] books() {
        return new Object[][] {
            { "release-notes",
              new String[] { "OpenDJ 5.2.0", "Requirements and Upgrade Considerations", "Release History" },
              new String[] { "release-5-2-0", "release-requirements", "release-history" } },
            { "getting-started", new String[] { "Directory Concepts", "Your First Directory" },
              new String[] { "getting-started-overview", "getting-started-first-directory" } }
        };
    }

    @Test(dataProvider = "books")
    public void combinedBookKeepsChapters(String guide, String[] titles, String[] ids) {
        final File input = new File(buildDirectory, "source/" + guide + "/index.adoc");
        assertThat(input).isFile();
        final Document document = asciidoctor.loadFile(input,
                Options.builder().safe(SafeMode.UNSAFE).build());
        final List<Section> chapters = document.getBlocks().stream()
                .filter(Section.class::isInstance).map(Section.class::cast).collect(Collectors.toList());
        assertThat(chapters).extracting(Section::getTitle).containsExactly(titles);
        assertThat(chapters).extracting(Section::getId).containsExactly(ids);
        assertThat(chapters).extracting(Section::getLevel).containsOnly(1);
    }
}
