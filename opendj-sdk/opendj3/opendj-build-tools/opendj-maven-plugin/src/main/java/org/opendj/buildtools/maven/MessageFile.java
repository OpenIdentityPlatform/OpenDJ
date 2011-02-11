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



import java.io.File;



/**
 * A message file descriptor passed in from the POM configuration.
 */
public final class MessageFile
{
  private final int category = -1;

  private final String name = null;

  private final boolean useOrdinals = false;



  /**
   * Creates a new message file.
   */
  public MessageFile()
  {
    // Default constructor required for injection.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "MessageFile [category=" + category + ", name=" + name + "]";
  }



  String getBaseName()
  {
    return getPackageName() + "." + getShortName();
  }



  /**
   * The category ID.
   * 
   * @return The category ID.
   */
  int getCategory()
  {
    return category;
  }



  String getClassName()
  {
    final String shortName = getShortName();
    final StringBuilder builder = new StringBuilder(shortName.length());
    builder.append(Character.toUpperCase(shortName.charAt(0)));
    builder.append(shortName.substring(1));
    return builder.toString();
  }



  /**
   * The name of the message file relative to the resource directory.
   * 
   * @return The name of the message file relative to the resource directory.
   */
  String getName()
  {
    return name;
  }



  File getOutputFile(final File outputDirectory)
  {
    final int lastSlash = name.lastIndexOf('/');
    final String parentPath = name.substring(0, lastSlash);
    final String path = parentPath.replace('/', File.separatorChar)
        + File.separator + getClassName() + ".java";
    return new File(outputDirectory, path);
  }



  String getPackageName()
  {
    final int lastSlash = name.lastIndexOf('/');
    final String parentPath = name.substring(0, lastSlash);
    return parentPath.replace('/', '.');
  }



  String getShortName()
  {
    final int lastSlash = name.lastIndexOf('/');
    final String fileName = name.substring(lastSlash + 1);
    final int lastDot = fileName.lastIndexOf('.');
    return fileName.substring(0, lastDot);
  }



  File getSourceFile(final File sourceDirectory)
  {
    final String path = name.replace('/', File.separatorChar);
    return new File(sourceDirectory, path);
  }



  /**
   * Returns {@code true} if this message file contains numbered messages.
   * 
   * @return {@code true} if this message file contains numbered messages.
   */
  boolean useOrdinals()
  {
    return useOrdinals;
  }

}
