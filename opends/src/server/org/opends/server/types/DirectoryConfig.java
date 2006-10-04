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
package org.opends.server.types;



import java.util.Map;
import java.util.Set;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.InvokableComponent;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.Debug.*;



/**
 * This interface defines a set of methods that may be used by
 * third-party code to obtatin information about the core Directory
 * Server configuration and the instances of various kinds of
 * components that have registered themselves with the server.
 * <BR><BR>
 * Note that this interface is not intended to be implemented by any
 * third-party code.  It is merely used to control which elements are
 * intended for use by external classes.
 */
public final class DirectoryConfig
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
   private static final String CLASS_NAME =
        "org.opends.server.types.DirectoryConfig";



  /**
   * Retrieves a reference to the Directory Server crypto manager.
   *
   * @return  A reference to the Directory Server crypto manager.
   */
  public static final CryptoManager getCryptoManager()
  {
    assert debugEnter(CLASS_NAME, "getCryptoManager");

    return DirectoryServer.getCryptoManager();
  }



  /**
   * Retrieves the operating system on which the Directory Server is
   * running.
   *
   * @return  The operating system on which the Directory Server is
   *          running.
   */
  public static final OperatingSystem getOperatingSystem()
  {
    assert debugEnter(CLASS_NAME, "getOperatingSystem");

    return DirectoryServer.getOperatingSystem();
  }



  /**
   * Retrieves a reference to the Directory Server configuration
   * handler.
   *
   * @return  A reference to the Directory Server configuration
   *          handler.
   */
  public static final ConfigHandler getConfigHandler()
  {
    assert debugEnter(CLASS_NAME, "getConfigHandler");

    return DirectoryServer.getConfigHandler();
  }



  /**
   * Retrieves the requested entry from the Directory Server
   * configuration.
   *
   * @param  entryDN  The DN of the configuration entry to retrieve.
   *
   * @return  The requested entry from the Directory Server
   *          configuration.
   *
   * @throws  ConfigException  If a problem occurs while trying to
   *                           retrieve the requested entry.
   */
  public static final ConfigEntry getConfigEntry(DN entryDN)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "getConfigEntry",
                      String.valueOf(entryDN));

    return DirectoryServer.getConfigEntry(entryDN);
  }



  /**
   * Retrieves the path to the root directory for this instance of the
   * Directory Server.
   *
   * @return  The path to the root directory for this instance of the
   *          Directory Server.
  */
  public static final String getServerRoot()
  {
    assert debugEnter(CLASS_NAME, "getServerRoot");

    return DirectoryServer.getServerRoot();
  }



  /**
   * Retrieves the time that the Directory Server was started, in
   * milliseconds since the epoch.
   *
   * @return  The time that the Directory Server was started, in
   *          milliseconds since the epoch.
   */
  public static final long getStartTime()
  {
    assert debugEnter(CLASS_NAME, "getStartTime");

    return DirectoryServer.getStartTime();
  }



  /**
   * Retrieves the time that the Directory Server was started,
   * formatted in UTC.
   *
   * @return  The time that the Directory Server was started,
   *          formatted in UTC.
   */
  public static final String getStartTimeUTC()
  {
    assert debugEnter(CLASS_NAME, "getStartTimeUTC");

    return DirectoryServer.getStartTimeUTC();
  }



  /**
   * Retrieves a reference to the Directory Server schema.
   *
   * @return  A reference to the Directory Server schema.
   */
  public static final Schema getSchema()
  {
    assert debugEnter(CLASS_NAME, "getSchema");

    return DirectoryServer.getSchema();
  }



  /**
   * Retrieves the set of matching rules registered with the Directory
   * Server.  The mapping will be between the lowercase name or OID
   * for each matching rule and the matching rule implementation.  The
   * same matching rule instance may be included multiple times with
   * different keys.  The returned map must not be altered by the
   * caller.
   *
   * @return  The set of matching rules registered with the Directory
   *          Server.
   */
  public static Map<String,MatchingRule> getMatchingRules()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRules");

    return DirectoryServer.getMatchingRules();
  }



  /**
   * Retrieves the matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the matching
   *                    rule to retrieve.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no
   *          such matching rule has been defined in the server.
   */
  public static final MatchingRule getMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getMatchingRule",
                      String.valueOf(lowerName));

    return DirectoryServer.getMatchingRule(lowerName);
  }



  /**
   * Retrieves the approximate matching rule with the specified name
   * or OID.
   *
   * @param  lowerName  The lowercase name or OID for the approximate
   *                    matching rule to retrieve.
   *
   * @return  The requested approximate matching rule, or
   *          <CODE>null</CODE> if no such matching rule has been
   *          defined in the server.
   */
  public static final ApproximateMatchingRule
       getApproximateMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule",
                      String.valueOf(lowerName));

    return DirectoryServer.getApproximateMatchingRule(lowerName);
  }



  /**
   * Retrieves the equality matching rule with the specified name or
   * OID.
   *
   * @param  lowerName  The lowercase name or OID for the equality
   *                    matching rule to retrieve.
   *
   * @return  The requested equality matching rule, or
   *          <CODE>null</CODE> if no such matching rule has been
   *          defined in the server.
   */
  public static final EqualityMatchingRule
       getEqualityMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule",
                      String.valueOf(lowerName));

    return DirectoryServer.getEqualityMatchingRule(lowerName);
  }



  /**
   * Retrieves the ordering matching rule with the specified name or
   * OID.
   *
   * @param  lowerName  The lowercase name or OID for the ordering
   *                    matching rule to retrieve.
   *
   * @return  The requested ordering matching rule, or
   *          <CODE>null</CODE> if no such matching rule has been
   *          defined in the server.
   */
  public static final OrderingMatchingRule
       getOrderingMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule",
                      String.valueOf(lowerName));

    return DirectoryServer.getOrderingMatchingRule(lowerName);
  }



  /**
   * Retrieves the substring matching rule with the specified name or
   * OID.
   *
   * @param  lowerName  The lowercase name or OID for the substring
   *                    matching rule to retrieve.
   *
   * @return  The requested substring matching rule, or
   *          <CODE>null</CODE> if no such matching rule has been
   *          defined in the server.
   */
  public static final SubstringMatchingRule
       getSubstringMatchingRule(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule",
                      String.valueOf(lowerName));

    return DirectoryServer.getSubstringMatchingRule(lowerName);
  }



  /**
   * Retrieves the set of objectclasses registered with the Directory
   * Server.  The mapping will be between the lowercase name or OID
   * for each objectclass and the objectclass implementation.  The
   * same objectclass instance may be included multiple times with
   * different keys.  The returned map must not be altered by the
   * caller.
   *
   * @return  The set of objectclasses defined in the Directory
   *          Server.
   */
  public static final Map<String,ObjectClass> getObjectClasses()
  {
    assert debugEnter(CLASS_NAME, "getObjectClasses");

    return DirectoryServer.getObjectClasses();
  }



  /**
   * Retrieves the objectclass for the provided lowercase name or OID.
   * It can optionally return a generated "default" version if the
   * requested objectclass is not defined in the schema.
   *
   * @param  lowerName      The lowercase name or OID for the
   *                        objectclass to retrieve.
   * @param  returnDefault  Indicates whether to generate a default
   *                        version if the requested objectclass is
   *                        not defined in the server schema.
   *
   * @return  The objectclass type, or <CODE>null</CODE> if there is
   *          no objectclass with the specified name or OID defined in
   *          the server schema and a default class should not be
   *          returned.
   */
  public static final ObjectClass
       getObjectClass(String lowerName, boolean returnDefault)
  {
    assert debugEnter(CLASS_NAME, "getObjectClass",
                      String.valueOf(lowerName),
                      String.valueOf(returnDefault));

    return DirectoryServer.getObjectClass(lowerName, returnDefault);
  }



  /**
   * Retrieves the "top" objectClass, which should be the topmost
   * objectclass in the inheritance chain for most other
   * objectclasses.
   *
   * @return  The "top" objectClass.
   */
  public static final ObjectClass getTopObjectClass()
  {
    assert debugEnter(CLASS_NAME, "getTopObjectClass");

    return DirectoryServer.getTopObjectClass();
  }



  /**
   * Retrieves the set of attribute type definitions that have been
   * defined in the Directory Server.  The mapping will be between the
   * lowercase name or OID for each attribute type and the attribute
   * type implementation.  The same attribute type may be included
   * multiple times with different keys.  The returned map must not be
   * altered by the caller.
   *
   * @return The set of attribute type definitions that have been
   *         defined in the Directory Server.
   */
  public static final Map<String,AttributeType> getAttributeTypes()
  {
    assert debugEnter(CLASS_NAME, "getAttributeTypes");

    return DirectoryServer.getAttributeTypes();
  }



  /**
   * Retrieves the attribute type for the provided lowercase name or
   * OID.  It can optionally return a generated "default" version if
   * the requested attribute type is not defined in the schema.
   *
   * @param  lowerName      The lowercase name or OID for the
   *                        attribute type to retrieve.
   * @param  returnDefault  Indicates whether to generate a default
   *                        version if the requested attribute type is
   *                        not defined in the server schema.
   *
   * @return  The requested attribute type, or <CODE>null</CODE> if
   *          there is no attribute with the specified type defined in
   *          the server schema and a default type should not be
   *          returned.
   */
  public static final AttributeType
       getAttributeType(String lowerName, boolean returnDefault)
  {
    assert debugEnter(CLASS_NAME, "getAttributeType",
                      String.valueOf(lowerName),
                      String.valueOf(returnDefault));

    return DirectoryServer.getAttributeType(lowerName, returnDefault);
  }



  /**
   * Retrieves the attribute type for the "objectClass" attribute.
   *
   * @return  The attribute type for the "objectClass" attribute.
   */
  public static final AttributeType getObjectClassAttributeType()
  {
    assert debugEnter(CLASS_NAME, "getObjectClassAttributeType");

    return DirectoryServer.getObjectClassAttributeType();
  }



  /**
   * Retrieves the set of attribute syntaxes defined in the Directory
   * Server.  The mapping will be between the OID and the
   * corresponding syntax implementation.  The returned map must not
   * be altered by the caller.
   *
   * @return  The set of attribute syntaxes defined in the Directory
   *          Server.
   */
  public static final Map<String,AttributeSyntax>
       getAttributeSyntaxes()
  {
    assert debugEnter(CLASS_NAME, "getAttributeSyntaxes");

    return DirectoryServer.getAttributeSyntaxes();
  }



  /**
   * Retrieves the requested attribute syntax.
   *
   * @param  oid           The OID of the syntax to retrieve.
   * @param  allowDefault  Indicates whether to return the default
   *                       attribute syntax if the requested syntax is
   *                       unknown.
   *
   * @return  The requested attribute syntax, the default syntax if
   *          the requested syntax is unknown and the caller has
   *          indicated that the default is acceptable, or
   *          <CODE>null</CODE> otherwise.
   */
  public static final AttributeSyntax
       getAttributeSyntax(String oid, boolean allowDefault)
  {
    assert debugEnter(CLASS_NAME, "getAttributeSyntax",
                      String.valueOf(oid),
                      String.valueOf(allowDefault));

    return DirectoryServer.getAttributeSyntax(oid, allowDefault);
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema.
   */
  public static final AttributeSyntax getDefaultAttributeSyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultAttributeSyntax");

    return DirectoryServer.getDefaultAttributeSyntax();
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema and are
   * meant to store binary values.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema and
   *          are meant to store binary values.
   */
  public static final AttributeSyntax getDefaultBinarySyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultBinarySyntax");

    return DirectoryServer.getDefaultBinarySyntax();
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema and are
   * meant to store Boolean values.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema and
   *          are meant to store Boolean values.
   */
  public static final AttributeSyntax getDefaultBooleanSyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultBooleanSyntax");

    return DirectoryServer.getDefaultBooleanSyntax();
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema and are
   * meant to store DN values.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema and
   *          are meant to store DN values.
   */
  public static final AttributeSyntax getDefaultDNSyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultDNSyntax");

    return DirectoryServer.getDefaultDNSyntax();
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema and are
   * meant to store integer values.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema and
   *          are meant to store integer values.
   */
  public static final AttributeSyntax getDefaultIntegerSyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultIntegerSyntax");

    return DirectoryServer.getDefaultIntegerSyntax();
  }



  /**
   * Retrieves the default attribute syntax that should be used for
   * attributes that are not defined in the server schema and are
   * meant to store string values.
   *
   * @return  The default attribute syntax that should be used for
   *          attributes that are not defined in the server schema and
   *          are meant to store string values.
   */
  public static final AttributeSyntax getDefaultStringSyntax()
  {
    assert debugEnter(CLASS_NAME, "getDefaultStringSyntax");

    return DirectoryServer.getDefaultStringSyntax();
  }



  /**
   * Retrieves the set of matching rule uses defined in the Directory
   * Server.  The mapping will be between the matching rule and its
   * corresponding matching rule use.  The returned map must not be
   * altered by the caller.
   *
   * @return  The set of matching rule uses defined in the Directory
   *          Server.
   */
  public static final Map<MatchingRule,MatchingRuleUse>
       getMatchingRuleUses()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleUses");

    return DirectoryServer.getMatchingRuleUses();
  }



  /**
   * Retrieves the matching rule use associated with the provided
   * matching rule.
   *
   * @param  matchingRule  The matching rule for which to retrieve the
   *                       matching rule use.
   *
   * @return  The matching rule use for the provided matching rule, or
   *          <CODE>null</CODE> if none is defined.
   */
  public static final MatchingRuleUse
       getMatchingRuleUse(MatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "getMatchingRuleUse",
                      String.valueOf(matchingRule));

    return DirectoryServer.getMatchingRuleUse(matchingRule);
  }



  /**
   * Retrieves the set of DIT content rules defined in the Directory
   * Server.  The mapping will be between the structural objectclass
   * and its corresponding DIT content rule.  The returned map must
   * not be altered by the caller.
   *
   * @return  The set of DIT content rules defined in the Directory
   *          Server.
   */
  public static final Map<ObjectClass,DITContentRule>
       getDITContentRules()
  {
    assert debugEnter(CLASS_NAME, "getDITContentRules");

    return DirectoryServer.getDITContentRules();
  }



  /**
   * Retrieves the DIT content rule associated with the specified
   * objectclass.
   *
   * @param  objectClass  The objectclass for which to retrieve the
   *                      associated DIT content rule.
   *
   * @return  The requested DIT content rule, or <CODE>null</CODE> if
   *          no such rule is defined in the schema.
   */
  public static final DITContentRule
       getDITContentRule(ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "getDITContentRule",
                      String.valueOf(objectClass));

    return DirectoryServer.getDITContentRule(objectClass);
  }



  /**
   * Retrieves the set of DIT structure rules defined in the Directory
   * Server.  The mapping will be between the name form and its
   * corresponding DIT structure rule.  The returned map must not be
   * altered by the caller.
   *
   * @return  The set of DIT structure rules defined in the Directory
   *          Server.
   */
  public static final Map<NameForm,DITStructureRule>
       getDITStructureRules()
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRules");

    return DirectoryServer.getDITStructureRules();
  }



  /**
   * Retrieves the DIT structure rule associated with the provided
   * rule ID.
   *
   * @param  ruleID  The rule ID for which to retrieve the associated
   *                 DIT structure rule.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE>
   *          if no such rule is defined.
   */
  public static final DITStructureRule getDITStructureRule(int ruleID)
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(ruleID));

    return DirectoryServer.getDITStructureRule(ruleID);
  }



  /**
   * Retrieves the DIT structure rule associated with the provided
   * name form.
   *
   * @param  nameForm  The name form for which to retrieve the
   *                   associated DIT structure rule.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE>
   *          if no such rule is defined.
   */
  public static final DITStructureRule
       getDITStructureRule(NameForm nameForm)
  {
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(nameForm));

    return DirectoryServer.getDITStructureRule(nameForm);
  }



  /**
   * Retrieves the set of name forms defined in the Directory Server.
   * The mapping will be between the structural objectclass and its
   * corresponding name form.  The returned map must not be altered by
   * the caller.
   *
   * @return  The set of name forms defined in the Directory Server.
   */
  public static final Map<ObjectClass,NameForm> getNameForms()
  {
    assert debugEnter(CLASS_NAME, "getNameForms");

    return DirectoryServer.getNameForms();
  }



  /**
   * Retrieves the name form associated with the specified structural
   * objectclass.
   *
   * @param  objectClass  The structural objectclass for which to
   *                      retrieve the  associated name form.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no such
   *          name form is defined in the schema.
   */
  public static final NameForm getNameForm(ObjectClass objectClass)
  {
    assert debugEnter(CLASS_NAME, "getNameForm",
                      String.valueOf(objectClass));

    return DirectoryServer.getNameForm(objectClass);
  }



  /**
   * Retrieves the name form associated with the specified name or
   * OID.
   *
   * @param  lowerName  The name or OID of the name form to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no such
   *          name form is defined in the schema.
   */
  public static final NameForm getNameForm(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getNameForm",
                      String.valueOf(lowerName));

    return DirectoryServer.getNameForm(lowerName);
  }



  /**
   * Registers the provided configurable component with the Directory
   * Server.
   *
   * @param  component  The configurable component to register.
   */
  public static final void registerConfigurableComponent(
                                ConfigurableComponent component)
  {
    assert debugEnter(CLASS_NAME, "registerConfigurableComponent",
                      String.valueOf(component));

    DirectoryServer.registerConfigurableComponent(component);
  }



  /**
   * Deregisters the provided configurable component with the
   * Directory Server.
   *
   * @param  component  The configurable component to deregister.
   */
  public static final void deregisterConfigurableComponent(
                                ConfigurableComponent component)
  {
    assert debugEnter(CLASS_NAME, "deregisterConfigurableComponent",
                      String.valueOf(component));

    DirectoryServer.deregisterConfigurableComponent(component);
  }



  /**
   * Registers the provided invokable component with the Directory
   * Server.
   *
   * @param  component  The invokable component to register.
   */
  public static final void registerInvokableComponent(
                                InvokableComponent component)
  {
    assert debugEnter(CLASS_NAME, "registerInvokableComponent",
                      String.valueOf(component));

    DirectoryServer.registerInvokableComponent(component);
  }



  /**
   * Deregisters the provided invokable component with the Directory
   * Server.
   *
   * @param  component  The invokable component to deregister.
   */
  public static final void deregisterInvokableComponent(
                                InvokableComponent component)
  {
    assert debugEnter(CLASS_NAME, "deregisterInvokableComponent",
                      String.valueOf(component));

    DirectoryServer.deregisterInvokableComponent(component);
  }



  /**
   * Registers the provided alert generator with the Directory Server.
   *
   * @param  alertGenerator  The alert generator to register.
   */
  public static final void registerAlertGenerator(
                                AlertGenerator alertGenerator)
  {
    assert debugEnter(CLASS_NAME, "registerAlertGenerator");

    DirectoryServer.registerAlertGenerator(alertGenerator);
  }



  /**
   * Deregisters the provided alert generator with the Directory
   * Server.
   *
   * @param  alertGenerator  The alert generator to deregister.
   */
  public static final void deregisterAlertGenerator(
                                AlertGenerator alertGenerator)
  {
    assert debugEnter(CLASS_NAME, "deregisterAlertGenerator");

    DirectoryServer.deregisterAlertGenerator(alertGenerator);
  }



  /**
   * Sends an alert notification with the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertID       The alert ID that uniquely identifies the
   *                       type of alert.
   * @param  alertMessage  A message (possibly <CODE>null</CODE>) that
   *                       can provide more information about this
   *                       alert.
   */
  public static final void
       sendAlertNotification(AlertGenerator generator,
                             String alertType, int alertID,
                             String alertMessage)
  {
    assert debugEnter(CLASS_NAME, "sendAlertNotification",
                      String.valueOf(generator),
                      String.valueOf(alertType),
                      String.valueOf(alertID),
                      String.valueOf(alertMessage));

    DirectoryServer.sendAlertNotification(generator, alertType,
                                          alertID, alertMessage);
  }



  /**
   * Retrieves the result code that should be used when the Directory
   * Server encounters an internal server error.
   *
   * @return  The result code that should be used when the Directory
   *          Server encounters an internal server error.
   */
  public static final ResultCode getServerErrorResultCode()
  {
    assert debugEnter(CLASS_NAME, "getServerErrorResultCode");

    return DirectoryServer.getServerErrorResultCode();
  }



  /**
   * Retrieves the entry with the requested DN.  It will first
   * determine which backend should be used for this DN and will then
   * use that backend to retrieve the entry.  The caller must already
   * hold the appropriate lock on the specified entry.
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if it does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to retrieve the entry.
   */
  public static final Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getEntry",
                      String.valueOf(entryDN));

    return DirectoryServer.getEntry(entryDN);
  }



  /**
   * Indicates whether the specified entry exists in the Directory
   * Server.  The caller is not required to hold any locks when
   * invoking this method.
   *
   * @param  entryDN  The DN of the entry for which to make the
   *                  determination.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in one
   *          of the backends, or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to make the determination.
   */
  public static final boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "entryExists",
                      String.valueOf(entryDN));

    return DirectoryServer.entryExists(entryDN);
  }



  /**
   * Retrieves the set of OIDs for the supported controls registered
   * with the Directory Server.
   *
   * @return  The set of OIDS for the supported controls registered
   *          with the Directory Server.
   */
  public static final Set<String> getSupportedControls()
  {
    assert debugEnter(CLASS_NAME, "getSupportedControls");

    return DirectoryServer.getSupportedControls();
  }



  /**
   * Indicates whether the specified OID is registered with the
   * Directory Server as a supported control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered
   *          with the server as a supported control, or
   *          <CODE>false</CODE> if not.
   */
  public static final boolean isSupportedControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "isSupportedControl",
                      String.valueOf(controlOID));

    return DirectoryServer.isSupportedControl(controlOID);
  }



  /**
   * Registers the provided OID as a supported control for the
   * Directory Server.  This will have no effect if the specified
   * control OID is already present in the list of supported controls.
   *
   * @param  controlOID  The OID of the control to register as a
   *                     supported control.
   */
  public static final void registerSupportedControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "registerSupportedControl",
                      String.valueOf(controlOID));

    DirectoryServer.registerSupportedControl(controlOID);
  }



  /**
   * Deregisters the provided OID as a supported control for the
   * Directory Server.  This will have no effect if the specified
   * control OID is not present in the list of supported controls.
   *
   * @param  controlOID  The OID of the control to deregister as a
   *                     supported control.
   */
  public static final void
       deregisterSupportedControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "deregisterSupportedControl",
                      String.valueOf(controlOID));

    DirectoryServer.deregisterSupportedControl(controlOID);
  }



  /**
   * Retrieves the set of OIDs for the supported features registered
   * with the Directory Server.
   *
   * @return  The set of OIDs for the supported features registered
   *          with the Directory Server.
   */
  public static final Set<String> getSupportedFeatures()
  {
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

    return DirectoryServer.getSupportedFeatures();
  }



  /**
   * Indicates whether the specified OID is registered with the
   * Directory Server as a supported feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered
   *          with the server as a supported feature, or
   *          <CODE>false</CODE> if not.
   */
  public static final boolean isSupportedFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "isSupportedFeature",
                      String.valueOf(featureOID));

    return DirectoryServer.isSupportedFeature(featureOID);
  }



  /**
   * Registers the provided OID as a supported feature for the
   * Directory Server.  This will have no effect if the specified
   * feature OID is already present in the list of supported features.
   *
   * @param  featureOID  The OID of the feature to register as a
   *                     supported feature.
   */
  public static final void registerSupportedFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "registerSupportedFeature",
                      String.valueOf(featureOID));

    DirectoryServer.registerSupportedFeature(featureOID);
  }



  /**
   * Deregisters the provided OID as a supported feature for the
   * Directory Server.  This will have no effect if the specified
   * feature OID is not present in the list of supported features.
   *
   * @param  featureOID  The OID of the feature to deregister as a
   *                     supported feature.
   */
  public static final void
       deregisterSupportedFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "deregisterSupportedFeature",
                      String.valueOf(featureOID));

    DirectoryServer.deregisterSupportedFeature(featureOID);
  }



  /**
   * Retrieves the set of extended operations that may be processed by
   * the Directory Server.  The mapping will be between the OID and
   * the extended operation handler providing the logic for the
   * extended operation with that OID.  The returned map must not be
   * altered by the caller.
   *
   * @return  The set of extended operations that may be processed by
   *          the Directory Server.
   */
  public static final Map<String,ExtendedOperationHandler>
                     getSupportedExtensions()
  {
    assert debugEnter(CLASS_NAME, "getSupportedExtensions");

    return DirectoryServer.getSupportedExtensions();
  }



  /**
   * Retrieves the handler for the extended operation for the provided
   * extended operation OID.
   *
   * @param  oid  The OID of the extended operation to retrieve.
   *
   * @return  The handler for the specified extended operation, or
   *          <CODE>null</CODE> if there is none.
   */
  public static final ExtendedOperationHandler
       getExtendedOperationHandler(String oid)
  {
    assert debugEnter(CLASS_NAME, "getExtendedOperationHandler",
                      String.valueOf(oid));

    return DirectoryServer.getExtendedOperationHandler(oid);
  }



  /**
   * Registers the provided extended operation handler with the
   * Directory Server.
   *
   * @param  oid      The OID for the extended operation to register.
   * @param  handler  The extended operation handler to register with
   *                  the Directory Server.
   */
  public static final void registerSupportedExtension(String oid,
                          ExtendedOperationHandler handler)
  {
    assert debugEnter(CLASS_NAME, "registerSupportedExtension",
                      String.valueOf(oid), String.valueOf(handler));

    DirectoryServer.registerSupportedExtension(oid, handler);
  }



  /**
   * Deregisters the provided extended operation handler with the
   * Directory Server.
   *
   * @param  oid  The OID for the extended operation to deregister.
   */
  public static final void deregisterSupportedExtension(String oid)
  {
    assert debugEnter(CLASS_NAME, "deregisterSupportedExtension",
                      String.valueOf(oid));

    DirectoryServer.deregisterSupportedExtension(oid);
  }



  /**
   * Retrieves the set of SASL mechanisms that are supported by the
   * Directory Server.  The mapping will be between the mechanism name
   * and the SASL mechanism handler that implements support for that
   * mechanism.  The returned map must not be altered by the caller.
   *
   * @return  The set of SASL mechanisms that are supported by the
   *          Directory Server.
   */
  public static final Map<String,SASLMechanismHandler>
                     getSupportedSASLMechanisms()
  {
    assert debugEnter(CLASS_NAME, "getSupportedSASLMechanisms");

    return DirectoryServer.getSupportedSASLMechanisms();
  }



  /**
   * Retrieves the handler for the specified SASL mechanism.
   *
   * @param  name  The name of the SASL mechanism to retrieve.
   *
   * @return  The handler for the specified SASL mechanism, or
   *          <CODE>null</CODE> if there is none.
   */
  public static final SASLMechanismHandler
       getSASLMechanismHandler(String name)
  {
    assert debugEnter(CLASS_NAME, "getSASLMechanismHandler",
                      String.valueOf(name));

    return DirectoryServer.getSASLMechanismHandler(name);
  }



  /**
   * Registers the provided SASL mechanism handler with the Directory
   * Server.
   *
   * @param  name     The name of the SASL mechanism to be registered.
   * @param  handler  The SASL mechanism handler to register with the
   *                  Directory Server.
   */
  public static final void
       registerSASLMechanismHandler(String name,
                                    SASLMechanismHandler handler)
  {
    assert debugEnter(CLASS_NAME, "registerSASLMechanismHandler",
                      String.valueOf(name), String.valueOf(handler));

    DirectoryServer.registerSASLMechanismHandler(name, handler);
  }



  /**
   * Deregisters the provided SASL mechanism handler with the
   * Directory Server.
   *
   * @param  name  The name of the SASL mechanism to be deregistered.
   */
  public static final void deregisterSASLMechanismHandler(String name)
  {
    assert debugEnter(CLASS_NAME, "deregisterSASLMechanismHandler",
                      String.valueOf(name));

    DirectoryServer.deregisterSASLMechanismHandler(name);
  }



  /**
   * Registers the provided change notification listener with the
   * Directory Server so that it will be notified of any add, delete,
   * modify, or modify DN operations that are performed.
   *
   * @param  changeListener  The change notification listener to
   *                         register with the Directory Server.
   */
  public static final void
       registerChangeNotificationListener(
            ChangeNotificationListener changeListener)
  {
    assert debugEnter(CLASS_NAME,
                      "registerChangeNotificationListener",
                      String.valueOf(changeListener));

    DirectoryServer.registerChangeNotificationListener(
                         changeListener);
  }



  /**
   * Deregisters the provided change notification listener with the
   * Directory Server so that it will no longer be notified of any
   * add, delete, modify, or modify DN operations that are performed.
   *
   * @param  changeListener  The change notification listener to
   *                         deregister with the Directory Server.
   */
  public static final void deregisterChangeNotificationListener(
                          ChangeNotificationListener changeListener)
  {
    assert debugEnter(CLASS_NAME,
                      "deregisterChangeNotificationListener",
                      String.valueOf(changeListener));

    DirectoryServer.deregisterChangeNotificationListener(
                         changeListener);
  }



  /**
   * Registers the provided shutdown listener with the Directory
   * Server so that it will be notified when the server shuts down.
   *
   * @param  listener  The shutdown listener to register with the
   *                   Directory Server.
   */
  public static final void
       registerShutdownListener(ServerShutdownListener listener)
  {
    assert debugEnter(CLASS_NAME, "registerShutdownListener",
                      String.valueOf(listener));

    DirectoryServer.registerShutdownListener(listener);
  }



  /**
   * Deregisters the provided shutdown listener with the Directory
   * Server.
   *
   * @param  listener  The shutdown listener to deregister with the
   *                   Directory Server.
   */
  public static final void
       deregisterShutdownListener(ServerShutdownListener listener)
  {
    assert debugEnter(CLASS_NAME, "deregisterShutdownListener",
                      String.valueOf(listener));

    DirectoryServer.deregisterShutdownListener(listener);
  }



  /**
   * Retrieves the full version string for the Directory Server.
   *
   * @return  The full version string for the Directory Server.
   */
  public static final String getVersionString()
  {
    return DirectoryServer.getVersionString();
  }
}

