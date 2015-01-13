/*
 The Broad Institute
 SOFTWARE COPYRIGHT NOTICE AGREEMENT
 This software and its documentation are copyright (2003-2011) by the
 Broad Institute/Massachusetts Institute of Technology. All rights are
 reserved.
 
 This software is supplied without any warranty or guaranteed support
 whatsoever. Neither the Broad Institute nor MIT can be responsible for its
 use, misuse, or functionality.
 */

package org.genepattern.server.database;

import java.io.File;
import java.math.BigDecimal;
import java.util.Properties;


import org.apache.log4j.Logger;
import org.genepattern.server.config.GpConfig;
import org.genepattern.server.config.GpContext;
import org.genepattern.server.config.GpServerProperties;
import org.genepattern.server.config.ServerConfigurationFactory;
import org.genepattern.server.domain.Sequence;
import org.genepattern.webservice.OmnigeneException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;


public class HibernateUtil {
    private static final Logger log = Logger.getLogger(HibernateUtil.class);
    private static HibernateSessionManager instance;
    
    private static synchronized HibernateSessionManager instance() {
        if (instance==null) {
            log.debug("initializing hibernate session ...");
            GpContext serverContext=GpContext.getServerContext();
            GpConfig gpConfig=ServerConfigurationFactory.instance();
            instance=initFromConfig(gpConfig, serverContext);
        }
        
        return instance;
    }

    /**
     * Special-case for default database.vendor=HSQL, set the 'hibernate.connection.url' from the 'HSQL_port'.
     * @param gpConfig
     * @param gpContext
     * @param hibProps
     */
    private static void initHsqlConnectionUrl(GpConfig gpConfig, GpContext gpContext, Properties hibProps) {
        //special-case for default database.vendor=HSQL
        if ("hsql".equalsIgnoreCase(gpConfig.getDbVendor())) {
            Integer hsqlPort=null;
            final String PROP_HSQL_PORT="HSQL_port";
            if (hibProps.containsKey(PROP_HSQL_PORT)) {
                try {
                    hsqlPort=Integer.parseInt( hibProps.getProperty(PROP_HSQL_PORT) );
                }
                catch (Throwable t) {
                    log.error("Error in config file, expecting an Integer value for "+PROP_HSQL_PORT+"="+hibProps.getProperty(PROP_HSQL_PORT), t);
                }
            }
            if (hsqlPort==null) {
                hsqlPort=gpConfig.getGPIntegerProperty(gpContext, PROP_HSQL_PORT, 9001);
            }
            final String PROP_HIBERNATE_CONNECTION_URL="hibernate.connection.url";
            if (!hibProps.containsKey(PROP_HIBERNATE_CONNECTION_URL)) {
                String jdbcUrl="jdbc:hsqldb:hsql://127.0.0.1:"+hsqlPort+"/xdb";
                hibProps.setProperty(PROP_HIBERNATE_CONNECTION_URL, jdbcUrl);
                log.debug("setting "+PROP_HIBERNATE_CONNECTION_URL+"="+jdbcUrl);
            }
        }
    }
    
    protected static HibernateSessionManager initFromConfig(GpConfig gpConfig, GpContext gpContext) {
        Properties hibProps=gpConfig.getDbProperties();
        
        if (hibProps==null) {
            final String legacyConfigFile = System.getProperty("hibernate.configuration.file");
            
            if (legacyConfigFile==null) {
                log.warn("Using hard-coded database properties");
                // use hard-coded DB properties
                hibProps=new Properties();
                hibProps.setProperty("database.vendor","HSQL");
                hibProps.setProperty("HSQL_port","9001");
                hibProps.setProperty("hibernate.current_session_context_class","thread");
                hibProps.setProperty("hibernate.transaction.factory_class","org.hibernate.transaction.JDBCTransactionFactory");
                hibProps.setProperty("hibernate.connection.provider_class","org.hibernate.connection.C3P0ConnectionProvider");
                hibProps.setProperty("hibernate.jdbc.batch_size","20");
                hibProps.setProperty("hibernate.statement_cache.size","0");
                hibProps.setProperty("hibernate.connection.driver_class","org.hsqldb.jdbcDriver");
                hibProps.setProperty("hibernate.username","sa");
                hibProps.setProperty("hibernate.password","");
                hibProps.setProperty("hibernate.dialect","org.hibernate.dialect.HSQLDialect");
                hibProps.setProperty("hibernate.default_schema","PUBLIC");
                initHsqlConnectionUrl(gpConfig, gpContext, hibProps);
            }
            
            if (legacyConfigFile != null) {
                // fallback to pre 3.9.0 implementation
                log.warn("Using deprecated (pre-3.9.0) database configuration");
                final String jdbcUrl=null;
                return new HibernateSessionManager(legacyConfigFile, jdbcUrl);
            }

        }

        return new HibernateSessionManager(hibProps);
    }

