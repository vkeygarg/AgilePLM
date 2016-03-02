#!/bin/sh 
@echo off
ECHO %DATE%
ECHO %TIME%


/apps/agile/agile932/jdk/bin/java -Xmx1024m -jar BOMDownTraversal.jar >> BOMDownTraversal.log


ECHO done
ECHO %DATE%
ECHO %TIME%