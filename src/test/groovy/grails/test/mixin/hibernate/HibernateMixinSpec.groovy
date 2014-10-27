package grails.test.mixin.hibernate

import grails.persistence.Entity
import grails.test.mixin.TestMixin
import spock.lang.Specification

/**
 * Created by graemerocher on 24/03/14.
 */
@TestMixin(HibernateTestMixin)
class HibernateMixinSpec extends Specification{

    void setupSpec() {
        hibernateDomain([Person])
    }
    void "Test that it is possible to use a Hibernate mixin to test Hibernate interaction"() {
        given:
            def person = new Person(name:'John Doe')
            def personId = person.save(flush:true, failOnError:true)?.id
        expect:"Dynamic finders to work"
            Person.count() == 1
            Person.get(personId).name == 'John Doe'
            sessionFactory != null
            transactionManager != null
            hibernateSession != null
    }
}

@Entity
class Person {
    Long id
    Long version
    String name
    
    static mapping = {
        cache true
    }
}

