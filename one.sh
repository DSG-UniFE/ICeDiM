#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:lib/uncommons-maths-1.2.3/uncommons-maths-1.2.3.jar core.DTNSim $*
