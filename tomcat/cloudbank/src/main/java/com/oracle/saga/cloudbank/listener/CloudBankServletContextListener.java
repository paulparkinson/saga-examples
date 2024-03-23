/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.cloudbank.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oracle.saga.cloudbank.util.ConnectionPools;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class CloudBankServletContextListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory
            .getLogger(CloudBankServletContextListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {

        logger.info("CloudBank deployment starting");

        try {
            ConnectionPools.getCloudBankConnection();
        } catch (Exception e) {
            logger.error("Error creating pools", e);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        logger.info("CloudBank deployment shutting down!");
    }

}

