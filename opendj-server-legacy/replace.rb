#!/usr/bin/env ruby

require 'fileutils'

#
# Automate code replacements using regular expressions
#
# To define a new replacement, add a new constant like VALIDATOR.
#
# It should be a ruby Hash with three mandatory keys and two optional key.
#
# The mandatory keys are:
#
#  :dirs => a list of directory to run replacements. All subdirs are processed.
#
#  :extensions => a list of file extensions. Only file with these extensions are processed.
#
#  :replacements => a list of replacements, lines are processed 2 by 2
#    - first line gives the pattern to replace, as a ruby regexp (see http://rubular.com/ for help and tool)
#    - second line gives the replacement string, using \1, \2, ... to insert matching groups. This is a string,
#       use simple quote if no special char is inserted, or use double quote if using special char like \n
#    Don't forget to put a comma at end of each line, this is the array element separator.
#    It is ok to leave a new line to separate each pair of line for readability.
#    It is ok to use a comment in the array (use # as first non blank character of line).
#
# The optional keys are:
#
#   :whitelist => a list of mandatory words. If any word in this list appears in a file name, the file
#       is processed, otherwise it is ignored. Use it to explicitely indicates files to process.
#
#   :stoplist => a list of stop words. If any word in this list appears in a file name, the file
#       is not processed. Use it to exclude some files or directory that must not be processed.
#
#   Note that if you use both whitelist and stoplist, a word in stoplist can prevent processing a file even if it
#   matches the whitelist content.
#
# Once you have define your replacement, add the constant in REPLACEMENTS array. it will be taken into account when
# running the program (run it at root of project) with command: ./replace.rb
#
class Replace

  # All directories that contains java code
  JAVA_DIRS = ["src/main/java", "src/test/java"]
  TOOLS_DIR = ["src/server/org/opends/server/tools", "src/quicksetup", "src/ads", "src/guitools",
    "tests/unit-tests-testng/src/server/org/opends/server/tools" ]
  SNMP_DIR = ["src/snmp/src"]
  DSML_DIR = ["src/dsml/org"]

  # Replacement for syntaxes
  SYNTAXES_TO_SDK = {
    :dirs => JAVA_DIRS + SNMP_DIR,
    :extensions => ["java"],
    :stoplist => ["Syntax"],
    :replacements =>
      [
        /import org.opends.server.api.AttributeSyntax;/,
        'import org.forgerock.opendj.ldap.schema.Syntax;',

        /package org.opends.server.api;/,
        "package org.opends.server.api;\n\nimport org.forgerock.opendj.ldap.schema.Syntax;",

        /import org.opends.server.api.\*;/,
        "import org.forgerock.opendj.ldap.schema.Syntax;\nimport org.opends.server.api.*;",

        /\bAttributeSyntax\b(<AttributeSyntaxCfg>)/,
        "Syntax",

        /\bAttributeSyntax\b(<\w*>)?/,
        "Syntax",

        /\bSyntax\b<\w*>/,
        "Syntax",

        /\bSyntax\b<\?>/,
        "Syntax",

      ]
  }
  
  SYNTAXES_TO_SDK_SCM = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :whitelist => ["SchemaConfigManager"],
    :replacements =>
      [
        /\b\w*Syntax\b\s+(\w+Syntax);/,
        'Syntax \1;',
        
        /\((\w*Syntax)\)\s*schema.getSyntax/m,
        'schema.getSyntax',
        
        /\w*Syntax.initializeSyntax\(null\);\s/,
        ''
      ]
  }

  # Replacement for matching rules
  MRULES_TO_SDK = {
    :dirs => JAVA_DIRS + SNMP_DIR,
    :extensions => ["java"],
    :stoplist => ["MatchingRule"],
    :replacements =>
      [
        /import org.opends.server.api.MatchingRule;/,
        'import org.forgerock.opendj.ldap.schema.MatchingRule;',

      ]
  }

  MRULES_FACTORIES = {
    :dirs => ["src/server/org/opends/server/schema"],
    :extensions => ["java"],
    :replacements =>
      [
        /import org.opends.server.api.MatchingRule;/,
        'import org.forgerock.opendj.ldap.schema.MatchingRule;',

        /private MatchingRule matchingRule;/,
        "private org.forgerock.opendj.ldap.schema.MatchingRule matchingRule;",

        /public final Collection<MatchingRule> getMatchingRules\(\)/,
        "public final Collection<org.forgerock.opendj.ldap.schema.MatchingRule> getMatchingRules()",

        /public final void initializeMatchingRule\(MatchingRuleCfg configuration\)/,
        "public final void initializeMatchingRule(ServerContext serverContext, MatchingRuleCfg configuration)",

        /\bmatchingRule = new \w*MatchingRule\(\);/,
        "matchingRule = serverContext.getSchemaNG().getMatchingRule(EMR_);"

       ]
  }

  MRULES_API_PACKAGE = {
    :dirs => ["src/server/org/opends/server/api"],
    :extensions => ["java"],
    :stoplist => ["MatchingRule.java"],
    :replacements =>
      [
        /\bMatchingRule\b/,
        "org.forgerock.opendj.ldap.schema.MatchingRule",
      ]
  }

  MRULES = {
    :dirs => JAVA_DIRS + SNMP_DIR,
    :extensions => ["java"],
    :stoplist => ["MatchingRule"],
    :replacements =>
      [

        /import org.opends.server.api.SubstringMatchingRule;/,
        '',

        /\bSubstringMatchingRule\b/,
        "MatchingRule",

       ]
  }


 # Replacement for attribute type
  ATTRTYPE = {
    :dirs => JAVA_DIRS + SNMP_DIR,
    :extensions => ["java"],
    :stoplist => [],
    :replacements =>
      [

        /import org.opends.server.types.AttributeType;/,
        'import org.forgerock.opendj.ldap.schema.AttributeType;',

        /package org.opends.server.types;/,
        "package org.opends.server.types;\n\nimport org.forgerock.opendj.ldap.schema.AttributeType;",

        /import org.opends.server.types.\*;/,
        "import org.forgerock.opendj.ldap.schema.AttributeType;\nimport org.opends.server.types.*;",

       ]
  }

  # Replacement for new config framework
  NEW_CONFIG = {
    :dirs => JAVA_DIRS + SNMP_DIR,
    :extensions => ["java"],
    :stoplist => ["org/opends/server/admin", "api/Config", "MatchingRuleConfigManager"],
    :replacements =>
      [
        /import org.opends.server.admin.std.server\.([^;]+);/,
        'import org.forgerock.opendj.server.config.server.\1;',

        /import org.opends.server.admin.std.meta\.([^;]+);/,
        'import org.forgerock.opendj.server.config.meta.\1;',

        /import org.opends.server.admin.std.client\.([^;]+);/,
        'import org.forgerock.opendj.server.config.client.\1;',

        /import org.opends.server.admin.client\.(\w+);/,
        'import org.forgerock.opendj.config.client.\1;',

        /import org.opends.server.admin.client.ldap\.(\w+);/,
        'import org.forgerock.opendj.config.client.ldap.\1;',

        /import org.opends.server.admin.client.spi\.(\w+);/,
        'import org.forgerock.opendj.config.client.spi.\1;',

        /import org.opends.server.admin.server\.([^;]+);/,
        'import org.forgerock.opendj.config.server.\1;',

        /import org.opends.server.admin\.(\w+);/,
        'import org.forgerock.opendj.config.\1;',

        /import org.forgerock.opendj.config.client.AuthorizationException;/,
        'import org.forgerock.opendj.ldap.LdapException;',

        /import org.forgerock.opendj.config.client.CommunicationException;$/,
        '',

        /catch \(AuthorizationException e\)/,
        'catch (LdapException e)',

        /catch \(CommunicationException e\)/,
        'catch (LdapException e)',

        # Now bring back removed imports that have no replacement
        /import org.forgerock.opendj.config.client.ldap.JNDIDirContextAdaptor;/,
        'import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;',

        /import org.forgerock.opendj.config.AdministrationConnector;/,
        'import org.opends.server.admin.AdministrationConnector;',

        /import org.forgerock.opendj.config.AdministrationDataSync;/,
        'import org.opends.server.admin.AdministrationDataSync;',

        /import org.forgerock.opendj.config.ClassLoaderProvider;/,
        'import org.forgerock.opendj.config.ConfigurationFramework;',

        /import org.opends.server.types.ConfigChangeResult;/,
        'import org.forgerock.opendj.config.server.ConfigChangeResult;',

        /public ConfigChangeResult\s/,
        "public org.forgerock.opendj.config.server.ConfigChangeResult ",

        /new ConfigChangeResult\(/,
        "new org.forgerock.opendj.config.server.ConfigChangeResult(",

        /rdn\(\).getAttributeValue\(0\).getValue\(\).toString\(\)/,
        'rdn().getFirstAVA().getAttributeValue().toString()',

        /^(\s+)ServerManagementContext \w+\s*=\s*ServerManagementContext\s*.getInstance\(\);/m,
        '',

        /^(\s+)RootCfg (\w+)\s+=\s+\w+\.getRootConfiguration\(\);/m,
        '\1RootCfg \2 = serverContext.getServerManagementContext().getRootConfiguration();',

        /^(\s+)RootCfg (\w+)\s+=\s+ServerManagementContext.getInstance\(\)\.getRootConfiguration\(\);/m,
        '\1RootCfg \2 = serverContext.getServerManagementContext().getRootConfiguration();',

        #/(config|configuration|cfg|currentConfig)\.dn\(\)/,
        #'org.forgerock.opendj.adapter.server3x.Converters.to(\1.dn())',

        /(\b\w+\b)\.dn\(\)/,
        'org.forgerock.opendj.adapter.server3x.Converters.to(\1.dn())',

        /(config|configuration|cfg|currentConfig|configEntry|pluginCfg)\.get(\w+)(DN|DNs|Subtrees)\(\)/,
        'org.forgerock.opendj.adapter.server3x.Converters.to(\1.get\2\3())',

        /^(\s+)ConfigChangeResult (\b\w+\b);/,
        '\1org.forgerock.opendj.config.server.ConfigChangeResult \2;',

        /(\s+)AttributeType (\w+) = (configuration|config|cfg|\w+Cfg).get(\w+)Attribute\(\);/,
        '\1AttributeType \2 = \3.get\4Attribute();',

        /^(\s+)public DN dn\(\)/,
        '\1public org.forgerock.opendj.ldap.DN dn()',

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
        /(logger\.\s*(?:trace|debug|warn|info|error)\s*\([^;]*)\s*\.toString\(\)/m,
        '\1',
      ]
  }

  LOGGER_STRING_VALUEOF = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        # Need to fix removing the last parentheses
        /(logger\.\s*(?:trace|debug|warn|info|error)\s*\([^;]*)\s*String\s*\.\s*valueOf\s*\(/m,
        '\1',
      ]
  }

  LOGGER_MSG_ARGN_PRIMITIVE_TOSTRING = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /(logger\.\s*(?:trace|debug|warn|info|error)\s*\([^;]*)\s*(Character|Byte|Boolean|Short|Integer|Long|Float|Double)\s*\.\s*toString\s*\(/m,
        '\1',
        # Need to fix removing the last parentheses
        /([A-Z0-9_]+\s*\.\s*get\s*\([^;]*)\s*(Character|Byte|Boolean|Short|Integer|Long|Float|Double)\s*\.\s*toString\s*\(/m,
        '\1',
      ]
  }

  LOGGER_AND_ARGN_TO_LOGGER_ONLY = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /(logger\.\s*(?:trace|debug|warn|info|error)\s*\()\s*([A-Z0-9_]+)\s*\.\s*get\s*\(([^;]*)\)([^;]+)/m,
        '\1\2, \3\4',
        /(logger\.\s*(?:trace|debug|warn|info|error)\s*\()\s*([A-Z0-9_]+)\s*\.\s*get\s*\(([^;]*)\)([^;]+)/m,
        '\1\2, \3\4',
      ]
  }

  COLLAPSE_LOCALIZABLE_MESSAGE_TO_LOGGER_ONLY = {
    :dirs => JAVA_DIRS,
    :extensions => ["java"],
    :replacements =>
      [
        /(?:final)?\s*LocalizableMessage\s*(\w+)\s*=\s*((?:[^;]|\r\n|\r|\n)+);\s*(logger\s*\.(?:trace|debug|warn|info|error)\s*\()\s*\1/m,
        '\3\2',
        /(?: |\t)+$/m,
        '',
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

  ###############################  List of replacements to run #################################

  REPLACEMENTS = [ SYNTAXES_TO_SDK, SYNTAXES_TO_SDK_SCM ]

  ################################### Processing methods ########################################

  # Main method : run replacements defined in REPLACEMENTS constant
  def run
    REPLACEMENTS.each { |repl|
      puts "Replacing " + Replace.constants.find{ |name| Replace.const_get(name)==repl }.to_s
      stoplist = repl[:stoplist] || []
      whitelist = repl[:whitelist] || []
      replace_dirs(repl[:replacements], repl[:dirs], stoplist, whitelist, repl[:extensions])
    }
  end

  # Process replacements on the provided directories
  def replace_dirs(replacements, dirs, stoplist, whitelist, extensions)
    count_files = 0
    count_total = 0
    dirs.each { |directory|
      files = files_under_directory(directory, extensions)
      files.each { |file|
        filename_has_stopword = stoplist.any? { |stopword| file.include?(stopword) }
        filename_has_whiteword = whitelist.any? { |whiteword| file.include?(whiteword) }
        next if filename_has_stopword || (!whitelist.empty? && !filename_has_whiteword)
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
        #while is_replaced
          #puts "pattern: " + pattern.to_s
          is_replaced = contents.gsub!(pattern, replace)
          if is_replaced then count += 1 end
        #end
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
  def process_dirs(dirs, stoplist, whitelist, extensions)
    count_files = 0
    count_total = 0
    dirs.each { |directory|
      files = files_under_directory(directory, extensions)
      files.each { |file|
        filename_has_stopword = stoplist.any? { |stopword| file.include?(stopword) }
        filename_has_whiteword = whitelist.any? { |whiteword| file.include?(whiteword) }
        next if filename_has_stopword || (!whitelist.empty? && !filename_has_whiteword)
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

end

# Launch all replacements defined in the REPLACEMENTS constant
Replace.new.run
