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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



/**
 * This class defines a data structure that provides information about the
 * result of tag processing.
 */
public class TagResult
{
  /**
   * A tag result in which all components have a value of <CODE>true</CODE>.
   */
  public static final TagResult SUCCESS_RESULT =
       new TagResult(true, true, true, true);



  /**
   * A tag result that indicates the value should not be included in the entry,
   * but all other processing should continue.
   */
  public static final TagResult OMIT_FROM_ENTRY =
       new TagResult(false, true, true, true);



  /**
   * A tag result in whihc all components have a value of <CODE>false</CODE>.
   */
  public static final TagResult STOP_PROCESSING =
       new TagResult(false, false, false, false);



  // Indicates whether to keep processing the associated line.
  private boolean keepProcessingLine;

  // Indicates whether to keep processing the associated entry.
  private boolean keepProcessingEntry;

  // Indicates whether to keep processing entries below the associated parent.
  private boolean keepProcessingParent;

  // Indicates whether to keep processing entries for the template file.
  private boolean keepProcessingTemplateFile;



  /**
   * Creates a new tag result object with the provided information.
   *
   * @param  keepProcessingLine          Indicates whether to continue
   *                                     processing for the current line.  If
   *                                     not, then the line will not be included
   *                                     in the entry.
   * @param  keepProcessingEntry         Indicates whether to continue
   *                                     processing for the current entry.  If
   *                                     not, then the entry will not be
   *                                     included in the data.
   * @param  keepProcessingParent        Indicates whether to continue
   *                                     processing entries below the current
   *                                     parent in the template file.
   * @param  keepProcessingTemplateFile  Indicates whether to continue
   *                                     processing entries for the template
   *                                     file.
   */
  public TagResult(boolean keepProcessingLine, boolean keepProcessingEntry,
                   boolean keepProcessingParent,
                   boolean keepProcessingTemplateFile)
  {
    this.keepProcessingLine         = keepProcessingLine;
    this.keepProcessingEntry        = keepProcessingEntry;
    this.keepProcessingParent       = keepProcessingParent;
    this.keepProcessingTemplateFile = keepProcessingTemplateFile;
  }



  /**
   * Indicates whether to continue processing for the current line.  If this is
   * <CODE>false</CODE>, then the current line will not be included in the
   * entry.  It will have no impact on whehter the entry itself is included in
   * the generated LDIF.
   *
   * @return  <CODE>true</CODE> if the line should be included in the entry, or
   *          <CODE>false</CODE> if not.
   */
  public boolean keepProcessingLine()
  {
    return keepProcessingLine;
  }



  /**
   * Indicates whether to continue processing for the current entry.  If this is
   * <CODE>false</CODE>, then the current entry will not be included in the
   * generated LDIF, and processing will resume with the next entry below the
   * current parent.
   *
   * @return  <CODE>true</CODE> if the entry should be included in the
   *          generated LDIF, or <CODE>false</CODE> if not.
   */
  public boolean keepProcessingEntry()
  {
    return keepProcessingEntry;
  }



  /**
   * Indicates whether to continue processing entries below the current parent.
   * If this is <CODE>false</CODE>, then the current entry will not be included,
   * and processing will resume below the next parent in the template file.
   *
   * @return  <CODE>true</CODE> if processing for the current parent should
   *          continue, or <CODE>false</CODE> if not.
   */
  public boolean keepProcessingParent()
  {
    return keepProcessingParent;
  }



  /**
   * Indicates whether to keep processing entries for the template file.  If
   * this is <CODE>false</CODE>, then LDIF processing will end immediately (and
   * the current entry will not be included).
   *
   * @return  <CODE>true</CODE> if processing for the template file should
   *          continue, or <CODE>false</CODE> if not.
   */
  public boolean keepProcessingTemplateFile()
  {
    return keepProcessingTemplateFile;
  }
}

