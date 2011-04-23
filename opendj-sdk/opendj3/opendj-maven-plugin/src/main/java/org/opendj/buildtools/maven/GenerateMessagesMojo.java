/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2011 ForgeRock AS
 */
package org.opendj.buildtools.maven;



import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;



/**
 * Goal which generates message source files from a one or more property files.
 * 
 * @goal generatemessages
 * @phase generate-sources
 * @requiresDependencyResolution compile
 * @threadSafe
 */
public class GenerateMessagesMojo extends AbstractMojo
{
  /**
   * Representation of a format specifier (for example %s).
   */
  private class FormatSpecifier
  {

    private final String[] sa;



    /**
     * Creates a new specifier.
     * 
     * @param sa
     *          specifier components
     */
    FormatSpecifier(final String[] sa)
    {
      this.sa = sa;
    }



    /**
     * Returns a java class associated with a particular formatter based on the
     * conversion type of the specifier.
     * 
     * @return Class for representing the type of argument used as a replacement
     *         for this specifier.
     */
    Class<?> getSimpleConversionClass()
    {
      Class<?> c = null;
      final String sa4 = sa[4] != null ? sa[4].toLowerCase() : null;
      final String sa5 = sa[5] != null ? sa[5].toLowerCase() : null;
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



    /**
     * Indicates whether or not the specifier uses argument indexes (for example
     * 2$).
     * 
     * @return boolean true if this specifier uses indexing
     */
    boolean specifiesArgumentIndex()
    {
      return this.sa[0] != null;
    }

  }



  /**
   * Represents a message to be written into the messages files.
   */
  private class MessageDescriptorDeclaration
  {

    private final MessagePropertyKey key;

    private final String formatString;

    private final List<FormatSpecifier> specifiers;

    private final List<Class<?>> classTypes;

    private String[] constructorArgs;



