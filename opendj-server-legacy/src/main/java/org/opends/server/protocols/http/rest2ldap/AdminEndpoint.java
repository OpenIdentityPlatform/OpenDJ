/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 *
 */
package org.opends.server.protocols.http.rest2ldap;

import static org.forgerock.http.routing.RouteMatchers.newResourceApiVersionBehaviourManager;
import static org.forgerock.http.routing.Version.version;
import static org.forgerock.json.resource.RouteMatchers.resourceApiVersionContextFilter;
import static org.forgerock.json.resource.http.CrestHttp.newHttpHandler;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getBooleanSyntax;
import static org.forgerock.opendj.ldap.schema.CoreSchema.getIntegerSyntax;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.CREATE_ONLY;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_ONLY;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_WRITE;
import static org.forgerock.util.Options.defaultOptions;
import static org.opends.messages.ConfigMessages.ERR_BAD_ADMIN_API_RESOURCE_VERSION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.routing.ResourceApiVersionBehaviourManager;
import org.forgerock.http.routing.Version;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.FilterChain;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.DefaultBehaviorProvider;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectOption;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelationOption;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.TopCfgDefn;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.rest2ldap.AbstractRequestHandler;
import org.forgerock.opendj.rest2ldap.ReferencePropertyMapper;
import org.forgerock.opendj.rest2ldap.Resource;
import org.forgerock.opendj.rest2ldap.Rest2Ldap;
import org.forgerock.opendj.rest2ldap.SimplePropertyMapper;
import org.forgerock.opendj.rest2ldap.SubResourceCollection;
import org.forgerock.opendj.rest2ldap.SubResourceSingleton;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.forgerock.opendj.server.config.server.AdminEndpointCfg;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.opends.server.api.HttpEndpoint;
import org.opends.server.core.ServerContext;
import org.opends.server.types.InitializationException;

/**
 * An HTTP endpoint providing access to the server's monitoring backend (cn=monitor) and its configuration (cn=config).
 */
public final class AdminEndpoint extends HttpEndpoint<AdminEndpointCfg>
{
  private static final Version ADMIN_API_VERSION = version("1.0");
  private static final String TYPE_PROPERTY = "_schema";
  private static final String ADMIN_API = "admin-api";
  private static final String MONITOR = "monitor";
  private static final String CONFIG = "config";

  /**
   * Create a new AdminEndpoint with the supplied configuration.
   *
   * @param configuration
   *          Configuration to use for the {@link HttpApplication}
   * @param serverContext
   *          Server of this LDAP server
   */
  public AdminEndpoint(AdminEndpointCfg configuration, ServerContext serverContext)
  {
    super(configuration, serverContext);
  }

  @Override
  public HttpApplication newHttpApplication() throws InitializationException
  {
    return new AdminHttpApplication();
  }

  /**
   * Specialized {@link HttpApplication} using internal connections to this local LDAP server.
   */
  private final class AdminHttpApplication implements HttpApplication
  {
    private LDAPProfile ldapProfile = LDAPProfile.getInstance();

