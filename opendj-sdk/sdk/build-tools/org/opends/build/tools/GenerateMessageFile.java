/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.build.tools;



import static org.opends.build.tools.Utilities.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;



/**
 * Generates a Java class containing representations of messages found
 * in a properties file.
 */
public class GenerateMessageFile extends Task
{
  /**
   * The maximum number of arguments that can be handled by a specific
   * subclass. If you define more subclasses be sure to increment this
   * number appropriately.
   */
  static public final int DESCRIPTOR_MAX_ARG_HANDLER = 11;

  /**
   * The base name of the specific argument handling subclasses defined
   * below. The class names consist of the base name followed by a
   * number indicating the number of arguments that they handle when
   * creating messages or the letter "N" meaning any number of
   * arguments.
   */
  public static final String DESCRIPTOR_CLASS_BASE_NAME = "Arg";

  private File source;

  private File dest;

  private boolean overwrite;

  static private final String MESSAGES_FILE_STUB = "resource/Messages.java.stub";

  /**
   * When true generates messages that have no ordinals.
   */
  static private final String GLOBAL_ORDINAL = "global.ordinal";

  static private final Set<String> DIRECTIVE_PROPERTIES = new HashSet<String>();
  static
  {
    DIRECTIVE_PROPERTIES.add(GLOBAL_ORDINAL);
  }

  static private final String SPECIFIER_REGEX = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

  private final Pattern SPECIFIER_PATTERN = Pattern
      .compile(SPECIFIER_REGEX);

  /**
   * Message giving formatting rules for string keys.
   */
  static public String KEY_FORM_MSG;

  static
  {
    KEY_FORM_MSG = new StringBuilder().append(
        ".\n\nOpenDS message property keys must be of the form\n\n")
        .append("\t\'[DESCRIPTION]_[ORDINAL]\'\n\n")
        .append("where\n\n").append(
            "\n\nDESCRIPTION is a descriptive string composed ")
        .append("of uppercase character, digits and underscores ")
        .append("describing the purpose of the message ").append(
            "\n\nORDINAL is an integer between 0 and 65535 that is ")
        .append("unique to other messages defined in this file.\n\n")
        .toString();
  }

  /*
   * ISO_LANGUAGES contains all official supported languages for i18n
   */
  private static final List<String> ISO_LANGUAGES = Arrays
      .asList(Locale.getISOLanguages());

  /*
   * ISO_COUNTRIES contains all official supported countries for i18n
   */
  private static final List<String> ISO_COUNTRIES = Arrays
      .asList(Locale.getISOCountries());

  /*
   * A Pattern instance that matches
   * "<label>_<language>_<country>.properties" where <label> can be
   * anything including '_' <language> a two characters code contained
   * in the ISO_LANGUAGES list <country> a two characters code contained
   * in the ISO_COUNTRIES list
   */
  private static final Pattern LANGUAGE_COUNTRY_MATCHER = Pattern
      .compile("(.*)_([a-z]{2})_([A-Z]{2}).properties");

  /*
   * A Pattern instance that matches "<label>_<language>.properties"
   * where <label> and <language> have same definition as above.
   */
  private static final Pattern LANGUAGE_MATCHER = Pattern
      .compile("(.*)_([a-z]{2}).properties");



  /**
   * Representation of a format specifier (for example %s).
   */
  private class FormatSpecifier
  {

    private String[] sa;



    /**
     * Creates a new specifier.
     *
     * @param sa
     *          specifier components
     */
    FormatSpecifier(String[] sa)
    {
      this.sa = sa;
    }



    /**
     * Indicates whether or not the specifier uses arguement indexes
     * (for example 2$).
     *
     * @return boolean true if this specifier uses indexing
     */
    public boolean specifiesArgumentIndex()
    {
      return this.sa[0] != null;
    }



