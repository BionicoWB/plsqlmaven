package com.google.code.plsqlmaven;

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

import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.IOUtil;

import org.codehaus.groovy.maven.mojo.GroovyMojo
import groovy.sql.Sql


/**
 * Basic mojo to extend for PL/SQL Goals
 */
public abstract class PlSqlMojo
    extends GroovyMojo
{
    public PLSQL_EXTENSION= '.plsql';

    /**
     * Database username. 
     * @since 1.0
     * @parameter expression="${username}"
     */
    private String username;

    /**
     * Database password.
     * @since 1.0
     * @parameter expression="${password}"
     */
    private String password;

    /**
     * Database URL.
     * @parameter expression="${url}"
     * @since 1.0
     */
    private String url;

    /**
    * @parameter expression="${project}"
    * @required
    * @readonly
    */
    protected org.apache.maven.project.MavenProject project
   
    
    /**
     * Database connection
     */
    protected Sql sql

    public void disconnectFromDatabase()
    {
        if (sql)
            sql.close();
    }

    public boolean connectToDatabase()
    {
        if (url)
        {
            log.debug( "connecting to " + url )
            sql = Sql.newInstance(url, username, password, "oracle.jdbc.driver.OracleDriver")
        }
        else
            sql= null;
            
        return (sql!=null);
    }

    public getSourceDescriptor(File source)
    {
            String[] path= source.getAbsolutePath().split('/')
            def name= path[path.length-1]
            name= name.substring(0, name.indexOf('.'))
            def type= path[path.length-2]
            def baseType= type
            
            if (type == name)
            {
              baseType= type= path[path.length-3]
              
              def ext= path[path.length-1].split('\\.');
              
              if (ext[1] in ['pkb','tpb'])
                type+=' BODY';
            }
             
            log.debug("type: ${type} name: ${name} file: ${source.absolutePath}")
            
            return ['name': name.toUpperCase(), 'baseType': baseType, 'type': type.toUpperCase(), 'file': source]
    }
    
    public getPlsqlSourceFiles(dir=project.build.sourceDirectory)
    {
        
        if (!new File(dir).exists())
         return [];

        DirectoryScanner plsqlFiles= new DirectoryScanner();
        
        plsqlFiles.setBasedir(dir);
        
        plsqlFiles.setIncludes(["**/*"+PLSQL_EXTENSION] as String[]);
        
        plsqlFiles.scan();

        def fileNames= plsqlFiles.getIncludedFiles();
        def files= [];
        
        for (fileName in fileNames)
           files << new File(dir, fileName)
           
        log.debug("found ${files.size} sources...");
        
        return files; 
    }
    
    public unpackDependencies()
    {        
        ant.delete(dir: new File(project.build.directory,"deps"))
        
        def artifacts= project.getArtifacts()
        
        ant.mkdir(dir: project.build.directory)
        def depsDir= new File(project.build.directory,"deps");
        ant.mkdir(dir: depsDir.absolutePath)
        
        for (artifact in artifacts)
        {
            def artifactDir= new File(depsDir,artifact.id)
            ant.mkdir(dir: artifactDir.absolutePath)
            ant.unzip(src: artifact.file.absolutePath, dest: artifactDir.absolutePath)
        }

    }
    
    public getArtifactPlsqlSourceFiles(artifact)
    {
        def depsDir= new File(project.build.directory,"deps");
        def artifactDir= new File(depsDir,artifact.id)
        return getPlsqlSourceFiles(artifactDir.absolutePath)
    }

    public get_type_ext(type)
    {
        switch (type)
        {
            case 'package':
               return 'pks'
            case 'package body':
               return 'pkb'
            case 'type':
               return 'tps'
            case 'type body':
               return 'tpb'
            case 'function':
               return 'fnc'
            case 'procedure':
               return 'prc'
            case 'trigger':
               return 'trg'
            default:
               return 'unk'
        }
    }

    public get_dir(base,name)
    {
            def dir= new File(base, name)
            if (!dir.exists()) dir.mkdir()
            return dir;
    }
    
}
