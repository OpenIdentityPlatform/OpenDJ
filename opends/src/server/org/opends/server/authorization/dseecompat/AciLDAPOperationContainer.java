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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import java.util.List;

import org.opends.server.core.*;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.Entry;
import org.opends.server.workflowelement.localbackend.*;

/**
 * The AciLDAPOperationContainer is an AciContainer
 * extended class that wraps each LDAP operation being
 * evaluated or tested for target matched of an ACI.
 */
public class AciLDAPOperationContainer extends AciContainer  {

    /*
     * The entry to be returned if this is a LDAP search.
     */
    private SearchResultEntry searchEntry;

    /*
     * The list of modifications if this operation is a LDAP
     * modify.
     */
    private List<Modification>  modifications;

    /**
     * Constructor interface for the compare operation.
     * @param operation The compare operation to evaluate.
     * @param rights  The rights of a compare operation.
     */
    public AciLDAPOperationContainer(CompareOperation operation, int rights) {
        super(operation, rights, operation.getEntryToCompare());
    }

    /**
     * Constructor interface for the add operation.
     * @param operation The add operation to evaluate.
     * @param rights  The rights of an add operation.
     */
    public AciLDAPOperationContainer(LocalBackendAddOperation operation,
        int rights)
    {
        super(operation, rights, operation.getEntryToAdd());
    }

    /**
     * Constructor interface for the delete operation.
     * @param operation The add operation to evaluate.
     * @param rights  The rights of a delete operation.
     */
    public AciLDAPOperationContainer(LocalBackendDeleteOperation operation,
        int rights)
    {
        super(operation, rights, operation.getEntryToDelete());
    }

    /**
     * Constructor interface for the modify operation.
     * @param rights The rights of modify operation.
     * @param operation The add operation to evaluate.
     */
    public AciLDAPOperationContainer(LocalBackendModifyOperation operation,
        int rights)
    {
        super(operation, rights, operation.getCurrentEntry());
        this.modifications=operation.getModifications();
    }

    /**
     * Constructor interface for the modify DN operation.
     * @param operation  The modify DN operation.
     * @param rights  The rights of the modify DN operation.
     * @param entry  The entry to evalauted for this modify DN.
     */
    public AciLDAPOperationContainer(ModifyDNOperation operation,  int rights,
                                     Entry entry) {
        super(operation, rights,  entry);
    }

    /**
     * Constructor interface for the LDAP search operation.
     * @param operation The search operation.
     * @param rights The rights of a search operation.
     * @param entry The entry to be evaluated for this search.
     */
    public AciLDAPOperationContainer(SearchOperation operation,
        int rights,
        SearchResultEntry entry)
    {
        super(operation, rights,  entry);
        this.searchEntry = entry;
    }

    /**
     * Retrieve the search result entry of the search operation.
     * @return The search result entry.
     */
    public SearchResultEntry getSearchResultEntry() {
        return this.searchEntry;
    }

    /** Retrieve the list of modifications if this is a LDAP modify.
     * @return The list of LDAP modifications to made on the resource entry.
     */
    public  List<Modification>  getModifications() {
        return modifications;
    }
}
