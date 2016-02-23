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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.EntryChangeNotificationResponseControl;
import org.forgerock.opendj.ldap.controls.GetEffectiveRightsRequestControl;
import org.forgerock.opendj.ldap.controls.MatchedValuesRequestControl;
import org.forgerock.opendj.ldap.controls.PersistentSearchChangeType;
import org.forgerock.opendj.ldap.controls.PersistentSearchRequestControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortResponseControl;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewRequestControl;
import org.forgerock.opendj.ldap.controls.VirtualListViewResponseControl;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldif.EntryWriter;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.ldap.controls.AccountUsabilityResponseControl;
import com.forgerock.opendj.util.StaticUtils;

import static com.forgerock.opendj.cli.CliMessages.INFO_DESCRIPTION_SIMPLE_PAGE_SIZE;
import static com.forgerock.opendj.cli.CliMessages.INFO_NUM_ENTRIES_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_SEARCH_DESCRIPTION_SIZE_LIMIT;
import static com.forgerock.opendj.cli.CliMessages.INFO_SEARCH_DESCRIPTION_TIME_LIMIT;
import static com.forgerock.opendj.cli.CliMessages.INFO_SIZE_LIMIT_PLACEHOLDER;
import static com.forgerock.opendj.cli.CliMessages.INFO_TIME_LIMIT_PLACEHOLDER;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.printPasswordPolicyResults;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

/** A tool that can be used to issue Search requests to the Directory Server. */
public final class LDAPSearch extends ConsoleApplication {
    private class LDAPSearchResultHandler implements SearchResultHandler {
        private int entryCount;

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            entryCount++;

