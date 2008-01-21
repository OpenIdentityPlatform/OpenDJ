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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.doc;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import org.opends.messages.Message;
import org.opends.server.admin.ACIPropertyDefinition;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AdministratorAction.Type;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.AttributeTypePropertyDefinition;
import org.opends.server.admin.BooleanPropertyDefinition;
import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.DNPropertyDefinition;
import org.opends.server.admin.DefaultBehaviorProvider;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DurationPropertyDefinition;
import org.opends.server.admin.EnumPropertyDefinition;
import org.opends.server.admin.IPAddressMaskPropertyDefinition;
import org.opends.server.admin.IPAddressPropertyDefinition;
import org.opends.server.admin.IntegerPropertyDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.SizePropertyDefinition;
import org.opends.server.admin.StringPropertyDefinition;
import org.opends.server.admin.TopCfgDefn;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.types.InitializationException;
import org.opends.server.util.EmbeddedUtils;

/**
 *  This class allow Configuration Guide documentation generation (html format).
 * It is based on the Admin Framework Introspection API
 *
 */
public class ConfigGuideGeneration {

  // Note : still to be done :
  // I18n support. Today all the strings are hardcoded in this file

  private final String OPENDS_WIKI = "https://www.opends.org/wiki";
  private final String ACI_SYNTAX_PAGE = OPENDS_WIKI + "/page/ACISyntax";
  private final String CSS_FILE = "opends-config.css";

  /**
   * Entry point for documentation generation.
   *
   * @param args The output generation directory (optional)
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      // Default dir is prefixed by the system-dependent default temporary dir
      generationDir = System.getProperty("java.io.tmpdir") + File.separator +
        "opends_config_guide";
    } else if ((args.length != 1) || !(new File(args[0])).isDirectory()) {
      usage();
    } else {
      generationDir = args[0];
    }
    // Create new dir if necessary
    try {
      (new File(generationDir)).mkdir();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    System.out.println("Generation directory is : " + generationDir);
    ConfigGuideGeneration myGen = new ConfigGuideGeneration();
    myGen.generate();
  }

  private void generate() {
    init();

    // Generate the inheritance tree of all the managed objects
    genManagedObjectInheritanceTree(topMoList);

    // Generate the relation tree of all the managed objects
    genManagedObjectRelationTree(topRelList);

    // Generate all the managed objects and their children
    genAllManagedObject(topMoList);

    // Generate a list of managed objects
    genManagedObjectList(moList);

    // Generate an index of properties
    genPropertiesIndex();
  }

  private void init() {

    // Build a list of top relations
    RootCfgDefn rootCfg = RootCfgDefn.getInstance();
    for (RelationDefinition rel : rootCfg.getAllRelationDefinitions()) {
      topRelList.put(rel.getChildDefinition().getName(), rel);
    }

    // Enable the client-side class loader to explicitly load classes
    // which are not directly reachable from the root configuration
    EmbeddedUtils.initializeForClientUse();
    // Bootstrap definition classes.
    try {
      ClassLoaderProvider.getInstance().enable();
    } catch (InitializationException e) {
      System.err.println("ERROR : Cannot enable the client-side class loader.");
      e.printStackTrace();
      System.exit(1);
    }
    // Switch off class name validation in client.
    ClassPropertyDefinition.setAllowClassValidation(false);
    // Switch off attribute type name validation in client.
    AttributeTypePropertyDefinition.setCheckSchema(false);

    // Build a sorted list of top managed objects
    TopCfgDefn topCfg = TopCfgDefn.getInstance();
    Collection<AbstractManagedObjectDefinition<?, ?>> topObjects =
      topCfg.getChildren();
    for (AbstractManagedObjectDefinition topObject : topObjects) {
      if (topObject.getName().equals("")) {
        // root
        continue;
      }
      topMoList.put(topObject.getName(), topObject);
    }

  }

  /**
   * Generate the inheritance tree of all the managed objects.
   */
  private void genManagedObjectInheritanceTree(
    TreeMap<String, AbstractManagedObjectDefinition> list) {

    htmlHeader("OpenDS - Configuring Specific Server Components - " +
      "Inheritance tree");
    heading2("Configuring Specific Server Components - Inheritance tree");
    genMoInheritanceTree(list);
    generateFile("ManagedObjectInheritanceTree.html");
  }

