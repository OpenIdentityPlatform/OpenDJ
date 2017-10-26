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
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Arrays.asList;
import static org.forgerock.api.enums.CountPolicy.*;
import static org.forgerock.api.enums.PagingMode.*;
import static org.forgerock.api.enums.ParameterSource.*;
import static org.forgerock.api.enums.PatchOperation.*;
import static org.forgerock.api.enums.Stability.*;
import static org.forgerock.api.models.VersionedPath.*;
import static org.forgerock.json.JsonValue.*;
import static org.forgerock.json.resource.ResourceException.*;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_ABSTRACT_TYPE_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_MISSING_TYPE_PROPERTY_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_RESOURCE_SUPER_TYPE;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_TYPE_IN_CREATE;
import static org.forgerock.opendj.rest2ldap.Utils.newBadRequestException;
import static org.forgerock.util.Utils.joinAsString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.api.commons.CommonsApi;
import org.forgerock.api.enums.CreateMode;
import org.forgerock.api.enums.QueryType;
import org.forgerock.api.models.ApiDescription;
import org.forgerock.api.models.ApiError;
import org.forgerock.api.models.Create;
import org.forgerock.api.models.Definitions;
import org.forgerock.api.models.Delete;
import org.forgerock.api.models.Errors;
import org.forgerock.api.models.Items;
import org.forgerock.api.models.Parameter;
import org.forgerock.api.models.Patch;
import org.forgerock.api.models.Paths;
import org.forgerock.api.models.Query;
import org.forgerock.api.models.Read;
import org.forgerock.api.models.Reference;
import org.forgerock.api.models.Schema;
import org.forgerock.api.models.Services;
import org.forgerock.api.models.Update;
import org.forgerock.http.ApiProducer;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.util.i18n.LocalizableString;

/**
 * Defines the characteristics of a resource, including its properties, inheritance, and sub-resources.
 */
public final class Resource {
    // errors
    private static final String ERROR_ADMIN_LIMIT_EXCEEDED = "#/errors/adminLimitExceeded";
    private static final String ERROR_READ_FOUND_MULTIPLE_ENTRIES = "#/errors/readFoundMultipleEntries";
    private static final String ERROR_PASSWORD_MODIFY_REQUIRES_HTTPS = "#/errors/passwordModifyRequiresHttps";
    private static final String ERROR_PASSWORD_MODIFY_REQUIRES_AUTHENTICATION = "#/errors/passwordModifyRequiresAuthn";

    private static final String ERROR_BAD_REQUEST = CommonsApi.Errors.BAD_REQUEST.getReference();
    private static final String ERROR_FORBIDDEN = CommonsApi.Errors.FORBIDDEN.getReference();
    private static final String ERROR_INTERNAL_SERVER_ERROR = CommonsApi.Errors.INTERNAL_SERVER_ERROR.getReference();
    private static final String ERROR_NOT_FOUND = CommonsApi.Errors.NOT_FOUND.getReference();
    private static final String ERROR_REQUEST_ENTITY_TOO_LARGE =
        CommonsApi.Errors.REQUEST_ENTITY_TOO_LARGE.getReference();
    private static final String ERROR_REQUEST_TIMEOUT = CommonsApi.Errors.REQUEST_TIMEOUT.getReference();
    private static final String ERROR_UNAUTHORIZED = CommonsApi.Errors.UNAUTHORIZED.getReference();
    private static final String ERROR_UNAVAILABLE = CommonsApi.Errors.UNAVAILABLE.getReference();
    private static final String ERROR_VERSION_MISMATCH = CommonsApi.Errors.VERSION_MISMATCH.getReference();

    /** All fields are queryable, but the directory server may reject some requests (unindexed?). */
    private static final String ALL_FIELDS = "*";


