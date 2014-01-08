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

  # All directories that contains java code
  JAVA_DIRS = ["src/server", "src/quicksetup", "src/ads", "src/guitools", "tests/unit-tests-testng/src"]

  # Replacement for Validator
  # Modify 88 files, for a total of 227 replacements - leaves 21 compilation errors
  VALIDATOR = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.util.\*;/,
        "import org.opends.server.util.*;\nimport org.forgerock.util.Reject;",

        /import static org.opends.server.util.Validator.ensureNotNull;/,
        'import static org.forgerock.util.Reject.ifNull;',

        /import static org.opends.server.util.Validator.ensureTrue;/,
        'import static org.forgerock.util.Reject.ifFalse;',

        /import static org.opends.server.util.Validator.\*;/,
        'import static org.forgerock.util.Reject.*;',

        /import org.opends.server.util.Validator;/,
        'import org.forgerock.util.Reject;',

        /(Validator\.| )ensureNotNull\((.*)$/,
        '\1ifNull(\2',

        /(Validator\.| )ensureTrue\((.*)$/,
        '\1ifFalse(\2',

        / Validator\./,
        ' Reject.'
      ]
  }

  # Replacement for messages
  # Modify 1052 files, for a total of 2366 replacements - leaves 10274 compilation errors mostly due to generated messages
  MESSAGES = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.messages.Message;/,
        'import org.forgerock.i18n.LocalizableMessage;',

        /([ <(])Message([ >)(.]|$)/,
        '\1LocalizableMessage\2',

        /import org.opends.messages.MessageBuilder;/,
        'import org.forgerock.i18n.LocalizableMessageBuilder;',

        /([ <(])MessageBuilder([ >)(.]|$)/,
        '\1LocalizableMessageBuilder\2'
      ]
  }

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

  BYTESTRING_TYPE = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /package org.opends.server.types;/,
        "package org.opends.server.types;\n\n" +
        "import org.forgerock.opendj.ldap.ByteString;\n" +
        "import org.forgerock.opendj.ldap.ByteStringBuilder;\n" +
        "import org.forgerock.opendj.ldap.ByteSequence;\n" +
        "import org.forgerock.opendj.ldap.ByteSequenceReader;",

        /import org.opends.server.types.\*;/,
        "import org.opends.server.types.*;\n" +
        "import org.forgerock.opendj.ldap.ByteString;\n" +
        "import org.forgerock.opendj.ldap.ByteStringBuilder;\n" +
        "import org.forgerock.opendj.ldap.ByteSequence;\n" +
        "import org.forgerock.opendj.ldap.ByteSequenceReader;",

        /import org.opends.server.types.(ByteString|ByteStringBuilder|ByteSequence|ByteSequenceReader);/,
        'import org.forgerock.opendj.ldap.\1;',

        /package org.opends.server.protocols.asn1;/,
        "package org.opends.server.protocols.asn1;\n\n" +
        "import com.forgerock.opendj.util.ByteSequenceOutputStream;",

        /import org.opends.server.protocols.asn1.ByteSequenceOutputStream;/,
        "import com.forgerock.opendj.util.ByteSequenceOutputStream;",

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
    :dirs => JAVA_DIRS,
    :stopwords => ['src/server/org/opends/server/loggers', 'DebugLogPublisher'],
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.loggers.debug.DebugTracer;/,
        "import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;",

        /import java.util.logging.Logger;/,
        "import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;",

        /import java.util.logging.Level;\n/,
        '',

        /import org.opends.server.types.DebugLogLevel;\n/,
        '',

        /import (static )?org.opends.server.loggers.debug.DebugLogger.*;\n/,
        '',

        /DebugTracer TRACER = (DebugLogger.)?getTracer\(\)/,
        "Logger debugLogger = LoggerFactory.getLogger({CLASSNAME}.class)",

        /^\s*\/\*\*\n.*The tracer object for the debug logger.\n\s*\*\/$\n/,
        '',

        /^\s*\/\/\s*The tracer object for the debug logger.$\n/,
        '',

        /if \(debugEnabled\(\)\)\s*{\s* TRACER.debugCaught\(DebugLogLevel.ERROR, (\b.*\b)\);\s*\n\s*}$/,
        'debugLogger.trace("Error", \1);',

        /TRACER\.debugCaught\(DebugLogLevel.ERROR, (\b.*\b)\);/,
        'debugLogger.trace("Error", \1);',

        /TRACER.debug[^(]+\(/,
        'debugLogger.trace(',

        /debugLogger.trace\(DebugLogLevel.\b\w+\b, ?/,
        'debugLogger.trace(',

        /debugLogger.trace\(e\)/,
        'debugLogger.trace("Error", e)',

        /(DebugLogger\.|\b)debugEnabled\(\)/,
        'debugLogger.isTraceEnabled()',

        /(LOG|logger).log\((Level.)?WARNING, ?/,
        '\1.warn(',

        /(LOG|logger).log\((Level.)?CONFIG, ?/,
        '\1.info(',

        /(LOG|logger).log\((Level.)?INFO, ?/,
        '\1.debug(',

        /(LOG|logger).log\((Level.)?SEVERE, ?/,
        '\1.error(',

        /(LOG|logger).log\((Level.)?FINE, ?/,
        '\1.trace(',

        /Logger.getLogger\((\n\s+)?(\b\w+\b).class.getName\(\)\);/,
        'LoggerFactory.getLogger(\2.class);',
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

  # List of replacements to run
  REPLACEMENTS = [ BYTESTRING_TYPE ]
  #REPLACEMENTS = [ VALIDATOR, MESSAGES, TYPES, EXCEPTIONS, LOGGERS, I18N_LOGGERS ]

  # Run replacements
  def run
    REPLACEMENTS.each { |repl|
      puts "Replacing " + Replace.constants.find{ |name| Replace.const_get(name)==repl }.to_s
      stopwords = repl[:stopwords] || ["--nostopword--"]
      replace_dirs(repl[:replacements], repl[:dirs], stopwords, repl[:extensions])
    }
  end

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

  def replace_file(file, replacements)
    count = 0
    File.open(file) { |source|
      contents = source.read
      (0..replacements.size-1).step(2).each { |index|
        pattern, replace = replacements[index], replacements[index+1]
        replace = replace.gsub('{CLASSNAME}', classname(file))
        is_replaced = contents.gsub!(pattern, replace)
        if is_replaced then count += 1 end
      }
      File.open(file + ".copy", "w+") { |f| f.write(contents) }
    }
    FileUtils.mv(file + ".copy", file, :verbose => false)
    count
  end

  def classname(file)
    name = file.gsub(/.*\/(.*).java$/, '\1')
    if name.nil? then '' else name end
  end

  def files_under_directory(directory, extensions)
    Dir[directory + '/**/*.{' + extensions.join(",") + '}']
  end

end

# Launch replacement
Replace.new.run

