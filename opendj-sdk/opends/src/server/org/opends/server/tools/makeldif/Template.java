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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.tools.makeldif;



import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a template, which is a pattern that may be used to
 * generate entries.  A template may be used either below a branch or below
 * another template.
 */
public class Template
{
  // The attribute types that are used in the RDN for entries generated using
  // this template.
  private AttributeType[] rdnAttributes;

  // The number of entries to create for each subordinate template.
  private int[] numEntriesPerTemplate;

  // The name for this template.
  private String name;

  // The names of the subordinate templates below this template.
  private String[] subordinateTemplateNames;

  // The subordinate templates below this template.
  private Template[] subordinateTemplates;

  // The template file that contains this template.
  private TemplateFile templateFile;

  // The set of template lines for this template.
  private TemplateLine[] templateLines;



  /**
   * Creates a new template with the provided information.
   *
   * @param  templateFile              The template file that contains this
   *                                   template.
   * @param  name                      The name for this template.
   * @param  rdnAttributes             The set of attribute types that are used
   *                                   in the RDN for entries generated using
   *                                   this template.
   * @param  subordinateTemplateNames  The names of the subordinate templates
   *                                   below this template.
   * @param  numEntriesPerTemplate     The number of entries to create below
   *                                   each subordinate template.
   */
  public Template(TemplateFile templateFile, String name,
                  AttributeType[] rdnAttributes,
                  String[] subordinateTemplateNames,
                  int[] numEntriesPerTemplate)
  {
    this.templateFile             = templateFile;
    this.name                     = name;
    this.rdnAttributes            = rdnAttributes;
    this.subordinateTemplateNames = subordinateTemplateNames;
    this.numEntriesPerTemplate    = numEntriesPerTemplate;

    templateLines        = new TemplateLine[0];
    subordinateTemplates = null;
  }



  /**
   * Creates a new template with the provided information.
   *
   * @param  templateFile              The template file that contains this
   *                                   template.
   * @param  name                      The name for this template.
   * @param  rdnAttributes             The set of attribute types that are used
   *                                   in the RDN for entries generated using
   *                                   this template.
   * @param  subordinateTemplateNames  The names of the subordinate templates
   *                                   below this template.
   * @param  numEntriesPerTemplate     The number of entries to create below
   *                                   each subordinate template.
   * @param  templateLines             The set of template lines for this
   *                                   template.
   */
  public Template(TemplateFile templateFile, String name,
                  AttributeType[] rdnAttributes,
                  String[] subordinateTemplateNames,
                  int[] numEntriesPerTemplate, TemplateLine[] templateLines)
  {
    this.templateFile             = templateFile;
    this.name                     = name;
    this.rdnAttributes            = rdnAttributes;
    this.subordinateTemplateNames = subordinateTemplateNames;
    this.numEntriesPerTemplate    = numEntriesPerTemplate;
    this.templateLines            = templateLines;

    subordinateTemplates = null;
  }



