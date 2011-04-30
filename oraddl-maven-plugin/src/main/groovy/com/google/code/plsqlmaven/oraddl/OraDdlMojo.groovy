package com.google.code.plsqlmaven.oraddl

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

import groovy.sql.Sql
import org.codehaus.groovy.maven.mojo.GroovyMojo
import com.google.code.plsqlmaven.shared.SchemaUtils


/**
 * Basic mojo to extend for Oracle DDL goals
 */
public abstract class OraDdlMojo
    extends GroovyMojo
{

    /**
     * Database username.
     * @since 1.0
     * @parameter expression="${username}"
     */
    protected String username;

    /**
     * Database password.
     * @since 1.0
     * @parameter expression="${password}"
     */
    protected String password;

    /**
     * Database URL.
     * @parameter expression="${url}"
     * @since 1.0
     */
    protected String url;

    /**
    * @parameter expression="${project}"
    * @required
    * @readonly
    */
    protected org.apache.maven.project.MavenProject project
   
    /**
     * Database connection helper
     */
    protected Sql sql
    
    /**
     * Utilities for schema handling
     */
    protected SchemaUtils schemaUtils= new SchemaUtils(ant,log);
    
    public void disconnectFromDatabase()
    {
        if (sql)
            sql.close();
    }

    public boolean connectToDatabase()
    {
        if (url)
        {
            log.debug "connecting to " + url 
            sql = Sql.newInstance(url, username, password, "oracle.jdbc.driver.OracleDriver")
            schemaUtils.setSql(sql);
        }
        else
            sql= null;
            
        return (sql!=null);
    }
    
   /**
    * Source directory for schema files src/main/schema
    */
    public String getSourceDirectory()
    {
       return project.basedir.absolutePath+File.separator+"src"+File.separator+"main"+File.separator+"schema"+File.separator;
    }
    
    public getSchemaSourceFiles()
    {
        return schemaUtils.getSchemaSourceFiles(sourceDirectory)
    }

    public getSourceDescriptor(File source)
    {
       return schemaUtils.getSourceDescriptor(source);
    }

    public String path(p)
    {
        return p.replace('/',File.separator)
    }
    
    private val(v,d)
    {
       return (v==d ? null : v);
    }
}
