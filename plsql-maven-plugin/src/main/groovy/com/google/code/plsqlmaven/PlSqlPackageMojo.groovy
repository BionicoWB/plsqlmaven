package com.google.code.plsqlmaven;

import java.io.File;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Packages the PL/SQL artifact
 *
 * @goal package
 * 
 * @phase prepare-package
 *
 * @requiresDependencyResolution compile
 */
public class PlSqlPackageMojo
    extends PlSqlMojo
{
    void execute()
    {
        def plsqlOutputDirectory= project.build.outputDirectory+File.separator+"plsql"
        if (project.getPackaging()=='war')
          plsqlOutputDirectory= project.build.directory+"/${project.build.finalName}/WEB-INF/plsql".replace('/', File.separator)
  
        ant.mkdir(dir: plsqlOutputDirectory)
        ant.copy(todir: plsqlOutputDirectory)
        {
          ant.fileset(dir: project.build.sourceDirectory)
        }  
    }
}
