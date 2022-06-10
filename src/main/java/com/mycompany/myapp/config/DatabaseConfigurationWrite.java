package com.mycompany.myapp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.ClassUtils;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableJpaRepositories(
    entityManagerFactoryRef = "writeEntityManagerFactory",
    transactionManagerRef = "writeTransactionManager",
    basePackages = {DatabaseConfigurationWrite.REPOSITORY_PACKAGE_TO_SCAN}
)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
@EnableTransactionManagement
public class DatabaseConfigurationWrite {

    public static final String PERSISTENCE_UNIT_NAME = "write";
    public static final String REPOSITORY_PACKAGE_TO_SCAN = "com.mycompany.myapp.repository.write";
    public static final String MODEL_PACKAGE_TO_SCAN = "com.mycompany.myapp.domain";

    private final JpaProperties jpaProperties;
    private final HibernateProperties hibernateProperties;
    private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;

    public DatabaseConfigurationWrite(JpaProperties jpaProperties, HibernateProperties hibernateProperties,
                                      ConfigurableListableBeanFactory beanFactory,
                                      ObjectProvider<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
        this.jpaProperties = jpaProperties;
        this.hibernateProperties = hibernateProperties;
        this.hibernatePropertiesCustomizers = determineHibernatePropertiesCustomizers(beanFactory,
            hibernatePropertiesCustomizers.orderedStream().collect(Collectors.toList()));
    }

    @Primary
    @Bean(name = "writeDataSourceProperties")
    @ConfigurationProperties("spring.write-datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "writeDataSource")
    @ConfigurationProperties(prefix = "spring.write-datasource.hikari")
    public DataSource dataSource(@Qualifier("writeDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "writeEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                       @Qualifier("writeDataSource") DataSource dataSource) {
        final Map<String, Object> vendorProperties = this.getVendorProperties();
        this.customizeVendorProperties(vendorProperties);

        return builder
            .dataSource(dataSource)
            .persistenceUnit(PERSISTENCE_UNIT_NAME)
            .packages(MODEL_PACKAGE_TO_SCAN)
            .properties(vendorProperties)
            .build();
    }

    @Primary
    @Bean(name = "writeTransactionManager")
    public PlatformTransactionManager transactionManager(
        @Qualifier("writeEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private List<HibernatePropertiesCustomizer> determineHibernatePropertiesCustomizers(
        ConfigurableListableBeanFactory beanFactory,
        List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {

        List<HibernatePropertiesCustomizer> customizers = new ArrayList();
        if (ClassUtils.isPresent("org.hibernate.resource.beans.container.spi.BeanContainer",
            this.getClass().getClassLoader())) {
            customizers.add((properties) -> {
                properties.put("hibernate.resource.beans.container", new SpringBeanContainer(beanFactory));
            });
        }

        customizers.addAll(hibernatePropertiesCustomizers);
        return customizers;
    }

    private Map<String, Object> getVendorProperties() {
        return new LinkedHashMap<>(
            this.hibernateProperties.determineHibernateProperties(this.jpaProperties.getProperties(),
                new HibernateSettings()
                    // Spring Boot's HibernateDefaultDdlAutoProvider is not available here
                    .hibernatePropertiesCustomizers(this.hibernatePropertiesCustomizers)
            )
        );
    }

    protected void customizeVendorProperties(Map<String, Object> vendorProperties) {
        if (!vendorProperties.containsKey("hibernate.transaction.jta.platform")) {
            //
        }

        if (!vendorProperties.containsKey("hibernate.connection.provider_disables_autocommit")) {
            //
        }
    }
}
