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
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.ldap.ResultCode.ADMIN_LIMIT_EXCEEDED;
import static org.forgerock.opendj.ldap.ResultCode.ENTRY_ALREADY_EXISTS;
import static org.forgerock.opendj.ldap.ResultCode.SIZE_LIMIT_EXCEEDED;
import static org.forgerock.opendj.rest2ldap.ReadOnUpdatePolicy.CONTROLS;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.PermanentException;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.RetryableException;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.ServiceUnavailableException;
import org.forgerock.opendj.ldap.AssertionFailureException;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AuthenticationException;
import org.forgerock.opendj.ldap.AuthorizationException;
import org.forgerock.opendj.ldap.ConnectionException;
import org.forgerock.opendj.ldap.ConstraintViolationException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.MultipleEntriesFoundException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.TimeoutResultException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;

/**
 * Provides methods for constructing Rest2Ldap protocol gateways. Applications construct a new Rest2Ldap
 * instance by calling {@link #rest2Ldap} passing in a list of {@link Resource resources} which together define
 * the data model being exposed by the gateway. Call {@link #newRequestHandlerFor(String)} in order to obtain
 * a request handler for a specific resource. The methods in this class can be categorized as follows:
 * <p/>
 * Creating Rest2Ldap gateways:
 * <ul>
 * <li>{@link #rest2Ldap} - creates a gateway for a given set of resources</li>
 * <li>{@link #newRequestHandlerFor} - obtains a request handler for the specified endpoint resource.</li>
 * </ul>
 * <p/>
 * Defining resource types, e.g. users, groups, devices, etc:
 * <ul>
 * <li>{@link #resource} - creates a resource having a fluent API for defining additional characteristics
 * such as the resource's inheritance, sub-resources, and properties</li>
 * </ul>
 * <p/>
 * Defining a resource's sub-resources. A sub-resource is a resource which is subordinate to another resource. Or, to
 * put it another way, sub-resources define parent child relationships where the life-cycle of a child resource is
 * constrained by the life-cycle of the parent: deleting the parent implies that all children are deleted as well. An
 * example of a sub-resource is a subscriber having one or more devices:
 * <ul>
 * <li>{@link #collectionOf} - creates a one-to-many relationship. Collections support creation, deletion,
 * and querying of child resources</li>
 * <li>{@link #singletonOf} - creates a one-to-one relationship. Singletons cannot be created or destroyed,
 * although they may be modified if they have properties which are modifiable. Singletons are usually only used as
 * top-level entry points into REST APIs.
 * </li>
 * </ul>
 * <p/>
 * Defining a resource's properties:
 * <ul>
 * <li>{@link #resourceType} - defines a property whose JSON value will be the name of the resource, e.g. "user"</li>
 * <li>{@link #simple} - defines a property which maps a JSON value to a single LDAP attribute</li>
 * <li>{@link #object} - defines a property which is a JSON object having zero or more nested properties</li>
 * <li>{@link #reference} - defines a property whose JSON value is a reference to another resource. Use these for
 * mapping LDAP attributes which contain the DN of another LDAP entry exposed by Rest2Ldap. For example, a user's
 * "manager" attribute or the members of a group.</li>
 * </ul>
 */
public final class Rest2Ldap {
    /**
     * Specifies the LDAP decoding options which should be used when decoding LDAP DNs, attribute types, and controls.
     * By default Rest2Ldap will use a set of options of will always use the default schema.
     */
    public static final Option<DecodeOptions> DECODE_OPTIONS = Option.withDefault(new DecodeOptions());
    /**
     * Specifies whether Rest2Ldap should support multi-version concurrency control (MVCC) through the use of an MVCC
     * LDAP {@link #MVCC_ATTRIBUTE attribute} such as "etag". By default Rest2Ldap will use MVCC.
     */
    public static final Option<Boolean> USE_MVCC = Option.withDefault(true);
    /**
     * Specifies the name of the LDAP attribute which should be used for multi-version concurrency control (MVCC) if
     * {@link #USE_MVCC enabled}. By default Rest2Ldap will use the "etag" operational attribute.
     */
    public static final Option<String> MVCC_ATTRIBUTE = Option.withDefault("etag");
    /**
     * Specifies the policy which should be used in order to read an entry before it is deleted, or after it is added or
     * modified. By default Rest2Ldap will use the {@link ReadOnUpdatePolicy#CONTROLS controls} read on update policy.
     */
    public static final Option<ReadOnUpdatePolicy> READ_ON_UPDATE_POLICY = Option.withDefault(CONTROLS);
    /**
     * Specifies whether Rest2Ldap should perform LDAP modify operations using the LDAP permissive modify
     * control. By default Rest2Ldap will use the permissive modify control and use of the control is strongly
     * recommended.
     */
    public static final Option<Boolean> USE_PERMISSIVE_MODIFY = Option.withDefault(true);
    /**
     * Specifies whether Rest2Ldap should perform LDAP delete operations using the LDAP subtree delete control. By
     * default Rest2Ldap will use the subtree delete control and use of the control is strongly recommended.
     */
    public static final Option<Boolean> USE_SUBTREE_DELETE = Option.withDefault(true);