            try {
                final EntryChangeNotificationResponseControl control =
                        entry.getControl(EntryChangeNotificationResponseControl.DECODER,
                                new DecodeOptions());
                if (control != null) {
                    println(INFO_LDAPSEARCH_PSEARCH_CHANGE_TYPE.get(control.getChangeType()
                            .toString()));
                    final DN previousDN = control.getPreviousName();
                    if (previousDN != null) {
                        println(INFO_LDAPSEARCH_PSEARCH_PREVIOUS_DN.get(previousDN.toString()));
                    }
                }
            } catch (final DecodeException de) {
                errPrintln(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
            }

            try {
                final AccountUsabilityResponseControl control =
                        entry.getControl(AccountUsabilityResponseControl.DECODER,
                                new DecodeOptions());

                if (control != null) {
                    println(INFO_LDAPSEARCH_ACCTUSABLE_HEADER.get());
                    if (control.isUsable()) {
                        println(INFO_LDAPSEARCH_ACCTUSABLE_IS_USABLE.get());
                        if (control.getSecondsBeforeExpiration() > 0) {
                            final int timeToExp = control.getSecondsBeforeExpiration();
                            final LocalizableMessage timeToExpStr =
                                    secondsToTimeString(timeToExp);

                            println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION
                                    .get(timeToExpStr));
                        }
                    } else {
                        println(INFO_LDAPSEARCH_ACCTUSABLE_NOT_USABLE.get());
                        if (control.isInactive()) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_ACCT_INACTIVE.get());
                        }
                        if (control.isReset()) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_PW_RESET.get());
                        }
                        if (control.isExpired()) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_PW_EXPIRED.get());

                            if (control.getRemainingGraceLogins() > 0) {
                                println(INFO_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE.get(control
                                        .getRemainingGraceLogins()));
                            }
                        }
                        if (control.isLocked()) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_LOCKED.get());
                            if (control.getSecondsBeforeUnlock() > 0) {
                                final int timeToUnlock = control.getSecondsBeforeUnlock();
                                final LocalizableMessage timeToUnlockStr =
                                        secondsToTimeString(timeToUnlock);

                                println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK
                                        .get(timeToUnlockStr));
                            }
                        }
                    }
                }
            } catch (final DecodeException de) {
                errPrintln(ERR_DECODE_CONTROL_FAILURE.get(de.getLocalizedMessage()));
            }

            try {
                ldifWriter.writeEntry(entry);
                ldifWriter.flush();
            } catch (final IOException ioe) {
                // Something is seriously wrong
                throw new RuntimeException(ioe);
            }
            return true;
        }

        @Override
        public boolean handleReference(final SearchResultReference reference) {
            println(LocalizableMessage.raw(reference.toString()));
            return true;
        }
    }

    /**
     * The main method for LDAPSearch tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */

    public static void main(final String[] args) {
        final int retCode = new LDAPSearch().run(args);
        System.exit(filterExitCode(retCode));
    }

    private BooleanArgument verbose;

    private EntryWriter ldifWriter;

    private LDAPSearch() {
        // Nothing to do.
    }

    /**
     * Constructor to allow tests.
     *
     * @param out output stream of console application
     * @param err error stream of console application
     */
    LDAPSearch(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    /** Run ldapsearch with provided command-line arguments. */
    int run(final String[] args) {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPSEARCH_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser =
                new ArgumentParser(LDAPSearch.class.getName(), toolDescription, false, true, 0, 0,
                        "[filter] [attributes ...]");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPSEARCH.get());

        ConnectionFactoryProvider connectionFactoryProvider;
        ConnectionFactory connectionFactory;
        BindRequest bindRequest;

        BooleanArgument countEntries;
        BooleanArgument dontWrap;
        BooleanArgument noop;
        BooleanArgument typesOnly;
        IntegerArgument simplePageSize;
        IntegerArgument timeLimit;
        IntegerArgument version;
        StringArgument baseDN;
        StringArgument controlStr;
        MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
        StringArgument filename;
        StringArgument matchedValuesFilter;
        StringArgument pSearchInfo;
        MultiChoiceArgument<SearchScope> searchScope;
        StringArgument vlvDescriptor;
        StringArgument effectiveRightsUser;
        StringArgument effectiveRightsAttrs;
        StringArgument sortOrder;
        StringArgument proxyAuthzID;
        StringArgument assertionFilter;
        IntegerArgument sizeLimit;
        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);
            final StringArgument propertiesFileArgument =
                propertiesFileArgument();
            argParser.addArgument(propertiesFileArgument);
            argParser.setFilePropertiesArgument(propertiesFileArgument);

            final BooleanArgument noPropertiesFileArgument = noPropertiesFileArgument();
            argParser.addArgument(noPropertiesFileArgument);
            argParser.setNoPropertiesFileArgument(noPropertiesFileArgument);

            baseDN =
                    StringArgument.builder(OPTION_LONG_BASEDN)
                            .shortIdentifier(OPTION_SHORT_BASEDN)
                            .description(INFO_SEARCH_DESCRIPTION_BASEDN.get())
                            .required()
                            .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            searchScope = searchScopeArgument();
            argParser.addArgument(searchScope);

            filename =
                    StringArgument.builder(OPTION_LONG_FILENAME)
                            .shortIdentifier(OPTION_SHORT_FILENAME)
                            .description(INFO_SEARCH_DESCRIPTION_FILENAME.get())
                            .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            proxyAuthzID =
                    StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                            .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                            .description(INFO_DESCRIPTION_PROXY_AUTHZID.get())
                            .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            pSearchInfo =
                    StringArgument.builder("persistentSearch")
                            .shortIdentifier('C')
                            .description(INFO_DESCRIPTION_PSEARCH_INFO.get())
                            .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_PSEARCH_INFO.get())
                            .valuePlaceholder(INFO_PSEARCH_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            simplePageSize =
                    IntegerArgument.builder("simplePageSize")
                            .description(INFO_DESCRIPTION_SIMPLE_PAGE_SIZE.get())
                            .lowerBound(1)
                            .defaultValue(1000)
                            .valuePlaceholder(INFO_NUM_ENTRIES_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            assertionFilter =
                    StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                            .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                            .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            matchedValuesFilter =
                    StringArgument.builder("matchedValuesFilter")
                            .description(INFO_DESCRIPTION_MATCHED_VALUES_FILTER.get())
                            .multiValued()
                            .valuePlaceholder(INFO_FILTER_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            sortOrder =
                    StringArgument.builder("sortOrder")
                            .shortIdentifier('S')
                            .description(INFO_DESCRIPTION_SORT_ORDER.get())
                            .valuePlaceholder(INFO_SORT_ORDER_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            vlvDescriptor =
                    StringArgument.builder("virtualListView")
                            .shortIdentifier('G')
                            .description(INFO_DESCRIPTION_VLV.get())
                            .valuePlaceholder(INFO_VLV_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            controlStr =
                    StringArgument.builder("control")
                            .shortIdentifier('J')
                            .description(INFO_DESCRIPTION_CONTROLS.get())
                            .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_CONTROLS.get())
                            .multiValued()
                            .valuePlaceholder(INFO_LDAP_CONTROL_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            effectiveRightsUser =
                    StringArgument.builder(OPTION_LONG_EFFECTIVERIGHTSUSER)
                            .shortIdentifier(OPTION_SHORT_EFFECTIVERIGHTSUSER)
                            .description(INFO_DESCRIPTION_EFFECTIVERIGHTS_USER.get())
                            .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            effectiveRightsAttrs =
                    StringArgument.builder(OPTION_LONG_EFFECTIVERIGHTSATTR)
                            .shortIdentifier(OPTION_SHORT_EFFECTIVERIGHTSATTR)
                            .description(INFO_DESCRIPTION_EFFECTIVERIGHTS_ATTR.get())
                            .multiValued()
                            .valuePlaceholder(INFO_ATTRIBUTE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);

            version = ldapVersionArgument();
            argParser.addArgument(version);

            dereferencePolicy =
                    MultiChoiceArgument.<DereferenceAliasesPolicy>builder("dereferencePolicy")
                            .shortIdentifier('a')
                            .description(INFO_SEARCH_DESCRIPTION_DEREFERENCE_POLICY.get())
                            .allowedValues(DereferenceAliasesPolicy.values())
                            .defaultValue(DereferenceAliasesPolicy.NEVER)
                            .valuePlaceholder(INFO_DEREFERENCE_POLICE_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            typesOnly =
                    BooleanArgument.builder("typesOnly")
                            .shortIdentifier('A')
                            .description(INFO_DESCRIPTION_TYPES_ONLY.get())
                            .buildAndAddToParser(argParser);
            sizeLimit =
                    IntegerArgument.builder("sizeLimit")
                            .shortIdentifier('z')
                            .description(INFO_SEARCH_DESCRIPTION_SIZE_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_SIZE_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            timeLimit =
                    IntegerArgument.builder("timeLimit")
                            .shortIdentifier('l')
                            .description(INFO_SEARCH_DESCRIPTION_TIME_LIMIT.get())
                            .defaultValue(0)
                            .valuePlaceholder(INFO_TIME_LIMIT_PLACEHOLDER.get())
                            .buildAndAddToParser(argParser);
            dontWrap =
                    BooleanArgument.builder("dontWrap")
                            .shortIdentifier('t')
                            .description(INFO_DESCRIPTION_DONT_WRAP.get())
                            .buildAndAddToParser(argParser);
            countEntries =
                    BooleanArgument.builder("countEntries")
                            .description(INFO_DESCRIPTION_COUNT_ENTRIES.get())
                            .buildAndAddToParser(argParser);

            final BooleanArgument continueOnError = continueOnErrorArgument();
            argParser.addArgument(continueOnError);

            noop = noOpArgument();
            argParser.addArgument(noop);

            verbose = verboseArgument();
            argParser.addArgument(verbose);

            final BooleanArgument showUsage = showUsageArgument();
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (final ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            // If we should just display usage or version information,
            // then print it and exit.
            if (argParser.usageOrVersionDisplayed()) {
                return 0;
            }

            connectionFactory = connectionFactoryProvider.getUnauthenticatedConnectionFactory();
            bindRequest = connectionFactoryProvider.getBindRequest();
        } catch (final ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        final List<Filter> filters = new LinkedList<>();
        final List<String> attributes = new LinkedList<>();
        final ArrayList<String> filterAndAttributeStrings = argParser.getTrailingArguments();
        if (!filterAndAttributeStrings.isEmpty()) {
            /* The list of trailing arguments should be structured as follow:
             - If a filter file is present, trailing arguments are
             considered as attributes
             - If filter file is not present, the first trailing argument is
             considered the filter, the other as attributes.*/
            if (!filename.isPresent()) {
                final String filterString = filterAndAttributeStrings.remove(0);

                try {
                    filters.add(Filter.valueOf(filterString));
                } catch (final LocalizedIllegalArgumentException e) {
                    errPrintln(e.getMessageObject());
                    return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
                }
            }
            // The rest are attributes
            attributes.addAll(filterAndAttributeStrings);
        }

        if (filename.isPresent()) {
            // Read the filter strings.
            try (BufferedReader in = new BufferedReader(new FileReader(filename.getValue()))) {
                String line = null;
                while ((line = in.readLine()) != null) {
                    if ("".equals(line.trim())) {
                        // ignore empty lines.
                        continue;
                    }
                    filters.add(Filter.valueOf(line));
                }
            } catch (final LocalizedIllegalArgumentException e) {
                errPrintln(e.getMessageObject());
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            } catch (final IOException e) {
                errPrintln(LocalizableMessage.raw(e.toString()));
                return ResultCode.CLIENT_SIDE_FILTER_ERROR.intValue();
            }
        }

        if (filters.isEmpty()) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_SEARCH_NO_FILTERS.get());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        SearchScope scope;
        try {
            scope = searchScope.getTypedValue();
        } catch (final ArgumentException ex1) {
            errPrintln(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        SearchRequest search;
        try {
            search =
                    Requests.newSearchRequest(DN.valueOf(baseDN.getValue()), scope, filters.get(0),
                            attributes.toArray(new String[attributes.size()]));
        } catch (final LocalizedIllegalArgumentException e) {
            errPrintln(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Read the LDAP version number.
        try {
            final int versionNumber = version.getIntValue();
            if (versionNumber != 2 && versionNumber != 3) {
                errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(String.valueOf(versionNumber)));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        } catch (final ArgumentException ae) {
            errPrintln(ERR_DESCRIPTION_INVALID_VERSION.get(version.getValue()));
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        search.setTypesOnly(typesOnly.isPresent());
        // searchOptions.setShowOperations(noop.isPresent());
        // searchOptions.setVerbose(verbose.isPresent());
        // searchOptions.setContinueOnError(continueOnError.isPresent());
        // searchOptions.setEncoding(encodingStr.getValue());
        // searchOptions.setCountMatchingEntries(countEntries.isPresent());
        try {
            search.setTimeLimit(timeLimit.getIntValue());
            search.setSizeLimit(sizeLimit.getIntValue());
        } catch (final ArgumentException ex1) {
            errPrintln(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }
        try {
            search.setDereferenceAliasesPolicy(dereferencePolicy.getTypedValue());
        } catch (final ArgumentException ex1) {
            errPrintln(ex1.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        if (controlStr.isPresent()) {
            for (final String ctrlString : controlStr.getValues()) {
                try {
                    final Control ctrl = Utils.getControl(ctrlString);
                    search.addControl(ctrl);
                } catch (final DecodeException de) {
                    errPrintln(ERR_TOOL_INVALID_CONTROL_STRING.get(ctrlString));
                    ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }
        }

        if (effectiveRightsUser.isPresent()) {
            final String authzID = effectiveRightsUser.getValue();
            if (!authzID.startsWith("dn:")) {
                errPrintln(ERR_EFFECTIVERIGHTS_INVALID_AUTHZID.get(authzID));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
            final Control effectiveRightsControl =
                    GetEffectiveRightsRequestControl.newControl(false, authzID.substring(3),
                            effectiveRightsAttrs.getValues().toArray(
                                    new String[effectiveRightsAttrs.getValues().size()]));
            search.addControl(effectiveRightsControl);
        }

        if (proxyAuthzID.isPresent()) {
            final Control proxyControl =
                    ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue());
            search.addControl(proxyControl);
        }

        if (pSearchInfo.isPresent()) {
            final String infoString = StaticUtils.toLowerCase(pSearchInfo.getValue().trim());
            boolean changesOnly = true;
            boolean returnECs = true;

            final StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

            if (!tokenizer.hasMoreTokens()) {
                errPrintln(ERR_PSEARCH_MISSING_DESCRIPTOR.get());
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            } else {
                final String token = tokenizer.nextToken();
                if (!"ps".equals(token)) {
                    errPrintln(ERR_PSEARCH_DOESNT_START_WITH_PS.get(infoString));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            final ArrayList<PersistentSearchChangeType> ct = new ArrayList<>(4);
            if (tokenizer.hasMoreTokens()) {
                final StringTokenizer st = new StringTokenizer(tokenizer.nextToken(), ", ");
                if (!st.hasMoreTokens()) {
                    ct.add(PersistentSearchChangeType.ADD);
                    ct.add(PersistentSearchChangeType.DELETE);
                    ct.add(PersistentSearchChangeType.MODIFY);
                    ct.add(PersistentSearchChangeType.MODIFY_DN);
                } else {
                    do {
                        final String token = st.nextToken();
                        if ("add".equals(token)) {
                            ct.add(PersistentSearchChangeType.ADD);
                        } else if ("delete".equals(token) || "del".equals(token)) {
                            ct.add(PersistentSearchChangeType.DELETE);
                        } else if ("modify".equals(token) || "mod".equals(token)) {
                            ct.add(PersistentSearchChangeType.MODIFY);
                        } else if ("modifydn".equals(token) || "moddn".equals(token)
                                || "modrdn".equals(token)) {
                            ct.add(PersistentSearchChangeType.MODIFY_DN);
                        } else if ("any".equals(token) || "all".equals(token)) {
                            ct.add(PersistentSearchChangeType.ADD);
                            ct.add(PersistentSearchChangeType.DELETE);
                            ct.add(PersistentSearchChangeType.MODIFY);
                            ct.add(PersistentSearchChangeType.MODIFY_DN);
                        } else {
                            errPrintln(ERR_PSEARCH_INVALID_CHANGE_TYPE.get(token));
                            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                        }
                    } while (st.hasMoreTokens());
                }
            }

            if (tokenizer.hasMoreTokens()) {
                final String token = tokenizer.nextToken();
                if ("1".equals(token) || "true".equals(token) || "yes".equals(token)) {
                    changesOnly = true;
                } else if ("0".equals(token) || "false".equals(token) || "no".equals(token)) {
                    changesOnly = false;
                } else {
                    errPrintln(ERR_PSEARCH_INVALID_CHANGESONLY.get(token));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            if (tokenizer.hasMoreTokens()) {
                final String token = tokenizer.nextToken();
                if ("1".equals(token) || "true".equals(token) || "yes".equals(token)) {
                    returnECs = true;
                } else if ("0".equals(token) || "false".equals(token) || "no".equals(token)) {
                    returnECs = false;
                } else {
                    errPrintln(ERR_PSEARCH_INVALID_RETURN_ECS.get(token));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            final PersistentSearchRequestControl psearchControl =
                    PersistentSearchRequestControl.newControl(true, changesOnly, returnECs, ct
                            .toArray(new PersistentSearchChangeType[ct.size()]));
            search.addControl(psearchControl);
        }

        if (assertionFilter.isPresent()) {
            final String filterString = assertionFilter.getValue();
            Filter filter;
            try {
                filter = Filter.valueOf(filterString);

                // FIXME -- Change this to the correct OID when the official one
                // is assigned.
                final Control assertionControl = AssertionRequestControl.newControl(true, filter);
                search.addControl(assertionControl);
            } catch (final LocalizedIllegalArgumentException le) {
                errPrintln(ERR_LDAP_ASSERTION_INVALID_FILTER.get(le.getMessage()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        if (matchedValuesFilter.isPresent()) {
            final List<String> mvFilterStrings = matchedValuesFilter.getValues();
            final List<Filter> mvFilters = new ArrayList<>();
            for (final String s : mvFilterStrings) {
                try {
                    final Filter f = Filter.valueOf(s);
                    mvFilters.add(f);
                } catch (final LocalizedIllegalArgumentException le) {
                    errPrintln(ERR_LDAP_MATCHEDVALUES_INVALID_FILTER.get(le.getMessage()));
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            }

            final MatchedValuesRequestControl mvc =
                    MatchedValuesRequestControl.newControl(true, mvFilters);
            search.addControl(mvc);
        }

        if (sortOrder.isPresent()) {
            try {
                search.addControl(ServerSideSortRequestControl.newControl(false, sortOrder
                        .getValue()));
            } catch (final LocalizedIllegalArgumentException le) {
                errPrintln(ERR_LDAP_SORTCONTROL_INVALID_ORDER.get(le.getMessageObject()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        if (vlvDescriptor.isPresent()) {
            if (!sortOrder.isPresent()) {
                final LocalizableMessage message =
                        ERR_LDAPSEARCH_VLV_REQUIRES_SORT.get(vlvDescriptor.getLongIdentifier(),
                                sortOrder.getLongIdentifier());
                errPrintln(message);
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }

            final StringTokenizer tokenizer = new StringTokenizer(vlvDescriptor.getValue(), ":");
            final int numTokens = tokenizer.countTokens();
            if (numTokens == 3) {
                try {
                    final int beforeCount = Integer.parseInt(tokenizer.nextToken());
                    final int afterCount = Integer.parseInt(tokenizer.nextToken());
                    final ByteString assertionValue = ByteString.valueOfUtf8(tokenizer.nextToken());
                    search.addControl(VirtualListViewRequestControl.newAssertionControl(true,
                            assertionValue, beforeCount, afterCount, null));
                } catch (final Exception e) {
                    errPrintln(ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            } else if (numTokens == 4) {
                try {
                    final int beforeCount = Integer.parseInt(tokenizer.nextToken());
                    final int afterCount = Integer.parseInt(tokenizer.nextToken());
                    final int offset = Integer.parseInt(tokenizer.nextToken());
                    final int contentCount = Integer.parseInt(tokenizer.nextToken());
                    search.addControl(VirtualListViewRequestControl.newOffsetControl(true, offset,
                            contentCount, beforeCount, afterCount, null));
                } catch (final Exception e) {
                    errPrintln(ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
                    return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
                }
            } else {
                errPrintln(ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        int pageSize = 0;
        if (simplePageSize.isPresent()) {
            if (filters.size() > 1) {
                errPrintln(ERR_PAGED_RESULTS_REQUIRES_SINGLE_FILTER.get());
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }

            try {
                pageSize = simplePageSize.getIntValue();
                search.addControl(SimplePagedResultsControl.newControl(true, pageSize, ByteString
                        .empty()));
            } catch (final ArgumentException ae) {
                errPrintln(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
                return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
            }
        }

        int wrapColumn = 80;
        if (dontWrap.isPresent()) {
            wrapColumn = 0;
        }

        if (noop.isPresent()) {
            /* We don't actually need to open a connection or perform the
             search, so we're done. We should return 0 to either mean that the
             processing was successful or that there were no matching entries,
             based on countEntries.isPresent() (but in either case the return value
             should be zero).*/
            return 0;
        }

        try (Connection connection = connectionFactory.getConnection()) {
            if (bindRequest != null) {
                printPasswordPolicyResults(this, connection.bind(bindRequest));
            }

            int filterIndex = 0;
            ldifWriter = new LDIFEntryWriter(getOutputStream()).setWrapColumn(wrapColumn);
            final LDAPSearchResultHandler resultHandler = new LDAPSearchResultHandler();
            while (true) {
                Result result = connection.search(search, resultHandler);
                try {
                    final ServerSideSortResponseControl control =
                            result.getControl(ServerSideSortResponseControl.DECODER,
                                    new DecodeOptions());
                    if (control != null
                            && control.getResult() != ResultCode.SUCCESS) {
                        println(WARN_LDAPSEARCH_SORT_ERROR.get(control.getResult().toString()));
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }

                try {
                    final VirtualListViewResponseControl control =
                            result.getControl(VirtualListViewResponseControl.DECODER,
                                    new DecodeOptions());
                    if (control != null) {
                        if (control.getResult() == ResultCode.SUCCESS) {
                            LocalizableMessage msg =
                                    INFO_LDAPSEARCH_VLV_TARGET_OFFSET.get(control
                                            .getTargetPosition());
                            println(msg);

                            msg = INFO_LDAPSEARCH_VLV_CONTENT_COUNT.get(control.getContentCount());
                            println(msg);
                        } else {
                            final LocalizableMessage msg =
                                    WARN_LDAPSEARCH_VLV_ERROR.get(control.getResult().toString());
                            println(msg);
                        }
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }

                try {
                    SimplePagedResultsControl control =
                            result.getControl(SimplePagedResultsControl.DECODER,
                                    new DecodeOptions());
                    if (control != null && control.getCookie().length() > 0) {
                        if (!isQuiet()) {
                            pressReturnToContinue();
                        }
                        final Iterator<Control> iterator = search.getControls().iterator();
                        while (iterator.hasNext()) {
                            if (SimplePagedResultsControl.OID.equals(iterator.next().getOID())) {
                                iterator.remove();
                            }
                        }
                        control = SimplePagedResultsControl.newControl(true, pageSize, control.getCookie());
                        search.addControl(control);
                        continue;
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }

                errPrintln();
                errPrintln(ERR_TOOL_RESULT_CODE.get(result.getResultCode().intValue(), result
                        .getResultCode().toString()));
                if (result.getDiagnosticMessage() != null
                        && result.getDiagnosticMessage().length() > 0) {
                    errPrintln(LocalizableMessage.raw(result.getDiagnosticMessage()));
                }
                if (result.getMatchedDN() != null && result.getMatchedDN().length() > 0) {
                    errPrintln(ERR_TOOL_MATCHED_DN.get(result.getMatchedDN()));
                }

                filterIndex++;
                if (filterIndex < filters.size()) {
                    search.setFilter(filters.get(filterIndex));
                } else {
                    break;
                }
            }
            if (countEntries.isPresent() && !isQuiet()) {
                println(INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT.get(resultHandler.entryCount));
                println();
            }
            return 0;
        } catch (final LdapException ere) {
            return printErrorMessage(this, ere);
        } finally {
            closeSilently(ldifWriter);
        }
    }
}
