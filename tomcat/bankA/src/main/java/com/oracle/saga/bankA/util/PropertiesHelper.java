/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.bankA.util;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesHelper {
    private static final Logger logger = LoggerFactory.getLogger(PropertiesHelper.class);

    private PropertiesHelper() {
    }

    public static Properties loadProperties() {
        Properties p = new Properties();
        try {
            InputStream in = PropertiesHelper.class.getClassLoader()
                    .getResourceAsStream("application.properties");
            p.load(in);
        } catch (Exception e) {
            logger.error("Could NOT load application.properties file", e);
        }
        return p;
    }
}