    @Override
    public Handler start() throws HttpApplicationException
    {
      final Map<String, Resource> resources = new HashMap<>();

      // Define the entry point to the admin API.
      resources.put(ADMIN_API, resource(ADMIN_API).subResources(singletonOf(MONITOR).urlTemplate(MONITOR)
                                                                                    .dnTemplate("cn=monitor")
                                                                                    .isReadOnly(true),
                                                                singletonOf(CONFIG).urlTemplate(CONFIG)
                                                                                   .dnTemplate("cn=config")));

      // Define the monitoring endpoint.
      resources.put(MONITOR, resource(MONITOR).includeAllUserAttributesByDefault(true)
                                              .excludedDefaultUserAttributes("objectClass", "cn")
                                              .objectClass ("ds-monitor-entry")
                                              .property("_id", simple("cn"))
                                              .subResource(collectionOf(MONITOR).useClientDnNaming("cn")));

      // Build the configuration endpoint using the configuration framework.
      final TopCfgDefn topCfgDefn = TopCfgDefn.getInstance();
      final RootCfgDefn rootCfgDefn = RootCfgDefn.getInstance();
      final GlobalCfgDefn globalCfgDefn = GlobalCfgDefn.getInstance();

      // The configuration framework exposes the root and global configuration as separate resources, but it would be
      // nice if we exposed them as a single resource.
      final Resource config = resource(CONFIG);
      configureResourceProperties(globalCfgDefn, config);
      configureResourceSubResources(rootCfgDefn, config, true);
      resources.put(CONFIG, config);

      resources.put(topCfgDefn.getName(), buildResource(topCfgDefn));
      for (final AbstractManagedObjectDefinition<?, ?> mod : topCfgDefn.getAllChildren())
      {
        if (!mod.hasOption(ManagedObjectOption.HIDDEN) && mod != globalCfgDefn && mod != rootCfgDefn)
        {
          resources.put(mod.getName(), buildResource(mod));
        }
      }

      // Now that all resources are defined, perform a second pass processing all relation definitions in order to
      // identity which attributes should be used for the "_id" property.
      for (final AbstractManagedObjectDefinition<?, ?> mod : topCfgDefn.getAllChildren())
      {
        for (final RelationDefinition<?, ?> rd : mod.getRelationDefinitions())
        {
          if (rd instanceof InstantiableRelationDefinition)
          {
            final InstantiableRelationDefinition<?, ?> ird = (InstantiableRelationDefinition) rd;
            final AbstractManagedObjectDefinition<?, ?> d = rd.getChildDefinition();
            final String rdnType = ldapProfile.getRelationChildRDNType(ird);
            resources.get(d.getName()).property("_id", simple(rdnType).isRequired(true).writability(CREATE_ONLY));
          }
        }
      }

      final Rest2Ldap rest2Ldap = rest2Ldap(defaultOptions(), resources.values());
      final RequestHandler handler = rest2Ldap.newRequestHandlerFor(ADMIN_API);
      final Router versionRouter = new Router();
      versionRouter.addRoute(ADMIN_API_VERSION, handler);
      versionRouter.setDefaultRoute(new AbstractRequestHandler()
      {
        @Override
        protected <V> Promise<V, ResourceException> handleRequest(final Context context, final Request request)
        {
          final String message = ERR_BAD_ADMIN_API_RESOURCE_VERSION.get(request.getResourceVersion(), ADMIN_API_VERSION)
                                                                   .toString();
          return new BadRequestException(message).asPromise();
        }
      });

      // FIXME: Disable the warning header for now due to CREST-389 / CREST-390.
      final ResourceApiVersionBehaviourManager behaviourManager = newResourceApiVersionBehaviourManager();
      behaviourManager.setWarningEnabled(false);
      return newHttpHandler(new FilterChain(versionRouter, resourceApiVersionContextFilter(behaviourManager)));
    }

    private Resource buildResource(final AbstractManagedObjectDefinition<?, ?> mod)
    {
      final Resource resource = resource(mod.getName());
      configureResourceProperties(mod, resource);
      configureResourceSubResources(mod, resource, false);
      return resource;
    }

    private void configureResourceSubResources(final AbstractManagedObjectDefinition<?, ?> mod, final Resource resource,
                                               final boolean removeCnEqualsConfig)
    {
      for (final RelationDefinition<?, ?> rd : mod.getRelationDefinitions())
      {
        if (rd.hasOption(RelationOption.HIDDEN))
        {
          continue;
        }

        if (rd instanceof InstantiableRelationDefinition)
        {
          final InstantiableRelationDefinition<?, ?> ird = (InstantiableRelationDefinition) rd;
          final AbstractManagedObjectDefinition<?, ?> d = rd.getChildDefinition();
          final SubResourceCollection collection = collectionOf(d.getName())
                  .useClientDnNaming(ldapProfile.getRelationChildRDNType(ird))
                  .urlTemplate(ird.getPluralName())
                  .dnTemplate(getRelationRdnSequence(rd, removeCnEqualsConfig))
                  .glueObjectClasses(ldapProfile.getRelationObjectClasses(rd).toArray(new String[0]));
          resource.subResource(collection);
        }
        else if (rd instanceof SingletonRelationDefinition)
        {
          if (mod == RootCfgDefn.getInstance() && rd.getChildDefinition() == GlobalCfgDefn.getInstance())
          {
            // Special case: ignore the root -> global configuration relation because these two resources are merged
            // into a single resource within the REST API.
            continue;
          }
          final SubResourceSingleton singleton = singletonOf(rd.getChildDefinition().getName())
                  .urlTemplate(rd.getName())
                  .dnTemplate(getRelationRdnSequence(rd, removeCnEqualsConfig));
          resource.subResource(singleton);
        }
        // Optional/set NYI
      }
    }