    /**
     * Creates a new {@link Rest2Ldap} instance using the provided options and {@link Resource resources}.
     * Applications should call {@link #newRequestHandlerFor(String)} to obtain a request handler for a specific
     * resource.
     * <p>
     * The supported options are defined in this class.
     *
     * @param options The configuration options for interactions with the backend LDAP server. The set of available
     *                options are provided in this class.
     * @param resources The list of resources.
     * @return A new Rest2Ldap instance from which REST request handlers can be obtained.
     */
    public static Rest2Ldap rest2Ldap(final Options options, final Collection<Resource> resources) {
        return new Rest2Ldap(options, resources);
    }

    /**
     * Creates a new {@link Rest2Ldap} instance using the provided options and {@link Resource resources}.
     * Applications should call {@link #newRequestHandlerFor(String)} to obtain a request handler for a specific
     * resource.
     * <p>
     * The supported options are defined in this class.
     *
     * @param options The configuration options for interactions with the backend LDAP server. The set of available
     *                options are provided in this class.
     * @param resources The list of resources.
     * @return A new Rest2Ldap instance from which REST request handlers can be obtained.
     */
    public static Rest2Ldap rest2Ldap(final Options options, final Resource... resources) {
        return rest2Ldap(options, Arrays.asList(resources));
    }

    /**
     * Creates a new {@link Resource resource} definition with the provided resource ID.
     *
     * @param resourceId
     *         The resource ID.
     * @return A new resource definition with the provided resource ID.
     */
    public static Resource resource(final String resourceId) {
        return new Resource(resourceId);
    }

    /**
     * Creates a new {@link SubResourceCollection collection} sub-resource definition whose members will be resources
     * having the provided resource ID or its sub-types.
     *
     * @param resourceId
     *         The type of resource contained in the sub-resource collection.
     * @return A new sub-resource definition with the provided resource ID.
     */
    public static SubResourceCollection collectionOf(final String resourceId) {
        return new SubResourceCollection(resourceId);
    }

    /**
     * Creates a new {@link SubResourceSingleton singleton} sub-resource definition which will reference a single
     * resource having the specified resource ID.
     *
     * @param resourceId
     *         The type of resource referenced by the sub-resource singleton.
     * @return A new sub-resource definition with the provided resource ID.
     */
    public static SubResourceSingleton singletonOf(final String resourceId) {
        return new SubResourceSingleton(resourceId);
    }

    /**
     * Returns a property mapper which maps a JSON property containing the resource type to its associated LDAP
     * object classes.
     *
     * @return The property mapper.
     */
    public static PropertyMapper resourceType() {
        return ResourceTypePropertyMapper.INSTANCE;
    }

    /**
     * Returns a property mapper which maps a single JSON attribute to a JSON constant.
     *
     * @param value
     *         The constant JSON value (a Boolean, Number, String, Map, or List).
     * @return The property mapper.
     */
    public static PropertyMapper constant(final Object value) {
        return new JsonConstantPropertyMapper(value);
    }

    /**
     * Returns a property mapper which maps JSON objects to LDAP attributes.
     *
     * @return The property mapper.
     */
    public static ObjectPropertyMapper object() {
        return new ObjectPropertyMapper();
    }

    /**
     * Returns a property mapper which provides a mapping from a JSON value to a single DN valued LDAP attribute.
     *
     * @param attribute
     *         The DN valued LDAP attribute to be mapped.
     * @param baseDN
     *         The search base DN for performing reverse lookups.
     * @param primaryKey
     *         The search primary key LDAP attribute to use for performing reverse lookups.
     * @param mapper
     *         An property mapper which will be used to map LDAP attributes in the referenced entry.
     * @return The property mapper.
     */
    public static ReferencePropertyMapper reference(final AttributeDescription attribute, final DN baseDN,
                                                    final AttributeDescription primaryKey,
                                                    final PropertyMapper mapper) {
        return new ReferencePropertyMapper(Schema.getDefaultSchema(), attribute, baseDN, primaryKey, mapper);
    }

