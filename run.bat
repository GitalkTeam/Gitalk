@echo off
set OUT_DIR=out
set LIB_DIR=lib

java -cp "%OUT_DIR%;%LIB_DIR%/*" com.gitalk.GitalkApplication
pause