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
 * Compile PL/SQL dependencies sources.
 *
 * @goal compile-dependencies
 *
 * @phase process-sources
 * 
 * @requiresDependencyResolution compile
 * 
 */
public class PlSqlCompileDependenciesMojo
    extends PlSqlCompileMojo
{
    
    void execute()
    {
            compileDependencies()
    }
    
    private compileDependencies()
    {
            def success= false;
            
            connectToDatabase();
            
            compileDependencyFiles();
            
            success= reportCompileErrors();
                
            disconnectFromDatabase();
            
            if (!success)
             fail('PL/SQL errors found.')
    }

    private compileDependencyFiles()
    {
          unpackDependencies()
          
          def artifacts= project.getArtifacts() 
          
          for (artifact in artifacts)
           determineSourcesToCompile(artifact)
          
          compileChangedFiles()
    }
    
    private determineSourcesToCompile(artifact)
    {
        def files= getArtifactPlsqlSourceFiles(artifact)
        def cnt= 0;
        
        for (sourceFile in files)
        {
            def sd= getSourceDescriptor(sourceFile)
            sd['changed']= true;
            sources << sd
        }
        
    }
    
}
