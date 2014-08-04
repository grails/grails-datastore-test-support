package grails.test.mixin.hibernate

import grails.test.mixin.TestMixin
import grails.test.mixin.gorm.Domain
import grails.test.runtime.FreshRuntime
import spock.lang.Specification

@Domain(Person)
@TestMixin(HibernateTestMixin)
public class FreshRuntimeSpec extends Specification {
    @FreshRuntime
    void "should runtime get refreshed between test methods"() {
      setup:
      new Person(name:'Erik').save(flush:true)

      expect:
      Person.findAllByName(name).size() == expected

      where:
      name || expected
      'Erik' | 1
      'Erik' | 1
      'Erik' | 1

    }
}
