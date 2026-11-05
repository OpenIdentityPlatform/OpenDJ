# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

require 'tmpdir'
require 'fileutils'
require_relative '../../../main/resources/asciidoc/check-xrefs'

# These run against the same CLI entry point as Maven, in one JRuby VM.
def check_xrefs_test
  count = 0
  skipped = []
  Dir.mktmpdir('xref-') do |temporary|
    root = File.join temporary, 'sources-{1}[1]'
    FileUtils.mkdir_p root
    write = lambda do |name, text|
      path = File.join root, name
      FileUtils.mkdir_p File.dirname(path)
      File.write path, text
    end
    write.call 'reference/page.adoc', "= Reference\n\ninclude::../partials/generated.adoc[]\n"
    write.call 'partials/generated.adoc', "include::nested/anchors.adoc[]\n"
    write.call 'partials/nested/anchors.adoc', "[#generated]\n== Generated Section\n\n=== Automatic Section\n\nInline [[inline-anchor]] anchor.\n"
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc#generated[Generated]\n"
    assert_check = lambda do |expected, patterns = ['getting-started/*.adoc'], expected_status = (expected ? 1 : 0)|
      status, output = CheckXrefs.run [root, *patterns]
      if expected
        raise "expected failure containing #{expected.inspect}, got #{status}: #{output}" unless status == expected_status && output.include?(expected)
      else
        raise "expected success, got #{status}: #{output}" unless status == 0
      end
      count += 1
      output
    end
    assert_check.call nil
    write.call 'man-pages/staged.adoc', 'xref:unpublished-page.adoc[]'
    %w[partials attachments images].each do |folder|
      write.call "#{folder}/unpublished.adoc", 'xref:missing.adoc[]'
    end
    assert_check.call nil, []
    write.call 'getting-started/page.adoc', "= Start\n\nxref:https://example.com/page.adoc#fragment[External]\n"
    assert_check.call nil
    ['_automatic_section', 'inline-anchor'].each do |id|
      write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc##{id}[Target]\n"
      assert_check.call nil
    end
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc[Target]\n"
    assert_check.call nil
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/missing.adoc#gone[Target]\n"
    output = assert_check.call "missing or unreadable file: #{File.join File.realpath(root), 'reference/missing.adoc'}"
    raise output unless output.include?('page.adoc:3:')
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc#generated[Target]\n"
    File.write File.join(root, 'partials/nested/anchors.adoc'), "== Renamed\n"
    assert_check.call '#generated'
    File.delete File.join(root, 'partials/nested/anchors.adoc')
    assert_check.call 'anchors.adoc'
    assert_check.call 'no input', ['how-to/*.adoc'], 2
    assert_check.call 'no input', ['getting-started/*.adoc', 'how-to/*.adoc'], 2
    write.call 'reference/page.adoc', "= Target\n:idprefix: custom-\n:idseparator: -\n\n== Automatic Section\n"
    write.call 'getting-started/page.adoc', "= Start\n:target: ../reference/page.adoc\n\nxref:{target}#custom-automatic-section[Target]\n"
    assert_check.call nil
    # WARN-level diagnostics must reject a partly parsed target.
    write.call 'partials/tagged.adoc', "// tag::present[]\n== Tagged\n// end::present[]\n"
    write.call 'reference/tagged.adoc', "= Tagged\n\ninclude::../partials/tagged.adoc[tag=nosuch]\n"
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/tagged.adoc[Target]\n"
    assert_check.call 'nosuch'
    # Commented, escaped, conditional and verbatim macros are not published links.
    write.call 'getting-started/page.adoc', <<~'ADOC'
      = Start

      // xref:../missing.adoc[]
      ifdef::never-defined[]
      xref:../missing.adoc[]
      endif::[]
      \xref:../missing.adoc[]

      ----
      xref:../missing.adoc[]
      ----
    ADOC
    assert_check.call nil
    write.call 'getting-started/page.adoc', "= Start\n\ninclude::../partials/ref.adoc[]\n"
    write.call 'partials/ref.adoc', "xref:../reference/page.adoc#missing[Target]\n"
    output = assert_check.call '#missing'
    # Asciidoctor may attribute the first included paragraph to its include directive.
    raise output unless output.include?('ref.adoc:1:') || output.include?('page.adoc:3:')
    # AsciiDoc cells use nested documents, but still belong to the publishing page.
    write.call 'getting-started/page.adoc', "= Start\n\n|===\na|xref:../reference/page.adoc#missing[Target]\n|===\n"
    assert_check.call '#missing'
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc#custom-automatic-section[Target]\n"
    File.delete File.join(root, 'reference/page.adoc')
    assert_check.call "missing or unreadable file: #{File.join File.realpath(root), 'reference/page.adoc'}"
    FileUtils.mkdir_p File.join(root, 'reference/page.adoc')
    assert_check.call "missing or unreadable file: #{File.join File.realpath(root), 'reference/page.adoc'}"
    status, output = CheckXrefs.run [File.join(root, 'absent')]
    raise output unless status == 2
    count += 1
    write.call 'getting-started/unreadable.adoc', '= Unreadable'
    unreadable = File.join root, 'getting-started/unreadable.adoc'
    begin
      File.chmod 0, unreadable
      if File.readable? unreadable
        skipped << 'unreadable file (chmod does not restrict reads)'
      else
        assert_check.call "missing or unreadable file: #{File.realpath unreadable}"
      end
    ensure
      File.chmod 0600, unreadable
    end
    directory = File.join root, 'unreadable-directory'
    FileUtils.mkdir_p directory
    begin
      File.chmod 0, directory
      if File.readable? directory
        skipped << 'unreadable directory (chmod does not restrict reads)'
      else
        assert_check.call 'unreadable-directory', ['getting-started/*.adoc'], 2
      end
    ensure
      File.chmod 0700, directory
    end
    status, output = CheckXrefs.run []
    raise output unless status == 2
    count += 1
    # A symlinked guide directory must fail traversal instead of being skipped.
    FileUtils.mkdir_p File.join(temporary, 'elsewhere')
    File.write File.join(temporary, 'elsewhere/linked.adoc'), "= Linked\n\nxref:missing.adoc[]\n"
    linked = File.join root, 'linked-guide'
    File.symlink File.join(temporary, 'elsewhere'), linked
    begin
      assert_check.call 'cannot traverse symbolic directory', [], 2
    ensure
      File.unlink linked
    end
    # A dangling file symlink fails when the page is loaded.
    File.symlink File.join(root, 'absent'), File.join(root, 'getting-started/broken.adoc')
    assert_check.call 'broken.adoc'
  end
  "#{count} xref regression checks passed#{skipped.empty? ? '' : "; skipped: #{skipped.join(', ')}"}"
