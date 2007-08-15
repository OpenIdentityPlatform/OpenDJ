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
package org.opends.server.util.args;
import org.opends.messages.Message;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;

import static org.opends.messages.UtilityMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an argument whose value will be read from a file rather
 * than actually specified on the command-line.  When a value is specified on
 * the command line, it will be treated as the path to the file containing the
 * actual value rather than the value itself.
 * <BR><BR>
 * Note that if if no filename is provided on the command line but a default
 * value is specified programatically or if the default value is read from a
 * specified property, then that default value will be taken as the actual value
 * rather than a filename.
 * <BR><BR>
 * Also note that this argument type assumes that the entire value for the
 * argument is on a single line in the specified file.  If the file contains
 * multiple lines, then only the first line will be read.
 */
public class FileBasedArgument
       extends Argument
{
  // The mapping between filenames specified and the first lines read from those
  // files.
  private LinkedHashMap<String,String> namesToValues;



  /**
   * Creates a new file-based argument with the provided information.
   *
   * @param  name              The generic name that should be used to refer to
   *                           this argument.
   * @param  shortIdentifier   The single-character identifier for this
   *                           argument, or <CODE>null</CODE> if there is none.
   * @param  longIdentifier    The long identifier for this argument, or
   *                           <CODE>null</CODE> if there is none.
   * @param  isRequired        Indicates whether this argument must be specified
   *                           on the command line.
   * @param  valuePlaceholder  The placeholder for the argument value that will
   *                           be displayed in usage information, or
   *                           <CODE>null</CODE> if this argument does not
   *                           require a value.
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public FileBasedArgument(String name, Character shortIdentifier,
                           String longIdentifier, boolean isRequired,
                           String valuePlaceholder,
                           Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, false, true,
          valuePlaceholder, null, null, description);


    namesToValues = new LinkedHashMap<String,String>();
  }



  /**
   * Creates a new file-based argument with the provided information.
   *
   * @param  name              The generic name that should be used to refer to
   *                           this argument.
   * @param  shortIdentifier   The single-character identifier for this
   *                           argument, or <CODE>null</CODE> if there is none.
   * @param  longIdentifier    The long identifier for this argument, or
   *                           <CODE>null</CODE> if there is none.
   * @param  isRequired        Indicates whether this argument must be specified
   *                           on the command line.
   * @param  isMultiValued     Indicates whether this argument may be specified
   *                           more than once to provide multiple values.
   * @param  valuePlaceholder  The placeholder for the argument value that will
   *                           be displayed in usage information, or
   *                           <CODE>null</CODE> if this argument does not
   *                           require a value.
   * @param  defaultValue      The default value that should be used for this
   *                           argument if none is provided in a properties file
   *                           or on the command line.  This may be
   *                           <CODE>null</CODE> if there is no generic default.
   * @param  propertyName      The name of the property in a property file that
   *                           may be used to override the default value but
   *                           will be overridden by a command-line argument.
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public FileBasedArgument(String name, Character shortIdentifier,
                           String longIdentifier, boolean isRequired,
                           boolean isMultiValued, String valuePlaceholder,
                           String defaultValue, String propertyName,
                           Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, isMultiValued,
          true, valuePlaceholder, defaultValue, propertyName,
          description);

    namesToValues = new LinkedHashMap<String,String>();
  }



  /**
   * Retrieves a map between the filenames specified on the command line and the
   * first lines read from those files.
   *
   * @return  A map between the filenames specified on the command line and the
   *          first lines read from those files.
   */
  public LinkedHashMap<String,String> getNameToValueMap()
  {
    return namesToValues;
  }



  /**
   * Indicates whether the provided value is acceptable for use in this
   * argument.
   *
   * @param  valueString    The value for which to make the determination.
   * @param  invalidReason  A buffer into which the invalid reason may be
   *                        written if the value is not acceptable.
   *
   * @return  <CODE>true</CODE> if the value is acceptable, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean valueIsAcceptable(String valueString,
                                   MessageBuilder invalidReason)
  {
    // First, make sure that the specified file exists.
    File valueFile;
    try
    {
      valueFile = new File(valueString);
      if (! valueFile.exists())
      {
        invalidReason.append(ERR_FILEARG_NO_SUCH_FILE.get(
                valueString, getName()));
        return false;
      }
    }
    catch (Exception e)
    {
      invalidReason.append(ERR_FILEARG_CANNOT_VERIFY_FILE_EXISTENCE.get(
              valueString, getName(),
              getExceptionMessage(e)));
      return false;
    }


    // Open the file for reading.
    BufferedReader reader;
    try
    {
      reader = new BufferedReader(new FileReader(valueFile));
    }
    catch (Exception e)
    {
      invalidReason.append(ERR_FILEARG_CANNOT_OPEN_FILE.get(
              valueString, getName(),
              getExceptionMessage(e)));
      return false;
    }


    // Read the first line and close the file.
    String line;
    try
    {
      line = reader.readLine();
    }
    catch (Exception e)
    {
      invalidReason.append(ERR_FILEARG_CANNOT_READ_FILE.get(
              valueString, getName(),
              getExceptionMessage(e)));
      return false;
    }
    finally
    {
      try
      {
        reader.close();
      } catch (Exception e) {}
    }


    // If the line read is null, then that means the file was empty.
    if (line == null)
    {

      invalidReason.append(ERR_FILEARG_EMPTY_FILE.get(valueString, getName()));
      return false;
    }


    // Store the value in the hash so it will be available for addValue.  We
    // won't do any validation on the value itself, so anything that we read
    // will be considered acceptable.
    namesToValues.put(valueString, line);
    return true;
  }



  /**
   * Adds a value to the set of values for this argument.  This should only be
   * called if the value is allowed by the <CODE>valueIsAcceptable</CODE>
   * method.  Note that in this case, correct behavior depends on a previous
   * successful call to <CODE>valueIsAcceptable</CODE> so that the value read
   * from the file may be stored in the name-to-value hash and used in place of
   * the filename here.
   *
   * @param  valueString  The string representation of the value to add to this
   *                      argument.
   */
  public void addValue(String valueString)
  {
    String actualValue = namesToValues.get(valueString);
    if (actualValue != null)
    {
      super.addValue(actualValue);
    }
  }
}

