#!/usr/bin/env ruby

require 'fileutils'

# Automate copyright update with year 2014, using 'svn status' to retrieve modified files.
# Only modified files (with 'M' mark) are updated.
#
class Copyright

  def updated_files
    files = []
    puts "Get modified files from svn..."
    lines = `svn st`
    puts "There are #{lines.split("\n").size} files to update."
    lines.split("\n").each { |line|
      mod, file = line.split(" ")
      if mod=="M"
        files << file
      end
    }
    files
  end

  def process(file)
    is_replaced = nil
    File.open(file) { |source|
      content = source.read
      is_replaced = update_copyright(content)
      if is_replaced
        File.open(file + ".copy", "w+") { |f| f.write(content) }
        #puts `head -n 27 #{file}`
        #puts("content:\n" + content.split("\n")[20..27].join("\n"))
      else
        puts "WARN : no replacement"
      end
    }
    if is_replaced
      FileUtils.mv(file + ".copy", file, :verbose => false)
    end
  end


  def update_copyright(content)
    pattern = /(^\s+\*\s+)Portions Copyright (\d+)-(\d+) ForgeRock,? AS/i
    replace = '\1Portions Copyright \2-2014 ForgeRock AS'
    is_replaced = content.gsub!(pattern, replace)

    if is_replaced.nil?
      pattern = /(^\s+\*\s+)Portions Copyright (\d+) ForgeRock,? AS/i
      mdata = content.match(pattern)
      if !mdata.nil? && mdata[2]!="2014"
        replace = '\1Portions Copyright \2-2014 ForgeRock AS'
        is_replaced = content.gsub!(pattern, replace)
      end
      if is_replaced.nil?
        # No portions line, add it
        pattern = /(^\s+\*)(\s+)Copyright (\d+-?\d*)\s(.*)$\n\s+\*\/$/i
        replace = '\1\2Copyright \3 \4' + "\n\\1\\2Portions Copyright 2014 ForgeRock AS\n\\1/"
        is_replaced = content.gsub!(pattern, replace)
      end
    end
    is_replaced
  end

  def run(args)
    if args.size==0
      files = updated_files
    else
      files = argv
    end
    files.each { |file|
      puts "Processing file #{file}"
      process(file)
    }
  end

end

# Launch copyright update
Copyright.new.run(ARGV)

