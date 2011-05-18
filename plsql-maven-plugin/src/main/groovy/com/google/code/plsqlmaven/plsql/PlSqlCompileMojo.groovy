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

import groovy.xml.MarkupBuilder

/**
 * Compile PL/SQL sources.
 *
 * @goal compile
 * 
 * @phase compile
 */
public class PlSqlCompileMojo
    extends PlSqlMojo
{
    
   /**
    * The specific source file to compile
    * @since 1.0
    * @parameter expression="${plsqlSource}"
    */
   private String plsqlSource;


    /**
     * Location of the touch file.
     */
    private touchFile;
    
    /**
     * Last compile time.
     */
    private lastCompileTime= 0L;
    
    /**
     * Files changed since last compile.
     */
    protected sources= [];

    void execute()
    {
          if (plsqlSource)
            compileSource()
          else
            compileSources()
    }
    
    private compileSource()
    {
            def success= false;
            
            getLastCompileTime();
            
            def sd= getSourceDescriptor(new File(plsqlSource))
            sd['changed']= true
            sources << sd
            
            if (!connectToDatabase())
            {
              fail('Need an Oracle connection')
              return
            }
            
            compileChangedFiles();
            
            success= reportCompileErrors();
                
            disconnectFromDatabase();
            
            touchReferenceFile();
            
            if (!success)
             fail('PL/SQL errors found.')
    }

    private compileSources()
    {
            def success= false;
            
            getLastCompileTime();
            
            if (determineChangedFiles())
            {
            
                if (!connectToDatabase())
                {
                  fail('Need an Oracle connection')
                  return
                }
            
                
                compileChangedFiles();
                
                success= reportCompileErrors();
                
                buildXmlReport(); // create an xml report for UI integration
                
                disconnectFromDatabase();
            }
			else
			   success= true
                
            touchReferenceFile();
            
            if (!success)
             fail('PL/SQL errors found.')
    }
    
    private boolean determineChangedFiles()
    {
        def files= getPlsqlSourceFiles()
        def cnt= 0;
        
        for (sourceFile in files)
        {
            if (log.debugEnabled)
            {
                log.debug("source filename: "+sourceFile.getAbsolutePath())
                log.debug("source: "+sourceFile.lastModified())
                log.debug("last: "+lastCompileTime)
            }
            
            def sd= getSourceDescriptor(sourceFile)
            
            if (sourceFile.lastModified() > lastCompileTime)
            {
                sd['changed']= true;
                cnt++;
            }
            else
                sd['changed']= false;
            
            sources << sd 
        }
        
        log.info("found ${cnt} changed sources...");
        
        return (sources.size()>0)
    }

    public boolean reportCompileErrors()
    throws Error
    {
        def success= true;
        
        sources.each()
        {
             sd ->
             
             sd['errors']= [];
             
             sql.eachRow("""select a.line, 
                                   a.position,
                                   a.text,
                                   lower(decode(a.attribute,'WARNING','WARN',a.attribute)) type,
                                   a.message_number,
                                   nvl(b.text,c.text) source_text
                              from user_errors a,
                                   user_source b,
                                   user_source c
                             where c.line(+)= a.line-1
                               and c.name(+)= a.name
                               and c.type(+)= a.type
                               and b.line(+)= a.line
                               and b.name(+)= a.name
                               and b.type(+)= a.type
                               and a.name= ${sd.name}
                               and a.type= ${sd.type}
                          order by a.sequence""")
             { 
                 sd.errors << it.toRowResult() 
             }
             
             if ( sd.errors.size() > 0 )
             {
                 
                log.error("file ${sd.file.absolutePath} has errors");
                
                for (error in sd.errors)
                {
                  this.logError(error);
                  
                  if (error.type=='error') 
                    success=false
                }
             }    
        }
        
		log.debug 'success: '+success
        return success
    }
    
    private buildXmlReport()
    {
        touchReferenceFile();
        
        def writer = new FileWriter(touchFile)
        def xml = new MarkupBuilder(writer)
        
        xml.errors() 
        {
            sources.each()
            {
                 sd ->
                 
                 xml.source(path: sd.file.absolutePath) 
                 {
                     sd.errors.each()
                     {
                         error ->
                         xml."${error.type}"( line: error.line, position: error.position, code: error.message_number)
                         {
                             xml.text('')
                             {
                                writer.write("<![CDATA[${error.text}]]>")
                             }
                         }                         
                     }
                 }
            }
        }
    }
    
    private void logError(error)
    {
          if (error.source_text)
          {
             log."${error.type}"(error.source_text.replaceFirst("\n", ""));
             log."${error.type}"('^'.padLeft(error.position));
          }
          
          log."${error.type}"("line: ${error.line} position: ${error.position} code: ${error.message_number} -> ${error.text}");
          log."${error.type}"("\n\n\n");
    }
    
    private void getLastCompileTime()
    {
        touchFile= new File(project.build.directory,".plsql")
        lastCompileTime= touchFile.lastModified();
        log.debug("touch file: ${touchFile.absolutePath}")
    }

    private void touchReferenceFile()
    {
        ant.mkdir(dir: project.build.directory)
        
        ant.touch(file: touchFile.getAbsolutePath())
    }

    public void compileChangedFiles()
    {
        sources.each() 
        {
           if (it.changed)
           {
               log.info("compiling: "+it.file.getAbsolutePath()+"...")
               
               compile(it.file)
           }
        }
        
		def recompiled= [:]
        sources.each()
        {
		   if (!recompiled[it.baseType+'|'+it.name])
		   {
             def ddl= """begin
                          execute immediate 'alter ${it.baseType} ${it.name} compile';
                         exception
                           when others then
                             null;
                         end;""";
						 
             log.info("re-compiling: ${it.baseType} ${it.name.toLowerCase()}...")
		   
             sql.execute(ddl.toString()) 
			 recompiled[it.baseType+'|'+it.name]= true;
		   }
        }
    }

}
