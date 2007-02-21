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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

/**
 * This class provides an enumeration of the allowed rights.
 */
public enum EnumRight {

    /**
     * This enumeration is returned when the result of the right is "read".
     */
    READ        ("read"),
    /**
     * This enumeration is returned when the result of the right is "write".
     */
    WRITE       ("write"),
    /**
     * This enumeration is returned when the result of the right is "add".
     */
    ADD         ("add"),
    /**
     * This enumeration is returned when the result of the right is "delete".
     */
    DELETE      ("delete"),
    /**
     * This enumeration is returned when the result of the right is "search".
     */
    SEARCH      ("search"),
    /**
     * This enumeration is returned when the result of the right is "compare".
     */
    COMPARE     ("compare"),
    /**
     * This enumeration is returned when the result of the right is
     * "selfwrite".
     */
    SELFWRITE   ("selfwrite"),
    /**
     * This enumeration is returned when the result of the right is "proxy".
     */
    PROXY       ("proxy"),
    /**
     * This enumeration is returned when the result of the right is "import".
     */
    IMPORT      ("import"),
    /**
     * This enumeration is returned when the result of the right is "export".
     */
    EXPORT      ("export"),
    /**
     * This enumeration is returned when the result of the right is "all".
     */
    ALL         ("all"),
    /**
     * This enumeration is used internally by the modify operation
     * processing and is not part of the ACI syntax.
     */
    DELWRITE    ("delwrite"),
    /**
     * This enumerations is used internally by the modify operation
     * processing and is not part of the ACI syntax.
     */
    ADDWRITE    ("addwrite");

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
        int mask=AciHandler.ACI_NULL;
        switch(right) {
            case READ:
                mask=AciHandler.ACI_READ;
                break;
            case WRITE:
                mask=AciHandler.ACI_WRITE;
                break;
            case ADD:
                mask=AciHandler.ACI_ADD;
                break;
            case DELETE:
                mask=AciHandler.ACI_DELETE;
                break;
            case SEARCH:
                mask=AciHandler.ACI_SEARCH;
                break;
            case COMPARE:
                mask=AciHandler.ACI_COMPARE;
                break;
            case ALL:
                mask=AciHandler.ACI_ALL;
                break;
            case  EXPORT:
                mask=AciHandler.ACI_EXPORT;
                break;
            case IMPORT:
                mask=AciHandler.ACI_IMPORT;
                break;
            case PROXY:
                mask=AciHandler.ACI_PROXY;
                break;
            case SELFWRITE:
                mask=AciHandler.ACI_SELF;
                break;
        }
        return mask;
    }
}
