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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.Aci.*;

import java.util.EnumSet;
import java.util.Set;

/**
 * This class provides an enumeration of the allowed rights.
 */
public enum EnumRight {

    /**
     * This enumeration is returned when the result of the right is "read".
     *
     * @see Aci#ACI_READ
     */
    READ        ("read"),
    /**
     * This enumeration is returned when the result of the right is "write".
     *
     * @see Aci#ACI_WRITE
     */
    WRITE       ("write"),
    /**
     * This enumeration is returned when the result of the right is "add".
     *
     * @see Aci#ACI_ADD
     */
    ADD         ("add"),
    /**
     * This enumeration is returned when the result of the right is "delete".
     *
     * @see Aci#ACI_DELETE
     */
    DELETE      ("delete"),
    /**
     * This enumeration is returned when the result of the right is "search".
     *
     * @see Aci#ACI_SEARCH
     */
    SEARCH      ("search"),
    /**
     * This enumeration is returned when the result of the right is "compare".
     *
     * @see Aci#ACI_COMPARE
     */
    COMPARE     ("compare"),
    /**
     * This enumeration is returned when the result of the right is
     * "selfwrite".
     *
     * @see Aci#ACI_SELF
     */
    SELFWRITE   ("selfwrite"),
    /**
     * This enumeration is returned when the result of the right is "proxy".
     *
     * @see Aci#ACI_PROXY
     */
    PROXY       ("proxy"),
    /**
     * This enumeration is returned when the result of the right is "import".
     *
     * @see Aci#ACI_IMPORT
     */
    IMPORT      ("import"),
    /**
     * This enumeration is returned when the result of the right is "export".
     *
     * @see Aci#ACI_EXPORT
     */
    EXPORT      ("export"),
    /**
     * This enumeration is returned when the result of the right is "all".
     *
     * @see Aci#ACI_ALL
     */
    ALL         ("all");

    /**
     * The name of the right.
     */
    private final String right;

    /**
     * Creates an enumeration of the right name.
     * @param right The name of the right.
     */
    EnumRight (String right) {
        this.right = right ;
    }

    /**
     * Returns the string representation of the right.
     *
     * @return the string representation of the right
     */
    public String getRight() {
        return right;
    }

    /**
     * Checks if the enumeration is equal to the right name.
     * @param right The name of the right to check.
     * @return  True if the right is equal to the enumeration's.
     */
    public boolean isRight(String right){
        return right.equalsIgnoreCase(this.right);
    }

    /**
     * Creates an enumeration of the right name.
     * @param right The name of the right.
     * @return An enumeration of the right or null if the name is invalid.
     */
    public static EnumRight decode(String right){
        if (right != null){
            for (EnumRight t : EnumRight.values()){
                if (t.isRight(right)){
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns bit mask associated with the specified right.
     * @param right The right enumeration to return the mask for.
     * @return The bit mask associated with the right.
     */
    public static int getMask(EnumRight right) {
        int mask=ACI_NULL;
        switch(right) {
            case READ:
                mask=ACI_READ;
                break;
            case WRITE:
                mask=ACI_WRITE;
                break;
            case ADD:
                mask=ACI_ADD;
                break;
            case DELETE:
                mask=ACI_DELETE;
                break;
            case SEARCH:
                mask=ACI_SEARCH;
                break;
            case COMPARE:
                mask=ACI_COMPARE;
                break;
            case ALL:
                mask=ACI_ALL;
                break;
            case  EXPORT:
                mask=ACI_EXPORT;
                break;
            case IMPORT:
                mask=ACI_IMPORT;
                break;
            case PROXY:
                mask=ACI_PROXY;
                break;
            case SELFWRITE:
                mask=ACI_SELF;
                break;
        }
        return mask;
    }

    /**
     * Return the EnumRight corresponding to the provided rightsMask.
     *
     * @param rightsMask
     *          the rights mask for which to return the corresponding EnumRight
     * @return EnumRight corresponding to the provided rightsMask.
     */
    public static Set<EnumRight> getEnumRight(int rightsMask) {
        final EnumSet<EnumRight> results = EnumSet.noneOf(EnumRight.class);
        // Next 3 rights are not included in ALL for historical reasons.
        // ALL already existed when they got added. For compatibility reasons
        // with existing deployments, they were not included in ALL.
        if (hasRights(rightsMask, ACI_EXPORT))
        {
          results.add(EXPORT);
        }
        if (hasRights(rightsMask, ACI_IMPORT))
        {
          results.add(IMPORT);
        }
        if (hasRights(rightsMask, ACI_PROXY))
        {
          results.add(PROXY);
        }

        if (hasRights(rightsMask, ACI_ALL)) {
            results.add(ALL);
            return results;
        }
        // the remaining rights are already included in ALL
        if (hasRights(rightsMask, ACI_READ))
        {
          results.add(READ);
        }
        if (hasRights(rightsMask, ACI_WRITE))
        {
          results.add(WRITE);
        }
        if (hasRights(rightsMask, ACI_ADD))
        {
          results.add(ADD);
        }
        if (hasRights(rightsMask, ACI_DELETE))
        {
          results.add(DELETE);
        }
        if (hasRights(rightsMask, ACI_SEARCH))
        {
          results.add(SEARCH);
        }
        if (hasRights(rightsMask, ACI_COMPARE))
        {
          results.add(COMPARE);
        }
        if (hasRights(rightsMask, ACI_SELF))
        {
          results.add(SELFWRITE);
        }
        return results;
    }

    /**
     * Checks if the provided rights mask has the specified rights.
     *
     * @param rightsMask
     *          The rights mask to look into.
     * @param rights
     *          The rights to check for.
     * @return true if the rights mask has the specified rights, false
     *           otherwise.
     */
    public static boolean hasRights(int rightsMask, int rights) {
        return (rightsMask & rights) == rights;
    }
}
