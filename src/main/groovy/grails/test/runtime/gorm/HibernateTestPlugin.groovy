/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.test.runtime.gorm

import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.test.runtime.TestEvent
import grails.test.runtime.TestPlugin
import grails.test.runtime.TestRuntime
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import javax.activation.DataSource

import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.InstanceFactoryBean
import org.codehaus.groovy.grails.orm.hibernate.cfg.HibernateUtils;
import org.codehaus.groovy.grails.plugins.datasource.EmbeddedDatabaseShutdownHook
import org.codehaus.groovy.grails.support.PersistenceContextInterceptor
import org.hibernate.SessionFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.jdbc.datasource.DriverManagerDataSource

/**
 * a TestPlugin for TestRuntime for adding Grails DomainClass (GORM) support
 * 
 * @author Lari Hotari
 * @since 2.4.1
 *
 */
@CompileStatic
class HibernateTestPlugin implements TestPlugin {
    String[] requiredFeatures = ['grailsApplication', 'coreBeans']
    String[] providedFeatures = ['domainClass', 'gorm', 'hibernateGorm']
    int ordinal = 1
    
    void connectPersistenceInterceptor(TestRuntime runtime) {
        GrailsApplication grailsApplication = getGrailsApplication(runtime)
        if(grailsApplication.getMainContext().containsBean("persistenceInterceptor")) {
            def persistenceInterceptor = grailsApplication.getMainContext().getBean("persistenceInterceptor", PersistenceContextInterceptor)
            persistenceInterceptor.init()
            runtime.putValue("hibernateInterceptor", persistenceInterceptor)
        }
    }

    void destroyPersistenceInterceptor(TestRuntime runtime) {
        if(runtime.containsValueFor("hibernateInterceptor")) {
            PersistenceContextInterceptor persistenceInterceptor=(PersistenceContextInterceptor)runtime.removeValue("hibernateInterceptor")
            persistenceInterceptor.destroy()
        }
    }
    
