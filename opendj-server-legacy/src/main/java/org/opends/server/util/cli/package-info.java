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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */

/**
 * Provides a high-level framework for implementing command-line tools.
 * <p>
 * The {@code LDAPConnectionArgumentParser} provides an implementation of
 * an utility that can manage the processing of command-line arguments for
 * an application.
 * This class centralizes a significant amount of processing so that it does
 * not need to be repeated in all tools requiring this kind of functionality,
 * as well as helping to ensure that the interaction with program arguments
 * is in compliance with Sun's CLIP specification.
 * <BR><BR>
 * Features offered by this argument parsing implementation include:
 * <BR>
 * <UL>
 *   <LI>
 *     Arguments can be denoted using either a single dash followed by a single
 *     character or two dashes followed by a more verbose multi-character
 *     token.
 *   </LI>
 *   <LI>
 *     The parsing performed on these arguments is very lenient so that it will
 *     likely be compatible with the style preferred by the end user.
 *   </LI>
 *   <LI>
 *     Arguments are declared with or without a value, and the parser can be
 *     used to ensure that a value is provided for arguments that require one.
 *   </LI>
 *   <LI>
 *     Each type of argument is associated with a particular data type, and a
 *     minimal amount of validation can be handled by the argument parser itself
 *     in this case (e.g., if an argument is associated with an integer type,
 *     then non-numeric values will be rejected, and it is also possible to
 *     define an acceptable range of values).
 *   </LI>
 *   <LI>
 *     The argument parser contains a built-in mechanism for ensuring that there
 *     are no conflicts between option names (i.e., it ensures that two
 *     different arguments don't both try to use "-x" to invoke them).
 *   </LI>
 *   <LI>
 *     The argument parser contains a mechanism for allowing "extra" arguments
 *     at the end of the list which are not explicitly associated with
 *     parameters.  For example, in the ldapsearch utility, at least one of
 *     these "extra" arguments would be used for the filter, and if there are
 *     any more of them then they would be used for the list of attributes to
 *     return.
 *   </LI>
 *   <LI>
 *     The argument parser itself can generate usage information in a consistent
 *     manner so that it is not necessary for each command-line application to
 *     explicitly provide this functionality.
 *   </LI>
 * </UL>
 * <BR>
 * A second version of the argument parser is also available which does not
 * include support for trailing arguments but does include support for
 * the use of subcommands.  In this case, you can define a number of subcommands
 * each with their own set of arguments.  This can be used for cases in which
 * one umbrella utility has a number of different capabilities (e.g., the "cvs"
 * command has a number of sub-commands like "checkout" and "commit" and "diff",
 * each of which has its own set of options).
 * <p>
 * The {@code ConsoleApplication} interface can be used as a basis for
 * console based applications. It includes common utility methods for
 * interacting with the console.
 * <p>
 * The {@code MenuBuilder} and associated classes and interfaces can
 * be used to implement text based menu driven applications.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.PRIVATE)
package org.opends.server.util.cli;



