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

# Uses the Asciidoctor bundled in AsciidoctorJ; no system Ruby gems are needed.
require 'rubygems'
Gem.use_paths 'uri:classloader:/' if defined?(JRUBY_VERSION)
require 'asciidoctor'
require 'asciidoctor/converter/html5'
require 'find'
require 'set'
require_relative 'extensions/nested-open-block'

module CheckXrefs
  # Collect only macros actually converted into links, after substitutions. This
  # excludes comments, inactive conditionals, escaped macros and literal examples.
  class Converter < Asciidoctor::Converter::Html5Converter
    attr_reader :xrefs

    def initialize *args
      super
      @xrefs = []
    end

    def convert_inline_anchor node
      if node.type == :xref && (path = node.attr 'path') && path !~ %r{\A(?:[a-z][a-z0-9+.-]*:|//)}i
        parent = node.parent
        parent = parent.parent while parent && !parent.source_location
        @xrefs << [path, (node.attr 'fragment'), node.target, parent && parent.source_location]
      end
      super
    end
  end

  class Resolver
    # Staging inputs are included by pages, never published as pages themselves.
    UNPUBLISHED = %w[partials attachments images man-pages].freeze

    attr_reader :errors, :checked

    def initialize root
      @root = File.realpath root
      raise IOError, "not a source directory: #{root}" unless File.directory? @root
      @documents = {}
      @errors = []
      @checked = 0
    end

    def inputs patterns
      # Find raises on unreadable directories. Dir.glob silently skips them.
      files = []
      Find.find(@root, ignore_error: false) do |path|
        if File.symlink?(path) && File.directory?(path)
          raise IOError, "cannot traverse symbolic directory: #{path}"
        end
        files << path.delete_prefix(@root + '/') if path.end_with? '.adoc'
      end
      if patterns.empty?
        selected = files.reject {|path| UNPUBLISHED.include? path.split('/').first }
      else
        selected = patterns.flat_map do |pattern|
          matches = files.select {|path| File.fnmatch? pattern, path, File::FNM_PATHNAME | File::FNM_EXTGLOB }
          raise IOError, "no input files match #{pattern.inspect} under #{@root}" if matches.empty?
          matches
        end
      end
      raise IOError, "no input .adoc pages under #{@root}" if selected.empty?
      selected.uniq.sort.map {|path| File.join @root, path }
    end

    def load_page path
      if @documents.key? path
        cached = @documents[path]
        raise cached if Exception === cached
        return cached
      end
      raise IOError, "missing or unreadable file: #{path}" unless File.file?(path) && File.readable?(path)
      logger = Asciidoctor::MemoryLogger.new
      converter = Converter.new 'html5'
      options = { safe: :unsafe, sourcemap: true,
          base_dir: File.dirname(path), converter: converter, logger: logger,
          attributes: { 'relfilesuffix' => '.adoc' } }
      if File.basename(path) == 'index.adoc'
        # AntoraMojo publishes guide indexes as chapter links, not an aggregate.
        source = File.read(path).gsub 'include::./', '* xref:'
        options[:attributes].merge! 'docfile' => path, 'docdir' => File.dirname(path),
            'docname' => 'index', 'docfilesuffix' => '.adoc'
        document = Asciidoctor.load source, options
      else
        document = Asciidoctor.load_file path, options
      end
      document.convert
      # Include failures and the maximum include depth are errors; warnings such as a
      # missing include tag also mean that the document was only partly traversed and
      # must fail the check.
      diagnostics = logger.messages.select {|entry| (Asciidoctor::Logger::Severity.const_get entry[:severity]) >= Asciidoctor::Logger::Severity::WARN }
      unless diagnostics.empty?
        messages = diagnostics.map do |entry|
          message = entry[:message]
          if Hash === message
            cursor = message[:source_location]
            "#{cursor && cursor.file}:#{cursor && cursor.lineno}: #{message[:text]}"
          else
            message.to_s
          end
        end
        raise IOError, messages.join("\n")
      end
      ids = document.catalog[:refs].keys.to_set
      ids << document.id if document.id
      @documents[path] = [ids, converter.xrefs]
    rescue StandardError => e
      @documents[path] = e
      raise
    end

    def check pages
      pages.each do |page|
        begin
          _, refs = load_page page
          refs.each do |path, fragment, reference, location|
            @checked += 1
            source = location && location.file || page
            line = location && location.lineno
            prefix = "#{source}#{line ? ':' + line.to_s : ''}: xref:#{reference}"
            begin
              target = File.expand_path path, File.dirname(page)
              if target.start_with?(@root + '/') && UNPUBLISHED.include?(target.delete_prefix(@root + '/').split('/').first)
                raise IOError, "not a published page: #{target}"
              end
              ids, = load_page target
              if fragment && !fragment.empty? && !ids.include?(fragment)
                @errors << "#{prefix}: missing anchor ##{fragment} in #{target}"
              end
            rescue StandardError => e
              @errors << "#{prefix}: #{e.message}"
            end
          end
        rescue StandardError => e
          @errors << "#{page}: #{e.message}"
        end
      end
    end
  end

  # Return status and report so the tests exercise exactly the CLI's behavior.
  def self.run args
    return [2, 'Usage: check-xrefs.rb SOURCE_ROOT [PATTERN ...]'] if args.empty?
    resolver = Resolver.new args.first
    pages = resolver.inputs args.drop(1)
    resolver.check pages
    if resolver.errors.empty?
      [0, "Checked #{resolver.checked} file xrefs in #{pages.size} pages"]
    else
      [1, resolver.errors.uniq.join("\n")]
    end
  rescue StandardError => e
    [2, "xref check: #{e.message}"]
  end
end

if $PROGRAM_NAME == __FILE__
  status, report = CheckXrefs.run ARGV
  (status == 0 ? $stdout : $stderr).puts report
  exit status
end
