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
missing inputs. To run these tests without regenerating the documentation:

```sh
mvn compiler:testCompile surefire:test
```
