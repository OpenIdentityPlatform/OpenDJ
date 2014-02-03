#!/usr/bin/env ruby

require 'fileutils'

#
# Automate code replacements using regular expressions
#
# To define a new replacement, add a new constant like VALIDATOR.
#
# It should be a ruby Hash with three mandatory keys and one optional key:
#
#  :dirs => a list of directory to run replacements. All subdirs are processed.
#  :extensions => a list of file extensions. Only file with these extensions are processed.
#  :replacements => a list of replacements, lines are processed 2 by 2
#    - first line gives the pattern to replace, as a ruby regexp (see http://rubular.com/ for help and tool)
#    - second line gives the replacement string, using \1, \2, ... to insert matching groups. This is a string,
#       use simple quote if no special char is inserted, or use double quote if using special char like \n
#    Don't forget to put a comma at end of each line, this is the array element separator.
#    It is ok to leave a new line to separate each pair of line for readability.
#    It is ok to use a comment in the array (use # as first non blank character of line).
#
# The optional key is :stopwords => a list of stopword. If any word in this list appears in a file name, the file
#   is not processed. Use it to exclude some files or directory that must not be processed.
#
# Once you have define your replacement, add the constant in REPLACEMENTS array. it will be taken into account when
# running the program (run it at root of project) with command: ./replace.rb
#
class Replace

  # Messages map : contains for each message its associated level
  MESSAGES_MAP = {}

  # Mapping of opendj2 log levels to opendj3 logging method
  LOG_LEVELS = {
    'INFO' => 'debug',
    'MILD_WARN' => 'warn',
    'SEVERE_WARN' => 'warn',
    'MILD_ERR' => 'error',
    'SEVERE_ERR' => 'error',
    'FATAL_ERR' => 'error',
    'DEBUG' => 'trace',
    'NOTICE' => 'info',
  }

  # All directories that contains java code
  JAVA_DIRS = ["src/server", "src/quicksetup", "src/ads", "src/guitools", "tests/unit-tests-testng/src"]
  SNMP_DIR = ["src/snmp/src"]
  DSML_DIR = ["src/dsml/org"]


  # Replacement for types
  TYPES = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.types.(DN|RDN|Attribute|Entry|ResultCode);/,
        'import org.forgerock.opendj.ldap.\1;',

        /import org.opends.server.(types|api).(AttributeType|MatchingRule);/,
        'import org.forgerock.opendj.ldap.schema.\2;',

      ]
  }

  # Replacement for types
  DN_TYPE = {
    :dirs => JAVA_DIRS + ["src/admin/generated"],
    :extensions => ["java"],
    :replacements =>
      [
        /package org.opends.server.types.(\b\w\b);/,
        "package org.opends.server.types.\\1;\n\n" +
        'import org.forgerock.opendj.ldap.DN;',

        /import org.opends.server.types.DN;/,
        'import org.forgerock.opendj.ldap.DN;',

        /import org.opends.server.types.\*;/,
        "import org.opends.server.types.*;\nimport org.forgerock.opendj.ldap.DN;",

        /DN.NULL_DN/,
        "DN.rootDN()"

      ]
  }

  # Replacement for exceptions
  # Modify 36 files, for a total of 134 replacements - leaves 1277 compilation errors but mostly from generated config
  EXCEPTIONS = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.admin.client.AuthorizationException;/,
        'import org.forgerock.opendj.ldap.ErrorResultException;',

        /\bAuthorizationException\b/,
        'ErrorResultException',

        /import org.opends.server.admin.client.CommunicationException;\n/,
        '',

        /throws CommunicationException\b, /,
        'throws ',

        /, CommunicationException\b(, )?/,
        '\1',

        /\bCommunicationException\b/,
        'ErrorResultException',
      ]
  }

  # Replacement for loggers
  # Modify 454 files, for a total of 2427 replacements - leaves 72 compilation errors
  # TODO: add I18N loggers
  LOGGERS = {
    :dirs => SNMP_DIR,
    :stopwords => ['src/server/org/opends/server/loggers', 'DebugLogPublisher'],
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.loggers.debug.DebugTracer;/,
        "import org.forgerock.i18n.LocalizableMessage;\nimport org.forgerock.i18n.slf4j.LocalizedLogger;",

        /import java.util.logging.Logger;/,
        "import org.forgerock.i18n.LocalizableMessage;\nimport org.forgerock.i18n.slf4j.LocalizedLogger;",

        /import java.util.logging.Level;/,
        '',

        /import org.opends.server.types.DebugLogLevel;\n/,
        '',

        #/import (static )?org.opends.server.loggers.debug.DebugLogger.*;\n/,
        #'',

        /DebugTracer TRACER = (DebugLogger.)?getTracer\(\)/,
        "LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass()",

        /^\s*\/\*\*\n.*The tracer object for the debug logger.\n\s*\*\/$\n/,
        '',

        /^\s*\/\/\s*The tracer object for the debug logger.$\n/,
        '',

        /if \(debugEnabled\(\)\)\s*{\s* TRACER.debugCaught\(DebugLogLevel.ERROR, (\b.*\b)\);\s*\n\s*}$/,
        'logger.traceException(\1);',

        /TRACER\.debugCaught\(DebugLogLevel.ERROR, (\b.*\b)\);/,
        'logger.traceException(\1);',

        /TRACER.debug[^(]+\(/,
        'logger.trace(',

        /logger.trace\(DebugLogLevel.\b\w+\b, ?/,
        'logger.trace(',

        /logger.trace\((e|de)\)/,
        'logger.traceException(\1)',

        /(DebugLogger\.|\b)debugEnabled\(\)/,
        'logger.isTraceEnabled()',

        /(LOG|logger).log\((Level.)?WARNING, ?/,
        'logger.warn(',

        /(LOG|logger).log\((Level.)?CONFIG, ?/,
        'logger.info(',

        /(LOG|logger).log\((Level.)?INFO, ?/,
        'logger.debug(',

        /(LOG|logger).log\((Level.)?SEVERE, ?/,
        'logger.error(',

        /(LOG|logger).log\((Level.)?FINE, ?/,
        'logger.trace(',

        /logger.(warn|info|error|debug)([^;]+);/,
        'logger.\1(LocalizableMessage.raw\2);',

        /(private static final|static private final) Logger LOG =[^;]+;/,
        'private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();',
      ]
  }

  I18N_LOGGERS = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
         # Message message = ERR_REFINT_UNABLE_TO_EVALUATE_TARGET_CONDITION.get(mo
         #                    .getManagedObjectDefinition().getUserFriendlyName(), String
         #                    .valueOf(mo.getDN()), StaticUtils.getExceptionMessage(e));
         # ErrorLogger.logError(message);
        /\bMessage\b \b(\w+)\b = (\w+\.)?\b(\w+)\b\s*.\s*get([^;]+);\n(\s+)ErrorLogger.logError\(\1\);/m,
        "    Message message = \\2.get\\4;\n" +
        "LocalizedLogger logger = LocalizedLogger.getLocalizedLogger(\\3.resourceName());\n\\5logger.error(\\1);",
     ]
  }

  MSG_ARGN_TOSTRING = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /([A-Z0-9_]+\s*\.\s*get\s*\([^;]*)\.toString\(\)/m,
        '\1',
      ]
  }

  MSG_ARGN_STRING_VALUEOF = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        # Need to fix removing the last parentheses
        /([A-Z0-9_]+\s*\.\s*get\s*\([^;]*)\s*String\s*\.\s*valueOf\s*\(/m,
        '\1',
      ]
  }

  LOGGER_TOSTRING = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        # Need to fix removing the last parentheses
        /(logger\.\s*(trace|debug|warn|info|error)\s*\([^;]*)\s*String\s*\.\s*valueOf\s*\(/m,
        /([A-Z0-9_]+\s*\.\s*get\s*\([^;]*)\.toString\(\)/m,
        '\1',
      ]
  }

  LOGGER_STRING_VALUEOF = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        # Need to fix removing the last parentheses
        /(logger\.\s*(trace|debug|warn|info|error)\s*\([^;]*)\s*String\s*\.\s*valueOf\s*\(/m,
        '\1',
      ]
  }

  LOGGER_ISTRACEENABLED_TRACEEXCEPTION = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /if\s*\(\s*logger\s*\.\s*isTraceEnabled\s*\(\s*\)\s*\)\s*(logger\s*\.\s*trace(Exception)?\s*\(\s*\w+\s*\)\s*;)/,
        '\1',
        /if\s*\(\s*logger\s*\.\s*isTraceEnabled\s*\(\s*\)\s*\)\s*\{\s*(logger\s*\.\s*trace(Exception)?\s*\(\s*\w+\s*\)\s*;)\s*\}/,
        '\1',
      ]
  }

  # List of replacements to run
  REPLACEMENTS = [ LOGGER_STRING_VALUEOF ]
  #REPLACEMENTS = [ MESSAGES, TYPES, DN_TYPES, EXCEPTIONS, LOGGERS, I18N_LOGGERS ]


  ################################### Processing methods ########################################

  # Main method : run replacements defined in REPLACEMENTS constant
  def run
    REPLACEMENTS.each { |repl|
      puts "Replacing " + Replace.constants.find{ |name| Replace.const_get(name)==repl }.to_s
      stopwords = repl[:stopwords] || ["--nostopword--"]
      replace_dirs(repl[:replacements], repl[:dirs], stopwords, repl[:extensions])
    }
  end

  # Process replacements on the provided directories
  def replace_dirs(replacements, dirs, stopwords, extensions)
    count_files = 0
    count_total = 0
    dirs.each { |directory|
      files = files_under_directory(directory, extensions)
      files.each { |file|
        exclude_file = stopwords.any? { |stopword| file.include?(stopword) }
        next if exclude_file
        count = replace_file(file, replacements)
        if count > 0
          count_files += 1
          count_total += count
        end
      }
    }
    puts "Replaced in #{count_files} files, for a total of #{count_total} replacements"
  end

  # Process replacement on the provided file
  def replace_file(file, replacements)
    count = 0
    File.open(file) { |source|
      contents = source.read
      (0..replacements.size-1).step(2).each { |index|
        pattern, replace = replacements[index], replacements[index+1]
        replace = replace.gsub('{CLASSNAME}', classname(file))
        is_replaced = true
        while is_replaced
          is_replaced = contents.gsub!(pattern, replace)
          if is_replaced then count += 1 end
        end
      }
      File.open(file + ".copy", "w+") { |f| f.write(contents) }
    }
    FileUtils.mv(file + ".copy", file, :verbose => false)
    count
  end

  # Return java class name from java filename
  def classname(file)
    name = file.gsub(/.*\/(.*).java$/, '\1')
    if name.nil? then '' else name end
  end

  # Process provided directories
  # Expects a processing block accepting a file as argument and returning a count of changes dones
  def process_dirs(dirs, stopwords, extensions)
    count_files = 0
    count_total = 0
    dirs.each { |directory|
      files = files_under_directory(directory, extensions)
      files.each { |file|
        #puts file.to_s
        exclude_file = stopwords.any? { |stopword| file.include?(stopword) }
        next if exclude_file
        count = yield file # call the block
        if count > 0
          count_files += 1
          count_total += count
        end
      }
    }
    puts "Replaced in #{count_files} files, for a total of #{count_total} replacements"
  end

  # Process provided file
  # Expects a processing block accepting a source string as argument and returning a count of changes + a new
  # content
  def process_file(file)
    count = 0
    File.open(file) { |source|
      contents = source.read
      count, new_contents = yield contents
      File.open(file + ".copy", "w+") { |f| f.write(new_contents) }
    }
    FileUtils.mv(file + ".copy", file, :verbose => false)
    count
  end


  # Return all files with provided extensions under the provided directory
  # and all its subdirectories recursively
  def files_under_directory(directory, extensions)
    Dir[directory + '/**/*.{' + extensions.join(",") + '}']
  end

  def run_messages
    prepare_messages
    process_dirs(JAVA_DIRS, ["--nostopword--"], ['java']) { |file|
       process_file(file) { |content|
          count, new_content = process_message(content)
          next count, new_content
       }
    }
  end

  def process_message(content)
    has_logger = /LocalizedLogger\.getLoggerForThisClass/ =~ content
    needs_logger = /logError/ =~ content
    count = 0
    if needs_logger && has_logger.nil?
       count = 1

       content.sub!(/class ([^{]+){/,
          "class \\1{\n\n  " +
          "private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();\n")

       content.sub!(/import (.*);/,
          "import \\1;\nimport org.forgerock.i18n.slf4j.LocalizedLogger;")
    end

    if needs_logger
      count = 1
      pattern = /(final )?(LocalizableMessage )?message\s*=\s*(\w+)\s*.\s*get\(\s*([^;]*)\);\s+(ErrorLogger\.)?logError\(\w+\);/m
      mdata = pattern.match(content)
      while !mdata.nil? do
         msg = mdata[3]
         args = if mdata[4].nil? || mdata[4]=="" then "" else ", " + mdata[4] end
         level = MESSAGES_MAP[msg]
         puts "1... #{level} - #{msg}"
         content.sub!(pattern, "logger.#{level}(#{msg}#{args});")
         mdata = pattern.match(content)
      end

      pattern = /logError\((\w+).get\s*\(\s*/m
      mdata = pattern.match(content)
      stop = {}
      while !mdata.nil? do
         msg = mdata[1]
         break if !stop[msg].nil?
         stop[msg] = msg
         level = MESSAGES_MAP[msg]
         puts "2... #{level} - #{msg}"
         if !level.nil?
            content.sub!(pattern, "logger.#{level}(#{msg}.get(")
         end
         mdata = pattern.match(content)
      end

      # all remaining patterns
      content.gsub!(/(ErrorLogger\.)?logError\(/, 'logger.error(')

    end
    return count, content
  end

  def prepare_messages
    files = Dir['src/messages/messages/*.properties']
    files.each do |file| messages(file) end
  end

  # Build a map of error messages and error level
  def messages(message_file)
    File.open(message_file).each { |line|
      line = line.chomp
      next if line.size==0 || line[0..0]=="#" || line[0..0]==" " || line[0..0]!=line[0..0].upcase || line[0..5]=="global"
      token = line.split("=")
      first, *rest = token[0].split "_"
      level_label = if %w(INFO DEBUG NOTICE).include?(first) then first else first.to_s + "_" + rest[0].to_s end
      level = LOG_LEVELS[level_label]
      label = first + "_" + rest.join("_")
      label = label.gsub("MILD_", '').gsub("SEVERE_", '').gsub("FATAL_", '').gsub("NOTICE_", 'NOTE_').gsub(/_\d+$/, '')
      MESSAGES_MAP[label] = level
      #puts "#{label}=#{level} #{token}"
    }
  end

end

# Launch all replacements defined in the REPLACEMENTS constant
#Replace.new.messages("src/messages/messages/admin.properties")
#Replace.new.run_messages
Replace.new.run
