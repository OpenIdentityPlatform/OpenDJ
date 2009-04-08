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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;
import org.opends.messages.Message;



import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a branch that should be included in the resulting LDIF.  A
 * branch may or may not have subordinate entries.
 */
public class Branch
{
  // The DN for this branch entry.
  private DN branchDN;

  // The number of entries that should be created below this branch for each
  // subordinate template.
  private int[] numEntriesPerTemplate;

  // The names of the subordinate templates for this branch.
  private String[] subordinateTemplateNames;

  // The set of subordinate templates for this branch.
  private Template[] subordinateTemplates;

  // The set of template lines that correspond to the RDN components.
  private TemplateLine[] rdnLines;

  // The set of extra lines that should be included in this branch entry.
  private TemplateLine[] extraLines;



  /**
   * Creates a new branch with the provided information.
   *
   * @param  templateFile  The template file in which this branch appears.
   * @param  branchDN      The DN for this branch entry.
   */
  public Branch(TemplateFile templateFile, DN branchDN)
  {
    this(templateFile, branchDN, new String[0], new int[0],
         new TemplateLine[0]);
  }



  /**
   * Creates a new branch with the provided information.
   *
   * @param  templateFile              The template file in which this branch
   *                                   appears.
   * @param  branchDN                  The DN for this branch entry.
   * @param  subordinateTemplateNames  The names of the subordinate templates
   *                                   used to generate entries below this
   *                                   branch.
   * @param  numEntriesPerTemplate     The number of entries that should be
   *                                   created below this branch for each
   *                                   subordinate template.
   * @param  extraLines                The set of extra lines that should be
   *                                   included in this branch entry.
   */
  public Branch(TemplateFile templateFile, DN branchDN,
                String[] subordinateTemplateNames, int[] numEntriesPerTemplate,
                TemplateLine[] extraLines)
  {
    this.branchDN                 = branchDN;
    this.subordinateTemplateNames = subordinateTemplateNames;
    this.numEntriesPerTemplate    = numEntriesPerTemplate;
    this.extraLines               = extraLines;

    subordinateTemplates = null;


    // Get the RDN template lines based just on the entry DN.
    Entry entry = createEntry(branchDN);

    ArrayList<Message>       warnings = new ArrayList<Message>();
    ArrayList<TemplateLine> lineList = new ArrayList<TemplateLine>();

    for (String ocName : entry.getObjectClasses().values())
    {
      try
      {
        String[] valueStrings = new String[] { ocName };
        Tag[] tags = new Tag[1];
        tags[0] = new StaticTextTag();
        tags[0].initializeForBranch(templateFile, this, valueStrings, 0,
                                    warnings);

        TemplateLine l =
             new TemplateLine(DirectoryServer.getObjectClassAttributeType(), 0,
                              tags);
        lineList.add(l);
      }
      catch (Exception e)
      {
        // This should never happen.
        e.printStackTrace();
      }
    }

    for (List<Attribute> attrList : entry.getUserAttributes().values())
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          try
          {
            String[] valueStrings = new String[] { v.getValue().toString()};
            Tag[] tags = new Tag[1];
            tags[0] = new StaticTextTag();
            tags[0].initializeForBranch(templateFile, this, valueStrings, 0,
                                        warnings);
            lineList.add(new TemplateLine(a.getAttributeType(), 0, tags));
          }
          catch (Exception e)
          {
            // This should never happen.
            e.printStackTrace();
          }
        }
      }
    }

    for (List<Attribute> attrList : entry.getOperationalAttributes().values())
    {
      for (Attribute a : attrList)
      {
        for (AttributeValue v : a)
        {
          try
          {
            String[] valueStrings = new String[] { v.getValue().toString()};
            Tag[] tags = new Tag[1];
            tags[0] = new StaticTextTag();
            tags[0].initializeForBranch(templateFile, this, valueStrings, 0,
                                        warnings);
            lineList.add(new TemplateLine(a.getAttributeType(), 0, tags));
          }
          catch (Exception e)
          {
            // This should never happen.
            e.printStackTrace();
          }
        }
      }
    }

    rdnLines = new TemplateLine[lineList.size()];
    lineList.toArray(rdnLines);
  }



  /**
   * Performs any necessary processing to ensure that the branch initialization
   * is completed.  In particular, it should make sure that all referenced
   * subordinate templates actually exist in the template file.
   *
   * @param  templates  The set of templates defined in the template file.
   *
   * @throws  MakeLDIFException  If any of the subordinate templates are not
   *                             defined in the template file.
   */
  public void completeBranchInitialization(Map<String,Template> templates)
         throws MakeLDIFException
  {
    if (subordinateTemplateNames == null)
    {
      subordinateTemplateNames = new String[0];
      subordinateTemplates     = new Template[0];
    }
    else
    {
      subordinateTemplates = new Template[subordinateTemplateNames.length];
      for (int i=0; i < subordinateTemplates.length; i++)
      {
        subordinateTemplates[i] =
             templates.get(toLowerCase(subordinateTemplateNames[i]));
        if (subordinateTemplates[i] == null)
        {
          Message message = ERR_MAKELDIF_UNDEFINED_BRANCH_SUBORDINATE.get(
              subordinateTemplateNames[i], branchDN.toString());
          throw new MakeLDIFException(message);
        }
      }
    }
  }



  /**
   * Retrieves the DN for this branch entry.
   *
   * @return  The DN for this branch entry.
   */
  public DN getBranchDN()
  {
    return branchDN;
  }



  /**
   * Retrieves the names of the subordinate templates for this branch.
   *
   * @return  The names of the subordinate templates for this branch.
   */
  public String[] getSubordinateTemplateNames()
  {
    return subordinateTemplateNames;
  }



  /**
   * Retrieves the set of subordinate templates used to generate entries below
   * this branch.  Note that the subordinate templates will not be available
   * until the <CODE>completeBranchInitialization</CODE> method has been called.
   *
   * @return  The set of subordinate templates used to generate entries below
   *          this branch.
   */
  public Template[] getSubordinateTemplates()
  {
    return subordinateTemplates;
  }



  /**
   * Retrieves the number of entries that should be created below this branch
   * for each subordinate template.
   *
   * @return  The number of entries that should be created below this branch for
   *          each subordinate template.
   */
  public int[] getNumEntriesPerTemplate()
  {
    return numEntriesPerTemplate;
  }



  /**
   * Adds a new subordinate template to this branch.  Note that this should not
   * be used after <CODE>completeBranchInitialization</CODE> has been called.
   *
   * @param  name        The name of the template to use to generate the
   *                     entries.
   * @param  numEntries  The number of entries to create based on the template.
   */
  public void addSubordinateTemplate(String name, int numEntries)
  {
    String[] newNames  = new String[subordinateTemplateNames.length+1];
    int[]    newCounts = new int[numEntriesPerTemplate.length+1];

    System.arraycopy(subordinateTemplateNames, 0, newNames, 0,
                     subordinateTemplateNames.length);
    System.arraycopy(numEntriesPerTemplate, 0, newCounts, 0,
                     numEntriesPerTemplate.length);

    newNames[subordinateTemplateNames.length] = name;
    newCounts[numEntriesPerTemplate.length]   = numEntries;

    subordinateTemplateNames = newNames;
    numEntriesPerTemplate    = newCounts;
  }



  /**
   * Retrieves the set of extra lines that should be included in this branch
   * entry.
   *
   * @return  The set of extra lines that should be included in this branch
   *          entry.
   */
  public TemplateLine[] getExtraLines()
  {
    return extraLines;
  }



  /**
   * Adds the provided template line to the set of extra lines for this branch.
   *
   * @param  line  The line to add to the set of extra lines for this branch.
   */
  public void addExtraLine(TemplateLine line)
  {
    TemplateLine[] newExtraLines = new TemplateLine[extraLines.length+1];
    System.arraycopy(extraLines, 0, newExtraLines, 0, extraLines.length);
    newExtraLines[extraLines.length] = line;

    extraLines = newExtraLines;
  }



  /**
   * Indicates whether this branch contains a reference to the specified
   * attribute type, either in the RDN components of the DN or in the extra
   * lines.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the branch does contain the specified
   *          attribute type, or <CODE>false</CODE> if it does not.
   */
  public boolean hasAttribute(AttributeType attributeType)
  {
    if (branchDN.getRDN().hasAttributeType(attributeType))
    {
      return true;
    }

    for (TemplateLine l : extraLines)
    {
      if (l.getAttributeType().equals(attributeType))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Writes the entry for this branch, as well as all appropriate subordinate
   * entries.
   *
   * @param  entryWriter  The entry writer to which the entries should be
   *                      written.
   *
   * @return  The result that indicates whether processing should continue.
   *
   * @throws  IOException  If a problem occurs while attempting to write to the
   *                       LDIF writer.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  public TagResult writeEntries(EntryWriter entryWriter)
         throws IOException, MakeLDIFException
  {
    // Create a new template entry and populate it based on the RDN attributes
    // and extra lines.
    TemplateEntry entry = new TemplateEntry(this);

    for (TemplateLine l : rdnLines)
    {
      TagResult r = l.generateLine(entry);
      if (! (r.keepProcessingEntry() && r.keepProcessingParent() &&
             r.keepProcessingTemplateFile()))
      {
        return r;
      }
    }

    for (TemplateLine l : extraLines)
    {
      TagResult r = l.generateLine(entry);
      if (! (r.keepProcessingEntry() && r.keepProcessingParent() &&
             r.keepProcessingTemplateFile()))
      {
        return r;
      }
    }

    if (! entryWriter.writeEntry(entry))
    {
      return TagResult.STOP_PROCESSING;
    }


    for (int i=0; i < subordinateTemplates.length; i++)
    {
      TagResult r =
           subordinateTemplates[i].writeEntries(entryWriter, branchDN,
                                                numEntriesPerTemplate[i]);
      if (! (r.keepProcessingParent() && r.keepProcessingTemplateFile()))
      {
        if (r.keepProcessingTemplateFile())
        {
          // We don't want to propagate a "stop processing parent" all the way
          // up the chain.
          return TagResult.SUCCESS_RESULT;
        }

        return r;
      }
    }

    return TagResult.SUCCESS_RESULT;
  }
}