  @SuppressWarnings("unchecked")
  private void genMoInheritanceTree(
    TreeMap<String, AbstractManagedObjectDefinition> list) {

    beginList();
    for (AbstractManagedObjectDefinition mo : list.values()) {
      if (listLevel == 1) {
        paragraph(
          getLink(mo.getUserFriendlyPluralName().toString(),
          mo.getName() + ".html"));
      } else {
        link(mo.getUserFriendlyName().toString(), mo.getName() + ".html");
      }
      if (mo.hasChildren()) {
        genMoInheritanceTree(makeMOTreeMap(mo.getChildren()));
      }
    }
    endList();
    if (listLevel == 1) {
      newline();
    }
  }

  /**
   * Generate the relation tree of all the managed objects.
   */
  private void genManagedObjectRelationTree(
    TreeMap<String, RelationDefinition> list) {

    htmlHeader("OpenDS - Configuring Specific Server Components - " +
      "Containment tree");
    heading2("Configuring Specific Server Components - Containment tree");
    paragraph(
      "This tree represents the composition relation between components. " +
      "This means that a child component is deleted " +
      "when its parent is deleted.");
    genMORelationTree(list);
    generateFile("ManagedObjectRelationTree.html");
  }

  @SuppressWarnings("unchecked")
  private void genMORelationTree(TreeMap<String, RelationDefinition> subList) {
    if (!subList.values().isEmpty()) {
      beginList();
      for (RelationDefinition rel : subList.values()) {
        AbstractManagedObjectDefinition childMo = rel.getChildDefinition();
        AbstractManagedObjectDefinition parentMo = rel.getParentDefinition();
        relList.put(childMo.getName(), rel);
        String linkStr = getLink(childMo.getUserFriendlyName().toString(),
          childMo.getName() + ".html");
        String fromStr = "";
        if (!parentMo.getName().equals("")) {
          fromStr = " (from " +
            getLink(parentMo.getUserFriendlyName().toString(),
            parentMo.getName() + ".html") + ")";
        }
        bullet(linkStr + fromStr);
        genMORelationTree(makeRelTreeMap(childMo.getAllRelationDefinitions()));
        if (childMo.hasChildren()) {
          for (Iterator<AbstractManagedObjectDefinition> it =
            childMo.getChildren().iterator(); it.hasNext();) {

            AbstractManagedObjectDefinition mo = it.next();
            genMORelationTree(makeRelTreeMap(mo.getAllRelationDefinitions()));
          }
        }
        if (listLevel == 1) {
          newline();
        }
      }
      endList();
    }
  }

  /**
   * Generate all the managed objects HTML pages.
   */
  @SuppressWarnings("unchecked")
  private void genAllManagedObject(
    TreeMap<String, AbstractManagedObjectDefinition> list) {

    for (AbstractManagedObjectDefinition mo : list.values()) {
      moList.put(mo.getName(), mo);
      genManagedObject(mo);
      if (mo.hasChildren()) {
        genAllManagedObject(makeMOTreeMap(mo.getChildren()));
      }
    }
  }

