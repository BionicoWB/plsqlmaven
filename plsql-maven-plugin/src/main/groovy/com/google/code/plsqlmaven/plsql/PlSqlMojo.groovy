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
import org.codehaus.groovy.maven.mojo.GroovyMojo
import groovy.sql.Sql
import com.google.code.plsqlmaven.shared.PlSqlUtils

/**
 * Basic mojo to extend for PL/SQL Goals
 */
public abstract class PlSqlMojo
    extends GroovyMojo
{
    public static PLSQL_EXTENSION= PlSqlUtils.PLSQL_EXTENSION;

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
     * Default procedure to invoke on dad access
     * @since 1.0
     * @parameter defaultPage="${defaultPage}" default-value="home"
     */
    protected String defaultPage;

   /**
    * @parameter expression="${project}"
    * @required
    * @readonly
    */
    protected org.apache.maven.project.MavenProject project
   
   /**
   * Whether to use DROP TYPE mytype FORCE before type spec compilation
   * @since 1.10
   * @parameter expression="${dropForceTypes}"
   */
    protected boolean dropForceTypes;
    
   /**
    * Specify source directory
    * @since 1.11
    * @parameter expression="${sourceDir}"
    */
    private String sourceDir;

    /**
     * Database connection helper
     */
    protected Sql sql
    
    private PlSqlUtils plsqlUtils
    
    private templateEngine = new GStringTemplateEngine()

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
            getPlsqlUtils().setSql(sql)
        }
        else
            sql= null;
            
        return (sql!=null);
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
        def depsDir= new File(project.build.directory,'deps');
        def artifactDir= new File(depsDir,artifact.id)
        return getPlsqlUtils().getPlsqlSourceFiles(artifactDir.absolutePath+File.separator+'plsql')
    }

    public getPlsqlSourceFiles()
    {
        return getPlsqlUtils().getPlsqlSourceFiles(getSourceDir())
    }
    
    public getSourceDescriptor(file)
    {
        return getPlsqlUtils().getSourceDescriptor(file)
    }
    
    public compile(file)
    {
        getPlsqlUtils().compile(file)
    }
    
    public get_dir(base,name)
    {
            def dir= new File(base, name)
            if (!dir.exists()) dir.mkdir()
            return dir;
    }

    public String getSourceDir()
    {
            return (sourceDir ? project.basedir.absolutePath+File.separator+sourceDir : project.build.sourceDirectory)
    }
    
    public String getTemplate(path,context)
    {
           def tpl = this.getClass().getClassLoader().getResourceAsStream(path)
           def baos= new ByteArrayOutputStream()
           baos << tpl
           tpl= baos.toString().replace('\\','\\\\')
           def template = templateEngine.createTemplate(tpl).make(context)
           return template.toString();
    }
    
    public PlSqlUtils getPlsqlUtils()
    {
         if (!plsqlUtils)
           plsqlUtils= new PlSqlUtils(ant,log,dropForceTypes)
         
         return plsqlUtils
    }
     
	public sid(oracleIdentifier)
	{
		  if (oracleIdentifier==null)
		    return null
			
          if (oracleIdentifier!=oracleIdentifier.toUpperCase()
              ||(oracleIdentifier==~'.* .*')
              ||(oracleIdentifier==~'^[^A-Z].*'))
            return '"'+oracleIdentifier+'"'
          else
            return oracleIdentifier.toLowerCase()
    }

    public String path(p)
    {
         return p.replace('/',File.separator)
    }

    public String path2(String... dirs)
    {
        return dirs.findAll{ it }.join(File.separator) //thanks groovy
    } 

}
