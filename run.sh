#!/bin/bash

if [ -z "$*" ]
then
	echo "No arguments given"
	exit
fi

mvn exec:java -Dexec.mainClass="com.jamierf.oversim.manager.main.RunSimulation" -Dexec.args="$*"
