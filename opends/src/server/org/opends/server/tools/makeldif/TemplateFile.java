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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a template file, which is a collection of constant
 * definitions, branches, and templates.
 */
public class TemplateFile
{
  /**
   * The name of the file holding the list of first names.
   */
  public static final String FIRST_NAME_FILE = "first.names";



  /**
   * The name of the file holding the list of last names.
   */
  public static final String LAST_NAME_FILE = "last.names";



  // A map of the contents of various text files used during the parsing
  // process, mapped from absolute path to the array of lines in the file.
  private HashMap<String,String[]> fileLines;

  // The index of the next first name value that should be used.
  private int firstNameIndex;

  // The index of the next last name value that should be used.
  private int lastNameIndex;

  // A counter used to keep track of the number of times that the larger of the
  // first/last name list has been completed.
  private int nameLoopCounter;

  // A counter that will be used in case we have exhausted all possible first
  // and last name combinations.
  private int nameUniquenessCounter;

  // The set of branch definitions for this template file.
  private LinkedHashMap<DN,Branch> branches;

  // The set of constant definitions for this template file.
  private LinkedHashMap<String,String> constants;

  // The set of registered tags for this template file.
  private LinkedHashMap<String,Tag> registeredTags;

  // The set of template definitions for this template file.
  private LinkedHashMap<String,Template> templates;

  // The random number generator for this template file.
  private Random random;

  // The next first name that should be used.
  private String firstName;

  // The next last name that should be used.
  private String lastName;

  // The resource path to use for filesystem elements that cannot be found
  // anywhere else.
  private String resourcePath;

  // The path to the directory containing the template file, if available.
  private String templatePath;

  // The set of first names to use when generating the LDIF.
  private String[] firstNames;

  // The set of last names to use when generating the LDIF.
  private String[] lastNames;



  /**
   * Creates a new, empty template file structure.
   *
   * @param  resourcePath  The path to the directory that may contain additional
   *                       resource files needed during the LDIF generation
   *                       process.
   */
  public TemplateFile(String resourcePath)
  {
    this(resourcePath, new Random());
  }



  /**
   * Creates a new, empty template file structure.
   *
   *
   * @param  resourcePath  The path to the directory that may contain additional
   *                       resource files needed during the LDIF generation
   *                       process.
   * @param  random        The random number generator for this template file.
   */
  public TemplateFile(String resourcePath, Random random)
  {
    this.resourcePath = resourcePath;
    this.random       = random;

    fileLines             = new HashMap<String,String[]>();
    branches              = new LinkedHashMap<DN,Branch>();
    constants             = new LinkedHashMap<String,String>();
    registeredTags        = new LinkedHashMap<String,Tag>();
    templates             = new LinkedHashMap<String,Template>();
    templatePath          = null;
    firstNames            = new String[0];
    lastNames             = new String[0];
    firstName             = null;
    lastName              = null;
    firstNameIndex        = 0;
    lastNameIndex         = 0;
    nameLoopCounter       = 0;
    nameUniquenessCounter = 1;

    registerDefaultTags();

    try
    {
      readNameFiles();
    }
    catch (IOException ioe)
    {
      // FIXME -- What to do here?
      ioe.printStackTrace();
      firstNames = new String[] { "John" };
      lastNames  = new String[] { "Doe" };
    }
  }



  /**
   * Retrieves the set of tags that have been registered.  They will be in the
   * form of a mapping between the name of the tag (in all lowercase characters)
   * and the corresponding tag implementation.
   *
   * @return  The set of tags that have been registered.
   */
  public Map<String,Tag> getTags()
  {
    return registeredTags;
  }



  /**
   * Retrieves the tag with the specified name.
   *
   * @param  lowerName  The name of the tag to retrieve, in all lowercase
   *                    characters.
   *
   * @return  The requested tag, or <CODE>null</CODE> if no such tag has been
   *          registered.
   */
  public Tag getTag(String lowerName)
  {
    return registeredTags.get(lowerName);
  }



  /**
   * Registers the specified class as a tag that may be used in templates.
   *
   * @param  tagClass  The fully-qualified name of the class to register as a
   *                   tag.
   *
   * @throws  MakeLDIFException  If a problem occurs while attempting to
   *                             register the specified tag.
   */
  public void registerTag(String tagClass)
         throws MakeLDIFException
  {
    Class c;
    try
    {
      c = Class.forName(tagClass);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_LOAD_TAG_CLASS;
      String message = getMessage(msgID, tagClass);
      throw new MakeLDIFException(msgID, message, e);
    }

    Tag t;
    try
    {
      t = (Tag) c.newInstance();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_INSTANTIATE_TAG;
      String message = getMessage(msgID, tagClass);
      throw new MakeLDIFException(msgID, message, e);
    }

    String lowerName = toLowerCase(t.getName());
    if (registeredTags.containsKey(lowerName))
    {
      int    msgID   = MSGID_MAKELDIF_CONFLICTING_TAG_NAME;
      String message = getMessage(msgID, tagClass, t.getName());
      throw new MakeLDIFException(msgID, message);
    }
    else
    {
      registeredTags.put(lowerName, t);
    }
  }



  /**
   * Registers the set of tags that will always be available for use in
   * templates.
   */
  private void registerDefaultTags()
  {
    Class[] defaultTagClasses = new Class[]
    {
      AttributeValueTag.class,
      DNTag.class,
      FileTag.class,
      FirstNameTag.class,
      GUIDTag.class,
      IfAbsentTag.class,
      IfPresentTag.class,
      LastNameTag.class,
      ParentDNTag.class,
      PresenceTag.class,
      RandomTag.class,
      RDNTag.class,
      SequentialTag.class,
      StaticTextTag.class,
      UnderscoreDNTag.class,
      UnderscoreParentDNTag.class
    };

    for (Class c : defaultTagClasses)
    {
      try
      {
        Tag t = (Tag) c.newInstance();
        registeredTags.put(toLowerCase(t.getName()), t);
      }
      catch (Exception e)
      {
        // This should never happen.
        e.printStackTrace();
      }
    }
  }



