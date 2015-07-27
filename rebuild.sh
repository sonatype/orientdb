#!/bin/sh
dirname=`dirname $0`
dirname=`cd "$dirname" && pwd`
cd "$dirname"

exec mvn clean install -Dtest=skip -DfailIfNoTests=false "$@"