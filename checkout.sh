#!/usr/bin/env bash

#Checkout
if git clone -b master --recursive --depth=1 git://git.eclipse.org/gitroot/platform/eclipse.platform.releng.aggregator.git source; then
pushd source
git fetch --unshallow

export MAVEN_OPTS="-Xmx2048m"

#Build the SDK
mvn clean verify javadoc:jar -DskipTests -Dmaven.javadoc.failOnError=false #--fail-at-end
popd

#Build an individual bundle
#cd rt.equinox.framework
#mvn -Pbuild-individual-bundles clean verify

#Setup
mv modules-generator source
cp master-pom/pom.xml source

#Package and publish the SDK
pushd source
mvn package


#Cleanup for next time :-)
#git submodule foreach git clean -f -d -x
#git submodule foreach git reset --hard HEAD
#git clean -f -d -x
#git reset --hard HEAD

popd
else
 echo "Git clone failed"
# rm -rf source
fi