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

# Warns about an attribute reference that a verbatim block publishes as literal text.
#
# A listing, literal or passthrough block replaces {name} only when its subs include
# attributes (subs="+attributes"). Without them the braces reach the page as they are,
# and Asciidoctor says nothing: `unzip opendj-{opendj-version}.zip` was published that
# way. A literal table cell (l|) never substitutes attributes and takes no subs. A
# reference to an attribute that is not defined at all, outside such a block, is
# Asciidoctor's own warning once attribute-missing is set to warn.
#
# A reference is reported when its name is an attribute at that point of the document,
# an intrinsic one such as {nbsp}, or one that an attribute entry anywhere under the
# directory named by the literal-attribute-sources attribute sets. The last catches a
# page that neither defines the attribute nor substitutes it. Braces around any other
# name are meant literally - {SSHA} password values, {cn} in a MakeLDIF template - and
# are left alone. Without attribute subs a backslash does not escape the reference, so
# \{name} is published with its backslash and is reported too.
require 'set'

class LiteralAttributeReferences < Asciidoctor::Extensions::TreeProcessor
  include Asciidoctor::Logging

  ReferenceRx = /(\\)?\{(\w[\w-]*)\}/
  EntryRx = /^:(\w[\w-]*):/

  @names_by_dir = {}

  # The names that an attribute entry sets in some .adoc file under dir. The directory is
  # the base of the glob, not a part of the pattern, so braces in its path match as such.
  def self.names_in dir
    @names_by_dir[dir] ||= Dir.glob('**/*.adoc', base: dir).each_with_object(Set.new) do |path, names|
      File.foreach((File.join dir, path), encoding: 'UTF-8') {|line| names << $1.downcase if EntryRx =~ line }
    end
  end

  def process document
    dir = document.attr 'literal-attribute-sources'
    names = dir ? (LiteralAttributeReferences.names_in dir) : Set.new
    # The parser has already reset the document attributes to the header, so replay the
    # entries of the body in document order, as the converter does, and reset them again
    # for the converter afterwards. An AsciiDoc table cell is a document of its own.
    documents = []
    document.find_by traverse_documents: true do |block|
      documents << block if block.context == :document
      block.document.playback_attributes block.attributes
      if Asciidoctor::Table::Cell === block
        # The text of a literal cell only escapes special characters, so its braces stay.
        check block, block.text, names, 'literal table cell', 'use an a| cell with a listing that has subs="+attributes"' if block.content_model == :verbatim
      elsif Asciidoctor::Block === block &&
          (block.content_model == :verbatim || block.content_model == :raw) && !(block.subs.include? :attributes)
        # subs="attributes" would replace the default subs of the block, so the advice adds to them.
        check block, (block.lines.join Asciidoctor::LF), names, %(#{block.context} block), 'add subs="+attributes"'
      end
      false
    end
    documents.each(&:restore_attributes)
    nil
  end

  def check block, text, names, what, advice
    attributes = block.document.attributes
    text.scan(ReferenceRx) do |escaped, name|
      key = name.downcase
      next unless (attributes.key? key) || (names.include? key) || (Asciidoctor::INTRINSIC_ATTRIBUTES.key? key)
      message = escaped ?
          %(\\{#{name}} is published with its backslash: the #{what} does not substitute attributes, #{advice}) :
          %(attribute {#{name}} is published as literal text: the #{what} does not substitute attributes, #{advice})
      logger.warn message_with_context message, source_location: block.source_location
    end
  end
end

Asciidoctor::Extensions.register do
  tree_processor LiteralAttributeReferences
end
