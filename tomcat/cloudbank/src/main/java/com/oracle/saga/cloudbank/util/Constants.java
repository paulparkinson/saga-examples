/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.util;

/**
 * Contains a collection of defined constants.
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Transaction State Types
     */
    public static final int TRANS_STARTED = 1;
    public static final int TRANS_COMPLETED = 2;
    public static final int TRANS_ERROR = 3;

}
