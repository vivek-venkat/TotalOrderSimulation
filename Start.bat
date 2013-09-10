@echo OFF
set INPUT=4
set /P INPUT=Enter Number of Nodes(Default 4): %=%
set JAVA_HOME=C:\Program Files\Java\jre6  
set PATH=C:\Program Files\Java\jre6 
set CLASSPATH=C:\Users\Sparrow\workspace_RMI\TotalOrderSimulation\bin
start java Overseer %INPUT%