  private void genManagedObject(AbstractManagedObjectDefinition mo) {
    //------------------------------------------------------------------------
    // Header
    //------------------------------------------------------------------------

    String title = "The " + mo.getUserFriendlyName() + " Configuration";
    htmlHeader("OpenDS - " + title);

    // title
    heading2(title);

    // description
    paragraph(mo.getSynopsis());
    paragraph(mo.getDescription());

    newline();
    horizontalLine();

    // sub-components
    if (mo.hasChildren()) {
      heading4("Direct Subcomponents");
      paragraph("The following " + mo.getUserFriendlyPluralName() +
        " are available in the server :");
      beginList();
      @SuppressWarnings("unchecked")
      TreeMap<String, AbstractManagedObjectDefinition> children =
        makeMOTreeMap(mo.getChildren());
      for ( AbstractManagedObjectDefinition child : children.values()) {
        link(child.getUserFriendlyName().toString(), child.getName() + ".html");
      }
      endList();

      paragraph("All the " + mo.getUserFriendlyPluralName() +
        " inherit from the properties described below.");
    }

    // Parent
    if (!mo.getParent().isTop()) {
      heading4("Parent Component");
      paragraph("The " + mo.getUserFriendlyName() + " component inherits from "
        + getLink(mo.getParent().getUserFriendlyName().toString(),
        mo.getParent().getName() + ".html"));
    }

    // Relations
    if (!mo.getRelationDefinitions().isEmpty()) {
      heading4("Relations From this Component");
      paragraph(
        "The following components have a direct composition relation FROM " +
        mo.getUserFriendlyPluralName() + " :");
      @SuppressWarnings("unchecked")
      Collection<RelationDefinition> rels = mo.getRelationDefinitions();
      for ( RelationDefinition rel : rels) {
        beginList();
        AbstractManagedObjectDefinition childRel = rel.getChildDefinition();
        link(childRel.getUserFriendlyName().toString(), childRel.getName() +
          ".html");
        endList();
      }
    }
    if (!mo.getReverseRelationDefinitions().isEmpty()) {
      boolean isRoot = false;
      @SuppressWarnings("unchecked")
      Collection<RelationDefinition> rels = mo.getReverseRelationDefinitions();
      for ( RelationDefinition rel : rels) {
        // only check if it is not root
        if (rel.getParentDefinition().getName().equals("")) {
          isRoot = true;
        }
      }
      if (!isRoot) {
        heading4("Relations To this Component");
        paragraph(
          "The following components have a direct composition relation TO " +
          mo.getUserFriendlyPluralName() + " :");
        for ( RelationDefinition rel : rels) {
          beginList();
          AbstractManagedObjectDefinition childRel = rel.getParentDefinition();
          link(childRel.getUserFriendlyName().toString(), childRel.getName() +
            ".html");
          endList();
        }
      }
    }

    newline();
    horizontalLine();
    newline();

    // Page links
    paragraph("This page describes the " + mo.getUserFriendlyName() + ":");
    beginList();
    link("Properties", "#Properties");
    link("LDAP Mapping", "#LDAP Mapping");
    endList();
    newline();


    //------------------------------------------------------------------------
    // Properties
    //------------------------------------------------------------------------

    heading3("Properties");

    paragraph(mo.getUserFriendlyPluralName() +
      " contain the following properties:");
    newline();

    // Properties actually defined in this managed object
    @SuppressWarnings("unchecked")
    Collection<PropertyDefinition> props = mo.getAllPropertyDefinitions();
    TreeMap<String, PropertyDefinition> propList = makePropTreeMap(props);
    for ( PropertyDefinition prop : propList.values()) {
      generateProperty(mo, prop);
      newline();
    }

    newline();

    //------------------------------------------------------------------------
    // LDAP mapping
    //------------------------------------------------------------------------

    heading3("LDAP Mapping");
    paragraph(
      "Each dscfg configuration property can be mapped to a specific " +
      "LDAP attribute under the \"cn=config\" entry. " +
      "The mappings that follow are provided for information only. " +
      "In general, you should avoid changing the server configuration " +
      "by manipulating the LDAP attributes directly.");

    // Managed object table
    startTable();

    LDAPProfile ldapProfile = LDAPProfile.getInstance();
    tableRow("Base DN", getBaseDN(mo, ldapProfile));

    tableRow("objectclass name", ldapProfile.getObjectClass(mo));
    if (mo.getParent().getName() != null) {
      String superior = "";
      if (mo.getParent().getName().equals("top")) {
        superior = "top";
      } else {
        if (moList.get(mo.getParent().getName()) != null) {
          superior =
            ldapProfile.getObjectClass(moList.get(mo.getParent().getName()));
        } else {
          System.err.println(
            "Error: managed object " + mo.getName() + " not found.");
        }
      }
      tableRow("objectclass superior", superior);
    } else {
      System.err.println(
        "Error: objectclass superior not found for " + mo.getName());
    }
    endTable();

    newline();
    // Properties table
    startTable();
    tableRow("Property", "LDAP attribute");
    for ( PropertyDefinition prop : propList.values()) {
      tableRow(prop.getName(), ldapProfile.getAttributeName(mo, prop));
    }

    endTable();

    htmlFooter();

    generateFile(mo.getName() + ".html");
  }

