#!/bin/bash

EXIT_STATUS=0
./gradlew test || EXIT_STATUS=$?

if [[ $TRAVIS_BRANCH =~ ^master|2\.[34]\.x$ && $TRAVIS_REPO_SLUG == "grails/grails-datastore-test-support"
        && $TRAVIS_PULL_REQUEST == 'false'
    && $EXIT_STATUS -eq 0 
    && -n "$ARTIFACTORY_PASSWORD" ]]; then
    echo "Publishing archives"
    #./gradlew -PartifactoryPublishUsername=travis-grails-core upload || EXIT_STATUS=$?
fi

exit $EXIT_STATUS