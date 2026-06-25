@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%~f0'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw -LiteralPath $script) -replace '^@.*$([\r\n]+)','' -replace '(?s)<#.*#>')) -NoNewScope}"`) DO (
  IF "%%A"=="MVN_CMD" (set __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo %%A) ELSE (echo %%A=%%B)
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=
@SET __MVNW_ARG0_NAME__=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=
@IF NOT "%__MVNW_CMD__%"=="" (%__MVNW_CMD__% %*)
@echo Cannot start maven from wrapper >&2 && exit /b 1
@GOTO :EOF
:EOF

<#
.SYNOPSIS
Apache Maven Wrapper startup batch script, version 3.2.0

.DESCRIPTION
This script will run maven from project directory.

.EXAMPLE
PS> ./mvnw.cmd -version
#>

$MAVEN_PROJECTBASEDIR = $env:MAVEN_BASEDIR
if (-not $MAVEN_PROJECTBASEDIR) {
  $script=$MyInvocation.MyCommand.Path
  $MAVEN_PROJECTBASEDIR=Split-Path -Parent $script
  while ((-not (Test-Path -Path "$MAVEN_PROJECTBASEDIR/.mvn" -PathType Container)) -and ($MAVEN_PROJECTBASEDIR -ne (Split-Path -Path $MAVEN_PROJECTBASEDIR -Qualifier))) {
    $MAVEN_PROJECTBASEDIR=Split-Path -Parent $MAVEN_PROJECTBASEDIR
  }
}

$wrapperJar="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
$wrapperProperties="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if (-not (Test-Path -Path $wrapperJar)) {
  if ($env:MVNW_VERBOSE -eq "true") {
    Write-Output "Couldn't find $wrapperJar, downloading it ..."
  }
  if ($env:MVNW_REPOURL) {
    $wrapperUrl="$env:MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
  } else {
    $wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
  }
  $props = Get-Content -Raw $wrapperProperties | ConvertFrom-StringData
  if ($props.wrapperUrl) {
    $wrapperUrl = $props.wrapperUrl
  }
  if ($env:MVNW_VERBOSE -eq "true") {
    Write-Output "Downloading from: $wrapperUrl"
  }
  $webclient = New-Object System.Net.WebClient
  if ($env:MVNW_USERNAME -and $env:MVNW_PASSWORD) {
    $webclient.Credentials = New-Object System.Net.NetworkCredential($env:MVNW_USERNAME, $env:MVNW_PASSWORD)
  }
  [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
  $webclient.DownloadFile($wrapperUrl, $wrapperJar)
  if ($env:MVNW_VERBOSE -eq "true") {
    Write-Output "Finished downloading $wrapperJar"
  }
}

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome) {
  $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
  if ($javaExe) {
    $jdkHome = Split-Path -Parent (Split-Path -Parent $javaExe)
  }
}
if ($jdkHome) {
  $javaCmd = Join-Path $jdkHome "bin/java.exe"
} else {
  $javaCmd = "java"
}

$mavenArgs = @(
  "-classpath", "$wrapperJar",
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR",
  "org.apache.maven.wrapper.MavenWrapperMain"
) + $args

Write-Output "MVN_CMD=$javaCmd $($mavenArgs -join ' ')"
