#!/usr/bin/env bash

#Checkout
git clone -b master --recursive git://git.eclipse.org/gitroot/platform/eclipse.platform.releng.aggregator.git source

#Setup
export MAVEN_OPTS="-Xmx2048m"
cp -r maven-ant source
cp -r modules-generator source
cp master-pom/pom.xml source

#Build the SDK
pushd source/eclipse.platform.releng.aggregator
mvn clean verify javadoc:jar -Dmaven.javadoc.failOnError=false --fail-at-end
popd

#Package and publish the SDK
pushd source
mvn


#Cleanup for next time :-)
git submodule foreach git clean -f -d -x
git submodule foreach git reset --hard HEAD
git clean -f -d -x
git reset --hard HEAD

popd