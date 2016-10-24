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

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
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
import org.forgerock.opendj.ldap.controls.SubentriesRequestControl;
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
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.MultiChoiceArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.ldap.controls.AccountUsabilityResponseControl;
import com.forgerock.opendj.util.StaticUtils;
import org.forgerock.util.annotations.VisibleForTesting;

import static com.forgerock.opendj.cli.CliMessages.INFO_NUM_ENTRIES_PLACEHOLDER;
import static com.forgerock.opendj.cli.ToolVersionHandler.newSdkVersionHandler;
import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolParamException;
import static com.forgerock.opendj.ldap.tools.Utils.addControlsToRequest;
import static com.forgerock.opendj.ldap.tools.Utils.computeWrapColumn;
import static com.forgerock.opendj.ldap.tools.Utils.printErrorMessage;
import static com.forgerock.opendj.ldap.tools.Utils.printPasswordPolicyResults;
import static com.forgerock.opendj.ldap.tools.Utils.printlnTextMsg;
import static com.forgerock.opendj.ldap.tools.Utils.readAssertionControl;
import static com.forgerock.opendj.ldap.tools.Utils.readControls;
import static com.forgerock.opendj.ldap.tools.Utils.readFilterFromString;
import static com.forgerock.opendj.ldap.tools.Utils.runTool;
import static com.forgerock.opendj.ldap.tools.Utils.runToolAndExit;
import static org.forgerock.util.Utils.*;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.CommonArguments.*;

/** A tool that can be used to issue Search requests to the Directory Server. */
public final class LDAPSearch extends ToolConsoleApplication {

    /**
     * The main method for ldapsearch tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        runToolAndExit(new LDAPSearch(System.out, System.err), args);
    }

    /**
     * This method should be used to run this ldap tool programmatically.
     * Output and errors will be printed on provided {@link PrintStream}.
     *
     * @param out
     *            The {@link PrintStream} to use to write tool output.
     * @param err
     *            The {@link PrintStream} to use to write tool errors.
     * @param args
     *            The arguments to use with this tool.
     * @return The code returned by the tool
     */
    public static int run(final PrintStream out, final PrintStream err, final String... args) {
        return runTool(new LDAPSearch(out, err), args);
    }

    private class LDAPSearchResultHandler implements SearchResultHandler {
        private int entryCount;

