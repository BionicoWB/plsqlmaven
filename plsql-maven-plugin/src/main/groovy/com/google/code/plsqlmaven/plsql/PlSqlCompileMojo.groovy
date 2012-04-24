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
import java.sql.SQLSyntaxErrorException

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
   * Whether to loop waiting for changes, expressend in seconds between loops
   * @since 1.9
   * @parameter expression="${loop}"
   */
    private int loop;

   /**
    * Whether to compile code natively in C or not
    * @since 1.10
    * @parameter expression="${native}"
    */
    private boolean nativeComp;

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
            
            
            def sd= getSourceDescriptor(new File(plsqlSource))
            sd['changed']= true
            sources << sd
            
            if (!connectToDatabase())
            {
              fail('Need an Oracle connection')
              return
            }

            if (nativeComp) setNativeCompilation();
            
			def compileThings= 
			{
                getLastCompileTime();
			
	            compileChangedFiles();
	            
	            success= reportCompileErrors();
	                
	            touchReferenceFile();
			}
			
			if (loop)
			  while (true) 
			  {
				  compileThings()
				  Thread.currentThread().sleep(loop*1000)
		      }
			else
			  compileThings()
			
			
            disconnectFromDatabase();
			
            if (!success)
             fail('PL/SQL errors found.')
    }

    private setNativeCompilation()
    {
       log.info('using NATIVE compilation');
       sql.execute("alter session set plsql_code_type='NATIVE'");
    }

    private compileSources()
    {
            def success= false;
            
            getLastCompileTime();
            
            if (determineChangedFiles()||loop)
            {
            
                if (!connectToDatabase())
                {
                  fail('Need an Oracle connection')
                  return
                }

                if (nativeComp) setNativeCompilation();
            
                def compileThings= 
                {
                    compileChangedFiles();
                    success= reportCompileErrors();
                    touchReferenceFile();
                }
                
                if (loop)
                    while (true)
                    {
                        compileThings()
                        Thread.currentThread().sleep(loop*1000)
                        getLastCompileTime()
                        determineChangedFiles()
                    }
                else
                   compileThings()
                    
                buildXmlReport() // create an xml report for UI integration
						
                disconnectFromDatabase()
				
            }
    	    else
	        success= true
                
            if (!success)
             fail('PL/SQL errors found.')
    }
    
    private boolean determineChangedFiles()
    {
		sources= [];
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

        if (dropForceTypes) ensureTypeBodies()
        
        log.info("found ${cnt} changed sources...");
        
        return (cnt>0)
    }

    public void ensureTypeBodies()
    {
          def ensuredSources= sources.clone()

          sources.each
          {
               source ->

               if (source.type=='TYPE'&&source.changed)
               {
                   def body= ensuredSources.find{ body -> (body.name == source.name && body.type == 'TYPE BODY') }

                   if (body)
                   {
                     body.changed = true
                     ensuredSources.remove(body)
                     ensuredSources << body
                   }
               }
          }

          sources= ensuredSources
    }

    public boolean reportCompileErrors()
    throws Error
    {
        def success= true;
        
        sources.each()
        {
             sd ->
             
             if (sd['errors']) 
				 success= false
			 else
             if (!createForceViews||sd.type!='VIEW')
			 {
			     sd['errors']= []
			 
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
				 
			 }
			 
             if (sd['errors'] && sd.errors.size() > 0 )
             {
                 
                log.error("file ${sd.file.absolutePath} has errors");
                
                for (error in sd.errors)
                {
                  this.logError(error)
                  
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
        
        ant.touch(file: touchFile.absolutePath)
		
		def wait= true
		
		sources.each
		{
			source ->
			
			if (source.touchme)
			{
			  if (wait)
			  {
				   Thread.currentThread().sleep(1000)
				   wait= false
			  }
			  
			  ant.touch(file: source.file.absolutePath)
			}
	    }
    }

    public void compileChangedFiles()
    {
		def changed= false;
		
        sources.each() 
        {
           if (it.changed)
           {
			   changed= true;
			   
               log.info("compiling: "+it.file.getAbsolutePath()+"...")
               
			   try
			   {
                   compile(it.file)
			   }
			   catch (SQLSyntaxErrorException ssex)
			   {
				   it['touchme']= true;
				   it['errors']= []
				   it.errors << [ line: 1, position: 1, message_number: ssex.errorCode, 
					              text: 'syntax error: '+ssex.message, type: 'error']
			   }
           }
        }
        
		if (changed)
		{
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

}
