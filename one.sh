#! /bin/sh
java -Xmx512M -cp ".:lib/*:lib/uncommons-maths-1.2.3/uncommons-maths-1.2.3.jar:lib/batik/*" core.DTNSim $*