    /**
     * Returns a java class associated with a particular formatter based
     * on the conversion type of the specifier.
     *
     * @return Class for representing the type of arguement used as a
     *         replacement for this specifier.
     */
    public Class<?> getSimpleConversionClass()
    {
      Class<?> c = null;
      String sa4 = sa[4] != null ? sa[4].toLowerCase() : null;
      String sa5 = sa[5] != null ? sa[5].toLowerCase() : null;
      if ("t".equals(sa4))
      {
        c = Calendar.class;
      }
      else if ("b".equals(sa5))
      {
        c = Boolean.class;
      }
      else if ("h".equals(sa5))
      {
        c = Integer.class;
      }
      else if ("s".equals(sa5))
      {
        c = CharSequence.class;
      }
      else if ("c".equals(sa5))
      {
        c = Character.class;
      }
      else if ("d".equals(sa5) || "o".equals(sa5) || "x".equals(sa5)
          || "e".equals(sa5) || "f".equals(sa5) || "g".equals(sa5)
          || "a".equals(sa5))
      {
        c = Number.class;
      }
      else if ("n".equals(sa5) || "%".equals(sa5))
      {
        // ignore literals
      }
      return c;
    }

  }



  /**
   * Represents a message to be written into the messages files.
   */
  private class MessageDescriptorDeclaration
  {

    private MessagePropertyKey key;

    private String formatString;

    private List<FormatSpecifier> specifiers;

    private List<Class<?>> classTypes;

    private String[] constructorArgs;



    /**
     * Creates a parameterized instance.
     *
     * @param key
     *          of the message
     * @param formatString
     *          of the message
     */
    public MessageDescriptorDeclaration(MessagePropertyKey key,
        String formatString)
    {
      this.key = key;
      this.formatString = formatString;
      this.specifiers = parse(formatString);
      this.classTypes = new ArrayList<Class<?>>();
      for (FormatSpecifier f : specifiers)
      {
        Class<?> c = f.getSimpleConversionClass();
        if (c != null)
        {
          classTypes.add(c);
        }
      }
    }



    /**
     * Gets the name of the Java class that will be used to represent
     * this message's type.
     *
     * @return String representing the Java class name
     */
    public String getDescriptorClassDeclaration()
    {
      StringBuilder sb = new StringBuilder();
      if (useGenericMessageTypeClass())
      {
        sb.append("MessageDescriptor");
        sb.append(".");
        sb.append(DESCRIPTOR_CLASS_BASE_NAME);
        sb.append("N");
      }
      else
      {
        sb.append("MessageDescriptor");
        sb.append(".");
        sb.append(DESCRIPTOR_CLASS_BASE_NAME);
        sb.append(classTypes.size());
        sb.append(getClassTypeVariables());
      }
      return sb.toString();
    }



    /**
     * Gets a string representing the message type class' variable
     * information (for example '<String,Integer>') that is based on the
     * type of arguments specified by the specifiers in this message.
     *
     * @return String representing the message type class parameters
     */
    public String getClassTypeVariables()
    {
      StringBuilder sb = new StringBuilder();
      if (classTypes.size() > 0)
      {
        sb.append("<");
        for (int i = 0; i < classTypes.size(); i++)
        {
          Class<?> c = classTypes.get(i);
          if (c != null)
          {
            sb.append(getShortClassName(c));
            if (i < classTypes.size() - 1)
            {
              sb.append(",");
            }
          }
        }
        sb.append(">");
      }
      return sb.toString();
    }



    /**
     * Gets the javadoc comments that will appear above the messages
     * declaration in the messages file.
     *
     * @return String comment
     */
    public String getComment()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(indent(1)).append("/**").append(EOL);

      // Unwrapped so that you can search through the descriptor
      // file for a message and not have to worry about line breaks
      String ws = formatString; // wrapText(formatString, 70);

      String[] sa = ws.split(EOL);
      for (String s : sa)
      {
        sb.append(indent(1)).append(" * ").append(s).append(EOL);
      }
      sb.append(indent(1)).append(" */").append(EOL);
      return sb.toString();
    }