    public static final Session getSession() {
        return instance().getSession();
    }
    
    public static final SessionFactory getSessionFactory() {
        return instance().getSessionFactory();
    }
    
    
    /**
     * Close the current session, if open.
     * 
     */
    public static void closeCurrentSession() {
        instance().closeCurrentSession();
    }

    /**
     * If the current session has an open transaction commit it and close the current session, otherwise do nothing.
     */
    public static void commitTransaction() {
        instance().commitTransaction();
    }

    /**
     * If the current session has an open transaction roll it back and close the current session, otherwise do nothing.
     * 
     */
    public static void rollbackTransaction() {
        instance().rollbackTransaction();
    }

    /**
     * Begin a new transaction. If a transaction is in progress do nothing.
     * 
     * @return
     */
    public static void beginTransaction() {
        instance().beginTransaction();
    }

    public static boolean isInTransaction() {
        return instance().isInTransaction();
    }

    /**
     * @deprecated - no longer rely on System properties for DB configuration.
     * @param sequenceName
     * @return
     */
    public static int getNextSequenceValue(String sequenceName) {
        String dbVendor = System.getProperty("database.vendor", "UNKNOWN");
        return getNextSequenceValue(dbVendor, sequenceName);
    }
    
    public static int getNextSequenceValue(GpConfig gpConfig, String sequenceName) {
        final String dbVendor=gpConfig.getDbVendor();
        return getNextSequenceValue(dbVendor, sequenceName);
    }
    
    public static int getNextSequenceValue(final String dbVendor, final String sequenceName) {
        if (dbVendor.equals("ORACLE")) {
            return ((BigDecimal) getSession().createSQLQuery("SELECT " + sequenceName + ".NEXTVAL FROM dual")
                    .uniqueResult()).intValue();
        } 
        else if (dbVendor.equals("HSQL")) {
            return (Integer) getSession().createSQLQuery("SELECT NEXT VALUE FOR " + sequenceName + " FROM dual").uniqueResult();
        } 
        else {
            return getNextSequenceValueGeneric(sequenceName);
        }
    }

    /**
     * get the next available sequence. Sequences are not part of the sql 92 standard and are not portable. The syntax
     * for hsql and oracle differ, and MySql doesn't expose sequeneces at all. Thus we use a table based scheme to
     * simulate a sequence.
     * 
     * The method is synchronized to prevent the same sequence number to be handed out to multiple callers (from
     * different threads. For the same reason a new session and transaction is created and closed prior to exit.
     */
    private static synchronized int getNextSequenceValueGeneric(String sequenceName) {
        StatelessSession session = null;
        try {
            // Open a new session and transaction. 
            // It's necessary that the sequence update be committed prior to exiting this method.
            SessionFactory sessionFactory = getSessionFactory();
            if (sessionFactory == null) {
                throw new ExceptionInInitializerError("Hibernate session factory is not initialized");
            }
            session = sessionFactory.openStatelessSession();
            session.beginTransaction();

            Query query = session.createQuery("from org.genepattern.server.domain.Sequence where name = :name");
            query.setString("name", sequenceName);
            Sequence seq = (Sequence) query.uniqueResult();
            if (seq != null) {
                int nextValue = seq.getNextValue();
                seq.setNextValue(nextValue + 1);
                session.update(seq);
                session.getTransaction().commit();
                return nextValue;
            } 
            else {
                session.getTransaction().rollback();
                String errorMsg = "Sequence table does not have an entry for: " + sequenceName;
                log.error(errorMsg);
                throw new OmnigeneException(errorMsg);
            }
        } 
        catch (Exception e) {
            if (session != null) {
                session.getTransaction().rollback();
            }
            log.error(e);
            throw new OmnigeneException(e);
        } 
        finally {
            if (session != null) {
                session.close();
            }
        }
    }
}
