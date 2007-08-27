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
package org.opends.server.backends;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.RootDSEBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.*;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.Validator;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.
     ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;


/**
 * This class defines a backend to hold the Directory Server root DSE.  It is a
 * kind of meta-backend in that it will dynamically generate the root DSE entry
 * (although there will be some caching) for base-level searches, and will
 * simply redirect to other backends for operations in other scopes.
 * <BR><BR>
 * This should not be treated like a regular backend when it comes to
 * initializing the server configuration.  It should only be initialized after
 * all other backends are configured.  As such, it should have a special entry
 * in the configuration rather than being placed under the cn=Backends branch
 * with the other backends.
 */
public class RootDSEBackend
       extends Backend
       implements ConfigurationChangeListener<RootDSEBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of standard "static" attributes that we will always include in the
  // root DSE entry and won't change while the server is running.
  private ArrayList<Attribute> staticDSEAttributes;

  // The set of user-defined attributes that will be included in the root DSE
  // entry.
  private ArrayList<Attribute> userDefinedAttributes;

  // Indicates whether the attributes of the root DSE should always be treated
  // as user attributes even if they are defined as operational in the schema.
  private boolean showAllAttributes;

  // The set of subordinate base DNs and their associated backends that will be
  // used for non-base searches.
  private ConcurrentHashMap<DN,Backend> subordinateBaseDNs;

  // The set of objectclasses that will be used in the root DSE entry.
  private HashMap<ObjectClass,String> dseObjectClasses;

  // The current configuration state.
  private RootDSEBackendCfg currentConfig;

  // The DN of the configuration entry for this backend.
  private DN configEntryDN;

  // The DN for the root DSE.
  private DN rootDSEDN;

  // The set of base DNs for this backend.
  private DN[] baseDNs;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public RootDSEBackend()
  {
    super();

    // Perform all initialization in initializeBackend.
  }


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    Validator.ensureNotNull(config);
    Validator.ensureTrue(config instanceof RootDSEBackendCfg);
    currentConfig = (RootDSEBackendCfg)config;
    configEntryDN = config.dn();
  }

  /**
   * {@inheritDoc}
   */
  public void initializeBackend()
         throws ConfigException, InitializationException
  {
    ConfigEntry configEntry =
         DirectoryServer.getConfigEntry(configEntryDN);

    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      Message message = ERR_ROOTDSE_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    // Get the set of user-defined attributes for the configuration entry.  Any
    // attributes that we don't recognize will be included directly in the root
    // DSE.
    userDefinedAttributes = new ArrayList<Attribute>();
    for (List<Attribute> attrs :
         configEntry.getEntry().getUserAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (! isDSEConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }
    for (List<Attribute> attrs :
         configEntry.getEntry().getOperationalAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (! isDSEConfigAttribute(a))
        {
          userDefinedAttributes.add(a);
        }
      }
    }


    // Create the set of base DNs that we will handle.  In this case, it's just
    // the root DSE.
    rootDSEDN    = DN.nullDN();
    this.baseDNs = new DN[] { rootDSEDN };


    // Create the set of subordinate base DNs.  If this is specified in the
    // configuration, then use that set.  Otherwise, use the set of non-private
    // backends defined in the server.
    try
    {
      Set<DN> subDNs = currentConfig.getSubordinateBaseDN();
      if (subDNs.isEmpty())
      {
        // This is fine -- we'll just use the set of user-defined suffixes.
        subordinateBaseDNs = null;
      }
      else
      {
        subordinateBaseDNs = new ConcurrentHashMap<DN,Backend>();
        for (DN baseDN : subDNs)
        {
          Backend backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            Message message = WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(
                String.valueOf(baseDN));
            logError(message);
          }
          else
          {
            subordinateBaseDNs.put(baseDN, backend);
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
          stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }


    // Determine whether all root DSE attributes should be treated as user
    // attributes.
    showAllAttributes = currentConfig.isShowAllAttributes();


    // Construct the set of "static" attributes that will always be present in
    // the root DSE.
    staticDSEAttributes = new ArrayList<Attribute>();

    staticDSEAttributes.add(createAttribute(ATTR_VENDOR_NAME,
                                            ATTR_VENDOR_NAME_LC,
                                            SERVER_VENDOR_NAME));

    staticDSEAttributes.add(createAttribute(ATTR_VENDOR_VERSION,
                                 ATTR_VENDOR_VERSION_LC,
                                 DirectoryServer.getVersionString()));



    // Construct the set of objectclasses to include in the root DSE entry.
    dseObjectClasses = new HashMap<ObjectClass,String>(2);
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP);
    if (topOC == null)
    {
      topOC = DirectoryServer.getDefaultObjectClass(OC_TOP);
    }
    dseObjectClasses.put(topOC, OC_TOP);

    ObjectClass rootDSEOC =
         DirectoryServer.getObjectClass(OC_ROOT_DSE);
    if (rootDSEOC == null)
    {
      rootDSEOC = DirectoryServer.getDefaultObjectClass(OC_ROOT_DSE);
    }
    dseObjectClasses.put(rootDSEOC, OC_ROOT_DSE);


    // Define an empty sets for the supported controls and features.
    supportedControls = new HashSet<String>(0);
    supportedFeatures = new HashSet<String>(0);


    // Set the backend ID for this backend.  We don't have to worry about
    // potential conflicts because this backend will never get registered with
    // the Directory Server like other backends.
    setBackendID("rootdse");


    // Register as a change listener.
    currentConfig.addChangeListener(this);
  }



  /**
   * Performs any necessary work to finalize this backend, including closing any
   * underlying databases or connections and deregistering any suffixes that it
   * manages with the Directory Server.  This may be called during the
   * Directory Server shutdown process or if a backend is disabled with the
   * server online.  It must not return until the backend is closed.
   * <BR><BR>
   * This method may not throw any exceptions.  If any problems are encountered,
   * then they may be logged but the closure should progress as completely as
   * possible.
   */
  public void finalizeBackend()
  {
    currentConfig.removeChangeListener(this);
  }



  /**
   * Indicates whether the provided attribute is one that is used in the
   * configuration of this backend.
   *
   * @param  attribute  The attribute for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute is one that is used in
   *          the configuration of this backend, <CODE>false</CODE> if not.
   */
  private boolean isDSEConfigAttribute(Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeType();
    if (attrType.hasName(ATTR_ROOT_DSE_SUBORDINATE_BASE_DN.toLowerCase()) ||
        attrType.hasName(ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES.toLowerCase()) ||
        attrType.hasName(ATTR_COMMON_NAME))
    {
      return true;
    }

    return false;
  }



  /**
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return  The set of base-level DNs that may be used within this backend.
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized long getEntryCount()
  {
    // There is always just a single entry in this backend.
    return 1;
  }



  /**
   * Indicates whether the data associated with this backend may be considered
   * local (i.e., in a repository managed by the Directory Server) rather than
   * remote (i.e., in an external repository accessed by the Directory Server
   * but managed through some other means).
   *
   * @return  <CODE>true</CODE> if the data associated with this backend may be
   *          considered local, or <CODE>false</CODE> if it is remote.
   */
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    long ret = numSubordinates(entryDN);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if(ret == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }

  /**
   * {@inheritDoc}
   */
  public long numSubordinates(DN entryDN) throws DirectoryException
  {
    if (entryDN == null || ! entryDN.isNullDN())
    {
      return -1;
    }

    long count = 0;

    Map<DN,Backend> baseMap;
    if (subordinateBaseDNs == null)
    {
      baseMap = DirectoryServer.getPublicNamingContexts();
    }
    else
    {
      baseMap = subordinateBaseDNs;
    }

    for (DN subBase : baseMap.keySet())
    {

      Backend b = baseMap.get(subBase);
      Entry subBaseEntry = b.getEntry(subBase);
      if (subBaseEntry != null)
      {
        count++;
      }
    }

    return count;
  }

  /**
   * Retrieves the requested entry from this backend.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if the entry does not
   *          exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to retrieve
   *                              the entry.
   */
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was the root DSE, then create and return it.
    if ((entryDN == null) || entryDN.isNullDN())
    {
      return getRootDSE();
    }


    // This method should never be used to get anything other than the root DSE.
    // If we got here, then that appears to be the case, so log a message.
    Message message =
        WARN_ROOTDSE_GET_ENTRY_NONROOT.get(String.valueOf(entryDN));
    logError(message);


    // Go ahead and check the subordinate backends to see if we can find the
    // entry there.  Note that in order to avoid potential loop conditions, this
    // will only work if the set of subordinate bases has been explicitly
    // specified.
    if (subordinateBaseDNs != null)
    {
      for (Backend b : subordinateBaseDNs.values())
      {
        if (b.handlesEntry(entryDN))
        {
          return b.getEntry(entryDN);
        }
      }
    }


    // If we've gotten here, then we couldn't find the entry so return null.
    return null;
  }



  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @return  The root DSE entry for the Directory Server.
   */
  public Entry getRootDSE()
  {
    HashMap<AttributeType,List<Attribute>> dseUserAttrs =
         new HashMap<AttributeType,List<Attribute>>();

    HashMap<AttributeType,List<Attribute>> dseOperationalAttrs =
         new HashMap<AttributeType,List<Attribute>>();


    // Add the "namingContexts" attribute.
    Attribute publicNamingContextAttr =
         createDNAttribute(ATTR_NAMING_CONTEXTS, ATTR_NAMING_CONTEXTS_LC,
                           DirectoryServer.getPublicNamingContexts().keySet());
    ArrayList<Attribute> publicNamingContextAttrs = new ArrayList<Attribute>(1);
    publicNamingContextAttrs.add(publicNamingContextAttr);
    if (showAllAttributes ||
        (! publicNamingContextAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(publicNamingContextAttr.getAttributeType(),
                       publicNamingContextAttrs);
    }
    else
    {
      dseOperationalAttrs.put(publicNamingContextAttr.getAttributeType(),
                              publicNamingContextAttrs);
    }


    // Add the "ds-private-naming-contexts" attribute.
    Attribute privateNamingContextAttr =
         createDNAttribute(ATTR_PRIVATE_NAMING_CONTEXTS,
                           ATTR_PRIVATE_NAMING_CONTEXTS,
                           DirectoryServer.getPrivateNamingContexts().keySet());
    ArrayList<Attribute> privateNamingContextAttrs =
         new ArrayList<Attribute>(1);
    privateNamingContextAttrs.add(privateNamingContextAttr);
    if (showAllAttributes ||
        (! privateNamingContextAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(privateNamingContextAttr.getAttributeType(),
                       privateNamingContextAttrs);
    }
    else
    {
      dseOperationalAttrs.put(privateNamingContextAttr.getAttributeType(),
                              privateNamingContextAttrs);
    }


    // Add the "supportedControl" attribute.
    Attribute supportedControlAttr =
         createAttribute(ATTR_SUPPORTED_CONTROL, ATTR_SUPPORTED_CONTROL_LC,
                         DirectoryServer.getSupportedControls());
    ArrayList<Attribute> supportedControlAttrs = new ArrayList<Attribute>(1);
    supportedControlAttrs.add(supportedControlAttr);
    if (showAllAttributes ||
        (! supportedControlAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(supportedControlAttr.getAttributeType(),
                       supportedControlAttrs);
    }
    else
    {
      dseOperationalAttrs.put(supportedControlAttr.getAttributeType(),
                              supportedControlAttrs);
    }


    // Add the "supportedExtension" attribute.
    Attribute supportedExtensionAttr =
         createAttribute(ATTR_SUPPORTED_EXTENSION, ATTR_SUPPORTED_EXTENSION_LC,
                         DirectoryServer.getSupportedExtensions().keySet());
    ArrayList<Attribute> supportedExtensionAttrs = new ArrayList<Attribute>(1);
    supportedExtensionAttrs.add(supportedExtensionAttr);
    if (showAllAttributes ||
        (! supportedExtensionAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(supportedExtensionAttr.getAttributeType(),
                       supportedExtensionAttrs);
    }
    else
    {
      dseOperationalAttrs.put(supportedExtensionAttr.getAttributeType(),
                              supportedExtensionAttrs);
    }


    // Add the "supportedFeature" attribute.
    Attribute supportedFeatureAttr =
         createAttribute(ATTR_SUPPORTED_FEATURE, ATTR_SUPPORTED_FEATURE_LC,
                         DirectoryServer.getSupportedFeatures());
    ArrayList<Attribute> supportedFeatureAttrs = new ArrayList<Attribute>(1);
    supportedFeatureAttrs.add(supportedFeatureAttr);
    if (showAllAttributes ||
        (! supportedFeatureAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(supportedFeatureAttr.getAttributeType(),
                       supportedFeatureAttrs);
    }
    else
    {
      dseOperationalAttrs.put(supportedFeatureAttr.getAttributeType(),
                              supportedFeatureAttrs);
    }


    // Add the "supportedSASLMechanisms" attribute.
    Attribute supportedSASLMechAttr =
         createAttribute(ATTR_SUPPORTED_SASL_MECHANISMS,
                         ATTR_SUPPORTED_SASL_MECHANISMS_LC,
                         DirectoryServer.getSupportedSASLMechanisms().keySet());
    ArrayList<Attribute> supportedSASLMechAttrs = new ArrayList<Attribute>(1);
    supportedSASLMechAttrs.add(supportedSASLMechAttr);
    if (showAllAttributes ||
        (! supportedSASLMechAttr.getAttributeType().isOperational()))
    {
      dseUserAttrs.put(supportedSASLMechAttr.getAttributeType(),
                       supportedSASLMechAttrs);
    }
    else
    {
      dseOperationalAttrs.put(supportedSASLMechAttr.getAttributeType(),
                              supportedSASLMechAttrs);
    }


    // Add the "supportedAuthPasswordSchemes" attribute.
    Set<String> authPWSchemes =
         DirectoryServer.getAuthPasswordStorageSchemes().keySet();
    if (! authPWSchemes.isEmpty())
    {
      Attribute supportedAuthPWSchemesAttr =
           createAttribute(ATTR_SUPPORTED_AUTH_PW_SCHEMES,
                           ATTR_SUPPORTED_AUTH_PW_SCHEMES_LC, authPWSchemes);
      ArrayList<Attribute> supportedAuthPWSchemesAttrs =
           new ArrayList<Attribute>(1);
      supportedAuthPWSchemesAttrs.add(supportedAuthPWSchemesAttr);
      if (showAllAttributes ||
          (! supportedSASLMechAttr.getAttributeType().isOperational()))
      {
        dseUserAttrs.put(supportedAuthPWSchemesAttr.getAttributeType(),
                         supportedAuthPWSchemesAttrs);
      }
      else
      {
        dseOperationalAttrs.put(supportedAuthPWSchemesAttr.getAttributeType(),
                                supportedAuthPWSchemesAttrs);
      }
    }


    // Add all the standard "static" attributes.
    for (Attribute a : staticDSEAttributes)
    {
      AttributeType type = a.getAttributeType();

      if (type.isOperational() && (! showAllAttributes))
      {
        List<Attribute> attrs = dseOperationalAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          dseOperationalAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
      else
      {
        List<Attribute> attrs = dseUserAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          dseUserAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
    }


    // Add all the user-defined attributes.
    for (Attribute a : userDefinedAttributes)
    {
      AttributeType type = a.getAttributeType();

      if (type.isOperational() && (! showAllAttributes))
      {
        List<Attribute> attrs = dseOperationalAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          dseOperationalAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
      else
      {
        List<Attribute> attrs = dseUserAttrs.get(type);
        if (attrs == null)
        {
          attrs = new ArrayList<Attribute>();
          attrs.add(a);
          dseUserAttrs.put(type, attrs);
        }
        else
        {
          attrs.add(a);
        }
      }
    }


    // Construct and return the entry.
    Entry e = new Entry(rootDSEDN, dseObjectClasses, dseUserAttrs,
                        dseOperationalAttrs);
    e.processVirtualAttributes();
    return e;
  }



  /**
   * Creates an attribute for the root DSE with the following criteria.
   *
   * @param  name       The name for the attribute.
   * @param  lowerName  The name for the attribute formatted in all lowercase
   *                    characters.
   * @param  value      The value to use for the attribute.
   *
   * @return  The constructed attribute.
   */
  private Attribute createAttribute(String name, String lowerName,
                                    String value)
  {
    AttributeType type = DirectoryServer.getAttributeType(lowerName);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(name);
    }

    LinkedHashSet<AttributeValue> attrValues =
         new LinkedHashSet<AttributeValue>(1);
    attrValues.add(new AttributeValue(type, new ASN1OctetString(value)));

    return new Attribute(type, name, attrValues);
  }



  /**
   * Creates an attribute for the root DSE meant to hold a set of DNs.
   *
   * @param  name       The name for the attribute.
   * @param  lowerName  The name for the attribute formatted in all lowercase
   *                    characters.
   * @param  values     The set of DN values to use for the attribute.
   *
   * @return  The constructed attribute.
   */
  private Attribute createDNAttribute(String name, String lowerName,
                                      Collection<DN> values)
  {
    AttributeType type = DirectoryServer.getAttributeType(lowerName);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(name);
    }

    LinkedHashSet<AttributeValue> attrValues =
         new LinkedHashSet<AttributeValue>();
    for (DN dn : values)
    {
      attrValues.add(new AttributeValue(type,
                                        new ASN1OctetString(dn.toString())));
    }

    return new Attribute(type, name, attrValues);
  }



  /**
   * Creates an attribute for the root DSE with the following criteria.
   *
   * @param  name       The name for the attribute.
   * @param  lowerName  The name for the attribute formatted in all lowercase
   *                    characters.
   * @param  values     The set of values to use for the attribute.
   *
   * @return  The constructed attribute.
   */
  private Attribute createAttribute(String name, String lowerName,
                                    Collection<String> values)
  {
    AttributeType type = DirectoryServer.getAttributeType(lowerName);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(name);
    }

    LinkedHashSet<AttributeValue> attrValues =
         new LinkedHashSet<AttributeValue>();
    for (String s : values)
    {
      attrValues.add(new AttributeValue(type, new ASN1OctetString(s)));
    }

    return new Attribute(type, name, attrValues);
  }



  /**
   * Indicates whether an entry with the specified DN exists in the backend.
   * The default implementation obtains a read lock and calls
   * <CODE>getEntry</CODE>, but backend implementations may override this with a
   * more efficient version that does not require a lock.  The caller is not
   * required to hold any locks on the specified DN.
   *
   * @param  entryDN  The DN of the entry for which to determine existence.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in this backend,
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    // If the specified DN was the null DN, then it exists.
    if (entryDN.isNullDN())
    {
      return true;
    }


    // If it was not the null DN, then iterate through the associated
    // subordinate backends to make the determination.
    Map<DN,Backend> baseMap;
    if (subordinateBaseDNs == null)
    {
      baseMap = DirectoryServer.getPublicNamingContexts();
    }
    else
    {
      baseMap = subordinateBaseDNs;
    }

    for (DN baseDN : baseMap.keySet())
    {
      if (entryDN.isDescendantOf(baseDN))
      {
        Backend b = baseMap.get(baseDN);
        if (b.entryExists(entryDN))
        {
          return true;
        }
      }
    }

    return false;
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure that the
   * entry is appropriate for the backend and that no entry already exists with
   * the same DN.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry is
   *                       associated.  This may be <CODE>null</CODE> for adds
   *                       performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to add the
   *                              entry.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Message message =
        ERR_ROOTDSE_ADD_NOT_SUPPORTED.get(String.valueOf(entry.getDN()));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Removes the specified entry from this backend.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the backend supports a subtree delete operation and the client
   * included the appropriate information in the request).
   *
   * @param  entryDN          The DN of the entry to remove from this backend.
   * @param  deleteOperation  The delete operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          deletes performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to remove the
   *                              entry.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    Message message =
        ERR_ROOTDSE_DELETE_NOT_SUPPORTED.get(String.valueOf(entryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Replaces the specified entry with the provided entry in this backend.  The
   * backend must ensure that an entry already exists with the same DN as the
   * provided entry.
   *
   * @param  entry            The new entry to use in place of the existing
   *                          entry with the same DN.
   * @param  modifyOperation  The modify operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          modifications performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to replace
   *                              the entry.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
         throws DirectoryException
  {
    Message message = ERR_ROOTDSE_MODIFY_NOT_SUPPORTED.get(
        String.valueOf(entry.getDN()), String.valueOf(configEntryDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.
   *
   * @param  currentDN          The current DN of the entry to be replaced.
   * @param  entry              The new content to use for the entry.
   * @param  modifyDNOperation  The modify DN operation with which this action
   *                            is associated.  This may be <CODE>null</CODE>
   *                            for modify DN operations performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to perform
   *                              the rename.
   */
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Message message =
        ERR_ROOTDSE_MODIFY_DN_NOT_SUPPORTED.get(String.valueOf(currentDN));
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Processes the specified search in this backend.  Matching entries should be
   * provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs while processing the
   *                              search.
   *
   * @throws  CancelledOperationException  If this backend noticed and reacted
   *                                       to a request to cancel or abandon the
   *                                       add operation.
   */
  public void search(SearchOperation searchOperation)
         throws DirectoryException, CancelledOperationException
  {
    DN baseDN = searchOperation.getBaseDN();
    if (! baseDN.isNullDN())
    {
      Message message = ERR_ROOTDSE_INVALID_SEARCH_BASE.
          get(searchOperation.getConnectionID(),
              searchOperation.getOperationID(), String.valueOf(baseDN));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    SearchFilter filter = searchOperation.getFilter();
    switch (searchOperation.getScope())
    {
      case BASE_OBJECT:
        Entry dseEntry = getRootDSE();
        if (filter.matchesEntry(dseEntry))
        {
          searchOperation.returnEntry(dseEntry, null);
        }
        break;


      case SINGLE_LEVEL:
        Map<DN,Backend> baseMap;
        if (subordinateBaseDNs == null)
        {
          baseMap = DirectoryServer.getPublicNamingContexts();
        }
        else
        {
          baseMap = subordinateBaseDNs;
        }

        for (DN subBase : baseMap.keySet())
        {
          CancelRequest cancelRequest = searchOperation.getCancelRequest();
          if (cancelRequest != null)
          {
            throw new CancelledOperationException(CancelResult.CANCELED);
          }

          Backend b = baseMap.get(subBase);
          Entry subBaseEntry = b.getEntry(subBase);
          if ((subBaseEntry != null) && filter.matchesEntry(subBaseEntry))
          {
            searchOperation.returnEntry(subBaseEntry, null);
          }
        }
        break;


      case WHOLE_SUBTREE:
      case SUBORDINATE_SUBTREE:
        if (subordinateBaseDNs == null)
        {
          baseMap = DirectoryServer.getPublicNamingContexts();
        }
        else
        {
          baseMap = subordinateBaseDNs;
        }

        try
        {
          for (DN subBase : baseMap.keySet())
          {
            CancelRequest cancelRequest = searchOperation.getCancelRequest();
            if (cancelRequest != null)
            {
              throw new CancelledOperationException(CancelResult.CANCELED);
            }

            Backend b = baseMap.get(subBase);
            searchOperation.setBaseDN(subBase);

            try
            {
              b.search(searchOperation);
            }
            catch (DirectoryException de)
            {
              // If it's a "no such object" exception, then the base entry for
              // the backend doesn't exist.  This isn't an error, so ignore it.
              // We'll propogate all other errors, though.
              if (de.getResultCode() != ResultCode.NO_SUCH_OBJECT)
              {
                throw de;
              }
            }
          }
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          throw de;
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_ROOTDSE_UNEXPECTED_SEARCH_FAILURE.
              get(searchOperation.getConnectionID(),
                  searchOperation.getOperationID(),
                  stackTraceToSingleLineString(e));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         e);
        }
        finally
        {
          searchOperation.setBaseDN(rootDSEDN);
        }
        break;

      default:
        Message message = ERR_ROOTDSE_INVALID_SEARCH_SCOPE.
            get(searchOperation.getConnectionID(),
                searchOperation.getOperationID(),
                String.valueOf(searchOperation.getScope()));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return  The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return  The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * Indicates whether this backend provides a mechanism to export the data it
   * contains to an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF export
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFExport()
  {
    // We will only export the DSE entry itself.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER.get(
          stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }


    // Write the root DSE entry itself to it.  Make sure to close the LDIF
    // writer when we're done.
    try
    {
      ldifWriter.writeEntry(getRootDSE());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_ROOTDSE_UNABLE_TO_EXPORT_DSE.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      try
      {
        ldifWriter.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Indicates whether this backend provides a mechanism to import its data from
   * an LDIF file.
   *
   * @return  <CODE>true</CODE> if this backend provides an LDIF import
   *          mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFImport()
  {
    // This backend does not support LDIF imports.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    // This backend does not support LDIF imports.
    Message message = ERR_ROOTDSE_IMPORT_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Indicates whether this backend provides a backup mechanism of any kind.
   * This method is used by the backup process when backing up all backends to
   * determine whether this backend is one that should be skipped.  It should
   * only return <CODE>true</CODE> for backends that it is not possible to
   * archive directly (e.g., those that don't store their data locally, but
   * rather pass through requests to some other repository).
   *
   * @return  <CODE>true</CODE> if this backend provides any kind of backup
   *          mechanism, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * Indicates whether this backend provides a mechanism to perform a backup of
   * its contents in a form that can be restored later, based on the provided
   * configuration.
   *
   * @param  backupConfig       The configuration of the backup for which to
   *                            make the determination.
   * @param  unsupportedReason  A buffer to which a message can be appended
   *                            explaining why the requested backup is not
   *                            supported.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          performing backups with the provided configuration, or
   *          <CODE>false</CODE> if not.
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDirectory  The backup directory structure with which the
   *                          specified backup is associated.
   * @param  backupID         The backup ID for the backup to be removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the specified
   *                              backup for some reason (e.g., no such backup
   *                              exists or there are other backups that are
   *                              dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return  <CODE>true</CODE> if this backend provides a mechanism for
   *          restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    // This backend does not provide a backup/restore mechanism.
    Message message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(Configuration configuration,
                                           List<Message> unacceptableReasons)
  {
    RootDSEBackendCfg config = (RootDSEBackendCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       RootDSEBackendCfg cfg,
       List<Message> unacceptableReasons)
  {
    boolean configIsAcceptable = true;


    try
    {
      Set<DN> subDNs = cfg.getSubordinateBaseDN();
      if (subDNs.isEmpty())
      {
        // This is fine -- we'll just use the set of user-defined suffixes.
      }
      else
      {
        for (DN baseDN : subDNs)
        {
          Backend backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            Message message = WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(
                    String.valueOf(baseDN));
            unacceptableReasons.add(message);
            configIsAcceptable = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
              stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(RootDSEBackendCfg cfg)
  {
    ResultCode         resultCode          = ResultCode.SUCCESS;
    boolean            adminActionRequired = false;
    ArrayList<Message> messages            = new ArrayList<Message>();


    // Check to see if we should apply a new set of base DNs.
    ConcurrentHashMap<DN,Backend> subBases;
    try
    {
      Set<DN> subDNs = cfg.getSubordinateBaseDN();
      if (subDNs.isEmpty())
      {
        // This is fine -- we'll just use the set of user-defined suffixes.
        subBases = null;
      }
      else
      {
        subBases = new ConcurrentHashMap<DN,Backend>();
        for (DN baseDN : subDNs)
        {
          Backend backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            // This is not fine.  We can't use a suffix that doesn't exist.
            Message message = WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(
                    String.valueOf(baseDN));
            messages.add(message);

            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = DirectoryServer.getServerErrorResultCode();
            }
          }
          else
          {
            subBases.put(baseDN, backend);
          }
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
              stackTraceToSingleLineString(e));
      messages.add(message);

      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      subBases = null;
    }


    boolean newShowAll = cfg.isShowAllAttributes();


    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> userAttrs = new ArrayList<Attribute>();
    try
    {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(configEntryDN);

      for (List<Attribute> attrs :
           configEntry.getEntry().getUserAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isDSEConfigAttribute(a))
          {
            userAttrs.add(a);
          }
        }
      }
      for (List<Attribute> attrs :
           configEntry.getEntry().getOperationalAttributes().values())
      {
        for (Attribute a : attrs)
        {
          if (! isDSEConfigAttribute(a))
          {
            userAttrs.add(a);
          }
        }
      }
    }
    catch (ConfigException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      messages.add(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
              String.valueOf(configEntryDN),
              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    if (resultCode == ResultCode.SUCCESS)
    {
      subordinateBaseDNs = subBases;

      if (subordinateBaseDNs == null)
      {
        Message message = INFO_ROOTDSE_USING_SUFFIXES_AS_BASE_DNS.get();
        messages.add(message);
      }
      else
      {
        StringBuilder basesStr = new StringBuilder();
        for (DN dn : subordinateBaseDNs.keySet())
        {
          if (basesStr.length() > 0)
          {
            basesStr.append(", ");
          }
          else
          {
            basesStr.append("{ ");
          }

          basesStr.append(dn);
        }

        basesStr.append(" }");

        Message message = INFO_ROOTDSE_USING_NEW_SUBORDINATE_BASE_DNS.get(
                basesStr.toString());
        messages.add(message);
      }


      if (showAllAttributes != newShowAll)
      {
        showAllAttributes = newShowAll;
        Message message = INFO_ROOTDSE_UPDATED_SHOW_ALL_ATTRS.get(
                ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES,
                String.valueOf(showAllAttributes));
        messages.add(message);
      }


      userDefinedAttributes = userAttrs;
      Message message = INFO_ROOTDSE_USING_NEW_USER_ATTRS.get();
      messages.add(message);
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

