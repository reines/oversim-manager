#!/bin/bash

if [ "$#" -ne "2" ]
then
	echo "Invalid arguments given"
	exit
fi

mvn exec:java -Dexec.mainClass="com.jamierf.oversim.manager.main.ParseData" -Dexec.args="$1 $2"