    /**
     * Creates a parameterized instance.
     * 
     * @param key
     *          of the message
     * @param formatString
     *          of the message
     */
    MessageDescriptorDeclaration(final MessagePropertyKey key,
        final String formatString)
    {
      this.key = key;
      this.formatString = formatString;
      this.specifiers = parse(formatString);
      this.classTypes = new ArrayList<Class<?>>();
      for (final FormatSpecifier f : specifiers)
      {
        final Class<?> c = f.getSimpleConversionClass();
        if (c != null)
        {
          classTypes.add(c);
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      final StringBuilder sb = new StringBuilder();
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
     * Gets a string representing the message type class' variable information
     * (for example '<String,Integer>') that is based on the type of arguments
     * specified by the specifiers in this message.
     * 
     * @return String representing the message type class parameters
     */
    String getClassTypeVariables()
    {
      final StringBuilder sb = new StringBuilder();
      if (classTypes.size() > 0)
      {
        sb.append("<");
        for (int i = 0; i < classTypes.size(); i++)
        {
          final Class<?> c = classTypes.get(i);
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
     * Gets the javadoc comments that will appear above the messages declaration
     * in the messages file.
     * 
     * @return String comment
     */
    String getComment()
    {
      final StringBuilder sb = new StringBuilder();
      sb.append(indent(1)).append("/**").append(EOL);

      // Unwrapped so that you can search through the descriptor
      // file for a message and not have to worry about line breaks
      final String ws = formatString; // wrapText(formatString, 70);

      final String[] sa = ws.split(EOL);
      for (final String s : sa)
      {
        sb.append(indent(1)).append(" * ").append(s).append(EOL);
      }
      sb.append(indent(1)).append(" */").append(EOL);
      return sb.toString();
    }



    /**
     * Gets the name of the Java class that will be used to represent this
     * message's type.
     * 
     * @return String representing the Java class name
     */
    String getDescriptorClassDeclaration()
    {
      final StringBuilder sb = new StringBuilder();
      if (useGenericMessageTypeClass())
      {
        sb.append("LocalizableMessageDescriptor");
        sb.append(".");
        sb.append(DESCRIPTOR_CLASS_BASE_NAME);
        sb.append("N");
      }
      else
      {
        sb.append("LocalizableMessageDescriptor");
        sb.append(".");
        sb.append(DESCRIPTOR_CLASS_BASE_NAME);
        sb.append(classTypes.size());
        sb.append(getClassTypeVariables());
      }
      return sb.toString();
    }



    /**
     * Sets the arguments that will be supplied in the declaration of the
     * message.
     * 
     * @param s
     *          array of string arguments that will be passed in the constructor
     */
    void setConstructorArguments(final String... s)
    {
      this.constructorArgs = s;
    }



    private void checkText(final String s)
    {
      int idx;
      // If there are any '%' in the given string, we got a bad format
      // specifier.
      if ((idx = s.indexOf('%')) != -1)
      {
        final char c = (idx > s.length() - 2 ? '%' : s.charAt(idx + 1));
        throw new UnknownFormatConversionException(String.valueOf(c));
      }
    }



    private String getShortClassName(final Class<?> c)
    {
      String name;
      final String fqName = c.getName();
      final int i = fqName.lastIndexOf('.');
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



    private String indent(final int indent)
    {
      final char[] blankArray = new char[2 * indent];
      Arrays.fill(blankArray, ' ');
      return new String(blankArray);
    }



    /**
     * Look for format specifiers in the format string.
     * 
     * @param s
     *          format string
     * @return list of format specifiers
     */
    private List<FormatSpecifier> parse(final String s)
    {
      final List<FormatSpecifier> sl = new ArrayList<FormatSpecifier>();
      final Matcher m = SPECIFIER_PATTERN.matcher(s);
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
          final String[] sa = new String[6];
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



    /**
     * Indicates whether the generic message type class should be used. In
     * general this is when a format specifier is more complicated than we
     * support or when the number of arguments exceeeds the number of specific
     * message type classes (MessageType0, MessageType1 ...) that are defined.
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
        for (final FormatSpecifier s : specifiers)
        {
          if (s.specifiesArgumentIndex())
          {
            return true;
          }
        }
      }
      return false;
    }

  }



  /**
   * The destination directory.
   * 
   * @parameter 
   *            default-value="${project.build.directory}/generated-sources/messages"
   * @required
   */
  private File outputDirectory;

  /**
   * The source directory.
   * 
   * @parameter default-value="${basedir}/src/main/resources"
   * @required
   */
  private File sourceDirectory;

  /**
   * Indicates whether or not message source files should be regenerated even if
   * they are already up to date.
   * 
   * @parameter default-value="false"
   * @required
   */
  private boolean force;

  /**
   * The list of files we want to transfer, relative to the source directory.
   * 
   * @parameter
   * @required
   */
  private List<MessageFile> messageFiles;

  /**
   * The current Maven project.
   * 
   * @parameter default-value="${project}"
   * @readonly
   * @required
   */
  private MavenProject project;

  /**
   * The base name of the specific argument handling subclasses defined below.
   * The class names consist of the base name followed by a number indicating
   * the number of arguments that they handle when creating messages or the
   * letter "N" meaning any number of arguments.
   */
  private static final String DESCRIPTOR_CLASS_BASE_NAME = "Arg";

  /**
   * The maximum number of arguments that can be handled by a specific subclass.
   * If you define more subclasses be sure to increment this number
   * appropriately.
   */
  private static final int DESCRIPTOR_MAX_ARG_HANDLER = 11;

  private static final String SPECIFIER_REGEX = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

  private static final Pattern SPECIFIER_PATTERN = Pattern
      .compile(SPECIFIER_REGEX);

  /**
   * The end-of-line character for this platform.
   */
  private static final String EOL = System.getProperty("line.separator");

  /**
   * The UTF-8 character set used for encoding/decoding files.
   */
  private static final Charset UTF_8 = Charset.forName("UTF-8");



  /**
   * {@inheritDoc}
   */
  @Override
  public void execute() throws MojoExecutionException
  {
    if (!sourceDirectory.exists())
    {
      throw new MojoExecutionException("Source directory "
          + sourceDirectory.getPath() + " does not exist");
    }
    else if (!sourceDirectory.isDirectory())
    {
      throw new MojoExecutionException("Source directory "
          + sourceDirectory.getPath() + " is not a directory");
    }

    if (!outputDirectory.exists())
    {
      if (outputDirectory.mkdirs())
      {
        getLog().info(
            "Created message output directory: " + outputDirectory.getPath());
      }
      else
      {
        throw new MojoExecutionException(
            "Unable to create message output directory: "
                + outputDirectory.getPath());
      }
    }
    else if (!outputDirectory.isDirectory())
    {
      throw new MojoExecutionException("Output directory "
          + outputDirectory.getPath() + " is not a directory");
    }

    if (project != null)
    {
      getLog().info("Adding source directory: " + outputDirectory.getPath());
      project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    }

    for (final MessageFile messageFile : messageFiles)
    {
      processMessageFile(messageFile);
    }
  }



  /*
   * Returns the message Java stub file from this plugin's resources.
   */
  private InputStream getStubFile() throws MojoExecutionException
  {
    return getClass().getResourceAsStream("Messages.java.stub");
  }



  private void processMessageFile(final MessageFile messageFile)
      throws MojoExecutionException
  {
    final File sourceFile = messageFile.getSourceFile(sourceDirectory);
    final File outputFile = messageFile.getOutputFile(outputDirectory);

    // Decide whether to generate messages based on modification
    // times and print status messages.
    if (!sourceFile.exists())
    {
      throw new MojoExecutionException("Message file " + messageFile.getName()
          + " does not exist");
    }

    if (outputFile.exists())
    {
      if (force || sourceFile.lastModified() > outputFile.lastModified())
      {
        outputFile.delete();
        getLog().info(
            "Regenerating " + outputFile.getName() + " from "
                + sourceFile.getName());
      }
      else
      {
        getLog().info(outputFile.getName() + " is up to date");
        return;
      }
    }
    else
    {
      final File packageDirectory = outputFile.getParentFile();
      if (!packageDirectory.exists())
      {
        if (!packageDirectory.mkdirs())
        {
          throw new MojoExecutionException(
              "Unable to create message output directory: "
                  + packageDirectory.getPath());
        }
      }
      getLog().info(
          "Generating " + outputFile.getName() + " from "
              + sourceFile.getName());
    }

    BufferedReader stubReader = null;
    PrintWriter outputWriter = null;

    try
    {
      stubReader = new BufferedReader(new InputStreamReader(getStubFile(),
          UTF_8));
      outputWriter = new PrintWriter(outputFile, "UTF-8");

      final Properties properties = new Properties();
      properties.load(new FileInputStream(sourceFile));

      for (String stubLine = stubReader.readLine(); stubLine != null; stubLine = stubReader
          .readLine())
      {
        if (stubLine.contains("${MESSAGES}"))
        {
          final Map<MessagePropertyKey, String> keyMap = new TreeMap<MessagePropertyKey, String>();

          for (final Object propO : properties.keySet())
          {
            final String propKey = propO.toString();
            final MessagePropertyKey key = MessagePropertyKey
                .parseString(propKey);
            final String formatString = properties.getProperty(propKey);
            keyMap.put(key, formatString);
          }

          int usesOfGenericDescriptor = 0;

          for (final MessagePropertyKey key : keyMap.keySet())
          {
            final String formatString = keyMap.get(key);
            final MessageDescriptorDeclaration message = new MessageDescriptorDeclaration(
                key, formatString);

            message.setConstructorArguments("BASE", quote(key.toString()));
            outputWriter.println(message.toString());
            outputWriter.println();

            // Keep track of when we use the generic descriptor so that we can
            // report it later
            if (message.useGenericMessageTypeClass())
            {
              usesOfGenericDescriptor++;
            }
          }

          getLog().debug("  LocalizableMessage Generated:" + keyMap.size());
          getLog().debug(
              "  LocalizableMessageDescriptor.ArgN:" + usesOfGenericDescriptor);

        }
        else
        {
          stubLine = stubLine.replace("${PACKAGE}",
              messageFile.getPackageName());
          stubLine = stubLine.replace("${CLASS_NAME}",
              messageFile.getClassName());
          stubLine = stubLine.replace("${BASE}", messageFile.getBaseName());
          outputWriter.println(stubLine);
        }
      }
    }
    catch (final Exception e)
    {
      // Don't leave a malformed file laying around. Delete it so it will be
      // forced to be regenerated.
      if (outputFile.exists())
      {
        outputFile.deleteOnExit();
      }
      throw new MojoExecutionException(
          "An IO error occurred while generating the message file: " + e);
    }
    finally
    {
      if (stubReader != null)
      {
        try
        {
          stubReader.close();
        }
        catch (final Exception e)
        {
          // Ignore.
        }
      }

      if (outputWriter != null)
      {
        try
        {
          outputWriter.close();
        }
        catch (final Exception e)
        {
          // Ignore.
        }
      }
    }

  }



  private String quote(final String s)
  {
    return new StringBuilder().append("\"").append(s).append("\"").toString();
  }
}