  /**
   * Retrieves the set of constants defined for this template file.
   *
   * @return  The set of constants defined for this template file.
   */
  public Map<String,String> getConstants()
  {
    return constants;
  }



  /**
   * Retrieves the value of the constant with the specified name.
   *
   * @param  lowerName  The name of the constant to retrieve, in all lowercase
   *                    characters.
   *
   * @return  The value of the constant with the specified name, or
   *          <CODE>null</CODE> if there is no such constant.
   */
  public String getConstant(String lowerName)
  {
    return constants.get(lowerName);
  }



  /**
   * Registers the provided constant for use in the template.
   *
   * @param  name   The name for the constant.
   * @param  value  The value for the constant.
   */
  public void registerConstant(String name, String value)
  {
    constants.put(toLowerCase(name), value);
  }



  /**
   * Retrieves the set of branches defined in this template file.
   *
   * @return  The set of branches defined in this template file.
   */
  public Map<DN,Branch> getBranches()
  {
    return branches;
  }



  /**
   * Retrieves the branch registered with the specified DN.
   *
   * @param  branchDN  The DN for which to retrieve the corresponding branch.
   *
   * @return  The requested branch, or <CODE>null</CODE> if no such branch has
   *          been registered.
   */
  public Branch getBranch(DN branchDN)
  {
    return branches.get(branchDN);
  }



  /**
   * Registers the provided branch in this template file.
   *
   * @param  branch  The branch to be registered.
   */
  public void registerBranch(Branch branch)
  {
    branches.put(branch.getBranchDN(), branch);
  }



  /**
   * Retrieves the set of templates defined in this template file.
   *
   * @return  The set of templates defined in this template file.
   */
  public Map<String,Template> getTemplates()
  {
    return templates;
  }



  /**
   * Retrieves the template with the specified name.
   *
   * @param  lowerName  The name of the template to retrieve, in all lowercase
   *                    characters.
   *
   * @return  The requested template, or <CODE>null</CODE> if there is no such
   *          template.
   */
  public Template getTemplate(String lowerName)
  {
    return templates.get(lowerName);
  }



  /**
   * Registers the provided template for use in this template file.
   *
   * @param  template  The template to be registered.
   */
  public void registerTemplate(Template template)
  {
    templates.put(toLowerCase(template.getName()), template);
  }



  /**
   * Retrieves the random number generator for this template file.
   *
   * @return  The random number generator for this template file.
   */
  public Random getRandom()
  {
    return random;
  }



  /**
   * Reads the contents of the first and last name files into the appropriate
   * arrays and sets up the associated index pointers.
   *
   * @throws  IOException  If a problem occurs while reading either of the
   *                       files.
   */
  private void readNameFiles()
          throws IOException
  {
    File f = getFile(FIRST_NAME_FILE);
    ArrayList<String> nameList = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new FileReader(f));
    while (true)
    {
      String line = reader.readLine();
      if (line == null)
      {
        break;
      }
      else
      {
        nameList.add(line);
      }
    }
    reader.close();
    firstNames = new String[nameList.size()];
    nameList.toArray(firstNames);

