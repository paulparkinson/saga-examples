/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.bankB.listener;

import com.oracle.saga.bankB.util.ConnectionPools;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class AccountsServletContextListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory
            .getLogger(AccountsServletContextListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {

        logger.info("Bank B deployment starting");

        try {
            ConnectionPools.getAccountsConnection();
        } catch (Exception e) {
            logger.error("Error creating pools", e);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        logger.info("Bank B deployment shutting down!");
    }

}