  /**
   * Performs any necessary processing to ensure that the template
   * initialization is completed.  In particular, it should make sure that all
   * referenced subordinate templates actually exist in the template file, and
   * that all of the RDN attributes are contained in the template lines.
   *
   * @param  templates  The set of templates defined in the template file.
   *
   * @throws  MakeLDIFException  If any of the subordinate templates are not
   *                             defined in the template file.
   */
  public void completeTemplateInitialization(Map<String,Template> templates)
         throws MakeLDIFException
  {
    // Make sure that all of the specified subordinate templates exist.
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
          int    msgID   = MSGID_MAKELDIF_UNDEFINED_TEMPLATE_SUBORDINATE;
          String message = getMessage(msgID, subordinateTemplateNames[i], name);
          throw new MakeLDIFException(msgID, message);
        }
      }
    }


    // Make sure that all of the RDN attributes are defined.
    HashSet<AttributeType> rdnAttrs =
         new HashSet<AttributeType>(rdnAttributes.length);
    for (AttributeType t : rdnAttributes)
    {
      rdnAttrs.add(t);
    }

    for (TemplateLine l : templateLines)
    {
      if (rdnAttrs.remove(l.getAttributeType()))
      {
        if (rdnAttrs.isEmpty())
        {
          break;
        }
      }
    }

    if (! rdnAttrs.isEmpty())
    {
      AttributeType t       = rdnAttrs.iterator().next();
      int           msgID   = MSGID_MAKELDIF_TEMPLATE_MISSING_RDN_ATTR;
      String        message = getMessage(msgID, name, t.getNameOrOID());
      throw new MakeLDIFException(msgID, message);
    }
  }



  /**
   * Retrieves the name for this template.
   *
   * @return  The name for this template.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves the set of attribute types that are used in the RDN for entries
   * generated using this template.
   *
   * @return  The set of attribute types that are used in the RDN for entries
   *          generated using this template.
   */
  public AttributeType[] getRDNAttributes()
  {
    return rdnAttributes;
  }



  /**
   * Retrieves the names of the subordinate templates used to generate entries
   * below entries created by this template.
   *
   * @return  The names of the subordinate templates used to generate entries
   *          below entries created by this template.
   */
  public String[] getSubordinateTemplateNames()
  {
    return subordinateTemplateNames;
  }



  /**
   * Retrieves the subordinate templates used to generate entries below entries
   * created by this template.
   *
   * @return  The subordinate templates used to generate entries below entries
   *          created by this template.
   */
  public Template[] getSubordinateTemplates()
  {
    return subordinateTemplates;
  }



  /**
   * Retrieves the number of entries that should be created for each subordinate
   * template.
   *
   * @return  The number of entries that should be created for each subordinate
   *          template.
   */
  public int[] getNumEntriesPerTemplate()
  {
    return numEntriesPerTemplate;
  }



  /**
   * Retrieves the set of template lines for this template.
   *
   * @return  The set of template lines for this template.
   */
  public TemplateLine[] getTemplateLines()
  {
    return templateLines;
  }



  /**
   * Adds the provided template line to this template.
   *
   * @param  line  The template line to add to this template.
   */
  public void addTemplateLine(TemplateLine line)
  {
    TemplateLine[] newTemplateLines = new TemplateLine[templateLines.length+1];
    System.arraycopy(templateLines, 0, newTemplateLines, 0,
                     templateLines.length);
    newTemplateLines[templateLines.length] = line;
    templateLines = newTemplateLines;
  }



  /**
   * Indicates whether this template contains any template lines that reference
   * the provided attribute type.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if this template contains one or more template
   *          lines that reference the provided attribute type, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasAttribute(AttributeType attributeType)
  {
    for (TemplateLine l : templateLines)
    {
      if (l.getAttributeType().equals(attributeType))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Writes the entry for this template, as well as all appropriate subordinate
   * entries.
   *
   * @param  entryWriter  The entry writer that will be used to write the
   *                      entries.
   * @param  parentDN     The DN of the entry below which the subordinate
   *                      entries should be generated.
   * @param  count        The number of entries to generate based on this
   *                      template.
   *
   * @return  The result that indicates whether processing should continue.
   *
   * @throws  IOException  If a problem occurs while attempting to write to the
   *                       LDIF writer.
   *
   * @throws  MakeLDIFException  If some other problem occurs.
   */
  public TagResult writeEntries(EntryWriter entryWriter, DN parentDN, int count)
         throws IOException, MakeLDIFException
  {
    for (int i=0; i < count; i++)
    {
      templateFile.nextFirstAndLastNames();
      TemplateEntry templateEntry = new TemplateEntry(this, parentDN);

      for (TemplateLine l : templateLines)
      {
        TagResult r = l.generateLine(templateEntry);
        if (! (r.keepProcessingEntry() && r.keepProcessingParent() &&
               r.keepProcessingTemplateFile()))
        {
          return r;
        }
      }

      Entry entry = templateEntry.toEntry();
      if (! entryWriter.writeEntry(entry))
      {
        return TagResult.STOP_PROCESSING;
      }

      for (int j=0; j < subordinateTemplates.length; j++)
      {
        TagResult r =
             subordinateTemplates[j].writeEntries(entryWriter, entry.getDN(),
                                                  numEntriesPerTemplate[j]);
        if (! (r.keepProcessingParent() && r.keepProcessingTemplateFile()))
        {
          if (r.keepProcessingTemplateFile())
          {
            // We don't want to propagate a "stop processing parent" all the
            // way up the chain.
            return TagResult.SUCCESS_RESULT;
          }

          return r;
        }
      }
    }

    return TagResult.SUCCESS_RESULT;
  }
}