    f = getFile(LAST_NAME_FILE);
    nameList = new ArrayList<String>();
    reader = new BufferedReader(new FileReader(f));
    while (true)
    {
      String line = reader.readLine();
      if (line == null)
      {
        break;
      }
      else
      {
        nameList.add(line);
      }
    }
    reader.close();
    lastNames = new String[nameList.size()];
    nameList.toArray(lastNames);
  }



  /**
   * Updates the first and last name indexes to choose new values.  The
   * algorithm used is designed to ensure that the combination of first and last
   * names will never be repeated.  It depends on the number of first names and
   * the number of last names being relatively prime.  This method should be
   * called before beginning generation of each template entry.
   */
  public void nextFirstAndLastNames()
  {
    firstName = firstNames[firstNameIndex++];
    lastName  = lastNames[lastNameIndex++];


    // If we've already exhausted every possible combination, then append an
    // integer to the last name.
    if (nameUniquenessCounter > 1)
    {
      lastName += nameUniquenessCounter;
    }

    if (firstNameIndex >= firstNames.length)
    {
      // We're at the end of the first name list, so start over.  If the first
      // name list is larger than the last name list, then we'll also need to
      // set the last name index to the next loop counter position.
      firstNameIndex = 0;
      if (firstNames.length > lastNames.length)
      {
        lastNameIndex = ++nameLoopCounter;
        if (lastNameIndex >= lastNames.length)
        {
          lastNameIndex = 0;
          nameUniquenessCounter++;
        }
      }
    }

    if (lastNameIndex >= lastNames.length)
    {
      // We're at the end of the last name list, so start over.  If the last
      // name list is larger than the first name list, then we'll also need to
      // set the first name index to the next loop counter position.
      lastNameIndex = 0;
      if (lastNames.length > firstNames.length)
      {
        firstNameIndex = ++nameLoopCounter;
        if (firstNameIndex >= firstNames.length)
        {
          firstNameIndex = 0;
          nameUniquenessCounter++;
        }
      }
    }
  }



  /**
   * Retrieves the first name value that should be used for the current entry.
   *
   * @return  The first name value that should be used for the current entry.
   */
  public String getFirstName()
  {
    return firstName;
  }



  /**
   * Retrieves the last name value that should be used for the current entry.
   *
   * @return  The last name value that should be used for the current entry.
   */
  public String getLastName()
  {
    return lastName;
  }



  /**
   * Parses the contents of the specified file as a MakeLDIF template file
   * definition.
   *
   * @param  filename  The name of the file containing the template data.
   * @param  warnings  A list into which any warnings identified may be placed.
   *
   * @throws  IOException  If a problem occurs while attempting to read data
   *                       from the specified file.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the MakeLDIF components.
   *
   * @throws  MakeLDIFException  If any other problem occurs while parsing the
   *                             template file.
   */
  public void parse(String filename, List<String> warnings)
         throws IOException, InitializationException, MakeLDIFException
  {
    ArrayList<String> fileLines = new ArrayList<String>();

    templatePath = null;
    File f = getFile(filename);
    if ((f == null) || (! f.exists()))
    {
      int    msgID   = MSGID_MAKELDIF_COULD_NOT_FIND_TEMPLATE_FILE;
      String message = getMessage(msgID, filename);
      throw new IOException(message);
    }
    else
    {
      templatePath = f.getParentFile().getAbsolutePath();
    }

    BufferedReader reader = new BufferedReader(new FileReader(f));
    while (true)
    {
      String line = reader.readLine();
      if (line == null)
      {
        break;
      }
      else
      {
        fileLines.add(line);
      }
    }

    reader.close();

    String[] lines = new String[fileLines.size()];
    fileLines.toArray(lines);
    parse(lines, warnings);
  }



  /**
   * Parses the data read from the provided input stream as a MakeLDIF template
   * file definition.
   *
   * @param  inputStream  The input stream from which to read the template file
   *                      data.
   * @param  warnings     A list into which any warnings identified may be
   *                      placed.
   *
   * @throws  IOException  If a problem occurs while attempting to read data
   *                       from the provided input stream.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the MakeLDIF components.
   *
   * @throws  MakeLDIFException  If any other problem occurs while parsing the
   *                             template file.
   */
  public void parse(InputStream inputStream, List<String> warnings)
         throws IOException, InitializationException, MakeLDIFException
  {
    ArrayList<String> fileLines = new ArrayList<String>();

    BufferedReader reader =
         new BufferedReader(new InputStreamReader(inputStream));
    while (true)
    {
      String line = reader.readLine();
      if (line == null)
      {
        break;
      }
      else
      {
        fileLines.add(line);
      }
    }

    reader.close();

    String[] lines = new String[fileLines.size()];
    fileLines.toArray(lines);
    parse(lines, warnings);
  }



  /**
   * Parses the provided data as a MakeLDIF template file definition.
   *
   * @param  lines  The lines that make up the template file.
   * @param  warnings  A list into which any warnings identified may be placed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the MakeLDIF components.
   *
   * @throws  MakeLDIFException  If any other problem occurs while parsing the
   *                             template file.
   */
  public void parse(String[] lines, List<String> warnings)
         throws InitializationException, MakeLDIFException
  {
    // Create temporary variables that will be used to hold the data read.
    LinkedHashMap<String,Tag> templateFileIncludeTags =
         new LinkedHashMap<String,Tag>();
    LinkedHashMap<String,String> templateFileConstants =
         new LinkedHashMap<String,String>();
    LinkedHashMap<DN,Branch> templateFileBranches =
         new LinkedHashMap<DN,Branch>();
    LinkedHashMap<String,Template> templateFileTemplates =
         new LinkedHashMap<String,Template>();

    for (int lineNumber=0; lineNumber < lines.length; lineNumber++)
    {
      String line = lines[lineNumber];

      // See if there are any constant definitions in the line that need to be
      // replaced.  We'll do that first before any further processing.
      int closePos = line.lastIndexOf(']');
      if (closePos > 0)
      {
        StringBuilder lineBuffer = new StringBuilder(line);
        int openPos = line.lastIndexOf('[', closePos);
        if (openPos >= 0)
        {
          String constantName =
               toLowerCase(line.substring(openPos+1, closePos));
          String constantValue = templateFileConstants.get(constantName);
          if (constantValue == null)
          {
            int    msgID   = MSGID_MAKELDIF_WARNING_UNDEFINED_CONSTANT;
            String message = getMessage(msgID, constantName, lineNumber);
            warnings.add(message);
          }
          else
          {
            lineBuffer.replace(openPos, closePos+1, constantValue);
          }
        }

        line = lineBuffer.toString();
      }


      String lowerLine = toLowerCase(line);
      if ((line.length() == 0) || line.startsWith("#"))
      {
        // This is a comment or a blank line, so we'll ignore it.
        continue;
      }
      else if (lowerLine.startsWith("include "))
      {
        // This should be an include definition.  The next element should be the
        // name of the class.  Load and instantiate it and make sure there are
        // no conflicts.
        String className = line.substring(8).trim();

        Class tagClass;
        try
        {
          tagClass = Class.forName(className);
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_MAKELDIF_CANNOT_LOAD_TAG_CLASS;
          String message = getMessage(msgID, className);
          throw new MakeLDIFException(msgID, message, e);
        }

        Tag tag;
        try
        {
          tag = (Tag) tagClass.newInstance();
        }
        catch (Exception e)
        {
          int    msgID   = MSGID_MAKELDIF_CANNOT_INSTANTIATE_TAG;
          String message = getMessage(msgID, className);
          throw new MakeLDIFException(msgID, message, e);
        }

        String lowerName = toLowerCase(tag.getName());
        if (registeredTags.containsKey(lowerName) ||
            templateFileIncludeTags.containsKey(lowerName))
        {
          int    msgID   = MSGID_MAKELDIF_CONFLICTING_TAG_NAME;
          String message = getMessage(msgID, className, tag.getName());
          throw new MakeLDIFException(msgID, message);
        }

        templateFileIncludeTags.put(lowerName, tag);
      }
      else if (lowerLine.startsWith("define "))
      {
        // This should be a constant definition.  The rest of the line should
        // contain the constant name, an equal sign, and the constant value.
        int equalPos = line.indexOf('=', 7);
        if (equalPos < 0)
        {
          int    msgID   = MSGID_MAKELDIF_DEFINE_MISSING_EQUALS;
          String message = getMessage(msgID, lineNumber);
          throw new MakeLDIFException(msgID, message);
        }

        String name  = line.substring(7, equalPos).trim();
        if (name.length() == 0)
        {
          int    msgID   = MSGID_MAKELDIF_DEFINE_NAME_EMPTY;
          String message = getMessage(msgID, lineNumber);
          throw new MakeLDIFException(msgID, message);
        }

        String lowerName = toLowerCase(name);
        if (templateFileConstants.containsKey(lowerName))
        {
          int    msgID   = MSGID_MAKELDIF_CONFLICTING_CONSTANT_NAME;
          String message = getMessage(msgID, name, lineNumber);
          throw new MakeLDIFException(msgID, message);
        }

        String value = line.substring(equalPos+1);
        if (value.length() == 0)
        {
          int    msgID   = MSGID_MAKELDIF_WARNING_DEFINE_VALUE_EMPTY;
          String message = getMessage(msgID, name, lineNumber);
          warnings.add(message);
        }

        templateFileConstants.put(lowerName, value);
      }
      else if (lowerLine.startsWith("branch: "))
      {
        int startLineNumber = lineNumber;
        ArrayList<String> lineList = new ArrayList<String>();
        lineList.add(line);
        while (true)
        {
          lineNumber++;
          if (lineNumber >= lines.length)
          {
            break;
          }

          line = lines[lineNumber];
          if (line.length() == 0)
          {
            break;
          }
          else
          {
            // See if there are any constant definitions in the line that need
            // to be replaced.  We'll do that first before any further
            // processing.
            closePos = line.lastIndexOf(']');
            if (closePos > 0)
            {
              StringBuilder lineBuffer = new StringBuilder(line);
              int openPos = line.lastIndexOf('[', closePos);
              if (openPos >= 0)
              {
                String constantName =
                     toLowerCase(line.substring(openPos+1, closePos));
                String constantValue = templateFileConstants.get(constantName);
                if (constantValue == null)
                {
                  int    msgID   = MSGID_MAKELDIF_WARNING_UNDEFINED_CONSTANT;
                  String message = getMessage(msgID, constantName, lineNumber);
                  warnings.add(message);
                }
                else
                {
                  lineBuffer.replace(openPos, closePos+1, constantValue);
                }
              }

              line = lineBuffer.toString();
            }

            lineList.add(line);
          }
        }

        String[] branchLines = new String[lineList.size()];
        lineList.toArray(branchLines);

        Branch b = parseBranchDefinition(branchLines, lineNumber,
                                         templateFileIncludeTags,
                                         templateFileConstants, warnings);
        DN branchDN = b.getBranchDN();
        if (templateFileBranches.containsKey(branchDN))
        {
          int    msgID   = MSGID_MAKELDIF_CONFLICTING_BRANCH_DN;
          String message = getMessage(msgID, String.valueOf(branchDN),
                                      startLineNumber);
          throw new MakeLDIFException(msgID, message);
        }
        else
        {
          templateFileBranches.put(branchDN, b);
        }
      }
      else if (lowerLine.startsWith("template: "))
      {
        int startLineNumber = lineNumber;
        ArrayList<String> lineList = new ArrayList<String>();
        lineList.add(line);
        while (true)
        {
          lineNumber++;
          if (lineNumber >= lines.length)
          {
            break;
          }

          line = lines[lineNumber];
          if (line.length() == 0)
          {
            break;
          }
          else
          {
            // See if there are any constant definitions in the line that need
            // to be replaced.  We'll do that first before any further
            // processing.
            closePos = line.lastIndexOf(']');
            if (closePos > 0)
            {
              StringBuilder lineBuffer = new StringBuilder(line);
              int openPos = line.lastIndexOf('[', closePos);
              if (openPos >= 0)
              {
                String constantName =
                     toLowerCase(line.substring(openPos+1, closePos));
                String constantValue = templateFileConstants.get(constantName);
                if (constantValue == null)
                {
                  int    msgID   = MSGID_MAKELDIF_WARNING_UNDEFINED_CONSTANT;
                  String message = getMessage(msgID, constantName, lineNumber);
                  warnings.add(message);
                }
                else
                {
                  lineBuffer.replace(openPos, closePos+1, constantValue);
                }
              }

              line = lineBuffer.toString();
            }

            lineList.add(line);
          }
        }

        String[] templateLines = new String[lineList.size()];
        lineList.toArray(templateLines);

        Template t = parseTemplateDefinition(templateLines, startLineNumber,
                                             templateFileIncludeTags,
                                             templateFileConstants, warnings);
        String lowerName = toLowerCase(t.getName());
        if (templateFileTemplates.containsKey(lowerName))
        {
          int    msgID   = MSGID_MAKELDIF_CONFLICTING_TEMPLATE_NAME;
          String message = getMessage(msgID, String.valueOf(t.getName()),
                                      startLineNumber);
          throw new MakeLDIFException(msgID, message);
        }
        else
        {
          templateFileTemplates.put(lowerName, t);
        }
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_UNEXPECTED_TEMPLATE_FILE_LINE;
        String message = getMessage(msgID, line, lineNumber);
        throw new MakeLDIFException(msgID, message);
      }
    }


    // If we've gotten here, then we're almost done.  We just need to finalize
    // the branch and template definitions and then update the template file
    // variables.
    for (Branch b : templateFileBranches.values())
    {
      b.completeBranchInitialization(templateFileTemplates);
    }

    for (Template t : templateFileTemplates.values())
    {
      t.completeTemplateInitialization(templateFileTemplates);
    }

    registeredTags.putAll(templateFileIncludeTags);
    constants.putAll(templateFileConstants);
    branches.putAll(templateFileBranches);
    templates.putAll(templateFileTemplates);
  }



  /**
   * Parses the information contained in the provided set of lines as a MakeLDIF
   * branch definition.
   *
   * @param  branchLines      The set of lines containing the branch definition.
   * @param  startLineNumber  The line number in the template file on which the
   *                          first of the branch lines appears.
   * @param  tags             The set of defined tags from the template file.
   *                          Note that this does not include the tags that are
   *                          always registered by default.
   * @param  constants        The set of constants defined in the template file.
   * @param  warnings         A list into which any warnings identified may be
   *                          placed.
   *
   * @return  The decoded branch definition.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the branch elements.
   *
   * @throws  MakeLDIFException  If some other problem occurs during processing.
   */
  private Branch parseBranchDefinition(String[] branchLines,
                                       int startLineNumber,
                                       LinkedHashMap<String,Tag> tags,
                                       LinkedHashMap<String,String> constants,
                                       List<String> warnings)
          throws InitializationException, MakeLDIFException
  {
    // The first line must be "branch: " followed by the branch DN.
    String dnString = branchLines[0].substring(8).trim();
    DN branchDN;
    try
    {
      branchDN = DN.decode(dnString);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_DECODE_BRANCH_DN;
      String message = getMessage(msgID, dnString, startLineNumber);
      throw new MakeLDIFException(msgID, message);
    }


    // Create a new branch that will be used for the verification process.
    Branch branch = new Branch(this, branchDN);

    for (int i=1; i < branchLines.length; i++)
    {
      String line       = branchLines[i];
      String lowerLine  = toLowerCase(line);
      int    lineNumber = startLineNumber + i;

      if (lowerLine.startsWith("#"))
      {
        // It's a comment, so we should ignore it.
        continue;
      }
      else if (lowerLine.startsWith("subordinatetemplate: "))
      {
        // It's a subordinate template, so we'll want to parse the name and the
        // number of entries.
        int colonPos = line.indexOf(':', 21);
        if (colonPos <= 21)
        {
          int    msgID   = MSGID_MAKELDIF_BRANCH_SUBORDINATE_TEMPLATE_NO_COLON;
          String message = getMessage(msgID, lineNumber, dnString);
          throw new MakeLDIFException(msgID, message);
        }

        String templateName = line.substring(21, colonPos).trim();

        int numEntries;
        try
        {
          numEntries = Integer.parseInt(line.substring(colonPos+1).trim());
          if (numEntries < 0)
          {
            int msgID = MSGID_MAKELDIF_BRANCH_SUBORDINATE_INVALID_NUM_ENTRIES;
            String message = getMessage(msgID, lineNumber, dnString, numEntries,
                                        templateName);
            throw new MakeLDIFException(msgID, message);
          }
          else if (numEntries == 0)
          {
            int    msgID   = MSGID_MAKELDIF_BRANCH_SUBORDINATE_ZERO_ENTRIES;
            String message = getMessage(msgID, lineNumber, dnString,
                                        templateName);
            warnings.add(message);
          }

          branch.addSubordinateTemplate(templateName, numEntries);
        }
        catch (NumberFormatException nfe)
        {
          int msgID = MSGID_MAKELDIF_BRANCH_SUBORDINATE_CANT_PARSE_NUMENTRIES;
          String message = getMessage(msgID, templateName, lineNumber,
                                      dnString);
          throw new MakeLDIFException(msgID, message);
        }
      }
      else
      {
        TemplateLine templateLine = parseTemplateLine(line, lowerLine,
                                                      lineNumber, branch, null,
                                                      tags, warnings);
        branch.addExtraLine(templateLine);
      }
    }

    return branch;
  }



  /**
   * Parses the information contained in the provided set of lines as a MakeLDIF
   * template definition.
   *
   * @param  templateLines    The set of lines containing the template
   *                          definition.
   * @param  startLineNumber  The line number in the template file on which the
   *                          first of the template lines appears.
   * @param  tags             The set of defined tags from the template file.
   *                          Note that this does not include the tags that are
   *                          always registered by default.
   * @param  constants        The set of constants defined in the template file.
   * @param  warnings         A list into which any warnings identified may be
   *                          placed.
   *
   * @return  The decoded template definition.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the template elements.
   *
   * @throws  MakeLDIFException  If some other problem occurs during processing.
   */
  private Template parseTemplateDefinition(String[] templateLines,
                                           int startLineNumber,
                                           LinkedHashMap<String,Tag> tags,
                                           LinkedHashMap<String,String>
                                                constants,
                                           List<String> warnings)
          throws InitializationException, MakeLDIFException
  {
    // The first line must be "template: " followed by the template name.
    String templateName = templateLines[0].substring(10).trim();


    // The next line may start with either "extends: ", "rdnAttr: ", or
    // "subordinateTemplate: ".  Keep reading until we find something that's
    // not one of those.
    int                arrayLineNumber    = 1;
    String             parentTemplateName = null;
    AttributeType[]    rdnAttributes      = null;
    ArrayList<String>  subTemplateNames   = new ArrayList<String>();
    ArrayList<Integer> entriesPerTemplate = new ArrayList<Integer>();
    for ( ; arrayLineNumber < templateLines.length; arrayLineNumber++)
    {
      int    lineNumber = startLineNumber + arrayLineNumber;
      String line       = templateLines[arrayLineNumber];
      String lowerLine  = toLowerCase(line);

      if (lowerLine.startsWith("#"))
      {
        // It's a comment.  Ignore it.
        continue;
      }
      else if (lowerLine.startsWith("extends: "))
      {
        parentTemplateName = line.substring(9).trim();
      }
      else if (lowerLine.startsWith("rdnattr: "))
      {
        // This is the set of RDN attributes.  If there are multiple, they may
        // be separated by plus signs.
        ArrayList<AttributeType> attrList = new ArrayList<AttributeType>();
        String rdnAttrNames = lowerLine.substring(9).trim();
        StringTokenizer tokenizer = new StringTokenizer(rdnAttrNames, "+");
        while (tokenizer.hasMoreTokens())
        {
          attrList.add(DirectoryServer.getAttributeType(tokenizer.nextToken(),
                                                        true));
        }

        rdnAttributes = new AttributeType[attrList.size()];
        attrList.toArray(rdnAttributes);
      }
      else if (lowerLine.startsWith("subordinatetemplate: "))
      {
        // It's a subordinate template, so we'll want to parse the name and the
        // number of entries.
        int colonPos = line.indexOf(':', 21);
        if (colonPos <= 21)
        {
          int msgID = MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_TEMPLATE_NO_COLON;
          String message = getMessage(msgID, lineNumber, templateName);
          throw new MakeLDIFException(msgID, message);
        }

        String subTemplateName = line.substring(21, colonPos).trim();

        int numEntries;
        try
        {
          numEntries = Integer.parseInt(line.substring(colonPos+1).trim());
          if (numEntries < 0)
          {
            int msgID = MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_INVALID_NUM_ENTRIES;
            String message = getMessage(msgID, lineNumber, templateName,
                                        numEntries, subTemplateName);
            throw new MakeLDIFException(msgID, message);
          }
          else if (numEntries == 0)
          {
            int    msgID   = MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_ZERO_ENTRIES;
            String message = getMessage(msgID, lineNumber, templateName,
                                        subTemplateName);
            warnings.add(message);
          }

          subTemplateNames.add(subTemplateName);
          entriesPerTemplate.add(numEntries);
        }
        catch (NumberFormatException nfe)
        {
          int msgID = MSGID_MAKELDIF_TEMPLATE_SUBORDINATE_CANT_PARSE_NUMENTRIES;
          String message = getMessage(msgID, subTemplateName, lineNumber,
                                      templateName);
          throw new MakeLDIFException(msgID, message);
        }
      }
      else
      {
        // It's something we don't recognize, so it must be a template line.
        break;
      }
    }

    // Create a new template that will be used for the verification process.
    String[] subordinateTemplateNames = new String[subTemplateNames.size()];
    subTemplateNames.toArray(subordinateTemplateNames);

    int[] numEntriesPerTemplate = new int[entriesPerTemplate.size()];
    for (int i=0; i < numEntriesPerTemplate.length; i++)
    {
      numEntriesPerTemplate[i] = entriesPerTemplate.get(i);
    }

    Template template = new Template(this, templateName, rdnAttributes,
                                     subordinateTemplateNames,
                                     numEntriesPerTemplate);

    for ( ; arrayLineNumber < templateLines.length; arrayLineNumber++)
    {
      String line       = templateLines[arrayLineNumber];
      String lowerLine  = toLowerCase(line);
      int    lineNumber = startLineNumber + arrayLineNumber;

      if (lowerLine.startsWith("#"))
      {
        // It's a comment, so we should ignore it.
        continue;
      }
      else
      {
        TemplateLine templateLine = parseTemplateLine(line, lowerLine,
                                                      lineNumber, null,
                                                      template, tags, warnings);
        template.addTemplateLine(templateLine);
      }
    }

    return template;
  }



  /**
   * Parses the provided line as a template line.  Note that exactly one of the
   * branch or template arguments must be non-null and the other must be null.
   *
   * @param  line        The text of the template line.
   * @param  lowerLine   The template line in all lowercase characters.
   * @param  lineNumber  The line number on which the template line appears.
   * @param  branch      The branch with which the template line is associated.
   * @param  template    The template with which the template line is
   *                     associated.
   * @param  tags        The set of defined tags from the template file.  Note
   *                     that this does not include the tags that are always
   *                     registered by default.
   * @param  warnings    A list into which any warnings identified may be
   *                     placed.
   *
   * @return  The template line that has been parsed.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   any of the template elements.
   *
   * @throws  MakeLDIFException  If some other problem occurs during processing.
   */
  private TemplateLine parseTemplateLine(String line, String lowerLine,
                                         int lineNumber, Branch branch,
                                         Template template,
                                         LinkedHashMap<String,Tag> tags,
                                         List<String> warnings)
          throws InitializationException, MakeLDIFException
  {
    // The first component must be the attribute type, followed by a colon.
    int colonPos = lowerLine.indexOf(':');
    if (colonPos < 0)
    {
      if (branch == null)
      {
        int    msgID   = MSGID_MAKELDIF_NO_COLON_IN_TEMPLATE_LINE;
        String message = getMessage(msgID, lineNumber, template.getName());
        throw new MakeLDIFException(msgID, message);
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_NO_COLON_IN_BRANCH_EXTRA_LINE;
        String message = getMessage(msgID, lineNumber,
                                    String.valueOf(branch.getBranchDN()));
        throw new MakeLDIFException(msgID, message);
      }
    }
    else if (colonPos == 0)
    {
      if (branch == null)
      {
        int    msgID   = MSGID_MAKELDIF_NO_ATTR_IN_TEMPLATE_LINE;
        String message = getMessage(msgID, lineNumber, template.getName());
        throw new MakeLDIFException(msgID, message);
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_NO_ATTR_IN_BRANCH_EXTRA_LINE;
        String message = getMessage(msgID, lineNumber,
                                    String.valueOf(branch.getBranchDN()));
        throw new MakeLDIFException(msgID, message);
      }
    }

    AttributeType attributeType =
         DirectoryServer.getAttributeType(lowerLine.substring(0, colonPos),
                                          true);


    // First, find the position of the first non-blank character in the line.
    int length = line.length();
    int pos    = colonPos + 1;
    while ((pos < length) && (lowerLine.charAt(pos) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // We've hit the end of the line with no value.  We'll allow it, but add a
      // warning.
      if (branch == null)
      {
        int    msgID   = MSGID_MAKELDIF_NO_VALUE_IN_TEMPLATE_LINE;
        String message = getMessage(msgID, lineNumber, template.getName());
        warnings.add(message);
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_NO_VALUE_IN_BRANCH_EXTRA_LINE;
        String message = getMessage(msgID, lineNumber,
                                    String.valueOf(branch.getBranchDN()));
        warnings.add(message);
      }
    }


    // Define constants that specify what we're currently parsing.
    final int PARSING_STATIC_TEXT     = 0;
    final int PARSING_REPLACEMENT_TAG = 1;
    final int PARSING_ATTRIBUTE_TAG   = 2;

    int phase = PARSING_STATIC_TEXT;


    ArrayList<Tag> tagList = new ArrayList<Tag>();
    StringBuilder buffer = new StringBuilder();
    for ( ; pos < length; pos++)
    {
      char c = line.charAt(pos);
      switch (phase)
      {
        case PARSING_STATIC_TEXT:
          switch (c)
          {
            case '<':
              if (buffer.length() > 0)
              {
                StaticTextTag t = new StaticTextTag();
                String[] args = new String[] { buffer.toString() };
                t.initializeForBranch(this, branch, args, lineNumber,
                                      warnings);
                tagList.add(t);
                buffer = new StringBuilder();
              }

              phase = PARSING_REPLACEMENT_TAG;
              break;
            case '{':
              if (buffer.length() > 0)
              {
                StaticTextTag t = new StaticTextTag();
                String[] args = new String[] { buffer.toString() };
                t.initializeForBranch(this, branch, args, lineNumber,
                                      warnings);
                tagList.add(t);
                buffer = new StringBuilder();
              }

              phase = PARSING_ATTRIBUTE_TAG;
              break;
            default:
              buffer.append(c);
          }
          break;

        case PARSING_REPLACEMENT_TAG:
          switch (c)
          {
            case '>':
              Tag t = parseReplacementTag(buffer.toString(), branch, template,
                                          lineNumber, tags, warnings);
              tagList.add(t);
              buffer = new StringBuilder();

              phase = PARSING_STATIC_TEXT;
              break;
            default:
              buffer.append(c);
              break;
          }
          break;

        case PARSING_ATTRIBUTE_TAG:
          switch (c)
          {
              case '}':
              Tag t = parseAttributeTag(buffer.toString(), branch, template,
                                        lineNumber, warnings);
              tagList.add(t);
              buffer = new StringBuilder();

              phase = PARSING_STATIC_TEXT;
              break;
            default:
              buffer.append(c);
              break;
          }
          break;
      }
    }

    if (phase == PARSING_STATIC_TEXT)
    {
      if (buffer.length() > 0)
      {
        StaticTextTag t = new StaticTextTag();
        String[] args = new String[] { buffer.toString() };
        t.initializeForBranch(this, branch, args, lineNumber, warnings);
        tagList.add(t);
      }
    }
    else
    {
      int    msgID   = MSGID_MAKELDIF_INCOMPLETE_TAG;
      String message = getMessage(msgID, lineNumber);
      throw new InitializationException(msgID, message);
    }

    Tag[] tagArray = new Tag[tagList.size()];
    tagList.toArray(tagArray);
    return new TemplateLine(attributeType, lineNumber, tagArray);
  }



  /**
   * Parses the provided string as a replacement tag.  Exactly one of the branch
   * or template must be null, and the other must be non-null.
   *
   * @param  tagString   The string containing the encoded tag.
   * @param  branch      The branch in which this tag appears.
   * @param  template    The template in which this tag appears.
   * @param  lineNumber  The line number on which this tag appears in the
   *                     template file.
   * @param  tags        The set of defined tags from the template file.  Note
   *                     that this does not include the tags that are always
   *                     registered by default.
   * @param  warnings    A list into which any warnings identified may be
   *                     placed.
   *
   * @return  The replacement tag parsed from the provided string.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the tag.
   *
   * @throws  MakeLDIFException  If some other problem occurs during processing.
   */
  private Tag parseReplacementTag(String tagString, Branch branch,
                                  Template template, int lineNumber,
                                  LinkedHashMap<String,Tag> tags,
                                  List<String> warnings)
          throws InitializationException, MakeLDIFException
  {
    // The components of the replacement tag will be separated by colons, with
    // the first being the tag name and the remainder being arguments.
    StringTokenizer tokenizer = new StringTokenizer(tagString, ":");
    String          tagName      = tokenizer.nextToken().trim();
    String          lowerTagName = toLowerCase(tagName);

    Tag t = getTag(lowerTagName);
    if (t == null)
    {
      t = tags.get(lowerTagName);
      if (t == null)
      {
        int    msgID   = MSGID_MAKELDIF_NO_SUCH_TAG;
        String message = getMessage(msgID, tagName, lineNumber);
        throw new MakeLDIFException(msgID, message);
      }
    }

    ArrayList<String> argList = new ArrayList<String>();
    while (tokenizer.hasMoreTokens())
    {
      argList.add(tokenizer.nextToken().trim());
    }

    String[] args = new String[argList.size()];
    argList.toArray(args);


    Tag newTag;
    try
    {
      newTag = t.getClass().newInstance();
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_MAKELDIF_CANNOT_INSTANTIATE_NEW_TAG;
      String message = getMessage(msgID, tagName, lineNumber,
                                  String.valueOf(e));
      throw new MakeLDIFException(msgID, message, e);
    }


    if (branch == null)
    {
      newTag.initializeForTemplate(this, template, args, lineNumber, warnings);
    }
    else
    {
      if (newTag.allowedInBranch())
      {
        newTag.initializeForBranch(this, branch, args, lineNumber, warnings);
      }
      else
      {
        int    msgID   = MSGID_MAKELDIF_TAG_NOT_ALLOWED_IN_BRANCH;
        String message = getMessage(msgID, newTag.getName(), lineNumber);
        throw new MakeLDIFException(msgID, message);
      }
    }

    return newTag;
  }



  /**
   * Parses the provided string as an attribute tag.  Exactly one of the branch
   * or template must be null, and the other must be non-null.
   *
   * @param  tagString   The string containing the encoded tag.
   * @param  branch      The branch in which this tag appears.
   * @param  template    The template in which this tag appears.
   * @param  lineNumber  The line number on which this tag appears in the
   *                     template file.
   * @param  warnings    A list into which any warnings identified may be
   *                     placed.
   *
   * @return  The attribute tag parsed from the provided string.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the tag.
   *
   * @throws  MakeLDIFException  If some other problem occurs during processing.
   */
  private Tag parseAttributeTag(String tagString, Branch branch,
                                Template template, int lineNumber,
                                List<String> warnings)
          throws InitializationException, MakeLDIFException
  {
    // The attribute tag must have at least one argument, which is the name of
    // the attribute to reference.  It may have a second argument, which is the
    // number of characters to use from the attribute value.  The arguments will
    // be delimited by colons.
    StringTokenizer   tokenizer = new StringTokenizer(tagString, ":");
    ArrayList<String> argList   = new ArrayList<String>();
    while (tokenizer.hasMoreTokens())
    {
      argList.add(tokenizer.nextToken());
    }

    String[] args = new String[argList.size()];
    argList.toArray(args);

    AttributeValueTag tag = new AttributeValueTag();
    if (branch == null)
    {
      tag.initializeForTemplate(this, template, args, lineNumber, warnings);
    }
    else
    {
      tag.initializeForBranch(this, branch, args, lineNumber, warnings);
    }

    return tag;
  }



  /**
   * Retrieves a File object based on the provided path.  If the given path is
   * absolute, then that absolute path will be used.  If it is relative, then it
   * will first be evaluated relative to the current working directory.  If that
   * path doesn't exist, then it will be evaluated relative to the resource
   * path.  If that path doesn't exist, then it will be evaluated relative to
   * the directory containing the template file.
   *
   * @param  path  The path provided for the file.
   *
   * @return  The File object for the specified path, or <CODE>null</CODE> if
   *          the specified file could not be found.
   */
  public File getFile(String path)
  {
    // First, see if the file exists using the given path.  This will work if
    // the file is absolute, or it's relative to the current working directory.
    File f = new File(path);
    if (f.exists())
    {
      return f;
    }


    // If the provided path was absolute, then use it anyway, even though we
    // couldn't find the file.
    if (f.isAbsolute())
    {
      return f;
    }


    // Try a path relative to the resource directory.
    String newPath = resourcePath + File.separator + path;
    f = new File(newPath);
    if (f.exists())
    {
      return f;
    }


    // Try a path relative to the template directory, if it's available.
    if (templatePath != null)
    {
      newPath = templatePath = File.separator + path;
      f = new File(newPath);
      if (f.exists())
      {
        return f;
      }
    }

    return null;
  }



  /**
   * Retrieves the lines of the specified file as a string array.  If the result
   * is already cached, then it will be used.  If the result is not cached, then
   * the file data will be cached so that the contents can be re-used if there
   * are multiple references to the same file.
   *
   * @param  file  The file for which to retrieve the contents.
   *
   * @return  An array containing the lines of the specified file.
   *
   * @throws  IOException  If a problem occurs while reading the file.
   */
  public String[] getFileLines(File file)
         throws IOException
  {
    String absolutePath = file.getAbsolutePath();
    String[] lines = fileLines.get(absolutePath);
    if (lines == null)
    {
      ArrayList<String> lineList = new ArrayList<String>();

      BufferedReader reader = new BufferedReader(new FileReader(file));
      while (true)
      {
        String line = reader.readLine();
        if (line == null)
        {
          break;
        }
        else
        {
          lineList.add(line);
        }
      }

      reader.close();

      lines = new String[lineList.size()];
      lineList.toArray(lines);
      lineList.clear();
      fileLines.put(absolutePath, lines);
    }

    return lines;
  }



  /**
   * Generates the LDIF content and writes it to the provided LDIF writer.
   *
   * @param  entryWriter  The entry writer that should be used to write the
   *                      entries.
   *
   * @return  The result that indicates whether processing should continue.
   *
   * @throws  IOException  If an error occurs while writing to the LDIF file.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  public TagResult generateLDIF(EntryWriter entryWriter)
         throws IOException, MakeLDIFException
  {
    for (Branch b : branches.values())
    {
      TagResult result = b.writeEntries(entryWriter);
      if (! (result.keepProcessingTemplateFile()))
      {
        return result;
      }
    }

    entryWriter.closeEntryWriter();
    return TagResult.SUCCESS_RESULT;
  }
}