end

# Targets must have the anchors of the published page, not staging/aggregate input.
def check_xrefs_publication_test
  failures = []
  count = 0
  Dir.mktmpdir('xref-publication-') do |temporary|
    root = File.join temporary, 'sources'
    write = lambda do |name, text|
      path = File.join root, name
      FileUtils.mkdir_p File.dirname(path)
      File.write path, text
    end
    assert_result = lambda do |status_expected, text_expected|
      status, report = CheckXrefs.run [root, 'start/*.adoc']
      failures << "expected #{status_expected} containing #{text_expected.inspect}, got #{status}: #{report}" unless
          status == status_expected && report.include?(text_expected)
      count += 1
    end
    %w[partials attachments images man-pages].each do |folder|
      write.call "#{folder}/fragment.adoc", "[#in-partial]\n== Fragment\n"
      write.call 'start/page.adoc', "= Start\n\nxref:../#{folder}/fragment.adoc#in-partial[Target]\n"
      assert_result.call 1, 'not a published page'
    end
    write.call 'guide/chapter.adoc', "[#chapter-id]\n== Chapter\n"
    write.call 'guide/index.adoc', "= Guide\n\n[#index-id]\n== Contents\n\ninclude::./chapter.adoc[]\n"
    write.call 'start/page.adoc', "= Start\n\nxref:../guide/index.adoc#chapter-id[Chapter]\n"
    assert_result.call 1, 'missing anchor #chapter-id'
    write.call 'start/page.adoc', "= Start\n\nxref:../guide/index.adoc#index-id[Contents]\n"
    assert_result.call 0, 'Checked 1 file xrefs'
    write.call 'start/page.adoc', "= Start\n\nxref:../guide/chapter.adoc#chapter-id[Chapter]\n"
    assert_result.call 0, 'Checked 1 file xrefs'
    # The converted index must retain its source path and line in diagnostics.
    write.call 'guide/index.adoc', "= Guide\n\ninclude::./missing.adoc[]\n"
    status, report = CheckXrefs.run [root, 'guide/index.adoc']
    failures << "expected missing index chapter diagnostic, got #{status}: #{report}" unless
        status == 1 && report.include?('index.adoc:3:') && report.include?('missing.adoc')
    count += 1
    # Include forms that Antora leaves inline contribute anchors to the index.
    write.call 'guide/index.adoc', "= Guide\n\ninclude::chapter.adoc[]\n"
    write.call 'start/page.adoc', "= Start\n\nxref:../guide/index.adoc#chapter-id[Chapter]\n"
    assert_result.call 0, 'Checked 1 file xrefs'
    # Index loading must use UTF-8 regardless of the runtime's default encoding.
    write.call 'guide/index.adoc', "= Guide\n:authors: Nemanja Lukić\n\ninclude::./chapter.adoc[]\n"
    previous = Encoding.default_external
    begin
      Encoding.default_external = Encoding::US_ASCII
      status, report = CheckXrefs.run [root, 'guide/index.adoc']
    ensure
      Encoding.default_external = previous
    end
    failures << "expected a non-ASCII index to load, got #{status}: #{report}" unless
        status == 0 && report.include?('Checked 1 file xrefs')
    count += 1
    # Existing files outside the published source tree cannot satisfy an xref.
    File.write File.join(temporary, 'outside.adoc'), "[#outside]\n== Outside\n"
    write.call 'start/page.adoc', "= Start\n\nxref:../../outside.adoc#outside[Outside]\n"
    assert_result.call 1, "outside the source root: #{File.join File.realpath(temporary), 'outside.adoc'}"
    FileUtils.mkdir_p File.join(temporary, 'sources-sibling')
    File.write File.join(temporary, 'sources-sibling/page.adoc'), "= Sibling\n"
    write.call 'start/page.adoc', "= Start\n\nxref:../../sources-sibling/page.adoc[Sibling]\n"
    assert_result.call 1, 'outside the source root'
  end
  raise failures.join("\n") unless failures.empty?
  "#{count} publication checks passed"
end