    /**
     * Sets the arguments that will be supplied in the declaration of
     * the message.
     *
     * @param s
     *          array of string arguments that will be passed in the
     *          constructor
     */
    public void setConstructorArguments(String... s)
    {
      this.constructorArgs = s;
    }



    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(getComment());
      sb.append(indent(1));
      sb.append("public static final ");
      sb.append(getDescriptorClassDeclaration());
      sb.append(" ");
      sb.append(key.getMessageDescriptorName());
      sb.append(" =");
      sb.append(EOL);
      sb.append(indent(5));
      sb.append("new ");
      sb.append(getDescriptorClassDeclaration());
      sb.append("(");
      if (constructorArgs != null)
      {
        for (int i = 0; i < constructorArgs.length; i++)
        {
          sb.append(constructorArgs[i]);
          if (i < constructorArgs.length - 1)
          {
            sb.append(",");
          }
        }
        sb.append(", ");
      }
      sb.append("getClassLoader()");
      sb.append(");");
      return sb.toString();
    }



    /**
     * Indicates whether the generic message type class should be used.
     * In general this is when a format specifier is more complicated
     * than we support or when the number of arguments exceeeds the
     * number of specific message type classes (MessageType0,
     * MessageType1 ...) that are defined.
     *
     * @return boolean indicating
     */
    private boolean useGenericMessageTypeClass()
    {
      if (specifiers.size() > DESCRIPTOR_MAX_ARG_HANDLER)
      {
        return true;
      }
      else if (specifiers != null)
      {
        for (FormatSpecifier s : specifiers)
        {
          if (s.specifiesArgumentIndex())
          {
            return true;
          }
        }
      }
      return false;
    }



    /**
     * Look for format specifiers in the format string.
     *
     * @param s
     *          format string
     * @return list of format specifiers
     */
    private List<FormatSpecifier> parse(String s)
    {
      List<FormatSpecifier> sl = new ArrayList<FormatSpecifier>();
      Matcher m = SPECIFIER_PATTERN.matcher(s);
      int i = 0;
      while (i < s.length())
      {
        if (m.find(i))
        {
          // Anything between the start of the string and the beginning
          // of the format specifier is either fixed text or contains
          // an invalid format string.
          if (m.start() != i)
          {
            // Make sure we didn't miss any invalid format specifiers
            checkText(s.substring(i, m.start()));
            // Assume previous characters were fixed text
            // al.add(new FixedString(s.substring(i, m.start())));
          }

          // Expect 6 groups in regular expression
          String[] sa = new String[6];
          for (int j = 0; j < m.groupCount(); j++)
          {
            sa[j] = m.group(j + 1);
          }
          sl.add(new FormatSpecifier(sa));
          i = m.end();
        }
        else
        {
          // No more valid format specifiers. Check for possible invalid
          // format specifiers.
          checkText(s.substring(i));
          // The rest of the string is fixed text
          // al.add(new FixedString(s.substring(i)));
          break;
        }
      }
      return sl;
    }



    private void checkText(String s)
    {
      int idx;
      // If there are any '%' in the given string, we got a bad format
      // specifier.
      if ((idx = s.indexOf('%')) != -1)
      {
        char c = (idx > s.length() - 2 ? '%' : s.charAt(idx + 1));
        throw new UnknownFormatConversionException(String.valueOf(c));
      }
    }

  }



  /**
   * Sets the source of the messages.
   *
   * @param source
   *          File representing the properties file containing messages
   */
  public void setSourceProps(File source)
  {
    this.source = source;
  }



  /**
   * Sets the file that will be generated containing declarations of
   * messages from <code>source</code>.
   *
   * @param dest
   *          File destination
   */
  public void setDestJava(File dest)
  {
    this.dest = dest;
  }



  /**
   * Indicates when true that an existing destination file will be
   * overwritten.
   *
   * @param o
   *          boolean where true means overwrite
   */
  public void setOverwrite(boolean o)
  {
    this.overwrite = o;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void execute() throws BuildException
  {

    if (this.dest == null)
    {
      // this is an example-plugin call:
      // - check the source file is not a localization
      // - guess the destination filename from source filename
      String sourcefilename = source.getAbsolutePath();
      int filenameIndex = sourcefilename.lastIndexOf(File.separator) + 1;
      String pathname = sourcefilename.substring(0, filenameIndex);
      String filename = sourcefilename.substring(filenameIndex);

      /*
       * Make sure only <label>.properties are generated thus avoiding
       * to generate messages for localized properties files.
       */
      Matcher matcher = LANGUAGE_COUNTRY_MATCHER.matcher(filename);
      if (matcher.find())
      {
        if (ISO_LANGUAGES.contains(matcher.group(2))
            && ISO_COUNTRIES.contains(matcher.group(3)))
        {
          // do not generate message for
          // <label>_<language>_<country>.properties
          return;
        }
      }

      matcher = LANGUAGE_MATCHER.matcher(filename);
      if (matcher.find())
      {
        if (ISO_LANGUAGES.contains(matcher.group(2)))
        {
          // do not generate message for <label>_<language>.properties
          return;
        }
      }
      // filename without ".properties"
      filename = filename.substring(0, filename.length() - 11);
      // change to src-generated directory keeping package name
      pathname = pathname.replace(getProject().getProperty("src.dir"),
          getProject().getProperty("srcgen.dir"));

      // append characters from filename to pathname starting with an
      // uppercase letter, ignoring '_' and uppering all characters
      // prefixed with "_"
      StringBuilder sb = new StringBuilder(pathname);
      boolean upperCaseNextChar = true;
      for (char c : filename.toCharArray())
      {
        if (c == '_')
        {
          upperCaseNextChar = true;
          continue;
        }
        if (upperCaseNextChar)
        {
          sb.append(Character.toUpperCase(c));
          upperCaseNextChar = false;
        }
        else
        {
          sb.append(c);
        }
      }
      sb.append("Messages.java");

      setDestJava(new File(sb.toString()));
    }

    BufferedReader stubReader = null;
    PrintWriter destWriter = null;
    try
    {

      // Decide whether to generate messages based on modification
      // times and print status messages.
      if (!source.exists())
      {
        throw new BuildException("file " + source.getName()
            + " does not exist");
      }
      if (dest.exists())
      {
        if (this.overwrite
            || source.lastModified() > dest.lastModified())
        {
          dest.delete();
          log("Regenerating " + dest.getName() + " from "
              + source.getName());
        }
        else
        {
          log(dest.getName() + " is up to date");
          return;
        }
      }
      else
      {
        File javaGenDir = dest.getParentFile();
        if (!javaGenDir.exists())
        {
          javaGenDir.mkdirs();
        }
        log("Generating " + dest.getName() + " from "
            + source.getName());
      }

      stubReader = new BufferedReader(new InputStreamReader(
          getStubFile(), "UTF-8"));
      destWriter = new PrintWriter(dest, "UTF-8");
      String stubLine;
      Properties properties = new Properties();
      properties.load(new FileInputStream(source));
      while (null != (stubLine = stubReader.readLine()))
      {
        if (stubLine.contains("${MESSAGES}"))
        {
          Integer globalOrdinal = null;
          String go = properties.getProperty(GLOBAL_ORDINAL);
          if (go != null)
          {
            globalOrdinal = new Integer(go);
          }

          Map<MessagePropertyKey, String> keyMap = new TreeMap<MessagePropertyKey, String>();

          for (Object propO : properties.keySet())
          {
            String propKey = propO.toString();
            try
            {
              if (!DIRECTIVE_PROPERTIES.contains(propKey))
              {
                MessagePropertyKey key = MessagePropertyKey
                    .parseString(propKey, globalOrdinal == null);
                String formatString = properties.getProperty(propKey);
                keyMap.put(key, formatString);
              }
            }
            catch (IllegalArgumentException iae)
            {
              throw new BuildException("ERROR: invalid property key "
                  + propKey + ": " + iae.getMessage() + KEY_FORM_MSG);
            }
          }

          int usesOfGenericDescriptor = 0;

          Set<Integer> usedOrdinals = new HashSet<Integer>();
          for (MessagePropertyKey key : keyMap.keySet())
          {
            String formatString = keyMap.get(key);
            MessageDescriptorDeclaration message = new MessageDescriptorDeclaration(
                key, formatString);

            if (globalOrdinal == null)
            {
              Integer ordinal = key.getOrdinal();
              if (usedOrdinals.contains(ordinal))
              {
                throw new BuildException("The ordinal value \'"
                    + ordinal + "\' in key " + key
                    + " has been previously defined in " + source
                    + KEY_FORM_MSG);
              }
              else
              {
                usedOrdinals.add(ordinal);
              }
            }

            message.setConstructorArguments("BASE", quote(key
                .toString()), globalOrdinal != null ? globalOrdinal
                .toString() : key.getOrdinal().toString());
            destWriter.println(message.toString());
            destWriter.println();

            // Keep track of when we use the generic descriptor
            // so that we can report it later
            if (message.useGenericMessageTypeClass())
            {
              usesOfGenericDescriptor++;
            }
          }

          log("  Message Generated:" + keyMap.size(),
              Project.MSG_VERBOSE);
          log("  MessageDescriptor.ArgN:" + usesOfGenericDescriptor,
              Project.MSG_VERBOSE);

        }
        else
        {
          stubLine = stubLine.replace("${PACKAGE}", getPackage());
          stubLine = stubLine
              .replace("${CLASS_NAME}", dest.getName().substring(0,
                  dest.getName().length() - ".java".length()));
          stubLine = stubLine.replace("${BASE}", getBase());
          destWriter.println(stubLine);
        }
      }

      stubReader.close();
      destWriter.close();
    }
    catch (Exception e)
    {
      // Don't leave a malformed file laying around. Delete
      // it so it will be forced to be regenerated.
      if (dest.exists())
      {
        dest.deleteOnExit();
      }
      e.printStackTrace();
      throw new BuildException("Error processing " + source + ":  "
          + e.getMessage());
    }
    finally
    {
      if (stubReader != null)
      {
        try
        {
          stubReader.close();
        }
        catch (Exception e)
        {
          // ignore
        }
      }
      if (destWriter != null)
      {
        try
        {
          destWriter.close();
        }
        catch (Exception e)
        {
          // ignore
        }
      }
    }
  }



  private String getBase()
  {
    String srcDir = unixifyPath(getProject().getProperty("src.dir"));
    String srcPath = unixifyPath(source.getAbsolutePath());

    String relativeSrcPath = srcPath.substring(srcDir.length() + 1,
        srcPath.length() - ".properties".length());
    String base = relativeSrcPath.replace('/', '.');
    return base;
  }



  private String getPackage()
  {
    String destPath = unixifyPath(dest.getAbsolutePath());
    String msgJavaGenDir = unixifyPath(getProject().getProperty(
        "srcgen.dir"));
    String c = destPath.substring(msgJavaGenDir.length() + 1);
    c = c.replace('/', '.');
    c = c.substring(0, c.lastIndexOf(".")); // strip .java
    c = c.substring(0, c.lastIndexOf(".")); // strip class name
    return c;
  }



  static private String indent(int indent)
  {
    char[] blankArray = new char[2 * indent];
    Arrays.fill(blankArray, ' ');
    return new String(blankArray);
  }



  static private String quote(String s)
  {
    return new StringBuilder().append("\"").append(s).append("\"")
        .toString();
  }



  static private String getShortClassName(Class<?> c)
  {
    String name;
    String fqName = c.getName();
    int i = fqName.lastIndexOf('.');
    if (i > 0)
    {
      name = fqName.substring(i + 1);
    }
    else
    {
      name = fqName;
    }
    return name;
  }



  private File getProjectBase()
  {
    File projectBase;

    // Get the path to build.xml and return the parent
    // directory else just return the working directory.
    Location l = getLocation();
    String fileName = l.getFileName();
    if (fileName != null)
    {
      File f = new File(fileName);
      projectBase = f.getParentFile();
    }
    else
    {
      projectBase = new File(System.getProperty("user.dir"));
    }

    return projectBase;
  }



  private String unixifyPath(String path)
  {
    return path.replace("\\", "/");
  }



  /*
   * Returns the stub file ("resource/Messages.java.stub") from the
   * appropriate location: ant or jar file.
   */
  private InputStream getStubFile()
  {
    InputStream result = null;

    File stub = new File(getProjectBase(), MESSAGES_FILE_STUB);
    if (stub.exists())
    {
      // this is the OpenDS's ant project calling
      // Stub is located at OPENDS_ROOT/resource/Messages.java.stub
      try
      {
        result = new FileInputStream(stub);
      }
      catch (FileNotFoundException e)
      {
        // should never happen
        throw new BuildException("Unable to load template "
            + MESSAGES_FILE_STUB + ":  " + e.getMessage());
      }
    }
    else
    {
      // this is the example plugin's ant project calling
      // Stub is located at build-tools.jar:resource/Messages.java.stub
      result = getClass().getResourceAsStream(MESSAGES_FILE_STUB);
    }

    return result;
  }

}
