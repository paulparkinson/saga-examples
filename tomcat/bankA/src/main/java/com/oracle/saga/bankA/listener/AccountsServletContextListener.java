/**
 * Copyright (c) 2023 Oracle and/or its affiliates.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
package com.oracle.saga.bankA.listener;

import com.oracle.saga.bankA.controller.AccountsController;
import com.oracle.saga.bankA.util.ConnectionPools;
import com.oracle.saga.bankA.util.PropertiesHelper;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Properties;

@WebListener
public class AccountsServletContextListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory
            .getLogger(AccountsServletContextListener.class);

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {

        logger.info("Bank A deployment starting");

        try {
            ConnectionPools.getAccountsConnection();
        } catch (Exception e) {
            logger.error("Error creating pools", e);
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        logger.info("Bank A deployment shutting down!");
    }

}

