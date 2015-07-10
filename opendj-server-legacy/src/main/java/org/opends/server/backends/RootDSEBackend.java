/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.RootDSEBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.ClientConnection;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.*;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.LDIFWriter;

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
       extends Backend<RootDSEBackendCfg>
       implements ConfigurationChangeListener<RootDSEBackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of standard "static" attributes that we will always include in the
   * root DSE entry and won't change while the server is running.
   */
  private ArrayList<Attribute> staticDSEAttributes;

  /**
   * The set of user-defined attributes that will be included in the root DSE
   * entry.
   */
  private ArrayList<Attribute> userDefinedAttributes;

  /**
   * Indicates whether the attributes of the root DSE should always be treated
   * as user attributes even if they are defined as operational in the schema.
   */
  private boolean showAllAttributes;

  /**
   * The set of subordinate base DNs and their associated backends that will be
   * used for non-base searches.
   */
  private ConcurrentHashMap<DN, Backend<?>> subordinateBaseDNs;

  /** The set of objectclasses that will be used in the root DSE entry. */
  private HashMap<ObjectClass,String> dseObjectClasses;

  /** The current configuration state. */
  private RootDSEBackendCfg currentConfig;

  /** The DN of the configuration entry for this backend. */
  private DN configEntryDN;

  /** The DN for the root DSE. */
  private DN rootDSEDN;

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;



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

  /** {@inheritDoc} */
  @Override
  public void configureBackend(RootDSEBackendCfg config, ServerContext serverContext) throws ConfigException
  {
    Reject.ifNull(config);
    currentConfig = config;
    configEntryDN = config.dn();
  }

  /** {@inheritDoc} */
  @Override
  public void openBackend() throws ConfigException, InitializationException
  {
    ConfigEntry configEntry =
         DirectoryServer.getConfigEntry(configEntryDN);

    // Make sure that a configuration entry was provided.  If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      LocalizableMessage message = ERR_ROOTDSE_CONFIG_ENTRY_NULL.get();
      throw new ConfigException(message);
    }

    userDefinedAttributes = new ArrayList<>();
    addAllUserDefinedAttrs(userDefinedAttributes, configEntry.getEntry());


    // Create the set of base DNs that we will handle.  In this case, it's just
    // the root DSE.
    rootDSEDN    = DN.rootDN();
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
        subordinateBaseDNs = new ConcurrentHashMap<>();
        for (DN baseDN : subDNs)
        {
          Backend<?> backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            logger.warn(WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE, baseDN);
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
      logger.traceException(e);

      LocalizableMessage message = WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
          stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }


    // Determine whether all root DSE attributes should be treated as user
    // attributes.
    showAllAttributes = currentConfig.isShowAllAttributes();


    // Construct the set of "static" attributes that will always be present in
    // the root DSE.
    staticDSEAttributes = new ArrayList<>();
    staticDSEAttributes.add(Attributes.create(ATTR_VENDOR_NAME, SERVER_VENDOR_NAME));
    staticDSEAttributes.add(Attributes.create(ATTR_VENDOR_VERSION,
                                 DirectoryServer.getVersionString()));
    staticDSEAttributes.add(Attributes.create("fullVendorVersion",
                                 BuildVersion.binaryVersion().toString()));

    // Construct the set of objectclasses to include in the root DSE entry.
    dseObjectClasses = new HashMap<>(2);
    ObjectClass topOC = DirectoryServer.getObjectClass(OC_TOP);
    if (topOC == null)
    {
      topOC = DirectoryServer.getDefaultObjectClass(OC_TOP);
    }
    dseObjectClasses.put(topOC, OC_TOP);

    ObjectClass rootDSEOC = DirectoryServer.getObjectClass(OC_ROOT_DSE);
    if (rootDSEOC == null)
    {
      rootDSEOC = DirectoryServer.getDefaultObjectClass(OC_ROOT_DSE);
    }
    dseObjectClasses.put(rootDSEOC, OC_ROOT_DSE);


    // Set the backend ID for this backend. The identifier needs to be
    // specific enough to avoid conflict with user backend identifiers.
    setBackendID("__root.dse__");


    // Register as a change listener.
    currentConfig.addChangeListener(this);
  }

  /**
   * Get the set of user-defined attributes for the configuration entry. Any
   * attributes that we do not recognize will be included directly in the root
   * DSE.
   */
  private void addAllUserDefinedAttrs(ArrayList<Attribute> userDefinedAttrs, Entry configEntry)
  {
    for (List<Attribute> attrs : configEntry.getUserAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (!isDSEConfigAttribute(a))
        {
          userDefinedAttrs.add(a);
        }
      }
    }
    for (List<Attribute> attrs : configEntry.getOperationalAttributes().values())
    {
      for (Attribute a : attrs)
      {
        if (!isDSEConfigAttribute(a))
        {
          userDefinedAttrs.add(a);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void closeBackend()
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
    return attrType.hasName(ATTR_ROOT_DSE_SUBORDINATE_BASE_DN.toLowerCase())
        || attrType.hasName(ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES.toLowerCase())
        || attrType.hasName(ATTR_COMMON_NAME);
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public synchronized long getEntryCount()
  {
    // There is always just a single entry in this backend.
    return 1;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    final long ret = getNumberOfChildren(entryDN);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    checkNotNull(baseDN, "baseDN must not be null");
    if (!baseDN.isRootDN())
    {
      return -1;
    }

    long count = 1;
    for (Map.Entry<DN, Backend<?>> entry : getSubordinateBaseDNs().entrySet())
    {
      DN subBase = entry.getKey();
      Backend<?> b = entry.getValue();
      Entry subBaseEntry = b.getEntry(subBase);
      if (subBaseEntry != null)
      {
        count++;
        count += b.getNumberOfEntriesInBaseDN(subBase);
      }
    }

    return count;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    checkNotNull(parentDN, "parentDN must not be null");
    if (!parentDN.isRootDN())
    {
      return -1;
    }

    long count = 0;

    for (Map.Entry<DN, Backend<?>> entry : getSubordinateBaseDNs().entrySet())
    {
      DN subBase = entry.getKey();
      Entry subBaseEntry = entry.getValue().getEntry(subBase);
      if (subBaseEntry != null)
      {
        count ++;
      }
    }

    return count;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the requested entry was the root DSE, then create and return it.
    if (entryDN == null || entryDN.isRootDN())
    {
      return getRootDSE();
    }


    // This method should never be used to get anything other than the root DSE.
    // If we got here, then that appears to be the case, so log a message.
    logger.warn(WARN_ROOTDSE_GET_ENTRY_NONROOT, entryDN);


    // Go ahead and check the subordinate backends to see if we can find the
    // entry there.  Note that in order to avoid potential loop conditions, this
    // will only work if the set of subordinate bases has been explicitly
    // specified.
    if (subordinateBaseDNs != null)
    {
      for (Backend<?> b : subordinateBaseDNs.values())
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
   * @return The root DSE entry for the Directory Server.
   */
  public Entry getRootDSE()
  {
    return getRootDSE(null);
  }



  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @param connection
   *          The client connection, or {@code null} if there is no associated
   *          client connection.
   * @return The root DSE entry for the Directory Server.
   */
  private Entry getRootDSE(ClientConnection connection)
  {
    HashMap<AttributeType,List<Attribute>> dseUserAttrs = new HashMap<>();
    HashMap<AttributeType,List<Attribute>> dseOperationalAttrs = new HashMap<>();


    Attribute publicNamingContextAttr = createAttribute(
        ATTR_NAMING_CONTEXTS, ATTR_NAMING_CONTEXTS_LC,
        DirectoryServer.getPublicNamingContexts().keySet());
    addAttribute(publicNamingContextAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "ds-private-naming-contexts" attribute.
    Attribute privateNamingContextAttr = createAttribute(
        ATTR_PRIVATE_NAMING_CONTEXTS, ATTR_PRIVATE_NAMING_CONTEXTS,
        DirectoryServer.getPrivateNamingContexts().keySet());
    addAttribute(privateNamingContextAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedControl" attribute.
    Attribute supportedControlAttr = createAttribute(ATTR_SUPPORTED_CONTROL,
        ATTR_SUPPORTED_CONTROL_LC, DirectoryServer.getSupportedControls());
    addAttribute(supportedControlAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedExtension" attribute.
    Attribute supportedExtensionAttr = createAttribute(
        ATTR_SUPPORTED_EXTENSION, ATTR_SUPPORTED_EXTENSION_LC, DirectoryServer
            .getSupportedExtensions().keySet());
    addAttribute(supportedExtensionAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedFeature" attribute.
    Attribute supportedFeatureAttr = createAttribute(ATTR_SUPPORTED_FEATURE,
        ATTR_SUPPORTED_FEATURE_LC, DirectoryServer.getSupportedFeatures());
    addAttribute(supportedFeatureAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedSASLMechanisms" attribute.
    Attribute supportedSASLMechAttr = createAttribute(
        ATTR_SUPPORTED_SASL_MECHANISMS, ATTR_SUPPORTED_SASL_MECHANISMS_LC,
        DirectoryServer.getSupportedSASLMechanisms().keySet());
    addAttribute(supportedSASLMechAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedLDAPVersions" attribute.
    TreeSet<String> versionStrings = new TreeSet<>();
    for (Integer ldapVersion : DirectoryServer.getSupportedLDAPVersions())
    {
      versionStrings.add(ldapVersion.toString());
    }
    Attribute supportedLDAPVersionAttr = createAttribute(
        ATTR_SUPPORTED_LDAP_VERSION, ATTR_SUPPORTED_LDAP_VERSION_LC, versionStrings);
    addAttribute(supportedLDAPVersionAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedAuthPasswordSchemes" attribute.
    Set<String> authPWSchemes =
         DirectoryServer.getAuthPasswordStorageSchemes().keySet();
    if (!authPWSchemes.isEmpty())
    {
      Attribute supportedAuthPWSchemesAttr =
           createAttribute(ATTR_SUPPORTED_AUTH_PW_SCHEMES,
                           ATTR_SUPPORTED_AUTH_PW_SCHEMES_LC, authPWSchemes);
      ArrayList<Attribute> supportedAuthPWSchemesAttrs = newArrayList(supportedAuthPWSchemesAttr);
      if (showAllAttributes
          || !supportedSASLMechAttr.getAttributeType().isOperational())
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


    // Obtain TLS protocol and cipher support.
    Collection<String> supportedTlsProtocols;
    Collection<String> supportedTlsCiphers;
    if (connection != null)
    {
      // Only return the list of enabled protocols / ciphers for the connection
      // handler to which the client is connected.
      supportedTlsProtocols = connection.getConnectionHandler()
          .getEnabledSSLProtocols();
      supportedTlsCiphers = connection.getConnectionHandler()
          .getEnabledSSLCipherSuites();
    }
    else
    {
      try
      {
        final SSLContext context = SSLContext.getDefault();
        final SSLParameters parameters = context.getSupportedSSLParameters();
        supportedTlsProtocols = Arrays.asList(parameters.getProtocols());
        supportedTlsCiphers = Arrays.asList(parameters.getCipherSuites());
      }
      catch (Exception e)
      {
        // A default SSL context should always be available.
        supportedTlsProtocols = Collections.emptyList();
        supportedTlsCiphers = Collections.emptyList();
      }
    }

    // Add the "supportedTLSProtocols" attribute.
    Attribute supportedTLSProtocolsAttr = createAttribute(
        ATTR_SUPPORTED_TLS_PROTOCOLS, ATTR_SUPPORTED_TLS_PROTOCOLS_LC,
        supportedTlsProtocols);
    addAttribute(supportedTLSProtocolsAttr, dseUserAttrs, dseOperationalAttrs);

    // Add the "supportedTLSCiphers" attribute.
    Attribute supportedTLSCiphersAttr = createAttribute(
        ATTR_SUPPORTED_TLS_CIPHERS, ATTR_SUPPORTED_TLS_CIPHERS_LC,
        supportedTlsCiphers);
    addAttribute(supportedTLSCiphersAttr, dseUserAttrs, dseOperationalAttrs);

    addAll(staticDSEAttributes, dseUserAttrs, dseOperationalAttrs);
    addAll(userDefinedAttributes, dseUserAttrs, dseOperationalAttrs);

    // Construct and return the entry.
    Entry e = new Entry(rootDSEDN, dseObjectClasses, dseUserAttrs,
                        dseOperationalAttrs);
    e.processVirtualAttributes();
    return e;
  }

  private void addAll(ArrayList<Attribute> attributes,
      Map<AttributeType, List<Attribute>> userAttrs, Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    for (Attribute a : attributes)
    {
      AttributeType type = a.getAttributeType();

      final Map<AttributeType, List<Attribute>> attrsMap = type.isOperational() && !showAllAttributes
          ? operationalAttrs
          : userAttrs;
      List<Attribute> attrs = attrsMap.get(type);
      if (attrs == null)
      {
        attrs = new ArrayList<>();
        attrsMap.put(type, attrs);
      }
      attrs.add(a);
    }
  }



  private void addAttribute(Attribute publicNamingContextAttr,
      HashMap<AttributeType, List<Attribute>> userAttrs,
      HashMap<AttributeType, List<Attribute>> operationalAttrs)
  {
    if (!publicNamingContextAttr.isEmpty())
    {
      List<Attribute> privateNamingContextAttrs = newArrayList(publicNamingContextAttr);
      final AttributeType attrType = publicNamingContextAttr.getAttributeType();
      if (showAllAttributes || !attrType.isOperational())
      {
        userAttrs.put(attrType, privateNamingContextAttrs);
      }
      else
      {
        operationalAttrs.put(attrType, privateNamingContextAttrs);
      }
    }
  }

  /**
   * Creates an attribute for the root DSE with the following
   * criteria.
   *
   * @param name
   *          The name for the attribute.
   * @param lowerName
   *          The name for the attribute formatted in all lowercase
   *          characters.
   * @param values
   *          The set of values to use for the attribute.
   * @return The constructed attribute.
   */
  private Attribute createAttribute(String name, String lowerName,
                                    Collection<? extends Object> values)
  {
    AttributeType type = DirectoryServer.getAttributeType(lowerName, name);

    AttributeBuilder builder = new AttributeBuilder(type, name);
    builder.addAllStrings(values);
    return builder.toAttribute();
  }

  /** {@inheritDoc} */
  @Override
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    // If the specified DN was the null DN, then it exists.
    if (entryDN.isRootDN())
    {
      return true;
    }


    // If it was not the null DN, then iterate through the associated
    // subordinate backends to make the determination.
    for (Map.Entry<DN, Backend<?>> entry : getSubordinateBaseDNs().entrySet())
    {
      DN baseDN = entry.getKey();
      if (entryDN.isDescendantOf(baseDN))
      {
        Backend<?> b = entry.getValue();
        if (b.entryExists(entryDN))
        {
          return true;
        }
      }
    }

    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(entry.getName(), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(entryDN, getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_ROOTDSE_MODIFY_NOT_SUPPORTED.get(newEntry.getName(), configEntryDN));
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
                                   ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(currentDN, getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation)
         throws DirectoryException, CanceledOperationException {
    DN baseDN = searchOperation.getBaseDN();
    if (! baseDN.isRootDN())
    {
      LocalizableMessage message = ERR_ROOTDSE_INVALID_SEARCH_BASE.
          get(searchOperation.getConnectionID(), searchOperation.getOperationID(), baseDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    SearchFilter filter = searchOperation.getFilter();
    switch (searchOperation.getScope().asEnum())
    {
      case BASE_OBJECT:
        Entry dseEntry = getRootDSE(searchOperation.getClientConnection());
        if (filter.matchesEntry(dseEntry))
        {
          searchOperation.returnEntry(dseEntry, null);
        }
        break;


      case SINGLE_LEVEL:
        for (Map.Entry<DN, Backend<?>> entry : getSubordinateBaseDNs().entrySet())
        {
          searchOperation.checkIfCanceled(false);

          DN subBase = entry.getKey();
          Backend<?> b = entry.getValue();
          Entry subBaseEntry = b.getEntry(subBase);
          if (subBaseEntry != null && filter.matchesEntry(subBaseEntry))
          {
            searchOperation.returnEntry(subBaseEntry, null);
          }
        }
        break;


      case WHOLE_SUBTREE:
      case SUBORDINATES:
        try
        {
          for (Map.Entry<DN, Backend<?>> entry : getSubordinateBaseDNs().entrySet())
          {
            searchOperation.checkIfCanceled(false);

            DN subBase = entry.getKey();
            Backend<?> b = entry.getValue();

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
          logger.traceException(de);

          throw de;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_ROOTDSE_UNEXPECTED_SEARCH_FAILURE.
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
        LocalizableMessage message = ERR_ROOTDSE_INVALID_SEARCH_SCOPE.
            get(searchOperation.getConnectionID(),
                searchOperation.getOperationID(),
                searchOperation.getScope());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
    }
  }

  /**
   * Returns the subordinate base DNs of the root DSE.
   *
   * @return the subordinate base DNs of the root DSE
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Map<DN, Backend<?>> getSubordinateBaseDNs()
  {
    if (subordinateBaseDNs != null)
    {
      return subordinateBaseDNs;
    }
    return (Map) DirectoryServer.getPublicNamingContexts();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    // We will only export the DSE entry itself.
    return backendOperation.equals(BackendOperation.LDIF_EXPORT);
  }

  /** {@inheritDoc} */
  @Override
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
      logger.traceException(e);

      LocalizableMessage message = ERR_ROOTDSE_UNABLE_TO_CREATE_LDIF_WRITER.get(
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
      logger.traceException(e);

      LocalizableMessage message =
          ERR_ROOTDSE_UNABLE_TO_EXPORT_DSE.get(stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      close(ldifWriter);
    }
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_ROOTDSE_BACKUP_AND_RESTORE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(RootDSEBackendCfg config,
                                           List<LocalizableMessage> unacceptableReasons,
                                           ServerContext serverContext)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
       RootDSEBackendCfg cfg,
       List<LocalizableMessage> unacceptableReasons)
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
          Backend<?> backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            unacceptableReasons.add(WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(baseDN));
            configIsAcceptable = false;
          }
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReasons.add(WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
          stackTraceToSingleLineString(e)));
      configIsAcceptable = false;
    }


    return configIsAcceptable;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(RootDSEBackendCfg cfg)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Check to see if we should apply a new set of base DNs.
    ConcurrentHashMap<DN, Backend<?>> subBases;
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
        subBases = new ConcurrentHashMap<>();
        for (DN baseDN : subDNs)
        {
          Backend<?> backend = DirectoryServer.getBackend(baseDN);
          if (backend == null)
          {
            // This is not fine.  We can't use a suffix that doesn't exist.
            ccr.addMessage(WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(baseDN));
            ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
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
      logger.traceException(e);

      ccr.setResultCodeIfSuccess(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(
              stackTraceToSingleLineString(e)));

      subBases = null;
    }


    boolean newShowAll = cfg.isShowAllAttributes();


    // Check to see if there is a new set of user-defined attributes.
    ArrayList<Attribute> userAttrs = new ArrayList<>();
    try
    {
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(configEntryDN);
      addAllUserDefinedAttrs(userAttrs, configEntry.getEntry());
    }
    catch (ConfigException e)
    {
      logger.traceException(e);

      ccr.addMessage(ERR_CONFIG_BACKEND_ERROR_INTERACTING_WITH_BACKEND_ENTRY.get(
              configEntryDN, stackTraceToSingleLineString(e)));
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
    }


    if (ccr.getResultCode() == ResultCode.SUCCESS)
    {
      subordinateBaseDNs = subBases;

      if (subordinateBaseDNs == null)
      {
        ccr.addMessage(INFO_ROOTDSE_USING_SUFFIXES_AS_BASE_DNS.get());
      }
      else
      {
        String basesStr = "{ " + Utils.joinAsString(", ", subordinateBaseDNs.keySet()) + " }";
        ccr.addMessage(INFO_ROOTDSE_USING_NEW_SUBORDINATE_BASE_DNS.get(basesStr));
      }


      if (showAllAttributes != newShowAll)
      {
        showAllAttributes = newShowAll;
        ccr.addMessage(INFO_ROOTDSE_UPDATED_SHOW_ALL_ATTRS.get(
                ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES, showAllAttributes));
      }


      userDefinedAttributes = userAttrs;
      ccr.addMessage(INFO_ROOTDSE_USING_NEW_USER_ATTRS.get());
    }


    return ccr;
  }
}
