package com.google.code.plsqlmaven.plsql


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

import groovy.text.GStringTemplateEngine

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
        def engine = new GStringTemplateEngine()
        
        def plsqlOutputDirectory= project.build.outputDirectory+File.separator+"plsql"
        
        if (project.getPackaging()=='war')
        {
          plsqlOutputDirectory= project.build.directory+path("/${project.build.finalName}/WEB-INF/plsql")
          def configDir= plsqlOutputDirectory.replaceFirst('plsql$','entity-config'+File.separator+'plsqlgateway')
          ant.mkdir(dir: configDir)
		  
		  if (!new File(project.basedir.absolutePath+path('/src/main/webapp/WEB-INF/entity-config/plsqlgateway/general.xml')).exists())
		  {
	          def general= new File(configDir,'general.xml')
	          ant.truncate(file: general.absolutePath)
	          general << this.getClass().getClassLoader().getResourceAsStream('com/google/code/plsqlmaven/webapp/general.xml')
		  }
		  
		  if (!new File(project.basedir.absolutePath+path('/src/main/webapp/WEB-INF/entity-config/plsqlgateway/embedded.xml')).exists())
		  {
	          def embedded= new File(configDir,'embedded.xml')
	          ant.truncate(file: embedded.absolutePath)
	          embedded << getTemplate('com/google/code/plsqlmaven/webapp/embedded.xml',['defaultPage': defaultPage])
		  }
		  
		  if (!new File(project.basedir.absolutePath+path('/src/main/webapp/WEB-INF/web.xml')).exists())
		  {
	          def web= new File(plsqlOutputDirectory.replaceFirst('plsql$','web.xml'))
	          ant.truncate(file: web.absolutePath)
	          web << getTemplate('com/google/code/plsqlmaven/webapp/web.xml',['project': project])
		  }
        }
        
        ant.mkdir(dir: plsqlOutputDirectory)
        ant.copy(todir: plsqlOutputDirectory)
        {
          ant.fileset(dir: sourceDir)
        }  
        
        /*
        if (project.getPackaging()=='war')
        {
          def depsOutputDirectory= project.build.directory+"/${project.build.finalName}/WEB-INF/deps".replace('/', File.separator)
          def artifacts= project.getArtifacts()
        
          for (artifact in artifacts)
           if (artifact.getScope()!='provided')
            ant.copy(file: artifact.file.absolutePath, todir: depsOutputDirectory)
        }*/
        
    }
}
