#!/bin/bash
# This will update all pom.xml files to use version specified as first parameter of this script

newVersion=$1
if [ -z "$newVersion" ]; then
    echo "usage: `basename $0` <new-version>"
    exit 1
fi

mvn org.eclipse.tycho:tycho-versions-plugin:0.23.1:set-version \
    -Dtycho.mode=maven \
    -Dartifacts=orientdb-parent \
    -DnewVersion="$newVersion"