    private String getRelationRdnSequence(final RelationDefinition<?, ?> rd, final boolean removeCnEqualsConfig)
    {
      final String rdnSequence = ldapProfile.getRelationRDNSequence(rd);
      if (removeCnEqualsConfig)
      {
        final DN dn = DN.valueOf(rdnSequence);
        return dn.localName(dn.size() - 1).toString();
      }
      return rdnSequence;
    }

    private void configureResourceProperties(final AbstractManagedObjectDefinition<?, ?> mod, final Resource resource)
    {
      resource.isAbstract(!(mod instanceof ManagedObjectDefinition));
      if (mod.getParent() != null)
      {
        resource.superType(mod.getParent().getName());
      }

      final String objectClass = ldapProfile.getObjectClass(mod);
      if (objectClass != null)
      {
        resource.objectClass(objectClass);
      }

      resource.resourceTypeProperty(new JsonPointer(TYPE_PROPERTY));
      resource.property(TYPE_PROPERTY, resourceType());
      resource.property("_rev", simple("etag").writability(READ_ONLY));

      for (final PropertyDefinition<?> pd : mod.getPropertyDefinitions())
      {
        if (pd.hasOption(PropertyOption.HIDDEN))
        {
          continue;
        }

        final String attributeName = ldapProfile.getAttributeName(mod, pd);
        if (pd instanceof AggregationPropertyDefinition)
        {
          final AggregationPropertyDefinition apd = (AggregationPropertyDefinition) pd;
          final String relationChildRdnType = ldapProfile.getRelationChildRDNType(apd.getRelationDefinition());
          final SimplePropertyMapper referencePropertyMapper = simple(relationChildRdnType).isRequired(true);
          final DN baseDn = apd.getParentPath().toDN()
                               .child(ldapProfile.getRelationRDNSequence(apd.getRelationDefinition()));
          final ReferencePropertyMapper mapper = reference(attributeName,
                                                           baseDn.toString(),
                                                           relationChildRdnType,
                                                           referencePropertyMapper);
          resource.property(pd.getName(), mapper);
        }
        else
        {
          final SimplePropertyMapper mapper = simple(attributeName)
                  .isRequired(pd.hasOption(PropertyOption.MANDATORY))
                  .writability(pd.hasOption(PropertyOption.READ_ONLY) ? CREATE_ONLY : READ_WRITE)
                  .isMultiValued(pd.hasOption(PropertyOption.MULTI_VALUED));

          // Define the default value as well if possible.
          final DefaultBehaviorProvider<?> dbp = pd.getDefaultBehaviorProvider();
          if (dbp instanceof DefinedDefaultBehaviorProvider)
          {
            final DefinedDefaultBehaviorProvider<?> ddbp = (DefinedDefaultBehaviorProvider) dbp;
            final Collection<String> defaultValues = ddbp.getDefaultValues();
            final List<Object> decodedDefaultValues = new ArrayList<>(defaultValues.size());
            final Function<String, ?, NeverThrowsException> converter = getConverter(attributeName);
            for (final String defaultValue : defaultValues)
            {
              decodedDefaultValues.add(converter.apply(defaultValue));
            }
            mapper.defaultJsonValues(decodedDefaultValues);
          }
          resource.property(pd.getName(), mapper);
        }
      }
    }

    private Function<String, ?, NeverThrowsException> getConverter(final String attributeName)
    {
      final AttributeDescription attributeDescription = AttributeDescription.valueOf(attributeName);
      final Syntax syntax = attributeDescription.getAttributeType().getSyntax();
      if (syntax.equals(getBooleanSyntax()))
      {
        return Functions.stringToBoolean();
      }
      else if (syntax.equals(getIntegerSyntax()))
      {
        return Functions.stringToLong();
      }
      else
      {
        return Functions.identityFunction();
      }
    }

    @Override
    public void stop()
    {
      // Nothing to do
    }

    @Override
    public Factory<Buffer> getBufferFactory()
    {
      return null;
    }
  }
}