    /** The resource ID. */
    private final String id;
    /** {@code true} if only sub-types of this resource can be created. */
    private boolean isAbstract;
    /** The ID of the super-type of this resource, may be {@code null}. */
    private String superTypeId;
    /** The LDAP object classes associated with this resource. */
    private final Attribute objectClasses = new LinkedAttribute("objectClass");
    /** The possibly empty set of sub-resources. */
    private final Set<SubResource> subResources = new LinkedHashSet<>();
    /** The set of property mappers associated with this resource, excluding inherited properties. */
    private final Map<String, PropertyMapper> declaredProperties = new LinkedHashMap<>();
    /** The set of property mappers associated with this resource, including inherited properties. */
    private final Map<String, PropertyMapper> allProperties = new LinkedHashMap<>();
    /**
     * A JSON pointer to the primitive JSON property that will be used to convey type information. May be {@code
     * null} if the type property is defined in a super type or if this resource does not have any sub-types.
     */
    private JsonPointer resourceTypeProperty;
    /** Set to {@code true} once this Resource has been built. */
    private boolean isBuilt = false;
    /** The resolved super-type. */
    private Resource superType;
    /** The resolved sub-resources (only immediate children). */
    private final Set<Resource> subTypes = new LinkedHashSet<>();
    /** The property mapper which will map all properties for this resource including inherited properties. */
    private final ObjectPropertyMapper propertyMapper = new ObjectPropertyMapper();
    /** Routes requests to sub-resources. */
    private final Router subResourceRouter = new Router();
    private volatile Boolean hasSubTypesWithSubResources = null;
    /** The set of actions supported by this resource and its sub-types. */
    private final Set<Action> supportedActions = new HashSet<>();
    private LocalizableMessage description;

    Resource(final String id) {
        this.id = id;
    }

    /**
     * Sets the description of this resource.
     *
     * @param description
     *          the description of this resource
     */
    public void description(LocalizableMessage description) {
        this.description = description;
    }

