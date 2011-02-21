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


/**
 * Creates a SQL version of the artifact
 * to be run with SQL*Plus.
 *
 * @goal sqlplus
 *
 * @requiresDependencyResolution compile
 *
 */
public class PlSqlSqlPlusMojo
    extends PlSqlMojo
{
    
   /**
    * Whether to include dependencies in the script or not
    * @since 1.0
    * @parameter expression="${dependencies}"
    */
    private boolean dependencies;

    public static final SQLPLUS_SCRIPT= 'plsql_source.sqlplus.sql';
    
    void execute()
    {
        buildSqlScript();
    }
    
    private buildSqlScript()
    {
        ant.mkdir(dir: project.build.directory)

        def sqlScript= new File(project.build.directory,SQLPLUS_SCRIPT)
        ant.delete(file: sqlScript.absolutePath)
        
        sqlScript << "set define off;\n\n"
        
        if (dependencies)
        {
            unpackDependencies()
            def artifacts= project.getArtifacts()
            
            for (artifact in artifacts)        
               addSourceFiles(getArtifactPlsqlSourceFiles(artifact),sqlScript)
        }
        
        addSourceFiles(getPlsqlSourceFiles(),sqlScript)
            
    }
    
    private addSourceFiles(sourceFiles,sqlScript)
    {
        
        // first compile with no order
        sourceFiles.each()
        {
            sourceFile ->
            
            sqlScript << sourceFile.getText()
            
            sqlScript << "\n\n"
        }
        
        // then recompile it to make it VALID
        sourceFiles.each()
        {
            sourceFile ->
            
            def sd= getSourceDescriptor(sourceFile);
            
            sqlScript << "alter ${sd.baseType} ${sd.name} compile;\n";
            
            sqlScript << "show errors;\n\n"
        }

    }

}