  private void generateProperty(
    AbstractManagedObjectDefinition mo, PropertyDefinition prop) {

    // Property name
    paragraph(getAnchor(prop.getName()) + prop.getName(), TextStyle.BOLD);

    // Property table
    startTable();
    tableRow("Description",
      ((prop.getSynopsis() != null) ? prop.getSynopsis().toString()+ " " : "") +
      ((prop.getDescription() != null) ?
        prop.getDescription().toString() : ""));

    // Default value
    String defValueStr = getDefaultBehaviorString(prop);
    tableRow("Default Value", defValueStr);

    tableRow("Allowed Values", getSyntaxStr(prop));

    tableRow("Multi-valued",
      (prop.hasOption(PropertyOption.MULTI_VALUED) ? "Yes" : "No"));

    if (prop.hasOption(PropertyOption.MANDATORY)) {
      tableRow("Required", "Yes");
    }

    if (prop.getAdministratorAction() != null) {
      Message synopsis = prop.getAdministratorAction().getSynopsis();
      Type actionType = prop.getAdministratorAction().getType();
      String actionStr = "";
      if (actionType == actionType.COMPONENT_RESTART) {
        actionStr = "The " + mo.getUserFriendlyName() +
          " must be disabled and re-enabled for changes to this setting " +
          "to take effect";
      } else if (actionType == actionType.SERVER_RESTART) {
        actionStr = "Restart the server";
      } else if (actionType == actionType.NONE) {
        actionStr = "None";
      }
      String action = actionStr +
        ((synopsis != null) ? ". " + synopsis : "");
      tableRow("Admin Action Required", action);
    }


    if (prop.hasOption(PropertyOption.ADVANCED)) {
      tableRow("Advanced Property", "Yes");
    }

    endTable();

  }

  private void genManagedObjectList(
    TreeMap<String, AbstractManagedObjectDefinition> list) {

    htmlHeader("Component List");
    for (AbstractManagedObjectDefinition mo : list.values()) {
      link(mo.getUserFriendlyName().toString(), mo.getName() + ".html");
    }
    htmlFooter();
    generateFile("ManagedObjectList.html");
  }

  private void genPropertiesIndex() {

    // Build a sorted list of (property name + its managed object name)
    TreeSet<String> propMoList = new TreeSet<String>();
    for (AbstractManagedObjectDefinition<?, ?> mo : moList.values()) {
      for (PropertyDefinition<?> prop : mo.getPropertyDefinitions()) {
        propMoList.add(
          prop.getName() + "," + prop.getManagedObjectDefinition().getName());
      }
    }

    String lettersPointers = "";
    String firstChar = ".";
    for (String propMoStr : propMoList) {
      String[] propMoArray = propMoStr.split(",");
      String propName = propMoArray[0];
      AbstractManagedObjectDefinition mo = moList.get(propMoArray[1]);
      if (!propName.startsWith(firstChar)) {
        firstChar = propName.substring(0, 1);
        String letter = firstChar.toUpperCase();
        htmlBuff.append(getAnchor(letter) + getHeading2(letter));
        lettersPointers += getLink(letter, "#" + letter) + " ";
      }
      String propLink = getLink(propName,
        mo.getName() + ".html" + "#" + propName);
      String moLink =
        getLink(mo.getUserFriendlyName().toString(), mo.getName() + ".html");
      paragraph(propLink + " (" + moLink + ")");
    }

    String indexBody = htmlBuff.toString();
    htmlBuff = new StringBuffer();
    htmlHeader("Properties Index");
    paragraph(lettersPointers);
    htmlBuff.append(indexBody);
    htmlFooter();
    generateFile("PropertiesIndex.html");
  }