        @Override
        public boolean handleEntry(final SearchResultEntry entry) {
            entryCount++;

            try {
                final EntryChangeNotificationResponseControl control =
                        entry.getControl(EntryChangeNotificationResponseControl.DECODER, new DecodeOptions());
                if (control != null) {
                    println(INFO_LDAPSEARCH_PSEARCH_CHANGE_TYPE.get(control.getChangeType().toString()));
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
                        entry.getControl(AccountUsabilityResponseControl.DECODER, new DecodeOptions());
                if (control != null) {
                    println(INFO_LDAPSEARCH_ACCTUSABLE_HEADER.get());
                    if (control.isUsable()) {
                        println(INFO_LDAPSEARCH_ACCTUSABLE_IS_USABLE.get());
                        if (control.getSecondsBeforeExpiration() > 0) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_EXPIRATION.get(
                                    secondsToTimeString(control.getSecondsBeforeExpiration())));
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
                                println(INFO_LDAPSEARCH_ACCTUSABLE_REMAINING_GRACE.get(
                                        control.getRemainingGraceLogins()));
                            }
                        }
                        if (control.isLocked()) {
                            println(INFO_LDAPSEARCH_ACCTUSABLE_LOCKED.get());
                            if (control.getSecondsBeforeUnlock() > 0) {
                                println(INFO_LDAPSEARCH_ACCTUSABLE_TIME_UNTIL_UNLOCK.get(
                                        secondsToTimeString(control.getSecondsBeforeUnlock())));
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

    private BooleanArgument verbose;

    private EntryWriter ldifWriter;

    @VisibleForTesting
    LDAPSearch(final PrintStream out, final PrintStream err) {
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

    @Override
    int run(final String... args) throws LDAPToolException {
        try {
            return runLdapSearch(args);
        } catch (final ArgumentException e) {
            throw newToolParamException(e, ERR_ERROR_PARSING_ARGS.get(e.getMessageObject()));
        }
    }

    int runLdapSearch(final String[] args) throws LDAPToolException, ArgumentException {
        // Create the command-line argument parser for use with this program.
        final LocalizableMessage toolDescription = INFO_LDAPSEARCH_TOOL_DESCRIPTION.get();
        final LDAPToolArgumentParser argParser = LDAPToolArgumentParser.builder(LDAPSearch.class.getName())
                .toolDescription(toolDescription)
                .trailingArgumentsUnbounded(1, "filter [attributes ...]")
                .build();
        argParser.setVersionHandler(newSdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_LDAPSEARCH.get());

        ConnectionFactoryProvider connectionFactoryProvider;

        BooleanArgument countEntries;
        IntegerArgument wrapColumn;
        BooleanArgument noop;
        BooleanArgument typesOnly;
        IntegerArgument simplePageSize;
        IntegerArgument timeLimit;
        StringArgument baseDN;
        StringArgument controlStr;
        MultiChoiceArgument<DereferenceAliasesPolicy> dereferencePolicy;
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
        BooleanArgument subEntriesArgument;

        try {
            connectionFactoryProvider = new ConnectionFactoryProvider(argParser, this);

            final StringArgument propertiesFileArgument = propertiesFileArgument();
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

            controlStr = controlArgument();
            argParser.addArgument(controlStr);

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
            wrapColumn = wrapColumnArgument();
            argParser.addArgument(wrapColumn);
            countEntries =
                    BooleanArgument.builder("countEntries")
                            .description(INFO_DESCRIPTION_COUNT_ENTRIES.get())
                            .buildAndAddToParser(argParser);
            subEntriesArgument =
                    BooleanArgument.builder(OPTION_LONG_SUBENTRIES)
                            .shortIdentifier(OPTION_SHORT_SUBENTRIES)
                            .description(INFO_DESCRIPTION_SUBENTRIES.get())
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
            throw newToolParamException(ae, ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
        }

        argParser.parseArguments(args, getErrStream(), connectionFactoryProvider);
        if (argParser.usageOrVersionDisplayed()) {
            return ResultCode.SUCCESS.intValue();
        }

        final List<String> trailingArgs = argParser.getTrailingArguments();
        final Filter filter = readFilterFromString(trailingArgs.get(0));
        final List<String> attributes = new LinkedList<>();
        if (trailingArgs.size() > 1) {
            // The rest of trailing argument are attributes
            attributes.addAll(trailingArgs.subList(1, trailingArgs.size()));
        }

        final SearchScope scope = searchScope.getTypedValue();
        final SearchRequest searchRequest;
        try {
            searchRequest = Requests.newSearchRequest(DN.valueOf(baseDN.getValue()),
                                                      scope,
                                                      filter,
                                                      attributes.toArray(new String[attributes.size()]));
        } catch (final LocalizedIllegalArgumentException e) {
            errPrintln(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        searchRequest.setTypesOnly(typesOnly.isPresent())
                     .setTimeLimit(timeLimit.getIntValue())
                     .setSizeLimit(sizeLimit.getIntValue())
                     .setDereferenceAliasesPolicy(dereferencePolicy.getTypedValue());
        addControlsToRequest(searchRequest, readControls(controlStr));

        if (effectiveRightsUser.isPresent()) {
            final String authzID = effectiveRightsUser.getValue();
            if (!authzID.startsWith("dn:")) {
                throw newToolParamException(ERR_EFFECTIVERIGHTS_INVALID_AUTHZID.get(authzID));
            }
            final List<String> attrValues = effectiveRightsAttrs.getValues();
            searchRequest.addControl(GetEffectiveRightsRequestControl.newControl(
                            false, authzID.substring(3), attrValues.toArray(new String[attrValues.size()])));
        }

        if (proxyAuthzID.isPresent()) {
            searchRequest.addControl(ProxiedAuthV2RequestControl.newControl(proxyAuthzID.getValue()));
        }

        if (pSearchInfo.isPresent()) {
            searchRequest.addControl(computePSearchControl(pSearchInfo));
        }

        if (assertionFilter.isPresent()) {
            searchRequest.addControl(readAssertionControl(assertionFilter.getValue()));
        }

        if (matchedValuesFilter.isPresent()) {
            final List<String> mvFilterStrings = matchedValuesFilter.getValues();
            final List<Filter> mvFilters = new ArrayList<>();
            for (final String s : mvFilterStrings) {
                try {
                    mvFilters.add(Filter.valueOf(s));
                } catch (final LocalizedIllegalArgumentException le) {
                    throw newToolParamException(le, ERR_LDAP_MATCHEDVALUES_INVALID_FILTER.get(le.getMessage()));
                }
            }
            searchRequest.addControl(MatchedValuesRequestControl.newControl(true, mvFilters));
        }

        if (sortOrder.isPresent()) {
            try {
                searchRequest.addControl(ServerSideSortRequestControl.newControl(false, sortOrder.getValue()));
            } catch (final LocalizedIllegalArgumentException le) {
                throw newToolParamException(le, ERR_LDAP_SORTCONTROL_INVALID_ORDER.get(le.getMessageObject()));
            }
        }

        if (vlvDescriptor.isPresent()) {
            searchRequest.addControl(readVLVControl(vlvDescriptor, sortOrder));
        }

        if (subEntriesArgument.isPresent()) {
            searchRequest.addControl(SubentriesRequestControl.newControl(true, true));
        }

        int pageSize = 0;
        if (simplePageSize.isPresent()) {
            pageSize = simplePageSize.getIntValue();
            searchRequest.addControl(SimplePagedResultsControl.newControl(true, pageSize, ByteString.empty()));
        }

        if (noop.isPresent()) {
            /* We don't actually need to open a connection or perform the
             search, so we're done. We should return 0 to either mean that the
             processing was successful or that there were no matching entries,
             based on countEntries.isPresent() (but in either case the return value
             should be zero).*/
            return ResultCode.SUCCESS.intValue();
        }

        try (Connection connection = argParser.getConnectionFactory().getConnection()) {
            final BindRequest bindRequest = argParser.getBindRequest();
            if (bindRequest != null) {
                printPasswordPolicyResults(this, connection.bind(bindRequest));
            }

            ldifWriter = new LDIFEntryWriter(getOutputStream()).setWrapColumn(computeWrapColumn(wrapColumn));
            final LDAPSearchResultHandler resultHandler = new LDAPSearchResultHandler();
            boolean hasRemainingPages;
            Result result;
            do {
                hasRemainingPages = false;
                result = connection.search(searchRequest, resultHandler);
                try {
                    final ServerSideSortResponseControl control =
                            result.getControl(ServerSideSortResponseControl.DECODER, new DecodeOptions());
                    if (control != null && ResultCode.SUCCESS != control.getResult()) {
                        println(WARN_LDAPSEARCH_SORT_ERROR.get(control.getResult().toString()));
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }

                try {
                    final VirtualListViewResponseControl control =
                            result.getControl(VirtualListViewResponseControl.DECODER, new DecodeOptions());
                    if (control != null) {
                        if (ResultCode.SUCCESS == control.getResult()) {
                            println(INFO_LDAPSEARCH_VLV_TARGET_OFFSET.get(control.getTargetPosition()));
                            println(INFO_LDAPSEARCH_VLV_CONTENT_COUNT.get(control.getContentCount()));
                        } else {
                            println(WARN_LDAPSEARCH_VLV_ERROR.get(control.getResult().toString()));
                        }
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }

                try {
                    SimplePagedResultsControl control =
                            result.getControl(SimplePagedResultsControl.DECODER, new DecodeOptions());
                    if (control != null && !control.getCookie().isEmpty()) {
                        if (!isQuiet()) {
                            pressReturnToContinue();
                        }
                        final Iterator<Control> iterator = searchRequest.getControls().iterator();
                        while (iterator.hasNext()) {
                            if (SimplePagedResultsControl.OID.equals(iterator.next().getOID())) {
                                iterator.remove();
                            }
                        }
                        control = SimplePagedResultsControl.newControl(true, pageSize, control.getCookie());
                        searchRequest.addControl(control);
                        hasRemainingPages = true;
                    }
                } catch (final DecodeException e) {
                    errPrintln(ERR_DECODE_CONTROL_FAILURE.get(e.getLocalizedMessage()));
                }
            } while (hasRemainingPages);

            printlnTextMsg(this, result.getDiagnosticMessage());
            printlnTextMsg(this, ERR_TOOL_MATCHED_DN, result.getMatchedDN());

            if (countEntries.isPresent() && !isQuiet()) {
                println(INFO_LDAPSEARCH_MATCHING_ENTRY_COUNT.get(resultHandler.entryCount));
                println();
            }
            return ResultCode.SUCCESS.intValue();
        } catch (final LdapException ere) {
            return printErrorMessage(this, ere, ERR_LDAP_SEARCH_FAILED);
        } finally {
            closeSilently(ldifWriter);
        }
    }

    private Control readVLVControl(final StringArgument vlvDescriptor, final StringArgument sortOrder)
            throws LDAPToolException {
        if (!sortOrder.isPresent()) {
            throw newToolParamException(ERR_LDAPSEARCH_VLV_REQUIRES_SORT.get(
                    vlvDescriptor.getLongIdentifier(), sortOrder.getLongIdentifier()));
        }

        final StringTokenizer tokenizer = new StringTokenizer(vlvDescriptor.getValue(), ":");
        final int numTokens = tokenizer.countTokens();
        try {
            if (numTokens == 3) {
                final int beforeCount = Integer.parseInt(tokenizer.nextToken());
                final int afterCount = Integer.parseInt(tokenizer.nextToken());
                final ByteString assertionValue = ByteString.valueOfUtf8(tokenizer.nextToken());
                return VirtualListViewRequestControl.newAssertionControl(
                        true, assertionValue, beforeCount, afterCount, null);
            } else if (numTokens == 4) {
                final int beforeCount = Integer.parseInt(tokenizer.nextToken());
                final int afterCount = Integer.parseInt(tokenizer.nextToken());
                final int offset = Integer.parseInt(tokenizer.nextToken());
                final int contentCount = Integer.parseInt(tokenizer.nextToken());
                return VirtualListViewRequestControl.newOffsetControl(
                        true, offset, contentCount, beforeCount, afterCount, null);
            } else {
                throw newToolParamException(ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
            }
        } catch (final Exception e) {
            throw newToolParamException(e, ERR_LDAPSEARCH_VLV_INVALID_DESCRIPTOR.get());
        }
    }

    private Control computePSearchControl(final StringArgument pSearchInfo) throws LDAPToolException {
        final String infoString = StaticUtils.toLowerCase(pSearchInfo.getValue().trim());
        final StringTokenizer tokenizer = new StringTokenizer(infoString, ":");

        if (!tokenizer.hasMoreTokens()) {
            throw newToolParamException(ERR_PSEARCH_MISSING_DESCRIPTOR.get());
        }

        final String pSearchToken = tokenizer.nextToken();
        if (!"ps".equals(pSearchToken)) {
            throw newToolParamException(ERR_PSEARCH_DOESNT_START_WITH_PS.get(infoString));
        }

        final List<PersistentSearchChangeType> pSearchChangeTypes = new ArrayList<>(4);
        if (tokenizer.hasMoreTokens()) {
            final StringTokenizer st = new StringTokenizer(tokenizer.nextToken(), ", ");
            if (!st.hasMoreTokens()) {
                addAllPersistentSearchChangeTypes(pSearchChangeTypes);
            } else {
                do {
                    addPersistentSearchChangeTypes(st.nextToken(), pSearchChangeTypes);
                } while (st.hasMoreTokens());
            }
        }

        boolean changesOnly = true;
        if (tokenizer.hasMoreTokens()) {
            changesOnly = readBooleanToken(tokenizer.nextToken(), ERR_PSEARCH_INVALID_CHANGESONLY);

        }

        boolean returnECs = true;
        if (tokenizer.hasMoreTokens()) {
            returnECs = readBooleanToken(tokenizer.nextToken(), ERR_PSEARCH_INVALID_RETURN_ECS);
        }

        return PersistentSearchRequestControl.newControl(true, changesOnly, returnECs,
                pSearchChangeTypes.toArray(new PersistentSearchChangeType[pSearchChangeTypes.size()]));
    }

    private boolean readBooleanToken(final String token, final LocalizableMessageDescriptor.Arg1<Object> errorMsg)
            throws LDAPToolException {
        switch (token) {
        case "1":
        case "true":
        case "yes":
            return true;
        case "0":
        case "false":
        case "no":
            return false;
        default:
            throw newToolParamException(errorMsg.get(token));
        }
    }

    private void addPersistentSearchChangeTypes(
            final String token, final List<PersistentSearchChangeType> pSearchChangeTypes) throws LDAPToolException {
        switch (token) {
        case "add":
            pSearchChangeTypes.add(PersistentSearchChangeType.ADD);
            break;
        case "del":
        case "delete":
            pSearchChangeTypes.add(PersistentSearchChangeType.DELETE);
            break;
        case "mod":
        case "modify":
            pSearchChangeTypes.add(PersistentSearchChangeType.MODIFY);
            break;
        case "moddn":
        case "modrdn":
        case "modifydn":
            pSearchChangeTypes.add(PersistentSearchChangeType.MODIFY_DN);
            break;
        case "any":
        case "all":
            addAllPersistentSearchChangeTypes(pSearchChangeTypes);
            break;
        default:
            throw newToolParamException(ERR_PSEARCH_INVALID_CHANGE_TYPE.get(token));
        }
    }

    private void addAllPersistentSearchChangeTypes(final List<PersistentSearchChangeType> pSearchChangeTypes) {
        pSearchChangeTypes.add(PersistentSearchChangeType.ADD);
        pSearchChangeTypes.add(PersistentSearchChangeType.DELETE);
        pSearchChangeTypes.add(PersistentSearchChangeType.MODIFY);
        pSearchChangeTypes.add(PersistentSearchChangeType.MODIFY_DN);
    }
}