    void cleanupSessionFactory(TestRuntime runtime) {
        GrailsApplication grailsApplication = getGrailsApplication(runtime)
        if(grailsApplication.getMainContext().containsBean("sessionFactory")) {
            SessionFactory sessionFactory = grailsApplication.getMainContext().getBean("sessionFactory", SessionFactory)
            if(sessionFactory != null && !sessionFactory.isClosed()) {
                try {
                    sessionFactory.close()
                } catch (Exception e) {
                    // ignore exception
                }
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void configureDefaultDataSource(TestRuntime runtime, boolean immediateDelivery = true) {
        defineBeans(runtime, immediateDelivery) {
            dataSource(DriverManagerDataSource) {
                url = "jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                username = "sa"
                password = ""
            }
            embeddedDatabaseShutdownHook(EmbeddedDatabaseShutdownHook)
        }
    }

    /**
     * Sets up a GORM for Hibernate domain
     *
     * @param persistentClasses
     */
    void hibernateDomain(TestRuntime runtime, Map parameters) {
        Collection<Class> persistentClasses = [] as Set
        persistentClasses.addAll((Collection<Class>)parameters.domains)
        boolean immediateDelivery = true
        if(runtime.containsValueFor("hibernatePersistentClassesToRegister")) {
            Collection<Class<?>> allPersistentClasses = runtime.getValue("hibernatePersistentClassesToRegister", Collection)
            allPersistentClasses.addAll(persistentClasses)
            immediateDelivery = false
        }

        DataSource dataSource = (DataSource)parameters.dataSource
        if(dataSource != null) {
            defineDataSourceBean(runtime, immediateDelivery, dataSource)
        }
        
        Properties initializerConfig
        if(parameters.config instanceof Map) {
            initializerConfig = new Properties()
            parameters.config.each { k, v ->
                initializerConfig.setProperty(k.toString(), v?.toString())
            }
        }
        
        if(immediateDelivery) {
            Collection<Class<?>> previousPersistentClasses = runtime.getValue("initializedHibernatePersistentClasses", Collection)
            if(!previousPersistentClasses?.containsAll(persistentClasses) || initializerConfig || dataSource) {
                if(previousPersistentClasses) {
                    persistentClasses.addAll(previousPersistentClasses)
                }
                boolean reconnectPersistenceInterceptor = false
                if(runtime.containsValueFor("hibernateInterceptor")) {
                    destroyPersistenceInterceptor(runtime)
                    cleanupSessionFactory(runtime)
                    reconnectPersistenceInterceptor = true
                }
                registerHibernateDomains(runtime, runtime.getValueIfExists("grailsApplication", GrailsApplication), persistentClasses, initializerConfig, true)
                if(reconnectPersistenceInterceptor) {
                    connectPersistenceInterceptor(runtime)
                }
            }
        } else {
            if(initializerConfig) {
                runtime.putValue("hibernateInitializerConfig", initializerConfig)
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private defineDataSourceBean(TestRuntime runtime, boolean immediateDelivery, DataSource dataSource) {
        defineBeans(runtime, immediateDelivery) {
            delegate.dataSource(InstanceFactoryBean, dataSource)
        }
    }

    protected void before(TestRuntime runtime) {
        connectPersistenceInterceptor(runtime)
    }

    protected void after(TestRuntime runtime) {
        destroyPersistenceInterceptor(runtime)
    }
    
    void defineBeans(TestRuntime runtime, boolean immediateDelivery = true, Closure<?> closure) {
        runtime.publishEvent("defineBeans", [closure: closure], [immediateDelivery: immediateDelivery])
    }
    
    GrailsApplication getGrailsApplication(TestRuntime runtime) {
        runtime.getValue("grailsApplication", GrailsApplication)
    }
    
    void registerBeans(TestRuntime runtime, GrailsApplication grailsApplication) {
        configureDefaultDataSource(runtime, false)
        if(runtime.containsValueFor("hibernatePersistentClassesToRegister")) {
            Collection<Class<?>> persistentClasses = runtime.removeValue("hibernatePersistentClassesToRegister", Collection)
            registerHibernateDomains(runtime, grailsApplication, persistentClasses, runtime.getValueIfExists("hibernateInitializerConfig", Properties), false)
        }
    }

    void registerHibernateDomains(TestRuntime runtime, GrailsApplication grailsApplication, Collection<Class<?>> persistentClasses, Properties initializerConfig, boolean immediateDelivery) {
        for(cls in persistentClasses) {
            grailsApplication.addArtefact(DomainClassArtefactHandler.TYPE, cls)
        }
        // workaround for GRAILS-11456
        if(!grailsApplication.config.containsKey("dataSource")) {
            grailsApplication.config.getProperty("dataSource")
        }
        def initializer = new HibernateDatastoreSpringInitializer(persistentClasses)
        def context = grailsApplication.getMainContext()
        def beansClosure = initializer.getBeanDefinitions((BeanDefinitionRegistry)context)
        runtime.putValue('initializedHibernatePersistentClasses', Collections.unmodifiableList(new ArrayList(persistentClasses)))
        defineBeans(runtime, immediateDelivery, beansClosure)
    }

    void onTestEvent(TestEvent event) {
        switch(event.name) {
            case 'before':
                before(event.runtime)
                break
            case 'after':
                after(event.runtime)
                break
            case 'registerBeans':
                registerBeans(event.runtime, (GrailsApplication)event.arguments.grailsApplication)
                break
            case 'afterClass':
                event.runtime.removeValue("hibernatePersistentClassesToRegister")
                break
            case 'beforeClass':
                Collection<Class<?>> persistentClasses = [] as Set
                persistentClasses.addAll(GormTestPluginUtil.collectDomainClassesFromAnnotations((Class<?>)event.arguments.testClass, event.runtime.getSharedRuntimeConfigurer()?.getClass()))
                event.runtime.putValue('hibernatePersistentClassesToRegister', persistentClasses)
                break
            case 'hibernateDomain':
                hibernateDomain(event.runtime, event.arguments)
                break
            case 'applicationInitialized':
                enhanceDomains(event.runtime, (GrailsApplication)event.arguments.grailsApplication)
                break
        }
    }
    
    void close(TestRuntime runtime) {
        runtime.removeValue('initializedHibernatePersistentClasses')
    }
    
    void enhanceDomains(TestRuntime runtime, GrailsApplication grailsApplication) {
        HibernateUtils.enhanceSessionFactories(grailsApplication.mainContext, grailsApplication)
    }
}


