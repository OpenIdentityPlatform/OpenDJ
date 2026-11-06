<!--
  The contents of this file are subject to the terms of the Common Development and
  Distribution License (the License). You may not use this file except in compliance with the
  License.

  You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
  specific language governing permission and limitations under the License.

  When distributing Covered Software, include this CDDL Header Notice in each file and include
  the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
  Header, with the fields enclosed by brackets [] replaced by your own identifying
  information: "Portions copyright [year] [name of copyright owner]".

  Copyright 2026 3A Systems, LLC.
-->

# Documentation checks

The Linux documentation build runs the cross-guide xref check during `verify`,
after `package` has generated the reference partials and preprocessed the guides.
It uses the same Asciidoctor 2.0 engine bundled with AsciidoctorJ as the existing
attribute check; no system Ruby or extra gems are required.

From the repository root, build the dependencies and documentation with:

```sh
mvn -Pdistribution-unix,man-pages verify
```

On Linux these profiles are enabled automatically. On macOS enable both explicitly.
The documentation module needs the core sources JAR and the server and LDAP toolkit
ZIPs produced by the dependency build. A standalone module build therefore requires
those artifacts to have been installed locally first.

After generation and preprocessing, rerun just the xref check **from this module**:

```sh
mvn -Pman-pages exec:exec@check-cross-guide-xrefs
```

The default source root is `target/asciidoc/source`, resolved against this module.
To check another prepared tree, pass an absolute path:

```sh
mvn -Pman-pages exec:exec@check-cross-guide-xrefs -Dxref.sourceDirectory=/path/to/prepared/source
```

The checker discovers every `.adoc` page, including Getting Started, Release Notes
and How-To when present. `partials`, `attachments`, `images` and the `man-pages` staging directory are not
independent guide pages; their content is checked in the context of the pages that include it.
A file xref into one of these staging directories fails: the target is not a
published page. File xref targets outside the supplied source root also fail,
even when those files exist locally. Guide `index.adoc` pages are read as UTF-8
and checked as Antora publishes them, with `include::./<chapter>.adoc[]` lines
replaced by links. Other include forms stay inline, and their anchors belong
to the index. A fragment on an index target must belong to that published index,
rather than a chapter linked by the rewrite.
Do not run against `src/main/asciidoc`: generated partials are required inputs.

For a subset, invoke the same script with the module's dependency classpath:

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/xref-classpath.txt
java -cp "$(cat target/xref-classpath.txt)" org.jruby.Main \
  src/main/resources/asciidoc/check-xrefs.rb target/asciidoc/source \
  'getting-started/*.adoc' 'release-notes/*.adoc' 'how-to/*.adoc'
```

Quote patterns: the checker expands each pattern relative to the supplied source
root, including when invoked from the module directory. Every requested pattern
must match at least one input; a missing root, unreadable input or failed traversal
also fails the command. Braces and brackets in the root path are literal.

The resolver checks file xrefs and their fragments after Asciidoctor substitutions,
using each target page's anchor catalog. Explicit anchors, automatically generated
section IDs (including custom prefixes/separators), conditional content and nested
includes use Asciidoctor's own rules. It ignores macros in comments, escaped macros
and examples without macro substitution. External URLs and internal fragment-only
xrefs are outside this check's scope.

Failures name the referring source, reference, target and source line when
Asciidoctor supplies it. The line is the start of the containing block; for some
included paragraphs it is the including directive. Include/parse warnings fail the
check rather than allowing a partially parsed target to pass. Exit status is 0 for
success, 1 for invalid references/inputs found while checking pages, and 2 for CLI
or discovery failures.

The module's TestNG suite runs the Ruby regression fixtures through the bundled
JRuby. It exercises valid references and failures after deleting targets or
renaming generated anchors, nested includes, automatic IDs, quoted patterns and
missing inputs. It also pins staging targets and discovery exclusions, published
index anchors, WARN diagnostics, external URLs and directory symlinks. Separate
JVM processes verify the actual CLI exit statuses 0, 1 and 2 and their diagnostic
streams (stdout on success, stderr on failure). When `chmod` cannot restrict
reads, the regression summary names the skipped unreadable-file and
unreadable-directory checks. To run these tests without regenerating the documentation:

```sh
mvn compiler:testCompile surefire:test
```

The `man-pages` profile also runs `AntoraPageTitlesIT` during `verify`, after
Antora conversion. It parses the headers of the four Release Notes and Getting
Started chapter pages and checks their expected document titles, then renders
their bodies with Antora's `env-site` attribute to check all 16 published chapter
and subsection fragment IDs. A `:title:` attribute must not substitute for a document
header: Asciidoctor can return that attribute as the title even without a header,
but Antora does not use it.
A section heading alone must fail: Antora needs a document header
to populate default xref labels, navigation labels and browser titles. The tests
also parse both preprocessed books to preserve their chapter titles, IDs and levels
for PDF generation. Missing generated inputs fail rather than skipping the check.

After preprocessing and Antora conversion, rerun this check from this module:

```sh
mvn -Pman-pages compiler:testCompile surefire:test@test-antora-page-titles
```

Chapter sources keep `==` headings for the combined books. With doc-maven-plugin
3.2.2, keep `:table-caption!:` before each chapter heading: the Antora converter
uses this existing attribute as the insertion point for `:leveloffset: -1`,
which promotes the chapter heading to a standalone page title only in its output.
Do not put the level offset directly in the book sources.

The chapter ID stays on the heading outside `env-site`. On the site, it is emitted
as a separate inline anchor after the page title: Antora's default UI does not
render the document ID as an HTML anchor. Keep both forms so incoming fragments
work in HTML and the combined books retain their chapter IDs. Keep a blank line
after the anchor's `endif::[]`: Asciidoctor does not end a paragraph at a section
title, so a heading directly below the anchor joins its paragraph and disappears
together with its fragment ID. Do not turn the anchor into a block anchor (`[[id]]`
or `[#id]`) either: a block anchor attaches to the next block and replaces that
section's automatic ID.