  private String getBaseDN(
    AbstractManagedObjectDefinition mo, LDAPProfile ldapProfile) {

    if (relList.get(mo.getName()) != null) {
      return ldapProfile.getRelationRDNSequence(relList.get(mo.getName()));
    } else if (moList.get(mo.getParent().getName()) != null) {
      // check its superior
      return getBaseDN(moList.get(mo.getParent().getName()), ldapProfile);
    } else {
      System.err.println("Error: Base DN not found for " + mo.getName());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private String getSyntaxStr(PropertyDefinition prop) {
    // Create a visitor for performing syntax specific processing.
    PropertyDefinitionVisitor<String, Void> visitor =
      new PropertyDefinitionVisitor<String, Void>() {

      @Override
      public String visitACI(ACIPropertyDefinition prop, Void p) {
        return getLink("An ACI Syntax", ACI_SYNTAX_PAGE);
      }

      @Override
      public String visitAggregation(
        AggregationPropertyDefinition prop, Void p) {

        RelationDefinition rel = prop.getRelationDefinition();
        String linkStr = getLink(rel.getUserFriendlyName().toString(),
          rel.getName() + ".html");
      return "The DN of any " +  linkStr + ". " +
        ((prop.getSourceConstraintSynopsis() != null) ?
          prop.getSourceConstraintSynopsis().toString() : "");
      }

      @Override
      public String visitAttributeType(
        AttributeTypePropertyDefinition prop, Void p) {
        return "The name of an attribute type defined in the server schema.";
      }

      @Override
      public String visitBoolean(BooleanPropertyDefinition prop, Void p) {
        return "true" + getNewLine() + "false";
      }

      @Override
      public String visitClass(ClassPropertyDefinition prop, Void p) {
        String classStr =
          "A java class that implements or extends the class(es) :";
        for (String clazz : prop.getInstanceOfInterface()) {
          classStr += getNewLine() + clazz;
        }
        return classStr;
      }

      @Override
      public String visitDN(DNPropertyDefinition prop, Void p) {
        String retStr = "A valid DN.";
        if (prop.getBaseDN() != null) {
          retStr += prop.getBaseDN().toString();
        }
        return retStr;
      }

      @Override
      public String visitDuration(DurationPropertyDefinition prop, Void p) {
        String durationStr = "";

        if (prop.isAllowUnlimited()) {
          durationStr += "A value of \"-1\" or \"unlimited\" for no limit. ";
        }
        if (prop.getMaximumUnit() != null) {
          durationStr += "Maximum unit is \"" +
            prop.getMaximumUnit().getLongName() + "\". ";
        }
        long lowerLimitStr = new Double(prop.getBaseUnit().
          fromMilliSeconds(prop.getLowerLimit())).longValue();
        durationStr += "Lower limit is " + lowerLimitStr +
          " " + prop.getBaseUnit().getLongName() + ". ";
        if (prop.getUpperLimit() != null) {
          long upperLimitStr = new Double(prop.getBaseUnit().
            fromMilliSeconds(prop.getUpperLimit())).longValue();
          durationStr += "Upper limit is " + upperLimitStr +
            " " + prop.getBaseUnit().getLongName() + ". ";
        }

        return durationStr;
      }

      @Override
      public String visitEnum(EnumPropertyDefinition prop, Void p) {
        String enumStr = "";
        Class en = prop.getEnumClass();
        for (Object cst : en.getEnumConstants()) {
          enumStr += cst.toString();
          if (prop.getValueSynopsis((Enum) cst) != null) {
            enumStr += " - " + prop.getValueSynopsis((Enum) cst).toString();
          }
          enumStr += getNewLine() + getNewLine();
        }
        return enumStr;
      }

      @Override
      public String visitInteger(IntegerPropertyDefinition prop, Void p) {
        String intStr = "An integer value.";
        intStr += " Lower value is " + prop.getLowerLimit() + ".";
        if (prop.getUpperLimit() != null) {
          intStr += " Upper value is " + prop.getUpperLimit() + " .";
        }
        if (prop.isAllowUnlimited()) {
          intStr += " A value of \"-1\" or \"unlimited\" for no limit.";
        }
        if (prop.getUnitSynopsis() != null) {
          intStr += " Unit is " + prop.getUnitSynopsis() + ".";
        }
        return intStr;
      }

      @Override
      public String visitIPAddress(IPAddressPropertyDefinition prop, Void p) {
        return "An IP address";
      }

      @Override
      public String visitIPAddressMask(
        IPAddressMaskPropertyDefinition prop, Void p) {

        return "An IP address mask";
      }

      @Override
      public String visitSize(SizePropertyDefinition prop, Void p) {
        String sizeStr = "A positive integer representing a size.";
        if (prop.getLowerLimit() != 0) {
          sizeStr += " Lower value is " + prop.getLowerLimit() + ".";
        }
        if (prop.getUpperLimit() != null) {
          sizeStr += " Upper value is " + prop.getUpperLimit() + " .";
        }
        if (prop.isAllowUnlimited()) {
          sizeStr += " A value of \"-1\" or \"unlimited\" for no limit.";
        }
        return sizeStr;
      }

      @Override
      public String visitString(StringPropertyDefinition prop, Void p) {
        String retStr = "A String";
        if (prop.getPatternSynopsis() != null) {
          retStr = prop.getPatternSynopsis().toString();
        }
        return retStr;
      }

      @Override
      public String visitUnknown(PropertyDefinition prop, Void p) {
        return "Unknown";
      }
    };

    // Invoke the visitor against the property definition.
    return (String) prop.accept(visitor, null);

  }

  @SuppressWarnings("unchecked")
  private String getDefaultBehaviorString(PropertyDefinition prop) {
    DefaultBehaviorProvider defaultBehav = prop.getDefaultBehaviorProvider();
    String defValueStr = "";
    if (defaultBehav instanceof UndefinedDefaultBehaviorProvider) {
      defValueStr = "None";
    } else if (defaultBehav instanceof DefinedDefaultBehaviorProvider) {
      DefinedDefaultBehaviorProvider defBehav =
        (DefinedDefaultBehaviorProvider) defaultBehav;
      for (Iterator<String> it = defBehav.getDefaultValues().iterator();
      it.hasNext();) {

        String str = it.next();
        defValueStr += str + (it.hasNext() ? "\n" : "");
      }
    } else if (defaultBehav instanceof AliasDefaultBehaviorProvider) {
      AliasDefaultBehaviorProvider aliasBehav = (
        AliasDefaultBehaviorProvider) defaultBehav;
      defValueStr = aliasBehav.getSynopsis().toString();
    } else if
      (defaultBehav instanceof RelativeInheritedDefaultBehaviorProvider) {
      RelativeInheritedDefaultBehaviorProvider relativBehav =
        (RelativeInheritedDefaultBehaviorProvider) defaultBehav;
      defValueStr = getDefaultBehaviorString(
        relativBehav.getManagedObjectDefinition().
        getPropertyDefinition(relativBehav.getPropertyName()));
    } else if
      (defaultBehav instanceof AbsoluteInheritedDefaultBehaviorProvider) {
      AbsoluteInheritedDefaultBehaviorProvider absoluteBehav =
        (AbsoluteInheritedDefaultBehaviorProvider) defaultBehav;
      defValueStr = getDefaultBehaviorString(
        absoluteBehav.getManagedObjectDefinition().
        getPropertyDefinition(absoluteBehav.getPropertyName()));
    }
    return defValueStr;
  }

  private TreeMap<String, AbstractManagedObjectDefinition> makeMOTreeMap(
    Collection<AbstractManagedObjectDefinition> coll) {

    if (coll == null) {
      return null;
    }
    TreeMap<String, AbstractManagedObjectDefinition> map =
      new TreeMap<String, AbstractManagedObjectDefinition>();
    for (AbstractManagedObjectDefinition mo : coll) {
      map.put(mo.getName(), mo);
    }
    return map;
  }

  private TreeMap<String, RelationDefinition> makeRelTreeMap(
    Collection<RelationDefinition> coll) {

    if (coll == null) {
      return null;
    }
    TreeMap<String, RelationDefinition> map =
      new TreeMap<String, RelationDefinition>();
    for (RelationDefinition rel : coll) {
      map.put(rel.getChildDefinition().getName(), rel);
    }
    return map;
  }

  private TreeMap<String, PropertyDefinition> makePropTreeMap(
    Collection<PropertyDefinition> coll) {

    if (coll == null) {
      return null;
    }
    TreeMap<String, PropertyDefinition> map =
      new TreeMap<String, PropertyDefinition>();
    for (PropertyDefinition prop : coll) {
      map.put(prop.getName(), prop);
    }
    return map;
  }

  private void horizontalLine() {
    htmlBuff.append("<hr style=\"width: 100%; height: 2px;\">");
  }

  private void endTable() {
    htmlBuff.append("</tbody>\n");
    htmlBuff.append("</table>\n");
  }

  private void bullet(String str) {
    htmlBuff.append(
      "<li>" +
      str +
      "</li>\n");
  }

  private void heading2(String string) {
    heading(string, 2);
  }

  private void heading3(String string) {
    heading(string, 3);
  }

  private void heading4(String string) {
    heading(string, 4);
  }

  private void heading(String str, int level) {
    htmlBuff.append(getHeading(str, level));
  }

  private String getHeading(String str, int level) {
    String strLevel = (new Integer(level)).toString();
    return "<h" + strLevel + ">" +
      "<a name=\"" + str + "\"></a>" +
      str +
      "</h" + strLevel + ">\n";
  }

  private String getHeading2(String str) {
    return getHeading(str, 2);
  }

  private String getAnchor(String str) {
    return "<a name=\"" + str + "\"></a>";
  }

  private void htmlHeader(String pageTitle) {
    htmlBuff.append(getHtmlHeader(pageTitle) +
      "<body style=\"color: rgb(0, 0, 0); " +
      "background-color: rgb(255, 255, 255);\">\n");

  }

//  private void htmlHeaderForFrames(String pageTitle) {
//    htmlBuff.append(getHtmlHeader(pageTitle));
//  }
  private String getHtmlHeader(String pageTitle) {
    return ("<html>\n" +
      "<head>\n" +
      "<meta http-equiv=\"content-type\"\n" +
      "content=\"text/html; charset=ISO-8859-1\">\n" +
      "<title>" + pageTitle + "</title>\n" +
      "<link rel=\"stylesheet\" type=\"text/css\"\n" +
      "href=\"" + CSS_FILE + "\">\n" +
      "</head>\n");
  }

  private String getLink(String str, String link) {
    return "<a href=\"" + link + "\">" + str + "</a>";
  }

  private void link(String str, String link) {
    String htmlStr = "";
    if (!inList && getIndentPixels() > 0) {
      htmlStr += "<div style=\"margin-left: " + getIndentPixels() + "px;\">";
    } else if (inList) {
      htmlStr += "<li>";
    }
    htmlStr += getLink(str, link);
    if (!inList && getIndentPixels() > 0) {
      htmlStr += "</div>";
    } else if (inList) {
      htmlStr += "</li>";
    }
    if (!inList) {
      htmlStr += "<br>";
    }
    htmlBuff.append(htmlStr + "\n");
  }

  private void newline() {
    htmlBuff.append(
      getNewLine());
  }

  private String getNewLine() {
    return "<br>\n";
  }

  private void paragraph(Message description) {
    if (description != null) {
      paragraph(description.toString());
    }
  }

  private void paragraph(Message description, TextStyle style) {
    if (description != null) {
      paragraph(description.toString(), style);
    }
  }

  private void paragraph(String description) {
    paragraph(description, TextStyle.STANDARD);
  }

  private void paragraph(String description, TextStyle style) {
    String firstTag;
    if (getIndentPixels() > 0) {
      firstTag = "<p style=\"margin-left: " + getIndentPixels() + "px;\">";
    } else if (style == style.BOLD) {
      firstTag = "<p style=\"font-weight: bold;\">";
    } else {
      firstTag = "<p>";
    }
    htmlBuff.append(
      firstTag +
      description +
      "</p>\n");
  }

  private int getIndentPixels() {
    return (ind * 40);
  }

  private void startTable() {
    htmlBuff.append(
      "<table " +
      "style=\"width: 100%; text-align: left;\"" +
      "border=\"1\"" +
      "cellpadding=\"1\"" +
      "cellspacing=\"0\"" +
      ">\n");

    htmlBuff.append("<tbody>\n");
  }

  private void tableRow(String... strings) {
    htmlBuff.append(
      "<tr>\n");
    for (int ii = 0; ii < strings.length; ii++) {
      String string = strings[ii];
      htmlBuff.append(
        "<td style=\"" +
        "vertical-align: top; " +
        ((ii == 0) ? "width: 20%;" : "") +
        "\">" +
        string +
        "<br></td>");
    }
    htmlBuff.append(
      "</tr>\n");
  }

  /**
   * Text style.
   */
  private enum TextStyle {

    STANDARD, BOLD, ITALIC, UNDERLINE, FIXED_WIDTH
  }

  /**
   * List style.
   */
  private enum ListStyle {

    STANDARD, BULLET, NUMBER
  }

  private void indent() {
    ind++;
  }

  private void outdent() {
    ind--;
  }

  private void beginList() {
    inList = true;
    listLevel++;
    htmlBuff.append(
      "<ul>\n");
  }

  private void endList() {
    listLevel--;
    if (listLevel == 0) {
      inList = false;
    }
    htmlBuff.append(
      "</ul>\n");
  }

  private void htmlFooter() {
    htmlBuff.append(
      "</body>\n" +
      "</html>\n");
  }

  private static void usage() {
    System.err.println(
      "Usage : Provide the argument : output generation directory.");
    System.exit(1);
  }

  private void generateFile(String fileName) {
    // Write the html buffer in a file
    try {
      PrintWriter file = new java.io.PrintWriter(
        new java.io.FileWriter(generationDir + File.separator + fileName));
      file.write(htmlBuff.toString());
      file.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    // re-init html buffer
    htmlBuff = new StringBuffer();
  }

  // Relation List from RootConfiguration
  private TreeMap<String, RelationDefinition> topRelList =
    new TreeMap<String, RelationDefinition>();
  private TreeMap<String, RelationDefinition> relList =
    new TreeMap<String, RelationDefinition>();
  // managed object list
  private TreeMap<String, AbstractManagedObjectDefinition> moList =
    new TreeMap<String, AbstractManagedObjectDefinition>();
  private TreeMap<String, AbstractManagedObjectDefinition> topMoList =
    new TreeMap<String, AbstractManagedObjectDefinition>();
  private int ind = 0;
  private StringBuffer htmlBuff = new StringBuffer();
  private static String generationDir;
  private boolean inList = false;
  private int listLevel = 0;
}