    /**
     * Returns a property mapper which provides a mapping from a JSON value to a single DN valued LDAP attribute.
     *
     * @param attribute
     *         The DN valued LDAP attribute to be mapped.
     * @param baseDN
     *         The search base DN for performing reverse lookups.
     * @param primaryKey
     *         The search primary key LDAP attribute to use for performing reverse lookups.
     * @param mapper
     *         An property mapper which will be used to map LDAP attributes in the referenced entry.
     * @return The property mapper.
     */
    public static ReferencePropertyMapper reference(final String attribute, final String baseDN,
                                                    final String primaryKey, final PropertyMapper mapper) {
        return reference(AttributeDescription.valueOf(attribute),
                         DN.valueOf(baseDN),
                         AttributeDescription.valueOf(primaryKey),
                         mapper);
    }

    /**
     * Returns a property mapper which provides a simple mapping from a JSON value to a single LDAP attribute.
     *
     * @param attribute
     *         The LDAP attribute to be mapped.
     * @return The property mapper.
     */
    public static SimplePropertyMapper simple(final AttributeDescription attribute) {
        return new SimplePropertyMapper(attribute);
    }

    /**
     * Returns a property mapper which provides a simple mapping from a JSON value to a single LDAP attribute.
     *
     * @param attribute
     *         The LDAP attribute to be mapped.
     * @return The property mapper.
     */
    public static SimplePropertyMapper simple(final String attribute) {
        return simple(AttributeDescription.valueOf(attribute));
    }

    /**
     * Adapts a {@code Throwable} to a {@code ResourceException}. If the {@code Throwable} is an LDAP
     * {@link LdapException} then an appropriate {@code ResourceException} is returned, otherwise an {@code
     * InternalServerErrorException} is returned.
     * @param t
     *         The {@code Throwable} to be converted.
     * @return The equivalent resource exception.
     */
    public static ResourceException asResourceException(final Throwable t) {
        try {
            throw t;
        } catch (final ResourceException e) {
            return e;
        } catch (final AssertionFailureException e) {
            return new PreconditionFailedException(e);
        } catch (final ConstraintViolationException e) {
            final ResultCode rc = e.getResult().getResultCode();
            if (rc.equals(ENTRY_ALREADY_EXISTS)) {
                return new PreconditionFailedException(e); // Consistent with MVCC.
            } else {
                return new BadRequestException(e); // Schema violation, etc.
            }
        } catch (final AuthenticationException e) {
            return new PermanentException(401, null, e); // Unauthorized
        } catch (final AuthorizationException e) {
            return new ForbiddenException(e);
        } catch (final ConnectionException e) {
            return new ServiceUnavailableException(e);
        } catch (final EntryNotFoundException e) {
            return new NotFoundException(e);
        } catch (final MultipleEntriesFoundException e) {
            return new InternalServerErrorException(e);
        } catch (final TimeoutResultException e) {
            return new RetryableException(408, null, e); // Request Timeout
        } catch (final LdapException e) {
            final ResultCode rc = e.getResult().getResultCode();
            if (rc.equals(ADMIN_LIMIT_EXCEEDED) || rc.equals(SIZE_LIMIT_EXCEEDED)) {
                return new PermanentException(413, null, e); // Payload Too Large (Request Entity Too Large)
            } else {
                return new InternalServerErrorException(e);
            }
        } catch (final Throwable tmp) {
            return new InternalServerErrorException(t);
        }
    }

    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final Options options;

    private Rest2Ldap(final Options options, final Collection<Resource> resources) {
        this.options = options;
        for (final Resource resource : resources) {
            this.resources.put(resource.getResourceId(), resource);
        }
        // Now build the model.
        for (final Resource resource : resources) {
            resource.build(this);
        }
    }

    /**
     * Returns a {@link RequestHandler} which will handle requests to the named resource and any of its sub-resources.
     *
     * @param resourceId
     *         The resource ID.
     * @return A {@link RequestHandler} which will handle requests to the named resource.
     */
    public RequestHandler newRequestHandlerFor(final String resourceId) {
        Reject.ifTrue(!resources.containsKey(resourceId), "unrecognized resource '" + resourceId + "'");
        final SubResourceSingleton root = singletonOf(resourceId);
        root.build(this, null);
        return root.addRoutes(new Router());
    }

    Options getOptions() {
        return options;
    }

    Resource getResource(final String resourceId) {
        return resources.get(resourceId);
    }
}