    /**
     * Returns the resource ID of this resource.
     *
     * @return The resource ID of this resource.
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Returns {@code true} if the provided parameter is a {@code Resource} having the same resource ID as this
     * resource.
     *
     * @param o
     *         The object to compare.
     * @return {@code true} if the provided parameter is a {@code Resource} having the same resource ID as this
     * resource.
     */
    @Override
    public boolean equals(final Object o) {
        return this == o || (o instanceof Resource && id.equals(((Resource) o).id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Specifies the resource ID of the resource which is a super-type of this resource. This resource will inherit
     * the properties and sub-resources of the super-type, and may optionally override them.
     *
     * @param resourceId
     *         The resource ID of the resource which is a super-type of this resource, or {@code null} if there is no
     *         super-type.
     * @return A reference to this object.
     */
    public Resource superType(final String resourceId) {
        this.superTypeId = resourceId;
        return this;
    }

    /**
     * Specifies whether this resource is an abstract type and therefore cannot be created. Only non-abstract
     * sub-types can be created.
     *
     * @param isAbstract
     *         {@code true} if this resource is abstract.
     * @return A reference to this object.
     */
    public Resource isAbstract(final boolean isAbstract) {
        this.isAbstract = isAbstract;
        return this;
    }

    /**
     * Specifies a mapping for a property contained in this JSON resource. Properties are inherited and sub-types may
     * override them. Properties are optional: a resource that does not have any properties cannot be created, read,
     * or modified, and may only be used for accessing sub-resources. These resources usually represent API
     * "endpoints".
     *
     * @param name
     *         The name of the JSON property to be mapped.
     * @param mapper
     *         The property mapper responsible for mapping the JSON property to LDAP attribute(s).
     * @return A reference to this object.
     */
    public Resource property(final String name, final PropertyMapper mapper) {
        declaredProperties.put(name, mapper);
        return this;
    }

    /**
     * Specifies whether all LDAP user attributes should be mapped by default using the default schema based mapping
     * rules. Individual attributes can be excluded using {@link #excludedDefaultUserAttributes} in order to prevent
     * attributes with explicit mappings being mapped twice.
     *
     * @param include {@code true} if all LDAP user attributes be mapped by default.
     * @return A reference to this object.
     */
    public Resource includeAllUserAttributesByDefault(final boolean include) {
        propertyMapper.includeAllUserAttributesByDefault(include);
        return this;
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this object.
     */
    public Resource excludedDefaultUserAttributes(final String... attributeNames) {
        return excludedDefaultUserAttributes(Arrays.asList(attributeNames));
    }

    /**
     * Specifies zero or more user attributes which will be excluded from the default user attribute mappings when
     * enabled using {@link #includeAllUserAttributesByDefault}. Attributes which have explicit mappings should be
     * excluded in order to prevent duplication.
     *
     * @param attributeNames The list of attributes to be excluded.
     * @return A reference to this object.
     */
    public Resource excludedDefaultUserAttributes(final Collection<String> attributeNames) {
        propertyMapper.excludedDefaultUserAttributes(attributeNames);
        return this;
    }

    /**
     * Specifies the name of the JSON property which contains the resource's type, whose value is the
     * resource ID. The resource type property is inherited by sub-types and must be available to any resources
     * referenced from {@link SubResource sub-resources}.
     *
     * @param resourceTypeProperty
     *         The name of the JSON property which contains the resource's type, or {@code null} if this resource does
     *         not have a resource type property or if it should be inherited from a super-type.
     * @return A reference to this object.
     */
    public Resource resourceTypeProperty(final JsonPointer resourceTypeProperty) {
        this.resourceTypeProperty = resourceTypeProperty;
        return this;
    }

    /**
     * Specifies an LDAP object class which is to be associated with this resource. Multiple object classes may be
     * specified. The object classes are used for determining the type of resource being accessed during all requests
     * other than create. Object classes are inherited by sub-types and must be defined for any resources that are
     * non-abstract and which can be created.
     *
     * @param objectClass
     *         An LDAP object class associated with this resource's LDAP representation.
     * @return A reference to this object.
     */
    public Resource objectClass(final String objectClass) {
        this.objectClasses.add(objectClass);
        return this;
    }

    /**
     * Specifies LDAP object classes which are to be associated with this resource. Multiple object classes may be
     * specified. The object classes are used for determining the type of resource being accessed during all requests
     * other than create. Object classes are inherited by sub-types and must be defined for any resources that are
     * non-abstract and which can be created.
     *
     * @param objectClasses
     *         The LDAP object classes associated with this resource's LDAP representation.
     * @return A reference to this object.
     */
    public Resource objectClasses(final String... objectClasses) {
        this.objectClasses.add((Object[]) objectClasses);
        return this;
    }

    /**
     * Registers an action which should be supported by this resource. By default, no actions are supported.
     *
     * @param action
     *         The action supported by this resource.
     * @return A reference to this object.
     */
    public Resource supportedAction(final Action action) {
        this.supportedActions.add(action);
        return this;
    }

    /**
     * Registers zero or more actions which should be supported by this resource. By default, no actions are supported.
     *
     * @param actions
     *         The actions supported by this resource.
     * @return A reference to this object.
     */
    public Resource supportedActions(final Action... actions) {
        this.supportedActions.addAll(Arrays.asList(actions));
        return this;
    }

    /**
     * Specifies a parent-child relationship with another resource. Sub-resources are inherited by sub-types and may
     * be overridden.
     *
     * @param subResource
     *         The sub-resource definition.
     * @return A reference to this object.
     */
    public Resource subResource(final SubResource subResource) {
        this.subResources.add(subResource);
        return this;
    }

    /**
     * Specifies a parent-child relationship with zero or more resources. Sub-resources are inherited by sub-types and
     * may be overridden.
     *
     * @param subResources
     *         The sub-resource definitions.
     * @return A reference to this object.
     */
    public Resource subResources(final SubResource... subResources) {
        this.subResources.addAll(asList(subResources));
        return this;
    }

    boolean hasSupportedAction(final Action action) {
        return supportedActions.contains(action);
    }

    boolean hasSubTypes() {
        return !subTypes.isEmpty();
    }

    boolean mayHaveSubResources() {
        return !subResources.isEmpty() || hasSubTypesWithSubResources();
    }

    boolean hasSubTypesWithSubResources() {
        if (hasSubTypesWithSubResources == null) {
            for (final Resource subType : subTypes) {
                if (!subType.subResources.isEmpty() || subType.hasSubTypesWithSubResources()) {
                    hasSubTypesWithSubResources = true;
                    return true;
                }
            }
            hasSubTypesWithSubResources = false;
        }
        return hasSubTypesWithSubResources;
    }

    Set<Resource> getSubTypes() {
        return subTypes;
    }

    Resource resolveSubTypeFromJson(final JsonValue content) throws ResourceException {
        if (!hasSubTypes()) {
            // The resource type is implied because this resource does not have sub-types. In particular, resources
            // are not required to have type information if they don't have sub-types.
            return this;
        }
        final JsonValue jsonType = content.get(resourceTypeProperty);
        if (jsonType == null || !jsonType.isString()) {
            throw newBadRequestException(ERR_MISSING_TYPE_PROPERTY_IN_CREATE.get(resourceTypeProperty));
        }
        final String type = jsonType.asString();
        final Resource subType = resolveSubTypeFromString(type);
        if (subType == null) {
            throw newBadRequestException(ERR_UNRECOGNIZED_TYPE_IN_CREATE.get(type, getAllowedResourceTypes()));
        }
        if (subType.isAbstract) {
            throw newBadRequestException(ERR_ABSTRACT_TYPE_IN_CREATE.get(type, getAllowedResourceTypes()));
        }
        return subType;
    }

    private String getAllowedResourceTypes() {
        final List<String> allowedTypes = new ArrayList<>();
        getAllowedResourceTypes(allowedTypes);
        return joinAsString(", ", allowedTypes);
    }

    private void getAllowedResourceTypes(final List<String> allowedTypes) {
        if (!isAbstract) {
            allowedTypes.add(id);
        }
        for (final Resource subType : subTypes) {
            subType.getAllowedResourceTypes(allowedTypes);
        }
    }

    Resource resolveSubTypeFromString(final String type) {
        if (id.equalsIgnoreCase(type)) {
            return this;
        }
        for (final Resource subType : subTypes) {
            final Resource resolvedSubType = subType.resolveSubTypeFromString(type);
            if (resolvedSubType != null) {
                return resolvedSubType;
            }
        }
        return null;
    }

    Resource resolveSubTypeFromObjectClasses(final Entry entry) {
        if (!hasSubTypes()) {
            // This resource does not have sub-types.
            return this;
        }
        final Attribute objectClassesFromEntry = entry.getAttribute("objectClass");
        final Resource subType = resolveSubTypeFromObjectClasses(objectClassesFromEntry);
        if (subType == null) {
            // Best effort.
            return this;
        }
        return subType;
    }

    private Resource resolveSubTypeFromObjectClasses(final Attribute objectClassesFromEntry) {
        if (!objectClassesFromEntry.containsAll(objectClasses)) {
            return null;
        }
        // This resource is a potential match, but sub-types may be better.
        for (final Resource subType : subTypes) {
            final Resource resolvedSubType = subType.resolveSubTypeFromObjectClasses(objectClassesFromEntry);
            if (resolvedSubType != null) {
                return resolvedSubType;
            }
        }
        return this;
    }

    Attribute getObjectClassAttribute() {
        return objectClasses;
    }

    RequestHandler getSubResourceRouter() {
        return subResourceRouter;
    }

    String getResourceId() {
        return id;
    }

    /**
     * Gets a unique name for the configuration of this resource in CREST.
     *
     * The name is the combination of the resource type and the writability of the resource. For
     * example, {@code frapi:opendj:rest2ldap:group:1.0:read-write} or
     * {@code frapi:opendj:rest2ldap:user:1.0:read-only}. Multiple resources can share the same
     * service description if they manipulate the same resource type and have the same writability.
     *
     * @param  isReadOnly
     *         Whether or not this resource is read-only.
     *
     * @return The unique service ID for this resource, given the specified writability.
     */
    String getServiceId(boolean isReadOnly) {
        StringBuilder serviceId = new StringBuilder(this.getResourceId());

        if (isReadOnly) {
            serviceId.append(":read-only");
        } else {
            serviceId.append(":read-write");
        }

        return serviceId.toString();
    }

    void build(final Rest2Ldap rest2Ldap) {
        // Prevent re-entrant calls.
        if (isBuilt) {
            return;
        }
        isBuilt = true;

        if (superTypeId != null) {
            superType = rest2Ldap.getResource(superTypeId);
            if (superType == null) {
                throw new LocalizedIllegalArgumentException(ERR_UNRECOGNIZED_RESOURCE_SUPER_TYPE.get(id, superTypeId));
            }
            // Inherit content from super-type.
            superType.build(rest2Ldap);
            superType.subTypes.add(this);
            if (resourceTypeProperty == null) {
                resourceTypeProperty = superType.resourceTypeProperty;
            }
            objectClasses.addAll(superType.objectClasses);
            subResourceRouter.addAllRoutes(superType.subResourceRouter);
            allProperties.putAll(superType.allProperties);
        }
        allProperties.putAll(declaredProperties);
        for (final Map.Entry<String, PropertyMapper> property : allProperties.entrySet()) {
            propertyMapper.property(property.getKey(), property.getValue());
        }
        for (final SubResource subResource : subResources) {
            subResource.build(rest2Ldap, id);
            subResource.addRoutes(subResourceRouter);
        }
    }

    PropertyMapper getPropertyMapper() {
        return propertyMapper;
    }

    /**
     * Returns the api description that describes a single instance resource.
     *
     * @param isReadOnly
     *          whether the associated resource is read only
     * @return a new api description that describes a single instance resource.
     */
    ApiDescription instanceApi(boolean isReadOnly) {
        if (allProperties.isEmpty() && superType == null && subTypes.isEmpty()) {
            // It is not used in the api description
            // so do not generate anything for this resource
            return null;
        }

        org.forgerock.api.models.Resource.Builder resource = org.forgerock.api.models.Resource.
            resource()
            .title(this.getServiceId(isReadOnly))
            .description(toLS(description))
            .resourceSchema(schemaRef("#/definitions/" + id))
            .mvccSupported(isMvccSupported());

        resource.read(readOperation());
        if (!isReadOnly) {
            resource.update(updateOperation());
            resource.patch(patchOperation());
            for (Action action : supportedActions) {
                resource.action(actions(action));
            }
        }

        return ApiDescription.apiDescription()
                      .id("unused").version("unused")
                      .definitions(definitions())
                      .services(services(resource, isReadOnly))
                      .paths(paths(isReadOnly))
                      .errors(errors())
                      .build();
    }

    /**
     * Returns the api description that describes a collection resource.
     *
     * @param isReadOnly
     *          whether the associated resource is read only
     * @return a new api description that describes a collection resource.
     */
    ApiDescription collectionApi(boolean isReadOnly) {
        org.forgerock.api.models.Resource.Builder resource = org.forgerock.api.models.Resource.
            resource()
            .title(this.getServiceId(isReadOnly))
            .description(toLS(description))
            .resourceSchema(schemaRef("#/definitions/" + id))
            .mvccSupported(isMvccSupported());

        resource.items(buildItems(isReadOnly));

        if (!isReadOnly) {
            resource.create(createOperation(CreateMode.ID_FROM_SERVER));
        }

        resource.query(Query.query()
                           .stability(EVOLVING)
                           .type(QueryType.FILTER)
                           .queryableFields(ALL_FIELDS)
                           .pagingModes(COOKIE, OFFSET)
                           .countPolicies(NONE)
                           .error(errorRef(ERROR_BAD_REQUEST))
                           .error(errorRef(ERROR_UNAUTHORIZED))
                           .error(errorRef(ERROR_FORBIDDEN))
                           .error(errorRef(ERROR_REQUEST_TIMEOUT))
                           .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                           .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                           .error(errorRef(ERROR_UNAVAILABLE))
                           .build());

        return ApiDescription.apiDescription()
                             .id("unused").version("unused")
                             .definitions(definitions())
                             .services(services(resource, isReadOnly))
                             .paths(paths(isReadOnly))
                             .errors(errors())
                             .build();
    }

    private Services services(org.forgerock.api.models.Resource.Builder resource,
                              boolean isReadOnly) {
        final String serviceId = this.getServiceId(isReadOnly);

        return Services.services()
                       .put(serviceId, resource.build())
                       .build();
    }

    private Paths paths(boolean isReadOnly) {
        final String serviceId = this.getServiceId(isReadOnly);
        final org.forgerock.api.models.Resource resource = resourceRef("#/services/" + serviceId);

        return Paths.paths()
                     // do not put anything in the path to avoid unfortunate string concatenation
                     // also use UNVERSIONED and rely on the router to stamp the version
                     .put("", versionedPath().put(UNVERSIONED, resource).build())
                     .build();
    }

    private Definitions definitions() {
        final Definitions.Builder definitions = Definitions.definitions();
        for (Resource res : collectTypeHierarchy(this)) {
            definitions.put(res.id, res.buildJsonSchema());
        }
        return definitions.build();
    }

    private static Iterable<Resource> collectTypeHierarchy(Resource currentType) {
        final List<Resource> typeHierarchy = new ArrayList<>();

        Resource ancestorType = currentType;
        while (ancestorType.superType != null) {
            ancestorType = ancestorType.superType;
            typeHierarchy.add(ancestorType);
        }

        typeHierarchy.add(currentType);

        addSubTypes(typeHierarchy, currentType);
        return typeHierarchy;
    }

    private static void addSubTypes(final List<Resource> typeHierarchy, Resource res) {
        for (Resource subType : res.subTypes) {
            typeHierarchy.add(subType);
            addSubTypes(typeHierarchy, subType);
        }
    }

    private LocalizableString toLS(LocalizableMessage msg) {
        if (msg != null) {
            // FIXME this code does cannot work today because LocalizableMessage.getKey() does not exist
            //if (msg.resourceName() != null) {
            //    return new LocalizableString("i18n:" + msg.resourceName() + "#" + msg.getKey());
            //}
            return new LocalizableString(msg.toString());
        }
        return null;
    }

    /**
     * Returns the api description that describes a resource with sub resources.
     *
     * @param producer
     *          the api producer
     * @return a new api description that describes a resource with sub resources.
     */
    ApiDescription subResourcesApi(ApiProducer<ApiDescription> producer) {
        return subResourceRouter.api(producer);
    }

    private boolean isMvccSupported() {
        return allProperties.containsKey("_rev");
    }

    private Items buildItems(boolean isReadOnly) {
        final Items.Builder builder = Items.items();
        builder.pathParameter(Parameter
                              .parameter()
                              .name("id")
                              .type("string")
                              .source(PATH)
                              .required(true)
                              .build())
               .read(readOperation());
        if (!isReadOnly) {
            builder.create(createOperation(CreateMode.ID_FROM_CLIENT));
            builder.update(updateOperation());
            builder.delete(deleteOperation());
            builder.patch(patchOperation());
            for (Action action : supportedActions) {
                builder.action(actions(action));
            }
        }
        return builder.build();
    }

    private org.forgerock.api.models.Action actions(Action action) {
        switch (action) {
        case MODIFY_PASSWORD:
            return modifyPasswordAction();
        case RESET_PASSWORD:
            return resetPasswordAction();
        default:
            throw new RuntimeException("Not implemented for action " + action);
        }
    }

    private static Create createOperation(CreateMode createMode) {
        return Create.create()
                     .stability(EVOLVING)
                     .mode(createMode)
                     .error(errorRef(ERROR_BAD_REQUEST))
                     .error(errorRef(ERROR_UNAUTHORIZED))
                     .error(errorRef(ERROR_FORBIDDEN))
                     .error(errorRef(ERROR_NOT_FOUND))
                     .error(errorRef(ERROR_REQUEST_TIMEOUT))
                     .error(errorRef(ERROR_VERSION_MISMATCH))
                     .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
                     .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                     .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                     .error(errorRef(ERROR_UNAVAILABLE))
                     .build();
    }

    private static Delete deleteOperation() {
        return Delete.delete()
                     .stability(EVOLVING)
                     .error(errorRef(ERROR_BAD_REQUEST))
                     .error(errorRef(ERROR_UNAUTHORIZED))
                     .error(errorRef(ERROR_FORBIDDEN))
                     .error(errorRef(ERROR_NOT_FOUND))
                     .error(errorRef(ERROR_REQUEST_TIMEOUT))
                     .error(errorRef(ERROR_VERSION_MISMATCH))
                     .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
                     .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
                     .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                     .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                     .error(errorRef(ERROR_UNAVAILABLE))
                     .build();
    }

    private static Patch patchOperation() {
        return Patch.patch()
                    .stability(EVOLVING)
                    .operations(ADD, REMOVE, REPLACE, INCREMENT)
                    .error(errorRef(ERROR_BAD_REQUEST))
                    .error(errorRef(ERROR_UNAUTHORIZED))
                    .error(errorRef(ERROR_FORBIDDEN))
                    .error(errorRef(ERROR_NOT_FOUND))
                    .error(errorRef(ERROR_REQUEST_TIMEOUT))
                    .error(errorRef(ERROR_VERSION_MISMATCH))
                    .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
                    .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
                    .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                    .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                    .error(errorRef(ERROR_UNAVAILABLE))
                    .build();
    }

    private static Read readOperation() {
        return Read.read()
                   .stability(EVOLVING)
                   .error(errorRef(ERROR_BAD_REQUEST))
                   .error(errorRef(ERROR_UNAUTHORIZED))
                   .error(errorRef(ERROR_FORBIDDEN))
                   .error(errorRef(ERROR_NOT_FOUND))
                   .error(errorRef(ERROR_REQUEST_TIMEOUT))
                   .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
                   .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                   .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                   .error(errorRef(ERROR_UNAVAILABLE))
                   .build();
    }

    private static Update updateOperation() {
        return Update.update()
                     .stability(EVOLVING)
                     .error(errorRef(ERROR_BAD_REQUEST))
                     .error(errorRef(ERROR_UNAUTHORIZED))
                     .error(errorRef(ERROR_FORBIDDEN))
                     .error(errorRef(ERROR_NOT_FOUND))
                     .error(errorRef(ERROR_REQUEST_TIMEOUT))
                     .error(errorRef(ERROR_VERSION_MISMATCH))
                     .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
                     .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
                     .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
                     .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
                     .error(errorRef(ERROR_UNAVAILABLE))
                     .build();
    }

    private static org.forgerock.api.models.Action modifyPasswordAction() {
        return org.forgerock.api.models.Action.action()
               .stability(EVOLVING)
               .name("modifyPassword")
               .request(passwordModifyRequest())
               .description("Modify a user password. This action requires HTTPS.")
               .error(errorRef(ERROR_BAD_REQUEST))
               .error(errorRef(ERROR_UNAUTHORIZED))
               .error(errorRef(ERROR_PASSWORD_MODIFY_REQUIRES_HTTPS))
               .error(errorRef(ERROR_PASSWORD_MODIFY_REQUIRES_AUTHENTICATION))
               .error(errorRef(ERROR_FORBIDDEN))
               .error(errorRef(ERROR_NOT_FOUND))
               .error(errorRef(ERROR_REQUEST_TIMEOUT))
               .error(errorRef(ERROR_VERSION_MISMATCH))
               .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
               .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
               .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
               .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
               .error(errorRef(ERROR_UNAVAILABLE))
               .build();
    }

    private static org.forgerock.api.models.Schema passwordModifyRequest() {
        final JsonValue jsonSchema = json(object(
            field("type", "object"),
            field("description", "Supply the old password and new password."),
            field("required", array("oldPassword", "newPassword")),
            field("properties", object(
                field("oldPassword", object(
                    field("type", "string"),
                    field("name", "Old Password"),
                    field("description", "Current password as a UTF-8 string."),
                    field("format", "password"))),
                field("newPassword", object(
                    field("type", "string"),
                    field("name", "New Password"),
                    field("description", "New password as a UTF-8 string."),
                    field("format", "password")))))));
        return schema(jsonSchema);
    }

    private static org.forgerock.api.models.Action resetPasswordAction() {
        return org.forgerock.api.models.Action.action()
               .stability(EVOLVING)
               .name("resetPassword")
               .response(resetPasswordResponse())
               .description("Reset a user password to a generated value. This action requires HTTPS.")
               .error(errorRef(ERROR_BAD_REQUEST))
               .error(errorRef(ERROR_UNAUTHORIZED))
               .error(errorRef(ERROR_PASSWORD_MODIFY_REQUIRES_HTTPS))
               .error(errorRef(ERROR_PASSWORD_MODIFY_REQUIRES_AUTHENTICATION))
               .error(errorRef(ERROR_FORBIDDEN))
               .error(errorRef(ERROR_NOT_FOUND))
               .error(errorRef(ERROR_REQUEST_TIMEOUT))
               .error(errorRef(ERROR_VERSION_MISMATCH))
               .error(errorRef(ERROR_REQUEST_ENTITY_TOO_LARGE))
               .error(errorRef(ERROR_READ_FOUND_MULTIPLE_ENTRIES))
               .error(errorRef(ERROR_ADMIN_LIMIT_EXCEEDED))
               .error(errorRef(ERROR_INTERNAL_SERVER_ERROR))
               .error(errorRef(ERROR_UNAVAILABLE))
               .build();
    }

    private static org.forgerock.api.models.Schema resetPasswordResponse() {
        final JsonValue jsonSchema = json(object(
            field("type", "object"),
            field("properties", object(
                field("generatedPassword", object(
                    field("type", "string"),
                    field("description", "Generated password to communicate to the user.")))))));
        return schema(jsonSchema);
    }

    private Schema buildJsonSchema() {
        final List<String> requiredFields = new ArrayList<>();
        JsonValue properties = json(JsonValue.object());
        for (Map.Entry<String, PropertyMapper> prop : declaredProperties.entrySet()) {
            final String propertyName = prop.getKey();
            final PropertyMapper mapper = prop.getValue();
            if (mapper.isRequired()) {
                requiredFields.add(propertyName);
            }
            final JsonValue jsonSchema = mapper.toJsonSchema();
            if (jsonSchema != null) {
                properties.put(propertyName, jsonSchema.getObject());
            }
        }

        final JsonValue jsonSchema = json(object(field("type", "object")));
        final String discriminator = getDiscriminator();
        if (discriminator != null) {
            jsonSchema.put("discriminator", discriminator);
        }
        if (!requiredFields.isEmpty()) {
            jsonSchema.put("required", requiredFields);
        }
        if (properties.size() > 0) {
            jsonSchema.put("properties", properties.getObject());
        }

        if (superType != null) {
            return schema(json(object(
                field("allOf", array(
                    object(field("$ref", "#/definitions/" + superType.id)),
                    jsonSchema.getObject())))));
        }
        return schema(jsonSchema);
    }

    private String getDiscriminator() {
        if (resourceTypeProperty != null) {
            // Subtypes inherit the resourceTypeProperty from their parent.
            // The discriminator must only be output for the type that defined it.
            final String propertyName = resourceTypeProperty.leaf();
            return declaredProperties.containsKey(propertyName) ? propertyName : null;
        }
        return null;
    }

    private Errors errors() {
        return Errors
            .errors()
            .put("passwordModifyRequiresHttps",
                error(FORBIDDEN, "Password modify requires a secure connection."))
            .put("passwordModifyRequiresAuthn",
                error(FORBIDDEN, "Password modify requires user to be authenticated."))
            .put("readFoundMultipleEntries",
                error(INTERNAL_ERROR, "Multiple entries where found when trying to read a single entry."))
            .put("adminLimitExceeded",
                error(INTERNAL_ERROR, "The request exceeded an administrative limit."))
            .build();
    }

    static ApiError error(int code, String description) {
        return ApiError.apiError().code(code).description(description).build();
    }

    static ApiError error(int code, LocalizableString description) {
        return ApiError.apiError().code(code).description(description).build();
    }

    static ApiError errorRef(String referenceValue) {
        return ApiError.apiError().reference(ref(referenceValue)).build();
    }

    static org.forgerock.api.models.Resource resourceRef(String referenceValue) {
        return org.forgerock.api.models.Resource.resource().reference(ref(referenceValue)).build();
    }

    static org.forgerock.api.models.Schema schemaRef(String referenceValue) {
        return Schema.schema().reference(ref(referenceValue)).build();
    }

    private static org.forgerock.api.models.Schema schema(JsonValue jsonSchema) {
        return Schema.schema().schema(jsonSchema).build();
    }

    static Reference ref(String referenceValue) {
        return Reference.reference().value(referenceValue).build();
    }
}
