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
    assert_check = lambda do |expected, patterns = ['getting-started/*.adoc']|
      status, output = CheckXrefs.run [root, *patterns]
      if expected
        raise "expected failure containing #{expected.inspect}, got #{status}: #{output}" unless status != 0 && output.include?(expected)
      else
        raise "expected success, got #{status}: #{output}" unless status == 0
      end
      count += 1
      output
    end
    assert_check.call nil
    write.call 'man-pages/staged.adoc', 'xref:unpublished-page.adoc[]'
    assert_check.call nil, []
    ['_automatic_section', 'inline-anchor'].each do |id|
      write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc##{id}[Target]\n"
      assert_check.call nil
    end
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc[Target]\n"
    assert_check.call nil
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/missing.adoc#gone[Target]\n"
    output = assert_check.call 'missing.adoc'
    raise output unless output.include?('page.adoc:3:')
    write.call 'getting-started/page.adoc', "= Start\n\nxref:../reference/page.adoc#generated[Target]\n"
    File.write File.join(root, 'partials/nested/anchors.adoc'), "== Renamed\n"
    assert_check.call '#generated'
    File.delete File.join(root, 'partials/nested/anchors.adoc')
    assert_check.call 'anchors.adoc'
    assert_check.call 'no input', ['how-to/*.adoc']
    assert_check.call 'no input', ['getting-started/*.adoc', 'how-to/*.adoc']
    write.call 'reference/page.adoc', "= Target\n:idprefix: custom-\n:idseparator: -\n\n== Automatic Section\n"
    write.call 'getting-started/page.adoc', "= Start\n:target: ../reference/page.adoc\n\nxref:{target}#custom-automatic-section[Target]\n"
    assert_check.call nil
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
    assert_check.call 'page.adoc'
    FileUtils.mkdir_p File.join(root, 'reference/page.adoc')
    assert_check.call 'page.adoc'
    status, output = CheckXrefs.run [File.join(root, 'absent')]
    raise output if status == 0
    count += 1
    write.call 'getting-started/unreadable.adoc', '= Unreadable'
    unreadable = File.join root, 'getting-started/unreadable.adoc'
    begin
      File.chmod 0, unreadable
      assert_check.call 'unreadable.adoc' unless File.readable? unreadable
    ensure
      File.chmod 0600, unreadable
    end
    directory = File.join root, 'unreadable-directory'
    FileUtils.mkdir_p directory
    begin
      File.chmod 0, directory
      assert_check.call 'unreadable-directory' unless File.readable? directory
    ensure
      File.chmod 0700, directory
    end
    status, output = CheckXrefs.run []
    raise output if status == 0
    count += 1
    # Traverse errors must not be swallowed by a glob or by the CLI.
    File.symlink File.join(root, 'absent'), File.join(root, 'getting-started/broken.adoc')
    assert_check.call 'broken.adoc'
  end
  "#{count} xref regression checks passed"
end
